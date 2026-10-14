package com.example.data

import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPair
import java.io.ByteArrayOutputStream

object KeyGenerator {
  data class GeneratedKeyPair(
    val keyType: String,
    val publicKey: String,
    val privateKey: String
  )

  fun generateKey(keyType: String = "RSA", comment: String = "roamsh-android"): GeneratedKeyPair {
    val jsch = JSch()
    val kp = when (keyType.uppercase()) {
      "ED25519" -> {
        try {
          KeyPair.genKeyPair(jsch, KeyPair.ED25519)
        } catch (_: Exception) {
          // Fallback to RSA if ED25519 is not supported by device provider
          KeyPair.genKeyPair(jsch, KeyPair.RSA, 2048)
        }
      }
      "ECDSA" -> KeyPair.genKeyPair(jsch, KeyPair.ECDSA, 256)
      else -> KeyPair.genKeyPair(jsch, KeyPair.RSA, 2048)
    }

    val pubOut = ByteArrayOutputStream()
    val privOut = ByteArrayOutputStream()

    kp.writePublicKey(pubOut, comment)
    kp.writePrivateKey(privOut)
    val pubKeyStr = pubOut.toString("UTF-8").trim()
    val privKeyStr = privOut.toString("UTF-8").trim()
    kp.dispose()

    return GeneratedKeyPair(
      keyType = keyType,
      publicKey = pubKeyStr,
      privateKey = privKeyStr
    )
  }
}
