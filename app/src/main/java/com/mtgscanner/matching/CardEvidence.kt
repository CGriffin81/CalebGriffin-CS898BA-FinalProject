package com.mtgscanner.matching

import com.mtgscanner.anatomy.model.FrameType

/**
 * Evidence collected from OCR about a card's identity.
 *
 * Each field includes its own confidence score (0.0–1.0), reflecting how
 * certain the OCR reader was about that specific extraction. Fields with
 * empty values or zero confidence are treated as absent evidence, NOT as
 * disqualifying criteria.
 *
 * This model treats OCR output as probabilistic evidence rather than truth.
 * A missing collector number does not prevent card identification —
 * it simply provides less disambiguation power.
 *
 * @param name Detected card name and confidence
 * @param collectorNumber Detected collector number and confidence
 * @param setCode Detected set code and confidence
 * @param typeLine Detected type line and confidence
 * @param powerToughness Detected power/toughness and confidence
 * @param frameType Classified card frame type (provides context, not lookup criteria)
 */
data class CardEvidence(
    val name: EvidenceField,
    val collectorNumber: EvidenceField = EvidenceField.absent(),
    val setCode: EvidenceField = EvidenceField.absent(),
    val typeLine: EvidenceField = EvidenceField.absent(),
    val powerToughness: EvidenceField = EvidenceField.absent(),
    val frameType: FrameType = FrameType.UNKNOWN
) {
    /** Whether we have any usable evidence at all. */
    val hasAnyEvidence: Boolean
        get() = name.isPresent || collectorNumber.isPresent || setCode.isPresent

    /** Whether identity lookup (set + collector) is feasible. */
    val canDoIdentityLookup: Boolean
        get() = setCode.isPresent && setCode.confidence > 0.4f
            && collectorNumber.isPresent && collectorNumber.confidence > 0.4f

    /** Whether fuzzy name lookup is feasible. */
    val canDoNameLookup: Boolean
        get() = name.isPresent && name.confidence > 0.3f && name.value.length >= 3

    /** Debug summary. */
    override fun toString(): String = buildString {
        append("Evidence(")
        append("name='${name.value}'@${"%.0f".format(name.confidence * 100)}%")
        if (setCode.isPresent) append(", set='${setCode.value}'@${"%.0f".format(setCode.confidence * 100)}%")
        if (collectorNumber.isPresent) append(", cn='${collectorNumber.value}'@${"%.0f".format(collectorNumber.confidence * 100)}%")
        if (typeLine.isPresent) append(", type='${typeLine.value}'")
        if (powerToughness.isPresent) append(", pt='${powerToughness.value}'")
        append(", frame=$frameType)")
    }
}

/**
 * A single evidence field with its extracted value and confidence.
 *
 * @param value The extracted text (empty string = absent)
 * @param confidence Confidence in the extraction (0.0 = no confidence, 1.0 = certain)
 */
data class EvidenceField(
    val value: String,
    val confidence: Float
) {
    /** Whether this field has a non-empty value. */
    val isPresent: Boolean get() = value.isNotBlank()

    /** Whether this field is absent (empty or zero confidence). */
    val isAbsent: Boolean get() = value.isBlank() || confidence <= 0f

    companion object {
        /** Create an absent (missing) evidence field. */
        fun absent(): EvidenceField = EvidenceField(value = "", confidence = 0f)

        /** Create a present evidence field. */
        fun of(value: String, confidence: Float): EvidenceField =
            if (value.isBlank()) absent() else EvidenceField(value.trim(), confidence.coerceIn(0f, 1f))
    }
}
