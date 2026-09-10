package com.example.ui.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object ImageUtils {

    /**
     * Safely decodes, rotates according to EXIF, rescales to max 512x512,
     * and saves into the app's internal files directory as business_logo.png.
     * Returns the absolute path if successful, or null on error.
     */
    fun saveLogoFromUri(context: Context, uri: Uri): String? {
        var inputStream: InputStream? = null
        try {
            val contentResolver = context.contentResolver

            // 1. Decode bounds only to prevent OutOfMemory on 12MP/48MP Samsung cameras
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            inputStream = contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream, null, boundsOptions)
            inputStream?.close()

            val srcWidth = boundsOptions.outWidth
            val srcHeight = boundsOptions.outHeight
            if (srcWidth <= 0 || srcHeight <= 0) return null

            // Target max dimension
            val maxDimension = 512
            var inSampleSize = 1
            while ((srcWidth / inSampleSize) > maxDimension * 1.5 || (srcHeight / inSampleSize) > maxDimension * 1.5) {
                inSampleSize *= 2
            }

            // 2. Decode actual scaled bitmap
            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            inputStream = contentResolver.openInputStream(uri)
            val decodedBitmap = BitmapFactory.decodeStream(inputStream, null, decodeOptions)
            inputStream?.close()

            if (decodedBitmap == null) return null

            // 3. Read EXIF rotation for Samsung Gallery compatibility
            var rotationDegrees = 0
            try {
                contentResolver.openInputStream(uri)?.use { exifStream ->
                    val exif = ExifInterface(exifStream)
                    val orientation = exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                    rotationDegrees = when (orientation) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270
                        else -> 0
                    }
                }
            } catch (e: Exception) {
                // EXIF parsing failure is non-fatal
            }

            val finalBitmap = if (rotationDegrees != 0) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                val rotated = Bitmap.createBitmap(
                    decodedBitmap,
                    0,
                    0,
                    decodedBitmap.width,
                    decodedBitmap.height,
                    matrix,
                    true
                )
                if (rotated != decodedBitmap) {
                    decodedBitmap.recycle()
                }
                rotated
            } else {
                decodedBitmap
            }

            // 4. Save to app internal files directory
            val destFile = File(context.filesDir, "business_logo.png")
            FileOutputStream(destFile).use { out ->
                finalBitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
                out.flush()
            }
            finalBitmap.recycle()

            return destFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        } finally {
            try {
                inputStream?.close()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    /**
     * Safely decodes, rotates, rescales and saves a signature into internal files directory as business_signature.png.
     */
    fun saveSignatureFromUri(context: Context, uri: Uri): String? {
        var inputStream: InputStream? = null
        try {
            val contentResolver = context.contentResolver
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            inputStream = contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream, null, boundsOptions)
            inputStream?.close()

            val srcWidth = boundsOptions.outWidth
            val srcHeight = boundsOptions.outHeight
            if (srcWidth <= 0 || srcHeight <= 0) return null

            val maxDimension = 512
            var inSampleSize = 1
            while ((srcWidth / inSampleSize) > maxDimension * 1.5 || (srcHeight / inSampleSize) > maxDimension * 1.5) {
                inSampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            inputStream = contentResolver.openInputStream(uri)
            val decodedBitmap = BitmapFactory.decodeStream(inputStream, null, decodeOptions)
            inputStream?.close()

            if (decodedBitmap == null) return null

            val destFile = File(context.filesDir, "business_signature.png")
            FileOutputStream(destFile).use { out ->
                decodedBitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
                out.flush()
            }
            decodedBitmap.recycle()
            return destFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        } finally {
            try { inputStream?.close() } catch (e: Exception) { }
        }
    }

    /**
     * Safely loads a bitmap from file path with dimension bounds checking.
     */
    fun loadLogoBitmap(filePath: String?, maxDim: Int = 256): Bitmap? {
        if (filePath.isNullOrBlank()) return null
        val file = File(filePath)
        if (!file.exists() || !file.canRead() || file.length() == 0L) return null

        return try {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, boundsOptions)

            var sample = 1
            while ((boundsOptions.outWidth / sample) > maxDim * 1.5 || (boundsOptions.outHeight / sample) > maxDim * 1.5) {
                sample *= 2
            }

            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeFile(file.absolutePath, opts)
        } catch (e: Exception) {
            null
        }
    }
}
