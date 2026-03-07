package com.lumen.core.memory

import com.lumen.core.database.entities.MemoryEntry

sealed class SynthesisResult {
    data object NoMatch : SynthesisResult()
    data object Kept : SynthesisResult()
    data class Merged(val entry: MemoryEntry) : SynthesisResult()
}
