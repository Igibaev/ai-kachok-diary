package com.fitcoach.app.presentation.screens.club

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fitcoach.app.R
import com.fitcoach.app.brand.BrandConfig
import com.fitcoach.app.data.club.ClubService
import com.fitcoach.app.data.club.ScheduleItem
import com.fitcoach.app.data.club.Trainer
import com.fitcoach.app.l10n.DomainTranslations
import com.fitcoach.app.presentation.components.FitCard
import com.fitcoach.app.presentation.theme.FitCoachColors
import java.time.DayOfWeek
import java.time.format.TextStyle

@Composable
fun ClubScreen(onOpenQrPass: () -> Unit, onOpenChat: () -> Unit, viewModel: ClubViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val content = state.content

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(FitCoachColors.Background),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { ClubHero() }
        item { ActionRow() }
        item { BookTrainerCard(onClick = { ClubLinks.whatsapp(context, ClubLinks.bookTrainerText(context, null)) }) }

        item { SectionTitle(stringResource(R.string.club_promos)) }
        if (state.activePromos.isEmpty()) {
            item { EmptyHint(stringResource(R.string.club_promos_empty)) }
        } else {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.activePromos, key = { it.id }) { promo ->
                        PromoCard(promo, Modifier.width(260.dp)) { openPromo(context, promo) }
                    }
                }
            }
        }

        item { SectionTitle(stringResource(R.string.club_schedule)) }
        item { ScheduleCard(content.schedule, state.today) }

        item { SectionTitle(stringResource(R.string.club_trainers)) }
        if (content.trainers.isEmpty()) {
            item { EmptyHint(stringResource(R.string.club_trainers_empty)) }
        } else {
            items(content.trainers) { TrainerCard(it) }
        }

        item { SectionTitle(stringResource(R.string.club_services)) }
        if (content.services.isEmpty()) {
            item { EmptyHint(stringResource(R.string.club_services_empty)) }
        } else {
            item { ServicesCard(content.services) }
        }

        if (content.referralCode.isNotBlank()) {
            item { ReferralCard(content.referralCode, content.referralText) }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onOpenQrPass,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = FitCoachColors.Accent, contentColor = FitCoachColors.AccentOn)
                ) {
                    Icon(Icons.Default.QrCode2, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.club_member_card), fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onOpenChat,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, FitCoachColors.Accent),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = FitCoachColors.Accent)
                ) {
                    Icon(Icons.Default.SmartToy, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.club_ai_coach), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ClubHero() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.size(84.dp).clip(CircleShape).background(FitCoachColors.AccentSoft)
                .border(1.dp, FitCoachColors.Accent.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Image(painterResource(R.drawable.brand_logo), contentDescription = BrandConfig.brandName, modifier = Modifier.size(56.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(BrandConfig.clubName, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = FitCoachColors.TextPrimary)
        if (BrandConfig.clubCity.isNotBlank() || BrandConfig.clubAddress.isNotBlank()) {
            Text(
                listOf(BrandConfig.clubAddress, BrandConfig.clubCity).filter { it.isNotBlank() }.distinct().joinToString(" · "),
                fontSize = 13.sp, color = FitCoachColors.TextSecondary
            )
        }
        if (BrandConfig.clubHours.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Schedule, null, tint = FitCoachColors.TextMuted, modifier = Modifier.size(14.dp))
                Text(BrandConfig.clubHours, fontSize = 12.sp, color = FitCoachColors.TextMuted)
            }
        }
    }
}

@Composable
private fun ActionRow() {
    val context = LocalContext.current
    val callLabel = stringResource(R.string.club_call)
    val actions = buildList {
        if (BrandConfig.hasPhone) add(Triple(Icons.Default.Call, callLabel) { ClubLinks.dial(context) })
        if (BrandConfig.hasWhatsapp) add(Triple(Icons.AutoMirrored.Filled.Chat, "WhatsApp") {
            ClubLinks.whatsapp(context, ClubLinks.greetingText(context))
        })
        if (BrandConfig.hasInstagram) add(Triple(Icons.Default.CameraAlt, "Instagram") { ClubLinks.instagram(context) })
        if (BrandConfig.hasMap) add(Triple(Icons.Default.Place, "2GIS") { ClubLinks.map(context) })
    }
    if (actions.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        actions.forEach { (icon, label, action) ->
            ActionButton(icon, label, onClick = action, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun BookTrainerCard(onClick: () -> Unit) {
    if (!BrandConfig.hasWhatsapp) return
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(FitCoachColors.Accent)
            .clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.club_book_trainer), fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = FitCoachColors.AccentOn)
            Text(stringResource(R.string.club_book_trainer_hint), fontSize = 12.sp, color = FitCoachColors.AccentOn.copy(alpha = 0.75f))
        }
        Icon(Icons.AutoMirrored.Filled.Chat, null, tint = FitCoachColors.AccentOn)
    }
}

@Composable
private fun ScheduleCard(schedule: List<ScheduleItem>, today: Int) {
    FitCard {
        if (schedule.isEmpty()) {
            EmptyHint(stringResource(R.string.club_schedule_empty))
            return@FitCard
        }
        val locale = DomainTranslations.currentLocale()
        val ordered = (today..7) + (1 until today)
        ordered.forEachIndexed { index, day ->
            val items = schedule.filter { it.day == day }.sortedBy { it.time }
            if (items.isEmpty()) return@forEachIndexed
            val isToday = day == today
            if (index > 0) HorizontalDivider(color = FitCoachColors.Border, modifier = Modifier.padding(vertical = 8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    runCatching { DayOfWeek.of(day).getDisplayName(TextStyle.SHORT, locale).replaceFirstChar { it.uppercase() } }.getOrDefault("$day"),
                    fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    color = if (isToday) FitCoachColors.Accent else FitCoachColors.TextSecondary
                )
                if (isToday) SmallChip(stringResource(R.string.club_today), color = FitCoachColors.AccentOn, background = FitCoachColors.Accent)
            }
            Spacer(Modifier.height(4.dp))
            items.forEach { item ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(item.time, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (isToday) FitCoachColors.TextPrimary else FitCoachColors.TextSecondary, modifier = Modifier.width(48.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.title, fontSize = 14.sp, color = FitCoachColors.TextPrimary, fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal)
                        val sub = listOfNotNull(item.trainer, stringResource(R.string.format_minutes, item.durationMin)).joinToString(" · ")
                        Text(sub, fontSize = 11.sp, color = FitCoachColors.TextMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun TrainerCard(trainer: Trainer) {
    val context = LocalContext.current
    FitCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).background(FitCoachColors.AccentSoft),
                contentAlignment = Alignment.Center
            ) {
                Text(trainer.name.split(" ").take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString(""), fontWeight = FontWeight.Bold, color = FitCoachColors.Accent)
            }
            Column(Modifier.weight(1f)) {
                Text(trainer.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = FitCoachColors.TextPrimary)
                if (trainer.role.isNotBlank()) Text(trainer.role, fontSize = 12.sp, color = FitCoachColors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trainer.specialties.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                trainer.specialties.take(3).forEach { SmallChip(it) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val wa = trainer.whatsappDigits.ifBlank { BrandConfig.clubWhatsapp }
            if (wa.isNotBlank()) {
                SmallChip(stringResource(R.string.club_book), color = FitCoachColors.AccentOn, background = FitCoachColors.Accent) {
                    ClubLinks.whatsapp(context, ClubLinks.bookTrainerText(context, trainer.name), wa)
                }
            }
            if (trainer.instagramHandle.isNotBlank()) {
                SmallChip("@${trainer.instagramHandle}", color = FitCoachColors.Accent, background = FitCoachColors.AccentSoft) {
                    ClubLinks.instagram(context, trainer.instagramHandle)
                }
            }
        }
    }
}

@Composable
private fun ServicesCard(services: List<ClubService>) {
    val context = LocalContext.current
    FitCard {
        services.forEachIndexed { index, s ->
            if (index > 0) HorizontalDivider(color = FitCoachColors.Border, modifier = Modifier.padding(vertical = 8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().clickable(enabled = BrandConfig.hasWhatsapp) {
                    ClubLinks.whatsapp(context, ClubLinks.bookServiceText(context, s.title))
                },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(s.title, fontSize = 14.sp, color = FitCoachColors.TextPrimary, fontWeight = FontWeight.Medium)
                    s.description?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 11.sp, color = FitCoachColors.TextMuted) }
                }
                Text(servicePriceLabel(s), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FitCoachColors.Accent)
                if (BrandConfig.hasWhatsapp) {
                    Icon(
                        Icons.AutoMirrored.Filled.Chat, contentDescription = stringResource(R.string.club_services_tap_hint),
                        tint = FitCoachColors.Accent, modifier = Modifier.padding(start = 10.dp).size(18.dp)
                    )
                }
            }
        }
        if (BrandConfig.hasWhatsapp && services.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.club_services_tap_hint), fontSize = 11.sp, color = FitCoachColors.TextMuted)
        }
    }
}

@Composable
private fun ReferralCard(code: String, text: String) {
    FitCard {
        Text(stringResource(R.string.club_referral), style = MaterialTheme.typography.labelSmall, color = FitCoachColors.TextMuted)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(FitCoachColors.AccentSoft)
                    .border(1.dp, FitCoachColors.Accent, RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(code, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = FitCoachColors.Accent, letterSpacing = 2.sp)
            }
            Text(text.ifBlank { stringResource(R.string.club_referral_default) }, fontSize = 12.sp, color = FitCoachColors.TextSecondary, modifier = Modifier.weight(1f))
        }
    }
}

