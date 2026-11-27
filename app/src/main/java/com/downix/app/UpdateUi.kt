package com.downix.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* ------------------------------------------------------------------ */
/*  Update UI: banner under the header, popup, row in Settings         */
/* ------------------------------------------------------------------ */

private fun UpdateState.info(): UpdateInfo? = when (this) {
    is UpdateState.Available -> this.info
    is UpdateState.Downloading -> this.info
    is UpdateState.ReadyToInstall -> this.info
    is UpdateState.Error -> this.info
    else -> null
}

private fun percentText(state: UpdateState.Downloading): String =
    if (state.hasKnownSize) "${(state.progress * 100).toInt()}%" else formatBytes(state.downloadedBytes)

/** Tappable glass strip shown on the main screen while an update is waiting, downloading or ready. */
@Composable
internal fun UpdateBanner(state: UpdateState, onClick: () -> Unit) {
    val (title, subtitle) = when (state) {
        is UpdateState.Downloading -> "Downloading update" to percentText(state)
        is UpdateState.ReadyToInstall -> "Update ready" to "Tap to install Downix ${state.info.versionName}"
        is UpdateState.Available -> "Update available" to "Downix ${state.info.versionName} is ready to download"
        else -> "Update" to ""
    }
    Row(
        Modifier
            .padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
            .fillMaxWidth()
            .glass(RoundedCornerShape(16.dp))
            .pressable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.SystemUpdate, null, tint = Downix.colors.green, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Downix.colors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, color = Downix.colors.textSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** The update sheet: what is new, then Update now -> progress -> Install. */
@Composable
internal fun UpdateDialog(
    state: UpdateState,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onClose: () -> Unit
) {
    val c = Downix.colors
    val info = state.info()
    val title = when {
        info == null -> "Downix is up to date"
        state is UpdateState.ReadyToInstall -> "Ready to install"
        state is UpdateState.Downloading -> "Downloading update"
        state is UpdateState.Error -> "Update problem"
        else -> "Update available"
    }
    GlassBottomSheet(onDismiss = onClose, title = title) {
        if (info == null) {
            Text("You have version ${BuildConfig.VERSION_NAME}.", color = c.textSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            PillButton("Done", onClick = onClose, modifier = Modifier.fillMaxWidth())
            return@GlassBottomSheet
        }

        val size = if (info.apkSize > 0) "  |  ${formatBytes(info.apkSize)}" else ""
        Text("Downix ${info.versionName}$size", color = c.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text("You have ${BuildConfig.VERSION_NAME}", color = c.textSecondary, fontSize = 11.sp)

        Spacer(Modifier.height(10.dp))
        Text("What's new", color = c.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        Text(
            "Bug fixes, performance improvements and a smoother experience.",
            color = c.textSecondary,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
        Spacer(Modifier.height(16.dp))

        when (state) {
            is UpdateState.Downloading -> {
                GlassProgress(
                    progress = state.progress,
                    indeterminate = !state.hasKnownSize,
                    color = c.greenBar
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Downloading ${percentText(state)}. You can close this, it continues in the background.",
                    color = c.textSecondary,
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(14.dp))
                PillButton("Close", onClick = onClose, modifier = Modifier.fillMaxWidth())
            }

            is UpdateState.ReadyToInstall -> {
                Text(
                    "Android will ask you to confirm. Your downloads and settings stay as they are.",
                    color = c.textSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PillButton("Later", onClick = onClose, modifier = Modifier.weight(1f))
                    PillButton("Install", onClick = onInstall, modifier = Modifier.weight(1f))
                }
            }

            is UpdateState.Error -> {
                Text(state.message, color = c.red, fontSize = 12.sp, lineHeight = 17.sp)
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PillButton("Close", onClick = onClose, modifier = Modifier.weight(1f))
                    PillButton("Try again", onClick = onDownload, modifier = Modifier.weight(1f))
                }
            }

            else -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PillButton("Later", onClick = onClose, modifier = Modifier.weight(1f))
                    PillButton("Update now", onClick = onDownload, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Small "update available" dot: a soft green dot with a thin, faint ring around it and a clear gap between
 * them. Used on the settings icon (home header) and on the Update button (Settings screen).
 */
@Composable
internal fun UpdateDot(modifier: Modifier = Modifier) {
    val c = Downix.colors
    Canvas(modifier.size(14.dp)) {
        drawCircle(c.green.copy(alpha = 0.85f), radius = 3.dp.toPx())
        drawCircle(c.green.copy(alpha = 0.35f), radius = 6.dp.toPx(), style = Stroke(width = 1.dp.toPx()))
    }
}

/** "Check now" row inside the Settings screen. */
@Composable
internal fun UpdateSettingsRow(
    state: UpdateState,
    configured: Boolean,
    onCheck: () -> Unit,
    onOpen: () -> Unit,
    onInstall: () -> Unit
) {
    val subtitle = when {
        !configured -> "Not available in this build"
        state is UpdateState.Checking -> "Checking..."
        state is UpdateState.UpToDate -> "You have the latest version (${BuildConfig.VERSION_NAME})"
        state is UpdateState.Available -> "Version ${state.info.versionName} is available"
        state is UpdateState.Downloading -> "Downloading ${percentText(state)}"
        state is UpdateState.ReadyToInstall -> "Version ${state.info.versionName} is ready to install"
        state is UpdateState.Error -> state.message
        else -> "Downix ${BuildConfig.VERSION_NAME}"
    }
    SettingRow(title = "Check now", subtitle = subtitle) {
        when {
            !configured || state is UpdateState.Checking -> Unit
            state is UpdateState.Available -> Box {
                PillButton("Update", onClick = onOpen, modifier = Modifier.widthIn(min = 84.dp), small = true)
                // small green dot INSIDE the pill, on the right side (the pill is widened so it never touches the text)
                val dotColor = Downix.colors.green
                Canvas(Modifier.align(Alignment.CenterEnd).padding(end = 11.dp).size(7.dp)) {
                    drawCircle(dotColor)
                }
            }
            state is UpdateState.Downloading -> PillButton("View", onClick = onOpen, small = true)
            state is UpdateState.ReadyToInstall -> PillButton("Install", onClick = onInstall, small = true)
            state is UpdateState.Error && state.info != null -> PillButton("Details", onClick = onOpen, small = true)
            else -> PillButton("Check", onClick = onCheck, small = true)
        }
    }
}
