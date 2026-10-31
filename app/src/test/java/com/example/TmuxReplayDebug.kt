package com.example

import com.example.ssh.TerminalAnsiParser
import org.junit.Test
import java.io.File

/**
 * TEMPORARY debug replay: feed real captured tmux byte stream through the parser
 * to reproduce the layout corruption seen on device.
 */
class TmuxReplayDebug {
  private fun feedFile(parser: TerminalAnsiParser, name: String) {
    val bytes = File("/tmp/tmux_repro/captured/$name").readBytes()
    parser.feed(String(bytes, Charsets.UTF_8))
  }

  private fun dump(parser: TerminalAnsiParser, label: String) {
    val lines = parser.lines.value
    println("===== $label (${lines.size} lines) =====")
    lines.forEachIndexed { i, l ->
      val t = l.plainText.replace(' ', '·')
      println("%3d| %s".format(i, t))
    }
  }

  @Test
  fun replayRealTmuxSession() {
    val parser = TerminalAnsiParser()
    parser.resize(cols = 65, rows = 22)
    feedFile(parser, "phase1_startup.bin")
    feedFile(parser, "phase2_cmd1.bin")
    feedFile(parser, "phase3_ls.bin")
    dump(parser, "AFTER LS @65x22")

    feedFile(parser, "phase4_zoom_on.bin")
    feedFile(parser, "phase5_zoom_off.bin")

    // Font increase on phone: cols/rows shrink -> app calls resize + tmux redraws
    parser.resize(cols = 51, rows = 19)
    feedFile(parser, "phase6_resize_shrink.bin")
    dump(parser, "AFTER SHRINK @51x19")

    feedFile(parser, "phase7_stream.bin")
    feedFile(parser, "phase8_zoom_toggle.bin")
    dump(parser, "AFTER STREAM+ZOOM")
  }
}
