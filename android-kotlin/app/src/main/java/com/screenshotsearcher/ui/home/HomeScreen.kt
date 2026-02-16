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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.unit.dp
import com.screenshotsearcher.core.model.Screenshot
import com.screenshotsearcher.ui.detail.DetailActivity
import com.screenshotsearcher.ui.search.SearchActivity

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun HomeScreen(viewModel: HomeViewModel) {
    val screenshots by viewModel.screenshots.collectAsState()
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                viewModel.importImage(uri)
            }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Screenshots") },
                actions = {
                    IconButton(
                        onClick = {
                            context.startActivity(Intent(context, SearchActivity::class.java))
                        }
                    ) {
                        Icon(imageVector = Icons.Filled.Search, contentDescription = "Search")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
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
            if (screenshots.isEmpty()) {
                EmptyState()
            } else {
                ScreenshotGrid(
                    screenshots = screenshots,
                    onOpenDetail = { id ->
                        val intent = Intent(context, DetailActivity::class.java)
                        intent.putExtra(DetailActivity.EXTRA_ID, id)
                        context.startActivity(intent)
                    }
                )
            }
        }
    }
}

@Composable
private fun ScreenshotGrid(screenshots: List<Screenshot>, onOpenDetail: (Long) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(120.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(screenshots, key = { it.id }) { screenshot ->
            ThumbnailCard(screenshot = screenshot, onOpenDetail = onOpenDetail)
        }
    }
}

@Composable
private fun ThumbnailCard(screenshot: Screenshot, onOpenDetail: (Long) -> Unit) {
    val bitmap = remember(screenshot.thumbnailBytes) {
        BitmapFactory.decodeByteArray(screenshot.thumbnailBytes, 0, screenshot.thumbnailBytes.size)
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface)
                .clickable { onOpenDetail(screenshot.id) }
                .padding(4.dp)
        )
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
