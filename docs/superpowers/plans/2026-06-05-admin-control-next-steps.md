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

- [x] **Step 1:** ~~Firebase console: add a second Android app `tools.mo3ta.bazeed.admin`.~~ Done by maintainer.
- [x] **Step 2:** ~~Regenerated `google-services.json` with both clients placed at `app/google-services.json`.~~ Done by maintainer.
- [ ] **Step 3:** Verify: `./gradlew assembleCustomerDebug assembleAdminDebug` both succeed. *(Run locally — the cloud session has no `google-services.json`.)*
- [ ] **Step 4 (isolation check):** Confirm the customer APK has no admin classes, e.g. inspect the dex for `AdminDashboardScreen` / `CreateUserScreen` — they must be **absent** from `customerDebug` and **present** in `adminDebug`.

---

## Task 2: ~~Persist the local store across launches (DataStore)~~ — SKIPPED

**Superseded by Task 3.** Going straight to Firebase makes accounts durable and cross-device, so device-local DataStore persistence is no longer worth building. The local impls (`LocalUserStore`, `LocalAuthRepository`, `LocalUserRepository`) remain in the tree as a no-cloud fallback for UI work; if that fallback is ever used seriously, revisit DataStore then.

---

## Task 3: Firebase implementations behind the existing interfaces — DONE (PR #2)

**Why:** Move from local to real cross-device accounts without touching screens. This is the payoff of the repository seam.

**Files:** `gradle/libs.versions.toml`, `app/build.gradle.kts`, `app/src/main/.../data/repo/firebase/*`, `app/src/main/.../data/Repositories.kt`, `app/src/main/.../BazeedApp.kt`.

- [x] **Step 1:** Added `firebase-auth` + `firebase-firestore` to the catalog and `app` deps.
- [x] **Step 2:** `FirebaseAuthRepository` — `signIn` via Firebase Auth; `currentUser` `StateFlow` driven by an auth-state listener that loads the role from `users/{uid}` (`UserDocs`); unprovisioned/inactive accounts are signed back out.
- [x] **Step 3:** `FirestoreUserRepository` — `users` via a snapshot listener; `createUser` via the **secondary `FirebaseApp`** pattern (primary admin session untouched), then the role doc written by the primary (admin) Firestore; `FirebaseAuthUserCollisionException` handled; secondary signed out in `finally`.
- [x] **Step 4:** `Repositories` now initializes Firebase impls from `BazeedApp.onCreate(this)`.
- [x] **Step 5:** Admin role gate keeps `role != ADMIN → NotAuthorized`; unprovisioned accounts resolve to `currentUser == null`.
- [ ] **Step 6 (verify):** Build locally and smoke-test: admin login → create user → new user logs into customer app; admin session persists through creation. *(Needs `google-services.json` + a bootstrapped admin — see Task 5.)*
- [ ] **Note:** A `TaskAwait.kt` helper provides `Task.await()` without adding `kotlinx-coroutines-play-services`; replace with the library if preferred.

---

## Task 4: Firestore Security Rules — file added (PR #2), deploy pending

**Why:** This is the real authorization control.

**Files:** `firestore.rules` (repo root).

- [x] **Step 1:** `firestore.rules` added at repo root (mirrors the spec).
- [ ] **Step 2:** Deploy (console paste or `firebase deploy --only firestore:rules` — config deploy, NOT Cloud Functions).
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
