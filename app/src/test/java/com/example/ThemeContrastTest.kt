package com.example

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.DarkColorScheme
import com.example.ui.theme.LightColorScheme
import com.example.ui.theme.Palette
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * WCAG 2.1 contrast verification for both app color schemes: every text/icon
 * color the screens actually pair must reach AA (>= 4.5:1). This guards the
 * light-theme migration — dark pairs mirror the legacy palette, light pairs
 * were re-picked specifically to pass.
 */
class ThemeContrastTest {

  // WCAG relative luminance
  private fun channel(c: Float): Double {
    val v = c.toDouble()
    return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
  }

  private fun luminance(c: Color): Double =
    0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)

  private fun contrast(fg: Color, bg: Color): Double {
    val l1 = luminance(fg) + 0.05
    val l2 = luminance(bg) + 0.05
    return maxOf(l1, l2) / minOf(l1, l2)
  }

  private fun assertAA(name: String, fg: Color, bg: Color) {
    val ratio = contrast(fg, bg)
    assertTrue("$name contrast %.2f:1 < 4.5:1".format(ratio), ratio >= 4.5)
  }

  private fun checkScheme(label: String, s: ColorScheme) {
    // Body & label text on the surfaces it sits on
    assertAA("$label onSurface/surface", s.onSurface, s.surface)
    assertAA("$label onSurface/background", s.onSurface, s.background)
    assertAA("$label onSurface/surfaceVariant", s.onSurface, s.surfaceVariant)
    assertAA("$label onSurfaceVariant/surface", s.onSurfaceVariant, s.surface)
    assertAA("$label onSurfaceVariant/surfaceVariant", s.onSurfaceVariant, s.surfaceVariant)
    // Accent-colored text (labels, status, menu icons) on all chrome surfaces
    assertAA("$label primary/surface", s.primary, s.surface)
    assertAA("$label primary/background", s.primary, s.background)
    assertAA("$label primary/surfaceVariant", s.primary, s.surfaceVariant)
    assertAA("$label tertiary/surface", s.tertiary, s.surface)
    assertAA("$label error/surface", s.error, s.surface)
    // Filled buttons / badges: content color on its container
    assertAA("$label onPrimary/primary", s.onPrimary, s.primary)
    assertAA("$label onTertiary/tertiary", s.onTertiary, s.tertiary)
    assertAA("$label onError/error", s.onError, s.error)
  }

  @Test
  fun darkSchemeMeetsAA() = checkScheme("dark", DarkColorScheme)

  @Test
  fun lightSchemeMeetsAA() = checkScheme("light", LightColorScheme)

  @Test
  fun amberWarningAliasMeetsAAOnBothSurfaces() {
    // AmberWarning is a status color (connecting dot / starred host icon) drawn on
    // background and surface; both mode variants must stay readable.
    assertAA("amberDark/background", Palette.amberDark, Palette.slate950Dark)
    assertAA("amberDark/surface", Palette.amberDark, Palette.slate900Dark)
    assertAA("amberLight/background", Palette.amberLight, Palette.slate50Light)
    assertAA("amberLight/surface", Palette.amberLight, Color.White)
  }

  @Test
  fun darkSchemeMatchesLegacyPalette() {
    // The migration promise: dark mode keeps the exact legacy chrome colors.
    assertTrue(DarkColorScheme.background == Palette.slate950Dark)
    assertTrue(DarkColorScheme.surface == Palette.slate900Dark)
    assertTrue(DarkColorScheme.surfaceVariant == Palette.slate800Dark)
    assertTrue(DarkColorScheme.outlineVariant == Palette.slate700Dark)
    assertTrue(DarkColorScheme.primary == Palette.cyanDark)
    assertTrue(DarkColorScheme.tertiary == Palette.emeraldDark)
    assertTrue(DarkColorScheme.error == Palette.roseDark)
    assertTrue(DarkColorScheme.onSurface == Color.White)
  }
}
