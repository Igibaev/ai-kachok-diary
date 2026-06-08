package com.fitcoach.app.presentation.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitcoach.app.domain.model.*
import com.fitcoach.app.domain.repository.*
import com.fitcoach.app.domain.usecase.ai.BuildSystemPromptUseCase
import com.fitcoach.app.domain.usecase.ai.SendMessageUseCase
import com.fitcoach.app.presentation.theme.FitCoachColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isTyping: Boolean = false,
    val hasApiKey: Boolean = false
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val sendMessageUseCase: SendMessageUseCase,
    private val buildSystemPromptUseCase: BuildSystemPromptUseCase,
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
        viewModelScope.launch {
            val key = userRepository.getApiKey()
            _state.update { it.copy(hasApiKey = key.isNotEmpty()) }
        }
    }

    fun sendMessage(userInput: String) {
        viewModelScope.launch {
            val userMsg = ChatMessage(
                id = UUID.randomUUID().toString(),
                role = "user",
                content = userInput,
                timestamp = System.currentTimeMillis()
            )
            chatRepository.saveMessage(userMsg)
            _state.update { it.copy(isTyping = true) }

            try {
                val profile = userRepository.getProfile() ?: UserProfile()
                val apiKey = userRepository.getApiKey()
                val todayMillis = System.currentTimeMillis()
                val todayWorkout = workoutRepository.getWorkoutForDate(todayMillis)
                val recentWorkouts = workoutRepository.getRecentWorkouts(5)
                val nutritionSummary = nutritionRepository.getNutritionSummaryForDate(todayMillis).first()
                val waterToday = waterRepository.getTotalForDateSync(todayMillis)
                val startDate = LocalDate.ofEpochDay(profile.programStartDate / 86400000L)
                val currentWeek = WorkoutPlan.getCurrentWeek(startDate)

                val systemPrompt = buildSystemPromptUseCase(
                    profile, todayWorkout, recentWorkouts, nutritionSummary, waterToday, currentWeek
                )

                val history = chatRepository.getRecentMessages(20)
                val result = sendMessageUseCase(apiKey, systemPrompt, history, userInput)

                result.fold(
                    onSuccess = { response ->
                        val assistantMsg = ChatMessage(
                            id = UUID.randomUUID().toString(),
                            role = "assistant",
                            content = response,
                            timestamp = System.currentTimeMillis()
                        )
                        chatRepository.saveMessage(assistantMsg)
                    },
                    onFailure = { error ->
                        val errorMsg = ChatMessage(
                            id = UUID.randomUUID().toString(),
                            role = "assistant",
                            content = "⚠️ Ошибка: ${error.message ?: "Не удалось получить ответ"}",
                            timestamp = System.currentTimeMillis()
                        )
                        chatRepository.saveMessage(errorMsg)
                    }
                )
            } finally {
                _state.update { it.copy(isTyping = false) }
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch { chatRepository.clearAll() }
    }
}

private val quickPrompts = listOf(
    "Как прошла тренировка?",
    "Что поесть сейчас?",
    "Болит спина",
    "Мотивируй меня"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FitCoachColors.Background)
    ) {
        // Top bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(FitCoachColors.Surface)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null, tint = FitCoachColors.TextPrimary)
                }
                Text(
                    text = "AI Тренер",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = FitCoachColors.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { viewModel.clearHistory() }) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "Очистить", tint = FitCoachColors.TextMuted)
                }
            }
        }

        if (!state.hasApiKey) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FitCoachColors.Warning.copy(alpha = 0.1f))
                    .padding(12.dp)
            ) {
                Text(
                    "⚠️ API ключ не настроен. Перейди в Настройки → API ключ Anthropic",
                    fontSize = 13.sp,
                    color = FitCoachColors.Warning
                )
            }
        }

        // Messages
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            items(state.messages) { message ->
                MessageBubble(message)
            }
            if (state.isTyping) {
                item {
                    TypingIndicator()
                }
            }
        }

        // Quick prompts
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            items(quickPrompts) { prompt ->
                AssistChip(
                    onClick = { viewModel.sendMessage(prompt) },
                    label = { Text(prompt, fontSize = 12.sp) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = FitCoachColors.Card,
                        labelColor = FitCoachColors.TextSecondary
                    ),
                    border = AssistChipDefaults.assistChipBorder(true, borderColor = FitCoachColors.Border)
                )
            }
        }

        // Input
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(FitCoachColors.Surface)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Напиши тренеру...", color = FitCoachColors.TextMuted) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = FitCoachColors.TextPrimary,
                    unfocusedTextColor = FitCoachColors.TextPrimary,
                    focusedBorderColor = FitCoachColors.Accent,
                    unfocusedBorderColor = FitCoachColors.Border,
                    cursorColor = FitCoachColors.Accent
                ),
                shape = RoundedCornerShape(16.dp),
                maxLines = 4
            )
            IconButton(
                onClick = {
                    if (input.isNotBlank()) {
                        viewModel.sendMessage(input.trim())
                        input = ""
                    }
                },
                enabled = input.isNotBlank() && !state.isTyping,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (input.isNotBlank()) FitCoachColors.Accent else FitCoachColors.Border)
            ) {
                Icon(Icons.Default.Send, contentDescription = "Отправить",
                    tint = if (input.isNotBlank()) FitCoachColors.Background else FitCoachColors.TextMuted)
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.isUser

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(FitCoachColors.Accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) { Text("🤖", fontSize = 16.sp) }
            Spacer(Modifier.width(8.dp))
        }

        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = if (isUser) 16.dp else 4.dp,
                        topEnd = if (isUser) 4.dp else 16.dp,
                        bottomStart = 16.dp,
                        bottomEnd = 16.dp
                    )
                )
                .background(if (isUser) FitCoachColors.Accent else FitCoachColors.Card)
                .padding(12.dp)
        ) {
            Text(
                text = message.content,
                color = if (isUser) FitCoachColors.Background else FitCoachColors.TextPrimary,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun TypingIndicator() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(FitCoachColors.Accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) { Text("🤖", fontSize = 16.sp) }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(FitCoachColors.Card)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = FitCoachColors.Accent,
                strokeWidth = 2.dp
            )
        }
    }
}
