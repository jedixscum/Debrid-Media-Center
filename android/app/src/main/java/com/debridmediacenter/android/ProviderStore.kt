package com.debridmediacenter.android

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores provider tokens encrypted with a non-exportable Android Keystore AES key.
 * The app preferences contain only Base64-encoded IV/ciphertext, never the token itself.
 */
class ProviderStore(context: Context) {
    private val prefs = context.getSharedPreferences("provider_secrets", Context.MODE_PRIVATE)
    private val keyAlias = "debrid_media_center_provider_key"

    fun hasToken(provider: String): Boolean = prefs.contains(storageKey(provider))

    fun saveToken(provider: String, token: String) {
        val normalized = token.trim()
        require(normalized.isNotEmpty()) { "Enter an API key first." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(normalized.toByteArray(Charsets.UTF_8))
        val packed = cipher.iv + encrypted
        check(prefs.edit().putString(storageKey(provider), Base64.encodeToString(packed, Base64.NO_WRAP)).commit()) {
            "Could not save the encrypted API key."
        }
    }

    fun readToken(provider: String): String? {
        val packedText = prefs.getString(storageKey(provider), null) ?: return null
        val packed = Base64.decode(packedText, Base64.NO_WRAP)
        require(packed.size > 12) { "Saved key data is invalid. Please enter the key again." }
        val iv = packed.copyOfRange(0, 12)
        val ciphertext = packed.copyOfRange(12, packed.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    fun removeToken(provider: String) {
        prefs.edit().remove(storageKey(provider)).apply()
    }

    fun testConnection(provider: String, token: String? = readToken(provider)): String {
        val secret = token?.trim().orEmpty()
        require(secret.isNotEmpty()) { "Enter an API key first." }

        val endpoint = when (provider) {
            REAL_DEBRID -> "https://api.real-debrid.com/rest/1.0/user"
            TORBOX -> "https://api.torbox.app/v1/api/user/me"
            else -> error("Unknown provider.")
        }

        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12000
            readTimeout = 12000
            setRequestProperty("Authorization", "Bearer $secret")
            setRequestProperty("Accept", "application/json")
        }

        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val detail = runCatching { JSONObject(body).optString("detail").ifBlank { JSONObject(body).optString("error") } }.getOrNull()
                throw IllegalStateException(
                    when (status) {
                        401, 403 -> "Authentication failed. Check the API key and account access."
                        429 -> "The provider is rate-limiting requests. Wait a moment and retry."
                        else -> detail?.takeIf { it.isNotBlank() } ?: "Provider returned HTTP $status."
                    }
                )
            }

            val json = JSONObject(body)
            if (provider == TORBOX && json.has("success") && !json.optBoolean("success", true)) {
                throw IllegalStateException(json.optString("detail").ifBlank { "TorBox rejected the connection." })
            }
            val user = if (provider == REAL_DEBRID) {
                json.optString("username").takeIf { it.isNotBlank() } ?: "account"
            } else {
                val data = json.optJSONObject("data") ?: json
                data.optString("email").takeIf { it.isNotBlank() }
                    ?: data.optString("username").takeIf { it.isNotBlank() }
                    ?: "account"
            }
            return "Connected successfully · $user"
        } finally {
            connection.disconnect()
        }
    }

    private fun storageKey(provider: String) = "token_$provider"

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        const val REAL_DEBRID = "real_debrid"
        const val TORBOX = "torbox"
    }
}
