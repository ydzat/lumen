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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.lumen.research.analyzer.DeepAnalysisService
import com.lumen.ui.displaySourceType
import com.lumen.ui.HtmlContentRenderer
import com.lumen.ui.i18n.AppStrings
import com.lumen.ui.i18n.strings
import com.lumen.ui.stripHtml
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleDetailScreen(
    article: Article,
    sourceName: String,
    activeProjectName: String = "",
    onBack: () -> Unit,
    onStarToggle: () -> Unit,
    onArchiveToggle: () -> Unit,
    sections: List<ArticleSection> = emptyList(),
    localSections: List<DeepAnalysisService.LocalSection> = emptyList(),
    onAnalyzeSection: (Int) -> Unit = {},
    onTranslateSection: (Long) -> Unit = {},
    sectionLoadingIds: Set<Long> = emptySet(),
    sectionAnalyzingIndices: Set<Int> = emptySet(),
) {
    val s = strings()
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var showToc by remember { mutableStateOf(false) }

    val tocEntries = buildTocEntries(article, activeProjectName, localSections, s)

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
                            contentDescription = s.back,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showToc = true }) {
                        Icon(Icons.Default.Menu, contentDescription = s.tableOfContents)
                    }
                    IconButton(onClick = onStarToggle) {
                        Icon(
                            imageVector = if (article.starred) Icons.Default.Star
                            else Icons.Default.StarBorder,
                            contentDescription = if (article.starred) s.unstar else s.star,
                            tint = if (article.starred) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onArchiveToggle) {
                        Icon(
                            imageVector = if (article.archived) Icons.Default.Unarchive
                            else Icons.Default.Archive,
                            contentDescription = if (article.archived) s.restore else s.archive,
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

            // Relevance card
            if (article.aiRelevanceScore > 0f || article.citationCount > 0 || article.influentialCitationCount > 0) {
                RelevanceCard(article, activeProjectName)
            }

            HorizontalDivider()

            // AI Summary
            if (article.aiSummary.isNotBlank()) {
                ExpandableSection(
                    title = s.aiSummary,
                    initiallyExpanded = true,
                ) {
                    Text(
                        text = article.aiSummary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            // Keywords
            if (article.keywords.isNotBlank()) {
                ExpandableSection(
                    title = s.keywords,
                    initiallyExpanded = true,
                ) {
                    Text(
                        text = article.keywords,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Determine content source: prefer article.content, fall back to summary
            val hasDistinctContent = article.content.isNotBlank()
            val cleanSummary = stripHtml(article.summary)

            // Show Abstract only when there is separate content (avoid duplication)
            if (hasDistinctContent && cleanSummary.isNotBlank()) {
                ExpandableSection(
                    title = s.abstract_,
                    initiallyExpanded = article.aiSummary.isBlank(),
                ) {
                    Text(
                        text = cleanSummary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            // Pipeline translation (whole-article)
            if (article.aiTranslation.isNotBlank()) {
                ExpandableSection(
                    title = s.translation,
                    initiallyExpanded = false,
                ) {
                    Text(
                        text = article.aiTranslation,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            // Article content sections (from content or summary)
            if (localSections.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    text = if (hasDistinctContent) s.articleContent else s.sourceText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )

                localSections.forEachIndexed { index, localSection ->
                    val dbSection = sections.firstOrNull { it.sectionIndex == index }
                    ContentSectionCard(
                        localSection = localSection,
                        sectionIndex = index,
                        dbSection = dbSection,
                        isAnalyzing = index in sectionAnalyzingIndices,
                        isTranslating = dbSection?.id?.let { it in sectionLoadingIds } ?: false,
                        onAnalyze = { onAnalyzeSection(index) },
                        onTranslate = { dbSection?.id?.let { onTranslateSection(it) } },
                    )
                }
            } else if (cleanSummary.isNotBlank() && !hasDistinctContent) {
                // No sections parsed and no distinct content — show summary as fallback
                ExpandableSection(
                    title = s.abstract_,
                    initiallyExpanded = article.aiSummary.isBlank(),
                ) {
                    Text(
                        text = cleanSummary,
                        style = MaterialTheme.typography.bodyMedium,
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

    // TOC BottomSheet
    if (showToc) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showToc = false },
            sheetState = sheetState,
        ) {
            Column(
                modifier = Modifier.padding(bottom = 32.dp),
            ) {
                Text(
                    text = s.tableOfContents,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                HorizontalDivider()
                tocEntries.forEach { entry ->
                    ListItem(
                        headlineContent = {
                            Text(
                                text = entry.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (entry.isKey) FontWeight.Bold else FontWeight.Normal,
                                modifier = if (entry.indent > 0) Modifier.padding(start = (entry.indent * 16).dp)
                                else Modifier,
                            )
                        },
                        modifier = Modifier.clickable {
                            showToc = false
                            scope.launch {
                                val targetPosition = entry.scrollFraction * scrollState.maxValue
                                scrollState.animateScrollTo(targetPosition.toInt())
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ContentSectionCard(
    localSection: DeepAnalysisService.LocalSection,
    sectionIndex: Int,
    dbSection: ArticleSection?,
    isAnalyzing: Boolean,
    isTranslating: Boolean,
    onAnalyze: () -> Unit,
    onTranslate: () -> Unit,
) {
    val s = strings()
    val headingStyle = when (localSection.level) {
        1 -> MaterialTheme.typography.titleMedium
        2 -> MaterialTheme.typography.titleSmall
        else -> MaterialTheme.typography.bodyLarge
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        // Section heading
        Text(
            text = localSection.heading,
            style = headingStyle,
            fontWeight = FontWeight.Bold,
        )

        Spacer(Modifier.height(4.dp))

        // Section content — rendered as HTML if it contains tags, plain text otherwise
        if (localSection.content.contains('<')) {
            HtmlContentRenderer(
                html = localSection.content,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text(
                text = localSection.content,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        // AI Translation (if translated) — shown right below original text
        if (dbSection?.aiTranslation?.isNotBlank() == true) {
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))
                    .padding(10.dp),
            ) {
                Column {
                    Text(
                        text = s.translation,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = dbSection.aiTranslation,
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        // AI Commentary (if analyzed)
        if (dbSection?.aiCommentary?.isNotBlank() == true) {
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                    .padding(10.dp),
            ) {
                Column {
                    Text(
                        text = s.aiCommentary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = dbSection.aiCommentary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        // Action buttons row
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // AI Analysis button
            if (dbSection?.aiCommentary.isNullOrBlank()) {
                TextButton(
                    onClick = onAnalyze,
                    enabled = !isAnalyzing,
                ) {
                    if (isAnalyzing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(s.analyzing, style = MaterialTheme.typography.labelSmall)
                    } else {
                        Icon(
                            Icons.Default.Analytics,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(s.aiAnalysis, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // Translate button (only when section exists in DB)
            if (dbSection != null && dbSection.aiTranslation.isBlank()) {
                TextButton(
                    onClick = onTranslate,
                    enabled = !isTranslating,
                ) {
                    if (isTranslating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(s.translating, style = MaterialTheme.typography.labelSmall)
                    } else {
                        Icon(
                            Icons.Default.Translate,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(s.translate, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}

@Composable
private fun RelevanceCard(article: Article, projectName: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (article.aiRelevanceScore > 0f) {
                val label = if (projectName.isNotBlank()) {
                    "Relevance to $projectName"
                } else {
                    "Relevance"
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        text = "${"%.0f".format(article.aiRelevanceScore * 100)}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            if (article.citationCount > 0 || article.influentialCitationCount > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (article.citationCount > 0) {
                        Text(
                            text = "Citations: ${article.citationCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                    if (article.influentialCitationCount > 0) {
                        Text(
                            text = "Influential: ${article.influentialCitationCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
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
private fun ExpandableSection(
    title: String,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit,
) {
    val s = strings()
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
                contentDescription = if (expanded) s.collapse else s.expand,
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

private data class TocEntry(
    val title: String,
    val scrollFraction: Float,
    val isKey: Boolean = false,
    val indent: Int = 0,
)

private fun buildTocEntries(
    article: Article,
    activeProjectName: String,
    localSections: List<DeepAnalysisService.LocalSection>,
    s: AppStrings,
): List<TocEntry> {
    val entries = mutableListOf<TocEntry>()
    var index = 0

    val hasDistinctContent = article.content.isNotBlank()

    // Count total items for scroll fraction calculation
    var totalItems = 2 // title + content header
    if (article.aiRelevanceScore > 0f || activeProjectName.isNotBlank()) totalItems++
    if (article.aiSummary.isNotBlank()) totalItems++
    if (article.keywords.isNotBlank()) totalItems++
    if (hasDistinctContent && article.summary.isNotBlank()) totalItems++ // Abstract only when distinct content exists
    totalItems += localSections.size
    totalItems = totalItems.coerceAtLeast(1)

    // Meta sections
    entries.add(TocEntry("Title & Metadata", index.toFloat() / totalItems))
    index++

    if (article.aiRelevanceScore > 0f || activeProjectName.isNotBlank()) {
        entries.add(TocEntry("Relevance", index.toFloat() / totalItems))
        index++
    }

    if (article.aiSummary.isNotBlank()) {
        entries.add(TocEntry(s.aiSummary, index.toFloat() / totalItems))
        index++
    }

    if (article.keywords.isNotBlank()) {
        entries.add(TocEntry(s.keywords, index.toFloat() / totalItems))
        index++
    }

    // Abstract only shown when there's separate content
    if (hasDistinctContent && article.summary.isNotBlank()) {
        entries.add(TocEntry(s.abstract_, index.toFloat() / totalItems))
        index++
    }

    // Content sections
    if (localSections.isNotEmpty()) {
        localSections.forEach { section ->
            val indent = (section.level - 1).coerceIn(0, 2)
            entries.add(
                TocEntry(
                    section.heading,
                    index.toFloat() / totalItems,
                    indent = indent,
                ),
            )
            index++
        }
    }

    return entries
}
