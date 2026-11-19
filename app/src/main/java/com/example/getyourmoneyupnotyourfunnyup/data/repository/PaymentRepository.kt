package com.example.getyourmoneyupnotyourfunnyup.data.repository

import android.content.ContentValues
import androidx.core.database.sqlite.transaction
import com.example.getyourmoneyupnotyourfunnyup.data.local.AppDatabase
import com.example.getyourmoneyupnotyourfunnyup.data.local.DatabaseContract.SettlementsTable as Payments
import com.example.getyourmoneyupnotyourfunnyup.data.local.DatabaseContract.SyncQueueTable as Queue
import com.example.getyourmoneyupnotyourfunnyup.model.BalanceCalculator
import com.example.getyourmoneyupnotyourfunnyup.model.DebtTransfer
import com.example.getyourmoneyupnotyourfunnyup.model.Settlement
import com.example.getyourmoneyupnotyourfunnyup.model.SettlementCalculator
import com.example.getyourmoneyupnotyourfunnyup.model.SettlementStatus
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** S8-pay: local simulated repayment. The caller retains requestId across retries of one confirmation. */
interface PaymentRepository {
    suspend fun pay(accountId: String, groupId: String, transfer: DebtTransfer, requestId: String): Settlement
    suspend fun markAsPaid(accountId: String, groupId: String, transfer: DebtTransfer, requestId: String): Settlement
}

/** Uses the existing outbox; uploading and notification delivery remain the sync module's job. */
class LocalPaymentRepository(
    private val database: AppDatabase,
    private val currentAccountId: () -> String?
) : PaymentRepository {
    override suspend fun pay(accountId: String, groupId: String, transfer: DebtTransfer, requestId: String): Settlement =
        record(accountId, groupId, transfer, requestId, creditorConfirmation = false)

    override suspend fun markAsPaid(accountId: String, groupId: String, transfer: DebtTransfer, requestId: String): Settlement =
        record(accountId, groupId, transfer, requestId, creditorConfirmation = true)

    private suspend fun record(
        accountId: String, groupId: String, transfer: DebtTransfer, requestId: String, creditorConfirmation: Boolean
    ): Settlement = withContext(Dispatchers.IO) {
            require(accountId.isNotBlank() && groupId.isNotBlank() && requestId.isNotBlank()) { "Account, group and request IDs are required." }
            require(transfer.amountCents > 0 && transfer.debtorId != transfer.creditorId) { "Invalid repayment." }
            if (creditorConfirmation) {
                if (transfer.creditorId != accountId) throw SecurityException("Only the creditor can mark this debt as paid.")
            } else if (transfer.debtorId != accountId) throw SecurityException("You can only pay your own debt.")
            checkAccount(accountId)
            // Serialize validation and both inserts. A competing confirmation sees the updated ledger.
            val result = database.writableDatabase.transaction {
                checkAccount(accountId)
                val group = database.groupDao.getGroupById(groupId)
                    ?: throw IllegalArgumentException("This group is no longer available.")
                val members = database.groupDao.getMembersForGroup(groupId)
                if (members.none { it.userId == accountId }) throw SecurityException("You are no longer a member of this group.")
                require(group.currency == "DKK" && transfer.currency == group.currency) { "This payment requires a DKK group." }
                val settlements = database.settlementDao.getSettlementsForGroup(groupId).map { it.toDomain() }
                val existing = settlements.firstOrNull { it.id == requestId }
                if (existing != null) {
                    require(existing.debtorId == transfer.debtorId && existing.creditorId == transfer.creditorId &&
                        existing.amountCents == transfer.amountCents && existing.currency == transfer.currency &&
                        existing.paymentMethod == "Simulated" && existing.status == SettlementStatus.COMPLETED) {
                        "This request ID was already used for a different payment."
                    }
                    checkAccount(accountId)
                    existing
                } else {
                    val splits = database.expenseDao.getSplitsForGroup(groupId).groupBy { it.expenseId }
                    val expenses = database.expenseDao.getActiveExpensesForGroup(groupId).map {
                        it.toDomain(splits[it.id].orEmpty().map { share -> share.toDomain() })
                    }
                    val balances = BalanceCalculator.calculate(groupId, group.currency, members.map { it.userId }, expenses, settlements)
                    require(transfer in SettlementCalculator.suggest(balances)) {
                        "This balance changed. Review the latest payment suggestions."
                    }
                    val payment = Settlement(
                        id = requestId, groupId = groupId, debtorId = transfer.debtorId,
                        creditorId = transfer.creditorId, amountCents = transfer.amountCents,
                        currency = transfer.currency, paymentMethod = "Simulated",
                        status = SettlementStatus.COMPLETED, syncStatus = SyncStatus.PENDING_INSERT
                    )
                    // insertOrThrow avoids silently losing a record or replacing an unrelated request.
                    insertOrThrow(Payments.TABLE_NAME, null, ContentValues().apply {
                        put(Payments.COL_ID, payment.id)
                        put(Payments.COL_GROUP_ID, payment.groupId)
                        put(Payments.COL_DEBTOR_ID, payment.debtorId)
                        put(Payments.COL_CREDITOR_ID, payment.creditorId)
                        put(Payments.COL_AMOUNT_CENTS, payment.amountCents)
                        put(Payments.COL_CURRENCY, payment.currency)
                        put(Payments.COL_PAYMENT_METHOD, payment.paymentMethod)
                        put(Payments.COL_STATUS, payment.status.name)
                        put(Payments.COL_CREATED_AT, payment.createdAt)
                        put(Payments.COL_SYNC_STATUS, payment.syncStatus.name)
                    })
                    val payload = JSONObject().apply {
                        put("id", payment.id); put("groupId", payment.groupId)
                        put("debtorId", payment.debtorId); put("creditorId", payment.creditorId)
                        put("amountCents", payment.amountCents); put("currency", payment.currency)
                        put("paymentMethod", payment.paymentMethod); put("status", payment.status.name)
                        put("createdAt", payment.createdAt)
                    }.toString()
                    insertOrThrow(Queue.TABLE_NAME, null, ContentValues().apply {
                        put(Queue.COL_ENTITY_TYPE, "SETTLEMENT")
                        put(Queue.COL_ENTITY_ID, payment.id)
                        put(Queue.COL_ACTION, "CREATE")
                        put(Queue.COL_PAYLOAD_JSON, payload)
                        put(Queue.COL_RETRY_COUNT, 0)
                    })
                    checkAccount(accountId)
                    payment
                }
            }
            // Publish only after commit, so observers cannot read an uncommitted or partial save.
            database.notifyTableChanged(Payments.TABLE_NAME, Queue.TABLE_NAME)
            result
        }

    private fun checkAccount(accountId: String) {
        if (currentAccountId() != accountId) throw SecurityException("Sign in again before recording a payment.")
    }
}
