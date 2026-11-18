# Ramin Javed — Personal Codex Development Instructions

## Purpose and applicability

These instructions apply when Codex works on tasks for **Ramin Javed (Person 4)**, who is also taking on **Gustav's (Person 1)** assigned responsibilities in this five-person university Kotlin Android project.

Read and follow the repository's existing root `AGENTS.md` for the established Kotlin/Jetpack Compose architecture, style system, Firebase and local data conventions, testing requirements, and team standards. **This personal file narrows the permitted implementation scope; it does not replace team standards.** If the two files or the documentation conflict, explain the conflict and request clarification before making changes.

Review the documents in `documentsR/` when relevant:
- `Design document.pdf` — requirements, priorities, acceptance criteria, and out-of-scope items.
- `Ansvarsfordeling - App Projekt.pdf` — feature ownership and integration boundaries.
- `Navigation model 2.drawio.png` — navigation flows.
- `Lofi Wireframe Diagram.drawio.png` — wireframes.
- `hifi.html` — visual prototype.

The existing `docs/` folder also contains team technical specifications referenced by the root `AGENTS.md`. Consult these when necessary, but **do not edit existing documentation or team-owned files without approval**. Confirm that PDFs and diagrams are actually readable; if an image or PDF cannot be interpreted, say so rather than inventing its content.

## Strict implementation scope

**Only implement work assigned to Person 1 or Person 4.** You may inspect the rest of the repository to understand dependencies, but do not implement, modify, or refactor another member's work without Ramin's explicit approval.

### Person 1 — Gustav: Authentication, Shell & Profile
- S1 / S2: login, account registration, and authentication screens/flows.
- Authenticated app shell and five-tab bottom navigation **as a navigation container only**, not the other tabs' feature implementations.
- S7 / S7-edit: user profile, profile editing, dietary/expense preferences, notification preferences.
- Logout and session handling.
- Only the minimum supporting app structure necessary for the above.

### Person 4 — Ramin: Balances, Settlement & Expense Details
- S8b: group balances view, diverging balance bar chart, and net-balance calculation.
- Settlement suggestions and the group-level "who pays whom" flow.
- S8-pay: relevant inline `Pay`, `Remind`, and `Mark as paid` actions and states.
- S9 / S9-confirm: expense-details bottom sheet, payer/date/total/splits, receipt image display where available, and owner-authorized deletion with confirmation.
- Relevant financial correctness, access rules, and tests for these features.

Keep implementation aligned with the design document's requirement priorities and acceptance criteria. Store and calculate monetary amounts using the existing project's integer-øre conventions; preserve correct balances and rounding.

## Out of scope — other team members

Do not independently implement:
- **Person 2:** group creation, invitations, search/filter/favourites, group membership and roles, member removal.
- **Person 3:** camera, receipt OCR/parsing, manual expense entry, item review/editing, item-level assignment and scanning flow.
- **Person 5:** home dashboard, global activity feed, and offline synchronization infrastructure, including ownership of Room/WorkManager sync work.

Existing shared libraries, models, repositories, and screens may be *read and reused*. If a feature depends on unfinished teammate work, describe the dependency. You may **propose** a minimal interface, data contract, adapter, or temporary test fixture, but do not change shared contracts or implement another person's module without approval. Avoid permanent fake implementations that conceal missing integrations.

## Student project and simulated payments

This is an educational project. Expenses, debts, balances and settlement actions are **application records**, not real-money transfers. Do not integrate banks, payment gateways, MobilePay transfers, or actual payment processing. A `Pay` action may show the documented confirmation flow and record a simulated settlement according to the application's agreed data model. `Remind` must only use the project's agreed notification/demo mechanism; do not invent external messaging services. Despite being simulated, arithmetic, authorization and persisted state must be correct.

## Keep it small

- Implement the smallest solution that meets approved requirements; prioritize **Must**, then **Should**, then **Could**.
- Do not add features, dependencies, frameworks, abstractions or major refactors merely because they seem useful.
- Follow existing project architecture, Compose design tokens, naming and navigation conventions.
- Avoid touching unrelated files. Do not overwrite teammates' work or change shared schemas and APIs without approval.
- Work on one feature/task at a time. Do not proceed automatically to the next task.

## Required workflow

**First session: analysis only.** Before changing files, inspect the existing Kotlin project, root `AGENTS.md`, `documentsR/` materials, and relevant team specifications in `docs/`. Then provide:
1. What is already implemented within Person 1 and Person 4 scope.
2. What is missing or incomplete, mapped to requirement/screen IDs and Must/Should/Could priority.
3. Dependencies on other team members and any documentation/code conflicts.
4. A minimal prioritized plan, including expected files to change.

**Do not edit files during this first analysis. Wait for Ramin's approval.**

For each later approved task:
1. State the specific goal, scope, impacted files, and necessary dependencies.
2. Change only what was approved.
3. Run relevant Windows Gradle checks when possible (for example `./gradlew.bat assembleDebug` and applicable tests); report what actually ran and any limitations.
4. Summarize the modifications, requirement IDs, verification results, unresolved issues, and any proposed follow-up work.
5. Ask before making changes outside Person 1/Person 4 ownership.

Follow the repository's rules for human review and AI interaction logging. Do not commit or push without explicit authorization.

## Scope reminder

**Understand the whole app; implement only Ramin + Gustav.** If doing a task would require implementing someone else's feature, stop at the boundary, explain it, and request permission.
