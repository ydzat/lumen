package com.lumen.ui

actual suspend fun pickFile(allowedExtensions: List<String>): PickedFile? {
    // Android file picking requires Activity context with ActivityResultContracts.
    // Full implementation deferred — JVM (Desktop) is the primary target for M3.1.
    return null
}
