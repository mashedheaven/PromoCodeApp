package com.promocodeapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Drives the real app (real DI graph, real database) through its main flow. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AppSmokeTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    /** Room writes happen off the main thread; wait for the UI to catch up. */
    private fun awaitText(text: String) = compose.waitUntil(5_000) {
        compose.onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun `add a coupon then find use and delete it`() {
        awaitText("No coupons yet")

        compose.onNodeWithText("Add coupon", useUnmergedTree = true).performClick()
        compose.onNodeWithText("New coupon").assertIsDisplayed()

        // Saving an empty form explains what is missing instead of saving junk.
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Enter the store name").assertIsDisplayed()
        compose.onNodeWithText("Enter the discount").assertIsDisplayed()

        compose.onNodeWithText("Store").performTextInput("Target")
        compose.onNodeWithText("Promo code").performTextInput("SAVE20")
        compose.onNodeWithText("Percent").performTextInput("20")
        compose.onNodeWithText("Save").performClick()

        awaitText("20% OFF")
        awaitText("Target")
        awaitText("SAVE20")
        awaitText("No expiry")

        // Detail screen: mark as used, which moves it out of the active list.
        compose.onNodeWithText("Target").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("Copy code")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Mark as used").performClick()
        awaitText("Mark as unused")
        compose.onNodeWithContentDescription("Back").performClick()
        awaitText("You have no active coupons.")
        compose.onNodeWithText("Used").performClick()
        awaitText("SAVE20")

        // Delete with confirmation.
        compose.onNodeWithText("Target").performClick()
        compose.onNodeWithContentDescription("Delete").performClick()
        awaitText("Delete coupon?")
        compose.onNodeWithText("Delete").performClick()
        awaitText("No coupons yet")
    }

    @Test
    fun `every tab opens and settings explains alert setup`() {
        compose.onNodeWithText("Memberships").performClick()
        awaitText("No memberships yet")
        compose.onNodeWithText("Places").performClick()
        awaitText("No places yet")
        compose.onNodeWithText("Settings").performClick()
        awaitText("Finish setting up nearby alerts")
        compose.onNodeWithText("Cloud sync isn't set up in this build.", substring = true).assertExists()
    }
}
