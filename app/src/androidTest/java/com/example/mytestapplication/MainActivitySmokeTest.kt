package com.example.mytestapplication

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the production Activity/Application wiring without replacing its repository. */
@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun productionEntryPointOpensCreateAndReturnsToList() {
        // Create is available during loading and after either random load outcome.
        // Avoid random writes so this smoke test never depends on simulated failure luck.
        compose.onNodeWithText("Todos").assertIsDisplayed()
        compose.onNodeWithText("Create").assertIsEnabled().performClick()
        compose.onNodeWithText("New todo").assertIsDisplayed()
        compose.onNodeWithText("Title").assertIsDisplayed()
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNode(isSelectable().and(hasText("Medium"))).assertIsSelected()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Todos").assertIsDisplayed()
        compose.onNodeWithText("Create").assertIsEnabled()
    }
}
