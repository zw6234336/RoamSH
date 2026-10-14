package com.example.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material.icons.filled.Warning
import com.example.ui.AppScreen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
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
  val connectionState by viewModel.sshManager.connectionState.collectAsState()
  val lines by viewModel.sshManager.terminalParser.lines.collectAsState()
  val fontSize by viewModel.terminalFontSize.collectAsState()
  val themeType by viewModel.terminalThemeType.collectAsState()
  val theme = viewModel.currentThemeScheme

  val isCtrlActive by viewModel.isCtrlActive.collectAsState()
  val isAltActive by viewModel.isAltActive.collectAsState()

  // Use persistent LazyListState from ViewModel so scroll position is never lost on tab switch or pause
  val listState = viewModel.terminalListState
  val autoScroll by viewModel.autoScroll.collectAsState()
  var showThemeDialog by remember { mutableStateOf(false) }

  val isKeepAliveEnabled by viewModel.isKeepAliveEnabled.collectAsState()
  var showKeepAliveDialog by remember { mutableStateOf(false) }

  val inputFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

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

  // Auto scroll to bottom when new terminal output arrives
  LaunchedEffect(lines.size, lines.lastOrNull()?.plainText, autoScroll) {
    if (autoScroll && lines.isNotEmpty()) {
      try {
        listState.scrollToItem((lines.size - 1).coerceAtLeast(0))
      } catch (_: Throwable) {}
    }
  }

  val density = androidx.compose.ui.platform.LocalDensity.current
  var terminalWidthPx by remember { mutableIntStateOf(0) }
  var terminalHeightPx by remember { mutableIntStateOf(0) }
  var lastCols by remember { mutableIntStateOf(0) }
  var lastRows by remember { mutableIntStateOf(0) }

  var isLineWrapEnabled by remember { mutableStateOf(false) }
  val horizontalScrollState = rememberScrollState()

  // Auto calculate PTY columns and rows based on screen width/height, font size, and density
  // and dispatch SIGWINCH to remote Linux host so CLIs like agy / top / vim format accurately
  LaunchedEffect(terminalWidthPx, terminalHeightPx, fontSize, density, connectionState) {
    if (terminalWidthPx > 0 && terminalHeightPx > 0 && connectionState is SshConnectionState.Connected) {
      val charWidthPx = with(density) { (fontSize * 0.60f).sp.toPx() }
      val charHeightPx = with(density) { (fontSize * 1.18f).sp.toPx() }
      val horizontalPaddingPx = with(density) { 16.dp.toPx() }
      val verticalPaddingPx = with(density) { 12.dp.toPx() }

      val cols = ((terminalWidthPx - horizontalPaddingPx) / charWidthPx).toInt().coerceIn(24, 200)
      val rows = ((terminalHeightPx - verticalPaddingPx) / charHeightPx).toInt().coerceIn(6, 100)

      if (cols != lastCols || rows != lastRows) {
        lastCols = cols
        lastRows = rows
        viewModel.resizeTerminal(cols, rows, terminalWidthPx, terminalHeightPx)
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
      onBack = { viewModel.navigateTo(AppScreen.HOSTS) },
      onToggleLineWrap = { isLineWrapEnabled = !isLineWrapEnabled },
      onToggleKeepAlive = { showKeepAliveDialog = true },
      onToggleAutoScroll = { viewModel.setAutoScroll(!autoScroll) },
      onOpenThemePicker = { showThemeDialog = true },
      onOpenSettings = { viewModel.navigateTo(AppScreen.SETTINGS) },
      onIncreaseFont = { viewModel.setTerminalFontSize(fontSize + 1) },
      onDecreaseFont = { viewModel.setTerminalFontSize(fontSize - 1) },
      onClearTerminal = { viewModel.sshManager.terminalParser.clear() },
      onDisconnect = { viewModel.disconnectCurrentSession() },
      onReconnect = {
        viewModel.sshManager.currentHost?.let {
          viewModel.connectToHost(it)
        }
      }
    )

    // 2. Terminal Main Output Viewport
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
        .clickable(
          interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
          indication = null
        ) {
          // Tapping anywhere on the terminal output focuses the input field for immediate Chinese / English typing
          inputFocusRequester.requestFocus()
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
        val minWidthDp = with(density) { terminalWidthPx.toDp() }
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
                softWrap = isLineWrapEnabled
              )
            }
          }
        }
      }

      // Scroll lock Floating indicator
      if (!autoScroll) {
        Box(
          modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(12.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xCC000000))
            .clickable { viewModel.setAutoScroll(true) }
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
              text = "滚屏锁定中 (点击回到底部)",
              color = Color.White,
              fontSize = 11.sp
            )
          }
        }
      }
    }

    // 3. Developer Extended Keyboard Sticky Bar
    ExtendedKeyboardBar(
      isCtrlActive = isCtrlActive,
      isAltActive = isAltActive,
      savedPassword = viewModel.sshManager.currentHost?.password,
      focusRequester = inputFocusRequester,
      terminalLines = lines.map { it.plainText },
      onToggleCtrl = { viewModel.toggleCtrlLatch() },
      onToggleAlt = { viewModel.toggleAltLatch() },
      onSendSpecialKey = { viewModel.sendSpecialKey(it) },
      onSendRawInput = { viewModel.sendTerminalInput(it) },
      onRequestCompletion = { tfv, cycle ->
        viewModel.requestLinuxCompletion(tfv, cycle)
      }
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
            tint = if (isKeepAliveEnabled) EmeraldSuccess else Color.Gray
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
            color = Color.LightGray,
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
            color = Color.Black,
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
}

@Composable
private fun TerminalTopBar(
  connectionState: SshConnectionState,
  themeType: TerminalThemeType,
  fontSize: Int,
  autoScroll: Boolean,
  isKeepAliveEnabled: Boolean,
  isLineWrapEnabled: Boolean,
  onBack: () -> Unit,
  onToggleLineWrap: () -> Unit,
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
          modifier = Modifier.size(28.dp)
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "返回主机列表",
            tint = Color.White,
            modifier = Modifier.size(18.dp)
          )
        }
        Spacer(modifier = Modifier.width(2.dp))

        val (dotColor, statusText) = when (connectionState) {
          is SshConnectionState.Connected -> {
            if (connectionState.isSimulated) {
              Pair(Color(0xFF38BDF8), "${connectionState.hostName}")
            } else {
              Pair(EmeraldSuccess, "${connectionState.hostName}")
            }
          }
          is SshConnectionState.Connecting -> Pair(Color(0xFFF59E0B), connectionState.message)
          is SshConnectionState.Error -> Pair(RoseError, "连接异常")
          is SshConnectionState.Disconnected -> Pair(Color.Gray, "已断开")
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
          color = Color.White,
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold,
          maxLines = 1
        )
      }

      // Right: Quick Terminal Controls
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
      ) {
        // Line Wrap / Layout Protection Toggle
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (isLineWrapEnabled) Color(0xFF1E3A8A) else Slate800)
            .clickable(onClick = onToggleLineWrap)
            .padding(horizontal = 6.dp, vertical = 3.dp)
            .testTag("btn_toggle_wrap")
        ) {
          Text(
            text = if (isLineWrapEnabled) "折行" else "对齐",
            color = if (isLineWrapEnabled) Color(0xFF60A5FA) else CyanAccent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
          )
        }

        // Keepalive indicator & toggle
        IconButton(onClick = onToggleKeepAlive, modifier = Modifier.size(28.dp)) {
          Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = "后台保活状态",
            tint = if (isKeepAliveEnabled) EmeraldSuccess else Color.Gray,
            modifier = Modifier.size(16.dp)
          )
        }

        // Font size control
        Text(
          text = "${fontSize}sp",
          color = Color.Gray,
          fontSize = 11.sp,
          fontFamily = FontFamily.Monospace,
          modifier = Modifier.padding(horizontal = 1.dp)
        )
        IconButton(onClick = onDecreaseFont, modifier = Modifier.size(24.dp)) {
          Text("-", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        IconButton(onClick = onIncreaseFont, modifier = Modifier.size(24.dp)) {
          Text("+", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        // Auto-scroll toggle
        IconButton(onClick = onToggleAutoScroll, modifier = Modifier.size(28.dp)) {
          Icon(
            imageVector = if (autoScroll) Icons.Default.LockOpen else Icons.Default.Lock,
            contentDescription = "自动滚动",
            tint = if (autoScroll) CyanAccent else Color.Gray,
            modifier = Modifier.size(16.dp)
          )
        }

        // Quick Theme picker
        IconButton(onClick = onOpenThemePicker, modifier = Modifier.size(28.dp)) {
          Icon(
            imageVector = Icons.Default.ColorLens,
            contentDescription = "终端主题",
            tint = Color.White,
            modifier = Modifier.size(16.dp)
          )
        }

        // Full Terminal Settings & Appearance
        IconButton(
          onClick = onOpenSettings,
          modifier = Modifier
            .size(28.dp)
            .testTag("btn_terminal_settings")
        ) {
          Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = "终端外观与偏好设置",
            tint = CyanAccent,
            modifier = Modifier.size(16.dp)
          )
        }

        // Clear
        IconButton(onClick = onClearTerminal, modifier = Modifier.size(28.dp)) {
          Icon(
            imageVector = Icons.Default.Clear,
            contentDescription = "清屏",
            tint = Color.White,
            modifier = Modifier.size(16.dp)
          )
        }

        // Reconnect or Disconnect
        if (connectionState is SshConnectionState.Connected) {
          IconButton(onClick = onDisconnect, modifier = Modifier.size(28.dp)) {
            Icon(
              imageVector = Icons.Default.PowerSettingsNew,
              contentDescription = "断开连接",
              tint = RoseError,
              modifier = Modifier.size(16.dp)
            )
          }
        } else {
          IconButton(onClick = onReconnect, modifier = Modifier.size(28.dp)) {
            Icon(
              imageVector = Icons.Default.Refresh,
              contentDescription = "重新连接",
              tint = CyanAccent,
              modifier = Modifier.size(16.dp)
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
  softWrap: Boolean = false
) {
  val annotatedString = remember(line, defaultColor) {
    buildAnnotatedString {
      line.segments.forEach { seg ->
        withStyle(
          SpanStyle(
            color = seg.fgColor ?: defaultColor,
            background = seg.bgColor ?: Color.Transparent,
            fontWeight = if (seg.bold) FontWeight.Bold else FontWeight.Normal,
            textDecoration = if (seg.underline) TextDecoration.Underline else TextDecoration.None
          )
        ) {
          append(seg.text)
        }
      }
    }
  }

  Text(
    text = annotatedString,
    fontSize = fontSize.sp,
    fontFamily = FontFamily.Monospace,
    lineHeight = (fontSize * 1.18f).sp,
    softWrap = softWrap,
    style = TextStyle(
      platformStyle = PlatformTextStyle(includeFontPadding = false)
    )
  )
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
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
            Spacer(modifier = Modifier.width(6.dp))
            Text("启动本地模拟沙箱测试", color = Color.Black, fontWeight = FontWeight.Bold)
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
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
            Spacer(modifier = Modifier.width(6.dp))
            Text("立即进入开发者模拟沙箱", color = Color.Black, fontWeight = FontWeight.Bold)
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
