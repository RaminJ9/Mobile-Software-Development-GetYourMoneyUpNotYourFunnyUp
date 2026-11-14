# Person 5: independent offline foundation

## Scope and activation boundary

This stage adds independent scheduling/processing code, connectivity observation,
and honest status presentation. It does not implement Firebase queue processing,
write to any DAO, change the schema/version, introduce Room or another outbox,
change MainActivity/navigation, or implement financial algorithms. No real
processor or authenticated startup hook is registered. The debug sandbox's
payments/retries remain explicitly simulated and are not connected to WorkManager.

The design follows Android 2's StateFlow/lifecycle guidance, Android 4's repository
boundary, Software Architecture Part 1's outbox/worker/feedback diagram (page 9),
and the usability lecture's status/retry mechanisms (page 10). The team's design
document page 5 defines NFR-K1–K3; the responsibility document page 2 explicitly
names Room and WorkManager. Room remains a separate unresolved team deliverable.

## Dependencies and approved manifest scope

The user explicitly approved the standard WorkManager dependencies and necessary
merged components after the interrupted turn. WorkManager 2.12.0 is declared in
the version catalog, with `androidx.work:work-runtime` as implementation and
`androidx.work:work-testing` as androidTestImplementation. It is a stable release
whose minimum SDK is 24, matching this project. CoroutineWorker is available in
work-runtime; no extra KTX, experimental metrics, DI or notification dependency
is added. Actual resolution with the configured AGP/Kotlin still needs a build.

References: [WorkManager releases](https://developer.android.com/jetpack/androidx/releases/work)
and [upstream runtime manifest](https://github.com/androidx/androidx/blob/androidx-main/work/work-runtime/src/main/AndroidManifest.xml).

The implementation uses only bounded background work, never setForeground or
expedited foreground execution. Main AndroidManifest.xml therefore contains
removal markers for the unused FOREGROUND_SERVICE permission and
SystemForegroundService component. The optional DUMP-protected DiagnosticsReceiver
is also removed. These markers remove library entries; they do not add a custom
permission, service, receiver, initializer or Application class. Future foreground
work/diagnostics would require revisiting those explicit removals.

The expected retained library entries are:

| Permission/component | Purpose |
|---|---|
| ACCESS_NETWORK_STATE | Network constraints; already in the earlier merged debug manifest. |
| WAKE_LOCK | WorkManager execution support; already in that manifest. |
| RECEIVE_BOOT_COMPLETED | Rescheduling persisted work after reboot. |
| InitializationProvider + WorkManagerInitializer metadata | Standard library initialization, not registration of our processor. |
| SystemJobService (BIND_JOB_SERVICE protected) | Android JobScheduler execution. |
| ForceStopRunnable$BroadcastReceiver | WorkManager's internal force-stop bookkeeping. |
| RescheduleReceiver | Restores scheduled work after boot. |

INTERNET and Firebase/analytics components already belong to the existing app;
they are not newly implemented synchronization endpoints. No POST_NOTIFICATIONS
permission is introduced. WorkManager's internal scheduling storage and any
transitive persistence libraries do not replace or supplement the financial
database; no application Room database/entities/DAOs are introduced.

The generated merged manifest inspected before the dependency addition is stale
and contains no WorkManager entries. It is not evidence of the final merge.
The final post-dependency manifest must be verified using processDebugMainManifest
or Android Studio's Merged Manifest view after successful resolution. The
task was attempted explicitly but failed resolving the missing WorkManager
dependency in this environment. No final merged-manifest verification is claimed.

## Classes and data flow

`SyncQueueProcessor.processNext(accountId)` is the only new owner-facing
processing interface. It returns one of:

| Result | Meaning |
|---|---|
| `Empty` | No eligible operation remains for this account; not proof that everything synchronized. |
| `Acknowledged(accountId)` | One operation was remotely acknowledged and locally reconciled by its owner. |
| `RetryLater` | A temporary failure; retain the mutation and its original queue row. |
| `NeedsAttention` | A blocked/unsupported operation requires attention; retain it. |

`SyncRunner` calls that interface sequentially under a process-wide coroutine
mutex, shared even between different runner instances. This serializes the
automatic and manual worker lanes in the current single-process app. Each run
has a cooperative 30-second timeout and at most 20 acknowledged operations. The
bound includes time waiting for the mutex. Exhausting a bound returns retry.
Cancellation propagates and releases the lock; an unexpected exception fails
closed as NeedsAttention. The processor must classify transient exceptions as
RetryLater rather than relying on the runner to guess Firebase error semantics.

The account is checked before starting, after obtaining the lock, before each
operation and after its result. A mismatched acknowledgement is rejected. These
checks cannot undo a remote side effect already performed by an incorrect owner
implementation. Authentication/ownership must also be checked inside that
implementation at dispatch and reconciliation, including logout/login during a
suspension. A UID check alone does not distinguish two successive sessions for
the same account.

`SyncWorker` maps Complete to WorkManager success, Retry to retry, and unavailable,
account-changed or needs-attention outcomes to failure with a reason. WorkManager
success is never mapped to a screen's Synced state. The public two-argument
constructor has no processor and therefore fails closed. `SyncWorkerFactory`
accepts the owner processor and a current-account callback, and exposes the same
runner for constructing `SyncScheduler`. Neither factory nor processor is
registered in production during this stage.

`SyncScheduler` offers suspend functions `scheduleAutomatic`, `scheduleManual`
and `recoverAfterRestart`. Automatic/manual requests have separate unique names
per account and both use KEEP. Names contain a SHA-256 account digest; worker
input contains the UID only, never a password/token/financial payload. Manual
retry can request a new opportunity while the automatic lane waits for backoff,
without cancelling an in-flight operation. Both lanes use the shared runner lock.

Requests require NetworkType.CONNECTED and exponential backoff starting at ten
seconds. The suspend scheduler waits for WorkManager's enqueue operation off the
main thread and returns Pending, Unavailable, AccountChanged or Rejected. Pending
means accepted/coalesced scheduling, not a completed upload. A scheduling timeout
can have an uncertain enqueue outcome; repeating the same unique request is safe
at the scheduling level. OS scheduling is inexact and not a delivery deadline.

The restart method is an explicit future lifecycle hook, not an installed startup
listener. There is no periodic watchdog or automatic queue observer in this
independent stage. The owner must address the transaction-to-scheduling crash gap
and the KEEP/worker-finishing race by reconciling persisted pending work at
authenticated startup and after work completion, or agreeing a recovery trigger.

## Ownership contract required before activation

The database/Firebase owner must supply an implementation that:

1. Uses the existing queue and processes only operations belonging to the supplied
   authenticated account. The current schema does not encode ownership explicitly.
2. Saves local mutations and their queue entries atomically, with notification of
   flows after commit, and preserves FIFO/dependency order where necessary.
3. Reuses stable remote IDs/idempotency identifiers. A crash after remote success
   but before local acknowledgement must not create another settlement/expense.
4. Changes status/removes only the acknowledged queue operation and does not clear
   a newer pending edit of the same entity.
5. Defines valid JSON payloads, supported actions, authorization checks, Firestore
   acknowledgement semantics, and pending-local versus remote-refresh rules.
6. Cooperates with coroutine cancellation/timeouts. Cancelling a suspended Firebase
   wait does not necessarily cancel the remote SDK write; reconcile uncertain
   results before replaying. The in-process mutex is not cross-device idempotency.
7. Supplies authoritative status, pending counts, cache timestamp and durable
   recovery information to the screen sources.

Person 1 must later install the WorkerFactory in the application's agreed
WorkManager configuration, recreate those dependencies on every process start,
provide the current authenticated UID, and connect startup/logout hooks. Do not
call WorkManager.initialize a second time after its default initializer. No
Application class or initializer replacement has been introduced here.

Person 4 still owns manual settlement recording, its stable operation reference,
and balances calculated from the locally available ledger. An operation's local
recording is distinct from remote synchronization. All existing integration
requirements in `person5_phase2_phase3.md` continue to apply.

## Screen behavior

`ConnectivityObserver` observes the default network with ConnectivityManager,
including validated internet capability. It emits Unknown/Online/Offline and
unregisters its callback when collection stops. Unknown is neutral, including
when permission/service access is unavailable. Online does not prove Firebase is
reachable and is never used to set Synced.

The Home and Activity routes collect connectivity with lifecycle awareness and
pass it through the existing immutable screen state. Stateless screen tests and
previews can supply a connection value directly. No network operation is started
by observing connectivity.

`SyncPresentation` adds optional source-supplied pending count, saved timestamp,
failure and acknowledged-recovery text. Its defaults produce no status strip or
extra list spacing. Both screens reuse `SyncFeedback`. A missing saved timestamp
produces “You're offline · showing available data.” A known timestamp is displayed
only when actually supplied. The banner does not promise that an unavailable
processor will automatically upload changes.

Pending is amber, failure uses the prototype's retry colours, and a supplied
acknowledged recovery uses green. Zero pending operations does not produce a
synthetic Synced message. Recovery text is a display contract, not a durable event
store: only emit it after a real acknowledgement, and clear it in the source when
appropriate. No recovery notification or cross-restart acknowledgement history
is implemented here.

The failed-state strip defaults to “Couldn't sync · needs attention”; automatic
retry wording requires explicit availability. Inline settlement feedback says
“Settlement recorded · synchronization pending” for queued results. Paid/green
completion requires SYNCED. The existing Activity fake/deferred-result regression
test verifies that queue acceptance remains unresolved until the source emits an
acknowledged result.

## Verification in Android Studio

1. Sync Gradle to resolve the approved WorkManager dependencies.
2. Run `assembleDebug` and `testDebugUnitTest`, or run the JVM test classes with
   their gutter icons. These tests do not require an emulator.
3. Select an emulator and run `SyncWorkTest` and `SyncFeedbackTest` with their
   instrumentation gutter icons. They use fakes/test WorkManager, not Firebase.
   Alternatively run `connectedDebugAndroidTest` to include existing UI/database
   instrumentation regressions. Instrumentation must run separately from JVM tests.
4. Launch the existing specified Activity `debug.Person5TestActivity`, as described
   in `person5_debug_testing.md`. With Show offline disabled, switch airplane mode
   on/off and check the neutral banner on both Home and Activity. This tests real
   connectivity presentation over sample data, not offline financial persistence.
5. Use its Pending/Failure/Success simulation controls for inline feedback, then
   Reset. Do not interpret sandbox completion as a real upload. Instrumentation
   covers supplied pending-count/recovery messages without creating an event store.
6. Open main AndroidManifest.xml and select Merged Manifest for debug, and repeat
   for release. Check that RECEIVE_BOOT_COMPLETED, WorkManagerInitializer,
   SystemJobService and the internal recovery receivers are supplied by the
   library, and that FOREGROUND_SERVICE, SystemForegroundService and
   DiagnosticsReceiver are absent. Use the provenance pane to distinguish the
   existing Firebase/AndroidX entries from WorkManager additions. These checks
   remain pending here because dependency resolution failed.

No production setup step should register a real processor or connect authenticated
startup until the team approves activation. No emulator verification is claimed
for Phase 4B in this session.

## Requirements still incomplete

- NFR-K1: reading previously fetched groups/expenses/balances depends on owner-side
  local data integration and Person 4's offline balance source; not proved here.
- NFR-K2: the independent runner/scheduler is not a transactional write path or an
  implemented Firestore processor. No end-to-end offline-write/replay claim.
- NFR-K3: live presentation is available, but durable recovery feedback,
  notifications/preferences, event/read history and actual acknowledgement data
  require the relevant owners. Restart hooks are not verified restart recovery.
- Room remains deferred and is not satisfied by SQLiteOpenHelper or WorkManager's
  internal scheduling database.

## Executed verification

- Wrapper command attempted after dependency wiring:
  `.\gradlew.bat assembleDebug testDebugUnitTest processDebugMainManifest`.
  It failed before project compilation/tests because Gradle 9.5.0 could not be
  downloaded (`SocketException: Permission denied: getsockopt`). No Gradle APK or
  unit-test pass is claimed for this stage.
- A real Gradle 9.5.0 fallback was run using the installed distribution copied
  into ignored build/person5-verification/, JDK 21, a workspace-local Gradle home
  and the existing dependency cache in read-only mode. The source wrapper,
  configured versions and other users' cache files were not changed. That run
  reached actual Android build tasks and failed resolving work-runtime:2.12.0,
  which is not cached for offline mode. Explicit testDebugUnitTest,
  compileDebugAndroidTestKotlin and processDebugMainManifest requests with
  --continue hit the same prerequisite failure. This is a blocked dependency,
  not a Kotlin source compilation diagnostic. Neither test suite executed.
- A final online attempt with that same cached Gradle 9.5.0 requested
  assembleDebug, testDebugUnitTest, compileDebugAndroidTestKotlin and
  processDebugMainManifest with --continue. It failed fetching the
  work-runtime:2.12.0 POM from dl.google.com with Permission denied: getsockopt.
  This confirms the dependency is blocked by network access, not merely an
  offline-mode setting. The final merged manifest and exact-toolchain compilation
  of the worker/scheduler remain unverified; no Kotlin error was reached for them.
- A supplementary cached Kotlin 2.2.10/Compose compilation succeeded for the
  runner/contracts, connectivity observer, main UI/model/theme sources, debug
  sandbox, JVM tests and Compose instrumentation-test source. WorkManager
  scheduler/worker and SyncWorkTest are excluded: their binary dependencies are
  not cached. Dependency wiring is now approved and present, but exact-toolchain
  WorkManager/instrumentation compilation remains unverified.
- JUnit actually executed 45 JVM tests in that supplementary harness: 28 existing
  Home/Activity tests, 13 SyncRunner tests and four SyncPresentation tests. All
  passed. This uses a different cached compiler/library set from the configured
  project toolchain, and is not a replacement for Gradle verification.
- Nine WorkManager instrumentation tests and three new feedback instrumentation
  tests are provided but were not executed. The existing three Compose tests were
  not executed either. A compile check of Compose-test source is not a device test.
- The cached library set reports createComposeRule deprecation warnings. No
  claim of warning-free exact-toolchain compilation or visual comparison is made.
- Git diff checks of the protected database/repository/domain/navigation files
  are empty. The manifest has only the approved unused-library-entry removals.
  The pre-existing earlier-phase edits remain in place. git diff --check passed.

## Phase 4B file inventory

New main Kotlin files (under the app package):

- `data/sync/ConnectivityObserver.kt`
- `data/sync/SyncQueueProcessor.kt`
- `data/sync/SyncRunner.kt`
- `worker/SyncWorker.kt` (includes the factory)
- `worker/SyncScheduler.kt`
- `ui/dashboard/SyncPresentation.kt`
- `ui/components/SyncFeedback.kt`

Modified main files:

- `ui/dashboard/DashboardModels.kt`
- `ui/dashboard/ActivityModels.kt`
- `ui/components/DashboardComponents.kt`
- `ui/screens/home/HomeScreen.kt`
- `ui/screens/activity/ActivityScreen.kt`
- `app/src/main/AndroidManifest.xml` (library-entry removals only)

Modified build files:

- `gradle/libs.versions.toml`
- `app/build.gradle.kts`

New JVM tests:

- `app/src/test/java/com/example/getyourmoneyupnotyourfunnyup/data/sync/SyncRunnerTest.kt`
- `app/src/test/java/com/example/getyourmoneyupnotyourfunnyup/ui/dashboard/SyncPresentationTest.kt`

New Android instrumentation tests:

- `app/src/androidTest/java/com/example/getyourmoneyupnotyourfunnyup/worker/SyncWorkTest.kt`
- `app/src/androidTest/java/com/example/getyourmoneyupnotyourfunnyup/ui/screens/SyncFeedbackTest.kt`

Documentation: this report, `person5_phase2_phase3.md`, and `ai_dossier_person5.md`.
Existing earlier-phase uncommitted files and the pre-existing Documentation folder
are preserved. Disposable supplementary verification files stay in ignored build/.
