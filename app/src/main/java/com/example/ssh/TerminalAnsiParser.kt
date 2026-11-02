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
  val segments: List<StyledSegment>,
  /**
   * Per-cell grid for strict monospace rendering. Continuation cells of wide
   * characters are included so column indices stay aligned with cell indices;
   * renderers must skip them (isContinuation == true).
   */
  val cells: List<CharCell> = emptyList()
) {
  val plainText: String
    get() = segments.joinToString("") { it.text }
}

data class CharCell(
  val char: Char,
  val fg: Color?,
  val bg: Color?,
  val bold: Boolean = false,
  val underline: Boolean = false,
  val isContinuation: Boolean = false
)

fun getEastAsianWidth(ch: Char): Int {
  if (ch < ' ') return 0
  if (ch in ' '..'~') return 1
  val code = ch.code
  if (code in 0x1100..0x115F ||
      code in 0x2E80..0x9FFF ||
      code in 0xA000..0xA4CF ||
      code in 0xAC00..0xD7A3 ||
      code in 0xF900..0xFAFF ||
      code in 0xFE10..0xFE19 ||
      code in 0xFE30..0xFE6F ||
      code in 0xFF00..0xFF60 ||
      code in 0xFFE0..0xFFE6 ||
      code in 0x20000..0x2FA1F) {
    return 2
  }
  return 1
}

class LineBuffer(initialCells: List<CharCell> = emptyList()) {
  val cells = ArrayList<CharCell>(initialCells)

  fun clone(): LineBuffer = LineBuffer(cells)

  fun clear() {
    cells.clear()
  }

  /** Discard cells beyond [newCols]; if the cut splits a wide char, blank its dangling primary too. */
  fun truncateTo(newCols: Int) {
    if (cells.size > newCols) {
      cells.subList(newCols, cells.size).clear()
      val lastIdx = cells.size - 1
      val lastCell = cells.getOrNull(lastIdx) ?: return
      if (!lastCell.isContinuation && getEastAsianWidth(lastCell.char) == 2) {
        // Wide char lost its continuation cell in the cut: blank it so nothing renders half-width
        cells[lastIdx] = CharCell(' ', null, null, false, false, false)
      }
    }
  }

  fun clearFrom(col: Int) {
    if (col < cells.size) {
      cells.subList(col, cells.size).clear()
    }
  }

  fun clearTo(col: Int) {
    val end = minOf(col, cells.size)
    for (i in 0 until end) {
      cells[i] = CharCell(' ', null, null, false, false, false)
    }
  }

  fun writeChar(
    col: Int,
    ch: Char,
    charWidth: Int,
    fg: Color?,
    bg: Color?,
    bold: Boolean,
    underline: Boolean
  ): Int {
    var c = col
    val primaryCell = CharCell(ch, fg, bg, bold, underline, isContinuation = false)
    if (c < cells.size) {
      cells[c] = primaryCell
    } else {
      while (cells.size < c) {
        cells.add(CharCell(' ', null, null, false, false, false))
      }
      cells.add(primaryCell)
    }
    c++

    if (charWidth == 2) {
      val contCell = CharCell('\u0000', fg, bg, bold, underline, isContinuation = true)
      if (c < cells.size) {
        cells[c] = contCell
      } else {
        cells.add(contCell)
      }
      c++
    }
    return c
  }

  fun write(col: Int, text: String, fg: Color?, bg: Color?, bold: Boolean, underline: Boolean): Int {
    var c = col
    for (i in 0 until text.length) {
      val ch = text[i]
      val w = getEastAsianWidth(ch)
      c = writeChar(c, ch, w, fg, bg, bold, underline)
    }
    return c
  }

  fun toTerminalLine(defaultFg: Color): TerminalLine {
    if (cells.isEmpty()) return TerminalLine(emptyList())

    // Trim trailing transparent spaces so they do not cause Compose to soft-wrap empty whitespace
    var lastVisibleIdx = cells.size - 1
    while (lastVisibleIdx >= 0) {
      val c = cells[lastVisibleIdx]
      val isTrailingSpace = (c.char == ' ' || c.char == '\u0000') && c.bg == null && !c.underline
      if (!isTrailingSpace) {
        break
      }
      lastVisibleIdx--
    }

    if (lastVisibleIdx < 0) {
      return TerminalLine(emptyList())
    }

    val segments = mutableListOf<StyledSegment>()
    var currentFg = cells[0].fg ?: defaultFg
    var currentBg = cells[0].bg
    var currentBold = cells[0].bold
    var currentUnderline = cells[0].underline
    val sb = java.lang.StringBuilder()

    for (i in 0..lastVisibleIdx) {
      val cell = cells[i]
      if (cell.isContinuation) continue

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
    // Include the raw cell grid (incl. continuation cells) for strict-grid rendering
    val grid = ArrayList<CharCell>(lastVisibleIdx + 1)
    for (i in 0..lastVisibleIdx) {
      grid.add(cells[i])
    }
    return TerminalLine(segments, grid)
  }
}

class TerminalAnsiParser(
  private var theme: TerminalColorScheme = TerminalThemes.Dracula,
  private val maxScrollback: Int = 1500
) {
  private val lock = ReentrantLock()

  private val _lines = MutableStateFlow<List<TerminalLine>>(emptyList())
  val lines: StateFlow<List<TerminalLine>> = _lines.asStateFlow()

  // Terminal active screen grid dimensions (updated by SIGWINCH)
  var cols: Int = 80
    private set
  var rows: Int = 24
    private set

  // Scrolling region (DECSTBM): top and bottom margin (0-indexed within screen)
  private var scrollTopMargin = 0
  private var scrollBottomMargin = 23

  // VT100 / xterm autowrap pending flag (DECAWM)
  private var wrapPending = false

  // True while the remote enabled mouse reporting (DECSET 1000/1002/1003),
  // e.g. tmux with `set -g mouse on`. Swipes can then be forwarded as wheel events.
  @Volatile var isMouseReporting: Boolean = false
    private set

  // True while the remote switched to the alternate screen buffer (DECSET 1049/47/1047),
  // e.g. tmux / vim / less. Scrollback lives on the remote then, so local swipes can
  // neither scroll the local buffer nor (without mouse reporting) reach remote history.
  @Volatile var isAlternateScreen: Boolean = false
    private set

  // Character cell line buffer
  private val linesList = mutableListOf<LineBuffer>().apply {
    add(LineBuffer())
  }
  private var cursorRow = 0
  private var cursorCol = 0

  // Saved cursor relative to screen (for ESC 7/8 or CSI s/u)
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

  fun resize(cols: Int, rows: Int) {
    lock.withLock {
      if (cols > 0 && rows > 0) {
        val oldCols = this.cols
        this.cols = cols
        this.rows = rows
        scrollTopMargin = 0
        scrollBottomMargin = rows - 1
        wrapPending = false
        if (cols < oldCols) {
          // Width shrink: stale cells beyond the new width MUST be discarded, otherwise
          // tmux's diff-based redraw (which only rewrites cells it thinks changed) leaves
          // fragments of the old wider content merged into the new rows.
          for (line in linesList) {
            line.truncateTo(cols)
          }
          altLinesSnapshot?.forEach { it.truncateTo(cols) }
        }
        // Clamp cursor into the new screen area
        val baseRow = getScreenBaseRow()
        if (cursorRow > baseRow + rows - 1) {
          cursorRow = baseRow + rows - 1
        }
        if (cursorCol > cols - 1) cursorCol = cols - 1
        publishLines()
      }
    }
  }

  private fun getScreenBaseRow(): Int = (linesList.size - rows).coerceAtLeast(0)

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
      wrapPending = false
      publishLines()
    }
  }

  private fun printChar(ch: Char) {
    val charW = getEastAsianWidth(ch)
    if (charW == 0) return

    val maxCols = cols.coerceAtLeast(10)

    if (wrapPending) {
      advanceNewline()
      wrapPending = false
    } else if (cursorCol + charW > maxCols) {
      advanceNewline()
    }

    val baseRow = getScreenBaseRow()
    while (cursorRow >= linesList.size) {
      linesList.add(LineBuffer())
    }
    val lineBuffer = linesList[cursorRow]

    cursorCol = lineBuffer.writeChar(
      col = cursorCol,
      ch = ch,
      charWidth = charW,
      fg = currentFg,
      bg = currentBg,
      bold = isBold,
      underline = isUnderline
    )

    if (cursorCol >= maxCols) {
      wrapPending = true
      cursorCol = maxCols - 1
    }
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
            wrapPending = false
            cursorCol = 0
            if (i + 1 < len && input[i + 1] == '\n') {
              i++
              advanceNewline()
            }
          }
          '\n' -> {
            wrapPending = false
            advanceNewline()
          }
          '\b' -> {
            wrapPending = false
            cursorCol = (cursorCol - 1).coerceAtLeast(0)
          }
          '\t' -> {
            wrapPending = false
            val maxCols = cols.coerceAtLeast(10)
            val nextTab = minOf(((cursorCol / 8) + 1) * 8, maxCols - 1)
            while (cursorCol < nextTab) {
              printChar(' ')
            }
          }
          '\u001b' -> { // ESC
            wrapPending = false
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
                  val baseRow = getScreenBaseRow()
                  savedRow = cursorRow - baseRow
                  savedCol = cursorCol
                  i++
                }
                '8' -> { // DECRC: Restore cursor
                  val baseRow = getScreenBaseRow()
                  val targetRow = baseRow + savedRow.coerceIn(0, rows - 1)
                  while (linesList.size <= targetRow) {
                    linesList.add(LineBuffer())
                  }
                  cursorRow = targetRow
                  cursorCol = savedCol.coerceAtLeast(0)
                  i++
                }
                'M' -> { // RI: Reverse Index - cursor up; scroll region down when at top margin.
                  // tmux relies on RI for cursor repositioning after region scrolls!
                  val baseRow = getScreenBaseRow()
                  val screenRow = cursorRow - baseRow
                  if (screenRow == scrollTopMargin) {
                    scrollRegionDown(1)
                    cursorRow = baseRow + scrollTopMargin
                  } else if (screenRow < scrollTopMargin) {
                    cursorRow = (cursorRow - 1).coerceAtLeast(baseRow)
                  } else {
                    cursorRow--
                  }
                  wrapPending = false
                  i++
                }
                'D' -> { // IND: Index - move down like LF, honors scroll region
                  advanceNewline()
                  i++
                }
                'E' -> { // NEL: Next Line - CR + LF
                  advanceNewline()
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
            printChar(c)
          }
        }
        i++
      }

      publishLines()
    }
  }

  private fun scrollRegionUp(n: Int) {
    val baseRow = getScreenBaseRow()
    val top = baseRow + scrollTopMargin
    while (linesList.size < baseRow + rows) {
      linesList.add(LineBuffer())
    }
    val bottom = (baseRow + scrollBottomMargin).coerceAtMost(linesList.size - 1)
    if (top >= bottom || top < 0 || bottom >= linesList.size) return

    val steps = n.coerceIn(1, scrollBottomMargin - scrollTopMargin + 1)
    if (scrollTopMargin == 0 && scrollBottomMargin >= rows - 1) {
      // Normal full-screen scroll: allow lines to scroll off top into scrollback.
      // Cursor is NOT moved here - xterm keeps it on the bottom margin row; callers adjust if needed.
      for (k in 0 until steps) {
        linesList.add(LineBuffer())
        if (linesList.size > maxScrollback) {
          linesList.removeAt(0)
        }
      }
    } else {
      // Restricted scrolling region (e.g. Tmux pane):
      // Lines outside the region (e.g. status bar) MUST stay strictly in place!
      for (k in 0 until steps) {
        if (top < linesList.size) {
          linesList.removeAt(top)
          linesList.add(bottom.coerceIn(0, linesList.size), LineBuffer())
        }
      }
    }
  }

  private fun scrollRegionDown(n: Int) {
    val baseRow = getScreenBaseRow()
    val top = baseRow + scrollTopMargin
    while (linesList.size < baseRow + rows) {
      linesList.add(LineBuffer())
    }
    val bottom = (baseRow + scrollBottomMargin).coerceAtMost(linesList.size - 1)
    if (top >= bottom || top < 0 || bottom >= linesList.size) return

    val steps = n.coerceIn(1, scrollBottomMargin - scrollTopMargin + 1)
    for (k in 0 until steps) {
      if (bottom < linesList.size) {
        linesList.removeAt(bottom)
        linesList.add(top.coerceIn(0, linesList.size), LineBuffer())
      }
    }
  }

  private fun advanceNewline() {
    val baseRow = getScreenBaseRow()
    val screenRow = cursorRow - baseRow
    if (screenRow == scrollBottomMargin) {
      scrollRegionUp(1)
      // After a full-screen scroll the buffer grew: re-anchor cursor to the bottom margin row
      cursorRow = getScreenBaseRow() + scrollBottomMargin
      while (cursorRow >= linesList.size) {
        linesList.add(LineBuffer())
      }
      cursorCol = 0
      return
    }
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
    wrapPending = false
    when (terminator) {
      'A' -> { // Cursor Up: \e[nA
        val n = params.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val baseRow = getScreenBaseRow()
        cursorRow = (cursorRow - n).coerceAtLeast(baseRow)
      }
      'B' -> { // Cursor Down: \e[nB
        val n = params.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val baseRow = getScreenBaseRow()
        val maxRow = baseRow + rows - 1
        while (linesList.size <= cursorRow + n) {
          linesList.add(LineBuffer())
        }
        cursorRow = (cursorRow + n).coerceAtMost(maxOf(linesList.size - 1, maxRow))
      }
      'C', 'a' -> { // Cursor Forward: \e[nC or \e[na
        val n = params.toIntOrNull()?.coerceAtLeast(1) ?: 1
        cursorCol = (cursorCol + n).coerceAtMost(cols - 1)
      }
      'D' -> { // Cursor Back: \e[nD
        val n = params.toIntOrNull()?.coerceAtLeast(1) ?: 1
        cursorCol = (cursorCol - n).coerceAtLeast(0)
      }
      'E' -> { // Cursor Next Line: \e[nE
        val n = params.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val baseRow = getScreenBaseRow()
        val maxRow = baseRow + rows - 1
        while (linesList.size <= cursorRow + n) {
          linesList.add(LineBuffer())
        }
        cursorRow = (cursorRow + n).coerceAtMost(maxOf(linesList.size - 1, maxRow))
        cursorCol = 0
      }
      'F' -> { // Cursor Previous Line: \e[nF
        val n = params.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val baseRow = getScreenBaseRow()
        cursorRow = (cursorRow - n).coerceAtLeast(baseRow)
        cursorCol = 0
      }
      'G' -> { // Cursor Column Absolute: \e[nG
        val n = (params.toIntOrNull() ?: 1) - 1
        cursorCol = n.coerceIn(0, cols - 1)
      }
      'd' -> { // Line Position Absolute: \e[nd
        val n = (params.toIntOrNull() ?: 1) - 1
        val baseRow = getScreenBaseRow()
        val targetRow = baseRow + n.coerceIn(0, rows - 1)
        while (linesList.size <= targetRow) {
          linesList.add(LineBuffer())
        }
        cursorRow = targetRow
      }
      'H', 'f' -> { // Cursor Position: \e[y;xH
        val parts = params.split(';')
        val r = (parts.getOrNull(0)?.toIntOrNull() ?: 1) - 1
        val c = (parts.getOrNull(1)?.toIntOrNull() ?: 1) - 1
        val baseRow = getScreenBaseRow()
        val targetRow = baseRow + r.coerceIn(0, rows - 1)
        while (linesList.size <= targetRow) {
          linesList.add(LineBuffer())
        }
        cursorRow = targetRow
        cursorCol = c.coerceIn(0, cols - 1)
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
      'J' -> { // Clear display: \e[0J, \e[1J, \e[2J, \e[3J
        val code = params.toIntOrNull() ?: 0
        val baseRow = getScreenBaseRow()
        when (code) {
          3 -> { // Erase saved lines (xterm scrollback clear)
            linesList.clear()
            linesList.add(LineBuffer())
            cursorRow = 0
            cursorCol = 0
          }
          2 -> { // Clear active visible screen without wiping previous scrollback history
            val endRow = minOf(linesList.size - 1, baseRow + rows - 1)
            for (r in baseRow..endRow) {
              linesList[r].clear()
            }
            cursorRow = baseRow
            cursorCol = 0
          }
          1 -> { // Erase from beginning of screen to cursor
            for (r in baseRow until cursorRow) {
              if (r < linesList.size) linesList[r].clear()
            }
            if (cursorRow < linesList.size) {
              linesList[cursorRow].clearTo(cursorCol)
            }
          }
          else -> { // Erase from cursor to end of screen
            if (cursorRow < linesList.size) {
              linesList[cursorRow].clearFrom(cursorCol)
            }
            val endRow = minOf(linesList.size - 1, baseRow + rows - 1)
            for (r in (cursorRow + 1)..endRow) {
              linesList[r].clear()
            }
          }
        }
      }
      'X' -> { // Erase characters: \e[nX
        val n = params.toIntOrNull()?.coerceAtLeast(1) ?: 1
        if (cursorRow < linesList.size) {
          for (c in 0 until n) {
            linesList[cursorRow].write(
              col = cursorCol + c,
              text = " ",
              fg = null,
              bg = null,
              bold = false,
              underline = false
            )
          }
        }
      }
      'L' -> { // Insert Line: \e[nL
        val n = params.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val baseRow = getScreenBaseRow()
        while (linesList.size < baseRow + rows) {
          linesList.add(LineBuffer())
        }
        val regionBottom = (baseRow + scrollBottomMargin).coerceAtMost(linesList.size - 1)
        if (cursorRow in (baseRow + scrollTopMargin)..regionBottom) {
          val steps = n.coerceIn(1, regionBottom - cursorRow + 1)
          for (k in 0 until steps) {
            if (regionBottom < linesList.size) {
              linesList.removeAt(regionBottom)
            }
            linesList.add(cursorRow.coerceIn(0, linesList.size), LineBuffer())
          }
        }
        cursorCol = 0
      }
      'M' -> { // Delete Line: \e[nM
        val n = params.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val baseRow = getScreenBaseRow()
        while (linesList.size < baseRow + rows) {
          linesList.add(LineBuffer())
        }
        val regionBottom = (baseRow + scrollBottomMargin).coerceAtMost(linesList.size - 1)
        if (cursorRow in (baseRow + scrollTopMargin)..regionBottom) {
          val steps = n.coerceIn(1, regionBottom - cursorRow + 1)
          for (k in 0 until steps) {
            if (cursorRow < linesList.size) {
              linesList.removeAt(cursorRow)
              linesList.add(regionBottom.coerceIn(0, linesList.size), LineBuffer())
            }
          }
        }
        cursorCol = 0
      }
      'S' -> { // Scroll Up: \e[nS
        val n = params.toIntOrNull()?.coerceAtLeast(1) ?: 1
        scrollRegionUp(n)
      }
      'T' -> { // Scroll Down: \e[nT
        val n = params.toIntOrNull()?.coerceAtLeast(1) ?: 1
        scrollRegionDown(n)
      }
      'r' -> { // Set Scrolling Region: \e[top;bottomr
        val parts = params.split(';')
        val top = (parts.getOrNull(0)?.toIntOrNull() ?: 1) - 1
        val bottom = (parts.getOrNull(1)?.toIntOrNull() ?: rows) - 1
        scrollTopMargin = top.coerceIn(0, rows - 1)
        scrollBottomMargin = bottom.coerceIn(scrollTopMargin, rows - 1)
        val baseRow = getScreenBaseRow()
        while (linesList.size < baseRow + rows) {
          linesList.add(LineBuffer())
        }
        cursorRow = (baseRow + scrollTopMargin).coerceIn(0, (linesList.size - 1).coerceAtLeast(0))
        cursorCol = 0
      }
      'h' -> { // Private mode: \e[?1049h, \e[?1047h, \e[?47h (Alternate screen buffer)
        if (params == "?1000" || params == "?1002" || params == "?1003") {
          // Mouse reporting enabled (e.g. tmux `set -g mouse on`): the app may forward
          // touch swipes as SGR wheel events.
          isMouseReporting = true
        }
        if (params == "?1049" || params == "?47" || params == "?1047") {
          isAlternateScreen = true
          altLinesSnapshot = linesList.map { it.clone() }
          altCursorRow = cursorRow
          altCursorCol = cursorCol
          linesList.clear()
          linesList.add(LineBuffer())
          cursorRow = 0
          cursorCol = 0
          scrollTopMargin = 0
          scrollBottomMargin = rows - 1
        }
      }
      'l' -> { // Private mode: \e[?1049l, \e[?1047l, \e[?47l (Restore main screen buffer)
        if (params == "?1000" || params == "?1002" || params == "?1003") {
          isMouseReporting = false
        }
        if (params == "?1049" || params == "?47" || params == "?1047") {
          isAlternateScreen = false
          altLinesSnapshot?.let { snapshot ->
            linesList.clear()
            linesList.addAll(snapshot.map { it.clone() })
            cursorRow = altCursorRow.coerceIn(0, (linesList.size - 1).coerceAtLeast(0))
            cursorCol = altCursorCol
            altLinesSnapshot = null
            scrollTopMargin = 0
            scrollBottomMargin = rows - 1
          }
        }
      }
      's' -> { // Save cursor: \e[s
        val baseRow = getScreenBaseRow()
        savedRow = cursorRow - baseRow
        savedCol = cursorCol
      }
      'u' -> { // Restore cursor: \e[u
        val baseRow = getScreenBaseRow()
        val targetRow = baseRow + savedRow.coerceIn(0, rows - 1)
        while (linesList.size <= targetRow) {
          linesList.add(LineBuffer())
        }
        cursorRow = targetRow
        cursorCol = savedCol.coerceAtLeast(0)
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
