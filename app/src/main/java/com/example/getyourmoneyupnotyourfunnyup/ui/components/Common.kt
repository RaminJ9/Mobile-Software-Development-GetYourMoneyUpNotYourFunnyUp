package com.example.getyourmoneyupnotyourfunnyup.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppColors
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppExtraColors
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppShapes
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.avatarColor
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.jakarta

// ---------------------------------------------------------------------------------------------
// Avatars
// ---------------------------------------------------------------------------------------------

/** Rounded-square group tile: same colour on Home, Groups, the pie chart and Activity. */
@Composable
fun GroupAvatar(
    initial: String,
    paletteIndex: Int,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    corner: Dp = 14.dp,
    fontSize: Int = 17,
) {
    Box(
        modifier = modifier
            .size(size)
            .background(avatarColor(paletteIndex), RoundedCornerShape(corner)),
        contentAlignment = Alignment.Center,
    ) {
        Text(initial, style = jakarta(fontSize, FontWeight.ExtraBold), color = AppColors.Ink900)
    }
}

/** Round person avatar. "You" (negative palette index) is always Green 300. */
@Composable
fun PersonAvatar(
    initial: String,
    paletteIndex: Int,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    ringColor: Color? = null,
) {
    val ring = if (ringColor != null) Modifier.border(2.dp, ringColor, CircleShape) else Modifier
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(avatarColor(paletteIndex))
            .then(ring),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initial,
            style = jakarta((size.value * 0.4f).toInt(), FontWeight.ExtraBold),
            color = AppColors.Ink900,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Chips & pills
// ---------------------------------------------------------------------------------------------

enum class ChipTone(val background: Color, val content: Color) {
    Owe(AppColors.Red100, AppColors.Red700),
    Owed(AppColors.Green100, AppColors.Green700),
    Neutral(AppColors.Surface, AppColors.Ink600),
}

/** Small balance chip: "You owe 286,00", "+145,00 to you", "Settled". */
@Composable
fun BalanceChip(text: String, tone: ChipTone, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier
            .background(tone.background, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        style = jakarta(12, FontWeight.Bold),
        color = tone.content,
        maxLines = 1,
    )
}

/** Sync status pill (pending · failed · done) from the style guide. */
@Composable
fun SyncPill(status: SyncStatus, modifier: Modifier = Modifier) {
    val (label, background, content) = when (status) {
        SyncStatus.SYNCED -> Triple("Synced", AppColors.Green100, AppColors.Green700)
        SyncStatus.FAILED -> Triple("Retrying…", AppColors.Red50, AppColors.Red800)
        else -> Triple("Waiting to sync", AppColors.Amber100, AppColors.Amber800)
    }
    Text(
        text = label,
        modifier = modifier
            .background(background, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        style = jakarta(13, FontWeight.Bold),
        color = content,
        maxLines = 1,
    )
}

/** Pill-shaped filter used on the Groups and Activity tabs. */
@Composable
fun FilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int? = null,
    selectedBackground: Color = AppColors.Ink900,
    selectedContent: Color = AppColors.White,
) {
    val background = if (selected) selectedBackground else AppColors.White
    val content = if (selected) selectedContent else AppColors.Ink900
    Row(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(background)
            .border(1.5.dp, if (selected) selectedBackground else AppColors.Line, RoundedCornerShape(20.dp))
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, style = jakarta(14, FontWeight.Bold), color = content, maxLines = 1)
        if (count != null) {
            Text(count.toString(), style = jakarta(12, FontWeight.Bold), color = content.copy(alpha = 0.75f))
        }
    }
}

/** Segmented control: "Recent · Favourites · All", "Activity · Balances · Members". */
@Composable
fun SegmentedTabs(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(AppColors.Surface, RoundedCornerShape(16.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val tabModifier = if (selected) {
                Modifier.shadow(1.dp, RoundedCornerShape(12.dp), clip = false)
                    .background(AppColors.White, RoundedCornerShape(12.dp))
            } else {
                Modifier
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .then(tabModifier)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Tab) { onSelect(index) }
                    .semantics { this.selected = selected },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = jakarta(14, if (selected) FontWeight.Bold else FontWeight.SemiBold),
                    color = if (selected) AppColors.Ink900 else AppColors.Ink600,
                    maxLines = 1,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Banners
// ---------------------------------------------------------------------------------------------

/** "You're offline · showing saved data from 10:42…" */
@Composable
fun OfflineBanner(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(AppColors.Surface, RoundedCornerShape(16.dp))
            .semantics(mergeDescendants = true) { }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(AppIcons.CloudOff, size = 18.dp, strokeWidth = 2f, tint = AppColors.Ink700)
        Text(
            text,
            style = jakarta(13, FontWeight.SemiBold, lineHeight = 18.sp),
            color = AppColors.Ink700,
        )
    }
}

enum class NoticeTone(val background: Color, val content: Color) {
    Warning(AppColors.Amber100, AppColors.Amber800),
    Success(AppColors.Green100, AppColors.Green700),
    Rule(AppColors.Lavender50, AppColors.Lavender800),
}

/** Tinted notice with an icon, e.g. "Items add up to …" or "Items match the receipt total". */
@Composable
fun Notice(
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

// ---------------------------------------------------------------------------------------------
// Inputs
// ---------------------------------------------------------------------------------------------

/** Text field with a bold label above it (forms on Log in, Create group, Edit profile). */
@Composable
fun LabeledTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    labelSuffix: String? = null,
    placeholder: String? = null,
    supportingText: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    singleLine: Boolean = true,
    minLines: Int = 1,
    isWarning: Boolean = false,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label.isNotEmpty()) {
            Row {
                Text(label, style = jakarta(14, FontWeight.Bold), color = AppColors.Ink900)
                if (labelSuffix != null) {
                    Text(" $labelSuffix", style = jakarta(14, FontWeight.Medium), color = AppColors.Ink600)
                }
            }
        }
        AppTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            keyboardType = keyboardType,
            visualTransformation = visualTransformation,
            singleLine = singleLine,
            minLines = minLines,
            isWarning = isWarning,
            trailingIcon = trailingIcon,
            contentDescription = if (label.isEmpty()) placeholder else null,
        )
        if (supportingText != null) {
            Text(supportingText, style = jakarta(13, FontWeight.Medium), color = AppColors.Ink600)
        }
    }
}

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    singleLine: Boolean = true,
    minLines: Int = 1,
    isWarning: Boolean = false,
    shape: Shape = AppShapes.TextField,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    contentDescription: String? = null,
    borderless: Boolean = false,
    containerColor: Color = AppColors.White,
) {
    val border = when {
        borderless -> Color.Transparent
        isWarning -> AppColors.Amber500
        else -> AppColors.InputBorder
    }
    val describedModifier = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else {
        Modifier
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().then(describedModifier),
        textStyle = jakarta(16, FontWeight.Medium).copy(color = AppColors.Ink900),
        placeholder = placeholder?.let { { Text(it, style = jakarta(16, FontWeight.Medium), color = AppColors.Ink400) } },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = visualTransformation,
        shape = shape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = if (borderless) Color.Transparent else AppColors.Green500,
            unfocusedBorderColor = border,
            focusedContainerColor = if (isWarning) AppExtraColors.AmberInputTint else containerColor,
            unfocusedContainerColor = if (isWarning) AppExtraColors.AmberInputTint else containerColor,
            cursorColor = AppColors.Green700,
        ),
    )
}

// ---------------------------------------------------------------------------------------------
// Switch, spinner, misc
// ---------------------------------------------------------------------------------------------

/** 52×32 switch from the style guide (green when on). */
@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier.semantics { this.contentDescription = contentDescription },
        // A non-null thumbContent keeps the knob full-size in both states, as in the design.
        thumbContent = {},
        colors = SwitchDefaults.colors(
            checkedThumbColor = AppColors.White,
            checkedTrackColor = AppColors.Green700,
            checkedBorderColor = AppColors.Green700,
            uncheckedThumbColor = AppColors.White,
            uncheckedTrackColor = AppColors.SwitchOff,
            uncheckedBorderColor = AppColors.SwitchOff,
        ),
    )
}

/** Circular spinner with a tinted track (e.g. "Paying…", "Saved on your phone · waiting to sync"). */
@Composable
fun Spinner(
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    strokeWidth: Dp = 2.dp,
    color: Color = AppColors.Green700,
    trackColor: Color = AppColors.Green100,
) {
    CircularProgressIndicator(
        modifier = modifier.size(size),
        color = color,
        strokeWidth = strokeWidth,
        trackColor = trackColor,
    )
}

/** Card container with the hairline border used for list rows. */
fun Modifier.cardOutline(corner: Dp = 20.dp, color: Color = AppColors.LineSoft): Modifier =
    this.border(1.dp, color, RoundedCornerShape(corner)).clip(RoundedCornerShape(corner))

/** Small "receipt" thumbnail (grey lines on paper) used for receipt rows. */
@Composable
fun ReceiptThumb(modifier: Modifier = Modifier, width: Dp = 40.dp, height: Dp = 52.dp) {
    Column(
        modifier = modifier
            .size(width, height)
            .background(AppColors.ReceiptPaper, RoundedCornerShape(8.dp))
            .border(1.dp, AppColors.Line, RoundedCornerShape(8.dp))
            .padding(horizontal = 7.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(1f, 0.8f, 1f, 0.6f).forEach { fraction ->
            Spacer(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(3.dp)
                    .background(AppColors.SwitchOff, RoundedCornerShape(2.dp)),
            )
        }
    }
}

/** Grey empty-state card ("No favourites yet…"). */
@Composable
fun EmptyCard(
    title: String?,
    text: String,
    modifier: Modifier = Modifier,
    background: Color = AppColors.SurfaceSoft,
    titleColor: Color = AppColors.Ink900,
    textColor: Color = AppColors.Ink600,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(20.dp))
            .padding(horizontal = 20.dp, vertical = if (title != null) 32.dp else 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (title != null) Text(title, style = jakarta(16, FontWeight.ExtraBold), color = titleColor, textAlign = TextAlign.Center)
        Text(text, style = jakarta(14, FontWeight.Medium, lineHeight = 20.sp), color = textColor, textAlign = TextAlign.Center)
    }
}

/** Single-line text that ellipsizes. */
@Composable
fun OneLine(text: String, style: androidx.compose.ui.text.TextStyle, color: Color, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/**
 * A scrollable column that is at least as tall as the viewport, so `Modifier.weight` spacers can
 * push content (e.g. a primary button) to the bottom while the form still scrolls on small screens.
 */
@Composable
fun FillingScrollColumn(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier) {
        val viewport = maxHeight
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .heightIn(min = viewport)
                .padding(contentPadding),
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
            content = content,
        )
    }
}

/** Dashed outline ("Add member", "Add item", invited people). */
fun Modifier.dashedBorder(
    color: Color,
    corner: Dp,
    strokeWidth: Dp = 1.5.dp,
    dash: Dp = 5.dp,
    gap: Dp = 4.dp,
): Modifier = this.drawBehind {
    val inset = strokeWidth.toPx() / 2
    drawRoundRect(
        color = color,
        topLeft = Offset(inset, inset),
        size = Size(size.width - inset * 2, size.height - inset * 2),
        cornerRadius = CornerRadius(corner.toPx() - inset),
        style = Stroke(
            width = strokeWidth.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash.toPx(), gap.toPx())),
        ),
    )
}
