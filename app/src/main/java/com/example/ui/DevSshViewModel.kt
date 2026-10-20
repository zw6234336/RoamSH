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

import com.example.ssh.TerminalSession
import com.example.data.model.HostHealthMetrics
import com.example.ssh.HostHealthProber
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.combine

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

  // Multi-session & Tab management
  private val _sessions = MutableStateFlow<List<TerminalSession>>(emptyList())
  val sessions: StateFlow<List<TerminalSession>> = _sessions.asStateFlow()

  private val _activeSessionId = MutableStateFlow<String?>(null)
  val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

  val activeSession: StateFlow<TerminalSession?> = combine(_sessions, _activeSessionId) { list, id ->
    list.firstOrNull { it.id == id } ?: list.lastOrNull()
  }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

  private val fallbackSshManager = SshManager(viewModelScope)

  val sshManager: SshManager
    get() = activeSession.value?.sshManager ?: fallbackSshManager

  val allHosts: StateFlow<List<HostConfig>> = hostDao.getAllHosts()
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  val allKeys: StateFlow<List<SshKey>> = sshKeyDao.getAllKeys()
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  val allSnippets: StateFlow<List<Snippet>> = snippetDao.getAllSnippets()
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  val allTunnels: StateFlow<List<PortTunnel>> = portTunnelDao.getAllTunnels()
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  // LazyListState preserved per active session
  private val fallbackListState = androidx.compose.foundation.lazy.LazyListState()
  val terminalListState: androidx.compose.foundation.lazy.LazyListState
    get() = activeSession.value?.listState ?: fallbackListState

  private val _fallbackAutoScroll = MutableStateFlow(true)
  val autoScroll: StateFlow<Boolean>
    get() = activeSession.value?.autoScroll?.asStateFlow() ?: _fallbackAutoScroll.asStateFlow()

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

  // Host health metrics map (hostId -> HostHealthMetrics)
  private val _hostHealthMap = MutableStateFlow<Map<String, HostHealthMetrics>>(emptyMap())
  val hostHealthMap: StateFlow<Map<String, HostHealthMetrics>> = _hostHealthMap.asStateFlow()

  private val _isGlobalProbing = MutableStateFlow(false)
  val isGlobalProbing: StateFlow<Boolean> = _isGlobalProbing.asStateFlow()

  // Editing state
  private val _selectedHostForEdit = MutableStateFlow<HostConfig?>(null)
  val selectedHostForEdit: StateFlow<HostConfig?> = _selectedHostForEdit.asStateFlow()

  // Background keepalive toggle
  private val _isKeepAliveEnabled = MutableStateFlow(true)
  val isKeepAliveEnabled: StateFlow<Boolean> = _isKeepAliveEnabled.asStateFlow()

  init {
    SshKeepAliveService.onDisconnectRequested = {
      disconnectAllSessions()
    }
    // Auto probe hosts once loaded
    viewModelScope.launch {
      allHosts.collect { hosts ->
        if (hosts.isNotEmpty() && _hostHealthMap.value.isEmpty()) {
          probeAllHostsHealth()
        }
      }
    }
  }

  fun setAutoScroll(enabled: Boolean) {
    activeSession.value?.autoScroll?.value = enabled
    if (enabled) {
      scrollToBottom()
    }
  }

  fun scrollToBottom() {
    val session = activeSession.value ?: return
    val totalLines = session.sshManager.terminalParser.lines.value.size
    if (totalLines > 0) {
      viewModelScope.launch(Dispatchers.Main) {
        try {
          session.listState.scrollToItem(totalLines - 1)
        } catch (_: Throwable) {}
      }
    }
  }

  fun toggleKeepAlive() {
    val newValue = !_isKeepAliveEnabled.value
    _isKeepAliveEnabled.value = newValue
    updateKeepAliveService()
  }

  fun selectSession(sessionId: String) {
    _activeSessionId.value = sessionId
    activeSession.value?.let { session ->
      _terminalFontSize.value = session.host.fontSize
      setTerminalTheme(session.host.theme)
      scrollToBottom()
    }
  }

  fun closeSession(sessionId: String) {
    val sessionToClose = _sessions.value.firstOrNull { it.id == sessionId } ?: return
    sessionToClose.sshManager.disconnect()
    val updated = _sessions.value.filter { it.id != sessionId }
    _sessions.value = updated

    if (_activeSessionId.value == sessionId) {
      _activeSessionId.value = updated.lastOrNull()?.id
    }

    updateKeepAliveService()

    if (updated.isEmpty()) {
      _currentScreen.value = AppScreen.HOSTS
    }
  }

  fun renameSession(sessionId: String, newTitle: String) {
    val trimmed = newTitle.trim()
    if (trimmed.isEmpty()) return
    _sessions.value = _sessions.value.map {
      if (it.id == sessionId) it.copy(title = trimmed) else it
    }
  }

  fun disconnectCurrentSession() {
    activeSession.value?.let { session ->
      session.sshManager.disconnect()
      updateKeepAliveService()
    }
  }

  fun reconnectCurrentSession() {
    activeSession.value?.let { session ->
      viewModelScope.launch {
        val key = session.host.keyId?.let { sshKeyDao.getKeyById(it) }
        val currentTunnels = try {
          portTunnelDao.getTunnelsForHost(session.host.id).firstOrNull() ?: emptyList()
        } catch (_: Throwable) {
          emptyList()
        }
        session.sshManager.connect(
          host = session.host,
          key = key,
          tunnels = currentTunnels,
          forceSimulation = session.isSimulated
        )
      }
    }
  }

  fun disconnectAllSessions() {
    SshKeepAliveService.stop(getApplication<Application>())
    _sessions.value.forEach { it.sshManager.disconnect() }
  }

  private fun updateKeepAliveService() {
    if (!_isKeepAliveEnabled.value) {
      SshKeepAliveService.stop(getApplication<Application>())
      return
    }

    val connectedSessions = _sessions.value.filter {
      val state = it.sshManager.connectionState.value
      state is SshConnectionState.Connected && !state.isSimulated
    }

    if (connectedSessions.isNotEmpty()) {
      val count = connectedSessions.size
      val title = if (count == 1) {
        connectedSessions.first().title
      } else {
        "$count 个活跃终端会话 (${connectedSessions.first().title} 等)"
      }
      val hostAddress = connectedSessions.first().let {
        "${it.host.username}@${it.host.host}"
      }
      SshKeepAliveService.start(getApplication<Application>(), title, hostAddress)
    } else {
      SshKeepAliveService.stop(getApplication<Application>())
    }
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
    val theme = TerminalThemes.get(type)
    _sessions.value.forEach { it.sshManager.terminalParser.setTheme(theme) }
    fallbackSshManager.terminalParser.setTheme(theme)
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

  fun createNewTab(host: HostConfig, forceSimulation: Boolean = false) {
    connectToHost(host, forceSimulation = forceSimulation, newTab = true)
  }

  fun connectToHost(host: HostConfig, forceSimulation: Boolean = false, newTab: Boolean = false) {
    viewModelScope.launch {
      hostDao.updateLastConnected(host.id, System.currentTimeMillis())
      _terminalFontSize.value = host.fontSize
      setTerminalTheme(host.theme)
      _currentScreen.value = AppScreen.TERMINAL

      // If user tapped a host from host list and NOT explicitly requesting a new tab,
      // and an active session for this host already exists, switch to it!
      val existingSession = _sessions.value.firstOrNull { it.host.id == host.id }
      if (!newTab && existingSession != null) {
        _activeSessionId.value = existingSession.id
        if (existingSession.sshManager.connectionState.value is SshConnectionState.Connected) {
          scrollToBottom()
          return@launch
        }
      }

      val sameHostCount = _sessions.value.count { it.host.id == host.id }
      val tabTitle = if (sameHostCount > 0) {
        "${host.name} #${sameHostCount + 1}"
      } else {
        host.name
      }

      val newManager = SshManager(viewModelScope)
      newManager.terminalParser.setTheme(currentThemeScheme)

      val newSession = TerminalSession(
        title = tabTitle,
        host = host,
        sshManager = newManager,
        isSimulated = forceSimulation
      )

      _sessions.value = _sessions.value + newSession
      _activeSessionId.value = newSession.id

      // Observe connection state changes for this session to update KeepAliveService
      viewModelScope.launch {
        newManager.connectionState.collect {
          updateKeepAliveService()
        }
      }

      val key = host.keyId?.let { sshKeyDao.getKeyById(it) }
      val currentTunnels = try {
        portTunnelDao.getTunnelsForHost(host.id).firstOrNull() ?: emptyList()
      } catch (_: Throwable) {
        emptyList()
      }

      newManager.connect(
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
    if (remoteCandidates.isNotEmpty()) {
      return CommandCompletionEngine.buildResult(
        currentValue = currentValue,
        tokenStartIndex = tokenStartIndex,
        currentToken = currentToken,
        textAfterCursor = textAfterCursor,
        matches = remoteCandidates,
        cycleIndex = cycleIndex
      )
    }

    // 3. Fallback: Full CommandCompletionEngine completion (covers agy, developer tools, subcommands & terminal words)
    val terminalPlainLines = sshManager.terminalParser.lines.value.map { it.plainText }
    return CommandCompletionEngine.complete(
      currentValue = currentValue,
      recentTerminalLines = terminalPlainLines,
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
    probeHostHealth(host)
  }

  fun probeHostHealth(host: HostConfig) {
    viewModelScope.launch {
      val current = _hostHealthMap.value[host.id] ?: HostHealthMetrics(hostId = host.id)
      _hostHealthMap.value = _hostHealthMap.value + (host.id to current.copy(isProbing = true))

      val activeJschSession = sessions.value.firstOrNull { it.host.id == host.id }?.sshManager?.getSession()
      val result = HostHealthProber.probeHealth(host, sshKeyDao, activeJschSession)

      _hostHealthMap.value = _hostHealthMap.value + (host.id to result.copy(isProbing = false))
      _pingStatus.value = _pingStatus.value + (host.id to result.isOnline)
    }
  }

  fun probeAllHostsHealth() {
    viewModelScope.launch {
      val hosts = allHosts.value
      if (hosts.isEmpty()) return@launch

      _isGlobalProbing.value = true
      val updated = _hostHealthMap.value.toMutableMap()
      hosts.forEach { h ->
        val cur = updated[h.id] ?: HostHealthMetrics(hostId = h.id)
        updated[h.id] = cur.copy(isProbing = true)
      }
      _hostHealthMap.value = updated

      val deferredList = hosts.map { host ->
        async {
          val activeJschSession = sessions.value.firstOrNull { it.host.id == host.id }?.sshManager?.getSession()
          val result = HostHealthProber.probeHealth(host, sshKeyDao, activeJschSession)
          host.id to result
        }
      }

      val results = deferredList.awaitAll()
      val newHealthMap = _hostHealthMap.value.toMutableMap()
      val newPingMap = _pingStatus.value.toMutableMap()

      results.forEach { (hostId, metrics) ->
        newHealthMap[hostId] = metrics.copy(isProbing = false)
        newPingMap[hostId] = metrics.isOnline
      }

      _hostHealthMap.value = newHealthMap
      _pingStatus.value = newPingMap
      _isGlobalProbing.value = false
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
