package com.example

import com.example.ssh.TmuxStateParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parser for `tmux list-windows` output over the exec channel. Guards the
 * TmuxControlBar's real-state integration: session detection, window list,
 * active flag, pane count, mouse mode, and graceful handling of missing tmux.
 */
class TmuxStateParserTest {

  @Test
  fun parsesSessionWindowsActiveFlagAndMouse() {
    val out = """
      ===TMS===
      work
      ===TMW===
      0|bash|1|1
      1|vim|0|1
      2|logs|0|3
      ===TMM===
      on
    """.trimIndent()

    val state = TmuxStateParser.parse(out)

    assertEquals("work", state.sessionName)
    assertEquals(3, state.windows.size)
    assertEquals(0, state.windows[0].index)
    assertEquals("bash", state.windows[0].title)
    assertTrue(state.windows[0].isActive)
    assertFalse(state.windows[1].isActive)
    assertEquals(3, state.windows[2].panes)
    assertEquals(true, state.mouseEnabled)
  }

  @Test
  fun sortsWindowsByIndexRegardlessOfOutputOrder() {
    val out = """
      ===TMS===
      main
      ===TMW===
      3|top|0|1
      1|editor|1|2
      ===TMM===
      off
    """.trimIndent()

    val state = TmuxStateParser.parse(out)

    assertEquals(listOf(1, 3), state.windows.map { it.index })
    assertTrue(state.windows.first().isActive)
    assertEquals(false, state.mouseEnabled)
  }

  @Test
  fun noTmuxServerYieldsNoneState() {
    // tmux absent: markers echo but all blocks are empty
    val out = """
      ===TMS===

      ===TMW===
      ===TMM===
    """.trimIndent()

    val state = TmuxStateParser.parse(out)

    assertNull(state.sessionName)
    assertTrue(state.windows.isEmpty())
    assertNull(state.mouseEnabled)
    assertEquals(com.example.ssh.TmuxSessionState.NONE, state)
  }

  @Test
  fun garbageOutputWithoutMarkersIsNone() {
    val state = TmuxStateParser.parse("command not found: tmux\nsomething else")
    assertNull(state.sessionName)
    assertTrue(state.windows.isEmpty())
    assertNull(state.mouseEnabled)
  }

  @Test
  fun windowTitlesContainingPipesDoNotCrashOrDropRows() {
    val out = """
      ===TMS===
      my session
      ===TMW===
      0|run|a|b|c|0|1
      ===TMM===
      on
    """.trimIndent()

    val state = TmuxStateParser.parse(out)

    // The index|name|active|panes format is inherently pipe-ambiguous; with
    // split(limit=4) the tail is absorbed into the last field. The parser must
    // keep the row (no crash, no drop) and fall back safely on bad fields.
    assertEquals(1, state.windows.size)
    assertEquals("run", state.windows.single().title)
    assertFalse(state.windows.single().isActive) // "a" != "1" -> not active
    assertEquals(1, state.windows.single().panes) // unparseable -> fallback
    assertEquals("my session", state.sessionName) // spaces in session name fine
  }

  @Test
  fun malformedWindowLinesAreSkipped() {
    val out = """
      ===TMS===
      s
      ===TMW===
      not-a-window-line
      2|ok|0|1
      x|bad-index|0|1
      ===TMM===
      maybe
    """.trimIndent()

    val state = TmuxStateParser.parse(out)

    assertEquals(1, state.windows.size)
    assertEquals(2, state.windows[0].index)
    assertNull(state.mouseEnabled) // unknown mouse value -> null, not a guess
  }

  @Test
  fun emptyTitleFallsBackToWindowIndexLabel() {
    val out = """
      ===TMS===
      s
      ===TMW===
      5||0|1
      ===TMM===
      on
    """.trimIndent()

    val state = TmuxStateParser.parse(out)
    assertEquals("window 5", state.windows.single().title)
  }
}
