package hu.regelo.app.util

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.scale
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object Images {

    /** Kép betöltése legfeljebb [maxSide] px hosszabb oldallal, EXIF-forgatással. */
    fun load(context: Context, uri: Uri, maxSide: Int = 2400): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val rotation = runCatching {
            resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        }.getOrDefault(0)
        val rotated = if (rotation != 0) {
            val m = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
        } else bitmap
        return scaleDown(rotated, maxSide)
    }

    fun scaleDown(bitmap: Bitmap, maxSide: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxSide) return bitmap
        val f = maxSide.toFloat() / longest
        return bitmap.scale((bitmap.width * f).roundToInt().coerceAtLeast(1), (bitmap.height * f).roundToInt().coerceAtLeast(1))
    }

    /**
     * Kivágás normalizált téglalappal (0..1), majd átméretezés pontosan [outW]×[outH] képpontra.
     */
    fun cropNormalized(src: Bitmap, left: Float, top: Float, width: Float, height: Float, outW: Int, outH: Int): Bitmap {
        val x = (left * src.width).roundToInt().coerceIn(0, src.width - 1)
        val y = (top * src.height).roundToInt().coerceIn(0, src.height - 1)
        val w = (width * src.width).roundToInt().coerceIn(1, src.width - x)
        val h = (height * src.height).roundToInt().coerceIn(1, src.height - y)
        val cropped = Bitmap.createBitmap(src, x, y, w, h)
        return cropped.scale(outW, outH)
    }

    fun toJpeg(bitmap: Bitmap, quality: Int = 85): ByteArray {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return out.toByteArray()
    }

    fun saveTemp(context: Context, bytes: ByteArray, name: String): File {
        val dir = File(context.filesDir, "pending").apply { mkdirs() }
        val file = File(dir, name)
        file.writeBytes(bytes)
        return file
    }

    fun clamp01(v: Float) = min(1f, max(0f, v))
}
