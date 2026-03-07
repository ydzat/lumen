package com.lumen.core.document

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper

actual class DocumentParser actual constructor() {

    actual suspend fun extractText(fileBytes: ByteArray, mimeType: String): String {
        return when (mimeType) {
            MimeTypes.PDF -> extractPdfText(fileBytes)
            MimeTypes.PLAIN, MimeTypes.MARKDOWN -> fileBytes.decodeToString()
            else -> throw IllegalArgumentException("Unsupported MIME type: $mimeType")
        }
    }

    private fun extractPdfText(fileBytes: ByteArray): String {
        val document = Loader.loadPDF(fileBytes)
        return document.use { doc ->
            PDFTextStripper().getText(doc)
        }
    }
}
