package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class AuthType {
  PASSWORD,
  PRIVATE_KEY,
  KEY_WITH_PASSPHRASE,
  NONE_PROMPT
}

enum class TerminalThemeType {
  DRACULA,
  SOLARIZED_DARK,
  GRUVBOX_DARK,
  GRUVBOX_LIGHT,
  SOLARIZED_LIGHT,
  ONE_DARK,
  MONOKAI,
  NORD,
  RETRO_MATRIX,
  CYBERPUNK
}

@Entity(tableName = "hosts")
data class HostConfig(
  @PrimaryKey
  val id: String = UUID.randomUUID().toString(),
  val name: String,
  val host: String,
  val port: Int = 22,
  val username: String = "root",
  val authType: AuthType = AuthType.PASSWORD,
  val password: String = "",
  val keyId: String? = null,
  val passphrase: String = "",
  val groupTag: String = "默认",
  val initialCommand: String = "",
  val termType: String = "xterm-256color",
  val fontSize: Int = 13,
  val theme: TerminalThemeType = TerminalThemeType.DRACULA,
  val keepAliveIntervalSeconds: Int = 30,
  val lastConnectedAt: Long = 0L,
  val isStarred: Boolean = false
)
