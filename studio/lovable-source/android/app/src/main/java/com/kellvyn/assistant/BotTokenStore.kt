package com.kellvyn.assistant

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores bot credentials encrypted with an AES-GCM key held in Android Keystore.
 * The raw token is never written to a preference or to the chat/model context.
 */
object BotTokenStore {
    private const val KEY_ALIAS = "kellvyn.bot.tokens.aes"
    private const val PREFS = "kellvyn_bot_tokens"
    private const val IV_LENGTH = 12

    private fun validPlatform(platform: String): Boolean =
        platform == "telegram" || platform == "discord"

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore")
        store.load(null)
        val existing = store.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
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

    @JvmStatic
    fun save(context: Context, platform: String, rawToken: String): Boolean {
        if (!validPlatform(platform)) return false
        val token = rawToken.trim()
        if (token.length !in 12..512 || token.any { it.isWhitespace() }) return false
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val iv = cipher.iv
            val encrypted = cipher.doFinal(token.toByteArray(StandardCharsets.UTF_8))
            val packed = ByteBuffer.allocate(iv.size + encrypted.size).put(iv).put(encrypted).array()
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(platform, Base64.encodeToString(packed, Base64.NO_WRAP)).apply()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun read(context: Context, platform: String): String? {
        if (!validPlatform(platform)) return null
        return try {
            val stored = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(platform, null) ?: return null
            val packed = Base64.decode(stored, Base64.NO_WRAP)
            if (packed.size <= IV_LENGTH) return null
            val iv = packed.copyOfRange(0, IV_LENGTH)
            val ciphertext = packed.copyOfRange(IV_LENGTH, packed.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            cipher.doFinal(ciphertext).toString(StandardCharsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    @JvmStatic
    fun hasToken(context: Context, platform: String): Boolean =
        !read(context, platform).isNullOrBlank()

    fun clear(context: Context, platform: String) {
        if (!validPlatform(platform)) return
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(platform).apply()
    }
}
