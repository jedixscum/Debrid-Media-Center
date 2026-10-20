package com.debridmediacenter.android

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Link
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import androidx.media3.common.C
import androidx.media3.ui.TrackSelectionDialogBuilder
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private val Bg = Color(0xFF080E14)
private val Panel = Color(0xFF101922)
private val Panel2 = Color(0xFF14232C)
private val Teal = Color(0xFF58E1CF)
private val Muted = Color(0xFF91A5AE)

@Composable
fun DebridMediaCenterApp() {
    val context = LocalContext.current
    val store = remember { ProviderStore(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val collectionStore = remember { MediaCollectionStore(context.applicationContext) }
    var tab by remember { mutableIntStateOf(0) }
    var playerUrl by remember { mutableStateOf<String?>(null) }
    var playerItem by remember { mutableStateOf<MediaSearchResult?>(null) }
    var resumePositionMs by remember { mutableStateOf(0L) }
    var detailsItem by remember { mutableStateOf<MediaSearchResult?>(null) }
    var pendingSelection by remember { mutableStateOf<MediaSearchResult?>(null) }

    MaterialTheme(colorScheme = darkColorScheme(primary = Teal, background = Bg, surface = Panel)) {
        Surface(Modifier.fillMaxSize(), color = Bg, contentColor = Color.White) {
            when {
                playerUrl != null -> DmcPlayer(
                    url = playerUrl!!,
                    item = playerItem ?: MediaSearchResult("direct", "direct", "Now playing", "", ""),
                    collectionStore = collectionStore,
                    initialPositionMs = resumePositionMs,
                    onClose = { playerUrl = null; playerItem = null; resumePositionMs = 0L }
                )
                detailsItem != null -> MediaDetailsScreen(
                    item = detailsItem!!,
                    collectionStore = collectionStore,
                    onBack = { detailsItem = null },
                    onFindReleases = { selected ->
                        pendingSelection = selected
                        detailsItem = null
                        tab = 1
                    }
                )
                else -> {
                    Column(Modifier.fillMaxSize()) {
                        Column(Modifier.weight(1f).fillMaxWidth()) {
                            when (tab) {
                                0 -> HomeScreen(
                                    store = store,
                                    collectionStore = collectionStore,
                                    onNavigate = { tab = it },
                                    onOpenDetails = { detailsItem = it },
                                    onResume = { entry ->
                                        val savedUrl = collectionStore.playbackUrl(entry)
                                        if (savedUrl != null) {
                                            playerItem = entry.item
                                            resumePositionMs = entry.positionMs
                                            playerUrl = savedUrl
                                        }
                                    }
                                )
                                1 -> DiscoverScreen(
                                    store = store,
                                    scope = scope,
                                    initialSelection = pendingSelection,
                                    onSelectionConsumed = { pendingSelection = null },
                                    onDetails = { detailsItem = it },
                                    onPlay = { url, item ->
                                        playerItem = item
                                        resumePositionMs = 0L
                                        playerUrl = url
                                    }
                                )
                                2 -> LibraryScreen(store, scope, onPlay = { url, item ->
                                    playerItem = item
                                    resumePositionMs = 0L
                                    playerUrl = url
                                })
                                else -> SettingsScreen(store, scope, collectionStore) { destination -> tab = destination }
                            }
                        }
                        NavigationBar(containerColor = Panel) {
                            val nav = listOf(
                                "Home" to Icons.Default.Home,
                                "Discover" to Icons.Default.Search,
                                "Library" to Icons.Default.Folder,
                                "Settings" to Icons.Default.Settings
                            )
                            nav.forEachIndexed { index, item ->
                                NavigationBarItem(
                                    selected = tab == index,
                                    onClick = { tab = index },
                                    icon = { Icon(item.second, contentDescription = item.first) },
                                    label = { Text(item.first, fontSize = 9.sp) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(title: String, subtitle: String) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 22.dp)) {
        Text("DEBRID MEDIA CENTER", color = Teal, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Spacer(Modifier.height(6.dp))
        Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun HomeScreen(store: ProviderStore, collectionStore: MediaCollectionStore, onNavigate: (Int) -> Unit, onOpenDetails: (MediaSearchResult) -> Unit, onResume: (WatchHistoryEntry) -> Unit) {
    var movies by remember { mutableStateOf<List<MediaSearchResult>>(emptyList()) }
    var shows by remember { mutableStateOf<List<MediaSearchResult>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val catalog = withContext(Dispatchers.IO) {
            val movieRows = runCatching { fetchCatalogTitles("movie", "imdbRating") }.getOrDefault(emptyList())
            val showRows = runCatching { fetchCatalogTitles("series", "imdbRating") }.getOrDefault(emptyList())
            movieRows to showRows
        }
        movies = catalog.first
        shows = catalog.second
        loading = false
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        item {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier.size(34.dp).background(Teal.copy(alpha = 0.13f), RoundedCornerShape(11.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Movie, contentDescription = null, tint = Teal, modifier = Modifier.size(21.dp))
                    }
                    Column {
                        Text("DEBRID MEDIA CENTER", color = Teal, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Text("Your personal cinema", color = Muted, fontSize = 11.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { onNavigate(3) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Muted)
                    }
                }
                Spacer(Modifier.height(22.dp))
                Text("What are we watching?", fontSize = 27.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("Discover something great and play it your way.", color = Muted, fontSize = 13.sp)
            }
        }
        item {
            val featured = movies.firstOrNull() ?: shows.firstOrNull()
            Card(
                onClick = { featured?.let(onOpenDetails) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                colors = CardDefaults.cardColors(containerColor = Panel2),
                shape = RoundedCornerShape(24.dp)
            ) {
                Box {
                    Box(
                        Modifier.matchParentSize().background(
                            Brush.horizontalGradient(listOf(Color(0xFF173A40), Panel2, Panel))
                        )
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(18.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (featured?.posterUrl?.isNotBlank() == true) {
                            AsyncImage(
                                model = featured.posterUrl,
                                contentDescription = featured.title,
                                modifier = Modifier.size(112.dp, 158.dp).background(Panel, RoundedCornerShape(14.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                Modifier.size(112.dp, 158.dp).background(Panel, RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Movie, contentDescription = null, tint = Teal, modifier = Modifier.size(38.dp))
                            }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("TONIGHT'S SPOTLIGHT", color = Teal, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                            Text(featured?.title ?: "Your next favorite", fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
                            Text(
                                featured?.let { "${it.year.ifBlank { "Featured pick" }} · Curated for discovery" }
                                    ?: "Explore movies and series, then find a release from your provider.",
                                color = Muted, fontSize = 11.sp, lineHeight = 16.sp
                            )
                            Button(
                                onClick = { featured?.let(onOpenDetails) },
                                enabled = featured != null,
                                colors = ButtonDefaults.buttonColors(containerColor = Teal, contentColor = Bg),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.size(5.dp))
                                Text("Explore titles", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
        item {
            val history = collectionStore.continueWatching()
            if (history.isNotEmpty()) HomeHistoryRow(history, onResume)
        }
        item {
            HomeCollectionRow("Your watchlist", collectionStore.watchlist(), onOpenDetails)
        }
        item {
            HomeCatalogRow("Popular movies", movies, loading, onSeeAll = { onNavigate(1) }, onOpenDetails = onOpenDetails)
        }
        item {
            HomeCatalogRow("Popular series", shows, loading, onSeeAll = { onNavigate(1) }, onOpenDetails = onOpenDetails)
        }
        item {
            Card(
                onClick = { onNavigate(3) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                colors = CardDefaults.cardColors(containerColor = Panel),
                shape = RoundedCornerShape(18.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(Modifier.size(42.dp).background(Teal.copy(alpha = 0.12f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Cloud, contentDescription = null, tint = Teal)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Your streaming services", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        val connected = listOf(
                            store.hasToken(ProviderStore.REAL_DEBRID),
                            store.hasToken(ProviderStore.TORBOX)
                        ).count { it }
                        Text(
                            when (connected) {
                                2 -> "Real-Debrid and TorBox keys are saved"
                                1 -> "1 provider key saved · finish setup anytime"
                                else -> "Connect Real-Debrid or TorBox to get started"
                            },
                            color = Muted, fontSize = 11.sp
                        )
                    }
                    Icon(Icons.Default.Link, contentDescription = "Manage services", tint = Teal, modifier = Modifier.size(19.dp))
                }
            }
        }
        item {
            OutlinedButton(
                onClick = { onNavigate(1) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                contentPadding = PaddingValues(vertical = 14.dp)
            ) {
                Icon(Icons.Default.Search, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Search all movies and shows")
            }
        }
    }
}

@Composable
private fun HomeHistoryRow(entries: List<WatchHistoryEntry>, onResume: (WatchHistoryEntry) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.padding(horizontal = 18.dp)) {
            Text("Continue watching", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Pick up where you left off", color = Muted, fontSize = 11.sp)
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(entries.take(12), key = { "${it.item.type}:${it.item.id}" }) { entry ->
                Card(
                    onClick = { onResume(entry) },
                    modifier = Modifier.width(154.dp),
                    colors = CardDefaults.cardColors(containerColor = Panel),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Box {
                            AsyncImage(
                                model = entry.item.posterUrl.takeIf { it.isNotBlank() },
                                contentDescription = entry.item.title,
                                modifier = Modifier.fillMaxWidth().height(126.dp),
                                contentScale = ContentScale.Crop
                            )
                            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Panel2)) {
                                val progress = if (entry.durationMs > 0L) (entry.positionMs.toFloat() / entry.durationMs).coerceIn(0f, 1f) else 0f
                                Box(Modifier.fillMaxWidth(progress).height(4.dp).background(Teal))
                            }
                        }
                        Column(Modifier.padding(horizontal = 10.dp, vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(entry.item.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                            Text("Resume playback  ·  ▶", color = Teal, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeCollectionRow(
    title: String,
    entries: List<MediaSearchResult>,
    onOpenDetails: (MediaSearchResult) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(if (entries.isEmpty()) "Save titles to keep them close" else "${entries.size} saved title${if (entries.size == 1) "" else "s"}", color = Muted, fontSize = 11.sp)
            }
        }
        if (entries.isEmpty()) {
            Text("Your saved titles will appear here.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 18.dp))
        } else {
            LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(entries.take(20), key = { "${it.type}:${it.id}" }) { entry ->
                    Column(Modifier.width(116.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Card(
                            onClick = { onOpenDetails(entry) },
                            colors = CardDefaults.cardColors(containerColor = Panel2),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            AsyncImage(
                                model = entry.posterUrl.takeIf { it.isNotBlank() },
                                contentDescription = entry.title,
                                modifier = Modifier.fillMaxWidth().height(166.dp),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Text(entry.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, lineHeight = 15.sp)
                        Text(entry.year.ifBlank { entry.type.uppercase() }, color = Muted, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeCatalogRow(
    title: String,
    items: List<MediaSearchResult>,
    loading: Boolean,
    onSeeAll: () -> Unit,
    onOpenDetails: (MediaSearchResult) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("A few picks to get you started", color = Muted, fontSize = 11.sp)
            }
            Text(
                "SEE ALL  ›",
                color = Teal,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(4.dp)
                    .clickable(onClick = onSeeAll)
            )
        }
        if (items.isEmpty()) {
            Text(
                if (loading) "Loading titles…" else "Catalog unavailable right now. Try Discover to browse again.",
                color = Muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
            )
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items.take(12), key = { it.id }) { item ->
                    Column(
                        Modifier.width(116.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Card(
                            onClick = { onOpenDetails(item) },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Panel2)
                        ) {
                            AsyncImage(
                                model = item.posterUrl.takeIf { it.isNotBlank() },
                                contentDescription = item.title,
                                modifier = Modifier.fillMaxWidth().height(166.dp),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Text(item.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, lineHeight = 15.sp)
                        Text(item.year.ifBlank { "Movie & TV" }, color = Muted, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeCard(title: String, body: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp), colors = CardDefaults.cardColors(Panel), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(icon, null, tint = Teal, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Teal, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(Modifier.height(4.dp))
                Text(body, color = Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun DiscoverScreen(store: ProviderStore, scope: CoroutineScope, initialSelection: MediaSearchResult?, onSelectionConsumed: () -> Unit, onDetails: (MediaSearchResult) -> Unit, onPlay: (String, MediaSearchResult) -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<MediaSearchResult>>(emptyList()) }
    var selected by remember { mutableStateOf<MediaSearchResult?>(null) }
    var releases by remember { mutableStateOf<List<ReleaseSearchResult>>(emptyList()) }
    var provider by remember { mutableStateOf(ProviderStore.REAL_DEBRID) }
    var quality by remember { mutableStateOf("best") }
    var status by remember { mutableStateOf("") }
    var magnet by remember { mutableStateOf("") }
    var directUrl by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var activeTool by remember { mutableStateOf<String?>(null) }
    var contentType by remember { mutableStateOf("movie") }
    var searchType by remember { mutableStateOf("All") }
    var selectedGenre by remember { mutableStateOf("All") }
    var featured by remember { mutableStateOf<List<MediaSearchResult>>(emptyList()) }
    var recent by remember { mutableStateOf<List<MediaSearchResult>>(emptyList()) }
    var genreTitles by remember { mutableStateOf<List<MediaSearchResult>>(emptyList()) }
    var catalogBusy by remember { mutableStateOf(true) }
    var catalogError by remember { mutableStateOf("") }
    val genres = listOf("All", "Action", "Adventure", "Animation", "Comedy", "Crime", "Documentary", "Drama", "Family", "Fantasy", "Horror", "Mystery", "Romance", "Sci-Fi", "Thriller")

    LaunchedEffect(contentType, selectedGenre) {
        catalogBusy = true
        catalogError = ""
        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                val hero = runCatching { fetchCatalogTitles(contentType, "imdbRating") }.getOrDefault(emptyList())
                val latest = runCatching { fetchCatalogTitles(contentType, "year", "genre", java.util.Calendar.getInstance().get(java.util.Calendar.YEAR).toString()) }
                    .getOrElse { emptyList() }
                    .ifEmpty { fetchCatalogTitles(contentType, "top") }
                val genreItems = if (selectedGenre == "All") latest else fetchCatalogTitles(contentType, "top", "genre", selectedGenre)
                Triple(hero.ifEmpty { latest }, latest, genreItems)
            }
        }
        loaded.onSuccess {
            featured = it.first
            recent = it.second
            genreTitles = it.third
            if (it.second.isEmpty()) catalogError = "No catalog titles were returned. Pull down later to retry."
        }.onFailure {
            catalogError = it.message ?: "Couldn't load the discovery catalog."
            featured = emptyList()
            recent = emptyList()
            genreTitles = emptyList()
        }
        catalogBusy = false
    }

    fun chooseTitle(item: MediaSearchResult) {
        selected = item
        query = item.title
        activeTool = "search"
        releases = emptyList()
        status = "Selected ${item.title}. Choose a provider and find releases."
    }

    LaunchedEffect(initialSelection) {
        initialSelection?.let {
            chooseTitle(it)
            onSelectionConsumed()
        }
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 28.dp)) {
        item { Header("Discover", "Find something worth watching.") }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ToolIcon("Search", Icons.Default.Search, activeTool == "search", Modifier.weight(1f)) { activeTool = if (activeTool == "search") null else "search" }
                ToolIcon("Magnet", Icons.Default.Link, activeTool == "magnet", Modifier.weight(1f)) { activeTool = if (activeTool == "magnet") null else "magnet" }
                ToolIcon("Player", Icons.Default.PlayArrow, activeTool == "player", Modifier.weight(1f)) { activeTool = if (activeTool == "player") null else "player" }
            }
        }
        if (activeTool == "search") {
            item {
                Card(Modifier.padding(horizontal = 18.dp).fillMaxWidth(), colors = CardDefaults.cardColors(Panel), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("SEARCH TITLES", color = Teal, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(query, { query = it }, label = { Text("Movie or show") }, modifier = Modifier.weight(1f), singleLine = true)
                            Button(enabled = !busy && query.trim().length >= 2, onClick = {
                                busy = true
                                status = "Searching…"
                                selected = null
                                releases = emptyList()
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) { runCatching { searchTitles(query) } }
                                    busy = false
                                    result.onSuccess { results = it; status = if (it.isEmpty()) "No matching titles found." else "${it.size} titles found." }
                                        .onFailure { results = emptyList(); status = it.message ?: "Search failed." }
                                }
                            }) { Icon(Icons.Default.Search, contentDescription = "Search") }
                        }
                        if (status.isNotBlank()) Text(status, color = Muted, fontSize = 11.sp)
                    }
                }
            }
            if (selected == null) {
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("All", "Movies", "TV Shows").forEach { kind ->
                            FilterChip(selected = searchType == kind, onClick = { searchType = kind }, label = { Text(kind) })
                        }
                    }
                }
                items(results.filter { searchType == "All" || (searchType == "Movies" && it.type == "movie") || (searchType == "TV Shows" && it.type != "movie") }, key = { it.id }) { item -> MediaResultCard(item) { onDetails(item) } }
            } else {
                item { SelectedTitleCard(selected!!) { selected = null; releases = emptyList() } }
                item {
                    Card(Modifier.padding(horizontal = 18.dp).fillMaxWidth(), colors = CardDefaults.cardColors(Panel2), shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("RELEASE SEARCH", color = Teal, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(provider == ProviderStore.REAL_DEBRID, { provider = ProviderStore.REAL_DEBRID }, label = { Text("Real-Debrid") })
                                FilterChip(provider == ProviderStore.TORBOX, { provider = ProviderStore.TORBOX }, label = { Text("TorBox") })
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("best", "2160p", "1080p", "720p").forEach { q -> FilterChip(quality == q, { quality = q }, label = { Text(q) }) }
                            }
                            Button(enabled = !busy, onClick = {
                                busy = true
                                status = "Searching releases…"
                                scope.launch {
                                    val item = selected!!
                                    val result = withContext(Dispatchers.IO) { runCatching { store.searchReleases(item.id, item.type, quality) } }
                                    busy = false
                                    result.onSuccess { releases = it; status = if (it.isEmpty()) "No releases returned." else "${it.size} releases found." }
                                        .onFailure { status = it.message ?: "Release search failed." }
                                }
                            }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Teal, contentColor = Bg)) {
                                Text(if (busy) "Searching…" else "Find releases", fontWeight = FontWeight.Bold)
                            }
                            Text(status, color = Muted, fontSize = 11.sp)
                        }
                    }
                }
                items(releases, key = { it.hash }) { release -> ReleaseCard(release, provider, store, scope) { status = it } }
            }
        } else if (activeTool == "magnet") {
            item {
                Card(Modifier.padding(horizontal = 18.dp).fillMaxWidth(), colors = CardDefaults.cardColors(Panel), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("ADD A MAGNET", color = Teal, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(provider == ProviderStore.REAL_DEBRID, { provider = ProviderStore.REAL_DEBRID }, label = { Text("Real-Debrid") })
                            FilterChip(provider == ProviderStore.TORBOX, { provider = ProviderStore.TORBOX }, label = { Text("TorBox") })
                        }
                        OutlinedTextField(magnet, { magnet = it }, label = { Text("Magnet link") }, placeholder = { Text("magnet:?xt=urn:btih:…") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Button(onClick = {
                            busy = true
                            scope.launch {
                                val result = withContext(Dispatchers.IO) { runCatching { store.addMagnet(provider, magnet) } }
                                busy = false
                                result.onSuccess { status = it; magnet = "" }.onFailure { status = it.message ?: "Could not add magnet." }
                            }
                        }, enabled = !busy && magnet.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Adding…" else "Add to provider") }
                        if (status.isNotBlank()) Text(status, color = Muted, fontSize = 11.sp)
                    }
                }
            }
        } else if (activeTool == "player") {
            item {
                Card(Modifier.padding(horizontal = 18.dp).fillMaxWidth(), colors = CardDefaults.cardColors(Panel2), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("BUILT-IN PLAYER", color = Teal, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("Paste a direct media link, or play an item from your provider library.", color = Muted, fontSize = 12.sp)
                        OutlinedTextField(directUrl, { directUrl = it }, label = { Text("Direct media URL") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Button(onClick = { val url = directUrl.trim(); onPlay(url, MediaSearchResult("direct:${url.hashCode()}", "direct", url.substringAfterLast('/').take(60).ifBlank { "Direct media" }, "", "")) }, enabled = (directUrl.startsWith("https://", true) || directUrl.startsWith("http://", true)), modifier = Modifier.fillMaxWidth()) { Text("Play") }
                    }
                }
            }
        }

        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Featured", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text("Highly rated picks to get you started", color = Muted, fontSize = 12.sp)
            }
        }
        if (catalogBusy && featured.isEmpty()) {
            item { BoxLoading() }
        } else if (featured.isNotEmpty()) {
            item { FeaturedHero(featured.first(), onDetails) }
        }

        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Browse by genre", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(genres, key = { it }) { genre ->
                        FilterChip(selected = selectedGenre == genre, onClick = { selectedGenre = genre }, label = { Text(genre) })
                    }
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (selectedGenre == "All") "Popular picks" else "$selectedGenre picks", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Choose a title to search its available releases", color = Muted, fontSize = 12.sp)
            }
        }
        if (catalogBusy && genreTitles.isEmpty()) {
            item { BoxLoading() }
        } else if (genreTitles.isNotEmpty()) {
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(genreTitles, key = { "genre-${it.id}" }) { item -> PosterTile(item) { onDetails(item) } }
                }
            }
        } else {
            item { Text("No titles in this genre right now.", color = Muted, modifier = Modifier.padding(horizontal = 18.dp)) }
        }

        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (contentType == "movie") "New movies" else "New TV shows", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(contentType == "movie", { contentType = "movie" }, label = { Text("Movies") })
                    FilterChip(contentType == "series", { contentType = "series" }, label = { Text("TV Shows") })
                }
            }
        }
        if (catalogBusy && recent.isEmpty()) {
            item { BoxLoading() }
        } else if (recent.isNotEmpty()) {
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(recent, key = { "recent-${it.id}" }) { item -> PosterTile(item) { onDetails(item) } }
                }
            }
        } else {
            item { Text(catalogError.ifBlank { "Recent titles are unavailable." }, color = Muted, modifier = Modifier.padding(horizontal = 18.dp)) }
        }
        if (catalogError.isNotBlank()) item { Text(catalogError, color = Muted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 18.dp)) }
    }
}

@Composable
private fun FeaturedHero(item: MediaSearchResult, onSelect: (MediaSearchResult) -> Unit) {
    Card(
        onClick = { onSelect(item) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
        colors = CardDefaults.cardColors(containerColor = Panel2),
        shape = RoundedCornerShape(22.dp)
    ) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = item.posterUrl,
                contentDescription = item.title,
                modifier = Modifier.size(126.dp, 184.dp),
                contentScale = ContentScale.Crop
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("EDITOR'S PICK", color = Teal, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Text(item.title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("${item.year.ifBlank { "Featured" }} · ${item.type.uppercase()}", color = Muted, fontSize = 11.sp)
                if (item.overview.isNotBlank()) Text(item.overview.take(150), color = Muted, fontSize = 11.sp)
                Button(onClick = { onSelect(item) }, colors = ButtonDefaults.buttonColors(containerColor = Teal, contentColor = Bg)) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(5.dp))
                    Text("Find releases")
                }
            }
        }
    }
}

@Composable
private fun PosterTile(item: MediaSearchResult, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.size(width = 132.dp, height = 238.dp),
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AsyncImage(
                model = item.posterUrl,
                contentDescription = item.title,
                modifier = Modifier.fillMaxWidth().height(174.dp),
                contentScale = ContentScale.Crop
            )
            Column(Modifier.padding(horizontal = 9.dp, vertical = 3.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(item.title, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                Text("${item.year.ifBlank { item.type.uppercase() }}", color = Teal, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun ToolIcon(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier, colors = CardDefaults.cardColors(containerColor = if (selected) Panel2 else Panel), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 13.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(icon, contentDescription = label, tint = if (selected) Teal else Color.White, modifier = Modifier.size(24.dp))
            Text(label, color = if (selected) Teal else Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun BoxLoading() {
    Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(color = Teal, modifier = Modifier.size(24.dp))
        Spacer(Modifier.size(10.dp))
        Text("Loading recent titles…", color = Muted, fontSize = 12.sp)
    }
}

private fun fetchCatalogTitles(
    type: String,
    catalogId: String,
    extraName: String? = null,
    extraValue: String? = null
): List<MediaSearchResult> {
    val nativeType = if (type == "series") "series" else "movie"
    val extraPath = if (extraName != null && extraValue != null) {
        "/$extraName=${URLEncoder.encode(extraValue, "UTF-8").replace("+", "%20")}"
    } else ""
    val endpoint = "https://v3-cinemeta.strem.io/catalog/$nativeType/$catalogId$extraPath.json"
    val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 12000
        readTimeout = 15000
        setRequestProperty("Accept", "application/json")
        setRequestProperty("User-Agent", "DebridMediaCenter/0.7")
    }
    try {
        val code = connection.responseCode
        val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        if (code !in 200..299) throw IllegalStateException("Discovery service returned HTTP $code.")
        val metas = JSONObject(body).optJSONArray("metas") ?: return emptyList()
        val output = mutableListOf<MediaSearchResult>()
        for (i in 0 until metas.length()) {
            val row = metas.optJSONObject(i) ?: continue
            val id = row.optString("imdb_id").takeIf { it.startsWith("tt") }
                ?: row.optString("id").takeIf { it.startsWith("tt") } ?: continue
            val title = row.optString("name").takeIf { it.isNotBlank() } ?: continue
            val year = row.optString("releaseInfo").ifBlank { row.optString("year") }
            val poster = row.optString("poster").takeIf { it.startsWith("http") }.orEmpty()
            val overview = row.optString("description")
            output.add(MediaSearchResult(id, if (nativeType == "movie") "movie" else "tv", title, year, overview, poster))
        }
        return output.distinctBy { it.id }.take(40)
    } finally {
        connection.disconnect()
    }
}

@Composable
private fun MediaResultCard(item: MediaSearchResult, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.padding(horizontal = 18.dp).fillMaxWidth(), colors = CardDefaults.cardColors(Panel), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (item.posterUrl.isNotBlank()) {
                AsyncImage(model = item.posterUrl, contentDescription = item.title, modifier = Modifier.size(76.dp, 112.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(item.title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("${item.type.uppercase()} · ${item.year.ifBlank { "Unknown year" }}", color = Teal, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                if (item.overview.isNotBlank()) Text(item.overview.take(180), color = Muted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun SelectedTitleCard(item: MediaSearchResult, onBack: () -> Unit) {
    Card(Modifier.padding(horizontal = 18.dp).fillMaxWidth(), colors = CardDefaults.cardColors(Panel), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (item.posterUrl.isNotBlank()) AsyncImage(model = item.posterUrl, contentDescription = item.title, modifier = Modifier.size(88.dp, 130.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(item.title, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text("${item.type.uppercase()} · ${item.year}", color = Teal, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(item.overview.take(260), color = Muted, fontSize = 11.sp)
                OutlinedButton(onClick = onBack) { Text("Back to results") }
            }
        }
    }
}

@Composable
private fun ReleaseCard(release: ReleaseSearchResult, provider: String, store: ProviderStore, scope: CoroutineScope, onStatus: (String) -> Unit) {
    var busy by remember { mutableStateOf(false) }
    Card(Modifier.padding(horizontal = 18.dp).fillMaxWidth(), colors = CardDefaults.cardColors(Panel), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(release.title, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("${release.sizeMb.toInt()} MB · ${release.hash.take(10)}…", color = Muted, fontSize = 10.sp)
            Button(
                enabled = !busy,
                onClick = {
                    busy = true
                    val magnet = "magnet:?xt=urn:btih:${release.hash}&dn=${URLEncoder.encode(release.title, "UTF-8")}"
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { runCatching { store.addMagnet(provider, magnet) } }
                        busy = false
                        result.onSuccess { onStatus(it) }.onFailure { onStatus(it.message ?: "Could not add release.") }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Teal, contentColor = Bg)
            ) { Text(if (busy) "Adding…" else "Add to ${if (provider == ProviderStore.REAL_DEBRID) "Real-Debrid" else "TorBox"}") }
        }
    }
}

private fun searchTitles(query: String): List<MediaSearchResult> {
    val encoded = URLEncoder.encode(query.trim(), "UTF-8")
    val connection = (URL("https://v3.sg.media-imdb.com/suggestion/x/$encoded.json").openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 12000
        readTimeout = 12000
        setRequestProperty("Accept", "application/json")
        setRequestProperty("User-Agent", "DebridMediaCenter/0.5")
    }
    try {
        val code = connection.responseCode
        val body = (if (code in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        if (code !in 200..299) throw IllegalStateException("Title search failed (HTTP $code).")
        val root = JSONObject(body)
        val rows = root.optJSONArray("d") ?: return emptyList()
        val output = mutableListOf<MediaSearchResult>()
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            val id = row.optString("id").takeIf { it.startsWith("tt") } ?: continue
            val title = row.optString("l").takeIf { it.isNotBlank() } ?: continue
            val type = when (row.optString("q").lowercase()) {
                "feature", "movie", "short" -> "movie"
                else -> "tv"
            }
            val year = row.optString("y")
            val poster = row.optString("i").let { raw -> if (raw.startsWith("http")) raw else "" }
            val stars = row.optString("s")
            output.add(MediaSearchResult(id, type, title, year, stars, poster))
        }
        return output.distinctBy { it.id }.take(20)
    } finally {
        connection.disconnect()
    }
}

@Composable
private fun LibraryScreen(
    store: ProviderStore,
    scope: CoroutineScope,
    onPlay: (String, MediaSearchResult) -> Unit
) {
    var items by remember { mutableStateOf<List<ProviderLibraryItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }
    var playingId by remember { mutableStateOf<String?>(null) }

    fun loadLibrary() {
        if (loading) return
        loading = true
        error = ""
        notice = ""
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                val connectedProviders = listOf(ProviderStore.REAL_DEBRID, ProviderStore.TORBOX)
                    .filter { store.hasToken(it) }
                if (connectedProviders.isEmpty()) {
                    return@withContext Result.success(emptyList<ProviderLibraryItem>())
                }
                val combined = mutableListOf<ProviderLibraryItem>()
                val failures = mutableListOf<String>()
                connectedProviders.forEach { provider ->
                    runCatching { store.fetchLibrary(provider) }
                        .onSuccess { combined.addAll(it) }
                        .onFailure { failures.add("${if (provider == ProviderStore.TORBOX) "TorBox" else "Real-Debrid"}: ${it.message ?: "request failed"}") }
                }
                if (combined.isEmpty() && failures.isNotEmpty()) {
                    Result.failure<List<ProviderLibraryItem>>(IllegalStateException(failures.joinToString("\n")))
                } else {
                    Result.success(combined.toList()).also {
                        if (failures.isNotEmpty()) error = failures.joinToString("\n")
                    }
                }
            }
            outcome.onSuccess {
                items = it
                loaded = true
                if (it.isEmpty() && error.isBlank()) notice = "Your provider library is empty. Add a torrent or magnet through your provider first."
            }.onFailure {
                error = it.message ?: "Could not load your provider library."
                items = emptyList()
                loaded = true
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) { loadLibrary() }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        item { Header("Library", "Your Real-Debrid and TorBox downloads, together.") }
        item {
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                colors = CardDefaults.cardColors(containerColor = Panel2),
                shape = RoundedCornerShape(18.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        Modifier.size(42.dp).background(Teal.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = Teal)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(if (loading) "Syncing your library…" else "Your provider library", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(
                            if (loading) "Fetching items from connected services"
                            else "${items.size} item${if (items.size == 1) "" else "s"} loaded",
                            color = Muted, fontSize = 11.sp
                        )
                    }
                    if (loading) {
                        CircularProgressIndicator(color = Teal, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = { loadLibrary() }) {
                            Icon(Icons.Default.Search, contentDescription = "Refresh library", tint = Teal)
                        }
                    }
                }
            }
        }
        if (loading && !loaded) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(color = Teal)
                    Text("Loading items from your connected provider…", color = Muted, fontSize = 12.sp)
                }
            }
        } else if (items.isEmpty() && error.isBlank()) {
            item {
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                    colors = CardDefaults.cardColors(containerColor = Panel),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (notice.isNotBlank()) "Nothing here yet" else "No library items found", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(notice.ifBlank { "No items were returned by your connected providers. Pull to refresh with the refresh button above." }, color = Muted, fontSize = 12.sp)
                    }
                }
            }
        }
        if (error.isNotBlank()) {
            item {
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1A20)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Library sync issue", color = Color(0xFFFFB4AB), fontWeight = FontWeight.Bold)
                        Text(error, color = Color(0xFFFFDAD5), fontSize = 12.sp, lineHeight = 17.sp)
                        OutlinedButton(onClick = { loadLibrary() }) { Text("Try again") }
                    }
                }
            }
        }
        items(items, key = { "${it.provider}:${it.id}:${it.title}" }) { item ->
            var playBusy by remember(item.id) { mutableStateOf(false) }
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                colors = CardDefaults.cardColors(containerColor = Panel),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            Modifier.size(38.dp).background(Teal.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Movie, contentDescription = null, tint = Teal)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(item.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(
                                "${if (item.provider == ProviderStore.TORBOX) "TORBOX" else "REAL-DEBRID"} · ${item.status.replace('_', ' ').uppercase()}",
                                color = Teal, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp
                            )
                        }
                        Text("${item.progress}%", color = Muted, fontSize = 11.sp)
                    }
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { item.progress.coerceIn(0, 100) / 100f },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = Teal,
                        trackColor = Panel2
                    )
                    val ready = item.status.equals("completed", true) ||
                        item.status.equals("downloaded", true) ||
                        item.status.equals("cached", true) ||
                        item.progress >= 100
                    if (ready && item.id.isNotBlank()) {
                        Button(
                            enabled = !playBusy,
                            onClick = {
                                playBusy = true
                                playingId = item.id
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        runCatching { store.resolvePlaybackUrl(item.provider, item.id) }
                                    }
                                    playBusy = false
                                    playingId = null
                                    result.onSuccess { onPlay(it, MediaSearchResult("library:${item.provider}:${item.id}", "library", item.title, "", "")) }
                                        .onFailure { error = it.message ?: "Could not prepare playback for this item." }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Teal, contentColor = Bg)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.size(6.dp))
                            Text(if (playBusy || playingId == item.id) "Preparing playback…" else "Play in app")
                        }
                    } else {
                        Text("Playback will be available when this item is ready.", color = Muted, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderCard(store: ProviderStore, scope: CoroutineScope, provider: String, name: String) {
    var token by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(if (store.hasToken(provider)) "API key saved" else "Not connected") }
    var busy by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    val hasKey = store.hasToken(provider)

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(Panel2),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(status, color = if (hasKey) Teal else Muted, fontSize = 11.sp, maxLines = 2)
                }
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Done" else if (hasKey) "Manage" else "Set up")
                }
            }
            if (expanded) {
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("API key") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            runCatching { store.saveToken(provider, token) }
                                .onSuccess {
                                    status = "API key saved securely"
                                    token = ""
                                }
                                .onFailure { status = it.message ?: "Save failed" }
                        },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) { Text("Save key") }
                    if (provider != ProviderStore.DMM) {
                        OutlinedButton(
                            enabled = !busy,
                            onClick = {
                                busy = true
                                scope.launch {
                                    val enteredToken = token.trim().takeIf { it.isNotEmpty() }
                                    val result = withContext(Dispatchers.IO) {
                                        runCatching { store.testConnection(provider, enteredToken) }
                                    }
                                    busy = false
                                    result.onSuccess {
                                        if (enteredToken != null) {
                                            runCatching { store.saveToken(provider, enteredToken) }
                                                .onSuccess { token = "" }
                                        }
                                        status = it
                                    }.onFailure { status = it.message ?: "Connection failed" }
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) { Text(if (busy) "Checking…" else "Test account") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(store: ProviderStore, scope: CoroutineScope, collectionStore: MediaCollectionStore, go: (Int) -> Unit) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE) }
    var historyRevision by remember { mutableIntStateOf(0) }
    val historyCount = remember(historyRevision) { collectionStore.history().size }
    var compactPosters by remember { mutableStateOf(preferences.getBoolean("compact_posters", false)) }
    var showOverview by remember { mutableStateOf(preferences.getBoolean("show_overview", true)) }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        item { Header("Settings", "Make Debrid Media Center yours.") }
        item {
            SettingsSection("PROVIDER CONNECTIONS", "Tap a provider to add or manage its API key. Keys are encrypted on this device.") {
                ProviderCard(store, scope, ProviderStore.REAL_DEBRID, "Real-Debrid")
                ProviderCard(store, scope, ProviderStore.TORBOX, "TorBox")
                ProviderCard(store, scope, ProviderStore.DMM, "DMM sponsor API")
            }
        }
        item {
            SettingsSection("DISCOVERY & DISPLAY", "Choose how titles are shown in the app.") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Compact poster rows", fontWeight = FontWeight.SemiBold)
                        Text("Prefer a denser browsing layout.", color = Muted, fontSize = 12.sp)
                    }
                    androidx.compose.material3.Switch(
                        checked = compactPosters,
                        onCheckedChange = {
                            compactPosters = it
                            preferences.edit().putBoolean("compact_posters", it).apply()
                        }
                    )
                }
                HorizontalDivider(color = Panel2)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Show title descriptions", fontWeight = FontWeight.SemiBold)
                        Text("Display summaries where available.", color = Muted, fontSize = 12.sp)
                    }
                    androidx.compose.material3.Switch(
                        checked = showOverview,
                        onCheckedChange = {
                            showOverview = it
                            preferences.edit().putBoolean("show_overview", it).apply()
                        }
                    )
                }
                Text("Display preferences are saved on this device.", color = Muted, fontSize = 11.sp)
            }
        }
        item {
            SettingsSection("PERSONAL MEDIA", "Your watchlist and viewing progress stay on this device.") {
                SettingsStatusRow("Watchlist", "${collectionStore.watchlist().size} saved", true)
                SettingsStatusRow("Viewing history", "${historyCount} titles", true)
                OutlinedButton(onClick = { collectionStore.clearHistory(); historyRevision++ }, modifier = Modifier.fillMaxWidth()) {
                    Text("Clear viewing history")
                }
            }
        }
        item {
            SettingsSection("QUICK ACTIONS", "Jump straight to common tasks.") {
                OutlinedButton(onClick = { go(1) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Discover movies and shows")
                }
                OutlinedButton(onClick = { go(2) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Folder, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Open your library")
                }
            }
        }
        item {
            SettingsSection("ABOUT", "Debrid Media Center for Android") {
                SettingsStatusRow("App version", "0.7.0", true)
                HorizontalDivider(color = Panel2)
                SettingsStatusRow("Playback", "Media3 built-in player", true)
                HorizontalDivider(color = Panel2)
                SettingsStatusRow("Credential storage", "Android Keystore encryption", true)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Use only media and provider accounts you are authorized to access. API keys stay on this device and are stored encrypted.",
                    color = Muted,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, description: String, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp),
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, color = Teal, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
            Text(description, color = Muted, fontSize = 12.sp)
            content()
        }
    }
}

@Composable
private fun SettingsStatusRow(label: String, value: String, positive: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.Medium, fontSize = 13.sp)
        }
        Text(value, color = if (positive) Teal else Muted, fontSize = 12.sp)
    }
}

private data class MediaDetailsInfo(
    val overview: String = "",
    val genres: List<String> = emptyList(),
    val rating: String = "",
    val runtime: String = "",
    val cast: List<String> = emptyList(),
    val releaseInfo: String = ""
)

private fun fetchMediaDetails(item: MediaSearchResult): MediaDetailsInfo {
    val type = if (item.type == "movie") "movie" else "series"
    val id = URLEncoder.encode(item.id, "UTF-8")
    val connection = (URL("https://v3-cinemeta.strem.io/meta/$type/$id.json").openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 10000
        readTimeout = 12000
        setRequestProperty("Accept", "application/json")
        setRequestProperty("User-Agent", "DebridMediaCenter/0.8")
    }
    try {
        val code = connection.responseCode
        if (code !in 200..299) throw IllegalStateException("Details service returned HTTP $code.")
        val meta = JSONObject(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }).optJSONObject("meta")
            ?: throw IllegalStateException("No extra details were returned.")
        val genresArray = meta.optJSONArray("genres")
        val genres = if (genresArray == null) emptyList() else (0 until genresArray.length()).mapNotNull { genresArray.optString(it).takeIf(String::isNotBlank) }
        val castArray = meta.optJSONArray("cast")
        val cast = if (castArray == null) emptyList() else (0 until castArray.length()).mapNotNull { castArray.optString(it).takeIf(String::isNotBlank) }.take(8)
        return MediaDetailsInfo(
            overview = meta.optString("description").takeIf { it.isNotBlank() } ?: item.overview,
            genres = genres,
            rating = meta.optString("imdbRating").takeIf { it.isNotBlank() && it != "null" }.orEmpty(),
            runtime = meta.optString("runtime").takeIf { it.isNotBlank() && it != "null" }.orEmpty(),
            cast = cast,
            releaseInfo = meta.optString("releaseInfo").takeIf { it.isNotBlank() && it != "null" }.orEmpty()
        )
    } finally {
        connection.disconnect()
    }
}

@Composable
private fun MediaDetailsScreen(
    item: MediaSearchResult,
    collectionStore: MediaCollectionStore,
    onBack: () -> Unit,
    onFindReleases: (MediaSearchResult) -> Unit
) {
    var saved by remember(item.id) { mutableStateOf(collectionStore.isSaved(item)) }
    var details by remember(item.id) { mutableStateOf<MediaDetailsInfo?>(null) }
    var detailsError by remember(item.id) { mutableStateOf("") }

    LaunchedEffect(item.id) {
        val result = withContext(Dispatchers.IO) { runCatching { fetchMediaDetails(item) } }
        result.onSuccess { details = it }.onFailure { detailsError = it.message ?: "Extra details are temporarily unavailable." }
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 28.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 18.dp, top = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(onClick = onBack) { Icon(Icons.Default.Home, contentDescription = "Back", tint = Teal) }
                Column(Modifier.weight(1f)) {
                    Text("TITLE DETAILS", color = Teal, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
                    Text("Discover something worth watching", color = Muted, fontSize = 11.sp)
                }
            }
        }
        item {
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                colors = CardDefaults.cardColors(containerColor = Panel2),
                shape = RoundedCornerShape(22.dp)
            ) {
                Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
                    AsyncImage(
                        model = item.posterUrl.takeIf { it.isNotBlank() },
                        contentDescription = item.title,
                        modifier = Modifier.width(124.dp).height(184.dp),
                        contentScale = ContentScale.Crop
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(item.title, fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold)
                        Text("${item.year.ifBlank { details?.releaseInfo ?: "Release year unavailable" }} · ${item.type.uppercase()}", color = Teal, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        val rating = details?.rating.orEmpty()
                        if (rating.isNotBlank()) Text("★ $rating / 10", color = Color(0xFFFFD166), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        val runtime = details?.runtime.orEmpty()
                        if (runtime.isNotBlank()) Text(runtime, color = Muted, fontSize = 11.sp)
                        Button(
                            onClick = { onFindReleases(item) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Teal, contentColor = Bg),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 9.dp)
                        ) { Text("Find releases", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        OutlinedButton(
                            onClick = { saved = collectionStore.toggleWatchlist(item) },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                        ) { Text(if (saved) "♥ Saved" else "＋ Watchlist", fontSize = 11.sp) }
                    }
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Overview", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                val overview = details?.overview?.takeIf { it.isNotBlank() } ?: item.overview
                Text(overview.ifBlank { if (details == null) "Loading synopsis…" else "No synopsis is available for this title." }, color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
                if (detailsError.isNotBlank()) Text(detailsError, color = Muted, fontSize = 11.sp)
            }
        }
        if (!details?.genres.isNullOrEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Genres", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        details!!.genres.take(5).forEach { FilterChip(selected = false, onClick = {}, enabled = false, label = { Text(it) }) }
                    }
                }
            }
        }
        if (!details?.cast.isNullOrEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Cast", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(details!!.cast.joinToString("  ·  "), color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                }
            }
        }
    }
}

@Composable
private fun DmcPlayer(
    url: String,
    item: MediaSearchResult,
    collectionStore: MediaCollectionStore,
    initialPositionMs: Long,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var playbackError by remember(url) { mutableStateOf("") }
    var playbackSpeed by remember(url) { mutableStateOf(1f) }
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(url)), initialPositionMs.coerceAtLeast(0L))
            playWhenReady = true
            prepare()
        }
    }

    LaunchedEffect(player, url, item) {
        while (true) {
            delay(10_000)
            val duration = player.duration.takeIf { it > 0L } ?: 0L
            runCatching { collectionStore.saveProgress(item, url, player.currentPosition, duration) }
        }
    }
    androidx.compose.runtime.DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                playbackError = error.message ?: error.errorCodeName
            }
        }
        player.addListener(listener)
        onDispose {
            val duration = player.duration.takeIf { it > 0L } ?: 0L
            runCatching { collectionStore.saveProgress(item, url, player.currentPosition, duration) }
            player.removeListener(listener)
            player.release()
        }
    }
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("NOW PLAYING", color = Teal, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(item.title, color = Color.White, fontSize = 12.sp, maxLines = 1)
            }
            if (playbackError.isNotBlank()) {
                OutlinedButton(onClick = {
                    playbackError = ""
                    player.seekTo(0)
                    player.prepare()
                    player.playWhenReady = true
                }) { Text("Retry") }
            }
            OutlinedButton(onClick = onClose) { Text("Close") }
        }
        if (playbackError.isNotBlank()) {
            Text(
                "Playback failed: $playbackError",
                color = Color(0xFFFF9B9B),
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        AndroidView(
            factory = { PlayerView(it).apply { this.player = player; useController = true } },
            update = { it.player = player },
            modifier = Modifier.fillMaxWidth().weight(1f)
        )
        Column(Modifier.fillMaxWidth().background(Panel).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("PLAYBACK SPEED", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                    FilterChip(
                        selected = playbackSpeed == speed,
                        onClick = { playbackSpeed = speed; player.setPlaybackSpeed(speed) },
                        modifier = Modifier.weight(1f),
                        label = { Text(if (speed == 1f) "1×" else "${speed}×", fontSize = 10.sp) }
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { TrackSelectionDialogBuilder(context, "Audio track", player, C.TRACK_TYPE_AUDIO).build().show() }, modifier = Modifier.weight(1f)) {
                    Text("Audio")
                }
                OutlinedButton(onClick = { TrackSelectionDialogBuilder(context, "Subtitles", player, C.TRACK_TYPE_TEXT).build().show() }, modifier = Modifier.weight(1f)) {
                    Text("Subtitles")
                }
            }
        }
    }
}
