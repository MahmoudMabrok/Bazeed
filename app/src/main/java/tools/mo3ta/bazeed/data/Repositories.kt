package tools.mo3ta.bazeed.data

import android.content.Context
import tools.mo3ta.bazeed.data.repo.AuthRepository
import tools.mo3ta.bazeed.data.repo.UserRepository
import tools.mo3ta.bazeed.data.repo.firebase.FirebaseAuthRepository
import tools.mo3ta.bazeed.data.repo.firebase.FirestoreUserRepository

/**
 * The one place repository implementations are chosen. Must be initialized once
 * from [tools.mo3ta.bazeed.BazeedApp.onCreate] before any screen reads it.
 *
 * Currently wired to the Firebase (client-only, no Cloud Functions) impls. To
 * fall back to the local in-memory impls (e.g. for UI work without Firebase),
 * swap the two assignments in [init] for `LocalAuthRepository()` /
 * `LocalUserRepository()` — no screen or flavor code changes.
 */
object Repositories {

    lateinit var auth: AuthRepository
        private set

    lateinit var users: UserRepository
        private set

    fun init(context: Context) {
        if (::auth.isInitialized) return
        val appContext = context.applicationContext
        auth = FirebaseAuthRepository()
        users = FirestoreUserRepository(appContext)
    }
}
