package com.example.tielink.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object OriginalResumeFileStore {
    fun copyFromUri(context: Context, uri: Uri, displayName: String): Result<File> = runCatching {
        val safeName = displayName
            .substringAfterLast('/')
            .replace(Regex("""[^\p{L}\p{N}._-]"""), "_")
            .ifBlank { "resume_file" }
        val directory = File(context.filesDir, "resume_originals").apply { mkdirs() }
        val target = File(directory, "${System.currentTimeMillis()}_$safeName")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use(input::copyTo)
        } ?: error("无法读取所选文件")
        target
    }

    fun open(context: Context, path: String, mimeType: String): Result<Unit> = runCatching {
        val file = File(path)
        require(file.exists()) { "原文件不存在" }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mimeType.ifBlank { "*/*" })
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
