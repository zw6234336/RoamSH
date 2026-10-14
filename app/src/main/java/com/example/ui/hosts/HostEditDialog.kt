package com.example.ui.hosts

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AuthType
import com.example.data.model.HostConfig
import com.example.data.model.SshKey
import com.example.data.model.TerminalThemeType
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.util.CredentialParser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostEditDialog(
  host: HostConfig?,
  availableKeys: List<SshKey>,
  onSave: (HostConfig) -> Unit,
  onDismiss: () -> Unit
) {
  val context = LocalContext.current

  var name by remember { mutableStateOf(host?.name ?: "") }
  var hostAddress by remember { mutableStateOf(host?.host ?: "") }
  var port by remember { mutableIntStateOf(host?.port ?: 22) }
  var username by remember { mutableStateOf(host?.username ?: "dev") }
  var authType by remember { mutableStateOf(host?.authType ?: AuthType.PASSWORD) }
  var password by remember { mutableStateOf(host?.password ?: "") }
  var selectedKeyId by remember { mutableStateOf(host?.keyId) }
  var passphrase by remember { mutableStateOf(host?.passphrase ?: "") }
  var groupTag by remember { mutableStateOf(host?.groupTag ?: "电脑") }
  var initialCommand by remember { mutableStateOf(host?.initialCommand ?: "") }
  var fontSize by remember { mutableIntStateOf(host?.fontSize ?: 13) }
  var theme by remember { mutableStateOf(host?.theme ?: TerminalThemeType.DRACULA) }

  var showPassword by remember { mutableStateOf(false) }
  var keyDropdownExpanded by remember { mutableStateOf(false) }
  var show1PasswordPasteModal by remember { mutableStateOf(false) }
  var autofillMessage by remember { mutableStateOf<String?>(null) }

  val predefinedTags = listOf("电脑", "工作", "家庭", "MacBook", "WSL", "Linux", "云服务器")

  fun handleParseCredentials(rawText: String) {
    val parsed = CredentialParser.parse(rawText)
    var filledCount = 0
    if (!parsed.name.isNullOrBlank()) { name = parsed.name; filledCount++ }
    if (!parsed.host.isNullOrBlank()) { hostAddress = parsed.host; filledCount++ }
    if (parsed.port != null && parsed.port in 1..65535) { port = parsed.port; filledCount++ }
    if (!parsed.username.isNullOrBlank()) { username = parsed.username; filledCount++ }
    if (!parsed.password.isNullOrBlank()) {
      password = parsed.password
      authType = AuthType.PASSWORD
      filledCount++
    }

    if (filledCount > 0) {
      autofillMessage = "成功从 1Password 解析填入 $filledCount 项配置！"
      Toast.makeText(context, "已填入 1Password 凭据", Toast.LENGTH_SHORT).show()
    } else {
      Toast.makeText(context, "未能识别 1Password 格式，可点击展开手动粘贴", Toast.LENGTH_LONG).show()
    }
  }

  fun readClipboardAndFill() {
    try {
      val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
      val clipData = clipboard.primaryClip
      if (clipData != null && clipData.itemCount > 0) {
        val text = clipData.getItemAt(0).text?.toString() ?: ""
        if (text.isNotBlank()) {
          handleParseCredentials(text)
        } else {
          Toast.makeText(context, "剪贴板为空，请先在 1Password 复制凭据或条目", Toast.LENGTH_SHORT).show()
        }
      } else {
        Toast.makeText(context, "剪贴板为空", Toast.LENGTH_SHORT).show()
      }
    } catch (_: Exception) {
      Toast.makeText(context, "读取剪贴板异常", Toast.LENGTH_SHORT).show()
    }
  }

  fun pasteToHost() {
    try {
      val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
      val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString()?.trim() ?: ""
      if (text.isNotBlank()) {
        val parsed = CredentialParser.parse(text)
        if (!parsed.host.isNullOrBlank()) {
          hostAddress = parsed.host
          if (parsed.port != null) port = parsed.port
          if (!parsed.username.isNullOrBlank()) username = parsed.username
          if (!parsed.password.isNullOrBlank()) {
            password = parsed.password
            authType = AuthType.PASSWORD
          }
          Toast.makeText(context, "已从 1Password 填入地址", Toast.LENGTH_SHORT).show()
        } else {
          val cleanNoProto = text.removePrefix("ssh://").removePrefix("http://").removePrefix("https://").trim()
          val withoutUser = if (cleanNoProto.contains("@")) {
            username = cleanNoProto.substringBefore("@")
            cleanNoProto.substringAfter("@")
          } else cleanNoProto
          val hostPart = if (withoutUser.contains(":")) {
            port = withoutUser.substringAfter(":").toIntOrNull() ?: port
            withoutUser.substringBefore(":")
          } else withoutUser
          hostAddress = hostPart.trim()
          Toast.makeText(context, "已粘贴地址", Toast.LENGTH_SHORT).show()
        }
      }
    } catch (_: Exception) {}
  }

  fun pasteToUsername() {
    try {
      val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
      val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString()?.trim() ?: ""
      if (text.isNotBlank()) {
        val parsed = CredentialParser.parse(text)
        username = parsed.username ?: text
        Toast.makeText(context, "已填入用户名", Toast.LENGTH_SHORT).show()
      }
    } catch (_: Exception) {}
  }

  fun pasteToPassword() {
    try {
      val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
      val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString()?.trim() ?: ""
      if (text.isNotBlank()) {
        val parsed = CredentialParser.parse(text)
        password = parsed.password ?: text
        authType = AuthType.PASSWORD
        Toast.makeText(context, "已填入密码", Toast.LENGTH_SHORT).show()
      }
    } catch (_: Exception) {}
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = if (host == null) "添加新电脑 / 主机" else "编辑主机配置",
          fontWeight = FontWeight.Bold,
          fontSize = 18.sp
        )
      }
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        // 1Password One-Tap Autofill Banner
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0F264A))
            .padding(10.dp)
        ) {
          Column {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Default.Password,
                  contentDescription = null,
                  tint = CyanAccent,
                  modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = "1Password 凭据快速填入",
                  color = Color.White,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold
                )
              }

              Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(
                  onClick = { show1PasswordPasteModal = true },
                  modifier = Modifier.height(30.dp),
                  shape = RoundedCornerShape(6.dp)
                ) {
                  Text("手动粘贴", fontSize = 11.sp, color = Color.White)
                }

                Button(
                  onClick = { readClipboardAndFill() },
                  colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                  modifier = Modifier.height(30.dp),
                  shape = RoundedCornerShape(6.dp)
                ) {
                  Icon(Icons.Default.ContentPaste, contentDescription = null, tint = Color.Black, modifier = Modifier.size(12.dp))
                  Spacer(modifier = Modifier.width(3.dp))
                  Text("自动解析", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                }
              }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "支持 1Password 复制的 SSH 地址、用户名、密码或连接命令 (ssh user@host -p port)",
              color = Color.LightGray,
              fontSize = 10.sp
            )

            if (autofillMessage != null) {
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = autofillMessage!!,
                color = EmeraldSuccess,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
              )
            }
          }
        }

        // Name
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("名称 / 备注 (例: 我的开发电脑)") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("input_host_name")
        )

        // Host address & Port in a row
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          OutlinedTextField(
            value = hostAddress,
            onValueChange = { input ->
              var clean = input.trim()
                .removePrefix("ssh://")
                .removePrefix("http://")
                .removePrefix("https://")
                .removePrefix("ssh ")
              if (clean.contains("@")) {
                val parsedUser = clean.substringBefore("@").trim()
                if (parsedUser.isNotBlank() && (username.isBlank() || username == "dev")) {
                  username = parsedUser
                }
                clean = clean.substringAfter("@")
              }
              if (clean.contains(":")) {
                val p = clean.substringAfter(":").toIntOrNull()
                if (p != null) {
                  port = p
                  clean = clean.substringBefore(":")
                }
              }
              hostAddress = clean
            },
            label = { Text("IP / 1Password 地址") },
            placeholder = { Text("192.168.1.100 或 local") },
            singleLine = true,
            trailingIcon = {
              IconButton(onClick = { pasteToHost() }) {
                Icon(
                  imageVector = Icons.Default.ContentPaste,
                  contentDescription = "从 1Password 粘贴地址",
                  tint = CyanAccent,
                  modifier = Modifier.size(18.dp)
                )
              }
            },
            keyboardOptions = KeyboardOptions(
              keyboardType = KeyboardType.Uri,
              imeAction = ImeAction.Next
            ),
            modifier = Modifier
              .weight(1f)
              .testTag("input_host_address")
          )
          OutlinedTextField(
            value = port.toString(),
            onValueChange = { port = it.toIntOrNull() ?: 22 },
            label = { Text("端口") },
            keyboardOptions = KeyboardOptions(
              keyboardType = KeyboardType.Number,
              imeAction = ImeAction.Next
            ),
            singleLine = true,
            modifier = Modifier
              .width(80.dp)
              .testTag("input_host_port")
          )
        }

        // Username
        OutlinedTextField(
          value = username,
          onValueChange = { username = it },
          label = { Text("登录用户名 (1Password 用户名)") },
          singleLine = true,
          trailingIcon = {
            IconButton(onClick = { pasteToUsername() }) {
              Icon(
                imageVector = Icons.Default.ContentPaste,
                contentDescription = "从 1Password 粘贴用户名",
                tint = CyanAccent,
                modifier = Modifier.size(18.dp)
              )
            }
          },
          keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Next
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("input_host_username")
        )

        // Group Tag selection
        Text("分组标签", fontSize = 12.sp, color = Color.Gray)
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          predefinedTags.forEach { tag ->
            val isSelected = groupTag == tag
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(if (isSelected) CyanAccent else Slate800)
                .clickable { groupTag = tag }
                .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
              Text(
                text = tag,
                color = if (isSelected) Color.Black else Color.White,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
              )
            }
          }
        }

        // Auth type selection
        Text("认证方式", fontSize = 12.sp, color = Color.Gray)
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          listOf(
            AuthType.PASSWORD to "密码认证 (1Password)",
            AuthType.PRIVATE_KEY to "SSH 密钥"
          ).forEach { (type, label) ->
            val isSelected = authType == type
            Button(
              onClick = { authType = type },
              colors = ButtonDefaults.buttonColors(
                containerColor = if (isSelected) CyanAccent else Slate800,
                contentColor = if (isSelected) Color.Black else Color.White
              ),
              modifier = Modifier.weight(1f)
            ) {
              Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
          }
        }

        // Auth credential field
        if (authType == AuthType.PASSWORD) {
          OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("SSH 密码 (1Password 密码)") },
            singleLine = true,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
              keyboardType = KeyboardType.Password,
              imeAction = ImeAction.Done
            ),
            trailingIcon = {
              Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { pasteToPassword() }) {
                  Icon(
                    imageVector = Icons.Default.ContentPaste,
                    contentDescription = "从 1Password 粘贴密码",
                    tint = CyanAccent,
                    modifier = Modifier.size(18.dp)
                  )
                }
                IconButton(onClick = { showPassword = !showPassword }) {
                  Icon(
                    imageVector = if (showPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = "切换密码可见性",
                    modifier = Modifier.size(18.dp)
                  )
                }
              }
            },
            modifier = Modifier
              .fillMaxWidth()
              .testTag("input_host_password")
          )
        } else {
          // Key selector
          if (availableKeys.isEmpty()) {
            Text(
              text = "尚未生成或添加任何 SSH 密钥。可先在「密钥」页面一键生成，或直接使用密码认证。",
              color = Color.Yellow,
              fontSize = 11.sp
            )
          } else {
            ExposedDropdownMenuBox(
              expanded = keyDropdownExpanded,
              onExpandedChange = { keyDropdownExpanded = !keyDropdownExpanded }
            ) {
              val currentKeyName = availableKeys.find { it.id == selectedKeyId }?.name ?: "请选择密钥"
              OutlinedTextField(
                value = currentKeyName,
                onValueChange = {},
                readOnly = true,
                label = { Text("选择绑定的 SSH 密钥") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = keyDropdownExpanded) },
                modifier = Modifier
                  .menuAnchor()
                  .fillMaxWidth()
              )
              ExposedDropdownMenu(
                expanded = keyDropdownExpanded,
                onDismissRequest = { keyDropdownExpanded = false }
              ) {
                availableKeys.forEach { key ->
                  DropdownMenuItem(
                    text = { Text("${key.name} (${key.keyType})") },
                    onClick = {
                      selectedKeyId = key.id
                      keyDropdownExpanded = false
                    }
                  )
                }
              }
            }
          }

          OutlinedTextField(
            value = passphrase,
            onValueChange = { passphrase = it },
            label = { Text("私钥密码 (Passphrase, 如无留空)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
          )
        }

        // Initial Command
        OutlinedTextField(
          value = initialCommand,
          onValueChange = { initialCommand = it },
          label = { Text("连接后自动执行指令 (选填)") },
          placeholder = { Text("例如: tmux attach || tmux 或 cd ~/dev") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val cleanHost = hostAddress.trim()
            .removePrefix("ssh://")
            .removePrefix("http://")
            .removePrefix("https://")
            .removePrefix("ssh ")
          val parsedUser = if (cleanHost.contains("@")) cleanHost.substringBefore("@").trim() else ""
          val strippedHost = if (cleanHost.contains("@")) cleanHost.substringAfter("@") else cleanHost
          val finalHost = if (strippedHost.contains(":")) strippedHost.substringBefore(":") else strippedHost
          val finalPort = if (strippedHost.contains(":")) {
            strippedHost.substringAfter(":").toIntOrNull() ?: port
          } else {
            port
          }
          val finalUser = when {
            username.isNotBlank() && username != "dev" -> username.trim()
            parsedUser.isNotBlank() -> parsedUser
            else -> username.trim().ifEmpty { "dev" }
          }

          if (name.isNotBlank() && finalHost.isNotBlank()) {
            val newHost = (host ?: HostConfig(name = name.trim(), host = finalHost)).copy(
              name = name.trim(),
              host = finalHost,
              port = finalPort,
              username = finalUser,
              authType = authType,
              password = password,
              keyId = selectedKeyId,
              passphrase = passphrase,
              groupTag = groupTag,
              initialCommand = initialCommand.trim(),
              fontSize = fontSize,
              theme = theme
            )
            onSave(newHost)
          }
        },
        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
        enabled = name.isNotBlank() && hostAddress.isNotBlank(),
        modifier = Modifier.testTag("btn_save_host")
      ) {
        Text("保存", color = Color.Black, fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("取消")
      }
    }
  )

  // Manual 1Password Paste & Extract Dialog
  if (show1PasswordPasteModal) {
    var rawInput by remember { mutableStateOf("") }
    AlertDialog(
      onDismissRequest = { show1PasswordPasteModal = false },
      title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.Password, contentDescription = null, tint = CyanAccent)
          Spacer(modifier = Modifier.width(8.dp))
          Text("手动粘贴 1Password 条目内容", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = "从 1Password 复制条目文本、SSH URL 或命令，粘贴到下方即可自动解析填入：",
            color = Color.LightGray,
            fontSize = 11.sp
          )
          OutlinedTextField(
            value = rawInput,
            onValueChange = { rawInput = it },
            placeholder = { Text("例如:\nHost: 192.168.1.100\nUser: ubuntu\nPassword: xxx\nPort: 22") },
            modifier = Modifier
              .fillMaxWidth()
              .height(140.dp),
            maxLines = 8
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (rawInput.isNotBlank()) {
              handleParseCredentials(rawInput)
              show1PasswordPasteModal = false
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
          enabled = rawInput.isNotBlank()
        ) {
          Text("解析并填入", color = Color.Black, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { show1PasswordPasteModal = false }) {
          Text("取消")
        }
      }
    )
  }
}
