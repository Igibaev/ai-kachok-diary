package com.fitcoach.app.presentation.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitcoach.app.R
import com.fitcoach.app.ai.AiClientSelector
import com.fitcoach.app.ai.AiContextTitles
import com.fitcoach.app.ai.AiErrors
import com.fitcoach.app.ai.AiMode
import com.fitcoach.app.brand.BrandConfig
import com.fitcoach.app.domain.model.ChatMessage
import com.fitcoach.app.domain.model.UserProfile
import com.fitcoach.app.domain.program.ProgramCatalog
import com.fitcoach.app.domain.repository.ChatRepository
import com.fitcoach.app.domain.repository.NutritionRepository
import com.fitcoach.app.domain.repository.UserRepository
import com.fitcoach.app.domain.repository.WaterRepository
import com.fitcoach.app.domain.repository.WorkoutRepository
import com.fitcoach.app.domain.usecase.ai.BuildSystemPromptUseCase
import com.fitcoach.app.domain.usecase.ai.PromptContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isTyping: Boolean = false,
    val mode: AiMode = AiMode.DEMO,
    /** Идентификаторы строковых ресурсов быстрых подсказок — текст подставляет UI на текущем языке. */
    val quickPrompts: List<Int> = emptyList(),
    val coachName: String = BrandConfig.aiCoachName
)

/** URL WhatsApp клуба для кнопки «Спросить тренера» (текст лида — на языке приложения); null — WhatsApp не настроен. */
fun trainerWhatsappUrl(context: android.content.Context): String? =
    if (BrandConfig.hasWhatsapp) BrandConfig.whatsappUrl(context.getString(R.string.chat_trainer_message, BrandConfig.appName)) else null

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val selector: AiClientSelector,
    private val buildSystemPrompt: BuildSystemPromptUseCase,
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
    private val workoutRepository: WorkoutRepository,
    private val nutritionRepository: NutritionRepository,
    private val waterRepository: WaterRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            chatRepository.getAllMessages().collect { msgs ->
                _state.update { it.copy(messages = msgs) }
            }
        }
        viewModelScope.launch { refreshMode() }
        viewModelScope.launch { refreshQuickPrompts() }
    }

    /** Режим мог измениться в настройках — пересчитываем при возврате на экран. */
    fun refreshMode() {
        viewModelScope.launch {
            val mode = selector.currentMode()
            _state.update { it.copy(mode = mode) }
        }
    }

    fun sendMessage(userInput: String) {
        val text = userInput.trim()
        if (text.isEmpty() || _state.value.isTyping) return
        // Флаг ставим синхронно, до запуска корутины — иначе двойной тап успевает отправить дважды.
        _state.update { it.copy(isTyping = true) }
        viewModelScope.launch {
            // Всё тело — внутри try/finally: ошибка Room до отправки не должна ни уронить процесс,
            // ни оставить isTyping=true (ввод навсегда заблокирован).
            var locale = BrandConfig.defaultLanguage
            try {
                // История берётся ДО сохранения нового сообщения: текущий вопрос уходит отдельно.
                val history = chatRepository.getRecentMessages(HISTORY_LIMIT).filter { !it.isError }
                val userMsg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    role = "user",
                    content = text,
                    timestamp = System.currentTimeMillis()
                )
                chatRepository.saveMessage(userMsg)

                val profile = userRepository.getProfile() ?: UserProfile()
                locale = profile.language.ifBlank { BrandConfig.defaultLanguage }
                val system = buildSystemPrompt(buildContext(profile, locale))
                val client = selector.current()
                _state.update { it.copy(mode = selector.currentMode()) }
                val result = client.chat(system, history.filter { it.id != userMsg.id }, text, locale)
                result.fold(
                    onSuccess = { reply -> saveAssistant(reply, isError = false) },
                    onFailure = { e -> saveAssistant(e.message ?: AiErrors.emptyAnswer(locale), isError = true) }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                runCatching { saveAssistant(AiErrors.serverUnavailable(locale), isError = true) }
            } finally {
                _state.update { it.copy(isTyping = false) }
                refreshQuickPrompts()
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch { chatRepository.clearAll() }
    }

    private suspend fun saveAssistant(text: String, isError: Boolean) {
        chatRepository.saveMessage(
            ChatMessage(
                id = UUID.randomUUID().toString(),
                role = "assistant",
                content = text,
                timestamp = System.currentTimeMillis(),
                isError = isError
            )
        )
    }

    private suspend fun buildContext(profile: UserProfile, locale: String): PromptContext {
        val now = System.currentTimeMillis()
        val todayWorkout = workoutRepository.getWorkoutForDate(now)
        val recent = workoutRepository.getRecentWorkouts(5)
        val nutrition = nutritionRepository.getNutritionSummaryForDate(now).first()
        val water = waterRepository.getTotalForDateSync(now)
        val program = ProgramCatalog.getOrDefault(profile.programKey)
        val completed = workoutRepository.observeCompletedWorkouts().first()
        val next = ProgramCatalog.nextWorkout(program, completed, profile.daysPerWeek)
        val week = next.weekNumber
        val phase = next.phase.name
        return PromptContext(
            profile = profile,
            locale = locale,
            programTitle = AiContextTitles.programTitle(profile.programKey),
            currentWeek = week,
            phaseName = phase,
            todayWorkout = todayWorkout,
            todayWorkoutTitle = todayWorkout?.let { AiContextTitles.workoutTitle(it) },
            nextWorkoutTitle = if (todayWorkout?.isCompleted == true) null else "${next.template.title} · ${next.template.exercises.size} упражнений · ~${next.template.estimatedMinutes} мин",
            recentWorkouts = recent,
            todayNutrition = nutrition,
            waterToday = water
        )
    }

    /** Быстрые подсказки зависят от времени суток и того, была ли сегодня тренировка. */
    private suspend fun refreshQuickPrompts() {
        val todayWorkout = runCatching { workoutRepository.getWorkoutForDate(System.currentTimeMillis()) }.getOrNull()
        val hour = LocalTime.now(ZoneId.systemDefault()).hour
        val prompts = buildList {
            when {
                todayWorkout?.isCompleted == true -> {
                    add(R.string.chat_prompt_after_workout_food)
                    add(R.string.chat_prompt_recovery)
                }
                hour < 12 -> {
                    add(R.string.chat_prompt_breakfast)
                    add(R.string.chat_prompt_warmup)
                }
                hour >= 18 -> {
                    add(R.string.chat_prompt_eat_before_sleep)
                    add(R.string.chat_prompt_sleep)
                }
                else -> {
                    add(R.string.chat_prompt_eat_now)
                    add(R.string.chat_prompt_machine_busy)
                }
            }
            add(R.string.chat_prompt_back_pain)
            add(R.string.chat_prompt_motivate)
            add(R.string.chat_prompt_book_trainer)
        }
        _state.update { it.copy(quickPrompts = prompts) }
    }

    private companion object {
        const val HISTORY_LIMIT = 20
    }
}
