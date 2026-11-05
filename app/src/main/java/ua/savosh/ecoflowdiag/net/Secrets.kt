package ua.savosh.ecoflowdiag.net

import java.security.MessageDigest

/**
 * Секрети збірки: в APK вони лежать змішаними, щоб їх не витягнути простим пошуком рядків.
 * Рішучого зломщика це не зупинить, тому в застосунок не можна класти нічого цінного.
 */
internal object Secrets {
    /** Пара до hidden() у app/build.gradle.kts. */
    fun reveal(name: String, hidden: String): String {
        if (hidden.isEmpty()) return ""
        val key = MessageDigest.getInstance("SHA-256").digest("savosh-$name".toByteArray())
        val bytes = ByteArray(hidden.length / 2) { i ->
            val b = hidden.substring(i * 2, i * 2 + 2).toInt(16)
            (b xor (key[i % key.size].toInt() and 0xff)).toByte()
        }
        return String(bytes, Charsets.UTF_8)
    }
}
