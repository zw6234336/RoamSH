package com.example

import com.example.data.model.HealthRating
import com.example.data.model.HostConfig
import com.example.ssh.HostHealthProber
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HostHealthProberTest {

  @Test
  fun `verify simulated metrics generation for demo host`() {
    val simulated = HostHealthProber.generateSimulatedMetrics("test-host-id", 25L)
    assertEquals("test-host-id", simulated.hostId)
    assertTrue(simulated.isOnline)
    assertEquals(25L, simulated.latencyMs)
    assertEquals(HealthRating.EXCELLENT, simulated.rating)
    assertNotNull(simulated.cpuUsagePercent)
    assertTrue(simulated.cpuUsagePercent!! > 0f)
    assertNotNull(simulated.memUsagePercent)
    assertTrue(simulated.memUsagePercent!! > 0f)
    assertNotNull(simulated.diskUsagePercent)
    assertTrue(simulated.diskUsagePercent!! > 0f)
    assertTrue(simulated.topProcesses.isNotEmpty())
  }

  @Test
  fun `verify ping for simulated host returns success and low latency`() = runBlocking {
    val host = HostConfig(
      name = "Sandbox Demo",
      host = "local-sandbox",
      port = 22
    )
    val (online, latency) = HostHealthProber.pingHost(host)
    assertTrue(online)
    assertTrue(latency in 0..100)
  }
}
