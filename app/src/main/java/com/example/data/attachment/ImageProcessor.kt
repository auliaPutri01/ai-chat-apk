package com.example.data.attachment

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Pemroses gambar untuk lampiran vision.
 *
 * Tahapan:
 * 1. decode dengan inSampleSize (hemat memori, tidak memuat gambar penuh),
 * 2. perbaiki rotasi/ mirror dari EXIF (android.media.ExifInterface, tersedia sejak API 24),
 * 3. perkecil sisi terpanjang menjadi maksimal [AttachmentLimits.MAX_IMAGE_DIMENSION] px,
 * 4. kompres JPEG kualitas [AttachmentLimits.JPEG_QUALITY],
 * 5. ubah menjadi data URI base64.
 *
 * Seluruh pekerjaan berat dijalankan di Dispatchers.IO.
 */
object ImageProcessor {

    /** Hasil pemrosesan satu gambar. */
    data class Processed(
        val base64: String,
        val mime: String,
        val width: Int,
        val height: Int,
        val bytes: Int
    ) {
        /** Data URI siap dipakai sebagai image_url / inlineData. */
        val dataUri: String get() = "data:$mime;base64,$base64"
    }

    suspend fun process(file: File): Result<Processed> = withContext(Dispatchers.IO) {
        try {
            if (!file.exists()) {
                return@withContext Result.failure(Exception("File gambar tidak ditemukan"))
            }
            if (file.length() > AttachmentLimits.MAX_IMAGE_BYTES) {
                return@withContext Result.failure(
                    Exception(
                        "Gambar lebih dari " +
                            com.example.data.model.Attachment.formatSize(AttachmentLimits.MAX_IMAGE_BYTES)
                    )
                )
            }

            val sampled = decodeSampled(file)
                ?: return@withContext Result.failure(Exception("Gambar tidak bisa dibaca"))

            val upright = applyExifRotation(sampled, file)
            val scaled = scaleToMaxSide(upright, AttachmentLimits.MAX_IMAGE_DIMENSION)

            val output = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, AttachmentLimits.JPEG_QUALITY, output)
            val bytes = output.toByteArray()

            val result = Processed(
                base64 = Base64.encodeToString(bytes, Base64.NO_WRAP),
                mime = "image/jpeg",
                width = scaled.width,
                height = scaled.height,
                bytes = bytes.size
            )

            if (scaled !== upright) scaled.recycle()
            if (upright !== sampled) upright.recycle()
            sampled.recycle()

            Result.success(result)
        } catch (t: OutOfMemoryError) {
            Result.failure(Exception("Gambar terlalu besar untuk diproses"))
        } catch (t: Throwable) {
            Result.failure(Exception("Gagal memproses gambar: " + (t.localizedMessage ?: "tidak diketahui")))
        }
    }

    /** Decode bertahap sampai sisi terpanjang <= 2x target, memakai inSampleSize. */
    private fun decodeSampled(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        val target = AttachmentLimits.MAX_IMAGE_DIMENSION * 2
        var longest = maxOf(bounds.outWidth, bounds.outHeight)
        while (longest / sampleSize > target) {
            sampleSize *= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    /** Menerapkan rotasi/flip EXIF. Mengembalikan bitmap asli bila tidak ada transformasi. */
    fun applyExifRotation(bitmap: Bitmap, file: File): Bitmap {
        val orientation = try {
            ExifInterface(file.absolutePath)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (t: Throwable) {
            ExifInterface.ORIENTATION_NORMAL
        }

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
        } catch (t: Throwable) {
            bitmap
        }
    }

    /** Memperkecil agar sisi terpanjang <= [maxSide]. Tidak memperbesar gambar kecil. */
    fun scaleToMaxSide(bitmap: Bitmap, maxSide: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxSide || longest == 0) return bitmap
        val ratio = maxSide.toFloat() / longest.toFloat()
        val width = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val height = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return try {
            Bitmap.createScaledBitmap(bitmap, width, height, true)
        } catch (t: Throwable) {
            bitmap
        }
    }
}
