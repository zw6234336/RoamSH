package com.example.ui.hosts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.HealthRating
import com.example.data.model.HostConfig
import com.example.data.model.HostHealthMetrics
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoseError
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950

@Composable
fun HostHealthDashboardCard(
  hosts: List<HostConfig>,
  healthMap: Map<String, HostHealthMetrics>,
  isProbing: Boolean,
  onProbeAll: () -> Unit,
  onSelectHostHealth: (HostConfig) -> Unit,
  modifier: Modifier = Modifier
) {
  var isExpanded by remember { mutableStateOf(true) }

  val infiniteTransition = rememberInfiniteTransition(label = "spin_all")
  val angle by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 360f,
    animationSpec = infiniteRepeatable(
      animation = tween(1000, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "spin_angle"
  )

  val onlineCount = hosts.count { healthMap[it.id]?.isOnline == true }
  val validLatencies = hosts.mapNotNull { healthMap[it.id]?.latencyMs }
  val avgLatency = if (validLatencies.isNotEmpty()) validLatencies.average().toLong() else null

  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = Slate900),
    modifier = modifier
      .fillMaxWidth()
      .border(
        width = 1.dp,
        brush = Brush.horizontalGradient(
          colors = listOf(
            Color(0xFF1E3A8A).copy(alpha = 0.6f),
            CyanAccent.copy(alpha = 0.3f),
            Slate800
          )
        ),
        shape = RoundedCornerShape(14.dp)
      )
      .testTag("card_health_dashboard")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp)
    ) {
      // Header row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.clickable { isExpanded = !isExpanded }
        ) {
          Box(
            modifier = Modifier
              .size(28.dp)
              .clip(RoundedCornerShape(6.dp))
              .background(Color(0xFF0F264A)),
            contentAlignment = Alignment.Center
          ) {
            Icon(Icons.Default.Speed, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(16.dp))
          }
          Spacer(modifier = Modifier.width(8.dp))
          Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = "主机健康监控看板",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
              )
              Spacer(modifier = Modifier.width(4.dp))
              Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(16.dp)
              )
            }
          }
        }

        // Action & Stat Badges
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          // Online badge
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(6.dp))
              .background(if (onlineCount > 0) EmeraldSuccess.copy(alpha = 0.15f) else Slate800)
              .padding(horizontal = 7.dp, vertical = 3.dp)
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(6.dp)
                  .clip(CircleShape)
                  .background(if (onlineCount > 0) EmeraldSuccess else Color.Gray)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = "$onlineCount/${hosts.size} 在线",
                color = if (onlineCount > 0) EmeraldSuccess else Color.LightGray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
              )
            }
          }

          // Avg Latency badge
          if (avgLatency != null) {
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Slate800)
                .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
              Text(
                text = "${avgLatency}ms",
                color = CyanAccent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
              )
            }
          }

          // Probe All Button
          OutlinedButton(
            onClick = onProbeAll,
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.height(28.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Refresh,
              contentDescription = "全部体检",
              tint = CyanAccent,
              modifier = Modifier
                .size(12.dp)
                .let { if (isProbing) it.rotate(angle) else it }
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
              text = if (isProbing) "体检中" else "体检",
              color = Color.White,
              fontSize = 11.sp,
              fontWeight = FontWeight.Medium
            )
          }
        }
      }

      // Expandable Host Telemetry Chips Strip
      AnimatedVisibility(visible = isExpanded && hosts.isNotEmpty()) {
        Column {
          Spacer(modifier = Modifier.height(10.dp))
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            hosts.forEach { host ->
              val health = healthMap[host.id]
              val isHostOnline = health?.isOnline == true
              val (dotColor, latencyLabel) = when {
                health?.isProbing == true -> Pair(CyanAccent, "体检中...")
                health == null -> Pair(Color.Gray, "未探测")
                !isHostOnline -> Pair(RoseError, "离线")
                else -> Pair(
                  if ((health.latencyMs ?: 999) < 80) EmeraldSuccess else Color(0xFFF59E0B),
                  "${health.latencyMs}ms"
                )
              }

              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(8.dp))
                  .background(Slate950)
                  .border(1.dp, Slate800, RoundedCornerShape(8.dp))
                  .clickable { onSelectHostHealth(host) }
                  .padding(horizontal = 10.dp, vertical = 8.dp)
              ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                  ) {
                    Box(
                      modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                    )
                    Text(
                      text = host.name,
                      color = Color.White,
                      fontSize = 12.sp,
                      fontWeight = FontWeight.Bold,
                      maxLines = 1
                    )
                    Text(
                      text = latencyLabel,
                      color = dotColor,
                      fontSize = 11.sp,
                      fontWeight = FontWeight.Medium
                    )
                  }

                  // Mini metric summary row if available
                  if (health?.cpuUsagePercent != null || health?.memUsagePercent != null) {
                    Row(
                      horizontalArrangement = Arrangement.spacedBy(6.dp),
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      if (health.cpuUsagePercent != null) {
                        Text(
                          text = "CPU ${health.cpuUsagePercent}%",
                          color = Color.LightGray,
                          fontSize = 10.sp
                        )
                      }
                      if (health.memUsagePercent != null) {
                        Text(
                          text = "RAM ${health.memUsagePercent}%",
                          color = Color.LightGray,
                          fontSize = 10.sp
                        )
                      }
                    }
                  } else {
                    Text(
                      text = "点击展开体检详情",
                      color = Color.Gray,
                      fontSize = 10.sp
                    )
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
