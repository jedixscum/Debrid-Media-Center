package com.debridmediacenter.android

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private val AioBg = Color(0xFF080E14)
private val AioPanel = Color(0xFF101922)
private val AioPanel2 = Color(0xFF14232C)
private val AioTeal = Color(0xFF58E1CF)
private val AioMuted = Color(0xFF91A5AE)

private enum class AioMode { HOME, DEBRID, STREAMING }
private enum class StreamingTab { HOME, SEARCH, MOVIES, TV, ADDONS }

@Composable
fun AioMediaCenterApp() {
    var mode by remember { mutableStateOf(AioMode.HOME) }
    var streamingTab by remember { mutableStateOf(StreamingTab.HOME) }
    var selected by remember { mutableStateOf<AioStreamTitle?>(null) }
    var playerUrl by remember { mutableStateOf<String?>(null) }
    var playerTitle by remember { mutableStateOf("") }

    BackHandler(enabled = mode != AioMode.HOME || selected != null || playerUrl != null) {
        when {
            playerUrl != null -> { playerUrl = null; playerTitle = "" }
            selected != null -> selected = null
            mode != AioMode.HOME -> mode = AioMode.HOME
            else -> Unit
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = AioTeal, background = AioBg, surface = AioPanel)) {
        Surface(Modifier.fillMaxSize(), color = AioBg, contentColor = Color.White) {
            when {
                playerUrl != null -> AioStreamingPlayer(playerTitle, playerUrl!!) { playerUrl = null }
                mode == AioMode.HOME -> AioModeChooser(
                    onDebrid = { mode = AioMode.DEBRID },
                    onStreaming = { mode = AioMode.STREAMING }
                )
                mode == AioMode.DEBRID -> {
                    Box(Modifier.fillMaxSize()) {
                        DebridMediaCenterApp()
                        AioModePill("DEBRID MANAGER", Modifier.align(Alignment.TopCenter)) { mode = AioMode.HOME }
                    }
                }
                else -> AioStreamingApp(
                    tab = streamingTab,
                    onTab = { streamingTab = it },
                    selected = selected,
                    onSelect = { selected = it },
                    onPlay = { title, url -> playerTitle = title; playerUrl = url },
                    onBack = { selected = null }
                )
            }
        }
    }
}

@Composable
private fun AioModeChooser(onDebrid: () -> Unit, onStreaming: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("DEBRID MEDIA CENTER", color = AioTeal, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text("All your media, one app.", fontSize = 30.sp, fontWeight = FontWeight.Bold, lineHeight = 35.sp)
                Text("Manage your debrid libraries or stream through your installed Stremio add-ons.", color = AioMuted, fontSize = 13.sp, lineHeight = 19.sp)
            }
        }
        item {
            AioModeCard(
                icon = Icons.Default.Folder,
                eyebrow = "DEBRID MANAGER",
                title = "Find · Add · Manage · Play",
                body = "Search movies and TV, find releases, add them to Real-Debrid or TorBox, manage your libraries, and play ready media.",
                action = "Open Debrid Manager",
                onClick = onDebrid
            )
        }
        item {
            AioModeCard(
                icon = Icons.Default.PlayArrow,
                eyebrow = "STREMIO STREAMING",
                title = "Browse · Pick a stream · Watch",
                body = "Install generic Stremio add-ons, search movies and shows, choose a stream, and watch directly in the built-in player.",
                action = "Open Streaming",
                onClick = onStreaming
            )
        }
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AioPanel), shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Extension, contentDescription = null, tint = AioTeal)
                    Column(Modifier.weight(1f)) {
                        Text("Two systems, one clean app", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Your debrid accounts and Stremio add-ons stay separate so each side can evolve independently.", color = AioMuted, fontSize = 11.sp, lineHeight = 16.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun AioModeCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    eyebrow: String,
    title: String,
    body: String,
    action: String,
    onClick: () -> Unit
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AioPanel2), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Box(Modifier.size(48.dp).background(AioTeal.copy(alpha = 0.12f), RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = AioTeal, modifier = Modifier.size(25.dp))
            }
            Text(eyebrow, color = AioTeal, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Text(title, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text(body, color = AioMuted, fontSize = 12.sp, lineHeight = 18.sp)
            Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = AioTeal, contentColor = AioBg), modifier = Modifier.fillMaxWidth()) {
                Text(action, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AioModePill(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier.padding(top = 10.dp)) {
        Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.size(5.dp))
        Text(label, fontSize = 10.sp)
    }
}

private data class AioStreamTitle(
    val id: String,
    val type: String,
    val title: String,
    val year: String,
    val overview: String,
    val posterUrl: String,
    val episodes: List<AioEpisode> = emptyList()
)

private data class AioEpisode(val season: Int, val episode: Int, val title: String, val id: String)

@Composable
private fun AioStreamingApp(
    tab: StreamingTab,
    onTab: (StreamingTab) -> Unit,
    selected: AioStreamTitle?,
    onSelect: (AioStreamTitle) -> Unit,
    onPlay: (String, String) -> Unit,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxWidth()) {
            if (selected != null) {
                AioStreamDetails(selected, onBack, onPlay)
            } else {
                when (tab) {
                    StreamingTab.HOME -> AioStreamingHome(onSelect, onTab)
                    StreamingTab.SEARCH -> AioStreamingSearch(onSelect)
                    StreamingTab.MOVIES -> AioStreamingCatalog("movie", "Movies", onSelect)
                    StreamingTab.TV -> AioStreamingCatalog("series", "TV Shows", onSelect)
                    StreamingTab.ADDONS -> StremioAddonsScreen(onClose = { onTab(StreamingTab.HOME) })
                }
            }
        }
        if (selected == null && tab != StreamingTab.ADDONS) {
            NavigationBar(containerColor = AioPanel) {
                listOf(
                    StreamingTab.HOME to ("Home" to Icons.Default.Home),
                    StreamingTab.SEARCH to ("Search" to Icons.Default.Search),
                    StreamingTab.MOVIES to ("Movies" to Icons.Default.Movie),
                    StreamingTab.TV to ("TV" to Icons.Default.Folder),
                    StreamingTab.ADDONS to ("Add-ons" to Icons.Default.Extension)
                ).forEach { (target, pair) ->
                    NavigationBarItem(selected = tab == target, onClick = { onTab(target) }, icon = { Icon(pair.second, contentDescription = pair.first) }, label = { Text(pair.first, fontSize = 9.sp) })
                }
            }
        }
    }
}

@Composable
private fun AioStreamingHome(onSelect: (AioStreamTitle) -> Unit, onTab: (StreamingTab) -> Unit) {
    var movies by remember { mutableStateOf<List<AioStreamTitle>>(emptyList()) }
    var shows by remember { mutableStateOf<List<AioStreamTitle>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                fetchAioCatalog("movie", "imdbRating") to fetchAioCatalog("series", "imdbRating")
            }
        }
        result.onSuccess { movies = it.first; shows = it.second }
        loading = false
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("STREMIO STREAMING", color = AioTeal, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text("Watch your way.", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("Streams come from the add-ons you install.", color = AioMuted, fontSize = 13.sp)
            }
        }
        item {
            Card(onClick = { onTab(StreamingTab.SEARCH) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp), colors = CardDefaults.cardColors(containerColor = AioPanel2), shape = RoundedCornerShape(20.dp)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = AioTeal)
                    Column(Modifier.weight(1f)) { Text("Search movies & TV", fontWeight = FontWeight.SemiBold); Text("Find a title, then choose a stream.", color = AioMuted, fontSize = 11.sp) }
                }
            }
        }
        item { AioTitleRow("Popular movies", movies, loading, onSelect) }
        item { AioTitleRow("Popular TV shows", shows, loading, onSelect) }
        item {
            Card(onClick = { onTab(StreamingTab.ADDONS) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp), colors = CardDefaults.cardColors(containerColor = AioPanel), shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Extension, contentDescription = null, tint = AioTeal)
                    Column(Modifier.weight(1f)) { Text("Manage Stremio add-ons", fontWeight = FontWeight.SemiBold); Text("Add or remove stream sources.", color = AioMuted, fontSize = 11.sp) }
                    Icon(Icons.Default.AddCircle, contentDescription = null, tint = AioTeal)
                }
            }
        }
    }
}

@Composable
private fun AioStreamingCatalog(type: String, heading: String, onSelect: (AioStreamTitle) -> Unit) {
    var items by remember(type) { mutableStateOf<List<AioStreamTitle>>(emptyList()) }
    var loading by remember(type) { mutableStateOf(true) }
    LaunchedEffect(type) {
        items = withContext(Dispatchers.IO) { runCatching { fetchAioCatalog(type, "imdbRating") }.getOrDefault(emptyList()) }
        loading = false
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { AioStreamingHeader(heading, "Browse titles available to your add-ons") }
        if (loading) item { AioLoading() }
        items(items, key = { "$type:${it.id}" }) { item -> AioResultCard(item, onSelect) }
    }
}

@Composable
private fun AioStreamingSearch(onSelect: (AioStreamTitle) -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<AioStreamTitle>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { AioStreamingHeader("Search", "Find movies and TV shows by title") }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(query, { query = it }, modifier = Modifier.weight(1f), singleLine = true, label = { Text("Movie or TV show") })
                Button(enabled = !busy && query.trim().length >= 2, onClick = {
                    busy = true
                    // Search on the IO dispatcher without blocking Compose.
                }) { Icon(Icons.Default.Search, contentDescription = "Search") }
            }
        }
        item {
            if (busy) {
                LaunchedEffect(query) {
                    results = withContext(Dispatchers.IO) { runCatching { fetchAioSearch(query) }.getOrDefault(emptyList()) }
                    busy = false
                }
            }
        }
        items(results, key = { it.id }) { item -> AioResultCard(item, onSelect) }
    }
}

@Composable
private fun AioStreamingHeader(title: String, subtitle: String) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("STREAMING", color = AioTeal, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
        Text(title, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = AioMuted, fontSize = 12.sp)
    }
}

@Composable
private fun AioTitleRow(title: String, items: List<AioStreamTitle>, loading: Boolean, onSelect: (AioStreamTitle) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 18.dp))
        if (loading && items.isEmpty()) AioLoading()
        else LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items.take(14), key = { it.id }) { item ->
                Card(onClick = { onSelect(item) }, modifier = Modifier.width(120.dp), colors = CardDefaults.cardColors(containerColor = AioPanel), shape = RoundedCornerShape(15.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        AsyncImage(model = item.posterUrl.takeIf { it.isNotBlank() }, contentDescription = item.title, modifier = Modifier.fillMaxWidth().height(172.dp), contentScale = ContentScale.Crop)
                        Text(item.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, modifier = Modifier.padding(horizontal = 9.dp))
                        Text(item.year.ifBlank { item.type.uppercase() }, color = AioMuted, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun AioResultCard(item: AioStreamTitle, onSelect: (AioStreamTitle) -> Unit) {
    Card(onClick = { onSelect(item) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp), colors = CardDefaults.cardColors(containerColor = AioPanel), shape = RoundedCornerShape(17.dp)) {
        Row(Modifier.padding(11.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = item.posterUrl.takeIf { it.isNotBlank() }, contentDescription = item.title, modifier = Modifier.size(76.dp, 112.dp), contentScale = ContentScale.Crop)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(item.title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("${item.type.uppercase()} · ${item.year.ifBlank { "Unknown year" }}", color = AioTeal, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                if (item.overview.isNotBlank()) Text(item.overview.take(180), color = AioMuted, fontSize = 11.sp, lineHeight = 16.sp)
            }
            Icon(Icons.Default.PlayArrow, contentDescription = "Open", tint = AioTeal)
        }
    }
}

@Composable
private fun AioStreamDetails(item: AioStreamTitle, onBack: () -> Unit, onPlay: (String, String) -> Unit) {
    val context = LocalContext.current
    val addonStore = remember { StremioAddonStore(context.applicationContext) }
    var streams by remember(item.id) { mutableStateOf<List<StremioStream>>(emptyList()) }
    var loading by remember(item.id) { mutableStateOf(true) }
    var status by remember(item.id) { mutableStateOf("") }
    var selectedEpisode by remember(item.id) { mutableStateOf<AioEpisode?>(null) }
    var season by remember(item.id) { mutableIntStateOf(1) }
    val seasons = item.episodes.map { it.season }.distinct().sorted()
    LaunchedEffect(item.id, selectedEpisode?.id) {
        loading = true
        status = ""
        val mediaId = if (item.type == "movie") item.id else selectedEpisode?.let { "${item.id}:${it.season}:${it.episode}" }
        if (mediaId == null) {
            streams = emptyList()
            loading = false
            status = "Select an episode to load streams."
        } else {
            val result = withContext(Dispatchers.IO) { runCatching { addonStore.streams(if (item.type == "movie") "movie" else "series", mediaId) } }
            result.onSuccess { streams = it; if (it.isEmpty()) status = "No direct playable streams were returned by the installed add-ons." }
                .onFailure { status = it.message ?: "Could not query the installed add-ons." }
            loading = false
        }
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(13.dp), contentPadding = PaddingValues(bottom = 28.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 18.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = AioTeal) }
                Column(Modifier.weight(1f)) { Text("STREAM DETAILS", color = AioTeal, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp); Text("Choose a playable source", color = AioMuted, fontSize = 11.sp) }
            }
        }
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal = 18.dp), colors = CardDefaults.cardColors(containerColor = AioPanel2), shape = RoundedCornerShape(21.dp)) {
                Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(13.dp), verticalAlignment = Alignment.Top) {
                    AsyncImage(model = item.posterUrl.takeIf { it.isNotBlank() }, contentDescription = item.title, modifier = Modifier.width(112.dp).height(166.dp), contentScale = ContentScale.Crop)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(item.title, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                        Text("${item.year.ifBlank { "" }} · ${item.type.uppercase()}", color = AioTeal, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        if (item.overview.isNotBlank()) Text(item.overview.take(240), color = AioMuted, fontSize = 11.sp, lineHeight = 16.sp)
                        Text("${addonStore.addons().size} add-on(s) installed", color = AioMuted, fontSize = 10.sp)
                    }
                }
            }
        }
        if (item.type != "movie") {
            item {
                Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Episodes", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    if (seasons.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            items(seasons) { s -> FilterChip(selected = season == s, onClick = { season = s; selectedEpisode = null }, label = { Text("Season $s") }) }
                        }
                        item.episodes.filter { it.season == season }.forEach { episode ->
                            Card(onClick = { selectedEpisode = episode }, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (selectedEpisode?.id == episode.id) AioPanel2 else AioPanel), shape = RoundedCornerShape(13.dp)) {
                                Row(Modifier.padding(11.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("E${episode.episode}", color = AioTeal, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text(episode.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                    if (selectedEpisode?.id == episode.id) Icon(Icons.Default.PlayArrow, contentDescription = null, tint = AioTeal, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    } else Text("No episode guide was returned by the metadata service.", color = AioMuted, fontSize = 12.sp)
                }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Streams", fontSize = 20.sp, fontWeight = FontWeight.Bold); Text("Direct playable streams from your add-ons", color = AioMuted, fontSize = 11.sp) }
                if (loading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = AioTeal, strokeWidth = 2.dp)
            }
        }
        if (status.isNotBlank()) item { Text(status, color = AioMuted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 18.dp)) }
        items(streams, key = { "${it.addonId}:${it.url}" }) { stream ->
            Card(onClick = { onPlay(item.title + if (selectedEpisode != null) " · E${selectedEpisode!!.episode}" else "", stream.url) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp), colors = CardDefaults.cardColors(containerColor = AioPanel), shape = RoundedCornerShape(17.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stream.name, color = AioTeal, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = AioTeal)
                    }
                    Text(stream.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(stream.url, color = AioMuted, fontSize = 10.sp, maxLines = 2)
                }
            }
        }
    }
}

private fun fetchAioCatalog(type: String, catalog: String): List<AioStreamTitle> {
    val nativeType = if (type == "movie") "movie" else "series"
    val url = "https://v3-cinemeta.strem.io/catalog/$nativeType/$catalog.json"
    return fetchAioJson(url).optJSONArray("metas").toTitleList(nativeType)
}

private fun fetchAioSearch(query: String): List<AioStreamTitle> {
    val encoded = URLEncoder.encode(query.trim(), "UTF-8")
    val root = fetchAioJson("https://v3.sg.media-imdb.com/suggestion/x/$encoded.json")
    val rows = root.optJSONArray("d") ?: return emptyList()
    return (0 until rows.length()).mapNotNull { i ->
        val row = rows.optJSONObject(i) ?: return@mapNotNull null
        val id = row.optString("id").takeIf { it.startsWith("tt") } ?: return@mapNotNull null
        AioStreamTitle(id, if (row.optString("q").lowercase() in listOf("movie", "feature", "short")) "movie" else "tv", row.optString("l"), row.optString("y"), row.optString("s"), row.optString("i").takeIf { it.startsWith("http") }.orEmpty())
    }.distinctBy { it.id }.take(30)
}

private fun fetchAioJson(endpoint: String): JSONObject {
    val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 12000
        readTimeout = 15000
        setRequestProperty("Accept", "application/json")
        setRequestProperty("User-Agent", "DebridMediaCenter-AIO/1.0")
    }
    try {
        val code = connection.responseCode
        val body = (if (code in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        if (code !in 200..299) throw IllegalStateException("Metadata service returned HTTP $code.")
        return JSONObject(body)
    } finally { connection.disconnect() }
}

private fun JSONArray?.toTitleList(type: String): List<AioStreamTitle> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { i ->
        val row = optJSONObject(i) ?: return@mapNotNull null
        val id = row.optString("imdb_id").takeIf { it.startsWith("tt") } ?: row.optString("id").takeIf { it.startsWith("tt") } ?: return@mapNotNull null
        AioStreamTitle(id, type, row.optString("name"), row.optString("releaseInfo").ifBlank { row.optString("year") }, row.optString("description"), row.optString("poster").takeIf { it.startsWith("http") }.orEmpty())
    }.distinctBy { it.id }.take(40)
}

@Composable
private fun AioLoading() {
    Row(Modifier.fillMaxWidth().padding(25.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = AioTeal, strokeWidth = 2.dp)
        Spacer(Modifier.size(9.dp))
        Text("Loading…", color = AioMuted, fontSize = 12.sp)
    }
}

@Composable
private fun AioStreamingPlayer(title: String, url: String, onClose: () -> Unit) {
    val context = LocalContext.current
    var error by remember(url) { mutableStateOf("") }
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(url)))
            playWhenReady = true
            prepare()
        }
    }
    androidx.compose.runtime.DisposableEffect(player) {
        val listener = object : Player.Listener { override fun onPlayerError(errorValue: PlaybackException) { error = errorValue.message ?: errorValue.errorCodeName } }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("NOW PLAYING", color = AioTeal, fontSize = 10.sp, fontWeight = FontWeight.Bold); Text(title, color = Color.White, fontSize = 12.sp, maxLines = 1) }
            OutlinedButton(onClick = onClose) { Text("Close") }
        }
        if (error.isNotBlank()) Text("Playback failed: $error", color = Color(0xFFFF9B9B), fontSize = 12.sp, modifier = Modifier.padding(14.dp))
        AndroidView(factory = { PlayerView(it).apply { this.player = player; useController = true } }, update = { it.player = player }, modifier = Modifier.fillMaxWidth().weight(1f))
    }
}
