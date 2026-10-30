package com.example.getyourmoneyupnotyourfunnyup.data.repository

import com.example.getyourmoneyupnotyourfunnyup.data.local.dao.SyncQueueDao
import com.example.getyourmoneyupnotyourfunnyup.data.local.dao.UserDao
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.SyncAction
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.SyncEntityType
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.SyncQueueEntity
import com.example.getyourmoneyupnotyourfunnyup.data.local.entity.UserEntity
import com.example.getyourmoneyupnotyourfunnyup.model.NotificationSettings
import com.example.getyourmoneyupnotyourfunnyup.model.SyncStatus
import com.example.getyourmoneyupnotyourfunnyup.model.User
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Offline-first repository handling Firebase Authentication and `/users/{userId}`
 * synchronization between Cloud Firestore and the local SQLite database.
 */
class AuthRepository(
    private val userDao: UserDao,
    private val syncQueueDao: SyncQueueDao,
    private val auth: FirebaseAuth = Firebase.auth,
    private val firestore: FirebaseFirestore = Firebase.firestore,
    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    val currentAuthUid: String?
        get() = auth.currentUser?.uid

    val currentAuthEmail: String?
        get() = auth.currentUser?.email

    fun observeUser(userId: String): Flow<User?> =
        userDao.observeUserById(userId).map { it?.toDomain() }

    /**
     * Registers a new account in Firebase Auth and inserts the user document into
     * Firestore `/users/{uid}` and local SQLite `users`.
     */
    fun registerUser(
        displayName: String,
        email: String,
        password: String,
        onResult: (Result<User>) -> Unit
    ) {
        val trimmedName = displayName.trim().ifEmpty { email.trim().substringBefore("@") }
        val trimmedEmail = email.trim()

        auth.createUserWithEmailAndPassword(trimmedEmail, password)
            .addOnSuccessListener { authResult ->
                val firebaseUser = authResult.user
                val uid = firebaseUser?.uid
                if (uid == null) {
                    onResult(Result.failure(IllegalStateException("Missing user UID after registration.")))
                    return@addOnSuccessListener
                }

                // Update Firebase Auth display name in the background
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(trimmedName)
                    .build()
                firebaseUser.updateProfile(profileUpdates)

                val now = System.currentTimeMillis()
                val newUser = User(
                    id = uid,
                    email = trimmedEmail,
                    displayName = trimmedName,
                    photoUrl = null,
                    count = 0,
                    notificationSettings = NotificationSettings(),
                    syncStatus = SyncStatus.PENDING_INSERT,
                    updatedAt = now
                )

                // 1. Save locally first (SSOT)
                ioScope.launch {
                    userDao.upsertUser(UserEntity.fromDomain(newUser))
                }

                // 2. Insert into Firestore `/users/{uid}`
                firestore.collection(USERS_COLLECTION)
                    .document(uid)
                    .set(newUser.toFirestoreMap())
                    .addOnSuccessListener {
                        val syncedUser = newUser.copy(syncStatus = SyncStatus.SYNCED)
                        ioScope.launch {
                            userDao.upsertUser(UserEntity.fromDomain(syncedUser))
                        }
                        onResult(Result.success(syncedUser))
                    }
                    .addOnFailureListener { error ->
                        // Offline resilience: queue for background sync and still let the user proceed
                        ioScope.launch {
                            userDao.updateSyncStatus(uid, SyncStatus.FAILED)
                            syncQueueDao.enqueue(
                                SyncQueueEntity(
                                    entityType = SyncEntityType.USER,
                                    entityId = uid,
                                    action = SyncAction.CREATE,
                                    payloadJson = """{"id":"$uid","email":"$trimmedEmail","displayName":"$trimmedName","count":0}""",
                                    errorMessage = error.localizedMessage
                                )
                            )
                        }
                        onResult(Result.success(newUser.copy(syncStatus = SyncStatus.FAILED)))
                    }
            }
            .addOnFailureListener { error ->
                onResult(Result.failure(error))
            }
    }

    /**
     * Authenticates an existing user via Firebase Auth and fetches their `/users/{uid}`
     * document (including their saved `count`) from Cloud Firestore.
     */
    fun loginUser(
        email: String,
        password: String,
        onResult: (Result<User>) -> Unit
    ) {
        val trimmedEmail = email.trim()

        auth.signInWithEmailAndPassword(trimmedEmail, password)
            .addOnSuccessListener { authResult ->
                val firebaseUser = authResult.user
                val uid = firebaseUser?.uid
                if (uid == null) {
                    onResult(Result.failure(IllegalStateException("Missing user UID after login.")))
                    return@addOnSuccessListener
                }

                fetchOrCreateUserDocument(
                    uid = uid,
                    fallbackEmail = firebaseUser.email ?: trimmedEmail,
                    fallbackName = firebaseUser.displayName ?: trimmedEmail.substringBefore("@"),
                    onResult = onResult
                )
            }
            .addOnFailureListener { error ->
                onResult(Result.failure(error))
            }
    }

    /**
     * Fetches the user document from `/users/{uid}` in Firestore, or initializes it
     * if the user authenticated before a Firestore profile document was created.
     */
    fun fetchOrCreateUserDocument(
        uid: String,
        fallbackEmail: String,
        fallbackName: String,
        onResult: (Result<User>) -> Unit
    ) {
        firestore.collection(USERS_COLLECTION)
            .document(uid)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot != null && snapshot.exists()) {
                    val user = snapshot.toUserDomain(
                        uid = uid,
                        fallbackEmail = fallbackEmail,
                        fallbackName = fallbackName
                    )
                    ioScope.launch {
                        userDao.upsertUser(UserEntity.fromDomain(user))
                    }
                    onResult(Result.success(user))
                } else {
                    // User exists in Firebase Auth but not yet in `/users/{uid}` — insert it now
                    val newUser = User(
                        id = uid,
                        email = fallbackEmail,
                        displayName = fallbackName,
                        count = 0,
                        syncStatus = SyncStatus.SYNCED,
                        updatedAt = System.currentTimeMillis()
                    )
                    firestore.collection(USERS_COLLECTION)
                        .document(uid)
                        .set(newUser.toFirestoreMap())
                        .addOnSuccessListener {
                            ioScope.launch {
                                userDao.upsertUser(UserEntity.fromDomain(newUser))
                            }
                            onResult(Result.success(newUser))
                        }
                        .addOnFailureListener {
                            val pendingUser = newUser.copy(syncStatus = SyncStatus.FAILED)
                            ioScope.launch {
                                userDao.upsertUser(UserEntity.fromDomain(pendingUser))
                            }
                            onResult(Result.success(pendingUser))
                        }
                }
            }
            .addOnFailureListener { error ->
                // Offline fallback: check local SQLite cache
                ioScope.launch {
                    val cached = userDao.getUserById(uid)?.toDomain()
                    if (cached != null) {
                        onResult(Result.success(cached))
                    } else {
                        val offlineUser = User(
                            id = uid,
                            email = fallbackEmail,
                            displayName = fallbackName,
                            count = 0,
                            syncStatus = SyncStatus.FAILED,
                            updatedAt = System.currentTimeMillis()
                        )
                        userDao.upsertUser(UserEntity.fromDomain(offlineUser))
                        onResult(Result.failure(error))
                    }
                }
            }
    }

    /**
     * Listens for real-time updates to `/users/{uid}` in Firestore and syncs changes into SQLite.
     */
    fun listenToUserDocument(
        uid: String,
        fallbackEmail: String,
        fallbackName: String,
        onError: (String) -> Unit
    ): ListenerRegistration {
        return firestore.collection(USERS_COLLECTION)
            .document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error.localizedMessage ?: "Firestore listener error")
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val remoteUser = snapshot.toUserDomain(
                        uid = uid,
                        fallbackEmail = fallbackEmail,
                        fallbackName = fallbackName
                    )
                    ioScope.launch {
                        userDao.upsertUser(UserEntity.fromDomain(remoteUser))
                    }
                }
            }
    }

    /**
     * Increments the user's counter and saves it against `/users/{uid}` in both
     * local SQLite and Cloud Firestore.
     */
    fun incrementUserCount(
        user: User,
        onResult: (Result<Int>) -> Unit
    ) {
        val nextCount = if (user.count != 67) user.count + 1 else user.count
        val now = System.currentTimeMillis()

        // 1. Optimistic local SQLite update with PENDING_UPDATE
        ioScope.launch {
            val existing = userDao.getUserById(user.id)
            if (existing != null) {
                userDao.updateUserCount(user.id, nextCount, SyncStatus.PENDING_UPDATE)
            } else {
                userDao.upsertUser(
                    UserEntity.fromDomain(
                        user.copy(
                            count = nextCount,
                            syncStatus = SyncStatus.PENDING_UPDATE,
                            updatedAt = now
                        )
                    )
                )
            }
        }

        // 2. Persist count against `/users/{uid}` in Firestore
        val updates = mapOf(
            "id" to user.id,
            "email" to user.email,
            "displayName" to user.displayName,
            "count" to nextCount,
            "updatedAt" to now
        )

        firestore.collection(USERS_COLLECTION)
            .document(user.id)
            .set(updates, SetOptions.merge())
            .addOnSuccessListener {
                ioScope.launch {
                    userDao.updateUserCount(user.id, nextCount, SyncStatus.SYNCED)
                }
                onResult(Result.success(nextCount))
            }
            .addOnFailureListener { error ->
                ioScope.launch {
                    userDao.updateUserCount(user.id, nextCount, SyncStatus.FAILED)
                    syncQueueDao.enqueue(
                        SyncQueueEntity(
                            entityType = SyncEntityType.USER,
                            entityId = user.id,
                            action = SyncAction.UPDATE,
                            payloadJson = """{"id":"${user.id}","count":$nextCount,"updatedAt":$now}""",
                            errorMessage = error.localizedMessage
                        )
                    )
                }
                onResult(Result.failure(error))
            }
    }

    fun signOut() {
        auth.signOut()
    }

    private fun User.toFirestoreMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "email" to email,
        "displayName" to displayName,
        "photoUrl" to photoUrl,
        "count" to count,
        "notificationSettings" to mapOf(
            "expenseAlerts" to notificationSettings.expenseAlerts,
            "paymentReminders" to notificationSettings.paymentReminders
        ),
        "updatedAt" to updatedAt
    )

    private fun DocumentSnapshot.toUserDomain(
        uid: String,
        fallbackEmail: String,
        fallbackName: String
    ): User {
        val notifMap = get("notificationSettings") as? Map<*, *>
        val expenseAlerts = (notifMap?.get("expenseAlerts") as? Boolean) ?: true
        val paymentReminders = (notifMap?.get("paymentReminders") as? Boolean) ?: true

        return User(
            id = getString("id") ?: uid,
            email = getString("email") ?: fallbackEmail,
            displayName = getString("displayName") ?: fallbackName,
            photoUrl = getString("photoUrl"),
            count = getLong("count")?.toInt() ?: 0,
            notificationSettings = NotificationSettings(
                expenseAlerts = expenseAlerts,
                paymentReminders = paymentReminders
            ),
            syncStatus = SyncStatus.SYNCED,
            updatedAt = getLong("updatedAt") ?: System.currentTimeMillis()
        )
    }

    companion object {
        const val USERS_COLLECTION = "users"
    }
}
