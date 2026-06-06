package tools.mo3ta.bazeed.data.repo.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tools.mo3ta.bazeed.data.auth.AuthUser
import tools.mo3ta.bazeed.data.auth.UserRole
import tools.mo3ta.bazeed.data.repo.AuthException
import tools.mo3ta.bazeed.data.repo.AuthRepository
import tools.mo3ta.bazeed.data.repo.UserRepository

/** Client-only AuthRepository backed by [LocalUserStore]. */
class LocalAuthRepository : AuthRepository {

    private val _currentUser = MutableStateFlow<AuthUser?>(null)
    override val currentUser: StateFlow<AuthUser?> = _currentUser.asStateFlow()

    override suspend fun signIn(email: String, password: String): Result<AuthUser> {
        if (email.isBlank() || password.isBlank()) {
            return Result.failure(AuthException("من فضلك أدخل البريد وكلمة المرور"))
        }
        val user = LocalUserStore.verify(email, password)
            ?: return Result.failure(AuthException("بيانات الدخول غير صحيحة"))
        _currentUser.value = user
        return Result.success(user)
    }

    override fun signOut() {
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
        val trimmed = email.trim()
        if (trimmed.isBlank() || !trimmed.contains("@")) {
            return Result.failure(AuthException("بريد إلكتروني غير صالح"))
        }
        if (password.length < 6) {
            return Result.failure(AuthException("كلمة المرور يجب ألا تقل عن ٦ أحرف"))
        }
        if (LocalUserStore.exists(trimmed)) {
            return Result.failure(AuthException("هذا البريد مسجّل بالفعل"))
        }
        val user = AuthUser(
            uid = LocalUserStore.newUid(),
            email = trimmed,
            displayName = displayName.trim().ifBlank { trimmed.substringBefore("@") },
            role = role,
        )
        LocalUserStore.register(user, password)
        return Result.success(user)
    }
}
