# AI dossier — Receipt scanning (Person 3, branch `feature/scanning`)

Editable record for the design document's Appendix A: one row per significant AI interaction, then the
AI mistakes that were observed and how they were caught.

| Date | Tool & mode | Task (prompt) | Verification | Human decision |
|---|---|---|---|---|
| 18-11-2026 | Claude Code (agent), Opus 5.5, plan mode first | Real receipt scanning ("We need to add actual scanning of the receipt"): CameraX camera, ML Kit's bundled (offline) text recognizer, our own Danish receipt parser | Unit tests for the parser and the scan screen state; 3 instrumented tests that read a drawn receipt with the real ML Kit model; camera, reading, "couldn't read" and retake checked on an emulator | Plan approved; built first on top of the hi-fi PR (#3) |
| 19-11-2026 | Claude Code (agent), plan mode first | Move scanning onto today's `main` ("is it ready to push?" → "yes please") after `main` got real groups, syncing and an expense repository | Built from `main`; 209 unit tests (26 new), lint 0 errors and no new warnings except "newer version" notices; all instrumented tests, including saving to a real database and the whole "+ Add expense" flow, with and without the camera permission; emulator checks with a separate test account, online and in airplane mode | Only the scan flow goes to `main`; PR #3 stays as a reference; the team reviews the PR |
| 20-11-2026 | Claude Code (agent) | Fix what the user found on real receipts ("the receipt total doesn't match, some prices are wrong … note if the total in app doesn't match the total from the receipt") | Reproduced with tilted receipts read by the real ML Kit model (0° correct; 2°–6° wrong); after the fix correct from −5° to 8°; parser tests for tilt, totals and split prices; device test with 4° and −6° photos; the total check tried on the emulator | Straighten rows, find the total in more ways, and always show the "Total on receipt" check |
| 20-11-2026 | Claude Code (agent) | Whole-system review, then "do 3 and 5": upload receipt photos to Firebase Storage so other members see them (US13), and replace the scan screens' hardcoded colours with theme tokens | Device tests for the upload with a stand-in storage (upload, path queued for Firestore, give up vs. try again, nothing to upload, start-up catch-up, saving starts it); a real upload from the emulator with the test account | Upload in its own background job so the expense never waits for the photo; Storage itself must be enabled by the project owner |
| 20-11-2026 | Claude Code (agent) | Accuracy test (NFR-6) on 10 Danish receipts found on the web ("you can do the tests yourself"; "if it at any point can't note the total … you should be able to input yourself"), then improve the reader | Agent wrote the answer key (196 lines, `answer_key.csv`) and ran every photo through the app's own reader on the emulator: 75.0 % → 90.3 % of lines, totals found 9 → 10 of 10. Each rule checked by switching it off; 8 new parser tests (3 on real recognizer output) and 2 device tests | The answer key must be checked against the photos by a person; the score is optimistic because the rules were tuned on these same receipts (see 8 below) |

## How the AI was used

- The agent compared PR #3 with what `main` had become and recommended not merging it: `main` had moved
  to the real phone database and Firestore sync, and PR #3 would have replaced that with demo data.
- Teammates' code was reused, not rewritten:
  - Bashir's `GroupRepository` gives the groups and members;
  - Gustav's `ExpenseRepository` saves the expense and queues the upload;
  - Gustav's `formatDanishAmountFromCents` formats amounts;
  - the saved expense follows Ramin's balance rules, which a test checks.
- Teammates' files changed only where the scan flow plugs in: the Scan tab in `AppShell`, and the
  "+ Add expense" button on the group page.

## AI mistakes observed and how they were caught

1. **Scanning, first version (18-11-2026):**
   - **Compose upgraded by accident.** An image library silently upgraded Compose for the whole app;
     found from new deprecation warnings. The library was removed.
   - **No photo could ever be saved.** The photo store's size check treated Android's intentional `null`
     as a failure. Found by the on-device test.
   - **Crash on Android 7.** The parser used named regex groups, which need Android 8; found by lint.
   - **State pointing at a deleted file.** Discarding a photo deleted the file but left the screen state
     pointing at it; found by a unit test.
   - **The size estimate was wrong:** "+4 MB" in the plan; the real cost is about +12 MB per phone.
2. **A typed minus price could crash the balances.** With Ramin's balance rules a negative share is
   refused, and a "-50" typed on a normal line could create one. Found while reading his rules. The
   line is now shared like a discount, and a test proves it (the test was first written so it couldn't
   fail; undoing the fix on purpose showed that, and it was corrected).
3. **Porting to `main` (19-11-2026):**
   - a constructor parameter with the same name as a property;
   - a trimmed helper missing a parameter the screen used;
   - a removed function leaving a stray brace;
   - wrong imports for two enums.

   All were caught by the compiler before anything ran.
4. **Found on the emulator with the test account, not by unit tests:**
   - "Synced · Nobody notified" in a group of one; it now says "Synced".
   - Invisible status-bar icons on the camera: the code switched them to white for a camera drawn
     under the status bar, but on `main` the app keeps clear of the bars, so the icons stay dark.
   - **A crash when the camera permission was already granted:** CameraX was bound on whichever thread
     `awaitInstance` resumed on. It worked in the app by luck and failed in a UI test. It is now bound
     and unbound on the main thread, and the tests were rerun with the permission granted.
5. **Tested only on a straight receipt (found by the user on real receipts).** The device test drew a
   perfectly straight receipt, so it missed what happens with a hand-held photo. Tilted by a few degrees:
   - every price slid one item down;
   - "TOTAL" and its amount split into two rows, so the total became an item and the check vanished;
   - one price came back as "45, 00" with a space after the comma, so it wasn't read at all.

   Fixes:
   - rows are straightened using the recognizer's line angles, weighted by text length, because short
     price lines report 0°;
   - the total is also found on the next row, or from the card payment;
   - prices may have a space next to the comma;
   - Check items always shows a "Total on receipt" field and whether the items match it.

   The new tilt test was run without the straightening to prove it fails.
6. **An assumption checked against the real backend.** The project config names a Storage bucket, but
   the first real upload got a 404: the bucket was never created. That is why the upload was built not
   to hold up the expense. The expense saved and synced anyway, and the photo stays on the phone until
   Storage is enabled. Photos are then caught up when the app next starts.
7. **Tests checked against the bug.** The test for "pressing Save twice saves once" was run with the
   guard removed, to prove it fails without it.
8. **Accuracy test (NFR-6), 20-11-2026.**
   - **Method.** The agent typed the answer key from the 10 photos (every item, pant and rabat line and
     the total) and scored the app's own reader against it: a line counts when an item has exactly the
     printed price and a recognisably similar name. The key was written by the AI, so a person has to
     check it against the photos before the numbers go in the report.
   - **Result.** 147 → 177 of 196 lines (75.0 % → 90.3 %), totals found 9 → 10, photo to items in
     0.1–0.4 s on the emulator. Rema 1000 (a 399 × 501 picture) stays at 56 %: its letters are too
     blurred to read. Every receipt with a wrong line shows a mismatch between the items and the total,
     so the user is told to check it.
   - **What changed.** Small photos are enlarged before reading (most of the gain). Each row gets one
     price, matched closest first, so a price printed between two rows no longer chains them together.
     Text to the right of the price column (a wine label beside the receipt) is ignored. A few
     recognizer misreads are accepted: "AT DETALL" for AT BETALE, "DANNORT" for DANKORT, "2% 14,00"
     for "2 x 14,00". Dates with a month name are skipped. Tilt: only long lines are measured, below
     1.5° the photo counts as straight, and above it the angle where the prices line up best is used.
   - **Mistakes caught:**
     - Twice a change made the score *worse* (89.3 % → 88.3 %, later 89.8 % → 89.3 %), both times
       because of tilt: the recognizer's angles are off by a degree or two, and a receipt that curls
       isn't tilted the same everywhere.
       Every kept rule was then switched off on its own to prove it helps. The three tilt rules
       together are worth only one line.
     - The quick replay of the saved recognizer output reported a stale result: Gradle skipped the
       unchanged test task. Caught because the output still had the full receipts' names. All checks
       were redone with forced reruns.
     - A check of which tests fail reported none for every rule switched off. That was implausible;
       the search couldn't read the multi-line test reports. Redone with an XML parser.
     - The first month-date rule would have hidden items like "1 JULEKAGE 450 G" or "2 MAJS 340 G".
       Found in the agent's own review of the diff; tightened, with a test.
   - **Limits, stated plainly.** The rules were tuned on the same 10 receipts, so 90.3 % is an upper
     estimate; a fresh set of receipts would give a fairer number. On Meny's item section alone the
     reader gets 10 of 12 (it is curled: 3° at the top, 0° at the bottom, and one angle is used for the
     whole receipt). The receipt photos are not in the repository; the tests keep only the item
     sections' recognizer output, without addresses, phone numbers or staff names.

AI-assisted code reaches `main` through a pull request. PRs #4 and #5 were merged by the user (Victor,
Person 3) on 20-11-2026 for the hand-in deadline, after he tested #4 on his own phone. A teammate's
review was not done before merging and should follow.
