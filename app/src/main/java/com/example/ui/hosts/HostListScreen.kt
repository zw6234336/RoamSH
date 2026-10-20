package com.example.ui.hosts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import com.example.data.model.HostHealthMetrics
import com.example.ui.AppScreen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AuthType
import com.example.data.model.HostConfig
import com.example.ui.DevSshViewModel
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoseError
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950

@Composable
fun HostListScreen(
  viewModel: DevSshViewModel,
  modifier: Modifier = Modifier
) {
  val hosts by viewModel.allHosts.collectAsState()
  val availableKeys by viewModel.allKeys.collectAsState()
  val pingStatus by viewModel.pingStatus.collectAsState()
  val sessions by viewModel.sessions.collectAsState()
  val healthMap by viewModel.hostHealthMap.collectAsState()
  val isProbingAll by viewModel.isGlobalProbing.collectAsState()

  var searchQuery by remember { mutableStateOf("") }
  var selectedTag by remember { mutableStateOf("全部") }
  var hostToEdit by remember { mutableStateOf<HostConfig?>(null) }
  var hostForHealthDetail by remember { mutableStateOf<HostConfig?>(null) }
  var showAddDialog by remember { mutableStateOf(false) }

  val allTags = remember(hosts) {
    listOf("全部") + hosts.map { it.groupTag }.distinct().filter { it.isNotBlank() }
  }

  val filteredHosts = remember(hosts, searchQuery, selectedTag) {
    hosts.filter { host ->
      val matchesSearch = searchQuery.isBlank() ||
        host.name.contains(searchQuery, ignoreCase = true) ||
        host.host.contains(searchQuery, ignoreCase = true) ||
        host.username.contains(searchQuery, ignoreCase = true)
      val matchesTag = selectedTag == "全部" || host.groupTag == selectedTag
      matchesSearch && matchesTag
    }
  }

  Scaffold(
    floatingActionButton = {
      FloatingActionButton(
        onClick = { showAddDialog = true },
        containerColor = CyanAccent,
        contentColor = Color.Black,
        modifier = Modifier.testTag("fab_add_host")
      ) {
        Icon(Icons.Default.Add, contentDescription = "添加主机")
      }
    },
    containerColor = Slate950,
    modifier = modifier.fillMaxSize()
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
    ) {
      // Screen Header
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .background(Slate900)
          .padding(horizontal = 16.dp, vertical = 12.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(
              text = "RoamSH 远程工作站",
              color = Color.White,
              fontSize = 20.sp,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "连接你的个人电脑、服务器或开发环境",
              color = Color.Gray,
              fontSize = 12.sp
            )
          }
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Slate800)
                .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
              Text(
                text = "${hosts.size} 台电脑",
                color = CyanAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
              )
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
              onClick = { viewModel.navigateTo(AppScreen.SETTINGS) },
              modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Slate800)
                .testTag("btn_hosts_settings")
            ) {
              Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = "终端配色与外观设置",
                tint = CyanAccent,
                modifier = Modifier.size(18.dp)
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Host Health Dashboard Card
        HostHealthDashboardCard(
          hosts = hosts,
          healthMap = healthMap,
          isProbing = isProbingAll,
          onProbeAll = { viewModel.probeAllHostsHealth() },
          onSelectHostHealth = { hostForHealthDetail = it }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Search Bar with perfect vertical alignment
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Slate900)
            .border(1.dp, if (searchQuery.isNotEmpty()) CyanAccent else Slate800, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp),
          contentAlignment = Alignment.CenterStart
        ) {
          Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Default.Search,
              contentDescription = null,
              tint = if (searchQuery.isNotEmpty()) CyanAccent else Color.Gray,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            BasicTextField(
              value = searchQuery,
              onValueChange = { searchQuery = it },
              singleLine = true,
              textStyle = TextStyle(
                color = Color.White,
                fontSize = 13.sp,
                platformStyle = PlatformTextStyle(includeFontPadding = false)
              ),
              cursorBrush = SolidColor(CyanAccent),
              modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .testTag("host_search_input"),
              decorationBox = { innerTextField ->
                Box(
                  modifier = Modifier.fillMaxSize(),
                  contentAlignment = Alignment.CenterStart
                ) {
                  if (searchQuery.isEmpty()) {
                    Text(
                      text = "搜索电脑名称、IP 或用户名…",
                      color = Color.Gray,
                      fontSize = 13.sp,
                      style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                    )
                  }
                  innerTextField()
                }
              }
            )
            if (searchQuery.isNotEmpty()) {
              IconButton(
                onClick = { searchQuery = "" },
                modifier = Modifier.size(24.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.Close,
                  contentDescription = "清除搜索",
                  tint = Color.Gray,
                  modifier = Modifier.size(16.dp)
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Tag Filter Chips
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          allTags.forEach { tag ->
            val isSelected = selectedTag == tag
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(if (isSelected) CyanAccent else Slate800)
                .clickable { selectedTag = tag }
                .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
              Text(
                text = tag,
                color = if (isSelected) Color.Black else Color.White,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
              )
            }
          }
        }
      }

      // Hosts List
      if (filteredHosts.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
          contentAlignment = Alignment.Center
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
              imageVector = Icons.Default.Computer,
              contentDescription = null,
              tint = Color.Gray,
              modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
              text = if (searchQuery.isNotBlank()) "没有找到匹配的电脑主机" else "尚未添加电脑主机",
              color = Color.White,
              fontSize = 15.sp,
              fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "点击右下角「+」即可配置电脑的 SSH IP 地址和端口",
              color = Color.Gray,
              fontSize = 12.sp
            )
          }
        }
      } else {
        LazyColumn(
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
          modifier = Modifier
            .fillMaxSize()
            .testTag("hosts_list")
        ) {
          items(filteredHosts, key = { it.id }) { host ->
            val hostSessionsCount = sessions.count { it.host.id == host.id }
            HostCardItem(
              host = host,
              isOnline = pingStatus[host.id],
              health = healthMap[host.id],
              activeSessionsCount = hostSessionsCount,
              onConnect = { viewModel.connectToHost(host) },
              onConnectNewTab = { viewModel.createNewTab(host) },
              onConnectSandbox = { viewModel.connectToHost(host, forceSimulation = true) },
              onTestPing = { viewModel.testHostPing(host) },
              onShowHealthDetail = { hostForHealthDetail = host },
              onToggleStar = { viewModel.toggleHostStar(host) },
              onEdit = { hostToEdit = host },
              onDelete = { viewModel.deleteHost(host) }
            )
          }
        }
      }
    }
  }

  // Dialogs
  if (showAddDialog) {
    HostEditDialog(
      host = null,
      availableKeys = availableKeys,
      onSave = {
        viewModel.saveHost(it)
        showAddDialog = false
      },
      onDismiss = { showAddDialog = false }
    )
  }

  hostToEdit?.let { host ->
    HostEditDialog(
      host = host,
      availableKeys = availableKeys,
      onSave = {
        viewModel.saveHost(it)
        hostToEdit = null
      },
      onDismiss = { hostToEdit = null }
    )
  }

  hostForHealthDetail?.let { host ->
    HostHealthDetailDialog(
      host = host,
      health = healthMap[host.id],
      onRefresh = { viewModel.probeHostHealth(host) },
      onConnectTerminal = {
        viewModel.connectToHost(host)
      },
      onDismiss = { hostForHealthDetail = null }
    )
  }
}

@Composable
private fun HostCardItem(
  host: HostConfig,
  isOnline: Boolean?,
  health: HostHealthMetrics? = null,
  activeSessionsCount: Int = 0,
  onConnect: () -> Unit,
  onConnectNewTab: () -> Unit,
  onConnectSandbox: () -> Unit,
  onTestPing: () -> Unit,
  onShowHealthDetail: () -> Unit,
  onToggleStar: () -> Unit,
  onEdit: () -> Unit,
  onDelete: () -> Unit
) {
  var menuExpanded by remember { mutableStateOf(false) }

  Card(
    colors = CardDefaults.cardColors(containerColor = Slate900),
    shape = RoundedCornerShape(12.dp),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("host_card_${host.id}")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp)
    ) {
      // Header row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(36.dp)
              .clip(RoundedCornerShape(8.dp))
              .background(Slate800),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.Computer,
              contentDescription = null,
              tint = CyanAccent,
              modifier = Modifier.size(20.dp)
            )
          }
          Spacer(modifier = Modifier.width(10.dp))
          Column {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = host.name,
                  color = Color.White,
                  fontSize = 15.sp,
                  fontWeight = FontWeight.Bold
                )
                if (activeSessionsCount > 0) {
                  Spacer(modifier = Modifier.width(6.dp))
                  Box(
                    modifier = Modifier
                      .clip(RoundedCornerShape(4.dp))
                      .background(EmeraldSuccess.copy(alpha = 0.2f))
                      .border(1.dp, EmeraldSuccess.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                      .padding(horizontal = 5.dp, vertical = 2.dp)
                  ) {
                    Text("$activeSessionsCount 个会话", color = EmeraldSuccess, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                  }
                }
                if (host.groupTag.isNotBlank()) {
                  Spacer(modifier = Modifier.width(6.dp))
                  Box(
                    modifier = Modifier
                      .clip(RoundedCornerShape(4.dp))
                      .background(Slate800)
                      .padding(horizontal = 6.dp, vertical = 2.dp)
                  ) {
                    Text(host.groupTag, color = CyanAccent, fontSize = 10.sp)
                  }
                }
              }
            Text(
              text = "${host.username}@${host.host}:${host.port}",
              color = Color.LightGray,
              fontSize = 12.sp,
              fontFamily = FontFamily.Monospace
            )
          }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(onClick = onToggleStar, modifier = Modifier.size(32.dp)) {
            Icon(
              imageVector = if (host.isStarred) Icons.Default.Star else Icons.Default.StarBorder,
              contentDescription = "收藏",
              tint = if (host.isStarred) Color(0xFFF59E0B) else Color.Gray,
              modifier = Modifier.size(20.dp)
            )
          }

          Box {
            IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(32.dp)) {
              Icon(Icons.Default.MoreVert, contentDescription = "更多选项", tint = Color.Gray)
            }
            DropdownMenu(
              expanded = menuExpanded,
              onDismissRequest = { menuExpanded = false },
              modifier = Modifier.background(Slate800)
            ) {
              DropdownMenuItem(
                text = { Text("在新建标签页打开", color = Color.White) },
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, tint = CyanAccent) },
                onClick = {
                  menuExpanded = false
                  onConnectNewTab()
                }
              )
              DropdownMenuItem(
                text = { Text("编辑配置", color = Color.White) },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White) },
                onClick = {
                  menuExpanded = false
                  onEdit()
                }
              )
              DropdownMenuItem(
                text = { Text("主机健康监控看板", color = Color.White) },
                leadingIcon = { Icon(Icons.Default.Speed, contentDescription = null, tint = CyanAccent) },
                onClick = {
                  menuExpanded = false
                  onShowHealthDetail()
                }
              )
              DropdownMenuItem(
                text = { Text("测试 Ping 连通性", color = Color.White) },
                leadingIcon = { Icon(Icons.Default.NetworkCheck, contentDescription = null, tint = Color.White) },
                onClick = {
                  menuExpanded = false
                  onTestPing()
                }
              )
              DropdownMenuItem(
                text = { Text("删除主机", color = RoseError) },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = RoseError) },
                onClick = {
                  menuExpanded = false
                  onDelete()
                }
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Status indicator & Auth tag
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.clickable { onShowHealthDetail() }
        ) {
          val (pingColor, pingLabel) = when {
            health?.isProbing == true -> Pair(CyanAccent, "体检中...")
            health?.isOnline == true -> {
              val ms = health.latencyMs ?: 0L
              val rating = if (ms < 80) "极佳" else "良好"
              Pair(EmeraldSuccess, "$rating ${ms}ms")
            }
            health?.isOnline == false -> Pair(RoseError, "连接超时")
            isOnline == true -> Pair(EmeraldSuccess, "连通良好")
            isOnline == false -> Pair(RoseError, "连接超时")
            else -> Pair(Color.Gray, "未探测")
          }
          Box(
            modifier = Modifier
              .size(7.dp)
              .clip(CircleShape)
              .background(pingColor)
          )
          Spacer(modifier = Modifier.width(5.dp))
          Text(text = pingLabel, color = pingColor, fontSize = 11.sp, fontWeight = FontWeight.Medium)

          Spacer(modifier = Modifier.width(10.dp))
          Text(
            text = if (host.authType == AuthType.PASSWORD) "密码认证" else "密钥认证",
            color = Color.LightGray,
            fontSize = 11.sp
          )
        }

        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Slate800)
            .clickable { onShowHealthDetail() }
            .padding(horizontal = 7.dp, vertical = 3.dp)
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Speed, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(12.dp))
            Spacer(modifier = Modifier.width(3.dp))
            Text("健康看板", color = CyanAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
          }
        }
      }

      // Live Telemetry Mini Strip
      if (health?.hasSystemMetrics == true) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Slate950)
            .clickable { onShowHealthDetail() }
            .padding(horizontal = 8.dp, vertical = 5.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          if (health.cpuUsagePercent != null) {
            Text("💻 CPU ${health.cpuUsagePercent}%", color = Color.LightGray, fontSize = 11.sp)
          }
          if (health.memUsagePercent != null) {
            val memText = health.memUsedMb?.let { used ->
              if (used > 1024) String.format("%.1fG", used / 1024f) else "${used}M"
            } ?: ""
            Text("🧠 RAM ${health.memUsagePercent}% $memText", color = Color.LightGray, fontSize = 11.sp)
          }
          if (health.diskUsagePercent != null) {
            Text("💾 磁盘 ${health.diskUsagePercent}%", color = Color.LightGray, fontSize = 11.sp)
          }
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Action Buttons
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Button(
          onClick = onConnect,
          colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier
            .weight(1f)
            .height(38.dp)
            .testTag("btn_connect_${host.id}")
        ) {
          Icon(Icons.Default.Terminal, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = if (activeSessionsCount > 0) "进入终端 ($activeSessionsCount)" else "连接终端",
            color = Color.Black,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
          )
        }

        OutlinedButton(
          onClick = onShowHealthDetail,
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier
            .height(38.dp)
            .testTag("btn_health_${host.id}")
        ) {
          Icon(Icons.Default.Speed, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("体检看板", color = CyanAccent, fontSize = 12.sp)
        }

        OutlinedButton(
          onClick = onConnectSandbox,
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier
            .height(38.dp)
            .testTag("btn_sandbox_${host.id}")
        ) {
          Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("沙箱", color = Color.Gray, fontSize = 12.sp)
        }
      }
    }
  }
}
