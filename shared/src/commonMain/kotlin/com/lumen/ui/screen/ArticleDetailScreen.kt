package com.lumen.ui.screen

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lumen.core.database.entities.Article
import com.lumen.core.database.entities.ArticleSection
import com.lumen.core.util.formatEpochDate
import com.lumen.ui.displaySourceType
import com.lumen.ui.stripHtml
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleDetailScreen(
    article: Article,
    sourceName: String,
    onBack: () -> Unit,
    onStarToggle: () -> Unit,
    onArchiveToggle: () -> Unit,
    sections: List<ArticleSection> = emptyList(),
    onDeepAnalyze: () -> Unit = {},
    onTranslateSection: (Long) -> Unit = {},
    deepAnalysisLoading: Boolean = false,
    sectionLoadingIds: Set<Long> = emptySet(),
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // Build TOC entries
    val tocEntries = buildTocEntries(article, sections)

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(280.dp)) {
                Text(
                    text = "Table of Contents",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp),
                )
                HorizontalDivider()
                tocEntries.forEach { entry ->
                    NavigationDrawerItem(
                        label = {
                            Text(
                                text = entry.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (entry.isKey) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                        selected = false,
                        onClick = {
                            scope.launch {
                                drawerState.close()
                                val targetPosition = entry.scrollFraction * scrollState.maxValue
                                scrollState.animateScrollTo(targetPosition.toInt())
                            }
                        },
                    )
                }
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stripHtml(article.title),
                            maxLines = 1,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            scope.launch { drawerState.open() }
                        }) {
                            Icon(Icons.Default.Menu, contentDescription = "Table of Contents")
                        }
                        IconButton(onClick = onStarToggle) {
                            Icon(
                                imageVector = if (article.starred) Icons.Default.Star
                                else Icons.Default.StarBorder,
                                contentDescription = if (article.starred) "Unstar" else "Star",
                                tint = if (article.starred) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = onArchiveToggle) {
                            Icon(
                                imageVector = if (article.archived) Icons.Default.Unarchive
                                else Icons.Default.Archive,
                                contentDescription = if (article.archived) "Restore" else "Archive",
                            )
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Title
                Text(
                    text = stripHtml(article.title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )

                // Author
                if (article.author.isNotBlank()) {
                    Text(
                        text = article.author,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Metadata card
                MetadataCard(article, sourceName)

                // Metrics card
                if (article.aiRelevanceScore > 0f || article.citationCount > 0 || article.influentialCitationCount > 0) {
                    MetricsCard(article)
                }

                HorizontalDivider()

                // AI Summary (expandable)
                if (article.aiSummary.isNotBlank()) {
                    ExpandableSection(
                        title = "AI Summary",
                        initiallyExpanded = true,
                    ) {
                        Text(
                            text = article.aiSummary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                // Abstract from source
                val cleanSummary = stripHtml(article.summary)
                if (cleanSummary.isNotBlank()) {
                    ExpandableSection(
                        title = "Abstract",
                        initiallyExpanded = article.aiSummary.isBlank(),
                    ) {
                        Text(
                            text = cleanSummary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                // Translation (whole-article, from pipeline)
                if (article.aiTranslation.isNotBlank()) {
                    ExpandableSection(
                        title = "Translation",
                        initiallyExpanded = false,
                    ) {
                        Text(
                            text = article.aiTranslation,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                // Content: section-by-section or full content
                val cleanContent = stripHtml(article.content)
                if (sections.isNotEmpty()) {
                    // Section-by-section display
                    HorizontalDivider()
                    Text(
                        text = "Article Sections",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    sections.forEach { section ->
                        SectionCard(
                            section = section,
                            isLoading = section.id in sectionLoadingIds,
                            onTranslate = { onTranslateSection(section.id) },
                        )
                    }
                } else if (cleanContent.isNotBlank()) {
                    // No sections yet — show deep analyze button + full content fallback
                    DeepAnalyzeButton(
                        isLoading = deepAnalysisLoading,
                        onClick = onDeepAnalyze,
                    )
                    ExpandableSection(
                        title = "Full Content",
                        initiallyExpanded = false,
                    ) {
                        Text(
                            text = cleanContent,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                // Keywords
                if (article.keywords.isNotBlank()) {
                    ExpandableSection(
                        title = "Keywords",
                        initiallyExpanded = true,
                    ) {
                        Text(
                            text = article.keywords,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // URL
                if (article.url.isNotBlank()) {
                    Text(
                        text = article.url,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun DeepAnalyzeButton(
    isLoading: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = !isLoading,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(Modifier.width(8.dp))
            Text("Analyzing...")
        } else {
            Icon(
                Icons.Default.Analytics,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text("Deep Analyze Article")
        }
    }
}

@Composable
private fun SectionCard(
    section: ArticleSection,
    isLoading: Boolean,
    onTranslate: () -> Unit,
) {
    val headingStyle = when (section.level) {
        1 -> MaterialTheme.typography.titleMedium
        2 -> MaterialTheme.typography.titleSmall
        else -> MaterialTheme.typography.bodyLarge
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Heading row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = section.heading,
                    style = headingStyle,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (section.isKeySection) {
                    Text(
                        text = "KEY",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.tertiaryContainer)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }

            // Section content
            val cleanContent = stripHtml(section.content)
            ExpandableSection(
                title = "Content",
                initiallyExpanded = section.isKeySection,
            ) {
                Text(
                    text = cleanContent,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            // AI Commentary
            if (section.aiCommentary.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                        .padding(10.dp),
                ) {
                    Column {
                        Text(
                            text = "AI Commentary",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = section.aiCommentary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            // Translation
            if (section.aiTranslation.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))
                        .padding(10.dp),
                ) {
                    Column {
                        Text(
                            text = "Translation",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = section.aiTranslation,
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            // Action row: Translate button
            if (section.aiTranslation.isBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        OutlinedButton(
                            onClick = onTranslate,
                            contentPadding = ButtonDefaults.TextButtonContentPadding,
                        ) {
                            Icon(
                                Icons.Default.Translate,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Translate", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetadataCard(article: Article, sourceName: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            MetadataRow("Source", "$sourceName (${displaySourceType(article.sourceType)})")
            if (article.publishedAt > 0) {
                MetadataRow("Published", formatEpochDate(article.publishedAt))
            }
            if (article.fetchedAt > 0) {
                MetadataRow("Fetched", formatEpochDate(article.fetchedAt))
            }
            if (article.doi.isNotBlank()) {
                MetadataRow("DOI", article.doi)
            }
            if (article.arxivId.isNotBlank()) {
                MetadataRow("arXiv", article.arxivId)
            }
        }
    }
}

@Composable
private fun MetricsCard(article: Article) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            if (article.aiRelevanceScore > 0f) {
                MetricItem(
                    label = "Relevance",
                    value = "${"%.0f".format(article.aiRelevanceScore * 100)}%",
                )
            }
            if (article.citationCount > 0) {
                MetricItem(
                    label = "Citations",
                    value = article.citationCount.toString(),
                )
            }
            if (article.influentialCitationCount > 0) {
                MetricItem(
                    label = "Influential",
                    value = article.influentialCitationCount.toString(),
                )
            }
        }
    }
}

@Composable
private fun ExpandableSection(
    title: String,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }

    Column(modifier = Modifier.animateContentSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess
                else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        if (expanded) {
            Spacer(Modifier.height(4.dp))
            content()
        }
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Row {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MetricItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

private data class TocEntry(
    val title: String,
    val scrollFraction: Float,
    val isKey: Boolean = false,
)

private fun buildTocEntries(
    article: Article,
    sections: List<ArticleSection>,
): List<TocEntry> {
    val entries = mutableListOf<TocEntry>()

    if (sections.isNotEmpty()) {
        // Use actual section headings
        entries.add(TocEntry("Title & Metadata", 0f))
        val totalItems = sections.size + 2 // +2 for title and keywords
        sections.forEachIndexed { index, section ->
            val prefix = if (section.isKeySection) "* " else ""
            entries.add(
                TocEntry(
                    "$prefix${section.heading}",
                    (index + 1).toFloat() / totalItems,
                    section.isKeySection,
                ),
            )
        }
        if (article.keywords.isNotBlank()) {
            entries.add(TocEntry("Keywords", (totalItems - 1).toFloat() / totalItems))
        }
    } else {
        // Static TOC fallback
        var index = 0
        val totalSections = countSections(article)
        entries.add(TocEntry("Title & Metadata", index.toFloat() / totalSections))
        index++
        if (article.aiSummary.isNotBlank()) {
            entries.add(TocEntry("AI Summary", index.toFloat() / totalSections))
            index++
        }
        if (article.summary.isNotBlank()) {
            entries.add(TocEntry("Abstract", index.toFloat() / totalSections))
            index++
        }
        if (article.aiTranslation.isNotBlank()) {
            entries.add(TocEntry("Translation", index.toFloat() / totalSections))
            index++
        }
        if (article.content.isNotBlank()) {
            entries.add(TocEntry("Full Content", index.toFloat() / totalSections))
            index++
        }
        if (article.keywords.isNotBlank()) {
            entries.add(TocEntry("Keywords", index.toFloat() / totalSections))
        }
    }
    return entries
}

private fun countSections(article: Article): Int {
    var count = 1
    if (article.aiSummary.isNotBlank()) count++
    if (article.summary.isNotBlank()) count++
    if (article.aiTranslation.isNotBlank()) count++
    if (article.content.isNotBlank()) count++
    if (article.keywords.isNotBlank()) count++
    return count.coerceAtLeast(1)
}
