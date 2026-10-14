package com.example.ssh

import androidx.compose.ui.graphics.Color
import com.example.data.model.TerminalThemeType

data class TerminalColorScheme(
  val name: String,
  val background: Color,
  val foreground: Color,
  val cursor: Color,
  val black: Color,
  val red: Color,
  val green: Color,
  val yellow: Color,
  val blue: Color,
  val magenta: Color,
  val cyan: Color,
  val white: Color,
  val brightBlack: Color,
  val brightRed: Color,
  val brightGreen: Color,
  val brightYellow: Color,
  val brightBlue: Color,
  val brightMagenta: Color,
  val brightCyan: Color,
  val brightWhite: Color
) {
  fun getColor(ansiIndex: Int, isBright: Boolean = false): Color {
    return when (ansiIndex) {
      0 -> if (isBright) brightBlack else black
      1 -> if (isBright) brightRed else red
      2 -> if (isBright) brightGreen else green
      3 -> if (isBright) brightYellow else yellow
      4 -> if (isBright) brightBlue else blue
      5 -> if (isBright) brightMagenta else magenta
      6 -> if (isBright) brightCyan else cyan
      7 -> if (isBright) brightWhite else white
      else -> foreground
    }
  }
}

object TerminalThemes {
  val Dracula = TerminalColorScheme(
    name = "Dracula",
    background = Color(0xFF282A36),
    foreground = Color(0xFFF8F8F2),
    cursor = Color(0xFFFF79C6),
    black = Color(0xFF21222C),
    red = Color(0xFFFF5555),
    green = Color(0xFF50FA7B),
    yellow = Color(0xFFF1FA8C),
    blue = Color(0xFFBD93F9),
    magenta = Color(0xFFFF79C6),
    cyan = Color(0xFF8BE9FD),
    white = Color(0xFFBFBFBF),
    brightBlack = Color(0xFF6272A4),
    brightRed = Color(0xFFFF6E6E),
    brightGreen = Color(0xFF69FF94),
    brightYellow = Color(0xFFFFFFA5),
    brightBlue = Color(0xFFD6ACFF),
    brightMagenta = Color(0xFFFF92DF),
    brightCyan = Color(0xFFA4FFFF),
    brightWhite = Color(0xFFFFFFFF)
  )

  val Monokai = TerminalColorScheme(
    name = "Monokai",
    background = Color(0xFF272822),
    foreground = Color(0xFFF8F8F2),
    cursor = Color(0xFFF8F8F0),
    black = Color(0xFF272822),
    red = Color(0xFFF92672),
    green = Color(0xFFA6E22E),
    yellow = Color(0xFFF4BF75),
    blue = Color(0xFF66D9EF),
    magenta = Color(0xFFAE81FF),
    cyan = Color(0xFFA1EFE4),
    white = Color(0xFFF8F8F2),
    brightBlack = Color(0xFF75715E),
    brightRed = Color(0xFFF92672),
    brightGreen = Color(0xFFA6E22E),
    brightYellow = Color(0xFFE6DB74),
    brightBlue = Color(0xFF66D9EF),
    brightMagenta = Color(0xFFAE81FF),
    brightCyan = Color(0xFFA1EFE4),
    brightWhite = Color(0xFFF9F8F5)
  )

  val SolarizedDark = TerminalColorScheme(
    name = "Solarized Dark",
    background = Color(0xFF002B36),
    foreground = Color(0xFF839496),
    cursor = Color(0xFF93A1A1),
    black = Color(0xFF073642),
    red = Color(0xFFDC322F),
    green = Color(0xFF859900),
    yellow = Color(0xFFB58900),
    blue = Color(0xFF268BD2),
    magenta = Color(0xFFD33682),
    cyan = Color(0xFF2AA198),
    white = Color(0xFFEEE8D5),
    brightBlack = Color(0xFF586E75),
    brightRed = Color(0xFFCB4B16),
    brightGreen = Color(0xFF586E75),
    brightYellow = Color(0xFF657B83),
    brightBlue = Color(0xFF839496),
    brightMagenta = Color(0xFF6C71C4),
    brightCyan = Color(0xFF93A1A1),
    brightWhite = Color(0xFFFDF6E3)
  )

  val Nord = TerminalColorScheme(
    name = "Nord",
    background = Color(0xFF2E3440),
    foreground = Color(0xFFD8DEE9),
    cursor = Color(0xFFECEFF4),
    black = Color(0xFF3B4252),
    red = Color(0xFFBF616A),
    green = Color(0xFFA3BE8C),
    yellow = Color(0xFFEBCB8B),
    blue = Color(0xFF81A1C1),
    magenta = Color(0xFFB48EAD),
    cyan = Color(0xFF88C0D0),
    white = Color(0xFFE5E9F0),
    brightBlack = Color(0xFF4C566A),
    brightRed = Color(0xFFBF616A),
    brightGreen = Color(0xFFA3BE8C),
    brightYellow = Color(0xFFEBCB8B),
    brightBlue = Color(0xFF81A1C1),
    brightMagenta = Color(0xFFB48EAD),
    brightCyan = Color(0xFF8FBCBB),
    brightWhite = Color(0xFFECEFF4)
  )

  val RetroMatrix = TerminalColorScheme(
    name = "Retro Matrix",
    background = Color(0xFF050B05),
    foreground = Color(0xFF22C55E),
    cursor = Color(0xFF4ADE80),
    black = Color(0xFF0F1E0F),
    red = Color(0xFFEF4444),
    green = Color(0xFF16A34A),
    yellow = Color(0xFF84CC16),
    blue = Color(0xFF0D9488),
    magenta = Color(0xFF10B981),
    cyan = Color(0xFF22C55E),
    white = Color(0xFFDCFCE7),
    brightBlack = Color(0xFF1B381B),
    brightRed = Color(0xFFF87171),
    brightGreen = Color(0xFF4ADE80),
    brightYellow = Color(0xFFA3E635),
    brightBlue = Color(0xFF14B8A6),
    brightMagenta = Color(0xFF34D399),
    brightCyan = Color(0xFF86EFAC),
    brightWhite = Color(0xFFF0FDF4)
  )

  val Cyberpunk = TerminalColorScheme(
    name = "Cyberpunk",
    background = Color(0xFF120E24),
    foreground = Color(0xFF00FFCC),
    cursor = Color(0xFFFF007F),
    black = Color(0xFF1B1438),
    red = Color(0xFFFF0055),
    green = Color(0xFF00FF9F),
    yellow = Color(0xFFFFEA00),
    blue = Color(0xFF00B8FF),
    magenta = Color(0xFFFF007F),
    cyan = Color(0xFF00FFFF),
    white = Color(0xFFECE6FF),
    brightBlack = Color(0xFF423378),
    brightRed = Color(0xFFFF3377),
    brightGreen = Color(0xFF33FFB2),
    brightYellow = Color(0xFFFFEE33),
    brightBlue = Color(0xFF33C6FF),
    brightMagenta = Color(0xFFFF3399),
    brightCyan = Color(0xFF33FFFF),
    brightWhite = Color(0xFFFFFFFF)
  )

  val GruvboxDark = TerminalColorScheme(
    name = "Gruvbox Dark",
    background = Color(0xFF282828),
    foreground = Color(0xFFEBDBB2),
    cursor = Color(0xFFFE8019),
    black = Color(0xFF282828),
    red = Color(0xFFCC241D),
    green = Color(0xFF98971A),
    yellow = Color(0xFFD79921),
    blue = Color(0xFF458588),
    magenta = Color(0xFFB16286),
    cyan = Color(0xFF689D6A),
    white = Color(0xFFA89984),
    brightBlack = Color(0xFF928374),
    brightRed = Color(0xFFFB4934),
    brightGreen = Color(0xFFB8BB26),
    brightYellow = Color(0xFFFABD2F),
    brightBlue = Color(0xFF83A598),
    brightMagenta = Color(0xFFD3869B),
    brightCyan = Color(0xFF8EC07C),
    brightWhite = Color(0xFFEBDBB2)
  )

  val GruvboxLight = TerminalColorScheme(
    name = "Gruvbox Light",
    background = Color(0xFFFBF1C7),
    foreground = Color(0xFF3C3836),
    cursor = Color(0xFFAF3A03),
    black = Color(0xFFFBF1C7),
    red = Color(0xFFCC241D),
    green = Color(0xFF98971A),
    yellow = Color(0xFFD79921),
    blue = Color(0xFF458588),
    magenta = Color(0xFFB16286),
    cyan = Color(0xFF689D6A),
    white = Color(0xFF7C6F64),
    brightBlack = Color(0xFF928374),
    brightRed = Color(0xFF9D0006),
    brightGreen = Color(0xFF79740E),
    brightYellow = Color(0xFFB57614),
    brightBlue = Color(0xFF076678),
    brightMagenta = Color(0xFF8F3F71),
    brightCyan = Color(0xFF427B58),
    brightWhite = Color(0xFF3C3836)
  )

  val SolarizedLight = TerminalColorScheme(
    name = "Solarized Light",
    background = Color(0xFFFDF6E3),
    foreground = Color(0xFF657B83),
    cursor = Color(0xFF586E75),
    black = Color(0xFF073642),
    red = Color(0xFFDC322F),
    green = Color(0xFF859900),
    yellow = Color(0xFFB58900),
    blue = Color(0xFF268BD2),
    magenta = Color(0xFFD33682),
    cyan = Color(0xFF2AA198),
    white = Color(0xFFEEE8D5),
    brightBlack = Color(0xFF002B36),
    brightRed = Color(0xFFCB4B16),
    brightGreen = Color(0xFF586E75),
    brightYellow = Color(0xFF657B83),
    brightBlue = Color(0xFF839496),
    brightMagenta = Color(0xFF6C71C4),
    brightCyan = Color(0xFF93A1A1),
    brightWhite = Color(0xFFFDF6E3)
  )

  val OneDark = TerminalColorScheme(
    name = "One Dark Pro",
    background = Color(0xFF1E222A),
    foreground = Color(0xFFABB2BF),
    cursor = Color(0xFF528BFF),
    black = Color(0xFF1E222A),
    red = Color(0xFFE06C75),
    green = Color(0xFF98C379),
    yellow = Color(0xFFE5C07B),
    blue = Color(0xFF61AFEF),
    magenta = Color(0xFFC678DD),
    cyan = Color(0xFF56B6C2),
    white = Color(0xFFABB2BF),
    brightBlack = Color(0xFF5C6370),
    brightRed = Color(0xFFBE5046),
    brightGreen = Color(0xFF98C379),
    brightYellow = Color(0xFFD19A66),
    brightBlue = Color(0xFF61AFEF),
    brightMagenta = Color(0xFFC678DD),
    brightCyan = Color(0xFF56B6C2),
    brightWhite = Color(0xFFFFFFFF)
  )

  fun get(type: TerminalThemeType): TerminalColorScheme {
    return when (type) {
      TerminalThemeType.DRACULA -> Dracula
      TerminalThemeType.SOLARIZED_DARK -> SolarizedDark
      TerminalThemeType.GRUVBOX_DARK -> GruvboxDark
      TerminalThemeType.GRUVBOX_LIGHT -> GruvboxLight
      TerminalThemeType.SOLARIZED_LIGHT -> SolarizedLight
      TerminalThemeType.ONE_DARK -> OneDark
      TerminalThemeType.MONOKAI -> Monokai
      TerminalThemeType.NORD -> Nord
      TerminalThemeType.RETRO_MATRIX -> RetroMatrix
      TerminalThemeType.CYBERPUNK -> Cyberpunk
    }
  }

  fun allThemes(): List<Pair<TerminalThemeType, TerminalColorScheme>> {
    return TerminalThemeType.entries.map { it to get(it) }
  }
}
