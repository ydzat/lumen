package com.lumen.companion.agent.tools

import ai.koog.agents.core.tools.SimpleTool
import com.lumen.core.memory.MemoryManager
import kotlinx.serialization.Serializable

@Serializable
data class StoreMemoryArgs(
    val content: String,
    val category: String = "general",
)

class StoreMemoryTool(
    private val memoryManager: MemoryManager,
) : SimpleTool<StoreMemoryArgs>(
    StoreMemoryArgs.serializer(),
    "store_memory",
    "Store important information as a long-term memory",
) {
    override suspend fun execute(args: StoreMemoryArgs): String {
        val entry = memoryManager.store(args.content, args.category, "conversation")
        return "Memory stored (id=${entry.id}): ${entry.content}"
    }
}
