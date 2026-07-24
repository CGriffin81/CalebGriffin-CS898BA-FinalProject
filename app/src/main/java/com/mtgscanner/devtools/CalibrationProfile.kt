package com.mtgscanner.devtools

import android.content.Context
import android.util.Log
import com.mtgscanner.anatomy.model.FrameType
import com.mtgscanner.anatomy.model.ProportionalRect
import org.json.JSONObject

/**
 * A calibration profile stores normalized (0–1) region coordinates for one frame layout.
 * Regions are draggable/resizable in the calibration tool, then persisted as JSON.
 */
data class CalibrationRegion(
    val name: String,
    var left: Float,
    var top: Float,
    var right: Float,
    var bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun toProportionalRect() = ProportionalRect(top, bottom, left, right)

    fun toJson() = JSONObject().apply {
        put("left", left.toDouble())
        put("top", top.toDouble())
        put("right", right.toDouble())
        put("bottom", bottom.toDouble())
    }

    companion object {
        fun fromJson(name: String, json: JSONObject) = CalibrationRegion(
            name = name,
            left = json.getDouble("left").toFloat(),
            top = json.getDouble("top").toFloat(),
            right = json.getDouble("right").toFloat(),
            bottom = json.getDouble("bottom").toFloat()
        )
    }
}

/**
 * Complete calibration profile for one frame type.
 * Contains all 9 semantic card regions as normalized coordinates.
 */
data class CalibrationProfile(
    val frameType: String,
    val regions: MutableList<CalibrationRegion>
) {
    fun findRegion(name: String): CalibrationRegion? = regions.find { it.name == name }

    fun toJson() = JSONObject().apply {
        put("frameType", frameType)
        val regionsObj = JSONObject()
        regions.forEach { regionsObj.put(it.name, it.toJson()) }
        put("regions", regionsObj)
    }

    companion object {
        val REGION_NAMES = listOf(
            "Name", "ManaCost", "TypeLine", "RulesText",
            "CollectorInfo", "SetSymbol", "Artist", "PowerToughness"
        )

        fun fromJson(json: JSONObject): CalibrationProfile {
            val frameType = json.getString("frameType")
            val regionsObj = json.getJSONObject("regions")
            val regions = mutableListOf<CalibrationRegion>()
            for (name in REGION_NAMES) {
                if (regionsObj.has(name)) {
                    regions.add(CalibrationRegion.fromJson(name, regionsObj.getJSONObject(name)))
                }
            }
            return CalibrationProfile(frameType, regions)
        }

        /** Default Modern frame profile based on MTG_FRAME_SPECIFICATION. */
        fun defaultModern() = CalibrationProfile(
            frameType = "MODERN",
            regions = mutableListOf(
                CalibrationRegion("Name",           0.05f, 0.035f, 0.80f, 0.085f),
                CalibrationRegion("ManaCost",       0.80f, 0.035f, 0.95f, 0.085f),
                CalibrationRegion("TypeLine",       0.05f, 0.50f,  0.82f, 0.55f),
                CalibrationRegion("RulesText",      0.05f, 0.56f,  0.95f, 0.87f),
                CalibrationRegion("CollectorInfo",  0.05f, 0.93f,  0.50f, 0.97f),
                CalibrationRegion("SetSymbol",      0.82f, 0.50f,  0.95f, 0.55f),
                CalibrationRegion("Artist",         0.50f, 0.93f,  0.95f, 0.97f),
                CalibrationRegion("PowerToughness", 0.72f, 0.88f,  0.93f, 0.93f)
            )
        )

        fun defaultForType(type: String): CalibrationProfile = when (type) {
            "MODERN" -> defaultModern()
            "LEGACY" -> CalibrationProfile("LEGACY", mutableListOf(
                CalibrationRegion("Name",           0.06f, 0.04f,  0.78f, 0.09f),
                CalibrationRegion("ManaCost",       0.78f, 0.04f,  0.94f, 0.09f),
                CalibrationRegion("TypeLine",       0.06f, 0.52f,  0.82f, 0.57f),
                CalibrationRegion("RulesText",      0.06f, 0.58f,  0.94f, 0.89f),
                CalibrationRegion("CollectorInfo",  0.06f, 0.95f,  0.50f, 0.98f),
                CalibrationRegion("SetSymbol",      0.82f, 0.52f,  0.94f, 0.57f),
                CalibrationRegion("Artist",         0.50f, 0.95f,  0.94f, 0.98f),
                CalibrationRegion("PowerToughness", 0.70f, 0.89f,  0.93f, 0.94f)
            ))
            "OLD_FRAME" -> CalibrationProfile("OLD_FRAME", mutableListOf(
                CalibrationRegion("Name",           0.07f, 0.03f,  0.75f, 0.08f),
                CalibrationRegion("ManaCost",       0.75f, 0.03f,  0.93f, 0.08f),
                CalibrationRegion("TypeLine",       0.07f, 0.51f,  0.93f, 0.56f),
                CalibrationRegion("RulesText",      0.07f, 0.57f,  0.93f, 0.90f),
                CalibrationRegion("CollectorInfo",  0.07f, 0.96f,  0.93f, 0.99f),
                CalibrationRegion("SetSymbol",      0.80f, 0.51f,  0.93f, 0.56f),
                CalibrationRegion("Artist",         0.07f, 0.96f,  0.93f, 0.99f),
                CalibrationRegion("PowerToughness", 0.68f, 0.90f,  0.93f, 0.96f)
            ))
            "UNIVERSES_BEYOND" -> defaultModern().copy(frameType = "UNIVERSES_BEYOND")
            "DFC" -> CalibrationProfile("DFC", mutableListOf(
                CalibrationRegion("Name",           0.05f, 0.035f, 0.80f, 0.085f),
                CalibrationRegion("ManaCost",       0.80f, 0.035f, 0.95f, 0.085f),
                CalibrationRegion("TypeLine",       0.05f, 0.50f,  0.82f, 0.55f),
                CalibrationRegion("RulesText",      0.05f, 0.56f,  0.95f, 0.87f),
                CalibrationRegion("CollectorInfo",  0.05f, 0.93f,  0.50f, 0.97f),
                CalibrationRegion("SetSymbol",      0.82f, 0.50f,  0.95f, 0.55f),
                CalibrationRegion("Artist",         0.50f, 0.93f,  0.95f, 0.97f),
                CalibrationRegion("PowerToughness", 0.72f, 0.88f,  0.93f, 0.93f)
            ))
            "ADVENTURE" -> CalibrationProfile("ADVENTURE", mutableListOf(
                CalibrationRegion("Name",           0.05f, 0.035f, 0.80f, 0.085f),
                CalibrationRegion("ManaCost",       0.80f, 0.035f, 0.95f, 0.085f),
                CalibrationRegion("TypeLine",       0.05f, 0.50f,  0.82f, 0.55f),
                CalibrationRegion("RulesText",      0.55f, 0.56f,  0.95f, 0.87f),
                CalibrationRegion("CollectorInfo",  0.05f, 0.93f,  0.50f, 0.97f),
                CalibrationRegion("SetSymbol",      0.82f, 0.50f,  0.95f, 0.55f),
                CalibrationRegion("Artist",         0.50f, 0.93f,  0.95f, 0.97f),
                CalibrationRegion("PowerToughness", 0.72f, 0.88f,  0.93f, 0.93f)
            ))
            "SAGA" -> CalibrationProfile("SAGA", mutableListOf(
                CalibrationRegion("Name",           0.05f, 0.03f,  0.60f, 0.08f),
                CalibrationRegion("ManaCost",       0.60f, 0.03f,  0.68f, 0.08f),
                CalibrationRegion("TypeLine",       0.05f, 0.08f,  0.55f, 0.12f),
                CalibrationRegion("RulesText",      0.05f, 0.12f,  0.55f, 0.92f),
                CalibrationRegion("CollectorInfo",  0.05f, 0.93f,  0.50f, 0.97f),
                CalibrationRegion("SetSymbol",      0.45f, 0.08f,  0.55f, 0.12f),
                CalibrationRegion("Artist",         0.50f, 0.93f,  0.95f, 0.97f),
                CalibrationRegion("PowerToughness", 0.72f, 0.88f,  0.93f, 0.93f)
            ))
            "BATTLE" -> CalibrationProfile("BATTLE", mutableListOf(
                CalibrationRegion("Name",           0.05f, 0.03f,  0.60f, 0.08f),
                CalibrationRegion("ManaCost",       0.60f, 0.03f,  0.68f, 0.08f),
                CalibrationRegion("TypeLine",       0.05f, 0.50f,  0.95f, 0.55f),
                CalibrationRegion("RulesText",      0.05f, 0.55f,  0.95f, 0.87f),
                CalibrationRegion("CollectorInfo",  0.05f, 0.93f,  0.50f, 0.97f),
                CalibrationRegion("SetSymbol",      0.85f, 0.50f,  0.95f, 0.55f),
                CalibrationRegion("Artist",         0.50f, 0.93f,  0.95f, 0.97f),
                CalibrationRegion("PowerToughness", 0.72f, 0.87f,  0.93f, 0.925f)
            ))
            "TOKEN" -> CalibrationProfile("TOKEN", mutableListOf(
                CalibrationRegion("Name",           0.05f, 0.03f,  0.95f, 0.07f),
                CalibrationRegion("ManaCost",       0.85f, 0.03f,  0.95f, 0.07f),
                CalibrationRegion("TypeLine",       0.05f, 0.75f,  0.95f, 0.80f),
                CalibrationRegion("RulesText",      0.05f, 0.80f,  0.95f, 0.90f),
                CalibrationRegion("CollectorInfo",  0.05f, 0.96f,  0.95f, 0.99f),
                CalibrationRegion("SetSymbol",      0.85f, 0.75f,  0.95f, 0.80f),
                CalibrationRegion("Artist",         0.05f, 0.96f,  0.95f, 0.99f),
                CalibrationRegion("PowerToughness", 0.72f, 0.90f,  0.93f, 0.96f)
            ))
            else -> defaultModern().copy(frameType = type)
        }
    }
}

/**
 * Manages calibration profile persistence using SharedPreferences.
 */
class CalibrationStore(context: Context) {
    private val prefs = context.getSharedPreferences("calibration_profiles", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "CalibrationStore"
    }

    fun save(profile: CalibrationProfile) {
        val key = "profile_${profile.frameType}"
        prefs.edit().putString(key, profile.toJson().toString()).apply()
        Log.d(TAG, "Saved profile: ${profile.frameType}")
    }

    fun load(frameType: String): CalibrationProfile? {
        val key = "profile_$frameType"
        val json = prefs.getString(key, null) ?: return null
        return try {
            CalibrationProfile.fromJson(JSONObject(json))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load profile $frameType: ${e.message}")
            null
        }
    }

    fun loadOrDefault(frameType: String): CalibrationProfile {
        return load(frameType) ?: CalibrationProfile.defaultForType(frameType)
    }

    fun reset(frameType: String) {
        prefs.edit().remove("profile_$frameType").apply()
        Log.d(TAG, "Reset profile: $frameType")
    }

    fun exportAll(): String {
        val all = JSONObject()
        listOf("MODERN", "LEGACY", "DFC", "ADVENTURE", "SAGA", "BATTLE", "TOKEN").forEach { type ->
            load(type)?.let { all.put(type, it.toJson()) }
        }
        return all.toString(2)
    }

    fun importAll(json: String) {
        val all = JSONObject(json)
        for (key in all.keys()) {
            val profile = CalibrationProfile.fromJson(all.getJSONObject(key))
            save(profile)
        }
        Log.d(TAG, "Imported ${all.length()} profiles")
    }
}
