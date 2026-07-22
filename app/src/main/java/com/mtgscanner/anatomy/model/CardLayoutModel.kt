package com.mtgscanner.anatomy.model

import android.graphics.Bitmap
import android.graphics.Rect
import org.json.JSONArray
import org.json.JSONObject

/**
 * Geometry-only model describing the spatial layout of a Magic: The Gathering card.
 *
 * This model represents WHERE each semantic region is located on the card.
 * It does NOT contain OCR results or text content — only geometry.
 *
 * Each region includes:
 * - [BoundingRect]: Pixel-coordinate rectangle within the source image
 * - [Float] confidence: 0.0–1.0 detection certainty
 * - [Bitmap?] bitmap: Cropped region image (nullable, set after extraction)
 *
 * Supports JSON serialization for debugging and logging.
 *
 * @param frameType Classified card frame type used for layout detection
 * @param confidence Overall layout detection confidence (0.0–1.0)
 * @param imageWidth Width of the source card image in pixels
 * @param imageHeight Height of the source card image in pixels
 * @param nameRegion Card name bar (top-left area with title text)
 * @param manaRegion Mana cost symbols (top-right area)
 * @param artRegion Card artwork (large central illustration)
 * @param typeRegion Type line (middle divider: "Creature — Demon")
 * @param rulesRegion Rules text box (ability text area)
 * @param collectorRegion Collector information (bottom: set code, number, rarity)
 * @param artistRegion Artist credit (bottom area)
 * @param powerToughnessRegion Power/Toughness box (bottom-right, null for non-creatures)
 */
data class CardLayoutModel(
    val frameType: FrameType,
    val confidence: Float,
    val imageWidth: Int,
    val imageHeight: Int,
    val nameRegion: LayoutRegion,
    val manaRegion: LayoutRegion,
    val artRegion: LayoutRegion,
    val typeRegion: LayoutRegion,
    val rulesRegion: LayoutRegion,
    val collectorRegion: LayoutRegion,
    val artistRegion: LayoutRegion,
    val powerToughnessRegion: LayoutRegion?
) {
    /** All non-null regions as a list for iteration. */
    val allRegions: List<LayoutRegion>
        get() = listOfNotNull(
            nameRegion, manaRegion, artRegion, typeRegion,
            rulesRegion, collectorRegion, artistRegion, powerToughnessRegion
        )

    /** Serialize to JSON for debug logging. Bitmaps are NOT serialized. */
    fun toJson(): JSONObject = JSONObject().apply {
        put("frameType", frameType.name)
        put("confidence", confidence.toDouble())
        put("imageWidth", imageWidth)
        put("imageHeight", imageHeight)
        put("regions", JSONObject().apply {
            put("name", nameRegion.toJson())
            put("mana", manaRegion.toJson())
            put("art", artRegion.toJson())
            put("type", typeRegion.toJson())
            put("rules", rulesRegion.toJson())
            put("collector", collectorRegion.toJson())
            put("artist", artistRegion.toJson())
            powerToughnessRegion?.let { put("powerToughness", it.toJson()) }
        })
    }

    /** Human-readable debug summary. */
    fun toDebugString(): String = buildString {
        appendLine("CardLayoutModel(frame=$frameType, conf=${"%.2f".format(confidence)}, ${imageWidth}x${imageHeight})")
        appendLine("  name:      ${nameRegion.toShortString()}")
        appendLine("  mana:      ${manaRegion.toShortString()}")
        appendLine("  art:       ${artRegion.toShortString()}")
        appendLine("  type:      ${typeRegion.toShortString()}")
        appendLine("  rules:     ${rulesRegion.toShortString()}")
        appendLine("  collector: ${collectorRegion.toShortString()}")
        appendLine("  artist:    ${artistRegion.toShortString()}")
        powerToughnessRegion?.let { appendLine("  p/t:       ${it.toShortString()}") }
    }

    companion object {
        /** Deserialize from JSON (for test fixtures). Bitmaps will be null. */
        fun fromJson(json: JSONObject): CardLayoutModel {
            val regions = json.getJSONObject("regions")
            return CardLayoutModel(
                frameType = FrameType.valueOf(json.getString("frameType")),
                confidence = json.getDouble("confidence").toFloat(),
                imageWidth = json.getInt("imageWidth"),
                imageHeight = json.getInt("imageHeight"),
                nameRegion = LayoutRegion.fromJson(regions.getJSONObject("name")),
                manaRegion = LayoutRegion.fromJson(regions.getJSONObject("mana")),
                artRegion = LayoutRegion.fromJson(regions.getJSONObject("art")),
                typeRegion = LayoutRegion.fromJson(regions.getJSONObject("type")),
                rulesRegion = LayoutRegion.fromJson(regions.getJSONObject("rules")),
                collectorRegion = LayoutRegion.fromJson(regions.getJSONObject("collector")),
                artistRegion = LayoutRegion.fromJson(regions.getJSONObject("artist")),
                powerToughnessRegion = if (regions.has("powerToughness"))
                    LayoutRegion.fromJson(regions.getJSONObject("powerToughness")) else null
            )
        }
    }
}

/**
 * A single semantic region on the card — geometry only.
 *
 * Represents the bounding rectangle, confidence, and optional cropped bitmap
 * for one semantic area of the card (name bar, type line, collector info, etc.).
 *
 * The [bitmap] field is populated after region extraction and is NOT serialized.
 * It holds the cropped image content for that region, ready for OCR processing.
 *
 * @param bounds Pixel-coordinate bounding rectangle within the source card image
 * @param confidence Detection confidence for this region (0.0–1.0)
 * @param bitmap Cropped bitmap of this region (null until extracted, not serialized)
 */
data class LayoutRegion(
    val bounds: BoundingRect,
    val confidence: Float,
    val bitmap: Bitmap? = null
) {
    /** Width in pixels. */
    val width: Int get() = bounds.width

    /** Height in pixels. */
    val height: Int get() = bounds.height

    /** Whether a bitmap has been extracted for this region. */
    val hasBitmap: Boolean get() = bitmap != null && bitmap.isRecycled.not()

    /** Serialize to JSON (bitmap excluded). */
    fun toJson(): JSONObject = JSONObject().apply {
        put("bounds", bounds.toJson())
        put("confidence", confidence.toDouble())
        put("hasBitmap", hasBitmap)
    }

    /** Short debug string. */
    fun toShortString(): String =
        "[${bounds.left},${bounds.top} ${bounds.width}x${bounds.height}] conf=${"%.2f".format(confidence)} bmp=${hasBitmap}"

    companion object {
        fun fromJson(json: JSONObject): LayoutRegion = LayoutRegion(
            bounds = BoundingRect.fromJson(json.getJSONObject("bounds")),
            confidence = json.getDouble("confidence").toFloat(),
            bitmap = null
        )
    }
}

/**
 * Pixel-coordinate bounding rectangle.
 *
 * Uses explicit named fields rather than Android's [Rect] for cleaner serialization
 * and to avoid mutable state. Immutable value type.
 *
 * @param left X coordinate of the left edge
 * @param top Y coordinate of the top edge
 * @param width Width in pixels
 * @param height Height in pixels
 */
data class BoundingRect(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int
) {
    /** Right edge X coordinate. */
    val right: Int get() = left + width

    /** Bottom edge Y coordinate. */
    val bottom: Int get() = top + height

    /** Center X coordinate. */
    val centerX: Int get() = left + width / 2

    /** Center Y coordinate. */
    val centerY: Int get() = top + height / 2

    /** Area in pixels. */
    val area: Int get() = width * height

    /** Whether this rect has valid non-zero dimensions. */
    val isValid: Boolean get() = width > 0 && height > 0

    /** Convert to Android Rect. */
    fun toAndroidRect(): Rect = Rect(left, top, right, bottom)

    /** Serialize to JSON. */
    fun toJson(): JSONObject = JSONObject().apply {
        put("left", left)
        put("top", top)
        put("width", width)
        put("height", height)
    }

    /** Short debug string. */
    override fun toString(): String = "($left,$top ${width}x$height)"

    companion object {
        /** Create from Android Rect. */
        fun fromAndroidRect(rect: Rect): BoundingRect = BoundingRect(
            left = rect.left,
            top = rect.top,
            width = rect.width(),
            height = rect.height()
        )

        /** Deserialize from JSON. */
        fun fromJson(json: JSONObject): BoundingRect = BoundingRect(
            left = json.getInt("left"),
            top = json.getInt("top"),
            width = json.getInt("width"),
            height = json.getInt("height")
        )

        /** Create from proportional coordinates (0.0–1.0) given image dimensions. */
        fun fromProportional(
            leftFraction: Float,
            topFraction: Float,
            rightFraction: Float,
            bottomFraction: Float,
            imageWidth: Int,
            imageHeight: Int
        ): BoundingRect {
            val l = (leftFraction * imageWidth).toInt().coerceIn(0, imageWidth - 1)
            val t = (topFraction * imageHeight).toInt().coerceIn(0, imageHeight - 1)
            val r = (rightFraction * imageWidth).toInt().coerceIn(l + 1, imageWidth)
            val b = (bottomFraction * imageHeight).toInt().coerceIn(t + 1, imageHeight)
            return BoundingRect(left = l, top = t, width = r - l, height = b - t)
        }
    }
}
