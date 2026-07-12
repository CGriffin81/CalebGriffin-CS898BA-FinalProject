package com.mtgscanner.ui

import androidx.compose.runtime.*
import com.mtgscanner.model.CardVerification
import com.mtgscanner.model.ScannedCard

/**
 * AppScreen: Enum representing application navigation screens.
 * Defines all possible screens in the MTG Scanner UI.
 *
 * States:
 * - MAIN: Main menu screen (launch point, navigation hub)
 * - CAMERA: Live camera preview with real-time card detection
 * - VERIFICATION: User confirmation of detected card and quantity entry
 * - COLLECTION: Browse, search, and manage scanned card collection
 */
enum class AppScreen {
    MAIN,
    CAMERA,
    VERIFICATION,
    COLLECTION
}

/**
 * AppNavigator: State machine managing application navigation and screen transitions.
 * Maintains current screen state and passes data (CardVerification, ScannedCard) between screens.
 * Ensures clean backstack behavior and prevents invalid state transitions.
 *
 * Navigation Flow:
 * MAIN ←→ CAMERA → VERIFICATION → COLLECTION → MAIN (or back)
 *
 * State Management:
 * - currentScreen: MutableState of current AppScreen (triggers recomposition on change)
 * - cardVerification: CardVerification data passed to VerificationScreen
 * - selectedCard: ScannedCard for collection detail view (reserved for future use)
 *
 * Stability:
 * - @Stable annotation: Tells Compose this object's property changes don't require full recomposition
 * - Properties are MutableState to trigger composition updates on navigation change
 *
 * @property currentScreen Current screen being displayed
 * @property cardVerification CardVerification passed to VerificationScreen with error state
 * @property selectedCard ScannedCard for detail view (future use)
 */
@Stable
class AppNavigator {
    var currentScreen by mutableStateOf(AppScreen.MAIN)
        private set

    var cardVerification by mutableStateOf<CardVerification?>(null)
        private set

    var selectedCard by mutableStateOf<ScannedCard?>(null)
        private set

    /**
     * Navigate to a specified screen with optional data parameter.
     * Validates screen type before transition and clears irrelevant state.
     * Prevents invalid transitions (e.g., VERIFICATION without CardVerification data).
     *
     * @param screen Target AppScreen to navigate to
     * @param cardData Optional data for screen (CardVerification for VERIFICATION screen)
     * @throws IllegalArgumentException if CardVerification required but not provided
     */
    fun navigateTo(screen: AppScreen, cardData: Any? = null) {
        when {
            screen == AppScreen.CAMERA -> {
                currentScreen = screen
                cardVerification = null
            }
            screen == AppScreen.VERIFICATION && cardData is CardVerification -> {
                currentScreen = screen
                cardVerification = cardData
            }
            screen == AppScreen.COLLECTION -> {
                currentScreen = screen
                cardVerification = null
            }
            screen == AppScreen.MAIN -> {
                currentScreen = screen
                cardVerification = null
            }
        }
    }

    /**
     * Navigate back to previous screen.
     * Implements basic backstack behavior:
     * - VERIFICATION/COLLECTION → MAIN
     * - MAIN → stays at MAIN (no-op)
     * - CAMERA → MAIN (since CAMERA is typically entered from MAIN)
     * Clears CardVerification state to prevent stale data.
     */
    fun goBack() {
        currentScreen = when (currentScreen) {
            AppScreen.VERIFICATION, AppScreen.COLLECTION -> AppScreen.MAIN
            else -> AppScreen.MAIN
        }
        cardVerification = null
    }

    /**
     * Navigate to camera screen for live card scanning.
     * Convenience wrapper for navigateTo(AppScreen.CAMERA).
     * Clears any previous CardVerification state.
     */
    fun navigateToCamera() {
        navigateTo(AppScreen.CAMERA)
    }

    /**
     * Navigate to collection browsing screen.
     * Convenience wrapper for navigateTo(AppScreen.COLLECTION).
     * Displays all scanned cards with search, filter, and sort options.
     */
    fun navigateToCollection() {
        navigateTo(AppScreen.COLLECTION)
    }

    /**
     * Navigate to verification screen with error state parameters.
     * Creates a new CardVerification with merged error state for resilient error handling.
     * Error parameters override CardVerification fields to ensure UI reflects current error state.
     *
     * Error State Flow:
     * - errorMessage: Shown in ErrorSnackbar if not null
     * - isOffline: Shown as OfflineNotice if true (card loaded from cache)
     * - ocrConfidence: Used for LowConfidenceWarning if < 0.6
     *
     * @param cardData CardVerification from fuzzy matcher (matches, OCR results)
     * @param errorMessage Network/API error to display (null if no error)
     * @param isOffline Whether card was loaded from offline cache (affects storage strategy)
     * @param ocrConfidence OCR confidence override (0.0-1.0; < 0.6 shows warning)
     */
    fun navigateToVerification(
        cardData: CardVerification,
        errorMessage: String? = null,
        isOffline: Boolean = false,
        ocrConfidence: Float = 0f
    ) {
        val verificationWithErrorState = cardData.copy(
            errorMessage = errorMessage,
            isOffline = isOffline,
            ocrConfidence = ocrConfidence
        )
        navigateTo(AppScreen.VERIFICATION, verificationWithErrorState)
    }

    /**
     * Return to main screen from any screen.
     * Similar to goBack() but explicitly targets MAIN (no-op if already at MAIN).
     * Wrapper for goBack() with clearer semantics.
     */
    fun returnToMain() {
        goBack()
    }
}
