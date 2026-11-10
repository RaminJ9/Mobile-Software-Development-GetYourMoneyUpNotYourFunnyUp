# AGENTS.md

## Project Overview
This repository contains **"Get Your Money Up Not Your Funny Up"**, an Android mobile application designed to help groups track and manage shared expenses (inspired by WeShare, MobilePay groups, and Splitwise). The app enables groups of users to scan receipts or log bills offline, split items or totals with dietary rules, calculate optimal debt settlements, maintain offline resilience, and send payment reminders.

### Tech Stack
- **Language**: Kotlin (`2.4.x`)
- **UI Framework**: Jetpack Compose (Material 3, Compose BOM)
- **Typography**: `Plus Jakarta Sans` (weights 400–800) & `JetBrains Mono` (receipts only, weights 400–500) via downloadable Google Fonts (`res/font/`)
- **Architecture**: MVVM / Clean Architecture (UI -> ViewModel -> Repository -> Local/Remote Data Sources)
- **Local Storage & Offline Caching**: Android SQLite / Room Database (`AppDatabase`)
- **Background Tasks & Retries**: Android WorkManager (for resilient background sync/retries)
- **Backend & Cloud**: Firebase (Firebase Auth, Cloud Firestore, Analytics)
- **Build System**: Gradle with Kotlin DSL (`build.gradle.kts`) and Version Catalog (`gradle/libs.versions.toml`)
- **Target Specs**: Min SDK: 24, Target SDK: 36, Compile SDK: 37 (extension 1)

---

## Functional Requirements & Domain Model

1. **User Profile & Authentication**
   - Authentication via Firebase Auth (`Log in` / `Create account` with email, password, full name, and phone number).
   - User profile management (`Profile` tab & `Edit profile`): full name, email, phone number (used by friends to find and add users to groups), and profile avatar (`Green300` `#8FD6AA` for `"You"`).
   - **"Never charge me for" Dietary & Item Rules**: User-level toggles (`Alcohol`, `Meat`) that automatically exclude the user from matching scanned receipt items while allowing manual per-item override.
   - **Notification Preferences**: Granular toggles under `"Notify me about"` (`New expenses`, `Changed expenses`, `Payment reminders`, `Sync updates`).

2. **Group Management**
   - Create, edit, star/favourite (`fav`), and view groups with name, optional description, **group colour** (chosen from the 9-colour non-green/non-red palette), and members.
   - Add members by email or phone number, or quick-add from `"People you've split with"` suggestions.
   - **Unregistered User Handling**: If a searched email/phone does not match an existing user (`"No one uses the app with <query> yet"`), allow sending an invite (`Send invite`) which adds the member with an `invited` badge.
   - **Member Removal Safeguard**: Removing a member with a non-zero balance triggers an inline warning (`"<Name> still owes / is still owed X kr. Removing them can leave the group unbalanced."` with `Keep <Name>` / `Remove anyway`).

3. **Expense Entry, Receipt Scanning & Splitting**
   - **On-Device Receipt Scanner (`Scan` flow)**:
     - Works offline on the phone; frames and scans receipts in `JetBrains Mono` preview, extracts line items and prices, and checks whether the item sum matches the receipt total.
     - Highlights low-confidence/mismatched item prices in Amber (`#FFF4DC` fill, `#F0C870` border, `"Check this price"` tag) until corrected.
     - Supports special receipt line types: `Alcohol` (triggers dietary rule auto-exclusion), `Deposit · shared by share` (*pant*), and `Discount · shared by share` (*rabat*), which are distributed proportionally by each member's item share.
   - **Manual Entry & Item Editing**: Users can skip the camera (`Type it in manually instead`), edit item names/prices, add/remove items, select the target group, and change the payer (`Paid by`).
   - **Per-Item & Subset Splitting (`Who had what`)**:
     - Toggle members in/out of the entire expense at the top or toggle individual member avatars per item row.
     - Displays live per-member share totals at the bottom before saving (`Save expense · X kr`).
   - **Expense Details & Owner Removal**:
     - Tapping an expense opens the **Expense Details Bottom Sheet** displaying payer, date, total amount, sync status badge, receipt photo preview, and per-member split list.
     - Only the expense creator/payer can remove the expense via an inline confirmation (`Remove expense` -> `Keep it` / `Remove`), while non-owners see `"Only <Payer> can edit or remove this expense."`

4. **Debt Calculation, Balances & Inline Settlement**
   - **Danish Currency Format**: All monetary amounts are stored in integer minor units (`amount_cents` / øre) and formatted in Danish notation (`1.240,50 kr`, always 2 decimals, `.` thousands separator, `,` decimal separator).
   - **Running Balances & Diverging Bar Chart**: Group `Balances` tab visualizes each member's net balance (`Owes` in `Red300` `#F4B9B2` left of center, `Is owed` in `GreenBar` `#9FDCB5` right of center).
   - **Minimal Debt Settlement (`Settle up`)**: Computes the minimum number of payments required to clear all balances in the group (`"N payments clear every balance in the group"`).
   - **Confirm Money in Place**: Paying a debt (`Pay` / `Pay all`) always expands into `Cancel` / `Confirm` **in place on the same card** (never navigating to a separate screen), followed by `Paying…` and `Paid · settled` (`Green100` / `Green700`). Creditors can `Remind` (`Reminder sent`) or `Mark as paid`.

5. **Activity Feed, Notifications & Reminders**
   - Dedicated `Activity` tab grouping events into `Today`, `Yesterday`, and `Earlier` with filter pills (`All`, `Needs you · N`, `Expenses`, `Payments`) and `Mark all as read`.
   - Actionable feed cards allow paying a reminder (`Pay X kr` -> `Cancel` / `Confirm`) or retrying a failed sync (`Try now`) directly inside the feed.

---

## HiFi Design System, Themes & Styles (`docs/HiFi.html`)

All UI screens and components must strictly adhere to the interactive High-Fidelity prototype and Style Guide in [`docs/HiFi.html`](file:///c:/Users/gusta/Documents/GitHub/Mobile-Software-Development-GetYourMoneyUpNotYourFunnyUp/docs/HiFi.html), implemented in [`ui/theme/`](file:///c:/Users/gusta/Documents/GitHub/Mobile-Software-Development-GetYourMoneyUpNotYourFunnyUp/app/src/main/java/com/example/getyourmoneyupnotyourfunnyup/ui/theme):

### 1. Core Semantic Colour Rules ([`Color.kt`](file:///c:/Users/gusta/Documents/GitHub/Mobile-Software-Development-GetYourMoneyUpNotYourFunnyUp/app/src/main/java/com/example/getyourmoneyupnotyourfunnyup/ui/theme/Color.kt))
- **Calm, inviting, positive, intuitive**: Pastels are used for container fills; text and buttons use the deep shade of the same hue (all combinations pass 4.5:1 contrast). Dynamic wallpaper color is disabled by default (`dynamicColor = false`) to preserve semantic color meaning.
- **Green = good (`AppColors.Green*`)**: Money in, paid, settled, synced, done, and primary actions.
  - `Green700` (`#1E6B45`): Primary buttons, links, active nav tab, positive amounts (`+145,00 kr`).
  - `Green500` (`#3FA36B`): Progress bars, scanline, unread dots, input focus ring.
  - `Green300` (`#8FD6AA`): `"You"` avatar (always), camera frame corners, dark accents.
  - `Green100` (`#DDF3E4`): Positive hero cards (`All paid up`, `You're owed`), soft buttons (`Add expense`), `Synced` and `+X to you` chips.
  - `Green50` (`#EEF9F1`): Settled/paid card backgrounds.
- **Red = pay this (`AppColors.Red*`)**: **Strictly reserved for money you owe.** Disappears once paid.
  - `Red700` (`#A93A2F`): `Pay` buttons, amounts you owe.
  - `Red800` (`#8E3128`): Text on red fills and retry strips.
  - `Red300` (`#F4B9B2`): `Owes` bars in the group balance chart.
  - `Red100` (`#FCE4E1`): `"You owe"` hero card, `You owe X` chips.
  - `Red50` (`#FDF1EF`): `Payments due` expandable dropdown and sync-retry strips.
- **Ink & Surfaces (`AppColors.Ink*`, `Surface`, `Line`, `White`)**:
  - `Ink900` (`#17201B`): Main text and active pill fill.
  - `Ink600` (`#56635B`): Secondary text, captions, inactive tabs.
  - `Line` (`#E6EBE8`) & `LineSoft` (`#EEF1EF`): Card borders and dividers.
  - `Surface` (`#F1F4F2`) & `SurfaceSoft` (`#FFF4F7F5`): Search bars, segmented tab tracks, `Settled` chips.
  - `White` (`#FFFFFF`): Primary screen and card background.
- **Amber & Special Tokens (`AppColors.Amber*`, `Camera`, `Lavender50`)**:
  - `Amber800` (`#6E4500`), `Amber400` (`#F0C870`), `Amber100` (`#FFF4DC`): Warnings (price mismatch, unbalanced member removal, unfound user invite) and `Waiting to sync` badges.
  - `Camera` (`#101814`): Camera viewfinder background only.
  - `Lavender50` (`#F3EEFC`) & `Lavender800` (`#3E2C68`): Dietary rule notes (e.g., no alcohol / vegetarian).
- **Group & Member Colours (`AppColors.GroupColors`)**:
  - Each group is assigned one colour at creation and uses it everywhere (group avatar tile, `"Paid out"` donut chart slice, and group dot in `Activity`).
  - **Never green or red**, so a group tile or pie slice is never mistaken for `"paid"` or `"owe"`:
    - Primary 6: `Blue` (`#A9C8F5`), `Lavender` (`#C9B6F0`), `Peach` (`#F7C59F`), `Butter` (`#F3DE8A`), `Teal` (`#9ED9D2`), `Sand` (`#E2CDB0`).
    - Extra 3 (groups 7–9): `Sky` (`#B9DCEB`), `Lilac` (`#E4CBE9`), `Apricot` (`#F2D7B6`).
  - People avatars reuse the same palette; `"You"` is always `Green300` (`#8FD6AA`).

### 2. Typography Scale ([`Type.kt`](file:///c:/Users/gusta/Documents/GitHub/Mobile-Software-Development-GetYourMoneyUpNotYourFunnyUp/app/src/main/java/com/example/getyourmoneyupnotyourfunnyup/ui/theme/Type.kt))
- **Primary Font**: `PlusJakartaSansFontFamily` (weights `400`, `500`, `600`, `700`, `800`).
- **Receipt Font**: `JetBrainsMonoFontFamily` (weights `400`, `500`, used for receipt previews only).
- **HiFi Type Tokens (`AppTypography`)**:
  - `Display`: `30sp`, ExtraBold (`800`), `-3%` tracking (`"Snap a receipt"`, `"Welcome back"`).
  - `AmountLarge`: `34sp`, ExtraBold (`800`), `-3%` tracking (Expense details sheet total).
  - `Amount`: `28sp`, ExtraBold (`800`), `-3%` tracking (`"412,50 kr"` on hero cards).
  - `ScreenTitleLarge`: `26sp`, ExtraBold (`800`), `-2%` tracking (`"Groups"`, `"Activity"`, `"Expense saved"`).
  - `ScreenTitle`: `24sp`, ExtraBold (`800`), `-2%` tracking (`"Hi, Victor"`, group detail title).
  - `TopBarTitle`: `20sp`, ExtraBold (`800`) (`"Check items"`, `"Who had what"`, `"New group"`).
  - `Section`: `18sp`, ExtraBold (`800`) (`"Your groups"`).
  - `ItemTitle`: `15sp`, Bold (`700`) (`"Ski trip Åre"`).
  - `Body`: `15sp`, Medium (`500`), `1.5` line height (`"Everything is read on your phone."`).
  - `Caption`: `13sp`, Medium (`500`) (`"6 members · Active today"`).
  - `Label`: `12sp`, Bold (`700`) (Bottom nav labels, balance chips).

### 3. Spacing, Shapes & Touch Targets ([`Shape.kt`](file:///c:/Users/gusta/Documents/GitHub/Mobile-Software-Development-GetYourMoneyUpNotYourFunnyUp/app/src/main/java/com/example/getyourmoneyupnotyourfunnyup/ui/theme/Shape.kt))
- **Spacing (`AppSpacing`)**: `4, 8, 12, 16, 20, 24 dp`. Screen horizontal padding: `16 dp`; gap between cards: `8–12 dp`; gap between sections: `20 dp`.
- **Corner Radii (`AppShapes`)**:
  - `10 dp`: Small inputs (e.g., item price box)
  - `12 dp`: Balance chips, sync pills, segmented tab indicator
  - `14 dp`: Inline buttons (`44 dp` height), `44 dp` group avatar tiles
  - `16 dp`: Text fields (`52 dp` height), secondary buttons (`48 dp` height), segmented tab bars, alert strips
  - `18 dp`: Primary buttons (`56 dp` height), debt/member/split cards
  - `20 dp`: List cards, settings groups, filter pills
  - `22 dp`: Large group avatar tiles
  - `24 dp`: Hero cards, payments-due dropdown
  - `28 dp`: Bottom sheets (`topStart = 28.dp, topEnd = 28.dp`)
- **Touch Targets (`AppTouchTargets`)**: Nothing tappable smaller than `44 dp`. Primary buttons `56 dp`, text fields `52 dp`, secondary buttons `48 dp`, inline buttons `44 dp`, bottom navigation bar `80 dp` with center `64 dp` circular `Scan` button.
- **Icons**: Outline icons, `1.8` stroke width, rounded caps/joins (Material Symbols Rounded).

---

## Navigation Model & Screen Architecture

The application navigation flow combines the state transitions in [`docs/navigation_model.drawio.xml`](file:///c:/Users/gusta/Documents/GitHub/Mobile-Software-Development-GetYourMoneyUpNotYourFunnyUp/docs/navigation_model.drawio.xml) with the concrete screen layouts in [`docs/HiFi.html`](file:///c:/Users/gusta/Documents/GitHub/Mobile-Software-Development-GetYourMoneyUpNotYourFunnyUp/docs/HiFi.html):

### 1. Authentication & Onboarding (`Auth`)
- **`S1 Login` (`Log in`)**: App logo, `"Welcome back"`, email & password inputs (with show/hide password toggle and `"Forgot password?"`), primary `"Log in"` button (`56dp`), and mode switch (`"New here? Create an account"`).
- **`S2 Registration` (`Create account`)**: `"Split bills, stay friends"`, full name, email, phone number (`"Friends can find you by email or phone"`), password input, and `"Create account"` button. Navigates into the authenticated `Home` dashboard upon completion.
- **`Logout` (`Log out`)**: Accessible from the `Profile` tab under `Account`, terminating the session and returning to `S1 Login`.

### 2. Authenticated Shell: 5-Tab Bottom Navigation Bar
All main authenticated screens share an `80dp` bottom navigation bar with 5 destinations:
1. **`Home`** (`S3 View groups` dashboard)
2. **`Groups`** (`GroupsList` — full searchable & filterable groups directory)
3. **`Scan`** (Center elevated `64dp` green circular button launching the receipt scanner / expense entry flow)
4. **`Activity`** (Global chronological feed of expenses, reminders, payments, and sync statuses)
5. **`Profile`** (`View profile` & `Edit profile`)

### 3. Home Dashboard (`Home`)
- **Header**: Date caption, `"Hi, <User>"` title (`24sp`), and notification bell button with green unread indicator dot.
- **Offline Status Banner** (shown when disconnected): `"You're offline · showing saved data from HH:MM. Anything you add syncs when you're back."`
- **Dual Hero Cards**:
  - **Left Hero Card (Balance State)**:
    - *Owing state*: `Red100` (`#FCE4E1`) card showing `"You owe"`, total in `kr`, group count subtitle, and `"Pay now"` / `"Hide"` button that toggles the **Payments due** dropdown right below the hero row.
    - *All clear state*: `Green100` (`#DDF3E4`) card with checkmark badge, `"All paid up"`, and `"+X kr owed to you"` footer.
  - **Right Hero Card (`Paid out` Donut Chart)**:
    - Period selector dropdown (`Today`, `This week`, `This month`, `This year`).
    - Interactive SVG/Canvas donut chart with slices colored by each group's colour and center total (`"X kr this month"`).
    - **Pie Slice Popover**: Tapping a slice opens a floating callout card showing the group name, amount paid in that period, last payment details, and an `"Open group"` button.
- **`Payments due` Expandable Dropdown** (`Red50` `#FDF1EF`):
  - Header with `"Pay all"` button (expands inline `"Pay X kr to N groups?"` -> `Cancel` / `Confirm`).
  - Individual debt cards for each group owed, with **inline confirmation** (`Pay X kr` -> `Cancel` / `Confirm` -> `Paying…` -> `Paid · settled with <Name>`).
  - When all debts are paid, displays `"No payments due"` with a 5-second auto-draining progress bar before collapsing.
- **`Your groups` Section**:
  - Header with `+ New group` link and 3-way segmented control (`Recent`, `Favourites`, `All`).
  - Group rows showing group colour tile, name, favourite star, member count & activity timestamp (or `"1 expense waiting to sync"` spinner), and balance chip (`You owe X`, `+X to you`, or `Settled`).
  - `"See all N groups"` button routing to the `Groups` tab, or **`S-empty No groups yet`** empty state with `"Create a group"` and `"Join with an invite link"` actions.

### 4. Groups Tab & Create Group Flow (`Groups` / `Create group`)
- **`Groups` Tab (`S-filter view filtered groups`)**:
  - Search input (`"Search groups"`) and horizontal filter chips with counts (`All`, `Favourites`, `You owe`, `Owed to you`, `Settled`).
  - Contextual summary strip when filtering by `You owe` or `Owed to you` (e.g., `"You owe 412,50 kr across 3 groups"`).
  - Group cards with inline favourite star toggle button.
- **`Create group` (`New group`)**:
  - Live group tile preview + 6-swatch group colour picker (`Blue`, `Lavender`, `Peach`, `Butter`, `Teal`, `Sand`).
  - `Group name` and optional `Description` inputs.
  - `Members` section: added member chips (with removable `×` and `invited` badge), email/phone lookup field with `Add` button, **`S-Error User does not exist` inline alert** (`"No one uses the app with <query> yet..."` + `Send invite` button), and `"People you've split with"` quick-add suggestions.
  - Primary `"Create group"` button (disabled as `"Give your group a name"` until a name is entered).

### 5. Group Detail Hub (`View group`)
- **Top Bar & Header**: Back button, group settings (`⋯`) button, offline banner (if offline), `56dp` group colour tile, group name, `"N members · Created by <Name>"`, overlapping member avatar stack (tapping switches to `Members` tab), and group description.
- **Context-Aware Hero Card**:
  - *You owe*: `Red100` card (`"You owe <Name> X kr"`) with inline `Pay` -> `Cancel` / `Confirm X kr` -> `Paying…` -> transitions to `"You're all square"`.
  - *You're owed*: `Green100` card (`"You're owed +X kr"`) with debtor breakdown and `Remind` -> `Reminded` button.
  - *Settled*: `Green100` card (`"You're all square"`).
- **3 Segmented Tabs (`Activity` · `Balances` · `Members`)**:
  1. **`Activity` Tab**:
     - Header with expense count and soft green `+ Add expense` button.
     - Ledger rows for expenses (receipt icon or card icon) and settlements (swap arrows icon), showing payer, date, total amount, and user's share (`"You: X"`).
     - **Reliability Sync Strips**: Unsynced items show an attached bottom strip:
       - `Pending`: Amber strip (`"Saved on your phone · waiting to sync"` with spinner).
       - `Failed`: Red strip (`"Couldn't sync yet · retrying automatically"` with inline `"Try now"` button).
     - **Expense Details Bottom Sheet (`View expense`)**: Opens when tapping an expense row; displays title, payer, date, `34sp` amount, sync status pill (`Waiting to sync`, `Retrying…`, `Synced`), receipt photo card (`View`), per-member split list, and owner-only inline delete confirmation (`Remove expense` -> `Keep it` / `Remove`).
  2. **`Balances` Tab (`Balances & who pays whom`)**:
     - Diverging horizontal bar chart (`Owes` left in red, `Is owed` right in green) with net balance per member.
     - **`Settle up` Section**: Lists minimal settlement transactions (`"<Debtor> → <Creditor>"`) with inline `Pay X kr` (if user is debtor) or `Remind` + `Mark as paid` (if user is creditor).
  3. **`Members` Tab (`View group members`)**:
     - Dashed `+ Add member` button.
     - Member cards with avatar, name, role/dietary subtitle (`"Organiser · no alcohol"`, `"That's you"`, `"Member"`), net balance chip, and remove (`×`) button.
     - **Inline Unbalanced Removal Warning**: If removing a member with a non-zero balance, expands an Amber warning card with `Keep <Name>` and `Remove anyway`.

### 6. Receipt Scanning & Expense Entry Flow (`Scan` / `Add expense view`)
- **`0 · Camera permission` (`Ask for Device permission`)**: Shown on first use (`"Snap a receipt, skip the typing"`), with `"Allow camera"` and `"Type it in manually instead"`.
- **`1 · Camera view`**: Dark (`#101814`) viewfinder with green corner brackets, flash button, photo library picker, `80dp` shutter button, and manual entry button.
- **`2 · Reading receipt`**: Animated green scanline over the receipt with bottom sheet checklist (`"Found N lines"`, `"Reading prices"`, `"Checking the total"`, `"Read on your phone, so it works without internet"`).
- **`3 · Check items` (`Step 1 of 2` — `Add expense view`)**:
  - Group selector card (`Add to group` with `Change`), `Paid by` member selector pill, and receipt photo summary row.
  - **Total Verification Banner**: Amber warning if item sum mismatches receipt total (`"Items add up to X kr, but the receipt says Y kr. Check the highlighted price."`) or Green confirmation (`"Items match the receipt total"`).
  - Editable item rows with semantic tags (`Alcohol`, `Deposit · shared by share`, `Discount · shared by share`, `Check this price`), remove trash button, dashed `+ Add item` button, and sticky bottom bar with `Total` and `"Next: who had what"`.
- **`4 · Who had what` (`Step 2 of 2` — `Select group member's share view`)**:
  - Top member toggle row (`"Splitting between · tap to leave someone out"`).
  - Automatic dietary rule banner in `Lavender50` (e.g., `"Mads doesn't drink alcohol, so he's been taken off 2 items. Tap his circle on an item to add him back."`).
  - Per-item cards with member initial circles (`44dp`) to toggle who shared each item and live per-person cost (`"X each"` or `"Only you"`).
  - Proportional deposit/discount note, live 4-column member share summary strip, and primary `"Save expense · X kr"` button.
- **`5 · Saved` (`Confirmation`)**:
  - Checkmark hero badge, `"Expense saved"`, summary card of who owes whom and `"Your share"`, live sync status badge (`"Saved on your phone · syncing…"` -> `"Synced · <Members> notified"`), `"Done"` button, and `"Scan another receipt"` button.

---

## Non-Functional Requirements: Reliability Quality Attribute (QA)

The system must satisfy strict **Reliability** requirements visible throughout the HiFi prototype:
- **Offline Availability**:
  - All previously fetched groups, expenses, balances, and on-device receipt scanning must remain usable when disconnected from the internet.
  - Display the non-intrusive offline banner (`"You're offline · showing saved data from HH:MM..."`) when offline.
- **Fault Tolerance**:
  - If a network request fails (e.g. creating an expense or updating a group), the action is saved locally in SQLite and queued in `sync_queue` for automatic background retry via `WorkManager`.
  - Failed network calls must **never** block the user from interacting with the app.
- **User Feedback**:
  - Every expense and group surfaces its sync state clearly:
    - `Pending / Waiting to sync`: Amber badge (`#FFF4DC` / `#6E4500`) or group subtitle (`"1 expense waiting to sync"`).
    - `Failed / Retrying`: Red strip (`#FDF1EF` / `#8E3128`, `"Couldn't sync yet · retrying automatically"`) with an optional `"Try now"` button.
    - `Synced / Success`: Green badge (`#DDF3E4` / `#1E6B45`, `"Synced · <members> notified"`).

---

## Database Architecture & Setup (Local & Remote)

The project uses an **offline-first dual-database architecture** combining a local relational SQLite database (`AppDatabase`) with a remote NoSQL cloud database (Firebase Cloud Firestore). Full schema diagrams and documentation are stored in [`docs/`](file:///c:/Users/gusta/Documents/GitHub/Mobile-Software-Development-GetYourMoneyUpNotYourFunnyUp/docs):
- **Full Schema & Sync Documentation**: [`docs/database_schema.md`](file:///c:/Users/gusta/Documents/GitHub/Mobile-Software-Development-GetYourMoneyUpNotYourFunnyUp/docs/database_schema.md)
- **Local SQLite / Room Diagram**: [`docs/local_database_schema.drawio.xml`](file:///c:/Users/gusta/Documents/GitHub/Mobile-Software-Development-GetYourMoneyUpNotYourFunnyUp/docs/local_database_schema.drawio.xml)
- **Remote Cloud Firestore Diagram**: [`docs/remote_database_schema.drawio.xml`](file:///c:/Users/gusta/Documents/GitHub/Mobile-Software-Development-GetYourMoneyUpNotYourFunnyUp/docs/remote_database_schema.drawio.xml)

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
3. **UI / Compose & HiFi Standards**:
   - Uses semantic tokens from `AppColors`, `AppTypography`, `AppShapes`, `AppSpacing`, and `AppTouchTargets` in `ui/theme/` (never hardcoded hex values in screens).
   - Follows HiFi interaction rules: Danish amount formatting (`formatDanishAmountFromCents`), inline payment confirmation (`Pay` -> `Cancel` / `Confirm` on the same card), minimum `44dp` touch targets, and non-green/non-red group colours.
   - Screen states are hoisted, previews provided, and edge-to-edge layout supported.
   - Sync/network states (`Waiting to sync`, `Retrying…`, `Synced`, and offline banner) are visually exposed to the user.
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
│   ├── local/          # SQLite/Room DB, DAOs, Entities, Preferences
│   ├── remote/         # Firebase services, DTOs, network models
│   └── repository/     # Repository implementations with offline-first sync
├── model/              # Domain models (User, Group, Expense, Balance, Settlement, SyncStatus)
├── ui/
│   ├── components/     # Reusable Compose widgets (cards, sync indicators, balance chips, bottom nav)
│   ├── screens/        # Feature screens (auth, home, groups, scan, activity, profile)
│   ├── theme/          # Color.kt, Type.kt, Shape.kt, Theme.kt
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
