package com.example.getyourmoneyupnotyourfunnyup.ui.screens.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.getyourmoneyupnotyourfunnyup.data.demo.DemoLedger
import com.example.getyourmoneyupnotyourfunnyup.ui.components.AppButton
import com.example.getyourmoneyupnotyourfunnyup.ui.components.AppIcon
import com.example.getyourmoneyupnotyourfunnyup.ui.components.AppIcons
import com.example.getyourmoneyupnotyourfunnyup.ui.components.GroupAvatar
import com.example.getyourmoneyupnotyourfunnyup.ui.components.LabeledTextField
import com.example.getyourmoneyupnotyourfunnyup.ui.components.PersonAvatar
import com.example.getyourmoneyupnotyourfunnyup.ui.components.dashedBorder
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppColors
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.AppTypography
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.GetYourMoneyUpNotYourFunnyUpTheme
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.GroupColorNames
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.avatarColor
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.jakarta
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.CREATE_GROUP_COLOR_COUNT
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.CreateGroupAction
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.CreateGroupLocalState
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.CreateGroupUiState
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.CreateGroupViewModel
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.MemberChipUi
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.appViewModel
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.buildCreateGroupUiState

@Composable
fun CreateGroupRoute(
    onBack: () -> Unit,
    onCreated: (groupId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = appViewModel { CreateGroupViewModel(it.ledgerRepository) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.createdGroupId) { state.createdGroupId?.let(onCreated) }
    CreateGroupScreen(state, viewModel::onAction, onBack, modifier)
}

@Composable
fun CreateGroupScreen(
    state: CreateGroupUiState,
    onAction: (CreateGroupAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(AppColors.White).statusBarsPadding().imePadding()) {
        Row(
            Modifier.padding(start = 12.dp, end = 12.dp, top = 24.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "Back" }) {
                AppIcon(AppIcons.ArrowLeft, size = 22.dp, strokeWidth = 2f, tint = AppColors.Ink900)
            }
            Text("New group", style = jakarta(20, FontWeight.ExtraBold), color = AppColors.Ink900)
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                GroupAvatar(state.previewInitial, state.colorIndex, size = 72.dp, corner = 22.dp, fontSize = 30)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Group colour", style = jakarta(13, FontWeight.Bold), color = AppColors.Ink600)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(CREATE_GROUP_COLOR_COUNT) { index ->
                            val selected = index == state.colorIndex
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .border(1.dp, if (selected) AppColors.Ink900 else AppColors.Line, CircleShape)
                                    .padding(1.dp)
                                    .border(3.dp, if (selected) AppColors.Ink900 else AppColors.White, CircleShape)
                                    .padding(3.dp)
                                    .clip(CircleShape)
                                    .background(avatarColor(index))
                                    .clickable(role = Role.RadioButton) { onAction(CreateGroupAction.PickColor(index)) }
                                    .semantics {
                                        contentDescription = GroupColorNames[index]
                                        this.selected = selected
                                    },
                            )
                        }
                    }
                }
            }

            LabeledTextField(
                label = "Group name",
                value = state.name,
                onValueChange = { onAction(CreateGroupAction.NameChanged(it)) },
                placeholder = "e.g. Roskilde 2027",
            )
            LabeledTextField(
                label = "Description",
                labelSuffix = "(optional)",
                value = state.description,
                onValueChange = { onAction(CreateGroupAction.DescriptionChanged(it)) },
                placeholder = "What's this group for?",
                singleLine = false,
                minLines = 2,
            )

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("Members", Modifier.weight(1f), style = jakarta(16, FontWeight.ExtraBold), color = AppColors.Ink900)
                    Text("${state.members.size} in group", style = AppTypography.Caption, color = AppColors.Ink600)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.members.forEach { chip -> MemberChip(chip, onRemove = { onAction(CreateGroupAction.Remove(chip.id)) }) }
                }
                MemberSearch(
                    query = state.query,
                    onQueryChange = { onAction(CreateGroupAction.QueryChanged(it)) },
                    onSubmit = { onAction(CreateGroupAction.SubmitQuery) },
                    notFoundContact = state.notFoundContact,
                    onInvite = { onAction(CreateGroupAction.Invite) },
                    suggestions = state.suggestions,
                    onAddSuggestion = { onAction(CreateGroupAction.AddSuggested(it)) },
                )
            }
        }

        Column(Modifier.navigationBarsPadding()) {
            Spacer(Modifier.fillMaxWidth().height(1.dp).background(AppColors.LineSoft))
            AppButton(
                text = if (state.canCreate) "Create group" else "Give your group a name",
                onClick = { onAction(CreateGroupAction.Create) },
                enabled = state.canCreate,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun MemberChip(chip: MemberChipUi, onRemove: () -> Unit) {
    val background = when (chip.kind) {
        MemberChipUi.Kind.You -> AppColors.Green50
        MemberChipUi.Kind.Added -> AppColors.SurfaceSoft
        MemberChipUi.Kind.Invited -> AppColors.Amber100
    }
    Row(
        Modifier
            .height(40.dp)
            .background(background, RoundedCornerShape(20.dp))
            .padding(start = 6.dp, end = if (chip.kind == MemberChipUi.Kind.You) 14.dp else 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (chip.kind == MemberChipUi.Kind.Invited) {
            Box(
                Modifier.size(28.dp).background(AppColors.White, CircleShape).dashedBorder(AppColors.Amber500, 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text(chip.avatar.initial, style = jakarta(12, FontWeight.ExtraBold), color = AppColors.Amber800) }
        } else {
            PersonAvatar(chip.avatar.initial, chip.avatar.paletteIndex, size = 28.dp)
        }
        Text(chip.name, style = jakarta(14, FontWeight.Bold), color = AppColors.Ink900)
        if (chip.kind == MemberChipUi.Kind.Invited) {
            Text("invited", style = jakarta(12, FontWeight.SemiBold), color = AppColors.Amber800)
        }
        if (chip.kind != MemberChipUi.Kind.You) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onRemove)
                    .semantics { contentDescription = "Remove ${chip.name}" },
                contentAlignment = Alignment.Center,
            ) { AppIcon(AppIcons.Close, size = 14.dp, strokeWidth = 2.4f, tint = AppColors.Ink600) }
        }
    }
}

@Preview(name = "Create group", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun CreateGroupPreview() {
    GetYourMoneyUpNotYourFunnyUpTheme {
        CreateGroupScreen(buildCreateGroupUiState(DemoLedger.create(System.currentTimeMillis()), CreateGroupLocalState()), {}, {})
    }
}

@Preview(name = "Create group – user not found", showBackground = true, widthDp = 412, heightDp = 915)
@Composable
private fun CreateGroupErrorPreview() {
    val local = CreateGroupLocalState(
        name = "Roskilde 2027", description = "Tickets, camp gear and food", colorIndex = 2,
        addedIds = listOf("mads", "ida"), query = "anna.b@mail.dk", notFound = "anna.b@mail.dk",
    )
    GetYourMoneyUpNotYourFunnyUpTheme {
        CreateGroupScreen(buildCreateGroupUiState(DemoLedger.create(System.currentTimeMillis()), local), {}, {})
    }
}
