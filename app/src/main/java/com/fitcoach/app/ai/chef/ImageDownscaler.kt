package com.fitcoach.app.ai.chef

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * Подготовка фото еды к отправке: inSampleSize-декодирование, масштаб до 1024 px по длинной стороне,
 * EXIF-поворот, JPEG q80 (при превышении 1,5 МБ качество снижается). Результат — байты JPEG или null.
 */
object ImageDownscaler {
    const val MAX_SIDE = 1024
    const val MAX_BYTES = 1_500_000
    const val MEDIA_TYPE = "image/jpeg"

    suspend fun downscale(context: Context, uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        runCatching { decode(context, uri) }.getOrNull()
    }

    private fun decode(context: Context, uri: Uri): ByteArray? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null

        val rotation = resolver.openInputStream(uri)?.use { runCatching { ExifInterface(it).rotationDegrees }.getOrDefault(0) } ?: 0
        val scaled = scaleAndRotate(decoded, rotation)
        if (scaled !== decoded) decoded.recycle()

        var quality = 80
        var bytes: ByteArray
        do {
            bytes = ByteArrayOutputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
                out.toByteArray()
            }
            quality -= 10
        } while (bytes.size > MAX_BYTES && quality >= 40)
        scaled.recycle()
        return if (bytes.size > MAX_BYTES) null else bytes
    }

    private fun scaleAndRotate(src: Bitmap, rotation: Int): Bitmap {
        val longSide = max(src.width, src.height)
        val scale = if (longSide > MAX_SIDE) MAX_SIDE.toFloat() / longSide else 1f
        if (scale == 1f && rotation == 0) return src
        val matrix = Matrix().apply {
            if (scale != 1f) postScale(scale, scale)
            if (rotation != 0) postRotate(rotation.toFloat())
        }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }
}
