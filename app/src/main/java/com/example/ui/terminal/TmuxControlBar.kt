package com.example.ui.terminal

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoseError
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950

enum class TerminalMultiplexerType {
  TMUX,
  SCREEN
}

data class TmuxWindowItem(
  val index: Int,
  val title: String
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TmuxControlBar(
  onSendRawInput: (String) -> Unit,
  onSendSpecialKey: (String) -> Unit,
  onCloseBar: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  var multiplexerType by remember { mutableStateOf(TerminalMultiplexerType.TMUX) }
  var customPrefix by remember { mutableStateOf<String?>(null) }
  var showModeMenu by remember { mutableStateOf(false) }
  var showAdvancedDialog by remember { mutableStateOf(false) }

  // Touch scroll & Mouse mode: enabled by default to mirror external terminal touch behavior!
  var isTouchScrollActive by remember { mutableStateOf(true) }

  // Visual Window Tabs aligned with external TabBar experience
  val tmuxWindows = remember {
    mutableStateListOf(
      TmuxWindowItem(0, "窗口 1"),
      TmuxWindowItem(1, "窗口 2")
    )
  }
  var activeWindowIndex by remember { mutableIntStateOf(0) }
  var windowToRename by remember { mutableStateOf<TmuxWindowItem?>(null) }

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
      Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
    }
  }

  // Automatically enable Tmux mouse & touch support on mount so mobile touch scrolling works out of the box!
  LaunchedEffect(Unit) {
    onSendRawInput("tmux set -g mouse on\r")
  }

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
              .background(Color(0xFF0F264A))
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
                  .background(if (multiplexerType == TerminalMultiplexerType.TMUX) CyanAccent else Color(0xFFF59E0B))
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = "${if (multiplexerType == TerminalMultiplexerType.TMUX) "Tmux" else "Screen"} ($prefixLabel)",
                color = Color.White,
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
              text = { Text("Tmux 模式 (默认前缀 Ctrl+B)", color = Color.White, fontWeight = FontWeight.Bold) },
              onClick = {
                vibrate()
                multiplexerType = TerminalMultiplexerType.TMUX
                customPrefix = null
                showModeMenu = false
              }
            )
            DropdownMenuItem(
              text = { Text("Screen 模式 (前缀 Ctrl+A)", color = Color.White) },
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

        // Scrollable Window Tabs (Click to switch, x to close, long-press to rename)
        Row(
          modifier = Modifier
            .weight(1f)
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(5.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          tmuxWindows.forEach { win ->
            val isActive = win.index == activeWindowIndex
            val tabShape = RoundedCornerShape(6.dp)
            val tabBg = if (isActive) Color(0xFF1E293B) else Color(0xFF0F172A)
            val tabBorder = if (isActive) CyanAccent.copy(alpha = 0.85f) else Color(0xFF1E293B)
            val textColor = if (isActive) Color.White else Color(0xFF94A3B8)

            Box(
              modifier = Modifier
                .clip(tabShape)
                .background(tabBg)
                .border(if (isActive) 1.5.dp else 1.dp, tabBorder, tabShape)
                .combinedClickable(
                  onClick = {
                    vibrate()
                    activeWindowIndex = win.index
                    // Standard Tmux window switch: prefix + windowIndex
                    onSendRawInput(prefix + win.index.toString())
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
                // Active Dot Indicator
                Box(
                  modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (isActive) CyanAccent else Color(0xFF475569))
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

                // Close Window Tab Button (x)
                if (tmuxWindows.size > 1) {
                  Box(
                    modifier = Modifier
                      .size(16.dp)
                      .clip(CircleShape)
                      .background(if (isActive) Color(0x33FFFFFF) else Color.Transparent)
                      .clickable {
                        vibrate()
                        // Sends kill-window
                        onSendRawInput(prefix + "&")
                        tmuxWindows.remove(win)
                        if (activeWindowIndex == win.index) {
                          activeWindowIndex = tmuxWindows.first().index
                          onSendRawInput(prefix + activeWindowIndex.toString())
                        }
                      },
                    contentAlignment = Alignment.Center
                  ) {
                    Icon(
                      imageVector = Icons.Default.Close,
                      contentDescription = "关闭窗口",
                      tint = if (isActive) Color.White else Color.Gray,
                      modifier = Modifier.size(10.dp)
                    )
                  }
                }
              }
            }
          }

          // New Window (+) Tab Button, exactly like external TerminalTabBar
          Box(
            modifier = Modifier
              .size(28.dp)
              .clip(RoundedCornerShape(6.dp))
              .background(Color(0xFF1E293B))
              .border(1.dp, CyanAccent.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
              .clickable {
                vibrate()
                val nextIdx = (tmuxWindows.maxOfOrNull { it.index } ?: -1) + 1
                val newWin = TmuxWindowItem(nextIdx, "窗口 ${nextIdx + 1}")
                tmuxWindows.add(newWin)
                activeWindowIndex = nextIdx
                sendPrefixCommand("c", "已新建 Tmux 窗口")
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
          Icon(Icons.Default.Close, contentDescription = "关闭控制条", tint = Color.Gray, modifier = Modifier.size(15.dp))
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
              isTouchScrollActive = !isTouchScrollActive
              if (isTouchScrollActive) {
                onSendRawInput("tmux set -g mouse on\r")
                Toast.makeText(context, "已开启触控滚屏：可直接在屏幕上滑滚动历史、点击选屏", Toast.LENGTH_SHORT).show()
              } else {
                onSendRawInput("tmux set -g mouse off\r")
                Toast.makeText(context, "已关闭鼠标触控支持", Toast.LENGTH_SHORT).show()
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
              tint = if (isTouchScrollActive) EmeraldSuccess else Color.Gray,
              modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
              text = if (isTouchScrollActive) "🖱️ 触控滚屏 (开启)" else "🖱️ 触控滚屏 (关闭)",
              color = if (isTouchScrollActive) Color.White else Color.Gray,
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold
            )
          }
        }

        // Section: Visual Pane Splitting (分屏)
        TmuxActionButton(
          label = "⬒ 水平分屏",
          sublabel = "\"",
          tag = "btn_tmux_split_h"
        ) {
          if (multiplexerType == TerminalMultiplexerType.TMUX) {
            sendPrefixCommand("\"", "水平分屏")
          } else {
            sendPrefixCommand("S", "水平分屏 (Screen)")
          }
        }

        TmuxActionButton(
          label = "⬔ 垂直分屏",
          sublabel = "%",
          tag = "btn_tmux_split_v"
        ) {
          if (multiplexerType == TerminalMultiplexerType.TMUX) {
            sendPrefixCommand("%", "垂直分屏")
          } else {
            sendPrefixCommand("|", "垂直分屏 (Screen)")
          }
        }

        TmuxActionButton(
          label = "🔍 缩放窗格",
          sublabel = "z",
          activeColor = EmeraldSuccess,
          tag = "btn_tmux_zoom"
        ) {
          sendPrefixCommand("z", "窗格全屏/缩放")
        }

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

        TmuxActionButton(
          label = "🔀 轮换窗格",
          sublabel = "o",
          tag = "btn_tmux_next_pane"
        ) {
          sendPrefixCommand("o")
        }

        TmuxActionButton(
          label = "❌ 关窗格",
          sublabel = "x",
          activeColor = RoseError,
          tag = "btn_tmux_kill_pane"
        ) {
          sendPrefixCommand("x")
        }

        // Session detach
        TmuxActionButton(
          label = "🔌 分离后台",
          sublabel = "d",
          activeColor = Color(0xFFF59E0B),
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

  // Dialog: Rename Window (Aligned with external TerminalTabBar rename experience)
  val winRename = windowToRename
  if (winRename != null) {
    var newTitleText by remember(winRename) { mutableStateOf(winRename.title) }

    AlertDialog(
      onDismissRequest = { windowToRename = null },
      containerColor = Slate900,
      titleContentColor = Color.White,
      textContentColor = Color.White,
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
            color = Color.LightGray
          )
          Spacer(modifier = Modifier.height(10.dp))
          OutlinedTextField(
            value = newTitleText,
            onValueChange = { newTitleText = it },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = CyanAccent,
              unfocusedBorderColor = Color(0xFF475569),
              focusedTextColor = Color.White,
              unfocusedTextColor = Color.White,
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
            val targetIdx = tmuxWindows.indexOfFirst { it.index == winRename.index }
            if (targetIdx >= 0) {
              tmuxWindows[targetIdx] = winRename.copy(title = newTitleText)
            }
            // Send rename command to tmux: tmux rename-window '<title>'
            onSendRawInput("tmux rename-window '${newTitleText.replace("'", "")}'\r")
            Toast.makeText(context, "已重命名窗口: $newTitleText", Toast.LENGTH_SHORT).show()
            windowToRename = null
          },
          colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color.Black),
          shape = RoundedCornerShape(8.dp)
        ) {
          Text("保存修改", fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { windowToRename = null }) {
          Text("取消", color = Color.Gray)
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
              .background(Color(0xFF0F264A)),
            contentAlignment = Alignment.Center
          ) {
            Icon(Icons.Default.GridView, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(18.dp))
          }
          Spacer(modifier = Modifier.width(8.dp))
          Text("Tmux / Screen 高级控制台", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
      },
      text = {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Text("🖱️ 鼠标与触控模式", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            OutlinedButton(
              onClick = {
                vibrate()
                onSendRawInput("tmux set -g mouse on\r")
                isTouchScrollActive = true
                Toast.makeText(context, "已开启 Tmux 触摸滚动与点击选屏", Toast.LENGTH_SHORT).show()
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
                onSendRawInput("tmux set -g mouse off\r")
                isTouchScrollActive = false
                Toast.makeText(context, "已关闭鼠标支持", Toast.LENGTH_SHORT).show()
                showAdvancedDialog = false
              },
              modifier = Modifier.weight(1f).height(36.dp),
              shape = RoundedCornerShape(6.dp)
            ) {
              Text("关闭鼠标支持", fontSize = 11.sp, color = Color.LightGray)
            }
          }

          Spacer(modifier = Modifier.height(4.dp))
          Text("🔄 多窗格同步输入 (广播模式)", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
          OutlinedButton(
            onClick = {
              vibrate()
              sendPrefixCommand(":setw synchronize-panes\r", "已切换窗格同步输入")
              showAdvancedDialog = false
            },
            modifier = Modifier.fillMaxWidth().height(36.dp),
            shape = RoundedCornerShape(6.dp)
          ) {
            Icon(Icons.Default.Sync, contentDescription = null, tint = EmeraldSuccess, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("切换同步输入 (synchronize-panes)", fontSize = 11.sp, color = Color.White)
          }

          Spacer(modifier = Modifier.height(4.dp))
          Text("📐 窗格尺寸微调 (Resize)", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            OutlinedButton(
              onClick = {
                vibrate()
                sendPrefixCommand(SpecialKeyHandler.alt('u'))
              },
              modifier = Modifier.weight(1f).height(32.dp),
              shape = RoundedCornerShape(6.dp)
            ) {
              Text("↑ 上扩", fontSize = 10.sp, color = Color.White)
            }
            OutlinedButton(
              onClick = {
                vibrate()
                sendPrefixCommand(SpecialKeyHandler.alt('d'))
              },
              modifier = Modifier.weight(1f).height(32.dp),
              shape = RoundedCornerShape(6.dp)
            ) {
              Text("↓ 下扩", fontSize = 10.sp, color = Color.White)
            }
            OutlinedButton(
              onClick = {
                vibrate()
                sendPrefixCommand(SpecialKeyHandler.alt('l'))
              },
              modifier = Modifier.weight(1f).height(32.dp),
              shape = RoundedCornerShape(6.dp)
            ) {
              Text("← 左扩", fontSize = 10.sp, color = Color.White)
            }
            OutlinedButton(
              onClick = {
                vibrate()
                sendPrefixCommand(SpecialKeyHandler.alt('r'))
              },
              modifier = Modifier.weight(1f).height(32.dp),
              shape = RoundedCornerShape(6.dp)
            ) {
              Text("→ 右扩", fontSize = 10.sp, color = Color.White)
            }
          }

          Spacer(modifier = Modifier.height(4.dp))
          Text("🛠️ 常用辅助指令", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            OutlinedButton(
              onClick = {
                vibrate()
                sendPrefixCommand("q", "显示窗格编号")
                showAdvancedDialog = false
              },
              modifier = Modifier.weight(1f).height(34.dp),
              shape = RoundedCornerShape(6.dp)
            ) {
              Text("显示编号 (q)", fontSize = 10.sp, color = Color.White)
            }
            OutlinedButton(
              onClick = {
                vibrate()
                sendPrefixCommand("t")
                showAdvancedDialog = false
              },
              modifier = Modifier.weight(1f).height(34.dp),
              shape = RoundedCornerShape(6.dp)
            ) {
              Text("时钟模式 (t)", fontSize = 10.sp, color = Color.White)
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
              Text("列出会话", fontSize = 10.sp, color = Color.White)
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
  activeColor: Color = Color.White,
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
