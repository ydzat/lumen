package com.lumen.companion.persona

import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.MyObjectBox
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PersonaManagerTest {

    private lateinit var db: LumenDatabase
    private lateinit var tempDir: File
    private lateinit var manager: PersonaManager

    @BeforeTest
    fun setup() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "objectbox-test-${System.nanoTime()}")
        tempDir.mkdirs()
        val store = MyObjectBox.builder()
            .baseDirectory(tempDir)
            .build()
        db = LumenDatabase(store)
        manager = PersonaManager(db)
    }

    @AfterTest
    fun teardown() {
        db.close()
        tempDir.deleteRecursively()
    }

    @Test
    fun seedBuiltInPersonas_createsDefaultPersonas() {
        manager.seedBuiltInPersonas()

        val personas = manager.listAll()
        assertEquals(3, personas.size)

        val lumen = personas.find { it.name == PersonaManager.DEFAULT_PERSONA_NAME }
        assertNotNull(lumen)
        assertTrue(lumen.isBuiltIn)
        assertTrue(lumen.isActive)
        assertTrue(lumen.systemPrompt.isNotBlank())

        val formal = personas.find { it.name == PersonaManager.FORMAL_STYLE_NAME }
        assertNotNull(formal)
        assertTrue(formal.isBuiltIn)
        assertFalse(formal.isActive)

        val casual = personas.find { it.name == PersonaManager.CASUAL_STYLE_NAME }
        assertNotNull(casual)
        assertTrue(casual.isBuiltIn)
        assertFalse(casual.isActive)
    }

    @Test
    fun seedBuiltInPersonas_isIdempotent() {
        manager.seedBuiltInPersonas()
        manager.seedBuiltInPersonas()

        assertEquals(3, manager.listAll().size)
    }

    @Test
    fun setActive_deactivatesPreviousAndActivatesTarget() {
        manager.seedBuiltInPersonas()
        val personas = manager.listAll()
        val lumen = personas.find { it.name == PersonaManager.DEFAULT_PERSONA_NAME }!!
        val casual = personas.find { it.name == PersonaManager.CASUAL_STYLE_NAME }!!

        assertTrue(manager.getActive()!!.id == lumen.id)

        manager.setActive(casual.id)

        val activeAfter = manager.getActive()
        assertNotNull(activeAfter)
        assertEquals(casual.id, activeAfter.id)

        val lumenAfter = manager.get(lumen.id)
        assertNotNull(lumenAfter)
        assertFalse(lumenAfter.isActive)
    }

    @Test
    fun getActive_withNoActive_returnsNull() {
        assertNull(manager.getActive())
    }

    @Test
    fun create_addsCustomPersona() {
        val persona = manager.create(
            name = "Custom Bot",
            systemPrompt = "You are a custom bot.",
            greeting = "Hello!",
            avatarEmoji = "robot",
        )

        assertNotEquals(0L, persona.id)
        assertEquals("Custom Bot", persona.name)
        assertFalse(persona.isBuiltIn)
        assertFalse(persona.isActive)
    }

    @Test
    fun delete_removesCustomPersona() {
        val persona = manager.create("Temp", "temp prompt")

        manager.delete(persona.id)

        assertNull(manager.get(persona.id))
    }

    @Test
    fun delete_builtInPersona_throwsException() {
        manager.seedBuiltInPersonas()
        val builtIn = manager.listAll().first { it.isBuiltIn }

        assertFailsWith<IllegalArgumentException> {
            manager.delete(builtIn.id)
        }
    }

    @Test
    fun update_changesPersonaFields() {
        val persona = manager.create("Original", "original prompt")

        val updated = manager.update(persona.copy(name = "Updated", systemPrompt = "new prompt"))

        assertEquals("Updated", updated.name)
        assertEquals("new prompt", updated.systemPrompt)
    }

    @Test
    fun update_withoutId_throwsException() {
        assertFailsWith<IllegalArgumentException> {
            manager.update(com.lumen.core.database.entities.Persona(name = "No ID"))
        }
    }
}
