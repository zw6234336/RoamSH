package com.example.ssh

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

data class StyledSegment(
  val text: String,
  val fgColor: Color?,
  val bgColor: Color?,
  val bold: Boolean = false,
  val underline: Boolean = false
)

data class TerminalLine(
  val segments: List<StyledSegment>
) {
  val plainText: String
    get() = segments.joinToString("") { it.text }
}

data class CharCell(
  val char: Char,
  val fg: Color?,
  val bg: Color?,
  val bold: Boolean = false,
  val underline: Boolean = false
)

class LineBuffer(initialCells: List<CharCell> = emptyList()) {
  val cells = ArrayList<CharCell>(initialCells)

  fun clone(): LineBuffer = LineBuffer(cells)

  fun clear() {
    cells.clear()
  }

  fun clearFrom(col: Int) {
    if (col < cells.size) {
      cells.subList(col, cells.size).clear()
    }
  }

  fun clearTo(col: Int) {
    val end = minOf(col, cells.size)
    for (i in 0 until end) {
      cells[i] = CharCell(' ', null, null, false, false)
    }
  }

  fun write(col: Int, text: String, fg: Color?, bg: Color?, bold: Boolean, underline: Boolean): Int {
    var c = col
    for (i in 0 until text.length) {
      val ch = text[i]
      val cell = CharCell(ch, fg, bg, bold, underline)
      if (c < cells.size) {
        cells[c] = cell
      } else {
        while (cells.size < c) {
          cells.add(CharCell(' ', null, null, false, false))
        }
        cells.add(cell)
      }
      c++
    }
    return c
  }

  fun toTerminalLine(defaultFg: Color): TerminalLine {
    if (cells.isEmpty()) return TerminalLine(emptyList())

    val segments = mutableListOf<StyledSegment>()
    var currentFg = cells[0].fg ?: defaultFg
    var currentBg = cells[0].bg
    var currentBold = cells[0].bold
    var currentUnderline = cells[0].underline
    val sb = java.lang.StringBuilder()

    for (i in 0 until cells.size) {
      val cell = cells[i]
      val cellFg = cell.fg ?: defaultFg
      if (cellFg == currentFg && cell.bg == currentBg && cell.bold == currentBold && cell.underline == currentUnderline) {
        sb.append(cell.char)
      } else {
        if (sb.isNotEmpty()) {
          segments.add(
            StyledSegment(
              text = sb.toString(),
              fgColor = currentFg,
              bgColor = currentBg,
              bold = currentBold,
              underline = currentUnderline
            )
          )
          sb.setLength(0)
        }
        currentFg = cellFg
        currentBg = cell.bg
        currentBold = cell.bold
        currentUnderline = cell.underline
        sb.append(cell.char)
      }
    }
    if (sb.isNotEmpty()) {
      segments.add(
        StyledSegment(
          text = sb.toString(),
          fgColor = currentFg,
          bgColor = currentBg,
          bold = currentBold,
          underline = currentUnderline
        )
      )
    }
    return TerminalLine(segments)
  }
}

class TerminalAnsiParser(
  private var theme: TerminalColorScheme = TerminalThemes.Dracula,
  private val maxScrollback: Int = 1500
) {
  private val lock = ReentrantLock()

  private val _lines = MutableStateFlow<List<TerminalLine>>(emptyList())
  val lines: StateFlow<List<TerminalLine>> = _lines.asStateFlow()

  // Character cell line buffer
  private val linesList = mutableListOf<LineBuffer>().apply {
    add(LineBuffer())
  }
  private var cursorRow = 0
  private var cursorCol = 0

  // Saved cursor for ESC 7/8 or CSI s/u
  private var savedRow = 0
  private var savedCol = 0

  // Alternate screen buffer (for vim, htop, less, full-screen AI TUIs)
  private var altLinesSnapshot: List<LineBuffer>? = null
  private var altCursorRow = 0
  private var altCursorCol = 0

  // Cursor styling state
  private var currentFg: Color? = null
  private var currentBg: Color? = null
  private var isBold = false
  private var isUnderline = false

  fun setTheme(newTheme: TerminalColorScheme) {
    lock.withLock {
      theme = newTheme
      publishLines()
    }
  }

  fun clear() {
    lock.withLock {
      linesList.clear()
      linesList.add(LineBuffer())
      cursorRow = 0
      cursorCol = 0
      publishLines()
    }
  }

  private val textBuffer = java.lang.StringBuilder()

  private fun flushText() {
    if (textBuffer.isEmpty()) return
    val text = textBuffer.toString()
    textBuffer.setLength(0)

    while (cursorRow >= linesList.size) {
      linesList.add(LineBuffer())
    }
    val lineBuffer = linesList[cursorRow]
    cursorCol = lineBuffer.write(
      col = cursorCol,
      text = text,
      fg = currentFg,
      bg = currentBg,
      bold = isBold,
      underline = isUnderline
    )
  }

  fun feed(input: String) {
    if (input.isEmpty()) return
    lock.withLock {
      val len = input.length
      var i = 0

      while (i < len) {
        val c = input[i]

        when (c) {
          '\r' -> {
            flushText()
            cursorCol = 0
            if (i + 1 < len && input[i + 1] == '\n') {
              i++
              advanceNewline()
            }
          }
          '\n' -> {
            flushText()
            advanceNewline()
          }
          '\b' -> {
            flushText()
            cursorCol = (cursorCol - 1).coerceAtLeast(0)
          }
          '\t' -> {
            flushText()
            val nextTab = ((cursorCol / 8) + 1) * 8
            val spaces = nextTab - cursorCol
            for (s in 0 until spaces) {
              textBuffer.append(' ')
            }
          }
          '\u001b' -> { // ESC
            flushText()
            if (i + 1 < len) {
              val next = input[i + 1]
              when (next) {
                '[' -> { // CSI sequence: ESC [ ... [letter]
                  val seqStart = i + 2
                  var seqEnd = seqStart
                  while (seqEnd < len && !isCsiTerminator(input[seqEnd])) {
                    seqEnd++
                  }
                  if (seqEnd < len) {
                    val terminator = input[seqEnd]
                    val params = input.substring(seqStart, seqEnd)
                    handleCsi(params, terminator)
                    i = seqEnd
                  } else {
                    i = len
                  }
                }
                ']' -> { // OSC sequence: ESC ] ... BEL or ST
                  var oscEnd = i + 2
                  while (oscEnd < len && input[oscEnd] != '\u0007' && input[oscEnd] != '\u001b') {
                    oscEnd++
                  }
                  i = if (oscEnd < len && input[oscEnd] == '\u0007') oscEnd else oscEnd + 1
                }
                '(' , ')' -> { // Character set selection
                  i += 2
                }
                '7' -> { // DECSC: Save cursor
                  savedRow = cursorRow
                  savedCol = cursorCol
                  i++
                }
                '8' -> { // DECRC: Restore cursor
                  cursorRow = savedRow.coerceIn(0, (linesList.size - 1).coerceAtLeast(0))
                  cursorCol = savedCol
                  i++
                }
                else -> {
                  i++
                }
              }
            }
          }
          '\u0007' -> { // BEL - ignore
          }
          else -> {
            textBuffer.append(c)
          }
        }
        i++
      }

      flushText()
      publishLines()
    }
  }

  private fun advanceNewline() {
    cursorRow++
    cursorCol = 0
    while (cursorRow >= linesList.size) {
      linesList.add(LineBuffer())
    }
    if (linesList.size > maxScrollback) {
      linesList.removeAt(0)
      cursorRow = (cursorRow - 1).coerceAtLeast(0)
    }
  }

  private fun isCsiTerminator(c: Char): Boolean {
    return c in '@'..'~'
  }

  private fun handleCsi(params: String, terminator: Char) {
    when (terminator) {
      'A' -> { // Cursor Up: \e[nA
        val n = params.toIntOrNull()?.coerceAtLeast(1) ?: 1
        cursorRow = (cursorRow - n).coerceAtLeast(0)
      }
      'B' -> { // Cursor Down: \e[nB
        val n = params.toIntOrNull()?.coerceAtLeast(1) ?: 1
        while (linesList.size <= cursorRow + n) {
          linesList.add(LineBuffer())
        }
        cursorRow = (cursorRow + n).coerceAtMost(linesList.size - 1)
      }
      'C' -> { // Cursor Forward: \e[nC
        val n = params.toIntOrNull()?.coerceAtLeast(1) ?: 1
        cursorCol += n
      }
      'D' -> { // Cursor Back: \e[nD
        val n = params.toIntOrNull()?.coerceAtLeast(1) ?: 1
        cursorCol = (cursorCol - n).coerceAtLeast(0)
      }
      'G' -> { // Cursor Column Absolute: \e[nG
        val n = (params.toIntOrNull() ?: 1) - 1
        cursorCol = n.coerceAtLeast(0)
      }
      'H', 'f' -> { // Cursor Position: \e[y;xH
        val parts = params.split(';')
        val r = (parts.getOrNull(0)?.toIntOrNull() ?: 1) - 1
        val c = (parts.getOrNull(1)?.toIntOrNull() ?: 1) - 1
        while (linesList.size <= r) {
          linesList.add(LineBuffer())
        }
        cursorRow = r.coerceIn(0, (linesList.size - 1).coerceAtLeast(0))
        cursorCol = c.coerceAtLeast(0)
      }
      'K' -> { // Erase in line: \e[2K (entire line), \e[1K (start to cursor), \e[0K / \e[K (cursor to end)
        if (cursorRow < linesList.size) {
          when (params) {
            "2" -> linesList[cursorRow].clear()
            "1" -> linesList[cursorRow].clearTo(cursorCol)
            else -> linesList[cursorRow].clearFrom(cursorCol)
          }
        }
      }
      'J' -> { // Clear display: \e[2J, \e[3J
        val code = params.toIntOrNull() ?: 0
        if (code == 2 || code == 3) {
          linesList.clear()
          linesList.add(LineBuffer())
          cursorRow = 0
          cursorCol = 0
        } else if (code == 0) {
          if (cursorRow < linesList.size) {
            linesList[cursorRow].clearFrom(cursorCol)
            while (linesList.size > cursorRow + 1) {
              linesList.removeAt(linesList.size - 1)
            }
          }
        }
      }
      'h' -> { // Private mode: \e[?1049h (Alternate screen buffer)
        if (params == "?1049" || params == "?47") {
          altLinesSnapshot = linesList.map { it.clone() }
          altCursorRow = cursorRow
          altCursorCol = cursorCol
          linesList.clear()
          linesList.add(LineBuffer())
          cursorRow = 0
          cursorCol = 0
        }
      }
      'l' -> { // Private mode: \e[?1049l (Restore main screen buffer)
        if (params == "?1049" || params == "?47") {
          altLinesSnapshot?.let { snapshot ->
            linesList.clear()
            linesList.addAll(snapshot.map { it.clone() })
            cursorRow = altCursorRow.coerceIn(0, (linesList.size - 1).coerceAtLeast(0))
            cursorCol = altCursorCol
            altLinesSnapshot = null
          }
        }
      }
      's' -> { // Save cursor: \e[s
        savedRow = cursorRow
        savedCol = cursorCol
      }
      'u' -> { // Restore cursor: \e[u
        cursorRow = savedRow.coerceIn(0, (linesList.size - 1).coerceAtLeast(0))
        cursorCol = savedCol
      }
      'm' -> { // SGR - Graphics rendition (colors, styling)
        if (params.isEmpty()) {
          resetStyle()
          return
        }
        val codes = params.split(';').mapNotNull { it.toIntOrNull() }
        var idx = 0
        while (idx < codes.size) {
          when (val code = codes[idx]) {
            0 -> resetStyle()
            1 -> isBold = true
            4 -> isUnderline = true
            22 -> isBold = false
            24 -> isUnderline = false
            in 30..37 -> {
              currentFg = theme.getColor(code - 30, isBright = false)
            }
            38 -> {
              if (idx + 2 < codes.size && codes[idx + 1] == 5) {
                currentFg = parse256Color(codes[idx + 2])
                idx += 2
              } else if (idx + 4 < codes.size && codes[idx + 1] == 2) {
                currentFg = Color(codes[idx + 2], codes[idx + 3], codes[idx + 4])
                idx += 4
              }
            }
            39 -> currentFg = null // Default FG
            in 40..47 -> {
              currentBg = theme.getColor(code - 40, isBright = false)
            }
            48 -> {
              if (idx + 2 < codes.size && codes[idx + 1] == 5) {
                currentBg = parse256Color(codes[idx + 2])
                idx += 2
              } else if (idx + 4 < codes.size && codes[idx + 1] == 2) {
                currentBg = Color(codes[idx + 2], codes[idx + 3], codes[idx + 4])
                idx += 4
              }
            }
            49 -> currentBg = null // Default BG
            in 90..97 -> { // Bright FG
              currentFg = theme.getColor(code - 90, isBright = true)
            }
            in 100..107 -> { // Bright BG
              currentBg = theme.getColor(code - 100, isBright = true)
            }
          }
          idx++
        }
      }
    }
  }

  private fun resetStyle() {
    currentFg = null
    currentBg = null
    isBold = false
    isUnderline = false
  }

  private fun parse256Color(n: Int): Color {
    return when {
      n in 0..7 -> theme.getColor(n, false)
      n in 8..15 -> theme.getColor(n - 8, true)
      n in 16..231 -> {
        val v = n - 16
        val b = (v % 6) * 51
        val g = ((v / 6) % 6) * 51
        val r = (v / 36) * 51
        Color(r, g, b)
      }
      n in 232..255 -> {
        val gray = 8 + (n - 232) * 10
        Color(gray, gray, gray)
      }
      else -> theme.foreground
    }
  }

  private fun publishLines() {
    val defaultFg = theme.foreground
    val snapshot = linesList.map { it.toTerminalLine(defaultFg) }
    _lines.value = snapshot
  }
}
