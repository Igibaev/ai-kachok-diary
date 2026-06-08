package com.fitcoach.app.presentation.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.fitcoach.app.domain.model.UserProfile
import com.fitcoach.app.domain.repository.UserRepository
import com.fitcoach.app.presentation.components.FitCard
import com.fitcoach.app.presentation.theme.FitCoachColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userRepo: UserRepository
) : ViewModel() {

    val profile = userRepo.observeProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _apiKey = MutableStateFlow("")
    val apiKey = _apiKey.asStateFlow()

    init {
        viewModelScope.launch {
            _apiKey.value = userRepo.getApiKey()
        }
    }

    fun saveProfile(profile: UserProfile) = viewModelScope.launch { userRepo.saveProfile(profile) }
    fun saveApiKey(key: String) = viewModelScope.launch {
        userRepo.saveApiKey(key)
        _apiKey.value = key
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val profile by viewModel.profile.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()

    var name by remember(profile) { mutableStateOf(profile?.name ?: "") }
    var age by remember(profile) { mutableStateOf(profile?.age?.toString() ?: "29") }
    var weight by remember(profile) { mutableStateOf(profile?.weightKg?.toString() ?: "103") }
    var height by remember(profile) { mutableStateOf(profile?.heightCm?.toString() ?: "185") }
    var targetWeight by remember(profile) { mutableStateOf(profile?.targetWeightKg?.toString() ?: "") }
    var waterGoal by remember(profile) { mutableStateOf(profile?.waterGoalMl?.toString() ?: "2500") }
    var calorieGoal by remember(profile) { mutableStateOf(profile?.calorieGoal?.toString() ?: "2100") }
    var proteinGoal by remember(profile) { mutableStateOf(profile?.proteinGoal?.toString() ?: "190") }
    var carbsGoal by remember(profile) { mutableStateOf(profile?.carbsGoal?.toString() ?: "195") }
    var fatGoal by remember(profile) { mutableStateOf(profile?.fatGoal?.toString() ?: "60") }
    var apiKeyInput by remember(apiKey) { mutableStateOf(apiKey) }
    var apiKeyVisible by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = FitCoachColors.Background,
        topBar = {
            TopAppBar(
                title = { Text("Настройки", color = FitCoachColors.TextPrimary) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = FitCoachColors.TextPrimary) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = FitCoachColors.Surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Profile
            FitCard {
                Text("ПРОФИЛЬ", style = MaterialTheme.typography.labelSmall, color = FitCoachColors.TextMuted)
                Spacer(Modifier.height(12.dp))
                SettingsTextField("Имя", name) { name = it }
                SettingsTextField("Возраст", age, KeyboardType.Number) { age = it }
                SettingsTextField("Вес (кг)", weight, KeyboardType.Decimal) { weight = it }
                SettingsTextField("Рост (см)", height, KeyboardType.Number) { height = it }
                SettingsTextField("Целевой вес (кг)", targetWeight, KeyboardType.Decimal) { targetWeight = it }
            }

            // Goals
            FitCard {
                Text("ЦЕЛИ", style = MaterialTheme.typography.labelSmall, color = FitCoachColors.TextMuted)
                Spacer(Modifier.height(12.dp))
                SettingsTextField("Цель воды (мл/день)", waterGoal, KeyboardType.Number) { waterGoal = it }
                SettingsTextField("Калории (ккал/день)", calorieGoal, KeyboardType.Number) { calorieGoal = it }
                SettingsTextField("Белок (г/день)", proteinGoal, KeyboardType.Number) { proteinGoal = it }
                SettingsTextField("Углеводы (г/день)", carbsGoal, KeyboardType.Number) { carbsGoal = it }
                SettingsTextField("Жиры (г/день)", fatGoal, KeyboardType.Number) { fatGoal = it }
            }

            // API Key
            FitCard {
                Text("ANTHROPIC API КЛЮЧ", style = MaterialTheme.typography.labelSmall, color = FitCoachColors.TextMuted)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("sk-ant-...", color = FitCoachColors.TextMuted) },
                    visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                            Icon(if (apiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = FitCoachColors.TextMuted)
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = FitCoachColors.TextPrimary,
                        unfocusedTextColor = FitCoachColors.TextPrimary,
                        focusedBorderColor = FitCoachColors.Accent,
                        unfocusedBorderColor = FitCoachColors.Border
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Text("Ключ хранится зашифрованно на устройстве", fontSize = 11.sp, color = FitCoachColors.TextMuted)
            }

            // Save button
            Button(
                onClick = {
                    val p = (profile ?: UserProfile()).copy(
                        name = name,
                        age = age.toIntOrNull() ?: 29,
                        weightKg = weight.toFloatOrNull() ?: 103f,
                        heightCm = height.toIntOrNull() ?: 185,
                        targetWeightKg = targetWeight.toFloatOrNull(),
                        waterGoalMl = waterGoal.toIntOrNull() ?: 2500,
                        calorieGoal = calorieGoal.toIntOrNull() ?: 2100,
                        proteinGoal = proteinGoal.toIntOrNull() ?: 190,
                        carbsGoal = carbsGoal.toIntOrNull() ?: 195,
                        fatGoal = fatGoal.toIntOrNull() ?: 60
                    )
                    viewModel.saveProfile(p)
                    viewModel.saveApiKey(apiKeyInput)
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = FitCoachColors.Accent),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Сохранить", color = FitCoachColors.Background, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun SettingsTextField(
    label: String,
    value: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        label = { Text(label, color = FitCoachColors.TextMuted, fontSize = 12.sp) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = FitCoachColors.TextPrimary,
            unfocusedTextColor = FitCoachColors.TextPrimary,
            focusedBorderColor = FitCoachColors.Accent,
            unfocusedBorderColor = FitCoachColors.Border
        ),
        shape = RoundedCornerShape(12.dp),
        singleLine = true
    )
}
