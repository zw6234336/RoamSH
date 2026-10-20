package com.example.ui.hosts

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.HealthRating
import com.example.data.model.HostConfig
import com.example.data.model.HostHealthMetrics
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
fun HostHealthDetailDialog(
  host: HostConfig,
  health: HostHealthMetrics?,
  onRefresh: () -> Unit,
  onConnectTerminal: () -> Unit,
  onDismiss: () -> Unit
) {
  val context = LocalContext.current
  val metrics = health ?: HostHealthMetrics(hostId = host.id)

  val infiniteTransition = rememberInfiniteTransition(label = "spin")
  val angle by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 360f,
    animationSpec = infiniteRepeatable(
      animation = tween(1000, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "spin_angle"
  )

  fun copyDiagnosticReport() {
    val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(metrics.lastProbedTime))
    val report = buildString {
      appendLine("=== RoamSH 主机健康诊断报告 ===")
      appendLine("主机: ${host.name} (${host.username}@${host.host}:${host.port})")
      appendLine("时间: $timeStr")
      appendLine("网络状态: ${if (metrics.isOnline) "在线 (延迟 ${metrics.latencyMs ?: 0}ms)" else "离线/不可达"}")
      if (metrics.osName != null) appendLine("系统内核: ${metrics.osName}")
      if (metrics.uptimeText != null) appendLine("运行时间: ${metrics.uptimeText}")
      if (metrics.cpuUsagePercent != null) appendLine("CPU 占用: ${metrics.cpuUsagePercent}% (负载: ${metrics.load1m ?: "-"}, ${metrics.load5m ?: "-"}, ${metrics.load15m ?: "-"})")
      if (metrics.memUsagePercent != null) appendLine("内存占用: ${metrics.memUsagePercent}% (${metrics.memUsedMb ?: 0}MB / ${metrics.memTotalMb ?: 0}MB)")
      if (metrics.diskUsagePercent != null) appendLine("根分区磁盘: ${metrics.diskUsagePercent}% (${metrics.diskUsedGb ?: 0}GB / ${metrics.diskTotalGb ?: 0}GB)")
      if (metrics.topProcesses.isNotEmpty()) {
        appendLine("--- 高占用进程 ---")
        metrics.topProcesses.forEach {
          appendLine("PID ${it.pid}: ${it.name} (CPU: ${it.cpuPercent}, MEM: ${it.memPercent})")
        }
      }
    }

    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("RoamSH Health Report", report))
    Toast.makeText(context, "已复制体检报告到剪贴板", Toast.LENGTH_SHORT).show()
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    modifier = Modifier.clip(RoundedCornerShape(16.dp)),
    containerColor = Slate950,
    title = {
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
              .background(Color(0xFF0F264A)),
            contentAlignment = Alignment.Center
          ) {
            Icon(Icons.Default.Speed, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(20.dp))
          }
          Spacer(modifier = Modifier.width(10.dp))
          Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = "${host.name} 健康看板",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
              )
              if (host.groupTag.isNotBlank()) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                  modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Slate800)
                    .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                  Text(host.groupTag, color = CyanAccent, fontSize = 10.sp)
                }
              }
            }
            Text(
              text = "${host.username}@${host.host}:${host.port}",
              color = Color.Gray,
              fontSize = 11.sp,
              fontFamily = FontFamily.Monospace
            )
          }
        }

        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
          Icon(Icons.Default.Close, contentDescription = "关闭", tint = Color.Gray)
        }
      }
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        // Status & Ping Ribbon
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Slate900)
            .border(1.dp, Slate800, RoundedCornerShape(10.dp))
            .padding(12.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              val (ratingColor, ratingText) = when (metrics.rating) {
                HealthRating.EXCELLENT -> Pair(EmeraldSuccess, "极佳 (${metrics.latencyMs}ms)")
                HealthRating.GOOD -> Pair(CyanAccent, "良好 (${metrics.latencyMs}ms)")
                HealthRating.FAIR -> Pair(Color(0xFFF59E0B), "偏高 (${metrics.latencyMs}ms)")
                HealthRating.POOR -> Pair(RoseError, "较慢 (${metrics.latencyMs}ms)")
                HealthRating.OFFLINE -> Pair(RoseError, "离线 / 超时")
                HealthRating.UNCHECKED -> Pair(Color.Gray, "未探测")
              }

              Box(
                modifier = Modifier
                  .size(10.dp)
                  .clip(CircleShape)
                  .background(ratingColor)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Column {
                Text("网络延迟", fontSize = 11.sp, color = Color.Gray)
                Text(
                  text = ratingText,
                  color = ratingColor,
                  fontSize = 13.sp,
                  fontWeight = FontWeight.Bold
                )
              }
            }

            if (metrics.uptimeText != null) {
              Column(horizontalAlignment = Alignment.End) {
                Text("连续运行", fontSize = 11.sp, color = Color.Gray)
                Text(
                  text = metrics.uptimeText,
                  color = Color.White,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Medium
                )
              }
            }
          }
        }

        // OS / Kernel tag
        if (!metrics.osName.isNullOrBlank()) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(8.dp))
              .background(Slate900)
              .padding(horizontal = 10.dp, vertical = 6.dp)
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.Computer, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(14.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = metrics.osName,
                color = Color.LightGray,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
              )
            }
          }
        }

        // System Metrics Gauges
        if (metrics.hasSystemMetrics) {
          // CPU Card
          metrics.cpuUsagePercent?.let { cpu ->
            val cpuColor = when {
              cpu < 50f -> EmeraldSuccess
              cpu < 80f -> Color(0xFFF59E0B)
              else -> RoseError
            }

            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Slate900)
                .padding(12.dp)
            ) {
              Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Speed, contentDescription = null, tint = cpuColor, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("CPU 使用率", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                  }
                  Text("$cpu%", color = cpuColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }

                LinearProgressIndicator(
                  progress = { (cpu / 100f).coerceIn(0f, 1f) },
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                  color = cpuColor,
                  trackColor = Slate800,
                )

                if (metrics.load1m != null) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                  ) {
                    Text(
                      text = "负载均衡 (1m / 5m / 15m)",
                      color = Color.Gray,
                      fontSize = 11.sp
                    )
                    Text(
                      text = "${metrics.load1m} / ${metrics.load5m ?: "-"} / ${metrics.load15m ?: "-"}",
                      color = Color.LightGray,
                      fontSize = 11.sp,
                      fontFamily = FontFamily.Monospace
                    )
                  }
                }
              }
            }
          }

          // Memory Card
          metrics.memUsagePercent?.let { memPct ->
            val memColor = when {
              memPct < 60f -> EmeraldSuccess
              memPct < 85f -> Color(0xFFF59E0B)
              else -> RoseError
            }

            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Slate900)
                .padding(12.dp)
            ) {
              Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Memory, contentDescription = null, tint = memColor, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("内存 (RAM)", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                  }
                  Text("$memPct%", color = memColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }

                LinearProgressIndicator(
                  progress = { (memPct / 100f).coerceIn(0f, 1f) },
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                  color = memColor,
                  trackColor = Slate800,
                )

                if (metrics.memUsedMb != null && metrics.memTotalMb != null) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                  ) {
                    val usedStr = if (metrics.memUsedMb > 1024) String.format("%.1f GB", metrics.memUsedMb / 1024f) else "${metrics.memUsedMb} MB"
                    val totalStr = if (metrics.memTotalMb > 1024) String.format("%.1f GB", metrics.memTotalMb / 1024f) else "${metrics.memTotalMb} MB"
                    Text("占用统计", color = Color.Gray, fontSize = 11.sp)
                    Text("$usedStr / $totalStr", color = Color.LightGray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                  }
                }
              }
            }
          }

          // Disk Card
          metrics.diskUsagePercent?.let { diskPct ->
            val diskColor = when {
              diskPct < 70f -> EmeraldSuccess
              diskPct < 90f -> Color(0xFFF59E0B)
              else -> RoseError
            }

            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Slate900)
                .padding(12.dp)
            ) {
              Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Storage, contentDescription = null, tint = diskColor, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("根分区磁盘存储", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                  }
                  Text("$diskPct%", color = diskColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }

                LinearProgressIndicator(
                  progress = { (diskPct / 100f).coerceIn(0f, 1f) },
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                  color = diskColor,
                  trackColor = Slate800,
                )

                if (metrics.diskUsedGb != null && metrics.diskTotalGb != null) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                  ) {
                    Text("挂载点 /", color = Color.Gray, fontSize = 11.sp)
                    Text("${metrics.diskUsedGb} GB / ${metrics.diskTotalGb} GB", color = Color.LightGray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                  }
                }
              }
            }
          }

          // Top Processes
          if (metrics.topProcesses.isNotEmpty()) {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Slate900)
                .padding(12.dp)
            ) {
              Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("资源高占用进程 (TOP)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                metrics.topProcesses.forEach { proc ->
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Text(
                      text = "${proc.pid} ${proc.name}",
                      color = Color.LightGray,
                      fontSize = 11.sp,
                      fontFamily = FontFamily.Monospace,
                      maxLines = 1,
                      modifier = Modifier.weight(1f)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                      Text("CPU: ${proc.cpuPercent}", color = CyanAccent, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                      Text("MEM: ${proc.memPercent}", color = Color.Gray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                  }
                }
              }
            }
          }
        } else if (metrics.errorMessage != null) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(8.dp))
              .background(Color(0xFF2D1515))
              .padding(10.dp)
          ) {
            Text(
              text = metrics.errorMessage,
              color = RoseError,
              fontSize = 11.sp
            )
          }
        }
      }
    },
    confirmButton = {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        OutlinedButton(
          onClick = { copyDiagnosticReport() },
          modifier = Modifier.weight(1f).height(38.dp),
          shape = RoundedCornerShape(8.dp)
        ) {
          Icon(Icons.Default.ContentCopy, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(14.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("诊断报告", fontSize = 11.sp, color = Color.White)
        }

        OutlinedButton(
          onClick = onRefresh,
          modifier = Modifier.weight(1f).height(38.dp),
          shape = RoundedCornerShape(8.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = null,
            tint = CyanAccent,
            modifier = Modifier
              .size(14.dp)
              .let { if (metrics.isProbing) it.rotate(angle) else it }
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(if (metrics.isProbing) "体检中..." else "重新体检", fontSize = 11.sp, color = Color.White)
        }

        Button(
          onClick = {
            onDismiss()
            onConnectTerminal()
          },
          colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
          modifier = Modifier.weight(1.2f).height(38.dp),
          shape = RoundedCornerShape(8.dp)
        ) {
          Icon(Icons.Default.Terminal, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("直达终端", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold)
        }
      }
    },
    dismissButton = {}
  )
}
