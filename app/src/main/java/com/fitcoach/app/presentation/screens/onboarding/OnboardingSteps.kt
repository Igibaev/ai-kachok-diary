package com.fitcoach.app.presentation.screens.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitcoach.app.R
import com.fitcoach.app.domain.model.Goal
import com.fitcoach.app.domain.model.Level
import com.fitcoach.app.domain.model.Restriction
import com.fitcoach.app.domain.model.Sex
import com.fitcoach.app.l10n.tr
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
        Hint(stringResource(R.string.onboarding_language_hint))
        SelectableChip(stringResource(R.string.onboarding_language_ru), draft.language == "ru", { onSelect("ru") }, Modifier.fillMaxWidth(), leading = "🇷🇺")
        SelectableChip(stringResource(R.string.onboarding_language_kk), draft.language == "kk", { onSelect("kk") }, Modifier.fillMaxWidth(), leading = "🇰🇿")
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.onboarding_language_note), fontSize = 12.sp, color = FitCoachColors.TextMuted)
    }
}

@Composable
fun NameStep(draft: OnboardingDraft, update: DraftUpdate) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Hint(stringResource(R.string.onboarding_name_hint))
        OnboardingField(stringResource(R.string.onboarding_name_label), draft.name, { v -> update { it.copy(name = v.take(30)) } })
    }
}

@Composable
fun SexAgeStep(draft: OnboardingDraft, update: DraftUpdate) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Hint(stringResource(R.string.onboarding_sex_age_hint))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SelectableChip(stringResource(R.string.onboarding_sex_male), draft.sex == Sex.MALE, { update { it.copy(sex = Sex.MALE) } }, Modifier.weight(1f), leading = "👨")
            SelectableChip(stringResource(R.string.onboarding_sex_female), draft.sex == Sex.FEMALE, { update { it.copy(sex = Sex.FEMALE) } }, Modifier.weight(1f), leading = "👩")
        }
        OnboardingField(stringResource(R.string.onboarding_age_label), draft.age, { v -> update { it.copy(age = v.filter { c -> c.isDigit() }.take(2)) } }, KeyboardType.Number, stringResource(R.string.unit_years))
    }
}

@Composable
fun BodyStep(draft: OnboardingDraft, update: DraftUpdate) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val kg = stringResource(R.string.unit_kg)
        Hint(stringResource(R.string.onboarding_body_hint))
        OnboardingField(stringResource(R.string.onboarding_height_label), draft.heightCm, { v -> update { it.copy(heightCm = v.filter { c -> c.isDigit() }.take(3)) } }, KeyboardType.Number, stringResource(R.string.unit_cm))
        OnboardingField(stringResource(R.string.onboarding_weight_label), draft.weightKg, { v -> update { it.copy(weightKg = v.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(5)) } }, KeyboardType.Decimal, kg)
        OnboardingField(stringResource(R.string.onboarding_target_weight_label), draft.targetWeightKg, { v -> update { it.copy(targetWeightKg = v.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(5)) } }, KeyboardType.Decimal, kg)
    }
}

@Composable
fun GoalStep(draft: OnboardingDraft, update: DraftUpdate) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Hint(stringResource(R.string.onboarding_goal_hint))
        Goal.entries.forEach { g ->
            SelectableChip(g.title.tr(), draft.goal == g, { update { it.copy(goal = g) } }, Modifier.fillMaxWidth(), leading = g.emoji)
        }
    }
}

@Composable
fun LevelStep(draft: OnboardingDraft, update: DraftUpdate) {
    val descriptions = mapOf(
        Level.BEGINNER to stringResource(R.string.onboarding_level_beginner_desc),
        Level.INTERMEDIATE to stringResource(R.string.onboarding_level_intermediate_desc),
        Level.ADVANCED to stringResource(R.string.onboarding_level_advanced_desc)
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Hint(stringResource(R.string.onboarding_level_hint))
        Level.entries.forEach { l ->
            SelectableChip(stringResource(R.string.onboarding_level_chip, l.title.tr(), descriptions[l] ?: ""), draft.level == l, { update { it.copy(level = l) } }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun DaysStep(draft: OnboardingDraft, update: DraftUpdate) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Hint(stringResource(R.string.onboarding_days_hint))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SelectableChip(stringResource(R.string.onboarding_days_3), draft.daysPerWeek == 3, { update { it.copy(daysPerWeek = 3) } }, Modifier.weight(1f), leading = "🗓️")
            SelectableChip(stringResource(R.string.onboarding_days_4), draft.daysPerWeek == 4, { update { it.copy(daysPerWeek = 4) } }, Modifier.weight(1f), leading = "🔥")
        }
    }
}

@Composable
fun RestrictionsStep(draft: OnboardingDraft, toggle: (Restriction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Hint(stringResource(R.string.onboarding_restrictions_hint))
        Restriction.entries.forEach { r ->
            SelectableChip(r.title.tr(), r in draft.restrictions, { toggle(r) }, Modifier.fillMaxWidth(), leading = if (r in draft.restrictions) "✅" else "⬜")
        }
    }
}
