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
import com.lumen.research.collector.AnalysisStatus
import com.lumen.research.collector.CollectorManager
import com.lumen.research.collector.PipelineStage
import com.lumen.research.parseCsvSet
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
    var projectExpanded by remember { mutableStateOf(false) }

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
                                        progressStage = formatStageName(stage)
                                        progressDetail = if (total > 1) "$current/$total" else ""
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
                    text = article.title,
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
                            text = displaySourceType(article.sourceType),
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
                    val statusLabel = when (article.analysisStatus) {
                        AnalysisStatus.ANALYZED -> "Analyzed"
                        AnalysisStatus.SCORED -> "Scored"
                        AnalysisStatus.EMBEDDED -> "Embedded"
                        else -> "New"
                    }
                    val statusColor = when (article.analysisStatus) {
                        AnalysisStatus.ANALYZED -> MaterialTheme.colorScheme.primary
                        AnalysisStatus.SCORED -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                    )
                    if (article.archived) {
                        Text(
                            text = "Archived",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                if (article.aiSummary.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = article.aiSummary,
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
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        dismissButton = {
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
        },
        title = {
            Text(
                text = article.title,
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Source: $sourceName",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (article.sourceType.isNotBlank()) {
                        Text(
                            text = displaySourceType(article.sourceType),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                    if (article.fetchedAt > 0) {
                        Text(
                            text = formatEpochDate(article.fetchedAt),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

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

                if (article.aiSummary.isNotBlank()) {
                    Text(
                        text = "AI Summary",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = article.aiSummary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                if (article.keywords.isNotBlank()) {
                    Text(
                        text = "Keywords: ${article.keywords}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (article.url.isNotBlank()) {
                    Text(
                        text = "URL: ${article.url}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}

private fun displaySourceType(type: String): String = when (type.uppercase()) {
    "ARXIV_API" -> "arXiv"
    "SEMANTIC_SCHOLAR" -> "Scholar"
    "GITHUB_RELEASES" -> "GitHub"
    "RSS" -> "RSS"
    else -> type
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
