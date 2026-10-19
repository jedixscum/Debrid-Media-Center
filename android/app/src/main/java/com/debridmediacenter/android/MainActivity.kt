package com.debridmediacenter.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
                        1 -> PlaceholderScreen("Discover", "Search and add content from supported sources.", Icons.Default.Search)
                        2 -> PlaceholderScreen("Your library", "Your provider files will appear here as library integration is added.", Icons.Default.Folder)
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
