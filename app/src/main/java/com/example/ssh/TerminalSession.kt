package com.example.ssh

import androidx.compose.foundation.lazy.LazyListState
import com.example.data.model.HostConfig
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.UUID

/**
 * Represents an independent terminal session with its own SSH connection, PTY,
 * ANSI terminal buffer, and scroll state.
 */
data class TerminalSession(
  val id: String = UUID.randomUUID().toString(),
  val title: String,
  val host: HostConfig,
  val sshManager: SshManager,
  val listState: LazyListState = LazyListState(),
  val autoScroll: MutableStateFlow<Boolean> = MutableStateFlow(true),
  val isSimulated: Boolean = false,
  val createdAt: Long = System.currentTimeMillis()
)
