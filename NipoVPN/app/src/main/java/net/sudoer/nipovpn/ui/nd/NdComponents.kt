package net.sudoer.nipo.ui.nd

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import net.sudoer.nipo.ui.theme.NdColors
import net.sudoer.nipo.ui.theme.NdTheme
import net.sudoer.nipo.ui.theme.NothingFonts

/** Flat, ripple-less clickable — the Nothing interaction model (no scale/shadow). */
fun Modifier.ndClick(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
}

// ── ALL-CAPS instrument label (Space Mono) ──────────────────────────
@Composable
fun NdLabel(
    text: String,
    color: Color? = null,
    size: TextUnit = 11.sp,
    modifier: Modifier = Modifier,
) {
    val c = NdTheme.colors
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = TextStyle(
            fontFamily = NothingFonts.Mono,
            fontSize = size,
            letterSpacing = 0.08.em,
            color = color ?: c.secondary,
        ),
    )
}

@Composable
fun NdDivider(color: Color? = null, modifier: Modifier = Modifier) {
    val c = NdTheme.colors
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color ?: c.border)
    )
}

enum class NdDot { NONE, SOLID, PULSE }

// ── Bracketed status text: [ CONNECTED ] ────────────────────────────
@Composable
fun NdStatus(label: String, color: Color? = null, dot: NdDot = NdDot.NONE) {
    val c = NdTheme.colors
    val col = color ?: c.secondary
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (dot != NdDot.NONE) {
            // Square dot (Nothing uses sharp corners). Pulse is approximated as solid on Android.
            Box(Modifier.size(7.dp).background(col))
        }
        Text(
            text = "[ ${label.uppercase()} ]",
            style = TextStyle(fontFamily = NothingFonts.Mono, fontSize = 11.sp, letterSpacing = 0.1.em, color = col),
        )
    }
}

// ── Dot-grid backdrop ───────────────────────────────────────────────
@Composable
fun NdDotGrid(modifier: Modifier = Modifier, subtle: Boolean = false) {
    val c = NdTheme.colors
    val dotColor = if (subtle) c.border else c.borderVisible
    val stepPx = if (subtle) 12f else 16f
    val radius = if (subtle) 0.75f else 1.25f
    val alpha = if (subtle) 0.6f else 0.5f
    Canvas(modifier) {
        val step = stepPx * density
        val r = radius * density
        var y = step / 2
        while (y < size.height) {
            var x = step / 2
            while (x < size.width) {
                drawCircle(color = dotColor, radius = r, center = androidx.compose.ui.geometry.Offset(x, y), alpha = alpha)
                x += step
            }
            y += step
        }
    }
}

// ── Buttons ─────────────────────────────────────────────────────────
enum class NdButtonVariant { PRIMARY, SECONDARY, GHOST, DESTRUCTIVE }

@Composable
fun NdButton(
    text: String,
    onClick: () -> Unit,
    variant: NdButtonVariant = NdButtonVariant.PRIMARY,
    icon: ImageVector? = null,
    full: Boolean = false,
    large: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val c = NdTheme.colors
    var bg = Color.Transparent
    var fg = c.display
    var border: Color? = null
    when (variant) {
        NdButtonVariant.PRIMARY -> { bg = c.display; fg = c.black }
        NdButtonVariant.SECONDARY -> { border = c.borderVisible; fg = c.primary }
        NdButtonVariant.GHOST -> { fg = c.secondary }
        NdButtonVariant.DESTRUCTIVE -> { border = c.accent; fg = c.accent }
    }
    val h = if (large) 52.dp else 44.dp
    val radius = if (variant == NdButtonVariant.GHOST) 0.dp else 999.dp
    val shape = RoundedCornerShape(radius)
    Box(
        modifier
            .then(if (full) Modifier.fillMaxWidth() else Modifier)
            .height(h)
            .clip(shape)
            .background(bg)
            .then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier)
            .ndClick(enabled, onClick)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp))
            Text(
                text = text.uppercase(),
                style = TextStyle(fontFamily = NothingFonts.Mono, fontSize = 13.sp, letterSpacing = 0.06.em, color = fg),
            )
        }
    }
}

@Composable
fun NdIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    color: Color? = null,
    accent: Boolean = false,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier,
) {
    val c = NdTheme.colors
    val fg = color ?: if (accent) c.accent else c.secondary
    Box(
        modifier.size(size).ndClick(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun NdBackButton(icon: ImageVector, onClick: () -> Unit) {
    val c = NdTheme.colors
    Box(
        Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(999.dp))
            .border(1.dp, c.borderVisible, RoundedCornerShape(999.dp))
            .ndClick(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = c.primary, modifier = Modifier.size(20.dp))
    }
}

// ── Underline input ─────────────────────────────────────────────────
@Composable
fun NdInput(
    label: String?,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    mono: Boolean = true,
    multiline: Boolean = false,
    numeric: Boolean = false,
    placeholder: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = NdTheme.colors
    var focused by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        if (label != null) {
            NdLabel(label, color = if (focused) c.primary else c.secondary)
            Spacer(Modifier.height(2.dp))
        }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp)
                    .onFocusChangedCompat { focused = it },
                textStyle = TextStyle(
                    fontFamily = if (mono) NothingFonts.Mono else NothingFonts.Body,
                    fontSize = 15.sp,
                    color = c.primary,
                ),
                singleLine = !multiline,
                cursorBrush = SolidColor(c.primary),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text
                ),
                visualTransformation = VisualTransformation.None,
                decorationBox = { inner ->
                    if (value.isEmpty() && placeholder != null) {
                        Text(
                            placeholder,
                            style = TextStyle(
                                fontFamily = if (mono) NothingFonts.Mono else NothingFonts.Body,
                                fontSize = 15.sp,
                                color = c.disabled,
                            ),
                        )
                    }
                    inner()
                },
            )
            if (trailing != null) trailing()
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(if (focused) c.primary else c.borderVisible)
        )
    }
}

// Small focus helper.
private fun Modifier.onFocusChangedCompat(onChanged: (Boolean) -> Unit): Modifier =
    this.onFocusChanged { onChanged(it.isFocused) }

// ── Toggle ──────────────────────────────────────────────────────────
@Composable
fun NdToggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = NdTheme.colors
    val w = 48.dp
    val hgt = 28.dp
    val thumb = 20.dp
    Box(
        Modifier
            .size(w, hgt)
            .clip(RoundedCornerShape(999.dp))
            .background(if (checked) c.display else Color.Transparent)
            .border(1.dp, if (checked) c.display else c.borderVisible, RoundedCornerShape(999.dp))
            .ndClick { onChange(!checked) },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .padding(start = if (checked) (w - thumb - 4.dp) else 3.dp)
                .size(thumb)
                .clip(RoundedCornerShape(999.dp))
                .background(if (checked) c.black else c.disabled)
        )
    }
}

// ── Segmented control ───────────────────────────────────────────────
data class NdSegOption(val value: String, val label: String)

@Composable
fun NdSegmented(
    options: List<NdSegOption>,
    value: String,
    onChange: (String) -> Unit,
    full: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val c = NdTheme.colors
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier
            .then(if (full) Modifier.fillMaxWidth() else Modifier)
            .height(40.dp)
            .clip(shape)
            .border(1.dp, c.borderVisible, shape),
    ) {
        options.forEachIndexed { i, o ->
            val on = o.value == value
            if (i > 0) Box(Modifier.fillMaxHeight().width(1.dp).background(c.borderVisible))
            Box(
                Modifier
                    .then(if (full) Modifier.weight(1f) else Modifier)
                    .fillMaxHeight()
                    .background(if (on) c.display else Color.Transparent)
                    .ndClick { onChange(o.value) }
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    o.label.uppercase(),
                    style = TextStyle(fontFamily = NothingFonts.Mono, fontSize = 12.sp, letterSpacing = 0.06.em, color = if (on) c.black else c.secondary),
                )
            }
        }
    }
}

// ── Chip / tag ──────────────────────────────────────────────────────
@Composable
fun NdChip(label: String, selected: Boolean, onClick: () -> Unit, technical: Boolean = false) {
    val c = NdTheme.colors
    val shape = RoundedCornerShape(if (technical) 4.dp else 999.dp)
    Box(
        Modifier
            .height(30.dp)
            .clip(shape)
            .border(1.dp, if (selected) c.display else c.borderVisible, shape)
            .ndClick(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label.uppercase(),
            style = TextStyle(fontFamily = NothingFonts.Mono, fontSize = 11.sp, letterSpacing = 0.08.em, color = if (selected) c.display else c.secondary),
        )
    }
}

// ── Segmented progress bar (signature) ──────────────────────────────
@Composable
fun NdSegBar(
    value: Float,
    max: Float,
    segments: Int = 30,
    height: Dp = 10.dp,
    fill: Color? = null,
    label: String? = null,
    unit: String? = null,
    readout: Boolean = true,
    overrideColors: NdColors? = null,
    modifier: Modifier = Modifier,
) {
    val c = overrideColors ?: NdTheme.colors
    val frac = if (max <= 0f) 0f else (value / max).coerceIn(0f, 1f)
    val filledCount = Math.round(frac * segments)
    val over = value > max
    val fillColor = fill ?: if (over) c.accent else c.display
    Column(modifier.fillMaxWidth()) {
        if (label != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                NdLabelOn(c, label)
                if (readout) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(value.toInt().toString(), style = TextStyle(fontFamily = NothingFonts.Mono, fontSize = 14.sp, color = fillColor))
                        if (unit != null) NdLabelOn(c, unit, c.secondary)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (i in 0 until segments) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(height)
                        .background(if (i < filledCount) fillColor else c.emptySeg)
                )
            }
        }
    }
}

// Label variant that accepts an explicit token set (for the inverted hero).
@Composable
fun NdLabelOn(c: NdColors, text: String, color: Color? = null, size: TextUnit = 11.sp) {
    Text(
        text = text.uppercase(),
        style = TextStyle(fontFamily = NothingFonts.Mono, fontSize = size, letterSpacing = 0.08.em, color = color ?: c.secondary),
    )
}

// ── Boxed input (label rides the border — clearly editable) ─────────
@Composable
fun NdBoxInput(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    mono: Boolean = true,
    multiline: Boolean = false,
    numeric: Boolean = false,
    rows: Int = 3,
    placeholder: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = NdTheme.colors
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    val borderColor = if (focused) c.display else c.borderVisible
    Box(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(c.surface)
                .border(1.dp, borderColor, shape)
                .padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 11.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                modifier = Modifier.weight(1f).onFocusChangedCompat { focused = it },
                textStyle = TextStyle(fontFamily = if (mono) NothingFonts.Mono else NothingFonts.Body, fontSize = 15.sp, lineHeight = 20.sp, color = c.primary),
                singleLine = !multiline,
                minLines = if (multiline) rows else 1,
                cursorBrush = SolidColor(c.primary),
                keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text),
                decorationBox = { inner ->
                    if (value.isEmpty() && placeholder != null) {
                        Text(
                            placeholder,
                            style = TextStyle(fontFamily = if (mono) NothingFonts.Mono else NothingFonts.Body, fontSize = 15.sp, lineHeight = 20.sp, color = c.disabled),
                        )
                    }
                    inner()
                },
            )
            if (trailing != null) trailing()
        }
        // label chip straddling the top border, with the page-bg behind it to "cut" the border
        Box(
            Modifier
                .align(Alignment.TopStart)
                .offset(x = 10.dp, y = (-7).dp)
                .background(c.black)
                .padding(horizontal = 5.dp),
        ) {
            Text(
                label.uppercase(),
                style = TextStyle(fontFamily = NothingFonts.Mono, fontSize = 9.5.sp, letterSpacing = 0.1.em, color = if (focused) c.display else c.secondary),
            )
        }
    }
}

// ── Compact toggle box (for grouping switches side-by-side) ─────────
@Composable
fun NdToggleBox(label: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = NdTheme.colors
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier
            .clip(shape)
            .background(if (checked) c.surfaceRaised else c.surface)
            .border(1.dp, if (checked) c.display else c.borderVisible, shape)
            .ndClick { onChange(!checked) }
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text(label.uppercase(), style = TextStyle(fontFamily = NothingFonts.Mono, fontSize = 11.sp, letterSpacing = 0.08.em, color = if (checked) c.display else c.secondary))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(7.dp).background(if (checked) c.success else c.disabled))
            Text(if (checked) "ON" else "OFF", style = TextStyle(fontFamily = NothingFonts.Mono, fontSize = 10.sp, letterSpacing = 0.08.em, color = if (checked) c.success else c.disabled))
        }
    }
}

// ── Bottom navigation ───────────────────────────────────────────────
data class NdNavItem(val id: String, val icon: ImageVector, val label: String)

@Composable
fun NdBottomNav(items: List<NdNavItem>, active: String, onChange: (String) -> Unit) {
    val c = NdTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.black)
            .navigationBarsPadding()
            .height(64.dp)
    ) {
        items.forEach { it ->
            val on = it.id == active
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .ndClick { onChange(it.id) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(it.icon, null, tint = if (on) c.display else c.disabled, modifier = Modifier.size(20.dp))
                Spacer(Modifier.height(6.dp))
                Text(
                    if (on) "[ ${it.label.uppercase()} ]" else it.label.uppercase(),
                    style = TextStyle(fontFamily = NothingFonts.Mono, fontSize = 10.sp, letterSpacing = 0.08.em, color = if (on) c.display else c.disabled),
                )
            }
        }
    }
}
