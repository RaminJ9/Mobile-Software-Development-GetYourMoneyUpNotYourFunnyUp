# Person 1 and Person 4 handoff - 20 November 2026

## Completed application paths

- S1/S2 and session: email/password sign-in, registration with name/phone, password reset, restore session, logout to Login. Authenticated Home replaces Counter. Account-scoped ViewModels are disposed on logout.
- S7/S7-edit: name/phone editing, Alcohol/Meat preferences and four independent notification preferences persist locally and enter the outbox. Email editing requests Firebase verification; the displayed sign-in email remains unchanged until verification and a new login. Firebase Auth is the authority for verified email, which is then reconciled into SQLite and the user outbox.
- S8b (FE8): deterministic integer-ore balances, Danish amounts, diverging chart, minimal settlement suggestions. The existing Home/Groups UI receives the same ledger calculations and actual group metadata.
- S8-pay (FE11): inline virtual Pay / Pay all, creditor Mark as paid, and persisted in-app Remind. Reminders carry the actual debtor, creditor and amount into Activity. Payment confirmation records a virtual settlement, never a bank/payment-provider transaction. Stable obligation references prevent duplicate recording across connected screens and allow unchanged payments to complete in Pay all.
- S9 (FE9, FE7) / S9-confirm: group Activity lists saved expenses and completed virtual payments chronologically, including who paid whom. Details show payer/date/total/member shares/sync status and available receipt images. Payer or creator can confirm removal in the same sheet; cancellation writes nothing. Removal is a soft-delete plus outbox entry in one SQLite transaction. Existing virtual payments are retained, so removing an already-paid expense can create a reversed balance.
- SQLite schema 5 adds reminders and per-account Activity read markers. Earlier users/ledger/outbox data is preserved. Cloud snapshots normalize groups, expenses, settlements and reminders into SQLite; pending local edits are protected. Group updates no longer replace/delete the parent row and cascade away its ledger. Table revision observation avoids losing writes during the initial read.
- Existing WorkManager infrastructure is connected through the Application worker factory, connected-network scheduling, account-specific queue selection and retry. A pending/failed local record is never presented as a server acknowledgement.

## Reminder data contract

Firestore path: `/groups/{groupId}/reminders/{reminderId}`.
Fields: `id`, `groupId`, `debtorId`, `creditorId`, `amountCents` (integer), `currency` (`DKK`), `obligationId`, `createdAt` (milliseconds).
The document ID is `reminder-` plus the stable payment obligation reference. Repeated taps for that same obligation do not create extra reminders. A new financial obligation gets a new reference. Read state is saved on this phone in `activity_reads`, scoped by user ID and event ID.

SQLite saves the reminder and `REMINDER/CREATE` outbox entry atomically. The creditor sees waiting/failed status until Firestore acknowledges it. The debtor's account listener caches the reminder and the existing Activity UI offers an inline virtual payment only while the matching debt remains valid. Recipient payment-reminder preferences are respected. No SMS, push provider or external email reminder service was added.

## Backend and manual checks still required

These are integration/review checks, not claims of verified live delivery:

1. The Firebase owner must verify deployed Firestore rules for the new reminders subcollection. Authenticated group members need the documented read access; creating a reminder must be limited to its creditor with valid group membership, debtor, positive amount and currency. Preserve server authorization for expense owner removal and debtor/creditor settlement recording. No rules were deployed by Codex.
2. Verify two real test accounts: creditor sends Remind; debtor receives the correct Activity card; debtor confirms a virtual payment; both accounts converge. Repeat offline then reconnect/restart. Automated tests use an isolated database and a fake remote transport; they do not prove deployed Firebase rules.
3. Cloud receipt viewing requires an existing uploaded object and configured Firebase Storage bucket/rules permitting the current group member to read it. Downloads are limited and images cached privately for later offline viewing. Uploading/scanning/OCR and expense creation belong to Person 3 and are unchanged.
4. Manually test password-reset and change-email links with a test inbox. Recent-login errors ask the user to log out/in and retry. No verification/reset email was sent by Codex during testing.
5. Human team review/PR is still required by AGENTS.md. This work has not been committed or pushed automatically. Existing unrelated lint warnings are not an assurance that the repository meets the team's zero-warning DoD.

## Scope boundaries

Home/Activity presentation and group-management interfaces are reused through the approved shared integration. No scanner/OCR, expense-creation flow, group invitations or external financial processing was implemented here. Historical expense-edit notifications remain a Person 3/5 event-source integration; the app does not invent past edit events from current rows. Dietary toggles are stored here; applying them during item assignment is Person 3's responsibility.

## Verification

Verified on 20 November 2026:

- `:app:testDebugUnitTest`: 198 tests passed, zero failures.
- `assembleDebug`: APK built successfully, with no Kotlin compiler warnings in the final build.
- `:app:lintDebug`: zero errors, 125 advisory warnings across the repository (fonts, unused resources/attributes, typos, KTX and build-tool suggestions).
- `:app:connectedDebugAndroidTest`: all 86 tests passed on Pixel_8_Pro / API 37, using software graphics and a temporary 1 GB Gradle heap after the earlier emulator renderer crash. No emulator factory reset was performed. The old offline emulator was skipped.
- `git diff --check`: passed.

The AI interaction/failure record is appended to the design document Appendix A. No commit or push was performed.

Firebase reference used for the email-change implementation: https://firebase.google.com/docs/reference/android/com/google/firebase/auth/FirebaseUser#verifyBeforeUpdateEmail(java.lang.String)
