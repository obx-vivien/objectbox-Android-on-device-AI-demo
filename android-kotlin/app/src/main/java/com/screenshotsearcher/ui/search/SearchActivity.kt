package com.screenshotsearcher.ui.search

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.screenshotsearcher.ui.detail.DetailActivity
import com.screenshotsearcher.ui.settings.SettingsActivity
import com.screenshotsearcher.ui.theme.ScreenshotSearcherTheme

class SearchActivity : ComponentActivity() {
    private val viewModel: SearchViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ScreenshotSearcherTheme {
                val queryState = remember { mutableStateOf("") }
                SearchScreen(
                    viewModel = viewModel,
                    query = queryState.value,
                    onQueryChange = { queryState.value = it },
                    onBack = { finish() },
                    onOpenDetail = { id ->
                        val intent = Intent(this, DetailActivity::class.java)
                        intent.putExtra(DetailActivity.EXTRA_ID, id)
                        startActivity(intent)
                    },
                    onOpenSettings = {
                        val intent = Intent(this, SettingsActivity::class.java)
                        startActivity(intent)
                    }
                )
            }
        }
    }
}
