package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "ssh_keys")
data class SshKey(
  @PrimaryKey
  val id: String = UUID.randomUUID().toString(),
  val name: String,
  val keyType: String, // "ED25519" or "RSA"
  val publicKey: String,
  val privateKey: String,
  val passphrase: String = "",
  val createdAt: Long = System.currentTimeMillis()
)
