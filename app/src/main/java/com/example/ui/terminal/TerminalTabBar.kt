package com.example.ui.terminal

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.HostConfig
import com.example.ssh.SshConnectionState
import com.example.ssh.TerminalSession
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoseError
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TerminalTabBar(
  sessions: List<TerminalSession>,
  activeSessionId: String?,
  allHosts: List<HostConfig>,
  onSelectSession: (String) -> Unit,
  onCloseSession: (String) -> Unit,
  onRenameSession: (String, String) -> Unit,
  onNewTab: (HostConfig, Boolean) -> Unit,
  modifier: Modifier = Modifier
) {
  var showNewTabSheet by remember { mutableStateOf(false) }
  var sessionToRename by remember { mutableStateOf<TerminalSession?>(null) }

  Surface(
    color = Slate950,
    tonalElevation = 6.dp,
    modifier = modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(40.dp)
        .padding(horizontal = 6.dp, vertical = 3.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Scrollable Tabs Strip
      Row(
        modifier = Modifier
          .weight(1f)
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        sessions.forEach { session ->
          val isActive = session.id == activeSessionId
          val connState by session.sshManager.connectionState.collectAsState()

          val (dotColor, _) = when (val state = connState) {
            is SshConnectionState.Connected -> {
              if (state.isSimulated) Pair(Color(0xFF38BDF8), "模拟沙箱")
              else Pair(EmeraldSuccess, "已连接")
            }
            is SshConnectionState.Connecting -> Pair(Color(0xFFF59E0B), "连接中")
            is SshConnectionState.Error -> Pair(RoseError, "异常")
            is SshConnectionState.Disconnected -> Pair(Color.Gray, "断开")
          }

          val tabShape = RoundedCornerShape(8.dp)
          val tabBg = if (isActive) Color(0xFF1E293B) else Color(0xFF0F172A)
          val tabBorder = if (isActive) CyanAccent.copy(alpha = 0.85f) else Color(0xFF1E293B)
          val textColor = if (isActive) Color.White else Color(0xFF94A3B8)

          Box(
            modifier = Modifier
              .clip(tabShape)
              .background(tabBg)
              .border(if (isActive) 1.5.dp else 1.dp, tabBorder, tabShape)
              .combinedClickable(
                onClick = { onSelectSession(session.id) },
                onLongClick = { sessionToRename = session }
              )
              .padding(start = 9.dp, end = 5.dp, top = 4.dp, bottom = 4.dp)
              .testTag("tab_${session.id}"),
            contentAlignment = Alignment.CenterStart
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              // Status Indicator Dot with subtle glow
              Box(
                modifier = Modifier
                  .size(8.dp)
                  .clip(CircleShape)
                  .background(dotColor)
              )

              // Tab Title
              Text(
                text = session.title,
                color = textColor,
                fontSize = 12.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 125.dp)
              )

              // Close Tab Button (with touch padding)
              Box(
                modifier = Modifier
                  .size(18.dp)
                  .clip(CircleShape)
                  .background(if (isActive) Color(0x33FFFFFF) else Color.Transparent)
                  .clickable { onCloseSession(session.id) },
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.Close,
                  contentDescription = "关闭标签页",
                  tint = if (isActive) Color.White else Color.Gray,
                  modifier = Modifier.size(11.dp)
                )
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.width(6.dp))

      // New Tab (+) Button: High-affordance neon accent button
      Box(
        modifier = Modifier
          .size(32.dp)
          .clip(RoundedCornerShape(8.dp))
          .background(Color(0xFF1E293B))
          .border(1.dp, CyanAccent.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
          .clickable { showNewTabSheet = true }
          .testTag("btn_add_tab"),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.Add,
          contentDescription = "新建会话标签",
          tint = CyanAccent,
          modifier = Modifier.size(18.dp)
        )
      }
    }
  }

  // Dialog: Rename Tab
  val targetSession = sessionToRename
  if (targetSession != null) {
    var newTitleText by remember(targetSession) { mutableStateOf(targetSession.title) }

    AlertDialog(
      onDismissRequest = { sessionToRename = null },
      containerColor = Slate900,
      titleContentColor = Color.White,
      textContentColor = Color.White,
      shape = RoundedCornerShape(16.dp),
      icon = {
        Icon(
          imageVector = Icons.Default.Edit,
          contentDescription = null,
          tint = CyanAccent,
          modifier = Modifier.size(24.dp)
        )
      },
      title = {
        Text("重命名标签页", fontSize = 16.sp, fontWeight = FontWeight.Bold)
      },
      text = {
        Column {
          Text(
            text = "为当前终端会话命名，方便在多个窗口间快速切换：",
            fontSize = 12.sp,
            color = Color.LightGray
          )
          Spacer(modifier = Modifier.height(12.dp))
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
            onRenameSession(targetSession.id, newTitleText)
            sessionToRename = null
          },
          colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color.Black),
          shape = RoundedCornerShape(8.dp)
        ) {
          Text("保存修改", fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { sessionToRename = null }) {
          Text("取消", color = Color.Gray)
        }
      }
    )
  }

  // ModalBottomSheet: Modern, ergonomic New Tab Action Sheet
  if (showNewTabSheet) {
    val activeSession = sessions.firstOrNull { it.id == activeSessionId }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
      onDismissRequest = { showNewTabSheet = false },
      sheetState = sheetState,
      containerColor = Color(0xFF0B1120),
      dragHandle = {
        Box(
          modifier = Modifier
            .padding(top = 10.dp, bottom = 4.dp)
            .size(width = 36.dp, height = 4.dp)
            .clip(CircleShape)
            .background(Color(0xFF334155))
        )
      },
      shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp)
          .padding(bottom = 24.dp)
          .navigationBarsPadding()
      ) {
        // Sheet Header
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF06B6D4), Color(0xFF0284C7)))),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.Layers,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(20.dp)
              )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Text(
                text = "新建终端标签页",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = "开启独立 Shell 会话，多任务并行操作",
                color = Color(0xFF94A3B8),
                fontSize = 12.sp
              )
            }
          }

          IconButton(
            onClick = { showNewTabSheet = false },
            modifier = Modifier
              .size(28.dp)
              .clip(CircleShape)
              .background(Color(0xFF1E293B))
          ) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = "关闭",
              tint = Color.LightGray,
              modifier = Modifier.size(16.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 440.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          // Feature Hero Card: Current Host New Window
          if (activeSession != null) {
            item {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(12.dp))
                  .background(
                    Brush.horizontalGradient(
                      listOf(
                        Color(0xFF0E7490).copy(alpha = 0.35f),
                        Color(0xFF1E293B)
                      )
                    )
                  )
                  .border(1.5.dp, CyanAccent.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                  .clickable {
                    showNewTabSheet = false
                    onNewTab(activeSession.host, activeSession.isSimulated)
                  }
                  .padding(14.dp)
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                  ) {
                    Box(
                      modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0E7490)),
                      contentAlignment = Alignment.Center
                    ) {
                      Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                      )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                      Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                          text = "当前主机新建窗口",
                          color = Color.White,
                          fontSize = 14.sp,
                          fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                          modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(CyanAccent.copy(alpha = 0.2f))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                          Text("推荐", color = CyanAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                      }
                      Text(
                        text = "${activeSession.host.name} (${activeSession.host.username}@${activeSession.host.host})",
                        color = Color(0xFFCBD5E1),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                      )
                    }
                  }

                  Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = CyanAccent,
                    modifier = Modifier.size(20.dp)
                  )
                }
              }
            }
          }

          // Section 1: Saved Hosts
          if (allHosts.isNotEmpty()) {
            item {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(top = 8.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = "选择服务器快速连接",
                  color = Color(0xFF94A3B8),
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1E293B))
                    .padding(horizontal = 6.dp, vertical = 1.dp)
                ) {
                  Text("${allHosts.size}", color = Color.LightGray, fontSize = 10.sp)
                }
              }
            }

            items(allHosts) { host ->
              Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131D31)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                  .fillMaxWidth()
                  .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp))
                  .clickable {
                    showNewTabSheet = false
                    onNewTab(host, false)
                  }
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                  ) {
                    Box(
                      modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E293B)),
                      contentAlignment = Alignment.Center
                    ) {
                      Icon(
                        imageVector = Icons.Default.Computer,
                        contentDescription = null,
                        tint = EmeraldSuccess,
                        modifier = Modifier.size(18.dp)
                      )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                      Text(
                        text = host.name,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                      )
                      Text(
                        text = "${host.username}@${host.host}:${host.port}",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                      )
                    }
                  }

                  Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(16.dp)
                  )
                }
              }
            }
          }

          // Section 2: Local Simulation Sandbox
          item {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 2.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "离线环境",
                color = Color(0xFF94A3B8),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
              )
            }

            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF0F172A))
                .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                .clickable {
                  showNewTabSheet = false
                  val sandboxHost = HostConfig(
                    name = "本地模拟沙箱",
                    host = "127.0.0.1",
                    port = 22,
                    username = "dev"
                  )
                  onNewTab(sandboxHost, true)
                }
                .padding(14.dp)
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.weight(1f)
                ) {
                  Box(
                    modifier = Modifier
                      .size(36.dp)
                      .clip(RoundedCornerShape(8.dp))
                      .background(Color(0xFF0284C7).copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                  ) {
                    Icon(
                      imageVector = Icons.Default.Terminal,
                      contentDescription = null,
                      tint = Color(0xFF38BDF8),
                      modifier = Modifier.size(20.dp)
                    )
                  }
                  Spacer(modifier = Modifier.width(10.dp))
                  Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Text(
                        text = "本地沙箱模拟终端",
                        color = Color(0xFF38BDF8),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                      )
                      Spacer(modifier = Modifier.width(6.dp))
                      Box(
                        modifier = Modifier
                          .clip(RoundedCornerShape(4.dp))
                          .background(Color(0xFF38BDF8).copy(alpha = 0.15f))
                          .padding(horizontal = 4.dp, vertical = 1.dp)
                      ) {
                        Text("无需网络", color = Color(0xFF38BDF8), fontSize = 9.sp)
                      }
                    }
                    Text(
                      text = "内置模拟环境，支持免联网调试 agy, git, docker 等",
                      color = Color(0xFF94A3B8),
                      fontSize = 11.sp
                    )
                  }
                }

                Icon(
                  imageVector = Icons.Default.ChevronRight,
                  contentDescription = null,
                  tint = Color(0xFF38BDF8),
                  modifier = Modifier.size(18.dp)
                )
              }
            }
          }
        }
      }
    }
  }
}
