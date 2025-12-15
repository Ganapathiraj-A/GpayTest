package com.antigravity.gpaytest.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.InputStream

object ImageUtils {

    fun saveDrawableToGallery(context: Context, drawableId: Int, fileName: String): Uri? {
        val bitmap = BitmapFactory.decodeResource(context.resources, drawableId)
        val resolver = context.contentResolver
        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "$fileName.jpg")
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES)
        }
        val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        uri?.let {
            resolver.openOutputStream(it)?.use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
            }
        }
        return uri
    }

    fun compressUriToByteArray(context: Context, uri: Uri): ByteArray? {
        // Legacy: For Uploads
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val bytes = inputStream?.readBytes()
            inputStream?.close()
            bytes
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // NEW: Compress to Base64 (Max 1MB ~ 500KB target)
    fun compressToBase64(context: Context, uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (originalBitmap == null) return null

            var quality = 80
            var stream = ByteArrayOutputStream()
            originalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)

            // Loop to reduce size if > 800KB (Safety margin for Firestore 1MB limit)
            while (stream.toByteArray().size > 800 * 1024 && quality > 10) {
                stream = ByteArrayOutputStream() // Reset
                quality -= 10
                originalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            }

            val byteArray = stream.toByteArray()
            Base64.encodeToString(byteArray, Base64.DEFAULT)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    fun decodeBase64ToBitmap(base64Str: String): Bitmap? {
        return try {
             val decodedBytes = Base64.decode(base64Str, Base64.DEFAULT)
             BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
