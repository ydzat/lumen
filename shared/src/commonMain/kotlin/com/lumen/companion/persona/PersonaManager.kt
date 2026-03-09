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
                    name = DEFAULT_PERSONA_NAME,
                    systemPrompt = DEFAULT_PERSONA_PROMPT,
                    greeting = "Hi! I'm Lumen. How can I help you today?",
                    avatarEmoji = "star",
                    isBuiltIn = true,
                    isActive = true,
                    createdAt = now,
                ),
                Persona(
                    name = FORMAL_STYLE_NAME,
                    systemPrompt = FORMAL_STYLE_PROMPT,
                    greeting = "Hello. How may I assist you?",
                    avatarEmoji = "books",
                    isBuiltIn = true,
                    isActive = false,
                    createdAt = now,
                ),
                Persona(
                    name = CASUAL_STYLE_NAME,
                    systemPrompt = CASUAL_STYLE_PROMPT,
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
        const val DEFAULT_PERSONA_NAME = "Lumen"
        const val FORMAL_STYLE_NAME = "Formal"
        const val CASUAL_STYLE_NAME = "Casual"

        internal const val DEFAULT_PERSONA_PROMPT =
            "You are Lumen, a personal AI assistant and companion. " +
                "You help with research (finding papers, analyzing trends, generating digests) " +
                "and daily conversations alike. You remember things the user tells you across sessions. " +
                "Be warm but informative, concise but thorough when needed. " +
                "Adapt your tone naturally to the context: more structured for research questions, " +
                "more conversational for casual chat."

        internal const val FORMAL_STYLE_PROMPT =
            "You are Lumen, a personal AI assistant and companion. " +
                "You help with research and daily conversations alike, remembering things across sessions. " +
                "Use a structured, precise, and academic tone. Organize responses with clear headings " +
                "and bullet points when appropriate. Be thorough and well-sourced."

        internal const val CASUAL_STYLE_PROMPT =
            "You are Lumen, a personal AI assistant and companion. " +
                "You help with research and daily conversations alike, remembering things across sessions. " +
                "Be warm, conversational, and approachable. Use natural language and keep responses " +
                "concise but engaging."
    }
}
