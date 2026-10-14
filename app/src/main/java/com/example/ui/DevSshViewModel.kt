package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.KeyGenerator
import com.example.data.model.AuthType
import com.example.data.model.HostConfig
import com.example.data.model.PortTunnel
import com.example.data.model.Snippet
import com.example.data.model.SshKey
import com.example.data.model.TerminalThemeType
import com.example.data.model.TunnelType
import com.example.ssh.SpecialKeyHandler
import com.example.ssh.SshConnectionState
import com.example.ssh.SshKeepAliveService
import com.example.ssh.SshManager
import com.example.ssh.TerminalColorScheme
import com.example.ssh.TerminalThemes
import androidx.compose.ui.text.input.TextFieldValue
import com.example.ui.terminal.CandidateItem
import com.example.ui.terminal.CandidateType
import com.example.ui.terminal.CommandCompletionEngine
import com.example.ui.terminal.CompletionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppScreen {
  HOSTS,
  TERMINAL,
  SNIPPETS,
  KEYS,
  TUNNELS,
  SETTINGS
}

class DevSshViewModel(application: Application) : AndroidViewModel(application) {
  private val database = AppDatabase.getDatabase(application)
  private val hostDao = database.hostDao()
  private val sshKeyDao = database.sshKeyDao()
  private val snippetDao = database.snippetDao()
  private val portTunnelDao = database.portTunnelDao()

  val sshManager = SshManager(viewModelScope)

  val allHosts: StateFlow<List<HostConfig>> = hostDao.getAllHosts()
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  val allKeys: StateFlow<List<SshKey>> = sshKeyDao.getAllKeys()
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  val allSnippets: StateFlow<List<Snippet>> = snippetDao.getAllSnippets()
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  val allTunnels: StateFlow<List<PortTunnel>> = portTunnelDao.getAllTunnels()
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  // Persistent terminal LazyListState preserved across tab switches and app pauses
  val terminalListState = androidx.compose.foundation.lazy.LazyListState()

  private val _autoScroll = MutableStateFlow(true)
  val autoScroll: StateFlow<Boolean> = _autoScroll.asStateFlow()

  private val _currentScreen = MutableStateFlow(AppScreen.HOSTS)
  val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

  // Terminal UI customization & Readability
  private val _terminalFontSize = MutableStateFlow(14)
  val terminalFontSize: StateFlow<Int> = _terminalFontSize.asStateFlow()

  private val _terminalLineSpacing = MutableStateFlow(4)
  val terminalLineSpacing: StateFlow<Int> = _terminalLineSpacing.asStateFlow()

  private val _terminalFontBold = MutableStateFlow(false)
  val terminalFontBold: StateFlow<Boolean> = _terminalFontBold.asStateFlow()

  private val _terminalCursorStyle = MutableStateFlow("BLOCK")
  val terminalCursorStyle: StateFlow<String> = _terminalCursorStyle.asStateFlow()

  private val _terminalThemeType = MutableStateFlow(TerminalThemeType.DRACULA)
  val terminalThemeType: StateFlow<TerminalThemeType> = _terminalThemeType.asStateFlow()

  val currentThemeScheme: TerminalColorScheme
    get() = TerminalThemes.get(_terminalThemeType.value)

  // Extended keyboard latch states
  private val _isCtrlActive = MutableStateFlow(false)
  val isCtrlActive: StateFlow<Boolean> = _isCtrlActive.asStateFlow()

  private val _isAltActive = MutableStateFlow(false)
  val isAltActive: StateFlow<Boolean> = _isAltActive.asStateFlow()

  // Host ping status map (hostId -> isOnline)
  private val _pingStatus = MutableStateFlow<Map<String, Boolean>>(emptyMap())
  val pingStatus: StateFlow<Map<String, Boolean>> = _pingStatus.asStateFlow()

  // Editing state
  private val _selectedHostForEdit = MutableStateFlow<HostConfig?>(null)
  val selectedHostForEdit: StateFlow<HostConfig?> = _selectedHostForEdit.asStateFlow()

  // Background keepalive toggle
  private val _isKeepAliveEnabled = MutableStateFlow(true)
  val isKeepAliveEnabled: StateFlow<Boolean> = _isKeepAliveEnabled.asStateFlow()

  init {
    SshKeepAliveService.onDisconnectRequested = {
      disconnectCurrentSession()
    }

    viewModelScope.launch {
      sshManager.connectionState.collect { state ->
        when (state) {
          is SshConnectionState.Connected -> {
            if (_isKeepAliveEnabled.value && !state.isSimulated) {
              SshKeepAliveService.start(
                application,
                state.hostName,
                state.hostAddress
              )
            }
          }
          is SshConnectionState.Disconnected, is SshConnectionState.Error -> {
            SshKeepAliveService.stop(application)
          }
          else -> {}
        }
      }
    }
  }

  fun setAutoScroll(enabled: Boolean) {
    _autoScroll.value = enabled
    if (enabled) {
      scrollToBottom()
    }
  }

  fun scrollToBottom() {
    val totalLines = sshManager.terminalParser.lines.value.size
    if (totalLines > 0) {
      viewModelScope.launch(Dispatchers.Main) {
        try {
          terminalListState.scrollToItem(totalLines - 1)
        } catch (_: Throwable) {}
      }
    }
  }

  fun toggleKeepAlive() {
    val newValue = !_isKeepAliveEnabled.value
    _isKeepAliveEnabled.value = newValue
    val state = sshManager.connectionState.value
    if (state is SshConnectionState.Connected && !state.isSimulated) {
      if (newValue) {
        SshKeepAliveService.start(getApplication<Application>(), state.hostName, state.hostAddress)
      } else {
        SshKeepAliveService.stop(getApplication<Application>())
      }
    }
  }

  fun disconnectCurrentSession() {
    SshKeepAliveService.stop(getApplication<Application>())
    sshManager.disconnect()
  }

  fun navigateTo(screen: AppScreen) {
    _currentScreen.value = screen
  }

  fun setTerminalFontSize(size: Int) {
    _terminalFontSize.value = size.coerceIn(9, 28)
  }

  fun setTerminalLineSpacing(spacing: Int) {
    _terminalLineSpacing.value = spacing.coerceIn(0, 10)
  }

  fun setTerminalFontBold(bold: Boolean) {
    _terminalFontBold.value = bold
  }

  fun setTerminalCursorStyle(style: String) {
    _terminalCursorStyle.value = style
  }

  fun setTerminalTheme(type: TerminalThemeType) {
    _terminalThemeType.value = type
    sshManager.terminalParser.setTheme(TerminalThemes.get(type))
  }

  fun applyCurrentAppearanceToAllHosts() {
    viewModelScope.launch {
      val hosts = allHosts.value
      val currentTheme = _terminalThemeType.value
      val currentFont = _terminalFontSize.value
      hosts.forEach { h ->
        hostDao.updateHost(h.copy(theme = currentTheme, fontSize = currentFont))
      }
    }
  }

  fun toggleCtrlLatch() {
    _isCtrlActive.value = !_isCtrlActive.value
  }

  fun toggleAltLatch() {
    _isAltActive.value = !_isAltActive.value
  }

  fun connectToHost(host: HostConfig, forceSimulation: Boolean = false) {
    viewModelScope.launch {
      hostDao.updateLastConnected(host.id, System.currentTimeMillis())
      _terminalFontSize.value = host.fontSize
      setTerminalTheme(host.theme)
      _currentScreen.value = AppScreen.TERMINAL

      // If already connected to this host and active, keep the current conversation and stay at bottom!
      if (!forceSimulation &&
          sshManager.currentHost?.id == host.id &&
          sshManager.connectionState.value is SshConnectionState.Connected) {
        scrollToBottom()
        return@launch
      }

      val key = host.keyId?.let { sshKeyDao.getKeyById(it) }
      // Get tunnels for host safely
      val currentTunnels = try {
        portTunnelDao.getTunnelsForHost(host.id).firstOrNull() ?: emptyList()
      } catch (_: Throwable) {
        emptyList()
      }

      sshManager.connect(
        host = host,
        key = key,
        tunnels = currentTunnels,
        forceSimulation = forceSimulation
      )
    }
  }

  fun sendTerminalInput(text: String) {
    var toSend = text
    if (_isCtrlActive.value && text.length == 1) {
      toSend = SpecialKeyHandler.ctrl(text[0])
      _isCtrlActive.value = false
    } else if (_isAltActive.value && text.length == 1) {
      toSend = SpecialKeyHandler.alt(text[0])
      _isAltActive.value = false
    }
    sshManager.sendInput(toSend)
  }

  fun sendSpecialKey(code: String) {
    sshManager.sendInput(code)
  }

  fun resizeTerminal(cols: Int, rows: Int, widthPx: Int, heightPx: Int) {
    sshManager.resizeTerminal(cols, rows, widthPx, heightPx)
  }

  fun extractCwdFromTerminal(): String {
    val terminalPlainLines = sshManager.terminalParser.lines.value.map { it.plainText }
    val promptRegex = Regex("""[:\s]([~/\w\.\-]+)[$#%]\s*$""")
    for (line in terminalPlainLines.asReversed()) {
      val clean = line.trim()
      val match = promptRegex.find(clean)
      if (match != null) {
        return match.groupValues[1]
      }
    }
    return "~"
  }

  suspend fun requestLinuxCompletion(
    currentValue: TextFieldValue,
    cycleIndex: Int = 0
  ): CompletionResult {
    val text = currentValue.text
    val cursor = currentValue.selection.end.coerceIn(0, text.length)
    val prefixBeforeCursor = text.substring(0, cursor)
    val textAfterCursor = text.substring(cursor)
    val lastSpaceIndex = prefixBeforeCursor.lastIndexOf(' ')
    val tokenStartIndex = lastSpaceIndex + 1
    val currentToken = prefixBeforeCursor.substring(tokenStartIndex)

    val cwd = extractCwdFromTerminal()

    // 1. Query real Linux host via SSH compgen
    val remoteCandidates = sshManager.queryLinuxCompletions(prefixBeforeCursor, cwd)

    // 2. If remote returns results, use them directly (100% real Linux completions)
    val matches = if (remoteCandidates.isNotEmpty()) {
      remoteCandidates
    } else {
      // Fallback: extract words from recent terminal screen buffer
      val terminalPlainLines = sshManager.terminalParser.lines.value.map { it.plainText }
      val terminalWords = CommandCompletionEngine.extractWordsFromTerminal(terminalPlainLines)
      terminalWords.filter { it.startsWith(currentToken, ignoreCase = true) }
        .map {
          val isDir = it.endsWith('/')
          CandidateItem(it, it, if (isDir) CandidateType.DIRECTORY else CandidateType.FILE, isDirectory = isDir)
        }
    }

    return CommandCompletionEngine.buildResult(
      currentValue = currentValue,
      tokenStartIndex = tokenStartIndex,
      currentToken = currentToken,
      textAfterCursor = textAfterCursor,
      matches = matches,
      cycleIndex = cycleIndex
    )
  }

  fun sendSnippet(snippet: Snippet) {
    val cmd = if (snippet.autoExecute && !snippet.command.endsWith("\n")) {
      snippet.command + "\n"
    } else {
      snippet.command
    }
    sshManager.sendInput(cmd)
    if (_currentScreen.value != AppScreen.TERMINAL) {
      _currentScreen.value = AppScreen.TERMINAL
    }
  }

  fun testHostPing(host: HostConfig) {
    viewModelScope.launch {
      val isOnline = sshManager.checkHostPing(host.host, host.port, 3000)
      _pingStatus.value = _pingStatus.value + (host.id to isOnline)
    }
  }

  // Host operations
  fun saveHost(host: HostConfig) {
    viewModelScope.launch(Dispatchers.IO) {
      hostDao.insertHost(host)
    }
  }

  fun deleteHost(host: HostConfig) {
    viewModelScope.launch(Dispatchers.IO) {
      hostDao.deleteHost(host)
    }
  }

  fun toggleHostStar(host: HostConfig) {
    viewModelScope.launch(Dispatchers.IO) {
      hostDao.updateHost(host.copy(isStarred = !host.isStarred))
    }
  }

  // Key operations
  fun generateAndSaveKey(name: String, keyType: String = "RSA") {
    viewModelScope.launch(Dispatchers.IO) {
      val generated = KeyGenerator.generateKey(keyType, "$name-roamsh")
      val key = SshKey(
        name = name,
        keyType = generated.keyType,
        publicKey = generated.publicKey,
        privateKey = generated.privateKey
      )
      sshKeyDao.insertKey(key)
    }
  }

  fun saveKey(key: SshKey) {
    viewModelScope.launch(Dispatchers.IO) {
      sshKeyDao.insertKey(key)
    }
  }

  fun deleteKey(key: SshKey) {
    viewModelScope.launch(Dispatchers.IO) {
      sshKeyDao.deleteKey(key)
    }
  }

  // Snippet operations
  fun saveSnippet(snippet: Snippet) {
    viewModelScope.launch(Dispatchers.IO) {
      snippetDao.insertSnippet(snippet)
    }
  }

  fun deleteSnippet(snippet: Snippet) {
    viewModelScope.launch(Dispatchers.IO) {
      snippetDao.deleteSnippet(snippet)
    }
  }

  // Tunnel operations
  fun saveTunnel(tunnel: PortTunnel) {
    viewModelScope.launch(Dispatchers.IO) {
      portTunnelDao.insertTunnel(tunnel)
    }
  }

  fun toggleTunnelEnabled(tunnel: PortTunnel) {
    viewModelScope.launch(Dispatchers.IO) {
      portTunnelDao.updateTunnel(tunnel.copy(isEnabled = !tunnel.isEnabled))
    }
  }

  fun deleteTunnel(tunnel: PortTunnel) {
    viewModelScope.launch(Dispatchers.IO) {
      portTunnelDao.deleteTunnel(tunnel)
    }
  }
}
