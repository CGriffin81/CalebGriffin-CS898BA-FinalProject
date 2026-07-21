package com.mtgscanner

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

/**
 * EndToEndIntegrationTest — DISABLED.
 *
 * This test class was written against an earlier API surface that no longer exists:
 * - Uses `ScannedCard(name = ..., scannedAt = ...)` — correct params are `cardName`, `scannedTimestamp`
 * - Passes `ScannedCard` to `dao.insertCard()` which expects `ScannedCardEntity`
 * - References `DetectedCardText` constructor with positional args that don't match current signature
 *
 * Replaced by [com.mtgscanner.data.CardPersistenceTest] which tests the same persistence flows
 * using the correct domain model and entity types.
 *
 * This file will be deleted as part of P6 test suite repair.
 */
@RunWith(AndroidJUnit4::class)
@Ignore("Broken constructor params and type mismatches — replaced by CardPersistenceTest")
class EndToEndIntegrationTest {

    @Test
    fun placeholder() {
        // Exists only so the @Ignore on the class takes effect and Gradle reports this test as skipped.
    }
}
