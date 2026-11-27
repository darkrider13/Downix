package com.downix.app

import android.Manifest
import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.DisposableEffect
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.animation.scaleOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.requiredSize
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
import androidx.compose.ui.draw.drawWithContent
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

    /** A link sent into the app from another app (Share > Downix, or "Open with" > Downix). */
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
                // Window colour behind the UI follows the chosen mode (no black flash in Day mode).
                window.setBackgroundDrawable(
                    android.graphics.drawable.ColorDrawable(if (dark) 0xFF000000.toInt() else 0xFFE8EEF5.toInt())
                )
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

            DownixTheme(settings.themeMode) {
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

    private fun linkFromIntent(intent: Intent?): String? = when (intent?.action) {
        // Share > Downix
        Intent.ACTION_SEND -> extractLink(intent.getStringExtra(Intent.EXTRA_TEXT))
        // a download link tapped in another app, Downix picked in "Open with"
        Intent.ACTION_VIEW -> intent.data
            ?.takeIf { it.scheme.equals("http", true) || it.scheme.equals("https", true) }
            ?.toString()
        else -> null
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

    // null = the list is still being read from the database (so the screen shows nothing yet,
    // instead of flashing "No downloads yet" for a moment).
    val downloads: StateFlow<List<DownloadItem>?> =
        repo.downloads.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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
                FileDeleteResult.DELETED -> "File deleted from phone"
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
    fun removeMany(ids: List<Long>) { viewModelScope.launch { ids.forEach { repo.remove(it) } } }
    fun deleteManyWithFiles(ids: List<Long>, onResult: (String) -> Unit) {
        viewModelScope.launch {
            var failed = 0
            ids.forEach { if (repo.deleteWithFile(it) == FileDeleteResult.FAILED) failed++ }
            onResult(
                if (failed == 0) "${ids.size} deleted"
                else "$failed of ${ids.size} files could not be deleted. Delete them in your Files app."
            )
        }
    }
    fun pauseAll() { viewModelScope.launch { repo.pauseAll() } }
    fun clearFinished() { viewModelScope.launch { repo.clearFinished() } }

    /** Saves the changed settings at once (every tap in Settings). */
    fun applySettings(new: AppSettings) {
        val old = settingsRepo.current
        if (new.maxParallel != old.maxParallel) settingsRepo.setMaxParallel(new.maxParallel)
        if (new.wifiOnly != old.wifiOnly) settingsRepo.setWifiOnly(new.wifiOnly)
        if (new.notifications != old.notifications) settingsRepo.setNotifications(new.notifications)
        if (new.downloadFolder != old.downloadFolder) settingsRepo.setDownloadFolder(new.downloadFolder)
        if (new.themeMode != old.themeMode) settingsRepo.setThemeMode(new.themeMode)
    }

    /* ---------------------------- updates ---------------------------- */

    fun checkForUpdates() { viewModelScope.launch { updates.check(manual = true) } }
    fun autoCheckForUpdates() { viewModelScope.launch { updates.check(manual = false) } }
    fun downloadUpdate() = updates.startDownload()
    fun installUpdate(context: Context): InstallStart = updates.install(context)

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
    val loadedDownloads by vm.downloads.collectAsStateWithLifecycle()
    val listLoaded = loadedDownloads != null
    val downloads = loadedDownloads ?: emptyList()
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
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var toastText by remember { mutableStateOf<String?>(null) }
    var lastToast by remember { mutableStateOf("") }
    var toastTick by remember { mutableStateOf(0) }
    val showToast: (String) -> Unit = { message ->
        lastToast = message
        toastText = message
        toastTick++
    }
    // The toast hides itself after 2.6 s (a new message restarts the timer).
    LaunchedEffect(toastTick) {
        if (toastTick > 0) {
            delay(2600)
            toastText = null
        }
    }
    val clipboardManager = LocalClipboardManager.current
    var addInitial by remember { mutableStateOf("") }
    var confirmCancel by remember { mutableStateOf<DownloadItem?>(null) }
    var confirmRemove by remember { mutableStateOf<DownloadItem?>(null) }

    // Multi-select: long press a card to start, tap cards to add / remove, then delete them together.
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var confirmBulk by remember { mutableStateOf(false) }
    val selecting = selected.isNotEmpty()
    LaunchedEffect(filter, query) { selected = emptySet() }
    LaunchedEffect(visible) {
        val ids = visible.map { it.id }.toSet()
        if (!ids.containsAll(selected)) selected = selected intersect ids
    }
    LaunchedEffect(selected) { if (selected.isEmpty()) confirmBulk = false }
    BackHandler(enabled = selecting) { selected = emptySet() }

    // Same questions as before: finished -> "Delete download?", running / paused -> "Cancel this download?",
    // nothing downloaded yet -> removed at once.
    val requestRemove: (DownloadItem) -> Unit = { item ->
        when {
            item.isCompleted -> confirmRemove = item
            item.downloadedBytes == 0L -> vm.remove(item.id)
            else -> confirmCancel = item
        }
    }

    // A link shared from another app opens the add dialog with the link filled in.
    LaunchedEffect(sharedLink) {
        if (sharedLink != null) {
            addInitial = sharedLink
            showAdd = true
        }
    }

    // The check starts 2 seconds after the app opens. A new version only lights a small green dot on the
    // Settings icon (no banner, no popup); the update itself is opened from Settings > Updates.
    LaunchedEffect(Unit) {
        delay(2000)
        vm.autoCheckForUpdates()
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

    DownixBackground(Modifier.blurWhen(PopupLayers.count > 0)) {
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
                onSettings = { showSettings = true },
                updatePending = updateState is UpdateState.Available ||
                    updateState is UpdateState.Downloading ||
                    updateState is UpdateState.ReadyToInstall,
                loaded = listLoaded,
                selectedCount = selected.size,
                allSelected = visible.isNotEmpty() && selected.size == visible.size,
                onExitSelection = { selected = emptySet() },
                onSelectAll = { selected = visible.map { it.id }.toSet() },
                onDeleteSelected = { confirmBulk = true }
            )
            AnimatedVisibility(
                visible = network == NetworkState.NONE,
                enter = fadeIn(tween(200)) + expandVertically(),
                exit = fadeOut(tween(150)) + shrinkVertically()
            ) {
                OfflineBanner()
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
                if (!listLoaded) {
                    // still reading the list: show nothing for a moment
                } else if (downloads.isEmpty()) {
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
                                selected = item.id in selected,
                                selectionMode = selecting,
                                onPause = { vm.pause(item.id) },
                                onResume = { vm.resume(item.id) },
                                onRemove = { requestRemove(item) },
                                onOpen = { openDownloaded(context, item.savedUri, showToast) },
                                onDetails = { detailsId = item.id },
                                onToggleSelect = {
                                    selected = if (item.id in selected) selected - item.id else selected + item.id
                                },
                                onLongPress = { if (item.id !in selected) selected = selected + item.id }
                            )
                        }
                    }
                }
            }
        }

        PlusMenu(
            expanded = menuOpen,
            onToggle = { menuOpen = !menuOpen },
            onClose = { menuOpen = false },
            onAddLink = {
                menuOpen = false
                addInitial = ""
                showAdd = true
            },
            onFromClipboard = {
                menuOpen = false
                // Same clipboard logic as the Add dialog: find a web link in the copied text.
                val copied = runCatching { clipboardManager.getText()?.text }.getOrNull()
                val link = copied?.let { extractLink(it) }
                if (link != null) {
                    addInitial = link
                    showAdd = true
                } else {
                    showToast("No link found on the clipboard")
                }
            }
        )

        AnimatedVisibility(
            visible = showSettings,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(150))
        ) {
            SettingsScreen(
                settings = settings,
                updateState = updateState,
                updatesConfigured = vm.updatesConfigured,
                onApply = vm::applySettings,
                onCheckUpdate = vm::checkForUpdates,
                onOpenUpdate = { showUpdate = true },
                onInstallUpdate = { installUpdate(context, vm, showToast) },
                onMessage = showToast,
                onClose = { showSettings = false }
            )
        }

        // drawn last, so a message is visible above the Settings screen too
        AnimatedVisibility(
            visible = toastText != null,
            enter = EnterTransition.None,
            exit = ExitTransition.None,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 84.dp, bottom = 22.dp)
        ) {
            DownixToast(text = toastText ?: lastToast)
        }
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

    if (showUpdate) {
        UpdateDialog(
            state = updateState,
            onDownload = vm::downloadUpdate,
            onInstall = { installUpdate(context, vm, showToast) },
            onClose = { showUpdate = false }
        )
    }

    if (detailsItem != null) {
        DetailsDialog(
            item = detailsItem,
            onClose = { detailsId = null },
            onCopyLink = { copyLink(context, detailsItem.url, showToast) },
            onShare = { shareDownloaded(context, detailsItem, showToast) },
            onOpen = { openDownloaded(context, detailsItem.savedUri, showToast) },
            onDeleteFile = { confirmDeleteFile = detailsItem }
        )
    }

    confirmDeleteFile?.let { item ->
        ConfirmDeleteFileDialog(
            fileName = item.fileName,
            onKeep = { confirmDeleteFile = null },
            onDelete = {
                showToast("File deleted from phone")
                vm.deleteWithFile(item.id) { if (it != "File deleted from phone") showToast(it) }
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
                showToast("Removed from list")
            },
            onDeleteFile = {
                showToast("File deleted from phone")
                vm.deleteWithFile(item.id) { if (it != "File deleted from phone") showToast(it) }
                confirmRemove = null
            }
        )
    }

    if (confirmBulk) {
        val chosen = downloads.filter { it.id in selected }
        if (chosen.isNotEmpty()) {
            ConfirmBulkDeleteDialog(
                count = chosen.size,
                hasFinished = chosen.any { it.isCompleted },
                onCancel = { confirmBulk = false },
                onListOnly = {
                    vm.removeMany(chosen.map { it.id })
                    showToast(if (chosen.size == 1) "Removed from list" else "${chosen.size} removed from the list")
                    selected = emptySet()
                    confirmBulk = false
                },
                onDeleteFiles = {
                    showToast("${chosen.size} deleted")
                    vm.deleteManyWithFiles(chosen.map { it.id }) { if (it != "${chosen.size} deleted") showToast(it) }
                    selected = emptySet()
                    confirmBulk = false
                }
            )
        }
    }

    confirmCancel?.let { item ->
        ConfirmCancelDialog(
            fileName = item.fileName,
            onKeep = { confirmCancel = null },
            onCancelDownload = {
                vm.remove(item.id)
                confirmCancel = null
                showToast("Download cancelled")
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
    onSettings: () -> Unit,
    updatePending: Boolean = false,
    loaded: Boolean = true,
    selectedCount: Int = 0,
    allSelected: Boolean = false,
    onExitSelection: () -> Unit = {},
    onSelectAll: () -> Unit = {},
    onDeleteSelected: () -> Unit = {}
) {
    // keeps the last count while the selection bar fades out, so it never shows "0 selected"
    val lastCount = remember { intArrayOf(0) }
    if (selectedCount > 0) lastCount[0] = selectedCount
    Crossfade(targetState = selectedCount > 0, animationSpec = tween(200, easing = FastOutSlowInEasing), label = "headerMode") { selecting ->
        if (selecting) {
            SelectionHeader(
                count = if (selectedCount > 0) selectedCount else lastCount[0],
                allSelected = allSelected,
                onExit = onExitSelection,
                onSelectAll = onSelectAll,
                onDelete = onDeleteSelected
            )
        } else {
            NormalHeader(downloads, searchOpen, onSearch, onPauseAll, onClearFinished, onSettings, updatePending, loaded)
        }
    }
}

/** Replaces the normal header while cards are selected: close, count, select all, red trash. */
@Composable
private fun SelectionHeader(
    count: Int,
    allSelected: Boolean,
    onExit: () -> Unit,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit
) {
    val c = Downix.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 4.dp)
            .padding(start = 0.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlainIconButton(icon = DownixIcons.Close, description = "Cancel selection", tint = c.text, onClick = onExit)
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text("$count selected", color = c.text, fontSize = 18.sp, lineHeight = 25.sp, fontWeight = FontWeight.Bold)
            Text("Tap cards to select more", color = c.textSecondary, fontSize = 11.sp, lineHeight = 15.sp)
        }
        PlainIconButton(
            icon = DownixIcons.SelectAll,
            description = "Select all",
            tint = c.text,
            onClick = onSelectAll,
            enabled = !allSelected
        )
        PlainIconButton(icon = DownixIcons.Trash, description = "Delete selected", tint = c.red, onClick = onDelete)
    }
}

@Composable
private fun NormalHeader(
    downloads: List<DownloadItem>,
    searchOpen: Boolean,
    onSearch: () -> Unit,
    onPauseAll: () -> Unit,
    onClearFinished: () -> Unit,
    onSettings: () -> Unit,
    updatePending: Boolean = false,
    loaded: Boolean = true
) {
    val c = Downix.colors
    val active = downloads.count { it.isActive }
    val finished = downloads.count { !it.isActive && it.state != DownloadState.PAUSED }
    val speed = downloads.sumOf { it.speedBytesPerSec }
    val subtitle = when {
        !loaded -> ""
        active > 0 -> "$active active · ${formatSpeed(speed)}"
        downloads.isEmpty() -> "No downloads yet"
        else -> "${downloads.size} in your list"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 4.dp)
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("Downloads", color = c.text, fontSize = 18.sp, lineHeight = 25.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = c.textSecondary, fontSize = 11.sp, lineHeight = 15.sp)
        }
        PlainIconButton(
            icon = if (searchOpen) DownixIcons.Close else DownixIcons.Search,
            description = if (searchOpen) "Close search" else "Search downloads",
            tint = c.text,
            onClick = onSearch,
            enabled = downloads.isNotEmpty()
        )
        AnimatedIconButton(
            icon = DownixIcons.Pause,
            description = "Pause all downloads",
            tint = c.text,
            onClick = onPauseAll,
            enabled = active > 0
        )
        PlainIconButton(
            icon = DownixIcons.Trash,
            description = "Clear finished downloads",
            tint = c.red,
            onClick = onClearFinished,
            enabled = finished > 0
        )
        Box {
            PlainIconButton(
                icon = DownixIcons.Settings,
                description = if (updatePending) "Settings, update available" else "Settings",
                tint = c.text,
                onClick = onSettings
            )
            if (updatePending) {
                // soft green dot with a faint ring and a clear gap between them
                UpdateDot(Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 4.dp))
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    val c = Downix.colors
    Column(modifier.padding(horizontal = 44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.CloudDownload, null, tint = c.text, modifier = Modifier.size(48.dp))
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

/**
 * Plus button + animated menu (design: plus-menu).
 * Closed: 56 white circle with a black "+". Open: "+" turns 45 degrees into "x" (250 ms), overlay fades in (200 ms),
 * and two options rise above it: "Add link" (bottom) and "From clipboard" (above), the second 60 ms later.
 */
@Composable
private fun BoxScope.PlusMenu(
    expanded: Boolean,
    onToggle: () -> Unit,
    onClose: () -> Unit,
    onAddLink: () -> Unit,
    onFromClipboard: () -> Unit
) {
    val c = Downix.colors
    BackHandler(enabled = expanded, onBack = onClose)

    // ONE value drives the overlay, the "+" turning and both options. It is read only while drawing
    // (graphicsLayer / drawBehind), so opening the menu does not recompose anything: fast and smooth.
    val open = animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(if (expanded) 200 else 140, easing = FastOutSlowInEasing),
        label = "plusMenu"
    )

    // Always drawn (it fades with the menu), but it only has a touch handler while the menu is open.
    // When closed it must NOT take touches, or the whole screen behind it stops responding.
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind { drawRect(c.plusOverlay, alpha = open.value) }
            .then(
                if (expanded) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClose
                    )
                } else Modifier
            )
    )

    Column(
        Modifier
            .align(Alignment.BottomEnd)
            .navigationBarsPadding()
            .padding(end = 21.dp, bottom = 88.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        PlusMenuOption("From clipboard", DownixIcons.Clipboard, expanded, { ((open.value - 0.2f) / 0.8f).coerceIn(0f, 1f) }, onClick = onFromClipboard)
        PlusMenuOption("Add link", DownixIcons.Link, expanded, { open.value }, onClick = onAddLink)
    }

    Box(
        Modifier
            .align(Alignment.BottomEnd)
            .navigationBarsPadding()
            .padding(end = 16.dp, bottom = 18.dp)
            .size(56.dp)
            .pressable(pressedScale = 0.9f, onClick = onToggle)
            .whiteButton(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            DownixIcons.Plus,
            contentDescription = if (expanded) "Close menu" else "Add download",
            tint = Color.Black,
            modifier = Modifier.size(26.dp).graphicsLayer { rotationZ = 45f * open.value }
        )
    }
}

/**
 * One menu row: label chip + round white icon. Fades and rises 14 dp while it opens (no bounce, no shadows),
 * so it is light to draw. [progress] is read only in graphicsLayer.
 */
@Composable
private fun PlusMenuOption(
    label: String,
    icon: ImageVector,
    visible: Boolean,
    progress: () -> Float,
    onClick: () -> Unit
) {
    val c = Downix.colors
    Row(
        Modifier.graphicsLayer {
            val p = progress()
            alpha = p
            translationY = (1f - p) * 14.dp.toPx()
        },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .then(if (visible) Modifier.pressable(onClick = onClick) else Modifier)
                .glass(RoundedCornerShape(12.dp), strong = true, flat = true)
                .heightIn(min = 36.dp)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(label, color = c.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .size(46.dp)
                .then(if (visible) Modifier.pressable(onClick = onClick) else Modifier)
                .clip(CircleShape)
                .background(Color.White, CircleShape)
                .border(1.dp, c.whiteBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = Color.Black, modifier = Modifier.size(20.dp))
        }
    }
}

/* ------------------------------------------------------------------ */
/*  Download card                                                      */
/* ------------------------------------------------------------------ */

@Composable
private fun DownloadCard(
    item: DownloadItem,
    selected: Boolean,
    selectionMode: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRemove: () -> Unit,
    onOpen: () -> Unit,
    onDetails: () -> Unit,
    onToggleSelect: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = Downix.colors
    val cardShape = RoundedCornerShape(16.dp)
    // 0 -> 1 while selection mode turns on: the checkbox slot grows, the box fades and scales in, all together
    val sel by animateFloatAsState(if (selectionMode) 1f else 0f, tween(240, easing = FastOutSlowInEasing), label = "selectMode")
    val borderAlpha by animateFloatAsState(if (selected) 0.9f else 0f, tween(200, easing = FastOutSlowInEasing), label = "selectBorder")
    Column(
        modifier
            .fillMaxWidth()
            .pressable(
                pressedScale = 0.985f,
                onLongClick = onLongPress,
                onClick = if (selectionMode) onToggleSelect else onDetails
            )
            .glass(cardShape)
            .then(if (borderAlpha > 0.01f) Modifier.border(1.5.dp, c.text.copy(alpha = borderAlpha), cardShape) else Modifier)
            .padding(start = 12.dp, top = 12.dp, end = 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Row 1: file tile, name, status
        Row(verticalAlignment = Alignment.CenterVertically) {
            // checkbox (22) + gap (10) = 32 dp slot that opens smoothly instead of popping in
            Box(Modifier.width(32.dp * sel), contentAlignment = Alignment.CenterStart) {
                DownixCheckbox(
                    selected,
                    Modifier
                        .requiredSize(22.dp)
                        .graphicsLayer {
                            alpha = sel
                            val k = 0.6f + 0.4f * sel
                            scaleX = k
                            scaleY = k
                        }
                )
            }
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
                    color = barColor,
                    active = item.state == DownloadState.DOWNLOADING
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
                    AnimatedIconButton(DownixIcons.OpenFile, "Open file", c.text, onOpen, enabled = !selectionMode)
                }
                item.canPause -> {
                    AnimatedIconButton(DownixIcons.Pause, "Pause", c.text, onPause, enabled = !selectionMode, optimisticIcon = DownixIcons.Play)
                }
                else -> {
                    AnimatedIconButton(
                        icon = if (item.state == DownloadState.FAILED) DownixIcons.Retry else DownixIcons.Play,
                        description = if (item.state == DownloadState.FAILED) "Retry" else "Resume",
                        tint = c.text,
                        onClick = onResume,
                        enabled = !selectionMode,
                        optimisticIcon = DownixIcons.Pause
                    )
                }
            }
            // Red dustbin on every card (active, paused, failed, completed). No swipe-to-delete any more.
            PlainIconButton(DownixIcons.Trash, "Delete", c.red, onRemove, enabled = !selectionMode)
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

/** Which line the status text shows. Only a change of kind is animated, never the live numbers inside a kind. */
private enum class StatusKind { CONNECTING, RUNNING, SAVING, QUEUED, WAITING, PAUSED, FAILED, COMPLETED, CANCELED }

private fun statusText(kind: StatusKind, item: DownloadItem): String = when (kind) {
    StatusKind.CONNECTING -> "Connecting"
    StatusKind.SAVING -> "Saving file · ${maxOf(item.savingPercent, 0)}%"
    StatusKind.RUNNING ->
        if (item.hasKnownSize) "${item.percent}% · ${formatSpeed(item.speedBytesPerSec)}"
        else formatSpeed(item.speedBytesPerSec)
    StatusKind.QUEUED -> "Waiting in queue"
    StatusKind.WAITING -> item.error?.takeIf { it.isNotBlank() } ?: "Waiting for network"
    StatusKind.PAUSED -> if (item.hasKnownSize) "Paused · ${item.percent}%" else "Paused"
    StatusKind.FAILED -> "Failed"
    StatusKind.COMPLETED -> "Completed"
    StatusKind.CANCELED -> "Canceled"
}

/**
 * 12 sp status text: green while running / completed, gray when paused or waiting.
 * After a resume the engine really waits in the queue (no download yet), then the text fades into "Connecting" and the speed text. Kinds change with a 380 ms cross-fade (same duration and easing as the progress bar colour);
 * every kind has its own colour, and one font weight is used everywhere so the text never jumps in width.
 */
@Composable
internal fun StatusLine(item: DownloadItem) {
    val c = Downix.colors
    val kind = when (item.state) {
        DownloadState.DOWNLOADING -> when {
            item.savingPercent >= 0 -> StatusKind.SAVING
            item.speedBytesPerSec <= 0 -> StatusKind.CONNECTING
            else -> StatusKind.RUNNING
        }
        DownloadState.QUEUED -> StatusKind.QUEUED
        DownloadState.WAITING -> StatusKind.WAITING
        DownloadState.PAUSED -> StatusKind.PAUSED
        DownloadState.FAILED -> StatusKind.FAILED
        DownloadState.COMPLETED -> StatusKind.COMPLETED
        DownloadState.CANCELED -> StatusKind.CANCELED
    }
    Crossfade(targetState = kind, animationSpec = tween(380, easing = FastOutSlowInEasing), label = "statusKind") { k ->
        val color = when (k) {
            StatusKind.CONNECTING, StatusKind.RUNNING, StatusKind.SAVING, StatusKind.COMPLETED -> c.green
            StatusKind.FAILED -> c.red
            else -> c.textSecondary
        }
        Text(
            statusText(k, item),
            color = color,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 34 dp glass tile with a thin file-type icon. */
@Composable
internal fun FileTile(fileName: String) {
    val c = Downix.colors
    Box(Modifier.size(34.dp).glass(RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
        Icon(fileKind(fileName), null, tint = c.text, modifier = Modifier.size(17.dp))
    }
}

private fun fileKind(name: String): ImageVector =
    when (name.substringAfterLast('.', "").lowercase()) {
        "mp4", "mkv", "avi", "mov", "webm", "3gp", "flv", "m4v" -> DownixIcons.FileVideo
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "svg" -> DownixIcons.FileImage
        "mp3", "wav", "flac", "aac", "m4a", "ogg", "opus" -> DownixIcons.FileAudio
        "zip", "rar", "7z", "tar", "gz", "xz" -> DownixIcons.FileZip
        "apk", "aab", "xapk" -> DownixIcons.FileApp
        "pdf", "doc", "docx", "txt", "xls", "xlsx", "ppt", "pptx", "epub" -> DownixIcons.FileDoc
        else -> DownixIcons.FileOther
    }

/**
 * Flat 6 dp progress bar: a rounded track with a solid, rounded fill in the bar colour (no gloss, no border,
 * no sheen), so it looks clean and costs almost nothing to draw. The fill follows the progress with a smooth
 * ease-out. Unknown size ([indeterminate]) = a solid segment gliding across the track.
 * [active] is kept so old calls still compile.
 */
@Composable
internal fun GlassProgress(
    progress: Float,
    indeterminate: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    active: Boolean = true
) {
    val c = Downix.colors
    val animated by animateFloatAsState(progress, tween(500, easing = LinearOutSlowInEasing), label = "progress")
    val shownColor by animateColorAsState(color, tween(380, easing = FastOutSlowInEasing), label = "barColor")
    val x = if (indeterminate) {
        val sweep = rememberInfiniteTransition(label = "sweep")
        sweep.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing)),
            label = "sweepX"
        )
    } else null

    Box(
        modifier
            .fillMaxWidth()
            .height(6.dp)
            .drawBehind {
                val r = CornerRadius(size.height / 2f)
                drawRoundRect(color = c.track, cornerRadius = r)
                if (x != null) {
                    val w = size.width * 0.3f
                    val start = (size.width + w) * x.value - w
                    val left = start.coerceAtLeast(0f)
                    val right = (start + w).coerceAtMost(size.width)
                    if (right > left) {
                        drawRoundRect(
                            color = shownColor,
                            topLeft = Offset(left, 0f),
                            size = Size(right - left, size.height),
                            cornerRadius = r
                        )
                    }
                } else {
                    val fill = size.width * animated.coerceIn(0f, 1f)
                    if (fill > 0.5f) {
                        // never narrower than the bar is high, so the rounded end always looks right
                        drawRoundRect(
                            color = shownColor,
                            size = Size(maxOf(fill, size.height), size.height),
                            cornerRadius = r
                        )
                    }
                }
            }
    )
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
        delay(300) // after the popup has finished opening, so the keyboard does not make it jump
        runCatching { focus.requestFocus() }
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
            val startedAt = SystemClock.elapsedRealtime()
            val result = LinkChecker.check(value)
            // The loading state always stays visible for 2.5 s, then the result (ok or error) appears.
            val remaining = 2500L - (SystemClock.elapsedRealtime() - startedAt)
            if (remaining > 0) delay(remaining)
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

    GlassCenterDialog(onDismiss = onDismiss, title = "Add link") {
        val c = Downix.colors
        GlassField(
            value = text,
            onValueChange = {
                text = it
                showError = false
                duplicate = null
                probe = null
            },
            placeholder = "File link",
            isError = showError,
            focusRequester = focus,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            trailing = {
                // Empty box: paste button. As soon as there is text: one tap clears the box.
                AnimatedContent(
                    targetState = text.isNotEmpty(),
                    transitionSpec = {
                        (fadeIn(tween(140)) + scaleIn(initialScale = 0.7f, animationSpec = tween(140))) togetherWith
                            (fadeOut(tween(90)) + scaleOut(targetScale = 0.7f, animationSpec = tween(90)))
                    },
                    label = "fieldAction"
                ) { hasText ->
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable {
                                if (hasText) {
                                    text = ""
                                    runCatching { focus.requestFocus() }
                                } else {
                                    clipboard.getText()?.text?.let {
                                        text = extractLink(it) ?: it.trim()
                                    }
                                }
                                showError = false
                                duplicate = null
                                probe = null
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (hasText) DownixIcons.Close else DownixIcons.Clipboard,
                            contentDescription = if (hasText) "Clear text" else "Paste from clipboard",
                            tint = c.text,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        )
        if (showError) {
            Spacer(Modifier.height(6.dp))
            Text("That doesn't look like a valid link. Check it and try again.", color = c.red, fontSize = 11.sp)
        }

        Spacer(Modifier.height(10.dp))
        GlassField(
            value = name,
            onValueChange = { name = it },
            placeholder = "File name (optional)",
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { if (canDownload) submit() else connect() })
        )

        duplicate?.let { existing ->
            Spacer(Modifier.height(10.dp))
            Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(14.dp)).padding(12.dp)) {
                Text("Already in your list", color = c.paused, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(
                    "${existing.fileName.ifBlank { "This link" }}, ${stateLabel(existing).lowercase()}",
                    color = c.textSecondary,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        // Status line: a short hint, the checking message, or the result of the check (fades between them).
        Crossfade(targetState = checking to probe, animationSpec = tween(180), label = "linkStatus") { (isChecking, result) ->
            Row(Modifier.fillMaxWidth().heightIn(min = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                when {
                    isChecking -> Text("Checking the link...", color = c.textSecondary, fontSize = 11.sp)
                    result != null -> {
                        val color = when (result.status) {
                            ProbeStatus.OK -> c.green
                            ProbeStatus.WARN -> c.paused
                            ProbeStatus.FAIL -> c.red
                        }
                        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                        Spacer(Modifier.width(8.dp))
                        Text(result.message, color = color, fontSize = 11.sp, modifier = Modifier.weight(1f))
                    }
                    else -> Text("Tap Connect to check the link first.", color = c.textSecondary, fontSize = 11.sp)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        val actionMode = when {
            checking -> ActionMode.CHECKING
            canDownload -> ActionMode.READY
            else -> ActionMode.CONNECT
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton("Cancel", onClick = onDismiss, outlineRed = true, modifier = Modifier.weight(1f))
            MorphActionButton(
                mode = actionMode,
                label = when (actionMode) {
                    ActionMode.CHECKING -> "Connecting"
                    ActionMode.READY -> if (duplicate != null) "Download again" else "Download"
                    ActionMode.CONNECT -> if (probe != null) "Connect again" else "Connect"
                },
                enabled = when (actionMode) {
                    ActionMode.CHECKING -> false
                    ActionMode.READY -> true
                    ActionMode.CONNECT -> text.isNotBlank()
                },
                onClick = {
                    when (actionMode) {
                        ActionMode.READY -> if (duplicate != null) submit(skipDuplicateCheck = true) else submit()
                        else -> connect()
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private enum class ActionMode { CONNECT, CHECKING, READY }

/**
 * One button that does two jobs in the Add link popup: it starts as a white "Connect", shows a small
 * spinner while the link is checked, then turns green and becomes "Download". The colour fades and the
 * label slides out / in, so the two never overlap or clash.
 */
@Composable
private fun MorphActionButton(
    mode: ActionMode,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = Downix.colors
    val ready = mode == ActionMode.READY
    val fill by animateColorAsState(if (ready) c.green else Color.White, tween(280), label = "actionFill")
    val border by animateColorAsState(if (ready) c.green else c.whiteBorder, tween(280), label = "actionBorder")
    val alpha by animateFloatAsState(if (enabled) 1f else 0.4f, tween(180), label = "actionAlpha")
    Box(
        modifier
            .pressable(enabled = enabled, onClick = onClick)
            .alpha(alpha)
            .heightIn(min = 42.dp)
            .clip(CircleShape)
            .background(fill, CircleShape)
            .border(1.dp, border, CircleShape)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = mode to label,
            transitionSpec = {
                (fadeIn(tween(180, delayMillis = 90)) + slideInVertically(tween(220, delayMillis = 60)) { it / 2 }) togetherWith
                    (fadeOut(tween(90)) + slideOutVertically(tween(160)) { -it / 2 })
            },
            contentAlignment = Alignment.Center,
            label = "actionLabel"
        ) { (shownMode, shownLabel) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (shownMode == ActionMode.CHECKING) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.Black)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    shownLabel,
                    color = if (shownMode == ActionMode.READY) c.onGreen else Color.Black,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun ConfirmCancelDialog(fileName: String, onKeep: () -> Unit, onCancelDownload: () -> Unit) {
    GlassBottomSheet(onDismiss = onKeep, title = "Cancel this download?", instant = true) {
        Text(
            "${fileName.ifBlank { "This file" }} will be removed and the part already downloaded will be deleted.",
            color = Downix.colors.textSecondary,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton("Keep", onClick = onKeep, modifier = Modifier.weight(1f))
            PillButton("Cancel download", onClick = onCancelDownload, danger = true, modifier = Modifier.weight(1f))
        }
    }
}

/**
 * Full-screen Settings (design: settings-day / settings-dark): glass top bar with a back arrow,
 * white section pills, one glass group per section, white "Done" button at the bottom.
 */
@Composable
private fun SettingsScreen(
    settings: AppSettings,
    updateState: UpdateState,
    updatesConfigured: Boolean,
    onApply: (AppSettings) -> Unit,
    onCheckUpdate: () -> Unit,
    onOpenUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
    onMessage: (String) -> Unit,
    onClose: () -> Unit
) {
    val c = Downix.colors
    val context = LocalContext.current
    // Every change is saved at once: no Apply button.
    val showMessage = onMessage
    BackHandler(onBack = onClose)

    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            if (SaveLocation.keepAccess(context, uri)) onApply(settings.copy(downloadFolder = uri.toString()))
            else showMessage("Can't use this folder, choose another one")
        }
    }

    // The full screen sits on top of the list: swallow touches so nothing behind it reacts.
    Box(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
    ) {
        DownixBackground {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                // Top bar
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 12.dp, top = 4.dp, bottom = 0.dp)
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PlainIconButton(
                        icon = DownixIcons.Back,
                        description = "Back",
                        tint = c.text,
                        onClick = onClose
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Settings", color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                }

                // The credit sits in the middle of the free space under the settings (or just after them
                // on a small screen, where the page scrolls).
                BoxWithConstraints(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    val density = LocalDensity.current
                    val viewportPx = with(density) { maxHeight.roundToPx() }
                    var contentPx by remember { mutableStateOf(0) }
                    var creditPx by remember { mutableStateOf(0) }
                    val gap = if (contentPx > 0 && creditPx > 0) {
                        with(density) { ((viewportPx - contentPx - creditPx) / 2).toDp() }.coerceAtLeast(20.dp)
                    } else 20.dp
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        Column(Modifier.onSizeChanged { contentPx = it.height }.padding(bottom = 8.dp)) {
                        SettingSection("General", first = true)
                        SettingGroup {
                            SettingRow(title = SettingNames.THEME, subtitle = "") {
                                ThemeSegmented(settings.themeMode) { onApply(settings.copy(themeMode = it)) }
                            }
                            SettingDivider()
                            SettingRow(
                                title = SettingNames.DOWNLOAD_FOLDER,
                                subtitle = SaveLocation.folderPath(settings.downloadFolder)
                            ) {
                                PillButton(
                                    "Change",
                                    onClick = {
                                        runCatching { folderLauncher.launch(null) }
                                            .onFailure { showMessage("No folder picker found on this phone") }
                                    },
                                    small = true
                                )
                            }
                            SettingDivider()
                            SettingRow(title = SettingNames.MAX_PARALLEL, subtitle = "Files at the same time") {
                                Stepper(
                                    value = settings.maxParallel,
                                    min = AppSettings.MIN_PARALLEL,
                                    max = AppSettings.MAX_PARALLEL,
                                    onChange = { onApply(settings.copy(maxParallel = it)) }
                                )
                            }
                        }

                        SettingSection("Network and alerts")
                        SettingGroup {
                            SettingRow(title = SettingNames.WIFI_ONLY, subtitle = "Never use mobile data") {
                                GlassSwitch(settings.wifiOnly) { onApply(settings.copy(wifiOnly = it)) }
                            }
                            SettingDivider()
                            SettingRow(title = SettingNames.NOTIFICATIONS, subtitle = "When finished or failed") {
                                GlassSwitch(settings.notifications) { onApply(settings.copy(notifications = it)) }
                            }
                        }

                        SettingSection("Updates")
                        SettingGroup {
                            UpdateSettingsRow(
                                state = updateState,
                                configured = updatesConfigured,
                                onCheck = onCheckUpdate,
                                onOpen = onOpenUpdate,
                                onInstall = onInstallUpdate
                            )
                        }
                        }
                        Spacer(Modifier.height(gap))
                        DeveloperCredit(Modifier.onSizeChanged { creditPx = it.height })
                        Spacer(Modifier.height(gap))
                    }
                }

            }
        }
    }
}

/** Bold headline above a settings group (no background). */
@Composable
private fun SettingSection(title: String, first: Boolean = false) {
    Text(
        title,
        color = Downix.colors.text,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 20.dp, top = if (first) 6.dp else 18.dp, bottom = 8.dp)
    )
}

/** One glass group per section (radius 16, side margin 12). */
@Composable
private fun SettingGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .glass(RoundedCornerShape(16.dp)),
        content = content
    )
}

@Composable
internal fun SettingRow(title: String, subtitle: String, control: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = Downix.colors.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            if (subtitle.isNotEmpty()) Text(
                subtitle,
                color = Downix.colors.textSecondary,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        control()
    }
}

@Composable
internal fun SettingDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Downix.colors.divider))
}

/**
 * Compact "-" / "+" with the number between them. Small 14 dp icons, but a clear tap animation:
 * a quick squeeze on press, a soft glow ring that pops out, and the number rolls up / down.
 */
@Composable
private fun Stepper(value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    val c = Downix.colors
    // The 36 dp tap boxes leave empty space around the icons: shift the row so the "+" ends at the
    // same right edge as the buttons and switches in the other rows.
    Row(Modifier.offset(x = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        StepButton(DownixIcons.Minus, "Fewer downloads", enabled = value > min) { onChange(value - 1) }
        AnimatedContent(
            targetState = value,
            transitionSpec = {
                val up = targetState > initialState
                (slideInVertically(tween(180)) { if (up) it else -it } + fadeIn(tween(180))) togetherWith
                    (slideOutVertically(tween(180)) { if (up) -it else it } + fadeOut(tween(120)))
            },
            contentAlignment = Alignment.Center,
            modifier = Modifier.widthIn(min = 22.dp),
            label = "stepperValue"
        ) { shown ->
            Text(
                "$shown",
                color = c.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
        StepButton(DownixIcons.Plus, "More downloads", enabled = value < max) { onChange(value + 1) }
    }
}

@Composable
private fun StepButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    val c = Downix.colors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.6f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 700f),
        label = "stepScale"
    )
    val ring by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = tween(if (pressed) 90 else 260),
        label = "stepRing"
    )
    val alpha by animateFloatAsState(if (enabled) 1f else 0.3f, tween(200), label = "stepAlpha")
    Box(
        Modifier
            .size(36.dp)
            .graphicsLayer { this.alpha = alpha }
            .clickable(
                interactionSource = source,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .drawBehind {
                // soft ring behind the icon while pressed
                drawCircle(c.text.copy(alpha = 0.16f * ring), radius = size.minDimension / 2f * (0.55f + 0.45f * ring))
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = c.text,
            modifier = Modifier.size(15.dp).graphicsLayer { scaleX = scale; scaleY = scale }
        )
    }
}

/** Toggle 38x22: ON = white track + black knob, OFF = gray track + gray knob. */
@Composable
internal fun GlassSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val c = Downix.colors
    val knobX by animateDpAsState(if (checked) 19.dp else 3.dp, tween(160), label = "toggleKnob")
    val track = RoundedCornerShape(11.dp)
    Box(
        Modifier
            .offset(x = 1.dp) // 40 dp tap box, 38 dp switch: its right edge lines up with the other rows
            .heightIn(min = 40.dp)
            .widthIn(min = 40.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(width = 38.dp, height = 22.dp)
                .clip(track)
                .background(if (checked) Color.White else c.toggleOffTrack, track)
                .border(1.dp, if (checked) c.whiteBorder else c.toggleOffBorder, track),
            contentAlignment = Alignment.CenterStart // knob is centred vertically; only x moves
        ) {
            Box(
                Modifier
                    .offset(x = knobX)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(if (checked) Color.Black else c.paused, CircleShape)
            )
        }
    }
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
        Icon(Icons.Rounded.WifiOff, null, tint = Downix.colors.paused, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            "No internet connection. Downloads continue when you are back online.",
            color = Downix.colors.text,
            fontSize = 12.sp,
            lineHeight = 17.sp
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
private fun installUpdate(context: Context, vm: MainViewModel, onMessage: (String) -> Unit) {
    when (vm.installUpdate(context)) {
        InstallStart.STARTED -> Unit
        InstallStart.NEEDS_PERMISSION -> onMessage("Allow Downix to install apps, then come back and tap Install")
        InstallStart.FAILED -> onMessage("Could not start the installer")
    }
}

private fun Context.isGranted(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

private fun openDownloaded(context: Context, savedUri: String?, onMessage: (String) -> Unit = {}) {
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
        onMessage("Can't open this file")
    }
}
