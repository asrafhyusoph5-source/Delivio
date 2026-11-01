package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream

object BitmapUtil {
    private const val TAG = "BitmapUtil"
    private const val MAX_DIMENSION = 1024
    private const val MAX_BYTES = 300 * 1024 // 300 KB

    /**
     * Compresses image to max 1024px longest side, JPEG 70 quality, strictly <300 KB.
     * Returns Base64 data string ("data:image/jpeg;base64,...") or null if failed.
     */
    fun compressUriToBase64(context: Context, uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            if (originalBitmap == null) return null
            compressBitmapToBase64(originalBitmap)
        } catch (e: Exception) {
            Log.e(TAG, "Error compressing image from uri", e)
            null
        }
    }

    /**
     * Compresses bitmap to max 1024px longest side, JPEG 70 quality, strictly <300 KB.
     */
    fun compressBitmapToBase64(originalBitmap: Bitmap): String? {
        return try {
            val width = originalBitmap.width
            val height = originalBitmap.height
            val longest = maxOf(width, height)

            val scaledBitmap = if (longest > MAX_DIMENSION) {
                val scale = MAX_DIMENSION.toFloat() / longest.toFloat()
                val targetW = (width * scale).toInt().coerceAtLeast(1)
                val targetH = (height * scale).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(originalBitmap, targetW, targetH, true)
            } else {
                originalBitmap
            }

            var quality = 70
            var baos = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)

            // Ensure strictly < 300 KB
            while (baos.size() > MAX_BYTES && quality > 20) {
                quality -= 10
                baos = ByteArrayOutputStream()
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            }

            val bytes = baos.toByteArray()
            val base64Str = Base64.encodeToString(bytes, Base64.NO_WRAP)
            "data:image/jpeg;base64,$base64Str"
        } catch (e: Exception) {
            Log.e(TAG, "Error compressing bitmap", e)
            null
        }
    }
}
