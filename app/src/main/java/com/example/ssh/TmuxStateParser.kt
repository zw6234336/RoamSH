package com.example.ssh

/**
 * Real tmux state fetched over a dedicated exec channel (NOT the interactive PTY,
 * so nothing gets injected into the running shell / full-screen apps).
 *
 * The exec channel does not inherit the PTY's $TMUX environment, so the script
 * must locate the session itself: pick the first *attached* session (this app's
 * PTY is the typical only client), falling back to the first session. Markers
 * delimit the three answer blocks; [TmuxStateParser.parse] turns them into a
 * typed state that drives the TmuxControlBar window tabs.
 */
data class TmuxWindowInfo(
  val index: Int,
  val title: String,
  val isActive: Boolean,
  val panes: Int
)

data class TmuxSessionState(
  val sessionName: String?,
  val windows: List<TmuxWindowInfo>,
  val mouseEnabled: Boolean? = null // null = no tmux server / unknown
) {
  companion object {
    val NONE = TmuxSessionState(sessionName = null, windows = emptyList(), mouseEnabled = null)
  }
}

object TmuxStateParser {

  // ${'$'} escapes Kotlin string interpolation so the shell variables reach bash intact.
  internal val SCRIPT = """
S=$(tmux ls -F '#{session_name} #{session_attached}' 2>/dev/null | awk '${'$'}2==1{print ${'$'}1; exit}')
[ -z "${'$'}S" ] && S=$(tmux ls -F '#{session_name}' 2>/dev/null | head -n1)
echo ===TMS===
echo "${'$'}S"
echo ===TMW===
[ -n "${'$'}S" ] && tmux list-windows -t "${'$'}S" -F '#{window_index}|#{window_name}|#{window_active}|#{window_panes}' 2>/dev/null
echo ===TMM===
[ -n "${'$'}S" ] && tmux show-options -gv mouse 2>/dev/null
"""

  private const val SESSION_MARKER = "===TMS==="
  private const val WINDOW_MARKER = "===TMW==="
  private const val MOUSE_MARKER = "===TMM==="

  fun parse(output: String): TmuxSessionState {
    val lines = output.lines()
    val sessionIdx = lines.indexOfFirst { it.trim() == SESSION_MARKER }
    val windowIdx = lines.indexOfFirst { it.trim() == WINDOW_MARKER }
    val mouseIdx = lines.indexOfFirst { it.trim() == MOUSE_MARKER }
    if (sessionIdx < 0 || windowIdx < 0 || mouseIdx < 0) return TmuxSessionState.NONE

    val sessionName = lines.getOrNull(sessionIdx + 1)?.trim()?.takeIf { it.isNotEmpty() }

    val windows = lines.subList(windowIdx + 1, mouseIdx)
      .mapNotNull { line ->
        // index|title|active|panes — split with a limit so titles containing '|' survive
        val parts = line.split("|", limit = 4)
        if (parts.size < 4) return@mapNotNull null
        val idx = parts[0].trim().toIntOrNull() ?: return@mapNotNull null
        val title = parts[1].trim().ifEmpty { "window $idx" }
        TmuxWindowInfo(
          index = idx,
          title = title,
          isActive = parts[2].trim() == "1",
          panes = parts[3].trim().toIntOrNull() ?: 1
        )
      }
      .sortedBy { it.index }

    if (sessionName == null && windows.isEmpty()) return TmuxSessionState.NONE

    val mouseRaw = lines.getOrNull(mouseIdx + 1)?.trim()?.lowercase()
    val mouseEnabled = when (mouseRaw) {
      "on" -> true
      "off" -> false
      else -> null
    }

    return TmuxSessionState(
      sessionName = sessionName,
      windows = windows,
      mouseEnabled = mouseEnabled
    )
  }
}
