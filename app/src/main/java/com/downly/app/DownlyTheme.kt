package com.downly.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.launch
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
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
    val toggleOffBorder: Color,
    val selected: Color,     // selected chip / segment (Day: deep ink, Dark: white)
    val onSelected: Color    // text on the selected chip / segment
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
    sheetFill = Color(0xFF1A1A1F), // fully solid: nothing behind a popup / toast shows through
    scrim = Black.copy(alpha = 0.62f),
    plusOverlay = Black.copy(alpha = 0.70f),
    whiteBorder = White,
    toggleOffTrack = White.copy(alpha = 0.14f),
    toggleOffBorder = White.copy(alpha = 0.10f),
    selected = White,
    onSelected = Black
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
    sheetFill = Color(0xFFF8F9FD), // fully solid
    scrim = Color(0xFF141828).copy(alpha = 0.38f),
    plusOverlay = Color(0xFFE8ECF8).copy(alpha = 0.52f),
    whiteBorder = Color(0xFF101828).copy(alpha = 0.16f),
    toggleOffTrack = Color(0xFF101828).copy(alpha = 0.10f),
    toggleOffBorder = Color(0xFF101828).copy(alpha = 0.09f),
    selected = Color(0xFF1B2438),
    onSelected = White
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
fun DownlyBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val c = Downly.colors
    Box(
        modifier
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
    strong: Boolean = false,
    flat: Boolean = false
): Modifier = composed {
    val c = Downly.colors
    val fill = if (strong) c.sheetFill else c.glassFill
    this
        .then(
            if (c.isDark || flat) Modifier else Modifier.shadow(
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

/**
 * Surface of a SELECTED chip / segment. Dark mode keeps the white pill; Day mode uses a deep ink pill with
 * white text, so it matches the soft pastel background instead of looking like a washed-out white blob.
 */
internal fun Modifier.selectedSurface(shape: Shape): Modifier = composed {
    val c = Downly.colors
    if (c.isDark) this.whiteButton(shape)
    else this.clip(shape).background(c.selected, shape)
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
            Icon(icon, contentDescription = description, tint = Color.Black, modifier = Modifier.size(if (size >= 42.dp) 22.dp else 18.dp))
        }
    }
}

/**
 * Icon-only action button for the home screen (pause all, pause, resume, retry, open): no background circle.
 * 40 dp tap area, 22 dp icon. Pressing squeezes it; when [icon] changes (pause <-> play) the old icon shrinks
 * away and the new one grows in with a small bounce.
 */
@Composable
internal fun AnimatedIconButton(
    icon: ImageVector,
    description: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val alpha by animateFloatAsState(if (enabled) 1f else 0.35f, tween(200), label = "iconButtonAlpha")
    Box(
        modifier
            .size(40.dp)
            .alpha(alpha)
            .pressable(enabled = enabled, pressedScale = 0.82f, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = icon,
            transitionSpec = {
                (fadeIn(tween(160, delayMillis = 70)) + scaleIn(
                    initialScale = 0.4f,
                    animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium)
                )) togetherWith (fadeOut(tween(110)) + scaleOut(targetScale = 0.4f, animationSpec = tween(110)))
            },
            label = "iconSwap"
        ) { shown ->
            Icon(shown, contentDescription = description, tint = tint, modifier = Modifier.size(22.dp))
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
        outlineRed -> { fill = c.red; content = c.onRed; border = c.red }
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
    // Auto is gone from the choices; a phone still on the old Auto setting shows the mode it is following.
    val systemDark = isSystemInDarkTheme()
    val shown = if (selected == ThemeMode.AUTO) (if (systemDark) ThemeMode.DARK else ThemeMode.DAY) else selected
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.glass(shape).padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        ThemeMode.values().filter { it != ThemeMode.AUTO }.forEach { mode ->
            val on = mode == shown
            val pill = RoundedCornerShape(15.dp)
            Box(
                Modifier
                    .heightIn(min = 30.dp)
                    .then(if (on) Modifier.selectedSurface(pill) else Modifier.clip(pill))
                    .pressable { onSelect(mode) }
                    .padding(horizontal = 11.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    mode.label,
                    color = if (on) c.onSelected else c.textSecondary,
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


/**
 * Which popups are open right now. Every popup registers itself here, so the app screen can blur while any
 * popup is open, and a popup that has ANOTHER popup opened above it blurs too.
 */
internal object PopupLayers {
    var count by mutableIntStateOf(0)
}

/**
 * Blur used behind popups. It is a GPU RenderEffect on the layer (Android 12+; older phones just keep the dark
 * scrim). [active] fades the blur in and out; the value is read only while drawing, so nothing recomposes.
 */
internal fun Modifier.blurWhen(active: Boolean, maxRadius: Dp = 10.dp): Modifier = composed {
    val strength = animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(if (active) 240 else 160),
        label = "popupBlur"
    )
    val maxPx = with(LocalDensity.current) { maxRadius.toPx() }
    this.graphicsLayer {
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            val r = strength.value * maxPx
            renderEffect = if (r > 0.5f) BlurEffect(r, r, TileMode.Clamp) else null
        }
    }
}

/**
 * Call inside a Dialog's content. Registers the popup in [PopupLayers] and returns whether another popup is
 * open above this one (then this popup should blur). It also makes the dialog window cover the WHOLE screen,
 * under the status bar and navigation bar too, so the scrim darkens them together with the app.
 */
@Composable
internal fun rememberPopupCovered(): Boolean {
    val level = remember { Snapshot.withoutReadObservation { PopupLayers.count } }
    DisposableEffect(Unit) {
        PopupLayers.count++
        onDispose { PopupLayers.count-- }
    }
    val view = LocalView.current
    val window = (view.parent as? DialogWindowProvider)?.window
    SideEffect {
        window?.apply {
            setDimAmount(0f)
            setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            addFlags(android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            statusBarColor = android.graphics.Color.TRANSPARENT
            navigationBarColor = android.graphics.Color.TRANSPARENT
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                isNavigationBarContrastEnforced = false
                isStatusBarContrastEnforced = false
            }
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                attributes = attributes.also {
                    it.layoutInDisplayCutoutMode =
                        android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
        }
    }
    return PopupLayers.count > level + 1
}

/**
 * Bottom sheet from the design: glass, margin 8, radius 24, drag handle 36x4.
 * Opens with a smooth slide-up. Closes by: swiping the sheet down, tapping anywhere outside it, or Back.
 * Every close slides down smoothly. Moves above the keyboard.
 */
@Composable
internal fun GlassBottomSheet(
    onDismiss: () -> Unit,
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val c = Downly.colors
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    // progress: 0 = hidden, 1 = shown. dragPx: how far the finger pulled the sheet down.
    // Both are read only while drawing, so nothing is recomposed while the sheet moves.
    val progress = remember { Animatable(0f) }
    var dragPx by remember { mutableFloatStateOf(0f) }
    var sheetH by remember { mutableFloatStateOf(0f) }
    var closing by remember { mutableStateOf(false) }
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    // Starts after the first frame is ready, so the heavy first frame does not eat the animation.
    LaunchedEffect(Unit) {
        withFrameNanos { }
        progress.animateTo(1f, tween(240, easing = FastOutSlowInEasing))
    }

    val closeAll: () -> Unit = {
        if (!closing) {
            closing = true
            scope.launch {
                val from = dragPx
                val to = sheetH + with(density) { 48.dp.toPx() }
                animate(from, to, animationSpec = tween(190, easing = FastOutLinearInEasing)) { v, _ -> dragPx = v }
                currentOnDismiss()
            }
        }
    }
    val settle: (Float) -> Unit = { velocity ->
        if (!closing) {
            scope.launch {
                if (dragPx > sheetH * 0.28f || velocity > 1300f) {
                    closeAll()
                } else {
                    animate(dragPx, 0f, animationSpec = spring(dampingRatio = 0.85f, stiffness = 500f)) { v, _ -> dragPx = v }
                }
            }
        }
    }

    // Lets the sheet follow a downward swipe once its own content is at the top.
    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < 0f && dragPx > 0f) {
                    val used = minOf(-available.y, dragPx)
                    dragPx -= used
                    return Offset(0f, -used)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f && source == NestedScrollSource.Drag) {
                    dragPx += available.y
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (dragPx > 0f) {
                    settle(available.y)
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    Dialog(
        onDismissRequest = closeAll,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        val covered = rememberPopupCovered()
        Box(
            Modifier
                .fillMaxSize()
                .blurWhen(covered)
                .drawBehind {
                    val pulled = if (sheetH > 0f) (dragPx / sheetH).coerceIn(0f, 1f) else 0f
                    drawRect(c.scrim, alpha = (progress.value * (1f - pulled)).coerceIn(0f, 1f))
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = closeAll
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                Modifier
                    .graphicsLayer {
                        translationY = (1f - progress.value) * (size.height + 48.dp.toPx()) + dragPx
                    }
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(8.dp)
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .onSizeChanged { sheetH = it.height.toFloat() }
                    .glass(RoundedCornerShape(24.dp), strong = true, flat = true)
                    // swallow taps so they do not reach the scrim
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .nestedScroll(connection)
                    .padding(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.Start
            ) {
                // Handle + title: a bigger grab area that also drags the sheet down.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .draggable(
                            state = rememberDraggableState { delta -> dragPx = (dragPx + delta).coerceAtLeast(0f) },
                            orientation = Orientation.Vertical,
                            onDragStopped = { velocity -> settle(velocity) }
                        )
                ) {
                    Box(
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 10.dp)
                            .size(width = 36.dp, height = 4.dp)
                            .clip(CircleShape)
                            .background(c.textSecondary.copy(alpha = 0.55f))
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(title, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                }
                content()
            }
        }
    }
}

/**
 * Centered pop-up (used by "Add link" and "From clipboard"): glass card in the middle of the screen,
 * dark scrim behind it, fades in and scales up in 200 ms. Tap outside or Back closes it.
 * Moves above the keyboard when it opens.
 */
@Composable
internal fun GlassCenterDialog(
    onDismiss: () -> Unit,
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val c = Downly.colors
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        // Starts after the first frame is ready (no jump), drives scrim + card from one value.
        val progress = remember { Animatable(0f) }
        val covered = rememberPopupCovered()
        LaunchedEffect(Unit) {
            withFrameNanos { }
            progress.animateTo(1f, tween(190, easing = FastOutSlowInEasing))
        }
        Box(
            Modifier
                .fillMaxSize()
                .blurWhen(covered)
                // read in the draw phase: the screen is not recomposed on every animation frame
                .drawBehind { drawRect(c.scrim, alpha = progress.value) }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier
                    .graphicsLayer {
                        val p = progress.value
                        alpha = p
                        val sc = 0.94f + 0.06f * p
                        scaleX = sc
                        scaleY = sc
                    }
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .glass(RoundedCornerShape(24.dp), strong = true, flat = true)
                    // swallow taps so they do not reach the scrim
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.Start
            ) {
                Text(title, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                content()
            }
        }
    }
}

/** Single-line glass input, height 44, radius 12 (design: "File link"). */
@Composable
internal fun GlassField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    focusRequester: FocusRequester? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val c = Downly.colors
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(shape)
            .background(c.glassFill, shape)
            .border(1.dp, if (isError) c.red else c.glassBorder, shape)
            .padding(start = 14.dp, end = if (trailing != null) 2.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(placeholder, color = c.textSecondary, fontSize = 13.sp, maxLines = 1)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = c.text, fontSize = 13.sp),
                cursorBrush = SolidColor(c.text),
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                modifier = if (focusRequester != null) Modifier.fillMaxWidth().focusRequester(focusRequester) else Modifier.fillMaxWidth()
            )
        }
        if (trailing != null) trailing()
    }
}


/** Checkbox from the design: 22 dp, radius 7. Unchecked = 2 dp gray border, checked = white box with a black tick. */
@Composable
internal fun DownlyCheckbox(checked: Boolean, modifier: Modifier = Modifier) {
    val c = Downly.colors
    val shape = RoundedCornerShape(7.dp)
    Box(
        modifier
            .size(22.dp)
            .clip(shape)
            .background(if (checked) Color.White else Color.Transparent, shape)
            .border(
                width = if (checked) 1.dp else 2.dp,
                color = if (checked) c.whiteBorder else c.paused,
                shape = shape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Icon(DownlyIcons.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(15.dp))
        }
    }
}

/** Pill toast from the design: glass, height 48, 28 white circle with a black tick + 13sp text. */
@Composable
internal fun DownlyToast(text: String, modifier: Modifier = Modifier) {
    val c = Downly.colors
    Row(
        modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(24.dp), strong = true, flat = true)
            .heightIn(min = 48.dp)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Icon(DownlyIcons.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(text, color = c.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 3)
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
    /** Classic cog: toothed outline + centre ring. */
    val Settings by lazy {
        icon(
            "Settings",
            "M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z",
            "M9 12a3 3 0 1 0 6 0a3 3 0 1 0-6 0"
        )
    }
    val WhatsApp by lazy {
        icon(
            "WhatsApp",
            "M7.9 20A9 9 0 1 0 4 16.1L2 22z",
            "M9.2 8.6c-.3.9.1 2.2 1.3 3.5s2.6 2 3.6 1.8l.9-1.1-1.9-1-.8.7c-.8-.4-1.5-1.1-1.9-1.9l.7-.8-1-1.9z"
        )
    }
    val Instagram by lazy {
        icon(
            "Instagram",
            "M7 2h10a5 5 0 0 1 5 5v10a5 5 0 0 1-5 5H7a5 5 0 0 1-5-5V7a5 5 0 0 1 5-5z",
            "M16 11.4A4 4 0 1 1 12.6 8 4 4 0 0 1 16 11.4z",
            "M17.5 6.5h.01"
        )
    }
    val Telegram by lazy { icon("Telegram", "M22 2L11 13", "M22 2l-7 20-4-9-9-4 20-7z") }
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
