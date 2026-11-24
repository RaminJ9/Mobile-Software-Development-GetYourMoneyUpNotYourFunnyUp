# AI dossier — Receipt scanning (Person 3, branch `feature/scanning`)

Editable record for the design document's Appendix A: one row per significant AI interaction, then the
AI mistakes that were observed and how they were caught.

| Date | Tool & mode | Task (prompt) | Verification | Human decision |
|---|---|---|---|---|
| 18-11-2026 | Claude Code (agent), Opus 5.5, plan mode first | Real receipt scanning ("We need to add actual scanning of the receipt"): CameraX camera, ML Kit's bundled (offline) text recognizer, our own Danish receipt parser | Unit tests for the parser and the scan screen state; 3 instrumented tests that read a drawn receipt with the real ML Kit model; camera, reading, "couldn't read" and retake checked on an emulator | Plan approved; built first on top of the hi-fi PR (#3) |
| 19-11-2026 | Claude Code (agent), plan mode first | Move scanning onto today's `main` ("is it ready to push?" → "yes please") after `main` got real groups, syncing and an expense repository | Built from `main`; 209 unit tests (26 new), lint 0 errors and no new warnings except "newer version" notices; all instrumented tests, including saving to a real database and the whole "+ Add expense" flow, with and without the camera permission; emulator checks with a separate test account, online and in airplane mode | Only the scan flow goes to `main`; PR #3 stays as a reference; the team reviews the PR |
| 20-11-2026 | Claude Code (agent) | Fix what the user found on real receipts ("the receipt total doesn't match, some prices are wrong … note if the total in app doesn't match the total from the receipt") | Reproduced with tilted receipts read by the real ML Kit model (0° correct; 2°–6° wrong); after the fix correct from −5° to 8°; parser tests for tilt, totals and split prices; device test with 4° and −6° photos; the total check tried on the emulator | Straighten rows, find the total in more ways, and always show the "Total on receipt" check |
| 20-11-2026 | Claude Code (agent) | Whole-system review, then "do 3 and 5": upload receipt photos to Firebase Storage so other members see them (US13), and replace the scan screens' hardcoded colours with theme tokens | Device tests for the upload with a stand-in storage (upload, path queued for Firestore, give up vs. try again, nothing to upload, start-up catch-up, saving starts it); a real upload from the emulator with the test account | Upload in its own background job so the expense never waits for the photo; Storage itself must be enabled by the project owner |
| 20-11-2026 | Claude Code (agent) | Accuracy test (NFR-6) on Danish receipts found on the web ("you can do the tests yourself"; "if it at any point can't note the total … you should be able to input yourself"), then improve the reader; later "Added some more" (5 new receipts) | Agent wrote the answer keys (`answer_key.csv`, `answer_key_new.csv`) before running the reader, then ran every photo through the app's own reader on the emulator and on a Galaxy S10e. The 5 new receipts showed the first version was overfitted; final: 75.0 % → 89.8 % on the first 10, 95.2 % → 98.4 % on the new 5. Each kept rule checked by switching it off | User approved removing the overfitted tilt rules. The answer keys must be checked against the photos by a person (see 8 below) |
| 24-11-2026 | Claude Code (agent) | Demo preparation ("add some mock data … one needs to have toggled on alcohol and another the meat … does it work yet with the meat?"), then "create the accounts and fix both" | Three demo accounts (Mads: no alcohol, Sofie: no meat, Jonas: none) created the way the app's sign-up does; found by email from the Add member field on the emulator. Unit tests for the Meat tag and one note per person, each run with its fix undone to prove it fails; a SuperBrugsen receipt scanned on the emulator showed the Meat tags and "Sofie doesn't eat meat and was taken off 3 items" | User approved creating the accounts in the team's Firebase project and both fixes |

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
8. **Accuracy test (NFR-6), 20-11-2026: an overfitted first version, caught by fresh receipts.**
   - **Method.** The agent typed an answer key from the photos (every item, pant and rabat line and the
     total) and scored the app's own reader against it: a line counts when an item has exactly the
     printed price and a recognisably similar name. The keys were written by the AI, so a person has to
     check them against the photos before the numbers go in the report.
   - **First version (PR #6 as opened).** Tuned on the first 10 receipts: 75.0 % → 90.3 %. The agent
     said the number was optimistic and asked for a few fresh receipts.
   - **The fresh receipts showed overfitting.** The user added 5 receipts; the agent wrote their answer
     key before running the reader on them. There the tuned version read 76.2 % of the lines, while
     the unchanged `main` read 95.2 %. Switching the rules off one at a time on all 15 showed that
     enlarging small photos was nearly the whole gain. The three tilt rules (measure only long lines,
     read photos under 1.5° as straight, pick the angle where prices line up best) had won one line on
     the first 10 and lost 14 on the new ones; one-price-per-row added nothing once they were gone.
     With the user's approval they were removed, going back to `main`'s tilt and row handling.
   - **Final result** (the same on the emulator and the phone):

     | | `main` | final |
     |---|---|---|
     | First 10 (rules chosen on these) | 147/196 = 75.0 % | 176/196 = 89.8 % |
     | New 5 (used to choose what to remove) | 60/63 = 95.2 % | 62/63 = 98.4 % |
     | All 15 | 207/259 = 79.9 % | 238/259 = 91.9 % |

     The new 5 helped decide what to remove, so they are not a perfectly clean test either. Removing
     tuned rules is less risky than adding new ones, but a few more unseen receipts would confirm the
     number. On a Samsung Galaxy S10e a photo becomes items in at most 0.8 s, and the first read after the
     app starts takes 0.7–0.9 s. The scan screens add a 0.9 s animation.
   - **What stayed.** Small photos (from the web or a chat) are enlarged to 2400 px before reading.
     Text to the right of the price column (a wine label beside the receipt) is ignored. Accepted
     recognizer misreads: "AT DETALL" for AT BETALE, "DANNORT" for DANKORT, "2% 14,00" for "2 x 14,00".
     Dates with a month name are skipped.
   - **Every wrong receipt is flagged.** Each receipt with a wrong line shows a mismatch between the
     items and the total, or has no total, so the user types it into "Total on receipt". Two problems
     were in the photos themselves: the Jem & Fix picture cuts off its total, and a made-up Netto
     receipt's prices don't add up to its printed total, which the app correctly reports.
   - **Mistakes caught:**
     - Overfitting, described above. It is the main lesson: rules tuned on the scored set look better
       than they are, and only receipts the rules were not built on showed it.
     - The quick replay of the saved recognizer output reported a stale result: Gradle skipped the
       unchanged test task. Caught because the output still had the full receipts' names. All checks
       were redone with forced reruns.
     - A check of which tests fail reported none for every rule switched off. That was implausible;
       the search couldn't read the multi-line test reports. Redone with an XML parser.
     - The first month-date rule would have hidden items like "1 JULEKAGE 450 G" or "2 MAJS 340 G".
       Found in the agent's own review of the diff; tightened, with a test.
   - The receipt photos are not in the repository. The tests keep only the item sections' recognizer
     output, without addresses, phone numbers or staff names.

9. **Diet rules only half shown (found while preparing the demo, 24-11-2026).** The scanning code the agent wrote never set the "Meat" tag on Check items, although the design and the screen had one, and the diet-rule note on Who had what named only the first person a rule took off items. Meat items were split correctly, so no test noticed. Now meat items are tagged and every person gets their own note. Still open: an item's tag is set only when the receipt is read, so renaming a misread item doesn't tag it.

AI-assisted code reaches `main` through a pull request. PRs #4, #5 and #6 were merged by the user (Victor,
Person 3) on 20-11-2026 for the hand-in deadline, after he tested #4 on his own phone; #6 was also checked on
his phone and on receipts he added. A teammate's review was not done before merging and should follow.
