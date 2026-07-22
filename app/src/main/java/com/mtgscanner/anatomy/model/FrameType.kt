package com.mtgscanner.anatomy.model

/**
 * Known MTG card frame types. Each frame type has different region proportions.
 * New frame types can be added by creating a corresponding [FrameLayoutTemplate].
 */
enum class FrameType {
    /** Post-2015 standard frames (Magic 2015 onwards). Most common in circulation. */
    MODERN,

    /** Pre-2015 frames (8th Edition through Magic 2014). */
    CLASSIC,

    /** Full-art cards (Zendikar lands, Unstable, etc.) — art extends edge-to-edge. */
    FULL_ART,

    /** Borderless showcase and extended-art treatments. */
    BORDERLESS,

    /** Planeswalker cards — unique loyalty box and ability layout. */
    PLANESWALKER,

    /** Saga enchantments — vertical chapter layout. */
    SAGA,

    /** Split/Aftermath cards — two half-cards on one. */
    SPLIT,

    /** Default fallback when frame type cannot be determined. Uses MODERN proportions. */
    UNKNOWN
}
