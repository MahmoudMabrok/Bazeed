package tools.mo3ta.bazeed.data.repo.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tools.mo3ta.bazeed.data.auth.AuthUser
import tools.mo3ta.bazeed.data.repo.AuthException
import tools.mo3ta.bazeed.data.repo.AuthRepository

/**
 * Firebase-backed [AuthRepository]. Login uses Firebase Auth; the role and
 * profile come from the `users/{uid}` Firestore document (NOT Auth custom
 * claims, which would require the Admin SDK / Cloud Functions). An account that
 * authenticates but has no `users/{uid}` doc is treated as unprovisioned and
 * signed back out.
 */
class FirebaseAuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : AuthRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _currentUser = MutableStateFlow<AuthUser?>(null)
    override val currentUser: StateFlow<AuthUser?> = _currentUser.asStateFlow()

    init {
        // Restore session on cold start and keep the flow in sync with sign-out.
        auth.addAuthStateListener { fa ->
            val fbUser = fa.currentUser
            if (fbUser == null) {
                _currentUser.value = null
            } else {
                scope.launch {
                    _currentUser.value = UserDocs.load(firestore, fbUser.uid, fbUser.email)
                }
            }
        }
    }

    override suspend fun signIn(email: String, password: String): Result<AuthUser> {
        if (email.isBlank() || password.isBlank()) {
            return Result.failure(AuthException("من فضلك أدخل البريد وكلمة المرور"))
        }
        return try {
            val result = auth.signInWithEmailAndPassword(email.trim(), password).await()
            val uid = result.user?.uid
                ?: return Result.failure(AuthException("تعذّر تسجيل الدخول"))
            val profile = UserDocs.load(firestore, uid, result.user?.email)
            if (profile == null || !profile.active) {
                auth.signOut()
                Result.failure(AuthException("هذا الحساب غير مُصرّح له بالدخول"))
            } else {
                _currentUser.value = profile
                Result.success(profile)
            }
        } catch (e: Exception) {
            Result.failure(AuthException("بيانات الدخول غير صحيحة"))
        }
    }

    override fun signOut() {
        auth.signOut()
    }
}
