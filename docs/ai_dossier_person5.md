# AI dossier — Person 5

Editable record for incorporation into the design document's Appendix A. The
supplied design PDF has no editable Appendix A. Human review and CI are pending.

## Significant interaction

- Tool/mode: Codex coding agent, repository inspection, PyMuPDF via Windows `py`,
  native Kotlin/Compose editing and local compiler/JUnit verification.
- Task: Phase 1 review, then approved Phases 2–3: Home, donut, inline actions and
  Activity. The user explicitly deferred Room/WorkManager and prohibited changing
  SQLite/Firebase or creating the navigation shell.
- Prompt: The master Person 5 task and the subsequent “Approval to Proceed –
  Phases 2 and 3” message in this conversation.
- Design sources: all 11 supplied PDFs (222 pages including the duplicate overview),
  AGENTS.md, both bundled Hi-Fi files, navigation PNG/XML and database schemas.
- Implementation decision: reuse existing domain/theme models through injected
  Flow snapshots and two operation contracts. Fixtures are private preview data
  or test data. No production data, persistence or remote behavior was fabricated.
- Verification: source review against HTML/CSS/JavaScript, unit tests and cached
  Kotlin/Compose compilation. Gradle assembleDebug/test could not download the
  wrapper distribution because network sockets are denied. Android screenshot and
  instrumentation verification remain outstanding. See person5_phase2_phase3.md.
- Human decision: user approved screens only and explicitly rejected database
  migration for this stage. No commits, branches, merges or pushes were performed.

## Failure and correction log

1. The first Home presentation implementation could show All paid up when a
   negative supplied balance lacked matching transfer instructions. Review caught
   this before completion. Summary completeness checks and a regression test now
   make that state unavailable instead.
2. Initial action-state handling could apply an older successful operation result
   to an obligation whose amount changed under the same ID. Review identified the
   risk. Source reconciliation and result guards now compare the supplied payment,
   with regression tests for both screens and account switching.
3. The supplementary compiler harness initially selected cached version directories
   that contained metadata but no binary artifacts, producing unresolved AndroidX
   references. Its artifact selection was corrected. This was a verification-tool
   failure, not evidence that the project's configured build passed or failed.

The team must independently review the code, run the actual configured build and
tests, verify screenshots and explain the integration contracts before merging.

## Temporary debug testing entry point

- User request: open Home/Activity interactively with sample data, preserve release
  behavior and all shared architecture, and stop before Phase 4.
- Decision: separate specified-Activity entry under `src/debug`, using the existing
  routes/ViewModels with in-memory fixtures and explicitly labelled simulated
  settlement/retry results. No main-source or build configuration change.
- Verification: attempted `assembleDebug testDebugUnitTest`; wrapper download was
  blocked by denied network sockets. See `person5_debug_testing.md` for the launch
  procedure, checks and remaining emulator verification.
- Human verification supplied before this request: Android Studio project build,
  launch, Compose previews and `testDebugUnitTest` passed for Phases 2–3.

## Phase 4A review and Phase 4B independent foundation

- Tool/mode: Codex, agent-assisted repository review/editing, Windows Python with
  PyMuPDF for lecture/document review, and official Android/Firebase references.
- Prompts: Phase 4A architecture review, Phase 4B independent-only approval, and
  the request to resume the interrupted implementation without discarding work.
- Decision: reuse the shared queue through one future processor contract; no DAO,
  schema, entity, repository, Firebase, financial algorithm or navigation changes.
  Add a bounded serialized runner, inactive Worker/Factory/scheduler, lifecycle
  connectivity collection and source-supplied honest feedback. No real processor,
  automatic upload, notification or durable recovery claim.
- Approval boundary: the user explicitly approved stable WorkManager runtime/test
  dependencies and necessary library manifest entries after the interruption.
  Added version-catalog dependencies and removal markers for unused foreground
  service/permission and diagnostics receiver. No executable component was added
  manually, and no real processor or authenticated lifecycle hook was activated.
- Verification: `assembleDebug testDebugUnitTest` was attempted on resumption and
  failed before compilation because the Gradle 9.5.0 download was denied by this
  session's network policy. Supplementary cached Kotlin/Compose compilation and
  JUnit ran 45 tests successfully: 28 earlier screen tests, 13 runner tests and
  four presentation tests. WorkManager sources are excluded from that cached
  check because its binaries are unavailable. Android instrumentation tests are
  separate and have not been executed. No Phase 4B emulator testing is claimed.
- Review corrections: pending settlement feedback previously inherited a Paid/
  green completion treatment; it now distinguishes recording from cloud sync.
  A parent coroutine timeout is tested to ensure it is not swallowed as a retry.
  The scheduler now waits for its enqueue operation off the main thread instead
  of assuming asynchronous acceptance completed.
- Human decision: the user explicitly approved only independent implementation;
  real synchronization and shared integration still require later approval.

## Final WorkManager dependency wiring and verification

- Dependency: stable WorkManager 2.12.0, matching minSdk 24, via work-runtime and
  work-testing. Exact AGP/Kotlin resolution remains a build verification item.
- Actual wrapper build/test/manifest request was blocked downloading Gradle.
  Investigation found that Java's sandbox home differs from the Windows user's
  cache location. A cached Gradle 9.5.0 fallback was therefore run with JDK 21,
  a copied distribution and writable cache confined to the workspace, and the
  existing dependencies read-only. It reached Android tasks, then failed because
  work-runtime:2.12.0 was not cached offline. No project toolchain version was
  changed to make the build pass.
- A second explicit test/instrumentation-compilation/manifest task attempt with
  --continue was blocked by the same unresolved runtime dependency. No actual
  Gradle unit or instrumentation test pass, WorkManager source compilation or
  final merged-manifest success is claimed.
- Re-ran the supplementary cached source/JUnit check: 45 JVM tests passed.
  The earlier generated merged manifest was inspected and identified as stale;
  its contents are not presented as the post-dependency manifest. The Phase 4B
  report distinguishes expected library entries from verified generated output.
- Final online Gradle attempt (same installed version, JDK 21, workspace-local
  cache) also requested assembleDebug, testDebugUnitTest,
  compileDebugAndroidTestKotlin and processDebugMainManifest with --continue.
  Fetching the work-runtime 2.12.0 POM from Google's Maven repository failed with
  Permission denied: getsockopt. Consequently final manifest generation and
  exact-toolchain WorkManager source compilation are still blocked. The approved
  dependency wiring is complete; build/device verification is not claimed.

## Phase 5A review and approved Phase 5B hardening

- Prompt/task: read-only final integration review, then approved independent
  presentation/observation hardening with no SQLite, Firebase, domain, navigation,
  dependency or production synchronization changes.
- Tool/mode: Codex agent-assisted Kotlin/Compose editing, repository/document
  review, Windows Python/PyMuPDF and actual Gradle/JUnit/emulator instrumentation.
- Findings/decision: correct the unsupported all-synced claim for an empty filter
  and inconsistent group chip amounts; reconcile existing sync-event statuses
  without inventing settlement IDs; preserve confirmed local settlement state;
  refresh presentation time and permit failed read observation to restart.
- Boundary: Home/reminder background settlement acknowledgement still needs
  Person 4/database-owner correlation. Tests explicitly reject inferring it from
  zero balances, empty pending counts or generic reminder synchronization metadata.
  No real queue processor, authenticated shell or upload activation was added.
- Verification: baseline reports contained 49 JVM and 17 instrumentation passes.
  Actual final wrapper assembleDebug, testDebugUnitTest and
  connectedDebugAndroidTest completed BUILD SUCCESSFUL, with 65 JVM and 21
  instrumentation tests, zero failures/errors/skips. The original cases remain
  included. See person5_phase5.md for exact invocation and generated result paths.
- Environment: a long workspace wrapper-cache path and sandbox daemon/report
  permissions initially blocked a clean Gradle invocation. A shorter workspace
  cache, in-process compiler and disabled problems report resolved those issues
  without changing project configuration or another user's cache. An emulator
  console warning did not prevent instrumentation execution.
- AI failure correction: earlier generated presentation code could understate an
  inconsistent group debt and claim global synchronization from an empty filter.
  Phase 5A source review identified both; new JVM/UI regressions now verify the
  corrected behavior. No pixel-perfect visual verification is claimed.
- Decision: stop after independent Phase 5B; human review and teammate-owned
  production integration remain required. No commit/push/history operation.

## Phase 5C: final read-only implementation review and handoff

- Prompt/task: review Person 5 source, design/requirements, sample isolation,
  production inactivity, release manifest/APK and actual verification; write a
  concise teammate guide. The user explicitly prohibited implementation edits.
- Tool/mode: Codex source/document review, Python/PyMuPDF, actual Gradle wrapper
  builds/tests and read-only generated-manifest/DEX class-definition inspection.
  Only three documentation files were added/updated in this stage.
- Review: no major abstraction/refactoring was recommended. Small duplicated
  lifecycle/read-observation blocks are preferable to adding a generic framework.
  Compiler override-parameter warnings and readability cleanup were reported,
  not silently fixed. Pending settlement correlation remains an owner dependency.
- Correction: an initial review update called Activity's missing kr suffix a
  prototype mismatch. Reading the embedded template/fmt function showed that the
  prototype omits it too. The final report correctly identifies a written-rule
  versus Hi-Fi conflict, requiring a team decision rather than an automatic edit.
- Actual checks: assembleDebug passed/up-to-date; testDebugUnitTest explicitly
  reran 65 JVM cases successfully; connectedDebugAndroidTest executed 21 cases on
  the existing Android 16 emulator successfully. No failures/errors/skips.
- Release/lint qualification: the normal release/lint checks were blocked by
  unavailable lint-gradle:32.3.3 in the offline cache. Release compilation emitted
  two WorkerFactory parameter-name warnings. A separate CLI-only lint-vital-excluded
  packaging check assembled an unsigned release APK successfully; it is not
  represented as a normal lint-validated release pass. Build scripts were unchanged.
- Artifact evidence: release manifest is not debuggable/testOnly, has no sandbox
  activity and retains only the approved WorkManager components. DEX inspection
  found zero app debug-package classes in release versus 37 in debug. Private
  main-source preview code is still packaged in release, with no production
  caller/writer; a completely preview-free binary would require a later approved
  source-set change. No false fixture-exclusion claim is made.
- Safety: no real processor/factory/scheduling hook was registered, no financial
  fake writes reach SQLite/Firebase, and 69 implementation/configuration hashes
  were checked for unchanged contents. Existing uncommitted work was preserved.
- Deliverables: person5_team_handoff.md and the Phase 5C section of person5_phase5.md.
  Controlled team integration is appropriate; full production/reliability/release
  sign-off still requires teammate contributions, clean lint and human review.
  No commit/push/history change, production activation or implementation edit.
