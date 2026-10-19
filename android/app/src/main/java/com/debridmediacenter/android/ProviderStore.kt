package com.debridmediacenter.android

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.io.OutputStreamWriter
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores provider tokens encrypted with a non-exportable Android Keystore AES key.
 * The app preferences contain only Base64-encoded IV/ciphertext, never the token itself.
 */
data class ProviderLibraryItem(\n    val provider: String,\n    val id: String,\n    val title: String,\n    val status: String,\n    val progress: Int\n)\n\nclass ProviderStore(context: Context) {
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


    fun addMagnet(provider: String, magnet: String): String {
        val secret = readToken(provider)?.trim().orEmpty()
        require(secret.isNotEmpty()) { "Connect this provider in Services first." }
        val link = magnet.trim()
        require(link.startsWith("magnet:?xt=urn:btih:", ignoreCase = true)) {
            "Paste a valid magnet link starting with magnet:?xt=urn:btih:."
        }

        val connection: HttpURLConnection
        when (provider) {
            REAL_DEBRID -> {
                connection = (URL("https://api.real-debrid.com/rest/1.0/torrents/addMagnet").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 15000
                    readTimeout = 20000
                    setRequestProperty("Authorization", "Bearer $secret")
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    setRequestProperty("Accept", "application/json")
                }
                val body = "magnet=" + URLEncoder.encode(link, "UTF-8")
                OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(body) }
            }
            TORBOX -> {
                val boundary = "----DMCFormBoundary" + System.currentTimeMillis().toString(16)
                connection = (URL("https://api.torbox.app/v1/api/torrents/createtorrent").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 15000
                    readTimeout = 25000
                    setRequestProperty("Authorization", "Bearer $secret")
                    setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                    setRequestProperty("Accept", "application/json")
                }
                val payload = "--$boundary\r\nContent-Disposition: form-data; name=\"magnet\"\r\n\r\n$link\r\n--$boundary--\r\n"
                OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(payload) }
            }
            else -> error("Unknown provider.")
        }

        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val json = runCatching { JSONObject(body) }.getOrNull()
            if (status !in 200..299) {
                val detail = json?.optString("detail")?.takeIf { it.isNotBlank() }
                    ?: json?.optString("error")?.takeIf { it.isNotBlank() }
                throw IllegalStateException(detail ?: when (status) {
                    401, 403 -> "Authentication failed. Check the provider API key."
                    429 -> "Provider rate limit reached. Wait and try again."
                    else -> "Provider returned HTTP $status."
                })
            }
            if (provider == TORBOX && json?.has("success") == true && !json.optBoolean("success", false)) {
                throw IllegalStateException(json.optString("detail").ifBlank { "TorBox could not add this magnet." })
            }
            val id = json?.optString("id")?.takeIf { it.isNotBlank() && it != "null" }
                ?: json?.optJSONObject("data")?.optString("torrent_id")?.takeIf { it.isNotBlank() && it != "null" }
            return when {
                provider == TORBOX -> json?.optString("detail")?.takeIf { it.isNotBlank() } ?: "Magnet submitted to TorBox."
                id != null -> "Magnet added to Real-Debrid · ID $id"
                else -> "Magnet submitted successfully."
            }
        } finally {
            connection.disconnect()
        }
    }

    fun fetchLibrary(provider: String): List<ProviderLibraryItem> {
        val secret = readToken(provider)?.trim().orEmpty()
        require(secret.isNotEmpty()) { "Connect this provider in Services first." }
        val endpoint = when (provider) {
            REAL_DEBRID -> "https://api.real-debrid.com/rest/1.0/torrents"
            TORBOX -> "https://api.torbox.app/v1/api/torrents/mylist"
            else -> error("Unknown provider.")
        }
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("Authorization", "Bearer $secret")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val json = runCatching { JSONObject(body) }.getOrNull()
                throw IllegalStateException(json?.optString("detail")?.takeIf { it.isNotBlank() }
                    ?: "Could not load provider library (HTTP $status).")
            }
            val root = JSONObjectOrArray(body)
            val items = when (root) {
                is org.json.JSONArray -> root
                is JSONObject -> root.optJSONArray("data") ?: root.optJSONArray("torrents") ?: org.json.JSONArray()
                else -> org.json.JSONArray()
            }
            val result = mutableListOf<ProviderLibraryItem>()
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val title = item.optString("filename").takeIf { it.isNotBlank() && it != "null" }
                    ?: item.optString("name").takeIf { it.isNotBlank() && it != "null" }
                    ?: item.optString("title").takeIf { it.isNotBlank() && it != "null" }
                    ?: "Untitled torrent"
                val id = item.optString("id").takeIf { it.isNotBlank() && it != "null" }
                    ?: item.optString("torrent_id").takeIf { it.isNotBlank() && it != "null" }.orEmpty()
                val statusText = item.optString("status").takeIf { it.isNotBlank() && it != "null" } ?: "Unknown"
                val progress = item.optDouble("progress", 0.0).coerceIn(0.0, 100.0).toInt()
                result.add(ProviderLibraryItem(provider, id, title, statusText, progress))
            }
            return result
        } finally {
            connection.disconnect()
        }
    }

    private fun JSONObjectOrArray(body: String): Any {
        val trimmed = body.trim()
        return if (trimmed.startsWith("[")) org.json.JSONArray(trimmed) else JSONObject(trimmed)
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
