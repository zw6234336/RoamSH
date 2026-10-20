package com.example

import com.example.ssh.SpecialKeyHandler
import org.junit.Assert.assertEquals
import org.junit.Test

class TmuxControlTest {

  @Test
  fun testTmuxPrefixAndKeys() {
    val tmuxPrefix = SpecialKeyHandler.CTRL_B
    assertEquals("\u0002", tmuxPrefix)

    // Test Horizontal Split command
    val splitH = tmuxPrefix + "\""
    assertEquals("\u0002\"", splitH)

    // Test Vertical Split command
    val splitV = tmuxPrefix + "%"
    assertEquals("\u0002%", splitV)

    // Test Zoom toggle
    val zoom = tmuxPrefix + "z"
    assertEquals("\u0002z", zoom)

    // Test New window
    val newWin = tmuxPrefix + "c"
    assertEquals("\u0002c", newWin)

    // Test Next / Prev window
    assertEquals("\u0002n", tmuxPrefix + "n")
    assertEquals("\u0002p", tmuxPrefix + "p")

    // Test Copy mode
    assertEquals("\u0002[", tmuxPrefix + "[")

    // Test Detach
    assertEquals("\u0002d", tmuxPrefix + "d")
  }

  @Test
  fun testScreenPrefixAndKeys() {
    val screenPrefix = SpecialKeyHandler.CTRL_A
    assertEquals("\u0001", screenPrefix)

    // Screen horizontal split (S)
    assertEquals("\u0001S", screenPrefix + "S")

    // Screen vertical split (|)
    assertEquals("\u0001|", screenPrefix + "|")
  }

  @Test
  fun testPaneNavigationSequences() {
    val prefix = SpecialKeyHandler.CTRL_B
    assertEquals("\u0002\u001b[A", prefix + SpecialKeyHandler.ARROW_UP)
    assertEquals("\u0002\u001b[B", prefix + SpecialKeyHandler.ARROW_DOWN)
    assertEquals("\u0002\u001b[D", prefix + SpecialKeyHandler.ARROW_LEFT)
    assertEquals("\u0002\u001b[C", prefix + SpecialKeyHandler.ARROW_RIGHT)
  }

  @Test
  fun testAltResizeSequences() {
    val prefix = SpecialKeyHandler.CTRL_B
    val resizeUp = prefix + SpecialKeyHandler.alt('u')
    assertEquals("\u0002\u001bu", resizeUp)

    val resizeDown = prefix + SpecialKeyHandler.alt('d')
    assertEquals("\u0002\u001bd", resizeDown)
  }
}
