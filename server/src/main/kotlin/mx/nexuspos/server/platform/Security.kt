package mx.nexuspos.server.platform

import org.mindrot.jbcrypt.BCrypt
import java.security.MessageDigest
import java.util.UUID

object Security {

    fun hashPin(pin: String): String {
        return BCrypt.hashpw(pin, BCrypt.gensalt(10))
    }

    fun verifyPin(pin: String, hash: String): Boolean {
        return try {
            BCrypt.checkpw(pin, hash)
        } catch (e: Exception) {
            false
        }
    }

    fun generateSecureToken(): String {
        return UUID.randomUUID().toString() + "-" + UUID.randomUUID().toString()
    }

    fun hashToken(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(token.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
