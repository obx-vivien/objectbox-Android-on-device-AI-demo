package com.screenshotsearcher.ui.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.screenshotsearcher.ui.theme.ScreenshotSearcherTheme

class SettingsActivity : ComponentActivity() {
    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ScreenshotSearcherTheme {
                SettingsScreen(viewModel = viewModel, onBack = { finish() })
            }
        }
    }
}
