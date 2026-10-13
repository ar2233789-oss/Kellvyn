package com.kellvyn.assistant.data

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

data class OnlineProviderSettings(
    val endpoint: String = "",
    val model: String = "",
    val apiKey: String = ""
) {
    val isConfigured: Boolean
        get() = endpoint.isNotBlank() && model.isNotBlank()
}

/**
 * Saves provider URL/model in app-private preferences and encrypts the optional
 * bearer token with an AES-GCM key held by Android Keystore.
 */
class OnlineProviderSettingsStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("kellvyn_online_provider", Context.MODE_PRIVATE)

    fun load(): OnlineProviderSettings = OnlineProviderSettings(
        endpoint = prefs.getString(KEY_ENDPOINT, "").orEmpty(),
        model = prefs.getString(KEY_MODEL, "").orEmpty(),
        apiKey = decrypt(prefs.getString(KEY_TOKEN, null))
    )

    @Synchronized
    fun save(endpoint: String, model: String, apiKey: String) {
        val editor = prefs.edit()
            .putString(KEY_ENDPOINT, endpoint.trim())
            .putString(KEY_MODEL, model.trim())
        if (apiKey.isBlank()) {
            editor.remove(KEY_TOKEN)
        } else {
            editor.putString(KEY_TOKEN, encrypt(apiKey.trim()))
        }
        editor.apply()
    }

    @Synchronized
    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        val packed = ByteBuffer.allocate(iv.size + encrypted.size).put(iv).put(encrypted).array()
        return Base64.encodeToString(packed, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String?): String {
        if (encoded.isNullOrBlank()) return ""
        return try {
            val packed = Base64.decode(encoded, Base64.NO_WRAP)
            val buffer = ByteBuffer.wrap(packed)
            val iv = ByteArray(IV_LENGTH)
            buffer.get(iv)
            val encrypted = ByteArray(buffer.remaining())
            buffer.get(encrypted)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
            String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
        } catch (_: Exception) {
            // Keystore key loss or corrupted settings requires the user to save the token again.
            ""
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_TYPE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_TYPE)
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

    private companion object {
        const val KEYSTORE_TYPE = "AndroidKeyStore"
        const val KEY_ALIAS = "kellvyn_online_token_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_LENGTH_BITS = 128
        const val KEY_ENDPOINT = "endpoint"
        const val KEY_MODEL = "model"
        const val KEY_TOKEN = "encrypted_token"
    }
}
