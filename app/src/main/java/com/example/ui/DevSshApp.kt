package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ssh.SshConnectionState
import com.example.ui.hosts.HostListScreen
import com.example.ui.keys.KeyManagerScreen
import com.example.ui.settings.TerminalSettingsScreen
import com.example.ui.snippets.SnippetScreen
import com.example.ui.terminal.TerminalScreen
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950
import com.example.ui.tunnels.TunnelsScreen

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun DevSshApp(
  viewModel: DevSshViewModel = viewModel()
) {
  val currentScreen by viewModel.currentScreen.collectAsState()
  val connectionState by viewModel.sshManager.connectionState.collectAsState()
  val isImeVisible = WindowInsets.isImeVisible

  // Handle hardware back button: return to TERMINAL if on SETTINGS, or HOSTS if on other screens
  BackHandler(enabled = currentScreen != AppScreen.HOSTS) {
    if (currentScreen == AppScreen.SETTINGS) {
      viewModel.navigateTo(AppScreen.TERMINAL)
    } else {
      viewModel.navigateTo(AppScreen.HOSTS)
    }
  }

  Scaffold(
    bottomBar = {
      if (!isImeVisible && currentScreen != AppScreen.TERMINAL) {
        NavigationBar(
          containerColor = Slate950,
          contentColor = Color.White,
          tonalElevation = 8.dp,
          modifier = Modifier
            .navigationBarsPadding()
            .testTag("app_bottom_nav")
        ) {
          // 1. Hosts
          NavigationBarItem(
            selected = currentScreen == AppScreen.HOSTS,
            onClick = { viewModel.navigateTo(AppScreen.HOSTS) },
            icon = { Icon(Icons.Default.Computer, contentDescription = "电脑主机") },
            label = { Text("电脑主机", fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(
              selectedIconColor = Color.Black,
              selectedTextColor = CyanAccent,
              indicatorColor = CyanAccent,
              unselectedIconColor = Color.Gray,
              unselectedTextColor = Color.Gray
            ),
            modifier = Modifier.testTag("nav_hosts")
          )

          // 2. Terminal
          val isConnected = connectionState is SshConnectionState.Connected
          NavigationBarItem(
            selected = currentScreen == AppScreen.TERMINAL,
            onClick = { viewModel.navigateTo(AppScreen.TERMINAL) },
            icon = {
              BadgedBox(
                badge = {
                  if (isConnected) {
                    Badge(
                      containerColor = EmeraldSuccess,
                      modifier = Modifier.size(6.dp)
                    )
                  }
                }
              ) {
                Icon(Icons.Default.Terminal, contentDescription = "终端")
              }
            },
            label = { Text("终端控制台", fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(
              selectedIconColor = Color.Black,
              selectedTextColor = CyanAccent,
              indicatorColor = CyanAccent,
              unselectedIconColor = Color.Gray,
              unselectedTextColor = Color.Gray
            ),
            modifier = Modifier.testTag("nav_terminal")
          )


          // 4. Keys
          NavigationBarItem(
            selected = currentScreen == AppScreen.KEYS,
            onClick = { viewModel.navigateTo(AppScreen.KEYS) },
            icon = { Icon(Icons.Default.Key, contentDescription = "密钥") },
            label = { Text("SSH 密钥", fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(
              selectedIconColor = Color.Black,
              selectedTextColor = CyanAccent,
              indicatorColor = CyanAccent,
              unselectedIconColor = Color.Gray,
              unselectedTextColor = Color.Gray
            ),
            modifier = Modifier.testTag("nav_keys")
          )

          // 5. Tunnels
          NavigationBarItem(
            selected = currentScreen == AppScreen.TUNNELS,
            onClick = { viewModel.navigateTo(AppScreen.TUNNELS) },
            icon = { Icon(Icons.Default.SwapHoriz, contentDescription = "端口转发") },
            label = { Text("端口转发", fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(
              selectedIconColor = Color.Black,
              selectedTextColor = CyanAccent,
              indicatorColor = CyanAccent,
              unselectedIconColor = Color.Gray,
              unselectedTextColor = Color.Gray
            ),
            modifier = Modifier.testTag("nav_tunnels")
          )
        }
      }
    },
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    containerColor = Slate950,
    modifier = Modifier.fillMaxSize()
  ) { paddingValues ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(if (currentScreen == AppScreen.TERMINAL) PaddingValues(0.dp) else paddingValues)
    ) {
      when (currentScreen) {
        AppScreen.HOSTS -> HostListScreen(viewModel = viewModel)
        AppScreen.TERMINAL -> TerminalScreen(viewModel = viewModel)
        AppScreen.SNIPPETS -> SnippetScreen(viewModel = viewModel)
        AppScreen.KEYS -> KeyManagerScreen(viewModel = viewModel)
        AppScreen.TUNNELS -> TunnelsScreen(viewModel = viewModel)
        AppScreen.SETTINGS -> TerminalSettingsScreen(
          viewModel = viewModel,
          onBack = { viewModel.navigateTo(AppScreen.TERMINAL) }
        )
      }
    }
  }
}
