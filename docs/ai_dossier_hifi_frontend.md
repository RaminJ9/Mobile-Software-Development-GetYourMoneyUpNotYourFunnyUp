# AI dossier — Hi-fi frontend and integration (branch `feature/hifi-frontend`, PR #3)

Editable record for the design document's Appendix A. One row per significant interaction, then the
AI failures that were observed and how they were caught.

| Date | Tool & mode | Task (prompt) | Verification | Human decision |
|---|---|---|---|---|
| 10-11-2026 | Claude Code (agent), Opus 5.5 | Implement every screen in `docs/HiFi.html` in Compose ("make the frontend work to look like the hifi.html") | `assembleDebug`, 40 unit tests (debt calculation, ViewModels, Reliability QA), every screen compared with the HiFi on an emulator | Accepted; asked for it on a branch for the team |
| 10-11-2026 | Claude Code (agent) | Fix "google-services.json is missing" when pressing Run | App built and launched on the emulator | Used the team's own Firebase file locally; it stays out of git |
| 10-11-2026 | Claude Code (agent) | Put the work on a branch for teammates | Rebased onto `main`; switched to Gustav's design system instead of a duplicate theme; build, tests, lint and emulator re-checked | Branch `feature/hifi-frontend` pushed |
| 16-11-2026 | Claude Code (agent) | Compare Mohammed's Home/Activity (#1) with ours, then use his ("is his okay to use?" → "use his screens … follow his good parts like testing and safety checks") | Both versions run side by side on the emulator; 116 unit tests; Mohammed's 10 compose UI tests on the emulator; lint compared with `main` (no new issues) | Use Mohammed's screens, fed from our ledger; small HiFi fixes allowed, no large changes to his code |
| 17-11-2026 | Claude Code (agent) | Keep Bashir's group screens (#2), build ours to match, open the PR ("keep his group screen and build ours to match it, then create the pull request") | 151 unit tests (12 new for the groups adapter); lint 0 errors; clean merge into `main` | Our shell and group page stay; his Groups, Create group and Members screens are used, fed from our ledger |
| 17-11-2026 | Claude Code (agent) | Finish everything that doesn't need teammates (device checks, this dossier) | All instrumented tests and a manual emulator pass of the group screens (see PR #3) | Human review of PR #3 by the teammates whose code changed |

## How the AI was used

- Every change was built and tested by the agent, then checked on an Android emulator where possible.
- Teammates' code was integrated through their own interfaces (`SettlementActions`, `SyncRetryActions`,
  `ContactDirectory`, `GroupCreationActions`, `MemberActions`, …) instead of rewriting it. Each change
  to a teammate's file is listed in PR #3.
- The user decided which version to keep each time; the agent compared versions and made a
  recommendation first.

## AI failures observed and how they were caught

1. **Wrong claim about the offline banner.** The agent first said Mohammed's "You're offline" banner
   was wrong because the emulator could ping. Checking DNS showed the emulator really had no usable
   internet, so his stricter check was right and ours was wrong. The agent corrected itself and the
   app now uses his check.
2. **Wrong claim about tappable Activity cards.** Listed as a weakness of Mohammed's screen; the HiFi
   showed cards are not tappable, so the claim was withdrawn and nothing was added.
3. **Disappearing payment card.** The first adapter made the "Paying…" card vanish for ~0.2 s before
   "Paid". Found while writing a test; fixed by syncing an online payment when it completes.
4. **Test that passed for the wrong reason / failed for the wrong reason.** One reconnect test
   failed only because of how `UnconfinedTestDispatcher` queues work; a temporary trace test proved it
   was a test artifact, and the tests were rewritten. A later test had a conditional assertion that
   could pass without checking anything; it was rewritten.
5. **Visual bugs only seen on the device**, not in unit tests: the paid card jumping to the bottom,
   the donut ignoring a payment made "just now", the Activity offline banner scrolled out of view, the
   list keeping its scroll position after a filter change. All were fixed and re-checked on the
   emulator.
6. **Leftover code.** Early versions left unused helpers and placeholders, and later merges left
   unused imports and components. Each pass ended with a search for unused code, which removed them.
7. **Data mapped to the wrong field.** A phone number was first put in a contact's email field; caught
   during review and covered by a test.

The team must still review PR #3; AI-assisted code reaches `main` only through a human-reviewed PR.
