package com.fitcoach.app.presentation.screens.chef

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.fitcoach.app.R
import com.fitcoach.app.ai.AiMode
import com.fitcoach.app.domain.model.FoodAnalysis
import com.fitcoach.app.presentation.components.FitCard
import com.fitcoach.app.presentation.components.PrimaryButton
import com.fitcoach.app.presentation.theme.FitCoachColors

/**
 * Фото еды → КБЖУ. Камера — TakePicture в cacheDir/photos через FileProvider (разрешение CAMERA не нужно),
 * галерея — PickVisualMedia. Снимок уменьшается до 1024 px и удаляется после анализа.
 */
@Composable
fun FoodPhotoScreen(
    onBack: () -> Unit,
    viewModel: FoodPhotoViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        cameraUri?.let { viewModel.onCameraResult(ok, it) }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        viewModel.setImage(uri)
    }

    LaunchedEffect(state.addedCount) {
        val count = state.addedCount ?: return@LaunchedEffect
        snackbar.showSnackbar(context.resources.getQuantityString(R.plurals.plural_foodphoto_added, count, count))
        viewModel.consumeAdded()
        onBack()
    }

    Scaffold(
        containerColor = FitCoachColors.Background,
        topBar = { ChefTopBar(title = stringResource(R.string.foodphoto_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(FitCoachColors.Background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.mode == AiMode.DEMO) DemoBadge(stringResource(R.string.ai_mode_demo))

            if (state.hasImage) {
                AsyncImage(
                    model = state.imageBytes ?: state.imageUri,
                    contentDescription = stringResource(R.string.foodphoto_preview),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(16.dp)).background(FitCoachColors.Card)
                )
            } else {
                FitCard {
                    Text("📷", fontSize = 48.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.foodphoto_empty_text), fontSize = 14.sp, color = FitCoachColors.TextSecondary, textAlign = TextAlign.Center, lineHeight = 20.sp, modifier = Modifier.fillMaxWidth())
                }
            }

            val analysis = state.analysis
            when {
                state.analyzing -> ChefProgressCard(title = stringResource(R.string.foodphoto_progress), text = "")
                analysis != null -> FoodAnalysisResult(
                    analysis = analysis,
                    items = state.items,
                    totals = FoodAnalysis(state.items, "", true),
                    isDemo = analysis.isDemo || state.mode == AiMode.DEMO,
                    selectedMeal = state.selectedMeal,
                    onGramsChange = viewModel::updateGrams,
                    onSelectMeal = viewModel::selectMeal,
                    onAdd = viewModel::addToDiary,
                    onRetake = viewModel::reset
                )
                else -> {
                    state.error?.let { message ->
                        ChefErrorCard(
                            title = stringResource(R.string.foodphoto_error_title),
                            message = message,
                            onRetry = { viewModel.clearError(); if (state.imageUri != null) viewModel.analyze() else viewModel.reset() },
                            onDismiss = { viewModel.clearError() }
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val uri = viewModel.newCameraUri()
                                cameraUri = uri
                                runCatching { cameraLauncher.launch(uri) }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("📷 " + stringResource(R.string.foodphoto_camera), color = FitCoachColors.TextPrimary) }
                        OutlinedButton(
                            onClick = { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            modifier = Modifier.weight(1f)
                        ) { Text("🖼 " + stringResource(R.string.foodphoto_gallery), color = FitCoachColors.TextPrimary) }
                    }
                    if (state.imageUri != null) {
                        OutlinedTextField(
                            value = state.hint,
                            onValueChange = viewModel::setHint,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.foodphoto_hint_label), color = FitCoachColors.TextMuted) },
                            placeholder = { Text(stringResource(R.string.foodphoto_hint_placeholder), color = FitCoachColors.TextMuted) },
                            singleLine = true,
                            colors = chefTextFieldColors()
                        )
                        PrimaryButton(text = "✨ " + stringResource(R.string.foodphoto_analyze), onClick = viewModel::analyze)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (state.showPrivacyHint) {
        AlertDialog(
            onDismissRequest = viewModel::dismissPrivacyHint,
            containerColor = FitCoachColors.Card,
            title = { Text(stringResource(R.string.foodphoto_privacy_title), color = FitCoachColors.TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.foodphoto_privacy_hint), color = FitCoachColors.TextSecondary, lineHeight = 20.sp) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissPrivacyHint) { Text(stringResource(R.string.foodphoto_privacy_ok), color = FitCoachColors.Accent) }
            }
        )
    }
}
