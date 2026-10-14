package com.example.ssh

import com.example.data.model.AuthType
import com.example.data.model.HostConfig
import com.example.data.model.PortTunnel
import com.example.data.model.SshKey
import com.example.data.model.TunnelType
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.example.ui.terminal.CandidateItem
import com.example.ui.terminal.CandidateType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.Security
import java.util.Properties
import org.bouncycastle.jce.provider.BouncyCastleProvider

sealed class SshConnectionState {
  data object Disconnected : SshConnectionState()
  data class Connecting(val message: String) : SshConnectionState()
  data class Connected(
    val hostName: String,
    val hostAddress: String,
    val isSimulated: Boolean,
    val activeTunnelsCount: Int = 0
  ) : SshConnectionState()
  data class Error(val message: String, val canFallbackToSimulated: Boolean = false) : SshConnectionState()
}

class SshManager(
  private val scope: CoroutineScope
) {
  private val _connectionState = MutableStateFlow<SshConnectionState>(SshConnectionState.Disconnected)
  val connectionState: StateFlow<SshConnectionState> = _connectionState.asStateFlow()

  val terminalParser = TerminalAnsiParser()

  private var jsch: JSch? = null
  private var session: Session? = null
  private var shellChannel: ChannelShell? = null
  private var inputStream: InputStream? = null
  private var outputStream: OutputStream? = null

  private var readJob: Job? = null
  private var simShell: LocalDevSimulationShell? = null

  var currentHost: HostConfig? = null
    private set

  private val activeTunnels = mutableListOf<PortTunnel>()

  companion object {
    init {
      try {
        if (Security.getProvider("BC") == null) {
          Security.addProvider(BouncyCastleProvider())
        }
      } catch (t: Throwable) {
        android.util.Log.w("SshManager", "Failed to add BouncyCastleProvider: ${t.message}")
      }
    }
  }

  suspend fun connect(
    host: HostConfig,
    key: SshKey? = null,
    tunnels: List<PortTunnel> = emptyList(),
    forceSimulation: Boolean = false
  ) = withContext(Dispatchers.IO) {
    disconnect()
    currentHost = host

    // Clean and sanitize host and port to prevent parsing or DNS resolution crashes
    val rawHost = host.host.trim()
      .removePrefix("ssh://")
      .removePrefix("http://")
      .removePrefix("https://")
    val strippedUserHost = if (rawHost.contains("@")) rawHost.substringAfter("@") else rawHost
    val cleanHost = if (strippedUserHost.contains(":")) strippedUserHost.substringBefore(":") else strippedUserHost
    val parsedPort = if (strippedUserHost.contains(":")) {
      strippedUserHost.substringAfter(":").toIntOrNull() ?: host.port
    } else {
      host.port
    }
    val safePort = parsedPort.coerceIn(1, 65535)

    if (forceSimulation || cleanHost == "127.0.0.1" || cleanHost == "simulation" || cleanHost == "localhost" || cleanHost.isBlank()) {
      startSimulatedSession(host)
      return@withContext
    }

    _connectionState.value = SshConnectionState.Connecting("正在连接到 $cleanHost:$safePort...")

    try {
      jsch = JSch()

      // Handle Key-based authentication
      if ((host.authType == AuthType.PRIVATE_KEY || host.authType == AuthType.KEY_WITH_PASSPHRASE) && key != null) {
        val privKeyBytes = key.privateKey.toByteArray(Charsets.UTF_8)
        val pubKeyBytes = if (key.publicKey.isNotBlank()) key.publicKey.toByteArray(Charsets.UTF_8) else null
        val passBytes = if (host.passphrase.isNotBlank()) {
          host.passphrase.toByteArray(Charsets.UTF_8)
        } else if (key.passphrase.isNotBlank()) {
          key.passphrase.toByteArray(Charsets.UTF_8)
        } else null

        jsch?.addIdentity(key.name, privKeyBytes, pubKeyBytes, passBytes)
      }

      val loginUser = host.username.trim().ifEmpty { "dev" }
      val newSession = jsch!!.getSession(loginUser, cleanHost, safePort)
      if (host.authType == AuthType.PASSWORD && host.password.isNotEmpty()) {
        newSession.setPassword(host.password)
      }

      val config = Properties()
      config["StrictHostKeyChecking"] = "no"
      config["TCPKeepAlive"] = "yes"
      config["PreferredAuthentications"] = if (host.authType == AuthType.PASSWORD) {
        "password,keyboard-interactive"
      } else {
        "publickey,password,keyboard-interactive"
      }

      // Comprehensive Modern and Legacy SSH Algorithm Negotiations for Android compatibility
      config["server_host_key"] = "ssh-ed25519,ecdsa-sha2-nistp256,ecdsa-sha2-nistp384,ecdsa-sha2-nistp521,rsa-sha2-512,rsa-sha2-256,ssh-rsa"
      config["kex"] = "curve25519-sha256,curve25519-sha256@libssh.org,ecdh-sha2-nistp256,ecdh-sha2-nistp384,ecdh-sha2-nistp521,diffie-hellman-group-exchange-sha256,diffie-hellman-group14-sha256,diffie-hellman-group16-sha512,diffie-hellman-group18-sha512,diffie-hellman-group14-sha1"
      config["cipher.s2c"] = "aes128-ctr,aes256-ctr,aes128-gcm@openssh.com,aes256-gcm@openssh.com,chacha20-poly1305@openssh.com,aes192-ctr,aes128-cbc,aes256-cbc,3des-cbc"
      config["cipher.c2s"] = "aes128-ctr,aes256-ctr,aes128-gcm@openssh.com,aes256-gcm@openssh.com,chacha20-poly1305@openssh.com,aes192-ctr,aes128-cbc,aes256-cbc,3des-cbc"
      config["mac.s2c"] = "hmac-sha2-256-etm@openssh.com,hmac-sha2-512-etm@openssh.com,hmac-sha2-256,hmac-sha2-512,hmac-sha1,hmac-md5"
      config["mac.c2s"] = "hmac-sha2-256-etm@openssh.com,hmac-sha2-512-etm@openssh.com,hmac-sha2-256,hmac-sha2-512,hmac-sha1,hmac-md5"
      config["compression.s2c"] = "none"
      config["compression.c2s"] = "none"

      newSession.setConfig(config)
      newSession.timeout = 15000
      newSession.serverAliveInterval = (host.keepAliveIntervalSeconds.coerceAtLeast(15)) * 1000
      newSession.serverAliveCountMax = 4

      _connectionState.value = SshConnectionState.Connecting("正在验证身份与握手...")
      newSession.connect(15000)
      session = newSession

      // Setup port forwarding tunnels
      activeTunnels.clear()
      tunnels.filter { it.isEnabled }.forEach { tunnel ->
        try {
          when (tunnel.type) {
            TunnelType.LOCAL_TO_REMOTE -> {
              newSession.setPortForwardingL(tunnel.localPort, tunnel.remoteHost, tunnel.remotePort)
              activeTunnels.add(tunnel)
            }
            TunnelType.REMOTE_TO_LOCAL -> {
              newSession.setPortForwardingR(tunnel.remotePort, "127.0.0.1", tunnel.localPort)
              activeTunnels.add(tunnel)
            }
          }
        } catch (_: Throwable) {
          // Tunnel failed but session continues
        }
      }

      _connectionState.value = SshConnectionState.Connecting("正在分配虚拟终端 (PTY)...")
      val channel = newSession.openChannel("shell") as ChannelShell
      channel.setPtyType(host.termType.ifEmpty { "xterm-256color" }, 80, 24, 640, 480)

      val inStream = channel.inputStream
      val outStream = channel.outputStream

      channel.connect(10000)
      shellChannel = channel
      inputStream = inStream
      outputStream = outStream

      _connectionState.value = SshConnectionState.Connected(
        hostName = host.name,
        hostAddress = "$loginUser@$cleanHost:$safePort",
        isSimulated = false,
        activeTunnelsCount = activeTunnels.size
      )

      // Start stream reading coroutine with UTF-8 character reader (preserves multi-byte Chinese chars)
      readJob = scope.launch(Dispatchers.IO) {
        try {
          val reader = java.io.InputStreamReader(inStream, java.nio.charset.StandardCharsets.UTF_8)
          val charBuffer = CharArray(2048)
          while (isActive && channel.isConnected) {
            try {
              val charsRead = reader.read(charBuffer)
              if (charsRead > 0) {
                val text = String(charBuffer, 0, charsRead)
                android.util.Log.d("SshManager", "reader received ${text.length} chars: ${text.take(50).replace("\n", "\\n")}")
                terminalParser.feed(text)
              } else if (charsRead == -1) {
                android.util.Log.w("SshManager", "reader EOF (-1)")
                break
              }
            } catch (e: Throwable) {
              android.util.Log.e("SshManager", "reader exception", e)
              break
            }
          }
        } catch (e: Throwable) {
          android.util.Log.e("SshManager", "reader outer exception", e)
        } finally {
          if (isActive) {
            android.util.Log.w("SshManager", "reader loop exited, setting Disconnected")
            _connectionState.value = SshConnectionState.Disconnected
          }
        }
      }

      // Run initial command if configured
      if (host.initialCommand.isNotBlank()) {
        sendInput(host.initialCommand + "\n")
      }

    } catch (t: Throwable) {
      val msg = t.localizedMessage ?: t.message ?: t.javaClass.simpleName
      android.util.Log.e("SshManager", "SSH connection failed: $msg", t)
      _connectionState.value = SshConnectionState.Error(
        message = "无法连接到 $cleanHost:$safePort ($msg)",
        canFallbackToSimulated = true
      )
    }
  }

  fun startSimulatedSession(host: HostConfig) {
    disconnect()
    currentHost = host
    terminalParser.clear()

    _connectionState.value = SshConnectionState.Connected(
      hostName = "${host.name} (模拟沙箱)",
      hostAddress = "local-sandbox",
      isSimulated = true,
      activeTunnelsCount = 0
    )

    val sim = LocalDevSimulationShell(
      scope = scope,
      hostname = host.name.replace(" ", "-").lowercase(),
      username = host.username.ifEmpty { "dev" }
    )
    simShell = sim

    readJob = scope.launch(Dispatchers.Default) {
      sim.output.collect { text ->
        terminalParser.feed(text)
      }
    }

    sim.start()
  }

  fun sendInput(text: String) {
    simShell?.let {
      it.sendInput(text)
      return
    }

    scope.launch(Dispatchers.IO) {
      try {
        android.util.Log.d(
          "SshManager",
          "sendInput: text='$text', outputStream=$outputStream, channelConnected=${shellChannel?.isConnected}, sessionConnected=${session?.isConnected}"
        )
        outputStream?.let { out ->
          out.write(text.toByteArray(Charsets.UTF_8))
          out.flush()
          android.util.Log.d("SshManager", "sendInput flushed successfully")
        } ?: run {
          android.util.Log.w("SshManager", "sendInput: outputStream is null!")
        }
      } catch (e: Exception) {
        android.util.Log.e("SshManager", "sendInput write failed", e)
      }
    }
  }

  fun resizeTerminal(cols: Int, rows: Int, width: Int, height: Int) {
    scope.launch(Dispatchers.IO) {
      try {
        shellChannel?.setPtySize(
          cols.coerceAtLeast(10),
          rows.coerceAtLeast(5),
          width.coerceAtLeast(100),
          height.coerceAtLeast(100)
        )
      } catch (_: Exception) {
      }
    }
  }

  suspend fun queryLinuxCompletions(
    commandLine: String,
    cwd: String = "~"
  ): List<CandidateItem> = withContext(Dispatchers.IO) {
    val sim = simShell
    if (sim != null) {
      return@withContext sim.getCompletions(commandLine)
    }

    val activeSession = session
    if (activeSession == null || !activeSession.isConnected) {
      return@withContext emptyList()
    }

    try {
      kotlinx.coroutines.withTimeoutOrNull(2500L) {
        val channel = activeSession.openChannel("exec") as? ChannelExec ?: return@withTimeoutOrNull emptyList()

        val trimmed = commandLine.trimStart()
        val lastSpace = trimmed.lastIndexOf(' ')
        val token = if (lastSpace >= 0) trimmed.substring(lastSpace + 1) else trimmed
        val primaryCommand = if (lastSpace >= 0) {
          trimmed.substring(0, lastSpace).trim().split(" ").firstOrNull()?.lowercase() ?: ""
        } else {
          ""
        }

        val safeCwd = if (cwd.isBlank() || cwd == ".") "~" else cwd
        val safeToken = token.replace("\"", "\\\"").replace("`", "").replace("$", "\\$")

        val script = when {
          primaryCommand.isEmpty() -> {
            if (safeToken.isEmpty()) {
              "compgen -c 2>/dev/null | head -n 30"
            } else {
              "compgen -c -- \"$safeToken\" 2>/dev/null | head -n 30"
            }
          }
          primaryCommand in listOf("cd", "rmdir", "pushd") -> {
            "cd \"$safeCwd\" 2>/dev/null; for d in \$(compgen -d -- \"$safeToken\" 2>/dev/null); do echo \"\$d/\"; done | head -n 40"
          }
          primaryCommand == "git" -> {
            "compgen -W \"status log diff add commit push pull fetch checkout branch merge rebase stash clone reset remote tag switch show init restore\" -- \"$safeToken\" 2>/dev/null | head -n 25"
          }
          primaryCommand == "docker" -> {
            "compgen -W \"ps images run stop start restart exec logs compose build rm rmi pull push inspect network volume stats\" -- \"$safeToken\" 2>/dev/null | head -n 25"
          }
          primaryCommand == "systemctl" -> {
            "compgen -W \"status restart start stop reload enable disable daemon-reload is-active is-enabled list-units\" -- \"$safeToken\" 2>/dev/null | head -n 25"
          }
          else -> {
            "cd \"$safeCwd\" 2>/dev/null; for f in \$(compgen -f -- \"$safeToken\" 2>/dev/null); do if [ -d \"\$f\" ]; then echo \"\$f/\"; else echo \"\$f\"; fi; done | head -n 40"
          }
        }

        val singleQuoted = "'" + script.replace("'", "'\\''") + "'"
        channel.setCommand("bash -c $singleQuoted")
        channel.inputStream = null
        val inStream = channel.inputStream
        channel.connect(2000)

        val rawLines = inStream.bufferedReader().readLines()
        try {
          channel.disconnect()
        } catch (_: Throwable) {}

        rawLines.mapNotNull { raw ->
          val text = raw.trim()
          if (text.isEmpty()) null
          else {
            val isDir = text.endsWith('/')
            val type = when {
              isDir -> CandidateType.DIRECTORY
              primaryCommand.isEmpty() -> CandidateType.COMMAND
              primaryCommand in listOf("git", "docker", "systemctl") && !text.contains('/') -> CandidateType.SUBCOMMAND
              else -> CandidateType.FILE
            }
            CandidateItem(
              text = text,
              displayText = text,
              type = type,
              isDirectory = isDir
            )
          }
        }.distinctBy { it.text }
      } ?: emptyList()
    } catch (e: Throwable) {
      android.util.Log.w("SshManager", "Remote completion query failed: ${e.message}")
      emptyList()
    }
  }

  fun disconnect() {
    readJob?.cancel()
    readJob = null

    simShell?.close()
    simShell = null

    try {
      shellChannel?.disconnect()
    } catch (_: Throwable) {}
    shellChannel = null

    try {
      session?.disconnect()
    } catch (_: Throwable) {}
    session = null

    try {
      inputStream?.close()
    } catch (_: Throwable) {}
    inputStream = null

    try {
      outputStream?.close()
    } catch (_: Throwable) {}
    outputStream = null
    activeTunnels.clear()

    _connectionState.value = SshConnectionState.Disconnected
  }

  suspend fun checkHostPing(host: String, port: Int, timeoutMs: Int = 3000): Boolean = withContext(Dispatchers.IO) {
    try {
      val rawHost = host.trim()
        .removePrefix("ssh://")
        .removePrefix("http://")
        .removePrefix("https://")
        .removePrefix("ssh ")
      val stripped = if (rawHost.contains("@")) rawHost.substringAfter("@") else rawHost
      val cleanHost = if (stripped.contains(":")) stripped.substringBefore(":") else stripped
      val cleanPort = (if (stripped.contains(":")) stripped.substringAfter(":").toIntOrNull() else null) ?: port
      val safePort = cleanPort.coerceIn(1, 65535)

      if (cleanHost.isBlank() || cleanHost == "simulation" || cleanHost == "local-sandbox") {
        return@withContext true
      }

      val socket = Socket()
      socket.connect(InetSocketAddress(cleanHost, safePort), timeoutMs)
      socket.close()
      true
    } catch (_: Throwable) {
      false
    }
  }
}
