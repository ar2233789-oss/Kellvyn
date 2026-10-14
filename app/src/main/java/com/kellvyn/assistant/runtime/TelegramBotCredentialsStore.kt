package com.kellvyn.assistant.runtime

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class TelegramBotCredentials(val token: String)

/** Stores Telegram bot tokens encrypted with a dedicated Android Keystore key. */
class TelegramBotCredentialsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("kellvyn_telegram_bot_credentials", Context.MODE_PRIVATE)

    @Synchronized
    fun save(projectId: String, token: String) {
        require(validProjectId(projectId)) { "Identificador de projeto inválido." }
        val cleanToken = token.trim()
        require(cleanToken.length >= 30 && !cleanToken.any(Char::isWhitespace) &&
            cleanToken.matches(Regex("[0-9]{5,}:[A-Za-z0-9_-]{20,}"))) {
            "Informe o token completo do bot fornecido pelo BotFather."
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey())
        val encrypted = cipher.doFinal(cleanToken.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString("iv_$projectId", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("token_$projectId", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .apply()
    }

    @Synchronized
    fun get(projectId: String): TelegramBotCredentials? {
        if (!validProjectId(projectId)) return null
        val ivText = prefs.getString("iv_$projectId", null) ?: return null
        val tokenText = prefs.getString("token_$projectId", null) ?: return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                encryptionKey(),
                GCMParameterSpec(128, Base64.decode(ivText, Base64.NO_WRAP))
            )
            val token = String(
                cipher.doFinal(Base64.decode(tokenText, Base64.NO_WRAP)),
                Charsets.UTF_8
            )
            TelegramBotCredentials(token)
        }.getOrNull()
    }

    @Synchronized
    fun remove(projectId: String) {
        if (!validProjectId(projectId)) return
        prefs.edit().remove("iv_$projectId").remove("token_$projectId").apply()
    }

    private fun encryptionKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun validProjectId(value: String) = value.matches(Regex("[a-z0-9][a-z0-9-]{0,60}"))

    private companion object {
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "kellvyn_telegram_bot_aes_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
