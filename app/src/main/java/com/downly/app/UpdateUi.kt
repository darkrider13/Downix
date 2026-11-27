package com.downly.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
        is UpdateState.ReadyToInstall -> "Update ready" to "Tap to install Downly ${state.info.versionName}"
        is UpdateState.Available -> "Update available" to "Downly ${state.info.versionName} is ready to download"
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
        Icon(Icons.Rounded.SystemUpdate, null, tint = Downly.colors.green, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Downly.colors.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, color = Downly.colors.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** The update popup: what is new, then Update now -> progress -> Install. */
@Composable
internal fun UpdateDialog(
    state: UpdateState,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onClose: () -> Unit
) {
    val info = state.info()
    GlassDialog(onClose) {
        if (info == null) {
            Text("Downly is up to date", color = Downly.colors.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("You have version ${BuildConfig.VERSION_NAME}.", color = Downly.colors.textSecondary, fontSize = 14.sp)
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                PillButton("Done", onClick = onClose)
            }
            return@GlassDialog
        }

        val title = when (state) {
            is UpdateState.ReadyToInstall -> "Ready to install"
            is UpdateState.Downloading -> "Downloading update"
            is UpdateState.Error -> "Update problem"
            else -> "Update available"
        }
        Text(title, color = Downly.colors.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        val size = if (info.apkSize > 0) "  |  ${formatBytes(info.apkSize)}" else ""
        Text(
            "Downly ${info.versionName}$size",
            color = Downly.colors.text,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text("You have ${BuildConfig.VERSION_NAME}", color = Downly.colors.textSecondary, fontSize = 13.sp)

        if (info.notes.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                info.notes,
                color = Downly.colors.text,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                maxLines = 8,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(18.dp))

        when (state) {
            is UpdateState.Downloading -> {
                GlassProgress(
                    progress = state.progress,
                    indeterminate = !state.hasKnownSize,
                    color = Downly.colors.greenBar
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Downloading ${percentText(state)}. You can close this, it continues in the background.",
                    color = Downly.colors.textSecondary,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    PillButton("Close", onClick = onClose, primary = false)
                }
            }

            is UpdateState.ReadyToInstall -> {
                Text(
                    "Android will ask you to confirm. Your downloads and settings stay as they are.",
                    color = Downly.colors.textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    PillButton("Later", onClick = onClose, primary = false)
                    Spacer(Modifier.width(10.dp))
                    PillButton("Install", onClick = onInstall)
                }
            }

            is UpdateState.Error -> {
                Text(state.message, color = Downly.colors.red, fontSize = 14.sp, lineHeight = 19.sp)
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    PillButton("Close", onClick = onClose, primary = false)
                    Spacer(Modifier.width(10.dp))
                    PillButton("Try again", onClick = onDownload)
                }
            }

            else -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    PillButton("Later", onClick = onClose, primary = false)
                    Spacer(Modifier.width(10.dp))
                    PillButton("Update now", onClick = onDownload)
                }
            }
        }
    }
}

/** "Check for updates" row inside the Settings popup. */
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
        else -> "Current version ${BuildConfig.VERSION_NAME}"
    }
    SettingRow(title = "Check for updates", subtitle = subtitle) {
        when {
            !configured || state is UpdateState.Checking -> Unit
            state is UpdateState.Available -> PillButton("Update", onClick = onOpen)
            state is UpdateState.Downloading -> PillButton("View", onClick = onOpen, primary = false)
            state is UpdateState.ReadyToInstall -> PillButton("Install", onClick = onInstall)
            state is UpdateState.Error && state.info != null -> PillButton("Details", onClick = onOpen, primary = false)
            else -> PillButton("Check", onClick = onCheck, primary = false)
        }
    }
}
