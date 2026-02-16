package com.screenshotsearcher.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.screenshotsearcher.core.data.ScreenshotRepository
import android.net.Uri
import com.screenshotsearcher.core.data.ImageResult
import com.screenshotsearcher.core.data.SemanticResult
import com.screenshotsearcher.core.model.Screenshot
import com.screenshotsearcher.infra.objectbox.ObjectBoxStore
import com.screenshotsearcher.infra.image.ImageDecoder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SearchViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ScreenshotRepository(ObjectBoxStore.screenshotBox())

    private val _results = MutableStateFlow<List<SearchResult>>(emptyList())
    val results: StateFlow<List<SearchResult>> = _results

    init {
        search("", SearchMode.KEYWORD)
    }

    fun search(query: String, mode: SearchMode) {
        _results.value = when (mode) {
            SearchMode.KEYWORD -> repository.searchByKeyword(query).map { it.toKeywordResult() }
            SearchMode.SEMANTIC -> repository.searchBySemantic(getApplication(), query).map { it.toSemanticResult() }
            SearchMode.SIMILAR_IMAGE -> emptyList()
        }
    }

    fun searchByImage(uri: Uri) {
        val bitmap = ImageDecoder.decodeNormalized(getApplication(), uri) ?: run {
            _results.value = emptyList()
            return
        }
        val results = repository.searchByImageSimilarity(getApplication(), bitmap)
        _results.value = results.map { it.toImageResult() }
    }

    private fun Screenshot.toKeywordResult(): SearchResult {
        return SearchResult(this, null)
    }

    private fun SemanticResult.toSemanticResult(): SearchResult {
        return SearchResult(screenshot, score)
    }

    private fun ImageResult.toImageResult(): SearchResult {
        return SearchResult(screenshot, score)
    }
}

data class SearchResult(val screenshot: Screenshot, val score: Double?)

enum class SearchMode {
    KEYWORD,
    SEMANTIC,
    SIMILAR_IMAGE
}
