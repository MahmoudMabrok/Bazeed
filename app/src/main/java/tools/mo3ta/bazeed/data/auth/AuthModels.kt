package tools.mo3ta.bazeed.data.auth

/** Access level for a Bazeed account. Stored on the user record. */
enum class UserRole { ADMIN, USER }

/**
 * A provisioned account. There is no public sign-up — every account is created
 * by an admin. In the local (client-only) build this lives in memory; the future
 * Firebase build maps it to a `users/{uid}` Firestore document.
 */
data class AuthUser(
    val uid: String,
    val email: String,
    val displayName: String,
    val role: UserRole,
    val active: Boolean = true,
)
