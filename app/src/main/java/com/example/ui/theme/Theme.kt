package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
  primary = CyanAccent,
  onPrimary = Color(0xFF003548),
  primaryContainer = Color(0xFF004D68),
  onPrimaryContainer = Color(0xFFBDE9FF),
  secondary = IndigoAccent,
  onSecondary = Color(0xFF1E2678),
  secondaryContainer = Color(0xFF333D98),
  onSecondaryContainer = Color(0xFFE0E0FF),
  tertiary = EmeraldSuccess,
  onTertiary = Color(0xFF003822),
  background = Slate950,
  onBackground = Slate200,
  surface = Slate900,
  onSurface = Slate200,
  surfaceVariant = Slate800,
  onSurfaceVariant = Slate400,
  outline = Slate700,
  outlineVariant = Slate600
)

private val LightColorScheme = lightColorScheme(
  primary = CyanAccentVariant,
  onPrimary = Color.White,
  primaryContainer = Color(0xFFC3E8FF),
  onPrimaryContainer = Color(0xFF001E2B),
  secondary = Color(0xFF4F46E5),
  onSecondary = Color.White,
  secondaryContainer = Color(0xFFE0E7FF),
  onSecondaryContainer = Color(0xFF1E1B4B),
  tertiary = Color(0xFF059669),
  background = Color(0xFFF8FAFC),
  onBackground = Slate900,
  surface = Color.White,
  onSurface = Slate900,
  surfaceVariant = Color(0xFFF1F5F9),
  onSurfaceVariant = Slate700,
  outline = Color(0xFFCBD5E1),
  outlineVariant = Color(0xFFE2E8F0)
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false, // Use our sleek developer palette by default for authentic terminal aesthetics
  content: @Composable () -> Unit,
) {
  val colorScheme = when {
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
      val context = LocalContext.current
      if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    darkTheme -> DarkColorScheme
    else -> LightColorScheme
  }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content
  )
}
