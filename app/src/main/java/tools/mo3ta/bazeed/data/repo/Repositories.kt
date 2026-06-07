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

/**
 * Recoverable auth / user-management failures. Each case carries a ready-to-show
 * Arabic [userMessage] so screens don't need to decode strings to pick a UX.
 * [Unknown] preserves the original [cause] for logging.
 */
sealed class AuthException(
    val userMessage: String,
    cause: Throwable? = null,
) : Exception(userMessage, cause) {

    class Validation(message: String) : AuthException(message)
    class InvalidCredentials : AuthException("بيانات الدخول غير صحيحة")
    class UserNotProvisioned : AuthException("الحساب غير مفعّل — تواصل مع الإدارة")
    class NotAuthorized : AuthException("هذا الحساب غير مصرّح له بالدخول إلى لوحة الإدارة")
    class Network : AuthException("تعذّر الاتصال — تحقق من الإنترنت")
    class TooManyAttempts : AuthException("محاولات كثيرة، حاول لاحقاً")
    class EmailInUse : AuthException("هذا البريد مسجّل بالفعل")
    class Unknown(cause: Throwable)
        : AuthException("حدث خطأ غير متوقع، حاول مرة أخرى", cause)
}

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
