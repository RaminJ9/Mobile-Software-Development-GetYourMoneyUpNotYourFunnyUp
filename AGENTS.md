# AGENTS.md

## Project Overview
This repository contains **"Get Your Money Up Not Your Funny Up"**, an Android mobile application designed to help groups track and manage shared expenses (inspired by WeShare, MobilePay groups, and Splitwise). The app enables groups of users to split bills, calculate optimal debt settlements, maintain offline resilience, and send payment reminders.

### Tech Stack
- **Language**: Kotlin (`2.4.x`)
- **UI Framework**: Jetpack Compose (Material 3, Compose BOM)
- **Architecture**: MVVM / Clean Architecture (UI -> ViewModel -> Repository -> Local/Remote Data Sources)
- **Local Storage & Offline Caching**: Room Database or Firestore Offline Persistence
- **Background Tasks & Retries**: Android WorkManager (for resilient background sync/retries)
- **Backend & Cloud**: Firebase (Firebase Auth, Firestore, Analytics)
- **Build System**: Gradle with Kotlin DSL (`build.gradle.kts`) and Version Catalog (`gradle/libs.versions.toml`)
- **Target Specs**: Min SDK: 24, Target SDK: 36, Compile SDK: 37 (extension 1)

---

## Functional Requirements & Domain Model

1. **User Profile & Authentication**
   - User profile creation and updates (name, contact details, notification preferences).
   - Authentication via Firebase Auth (email/password or anonymous/mock fallback).

2. **Group Management**
   - Create, edit, and delete groups (title, description, members).
   - Add and remove members to/from groups.

3. **Expense Entry & Splitting**
   - Log expenses with payer, total amount, timestamp, and category/note.
   - Support splitting: equal split among all or customizable subset of members.
   - Receipt/invoice attachment support (optional/stretch).

4. **Debt Calculation & Balances**
   - Compute running balances (who owes whom and total net balance per person).
   - Debt settlement algorithm (e.g., greedy debt simplification to minimize total transactions).
   - Detailed ledger view of all group transactions and individual balances.

5. **Notifications & Reminders**
   - In-app and system alerts for new expenses and group updates.
   - Debt reminder notifications sent to debtor participants.

---

## Navigation Model & Screen Architecture

The application navigation flow is modeled in [`docs/navigation_model.drawio.xml`](file:///e:/Documents/GitHub/Mobile-Software-Development-GetYourMoneyUpNotYourFunnyUp/docs/navigation_model.drawio.xml). All Compose navigation destinations, routes, and interactions must strictly follow this structure:

### 1. Authentication & Onboarding
- **`S1 Login`**: Entry screen for unauthenticated users. Navigates to `S2 Registration` or authenticates to `S3 View groups`.
- **`S2 Registration`**: Account creation flow. Navigates into `S3 View groups` upon completion.
- **`Logout`**: Session termination action accessible from authenticated state, returning the user to `S1 Login`.

### 2. Main Groups Dashboard (Authenticated Session)
- **`S3 View groups`**: Main screen listing active groups.
  - **`S-filter view filtered groups`**: Search and filter view for groups.
  - **`S-empty No groups found`**: Empty state placeholder when no groups exist.
  - **Profile Action**: "click view profile" $\to$ `View profile` (edit details, notification settings).
  - **Add Group Action**: "Press Add group" $\to$ `Create group` $\to$ navigates into created `View group`.
  - **Group Selection**: "Press on group" $\to$ `View group`.

### 3. Group Detail & Member Management
- **`View group`**: Central hub for a specific group (displays current balances, running ledger, and navigation triggers):
  - **Members Action**: "Press view members" $\to$ `View group members`.
    - From members: "Press add member" $\to$ `Add member view`.
    - Error state: `S-Error User does not exist` if lookup fails.
  - **Add Expense Action**: "Press add expense" $\to$ `Add expense view`.
  - **View Expense Action**: "Press on expense" $\to$ `View expense`.
  - **Pay Action**: "Press Pay" $\to$ `View payment share`.

### 4. Expense Entry & Splitting Flow
- **`Add expense view`**: Enter expense title, total amount, payer, and date.
  - **Receipt Attachment Flow**:
    - "Press add receipt" $\to$ Check camera permissions.
    - If ungranted: `Ask for Device permission` $\to$ "Accept usage" $\to$ `Camera view`.
    - If granted: `Camera view` $\to$ "Take picture" $\to$ scan/parse receipt $\to$ populate expense details.
  - **Split Allocation**: "Press Next" $\to$ `Select group member's share view` (configure equal/custom split among members).
  - **Confirmation & Notification**: "Confirm" triggers "Notify group members" and shows `Confirmation` screen, then routes back to `View group`.

### 5. Expense Details & Deletion Flow
- **`View expense`**: Displays breakdown, receipt image (if present), and payer.
  - **Owner Action**: Expense owner presses "Remove" $\to$ `Confirmation` dialog (`(cancel)` dismisses, `(confirm)` deletes expense and navigates back to `View group`).

### 6. Settlement & Payment Flow
- **`View payment share`**: Displays debtor payment amount and payment methods.
  - **Confirm Payment**:
    - **Success**: Displays `Confirmation` screen, triggers debtor notification, returns to `View group`.
    - **Failure**: Displays `S-Error Payment failed` with retry capability.

---

## Non-Functional Requirements: Reliability Quality Attribute (QA)

The system must satisfy strict **Reliability** requirements:
- **Offline Availability**:
  - All previously fetched groups, expenses, and balances must remain viewable when the device is disconnected from the internet.
  - Local-first caching strategy via Room or Firestore offline cache.
- **Fault Tolerance**:
  - If a network request fails (e.g. creating an expense or updating a group), the action must be queued and retried automatically in the background (using WorkManager or retry interceptors).
  - Failed network calls must **never** block the user from interacting with the app.
- **User Feedback**:
  - The UI must clearly indicate sync state: `Pending / Syncing`, `Synced / Success`, or `Failed / Offline`.
  - When a queued background operation finally succeeds, notify or visually inform the user.

---

## Database Architecture & Setup (Local & Remote)

The project uses an **offline-first dual-database architecture** combining a local relational SQLite database (via Android Room) with a remote NoSQL cloud database (Firebase Cloud Firestore). Full schema diagrams and documentation are stored in [`docs/`](file:///e:/Documents/GitHub/Mobile-Software-Development-GetYourMoneyUpNotYourFunnyUp/docs):
- **Full Schema & Sync Documentation**: [`docs/database_schema.md`](file:///e:/Documents/GitHub/Mobile-Software-Development-GetYourMoneyUpNotYourFunnyUp/docs/database_schema.md)
- **Local SQLite / Room Diagram**: [`docs/local_database_schema.drawio.xml`](file:///e:/Documents/GitHub/Mobile-Software-Development-GetYourMoneyUpNotYourFunnyUp/docs/local_database_schema.drawio.xml)
- **Remote Cloud Firestore Diagram**: [`docs/remote_database_schema.drawio.xml`](file:///e:/Documents/GitHub/Mobile-Software-Development-GetYourMoneyUpNotYourFunnyUp/docs/remote_database_schema.drawio.xml)

### 1. Local Database Setup (Android SQLite / Room)
- **Role**: Acts as the **Single Source of Truth (SSOT)** for the UI. All Compose screens observe reactive Kotlin `Flow` streams emitted directly from local DAOs.
- **Database Manager & Contract**:
  - `AppDatabase` (`data/local/AppDatabase.kt`): `SQLiteOpenHelper` singleton configured with Write-Ahead Logging (`enableWriteAheadLogging()`), foreign key enforcement (`PRAGMA foreign_keys = ON`), and a reactive table invalidation tracker (`observeTables`).
  - `DatabaseContract` (`data/local/DatabaseContract.kt`): Defines the schema version, table names, column constants, `CREATE TABLE` DDL statements, and foreign key indexes.
- **Tables & Entities** (`data/local/entity/`):
  1. `users` (`UserEntity`): Local cache of user profiles, contact details, notification settings, and `sync_status`.
  2. `groups` (`GroupEntity`): Group metadata (`id`, `name`, `description`, `currency`, `created_by`, timestamps, and `sync_status`).
  3. `group_members` (`GroupMemberEntity`): Composite-key join table (`group_id`, `user_id`) with cascading deletes linking users to groups with their `role` (`ADMIN`, `MEMBER`) and `joined_at`.
  4. `expenses` (`ExpenseEntity`): Expense records storing monetary amounts in integer minor units (`amount_cents: Long`) to prevent floating-point rounding errors, along with `payer_id`, `receipt_local_uri`, `receipt_remote_path`, soft-delete flag (`is_deleted`), and `sync_status`.
  5. `expense_splits` (`ExpenseSplitEntity`): Individual member share allocations (`expense_id`, `user_id`, `amount_owed_cents: Long`, `share_weight: Double`) with `ON DELETE CASCADE` from `expenses`.
  6. `settlements` (`SettlementEntity`): Debt repayment records (`group_id`, `debtor_id`, `creditor_id`, `amount_cents: Long`, `payment_method`, `status`, and `sync_status`).
  7. `sync_queue` (`SyncQueueEntity`): Outbox table tracking unsynced mutations (`entity_type`, `entity_id`, `action`, `payload_json`, `retry_count`, `last_attempt_at`, `error_message`) processed by Android `WorkManager`.
- **DAOs** (`data/local/dao/`): `UserDao`, `GroupDao`, `ExpenseDao`, `SettlementDao`, and `SyncQueueDao` providing transactional CRUD operations and reactive `Flow` queries.
- **Type Converters** (`data/local/Converters.kt`): Maps domain enums (`SyncStatus`, `GroupRole`, `SettlementStatus`, `SyncAction`, `SyncEntityType`) to and from SQLite `TEXT` columns.

### 2. Remote Database Setup (Firebase Cloud Firestore)
- **Role**: Provides cloud backup, cross-device persistence, and real-time multi-user collaboration across group members.
- **Collection & Subcollection Hierarchy**:
  - `/users/{userId}`: Root collection storing user profile documents keyed by Firebase Auth UID, including nested `notificationSettings`.
  - `/groups/{groupId}`: Root collection storing group metadata. Each group document contains both a flat `memberIds: List<String>` array (enabling `whereArrayContains("memberIds", uid)` queries and single-lookup Firestore Security Rules) and a `members: List<Map>` array with embedded member display names and roles.
  - `/groups/{groupId}/expenses/{expenseId}`: Subcollection under each group containing expense documents with embedded `splits: List<Map>` arrays (`userId`, `amountOwedCents`, `shareWeight`) so an expense and its splits are fetched atomically in a single document read.
  - `/groups/{groupId}/settlements/{settlementId}`: Subcollection under each group recording debt repayments between `debtorId` and `creditorId`.
- **Synchronization Contract**:
  - **Writes**: Repositories write to Room first (`sync_status = PENDING_INSERT` / `PENDING_UPDATE` / `PENDING_DELETE`) and append an entry to `sync_queue`. A `WorkManager` worker (`NetworkType.CONNECTED`) pushes queued changes to Firestore and updates `sync_status` to `SYNCED` (or `FAILED` on unrecoverable errors).
  - **Reads / Real-Time Updates**: Snapshot listeners on `/groups` and active group subcollections normalize incoming Firestore documents into Room entities (`users`, `groups`, `group_members`, `expenses`, `expense_splits`, `settlements`) inside a database transaction with `sync_status = SYNCED`.
  - **Balance Calculation**: Balances are never stored as mutable fields in Room or Firestore (avoiding concurrent offline write conflicts). Instead, net balances are computed deterministically from the ledger of active `expenses`, `expense_splits`, and completed `settlements`.

---

## Definition of Done (DoD)

A feature or pull request is considered **Done** only when:
1. **Compiles & Passes CI**: Code builds cleanly without warnings (`.\gradlew.bat assembleDebug`).
2. **Automated Tests**:
   - Unit tests written for debt calculation logic and ViewModel state transitions (`.\gradlew.bat test`).
   - At least one test specifically verifying the **Reliability QA** (e.g., offline retrieval or retry mechanism).
3. **UI / Compose Standards**:
   - Implements Material 3 components and adheres to the app's theme.
   - Screen states are hoisted, previews provided, and edge-to-edge layout supported.
   - Sync/network states (Pending, Success, Failed) are visually exposed to the user.
4. **Peer Review**: AI-assisted code enters `main` only through a PR reviewed and verified by a human team member.
5. **AI Dossier Updated**: Log significant AI interactions in the design document Appendix A (task, prompt, tool, verification, decision, and any AI failures observed).

---

## Build & Test Commands

Run all commands using the Gradle wrapper in the project root:

### Windows (PowerShell / CMD)
```powershell
# Assemble debug APK
.\gradlew.bat assembleDebug

# Run unit tests
.\gradlew.bat test

# Run Android lint checks
.\gradlew.bat lint

# Clean build artifacts
.\gradlew.bat clean
```

### macOS / Linux
```bash
# Assemble debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew test

# Run Android lint checks
./gradlew lint

# Clean build artifacts
./gradlew clean
```

---

## Code Guidelines & Conventions

### Architecture & Layering
```
com.example.getyourmoneyupnotyourfunnyup/
├── data/
│   ├── local/          # Room DB, DAOs, Entities, Preferences
│   ├── remote/         # Firebase services, DTOs, network models
│   └── repository/     # Repository implementations with offline-first sync
├── model/              # Domain models (User, Group, Expense, Balance, SyncStatus)
├── ui/
│   ├── components/     # Reusable Compose widgets (cards, sync indicators, dialogs)
│   ├── screens/        # Feature screens (auth, groups, expenses, debt summary, profile)
│   ├── theme/          # Color, Type, Shape, Theme.kt
│   └── viewmodel/      # Architecture Components ViewModels & UI state definitions
└── worker/             # WorkManager sync and retry workers
```

### Jetpack Compose Best Practices
- **State Hoisting**: Stateful screens collect UI state from ViewModels; stateless composables receive immutable state and lambda event callbacks (`onAction: (Action) -> Unit`).
- **Modifiers**: Every public composable must expose `modifier: Modifier = Modifier` as its first optional parameter.
- **Recomposition Safety**: Use immutable data classes for UI state (`@Immutable` / `data class`). Do not run calculations directly in composables without `remember` or `derivedStateOf`.
- **Previews**: Provide light and dark theme `@Preview` annotations for all screen components.

### Kotlin Conventions
- Strictly prefer `val` over `var`.
- Represent UI state using sealed interfaces (`UiState.Loading`, `UiState.Success`, `UiState.Error`).
- Use Kotlin Coroutines (`Flow`, `StateFlow`) for asynchronous and reactive data streaming.

### Dependency Management
- Keep all dependencies declared in `gradle/libs.versions.toml`. Never hardcode library versions inside module build scripts.
