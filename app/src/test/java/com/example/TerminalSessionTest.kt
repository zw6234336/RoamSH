package com.example

import com.example.data.model.AuthType
import com.example.data.model.HostConfig
import com.example.ssh.SshManager
import com.example.ssh.TerminalSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalSessionTest {

  @Test
  fun testIndependentTerminalBuffers() {
    val scope = CoroutineScope(Dispatchers.Unconfined)
    val host1 = HostConfig(name = "Server A", host = "10.0.0.1", port = 22, username = "root")
    val host2 = HostConfig(name = "Server B", host = "10.0.0.2", port = 22, username = "root")

    val manager1 = SshManager(scope)
    val manager2 = SshManager(scope)

    val session1 = TerminalSession(
      title = "Server A",
      host = host1,
      sshManager = manager1
    )

    val session2 = TerminalSession(
      title = "Server B",
      host = host2,
      sshManager = manager2
    )

    // Feed output into session 1
    session1.sshManager.terminalParser.feed("Output from Server A\r\n")

    // Feed output into session 2
    session2.sshManager.terminalParser.feed("Output from Server B\r\n")

    // Verify buffers are completely isolated
    val lines1 = session1.sshManager.terminalParser.lines.value
    val lines2 = session2.sshManager.terminalParser.lines.value

    assertEquals(2, lines1.size)
    assertEquals("Output from Server A", lines1[0].plainText)

    assertEquals(2, lines2.size)
    assertEquals("Output from Server B", lines2[0].plainText)

    assertNotEquals(session1.id, session2.id)
  }

  @Test
  fun testSessionRenaming() {
    val scope = CoroutineScope(Dispatchers.Unconfined)
    val host = HostConfig(name = "Ubuntu", host = "192.168.1.100", port = 22, username = "dev")
    val session = TerminalSession(
      title = "Ubuntu #1",
      host = host,
      sshManager = SshManager(scope)
    )

    assertEquals("Ubuntu #1", session.title)
    val renamed = session.copy(title = "Web Server (Nginx)")
    assertEquals("Web Server (Nginx)", renamed.title)
    assertEquals(session.id, renamed.id)
  }
}
