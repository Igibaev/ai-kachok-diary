package com.fitcoach.app.presentation.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fitcoach.app.BuildConfig
import com.fitcoach.app.brand.BrandConfig
import com.fitcoach.app.domain.model.Goal
import com.fitcoach.app.domain.model.Level
import com.fitcoach.app.domain.model.Restriction
import com.fitcoach.app.domain.model.Sex
import com.fitcoach.app.presentation.components.FitCard
import com.fitcoach.app.presentation.components.PrimaryButton
import com.fitcoach.app.presentation.components.SectionLabel
import com.fitcoach.app.presentation.components.SelectableChip
import com.fitcoach.app.presentation.theme.FitCoachColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onRestartOnboarding: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val form = state.form
    val snackbar = remember { SnackbarHostState() }
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); viewModel.consumeMessage() }
    }

    Scaffold(
        containerColor = FitCoachColors.Background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Настройки", color = FitCoachColors.TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = FitCoachColors.TextPrimary) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = FitCoachColors.Surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Профиль
            FitCard {
                SectionLabel("Профиль")
                Spacer(Modifier.height(12.dp))
                SettingsTextField("Имя", form.name) { v -> viewModel.edit { it.copy(name = v) } }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    SelectableChip("Мужской", form.sex == Sex.MALE, { viewModel.edit { it.copy(sex = Sex.MALE) } }, Modifier.weight(1f))
                    SelectableChip("Женский", form.sex == Sex.FEMALE, { viewModel.edit { it.copy(sex = Sex.FEMALE) } }, Modifier.weight(1f))
                }
                SettingsTextField("Возраст", form.age, KeyboardType.Number) { v -> viewModel.edit { it.copy(age = v) } }
                SettingsTextField("Рост (см)", form.heightCm, KeyboardType.Number) { v -> viewModel.edit { it.copy(heightCm = v) } }
                SettingsTextField("Вес (кг)", form.weightKg, KeyboardType.Decimal) { v -> viewModel.edit { it.copy(weightKg = v) } }
                SettingsTextField("Целевой вес (кг)", form.targetWeightKg, KeyboardType.Decimal) { v -> viewModel.edit { it.copy(targetWeightKg = v) } }
            }

            // Тренировки
            FitCard {
                SectionLabel("Цель и уровень")
                Spacer(Modifier.height(10.dp))
                ChipGrid(Goal.entries.map { it.emoji + " " + it.title to (form.goal == it) }) { i -> viewModel.edit { it.copy(goal = Goal.entries[i]) } }
                Spacer(Modifier.height(10.dp))
                ChipGrid(Level.entries.map { it.title to (form.level == it) }) { i -> viewModel.edit { it.copy(level = Level.entries[i]) } }
                Spacer(Modifier.height(10.dp))
                Text("Дней в неделю", fontSize = 13.sp, color = FitCoachColors.TextSecondary)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectableChip("3", form.daysPerWeek == 3, { viewModel.edit { it.copy(daysPerWeek = 3) } }, Modifier.weight(1f))
                    SelectableChip("4", form.daysPerWeek == 4, { viewModel.edit { it.copy(daysPerWeek = 4) } }, Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Text("Ограничения", fontSize = 13.sp, color = FitCoachColors.TextSecondary)
                Spacer(Modifier.height(6.dp))
                Restriction.entries.forEach { r ->
                    SelectableChip(r.title, r in form.restrictions, { viewModel.toggleRestriction(r) }, Modifier.fillMaxWidth().padding(bottom = 6.dp),
                        leading = if (r in form.restrictions) "✅" else "⬜")
                }
            }

            // Цели питания
            FitCard {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    SectionLabel("Цели питания", Modifier.weight(1f))
                    TextButton(onClick = viewModel::recalculateGoals) { Text("Пересчитать цели", color = FitCoachColors.Accent, fontSize = 13.sp) }
                }
                Spacer(Modifier.height(8.dp))
                SettingsTextField("Вода (мл/день)", form.waterGoal, KeyboardType.Number) { v -> viewModel.edit { it.copy(waterGoal = v) } }
                SettingsTextField("Калории (ккал/день)", form.calorieGoal, KeyboardType.Number) { v -> viewModel.edit { it.copy(calorieGoal = v) } }
                SettingsTextField("Белок (г/день)", form.proteinGoal, KeyboardType.Number) { v -> viewModel.edit { it.copy(proteinGoal = v) } }
                SettingsTextField("Углеводы (г/день)", form.carbsGoal, KeyboardType.Number) { v -> viewModel.edit { it.copy(carbsGoal = v) } }
                SettingsTextField("Жиры (г/день)", form.fatGoal, KeyboardType.Number) { v -> viewModel.edit { it.copy(fatGoal = v) } }
            }

            PrimaryButton(text = "Сохранить", onClick = viewModel::save, enabled = state.loaded)

            // Язык
            FitCard {
                SectionLabel("Язык / Тіл")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectableChip("Русский", form.language == "ru", { viewModel.setLanguage("ru") }, Modifier.weight(1f), leading = "🇷🇺")
                    SelectableChip("Қазақша", form.language == "kk", { viewModel.setLanguage("kk") }, Modifier.weight(1f), leading = "🇰🇿")
                }
            }

            AiSettingsSection()

            // Демо-данные
            FitCard {
                SectionLabel("Демо-данные")
                Spacer(Modifier.height(6.dp))
                Text("Для презентации: 3 недели тренировок, воды, питания и замеров. Профиль не меняется.", fontSize = 12.sp, color = FitCoachColors.TextMuted)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = viewModel::seedDemo, enabled = !state.busy, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = FitCoachColors.AccentSoft, contentColor = FitCoachColors.Accent),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Загрузить 3 недели") }
                    OutlinedButton(
                        onClick = { confirmClear = true }, enabled = !state.busy, modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, FitCoachColors.Error.copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = FitCoachColors.Error),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Очистить всё") }
                }
                if (state.busy) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = FitCoachColors.Accent, trackColor = FitCoachColors.Border)
                }
            }

            OutlinedButton(
                onClick = { viewModel.restartOnboarding(onRestartOnboarding) },
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, FitCoachColors.Border),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = FitCoachColors.TextSecondary),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Пройти онбординг заново") }

            Text(
                "${BrandConfig.appName} · версия ${BuildConfig.VERSION_NAME} · бренд ${BrandConfig.brandId}",
                fontSize = 11.sp, color = FitCoachColors.TextMuted,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            )
            Spacer(Modifier.height(64.dp))
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = FitCoachColors.Card,
            title = { Text("Очистить все данные?", color = FitCoachColors.TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("Тренировки, вода, питание, замеры и чат будут удалены. Профиль останется.", color = FitCoachColors.TextSecondary) },
            confirmButton = {
                Button(
                    onClick = { confirmClear = false; viewModel.clearAll() },
                    colors = ButtonDefaults.buttonColors(containerColor = FitCoachColors.Error, contentColor = FitCoachColors.TextPrimary)
                ) { Text("Очистить") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Отмена", color = FitCoachColors.TextMuted) } }
        )
    }
}

@Composable
private fun ChipGrid(items: List<Pair<String, Boolean>>, onClick: (Int) -> Unit) {
    items.chunked(2).forEachIndexed { rowIdx, row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 6.dp)) {
            row.forEachIndexed { i, (text, selected) ->
                SelectableChip(text, selected, { onClick(rowIdx * 2 + i) }, Modifier.weight(1f))
            }
            if (row.size < 2) Spacer(Modifier.weight(1f))
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
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = FitCoachColors.TextPrimary,
            unfocusedTextColor = FitCoachColors.TextPrimary,
            focusedBorderColor = FitCoachColors.Accent,
            unfocusedBorderColor = FitCoachColors.Border,
            cursorColor = FitCoachColors.Accent
        ),
        shape = RoundedCornerShape(12.dp),
        singleLine = true
    )
}
