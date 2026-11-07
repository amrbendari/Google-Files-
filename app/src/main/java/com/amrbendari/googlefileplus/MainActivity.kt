package com.amrbendari.googlefileplus

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.io.File
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GoogleFilePlusApp() }
    }
}

private enum class Destination { HOME, BROWSE, SPACE, TRASH, SETTINGS }
private enum class NavigationStyle { FLOATING, STANDARD }
private enum class ThemeChoice { SYSTEM, LIGHT, DARK }
private enum class AppLanguage { SYSTEM, ENGLISH, ARABIC }

@Composable
private fun GoogleFilePlusApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("google_file_plus_settings", Context.MODE_PRIVATE) }
    var destination by remember { mutableStateOf(Destination.HOME) }
    var navStyle by remember { mutableStateOf(runCatching { NavigationStyle.valueOf(prefs.getString("navigation_style", "FLOATING")!!) }.getOrDefault(NavigationStyle.FLOATING)) }
    var themeChoice by remember { mutableStateOf(runCatching { ThemeChoice.valueOf(prefs.getString("theme_choice", "SYSTEM")!!) }.getOrDefault(ThemeChoice.SYSTEM)) }
    var language by remember { mutableStateOf(runCatching { AppLanguage.valueOf(prefs.getString("app_language", "SYSTEM")!!) }.getOrDefault(AppLanguage.SYSTEM)) }
    var glassAlpha by remember { mutableFloatStateOf(prefs.getFloat("glass_alpha", 0.78f)) }
    var refreshKey by remember { mutableIntStateOf(0) }
    val dark = when (themeChoice) {
        ThemeChoice.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
        ThemeChoice.LIGHT -> false
        ThemeChoice.DARK -> true
    }
    val isArabic = when (language) {
        AppLanguage.ARABIC -> true
        AppLanguage.ENGLISH -> false
        AppLanguage.SYSTEM -> Locale.getDefault().language == "ar"
    }
    val colors = if (Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (dark) darkColorScheme() else lightColorScheme()
    }
    val allFilesAllowed = remember(refreshKey) { hasStorageAccess(context) }
    val storagePermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refreshKey++
    }
    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refreshKey++
    }

    fun save(key: String, value: String) { prefs.edit().putString(key, value).apply() }
    fun requestStorage() {
        if (Build.VERSION.SDK_INT >= 30) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}"))
            try { settingsLauncher.launch(intent) }
            catch (_: Exception) { settingsLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
        } else {
            storagePermissionLauncher.launch(arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ))
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides if (isArabic) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr) {
        MaterialTheme(colorScheme = colors) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Box(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 22.dp)) {
                        Text("Google File+", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(label(destination, isArabic), style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(18.dp))
                        when (destination) {
                            Destination.HOME, Destination.BROWSE -> FileBrowser(
                                context = context,
                                hasAccess = allFilesAllowed,
                                onRequestAccess = ::requestStorage,
                                refreshKey = refreshKey,
                                onRefresh = { refreshKey++ },
                                arabic = isArabic
                            )
                            Destination.SPACE -> EmptySection(if (isArabic) "المساحة الخاصة مقفلة" else "Space is locked",
                                if (isArabic) "ستتوفر هنا الملفات المحمية بعد إعداد التشفير." else "Protected files will appear here after encryption is implemented.")
                            Destination.TRASH -> EmptySection(if (isArabic) "سلة المحذوفات" else "Recycle Bin",
                                if (isArabic) "إدارة سلة المحذوفات قيد التطوير." else "Recycle Bin management is not implemented yet.")
                            Destination.SETTINGS -> SettingsScreen(
                                navStyle, { navStyle = it; save("navigation_style", it.name) },
                                themeChoice, { themeChoice = it; save("theme_choice", it.name) },
                                language, { language = it; save("app_language", it.name) },
                                glassAlpha, { glassAlpha = it; prefs.edit().putFloat("glass_alpha", it).apply() },
                                isArabic
                            )
                        }
                    }
                    AppNavigationBar(
                        selected = destination,
                        onSelect = { destination = it },
                        floating = navStyle == NavigationStyle.FLOATING,
                        glassAlpha = glassAlpha,
                        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
                        arabic = isArabic
                    )
                }
            }
        }
    }
}

private fun hasStorageAccess(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
    else ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

@Composable
private fun FileBrowser(
    context: Context, hasAccess: Boolean, onRequestAccess: () -> Unit,
    refreshKey: Int, onRefresh: () -> Unit, arabic: Boolean
) {
    var currentDirectory by remember { mutableStateOf(Environment.getExternalStorageDirectory()) }
    val rootDirectory = remember { Environment.getExternalStorageDirectory() }
    val root = remember(refreshKey, hasAccess, currentDirectory) {
        if (hasAccess) runCatching { currentDirectory.listFiles()?.toList().orEmpty()
            .sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() }) }.getOrDefault(emptyList())
        else emptyList()
    }
    if (!hasAccess) {
        Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.FolderOpen, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text(if (arabic) "يلزم السماح بالوصول إلى الملفات" else "Storage access is required",
                style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(if (arabic) "اضغط للسماح للتطبيق بعرض الملفات والمجلدات." else "Allow access so Google File+ can display your files and folders.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))
            Button(onClick = onRequestAccess) { Text(if (arabic) "منح صلاحية الملفات" else "Grant file access") }
            if (Build.VERSION.SDK_INT >= 30) {
                Spacer(Modifier.height(8.dp))
                Text(if (arabic) "سيتم فتح إعدادات الوصول إلى كل الملفات." else "Android will open the All files access settings.")
            }
        }
    } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (currentDirectory != rootDirectory) {
                IconButton(onClick = { currentDirectory = currentDirectory.parentFile ?: rootDirectory }) {
                    Icon(Icons.Default.ArrowBack, if (arabic) "للخلف" else "Up")
                }
            }
            Text(currentDirectory.name.ifBlank { if (arabic) "التخزين الداخلي" else "Internal storage" },
                Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 1)
            IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, if (arabic) "تحديث" else "Refresh") }
        }
        if (root.isEmpty()) {
            EmptySection(if (arabic) "لا توجد ملفات ظاهرة" else "No files found",
                if (arabic) "قد يكون المجلد فارغًا أو تعذر قراءة محتوياته." else "The folder may be empty or Android denied access.")
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 104.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                items(root, key = { it.absolutePath }) { file ->
                    Row(Modifier.fillMaxWidth().clickable(enabled = file.isDirectory) { currentDirectory = file }
                        .padding(vertical = 9.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (file.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                            null, tint = if (file.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(file.name, maxLines = 1, style = MaterialTheme.typography.bodyLarge)
                            Text(if (file.isDirectory) (if (arabic) "مجلد" else "Folder") else formatSize(file.length()),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024f * 1024f))
}

@Composable
private fun SettingsScreen(
    nav: NavigationStyle, onNav: (NavigationStyle) -> Unit,
    theme: ThemeChoice, onTheme: (ThemeChoice) -> Unit,
    language: AppLanguage, onLanguage: (AppLanguage) -> Unit,
    alpha: Float, onAlpha: (Float) -> Unit, arabic: Boolean
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 104.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SettingGroup(if (arabic) "مظهر التطبيق" else "App appearance") {
            listOf(ThemeChoice.SYSTEM, ThemeChoice.LIGHT, ThemeChoice.DARK).forEach { choice ->
                val text = when (choice) {
                    ThemeChoice.SYSTEM -> if (arabic) "تلقائي حسب الجهاز" else "System default"
                    ThemeChoice.LIGHT -> if (arabic) "فاتح" else "Light"
                    ThemeChoice.DARK -> if (arabic) "داكن" else "Dark"
                }
                ChoiceRow(text, theme == choice) { onTheme(choice) }
            }
        }
        SettingGroup(if (arabic) "لغة التطبيق" else "App language") {
            listOf(AppLanguage.SYSTEM, AppLanguage.ARABIC, AppLanguage.ENGLISH).forEach { choice ->
                val text = when (choice) {
                    AppLanguage.SYSTEM -> if (arabic) "لغة الجهاز" else "System language"
                    AppLanguage.ARABIC -> "العربية"
                    AppLanguage.ENGLISH -> "English"
                }
                ChoiceRow(text, language == choice) { onLanguage(choice) }
            }
        }
        SettingGroup(if (arabic) "شريط التنقل" else "Navigation bar") {
            ChoiceRow(if (arabic) "عائم" else "Floating", nav == NavigationStyle.FLOATING) { onNav(NavigationStyle.FLOATING) }
            ChoiceRow(if (arabic) "قياسي" else "Standard", nav == NavigationStyle.STANDARD) { onNav(NavigationStyle.STANDARD) }
            Text(if (arabic) "شفافية Liquid Glass" else "Liquid Glass transparency", style = MaterialTheme.typography.titleSmall)
            Text("${(alpha * 100).toInt()}%", color = MaterialTheme.colorScheme.primary)
            Slider(value = alpha, onValueChange = onAlpha, valueRange = 0.25f..0.95f)
            Text(if (arabic) "اسحب للتحكم في شفافية خلفية الشريط." else "Adjust the translucency of the navigation surface.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun ChoiceRow(title: String, selected: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            RadioButton(selected = selected, onClick = onClick)
        }
    }
}

@Composable
private fun EmptySection(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(top = 34.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun label(destination: Destination, arabic: Boolean): String = when (destination) {
    Destination.HOME -> if (arabic) "الرئيسية" else "Home"
    Destination.BROWSE -> if (arabic) "استعراض الملفات" else "Browse"
    Destination.SPACE -> if (arabic) "المساحة الخاصة" else "Private Space"
    Destination.TRASH -> if (arabic) "المحذوفات" else "Trash"
    Destination.SETTINGS -> if (arabic) "الإعدادات" else "Settings"
}

@Composable
private fun AppNavigationBar(
    selected: Destination, onSelect: (Destination) -> Unit, floating: Boolean,
    glassAlpha: Float, modifier: Modifier = Modifier, arabic: Boolean
) {
    val items = listOf(
        Triple(Destination.HOME, Icons.Default.Home, if (arabic) "الرئيسية" else "Home"),
        Triple(Destination.BROWSE, Icons.Default.Folder, if (arabic) "الملفات" else "Files"),
        Triple(Destination.SPACE, Icons.Default.Lock, if (arabic) "خاصة" else "Space"),
        Triple(Destination.TRASH, Icons.Default.Delete, if (arabic) "المحذوفات" else "Trash"),
        Triple(Destination.SETTINGS, Icons.Default.Settings, if (arabic) "الضبط" else "Settings")
    )
    Surface(
        modifier = modifier.fillMaxWidth().then(if (floating) Modifier.padding(start = 22.dp, end = 22.dp, bottom = 9.dp) else Modifier)
            .height(if (floating) 62.dp else 68.dp)
            .then(if (floating) Modifier.shadow(6.dp, RoundedCornerShape(25.dp)) else Modifier),
        shape = if (floating) RoundedCornerShape(25.dp) else RoundedCornerShape(0.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = if (floating) glassAlpha else 1f),
        tonalElevation = 2.dp
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 1.dp, vertical = 2.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            items.forEach { (dest, icon, title) ->
                NavigationBarItem(
                    selected = selected == dest,
                    onClick = { onSelect(dest) },
                    icon = { Icon(icon, contentDescription = title, modifier = Modifier.size(19.dp)) },
                    label = { Text(title, fontSize = 9.sp, maxLines = 1) },
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.secondaryContainer)
                )
            }
        }
    }
}
