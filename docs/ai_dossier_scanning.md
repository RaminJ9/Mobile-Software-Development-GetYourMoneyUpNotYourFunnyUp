# AI dossier — Receipt scanning (Person 3, branch `feature/scanning`)

Editable record for the design document's Appendix A: one row per significant AI interaction, then the
AI mistakes that were observed and how they were caught.

| Date | Tool & mode | Task (prompt) | Verification | Human decision |
|---|---|---|---|---|
| 18-11-2026 | Claude Code (agent), Opus 5.5, plan mode first | Real receipt scanning ("We need to add actual scanning of the receipt"): CameraX camera, ML Kit's bundled (offline) text recognizer, our own Danish receipt parser | Unit tests for the parser and the scan screen state; 3 instrumented tests that read a drawn receipt with the real ML Kit model; camera, reading, "couldn't read" and retake checked on an emulator | Plan approved; built first on top of the hi-fi PR (#3) |
| 19-11-2026 | Claude Code (agent), plan mode first | Move scanning onto today's `main` ("is it ready to push?" → "yes please") after `main` got real groups, syncing and an expense repository | Built from `main`; 209 unit tests (26 new), lint 0 errors and no new warnings except "newer version" notices; all instrumented tests, including saving to a real database and the whole "+ Add expense" flow, with and without the camera permission; emulator checks with a separate test account, online and in airplane mode | Only the scan flow goes to `main`; PR #3 stays as a reference; the team reviews the PR |
| 20-11-2026 | Claude Code (agent) | Fix what the user found on real receipts ("the receipt total doesn't match, some prices are wrong … note if the total in app doesn't match the total from the receipt") | Reproduced with tilted receipts read by the real ML Kit model (0° correct; 2°–6° wrong); after the fix correct from −5° to 8°; parser tests for tilt, totals and split prices; device test with 4° and −6° photos; the total check tried on the emulator | Straighten rows, find the total in more ways, and always show the "Total on receipt" check |

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
6. **Tests checked against the bug.** The test for "pressing Save twice saves once" was run with the
   guard removed, to prove it fails without it.

AI-assisted code reaches `main` only through a pull request reviewed by a human team member.
