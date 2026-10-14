package com.example.ui.settings

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatLineSpacing
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TerminalThemeType
import com.example.ssh.TerminalColorScheme
import com.example.ssh.TerminalThemes
import com.example.ui.DevSshViewModel
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate850
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TerminalSettingsScreen(
  viewModel: DevSshViewModel,
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val currentThemeType by viewModel.terminalThemeType.collectAsState()
  val currentTheme = viewModel.currentThemeScheme
  val currentFontSize by viewModel.terminalFontSize.collectAsState()
  val currentLineSpacing by viewModel.terminalLineSpacing.collectAsState()
  val isFontBold by viewModel.terminalFontBold.collectAsState()
  val cursorStyle by viewModel.terminalCursorStyle.collectAsState()

  BackHandler(onBack = onBack)

  fun vibrate() {
    try {
      val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
      } else {
        @Suppress("DEPRECATION")
        vibrator?.vibrate(12)
      }
    } catch (_: Exception) {}
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = "终端外观与偏好设置",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
          )
        },
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.testTag("btn_back_settings")) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "返回",
              tint = Color.White
            )
          }
        },
        actions = {
          IconButton(
            onClick = {
              vibrate()
              viewModel.setTerminalTheme(TerminalThemeType.DRACULA)
              viewModel.setTerminalFontSize(14)
              viewModel.setTerminalLineSpacing(4)
              viewModel.setTerminalFontBold(false)
              viewModel.setTerminalCursorStyle("BLOCK")
              Toast.makeText(context, "已恢复默认外观设置", Toast.LENGTH_SHORT).show()
            }
          ) {
            Icon(
              imageVector = Icons.Default.RestartAlt,
              contentDescription = "恢复默认",
              tint = Color.LightGray
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Slate950)
      )
    },
    containerColor = Slate950,
    modifier = modifier.fillMaxSize()
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // 1. Live Interactive Terminal Preview
      item {
        SectionTitle(icon = Icons.Default.Terminal, title = "实时效果预览 (Live Preview)")
        Spacer(modifier = Modifier.height(8.dp))
        TerminalPreviewCard(
          theme = currentTheme,
          fontSize = currentFontSize,
          lineSpacing = currentLineSpacing,
          isBold = isFontBold,
          cursorStyle = cursorStyle
        )
      }

      // 2. Font Size & Readability
      item {
        SectionTitle(icon = Icons.Default.FormatSize, title = "字号与清晰度 (Font Size)")
        Spacer(modifier = Modifier.height(8.dp))
        Card(
          colors = CardDefaults.cardColors(containerColor = Slate900),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "终端字号大小",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
              )
              Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF0F264A))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                  Text(
                    text = "${currentFontSize} sp",
                    color = CyanAccent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                  )
                }
              }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Slider(
              value = currentFontSize.toFloat(),
              onValueChange = {
                viewModel.setTerminalFontSize(it.toInt())
              },
              valueRange = 10f..24f,
              steps = 13,
              colors = SliderDefaults.colors(
                thumbColor = CyanAccent,
                activeTrackColor = CyanAccent,
                inactiveTrackColor = Slate700
              ),
              modifier = Modifier.testTag("slider_font_size")
            )

            // Preset chips
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              val presets = listOf(
                11 to "11sp 紧凑",
                13 to "13sp 适中",
                14 to "14sp 标准",
                16 to "16sp 舒适",
                18 to "18sp 大字",
                20 to "20sp 特大"
              )
              presets.forEach { (size, label) ->
                val isSelected = currentFontSize == size
                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isSelected) CyanAccent else Slate800)
                    .clickable {
                      vibrate()
                      viewModel.setTerminalFontSize(size)
                    }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                  Text(
                    text = label,
                    color = if (isSelected) Color.Black else Color.White,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                  )
                }
              }
            }
          }
        }
      }

      // 3. Line Spacing & Font Weight
      item {
        SectionTitle(icon = Icons.Default.FormatLineSpacing, title = "排版舒适度与字重 (Typography)")
        Spacer(modifier = Modifier.height(8.dp))
        Card(
          colors = CardDefaults.cardColors(containerColor = Slate900),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            // Bold Toggle
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column {
                Text(
                  text = "全终端等宽字体加粗",
                  color = Color.White,
                  fontSize = 14.sp,
                  fontWeight = FontWeight.Medium
                )
                Text(
                  text = "在小屏或高分辨率手机上增强文字边缘对比度",
                  color = Color.Gray,
                  fontSize = 11.sp
                )
              }
              Switch(
                checked = isFontBold,
                onCheckedChange = {
                  vibrate()
                  viewModel.setTerminalFontBold(it)
                },
                colors = SwitchDefaults.colors(
                  checkedThumbColor = Color.Black,
                  checkedTrackColor = CyanAccent
                )
              )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Line Spacing Row
            Text(
              text = "行高间距倍率",
              color = Color.White,
              fontSize = 14.sp,
              fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              listOf(
                2 to "紧凑 (小屏)",
                4 to "标准 (默认)",
                6 to "宽松 (护眼)",
                8 to "超宽"
              ).forEach { (spacing, label) ->
                val isSelected = currentLineSpacing == spacing
                Box(
                  modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) CyanAccent else Slate800)
                    .clickable {
                      vibrate()
                      viewModel.setTerminalLineSpacing(spacing)
                    }
                    .padding(vertical = 8.dp),
                  contentAlignment = Alignment.Center
                ) {
                  Text(
                    text = label,
                    color = if (isSelected) Color.Black else Color.LightGray,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                  )
                }
              }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Cursor Style
            Text(
              text = "终端光标样式",
              color = Color.White,
              fontSize = 14.sp,
              fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              listOf(
                "BLOCK" to "█ 块状光标",
                "UNDERLINE" to "_ 下划线",
                "BAR" to "| 竖线"
              ).forEach { (style, label) ->
                val isSelected = cursorStyle == style
                Box(
                  modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) CyanAccent else Slate800)
                    .clickable {
                      vibrate()
                      viewModel.setTerminalCursorStyle(style)
                    }
                    .padding(vertical = 8.dp),
                  contentAlignment = Alignment.Center
                ) {
                  Text(
                    text = label,
                    color = if (isSelected) Color.Black else Color.LightGray,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                  )
                }
              }
            }
          }
        }
      }

      // 4. Color Themes (Dracula, Solarized, Gruvbox, Nord, Monokai, etc.)
      item {
        SectionTitle(icon = Icons.Default.ColorLens, title = "选择配色主题 (Themes)")
        Text(
          text = "支持开发者经典配色（含 Gruvbox、Dracula、Solarized、Nord 等），点击立即切换：",
          color = Color.Gray,
          fontSize = 11.sp,
          modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
        )
      }

      val allThemes = TerminalThemes.allThemes()
      items(allThemes) { (type, themeScheme) ->
        val isSelected = currentThemeType == type
        ThemeOptionCard(
          themeType = type,
          scheme = themeScheme,
          isSelected = isSelected,
          onSelect = {
            vibrate()
            viewModel.setTerminalTheme(type)
          }
        )
      }

      // 5. Global sync action button
      item {
        Spacer(modifier = Modifier.height(8.dp))
        Button(
          onClick = {
            vibrate()
            viewModel.applyCurrentAppearanceToAllHosts()
            Toast.makeText(context, "已将当前外观设置同步到全部主机！", Toast.LENGTH_SHORT).show()
          },
          colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .testTag("btn_sync_all_hosts")
        ) {
          Icon(Icons.Default.Sync, contentDescription = null, tint = Color.Black)
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "将当前主题与字号应用到所有主机",
            color = Color.Black,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
          )
        }
        Spacer(modifier = Modifier.height(16.dp))
      }
    }
  }
}

@Composable
private fun SectionTitle(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String
) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Icon(imageVector = icon, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(18.dp))
    Spacer(modifier = Modifier.width(6.dp))
    Text(
      text = title,
      color = Color.White,
      fontSize = 15.sp,
      fontWeight = FontWeight.Bold
    )
  }
}

@Composable
private fun TerminalPreviewCard(
  theme: TerminalColorScheme,
  fontSize: Int,
  lineSpacing: Int,
  isBold: Boolean,
  cursorStyle: String
) {
  val cursorChar = when (cursorStyle) {
    "UNDERLINE" -> "_"
    "BAR" -> "|"
    else -> "█"
  }

  Card(
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = theme.background),
    modifier = Modifier
      .fillMaxWidth()
      .border(1.dp, Slate700, RoundedCornerShape(12.dp))
  ) {
    Column {
      // Terminal Title Bar Mockup
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(Color(0x33000000))
          .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
          Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(Color(0xFFFF5F56)))
          Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(Color(0xFFFFBD2E)))
          Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(Color(0xFF27C93F)))
        }
        Text(
          text = "${theme.name} • ${fontSize}sp",
          color = theme.foreground.copy(alpha = 0.6f),
          fontSize = 10.sp,
          fontFamily = FontFamily.Monospace
        )
      }

      // Terminal Content
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(12.dp)
      ) {
        val sampleLines = listOf(
          buildAnnotatedString {
            withStyle(SpanStyle(color = theme.green, fontWeight = if (isBold) FontWeight.Bold else FontWeight.SemiBold)) {
              append("dev@workstation")
            }
            withStyle(SpanStyle(color = theme.foreground)) {
              append(":")
            }
            withStyle(SpanStyle(color = theme.blue)) {
              append("~/dev/project")
            }
            withStyle(SpanStyle(color = theme.foreground)) {
              append("$ git status")
            }
          },
          buildAnnotatedString {
            withStyle(SpanStyle(color = theme.cyan)) {
              append("On branch main: all changes committed (clean)")
            }
          },
          buildAnnotatedString {
            withStyle(SpanStyle(color = theme.green, fontWeight = if (isBold) FontWeight.Bold else FontWeight.SemiBold)) {
              append("dev@workstation")
            }
            withStyle(SpanStyle(color = theme.foreground)) {
              append(":")
            }
            withStyle(SpanStyle(color = theme.blue)) {
              append("~/dev/project")
            }
            withStyle(SpanStyle(color = theme.foreground)) {
              append("$ python3 run.py")
            }
          },
          buildAnnotatedString {
            withStyle(SpanStyle(color = theme.yellow)) {
              append("[INFO] Server listening on http://127.0.0.1:8080")
            }
          },
          buildAnnotatedString {
            withStyle(SpanStyle(color = theme.green, fontWeight = if (isBold) FontWeight.Bold else FontWeight.SemiBold)) {
              append("dev@workstation")
            }
            withStyle(SpanStyle(color = theme.foreground)) {
              append(":")
            }
            withStyle(SpanStyle(color = theme.blue)) {
              append("~/dev/project")
            }
            withStyle(SpanStyle(color = theme.foreground)) {
              append("$ ")
            }
            withStyle(SpanStyle(color = theme.cursor, fontWeight = FontWeight.Bold)) {
              append(cursorChar)
            }
          }
        )

        sampleLines.forEach { lineText ->
          Text(
            text = lineText,
            fontSize = fontSize.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = (fontSize + lineSpacing).sp,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
          )
        }
      }
    }
  }
}

@Composable
private fun ThemeOptionCard(
  themeType: TerminalThemeType,
  scheme: TerminalColorScheme,
  isSelected: Boolean,
  onSelect: () -> Unit
) {
  val themeTag = when (themeType) {
    TerminalThemeType.GRUVBOX_DARK -> "暖色复古 (Gruvbox)"
    TerminalThemeType.GRUVBOX_LIGHT -> "纸色浅色 (Gruvbox Light)"
    TerminalThemeType.SOLARIZED_DARK -> "经典防疲劳 (Solarized)"
    TerminalThemeType.SOLARIZED_LIGHT -> "日间明亮 (Solarized Light)"
    TerminalThemeType.DRACULA -> "暗夜吸血鬼 (Dracula)"
    TerminalThemeType.ONE_DARK -> "现代 IDE (One Dark)"
    TerminalThemeType.MONOKAI -> "高对比度 (Monokai)"
    TerminalThemeType.NORD -> "北欧冰霜 (Nord)"
    TerminalThemeType.RETRO_MATRIX -> "黑客绿屏 (Matrix CRT)"
    TerminalThemeType.CYBERPUNK -> "赛博霓虹 (Cyberpunk)"
  }

  Card(
    colors = CardDefaults.cardColors(
      containerColor = if (isSelected) Slate850 else Slate900
    ),
    shape = RoundedCornerShape(12.dp),
    modifier = Modifier
      .fillMaxWidth()
      .border(
        width = if (isSelected) 2.dp else 1.dp,
        color = if (isSelected) CyanAccent else Slate800,
        shape = RoundedCornerShape(12.dp)
      )
      .clickable(onClick = onSelect)
      .testTag("theme_card_${themeType.name.lowercase()}")
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.weight(1f)
      ) {
        // Theme background preview box
        Box(
          modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(scheme.background)
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(8.dp)),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = ">_",
            color = scheme.foreground,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
          )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = scheme.name,
              color = Color.White,
              fontSize = 15.sp,
              fontWeight = FontWeight.Bold
            )
            if (isSelected) {
              Spacer(modifier = Modifier.width(6.dp))
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(4.dp))
                  .background(CyanAccent)
                  .padding(horizontal = 6.dp, vertical = 1.dp)
              ) {
                Text(
                  text = "当前使用",
                  color = Color.Black,
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold
                )
              }
            }
          }
          Text(
            text = themeTag,
            color = Color.Gray,
            fontSize = 11.sp
          )
          Spacer(modifier = Modifier.height(4.dp))
          // Palette swatches
          Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(scheme.background, scheme.foreground, scheme.green, scheme.yellow, scheme.blue, scheme.red).forEach { c ->
              Box(
                modifier = Modifier
                  .size(12.dp)
                  .clip(CircleShape)
                  .background(c)
                  .border(0.5.dp, Color.White.copy(alpha = 0.2f), CircleShape)
              )
            }
          }
        }
      }

      if (isSelected) {
        Box(
          modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(CyanAccent),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.Check,
            contentDescription = "已选择",
            tint = Color.Black,
            modifier = Modifier.size(16.dp)
          )
        }
      }
    }
  }
}
