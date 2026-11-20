package com.example.getyourmoneyupnotyourfunnyup.data.repository

import com.example.getyourmoneyupnotyourfunnyup.data.local.AppDatabase
import com.example.getyourmoneyupnotyourfunnyup.data.remote.UserDocumentMapper
import com.example.getyourmoneyupnotyourfunnyup.model.ProfileChanges
import com.example.getyourmoneyupnotyourfunnyup.model.User
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface ProfileRepository {
    fun observeProfile(userId: String): Flow<User?>
    /** Returns once the local record/outbox are committed; cloud status arrives through observation. */
    suspend fun requestEmailChange(userId: String, email: String) { error("Email changes are unavailable.") }
    suspend fun save(userId: String, changes: ProfileChanges): User
}

class FirebaseProfileRepository(
    private val database: AppDatabase,
    private val auth: FirebaseAuth = Firebase.auth,
    private val firestore: FirebaseFirestore = Firebase.firestore,
    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : ProfileRepository {
    private val saving = Mutex()

    override fun observeProfile(userId: String): Flow<User?> = database.userDao.observeUserById(userId).map { it?.toDomain() }

    override suspend fun requestEmailChange(userId: String, email: String) {
        val user = auth.currentUser?.takeIf { it.uid == userId } ?: throw SecurityException("Please sign in again.")
        try {
            suspendCancellableCoroutine<Unit> { continuation ->
                user.verifyBeforeUpdateEmail(email).addOnSuccessListener {
                    if (continuation.isActive) continuation.resume(Unit)
                }.addOnFailureListener { error ->
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            }
            check(auth.currentUser?.uid == userId) { "Your session changed. Please sign in again." }
        } catch (recent: com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException) {
            throw IllegalStateException("Log out and log in again, then retry the email change.", recent)
        }
    }

    override suspend fun save(userId: String, changes: ProfileChanges): User = saving.withLock {
        check(auth.currentUser?.uid == userId) { "Please sign in again before saving your profile." }
        require(changes.displayName.isNotBlank()) { "Enter your full name." }
        val (saved, queueId) = withContext(Dispatchers.IO) {
            check(auth.currentUser?.uid == userId) { "Your session has changed. Please sign in again." }
            database.userDao.saveProfileAndEnqueue(userId, changes)
        }
        // Logout can occur after local commit. Keep the durable intent rather than upload as another account.
        if (auth.currentUser?.uid == userId) {
            try {
                firestore.collection(AuthRepository.USERS_COLLECTION).document(userId)
                    .set(UserDocumentMapper.profileUpdateMap(saved.toDomain()), SetOptions.merge())
                    .addOnSuccessListener {
                        ioScope.launch {
                            if (auth.currentUser?.uid == userId) database.userDao.finishProfileUpload(userId, queueId, saved.updatedAt)
                        }
                    }
                    .addOnFailureListener { error ->
                        ioScope.launch {
                            if (auth.currentUser?.uid == userId) database.userDao.finishProfileUpload(
                                userId, queueId, saved.updatedAt, error.localizedMessage ?: "Couldn't upload your profile."
                            )
                        }
                    }
            } catch (error: Exception) {
                withContext(Dispatchers.IO) {
                    database.userDao.finishProfileUpload(userId, queueId, saved.updatedAt, error.localizedMessage ?: "Couldn't upload your profile.")
                }
            }
        }
        saved.toDomain()
    }
}
