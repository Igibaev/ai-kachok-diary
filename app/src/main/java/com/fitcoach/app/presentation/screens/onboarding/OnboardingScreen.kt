package com.fitcoach.app.presentation.screens.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fitcoach.app.brand.BrandConfig
import com.fitcoach.app.presentation.components.PrimaryButton
import com.fitcoach.app.presentation.theme.FitCoachColors

@Composable
fun OnboardingScreen(onFinished: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val step = state.step

    BackHandler(enabled = step.ordinal > 0) { viewModel.back() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FitCoachColors.Background)
            .statusBarsPadding()
            .imePadding()
    ) {
        // Верх: назад + прогресс
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = viewModel::back, enabled = step.ordinal > 0) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад",
                    tint = if (step.ordinal > 0) FitCoachColors.TextPrimary else FitCoachColors.Background
                )
            }
            LinearProgressIndicator(
                progress = { step.progress },
                modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = FitCoachColors.Accent,
                trackColor = FitCoachColors.Border
            )
            Text(
                "${step.ordinal + 1}/${OnboardingStep.entries.size}",
                fontSize = 12.sp, color = FitCoachColors.TextMuted,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            if (step == OnboardingStep.LANGUAGE) {
                Text(BrandConfig.appName, style = MaterialTheme.typography.labelSmall, color = FitCoachColors.Accent)
                Spacer(Modifier.height(4.dp))
            }
            Text(step.title, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = FitCoachColors.TextPrimary)
            Spacer(Modifier.height(20.dp))

            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val forward = targetState.ordinal >= initialState.ordinal
                    (slideInHorizontally { if (forward) it / 3 else -it / 3 } + fadeIn()) togetherWith
                        (slideOutHorizontally { if (forward) -it / 3 else it / 3 } + fadeOut())
                },
                label = "onboarding_step"
            ) { current ->
                when (current) {
                    OnboardingStep.LANGUAGE -> LanguageStep(state.draft, viewModel::selectLanguage)
                    OnboardingStep.NAME -> NameStep(state.draft, viewModel::update)
                    OnboardingStep.SEX_AGE -> SexAgeStep(state.draft, viewModel::update)
                    OnboardingStep.BODY -> BodyStep(state.draft, viewModel::update)
                    OnboardingStep.GOAL -> GoalStep(state.draft, viewModel::update)
                    OnboardingStep.LEVEL -> LevelStep(state.draft, viewModel::update)
                    OnboardingStep.DAYS -> DaysStep(state.draft, viewModel::update)
                    OnboardingStep.RESTRICTIONS -> RestrictionsStep(state.draft, viewModel::toggleRestriction)
                    OnboardingStep.RESULT -> ResultStep(state, onConsent = { c -> viewModel.update { it.copy(consent = c) } })
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        Box(Modifier.padding(horizontal = 24.dp, vertical = 16.dp).navigationBarsPadding()) {
            if (step == OnboardingStep.RESULT) {
                PrimaryButton(
                    text = if (state.saving) "Сохраняем…" else "Начать путь",
                    enabled = state.canProceed && !state.saving,
                    onClick = { viewModel.finish(onFinished) }
                )
            } else {
                PrimaryButton(text = "Далее", enabled = state.canProceed, onClick = viewModel::next)
            }
        }
    }
}
