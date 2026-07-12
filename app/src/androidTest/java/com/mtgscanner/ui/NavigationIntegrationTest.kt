package com.mtgscanner.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import com.mtgscanner.model.*

/**
 * NavigationIntegrationTest: Tests UI navigation and screen transitions.
 * Validates: AppNavigator state machine, screen routing, data flow between screens.
 *
 * Uses Jetpack Compose Testing (ComposeTestRule).
 */
@RunWith(AndroidJUnit4::class)
class NavigationIntegrationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var navigator: AppNavigator

    @Before
    fun setUp() {
        navigator = AppNavigator()
    }

    /**
     * Test: Navigate from main screen to camera screen.
     */
    @Test
    fun testNavigationMainToCamera() {
        assertNotEquals("Initial screen should not be CAMERA", AppScreen.CAMERA, navigator.currentScreen)

        navigator.navigateToCamera()

        assertEquals("Should navigate to CAMERA screen", AppScreen.CAMERA, navigator.currentScreen)
    }

    /**
     * Test: Navigate from camera screen back to main.
     */
    @Test
    fun testNavigationCameraToMain() {
        navigator.navigateToCamera()
        assertEquals("Should be on CAMERA screen", AppScreen.CAMERA, navigator.currentScreen)

        navigator.returnToMain()

        assertEquals("Should return to MAIN screen", AppScreen.MAIN, navigator.currentScreen)
    }

    /**
     * Test: Navigate from main to collection screen.
     */
    @Test
    fun testNavigationMainToCollection() {
        assertEquals("Initial screen should be MAIN", AppScreen.MAIN, navigator.currentScreen)

        navigator.navigateToCollection()

        assertEquals("Should navigate to COLLECTION screen", AppScreen.COLLECTION, navigator.currentScreen)
    }

    /**
     * Test: Navigate from collection back to main.
     */
    @Test
    fun testNavigationCollectionToMain() {
        navigator.navigateToCollection()
        assertEquals("Should be on COLLECTION screen", AppScreen.COLLECTION, navigator.currentScreen)

        navigator.returnToMain()

        assertEquals("Should return to MAIN screen", AppScreen.MAIN, navigator.currentScreen)
    }

    /**
     * Test: Navigate to verification screen with card data.
     */
    @Test
    fun testNavigationToVerification() {
        val cardData = CardVerification(
            trackingId = 1,
            detectedCardText = DetectedCardText("Black Lotus", "LEA", "1", 0.95),
            matchCandidates = emptyList(),
            userAction = UserAction.PENDING
        )

        navigator.navigateToVerification(cardData)

        assertEquals("Should navigate to VERIFICATION screen", AppScreen.VERIFICATION, navigator.currentScreen)
        assertNotNull("Should store card verification data", navigator.cardVerification)
        assertEquals("Should match verification data", cardData.trackingId, navigator.cardVerification?.trackingId)
    }

    /**
     * Test: Navigation clears verification data when going back.
     */
    @Test
    fun testNavigationClearsVerificationData() {
        val cardData = CardVerification(
            trackingId = 1,
            detectedCardText = DetectedCardText("Black Lotus", "LEA", "1", 0.95),
            matchCandidates = emptyList(),
            userAction = UserAction.PENDING
        )

        navigator.navigateToVerification(cardData)
        assertNotNull("Should have verification data", navigator.cardVerification)

        navigator.goBack()

        assertNull("Should clear verification data on back", navigator.cardVerification)
    }

    /**
     * Test: Back navigation from different screens.
     */
    @Test
    fun testBackNavigationFromMultipleScreens() {
        // From camera
        navigator.navigateToCamera()
        assertEquals("Should be on CAMERA", AppScreen.CAMERA, navigator.currentScreen)
        navigator.goBack()
        assertEquals("Should go back to MAIN", AppScreen.MAIN, navigator.currentScreen)

        // From collection
        navigator.navigateToCollection()
        assertEquals("Should be on COLLECTION", AppScreen.COLLECTION, navigator.currentScreen)
        navigator.goBack()
        assertEquals("Should go back to MAIN", AppScreen.MAIN, navigator.currentScreen)
    }

    /**
     * Test: Main screen button visibility and navigation.
     */
    @Test
    fun testMainMenuScreenButtons() {
        composeTestRule.setContent {
            MainMenuScreen(
                onCameraClick = { navigator.navigateToCamera() },
                onCollectionClick = { navigator.navigateToCollection() }
            )
        }

        // Check for button presence
        composeTestRule.onNodeWithText("Scan Cards").assertIsDisplayed()
        composeTestRule.onNodeWithText("View Collection").assertIsDisplayed()

        // Click camera button
        composeTestRule.onNodeWithText("Scan Cards").performClick()
        assertEquals("Should navigate to CAMERA", AppScreen.CAMERA, navigator.currentScreen)
    }

    /**
     * Test: Collection screen displays collection stats.
     */
    @Test
    fun testCollectionScreenStats() {
        composeTestRule.setContent {
            // Mock empty database
            CollectionScreen(
                database = createMockEmptyDatabase(),
                onEditCard = {}
            )
        }

        // Check for stats display
        composeTestRule.onNodeWithText("My Collection").assertIsDisplayed()
        composeTestRule.onNodeWithText("Total Cards").assertExists()
        composeTestRule.onNodeWithText("Unique").assertExists()
        composeTestRule.onNodeWithText("Sets").assertExists()
    }

    /**
     * Test: Collection screen search functionality.
     */
    @Test
    fun testCollectionScreenSearch() {
        composeTestRule.setContent {
            CollectionScreen(
                database = createMockDatabaseWithCards(),
                onEditCard = {}
            )
        }

        // Find search box
        composeTestRule.onNodeWithText("Search by name...").assertIsDisplayed()

        // Type in search
        composeTestRule.onNodeWithText("Search by name...").performTextInput("Black")

        // Verify search is active
        composeTestRule.onNodeWithText("Search by name...").assert(hasText("Black"))
    }

    /**
     * Test: Verification screen displays card details.
     */
    @Test
    fun testVerificationScreenCardDisplay() {
        val cardVerification = CardVerification(
            trackingId = 1,
            detectedCardText = DetectedCardText(
                cardName = "Black Lotus",
                setCode = "LEA",
                collectorNumber = "1",
                confidence = 0.95
            ),
            matchCandidates = listOf(
                CardMatchCandidate(
                    scryfallCard = ScryfallCard(
                        id = "lotus",
                        name = "Black Lotus",
                        setCode = "LEA",
                        collectorNumber = "1",
                        imageUris = null
                    ),
                    matchScore = 0.98,
                    matchReason = "Perfect match"
                )
            ),
            userAction = UserAction.PENDING
        )

        composeTestRule.setContent {
            VerificationScreen(
                cardVerification = cardVerification,
                onConfirm = { _, _ -> },
                onReject = {},
                onSkip = {}
            )
        }

        // Check OCR results display
        composeTestRule.onNodeWithText("Verify Card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Black Lotus").assertIsDisplayed()
        composeTestRule.onNodeWithText("LEA").assertIsDisplayed()

        // Check action buttons
        composeTestRule.onNodeWithText("Confirm").assertIsDisplayed()
        composeTestRule.onNodeWithText("Reject").assertIsDisplayed()
        composeTestRule.onNodeWithText("Skip").assertIsDisplayed()
    }

    /**
     * Test: Verification screen confirm action.
     */
    @Test
    fun testVerificationScreenConfirm() {
        var confirmCalled = false
        var confirmedQuantity = 0

        val cardVerification = CardVerification(
            trackingId = 1,
            detectedCardText = DetectedCardText("Black Lotus", "LEA", "1", 0.95),
            matchCandidates = listOf(
                CardMatchCandidate(
                    scryfallCard = ScryfallCard(
                        id = "lotus",
                        name = "Black Lotus",
                        setCode = "LEA",
                        collectorNumber = "1",
                        imageUris = null
                    ),
                    matchScore = 0.98,
                    matchReason = "Perfect match"
                )
            ),
            userAction = UserAction.PENDING
        )

        composeTestRule.setContent {
            VerificationScreen(
                cardVerification = cardVerification,
                onConfirm = { _, qty ->
                    confirmCalled = true
                    confirmedQuantity = qty
                },
                onReject = {},
                onSkip = {}
            )
        }

        // Enter quantity
        composeTestRule.onNodeWithText("Quantity").performTextInput("2")

        // Click confirm
        composeTestRule.onNodeWithText("Confirm").performClick()

        assertTrue("Confirm callback should be invoked", confirmCalled)
        assertEquals("Quantity should be 2", 2, confirmedQuantity)
    }

    /**
     * Test: Verification screen reject action.
     */
    @Test
    fun testVerificationScreenReject() {
        var rejectCalled = false

        val cardVerification = CardVerification(
            trackingId = 1,
            detectedCardText = DetectedCardText("Black Lotus", "LEA", "1", 0.95),
            matchCandidates = emptyList(),
            userAction = UserAction.PENDING
        )

        composeTestRule.setContent {
            VerificationScreen(
                cardVerification = cardVerification,
                onConfirm = { _, _ -> },
                onReject = { rejectCalled = true },
                onSkip = {}
            )
        }

        composeTestRule.onNodeWithText("Reject").performClick()

        assertTrue("Reject callback should be invoked", rejectCalled)
    }

    /**
     * Helper: Create mock empty database.
     */
    private fun createMockEmptyDatabase(): ScannedCardDatabase {
        // In real tests, use Room.inMemoryDatabaseBuilder()
        // For now, return null/mock (would need actual implementation)
        TODO("Implement mock database")
    }

    /**
     * Helper: Create mock database with sample cards.
     */
    private fun createMockDatabaseWithCards(): ScannedCardDatabase {
        // In real tests, populate with cards and return
        TODO("Implement mock database with cards")
    }
}
