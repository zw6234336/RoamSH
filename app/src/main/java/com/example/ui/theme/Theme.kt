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

/**
 * Dark scheme reproduces the legacy hardcoded slate/cyan chrome pixel-for-pixel
 * (pure-white on-surface text, black text on cyan/emerald accents, rose error),
 * so switching the app to theme-aware colors changes nothing in dark mode.
 * Internal so ThemeContrastTest can assert WCAG AA pairs stay readable.
 */
internal val DarkColorScheme = darkColorScheme(
  primary = Palette.cyanDark,
  onPrimary = Color.Black,
  primaryContainer = Color(0xFF004D68),
  onPrimaryContainer = Color(0xFFBDE9FF),
  secondary = Palette.indigoDark,
  onSecondary = Color(0xFF1E2678),
  secondaryContainer = Color(0xFF333D98),
  onSecondaryContainer = Color(0xFFE0E0FF),
  tertiary = Palette.emeraldDark,
  onTertiary = Color.Black,
  background = Palette.slate950Dark,
  onBackground = Color.White,
  surface = Palette.slate900Dark,
  onSurface = Color.White,
  surfaceVariant = Palette.slate800Dark,
  onSurfaceVariant = Palette.slate400Dark,
  outline = Palette.slate600Dark,
  outlineVariant = Palette.slate700Dark,
  error = Palette.roseDark,
  onError = Color.Black,
  errorContainer = Palette.roseDark,
  onErrorContainer = Color.Black
)

/** Light scheme: AA-compliant text pairs on near-white slate surfaces. */
internal val LightColorScheme = lightColorScheme(
  primary = Palette.cyanLight,
  onPrimary = Color.White,
  primaryContainer = Color(0xFFC3E8FF),
  onPrimaryContainer = Color(0xFF001E2B),
  secondary = Palette.indigoLight,
  onSecondary = Color.White,
  secondaryContainer = Color(0xFFE0E7FF),
  onSecondaryContainer = Color(0xFF1E1B4B),
  tertiary = Palette.emeraldLight,
  onTertiary = Color.White,
  background = Palette.slate50Light,
  onBackground = Palette.slate900Light,
  surface = Color.White,
  onSurface = Palette.slate900Light,
  surfaceVariant = Palette.slate100Light,
  onSurfaceVariant = Palette.slate700Light,
  outline = Palette.slate400Light,
  outlineVariant = Palette.slate200Light,
  error = Palette.roseLight,
  onError = Color.White,
  errorContainer = Palette.roseLight,
  onErrorContainer = Color.White
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
