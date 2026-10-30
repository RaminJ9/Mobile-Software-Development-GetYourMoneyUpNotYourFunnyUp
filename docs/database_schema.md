# Database Schema Documentation

This document details the dual-layer database architecture for **"Get Your Money Up Not Your Funny Up"**:
- **Local Database (Android Room / SQLite)**: Primary offline-first source of truth for the UI, enforcing relational integrity and queueing unsynced operations.
  - Draw.io diagram: [`docs/local_database_schema.drawio.xml`](file:///e:/Documents/GitHub/Mobile-Software-Development-GetYourMoneyUpNotYourFunnyUp/docs/local_database_schema.drawio.xml)
- **Remote Database (Cloud Firestore)**: Cloud persistence and real-time multi-user synchronization engine.
  - Draw.io diagram: [`docs/remote_database_schema.drawio.xml`](file:///e:/Documents/GitHub/Mobile-Software-Development-GetYourMoneyUpNotYourFunnyUp/docs/remote_database_schema.drawio.xml)

---

## 1. Local SQLite / Room Relational Schema

The local database consists of 7 relational tables designed for offline resilience, transactional integrity, and WorkManager synchronization.

```mermaid
erDiagram
    users {
        TEXT id PK "Firebase Auth UID or UUID"
        TEXT email "NOT NULL"
        TEXT display_name "NOT NULL"
        TEXT photo_url "NULLABLE"
        INTEGER notifications_enabled "DEFAULT 1"
        TEXT sync_status "SYNCED | PENDING_INSERT | PENDING_UPDATE | PENDING_DELETE | FAILED"
        INTEGER updated_at "Epoch ms"
    }

    groups {
        TEXT id PK "UUID"
        TEXT name "NOT NULL"
        TEXT description "NULLABLE"
        TEXT currency "DEFAULT 'EUR'"
        TEXT created_by FK "users.id"
        INTEGER created_at "Epoch ms"
        INTEGER updated_at "Epoch ms"
        TEXT sync_status "Sync state"
    }

    group_members {
        TEXT group_id PK,FK "groups.id (ON DELETE CASCADE)"
        TEXT user_id PK,FK "users.id (ON DELETE CASCADE)"
        TEXT role "ADMIN | MEMBER"
        INTEGER joined_at "Epoch ms"
    }

    expenses {
        TEXT id PK "UUID"
        TEXT group_id FK "groups.id (ON DELETE CASCADE)"
        TEXT title "NOT NULL"
        INTEGER amount_cents "Minor currency units (Long)"
        TEXT currency "DEFAULT 'EUR'"
        TEXT payer_id FK "users.id"
        TEXT category "DEFAULT 'General'"
        TEXT notes "NULLABLE"
        TEXT receipt_local_uri "NULLABLE"
        TEXT receipt_remote_path "NULLABLE"
        INTEGER created_at "Epoch ms"
        TEXT created_by "users.id"
        INTEGER is_deleted "Soft delete flag (0 or 1)"
        TEXT sync_status "Sync state"
    }

    expense_splits {
        TEXT id PK "UUID"
        TEXT expense_id FK "expenses.id (ON DELETE CASCADE)"
        TEXT user_id FK "users.id"
        INTEGER amount_owed_cents "Minor currency units (Long)"
        REAL share_weight "DEFAULT 1.0"
    }

    settlements {
        TEXT id PK "UUID"
        TEXT group_id FK "groups.id (ON DELETE CASCADE)"
        TEXT debtor_id FK "users.id"
        TEXT creditor_id FK "users.id"
        INTEGER amount_cents "Minor currency units (Long)"
        TEXT currency "DEFAULT 'EUR'"
        TEXT payment_method "MobilePay | Cash | Bank"
        TEXT status "PENDING | COMPLETED | FAILED"
        INTEGER created_at "Epoch ms"
        TEXT sync_status "Sync state"
    }

    sync_queue {
        INTEGER queue_id PK "AUTOINCREMENT"
        TEXT entity_type "USER | GROUP | EXPENSE | SETTLEMENT"
        TEXT entity_id "Target row ID"
        TEXT action "CREATE | UPDATE | DELETE"
        TEXT payload_json "Serialized snapshot"
        INTEGER retry_count "DEFAULT 0"
        INTEGER last_attempt_at "Epoch ms (NULLABLE)"
        TEXT error_message "Last failure reason (NULLABLE)"
    }

    users ||--o{ group_members : "joins"
    groups ||--o{ group_members : "has members"
    groups ||--o{ expenses : "contains"
    users ||--o{ expenses : "pays (payer_id)"
    expenses ||--o{ expense_splits : "splits into"
    users ||--o{ expense_splits : "owes (user_id)"
    groups ||--o{ settlements : "tracks"
    users ||--o{ settlements : "pays back (debtor_id)"
    users ||--o{ settlements : "receives (creditor_id)"
```

---

## 2. Remote Cloud Firestore Document Hierarchy

Firestore uses a hierarchical document-subcollection model under `/groups/{groupId}` so security rules can validate group membership in a single lookup (`request.auth.uid in get(/databases/$(database)/documents/groups/$(groupId)).data.memberIds`).

```mermaid
flowchart TD
    Root["Firestore Root (/databases/(default)/documents)"]
    UsersCol["Collection: /users/{userId}"]
    GroupsCol["Collection: /groups/{groupId}"]
    ExpensesSub["Subcollection: /groups/{groupId}/expenses/{expenseId}"]
    SettlementsSub["Subcollection: /groups/{groupId}/settlements/{settlementId}"]

    Root --> UsersCol
    Root --> GroupsCol
    GroupsCol --> ExpensesSub
    GroupsCol --> SettlementsSub

    UsersDoc["User Document
    - id: String
    - email: String
    - displayName: String
    - photoUrl: String?
    - notificationSettings: Map
    - createdAt / updatedAt: Long"]

    GroupsDoc["Group Document
    - id: String
    - name: String
    - description: String?
    - currency: String
    - createdBy: String
    - memberIds: List&lt;String&gt;
    - members: List&lt;MemberMap&gt;
    - createdAt / updatedAt: Long"]

    ExpensesDoc["Expense Document
    - id: String
    - groupId: String
    - title: String
    - amountCents: Long
    - currency: String
    - payerId: String
    - category: String
    - notes: String?
    - receiptStoragePath: String?
    - splits: List&lt;SplitMap&gt;
    - createdAt: Long
    - createdBy: String
    - isDeleted: Boolean"]

    SettlementsDoc["Settlement Document
    - id: String
    - groupId: String
    - debtorId: String
    - creditorId: String
    - amountCents: Long
    - currency: String
    - paymentMethod: String
    - status: String
    - createdAt: Long
    - recordedBy: String"]

    UsersCol --- UsersDoc
    GroupsCol --- GroupsDoc
    ExpensesSub --- ExpensesDoc
    SettlementsSub --- SettlementsDoc
```

---

## 3. Synchronization & Offline-First Data Flow

```mermaid
sequenceDiagram
    participant UI as Compose UI / ViewModel
    participant Repo as OfflineFirstRepository
    participant Room as Local Room SQLite DB
    participant WM as WorkManager SyncWorker
    participant FS as Remote Cloud Firestore

    UI->>Repo: Create Expense / Settlement
    Repo->>Room: Insert entity (sync_status = PENDING_INSERT) & enqueue sync_queue
    Room-->>UI: Emit updated Flow immediately (non-blocking)
    Repo->>WM: Enqueue OneTimeWorkRequest (NetworkType.CONNECTED)
    WM->>Room: Read pending items from sync_queue
    WM->>FS: Write document to /groups/{groupId}/expenses/{id}
    alt Network Success
        FS-->>WM: ACK
        WM->>Room: Mark entity sync_status = SYNCED & remove from sync_queue
        Room-->>UI: Emit SYNCED status badge
    else Network Failure
        FS-->>WM: Exception / Timeout
        WM->>Room: Increment retry_count & set sync_status = FAILED (or retry backoff)
        Room-->>UI: Emit Pending/Failed indicator
    end
```
