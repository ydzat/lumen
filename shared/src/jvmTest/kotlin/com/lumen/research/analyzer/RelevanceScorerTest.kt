package com.lumen.research.analyzer

import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.Article
import com.lumen.core.database.entities.MyObjectBox
import com.lumen.core.database.entities.ResearchProject
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RelevanceScorerTest {

    private lateinit var db: LumenDatabase
    private lateinit var tempDir: File

    @BeforeTest
    fun setup() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "objectbox-test-${System.nanoTime()}")
        tempDir.mkdirs()
        val store = MyObjectBox.builder()
            .baseDirectory(tempDir)
            .build()
        db = LumenDatabase(store)
    }

    @AfterTest
    fun teardown() {
        db.close()
        tempDir.deleteRecursively()
    }

    private fun makeEmbedding(seed: Float): FloatArray {
        return FloatArray(384) { seed + it * 0.001f }
    }

    private fun makeSimilarEmbedding(base: FloatArray, noise: Float = 0.01f): FloatArray {
        return FloatArray(384) { base[it] + noise }
    }

    private fun makeDissimilarEmbedding(base: FloatArray): FloatArray {
        return FloatArray(384) { -base[it] }
    }

    @Test
    fun score_withNoProjectNoMemory_returnsZero() = runBlocking {
        val scorer = RelevanceScorer(db, null)
        val article = Article(
            title = "Test",
            url = "https://example.com",
            embedding = makeEmbedding(1.0f),
        )

        val score = scorer.score(article)

        assertEquals(0f, score)
    }

    @Test
    fun score_withEmptyEmbedding_returnsZero() = runBlocking {
        val scorer = RelevanceScorer(db, null)
        val article = Article(title = "Test", url = "https://example.com")

        val score = scorer.score(article)

        assertEquals(0f, score)
    }

    @Test
    fun score_withActiveProject_returnsPositiveScore() = runBlocking {
        val projectEmbedding = makeEmbedding(1.0f)
        db.researchProjectBox.put(ResearchProject(
            name = "AI Safety",
            embedding = projectEmbedding,
            isActive = true,
        ))

        val scorer = RelevanceScorer(db, null)
        val article = Article(
            title = "AI Safety Paper",
            url = "https://example.com",
            embedding = makeSimilarEmbedding(projectEmbedding),
        )

        val score = scorer.score(article)

        assertTrue(score > 0f)
        assertTrue(score <= 1f)
    }

    @Test
    fun score_projectMatchingArticle_scoresHigherThanUnrelated() = runBlocking {
        val projectEmbedding = makeEmbedding(1.0f)
        db.researchProjectBox.put(ResearchProject(
            name = "AI Safety",
            embedding = projectEmbedding,
            isActive = true,
        ))

        val scorer = RelevanceScorer(db, null)

        val matchingArticle = Article(
            title = "Related",
            url = "https://example.com/1",
            embedding = makeSimilarEmbedding(projectEmbedding),
        )
        val unrelatedArticle = Article(
            title = "Unrelated",
            url = "https://example.com/2",
            embedding = makeDissimilarEmbedding(projectEmbedding),
        )

        val matchingScore = scorer.score(matchingArticle)
        val unrelatedScore = scorer.score(unrelatedArticle)

        assertTrue(matchingScore > unrelatedScore, "Matching: $matchingScore > Unrelated: $unrelatedScore")
    }

    @Test
    fun score_withKeywordBoost_increasesScore() = runBlocking {
        val projectEmbedding = makeEmbedding(1.0f)
        db.researchProjectBox.put(ResearchProject(
            name = "AI Safety",
            keywords = "safety,alignment,ai",
            embedding = projectEmbedding,
            isActive = true,
        ))

        val scorer = RelevanceScorer(db, null)

        val articleWithKeywords = Article(
            title = "Keyword Match",
            url = "https://example.com/1",
            embedding = makeSimilarEmbedding(projectEmbedding),
            keywords = "safety,alignment",
        )
        val articleWithoutKeywords = Article(
            title = "No Keywords",
            url = "https://example.com/2",
            embedding = makeSimilarEmbedding(projectEmbedding),
            keywords = "cooking,gardening",
        )

        val withKwScore = scorer.score(articleWithKeywords)
        val withoutKwScore = scorer.score(articleWithoutKeywords)

        assertTrue(withKwScore > withoutKwScore, "With KW: $withKwScore > Without KW: $withoutKwScore")
    }

    @Test
    fun scoreBatch_scoresMultipleArticles() = runBlocking {
        val projectEmbedding = makeEmbedding(1.0f)
        db.researchProjectBox.put(ResearchProject(
            name = "Test Project",
            embedding = projectEmbedding,
            isActive = true,
        ))

        val id1 = db.articleBox.put(Article(
            title = "Article 1",
            url = "https://example.com/1",
            embedding = makeSimilarEmbedding(projectEmbedding),
        ))
        val id2 = db.articleBox.put(Article(
            title = "Article 2",
            url = "https://example.com/2",
            embedding = makeSimilarEmbedding(projectEmbedding, 0.02f),
        ))

        val scorer = RelevanceScorer(db, null)
        val results = scorer.scoreBatch(db.articleBox.all)

        assertEquals(2, results.size)
        assertTrue(results.all { it.aiRelevanceScore > 0f })

        val persisted1 = db.articleBox.get(id1)
        assertTrue(persisted1.aiRelevanceScore > 0f)
    }

    @Test
    fun score_withNullMemoryManager_usesProjectOnly() = runBlocking {
        val projectEmbedding = makeEmbedding(1.0f)
        db.researchProjectBox.put(ResearchProject(
            name = "Test",
            embedding = projectEmbedding,
            isActive = true,
        ))

        val scorer = RelevanceScorer(db, null)
        val article = Article(
            title = "Test",
            url = "https://example.com",
            embedding = makeSimilarEmbedding(projectEmbedding),
        )

        val score = scorer.score(article)

        assertTrue(score > 0f)
        assertTrue(score <= 1f)
    }

    @Test
    fun score_withInactiveProject_stillScoresPositive() = runBlocking {
        val projectEmbedding = makeEmbedding(1.0f)
        db.researchProjectBox.put(ResearchProject(
            name = "Inactive Project",
            embedding = projectEmbedding,
            isActive = false,
        ))

        val scorer = RelevanceScorer(db, null)
        val article = Article(
            title = "Test",
            url = "https://example.com",
            embedding = makeSimilarEmbedding(projectEmbedding),
        )

        val score = scorer.score(article)

        assertTrue(score > 0f, "Inactive projects should still be used for scoring")
    }

    @Test
    fun assignToProjects_returnsMatchingProjectIds() {
        val embedding1 = makeEmbedding(1.0f)
        val embedding2 = makeEmbedding(5.0f)
        val pid1 = db.researchProjectBox.put(ResearchProject(
            name = "Project A",
            embedding = embedding1,
        ))
        db.researchProjectBox.put(ResearchProject(
            name = "Project B",
            embedding = embedding2,
        ))

        val scorer = RelevanceScorer(db, null)
        val article = Article(
            title = "Related to A",
            url = "https://example.com",
            embedding = makeSimilarEmbedding(embedding1),
        )

        val assigned = scorer.assignToProjects(article)

        assertTrue(pid1 in assigned, "Should be assigned to similar project")
    }

    @Test
    fun assignToProjects_relativeThreshold_excludesDistantProject() {
        val embeddingA = makeEmbedding(1.0f)
        val embeddingB = makeDissimilarEmbedding(embeddingA)
        val pidA = db.researchProjectBox.put(ResearchProject(
            name = "Project A",
            embedding = embeddingA,
        ))
        val pidB = db.researchProjectBox.put(ResearchProject(
            name = "Project B",
            embedding = embeddingB,
        ))

        val scorer = RelevanceScorer(db, null)
        val article = Article(
            title = "Very related to A only",
            url = "https://example.com",
            embedding = makeSimilarEmbedding(embeddingA, noise = 0.005f),
        )

        val assigned = scorer.assignToProjects(article)

        assertTrue(pidA in assigned, "Should be assigned to highly similar project A")
        assertTrue(pidB !in assigned, "Should NOT be assigned to dissimilar project B")
    }

    @Test
    fun assignToProjects_emptyEmbedding_returnsEmpty() {
        db.researchProjectBox.put(ResearchProject(
            name = "Project",
            embedding = makeEmbedding(1.0f),
        ))

        val scorer = RelevanceScorer(db, null)
        val article = Article(title = "No Embedding", url = "https://example.com")

        val assigned = scorer.assignToProjects(article)

        assertTrue(assigned.isEmpty())
    }

    @Test
    fun assignToProjects_noProjects_returnsEmpty() {
        val scorer = RelevanceScorer(db, null)
        val article = Article(
            title = "Test",
            url = "https://example.com",
            embedding = makeEmbedding(1.0f),
        )

        val assigned = scorer.assignToProjects(article)

        assertTrue(assigned.isEmpty())
    }
}
