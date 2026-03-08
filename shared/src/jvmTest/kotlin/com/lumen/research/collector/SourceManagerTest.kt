package com.lumen.research.collector

import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.MyObjectBox
import com.lumen.core.database.entities.Source
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SourceManagerTest {

    private lateinit var db: LumenDatabase
    private lateinit var tempDir: File
    private lateinit var manager: SourceManager

    @BeforeTest
    fun setup() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "objectbox-test-${System.nanoTime()}")
        tempDir.mkdirs()
        val store = MyObjectBox.builder()
            .baseDirectory(tempDir)
            .build()
        db = LumenDatabase(store)
        manager = SourceManager(db)
    }

    @AfterTest
    fun teardown() {
        db.close()
        tempDir.deleteRecursively()
    }

    @Test
    fun add_createsSourceWithTimestamp() {
        val source = manager.add(Source(name = "Test", url = "https://example.com/feed", type = "rss"))

        assertNotEquals(0L, source.id)
        assertEquals("Test", source.name)
        assertEquals("https://example.com/feed", source.url)
        assertTrue(source.createdAt > 0)
    }

    @Test
    fun remove_deletesSource() {
        val source = manager.add(Source(name = "To Remove", url = "https://example.com/feed", type = "rss"))

        manager.remove(source.id)

        assertNull(manager.get(source.id))
    }

    @Test
    fun update_modifiesSource() {
        val source = manager.add(Source(name = "Original", url = "https://example.com/feed", type = "rss"))
        val updated = manager.update(source.copy(name = "Updated"))

        assertEquals("Updated", updated.name)
        assertEquals(source.id, updated.id)
    }

    @Test
    fun get_returnsSourceById() {
        val source = manager.add(Source(name = "Find Me", url = "https://example.com/feed", type = "rss"))

        val found = manager.get(source.id)

        assertNotNull(found)
        assertEquals("Find Me", found.name)
    }

    @Test
    fun get_returnsNullForMissingId() {
        assertNull(manager.get(999L))
    }

    @Test
    fun listAll_returnsAllSources() {
        manager.add(Source(name = "Source 1", url = "https://example.com/1", type = "rss"))
        manager.add(Source(name = "Source 2", url = "https://example.com/2", type = "rss"))

        assertEquals(2, manager.listAll().size)
    }

    @Test
    fun listEnabled_returnsOnlyEnabledSources() {
        manager.add(Source(name = "Enabled", url = "https://example.com/1", type = "rss", enabled = true))
        manager.add(Source(name = "Disabled", url = "https://example.com/2", type = "rss", enabled = false))

        val enabled = manager.listEnabled()
        assertEquals(1, enabled.size)
        assertEquals("Enabled", enabled[0].name)
    }

    @Test
    fun toggleEnabled_flipsEnabledState() {
        val source = manager.add(Source(name = "Toggle Me", url = "https://example.com/feed", type = "rss", enabled = true))

        val toggled = manager.toggleEnabled(source.id)
        assertNotNull(toggled)
        assertFalse(toggled.enabled)

        val toggledBack = manager.toggleEnabled(source.id)
        assertNotNull(toggledBack)
        assertTrue(toggledBack.enabled)
    }

    @Test
    fun toggleEnabled_returnsNullForMissingId() {
        assertNull(manager.toggleEnabled(999L))
    }

    @Test
    fun seedDefaultsIfEmpty_seedsWhenEmpty() {
        assertTrue(db.sourceBox.isEmpty)

        manager.seedDefaultsIfEmpty()

        val sources = manager.listAll()
        assertEquals(SourceManager.DEFAULT_SOURCES.size, sources.size)
        assertTrue(sources.all { it.createdAt > 0 })

        val names = sources.map { it.name }.toSet()
        assertTrue("arXiv CS.AI" in names)
        assertTrue("arXiv CS.LG" in names)
        assertTrue("Hacker News" in names)
        assertTrue("Semantic Scholar" in names)
        assertTrue("OpenAI Blog" in names)
        assertTrue("GitHub Releases" in names)
    }

    @Test
    fun seedDefaultsIfEmpty_doesNotReseedWhenSourcesExist() {
        manager.add(Source(name = "Existing", url = "https://example.com/feed", type = "rss"))

        manager.seedDefaultsIfEmpty()

        assertEquals(1, manager.listAll().size)
        assertEquals("Existing", manager.listAll()[0].name)
    }

    @Test
    fun defaultSources_containsExpectedTypes() {
        val types = SourceManager.DEFAULT_SOURCES.map { it.type }.toSet()
        assertTrue("ARXIV_API" in types)
        assertTrue("SEMANTIC_SCHOLAR" in types)
        assertTrue("RSS" in types)
        assertTrue("GITHUB_RELEASES" in types)
    }

    @Test
    fun seedNewDefaults_addsNewSourcesWithoutDuplicating() {
        manager.seedDefaultsIfEmpty()
        val initialCount = manager.listAll().size

        manager.seedNewDefaults()

        assertEquals(initialCount, manager.listAll().size)
    }

    @Test
    fun seedNewDefaults_preservesExistingUserSources() {
        manager.add(Source(name = "My Custom Feed", url = "https://custom.example.com/feed", type = "RSS"))
        manager.seedNewDefaults()

        val all = manager.listAll()
        val customSource = all.find { it.name == "My Custom Feed" }
        assertNotNull(customSource)
        assertEquals(SourceManager.DEFAULT_SOURCES.size + 1, all.size)
    }

    @Test
    fun seedNewDefaults_onFreshInstall_noOpAfterSeed() {
        manager.seedDefaultsIfEmpty()
        val countAfterSeed = manager.listAll().size

        manager.seedNewDefaults()

        assertEquals(countAfterSeed, manager.listAll().size)
    }

    @Test
    fun recordFailure_incrementsFailuresAndSetsError() {
        val source = manager.add(Source(name = "Failing", url = "https://example.com/feed", type = "RSS"))

        val failed = manager.recordFailure(source.id, "Connection timeout")

        assertNotNull(failed)
        assertEquals(1, failed.consecutiveFailures)
        assertEquals("Connection timeout", failed.lastError)
        assertTrue(failed.nextRetryAt > 0)
    }

    @Test
    fun recordFailure_incrementsOnMultipleCalls() {
        val source = manager.add(Source(name = "Failing", url = "https://example.com/feed", type = "RSS"))

        manager.recordFailure(source.id, "Error 1")
        val failed2 = manager.recordFailure(source.id, "Error 2")

        assertNotNull(failed2)
        assertEquals(2, failed2.consecutiveFailures)
        assertEquals("Error 2", failed2.lastError)
    }

    @Test
    fun recordSuccess_resetsFailureState() {
        val source = manager.add(Source(name = "Recovering", url = "https://example.com/feed", type = "RSS"))
        manager.recordFailure(source.id, "Temporary error")

        val recovered = manager.recordSuccess(source.id)

        assertNotNull(recovered)
        assertEquals(0, recovered.consecutiveFailures)
        assertEquals("", recovered.lastError)
        assertEquals(0L, recovered.nextRetryAt)
        assertTrue(recovered.lastFetchedAt > 0)
    }

    @Test
    fun recordFailure_returnsNullForMissingId() {
        assertNull(manager.recordFailure(999L, "error"))
    }

    @Test
    fun recordSuccess_returnsNullForMissingId() {
        assertNull(manager.recordSuccess(999L))
    }

    @Test
    fun listRetryable_excludesCooldownSources() {
        val source = manager.add(Source(name = "Healthy", url = "https://example.com/1", type = "RSS"))
        manager.add(Source(
            name = "In Cooldown",
            url = "https://example.com/2",
            type = "RSS",
            nextRetryAt = System.currentTimeMillis() + 3600000,
        ))

        val retryable = manager.listRetryable()

        assertEquals(1, retryable.size)
        assertEquals(source.id, retryable[0].id)
    }

    @Test
    fun listRetryable_excludesDisabledSources() {
        manager.add(Source(name = "Disabled", url = "https://example.com/1", type = "RSS", enabled = false))
        manager.add(Source(name = "Enabled", url = "https://example.com/2", type = "RSS"))

        val retryable = manager.listRetryable()

        assertEquals(1, retryable.size)
        assertEquals("Enabled", retryable[0].name)
    }
}
