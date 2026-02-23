package com.screenshotsearcher.ui.home

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.screenshotsearcher.ui.detail.DetailActivity
import com.screenshotsearcher.ui.search.SearchViewModel
import com.screenshotsearcher.ui.settings.SettingsActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.screenshotsearcher.ui.search.SearchResult
import com.screenshotsearcher.core.data.MetadataFilters
import com.screenshotsearcher.core.data.ScreenshotRepository.Companion.LABEL_DISPLAY_CONFIDENCE

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun HomeScreen(viewModel: HomeViewModel) {
    val screenshots by viewModel.screenshots.collectAsState()
    val indexingStats by viewModel.indexingStats.collectAsState()
    val appState by viewModel.appState.collectAsState()
    val captionProgress by viewModel.captionProgress.collectAsState()
    val context = LocalContext.current
    val searchViewModel: SearchViewModel = viewModel()
    val results by searchViewModel.results.collectAsState()
    val availableLabels by searchViewModel.availableLabels.collectAsState()
    val selectedLabels by searchViewModel.selectedLabels.collectAsState()
    val metadataFilters by searchViewModel.metadataFilters.collectAsState()
    val queryState = remember { mutableStateOf("") }
    val showFilters = remember { mutableStateOf(false) }
    val showAllScreenshots = queryState.value.isBlank() && results.isEmpty()
    val displayResults = if (showAllScreenshots) {
        screenshots.map { screenshot ->
            SearchResult(
                screenshot = screenshot,
                ocrKeywordMatch = false,
                descriptionKeywordMatch = false,
                ocrSemanticScore = null,
                descriptionSemanticScore = null,
                rankBucket = Int.MAX_VALUE,
                finalScore = 0.0
            )
        }
    } else {
        results
    }
    val gridState = rememberLazyGridState()
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                viewModel.importImage(uri)
            }
        }
    )

    LaunchedEffect(screenshots.size) {
        searchViewModel.search(queryState.value)
    }

    val menuExpanded = remember { mutableStateOf(false) }
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    launcher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "Import Images")
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                SearchControls(
                    query = queryState.value,
                    onQueryChange = {
                        queryState.value = it
                    },
                    onSearch = {
                        searchViewModel.search(queryState.value)
                    },
                    resultCount = displayResults.size,
                    showFilters = showFilters.value,
                    onShowFiltersChange = { showFilters.value = it }
                )
                val pausedLabel = if (appState.userPaused && indexingStats.queued > 0) " | Paused" else ""
                Text(
                    text = "Queued ${indexingStats.queued} | Indexed ${indexingStats.indexed} | Failed ${indexingStats.failed} | Cancelled ${indexingStats.cancelled}$pausedLabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                if (screenshots.isEmpty()) {
                    EmptyState()
                } else {
                    ResultsGrid(
                        results = displayResults,
                        gridState = gridState,
                        query = queryState.value,
                        onOpenDetail = { id ->
                            val intent = Intent(context, DetailActivity::class.java)
                            intent.putExtra(DetailActivity.EXTRA_ID, id)
                            context.startActivity(intent)
                        }
                    )
                }
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
                            val intent = Intent(context, SettingsActivity::class.java)
                            context.startActivity(intent)
                        }
                    )
                }
            }
        }
        if (screenshots.isNotEmpty()) {
            Scrollbar(gridState = gridState)
        }
    }

    if (showFilters.value) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val selectedTab = remember { mutableStateOf(0) }
        ModalBottomSheet(
            onDismissRequest = { showFilters.value = false },
            sheetState = sheetState
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                PrimaryTabRow(selectedTabIndex = selectedTab.value) {
                    Tab(
                        selected = selectedTab.value == 0,
                        onClick = { selectedTab.value = 0 },
                        text = { Text("Categories") }
                    )
                    Tab(
                        selected = selectedTab.value == 1,
                        onClick = { selectedTab.value = 1 },
                        text = { Text("Metadata") }
                    )
                }
                if (selectedTab.value == 0) {
                    FilterChipsRow(
                        labels = availableLabels,
                        selected = selectedLabels,
                        onToggle = searchViewModel::toggleLabel,
                        onClear = searchViewModel::clearLabelFilters
                    )
                } else {
                    MetadataFiltersPanel(
                        filters = metadataFilters,
                        onChange = searchViewModel::updateMetadataFilters
                    )
                }
                TextButton(
                    onClick = {
                        searchViewModel.clearLabelFilters()
                        searchViewModel.updateMetadataFilters(MetadataFilters())
                    },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("Clear all filters")
                }
            }
        }
    }

    if (captionProgress.isRunning || appState.userPaused) {
        CaptionOverlay(
            captionProgress = captionProgress,
            isPaused = appState.userPaused,
            onPause = viewModel::pauseIngestion,
            onResume = viewModel::resumeIngestion
        )
    }
}

@Composable
private fun SearchControls(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    resultCount: Int,
    showFilters: Boolean,
    onShowFiltersChange: (Boolean) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                label = { Text("Search") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() })
            )
            TextButton(
                onClick = { onShowFiltersChange(!showFilters) },
                modifier = Modifier.height(56.dp)
            ) {
                Text("Filters")
            }
        }
        Text(
            text = "Found $resultCount results",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterChipsRow(
    labels: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onClear: () -> Unit
) {
    if (labels.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        labels.forEach { label ->
            FilterChip(
                selected = selected.contains(label),
                onClick = { onToggle(label) },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
        TextButton(onClick = onClear, enabled = selected.isNotEmpty()) {
            Text("Clear categories")
        }
    }
}

@Composable
private fun MetadataFiltersPanel(
    filters: MetadataFilters,
    onChange: (MetadataFilters) -> Unit
) {
    Column {
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
        TextButton(onClick = { onChange(MetadataFilters()) }) {
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
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        itemsIndexed(
            uniqueResults,
            key = { index, result -> "${result.screenshot.id}-${result.screenshot.originalUri}-$index" }
        ) { _, result ->
            ThumbnailCard(result = result, query = query, onOpenDetail = onOpenDetail)
        }
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
                .align(Alignment.TopEnd)
                .padding(top = barOffset)
                .background(Color(0xFFB0B0B0))
                .width(4.dp)
                .height(barHeight)
        )
    }
}

@Composable
private fun ThumbnailCard(result: SearchResult, query: String, onOpenDetail: (Long) -> Unit) {
    val screenshot = result.screenshot
    val bitmap = remember(screenshot.thumbnailBytes) {
        BitmapFactory.decodeByteArray(screenshot.thumbnailBytes, 0, screenshot.thumbnailBytes.size)
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

private fun formatScore(score: Double?): String {
    return if (score == null) "—" else String.format(java.util.Locale.US, "%.2f", score)
}

private fun hasTagMatch(screenshot: com.screenshotsearcher.core.model.Screenshot, query: String): Boolean {
    if (query.isBlank()) return false
    return screenshot.labels.any { label ->
        label.confidence >= LABEL_DISPLAY_CONFIDENCE && label.text.contains(query, true)
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No screenshots yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Import images to begin indexing.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun CaptionOverlay(
    captionProgress: com.screenshotsearcher.core.data.CaptionProgress,
    isPaused: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit
) {
    val total = captionProgress.total
    val processed = captionProgress.completed
    val label = if (total > 0) {
        if (isPaused) {
            "Captioning paused: $processed/$total"
        } else {
            "Captioning in progress: $processed/$total"
        }
    } else {
        if (isPaused) "Captioning paused" else "Captioning in progress"
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Row(
            modifier = Modifier
                .padding(top = OVERLAY_TOP_PADDING)
                .height(OVERLAY_ROW_HEIGHT)
                .background(
                    color = Color(0xFF2AA7A1).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 8.dp, vertical = 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            IconButton(onClick = { if (isPaused) onResume() else onPause() }) {
                Icon(
                    imageVector = if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    contentDescription = if (isPaused) "Resume captioning" else "Pause captioning"
                )
            }
        }
    }
}

private val OVERLAY_TOP_PADDING = 24.dp
private val OVERLAY_ROW_HEIGHT = 32.dp
