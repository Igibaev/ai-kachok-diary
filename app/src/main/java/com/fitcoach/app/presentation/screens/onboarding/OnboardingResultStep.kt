package com.fitcoach.app.presentation.screens.onboarding

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitcoach.app.R
import com.fitcoach.app.brand.BrandConfig
import com.fitcoach.app.l10n.tr
import com.fitcoach.app.presentation.components.FitCard
import com.fitcoach.app.presentation.components.SectionLabel
import com.fitcoach.app.presentation.theme.FitCoachColors

@Composable
fun ResultStep(state: OnboardingUiState, onConsent: (Boolean) -> Unit) {
    val context = LocalContext.current
    val goals = state.goals
    val rec = state.recommendation

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Hint(stringResource(R.string.onboarding_result_hint))

        if (goals != null) {
            FitCard {
                SectionLabel(stringResource(R.string.onboarding_result_daily_goals))
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    GoalStat("${goals.calories}", stringResource(R.string.onboarding_result_kcal))
                    GoalStat("${goals.proteinG}", stringResource(R.string.onboarding_result_protein))
                    GoalStat("${goals.carbsG}", stringResource(R.string.onboarding_result_carbs))
                    GoalStat("${goals.fatG}", stringResource(R.string.onboarding_result_fat))
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.onboarding_result_water, goals.waterMl), fontSize = 13.sp, color = FitCoachColors.Water)
            }
        }

        if (rec != null) {
            FitCard {
                SectionLabel(stringResource(R.string.onboarding_result_program))
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(rec.program.emoji, fontSize = 28.sp)
                    Column {
                        Text(rec.program.title.tr(), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = FitCoachColors.Accent)
                        Text(rec.program.subtitle.tr(), fontSize = 12.sp, color = FitCoachColors.TextSecondary)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(rec.program.description.tr(), fontSize = 13.sp, color = FitCoachColors.TextSecondary)
                Text(stringResource(R.string.onboarding_result_program_meta), fontSize = 12.sp, color = FitCoachColors.TextMuted)
            }

            if (rec.needsTrainerConsult) {
                FitCard {
                    Text(stringResource(R.string.onboarding_result_consult_title), fontWeight = FontWeight.Bold, color = FitCoachColors.Warning)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.onboarding_result_consult_text, BrandConfig.clubName),
                        fontSize = 13.sp, color = FitCoachColors.TextSecondary
                    )
                    if (BrandConfig.hasWhatsapp) {
                        Spacer(Modifier.height(10.dp))
                        val whatsappText = stringResource(R.string.onboarding_result_consult_whatsapp, BrandConfig.appName)
                        Button(
                            onClick = {
                                val url = BrandConfig.whatsappUrl(whatsappText)
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = FitCoachColors.Success, contentColor = FitCoachColors.Background),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.onboarding_result_consult_button), fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }

        FitCard {
            SectionLabel(stringResource(R.string.onboarding_result_important))
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.onboarding_result_disclaimer), fontSize = 12.sp, color = FitCoachColors.TextSecondary)
        }

        Row(
            modifier = Modifier.fillMaxWidth().clickable { onConsent(!state.draft.consent) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = state.draft.consent,
                onCheckedChange = onConsent,
                colors = CheckboxDefaults.colors(checkedColor = FitCoachColors.Accent, checkmarkColor = FitCoachColors.AccentOn, uncheckedColor = FitCoachColors.TextMuted)
            )
            Text(
                stringResource(R.string.onboarding_result_consent),
                fontSize = 12.sp, color = FitCoachColors.TextPrimary
            )
        }
        if (BrandConfig.hasPrivacyPolicy) {
            Text(
                stringResource(R.string.privacy_policy_link),
                fontSize = 13.sp, color = FitCoachColors.Accent, fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .clickable { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BrandConfig.privacyPolicyUrl))) } }
            )
        }
    }
}

@Composable
private fun GoalStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = FitCoachColors.Accent)
        Text(label, fontSize = 11.sp, color = FitCoachColors.TextMuted)
    }
}
