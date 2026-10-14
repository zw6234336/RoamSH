package com.example.ui.tunnels

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PortTunnel
import com.example.data.model.TunnelType
import com.example.ui.DevSshViewModel
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.RoseError
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TunnelsScreen(
  viewModel: DevSshViewModel,
  modifier: Modifier = Modifier
) {
  val tunnels by viewModel.allTunnels.collectAsState()
  val hosts by viewModel.allHosts.collectAsState()
  var showAddDialog by remember { mutableStateOf(false) }

  Scaffold(
    floatingActionButton = {
      FloatingActionButton(
        onClick = { showAddDialog = true },
        containerColor = CyanAccent,
        contentColor = Color.Black,
        modifier = Modifier.testTag("fab_add_tunnel")
      ) {
        Icon(Icons.Default.Add, contentDescription = "新建端口隧道")
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
      // Header
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .background(Slate900)
          .padding(horizontal = 16.dp, vertical = 12.dp)
      ) {
        Text(
          text = "SSH 端口转发隧道",
          color = Color.White,
          fontSize = 20.sp,
          fontWeight = FontWeight.Bold
        )
        Text(
          text = "在手机上直接预览电脑运行的 Web 服务 (如 Vite 3000 或 API 8080)",
          color = Color.Gray,
          fontSize = 12.sp
        )
      }

      // Help card
      Card(
        colors = CardDefaults.cardColors(containerColor = Slate900),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 10.dp)
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(Icons.Default.Info, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(20.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "本地转发 (Local Port Forward): 开启后，在手机浏览器中打开 http://localhost:端口 即可访问电脑上的网页服务！",
            color = Color.LightGray,
            fontSize = 11.sp,
            lineHeight = 15.sp
          )
        }
      }

      if (tunnels.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
          contentAlignment = Alignment.Center
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
              imageVector = Icons.Default.Hub,
              contentDescription = null,
              tint = Color.Gray,
              modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
              text = "暂无端口转发隧道",
              color = Color.White,
              fontSize = 15.sp,
              fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "可以添加如 3000、8080 端口转发，在手机直接调试电脑项目",
              color = Color.Gray,
              fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(
              onClick = { showAddDialog = true },
              colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
            ) {
              Text("添加常用端口转发", color = Color.Black, fontWeight = FontWeight.Bold)
            }
          }
        }
      } else {
        LazyColumn(
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.fillMaxSize()
        ) {
          items(tunnels, key = { it.id }) { tunnel ->
            val targetHost = hosts.find { it.id == tunnel.hostId }
            Card(
              colors = CardDefaults.cardColors(containerColor = Slate900),
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Box(
                    modifier = Modifier
                      .size(34.dp)
                      .clip(RoundedCornerShape(6.dp))
                      .background(Slate800),
                    contentAlignment = Alignment.Center
                  ) {
                    Icon(
                      imageVector = Icons.Default.SwapHoriz,
                      contentDescription = null,
                      tint = CyanAccent,
                      modifier = Modifier.size(20.dp)
                    )
                  }
                  Spacer(modifier = Modifier.width(10.dp))
                  Column {
                    Text(
                      text = tunnel.name,
                      color = Color.White,
                      fontSize = 14.sp,
                      fontWeight = FontWeight.Bold
                    )
                    Text(
                      text = "${tunnel.localPort} ➔ ${tunnel.remoteHost}:${tunnel.remotePort}",
                      color = CyanAccent,
                      fontFamily = FontFamily.Monospace,
                      fontSize = 12.sp
                    )
                    if (targetHost != null) {
                      Text(
                        text = "关联电脑: ${targetHost.name}",
                        color = Color.Gray,
                        fontSize = 11.sp
                      )
                    }
                  }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                  Switch(
                    checked = tunnel.isEnabled,
                    onCheckedChange = { viewModel.toggleTunnelEnabled(tunnel) },
                    colors = SwitchDefaults.colors(checkedThumbColor = CyanAccent)
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  IconButton(
                    onClick = { viewModel.deleteTunnel(tunnel) },
                    modifier = Modifier.size(30.dp)
                  ) {
                    Icon(Icons.Default.Delete, contentDescription = "删除", tint = RoseError)
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  // Add Tunnel Dialog
  if (showAddDialog) {
    var name by remember { mutableStateOf("Vite 前端服务 (3000)") }
    var selectedHostId by remember { mutableStateOf(hosts.firstOrNull()?.id ?: "") }
    var localPort by remember { mutableIntStateOf(3000) }
    var remoteHost by remember { mutableStateOf("127.0.0.1") }
    var remotePort by remember { mutableIntStateOf(3000) }
    var tunnelType by remember { mutableStateOf(TunnelType.LOCAL_TO_REMOTE) }
    var hostDropdownExpanded by remember { mutableStateOf(false) }

    AlertDialog(
      onDismissRequest = { showAddDialog = false },
      title = { Text("新建端口转发规则") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("规则名称") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )

          // Host selection
          if (hosts.isNotEmpty()) {
            ExposedDropdownMenuBox(
              expanded = hostDropdownExpanded,
              onExpandedChange = { hostDropdownExpanded = !hostDropdownExpanded }
            ) {
              val currentHostName = hosts.find { it.id == selectedHostId }?.name ?: "选择关联主机"
              OutlinedTextField(
                value = currentHostName,
                onValueChange = {},
                readOnly = true,
                label = { Text("关联主机") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = hostDropdownExpanded) },
                modifier = Modifier
                  .menuAnchor()
                  .fillMaxWidth()
              )
              ExposedDropdownMenu(
                expanded = hostDropdownExpanded,
                onDismissRequest = { hostDropdownExpanded = false }
              ) {
                hosts.forEach { h ->
                  DropdownMenuItem(
                    text = { Text("${h.name} (${h.host})") },
                    onClick = {
                      selectedHostId = h.id
                      hostDropdownExpanded = false
                    }
                  )
                }
              }
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            OutlinedTextField(
              value = localPort.toString(),
              onValueChange = { localPort = it.toIntOrNull() ?: 3000 },
              label = { Text("手机本地端口") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              singleLine = true,
              modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
              value = remotePort.toString(),
              onValueChange = { remotePort = it.toIntOrNull() ?: 3000 },
              label = { Text("电脑远程端口") },
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              singleLine = true,
              modifier = Modifier.weight(1f)
            )
          }

          OutlinedTextField(
            value = remoteHost,
            onValueChange = { remoteHost = it },
            label = { Text("远程目标主机 (通常为 127.0.0.1)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (name.isNotBlank()) {
              viewModel.saveTunnel(
                PortTunnel(
                  hostId = selectedHostId,
                  name = name,
                  type = tunnelType,
                  localPort = localPort,
                  remoteHost = remoteHost,
                  remotePort = remotePort,
                  isEnabled = true
                )
              )
              showAddDialog = false
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
        ) {
          Text("保存", color = Color.Black, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showAddDialog = false }) {
          Text("取消")
        }
      }
    )
  }
}
