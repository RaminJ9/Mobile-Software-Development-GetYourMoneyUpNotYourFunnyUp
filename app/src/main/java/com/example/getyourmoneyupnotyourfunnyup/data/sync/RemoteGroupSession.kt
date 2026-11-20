package com.example.getyourmoneyupnotyourfunnyup.data.sync

import com.example.getyourmoneyupnotyourfunnyup.data.local.AppDatabase
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Account-lifetime listeners feed the local SSOT. No UI reads directly from Firestore. */
class RemoteGroupSession(
    database: AppDatabase, private val accountId: String,
    private val currentAccountId: () -> String?, private val firestore: FirebaseFirestore,
    private val onError: (String?) -> Unit = {}
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val cache = RemoteLedgerCache(database, accountId, currentAccountId)
    private val children = mutableMapOf<String, List<ListenerRegistration>>()
    private val lock = Mutex()
    private val groupListener = firestore.collection("groups").whereArrayContains("memberIds", accountId)
        .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) { onError("Cloud updates unavailable. Showing saved data."); return@addSnapshotListener }
            if (snapshot == null || currentAccountId() != accountId) return@addSnapshotListener
            scope.launch {
                lock.withLock {
                    try {
                        snapshot.documents.forEach { doc ->
                            cache.group(doc.id, doc.data.orEmpty())
                            if (doc.id !in children && currentAccountId() == accountId) children[doc.id] = listen(doc.id)
                        }
                        if (!snapshot.metadata.isFromCache && !snapshot.metadata.hasPendingWrites()) {
                            val ids = snapshot.documents.map { it.id }.toSet()
                            cache.revokeAbsentGroups(ids)
                            children.keys.filter { it !in ids }.toList().forEach { id -> children.remove(id)?.forEach { it.remove() } }
                        }
                        onError(null)
                    } catch (cancelled: CancellationException) { throw cancelled
                    } catch (_: Exception) { onError("Couldn't read a cloud update. Showing saved data.") }
                }
            }
        }
    private fun listen(groupId: String): List<ListenerRegistration> = listOf("expenses", "settlements", "reminders").map { collection ->
        firestore.collection("groups").document(groupId).collection(collection)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) { onError("Cloud updates unavailable. Showing saved data."); return@addSnapshotListener }
                if (snapshot == null || currentAccountId() != accountId) return@addSnapshotListener
                scope.launch {
                    lock.withLock {
                        try {
                            snapshot.documentChanges.forEach { change ->
                                if (change.type == DocumentChange.Type.REMOVED) {
                                    if (!snapshot.metadata.isFromCache) cache.removed(groupId, collection, change.document.id)
                                } else when (collection) {
                                    "expenses" -> cache.expense(groupId, change.document.id, change.document.data)
                                    "settlements" -> cache.settlement(groupId, change.document.id, change.document.data)
                                    "reminders" -> cache.reminder(groupId, change.document.id, change.document.data)
                                }
                            }
                        } catch (cancelled: CancellationException) { throw cancelled
                        } catch (_: Exception) { onError("Couldn't read a cloud update. Showing saved data.") }
                    }
                }
            }
    }
    override fun close() {
        groupListener.remove(); children.values.flatten().forEach { it.remove() }; children.clear(); scope.cancel()
    }
}
