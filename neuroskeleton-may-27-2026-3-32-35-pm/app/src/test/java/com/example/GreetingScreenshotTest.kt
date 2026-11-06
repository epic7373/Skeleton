package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.ui.screens.MainTrackerScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.NeuroViewModel
import com.example.ui.viewmodel.TrackingMode
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34]) // Change to 34 or supported sdk
class GreetingScreenshotTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun dashboard_screenshot() {
        val viewModel = NeuroViewModel().apply {
            toggleSimulation(true) // force simulation for stable UI visual
            setMode(TrackingMode.HUD_VISION)
        }

        composeTestRule.setContent {
            MyApplicationTheme {
                MainTrackerScreen(viewModel = viewModel)
            }
        }

        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
    }
}
