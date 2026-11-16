# Person 5: Phase 5B hardening and Phase 5C final review

## Scope

This stage hardens Home/Activity presentation and their existing read observation.
SQLiteOpenHelper, DAOs, entities, schema/version, Firebase repositories, domain
models, MainActivity/navigation and WorkManager activation remain unchanged.
No new dependency, permission, database, operation ID or acknowledgement contract
is introduced. The debug sandbox remains explicitly simulated.

Phase 5A inspected the existing generated reports: 49 JVM and 17 instrumentation
tests passed, with no failures/errors/skips. The user also verified the build and
sandbox interactions in Android Studio. Those baseline results are distinct from
the Phase 5B execution results recorded below.

## Changes

### Empty Activity filter

The Needs-you empty state retains its layout/title but now says “No actions in
this filter.” It does not claim that all debts are paid or all operations synced.
A filtered event list cannot prove either condition. Supplied pending/failure
strips remain visible above it. This deliberately qualifies the prototype's
all-clear wording to satisfy NFR-K3 without inventing global completeness data.

### Group balances and confirmation

Home group rows use `HomeUiState.Success.balanceForGroup`. When the financial
snapshot is incomplete or its balances/transfers disagree, the rows show the
existing neutral Balance unavailable chip, matching the hero's consistency guard.
For a consistent snapshot, the existing local-recording presentation remains:
confirmed recorded payments are removed from the displayed outstanding debt.
This is presentation of supplied results, not a balance/settlement algorithm.

Individual and bulk confirmation handlers recheck snapshot consistency before
calling the injected settlement operation. A previously opened confirmation
cannot bypass that check after an inconsistent refresh.

### Retry reconciliation

For an existing SyncFailure event, its supplied SyncStatus is enough to render
pending, failed and acknowledged states. Initial pending events no longer appear
as idle failures. Later source emissions can move pending → failed, failed →
pending and pending → synced without another UI action.

A later persisted FAILED snapshot can replace local queue-accepted feedback with
needs-attention feedback, even if its status value was already FAILED before
scheduling. This does not identify a new retry attempt: it surfaces the latest
available persisted failure and keeps Try now available. No attempt IDs, retry
revisions or remote errors are fabricated.

An explicit source failure/acknowledgement received while a scheduling request
is suspended wins over that request's older result. Result application also
checks the account, event identity/type and supplied payment. Queued retry cards
retain Try now, so a missing further source emission cannot permanently remove
the manual action. Existing duplicate-tap protection remains in the ViewModel;
remote idempotency is still the owner's responsibility.

### Settlement reconciliation boundary

The existing `SettlementActions` returns a SyncStatus but no persisted settlement
reference. OutstandingPayment.id identifies an obligation, not a newly recorded
settlement. There is no agreed link to the settlement IDs in paid-out history.
Likewise, a reminder event's own sync metadata does not prove that its payment
was synchronized. Such metadata changes no longer discard a confirmed local
recording and reopen the payment action.

Therefore Home and reminder-payment feedback do not infer acknowledgement from
a zero balance, an empty queue, a similar payment amount/person, a changed generic
recovery message or a reminder's status. Pending settlement feedback remains
pending until a reliable correlated result is available. A returned FAILED sync
status still represents the operation's confirmed local recording and must not
enable recording the same payment again.

Person 4/database ownership is required to agree a stable settlement reference
and authoritative correlated status updates. Background pending → synced/failed
reconciliation for those particular settlement cards remains deferred. This stage
implements the transitions only where the current event contract supplies the
necessary information, rather than inventing a replacement contract.

### Time and recoverable reads

`PresentationClock` is a small cancellable cold Flow. It emits the current time
on subscription and at minute boundaries, including midnight/month/year changes.
Home/Activity routes collect it with lifecycle awareness and project that time
into their displayed snapshots. Resume obtains current time immediately. Cache
timestamps, ledger timestamps and financial data are not modified. Stateless
screens/previews retain injectable times for deterministic verification.

Each ViewModel exposes `retryObservation`. It restarts only a failed read-source
collection, never an active observation or a settlement/retry operation. Last
delivered data, read state and compatible action state are retained; sourceError
clears only after another data emission. Routes invoke it on resume and when
connectivity becomes Online. There is no continuous retry loop or backend writer.
Repeated failures remain visible and can be retried on the next lifecycle/network
event, or explicitly through the same method.

These source flows must be safe to collect again and represent reads. Their owner
must not hide mutations in a read subscription. Account-scoped ViewModels and
production lifecycle wiring remain Person 1's integration responsibility.

## Requirement and design review

The relevant checks are NFR-K3/FE12 in the design document, inline S3-pay/S6-action
behavior in the navigation model, and the Home/Activity templates/scripts in HiFi.
The state flow and lifecycle approach follows Android 2's ViewModel/StateFlow
material, Android 3's shared-state guidance and Android 4's repository boundary.
The architecture/usability lectures distinguish observed persisted status from
worker scheduling and feedback. No new architecture pattern is introduced.

The component hierarchy, semantic colours, spacing, filters, chart interaction,
inline confirmation and five-second all-clear interaction are retained. Changes
are limited to truthful text/state handling and the existing retry control on a
queued card. No pixel-perfect screenshot comparison is claimed in this stage.

NFR-K1–K3 are still not claimed end-to-end. This stage supplies no real financial
data source, transactional outbox writes, queue processor, persistent feed/read
state, durable recovery history or background notification delivery. Room remains
a separately unresolved team requirement; no Room implementation is introduced.

## Files

Under `app/src/main/java/com/example/getyourmoneyupnotyourfunnyup/`:

- New: `ui/dashboard/PresentationClock.kt`.
- Modified: `ui/viewmodel/HomeViewModel.kt`, `ui/viewmodel/ActivityViewModel.kt`.
- Modified: `ui/screens/home/HomeScreen.kt`, `ui/screens/activity/ActivityScreen.kt`.
- Modified: `ui/components/SyncFeedback.kt` (lifecycle-aware presentation clock collection).

Tests:

- New: `app/src/test/java/com/example/getyourmoneyupnotyourfunnyup/ui/dashboard/PresentationClockTest.kt`.
- Modified: `HomeViewModelTest.kt` and `ActivityViewModelTest.kt` in that directory.
- Modified: `app/src/androidTest/java/com/example/getyourmoneyupnotyourfunnyup/ui/screens/SyncFeedbackTest.kt`.

Documentation: this report, `person5_phase2_phase3.md`, and `ai_dossier_person5.md`.
Earlier-phase uncommitted work is preserved. Generated verification artifacts
remain in ignored build directories.

## Verification

All three requested tasks executed successfully with the project's Gradle wrapper
and configured toolchain:

| Task/result | Executed result |
|---|---|
| assembleDebug | Passed; debug APK assembled. |
| testDebugUnitTest | 65 JVM tests passed; zero failures/errors/skips. |
| connectedDebugAndroidTest | 21 instrumentation tests passed on emulator-5554 (Android 16); zero failures/errors/skips. |

This includes all original 49 JVM and 17 instrumentation cases, plus 16 JVM and
four instrumentation regressions. The final combined invocation reported
BUILD SUCCESSFUL in 3m 11s. The instrumentation test APK was compiled and the
tests actually ran; this is not merely a source compilation check. An emulator
console warning preceded the run, but all 21 cases completed successfully.

The sandbox invocation used the existing JDK 21 and cached Gradle 9.5.0, with a
short workspace-local Gradle home (build/g), existing dependencies read-only,
offline mode, no configuration cache/daemon reuse, in-process Kotlin compilation
and --no-problems-report. These are command-line/environment settings, not changes
to the project's Gradle configuration. The initial long cache path exceeded a
Windows path limit. Initial daemon-marker/problem-report permissions failures
were resolved by these local settings. No Kotlin source error remained.

The final task arguments were:

```powershell
.\gradlew.bat --gradle-user-home "$PWD/build/g" --offline --no-daemon --no-configuration-cache --no-problems-report '-Dorg.gradle.jvmargs=' '-Pkotlin.compiler.execution.strategy=in-process' assembleDebug testDebugUnitTest connectedDebugAndroidTest --continue
```

Final log: ignored `build/person5-verification/phase5-verification-final.log`.
JUnit XML is under `app/build/test-results/testDebugUnitTest/`; connected results
are under `app/build/outputs/androidTest-results/connected/debug/`. An earlier
supplementary cached-compiler check passed 59 Person 5 JVM tests before the final
two retry-status cases were added; the full Gradle result above supersedes it.
git diff --check passed. Protected database/repository/domain/navigation files
have no Git diff, and this stage does not change the manifest, build dependencies,
worker or scheduler. No manual screenshot comparison was performed.

New regressions cover inconsistent balances and confirmation, queued/failed local
settlements without invented acknowledgements, retry state transitions and stale
results, re-observation preserving read/action state without replaying writes,
and current time on resume/midnight/month changes. New instrumentation cases cover
the neutral empty filter, an inconsistent group chip, a still-actionable queued
retry, and preventing duplicate recording after failed settlement sync.

## Android Studio verification and remaining owners

Run assembleDebug and testDebugUnitTest, then connectedDebugAndroidTest with the
emulator connected. The standard commands need no project configuration changes.
In the existing debug sandbox, check filters, single/bulk confirm/cancel, queued
and failed status, manual retry, and airplane-mode banners. The fixtures do not
simulate a persisted failure/recovery stream; the new automated source-transition
tests exercise that distinction. Navigating away/resuming refreshes presentation
time and permits a failed read source to reconnect. No real synchronization should
be registered or activated for these checks.

Person 1 still owns shell integration and account scope. Person 4/database owners
still owe stable settlement acknowledgement correlation, real financial flows,
safe idempotent queue processing and durable history/feedback. Those shared
contributions and production activation require separate approval.

## Phase 5C: review and integration readiness

This stage changed documentation only. Kotlin, manifests, build scripts and tests
were reviewed without modification. Hashes of 69 implementation/configuration
files were captured before verification and checked afterwards. The separate
`person5_team_handoff.md` is the concise owner-by-owner integration guide.

### Review outcome

The independent implementation is ready for controlled team integration, with the
following qualifications. It is not a complete production app, a completed
Reliability QA, or a signed/release-validated deliverable.

- UI/data boundaries are small: immutable presentation snapshots, ViewModels,
  callbacks and three injected operation interfaces. No duplicate database,
  repository stack or synchronization queue was introduced.
- Home/Activity use native Compose, semantic theme tokens, inline confirmations,
  documented filters/date sections and a native interactive chart. Shared payment,
  balance and status components avoid substantial duplication. Similar read-source
  and lifecycle blocks in two ViewModels/routes are acceptable at this scale;
  introducing a generic base ViewModel would add unnecessary abstraction.
- Some Home/Popover indentation and long argument lists could be formatted more
  consistently. This is a low-priority readability item, not an architectural
  rewrite. No implementation formatting was changed during this review.
- Offline feedback is truthful when supplied metadata is absent. Scheduling and
  worker completion never manufacture a server acknowledgement. The no-processor
  worker fails closed and production never registers the owner factory/scheduler.
- MainActivity remains the existing auth/counter implementation. No Person 4
  calculator/settlement implementation or real financial queue processor exists
  in this checkout. Independent tests do not establish those integrations.

### Issues/decisions reported without code changes

1. Release compilation emits two warnings at SyncWorker.kt:36: the createWorker
   override names parameters context/parameters whereas WorkerFactory names them
   appContext/workerParameters. Runtime tests pass, but the warning-free DoD is
   not satisfied. A future approved parameter-name cleanup is sufficient.
2. Activity's DKK feed amount column omits the kr suffix. Inspection of HiFi's
   actual fmt/template shows it also omits the suffix (its Pay button includes
   it). This is a written currency-rule versus authoritative prototype conflict,
   not an implementation mismatch. Obtain a team decision before changing the
   design. The review initially described it as a mismatch, then corrected that
   assessment after examining the embedded JavaScript/template.
3. Preview fixtures are logically confined to private @Preview helpers, but their
   bytecode remains packaged in release because those helpers are in main and
   optimization is disabled. No production caller, service injection or DAO/
   Firebase write path uses them. If the team requires a completely fixture-free
   release binary, approve moving preview sources to debug in a later change.
4. Pending Home/reminder settlements still lack authoritative background-result
   correlation. Persistent feed/read state, durable recovery notification and
   end-to-end offline behavior remain owner integration work, not hidden stubs.

Existing documented design qualifications remain: honest unavailable/offline
wording, neutral Needs-you empty wording, two-decimal donut totals, 44dp controls,
and reference light surfaces in dark previews. No formal screenshot-diff or
pixel-perfect comparison was performed. Narrow screens, large fonts, TalkBack and
final shell/insets still deserve integration-stage visual/accessibility review.

### Sample isolation and production safety

Source searches found financial fixtures only in preview files, tests and
src/debug. Debug settlement/retry lambdas change in-memory UI state only and
remain labelled simulated. They import no database/repository/Firebase writer.
Production ViewModels have no default sample source or successful fake operation.

The built release APK was opened as a ZIP and its DEX class-definition tables were
inspected, rather than relying solely on source-set names. It contains zero
classes in this app's debug package; Person5TestActivity, Person5SamplesKt and
SampleSession are absent. The debug APK contains 37 debug-package classes as
expected. Release does contain private HomePreviewsKt/ActivityPreviewsKt code,
with the qualification above. No claim that all preview literals disappear from
the APK is made.

No real SyncQueueProcessor implementation or production scheduling/factory call
was found. The normal WorkManager initializer is present, but it does not register
a financial queue processor or schedule this app's custom worker. Existing
Firebase authentication/counter behavior is unrelated and unchanged.

### Release manifest and package verification

The generated release manifests were inspected in merged_manifest and
merged_manifests. Release is not debuggable/testOnly and does not register the
debug sandbox Activity. MainActivity remains the launcher. WorkManager retains
ACCESS_NETWORK_STATE, WAKE_LOCK, RECEIVE_BOOT_COMPLETED, initializer metadata,
the BIND_JOB_SERVICE-protected job service and internal recovery receivers.
FOREGROUND_SERVICE, SystemForegroundService and DiagnosticsReceiver are absent.
Existing Firebase/Google components and permissions remain; they were not added
as new Person 5 financial functionality.

A normal assembleRelease attempt is blocked by the uncached
com.android.tools.lint:lint-gradle:32.3.3 dependency required by lintVitalAnalyzeRelease.
lintDebug/lintRelease are blocked by the same tool, not by reported application
lint findings. No lint pass is claimed.

To inspect an actual release artifact, a second command explicitly excluded only
lintVitalAnalyzeRelease, lintVitalReportRelease and lintVitalRelease at the CLI.
It assembled app-release-unsigned.apk (14,606,656 bytes) successfully. This is a
qualified packaging check, not a normal lint-validated release pass. No build
configuration was weakened, no signing/publishing was performed, and the release
APK was not manually installed/launched.

### Executed Phase 5C checks

| Check | Actual result |
|---|---|
| assembleDebug | Passed/up-to-date in the final review invocation. |
| testDebugUnitTest | Explicitly re-executed with --rerun: 65 passed, no failures/errors/skips. |
| connectedDebugAndroidTest | Executed on emulator-5554 (Android 16): 21 passed, no failures/errors/skips. |
| Normal assembleRelease | Blocked by unavailable lint-gradle:32.3.3. Release Kotlin compilation completed with the two warnings above. |
| Release packaging with CLI lint-vital exclusions | Passed; unsigned APK inspected. |
| lintDebug/lintRelease | Blocked dependency; no lint result. |
| Debug/release source-set and manifest boundaries | Inspected; debug Activity/classes excluded from release. |
| git diff --check | Passed. |

The same workspace-local wrapper cache/JDK 21/read-only dependency cache settings
as Phase 5B were used. No configured Gradle/Kotlin/AGP version changed. Logs are
in ignored build/person5-verification/phase5c-verification.log and
phase5c-release-packaging.log; the DEX inspection summary is phase5c-apk-inspection.json.
The initial combined command reports BUILD FAILED because lint dependencies were
unavailable, despite successful debug build/emulator execution. The qualified
packaging plus fresh unit-test command reports BUILD SUCCESSFUL.

### Remaining sign-off gates

Integrate through the existing routes/contracts with Persons 1, 2, 4 and the
database/Firebase owner as described in the handoff. Keep actual processing off
until an account-scoped, cancellation-aware and idempotent processor and its
transactional data path pass real offline/restart/uncertain-acknowledgement tests.
Room is explicitly named in the responsibility document and remains unresolved;
the existing SQLiteOpenHelper and WorkManager internal storage do not satisfy
that application-level deliverable. NFR-K1, K2 and K3 remain incomplete end-to-end.

Resolve the compiler warnings, currency-rule decision, full lint/release checks
and human review before final project sign-off. No implementation fix, shared
architecture change or production activation was made during this review.
