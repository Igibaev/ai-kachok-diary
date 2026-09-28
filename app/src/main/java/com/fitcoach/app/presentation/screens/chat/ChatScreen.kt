package com.fitcoach.app.presentation.screens.chat

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
import com.fitcoach.app.R
import com.fitcoach.app.ai.AiMode
import com.fitcoach.app.presentation.theme.FitCoachColors

@Composable
fun ChatScreen(
    onBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { viewModel.refreshMode() }

    LaunchedEffect(state.messages.size, state.isTyping) {
        val count = state.messages.size + if (state.isTyping) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = FitCoachColors.Card,
            title = { Text(stringResource(R.string.chat_clear_title), color = FitCoachColors.TextPrimary) },
            text = { Text(stringResource(R.string.chat_clear_text), color = FitCoachColors.TextSecondary) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearHistory(); confirmClear = false }) {
                    Text(stringResource(R.string.chat_clear_confirm), color = FitCoachColors.Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.common_cancel), color = FitCoachColors.TextSecondary) }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FitCoachColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        ChatTopBar(
            title = state.coachName,
            mode = state.mode,
            trainerWhatsappUrl = remember { trainerWhatsappUrl(context) },
            onBack = onBack,
            onAskTrainer = { url ->
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            },
            onClear = { confirmClear = true }
        )

        if (state.mode == AiMode.DEMO) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FitCoachColors.Warning.copy(alpha = 0.10f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    stringResource(R.string.chat_demo_banner),
                    fontSize = 12.sp,
                    color = FitCoachColors.Warning
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            if (state.messages.isEmpty() && !state.isTyping) {
                item { EmptyChatHint(state.coachName) }
            }
            items(state.messages, key = { it.id }) { message -> MessageBubble(message) }
            if (state.isTyping) {
                item(key = "typing") { TypingIndicator() }
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            items(state.quickPrompts) { promptRes ->
                val prompt = stringResource(promptRes)
                AssistChip(
                    onClick = { viewModel.sendMessage(prompt) },
                    enabled = !state.isTyping,
                    label = { Text(prompt, fontSize = 12.sp) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = FitCoachColors.Card,
                        labelColor = FitCoachColors.TextSecondary,
                        disabledContainerColor = FitCoachColors.Card,
                        disabledLabelColor = FitCoachColors.TextMuted
                    ),
                    border = AssistChipDefaults.assistChipBorder(true, borderColor = FitCoachColors.Border)
                )
            }
        }

        ChatInputBar(
            value = input,
            enabled = !state.isTyping,
            onValueChange = { input = it },
            onSend = {
                if (input.isNotBlank()) {
                    viewModel.sendMessage(input)
                    input = ""
                }
            }
        )
    }
}

@Composable
private fun ChatTopBar(
    title: String,
    mode: AiMode,
    trainerWhatsappUrl: String?,
    onBack: () -> Unit,
    onAskTrainer: (String) -> Unit,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(FitCoachColors.Surface)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back), tint = FitCoachColors.TextPrimary)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = FitCoachColors.TextPrimary
            )
            Spacer(Modifier.height(2.dp))
            AiModeChip(mode)
        }
        if (trainerWhatsappUrl != null) {
            IconButton(onClick = { onAskTrainer(trainerWhatsappUrl) }) {
                Icon(Icons.Default.SupportAgent, contentDescription = stringResource(R.string.chat_ask_trainer), tint = FitCoachColors.Accent)
            }
        }
        IconButton(onClick = onClear) {
            Icon(Icons.Default.DeleteSweep, contentDescription = stringResource(R.string.chat_clear), tint = FitCoachColors.TextMuted)
        }
    }
}

@Composable
private fun EmptyChatHint(coachName: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🏋️", fontSize = 40.sp)
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.chat_empty_title, coachName),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = FitCoachColors.TextPrimary
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.chat_empty_text),
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = FitCoachColors.TextSecondary,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Composable
private fun ChatInputBar(
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit
) {
    val canSend = value.isNotBlank() && enabled
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(FitCoachColors.Surface)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.chat_input_placeholder), color = FitCoachColors.TextMuted) },
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
            onClick = onSend,
            enabled = canSend,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(if (canSend) FitCoachColors.Accent else FitCoachColors.Border)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = stringResource(R.string.chat_send),
                tint = if (canSend) FitCoachColors.AccentOn else FitCoachColors.TextMuted
            )
        }
    }
}
