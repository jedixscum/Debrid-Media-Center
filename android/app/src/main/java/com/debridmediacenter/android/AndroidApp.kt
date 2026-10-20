package com.debridmediacenter.android

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    var tab by remember { mutableIntStateOf(0) }
    var playerUrl by remember { mutableStateOf<String?>(null) }

    MaterialTheme(colorScheme = darkColorScheme(primary = Teal, background = Bg, surface = Panel)) {
        Surface(Modifier.fillMaxSize(), color = Bg, contentColor = Color.White) {
            if (playerUrl != null) {
                DmcPlayer(playerUrl!!) { playerUrl = null }
            } else {
                Column(Modifier.fillMaxSize()) {
                    Column(Modifier.weight(1f).fillMaxWidth()) {
                        when (tab) {
                            0 -> HomeScreen { tab = it }
                            1 -> DiscoverScreen(store, scope) { playerUrl = it }
                            2 -> LibraryScreen { tab = 3 }
                            3 -> ServicesScreen(store, scope)
                            else -> SettingsScreen()
                        }
                    }
                    NavigationBar(containerColor = Panel) {
                        val nav = listOf(
                            "Home" to Icons.Default.Home,
                            "Discover" to Icons.Default.Search,
                            "Library" to Icons.Default.Folder,
                            "Services" to Icons.Default.Cloud,
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
private fun HomeScreen(go: (Int) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 20.dp)) {
        item { Header("Your media, unified.", "Search, manage and play your authorized media.") }
        item { HomeCard("DISCOVER", "Search titles and work with releases.", Icons.Default.Search) { go(1) } }
        item { HomeCard("LIBRARY", "Manage your provider content.", Icons.Default.Folder) { go(2) } }
        item { HomeCard("SERVICES", "Connect Real-Debrid and TorBox securely.", Icons.Default.Cloud) { go(3) } }
        item { HomeCard("PLAYER", "Play a direct media URL inside the app.", Icons.Default.PlayArrow) { go(1) } }
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
private fun DiscoverScreen(store: ProviderStore, scope: kotlinx.coroutines.CoroutineScope, onPlay: (String) -> Unit) {
    var magnet by remember { mutableStateOf("") }
    var provider by remember { mutableStateOf(ProviderStore.REAL_DEBRID) }
    var status by remember { mutableStateOf("Enter a magnet to add it to your selected provider.") }
    var directUrl by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 20.dp)) {
        item { Header("Discover", "Add authorized content or open a direct media URL.") }
        item {
            Card(Modifier.padding(horizontal = 18.dp).fillMaxWidth(), colors = CardDefaults.cardColors(Panel), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("PROVIDER", color = Teal, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(provider == ProviderStore.REAL_DEBRID, { provider = ProviderStore.REAL_DEBRID }, label = { Text("Real-Debrid") })
                        FilterChip(provider == ProviderStore.TORBOX, { provider = ProviderStore.TORBOX }, label = { Text("TorBox") })
                    }
                    OutlinedTextField(magnet, { magnet = it }, label = { Text("Magnet link") }, placeholder = { Text("magnet:?xt=urn:btih:…") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Button(
                        onClick = {
                            busy = true
                            scope.launch {
                                val result = withContext(Dispatchers.IO) { runCatching { store.addMagnet(provider, magnet) } }
                                busy = false
                                result.onSuccess { status = it; magnet = "" }.onFailure { status = it.message ?: "Could not add magnet." }
                            }
                        },
                        enabled = !busy && magnet.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Teal, contentColor = Bg)
                    ) { Text(if (busy) "Adding…" else "Add to provider", fontWeight = FontWeight.Bold) }
                    Text(status, color = Muted, fontSize = 11.sp)
                }
            }
        }
        item {
            Card(Modifier.padding(horizontal = 18.dp).fillMaxWidth(), colors = CardDefaults.cardColors(Panel2), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("BUILT-IN PLAYER", color = Teal, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(directUrl, { directUrl = it }, label = { Text("Direct media URL") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Button(onClick = { onPlay(directUrl.trim()) }, enabled = directUrl.startsWith("http", true), modifier = Modifier.fillMaxWidth()) { Text("Play") }
                }
            }
        }
    }
}

@Composable
private fun LibraryScreen(goServices: () -> Unit) {
    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 20.dp)) {
        item { Header("Library", "Provider library integration is ready for the next API pass.") }
        item {
            Card(Modifier.padding(horizontal = 18.dp).fillMaxWidth(), colors = CardDefaults.cardColors(Panel), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("No library loaded", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Connect a provider first. Library browsing will use the same encrypted credentials stored by the app.", color = Muted, fontSize = 12.sp)
                    OutlinedButton(onClick = goServices) { Text("Open Services") }
                }
            }
        }
    }
}

@Composable
private fun ServicesScreen(store: ProviderStore, scope: kotlinx.coroutines.CoroutineScope) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 20.dp)) {
        item { Header("Services", "API keys are encrypted with Android Keystore before storage.") }
        item { ProviderCard(store, scope, ProviderStore.REAL_DEBRID, "Real-Debrid") }
        item { ProviderCard(store, scope, ProviderStore.TORBOX, "TorBox") }
    }
}

@Composable
private fun ProviderCard(store: ProviderStore, scope: kotlinx.coroutines.CoroutineScope, provider: String, name: String) {
    var token by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(if (store.hasToken(provider)) "Saved key" else "Not connected") }
    var busy by remember { mutableStateOf(false) }
    Card(Modifier.padding(horizontal = 18.dp).fillMaxWidth(), colors = CardDefaults.cardColors(Panel), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(token, { token = it }, label = { Text("API key") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    runCatching { store.saveToken(provider, token) }.onSuccess { status = "Key saved"; token = "" }.onFailure { status = it.message ?: "Save failed" }
                }) { Text("Save") }
                OutlinedButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { runCatching { store.testConnection(provider) } }
                        busy = false
                        result.onSuccess { status = it }.onFailure { status = it.message ?: "Connection failed" }
                    }
                }) { Text(if (busy) "Testing…" else "Test") }
            }
            Text(status, color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SettingsScreen() {
    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 20.dp)) {
        item { Header("Settings", "Debrid Media Center Android") }
        item {
            Card(Modifier.padding(horizontal = 18.dp).fillMaxWidth(), colors = CardDefaults.cardColors(Panel), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Version 0.5.0", fontWeight = FontWeight.Bold)
                    Text("Native Jetpack Compose client with encrypted provider credentials and Media3 playback.", color = Muted, fontSize = 12.sp)
                    Text("This app is designed for authorized media and your own provider accounts.", color = Muted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun DmcPlayer(url: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(url)))
            prepare()
            playWhenReady = true
        }
    }
    androidx.compose.runtime.DisposableEffect(player) { onDispose { player.release() } }
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.End) {
            OutlinedButton(onClick = onClose) { Text("Close") }
        }
        AndroidView(factory = { PlayerView(it).apply { this.player = player } }, modifier = Modifier.fillMaxWidth().weight(1f))
    }
}
