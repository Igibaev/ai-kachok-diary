package com.fitcoach.app.presentation.screens.club

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fitcoach.app.R
import com.fitcoach.app.brand.BrandConfig
import com.fitcoach.app.data.club.QrPayload
import com.fitcoach.app.domain.model.UserProfile
import com.fitcoach.app.domain.repository.UserRepository
import com.fitcoach.app.presentation.theme.FitCoachColors
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class MemberCardState(
    val name: String = "",
    val memberId: String = "",
    val payload: String = ""
)

@HiltViewModel
class QrPassViewModel @Inject constructor(userRepository: UserRepository) : ViewModel() {
    val state: StateFlow<MemberCardState> = userRepository.observeProfile().map { p ->
        val profile = p ?: UserProfile()
        MemberCardState(
            name = profile.name,
            memberId = profile.memberId,
            payload = if (profile.memberId.isBlank()) "" else QrPayload.build(BrandConfig.brandId, profile.memberId)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MemberCardState())
}

/** Генерация QR через ZXing в Bitmap (тёмные модули — чёрные, фон — белый, для любых сканеров). */
object QrCodeBitmap {
    fun render(payload: String, sizePx: Int): Bitmap? = runCatching {
        val hints = mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val pixels = IntArray(sizePx * sizePx)
        for (y in 0 until sizePx) {
            val row = y * sizePx
            for (x in 0 until sizePx) pixels[row + x] = if (matrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        Bitmap.createBitmap(pixels, sizePx, sizePx, Bitmap.Config.ARGB_8888)
    }.getOrNull()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrPassScreen(onBack: () -> Unit, viewModel: QrPassViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var qr by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(state.payload) {
        qr = if (state.payload.isBlank()) null else withContext(Dispatchers.Default) { QrCodeBitmap.render(state.payload, 640) }
    }

    Scaffold(
        containerColor = FitCoachColors.Background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.qr_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = FitCoachColors.Background,
                    titleContentColor = FitCoachColors.TextPrimary,
                    navigationIconContentColor = FitCoachColors.TextPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(FitCoachColors.Card)
                    .border(1.dp, FitCoachColors.Border, RoundedCornerShape(24.dp)).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Image(painterResource(R.drawable.brand_logo), contentDescription = null, modifier = Modifier.size(36.dp))
                    Column {
                        Text(BrandConfig.clubName, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = FitCoachColors.TextPrimary)
                        if (BrandConfig.clubCity.isNotBlank()) Text(BrandConfig.clubCity, fontSize = 12.sp, color = FitCoachColors.TextMuted)
                    }
                }
                Spacer(Modifier.height(20.dp))
                Box(
                    modifier = Modifier.size(240.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = qr
                    if (bmp != null) {
                        Image(bmp.asImageBitmap(), contentDescription = stringResource(R.string.qr_code_description), modifier = Modifier.fillMaxSize())
                    } else if (state.memberId.isBlank()) {
                        Text(stringResource(R.string.qr_need_onboarding), color = Color.Black, fontSize = 12.sp, textAlign = TextAlign.Center)
                    } else {
                        CircularProgressIndicator(color = FitCoachColors.Accent)
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text(state.name.ifBlank { stringResource(R.string.qr_default_name) }, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = FitCoachColors.TextPrimary)
                Spacer(Modifier.height(6.dp))
                Text(
                    state.memberId.ifBlank { "—" },
                    fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, letterSpacing = 4.sp, color = FitCoachColors.Accent
                )
                Text(stringResource(R.string.qr_member_number), fontSize = 11.sp, color = FitCoachColors.TextMuted)
            }
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.qr_hint),
                fontSize = 13.sp, color = FitCoachColors.TextSecondary, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.qr_bound, BrandConfig.appName),
                fontSize = 11.sp, color = FitCoachColors.TextMuted, textAlign = TextAlign.Center
            )
        }
    }
}
