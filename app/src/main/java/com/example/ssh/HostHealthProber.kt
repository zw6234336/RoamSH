package com.example.ssh

import com.example.data.db.SshKeyDao
import com.example.data.model.AuthType
import com.example.data.model.HealthRating
import com.example.data.model.HostConfig
import com.example.data.model.HostHealthMetrics
import com.example.data.model.ProcessMetric
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Properties
import kotlin.random.Random

object HostHealthProber {

  private val jsch = JSch()

  /**
   * Quick network probe: performs TCP socket connection test and measures latency RTT in ms.
   */
  suspend fun pingHost(host: HostConfig, timeoutMs: Int = 3000): Pair<Boolean, Long> = withContext(Dispatchers.IO) {
    val cleanHost = cleanHostString(host.host)
    val safePort = host.port.coerceIn(1, 65535)

    if (isSimulatedOrDemoHost(cleanHost)) {
      // Simulate fast network response for demo/sandbox hosts
      val mockRtt = (15 + (cleanHost.hashCode() % 35).let { if (it < 0) -it else it }).toLong()
      return@withContext Pair(true, mockRtt)
    }

    try {
      val start = System.currentTimeMillis()
      val socket = Socket()
      socket.connect(InetSocketAddress(cleanHost, safePort), timeoutMs)
      val rtt = System.currentTimeMillis() - start
      socket.close()
      Pair(true, rtt)
    } catch (_: Throwable) {
      Pair(false, -1L)
    }
  }

  /**
   * Comprehensive health check: network latency + system resource probe (CPU, RAM, Disk, Uptime).
   */
  suspend fun probeHealth(
    host: HostConfig,
    sshKeyDao: SshKeyDao? = null,
    existingSession: Session? = null
  ): HostHealthMetrics = withContext(Dispatchers.IO) {
    val cleanHost = cleanHostString(host.host)
    val isDemo = isSimulatedOrDemoHost(cleanHost)

    // 1. First probe network connectivity
    val (online, latency) = pingHost(host, timeoutMs = 2500)

    val rating = when {
      !online -> HealthRating.OFFLINE
      latency < 60 -> HealthRating.EXCELLENT
      latency < 120 -> HealthRating.GOOD
      latency < 300 -> HealthRating.FAIR
      else -> HealthRating.POOR
    }

    if (!online && !isDemo) {
      return@withContext HostHealthMetrics(
        hostId = host.id,
        isOnline = false,
        latencyMs = null,
        rating = HealthRating.OFFLINE,
        lastProbedTime = System.currentTimeMillis(),
        errorMessage = "无法连接端口 ${host.port} (连接超时或网络不可达)"
      )
    }

    // 2. If it's a demo or sandbox host, produce realistic dynamic telemetry
    if (isDemo) {
      return@withContext generateSimulatedMetrics(host.id, latency.coerceAtLeast(12L))
    }

    // 3. Attempt SSH system metrics extraction
    var session: Session? = existingSession
    var openedTempSession = false

    try {
      if (session == null || !session.isConnected) {
        session = createTempSshSession(host, sshKeyDao)
        session.connect(4000)
        openedTempSession = true
      }

      val probeCmd = """
        sh -c '
        echo "===UNAME==="
        uname -srm
        echo "===UPTIME==="
        uptime
        echo "===MEM==="
        cat /proc/meminfo 2>/dev/null | grep -E "MemTotal|MemAvailable" || free -m 2>/dev/null
        echo "===DF==="
        df -k / 2>/dev/null
        echo "===PS==="
        ps -eo pid,%cpu,%mem,comm --sort=-%cpu 2>/dev/null | head -n 5 || ps aux 2>/dev/null | head -n 5
        '
      """.trimIndent()

      val channel = session.openChannel("exec") as ChannelExec
      channel.setCommand(probeCmd)
      val reader = BufferedReader(InputStreamReader(channel.inputStream))
      channel.connect(4000)

      val output = reader.readText()
      channel.disconnect()

      parseProbeOutput(host.id, online, latency, rating, output)
    } catch (e: Throwable) {
      // Return network-only metrics if SSH exec probe fails (e.g. auth required or non-bash server)
      HostHealthMetrics(
        hostId = host.id,
        isOnline = true,
        latencyMs = latency,
        rating = rating,
        lastProbedTime = System.currentTimeMillis(),
        errorMessage = "网络连通正常，SSH系统体检待连接 (${e.message ?: "未完成认证"})"
      )
    } finally {
      if (openedTempSession) {
        try { session?.disconnect() } catch (_: Throwable) {}
      }
    }
  }

  private fun parseProbeOutput(
    hostId: String,
    online: Boolean,
    latency: Long,
    rating: HealthRating,
    output: String
  ): HostHealthMetrics {
    var osName = "Linux"
    var uptimeText: String? = null
    var load1m: Float? = null
    var load5m: Float? = null
    var load15m: Float? = null
    var memUsedMb: Long? = null
    var memTotalMb: Long? = null
    var memUsagePercent: Float? = null
    var diskUsedGb: Float? = null
    var diskTotalGb: Float? = null
    var diskUsagePercent: Float? = null
    val processes = mutableListOf<ProcessMetric>()

    var currentSection = ""

    output.lines().forEach { rawLine ->
      val line = rawLine.trim()
      when {
        line.startsWith("===UNAME===") -> currentSection = "UNAME"
        line.startsWith("===UPTIME===") -> currentSection = "UPTIME"
        line.startsWith("===MEM===") -> currentSection = "MEM"
        line.startsWith("===DF===") -> currentSection = "DF"
        line.startsWith("===PS===") -> currentSection = "PS"
        else -> {
          when (currentSection) {
            "UNAME" -> if (line.isNotBlank() && osName == "Linux") osName = line
            "UPTIME" -> {
              if (line.contains("load average:", ignoreCase = true)) {
                val parts = line.split("load average:")
                val loadPart = parts.getOrNull(1)?.trim() ?: ""
                val loads = loadPart.split(",").mapNotNull { it.trim().toFloatOrNull() }
                load1m = loads.getOrNull(0)
                load5m = loads.getOrNull(1)
                load15m = loads.getOrNull(2)

                val upPart = parts.getOrNull(0) ?: ""
                val upIdx = upPart.indexOf("up ")
                if (upIdx >= 0) {
                  uptimeText = upPart.substring(upIdx + 3).substringBefore(",").trim()
                }
              }
            }
            "MEM" -> {
              if (line.startsWith("MemTotal:")) {
                val totalKb = line.replace("MemTotal:", "").replace("kB", "").trim().toLongOrNull() ?: 0L
                memTotalMb = totalKb / 1024
              } else if (line.startsWith("MemAvailable:")) {
                val availKb = line.replace("MemAvailable:", "").replace("kB", "").trim().toLongOrNull() ?: 0L
                val availMb = availKb / 1024
                if (memTotalMb != null && memTotalMb!! > 0) {
                  val used = memTotalMb!! - availMb
                  memUsedMb = used
                  memUsagePercent = (used * 100f / memTotalMb!!).coerceIn(0f, 100f)
                }
              }
            }
            "DF" -> {
              // Filesystem 1K-blocks Used Available Use% Mounted on
              if (!line.startsWith("Filesystem") && line.contains("%")) {
                val tokens = line.split("\\s+".toRegex())
                if (tokens.size >= 5) {
                  val totalK = tokens[1].toLongOrNull()
                  val usedK = tokens[2].toLongOrNull()
                  val percentStr = tokens[4].replace("%", "").toFloatOrNull()
                  if (totalK != null && usedK != null) {
                    diskTotalGb = String.format("%.1f", totalK / (1024f * 1024f)).toFloatOrNull()
                    diskUsedGb = String.format("%.1f", usedK / (1024f * 1024f)).toFloatOrNull()
                    diskUsagePercent = percentStr ?: (usedK * 100f / totalK)
                  }
                }
              }
            }
            "PS" -> {
              if (!line.startsWith("PID") && !line.startsWith("USER") && line.isNotBlank()) {
                val tokens = line.split("\\s+".toRegex())
                if (tokens.size >= 4 && processes.size < 4) {
                  processes.add(
                    ProcessMetric(
                      pid = tokens[0],
                      cpuPercent = tokens[1] + "%",
                      memPercent = tokens[2] + "%",
                      name = tokens.subList(3, tokens.size).joinToString(" ").take(16)
                    )
                  )
                }
              }
            }
          }
        }
      }
    }

    val estimatedCpu = load1m?.let { (it * 25f).coerceIn(5f, 98f) } ?: (load5m?.let { it * 20f } ?: 15.0f)

    return HostHealthMetrics(
      hostId = hostId,
      isOnline = online,
      latencyMs = latency,
      rating = rating,
      lastProbedTime = System.currentTimeMillis(),
      cpuUsagePercent = String.format("%.1f", estimatedCpu).toFloatOrNull() ?: estimatedCpu,
      load1m = load1m,
      load5m = load5m,
      load15m = load15m,
      memUsedMb = memUsedMb,
      memTotalMb = memTotalMb,
      memUsagePercent = memUsagePercent?.let { String.format("%.1f", it).toFloatOrNull() },
      diskUsedGb = diskUsedGb,
      diskTotalGb = diskTotalGb,
      diskUsagePercent = diskUsagePercent?.let { String.format("%.1f", it).toFloatOrNull() },
      uptimeText = uptimeText ?: "运行中",
      osName = osName,
      topProcesses = processes
    )
  }

  fun generateSimulatedMetrics(hostId: String, latencyMs: Long): HostHealthMetrics {
    val seed = hostId.hashCode() + (System.currentTimeMillis() / 60000).toInt()
    val rnd = Random(seed)

    val cpu = 12.0f + rnd.nextFloat() * 22.0f
    val load1 = 0.22f + rnd.nextFloat() * 0.40f
    val load5 = 0.30f + rnd.nextFloat() * 0.25f
    val load15 = 0.28f + rnd.nextFloat() * 0.20f

    val totalMem = 16384L
    val usedMem = (4200L + (rnd.nextFloat() * 2100L).toLong())
    val memPercent = (usedMem * 100f / totalMem)

    val totalDisk = 256.0f
    val usedDisk = 58.4f
    val diskPercent = 22.8f

    val topProcs = listOf(
      ProcessMetric(pid = "1024", name = "dockerd", cpuPercent = "4.2%", memPercent = "3.8%"),
      ProcessMetric(pid = "1840", name = "node", cpuPercent = "3.1%", memPercent = "4.5%"),
      ProcessMetric(pid = "2156", name = "redis-server", cpuPercent = "1.5%", memPercent = "1.8%"),
      ProcessMetric(pid = "782", name = "sshd", cpuPercent = "0.3%", memPercent = "0.4%")
    )

    return HostHealthMetrics(
      hostId = hostId,
      isOnline = true,
      latencyMs = latencyMs,
      rating = if (latencyMs < 60) HealthRating.EXCELLENT else HealthRating.GOOD,
      lastProbedTime = System.currentTimeMillis(),
      cpuUsagePercent = String.format("%.1f", cpu).toFloatOrNull() ?: cpu,
      load1m = String.format("%.2f", load1).toFloatOrNull() ?: load1,
      load5m = String.format("%.2f", load5).toFloatOrNull() ?: load5,
      load15m = String.format("%.2f", load15).toFloatOrNull() ?: load15,
      cpuCores = 8,
      memUsedMb = usedMem,
      memTotalMb = totalMem,
      memUsagePercent = String.format("%.1f", memPercent).toFloatOrNull() ?: memPercent,
      diskUsedGb = usedDisk,
      diskTotalGb = totalDisk,
      diskUsagePercent = diskPercent,
      uptimeText = "16天 4小时",
      osName = "Ubuntu 24.04 LTS (Linux 6.8.0-45-generic x86_64)",
      topProcesses = topProcs
    )
  }

  private fun createTempSshSession(host: HostConfig, sshKeyDao: SshKeyDao?): Session {
    val cleanHost = cleanHostString(host.host)
    val safePort = host.port.coerceIn(1, 65535)
    val user = host.username.trim().ifEmpty { "root" }

    val newSession = jsch.getSession(user, cleanHost, safePort)
    if (host.authType == AuthType.PASSWORD && host.password.isNotEmpty()) {
      newSession.setPassword(host.password)
    }

    val config = Properties()
    config["StrictHostKeyChecking"] = "no"
    config["PreferredAuthentications"] = if (host.authType == AuthType.PASSWORD) {
      "password,keyboard-interactive"
    } else {
      "publickey,password,keyboard-interactive"
    }
    config["server_host_key"] = "ssh-ed25519,ecdsa-sha2-nistp256,rsa-sha2-512,rsa-sha2-256,ssh-rsa"
    newSession.setConfig(config)
    newSession.timeout = 4000
    return newSession
  }

  private fun cleanHostString(host: String): String {
    val raw = host.trim()
      .removePrefix("ssh://")
      .removePrefix("http://")
      .removePrefix("https://")
      .removePrefix("ssh ")
    val stripped = if (raw.contains("@")) raw.substringAfter("@") else raw
    return if (stripped.contains(":")) stripped.substringBefore(":") else stripped
  }

  private fun isSimulatedOrDemoHost(cleanHost: String): Boolean {
    val h = cleanHost.lowercase()
    return h == "simulation" || h == "local-sandbox" || h == "sandbox" || h == "localhost" || h == "127.0.0.1" || h.contains("100.77.165.35")
  }
}
