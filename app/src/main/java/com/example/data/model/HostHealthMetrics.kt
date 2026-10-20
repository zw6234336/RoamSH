package com.example.data.model

enum class HealthRating {
  EXCELLENT, // < 50ms
  GOOD,      // 50-100ms
  FAIR,      // 100-250ms
  POOR,      // > 250ms
  OFFLINE,   // Timeout / error
  UNCHECKED  // Not probed yet
}

data class ProcessMetric(
  val pid: String,
  val name: String,
  val cpuPercent: String,
  val memPercent: String
)

data class HostHealthMetrics(
  val hostId: String,
  val isOnline: Boolean = false,
  val latencyMs: Long? = null,
  val rating: HealthRating = HealthRating.UNCHECKED,
  val lastProbedTime: Long = 0L,
  val isProbing: Boolean = false,
  val errorMessage: String? = null,
  
  // CPU metrics
  val cpuUsagePercent: Float? = null,
  val load1m: Float? = null,
  val load5m: Float? = null,
  val load15m: Float? = null,
  val cpuCores: Int? = null,

  // Memory metrics
  val memUsedMb: Long? = null,
  val memTotalMb: Long? = null,
  val memUsagePercent: Float? = null,

  // Disk metrics
  val diskUsedGb: Float? = null,
  val diskTotalGb: Float? = null,
  val diskUsagePercent: Float? = null,

  // System metadata
  val uptimeText: String? = null,
  val osName: String? = null,
  val topProcesses: List<ProcessMetric> = emptyList()
) {
  val hasSystemMetrics: Boolean
    get() = cpuUsagePercent != null || memUsagePercent != null || diskUsagePercent != null
}
