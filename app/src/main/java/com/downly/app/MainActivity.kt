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
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        if (savedInstanceState == null) pendingLink = linkFromIntent(intent)

        setContent {
            GlassTheme {
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
/*  Theme and glass building blocks                                    */
/* ------------------------------------------------------------------ */

internal object Palette {
    val NavyDeep = Color(0xFF040A1E)
    val Navy = Color(0xFF0A1B4D)
    val Royal = Color(0xFF1746C9)
    val Azure = Color(0xFF2F8BFF)
    val Sky = Color(0xFF63C7FF)
    val Ice = Color(0xFFEAF3FF)
    val Mist = Color(0xFFA7BCE6)
    val Mint = Color(0xFF5CE1B5)
    val Amber = Color(0xFFFFC46B)
    val Coral = Color(0xFFFF7D8A)
    val CoralDeep = Color(0xFFE5546A)
    val Violet = Color(0xFFB69CFF)
    val Lime = Color(0xFF9BE564)
}

@Composable
private fun GlassTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.Azure,
            onPrimary = Color.White,
            background = Palette.NavyDeep,
            surface = Palette.Navy,
            onSurface = Palette.Ice
        ),
        content = content
    )
}

/** Frosted glass surface: translucent white gradient fill + light-catching border. */
internal fun Modifier.glass(
    shape: Shape = RoundedCornerShape(24.dp),
    strong: Boolean = false
): Modifier = this
    .clip(shape)
    .background(
        Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = if (strong) 0.20f else 0.13f),
                Color.White.copy(alpha = if (strong) 0.08f else 0.04f)
            )
        ),
        shape
    )
    .border(
        1.dp,
        Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.45f),
                Color.White.copy(alpha = 0.06f),
                Color.White.copy(alpha = 0.18f)
            )
        ),
        shape
    )

/** Soft press feedback (spring scale) for every tappable element. */
internal fun Modifier.pressable(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 600f),
        label = "press"
    )
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(
            interactionSource = source,
            indication = null,
            enabled = enabled,
            role = Role.Button,
            onClick = onClick
        )
}

private fun DrawScope.orb(color: Color, center: Offset, radius: Float) {
    drawCircle(
        brush = Brush.radialGradient(listOf(color, Color.Transparent), center = center, radius = radius),
        radius = radius,
        center = center
    )
}

/** Deep-blue gradient with a few slowly drifting light orbs behind the glass. */
@Composable
private fun GlassBackground(content: @Composable BoxScope.() -> Unit) {
    val transition = rememberInfiniteTransition(label = "orbs")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(16_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift"
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Palette.NavyDeep,
                    0.55f to Palette.Navy,
                    1f to Palette.Royal
                )
            )
            .drawBehind {
                val w = size.width
                val h = size.height
                orb(
                    Palette.Azure.copy(alpha = 0.55f),
                    Offset(w * (0.10f + 0.30f * drift), h * (0.10f + 0.06f * drift)),
                    w * 0.95f
                )
                orb(
                    Palette.Sky.copy(alpha = 0.30f),
                    Offset(w * (0.95f - 0.35f * drift), h * (0.55f + 0.08f * drift)),
                    w * 0.80f
                )
                orb(
                    Palette.Royal.copy(alpha = 0.60f),
                    Offset(w * (0.30f + 0.25f * drift), h * 0.95f),
                    w * 1.00f
                )
            },
        content = content
    )
}

@Composable
internal fun GlassIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Palette.Ice,
    size: Dp = 40.dp,
    enabled: Boolean = true
) {
    Box(
        modifier
            .size(size)
            .alpha(if (enabled) 1f else 0.35f)
            .glass(CircleShape)
            .pressable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
internal fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    danger: Boolean = false,
    icon: ImageVector? = null
) {
    val shape = CircleShape
    val base = if (primary) {
        val colors = if (danger) listOf(Palette.Coral, Palette.CoralDeep) else listOf(Palette.Azure, Palette.Royal)
        modifier.clip(shape).background(Brush.horizontalGradient(colors), shape)
    } else {
        modifier.glass(shape)
    }
    Row(
        base
            .border(1.dp, Color.White.copy(alpha = if (primary) 0.35f else 0f), shape)
            .pressable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Pop-up container: glass panel that fades and springs in. */
@Composable
internal fun GlassDialog(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }
        AnimatedVisibility(
            visible = shown,
            enter = fadeIn(tween(160)) + scaleIn(
                initialScale = 0.92f,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium)
            )
        ) {
            val shape = RoundedCornerShape(28.dp)
            Column(
                Modifier
                    .padding(horizontal = 24.dp)
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .clip(shape)
                    .background(Palette.Navy.copy(alpha = 0.92f))
                    .glass(shape, strong = true)
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState()),
                content = content
            )
        }
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

    GlassBackground {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Header(
                downloads = downloads,
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
                    onFilter = { filter = it },
                    searchOpen = searchOpen,
                    onToggleSearch = {
                        if (searchOpen) query = ""
                        searchOpen = !searchOpen
                    }
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
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (downloads.isEmpty()) {
                    EmptyState(Modifier.align(Alignment.Center))
                } else if (visible.isEmpty()) {
                    EmptyListMessage(filter, query, Modifier.align(Alignment.Center))
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 130.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(visible, key = { it.id }) { item ->
                            DownloadCard(
                                item = item,
                                onPause = { vm.pause(item.id) },
                                onResume = { vm.resume(item.id) },
                                onRemove = {
                                    if (item.isCompleted || item.downloadedBytes == 0L) vm.remove(item.id)
                                    else confirmCancel = item
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
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 22.dp),
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
            onCheckUpdate = vm::checkForUpdates,
            onOpenUpdate = { showUpdate = true },
            onInstallUpdate = { installUpdate(context, vm) },
            onExport = vm::exportSettings,
            onImport = vm::importSettings,
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
    onPauseAll: () -> Unit,
    onClearFinished: () -> Unit,
    onSettings: () -> Unit
) {
    val active = downloads.count { it.isActive }
    val finished = downloads.count { !it.isActive && it.state != DownloadState.PAUSED }
    val speed = downloads.sumOf { it.speedBytesPerSec }
    val subtitle = when {
        active > 0 -> "$active active, ${formatSpeed(speed)}"
        downloads.isEmpty() -> "No downloads yet"
        else -> "${downloads.size} in your list"
    }
    Row(
        Modifier.fillMaxWidth().padding(start = 22.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "Downloads",
                color = Palette.Ice,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.8).sp
            )
            Text(subtitle, color = Palette.Mist, fontSize = 14.sp)
        }
        GlassIconButton(
            icon = Icons.Rounded.Pause,
            description = "Pause all downloads",
            onClick = onPauseAll,
            enabled = active > 0
        )
        Spacer(Modifier.width(10.dp))
        GlassIconButton(
            icon = Icons.Rounded.ClearAll,
            description = "Clear finished downloads",
            onClick = onClearFinished,
            enabled = finished > 0
        )
        Spacer(Modifier.width(10.dp))
        GlassIconButton(
            icon = Icons.Rounded.Settings,
            description = "Settings",
            onClick = onSettings
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(100.dp).glass(CircleShape, strong = true), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.CloudDownload, null, tint = Palette.Sky, modifier = Modifier.size(46.dp))
        }
        Spacer(Modifier.height(22.dp))
        Text("No downloads yet", color = Palette.Ice, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            "Tap Add link and paste a file link. Finished files are saved in your Downloads folder.",
            color = Palette.Mist,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AddButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = CircleShape
    Row(
        modifier
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(Palette.Azure, Palette.Royal)), shape)
            .border(
                1.dp,
                Brush.linearGradient(listOf(Color.White.copy(0.55f), Color.White.copy(0.10f))),
                shape
            )
            .pressable(onClick = onClick)
            .padding(horizontal = 28.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Add, null, tint = Color.White, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text("Add link", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
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
    Column(modifier.fillMaxWidth().glass().pressable(onClick = onDetails).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FileTile(item.fileName)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.fileName.ifBlank { "Getting file info" },
                    color = Palette.Ice,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 21.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                StatusLine(item)
            }
        }

        if (!item.isCompleted) {
            Spacer(Modifier.height(14.dp))
            val fill = when (item.state) {
                DownloadState.PAUSED, DownloadState.WAITING -> listOf(Palette.Amber.copy(alpha = 0.7f), Palette.Amber)
                DownloadState.FAILED -> listOf(Palette.Coral.copy(alpha = 0.7f), Palette.Coral)
                else -> listOf(Palette.Azure, Palette.Sky)
            }
            GlassProgress(
                progress = item.progress,
                indeterminate = item.state == DownloadState.DOWNLOADING && !item.hasKnownSize,
                colors = fill
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                sizeLabel(item),
                color = Palette.Mist,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    item.isCompleted -> {
                        GlassIconButton(Icons.Rounded.OpenInNew, "Open file", onOpen, tint = Palette.Sky, size = 38.dp)
                        GlassIconButton(Icons.Rounded.Delete, "Remove from list", onRemove, size = 38.dp)
                    }
                    item.canPause -> {
                        GlassIconButton(Icons.Rounded.Pause, "Pause", onPause, size = 38.dp)
                        GlassIconButton(Icons.Rounded.Close, "Cancel download", onRemove, size = 38.dp)
                    }
                    else -> {
                        GlassIconButton(
                            icon = if (item.state == DownloadState.FAILED) Icons.Rounded.Refresh else Icons.Rounded.PlayArrow,
                            description = if (item.state == DownloadState.FAILED) "Retry" else "Resume",
                            onClick = onResume,
                            tint = Palette.Sky,
                            size = 38.dp
                        )
                        GlassIconButton(Icons.Rounded.Close, "Cancel download", onRemove, size = 38.dp)
                    }
                }
            }
        }

        if (item.state == DownloadState.FAILED && !item.error.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(item.error, color = Palette.Coral, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

internal fun sizeLabel(item: DownloadItem): String = when {
    item.isCompleted -> "${formatBytes(maxOf(item.totalBytes, item.downloadedBytes))} saved to Downloads"
    item.hasKnownSize -> "${formatBytes(item.downloadedBytes)} of ${formatBytes(item.totalBytes)}, ${item.percent}%"
    else -> formatBytes(item.downloadedBytes)
}

@Composable
internal fun StatusLine(item: DownloadItem) {
    val (label, color) = when (item.state) {
        DownloadState.DOWNLOADING -> {
            val text = if (item.speedBytesPerSec > 0) {
                buildString {
                    append(formatSpeed(item.speedBytesPerSec))
                    if (item.etaSeconds >= 0) append(", ${formatEta(item.etaSeconds)} left")
                }
            } else "Connecting"
            text to Palette.Sky
        }
        DownloadState.QUEUED -> "Waiting in queue" to Palette.Mist
        DownloadState.WAITING -> (item.error?.takeIf { it.isNotBlank() } ?: "Waiting for network") to Palette.Amber
        DownloadState.PAUSED -> "Paused" to Palette.Amber
        DownloadState.FAILED -> "Failed" to Palette.Coral
        DownloadState.COMPLETED -> "Completed" to Palette.Mint
        DownloadState.CANCELED -> "Canceled" to Palette.Mist
    }
    val animated by animateColorAsState(color, tween(300), label = "status")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(animated))
        Spacer(Modifier.width(8.dp))
        Text(label, color = animated, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun FileTile(fileName: String) {
    val (icon, tint) = fileKind(fileName)
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .size(48.dp)
            .clip(shape)
            .background(tint.copy(alpha = 0.16f), shape)
            .border(1.dp, tint.copy(alpha = 0.35f), shape),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
    }
}

private fun fileKind(name: String): Pair<ImageVector, Color> =
    when (name.substringAfterLast('.', "").lowercase()) {
        "mp4", "mkv", "avi", "mov", "webm", "3gp", "flv", "m4v" -> Icons.Rounded.Movie to Palette.Coral
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "svg" -> Icons.Rounded.Image to Palette.Mint
        "mp3", "wav", "flac", "aac", "m4a", "ogg", "opus" -> Icons.Rounded.MusicNote to Palette.Violet
        "zip", "rar", "7z", "tar", "gz", "xz" -> Icons.Rounded.FolderZip to Palette.Amber
        "apk", "aab", "xapk" -> Icons.Rounded.Android to Palette.Lime
        "pdf", "doc", "docx", "txt", "xls", "xlsx", "ppt", "pptx", "epub" -> Icons.Rounded.Description to Palette.Sky
        else -> Icons.Rounded.InsertDriveFile to Palette.Mist
    }

@Composable
internal fun GlassProgress(progress: Float, indeterminate: Boolean, colors: List<Color>) {
    val animated by animateFloatAsState(progress, tween(450, easing = LinearOutSlowInEasing), label = "progress")
    val sweep = rememberInfiniteTransition(label = "sweep")
    val x by sweep.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing)),
        label = "sweepX"
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.12f))
    ) {
        if (indeterminate) {
            Box(
                Modifier.fillMaxSize().drawBehind {
                    val w = size.width * 0.35f
                    val start = (size.width + w) * x - w
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            listOf(Color.Transparent, Palette.Sky, Color.Transparent),
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
                    .clip(CircleShape)
                    .background(Brush.horizontalGradient(colors))
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

    LaunchedEffect(Unit) {
        delay(250)
        runCatching { focus.requestFocus() }
        // Nothing typed yet and a web link is on the clipboard: suggest it.
        if (text.isBlank()) {
            val link = runCatching { clipboard.getText()?.text }.getOrNull()?.let { extractLink(it) }
            if (link != null) text = link
        }
    }

    fun submit(skipDuplicateCheck: Boolean = false) {
        val value = text.trim()
        if (!DownloadEngine.isValidUrl(value)) {
            showError = true
            return
        }
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
        Text("Add download", color = Palette.Ice, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("Paste a direct link to the file you want.", color = Palette.Mist, fontSize = 14.sp)
        Spacer(Modifier.height(18.dp))

        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                showError = false
                duplicate = null
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
                    tint = Palette.Sky,
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .clip(CircleShape)
                        .clickable {
                            clipboard.getText()?.text?.let {
                                text = extractLink(it) ?: it.trim()
                                showError = false
                                duplicate = null
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
            Text("That doesn't look like a valid link. Check it and try again.", color = Palette.Coral, fontSize = 13.sp)
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
            keyboardActions = KeyboardActions(onGo = { submit() }),
            colors = glassFieldColors()
        )

        duplicate?.let { existing ->
            Spacer(Modifier.height(12.dp))
            Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)).padding(12.dp)) {
                Text("Already in your list", color = Palette.Amber, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(
                    "${existing.fileName.ifBlank { "This link" }}, ${stateLabel(existing).lowercase()}",
                    color = Palette.Mist,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            PillButton("Cancel", onClick = onDismiss, primary = false)
            Spacer(Modifier.width(10.dp))
            if (duplicate != null) {
                PillButton("Download again", onClick = { submit(skipDuplicateCheck = true) }, icon = Icons.Rounded.Download)
            } else {
                PillButton("Download", onClick = { submit() }, icon = Icons.Rounded.Download)
            }
        }
    }
}

@Composable
private fun ConfirmCancelDialog(fileName: String, onKeep: () -> Unit, onCancelDownload: () -> Unit) {
    GlassDialog(onKeep) {
        Text("Cancel this download?", color = Palette.Ice, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "${fileName.ifBlank { "This file" }} will be removed and the part already downloaded will be deleted.",
            color = Palette.Mist,
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
    onCheckUpdate: () -> Unit,
    onOpenUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
    onExport: (Uri, (String) -> Unit) -> Unit,
    onImport: (Uri, (String) -> Unit) -> Unit,
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

    GlassDialog(onClose) {
        Text("Settings", color = Palette.Ice, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))

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
            color = Palette.Mist,
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
        color = Palette.Sky,
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
            Text(title, color = Palette.Ice, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = Palette.Mist, fontSize = 13.sp, lineHeight = 18.sp)
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
            color = Palette.Ice,
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
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = Palette.Azure,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = Palette.Mist,
            uncheckedTrackColor = Color.White.copy(alpha = 0.12f),
            uncheckedBorderColor = Color.White.copy(alpha = 0.25f)
        )
    )
}

/** Shown under the header while the phone has no internet. */
@Composable
private fun OfflineBanner() {
    Row(
        Modifier
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
            .fillMaxWidth()
            .glass(RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.WifiOff, null, tint = Palette.Amber, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            "No internet connection. Downloads continue when you are back online.",
            color = Palette.Ice,
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
