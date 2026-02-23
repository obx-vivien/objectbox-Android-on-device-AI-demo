package com.screenshotsearcher.ui.search

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.screenshotsearcher.core.data.MetadataFilters
import com.screenshotsearcher.core.model.Screenshot
import com.screenshotsearcher.core.data.ScreenshotRepository.Companion.LABEL_DISPLAY_CONFIDENCE

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onOpenDetail: (Long) -> Unit,
    onOpenSettings: () -> Unit
) {
    val results by viewModel.results.collectAsState()
    val availableLabels by viewModel.availableLabels.collectAsState()
    val selectedLabels by viewModel.selectedLabels.collectAsState()
    val metadataFilters by viewModel.metadataFilters.collectAsState()
    val currentQuery by rememberUpdatedState(query)
    val filtersExpanded = remember { androidx.compose.runtime.mutableStateOf(false) }
    val menuExpanded = remember { androidx.compose.runtime.mutableStateOf(false) }
    val gridState = rememberLazyGridState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Search") },
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = currentQuery,
                        onValueChange = {
                            onQueryChange(it)
                            viewModel.search(it)
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text("Search") },
                        singleLine = true
                    )
                    TextButton(
                        onClick = { filtersExpanded.value = !filtersExpanded.value },
                        modifier = Modifier.height(56.dp)
                    ) {
                        Text("Filters")
                    }
                }
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    DropdownMenu(
                        expanded = filtersExpanded.value,
                        onDismissRequest = { filtersExpanded.value = false }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            FilterChips(
                                labels = availableLabels,
                                selected = selectedLabels,
                                onToggle = viewModel::toggleLabel,
                                onClear = viewModel::clearLabelFilters
                            )
                            MetadataFiltersPanel(
                                filters = metadataFilters,
                                onChange = viewModel::updateMetadataFilters
                            )
                        }
                    }
                }
                ResultsGrid(results = results, gridState = gridState, query = currentQuery, onOpenDetail = onOpenDetail)
            }
            if (results.isNotEmpty()) {
                Scrollbar(gridState = gridState)
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 16.dp, top = OVERLAY_TOP_PADDING)
                    .height(OVERLAY_ROW_HEIGHT),
                contentAlignment = Alignment.CenterStart
            ) {
                IconButton(onClick = { menuExpanded.value = true }) {
                    Icon(imageVector = Icons.Filled.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(
                    expanded = menuExpanded.value,
                    onDismissRequest = { menuExpanded.value = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Settings") },
                        onClick = {
                            menuExpanded.value = false
                            onOpenSettings()
                        }
                    )
                }
            }
        }
    }
}


@Composable
private fun MetadataFiltersPanel(
    filters: MetadataFilters,
    onChange: (MetadataFilters) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(
            text = "Metadata Filters",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        TextFieldRow("Name contains", filters.nameContains) {
            onChange(filters.copy(nameContains = it))
        }
        TextFieldRow("MIME contains", filters.mimeTypeContains) {
            onChange(filters.copy(mimeTypeContains = it))
        }
        TextFieldRow("Album contains", filters.albumContains) {
            onChange(filters.copy(albumContains = it))
        }
        NumberRow("Min size (KB)", filters.minSizeBytes?.div(1024)) { value ->
            onChange(filters.copy(minSizeBytes = value?.times(1024)))
        }
        NumberRow("Max size (KB)", filters.maxSizeBytes?.div(1024)) { value ->
            onChange(filters.copy(maxSizeBytes = value?.times(1024)))
        }
        NumberRow("Min width", filters.minWidth?.toLong()) { value ->
            onChange(filters.copy(minWidth = value?.toInt()))
        }
        NumberRow("Max width", filters.maxWidth?.toLong()) { value ->
            onChange(filters.copy(maxWidth = value?.toInt()))
        }
        NumberRow("Min height", filters.minHeight?.toLong()) { value ->
            onChange(filters.copy(minHeight = value?.toInt()))
        }
        NumberRow("Max height", filters.maxHeight?.toLong()) { value ->
            onChange(filters.copy(maxHeight = value?.toInt()))
        }
        NumberRow("Orientation", filters.orientation?.toLong()) { value ->
            onChange(filters.copy(orientation = value?.toInt()))
        }
        NumberRow("Min duration (ms)", filters.minDurationMs) { value ->
            onChange(filters.copy(minDurationMs = value))
        }
        NumberRow("Max duration (ms)", filters.maxDurationMs) { value ->
            onChange(filters.copy(maxDurationMs = value))
        }
        NumberRow("Taken from (ms)", filters.dateTakenFrom) { value ->
            onChange(filters.copy(dateTakenFrom = value))
        }
        NumberRow("Taken to (ms)", filters.dateTakenTo) { value ->
            onChange(filters.copy(dateTakenTo = value))
        }
        NumberRow("Modified from (ms)", filters.dateModifiedFrom) { value ->
            onChange(filters.copy(dateModifiedFrom = value))
        }
        NumberRow("Modified to (ms)", filters.dateModifiedTo) { value ->
            onChange(filters.copy(dateModifiedTo = value))
        }
        OutlinedButton(
            onClick = { onChange(MetadataFilters()) },
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text("Clear metadata filters")
        }
    }
}

@Composable
private fun TextFieldRow(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        label = { Text(label) },
        singleLine = true
    )
}

@Composable
private fun NumberRow(label: String, value: Long?, onChange: (Long?) -> Unit) {
    val text = value?.toString().orEmpty()
    OutlinedTextField(
        value = text,
        onValueChange = {
            val parsed = it.toLongOrNull()
            onChange(parsed)
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        label = { Text(label) },
        singleLine = true
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterChips(
    labels: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onClear: () -> Unit
) {
    if (labels.isEmpty()) return
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(
            text = "Filters",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp)
        ) {
            labels.forEach { label ->
                val isSelected = selected.contains(label)
                FilterChip(
                    selected = isSelected,
                    onClick = { onToggle(label) },
                    label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        }
        if (selected.isNotEmpty()) {
            OutlinedButton(
                onClick = onClear,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text("Clear filters")
            }
        }
    }
}

@Composable
private fun ResultsGrid(
    results: List<SearchResult>,
    gridState: LazyGridState,
    query: String,
    onOpenDetail: (Long) -> Unit
) {
    val uniqueResults = results.distinctBy { it.screenshot.id }
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(180.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(
            uniqueResults,
            key = { index, result -> "${result.screenshot.id}-${result.screenshot.originalUri}-$index" }
        ) { _, result ->
            val screenshot = result.screenshot
            val bitmap = remember(screenshot.thumbnailBytes) {
                BitmapFactory.decodeByteArray(
                    screenshot.thumbnailBytes,
                    0,
                    screenshot.thumbnailBytes.size
                )
            }
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { onOpenDetail(screenshot.id) }
                    .padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.Start
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(96.dp)
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(2.dp)
                    )
                }
                val ocrMatch = if (result.ocrKeywordMatch) "Y" else "N"
                val descMatch = if (result.descriptionKeywordMatch) "Y" else "N"
                val ocrSemantic = formatScore(result.ocrSemanticScore)
                val descSemantic = formatScore(result.descriptionSemanticScore)
                val tagMatch = if (hasTagMatch(screenshot, query)) "Y" else "N"
                TableRow(label = "OCR keyw", value = ocrMatch)
                TableRow(label = "Desc keyw", value = descMatch)
                TableRow(label = "Tag", value = tagMatch)
                TableRow(label = "OCR sem", value = ocrSemantic)
                TableRow(label = "Desc sem", value = descSemantic)
            }
        }
    }
}

@Composable
private fun TableRow(label: String, value: String) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .background(Color(0xFFE5E5E5))
                .padding(horizontal = 2.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private val OVERLAY_TOP_PADDING = 24.dp
private val OVERLAY_ROW_HEIGHT = 32.dp

private fun formatScore(score: Double?): String {
    return if (score == null) "—" else String.format(java.util.Locale.US, "%.2f", score)
}

private fun hasTagMatch(screenshot: Screenshot, query: String): Boolean {
    if (query.isBlank()) return false
    return screenshot.labels.any { label ->
        label.confidence >= LABEL_DISPLAY_CONFIDENCE && label.text.contains(query, true)
    }
}

@Composable
private fun Scrollbar(gridState: LazyGridState) {
    val layoutInfo = gridState.layoutInfo
    val totalItems = layoutInfo.totalItemsCount
    if (totalItems == 0) return
    val visibleItems = layoutInfo.visibleItemsInfo.size
    if (visibleItems == 0) return
    val firstIndex = layoutInfo.visibleItemsInfo.first().index
    val proportion = visibleItems.toFloat() / totalItems.toFloat()
    val offset = firstIndex.toFloat() / totalItems.toFloat()
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(end = 6.dp)
    ) {
        val trackHeight = maxHeight
        val barHeight = trackHeight * proportion
        val barOffset = (trackHeight - barHeight) * offset
        Box(
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.TopEnd)
                .padding(top = barOffset)
                .background(Color(0xFFB0B0B0))
                .width(4.dp)
                .height(barHeight)
        )
    }
}
