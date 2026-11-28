package com.downix.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import android.content.ClipData
import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.app.Activity
import android.provider.OpenableColumns
import android.widget.Toast
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.webkit.MimeTypeMap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.appendInlineContent
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* ------------------------------------------------------------------ */
/*  List tools: filter + search                                        */
/* ------------------------------------------------------------------ */

enum class DownloadFilter(val label: String, val emptyTitle: String, val emptyHint: String) {
    ALL("All", "No downloads yet", ""),
    ACTIVE("Active", "No active downloads", "Downloads that are running, waiting or paused show up here."),
    COMPLETED("Completed", "No completed downloads", "Finished files show up here."),
    FAILED("Failed", "No failed downloads", "Downloads that could not finish show up here.");

    fun matches(item: DownloadItem): Boolean = when (this) {
        ALL -> true
        ACTIVE -> item.isActive || item.state == DownloadState.PAUSED
        COMPLETED -> item.isCompleted
        FAILED -> item.state == DownloadState.FAILED
    }
}

/** Newest-first order is kept: this only removes items. Search is case insensitive on file name and link. */
fun applyListTools(items: List<DownloadItem>, filter: DownloadFilter, query: String): List<DownloadItem> {
    val q = query.trim()
    return items.filter { item ->
        filter.matches(item) &&
            (q.isEmpty() || item.fileName.contains(q, ignoreCase = true) || item.url.contains(q, ignoreCase = true))
    }
}

/** Filter chips with counts ("All 3"). Search lives in the header now. */
@Composable
internal fun FilterBar(
    items: List<DownloadItem>,
    filter: DownloadFilter,
    onFilter: (DownloadFilter) -> Unit
) {
    // The four pills share the whole row equally: no empty space on the right.
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        DownloadFilter.values().forEach { option ->
            FilterPill(
                label = option.label,
                count = items.count { option.matches(it) },
                selected = option == filter,
                onClick = { onFilter(option) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** 32 dp chip, radius 16. Selected = white with bold text, others = glass. */
@Composable
private fun FilterPill(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = Downix.colors
    val shape = RoundedCornerShape(16.dp)
    val text = "$label $count"
    Box(
        modifier
            .height(32.dp)
            .pressable(onClick = onClick)
            .then(if (selected) Modifier.selectedSurface(shape) else Modifier.glass(shape))
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (selected) c.onSelected else c.textSecondary,
            fontSize = if (text.length > 12) 11.sp else 12.sp, // e.g. "Completed 100" still fits
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip
        )
    }
}

@Composable
internal fun SearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = Downix.colors
    // The keyboard does NOT open by itself any more: it opens only when the search box is tapped.
    GlassField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = "Search name or link",
        modifier = modifier,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        trailing = {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(enabled = query.isNotEmpty()) { onQueryChange("") },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (query.isNotEmpty()) DownixIcons.Close else DownixIcons.Search,
                    contentDescription = if (query.isNotEmpty()) "Clear search" else "Search",
                    tint = if (query.isNotEmpty()) c.text else c.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    )
}

/** Same text-field look as the Add dialog, shared by search, link and file name fields. */
@Composable
internal fun glassFieldColors(): TextFieldColors {
    val c = Downix.colors
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = c.text,
        unfocusedTextColor = c.text,
        errorTextColor = c.text,
        cursorColor = c.text,
        errorCursorColor = c.red,
        focusedBorderColor = c.text,
        unfocusedBorderColor = c.text.copy(alpha = 0.25f),
        errorBorderColor = c.red,
        focusedPlaceholderColor = c.textSecondary.copy(alpha = 0.7f),
        unfocusedPlaceholderColor = c.textSecondary.copy(alpha = 0.7f),
        errorPlaceholderColor = c.textSecondary.copy(alpha = 0.7f),
        focusedLabelColor = c.text,
        unfocusedLabelColor = c.textSecondary,
        errorLabelColor = c.red,
        focusedContainerColor = c.glassFill,
        unfocusedContainerColor = c.glassFill,
        errorContainerColor = c.glassFill
    )
}

/** Shown instead of the list when a filter or a search has no result (not when the whole list is empty). */
@Composable
internal fun EmptyListMessage(filter: DownloadFilter, query: String, modifier: Modifier = Modifier) {
    val searching = query.isNotBlank()
    Column(modifier.padding(horizontal = 44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.Search, null, tint = Downix.colors.textSecondary, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(14.dp))
        Text(
            if (searching) "No matches" else filter.emptyTitle,
            color = Downix.colors.text,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (searching) "Nothing in this list matches \"${query.trim()}\"." else filter.emptyHint,
            color = Downix.colors.textSecondary,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            textAlign = TextAlign.Center
        )
    }
}

/* ------------------------------------------------------------------ */
/*  Details popup + delete confirmation                                */
/* ------------------------------------------------------------------ */

internal fun stateLabel(item: DownloadItem): String = when (item.state) {
    DownloadState.DOWNLOADING -> if (item.savingPercent >= 0) "Saving file" else "Downloading"
    DownloadState.QUEUED -> "Waiting in queue"
    DownloadState.WAITING -> item.error?.takeIf { it.isNotBlank() } ?: "Waiting for network"
    DownloadState.PAUSED -> "Paused"
    DownloadState.FAILED -> "Failed"
    DownloadState.COMPLETED -> "Completed"
    DownloadState.CANCELED -> "Canceled"
}

private fun detailSize(item: DownloadItem): String = when {
    item.isCompleted -> formatBytes(maxOf(item.totalBytes, item.downloadedBytes))
    item.hasKnownSize -> "${formatBytes(item.downloadedBytes)} of ${formatBytes(item.totalBytes)} (${item.percent}%)"
    item.downloadedBytes > 0 -> formatBytes(item.downloadedBytes)
    else -> "Unknown"
}

private fun dateText(millis: Long): String =
    SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()).format(Date(millis))

@Composable
private fun DetailRow(label: String, value: String, maxLines: Int = 3) {
    Column(Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
        Text(label, color = Downix.colors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(2.dp))
        Text(value, color = Downix.colors.text, fontSize = 13.sp, lineHeight = 18.sp, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    }
}

/** Opened by tapping a card. File actions only appear for finished downloads. */
@Composable
internal fun DetailsDialog(
    item: DownloadItem,
    onClose: () -> Unit,
    onCopyLink: () -> Unit,
    onDeleteFile: () -> Unit,
    onOpenFolder: () -> Unit = {},
    onStopSeeding: () -> Unit = {},
    loadFiles: suspend () -> List<TorrentFileInfo>? = { null }
) {
    GlassBottomSheet(onDismiss = onClose, title = "Details", instant = true) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val nameColor = Downix.colors.text
        val tickColor = Downix.colors.green
        // After a tap on the copy icon a green tick shows for 1 second, then the copy icon comes back.
        var copiedAt by remember { mutableStateOf(0L) }
        var showTick by remember { mutableStateOf(false) }
        LaunchedEffect(copiedAt) {
            if (copiedAt != 0L) {
                showTick = true
                kotlinx.coroutines.delay(1000)
                showTick = false
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            FileTile(item.fileName)
            Spacer(Modifier.width(12.dp))
            // The copy icon sits right after the last letter of the file name (inline), so it follows the text
            // on whichever line the name ends. The full name is shown (no "..."), so the icon is never cut off.
            val hasName = item.fileName.isNotBlank()
            val nameWithCopy = androidx.compose.ui.text.buildAnnotatedString {
                append(item.fileName.ifBlank { "Getting file info" })
                if (hasName) {
                    append("\u00A0")
                    appendInlineContent("copyName", "[copy]")
                }
            }
            val inline = mapOf(
                "copyName" to androidx.compose.foundation.text.InlineTextContent(
                    androidx.compose.ui.text.Placeholder(
                        width = 30.sp,
                        height = 24.sp,
                        placeholderVerticalAlign = androidx.compose.ui.text.PlaceholderVerticalAlign.Center
                    )
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .clickable {
                                copyFileName(context, item.fileName)
                                copiedAt = System.nanoTime()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (showTick) DownixIcons.Check else DownixIcons.Copy,
                            contentDescription = if (showTick) "File name copied" else "Copy file name",
                            tint = if (showTick) tickColor else nameColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            )
            Text(
                nameWithCopy,
                inlineContent = inline,
                color = nameColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 18.sp,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(10.dp))
        SettingDivider()

        // The popup keeps one fixed size: the tabs and the buttons stay in place, only the middle part scrolls.
        val files by produceState<List<TorrentFileInfo>?>(null, item.id, item.state, item.seeding != null) {
            value = if (item.isTorrent) loadFiles() else null
        }
        val fileList = files?.takeIf { it.size > 1 }
        val tabs = buildList {
            add("Overview")
            if (item.isTorrent) {
                if (fileList != null) add("Files")
                add("Info")
            }
        }
        var tab by remember { mutableStateOf(0) }
        val tabIndex = tab.coerceIn(0, tabs.lastIndex)
        if (tabs.size > 1) {
            Spacer(Modifier.height(10.dp))
            DetailTabs(tabs, tabIndex) { tab = it }
            Spacer(Modifier.height(4.dp))
        }
        val screenH = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp
        val bodyHeight = (screenH * 0.40f).dp.coerceIn(220.dp, 380.dp)
        androidx.compose.runtime.key(tabs[tabIndex]) {
            var drag by remember { mutableStateOf(0f) }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(bodyHeight)
                    // swipe left / right to go to the next / previous tab
                    .pointerInput(tabs.size, tabIndex) {
                        detectHorizontalDragGestures(
                            onDragStart = { drag = 0f },
                            onDragEnd = {
                                if (drag < -90f && tabIndex < tabs.lastIndex) tab = tabIndex + 1
                                else if (drag > 90f && tabIndex > 0) tab = tabIndex - 1
                            },
                            onHorizontalDrag = { _, delta -> drag += delta }
                        )
                    }
            ) {
                when (tabs[tabIndex]) {
                    // Same scrolling feel as the download cards: a lazy list whose rows fade and shrink at the edges.
                    "Files" -> fileList?.let { TorrentFileList(it) }
                    "Info" -> DetailLazyList(torrentInfoRows(item))
                    else -> DetailLazyList(
                        buildList<@Composable () -> Unit> {
                            if (!item.isTorrent || !item.url.startsWith(TorrentEngine.FILE_PREFIX)) {
                                add { DetailRow("Link", item.url, maxLines = 4) }
                            }
                            add { DetailRow("Size", detailSize(item)) }
                            add { DetailRow("Added", dateText(item.createdAt)) }
                            add {
                                Column(Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
                                    Text("Status", color = Downix.colors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                    Spacer(Modifier.height(4.dp))
                                    StatusLine(item)
                                }
                            }
                            add {
                                DetailRow(
                                    "Saved to",
                                    if (item.isCompleted) "${SaveLocation.savedFolderLabel(item.savedUri)} / ${item.fileName}"
                                    else "Your download folder, when the download is finished"
                                )
                            }
                            if (item.state == DownloadState.FAILED && !item.error.isNullOrBlank()) {
                                add { DetailRow("Error", item.error) }
                            }
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        if (item.seeding != null) {
            PillButton("Stop seeding", onClick = onStopSeeding, modifier = Modifier.fillMaxWidth(), primary = false)
            Spacer(Modifier.height(10.dp))
        }
        if (item.isFolderTorrent) {
            PillButton("Open folder", onClick = onOpenFolder, modifier = Modifier.fillMaxWidth(), primary = false)
            Spacer(Modifier.height(10.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("Copy link", onClick = onCopyLink, modifier = Modifier.weight(1f), primary = false)
            if (item.isCompleted) {
                PillButton(
                    if (item.isFolderTorrent) "Delete folder" else "Delete file",
                    onClick = onDeleteFile, modifier = Modifier.weight(1f), danger = true
                )
            }
        }
    }
}

/** Tab buttons of the Details popup. */
@Composable
private fun DetailTabs(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val c = Downix.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        tabs.forEachIndexed { i, label ->
            val on = i == selected
            val shape = RoundedCornerShape(10.dp)
            Box(
                Modifier
                    .weight(1f)
                    .clip(shape)
                    .then(if (on) Modifier.glass(shape) else Modifier)
                    .clickable { onSelect(i) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (on) c.text else c.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium
                )
            }
        }
    }
}

/** Torrent tab "Info": type, hash, peers and seeds, sharing numbers (one entry per row). */
private fun torrentInfoRows(item: DownloadItem): List<@Composable () -> Unit> = buildList {
    add { DetailRow("Type", if (item.url.startsWith(TorrentEngine.FILE_PREFIX)) "Torrent (.torrent file)" else "Torrent (magnet link)") }
    item.infoHash?.takeIf { it.isNotBlank() }?.let { add { DetailRow("Info hash", it, maxLines = 2) } }
    if (item.state == DownloadState.DOWNLOADING && item.savingPercent < 0) {
        add {
            DetailRow(
                "Connections",
                (if (item.peerTotal > item.peers) "${item.peers} of ${item.peerTotal} peers" else "${item.peers} peers") + ", ${item.seeds} seeds"
            )
        }
    }
    item.seeding?.let { seed ->
        add {
            val text = if (seed.uploadSpeed > 0) "Uploading ${formatSpeed(seed.uploadSpeed)}" else "Waiting for peers"
            DetailRow(
                "Seeding",
                "$text\nRatio ${"%.2f".format(seed.ratio)}, ${formatBytes(seed.uploaded)} shared, ${seed.peers} peers"
            )
        }
    }
}

/**
 * The scrolling of the Details tabs: the same lazy list as the download cards, rows fade and shrink a little
 * when they slide under the top or bottom edge (shorter reach than the cards, because the rows are taller).
 */
@Composable
private fun DetailLazyList(rows: List<@Composable () -> Unit>) {
    val state = androidx.compose.foundation.lazy.rememberLazyListState()
    androidx.compose.foundation.lazy.LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        itemsIndexed(rows, key = { i, _ -> i }, contentType = { _, _ -> "row" }) { i, row ->
            Column(Modifier.fillMaxWidth().fadeAtListEdges(state, i, reach = 0.5f)) {
                row()
                if (i < rows.lastIndex) SettingDivider()
            }
        }
    }
}

/** Torrent tab "Files": a lazy list (like the download cards) whose rows fade and shrink at the top and bottom edge. */
@Composable
private fun TorrentFileList(list: List<TorrentFileInfo>) {
    val c = Downix.colors
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    Column(Modifier.fillMaxSize()) {
        Text(
            "Files (${list.count { it.selected }} of ${list.size} selected)",
            color = c.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 9.dp, bottom = 6.dp)
        )
        androidx.compose.foundation.lazy.LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(list, key = { it.index }, contentType = { "file" }) { file ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .fadeAtListEdges(listState, file.index)
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        file.path.substringAfterLast('/'),
                        color = if (file.selected) c.text else c.textSecondary,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (file.selected) formatBytes(file.size) else "Skipped",
                        color = c.textSecondary, fontSize = 11.sp
                    )
                }
            }
        }
    }
}

/** The text to copy for "Copy link": a torrent that came from a .torrent file is copied as a magnet link. */
internal fun copyableLink(item: DownloadItem): String =
    if (item.url.startsWith(TorrentEngine.FILE_PREFIX) && !item.infoHash.isNullOrBlank())
        "magnet:?xt=urn:btih:${item.infoHash}"
    else item.url

/** Delete sheet opened from the details popup (file is already known to be on the phone). */
@Composable
internal fun ConfirmDeleteFileDialog(
    fileName: String,
    onKeep: () -> Unit,
    onDelete: () -> Unit,
    isFolder: Boolean = false
) {
    GlassBottomSheet(onDismiss = onKeep, title = if (isFolder) "Delete folder from phone?" else "Delete file from phone?", instant = true) {
        Text(
            "${fileName.ifBlank { if (isFolder) "This folder" else "This file" }} will be deleted from your phone" +
                (if (isFolder) " with all the files in it" else "") +
                " and removed from the list. This can't be undone.",
            color = Downix.colors.textSecondary,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton("Cancel", onClick = onKeep, modifier = Modifier.weight(1f))
            PillButton("Delete", onClick = onDelete, danger = true, modifier = Modifier.weight(1f))
        }
    }
}

/**
 * "Delete download?" sheet (design: delete-sheet). Asked when a finished download is removed.
 * Checkbox "Also delete the file from phone" is unchecked by default:
 * unchecked = only the list entry is removed, checked = the file on the phone is deleted too.
 */
@Composable
internal fun ConfirmRemoveDialog(
    item: DownloadItem,
    onCancel: () -> Unit,
    onListOnly: () -> Unit,
    onDeleteFile: () -> Unit
) {
    val c = Downix.colors
    var alsoFile by remember { mutableStateOf(false) }
    GlassBottomSheet(onDismiss = onCancel, title = "Delete download?", instant = true) {
        Text(
            "${item.fileName.ifBlank { "This file" }} will be removed from the list.",
            color = c.textSecondary,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .glass(RoundedCornerShape(14.dp))
                .clickable { alsoFile = !alsoFile }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DownixCheckbox(alsoFile)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (item.isFolderTorrent) "Also delete the folder from phone" else "Also delete the file from phone",
                    color = c.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                )
                Text("This can't be undone", color = c.textSecondary, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton("Cancel", onClick = onCancel, modifier = Modifier.weight(1f))
            PillButton(
                "Delete",
                onClick = { if (alsoFile) onDeleteFile() else onListOnly() },
                danger = true,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Delete sheet for several selected cards. The checkbox "Also delete the finished files from phone" is
 * unchecked by default (only shown when at least one selected download is finished).
 */
@Composable
internal fun ConfirmBulkDeleteDialog(
    count: Int,
    hasFinished: Boolean,
    onCancel: () -> Unit,
    onListOnly: () -> Unit,
    onDeleteFiles: () -> Unit
) {
    val c = Downix.colors
    var alsoFile by remember { mutableStateOf(false) }
    GlassBottomSheet(onDismiss = onCancel, title = if (count == 1) "Delete 1 download?" else "Delete $count downloads?", instant = true) {
        Text(
            "They will be removed from the list. Downloads that are not finished are cancelled and their partial files are deleted.",
            color = c.textSecondary,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
        if (hasFinished) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .glass(RoundedCornerShape(14.dp))
                    .clickable { alsoFile = !alsoFile }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DownixCheckbox(alsoFile)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Also delete the finished files from phone", color = c.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("This can't be undone", color = c.textSecondary, fontSize = 11.sp)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton("Cancel", onClick = onCancel, modifier = Modifier.weight(1f))
            PillButton(
                "Delete",
                onClick = { if (alsoFile) onDeleteFiles() else onListOnly() },
                danger = true,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/*  Actions: copy link, share file                                     */
/* ------------------------------------------------------------------ */

internal fun copyFileName(context: Context, fileName: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("Downix file name", fileName))
    // Android 13+ shows its own "copied" confirmation.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, "File name copied", Toast.LENGTH_SHORT).show()
    }
}

internal fun copyLink(context: Context, url: String, onMessage: (String) -> Unit = {}) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("Downix link", url))
    // Android 13+ shows its own "copied" confirmation.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        onMessage("Link copied")
    }
}

/** The Activity behind a Compose / wrapped context (needed to start other apps reliably). */
internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Starts another app. The Details sheet is a separate window that covers the in-app toast, so a failure is
 * shown with a normal system Toast (visible above the sheet).
 */
internal fun launchExternal(context: Context, intent: Intent, failText: String, noAppText: String = failText): Boolean {
    return try {
        val activity = context.findActivity()
        if (activity != null) {
            activity.startActivity(intent)
        } else {
            context.startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        true
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, noAppText, Toast.LENGTH_SHORT).show()
        false
    } catch (e: Exception) {
        Toast.makeText(context, failText, Toast.LENGTH_SHORT).show()
        false
    }
}

/** The real file type of a saved file: from the system, else from the file name. */
internal fun savedMime(context: Context, uri: Uri, fileName: String? = null): String {
    val fromSystem = runCatching { context.contentResolver.getType(uri) }.getOrNull()
    if (!fromSystem.isNullOrBlank() && fromSystem != "application/octet-stream") return fromSystem
    val name = fileName?.takeIf { it.isNotBlank() } ?: runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull().orEmpty()
    val fromName = mimeFromName(name)
    return if (fromName != "*/*") fromName else fromSystem ?: "*/*"
}

/** Shares the finished file itself (not the link) with any app. */
internal fun shareDownloaded(context: Context, item: DownloadItem, onMessage: (String) -> Unit = {}) {
    val saved = item.savedUri
    if (saved.isNullOrBlank()) return
    val uri = try {
        val parsed = Uri.parse(saved)
        if (parsed.scheme == "content") {
            parsed
        } else {
            // Android 9 and below: plain file, shared through our FileProvider.
            val path = parsed.path ?: return
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(path))
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Can't share this file", Toast.LENGTH_SHORT).show()
        return
    }
    val type = savedMime(context, uri, item.fileName)
    val send = Intent(Intent.ACTION_SEND)
        .setType(type)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    send.clipData = ClipData.newRawUri(item.fileName, uri)
    val chooser = Intent.createChooser(send, "Share file")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    chooser.clipData = send.clipData
    launchExternal(context, chooser, "Can't share this file")
}

/** Opens the finished file in an app that can show it. */
internal fun openSavedFile(context: Context, savedUri: String?, fileName: String? = null) {
    if (savedUri.isNullOrBlank()) return
    val uri = try { Uri.parse(savedUri) } catch (e: Exception) { return }
    if (uri.scheme != "content") {
        // Android 9 and below saves a plain file path: open the system Downloads list instead.
        launchExternal(context, Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS), "Can't open this file")
        return
    }
    val type = savedMime(context, uri, fileName)
    val view = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, type)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    view.clipData = ClipData.newRawUri("file", uri)
    try {
        val activity = context.findActivity()
        if (activity != null) activity.startActivity(view) else context.startActivity(Intent(view).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        // no app knows this exact type: let the user pick any app instead of failing
        val any = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "*/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        any.clipData = view.clipData
        launchExternal(
            context,
            Intent.createChooser(any, "Open with").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            "Can't open this file",
            "No app found to open this file"
        )
    } catch (e: Exception) {
        Toast.makeText(context, "Can't open this file", Toast.LENGTH_SHORT).show()
    }
}

/** Tag put in front of a folder reference when it is passed around as "the thing to open". */
internal const val FOLDER_TAG = "folder:"

/** What the Open button of a card opens: the folder of a multi-file torrent, otherwise the saved file. */
internal fun openTarget(item: DownloadItem): String? =
    if (item.isFolderTorrent) FOLDER_TAG + item.folderRef else item.savedUri

/** Opens the folder of a multi-file torrent in a file manager. Falls back to the system Downloads list. */
internal fun openTorrentFolder(context: Context, folderRef: String) {
    val intent = Intent(Intent.ACTION_VIEW).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    when {
        folderRef.startsWith(TorrentEngine.FOLDER_MEDIASTORE) -> {
            val name = folderRef.removePrefix(TorrentEngine.FOLDER_MEDIASTORE)
            val docId = "primary:" + android.os.Environment.DIRECTORY_DOWNLOADS + "/" + name
            intent.setDataAndType(
                android.provider.DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", docId),
                android.provider.DocumentsContract.Document.MIME_TYPE_DIR
            )
        }
        folderRef.startsWith(TorrentEngine.FOLDER_FILE) -> {
            openDownloadsList(context)
            return
        }
        else -> intent.setDataAndType(Uri.parse(folderRef), android.provider.DocumentsContract.Document.MIME_TYPE_DIR)
    }
    try {
        val activity = context.findActivity()
        if (activity != null) activity.startActivity(intent) else context.startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        // no file manager can open this folder directly: show the system Downloads list instead
        openDownloadsList(context)
    }
}

private fun openDownloadsList(context: Context) {
    launchExternal(context, Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS), "Can't open the folder")
}

internal fun mimeFromName(name: String): String =
    MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase()) ?: "*/*"
