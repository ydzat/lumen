package com.lumen.ui.screen

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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import com.lumen.core.config.ConfigStore
import com.lumen.core.config.LlmConfig
import com.lumen.core.config.UserPreferences
import com.lumen.ui.theme.ThemeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

private val THEMES = listOf(
    "system" to "System",
    "light" to "Light",
    "dark" to "Dark",
)

private val LANGUAGES = listOf(
    "zh" to "中文",
    "en" to "English",
)

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

private enum class SettingsSubScreen {
    Main,
    Sources,
    Projects,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    var subScreen by remember { mutableStateOf(SettingsSubScreen.Main) }

    when (subScreen) {
        SettingsSubScreen.Main -> SettingsMainScreen(
            onNavigateToSources = { subScreen = SettingsSubScreen.Sources },
            onNavigateToProjects = { subScreen = SettingsSubScreen.Projects },
        )
        SettingsSubScreen.Sources -> SourcesScreen(onBack = { subScreen = SettingsSubScreen.Main })
        SettingsSubScreen.Projects -> ProjectsScreen(onBack = { subScreen = SettingsSubScreen.Main })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsMainScreen(
    onNavigateToSources: () -> Unit,
    onNavigateToProjects: () -> Unit,
) {
    val configStore = koinInject<ConfigStore>()
    val config = remember { configStore.load() }

    var provider by remember { mutableStateOf(config.llm.provider) }
    var model by remember { mutableStateOf(config.llm.model) }
    var apiKey by remember { mutableStateOf(config.llm.apiKey) }
    var apiBase by remember { mutableStateOf(config.llm.apiBase) }
    var apiKeyVisible by remember { mutableStateOf(false) }
    var isTesting by remember { mutableStateOf(false) }
    var providerExpanded by remember { mutableStateOf(false) }

    var theme by remember { mutableStateOf(config.preferences.theme) }
    var language by remember { mutableStateOf(config.preferences.language) }
    var memoryAutoRecall by remember { mutableStateOf(config.preferences.memoryAutoRecall) }
    var memoryInterval by remember { mutableStateOf(config.preferences.memoryExtractionInterval.toFloat()) }
    var analysisMax by remember { mutableStateOf(config.preferences.analysisMaxPerCycle.toFloat()) }
    var dailyBudget by remember { mutableStateOf(config.preferences.dailyArticleBudget.toFloat()) }
    var themeExpanded by remember { mutableStateOf(false) }
    var languageExpanded by remember { mutableStateOf(false) }

    fun currentLlmConfig() = LlmConfig(
        provider = provider,
        model = model,
        apiKey = apiKey,
        apiBase = apiBase,
    )

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text("LLM Settings", style = MaterialTheme.typography.headlineSmall)

            Spacer(Modifier.height(16.dp))

            // Provider selector
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
                                provider = value
                                model = defaultModelForProvider(value)
                                providerExpanded = false
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Model name
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("Model") },
                placeholder = { Text("e.g. deepseek-chat") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            // API Key
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
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

            // API Base URL
            OutlinedTextField(
                value = apiBase,
                onValueChange = { apiBase = it },
                label = { Text("API Base URL (optional)") },
                placeholder = { Text("Leave empty for default") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(24.dp))

            // Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = {
                        val updatedConfig = config.copy(
                            llm = currentLlmConfig(),
                            preferences = config.preferences.copy(
                                theme = theme,
                                language = language,
                                memoryAutoRecall = memoryAutoRecall,
                                memoryExtractionInterval = memoryInterval.toInt(),
                                analysisMaxPerCycle = analysisMax.toInt(),
                                dailyArticleBudget = dailyBudget.toInt(),
                            ),
                        )
                        configStore.save(updatedConfig)
                        scope.launch {
                            snackbarHostState.showSnackbar("Settings saved")
                        }
                    },
                ) {
                    Text("Save")
                }

                OutlinedButton(
                    onClick = {
                        isTesting = true
                        scope.launch {
                            val agent = LumenAgent(currentLlmConfig())
                            try {
                                val result = withContext(Dispatchers.Default) {
                                    agent.chat("Hello")
                                }
                                val message = when (result) {
                                    is ChatResult.Success -> "Connection successful"
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
                        CircularProgressIndicator(
                            modifier = Modifier.height(16.dp).width(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Test Connection")
                }
            }

            Spacer(Modifier.height(32.dp))

            Text("Preferences", style = MaterialTheme.typography.headlineSmall)

            Spacer(Modifier.height(16.dp))

            // Theme selector
            ExposedDropdownMenuBox(
                expanded = themeExpanded,
                onExpandedChange = { themeExpanded = it },
            ) {
                OutlinedTextField(
                    value = THEMES.firstOrNull { it.first == theme }?.second ?: theme,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Theme") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = themeExpanded) },
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = themeExpanded,
                    onDismissRequest = { themeExpanded = false },
                ) {
                    THEMES.forEach { (value, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                theme = value
                                ThemeState.mode = value
                                themeExpanded = false
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Language selector
            ExposedDropdownMenuBox(
                expanded = languageExpanded,
                onExpandedChange = { languageExpanded = it },
            ) {
                OutlinedTextField(
                    value = LANGUAGES.firstOrNull { it.first == language }?.second ?: language,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Language") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = languageExpanded) },
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = languageExpanded,
                    onDismissRequest = { languageExpanded = false },
                ) {
                    LANGUAGES.forEach { (value, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                language = value
                                languageExpanded = false
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Memory auto-recall toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Memory Auto-Recall")
                Switch(
                    checked = memoryAutoRecall,
                    onCheckedChange = { memoryAutoRecall = it },
                )
            }

            Spacer(Modifier.height(12.dp))

            // Memory extraction interval
            Text("Memory Extraction Interval: ${memoryInterval.toInt()} messages")
            Slider(
                value = memoryInterval,
                onValueChange = { memoryInterval = it },
                valueRange = 5f..30f,
                steps = 24,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            // Daily article budget
            Text("Daily Article Budget: ${dailyBudget.toInt()}")
            Slider(
                value = dailyBudget,
                onValueChange = { dailyBudget = it },
                valueRange = 20f..500f,
                steps = 23,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            // Analysis max per cycle
            Text("Analysis Max Per Cycle: ${analysisMax.toInt()}")
            Slider(
                value = analysisMax,
                onValueChange = { analysisMax = it },
                valueRange = 1f..50f,
                steps = 48,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(32.dp))

            Text("Data Management", style = MaterialTheme.typography.headlineSmall)

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = onNavigateToSources,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Manage Sources")
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = onNavigateToProjects,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Manage Research Projects")
            }

            Spacer(Modifier.height(24.dp))

            Text("Backup", style = MaterialTheme.typography.headlineSmall)

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = {
                    scope.launch {
                        snackbarHostState.showSnackbar("Export is available via server API (POST /api/archive/export)")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Export Data")
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    scope.launch {
                        snackbarHostState.showSnackbar("Import is available via server API (POST /api/archive/import)")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Import Data")
            }
        }
    }
}
