package com.example.getyourmoneyupnotyourfunnyup.data.local

/**
 * Defines the relational SQLite schema (tables, columns, foreign keys, and indexes)
 * for the local offline-first database.
 */
object DatabaseContract {
    const val DATABASE_NAME = "get_your_money_up.db"
    const val DATABASE_VERSION = 2

    object UsersTable {
        const val TABLE_NAME = "users"
        const val COL_ID = "id"
        const val COL_EMAIL = "email"
        const val COL_DISPLAY_NAME = "display_name"
        const val COL_PHOTO_URL = "photo_url"
        const val COL_COUNT = "count"
        const val COL_NOTIFICATIONS_ENABLED = "notifications_enabled"
        const val COL_SYNC_STATUS = "sync_status"
        const val COL_UPDATED_AT = "updated_at"

        const val CREATE_SQL = """
            CREATE TABLE IF NOT EXISTS $TABLE_NAME (
                $COL_ID TEXT PRIMARY KEY NOT NULL,
                $COL_EMAIL TEXT NOT NULL,
                $COL_DISPLAY_NAME TEXT NOT NULL,
                $COL_PHOTO_URL TEXT,
                $COL_COUNT INTEGER NOT NULL DEFAULT 0,
                $COL_NOTIFICATIONS_ENABLED INTEGER NOT NULL DEFAULT 1,
                $COL_SYNC_STATUS TEXT NOT NULL DEFAULT 'SYNCED',
                $COL_UPDATED_AT INTEGER NOT NULL
            )
        """
    }

    object GroupsTable {
        const val TABLE_NAME = "groups"
        const val COL_ID = "id"
        const val COL_NAME = "name"
        const val COL_DESCRIPTION = "description"
        const val COL_CURRENCY = "currency"
        const val COL_CREATED_BY = "created_by"
        const val COL_CREATED_AT = "created_at"
        const val COL_UPDATED_AT = "updated_at"
        const val COL_SYNC_STATUS = "sync_status"

        const val CREATE_SQL = """
            CREATE TABLE IF NOT EXISTS $TABLE_NAME (
                $COL_ID TEXT PRIMARY KEY NOT NULL,
                $COL_NAME TEXT NOT NULL,
                $COL_DESCRIPTION TEXT,
                $COL_CURRENCY TEXT NOT NULL DEFAULT 'EUR',
                $COL_CREATED_BY TEXT NOT NULL,
                $COL_CREATED_AT INTEGER NOT NULL,
                $COL_UPDATED_AT INTEGER NOT NULL,
                $COL_SYNC_STATUS TEXT NOT NULL DEFAULT 'SYNCED'
            )
        """
    }

    object GroupMembersTable {
        const val TABLE_NAME = "group_members"
        const val COL_GROUP_ID = "group_id"
        const val COL_USER_ID = "user_id"
        const val COL_ROLE = "role"
        const val COL_JOINED_AT = "joined_at"

        const val CREATE_SQL = """
            CREATE TABLE IF NOT EXISTS $TABLE_NAME (
                $COL_GROUP_ID TEXT NOT NULL,
                $COL_USER_ID TEXT NOT NULL,
                $COL_ROLE TEXT NOT NULL DEFAULT 'MEMBER',
                $COL_JOINED_AT INTEGER NOT NULL,
                PRIMARY KEY ($COL_GROUP_ID, $COL_USER_ID),
                FOREIGN KEY ($COL_GROUP_ID) REFERENCES ${GroupsTable.TABLE_NAME}(${GroupsTable.COL_ID}) ON DELETE CASCADE,
                FOREIGN KEY ($COL_USER_ID) REFERENCES ${UsersTable.TABLE_NAME}(${UsersTable.COL_ID}) ON DELETE CASCADE
            )
        """
    }

    object ExpensesTable {
        const val TABLE_NAME = "expenses"
        const val COL_ID = "id"
        const val COL_GROUP_ID = "group_id"
        const val COL_TITLE = "title"
        const val COL_AMOUNT_CENTS = "amount_cents"
        const val COL_CURRENCY = "currency"
        const val COL_PAYER_ID = "payer_id"
        const val COL_CATEGORY = "category"
        const val COL_NOTES = "notes"
        const val COL_RECEIPT_LOCAL_URI = "receipt_local_uri"
        const val COL_RECEIPT_REMOTE_PATH = "receipt_remote_path"
        const val COL_CREATED_AT = "created_at"
        const val COL_CREATED_BY = "created_by"
        const val COL_IS_DELETED = "is_deleted"
        const val COL_SYNC_STATUS = "sync_status"

        const val CREATE_SQL = """
            CREATE TABLE IF NOT EXISTS $TABLE_NAME (
                $COL_ID TEXT PRIMARY KEY NOT NULL,
                $COL_GROUP_ID TEXT NOT NULL,
                $COL_TITLE TEXT NOT NULL,
                $COL_AMOUNT_CENTS INTEGER NOT NULL,
                $COL_CURRENCY TEXT NOT NULL DEFAULT 'EUR',
                $COL_PAYER_ID TEXT NOT NULL,
                $COL_CATEGORY TEXT NOT NULL DEFAULT 'General',
                $COL_NOTES TEXT,
                $COL_RECEIPT_LOCAL_URI TEXT,
                $COL_RECEIPT_REMOTE_PATH TEXT,
                $COL_CREATED_AT INTEGER NOT NULL,
                $COL_CREATED_BY TEXT NOT NULL,
                $COL_IS_DELETED INTEGER NOT NULL DEFAULT 0,
                $COL_SYNC_STATUS TEXT NOT NULL DEFAULT 'SYNCED',
                FOREIGN KEY ($COL_GROUP_ID) REFERENCES ${GroupsTable.TABLE_NAME}(${GroupsTable.COL_ID}) ON DELETE CASCADE,
                FOREIGN KEY ($COL_PAYER_ID) REFERENCES ${UsersTable.TABLE_NAME}(${UsersTable.COL_ID})
            )
        """
    }

    object ExpenseSplitsTable {
        const val TABLE_NAME = "expense_splits"
        const val COL_ID = "id"
        const val COL_EXPENSE_ID = "expense_id"
        const val COL_USER_ID = "user_id"
        const val COL_AMOUNT_OWED_CENTS = "amount_owed_cents"
        const val COL_SHARE_WEIGHT = "share_weight"

        const val CREATE_SQL = """
            CREATE TABLE IF NOT EXISTS $TABLE_NAME (
                $COL_ID TEXT PRIMARY KEY NOT NULL,
                $COL_EXPENSE_ID TEXT NOT NULL,
                $COL_USER_ID TEXT NOT NULL,
                $COL_AMOUNT_OWED_CENTS INTEGER NOT NULL,
                $COL_SHARE_WEIGHT REAL NOT NULL DEFAULT 1.0,
                FOREIGN KEY ($COL_EXPENSE_ID) REFERENCES ${ExpensesTable.TABLE_NAME}(${ExpensesTable.COL_ID}) ON DELETE CASCADE,
                FOREIGN KEY ($COL_USER_ID) REFERENCES ${UsersTable.TABLE_NAME}(${UsersTable.COL_ID})
            )
        """
    }

    object SettlementsTable {
        const val TABLE_NAME = "settlements"
        const val COL_ID = "id"
        const val COL_GROUP_ID = "group_id"
        const val COL_DEBTOR_ID = "debtor_id"
        const val COL_CREDITOR_ID = "creditor_id"
        const val COL_AMOUNT_CENTS = "amount_cents"
        const val COL_CURRENCY = "currency"
        const val COL_PAYMENT_METHOD = "payment_method"
        const val COL_STATUS = "status"
        const val COL_CREATED_AT = "created_at"
        const val COL_SYNC_STATUS = "sync_status"

        const val CREATE_SQL = """
            CREATE TABLE IF NOT EXISTS $TABLE_NAME (
                $COL_ID TEXT PRIMARY KEY NOT NULL,
                $COL_GROUP_ID TEXT NOT NULL,
                $COL_DEBTOR_ID TEXT NOT NULL,
                $COL_CREDITOR_ID TEXT NOT NULL,
                $COL_AMOUNT_CENTS INTEGER NOT NULL,
                $COL_CURRENCY TEXT NOT NULL DEFAULT 'EUR',
                $COL_PAYMENT_METHOD TEXT NOT NULL,
                $COL_STATUS TEXT NOT NULL DEFAULT 'COMPLETED',
                $COL_CREATED_AT INTEGER NOT NULL,
                $COL_SYNC_STATUS TEXT NOT NULL DEFAULT 'SYNCED',
                FOREIGN KEY ($COL_GROUP_ID) REFERENCES ${GroupsTable.TABLE_NAME}(${GroupsTable.COL_ID}) ON DELETE CASCADE,
                FOREIGN KEY ($COL_DEBTOR_ID) REFERENCES ${UsersTable.TABLE_NAME}(${UsersTable.COL_ID}),
                FOREIGN KEY ($COL_CREDITOR_ID) REFERENCES ${UsersTable.TABLE_NAME}(${UsersTable.COL_ID})
            )
        """
    }

    object SyncQueueTable {
        const val TABLE_NAME = "sync_queue"
        const val COL_QUEUE_ID = "queue_id"
        const val COL_ENTITY_TYPE = "entity_type"
        const val COL_ENTITY_ID = "entity_id"
        const val COL_ACTION = "action"
        const val COL_PAYLOAD_JSON = "payload_json"
        const val COL_RETRY_COUNT = "retry_count"
        const val COL_LAST_ATTEMPT_AT = "last_attempt_at"
        const val COL_ERROR_MESSAGE = "error_message"

        const val CREATE_SQL = """
            CREATE TABLE IF NOT EXISTS $TABLE_NAME (
                $COL_QUEUE_ID INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                $COL_ENTITY_TYPE TEXT NOT NULL,
                $COL_ENTITY_ID TEXT NOT NULL,
                $COL_ACTION TEXT NOT NULL,
                $COL_PAYLOAD_JSON TEXT NOT NULL,
                $COL_RETRY_COUNT INTEGER NOT NULL DEFAULT 0,
                $COL_LAST_ATTEMPT_AT INTEGER,
                $COL_ERROR_MESSAGE TEXT
            )
        """
    }

    val ALL_CREATE_STATEMENTS = listOf(
        UsersTable.CREATE_SQL,
        GroupsTable.CREATE_SQL,
        GroupMembersTable.CREATE_SQL,
        ExpensesTable.CREATE_SQL,
        ExpenseSplitsTable.CREATE_SQL,
        SettlementsTable.CREATE_SQL,
        SyncQueueTable.CREATE_SQL,
        "CREATE INDEX IF NOT EXISTS idx_group_members_user ON ${GroupMembersTable.TABLE_NAME}(${GroupMembersTable.COL_USER_ID})",
        "CREATE INDEX IF NOT EXISTS idx_expenses_group ON ${ExpensesTable.TABLE_NAME}(${ExpensesTable.COL_GROUP_ID})",
        "CREATE INDEX IF NOT EXISTS idx_expense_splits_expense ON ${ExpenseSplitsTable.TABLE_NAME}(${ExpenseSplitsTable.COL_EXPENSE_ID})",
        "CREATE INDEX IF NOT EXISTS idx_settlements_group ON ${SettlementsTable.TABLE_NAME}(${SettlementsTable.COL_GROUP_ID})",
        "CREATE INDEX IF NOT EXISTS idx_sync_queue_entity ON ${SyncQueueTable.TABLE_NAME}(${SyncQueueTable.COL_ENTITY_TYPE}, ${SyncQueueTable.COL_ENTITY_ID})"
    )
}
