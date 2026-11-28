package com.downix.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * "Add torrent" popup, opened by the magnet button in the + menu (or by tapping a magnet link in any app).
 * Paste a magnet link, check it, tap Download. The real name and size arrive after the download has started
 * (a magnet link only carries the hash; peers send the details).
 */
@Composable
internal fun MagnetDialog(
    initial: String,
    onFindDuplicate: suspend (String) -> DownloadItem?,
    onDismiss: () -> Unit,
    onPickFile: () -> Unit,
    onSubmit: (magnet: String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    var text by remember { mutableStateOf(initial) }
    var showError by remember { mutableStateOf(false) }
    var duplicate by remember { mutableStateOf<DownloadItem?>(null) }

    LaunchedEffect(Unit) {
        if (initial.isBlank()) {
            delay(300) // after the popup has finished opening, so the keyboard does not make it jump
            runCatching { focus.requestFocus() }
        }
    }

    val valid = TorrentEngine.normalizeMagnet(text) != null

    fun submit(skipDuplicateCheck: Boolean = false) {
        val magnet = TorrentEngine.normalizeMagnet(text)
        if (magnet == null) {
            showError = true
            return
        }
        if (skipDuplicateCheck) {
            onSubmit(magnet)
            return
        }
        scope.launch {
            val existing = onFindDuplicate(magnet)
            if (existing == null) onSubmit(magnet) else duplicate = existing
        }
    }

    GlassCenterDialog(onDismiss = onDismiss, title = "Add torrent") {
        val c = Downix.colors
        GlassField(
            value = text,
            onValueChange = {
                text = it
                showError = false
                duplicate = null
            },
            placeholder = "Magnet link",
            isError = showError,
            focusRequester = focus,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { submit() }),
            trailing = {
                // Empty box: paste button. As soon as there is text: one tap clears the box.
                AnimatedContent(
                    targetState = text.isNotEmpty(),
                    transitionSpec = {
                        (fadeIn(tween(140)) + scaleIn(initialScale = 0.7f, animationSpec = tween(140))) togetherWith
                            (fadeOut(tween(90)) + scaleOut(targetScale = 0.7f, animationSpec = tween(90)))
                    },
                    label = "magnetFieldAction"
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
                                        text = TorrentEngine.extractMagnet(it) ?: it.trim()
                                    }
                                }
                                showError = false
                                duplicate = null
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
            Text(
                "That is not a valid magnet link. It must start with magnet:?xt=urn:btih:",
                color = c.red,
                fontSize = 11.sp
            )
        }

        duplicate?.let { existing ->
            Spacer(Modifier.height(10.dp))
            Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(14.dp)).padding(12.dp)) {
                Text("Already in your list", color = c.paused, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(
                    "${existing.fileName.ifBlank { "This torrent" }}, ${stateLabel(existing).lowercase()}",
                    color = c.textSecondary,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val message = when {
                valid -> "Magnet link looks good. Name and size appear once the download starts."
                text.isBlank() -> "Paste a magnet link to download a torrent."
                else -> "Keep typing or paste a full magnet link."
            }
            Text(message, color = if (valid) c.green else c.textSecondary, fontSize = 11.sp)
        }

        Spacer(Modifier.height(12.dp))
        PillButton("Choose .torrent file", onClick = onPickFile, small = true)

        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton("Cancel", onClick = onDismiss, outlineRed = true, modifier = Modifier.weight(1f))
            PillButton(
                if (duplicate != null) "Download again" else "Download",
                onClick = { if (duplicate != null) submit(skipDuplicateCheck = true) else submit() },
                green = true,
                enabled = valid,
                modifier = Modifier.weight(1f)
            )
        }
    }
}


/**
 * "Choose files" popup for a torrent with several files. The torrent waits (nothing is downloaded) until the
 * user taps Download. Back / outside tap = download everything, Cancel = remove the torrent.
 */
@Composable
internal fun TorrentFilesDialog(
    name: String,
    files: List<TorrentFileInfo>,
    onCancelTorrent: () -> Unit,
    onConfirm: (Set<Int>) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf(files.map { it.index }.toSet()) }
    val allSelected = selected.size == files.size
    val selectedSize = files.filter { it.index in selected }.sumOf { it.size }

    GlassCenterDialog(onDismiss = onDismiss, title = "Choose files") {
        val c = Downix.colors
        Text(name, color = c.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${selected.size} of ${files.size} files · ${formatBytes(selectedSize)}",
                color = c.textSecondary,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                if (allSelected) "Select none" else "Select all",
                color = c.text,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { selected = if (allSelected) emptySet() else files.map { it.index }.toSet() }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp)
                .clip(RoundedCornerShape(14.dp))
                .glass(RoundedCornerShape(14.dp))
                .verticalScroll(rememberScrollState())
        ) {
            files.forEach { file ->
                val on = file.index in selected
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { selected = if (on) selected - file.index else selected + file.index }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (on) DownixIcons.SelectAll else DownixIcons.Square,
                        contentDescription = if (on) "Selected" else "Not selected",
                        tint = if (on) c.green else c.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.size(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            file.path.substringAfterLast('/'),
                            color = c.text,
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(formatBytes(file.size), color = c.textSecondary, fontSize = 11.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton("Cancel", onClick = onCancelTorrent, outlineRed = true, modifier = Modifier.weight(1f))
            PillButton(
                "Download",
                onClick = { onConfirm(selected) },
                green = true,
                enabled = selected.isNotEmpty(),
                modifier = Modifier.weight(1f)
            )
        }
    }
}
