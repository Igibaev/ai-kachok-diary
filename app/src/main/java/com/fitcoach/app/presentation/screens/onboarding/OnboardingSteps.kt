package com.fitcoach.app.presentation.screens.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitcoach.app.domain.model.Goal
import com.fitcoach.app.domain.model.Level
import com.fitcoach.app.domain.model.Restriction
import com.fitcoach.app.domain.model.Sex
import com.fitcoach.app.presentation.components.SelectableChip
import com.fitcoach.app.presentation.theme.FitCoachColors

typealias DraftUpdate = ((OnboardingDraft) -> OnboardingDraft) -> Unit

@Composable
internal fun Hint(text: String) = Text(text, fontSize = 14.sp, color = FitCoachColors.TextSecondary)

@Composable
internal fun OnboardingField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    suffix: String? = null,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label, color = FitCoachColors.TextMuted) },
        suffix = suffix?.let { { Text(it, color = FitCoachColors.TextMuted) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = FitCoachColors.TextPrimary, unfocusedTextColor = FitCoachColors.TextPrimary,
            focusedBorderColor = FitCoachColors.Accent, unfocusedBorderColor = FitCoachColors.Border,
            cursorColor = FitCoachColors.Accent
        )
    )
}

@Composable
fun LanguageStep(draft: OnboardingDraft, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Hint("Выбери язык приложения. Тілді таңдаңыз.")
        SelectableChip("Русский", draft.language == "ru", { onSelect("ru") }, Modifier.fillMaxWidth(), leading = "🇷🇺")
        SelectableChip("Қазақша", draft.language == "kk", { onSelect("kk") }, Modifier.fillMaxWidth(), leading = "🇰🇿")
        Spacer(Modifier.height(8.dp))
        Text("AI-тренер отвечает на выбранном языке. Интерфейс на казахском появится в ближайшем обновлении.", fontSize = 12.sp, color = FitCoachColors.TextMuted)
    }
}

@Composable
fun NameStep(draft: OnboardingDraft, update: DraftUpdate) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Hint("Так к тебе будет обращаться тренер.")
        OnboardingField("Имя", draft.name, { v -> update { it.copy(name = v.take(30)) } })
    }
}

@Composable
fun SexAgeStep(draft: OnboardingDraft, update: DraftUpdate) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Hint("Нужно для расчёта калорий и подбора нагрузки.")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SelectableChip("Мужской", draft.sex == Sex.MALE, { update { it.copy(sex = Sex.MALE) } }, Modifier.weight(1f), leading = "👨")
            SelectableChip("Женский", draft.sex == Sex.FEMALE, { update { it.copy(sex = Sex.FEMALE) } }, Modifier.weight(1f), leading = "👩")
        }
        OnboardingField("Возраст", draft.age, { v -> update { it.copy(age = v.filter { c -> c.isDigit() }.take(2)) } }, KeyboardType.Number, "лет")
    }
}

@Composable
fun BodyStep(draft: OnboardingDraft, update: DraftUpdate) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Hint("Целевой вес — по желанию, поможет отслеживать прогресс.")
        OnboardingField("Рост", draft.heightCm, { v -> update { it.copy(heightCm = v.filter { c -> c.isDigit() }.take(3)) } }, KeyboardType.Number, "см")
        OnboardingField("Вес", draft.weightKg, { v -> update { it.copy(weightKg = v.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(5)) } }, KeyboardType.Decimal, "кг")
        OnboardingField("Целевой вес (необязательно)", draft.targetWeightKg, { v -> update { it.copy(targetWeightKg = v.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(5)) } }, KeyboardType.Decimal, "кг")
    }
}

@Composable
fun GoalStep(draft: OnboardingDraft, update: DraftUpdate) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Hint("От цели зависят программа и калории.")
        Goal.entries.forEach { g ->
            SelectableChip(g.title, draft.goal == g, { update { it.copy(goal = g) } }, Modifier.fillMaxWidth(), leading = g.emoji)
        }
    }
}

@Composable
fun LevelStep(draft: OnboardingDraft, update: DraftUpdate) {
    val descriptions = mapOf(
        Level.BEGINNER to "Меньше 6 месяцев в зале или после долгого перерыва",
        Level.INTERMEDIATE to "Регулярно тренируюсь 6–24 месяца",
        Level.ADVANCED to "Больше 2 лет, знаю технику базовых движений"
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Hint("Честно — так программа будет безопаснее.")
        Level.entries.forEach { l ->
            SelectableChip("${l.title} — ${descriptions[l]}", draft.level == l, { update { it.copy(level = l) } }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun DaysStep(draft: OnboardingDraft, update: DraftUpdate) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Hint("График гибкий: тренировки идут по порядку, а не по дням недели. Пропустил — просто продолжай.")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SelectableChip("3 дня", draft.daysPerWeek == 3, { update { it.copy(daysPerWeek = 3) } }, Modifier.weight(1f), leading = "🗓️")
            SelectableChip("4 дня", draft.daysPerWeek == 4, { update { it.copy(daysPerWeek = 4) } }, Modifier.weight(1f), leading = "🔥")
        }
    }
}

@Composable
fun RestrictionsStep(draft: OnboardingDraft, toggle: (Restriction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Hint("Можно выбрать несколько или ничего. Упражнения с риском заменим автоматически.")
        Restriction.entries.forEach { r ->
            SelectableChip(r.title, r in draft.restrictions, { toggle(r) }, Modifier.fillMaxWidth(), leading = if (r in draft.restrictions) "✅" else "⬜")
        }
    }
}
