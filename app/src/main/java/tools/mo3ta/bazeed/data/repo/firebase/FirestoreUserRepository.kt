package tools.mo3ta.bazeed.data.repo.firebase

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import tools.mo3ta.bazeed.data.auth.AuthUser
import tools.mo3ta.bazeed.data.auth.UserRole
import tools.mo3ta.bazeed.data.repo.AuthException
import tools.mo3ta.bazeed.data.repo.UserRepository

private const val TAG = "FirestoreUsers"
private const val SECONDARY_APP_NAME = "userProvisioning"

/**
 * Production UserRepository against Firestore `/users/{uid}`. The collection is
 * admin-only by rules, so a customer-session snapshot listener will fail-fast with
 * permission-denied and emit an empty list (expected, not a bug).
 *
 * createUser uses the secondary-`FirebaseApp` pattern to mint the new Auth account
 * without disturbing the admin's primary session.
 */
class FirestoreUserRepository(
    private val applicationContext: Context,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : UserRepository {

    private val _users = MutableStateFlow<List<AuthUser>>(emptyList())
    override val users: StateFlow<List<AuthUser>> = _users.asStateFlow()

    init {
        firestore.collection("users")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    // Non-admin sessions fail here by design (rules deny collection read).
                    Log.d(TAG, "users listener stopped: ${err.message}")
                    _users.value = emptyList()
                    return@addSnapshotListener
                }
                if (snap == null) return@addSnapshotListener
                _users.value = snap.documents.mapNotNull { it.toAuthUserOrNull() }
                Log.d(TAG, "users snapshot updated: ${_users.value.size} entries")
            }
    }

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

        return runCatching {
            val secondaryAuth = FirebaseAuth.getInstance(secondaryApp())
            val result = secondaryAuth.createUserWithEmailAndPassword(trimmed, password).await()
            val newUid = result.user?.uid ?: error("Firebase returned null user on createUser")
            Log.d(TAG, "createUser auth ok: $newUid")

            try {
                firestore.collection("users").document(newUid).set(
                    mapOf(
                        "email" to trimmed,
                        "displayName" to displayName.trim().ifBlank { trimmed.substringBefore("@") },
                        "role" to role.name.lowercase(),
                        "active" to true,
                        "createdBy" to FirebaseAuth.getInstance().currentUser?.uid,
                        "createdAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
            } catch (t: Throwable) {
                Log.e(TAG, "createUser doc write failed; orphan auth uid=$newUid", t)
                secondaryAuth.signOut()
                throw t
            }

            secondaryAuth.signOut()
            Log.d(TAG, "createUser ok: $newUid")
            AuthUser(
                uid = newUid,
                email = trimmed,
                displayName = displayName.trim().ifBlank { trimmed.substringBefore("@") },
                role = role,
            )
        }.recoverCatching { throw mapException(it) }
    }

    /** Lazily init (and reuse) a secondary FirebaseApp using the primary's options. */
    private fun secondaryApp(): FirebaseApp {
        return FirebaseApp.getApps(applicationContext)
            .firstOrNull { it.name == SECONDARY_APP_NAME }
            ?: run {
                val primary = FirebaseApp.getInstance()
                val options = FirebaseOptions.Builder()
                    .setApiKey(primary.options.apiKey)
                    .setApplicationId(primary.options.applicationId)
                    .setProjectId(primary.options.projectId)
                    .setDatabaseUrl(primary.options.databaseUrl)
                    .setStorageBucket(primary.options.storageBucket)
                    .setGcmSenderId(primary.options.gcmSenderId)
                    .build()
                FirebaseApp.initializeApp(applicationContext, options, SECONDARY_APP_NAME)
            }
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toAuthUserOrNull(): AuthUser? {
        val email = getString("email") ?: return null
        val role = when (getString("role")?.lowercase()) {
            "admin" -> UserRole.ADMIN
            "user" -> UserRole.USER
            else -> return null
        }
        val active = getBoolean("active") ?: true
        return AuthUser(
            uid = id,
            email = email,
            displayName = getString("displayName") ?: email.substringBefore("@"),
            role = role,
            active = active,
        )
    }

    private fun mapException(t: Throwable): Throwable = when (t) {
        is AuthException -> t
        is FirebaseAuthUserCollisionException -> AuthException.EmailInUse()
        is FirebaseNetworkException -> AuthException.Network()
        else -> {
            Log.e(TAG, "unexpected createUser throwable", t)
            AuthException.Unknown(t)
        }
    }
}
