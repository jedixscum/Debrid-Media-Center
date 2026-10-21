package com.debridmediacenter.android

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val AddonBg = Color(0xFF080E14)
private val AddonPanel = Color(0xFF101922)
private val AddonPanel2 = Color(0xFF14232C)
private val AddonTeal = Color(0xFF58E1CF)
private val AddonMuted = Color(0xFF91A5AE)

@Composable
fun StremioAddonsScreen(onClose: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember { StremioAddonStore(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var addons by remember { mutableStateOf(store.addons()) }
    var manifestUrl by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(AddonBg)) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 32.dp)
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        Modifier.size(42.dp).background(AddonTeal.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Extension, contentDescription = null, tint = AddonTeal)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("STREMIO ADD-ONS", color = AddonTeal, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
                        Text("Manage your stream sources", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = AddonMuted)
                    }
                }
            }

            item {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AddonPanel2),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Add an add-on", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Paste a Stremio add-on manifest URL. The app validates and stores its declared capabilities, including metadata, catalogs, and movie or series streams when supported. Metadata add-ons are tried before the built-in fallback.",
                            color = AddonMuted,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                        OutlinedTextField(
                            value = manifestUrl,
                            onValueChange = { manifestUrl = it; if (status.isNotBlank()) status = "" },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Manifest URL") },
                            placeholder = { Text("https://example.com/manifest.json") },
                            leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) }
                        )
                        Button(
                            enabled = !busy && manifestUrl.isNotBlank(),
                            onClick = {
                                busy = true
                                status = "Validating manifest…"
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        runCatching { store.addManifest(manifestUrl.trim()) }
                                    }
                                    busy = false
                                    result.onSuccess {
                                        addons = store.addons()
                                        manifestUrl = ""
                                        status = "Added ${it.name}."
                                    }.onFailure {
                                        status = it.message ?: "Could not add that add-on."
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = AddonTeal, contentColor = AddonBg)
                        ) {
                            if (busy) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = AddonBg)
                            else Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.size(7.dp))
                            Text(if (busy) "Checking manifest…" else "Add add-on")
                        }
                        if (status.isNotBlank()) {
                            Text(
                                status,
                                color = if (status.startsWith("Added ")) AddonTeal else Color(0xFFFFB4AB),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Installed add-ons", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Text("${addons.size} configured", color = AddonMuted, fontSize = 11.sp)
                    }
                    IconButton(onClick = { addons = store.addons() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = AddonTeal)
                    }
                }
            }

            if (addons.isEmpty()) {
                item {
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = AddonPanel),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("No add-ons installed", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("Add a manifest above to enable Stremio stream sources inside the app.", color = AddonMuted, fontSize = 12.sp)
                        }
                    }
                }
            } else {
                items(addons, key = { it.id }) { addon ->
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = AddonPanel),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(
                                    Modifier.size(38.dp).background(AddonTeal.copy(alpha = 0.10f), RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Extension, contentDescription = null, tint = AddonTeal, modifier = Modifier.size(20.dp))
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(addon.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text(addon.id, color = AddonMuted, fontSize = 10.sp)
                                }
                            }
                            if (addon.description.isNotBlank()) {
                                Text(addon.description, color = AddonMuted, fontSize = 11.sp, maxLines = 3)
                            }
                            Text(addon.manifestUrl, color = AddonMuted, fontSize = 10.sp, maxLines = 2)
                            OutlinedButton(
                                onClick = {
                                    store.remove(addon.id)
                                    addons = store.addons()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Remove add-on") }
                        }
                    }
                }
            }

            item {
                Text(
                    "Add-ons are stored locally on this device. Only use add-ons and media sources you are authorized to access.",
                    color = AddonMuted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
            }
        }
    }
}
