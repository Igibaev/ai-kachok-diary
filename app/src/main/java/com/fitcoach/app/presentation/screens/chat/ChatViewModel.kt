package com.fitcoach.app.presentation.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitcoach.app.ai.AiClientSelector
import com.fitcoach.app.ai.AiContextTitles
import com.fitcoach.app.ai.AiErrors
import com.fitcoach.app.ai.AiMode
import com.fitcoach.app.brand.BrandConfig
import com.fitcoach.app.domain.model.ChatMessage
import com.fitcoach.app.domain.model.UserProfile
import com.fitcoach.app.domain.model.WorkoutPlan
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
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isTyping: Boolean = false,
    val mode: AiMode = AiMode.DEMO,
    val quickPrompts: List<String> = emptyList(),
    val coachName: String = BrandConfig.aiCoachName,
    /** URL WhatsApp клуба для кнопки «Спросить тренера»; null — WhatsApp не настроен. */
    val trainerWhatsappUrl: String? = null
)

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

    private val _state = MutableStateFlow(
        ChatUiState(
            trainerWhatsappUrl = if (BrandConfig.hasWhatsapp) BrandConfig.whatsappUrl(TRAINER_MESSAGE) else null
        )
    )
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
            val locale = profile.language.ifBlank { BrandConfig.defaultLanguage }
            try {
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
                saveAssistant(AiErrors.serverUnavailable(locale), isError = true)
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
        val startDate = Instant.ofEpochMilli(profile.programStartDate).atZone(ZoneId.systemDefault()).toLocalDate()
        val week = WorkoutPlan.getCurrentWeek(startDate)
        val phase = WorkoutPlan.getPhaseName(profile.programKey, week)
        return PromptContext(
            profile = profile,
            locale = locale,
            programTitle = AiContextTitles.programTitle(profile.programKey),
            currentWeek = week,
            phaseName = phase,
            todayWorkout = todayWorkout,
            todayWorkoutTitle = todayWorkout?.let { AiContextTitles.workoutTitle(it) },
            nextWorkoutTitle = null,
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
                    add("Что поесть после тренировки?")
                    add("Как восстановиться быстрее?")
                }
                hour < 12 -> {
                    add("Что съесть на завтрак?")
                    add("Разминка перед тренировкой")
                }
                hour >= 18 -> {
                    add("Можно ли есть перед сном?")
                    add("Как улучшить сон?")
                }
                else -> {
                    add("Что поесть сейчас?")
                    add("Тренажёр занят, чем заменить?")
                }
            }
            add("Болит спина")
            add("Мотивируй меня")
            add("Запиши меня к тренеру")
        }
        _state.update { it.copy(quickPrompts = prompts) }
    }

    private companion object {
        const val HISTORY_LIMIT = 20
        val TRAINER_MESSAGE = "Здравствуйте! Пишу из приложения ${BrandConfig.appName}. Вопрос тренеру: "
    }
}
