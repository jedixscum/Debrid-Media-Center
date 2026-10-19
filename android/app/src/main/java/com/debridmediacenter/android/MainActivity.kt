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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Background = Color(0xFF0B0E14)
private val Panel = Color(0xFF151B25)
private val Teal = Color(0xFF58E1CF)
private val Muted = Color(0xFF9AA7B8)

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
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 30.dp)) {
                            Text("DEBRID MEDIA CENTER", color = Teal, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                listOf("Your media, unified.", "Discover", "Your library", "Connections", "Settings")[selectedTab],
                                fontSize = 29.sp, fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(6.dp))
                            Text("One place for your debrid services.", color = Muted, fontSize = 14.sp)
                        }
                    }
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                            colors = CardDefaults.cardColors(containerColor = Panel),
                            shape = RoundedCornerShape(22.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Box(
                                    Modifier.size(48.dp).background(Teal.copy(alpha = .13f), RoundedCornerShape(15.dp)),
                                    contentAlignment = Alignment.Center
                                ) { Icon(Icons.Default.Cloud, contentDescription = null, tint = Teal) }
                                Column(Modifier.weight(1f)) {
                                    Text("Provider connections", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                    Spacer(Modifier.height(4.dp))
                                    Text("Real-Debrid  ·  TorBox", color = Muted, fontSize = 13.sp)
                                }
                                Text("SET UP", color = Teal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    item {
                        Column(Modifier.padding(horizontal = 22.dp)) {
                            Text("GET STARTED", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                            Spacer(Modifier.height(10.dp))
                            FeatureCard("Find something to watch", "Search and discover supported content", Icons.Default.Search)
                            Spacer(Modifier.height(10.dp))
                            FeatureCard("Your library", "Keep your saved items in one place", Icons.Default.Folder)
                            Spacer(Modifier.height(10.dp))
                            FeatureCard("Connect your services", "Configure Real-Debrid and TorBox", Icons.Default.Cloud)
                        }
                    }
                    item {
                        Text(
                            "EARLY PREVIEW  •  PROVIDER FEATURES NOT CONNECTED",
                            color = Muted, fontSize = 10.sp, letterSpacing = 1.sp,
                            modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp)
                        )
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
private fun FeatureCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(18.dp)
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
