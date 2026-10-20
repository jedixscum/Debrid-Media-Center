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
data class ProviderLibraryItem(
    val provider: String,
    val id: String,
    val title: String,
    val status: String,
    val progress: Int
)

data class CacheCheckResult(
    val provider: String,
    val hash: String,
    val cached: Boolean,
    val title: String
)

data class MediaSearchResult(
    val id: String,
    val type: String,
    val title: String,
    val year: String,
    val overview: String,
    val posterUrl: String = ""
)

data class ReleaseSearchResult(
    val title: String,
    val hash: String,
    val sizeMb: Double
)

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
            val account = if (provider == REAL_DEBRID) json else (json.optJSONObject("data") ?: json)
            val user = account.optString("username").takeIf { it.isNotBlank() && it != "null" }
                ?: account.optString("email").takeIf { it.isNotBlank() && it != "null" }
                ?: "account"
            val expiryText = listOf("expiration", "premium_expires_at", "expires_at", "premium_expiration")
                .firstNotNullOfOrNull { key ->
                    account.optString(key).takeIf { it.isNotBlank() && it != "null" && it != "0" }
                }
            val premiumSummary = expiryText?.let { rawExpiry ->
                runCatching {
                    val expiry = runCatching { java.time.Instant.parse(rawExpiry) }
                        .getOrElse { java.time.OffsetDateTime.parse(rawExpiry).toInstant() }
                    val daysLeft = java.time.Duration.between(java.time.Instant.now(), expiry).toDays()
                    when {
                        daysLeft < 0 -> "Premium expired"
                        daysLeft == 0L -> "Premium expires in less than a day"
                        else -> "Premium · $daysLeft day${if (daysLeft == 1L) "" else "s"} left"
                    }
                }.getOrElse { "Premium expiry: $rawExpiry" }
            } ?: run {
                val plan = listOf("plan", "subscription", "type")
                    .firstNotNullOfOrNull { key ->
                        account.optString(key).takeIf { it.isNotBlank() && it != "null" && it != "0" }
                    }
                plan?.let { "Plan: $it · expiry not provided by API" }
                    ?: "Account details loaded · expiry not provided by API"
            }
            return "Connected · $user\n$premiumSummary"
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
            val selectionWarning = if (provider == REAL_DEBRID && id != null) {
                runCatching { selectAllRealDebridFiles(id, secret) }.exceptionOrNull()?.message
            } else null
            return when {
                provider == TORBOX -> json?.optString("detail")?.takeIf { it.isNotBlank() } ?: "Magnet submitted to TorBox."
                id != null && selectionWarning == null -> "Magnet added to Real-Debrid · ID $id · files selected"
                id != null -> "Magnet added to Real-Debrid · ID $id. Select files in Real-Debrid if playback is unavailable."
                else -> "Magnet submitted successfully."
            }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Checks a supplied torrent hash or magnet against the selected provider's cached inventory.
     * This does not add anything to the account.
     */

    private fun selectAllRealDebridFiles(itemId: String, secret: String) {
        val connection = (URL("https://api.real-debrid.com/rest/1.0/torrents/selectFiles/" +
            URLEncoder.encode(itemId, "UTF-8")).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 12000
            readTimeout = 15000
            setRequestProperty("Authorization", "Bearer $secret")
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            setRequestProperty("Accept", "application/json")
        }
        try {
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write("files=all") }
            val code = connection.responseCode
            if (code !in 200..299) {
                val stream = connection.errorStream
                val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                val detail = runCatching { JSONObject(body).optString("error") }.getOrNull()
                throw IllegalStateException(detail?.takeIf { it.isNotBlank() }
                    ?: "Real-Debrid accepted the magnet but file selection failed (HTTP $code).")
            }
        } finally { connection.disconnect() }
    }


    /**
     * Search release hashes using DMM when the user has a sponsor key.
     * When TorBox is connected and no DMM key is configured, fall back to
     * TorBox's public IMDb-based search API so TorBox-only users can discover releases.
     */
    fun searchReleases(imdbId: String, mediaType: String, quality: String = "best"): List<ReleaseSearchResult> {
        require(imdbId.matches(Regex("^tt\\d+$"))) { "Select a title with a valid IMDb ID first." }
        require(mediaType == "movie" || mediaType == "tv") { "Unsupported media type." }
        val apiKey = readToken(DMM)?.trim().orEmpty()
        if (apiKey.isEmpty()) {
            require(hasToken(TORBOX)) {
                "Connect TorBox to search releases automatically, or add a DMM sponsor API key in Settings to use DMM release search."
            }
            return searchTorBoxReleases(imdbId, quality)
        }
        val connection = (URL("https://debridmediamanager.com/api/zurg/search-torrents").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15000
            readTimeout = 25000
            setRequestProperty("x-api-key", apiKey)
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val payload = JSONObject()
                .put("imdbId", imdbId)
                .put("mediaType", mediaType)
                .put("quality", quality)
                .put("limit", 30)
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val root = runCatching { JSONObject(body) }.getOrNull()
            if (code !in 200..299) {
                val detail = root?.optString("error")?.takeIf { it.isNotBlank() && it != "null" }
                throw IllegalStateException(detail ?: when (code) {
                    401, 403 -> "DMM rejected the key. An active sponsor API key is required for release search."
                    429 -> "DMM release search is rate-limited. Wait and retry."
                    else -> "Release search failed (HTTP $code)."
                })
            }
            val results = mutableListOf<ReleaseSearchResult>()
            fun addRows(rows: org.json.JSONArray?) {
                if (rows == null) return
                for (i in 0 until rows.length()) {
                    val row = rows.optJSONObject(i) ?: continue
                    val title = row.optString("title").takeIf { it.isNotBlank() && it != "null" } ?: continue
                    val hash = row.optString("hash").lowercase().takeIf { it.matches(Regex("^[a-f0-9]{40}$")) } ?: continue
                    val size = row.optDouble("fileSize", 0.0)
                    results.add(ReleaseSearchResult(title, hash, size))
                }
            }
            if (mediaType == "movie") {
                addRows(root?.optJSONArray("results"))
            } else {
                val seasons = root?.optJSONObject("seasons")
                if (seasons != null) {
                    val keys = seasons.keys()
                    while (keys.hasNext()) addRows(seasons.optJSONArray(keys.next()))
                }
            }
            return results.distinctBy { it.hash }.sortedByDescending { it.sizeMb }
        } finally {
            connection.disconnect()
        }
    }

    private fun searchTorBoxReleases(imdbId: String, quality: String): List<ReleaseSearchResult> {
        val endpoint = URL("https://search-api.torbox.app/torrents/imdb:$imdbId")
        val connection = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 25000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "DebridMediaCenter/0.6")
        }
        try {
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IllegalStateException("TorBox release search failed (HTTP $code).")
            val root = runCatching { JSONObject(body) }.getOrNull()
                ?: throw IllegalStateException("TorBox returned an unreadable release-search response.")
            if (root.has("success") && !root.optBoolean("success", true)) {
                throw IllegalStateException(root.optString("detail").ifBlank { "TorBox could not find releases for this title." })
            }
            val results = mutableListOf<ReleaseSearchResult>()
            fun scan(value: Any?) {
                when (value) {
                    is org.json.JSONArray -> for (i in 0 until value.length()) scan(value.opt(i))
                    is JSONObject -> {
                        val title = sequenceOf("title", "name", "release", "filename")
                            .map { value.optString(it) }.firstOrNull { it.isNotBlank() && it != "null" }.orEmpty()
                        val rawHash = sequenceOf("hash", "infoHash", "info_hash", "btih", "magnet")
                            .map { value.optString(it) }.firstOrNull { it.isNotBlank() && it != "null" }.orEmpty()
                        val hash = Regex("(?i)(?:btih:)?([a-f0-9]{40})").find(rawHash)?.groupValues?.get(1)?.lowercase()
                        if (title.isNotBlank() && hash != null) {
                            val rawSize = sequenceOf("size", "sizeBytes", "size_bytes", "fileSize", "filesize")
                                .map { value.optDouble(it, Double.NaN) }.firstOrNull { it.isFinite() && it > 0 } ?: 0.0
                            val sizeMb = if (rawSize > 1024 * 1024) rawSize / (1024.0 * 1024.0) else rawSize
                            results.add(ReleaseSearchResult(title, hash, sizeMb))
                        }
                        val keys = value.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            val child = value.opt(key)
                            if (child is JSONObject || child is org.json.JSONArray) scan(child)
                        }
                    }
                }
            }
            scan(root)
            val unique = results.distinctBy { it.hash }
            val filtered = if (quality == "best") unique else unique.filter { release ->
                release.title.contains(quality, ignoreCase = true) ||
                    (quality == "2160p" && release.title.contains("4k", ignoreCase = true))
            }
            return (filtered.ifEmpty { if (quality == "best") unique else emptyList() })
                .sortedByDescending { it.sizeMb }
        } finally {
            connection.disconnect()
        }
    }

    fun checkCached(provider: String, input: String): CacheCheckResult {
        val secret = readToken(provider)?.trim().orEmpty()
        require(secret.isNotEmpty()) { "Connect this provider in Services first." }
        val raw = input.trim()
        val hash = Regex("(?i)(?:btih:)?([a-f0-9]{40})").find(raw)?.groupValues?.get(1)?.lowercase()
            ?: throw IllegalArgumentException("Enter a 40-character hexadecimal info hash or a magnet link containing one.")

        if (provider == REAL_DEBRID) {
            throw IllegalStateException("Real-Debrid no longer exposes a reliable instant-availability check here. You can still add the magnet directly; use TorBox for the supported cache check.")
        }
        val endpoint = when (provider) {
            REAL_DEBRID -> "https://api.real-debrid.com/rest/1.0/torrents/instantAvailability/$hash"
            TORBOX -> "https://api.torbox.app/v1/api/torrents/checkcached?hash=$hash&format=object&list_files=false"
            else -> error("Unknown provider.")
        }
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12000
            readTimeout = 15000
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
                    ?: when (status) {
                        401, 403 -> "Authentication failed. Check the provider API key."
                        429 -> "Provider rate limit reached. Wait and try again."
                        else -> "Cache check failed (HTTP $status)."
                    })
            }
            val json = JSONObject(body)
            val cached: Boolean
            val title: String
            if (provider == REAL_DEBRID) {
                val item = json.optJSONObject(hash) ?: json.optJSONObject(hash.uppercase())
                val variants = item?.optJSONArray("rd")
                cached = variants != null && variants.length() > 0
                title = if (cached) "Cached on Real-Debrid" else "Not found in Real-Debrid cache"
            } else {
                if (json.has("success") && !json.optBoolean("success", false)) {
                    throw IllegalStateException(json.optString("detail").ifBlank { "TorBox could not check cache availability." })
                }
                val data = json.optJSONObject("data")
                val entry = data?.optJSONObject(hash) ?: data?.optJSONObject(hash.uppercase())
                cached = entry != null
                title = entry?.optString("name")?.takeIf { it.isNotBlank() && it != "null" }
                    ?: if (cached) "Cached on TorBox" else "Not found in TorBox cache"
            }
            return CacheCheckResult(provider, hash, cached, title)
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Searches real movie and show titles using Debrid Media Manager's public
     * Trakt search proxy. This searches titles, not torrent/release sources.
     */
    fun searchTitles(query: String): List<MediaSearchResult> {
        val term = query.trim()
        require(term.length >= 2) { "Enter at least two characters to search." }
        val endpoint = URL("https://debridmediamanager.com/api/trakt/search?query=" +
            URLEncoder.encode(term, "UTF-8") + "&types=movie,show")
        val connection = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12000
            readTimeout = 18000
            setRequestProperty("Accept", "application/json")
        }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                throw IllegalStateException(when (status) {
                    429 -> "Title search is being rate-limited. Wait a moment and retry."
                    else -> "Title search failed (HTTP $status)."
                })
            }
            val array = org.json.JSONArray(body)
            val results = mutableListOf<MediaSearchResult>()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val type = item.optString("type").ifBlank { "movie" }
                val media = item.optJSONObject(type) ?: continue
                val title = media.optString("title").takeIf { it.isNotBlank() } ?: continue
                val ids = media.optJSONObject("ids")
                val id = ids?.optString("imdb")?.takeIf { it.startsWith("tt") }
                    ?: ids?.optString("trakt")?.takeIf { it.isNotBlank() }
                    ?: "$type-$i"
                results.add(MediaSearchResult(
                    id = id,
                    type = type,
                    title = title,
                    year = media.optInt("year", 0).takeIf { it > 0 }?.toString() ?: "",
                    overview = media.optString("overview").takeIf { it.isNotBlank() && it != "null" }.orEmpty(),
                    posterUrl = (ids?.optString("imdb")?.takeIf { it.startsWith("tt") } ?: "").let { imdbId ->
                        if (imdbId.isNotBlank()) "https://images.metahub.space/poster/medium/$imdbId/img" else ""
                    }
                ))
            }
            return results
        } finally {
            connection.disconnect()
        }
    }


    /**
     * Resolves a playable video URL from a completed item in the user's own provider library.
     * URLs are obtained on demand and are not persisted.
     */
    fun resolvePlaybackUrl(provider: String, itemId: String): String {
        val secret = readToken(provider)?.trim().orEmpty()
        require(secret.isNotEmpty()) { "Connect this provider in Services first." }
        require(itemId.isNotBlank()) { "This library item has no provider ID." }

        return when (provider) {
            REAL_DEBRID -> {
                val infoConnection = (URL("https://api.real-debrid.com/rest/1.0/torrents/info/" +
                    URLEncoder.encode(itemId, "UTF-8")).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 12000
                    readTimeout = 18000
                    setRequestProperty("Authorization", "Bearer $secret")
                    setRequestProperty("Accept", "application/json")
                }
                val info = try {
                    val code = infoConnection.responseCode
                    val stream = if (code in 200..299) infoConnection.inputStream else infoConnection.errorStream
                    val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                    if (code !in 200..299) throw IllegalStateException("Could not inspect Real-Debrid item (HTTP $code).")
                    JSONObject(body)
                } finally { infoConnection.disconnect() }

                val status = info.optString("status")
                if (status != "downloaded") {
                    throw IllegalStateException("This item is not ready to play yet (status: $status).")
                }
                val files = info.optJSONArray("files") ?: org.json.JSONArray()
                val links = info.optJSONArray("links") ?: org.json.JSONArray()
                val videoExtensions = listOf(".mkv", ".mp4", ".m4v", ".avi", ".mov", ".webm", ".ts")
                var selectedLink: String? = null
                var selectedFileIndex = 0
                for (i in 0 until files.length()) {
                    val file = files.optJSONObject(i) ?: continue
                    if (file.optInt("selected", 0) != 1) continue
                    val path = file.optString("path").lowercase()
                    val linkForFile = if (selectedFileIndex < links.length()) links.optString(selectedFileIndex) else ""
                    selectedFileIndex++
                    if (videoExtensions.any { path.endsWith(it) } && linkForFile.startsWith("http")) {
                        selectedLink = linkForFile
                        break
                    }
                }
                if (selectedLink == null) {
                    for (i in 0 until links.length()) {
                        val link = links.optString(i)
                        if (link.startsWith("http")) { selectedLink = link; break }
                    }
                }
                val link = selectedLink ?: throw IllegalStateException(
                    "No selected playable files were returned. Open Real-Debrid and select the video file first."
                )
                val unrestrict = (URL("https://api.real-debrid.com/rest/1.0/unrestrict/link").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 12000
                    readTimeout = 20000
                    setRequestProperty("Authorization", "Bearer $secret")
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    setRequestProperty("Accept", "application/json")
                }
                try {
                    val body = "link=" + URLEncoder.encode(link, "UTF-8")
                    OutputStreamWriter(unrestrict.outputStream, Charsets.UTF_8).use { it.write(body) }
                    val code = unrestrict.responseCode
                    val stream = if (code in 200..299) unrestrict.inputStream else unrestrict.errorStream
                    val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                    if (code !in 200..299) throw IllegalStateException("Could not resolve Real-Debrid playback link (HTTP $code).")
                    val download = JSONObject(response).optString("download")
                    if (!download.startsWith("https://") && !download.startsWith("http://")) {
                        throw IllegalStateException("Real-Debrid did not return a playable link.")
                    }
                    download
                } finally { unrestrict.disconnect() }
            }
            TORBOX -> {
                val listConnection = (URL("https://api.torbox.app/v1/api/torrents/mylist?id=" +
                    URLEncoder.encode(itemId, "UTF-8")).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 12000
                    readTimeout = 18000
                    setRequestProperty("Authorization", "Bearer $secret")
                    setRequestProperty("Accept", "application/json")
                }
                val item = try {
                    val code = listConnection.responseCode
                    val stream = if (code in 200..299) listConnection.inputStream else listConnection.errorStream
                    val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                    if (code !in 200..299) throw IllegalStateException("Could not inspect TorBox item (HTTP $code).")
                    val root = JSONObject(body)
                    if (root.has("success") && !root.optBoolean("success", false)) {
                        throw IllegalStateException(root.optString("detail").ifBlank { "TorBox could not inspect this item." })
                    }
                    val data = root.opt("data")
                    when (data) {
                        is JSONObject -> data
                        is org.json.JSONArray -> data.optJSONObject(0)
                        else -> null
                    } ?: throw IllegalStateException("TorBox returned no details for this item.")
                } finally { listConnection.disconnect() }

                val files = item.optJSONArray("files") ?: org.json.JSONArray()
                val videoExtensions = listOf(".mkv", ".mp4", ".m4v", ".avi", ".mov", ".webm", ".ts")
                var fileId: String? = null
                for (i in 0 until files.length()) {
                    val file = files.optJSONObject(i) ?: continue
                    val name = file.optString("name").ifBlank { file.optString("short_name") }.lowercase()
                    if (videoExtensions.any { name.endsWith(it) }) {
                        fileId = file.opt("id")?.toString()?.takeIf { it.isNotBlank() && it != "null" }
                        if (fileId != null) break
                    }
                }
                val selectedFile = fileId ?: throw IllegalStateException(
                    "No video file was found. The torrent may still be processing or may contain unsupported files."
                )
                val endpoint = "https://api.torbox.app/v1/api/torrents/requestdl?token=" +
                    URLEncoder.encode(secret, "UTF-8") + "&torrent_id=" +
                    URLEncoder.encode(itemId, "UTF-8") + "&file_id=" +
                    URLEncoder.encode(selectedFile, "UTF-8")
                val dlConnection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 12000
                    readTimeout = 20000
                    setRequestProperty("Authorization", "Bearer $secret")
                    setRequestProperty("Accept", "application/json")
                }
                try {
                    val code = dlConnection.responseCode
                    val stream = if (code in 200..299) dlConnection.inputStream else dlConnection.errorStream
                    val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                    if (code !in 200..299) throw IllegalStateException("Could not request TorBox playback link (HTTP $code).")
                    val root = JSONObject(body)
                    if (!root.optBoolean("success", false)) {
                        throw IllegalStateException(root.optString("detail").ifBlank { "TorBox could not create a playback link." })
                    }
                    root.optString("data").takeIf { it.startsWith("https://") || it.startsWith("http://") }
                        ?: throw IllegalStateException("TorBox did not return a playable URL.")
                } finally { dlConnection.disconnect() }
            }
            else -> throw IllegalArgumentException("Unknown provider.")
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
                val statusText = item.optString("status").takeIf { it.isNotBlank() && it != "null" }
                    ?: item.optString("download_state").takeIf { it.isNotBlank() && it != "null" }
                    ?: "Unknown"
                val rawProgress = item.optDouble("progress", 0.0)
                val normalizedProgress = if (provider == TORBOX && rawProgress in 0.0..1.0) rawProgress * 100.0 else rawProgress
                val progress = normalizedProgress.coerceIn(0.0, 100.0).toInt()
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
        const val DMM = "dmm"
    }
}
