package com.downly.app

import android.Manifest
import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.DisposableEffect
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

/* ------------------------------------------------------------------ */
/*  Activity                                                           */
/* ------------------------------------------------------------------ */

class MainActivity : ComponentActivity() {

    /** A link shared into the app from another app (Share > Downly). */
    private var pendingLink by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) pendingLink = linkFromIntent(intent)
        val settingsRepo = SettingsRepository.get(this)

        setContent {
            val settings by settingsRepo.settings.collectAsStateWithLifecycle()
            val dark = settings.themeMode.isDark(isSystemInDarkTheme())

            // Status / navigation bar icons follow Day or Dark.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT
                    ) { dark },
                    navigationBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT
                    ) { dark }
                )
                onDispose { }
            }

            DownlyTheme(settings.themeMode) {
                MainScreen(
                    sharedLink = pendingLink,
                    onSharedLinkConsumed = { pendingLink = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingLink = linkFromIntent(intent)
    }

    private fun linkFromIntent(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) return null
        return extractLink(intent.getStringExtra(Intent.EXTRA_TEXT))
    }
}

private fun extractLink(text: String?): String? =
    text?.let { Regex("https?://\\S+", RegexOption.IGNORE_CASE).find(it)?.value }

/* ------------------------------------------------------------------ */
/*  ViewModel                                                          */
/* ------------------------------------------------------------------ */

class MainViewModel(private val app: Application) : AndroidViewModel(app) {
    private val repo = DownloadRepository.get(app)
    private val settingsRepo = SettingsRepository.get(app)

    private val updates = UpdateManager.get(app)

    val settings: StateFlow<AppSettings> = settingsRepo.settings
    val network: StateFlow<NetworkState> = repo.networkState
    val updateState: StateFlow<UpdateState> = updates.state
    val updatesConfigured: Boolean get() = updates.isConfigured

    init {
        // Background update check follows the "Check for updates automatically" setting
        // (toggle, import and first start all go through here).
        viewModelScope.launch {
            settingsRepo.settings.map { it.autoUpdateCheck }.distinctUntilChanged()
                .collect { UpdateCheckWorker.sync(app, it) }
        }
    }

    val downloads: StateFlow<List<DownloadItem>> =
        repo.downloads.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // After add / resume the foreground service keeps the download alive in the background.
    fun add(url: String, fileName: String? = null) {
        viewModelScope.launch {
            if (repo.add(url, fileName) != null) DownloadService.start(app)
        }
    }
    suspend fun findDuplicate(url: String): DownloadItem? = repo.findByUrl(url)
    fun deleteWithFile(id: Long, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val message = when (repo.deleteWithFile(id)) {
                FileDeleteResult.DELETED -> "File deleted"
                FileDeleteResult.ALREADY_GONE -> "The file was already gone. Removed from the list."
                FileDeleteResult.FAILED ->
                    "Could not delete the file. Delete it in your Files app, then remove it from the list."
            }
            onResult(message)
        }
    }
    fun pause(id: Long) = repo.pause(id)
    fun resume(id: Long) {
        viewModelScope.launch {
            repo.resume(id)
            DownloadService.start(app)
        }
    }
    fun remove(id: Long) { viewModelScope.launch { repo.remove(id) } }
    fun pauseAll() { viewModelScope.launch { repo.pauseAll() } }
    fun clearFinished() { viewModelScope.launch { repo.clearFinished() } }

    fun setMaxParallel(count: Int) = settingsRepo.setMaxParallel(count)
    fun setWifiOnly(enabled: Boolean) = settingsRepo.setWifiOnly(enabled)
    fun setNotifications(enabled: Boolean) = settingsRepo.setNotifications(enabled)
    fun setAutoUpdateCheck(enabled: Boolean) = settingsRepo.setAutoUpdateCheck(enabled)
    fun setDownloadFolder(folderUri: String) = settingsRepo.setDownloadFolder(folderUri)
    fun setThemeMode(mode: ThemeMode) = settingsRepo.setThemeMode(mode)

    /* ---------------------------- updates ---------------------------- */

    fun checkForUpdates() { viewModelScope.launch { updates.check(manual = true) } }
    fun autoCheckForUpdates() {
        if (settingsRepo.current.autoUpdateCheck) viewModelScope.launch { updates.check(manual = false) }
    }
    fun downloadUpdate() = updates.startDownload()
    fun installUpdate(context: Context): InstallStart = updates.install(context)
    fun shouldAnnounceUpdate(version: String): Boolean = updates.shouldAnnounce(version)
    fun markUpdateAnnounced(version: String) = updates.markAnnounced(version)

    /* ------------------------ settings backup ------------------------ */

    /** Writes the settings file to [uri] (chosen by the user in the system file picker). */
    fun exportSettings(uri: Uri, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val message = withContext(Dispatchers.IO) {
                try {
                    val text = SettingsBackup.export(settingsRepo.current, BuildConfig.VERSION_NAME)
                    val stream = app.contentResolver.openOutputStream(uri, "wt")
                    if (stream == null) {
                        "Could not save the file"
                    } else {
                        stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                        "Settings exported"
                    }
                } catch (e: Exception) {
                    "Could not save the file"
                }
            }
            onResult(message)
        }
    }

    /** Reads a settings file from [uri] and applies every valid setting in it. */
    fun importSettings(uri: Uri, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val message = withContext(Dispatchers.IO) {
                try {
                    val text = app.contentResolver.openInputStream(uri)?.use { readLimited(it) }
                        ?: return@withContext "Could not read the file"
                    val (result, restored) = SettingsBackup.import(text, settingsRepo.current)
                    when (result) {
                        is SettingsBackup.ImportResult.Failure -> result.message
                        is SettingsBackup.ImportResult.Success -> {
                            settingsRepo.replaceAll(restored)
                            "Settings restored: " + result.names.joinToString(", ")
                        }
                    }
                } catch (e: Exception) {
                    "Could not read the file"
                }
            }
            onResult(message)
        }
    }

    /** A settings file is tiny: refuse anything over 64 KB instead of reading it all into memory. */
    private fun readLimited(input: InputStream): String? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            out.write(buffer, 0, read)
            if (out.size() > 64 * 1024) return null
        }
        return out.toString(Charsets.UTF_8.name())
    }
}

/* ------------------------------------------------------------------ */
/*  Main screen                                                        */
/* ------------------------------------------------------------------ */

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MainScreen(
    sharedLink: String?,
    onSharedLinkConsumed: () -> Unit,
    vm: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val network by vm.network.collectAsStateWithLifecycle()
    val updateState by vm.updateState.collectAsStateWithLifecycle()

    var showSettings by remember { mutableStateOf(false) }
    var showUpdate by remember { mutableStateOf(false) }

    // List tools: filter chips, search, details popup
    var filter by rememberSaveable { mutableStateOf(DownloadFilter.ALL) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var detailsId by remember { mutableStateOf<Long?>(null) }
    var confirmDeleteFile by remember { mutableStateOf<DownloadItem?>(null) }
    val visible = remember(downloads, filter, query) { applyListTools(downloads, filter, query) }
    val detailsItem = downloads.firstOrNull { it.id == detailsId }
    var showAdd by remember { mutableStateOf(false) }
    var addInitial by remember { mutableStateOf("") }
    var confirmCancel by remember { mutableStateOf<DownloadItem?>(null) }
    var confirmRemove by remember { mutableStateOf<DownloadItem?>(null) }

    // A link shared from another app opens the add dialog with the link filled in.
    LaunchedEffect(sharedLink) {
        if (sharedLink != null) {
            addInitial = sharedLink
            showAdd = true
        }
    }

    // Look for a new build when the app opens, and open the update popup once per new version.
    LaunchedEffect(Unit) { vm.autoCheckForUpdates() }
    LaunchedEffect(updateState) {
        val found = (updateState as? UpdateState.Available)?.info
        if (found != null && vm.shouldAnnounceUpdate(found.versionName)) {
            vm.markUpdateAnnounced(found.versionName)
            showUpdate = true
        }
    }

    // Permissions: notifications (Android 13+) and storage (Android 9 and below).
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }
    LaunchedEffect(Unit) {
        val needed = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !context.isGranted(Manifest.permission.POST_NOTIFICATIONS)
            ) add(Manifest.permission.POST_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                !context.isGranted(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            ) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
    }

    DownlyBackground {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Header(
                downloads = downloads,
                searchOpen = searchOpen,
                onSearch = {
                    if (searchOpen) query = ""
                    searchOpen = !searchOpen
                },
                onPauseAll = vm::pauseAll,
                onClearFinished = vm::clearFinished,
                onSettings = { showSettings = true }
            )
            AnimatedVisibility(
                visible = network == NetworkState.NONE,
                enter = fadeIn(tween(200)) + expandVertically(),
                exit = fadeOut(tween(150)) + shrinkVertically()
            ) {
                OfflineBanner()
            }
            AnimatedVisibility(
                visible = updateState is UpdateState.Available ||
                    updateState is UpdateState.Downloading ||
                    updateState is UpdateState.ReadyToInstall,
                enter = fadeIn(tween(200)) + expandVertically(),
                exit = fadeOut(tween(150)) + shrinkVertically()
            ) {
                UpdateBanner(state = updateState, onClick = { showUpdate = true })
            }
            if (downloads.isNotEmpty()) {
                FilterBar(
                    items = downloads,
                    filter = filter,
                    onFilter = { filter = it }
                )
            }
            AnimatedVisibility(
                visible = searchOpen && downloads.isNotEmpty(),
                enter = fadeIn(tween(200)) + expandVertically(),
                exit = fadeOut(tween(150)) + shrinkVertically()
            ) {
                SearchField(
                    query = query,
                    onQueryChange = { query = it },
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (downloads.isEmpty()) {
                    EmptyState(Modifier.align(Alignment.Center))
                } else if (visible.isEmpty()) {
                    EmptyListMessage(filter, query, Modifier.align(Alignment.Center))
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 0.dp, bottom = 100.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(visible, key = { it.id }) { item ->
                            DownloadCard(
                                item = item,
                                onPause = { vm.pause(item.id) },
                                onResume = { vm.resume(item.id) },
                                onRemove = {
                                    when {
                                        item.isCompleted -> confirmRemove = item // ask: file too?
                                        item.downloadedBytes == 0L -> vm.remove(item.id)
                                        else -> confirmCancel = item
                                    }
                                },
                                onOpen = { openDownloaded(context, item.savedUri) },
                                onDetails = { detailsId = item.id },
                                modifier = Modifier.animateItemPlacement(
                                    spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
                                )
                            )
                        }
                    }
                }
            }
        }

        AddButton(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = 18.dp),
            onClick = {
                addInitial = ""
                showAdd = true
            }
        )
    }

    if (showAdd) {
        AddLinkDialog(
            initial = addInitial,
            onFindDuplicate = vm::findDuplicate,
            onDismiss = {
                showAdd = false
                onSharedLinkConsumed()
            },
            onSubmit = { url, fileName ->
                vm.add(url, fileName)
                showAdd = false
                onSharedLinkConsumed()
            }
        )
    }

    if (showSettings) {
        SettingsDialog(
            settings = settings,
            updateState = updateState,
            updatesConfigured = vm.updatesConfigured,
            onMaxParallel = vm::setMaxParallel,
            onWifiOnly = vm::setWifiOnly,
            onNotifications = vm::setNotifications,
            onAutoUpdate = vm::setAutoUpdateCheck,
            onDownloadFolder = vm::setDownloadFolder,
            onCheckUpdate = vm::checkForUpdates,
            onOpenUpdate = { showUpdate = true },
            onInstallUpdate = { installUpdate(context, vm) },
            onExport = vm::exportSettings,
            onImport = vm::importSettings,
            onThemeMode = vm::setThemeMode,
            onClose = { showSettings = false }
        )
    }

    if (showUpdate) {
        UpdateDialog(
            state = updateState,
            onDownload = vm::downloadUpdate,
            onInstall = { installUpdate(context, vm) },
            onClose = { showUpdate = false }
        )
    }

    if (detailsItem != null) {
        DetailsDialog(
            item = detailsItem,
            onClose = { detailsId = null },
            onCopyLink = { copyLink(context, detailsItem.url) },
            onShare = { shareDownloaded(context, detailsItem) },
            onOpen = { openDownloaded(context, detailsItem.savedUri) },
            onDeleteFile = { confirmDeleteFile = detailsItem }
        )
    }

    confirmDeleteFile?.let { item ->
        ConfirmDeleteFileDialog(
            fileName = item.fileName,
            onKeep = { confirmDeleteFile = null },
            onDelete = {
                vm.deleteWithFile(item.id) { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                confirmDeleteFile = null
                detailsId = null
            }
        )
    }

    confirmRemove?.let { item ->
        ConfirmRemoveDialog(
            item = item,
            onCancel = { confirmRemove = null },
            onListOnly = {
                vm.remove(item.id)
                confirmRemove = null
            },
            onDeleteFile = {
                vm.deleteWithFile(item.id) { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                confirmRemove = null
            }
        )
    }

    confirmCancel?.let { item ->
        ConfirmCancelDialog(
            fileName = item.fileName,
            onKeep = { confirmCancel = null },
            onCancelDownload = {
                vm.remove(item.id)
                confirmCancel = null
            }
        )
    }
}

@Composable
private fun Header(
    downloads: List<DownloadItem>,
    searchOpen: Boolean,
    onSearch: () -> Unit,
    onPauseAll: () -> Unit,
    onClearFinished: () -> Unit,
    onSettings: () -> Unit
) {
    val c = Downly.colors
    val active = downloads.count { it.isActive }
    val finished = downloads.count { !it.isActive && it.state != DownloadState.PAUSED }
    val speed = downloads.sumOf { it.speedBytesPerSec }
    val subtitle = when {
        active > 0 -> "$active active · ${formatSpeed(speed)}"
        downloads.isEmpty() -> "No downloads yet"
        else -> "${downloads.size} in your list"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 4.dp)
            .glass(RoundedCornerShape(18.dp))
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("Downloads", color = c.text, fontSize = 18.sp, lineHeight = 25.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = c.textSecondary, fontSize = 11.sp, lineHeight = 15.sp)
        }
        PlainIconButton(
            icon = if (searchOpen) DownlyIcons.Close else DownlyIcons.Search,
            description = if (searchOpen) "Close search" else "Search downloads",
            tint = c.text,
            onClick = onSearch,
            enabled = downloads.isNotEmpty()
        )
        GlassIconButton(
            icon = DownlyIcons.Pause,
            description = "Pause all downloads",
            onClick = onPauseAll,
            size = 32.dp,
            enabled = active > 0
        )
        PlainIconButton(
            icon = DownlyIcons.ClearAll,
            description = "Clear finished downloads",
            tint = c.red,
            onClick = onClearFinished,
            enabled = finished > 0
        )
        PlainIconButton(
            icon = DownlyIcons.Settings,
            description = "Settings",
            tint = c.text,
            onClick = onSettings
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    val c = Downly.colors
    Column(modifier.padding(horizontal = 44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).glass(CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.CloudDownload, null, tint = c.text, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("No downloads yet", color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Tap + and paste a file link. Finished files are saved in your download folder.",
            color = c.textSecondary,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            textAlign = TextAlign.Center
        )
    }
}

/** 56 dp white circle with a black "+" (no text). */
@Composable
private fun AddButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .size(56.dp)
            .pressable(onClick = onClick)
            .whiteButton(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(DownlyIcons.Plus, contentDescription = "Add download", tint = Color.Black, modifier = Modifier.size(26.dp))
    }
}

/* ------------------------------------------------------------------ */
/*  Download card                                                      */
/* ------------------------------------------------------------------ */

@Composable
private fun DownloadCard(
    item: DownloadItem,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRemove: () -> Unit,
    onOpen: () -> Unit,
    onDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = Downly.colors
    Column(
        modifier
            .fillMaxWidth()
            .pressable(pressedScale = 0.985f, onClick = onDetails)
            .glass(RoundedCornerShape(16.dp))
            .padding(start = 12.dp, top = 12.dp, end = 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Row 1: file tile, name, status
        Row(verticalAlignment = Alignment.CenterVertically) {
            FileTile(item.fileName)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.fileName.ifBlank { "Getting file info" },
                    color = c.text,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                StatusLine(item)
            }
        }

        // Row 2: progress bar (green while running, gray when paused / waiting, hidden when done)
        if (!item.isCompleted) {
            val barColor = when (item.state) {
                DownloadState.DOWNLOADING, DownloadState.QUEUED -> c.greenBar
                else -> c.paused
            }
            Box(Modifier.padding(end = 4.dp)) {
                GlassProgress(
                    progress = item.progress,
                    indeterminate = item.state == DownloadState.DOWNLOADING && !item.hasKnownSize,
                    color = barColor
                )
            }
        } else {
            Spacer(Modifier.height(0.dp)) // keeps the same gap the design has on finished cards
        }

        // Row 3: size info, white round button, plain red icon
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                sizeLabel(item),
                color = c.textSecondary,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                modifier = Modifier.weight(1f)
            )
            when {
                item.isCompleted -> {
                    GlassIconButton(DownlyIcons.OpenFile, "Open file", onOpen, Modifier.offset(x = (-3).dp))
                    PlainIconButton(DownlyIcons.Trash, "Remove from list", c.red, onRemove)
                }
                item.canPause -> {
                    GlassIconButton(DownlyIcons.Pause, "Pause", onPause, Modifier.offset(x = (-3).dp))
                    PlainIconButton(DownlyIcons.Close, "Cancel download", c.red, onRemove)
                }
                else -> {
                    GlassIconButton(
                        icon = if (item.state == DownloadState.FAILED) DownlyIcons.Retry else DownlyIcons.Play,
                        description = if (item.state == DownloadState.FAILED) "Retry" else "Resume",
                        onClick = onResume,
                        modifier = Modifier.offset(x = (-3).dp)
                    )
                    PlainIconButton(DownlyIcons.Close, "Cancel download", c.red, onRemove)
                }
            }
        }

        if (item.state == DownloadState.FAILED && !item.error.isNullOrBlank()) {
            Text(item.error, color = c.red, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(end = 4.dp))
        }
    }
}

/** "612.4 / 916.3 MB · 40s left" while running, "4.2 MB · Downloads" when finished. */
internal fun sizeLabel(item: DownloadItem): String = when {
    item.isCompleted ->
        "${formatBytes(maxOf(item.totalBytes, item.downloadedBytes))} · ${SaveLocation.savedFolderLabel(item.savedUri)}"
    item.hasKnownSize -> {
        val done = formatBytes(item.downloadedBytes)
        val total = formatBytes(item.totalBytes)
        val unit = total.substringAfter(' ', "")
        val head = if (unit.isNotEmpty() && done.endsWith(" $unit")) done.removeSuffix(" $unit") else done
        buildString {
            append("$head / $total")
            if (item.state == DownloadState.DOWNLOADING && item.etaSeconds >= 0) {
                append(" · ${formatEta(item.etaSeconds)} left")
            }
        }
    }
    else -> formatBytes(item.downloadedBytes)
}

/** 12 sp status text: green + bold while running / completed, gray when paused or waiting. */
@Composable
internal fun StatusLine(item: DownloadItem) {
    val c = Downly.colors
    val (label, color, weight) = when (item.state) {
        DownloadState.DOWNLOADING -> {
            val text = when {
                item.savingPercent >= 0 -> "Saving file · ${item.savingPercent}%"
                item.speedBytesPerSec > 0 ->
                    if (item.hasKnownSize) "${item.percent}% · ${formatSpeed(item.speedBytesPerSec)}"
                    else formatSpeed(item.speedBytesPerSec)
                else -> "Connecting"
            }
            Triple(text, c.green, FontWeight.Bold)
        }
        DownloadState.QUEUED -> Triple("Waiting in queue", c.textSecondary, FontWeight.Medium)
        DownloadState.WAITING ->
            Triple(item.error?.takeIf { it.isNotBlank() } ?: "Waiting for network", c.textSecondary, FontWeight.Medium)
        DownloadState.PAUSED ->
            Triple(if (item.hasKnownSize) "Paused · ${item.percent}%" else "Paused", c.textSecondary, FontWeight.Medium)
        DownloadState.FAILED -> Triple("Failed", c.red, FontWeight.Bold)
        DownloadState.COMPLETED -> Triple("Completed", c.green, FontWeight.Bold)
        DownloadState.CANCELED -> Triple("Canceled", c.textSecondary, FontWeight.Medium)
    }
    val animated by animateColorAsState(color, tween(300), label = "status")
    Text(label, color = animated, fontSize = 12.sp, lineHeight = 17.sp, fontWeight = weight, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** 34 dp glass tile with a thin file-type icon. */
@Composable
internal fun FileTile(fileName: String) {
    val c = Downly.colors
    Box(Modifier.size(34.dp).glass(RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
        Icon(fileKind(fileName), null, tint = c.text, modifier = Modifier.size(17.dp))
    }
}

private fun fileKind(name: String): ImageVector =
    when (name.substringAfterLast('.', "").lowercase()) {
        "mp4", "mkv", "avi", "mov", "webm", "3gp", "flv", "m4v" -> DownlyIcons.FileVideo
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "svg" -> DownlyIcons.FileImage
        "mp3", "wav", "flac", "aac", "m4a", "ogg", "opus" -> DownlyIcons.FileAudio
        "zip", "rar", "7z", "tar", "gz", "xz" -> DownlyIcons.FileZip
        "apk", "aab", "xapk" -> DownlyIcons.FileApp
        "pdf", "doc", "docx", "txt", "xls", "xlsx", "ppt", "pptx", "epub" -> DownlyIcons.FileDoc
        else -> DownlyIcons.FileOther
    }

/** 6 dp progress bar, fully rounded, flat colour (no gradient). */
@Composable
internal fun GlassProgress(progress: Float, indeterminate: Boolean, color: Color, modifier: Modifier = Modifier) {
    val c = Downly.colors
    val animated by animateFloatAsState(progress, tween(450, easing = LinearOutSlowInEasing), label = "progress")
    val sweep = rememberInfiniteTransition(label = "sweep")
    val x by sweep.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing)),
        label = "sweepX"
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(c.track)
    ) {
        if (indeterminate) {
            Box(
                Modifier.fillMaxSize().drawBehind {
                    val w = size.width * 0.35f
                    val start = (size.width + w) * x - w
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            listOf(color.copy(alpha = 0f), color, color.copy(alpha = 0f)),
                            startX = start,
                            endX = start + w
                        ),
                        topLeft = Offset(start, 0f),
                        size = Size(w, size.height),
                        cornerRadius = CornerRadius(size.height / 2)
                    )
                }
            )
        } else {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animated.coerceIn(0f, 1f))
                    .background(color)
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/*  Dialogs                                                            */
/* ------------------------------------------------------------------ */

@Composable
private fun AddLinkDialog(
    initial: String,
    onFindDuplicate: suspend (String) -> DownloadItem?,
    onDismiss: () -> Unit,
    onSubmit: (url: String, fileName: String?) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    var text by remember { mutableStateOf(initial) }
    var name by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    var duplicate by remember { mutableStateOf<DownloadItem?>(null) }
    var checking by remember { mutableStateOf(false) }
    var probe by remember { mutableStateOf<LinkProbe?>(null) }
    val canDownload = probe?.status == ProbeStatus.OK || probe?.status == ProbeStatus.WARN

    LaunchedEffect(Unit) {
        delay(250)
        runCatching { focus.requestFocus() }
        // Nothing typed yet and a web link is on the clipboard: suggest it.
        if (text.isBlank()) {
            val link = runCatching { clipboard.getText()?.text }.getOrNull()?.let { extractLink(it) }
            if (link != null) text = link
        }
    }

    fun connect() {
        val value = text.trim()
        if (!DownloadEngine.isValidUrl(value)) {
            showError = true
            return
        }
        probe = null
        checking = true
        scope.launch {
            val result = LinkChecker.check(value)
            checking = false
            // Ignore the answer if the link was edited while checking.
            if (value == text.trim()) probe = result
        }
    }

    fun submit(skipDuplicateCheck: Boolean = false) {
        val value = text.trim()
        if (!DownloadEngine.isValidUrl(value)) {
            showError = true
            return
        }
        if (!canDownload) return
        val customName = name.trim().ifBlank { null }
        if (skipDuplicateCheck) {
            onSubmit(value, customName)
            return
        }
        scope.launch {
            val existing = onFindDuplicate(value)
            if (existing == null) onSubmit(value, customName) else duplicate = existing
        }
    }

    GlassDialog(onDismiss) {
        Text("Add download", color = Downly.colors.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("Paste a direct link to the file you want.", color = Downly.colors.textSecondary, fontSize = 14.sp)
        Spacer(Modifier.height(18.dp))

        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                showError = false
                duplicate = null
                probe = null
            },
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            placeholder = { Text("https://example.com/file.zip") },
            isError = showError,
            maxLines = 3,
            shape = RoundedCornerShape(18.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            trailingIcon = {
                Icon(
                    Icons.Rounded.ContentPaste,
                    contentDescription = "Paste from clipboard",
                    tint = Downly.colors.text,
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .clip(CircleShape)
                        .clickable {
                            clipboard.getText()?.text?.let {
                                text = extractLink(it) ?: it.trim()
                                showError = false
                                duplicate = null
                                probe = null
                            }
                        }
                        .padding(10.dp)
                        .size(22.dp)
                )
            },
            colors = glassFieldColors()
        )
        if (showError) {
            Spacer(Modifier.height(8.dp))
            Text("That doesn't look like a valid link. Check it and try again.", color = Downly.colors.red, fontSize = 13.sp)
        }

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("File name (optional)") },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { if (canDownload) submit() else connect() }),
            colors = glassFieldColors()
        )

        duplicate?.let { existing ->
            Spacer(Modifier.height(12.dp))
            Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)).padding(12.dp)) {
                Text("Already in your list", color = Downly.colors.paused, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(
                    "${existing.fileName.ifBlank { "This link" }}, ${stateLabel(existing).lowercase()}",
                    color = Downly.colors.textSecondary,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PillButton(
                text = when {
                    checking -> "Connecting"
                    probe != null -> "Connect again"
                    else -> "Connect"
                },
                onClick = { connect() },
                primary = false,
                enabled = !checking && text.isNotBlank()
            )
            Spacer(Modifier.width(12.dp))
            val result = probe
            when {
                checking -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Downly.colors.text
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Checking the link...", color = Downly.colors.textSecondary, fontSize = 13.sp)
                }
                result != null -> {
                    val color = when (result.status) {
                        ProbeStatus.OK -> Downly.colors.green
                        ProbeStatus.WARN -> Downly.colors.paused
                        ProbeStatus.FAIL -> Downly.colors.red
                    }
                    Box(Modifier.size(12.dp).clip(CircleShape).background(color))
                    Spacer(Modifier.width(8.dp))
                    Text(result.message, color = color, fontSize = 13.sp)
                }
                else -> Text("Tap Connect to check the link first.", color = Downly.colors.textSecondary, fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            PillButton("Cancel", onClick = onDismiss, primary = false)
            Spacer(Modifier.width(10.dp))
            if (duplicate != null) {
                PillButton(
                    "Download again",
                    onClick = { submit(skipDuplicateCheck = true) },
                    icon = Icons.Rounded.Download,
                    enabled = canDownload,
                    green = true
                )
            } else {
                PillButton(
                    "Download",
                    onClick = { submit() },
                    icon = Icons.Rounded.Download,
                    enabled = canDownload,
                    green = true
                )
            }
        }
    }
}

@Composable
private fun ConfirmCancelDialog(fileName: String, onKeep: () -> Unit, onCancelDownload: () -> Unit) {
    GlassDialog(onKeep) {
        Text("Cancel this download?", color = Downly.colors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "${fileName.ifBlank { "This file" }} will be removed and the part already downloaded will be deleted.",
            color = Downly.colors.textSecondary,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            PillButton("Keep", onClick = onKeep, primary = false)
            Spacer(Modifier.width(10.dp))
            PillButton("Cancel download", onClick = onCancelDownload, danger = true)
        }
    }
}

@Composable
private fun SettingsDialog(
    settings: AppSettings,
    updateState: UpdateState,
    updatesConfigured: Boolean,
    onMaxParallel: (Int) -> Unit,
    onWifiOnly: (Boolean) -> Unit,
    onNotifications: (Boolean) -> Unit,
    onAutoUpdate: (Boolean) -> Unit,
    onDownloadFolder: (String) -> Unit,
    onCheckUpdate: () -> Unit,
    onOpenUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
    onExport: (Uri, (String) -> Unit) -> Unit,
    onImport: (Uri, (String) -> Unit) -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val showMessage: (String) -> Unit = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }

    // System file picker: the user chooses where the backup is saved / which file is restored.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> if (uri != null) onExport(uri, showMessage) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) onImport(uri, showMessage) }
    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            if (SaveLocation.keepAccess(context, uri)) onDownloadFolder(uri.toString())
            else showMessage("Can't use this folder, choose another one")
        }
    }

    GlassDialog(onClose) {
        Text("Settings", color = Downly.colors.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))

        SettingRow(title = SettingNames.THEME, subtitle = "Day or AMOLED dark") {
            ThemeSegmented(settings.themeMode, onThemeMode)
        }
        SettingDivider()
        SettingRow(
            title = SettingNames.MAX_PARALLEL,
            subtitle = "How many files download at the same time"
        ) {
            Stepper(
                value = settings.maxParallel,
                min = AppSettings.MIN_PARALLEL,
                max = AppSettings.MAX_PARALLEL,
                onChange = onMaxParallel
            )
        }
        SettingDivider()
        SettingRow(
            title = SettingNames.WIFI_ONLY,
            subtitle = "Wait for Wi-Fi, never use mobile data"
        ) {
            GlassSwitch(settings.wifiOnly, onWifiOnly)
        }
        SettingDivider()
        SettingRow(
            title = SettingNames.NOTIFICATIONS,
            subtitle = "Tell me when a download is finished or failed"
        ) {
            GlassSwitch(settings.notifications, onNotifications)
        }
        SettingDivider()
        SettingRow(
            title = SettingNames.DOWNLOAD_FOLDER,
            subtitle = "Save to: ${SaveLocation.folderLabel(settings.downloadFolder)}"
        ) {
            PillButton(
                "Change",
                onClick = {
                    runCatching { folderLauncher.launch(null) }
                        .onFailure { showMessage("No folder picker found on this phone") }
                },
                primary = false
            )
        }
        if (settings.downloadFolder.isNotBlank()) {
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.End) {
                PillButton("Use Downloads folder", onClick = { onDownloadFolder("") }, primary = false)
            }
        }

        SettingSection("Updates")
        SettingRow(
            title = SettingNames.AUTO_UPDATE_CHECK,
            subtitle = "Look for a new version in the background and tell me"
        ) {
            GlassSwitch(settings.autoUpdateCheck, onAutoUpdate)
        }
        SettingDivider()
        UpdateSettingsRow(
            state = updateState,
            configured = updatesConfigured,
            onCheck = onCheckUpdate,
            onOpen = onOpenUpdate,
            onInstall = onInstallUpdate
        )

        SettingSection("Backup")
        SettingRow(
            title = "Export settings",
            subtitle = "Save your settings to a file you can keep"
        ) {
            PillButton("Export", onClick = { exportLauncher.launch(SettingsBackup.suggestedFileName()) }, primary = false)
        }
        SettingDivider()
        SettingRow(
            title = "Import settings",
            subtitle = "Restore settings from a Downly backup file"
        ) {
            PillButton("Import", onClick = { importLauncher.launch(arrayOf("*/*")) }, primary = false)
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "Downly ${appVersion(context)}".trim(),
            color = Downly.colors.textSecondary,
            fontSize = 13.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            PillButton("Done", onClick = onClose)
        }
    }
}

/** Small caps label that groups rows inside the Settings popup. */
@Composable
private fun SettingSection(title: String) {
    Text(
        title.uppercase(),
        color = Downly.colors.text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 20.dp, bottom = 2.dp)
    )
}

@Composable
internal fun SettingRow(title: String, subtitle: String, control: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = Downly.colors.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = Downly.colors.textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
        }
        control()
    }
}

@Composable
internal fun SettingDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.10f)))
}

@Composable
private fun Stepper(value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        GlassIconButton(
            icon = Icons.Rounded.Remove,
            description = "Fewer downloads",
            onClick = { onChange(value - 1) },
            size = 34.dp,
            enabled = value > min
        )
        Text(
            "$value",
            color = Downly.colors.text,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 32.dp)
        )
        GlassIconButton(
            icon = Icons.Rounded.Add,
            description = "More downloads",
            onClick = { onChange(value + 1) },
            size = 34.dp,
            enabled = value < max
        )
    }
}

@Composable
internal fun GlassSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val c = Downly.colors
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.Black,
            checkedTrackColor = Color.White,
            checkedBorderColor = c.whiteBorder,
            uncheckedThumbColor = c.paused,
            uncheckedTrackColor = c.toggleOffTrack,
            uncheckedBorderColor = c.toggleOffBorder
        )
    )
}

/** Shown under the header while the phone has no internet. */
@Composable
private fun OfflineBanner() {
    Row(
        Modifier
            .padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
            .fillMaxWidth()
            .glass(RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.WifiOff, null, tint = Downly.colors.paused, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            "No internet connection. Downloads continue when you are back online.",
            color = Downly.colors.text,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
    }
}

/* ------------------------------------------------------------------ */
/*  Helpers                                                            */
/* ------------------------------------------------------------------ */

private fun appVersion(context: Context): String = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
} catch (e: Exception) {
    ""
}

/** Starts the system installer for the downloaded update and explains the one case that needs a user action. */
private fun installUpdate(context: Context, vm: MainViewModel) {
    when (vm.installUpdate(context)) {
        InstallStart.STARTED -> Unit
        InstallStart.NEEDS_PERMISSION -> Toast.makeText(
            context, "Allow Downly to install apps, then come back and tap Install", Toast.LENGTH_LONG
        ).show()
        InstallStart.FAILED -> Toast.makeText(context, "Could not start the installer", Toast.LENGTH_LONG).show()
    }
}

private fun Context.isGranted(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

private fun openDownloaded(context: Context, savedUri: String?) {
    if (savedUri.isNullOrBlank()) return
    try {
        val uri = Uri.parse(savedUri)
        val intent = if (uri.scheme == "content") {
            val type = context.contentResolver.getType(uri) ?: "*/*"
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, type)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else {
            // Android 9 and below saves a plain file path: open the system Downloads list instead.
            Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)
        }
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(context, "Can't open this file", Toast.LENGTH_SHORT).show()
    }
}
