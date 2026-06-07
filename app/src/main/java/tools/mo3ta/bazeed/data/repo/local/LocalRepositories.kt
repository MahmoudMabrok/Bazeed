package tools.mo3ta.bazeed.data.repo.local

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tools.mo3ta.bazeed.data.auth.AuthUser
import tools.mo3ta.bazeed.data.auth.UserRole
import tools.mo3ta.bazeed.data.repo.AuthException
import tools.mo3ta.bazeed.data.repo.AuthRepository
import tools.mo3ta.bazeed.data.repo.UserRepository

private const val TAG = "LocalAuth"

/** Client-only AuthRepository backed by [LocalUserStore]. */
class LocalAuthRepository : AuthRepository {

    private val _currentUser = MutableStateFlow<AuthUser?>(null)
    override val currentUser: StateFlow<AuthUser?> = _currentUser.asStateFlow()

    override suspend fun signIn(email: String, password: String): Result<AuthUser> {
        Log.d(TAG, "signIn attempt: $email")
        if (email.isBlank() || password.isBlank()) {
            Log.w(TAG, "signIn failed: Validation (empty fields)")
            return Result.failure(AuthException.Validation("من فضلك أدخل البريد وكلمة المرور"))
        }
        val user = LocalUserStore.verify(email, password)
        if (user == null) {
            Log.w(TAG, "signIn failed: InvalidCredentials for $email")
            return Result.failure(AuthException.InvalidCredentials())
        }
        _currentUser.value = user
        Log.d(TAG, "signed in as ${user.uid} (${user.role})")
        return Result.success(user)
    }

    override fun signOut() {
        Log.d(TAG, "signOut")
        _currentUser.value = null
    }
}

/** Client-only UserRepository backed by [LocalUserStore]. */
class LocalUserRepository : UserRepository {

    override val users: StateFlow<List<AuthUser>> = LocalUserStore.users

    override suspend fun createUser(
        email: String,
        password: String,
        displayName: String,
        role: UserRole,
    ): Result<AuthUser> {
        Log.d(TAG, "createUser attempt: $email (role=$role)")
        val trimmed = email.trim()
        if (trimmed.isBlank() || !trimmed.contains("@")) {
            Log.w(TAG, "createUser failed: Validation (bad email)")
            return Result.failure(AuthException.Validation("بريد إلكتروني غير صالح"))
        }
        if (password.length < 6) {
            Log.w(TAG, "createUser failed: Validation (short password)")
            return Result.failure(AuthException.Validation("كلمة المرور يجب ألا تقل عن ٦ أحرف"))
        }
        if (LocalUserStore.exists(trimmed)) {
            Log.w(TAG, "createUser failed: EmailInUse for $trimmed")
            return Result.failure(AuthException.EmailInUse())
        }
        val user = AuthUser(
            uid = LocalUserStore.newUid(),
            email = trimmed,
            displayName = displayName.trim().ifBlank { trimmed.substringBefore("@") },
            role = role,
        )
        LocalUserStore.register(user, password)
        Log.d(TAG, "createUser ok: ${user.uid}")
        return Result.success(user)
    }
}
