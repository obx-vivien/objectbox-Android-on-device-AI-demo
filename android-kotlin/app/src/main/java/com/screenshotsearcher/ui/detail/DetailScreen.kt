package com.screenshotsearcher.ui.detail

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.screenshotsearcher.core.data.ScreenshotRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(viewModel: DetailViewModel, onBack: () -> Unit) {
    val screenshot by viewModel.screenshot.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Details") },
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
        val scrollState = rememberScrollState()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(scrollState)
            ) {
                val bytes = screenshot?.thumbnailBytes
                if (bytes != null && bytes.isNotEmpty()) {
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bitmap != null) {
                        Image(bitmap = bitmap.asImageBitmap(), contentDescription = null)
                    }
                }
                Text(
                    text = "OCR Text",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 16.dp)
                )
                Text(
                    text = screenshot?.ocrText?.ifBlank { "(none)" } ?: "(none)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 8.dp)
                )

                TagsSection(labels = screenshot?.labels.orEmpty())
                MetadataSection(screenshot = screenshot)
            }
            DetailScrollbar(scrollState = scrollState)
        }
    }
}

@Composable
private fun DetailScrollbar(scrollState: androidx.compose.foundation.ScrollState) {
    val scrollRange = scrollState.maxValue.toFloat()
    if (scrollRange <= 0f) return
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(end = 6.dp)
    ) {
        val trackHeight = maxHeight
        val trackHeightPx = with(density) { trackHeight.toPx() }
        val proportion = trackHeightPx / (trackHeightPx + scrollRange)
        val barHeight = trackHeight * proportion
        val offset = if (scrollRange == 0f) 0.dp else {
            (trackHeight - barHeight) * (scrollState.value / scrollRange)
        }
        Box(
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.TopEnd)
                .padding(top = offset)
                .background(Color(0xFFB0B0B0))
                .width(4.dp)
                .height(barHeight)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsSection(labels: List<com.screenshotsearcher.core.model.ImageLabel>) {
    val visible = labels
        .filter { it.confidence >= ScreenshotRepository.LABEL_DISPLAY_CONFIDENCE }
        .sortedByDescending { it.confidence }
        .take(5)
    if (visible.isEmpty()) return

    Text(
        text = "Tags",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 16.dp)
    )
    FlowRow(
        modifier = Modifier.padding(top = 8.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
    ) {
        visible.forEach { label ->
            val pct = (label.confidence * 100).toInt()
            AssistChip(
                onClick = {},
                label = { Text("${label.text} ${pct}%") },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            )
        }
    }
}

@Composable
private fun MetadataSection(screenshot: com.screenshotsearcher.core.model.Screenshot?) {
    if (screenshot == null) return
    Text(
        text = "Metadata",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 16.dp)
    )
    MetadataRow("Name", screenshot.displayName)
    MetadataRow("MIME", screenshot.mimeType)
    MetadataRow("Size", screenshot.sizeBytes?.let { "${it / 1024} KB" })
    MetadataRow("Dimensions", "${screenshot.width} × ${screenshot.height}")
    MetadataRow("Orientation", screenshot.orientation?.toString())
    MetadataRow("Date taken", screenshot.dateTaken?.toString())
    MetadataRow("Date modified", screenshot.dateModified?.toString())
    MetadataRow("Album", screenshot.album)
    MetadataRow("Duration", screenshot.durationMs?.toString())

    if (!screenshot.description.isNullOrBlank()) {
        Text(
            text = "Description",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            text = screenshot.description ?: "",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 8.dp)
        )
    }

    val colors = screenshot.dominantColors
    if (colors.isNotEmpty()) {
        Text(
            text = "Dominant Colors",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 16.dp)
        )
        FlowRow(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
        ) {
            colors.forEach { color ->
                Surface(
                    color = Color(color or 0xFF000000.toInt()),
                    modifier = Modifier.size(28.dp),
                    shape = MaterialTheme.shapes.small
                ) {}
            }
        }
    }
}

@Composable
private fun MetadataRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Text(
        text = "$label: $value",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 6.dp)
    )
}
