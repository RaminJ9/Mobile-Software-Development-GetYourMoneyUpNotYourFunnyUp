package com.example.getyourmoneyupnotyourfunnyup.data.local.dao

import android.content.ContentValues
import com.example.getyourmoneyupnotyourfunnyup.data.local.AppDatabase
import com.example.getyourmoneyupnotyourfunnyup.model.Reminder
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus

class ReminderDao(private val database: AppDatabase) {
    fun get(id: String): Reminder? = query("id = ?", arrayOf(id)).firstOrNull()
    fun forGroup(groupId: String): List<Reminder> = query("group_id = ?", arrayOf(groupId))
    private fun query(selection: String, args: Array<String>): List<Reminder> = buildList {
        database.readableDatabase.query("reminders", null, selection, args, null, null, "created_at DESC").use { c ->
            while (c.moveToNext()) {
                fun text(name: String) = c.getString(c.getColumnIndexOrThrow(name))
                fun number(name: String) = c.getLong(c.getColumnIndexOrThrow(name))
                add(Reminder(text("id"), text("group_id"), text("debtor_id"), text("creditor_id"), number("amount_cents"),
                    text("currency"), text("obligation_id"), number("created_at"), SyncStatus.fromValue(text("sync_status"))))
            }
        }
    }
    fun save(reminder: Reminder) {
        val values = ContentValues().apply {
            put("id", reminder.id); put("group_id", reminder.groupId); put("debtor_id", reminder.debtorId); put("creditor_id", reminder.creditorId)
            put("amount_cents", reminder.amountCents); put("currency", reminder.currency); put("obligation_id", reminder.obligationId)
            put("created_at", reminder.createdAt); put("sync_status", reminder.syncStatus.name)
        }
        val db = database.writableDatabase
        if (db.update("reminders", values, "id = ?", arrayOf(reminder.id)) == 0) db.insertOrThrow("reminders", null, values)
    }
}
