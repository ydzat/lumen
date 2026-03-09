package com.lumen.ui.screen

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.Article
import com.lumen.core.database.entities.Digest
import com.lumen.core.util.formatEpochDate
import com.lumen.research.digest.DigestFormatter
import com.lumen.research.digest.DigestGenerator
import kotlinx.coroutines.launch
import com.lumen.ui.i18n.strings
import org.koin.compose.koinInject

@Composable
fun HomeScreen() {
    val db = koinInject<LumenDatabase>()
    var showDigestHistory by remember { mutableStateOf(false) }
    var detailArticle by remember { mutableStateOf<Article?>(null) }

    if (detailArticle != null) {
        val article = detailArticle!!
        val sourceNames = remember { db.sourceBox.all.associate { it.id to it.name } }
        ArticleDetailScreen(
            article = article,
            sourceName = sourceNames[article.sourceId] ?: "Unknown",
            onBack = { detailArticle = null },
            onStarToggle = {
                val updated = article.copy(starred = !article.starred)
                db.articleBox.put(updated)
                detailArticle = updated
            },
            onArchiveToggle = {
                val updated = article.copy(archived = !article.archived)
                db.articleBox.put(updated)
                detailArticle = updated
            },
        )
        return
    }

    if (showDigestHistory) {
        DigestHistoryScreen(onBack = { showDigestHistory = false })
        return
    }

    HomeMainScreen(
        onViewAllDigests = { showDigestHistory = true },
        onArticleClick = { articleId ->
            detailArticle = db.articleBox.get(articleId)
        },
    )
}

@Composable
private fun HomeMainScreen(
    onViewAllDigests: () -> Unit,
    onArticleClick: (Long) -> Unit,
) {
    val s = strings()
    val db = koinInject<LumenDatabase>()
    val digestFormatter = koinInject<DigestFormatter>()
    val digestGenerator = koinInject<DigestGenerator>()
    val scope = rememberCoroutineScope()

    var todayDigest by remember { mutableStateOf<Digest?>(null) }
    var recentSparks by remember {
        mutableStateOf<List<DigestGenerator.SparkSection>>(emptyList())
    }
    var articleCount by remember { mutableStateOf(0L) }
    var sourceCount by remember { mutableStateOf(0L) }
    var isRefreshing by remember { mutableStateOf(false) }
    val todayDate = remember { formatEpochDate(System.currentTimeMillis()) }

    fun loadDigest() {
        val digests = db.digestBox.all.sortedByDescending { it.createdAt }
        todayDigest = digests.firstOrNull { it.date == todayDate }
        recentSparks = digests.take(SPARK_DIGEST_LOOKBACK)
            .flatMap { digestFormatter.parseSparkSections(it.sparks) }
        articleCount = db.articleBox.count()
        sourceCount = db.sourceBox.count()
    }

    LaunchedEffect(Unit) {
        loadDigest()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Welcome header
        Text(
            text = s.welcomeToLumen,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = todayDate,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(4.dp))

        // Quick stats
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatCard(
                label = s.articles,
                value = articleCount.toString(),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = s.sources,
                value = sourceCount.toString(),
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(4.dp))

        // Today's digest header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = s.todaysDigest,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(horizontal = 8.dp)
                            .height(20.dp).width(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    IconButton(onClick = {
                        isRefreshing = true
                        scope.launch {
                            try {
                                val digest = digestGenerator.regenerate(todayDate)
                                todayDigest = digest
                                recentSparks = db.digestBox.all
                                    .sortedByDescending { it.createdAt }
                                    .take(SPARK_DIGEST_LOOKBACK)
                                    .flatMap { digestFormatter.parseSparkSections(it.sparks) }
                            } catch (_: Exception) {
                                // Refresh failed silently
                            } finally {
                                isRefreshing = false
                            }
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = s.refreshDigest,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                TextButton(onClick = onViewAllDigests) {
                    Text(s.viewAll)
                }
            }
        }

        val digest = todayDigest
        if (digest != null) {
            val projectSections = remember(digest) {
                digestFormatter.parseProjectSections(digest.projectSections)
            }
            val sourceBreakdown = remember(digest) {
                digestFormatter.parseSourceBreakdown(digest.sourceBreakdown)
            }
            val digestSparks = remember(digest) {
                digestFormatter.parseSparkSections(digest.sparks)
            }

            // 1. Header Card
            DigestHeaderCard(digest = digest, sourceBreakdown = sourceBreakdown)

            // 2. Overview Card (cross-project meta-summary)
            if (digest.content.isNotBlank()) {
                DigestOverviewCard(content = digest.content)
            }

            // 3. Project Section Cards (each with own highlights, trends, insight)
            if (projectSections.isNotEmpty()) {
                for ((index, section) in projectSections.withIndex()) {
                    ProjectSectionCard(
                        section = section,
                        colorIndex = index,
                        onArticleClick = onArticleClick,
                    )
                }
            }

            // 4. Spark Insights Card
            SparkInsightsCard(sparks = digestSparks)
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = s.noDigestAvailable,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = s.digestGeneratedAfterCollection,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Show recent sparks even when no digest for today
            if (recentSparks.isNotEmpty()) {
                SparkInsightsCard(sparks = recentSparks)
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

// ── Card 1: Header ──────────────────────────────────────────────────────────

@Composable
private fun DigestHeaderCard(
    digest: Digest,
    sourceBreakdown: List<DigestGenerator.SourceBreakdownEntry>,
) {
    val s = strings()
    val totalArticles = sourceBreakdown.sumOf { it.count }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = digest.date,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = digest.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (totalArticles > 0 && sourceBreakdown.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${s.articlesCount(totalArticles.toLong())} · ${sourceBreakdown.size} ${s.sources.lowercase()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(8.dp))
                // Proportion bar
                SourceProportionBar(
                    sourceBreakdown = sourceBreakdown,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                // Source legend
                SourceLegend(sourceBreakdown = sourceBreakdown)
            }
        }
    }
}

@Composable
private fun SourceProportionBar(
    sourceBreakdown: List<DigestGenerator.SourceBreakdownEntry>,
    modifier: Modifier = Modifier,
) {
    val total = sourceBreakdown.sumOf { it.count }.toFloat().coerceAtLeast(1f)
    val colors = SOURCE_BAR_COLORS

    Canvas(
        modifier = modifier
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp)),
    ) {
        var x = 0f
        sourceBreakdown.forEachIndexed { index, entry ->
            val width = (entry.count / total) * size.width
            drawRoundRect(
                color = colors[index % colors.size],
                topLeft = Offset(x, 0f),
                size = Size(width, size.height),
                cornerRadius = CornerRadius(4.dp.toPx()),
            )
            x += width
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SourceLegend(sourceBreakdown: List<DigestGenerator.SourceBreakdownEntry>) {
    val s = strings()
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        sourceBreakdown.forEachIndexed { index, entry ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(SOURCE_BAR_COLORS[index % SOURCE_BAR_COLORS.size]),
                )
                Text(
                    text = "${entry.source} (${entry.count})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
            }
        }
    }
}

// ── Card 2: Overview (cross-project meta-summary) ───────────────────────────

@Composable
private fun DigestOverviewCard(content: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun NumberedContent(text: String) {
    val lines = text.split("\n").filter { it.isNotBlank() }
    for (line in lines) {
        val trimmed = line.trim()
        // Check if line starts with a number pattern like "1." "2." etc.
        val numberMatch = Regex("""^(\d+)\.\s*(.*)""").find(trimmed)
        if (numberMatch != null) {
            val number = numberMatch.groupValues[1]
            val body = numberMatch.groupValues[2]
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append("$number. ")
                    }
                    append(body)
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        } else {
            Text(
                text = trimmed,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
    }
}

// ── Card 3: Project Section ─────────────────────────────────────────────────

@Composable
private fun ProjectSectionCard(
    section: DigestGenerator.ProjectSection,
    colorIndex: Int,
    onArticleClick: (Long) -> Unit,
) {
    val s = strings()
    val accentColor = PROJECT_COLORS[colorIndex % PROJECT_COLORS.size]
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier
                .drawBehind {
                    drawRect(
                        color = accentColor,
                        topLeft = Offset.Zero,
                        size = Size(4.dp.toPx(), size.height),
                    )
                }
                .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)
                .animateContentSize(),
        ) {
                // Header: project name + article count badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = section.projectName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text(
                        text = s.articlesCount(section.articleCount.toLong()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(4.dp),
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }

                // Per-section highlights
                if (section.highlights.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = s.highlights,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = accentColor,
                    )
                    Spacer(Modifier.height(4.dp))
                    NumberedContent(text = section.highlights)
                }

                // Per-section trends
                if (section.trends.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = s.trends,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = accentColor,
                    )
                    Spacer(Modifier.height(4.dp))
                    NumberedContent(text = section.trends)
                }

                // Per-section insight
                if (section.insight.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = accentColor,
                        )
                        Text(
                            text = section.insight,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                // Expandable article list
                if (expanded && section.articles.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(8.dp))
                    for (articleRef in section.articles) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onArticleClick(articleRef.articleId) }
                                .padding(vertical = 4.dp),
                        ) {
                            Text(
                                text = articleRef.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                textDecoration = TextDecoration.Underline,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (articleRef.summarySnippet.isNotBlank()) {
                                Text(
                                    text = articleRef.summarySnippet,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }

                // Expand hint
                if (section.articles.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (expanded) s.tapToCollapse else s.tapToExpand,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }

// ── Card 4: Spark Insights (or per-section insights fallback) ───────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SparkInsightsCard(sparks: List<DigestGenerator.SparkSection>) {
    val s = strings()

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    text = s.sparkInsights,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }

            Spacer(Modifier.height(8.dp))

            if (sparks.isEmpty()) {
                Text(
                    text = s.noSparkInsights,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
                )
            } else {
                sparks.forEachIndexed { index, spark ->
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.2f),
                        )
                    }
                    Text(
                        text = spark.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = spark.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    if (spark.relatedKeywords.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            spark.relatedKeywords.forEach { keyword ->
                                SuggestionChip(
                                    onClick = {},
                                    label = {
                                        Text(
                                            text = keyword,
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Stat Card ───────────────────────────────────────────────────────────────

@Composable
private fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

// ── Constants ───────────────────────────────────────────────────────────────

private const val SPARK_DIGEST_LOOKBACK = 7

private val PROJECT_COLORS = listOf(
    Color(0xFF1E88E5), // Blue
    Color(0xFF43A047), // Green
    Color(0xFFE53935), // Red
    Color(0xFF8E24AA), // Purple
    Color(0xFFFB8C00), // Orange
    Color(0xFF00ACC1), // Cyan
)

private val SOURCE_BAR_COLORS = listOf(
    Color(0xFF42A5F5), // Blue
    Color(0xFF66BB6A), // Green
    Color(0xFFFFA726), // Orange
    Color(0xFFAB47BC), // Purple
    Color(0xFFEF5350), // Red
    Color(0xFF26C6DA), // Cyan
)
