package com.example.ui.terminal

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
      val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        v.vibrate(VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE))
      } else {
        @Suppress("DEPRECATION")
        v.vibrate(18)
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
      // Row 1: Multiplexer Mode, Window & Pane Actions
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Mode & Prefix Selector Pill
        Box {
          Box(
            modifier = Modifier
              .height(30.dp)
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
              Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(14.dp))
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

        // Section: Pane Splitting (分屏)
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
          label = "🔍 缩放",
          sublabel = "z",
          activeColor = EmeraldSuccess,
          tag = "btn_tmux_zoom"
        ) {
          sendPrefixCommand("z", "窗格全屏/缩放")
        }

        // Section: Pane Navigation (窗格切换)
        TmuxNavButton(icon = Icons.Default.KeyboardArrowUp, tag = "btn_tmux_pane_up") {
          sendPrefixCommand(SpecialKeyHandler.ARROW_UP)
        }
        TmuxNavButton(icon = Icons.Default.KeyboardArrowDown, tag = "btn_tmux_pane_down") {
          sendPrefixCommand(SpecialKeyHandler.ARROW_DOWN)
        }
        TmuxNavButton(icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft, tag = "btn_tmux_pane_left") {
          sendPrefixCommand(SpecialKeyHandler.ARROW_LEFT)
        }
        TmuxNavButton(icon = Icons.AutoMirrored.Filled.KeyboardArrowRight, tag = "btn_tmux_pane_right") {
          sendPrefixCommand(SpecialKeyHandler.ARROW_RIGHT)
        }

        TmuxActionButton(
          label = "🔀 轮换",
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
          vibrate()
          // Sends prefix + x + y (confirm close)
          onSendRawInput(prefix + "x")
        }

        // Section: Window Management (窗口)
        TmuxActionButton(
          label = "➕ 新窗口",
          sublabel = "c",
          activeColor = CyanAccent,
          tag = "btn_tmux_new_window"
        ) {
          sendPrefixCommand("c", "新建 Tmux 窗口")
        }

        TmuxActionButton(
          label = "◀ 上窗口",
          sublabel = "p",
          tag = "btn_tmux_prev_window"
        ) {
          sendPrefixCommand("p")
        }

        TmuxActionButton(
          label = "▶ 下窗口",
          sublabel = "n",
          tag = "btn_tmux_next_window"
        ) {
          sendPrefixCommand("n")
        }

        TmuxActionButton(
          label = "📑 窗口列表",
          sublabel = "w",
          tag = "btn_tmux_window_list"
        ) {
          sendPrefixCommand("w")
        }

        TmuxActionButton(
          label = "🏷️ 命名",
          sublabel = ",",
          tag = "btn_tmux_rename_window"
        ) {
          sendPrefixCommand(",")
        }

        // Section: Copy / Scrollback / Session
        TmuxActionButton(
          label = "📜 滚屏复制",
          sublabel = "[",
          activeColor = Color(0xFF93C5FD),
          tag = "btn_tmux_copy_mode"
        ) {
          sendPrefixCommand("[", "进入滚屏查看模式 (q 退出)")
        }

        TmuxActionButton(
          label = "🔌 分离后台",
          sublabel = "d",
          activeColor = Color(0xFFF59E0B),
          tag = "btn_tmux_detach"
        ) {
          sendPrefixCommand("d", "已分离后台会话")
        }

        // Quick Attach / New Tmux session
        TmuxActionButton(
          label = "⚡ 连接/启动",
          sublabel = "tmux a",
          activeColor = EmeraldSuccess,
          tag = "btn_tmux_attach_or_new"
        ) {
          vibrate()
          onSendRawInput("tmux a || tmux new\r")
        }

        // Advanced Panel Button
        TmuxActionButton(
          label = "⚙ 更多工具",
          activeColor = Color(0xFFC084FC),
          tag = "btn_tmux_more"
        ) {
          vibrate()
          showAdvancedDialog = true
        }

        // Close Bar Button
        IconButton(
          onClick = {
            vibrate()
            onCloseBar()
          },
          modifier = Modifier
            .size(28.dp)
            .testTag("btn_close_tmux_bar")
        ) {
          Icon(Icons.Default.Close, contentDescription = "关闭 Tmux 控制条", tint = Color.Gray, modifier = Modifier.size(16.dp))
        }
      }
    }
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
          Text("🖱️ 鼠标与交互支持", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            OutlinedButton(
              onClick = {
                vibrate()
                onSendRawInput("tmux set -g mouse on\r")
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
                sendPrefixCommand(SpecialKeyHandler.alt('u')) // Resize up
              },
              modifier = Modifier.weight(1f).height(32.dp),
              shape = RoundedCornerShape(6.dp)
            ) {
              Text("↑ 上扩", fontSize = 10.sp, color = Color.White)
            }
            OutlinedButton(
              onClick = {
                vibrate()
                sendPrefixCommand(SpecialKeyHandler.alt('d')) // Resize down
              },
              modifier = Modifier.weight(1f).height(32.dp),
              shape = RoundedCornerShape(6.dp)
            ) {
              Text("↓ 下扩", fontSize = 10.sp, color = Color.White)
            }
            OutlinedButton(
              onClick = {
                vibrate()
                sendPrefixCommand(SpecialKeyHandler.alt('l')) // Resize left
              },
              modifier = Modifier.weight(1f).height(32.dp),
              shape = RoundedCornerShape(6.dp)
            ) {
              Text("← 左扩", fontSize = 10.sp, color = Color.White)
            }
            OutlinedButton(
              onClick = {
                vibrate()
                sendPrefixCommand(SpecialKeyHandler.alt('r')) // Resize right
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
      .height(30.dp)
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

@Composable
private fun TmuxNavButton(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  tag: String,
  onClick: () -> Unit
) {
  Box(
    modifier = Modifier
      .size(30.dp)
      .clip(RoundedCornerShape(6.dp))
      .background(Slate900)
      .border(1.dp, Slate800, RoundedCornerShape(6.dp))
      .clickable(onClick = onClick)
      .testTag(tag),
    contentAlignment = Alignment.Center
  ) {
    Icon(icon, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(16.dp))
  }
}
