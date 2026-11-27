package com.downly.app

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Developer credit shown at the bottom of Settings.
 * To turn an icon on, paste the full link (https://...) between the quotes below.
 * An empty link does nothing when the icon is tapped.
 */
internal object DeveloperInfo {
    const val NAME = "MD.INSANUL ISLAM ABIR"
    const val WHATSAPP = ""   // e.g. "https://wa.me/8801XXXXXXXXX"
    const val INSTAGRAM = ""  // e.g. "https://instagram.com/yourname"
    const val TELEGRAM = ""   // e.g. "https://t.me/yourname"
}

@Composable
internal fun DeveloperCredit() {
    val c = Downly.colors
    val context = LocalContext.current
    val open: (String) -> Unit = { link ->
        if (link.isNotBlank()) {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Developed by ${DeveloperInfo.NAME}",
            color = c.text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CreditIcon(DownlyIcons.WhatsApp, "WhatsApp") { open(DeveloperInfo.WHATSAPP) }
            CreditIcon(DownlyIcons.Instagram, "Instagram") { open(DeveloperInfo.INSTAGRAM) }
            CreditIcon(DownlyIcons.Telegram, "Telegram") { open(DeveloperInfo.TELEGRAM) }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "\u00A9 Copyright ${DeveloperInfo.NAME}. All rights reserved.",
            color = c.textSecondary,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CreditIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    AnimatedIconButton(
        icon = icon,
        description = description,
        tint = Downly.colors.text,
        onClick = onClick
    )
}
