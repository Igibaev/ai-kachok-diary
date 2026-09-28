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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fitcoach.app.brand.BrandConfig
import com.fitcoach.app.presentation.components.FitCard
import com.fitcoach.app.presentation.components.SectionLabel
import com.fitcoach.app.presentation.theme.FitCoachColors

const val MEDICAL_DISCLAIMER =
    "Приложение не заменяет врача. Рекомендации по тренировкам и питанию носят общий характер. " +
        "При хронических заболеваниях, беременности, болях или травмах проконсультируйтесь с врачом " +
        "и тренером клуба перед началом занятий."

@Composable
fun ResultStep(state: OnboardingUiState, onConsent: (Boolean) -> Unit) {
    val context = LocalContext.current
    val goals = state.goals
    val rec = state.recommendation

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Hint("Мы рассчитали цели и подобрали программу. Всё можно поменять в настройках.")

        if (goals != null) {
            FitCard {
                SectionLabel("Дневные цели")
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    GoalStat("${goals.calories}", "ккал")
                    GoalStat("${goals.proteinG}", "белок, г")
                    GoalStat("${goals.carbsG}", "углев., г")
                    GoalStat("${goals.fatG}", "жиры, г")
                }
                Spacer(Modifier.height(8.dp))
                Text("💧 Вода: ${goals.waterMl} мл в день", fontSize = 13.sp, color = FitCoachColors.Water)
            }
        }

        if (rec != null) {
            FitCard {
                SectionLabel("Рекомендуемая программа")
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(rec.program.emoji, fontSize = 28.sp)
                    Column {
                        Text(rec.program.title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = FitCoachColors.Accent)
                        Text(rec.program.subtitle, fontSize = 12.sp, color = FitCoachColors.TextSecondary)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(rec.program.description, fontSize = 13.sp, color = FitCoachColors.TextSecondary)
                Text("12 недель · 3 фазы · гибкий график", fontSize = 12.sp, color = FitCoachColors.TextMuted)
            }

            if (rec.needsTrainerConsult) {
                FitCard {
                    Text("🤍 Рекомендуем персональную консультацию тренера", fontWeight = FontWeight.Bold, color = FitCoachColors.Warning)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Беременность и послеродовой период требуют индивидуального подхода. " +
                            "Программа «Старт» — щадящая, но тренер клуба ${BrandConfig.clubName} подберёт нагрузку лично для тебя.",
                        fontSize = 13.sp, color = FitCoachColors.TextSecondary
                    )
                    if (BrandConfig.hasWhatsapp) {
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = {
                                val url = BrandConfig.whatsappUrl(
                                    "Здравствуйте! Пишу из приложения ${BrandConfig.appName}. Хочу консультацию тренера по тренировкам в период беременности / после родов."
                                )
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = FitCoachColors.Success, contentColor = FitCoachColors.Background),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Написать тренеру в WhatsApp", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }

        FitCard {
            SectionLabel("Важно")
            Spacer(Modifier.height(6.dp))
            Text(MEDICAL_DISCLAIMER, fontSize = 12.sp, color = FitCoachColors.TextSecondary)
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
                "Согласен на обработку данных о здоровье (вес, ограничения, самочувствие) для персонализации программы. Данные хранятся на устройстве.",
                fontSize = 12.sp, color = FitCoachColors.TextPrimary
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
