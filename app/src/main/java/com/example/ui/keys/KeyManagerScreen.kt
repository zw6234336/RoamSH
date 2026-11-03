package com.example.ui.keys

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.example.data.model.SshKey
import com.example.ui.DevSshViewModel
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoseError
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun KeyManagerScreen(
  viewModel: DevSshViewModel,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val keys by viewModel.allKeys.collectAsState()
  val isGeneratingKey by viewModel.isGeneratingKey.collectAsState()
  var showGenerateDialog by remember { mutableStateOf(false) }
  var hasStartedGeneration by remember { mutableStateOf(false) }
  var showImportDialog by remember { mutableStateOf(false) }
  var selectedKeyForPubView by remember { mutableStateOf<SshKey?>(null) }
  var keyToDelete by remember { mutableStateOf<SshKey?>(null) }

  // 密钥生成在后台 IO 线程进行；完成后自动关闭生成弹窗，让用户看到"生成中"反馈
  LaunchedEffect(isGeneratingKey) {
    if (!isGeneratingKey && hasStartedGeneration) {
      showGenerateDialog = false
      hasStartedGeneration = false
    }
  }

  fun copyToClipboard(text: String, label: String = "公钥") {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
    viewModel.showMessage("已复制$label")
  }

  Scaffold(
    floatingActionButton = {
      FloatingActionButton(
        onClick = { showGenerateDialog = true },
        containerColor = CyanAccent,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.testTag("fab_generate_key")
      ) {
        Icon(Icons.Default.Add, contentDescription = "生成新密钥")
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
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(
              text = "SSH 密钥管理",
              color = MaterialTheme.colorScheme.onSurface,
              fontSize = 20.sp,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "免密码安全登录电脑 (支持 RSA 与 Ed25519 格式)",
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              fontSize = 12.sp
            )
          }

          OutlinedButton(
            onClick = { showImportDialog = true },
            modifier = Modifier.height(34.dp)
          ) {
            Text("导入私钥", fontSize = 11.sp, color = CyanAccent)
          }
        }
      }

      // Quick Tutorial Guide Card
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
          verticalAlignment = Alignment.Top
        ) {
          Icon(
            imageVector = Icons.Default.HelpOutline,
            contentDescription = null,
            tint = CyanAccent,
            modifier = Modifier.size(20.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Column {
            Text(
              text = "电脑配置免密登录步骤",
              color = MaterialTheme.colorScheme.onSurface,
              fontSize = 13.sp,
              fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "1. 在下方生成或复制手机公钥\n2. 在电脑终端运行: echo \"公钥\" >> ~/.ssh/authorized_keys\n3. 在主机设置中切换为「密钥认证」即可安全连接！",
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              fontSize = 11.sp,
              lineHeight = 16.sp
            )
          }
        }
      }

      // Keys list
      if (keys.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
          contentAlignment = Alignment.Center
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
              imageVector = Icons.Default.VpnKey,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
              text = "暂无 SSH 密钥",
              color = MaterialTheme.colorScheme.onSurface,
              fontSize = 15.sp,
              fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "点击下方按钮即可一键在手机生成安全密钥对",
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(
              onClick = { showGenerateDialog = true },
              colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
            ) {
              Text("一键生成密钥对", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
            }
          }
        }
      } else {
        LazyColumn(
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.fillMaxSize()
        ) {
          items(keys, key = { it.id }) { key ->
            Card(
              colors = CardDefaults.cardColors(containerColor = Slate900),
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(14.dp)
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                      modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Slate800),
                      contentAlignment = Alignment.Center
                    ) {
                      Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = CyanAccent,
                        modifier = Modifier.size(18.dp)
                      )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                      Text(
                        text = key.name,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                      )
                      val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(key.createdAt))
                      Text(
                        text = "${key.keyType} · 生成于 $dateStr",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                      )
                    }
                  }

                  IconButton(onClick = { keyToDelete = key }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "删除", tint = RoseError)
                  }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Public key snippet preview
                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Slate950)
                    .clickable { selectedKeyForPubView = key }
                    .padding(8.dp)
                ) {
                  Text(
                    text = key.publicKey.take(65) + "...",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                  )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  Button(
                    onClick = { copyToClipboard(key.publicKey, "公钥") },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                      .weight(1f)
                      .height(34.dp)
                  ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("复制公钥", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                  }

                  OutlinedButton(
                    onClick = { selectedKeyForPubView = key },
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(34.dp)
                  ) {
                    Text("查看详情", color = CyanAccent, fontSize = 12.sp)
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  // Generate Key Dialog
  if (showGenerateDialog) {
    var keyName by remember { mutableStateOf("id_rsa_phone") }
    var keyType by remember { mutableStateOf("RSA") }

    AlertDialog(
      onDismissRequest = {
        if (!isGeneratingKey) {
          showGenerateDialog = false
          hasStartedGeneration = false
        }
      },
      title = { Text("生成新 SSH 密钥对") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = keyName,
            onValueChange = { keyName = it },
            label = { Text("密钥名称") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )

          Text("算法类型", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          listOf("RSA", "ED25519").forEach { type ->
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.fillMaxWidth()
            ) {
              RadioButton(
                selected = keyType == type,
                onClick = { keyType = type },
                colors = RadioButtonDefaults.colors(selectedColor = CyanAccent)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = if (type == "RSA") "RSA (2048 位 - 兼容性最佳)" else "ED25519 (高安全性与紧凑格式)",
                fontSize = 13.sp
              )
            }
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (keyName.isNotBlank() && !isGeneratingKey) {
              hasStartedGeneration = true
              viewModel.generateAndSaveKey(keyName, keyType)
            }
          },
          enabled = keyName.isNotBlank() && !isGeneratingKey,
          colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
        ) {
          if (isGeneratingKey) {
            CircularProgressIndicator(
              color = MaterialTheme.colorScheme.onPrimary,
              modifier = Modifier.size(14.dp),
              strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("生成中...", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
          } else {
            Text("立即生成", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
          }
        }
      },
      dismissButton = {
        TextButton(
          onClick = {
            if (!isGeneratingKey) {
              showGenerateDialog = false
              hasStartedGeneration = false
            }
          }
        ) {
          Text("取消")
        }
      }
    )
  }

  // 删除密钥二次确认
  keyToDelete?.let { key ->
    ConfirmDeleteDialog(
      title = "删除 SSH 密钥",
      targetName = key.name,
      detail = "使用该密钥登录的主机将无法再免密连接，删除后无法恢复。",
      onConfirm = {
        viewModel.deleteKey(key)
        keyToDelete = null
      },
      onDismiss = { keyToDelete = null }
    )
  }

  // Import Key Dialog
  if (showImportDialog) {
    var importName by remember { mutableStateOf("") }
    var privKeyContent by remember { mutableStateOf("") }
    var pubKeyContent by remember { mutableStateOf("") }

    AlertDialog(
      onDismissRequest = { showImportDialog = false },
      title = { Text("导入已有私钥") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = importName,
            onValueChange = { importName = it },
            label = { Text("密钥标识 (例如: 电脑私钥)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
          OutlinedTextField(
            value = privKeyContent,
            onValueChange = { privKeyContent = it },
            label = { Text("私钥内容 (-----BEGIN ... KEY-----)") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 6
          )
          OutlinedTextField(
            value = pubKeyContent,
            onValueChange = { pubKeyContent = it },
            label = { Text("对应公钥 (选填)") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (importName.isNotBlank() && privKeyContent.isNotBlank()) {
              val key = SshKey(
                name = importName,
                keyType = if (privKeyContent.contains("ED25519", ignoreCase = true)) "ED25519" else "RSA",
                publicKey = pubKeyContent,
                privateKey = privKeyContent.trim()
              )
              viewModel.saveKey(key)
              showImportDialog = false
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
        ) {
          Text("导入保存", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showImportDialog = false }) {
          Text("取消")
        }
      }
    )
  }

  // View Public Key Details Dialog
  selectedKeyForPubView?.let { key ->
    AlertDialog(
      onDismissRequest = { selectedKeyForPubView = null },
      title = { Text("${key.name} 公钥详情") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("公钥字符串 (用于电脑 authorized_keys):", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(6.dp))
              .background(Slate950)
              .padding(8.dp)
          ) {
            Text(
              text = key.publicKey,
              fontFamily = FontFamily.Monospace,
              fontSize = 11.sp,
              color = CyanAccent
            )
          }

          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = "一键添加到电脑命令示例:",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(6.dp))
              .background(Slate950)
              .padding(8.dp)
          ) {
            Text(
              text = "echo \"${key.publicKey}\" >> ~/.ssh/authorized_keys",
              fontFamily = FontFamily.Monospace,
              fontSize = 10.sp,
              color = EmeraldSuccess
            )
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            copyToClipboard(key.publicKey, "公钥")
            selectedKeyForPubView = null
          },
          colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
        ) {
          Text("复制公钥", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { selectedKeyForPubView = null }) {
          Text("关闭")
        }
      }
    )
  }
}
