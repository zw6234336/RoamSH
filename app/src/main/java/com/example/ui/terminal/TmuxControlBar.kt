package com.example.ui.terminal

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ssh.SpecialKeyHandler
import com.example.ssh.TmuxSessionState
import com.example.ssh.TmuxWindowInfo
import com.example.ui.theme.AmberWarning
import androidx.compose.material3.MaterialTheme
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class TerminalMultiplexerType {
  TMUX,
  SCREEN
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TmuxControlBar(
  onSendRawInput: (String) -> Unit,
  onSendSpecialKey: (String) -> Unit,
  onCloseBar: () -> Unit,
  onQueryTmuxState: suspend () -> TmuxSessionState,
  onSetTmuxMouse: suspend (Boolean) -> Boolean = { false },
  shouldAutoEnableMouse: () -> Boolean = { false },
  onFeedback: (String) -> Unit = {},
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  var multiplexerType by remember { mutableStateOf(TerminalMultiplexerType.TMUX) }
  var customPrefix by remember { mutableStateOf<String?>(null) }
  var showModeMenu by remember { mutableStateOf(false) }
  var showAdvancedDialog by remember { mutableStateOf(false) }

  // Touch scroll & Mouse mode. Initial state is unknown until the first server
  // query lands; the toggle only ever sends explicit user-confirmed commands.
  var isTouchScrollActive by remember { mutableStateOf(false) }

  // ---- Real tmux session state (queried over a dedicated exec channel) ----
  var tmuxState by remember { mutableStateOf(TmuxSessionState.NONE) }
  var refreshTick by remember { mutableIntStateOf(0) }
  var isQueryingTmux by remember { mutableStateOf(false) }
  var userToggledMouse by remember { mutableStateOf(false) }
  var autoEnableMouseAttempted by remember { mutableStateOf(false) }
  var windowToRename by remember { mutableStateOf<TmuxWindowInfo?>(null) }
  var windowToKill by remember { mutableStateOf<TmuxWindowInfo?>(null) }

  val tmuxScope = rememberCoroutineScope()
  fun refreshTmux(settleMs: Long = 0L) {
    tmuxScope.launch {
      if (settleMs > 0) delay(settleMs) // let tmux settle after a mutating command
      isQueryingTmux = true
      tmuxState = try {
        onQueryTmuxState()
      } catch (_: Throwable) {
        TmuxSessionState.NONE
      }
      isQueryingTmux = false
      // Adopt the server-side mouse flag unless the user flipped the toggle mid-flight
      if (!userToggledMouse) {
        tmuxState.mouseEnabled?.let { isTouchScrollActive = it }
      }
      userToggledMouse = false

      // Standard tmux ships with `mouse off`, which makes touch swiping dead inside
      // tmux (no wheel events reach it). Opening the tmux bar IS the intent to drive
      // tmux by touch, so turn mouse on once — via the exec channel (zero PTY
      // injection), with visible feedback, and never again once the user chose.
      if (!autoEnableMouseAttempted && !userToggledMouse &&
        tmuxState.mouseEnabled == false && shouldAutoEnableMouse()
      ) {
        autoEnableMouseAttempted = true
        tmuxScope.launch {
          if (onSetTmuxMouse(true)) {
            isTouchScrollActive = true
            onFeedback("已自动开启触控滚屏：可直接上下滑动查看 tmux 历史")
            refreshTmux(settleMs = 400)
          }
        }
      }
    }
  }

  val prefix = customPrefix ?: if (multiplexerType == TerminalMultiplexerType.TMUX) {
    SpecialKeyHandler.CTRL_B
  } else {
    SpecialKeyHandler.CTRL_A
  }

  val prefixLabel = when {
    customPrefix != null -> "Prefix"
    multiplexerType == TerminalMultiplexerType.TMUX -> "^B"
    else -> "^A"
  }

  fun vibrate() {
    try {
      val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        v?.vibrate(VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE))
      } else {
        @Suppress("DEPRECATION")
        v?.vibrate(18)
      }
    } catch (_: Exception) {}
  }

  fun sendPrefixCommand(commandSuffix: String, toastMsg: String? = null) {
    vibrate()
    onSendRawInput(prefix + commandSuffix)
    if (toastMsg != null) {
      onFeedback(toastMsg)
    }
  }

  // Initial fetch of real tmux state whenever the bar (re)appears or a refresh is
  // requested. NOTE: intentionally no auto "tmux set -g mouse on" on mount — that
  // silently mutated the user's server config without consent; mouse mode is now
  // only ever changed by explicit toggle taps.
  LaunchedEffect(refreshTick) { refreshTmux() }

  Surface(
    color = Slate950,
    tonalElevation = 8.dp,
    modifier = modifier
      .fillMaxWidth()
      .border(
        width = 1.dp,
        color = CyanAccent.copy(alpha = 0.35f)
      )
      .testTag("bar_tmux_controls")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp, horizontal = 6.dp)
    ) {
      // Tier 1: Visual Tmux Window Tabs Strip (Aligned with external TerminalTabBar)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .height(34.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Mode & Prefix Selector Pill
        Box {
          Box(
            modifier = Modifier
              .height(28.dp)
              .clip(RoundedCornerShape(6.dp))
              .background(Slate800)
              .border(1.dp, CyanAccent.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
              .clickable {
                vibrate()
                showModeMenu = true
              }
              .padding(horizontal = 7.dp)
              .testTag("btn_tmux_mode_dropdown"),
            contentAlignment = Alignment.Center
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(6.dp)
                  .clip(CircleShape)
                  .background(if (multiplexerType == TerminalMultiplexerType.TMUX) CyanAccent else AmberWarning)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = "${if (multiplexerType == TerminalMultiplexerType.TMUX) "Tmux" else "Screen"} ($prefixLabel)",
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
              )
              Spacer(modifier = Modifier.width(2.dp))
              Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(13.dp))
            }
          }

          DropdownMenu(
            expanded = showModeMenu,
            onDismissRequest = { showModeMenu = false },
            modifier = Modifier.background(Slate900)
          ) {
            DropdownMenuItem(
              text = { Text("Tmux 模式 (默认前缀 Ctrl+B)", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
              onClick = {
                vibrate()
                multiplexerType = TerminalMultiplexerType.TMUX
                customPrefix = null
                showModeMenu = false
              }
            )
            DropdownMenuItem(
              text = { Text("Screen 模式 (前缀 Ctrl+A)", color = MaterialTheme.colorScheme.onSurface) },
              onClick = {
                vibrate()
                multiplexerType = TerminalMultiplexerType.SCREEN
                customPrefix = null
                showModeMenu = false
              }
            )
            DropdownMenuItem(
              text = { Text("单独发送前缀 ($prefixLabel)", color = CyanAccent) },
              onClick = {
                vibrate()
                onSendRawInput(prefix)
                showModeMenu = false
              }
            )
          }
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Scrollable Window Tabs driven by REAL tmux state (click to switch, x to close
        // with confirmation, long-press to rename). The old hardcoded "窗口 1/2" list
        // desynced from the server; now every mutation re-queries list-windows.
        Row(
          modifier = Modifier
            .weight(1f)
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(5.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          if (tmuxState.windows.isEmpty()) {
            // No tmux server / not in tmux: honest hint instead of fake tabs
            Box(
              modifier = Modifier
                .height(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Slate800)
                .border(1.dp, Slate700, RoundedCornerShape(6.dp))
                .clickable {
                  vibrate()
                  refreshTick++
                }
                .padding(horizontal = 8.dp)
                .testTag("tmux_no_session"),
              contentAlignment = Alignment.Center
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                if (isQueryingTmux) {
                  CircularProgressIndicator(
                    strokeWidth = 1.5.dp,
                    color = CyanAccent,
                    modifier = Modifier.size(11.dp)
                  )
                } else {
                  Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(12.dp)
                  )
                }
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                  text = when {
                    isQueryingTmux -> "检测 tmux 中..."
                    multiplexerType == TerminalMultiplexerType.SCREEN -> "Screen 模式不支持窗口列表"
                    else -> "未检测到 tmux 会话，点击重试"
                  },
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  fontSize = 11.sp,
                  maxLines = 1
                )
              }
            }
          } else {
            // Compact session name chip so the user knows which session is listed
            tmuxState.sessionName?.let { sname ->
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(6.dp))
                  .background(Slate900)
                  .border(1.dp, Slate700, RoundedCornerShape(6.dp))
                  .padding(horizontal = 6.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = "▣ $sname",
                  color = CyanAccent,
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.widthIn(max = 72.dp)
                )
              }
            }

            tmuxState.windows.forEach { win ->
              val isActive = win.isActive
              val tabShape = RoundedCornerShape(6.dp)
              val tabBg = if (isActive) Slate800 else Slate900
              val tabBorder = if (isActive) CyanAccent.copy(alpha = 0.85f) else Slate800
              val textColor = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant

              Box(
                modifier = Modifier
                  .clip(tabShape)
                  .background(tabBg)
                  .border(if (isActive) 1.5.dp else 1.dp, tabBorder, tabShape)
                  .combinedClickable(
                    onClick = {
                      if (!isActive) {
                        vibrate()
                        // prefix + 0..9 is the tmux-native switch that also works
                        // inside full-screen apps; higher indices go via command mode
                        if (win.index in 0..9) {
                          onSendRawInput(prefix + win.index.toString())
                        } else {
                          sendPrefixCommand(":select-window -t ${win.index}\r")
                        }
                        refreshTmux(settleMs = 700)
                      }
                    },
                    onLongClick = {
                      vibrate()
                      windowToRename = win
                    }
                  )
                  .padding(start = 7.dp, end = 4.dp, top = 3.dp, bottom = 3.dp)
                  .testTag("tmux_tab_${win.index}"),
                contentAlignment = Alignment.CenterStart
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                  // Active Dot Indicator (server truth via window_active)
                  Box(
                    modifier = Modifier
                      .size(6.dp)
                      .clip(CircleShape)
                      .background(if (isActive) CyanAccent else Slate700)
                  )

                  // Window Label
                  Text(
                    text = "${win.index}: ${win.title}",
                    color = textColor,
                    fontSize = 11.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 100.dp)
                  )

                  // Close Window Tab Button (x) — asks for confirmation, then
                  // runs kill-window through tmux command mode (no y/n prompt)
                  if (tmuxState.windows.size > 1) {
                    Box(
                      modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(if (isActive) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f) else Color.Transparent)
                        .clickable {
                          vibrate()
                          windowToKill = win
                        },
                      contentAlignment = Alignment.Center
                    ) {
                      Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭窗口",
                        tint = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(10.dp)
                      )
                    }
                  }
                }
              }
            }
          }

          // New Window (+) Tab Button: creates on the server, list refreshes from tmux
          Box(
            modifier = Modifier
              .size(28.dp)
              .clip(RoundedCornerShape(6.dp))
              .background(Slate800)
              .border(1.dp, CyanAccent.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
              .clickable {
                vibrate()
                sendPrefixCommand("c", "已新建 Tmux 窗口")
                refreshTmux(settleMs = 700)
              }
              .testTag("btn_tmux_new_tab"),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.Add,
              contentDescription = "新建 Tmux 窗口",
              tint = CyanAccent,
              modifier = Modifier.size(15.dp)
            )
          }
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Close Control Bar Button
        IconButton(
          onClick = {
            vibrate()
            onCloseBar()
          },
          modifier = Modifier
            .size(26.dp)
            .testTag("btn_close_tmux_bar")
        ) {
          Icon(Icons.Default.Close, contentDescription = "关闭控制条", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
        }
      }

      Spacer(modifier = Modifier.height(4.dp))

      // Tier 2: Gesture & Pane Controls (Touch scroll toggle, Split panes, Page flip, Zoom)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        // High-affordance Touch Scrolling & Mouse Support Toggle
        Box(
          modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (isTouchScrollActive) Color(0xFF064E3B) else Slate900)
            .border(1.dp, if (isTouchScrollActive) EmeraldSuccess else Slate700, RoundedCornerShape(6.dp))
            .clickable {
              vibrate()
              userToggledMouse = true
              val target = !isTouchScrollActive
              tmuxScope.launch {
                if (onSetTmuxMouse(target)) {
                  isTouchScrollActive = target
                  onFeedback(
                    if (target) "已开启触控滚屏：可直接在屏幕上滑滚动历史、点击选屏"
                    else "已关闭鼠标触控支持"
                  )
                }
              }
            }
            .padding(horizontal = 7.dp)
            .testTag("btn_toggle_mouse_mode"),
          contentAlignment = Alignment.Center
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.TouchApp,
              contentDescription = null,
              tint = if (isTouchScrollActive) EmeraldSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
              text = if (isTouchScrollActive) "🖱️ 触控滚屏 (开启)" else "🖱️ 触控滚屏 (关闭)",
              color = if (isTouchScrollActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold
            )
          }
        }

        // 窗格/分屏类操作已按移动端使用场景移除：手机屏幕拆分窗格不可用，
        // 窗口级操作由上方真实窗口 Tab 承担。保留滚动、翻页与会话级控制。

        // Section: Direct Mobile Scrollback & Page navigation
        TmuxActionButton(
          label = "▲ 上翻页",
          tag = "btn_tmux_page_up"
        ) {
          vibrate()
          onSendSpecialKey(SpecialKeyHandler.PAGE_UP)
        }

        TmuxActionButton(
          label = "▼ 下翻页",
          tag = "btn_tmux_page_down"
        ) {
          vibrate()
          onSendSpecialKey(SpecialKeyHandler.PAGE_DOWN)
        }

        // Session detach
        TmuxActionButton(
          label = "🔌 分离后台",
          sublabel = "d",
          activeColor = AmberWarning,
          tag = "btn_tmux_detach"
        ) {
          sendPrefixCommand("d", "已分离后台会话")
        }

        // Advanced Tools Dialog
        TmuxActionButton(
          label = "⚙ 更多工具",
          activeColor = Color(0xFFC084FC),
          tag = "btn_tmux_more"
        ) {
          vibrate()
          showAdvancedDialog = true
        }
      }
    }
  }

  // Dialog: kill-window confirmation — closing a tmux window terminates whatever
  // runs inside it, so it must be explicit (was previously a single silent tap).
  windowToKill?.let { win ->
    com.example.ui.components.ConfirmDeleteDialog(
      title = "关闭 Tmux 窗口",
      targetName = "${win.index}: ${win.title}",
      detail = "将向远程 tmux 发送 kill-window，窗口内正在运行的任务会被立即终止。",
      confirmText = "关闭窗口",
      onConfirm = {
        sendPrefixCommand(":kill-window -t ${win.index}\r")
        windowToKill = null
        refreshTmux(settleMs = 700)
      },
      onDismiss = { windowToKill = null }
    )
  }

  // Dialog: Rename Window (Aligned with external TerminalTabBar rename experience)
  val winRename = windowToRename
  if (winRename != null) {
    var newTitleText by remember(winRename) { mutableStateOf(winRename.title) }

    AlertDialog(
      onDismissRequest = { windowToRename = null },
      containerColor = Slate900,
      titleContentColor = MaterialTheme.colorScheme.onSurface,
      textContentColor = MaterialTheme.colorScheme.onSurface,
      shape = RoundedCornerShape(14.dp),
      icon = {
        Icon(
          imageVector = Icons.Default.Edit,
          contentDescription = null,
          tint = CyanAccent,
          modifier = Modifier.size(24.dp)
        )
      },
      title = {
        Text("重命名 Tmux 窗口", fontSize = 16.sp, fontWeight = FontWeight.Bold)
      },
      text = {
        Column {
          Text(
            text = "为当前窗口设置更直观的标签名称：",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Spacer(modifier = Modifier.height(10.dp))
          OutlinedTextField(
            value = newTitleText,
            onValueChange = { newTitleText = it },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = CyanAccent,
              unfocusedBorderColor = Slate700,
              focusedTextColor = MaterialTheme.colorScheme.onSurface,
              unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
              cursorColor = CyanAccent
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            vibrate()
            // Send rename command to tmux: tmux rename-window '<title>'
            onSendRawInput("tmux rename-window '${newTitleText.replace("'", "")}'\r")
            onFeedback("已重命名窗口: $newTitleText")
            windowToRename = null
            refreshTmux(settleMs = 700)
          },
          colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color.Black),
          shape = RoundedCornerShape(8.dp)
        ) {
          Text("保存修改", fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { windowToRename = null }) {
          Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    )
  }

  // Advanced Tmux Tools Dialog
  if (showAdvancedDialog) {
    AlertDialog(
      onDismissRequest = { showAdvancedDialog = false },
      containerColor = Slate950,
      title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(32.dp)
              .clip(RoundedCornerShape(6.dp))
              .background(Slate800),
            contentAlignment = Alignment.Center
          ) {
            Icon(Icons.Default.GridView, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(18.dp))
          }
          Spacer(modifier = Modifier.width(8.dp))
          Text("Tmux / Screen 高级控制台", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
      },
      text = {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Text("🖱️ 鼠标与触控模式", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            OutlinedButton(
              onClick = {
                vibrate()
                tmuxScope.launch {
                  if (onSetTmuxMouse(true)) {
                    isTouchScrollActive = true
                    onFeedback("已开启 Tmux 触摸滚动与点击选屏")
                  }
                }
                showAdvancedDialog = false
              },
              modifier = Modifier.weight(1f).height(36.dp),
              shape = RoundedCornerShape(6.dp)
            ) {
              Text("开启鼠标/触控", fontSize = 11.sp, color = CyanAccent)
            }
            OutlinedButton(
              onClick = {
                vibrate()
                tmuxScope.launch {
                  if (onSetTmuxMouse(false)) {
                    isTouchScrollActive = false
                    onFeedback("已关闭鼠标支持")
                  }
                }
                showAdvancedDialog = false
              },
              modifier = Modifier.weight(1f).height(36.dp),
              shape = RoundedCornerShape(6.dp)
            ) {
              Text("关闭鼠标支持", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }

          Spacer(modifier = Modifier.height(4.dp))
          Text("🛠️ 常用辅助指令", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            OutlinedButton(
              onClick = {
                vibrate()
                sendPrefixCommand("t")
                showAdvancedDialog = false
              },
              modifier = Modifier.weight(1f).height(34.dp),
              shape = RoundedCornerShape(6.dp)
            ) {
              Text("时钟模式 (t)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
            }
            OutlinedButton(
              onClick = {
                vibrate()
                onSendRawInput("tmux ls\r")
                showAdvancedDialog = false
              },
              modifier = Modifier.weight(1f).height(34.dp),
              shape = RoundedCornerShape(6.dp)
            ) {
              Text("列出会话", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
            }
          }
        }
      },
      confirmButton = {
        Button(
          onClick = { showAdvancedDialog = false },
          colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
          shape = RoundedCornerShape(6.dp)
        ) {
          Text("完成", color = Color.Black, fontWeight = FontWeight.Bold)
        }
      }
    )
  }
}

@Composable
private fun TmuxActionButton(
  label: String,
  sublabel: String? = null,
  activeColor: Color = MaterialTheme.colorScheme.onSurface,
  tag: String,
  onClick: () -> Unit
) {
  Box(
    modifier = Modifier
      .height(28.dp)
      .clip(RoundedCornerShape(6.dp))
      .background(Slate900)
      .border(1.dp, Slate800, RoundedCornerShape(6.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = 7.dp)
      .testTag(tag),
    contentAlignment = Alignment.Center
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        text = label,
        color = activeColor,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium
      )
      if (sublabel != null) {
        Spacer(modifier = Modifier.width(3.dp))
        Text(
          text = sublabel,
          color = CyanAccent,
          fontSize = 10.sp,
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.Bold
        )
      }
    }
  }
}
