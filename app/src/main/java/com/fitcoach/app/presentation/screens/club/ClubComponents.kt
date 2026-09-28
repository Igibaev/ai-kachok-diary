package com.fitcoach.app.presentation.screens.club

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Icon
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fitcoach.app.R
import com.fitcoach.app.brand.BrandConfig
import com.fitcoach.app.data.club.ClubService
import com.fitcoach.app.data.club.Promo
import com.fitcoach.app.presentation.theme.FitCoachColors
import java.time.format.DateTimeFormatter

/** Баннер акций клуба для главной: горизонтальная лента, скрыт при отсутствии акций. */
@Composable
fun PromoBanner(modifier: Modifier = Modifier, viewModel: PromoBannerViewModel = hiltViewModel()) {
    val promos by viewModel.promos.collectAsState()
    if (promos.isEmpty()) return
    val context = LocalContext.current
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.club_promos_banner),
            style = MaterialTheme.typography.labelSmall,
            color = FitCoachColors.TextMuted,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(promos, key = { it.id }) { promo ->
                PromoCard(
                    promo = promo,
                    modifier = Modifier.width(260.dp),
                    onClick = { openPromo(context, promo) }
                )
            }
        }
    }
}

@Composable
fun PromoCard(promo: Promo, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(FitCoachColors.AccentSoft)
            .border(1.dp, FitCoachColors.Accent.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(promo.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = FitCoachColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (promo.text.isNotBlank()) {
            Text(promo.text, fontSize = 12.sp, color = FitCoachColors.TextSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(promo.ctaText ?: stringResource(R.string.club_promo_more), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = FitCoachColors.Accent)
            promo.validUntilDate()?.let {
                Text(stringResource(R.string.club_promo_until, it.format(DateTimeFormatter.ofPattern("dd.MM"))), fontSize = 11.sp, color = FitCoachColors.TextMuted)
            }
        }
    }
}

internal fun openPromo(context: android.content.Context, promo: Promo) {
    val url = promo.ctaUrl
    if (!url.isNullOrBlank()) ClubLinks.open(context, url)
    else if (BrandConfig.hasWhatsapp) ClubLinks.whatsapp(context, ClubLinks.promoText(context, promo.title))
    else ClubLinks.dial(context)
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = FitCoachColors.TextMuted,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(top = 4.dp)
    )
}

/** Круглая кнопка действия (Позвонить / WhatsApp / Instagram / 2GIS). */
@Composable
fun ActionButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(FitCoachColors.Card)
            .border(1.dp, FitCoachColors.Border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, contentDescription = label, tint = FitCoachColors.Accent, modifier = Modifier.size(22.dp))
        Text(label, fontSize = 11.sp, color = FitCoachColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SmallChip(text: String, color: Color = FitCoachColors.TextSecondary, background: Color = FitCoachColors.Surface, onClick: (() -> Unit)? = null) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(text, fontSize = 11.sp, color = color, fontWeight = FontWeight.Medium)
    }
}

/** «25 000 ₸ / мес» — локализованный формат цены (модель хранит только число и единицу). */
@Composable
fun servicePriceLabel(service: ClubService): String {
    val digits = "%,d".format(service.price).replace(',', ' ')
    val base = if (service.price <= 0) stringResource(R.string.club_price_free) else stringResource(R.string.club_price_tenge, digits)
    return if (service.unit.isBlank() || service.price <= 0) base else "$base / ${service.unit}"
}

@Composable
fun EmptyHint(text: String) {
    Text(text, fontSize = 13.sp, color = FitCoachColors.TextMuted, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
}
