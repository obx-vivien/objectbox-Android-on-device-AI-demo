package com.screenshotsearcher.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.screenshotsearcher.core.data.ScreenshotRepository
import com.screenshotsearcher.core.data.ScreenshotRepository.Companion.LABEL_DISPLAY_CONFIDENCE
import com.screenshotsearcher.core.data.ScreenshotRepository.Companion.SEMANTIC_MIN_SCORE
import com.screenshotsearcher.core.data.MetadataFilters
import com.screenshotsearcher.core.model.Screenshot
import com.screenshotsearcher.infra.objectbox.ObjectBoxStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SearchViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ScreenshotRepository(ObjectBoxStore.screenshotBox())

    private val _results = MutableStateFlow<List<SearchResult>>(emptyList())
    val results: StateFlow<List<SearchResult>> = _results

    private val _availableLabels = MutableStateFlow<List<String>>(emptyList())
    val availableLabels: StateFlow<List<String>> = _availableLabels

    private val _selectedLabels = MutableStateFlow<Set<String>>(emptySet())
    val selectedLabels: StateFlow<Set<String>> = _selectedLabels

    private val _metadataFilters = MutableStateFlow(MetadataFilters())
    val metadataFilters: StateFlow<MetadataFilters> = _metadataFilters

    private var lastQuery: String = ""

    init {
        search("")
    }

    fun search(query: String) {
        lastQuery = query
        val trimmed = query.trim()

        if (trimmed.isEmpty()) {
            val all = repository.searchByKeywordOcr("")
            val filtered = repository.filterByLabels(all, _selectedLabels.value)
            val metaFiltered = repository.filterByMetadata(filtered, _metadataFilters.value)
            _results.value = metaFiltered
                .sortedByDescending { it.createdAt }
                .map { screenshot ->
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
            _availableLabels.value = repository.availableLabels()
            return
        }

        val candidates = LinkedHashMap<Long, Screenshot>()
        val ocrSemanticScores = mutableMapOf<Long, Double>()
        val descriptionSemanticScores = mutableMapOf<Long, Double>()

        val semanticText = repository.searchBySemanticText(getApplication(), trimmed)
        val semanticDesc = repository.searchBySemanticDescription(getApplication(), trimmed)
        semanticText.forEach { result ->
            ocrSemanticScores[result.screenshot.id] = result.score
            candidates.putIfAbsent(result.screenshot.id, result.screenshot)
        }
        semanticDesc.forEach { result ->
            descriptionSemanticScores[result.screenshot.id] = result.score
            candidates.putIfAbsent(result.screenshot.id, result.screenshot)
        }

        val ocrMatches = repository.searchByKeywordOcr(trimmed)
        val descMatches = repository.searchByKeywordDescription(trimmed)
        ocrMatches.forEach { screenshot ->
            candidates.putIfAbsent(screenshot.id, screenshot)
        }
        descMatches.forEach { screenshot ->
            candidates.putIfAbsent(screenshot.id, screenshot)
        }

        val filtered = repository.filterByLabels(candidates.values.toList(), _selectedLabels.value)
        val metaFiltered = repository.filterByMetadata(filtered, _metadataFilters.value)
        val results = metaFiltered.mapNotNull { screenshot ->
            val ocrKeywordMatch = trimmed.isNotEmpty() && screenshot.ocrText.contains(trimmed, true)
            val descriptionKeywordMatch =
                trimmed.isNotEmpty() && (screenshot.description?.contains(trimmed, true) == true)
            val ocrScore = ocrSemanticScores[screenshot.id]
            val descriptionScore = descriptionSemanticScores[screenshot.id]
            val semanticScore = listOfNotNull(ocrScore, descriptionScore).let { scores ->
                if (scores.isEmpty()) {
                    null
                } else {
                    scores.average()
                }
            }
            val hasKeywordMatch = ocrKeywordMatch || descriptionKeywordMatch
            val keywordScore = if (hasKeywordMatch) 1.0 else 0.0
            val semanticPassed = (semanticScore ?: 0.0) >= SEMANTIC_MIN_SCORE
            val semanticScoreForRank = if (semanticPassed) semanticScore ?: 0.0 else 0.0
            val tagScore = if (trimmed.isNotEmpty()) {
                screenshot.labels
                    .filter { it.confidence >= LABEL_DISPLAY_CONFIDENCE && it.text.contains(trimmed, true) }
                    .maxOfOrNull { it.confidence.toDouble() } ?: 0.0
            } else {
                0.0
            }
            val tagPassed = tagScore > 0.0
            val include = hasKeywordMatch || semanticPassed || tagPassed
            if (!include) {
                return@mapNotNull null
            }

            val bucket = when {
                ocrKeywordMatch && descriptionKeywordMatch -> 0
                ocrKeywordMatch -> 1
                descriptionKeywordMatch -> 2
                else -> 3
            }
            val signalCount = listOf(
                hasKeywordMatch,
                semanticPassed,
                tagPassed
            ).count { it }
            val coverageBoost = when (signalCount) {
                3 -> 3.0
                2 -> 1.5
                else -> 0.0
            }
            val finalScore = coverageBoost +
                (0.8 * keywordScore) +
                (1.0 * semanticScoreForRank) +
                (0.6 * tagScore)

            SearchResult(
                screenshot = screenshot,
                ocrKeywordMatch = ocrKeywordMatch,
                descriptionKeywordMatch = descriptionKeywordMatch,
                ocrSemanticScore = ocrScore,
                descriptionSemanticScore = descriptionScore,
                rankBucket = bucket,
                finalScore = finalScore
            )
        }

        _results.value = results.sortedWith(
            compareByDescending<SearchResult> { it.finalScore }
                .thenByDescending { it.screenshot.createdAt }
        )
        _availableLabels.value = repository.availableLabels()
    }


    fun toggleLabel(label: String) {
        val current = _selectedLabels.value.toMutableSet()
        if (current.contains(label)) {
            current.remove(label)
        } else {
            current.add(label)
        }
        _selectedLabels.value = current
        search(lastQuery)
    }

    fun clearLabelFilters() {
        _selectedLabels.value = emptySet()
        search(lastQuery)
    }

    fun updateMetadataFilters(filters: MetadataFilters) {
        _metadataFilters.value = filters
        search(lastQuery)
    }

}

data class SearchResult(
    val screenshot: Screenshot,
    val ocrKeywordMatch: Boolean,
    val descriptionKeywordMatch: Boolean,
    val ocrSemanticScore: Double?,
    val descriptionSemanticScore: Double?,
    val rankBucket: Int,
    val finalScore: Double
)
