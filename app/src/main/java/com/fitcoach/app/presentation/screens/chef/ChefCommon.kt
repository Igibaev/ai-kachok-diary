package com.fitcoach.app.presentation.screens.chef

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitcoach.app.R
import com.fitcoach.app.domain.model.MealType
import com.fitcoach.app.l10n.tr
import com.fitcoach.app.presentation.components.FitCard
import com.fitcoach.app.presentation.components.PrimaryButton
import com.fitcoach.app.presentation.components.SelectableChip
import com.fitcoach.app.presentation.theme.FitCoachColors

/** Общие элементы экранов AI-повара: шапка, пустое состояние, прогресс, ошибка, бейдж «демо», выбор приёма пищи. */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChefTopBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title, color = FitCoachColors.TextPrimary, fontWeight = FontWeight.Bold) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back), tint = FitCoachColors.TextPrimary)
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = FitCoachColors.Surface)
    )
}

@Composable
fun ChefEmptyState(emoji: String, title: String, text: String, buttonText: String, onClick: () -> Unit) {
    FitCard {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, fontSize = 48.sp)
            Spacer(Modifier.height(12.dp))
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = FitCoachColors.TextPrimary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(text, fontSize = 14.sp, color = FitCoachColors.TextSecondary, textAlign = TextAlign.Center, lineHeight = 20.sp)
            Spacer(Modifier.height(16.dp))
            PrimaryButton(text = buttonText, onClick = onClick)
        }
    }
}

@Composable
fun ChefProgressCard(title: String, text: String, onCancel: (() -> Unit)? = null) {
    FitCard {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = FitCoachColors.Accent, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(16.dp))
            Text(title, fontWeight = FontWeight.SemiBold, color = FitCoachColors.TextPrimary, textAlign = TextAlign.Center)
            if (text.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(text, fontSize = 13.sp, color = FitCoachColors.TextSecondary, textAlign = TextAlign.Center, lineHeight = 18.sp)
            }
            if (onCancel != null) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onCancel) { Text(stringResource(R.string.common_cancel), color = FitCoachColors.TextMuted) }
            }
        }
    }
}

@Composable
fun ChefErrorCard(title: String, message: String, onRetry: () -> Unit, onDismiss: () -> Unit) {
    FitCard {
        Text(title, fontWeight = FontWeight.SemiBold, color = FitCoachColors.Error)
        Spacer(Modifier.height(6.dp))
        Text(message, fontSize = 14.sp, color = FitCoachColors.TextSecondary, lineHeight = 20.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onRetry) { Text(stringResource(R.string.common_retry), color = FitCoachColors.Accent) }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = FitCoachColors.TextMuted) }
        }
    }
}

@Composable
fun DemoBadge(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = FitCoachColors.Warning,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(FitCoachColors.Warning.copy(alpha = 0.15f))
            .border(1.dp, FitCoachColors.Warning.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

/** Чипы выбора приёма пищи дневника (без TEMPLATE). */
@Composable
fun MealTypeChips(selected: MealType, onSelect: (MealType) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MealType.entries.filter { it != MealType.TEMPLATE }.forEach { type ->
            SelectableChip(text = type.displayName.tr(), selected = type == selected, onClick = { onSelect(type) }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun chefTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = FitCoachColors.TextPrimary,
    unfocusedTextColor = FitCoachColors.TextPrimary,
    focusedBorderColor = FitCoachColors.Accent,
    unfocusedBorderColor = FitCoachColors.Border,
    cursorColor = FitCoachColors.Accent
)
