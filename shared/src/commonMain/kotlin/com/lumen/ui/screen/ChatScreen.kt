package com.lumen.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lumen.companion.agent.ChatEvent
import com.lumen.companion.agent.LumenAgent
import com.lumen.companion.conversation.ConversationManager
import com.lumen.companion.persona.PersonaManager
import com.lumen.core.database.entities.Conversation
import com.lumen.core.database.entities.Message
import com.lumen.core.database.entities.Persona
import com.lumen.core.database.entities.ResearchProject
import com.lumen.core.util.formatEpochDate
import com.lumen.research.ProjectManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.getKoin
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

private enum class ChatSubScreen {
    List,
    Conversation,
}

@Composable
fun ChatScreen() {
    var subScreen by remember { mutableStateOf(ChatSubScreen.List) }
    var activeConversationId by remember { mutableStateOf(0L) }

    when (subScreen) {
        ChatSubScreen.List -> ConversationListScreen(
            onOpenConversation = { id ->
                activeConversationId = id
                subScreen = ChatSubScreen.Conversation
            },
        )
        ChatSubScreen.Conversation -> ConversationChatScreen(
            conversationId = activeConversationId,
            onBack = { subScreen = ChatSubScreen.List },
        )
    }
}

// --- Conversation List ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationListScreen(
    onOpenConversation: (Long) -> Unit,
) {
    val conversationManager = koinInject<ConversationManager>()
    val personaManager = koinInject<PersonaManager>()
    val projectManager = getKoin().getOrNull<ProjectManager>()

    val scope = rememberCoroutineScope()
    var conversations by remember { mutableStateOf<List<Conversation>>(emptyList()) }
    var personas by remember { mutableStateOf<List<Persona>>(emptyList()) }
    var projects by remember { mutableStateOf<List<ResearchProject>>(emptyList()) }
    var showNewDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Conversation?>(null) }

    fun loadData() {
        conversations = conversationManager.listConversations()
        personas = personaManager.listAll()
        projects = projectManager?.listAll() ?: emptyList()
    }

    LaunchedEffect(Unit) { loadData() }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showNewDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "New conversation")
            }
        },
    ) { padding ->
        if (conversations.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No conversations yet. Tap + to start one.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp,
                ),
            ) {
                items(conversations, key = { it.id }) { conversation ->
                    val persona = personas.firstOrNull { it.id == conversation.personaId }
                    ConversationCard(
                        conversation = conversation,
                        persona = persona,
                        onClick = { onOpenConversation(conversation.id) },
                        onDelete = { deleteTarget = conversation },
                    )
                }
            }
        }
    }

    // New conversation dialog
    if (showNewDialog) {
        NewConversationDialog(
            personas = personas,
            projects = projects,
            onDismiss = { showNewDialog = false },
            onCreate = { title, personaId, projectId ->
                val conv = conversationManager.createConversation(title, personaId, projectId)
                showNewDialog = false
                loadData()
                onOpenConversation(conv.id)
            },
        )
    }

    // Delete confirmation dialog
    deleteTarget?.let { conversation ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete conversation?") },
            text = { Text("\"${conversation.title}\" and all its messages will be permanently deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        conversationManager.deleteConversation(conversation.id)
                        deleteTarget = null
                        loadData()
                    }
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ConversationCard(
    conversation: Conversation,
    persona: Persona?,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = conversation.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (persona != null) {
                        Text(
                            text = persona.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        text = "${conversation.messageCount} messages",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (conversation.updatedAt > 0) {
                        Text(
                            text = formatEpochDate(conversation.updatedAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewConversationDialog(
    personas: List<Persona>,
    projects: List<ResearchProject>,
    onDismiss: () -> Unit,
    onCreate: (title: String, personaId: Long, projectId: Long) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var selectedPersonaId by remember { mutableStateOf(personas.firstOrNull { it.isActive }?.id ?: 0L) }
    var selectedProjectId by remember { mutableStateOf(0L) }
    var personaExpanded by remember { mutableStateOf(false) }
    var projectExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Conversation") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    placeholder = { Text("e.g. Research discussion") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Persona selector
                if (personas.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = personaExpanded,
                        onExpandedChange = { personaExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = personas.firstOrNull { it.id == selectedPersonaId }?.name ?: "Default",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Persona") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = personaExpanded) },
                            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                        )
                        ExposedDropdownMenu(
                            expanded = personaExpanded,
                            onDismissRequest = { personaExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Default") },
                                onClick = {
                                    selectedPersonaId = 0L
                                    personaExpanded = false
                                },
                            )
                            personas.forEach { persona ->
                                DropdownMenuItem(
                                    text = { Text(persona.name) },
                                    onClick = {
                                        selectedPersonaId = persona.id
                                        personaExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }

                // Project selector
                if (projects.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = projectExpanded,
                        onExpandedChange = { projectExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = if (selectedProjectId == 0L) "None"
                            else projects.firstOrNull { it.id == selectedProjectId }?.name ?: "None",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Project (optional)") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = projectExpanded) },
                            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                        )
                        ExposedDropdownMenu(
                            expanded = projectExpanded,
                            onDismissRequest = { projectExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("None") },
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
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val effectiveTitle = title.ifBlank { LumenAgent.DEFAULT_TITLE }
                    onCreate(effectiveTitle, selectedPersonaId, selectedProjectId)
                },
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

// --- Conversation Chat View ---

private sealed interface ChatUiItem {
    data class UserMessage(val message: Message) : ChatUiItem
    data class AssistantMessage(val message: Message, val displayText: String) : ChatUiItem
    data class ToolCall(val toolName: String, val args: String, val result: String?, val isLoading: Boolean) : ChatUiItem
    data class StatusInfo(val text: String) : ChatUiItem
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationChatScreen(
    conversationId: Long,
    onBack: () -> Unit,
) {
    val conversationManager = koinInject<ConversationManager>()
    val personaManager = koinInject<PersonaManager>()
    val agentFactory = getKoin()

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    val initialConversation = remember { conversationManager.getConversation(conversationId) }
    var conversation by remember { mutableStateOf(initialConversation) }
    val uiItems = remember { mutableStateListOf<ChatUiItem>() }
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var typewriterTarget by remember { mutableStateOf("") }
    var typewriterCounter by remember { mutableStateOf(0) }
    var typewriterDisplay by remember { mutableStateOf("") }
    var typewriterDone by remember { mutableStateOf(true) }
    var currentPersonaId by remember { mutableStateOf(initialConversation?.personaId ?: 0L) }
    var showPersonaPicker by remember { mutableStateOf(false) }
    val currentPersonaName = remember(currentPersonaId) {
        if (currentPersonaId > 0) personaManager.get(currentPersonaId)?.name ?: "Default" else "Default"
    }
    val agent = remember(currentPersonaId) {
        agentFactory.get<LumenAgent> {
            parametersOf(initialConversation?.projectId ?: 0L, currentPersonaId)
        }
    }
    DisposableEffect(agent) { onDispose { agent.close() } }

    fun loadMessages() {
        conversation = conversationManager.getConversation(conversationId)
        val messages = conversationManager.getMessages(conversationId)
        uiItems.clear()
        for (msg in messages) {
            when (msg.role) {
                "user" -> uiItems.add(ChatUiItem.UserMessage(msg))
                "assistant" -> uiItems.add(ChatUiItem.AssistantMessage(msg, msg.content))
                "tool_call" -> uiItems.add(ChatUiItem.ToolCall(msg.toolName, msg.toolArgs, null, false))
                "tool_result" -> {
                    val lastTool = uiItems.lastOrNull()
                    if (lastTool is ChatUiItem.ToolCall && lastTool.toolName == msg.toolName) {
                        uiItems[uiItems.lastIndex] = lastTool.copy(result = msg.content, isLoading = false)
                    }
                }
            }
        }
    }

    LaunchedEffect(conversationId) { loadMessages() }

    // Typewriter effect — counter key ensures re-trigger for duplicate responses
    LaunchedEffect(typewriterTarget, typewriterCounter) {
        if (typewriterTarget.isEmpty()) return@LaunchedEffect
        typewriterDone = false
        typewriterDisplay = ""
        for (i in typewriterTarget.indices) {
            typewriterDisplay = typewriterTarget.substring(0, i + 1)
            // Update last assistant item display
            val lastIdx = uiItems.indexOfLast { it is ChatUiItem.AssistantMessage }
            if (lastIdx >= 0) {
                val item = uiItems[lastIdx] as ChatUiItem.AssistantMessage
                uiItems[lastIdx] = item.copy(displayText = typewriterDisplay)
            }
            delay(TYPEWRITER_DELAY_MS)
        }
        typewriterDone = true
    }

    // Auto-scroll to bottom when items change
    LaunchedEffect(uiItems.size) {
        if (uiItems.isNotEmpty()) {
            listState.animateScrollToItem(uiItems.lastIndex)
        }
    }

    fun sendMessage() {
        val text = inputText.trim()
        if (text.isBlank() || isLoading) return
        inputText = ""
        isLoading = true

        scope.launch {
            try {
                withContext(Dispatchers.Default) {
                    agent.chatStream(conversationId, text)
                }.collect { event ->
                    when (event) {
                        is ChatEvent.UserMessageSaved -> {
                            val msg = conversationManager.getMessages(conversationId).lastOrNull { it.role == "user" }
                            if (msg != null) uiItems.add(ChatUiItem.UserMessage(msg))
                        }
                        is ChatEvent.MemoryRecalled -> {
                            uiItems.add(ChatUiItem.StatusInfo("Recalled ${event.count} memories"))
                        }
                        is ChatEvent.ToolCallStart -> {
                            uiItems.add(ChatUiItem.ToolCall(event.toolName, event.args, null, true))
                        }
                        is ChatEvent.ToolCallResult -> {
                            val idx = uiItems.indexOfLast {
                                it is ChatUiItem.ToolCall && it.toolName == event.toolName && it.isLoading
                            }
                            if (idx >= 0) {
                                val item = uiItems[idx] as ChatUiItem.ToolCall
                                uiItems[idx] = item.copy(result = event.result, isLoading = false)
                            }
                        }
                        is ChatEvent.AssistantResponse -> {
                            val msg = Message(role = "assistant", content = event.text)
                            uiItems.add(ChatUiItem.AssistantMessage(msg, ""))
                            typewriterTarget = event.text
                            typewriterCounter++
                        }
                        is ChatEvent.TitleGenerated -> {
                            conversation = conversation?.copy(title = event.title)
                        }
                        is ChatEvent.MemoryExtracted -> {
                            uiItems.add(ChatUiItem.StatusInfo("Extracted ${event.count} memories"))
                        }
                        is ChatEvent.Error -> {
                            snackbarHostState.showSnackbar(event.message)
                        }
                        is ChatEvent.Done -> {
                            conversation = conversationManager.getConversation(conversationId)
                        }
                    }
                }
            } catch (e: Exception) {
                snackbarHostState.showSnackbar(e.message ?: "Unknown error")
            } finally {
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = conversation?.title ?: "Chat",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = currentPersonaName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showPersonaPicker = true },
                        enabled = !isLoading,
                    ) {
                        Icon(Icons.Default.Person, contentDescription = "Change persona")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            // Message list
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                items(uiItems.size) { index ->
                    when (val item = uiItems[index]) {
                        is ChatUiItem.UserMessage -> UserBubble(item.message.content)
                        is ChatUiItem.AssistantMessage -> AssistantBubble(
                            text = item.displayText,
                            onTapToSkip = {
                                if (!typewriterDone) {
                                    typewriterDone = true
                                    typewriterDisplay = typewriterTarget
                                    val lastIdx = uiItems.indexOfLast { it is ChatUiItem.AssistantMessage }
                                    if (lastIdx >= 0) {
                                        val a = uiItems[lastIdx] as ChatUiItem.AssistantMessage
                                        uiItems[lastIdx] = a.copy(displayText = typewriterTarget)
                                    }
                                }
                            },
                        )
                        is ChatUiItem.ToolCall -> ToolCallCard(item)
                        is ChatUiItem.StatusInfo -> StatusChip(item.text)
                    }
                }
            }

            // Input bar
            InputBar(
                text = inputText,
                onTextChange = { inputText = it },
                onSend = { sendMessage() },
                isLoading = isLoading,
            )
        }
    }

    // Persona picker dialog
    if (showPersonaPicker) {
        val personas = remember { personaManager.listAll() }
        AlertDialog(
            onDismissRequest = { showPersonaPicker = false },
            title = { Text("Select Persona") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Default option
                    TextButton(
                        onClick = {
                            currentPersonaId = 0L
                            conversationManager.updatePersona(conversationId, 0L)
                            showPersonaPicker = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "Default",
                            fontWeight = if (currentPersonaId == 0L) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    personas.forEach { persona ->
                        TextButton(
                            onClick = {
                                currentPersonaId = persona.id
                                conversationManager.updatePersona(conversationId, persona.id)
                                showPersonaPicker = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = persona.name,
                                fontWeight = if (currentPersonaId == persona.id) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPersonaPicker = false }) { Text("Cancel") }
            },
        )
    }
}

// --- UI Components ---

@Composable
private fun UserBubble(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp),
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun AssistantBubble(text: String, onTapToSkip: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
            modifier = Modifier.widthIn(max = 300.dp).clickable(onClick = onTapToSkip),
        ) {
            Text(
                text = text.ifEmpty { "..." },
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ToolCallCard(item: ChatUiItem.ToolCall) {
    var expanded by remember { mutableStateOf(false) }
    val argsSummary = remember(item.args) { formatArgsSummary(item.args) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { expanded = !expanded },
            ) {
                if (item.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = item.toolName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }

            // Args summary — always visible when args are present
            if (argsSummary.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = argsSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
                    maxLines = if (expanded) Int.MAX_VALUE else 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Expanded: full result
            AnimatedVisibility(
                visible = expanded && item.result != null,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = item.result ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        maxLines = 10,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

internal fun formatArgsSummary(argsJson: String): String {
    if (argsJson.isBlank()) return ""
    return try {
        val element = kotlinx.serialization.json.Json.parseToJsonElement(argsJson)
        val obj = element as? kotlinx.serialization.json.JsonObject ?: return argsJson.take(ARGS_SUMMARY_MAX_LENGTH)
        obj.entries
            .mapNotNull { (key, value) ->
                val display = when (value) {
                    is kotlinx.serialization.json.JsonPrimitive -> value.content
                    else -> value.toString()
                }
                if (display.isNotBlank()) "$key: $display" else null
            }
            .joinToString(", ")
            .take(ARGS_SUMMARY_MAX_LENGTH)
    } catch (_: Exception) {
        argsJson.take(ARGS_SUMMARY_MAX_LENGTH)
    }
}

private const val ARGS_SUMMARY_MAX_LENGTH = 100

@Composable
private fun StatusChip(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp),
        )
    }
}

@Composable
private fun InputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    isLoading: Boolean,
) {
    Surface(
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = { Text("Type a message...") },
                modifier = Modifier.weight(1f),
                maxLines = 4,
                shape = RoundedCornerShape(24.dp),
            )
            Spacer(Modifier.width(8.dp))
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(40.dp),
                    strokeWidth = 3.dp,
                )
            } else {
                IconButton(
                    onClick = onSend,
                    enabled = text.isNotBlank(),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (text.isNotBlank()) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private const val TYPEWRITER_DELAY_MS = 20L
