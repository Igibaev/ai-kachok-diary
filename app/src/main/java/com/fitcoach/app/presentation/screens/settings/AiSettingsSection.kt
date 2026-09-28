package com.fitcoach.app.presentation.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitcoach.app.BuildConfig
import com.fitcoach.app.ai.AiClientSelector
import com.fitcoach.app.ai.AiMode
import com.fitcoach.app.brand.BrandConfig
import com.fitcoach.app.domain.repository.ChatRepository
import com.fitcoach.app.domain.repository.UserRepository
import com.fitcoach.app.presentation.components.FitCard
import com.fitcoach.app.presentation.screens.chat.AiModeChip
import com.fitcoach.app.presentation.theme.FitCoachColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AiSettingsUiState(
    val mode: AiMode = AiMode.DEMO,
    val forceDemo: Boolean = false,
    val apiKey: String = "",
    val hasProxy: Boolean = BrandConfig.hasAiProxy,
    val isDebug: Boolean = BuildConfig.DEBUG,
    val model: String = BrandConfig.aiModel
)

@HiltViewModel
class AiSettingsViewModel @Inject constructor(
    private val selector: AiClientSelector,
    private val userRepo: UserRepository,
    private val chatRepo: ChatRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AiSettingsUiState())
    val state = _state.asStateFlow()

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        _state.update {
            it.copy(
                mode = selector.currentMode(),
                forceDemo = selector.isForceDemo(),
                apiKey = if (BuildConfig.DEBUG) userRepo.getApiKey() else ""
            )
        }
    }

    fun setForceDemo(value: Boolean) = viewModelScope.launch {
        selector.setForceDemo(value)
        refresh()
    }

    fun saveApiKey(key: String) = viewModelScope.launch {
        if (!BuildConfig.DEBUG) return@launch
        userRepo.saveApiKey(key)
        refresh()
    }

    fun clearChat() = viewModelScope.launch { chatRepo.clearAll() }
}

/** Секция настроек AI-тренера: режим, демо-переключатель, ключ разработчика (DEBUG), очистка чата. */
@Composable
fun AiSettingsSection(viewModel: AiSettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var keyInput by remember(state.apiKey) { mutableStateOf(state.apiKey) }
    var keyVisible by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refresh() }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = FitCoachColors.Card,
            title = { Text("Очистить историю чата?", color = FitCoachColors.TextPrimary) },
            text = { Text("Все сообщения с AI-тренером будут удалены с этого устройства.", color = FitCoachColors.TextSecondary) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearChat(); confirmClear = false }) {
                    Text("Очистить", color = FitCoachColors.Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Отмена", color = FitCoachColors.TextSecondary) }
            }
        )
    }

    FitCard {
        Text("AI-ТРЕНЕР", style = MaterialTheme.typography.labelSmall, color = FitCoachColors.TextMuted)
        Spacer(Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Режим", color = FitCoachColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(
                    text = when (state.mode) {
                        AiMode.DEMO -> "Ответы подготовлены заранее, интернет не нужен"
                        AiMode.CLUB -> "Ответы через AI-сервис клуба"
                        AiMode.DEVELOPER -> "Прямое подключение, ключ разработчика · ${state.model}"
                    },
                    color = FitCoachColors.TextSecondary,
                    fontSize = 12.sp
                )
            }
            AiModeChip(state.mode)
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = FitCoachColors.Border)
        Spacer(Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Демо-режим AI (офлайн)", color = FitCoachColors.TextPrimary, fontSize = 15.sp)
                Text(
                    text = if (state.hasProxy || state.isDebug) "Принудительно использовать офлайн-ответы"
                    else "AI-сервис клуба не настроен — доступен только демо-режим",
                    color = FitCoachColors.TextSecondary,
                    fontSize = 12.sp
                )
            }
            Switch(
                checked = state.forceDemo,
                onCheckedChange = { viewModel.setForceDemo(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = FitCoachColors.AccentOn,
                    checkedTrackColor = FitCoachColors.Accent,
                    uncheckedTrackColor = FitCoachColors.Border
                )
            )
        }

        if (state.isDebug) {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = FitCoachColors.Border)
            Spacer(Modifier.height(12.dp))
            Text("Ключ разработчика (только debug)", color = FitCoachColors.TextPrimary, fontSize = 15.sp)
            Text(
                "Anthropic API key для прямого режима. Хранится в зашифрованном виде и не попадает в релизные сборки.",
                color = FitCoachColors.TextSecondary,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = keyInput,
                onValueChange = { keyInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("sk-ant-…", color = FitCoachColors.TextMuted, fontSize = 12.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { keyVisible = !keyVisible }) {
                        Icon(
                            if (keyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (keyVisible) "Скрыть" else "Показать",
                            tint = FitCoachColors.TextMuted
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = FitCoachColors.TextPrimary,
                    unfocusedTextColor = FitCoachColors.TextPrimary,
                    focusedBorderColor = FitCoachColors.Accent,
                    unfocusedBorderColor = FitCoachColors.Border
                ),
                shape = RoundedCornerShape(12.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                if (state.apiKey.isNotBlank()) {
                    TextButton(onClick = { keyInput = ""; viewModel.saveApiKey("") }) {
                        Text("Удалить ключ", color = FitCoachColors.Error)
                    }
                }
                TextButton(
                    onClick = { viewModel.saveApiKey(keyInput.trim()) },
                    enabled = keyInput.trim() != state.apiKey
                ) {
                    Text("Сохранить ключ", color = FitCoachColors.Accent)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = FitCoachColors.Border)
        TextButton(onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Очистить историю чата", color = FitCoachColors.TextSecondary)
        }
    }
}
