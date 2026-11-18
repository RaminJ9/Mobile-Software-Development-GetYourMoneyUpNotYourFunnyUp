# Person 2 · Groups & member management (branch `GroupsMembers`)

Covers S4 & S-filter (Groups tab), S5 & S-Error (Create group, colour palette, invites),
S8c & S8c-add (Members tab, roles, dietary notes, add member) and S-Warn (removal warning).
Follows the Person 5 pattern: `Flow<Data>` source → ViewModel with sealed `UiState` →
`Route` (stateful) + `Screen` (stateless, `onAction`) + light/dark previews.

## Files

| Layer | File |
|---|---|
| Presentation logic | `ui/groups/GroupsModels.kt` (filters, search, summary, contact validation, member subtitle, S-Warn text) |
| ViewModels | `ui/viewmodel/GroupsViewModel.kt`, `CreateGroupViewModel.kt`, `GroupMembersViewModel.kt` |
| Screens | `ui/screens/groups/GroupsScreen.kt`, `CreateGroupScreen.kt`, `GroupMembersScreen.kt`, `GroupsPreviews.kt` |
| Components | `ui/components/GroupComponents.kt` (text field, lookup + S-Error alert, suggestion row, dashed add button, avatar) |
| Data | `data/repository/GroupRepository.kt` (offline-first: SQLite + `sync_queue` in one transaction) |
| Tests | `test/.../ui/groups/GroupsPresentationTest.kt`, `GroupsViewModelsTest.kt`, `androidTest/.../GroupRepositoryTest.kt` |

## Schema change (DB version 2 → 3)

Additive `ALTER TABLE` migration in `AppDatabase.onUpgrade`:
`groups.colour_index`, `groups.is_favourite`, `group_members.invited`, `users.phone`.
Invited people get a placeholder `users` row with id `invite:<normalised email/phone>`.
Favourite updates use `UPDATE` (not `REPLACE`) so group members are never cascade-deleted.

## Integration (shell owner)

```kotlin
val repo = GroupRepository(AppDatabase.getInstance(context), signedInUser)   // per account, drop on logout
GroupsViewModel(repo.observeGroupsList(balancesFlow), repo)
CreateGroupViewModel(signedInUser, repo, repo, repo.observeKnownContacts())
GroupMembersViewModel(repo.observeMembers(groupId, memberBalancesFlow, dietaryFlow), repo, repo, repo)
```

- `GroupsRoute(onCreateGroup, onOpenGroup)`, `CreateGroupRoute(onBack, onCreated(groupId))`.
- Members tab: embed `GroupMembersContent` under the group hub's segmented tabs, or use `GroupMembersRoute` standalone.
- `repo.observeGroups()` also supplies the real `DashboardGroup` (colour, favourite, last activity, pending count) Person 5 asked for in Home.

## App shell (wired in)

In the integrated app (`feature/hifi-frontend`) the shared shell, `ui/navigation/AppRoot.kt`, shows
these screens: the Groups tab, New group, and the Members tab inside the group page (next to its
Activity and Balances tabs). Their data comes from `ui/groups/LedgerGroupsSource.kt`, which
implements the same interfaces as `GroupRepository` over the app's current ledger, so groups stay
consistent with Home, Activity and Scan. Swap it for `GroupRepository` once the ledger lives in SQLite.
`MainShell.kt` moved to the debug source set and is only used by `debug/Person2TestActivity`, which
still runs the screens on `GroupRepository` and an in-memory database:
`adb shell am start -n com.example.getyourmoneyupnotyourfunnyup/.debug.Person2TestActivity`.

## Open contracts

- **Balances (Person 4):** `balancesFlow` = account's net per group, `memberBalancesFlow` = each member's net (øre). Until supplied they are `null`: money filters show no counts and *every* removal asks for confirmation (safe default).
- **Dietary rules (profile owner):** `Map<userId, Set<DietaryRule>>`; defaults to empty.
- **Sync processor (DB owner):** queued `GROUP` entries use `CREATE` (full group payload with `memberIds`/`members`) and `UPDATE` with `op` = `addMember` / `inviteMember` / `removeMember`.
- Favourites are a personal preference kept on the phone (not uploaded).
- `google-services.json` is not in git; locally build with `-x processDebugGoogleServices`.

## AI dossier (Appendix A entry)

| Date | Tool & mode | Task | Verification | Decision |
|---|---|---|---|---|
| 17-11-2026 | Claude Code (agent) | Implement Person 2 screens, ViewModels, repository and tests from HiFi + design document | `gradlew testDebugUnitTest` (26 new tests pass), `assembleDebug` clean; instrumented repository test compiled but not run (no emulator) | Human review required before merge into `main` |
