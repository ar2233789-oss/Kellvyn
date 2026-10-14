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

data class DiscordBotCredentials(val token: String, val applicationId: String)

/** Stores Discord tokens encrypted with an AES key held by Android Keystore. */
class DiscordBotCredentialsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("kellvyn_discord_bot_credentials", Context.MODE_PRIVATE)

    @Synchronized
    fun save(projectId: String, token: String, applicationId: String) {
        require(validProjectId(projectId)) { "Identificador de projeto inválido." }
        val cleanToken = token.trim()
        val cleanApplicationId = applicationId.trim()
        require(cleanToken.length >= 20 && !cleanToken.any(Char::isWhitespace)) {
            "Informe o token do bot do Discord sem espaços."
        }
        require(cleanApplicationId.matches(Regex("[0-9]{15,22}"))) {
            "Informe o Application ID numérico do Discord Developer Portal."
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey())
        val encrypted = cipher.doFinal(cleanToken.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString("iv_$projectId", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("token_$projectId", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString("app_$projectId", cleanApplicationId)
            .apply()
    }

    @Synchronized
    fun get(projectId: String): DiscordBotCredentials? {
        if (!validProjectId(projectId)) return null
        val ivText = prefs.getString("iv_$projectId", null) ?: return null
        val tokenText = prefs.getString("token_$projectId", null) ?: return null
        val applicationId = prefs.getString("app_$projectId", null) ?: return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                encryptionKey(),
                GCMParameterSpec(128, Base64.decode(ivText, Base64.NO_WRAP))
            )
            val token = String(Base64.decode(tokenText, Base64.NO_WRAP).let(cipher::doFinal), Charsets.UTF_8)
            DiscordBotCredentials(token, applicationId)
        }.getOrNull()
    }

    @Synchronized
    fun remove(projectId: String) {
        if (!validProjectId(projectId)) return
        prefs.edit().remove("iv_$projectId").remove("token_$projectId").remove("app_$projectId").apply()
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
        const val KEY_ALIAS = "kellvyn_discord_bot_aes_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

/** Persistent registry separate from secrets so START_STICKY can restore enabled bots. */
class GeneratedProjectRuntimeStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("kellvyn_project_runtime", Context.MODE_PRIVATE)

    @Synchronized
    fun activeProjectIds(): Set<String> = prefs.getStringSet(KEY_ACTIVE, emptySet()).orEmpty().toSet()

    @Synchronized
    fun isActive(projectId: String): Boolean = projectId in activeProjectIds()

    @Synchronized
    fun setActive(projectId: String, active: Boolean) {
        val ids = activeProjectIds().toMutableSet()
        if (active) ids.add(projectId) else ids.remove(projectId)
        prefs.edit().putStringSet(KEY_ACTIVE, ids).apply()
    }

    private companion object {
        const val KEY_ACTIVE = "active_projects"
    }
}
