package com.lumen.ui.screen

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.lumen.companion.agent.ChatResult
import com.lumen.companion.agent.LumenAgent
import com.lumen.companion.persona.PersonaManager
import com.lumen.core.config.ConfigStore
import com.lumen.core.config.LlmConfig
import com.lumen.research.collector.SourceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

private val PROVIDERS = listOf(
    "deepseek" to "DeepSeek",
    "openai" to "OpenAI",
    "anthropic" to "Anthropic",
    "custom" to "Custom",
)

private fun defaultModelForProvider(provider: String): String = when (provider) {
    "deepseek" -> "deepseek-chat"
    "openai" -> "gpt-4o"
    "anthropic" -> "claude-sonnet-4-20250514"
    else -> ""
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val configStore = koinInject<ConfigStore>()
    val sourceManager = koinInject<SourceManager>()
    val personaManager = koinInject<PersonaManager>()

    var step by remember { mutableStateOf(0) }

    var provider by remember { mutableStateOf("deepseek") }
    var model by remember { mutableStateOf(defaultModelForProvider("deepseek")) }
    var apiKey by remember { mutableStateOf("") }
    var apiBase by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    fun completeOnboarding() {
        val config = configStore.load()
        val llmConfig = if (apiKey.isNotBlank()) {
            LlmConfig(provider = provider, model = model, apiKey = apiKey, apiBase = apiBase)
        } else {
            config.llm
        }
        configStore.save(
            config.copy(
                llm = llmConfig,
                preferences = config.preferences.copy(hasCompletedOnboarding = true),
            ),
        )
        onComplete()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            when (step) {
                0 -> WelcomeStep(
                    provider = provider,
                    model = model,
                    apiKey = apiKey,
                    apiBase = apiBase,
                    snackbarHostState = snackbarHostState,
                    onProviderChange = { provider = it; model = defaultModelForProvider(it) },
                    onModelChange = { model = it },
                    onApiKeyChange = { apiKey = it },
                    onApiBaseChange = { apiBase = it },
                    onNext = { step = 1 },
                    onSkip = { completeOnboarding() },
                )
                1 -> SourcesStep(
                    sourceManager = sourceManager,
                    onNext = { step = 2 },
                    onSkip = {
                        sourceManager.seedDefaultsIfEmpty()
                        completeOnboarding()
                    },
                )
                2 -> PersonaStep(
                    personaManager = personaManager,
                    onComplete = { completeOnboarding() },
                    onSkip = { completeOnboarding() },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WelcomeStep(
    provider: String,
    model: String,
    apiKey: String,
    apiBase: String,
    snackbarHostState: SnackbarHostState,
    onProviderChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onApiBaseChange: (String) -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var providerExpanded by remember { mutableStateOf(false) }
    var apiKeyVisible by remember { mutableStateOf(false) }
    var isTesting by remember { mutableStateOf(false) }

    Text("Welcome to Lumen", style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(8.dp))
    Text(
        "Lumen is your personal AI assistant for research and companionship. " +
            "Let's get you set up in a few quick steps.",
        style = MaterialTheme.typography.bodyLarge,
    )

    Spacer(Modifier.height(24.dp))
    Text("Step 1/3: Configure LLM Provider", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(16.dp))

    ExposedDropdownMenuBox(
        expanded = providerExpanded,
        onExpandedChange = { providerExpanded = it },
    ) {
        OutlinedTextField(
            value = PROVIDERS.firstOrNull { it.first == provider }?.second ?: provider,
            onValueChange = {},
            readOnly = true,
            label = { Text("Provider") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerExpanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = providerExpanded,
            onDismissRequest = { providerExpanded = false },
        ) {
            PROVIDERS.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onProviderChange(value)
                        providerExpanded = false
                    },
                )
            }
        }
    }

    Spacer(Modifier.height(12.dp))

    OutlinedTextField(
        value = model,
        onValueChange = onModelChange,
        label = { Text("Model") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(12.dp))

    OutlinedTextField(
        value = apiKey,
        onValueChange = onApiKeyChange,
        label = { Text("API Key") },
        singleLine = true,
        visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                Icon(
                    imageVector = if (apiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (apiKeyVisible) "Hide API key" else "Show API key",
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(12.dp))

    OutlinedTextField(
        value = apiBase,
        onValueChange = onApiBaseChange,
        label = { Text("API Base URL (optional)") },
        placeholder = { Text("Leave empty for default") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(16.dp))

    OutlinedButton(
        onClick = {
            isTesting = true
            scope.launch {
                val agent = LumenAgent(LlmConfig(provider = provider, model = model, apiKey = apiKey, apiBase = apiBase))
                try {
                    val result = withContext(Dispatchers.Default) { agent.chat("Hello") }
                    val message = when (result) {
                        is ChatResult.Success -> "Connection successful!"
                        is ChatResult.Error -> "Connection failed: ${result.message}"
                    }
                    snackbarHostState.showSnackbar(message)
                } finally {
                    agent.close()
                    isTesting = false
                }
            }
        },
        enabled = !isTesting && apiKey.isNotBlank(),
    ) {
        if (isTesting) {
            CircularProgressIndicator(modifier = Modifier.height(16.dp).width(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text("Test Connection")
    }

    Spacer(Modifier.height(24.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        OutlinedButton(onClick = onSkip) { Text("Skip") }
        Button(onClick = onNext) { Text("Next") }
    }
}

@Composable
private fun SourcesStep(
    sourceManager: SourceManager,
    onNext: () -> Unit,
    onSkip: () -> Unit,
) {
    val defaultSources = remember { SourceManager.DEFAULT_SOURCES }
    val enabledStates = remember { mutableStateOf(defaultSources.map { true }.toMutableList()) }

    Text("Step 2/3: RSS Sources", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    Text(
        "Lumen can collect articles from these RSS feeds. Toggle the sources you want to follow.",
        style = MaterialTheme.typography.bodyMedium,
    )
    Spacer(Modifier.height(16.dp))

    defaultSources.forEachIndexed { index, source ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(source.name, style = MaterialTheme.typography.bodyLarge)
                if (source.description.isNotBlank()) {
                    Text(source.description, style = MaterialTheme.typography.bodySmall)
                }
            }
            Switch(
                checked = enabledStates.value[index],
                onCheckedChange = { checked ->
                    enabledStates.value = enabledStates.value.toMutableList().also { it[index] = checked }
                },
            )
        }
    }

    Spacer(Modifier.height(24.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        OutlinedButton(onClick = onSkip) { Text("Skip") }
        Button(
            onClick = {
                val now = System.currentTimeMillis()
                val selectedSources = defaultSources.filterIndexed { i, _ -> enabledStates.value[i] }
                    .map { it.copy(createdAt = now) }
                if (selectedSources.isNotEmpty()) {
                    selectedSources.forEach { sourceManager.add(it) }
                }
                onNext()
            },
        ) {
            Text("Next")
        }
    }
}

@Composable
private fun PersonaStep(
    personaManager: PersonaManager,
    onComplete: () -> Unit,
    onSkip: () -> Unit,
) {
    val personas = remember {
        personaManager.seedBuiltInPersonas()
        personaManager.listAll()
    }
    var selectedId by remember { mutableStateOf(personas.firstOrNull { it.isActive }?.id ?: 0L) }

    Text("Step 3/3: Choose Your Persona", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    Text(
        "Select how Lumen should interact with you. You can change this anytime in Settings.",
        style = MaterialTheme.typography.bodyMedium,
    )
    Spacer(Modifier.height(16.dp))

    personas.forEach { persona ->
        val isSelected = persona.id == selectedId
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(8.dp),
                )
                .clickable { selectedId = persona.id }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = isSelected, onClick = { selectedId = persona.id })
            Spacer(Modifier.width(8.dp))
            Column {
                Text(persona.name, style = MaterialTheme.typography.bodyLarge)
                if (persona.greeting.isNotBlank()) {
                    Text(persona.greeting, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    Spacer(Modifier.height(24.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        OutlinedButton(onClick = onSkip) { Text("Skip") }
        Button(
            onClick = {
                if (selectedId > 0) {
                    personaManager.setActive(selectedId)
                }
                onComplete()
            },
        ) {
            Text("Complete")
        }
    }
}
