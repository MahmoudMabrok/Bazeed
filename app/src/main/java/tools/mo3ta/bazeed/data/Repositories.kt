package tools.mo3ta.bazeed.data

import android.content.Context
import tools.mo3ta.bazeed.data.repo.AuthRepository
import tools.mo3ta.bazeed.data.repo.ContentRepository
import tools.mo3ta.bazeed.data.repo.PharmacyStatusRepository
import tools.mo3ta.bazeed.data.repo.UserRepository
import tools.mo3ta.bazeed.data.repo.firebase.FirebaseAuthRepository
import tools.mo3ta.bazeed.data.repo.firebase.FirestoreContentRepository
import tools.mo3ta.bazeed.data.repo.firebase.FirestorePharmacyStatusRepository
import tools.mo3ta.bazeed.data.repo.firebase.FirestoreUserRepository

/**
 * Production wiring: Firebase Auth + Firestore. Call [init] once from
 * `Application.onCreate` before any composable reads the fields.
 *
 * The Local* impls (LocalAuthRepository, LocalUserRepository, LocalContentRepository)
 * remain in the codebase as test utilities and are no longer wired in production.
 */
object Repositories {

    lateinit var auth: AuthRepository
        private set
    lateinit var users: UserRepository
        private set
    lateinit var content: ContentRepository
        private set
    lateinit var pharmacyStatus: PharmacyStatusRepository
        private set

    /** Idempotent — safe to call multiple times (subsequent calls no-op). */
    fun init(applicationContext: Context) {
        if (::auth.isInitialized) return
        auth = FirebaseAuthRepository()
        users = FirestoreUserRepository(applicationContext)
        content = FirestoreContentRepository()
        pharmacyStatus = FirestorePharmacyStatusRepository()
    }
}
