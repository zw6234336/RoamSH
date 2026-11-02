package com.example.ui.snippets

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Snippet
import com.example.ui.DevSshViewModel
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.RoseError
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950

@Composable
fun SnippetScreen(
  viewModel: DevSshViewModel,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val snippets by viewModel.allSnippets.collectAsState()
  var selectedCategory by remember { mutableStateOf("全部") }
  var showAddDialog by remember { mutableStateOf(false) }
  var snippetToDelete by remember { mutableStateOf<Snippet?>(null) }

  val categories = remember(snippets) {
    listOf("全部") + snippets.map { it.category }.distinct()
  }

  val filteredSnippets = remember(snippets, selectedCategory) {
    if (selectedCategory == "全部") snippets else snippets.filter { it.category == selectedCategory }
  }

  fun copyToClipboard(text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("snippet", text)
    clipboard.setPrimaryClip(clip)
    viewModel.showMessage("已复制指令到剪贴板")
  }

  Scaffold(
    floatingActionButton = {
      FloatingActionButton(
        onClick = { showAddDialog = true },
        containerColor = CyanAccent,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.testTag("fab_add_snippet")
      ) {
        Icon(Icons.Default.Add, contentDescription = "新建快捷指令")
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
          text = "常用编程指令片段库",
          color = MaterialTheme.colorScheme.onSurface,
          fontSize = 20.sp,
          fontWeight = FontWeight.Bold
        )
        Text(
          text = "一键向远程电脑终端发送 Git、Docker、编译运行等快捷指令",
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Category tabs
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          categories.forEach { cat ->
            val isSelected = selectedCategory == cat
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(if (isSelected) CyanAccent else Slate800)
                .clickable { selectedCategory = cat }
                .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
              Text(
                text = cat,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
              )
            }
          }
        }
      }

      // Snippet cards
      if (filteredSnippets.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
          contentAlignment = Alignment.Center
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
              imageVector = Icons.Default.Code,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
              text = if (selectedCategory == "全部") "暂无常用指令片段" else "「$selectedCategory」分类下暂无片段",
              color = MaterialTheme.colorScheme.onSurface,
              fontSize = 15.sp,
              fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "把 Git 拉取、Docker 重启等高频指令存成片段，一键发送到终端",
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              fontSize = 12.sp
            )
          }
        }
      } else {
      LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
      ) {
        items(filteredSnippets, key = { it.id }) { snippet ->
          Card(
            colors = CardDefaults.cardColors(containerColor = Slate900),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("snippet_card_${snippet.id}")
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Box(
                    modifier = Modifier
                      .clip(RoundedCornerShape(4.dp))
                      .background(Slate800)
                      .padding(horizontal = 6.dp, vertical = 2.dp)
                  ) {
                    Text(snippet.category, color = CyanAccent, fontSize = 10.sp)
                  }
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = snippet.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                  )
                }

                Row {
                  IconButton(
                    onClick = { copyToClipboard(snippet.command.trimEnd()) },
                    modifier = Modifier.size(30.dp)
                  ) {
                    Icon(
                      imageVector = Icons.Default.ContentCopy,
                      contentDescription = "复制",
                      tint = MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.size(16.dp)
                    )
                  }
                  IconButton(
                    onClick = { snippetToDelete = snippet },
                    modifier = Modifier.size(30.dp)
                  ) {
                    Icon(
                      imageVector = Icons.Default.Delete,
                      contentDescription = "删除",
                      tint = RoseError,
                      modifier = Modifier.size(16.dp)
                    )
                  }
                }
              }

              Spacer(modifier = Modifier.height(6.dp))

              // Code block preview
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(6.dp))
                  .background(Color(0xFF0F172A))
                  .padding(8.dp)
              ) {
                Text(
                  text = snippet.command.trimEnd(),
                  color = Color(0xFF86EFAC),
                  fontFamily = FontFamily.Monospace,
                  fontSize = 12.sp
                )
              }

              Spacer(modifier = Modifier.height(8.dp))

              // Run action button
              Button(
                onClick = { viewModel.sendSnippet(snippet) },
                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                  .fillMaxWidth()
                  .height(34.dp)
                  .testTag("btn_send_snippet_${snippet.id}")
              ) {
                Icon(
                  imageVector = Icons.Default.Send,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onPrimary,
                  modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("发送至终端执行", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
              }
            }
          }
        }
      }
      }
    }
  }

  // 删除片段二次确认
  snippetToDelete?.let { snippet ->
    ConfirmDeleteDialog(
      title = "删除指令片段",
      targetName = snippet.title,
      detail = "删除后需要重新手动输入该指令，操作无法恢复。",
      onConfirm = {
        viewModel.deleteSnippet(snippet)
        snippetToDelete = null
      },
      onDismiss = { snippetToDelete = null }
    )
  }

  // Add Snippet Dialog
  if (showAddDialog) {
    var title by remember { mutableStateOf("") }
    var command by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("开发") }
    var autoExecute by remember { mutableStateOf(true) }

    AlertDialog(
      onDismissRequest = { showAddDialog = false },
      title = { Text("添加快捷指令片段") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("名称 (例如: Git 强制拉取)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
          OutlinedTextField(
            value = category,
            onValueChange = { category = it },
            label = { Text("分类 (例如: Git / Docker / 编译 / 运维)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
          OutlinedTextField(
            value = command,
            onValueChange = { command = it },
            label = { Text("终端指令代码") },
            placeholder = { Text("git pull origin main") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 4
          )
          Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
              checked = autoExecute,
              onCheckedChange = { autoExecute = it },
              colors = CheckboxDefaults.colors(checkedColor = CyanAccent)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("自动按回车键立即执行", fontSize = 12.sp)
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (title.isNotBlank() && command.isNotBlank()) {
              viewModel.saveSnippet(
                Snippet(
                  title = title,
                  command = command,
                  category = category.ifBlank { "通用" },
                  autoExecute = autoExecute
                )
              )
              showAddDialog = false
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
          enabled = title.isNotBlank() && command.isNotBlank()
        ) {
          Text("保存", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
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
