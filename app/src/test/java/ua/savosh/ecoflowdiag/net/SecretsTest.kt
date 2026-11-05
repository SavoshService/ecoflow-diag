package ua.savosh.ecoflowdiag.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.security.MessageDigest

class SecretsTest {
    /** Той самий алгоритм, що hidden() у app/build.gradle.kts: якщо розійдуться, бот і Google-вхід мовчки перестануть працювати. */
    private fun hide(name: String, value: String): String {
        val key = MessageDigest.getInstance("SHA-256").digest("savosh-$name".toByteArray())
        return value.toByteArray().mapIndexed { i, b ->
            "%02x".format((b.toInt() and 0xff) xor (key[i % key.size].toInt() and 0xff))
        }.joinToString("")
    }

    @Test
    fun revealRestoresWhatTheBuildHid() {
        val value = "123456:ABC-def_ghi " + "x".repeat(64) + " ключ"
        assertEquals(value, Secrets.reveal("TG_APP_BOT_TOKEN", hide("TG_APP_BOT_TOKEN", value)))
    }

    @Test
    fun emptySecretStaysEmpty() {
        assertEquals("", Secrets.reveal("TG_APP_BOT_TOKEN", ""))
    }

    @Test
    fun hiddenValueDoesNotContainTheSecret() {
        val value = "123456:ABC-def_ghi"
        val hidden = hide("GOOGLE_SECRET", value)
        assertFalse(hidden.contains(value))
        assertFalse(hidden.contains(value.toByteArray().joinToString("") { "%02x".format(it) }))
    }
}
