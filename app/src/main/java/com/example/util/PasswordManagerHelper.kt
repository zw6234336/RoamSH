package com.example.util

import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * Utility to seamlessly launch the user's password manager app (e.g. 1Password)
 * or system autofill service directly from RoamSH.
 */
object PasswordManagerHelper {

  fun launchPasswordManager(context: Context) {
    val pm = context.packageManager

    // 1. Target 1Password Android packages
    val onePasswordPackages = listOf(
      "com.onepassword.android",
      "com.agilebits.onepassword"
    )

    for (pkg in onePasswordPackages) {
      val intent = pm.getLaunchIntentForPackage(pkg)
      if (intent != null) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return
      }
    }

    // 2. Generic password managers (Bitwarden, KeePass, LastPass, etc.)
    val genericPackages = listOf(
      "com.x8bit.bitwarden",
      "com.lastpass.lpandroid",
      "keepass2android.keepass2android"
    )

    for (pkg in genericPackages) {
      val intent = pm.getLaunchIntentForPackage(pkg)
      if (intent != null) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return
      }
    }

    // 3. Fallback: Open system Autofill settings
    try {
      val intent = Intent(android.provider.Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(intent)
    } catch (_: Throwable) {
      Toast.makeText(context, "未找到已安装的密码管理应用", Toast.LENGTH_SHORT).show()
    }
  }
}
