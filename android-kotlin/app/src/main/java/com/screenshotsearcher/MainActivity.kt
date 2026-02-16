package com.screenshotsearcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.screenshotsearcher.ui.home.HomeScreen
import com.screenshotsearcher.ui.home.HomeViewModel
import com.screenshotsearcher.ui.theme.ScreenshotSearcherTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ScreenshotSearcherTheme {
                val vm: HomeViewModel = viewModel()
                HomeScreen(viewModel = vm)
            }
        }
    }
}
