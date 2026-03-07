package com.lumen.ui

data class PickedFile(
    val name: String,
    val bytes: ByteArray,
    val mimeType: String,
)

expect suspend fun pickFile(allowedExtensions: List<String>): PickedFile?
