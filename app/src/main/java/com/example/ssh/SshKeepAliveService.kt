package com.example.ssh

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

class SshKeepAliveService : Service() {

  private var wakeLock: PowerManager.WakeLock? = null
  private var wifiLock: android.net.wifi.WifiManager.WifiLock? = null

  companion object {
    const val CHANNEL_ID = "ssh_keepalive_channel"
    const val NOTIFICATION_ID = 1001

    const val ACTION_START_KEEPALIVE = "com.example.ssh.ACTION_START_KEEPALIVE"
    const val ACTION_STOP_KEEPALIVE = "com.example.ssh.ACTION_STOP_KEEPALIVE"
    const val ACTION_DISCONNECT_FROM_NOTIFICATION = "com.example.ssh.ACTION_DISCONNECT_FROM_NOTIFICATION"

    const val EXTRA_HOST_NAME = "extra_host_name"
    const val EXTRA_HOST_ADDRESS = "extra_host_address"

    var isServiceRunning = false
      private set

    // Callback when user taps "断开" button in the notification
    var onDisconnectRequested: (() -> Unit)? = null

    fun start(context: Context, hostName: String, hostAddress: String) {
      try {
        val intent = Intent(context, SshKeepAliveService::class.java).apply {
          action = ACTION_START_KEEPALIVE
          putExtra(EXTRA_HOST_NAME, hostName)
          putExtra(EXTRA_HOST_ADDRESS, hostAddress)
        }
        ContextCompat.startForegroundService(context, intent)
      } catch (t: Throwable) {
        android.util.Log.w("SshKeepAliveService", "Cannot start foreground keepalive service: ${t.message}")
      }
    }

    fun stop(context: Context) {
      try {
        val intent = Intent(context, SshKeepAliveService::class.java).apply {
          action = ACTION_STOP_KEEPALIVE
        }
        context.startService(intent)
      } catch (t: Throwable) {
        android.util.Log.w("SshKeepAliveService", "Cannot stop keepalive service: ${t.message}")
      }
    }
  }

  override fun onCreate() {
    super.onCreate()
    try {
      createNotificationChannel()
      // CRITICAL: Call startForeground immediately in onCreate so Android OS 5-second deadline is ALWAYS met!
      val defaultNotification = buildForegroundNotification("SSH 远程终端", "会话连接保持中")
      promoteToForeground(defaultNotification)
      acquireWakeLock()
    } catch (t: Throwable) {
      android.util.Log.w("SshKeepAliveService", "Error in onCreate: ${t.message}")
    }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_STOP_KEEPALIVE -> {
        isServiceRunning = false
        try {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
          } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
          }
        } catch (_: Throwable) {}
        stopSelf()
        return START_NOT_STICKY
      }
      ACTION_DISCONNECT_FROM_NOTIFICATION -> {
        isServiceRunning = false
        onDisconnectRequested?.invoke()
        try {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
          } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
          }
        } catch (_: Throwable) {}
        stopSelf()
        return START_NOT_STICKY
      }
      else -> {
        val hostName = intent?.getStringExtra(EXTRA_HOST_NAME) ?: "远程电脑"
        val hostAddress = intent?.getStringExtra(EXTRA_HOST_ADDRESS) ?: "已连接"

        try {
          val updatedNotification = buildForegroundNotification(hostName, hostAddress)
          promoteToForeground(updatedNotification)
          val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
          notificationManager?.notify(NOTIFICATION_ID, updatedNotification)
          isServiceRunning = true
        } catch (t: Throwable) {
          android.util.Log.e("SshKeepAliveService", "Failed to update notification: ${t.message}")
        }
      }
    }
    return START_STICKY
  }

  private fun promoteToForeground(notification: Notification) {
    var posted = false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      try {
        startForeground(
          NOTIFICATION_ID,
          notification,
          ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
        posted = true
      } catch (t: Throwable) {
        android.util.Log.w("SshKeepAliveService", "startForeground dataSync failed: ${t.message}")
      }

      if (!posted) {
        try {
          startForeground(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
          )
          posted = true
        } catch (t: Throwable) {
          android.util.Log.w("SshKeepAliveService", "startForeground specialUse failed: ${t.message}")
        }
      }
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      try {
        startForeground(
          NOTIFICATION_ID,
          notification,
          ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
        posted = true
      } catch (t: Throwable) {
        android.util.Log.w("SshKeepAliveService", "startForeground Q dataSync failed: ${t.message}")
      }
    }

    if (!posted) {
      try {
        @Suppress("DEPRECATION")
        startForeground(NOTIFICATION_ID, notification)
        posted = true
      } catch (t: Throwable) {
        android.util.Log.w("SshKeepAliveService", "Legacy startForeground failed: ${t.message}")
      }
    }
    isServiceRunning = posted
  }

  private fun buildForegroundNotification(hostName: String, hostAddress: String): Notification {
    val openIntent = Intent(this, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val openPendingIntent = PendingIntent.getActivity(
      this,
      0,
      openIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val disconnectIntent = Intent(this, SshKeepAliveService::class.java).apply {
      action = ACTION_DISCONNECT_FROM_NOTIFICATION
    }
    val disconnectPendingIntent = PendingIntent.getService(
      this,
      1,
      disconnectIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    // Using locally guaranteed monochrome vector icon ic_stat_terminal
    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setContentTitle("RoamSH 会话后台保持中")
      .setContentText("$hostName ($hostAddress)")
      .setSmallIcon(R.drawable.ic_stat_terminal)
      .setOngoing(true)
      .setContentIntent(openPendingIntent)
      .addAction(
        android.R.drawable.ic_menu_close_clear_cancel,
        "断开连接",
        disconnectPendingIntent
      )
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .setCategory(NotificationCompat.CATEGORY_SERVICE)
      .build()
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        CHANNEL_ID,
        "SSH 终端后台连接保持",
        NotificationManager.IMPORTANCE_LOW
      ).apply {
        description = "防止锁屏或切换应用时 SSH 会话意外中断"
        setShowBadge(false)
      }
      val manager = getSystemService(NotificationManager::class.java)
      manager?.createNotificationChannel(channel)
    }
  }

  private fun acquireWakeLock() {
    if (wakeLock == null) {
      try {
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(
          PowerManager.PARTIAL_WAKE_LOCK,
          "RoamSH::ConnectionKeepAliveWakeLock"
        )?.apply {
          setReferenceCounted(false)
          acquire(8 * 60 * 60 * 1000L) // Safe 8-hour timeout limit
        }
      } catch (_: Throwable) {}
    }
    if (wifiLock == null) {
      try {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
        val lockType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          android.net.wifi.WifiManager.WIFI_MODE_FULL_LOW_LATENCY
        } else {
          @Suppress("DEPRECATION")
          android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF
        }
        wifiLock = wifiManager?.createWifiLock(lockType, "RoamSH::WifiKeepAliveLock")?.apply {
          setReferenceCounted(false)
          acquire()
        }
      } catch (_: Throwable) {}
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    isServiceRunning = false
    try {
      wakeLock?.let {
        if (it.isHeld) it.release()
      }
    } catch (_: Exception) {}
    wakeLock = null

    try {
      wifiLock?.let {
        if (it.isHeld) it.release()
      }
    } catch (_: Exception) {}
    wifiLock = null
  }

  override fun onBind(intent: Intent?): IBinder? = null
}
