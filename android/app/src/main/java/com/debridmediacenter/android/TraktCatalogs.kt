package com.debridmediacenter.android

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private data class MdbSection(val name: String, val listUrl: String, val kind: String, val sort: String)
private data class MdbRow(val title: String, val type: String, val year: String, val overview: String, val poster: String, val id: String, val rating: Double, val votes: Int, val added: String)

private class MdbSectionStore(context: Context) {
    private val prefs = context.getSharedPreferences("dmc_mdblist_sections", Context.MODE_PRIVATE)
    fun apiKey() = prefs.getString("api_key", "").orEmpty()
    fun setApiKey(value: String) { prefs.edit().putString("api_key", value.trim()).apply() }
    fun sections(): List<MdbSection> {
        val a = runCatching { JSONArray(prefs.getString("sections", "[]")) }.getOrDefault(JSONArray())
        return (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { MdbSection(it.optString("name"), it.optString("url"), it.optString("kind", "all"), it.optString("sort", "recent")) } }
    }
    fun save(sections: List<MdbSection>) {
        val a = JSONArray()
        sections.forEach { a.put(JSONObject().put("name", it.name).put("url", it.listUrl).put("kind", it.kind).put("sort", it.sort)) }
        prefs.edit().putString("sections", a.toString()).apply()
    }
}

private fun jsonText(obj: JSONObject, vararg keys: String): String {
    for (key in keys) {
        val value = obj.opt(key)
        if (value is String && value.isNotBlank() && value != "null") return value
        if (value is Number && value.toString().isNotBlank()) return value.toString()
    }
    return ""
}

private fun fetchMdbSection(section: MdbSection, apiKey: String): List<MdbRow> {
    require(apiKey.isNotBlank()) { "Add your MDBList API key first. Find it in MDBList Preferences." }
    val uri = Uri.parse(section.listUrl.trim())
    require(uri.scheme == "https" && (uri.host.equals("mdblist.com", true) || uri.host.equals("www.mdblist.com", true))) {
        "Use an MDBList URL such as https://mdblist.com/lists/USERNAME/LIST-SLUG"
    }
    val parts = uri.pathSegments
    require(parts.size >= 3 && parts[0] == "lists") { "Expected an MDBList list URL: https://mdblist.com/lists/USERNAME/LIST-SLUG" }
    val user = parts[1]
    val slug = parts[2]
    val endpoint = "https://api.mdblist.com/lists/${Uri.encode(user)}/${Uri.encode(slug)}/items?apikey=${Uri.encode(apiKey)}&limit=100&offset=0&append_to_response=genres,poster,description,ratings"
    val connection = URL(endpoint).openConnection() as HttpURLConnection
    connection.connectTimeout = 12000
    connection.readTimeout = 20000
    connection.setRequestProperty("Accept", "application/json")
    try {
        val code = connection.responseCode
        val body = (if (code in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) {
            val hint = when (code) {
                401, 403 -> "Check your MDBList API key and whether this list is accessible."
                404 -> "List not found. Check the MDBList username and list slug in the URL."
                429 -> "MDBList rate limit reached. Wait a little and try again."
                else -> "MDBList returned HTTP $code."
            }
            error(hint)
        }
        val root = runCatching { JSONObject(body) }.getOrNull()
        val array = runCatching { JSONArray(body) }.getOrNull()
            ?: root?.optJSONArray("items")
            ?: root?.optJSONArray("results")
            ?: root?.optJSONArray("data")
            ?: JSONArray()
        val rows = (0 until array.length()).mapNotNull { index ->
            val entry = array.optJSONObject(index) ?: return@mapNotNull null
            val nested = entry.optJSONObject("movie") ?: entry.optJSONObject("show")
            val media = nested ?: entry
            val mediaType = jsonText(entry, "mediatype", "media_type", "type").ifBlank {
                if (entry.has("show") || jsonText(media, "tvdbid", "tvdb_id").isNotBlank()) "show" else "movie"
            }.lowercase()
            val type = if (mediaType in listOf("show", "series", "tv", "tvshow", "tv_show")) "series" else "movie"
            if (section.kind == "movies" && type != "movie" || section.kind == "series" && type != "series") return@mapNotNull null
            val ids = media.optJSONObject("ids") ?: entry.optJSONObject("ids") ?: JSONObject()
            val imdb = jsonText(media, "imdbid", "imdb_id").ifBlank { jsonText(ids, "imdb") }
            val tmdb = jsonText(media, "tmdbid", "tmdb_id").ifBlank { jsonText(ids, "tmdb") }
            val mdblistId = jsonText(media, "id").ifBlank { jsonText(ids, "mdblist") }
            val id = when {
                imdb.isNotBlank() -> if (imdb.startsWith("tt")) imdb else "tt$imdb"
                tmdb.isNotBlank() -> tmdb
                mdblistId.isNotBlank() -> mdblistId
                else -> jsonText(media, "title", "name")
            }
            val posterObj = media.optJSONObject("poster") ?: entry.optJSONObject("poster")
            val poster = jsonText(media, "poster", "poster_url", "poster_path").ifBlank {
                jsonText(posterObj ?: JSONObject(), "url", "medium", "large")
            }.ifBlank {
                val posterId = if (imdb.isNotBlank()) (if (imdb.startsWith("tt")) imdb else "tt$imdb") else tmdb
                if (posterId.isNotBlank()) "https://images.metahub.space/poster/medium/$posterId/img" else ""
            }
            val ratings = media.optJSONObject("ratings") ?: entry.optJSONObject("ratings") ?: JSONObject()
            val imdbRating = ratings.optJSONObject("imdb")?.optDouble("rating", 0.0) ?: ratings.optDouble("imdb", 0.0)
            val rating = media.optDouble("score", media.optDouble("rating", imdbRating))
            val votes = media.optInt("imdbvotes", media.optInt("votes", ratings.optJSONObject("imdb")?.optInt("votes", 0) ?: 0))
            MdbRow(
                jsonText(media, "title", "name").ifBlank { "Untitled" },
                type,
                jsonText(media, "year", "release_year"),
                jsonText(media, "description", "overview", "plot"),
                poster,
                id,
                rating,
                votes,
                jsonText(entry, "added", "added_at", "created_at")
            )
        }
        return when (section.sort) {
            "popular" -> rows.sortedWith(compareByDescending<MdbRow> { it.votes }.thenByDescending { it.rating })
            "rating" -> rows.sortedByDescending { it.rating }
            "year" -> rows.sortedByDescending { it.year.toIntOrNull() ?: 0 }
            else -> rows.sortedByDescending { it.added }
        }.take(100)
    } finally {
        connection.disconnect()
    }
}

@Composable
internal fun V2MdbCatalogManager(onSelect: (V2Title) -> Unit) {
    val context = LocalContext.current
    val store = remember { MdbSectionStore(context.applicationContext) }
    var apiKey by remember { mutableStateOf(store.apiKey()) }
    var sections by remember { mutableStateOf(store.sections()) }
    var showEditor by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("all") }
    var sort by remember { mutableStateOf("recent") }
    var active by remember { mutableStateOf<MdbSection?>(null) }
    var rows by remember { mutableStateOf(emptyList<MdbRow>()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Your MDBList catalogs", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Create sections from live-updating MDBList lists.", color = Color(0xFF91A5AE), fontSize = 11.sp)
            }
            IconButton(onClick = { showEditor = !showEditor; error = "" }) {
                Icon(if (showEditor) Icons.Default.Close else Icons.Default.AddCircle, "Add MDBList catalog", tint = Color(0xFF58E1CF))
            }
        }
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it; store.setApiKey(it) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("MDBList API key") },
            supportingText = { Text("Get your key from mdblist.com Preferences. It is saved on this device.") }
        )
        if (showEditor) {
            OutlinedTextField(value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Section name (e.g. Netflix)") })
            OutlinedTextField(value = url, onValueChange = { url = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("MDBList list URL") }, placeholder = { Text("https://mdblist.com/lists/username/list-name") })
            Text("Content", color = Color(0xFF91A5AE), fontSize = 11.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("all" to "Movies + TV", "movies" to "Movies", "series" to "TV shows").forEach { (value, label) ->
                    FilterChip(selected = kind == value, onClick = { kind = value }, label = { Text(label, fontSize = 11.sp) })
                }
            }
            Text("Sort order", color = Color(0xFF91A5AE), fontSize = 11.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("recent" to "Recently added", "popular" to "Most popular", "rating" to "Top rated", "year" to "Newest year").forEach { (value, label) ->
                    FilterChip(selected = sort == value, onClick = { sort = value }, label = { Text(label, fontSize = 10.sp) })
                }
            }
            Button(onClick = {
                val cleanName = name.trim()
                if (cleanName.isBlank() || url.isBlank()) {
                    error = "Enter a section name and MDBList URL."
                } else {
                    val entry = MdbSection(cleanName, url.trim(), kind, sort)
                    sections = sections.filterNot { it.name.equals(cleanName, true) } + entry
                    store.save(sections)
                    active = entry
                    showEditor = false
                    busy = true
                    error = ""
                }
            }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(6.dp))
                Text("Save section and load list")
            }
        }
        if (sections.isNotEmpty()) {
            Text("YOUR SECTIONS", color = Color(0xFF58E1CF), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
                items(sections, key = { it.name + it.listUrl }) { section ->
                    Card(onClick = { active = section; busy = true; error = "" }, colors = CardDefaults.cardColors(if (active == section) Color(0xFF21434A) else Color(0xFF14232C)), shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.width(112.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.LiveTv, null, tint = Color(0xFF58E1CF), modifier = Modifier.size(24.dp))
                            Text(section.name, fontWeight = FontWeight.Bold, maxLines = 2, minLines = 2)
                            Text(if (section.kind == "all") "Movies & TV" else if (section.kind == "movies") "Movies" else "TV shows", color = Color(0xFF91A5AE), fontSize = 10.sp)
                            Row {
                                IconButton(onClick = { name = section.name; url = section.listUrl; kind = section.kind; sort = section.sort; showEditor = true }) {
                                    Icon(Icons.Default.Edit, "Edit section", tint = Color(0xFF58E1CF), modifier = Modifier.size(17.dp))
                                }
                                IconButton(onClick = {
                                    sections = sections.filterNot { it == section }
                                    store.save(sections)
                                    if (active == section) { active = null; rows = emptyList() }
                                }) { Icon(Icons.Default.DeleteOutline, "Delete section", tint = Color(0xFF91A5AE), modifier = Modifier.size(17.dp)) }
                            }
                        }
                    }
                }
            }
        }
        LaunchedEffect(active, busy) {
            val chosen = active
            if (busy && chosen != null) {
                val result = withContext(Dispatchers.IO) { runCatching { fetchMdbSection(chosen, apiKey.trim()) } }
                result.onSuccess { rows = it; error = if (it.isEmpty()) "This list returned no titles (or its response format was not recognized)." else "" }
                    .onFailure { rows = emptyList(); error = it.message ?: "Could not load MDBList." }
                busy = false
            }
        }
        if (active != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(active!!.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { busy = true }) { Icon(Icons.Default.Refresh, "Refresh MDBList", tint = Color(0xFF58E1CF)) }
                IconButton(onClick = { active = null; rows = emptyList(); error = "" }) { Icon(Icons.Default.Close, "Close list", tint = Color(0xFF91A5AE)) }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (error.isNotBlank()) Text(error, color = Color(0xFFFFB4AB), fontSize = 12.sp)
            rows.forEach { row ->
                val title = V2Title(row.id, row.type, row.title, row.year, row.overview, row.poster)
                Card(onClick = { onSelect(title) }, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color(0xFF101922)), shape = RoundedCornerShape(13.dp)) {
                    Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        androidx.compose.foundation.Image(painter = rememberAsyncImagePainter(row.poster), contentDescription = null, modifier = Modifier.width(44.dp).height(64.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(row.title, fontWeight = FontWeight.SemiBold)
                            Text(listOf(row.year, if (row.type == "series") "TV" else "Movie", if (row.rating > 0) "★ ${"%.1f".format(row.rating)}" else "").filter { it.isNotBlank() }.joinToString(" · "), color = Color(0xFF91A5AE), fontSize = 10.sp)
                            if (row.overview.isNotBlank()) Text(row.overview, color = Color(0xFF91A5AE), fontSize = 11.sp, maxLines = 2)
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = Color(0xFF58E1CF))
                    }
                }
            }
        }
    }
}
