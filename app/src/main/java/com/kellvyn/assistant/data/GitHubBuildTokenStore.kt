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

/**
 * Keeps a user-provided GitHub token encrypted using a non-exportable Android Keystore key.
 * The token is used only by the explicitly started private APK build flow.
 */
class GitHubBuildTokenStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("kellvyn_github_build", Context.MODE_PRIVATE)

    fun hasToken(): Boolean = prefs.getString(KEY_TOKEN, null).let { !it.isNullOrBlank() && loadToken().isNotBlank() }

    fun loadToken(): String = decrypt(prefs.getString(KEY_TOKEN, null))

    @Synchronized
    fun saveToken(token: String) {
        val normalized = token.trim()
        require(normalized.startsWith("github_pat_") || normalized.startsWith("ghp_")) {
            "Informe um token pessoal do GitHub válido. O token não será exibido depois de salvo."
        }
        require(normalized.length in 20..300) { "O token tem um tamanho inesperado." }
        prefs.edit().putString(KEY_TOKEN, encrypt(normalized)).apply()
    }

    @Synchronized
    fun clear() {
        prefs.edit().remove(KEY_TOKEN).apply()
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
        const val KEY_ALIAS = "kellvyn_github_build_token_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_LENGTH_BITS = 128
        const val KEY_TOKEN = "encrypted_token"
    }
}
