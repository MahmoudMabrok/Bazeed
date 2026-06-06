package tools.mo3ta.bazeed.data

import tools.mo3ta.bazeed.data.repo.AuthRepository
import tools.mo3ta.bazeed.data.repo.UserRepository
import tools.mo3ta.bazeed.data.repo.local.LocalAuthRepository
import tools.mo3ta.bazeed.data.repo.local.LocalUserRepository

/**
 * The one place repository implementations are chosen. Client-only today.
 *
 * To move to Firebase later, swap these two lines for the Firebase
 * implementations (e.g. `FirebaseAuthRepository()` / `FirestoreUserRepository()`)
 * — no screen or flavor code changes, because everything depends on the
 * interfaces in [tools.mo3ta.bazeed.data.repo].
 */
object Repositories {
    val auth: AuthRepository = LocalAuthRepository()
    val users: UserRepository = LocalUserRepository()
}
