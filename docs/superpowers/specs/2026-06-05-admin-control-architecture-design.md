# Admin Control Architecture — Design

**Date:** 2026-06-05
**Module:** `app` (`tools.mo3ta.bazeed`)
**Firebase project:** `marriyapp` (config already in `app/google-services.json`)
**Branch:** `claude/admin-control-architecture-5aj8S`

## Goal

Introduce an **admin-provisioned** access model on top of the existing customer pharmacy app:

- **Customer** users can **log in only**. There is no self sign-up anywhere in the customer experience.
- **Admin** users can log in **and create / manage other user accounts**, and manage app content (announcements, monthly service).

Admin functionality must **not ship inside the customer APK**. This is achieved with two Android **product flavors** (`customer`, `admin`) backed by **flavor source sets**, so admin code is never compiled into the customer build.

Constraint that shapes the whole design: **Cloud Functions will NOT be enabled.** The standard "admin creates users via a callable Function + Admin SDK" path is therefore unavailable. We use a **client-only** creation path (secondary `FirebaseApp`) and store roles in **Firestore documents** (not Auth custom claims, which require the Admin SDK).

## Client-only stance (no API, no Cloud Functions)

Everything runs **from the app**. There is:
- **No custom API / backend server** we write or deploy.
- **No Cloud Functions.**
- At most, **Firebase client SDKs** (Auth + Firestore) talking directly to the project, with authorization enforced by **Security Rules**. Firestore is client-direct, not an "API" tier.

To keep the option of **starting with zero cloud at all**, the data layer is defined behind **repository interfaces** (`AuthRepository`, `UserRepository`, `ContentRepository`). Two interchangeable implementations:

| Impl | Backing store | When |
|---|---|---|
| `LocalAuthRepository` / `LocalUserRepository` | in-memory + `DataStore` (single device) | prototype now, no cloud setup — demo flavors, login, admin-creates-user end to end on one device |
| `FirebaseAuthRepository` / `FirestoreUserRepository` | Firebase Auth + Firestore | when cross-device accounts are needed |

Screens and nav depend **only on the interfaces**, so swapping local → Firebase is a wiring change (one DI/factory line per flavor), not a screen rewrite. The flavor split, login UX, role gating, and admin user-creation flow are all identical either way — only the repository binding differs. **Recommended for "client for now": ship the `Local*` impls first**, wire Firebase later when the pharmacy needs accounts to work across phones.

> Caveat for the local impl: accounts created by admin live only on that device (no sync). That is fine for prototyping/demo; real multi-user operation needs the Firebase binding. The secondary-`FirebaseApp` creation trick applies only to the Firebase impl; the local impl just writes a record directly.

## Scope

### In scope
- Two product flavors: `customer` (id `tools.mo3ta.bazeed`) and `admin` (id `tools.mo3ta.bazeed.admin`).
- Flavor source sets that physically separate customer-only and admin-only code.
- **Repository interfaces** (`AuthRepository`, `UserRepository`, `ContentRepository`) with two impls: a **local** (in-memory + `DataStore`) impl for client-only prototyping now, and a **Firebase** impl (Auth + Firestore) for later.
- Email/password login in both flavors.
- Role model (`role` = `"admin" | "user"`) stored in the user record (local store now, `users/{uid}` Firestore doc later).
- Admin user creation — direct write (local) or **client-side secondary `FirebaseApp`** (Firebase), no Functions either way.
- **Firestore Security Rules** for the eventual Firebase binding (authorization enforced server-side).
- Shared login UI + repositories in `main`; flavor-specific post-login routing.

### Out of scope (for this design)
- Programmatic push-send from the admin app (FCM sends require a server key / Admin SDK; continue sending from the Firebase Console per the FCM baseline design). Admin manages **content**; push remains console-driven for now.
- Password reset / "forgot password" flows (can be added later via `sendPasswordResetEmail`).
- Multi-admin role tiers (super-admin vs staff). Single `admin` role for now.
- Migrating existing hardcoded `SampleData.kt` content into Firestore (separate task; this design defines the target, not the migration).
- Offline persistence / caching strategy beyond Firestore defaults.

## Why flavors + source sets (not role-toggle, not modules)

| Concern | Role toggle in one app | **Flavor + source sets (chosen)** | Separate Gradle module |
|---|---|---|---|
| Admin code off customer phones | ❌ ships, gated by client `if` | ✅ never compiled into customer APK | ✅ |
| Setup cost on current single-module app | low | **low** | medium/high |
| Separate `applicationId` / install side-by-side | ❌ | ✅ | ✅ |
| Compile-time visibility wall | ❌ | partial (build-time) | ✅ (strongest) |

Flavor source sets give the needed isolation now with minimal ceremony. We graduate admin to its own `:feature-admin` module later **only if** it grows large or we want a hard compile-time wall. The decisive security control in all cases is server-side (Security Rules), not packaging.

## Source set layout

```
app/src/
├── main/                                   ← SHARED, compiled into BOTH flavors
│   └── java/tools/mo3ta/bazeed/
│       ├── BazeedApp.kt                    (existing — notification channel)
│       ├── MainActivity.kt                 (existing — calls BazeedAppRoot())
│       ├── messaging/                      (existing FCM service)
│       ├── data/
│       │   ├── model/                      User, Announcement, Role, ...
│       │   └── repo/
│       │       ├── AuthRepository.kt       login / signOut / currentUser
│       │       └── FirestoreRepository.kt  announcements + user docs read
│       ├── ui/
│       │   ├── theme/                      (existing)
│       │   ├── components/                 (existing reusable composables)
│       │   └── auth/LoginScreen.kt         shared email+password UI
│       └── (NO BazeedAppRoot here — provided per flavor)
│
├── customer/                               ← ONLY in customer APK
│   ├── java/tools/mo3ta/bazeed/
│   │   ├── BazeedAppRoot.kt                customer nav graph (provides the entry composable)
│   │   └── ui/screens/                     Home, Announcements, MonthlyService, Contact
│   │                                       (MOVED from current main/.../ui/screens)
│   └── res/values/strings.xml              app_name = "بازيد"
│
└── admin/                                  ← ONLY in admin APK
    ├── java/tools/mo3ta/bazeed/
    │   ├── BazeedAppRoot.kt                admin nav graph (provides the entry composable)
    │   └── ui/screens/
    │       ├── AdminDashboardScreen.kt
    │       ├── CreateUserScreen.kt
    │       ├── UserListScreen.kt
    │       └── AnnouncementEditorScreen.kt
    └── res/values/strings.xml              app_name = "بازيد — إدارة"
```

**Flavor entry-point pattern.** `MainActivity` (in `main`) sets its content to a composable `BazeedAppRoot()` with a fixed signature. That composable is **defined separately in each flavor source set** (`customer/.../BazeedAppRoot.kt` and `admin/.../BazeedAppRoot.kt`). The compiler resolves whichever flavor is being built, so the customer build never references admin screens and vice versa. This is the idiomatic Android way to vary the app entry per flavor without an `if`.

**Shared vs flavor split.**
- Shared (`main`): theme, reusable components, data models, `AuthRepository`, `FirestoreRepository`, `LoginScreen`, `BazeedApp`, FCM service, `MainActivity`.
- Customer-only: the four existing screens (moved) + customer nav.
- Admin-only: dashboard, user creation, user list, announcement editor + admin nav.

## Gradle changes

`app/build.gradle.kts` — add flavors:

```kotlin
android {
    namespace = "tools.mo3ta.bazeed"
    // ...
    flavorDimensions += "audience"
    productFlavors {
        create("customer") {
            dimension = "audience"
            applicationId = "tools.mo3ta.bazeed"
        }
        create("admin") {
            dimension = "audience"
            applicationId = "tools.mo3ta.bazeed.admin"
        }
    }
}

dependencies {
    // existing ...
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.auth)        // NEW
    implementation(libs.firebase.firestore)   // NEW
}
```

`gradle/libs.versions.toml` — add (BoM pins the versions, no version literals needed):

```toml
[libraries]
firebase-auth = { group = "com.google.firebase", name = "firebase-auth" }
firebase-firestore = { group = "com.google.firebase", name = "firebase-firestore" }
```

Resulting build variants: `customerDebug`, `customerRelease`, `adminDebug`, `adminRelease`.

**`google-services.json` — required setup step.** The plugin matches clients by package name. Because the admin flavor uses a **different `applicationId` (`tools.mo3ta.bazeed.admin`)**, the Firebase project must have a **second Android app** registered for that package, and `google-services.json` must contain **both** client entries (one file can hold multiple). Action: in the Firebase console add an Android app for `tools.mo3ta.bazeed.admin`, re-download `google-services.json`, replace the file. Until then the `admin` flavor will fail at `processAdminDebugGoogleServices`.

## Auth & role model

### Roles live in Firestore, not custom claims

Custom claims (`setCustomUserClaims`) require the Admin SDK → Cloud Functions, which we are not enabling. Instead:

```
users/{uid} = {
    email:      string,
    role:       "admin" | "user",
    displayName: string?,
    createdBy:  string (admin uid),
    createdAt:  timestamp,
    active:     boolean
}
```

- **Authorization = existence + `role` of `users/{request.auth.uid}`**, evaluated inside Security Rules via `get()`.
- A logged-in user **without** a `users/{uid}` doc is treated as **unprovisioned** → denied all content. This is the key property that makes the model safe even though the Auth layer can technically mint accounts (see Security section).

### Login (both flavors)

Plain Firebase Auth client SDK, shared `AuthRepository`:

```
signIn(email, password) -> FirebaseUser
signOut()
currentUser(): FirebaseUser?
```

`LoginScreen` (in `main`) is identical UI for both flavors; only the **post-login routing differs**:

- **Customer flavor:** on success → customer home. (No role gate strictly needed for navigation, but content reads are still gated by rules.)
- **Admin flavor:** on success → read `users/{uid}`; **if `role != "admin"`, sign out and show "not authorized."** Otherwise → admin dashboard. This is a UX gate; the real enforcement is the Security Rules on every admin write.

### Admin creates a user — client-only path (no Functions)

`createUserWithEmailAndPassword` on the **primary** `FirebaseAuth` would replace the admin's own session with the new user's. The supported workaround without Functions is a **secondary `FirebaseApp`** instance:

```
1. Lazily init a secondary app:
     val secondary = FirebaseApp.initializeApp(context, primaryOptions, "userProvisioning")
     val secondaryAuth = FirebaseAuth.getInstance(secondary)
2. secondaryAuth.createUserWithEmailAndPassword(email, tempPassword)  // admin's primary session untouched
3. newUid = result.user.uid
4. Using the PRIMARY (admin) Firestore instance, write:
     users/{newUid} = { email, role:"user", createdBy: adminUid, createdAt: now(), active: true }
   → rule sees request.auth = admin → allowed.
5. secondaryAuth.signOut()   // discard the new user's secondary session
6. Show the admin the temp password to hand to the customer (admin sets it).
```

The admin **sets the email and a temporary password**; the customer logs in with those. Optional later enhancement: trigger `sendPasswordResetEmail` so the user sets their own.

Why this is safe: the privilege that matters — *who may create a `users/{uid}` role doc* — is enforced by Security Rules (only an admin can), not by possession of the create-user code. Step 2 only creates an inert Auth identity; step 4 (the authorization-bearing write) is gated server-side.

### Bootstrapping the first admin (manual, one-time)

No Functions means no automated seeding. One-time manual steps in the Firebase console:
1. Authentication → Users → **Add user** (email + password) for the pharmacy owner.
2. Firestore → create `users/{thatUid}` with `role: "admin"`, `active: true`.

From then on, that admin provisions everyone else through the admin app.

## Firestore Security Rules

Authorization is enforced here — this is the load-bearing security control.

```
rules_version = '2';
service cloud.firestore {
  match /databases/{db}/documents {

    function isSignedIn() { return request.auth != null; }
    function userDoc(uid) { return get(/databases/$(db)/documents/users/$(uid)).data; }
    function isProvisioned() {
      return isSignedIn()
          && exists(/databases/$(db)/documents/users/$(request.auth.uid));
    }
    function isAdmin() {
      return isProvisioned()
          && userDoc(request.auth.uid).role == 'admin'
          && userDoc(request.auth.uid).active == true;
    }

    // User/role docs: only admins manage; a user may read their own.
    match /users/{uid} {
      allow read:   if isAdmin() || request.auth.uid == uid;
      allow create, update, delete: if isAdmin();
      // NB: create requires isAdmin() → a rogue self-registered Auth account
      // cannot mint its own role doc, so it can never escalate to admin.
    }

    // Content: provisioned users read; only admins write.
    match /announcements/{id} {
      allow read:  if isProvisioned();
      allow write: if isAdmin();
    }
    match /config/{doc} {           // monthly service settings, etc.
      allow read:  if isProvisioned();
      allow write: if isAdmin();
    }
  }
}
```

Properties:
- Self-registered Auth accounts have **no** `users/{uid}` doc → `isProvisioned()` false → read nothing.
- They **cannot** create their own `users/{uid}` (create requires `isAdmin()`) → no privilege escalation.
- Only admins write content; provisioned users read it.

## Data flow

Login (both flavors):
```
LoginScreen (email, password)
   │
   ▼
AuthRepository.signIn ──▶ Firebase Auth
   │
   ▼ (admin flavor only)
read users/{uid} ── role=="admin"? ──no──▶ signOut + "not authorized"
   │ yes
   ▼
BazeedAppRoot (flavor-specific)
```

Admin creates a user (no Functions):
```
CreateUserScreen (email, tempPassword)
   │
   ▼
secondary FirebaseApp.createUserWithEmailAndPassword  ──▶ Auth (new uid)
   │                                                       (admin primary session untouched)
   ▼
primary Firestore.set users/{newUid} {role:"user", createdBy, ...}
   │   (Security Rule: isAdmin() ✓)
   ▼
secondaryAuth.signOut()  ──▶ show temp password to admin
```

Content:
```
Admin: AnnouncementEditor ──write──▶ Firestore announcements/*   (rule: isAdmin)
                                            │
Customer: AnnouncementsScreen ──read──◀─────┘                    (rule: isProvisioned)
```

## Error handling

- **Wrong password / unknown email:** `signIn` throws `FirebaseAuthException`; show a generic "incorrect email or password" (don't reveal which).
- **Admin login by a non-admin:** post-login role read returns non-admin → sign out + "not authorized." No admin screens ever render.
- **Create-user: email already in use:** secondary `createUser` throws `FirebaseAuthUserCollisionException` → show "email already registered." No Firestore write attempted.
- **Create-user: Firestore write fails after Auth user created:** leaves an Auth account with no role doc (inert / unprovisioned). Surface "user created but profile failed — retry"; admin can re-run the profile write (use `set` keyed by the known uid, idempotent). Document as known partial-failure mode (acceptable: the orphan is harmless and re-provisionable).
- **Missing admin client in `google-services.json`:** `processAdminDebugGoogleServices` fails at build — surfaced by Gradle.
- **Offline:** Firestore SDK serves cached reads where available; writes queue and replay. Login requires connectivity (Auth has no offline sign-in).

## Security considerations & residual risk

- **Embedded API key allows raw sign-up at the Auth layer.** The Web API key ships in every APK, and the email/password provider's REST `signUp` endpoint is public. Someone could mint Auth accounts outside our UI. This is **not** an authorization breach: such accounts get no `users/{uid}` role doc and are inert under the rules above.
- **Recommended hardening (no Functions required):**
  - **Firebase App Check** (Play Integrity) — restricts Auth/Firestore access to genuine instances of our apps, blocking raw REST abuse. Strongly recommended.
  - If the pharmacy later upgrades the project to **Identity Platform**, enable "disable public sign-up" to close the Auth-layer hole entirely (independent of Functions).
- **Admin APK reverse-engineering:** even with the create-user code extracted, an attacker still needs a valid **admin** session to write a role doc (rules enforce it). Keep the admin flavor off public stores; distribute internally.
- **Temp passwords** handed admin→customer should be one-time; recommend wiring `sendPasswordResetEmail` in a follow-up so users set their own.

## Files

New:
- `docs/superpowers/specs/2026-06-05-admin-control-architecture-design.md` (this doc)
- `app/src/main/java/tools/mo3ta/bazeed/data/model/User.kt`, `Role.kt`
- `app/src/main/java/tools/mo3ta/bazeed/data/repo/AuthRepository.kt`, `UserRepository.kt`, `ContentRepository.kt` (interfaces)
- `app/src/main/java/tools/mo3ta/bazeed/data/repo/local/LocalAuthRepository.kt`, `LocalUserRepository.kt`, `LocalContentRepository.kt` (client-only, ship first)
- `app/src/main/java/tools/mo3ta/bazeed/data/repo/firebase/FirebaseAuthRepository.kt`, `FirestoreUserRepository.kt`, `FirestoreContentRepository.kt` (wire later)
- `app/src/main/java/tools/mo3ta/bazeed/ui/auth/LoginScreen.kt`
- `app/src/customer/java/tools/mo3ta/bazeed/BazeedAppRoot.kt`
- `app/src/admin/java/tools/mo3ta/bazeed/BazeedAppRoot.kt`
- `app/src/admin/java/tools/mo3ta/bazeed/ui/screens/AdminDashboardScreen.kt`, `CreateUserScreen.kt`, `UserListScreen.kt`, `AnnouncementEditorScreen.kt`
- `app/src/customer/res/values/strings.xml`, `app/src/admin/res/values/strings.xml`
- `firestore.rules` (project root or `firebase/`) — deploy via console for now.

Modified / moved:
- `app/build.gradle.kts` — add `flavorDimensions` + `productFlavors`; add `firebase-auth`, `firebase-firestore`.
- `gradle/libs.versions.toml` — add the two Firebase library coords.
- `app/google-services.json` — add the `tools.mo3ta.bazeed.admin` client (regenerate from console).
- `app/src/main/.../navigation/BazeedNav.kt` — remove `BazeedAppRoot` (now per-flavor); keep shared nav helpers if any.
- The four existing screens move `main/.../ui/screens/` → `customer/.../ui/screens/`.
- `app/src/main/.../MainActivity.kt` — set content to the flavor-provided `BazeedAppRoot()`.

## Testing

Manual / instrumentation:
1. **Build variants exist:** `./gradlew assembleCustomerDebug assembleAdminDebug` both succeed (after `google-services.json` has both clients).
2. **Isolation:** confirm `customerDebug` APK does **not** contain admin classes (e.g. `dexdump`/`./gradlew :app:dependencies` inspection, or verify `AnnouncementEditorScreen` symbol absent from the customer dex).
3. **Customer login:** install `customerDebug`; log in with a provisioned `user`; confirm content loads, no sign-up UI exists.
4. **Customer unprovisioned:** log in with an Auth account that has no `users/{uid}` doc → content denied gracefully.
5. **Admin login gate:** `adminDebug`, log in with a `user` (non-admin) → signed out + "not authorized." Log in with the bootstrapped admin → dashboard.
6. **Create user:** as admin, create `user@test`; verify (a) admin stays logged in, (b) `users/{uid}` doc written with `role:"user"`, (c) new user can log into the **customer** app with the temp password.
7. **Escalation attempt:** as a `user`, attempt to write `users/{ownUid}.role="admin"` via the SDK → rejected by rules.
8. **Rules:** run the Firebase **Rules Playground** / emulator for the matrices in the Security Rules section.

## Open assumptions

- The `marriyapp` Firebase project will have **Authentication (email/password)** and **Firestore** enabled, and **App Check** is recommended but optional for v1.
- A second Firebase Android app for `tools.mo3ta.bazeed.admin` will be registered and `google-services.json` regenerated before the `admin` flavor is built.
- The first admin is seeded manually (console) as described.
- Push notifications continue to be sent from the Firebase Console (FCM baseline); programmatic admin-initiated push is a later, separate effort and intentionally not part of this design (no server key in-app).
- Existing `SampleData.kt` content remains as-is until a separate Firestore migration task.
