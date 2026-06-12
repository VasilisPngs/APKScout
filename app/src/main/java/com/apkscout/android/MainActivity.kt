package com.apkscout.android

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apkscout.android.apkmirror.ApkMirrorApiClient
import com.apkscout.android.apkmirror.ApkMirrorSource
import com.apkscout.android.settings.ReleaseChannelFilter
import com.apkscout.android.settings.ReleaseChannelSettings
import com.apkscout.android.settings.SettingsStore
import com.apkscout.android.ui.SettingsScreen
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledApp(
    val label: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val isSystem: Boolean,
    val icon: Bitmap?
)

data class UpdateInfo(
    val versionName: String,
    val versionCode: Long,
    val url: String,
    val formatLabel: String? = null
)

enum class AppListFilter {
    ALL,
    USER,
    SYSTEM,
    UPDATES
}

private enum class RootScreen {
    HOME,
    SEARCH,
    SETTINGS
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val context = LocalContext.current
            var darkMode by remember { mutableStateOf(SettingsStore.readDarkMode(context)) }

            APKScoutTheme(darkMode = darkMode) {
                APKScoutRoot(
                    darkMode = darkMode,
                    onDarkModeChange = { enabled ->
                        darkMode = enabled
                        SettingsStore.writeDarkMode(context, enabled)
                    }
                )
            }
        }
    }
}

@Composable
fun APKScoutTheme(
    darkMode: Boolean,
    content: @Composable () -> Unit
) {
    val colors = if (darkMode) {
        darkColorScheme(
            background = Color(0xFF101114),
            surface = Color(0xFF1A1C20),
            surfaceVariant = Color(0xFF25282E),
            primary = Color(0xFFE4E7EF),
            onPrimary = Color(0xFF111318),
            onBackground = Color(0xFFF2F4FA),
            onSurface = Color(0xFFF2F4FA),
            onSurfaceVariant = Color(0xFFC9CDD6),
            outline = Color(0xFF8B909A),
            outlineVariant = Color(0xFF3F444D)
        )
    } else {
        lightColorScheme(
            background = Color(0xFFF5F6FA),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFF0F2F7),
            primary = Color(0xFF15171C),
            onPrimary = Color(0xFFFFFFFF),
            onBackground = Color(0xFF16181D),
            onSurface = Color(0xFF16181D),
            onSurfaceVariant = Color(0xFF4D535D),
            outline = Color(0xFF777D87),
            outlineVariant = Color(0xFFD8DCE4)
        )
    }

    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}

@Composable
fun APKScoutRoot(
    darkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val homeListState = rememberLazyListState()

    var currentScreen by remember { mutableStateOf(RootScreen.HOME) }
    var releaseSettings by remember { mutableStateOf(SettingsStore.read(context)) }
    var selectedFilter by remember { mutableStateOf(AppListFilter.UPDATES) }
    var searchQuery by remember { mutableStateOf("") }
    var scanRequest by remember { mutableIntStateOf(0) }
    var updateRequest by remember { mutableIntStateOf(0) }
    var homeScrollRequest by remember { mutableIntStateOf(0) }
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var rawUpdates by remember { mutableStateOf<Map<String, UpdateInfo>>(emptyMap()) }
    var updateError by remember { mutableStateOf<String?>(null) }
    var loadingApps by remember { mutableStateOf(true) }
    var checkingUpdates by remember { mutableStateOf(false) }

    LaunchedEffect(currentScreen) {
        if (currentScreen != RootScreen.SEARCH) {
            searchQuery = ""
        }
    }

    LaunchedEffect(scanRequest) {
        loadingApps = true
        checkingUpdates = false
        updateError = null
        rawUpdates = emptyMap()

        apps = withContext(Dispatchers.Default) {
            scanInstalledApps(packageManager = context.packageManager)
        }

        loadingApps = false
        updateRequest++
    }

    LaunchedEffect(updateRequest) {
        if (updateRequest <= 0 || apps.isEmpty()) return@LaunchedEffect

        checkingUpdates = true
        updateError = null

        val result = ApkMirrorApiClient.checkUpdates(
            apps = apps,
            packageManager = context.packageManager
        )

        rawUpdates = result.updates
        updateError = result.error
        checkingUpdates = false
    }

    val updates = remember(rawUpdates, releaseSettings) {
        rawUpdates.filterValues { update ->
            ReleaseChannelFilter.isAllowed(
                versionName = update.versionName,
                settings = releaseSettings
            )
        }
    }

    val homeVisibleApps = remember(apps, updates, selectedFilter) {
        filterApps(
            apps = apps,
            updates = updates,
            filter = selectedFilter,
            query = ""
        )
    }

    val searchVisibleApps = remember(apps, updates, searchQuery) {
        filterApps(
            apps = apps,
            updates = updates,
            filter = AppListFilter.ALL,
            query = searchQuery
        )
    }

    val activeVisibleApps = if (currentScreen == RootScreen.SEARCH) {
        searchVisibleApps
    } else {
        homeVisibleApps
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            APKScoutTopBar(
                currentScreen = currentScreen,
                loading = loadingApps || checkingUpdates,
                darkMode = darkMode,
                updatesCount = updates.size,
                onToggleTheme = { onDarkModeChange(!darkMode) },
                onRefresh = { scanRequest++ }
            )
        },
        bottomBar = {
            APKScoutBottomBar(
                currentScreen = currentScreen,
                onHomeClick = {
                    currentScreen = RootScreen.HOME
                    homeScrollRequest++
                },
                onSearchClick = {
                    currentScreen = RootScreen.SEARCH
                },
                onSettingsClick = {
                    currentScreen = RootScreen.SETTINGS
                }
            )
        }
    ) { innerPadding ->
        when (currentScreen) {
            RootScreen.HOME,
            RootScreen.SEARCH -> {
                APKScoutScreen(
                    modifier = Modifier.padding(innerPadding),
                    listState = homeListState,
                    homeScrollRequest = homeScrollRequest,
                    apps = apps,
                    updates = updates,
                    homeVisibleApps = homeVisibleApps,
                    activeVisibleApps = activeVisibleApps,
                    selectedFilter = selectedFilter,
                    searchQuery = searchQuery,
                    loadingApps = loadingApps,
                    checkingUpdates = checkingUpdates,
                    updateError = updateError,
                    searchActive = currentScreen == RootScreen.SEARCH,
                    onFilterChange = { selectedFilter = it },
                    onSearchQueryChange = { searchQuery = it }
                )
            }

            RootScreen.SETTINGS -> {
                Box(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    SettingsScreen(
                        settings = releaseSettings,
                        onDevChanged = { value ->
                            val next = releaseSettings.copy(includeDev = value)
                            releaseSettings = next
                            SettingsStore.write(context, next)
                        },
                        onAlphaChanged = { value ->
                            val next = releaseSettings.copy(includeAlpha = value)
                            releaseSettings = next
                            SettingsStore.write(context, next)
                        },
                        onBetaChanged = { value ->
                            val next = releaseSettings.copy(includeBeta = value)
                            releaseSettings = next
                            SettingsStore.write(context, next)
                        },
                        onRcChanged = { value ->
                            val next = releaseSettings.copy(includeRc = value)
                            releaseSettings = next
                            SettingsStore.write(context, next)
                        },
                        onPrereleaseChanged = { value ->
                            val next = releaseSettings.copy(includePrerelease = value)
                            releaseSettings = next
                            SettingsStore.write(context, next)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun APKScoutTopBar(
    currentScreen: RootScreen,
    loading: Boolean,
    darkMode: Boolean,
    updatesCount: Int,
    onToggleTheme: () -> Unit,
    onRefresh: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 20.dp, top = 12.dp, end = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "APKScout",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )

                Text(
                    text = when {
                        loading -> "Working..."
                        currentScreen == RootScreen.SEARCH -> "Search installed apps"
                        currentScreen == RootScreen.SETTINGS -> "Settings"
                        updatesCount == 1 -> "1 update available"
                        else -> "$updatesCount updates available"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onToggleTheme) {
                Icon(
                    imageVector = if (darkMode) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                    contentDescription = if (darkMode) "Switch to light mode" else "Switch to dark mode"
                )
            }

            IconButton(
                onClick = onRefresh,
                enabled = !loading
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Refresh"
                )
            }
        }
    }
}

@Composable
private fun APKScoutBottomBar(
    currentScreen: RootScreen,
    onHomeClick: () -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        NavigationBar(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(64.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            windowInsets = WindowInsets(0, 0, 0, 0)
        ) {
            NavigationBarItem(
                selected = currentScreen == RootScreen.HOME,
                onClick = onHomeClick,
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Home,
                        contentDescription = "Home",
                        modifier = Modifier.size(23.dp)
                    )
                },
                label = null,
                alwaysShowLabel = false,
                colors = bottomBarItemColors()
            )

            NavigationBarItem(
                selected = currentScreen == RootScreen.SEARCH,
                onClick = onSearchClick,
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Search",
                        modifier = Modifier.size(23.dp)
                    )
                },
                label = null,
                alwaysShowLabel = false,
                colors = bottomBarItemColors()
            )

            NavigationBarItem(
                selected = currentScreen == RootScreen.SETTINGS,
                onClick = onSettingsClick,
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "Settings",
                        modifier = Modifier.size(23.dp)
                    )
                },
                label = null,
                alwaysShowLabel = false,
                colors = bottomBarItemColors()
            )
        }
    }
}

@Composable
private fun bottomBarItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    indicatorColor = MaterialTheme.colorScheme.primary
)

@Composable
fun APKScoutScreen(
    modifier: Modifier,
    listState: LazyListState,
    homeScrollRequest: Int,
    apps: List<InstalledApp>,
    updates: Map<String, UpdateInfo>,
    homeVisibleApps: List<InstalledApp>,
    activeVisibleApps: List<InstalledApp>,
    selectedFilter: AppListFilter,
    searchQuery: String,
    loadingApps: Boolean,
    checkingUpdates: Boolean,
    updateError: String?,
    searchActive: Boolean,
    onFilterChange: (AppListFilter) -> Unit,
    onSearchQueryChange: (String) -> Unit
) {
    val context = LocalContext.current

    LaunchedEffect(homeScrollRequest) {
        if (homeScrollRequest > 0) {
            listState.animateScrollToItem(0)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 14.dp,
                top = 8.dp,
                end = 14.dp,
                bottom = 18.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (searchActive) {
                item {
                    SearchBarCard(
                        query = searchQuery,
                        onQueryChange = onSearchQueryChange
                    )
                }
            } else {
                item {
                    ControlsCard(
                        selectedFilter = selectedFilter,
                        visibleCount = homeVisibleApps.size,
                        totalCount = apps.size,
                        loadingApps = loadingApps,
                        checkingUpdates = checkingUpdates,
                        updateError = updateError,
                        onFilterChange = onFilterChange
                    )
                }
            }

            items(
                items = activeVisibleApps,
                key = { it.packageName }
            ) { app ->
                InstalledAppCard(
                    app = app,
                    update = updates[app.packageName],
                    onOpenAPKMirror = {
                        openAPKMirror(
                            context = context,
                            packageName = app.packageName,
                            update = updates[app.packageName]
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun SearchBarCard(
    query: String,
    onQueryChange: (String) -> Unit
) {
    UniformCard {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            label = { Text("Search apps") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null
                )
            }
        )
    }
}

@Composable
fun ControlsCard(
    selectedFilter: AppListFilter,
    visibleCount: Int,
    totalCount: Int,
    loadingApps: Boolean,
    checkingUpdates: Boolean,
    updateError: String?,
    onFilterChange: (AppListFilter) -> Unit
) {
    UniformCard {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterTab(
                    label = "Updates",
                    selected = selectedFilter == AppListFilter.UPDATES,
                    modifier = Modifier.weight(1f),
                    onClick = { onFilterChange(AppListFilter.UPDATES) }
                )

                FilterTab(
                    label = "User",
                    selected = selectedFilter == AppListFilter.USER,
                    modifier = Modifier.weight(1f),
                    onClick = { onFilterChange(AppListFilter.USER) }
                )

                FilterTab(
                    label = "System",
                    selected = selectedFilter == AppListFilter.SYSTEM,
                    modifier = Modifier.weight(1f),
                    onClick = { onFilterChange(AppListFilter.SYSTEM) }
                )

                FilterTab(
                    label = "All",
                    selected = selectedFilter == AppListFilter.ALL,
                    modifier = Modifier.weight(1f),
                    onClick = { onFilterChange(AppListFilter.ALL) }
                )
            }

            Text(
                text = when {
                    loadingApps -> "Loading apps..."
                    checkingUpdates -> "$visibleCount of $totalCount visible • checking updates..."
                    updateError != null -> "$visibleCount of $totalCount visible • update check failed"
                    else -> "$visibleCount of $totalCount visible"
                },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (updateError == null) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.error
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FilterTab(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier.height(42.dp),
        label = {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
            )
        }
    )
}

@Composable
fun InstalledAppCard(
    app: InstalledApp,
    update: UpdateInfo?,
    onOpenAPKMirror: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
            draggedElevation = 0.dp
        ),
        shape = RoundedCornerShape(28.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            AppIcon(icon = app.icon)

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = app.label,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = app.packageName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    AppTypeLabel(isSystem = app.isSystem)
                }

                VersionBlock(
                    app = app,
                    update = update
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    update?.formatLabel
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { formatLabel ->
                            PackageFormatLabel(
                                formatLabel = formatLabel,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }

                    Button(
                        onClick = onOpenAPKMirror,
                        contentPadding = PaddingValues(
                            horizontal = 18.dp,
                            vertical = 10.dp
                        ),
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text(
                            text = "APKMirror",
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppIcon(icon: Bitmap?) {
    if (icon == null) {
        Surface(
            modifier = Modifier.size(56.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {}
    } else {
        Image(
            bitmap = icon.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.size(56.dp)
        )
    }
}

@Composable
private fun AppTypeLabel(isSystem: Boolean) {
    Surface(
        modifier = Modifier.heightIn(min = 36.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.30f)
        )
    ) {
        Box(
            modifier = Modifier
                .heightIn(min = 36.dp)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isSystem) "S" else "U",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun VersionBlock(
    app: InstalledApp,
    update: UpdateInfo?
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = if (update == null) {
                "Installed: ${app.versionName}"
            } else {
                buildUpdateLine(app.versionName, update)
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = if (update == null) {
                "Version code: ${app.versionCode}"
            } else {
                "${app.versionCode} -> ${update.versionCode}"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PackageFormatLabel(
    formatLabel: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.heightIn(min = 40.dp),
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    ) {
        Box(
            modifier = Modifier
                .heightIn(min = 40.dp)
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = formatLabel,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Composable
fun UniformCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(28.dp)
            ),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        shape = RoundedCornerShape(28.dp)
    ) {
        Box(
            modifier = Modifier.padding(16.dp)
        ) {
            content()
        }
    }
}

private fun buildUpdateLine(
    installedVersionName: String,
    update: UpdateInfo
): String {
    return "$installedVersionName -> ${update.versionName}"
}

fun filterApps(
    apps: List<InstalledApp>,
    updates: Map<String, UpdateInfo>,
    filter: AppListFilter,
    query: String
): List<InstalledApp> {
    val normalizedQuery = query.trim().lowercase(Locale.ROOT)

    return apps
        .filter { app ->
            val matchesFilter = when (filter) {
                AppListFilter.ALL -> true
                AppListFilter.USER -> !app.isSystem
                AppListFilter.SYSTEM -> app.isSystem
                AppListFilter.UPDATES -> updates.containsKey(app.packageName)
            }

            val matchesQuery = normalizedQuery.isEmpty() ||
                app.label.lowercase(Locale.ROOT).contains(normalizedQuery) ||
                app.packageName.lowercase(Locale.ROOT).contains(normalizedQuery)

            matchesFilter && matchesQuery
        }
        .sortedWith(
            compareBy<InstalledApp> { app ->
                if (updates.containsKey(app.packageName)) 0 else 1
            }.thenBy { app ->
                app.label.lowercase(Locale.ROOT)
            }.thenBy { app ->
                app.packageName
            }
        )
}

fun scanInstalledApps(
    packageManager: PackageManager
): List<InstalledApp> {
    return packageManager
        .getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
        .mapNotNull { info ->
            val appInfo = info.applicationInfo ?: return@mapNotNull null
            val isSystem = appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0 ||
                appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0

            InstalledApp(
                label = appInfo.loadLabel(packageManager).toString(),
                packageName = info.packageName,
                versionName = info.versionName ?: "Unknown",
                versionCode = info.longVersionCode,
                isSystem = isSystem,
                icon = appInfo.loadIcon(packageManager).toBitmap(size = 96)
            )
        }
        .sortedWith(
            compareBy<InstalledApp> { it.label.lowercase(Locale.ROOT) }
                .thenBy { it.packageName }
        )
}

fun Drawable.toBitmap(size: Int): Bitmap {
    if (this is BitmapDrawable && bitmap != null) {
        return bitmap
    }

    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)

    return bitmap
}

fun openAPKMirror(
    context: Context,
    packageName: String,
    update: UpdateInfo?
) {
    val uri = update?.url?.let { android.net.Uri.parse(it) }
        ?: ApkMirrorSource.searchUrl(packageName)

    context.startActivity(
        Intent(
            Intent.ACTION_VIEW,
            uri
        )
    )
}
