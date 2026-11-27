package com.downly.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/* ------------------------------------------------------------------ */
/*  Downly UI theme: Day + AMOLED Dark (from the Downly_Design_AI pack) */
/*  Same layout in both modes, only these colours change.              */
/* ------------------------------------------------------------------ */

/** What the user picks in Settings > Theme. AUTO follows the phone. */
enum class ThemeMode(val label: String, val key: String) {
    AUTO("Auto", "auto"),
    DAY("Day", "day"),
    DARK("Dark", "dark");

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        AUTO -> systemDark
        DAY -> false
        DARK -> true
    }

    companion object {
        fun fromKey(key: String?): ThemeMode = values().firstOrNull { it.key == key } ?: AUTO
    }
}

@Immutable
class DownlyColors(
    val isDark: Boolean,
    val background: Color,
    val text: Color,
    val textSecondary: Color,
    val glassFill: Color,
    val glassBorder: Color,
    val glassHighlight: Color,
    val green: Color,        // "Completed", speed, percent, Download button
    val greenBar: Color,     // progress bar
    val onGreen: Color,
    val red: Color,          // delete, cancel, clear finished
    val onRed: Color,
    val track: Color,        // progress track
    val paused: Color,       // paused bar / knob
    val divider: Color,
    val sheetFill: Color,    // bottom sheets, toast, dialogs
    val scrim: Color,        // dialog / sheet scrim
    val plusOverlay: Color,  // overlay behind the + menu
    val whiteBorder: Color,  // border of the white buttons
    val toggleOffTrack: Color,
    val toggleOffBorder: Color
)

private val White = Color(0xFFFFFFFF)
private val Black = Color(0xFF000000)

val DownlyDark = DownlyColors(
    isDark = true,
    background = Black,
    text = Color(0xFFF5F5F5),
    textSecondary = Color(0xFFA6A6AA),
    glassFill = White.copy(alpha = 0.06f),
    glassBorder = White.copy(alpha = 0.13f),
    glassHighlight = White.copy(alpha = 0.06f),
    green = Color(0xFF00FF00),
    greenBar = Color(0xFF00FF00),
    onGreen = Black,
    red = Color(0xFFFF0000),
    onRed = Black,
    track = White.copy(alpha = 0.14f),
    paused = Color(0xFF7A7A80),
    divider = White.copy(alpha = 0.10f),
    sheetFill = Color(0xFF18181C).copy(alpha = 0.86f),
    scrim = Black.copy(alpha = 0.62f),
    plusOverlay = Black.copy(alpha = 0.70f),
    whiteBorder = White,
    toggleOffTrack = White.copy(alpha = 0.14f),
    toggleOffBorder = White.copy(alpha = 0.10f)
)

val DownlyDay = DownlyColors(
    isDark = false,
    background = Color(0xFFE8EEF5),
    text = Color(0xFF101828),
    textSecondary = Color(0xFF465063),
    glassFill = White.copy(alpha = 0.48f),
    glassBorder = White.copy(alpha = 0.95f),
    glassHighlight = Color.Transparent,
    green = Color(0xFF007000),
    greenBar = Color(0xFF008A00),
    onGreen = White,
    red = Color(0xFFC40000),
    onRed = White,
    track = Color(0xFF101828).copy(alpha = 0.10f),
    paused = Color(0xFF7D8594),
    divider = Color(0xFF101828).copy(alpha = 0.09f),
    sheetFill = Color(0xFFF8F9FD).copy(alpha = 0.93f),
    scrim = Color(0xFF141828).copy(alpha = 0.38f),
    plusOverlay = Color(0xFFE8ECF8).copy(alpha = 0.52f),
    whiteBorder = Color(0xFF101828).copy(alpha = 0.16f),
    toggleOffTrack = Color(0xFF101828).copy(alpha = 0.10f),
    toggleOffBorder = Color(0xFF101828).copy(alpha = 0.09f)
)

val LocalDownlyColors = staticCompositionLocalOf { DownlyDark }

/** Short access: `val c = Downly.colors` inside any composable. */
object Downly {
    val colors: DownlyColors
        @Composable
        @ReadOnlyComposable
        get() = LocalDownlyColors.current
}

@Composable
fun DownlyTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = mode.isDark(isSystemInDarkTheme())
    val colors = if (dark) DownlyDark else DownlyDay
    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.text,
            onPrimary = colors.background,
            background = colors.background,
            surface = colors.background,
            onSurface = colors.text
        )
    } else {
        lightColorScheme(
            primary = colors.text,
            onPrimary = colors.background,
            background = colors.background,
            surface = colors.background,
            onSurface = colors.text
        )
    }
    CompositionLocalProvider(LocalDownlyColors provides colors) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/* ------------------------------------------------------------------ */
/*  Background: very soft radial glows (ellipses, fade out at 70 %)    */
/* ------------------------------------------------------------------ */

private fun DrawScope.glow(color: Color, cxFrac: Float, cyFrac: Float, rxDp: Float, ryDp: Float) {
    val rx = rxDp.dp.toPx()
    val ry = ryDp.dp.toPx()
    val center = Offset(size.width * cxFrac, size.height * cyFrac)
    withTransform({ scale(1f, ry / rx, center) }) {
        drawCircle(
            brush = Brush.radialGradient(
                0f to color,
                0.7f to color.copy(alpha = 0f),
                center = center,
                radius = rx
            ),
            radius = rx,
            center = center
        )
    }
}

@Composable
fun DownlyBackground(content: @Composable BoxScope.() -> Unit) {
    val c = Downly.colors
    Box(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .drawBehind {
                if (c.isDark) {
                    glow(Color(0xFF28D2C8).copy(alpha = 0.18f), 0f, 0f, 420f, 340f)
                    glow(Color(0xFF826EFF).copy(alpha = 0.18f), 1f, 0.24f, 400f, 400f)
                    glow(Color(0xFF4682FF).copy(alpha = 0.10f), 0.20f, 0.58f, 360f, 320f)
                    glow(Color(0xFF28D2C8).copy(alpha = 0.10f), 1f, 0.80f, 360f, 340f)
                    glow(Color(0xFFFF6EA5).copy(alpha = 0.13f), 0.08f, 1f, 480f, 400f)
                } else {
                    glow(Color(0xFF9FEDE2), 0.05f, 0f, 430f, 360f)
                    glow(Color(0xFFC8C0F8), 1f, 0.20f, 400f, 430f)
                    glow(Color(0xFFF0C6D3), 0.08f, 1f, 520f, 460f)
                }
            },
        content = content
    )
}

/* ------------------------------------------------------------------ */
/*  Surfaces and press feedback                                        */
/* ------------------------------------------------------------------ */

/** Soft glass surface. strong = the more opaque sheet / dialog fill. */
internal fun Modifier.glass(
    shape: Shape = RoundedCornerShape(16.dp),
    strong: Boolean = false
): Modifier = composed {
    val c = Downly.colors
    val fill = if (strong) c.sheetFill else c.glassFill
    this
        .then(
            if (c.isDark) Modifier else Modifier.shadow(
                elevation = 4.dp,
                shape = shape,
                clip = false,
                ambientColor = Color(0xFF37416E).copy(alpha = 0.10f),
                spotColor = Color(0xFF37416E).copy(alpha = 0.10f)
            )
        )
        .clip(shape)
        .background(fill, shape)
        .drawBehind {
            if (c.isDark) {
                val inset = 14.dp.toPx()
                drawLine(
                    color = c.glassHighlight,
                    start = Offset(inset, 0.5f),
                    end = Offset(size.width - inset, 0.5f),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }
        .border(1.dp, c.glassBorder, shape)
}

/** WHITE button surface: the default look for normal actions. */
internal fun Modifier.whiteButton(shape: Shape = CircleShape): Modifier = composed {
    val c = Downly.colors
    this
        .shadow(
            elevation = 2.dp,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = 0.12f),
            spotColor = Color.Black.copy(alpha = 0.12f)
        )
        .clip(shape)
        .background(White, shape)
        .border(1.dp, c.whiteBorder, shape)
}

/** Soft press feedback (spring scale) for every tappable element. */
internal fun Modifier.pressable(
    enabled: Boolean = true,
    pressedScale: Float = 0.94f,
    onClick: () -> Unit
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
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

/* ------------------------------------------------------------------ */
/*  Buttons                                                            */
/* ------------------------------------------------------------------ */

/**
 * Round WHITE icon button (pause / play / open / + / -). Default size 34.
 * The tap area is always at least 40 dp, the circle itself keeps [size].
 */
@Composable
internal fun GlassIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
    enabled: Boolean = true
) {
    val touch = if (size < 40.dp) 40.dp else size
    Box(
        modifier
            .size(touch)
            .alpha(if (enabled) 1f else 0.4f)
            .pressable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.size(size).whiteButton(CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, tint = Color.Black, modifier = Modifier.size(18.dp))
        }
    }
}

/** Plain icon without a background (search, settings, red cancel / trash). 40 dp tap area, 18 dp icon. */
@Composable
internal fun PlainIconButton(
    icon: ImageVector,
    description: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Box(
        modifier
            .size(40.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .pressable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(18.dp))
    }
}

/**
 * Pill button, 42 dp high, 13 sp bold.
 *  - default            : WHITE (normal actions)
 *  - green = true       : only the main "Download" button
 *  - danger = true      : RED filled ("Delete")
 *  - outlineRed = true  : RED outline ("Cancel")
 *  - small = true       : 32 dp high, 12 sp semibold
 * [primary] is kept only so old calls still compile; it no longer changes the look.
 */
@Composable
internal fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    danger: Boolean = false,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    green: Boolean = false,
    outlineRed: Boolean = false,
    small: Boolean = false
) {
    val c = Downly.colors
    val shape = CircleShape
    val fill: Color
    val content: Color
    val border: Color
    when {
        green -> { fill = c.green; content = c.onGreen; border = c.green }
        danger -> { fill = c.red; content = c.onRed; border = c.red }
        outlineRed -> { fill = Color.Transparent; content = c.red; border = c.red }
        else -> { fill = White; content = Color.Black; border = c.whiteBorder }
    }
    val look = if (fill == White) {
        Modifier.whiteButton(shape)
    } else {
        Modifier.clip(shape).background(fill, shape).border(1.dp, border, shape)
    }
    Row(
        modifier
            .pressable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f)
            .then(look)
            .heightIn(min = if (small) 32.dp else 42.dp)
            .padding(horizontal = if (small) 14.dp else 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(icon, null, tint = content, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text,
            color = content,
            fontSize = if (small) 12.sp else 13.sp,
            fontWeight = if (small) FontWeight.SemiBold else FontWeight.Bold
        )
    }
}

/** Auto / Day / Dark switch used in Settings. Selected = white pill. */
@Composable
internal fun ThemeSegmented(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val c = Downly.colors
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.glass(shape).padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        ThemeMode.values().forEach { mode ->
            val on = mode == selected
            val pill = RoundedCornerShape(15.dp)
            Box(
                Modifier
                    .heightIn(min = 30.dp)
                    .then(if (on) Modifier.whiteButton(pill) else Modifier.clip(pill))
                    .pressable { onSelect(mode) }
                    .padding(horizontal = 11.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    mode.label,
                    color = if (on) Color.Black else c.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/** Pop-up container (still used by the dialogs that get their bottom-sheet look in the next steps). */
@Composable
internal fun GlassDialog(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }
        AnimatedVisibility(
            visible = shown,
            enter = fadeIn(tween(160)) + scaleIn(
                initialScale = 0.94f,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium)
            )
        ) {
            Column(
                Modifier
                    .padding(horizontal = 16.dp)
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .glass(RoundedCornerShape(24.dp), strong = true)
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                content = content
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/*  Icons: thin 1.8 stroke, same paths as the design pack (24x24)      */
/* ------------------------------------------------------------------ */

internal object DownlyIcons {
    private fun icon(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            paths.forEach { d ->
                addPath(
                    pathData = PathParser().parsePathString(d).toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.8f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round
                )
            }
        }.build()

    val Search by lazy { icon("Search", "M5 11a6 6 0 1 0 12 0a6 6 0 1 0-12 0", "M20 20l-4.5-4.5") }
    val Pause by lazy { icon("Pause", "M9 5v14M15 5v14") }
    val ClearAll by lazy { icon("ClearAll", "M4 7h16M4 12h11M4 17h7") }
    val Settings by lazy {
        icon(
            "Settings",
            "M9 12a3 3 0 1 0 6 0a3 3 0 1 0-6 0",
            "M12 2v3M12 19v3M2 12h3M19 12h3M4.9 4.9L7 7M17 17l2.1 2.1M4.9 19.1L7 17M17 7l2.1-2.1"
        )
    }
    val Play by lazy { icon("Play", "M8 5l11 7-11 7z") }
    val Close by lazy { icon("Close", "M6 6l12 12M18 6L6 18") }
    val OpenFile by lazy { icon("OpenFile", "M14 4h6v6M20 4l-9 9M18 14v6H4V6h6") }
    val Trash by lazy { icon("Trash", "M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13") }
    val Plus by lazy { icon("Plus", "M12 5v14M5 12h14") }
    val Minus by lazy { icon("Minus", "M5 12h14") }
    val Check by lazy { icon("Check", "M5 12l5 5 9-10") }
    val Back by lazy { icon("Back", "M15 5l-7 7 7 7") }
    val Link by lazy {
        icon(
            "Link",
            "M10 14a4 4 0 0 0 6 0l3-3a4 4 0 0 0-6-6l-1 1M14 10a4 4 0 0 0-6 0l-3 3a4 4 0 0 0 6 6l1-1"
        )
    }
    val Clipboard by lazy { icon("Clipboard", "M9 4h6v3H9zM9 5H6v16h12V5h-3M9 14l2 2 4-4") }
    val Retry by lazy { icon("Retry", "M20 12a8 8 0 1 1-2.5-5.8M20 4v5h-5") }

    // File type tiles (video / zip / document come from the design, the rest follow the same style)
    val FileVideo by lazy { icon("FileVideo", "M4 5h16v14H4zM8 5v14M16 5v14M4 12h16") }
    val FileZip by lazy { icon("FileZip", "M5 3h14v18H5zM12 3v10M10 6h4M10 9h4") }
    val FileDoc by lazy { icon("FileDoc", "M6 3h8l4 4v14H6zM9 12h6M9 16h6") }
    val FileImage by lazy { icon("FileImage", "M4 5h16v14H4zM4 16l5-5 4 4 3-3 4 4M15 9.5h.01") }
    val FileAudio by lazy {
        icon("FileAudio", "M9 18V6l10-2v12M9 18a3 3 0 1 1-6 0a3 3 0 1 1 6 0M19 16a3 3 0 1 1-6 0a3 3 0 1 1 6 0")
    }
    val FileApp by lazy { icon("FileApp", "M12 3l8 4.5v9L12 21l-8-4.5v-9zM4 7.5l8 4.5 8-4.5M12 12v9") }
    val FileOther by lazy { icon("FileOther", "M6 3h8l4 4v14H6zM14 3v4h4") }
}
