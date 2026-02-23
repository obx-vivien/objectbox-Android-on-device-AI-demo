package com.screenshotsearcher.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.screenshotsearcher.infra.captioning.GemmaCaptioner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val config by viewModel.moduleConfig.collectAsState()
    val llmModelAvailable by viewModel.llmModelAvailable.collectAsState()
    val llmActionStatus by viewModel.llmActionStatus.collectAsState()
    val showReindexConfirm = remember { mutableStateOf(false) }
    val showClearConfirm = remember { mutableStateOf(false) }
    val showModelHelp = remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshLlmStatus()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ToggleRow(
                label = "OCR",
                description = "Extracts on-device text from images for keyword search.",
                checked = config.ocrEnabled,
                onChecked = { value ->
                    viewModel.updateModuleConfig { it.ocrEnabled = value }
                }
            )
            ToggleRow(
                label = "Text Embeddings",
                description = "Creates semantic vectors for OCR/description text.",
                checked = config.textEmbeddingsEnabled,
                onChecked = { value ->
                    viewModel.updateModuleConfig { it.textEmbeddingsEnabled = value }
                }
            )
            ToggleRow(
                label = "Labeling",
                description = "Generates tags/categories from the image.",
                checked = config.labelingEnabled,
                onChecked = { value ->
                    viewModel.updateModuleConfig { it.labelingEnabled = value }
                }
            )
            ToggleRow(
                label = "Gemma captioning",
                description = "Generates a short caption using the Gemma model (requires model file).",
                checked = config.llmEnabled,
                onChecked = { value ->
                    viewModel.updateModuleConfig { it.llmEnabled = value }
                    if (!value) {
                        viewModel.releaseLlm()
                    }
                }
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Gemma model: " + if (llmModelAvailable) "Installed" else "Missing",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = { showModelHelp.value = true }) {
                        Text("Install model")
                    }
                    OutlinedButton(onClick = viewModel::warmUpLlm, enabled = llmModelAvailable) {
                        Text("Warm up")
                    }
                    OutlinedButton(onClick = viewModel::releaseLlm) {
                        Text("Release")
                    }
                }
                if (llmActionStatus != null) {
                    Text(
                        text = llmActionStatus ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                TextButton(onClick = viewModel::refreshLlmStatus) {
                    Text("Refresh model status")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Indexing",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = viewModel::cancelQueued) {
                    Text("Cancel queued")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = { showReindexConfirm.value = true }) {
                    Text("Re-index all")
                }
                OutlinedButton(onClick = { showClearConfirm.value = true }) {
                    Text("Clear database")
                }
            }
        }
    }

    if (showReindexConfirm.value) {
        AlertDialog(
            onDismissRequest = { showReindexConfirm.value = false },
            title = { Text("Re-index all images?") },
            text = {
                Text("This will re-queue all screenshots and rebuild OCR, labels, and embeddings.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showReindexConfirm.value = false
                    viewModel.reindexAll()
                }) {
                    Text("Re-index")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReindexConfirm.value = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showClearConfirm.value) {
        AlertDialog(
            onDismissRequest = { showClearConfirm.value = false },
            title = { Text("Clear database?") },
            text = {
                Text("This removes all indexed screenshots from the local database. It does not delete files.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm.value = false
                    viewModel.clearDatabase()
                }) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm.value = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showModelHelp.value) {
        AlertDialog(
            onDismissRequest = { showModelHelp.value = false },
            title = { Text("Install Gemma model") },
            text = {
                Text(
                    "Push the model to the device:\n" +
                        "adb shell mkdir -p /data/local/tmp/llm\n" +
                        "adb push <path-to-model> ${GemmaCaptioner.modelPath()}\n\n" +
                        "After pushing, return here and tap \"Refresh model status\"."
                )
            },
            confirmButton = {
                TextButton(onClick = { showModelHelp.value = false }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun ToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                } else {
                    Color.Gray
                }
            )
        }
        Switch(checked = checked, onCheckedChange = onChecked, enabled = enabled)
    }
}
