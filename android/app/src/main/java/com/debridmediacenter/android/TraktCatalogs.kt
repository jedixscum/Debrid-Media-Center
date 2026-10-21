package com.debridmediacenter.android

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private data class TraktSection(val name: String, val listUrl: String, val kind: String, val sort: String)
private data class TraktRow(val title: String, val type: String, val year: String, val overview: String, val poster: String, val id: String, val rating: Double, val votes: Int, val added: String)

private class TraktSectionStore(context: Context) {
    private val prefs = context.getSharedPreferences("dmc_trakt_sections", Context.MODE_PRIVATE)
    fun clientId() = prefs.getString("client_id", "").orEmpty()
    fun setClientId(value: String) { prefs.edit().putString("client_id", value.trim()).apply() }
    fun sections(): List<TraktSection> {
        val a = runCatching { JSONArray(prefs.getString("sections", "[]")) }.getOrDefault(JSONArray())
        return (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { TraktSection(it.optString("name"), it.optString("url"), it.optString("kind", "all"), it.optString("sort", "recent")) } }
    }
    fun save(sections: List<TraktSection>) {
        val a = JSONArray()
        sections.forEach { a.put(JSONObject().put("name", it.name).put("url", it.listUrl).put("kind", it.kind).put("sort", it.sort)) }
        prefs.edit().putString("sections", a.toString()).apply()
    }
}

private fun fetchTraktSection(section: TraktSection, clientId: String): List<TraktRow> {
    require(clientId.isNotBlank()) { "Add your Trakt API Client ID in Discover settings first." }
    val uri = Uri.parse(section.listUrl.trim())
    val parts = uri.pathSegments
    val listIndex = parts.indexOf("lists")
    require(listIndex >= 0 && parts.size > listIndex + 1) { "Use a Trakt list URL such as https://trakt.tv/users/USERNAME/lists/LIST-SLUG" }
    val user = parts.getOrNull(parts.indexOf("users") + 1).orEmpty()
    val slug = parts.getOrNull(listIndex + 1).orEmpty()
    require(user.isNotBlank() && slug.isNotBlank()) { "Could not read the username and list slug from that Trakt URL." }
    val endpoint = "https://api.trakt.tv/users/${Uri.encode(user)}/lists/${Uri.encode(slug)}/items/${if (section.kind == "all") "movies,shows" else if (section.kind == "series") "shows" else "movies"}?extended=full"
    val c = URL(endpoint).openConnection() as HttpURLConnection
    c.connectTimeout = 12000; c.readTimeout = 18000
    c.setRequestProperty("trakt-api-version", "2")
    c.setRequestProperty("trakt-api-key", clientId)
    c.setRequestProperty("Content-Type", "application/json")
    try {
        val code = c.responseCode
        val body = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) error("Trakt returned HTTP $code. Check that the list is public and your Client ID is valid.")
        val a = JSONArray(body)
        val rows = (0 until a.length()).mapNotNull { i ->
            val entry = a.optJSONObject(i) ?: return@mapNotNull null
            val kind = if (entry.has("movie")) "movie" else if (entry.has("show")) "series" else return@mapNotNull null
            if (section.kind == "movies" && kind != "movie" || section.kind == "series" && kind != "series") return@mapNotNull null
            val m = entry.optJSONObject(if (kind == "movie") "movie" else "show") ?: return@mapNotNull null
            val ids = m.optJSONObject("ids") ?: JSONObject()
            TraktRow(m.optString("title"), kind, m.optString("year"), m.optString("overview"),
                "https://images.metahub.space/poster/medium/${ids.optString("imdb", ids.optString("tmdb"))}/img",
                ids.optString("imdb").takeIf { it.isNotBlank() }?.let { "tt$it".removePrefix("tttt") } ?: ids.optString("tmdb").ifBlank { m.optString("title") },
                m.optDouble("rating", 0.0), m.optInt("votes", 0), entry.optString("listed_at"))
        }
        return when(section.sort) {
            "popular" -> rows.sortedWith(compareByDescending<TraktRow> { it.votes }.thenByDescending { it.rating })
            "rating" -> rows.sortedByDescending { it.rating }
            "year" -> rows.sortedByDescending { it.year.toIntOrNull() ?: 0 }
            else -> rows.sortedByDescending { it.added }
        }.take(100)
    } finally { c.disconnect() }
}

@Composable
internal fun V2TraktCatalogManager(onSelect: (V2Title) -> Unit) {
    val context = LocalContext.current
    val store = remember { TraktSectionStore(context.applicationContext) }
    var clientId by remember { mutableStateOf(store.clientId()) }
    var sections by remember { mutableStateOf(store.sections()) }
    var showEditor by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("all") }
    var sort by remember { mutableStateOf("recent") }
    var active by remember { mutableStateOf<TraktSection?>(null) }
    var rows by remember { mutableStateOf(emptyList<TraktRow>()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Your Trakt catalogs", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Turn any public Trakt list into a live-updating section.", color = Color(0xFF91A5AE), fontSize = 11.sp)
            }
            IconButton(onClick = { showEditor = !showEditor; error = "" }) { Icon(if (showEditor) Icons.Default.Close else Icons.Default.AddCircle, "Add Trakt catalog", tint = Color(0xFF58E1CF)) }
        }
        OutlinedTextField(clientId, { clientId = it; store.setClientId(it) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Trakt API Client ID") }, supportingText = { Text("Create a free API app at trakt.tv/oauth/applications. The ID is saved on this device.") })
        if (showEditor) {
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Section name (e.g. Netflix)") })
            OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Trakt list URL") }, placeholder = { Text("https://trakt.tv/users/.../lists/...") })
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
                if (cleanName.isBlank() || url.isBlank()) { error = "Enter a section name and Trakt list URL." }
                else {
                    val entry = TraktSection(cleanName, url.trim(), kind, sort)
                    sections = sections.filterNot { it.name.equals(cleanName, true) } + entry
                    store.save(sections); active = entry; showEditor = false; busy = true; error = ""
                }
            }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Save section and load list") }
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
                                IconButton(onClick = { name = section.name; url = section.listUrl; kind = section.kind; sort = section.sort; showEditor = true }) { Icon(Icons.Default.Edit, "Edit section", tint = Color(0xFF58E1CF), modifier = Modifier.size(17.dp)) }
                                IconButton(onClick = { sections = sections.filterNot { it == section }; store.save(sections); if (active == section) { active = null; rows = emptyList() } }) { Icon(Icons.Default.DeleteOutline, "Delete section", tint = Color(0xFF91A5AE), modifier = Modifier.size(17.dp)) }
                            }
                        }
                    }
                }
            }
        }
        LaunchedEffect(active, busy) {
            val chosen = active
            if (busy && chosen != null) {
                val result = withContext(Dispatchers.IO) { runCatching { fetchTraktSection(chosen, clientId.trim()) } }
                result.onSuccess { rows = it; error = if (it.isEmpty()) "This list returned no titles." else "" }
                    .onFailure { rows = emptyList(); error = it.message ?: "Could not load Trakt list." }
                busy = false
            }
        }
        if (active != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(active!!.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { busy = true }) { Icon(Icons.Default.Refresh, "Refresh Trakt list", tint = Color(0xFF58E1CF)) }
                IconButton(onClick = { active = null; rows = emptyList(); error = "" }) { Icon(Icons.Default.Close, "Close list", tint = Color(0xFF91A5AE)) }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (error.isNotBlank()) Text(error, color = Color(0xFFFFB4AB), fontSize = 12.sp)
            rows.forEach { row ->
                val title = V2Title(row.id, row.type, row.title, row.year, row.overview, row.poster)
                Card(onClick = { onSelect(title) }, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color(0xFF101922)), shape = RoundedCornerShape(13.dp)) {
                    Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        androidx.compose.foundation.Image(painter = coil.compose.rememberAsyncImagePainter(row.poster), contentDescription = null, modifier = Modifier.width(44.dp).height(64.dp))
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
