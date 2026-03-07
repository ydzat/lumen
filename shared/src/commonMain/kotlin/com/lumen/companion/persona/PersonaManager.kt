package com.lumen.companion.persona

import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.Persona

class PersonaManager(private val db: LumenDatabase) {

    fun seedBuiltInPersonas() {
        if (db.personaBox.all.any { it.isBuiltIn }) return
        val now = System.currentTimeMillis()
        db.personaBox.put(
            listOf(
                Persona(
                    name = RESEARCH_ASSISTANT_NAME,
                    systemPrompt = RESEARCH_ASSISTANT_PROMPT,
                    greeting = "How can I help with your research today?",
                    avatarEmoji = "microscope",
                    isBuiltIn = true,
                    isActive = true,
                    createdAt = now,
                ),
                Persona(
                    name = CASUAL_COMPANION_NAME,
                    systemPrompt = CASUAL_COMPANION_PROMPT,
                    greeting = "Hey! What's on your mind?",
                    avatarEmoji = "sparkles",
                    isBuiltIn = true,
                    isActive = false,
                    createdAt = now,
                ),
            )
        )
    }

    fun getActive(): Persona? {
        return db.personaBox.all.firstOrNull { it.isActive }
    }

    fun setActive(id: Long) {
        val all = db.personaBox.all
        val updated = all.map { it.copy(isActive = it.id == id) }
        db.personaBox.put(updated)
    }

    fun listAll(): List<Persona> {
        return db.personaBox.all
    }

    fun get(id: Long): Persona? {
        return db.personaBox.get(id)
    }

    fun create(
        name: String,
        systemPrompt: String,
        greeting: String = "",
        avatarEmoji: String = "",
    ): Persona {
        val now = System.currentTimeMillis()
        val persona = Persona(
            name = name,
            systemPrompt = systemPrompt,
            greeting = greeting,
            avatarEmoji = avatarEmoji,
            isBuiltIn = false,
            isActive = false,
            createdAt = now,
        )
        val id = db.personaBox.put(persona)
        return db.personaBox.get(id)
    }

    fun update(persona: Persona): Persona {
        require(persona.id != 0L) { "Cannot update a persona without an id" }
        db.personaBox.put(persona)
        return db.personaBox.get(persona.id)
    }

    fun delete(id: Long) {
        val persona = db.personaBox.get(id) ?: return
        require(!persona.isBuiltIn) { "Cannot delete built-in persona '${persona.name}'" }
        db.personaBox.remove(id)
    }

    companion object {
        const val RESEARCH_ASSISTANT_NAME = "Research Assistant"
        const val CASUAL_COMPANION_NAME = "Casual Companion"

        internal const val RESEARCH_ASSISTANT_PROMPT =
            "You are a research assistant. Be structured, precise, and academic in your responses. " +
                "Focus on providing well-sourced, factual information. Use clear organization with " +
                "bullet points and headings when appropriate."

        internal const val CASUAL_COMPANION_PROMPT =
            "You are a friendly, casual companion. Be warm, conversational, and approachable. " +
                "Use natural language and show genuine interest in the conversation. " +
                "Keep responses concise but engaging."
    }
}
