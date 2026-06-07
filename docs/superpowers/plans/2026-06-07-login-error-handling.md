# Login Error Handling + Firebase Auth Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the generic `AuthException` with a typed sealed hierarchy carrying per-category Arabic messages, instrument the auth path with structured `Log` calls, and ship the Firebase implementations of `AuthRepository` and `UserRepository` (closes the prior spec's pending Task 3).

**Architecture:** Five focused, sequential tasks. Each task leaves the build green and is independently committable. Tasks 1–2 modify the existing shared auth surface; Tasks 3–4 add new Firebase impls without wiring them; Task 5 flips production wiring through a new `Repositories.init(context)` pattern called from `BazeedApp.onCreate`. Task 6 is human-driven manual e2e.

**Tech Stack:** Kotlin 2.0.21, Jetpack Compose, Firebase BoM 34.14.0 (`firebase-auth`, `firebase-firestore`, `firebase-messaging`), `kotlinx-coroutines-play-services` for `.await()` on Firebase `Task<T>`. All these are already in the catalog from the announcements work.

**Spec:** `docs/superpowers/specs/2026-06-07-login-error-handling-design.md`

**Branch:** continue on `claude/admin-announcements` (the announcements PR is still open). Each task commits with a focused message.

---

## Prerequisites

These environment items must be true before the runtime behavior is correct. The plan ships code regardless:

1. Firebase Auth (email/password provider) enabled in the `marriyapp` project.
2. Firestore enabled, with `firestore.rules` (from the announcements branch) deployed.
3. At least one admin bootstrapped: Firebase Auth user + `users/{uid}` doc with `role: "admin"`, `active: true`.
4. `google-services.json` has clients for both `tools.mo3ta.bazeed` and `tools.mo3ta.bazeed.admin` (already true on this clone).

---

## File map

**New:**

| Path | Responsibility |
|---|---|
| `app/src/main/.../data/repo/firebase/FirebaseAuthRepository.kt` | Production `AuthRepository` against Firebase Auth + Firestore user doc |
| `app/src/main/.../data/repo/firebase/FirestoreUserRepository.kt` | Production `UserRepository` (admin user CRUD + snapshot listener) |
| `app/src/test/.../data/repo/AuthExceptionTest.kt` | Verifies sealed hierarchy properties |

**Modified:**

| Path | Change |
|---|---|
| `app/src/main/.../data/repo/Repositories.kt` | Replace `class AuthException(message)` with sealed hierarchy |
| `app/src/main/.../data/repo/local/LocalRepositories.kt` | Return typed exceptions; add `Log.d`/`Log.w` |
| `app/src/main/.../ui/auth/LoginScreen.kt` | Hold `AuthException?` state; read `.userMessage`; add `Log.d`/`Log.w`/`Log.e` |
| `app/src/main/.../data/Repositories.kt` | Convert to `init(context)` + `lateinit` fields; wire Firebase impls |
| `app/src/main/.../BazeedApp.kt` | Call `Repositories.init(this)` in `onCreate` |

---

## Task 1: Sealed `AuthException` hierarchy + coordinated callers update

**Files:**
- Modify: `app/src/main/java/tools/mo3ta/bazeed/data/repo/Repositories.kt`
- Modify: `app/src/main/java/tools/mo3ta/bazeed/data/repo/local/LocalRepositories.kt`
- Create: `app/src/test/java/tools/mo3ta/bazeed/data/repo/AuthExceptionTest.kt`

One task because the rename is coordinated: the moment `AuthException` becomes sealed, every existing `AuthException("...")` call site stops compiling. We change them together so the build never breaks in between.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/tools/mo3ta/bazeed/data/repo/AuthExceptionTest.kt`:

```kotlin
package tools.mo3ta.bazeed.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthExceptionTest {

    @Test fun validation_carries_passed_message() {
        val ex = AuthException.Validation("custom validation message")
        assertEquals("custom validation message", ex.userMessage)
        assertEquals("custom validation message", ex.message)
    }

    @Test fun invalid_credentials_has_arabic_message() {
        val ex = AuthException.InvalidCredentials()
        assertEquals("بيانات الدخول غير صحيحة", ex.userMessage)
    }

    @Test fun user_not_provisioned_has_arabic_message() {
        val ex = AuthException.UserNotProvisioned()
        assertTrue(ex.userMessage.isNotBlank())
    }

    @Test fun not_authorized_has_arabic_message() {
        val ex = AuthException.NotAuthorized()
        assertTrue(ex.userMessage.isNotBlank())
    }

    @Test fun network_has_arabic_message() {
        val ex = AuthException.Network()
        assertTrue(ex.userMessage.isNotBlank())
    }

    @Test fun too_many_attempts_has_arabic_message() {
        val ex = AuthException.TooManyAttempts()
        assertTrue(ex.userMessage.isNotBlank())
    }

    @Test fun email_in_use_has_arabic_message() {
        val ex = AuthException.EmailInUse()
        assertTrue(ex.userMessage.isNotBlank())
    }

    @Test fun unknown_preserves_cause_and_message() {
        val cause = RuntimeException("boom")
        val ex = AuthException.Unknown(cause)
        assertNotNull(ex.cause)
        assertSame(cause, ex.cause)
        assertTrue(ex.userMessage.isNotBlank())
    }

    @Test fun userMessage_is_also_exception_message() {
        // The screen used to read .message; we keep that working.
        val ex = AuthException.InvalidCredentials()
        assertEquals(ex.userMessage, ex.message)
    }
}
```

- [ ] **Step 2: Run the test and verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests 'tools.mo3ta.bazeed.data.repo.AuthExceptionTest'`
Expected: FAIL — `Unresolved reference: AuthException.Validation` (or similar — the current `AuthException` doesn't have these subclasses).

- [ ] **Step 3: Replace the `AuthException` definition with the sealed hierarchy**

Open `app/src/main/java/tools/mo3ta/bazeed/data/repo/Repositories.kt`. The file currently ends with:

```kotlin
/** Thrown by repositories for recoverable auth/validation failures. */
class AuthException(message: String) : Exception(message)
```

Replace that single declaration with:

```kotlin
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
```

Do not change `AuthRepository` / `UserRepository` / `ContentRepository` interface declarations in this file.

- [ ] **Step 4: Update `LocalRepositories.kt` to use the typed subclasses**

Replace the contents of `app/src/main/java/tools/mo3ta/bazeed/data/repo/local/LocalRepositories.kt` with:

```kotlin
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
```

- [ ] **Step 5: Run the test and verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests 'tools.mo3ta.bazeed.data.repo.AuthExceptionTest'`
Expected: PASS — all 9 tests green.

- [ ] **Step 6: Verify the full build is still green**

Run: `./gradlew :app:assembleCustomerDebug :app:assembleAdminDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL for both.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/tools/mo3ta/bazeed/data/repo/Repositories.kt \
        app/src/main/java/tools/mo3ta/bazeed/data/repo/local/LocalRepositories.kt \
        app/src/test/java/tools/mo3ta/bazeed/data/repo/AuthExceptionTest.kt
git -c commit.gpgsign=false commit -m "auth: sealed AuthException hierarchy with per-category Arabic messages"
```

---

## Task 2: `LoginScreen` typed-error + structured logging

**Files:**
- Modify: `app/src/main/java/tools/mo3ta/bazeed/ui/auth/LoginScreen.kt`

- [ ] **Step 1: Replace the file**

Replace the contents of `app/src/main/java/tools/mo3ta/bazeed/ui/auth/LoginScreen.kt` with:

```kotlin
package tools.mo3ta.bazeed.ui.auth

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import tools.mo3ta.bazeed.data.Repositories
import tools.mo3ta.bazeed.data.repo.AuthException
import tools.mo3ta.bazeed.ui.theme.Ink
import tools.mo3ta.bazeed.ui.theme.Sand
import tools.mo3ta.bazeed.ui.theme.Terracotta

private const val TAG = "LoginScreen"

/**
 * Shared login UI. Both flavors render this when no one is signed in; routing
 * after a successful sign-in is decided by each flavor's app root, which reacts
 * to [Repositories.auth] `currentUser` changing.
 */
@Composable
fun LoginScreen(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<AuthException?>(null) }
    var loading by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = title, style = MaterialTheme.typography.displaySmall, color = Ink)
        Spacer(Modifier.height(8.dp))
        Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = Ink)
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it; error = null },
            label = { Text("البريد الإلكتروني") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            enabled = !loading,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; error = null },
            label = { Text("كلمة المرور") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            enabled = !loading,
            modifier = Modifier.fillMaxWidth(),
        )

        error?.let { authEx ->
            Spacer(Modifier.height(12.dp))
            Text(text = authEx.userMessage, color = Terracotta, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(28.dp))
        Button(
            onClick = {
                Log.d(TAG, "signIn attempt: $email")
                loading = true
                error = null
                scope.launch {
                    val result = Repositories.auth.signIn(email, password)
                    loading = false
                    result.fold(
                        onSuccess = { user ->
                            Log.d(TAG, "signed in as ${user.uid}")
                            // currentUser flips → app root re-routes
                        },
                        onFailure = { throwable ->
                            error = when (throwable) {
                                is AuthException -> {
                                    Log.w(TAG, "signIn failed: ${throwable::class.simpleName}")
                                    throwable
                                }
                                else -> {
                                    Log.e(TAG, "unexpected signIn throw", throwable)
                                    AuthException.Unknown(throwable)
                                }
                            }
                        },
                    )
                }
            },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.height(18.dp), color = Sand, strokeWidth = 2.dp)
            } else {
                Text("دخول")
            }
        }
    }
}
```

- [ ] **Step 2: Build both flavors**

Run: `./gradlew :app:assembleCustomerDebug :app:assembleAdminDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL for both.

- [ ] **Step 3: Run unit tests**

Run: `./gradlew :app:testCustomerDebugUnitTest 2>&1 | tail -3`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/tools/mo3ta/bazeed/ui/auth/LoginScreen.kt
git -c commit.gpgsign=false commit -m "login: hold typed AuthException; add structured Log calls"
```

---

## Task 3: `FirebaseAuthRepository`

**Files:**
- Create: `app/src/main/java/tools/mo3ta/bazeed/data/repo/firebase/FirebaseAuthRepository.kt`

No unit test — exercised by manual e2e (Task 6).

- [ ] **Step 1: Create the file**

Create `app/src/main/java/tools/mo3ta/bazeed/data/repo/firebase/FirebaseAuthRepository.kt`:

```kotlin
package tools.mo3ta.bazeed.data.repo.firebase

import android.util.Log
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
                Log.d(TAG, "authState: signed out")
                _currentUser.value = null
            } else {
                Log.d(TAG, "authState: signed in as ${fbUser.uid}")
                scope.launch { refreshFromUserDoc(fbUser.uid, fbUser.email.orEmpty()) }
            }
        }
    }

    override suspend fun signIn(email: String, password: String): Result<AuthUser> {
        Log.d(TAG, "signIn attempt: $email")
        val trimmed = email.trim()
        if (trimmed.isBlank() || password.isBlank()) {
            Log.w(TAG, "signIn failed: Validation (empty fields)")
            return Result.failure(AuthException.Validation("من فضلك أدخل البريد وكلمة المرور"))
        }
        return runCatching {
            val cred = auth.signInWithEmailAndPassword(trimmed, password).await()
            val uid = cred.user?.uid ?: error("Firebase returned null user")
            val authUser = readUserDoc(uid, trimmed)
            if (authUser == null) {
                Log.w(TAG, "signIn failed: UserNotProvisioned uid=$uid")
                auth.signOut()
                throw AuthException.UserNotProvisioned()
            }
            _currentUser.value = authUser
            Log.d(TAG, "signIn ok: ${authUser.uid} (${authUser.role})")
            authUser
        }.recoverCatching { throw mapException(it) }
    }

    override fun signOut() {
        Log.d(TAG, "signOut")
        auth.signOut()
    }

    /** Fetches `users/{uid}` and updates [_currentUser]; null-emits on missing/unparseable. */
    private suspend fun refreshFromUserDoc(uid: String, fallbackEmail: String) {
        try {
            val authUser = readUserDoc(uid, fallbackEmail)
            if (authUser == null) {
                Log.e(TAG, "users/$uid missing or unparseable; treating as signed out")
                _currentUser.value = null
            } else {
                _currentUser.value = authUser
            }
        } catch (t: Throwable) {
            Log.e(TAG, "users/$uid read failed; treating as signed out", t)
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
            Log.e(TAG, "unexpected signIn throwable", t)
            AuthException.Unknown(t)
        }
    }
}
```

- [ ] **Step 2: Compile check**

Run: `./gradlew :app:compileCustomerDebugKotlin 2>&1 | grep -E 'FirebaseAuthRepository' | head -5`
Expected: no errors mentioning `FirebaseAuthRepository.kt`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/tools/mo3ta/bazeed/data/repo/firebase/FirebaseAuthRepository.kt
git -c commit.gpgsign=false commit -m "auth: FirebaseAuthRepository with typed exception mapping"
```

---

## Task 4: `FirestoreUserRepository`

**Files:**
- Create: `app/src/main/java/tools/mo3ta/bazeed/data/repo/firebase/FirestoreUserRepository.kt`

- [ ] **Step 1: Create the file**

Create `app/src/main/java/tools/mo3ta/bazeed/data/repo/firebase/FirestoreUserRepository.kt`:

```kotlin
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
```

- [ ] **Step 2: Compile check**

Run: `./gradlew :app:compileCustomerDebugKotlin 2>&1 | grep -E 'FirestoreUserRepository' | head -5`
Expected: no errors mentioning `FirestoreUserRepository.kt`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/tools/mo3ta/bazeed/data/repo/firebase/FirestoreUserRepository.kt
git -c commit.gpgsign=false commit -m "users: FirestoreUserRepository with secondary-FirebaseApp createUser"
```

---

## Task 5: Flip production wiring via `Repositories.init(context)` + `BazeedApp`

**Files:**
- Modify: `app/src/main/java/tools/mo3ta/bazeed/data/Repositories.kt`
- Modify: `app/src/main/java/tools/mo3ta/bazeed/BazeedApp.kt`

- [ ] **Step 1: Replace `Repositories.kt`**

Replace the entire contents of `app/src/main/java/tools/mo3ta/bazeed/data/Repositories.kt` with:

```kotlin
package tools.mo3ta.bazeed.data

import android.content.Context
import tools.mo3ta.bazeed.data.repo.AuthRepository
import tools.mo3ta.bazeed.data.repo.ContentRepository
import tools.mo3ta.bazeed.data.repo.UserRepository
import tools.mo3ta.bazeed.data.repo.firebase.FirebaseAuthRepository
import tools.mo3ta.bazeed.data.repo.firebase.FirestoreContentRepository
import tools.mo3ta.bazeed.data.repo.firebase.FirestoreUserRepository

/**
 * Production wiring: Firebase Auth + Firestore. Call [init] once from
 * `Application.onCreate` before any composable reads the fields.
 *
 * The Local* impls (LocalAuthRepository, LocalUserRepository, LocalContentRepository)
 * remain in the codebase as test utilities and are no longer wired in production.
 */
object Repositories {

    lateinit var auth: AuthRepository
        private set
    lateinit var users: UserRepository
        private set
    lateinit var content: ContentRepository
        private set

    /** Idempotent — safe to call multiple times (subsequent calls no-op). */
    fun init(applicationContext: Context) {
        if (::auth.isInitialized) return
        auth = FirebaseAuthRepository()
        users = FirestoreUserRepository(applicationContext)
        content = FirestoreContentRepository()
    }
}
```

- [ ] **Step 2: Update `BazeedApp.kt`**

Replace the contents of `app/src/main/java/tools/mo3ta/bazeed/BazeedApp.kt` with:

```kotlin
package tools.mo3ta.bazeed

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationManagerCompat
import tools.mo3ta.bazeed.data.Repositories

class BazeedApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Repositories.init(this)
        createDefaultNotificationChannel()
    }

    private fun createDefaultNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID_GENERAL,
            getString(R.string.notification_channel_general_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = getString(R.string.notification_channel_general_description)
        }
        NotificationManagerCompat.from(this).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID_GENERAL = "general"
    }
}
```

- [ ] **Step 3: Build both flavors**

Run: `./gradlew :app:assembleCustomerDebug :app:assembleAdminDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL for both.

- [ ] **Step 4: Run unit tests**

Run: `./gradlew :app:testCustomerDebugUnitTest 2>&1 | tail -3`
Expected: BUILD SUCCESSFUL. (Tests don't touch `Repositories.init` — they use the `Local*` impls directly.)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/tools/mo3ta/bazeed/data/Repositories.kt \
        app/src/main/java/tools/mo3ta/bazeed/BazeedApp.kt
git -c commit.gpgsign=false commit -m "wiring: Repositories.init(context) flips to Firebase impls"
```

---

## Task 6: Manual end-to-end verification (human-driven)

Run on real devices after prerequisites at the top of this plan are met. Open a logcat filter on tag `LoginScreen | FirebaseAuth | FirestoreUsers | LocalAuth` and walk through each scenario.

- [ ] **1. Happy login.** Customer with a provisioned account logs in → routes home. Logcat shows: `LoginScreen signIn attempt → FirebaseAuth signIn attempt → FirebaseAuth signIn ok → LoginScreen signed in`.
- [ ] **2. Wrong password.** Login fails with "بيانات الدخول غير صحيحة". Logcat shows `FirebaseAuth signIn failed: InvalidCredentials` (mapped from `FirebaseAuthInvalidCredentialsException`).
- [ ] **3. Unknown email.** Same UI message as #2 — does NOT leak whether the email is known.
- [ ] **4. No network.** Disable wifi/data; login shows "تعذّر الاتصال — تحقق من الإنترنت". Logcat shows `FirebaseAuth signIn failed: Network`.
- [ ] **5. Throttled.** After many rapid bad attempts, Firebase returns `FirebaseTooManyRequestsException` → screen shows "محاولات كثيرة، حاول لاحقاً".
- [ ] **6. Unprovisioned auth account.** In the Firebase console, create an Auth user **without** a corresponding `users/{uid}` doc. Try to log in. Screen shows "الحساب غير مفعّل — تواصل مع الإدارة"; logcat shows `FirebaseAuth signIn failed: UserNotProvisioned` AND `FirebaseAuth signOut` (clean state).
- [ ] **7. Admin role gate.** Log a `user`-role account into the **admin** flavor → `NotAuthorizedScreen` renders (existing UX path; this plan didn't change it).
- [ ] **8. Admin creates a user.** From the admin app, create a new customer. Logcat shows `FirestoreUsers createUser attempt → createUser auth ok → createUser ok`. New user can log into the customer flavor with the temp password.
- [ ] **9. Admin createUser email collision.** Try to create with an email already in use. Screen shows "هذا البريد مسجّل بالفعل"; logcat shows `FirestoreUsers createUser failed: EmailInUse`.

No commit for this task.

---

## Self-review checklist (plan author — already done)

**Spec coverage:**
- Sealed `AuthException` hierarchy → Task 1 ✓
- `AuthExceptionTest` → Task 1 ✓
- LoginScreen typed-error path + logs → Task 2 ✓
- `Log.d`/`Log.w` in Local impls → Task 1 ✓
- `FirebaseAuthRepository` with mapping table → Task 3 ✓
- `FirestoreUserRepository` with secondary-`FirebaseApp` create → Task 4 ✓
- `Repositories.init(context)` + `BazeedApp.onCreate` → Task 5 ✓
- Wiring flip → Task 5 ✓
- Manual e2e (9 scenarios) → Task 6 ✓

**Type consistency:**
- `AuthException.Validation`, `InvalidCredentials`, `UserNotProvisioned`, `NotAuthorized`, `Network`, `TooManyAttempts`, `EmailInUse`, `Unknown` — used identically across Tasks 1, 2, 3, 4.
- `userMessage` property reads consistently in LoginScreen (Task 2) and tests (Task 1).
- `Repositories.auth/users/content` field names match between Task 5 and existing call sites.
- `secondaryAuth.signOut()` after success and after Firestore-write failure in Task 4 — clean state in both branches.

**No placeholders — confirmed.** Every code step contains complete source; verification steps name the exact gradle command.
