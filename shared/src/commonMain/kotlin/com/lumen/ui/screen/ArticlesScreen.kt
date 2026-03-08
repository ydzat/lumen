package com.lumen.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.Article
import com.lumen.core.database.entities.ResearchProject
import com.lumen.core.memory.MemoryManager
import com.lumen.core.util.formatEpochDate
import com.lumen.research.ProjectManager
import com.lumen.core.database.entities.ArticleSection
import com.lumen.research.analyzer.ArticleAnalyzer
import com.lumen.research.analyzer.DeepAnalysisService
import com.lumen.research.collector.AnalysisStatus
import com.lumen.research.collector.CollectorManager
import com.lumen.research.collector.PipelineStage
import com.lumen.research.parseCsvSet
import com.lumen.ui.displaySourceType
import com.lumen.ui.displaySourceTypeShort
import com.lumen.ui.stripHtml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.getKoin
import org.koin.compose.koinInject

private enum class SortMode(val label: String) {
    DATE("Date"),
    RELEVANCE("Relevance"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticlesScreen() {
    val db = koinInject<LumenDatabase>()
    val projectManager = koinInject<ProjectManager>()
    val collectorManager = koinInject<CollectorManager>()
    val memoryManager = getKoin().getOrNull<MemoryManager>()
    val articleAnalyzer = koinInject<ArticleAnalyzer>()
    val deepAnalysisService = koinInject<DeepAnalysisService>()
    val configStore = koinInject<com.lumen.core.config.ConfigStore>()

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var articles by remember { mutableStateOf<List<Article>>(emptyList()) }
    var sourceNames by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    var projects by remember { mutableStateOf<List<ResearchProject>>(emptyList()) }
    var activeProjectId by remember { mutableStateOf(0L) }
    var sortMode by remember { mutableStateOf(SortMode.DATE) }
    var filterStarred by remember { mutableStateOf(false) }
    var showArchived by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var progressStage by remember { mutableStateOf("") }
    var progressDetail by remember { mutableStateOf("") }
    var selectedArticle by remember { mutableStateOf<Article?>(null) }
    var detailArticle by remember { mutableStateOf<Article?>(null) }
    var showAnalyzePrompt by remember { mutableStateOf(false) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var projectExpanded by remember { mutableStateOf(false) }
    var articleSections by remember { mutableStateOf<List<ArticleSection>>(emptyList()) }
    var localSections by remember { mutableStateOf<List<DeepAnalysisService.LocalSection>>(emptyList()) }
    var sectionLoadingIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var sectionAnalyzingIndices by remember { mutableStateOf<Set<Int>>(emptySet()) }

    // Initialize activeProjectId from DB once on first composition
    LaunchedEffect(Unit) {
        val activeProject = projectManager.getActive()
        activeProjectId = activeProject?.id ?: 0L
    }

    fun loadData() {
        sourceNames = db.sourceBox.all.associate { it.id to it.name }
        projects = projectManager.listAll()

        val allArticles = db.articleBox.all
        val filtered = allArticles
            .filter { showArchived || !it.archived }
            .filter { !filterStarred || it.starred }
            .filter {
                activeProjectId == 0L ||
                    activeProjectId.toString() in parseCsvSet(it.projectIds)
            }

        articles = when (sortMode) {
            SortMode.DATE -> filtered.sortedByDescending { it.fetchedAt }
            SortMode.RELEVANCE -> filtered.sortedByDescending { it.aiRelevanceScore }
        }
    }

    LaunchedEffect(sortMode, filterStarred, showArchived, activeProjectId) {
        loadData()
    }

    fun toggleStar(article: Article) {
        val updated = article.copy(starred = !article.starred)
        db.articleBox.put(updated)

        if (updated.starred && memoryManager != null) {
            scope.launch {
                try {
                    val content = buildString {
                        append("Starred article: ${updated.title}.")
                        if (updated.aiSummary.isNotBlank()) {
                            append(" Summary: ${updated.aiSummary}.")
                        }
                        if (updated.keywords.isNotBlank()) {
                            append(" Keywords: ${updated.keywords}.")
                        }
                    }
                    withContext(Dispatchers.Default) {
                        memoryManager.store(
                            content = content.take(1000),
                            category = "research_interest",
                            source = "star",
                        )
                    }
                } catch (_: Exception) {
                    // Memory storage is best-effort
                }
            }
        }
        loadData()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (!isRefreshing) {
                        isRefreshing = true
                        progressStage = ""
                        progressDetail = ""
                        scope.launch {
                            try {
                                val result = withContext(Dispatchers.Default) {
                                    collectorManager.runNow { stage, current, total ->
                                        withContext(Dispatchers.Main) {
                                            progressStage = formatStageName(stage)
                                            progressDetail = if (total > 1) "$current/$total" else ""
                                        }
                                    }
                                }
                                loadData()
                                val msg = buildString {
                                    append("Fetched ${result.fetched}, embedded ${result.embedded}, analyzed ${result.analyzed} article(s)")
                                    if (result.fetchErrors.isNotEmpty()) {
                                        append("\nErrors: ${result.fetchErrors.joinToString("; ")}")
                                    }
                                }
                                snackbarHostState.showSnackbar(msg)
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar(
                                    "Refresh failed: ${e.message ?: "Unknown error"}"
                                )
                            } finally {
                                isRefreshing = false
                                progressStage = ""
                                progressDetail = ""
                            }
                        }
                    }
                },
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh feeds")
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Top controls: project selector + sort/filter
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Project selector
                ExposedDropdownMenuBox(
                    expanded = projectExpanded,
                    onExpandedChange = { projectExpanded = it },
                    modifier = Modifier.weight(1f),
                ) {
                    OutlinedTextField(
                        value = if (activeProjectId == 0L) "All Projects"
                        else projects.firstOrNull { it.id == activeProjectId }?.name
                            ?: "All Projects",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Project") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = projectExpanded)
                        },
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                        singleLine = true,
                    )
                    ExposedDropdownMenu(
                        expanded = projectExpanded,
                        onDismissRequest = { projectExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("All Projects") },
                            onClick = {
                                activeProjectId = 0L
                                projectExpanded = false
                            },
                        )
                        projects.forEach { project ->
                            DropdownMenuItem(
                                text = { Text(project.name) },
                                onClick = {
                                    activeProjectId = project.id
                                    scope.launch {
                                        projectManager.setActive(project.id)
                                    }
                                    projectExpanded = false
                                },
                            )
                        }
                    }
                }

                Spacer(Modifier.width(8.dp))

                // Sort toggle
                TextButton(
                    onClick = {
                        sortMode = when (sortMode) {
                            SortMode.DATE -> SortMode.RELEVANCE
                            SortMode.RELEVANCE -> SortMode.DATE
                        }
                    },
                ) {
                    Text(sortMode.label)
                }

                // Starred filter toggle
                IconToggleButton(
                    checked = filterStarred,
                    onCheckedChange = { filterStarred = it },
                ) {
                    Icon(
                        imageVector = if (filterStarred) Icons.Default.Star
                        else Icons.Default.FilterList,
                        contentDescription = if (filterStarred) "Show all"
                        else "Show starred only",
                        tint = if (filterStarred) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Archive filter toggle
                IconToggleButton(
                    checked = showArchived,
                    onCheckedChange = { showArchived = it },
                ) {
                    Icon(
                        imageVector = Icons.Default.Archive,
                        contentDescription = if (showArchived) "Hide archived"
                        else "Show archived",
                        tint = if (showArchived) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Pipeline progress banner
            if (isRefreshing && progressStage.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = progressStage,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (progressDetail.isNotBlank()) {
                        Text(
                            text = progressDetail,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Article list
            if (articles.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Article,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "No articles yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Tap the refresh button to fetch feeds.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 4.dp,
                        bottom = 80.dp,
                    ),
                ) {
                    items(articles, key = { it.id }) { article ->
                        ArticleCard(
                            article = article,
                            sourceName = sourceNames[article.sourceId] ?: "Unknown",
                            onClick = { selectedArticle = article },
                            onStarClick = { toggleStar(article) },
                        )
                    }
                }
            }
        }
    }

    // Detail dialog
    selectedArticle?.let { article ->
        ArticleDetailDialog(
            article = article,
            sourceName = sourceNames[article.sourceId] ?: "Unknown",
            onDismiss = { selectedArticle = null },
            onArchiveToggle = {
                val updated = article.copy(archived = !article.archived)
                db.articleBox.put(updated)
                selectedArticle = updated
                loadData()
            },
            onViewDetails = {
                if (article.analysisStatus == AnalysisStatus.ANALYZED) {
                    detailArticle = article
                    selectedArticle = null
                } else {
                    showAnalyzePrompt = true
                }
            },
        )
    }

    // Analyze prompt for unanalyzed articles
    if (showAnalyzePrompt) {
        selectedArticle?.let { article ->
            AlertDialog(
                onDismissRequest = { showAnalyzePrompt = false },
                title = { Text("Article Not Analyzed") },
                text = {
                    Text(
                        "This article has not been analyzed by AI yet. " +
                            "Would you like to analyze it now? This will generate " +
                            "an AI summary and extract keywords.",
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showAnalyzePrompt = false
                            isAnalyzing = true
                            scope.launch {
                                try {
                                    val analyzed = withContext(Dispatchers.Default) {
                                        articleAnalyzer.analyze(article)
                                    }
                                    detailArticle = analyzed
                                    selectedArticle = null
                                    loadData()
                                    snackbarHostState.showSnackbar("Analysis complete")
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar(
                                        "Analysis failed: ${e.message ?: "Unknown error"}"
                                    )
                                } finally {
                                    isAnalyzing = false
                                }
                            }
                        },
                        enabled = !isAnalyzing,
                    ) {
                        if (isAnalyzing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Analyzing...")
                        } else {
                            Text("Analyze")
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showAnalyzePrompt = false
                            detailArticle = selectedArticle
                            selectedArticle = null
                        },
                    ) {
                        Text("View Without Analysis")
                    }
                },
            )
        }
    }

    // Load sections when detail article changes
    LaunchedEffect(detailArticle?.id) {
        detailArticle?.let { art ->
            articleSections = deepAnalysisService.getSections(art.id)
            val rawContent = art.content.ifBlank { art.summary }
            localSections = if (rawContent.isNotBlank()) {
                deepAnalysisService.splitIntoSections(rawContent)
            } else {
                emptyList()
            }
        } ?: run {
            articleSections = emptyList()
            localSections = emptyList()
        }
    }

    // Full detail screen
    val activeProjectName = projects.firstOrNull { it.id == activeProjectId }?.name ?: ""

    detailArticle?.let { article ->
        ArticleDetailScreen(
            article = article,
            sourceName = sourceNames[article.sourceId] ?: "Unknown",
            activeProjectName = activeProjectName,
            sections = articleSections,
            localSections = localSections,
            sectionLoadingIds = sectionLoadingIds,
            sectionAnalyzingIndices = sectionAnalyzingIndices,
            onBack = {
                detailArticle = null
                articleSections = emptyList()
                localSections = emptyList()
                loadData()
            },
            onStarToggle = {
                val updated = article.copy(starred = !article.starred)
                db.articleBox.put(updated)
                detailArticle = updated
                if (updated.starred && memoryManager != null) {
                    scope.launch {
                        try {
                            val content = buildString {
                                append("Starred article: ${updated.title}.")
                                if (updated.aiSummary.isNotBlank()) {
                                    append(" Summary: ${updated.aiSummary}.")
                                }
                            }
                            withContext(Dispatchers.Default) {
                                memoryManager.store(
                                    content = content.take(1000),
                                    category = "research_interest",
                                    source = "star",
                                )
                            }
                        } catch (_: Exception) { }
                    }
                }
            },
            onArchiveToggle = {
                val updated = article.copy(archived = !article.archived)
                db.articleBox.put(updated)
                detailArticle = updated
            },
            onAnalyzeSection = { sectionIndex ->
                scope.launch {
                    sectionAnalyzingIndices = sectionAnalyzingIndices + sectionIndex
                    try {
                        withContext(Dispatchers.Default) {
                            deepAnalysisService.analyzeSingleSection(article.id, sectionIndex)
                        }
                        articleSections = deepAnalysisService.getSections(article.id)
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar(
                            "Analysis failed: ${e.message ?: "Unknown error"}"
                        )
                    } finally {
                        sectionAnalyzingIndices = sectionAnalyzingIndices - sectionIndex
                    }
                }
            },
            onTranslateSection = { sectionId ->
                scope.launch {
                    sectionLoadingIds = sectionLoadingIds + sectionId
                    try {
                        val language = configStore.load().preferences.language
                        withContext(Dispatchers.Default) {
                            deepAnalysisService.translateSection(sectionId, language)
                        }
                        articleSections = deepAnalysisService.getSections(article.id)
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar(
                            "Translation failed: ${e.message ?: "Unknown error"}"
                        )
                    } finally {
                        sectionLoadingIds = sectionLoadingIds - sectionId
                    }
                }
            },
        )
    }
}

@Composable
private fun ArticleCard(
    article: Article,
    sourceName: String,
    onClick: () -> Unit,
    onStarClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stripHtml(article.title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = sourceName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (article.sourceType.isNotBlank()) {
                        Text(
                            text = displaySourceTypeShort(article.sourceType),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                    if (article.aiRelevanceScore > 0f) {
                        Text(
                            text = "${"%.0f".format(article.aiRelevanceScore * 100)}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    if (article.citationCount > 0) {
                        Text(
                            text = "${article.citationCount} cited",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    if (article.fetchedAt > 0) {
                        Text(
                            text = formatEpochDate(article.fetchedAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (article.analysisStatus == AnalysisStatus.ANALYZED) {
                        Text(
                            text = "Analyzed",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (article.archived) {
                        Text(
                            text = "Archived",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                // Show AI summary if analyzed, otherwise show stripped source summary
                val previewText = if (article.aiSummary.isNotBlank()) {
                    article.aiSummary
                } else if (article.summary.isNotBlank()) {
                    stripHtml(article.summary)
                } else {
                    null
                }
                if (previewText != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = previewText,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            IconButton(onClick = onStarClick) {
                Icon(
                    imageVector = if (article.starred) Icons.Default.Star
                    else Icons.Default.StarBorder,
                    contentDescription = if (article.starred) "Unstar" else "Star",
                    tint = if (article.starred) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ArticleDetailDialog(
    article: Article,
    sourceName: String,
    onDismiss: () -> Unit,
    onArchiveToggle: () -> Unit,
    onViewDetails: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onViewDetails) {
                Text("View Details")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onArchiveToggle) {
                    Icon(
                        imageVector = if (article.archived) Icons.Default.Unarchive
                        else Icons.Default.Archive,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (article.archived) "Restore" else "Archive")
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        },
        title = {
            Text(
                text = stripHtml(article.title),
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Author
                if (article.author.isNotBlank()) {
                    Text(
                        text = article.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Source info
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "$sourceName (${displaySourceType(article.sourceType)})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (article.publishedAt > 0) {
                        Text(
                            text = formatEpochDate(article.publishedAt),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (article.fetchedAt > 0) {
                        Text(
                            text = formatEpochDate(article.fetchedAt),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Identifiers
                if (article.doi.isNotBlank() || article.arxivId.isNotBlank()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (article.doi.isNotBlank()) {
                            Text(
                                text = "DOI: ${article.doi}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (article.arxivId.isNotBlank()) {
                            Text(
                                text = "arXiv: ${article.arxivId}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // Metrics
                if (article.aiRelevanceScore > 0f || article.citationCount > 0 || article.influentialCitationCount > 0) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (article.aiRelevanceScore > 0f) {
                            Text(
                                text = "Relevance: ${"%.0f".format(article.aiRelevanceScore * 100)}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                        if (article.citationCount > 0) {
                            Text(
                                text = "Citations: ${article.citationCount}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                        if (article.influentialCitationCount > 0) {
                            Text(
                                text = "Influential: ${article.influentialCitationCount}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                }

                // Abstract / Summary from source (HTML stripped)
                if (article.summary.isNotBlank()) {
                    Text(
                        text = stripHtml(article.summary),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // AI Summary
                if (article.aiSummary.isNotBlank()) {
                    Text(
                        text = "AI Summary",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = article.aiSummary,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Keywords
                if (article.keywords.isNotBlank()) {
                    Text(
                        text = "Keywords: ${article.keywords}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}

private fun formatStageName(stage: PipelineStage): String = when (stage) {
    PipelineStage.FETCHING -> "Fetching articles..."
    PipelineStage.DEDUPLICATING -> "Removing duplicates..."
    PipelineStage.EMBEDDING -> "Generating embeddings..."
    PipelineStage.SCORING -> "Scoring relevance..."
    PipelineStage.ANALYZING -> "Analyzing articles..."
    PipelineStage.SPARKING -> "Generating insights..."
    PipelineStage.DIGESTING -> "Creating digest..."
}
