package com.mtgscanner.devtools

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Developer overlay settings with independent toggles for each overlay layer.
 * Persisted via SharedPreferences (survives app restart).
 * Each overlay can be enabled/disabled independently.
 *
 * Pipeline Status is disabled by default to avoid obscuring the camera.
 */
class DevSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("dev_overlay_settings", Context.MODE_PRIVATE)

    /** Show card detection bounding boxes (white/green/red rectangles). */
    var showCardDetection by mutableStateOf(prefs.getBoolean(KEY_CARD_DETECTION, false))
        private set

    /** Show OCR region rectangles (colored boxes on card). */
    var showOcrRegions by mutableStateOf(prefs.getBoolean(KEY_OCR_REGIONS, false))
        private set

    /** Show tracking IDs on detected cards. */
    var showTrackingIds by mutableStateOf(prefs.getBoolean(KEY_TRACKING_IDS, false))
        private set

    /** Show OCR recognized text beside regions. */
    var showOcrText by mutableStateOf(prefs.getBoolean(KEY_OCR_TEXT, false))
        private set

    /** Show pipeline status panel (disabled by default). */
    var showPipelineStatus by mutableStateOf(prefs.getBoolean(KEY_PIPELINE_STATUS, false))
        private set

    /** Show region labels (Name, Type, Collector, etc.). */
    var showRegionLabels by mutableStateOf(prefs.getBoolean(KEY_REGION_LABELS, false))
        private set

    /** Show cropped region bitmaps beside the overlay. */
    var showRegionCrops by mutableStateOf(prefs.getBoolean(KEY_REGION_CROPS, false))
        private set

    /** Show performance metrics (timing, FPS). */
    var showPerformance by mutableStateOf(prefs.getBoolean(KEY_PERFORMANCE, false))
        private set

    /** Show Scryfall lookup state (searching, found, cache, error). */
    var showScryfallLookup by mutableStateOf(prefs.getBoolean(KEY_SCRYFALL, false))
        private set

    /** Enable debug image capture (saves pipeline images to disk). */
    var debugCaptureEnabled by mutableStateOf(prefs.getBoolean(KEY_DEBUG_CAPTURE, false))
        private set

    /** Whether ANY overlay is currently enabled. */
    val anyOverlayEnabled: Boolean
        get() = showCardDetection || showOcrRegions || showTrackingIds ||
                showOcrText || showPipelineStatus || showRegionLabels ||
                showRegionCrops || showPerformance || showScryfallLookup

    // ═══ Toggle methods (update state + persist) ═══

    fun toggleCardDetection() { toggle(KEY_CARD_DETECTION) { showCardDetection = it } }
    fun toggleOcrRegions() { toggle(KEY_OCR_REGIONS) { showOcrRegions = it } }
    fun toggleTrackingIds() { toggle(KEY_TRACKING_IDS) { showTrackingIds = it } }
    fun toggleOcrText() { toggle(KEY_OCR_TEXT) { showOcrText = it } }
    fun togglePipelineStatus() { toggle(KEY_PIPELINE_STATUS) { showPipelineStatus = it } }
    fun toggleRegionLabels() { toggle(KEY_REGION_LABELS) { showRegionLabels = it } }
    fun toggleRegionCrops() { toggle(KEY_REGION_CROPS) { showRegionCrops = it } }
    fun togglePerformance() { toggle(KEY_PERFORMANCE) { showPerformance = it } }
    fun toggleScryfallLookup() { toggle(KEY_SCRYFALL) { showScryfallLookup = it } }
    fun toggleDebugCapture() { toggle(KEY_DEBUG_CAPTURE) { debugCaptureEnabled = it } }

    /** Enable all overlays at once. */
    fun enableAll() {
        setAll(true)
    }

    /** Disable all overlays at once. */
    fun disableAll() {
        setAll(false)
    }

    private fun toggle(key: String, setter: (Boolean) -> Unit) {
        val current = prefs.getBoolean(key, false)
        val newValue = !current
        prefs.edit().putBoolean(key, newValue).apply()
        setter(newValue)
    }

    private fun setAll(value: Boolean) {
        prefs.edit()
            .putBoolean(KEY_CARD_DETECTION, value)
            .putBoolean(KEY_OCR_REGIONS, value)
            .putBoolean(KEY_TRACKING_IDS, value)
            .putBoolean(KEY_OCR_TEXT, value)
            .putBoolean(KEY_PIPELINE_STATUS, value)
            .putBoolean(KEY_REGION_LABELS, value)
            .putBoolean(KEY_REGION_CROPS, value)
            .putBoolean(KEY_PERFORMANCE, value)
            .putBoolean(KEY_SCRYFALL, value)
            .apply()
        showCardDetection = value
        showOcrRegions = value
        showTrackingIds = value
        showOcrText = value
        showPipelineStatus = value
        showRegionLabels = value
        showRegionCrops = value
        showPerformance = value
        showScryfallLookup = value
    }

    companion object {
        private const val KEY_CARD_DETECTION = "overlay_card_detection"
        private const val KEY_OCR_REGIONS = "overlay_ocr_regions"
        private const val KEY_TRACKING_IDS = "overlay_tracking_ids"
        private const val KEY_OCR_TEXT = "overlay_ocr_text"
        private const val KEY_PIPELINE_STATUS = "overlay_pipeline_status"
        private const val KEY_REGION_LABELS = "overlay_region_labels"
        private const val KEY_REGION_CROPS = "overlay_region_crops"
        private const val KEY_PERFORMANCE = "overlay_performance"
        private const val KEY_SCRYFALL = "overlay_scryfall_lookup"
        private const val KEY_DEBUG_CAPTURE = "debug_capture_enabled"
    }
}
