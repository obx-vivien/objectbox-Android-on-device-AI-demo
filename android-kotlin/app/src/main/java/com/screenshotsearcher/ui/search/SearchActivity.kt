package com.screenshotsearcher.ui.search

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.screenshotsearcher.ui.detail.DetailActivity
import com.screenshotsearcher.ui.theme.ScreenshotSearcherTheme

class SearchActivity : ComponentActivity() {
    private val viewModel: SearchViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ScreenshotSearcherTheme {
                val queryState = remember { mutableStateOf("") }
                val modeState = remember { mutableStateOf(SearchMode.KEYWORD) }
                val imagePicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.PickVisualMedia(),
                    onResult = { uri ->
                        if (uri != null) {
                            viewModel.searchByImage(uri)
                        }
                    }
                )
                SearchScreen(
                    viewModel = viewModel,
                    query = queryState.value,
                    onQueryChange = { queryState.value = it },
                    mode = modeState.value,
                    onModeChange = {
                        modeState.value = it
                        viewModel.search(queryState.value, it)
                    },
                    onPickImage = {
                        imagePicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onBack = { finish() },
                    onOpenDetail = { id ->
                        val intent = Intent(this, DetailActivity::class.java)
                        intent.putExtra(DetailActivity.EXTRA_ID, id)
                        startActivity(intent)
                    }
                )
            }
        }
    }
}
