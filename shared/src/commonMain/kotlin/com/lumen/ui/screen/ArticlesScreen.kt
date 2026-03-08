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
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
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
import com.lumen.research.collector.CollectorManager
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
    var isRefreshing by remember { mutableStateOf(false) }
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

    LaunchedEffect(sortMode, filterStarred, activeProjectId) {
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
                        scope.launch {
                            try {
                                val result = withContext(Dispatchers.Default) {
                                    collectorManager.runNow()
                                }
                                loadData()
                                snackbarHostState.showSnackbar(
                                    "Fetched ${result.fetched}, analyzed ${result.analyzed} article(s)"
                                )
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar(
                                    "Refresh failed: ${e.message ?: "Unknown error"}"
                                )
                            } finally {
                                isRefreshing = false
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
                    if (article.aiRelevanceScore > 0f) {
                        Text(
                            text = "${"%.0f".format(article.aiRelevanceScore * 100)}%",
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
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
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
                    if (article.fetchedAt > 0) {
                        Text(
                            text = formatEpochDate(article.fetchedAt),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (article.aiRelevanceScore > 0f) {
                    Text(
                        text = "Relevance: ${"%.0f".format(article.aiRelevanceScore * 100)}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
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

