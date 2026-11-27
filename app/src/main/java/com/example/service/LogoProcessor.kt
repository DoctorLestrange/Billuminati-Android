package com.example.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import com.example.domain.engine.BillingEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

object LogoProcessor {

    /**
     * Reads a picked image URI without requiring storage permissions (Photo Picker / SAF),
     * respects EXIF orientation for camera photos, downsamples safely to avoid OOM,
     * expands non-square images onto a transparent square canvas centered without altering aspect ratio,
     * downscales to 512x512 PNG, and returns a web-compatible PNG data URL.
     */
    suspend fun processLogoUri(
        context: Context,
        uri: Uri,
        targetSide: Int = 512
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOpts)
            } ?: throw IllegalStateException("Could not open selected image.")

            val rawW = boundsOpts.outWidth
            val rawH = boundsOpts.outHeight
            if (rawW <= 0 || rawH <= 0) {
                throw IllegalArgumentException("Selected file is not a supported image.")
            }

            // Read EXIF orientation safely (available on API 24+)
            val orientation = try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    ExifInterface(stream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                } ?: ExifInterface.ORIENTATION_NORMAL
            } catch (_: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }

            // Downsample huge camera photos cleanly before final square normalization
            var sampleSize = 1
            while (rawW / sampleSize > targetSide * 4 || rawH / sampleSize > targetSide * 4) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            } ?: throw IllegalArgumentException("Could not decode image.")

            val oriented = applyExifOrientation(decoded, orientation)
            try {
                encodeSquareLogoBitmap(oriented, targetSide)
            } finally {
                if (oriented !== decoded && !oriented.isRecycled) {
                    oriented.recycle()
                }
                if (!decoded.isRecycled) {
                    decoded.recycle()
                }
            }
        }
    }

    /**
     * Normalizes an in-memory Bitmap onto a centered square canvas and returns a PNG data URL.
     */
    fun encodeSquareLogoBitmap(source: Bitmap, targetSide: Int = 512): String {
        val placement = BillingEngine.calculateLogoSquarePlacement(
            naturalWidth = source.width,
            naturalHeight = source.height,
            targetSide = targetSide
        )

        val output = Bitmap.createBitmap(
            placement.canvasSize,
            placement.canvasSize,
            Bitmap.Config.ARGB_8888
        )
        return try {
            val canvas = Canvas(output)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val srcRect = Rect(0, 0, source.width, source.height)
            val dstRect = RectF(
                placement.offsetX.toFloat(),
                placement.offsetY.toFloat(),
                (placement.offsetX + placement.drawWidth).toFloat(),
                (placement.offsetY + placement.drawHeight).toFloat()
            )
            canvas.drawBitmap(source, srcRect, dstRect, paint)

            val baos = ByteArrayOutputStream()
            output.compress(Bitmap.CompressFormat.PNG, 100, baos)
            val encoded = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
            "data:image/png;base64,$encoded"
        } finally {
            if (!output.isRecycled) output.recycle()
        }
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return try {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (_: Exception) {
            bitmap
        }
    }

    private const val MAX_LOGO_BASE64_CHARS = 10_000_000 // ~7.5 MB binary limit

    fun decodeDataUrlToBitmap(dataUrl: String?, maxSide: Int = 1024): Bitmap? {
        if (dataUrl.isNullOrBlank()) return null
        return try {
            val base64Part = dataUrl.substringAfter("base64,", dataUrl).trim()
            if (base64Part.isEmpty() || base64Part.length > MAX_LOGO_BASE64_CHARS) return null
            val bytes = Base64.decode(base64Part, Base64.DEFAULT)
            if (!hasSupportedImageSignature(bytes)) return null

            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOpts)
            val rawW = boundsOpts.outWidth
            val rawH = boundsOpts.outHeight
            if (rawW <= 0 || rawH <= 0) return null

            var sampleSize = 1
            while (rawW / sampleSize > maxSide * 2 || rawH / sampleSize > maxSide * 2) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
        } catch (_: Throwable) {
            null
        }
    }

    private fun hasSupportedImageSignature(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        val b0 = bytes[0].toInt() and 0xFF
        val b1 = bytes[1].toInt() and 0xFF
        val b2 = bytes[2].toInt() and 0xFF
        val b3 = bytes[3].toInt() and 0xFF
        // PNG: 89 50 4E 47
        if (b0 == 0x89 && b1 == 0x50 && b2 == 0x4E && b3 == 0x47) return true
        // JPEG: FF D8 FF
        if (b0 == 0xFF && b1 == 0xD8 && b2 == 0xFF) return true
        // GIF: 47 49 46 38
        if (b0 == 0x47 && b1 == 0x49 && b2 == 0x46 && b3 == 0x38) return true
        // BMP: 42 4D
        if (b0 == 0x42 && b1 == 0x4D) return true
        // WEBP: RIFF....WEBP
        if (bytes.size >= 12 &&
            b0 == 0x52 && b1 == 0x49 && b2 == 0x46 && b3 == 0x46 &&
            (bytes[8].toInt() and 0xFF) == 0x57 &&
            (bytes[9].toInt() and 0xFF) == 0x45 &&
            (bytes[10].toInt() and 0xFF) == 0x42 &&
            (bytes[11].toInt() and 0xFF) == 0x50
        ) return true
        return false
    }
}
