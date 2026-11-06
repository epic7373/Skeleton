package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = NeonCyan,
    secondary = NeonBlue,
    tertiary = NeonPurple,
    background = CyberBlack,
    surface = CyberSlate,
    onPrimary = CyberBlack,
    onSecondary = GlowWhite,
    onBackground = GlowWhite,
    onSurface = GlowWhite,
    error = NeonRed
  )

private val LightColorScheme = DarkColorScheme // Always premium dark for a cinematic futuristic feel!

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true,
  dynamicColor: Boolean = false, // Disable dynamic colors to keep our iconic custom scifi brand colors!
  content: @Composable () -> Unit,
) {
  val colorScheme = DarkColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
