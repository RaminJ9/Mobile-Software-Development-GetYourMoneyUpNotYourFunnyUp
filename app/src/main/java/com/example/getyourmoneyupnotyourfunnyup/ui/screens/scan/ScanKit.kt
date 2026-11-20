package com.example.getyourmoneyupnotyourfunnyup.ui.screens.scan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppColors
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppShapes
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.PlusJakartaSansFontFamily

/*
 * The small UI pieces the scan screens are built from: buttons, outline icons, notices, avatars and a
 * text field, styled like `docs/HiFi.html`. They are internal to the scan package on purpose, so they
 * don't add a second set of shared components next to the design system in `ui/theme` and
 * `ui/components`.
 */

/** One-off shades used only by the scan screens. */
internal object ScanColors {
    val StarOutline = Color(0xFFA9B3AD) // Unselected group in the group picker
    val SurfaceFaint = Color(0xFFF7F9F8) // Receipt item rows in "Check items"
    val Scrim = Color(0x6B17201B) // Behind bottom sheets (Ink900 at 42%)
    val AmberInputTint = Color(0xFFFFFBF0) // Text field showing a warning
}

/** Avatar colour for a palette index; negative means the signed-in user, always Green300 ("You"). */
internal fun avatarColor(paletteIndex: Int): Color =
    if (paletteIndex < 0) AppColors.Green300 else AppColors.groupColorAt(paletteIndex)

/** Plus Jakarta Sans for the one-off sizes in the hi-fi; [tracking] is letter spacing in em. */
internal fun jakarta(size: Int, weight: FontWeight, tracking: Float = 0f, lineHeight: TextUnit = TextUnit.Unspecified): TextStyle =
    TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontSize = size.sp,
        fontWeight = weight,
        letterSpacing = if (tracking == 0f) TextUnit.Unspecified else tracking.em,
        lineHeight = lineHeight,
    )

// ---- Buttons ------------------------------------------------------------------------------------

/** Button colour roles from the style guide. */
@Immutable
internal enum class ButtonTone(val container: Color, val content: Color) {
    /** Primary · Save expense */
    Primary(AppColors.Green700, AppColors.White),

    /** Text · Scan another receipt */
    Text(Color.Transparent, AppColors.Green700),
}

/** Height / radius / type pairs from the style guide: primary 56, secondary 48. */
@Immutable
internal enum class ButtonSize(val height: Dp, val radius: Dp, val fontSize: Int) {
    Large(56.dp, 18.dp, 16),
    Medium(48.dp, 16.dp, 15),
}

@Composable
internal fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: ButtonTone = ButtonTone.Primary,
    size: ButtonSize = ButtonSize.Large,
    enabled: Boolean = true,
    leadingIcon: AppIconSpec? = null,
    trailingIcon: AppIconSpec? = null,
) {
    AppButton(onClick = onClick, modifier = modifier, tone = tone, size = size, enabled = enabled) {
        if (leadingIcon != null) AppIcon(leadingIcon, size = 18.dp, strokeWidth = 2.2f)
        Text(text = text, style = jakarta(size.fontSize, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (trailingIcon != null) AppIcon(trailingIcon, size = 18.dp, strokeWidth = 2.2f)
    }
}

@Composable
internal fun AppButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: ButtonTone = ButtonTone.Primary,
    size: ButtonSize = ButtonSize.Large,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = size.height).height(size.height),
        enabled = enabled,
        shape = RoundedCornerShape(size.radius),
        colors = ButtonDefaults.buttonColors(
            containerColor = tone.container,
            contentColor = tone.content,
            disabledContainerColor = AppColors.LineSoft,
            disabledContentColor = AppColors.Ink600,
        ),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

// ---- Icons ------------------------------------------------------------------------------------

/** An outline icon on a 24×24 grid, with the SVG path data from the hi-fi (1.8 stroke, rounded caps). */
@Immutable
internal class AppIconSpec(val paths: List<String>)

private fun circle(cx: Float, cy: Float, r: Float): String =
    "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0z"

private fun roundRect(x: Float, y: Float, w: Float, h: Float, r: Float): String =
    "M${x + r} ${y}h${w - 2 * r}a$r $r 0 0 1 $r ${r}v${h - 2 * r}a$r $r 0 0 1 ${-r} ${r}" +
        "h${-(w - 2 * r)}a$r $r 0 0 1 ${-r} ${-r}v${-(h - 2 * r)}a$r $r 0 0 1 $r ${-r}z"

/** The icons the scan screens use. Path data is copied from `docs/HiFi.html`. */
internal object AppIcons {
    val ChevronDown = AppIconSpec(listOf("m6 9 6 6 6-6"))
    val Close = AppIconSpec(listOf("M18 6 6 18M6 6l12 12"))
    val ArrowRight = AppIconSpec(listOf("M5 12h14M13 6l6 6-6 6"))
    val ArrowLeft = AppIconSpec(listOf("M19 12H5M11 18l-6-6 6-6"))
    val Check = AppIconSpec(listOf("M20 6 9 17l-5-5"))
    val AlertCircle = AppIconSpec(listOf(circle(12f, 12f, 9f), "M12 8v4M12 16h.01"))
    val Trash = AppIconSpec(listOf("M3 6h18M8 6V4h8v2M6 6l1 14h10l1-14"))
    val Camera = AppIconSpec(
        listOf(
            "M14.5 4h-5L7 7H4a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-3z",
            circle(12f, 13f, 3.5f),
        ),
    )
    val Flash = AppIconSpec(listOf("M13 2 4 14h7l-1 8 9-12h-7z"))
    val Image = AppIconSpec(listOf(roundRect(3f, 3f, 18f, 18f, 3f), circle(9f, 9f, 2f), "m21 15-5-5L5 21"))
    val Pencil = AppIconSpec(listOf("M12 20h9", "M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4z"))
    val Warning = AppIconSpec(
        listOf(
            "M12 9v4M12 17h.01",
            "M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z",
        ),
    )
    val Phone = AppIconSpec(listOf(roundRect(5f, 2f, 14f, 20f, 3f), "M11 18h2"))
}

/** Draws an [AppIconSpec]. [contentDescription] is null for decorative icons (buttons carry the label). */
@Composable
internal fun AppIcon(
    icon: AppIconSpec,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    strokeWidth: Float = 1.8f,
    tint: Color = Color.Unspecified,
    contentDescription: String? = null,
) {
    val paths: List<Path> = remember(icon) { icon.paths.map { PathParser().parsePathString(it).toPath() } }
    val described = if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier
    val color = if (tint == Color.Unspecified) LocalContentColor.current else tint
    Canvas(modifier = modifier.then(described).size(size)) {
        scale(this.size.minDimension / 24f, pivot = Offset.Zero) {
            paths.forEach { path ->
                drawPath(path, color = color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

// ---- Notices, avatars, layout -------------------------------------------------------------------

internal enum class NoticeTone(val background: Color, val content: Color) {
    Warning(AppColors.Amber100, AppColors.Amber800),
    Success(AppColors.Green100, AppColors.Green700),
    Rule(AppColors.Lavender50, AppColors.Lavender800),
}

/** A tinted message strip: warnings (amber), confirmations (green) and diet rules (lavender). */
@Composable
internal fun Notice(
    tone: NoticeTone,
    modifier: Modifier = Modifier,
    icon: AppIconSpec? = null,
    leading: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(tone.background, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (icon != null) AppIcon(icon, size = 20.dp, strokeWidth = 2f, tint = tone.content, modifier = Modifier.padding(top = 1.dp))
        leading?.invoke()
        Box(modifier = Modifier.weight(1f)) { content() }
    }
}

/** A person's circle with their initial, in their palette colour ([paletteIndex] < 0 is "You"). */
@Composable
internal fun PaletteAvatar(initial: String, paletteIndex: Int, modifier: Modifier = Modifier, size: Dp = 44.dp, ringColor: Color? = null) {
    val ring = if (ringColor != null) Modifier.border(2.dp, ringColor, CircleShape) else Modifier
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(avatarColor(paletteIndex)).then(ring),
        contentAlignment = Alignment.Center,
    ) {
        Text(initial, style = jakarta((size.value * 0.4f).toInt(), FontWeight.ExtraBold), color = AppColors.Ink900)
    }
}

/** A group's rounded tile with its initial, in the group colour. */
@Composable
internal fun GroupAvatar(initial: String, paletteIndex: Int, modifier: Modifier = Modifier, size: Dp = 44.dp, corner: Dp = 14.dp, fontSize: Int = 17) {
    Box(
        modifier = modifier.size(size).background(avatarColor(paletteIndex), RoundedCornerShape(corner)),
        contentAlignment = Alignment.Center,
    ) {
        Text(initial, style = jakarta(fontSize, FontWeight.ExtraBold), color = AppColors.Ink900)
    }
}

@Composable
internal fun Spinner(
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    strokeWidth: Dp = 2.dp,
    color: Color = AppColors.Green700,
    trackColor: Color = AppColors.Green100,
) {
    CircularProgressIndicator(modifier = modifier.size(size), color = color, strokeWidth = strokeWidth, trackColor = trackColor)
}

@Composable
internal fun OneLine(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** 1 dp card border with rounded corners, clipping the content. */
internal fun Modifier.cardOutline(corner: Dp = 20.dp, color: Color = AppColors.LineSoft): Modifier =
    this.border(1.dp, color, RoundedCornerShape(corner)).clip(RoundedCornerShape(corner))

/** A scrolling column that is at least as tall as the screen, so content can be pinned to the bottom. */
@Composable
internal fun FillingScrollColumn(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier) {
        val viewport = maxHeight
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).heightIn(min = viewport).padding(contentPadding),
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
            content = content,
        )
    }
}

/** Text field with a bold label above it, as in the hi-fi forms. */
@Composable
internal fun LabeledTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    isWarning: Boolean = false,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label.isNotEmpty()) Text(label, style = jakarta(14, FontWeight.Bold), color = AppColors.Ink900)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().then(
                if (label.isEmpty() && placeholder != null) Modifier.semantics { contentDescription = placeholder } else Modifier,
            ),
            textStyle = jakarta(16, FontWeight.Medium).copy(color = AppColors.Ink900),
            placeholder = placeholder?.let { { Text(it, style = jakarta(16, FontWeight.Medium), color = AppColors.Ink400) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            shape = AppShapes.TextField,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AppColors.Green500,
                unfocusedBorderColor = if (isWarning) AppColors.Amber500 else AppColors.InputBorder,
                focusedContainerColor = if (isWarning) ScanColors.AmberInputTint else AppColors.White,
                unfocusedContainerColor = if (isWarning) ScanColors.AmberInputTint else AppColors.White,
                cursorColor = AppColors.Green700,
            ),
        )
    }
}
