package com.example.util

import java.net.URI

data class ParsedCredentials(
  val name: String? = null,
  val host: String? = null,
  val port: Int? = null,
  val username: String? = null,
  val password: String? = null
)

object CredentialParser {

  /**
   * Parses various clipboard formats copied from 1Password, Bitwarden, or terminal:
   * 1. Multi-line 1Password item copies:
   *    Server: 192.168.1.100
   *    Username: dev
   *    Password: MyPassword123
   *    Port: 22
   * 2. SSH URL format: ssh://user:password@hostname:2222
   * 3. SSH Command format: ssh dev@192.168.1.100 -p 22
   * 4. user@host:port format
   */
  fun parse(rawText: String): ParsedCredentials {
    val text = rawText.trim()
    if (text.isBlank()) return ParsedCredentials()

    // Format 1: Multi-line key-value (standard 1Password copy item / notes)
    if (text.contains("\n") || text.contains(":") && (text.contains("user", ignoreCase = true) || text.contains("pass", ignoreCase = true) || text.contains("host", ignoreCase = true))) {
      var parsedHost: String? = null
      var parsedPort: Int? = null
      var parsedUser: String? = null
      var parsedPass: String? = null
      var parsedTitle: String? = null

      val lines = text.lines()
      for (line in lines) {
        val trimmed = line.trim()
        val colonIdx = trimmed.indexOfAny(charArrayOf(':', '='))
        if (colonIdx > 0) {
          val key = trimmed.substring(0, colonIdx).trim().lowercase()
          val value = trimmed.substring(colonIdx + 1).trim()
          when {
            key.contains("host") || key.contains("server") || key.contains("address") || key.contains("ip") || key == "url" -> {
              // May contain port: 192.168.1.1:22 or http://...
              val clean = value.removePrefix("ssh://").removePrefix("http://").removePrefix("https://")
              if (clean.contains(":")) {
                val parts = clean.split(":")
                parsedHost = parts[0]
                parsedPort = parts.getOrNull(1)?.toIntOrNull()
              } else {
                parsedHost = clean
              }
            }
            key.contains("user") || key == "account" || key == "login" -> {
              parsedUser = value
            }
            key.contains("pass") || key == "pwd" || key == "secret" -> {
              parsedPass = value
            }
            key == "port" -> {
              parsedPort = value.toIntOrNull()
            }
            key == "title" || key == "name" -> {
              parsedTitle = value
            }
          }
        }
      }

      if (parsedHost != null || parsedUser != null || parsedPass != null) {
        return ParsedCredentials(
          name = parsedTitle,
          host = parsedHost,
          port = parsedPort,
          username = parsedUser,
          password = parsedPass
        )
      }
    }

    // Format 2: URI format: ssh://user:password@hostname:2222
    if (text.startsWith("ssh://", ignoreCase = true)) {
      try {
        val uri = URI(text)
        val host = uri.host
        val port = if (uri.port > 0) uri.port else null
        val userInfo = uri.userInfo
        val user = userInfo?.substringBefore(":")
        val pass = if (userInfo != null && userInfo.contains(":")) userInfo.substringAfter(":") else null
        return ParsedCredentials(host = host, port = port, username = user, password = pass)
      } catch (_: Exception) {}
    }

    // Format 3: SSH CLI format: ssh -p 2222 user@hostname
    if (text.startsWith("ssh ", ignoreCase = true)) {
      var port: Int? = null
      var userHost: String? = null

      val tokens = text.split("\\s+".toRegex())
      var i = 1
      while (i < tokens.size) {
        val token = tokens[i]
        if (token == "-p" && i + 1 < tokens.size) {
          port = tokens[i + 1].toIntOrNull()
          i++
        } else if (!token.startsWith("-")) {
          userHost = token
        }
        i++
      }

      if (userHost != null) {
        val atIdx = userHost.indexOf('@')
        val (u, h) = if (atIdx >= 0) {
          Pair(userHost.substring(0, atIdx), userHost.substring(atIdx + 1))
        } else {
          Pair(null, userHost)
        }
        return ParsedCredentials(host = h, port = port, username = u)
      }
    }

    // Format 4: user@host or user@host:port
    if (text.contains("@")) {
      val user = text.substringBefore("@")
      val remainder = text.substringAfter("@")
      val (h, p) = if (remainder.contains(":")) {
        Pair(remainder.substringBefore(":"), remainder.substringAfter(":").toIntOrNull())
      } else {
        Pair(remainder, null)
      }
      return ParsedCredentials(host = h, port = p, username = user)
    }

    return ParsedCredentials()
  }
}
