package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/**
 * Raw palette — the single source both Material color schemes in Theme.kt are built
 * from. Screens must not reference these values directly; they read the theme-aware
 * aliases below (or MaterialTheme.colorScheme) so the whole app follows the system
 * light/dark setting.
 *
 * Dark values are exactly the legacy RoamSH slate palette, so dark mode keeps its
 * previous look; light values are chosen for WCAG AA text contrast on light surfaces.
 */
internal object Palette {
  // Legacy dark slate palette (unchanged visuals in dark mode)
  val slate950Dark = Color(0xFF0B0F19) // chrome background
  val slate900Dark = Color(0xFF0F172A) // cards / sheets
  val slate850Dark = Color(0xFF131D31)
  val slate800Dark = Color(0xFF1E293B) // chips / menu surface
  val slate700Dark = Color(0xFF334155) // borders
  val slate600Dark = Color(0xFF475569)
  val slate400Dark = Color(0xFF94A3B8)
  val slate200Dark = Color(0xFFE2E8F0)
  val cyanDark = Color(0xFF38BDF8)
  val indigoDark = Color(0xFF818CF8)
  val emeraldDark = Color(0xFF10B981)
  val amberDark = Color(0xFFF59E0B)
  val roseDark = Color(0xFFF43F5E)

  // Light equivalents with AA-compliant text contrast on white/light surfaces
  val slate50Light = Color(0xFFF8FAFC)
  val slate100Light = Color(0xFFF1F5F9)
  val slate200Light = Color(0xFFE2E8F0)
  val slate400Light = Color(0xFF94A3B8)
  val slate700Light = Color(0xFF334155)
  val slate900Light = Color(0xFF0F172A)
  val cyanLight = Color(0xFF0369A1) // sky-700, ~5.3:1 on white
  val indigoLight = Color(0xFF4F46E5)
  val emeraldLight = Color(0xFF047857) // emerald-700, ~5.2:1 on white
  val amberLight = Color(0xFFB45309) // amber-700
  val roseLight = Color(0xFFBE123C) // rose-700, ~5.9:1 on white
}

/**
 * Theme-aware aliases. The historical palette names are what every screen already
 * references; resolving them through MaterialTheme.colorScheme makes all of those
 * call sites follow the active scheme (terminal-theme driven once a session theme
 * is applied) with zero call-site churn.
 */
val Slate950: Color
  @Composable get() = MaterialTheme.colorScheme.background

val Slate900: Color
  @Composable get() = MaterialTheme.colorScheme.surface

val Slate850: Color
  @Composable get() = lerp(
    MaterialTheme.colorScheme.surface,
    MaterialTheme.colorScheme.primary,
    0.08f
  )

val Slate800: Color
  @Composable get() = MaterialTheme.colorScheme.surfaceVariant

val Slate700: Color
  @Composable get() = MaterialTheme.colorScheme.outlineVariant

val CyanAccent: Color
  @Composable get() = MaterialTheme.colorScheme.primary

val IndigoAccent: Color
  @Composable get() = MaterialTheme.colorScheme.secondary

val EmeraldSuccess: Color
  @Composable get() = MaterialTheme.colorScheme.tertiary

val RoseError: Color
  @Composable get() = MaterialTheme.colorScheme.error

val AmberWarning: Color
  @Composable get() = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
    Palette.amberDark
  } else {
    Palette.amberLight
  }

val CyanAccentVariant: Color
  @Composable get() = MaterialTheme.colorScheme.primary

// Terminal defaults are independent of the app chrome theme (the terminal viewport
// keeps the user-selected terminal color scheme in both light and dark mode).
val TerminalBackgroundDefault = Color(0xFF0D1117)
val TerminalTextDefault = Color(0xFFC9D1D9)
val TerminalCursorDefault = Color(0xFF58A6FF)
