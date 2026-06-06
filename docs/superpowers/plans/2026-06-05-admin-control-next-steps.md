# Admin Control — Next Steps Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Continue the admin-control feature from the client-only scaffold (PR #1) toward a working Firebase-backed, admin-provisioned access model — while keeping admin code out of the customer APK.

**Spec:** `docs/superpowers/specs/2026-06-05-admin-control-architecture-design.md` (read this first — it has the full design, the Firestore security rules, and the rationale).

**Branch:** continue on `claude/admin-control-architecture-5aj8S` (PR #1) or branch from it.

**Tech stack:** Kotlin 2.0.21, AGP 9.1.0-alpha08, Jetpack Compose, Firebase BoM 34.14.0, google-services 4.4.4. Two product flavors: `customer` (`tools.mo3ta.bazeed`) and `admin` (`tools.mo3ta.bazeed.admin`), dimension `audience`.

---

## What already exists (PR #1 — do NOT redo)

Client-only scaffold is merged/open on the branch:

- **Flavors:** `customer` + `admin` in `app/build.gradle.kts` (`flavorDimensions += "audience"`).
- **Source-set isolation:** customer screens/nav/bottom-bar live in `app/src/customer/`; admin screens in `app/src/admin/`. Each flavor defines its own `BazeedAppRoot()` (package `tools.mo3ta.bazeed.navigation`); `MainActivity` in `main` calls it and the compiler resolves per flavor. Admin code never compiles into the customer APK.
- **Shared auth layer (`app/src/main/`):**
  - `data/auth/AuthModels.kt` — `AuthUser`, `UserRole { ADMIN, USER }`.
  - `data/repo/Repositories.kt` — `AuthRepository`, `UserRepository` interfaces + `AuthException`.
  - `data/repo/local/LocalUserStore.kt` — in-memory store, seeded admin + customer.
  - `data/repo/local/LocalRepositories.kt` — `LocalAuthRepository`, `LocalUserRepository`.
  - `data/Repositories.kt` — **the single wiring point** (object `Repositories { auth; users }`).
  - `ui/auth/LoginScreen.kt` — shared email+password UI.
- **Customer flavor:** `app/src/customer/.../navigation/BazeedNav.kt` — login gate → existing shell + sign-out.
- **Admin flavor:** `app/src/admin/.../navigation/BazeedNav.kt` + `ui/screens/` (`AdminDashboardScreen`, `CreateUserScreen`, `UserListScreen`, `NotAuthorizedScreen`); login → role gate → dashboard.
- **Seed creds (local, dev only):** admin `admin@bazeed.app` / `admin123`; customer `user@bazeed.app` / `user123`.

**Key constraint (unchanged):** no custom API, **no Cloud Functions**. Everything is client-side. Roles live in the user record / a Firestore `users/{uid}` doc (NOT Auth custom claims). Admin creates users via the secondary-`FirebaseApp` trick. Authorization is enforced by **Firestore Security Rules**.

---

## Task 1: Make both flavors actually build (Firebase config prerequisite)

**Why:** `google-services.json` is gitignored and the `admin` flavor uses a different `applicationId` (`tools.mo3ta.bazeed.admin`), so the google-services plugin fails for the admin variant until a matching client exists.

**Files:** `app/google-services.json` (local/CI only — gitignored), Firebase console.

- [ ] **Step 1:** In the Firebase console (project `marriyapp`), add a second **Android app** with package name `tools.mo3ta.bazeed.admin`.
- [ ] **Step 2:** Download the regenerated `google-services.json` (it will contain both client entries) and place it at `app/google-services.json`.
- [ ] **Step 3:** Verify: `./gradlew assembleCustomerDebug assembleAdminDebug` both succeed.
- [ ] **Step 4 (isolation check):** Confirm the customer APK has no admin classes, e.g. inspect the dex for `AdminDashboardScreen` / `CreateUserScreen` — they must be **absent** from `customerDebug` and **present** in `adminDebug`.

---

## Task 2: Persist the local store across launches (DataStore)

**Why:** `LocalUserStore` is in-memory only — created accounts vanish on cold start. Keep client-only, just durable, so the prototype is usable on one device before Firebase.

**Files:** `gradle/libs.versions.toml`, `app/build.gradle.kts`, `app/src/main/.../data/repo/local/LocalUserStore.kt` (+ maybe a new `DataStoreUserStore.kt`).

- [ ] **Step 1:** Add `androidx.datastore:datastore-preferences` to the version catalog and `app` deps.
- [ ] **Step 2:** Back `LocalUserStore` with DataStore (serialize the user list + a salted password hash map — do NOT store plaintext passwords even locally; use e.g. a simple hash for the prototype and note it's not production crypto).
- [ ] **Step 3:** Keep the seed (admin + customer) as a first-run initialization only (don't overwrite real data on every launch).
- [ ] **Step 4:** Verify created users survive an app restart (manual).

---

## Task 3: Firebase implementations behind the existing interfaces

**Why:** Move from local to real cross-device accounts without touching screens. This is the payoff of the repository seam.

**Files:** `gradle/libs.versions.toml`, `app/build.gradle.kts`, new `app/src/main/.../data/repo/firebase/FirebaseAuthRepository.kt`, `FirestoreUserRepository.kt`, and `app/src/main/.../data/Repositories.kt` (flip the wiring).

- [ ] **Step 1:** Add `firebase-auth` and `firebase-firestore` to the version catalog (BoM pins versions) and `app` deps. (See spec "Gradle changes".)
- [ ] **Step 2:** `FirebaseAuthRepository` — `signIn` via `FirebaseAuth.signInWithEmailAndPassword`; expose `currentUser` as a `StateFlow<AuthUser?>` by combining the Auth state listener with a read of `users/{uid}` (role comes from Firestore, not claims); `signOut`.
- [ ] **Step 3:** `FirestoreUserRepository`:
  - `users` → snapshot listener on the `users` collection (admin-only by rules).
  - `createUser` → **secondary `FirebaseApp`** pattern: init a named secondary app from the same options, `createUserWithEmailAndPassword` on its `FirebaseAuth` (so the admin's primary session is untouched), capture the new uid, then write `users/{uid}` via the **primary** Firestore instance (admin context), then `signOut()` the secondary auth. See spec "Admin creates a user — client-only path".
  - Handle `FirebaseAuthUserCollisionException` (email exists) and the partial-failure case (Auth user created but Firestore write failed — surface a retry; the write is idempotent by uid).
- [ ] **Step 4:** Flip `data/Repositories.kt` to the Firebase impls.
- [ ] **Step 5:** Admin flavor login: after sign-in, the role gate already checks `user.role != ADMIN` → keep; ensure a non-provisioned account (no `users/{uid}` doc) is treated as not-authorized and signed out.

---

## Task 4: Firestore Security Rules

**Why:** This is the real authorization control. Rules are already written in the spec — deploy them.

**Files:** new `firestore.rules` (repo root), Firebase console / CLI deploy.

- [ ] **Step 1:** Copy the rules from the spec ("Firestore Security Rules" section) into `firestore.rules`.
- [ ] **Step 2:** Deploy (console paste or `firebase deploy --only firestore:rules` if the CLI is set up — note: this is config deploy, NOT Cloud Functions).
- [ ] **Step 3:** Validate the matrices in the spec "Testing" section using the Rules Playground / emulator: provisioned user reads content; unprovisioned reads nothing; a `user` cannot create/escalate their own `users/{uid}`; only `admin` writes content.

---

## Task 5: First-admin bootstrap (one-time, manual)

**Files:** Firebase console only.

- [ ] **Step 1:** Authentication → add the pharmacy owner (email + password).
- [ ] **Step 2:** Firestore → create `users/{thatUid}` with `role: "admin"`, `active: true`.
- [ ] **Step 3:** Verify owner can log into the admin flavor and create further users.

---

## Backlog (separate, smaller PRs — not blocking)

- [ ] Forgot-password / admin-triggered reset via `sendPasswordResetEmail`.
- [ ] Migrate `SampleData.kt` content into a `ContentRepository` (interface + local + Firestore impls), then have the admin `AnnouncementEditor` write and the customer `AnnouncementsScreen` read it.
- [ ] Firebase **App Check** (Play Integrity) to block raw REST sign-up abuse (see spec "Security considerations").
- [ ] Refine the customer sign-out affordance (currently a floating top-end icon).
- [ ] Consider graduating admin to a `:feature-admin` Gradle module if it grows (stronger compile-time wall than source sets).

---

## Conventions / gotchas for the next session

- Use `androidx.compose.runtime.collectAsState()` for `StateFlow` in composables (avoids adding `lifecycle-runtime-compose`).
- Customer and admin screens share the package `tools.mo3ta.bazeed.ui.screens` but live in different source sets — they never co-compile, so there is no collision. Keep it that way.
- Shared/reusable code goes in `main`; anything flavor-specific goes in `customer`/`admin` source sets. Never reference an admin symbol from `main` or `customer`.
- The build cannot be verified in the cloud session (no `google-services.json`); always note this and rely on local/CI builds.
