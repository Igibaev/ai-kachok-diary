package com.fitcoach.app.presentation.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitcoach.app.R
import com.fitcoach.app.ai.AiMode
import com.fitcoach.app.domain.model.ChatMessage
import com.fitcoach.app.presentation.theme.FitCoachColors

@Composable
fun MessageBubble(message: ChatMessage) {
    when {
        message.isError -> SystemBubble(message.content)
        message.isUser -> UserBubble(message.content)
        else -> AssistantBubble(message.content)
    }
}

@Composable
private fun UserBubble(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp))
                .background(FitCoachColors.Accent)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(text = text, color = FitCoachColors.AccentOn, fontSize = 14.sp, lineHeight = 20.sp)
        }
    }
}

@Composable
private fun AssistantBubble(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        CoachAvatar()
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp))
                .background(FitCoachColors.Card)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            MarkdownText(text = text, color = FitCoachColors.TextPrimary)
        }
    }
}

/** Приглушённый системный пузырь для ошибок: по центру, без аватара, не выглядит как ответ тренера. */
@Composable
private fun SystemBubble(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(FitCoachColors.Surface)
                .border(1.dp, FitCoachColors.Border, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = text,
                color = FitCoachColors.TextMuted,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
fun MarkdownText(text: String, color: Color) {
    val blocks = remember(text) { ChatMarkdown.parse(text) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEach { block ->
            when (block) {
                is ChatBlock.Paragraph -> Text(
                    text = ChatMarkdown.inline(block.text),
                    color = color, fontSize = 14.sp, lineHeight = 20.sp
                )
                is ChatBlock.Heading -> Text(
                    text = ChatMarkdown.plain(block.text),
                    color = color, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold
                )
                is ChatBlock.Bullet -> ListRow(marker = "•", text = block.text, color = color)
                is ChatBlock.Numbered -> ListRow(marker = "${block.number}.", text = block.text, color = color)
            }
        }
    }
}

@Composable
private fun ListRow(marker: String, text: String, color: Color) {
    Row(modifier = Modifier.padding(start = 2.dp)) {
        Text(
            text = marker,
            color = FitCoachColors.Accent,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.widthIn(min = 18.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(text = ChatMarkdown.inline(text), color = color, fontSize = 14.sp, lineHeight = 20.sp)
    }
}

@Composable
fun CoachAvatar() {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(FitCoachColors.AccentSoft),
        contentAlignment = Alignment.Center
    ) { Text("🏋️", fontSize = 16.sp) }
}

@Composable
fun TypingIndicator() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CoachAvatar()
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

/** Чип режима AI в шапке: «Демо-режим» / «AI клуба» / «Разработчик». */
@Composable
fun AiModeChip(mode: AiMode, modifier: Modifier = Modifier) {
    val color = when (mode) {
        AiMode.DEMO -> FitCoachColors.Warning
        AiMode.CLUB -> FitCoachColors.Success
        AiMode.DEVELOPER -> FitCoachColors.Water
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(color))
        Text(text = aiModeLabel(mode), fontSize = 11.sp, fontWeight = FontWeight.Medium, color = color)
    }
}

/** Локализованная подпись режима AI (сам enum живёт в слое ai и хранит русские названия). */
@Composable
fun aiModeLabel(mode: AiMode): String = stringResource(
    when (mode) {
        AiMode.DEMO -> R.string.ai_mode_demo
        AiMode.CLUB -> R.string.ai_mode_club
        AiMode.DEVELOPER -> R.string.ai_mode_developer
    }
)
