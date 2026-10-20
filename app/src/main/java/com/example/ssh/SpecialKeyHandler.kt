package com.example.ssh

object SpecialKeyHandler {
  const val ESC = "\u001b"
  const val TAB = "\t"
  const val ENTER = "\r"
  const val BACKSPACE = "\u007f"

  const val ARROW_UP = "\u001b[A"
  const val ARROW_DOWN = "\u001b[B"
  const val ARROW_RIGHT = "\u001b[C"
  const val ARROW_LEFT = "\u001b[D"

  const val HOME = "\u001b[H"
  const val END = "\u001b[F"
  const val PAGE_UP = "\u001b[5~"
  const val PAGE_DOWN = "\u001b[6~"
  const val INSERT = "\u001b[2~"
  const val DELETE = "\u001b[3~"

  // Quick Ctrl combinations
  const val CTRL_C = "\u0003" // SIGINT
  const val CTRL_D = "\u0004" // EOF
  const val CTRL_Z = "\u001a" // SIGTSTP
  const val CTRL_L = "\u000c" // Clear screen
  const val CTRL_R = "\u0012" // Reverse search
  const val CTRL_A = "\u0001" // Line start
  const val CTRL_E = "\u0005" // Line end
  const val CTRL_U = "\u0015" // Cut before cursor
  const val CTRL_K = "\u000b" // Cut after cursor
  const val CTRL_W = "\u0017" // Delete word
  const val CTRL_B = "\u0002" // Tmux prefix default

  fun ctrl(char: Char): String {
    val upper = char.uppercaseChar()
    return if (upper in 'A'..'Z') {
      val code = upper.code - 'A'.code + 1
      code.toChar().toString()
    } else if (char == '[') {
      "\u001b"
    } else if (char == '\\') {
      "\u001c"
    } else if (char == ']') {
      "\u001d"
    } else {
      char.toString()
    }
  }

  fun alt(char: Char): String {
    return "$ESC$char"
  }

  fun fKey(number: Int): String {
    return when (number) {
      1 -> "\u001bOP"
      2 -> "\u001bOQ"
      3 -> "\u001bOR"
      4 -> "\u001bOS"
      5 -> "\u001b[15~"
      6 -> "\u001b[17~"
      7 -> "\u001b[18~"
      8 -> "\u001b[19~"
      9 -> "\u001b[20~"
      10 -> "\u001b[21~"
      11 -> "\u001b[23~"
      12 -> "\u001b[24~"
      else -> ""
    }
  }

  // SGR mouse encoding for Tmux / terminal mouse mode (mouse on)
  fun mouseWheelUp(col: Int = 1, row: Int = 1): String = "\u001b[<64;$col;${row}M"
  fun mouseWheelDown(col: Int = 1, row: Int = 1): String = "\u001b[<65;$col;${row}M"
  fun mouseClick(col: Int = 1, row: Int = 1): String = "\u001b[<0;$col;${row}M\u001b[<0;$col;${row}m"
}
