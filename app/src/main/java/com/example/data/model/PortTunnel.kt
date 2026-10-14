package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class TunnelType {
  LOCAL_TO_REMOTE, // -L localPort:remoteHost:remotePort (access remote server/service on phone)
  REMOTE_TO_LOCAL  // -R remotePort:localHost:localPort (expose phone/local service to remote)
}

@Entity(tableName = "port_tunnels")
data class PortTunnel(
  @PrimaryKey
  val id: String = UUID.randomUUID().toString(),
  val hostId: String,
  val name: String,
  val type: TunnelType = TunnelType.LOCAL_TO_REMOTE,
  val localPort: Int = 3000,
  val remoteHost: String = "127.0.0.1",
  val remotePort: Int = 3000,
  val isEnabled: Boolean = false
)
