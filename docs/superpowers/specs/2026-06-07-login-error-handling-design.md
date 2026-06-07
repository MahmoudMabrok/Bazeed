# Login Error Handling + Firebase Auth Wiring — Design

**Date:** 2026-06-07
**Module:** `app` (`tools.mo3ta.bazeed`)
**Firebase project:** `marriyapp`
**Builds on:** `docs/superpowers/specs/2026-06-05-admin-control-architecture-design.md` (closes its pending Task 3)

## Goal

Two coupled improvements on the existing login flow:

1. **Diagnosable failures.** Today the login path has zero `Log` calls and squashes everything into a single Arabic string. Add structured logging across `LoginScreen`, `LocalAuthRepository`, and the new Firebase impls so login problems are visible in logcat.
2. **Typed errors with per-category UX messages.** Replace `AuthException(message: String)` with a sealed hierarchy carrying a `userMessage`, so each failure mode (bad credentials, no network, throttled, not provisioned, not authorized) shows a meaningful Arabic line without the screen having to decode strings.

Same PR also lands the Firebase implementations of `AuthRepository` and `UserRepository` — they're needed to exercise the new error categorization against real failure modes (and the announcements feature on this branch can't work end-to-end without them).

## Non-goals

- "Forgot password" flow — separate, smaller PR.
- Crashlytics / remote log shipping — separate dep + setup.
- Migrating existing `LocalUserStore` seed accounts to Firebase — the seed data is dev-only; no production migration.
- New UI for the auth flow beyond the typed-error message swap.

## Typed exceptions

Replaces `class AuthException(message: String)` in `app/src/main/.../data/repo/Repositories.kt` with:

```kotlin
sealed class AuthException(val userMessage: String, cause: Throwable? = null)
    : Exception(userMessage, cause) {

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

Properties:
- Each case carries a `userMessage` ready for the UI — no string-matching to recover the type.
- `Unknown` preserves the cause so it shows up in logs and crash reports.
- `EmailInUse` is added now because `FirestoreUserRepository.createUser` needs it; reusing the same hierarchy keeps admin user-creation and login error handling consistent.

The `Result<T>` return shape stays — callers continue to do `result.exceptionOrNull() as? AuthException`. The change is the *type* of the exception, not the calling convention.

## Logging

Library: `android.util.Log` (already used by `FcmTopics`). No new dependency.

| TAG | Where | Levels |
|---|---|---|
| `LoginScreen` | login UI | `Log.d` on attempt + success; `Log.w` on AuthException; `Log.e` on non-AuthException throws |
| `FirebaseAuth` | `FirebaseAuthRepository` | `Log.d` on attempt/success/sign-out/auth-state-change; `Log.w` on mapped recoverable errors; `Log.e` on unexpected throws |
| `FirestoreUsers` | `FirestoreUserRepository` | `Log.d` on snapshot updates + createUser flow steps; `Log.w` on mapped errors; `Log.e` on partial-failure orphan accounts |
| `LocalAuth` | `LocalAuthRepository` / `LocalUserRepository` | `Log.d` on attempt/success; `Log.w` on the new typed exceptions |

**Privacy rules:**
- Emails are logged (identifying but not secret; needed for diagnosis).
- Passwords are **never** logged — even at `Log.d`.
- UIDs are logged.
- Display names and role values are logged.

These are guidelines for the implementation, not new infrastructure.

## `FirebaseAuthRepository`

```
app/src/main/java/tools/mo3ta/bazeed/data/repo/firebase/FirebaseAuthRepository.kt
```

Constructor parameters (defaulted for production wiring):

```kotlin
class FirebaseAuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : AuthRepository
```

### `currentUser: StateFlow<AuthUser?>`

- Backed by `MutableStateFlow<AuthUser?>(null)`.
- On init: register an `AuthStateListener`. When auth state changes:
  - If `auth.currentUser == null` → emit null.
  - Else launch a coroutine that reads `users/{uid}`:
    - Doc exists with valid `role` → build `AuthUser`, emit it.
    - Doc missing OR role unparseable → emit null and `Log.e` (treat as not provisioned).
    - Firestore read throws → emit null and `Log.e`.
- On init, also seed from `auth.currentUser` synchronously (so cold-start with an existing Firebase session doesn't briefly flash the login screen).

### `signIn(email, password)`

1. Trim email; validate non-blank email + non-blank password → `Validation` if empty.
2. `Log.d("FirebaseAuth", "signIn attempt: $email")`.
3. `auth.signInWithEmailAndPassword(email, password).await()`.
4. Read `users/{uid}` from primary Firestore.
5. If doc missing → `auth.signOut()` (keep state clean) and return `Result.failure(UserNotProvisioned())`.
6. Build `AuthUser` from doc; the auth state listener will re-fire and update `currentUser`, so just return `Result.success(user)`.
7. Wrap the whole thing in `runCatching` so the exception mapping below catches Firebase throws.

### Firebase exception mapping

| Firebase exception | Mapped to |
|---|---|
| `FirebaseAuthInvalidUserException` (no account, disabled, deleted) | `InvalidCredentials` |
| `FirebaseAuthInvalidCredentialsException` (wrong password, malformed email) | `InvalidCredentials` |
| `FirebaseNetworkException` | `Network` |
| `FirebaseTooManyRequestsException` | `TooManyAttempts` |
| `Validation` / `UserNotProvisioned` (our own) | re-thrown / returned as-is |
| anything else | `Unknown(cause)` |

Important: do NOT distinguish "no user with this email" from "wrong password" in the user-facing message — leaks account existence. Both map to `InvalidCredentials`.

### `signOut()`

- `auth.signOut()`. Auth state listener handles the `currentUser` transition.

## `FirestoreUserRepository`

```
app/src/main/java/tools/mo3ta/bazeed/data/repo/firebase/FirestoreUserRepository.kt
```

Constructor:

```kotlin
class FirestoreUserRepository(
    private val context: Context, // Application context for secondary FirebaseApp init
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : UserRepository
```

### `users: StateFlow<List<AuthUser>>`

- Snapshot listener on `users/` collection, sorted by `createdAt asc`.
- Non-admin readers are denied by Firestore rules; the listener will receive an error → emit empty list and `Log.d` ("not allowed to read users — expected for non-admin"). Not `Log.w` because it's an expected outcome for customer-flavor sessions.

### `createUser(email, password, displayName, role)`

The secondary-`FirebaseApp` pattern from the prior spec:

```
1. Validate inputs (email format, password length ≥ 6) → AuthException.Validation if bad.
2. Lazily init/get a named secondary FirebaseApp from primary options.
3. secondaryAuth.createUserWithEmailAndPassword(email, password).await()
4. newUid = result.user.uid
5. PRIMARY firestore.collection("users").document(newUid).set({
       email, role: role.name.lowercase(), displayName,
       createdBy: auth.currentUser?.uid, createdAt: serverTimestamp(), active: true
   }).await()
6. secondaryAuth.signOut()
7. Return Result.success(AuthUser(...))
```

Exception mapping:

| Failure | Mapped to | Notes |
|---|---|---|
| `FirebaseAuthUserCollisionException` | `EmailInUse` | Step 3 |
| `FirebaseNetworkException` | `Network` | any step |
| Firestore write fails after Auth succeeds | `Unknown(cause)` | Log.e with the orphan uid so admin can manually delete or re-provision |
| any other | `Unknown(cause)` | |

Secondary app **lifecycle**: created once (lazily), reused across calls. Don't delete the FirebaseApp after each call — `FirebaseApp.delete()` is expensive and rare. `signOut()` on the secondary auth is enough.

## `Repositories.kt` wiring change

```kotlin
// app/src/main/java/tools/mo3ta/bazeed/data/Repositories.kt
object Repositories {
    fun init(applicationContext: Context) {
        // Allowed to be called multiple times; idempotent.
        if (::auth.isInitialized) return
        auth = FirebaseAuthRepository()
        users = FirestoreUserRepository(applicationContext)
        content = FirestoreContentRepository()
    }

    lateinit var auth: AuthRepository
        private set
    lateinit var users: UserRepository
        private set
    lateinit var content: ContentRepository
        private set
}
```

Reasons for `init(context)`:
- `FirestoreUserRepository` needs an `Application` context for `FirebaseApp.initializeApp(context, options, name)`.
- Avoids a top-level `getInstance().applicationContext` lookup at object-init time (fragile under unit tests).

Called once from `BazeedApp.onCreate()`:

```kotlin
class BazeedApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Repositories.init(this)
        // existing notification channel setup ...
    }
}
```

### What about the `Local*` impls?

They stay in the codebase, but they are no longer wired into production. They keep two jobs:
- Backing for unit tests (`LocalContentRepositoryTest` already exists; `LocalAuthRepository` + `LocalUserRepository` are useful for future tests).
- Dev iteration without a network.

Nothing imports them from production code paths after this change.

## `LoginScreen` changes

`app/src/main/java/tools/mo3ta/bazeed/ui/auth/LoginScreen.kt`:

- Internal state changes from `var error: String?` to `var error: AuthException?`.
- The displayed text becomes `error.userMessage` instead of `error.message ?: ...fallback...`.
- On submit:
  - `Log.d("LoginScreen", "signIn attempt: $email")` before the call.
  - On `Result.success`: `Log.d("LoginScreen", "signed in as ${user.uid}")`.
  - On `Result.failure(authEx: AuthException)`: `Log.w("LoginScreen", "signIn failed: ${authEx::class.simpleName}")` and `error = authEx`.
  - On `Result.failure(other)`: `Log.e("LoginScreen", "unexpected signIn throw", other)` and `error = AuthException.Unknown(other)`.

The screen otherwise stays the same — same layout, same fields.

## `LocalAuthRepository` + `LocalUserRepository` updates

Even though they're not production-wired anymore, update them to return the new typed exceptions so unit tests exercise the same shape as production:

- Empty fields → `AuthException.Validation("...")`.
- Bad credentials → `AuthException.InvalidCredentials()`.
- Existing-email on create → `AuthException.EmailInUse()`.
- Add `Log.d` / `Log.w` calls so logcat output looks consistent across impls when running on a device.

## Data flow on login

```
LoginScreen
   ├─ Log.d "signIn attempt: $email"
   │
   ▼
Repositories.auth.signIn(email, password)
   │
   ▼  (FirebaseAuthRepository)
   ├─ Log.d "signIn attempt: $email"
   ├─ FirebaseAuth.signInWithEmailAndPassword
   │     │
   │     ├─ success → read users/{uid}
   │     │     ├─ doc missing → signOut + UserNotProvisioned
   │     │     └─ doc present → AuthUser, currentUser flow updates
   │     │
   │     └─ Firebase throws → mapping table → AuthException.{InvalidCredentials|Network|TooManyAttempts|Unknown}
   │
   ▼
LoginScreen
   ├─ success: Log.d "signed in as ${user.uid}" → app root re-routes
   └─ failure: Log.w "signIn failed: <type>" → render error.userMessage
```

## Testing

### Unit tests (no Firebase)

- `AuthExceptionTest` — for each subclass, assert `userMessage` is non-empty and `Unknown` preserves cause.
- Update `LocalAuthRepository` and `LocalUserRepository` tests (if any exist) to assert the typed exception class, not the string message.

### Manual e2e (with Firebase)

Run on a real device with all Task 17 prerequisites from the announcements spec met:

1. **Happy login.** Customer logs in with a provisioned account → routes to home; logcat shows `FirebaseAuth signIn attempt → success → LoginScreen signed in`.
2. **Wrong password.** Logcat shows `FirebaseAuth signIn failed: InvalidCredentials`; screen shows "بيانات الدخول غير صحيحة".
3. **No account with this email.** Same result as wrong password — does NOT leak that the email is unknown.
4. **No network.** Disable wifi/data, try to log in. Logcat shows `FirebaseAuth signIn failed: Network`; screen shows "تعذّر الاتصال — تحقق من الإنترنت".
5. **Throttled.** After many rapid failed attempts, Firebase returns `TooManyRequestsException`. Screen shows the throttled Arabic line.
6. **Unprovisioned auth account.** Create an Auth user via the Firebase console *without* a corresponding `users/{uid}` doc. Try to log in. Screen shows "الحساب غير مفعّل"; logcat shows `FirebaseAuth signIn failed: UserNotProvisioned` and the user is signed back out.
7. **Admin role gate.** Log a `user`-role account into the **admin** flavor → `NotAuthorizedScreen` shows (existing UX).
8. **Admin creates a user.** From the admin app, create a new customer; logcat shows `FirestoreUsers createUser steps → success`. New user can log in to the customer flavor.
9. **Admin createUser email collision.** Try to create with an email already in use. Logcat shows `FirestoreUsers createUser failed: EmailInUse`; screen shows the Arabic collision message.

## Error handling summary table

| Scenario | Exception | UI message |
|---|---|---|
| Empty email or password | `Validation` | "من فضلك أدخل البريد وكلمة المرور" |
| Wrong password / unknown email | `InvalidCredentials` | "بيانات الدخول غير صحيحة" |
| Auth user has no role doc | `UserNotProvisioned` | "الحساب غير مفعّل — تواصل مع الإدارة" |
| Customer-role logs into admin flavor | `NotAuthorized` (existing role-gate UX) | "هذا الحساب غير مصرّح له بالدخول إلى لوحة الإدارة" |
| No network | `Network` | "تعذّر الاتصال — تحقق من الإنترنت" |
| Throttled (many failed attempts) | `TooManyAttempts` | "محاولات كثيرة، حاول لاحقاً" |
| Admin creates user; email taken | `EmailInUse` | "هذا البريد مسجّل بالفعل" |
| Unexpected throw | `Unknown(cause)` | "حدث خطأ غير متوقع، حاول مرة أخرى" |

## Files

**New:**
- `app/src/main/java/tools/mo3ta/bazeed/data/repo/firebase/FirebaseAuthRepository.kt`
- `app/src/main/java/tools/mo3ta/bazeed/data/repo/firebase/FirestoreUserRepository.kt`
- `app/src/test/java/tools/mo3ta/bazeed/data/repo/AuthExceptionTest.kt`

**Modified:**
- `app/src/main/java/tools/mo3ta/bazeed/data/repo/Repositories.kt` — replace `class AuthException(message)` with the sealed hierarchy.
- `app/src/main/java/tools/mo3ta/bazeed/data/repo/local/LocalRepositories.kt` — return the new typed exceptions; add `Log.d`/`Log.w`.
- `app/src/main/java/tools/mo3ta/bazeed/ui/auth/LoginScreen.kt` — accept `AuthException?`; add login logging.
- `app/src/main/java/tools/mo3ta/bazeed/data/Repositories.kt` — convert `object Repositories` into one with `init(applicationContext)` + `lateinit` fields; wire Firebase impls.
- `app/src/main/java/tools/mo3ta/bazeed/BazeedApp.kt` — call `Repositories.init(this)` in `onCreate`.

## Open assumptions

- Firebase Auth (email/password provider) is enabled in `marriyapp`. (Listed in the announcements spec's prerequisites.)
- The first admin is bootstrapped manually as documented in the prior spec.
- App Check is not yet enabled. Without it the embedded API key still allows raw sign-up at the Auth layer — but those accounts are inert (no `users/{uid}`) and `UserNotProvisioned` rejects them. Hardening with App Check stays a separate task.
- The role enum mapping: Firestore `role: "admin"` / `"user"` → `UserRole.ADMIN` / `UserRole.USER`. Anything else is treated as a missing doc (UserNotProvisioned).
