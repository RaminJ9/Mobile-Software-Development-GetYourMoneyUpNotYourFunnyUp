package com.example.getyourmoneyupnotyourfunnyup.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.getyourmoneyupnotyourfunnyup.ui.groups.Contact
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.*
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.MemberLookupState

/** Circle avatar for a person. "You" is always [AppColors.Green300]; others use the group palette. */
@Composable
fun PersonAvatar(
    name: String,
    colourIndex: Int,
    modifier: Modifier = Modifier,
    isYou: Boolean = false,
    invited: Boolean = false,
    size: Dp = AppTouchTargets.MinTouchTarget
) {
    val fill = when {
        invited -> AppColors.White
        isYou -> AppColors.Green300
        else -> AppColors.groupColorAt(colourIndex)
    }
    Box(
        modifier
            .size(size)
            .background(fill, AppShapes.Circle)
            .then(if (invited) Modifier.dashedBorder(AppColors.Amber500, size / 2) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            name.trim().take(1).uppercase().ifEmpty { "?" },
            style = if (size >= 40.dp) AppTypography.Section else AppTypography.Label,
            color = AppColors.Ink900
        )
    }
}

fun Modifier.dashedBorder(colour: Color, radius: Dp, width: Dp = 1.5.dp): Modifier = drawBehind {
    drawRoundRect(
        colour,
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(width.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
    )
}

/** HiFi text field: 52dp, 16dp corners, 1.5dp `InputBorder`, green focus ring, amber when flagged. */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    warning: Boolean = false,
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
    leading: (@Composable () -> Unit)? = null,
    filled: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.heightIn(min = AppTouchTargets.TextFieldHeight),
        enabled = enabled,
        textStyle = AppTypography.Body.copy(color = AppColors.Ink900),
        placeholder = { Text(placeholder, style = AppTypography.Body, color = AppColors.Ink600) },
        label = label?.let { { Text(it) } },
        leadingIcon = leading,
        singleLine = singleLine,
        minLines = minLines,
        shape = AppShapes.TextField,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(onAny = { onImeAction() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = if (warning) AppColors.Amber500 else AppColors.Green500,
            unfocusedBorderColor = when {
                warning -> AppColors.Amber500
                filled -> AppColors.Surface
                else -> AppColors.InputBorder
            },
            focusedContainerColor = if (warning) AppColors.Amber50 else if (filled) AppColors.Surface else AppColors.White,
            unfocusedContainerColor = if (warning) AppColors.Amber50 else if (filled) AppColors.Surface else AppColors.White,
            cursorColor = AppColors.Green700,
            focusedTextColor = AppColors.Ink900,
            unfocusedTextColor = AppColors.Ink900
        )
    )
}

@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier, optional: Boolean = false) {
    Text(
        buildAnnotatedString {
            append(text)
            if (optional) withStyle(SpanStyle(fontWeight = FontWeight.Medium, color = AppColors.Ink600)) { append(" (optional)") }
        },
        modifier,
        style = AppTypography.ButtonInline,
        color = AppColors.Ink900
    )
}

/** `Members` / suggestions header style ("People you've split with"). */
@Composable
fun SectionCaption(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.semantics { heading() }, style = AppTypography.CaptionStrong, color = AppColors.Ink600)
}

/**
 * Email/phone field + `Add` button, followed by the lookup feedback (S-Error "No one uses the app
 * with … yet" + `Send invite`). Shared by Create group (S5) and the Members tab (S8c-add).
 */
@Composable
fun MemberLookupField(
    query: String,
    lookup: MemberLookupState,
    onQueryChange: (String) -> Unit,
    onAdd: () -> Unit,
    onInvite: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val notFound = lookup as? MemberLookupState.NotFound
    Column(modifier, verticalArrangement = Arrangement.spacedBy(AppSpacing.Space12)) {
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space8), verticalAlignment = Alignment.CenterVertically) {
            AppTextField(
                query,
                onQueryChange,
                "Email or phone number",
                Modifier.weight(1f).semantics { contentDescription = "Email or phone number" },
                warning = notFound != null || lookup == MemberLookupState.Invalid,
                enabled = enabled,
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done,
                onImeAction = onAdd
            )
            Button(
                onClick = onAdd,
                enabled = enabled && query.isNotBlank() && lookup != MemberLookupState.Searching,
                modifier = Modifier.height(AppTouchTargets.TextFieldHeight),
                shape = AppShapes.TextField,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.Green100,
                    contentColor = AppColors.Green700,
                    disabledContainerColor = AppColors.LineSoft,
                    disabledContentColor = AppColors.Ink600
                ),
                contentPadding = PaddingValues(horizontal = 18.dp)
            ) {
                if (lookup == MemberLookupState.Searching) {
                    CircularProgressIndicator(Modifier.size(16.dp), color = AppColors.Green700, strokeWidth = 2.dp)
                } else Text("Add", style = AppTypography.ButtonSecondary)
            }
        }
        when (lookup) {
            is MemberLookupState.NotFound -> UserNotFoundAlert(lookup.query, onInvite, enabled)
            MemberLookupState.Invalid -> LookupHint("Type a full email (name@mail.dk) or a phone number with at least 8 digits.")
            MemberLookupState.Failed -> LookupHint("Couldn't check that right now. Try again in a moment.")
            is MemberLookupState.AlreadyAdded -> LookupHint(
                if (lookup.name == "You") "That's you – you're already in the group." else "${lookup.name} is already in the group."
            )
            else -> Unit
        }
    }
}

/** S-Error · User does not exist. */
@Composable
fun UserNotFoundAlert(query: String, onInvite: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .background(AppColors.Amber100, AppShapes.SecondaryButton)
            .padding(horizontal = 14.dp, vertical = AppSpacing.Space12)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DashboardSymbol(DashboardIcon.Info, Modifier.size(20.dp), AppColors.Amber800)
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("No one uses the app with ${query.trim()} yet.") }
                    append(" Check the spelling, or invite them – they'll join the group when they sign up.")
                },
                style = AppTypography.BodyStrong.copy(fontWeight = FontWeight.Medium),
                color = AppColors.Amber800
            )
        }
        OutlinedButton(
            onClick = onInvite,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().height(AppTouchTargets.InlineButtonHeight),
            shape = AppShapes.InlineButton,
            border = BorderStroke(1.5.dp, AppColors.Amber800),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Amber800)
        ) { Text("Send invite", style = AppTypography.ButtonInline) }
    }
}

@Composable
private fun LookupHint(text: String) {
    Text(
        text,
        Modifier.fillMaxWidth().background(AppColors.SurfaceSoft, AppShapes.SecondaryButton).padding(AppSpacing.Space12)
            .semantics { liveRegion = LiveRegionMode.Polite },
        style = AppTypography.Caption,
        color = AppColors.Ink700
    )
}

/** One row in "People you've split with" — tapping adds the person. */
@Composable
fun SuggestionRow(contact: Contact, onAdd: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Surface(
        modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Button, onClickLabel = "Add ${contact.displayName}", onClick = onAdd),
        shape = AppShapes.SecondaryButton,
        border = BorderStroke(1.dp, AppColors.LineSoft),
        color = AppColors.White
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = AppSpacing.Space8),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space12)
        ) {
            PersonAvatar(contact.displayName, contact.colourIndex, size = 40.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(contact.displayName, style = AppTypography.ItemTitle, color = AppColors.Ink900, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (contact.contactLine.isNotBlank()) Text(
                    contact.contactLine, style = AppTypography.Caption, color = AppColors.Ink600, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Box(Modifier.size(36.dp).background(AppColors.Green100, AppShapes.Circle), contentAlignment = Alignment.Center) {
                DashboardSymbol(DashboardIcon.UserPlus, Modifier.size(18.dp), AppColors.Green700)
            }
        }
    }
}

/** Dashed `+ Add member` / `+ Add item` button (52dp, Green200 dashes). */
@Composable
fun DashedAddButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(AppTouchTargets.TextFieldHeight)
            .dashedBorder(AppColors.Green200, 18.dp)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space8)) {
            DashboardSymbol(DashboardIcon.Plus, Modifier.size(18.dp), AppColors.Green700)
            Text(text, style = AppTypography.ButtonSecondary, color = AppColors.Green700)
        }
    }
}

/** Neutral back-arrow top bar used by flows pushed on top of the tabs ("New group"). */
@Composable
fun BackTopBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(start = AppSpacing.Space12, end = AppSpacing.Space12, top = AppSpacing.Space24, bottom = AppSpacing.Space8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space8)
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(AppTouchTargets.MinTouchTarget)) {
            DashboardSymbol(DashboardIcon.Back, description = "Back")
        }
        Text(title, Modifier.semantics { heading() }, style = AppTypography.TopBarTitle, color = AppColors.Ink900)
    }
}

/** Colour-picker swatch ring: Ink900 when selected, white gap plus a hairline otherwise (HiFi). */
fun Modifier.swatchRing(selected: Boolean): Modifier =
    border(1.dp, if (selected) AppColors.Ink900 else AppColors.Line, AppShapes.Circle)
        .padding(1.dp)
        .border(3.dp, if (selected) AppColors.Ink900 else AppColors.White, AppShapes.Circle)
