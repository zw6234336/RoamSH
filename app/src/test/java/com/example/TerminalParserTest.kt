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
}
