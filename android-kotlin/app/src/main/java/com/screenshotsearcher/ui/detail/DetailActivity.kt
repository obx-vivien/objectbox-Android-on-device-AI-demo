package com.screenshotsearcher.ui.detail

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.screenshotsearcher.ui.theme.ScreenshotSearcherTheme

class DetailActivity : ComponentActivity() {
    private val viewModel: DetailViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getLongExtra(EXTRA_ID, 0L)
        viewModel.load(id)
        setContent {
            ScreenshotSearcherTheme {
                DetailScreen(viewModel = viewModel, onBack = { finish() })
            }
        }
    }

    companion object {
        const val EXTRA_ID = "screenshot_id"
    }
}
