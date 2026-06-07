package tools.mo3ta.bazeed.data.repo.firebase

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn
import tools.mo3ta.bazeed.data.auth.AuthUser
import tools.mo3ta.bazeed.data.auth.UserRole
import tools.mo3ta.bazeed.data.repo.AuthException
import tools.mo3ta.bazeed.data.repo.UserRepository

/**
 * Firebase-backed [UserRepository].
 *
 * User creation uses the **secondary FirebaseApp** pattern: a separate
 * [FirebaseApp]/[FirebaseAuth] instance creates the account so the admin's own
 * (primary) session is never replaced. The role-bearing write to `users/{uid}`
 * is done with the primary Firestore instance (admin context) so Security Rules
 * authorize it. No Cloud Functions / Admin SDK involved.
 */
class FirestoreUserRepository(
    appContext: Context,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : UserRepository {

    private val appContext = appContext.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val users: StateFlow<List<AuthUser>> =
        callbackFlow {
            val registration = firestore.collection(UserDocs.COLLECTION)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        trySend(emptyList())
                        return@addSnapshotListener
                    }
                    val list = snapshot?.documents?.mapNotNull { UserDocs.toAuthUser(it) }.orEmpty()
                    trySend(list)
                }
            awaitClose { registration.remove() }
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

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

        val secondaryAuth = FirebaseAuth.getInstance(secondaryApp())
        return try {
            val created = secondaryAuth.createUserWithEmailAndPassword(trimmed, password).await()
            val uid = created.user?.uid
                ?: return Result.failure(AuthException("تعذّر إنشاء الحساب"))

            val user = AuthUser(
                uid = uid,
                email = trimmed,
                displayName = displayName.trim().ifBlank { trimmed.substringBefore("@") },
                role = role,
            )

            // Authorization-bearing write, performed as the admin (primary app).
            firestore.collection(UserDocs.COLLECTION).document(uid).set(
                mapOf(
                    "email" to user.email,
                    "displayName" to user.displayName,
                    "role" to user.role.name.lowercase(),
                    "active" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                )
            ).await()

            Result.success(user)
        } catch (e: FirebaseAuthUserCollisionException) {
            Result.failure(AuthException("هذا البريد مسجّل بالفعل"))
        } catch (e: Exception) {
            Result.failure(AuthException(e.message ?: "تعذّر إنشاء الحساب"))
        } finally {
            // Discard the new user's secondary session; admin's primary stays intact.
            secondaryAuth.signOut()
        }
    }

    /** Lazily reuses a dedicated secondary app for provisioning. */
    private fun secondaryApp(): FirebaseApp = try {
        FirebaseApp.getInstance(SECONDARY_APP_NAME)
    } catch (e: IllegalStateException) {
        FirebaseApp.initializeApp(
            appContext,
            FirebaseApp.getInstance().options,
            SECONDARY_APP_NAME,
        )
    }

    private companion object {
        const val SECONDARY_APP_NAME = "userProvisioning"
    }
}
