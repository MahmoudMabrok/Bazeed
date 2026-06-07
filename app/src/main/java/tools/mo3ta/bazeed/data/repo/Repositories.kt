package tools.mo3ta.bazeed.data.repo

import kotlinx.coroutines.flow.StateFlow
import tools.mo3ta.bazeed.data.Announcement
import tools.mo3ta.bazeed.data.auth.AuthUser
import tools.mo3ta.bazeed.data.auth.UserRole

/**
 * Login + current session. Implemented client-only today (no API, no Cloud
 * Functions); a Firebase Auth implementation can be dropped in later behind
 * the same interface without touching screens.
 */
interface AuthRepository {
    /** The signed-in user, or null when logged out. Drives top-level routing. */
    val currentUser: StateFlow<AuthUser?>

    /** Admin-provisioned login only — there is no registration path here. */
    suspend fun signIn(email: String, password: String): Result<AuthUser>

    fun signOut()
}

/**
 * Admin-side user management. Customers never see an implementation of this —
 * it is only wired into the admin flavor.
 */
interface UserRepository {
    /** All provisioned accounts, observable for the admin user list. */
    val users: StateFlow<List<AuthUser>>

    /**
     * Create an account with an admin-set temporary password. In the Firebase
     * build this is the secondary-FirebaseApp creation path; here it writes to
     * the in-memory store. The caller (admin UI) is responsible for handing the
     * temp password to the customer.
     */
    suspend fun createUser(
        email: String,
        password: String,
        displayName: String,
        role: UserRole = UserRole.USER,
    ): Result<AuthUser>
}

/** Thrown by repositories for recoverable auth/validation failures. */
class AuthException(message: String) : Exception(message)

/**
 * Announcement CRUD. Admin app writes; customer app reads via a live snapshot.
 * Production impl is FirestoreContentRepository; LocalContentRepository is for unit tests.
 */
interface ContentRepository {
    val announcements: StateFlow<List<Announcement>>
    suspend fun create(a: Announcement): Result<Announcement>
    suspend fun update(id: String, a: Announcement): Result<Unit>
    suspend fun delete(id: String): Result<Unit>
}
