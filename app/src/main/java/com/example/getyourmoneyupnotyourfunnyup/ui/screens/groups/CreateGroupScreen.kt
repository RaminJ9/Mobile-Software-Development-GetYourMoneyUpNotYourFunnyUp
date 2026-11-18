package com.example.getyourmoneyupnotyourfunnyup.ui.screens.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus
import com.example.getyourmoneyupnotyourfunnyup.ui.components.*
import com.example.getyourmoneyupnotyourfunnyup.ui.groups.Contact
import com.example.getyourmoneyupnotyourfunnyup.ui.groups.GroupColourOption
import com.example.getyourmoneyupnotyourfunnyup.ui.groups.MemberDraft
import com.example.getyourmoneyupnotyourfunnyup.ui.theme.*
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.CreateGroupSubmission
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.CreateGroupUiState
import com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel.CreateGroupViewModel

sealed interface CreateGroupAction {
    data class Name(val value: String) : CreateGroupAction
    data class Description(val value: String) : CreateGroupAction
    data class Colour(val colour: GroupColourOption) : CreateGroupAction
    data class Query(val value: String) : CreateGroupAction
    data object LookUp : CreateGroupAction
    data object Invite : CreateGroupAction
    data class AddSuggestion(val contact: Contact) : CreateGroupAction
    data class Remove(val key: String) : CreateGroupAction
    data object Create : CreateGroupAction
}

/** S5 & S-Error · `New group`. [onCreated] receives the new group's id once it's saved on the phone. */
@Composable
fun CreateGroupRoute(
    viewModel: CreateGroupViewModel,
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val saved = state.submission as? CreateGroupSubmission.Saved
    LaunchedEffect(saved) { saved?.let { onCreated(it.group.groupId) } }
    CreateGroupScreen(state, { action ->
        when (action) {
            is CreateGroupAction.Name -> viewModel.setName(action.value)
            is CreateGroupAction.Description -> viewModel.setDescription(action.value)
            is CreateGroupAction.Colour -> viewModel.selectColour(action.colour)
            is CreateGroupAction.Query -> viewModel.setQuery(action.value)
            CreateGroupAction.LookUp -> viewModel.lookUp()
            CreateGroupAction.Invite -> viewModel.sendInvite()
            is CreateGroupAction.AddSuggestion -> viewModel.addSuggestion(action.contact)
            is CreateGroupAction.Remove -> viewModel.removeMember(action.key)
            CreateGroupAction.Create -> viewModel.create()
        }
    }, onBack, modifier)
}

@Composable
fun CreateGroupScreen(
    state: CreateGroupUiState,
    onAction: (CreateGroupAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val locked = state.submission is CreateGroupSubmission.Saving || state.submission is CreateGroupSubmission.Saved
    CompositionLocalProvider(LocalContentColor provides AppColors.Ink900) {
        Column(modifier.fillMaxSize().background(AppColors.White).imePadding()) {
            BackTopBar("New group", onBack)
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState())
                    .padding(start = AppSpacing.Space16, end = AppSpacing.Space16, top = AppSpacing.Space8, bottom = AppSpacing.Space24),
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                ColourPicker(state, onAction, locked)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FieldLabel("Group name")
                    AppTextField(
                        state.name, { onAction(CreateGroupAction.Name(it)) }, "e.g. Roskilde 2027",
                        Modifier.fillMaxWidth().semantics { contentDescription = "Group name" }, enabled = !locked
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FieldLabel("Description", optional = true)
                    AppTextField(
                        state.description, { onAction(CreateGroupAction.Description(it)) }, "What's this group for?",
                        Modifier.fillMaxWidth().semantics { contentDescription = "Description (optional)" },
                        singleLine = false, minLines = 2, enabled = !locked
                    )
                }
                MembersSection(state, onAction, locked)
            }
            BottomBar(state, onAction)
        }
    }
}

@Composable
private fun ColourPicker(state: CreateGroupUiState, onAction: (CreateGroupAction) -> Unit, locked: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            Modifier.size(72.dp).background(AppColors.groupColorAt(state.colour.colourIndex), AppShapes.AvatarTile)
                .semantics { contentDescription = "Group tile preview, ${state.colour.label}" },
            contentAlignment = Alignment.Center
        ) { Text(state.previewInitial, style = AppTypography.Display, color = AppColors.Ink900) }
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.Space8)) {
            SectionCaption("Group colour")
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space4)) {
                GroupColourOption.entries.forEach { option ->
                    val on = option == state.colour
                    Box(
                        Modifier.size(AppTouchTargets.MinTouchTarget)
                            .semantics {
                                contentDescription = option.label
                                selected = on
                                role = Role.RadioButton
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            onClick = { onAction(CreateGroupAction.Colour(option)) },
                            enabled = !locked,
                            modifier = Modifier.size(36.dp).swatchRing(on),
                            shape = AppShapes.Circle,
                            color = AppColors.groupColorAt(option.colourIndex)
                        ) {}
                    }
                }
            }
        }
    }
}

@Composable
private fun MembersSection(state: CreateGroupUiState, onAction: (CreateGroupAction) -> Unit, locked: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.Space12)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Members", Modifier.weight(1f), style = AppTypography.Subsection)
            Text("${state.memberCount} in group", style = AppTypography.Caption, color = AppColors.Ink600)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space8), verticalArrangement = Arrangement.spacedBy(AppSpacing.Space8)) {
            MemberChip("You", 0, isYou = true, invited = false, onRemove = null)
            state.members.forEach { member ->
                val colour = (member as? MemberDraft.Existing)?.contact?.colourIndex ?: 0
                MemberChip(
                    member.displayName, colour, isYou = false, invited = member is MemberDraft.Invited,
                    onRemove = if (locked) null else ({ onAction(CreateGroupAction.Remove(member.key)) })
                )
            }
        }
        MemberLookupField(
            state.query, state.lookup,
            { onAction(CreateGroupAction.Query(it)) },
            { onAction(CreateGroupAction.LookUp) },
            { onAction(CreateGroupAction.Invite) },
            enabled = !locked
        )
        if (state.suggestions.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.Space8)) {
                SectionCaption("People you've split with")
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.suggestions.take(6).forEach { contact ->
                        SuggestionRow(contact, { onAction(CreateGroupAction.AddSuggestion(contact)) }, enabled = !locked)
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberChip(name: String, colourIndex: Int, isYou: Boolean, invited: Boolean, onRemove: (() -> Unit)?) {
    Row(
        Modifier.heightIn(min = 40.dp)
            .background(
                when {
                    isYou -> AppColors.Green50
                    invited -> AppColors.Amber100
                    else -> AppColors.SurfaceSoft
                },
                AppShapes.Pill
            )
            .padding(start = 6.dp, end = if (onRemove == null) 14.dp else 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.Space8)
    ) {
        PersonAvatar(name, colourIndex, isYou = isYou, invited = invited, size = 28.dp)
        Text(name, style = AppTypography.ButtonInline, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 160.dp))
        if (invited) Text("invited", style = AppTypography.Label, color = AppColors.Amber800)
        if (onRemove != null) IconButton(onClick = onRemove, modifier = Modifier.size(AppTouchTargets.MinTouchTarget)) {
            DashboardSymbol(
                DashboardIcon.Close, Modifier.size(16.dp), AppColors.Ink600,
                if (invited) "Remove invite for $name" else "Remove $name"
            )
        }
    }
}

@Composable
private fun BottomBar(state: CreateGroupUiState, onAction: (CreateGroupAction) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(color = AppColors.LineSoft)
        Column(
            Modifier.padding(start = AppSpacing.Space16, end = AppSpacing.Space16, top = AppSpacing.Space12, bottom = AppSpacing.Space24),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.Space8)
        ) {
            when (val submission = state.submission) {
                is CreateGroupSubmission.Failed -> Text(submission.message, style = AppTypography.Caption, color = AppColors.Amber800)
                is CreateGroupSubmission.Saved -> if (submission.group.syncStatus != SyncStatus.SYNCED) {
                    SyncStatusStrip(submission.group.syncStatus, Modifier.fillMaxWidth())
                }
                else -> Unit
            }
            val saving = state.submission is CreateGroupSubmission.Saving
            Button(
                onClick = { onAction(CreateGroupAction.Create) },
                enabled = state.canCreate,
                modifier = Modifier.fillMaxWidth().height(AppTouchTargets.PrimaryButtonHeight),
                shape = AppShapes.BigButton,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.Green700,
                    contentColor = AppColors.White,
                    disabledContainerColor = AppColors.LineSoft,
                    disabledContentColor = AppColors.Ink600
                )
            ) {
                if (saving) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = AppColors.Ink600, strokeWidth = 2.dp)
                    Spacer(Modifier.width(AppSpacing.Space8))
                    Text("Saving…", style = AppTypography.ButtonPrimary)
                } else Text(
                    when {
                        state.submission is CreateGroupSubmission.Saved -> "Group created"
                        state.name.isBlank() -> "Give your group a name"
                        else -> "Create group"
                    },
                    style = AppTypography.ButtonPrimary
                )
            }
        }
    }
}
