package com.example

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.example.ssh.TerminalThemes
import com.example.ui.theme.AA_CONTRAST
import com.example.ui.theme.appColorSchemeFromTerminal
import com.example.ui.theme.contrastRatio
import com.example.ui.theme.isDarkScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WCAG 2.1 contrast verification for the terminal-derived app schemes: picking a
 * terminal theme (Dracula, Nord, Gruvbox Light, ...) restyles the whole app, so
 * every derived scheme must pass the same AA matrix as ThemeContrastTest asserts
 * for the built-in dark/light schemes — on any of the 10 palettes.
 */
class TerminalAppThemeContrastTest {

  private fun assertAA(name: String, fg: Color, bg: Color) {
    val ratio = contrastRatio(fg, bg)
    assertTrue("$name contrast %.2f:1 < $AA_CONTRAST:1".format(ratio), ratio >= AA_CONTRAST)
  }

  private fun checkScheme(label: String, s: ColorScheme) {
    // Body & label text on the surfaces it sits on
    assertAA("$label onSurface/surface", s.onSurface, s.surface)
    assertAA("$label onSurface/background", s.onSurface, s.background)
    assertAA("$label onSurface/surfaceVariant", s.onSurface, s.surfaceVariant)
    assertAA("$label onSurfaceVariant/surface", s.onSurfaceVariant, s.surface)
    assertAA("$label onSurfaceVariant/surfaceVariant", s.onSurfaceVariant, s.surfaceVariant)
    assertAA("$label onSurfaceVariant/background", s.onSurfaceVariant, s.background)
    // Accent-colored text (labels, status, menu icons) on all chrome surfaces
    assertAA("$label primary/surface", s.primary, s.surface)
    assertAA("$label primary/background", s.primary, s.background)
    assertAA("$label primary/surfaceVariant", s.primary, s.surfaceVariant)
    assertAA("$label secondary/surface", s.secondary, s.surface)
    assertAA("$label tertiary/surface", s.tertiary, s.surface)
    assertAA("$label error/surface", s.error, s.surface)
    // Filled buttons / badges: content color on its container
    assertAA("$label onPrimary/primary", s.onPrimary, s.primary)
    assertAA("$label onSecondary/secondary", s.onSecondary, s.secondary)
    assertAA("$label onTertiary/tertiary", s.onTertiary, s.tertiary)
    assertAA("$label onError/error", s.onError, s.error)
    assertAA("$label onPrimaryContainer/primaryContainer", s.onPrimaryContainer, s.primaryContainer)
  }

  @Test
  fun allTerminalDerivedSchemesMeetAA() {
    TerminalThemes.allThemes().forEach { (_, scheme) ->
      checkScheme(scheme.name, appColorSchemeFromTerminal(scheme))
    }
  }

  @Test
  fun derivedSchemeKeepsTerminalBackgroundAsAppBackground() {
    TerminalThemes.allThemes().forEach { (_, scheme) ->
      assertEquals(scheme.name, scheme.background, appColorSchemeFromTerminal(scheme).background)
    }
  }

  @Test
  fun lightTerminalThemesProduceLightChrome() {
    val lightSchemes = TerminalThemes.allThemes().filter { (_, s) -> s.background.luminanceIsLight() }
    assertTrue("expected at least one light terminal palette", lightSchemes.isNotEmpty())
    lightSchemes.forEach { (_, scheme) ->
      assertTrue("${scheme.name} should derive a light scheme", !isDarkScheme(scheme))
    }
  }

  private fun Color.luminanceIsLight(): Boolean = contrastRatio(this, Color.White) < contrastRatio(this, Color.Black)
}
