package com.omrscanner.app.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/** App-private storage for scans, plus sharing and saving to Downloads. */
class FileStore(private val context: Context) {
    private fun dir(name: String) = File(context.filesDir, name).apply { mkdirs() }

    fun saveJpeg(bmp: Bitmap, folder: String, quality: Int = 88): String {
        val f = File(dir(folder), UUID.randomUUID().toString() + ".jpg")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, quality, it) }
        return f.absolutePath
    }

    /** Copies an image the document scanner returned into app storage. */
    fun importUri(uri: Uri, folder: String): String? = runCatching {
        val f = File(dir(folder), UUID.randomUUID().toString() + ".jpg")
        context.contentResolver.openInputStream(uri)?.use { input -> f.outputStream().use { input.copyTo(it) } }
        f.absolutePath
    }.getOrNull()

    fun exportFile(name: String) = File(dir("exports"), name.replace(Regex("[\\\\/:*?\"<>|]+"), "-"))

    fun delete(path: String?) { if (path != null) runCatching { File(path).delete() } }

    fun share(file: File, mime: String, title: String) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun open(file: File, mime: String) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(view) }
    }

    /** Copies a file into the public Downloads folder. Returns a short description of where it went. */
    fun saveToDownloads(file: File, mime: String): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, file.name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/OMR Scanner")
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        if (uri != null) context.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
        "Downloads/OMR Scanner/${file.name}"
    } else {
        val target = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), file.name)
        file.copyTo(target, overwrite = true)
        target.absolutePath
    }
}
