package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import com.example.ssh.TerminalColorScheme

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

// ---------------------------------------------------------------------------
// Terminal-driven app theming: the selected terminal color scheme (Dracula,
// Nord, ...) restyles the whole app chrome, not just the terminal viewport.
// ---------------------------------------------------------------------------

/** WCAG AA text contrast threshold. */
internal const val AA_CONTRAST = 4.5

/** WCAG 2.1 contrast ratio between two opaque colors. */
internal fun contrastRatio(a: Color, b: Color): Double {
  val l1 = a.luminance().toDouble() + 0.05
  val l2 = b.luminance().toDouble() + 0.05
  return maxOf(l1, l2) / minOf(l1, l2)
}

/**
 * Returns [color] adjusted (if needed) by the smallest blend toward the
 * contrasting pole (black/white) that reaches [min] contrast against every
 * [backgrounds], so derived palettes stay readable without abandoning the
 * theme's identity.
 */
private fun ensureContrast(color: Color, backgrounds: List<Color>, min: Double = AA_CONTRAST): Color {
  fun passes(c: Color) = backgrounds.all { contrastRatio(c, it) >= min }
  if (passes(color)) return color
  val pole = if (contrastRatio(Color.Black, backgrounds.first()) >=
    contrastRatio(Color.White, backgrounds.first())
  ) {
    Color.Black
  } else {
    Color.White
  }
  var low = 0f
  var high = 1f
  repeat(16) {
    val mid = (low + high) / 2f
    if (passes(lerp(color, pole, mid))) high = mid else low = mid
  }
  return lerp(color, pole, high)
}

/** Black or white — whichever is AA-readable on [accent] (always possible: max ≥ 4.58). */
private fun onColorFor(accent: Color): Color =
  if (contrastRatio(Color.Black, accent) >= contrastRatio(Color.White, accent)) Color.Black else Color.White

/** A terminal scheme reads as "dark" when its background luminance is below mid. */
internal fun isDarkScheme(scheme: TerminalColorScheme): Boolean = scheme.background.luminance() < 0.5f

/**
 * Derives the app Material scheme from a terminal palette so the entire app
 * follows the selected terminal theme:
 *  - background/surfaces: blends of the theme background toward its foreground;
 *  - primary/secondary: the theme's accent whose identity survives AA contrast
 *    (cyan/green first, yellow as fallback — e.g. Gruvbox & Solarized resolve
 *    to their signature yellow);
 *  - text/on colors are contrast-guaranteed, so every derived scheme passes
 *    the same WCAG AA matrix as [ThemeContrastTest] asserts.
 */
internal fun appColorSchemeFromTerminal(scheme: TerminalColorScheme): ColorScheme {
  val bg = scheme.background
  val fg = scheme.foreground
  val isDark = isDarkScheme(scheme)

  val surface = lerp(bg, fg, 0.05f)
  val surfaceVariant = lerp(bg, fg, 0.11f)
  val outlineVariant = lerp(bg, fg, 0.20f)
  val outline = lerp(bg, fg, 0.32f)

  // surfaceVariant is the surface furthest from the background (toward the
  // foreground); bg is the opposite extreme. Text and accents must read on both.
  val textSurfaces = listOf(bg, surfaceVariant)
  val onSurface = ensureContrast(fg, textSurfaces)
  val onSurfaceVariant = ensureContrast(lerp(bg, fg, 0.68f), textSurfaces)

  // Identity-ordered accent candidates: prefer cyan/green terminal accents,
  // fall back to whichever accent contrasts best with this theme's background.
  val orderedAccents = listOf(
    scheme.cyan, scheme.green, scheme.blue, scheme.magenta, scheme.yellow,
    scheme.brightCyan, scheme.brightGreen
  ).distinct()
  fun pickAccent(exclude: Set<Color>): Color =
    orderedAccents.firstOrNull { it !in exclude && contrastRatio(it, bg) >= AA_CONTRAST }
      ?: orderedAccents.filter { it !in exclude }.maxByOrNull { contrastRatio(it, bg) }
      ?: scheme.foreground

  val primaryRaw = pickAccent(emptySet())
  val primary = ensureContrast(primaryRaw, textSurfaces)
  val secondaryRaw = pickAccent(setOf(primaryRaw))
  val secondary = ensureContrast(secondaryRaw, textSurfaces)
  // Green keeps "success" semantics; when the whole green family is already
  // taken by primary/secondary (e.g. Monokai where green == brightGreen),
  // fall back to the secondary accent rather than crashing on an empty list.
  val tertiaryRaw = listOf(scheme.green, scheme.brightGreen, scheme.cyan)
    .filter { it != primaryRaw && it != secondaryRaw }
    .maxByOrNull { contrastRatio(it, bg) }
    ?: secondaryRaw
  val tertiary = ensureContrast(tertiaryRaw, textSurfaces)
  val errorRaw = if (contrastRatio(scheme.red, bg) >= contrastRatio(scheme.brightRed, bg)) {
    scheme.red
  } else {
    scheme.brightRed
  }
  val error = ensureContrast(errorRaw, textSurfaces)

  fun containerFor(accent: Color): Color = lerp(bg, accent, 0.28f)

  val base = if (isDark) DarkColorScheme else LightColorScheme
  return base.copy(
    primary = primary,
    onPrimary = onColorFor(primary),
    primaryContainer = containerFor(primary),
    onPrimaryContainer = ensureContrast(primary, listOf(containerFor(primary))),
    secondary = secondary,
    onSecondary = onColorFor(secondary),
    secondaryContainer = containerFor(secondary),
    onSecondaryContainer = ensureContrast(secondary, listOf(containerFor(secondary))),
    tertiary = tertiary,
    onTertiary = onColorFor(tertiary),
    tertiaryContainer = containerFor(tertiary),
    onTertiaryContainer = ensureContrast(tertiary, listOf(containerFor(tertiary))),
    background = bg,
    onBackground = onSurface,
    surface = surface,
    onSurface = onSurface,
    surfaceVariant = surfaceVariant,
    onSurfaceVariant = onSurfaceVariant,
    outline = outline,
    outlineVariant = outlineVariant,
    error = error,
    onError = onColorFor(error),
    errorContainer = error,
    onErrorContainer = onColorFor(error)
  )
}

/**
 * App theme bound to the selected terminal color scheme. Nested inside
 * [MyApplicationTheme]; every screen reads MaterialTheme.colorScheme (directly
 * or via the Slate/Cyan aliases), so the whole app restyles when the terminal
 * theme changes.
 */
@Composable
fun TerminalDrivenTheme(
  scheme: TerminalColorScheme,
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = appColorSchemeFromTerminal(scheme),
    typography = Typography,
    content = content
  )
}
