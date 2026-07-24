package com.mtgscanner.anatomy

import com.mtgscanner.anatomy.model.FrameLayoutTemplate
import com.mtgscanner.anatomy.model.FrameType
import com.mtgscanner.anatomy.model.ProportionalRect

/**
 * Registry of frame layout templates for known MTG card frame types.
 *
 * Each template defines proportional coordinates (0.0–1.0) for all semantic regions.
 * Proportions are measured from standard MTG card dimensions (63mm × 88mm, ratio 1:1.397).
 *
 * New frame types can be added by calling [register] or by adding entries to [defaults].
 * The registry is designed to be extensible without modifying existing code.
 */
class FrameLayoutRegistry {

    private val templates = mutableMapOf<FrameType, FrameLayoutTemplate>()

    init {
        defaults.forEach { templates[it.frameType] = it }
    }

    /** Get the template for a frame type. Falls back to MODERN if not registered. */
    fun getTemplate(frameType: FrameType): FrameLayoutTemplate {
        return templates[frameType] ?: templates[FrameType.MODERN]!!
    }

    /** Register a custom template (for testing or future frame types). */
    fun register(template: FrameLayoutTemplate) {
        templates[template.frameType] = template
    }

    /** All registered frame types. */
    fun registeredTypes(): Set<FrameType> = templates.keys.toSet()

    companion object {
        /**
         * Default templates based on measured MTG card proportions.
         *
         * Measurements taken from standard card scans (2.5" × 3.5" cards scanned at 300dpi).
         * All values are normalized to 0.0–1.0 range.
         */
        val defaults: List<FrameLayoutTemplate> = listOf(
            // ═══ MODERN (M15+ frame, 2015–present) ═══
            // Most common frame in current circulation.
            FrameLayoutTemplate(
                frameType = FrameType.MODERN,
                nameBar = ProportionalRect(
                    top = 0.0f, bottom = 0.12f, left = 0.03f, right = 0.82f
                ),
                manaCost = ProportionalRect(
                    top = 0.0f, bottom = 0.12f, left = 0.82f, right = 0.97f
                ),
                artwork = ProportionalRect(
                    top = 0.10f, bottom = 0.50f, left = 0.03f, right = 0.97f
                ),
                typeLine = ProportionalRect(
                    top = 0.49f, bottom = 0.57f, left = 0.03f, right = 0.83f
                ),
                rulesText = ProportionalRect(
                    top = 0.57f, bottom = 0.88f, left = 0.03f, right = 0.97f
                ),
                setSymbol = ProportionalRect(
                    top = 0.49f, bottom = 0.57f, left = 0.83f, right = 0.97f
                ),
                collectorInfo = ProportionalRect(
                    top = 0.90f, bottom = 1.0f, left = 0.03f, right = 0.55f
                ),
                artistCredit = ProportionalRect(
                    top = 0.90f, bottom = 1.0f, left = 0.55f, right = 0.97f
                ),
                powerToughness = ProportionalRect(
                    top = 0.86f, bottom = 0.93f, left = 0.70f, right = 0.95f
                )
            ),

            // ═══ CLASSIC (8th Ed through M14, 2003–2014) ═══
            FrameLayoutTemplate(
                frameType = FrameType.CLASSIC,
                nameBar = ProportionalRect(
                    top = 0.04f, bottom = 0.08f, left = 0.06f, right = 0.80f
                ),
                manaCost = ProportionalRect(
                    top = 0.04f, bottom = 0.08f, left = 0.80f, right = 0.94f
                ),
                artwork = ProportionalRect(
                    top = 0.10f, bottom = 0.51f, left = 0.06f, right = 0.94f
                ),
                typeLine = ProportionalRect(
                    top = 0.52f, bottom = 0.56f, left = 0.06f, right = 0.85f
                ),
                rulesText = ProportionalRect(
                    top = 0.57f, bottom = 0.89f, left = 0.06f, right = 0.94f
                ),
                setSymbol = ProportionalRect(
                    top = 0.52f, bottom = 0.56f, left = 0.85f, right = 0.94f
                ),
                collectorInfo = ProportionalRect(
                    top = 0.95f, bottom = 0.98f, left = 0.06f, right = 0.50f
                ),
                artistCredit = ProportionalRect(
                    top = 0.95f, bottom = 0.98f, left = 0.50f, right = 0.94f
                ),
                powerToughness = ProportionalRect(
                    top = 0.89f, bottom = 0.94f, left = 0.70f, right = 0.93f
                )
            ),

            // ═══ FULL_ART (full-art lands, Unstable, etc.) ═══
            FrameLayoutTemplate(
                frameType = FrameType.FULL_ART,
                nameBar = ProportionalRect(
                    top = 0.04f, bottom = 0.08f, left = 0.05f, right = 0.82f
                ),
                manaCost = ProportionalRect(
                    top = 0.04f, bottom = 0.08f, left = 0.82f, right = 0.95f
                ),
                artwork = ProportionalRect(
                    top = 0.0f, bottom = 1.0f, left = 0.0f, right = 1.0f
                ),
                typeLine = ProportionalRect(
                    top = 0.85f, bottom = 0.89f, left = 0.05f, right = 0.95f
                ),
                rulesText = ProportionalRect(
                    top = 0.89f, bottom = 0.93f, left = 0.05f, right = 0.95f
                ),
                setSymbol = ProportionalRect(
                    top = 0.85f, bottom = 0.89f, left = 0.85f, right = 0.95f
                ),
                collectorInfo = ProportionalRect(
                    top = 0.95f, bottom = 0.98f, left = 0.05f, right = 0.50f
                ),
                artistCredit = ProportionalRect(
                    top = 0.95f, bottom = 0.98f, left = 0.50f, right = 0.95f
                ),
                powerToughness = null  // Lands typically don't have P/T
            ),

            // ═══ BORDERLESS (Showcase, Extended Art) ═══
            FrameLayoutTemplate(
                frameType = FrameType.BORDERLESS,
                nameBar = ProportionalRect(
                    top = 0.02f, bottom = 0.06f, left = 0.04f, right = 0.80f
                ),
                manaCost = ProportionalRect(
                    top = 0.02f, bottom = 0.06f, left = 0.80f, right = 0.96f
                ),
                artwork = ProportionalRect(
                    top = 0.0f, bottom = 1.0f, left = 0.0f, right = 1.0f
                ),
                typeLine = ProportionalRect(
                    top = 0.50f, bottom = 0.54f, left = 0.04f, right = 0.85f
                ),
                rulesText = ProportionalRect(
                    top = 0.54f, bottom = 0.88f, left = 0.04f, right = 0.96f
                ),
                setSymbol = ProportionalRect(
                    top = 0.50f, bottom = 0.54f, left = 0.85f, right = 0.96f
                ),
                collectorInfo = ProportionalRect(
                    top = 0.94f, bottom = 0.98f, left = 0.04f, right = 0.50f
                ),
                artistCredit = ProportionalRect(
                    top = 0.94f, bottom = 0.98f, left = 0.50f, right = 0.96f
                ),
                powerToughness = ProportionalRect(
                    top = 0.88f, bottom = 0.93f, left = 0.72f, right = 0.94f
                )
            ),

            // ═══ UNKNOWN (fallback — uses MODERN proportions) ═══
            FrameLayoutTemplate(
                frameType = FrameType.UNKNOWN,
                nameBar = ProportionalRect(
                    top = 0.0f, bottom = 0.12f, left = 0.03f, right = 0.82f
                ),
                manaCost = ProportionalRect(
                    top = 0.0f, bottom = 0.12f, left = 0.82f, right = 0.97f
                ),
                artwork = ProportionalRect(
                    top = 0.10f, bottom = 0.50f, left = 0.03f, right = 0.97f
                ),
                typeLine = ProportionalRect(
                    top = 0.49f, bottom = 0.57f, left = 0.03f, right = 0.83f
                ),
                rulesText = ProportionalRect(
                    top = 0.57f, bottom = 0.88f, left = 0.03f, right = 0.97f
                ),
                setSymbol = ProportionalRect(
                    top = 0.49f, bottom = 0.57f, left = 0.83f, right = 0.97f
                ),
                collectorInfo = ProportionalRect(
                    top = 0.90f, bottom = 1.0f, left = 0.03f, right = 0.55f
                ),
                artistCredit = ProportionalRect(
                    top = 0.90f, bottom = 1.0f, left = 0.55f, right = 0.97f
                ),
                powerToughness = ProportionalRect(
                    top = 0.86f, bottom = 0.93f, left = 0.70f, right = 0.95f
                )
            )
        )
    }
}
