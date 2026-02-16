package com.screenshotsearcher.ui.search

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.screenshotsearcher.core.model.Screenshot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    query: String,
    onQueryChange: (String) -> Unit,
    mode: SearchMode,
    onModeChange: (SearchMode) -> Unit,
    onPickImage: () -> Unit,
    onBack: () -> Unit,
    onOpenDetail: (Long) -> Unit
) {
    val results by viewModel.results.collectAsState()
    val currentQuery by rememberUpdatedState(query)

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            OutlinedTextField(
                value = currentQuery,
                onValueChange = {
                    onQueryChange(it)
                    viewModel.search(it, mode)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                label = {
                    Text(
                        when (mode) {
                            SearchMode.KEYWORD -> "Keyword (OCR)"
                            SearchMode.SEMANTIC -> "Semantic"
                            SearchMode.SIMILAR_IMAGE -> "Similar Image"
                        }
                    )
                },
                singleLine = true
            )
            ModeToggle(mode = mode, onModeChange = onModeChange)
            if (mode == SearchMode.SIMILAR_IMAGE) {
                OutlinedButton(
                    onClick = onPickImage,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Text("Pick Image")
                }
            }
            ResultsGrid(results = results, onOpenDetail = onOpenDetail)
        }
    }
}

@Composable
private fun ModeToggle(mode: SearchMode, onModeChange: (SearchMode) -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(onClick = { onModeChange(SearchMode.KEYWORD) }) {
            Text(if (mode == SearchMode.KEYWORD) "Keyword ✓" else "Keyword")
        }
        OutlinedButton(onClick = { onModeChange(SearchMode.SEMANTIC) }) {
            Text(if (mode == SearchMode.SEMANTIC) "Semantic ✓" else "Semantic")
        }
        OutlinedButton(onClick = { onModeChange(SearchMode.SIMILAR_IMAGE) }) {
            Text(if (mode == SearchMode.SIMILAR_IMAGE) "Similar ✓" else "Similar")
        }
    }
}

@Composable
private fun ResultsGrid(results: List<SearchResult>, onOpenDetail: (Long) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(120.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(results, key = { it.screenshot.id }) { result ->
            val screenshot = result.screenshot
            val bitmap = remember(screenshot.thumbnailBytes) {
                BitmapFactory.decodeByteArray(
                    screenshot.thumbnailBytes,
                    0,
                    screenshot.thumbnailBytes.size
                )
            }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(4.dp)
                        .clickable { onOpenDetail(screenshot.id) }
                )
            }
            if (result.score != null) {
                Text(
                    text = "Score: ${"%.2f".format(result.score)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )
            }
        }
    }
}
