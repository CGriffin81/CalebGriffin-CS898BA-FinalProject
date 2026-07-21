package com.mtgscanner.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

/**
 * ScannedCardDatabaseIntegrationTest — DISABLED.
 *
 * This test class was written against an earlier API surface:
 * - Uses `ScannedCard(name = ..., scannedAt = ...)` — correct params are `cardName`, `scannedTimestamp`
 * - Passes `ScannedCard` to `dao.insertCard()` which expects `ScannedCardEntity`
 * - Uses `Int` for card IDs where the schema uses `Long`
 *
 * All persistence behavior is now covered by [CardPersistenceTest] which uses the correct
 * entity types and constructor parameters.
 *
 * This file will be fully rewritten as part of P6 test suite repair.
 */
@RunWith(AndroidJUnit4::class)
@Ignore("Broken constructor params — replaced by CardPersistenceTest")
class ScannedCardDatabaseIntegrationTest {

    @Test
    fun placeholder() {
        // Exists so @Ignore on the class is reported correctly by the test runner.
    }
}
