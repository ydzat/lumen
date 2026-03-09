package com.lumen.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import com.lumen.core.database.entities.ResearchProject
import com.lumen.research.ProjectManager
import com.lumen.ui.i18n.strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

@Composable
fun ProjectsScreen(onBack: () -> Unit) {
    val s = strings()
    val projectManager = koinInject<ProjectManager>()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var projects by remember { mutableStateOf<List<ResearchProject>>(emptyList()) }
    var articleCounts by remember { mutableStateOf<Map<Long, Int>>(emptyMap()) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingProject by remember { mutableStateOf<ResearchProject?>(null) }
    var deletingProject by remember { mutableStateOf<ResearchProject?>(null) }

    fun loadProjects() {
        projects = projectManager.listAll()
        articleCounts = projects.associate { it.id to projectManager.getArticlesForProject(it.id).size }
    }

    LaunchedEffect(Unit) { loadProjects() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = s.createProject)
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s.back)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = s.researchProjects,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp),
            ) {
                items(projects, key = { it.id }) { project ->
                    ProjectCard(
                        project = project,
                        articleCount = articleCounts[project.id] ?: 0,
                        onSetActive = {
                            projectManager.setActive(project.id)
                            loadProjects()
                        },
                        onEdit = { editingProject = project },
                        onDelete = { deletingProject = project },
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        ProjectFormDialog(
            title = s.createProject,
            initial = null,
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, description, keywords ->
                scope.launch {
                    withContext(Dispatchers.Default) {
                        projectManager.create(name, description, keywords)
                    }
                    showCreateDialog = false
                    loadProjects()
                    snackbarHostState.showSnackbar("Project created")
                }
            },
        )
    }

    editingProject?.let { project ->
        ProjectFormDialog(
            title = s.editProject,
            initial = project,
            onDismiss = { editingProject = null },
            onConfirm = { name, description, keywords ->
                scope.launch {
                    withContext(Dispatchers.Default) {
                        projectManager.update(
                            project.copy(name = name, description = description, keywords = keywords)
                        )
                    }
                    editingProject = null
                    loadProjects()
                    snackbarHostState.showSnackbar("Project updated")
                }
            },
        )
    }

    deletingProject?.let { project ->
        AlertDialog(
            onDismissRequest = { deletingProject = null },
            title = { Text(s.deleteProject) },
            text = { Text(s.deleteProjectConfirm(project.name)) },
            confirmButton = {
                TextButton(onClick = {
                    projectManager.delete(project.id)
                    deletingProject = null
                    loadProjects()
                }) {
                    Text(s.delete, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingProject = null }) { Text(s.cancel) }
            },
        )
    }
}

@Composable
private fun ProjectCard(
    project: ResearchProject,
    articleCount: Int,
    onSetActive: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val s = strings()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (project.isActive) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = project.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                    )
                    if (project.isActive) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = s.active,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (project.description.isNotBlank()) {
                    Text(
                        text = project.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (project.keywords.isNotBlank()) {
                        Text(
                            text = project.keywords,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            maxLines = 1,
                        )
                    }
                    Text(
                        text = s.articlesCount(articleCount.toLong()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            IconButton(onClick = onSetActive) {
                Icon(
                    imageVector = if (project.isActive) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = if (project.isActive) s.active else s.setActive,
                    tint = if (project.isActive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = s.edit)
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = s.delete,
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun ProjectFormDialog(
    title: String,
    initial: ResearchProject?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String, keywords: String) -> Unit,
) {
    val s = strings()
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var description by remember { mutableStateOf(initial?.description ?: "") }
    var keywords by remember { mutableStateOf(initial?.keywords ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(s.name) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(s.description) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                )
                OutlinedTextField(
                    value = keywords,
                    onValueChange = { keywords = it },
                    label = { Text(s.keywordsLabel) },
                    placeholder = { Text(s.keywordsPlaceholder) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), description.trim(), keywords.trim()) },
                enabled = name.isNotBlank(),
            ) {
                Text(s.save)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(s.cancel) }
        },
    )
}
