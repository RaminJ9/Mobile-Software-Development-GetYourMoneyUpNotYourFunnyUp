package com.example.getyourmoneyupnotyourfunnyup.data.repository

import com.example.getyourmoneyupnotyourfunnyup.model.User
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

/** Account operations used by the authentication ViewModel; Firebase stays in AuthRepository. */
interface AuthSessionSource {
    val currentUid: String?
    val currentEmail: String?
    fun restore(uid: String, email: String, onResult: (Result<User>) -> Unit)
    fun login(email: String, password: String, onResult: (Result<User>) -> Unit)
    fun register(name: String, email: String, password: String, onResult: (Result<User>) -> Unit)
    fun observeUser(user: User, onError: (String) -> Unit): Flow<User>
    fun signOut()
}

class RepositoryAuthSessionSource(private val repository: AuthRepository) : AuthSessionSource {
    override val currentUid: String? get() = repository.currentAuthUid
    override val currentEmail: String? get() = repository.currentAuthEmail

    override fun restore(uid: String, email: String, onResult: (Result<User>) -> Unit) =
        repository.fetchOrCreateUserDocument(uid, email, email.substringBefore("@").ifEmpty { "User" }, onResult)

    override fun login(email: String, password: String, onResult: (Result<User>) -> Unit) =
        repository.loginUser(email, password, onResult)

    override fun register(name: String, email: String, password: String, onResult: (Result<User>) -> Unit) =
        repository.registerUser(name, email, password, onResult)

    override fun observeUser(user: User, onError: (String) -> Unit): Flow<User> = callbackFlow {
        val local = launch {
            repository.observeUser(user.id).collect { updated ->
                if (updated != null) send(updated)
            }
        }
        val listener = repository.listenToUserDocument(user.id, user.email, user.displayName) {
            onError(it)
        }
        awaitClose {
            listener.remove()
            local.cancel()
        }
    }

    override fun signOut() = repository.signOut()
}
