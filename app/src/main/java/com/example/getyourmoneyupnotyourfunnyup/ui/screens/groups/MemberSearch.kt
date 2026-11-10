package com.example.getyourmoneyupnotyourfunnyup.ui.screens.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.getyourmoneyupnotyourfunnyup.ui.components.AppButton
import com.example.getyourmoneyupnotyourfunnyup.ui.components.AppIcon
import com.example.getyourmoneyupnotyourfunnyup.ui.components.AppIcons
import com.example.getyourmoneyupnotyourfunnyup.ui.components.AppTextField
import com.example.getyourmoneyupnotyourfunnyup.ui.components.ButtonSize
import com.example.getyourmoneyupnotyourfunnyup.ui.components.ButtonTone
import com.example.getyourmoneyupnotyourfunnyup.ui.components.PersonAvatar
import com.example.getyourmoneyupnotyourfunnyup.ui.components.cardOutline
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppColors
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppTypography
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.jakarta
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.PersonSuggestionUi

/**
 * "Email or phone number" + Add, the S-Error "User does not exist" warning with "Send invite", and
 * "People you've split with". Used by Create group and the group's Add member sheet.
 */
@Composable
fun MemberSearch(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    notFoundContact: String?,
    onInvite: () -> Unit,
    suggestions: List<PersonSuggestionUi>,
    onAddSuggestion: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AppTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                placeholder = "Email or phone number",
                keyboardType = KeyboardType.Email,
                isWarning = notFoundContact != null,
                contentDescription = "Email or phone number",
            )
            AppButton(
                text = "Add",
                onClick = onSubmit,
                tone = ButtonTone.Soft,
                size = ButtonSize.Large,
                contentPadding = PaddingValues(horizontal = 18.dp),
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        if (notFoundContact != null) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(AppColors.Amber100, RoundedCornerShape(16.dp))
                    .semantics(mergeDescendants = true) { }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppIcon(AppIcons.AlertCircle, size = 20.dp, strokeWidth = 2f, tint = AppColors.Amber800)
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                append("No one uses the app with $notFoundContact yet.")
                            }
                            append(" Check the spelling, or invite them – they'll join the group when they sign up.")
                        },
                        style = jakarta(14, FontWeight.Medium, lineHeight = 20.sp),
                        color = AppColors.Amber800,
                    )
                }
                AppButton(
                    text = "Send invite", onClick = onInvite, modifier = Modifier.fillMaxWidth(),
                    tone = ButtonTone.WarningOutline, size = ButtonSize.Small,
                )
            }
        }

        if (suggestions.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("People you've split with", style = jakarta(13, FontWeight.Bold), color = AppColors.Ink600)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    suggestions.forEach { person ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .cardOutline(corner = 16.dp)
                                .clickable(role = Role.Button) { onAddSuggestion(person.id) }
                                .semantics(mergeDescendants = true) { contentDescription = "Add ${person.name}" }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            PersonAvatar(person.avatar.initial, person.avatar.paletteIndex, size = 40.dp)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(person.name, style = AppTypography.ItemTitle, color = AppColors.Ink900)
                                Text(person.contact, style = AppTypography.Caption, color = AppColors.Ink600)
                            }
                            Box(
                                Modifier.size(36.dp).background(AppColors.Green100, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                AppIcon(AppIcons.Plus, size = 16.dp, strokeWidth = 2.6f, tint = AppColors.Green700)
                            }
                        }
                    }
                }
            }
        }
    }
}
