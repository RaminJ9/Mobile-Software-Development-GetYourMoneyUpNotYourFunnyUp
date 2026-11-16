# Person 5 integration handoff

The independent Home/Activity UI and inactive offline foundation are ready for
controlled team integration. They are not a completed production financial data
path or an activated synchronization system. Keep preview/debug sources out of
production wiring. No new dependency or architecture layer is needed to attach
the existing entry points.

## Person 1: shell and account scope

- Connect `HomeRoute` and `ActivityRoute` to the existing intended five-tab shell;
  the current MainActivity still routes authentication to Counter. Person 5 did
  not replace it.
- Construct `HomeViewModel(Flow<HomeData>, SettlementActions?)` and
  `ActivityViewModel(Flow<ActivityData>, SettlementActions?, SyncRetryActions?)`
  through ordinary factories at the authenticated account scope. Pass the
  instances to the routes; destroy them on logout/account switch.
- Supply Home's callbacks: `onOpenActivity`, `onOpenGroups`, `onCreateGroup`,
  `onJoinGroup`, and `onOpenGroup(groupId)`. Bell opens Activity; see-all opens
  Groups; a group row/donut callout opens that group. Confirmation stays inline.
- The shell owns system insets and the bottom navigation area. Pass its padded
  modifier to each route. The routes already observe lifecycle, current time and
  connectivity. Data-source flows must be safe read subscriptions on reconnect.
- Future WorkManager factory/configuration and authenticated startup/logout hooks
  require a separately approved shared initialization change. Do not initialize
  WorkManager twice or register a fake processor.

## Person 2: group presentation metadata

Supply `DashboardGroup` values wrapping the existing Group plus:

- Stable group ID and actual member list.
- Chosen `colourIndex`, consistently used for group tile, chart and feed dot.
  Use the existing non-green/non-red palette.
- Actual `isFavourite`, `lastActivityAt` and persisted pending-expense count.

No group colour/favourite schema or persistence is introduced by Person 5.
Coordinate their source with the database owner rather than inferring metadata
from sample group names or assigning fictitious stored values.

## Person 4: financial results and manual recording

- Supply `FinancialSummary`: balances keyed by every listed group, using the
  current user's existing MemberBalance, and OutstandingPayment values wrapping
  existing DebtTransfer. Current summary/chart support DKK without currency
  mixing. Outgoing transfers must match each negative group balance.
- An `OutstandingPayment.id` identifies one stable obligation. Use it consistently
  across Home and reminders; do not reuse it for a changed obligation.
- Implement `SettlementActions.recordSettlement(payment): SyncStatus`. Revalidate
  account, ownership, amount and current obligation inside the operation and make
  recording idempotent across screens/devices. It records a manual settlement;
  no money is transferred by these screens.
- Return actual local-recording/sync status and emit updated financial snapshots.
  PENDING/FAILED sync can still mean the local settlement was recorded; do not
  throw as though nothing was saved and enable duplicate recording. Reconcile
  uncertain outcomes before allowing another recording.
- Supply `PaidOutPayment(Settlement, creditorName)` history for the donut. The
  chart uses completed DKK manual settlements paid by the current user in the
  selected period, distinct from their separate cloud sync status.
- Agree with the database owner on a stable persisted settlement reference and
  correlated status updates. The current status-only return cannot correlate a
  later background acknowledgement to an obligation. Do not infer the link from
  equal amounts, zero balances, reminder metadata or an empty queue.

## Database/Firebase owner: local sources, activity and processor

- Supply account-scoped local `Flow<HomeData>` and `Flow<ActivityData>` (or inputs
  combined into those snapshots). UI reads must remain available from local data;
  do not replace them with direct remote-only screen reads.
- `finances = null` / `paidOutPayments = null` means unavailable. An explicit empty
  value means a known empty result. `events = null` means history unavailable.
- Supply actual event history, stable event IDs, unread state and shared bell/read
  information. Mark-all-read is currently session-only. Do not reconstruct edits
  or reminders as though they were recorded history from current ledger rows.
- Populate optional `SyncPresentation` only with real counts, cache timestamps,
  failure information and acknowledged recovery feedback. Its defaults are
  neutral. Connectivity/WorkManager success is not a server acknowledgement.
  Prefer `sync.savedAt` for cache freshness; the legacy Home `offlineSavedAt`
  explicitly signals offline mode and must be null while online.
- Implement `SyncRetryActions.retry(event): SyncStatus` with an account-verified
  mapping to the real operation. Return a pending status for accepted scheduling;
  SYNCED requires acknowledgement. Emit subsequent statuses on the same supplied
  SyncFailure event. Never match a queue target by title/amount/group name alone.
- Implement `SyncQueueProcessor.processNext(accountId): SyncStep` over the existing
  queue. Results are Empty, Acknowledged(accountId), RetryLater or NeedsAttention.
  Own atomic local/outbox writes, operation ownership, dependency ordering,
  payload validation, remote idempotency, acknowledgement reconciliation and
  retaining blocked writes. Do not clear a newer edit when an older one finishes.
- Check authentication/session and ownership inside the processor at dispatch and
  commit. The runner's UID checks/mutex are additional guards, not authorization
  or cross-device idempotency. Cancellation may not cancel an SDK write already
  dispatched; uncertain acknowledgements need reconciliation.
- Once approved, install `SyncWorkerFactory(processor, currentAccount)` before
  WorkManager initialization and build `SyncScheduler` with its exposed runner.
  Recreate dependencies after process restart. Connect automatic scheduling after
  transaction commit and authenticated recovery; handle the enqueue/crash and
  worker-finishing races. The hooks are not connected today.

## Before enabling production writes/uploads

Keep optional services absent/disabled until their contracts are fulfilled. Agree
on Room versus the existing SQLiteOpenHelper with its owner: the responsibility
document explicitly names Room, and WorkManager's internal database does not
satisfy that application deliverable.

Verify real offline reads/writes, restart/reboot recovery, account switching,
newer edits during upload, duplicate prevention after uncertain acknowledgement,
and durable recovery feedback/notification preferences. NFR-K1–K3 remain
incomplete until that data path is implemented and tested. Do not activate the
processor merely because independent tests pass.

Review findings and final build/release qualifications are in
`person5_phase5.md` (Phase 5C section). Human review, clean lint and unresolved
warning/design decisions remain part of the final project sign-off.
