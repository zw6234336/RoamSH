package com.example.ui.terminal

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WrapText
import com.example.ui.AppScreen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TerminalThemeType
import com.example.ssh.SshConnectionState
import com.example.ssh.TerminalLine
import com.example.ui.DevSshViewModel
import com.example.ui.components.ExtendedKeyboardBar
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoseError
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950

import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding

@Composable
fun TerminalScreen(
  viewModel: DevSshViewModel,
  modifier: Modifier = Modifier
) {
  val sessions by viewModel.sessions.collectAsState()
  val activeSessionId by viewModel.activeSessionId.collectAsState()
  val activeSession by viewModel.activeSession.collectAsState()
  val allHosts by viewModel.allHosts.collectAsState()

  val currentSshManager = activeSession?.sshManager ?: viewModel.sshManager
  val connectionState by currentSshManager.connectionState.collectAsState()
  val lines by currentSshManager.terminalParser.lines.collectAsState()
  val fontSize by viewModel.terminalFontSize.collectAsState()
  val themeType by viewModel.terminalThemeType.collectAsState()
  val theme = viewModel.currentThemeScheme

  val isCtrlActive by viewModel.isCtrlActive.collectAsState()
  val isAltActive by viewModel.isAltActive.collectAsState()

  // Use persistent LazyListState from active session so each tab preserves its own position
  val listState = activeSession?.listState ?: viewModel.terminalListState
  val autoScroll by (activeSession?.autoScroll ?: viewModel.autoScroll).collectAsState()
  var showThemeDialog by remember { mutableStateOf(false) }

  val isKeepAliveEnabled by viewModel.isKeepAliveEnabled.collectAsState()
  var showKeepAliveDialog by remember { mutableStateOf(false) }
  var isTmuxModeActive by remember { mutableStateOf(false) }

  val inputFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

  // Terminal clipboard affordances: long-press the output viewport to copy/paste
  val hapticFeedback = LocalHapticFeedback.current
  var showClipboardSheet by remember { mutableStateOf(false) }

  // One-shot hint when swiping inside an alternate-screen app (tmux) that has NOT
  // enabled mouse reporting: without it the swipe cannot reach tmux's remote
  // history. Offer the exec-channel fix right where the gesture failed.
  var showTmuxScrollHint by remember { mutableStateOf(false) }
  var tmuxScrollHintDismissed by remember { mutableStateOf(false) }

  // Runtime notification permission launcher for Android 13+
  val notificationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
    contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
  ) {}

  LaunchedEffect(connectionState) {
    if (connectionState is SshConnectionState.Connected && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
      try {
        notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
      } catch (_: Throwable) {}
    }
  }

  // Lifecycle resume listener: safely scrolls to bottom when screen resumes or returning from background/other apps
  val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
  LaunchedEffect(lifecycleOwner) {
    lifecycleOwner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
      if (autoScroll && lines.isNotEmpty()) {
        try {
          listState.scrollToItem((lines.size - 1).coerceAtLeast(0))
        } catch (_: Throwable) {}
      }
    }
  }

  val coroutineScope = rememberCoroutineScope()

  // Auto scroll to bottom when new terminal output arrives, only while following is on
  // and the user isn't mid-scroll. Swiping up pauses following (below); a plain list
  // otherwise handles all touch scrolling natively.
  LaunchedEffect(lines.size, lines.lastOrNull()?.plainText, autoScroll) {
    if (autoScroll && lines.isNotEmpty() && !listState.isScrollInProgress) {
      try {
        listState.scrollToItem((lines.size - 1).coerceAtLeast(0))
      } catch (_: Throwable) {}
    }
  }

  // Pause autoScroll when a scroll gesture ENDS with the viewport away from the bottom;
  // resume when it settles back at (or within ~2 rows of) the bottom so a fling that
  // stops just short still re-engages follow mode. Deciding at gesture START is
  // unreliable: the first frame of an upward drag still reports "at bottom", and
  // programmatic scrollToItem also flips isScrollInProgress, so it never disabled.
  LaunchedEffect(Unit) {
    var wasScrolling = false
    androidx.compose.runtime.snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
      if (wasScrolling && !scrolling) {
        val info = listState.layoutInfo
        val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
        val nearBottom = info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - 2
        viewModel.setAutoScroll(nearBottom)
      }
      wasScrolling = scrolling
    }
  }

  val density = androidx.compose.ui.platform.LocalDensity.current
  var terminalWidthPx by remember { mutableIntStateOf(0) }
  var terminalHeightPx by remember { mutableIntStateOf(0) }
  var maxStableHeightPx by remember { mutableIntStateOf(0) }
  var lastWidthPx by remember { mutableIntStateOf(0) }
  var lastCols by remember { mutableIntStateOf(0) }
  var lastRows by remember { mutableIntStateOf(0) }

  // Default to line wrap enabled on mobile devices for optimal readability and vertical scroll ergonomics
  var isLineWrapEnabled by remember { mutableStateOf(true) }
  val horizontalScrollState = rememberScrollState()

  // Exact character cell metrics measured via Paint with monospace typeface
  val actualCharMetrics = remember(fontSize, density) {
    val paint = android.graphics.Paint().apply {
      typeface = android.graphics.Typeface.MONOSPACE
      textSize = with(density) { fontSize.sp.toPx() }
    }
    val measuredWidth = paint.measureText("M")
    val width = if (measuredWidth > 0f) measuredWidth else with(density) { (fontSize * 0.60f).sp.toPx() }
    val height = with(density) { (fontSize * 1.18f).sp.toPx() }
    Pair(width, height)
  }
  val charWidthPx = actualCharMetrics.first
  val charHeightPx = actualCharMetrics.second
  val horizontalPaddingPx = with(density) { 16.dp.toPx() }
  val verticalPaddingPx = with(density) { 12.dp.toPx() }

  // Auto calculate PTY columns and rows based on actual screen width/height and exact monospace font metrics,
  // and dispatch SIGWINCH to remote Linux host so CLIs like agy / claude code / aider / tmux format accurately.
  // Using maxStableHeightPx ensures soft keyboard (IME) popup doesn't shrink rows and corrupt tmux/curses state!
  LaunchedEffect(terminalWidthPx, terminalHeightPx, fontSize, density, charWidthPx, charHeightPx) {
    if (terminalWidthPx > 0 && terminalHeightPx > 0 && charWidthPx > 0f && charHeightPx > 0f) {
      if (terminalWidthPx != lastWidthPx) {
        lastWidthPx = terminalWidthPx
        maxStableHeightPx = terminalHeightPx
      } else if (terminalHeightPx > maxStableHeightPx) {
        maxStableHeightPx = terminalHeightPx
      }

      val effectiveHeightPx = maxOf(maxStableHeightPx, terminalHeightPx)
      val usableWidth = (terminalWidthPx - horizontalPaddingPx).coerceAtLeast(100f)
      val usableHeight = (effectiveHeightPx - verticalPaddingPx).coerceAtLeast(100f)

      val cols = (usableWidth / charWidthPx).toInt().coerceIn(20, 250)
      val rows = (usableHeight / charHeightPx).toInt().coerceIn(6, 120)

      if (cols != lastCols || rows != lastRows) {
        lastCols = cols
        lastRows = rows
        viewModel.resizeTerminal(cols, rows, terminalWidthPx, effectiveHeightPx)
      }
    }
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(theme.background)
      .statusBarsPadding()
      .imePadding()
  ) {
    // 1. Top Terminal Status & Control Bar
    TerminalTopBar(
      connectionState = connectionState,
      themeType = themeType,
      fontSize = fontSize,
      autoScroll = autoScroll,
      isKeepAliveEnabled = isKeepAliveEnabled,
      isLineWrapEnabled = isLineWrapEnabled,
      isTmuxActive = isTmuxModeActive,
      cols = lastCols,
      onBack = { viewModel.navigateTo(AppScreen.HOSTS) },
      onToggleLineWrap = { isLineWrapEnabled = !isLineWrapEnabled },
      onToggleTmux = { isTmuxModeActive = !isTmuxModeActive },
      onToggleKeepAlive = { showKeepAliveDialog = true },
      onToggleAutoScroll = { viewModel.setAutoScroll(!autoScroll) },
      onOpenThemePicker = { showThemeDialog = true },
      onOpenSettings = { viewModel.navigateTo(AppScreen.SETTINGS) },
      onIncreaseFont = { viewModel.setTerminalFontSize(fontSize + 1) },
      onDecreaseFont = { viewModel.setTerminalFontSize(fontSize - 1) },
      onClearTerminal = { currentSshManager.terminalParser.clear() },
      onDisconnect = { viewModel.disconnectCurrentSession() },
      onReconnect = { viewModel.reconnectCurrentSession() }
    )

    // 2. Multi-Session Tab Strip
    if (sessions.isNotEmpty()) {
      TerminalTabBar(
        sessions = sessions,
        activeSessionId = activeSessionId,
        allHosts = allHosts,
        onSelectSession = { viewModel.selectSession(it) },
        onCloseSession = { viewModel.closeSession(it) },
        onRenameSession = { id, title -> viewModel.renameSession(id, title) },
        onNewTab = { host, isSim -> viewModel.createNewTab(host, isSim) }
      )
    }

    // 2. Terminal Main Output Viewport
    // Plain scrollable area: swiping behaves like a normal list. When the list cannot
    // scroll any further (e.g. a full-screen tmux whose history lives on the remote),
    // the leftover swipe distance is forwarded to the remote as mouse-wheel events, so
    // tmux/vim scroll their own history. Only active when the remote enabled mouse
    // reporting (DECSET 1000/1002/1003), so a plain shell never sees wheel garbage.
    val wheelAccumulator = remember { FloatArray(1) }
    val wheelConnection = remember(viewModel, activeSession, charHeightPx) {
      val sshManager = activeSession?.sshManager
      object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
        override fun onPostScroll(
          consumed: androidx.compose.ui.geometry.Offset,
          available: androidx.compose.ui.geometry.Offset,
          source: androidx.compose.ui.input.nestedscroll.NestedScrollSource
        ): androidx.compose.ui.geometry.Offset {
          if (source != androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput ||
            available.y == 0f || charHeightPx <= 0f
          ) {
            return androidx.compose.ui.geometry.Offset.Zero
          }
          val parser = sshManager?.terminalParser
          if (parser?.isMouseReporting != true) {
            wheelAccumulator[0] = 0f
            // tmux (alternate screen) without mouse reporting: the swipe can never
            // reach remote history — surface the one-tap enable hint instead.
            if (parser?.isAlternateScreen == true && !tmuxScrollHintDismissed && !showTmuxScrollHint) {
              showTmuxScrollHint = true
            }
            return androidx.compose.ui.geometry.Offset.Zero
          }
          wheelAccumulator[0] += available.y
          val steps = (wheelAccumulator[0] / charHeightPx).toInt()
          if (steps == 0) return androidx.compose.ui.geometry.Offset.Zero
          wheelAccumulator[0] -= steps * charHeightPx
          // SGR wheel at the screen center: 64 = wheel up, 65 = wheel down (1-based cells).
          val cellRow = (parser.rows / 2).coerceAtLeast(1)
          val cellCol = (parser.cols / 2).coerceAtLeast(1)
          val seq = if (steps > 0) {
            buildString { repeat(steps) { append("\u001b[<65;$cellCol;${cellRow}M") } }
          } else {
            buildString { repeat(-steps) { append("\u001b[<64;$cellCol;${cellRow}M") } }
          }
          viewModel.sendTerminalInput(seq)
          return available
        }
      }
    }
    Box(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .background(theme.background)
        .onSizeChanged { size ->
          if (size.width > 0 && size.height > 0) {
            terminalWidthPx = size.width
            terminalHeightPx = size.height
          }
        }
        .nestedScroll(wheelConnection)
        .pointerInput(Unit) {
          detectTapGestures(
            // Tap focuses the hidden terminal input so the soft keyboard can come up;
            // long-press opens the copy/paste sheet — the only way to get terminal
            // output into the clipboard since the viewport is a custom Canvas grid.
            onTap = { inputFocusRequester.requestFocus() },
            onLongPress = {
              hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
              showClipboardSheet = true
            }
          )
        }
    ) {
      if (lines.isEmpty()) {
        EmptyTerminalPlaceholder(
          connectionState = connectionState,
          onStartSandbox = {
            viewModel.sshManager.currentHost?.let {
              viewModel.connectToHost(it, forceSimulation = true)
            } ?: run {
              // Create default fallback host
              val defaultHost = viewModel.allHosts.value.firstOrNull()
              if (defaultHost != null) {
                viewModel.connectToHost(defaultHost, forceSimulation = true)
              }
            }
          }
        )
      } else {
        val minWidthDp = with(density) {
          if (isLineWrapEnabled) {
            terminalWidthPx.toDp()
          } else {
            val maxContentCols = lines.maxOfOrNull { it.plainText.length } ?: lastCols
            val requiredCols = maxOf(lastCols, maxContentCols)
            val contentWidthPx = requiredCols * charWidthPx + horizontalPaddingPx
            maxOf(terminalWidthPx.toFloat(), contentWidthPx).toDp()
          }
        }
        val terminalModifier = if (isLineWrapEnabled) {
          Modifier.fillMaxSize()
        } else {
          Modifier
            .fillMaxHeight()
            .defaultMinSize(minWidth = minWidthDp)
            .horizontalScroll(horizontalScrollState)
        }

        LazyColumn(
          state = listState,
          contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
          modifier = terminalModifier.testTag("terminal_output_list")
        ) {
          items(lines.size) { index ->
            val line = lines.getOrNull(index)
            if (line != null) {
              TerminalLineItem(
                line = line,
                fontSize = fontSize,
                defaultColor = theme.foreground,
                charWidthPx = charWidthPx,
                charHeightPx = charHeightPx,
                softWrap = isLineWrapEnabled
              )
            }
          }
        }
      }

      // tmux 触控滚屏引导（备用屏 + 未开 mouse 时滑动触发，一次性）
      if (showTmuxScrollHint) {
        Row(
          modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(12.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Slate800.copy(alpha = 0.93f))
            .border(1.dp, CyanAccent.copy(alpha = 0.5f), RoundedCornerShape(20.dp)),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "滑动查看 tmux 历史需开启触控滚屏",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 11.sp,
            modifier = Modifier.padding(start = 12.dp)
          )
          Text(
            text = "开启",
            color = MaterialTheme.colorScheme.onPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
              .padding(horizontal = 8.dp, vertical = 6.dp)
              .clip(RoundedCornerShape(14.dp))
              .background(CyanAccent)
              .clickable {
                showTmuxScrollHint = false
                tmuxScrollHintDismissed = true
                coroutineScope.launch {
                  if (viewModel.setTmuxMouse(true)) {
                    viewModel.setAutoScroll(true)
                  }
                }
              }
              .padding(horizontal = 10.dp, vertical = 4.dp)
          )
          Icon(
            imageVector = Icons.Default.Clear,
            contentDescription = "忽略提示",
            tint = Color.LightGray,
            modifier = Modifier
              .clickable {
                showTmuxScrollHint = false
                tmuxScrollHintDismissed = true
              }
              .padding(6.dp)
              .size(14.dp)
          )
        }
      }

      // Scroll lock Floating indicator & Navigation pills
      if (!autoScroll) {
        Row(
          modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(12.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          // Jump to Top button
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(20.dp))
              .background(Slate800.copy(alpha = 0.93f))
              .clickable {
                coroutineScope.launch {
                  try {
                    listState.animateScrollToItem(0)
                  } catch (_: Throwable) {
                    listState.scrollToItem(0)
                  }
                }
              }
              .padding(horizontal = 10.dp, vertical = 6.dp)
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.ArrowUpward,
                contentDescription = "回到顶部",
                tint = CyanAccent,
                modifier = Modifier.size(15.dp)
              )
              Spacer(modifier = Modifier.width(3.dp))
              Text(
                text = "回到顶部",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
              )
            }
          }

          // Resume Auto-scroll & Jump to Bottom button
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(20.dp))
              .background(Slate900.copy(alpha = 0.93f))
              .clickable {
                viewModel.setAutoScroll(true)
                coroutineScope.launch {
                  try {
                    val lastIdx = (lines.size - 1).coerceAtLeast(0)
                    listState.animateScrollToItem(lastIdx)
                  } catch (_: Throwable) {}
                }
              }
              .padding(horizontal = 12.dp, vertical = 6.dp)
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.ArrowDownward,
                contentDescription = "恢复自动滚动",
                tint = CyanAccent,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = "回到底部 (恢复滚屏)",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
              )
            }
          }
        }
      }
    }

    // Dedicated Tmux / Screen Control Bar
    AnimatedVisibility(
      visible = isTmuxModeActive,
      enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
      exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
    ) {
      TmuxControlBar(
        onSendRawInput = { viewModel.sendTerminalInput(it) },
        onSendSpecialKey = { viewModel.sendSpecialKey(it) },
        onQueryTmuxState = { viewModel.queryTmuxState() },
        onSetTmuxMouse = { viewModel.setTmuxMouse(it) },
        shouldAutoEnableMouse = { viewModel.userTmuxMousePreference == null },
        onFeedback = { viewModel.showMessage(it) },
        onCloseBar = { isTmuxModeActive = false }
      )
    }

    // 3. Developer Extended Keyboard Sticky Bar
    ExtendedKeyboardBar(
      isCtrlActive = isCtrlActive,
      isAltActive = isAltActive,
      isTmuxActive = isTmuxModeActive,
      onToggleTmux = { isTmuxModeActive = !isTmuxModeActive },
      savedPassword = activeSession?.host?.password,
      focusRequester = inputFocusRequester,
      terminalLines = lines.map { it.plainText },
      onToggleCtrl = { viewModel.toggleCtrlLatch() },
      onToggleAlt = { viewModel.toggleAltLatch() },
      onSendSpecialKey = { viewModel.sendSpecialKey(it) },
      onSendRawInput = { viewModel.sendTerminalInput(it) },
      onRequestCompletion = { tfv, cycle ->
        viewModel.requestLinuxCompletion(tfv, cycle)
      },
      onFeedback = { viewModel.showMessage(it) }
    )
  }

  // Theme Picker Dialog
  // Keepalive Info & Control Dialog
  if (showKeepAliveDialog) {
    AlertDialog(
      onDismissRequest = { showKeepAliveDialog = false },
      title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = if (isKeepAliveEnabled) EmeraldSuccess else MaterialTheme.colorScheme.onSurfaceVariant
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(text = "SSH 后台连接保活设置", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text = "状态: " + if (isKeepAliveEnabled) "已开启 (前台服务 + WakeLock 唤醒锁)" else "已关闭 (后台切屏可能断开)",
            color = if (isKeepAliveEnabled) EmeraldSuccess else RoseError,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp
          )
          Text(
            text = "工作机制:\n• 开启后启动系统前台服务与通知栏常驻卡片，防止系统杀后台。\n• 自动获取 CPU 浅度唤醒锁 (WakeLock)，手机熄屏仍持续运行。\n• 底层定期发送 SSH 心跳包 (ServerAliveInterval=15s)，防止路由器超时断网。\n• 即使切到微信、浏览器或其它软件，远程长耗时编译或服务绝不断联！",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            lineHeight = 17.sp
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.toggleKeepAlive()
            showKeepAliveDialog = false
          },
          colors = ButtonDefaults.buttonColors(
            containerColor = if (isKeepAliveEnabled) RoseError else CyanAccent
          )
        ) {
          Text(
            text = if (isKeepAliveEnabled) "关闭后台保活" else "开启后台保活",
            color = if (isKeepAliveEnabled) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.Bold
          )
        }
      },
      dismissButton = {
        TextButton(onClick = { showKeepAliveDialog = false }) {
          Text("我知道了")
        }
      }
    )
  }

  // Theme Picker Dialog
  if (showThemeDialog) {
    ThemePickerDialog(
      currentTheme = themeType,
      onSelectTheme = {
        viewModel.setTerminalTheme(it)
        showThemeDialog = false
      },
      onOpenSettings = {
        showThemeDialog = false
        viewModel.navigateTo(AppScreen.SETTINGS)
      },
      onDismiss = { showThemeDialog = false }
    )
  }

  // Long-press copy/paste sheet for the terminal viewport
  if (showClipboardSheet) {
    TerminalClipboardSheet(
      lines = lines,
      listState = listState,
      isConnected = connectionState is SshConnectionState.Connected,
      onPasteToTerminal = { text ->
        viewModel.sendTerminalInput(text)
      },
      onFeedback = { viewModel.showMessage(it) },
      onDismiss = { showClipboardSheet = false }
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TerminalClipboardSheet(
  lines: List<TerminalLine>,
  listState: androidx.compose.foundation.lazy.LazyListState,
  isConnected: Boolean,
  onPasteToTerminal: (String) -> Unit,
  onFeedback: (String) -> Unit = {},
  onDismiss: () -> Unit
) {
  val clipboard = LocalClipboardManager.current
  val clipboardText = clipboard.getText()?.text?.trim().orEmpty()

  fun copyToClipboard(text: String, lineCount: Int) {
    clipboard.setText(AnnotatedString(text))
    onFeedback("已复制 $lineCount 行终端输出")
    onDismiss()
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    containerColor = Slate900
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp)
        .padding(bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Text(
        text = "终端复制 / 粘贴",
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold
      )
      Text(
        text = "长按终端输出区域可随时呼出此面板",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp
      )
      Spacer(modifier = Modifier.height(4.dp))

      // Visible portion of the scrollback (matches what's on screen right now)
      val visibleText = listState.layoutInfo.visibleItemsInfo
        .mapNotNull { lines.getOrNull(it.index)?.plainText }

      ClipboardSheetAction(
        icon = Icons.Default.ContentCopy,
        title = "复制屏幕可见输出",
        subtitle = "当前视口内的 ${visibleText.size} 行内容",
        enabled = visibleText.isNotEmpty(),
        onClick = { copyToClipboard(visibleText.joinToString("\n"), visibleText.size) }
      )
      ClipboardSheetAction(
        icon = Icons.Default.ContentCopy,
        title = "复制最近 100 行",
        subtitle = "适合快速分享报错上下文",
        enabled = lines.isNotEmpty(),
        onClick = {
          val recent = lines.takeLast(100)
          copyToClipboard(recent.joinToString("\n") { it.plainText }, recent.size)
        }
      )
      ClipboardSheetAction(
        icon = Icons.Default.ContentCopy,
        title = "复制全部输出",
        subtitle = "回滚缓冲区共 ${lines.size} 行",
        enabled = lines.isNotEmpty(),
        onClick = {
          copyToClipboard(lines.joinToString("\n") { it.plainText }, lines.size)
        }
      )
      ClipboardSheetAction(
        icon = Icons.Default.ContentPaste,
        title = "粘贴到终端",
        subtitle = if (isConnected) {
          if (clipboardText.contains('\n')) "剪贴板为多行内容，将逐行发送并执行" else "将剪贴板内容发送到远程终端"
        } else {
          "终端未连接，无法粘贴"
        },
        enabled = isConnected && clipboardText.isNotEmpty(),
        tint = EmeraldSuccess,
        onClick = {
          val payload = clipboardText.replace("\r\n", "\n").replace("\n", "\r")
          onPasteToTerminal(payload)
          onFeedback("已粘贴 ${clipboardText.length} 个字符到终端")
          onDismiss()
        }
      )
    }
  }
}

@Composable
private fun ClipboardSheetAction(
  icon: ImageVector,
  title: String,
  subtitle: String,
  enabled: Boolean = true,
  tint: Color = CyanAccent,
  onClick: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(10.dp))
      .background(if (enabled) Slate800 else Slate800.copy(alpha = 0.4f))
      .clickable(enabled = enabled) { onClick() }
      .padding(horizontal = 14.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = if (enabled) tint else MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(20.dp)
    )
    Spacer(modifier = Modifier.width(12.dp))
    Column {
      Text(
        text = title,
        color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium
      )
      Text(text = subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
    }
  }
}

@Composable
private fun TerminalTopBar(
  connectionState: SshConnectionState,
  themeType: TerminalThemeType,
  fontSize: Int,
  autoScroll: Boolean,
  isKeepAliveEnabled: Boolean,
  isLineWrapEnabled: Boolean,
  isTmuxActive: Boolean = false,
  cols: Int = 0,
  onBack: () -> Unit,
  onToggleLineWrap: () -> Unit,
  onToggleTmux: () -> Unit = {},
  onToggleKeepAlive: () -> Unit,
  onToggleAutoScroll: () -> Unit,
  onOpenThemePicker: () -> Unit,
  onOpenSettings: () -> Unit,
  onIncreaseFont: () -> Unit,
  onDecreaseFont: () -> Unit,
  onClearTerminal: () -> Unit,
  onDisconnect: () -> Unit,
  onReconnect: () -> Unit
) {
  Surface(
    color = Slate950,
    tonalElevation = 4.dp,
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 6.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      // Left: Back button & Host status pill
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.weight(1f, fill = false)
      ) {
        IconButton(
          onClick = onBack,
          modifier = Modifier.size(34.dp)
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "返回主机列表",
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp)
          )
        }
        Spacer(modifier = Modifier.width(2.dp))

        val (dotColor, statusText) = when (connectionState) {
          is SshConnectionState.Connected -> {
            if (connectionState.isSimulated) {
              Pair(CyanAccent, "${connectionState.hostName}")
            } else {
              Pair(EmeraldSuccess, "${connectionState.hostName}")
            }
          }
          is SshConnectionState.Connecting -> Pair(AmberWarning, connectionState.message)
          is SshConnectionState.Error -> Pair(RoseError, "连接异常")
          is SshConnectionState.Disconnected -> Pair(MaterialTheme.colorScheme.onSurfaceVariant, "已断开")
        }

        Box(
          modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(dotColor)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
          text = statusText,
          color = MaterialTheme.colorScheme.onSurface,
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold,
          maxLines = 1
        )
      }

      // Right: Quick Terminal Controls — high-frequency actions (font size, overflow
      // menu, connection) get proper touch targets; lower-frequency toggles (wrap,
      // tmux bar, autoscroll, keepalive, clear, theme, settings) move into the
      // overflow menu instead of cramming a dozen 24dp controls into one row.
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
      ) {
        // Responsive Live Column Count Badge
        if (cols > 0) {
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(4.dp))
              .background(Slate800)
              .border(1.dp, Slate700, RoundedCornerShape(4.dp))
              .padding(horizontal = 5.dp, vertical = 3.dp)
              .testTag("badge_terminal_cols")
          ) {
            Text(
              text = "${cols}列",
              color = CyanAccent,
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.Monospace,
              style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
            )
          }
        }

        // Line wrap / tmux / keepalive toggles moved into the overflow menu below
        // (previously three cramped 24-28dp controls sat here).

        // Font size control
        IconButton(onClick = onDecreaseFont, modifier = Modifier.size(34.dp)) {
          Text("-", color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Text(
          text = "${fontSize}sp",
          color = CyanAccent,
          fontSize = 11.sp,
          fontFamily = FontFamily.Monospace,
          modifier = Modifier.padding(horizontal = 1.dp)
        )
        IconButton(onClick = onIncreaseFont, modifier = Modifier.size(34.dp)) {
          Text("+", color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        // Overflow menu for lower-frequency terminal toggles
        Box {
          var menuExpanded by remember { mutableStateOf(false) }
          IconButton(
            onClick = { menuExpanded = true },
            modifier = Modifier
              .size(34.dp)
              .testTag("btn_terminal_more")
          ) {
            Icon(
              imageVector = Icons.Default.MoreVert,
              contentDescription = "更多终端操作",
              tint = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.size(20.dp)
            )
          }
          DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            containerColor = Slate800
          ) {
            DropdownMenuItem(
              text = {
                Text(
                  if (isLineWrapEnabled) "自动折行：开" else "自动折行：关",
                  color = MaterialTheme.colorScheme.onSurface
                )
              },
              leadingIcon = {
                Icon(Icons.Default.WrapText, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(18.dp))
              },
              onClick = {
                menuExpanded = false
                onToggleLineWrap()
              },
              modifier = Modifier.testTag("btn_toggle_wrap")
            )
            DropdownMenuItem(
              text = {
                Text(
                  if (isTmuxActive) "Tmux 控制条：显示" else "Tmux 控制条：隐藏",
                  color = MaterialTheme.colorScheme.onSurface
                )
              },
              leadingIcon = {
                Icon(Icons.Default.Terminal, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(18.dp))
              },
              onClick = {
                menuExpanded = false
                onToggleTmux()
              },
              modifier = Modifier.testTag("btn_topbar_tmux")
            )
            DropdownMenuItem(
              text = {
                Text(
                  if (autoScroll) "跟随输出滚动：开" else "跟随输出滚动：关",
                  color = MaterialTheme.colorScheme.onSurface
                )
              },
              leadingIcon = {
                Icon(
                  if (autoScroll) Icons.Default.LockOpen else Icons.Default.Lock,
                  contentDescription = null,
                  tint = CyanAccent,
                  modifier = Modifier.size(18.dp)
                )
              },
              onClick = {
                menuExpanded = false
                onToggleAutoScroll()
              }
            )
            DropdownMenuItem(
              text = { Text("清空屏幕输出", color = MaterialTheme.colorScheme.onSurface) },
              leadingIcon = {
                Icon(Icons.Default.Clear, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
              },
              onClick = {
                menuExpanded = false
                onClearTerminal()
              }
            )
            DropdownMenuItem(
              text = { Text("切换终端主题", color = MaterialTheme.colorScheme.onSurface) },
              leadingIcon = {
                Icon(Icons.Default.ColorLens, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
              },
              onClick = {
                menuExpanded = false
                onOpenThemePicker()
              }
            )
            DropdownMenuItem(
              text = { Text("外观与偏好设置", color = MaterialTheme.colorScheme.onSurface) },
              leadingIcon = {
                Icon(Icons.Default.Settings, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(18.dp))
              },
              onClick = {
                menuExpanded = false
                onOpenSettings()
              },
              modifier = Modifier.testTag("btn_terminal_settings")
            )
            DropdownMenuItem(
              text = {
                Text(
                  if (isKeepAliveEnabled) "后台保活：已开启" else "后台保活：已关闭",
                  color = MaterialTheme.colorScheme.onSurface
                )
              },
              leadingIcon = {
                Icon(
                  Icons.Default.Lock,
                  contentDescription = null,
                  tint = if (isKeepAliveEnabled) EmeraldSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(18.dp)
                )
              },
              onClick = {
                menuExpanded = false
                onToggleKeepAlive()
              }
            )
          }
        }

        // Reconnect or Disconnect
        if (connectionState is SshConnectionState.Connected) {
          IconButton(onClick = onDisconnect, modifier = Modifier.size(34.dp)) {
            Icon(
              imageVector = Icons.Default.PowerSettingsNew,
              contentDescription = "断开连接",
              tint = RoseError,
              modifier = Modifier.size(20.dp)
            )
          }
        } else {
          IconButton(onClick = onReconnect, modifier = Modifier.size(34.dp)) {
            Icon(
              imageVector = Icons.Default.Refresh,
              contentDescription = "重新连接",
              tint = CyanAccent,
              modifier = Modifier.size(20.dp)
            )
          }
        }
      }
    }
  }
}

@Composable
private fun TerminalLineItem(
  line: TerminalLine,
  fontSize: Int,
  defaultColor: Color,
  charWidthPx: Float,
  charHeightPx: Float,
  softWrap: Boolean = false
) {
  val density = androidx.compose.ui.platform.LocalDensity.current

  // Strict-grid renderer: every character is drawn at an exact multiple of the
  // monospace cell width. Compose Text would lay box-drawing / CJK / block glyphs
  // through fallback fonts with non-monospace advances and drift whole columns.
  val paint = remember(fontSize, density) {
    android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
      typeface = android.graphics.Typeface.MONOSPACE
      textSize = with(density) { fontSize.sp.toPx() }
    }
  }

  val rowHeightDp = with(density) { charHeightPx.toDp() }

  if (line.cells.isEmpty()) {
    Spacer(modifier = Modifier.height(rowHeightDp))
    return
  }

  Canvas(modifier = Modifier.fillMaxWidth().height(rowHeightDp)) {
    val fm = paint.fontMetrics
    val textHeight = fm.descent - fm.ascent
    val baseline = (charHeightPx - textHeight) / 2f - fm.ascent

    // Background pass (drawn first so glyphs stay on top)
    for (i in line.cells.indices) {
      val bg = line.cells[i].bg ?: continue
      drawRect(
        color = bg,
        topLeft = Offset(x = i * charWidthPx, y = 0f),
        size = Size(width = charWidthPx, height = charHeightPx)
      )
    }

    // Glyph pass at exact grid positions; continuation cells render nothing
    paint.style = android.graphics.Paint.Style.FILL
    var i = 0
    while (i < line.cells.size) {
      val cell = line.cells[i]
      val cellWidth = if (i + 1 < line.cells.size && line.cells[i + 1].isContinuation) 2 else 1
      if (!cell.isContinuation && cell.char != ' ') {
        paint.color = (cell.fg ?: defaultColor).toArgb()
        paint.isFakeBoldText = cell.bold
        paint.isUnderlineText = cell.underline
        drawContext.canvas.nativeCanvas.drawText(
          cell.char.toString(),
          i * charWidthPx,
          baseline,
          paint
        )
      }
      i += cellWidth
    }
  }
}

@Composable
private fun EmptyTerminalPlaceholder(
  connectionState: SshConnectionState,
  onStartSandbox: () -> Unit
) {
  Box(
    modifier = Modifier.fillMaxSize(),
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier.padding(24.dp)
    ) {
      when (connectionState) {
        is SshConnectionState.Connecting -> {
          CircularProgressIndicator(color = CyanAccent, modifier = Modifier.size(36.dp))
          Spacer(modifier = Modifier.height(16.dp))
          Text(
            text = connectionState.message,
            color = Color.White,
            fontSize = 14.sp
          )
        }
        is SshConnectionState.Error -> {
          Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = "错误",
            tint = RoseError,
            modifier = Modifier.size(42.dp)
          )
          Spacer(modifier = Modifier.height(12.dp))
          Text(
            text = connectionState.message,
            color = RoseError,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
          )
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "排查提示: 请检查手机与远程主机的网络连通性 (是否处于同一局域网或已连接 Tailscale/WireGuard/VPN)、端口是否为 22、用户名及密码或密钥是否正确。",
            color = Color.LightGray,
            fontSize = 11.sp
          )
          Spacer(modifier = Modifier.height(16.dp))
          Button(
            onClick = onStartSandbox,
            colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
          ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
            Spacer(modifier = Modifier.width(6.dp))
            Text("启动本地模拟沙箱测试", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
          }
        }
        else -> {
          Text(
            text = "终端未激活",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
          )
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "请前往「主机」选择电脑进行连接，或直接点击下方按钮体验开发沙箱环境。",
            color = Color.Gray,
            fontSize = 12.sp
          )
          Spacer(modifier = Modifier.height(16.dp))
          Button(
            onClick = onStartSandbox,
            colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
          ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
            Spacer(modifier = Modifier.width(6.dp))
            Text("立即进入开发者模拟沙箱", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  }
}

@Composable
private fun ThemePickerDialog(
  currentTheme: TerminalThemeType,
  onSelectTheme: (TerminalThemeType) -> Unit,
  onOpenSettings: () -> Unit,
  onDismiss: () -> Unit
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("选择终端主题风格") },
    text = {
      Column {
        TerminalThemeType.entries.forEach { type ->
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
              .fillMaxWidth()
              .clickable { onSelectTheme(type) }
              .padding(vertical = 6.dp)
          ) {
            RadioButton(
              selected = currentTheme == type,
              onClick = { onSelectTheme(type) },
              colors = RadioButtonDefaults.colors(selectedColor = CyanAccent)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = type.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() },
              fontSize = 14.sp
            )
          }
        }
      }
    },
    confirmButton = {
      Row {
        TextButton(onClick = onOpenSettings) {
          Text("全量外观与字号设置", color = CyanAccent)
        }
        TextButton(onClick = onDismiss) {
          Text("关闭")
        }
      }
    }
  )
}
