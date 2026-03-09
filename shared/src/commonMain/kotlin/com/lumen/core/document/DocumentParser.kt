package com.lumen.core.document

expect class DocumentParser() {
    suspend fun extractText(fileBytes: ByteArray, mimeType: String): String
}
