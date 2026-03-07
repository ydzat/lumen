package com.lumen.core.document

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayInputStream

actual class DocumentParser actual constructor() {

    actual suspend fun extractText(fileBytes: ByteArray, mimeType: String): String {
        return when (mimeType) {
            MIME_PDF -> extractPdfText(fileBytes)
            MIME_PLAIN, MIME_MARKDOWN -> fileBytes.decodeToString()
            else -> throw IllegalArgumentException("Unsupported MIME type: $mimeType")
        }
    }

    private fun extractPdfText(fileBytes: ByteArray): String {
        val document = PDDocument.load(ByteArrayInputStream(fileBytes))
        return document.use { doc ->
            PDFTextStripper().getText(doc)
        }
    }

    companion object {
        const val MIME_PDF = "application/pdf"
        const val MIME_PLAIN = "text/plain"
        const val MIME_MARKDOWN = "text/markdown"
    }
}
