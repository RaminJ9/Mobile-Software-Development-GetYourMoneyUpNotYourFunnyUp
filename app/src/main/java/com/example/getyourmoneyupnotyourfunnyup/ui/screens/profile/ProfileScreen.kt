package com.example.getyourmoneyupnotyourfunnyup.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.getyourmoneyupnotyourfunnyup.data.demo.DemoLedger
import com.example.getyourmoneyupnotyourfunnyup.model.DietRule
import com.example.getyourmoneyupnotyourfunnyup.ui.components.AppButton
import com.example.getyourmoneyupnotyourfunnyup.ui.components.AppIcon
import com.example.getyourmoneyupnotyourfunnyup.ui.components.AppIconSpec
import com.example.getyourmoneyupnotyourfunnyup.ui.components.AppIcons
import com.example.getyourmoneyupnotyourfunnyup.ui.components.AppSwitch
import com.example.getyourmoneyupnotyourfunnyup.ui.components.ButtonSize
import com.example.getyourmoneyupnotyourfunnyup.ui.components.ButtonTone
import com.example.getyourmoneyupnotyourfunnyup.ui.components.LabeledTextField
import com.example.getyourmoneyupnotyourfunnyup.ui.components.PersonAvatar
import com.example.getyourmoneyupnotyourfunnyup.ui.components.cardOutline
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppColors
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.GetYourMoneyUpNotYourFunnyUpTheme
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.jakarta
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.NotifyKey
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.ProfileAction
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.ProfileLocalState
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.ProfileUiState
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.ProfileViewModel
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.appViewModel
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.buildProfileUiState

@Composable
fun ProfileRoute(onLogout: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel = appViewModel { ProfileViewModel(it.ledgerRepository, it.authRepository) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProfileScreen(state, viewModel::onAction, onLogout, modifier)
}

@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onAction: (ProfileAction) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(AppColors.White)) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            if (state.editing) EditProfile(state, onAction) else ProfileHeader(state, onAction)

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Never charge me for", style = jakarta(16, FontWeight.ExtraBold), color = AppColors.Ink900)
                    Text(
                        "When someone scans a receipt, these items are taken off your share automatically. They can still add you back.",
                        style = jakarta(14, FontWeight.Medium, lineHeight = 21.sp),
                        color = AppColors.Ink600,
                    )
                }
                SettingsCard {
                    RuleRow(AppIcons.Glass, "Alcohol", "Beer, wine, spirits", state.noAlcohol, last = false) {
                        onAction(ProfileAction.SetDietRule(DietRule.NO_ALCOHOL, it))
                    }
                    RuleRow(AppIcons.Meat, "Meat", "For vegetarians", state.noMeat, last = true) {
                        onAction(ProfileAction.SetDietRule(DietRule.NO_MEAT, it))
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Notify me about", style = jakarta(16, FontWeight.ExtraBold), color = AppColors.Ink900)
                SettingsCard {
                    NotifyKey.entries.forEachIndexed { index, key ->
                        SwitchRow(
                            title = key.label,
                            subtitle = key.description,
                            checked = state.notify[key] == true,
                            last = index == NotifyKey.entries.lastIndex,
                            onChange = { onAction(ProfileAction.SetNotify(key, it)) },
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Account", style = jakarta(16, FontWeight.ExtraBold), color = AppColors.Ink900)
                SettingsCard {
                    LinkRow("Change password", AppIcons.ChevronRight, last = false) { onAction(ProfileAction.ChangePassword) }
                    LinkRow("Log out", AppIcons.Logout, last = true, onClick = onLogout)
                }
            }
        }

        state.toast?.let { toast ->
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp)
                    .shadow(12.dp, RoundedCornerShape(20.dp))
                    .background(AppColors.Ink900, RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppIcon(AppIcons.Check, size = 16.dp, strokeWidth = 2.6f, tint = AppColors.Green300)
                Text(toast, style = jakarta(14, FontWeight.SemiBold), color = AppColors.White)
            }
        }
    }
}

@Composable
private fun ProfileHeader(state: ProfileUiState, onAction: (ProfileAction) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        PersonAvatar(state.initial, -1, size = 72.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(state.name, style = jakarta(22, FontWeight.ExtraBold, -0.02f), color = AppColors.Ink900)
            Text(state.email, style = jakarta(14, FontWeight.Medium), color = AppColors.Ink600)
            state.phone?.let { Text(it, style = jakarta(14, FontWeight.Medium), color = AppColors.Ink600) }
        }
        AppButton(
            "Edit", { onAction(ProfileAction.StartEdit) },
            tone = ButtonTone.Secondary, size = ButtonSize.Small, contentPadding = PaddingValues(horizontal = 16.dp),
        )
    }
}

@Composable
private fun EditProfile(state: ProfileUiState, onAction: (ProfileAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(72.dp)) {
                PersonAvatar(state.draftName.trim().take(1).uppercase().ifEmpty { state.initial }, -1, size = 72.dp)
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .offset(6.dp, 6.dp)
                        .size(36.dp)
                        .background(AppColors.White, CircleShape)
                        .padding(3.dp)
                        .background(AppColors.Green700, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { AppIcon(AppIcons.Camera, size = 16.dp, strokeWidth = 2.2f, tint = AppColors.White) }
            }
            Text("Edit profile", style = jakarta(22, FontWeight.ExtraBold, -0.02f), color = AppColors.Ink900)
        }
        LabeledTextField("Full name", state.draftName, { onAction(ProfileAction.DraftName(it)) })
        LabeledTextField("Email", state.draftEmail, { onAction(ProfileAction.DraftEmail(it)) }, keyboardType = KeyboardType.Email)
        LabeledTextField(
            "Phone number", state.draftPhone, { onAction(ProfileAction.DraftPhone(it)) },
            keyboardType = KeyboardType.Phone, supportingText = "Friends use this to add you to groups.",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppButton("Cancel", { onAction(ProfileAction.CancelEdit) }, Modifier.weight(1f).height(52.dp), tone = ButtonTone.Secondary, size = ButtonSize.Medium)
            AppButton("Save changes", { onAction(ProfileAction.SaveEdit) }, Modifier.weight(1f), size = ButtonSize.Medium, enabled = state.draftName.isNotBlank())
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().cardOutline(), content = content)
}

@Composable
private fun RowDivider(last: Boolean) {
    if (!last) Spacer(Modifier.fillMaxWidth().height(1.dp).background(AppColors.LineSoft))
}

@Composable
private fun RuleRow(icon: AppIconSpec, title: String, subtitle: String, checked: Boolean, last: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(40.dp).background(if (checked) AppColors.Lavender50 else AppColors.Surface, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) { AppIcon(icon, size = 20.dp, tint = if (checked) AppColors.Lavender800 else AppColors.Ink600) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = jakarta(15, FontWeight.Bold), color = AppColors.Ink900)
            Text(subtitle, style = jakarta(13, FontWeight.Medium), color = AppColors.Ink600)
        }
        AppSwitch(checked, onChange, contentDescription = title)
    }
    RowDivider(last)
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, last: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = jakarta(15, FontWeight.Bold), color = AppColors.Ink900)
            Text(subtitle, style = jakarta(13, FontWeight.Medium), color = AppColors.Ink600)
        }
        AppSwitch(checked, onChange, contentDescription = title)
    }
    RowDivider(last)
}

@Composable
private fun LinkRow(title: String, icon: AppIconSpec, last: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), style = jakarta(15, FontWeight.SemiBold), color = AppColors.Ink900)
        AppIcon(icon, size = 18.dp, strokeWidth = 2f, tint = AppColors.Ink600)
    }
    RowDivider(last)
}

@Preview(name = "Profile", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun ProfilePreview() {
    GetYourMoneyUpNotYourFunnyUpTheme {
        ProfileScreen(buildProfileUiState(DemoLedger.create(System.currentTimeMillis()), ProfileLocalState()), {}, {})
    }
}

@Preview(name = "Profile – edit", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun ProfileEditPreview() {
    GetYourMoneyUpNotYourFunnyUpTheme {
        val local = ProfileLocalState(editing = true, draftName = "Victor Munk", draftEmail = "victor@example.dk", draftPhone = "+45 12 34 56 78")
        ProfileScreen(buildProfileUiState(DemoLedger.create(System.currentTimeMillis()), local), {}, {})
    }
}
