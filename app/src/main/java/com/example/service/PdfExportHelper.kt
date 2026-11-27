package com.example.service

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.content.FileProvider
import com.example.domain.model.PdfOrientation
import com.example.domain.model.PdfPageOptions
import com.example.domain.model.PdfPaperSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream

object PdfExportHelper {

    private const val MAX_IMPORT_BYTES = 25 * 1024 * 1024 // 25 MB safety limit

    fun getExportCacheDir(context: Context): File =
        File(context.cacheDir, "exports").apply { mkdirs() }

    fun cleanupStaleExports(context: Context, keepFile: File? = null, maxAgeMs: Long = 24 * 60 * 60 * 1000L) {
        runCatching {
            val dir = getExportCacheDir(context)
            val cutoff = System.currentTimeMillis() - maxAgeMs
            dir.listFiles()?.forEach { file ->
                if (file != keepFile && file.isFile && file.lastModified() < cutoff) {
                    file.delete()
                }
            }
        }
    }

    fun sanitizedPdfFilename(invoiceNumber: String): String {
        val clean = invoiceNumber.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "invoice" }
        return "$clean.pdf"
    }

    /**
     * Renders every page of a generated PDF file into crisp high-DPI bitmaps via Android PdfRenderer.
     * Preserves exact aspect ratio when clamping maximum render dimensions and cleans up partial
     * bitmaps if rendering fails mid-document.
     */
    suspend fun renderPdfPages(pdfFile: File, scale: Float = 2.0f): Result<List<Bitmap>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val pages = mutableListOf<Bitmap>()
                try {
                    ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                        PdfRenderer(pfd).use { renderer ->
                            for (i in 0 until renderer.pageCount) {
                                renderer.openPage(i).use { page ->
                                    val pw = page.width.coerceAtLeast(1)
                                    val ph = page.height.coerceAtLeast(1)
                                    val uniformScale = minOf(
                                        scale,
                                        2400f / pw.toFloat(),
                                        3200f / ph.toFloat()
                                    ).coerceAtLeast(0.5f)
                                    val width = (pw * uniformScale).toInt().coerceAtLeast(1)
                                    val height = (ph * uniformScale).toInt().coerceAtLeast(1)
                                    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                    bmp.eraseColor(Color.WHITE)
                                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    pages.add(bmp)
                                }
                            }
                        }
                    }
                    pages
                } catch (t: Throwable) {
                    pages.forEach { if (!it.isRecycled) it.recycle() }
                    throw t
                }
            }
        }

    private fun openTruncatedOutputStream(context: Context, targetUri: Uri): OutputStream? {
        // Android 10+ (API 29+) requires explicit "wt" mode so overwriting an existing SAF document
        // truncates trailing bytes from a longer previous file.
        return try {
            context.contentResolver.openOutputStream(targetUri, "wt")
        } catch (_: Exception) {
            context.contentResolver.openOutputStream(targetUri)
        }
    }

    suspend fun copyFileToUri(context: Context, sourceFile: File, targetUri: Uri): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                openTruncatedOutputStream(context, targetUri)?.use { out ->
                    FileInputStream(sourceFile).use { input ->
                        input.copyTo(out)
                        out.flush()
                    }
                } ?: throw IllegalStateException("Unable to open destination file.")
                Unit
            }
        }

    suspend fun writeTextToUri(context: Context, content: String, targetUri: Uri): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                openTruncatedOutputStream(context, targetUri)?.use { out ->
                    out.write(content.toByteArray(Charsets.UTF_8))
                    out.flush()
                } ?: throw IllegalStateException("Unable to open destination file.")
            }
        }

    suspend fun readTextFromUri(context: Context, uri: Uri): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val buffer = ByteArray(16 * 1024)
                    val baos = ByteArrayOutputStream()
                    var totalRead = 0
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        totalRead += read
                        if (totalRead > MAX_IMPORT_BYTES) {
                            throw IllegalArgumentException("Selected file exceeds the 25 MB backup limit.")
                        }
                        baos.write(buffer, 0, read)
                    }
                    baos.toString(Charsets.UTF_8.name())
                } ?: throw IllegalStateException("Unable to read selected file.")
            }
        }

    fun shareFile(
        context: Context,
        file: File,
        mimeType: String,
        chooserTitle: String
    ): Result<Unit> = runCatching {
        require(file.exists()) { "Export file is not available." }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(chooserTitle, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, chooserTitle).apply {
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(chooser)
    }

    fun printPdfFile(
        context: Context,
        pdfFile: File,
        jobName: String,
        pdfOptions: PdfPageOptions? = null
    ): Result<Unit> = runCatching {
        require(pdfFile.exists()) { "PDF file is not ready to print." }
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            ?: throw IllegalStateException("Android Print service is unavailable on this device.")

        val attributesBuilder = PrintAttributes.Builder()
        if (pdfOptions != null) {
            val baseMedia = when (PdfPaperSize.fromCode(pdfOptions.paperSize)) {
                PdfPaperSize.A4 -> PrintAttributes.MediaSize.ISO_A4
                PdfPaperSize.LETTER -> PrintAttributes.MediaSize.NA_LETTER
                PdfPaperSize.LEGAL -> PrintAttributes.MediaSize.NA_LEGAL
            }
            val orientedMedia = if (PdfOrientation.fromCode(pdfOptions.orientation) == PdfOrientation.LANDSCAPE) {
                baseMedia.asLandscape()
            } else {
                baseMedia.asPortrait()
            }
            attributesBuilder.setMediaSize(orientedMedia)
        }

        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onLayoutCancelled()
                    return
                }
                val info = PrintDocumentInfo.Builder(pdfFile.name)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                    .build()
                callback.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onWriteCancelled()
                    return
                }
                try {
                    FileInputStream(pdfFile).use { input ->
                        FileOutputStream(destination.fileDescriptor).use { output ->
                            val buffer = ByteArray(8192)
                            while (true) {
                                if (cancellationSignal?.isCanceled == true) {
                                    callback.onWriteCancelled()
                                    return
                                }
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback.onWriteFailed(e.message ?: "Unable to write print stream.")
                }
            }
        }
        printManager.print(jobName, adapter, attributesBuilder.build())
    }
}
