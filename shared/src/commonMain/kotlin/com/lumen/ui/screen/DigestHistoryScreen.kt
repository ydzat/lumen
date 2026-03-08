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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.Digest
import com.lumen.core.database.entities.ResearchProject
import com.lumen.research.ProjectManager
import com.lumen.research.digest.DigestFormatter
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DigestHistoryScreen(onBack: () -> Unit) {
    val db = koinInject<LumenDatabase>()
    val projectManager = koinInject<ProjectManager>()
    val digestFormatter = koinInject<DigestFormatter>()

    var digests by remember { mutableStateOf<List<Digest>>(emptyList()) }
    var projects by remember { mutableStateOf<List<ResearchProject>>(emptyList()) }
    var selectedProjectId by remember { mutableStateOf(0L) }
    var projectExpanded by remember { mutableStateOf(false) }
    var selectedDigest by remember { mutableStateOf<Digest?>(null) }

    fun loadData() {
        projects = projectManager.listAll()
        val allDigests = db.digestBox.all
        digests = allDigests
            .filter { selectedProjectId == 0L || it.projectId == selectedProjectId }
            .sortedByDescending { it.createdAt }
    }

    LaunchedEffect(selectedProjectId) { loadData() }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "Digest History",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
        }

        // Project filter
        ExposedDropdownMenuBox(
            expanded = projectExpanded,
            onExpandedChange = { projectExpanded = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            OutlinedTextField(
                value = if (selectedProjectId == 0L) "All Projects"
                else projects.firstOrNull { it.id == selectedProjectId }?.name ?: "All Projects",
                onValueChange = {},
                readOnly = true,
                label = { Text("Filter by Project") },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = projectExpanded)
                },
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
                singleLine = true,
            )
            ExposedDropdownMenu(
                expanded = projectExpanded,
                onDismissRequest = { projectExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("All Projects") },
                    onClick = {
                        selectedProjectId = 0L
                        projectExpanded = false
                    },
                )
                projects.forEach { project ->
                    DropdownMenuItem(
                        text = { Text(project.name) },
                        onClick = {
                            selectedProjectId = project.id
                            projectExpanded = false
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (digests.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Newspaper,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "No digests yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Digests are generated after articles are collected and analyzed.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            ) {
                items(digests, key = { it.id }) { digest ->
                    DigestCard(
                        digest = digest,
                        preview = digestFormatter.formatCompact(digest),
                        onClick = { selectedDigest = digest },
                    )
                }
            }
        }
    }

    selectedDigest?.let { digest ->
        DigestDetailDialog(
            digest = digest,
            formattedContent = digestFormatter.format(digest),
            onDismiss = { selectedDigest = null },
        )
    }
}

@Composable
private fun DigestCard(
    digest: Digest,
    preview: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = digest.date,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = digest.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = preview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DigestDetailDialog(
    digest: Digest,
    formattedContent: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        title = {
            Column {
                Text(
                    text = digest.date,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = digest.title,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = formattedContent,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
    )
}
