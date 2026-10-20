package com.debridmediacenter.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Bg = Color(0xFF080E14)
private val PanelA = Color(0xFF101922)
private val PanelB = Color(0xFF14232C)
private val TealA = Color(0xFF58E1CF)
private val MutedA = Color(0xFF91A5AE)
private val ErrorA = Color(0xFFFF8585)

@Composable
fun DebridMediaCenterApp() {
    val context = LocalContext.current
    val store = remember { ProviderStore(context.applicationContext) }
    var tab by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf<Pair<String, String>?>(null) }
    MaterialTheme(colorScheme = darkColorScheme(primary = TealA, background = Bg, surface = PanelA)) {
        Surface(Modifier.fillMaxSize(), color = Bg, contentColor = Color.White) {
            if (playing != null) {
                DmcPlayer(playing!!.first, playing!!.second) { playing = null }
            } else {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when (tab) {
                            0 -> DmcHome { tab = it }
                            1 -> DmcDiscover(store) { title, url -> playing = title to url }
                            2 -> DmcLibrary(store) { title, url -> playing = title to url }
                            3 -> DmcServices(store)
                            4 -> DmcFavorites(store) { tab = 1 }
                            else -> DmcSettings(store)
                        }
                    }
                    NavigationBar(containerColor = PanelA) {
                        val items = listOf("Home" to Icons.Default.Home, "Discover" to Icons.Default.Search, "Library" to Icons.Default.Folder, "Services" to Icons.Default.Cloud, "Favorites" to Icons.Default.Favorite, "Settings" to Icons.Default.Settings)
                        items.forEachIndexed { i, pair ->
                            NavigationBarItem(tab == i, { tab = i }, icon = { Icon(pair.second, null) }, label = { Text(pair.first, fontSize = 9.sp) })
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun DmcHeader(title: String, subtitle: String? = null) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 24.dp)) {
        Text("DEBRID MEDIA CENTER", color = TealA, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Spacer(Modifier.height(8.dp)); Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        if (subtitle != null) { Spacer(Modifier.height(4.dp)); Text(subtitle, color = MutedA, fontSize = 13.sp) }
    }
}

@Composable private fun DmcHome(go: (Int) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { DmcHeader("Your media, unified.", "Search, add, manage and play your authorized media.") }
        item { DmcBanner("SEARCH & DISCOVER", "Find movies and TV shows, then search indexed releases.", Icons.Default.Search) { go(1) } }
        item { DmcBanner("YOUR LIBRARY", "Refresh Real-Debrid or TorBox and play completed files.", Icons.Default.Folder) { go(2) } }
        item { DmcBanner("PROVIDER CONNECTIONS", "Securely configure and test your accounts.", Icons.Default.Cloud) { go(3) } }
        item { DmcBanner("FAVORITES", "Keep the titles you return to most in one place.", Icons.Default.Favorite) { go(4) } }
        item { Text("Tip: DMM release search is optional and requires your own active sponsor key in Services. Manual magnets work independently.", color = MutedA, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 22.dp)) }
    }
}

@Composable private fun DmcBanner(title: String, body: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 18.dp), colors = CardDefaults.cardColors(PanelA), shape = RoundedCornerShape(18.dp), onClick = onClick) {
        Row(Modifier.padding(17.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(46.dp).background(TealA.copy(alpha = .12f), RoundedCornerShape(14.dp)), Alignment.Center) { Icon(icon, null, tint = TealA) }
            Column(Modifier.weight(1f)) { Text(title, color = TealA, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp); Spacer(Modifier.height(4.dp)); Text(body, color = MutedA, fontSize = 12.sp) }
        }
    }
}

@Composable private fun DmcDiscover(store: ProviderStore, onPlay: (String, String) -> Unit) {
    val scope = rememberCoroutineScope(); var query by remember { mutableStateOf("") }; var results by remember { mutableStateOf<List<MediaSearchResult>>(emptyList()) }
    var selected by remember { mutableStateOf<MediaSearchResult?>(null) }; var releases by remember { mutableStateOf<List<ReleaseSearchResult>>(emptyList()) }
    var quality by remember { mutableStateOf("best") }; var status by remember { mutableStateOf("Search by title to get started.") }; var busy by remember { mutableStateOf(false) }
    var action by remember { mutableStateOf("") }; var magnet by remember { mutableStateOf("") }; var provider by remember { mutableStateOf(ProviderStore.REAL_DEBRID) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 26.dp)) {
        item { DmcHeader("Discover", "Search titles, find releases, or add a magnet directly.") }
        item { Card(Modifier.padding(horizontal = 18.dp), colors = CardDefaults.cardColors(PanelA), shape = RoundedCornerShape(18.dp)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DmcField(query, { query = it }, "Movie or TV show", "Search a title…")
            Button({ if (query.trim().length >= 2) { busy = true; status = "Searching titles…"; scope.launch { val r = withContext(Dispatchers.IO) { runCatching { store.searchTitles(query) } }; busy = false; r.onSuccess { results = it; selected = null; releases = emptyList(); status = "Found ${it.size} title(s)." }.onFailure { status = it.message ?: "Search failed." } } } else status = "Enter at least two characters." }, enabled = !busy, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = TealA, contentColor = Bg)) { Text(if (busy) "Searching…" else "Search titles", fontWeight = FontWeight.Bold) }
            Text(status, color = MutedA, fontSize = 11.sp)
        } } }
        items(results.size) { i -> val r = results[i]; DmcTitleCard(r, selected?.id == r.id) {
            selected = r; releases = emptyList(); action = "Searching releases…"; scope.launch { val x = withContext(Dispatchers.IO) { runCatching { store.searchReleases(r.id, if (r.type == "show") "tv" else "movie", quality) } }; x.onSuccess { releases = it; action = "Found ${it.size} release(s)." }.onFailure { action = it.message ?: "Release search failed." } }
        } }
        if (selected != null) item { Card(Modifier.padding(horizontal = 18.dp), colors = CardDefaults.cardColors(PanelB), shape = RoundedCornerShape(18.dp)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("RELEASE SEARCH · ${selected!!.title.uppercase()}", color = TealA, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { Text("Quality", color = MutedA, fontSize = 12.sp); listOf("best", "2160p", "1080p", "720p").forEach { q -> FilterChip(selected = quality == q, onClick = { quality = q }, label = { Text(q) }) } }
            Text(action, color = MutedA, fontSize = 11.sp); Text("Release search uses your DMM sponsor key. Choose a release to send it to your provider.", color = MutedA, fontSize = 11.sp)
        } } }
        items(releases.size) { i -> val rel = releases[i]; Card(Modifier.padding(horizontal = 18.dp), colors = CardDefaults.cardColors(PanelA), shape = RoundedCornerShape(15.dp)) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(rel.title, fontWeight = FontWeight.SemiBold, maxLines = 2); Text(if (rel.sizeMb > 0) String.format(java.util.Locale.US, "%.2f GB · %s", rel.sizeMb / 1024.0, rel.hash) else rel.hash, color = MutedA, fontSize = 10.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button({ addRelease(store, rel, ProviderStore.REAL_DEBRID, scope) { action = it }; action = "Adding to Real-Debrid…" }, enabled = action.isBlank() || !action.startsWith("Adding"), Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = TealA, contentColor = Bg)) { Text("Add RD") }; OutlinedButton({ addRelease(store, rel, ProviderStore.TORBOX, scope) { action = it }; action = "Adding to TorBox…" }, enabled = action.isBlank() || !action.startsWith("Adding"), Modifier.weight(1f)) { Text("Add TorBox") } }
        } } }
        item { Card(Modifier.padding(horizontal = 18.dp), colors = CardDefaults.cardColors(PanelA), shape = RoundedCornerShape(18.dp)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("MANUAL MAGNET", color = TealA, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(provider == ProviderStore.REAL_DEBRID, { provider = ProviderStore.REAL_DEBRID }, label = { Text("Real-Debrid") }); FilterChip(provider == ProviderStore.TORBOX, { provider = ProviderStore.TORBOX }, label = { Text("TorBox") }) }
            DmcField(magnet, { magnet = it }, "Magnet link", "magnet:?xt=urn:btih:…", false)
            Button({ scope.launch { action = "Adding…"; val x = withContext(Dispatchers.IO) { runCatching { store.addMagnet(provider, magnet) } }; x.onSuccess { action = it; magnet = "" }.onFailure { action = it.message ?: "Could not add magnet." } } }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = TealA, contentColor = Bg)) { Text("Add to provider", fontWeight = FontWeight.Bold) }
            Text("${if (provider == ProviderStore.TORBOX) "TorBox supports cache checks." else "Real-Debrid can receive magnets directly."}", color = MutedA, fontSize = 11.sp)
        } } }
    }
}

private fun addRelease(store: ProviderStore, release: ReleaseSearchResult, provider: String, scope: kotlinx.coroutines.CoroutineScope, done: (String) -> Unit) { scope.launch { val x = withContext(Dispatchers.IO) { runCatching { store.addMagnet(provider, "magnet:?xt=urn:btih:${release.hash}") } }; x.onSuccess { done(it) }.onFailure { done(it.message ?: "Could not add release.") } } }

@Composable private fun DmcTitleCard(r: MediaSearchResult, selected: Boolean, onSelect: () -> Unit) {
    Card(Modifier.padding(horizontal = 18.dp).fillMaxWidth(), colors = CardDefaults.cardColors(if (selected) PanelB else PanelA), shape = RoundedCornerShape(16.dp)) { Row(Modifier.padding(11.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(88.dp, 128.dp).background(Bg, RoundedCornerShape(9.dp)), Alignment.Center) { Text(r.title.take(1).uppercase(), color = TealA, fontSize = 26.sp, fontWeight = FontWeight.Bold); AsyncImage(r.posterUrl.takeIf { it.isNotBlank() }, r.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) { Row { Text(r.title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text(r.type.uppercase(), color = TealA, fontSize = 9.sp, fontWeight = FontWeight.Bold) }; if (r.year.isNotBlank()) Text(r.year, color = MutedA, fontSize = 11.sp); if (r.overview.isNotBlank()) Text(r.overview, color = MutedA, fontSize = 11.sp, maxLines = 4); Button(onSelect, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = TealA, contentColor = Bg)) { Text("Find releases") } }
    } }
}

@Composable private fun DmcLibrary(store: ProviderStore, onPlay: (String, String) -> Unit) {
    val scope = rememberCoroutineScope(); var provider by remember { mutableStateOf(ProviderStore.REAL_DEBRID) }; var items by remember { mutableStateOf<List<ProviderLibraryItem>>(emptyList()) }; var q by remember { mutableStateOf("") }; var status by remember { mutableStateOf("Refresh a provider to load your library.") }; var busy by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) { DmcHeader("Your library", "Transfers, completed files, and in-app playback.")
        Row(Modifier.padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(provider == ProviderStore.REAL_DEBRID, { provider = ProviderStore.REAL_DEBRID; items = emptyList() }, label = { Text("Real-Debrid") }); FilterChip(provider == ProviderStore.TORBOX, { provider = ProviderStore.TORBOX; items = emptyList() }, label = { Text("TorBox") }) }
        Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button({ if (!store.hasToken(provider)) status = "Connect this provider in Services first." else { busy = true; scope.launch { val x = withContext(Dispatchers.IO) { runCatching { store.fetchLibrary(provider) } }; busy = false; x.onSuccess { items = it; status = "Loaded ${it.size} item(s)." }.onFailure { status = it.message ?: "Library load failed." } } } }, Modifier.weight(1f), enabled = !busy, colors = ButtonDefaults.buttonColors(containerColor = TealA, contentColor = Bg)) { Text(if (busy) "Loading…" else "Refresh") }; DmcField(q, { q = it }, "Filter", "Search library", true, Modifier.weight(1f)) }
        Text(status, color = MutedA, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 20.dp))
        LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { val visible = items.filter { q.isBlank() || it.title.contains(q, true) || it.status.contains(q, true) }; if (visible.isEmpty()) item { Text("No matching items. Refresh your provider after adding content.", color = MutedA, fontSize = 13.sp) }; items(visible.size) { i -> val item = visible[i]; Card(colors = CardDefaults.cardColors(PanelA), shape = RoundedCornerShape(15.dp)) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(item.title, fontWeight = FontWeight.SemiBold); Text("${item.status} · ${item.progress}%", color = MutedA, fontSize = 11.sp); LinearProgressIndicator({ item.progress / 100f }, Modifier.fillMaxWidth(), color = TealA, trackColor = Bg); Button({ scope.launch { status = "Preparing playback…"; val x = withContext(Dispatchers.IO) { runCatching { store.resolvePlaybackUrl(provider, item.id) } }; x.onSuccess { onPlay(item.title, it) }.onFailure { status = it.message ?: "Playback preparation failed." } } }, enabled = item.id.isNotBlank(), Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = TealA, contentColor = Bg)) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Play in app") } } } } }
    }
}

@Composable private fun DmcServices(store: ProviderStore) {
    val scope = rememberCoroutineScope(); Column(Modifier.fillMaxSize()) { DmcHeader("Services", "Encrypted provider credentials and connection checks."); LazyColumn(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { DmcProviderCard(store, ProviderStore.REAL_DEBRID, "Real-Debrid", "Premium debrid and direct-link service") }
        item { DmcProviderCard(store, ProviderStore.TORBOX, "TorBox", "Torrents, usenet and web downloads") }
        item { DmcDmmCard(store) }
        item { Text("API keys are protected with Android Keystore-backed AES/GCM encryption. No keys are written to logs.", color = MutedA, fontSize = 11.sp) }
    } }
}

@Composable private fun DmcProviderCard(store: ProviderStore, provider: String, title: String, subtitle: String) {
    val scope = rememberCoroutineScope(); var key by remember { mutableStateOf("") }; var saved by remember { mutableStateOf(store.hasToken(provider)) }; var status by remember { mutableStateOf(if (saved) "Saved securely" else "Not connected") }; var busy by remember { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(PanelA), shape = RoundedCornerShape(18.dp)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) { Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold); Text(subtitle, color = MutedA, fontSize = 11.sp); DmcField(key, { key = it }, "API key", if (saved) "Saved securely · enter to replace" else "Paste API key", password = true); Text(status, color = MutedA, fontSize = 11.sp); Button({ busy = true; scope.launch { val x = withContext(Dispatchers.IO) { runCatching { if (key.isNotBlank()) store.saveToken(provider, key); store.testConnection(provider) } }; busy = false; x.onSuccess { saved = true; key = ""; status = it }.onFailure { status = it.message ?: "Connection failed." } } }, enabled = !busy && (saved || key.isNotBlank()), Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = TealA, contentColor = Bg)) { Text(if (busy) "Testing…" else "Save & test connection") }; Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { TextButton({ if (saved) { busy = true; scope.launch { val x = withContext(Dispatchers.IO) { runCatching { store.testConnection(provider) } }; busy = false; status = x.getOrElse { it.message ?: "Connection failed." } } } }, enabled = saved && !busy) { Text("Test saved", color = TealA) }; TextButton({ store.removeToken(provider); saved = false; key = ""; status = "Key removed" }, enabled = saved && !busy) { Text("Remove", color = MutedA) } } } }
}

@Composable private fun DmcDmmCard(store: ProviderStore) { var key by remember { mutableStateOf("") }; var saved by remember { mutableStateOf(store.hasToken(ProviderStore.DMM)) }; var status by remember { mutableStateOf(if (saved) "Sponsor key saved" else "Optional · not configured") }; Card(colors = CardDefaults.cardColors(PanelA), shape = RoundedCornerShape(18.dp)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) { Text("Debrid Media Manager", fontSize = 17.sp, fontWeight = FontWeight.Bold); Text("Optional release-index integration. Title search and manual magnets do not depend on this.", color = MutedA, fontSize = 11.sp); DmcField(key, { key = it }, "Sponsor API key", if (saved) "Saved securely · enter to replace" else "Paste sponsor key", password = true); Text(status, color = MutedA, fontSize = 11.sp); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button({ runCatching { store.saveToken(ProviderStore.DMM, key) }.onSuccess { saved = true; key = ""; status = "Sponsor key saved securely" }.onFailure { status = it.message ?: "Could not save key." } }, enabled = key.isNotBlank(), Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = TealA, contentColor = Bg)) { Text("Save key") }; TextButton({ store.removeToken(ProviderStore.DMM); saved = false; key = ""; status = "Key removed" }, enabled = saved) { Text("Remove", color = MutedA) } } } } }

@Composable private fun DmcFavorites(store: ProviderStore, goDiscover: () -> Unit) {
    val context = LocalContext.current; val prefs = remember { context.getSharedPreferences("favorites", 0) }; var ids by remember { mutableStateOf(prefs.getStringSet("ids", emptySet())!!.toSet()) }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) { item { DmcHeader("Favorites", "Saved titles on this device.") }; item { if (ids.isEmpty()) Text("No favorites yet. Search a title and use the save action there.", color = MutedA, modifier = Modifier.padding(20.dp)); else Text("${ids.size} saved title ID(s)", color = MutedA, modifier = Modifier.padding(horizontal = 20.dp)) }; item { Button(goDiscover, Modifier.padding(20.dp).fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = TealA, contentColor = Bg)) { Text("Discover more") } }; item { if (ids.isNotEmpty()) TextButton({ ids = emptySet(); prefs.edit().remove("ids").apply() }, Modifier.padding(horizontal = 20.dp)) { Text("Clear favorites", color = MutedA) } } }
}

@Composable private fun DmcSettings(store: ProviderStore) { val context = LocalContext.current; LazyColumn(contentPadding = PaddingValues(bottom = 30.dp)) { item { DmcHeader("Settings", "Local app controls and account security.") }; item { DmcBanner("SECURITY", "Provider credentials use Android Keystore-backed encryption.", Icons.Default.Settings) {} }; item { Card(Modifier.padding(18.dp), colors = CardDefaults.cardColors(PanelA), shape = RoundedCornerShape(18.dp)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Application", fontWeight = FontWeight.Bold); Text("Debrid Media Center 0.3.0", color = MutedA); Text("Native Android build · embedded Media3 playback · DMM release search", color = MutedA, fontSize = 11.sp); Text("Only add and play content you are authorized to access.", color = MutedA, fontSize = 11.sp) } } } }

@Composable private fun DmcPlayer(title: String, url: String, close: () -> Unit) { val context = LocalContext.current; val player = remember(url) { ExoPlayer.Builder(context).build().apply { setMediaItem(MediaItem.fromUri(url)); prepare(); playWhenReady = true } }; DisposableEffect(player) { onDispose { player.release() } }; Column(Modifier.fillMaxSize().background(Bg)) { Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) { TextButton(close) { Text("← Back", color = TealA) }; Column(Modifier.weight(1f)) { Text("NOW PLAYING", color = TealA, fontSize = 9.sp, fontWeight = FontWeight.Bold); Text(title, fontWeight = FontWeight.Bold, maxLines = 2) } }; Box(Modifier.fillMaxWidth().weight(1f), Alignment.Center) { AndroidView(Modifier.fillMaxWidth().height(300.dp), factory = { c -> PlayerView(c).apply { this.player = player; useController = true; setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING) } }, update = { it.player = player }) }; Text("Playback uses a temporary provider link. If a file fails, try another completed video file.", color = MutedA, fontSize = 11.sp, modifier = Modifier.padding(16.dp)) } }

@Composable private fun DmcField(value: String, onValue: (String) -> Unit, label: String, placeholder: String, password: Boolean = false, modifier: Modifier = Modifier.fillMaxWidth()) { OutlinedTextField(value, onValue, modifier, label = { Text(label) }, placeholder = { Text(placeholder) }, singleLine = !password, maxLines = if (password) 3 else 1, keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else KeyboardType.Text), visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = TealA, unfocusedBorderColor = MutedA.copy(alpha = .4f), focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedLabelColor = TealA, cursorColor = TealA, focusedContainerColor = PanelA, unfocusedContainerColor = PanelA, focusedPlaceholderColor = MutedA, unfocusedPlaceholderColor = MutedA)) }
