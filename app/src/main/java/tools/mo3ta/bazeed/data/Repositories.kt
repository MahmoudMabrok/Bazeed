package tools.mo3ta.bazeed.data

import tools.mo3ta.bazeed.data.repo.AuthRepository
import tools.mo3ta.bazeed.data.repo.ContentRepository
import tools.mo3ta.bazeed.data.repo.UserRepository
import tools.mo3ta.bazeed.data.repo.firebase.FirestoreContentRepository
import tools.mo3ta.bazeed.data.repo.local.LocalAuthRepository
import tools.mo3ta.bazeed.data.repo.local.LocalUserRepository

/**
 * The one place repository implementations are chosen.
 *
 * Auth/users are still local in this PR; flip them per the prior spec's Task 3
 * before announcements work end-to-end (Firestore rules require an authenticated,
 * provisioned user).
 */
object Repositories {
    val auth: AuthRepository = LocalAuthRepository()
    val users: UserRepository = LocalUserRepository()
    val content: ContentRepository = FirestoreContentRepository()
}
