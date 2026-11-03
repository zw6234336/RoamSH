package com.example.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ssh.SshConnectionState
import com.example.ssh.TerminalThemes
import com.example.ui.hosts.HostListScreen
import com.example.ui.keys.KeyManagerScreen
import com.example.ui.settings.TerminalSettingsScreen
import com.example.ui.snippets.SnippetScreen
import com.example.ui.terminal.TerminalScreen
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoseError
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950
import com.example.ui.theme.TerminalDrivenTheme
import com.example.ui.theme.isDarkScheme
import com.example.ui.tunnels.TunnelsScreen

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
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

  // The selected terminal theme drives the whole app chrome: nav bar, cards,
  // dialogs and the terminal viewport all follow the same palette.
  val terminalThemeType by viewModel.terminalThemeType.collectAsState()
  val terminalScheme = TerminalThemes.get(terminalThemeType)

  // Keep status/navigation bar icon contrast correct even when a light terminal
  // theme (e.g. Gruvbox Light) runs while the system stays in dark mode.
  val view = LocalView.current
  val isChromeDark = isDarkScheme(terminalScheme)
  SideEffect {
    val window = (view.context as? Activity)?.window ?: return@SideEffect
    val controller = WindowCompat.getInsetsController(window, view)
    controller.isAppearanceLightStatusBars = !isChromeDark
    controller.isAppearanceLightNavigationBars = !isChromeDark
  }

  TerminalDrivenTheme(scheme = terminalScheme) {
    DevSshAppContent(viewModel, currentScreen, isImeVisible)
  }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun DevSshAppContent(
  viewModel: DevSshViewModel,
  currentScreen: AppScreen,
  isImeVisible: Boolean
) {
  // ---- Unified transient feedback: ViewModel messages -> app-level Snackbar ----
  val snackbarHostState = remember { SnackbarHostState() }
  val uiMessage by viewModel.uiMessage.collectAsState()
  LaunchedEffect(uiMessage) {
    val message = uiMessage ?: return@LaunchedEffect
    snackbarHostState.showSnackbar(message.text)
    viewModel.consumeUiMessage()
  }

  // Handle hardware back button: return to TERMINAL if on SETTINGS, or HOSTS if on other screens
  BackHandler(enabled = currentScreen != AppScreen.HOSTS) {
    if (currentScreen == AppScreen.SETTINGS) {
      viewModel.navigateTo(AppScreen.TERMINAL)
    } else {
      viewModel.navigateTo(AppScreen.HOSTS)
    }
  }

  Scaffold(
    snackbarHost = {
      // imePadding keeps copy/paste feedback visible while the soft keyboard is up
      SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.imePadding()
      ) { data ->
        Snackbar(
          snackbarData = data,
          containerColor = if (uiMessage?.isError == true) RoseError else Slate800,
          contentColor = if (uiMessage?.isError == true) MaterialTheme.colorScheme.onError
          else MaterialTheme.colorScheme.onSurface
        )
      }
    },
    bottomBar = {
      if (!isImeVisible && currentScreen != AppScreen.TERMINAL) {
        NavigationBar(
          containerColor = Slate950,
          contentColor = MaterialTheme.colorScheme.onSurface,
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
              selectedIconColor = MaterialTheme.colorScheme.onPrimary,
              selectedTextColor = CyanAccent,
              indicatorColor = CyanAccent,
              unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
              unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier.testTag("nav_hosts")
          )

          // 2. Terminal
          val sessions by viewModel.sessions.collectAsState()
          val hasConnectedSessions = sessions.any { it.sshManager.connectionState.value is SshConnectionState.Connected }
          NavigationBarItem(
            selected = currentScreen == AppScreen.TERMINAL,
            onClick = { viewModel.navigateTo(AppScreen.TERMINAL) },
            icon = {
              BadgedBox(
                badge = {
                  if (sessions.isNotEmpty()) {
                    Badge(
                      containerColor = if (hasConnectedSessions) EmeraldSuccess else CyanAccent,
                      contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                      Text("${sessions.size}", fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                  }
                }
              ) {
                Icon(Icons.Default.Terminal, contentDescription = "终端")
              }
            },
            label = { Text("终端控制台", fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(
              selectedIconColor = MaterialTheme.colorScheme.onPrimary,
              selectedTextColor = CyanAccent,
              indicatorColor = CyanAccent,
              unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
              unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier.testTag("nav_terminal")
          )


          // 3. Snippets — quick command fragments sent to the terminal
          NavigationBarItem(
            selected = currentScreen == AppScreen.SNIPPETS,
            onClick = { viewModel.navigateTo(AppScreen.SNIPPETS) },
            icon = { Icon(Icons.Default.Code, contentDescription = "指令片段") },
            label = { Text("指令片段", fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(
              selectedIconColor = MaterialTheme.colorScheme.onPrimary,
              selectedTextColor = CyanAccent,
              indicatorColor = CyanAccent,
              unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
              unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier.testTag("nav_snippets")
          )

          // 4. Keys
          NavigationBarItem(
            selected = currentScreen == AppScreen.KEYS,
            onClick = { viewModel.navigateTo(AppScreen.KEYS) },
            icon = { Icon(Icons.Default.Key, contentDescription = "密钥") },
            label = { Text("SSH 密钥", fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(
              selectedIconColor = MaterialTheme.colorScheme.onPrimary,
              selectedTextColor = CyanAccent,
              indicatorColor = CyanAccent,
              unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
              unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
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
              selectedIconColor = MaterialTheme.colorScheme.onPrimary,
              selectedTextColor = CyanAccent,
              indicatorColor = CyanAccent,
              unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
              unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
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
      // Animated cross-screen transition instead of a hard swap: a quick fade with a
      // subtle scale-in makes tab switching feel responsive rather than jarring.
      AnimatedContent(
        targetState = currentScreen,
        transitionSpec = {
          (fadeIn(animationSpec = tween(220)) + scaleIn(initialScale = 0.98f, animationSpec = tween(220)))
            .togetherWith(fadeOut(animationSpec = tween(150)))
        },
        label = "screen_transition"
      ) { screen ->
        when (screen) {
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
}
