package com.debridmediacenter.android

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.ui.PlayerView
import androidx.media3.ui.TrackSelectionDialogBuilder
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private val V2Bg = Color(0xFF080E14)
private val V2Panel = Color(0xFF101922)
private val V2Panel2 = Color(0xFF14232C)
private val V2Teal = Color(0xFF58E1CF)
private val V2Muted = Color(0xFF91A5AE)

private enum class V2Mode { HOME, DEBRID, STREAMING }
private enum class V2Tab { HOME, SEARCH, MOVIES, TV, CATALOGS, LIBRARY, ADDONS }

internal data class V2Title(
    val id: String,
    val type: String,
    val title: String,
    val year: String,
    val overview: String,
    val posterUrl: String
)

private data class V2Episode(
    val season: Int,
    val episode: Int,
    val title: String,
    val id: String,
    val overview: String = "",
    val thumbnail: String = ""
)

@Composable
fun AioMediaCenterV2App() {
    var mode by remember { mutableStateOf(V2Mode.HOME) }
    var tab by remember { mutableStateOf(V2Tab.HOME) }
    var selected by remember { mutableStateOf<V2Title?>(null) }
    var player by remember { mutableStateOf<Pair<String, String>?>(null) }

    BackHandler(enabled = mode != V2Mode.HOME || selected != null || player != null) {
        when {
            player != null -> player = null
            selected != null -> selected = null
            mode != V2Mode.HOME -> mode = V2Mode.HOME
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = V2Teal, background = V2Bg, surface = V2Panel)) {
        Surface(Modifier.fillMaxSize(), color = V2Bg, contentColor = Color.White) {
            when {
                player != null -> V2Player(player!!.first, player!!.second) { player = null }
                mode == V2Mode.HOME -> V2Chooser(
                    onDebrid = { mode = V2Mode.DEBRID },
                    onStreaming = { mode = V2Mode.STREAMING }
                )
                mode == V2Mode.DEBRID -> Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
                    DebridMediaCenterApp(onHome = { mode = V2Mode.HOME })
                }
                else -> V2Streaming(
                    tab = tab, onTab = { tab = it }, selected = selected,
                    onSelect = { selected = it }, onBack = { selected = null },
                    onPlay = { title, url -> player = title to url },
                    onHome = { mode = V2Mode.HOME; selected = null; tab = V2Tab.HOME }
                )
            }
        }
    }
}

@Composable
private fun V2Chooser(onDebrid: () -> Unit, onStreaming: () -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                DmcBrandMark(62)
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("DEBRID MEDIA CENTER", color = V2Teal, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.7.sp)
                    Text("Your media. Your way.", fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp)
                    Text("One home for discovery, debrid libraries and playback.", color = V2Muted, fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(V2Panel), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.Security, null, tint = V2Teal)
                    Text("Your add-ons and provider connections stay under your control.", color = V2Muted, fontSize = 11.sp, lineHeight = 16.sp)
                }
            }
        }
        item {
            V2ModeCard(Icons.Default.Folder, "DEBRID MANAGER", "Find · Add · Manage · Download", "Search titles, find releases, add them to Real-Debrid or TorBox, manage libraries, and play ready media.", "Open Debrid Manager", onDebrid)
        }
        item {
            V2ModeCard(Icons.Default.PlayArrow, "STREMIO STREAMING", "Browse · Pick a stream · Watch", "Search movies and TV, select seasons and episodes, query your installed Stremio add-ons, and play direct streams.", "Open Streaming", onStreaming)
        }
    }
}

@Composable
private fun DmcBrandMark(size: Int) {
    Box(Modifier.size(size.dp).background(V2Panel2, RoundedCornerShape((size / 4).dp)), contentAlignment = Alignment.Center) {
        Box(Modifier.size((size * 0.72f).dp).background(V2Teal.copy(alpha = 0.14f), RoundedCornerShape((size / 5).dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.PlayCircleFilled, contentDescription = "Debrid Media Center", tint = V2Teal, modifier = Modifier.size((size * 0.53f).dp))
        }
    }
}

@Composable
private fun V2ModeCard(icon: androidx.compose.ui.graphics.vector.ImageVector, eyebrow: String, title: String, body: String, action: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(V2Panel2), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(48.dp).background(V2Teal.copy(.12f), RoundedCornerShape(15.dp)), Alignment.Center) { Icon(icon, null, tint = V2Teal) }
            Text(eyebrow, color = V2Teal, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Text(title, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text(body, color = V2Muted, fontSize = 12.sp, lineHeight = 18.sp)
            Button(onClick, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(V2Teal, V2Bg)) { Text(action, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun V2Streaming(tab: V2Tab, onTab: (V2Tab) -> Unit, selected: V2Title?, onSelect: (V2Title) -> Unit, onBack: () -> Unit, onPlay: (String, String) -> Unit, onHome: () -> Unit) {
    val context = LocalContext.current
    val mediaLibrary = remember { V2MediaLibrary(context.applicationContext) }
    LaunchedEffect(selected?.id) { selected?.let { mediaLibrary.markRecent(it.toSavedMedia()) } }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 14.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DmcBrandMark(38)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("DEBRID MEDIA CENTER", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text("Your media, in one place", color = V2Muted, fontSize = 10.sp)
            }
            TextButton(onClick = onHome) { Icon(Icons.Default.Home, contentDescription = "Home", modifier = Modifier.size(17.dp)); Spacer(Modifier.width(4.dp)); Text("Home", fontSize = 12.sp) }
        }
        Column(Modifier.weight(1f).fillMaxWidth()) {
            if (selected != null) V2Details(selected, onBack, onPlay)
            else when (tab) {
                V2Tab.HOME -> V2Home(onSelect, onTab)
                V2Tab.SEARCH -> V2Search(onSelect)
                V2Tab.MOVIES -> V2Catalog("movie", "Movies", onSelect)
                V2Tab.TV -> V2Catalog("series", "TV Shows", onSelect)
                V2Tab.CATALOGS -> V2AddonCatalogBrowser(onSelect)
                V2Tab.LIBRARY -> V2LibraryScreen(onSelect)
                V2Tab.ADDONS -> StremioAddonsScreen { onTab(V2Tab.HOME) }
            }
        }
        if (selected == null && tab != V2Tab.ADDONS) {
            NavigationBar(containerColor = V2Panel) {
                listOf(
                    V2Tab.HOME to ("Home" to Icons.Default.Home),
                    V2Tab.SEARCH to ("Search" to Icons.Default.Search),
                    V2Tab.MOVIES to ("Movies" to Icons.Default.Movie),
                    V2Tab.TV to ("TV" to Icons.Default.Tv),
                    V2Tab.CATALOGS to ("Discover" to Icons.Default.Explore),
                    V2Tab.LIBRARY to ("Library" to Icons.Default.Bookmarks),
                    V2Tab.ADDONS to ("Add-ons" to Icons.Default.Extension)
                ).forEach { (target, p) ->
                    NavigationBarItem(selected = tab == target, onClick = { onTab(target) }, icon = { Icon(p.second, p.first) }, label = { Text(p.first, fontSize = 9.sp) })
                }
            }
        }
    }
}

@Composable
private fun V2Home(onSelect: (V2Title) -> Unit, onTab: (V2Tab) -> Unit) {
    var movies by remember { mutableStateOf(emptyList<V2Title>()) }
    var shows by remember { mutableStateOf(emptyList<V2Title>()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        val result = withContext(Dispatchers.IO) { runCatching { fetchV2Catalog("movie") to fetchV2Catalog("series") } }
        result.onSuccess { movies = it.first; shows = it.second }
        loading = false
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { V2Header("Watch your way.", "Streams come from the add-ons you install.") }
        item {
            Card(onClick = { onTab(V2Tab.SEARCH) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp), colors = CardDefaults.cardColors(V2Panel2), shape = RoundedCornerShape(20.dp)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Search, null, tint = V2Teal)
                    Column(Modifier.weight(1f)) { Text("Search movies & TV", fontWeight = FontWeight.SemiBold); Text("Find a title, then choose a stream.", color = V2Muted, fontSize = 11.sp) }
                }
            }
        }
        item { V2TitleRow("Popular movies", movies, loading, onSelect) }
        item { V2TitleRow("Popular TV shows", shows, loading, onSelect) }
        item {
            Card(onClick = { onTab(V2Tab.CATALOGS) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp), colors = CardDefaults.cardColors(V2Panel2), shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.Explore, null, tint = V2Teal)
                    Column(Modifier.weight(1f)) { Text("Discover add-on catalogs", fontWeight = FontWeight.SemiBold); Text("Browse collections from installed add-ons.", color = V2Muted, fontSize = 11.sp) }
                    Icon(Icons.Default.ChevronRight, null, tint = V2Teal)
                }
            }
        }
        item {
            Card(onClick = { onTab(V2Tab.ADDONS) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp), colors = CardDefaults.cardColors(V2Panel), shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Extension, null, tint = V2Teal)
                    Column(Modifier.weight(1f)) { Text("Manage Stremio add-ons", fontWeight = FontWeight.SemiBold); Text("Add or remove stream sources.", color = V2Muted, fontSize = 11.sp) }
                    Icon(Icons.Default.AddCircle, null, tint = V2Teal)
                }
            }
        }
    }
}

@Composable
private fun V2Catalog(type: String, heading: String, onSelect: (V2Title) -> Unit) {
    var rows by remember(type) { mutableStateOf(emptyList<V2Title>()) }
    var loading by remember(type) { mutableStateOf(true) }
    LaunchedEffect(type) {
        rows = withContext(Dispatchers.IO) { runCatching { fetchV2Catalog(type) }.getOrDefault(emptyList()) }
        loading = false
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { V2Header(heading, "Browse titles, then choose a stream source.") }
        if (loading) item { V2Loading() }
        items(rows, key = { "$type:${it.id}" }) { V2ResultCard(it, onSelect) }
    }
}

@Composable
private fun V2Search(onSelect: (V2Title) -> Unit) {
    val context = LocalContext.current
    val catalogClient = remember { StremioCatalogClient(context.applicationContext) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<V2Title>()) }
    var busy by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { V2Header("Search", "Search metadata and searchable catalogs from installed add-ons.") }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(query, { query = it }, Modifier.weight(1f), singleLine = true, label = { Text("Movie or TV show") })
                Button(enabled = !busy && query.trim().length >= 2, onClick = { busy = true; searched = true; message = "" }) { Icon(Icons.Default.Search, "Search") }
            }
        }
        item {
            LaunchedEffect(busy) {
                if (busy) {
                    val searchResult = withContext(Dispatchers.IO) {
                        val core = runCatching { fetchV2Search(query) }.getOrDefault(emptyList())
                        val catalogs = runCatching { catalogClient.catalogs() }.getOrDefault(emptyList())
                        val searchable = catalogs.filter { it.supportsSearch }.take(10)
                        val addonRows = searchable.flatMap { catalog ->
                            runCatching { catalogClient.items(catalog, query) }.getOrDefault(emptyList()).map { row ->
                                V2Title(row.id, if (row.type.lowercase() in listOf("series", "tv")) "series" else "movie", row.title, row.year, row.overview, row.poster)
                            }
                        }
                        Pair((addonRows + core).distinctBy { it.id }.take(60), searchable.isNotEmpty())
                    }
                    results = searchResult.first
                    message = if (!searchResult.second) "No searchable add-on catalogs detected; results use metadata search." else ""
                    busy = false
                }
            }
        }
        if (busy) item { V2Loading() }
        if (message.isNotBlank()) item { Text(message, color = V2Muted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 18.dp)) }
        if (!busy && searched && results.isEmpty()) item { Text("No matching titles found. Check your connection or try another title.", color = V2Muted, modifier = Modifier.padding(horizontal = 18.dp)) }
        items(results, key = { it.type + ":" + it.id }) { V2ResultCard(it, onSelect) }
    }
}

@Composable
private fun V2AddonCatalogBrowser(onSelect: (V2Title) -> Unit) {
    val context = LocalContext.current
    val client = remember { StremioCatalogClient(context.applicationContext) }
    var catalogs by remember { mutableStateOf(emptyList<StremioCatalogRef>()) }
    var selected by remember { mutableStateOf<StremioCatalogRef?>(null) }
    var query by remember { mutableStateOf("") }
    var rows by remember { mutableStateOf(emptyList<StremioCatalogTitle>()) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var refreshKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(refreshKey) {
        catalogs = withContext(Dispatchers.IO) { runCatching { client.catalogs() }.getOrDefault(emptyList()) }
    }
    LaunchedEffect(selected, query) {
        val catalog = selected ?: return@LaunchedEffect
        loading = true; message = ""
        val result = withContext(Dispatchers.IO) { runCatching { client.items(catalog, query) } }
        result.onSuccess { rows = it; if (it.isEmpty()) message = "No titles returned. This catalog may not support search or may be temporarily unavailable." }
            .onFailure { message = it.message ?: "Could not load this catalog." }
        loading = false
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 26.dp)) {
        item { V2Header("Discover", "Browse collections published by your installed add-ons.") }
        if (selected == null) {
            item { V2MdbCatalogManager(onSelect) }
            item {
                OutlinedButton(onClick = { refreshKey++ }, modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp)) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(5.dp)); Text("Refresh add-on catalogs") }
            }
            if (catalogs.isEmpty()) item {
                Card(Modifier.fillMaxWidth().padding(horizontal = 18.dp), colors = CardDefaults.cardColors(V2Panel), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("No catalogs detected yet", fontWeight = FontWeight.Bold)
                        Text("Install an add-on that declares catalogs in its manifest. Stream-only add-ons remain available when opening a title.", color = V2Muted, fontSize = 12.sp)
                    }
                }
            }
            items(catalogs, key = { it.addonId + ":" + it.type + ":" + it.id }) { catalog ->
                Card(onClick = { selected = catalog; query = "" }, modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp), colors = CardDefaults.cardColors(V2Panel2), shape = RoundedCornerShape(15.dp)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(40.dp).background(V2Teal.copy(alpha = .12f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.VideoLibrary, null, tint = V2Teal) }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(catalog.name, fontWeight = FontWeight.SemiBold)
                            Text(catalog.addonName + " · " + catalog.type, color = V2Muted, fontSize = 10.sp)
                            if (catalog.supportsSearch) Text("Searchable catalog", color = V2Teal, fontSize = 10.sp)
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = V2Teal)
                    }
                }
            }
        } else {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { selected = null; rows = emptyList(); message = "" }) { Icon(Icons.Default.ArrowBack, "Back", tint = V2Teal) }
                    Column(Modifier.weight(1f)) { Text(selected!!.name, fontWeight = FontWeight.Bold); Text(selected!!.addonName, color = V2Muted, fontSize = 10.sp) }
                }
            }
            if (selected!!.supportsSearch) item { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 18.dp), singleLine = true, label = { Text("Search this catalog") }) }
            if (loading) item { V2Loading() }
            if (message.isNotBlank() && !loading) item { Text(message, color = V2Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 18.dp)) }
            items(rows, key = { it.type + ":" + it.id }) { row -> V2ResultCard(V2Title(row.id, if (row.type.lowercase() in listOf("series", "tv")) "series" else "movie", row.title, row.year, row.overview, row.poster), onSelect) }
        }
    }
}

private fun V2Title.toSavedMedia() = V2SavedMedia(
    id = id, type = type, title = title, year = year,
    overview = overview, posterUrl = posterUrl
)

@Composable
private fun V2LibraryScreen(onSelect: (V2Title) -> Unit) {
    val context = LocalContext.current
    val library = remember { V2MediaLibrary(context.applicationContext) }
    var refresh by remember { mutableIntStateOf(0) }
    val saved = remember(refresh) { library.watchlist() }
    val recent = remember(refresh) { library.recentlyViewed() }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(13.dp), contentPadding = PaddingValues(bottom = 26.dp)) {
        item { V2Header("Your library", "Your watchlist and recently opened titles stay on this device.") }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Watchlist", fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${saved.size} saved", color = V2Muted, fontSize = 11.sp)
            }
        }
        if (saved.isEmpty()) item {
            Card(Modifier.fillMaxWidth().padding(horizontal = 18.dp), colors = CardDefaults.cardColors(V2Panel), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Your watchlist is empty", fontWeight = FontWeight.SemiBold)
                    Text("Open a movie or show and tap Add to watchlist to keep it here.", color = V2Muted, fontSize = 12.sp)
                }
            }
        }
        items(saved, key = { "saved:${it.id}" }) { row ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { V2ResultCard(row.toV2Title(), onSelect) }
                IconButton(onClick = { library.removeFromWatchlist(row.id); refresh++ }) {
                    Icon(Icons.Default.BookmarkRemove, "Remove from watchlist", tint = V2Teal)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Recently viewed", fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${recent.size} titles", color = V2Muted, fontSize = 11.sp)
            }
        }
        if (recent.isEmpty()) item {
            Text("Titles you open will appear here automatically.", color = V2Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 18.dp))
        }
        items(recent, key = { "recent:${it.id}" }) { row -> V2ResultCard(row.toV2Title(), onSelect) }
    }
}

private fun V2SavedMedia.toV2Title() = V2Title(id, type, title, year, overview, posterUrl)

@Composable
private fun V2Header(title: String, subtitle: String) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("STREMIO STREAMING", color = V2Teal, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = V2Muted, fontSize = 13.sp)
    }
}

@Composable
private fun V2TitleRow(title: String, rows: List<V2Title>, loading: Boolean, onSelect: (V2Title) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 18.dp))
        if (loading && rows.isEmpty()) V2Loading() else LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(rows.take(14), key = { it.id }) { item ->
                Card(onClick = { onSelect(item) }, modifier = Modifier.width(120.dp), colors = CardDefaults.cardColors(V2Panel), shape = RoundedCornerShape(15.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        AsyncImage(item.posterUrl.takeIf { it.isNotBlank() }, item.title, Modifier.fillMaxWidth().height(172.dp), contentScale = ContentScale.Crop)
                        Text(item.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, modifier = Modifier.padding(horizontal = 9.dp))
                        Text(item.year.ifBlank { item.type.uppercase() }, color = V2Muted, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun V2ResultCard(item: V2Title, onSelect: (V2Title) -> Unit) {
    Card(onClick = { onSelect(item) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp), colors = CardDefaults.cardColors(V2Panel), shape = RoundedCornerShape(17.dp)) {
        Row(Modifier.padding(11.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(item.posterUrl.takeIf { it.isNotBlank() }, item.title, Modifier.size(76.dp, 112.dp), contentScale = ContentScale.Crop)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(item.title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("${item.type.uppercase()} · ${item.year.ifBlank { "Unknown year" }}", color = V2Teal, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                if (item.overview.isNotBlank()) Text(item.overview.take(180), color = V2Muted, fontSize = 11.sp, lineHeight = 16.sp)
            }
            Icon(Icons.Default.PlayArrow, "Open", tint = V2Teal)
        }
    }
}

@Composable
private fun V2Details(item: V2Title, onBack: () -> Unit, onPlay: (String, String) -> Unit) {
    val context = LocalContext.current
    val addonStore = remember { StremioAddonStore(context.applicationContext) }
    val mediaLibrary = remember { V2MediaLibrary(context.applicationContext) }
    var isSaved by remember(item.id) { mutableStateOf(mediaLibrary.isInWatchlist(item.id)) }
    var episodes by remember(item.id) { mutableStateOf(emptyList<V2Episode>()) }
    var selectedEpisode by remember(item.id) { mutableStateOf<V2Episode?>(null) }
    var season by remember(item.id) { mutableIntStateOf(1) }
    var guideLoading by remember(item.id) { mutableStateOf(item.type != "movie") }
    var guideError by remember(item.id) { mutableStateOf("") }
    var streams by remember(item.id) { mutableStateOf(emptyList<StremioStream>()) }
    var streamLoading by remember(item.id) { mutableStateOf(false) }
    var streamStatus by remember(item.id) { mutableStateOf("") }
    var showStreamPicker by remember(item.id) { mutableStateOf(item.type == "movie") }
    var selectedResolution by remember(item.id) { mutableStateOf("All") }

    LaunchedEffect(item.id, item.type) {
        if (item.type == "movie") {
            guideLoading = false
            return@LaunchedEffect
        }
        guideLoading = true
        guideError = ""
        val result = withContext(Dispatchers.IO) { runCatching { fetchV2Episodes(item.id, addonStore) } }
        result.onSuccess {
            episodes = it
            season = it.firstOrNull()?.season ?: 1
            guideLoading = false
            guideError = if (it.isEmpty()) "No episode guide was returned by the metadata service." else ""
        }.onFailure {
            guideLoading = false
            guideError = it.message ?: "Could not load the episode guide."
        }
    }

    LaunchedEffect(item.id, selectedEpisode?.id, showStreamPicker) {
        if (item.type == "movie") {
            streamLoading = true
            streamStatus = ""
            val result = withContext(Dispatchers.IO) { runCatching { addonStore.streams("movie", item.id) } }
            result.onSuccess { streams = it; if (it.isEmpty()) streamStatus = "No direct playable streams were returned by the installed add-ons." }
                .onFailure { streamStatus = it.message ?: "Could not query the installed add-ons." }
            streamLoading = false
        } else if (selectedEpisode != null && showStreamPicker) {
            streamLoading = true
            streamStatus = ""
            val episode = selectedEpisode!!
            val mediaId = "${item.id}:${episode.season}:${episode.episode}"
            val result = withContext(Dispatchers.IO) { runCatching { addonStore.streams("series", mediaId) } }
            result.onSuccess {
                streams = it
                if (it.isEmpty()) streamStatus = "No direct playable streams were returned for S${episode.season.toString().padStart(2, '0')}E${episode.episode.toString().padStart(2, '0')}."
            }.onFailure { streamStatus = it.message ?: "Could not query the installed add-ons." }
            streamLoading = false
        } else if (!showStreamPicker) {
            streams = emptyList()
            streamLoading = false
            streamStatus = ""
        }
    }

    val seasons = episodes.map { it.season }.distinct().sorted()
    val visibleEpisodes = episodes.filter { it.season == season }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(13.dp), contentPadding = PaddingValues(bottom = 28.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 18.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = V2Teal) }
                Column(Modifier.weight(1f)) {
                    Text("STREAM DETAILS", color = V2Teal, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Text(if (item.type == "movie") "Choose a playable source" else "Choose a season and episode", color = V2Muted, fontSize = 11.sp)
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth().padding(horizontal = 18.dp), colors = CardDefaults.cardColors(V2Panel2), shape = RoundedCornerShape(21.dp)) {
                Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(13.dp), verticalAlignment = Alignment.Top) {
                    AsyncImage(item.posterUrl.takeIf { it.isNotBlank() }, item.title, Modifier.width(112.dp).height(166.dp), contentScale = ContentScale.Crop)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(item.title, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                        Text("${item.year} · ${item.type.uppercase()}", color = V2Teal, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        if (item.overview.isNotBlank()) Text(item.overview.take(240), color = V2Muted, fontSize = 11.sp, lineHeight = 16.sp)
                        Text("${addonStore.addons().size} add-on(s) installed", color = V2Muted, fontSize = 10.sp)
                        OutlinedButton(onClick = {
                            isSaved = mediaLibrary.toggleWatchlist(item.toSavedMedia())
                        }) {
                            Icon(if (isSaved) Icons.Default.BookmarkAdded else Icons.Default.BookmarkAdd, null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(if (isSaved) "In watchlist" else "Add to watchlist", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        if (item.type != "movie") {
            item {
                Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Episodes", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    when {
                        guideLoading -> V2Loading()
                        guideError.isNotBlank() -> Text(guideError, color = V2Muted, fontSize = 12.sp)
                        seasons.isEmpty() -> Text("No episodes were returned for this show.", color = V2Muted, fontSize = 12.sp)
                        else -> {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                items(seasons) { s ->
                                    FilterChip(selected = season == s, onClick = { season = s; selectedEpisode = null }, label = { Text(if (s == 0) "Specials" else "Season $s") })
                                }
                            }
                            visibleEpisodes.forEach { episode ->
                                Card(
                                    onClick = {
                                        selectedEpisode = episode
                                        selectedResolution = "All"
                                        streamStatus = ""
                                        streams = emptyList()
                                        showStreamPicker = true
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(if (selectedEpisode?.id == episode.id) V2Panel2 else V2Panel),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(11.dp), verticalAlignment = Alignment.CenterVertically) {
                                        if (episode.thumbnail.isNotBlank()) AsyncImage(episode.thumbnail, episode.title, Modifier.size(104.dp, 60.dp), contentScale = ContentScale.Crop)
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Text("EPISODE ${episode.episode}", color = V2Teal, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            Text(episode.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                            if (episode.overview.isNotBlank()) Text(episode.overview.take(110), color = V2Muted, fontSize = 10.sp, maxLines = 2)
                                        }
                                        if (selectedEpisode?.id == episode.id) Icon(Icons.Default.PlayArrow, null, tint = V2Teal, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

    }

    if (showStreamPicker) {
        val resolutions = listOf("All", "2160p", "1080p", "720p", "480p", "Other")
        fun resolution(stream: StremioStream): String {
            val text = (stream.title + " " + stream.name).uppercase()
            return when {
                Regex("\\b(2160P|4K|UHD)\\b").containsMatchIn(text) -> "2160p"
                Regex("\\b(1080P|FHD)\\b").containsMatchIn(text) -> "1080p"
                Regex("\\b(720P|HD)\\b").containsMatchIn(text) -> "720p"
                Regex("\\b(480P|SD)\\b").containsMatchIn(text) -> "480p"
                else -> "Other"
            }
        }
        val filtered = streams.filter { selectedResolution == "All" || resolution(it) == selectedResolution }
        AlertDialog(
            onDismissRequest = { showStreamPicker = false },
            containerColor = V2Panel,
            title = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("CHOOSE A STREAM", color = V2Teal, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Text(selectedEpisode?.let { "S${it.season.toString().padStart(2, '0')}E${it.episode.toString().padStart(2, '0')} · ${it.title}" } ?: item.title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(Modifier.fillMaxWidth().height(400.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (streamLoading) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                CircularProgressIndicator(color = V2Teal)
                                Text("Loading streams from add-ons…", color = V2Muted, fontSize = 12.sp)
                            }
                        }
                    } else if (streams.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(streamStatus.ifBlank { "No streams found for this episode." }, color = V2Muted, fontSize = 12.sp)
                        }
                    } else {
                        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column(Modifier.width(86.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("QUALITY", color = V2Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                resolutions.forEach { quality ->
                                    val count = if (quality == "All") streams.size else streams.count { resolution(it) == quality }
                                    if (quality == "All" || count > 0) {
                                        Card(onClick = { selectedResolution = quality }, colors = CardDefaults.cardColors(if (selectedResolution == quality) V2Panel2 else V2Bg), shape = RoundedCornerShape(9.dp)) {
                                            Row(Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Text(quality, color = if (selectedResolution == quality) V2Teal else Color.White, fontSize = 10.sp, modifier = Modifier.weight(1f))
                                                Text(count.toString(), color = V2Muted, fontSize = 9.sp)
                                            }
                                        }
                                    }
                                }
                            }
                            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(filtered, key = { "${it.addonId}:${it.url}" }) { stream ->
                                    Card(onClick = {
                                        showStreamPicker = false
                                        val label = item.title + (selectedEpisode?.let { " · S${it.season}E${it.episode}" } ?: "") + " · " + stream.title
                                        onPlay(label, stream.url)
                                    }, colors = CardDefaults.cardColors(V2Panel2), shape = RoundedCornerShape(12.dp)) {
                                        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                            Text(resolution(stream), color = V2Teal, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                            Text(stream.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 4)
                                            Text(stream.name, color = V2Muted, fontSize = 10.sp, maxLines = 2)
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.PlayArrow, null, tint = V2Teal, modifier = Modifier.size(15.dp))
                                                Text("Play stream", color = V2Teal, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                                if (filtered.isEmpty()) item { Text("No streams at this resolution.", color = V2Muted, fontSize = 11.sp) }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showStreamPicker = false }) { Text("Close", color = V2Teal) } }
        )
    }
}

private fun fetchV2Episodes(imdbId: String, addonStore: StremioAddonStore): List<V2Episode> {
    require(imdbId.matches(Regex("^tt\\d+$"))) { "This show has no valid IMDb ID for its episode guide." }
    val root = addonStore.metaResponse("series", imdbId)
        ?: fetchV2Json("https://v3-cinemeta.strem.io/meta/series/" + Uri.encode(imdbId) + ".json")
    val meta = root.optJSONObject("meta") ?: return emptyList()
    val videos = meta.optJSONArray("videos") ?: JSONArray()
    return (0 until videos.length()).mapNotNull { i ->
        val v = videos.optJSONObject(i) ?: return@mapNotNull null
        val season = v.optInt("season", -1)
        val episode = v.optInt("episode", -1)
        if (season < 0 || episode < 1) return@mapNotNull null
        V2Episode(
            season = season,
            episode = episode,
            title = v.optString("title").ifBlank { "Episode $episode" },
            id = v.optString("id").ifBlank { "$imdbId:$season:$episode" },
            overview = v.optString("overview"),
            thumbnail = v.optString("thumbnail").takeIf { it.startsWith("http") }.orEmpty()
        )
    }.distinctBy { "${it.season}:${it.episode}" }.sortedWith(compareBy<V2Episode> { it.season }.thenBy { it.episode })
}

private fun fetchV2Catalog(type: String): List<V2Title> {
    val native = if (type == "movie") "movie" else "series"
    val root = fetchV2Json("https://v3-cinemeta.strem.io/catalog/$native/imdbRating.json")
    return root.optJSONArray("metas").toV2Titles(native)
}

private fun fetchV2Search(query: String): List<V2Title> {
    val root = fetchV2Json("https://v3.sg.media-imdb.com/suggestion/x/${URLEncoder.encode(query.trim(), "UTF-8")}.json")
    val rows = root.optJSONArray("d") ?: return emptyList()
    return (0 until rows.length()).mapNotNull { i ->
        val row = rows.optJSONObject(i) ?: return@mapNotNull null
        val id = row.optString("id").takeIf { it.startsWith("tt") } ?: return@mapNotNull null
        val q = row.optString("q").lowercase()
        V2Title(id, if (q in listOf("movie", "feature", "short")) "movie" else "series", row.optString("l"), row.optString("y"), row.optString("s"), row.optString("i").takeIf { it.startsWith("http") }.orEmpty())
    }.distinctBy { it.id }.take(30)
}

private fun fetchV2Json(endpoint: String): JSONObject {
    val c = URL(endpoint).openConnection() as HttpURLConnection
    c.requestMethod = "GET"
    c.connectTimeout = 12000
    c.readTimeout = 18000
    c.setRequestProperty("Accept", "application/json")
    c.setRequestProperty("User-Agent", "DebridMediaCenter-Streaming/1.1")
    try {
        val code = c.responseCode
        val body = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        if (code !in 200..299) throw IllegalStateException("Metadata service returned HTTP $code.")
        return JSONObject(body)
    } finally { c.disconnect() }
}

private fun JSONArray?.toV2Titles(type: String): List<V2Title> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { i ->
        val row = optJSONObject(i) ?: return@mapNotNull null
        val id = row.optString("imdb_id").takeIf { it.startsWith("tt") } ?: row.optString("id").takeIf { it.startsWith("tt") } ?: return@mapNotNull null
        val title = row.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        V2Title(id, type, title, row.optString("releaseInfo").ifBlank { row.optString("year") }, row.optString("description"), row.optString("poster").takeIf { it.startsWith("http") }.orEmpty())
    }.distinctBy { it.id }.take(40)
}

@Composable
private fun V2Loading() {
    Row(Modifier.fillMaxWidth().padding(25.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(22.dp), color = V2Teal, strokeWidth = 2.dp)
        Spacer(Modifier.size(9.dp))
        Text("Loading…", color = V2Muted, fontSize = 12.sp)
    }
}

@Composable
private fun V2Player(title: String, url: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val mediaLibrary = remember { V2MediaLibrary(context.applicationContext) }
    var error by remember(url) { mutableStateOf("") }
    var playbackSpeed by remember(url) { mutableFloatStateOf(1f) }
    val exoPlayer = remember(url) {
        androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
            setMediaItem(androidx.media3.common.MediaItem.fromUri(Uri.parse(url)))
            playWhenReady = true
            prepare()
        }
    }
    LaunchedEffect(exoPlayer, url) {
        val savedPosition = mediaLibrary.position(url)
        if (savedPosition > 10_000L) exoPlayer.seekTo(savedPosition)
        while (true) {
            delay(5000)
            val duration = exoPlayer.duration
            val position = exoPlayer.currentPosition
            mediaLibrary.savePosition(url, if (duration > 0 && position >= duration - 30_000L) 0L else position)
        }
    }
    DisposableEffect(exoPlayer, url) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPlayerError(e: androidx.media3.common.PlaybackException) { error = e.message ?: e.errorCodeName }
        }
        exoPlayer.addListener(listener)
        onDispose {
            val duration = exoPlayer.duration
            val position = exoPlayer.currentPosition
            mediaLibrary.savePosition(url, if (duration > 0 && position >= duration - 30_000L) 0L else position)
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }
    Column(Modifier.fillMaxSize().background(Color.Black).windowInsetsPadding(WindowInsets.statusBars)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            DmcBrandMark(34); Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("NOW PLAYING", color = V2Teal, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                Text(title, color = Color.White, fontSize = 12.sp, maxLines = 1)
            }
            TextButton(onClick = onClose) { Text("Done", color = V2Teal) }
        }
        if (error.isNotBlank()) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Playback couldn't start", color = Color(0xFFFF9B9B), fontWeight = FontWeight.Bold)
                Text(error, color = V2Muted, fontSize = 12.sp)
                TextButton(onClick = { error = ""; exoPlayer.prepare(); exoPlayer.playWhenReady = true }) { Text("Retry", color = V2Teal) }
            }
        }
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { PlayerView(it).apply { this.player = exoPlayer; useController = true; controllerAutoShow = true } },
            update = { it.player = exoPlayer },
            modifier = Modifier.fillMaxWidth().weight(1f)
        )
        Column(Modifier.fillMaxWidth().background(V2Panel).padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { exoPlayer.seekTo((exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Replay10, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("10 sec")
                }
                OutlinedButton(onClick = { exoPlayer.seekTo((exoPlayer.currentPosition + 10_000L).coerceAtMost(exoPlayer.duration.takeIf { it > 0 } ?: Long.MAX_VALUE)) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Forward10, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("10 sec")
                }
                OutlinedButton(onClick = { TrackSelectionDialogBuilder(context, "Audio track", exoPlayer, C.TRACK_TYPE_AUDIO).build().show() }, modifier = Modifier.weight(1f)) { Text("Audio", fontSize = 11.sp) }
                OutlinedButton(onClick = { TrackSelectionDialogBuilder(context, "Subtitles", exoPlayer, C.TRACK_TYPE_TEXT).build().show() }, modifier = Modifier.weight(1f)) { Text("Subtitles", fontSize = 11.sp) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Speed", color = V2Muted, fontSize = 10.sp)
                listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                    FilterChip(selected = playbackSpeed == speed, onClick = { playbackSpeed = speed; exoPlayer.setPlaybackSpeed(speed) },
                        label = { Text(if (speed == 1f) "1×" else speed.toString() + "×", fontSize = 10.sp) })
                }
            }
        }
    }
}
