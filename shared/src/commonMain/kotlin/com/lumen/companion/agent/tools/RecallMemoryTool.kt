package com.lumen.companion.agent.tools

import ai.koog.agents.core.tools.SimpleTool
import com.lumen.core.memory.MemoryManager
import kotlinx.serialization.Serializable

@Serializable
data class RecallMemoryArgs(
    val query: String,
    val limit: Int = 5,
)

class RecallMemoryTool(
    private val memoryManager: MemoryManager,
) : SimpleTool<RecallMemoryArgs>(
    RecallMemoryArgs.serializer(),
    "recall_memory",
    "Retrieve relevant memories matching a query",
) {
    override suspend fun execute(args: RecallMemoryArgs): String {
        val entries = memoryManager.recall(args.query, args.limit)
        if (entries.isEmpty()) return "No memories found."
        return entries.joinToString("\n") { "- ${it.content}" }
    }
}
