package com.lumen.core.document

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayInputStream

actual class DocumentParser actual constructor() {

    actual suspend fun extractText(fileBytes: ByteArray, mimeType: String): String {
        return when (mimeType) {
            MimeTypes.PDF -> extractPdfText(fileBytes)
            MimeTypes.PLAIN, MimeTypes.MARKDOWN -> fileBytes.decodeToString()
            else -> throw IllegalArgumentException("Unsupported MIME type: $mimeType")
        }
    }

    private fun extractPdfText(fileBytes: ByteArray): String {
        val document = PDDocument.load(ByteArrayInputStream(fileBytes))
        return document.use { doc ->
            PDFTextStripper().getText(doc)
        }
    }
}
