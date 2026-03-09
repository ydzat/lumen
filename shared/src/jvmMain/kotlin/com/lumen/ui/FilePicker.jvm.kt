package com.lumen.ui

import com.lumen.core.document.MimeTypes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.FilenameFilter

actual suspend fun pickFile(allowedExtensions: List<String>): PickedFile? = withContext(Dispatchers.IO) {
    val dialog = FileDialog(null as Frame?, "Select Document", FileDialog.LOAD)
    dialog.filenameFilter = FilenameFilter { _, name ->
        allowedExtensions.any { name.lowercase().endsWith(it) }
    }
    dialog.isVisible = true

    val directory = dialog.directory ?: return@withContext null
    val filename = dialog.file ?: return@withContext null
    val file = File(directory, filename)
    if (!file.exists()) return@withContext null

    val mimeType = when {
        filename.lowercase().endsWith(".pdf") -> MimeTypes.PDF
        filename.lowercase().endsWith(".md") -> MimeTypes.MARKDOWN
        else -> MimeTypes.PLAIN
    }

    PickedFile(
        name = filename,
        bytes = file.readBytes(),
        mimeType = mimeType,
    )
}
