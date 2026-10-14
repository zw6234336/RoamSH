package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.ui.DevSshApp
import com.example.ui.theme.MyApplicationTheme
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    try {
      if (Security.getProvider("BC") == null) {
        Security.addProvider(BouncyCastleProvider())
      }
    } catch (t: Throwable) {
      android.util.Log.w("MainActivity", "BouncyCastle init: ${t.message}")
    }

    val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
      android.util.Log.e("RoamSH", "Uncaught exception in thread ${thread.name}: ${throwable.message}", throwable)
      defaultHandler?.uncaughtException(thread, throwable)
    }

    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        DevSshApp()
      }
    }
  }
}
