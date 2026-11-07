package com.amrbendari.googlefileplus

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GoogleFilePlusApp()
        }
    }
}

private enum class Destination(val title: String) {
    HOME("Home"),
    BROWSE("Browse"),
    SPACE("Space"),
    TRASH("Trash"),
    SETTINGS("Settings")
}

@Composable
private fun GoogleFilePlusApp() {
    var selected by remember { mutableStateOf(Destination.HOME) }
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()

    val appColorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && systemDark ->
            dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            dynamicLightColorScheme(context)
        systemDark -> darkColorScheme()
        else -> lightColorScheme()
    }

    MaterialTheme(colorScheme = appColorScheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 24.dp)
                ) {
                    Text(
                        text = "Google File+",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = selected.title,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    when (selected) {
                        Destination.HOME ->
                            EmptyState("Your files, all in one place")
                        Destination.BROWSE ->
                            EmptyState("Browse storage")
                        Destination.SPACE ->
                            EmptyState(
                                "Space is locked",
                                "Unlock with your password or supported device biometrics."
                            )
                        Destination.TRASH ->
                            EmptyState(
                                "Recycle Bin",
                                "Deleted items will appear here."
                            )
                        Destination.SETTINGS ->
                            EmptyState(
                                "Settings",
                                "Appearance, language, security, and file management options."
                            )
                    }
                }

                FloatingNavigationBar(
                    selected = selected,
                    onSelect = { selected = it },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyState(
    title: String,
    subtitle: String = "This section is part of the initial app shell."
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FloatingNavigationBar(
    selected: Destination,
    onSelect: (Destination) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        Triple(Destination.HOME, Icons.Default.Home, "Home"),
        Triple(Destination.BROWSE, Icons.Default.Folder, "Browse"),
        Triple(Destination.SPACE, Icons.Default.Lock, "Space"),
        Triple(Destination.TRASH, Icons.Default.Delete, "Trash"),
        Triple(Destination.SETTINGS, Icons.Default.Settings, "Settings")
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(76.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 5.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { (destination, icon, label) ->
                NavigationBarItem(
                    selected = selected == destination,
                    onClick = { onSelect(destination) },
                    icon = {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            modifier = Modifier.size(23.dp)
                        )
                    },
                    label = {
                        Text(
                            text = label,
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    },
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                )
            }
        }
    }
}
