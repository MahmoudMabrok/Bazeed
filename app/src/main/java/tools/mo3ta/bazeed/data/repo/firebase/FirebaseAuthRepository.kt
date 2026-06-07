package tools.mo3ta.bazeed.data.repo.firebase

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import tools.mo3ta.bazeed.data.auth.AuthUser
import tools.mo3ta.bazeed.data.auth.UserRole
import tools.mo3ta.bazeed.data.repo.AuthException
import tools.mo3ta.bazeed.data.repo.AuthRepository
import tools.mo3ta.bazeed.util.Logger

private const val TAG = "FirebaseAuth"

/**
 * Production AuthRepository against Firebase Auth, with role/displayName sourced
 * from the Firestore `users/{uid}` doc per the architecture spec.
 *
 * Errors are mapped to the typed [AuthException] hierarchy so the UI can show a
 * meaningful Arabic message and logs carry the concrete type.
 */
class FirebaseAuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : AuthRepository {

    private val _currentUser = MutableStateFlow<AuthUser?>(null)
    override val currentUser: StateFlow<AuthUser?> = _currentUser.asStateFlow()

    init {
        // If the Firebase session is already live (warm start), populate currentUser
        // by reading the user doc in the background.
        auth.currentUser?.let { fbUser ->
            scope.launch { refreshFromUserDoc(fbUser.uid, fbUser.email.orEmpty()) }
        }
        auth.addAuthStateListener { fb ->
            val fbUser = fb.currentUser
            if (fbUser == null) {
                Logger.d(TAG, "authState: signed out")
                _currentUser.value = null
            } else {
                Logger.d(TAG, "authState: signed in as ${fbUser.uid}")
                scope.launch { refreshFromUserDoc(fbUser.uid, fbUser.email.orEmpty()) }
            }
        }
    }

    override suspend fun signIn(email: String, password: String): Result<AuthUser> {
        Logger.d(TAG, "signIn attempt: $email")
        val trimmed = email.trim()
        if (trimmed.isBlank() || password.isBlank()) {
            Logger.w(TAG, "signIn failed: Validation (empty fields)")
            return Result.failure(AuthException.Validation("من فضلك أدخل البريد وكلمة المرور"))
        }
        return runCatching {
            val cred = auth.signInWithEmailAndPassword(trimmed, password).await()
            val uid = cred.user?.uid ?: error("Firebase returned null user")
            val authUser = readUserDoc(uid, trimmed)
            if (authUser == null) {
                Logger.w(TAG, "signIn failed: UserNotProvisioned uid=$uid")
                auth.signOut()
                throw AuthException.UserNotProvisioned()
            }
            _currentUser.value = authUser
            Logger.d(TAG, "signIn ok: ${authUser.uid} (${authUser.role})")
            authUser
        }.recoverCatching { throw mapException(it) }
    }

    override fun signOut() {
        Logger.d(TAG, "signOut")
        auth.signOut()
    }

    /** Fetches `users/{uid}` and updates [_currentUser]; null-emits on missing/unparseable. */
    private suspend fun refreshFromUserDoc(uid: String, fallbackEmail: String) {
        try {
            val authUser = readUserDoc(uid, fallbackEmail)
            if (authUser == null) {
                Logger.e(TAG, "users/$uid missing or unparseable; treating as signed out")
                _currentUser.value = null
            } else {
                _currentUser.value = authUser
            }
        } catch (t: Throwable) {
            Logger.e(TAG, "users/$uid read failed; treating as signed out", t)
            _currentUser.value = null
        }
    }

    private suspend fun readUserDoc(uid: String, fallbackEmail: String): AuthUser? {
        val snap = firestore.collection("users").document(uid).get().await()
        if (!snap.exists()) return null
        val role = when (snap.getString("role")?.lowercase()) {
            "admin" -> UserRole.ADMIN
            "user" -> UserRole.USER
            else -> return null
        }
        val active = snap.getBoolean("active") ?: true
        if (!active) return null
        return AuthUser(
            uid = uid,
            email = snap.getString("email") ?: fallbackEmail,
            displayName = snap.getString("displayName") ?: fallbackEmail.substringBefore("@"),
            role = role,
        )
    }

    private fun mapException(t: Throwable): Throwable = when (t) {
        is AuthException -> t
        is FirebaseAuthInvalidUserException -> AuthException.InvalidCredentials()
        is FirebaseAuthInvalidCredentialsException -> AuthException.InvalidCredentials()
        is FirebaseNetworkException -> AuthException.Network()
        is FirebaseTooManyRequestsException -> AuthException.TooManyAttempts()
        else -> {
            Logger.e(TAG, "unexpected signIn throwable", t)
            AuthException.Unknown(t)
        }
    }
}
