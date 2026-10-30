package com.example.getyourmoneyupnotyourfunnyup.data.local

import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.SyncAction
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.SyncEntityType
import com.example.getyourmoneyupnotyourfunnyup.model.GroupRole
import com.example.getyourmoneyupnotyourfunnyup.model.SettlementStatus
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus

/**
 * Type converters between domain/entity enums and SQLite primitive values.
 */
object Converters {
    fun fromSyncStatus(status: SyncStatus): String = status.name
    fun toSyncStatus(value: String?): SyncStatus = SyncStatus.fromValue(value)

    fun fromGroupRole(role: GroupRole): String = role.name
    fun toGroupRole(value: String?): GroupRole = GroupRole.fromValue(value)

    fun fromSettlementStatus(status: SettlementStatus): String = status.name
    fun toSettlementStatus(value: String?): SettlementStatus = SettlementStatus.fromValue(value)

    fun fromSyncEntityType(type: SyncEntityType): String = type.name
    fun toSyncEntityType(value: String?): SyncEntityType = SyncEntityType.fromValue(value)

    fun fromSyncAction(action: SyncAction): String = action.name
    fun toSyncAction(value: String?): SyncAction = SyncAction.fromValue(value)
}
