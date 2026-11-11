package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.math.max

object ChatImageUtil {

    private const val MAX_LONGEST_SIDE = 1024
    private const val TARGET_BYTES = 250 * 1024 // 250 KB
    private const val MAX_BYTES = 350 * 1024 // 350 KB
    private const val MAX_DATA_URI_CHARS = 400000

    /**
     * Decodes and resizes a bitmap from Uri, rotating it correctly according to EXIF,
     * resizing its longest side to at most 1024 px, and compressing it into a JPEG
     * data URI (data:image/jpeg;base64,...) with target <= 250 KB and maximum <= 350 KB,
     * ensuring base64 character length does not exceed 400,000 chars.
     */
    fun processImageUri(context: Context, uri: Uri): Pair<Bitmap, String>? {
        return try {
            val orientation = getExifOrientation(context, uri)
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val original = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (original == null) return null

            // Correct orientation if needed
            val rotated = if (orientation != 0) {
                val matrix = Matrix().apply { postRotate(orientation.toFloat()) }
                Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
            } else {
                original
            }

            // Resize longest side to 1024 px
            val width = rotated.width
            val height = rotated.height
            val longest = max(width, height)

            val scaled = if (longest > MAX_LONGEST_SIDE) {
                val ratio = MAX_LONGEST_SIDE.toFloat() / longest.toFloat()
                val targetW = (width * ratio).toInt().coerceAtLeast(1)
                val targetH = (height * ratio).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(rotated, targetW, targetH, true)
            } else {
                rotated
            }

            // Compress to JPEG starting at quality 60
            var quality = 60
            var outputBytes = compressToJpeg(scaled, quality)

            // If still over 350 KB or data URI length > 400,000, reduce quality iteratively
            while ((outputBytes.size > MAX_BYTES || (outputBytes.size * 4 / 3) > (MAX_DATA_URI_CHARS - 50)) && quality > 15) {
                quality -= 10
                outputBytes = compressToJpeg(scaled, quality)
            }

            val base64 = Base64.encodeToString(outputBytes, Base64.NO_WRAP)
            val dataUri = "data:image/jpeg;base64,$base64"

            if (dataUri.length > MAX_DATA_URI_CHARS) {
                // Emergency scale down if somehow still over limit
                val emergencyScaled = Bitmap.createScaledBitmap(scaled, scaled.width / 2, scaled.height / 2, true)
                val emergencyBytes = compressToJpeg(emergencyScaled, 40)
                val emergencyBase64 = Base64.encodeToString(emergencyBytes, Base64.NO_WRAP)
                Pair(emergencyScaled, "data:image/jpeg;base64,$emergencyBase64")
            } else {
                Pair(scaled, dataUri)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun compressToJpeg(bitmap: Bitmap, quality: Int): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(10, 100), stream)
        return stream.toByteArray()
    }

    private fun getExifOrientation(context: Context, uri: Uri): Int {
        return try {
            val input = context.contentResolver.openInputStream(uri) ?: return 0
            val exif = ExifInterface(input)
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            input.close()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (_: Exception) {
            0
        }
    }
}
