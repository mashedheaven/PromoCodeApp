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

    @Test
    fun `add a coupon then find use and delete it`() {
        compose.onNodeWithText("No coupons yet").assertIsDisplayed()

        compose.onNodeWithText("Add coupon").performClick()
        compose.onNodeWithText("New coupon").assertIsDisplayed()

        // Saving an empty form explains what is missing instead of saving junk.
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Enter the store name").assertIsDisplayed()
        compose.onNodeWithText("Enter the discount").assertIsDisplayed()

        compose.onNodeWithText("Store").performTextInput("Target")
        compose.onNodeWithText("Promo code").performTextInput("SAVE20")
        compose.onNodeWithText("Percent").performTextInput("20")
        compose.onNodeWithText("Save").performClick()

        compose.waitUntil(5_000) { compose.onAllNodes(androidx.compose.ui.test.hasText("20% OFF")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Target").assertIsDisplayed()
        compose.onNodeWithText("SAVE20").assertIsDisplayed()
        compose.onNodeWithText("No expiry").assertIsDisplayed()

        // Detail screen: mark as used, which moves it out of the active list.
        compose.onNodeWithText("Target").performClick()
        compose.onNodeWithContentDescription("Copy code").assertIsDisplayed()
        compose.onNodeWithText("Mark as used").performClick()
        compose.onNodeWithText("Mark as unused").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("You have no active coupons.").assertIsDisplayed()
        compose.onNodeWithText("Used").performClick()
        compose.onNodeWithText("SAVE20").assertIsDisplayed()

        // Delete with confirmation.
        compose.onNodeWithText("Target").performClick()
        compose.onNodeWithContentDescription("Delete").performClick()
        compose.onNodeWithText("Delete coupon?").assertIsDisplayed()
        compose.onNodeWithText("Delete").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(androidx.compose.ui.test.hasText("No coupons yet")).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `every tab opens and settings explains alert setup`() {
        compose.onNodeWithText("Memberships").performClick()
        compose.onNodeWithText("No memberships yet").assertIsDisplayed()
        compose.onNodeWithText("Places").performClick()
        compose.onNodeWithText("No places yet").assertIsDisplayed()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Finish setting up nearby alerts").assertIsDisplayed()
        compose.onNodeWithText("Cloud sync isn't set up in this build.", substring = true).assertExists()
    }
}
