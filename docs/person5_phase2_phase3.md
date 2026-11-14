# Person 5 — Home and Activity integration

## Approved boundary

This change implements independent native Compose screens for S3, S3-pay, S3-slice,
S6 and S6-action. It does not attach them to MainActivity or implement the shared
bottom navigation. SQLiteOpenHelper, the schema, entities, DAOs, repositories and
Firebase behavior are unchanged. Room remains deferred. The independent Phase 4B
connectivity/scheduling work and its activation limitations are described in
`person5_phase4b.md`; it does not provide a real queue processor.

Independent presentation/read-observation hardening and its remaining settlement
correlation limits are recorded in `person5_phase5.md`.

## Entry points

- `HomeRoute(viewModel, onOpenActivity, onOpenGroups, onCreateGroup, onJoinGroup,
  onOpenGroup, modifier)` collects state with lifecycle awareness.
- `ActivityRoute(viewModel, modifier)` collects the feed state.
- `HomeScreen` and `ActivityScreen` are stateless entry points for previews and
  UI tests. They receive immutable state and an action callback.

Person 1 should create each ViewModel with a normal ViewModel factory at the
authenticated destination's scope, then pass it into the route. Do not construct
a new ViewModel on every recomposition. The host shell owns system/window insets
and the 80dp navigation bar; pass the shell's padded modifier to each route.
The screen previews use a 412 × 835dp content area, leaving the navigation bar to
its owner. Destroy the account-scoped ViewModels on logout/account switch.

## Data and operation contracts

`HomeViewModel` accepts a `Flow<HomeData>` and optional `SettlementActions`.
`ActivityViewModel` accepts a `Flow<ActivityData>`, optional `SettlementActions`
and optional `SyncRetryActions`. There are no default sample sources or default
successful operations. The existing repositories can be connected by their owners
without changing the screen APIs.

| Owner | Supplies |
|---|---|
| Person 1 | Current user, account scope and navigation callbacks |
| Person 2 | Existing Group objects plus chosen colour index, favourite flag and last activity |
| Person 4 | Group-keyed MemberBalance values and OutstandingPayment objects wrapping DebtTransfer; the manual settlement operation |
| Database/sync owner | Group/ledger flows, pending counts, actual saved-data timestamp, event history and sync retry operations |

An `OutstandingPayment.id` identifies a particular outstanding obligation, not
merely a group. Reuse the same identity between Home and reminder events. The
settlement implementation must revalidate the obligation and record it
idempotently, including concurrent calls from another screen/device. An error
must mean no successful local recording; uncertain outcomes need reconciliation
before permitting a duplicate operation. This guarantee belongs in Person 4's
service, not in the screen's button state.

`SettlementActions.recordSettlement` returns the recorded settlement's real
SyncStatus. It records a manual settlement; it does not transfer money. A return
of PENDING_INSERT or FAILED can mean a locally recorded settlement still awaiting
synchronization, which is displayed separately from Paid. The owner must emit
updated financial snapshots after recording. Screen-level duplicate taps are
guarded; they do not replace database idempotency.

`SyncRetryActions.retry` receives the supplied event so its owner can resolve the
real queued operation. Return SYNCED only after remote acknowledgement. Returning
a pending status displays waiting feedback and keeps the event in Needs you.
Throwing or returning FAILED displays a failed attempt. Later source emissions
update its real synchronization state. No WorkManager job or backend event is
created by these screens.

Home rejects incomplete/inconsistent DKK summaries instead of presenting them as
All paid up. FinancialSummary must include a balance for every supplied group;
the current user's outgoing transfers must match each negative group balance.
This checks a supplied result; it does not calculate balances or settlements.
An absent paid-out source is unavailable, while an explicitly empty source means
zero recorded payments. The donut counts completed DKK settlements paid by the
current user, within the selected calendar period. It does not count expenses or
mix currencies. Weeks start Monday; date boundaries use the device timezone.

Feed events must be supplied by an actual history/reminder source. The five event
types correspond to the prototype; the screen does not infer historical changes
from current ledger rows. `ActivityData.events = null` means history is unavailable;
an empty list means the source has supplied an empty history. Event IDs must be
unique and stable. `nowMillis` is injectable for tests; the source should refresh
it when the screen resumes or the calendar day changes.

Unread changes are held only in the ActivityViewModel's session. Mark all as read
applies across filters, and does not resolve unpaid reminders or failed syncs.
There is no persistent or cross-device mark-read claim. Home's unread indicator
must be supplied by the eventual shared feed/read-state source.

The flows should handle recoverable refresh failures and continue emitting local
data. If a flow throws after emitting, the screen retains its last snapshot and
reports the refresh failure; that closed flow requires its owner to reconnect.
This is in-memory presentation resilience, not database-backed offline support.
Phase 5B adds explicit failed-read re-observation on resume/reconnection; the
source must be safe to collect again. It never replays settlement operations.

## Implemented interactions

- Home Recent/Favourites/All filtering, six-row limit, conditional See all link,
  no-group and no-favourites states, date/greeting and bell callback.
- Supplied balance summary, period menu, native annular chart with hit testing,
  selected-slice emphasis, outside/back dismissal and Open group callback.
- Individual and bulk inline confirmation, pending/success/failure states,
  partial bulk failure and a five-second all-clear drain/collapse.
- Global feed sections and the four documented filters. Needs you counts
  unresolved actionable reminders and failed-sync events independently of unread.
- Inline manual settlement and retry controls whose outcomes follow the injected
  operation results. Missing services disable controls and explain availability.
- Offline banner and pending/failed/synced feedback components render supplied
  state. No connectivity monitoring or automatic background retry is claimed.

## Design checks and limits

The HTML templates and scripts were checked against the native implementation:
16dp side padding, semantic colours, the existing typography scale, 24dp hero
cards, 18dp payment/feed cards, group colours, Danish amounts, and inline actions.
The added unread, retry-border and empty-illustration tokens match the prototype.
Controls retain at least 44dp interaction areas. Chart segments also expose named
accessibility actions, including tiny segments that are difficult to tap.

The donut centre retains two decimals per the explicit style-guide requirement,
whereas the HTML's `fmtShort` rounds it. Loading, missing-integration and operation
failure states explain unavailable functionality rather than displaying fixtures.
The standalone screens retain the reference's light semantic surfaces even in
dark-mode previews. No alternative dark design was invented.

No Android screenshots were rendered or compared. Pixel-perfect verification is
not claimed. Popover positioning, font loading, narrow screens, large font scales
and the final shell/inset integration require device/emulator review. The slice
popover includes the reference information, triangular pointer and dismissal behavior.

## Verification

The Gradle wrapper's `assembleDebug` and `test` commands were attempted but stopped
before compilation: Gradle 9.5.0 needs downloading and the session denies network
sockets. No wrapper build or wrapper test success is claimed.

Supplementary verification uses locally cached Kotlin 2.2.10, its Compose compiler,
cached AndroidX libraries and the installed Android SDK. It compiles the new main
sources, existing domain/theme sources and unit tests, then runs JUnit. A temporary
resource-ID stub supplies font IDs solely to this source check. This does not
validate Android resource packaging, manifest merging, the configured Kotlin
2.4.21 toolchain, or exact declared dependency resolution. The disposable harness
and logs are under ignored `build/person5-verification/`.

All 28 unit tests passed in that supplementary harness. The unit tests cover calendar/DST boundaries, chart filtering and hit testing,
group/feed filters, confirmations, duplicate taps, partial bulk failures,
unavailable services, actual versus queued retry outcomes, account changes,
changed obligations and preservation of the last supplied data after refresh
failure. Compose instrumentation tests are included for callbacks, inline
confirmation and the five-second collapse, but have not been executed.

## Phase 4 and remaining integration

Room coordination, durable read/history state, WorkManager scheduling/backoff,
transactional outbox writes, Firestore normalization/conflict handling, recovery
notifications and actual offline availability must be implemented and verified
with the database owner after separate approval. The existing database is not
changed or supplemented here. Do not display “group notified” without an actual
notification/reminder delivery result.

## File inventory

Modified:

- `app/build.gradle.kts`
- `gradle/libs.versions.toml`
- `app/src/main/java/com/example/getyourmoneyupnotyourfunnyup/ui/theme/Color.kt`

Created under `app/src/main/java/com/example/getyourmoneyupnotyourfunnyup/`:

- `ui/dashboard/DashboardModels.kt`
- `ui/dashboard/DashboardPresentation.kt`
- `ui/dashboard/ActivityModels.kt`
- `ui/components/DashboardComponents.kt`
- `ui/viewmodel/HomeViewModel.kt`
- `ui/viewmodel/ActivityViewModel.kt`
- `ui/screens/home/HomeScreen.kt`
- `ui/screens/home/PaidOutChart.kt`
- `ui/screens/home/HomePreviews.kt`
- `ui/screens/activity/ActivityScreen.kt`
- `ui/screens/activity/ActivityPreviews.kt`

Created under `app/src/test/java/com/example/getyourmoneyupnotyourfunnyup/`:

- `ui/dashboard/HomePresentationTest.kt`
- `ui/dashboard/HomeViewModelTest.kt`
- `ui/dashboard/ActivityPresentationTest.kt`
- `ui/dashboard/ActivityViewModelTest.kt`

Other new files:

- `app/src/androidTest/java/com/example/getyourmoneyupnotyourfunnyup/ui/screens/DashboardScreenTest.kt`
- `docs/person5_phase2_phase3.md`
- `docs/ai_dossier_person5.md`

Generated verification script, dependency extracts, compiler arguments, font-ID
stub, classes and logs are confined to ignored `build/person5-verification/`.
The pre-existing untracked `Documentation/` files were not modified.
