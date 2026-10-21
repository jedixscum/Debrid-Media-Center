package com.debridmediacenter.android

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class V2SavedMedia(
    val id: String,
    val type: String,
    val title: String,
    val year: String,
    val overview: String,
    val posterUrl: String,
    val savedAt: Long = System.currentTimeMillis()
)

/** Local-only watchlist, recently opened titles, and playback resume checkpoints. */
class V2MediaLibrary(context: Context) {
    private val prefs = context.getSharedPreferences("dmc_media_library", Context.MODE_PRIVATE)

    fun watchlist(): List<V2SavedMedia> = readList("watchlist")
    fun recentlyViewed(): List<V2SavedMedia> = readList("recent")

    fun isInWatchlist(id: String): Boolean = watchlist().any { it.id == id }

    fun toggleWatchlist(media: V2SavedMedia): Boolean {
        val rows = watchlist().toMutableList()
        val added = rows.none { it.id == media.id }
        if (added) rows.add(0, media) else rows.removeAll { it.id == media.id }
        writeList("watchlist", rows.take(300))
        return added
    }

    fun removeFromWatchlist(id: String) {
        writeList("watchlist", watchlist().filterNot { it.id == id })
    }

    fun markRecent(media: V2SavedMedia) {
        val rows = (listOf(media.copy(savedAt = System.currentTimeMillis())) + recentlyViewed().filterNot { it.id == media.id })
            .take(80)
        writeList("recent", rows)
    }

    fun savePosition(streamUrl: String, positionMs: Long) {
        if (streamUrl.isBlank()) return
        val key = "position_" + streamUrl.hashCode().toUInt().toString(16)
        if (positionMs < 10_000L) prefs.edit().remove(key).apply()
        else prefs.edit().putLong(key, positionMs).apply()
    }

    fun position(streamUrl: String): Long {
        val key = "position_" + streamUrl.hashCode().toUInt().toString(16)
        return prefs.getLong(key, 0L)
    }

    private fun readList(key: String): List<V2SavedMedia> = runCatching {
        val array = JSONArray(prefs.getString(key, "[]"))
        (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id")
            val title = o.optString("title")
            if (id.isBlank() || title.isBlank()) return@mapNotNull null
            V2SavedMedia(id, o.optString("type", "movie"), title, o.optString("year"), o.optString("overview"), o.optString("posterUrl"), o.optLong("savedAt"))
        }.sortedByDescending { it.savedAt }
    }.getOrDefault(emptyList())

    private fun writeList(key: String, rows: List<V2SavedMedia>) {
        val array = JSONArray()
        rows.forEach { row ->
            array.put(JSONObject()
                .put("id", row.id)
                .put("type", row.type)
                .put("title", row.title)
                .put("year", row.year)
                .put("overview", row.overview)
                .put("posterUrl", row.posterUrl)
                .put("savedAt", row.savedAt))
        }
        prefs.edit().putString(key, array.toString()).apply()
    }
}
