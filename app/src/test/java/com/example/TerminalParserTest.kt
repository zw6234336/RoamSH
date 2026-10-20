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
}
