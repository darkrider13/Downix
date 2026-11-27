package com.downly.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        DownloadFilter.values().forEach { option ->
            FilterPill(
                label = option.label,
                count = items.count { option.matches(it) },
                selected = option == filter,
                onClick = { onFilter(option) }
            )
        }
    }
}

/** 32 dp chip, radius 16. Selected = white with black bold text, others = glass. */
@Composable
private fun FilterPill(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    val c = Downly.colors
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .height(32.dp)
            .pressable(onClick = onClick)
            .then(if (selected) Modifier.whiteButton(shape) else Modifier.glass(shape))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "$label $count",
            color = if (selected) Color.Black else c.textSecondary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
internal fun SearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth().focusRequester(focus),
        placeholder = { Text("Search name or link") },
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        leadingIcon = { Icon(Icons.Rounded.Search, null, tint = Downly.colors.text) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Clear search",
                    tint = Downly.colors.textSecondary,
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .clip(CircleShape)
                        .clickable { onQueryChange("") }
                        .padding(8.dp)
                        .size(20.dp)
                )
            }
        },
        colors = glassFieldColors()
    )
}

/** Same text-field look as the Add dialog, shared by search, link and file name fields. */
@Composable
internal fun glassFieldColors(): TextFieldColors {
    val c = Downly.colors
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
        Icon(Icons.Rounded.Search, null, tint = Downly.colors.textSecondary, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(14.dp))
        Text(
            if (searching) "No matches" else filter.emptyTitle,
            color = Downly.colors.text,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (searching) "Nothing in this list matches \"${query.trim()}\"." else filter.emptyHint,
            color = Downly.colors.textSecondary,
            fontSize = 14.sp,
            lineHeight = 20.sp,
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
        Text(label, color = Downly.colors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(2.dp))
        Text(value, color = Downly.colors.text, fontSize = 13.sp, lineHeight = 18.sp, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    }
}

/** Opened by tapping a card. File actions only appear for finished downloads. */
@Composable
internal fun DetailsDialog(
    item: DownloadItem,
    onClose: () -> Unit,
    onCopyLink: () -> Unit,
    onShare: () -> Unit,
    onOpen: () -> Unit,
    onDeleteFile: () -> Unit
) {
    GlassBottomSheet(onDismiss = onClose, title = "Details") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FileTile(item.fileName)
            Spacer(Modifier.width(12.dp))
            Text(
                item.fileName.ifBlank { "Getting file info" },
                color = Downly.colors.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 18.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(10.dp))
        SettingDivider()
        DetailRow("Link", item.url, maxLines = 4)
        SettingDivider()
        DetailRow("Size", detailSize(item))
        SettingDivider()
        DetailRow("Added", dateText(item.createdAt))
        SettingDivider()
        Column(Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
            Text("Status", color = Downly.colors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            StatusLine(item)
        }
        SettingDivider()
        DetailRow(
            "Saved to",
            if (item.isCompleted) "${SaveLocation.savedFolderLabel(item.savedUri)} / ${item.fileName}"
            else "Your download folder, when the download is finished"
        )
        if (item.state == DownloadState.FAILED && !item.error.isNullOrBlank()) {
            SettingDivider()
            DetailRow("Error", item.error)
        }

        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("Copy link", onClick = onCopyLink, modifier = Modifier.weight(1f), primary = false)
            if (item.isCompleted) {
                PillButton("Share file", onClick = onShare, modifier = Modifier.weight(1f), primary = false)
            }
        }
        if (item.isCompleted) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton("Open", onClick = onOpen, modifier = Modifier.weight(1f))
                PillButton("Delete file", onClick = onDeleteFile, modifier = Modifier.weight(1f), danger = true)
            }
        }
        Spacer(Modifier.height(12.dp))
        PillButton("Close", onClick = onClose, modifier = Modifier.fillMaxWidth())
    }
}

/** Delete sheet opened from the details popup (file is already known to be on the phone). */
@Composable
internal fun ConfirmDeleteFileDialog(fileName: String, onKeep: () -> Unit, onDelete: () -> Unit) {
    GlassBottomSheet(onDismiss = onKeep, title = "Delete file from phone?") {
        Text(
            "${fileName.ifBlank { "This file" }} will be deleted from your phone and removed from the list. This can't be undone.",
            color = Downly.colors.textSecondary,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton("Cancel", onClick = onKeep, outlineRed = true, modifier = Modifier.weight(1f))
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
    val c = Downly.colors
    var alsoFile by remember { mutableStateOf(false) }
    GlassBottomSheet(onDismiss = onCancel, title = "Delete download?") {
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
            DownlyCheckbox(alsoFile)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Also delete the file from phone", color = c.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("This can't be undone", color = c.textSecondary, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton("Cancel", onClick = onCancel, outlineRed = true, modifier = Modifier.weight(1f))
            PillButton(
                "Delete",
                onClick = { if (alsoFile) onDeleteFile() else onListOnly() },
                danger = true,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/*  Actions: copy link, share file                                     */
/* ------------------------------------------------------------------ */

internal fun copyLink(context: Context, url: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("Downly link", url))
    // Android 13+ shows its own "copied" confirmation.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
    }
}

/** Shares the finished file itself (not the link) with any app. */
internal fun shareDownloaded(context: Context, item: DownloadItem) {
    val saved = item.savedUri
    if (saved.isNullOrBlank()) return
    try {
        val parsed = Uri.parse(saved)
        val uri = if (parsed.scheme == "content") {
            parsed
        } else {
            // Android 9 and below: plain file, shared through our FileProvider.
            val path = parsed.path ?: return
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(path))
        }
        val type = context.contentResolver.getType(uri) ?: mimeFromName(item.fileName)
        val send = Intent(Intent.ACTION_SEND)
            .setType(type)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(item.fileName, uri)
        context.startActivity(Intent.createChooser(send, "Share file").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(context, "Can't share this file", Toast.LENGTH_SHORT).show()
    }
}

private fun mimeFromName(name: String): String =
    MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase()) ?: "*/*"
