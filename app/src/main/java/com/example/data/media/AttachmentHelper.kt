package com.example.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.FileProvider
import com.example.data.local.AttachmentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class PendingAttachment(
    val type: AttachmentType,
    val localPath: String,
    val previewPath: String?,
    val fileName: String,
    val metadataSummary: String
)

object AttachmentHelper {
    private const val TAG = "AttachmentHelper"

    private fun attachmentsDir(context: Context): File {
        return File(context.filesDir, "attachments").apply { if (!exists()) mkdirs() }
    }

    fun createCameraUri(context: Context): Pair<Uri, File> {
        val cacheDir = File(context.cacheDir, "camera").apply { if (!exists()) mkdirs() }
        val photoFile = File(cacheDir, "cam_${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            photoFile
        )
        return uri to photoFile
    }

    suspend fun importImageFromUri(context: Context, uri: Uri): Result<PendingAttachment> =
        withContext(Dispatchers.IO) {
            try {
                val fileName = queryDisplayName(context, uri) ?: "imagen_${System.currentTimeMillis()}.jpg"
                val destFile = File(attachmentsDir(context), "${UUID.randomUUID()}_$fileName")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: error("No se pudo abrir la imagen seleccionada.")

                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(destFile.absolutePath, bounds)
                val sizeLabel = formatBytes(destFile.length())
                val dims = if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                    "${bounds.outWidth}×${bounds.outHeight} px • $sizeLabel"
                } else {
                    sizeLabel
                }

                Result.success(
                    PendingAttachment(
                        type = AttachmentType.IMAGE,
                        localPath = destFile.absolutePath,
                        previewPath = destFile.absolutePath,
                        fileName = fileName,
                        metadataSummary = dims
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error importing image", e)
                Result.failure(e)
            }
        }

    suspend fun importImageFromFile(context: Context, sourceFile: File): Result<PendingAttachment> =
        withContext(Dispatchers.IO) {
            try {
                if (!sourceFile.exists() || sourceFile.length() == 0L) {
                    error("No se capturó ninguna fotografía.")
                }
                val destFile = File(attachmentsDir(context), "${UUID.randomUUID()}_foto.jpg")
                sourceFile.copyTo(destFile, overwrite = true)

                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(destFile.absolutePath, bounds)
                val sizeLabel = formatBytes(destFile.length())
                val dims = if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                    "${bounds.outWidth}×${bounds.outHeight} px • $sizeLabel"
                } else {
                    sizeLabel
                }

                Result.success(
                    PendingAttachment(
                        type = AttachmentType.IMAGE,
                        localPath = destFile.absolutePath,
                        previewPath = destFile.absolutePath,
                        fileName = destFile.name,
                        metadataSummary = dims
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error importing camera file", e)
                Result.failure(e)
            }
        }

    suspend fun importPdfFromUri(context: Context, uri: Uri): Result<PendingAttachment> =
        withContext(Dispatchers.IO) {
            try {
                val rawName = queryDisplayName(context, uri) ?: "documento_${System.currentTimeMillis()}.pdf"
                val safeName = if (rawName.endsWith(".pdf", ignoreCase = true)) rawName else "$rawName.pdf"
                val destFile = File(attachmentsDir(context), "${UUID.randomUUID()}_$safeName")

                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: error("No se pudo leer el documento PDF.")

                var pageCount = 0
                var previewFilePath: String? = null
                var pfd: ParcelFileDescriptor? = null
                var renderer: PdfRenderer? = null
                try {
                    pfd = ParcelFileDescriptor.open(destFile, ParcelFileDescriptor.MODE_READ_ONLY)
                    renderer = PdfRenderer(pfd)
                    pageCount = renderer.pageCount
                    if (pageCount > 0) {
                        renderer.openPage(0).use { page ->
                            val targetWidth = 420
                            val targetHeight = ((page.height.toFloat() / page.width.coerceAtLeast(1)) * targetWidth)
                                .toInt()
                                .coerceIn(200, 720)
                            val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(bitmap)
                            canvas.drawColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                            val thumbFile = File(attachmentsDir(context), "thumb_${UUID.randomUUID()}.png")
                            FileOutputStream(thumbFile).use { out ->
                                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
                            }
                            bitmap.recycle()
                            previewFilePath = thumbFile.absolutePath
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "PdfRenderer preview failed, keeping metadata only", e)
                } finally {
                    renderer?.close()
                    pfd?.close()
                }

                val sizeLabel = formatBytes(destFile.length())
                val meta = if (pageCount > 0) {
                    "$pageCount pág. • $sizeLabel"
                } else {
                    "PDF • $sizeLabel"
                }

                Result.success(
                    PendingAttachment(
                        type = AttachmentType.PDF,
                        localPath = destFile.absolutePath,
                        previewPath = previewFilePath,
                        fileName = safeName,
                        metadataSummary = meta
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error importing PDF", e)
                Result.failure(e)
            }
        }

    suspend fun loadScaledBitmap(path: String, maxDim: Int = 1600): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

                var sampleSize = 1
                while (bounds.outWidth / sampleSize > maxDim || bounds.outHeight / sampleSize > maxDim) {
                    sampleSize *= 2
                }
                val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                BitmapFactory.decodeFile(path, opts)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading bitmap for Gemini", e)
                null
            }
        }

    suspend fun readPdfBytes(path: String, maxBytes: Long = 18L * 1024 * 1024): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            try {
                val file = File(path)
                if (!file.exists()) {
                    return@withContext Result.failure(IllegalStateException("El archivo PDF ya no existe en el dispositivo."))
                }
                if (file.length() > maxBytes) {
                    return@withContext Result.failure(
                        IllegalArgumentException("El PDF supera el límite de 18 MB para envío directo a Gemini.")
                    )
                }
                Result.success(file.readBytes())
            } catch (e: Exception) {
                Log.e(TAG, "Error reading PDF bytes", e)
                Result.failure(e)
            }
        }

    suspend fun saveGeneratedBitmap(context: Context, bitmap: Bitmap): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val outDir = File(context.filesDir, "generated_images").apply { if (!exists()) mkdirs() }
                val file = File(outDir, "gen_${UUID.randomUUID()}.png")
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
                }
                Result.success(file.absolutePath)
            } catch (e: Exception) {
                Log.e(TAG, "Error saving generated bitmap", e)
                Result.failure(e)
            }
        }

    fun openPdfExternally(context: Context, path: String): Boolean {
        return try {
            val file = File(path)
            if (!file.exists()) return false
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "No PDF viewer installed or unable to open PDF", e)
            false
        }
    }

    fun shareImageFile(context: Context, path: String): Boolean {
        return try {
            val file = File(path)
            if (!file.exists()) return false
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(android.content.Intent.createChooser(intent, "Compartir imagen de ChatDep"))
            true
        } catch (e: Exception) {
            Log.w(TAG, "Unable to share image", e)
            false
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else null
                }
        } catch (_: Exception) {
            null
        }
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }
}
