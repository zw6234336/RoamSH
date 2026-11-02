package com.example

import com.example.ssh.TerminalAnsiParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalParserTest {

  @Test
  fun testAutoWrapOnLongLines() {
    val parser = TerminalAnsiParser()
    parser.resize(cols = 40, rows = 20)

    // Send a 100-character line without explicit newlines (typical of long AI streaming responses)
    val text = "A".repeat(100)
    parser.feed(text)

    val lines = parser.lines.value
    // 100 characters wrapped at cols=40 should span 3 lines: 40 + 40 + 20
    assertEquals(3, lines.size)
    assertEquals(40, lines[0].plainText.length)
    assertEquals(40, lines[1].plainText.length)
    assertEquals(20, lines[2].plainText.length)
  }

  @Test
  fun testViewportRelativeCursorAddressing() {
    val parser = TerminalAnsiParser()
    parser.resize(cols = 80, rows = 10)

    // Fill 30 lines into scrollback buffer
    for (i in 1..30) {
      parser.feed("Line $i\r\n")
    }

    val linesBefore = parser.lines.value
    assertEquals(31, linesBefore.size)
    assertEquals("Line 1", linesBefore[0].plainText)

    // Now send Cursor Home \e[1;1H (or \e[H) - in VT100 this must target row 1 of the ACTIVE screen (Line 22), NOT Line 1 in scrollback!
    parser.feed("\u001b[1;1HActiveScreenTop")

    val linesAfter = parser.lines.value
    // Line 1 in scrollback must NOT be overwritten!
    assertEquals("Line 1", linesAfter[0].plainText)
    // The active screen row (index 21) should be written to
    val activeTopLine = linesAfter[21].plainText
    assertTrue("Expected active screen top line to contain 'ActiveScreenTop', got: '$activeTopLine'", activeTopLine.contains("ActiveScreenTop"))
  }

  @Test
  fun testClearVisibleScreenPreservesScrollback() {
    val parser = TerminalAnsiParser()
    parser.resize(cols = 80, rows = 10)

    // Feed 20 lines
    for (i in 1..20) {
      parser.feed("History $i\r\n")
    }

    val before = parser.lines.value
    assertTrue(before.size >= 20)

    // \e[2J (Clear active screen) - MUST NOT wipe scrollback history!
    parser.feed("\u001b[2J")

    val after = parser.lines.value
    // History 1 should still be preserved in scrollback!
    assertEquals("History 1", after[0].plainText)

    // \e[3J (Explicit xterm clear scrollback) - WILL clear scrollback
    parser.feed("\u001b[3J")
    val after3J = parser.lines.value
    assertEquals(1, after3J.size)
  }

  @Test
  fun testCursorUpDoesNotExceedActiveScreenTop() {
    val parser = TerminalAnsiParser()
    parser.resize(cols = 80, rows = 5)

    // Feed 15 lines
    for (i in 1..15) {
      parser.feed("Row $i\r\n")
    }

    // Cursor Up 50 times (\e[50A) - should clamp at top of active screen, not scrollback line 0
    parser.feed("\u001b[50AClampedTest")

    val lines = parser.lines.value
    assertEquals("Row 1", lines[0].plainText)
    assertEquals("Row 2", lines[1].plainText)
  }

  @Test
  fun testTmuxScrollingRegionPreservesStatusBar() {
    val parser = TerminalAnsiParser()
    parser.resize(cols = 80, rows = 10)

    // Set scrolling region: lines 1 to 9 (0 to 8 0-indexed). Line 10 (index 9) is reserved for Tmux status bar!
    parser.feed("\u001b[1;9r")
    // Draw status bar at row 10
    parser.feed("\u001b[10;1HTMUX_STATUS_BAR")

    // Move cursor to row 1 of the pane
    parser.feed("\u001b[1;1H")
    // Feed 30 lines of streaming output into the pane
    for (i in 1..30) {
      parser.feed("Pane output $i\r\n")
    }

    val lines = parser.lines.value
    // In restricted region mode, lines outside region must not be multiplied or pushed down
    val statusBarCount = lines.count { it.plainText.contains("TMUX_STATUS_BAR") }
    assertEquals("TMUX_STATUS_BAR should appear exactly once in buffer, got $statusBarCount", 1, statusBarCount)

    // The status bar must remain on the active screen row
    val lastLine = lines.last().plainText
    assertTrue("Last line should contain TMUX_STATUS_BAR, got: '$lastLine'", lastLine.contains("TMUX_STATUS_BAR"))
  }

  @Test
  fun testScrollingRegionInsertAndDeleteLine() {
    val parser = TerminalAnsiParser()
    parser.resize(cols = 80, rows = 5)

    // Scrolling region rows 1..4 (index 0..3), row 5 (index 4) is bottom bar
    parser.feed("\u001b[1;4r")
    parser.feed("\u001b[5;1HBOTTOM_BAR")
    parser.feed("\u001b[1;1HLineA\r\nLineB\r\nLineC\r\nLineD")

    val before = parser.lines.value
    assertEquals("BOTTOM_BAR", before[4].plainText)

    // Delete Line at row 2
    parser.feed("\u001b[2;1H\u001b[1M")
    val afterDelete = parser.lines.value
    // LineB deleted, LineC moved up to row 2
    assertTrue(afterDelete[1].plainText.contains("LineC"))
    // BOTTOM_BAR still at row 5!
    assertEquals("BOTTOM_BAR", afterDelete[4].plainText)
  }

  @Test
  fun testTrailingSpaceTrimmingPreservesBackground() {
    val parser = TerminalAnsiParser()
    parser.resize(cols = 40, rows = 10)

    // Output "Hello" followed by transparent spaces to column 30
    parser.feed("Hello\u001b[30G\r\n")

    // Output Tmux status bar with green background: \e[42mSTATUS_BAR   \e[0m
    parser.feed("\u001b[42mSTATUS_BAR   \u001b[0m\r\n")

    val lines = parser.lines.value
    // First line should have trimmed trailing spaces ("Hello", length 5)
    assertEquals("Hello", lines[0].plainText)

    // Second line has green background on trailing spaces, so they must be preserved!
    val statusLine = lines[1]
    assertTrue(statusLine.plainText.startsWith("STATUS_BAR   "))
    val greenSegment = statusLine.segments.firstOrNull { it.text.contains("STATUS_BAR   ") }
    assertTrue("Status bar segment should have background color", greenSegment?.bgColor != null)
  }

  @Test
  fun testEastAsianWidthChineseWrapping() {
    val parser = TerminalAnsiParser()
    // 20 columns: exactly fits 10 CJK characters (2 cols each)
    parser.resize(cols = 20, rows = 10)

    // Feed 10 Chinese characters: "一二三四五六七八九十" = 20 columns
    val tenChars = "一二三四五六七八九十"
    parser.feed(tenChars)

    val linesBefore = parser.lines.value
    assertEquals(1, linesBefore.size)
    assertEquals(tenChars, linesBefore[0].plainText)

    // Now feed 1 more Chinese character "超"
    parser.feed("超")
    val linesAfter = parser.lines.value
    // Should wrap to line 2 without duplicate blank lines
    assertEquals(2, linesAfter.size)
    assertEquals(tenChars, linesAfter[0].plainText)
    assertEquals("超", linesAfter[1].plainText)
  }

  @Test
  fun testWrapPendingDoesNotDoubleNewlineOnCrLf() {
    val parser = TerminalAnsiParser()
    parser.resize(cols = 20, rows = 10)

    // 20 ASCII characters followed immediately by \r\n
    val twentyChars = "12345678901234567890"
    parser.feed(twentyChars + "\r\nNextLine")

    val lines = parser.lines.value
    // There must be exactly 2 lines ("12345678901234567890" and "NextLine"), NOT 3 (no empty row in between)
    assertEquals(2, lines.size)
    assertEquals(twentyChars, lines[0].plainText)
    assertEquals("NextLine", lines[1].plainText)
  }

  @Test
  fun testReverseIndexMovesCursorUp() {
    val parser = TerminalAnsiParser()
    parser.resize(cols = 20, rows = 5)

    parser.feed("line1\r\nline2\r\n")
    // ESC M (RI) twice: cursor walks up two rows, KEEPING its column (xterm RI semantics)
    parser.feed("\u001b[3;1H")   // row 3 (1-based) = line below "line2"
    parser.feed("\u001bMX\u001bMX")
    val lines = parser.lines.value
    // Second X lands one column right of the first (column is preserved across RI)
    assertEquals("lXne1", lines[0].plainText)
    assertEquals("Xine2", lines[1].plainText)
  }

  @Test
  fun testReverseIndexAtTopMarginScrollsRegionDown() {
    val parser = TerminalAnsiParser()
    parser.resize(cols = 20, rows = 4)

    parser.feed("A\r\nB\r\nC\r\n\u001b[1;3r")  // region rows 1..3, cursor homed to row 1
    parser.feed("\u001bM")                       // RI at top margin -> scroll region down
    val lines = parser.lines.value
    // Region content shifts down one row; the line at the bottom margin ("C") is lost
    assertEquals("", lines[0].plainText)
    assertEquals("A", lines[1].plainText)
    assertEquals("B", lines[2].plainText)
    assertEquals("", lines[3].plainText)
  }

  @Test
  fun testResizeShrinkTruncatesStaleCellsBeyondNewWidth() {
    val parser = TerminalAnsiParser()
    parser.resize(cols = 20, rows = 5)
    parser.feed("0123456789012345678XY")  // 20 cols filled, X Y wrap to next line? No: exactly 20 then wrapPending

    // Shrink to 10 cols: cells beyond col 10 must be discarded so tmux's
    // diff redraw can never merge old-wide fragments into new rows
    parser.resize(cols = 10, rows = 5)
    val lines = parser.lines.value
    assertEquals("0123456789", lines[0].plainText)
  }

  @Test
  fun testTmuxRedrawAfterShrinkKeepsStatusBarIntact() {
    // Reproduces the on-device corruption: tmux status written at 65 cols,
    // then app shrinks (font up) and tmux redraws diff-style at 51 cols.
    val parser = TerminalAnsiParser()
    parser.resize(cols = 20, rows = 6)

    // Old wide screen: content fills all 20 cols incl. status line
    parser.feed("pane-line-1\r\npane-line-2\r\n")
    parser.feed("STATUS-OLD-LINE99")   // 19 chars
    parser.feed("\u001b[4;1H")          // cursor to status row

    parser.resize(cols = 10, rows = 6)
    // tmux redraws: only rewrites the pane rows it thinks changed + new short status
    parser.feed("\u001b[1;1Hnew1\u001b[K\r\nnew2\u001b[K\r\n\u001b[K\r\n")
    parser.feed("\u001b[4;1HSTATUS\u001b[K")

    val lines = parser.lines.value
    val statusLine = lines[3].plainText
    // New short status must fully replace the old one - no merged leftovers
    assertEquals("STATUS", statusLine)
    assertTrue(
      "No line may contain stale fragments of the old wider content",
      lines.none { it.plainText.contains("OLD") || it.plainText.contains("LINE99") }
    )
  }

  @Test
  fun testTmuxCursorDanceLfThenRiKeepsAlignment() {
    // tmux ends many redraws with: CUP(bottom) DECSTBM CUP(bottom) \n ESC M <prompt> ESC[K
    // The \n scrolls the restricted region up (cursor stays on bottom margin);
    // ESC M (RI) must then move the cursor UP one row so the prompt lands correctly.
    val parser = TerminalAnsiParser()
    parser.resize(cols = 10, rows = 4)

    parser.feed("aaaa\r\nbbbb\r\ncccc\r\n")
    parser.feed("\u001b[3;1H")               // cursor to bottom area
    parser.feed("\u001b[1;3r")               // DECSTBM 1..3 (homes cursor)
    parser.feed("\u001b[3;1H")               // cursor back to bottom margin row (index 2)
    parser.feed("\n\u001bMPROMPT")

    val lines = parser.lines.value
    // \n at bottom margin scrolled region up: [bbbb, cccc, blank]; RI moved cursor to row 1;
    // PROMPT overwrites "cccc" (index 1) - exactly what a real terminal shows.
    assertEquals("bbbb", lines[0].plainText)
    assertEquals("PROMPT", lines[1].plainText)
    assertEquals("", lines[2].plainText)
  }
  @Test
  fun testAlternateScreenTracking() {
    // tmux/vim switch to the alternate screen (DECSET 1049/47/1047) and back.
    // Swipe-to-scroll logic relies on this flag to know scrollback lives remotely.
    val parser = TerminalAnsiParser()
    org.junit.Assert.assertFalse(parser.isAlternateScreen)

    parser.feed("\u001b[?1049h")
    assertTrue(parser.isAlternateScreen)
    parser.feed("\u001b[?1047l")
    org.junit.Assert.assertFalse(parser.isAlternateScreen)

    parser.feed("\u001b[?47h")
    assertTrue(parser.isAlternateScreen)
    parser.feed("\u001b[?47l")
    org.junit.Assert.assertFalse(parser.isAlternateScreen)

    // Mouse reporting flag is orthogonal and untouched by screen switches
    parser.feed("\u001b[?1000h")
    assertTrue(parser.isMouseReporting)
    org.junit.Assert.assertFalse(parser.isAlternateScreen)
    parser.feed("\u001b[?1000l")
    org.junit.Assert.assertFalse(parser.isMouseReporting)
  }
}

