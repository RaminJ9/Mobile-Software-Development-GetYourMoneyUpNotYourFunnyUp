package com.example.getyourmoneyupnotyourfunnyup.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.example.getyourmoneyupnotyourfunnyup.data.repository.AuthRepository
import com.example.getyourmoneyupnotyourfunnyup.data.repository.LedgerRepository
import com.example.getyourmoneyupnotyourfunnyup.model.User

/**
 * Who is signed in. Restores an existing Firebase session on launch (so the app opens on Home), and
 * copies the account's name and email into the ledger's "me" profile.
 */
class SessionViewModel(
    private val authRepository: AuthRepository,
    private val ledger: LedgerRepository,
) : ViewModel() {

    /** True when Firebase already has a signed-in user at launch. */
    val startSignedIn: Boolean = authRepository.currentAuthUid != null

    init {
        val uid = authRepository.currentAuthUid
        if (uid != null) {
            val email = authRepository.currentAuthEmail.orEmpty()
            val name = authRepository.currentAuthDisplayName ?: email.substringBefore("@")
            ledger.signIn(name, email)
            authRepository.fetchOrCreateUserDocument(uid, email, name) { result ->
                result.onSuccess { ledger.signIn(it.displayName, it.email) }
            }
        }
    }

    fun onAuthenticated(user: User, phone: String?) {
        ledger.signIn(user.displayName, user.email)
        if (phone != null) ledger.updateProfile(user.displayName, user.email, phone)
    }

    fun signOut() = authRepository.signOut()
}
