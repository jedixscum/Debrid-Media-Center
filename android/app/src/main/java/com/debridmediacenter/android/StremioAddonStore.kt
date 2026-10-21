package com.debridmediacenter.android

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Generic Stremio addon manifest + stream client. */
data class StremioAddon(
    val id: String, val name: String, val manifestUrl: String,
    val description: String = "", val resources: List<String> = emptyList()
)

data class StremioStream(
    val addonId: String,
    val name: String,
    val title: String,
    val url: String,
    val behaviorHints: JSONObject? = null
)

class StremioAddonStore(context: Context) {
    private val prefs = context.getSharedPreferences("stremio_addons", Context.MODE_PRIVATE)

    fun addons(): List<StremioAddon> = runCatching {
        val array = JSONArray(prefs.getString("addons", "[]"))
        (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            StremioAddon(
                id = o.optString("id"),
                name = o.optString("name").ifBlank { o.optString("id") },
                manifestUrl = o.optString("manifestUrl"),
                description = o.optString("description"),
                resources = o.optJSONArray("resources")?.let { arr -> (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() } }.orEmpty()
            ).takeIf { it.id.isNotBlank() && it.manifestUrl.isNotBlank() }
        }
    }.getOrDefault(emptyList())

    fun addManifest(manifestUrl: String): StremioAddon {
        val normalized = normalizeManifestUrl(manifestUrl)
        val json = getJson(normalized)
        val id = json.optString("id").ifBlank { throw IllegalArgumentException("The manifest has no addon ID.") }
        val addon = StremioAddon(
            id = id,
            name = json.optString("name").ifBlank { id },
            manifestUrl = normalized,
            description = json.optString("description"),
            resources = json.optJSONArray("resources")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    when (val resource = arr.opt(i)) {
                        is JSONObject -> resource.optString("name").takeIf { it.isNotBlank() }
                        is String -> resource.takeIf { it.isNotBlank() }
                        else -> null
                    }
                }
            }.orEmpty()
        )
        val current = addons().filterNot { it.id == addon.id }.toMutableList()
        current.add(addon)
        val array = JSONArray()
        current.forEach { a ->
            array.put(JSONObject().put("id", a.id).put("name", a.name).put("manifestUrl", a.manifestUrl).put("description", a.description).put("resources", JSONArray(a.resources)))
        }
        check(prefs.edit().putString("addons", array.toString()).commit()) { "Could not save the addon." }
        return addon
    }

    fun remove(id: String) {
        val array = JSONArray()
        addons().filterNot { it.id == id }.forEach { a ->
            array.put(JSONObject().put("id", a.id).put("name", a.name).put("manifestUrl", a.manifestUrl).put("description", a.description).put("resources", JSONArray(a.resources)))
        }
        prefs.edit().putString("addons", array.toString()).apply()
    }

    /** Query metadata-capable add-ons before the built-in Cinemeta fallback. */
    fun metaResponse(type: String, mediaId: String): JSONObject? {
        val safeType = if (type.lowercase() in listOf("series", "tv")) "series" else "movie"
        for (addon in addons()) {
            if (addon.resources.isNotEmpty() && addon.resources.none { it.equals("meta", ignoreCase = true) }) continue
            val base = addon.manifestUrl.substringBefore('?').removeSuffix("/manifest.json").removeSuffix("/")
            val response = runCatching { getJson(base + "/meta/" + safeType + "/" + Uri.encode(mediaId) + ".json") }.getOrNull()
            if (response != null && response.optJSONObject("meta") != null) return response
        }
        return null
    }

    fun streams(type: String, mediaId: String): List<StremioStream> {
        val safeType = when (type.lowercase()) { "movie" -> "movie"; "series", "tv" -> "series"; else -> type.lowercase() }
        return addons().flatMap { addon ->
            runCatching {
                val base = addon.manifestUrl
                    .substringBefore('?')
                    .removeSuffix("/manifest.json")
                    .removeSuffix("/")
                val endpoint = "$base/stream/$safeType/${Uri.encode(mediaId)}.json"
                val root = getJson(endpoint)
                val rows = root.optJSONArray("streams") ?: JSONArray()
                (0 until rows.length()).mapNotNull { i ->
                    val row = rows.optJSONObject(i) ?: return@mapNotNull null
                    val url = row.optString("url").takeIf { it.startsWith("http://") || it.startsWith("https://") } ?: return@mapNotNull null
                    StremioStream(
                        addonId = addon.id,
                        name = row.optString("name").ifBlank { addon.name },
                        title = row.optString("title").ifBlank { row.optString("name").ifBlank { "Stream" } },
                        url = url,
                        behaviorHints = row.optJSONObject("behaviorHints")
                    )
                }
            }.getOrDefault(emptyList())
        }
    }

    private fun normalizeManifestUrl(value: String): String {
        var url = value.trim()
        require(url.startsWith("https://") || url.startsWith("http://")) { "Addon URL must start with http:// or https://." }
        val clean = url.substringBefore('?').removeSuffix("/")
        return if (clean.endsWith("/manifest.json")) url else url.removeSuffix("/") + "/manifest.json"
    }

    private fun getJson(endpoint: String): JSONObject {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12000
            readTimeout = 18000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "DebridMediaCenter-Stremio/1.1")
        }
        try {
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IllegalStateException("Stremio addon returned HTTP $code.")
            return JSONObject(body)
        } finally { connection.disconnect() }
    }
}
