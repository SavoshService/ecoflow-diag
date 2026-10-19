package ua.savosh.ecoflowdiag.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Зашифроване сховище для пароля EcoFlow.
 * Ключ шифрування (AES-256/GCM) створюється в Android Keystore і ніколи не покидає пристрій;
 * у налаштуваннях зберігається лише шифротекст.
 */
class SecureStore(context: Context) {

    private val sp: SharedPreferences = context.getSharedPreferences("savosh_ecoflow_secure", Context.MODE_PRIVATE)

    fun put(name: String, value: String?) {
        if (value.isNullOrEmpty()) {
            sp.edit().remove(name).apply()
            return
        }
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val ct = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            val enc = Base64.getEncoder()
            sp.edit().putString(name, enc.encodeToString(cipher.iv) + ":" + enc.encodeToString(ct)).apply()
        } catch (e: Exception) {
            sp.edit().remove(name).apply()
        }
    }

    fun get(name: String): String? {
        val stored = sp.getString(name, null) ?: return null
        return try {
            val parts = stored.split(":")
            if (parts.size != 2) return null
            val dec = Base64.getDecoder()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, dec.decode(parts[0])))
            String(cipher.doFinal(dec.decode(parts[1])), Charsets.UTF_8)
        } catch (e: Exception) {
            // Ключ шифрування втрачено (наприклад, після перенесення даних на інший телефон) — пароль треба ввести заново.
            null
        }
    }

    fun clear() = sp.edit().clear().apply()

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE)
        ks.load(null)
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "savosh_ecoflow_login"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
