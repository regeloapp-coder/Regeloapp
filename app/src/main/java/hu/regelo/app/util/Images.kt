package hu.regelo.app.util

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.scale
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.system.Os
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
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
        require(name == File(name).name) { "Invalid temporary image name." }
        val dir = File(context.filesDir, "pending").apply { mkdirs() }
        val file = File(dir, name)
        file.writeBytes(encryptTemp(bytes))
        return file
    }

    fun readTemp(file: File): ByteArray {
        val stored = file.readBytes()
        if (!stored.startsWith(TEMP_MAGIC)) {
            val encryptedFile = File(file.parentFile, "${file.name}.encrypted.tmp")
            encryptedFile.writeBytes(encryptTemp(stored))
            try {
                Os.rename(encryptedFile.absolutePath, file.absolutePath)
            } catch (e: Exception) {
                encryptedFile.delete()
                throw e
            }
            return stored
        }
        check(stored.size > TEMP_MAGIC.size + GCM_IV_SIZE) { "Temporary image is corrupted." }
        val iv = stored.copyOfRange(TEMP_MAGIC.size, TEMP_MAGIC.size + GCM_IV_SIZE)
        val ciphertext = stored.copyOfRange(TEMP_MAGIC.size + GCM_IV_SIZE, stored.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, tempKey(create = false), GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext)
    }

    fun clearPending(context: Context) {
        val dir = File(context.filesDir, "pending")
        val deleted = !dir.exists() || dir.deleteRecursively()
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        keyStore.deleteEntry(TEMP_KEY_ALIAS)
        check(deleted && !dir.exists()) { "Pending images could not be removed." }
    }

    private fun encryptTemp(bytes: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, tempKey(create = true))
        return TEMP_MAGIC + cipher.iv + cipher.doFinal(bytes)
    }

    private fun tempKey(create: Boolean): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(TEMP_KEY_ALIAS, null) as? SecretKey)?.let { return it }
        check(create) { "Temporary image encryption key is unavailable." }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(
            TEMP_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) builder.setUnlockedDeviceRequired(true)
        generator.init(builder.build())
        return generator.generateKey()
    }

    fun clamp01(v: Float) = min(1f, max(0f, v))

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    private const val KEYSTORE = "AndroidKeyStore"
    private const val TEMP_KEY_ALIAS = "regelo_pending_image_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_IV_SIZE = 12
    private val TEMP_MAGIC = byteArrayOf(0x52, 0x47, 0x49, 0x01)
}
