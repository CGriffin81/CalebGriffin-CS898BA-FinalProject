package com.mtgscanner.ui

import androidx.compose.runtime.*
import com.mtgscanner.model.CardVerification
import com.mtgscanner.model.ScannedCard

/**
 * AppScreen: Enum representing navigation states.
 */
enum class AppScreen {
    MAIN,
    CAMERA,
    VERIFICATION,
    COLLECTION
}

/**
 * AppNavigator: Manages navigation state and screen transitions.
 * Maintains current screen and passes data between screens (e.g., CardVerification to VerificationScreen).
 */
@Stable
class AppNavigator {
    var currentScreen by mutableStateOf(AppScreen.MAIN)
        private set

    var cardVerification by mutableStateOf<CardVerification?>(null)
        private set

    var selectedCard by mutableStateOf<ScannedCard?>(null)
        private set

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

    fun goBack() {
        currentScreen = when (currentScreen) {
            AppScreen.VERIFICATION, AppScreen.COLLECTION -> AppScreen.MAIN
            else -> AppScreen.MAIN
        }
        cardVerification = null
    }

    fun navigateToCamera() {
        navigateTo(AppScreen.CAMERA)
    }

    fun navigateToCollection() {
        navigateTo(AppScreen.COLLECTION)
    }

    fun navigateToVerification(
        cardData: CardVerification,
        errorMessage: String? = null,
        isOffline: Boolean = false,
        ocrConfidence: Double = 0.0
    ) {
        val verificationWithErrorState = cardData.copy(
            errorMessage = errorMessage,
            isOffline = isOffline,
            ocrConfidence = ocrConfidence.toFloat()
        )
        navigateTo(AppScreen.VERIFICATION, verificationWithErrorState)
    }

    fun returnToMain() {
        goBack()
    }
}
