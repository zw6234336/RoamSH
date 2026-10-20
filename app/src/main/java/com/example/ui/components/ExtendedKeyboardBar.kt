package com.example.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import com.example.ui.terminal.CompletionResult
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ssh.SpecialKeyHandler
import com.example.ui.terminal.CandidateItem
import com.example.ui.terminal.CandidateType
import com.example.ui.terminal.CommandCompletionEngine
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950

@Composable
fun ExtendedKeyboardBar(
  isCtrlActive: Boolean,
  isAltActive: Boolean,
  savedPassword: String? = null,
  focusRequester: FocusRequester? = null,
  terminalLines: List<String> = emptyList(),
  onToggleCtrl: () -> Unit,
  onToggleAlt: () -> Unit,
  onSendSpecialKey: (String) -> Unit,
  onSendRawInput: (String) -> Unit,
  onRequestCompletion: (suspend (TextFieldValue, Int) -> CompletionResult)? = null,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  var isQueryingCompletion by remember { mutableStateOf(false) }
  // Using TextFieldValue to preserve Chinese IME composition spans (pinyin/candiates)
  var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }
  var candidateSuggestions by remember { mutableStateOf<List<CandidateItem>>(emptyList()) }
  var sendWithEnter by remember { mutableStateOf(true) }
  var showFKeysMenu by remember { mutableStateOf(false) }
  var showChineseComposerDialog by remember { mutableStateOf(false) }
  var showPasswordMenu by remember { mutableStateOf(false) }
  var showAiMenu by remember { mutableStateOf(false) }

  fun vibrate() {
    try {
      val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
      } else {
        @Suppress("DEPRECATION")
        vibrator?.vibrate(15)
      }
    } catch (_: Exception) {}
  }

  var lastCompletionToken by remember { mutableStateOf("") }
  var cycleCount by remember { mutableStateOf(0) }
  var activeCandidates by remember { mutableStateOf<List<CandidateItem>>(emptyList()) }

  fun performInBoxCompletion() {
    vibrate()
    val text = textFieldValue.text
    val cursor = textFieldValue.selection.end.coerceIn(0, text.length)
    val prefix = text.substring(0, cursor)
    val lastSpace = prefix.lastIndexOf(' ')
    val currentToken = prefix.substring(lastSpace + 1)

    // Check if this is a consecutive Tab on the same token
    val isConsecutive = (currentToken.isNotEmpty() && currentToken == lastCompletionToken) ||
                        (activeCandidates.isNotEmpty() && activeCandidates.any { it.text.startsWith(currentToken, ignoreCase = true) || currentToken.startsWith(it.text, ignoreCase = true) })

    val currentCycle = if (isConsecutive) cycleCount + 1 else 0

    coroutineScope.launch {
      isQueryingCompletion = true
      try {
        val result = if (onRequestCompletion != null) {
          onRequestCompletion(textFieldValue, currentCycle)
        } else {
          CommandCompletionEngine.complete(
            currentValue = textFieldValue,
            recentTerminalLines = terminalLines,
            cycleIndex = currentCycle
          )
        }

        if (result.hasNoLocalMatch || (result.candidates.isEmpty() && result.newTextFieldValue.text == textFieldValue.text)) {
          // If no matches found, forward raw Tab to remote shell
          onSendRawInput("\t")
          android.widget.Toast.makeText(context, "未找到匹配项 (已向远端发送 Tab)", android.widget.Toast.LENGTH_SHORT).show()
        } else {
          textFieldValue = result.newTextFieldValue
          candidateSuggestions = result.candidates
          activeCandidates = result.candidates
          lastCompletionToken = result.completedToken ?: currentToken
          cycleCount = currentCycle
          result.feedbackMessage?.let { msg ->
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
          }
        }
      } catch (t: Throwable) {
        onSendRawInput("\t")
      } finally {
        isQueryingCompletion = false
      }
    }
  }

  fun sendConfirmedCommand() {
    vibrate()
    val text = textFieldValue.text
    if (text.isNotEmpty()) {
      // Send Ctrl+U (\u0015) to clear any partial line residue on the remote server shell,
      // then send the confirmed command from the input box with Enter (\r)!
      onSendRawInput("\u0015" + text + "\r")
      textFieldValue = TextFieldValue("")
      candidateSuggestions = emptyList()
      activeCandidates = emptyList()
      cycleCount = 0
    } else {
      // When input is empty, send standard Enter (\r) to terminal PTY!
      onSendSpecialKey(SpecialKeyHandler.ENTER)
    }
  }

  Surface(
    color = Slate950,
    tonalElevation = 6.dp,
    modifier = modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .padding(top = 4.dp, bottom = 6.dp)
    ) {
      // Row 1: Primary Developer Modifier Keys (ESC, TAB, Ctrl, Alt, Arrow keys, Chinese composer)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState())
          .padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        KeyButton(label = "ESC", tag = "key_esc") {
          vibrate()
          onSendSpecialKey(SpecialKeyHandler.ESC)
        }

        KeyButton(
          label = if (isQueryingCompletion) "查询中..." else if (textFieldValue.text.isNotEmpty()) "TAB ⇥" else "TAB",
          isActive = textFieldValue.text.isNotEmpty() || isQueryingCompletion,
          activeColor = CyanAccent,
          tag = "key_tab"
        ) {
          if (textFieldValue.text.isNotEmpty()) {
            performInBoxCompletion()
          } else {
            vibrate()
            onSendSpecialKey(SpecialKeyHandler.TAB)
          }
        }

        // Prominent Emergency Interrupt button (Ctrl+C)
        KeyButton(
          label = "🛑 ^C",
          isActive = true,
          activeColor = Color(0xFFEF4444),
          tag = "key_ctrl_c"
        ) {
          vibrate()
          onSendSpecialKey(SpecialKeyHandler.CTRL_C)
        }

        KeyButton(
          label = "CTRL",
          isActive = isCtrlActive,
          activeColor = CyanAccent,
          tag = "key_ctrl"
        ) {
          vibrate()
          onToggleCtrl()
        }

        KeyButton(
          label = "ALT",
          isActive = isAltActive,
          activeColor = Color(0xFFF59E0B),
          tag = "key_alt"
        ) {
          vibrate()
          onToggleAlt()
        }

        // Arrow keys directly in the front row for interactive CLI navigation (e.g. agy / menu selection)
        ArrowKeyButton(icon = Icons.Default.KeyboardArrowUp, tag = "key_arrow_up") {
          vibrate()
          onSendSpecialKey(SpecialKeyHandler.ARROW_UP)
        }
        ArrowKeyButton(icon = Icons.Default.KeyboardArrowDown, tag = "key_arrow_down") {
          vibrate()
          onSendSpecialKey(SpecialKeyHandler.ARROW_DOWN)
        }

        // Quick Enter key for one-tap confirmation
        KeyButton(
          label = "↵ Enter",
          isActive = true,
          activeColor = Color(0xFF2563EB),
          tag = "key_quick_enter"
        ) {
          vibrate()
          onSendSpecialKey(SpecialKeyHandler.ENTER)
        }

        KeyButton(label = "^D", tag = "key_ctrl_d") {
          vibrate()
          onSendSpecialKey(SpecialKeyHandler.CTRL_D)
        }

        // Dedicated Chinese & Long text input composer button
        Box(
          modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF1E3A8A))
            .clickable {
              vibrate()
              showChineseComposerDialog = true
            }
            .padding(horizontal = 8.dp)
            .testTag("btn_chinese_composer"),
          contentAlignment = Alignment.Center
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.EditNote,
              contentDescription = "中文/多行输入",
              tint = CyanAccent,
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
              text = "中文输入",
              color = CyanAccent,
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold
            )
          }
        }

        // 1Password & Credentials Fast Fill Button
        Box {
          Box(
            modifier = Modifier
              .height(34.dp)
              .clip(RoundedCornerShape(6.dp))
              .background(Color(0xFF0F264A))
              .clickable {
                vibrate()
                showPasswordMenu = true
              }
              .padding(horizontal = 8.dp)
              .testTag("btn_1password_fill"),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = "🔑 1Password",
              color = CyanAccent,
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold
            )
          }

          DropdownMenu(
            expanded = showPasswordMenu,
            onDismissRequest = { showPasswordMenu = false },
            modifier = Modifier.background(Slate900)
          ) {
            if (!savedPassword.isNullOrBlank()) {
              DropdownMenuItem(
                text = { Text("填入主机保存密码 (Enter)", color = Color.White) },
                onClick = {
                  vibrate()
                  onSendRawInput(savedPassword + "\r")
                  showPasswordMenu = false
                  android.widget.Toast.makeText(context, "已发送保存密码", android.widget.Toast.LENGTH_SHORT).show()
                }
              )
            }
            DropdownMenuItem(
              text = { Text("粘贴 1Password 剪贴板密码 (Enter)", color = Color.White) },
              onClick = {
                vibrate()
                try {
                  val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                  val clip = cb.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                  if (clip.isNotBlank()) {
                    onSendRawInput(clip + "\r")
                    android.widget.Toast.makeText(context, "已粘贴并发送 1Password 密码", android.widget.Toast.LENGTH_SHORT).show()
                  } else {
                    android.widget.Toast.makeText(context, "剪贴板为空", android.widget.Toast.LENGTH_SHORT).show()
                  }
                } catch (_: Exception) {}
                showPasswordMenu = false
              }
            )
            DropdownMenuItem(
              text = { Text("粘贴 1Password 剪贴板用户名", color = Color.White) },
              onClick = {
                vibrate()
                try {
                  val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                  val clip = cb.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                  if (clip.isNotBlank()) {
                    onSendRawInput(clip)
                    android.widget.Toast.makeText(context, "已插入用户名", android.widget.Toast.LENGTH_SHORT).show()
                  } else {
                    android.widget.Toast.makeText(context, "剪贴板为空", android.widget.Toast.LENGTH_SHORT).show()
                  }
                } catch (_: Exception) {}
                showPasswordMenu = false
              }
            )
            DropdownMenuItem(
              text = { Text("粘贴 1Password Token / 密钥", color = Color.White) },
              onClick = {
                vibrate()
                try {
                  val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                  val clip = cb.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                  if (clip.isNotBlank()) {
                    onSendRawInput(clip)
                    android.widget.Toast.makeText(context, "已插入 Token", android.widget.Toast.LENGTH_SHORT).show()
                  } else {
                    android.widget.Toast.makeText(context, "剪贴板为空", android.widget.Toast.LENGTH_SHORT).show()
                  }
                } catch (_: Exception) {}
                showPasswordMenu = false
              }
            )
          }
        }

        // AI Agent & Dangerous Skip-Confirmation Commands
        Box {
          Box(
            modifier = Modifier
              .height(34.dp)
              .clip(RoundedCornerShape(6.dp))
              .background(Color(0xFF4C1D95))
              .clickable {
                vibrate()
                showAiMenu = true
              }
              .padding(horizontal = 8.dp)
              .testTag("btn_llm_menu"),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = "⚡ AI 命令 (免确认)",
              color = Color(0xFFE9D5FF),
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold
            )
          }

          DropdownMenu(
            expanded = showAiMenu,
            onDismissRequest = { showAiMenu = false },
            modifier = Modifier.background(Slate900)
          ) {
            DropdownMenuItem(
              text = {
                Column {
                  Text("Claude Code: 危险命令跳过确认", color = Color(0xFFFDE047), fontWeight = FontWeight.Bold)
                  Text("claude code --dangerously-skip-permissions", color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
              },
              onClick = {
                vibrate()
                onSendRawInput("claude code --dangerously-skip-permissions\n")
                showAiMenu = false
              }
            )
            DropdownMenuItem(
              text = {
                Column {
                  Text("Claude: 跳过权限确认执行", color = Color(0xFFFDE047), fontWeight = FontWeight.Bold)
                  Text("claude --dangerously-skip-permissions", color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
              },
              onClick = {
                vibrate()
                onSendRawInput("claude --dangerously-skip-permissions\n")
                showAiMenu = false
              }
            )
            DropdownMenuItem(
              text = {
                Column {
                  Text("Agy: 自动执行所有危险操作 (-y)", color = Color.White, fontWeight = FontWeight.Bold)
                  Text("agy -y", color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
              },
              onClick = {
                vibrate()
                onSendRawInput("agy -y\n")
                showAiMenu = false
              }
            )
            DropdownMenuItem(
              text = {
                Column {
                  Text("Agy: 全程免确认自主编程 (--yes-always)", color = Color.White)
                  Text("agy --yes-always", color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
              },
              onClick = {
                vibrate()
                onSendRawInput("agy --yes-always\n")
                showAiMenu = false
              }
            )
            DropdownMenuItem(
              text = {
                Column {
                  Text("Grok: 自动确认执行 (-y)", color = Color.White)
                  Text("grok -y", color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
              },
              onClick = {
                vibrate()
                onSendRawInput("grok -y\n")
                showAiMenu = false
              }
            )
            DropdownMenuItem(
              text = {
                Column {
                  Text("Grok: 危险操作免确认 (--dangerously-skip-confirmation)", color = Color.White)
                  Text("grok --dangerously-skip-confirmation", color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
              },
              onClick = {
                vibrate()
                onSendRawInput("grok --dangerously-skip-confirmation\n")
                showAiMenu = false
              }
            )
            DropdownMenuItem(
              text = {
                Column {
                  Text("VS Code: 免确认启动 Web 服务", color = CyanAccent)
                  Text("code serve-web --without-connection-token --accept-server-license-terms", color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
              },
              onClick = {
                vibrate()
                onSendRawInput("code serve-web --without-connection-token --accept-server-license-terms\n")
                showAiMenu = false
              }
            )
            DropdownMenuItem(
              text = {
                Column {
                  Text("VS Code: 启动远程隧道 (免确认条款)", color = CyanAccent)
                  Text("code tunnel --accept-server-license-terms", color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
              },
              onClick = {
                vibrate()
                onSendRawInput("code tunnel --accept-server-license-terms\n")
                showAiMenu = false
              }
            )
            DropdownMenuItem(
              text = {
                Column {
                  Text("VS Code: 打开当前目录", color = CyanAccent)
                  Text("code .", color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
              },
              onClick = {
                vibrate()
                onSendRawInput("code .\n")
                showAiMenu = false
              }
            )
            DropdownMenuItem(
              text = {
                Column {
                  Text("Aider: 全自动结对编程 (跳过所有确认)", color = Color(0xFF86EFAC), fontWeight = FontWeight.Bold)
                  Text("aider --yes --yes-always", color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
              },
              onClick = {
                vibrate()
                onSendRawInput("aider --yes --yes-always\n")
                showAiMenu = false
              }
            )
            DropdownMenuItem(
              text = {
                Column {
                  Text("Open Interpreter: 自动执行代码 (-y)", color = Color.White)
                  Text("interpreter -y", color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
              },
              onClick = {
                vibrate()
                onSendRawInput("interpreter -y\n")
                showAiMenu = false
              }
            )
            DropdownMenuItem(
              text = {
                Column {
                  Text("Ollama: 运行 DeepSeek-R1 推理模型", color = Color(0xFF93C5FD))
                  Text("ollama run deepseek-r1:8b", color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
              },
              onClick = {
                vibrate()
                onSendRawInput("ollama run deepseek-r1:8b\n")
                showAiMenu = false
              }
            )
            DropdownMenuItem(
              text = {
                Column {
                  Text("Ollama: 运行 Qwen 编程大模型", color = Color(0xFF93C5FD))
                  Text("ollama run qwen2.5-coder:7b", color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
              },
              onClick = {
                vibrate()
                onSendRawInput("ollama run qwen2.5-coder:7b\n")
                showAiMenu = false
              }
            )
          }
        }

        // Quick AI confirmation buttons: Y / N / Enter / All
        KeyButton(label = "Y (确认)", tag = "key_ai_y", activeColor = EmeraldSuccess, isActive = true) {
          vibrate()
          onSendRawInput("y\r")
        }

        KeyButton(label = "N (拒绝)", tag = "key_ai_n", activeColor = Color(0xFFF43F5E), isActive = false) {
          vibrate()
          onSendRawInput("n\r")
        }

        KeyButton(label = "Enter (继续)", tag = "key_ai_enter") {
          vibrate()
          onSendRawInput("\r")
        }

        KeyButton(label = "All (全部)", tag = "key_ai_all") {
          vibrate()
          onSendRawInput("a\r")
        }

        // Additional Arrow keys & control shortcuts
        ArrowKeyButton(icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft, tag = "key_arrow_left") {
          vibrate()
          onSendSpecialKey(SpecialKeyHandler.ARROW_LEFT)
        }
        ArrowKeyButton(icon = Icons.AutoMirrored.Filled.KeyboardArrowRight, tag = "key_arrow_right") {
          vibrate()
          onSendSpecialKey(SpecialKeyHandler.ARROW_RIGHT)
        }

        KeyButton(label = "^Z", tag = "key_ctrl_z") {
          vibrate()
          onSendSpecialKey(SpecialKeyHandler.CTRL_Z)
        }
        KeyButton(label = "^L", tag = "key_ctrl_l") {
          vibrate()
          onSendSpecialKey(SpecialKeyHandler.CTRL_L)
        }
        KeyButton(label = "^R", tag = "key_ctrl_r") {
          vibrate()
          onSendSpecialKey(SpecialKeyHandler.CTRL_R)
        }

        Box {
          KeyButton(label = "Fn ▾", tag = "key_f_menu") {
            vibrate()
            showFKeysMenu = true
          }
          DropdownMenu(
            expanded = showFKeysMenu,
            onDismissRequest = { showFKeysMenu = false },
            modifier = Modifier.background(Slate900)
          ) {
            (1..12).forEach { num ->
              DropdownMenuItem(
                text = { Text("F$num", color = Color.White) },
                onClick = {
                  vibrate()
                  onSendSpecialKey(SpecialKeyHandler.fKey(num))
                  showFKeysMenu = false
                }
              )
            }
          }
        }
      }

      // Candidate Suggestion Chip Bar (Shows command, subcommand, file and path suggestions)
      if (candidateSuggestions.isNotEmpty()) {
        Spacer(modifier = Modifier.height(3.dp))
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Slate900)
            .padding(horizontal = 6.dp, vertical = 3.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "⇥ 候选:",
            color = CyanAccent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
          )
          Spacer(modifier = Modifier.width(6.dp))

          Row(
            modifier = Modifier
              .weight(1f)
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            candidateSuggestions.forEach { candidate ->
              val (iconStr, chipBg) = when (candidate.type) {
                CandidateType.DIRECTORY -> "📁" to Color(0xFF1E3A8A)
                CandidateType.FILE -> "📄" to Color(0xFF1F2937)
                CandidateType.SUBCOMMAND -> "⚡" to Color(0xFF065F46)
                CandidateType.OPTION -> "⚙" to Color(0xFF0F766E)
                CandidateType.COMMAND -> "▶" to Color(0xFF312E81)
                else -> "•" to Color(0xFF1E293B)
              }

              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(4.dp))
                  .background(chipBg)
                  .clickable {
                    vibrate()
                    val newTfv = CommandCompletionEngine.applyCandidate(textFieldValue, candidate)
                    textFieldValue = newTfv
                    candidateSuggestions = CommandCompletionEngine.getQuickSuggestions(
                      input = newTfv.text,
                      terminalLines = terminalLines
                    )
                  }
                  .padding(horizontal = 7.dp, vertical = 3.dp)
              ) {
                Text(
                  text = "$iconStr ${candidate.displayText}",
                  color = Color.White,
                  fontSize = 11.sp,
                  fontFamily = FontFamily.Monospace,
                  fontWeight = FontWeight.SemiBold
                )
              }
            }
          }

          IconButton(
            onClick = { candidateSuggestions = emptyList() },
            modifier = Modifier.size(20.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Clear,
              contentDescription = "关闭候选",
              tint = Color.Gray,
              modifier = Modifier.size(12.dp)
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(4.dp))

      // Row 2: Coding symbols & Enhanced Chinese-compatible input bar
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Horizontal scrollable symbols
        Row(
          modifier = Modifier
            .weight(0.65f)
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(4.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          val symbols = listOf(
            "|", "~", "/", "\\", "-", "_", "$", ":", ";",
            "&", "*", ">", "<", "'", "\"", "`",
            "{", "}", "[", "]", "(", ")", "!", "#", "%", "=", "+"
          )

          symbols.forEach { sym ->
            SymbolButton(symbol = sym) {
              vibrate()
              onSendRawInput(sym)
            }
          }
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Direct command & Chinese input bar with Enter action
        Box(
          modifier = Modifier
            .weight(1.6f)
            .height(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Slate800)
            .padding(horizontal = 8.dp),
          contentAlignment = Alignment.CenterStart
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
              .fillMaxWidth()
              .fillMaxHeight()
          ) {
            val inputModifier = (if (focusRequester != null) {
              Modifier
                .weight(1f)
                .fillMaxHeight()
                .focusRequester(focusRequester)
                .testTag("terminal_quick_input")
            } else {
              Modifier
                .weight(1f)
                .fillMaxHeight()
                .testTag("terminal_quick_input")
            }).onPreviewKeyEvent { event ->
              if (event.type == KeyEventType.KeyDown && event.key == Key.Tab) {
                if (textFieldValue.text.isNotEmpty()) {
                  performInBoxCompletion()
                } else {
                  vibrate()
                  onSendSpecialKey(SpecialKeyHandler.TAB)
                }
                true
              } else if (event.type == KeyEventType.KeyDown && event.key == Key.Backspace && textFieldValue.text.isEmpty()) {
                vibrate()
                onSendSpecialKey(SpecialKeyHandler.BACKSPACE)
                true
              } else {
                false
              }
            }

            BasicTextField(
              value = textFieldValue,
              onValueChange = { newValue ->
                if (isCtrlActive && newValue.text.length > textFieldValue.text.length) {
                  val typedChar = newValue.text.lastOrNull()
                  if (typedChar != null) {
                    vibrate()
                    onSendSpecialKey(SpecialKeyHandler.ctrl(typedChar))
                    onToggleCtrl()
                    return@BasicTextField
                  }
                }
                textFieldValue = newValue
                if (newValue.text.isEmpty()) {
                  candidateSuggestions = emptyList()
                } else {
                  candidateSuggestions = CommandCompletionEngine.getQuickSuggestions(
                    input = newValue.text,
                    terminalLines = terminalLines
                  )
                }
              },
              singleLine = true,
              textStyle = TextStyle(
                color = Color.White,
                fontSize = 13.sp,
                fontFamily = FontFamily.Default,
                platformStyle = PlatformTextStyle(includeFontPadding = false)
              ),
              cursorBrush = SolidColor(CyanAccent),
              keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Send
              ),
              keyboardActions = KeyboardActions(onSend = {
                sendConfirmedCommand()
              }),
              modifier = inputModifier,
              decorationBox = { innerTextField ->
                Box(
                  modifier = Modifier.fillMaxSize(),
                  contentAlignment = Alignment.CenterStart
                ) {
                  if (textFieldValue.text.isEmpty()) {
                    Text(
                      text = "输入指令 / 中文拼音…",
                      color = Color.Gray,
                      fontSize = 12.sp,
                      style = TextStyle(
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                      )
                    )
                  }
                  innerTextField()
                }
              }
            )

            if (textFieldValue.text.isNotEmpty()) {
              // In-box Tab completion button: directly completes within input box
              Box(
                modifier = Modifier
                  .height(26.dp)
                  .clip(RoundedCornerShape(4.dp))
                  .background(if (isQueryingCompletion) Color(0xFF0369A1) else Color(0xFF0E7490))
                  .clickable(enabled = !isQueryingCompletion) {
                    performInBoxCompletion()
                  }
                  .padding(horizontal = 6.dp)
                  .testTag("btn_tab_complete"),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = if (isQueryingCompletion) "⇥ 查询中..." else "⇥ 补全",
                  color = Color.White,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                )
              }

              Spacer(modifier = Modifier.width(3.dp))

              // Clear button
              IconButton(
                onClick = {
                  vibrate()
                  textFieldValue = TextFieldValue("")
                  candidateSuggestions = emptyList()
                  activeCandidates = emptyList()
                  cycleCount = 0
                },
                modifier = Modifier.size(24.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.Clear,
                  contentDescription = "清空输入",
                  tint = Color.Gray,
                  modifier = Modifier.size(14.dp)
                )
              }

              Spacer(modifier = Modifier.width(2.dp))

              // Send button: confirms and sends the completed command to the remote terminal
              Box(
                modifier = Modifier
                  .size(26.dp)
                  .clip(RoundedCornerShape(4.dp))
                  .background(CyanAccent)
                  .clickable {
                    sendConfirmedCommand()
                  },
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.Send,
                  contentDescription = "发送指令",
                  tint = Color.Black,
                  modifier = Modifier.size(14.dp)
                )
              }
            } else {
              // Quick Enter (↵) button when input is empty
              Box(
                modifier = Modifier
                  .size(26.dp)
                  .clip(RoundedCornerShape(4.dp))
                  .background(Slate800)
                  .clickable {
                    sendConfirmedCommand()
                  },
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = "↵",
                  color = CyanAccent,
                  fontSize = 16.sp,
                  fontWeight = FontWeight.Bold
                )
              }
            }
          }
        }
      }
    }
  }

  // Full Chinese & Multi-line Text Composer Dialog
  if (showChineseComposerDialog) {
    ChineseTextComposerDialog(
      onSend = { text, autoEnter ->
        val toSend = if (autoEnter) text + "\r" else text
        onSendRawInput(toSend)
        showChineseComposerDialog = false
      },
      onDismiss = { showChineseComposerDialog = false }
    )
  }
}

@Composable
private fun ChineseTextComposerDialog(
  onSend: (text: String, autoEnter: Boolean) -> Unit,
  onDismiss: () -> Unit
) {
  var text by remember { mutableStateOf("") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.EditNote, contentDescription = null, tint = CyanAccent)
        Spacer(modifier = Modifier.width(8.dp))
        Text("中文与多行文本输入框", fontSize = 16.sp, fontWeight = FontWeight.Bold)
      }
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Text(
          text = "支持任何手机中文输入法 (拼音/五笔/手写/语音)，直接发送 UTF-8 到终端，适合编写 Git Commit、文件备注或运行带中文参数的指令：",
          color = Color.LightGray,
          fontSize = 11.sp
        )

        // Quick template chips
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          listOf(
            "git commit -m \"\"" to "git commit -m \"提交中文说明\"",
            "echo \"\"" to "echo \"测试中文输出\"",
            "nano 说明.txt" to "nano README_CN.md"
          ).forEach { (label, template) ->
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Slate800)
                .clickable { text = template }
                .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
              Text(label, color = CyanAccent, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
          }
        }

        // Input field
        OutlinedTextField(
          value = text,
          onValueChange = { text = it },
          placeholder = { Text("在此输入任意中文内容或复杂命令...") },
          modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .testTag("dialog_chinese_input"),
          maxLines = 6
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (text.isNotBlank()) {
            onSend(text, true)
          }
        },
        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
        enabled = text.isNotBlank()
      ) {
        Text("发送并执行 (回车)", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
      }
    },
    dismissButton = {
      Row {
        if (text.isNotBlank()) {
          TextButton(onClick = { onSend(text, false) }) {
            Text("仅插入文本", fontSize = 12.sp, color = CyanAccent)
          }
        }
        TextButton(onClick = onDismiss) {
          Text("取消", fontSize = 12.sp)
        }
      }
    }
  )
}

@Composable
private fun KeyButton(
  label: String,
  tag: String,
  isActive: Boolean = false,
  activeColor: Color = CyanAccent,
  onClick: () -> Unit
) {
  Box(
    modifier = Modifier
      .height(34.dp)
      .clip(RoundedCornerShape(6.dp))
      .background(if (isActive) activeColor else Slate800)
      .clickable(onClick = onClick)
      .padding(horizontal = 10.dp)
      .testTag(tag),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = label,
      color = if (isActive) Color.Black else Color.White,
      fontSize = 12.sp,
      fontWeight = FontWeight.Bold,
      fontFamily = FontFamily.Monospace,
      style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
    )
  }
}

@Composable
private fun ArrowKeyButton(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  tag: String,
  onClick: () -> Unit
) {
  Box(
    modifier = Modifier
      .size(34.dp)
      .clip(RoundedCornerShape(6.dp))
      .background(Slate800)
      .clickable(onClick = onClick)
      .testTag(tag),
    contentAlignment = Alignment.Center
  ) {
    Icon(
      imageVector = icon,
      contentDescription = tag,
      tint = Color.White,
      modifier = Modifier.size(18.dp)
    )
  }
}

@Composable
private fun SymbolButton(
  symbol: String,
  onClick: () -> Unit
) {
  Box(
    modifier = Modifier
      .height(30.dp)
      .width(28.dp)
      .clip(RoundedCornerShape(4.dp))
      .background(Slate900)
      .clickable(onClick = onClick),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = symbol,
      color = CyanAccent,
      fontSize = 13.sp,
      fontWeight = FontWeight.Medium,
      fontFamily = FontFamily.Monospace,
      style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
    )
  }
}
