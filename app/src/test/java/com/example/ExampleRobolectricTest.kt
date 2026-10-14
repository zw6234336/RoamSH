package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.KeyGenerator
import com.example.ssh.SpecialKeyHandler
import com.example.util.CredentialParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("RoamSH", appName)
  }

  @Test
  fun `verify special keys encoding`() {
    assertEquals("\u001b", SpecialKeyHandler.ESC)
    assertEquals("\t", SpecialKeyHandler.TAB)
    assertEquals("\u0003", SpecialKeyHandler.CTRL_C)
    assertEquals("\u0004", SpecialKeyHandler.CTRL_D)
    assertEquals("\u001b[A", SpecialKeyHandler.ARROW_UP)
    assertEquals("\u001b[B", SpecialKeyHandler.ARROW_DOWN)
    assertEquals("\u0003", SpecialKeyHandler.ctrl('c'))
  }

  @Test
  fun `verify key generator creates valid rsa key pair`() {
    val keyPair = KeyGenerator.generateKey("RSA", "test-key")
    assertNotNull(keyPair.publicKey)
    assertNotNull(keyPair.privateKey)
    assertTrue(keyPair.publicKey.startsWith("ssh-rsa"))
    assertTrue(keyPair.privateKey.contains("PRIVATE KEY"))
  }

  @Test
  fun `verify 1password multiline credential parsing`() {
    val onePasswordExport = """
      Server: 192.168.31.200:2222
      Username: developer
      Password: MySecretPassword@123
      Title: Home Ubuntu Rig
    """.trimIndent()

    val parsed = CredentialParser.parse(onePasswordExport)
    assertEquals("192.168.31.200", parsed.host)
    assertEquals(2222, parsed.port)
    assertEquals("developer", parsed.username)
    assertEquals("MySecretPassword@123", parsed.password)
    assertEquals("Home Ubuntu Rig", parsed.name)
  }

  @Test
  fun `verify ssh command string parsing`() {
    val sshCmd = "ssh -p 2200 ubuntu@workstation.local"
    val parsed = CredentialParser.parse(sshCmd)
    assertEquals("workstation.local", parsed.host)
    assertEquals("ubuntu", parsed.username)
    assertEquals(2200, parsed.port)
  }

  @Test
  fun `verify service startup does not crash`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    com.example.ssh.SshKeepAliveService.start(context, "TestServer", "192.168.1.1:22")
  }
}
