package com.debridmediacenter.android

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class WatchHistoryEntry(
    val item: MediaSearchResult,
    val encryptedUrl: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long
)

class MediaCollectionStore(context: Context) {
    private val prefs = context.getSharedPreferences("media_collection", Context.MODE_PRIVATE)
    private val keyAlias = "debrid_media_center_history_key"

    fun watchlist(): List<MediaSearchResult> = readItems("watchlist")
    fun history(): List<WatchHistoryEntry> = readHistory()
    fun isSaved(item: MediaSearchResult): Boolean = watchlist().any { it.id == item.id && it.type == item.type }

    @Synchronized
    fun toggleWatchlist(item: MediaSearchResult): Boolean {
        val entries = watchlist().toMutableList()
        val existing = entries.indexOfFirst { it.id == item.id && it.type == item.type }
        val saved: Boolean
        if (existing >= 0) {
            entries.removeAt(existing)
            saved = false
        } else {
            entries.add(0, item)
            saved = true
        }
        prefs.edit().putString("watchlist", JSONArray().apply {
            entries.take(200).forEach { put(encodeItem(it)) }
        }.toString()).apply()
        return saved
    }

    @Synchronized
    fun saveProgress(item: MediaSearchResult, url: String, positionMs: Long, durationMs: Long) {
        val entries = readHistory().toMutableList()
        val existing = entries.indexOfFirst { it.item.id == item.id && it.item.type == item.type }
        val old = if (existing >= 0) entries.removeAt(existing) else null
        val encryptedUrl = runCatching { encrypt(url) }.getOrElse { old?.encryptedUrl.orEmpty() }
        entries.add(
            0,
            WatchHistoryEntry(
                item = item,
                encryptedUrl = encryptedUrl,
                positionMs = positionMs.coerceAtLeast(0),
                durationMs = durationMs.coerceAtLeast(0),
                updatedAt = System.currentTimeMillis()
            )
        )
        prefs.edit().putString("history", JSONArray().apply {
            entries.take(100).forEach { entry ->
                put(JSONObject()
                    .put("item", encodeItem(entry.item))
                    .put("url", entry.encryptedUrl)
                    .put("position", entry.positionMs)
                    .put("duration", entry.durationMs)
                    .put("updated", entry.updatedAt))
            }
        }.toString()).apply()
    }

    fun continueWatching(): List<WatchHistoryEntry> = history().filter {
        it.positionMs > 15_000L && (it.durationMs <= 0L || it.positionMs < it.durationMs - 45_000L)
    }

    fun playbackUrl(entry: WatchHistoryEntry): String? = runCatching { decrypt(entry.encryptedUrl) }.getOrNull()

    @Synchronized
    fun removeHistory(item: MediaSearchResult) {
        val entries = history().filterNot { it.item.id == item.id && it.item.type == item.type }
        writeHistory(entries)
    }

    @Synchronized
    fun clearHistory() = prefs.edit().remove("history").apply()

    private fun readItems(key: String): List<MediaSearchResult> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { decodeItem(array.optJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    private fun readHistory(): List<WatchHistoryEntry> {
        val raw = prefs.getString("history", null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val row = array.optJSONObject(index) ?: return@mapNotNull null
                val item = decodeItem(row.optJSONObject("item")) ?: return@mapNotNull null
                WatchHistoryEntry(
                    item = item,
                    encryptedUrl = row.optString("url"),
                    positionMs = row.optLong("position", 0L),
                    durationMs = row.optLong("duration", 0L),
                    updatedAt = row.optLong("updated", 0L)
                )
            }.sortedByDescending { it.updatedAt }
        }.getOrDefault(emptyList())
    }

    private fun writeHistory(entries: List<WatchHistoryEntry>) {
        prefs.edit().putString("history", JSONArray().apply {
            entries.take(100).forEach { entry ->
                put(JSONObject().put("item", encodeItem(entry.item)).put("url", entry.encryptedUrl)
                    .put("position", entry.positionMs).put("duration", entry.durationMs).put("updated", entry.updatedAt))
            }
        }.toString()).apply()
    }

    private fun encodeItem(item: MediaSearchResult) = JSONObject()
        .put("id", item.id).put("type", item.type).put("title", item.title)
        .put("year", item.year).put("overview", item.overview).put("poster", item.posterUrl)

    private fun decodeItem(row: JSONObject?): MediaSearchResult? {
        row ?: return null
        val id = row.optString("id")
        val title = row.optString("title")
        if (id.isBlank() || title.isBlank()) return null
        return MediaSearchResult(id, row.optString("type", "movie"), title, row.optString("year"),
            row.optString("overview"), row.optString("poster"))
    }

    private fun encrypt(value: String): String {
        require(value.isNotBlank()) { "Playback URL is empty." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val packed = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(packed, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        require(value.isNotBlank()) { "No saved playback link is available." }
        val packed = Base64.decode(value, Base64.NO_WRAP)
        require(packed.size > 12) { "Saved playback link is invalid." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, packed.copyOfRange(0, 12)))
        return String(cipher.doFinal(packed.copyOfRange(12, packed.size)), Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build())
        return generator.generateKey()
    }
}
