package com.debridmediacenter.android

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class StremioCatalogRef(
    val addonId: String, val addonName: String, val id: String, val type: String,
    val name: String, val supportsSearch: Boolean
)
data class StremioCatalogTitle(
    val id: String, val type: String, val title: String, val year: String,
    val overview: String, val poster: String
)

/** Reads declared catalogs from installed manifests without changing saved add-on settings. */
class StremioCatalogClient(context: Context) {
    private val addons = StremioAddonStore(context.applicationContext)

    fun catalogs(): List<StremioCatalogRef> = addons.addons().flatMap { addon ->
        runCatching {
            val manifest = getJson(addon.manifestUrl)
            val rows = manifest.optJSONArray("catalogs") ?: JSONArray()
            (0 until rows.length()).mapNotNull { index ->
                val row = rows.optJSONObject(index) ?: return@mapNotNull null
                val id = row.optString("id")
                val type = row.optString("type")
                if (id.isBlank() || type.isBlank()) return@mapNotNull null
                val extra = row.optJSONArray("extra") ?: JSONArray()
                val searchable = (0 until extra.length()).any { extraIndex ->
                    val item = extra.opt(extraIndex)
                    (if (item is JSONObject) item.optString("name") else item?.toString()).equals("search", true)
                }
                StremioCatalogRef(addon.id, addon.name, id, type, row.optString("name").ifBlank { id }, searchable)
            }
        }.getOrDefault(emptyList())
    }.distinctBy { it.addonId + ":" + it.type + ":" + it.id }

    fun items(catalog: StremioCatalogRef, query: String = ""): List<StremioCatalogTitle> {
        val addon = addons.addons().firstOrNull { it.id == catalog.addonId } ?: return emptyList()
        val base = addon.manifestUrl.substringBefore("?").removeSuffix("/manifest.json").removeSuffix("/")
        val extra = if (query.isNotBlank() && catalog.supportsSearch) "/search=" + Uri.encode(query.trim()) else ""
        val endpoint = base + "/catalog/" + Uri.encode(catalog.type) + "/" + Uri.encode(catalog.id) + extra + ".json"
        val rows = getJson(endpoint).optJSONArray("metas") ?: JSONArray()
        return (0 until rows.length()).mapNotNull { index ->
            val row = rows.optJSONObject(index) ?: return@mapNotNull null
            val id = row.optString("imdb_id").ifBlank { row.optString("id") }
            val title = row.optString("name").ifBlank { row.optString("title") }
            if (id.isBlank() || title.isBlank()) return@mapNotNull null
            StremioCatalogTitle(id, row.optString("type").ifBlank { catalog.type }, title,
                row.optString("releaseInfo").ifBlank { row.optString("year") },
                row.optString("description").ifBlank { row.optString("overview") },
                row.optString("poster").takeIf { it.startsWith("http") }.orEmpty())
        }.distinctBy { it.id }.take(100)
    }

    fun test(addonId: String): String {
        val addon = addons.addons().firstOrNull { it.id == addonId } ?: error("Add-on is no longer installed.")
        val manifest = getJson(addon.manifestUrl)
        val catalogCount = manifest.optJSONArray("catalogs")?.length() ?: 0
        val resources = manifest.optJSONArray("resources") ?: JSONArray()
        val capabilities = (0 until resources.length()).mapNotNull { i ->
            val item = resources.opt(i)
            (if (item is JSONObject) item.optString("name") else item?.toString())?.takeIf { it.isNotBlank() }
        }.distinct()
        return addon.name + " is reachable · " + capabilities.joinToString(", ").ifBlank { "manifest valid" } + " · " + catalogCount + " catalog(s)"
    }

    private fun getJson(endpoint: String): JSONObject {
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10000
        connection.readTimeout = 15000
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "DebridMediaCenter/1.2")
        try {
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("Add-on returned HTTP " + code + ".")
            return JSONObject(body)
        } finally { connection.disconnect() }
    }
}
