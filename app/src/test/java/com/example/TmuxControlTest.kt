package com.example

import com.example.ssh.SpecialKeyHandler
import org.junit.Assert.assertEquals
import org.junit.Test

class TmuxControlTest {

  @Test
  fun testTmuxPrefixAndKeys() {
    val tmuxPrefix = SpecialKeyHandler.CTRL_B
    assertEquals("\u0002", tmuxPrefix)

    // 窗格/分屏序列已随移动端功能移除，不再断言

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

    // Screen 分屏序列已随移动端功能移除
  }

}
