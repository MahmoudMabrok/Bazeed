# Admin Announcements — Design

**Date:** 2026-06-07
**Module:** `app` (`tools.mo3ta.bazeed`)
**Firebase project:** `marriyapp`
**Builds on:** `docs/superpowers/specs/2026-06-05-admin-control-architecture-design.md`

## Goal

Let admin users **create, edit, and delete announcements** that appear in the customer feed, with two automated side effects:

1. **Push notification** — every newly created announcement triggers an FCM push to all customer devices.
2. **Auto-expiry** — a daily job deletes announcements past their expiration date.

Expiration date is set by the admin but **not rendered on the customer UI**.

## Constraints inherited from the prior spec

- **No Cloud Functions.** All "server" work runs in GitHub Actions with credentials in GH Secrets.
- **No backend server we write.** Firebase client SDKs + Firestore Security Rules continue to be the authorization control.
- Admin code stays out of the customer APK (flavor source sets — already in place).

## Architectural decision: this is the trigger for Firestore content

The prior spec deferred the "migrate `SampleData.kt` to Firestore" task as a follow-up. This feature **forces** that migration because:

- The daily cleanup script can't reach per-device local stores.
- "FCM to all users" only makes sense across real devices.

So this feature ships `FirestoreContentRepository` as its content backing — there is no `Local` fallback used in production for announcements.

## Data model

Replaces the existing `Announcement` (drops `snippet`, `body`, `featured`, `highlight`, `timeAgoAr`; renames `category` → `type`).

```kotlin
// app/src/main/.../data/Announcement.kt
enum class AnnouncementType(val labelAr: String) {
    Health("صحة"),
    Alert("تنبيه"),
    Tip("نصيحة"),
    Offer("عروض"),
}

data class Announcement(
    val id: String,
    val title: String,
    val description: String,
    val type: AnnouncementType,
    val expirationDate: Long,   // epoch millis — admin-set, not rendered on customer UI
    val createdAt: Long,        // epoch millis — used for "منذ كذا" relative label
)
```

### Firestore document shape

`announcements/{id}`:

| Field | Type | Notes |
|---|---|---|
| `title` | string | required, non-empty |
| `description` | string | required, non-empty |
| `type` | string | `"Health" \| "Alert" \| "Tip" \| "Offer"` (enum name) |
| `expirationDate` | timestamp | required, must be in the future at create time |
| `createdAt` | timestamp | server timestamp, set once on create |
| `notified` | bool | starts `false`; flipped to `true` by the GH Action after FCM send. Not present in the Android model — purely Action-internal. |

## Repository seam

New interface alongside the existing `AuthRepository` / `UserRepository`:

```kotlin
// app/src/main/.../data/repo/Repositories.kt (appended)
interface ContentRepository {
    /** Live snapshot of all current announcements, sorted createdAt desc. */
    val announcements: StateFlow<List<Announcement>>

    /** Admin only by rules. Sets notified=false and createdAt server-side. */
    suspend fun create(a: Announcement): Result<Announcement>

    /** Admin only. Does NOT touch notified or createdAt — edits don't re-push. */
    suspend fun update(id: String, a: Announcement): Result<Unit>

    /** Admin only. Hard delete. */
    suspend fun delete(id: String): Result<Unit>
}
```

Two implementations (parallel to the existing Auth pattern):

- `LocalContentRepository` — in-memory list, useful for dev iteration without the network.
- `FirestoreContentRepository` — `addSnapshotListener` on `announcements/` for reads; `add`/`update`/`delete` for writes.

Wired through `data/Repositories.kt`:

```kotlin
object Repositories {
    val auth: AuthRepository = ...
    val users: UserRepository = ...
    val content: ContentRepository = FirestoreContentRepository()
}
```

## Customer UX changes

`app/src/customer/.../ui/screens/AnnouncementsScreen.kt`:

- Reads `Repositories.content.announcements.collectAsState()` instead of `SampleData.announcements`.
- Removes: `FeaturedAnnouncementCard` usage, search bar, category-filter chips. (These can be re-introduced in a later PR; keeping this one focused.)
- Empty state when the list is empty: a short "لا توجد إعلانات حالياً" message.

`app/src/main/.../ui/components/AnnouncementCard.kt`:

- Renders type chip, time-ago label derived from `createdAt`, title, and a 2-line truncated description.
- `FeaturedAnnouncementCard` deleted entirely.

`SampleData.kt`:

- `featuredAnnouncement` and `announcements` removed.
- `pharmacy` and `benefits` stay (used by other screens).

## Admin UX (new)

```
AdminDashboardScreen
   ├── "إنشاء مستخدم جديد"        (existing)
   ├── "قائمة المستخدمين"          (existing)
   ├── "إدارة الإعلانات"           (NEW → AnnouncementListScreen)
   └── "تسجيل الخروج"              (existing)

AnnouncementListScreen
   ├── FAB (+)                    → AnnouncementEditorScreen (create mode)
   ├── Row tap                    → AnnouncementEditorScreen (edit mode)
   └── Row trailing delete icon   → confirm dialog → ContentRepository.delete

AnnouncementEditorScreen (form, single screen for create + edit)
   ├── Title          (TextField, required)
   ├── Description    (TextField, multiline, required)
   ├── Type           (dropdown, AnnouncementType.values())
   ├── Expiration     (date picker, defaults to today + 7d on create; must be > today)
   └── حفظ / إلغاء
```

- Editor used for both create and edit. Create mode initialises with empty fields and default expiration; edit mode preloads from the announcement.
- Save calls `create` (new) or `update` (existing). Update never touches `notified` or `createdAt`.

## FCM topic subscription

- Customer flavor only. New file `app/src/customer/.../messaging/FcmTopics.kt`:
  ```kotlin
  object FcmTopics {
      fun subscribeAnnouncements() {
          FirebaseMessaging.getInstance().subscribeToTopic("announcements")
      }
  }
  ```
- Called once from the customer `BazeedAppRoot` on composition (idempotent — FCM SDK handles re-subscribes).
- Admin flavor has no equivalent file → admin devices never subscribe → admins don't receive the push. This is intentional separation.
- `BazeedMessagingService` (existing) handles the inbound notification with the existing channel.

## GitHub Actions

Two workflows, both reading the `FIREBASE_SERVICE_ACCOUNT` GH Secret (a service account JSON with Firestore + FCM permissions).

### `.github/workflows/announcements-notify.yml`

```yaml
name: Announcements — notify
on:
  schedule:
    - cron: "*/5 * * * *"   # every 5 minutes
  workflow_dispatch:        # manual trigger for testing
jobs:
  notify:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with: { node-version: '20' }
      - run: npm ci
        working-directory: scripts/announcements
      - run: node notify.mjs
        working-directory: scripts/announcements
        env:
          FIREBASE_SERVICE_ACCOUNT: ${{ secrets.FIREBASE_SERVICE_ACCOUNT }}
```

`scripts/announcements/notify.mjs`:

1. Init `firebase-admin` from the service account JSON (written to a tmpfile from env).
2. Query `firestore().collection('announcements').where('notified', '==', false).get()`.
3. For each doc:
   - `messaging().send({ topic: 'announcements', notification: { title: doc.title, body: doc.description } })`.
   - On success: `doc.ref.update({ notified: true })`.
   - On failure: log and continue — next 5-min run will retry.
4. Idempotent: nothing happens when the un-notified set is empty.

### `.github/workflows/announcements-cleanup.yml`

```yaml
name: Announcements — cleanup
on:
  schedule:
    - cron: "0 3 * * *"     # daily at 03:00 UTC
  workflow_dispatch:
jobs:
  cleanup:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with: { node-version: '20' }
      - run: npm ci
        working-directory: scripts/announcements
      - run: node cleanup.mjs
        working-directory: scripts/announcements
        env:
          FIREBASE_SERVICE_ACCOUNT: ${{ secrets.FIREBASE_SERVICE_ACCOUNT }}
```

`scripts/announcements/cleanup.mjs`:

1. Init Admin SDK.
2. `firestore().collection('announcements').where('expirationDate', '<', new Date()).get()`.
3. Batch delete each doc.
4. Log count deleted.

### `scripts/announcements/package.json`

Pins `firebase-admin` and a single helper for writing the service-account tmpfile. No other dependencies.

## Firestore Security Rules

The block from the prior spec already covers this — no changes needed beyond deploying it:

```
match /announcements/{id} {
  allow read:  if isProvisioned();
  allow write: if isAdmin();
}
```

The Admin SDK in the GH Action **bypasses rules** (it's a service-account, not an end-user). It can therefore:
- Flip `notified` on existing docs without satisfying `isAdmin()`.
- Delete expired docs without a signed-in user.

This is why the rule on `notified` doesn't need a special carve-out for the Action.

## Data flow

Create:

```
Admin: AnnouncementEditor (save)
   │
   ▼
ContentRepository.create  ──▶ Firestore announcements/{newId}
                                  {title, description, type,
                                   expirationDate, createdAt: serverTs,
                                   notified: false}
   │
   ▼ (snapshot listener)
Customer feed updates within seconds.

   ┌─── cron */5 * * * * ──────────────────────────────────────┐
GH Action 'notify'
   ├─ query notified == false
   ├─ for each → FCM topic 'announcements' { title, body: description }
   └─ on send success: doc.update({ notified: true })

Customer device wakes BazeedMessagingService → system notification.
```

Edit:

```
Admin: AnnouncementEditor (save existing)
   │
   ▼
ContentRepository.update(id, fields)
   updates title/description/type/expirationDate;
   leaves createdAt and notified untouched.
   → no re-push (notified stays true).
```

Delete (admin-initiated):

```
Admin: AnnouncementList (trash → confirm)
   │
   ▼
ContentRepository.delete(id) ──▶ Firestore delete.
```

Cleanup (scheduled):

```
   ┌── cron 0 3 * * * ────────────────────────────────────────┐
GH Action 'cleanup'
   ├─ query expirationDate < now
   └─ batch delete
```

## Error handling

| Failure | Behavior |
|---|---|
| Empty title or description | Inline editor error; no Firestore call. |
| Expiration date in the past | Inline editor error; no Firestore call. |
| Firestore write rejected by rules | Surface "غير مصرّح بهذا الإجراء". Admin should already be gated; this is a safety net. |
| GH Action FCM send fails (network, FCM 5xx) | `notified` stays `false`; the next 5-min tick retries. Log the error in the Action log. |
| GH Action runs while no un-notified docs exist | No-op, exits 0. |
| GH Action service-account secret missing/invalid | Workflow fails red in the Actions tab — visible, no silent drop. |
| Customer offline at FCM send | FCM stores the message; delivers on next connection. |
| Customer offline reading feed | Firestore SDK serves cached snapshot until reconnect. |
| Cleanup runs while no expired docs | No-op. |
| Customer hasn't subscribed yet (cold first launch) | Misses any push sent before subscription completes — acceptable: subscription happens on first `onResume`, well before any announcement is created. |

## Testing

Manual end-to-end (the load-bearing checks):

1. **Create + receive push.** As admin: create announcement with expiration +7d. Customer feed shows it within ~5s (snapshot). Within 5 min, customer device receives a push notification with the title + description.
2. **Edit does not re-push.** Edit the announcement title. Customer feed updates within ~5s. **No** second push arrives.
3. **Cleanup.** Create an announcement with `expirationDate = now + 60s`. Wait. Fire `announcements-cleanup` via `workflow_dispatch`. Doc is deleted; customer feed empties.
4. **Topic isolation.** Admin device does **not** receive the push (admin flavor never subscribes).
5. **Non-admin attempts.** Customer user opens the SDK and tries `firestore.collection('announcements').add(...)` → rejected by rules.
6. **Empty state.** With no announcements, the customer screen shows the empty state, not the SampleData fixtures (which are gone).

Unit (light — most value is in the e2e):

- Editor validation: empty title, empty description, past expiration each surface the correct error.
- Time-ago helper: maps a `createdAt` delta to the expected Arabic label ("الآن", "منذ ساعة", "أمس", "منذ X يوم").

## Files

**New:**
- `app/src/main/java/tools/mo3ta/bazeed/data/repo/ContentRepository.kt` (interface — may also live in the existing `Repositories.kt` file)
- `app/src/main/java/tools/mo3ta/bazeed/data/repo/local/LocalContentRepository.kt`
- `app/src/main/java/tools/mo3ta/bazeed/data/repo/firebase/FirestoreContentRepository.kt`
- `app/src/customer/java/tools/mo3ta/bazeed/messaging/FcmTopics.kt`
- `app/src/admin/java/tools/mo3ta/bazeed/ui/screens/AnnouncementListScreen.kt`
- `app/src/admin/java/tools/mo3ta/bazeed/ui/screens/AnnouncementEditorScreen.kt`
- `.github/workflows/announcements-notify.yml`
- `.github/workflows/announcements-cleanup.yml`
- `scripts/announcements/notify.mjs`
- `scripts/announcements/cleanup.mjs`
- `scripts/announcements/package.json`
- `scripts/announcements/.gitignore` (ignore `node_modules/`, tmp service-account file)

**Modified:**
- `app/src/main/java/tools/mo3ta/bazeed/data/Announcement.kt` — rewrite to the new model.
- `app/src/main/java/tools/mo3ta/bazeed/data/SampleData.kt` — remove announcement fixtures; keep `pharmacy` and `benefits`.
- `app/src/main/java/tools/mo3ta/bazeed/ui/components/AnnouncementCard.kt` — simplify; delete `FeaturedAnnouncementCard`.
- `app/src/customer/java/tools/mo3ta/bazeed/ui/screens/AnnouncementsScreen.kt` — read from repo; remove featured/search/filter UI; add empty state.
- `app/src/customer/java/tools/mo3ta/bazeed/ui/screens/HomeScreen.kt` — if it references the removed `featuredAnnouncement`/`SampleData.announcements`, adjust accordingly.
- `app/src/admin/java/tools/mo3ta/bazeed/ui/screens/AdminDashboardScreen.kt` — add "إدارة الإعلانات" button.
- `app/src/admin/java/tools/mo3ta/bazeed/navigation/BazeedNav.kt` — add `AnnouncementList` and `AnnouncementEditor` routes.
- `app/src/customer/java/tools/mo3ta/bazeed/navigation/BazeedNav.kt` — call `FcmTopics.subscribeAnnouncements()` on first composition.
- `app/src/main/java/tools/mo3ta/bazeed/data/Repositories.kt` — add `content` wiring.
- `app/build.gradle.kts` — add `firebase-firestore` (and `firebase-auth` if not already added by the prior spec's Task 3).
- `gradle/libs.versions.toml` — add the two coords.
- `firestore.rules` — deploy the announcements block from the prior spec (file may not exist yet at the repo root).

## Bootstrapping prerequisites

These are environment/config steps that must be done before this feature works end-to-end. They are NOT code changes in this PR:

1. **Firestore enabled** in the `marriyapp` Firebase project.
2. **`firestore.rules` deployed** (the rules block already exists in the prior spec; this just needs to actually land in Firebase).
3. **GH Secret `FIREBASE_SERVICE_ACCOUNT`** populated with a service account JSON having:
   - `roles/datastore.user` (Firestore read/write)
   - `roles/firebase.admin` or `roles/cloudmessaging.messageSender` (FCM HTTP v1 send)
4. **Customer `google-services.json`** already configured (it is — FCM is wired today).
5. **Admin flavor `google-services.json` client** registered per the prior spec's Task 1 (the admin flavor still won't build without it).

## Open assumptions

- The 4-category `type` enum (Health/Alert/Tip/Offer) is the right taxonomy. If you want a different set, the enum rename is one place.
- Time-ago label rendering is good enough as "الآن" / "منذ ساعة" / "أمس" / "منذ ٣ أيام" (no exact timestamps). If precise dates are wanted, swap for a localized formatter.
- The customer's expectations around push timing are "within minutes, not seconds." 5-minute polling is fine. If we need real-time, the only fix without Cloud Functions is to also embed a service account in the admin APK — explicitly not the choice we made.
- GH Actions free tier comfortably absorbs the polling load (~8,640 runs/month at every 5 min; free tier is 2,000 min/month and each run is ~30s).

## Out of scope

- Re-introducing search / category filters on the customer feed (separate, smaller PR if wanted).
- Per-user push targeting (we use topic broadcast).
- Localised push (Arabic/English — title and description go as-is).
- Migrating `Benefit` / pharmacy data to Firestore (separate task; only announcements move).
- Push deep-linking (notification tap opens the app; doesn't navigate to a specific announcement).
- Featured / highlight UX from the prior model. If the pharmacy wants "this week's highlight," that's a follow-up feature.
