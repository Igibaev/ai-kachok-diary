package com.fitcoach.app.share

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import com.fitcoach.app.R
import com.fitcoach.app.brand.BrandConfig
import com.fitcoach.app.data.club.ClubRepository
import com.fitcoach.app.domain.service.ProgressShareData
import com.fitcoach.app.domain.service.ShareService
import com.fitcoach.app.domain.service.WorkoutShareData
import com.fitcoach.app.l10n.DomainTranslations
import com.fitcoach.app.l10n.withLanguage
import com.fitcoach.app.presentation.theme.FitCoachColors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Карточка 1080×1920 → PNG в cacheDir/share → FileProvider → Instagram Stories (если установлен) или системный chooser.
 * Для Instagram на Android 11+ в манифесте нужен блок <queries><package android:name="com.instagram.android"/></queries>.
 */
@Singleton
class ShareServiceImpl @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val clubRepository: ClubRepository
) : ShareService {

    /** Рендерер создаётся на каждый вызов с контекстом на текущем языке приложения — подписи карточки локализованы. */
    private fun renderer() = ShareCardRenderer(appContext.withLanguage(DomainTranslations.currentLanguage()))

    override suspend fun shareWorkoutCard(context: Context, data: WorkoutShareData): Result<Unit> = runCatching {
        val branding = branding(data.referralCode)
        val renderer = renderer()
        val bitmap = withContext(Dispatchers.Default) { renderer.renderWorkout(data, branding) }
        share(context, bitmap, "workout")
    }

    override suspend fun shareProgressCard(context: Context, data: ProgressShareData): Result<Unit> = runCatching {
        val branding = branding(data.referralCode)
        val renderer = renderer()
        val bitmap = withContext(Dispatchers.Default) { renderer.renderProgress(data, branding) }
        share(context, bitmap, "progress")
    }

    private suspend fun branding(referralOverride: String): ShareCardRenderer.Branding {
        val club = clubRepository.current()
        return ShareCardRenderer.Branding(
            hashtags = club.hashtags,
            referralCode = referralOverride.ifBlank { club.referralCode }
        )
    }

    private suspend fun share(context: Context, bitmap: Bitmap, prefix: String) {
        val uri = withContext(Dispatchers.IO) { saveToCache(bitmap, prefix) }
        withContext(Dispatchers.Main) {
            if (!tryInstagramStories(context, uri)) openChooser(context, uri)
        }
    }

    private fun saveToCache(bitmap: Bitmap, prefix: String): Uri {
        val dir = File(appContext.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 24 * 60 * 60 * 1000L }?.forEach { it.delete() }
        val file = File(dir, "${prefix}_${System.currentTimeMillis()}.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)
    }

    private fun tryInstagramStories(context: Context, uri: Uri): Boolean {
        if (appContext.packageManager.getLaunchIntentForPackage(INSTAGRAM) == null) return false
        val intent = Intent(INSTAGRAM_STORY_ACTION).apply {
            setDataAndType(uri, "image/png")
            setPackage(INSTAGRAM)
            putExtra("source_application", appContext.packageName)
            putExtra("top_background_color", hex(FitCoachColors.Background.toArgb()))
            putExtra("bottom_background_color", hex(FitCoachColors.Accent.toArgb()))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.grantUriPermission(INSTAGRAM, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    private fun openChooser(context: Context, uri: Uri) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "${BrandConfig.clubName} · ${BrandConfig.appName}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, context.getString(R.string.share_chooser)).apply {
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    private fun hex(argb: Int) = "#%06X".format(0xFFFFFF and argb)

    companion object {
        private const val INSTAGRAM = "com.instagram.android"
        private const val INSTAGRAM_STORY_ACTION = "com.instagram.share.ADD_TO_STORY"
    }
}
