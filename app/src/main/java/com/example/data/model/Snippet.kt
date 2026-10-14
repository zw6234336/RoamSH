package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "snippets")
data class Snippet(
  @PrimaryKey
  val id: String = UUID.randomUUID().toString(),
  val title: String,
  val command: String,
  val category: String, // "Git", "Docker", "Tmux/Vim", "Dev", "System"
  val autoExecute: Boolean = false // If true, adds trailing newline to execute immediately
)
