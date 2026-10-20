package com.debridmediacenter.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Background = Color(0xFF0B0E14)
private val Panel = Color(0xFF151B25)
private val Teal = Color(0xFF58E1CF)
private val Muted = Color(0xFF9AA7B8)
private val ErrorRed = Color(0xFFFF8585)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DebridApp() }
    }
}

@Composable
private fun DebridApp() {
    var selectedTab by remember { mutableIntStateOf(0) }
    MaterialTheme {
        Surface(Modifier.fillMaxSize(), color = Background, contentColor = Color.White) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (selectedTab) {
                        0 -> HomeScreen(onServices = { selectedTab = 3 }, onDiscover = { selectedTab = 1 }, onLibrary = { selectedTab = 2 })
                        1 -> DiscoverScreen()
                        2 -> LibraryScreen()
                        3 -> ServicesScreen()
                        else -> PlaceholderScreen("Settings", "Secure provider credentials are stored on this device using Android Keystore encryption.", Icons.Default.Settings)
                    }
                }
                NavigationBar(containerColor = Panel, contentColor = Teal) {
                    val labels = listOf("Home", "Discover", "Library", "Services", "Settings")
                    val icons = listOf(Icons.Default.Home, Icons.Default.Search, Icons.Default.Folder, Icons.Default.Cloud, Icons.Default.Settings)
                    labels.forEachIndexed { index, label ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = { Icon(icons[index], contentDescription = label) },
                            label = { Text(label, fontSize = 10.sp) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(onServices: () -> Unit, onDiscover: () -> Unit, onLibrary: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 30.dp)) {
                BrandHeader()
                Text("Your media, unified.", fontSize = 29.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("One place for your debrid services.", color = Muted, fontSize = 14.sp)
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                colors = CardDefaults.cardColors(containerColor = Panel),
                shape = RoundedCornerShape(22.dp),
                onClick = onServices
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    IconBadge(Icons.Default.Cloud)
                    Column(Modifier.weight(1f)) {
                        Text("Provider connections", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("Set up Real-Debrid and TorBox", color = Muted, fontSize = 13.sp)
                    }
                    Text("OPEN", color = Teal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 22.dp)) {
                Text("QUICK ACCESS", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Spacer(Modifier.height(10.dp))
                FeatureCard("Discover content", "Search and add supported content", Icons.Default.Search, onDiscover)
                Spacer(Modifier.height(10.dp))
                FeatureCard("Your library", "Browse files from connected services", Icons.Default.Folder, onLibrary)
                Spacer(Modifier.height(10.dp))
                FeatureCard("Manage services", "Connect and test your provider accounts", Icons.Default.Cloud, onServices)
            }
        }
        item {
            Text("CONNECTIONS ARE CONFIGURED IN SERVICES", color = Muted, fontSize = 10.sp, letterSpacing = 1.sp,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp))
        }
    }
}

@Composable
private fun ServicesScreen() {
    val context = LocalContext.current
    val store = remember { ProviderStore(context.applicationContext) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 28.dp, bottom = 14.dp)) {
            BrandHeader()
            Text("Connections", fontSize = 29.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Connect your accounts securely.", color = Muted, fontSize = 14.sp)
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 18.dp, end = 18.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                ProviderCard(
                    title = "Real-Debrid",
                    subtitle = "Premium link and torrent service",
                    provider = ProviderStore.REAL_DEBRID,
                    store = store
                )
            }
            item {
                ProviderCard(
                    title = "TorBox",
                    subtitle = "Torrents, usenet and web downloads",
                    provider = ProviderStore.TORBOX,
                    store = store
                )
            }
            item {
                Text(
                    "API keys are encrypted on this device. Never share your keys or include them in screenshots.",
                    color = Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun ProviderCard(title: String, subtitle: String, provider: String, store: ProviderStore) {
    val scope = rememberCoroutineScope()
    var apiKey by remember { mutableStateOf("") }
    var hasSavedKey by remember { mutableStateOf(store.hasToken(provider)) }
    var status by remember { mutableStateOf(if (hasSavedKey) "Key saved · connection not yet checked" else "Not connected") }
    var isError by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconBadge(Icons.Default.Cloud)
                Column(Modifier.weight(1f)) {
                    Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, color = Muted, fontSize = 12.sp)
                }
            }
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API key") },
                placeholder = { Text(if (hasSavedKey) "Saved securely · enter to replace" else "Paste your API key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Teal,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedPlaceholderColor = Muted,
                    unfocusedPlaceholderColor = Muted,
                    focusedContainerColor = Panel,
                    unfocusedContainerColor = Panel,
                    disabledTextColor = Muted,
                    focusedLabelColor = Teal,
                    cursorColor = Teal,
                    unfocusedBorderColor = Muted.copy(alpha = .45f)
                )
            )
            Text(status, color = if (isError) ErrorRed else Muted, fontSize = 12.sp)
            Button(
                onClick = {
                    val keyToTest = apiKey.trim()
                    if (keyToTest.isEmpty() && !hasSavedKey) {
                        status = "Enter an API key first."
                        isError = true
                    } else {
                        busy = true
                        status = "Testing connection…"
                        isError = false
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching {
                                    if (keyToTest.isNotEmpty()) store.saveToken(provider, keyToTest)
                                    store.testConnection(provider)
                                }
                            }
                            busy = false
                            result.onSuccess {
                                hasSavedKey = true
                                apiKey = ""
                                status = it
                                isError = false
                            }.onFailure {
                                status = it.message ?: "Connection failed. Please try again."
                                isError = true
                                hasSavedKey = store.hasToken(provider)
                            }
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Teal, contentColor = Background),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(if (busy) "Testing…" else if (hasSavedKey) "Save key & test connection" else "Save key & connect", fontWeight = FontWeight.Bold)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(
                    onClick = {
                        if (!hasSavedKey) {
                            status = "No saved key to test."
                            isError = true
                        } else {
                            busy = true
                            status = "Testing saved key…"
                            isError = false
                            scope.launch {
                                val result = withContext(Dispatchers.IO) { runCatching { store.testConnection(provider) } }
                                busy = false
                                result.onSuccess { status = it; isError = false }
                                    .onFailure { status = it.message ?: "Connection failed."; isError = true }
                            }
                        }
                    },
                    enabled = !busy && hasSavedKey
                ) { Text("Test saved key", color = Teal) }
                TextButton(
                    onClick = {
                        store.removeToken(provider)
                        apiKey = ""
                        hasSavedKey = false
                        status = "Key removed from this device."
                        isError = false
                    },
                    enabled = !busy && hasSavedKey
                ) { Text("Remove key", color = Muted) }
            }
        }
    }
}


@Composable
private fun DiscoverScreen() {
    val context = LocalContext.current
    val store = remember { ProviderStore(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var provider by remember { mutableStateOf(ProviderStore.REAL_DEBRID) }
    var magnet by remember { mutableStateOf("") }
    var titleQuery by remember { mutableStateOf("") }
    var titleResults by remember { mutableStateOf<List<MediaSearchResult>>(emptyList()) }
    var titleStatus by remember { mutableStateOf("Search movies and TV shows by title.") }
    var titleBusy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Paste a magnet link to add it to your selected provider.") }
    var error by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            BrandHeader()
            Text("Discover", fontSize = 29.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Add a magnet directly to your debrid account.", color = Muted, fontSize = 14.sp)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("SEARCH TITLES", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                    OutlinedTextField(
                        value = titleQuery,
                        onValueChange = { titleQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Movie or TV show") },
                        placeholder = { Text("Search a title…") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Teal,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedPlaceholderColor = Muted,
                            unfocusedPlaceholderColor = Muted,
                            focusedContainerColor = Panel,
                            unfocusedContainerColor = Panel,
                            disabledTextColor = Muted,
                            focusedLabelColor = Teal,
                            cursorColor = Teal,
                            unfocusedBorderColor = Muted.copy(alpha = .45f)
                        )
                    )
                    Button(
                        onClick = {
                            if (titleQuery.trim().length < 2) {
                                titleStatus = "Enter at least two characters."
                            } else {
                                titleBusy = true
                                titleStatus = "Searching titles…"
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        runCatching { store.searchTitles(titleQuery) }
                                    }
                                    titleBusy = false
                                    result.onSuccess {
                                        titleResults = it
                                        titleStatus = if (it.isEmpty()) "No matching titles found." else "Found " + it.size + " title(s)."
                                    }.onFailure {
                                        titleStatus = it.message ?: "Title search failed."
                                    }
                                }
                            }
                        },
                        enabled = !titleBusy,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Teal, contentColor = Background),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (titleBusy) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Background)
                            Spacer(Modifier.size(8.dp))
                        }
                        Text(if (titleBusy) "Searching…" else "Search titles", fontWeight = FontWeight.Bold)
                    }
                    Text(titleStatus, color = Muted, fontSize = 12.sp)
                }
            }
        }
        items(titleResults.size) { index ->
            val result = titleResults[index]
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    AsyncImage(
                        model = result.posterUrl.takeIf { it.isNotBlank() },
                        contentDescription = result.title + " poster",
                        modifier = Modifier.size(width = 94.dp, height = 138.dp)
                            .background(Background, RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(result.title, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text(result.type.uppercase(), color = Teal, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        if (result.year.isNotBlank()) Text(result.year, color = Muted, fontSize = 12.sp)
                        if (result.overview.isNotBlank()) Text(result.overview, color = Muted, fontSize = 12.sp, maxLines = 5)
                        Text("IMDb / Trakt ID: " + result.id, color = Muted.copy(alpha = .8f), fontSize = 10.sp)
                    }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("CHOOSE PROVIDER", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { provider = ProviderStore.REAL_DEBRID; status = "Ready to add to Real-Debrid."; error = false },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = if (provider == ProviderStore.REAL_DEBRID) Teal else Background, contentColor = if (provider == ProviderStore.REAL_DEBRID) Background else Color.White)
                        ) { Text("Real-Debrid") }
                        Button(
                            onClick = { provider = ProviderStore.TORBOX; status = "Ready to add to TorBox."; error = false },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = if (provider == ProviderStore.TORBOX) Teal else Background, contentColor = if (provider == ProviderStore.TORBOX) Background else Color.White)
                        ) { Text("TorBox") }
                    }
                    OutlinedTextField(
                        value = magnet,
                        onValueChange = { magnet = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Magnet link or info hash") },
                        placeholder = { Text("magnet:?xt=urn:btih:… or 40-character hexadecimal hash") },
                        minLines = 3,
                        maxLines = 5,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Teal,
                            focusedLabelColor = Teal,
                            cursorColor = Teal,
                            unfocusedBorderColor = Muted.copy(alpha = .45f)
                        )
                    )
                    Text("Only add content you have the right to access. This sends the magnet directly to the selected provider.", color = Muted, fontSize = 12.sp)
                    Button(
                        onClick = {
                            if (!store.hasToken(provider)) {
                                status = "Connect this provider in Services before checking cache."
                                error = true
                            } else if (magnet.isBlank()) {
                                status = "Paste a magnet link or enter a torrent info hash first."
                                error = true
                            } else {
                                busy = true
                                status = "Checking cache on " + (if (provider == ProviderStore.REAL_DEBRID) "Real-Debrid" else "TorBox") + "…"
                                error = false
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) { runCatching { store.checkCached(provider, magnet) } }
                                    busy = false
                                    result.onSuccess {
                                        status = (if (it.cached) "✓ CACHED" else "NOT CACHED") + " · " + it.title + " · Hash " + it.hash
                                        error = false
                                    }.onFailure {
                                        status = it.message ?: "Cache check failed."
                                        error = true
                                    }
                                }
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = Teal),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (busy) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Teal)
                            Spacer(Modifier.size(8.dp))
                        }
                        Text(if (busy) "Working…" else "Check cache availability", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            if (!store.hasToken(provider)) {
                                status = "Connect this provider in Services before adding content."
                                error = true
                            } else if (!magnet.trim().startsWith("magnet:?xt=urn:btih:", ignoreCase = true)) {
                                status = "Enter a valid magnet link."
                                error = true
                            } else {
                                busy = true
                                status = "Sending magnet to " + (if (provider == ProviderStore.REAL_DEBRID) "Real-Debrid" else "TorBox") + "…"
                                error = false
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) { runCatching { store.addMagnet(provider, magnet) } }
                                    busy = false
                                    result.onSuccess { status = it; magnet = ""; error = false }
                                        .onFailure { status = it.message ?: "Could not add magnet."; error = true }
                                }
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Teal, contentColor = Background),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (busy) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Background)
                            Spacer(Modifier.size(8.dp))
                        }
                        Text(if (busy) "Adding…" else "Add to provider", fontWeight = FontWeight.Bold)
                    }
                    Text(status, color = if (error) ErrorRed else Muted, fontSize = 12.sp)
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = Teal, modifier = Modifier.size(24.dp))
                    Column {
                        Text("Release search and playback", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text("Title search is now available above. Selecting releases, checking each release's cache status, and opening playable files are still being connected.", color = Muted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryScreen() {
    val context = LocalContext.current
    val store = remember { ProviderStore(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var provider by remember { mutableStateOf(ProviderStore.REAL_DEBRID) }
    var items by remember { mutableStateOf<List<ProviderLibraryItem>>(emptyList()) }
    var libraryQuery by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Choose a provider and load its items.") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 28.dp, bottom = 12.dp)) {
            BrandHeader()
            Text("Your library", fontSize = 29.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Items currently stored with your provider.", color = Muted, fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { provider = ProviderStore.REAL_DEBRID; items = emptyList(); status = "Ready to load Real-Debrid."; error = false },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = if (provider == ProviderStore.REAL_DEBRID) Teal else Panel, contentColor = if (provider == ProviderStore.REAL_DEBRID) Background else Color.White)
                ) { Text("Real-Debrid") }
                Button(
                    onClick = { provider = ProviderStore.TORBOX; items = emptyList(); status = "Ready to load TorBox."; error = false },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = if (provider == ProviderStore.TORBOX) Teal else Panel, contentColor = if (provider == ProviderStore.TORBOX) Background else Color.White)
                ) { Text("TorBox") }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    if (!store.hasToken(provider)) {
                        status = "Connect this provider in Services first."
                        error = true
                    } else {
                        busy = true
                        status = "Loading your provider library…"
                        error = false
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { runCatching { store.fetchLibrary(provider) } }
                            busy = false
                            result.onSuccess {
                                items = it
                                status = if (it.isEmpty()) "No items returned by this provider yet." else "Loaded " + it.size + " item(s)."
                                error = false
                            }.onFailure {
                                status = it.message ?: "Could not load library."
                                error = true
                            }
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = Teal)
            ) {
                if (busy) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Teal)
                Spacer(Modifier.size(8.dp))
                Text(if (busy) "Loading…" else "Refresh library")
            }
            Text(status, color = if (error) ErrorRed else Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = libraryQuery,
                onValueChange = { libraryQuery = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Filter your library") },
                placeholder = { Text("Search titles or status") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Teal,
                    focusedLabelColor = Teal,
                    cursorColor = Teal,
                    unfocusedBorderColor = Muted.copy(alpha = .45f)
                )
            )
        }
        val visibleItems = items.filter {
            libraryQuery.isBlank() ||
                it.title.contains(libraryQuery, ignoreCase = true) ||
                it.status.contains(libraryQuery, ignoreCase = true) ||
                it.id.contains(libraryQuery, ignoreCase = true)
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 18.dp, end = 18.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (visibleItems.isEmpty()) {
                item {
                    Text(
                        if (items.isEmpty()) "Your library is empty. Connect a provider and refresh to load items."
                        else "No library items match “$libraryQuery”.",
                        color = Muted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp)
                    )
                }
            }
            items(visibleItems.size) { index ->
                val item = visibleItems[index]
                Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(item.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text(item.status + " · " + item.progress + "% · ID " + item.id, color = Muted, fontSize = 12.sp)
                        Box(Modifier.fillMaxWidth().height(4.dp).background(Background, RoundedCornerShape(3.dp))) {
                            Box(Modifier.fillMaxWidth(item.progress / 100f).height(4.dp).background(Teal, RoundedCornerShape(3.dp)))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        BrandHeader()
        Text(title, fontSize = 29.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Muted, fontSize = 14.sp)
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(icon, contentDescription = null, tint = Teal, modifier = Modifier.size(30.dp))
                Text("This section is coming next", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text("The current update adds real provider connection checks and encrypted credential storage. Search, library syncing and playback will be added in the next development phase.", color = Muted, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun BrandHeader() {
    Text("DEBRID MEDIA CENTER", color = Teal, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun IconBadge(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(
        Modifier.size(48.dp).background(Teal.copy(alpha = .13f), RoundedCornerShape(15.dp)),
        contentAlignment = Alignment.Center
    ) { Icon(icon, contentDescription = null, tint = Teal) }
}

@Composable
private fun FeatureCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(18.dp),
        onClick = onClick
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(icon, contentDescription = null, tint = Teal, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, color = Muted, fontSize = 12.sp)
            }
        }
    }
}
