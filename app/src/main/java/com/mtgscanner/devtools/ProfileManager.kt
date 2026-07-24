package com.mtgscanner.devtools

import android.content.Context
import android.util.Log
import org.json.JSONObject

/**
 * Full calibration profile manager with CRUD operations.
 *
 * Supports: Save, Load, Duplicate, Delete, Export JSON, Import JSON, Reset to Defaults.
 * Stores ONLY normalized 0–1 coordinates. No pixel values, no bitmaps.
 *
 * Frame types supported (with defaults):
 * - MODERN (M15+ standard frame, 2015–present)
 * - OLD_FRAME (Pre-2003 classic frame, Alpha through 7th Edition)
 * - UNIVERSES_BEYOND (Universes Beyond / Secret Lair treatments)
 * - DFC (Double-Faced Cards)
 * - ADVENTURE (Adventure cards with sub-card)
 * - SAGA (Vertical chapter layout)
 * - BATTLE (Battle cards with defense)
 * - TOKEN (Token cards with large art)
 */
class ProfileManager(private val context: Context) {

    private val store = CalibrationStore(context)

    companion object {
        private const val TAG = "ProfileManager"

        /** All supported frame types with display names. */
        val FRAME_TYPES = listOf(
            FrameTypeInfo("MODERN", "Modern (M15+)"),
            FrameTypeInfo("OLD_FRAME", "Old Frame (pre-8th)"),
            FrameTypeInfo("LEGACY", "Legacy (8th–M14)"),
            FrameTypeInfo("UNIVERSES_BEYOND", "Universes Beyond"),
            FrameTypeInfo("DFC", "Double-Faced"),
            FrameTypeInfo("ADVENTURE", "Adventure"),
            FrameTypeInfo("SAGA", "Saga"),
            FrameTypeInfo("BATTLE", "Battle"),
            FrameTypeInfo("TOKEN", "Token")
        )
    }

    // ═══ CRUD Operations ═══

    /** Save a profile (creates or updates). */
    fun save(profile: CalibrationProfile) {
        store.save(profile)
        Log.d(TAG, "Saved: ${profile.frameType} with ${profile.regions.size} regions")
    }

    /** Load a profile by frame type. Returns null if not saved. */
    fun load(frameType: String): CalibrationProfile? = store.load(frameType)

    /** Load a profile, falling back to built-in defaults if not saved. */
    fun loadOrDefault(frameType: String): CalibrationProfile = store.loadOrDefault(frameType)

    /** Duplicate an existing profile under a new frame type name. */
    fun duplicate(sourceType: String, targetType: String): CalibrationProfile {
        val source = loadOrDefault(sourceType)
        val duplicated = source.copy(
            frameType = targetType,
            regions = source.regions.map { it.copy() }.toMutableList()
        )
        save(duplicated)
        Log.d(TAG, "Duplicated: $sourceType → $targetType")
        return duplicated
    }

    /** Delete a saved profile (reverts to defaults on next load). */
    fun delete(frameType: String) {
        store.reset(frameType)
        Log.d(TAG, "Deleted: $frameType")
    }

    /** Reset a profile to its built-in defaults. */
    fun resetToDefaults(frameType: String): CalibrationProfile {
        store.reset(frameType)
        val defaults = CalibrationProfile.defaultForType(frameType)
        Log.d(TAG, "Reset to defaults: $frameType")
        return defaults
    }

    /** List all frame types that have saved (non-default) profiles. */
    fun listSavedProfiles(): List<String> {
        return FRAME_TYPES.map { it.id }.filter { store.load(it) != null }
    }

    /** Check if a profile has been customized (differs from defaults). */
    fun isCustomized(frameType: String): Boolean = store.load(frameType) != null

    // ═══ Export / Import ═══

    /** Export all saved profiles as a pretty-printed JSON string. */
    fun exportAllAsJson(): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())

        val profiles = JSONObject()
        FRAME_TYPES.forEach { typeInfo ->
            store.load(typeInfo.id)?.let { profile ->
                profiles.put(typeInfo.id, profile.toJson())
            }
        }
        root.put("profiles", profiles)

        val json = root.toString(2)
        Log.d(TAG, "Exported ${profiles.length()} profiles (${json.length} chars)")
        return json
    }

    /** Import profiles from a JSON string. Existing profiles are overwritten. */
    fun importFromJson(json: String): Int {
        return try {
            val root = JSONObject(json)
            val profiles = if (root.has("profiles")) root.getJSONObject("profiles") else root

            var count = 0
            for (key in profiles.keys()) {
                val profileJson = profiles.getJSONObject(key)
                val profile = CalibrationProfile.fromJson(profileJson)
                save(profile)
                count++
            }
            Log.d(TAG, "Imported $count profiles")
            count
        } catch (e: Exception) {
            Log.e(TAG, "Import failed: ${e.message}", e)
            0
        }
    }

    /** Export a single profile as JSON. */
    fun exportProfile(frameType: String): String? {
        val profile = store.load(frameType) ?: return null
        return profile.toJson().toString(2)
    }
}

/** Frame type descriptor with ID and human-readable display name. */
data class FrameTypeInfo(
    val id: String,
    val displayName: String
)
