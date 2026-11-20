package com.example.getyourmoneyupnotyourfunnyup.data.remote

import com.example.getyourmoneyupnotyourfunnyup.model.Reminder
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus

internal object ReminderMapper {
    fun toMap(r: Reminder): Map<String, Any?> = mapOf("id" to r.id, "groupId" to r.groupId, "debtorId" to r.debtorId,
        "creditorId" to r.creditorId, "amountCents" to r.amountCents, "currency" to r.currency,
        "obligationId" to r.obligationId, "createdAt" to r.createdAt)
    fun fromMap(data: Map<String, Any?>, id: String): Reminder {
        fun text(key: String) = data[key] as? String ?: error("Missing reminder $key")
        return Reminder(id, text("groupId"), text("debtorId"), text("creditorId"),
            (data["amountCents"] as Number).toLong(), text("currency"), text("obligationId"),
            (data["createdAt"] as Number).toLong(), SyncStatus.SYNCED).also {
                require(it.amountCents > 0 && it.currency == "DKK" && it.debtorId != it.creditorId)
            }
    }
}
