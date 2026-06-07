# Admin Announcements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship admin-managed announcements: Firestore-backed CRUD, FCM topic push fan-out on create, and a daily GitHub Action that hard-deletes expired docs.

**Architecture:** Replace the hardcoded `SampleData.announcements` with a `FirestoreContentRepository` exposed via a new `ContentRepository` interface. Admin gets two new screens (list + editor). Customer screen reads the live Firestore snapshot. Two GitHub Actions workflows (every 5 min for FCM; daily for cleanup) use the `firebase-admin` Node SDK with a service-account JSON in GH Secrets — no Cloud Functions.

**Tech Stack:** Kotlin 2.0.21, Jetpack Compose, Firebase BoM 34.14.0 (Auth, Firestore, Messaging), Node 20 + `firebase-admin` for the scheduled workflows.

**Spec:** `docs/superpowers/specs/2026-06-07-admin-announcements-design.md`

---

## Prerequisites (NOT covered by this plan — must be true for the feature to work end-to-end)

These are environment/config steps. The plan ships working code regardless, but the runtime behavior requires:

1. **Firestore enabled** in the `marriyapp` Firebase project.
2. **`google-services.json`** at `app/google-services.json` contains client entries for both `tools.mo3ta.bazeed` and `tools.mo3ta.bazeed.admin` (prior spec's Task 1 — required for the admin flavor to build).
3. **`firestore.rules` deployed** to the project (this plan writes the file; deployment happens via the Firebase console paste or `firebase deploy --only firestore:rules`).
4. **Firebase Auth + User repositories wired.** Prior spec's Task 3 — `FirebaseAuthRepository` / `FirestoreUserRepository` must replace the `Local*` impls in `data/Repositories.kt`. The new content rules require `isProvisioned()`, which needs a real `FirebaseAuth` user with a matching `users/{uid}` doc. If you're still on `Local*` auth, customer reads will be denied by rules.
5. **First admin bootstrapped** — see prior spec's Task 5.
6. **GH Secret `FIREBASE_SERVICE_ACCOUNT`** populated with a service-account JSON granting `roles/datastore.user` + `roles/cloudmessaging.messageSender` (or `roles/firebase.admin`).

If any of these are not yet in place, **stop and complete them first**, then return to this plan.

---

## File map (locks in decomposition before tasks start)

**New files:**

| Path | Responsibility |
|---|---|
| `app/src/main/.../data/repo/ContentRepository.kt` | Interface for content CRUD |
| `app/src/main/.../data/repo/local/LocalContentRepository.kt` | In-memory impl for unit tests + dev |
| `app/src/main/.../data/repo/firebase/FirestoreContentRepository.kt` | Production impl |
| `app/src/main/.../data/TimeAgo.kt` | Pure helper: epoch ms → Arabic relative label |
| `app/src/customer/.../messaging/FcmTopics.kt` | Customer-only topic subscription |
| `app/src/admin/.../ui/screens/AnnouncementListScreen.kt` | Admin: list + delete |
| `app/src/admin/.../ui/screens/AnnouncementEditorScreen.kt` | Admin: create/edit form |
| `app/src/test/java/.../data/TimeAgoTest.kt` | Unit test for TimeAgo |
| `app/src/test/java/.../data/repo/local/LocalContentRepositoryTest.kt` | Unit test for the in-memory repo |
| `firestore.rules` | Security rules at repo root |
| `scripts/announcements/package.json` | Node project pinning `firebase-admin` |
| `scripts/announcements/_init.mjs` | Shared SDK init helper |
| `scripts/announcements/notify.mjs` | FCM fan-out script |
| `scripts/announcements/cleanup.mjs` | Expired-doc deletion script |
| `scripts/announcements/.gitignore` | Ignore `node_modules/` |
| `.github/workflows/announcements-notify.yml` | Cron */5 * * * * |
| `.github/workflows/announcements-cleanup.yml` | Cron 0 3 * * * |

**Modified files:**

| Path | Change |
|---|---|
| `app/src/main/.../data/Announcement.kt` | Rewrite model |
| `app/src/main/.../data/SampleData.kt` | Drop announcement fixtures |
| `app/src/main/.../ui/components/AnnouncementCard.kt` | Simplify; remove `FeaturedAnnouncementCard` |
| `app/src/customer/.../ui/screens/AnnouncementsScreen.kt` | Read from repo; remove featured/search/filter; add empty state |
| `app/src/customer/.../ui/screens/HomeScreen.kt` | Read top-3 from repo instead of `SampleData` |
| `app/src/customer/.../navigation/BazeedNav.kt` | Subscribe to FCM topic on first composition |
| `app/src/admin/.../ui/screens/AdminDashboardScreen.kt` | Add "إدارة الإعلانات" button |
| `app/src/admin/.../navigation/BazeedNav.kt` | Wire the two new routes |
| `app/src/main/.../data/Repositories.kt` | Add `content` wiring |
| `app/src/main/.../data/repo/Repositories.kt` | Add `ContentRepository` to file (or new file — chose new) |
| `app/build.gradle.kts` | Add `firebase-firestore` dep |
| `gradle/libs.versions.toml` | Add `firebase-firestore` coord |

---

## Task 1: Add Firebase Firestore Gradle dependency

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add coordinates to the version catalog**

Edit `gradle/libs.versions.toml`. After line 34 (`firebase-messaging = ...`), insert:

```toml
firebase-firestore = { group = "com.google.firebase", name = "firebase-firestore" }
firebase-auth = { group = "com.google.firebase", name = "firebase-auth" }
kotlinx-coroutines-play-services = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-play-services", version = "1.9.0" }
```

(`firebase-auth` is also added here because the prior spec's Task 3 — the Firebase auth/user impls — is a prerequisite for this feature. If the prerequisite already added it, just leave the existing line. `kotlinx-coroutines-play-services` provides the `.await()` extension on Firebase `Task<T>` that `FirestoreContentRepository` uses in Task 4.)

- [ ] **Step 2: Add dependencies to the app module**

Edit `app/build.gradle.kts`. In the `dependencies { ... }` block, after `implementation(libs.firebase.messaging)`, add:

```kotlin
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)
    implementation(libs.kotlinx.coroutines.play.services)
```

- [ ] **Step 3: Sync & build**

Run: `./gradlew :app:dependencies --configuration customerDebugRuntimeClasspath | grep firebase-firestore`
Expected: a line like `com.google.firebase:firebase-firestore:<version>` appears in the output.

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git -c commit.gpgsign=false commit -m "deps: add firebase-firestore and firebase-auth"
```

---

## Task 2: Rewrite the Announcement data model + add a time-ago helper

**Files:**
- Modify: `app/src/main/java/tools/mo3ta/bazeed/data/Announcement.kt`
- Create: `app/src/main/java/tools/mo3ta/bazeed/data/TimeAgo.kt`
- Create: `app/src/test/java/tools/mo3ta/bazeed/data/TimeAgoTest.kt`

- [ ] **Step 1: Write the failing test for TimeAgo**

Create `app/src/test/java/tools/mo3ta/bazeed/data/TimeAgoTest.kt`:

```kotlin
package tools.mo3ta.bazeed.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeAgoTest {

    private val now = 1_717_000_000_000L // arbitrary fixed "now"

    @Test fun under_a_minute_returns_now_label() {
        assertEquals("الآن", TimeAgo.format(now - 30_000, now))
    }

    @Test fun under_an_hour_returns_minutes() {
        assertEquals("منذ ٥ دقائق", TimeAgo.format(now - 5 * 60_000, now))
    }

    @Test fun under_a_day_returns_hours() {
        assertEquals("منذ ٣ ساعات", TimeAgo.format(now - 3 * 3_600_000, now))
    }

    @Test fun yesterday_returns_yesterday_label() {
        assertEquals("أمس", TimeAgo.format(now - 26 * 3_600_000, now))
    }

    @Test fun multiple_days_returns_days() {
        assertEquals("منذ ٤ أيام", TimeAgo.format(now - 4L * 24 * 3_600_000, now))
    }
}
```

- [ ] **Step 2: Run the test and verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests 'tools.mo3ta.bazeed.data.TimeAgoTest'`
Expected: FAIL — `Unresolved reference: TimeAgo`.

- [ ] **Step 3: Implement TimeAgo**

Create `app/src/main/java/tools/mo3ta/bazeed/data/TimeAgo.kt`:

```kotlin
package tools.mo3ta.bazeed.data

/** Renders an Arabic relative-time label like "الآن", "منذ ٣ ساعات", "أمس", "منذ ٤ أيام". */
object TimeAgo {

    private val arabicDigits = charArrayOf('٠','١','٢','٣','٤','٥','٦','٧','٨','٩')

    fun format(createdAt: Long, now: Long = System.currentTimeMillis()): String {
        val diff = (now - createdAt).coerceAtLeast(0L)
        val minutes = diff / 60_000
        val hours = diff / 3_600_000
        val days = diff / (24 * 3_600_000)
        return when {
            minutes < 1 -> "الآن"
            hours < 1 -> "منذ ${toArabic(minutes)} ${pluralAr(minutes, "دقيقة", "دقيقتين", "دقائق")}"
            days < 1 -> "منذ ${toArabic(hours)} ${pluralAr(hours, "ساعة", "ساعتين", "ساعات")}"
            days < 2 -> "أمس"
            else -> "منذ ${toArabic(days)} ${pluralAr(days, "يوم", "يومين", "أيام")}"
        }
    }

    private fun pluralAr(n: Long, one: String, two: String, plural: String): String =
        when (n) { 1L -> one; 2L -> two; else -> plural }

    private fun toArabic(n: Long): String =
        n.toString().map { arabicDigits[it.digitToInt()] }.joinToString("")
}
```

- [ ] **Step 4: Run the test and verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests 'tools.mo3ta.bazeed.data.TimeAgoTest'`
Expected: PASS — all 5 tests green.

- [ ] **Step 5: Rewrite the Announcement model**

Replace the entire contents of `app/src/main/java/tools/mo3ta/bazeed/data/Announcement.kt` with:

```kotlin
package tools.mo3ta.bazeed.data

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
    val expirationDate: Long, // epoch ms, admin-set, not rendered on customer UI
    val createdAt: Long,      // epoch ms, used for the time-ago label
)

data class PharmacyInfo(
    val nameAr: String = "صيدلية بازيد",
    val nameEn: String = "Bazeed Pharmacy",
    val cityAr: String = "البحيرة، دمنهور",
    val streetAr: String = "شارع الجيش — دمنهور",
    val hoursAr: String = "السبت — الخميس · ٩ ص حتى ١٢ م",
    val phone: String = "045 333 0 333",
    val openNow: Boolean = true,
)

data class Benefit(
    val titleAr: String,
    val detailAr: String,
    val accent: BenefitAccent,
)

enum class BenefitAccent { Green, Terracotta, Saffron, Ink }
```

(Note: `PharmacyInfo` / `Benefit` / `BenefitAccent` kept verbatim — used by other screens. Only `AnnouncementCategory` → `AnnouncementType` and `Announcement` field set changed.)

- [ ] **Step 6: Verify the model compiles in isolation**

Run: `./gradlew :app:compileCustomerDebugKotlin 2>&1 | head -40`
Expected: Errors — `SampleData.kt`, `AnnouncementCard.kt`, `AnnouncementsScreen.kt`, `HomeScreen.kt`, and `BazeedNav.kt` still reference the old `AnnouncementCategory` / `snippet` / `body` / `featuredAnnouncement`. **This is expected** — subsequent tasks fix them. Do not try to silence the errors here.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/tools/mo3ta/bazeed/data/Announcement.kt \
        app/src/main/java/tools/mo3ta/bazeed/data/TimeAgo.kt \
        app/src/test/java/tools/mo3ta/bazeed/data/TimeAgoTest.kt
git -c commit.gpgsign=false commit -m "model: rewrite Announcement; add TimeAgo helper"
```

The build is intentionally red after this commit — Tasks 3–8 bring it back to green.

---

## Task 3: Add `ContentRepository` interface + `LocalContentRepository` (with unit test)

**Files:**
- Modify: `app/src/main/java/tools/mo3ta/bazeed/data/repo/Repositories.kt`
- Create: `app/src/main/java/tools/mo3ta/bazeed/data/repo/local/LocalContentRepository.kt`
- Create: `app/src/test/java/tools/mo3ta/bazeed/data/repo/local/LocalContentRepositoryTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/tools/mo3ta/bazeed/data/repo/local/LocalContentRepositoryTest.kt`:

```kotlin
package tools.mo3ta.bazeed.data.repo.local

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tools.mo3ta.bazeed.data.Announcement
import tools.mo3ta.bazeed.data.AnnouncementType

class LocalContentRepositoryTest {

    private fun sample(id: String, createdAt: Long = 1L) = Announcement(
        id = id,
        title = "t-$id",
        description = "d-$id",
        type = AnnouncementType.Health,
        expirationDate = createdAt + 86_400_000L,
        createdAt = createdAt,
    )

    @Test fun create_appends_and_flow_updates() = runBlocking {
        val repo = LocalContentRepository()
        repo.create(sample("a", createdAt = 100)).getOrThrow()
        repo.create(sample("b", createdAt = 200)).getOrThrow()
        val list = repo.announcements.value
        assertEquals(listOf("b", "a"), list.map { it.id }) // sorted createdAt desc
    }

    @Test fun update_replaces_fields() = runBlocking {
        val repo = LocalContentRepository()
        repo.create(sample("a")).getOrThrow()
        val edited = sample("a").copy(title = "edited")
        repo.update("a", edited).getOrThrow()
        assertEquals("edited", repo.announcements.value.single().title)
    }

    @Test fun delete_removes() = runBlocking {
        val repo = LocalContentRepository()
        repo.create(sample("a")).getOrThrow()
        repo.delete("a").getOrThrow()
        assertTrue(repo.announcements.value.isEmpty())
    }
}
```

- [ ] **Step 2: Run the test and verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests 'tools.mo3ta.bazeed.data.repo.local.LocalContentRepositoryTest'`
Expected: FAIL — `Unresolved reference: LocalContentRepository` and `ContentRepository`.

- [ ] **Step 3: Add the `ContentRepository` interface**

Edit `app/src/main/java/tools/mo3ta/bazeed/data/repo/Repositories.kt`. The file currently looks like:

```kotlin
package tools.mo3ta.bazeed.data.repo

import kotlinx.coroutines.flow.StateFlow
import tools.mo3ta.bazeed.data.auth.AuthUser
import tools.mo3ta.bazeed.data.auth.UserRole
// ...existing AuthRepository, UserRepository, AuthException...
```

Add a new `Announcement` import next to the existing imports:

```kotlin
import tools.mo3ta.bazeed.data.Announcement
```

Then append the new interface to the end of the file (after `class AuthException(...)`):

```kotlin
/**
 * Announcement CRUD. Admin app writes; customer app reads via a live snapshot.
 * Production impl is FirestoreContentRepository; LocalContentRepository is for unit tests.
 */
interface ContentRepository {
    val announcements: StateFlow<List<Announcement>>
    suspend fun create(a: Announcement): Result<Announcement>
    suspend fun update(id: String, a: Announcement): Result<Unit>
    suspend fun delete(id: String): Result<Unit>
}
```

The existing `StateFlow` import is reused — don't add a second one.

- [ ] **Step 4: Implement `LocalContentRepository`**

Create `app/src/main/java/tools/mo3ta/bazeed/data/repo/local/LocalContentRepository.kt`:

```kotlin
package tools.mo3ta.bazeed.data.repo.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tools.mo3ta.bazeed.data.Announcement
import tools.mo3ta.bazeed.data.repo.ContentRepository

/** In-memory ContentRepository for unit tests and developer iteration. */
class LocalContentRepository : ContentRepository {

    private val _announcements = MutableStateFlow<List<Announcement>>(emptyList())
    override val announcements: StateFlow<List<Announcement>> = _announcements.asStateFlow()

    override suspend fun create(a: Announcement): Result<Announcement> {
        _announcements.value = (_announcements.value + a).sortedByDescending { it.createdAt }
        return Result.success(a)
    }

    override suspend fun update(id: String, a: Announcement): Result<Unit> {
        val current = _announcements.value
        val idx = current.indexOfFirst { it.id == id }
        if (idx < 0) return Result.failure(IllegalArgumentException("not found: $id"))
        val replaced = current.toMutableList().apply {
            // Preserve original createdAt; the update form does not edit it.
            set(idx, a.copy(createdAt = current[idx].createdAt))
        }
        _announcements.value = replaced.sortedByDescending { it.createdAt }
        return Result.success(Unit)
    }

    override suspend fun delete(id: String): Result<Unit> {
        _announcements.value = _announcements.value.filterNot { it.id == id }
        return Result.success(Unit)
    }
}
```

- [ ] **Step 5: Run the test and verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests 'tools.mo3ta.bazeed.data.repo.local.LocalContentRepositoryTest'`
Expected: PASS — all 3 tests green.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/tools/mo3ta/bazeed/data/repo/Repositories.kt \
        app/src/main/java/tools/mo3ta/bazeed/data/repo/local/LocalContentRepository.kt \
        app/src/test/java/tools/mo3ta/bazeed/data/repo/local/LocalContentRepositoryTest.kt
git -c commit.gpgsign=false commit -m "content: add ContentRepository + LocalContentRepository"
```

---

## Task 4: Implement `FirestoreContentRepository`

**Files:**
- Create: `app/src/main/java/tools/mo3ta/bazeed/data/repo/firebase/FirestoreContentRepository.kt`

No unit test — this hits Firebase. Verification happens in the manual e2e (Task 17). Skipping a Firebase emulator setup keeps the plan focused.

- [ ] **Step 1: Implement the repository**

Create `app/src/main/java/tools/mo3ta/bazeed/data/repo/firebase/FirestoreContentRepository.kt`:

```kotlin
package tools.mo3ta.bazeed.data.repo.firebase

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import tools.mo3ta.bazeed.data.Announcement
import tools.mo3ta.bazeed.data.AnnouncementType
import tools.mo3ta.bazeed.data.repo.ContentRepository

/**
 * Production ContentRepository backed by Firestore /announcements collection.
 *
 * Reads: a single snapshot listener feeds `announcements`.
 * Writes: ordinary client SDK calls; authorization is the Firestore Security Rules
 *         block (admin can write; provisioned users can read).
 *
 * The `notified` field is set to false on create and is otherwise managed by the
 * scheduled GitHub Action — this class never touches it on update.
 */
class FirestoreContentRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : ContentRepository {

    private val collection = firestore.collection("announcements")

    private val _announcements = MutableStateFlow<List<Announcement>>(emptyList())
    override val announcements: StateFlow<List<Announcement>> = _announcements.asStateFlow()

    init {
        // Live snapshot. Sorted server-side by createdAt desc; rules filter by isProvisioned.
        collection
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                _announcements.value = snap.documents.mapNotNull { it.toAnnouncementOrNull() }
            }
    }

    override suspend fun create(a: Announcement): Result<Announcement> = runCatching {
        val ref = collection.document()
        val data = mapOf(
            "title" to a.title,
            "description" to a.description,
            "type" to a.type.name,
            "expirationDate" to Timestamp(a.expirationDate / 1000, 0),
            "createdAt" to FieldValue.serverTimestamp(),
            "notified" to false,
        )
        ref.set(data).await()
        a.copy(id = ref.id)
    }

    override suspend fun update(id: String, a: Announcement): Result<Unit> = runCatching {
        val data = mapOf(
            "title" to a.title,
            "description" to a.description,
            "type" to a.type.name,
            "expirationDate" to Timestamp(a.expirationDate / 1000, 0),
            // intentionally NOT touching createdAt or notified
        )
        collection.document(id).update(data).await()
        Unit
    }

    override suspend fun delete(id: String): Result<Unit> = runCatching {
        collection.document(id).delete().await()
        Unit
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toAnnouncementOrNull(): Announcement? {
        val title = getString("title") ?: return null
        val description = getString("description") ?: return null
        val typeName = getString("type") ?: return null
        val type = AnnouncementType.entries.firstOrNull { it.name == typeName } ?: return null
        val expiration = getTimestamp("expirationDate")?.toDate()?.time ?: return null
        val created = getTimestamp("createdAt")?.toDate()?.time ?: return null
        return Announcement(
            id = id,
            title = title,
            description = description,
            type = type,
            expirationDate = expiration,
            createdAt = created,
        )
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileCustomerDebugKotlin 2>&1 | grep -E '(FirestoreContentRepository|error)' | head -20`
Expected: No errors **for the FirestoreContentRepository file**. Other files in the project may still have unresolved references from Task 2 — that's expected and fixed in later tasks.

(If the build is fully broken in a way that hides this file's errors, do `grep -A2 'FirestoreContentRepository.kt'` to look specifically.)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/tools/mo3ta/bazeed/data/repo/firebase/FirestoreContentRepository.kt
git -c commit.gpgsign=false commit -m "content: add FirestoreContentRepository"
```

---

## Task 5: Wire `content` into `data/Repositories.kt`; drop `SampleData` announcement fixtures; fix HomeScreen + customer BazeedNav imports

This task brings the build back to green by deleting the old references.

**Files:**
- Modify: `app/src/main/java/tools/mo3ta/bazeed/data/Repositories.kt`
- Modify: `app/src/main/java/tools/mo3ta/bazeed/data/SampleData.kt`
- Modify: `app/src/customer/java/tools/mo3ta/bazeed/ui/screens/HomeScreen.kt`
- Modify: `app/src/customer/java/tools/mo3ta/bazeed/navigation/BazeedNav.kt`

- [ ] **Step 1: Wire the content repo**

Replace the contents of `app/src/main/java/tools/mo3ta/bazeed/data/Repositories.kt` with:

```kotlin
package tools.mo3ta.bazeed.data

import tools.mo3ta.bazeed.data.repo.AuthRepository
import tools.mo3ta.bazeed.data.repo.ContentRepository
import tools.mo3ta.bazeed.data.repo.UserRepository
import tools.mo3ta.bazeed.data.repo.firebase.FirestoreContentRepository
import tools.mo3ta.bazeed.data.repo.local.LocalAuthRepository
import tools.mo3ta.bazeed.data.repo.local.LocalUserRepository

/**
 * The one place repository implementations are chosen.
 *
 * Auth/users are still local in this PR; flip them per the prior spec's Task 3
 * before announcements work end-to-end (Firestore rules require an authenticated,
 * provisioned user).
 */
object Repositories {
    val auth: AuthRepository = LocalAuthRepository()
    val users: UserRepository = LocalUserRepository()
    val content: ContentRepository = FirestoreContentRepository()
}
```

- [ ] **Step 2: Remove announcement fixtures from `SampleData.kt`**

Replace the contents of `app/src/main/java/tools/mo3ta/bazeed/data/SampleData.kt` with:

```kotlin
package tools.mo3ta.bazeed.data

object SampleData {

    val pharmacy = PharmacyInfo()

    val benefits = listOf(
        Benefit(
            titleAr = "توصيل لباب البيت",
            detailAr = "مجاني داخل دمنهور",
            accent = BenefitAccent.Green,
        ),
        Benefit(
            titleAr = "مراجعة الصيدلي",
            detailAr = "قبل كل إمداد شهري",
            accent = BenefitAccent.Terracotta,
        ),
        Benefit(
            titleAr = "تجديد تلقائي",
            detailAr = "لا تنفد أدويتك",
            accent = BenefitAccent.Saffron,
        ),
        Benefit(
            titleAr = "تنبيه قبل التوصيل",
            detailAr = "قبل اليوم بثلاثة أيام",
            accent = BenefitAccent.Ink,
        ),
    )
}
```

- [ ] **Step 3: Update `HomeScreen.kt` to read from the repo**

Replace the contents of `app/src/customer/java/tools/mo3ta/bazeed/ui/screens/HomeScreen.kt` with:

```kotlin
package tools.mo3ta.bazeed.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tools.mo3ta.bazeed.data.Repositories
import tools.mo3ta.bazeed.ui.components.AnnouncementCard
import tools.mo3ta.bazeed.ui.components.BrandHeader
import tools.mo3ta.bazeed.ui.components.LocationPill
import tools.mo3ta.bazeed.ui.components.MonthlyServicePromoCard
import tools.mo3ta.bazeed.ui.components.SectionHeader

@Composable
fun HomeScreen(
    onMonthlyServiceTap: () -> Unit,
    onAnnouncementTap: (String) -> Unit,
    onSeeAllAnnouncements: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val announcements by Repositories.content.announcements.collectAsState()
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(scrollState)
            .padding(bottom = 96.dp)
    ) {
        BrandHeader(
            titleAr = "أهلًا بك",
            captionAr = "صيدلية بازيد · البحيرة"
        )
        Spacer(Modifier.height(4.dp))
        LocationPill(
            text = "دمنهور — شارع الجيش · مفتوحة الآن",
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(Modifier.height(12.dp))

        MonthlyServicePromoCard(onTap = onMonthlyServiceTap)

        SectionHeader(titleAr = "إعلانات بازيد", actionAr = "عرض الكل")
        announcements.take(3).forEach { ann ->
            AnnouncementCard(
                announcement = ann,
                onClick = { onAnnouncementTap(ann.id) }
            )
        }
    }
}
```

- [ ] **Step 4: Remove the stale `SampleData` announcement import from customer `BazeedNav.kt`**

Edit `app/src/customer/java/tools/mo3ta/bazeed/navigation/BazeedNav.kt`. The existing file already imports `SampleData` for `pharmacy.streetAr` and `pharmacy.phone` — keep those. **No code change in this file is needed for this step**, but verify the line `import tools.mo3ta.bazeed.data.SampleData` is still present and is used only by the `MonthlyServiceScreen` and `ContactScreen` callbacks (it is).

- [ ] **Step 5: Build**

Run: `./gradlew :app:compileCustomerDebugKotlin 2>&1 | grep -E '(error|FAIL)' | head -20`
Expected: errors are now only in `AnnouncementCard.kt` (still references `AnnouncementCategory`/`snippet`/etc) and `AnnouncementsScreen.kt`. **Do not** fix them here — Tasks 6 and 7 handle them.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/tools/mo3ta/bazeed/data/Repositories.kt \
        app/src/main/java/tools/mo3ta/bazeed/data/SampleData.kt \
        app/src/customer/java/tools/mo3ta/bazeed/ui/screens/HomeScreen.kt
git -c commit.gpgsign=false commit -m "content: wire FirestoreContentRepository; drop SampleData announcements"
```

---

## Task 6: Simplify `AnnouncementCard`; delete `FeaturedAnnouncementCard`

**Files:**
- Modify: `app/src/main/java/tools/mo3ta/bazeed/ui/components/AnnouncementCard.kt`

- [ ] **Step 1: Replace the file contents**

Replace the entire contents of `app/src/main/java/tools/mo3ta/bazeed/ui/components/AnnouncementCard.kt` with:

```kotlin
package tools.mo3ta.bazeed.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tools.mo3ta.bazeed.data.Announcement
import tools.mo3ta.bazeed.data.AnnouncementType
import tools.mo3ta.bazeed.data.TimeAgo
import tools.mo3ta.bazeed.ui.theme.Almarai
import tools.mo3ta.bazeed.ui.theme.Amiri
import tools.mo3ta.bazeed.ui.theme.Green
import tools.mo3ta.bazeed.ui.theme.GreenSoft
import tools.mo3ta.bazeed.ui.theme.Ink
import tools.mo3ta.bazeed.ui.theme.InkMute
import tools.mo3ta.bazeed.ui.theme.LineSoft
import tools.mo3ta.bazeed.ui.theme.Mint
import tools.mo3ta.bazeed.ui.theme.Mono
import tools.mo3ta.bazeed.ui.theme.Paper2
import tools.mo3ta.bazeed.ui.theme.Saffron
import tools.mo3ta.bazeed.ui.theme.SaffronLight

@Composable
fun AnnouncementCard(
    announcement: Announcement,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val (icon, gradient, onAccent) = typeStyle(announcement.type)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Paper2)
            .border(1.dp, LineSoft, RoundedCornerShape(20.dp))
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(brush = Brush.linearGradient(gradient)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = onAccent,
                modifier = Modifier.size(24.dp)
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = announcement.type.labelAr,
                    fontFamily = Mono,
                    fontSize = 9.sp,
                    color = Green,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.6.sp
                )
                Box(
                    Modifier
                        .size(3.dp)
                        .clip(RoundedCornerShape(50))
                        .background(InkMute)
                )
                Icon(
                    imageVector = Icons.Outlined.AccessTime,
                    contentDescription = null,
                    tint = InkMute,
                    modifier = Modifier.size(10.dp)
                )
                Text(
                    text = TimeAgo.format(announcement.createdAt),
                    fontFamily = Almarai,
                    fontSize = 10.sp,
                    color = InkMute
                )
            }
            Text(
                text = announcement.title,
                fontFamily = Amiri,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Ink,
                lineHeight = 20.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = announcement.description,
                fontFamily = Almarai,
                fontSize = 11.sp,
                color = InkMute,
                lineHeight = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun typeStyle(type: AnnouncementType): Triple<ImageVector, List<Color>, Color> =
    when (type) {
        AnnouncementType.Health -> Triple(Icons.Outlined.Spa, listOf(Mint, GreenSoft), Green)
        AnnouncementType.Alert  -> Triple(Icons.Outlined.Campaign, listOf(SaffronLight, Saffron), Ink)
        AnnouncementType.Tip    -> Triple(Icons.Outlined.MedicalServices, listOf(Mint, GreenSoft), Green)
        AnnouncementType.Offer  -> Triple(Icons.Outlined.LocalOffer, listOf(SaffronLight, Saffron), Ink)
    }

@Composable
fun Divider3dp() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(LineSoft)
    )
}
```

`FeaturedAnnouncementCard` is gone entirely. The `Divider3dp` composable is preserved because other screens may use it.

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileCustomerDebugKotlin 2>&1 | grep -E 'AnnouncementCard|FeaturedAnnouncementCard|error' | head -20`
Expected: no errors in `AnnouncementCard.kt`. The remaining error should be in `AnnouncementsScreen.kt` which still references `FeaturedAnnouncementCard` — Task 7 fixes it.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/tools/mo3ta/bazeed/ui/components/AnnouncementCard.kt
git -c commit.gpgsign=false commit -m "ui: simplify AnnouncementCard; drop FeaturedAnnouncementCard"
```

---

## Task 7: Update customer `AnnouncementsScreen` to read from the repo + empty state

**Files:**
- Modify: `app/src/customer/java/tools/mo3ta/bazeed/ui/screens/AnnouncementsScreen.kt`

- [ ] **Step 1: Replace the file**

Replace the entire contents of `app/src/customer/java/tools/mo3ta/bazeed/ui/screens/AnnouncementsScreen.kt` with:

```kotlin
package tools.mo3ta.bazeed.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tools.mo3ta.bazeed.data.Repositories
import tools.mo3ta.bazeed.ui.components.AnnouncementCard
import tools.mo3ta.bazeed.ui.theme.Almarai
import tools.mo3ta.bazeed.ui.theme.Amiri
import tools.mo3ta.bazeed.ui.theme.Ink
import tools.mo3ta.bazeed.ui.theme.InkMute
import tools.mo3ta.bazeed.ui.theme.LineSoft
import tools.mo3ta.bazeed.ui.theme.Mono
import tools.mo3ta.bazeed.ui.theme.Paper
import tools.mo3ta.bazeed.ui.theme.Paper2
import tools.mo3ta.bazeed.ui.theme.Terracotta

@Composable
fun AnnouncementsScreen(
    onAnnouncementTap: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val announcements by Repositories.content.announcements.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
    ) {
        item { AnnouncementsHeader(count = announcements.size); Spacer(Modifier.height(8.dp)) }
        if (announcements.isEmpty()) {
            item { EmptyState() }
        } else {
            items(announcements, key = { it.id }) { ann ->
                AnnouncementCard(announcement = ann, onClick = { onAnnouncementTap(ann.id) })
            }
        }
    }
}

@Composable
private fun AnnouncementsHeader(count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "ANNOUNCEMENTS · $count",
                fontFamily = Mono,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                color = InkMute,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "إعلانات بازيد",
                fontFamily = Amiri,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Paper2)
                .border(1.dp, LineSoft, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Notifications,
                contentDescription = null,
                tint = Ink,
                modifier = Modifier.size(18.dp),
            )
            if (count > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 2.dp, end = 2.dp)
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(Terracotta)
                        .border(2.dp, Paper, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = count.toString(),
                        fontFamily = Almarai,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = Paper,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "لا توجد إعلانات حالياً",
            fontFamily = Almarai,
            fontSize = 14.sp,
            color = InkMute,
        )
    }
}
```

- [ ] **Step 2: Verify the full build compiles**

Run: `./gradlew :app:compileCustomerDebugKotlin 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL (no errors). If there are still references to removed types, hunt them down with `grep -rn "AnnouncementCategory\|featuredAnnouncement\|FeaturedAnnouncementCard\|\.snippet\|\.body\|\.timeAgoAr" app/src/`.

- [ ] **Step 3: Run unit tests**

Run: `./gradlew :app:testCustomerDebugUnitTest`
Expected: PASS — both `TimeAgoTest` and `LocalContentRepositoryTest` green.

- [ ] **Step 4: Commit**

```bash
git add app/src/customer/java/tools/mo3ta/bazeed/ui/screens/AnnouncementsScreen.kt
git -c commit.gpgsign=false commit -m "customer: AnnouncementsScreen reads from ContentRepository"
```

---

## Task 8: Customer FCM topic subscription

**Files:**
- Create: `app/src/customer/java/tools/mo3ta/bazeed/messaging/FcmTopics.kt`
- Modify: `app/src/customer/java/tools/mo3ta/bazeed/navigation/BazeedNav.kt`

- [ ] **Step 1: Create the topic helper**

Create `app/src/customer/java/tools/mo3ta/bazeed/messaging/FcmTopics.kt`:

```kotlin
package tools.mo3ta.bazeed.messaging

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging

/**
 * Customer-only FCM topic subscriptions. The admin flavor has no equivalent
 * file, so admin devices never subscribe to "announcements" and don't receive
 * the broadcast push.
 *
 * Idempotent — FCM SDK handles repeat subscribes silently.
 */
object FcmTopics {

    private const val TAG = "FcmTopics"
    const val ANNOUNCEMENTS = "announcements"

    fun subscribeAnnouncements() {
        FirebaseMessaging.getInstance().subscribeToTopic(ANNOUNCEMENTS)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) Log.d(TAG, "subscribed to $ANNOUNCEMENTS")
                else Log.w(TAG, "subscribe failed", task.exception)
            }
    }
}
```

- [ ] **Step 2: Call it from the customer `BazeedAppRoot`**

Edit `app/src/customer/java/tools/mo3ta/bazeed/navigation/BazeedNav.kt`.

Add the import alongside the others:

```kotlin
import androidx.compose.runtime.LaunchedEffect
import tools.mo3ta.bazeed.messaging.FcmTopics
```

Then replace the existing `BazeedAppRoot` (around lines 52-60) with:

```kotlin
/** Customer flavor entry point: login gate → customer shell. */
@Composable
fun BazeedAppRoot() {
    LaunchedEffect(Unit) { FcmTopics.subscribeAnnouncements() }
    val user by Repositories.auth.currentUser.collectAsState()
    if (user == null) {
        LoginScreen(title = "صيدلية بازيد", subtitle = "تسجيل الدخول")
    } else {
        CustomerShell(onSignOut = { Repositories.auth.signOut() })
    }
}
```

The subscribe call lives inside `BazeedAppRoot` (not `MainActivity`) so it survives configuration changes without re-firing.

- [ ] **Step 3: Build**

Run: `./gradlew :app:assembleCustomerDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL. (Will fail if `google-services.json` lacks a client for `tools.mo3ta.bazeed` — that's prerequisite #2.)

- [ ] **Step 4: Commit**

```bash
git add app/src/customer/java/tools/mo3ta/bazeed/messaging/FcmTopics.kt \
        app/src/customer/java/tools/mo3ta/bazeed/navigation/BazeedNav.kt
git -c commit.gpgsign=false commit -m "fcm: customer subscribes to 'announcements' topic on app start"
```

---

## Task 9: Admin `AnnouncementEditorScreen`

**Files:**
- Create: `app/src/admin/java/tools/mo3ta/bazeed/ui/screens/AnnouncementEditorScreen.kt`

- [ ] **Step 1: Create the editor**

Create `app/src/admin/java/tools/mo3ta/bazeed/ui/screens/AnnouncementEditorScreen.kt`:

```kotlin
package tools.mo3ta.bazeed.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import tools.mo3ta.bazeed.data.Announcement
import tools.mo3ta.bazeed.data.AnnouncementType
import tools.mo3ta.bazeed.data.Repositories
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

private const val DEFAULT_EXPIRATION_DAYS = 7L
private const val MS_PER_DAY = 24L * 60 * 60 * 1000

/**
 * Create-or-edit form. If [existing] is null, this is create mode; otherwise edit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnouncementEditorScreen(
    existing: Announcement?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val isEdit = existing != null

    var title by remember { mutableStateOf(existing?.title ?: "") }
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var type by remember { mutableStateOf(existing?.type ?: AnnouncementType.Health) }
    var expirationMs by remember {
        mutableStateOf(existing?.expirationDate ?: (System.currentTimeMillis() + DEFAULT_EXPIRATION_DAYS * MS_PER_DAY))
    }

    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var typeMenuExpanded by remember { mutableStateOf(false) }
    var datePickerOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = if (isEdit) "تعديل إعلان" else "إعلان جديد",
            style = MaterialTheme.typography.headlineSmall,
        )

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("العنوان") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("الوصف") },
            minLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )

        ExposedDropdownMenuBox(
            expanded = typeMenuExpanded,
            onExpandedChange = { typeMenuExpanded = !typeMenuExpanded },
        ) {
            OutlinedTextField(
                readOnly = true,
                value = type.labelAr,
                onValueChange = {},
                label = { Text("النوع") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeMenuExpanded) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(
                expanded = typeMenuExpanded,
                onDismissRequest = { typeMenuExpanded = false },
            ) {
                AnnouncementType.entries.forEach { t ->
                    DropdownMenuItem(
                        text = { Text(t.labelAr) },
                        onClick = { type = t; typeMenuExpanded = false },
                    )
                }
            }
        }

        OutlinedButton(
            onClick = { datePickerOpen = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("تاريخ الانتهاء: ${formatDate(expirationMs)}")
        }

        if (datePickerOpen) {
            val state = rememberDatePickerState(initialSelectedDateMillis = expirationMs)
            DatePickerDialog(
                onDismissRequest = { datePickerOpen = false },
                confirmButton = {
                    TextButton(onClick = {
                        state.selectedDateMillis?.let { expirationMs = it }
                        datePickerOpen = false
                    }) { Text("تأكيد") }
                },
                dismissButton = {
                    TextButton(onClick = { datePickerOpen = false }) { Text("إلغاء") }
                },
            ) { DatePicker(state = state) }
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Spacer(Modifier.height(8.dp))

        Button(
            enabled = !saving,
            onClick = {
                val v = validate(title, description, expirationMs)
                if (v != null) { error = v; return@Button }
                error = null
                saving = true
                scope.launch {
                    val ann = Announcement(
                        id = existing?.id ?: UUID.randomUUID().toString(),
                        title = title.trim(),
                        description = description.trim(),
                        type = type,
                        expirationDate = expirationMs,
                        createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                    )
                    val result = if (isEdit) {
                        Repositories.content.update(ann.id, ann)
                    } else {
                        Repositories.content.create(ann).map { }
                    }
                    saving = false
                    result.fold(
                        onSuccess = { onSaved() },
                        onFailure = { error = it.message ?: "تعذّر الحفظ" },
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (saving) "جاري الحفظ..." else "حفظ") }

        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("إلغاء")
        }
    }
}

private fun validate(title: String, description: String, expirationMs: Long): String? {
    if (title.isBlank()) return "العنوان مطلوب"
    if (description.isBlank()) return "الوصف مطلوب"
    if (expirationMs <= System.currentTimeMillis()) return "تاريخ الانتهاء يجب أن يكون في المستقبل"
    return null
}

private fun formatDate(ms: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date(ms))
```

- [ ] **Step 2: Build admin variant**

Run: `./gradlew :app:compileAdminDebugKotlin 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL for this file. (Errors from missing `AnnouncementListScreen` and the dashboard route still expected — handled in Tasks 10 and 11.)

- [ ] **Step 3: Commit**

```bash
git add app/src/admin/java/tools/mo3ta/bazeed/ui/screens/AnnouncementEditorScreen.kt
git -c commit.gpgsign=false commit -m "admin: AnnouncementEditorScreen (create + edit)"
```

---

## Task 10: Admin `AnnouncementListScreen`

**Files:**
- Create: `app/src/admin/java/tools/mo3ta/bazeed/ui/screens/AnnouncementListScreen.kt`

- [ ] **Step 1: Create the screen**

Create `app/src/admin/java/tools/mo3ta/bazeed/ui/screens/AnnouncementListScreen.kt`:

```kotlin
package tools.mo3ta.bazeed.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import tools.mo3ta.bazeed.data.Announcement
import tools.mo3ta.bazeed.data.Repositories
import tools.mo3ta.bazeed.data.TimeAgo

@Composable
fun AnnouncementListScreen(
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (Announcement) -> Unit,
) {
    val items by Repositories.content.announcements.collectAsState()
    val scope = rememberCoroutineScope()
    var pendingDelete by remember { mutableStateOf<Announcement?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onCreate) {
                Icon(Icons.Outlined.Add, contentDescription = "إعلان جديد")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("إدارة الإعلانات", style = MaterialTheme.typography.headlineSmall)

            OutlinedButton(onClick = onBack) { Text("رجوع") }

            if (items.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("لا توجد إعلانات. اضغط + لإضافة إعلان جديد.")
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(items, key = { it.id }) { ann ->
                        AdminAnnouncementRow(
                            announcement = ann,
                            onTap = { onEdit(ann) },
                            onDelete = { pendingDelete = ann },
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { ann ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("حذف الإعلان؟") },
            text = { Text("سيتم حذف \"${ann.title}\" نهائياً.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    scope.launch { Repositories.content.delete(ann.id) }
                }) { Text("حذف") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("إلغاء") }
            },
        )
    }
}

@Composable
private fun AdminAnnouncementRow(
    announcement: Announcement,
    onTap: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onTap)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(announcement.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(
                "${announcement.type.labelAr} · ${TimeAgo.format(announcement.createdAt)}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.Delete, contentDescription = "حذف")
        }
    }
}
```

- [ ] **Step 2: Build admin variant**

Run: `./gradlew :app:compileAdminDebugKotlin 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/admin/java/tools/mo3ta/bazeed/ui/screens/AnnouncementListScreen.kt
git -c commit.gpgsign=false commit -m "admin: AnnouncementListScreen with delete confirmation"
```

---

## Task 11: Wire admin navigation routes + dashboard button

**Files:**
- Modify: `app/src/admin/java/tools/mo3ta/bazeed/ui/screens/AdminDashboardScreen.kt`
- Modify: `app/src/admin/java/tools/mo3ta/bazeed/navigation/BazeedNav.kt`

- [ ] **Step 1: Add the dashboard button**

Edit `app/src/admin/java/tools/mo3ta/bazeed/ui/screens/AdminDashboardScreen.kt`. The current signature is:

```kotlin
fun AdminDashboardScreen(
    admin: AuthUser,
    onCreateUser: () -> Unit,
    onViewUsers: () -> Unit,
    onSignOut: () -> Unit,
)
```

Change it to:

```kotlin
fun AdminDashboardScreen(
    admin: AuthUser,
    onCreateUser: () -> Unit,
    onViewUsers: () -> Unit,
    onManageAnnouncements: () -> Unit,
    onSignOut: () -> Unit,
)
```

Add the button after the existing `OutlinedButton(onClick = onViewUsers, ...)`. Insert before the `Spacer(Modifier.height(8.dp))`:

```kotlin
        OutlinedButton(onClick = onManageAnnouncements, modifier = Modifier.fillMaxWidth()) {
            Text("إدارة الإعلانات")
        }
```

- [ ] **Step 2: Wire the new routes in admin `BazeedNav.kt`**

Replace the entire contents of `app/src/admin/java/tools/mo3ta/bazeed/navigation/BazeedNav.kt` with:

```kotlin
package tools.mo3ta.bazeed.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import tools.mo3ta.bazeed.data.Announcement
import tools.mo3ta.bazeed.data.Repositories
import tools.mo3ta.bazeed.data.auth.AuthUser
import tools.mo3ta.bazeed.data.auth.UserRole
import tools.mo3ta.bazeed.ui.auth.LoginScreen
import tools.mo3ta.bazeed.ui.screens.AdminDashboardScreen
import tools.mo3ta.bazeed.ui.screens.AnnouncementEditorScreen
import tools.mo3ta.bazeed.ui.screens.AnnouncementListScreen
import tools.mo3ta.bazeed.ui.screens.CreateUserScreen
import tools.mo3ta.bazeed.ui.screens.NotAuthorizedScreen
import tools.mo3ta.bazeed.ui.screens.UserListScreen
import tools.mo3ta.bazeed.ui.theme.Sand

/** Admin flavor entry point: login → role gate → admin shell. */
@Composable
fun BazeedAppRoot() {
    val user by Repositories.auth.currentUser.collectAsState()
    val current = user
    when {
        current == null ->
            LoginScreen(title = "بازيد — الإدارة", subtitle = "دخول المسؤول")

        current.role != UserRole.ADMIN ->
            NotAuthorizedScreen(onSignOut = { Repositories.auth.signOut() })

        else ->
            AdminShell(admin = current, onSignOut = { Repositories.auth.signOut() })
    }
}

private sealed interface AdminRoute {
    data object Dashboard : AdminRoute
    data object CreateUser : AdminRoute
    data object Users : AdminRoute
    data object Announcements : AdminRoute
    data class AnnouncementEditor(val existing: Announcement?) : AdminRoute
}

@Composable
private fun AdminShell(admin: AuthUser, onSignOut: () -> Unit) {
    var route by remember { mutableStateOf<AdminRoute>(AdminRoute.Dashboard) }

    Scaffold(containerColor = Sand) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val r = route) {
                AdminRoute.Dashboard -> AdminDashboardScreen(
                    admin = admin,
                    onCreateUser = { route = AdminRoute.CreateUser },
                    onViewUsers = { route = AdminRoute.Users },
                    onManageAnnouncements = { route = AdminRoute.Announcements },
                    onSignOut = onSignOut,
                )

                AdminRoute.CreateUser -> CreateUserScreen(
                    onBack = { route = AdminRoute.Dashboard },
                )

                AdminRoute.Users -> UserListScreen(
                    onBack = { route = AdminRoute.Dashboard },
                )

                AdminRoute.Announcements -> AnnouncementListScreen(
                    onBack = { route = AdminRoute.Dashboard },
                    onCreate = { route = AdminRoute.AnnouncementEditor(existing = null) },
                    onEdit = { ann -> route = AdminRoute.AnnouncementEditor(existing = ann) },
                )

                is AdminRoute.AnnouncementEditor -> AnnouncementEditorScreen(
                    existing = r.existing,
                    onBack = { route = AdminRoute.Announcements },
                    onSaved = { route = AdminRoute.Announcements },
                )
            }
        }
    }
}
```

- [ ] **Step 3: Build admin variant end-to-end**

Run: `./gradlew :app:assembleAdminDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL. (Requires prerequisite #2 — admin client in `google-services.json`.)

- [ ] **Step 4: Commit**

```bash
git add app/src/admin/java/tools/mo3ta/bazeed/ui/screens/AdminDashboardScreen.kt \
        app/src/admin/java/tools/mo3ta/bazeed/navigation/BazeedNav.kt
git -c commit.gpgsign=false commit -m "admin: wire announcement list + editor routes"
```

---

## Task 12: Write `firestore.rules`

**Files:**
- Create: `firestore.rules` (repo root — referenced by existing `firebase.json`)

- [ ] **Step 1: Create the rules file**

Create `firestore.rules` at the repo root with:

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
    }

    // Announcements: provisioned users read; only admins write.
    // The 'notified' field is flipped by the GH Action's Admin SDK (bypasses rules).
    match /announcements/{id} {
      allow read:  if isProvisioned();
      allow write: if isAdmin();
    }

    // Future content (monthly service config, etc.)
    match /config/{doc} {
      allow read:  if isProvisioned();
      allow write: if isAdmin();
    }
  }
}
```

- [ ] **Step 2: Deploy (manual step, NOT part of the commit)**

Either paste the file content into the Firebase Console (`marriyapp` → Firestore → Rules → Publish), or if the Firebase CLI is installed:

```bash
firebase deploy --only firestore:rules --project marriyapp
```

Verify in the console that the rules version timestamp updated.

- [ ] **Step 3: Commit**

```bash
git add firestore.rules
git -c commit.gpgsign=false commit -m "rules: announcements read=isProvisioned, write=isAdmin"
```

---

## Task 13: Node script scaffolding

**Files:**
- Create: `scripts/announcements/package.json`
- Create: `scripts/announcements/.gitignore`
- Create: `scripts/announcements/_init.mjs`

- [ ] **Step 1: Create the gitignore**

Create `scripts/announcements/.gitignore`:

```
node_modules/
service-account.json
package-lock.json
```

(We allow `package-lock.json` to be optional — pin via `npm ci` in CI but don't insist locally.)

Actually, **delete `package-lock.json` from that gitignore** — CI uses `npm ci` which **requires** a lockfile. Replace with just:

```
node_modules/
service-account.json
```

- [ ] **Step 2: Create the `package.json`**

Create `scripts/announcements/package.json`:

```json
{
  "name": "bazeed-announcements-scripts",
  "private": true,
  "type": "module",
  "version": "1.0.0",
  "description": "Scheduled GitHub Action scripts for Bazeed announcements (FCM fan-out + expiry cleanup).",
  "scripts": {
    "notify": "node notify.mjs",
    "cleanup": "node cleanup.mjs"
  },
  "dependencies": {
    "firebase-admin": "^12.7.0"
  },
  "engines": {
    "node": ">=20"
  }
}
```

- [ ] **Step 3: Create the shared init helper**

Create `scripts/announcements/_init.mjs`:

```javascript
import { initializeApp, cert } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";

/**
 * Initialise firebase-admin from the FIREBASE_SERVICE_ACCOUNT env var (a JSON string).
 * Both scripts call this once at startup. Exits with code 2 if the secret is missing
 * so the GitHub Action surfaces a visible failure rather than silently no-op'ing.
 */
function initAdmin() {
  const raw = process.env.FIREBASE_SERVICE_ACCOUNT;
  if (!raw) {
    console.error("FIREBASE_SERVICE_ACCOUNT env var is missing");
    process.exit(2);
  }
  let credential;
  try {
    credential = cert(JSON.parse(raw));
  } catch (err) {
    console.error("FIREBASE_SERVICE_ACCOUNT is not valid JSON:", err.message);
    process.exit(2);
  }
  return initializeApp({ credential });
}

export const app = initAdmin();
export const firestore = getFirestore(app);
export const messaging = getMessaging(app);
export const TOPIC_ANNOUNCEMENTS = "announcements";
```

- [ ] **Step 4: Install dependencies and generate the lockfile**

Run:

```bash
cd scripts/announcements && npm install && cd ../..
```

Expected: `package-lock.json` is created; `node_modules/` is populated (and ignored by git).

- [ ] **Step 5: Commit**

```bash
git add scripts/announcements/package.json \
        scripts/announcements/package-lock.json \
        scripts/announcements/.gitignore \
        scripts/announcements/_init.mjs
git -c commit.gpgsign=false commit -m "scripts: announcements/_init.mjs + package.json"
```

---

## Task 14: `notify.mjs`

**Files:**
- Create: `scripts/announcements/notify.mjs`

- [ ] **Step 1: Create the script**

Create `scripts/announcements/notify.mjs`:

```javascript
import { firestore, messaging, TOPIC_ANNOUNCEMENTS } from "./_init.mjs";

/**
 * Pick up announcements where notified == false, send one FCM each to topic
 * 'announcements', then flip notified = true. Idempotent — safe to re-run.
 *
 * On send failure for a given doc: log + skip (leave notified=false) so the
 * next 5-min tick retries. Other docs in the batch still process.
 */
async function main() {
  const snap = await firestore
    .collection("announcements")
    .where("notified", "==", false)
    .get();

  if (snap.empty) {
    console.log("no un-notified announcements");
    return;
  }

  console.log(`processing ${snap.size} announcement(s)`);
  let sent = 0;
  let failed = 0;

  for (const doc of snap.docs) {
    const data = doc.data();
    const title = data.title;
    const body = data.description;
    if (!title || !body) {
      console.warn(`skipping ${doc.id}: missing title/description`);
      continue;
    }
    try {
      const messageId = await messaging.send({
        topic: TOPIC_ANNOUNCEMENTS,
        notification: { title, body },
      });
      await doc.ref.update({ notified: true });
      console.log(`sent ${doc.id} → ${messageId}`);
      sent++;
    } catch (err) {
      console.error(`send failed for ${doc.id}: ${err.message}`);
      failed++;
    }
  }

  console.log(`done: sent=${sent} failed=${failed}`);
  if (failed > 0) process.exit(1); // make the GH Action turn red on partial failure
}

main().catch((err) => {
  console.error("fatal:", err);
  process.exit(1);
});
```

- [ ] **Step 2: Smoke-check syntax**

Run: `node --check scripts/announcements/notify.mjs`
Expected: no output (success). If there's a syntax error, fix it before committing.

- [ ] **Step 3: Commit**

```bash
git add scripts/announcements/notify.mjs
git -c commit.gpgsign=false commit -m "scripts: notify.mjs — FCM fan-out for un-notified announcements"
```

---

## Task 15: `cleanup.mjs`

**Files:**
- Create: `scripts/announcements/cleanup.mjs`

- [ ] **Step 1: Create the script**

Create `scripts/announcements/cleanup.mjs`:

```javascript
import { firestore } from "./_init.mjs";

/**
 * Hard-delete announcements whose expirationDate has passed.
 * Batched at 400/commit (Firestore batch limit is 500; leave headroom).
 */
async function main() {
  const now = new Date();
  const snap = await firestore
    .collection("announcements")
    .where("expirationDate", "<", now)
    .get();

  if (snap.empty) {
    console.log("no expired announcements");
    return;
  }

  console.log(`deleting ${snap.size} expired announcement(s)`);
  const BATCH_SIZE = 400;
  for (let i = 0; i < snap.docs.length; i += BATCH_SIZE) {
    const batch = firestore.batch();
    snap.docs.slice(i, i + BATCH_SIZE).forEach((d) => batch.delete(d.ref));
    await batch.commit();
  }
  console.log(`done: deleted=${snap.size}`);
}

main().catch((err) => {
  console.error("fatal:", err);
  process.exit(1);
});
```

- [ ] **Step 2: Smoke-check syntax**

Run: `node --check scripts/announcements/cleanup.mjs`
Expected: no output.

- [ ] **Step 3: Commit**

```bash
git add scripts/announcements/cleanup.mjs
git -c commit.gpgsign=false commit -m "scripts: cleanup.mjs — hard-delete expired announcements"
```

---

## Task 16: GitHub Actions workflows

**Files:**
- Create: `.github/workflows/announcements-notify.yml`
- Create: `.github/workflows/announcements-cleanup.yml`

- [ ] **Step 1: Notify workflow**

Create `.github/workflows/announcements-notify.yml`:

```yaml
name: Announcements — notify
on:
  schedule:
    - cron: "*/5 * * * *"   # every 5 minutes
  workflow_dispatch: {}     # manual trigger for testing

concurrency:
  group: announcements-notify
  cancel-in-progress: false

jobs:
  notify:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up Node
        uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: 'npm'
          cache-dependency-path: scripts/announcements/package-lock.json

      - name: Install
        working-directory: scripts/announcements
        run: npm ci

      - name: Run notify
        working-directory: scripts/announcements
        env:
          FIREBASE_SERVICE_ACCOUNT: ${{ secrets.FIREBASE_SERVICE_ACCOUNT }}
        run: node notify.mjs
```

- [ ] **Step 2: Cleanup workflow**

Create `.github/workflows/announcements-cleanup.yml`:

```yaml
name: Announcements — cleanup
on:
  schedule:
    - cron: "0 3 * * *"     # daily at 03:00 UTC
  workflow_dispatch: {}

jobs:
  cleanup:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up Node
        uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: 'npm'
          cache-dependency-path: scripts/announcements/package-lock.json

      - name: Install
        working-directory: scripts/announcements
        run: npm ci

      - name: Run cleanup
        working-directory: scripts/announcements
        env:
          FIREBASE_SERVICE_ACCOUNT: ${{ secrets.FIREBASE_SERVICE_ACCOUNT }}
        run: node cleanup.mjs
```

- [ ] **Step 3: Lint the YAML**

Run: `python3 -c 'import yaml,sys; [yaml.safe_load(open(p)) for p in sys.argv[1:]]' .github/workflows/announcements-notify.yml .github/workflows/announcements-cleanup.yml && echo OK`
Expected: `OK`.

- [ ] **Step 4: Commit and push**

```bash
git add .github/workflows/announcements-notify.yml .github/workflows/announcements-cleanup.yml
git -c commit.gpgsign=false commit -m "ci: announcements-notify and announcements-cleanup workflows"
git push
```

Pushing matters here: the GH Actions schedule only activates after the workflow YAML is on the default branch.

---

## Task 17: Manual end-to-end verification

This task isn't code — it confirms the system works once all prerequisites are met. Do not commit anything; just check boxes.

- [ ] **Step 1: Confirm prerequisites are live**

- [ ] Firestore enabled in the `marriyapp` project (Firebase console → Firestore Database).
- [ ] `firestore.rules` published (the timestamp shown in the rules tab matches your recent deploy).
- [ ] GH Secret `FIREBASE_SERVICE_ACCOUNT` is set in the repo (Settings → Secrets → Actions).
- [ ] `Repositories.auth` and `Repositories.users` already point at the Firebase impls (prior spec Task 3). If still on `Local*`, customer reads will be denied by rules.
- [ ] First admin exists in Firebase Auth and has `users/{uid}.role = "admin", active = true`.
- [ ] `google-services.json` has clients for both `tools.mo3ta.bazeed` and `tools.mo3ta.bazeed.admin`.

- [ ] **Step 2: Create + receive push**

Install the admin APK (`./gradlew installAdminDebug`), log in as the admin. Tap "إدارة الإعلانات" → FAB → fill: title "اختبار", description "هذا اختبار", type "نصيحة", expiration today + 7d. Save.

Then on a customer device: install `./gradlew installCustomerDebug`, log in as a provisioned customer user. Open the app at least once so the topic subscription completes.

- [ ] The announcement appears on the customer's announcements screen within ~5 seconds.
- [ ] Within 5 minutes, the customer device shows a system notification with the title "اختبار" and body "هذا اختبار".

If the push doesn't arrive within ~10 min:
- Check the `Announcements — notify` Actions run log. A successful run prints `sent <docId>`.
- Check the Firestore doc — `notified` should be `true` after the action sent.
- If `notified` is still `false`, the FCM call failed: look at the action log for `send failed for <id>: <reason>` (often invalid service account permissions).

- [ ] **Step 3: Edit does not re-push**

Edit the test announcement (change the title). Save.

- [ ] Customer screen updates within ~5 seconds with the new title.
- [ ] **No** second system notification arrives within the next 10 minutes. (`notified` stays `true`.)

- [ ] **Step 4: Cleanup**

Create a new announcement with `expirationDate = (today + 1 day)`. Manually edit the Firestore doc (or create one via console) so `expirationDate` is in the past (e.g., yesterday).

Trigger the cleanup workflow manually: GitHub → Actions → `Announcements — cleanup` → Run workflow.

- [ ] The Actions run prints `deleting 1 expired announcement(s)` and `done: deleted=1`.
- [ ] The Firestore doc is gone.
- [ ] The customer screen no longer shows the announcement (within ~5 seconds).

- [ ] **Step 5: Admin device does not receive the push**

Confirm that during Step 2, the *admin* device's notification tray did **not** receive the announcement push. This validates the topic-isolation property — admin flavor never subscribes.

- [ ] **Step 6: Rules deny non-admin writes**

In the Firebase Rules Playground (Console → Firestore → Rules → Playground):
- Path: `/announcements/test123`
- Operation: `create`
- Authenticated: `request.auth.uid = "<a non-admin user uid>"`
- Resource data: any valid shape

- [ ] Playground reports the simulation **denied**.

- [ ] **Step 7: Final commit (cleanup test docs only)**

If you created test announcements that you'd like gone, delete them through the admin app. No code commits in this step.

---

## Self-review checklist (for the plan author — already done)

**Spec coverage:**
- Data model rewrite → Task 2 ✓
- `ContentRepository` interface + Local impl → Task 3 ✓
- Firestore impl → Task 4 ✓
- Customer screen + Home → Tasks 5, 7 ✓
- Admin editor + list → Tasks 9, 10 ✓
- Admin nav + dashboard button → Task 11 ✓
- FCM topic subscription → Task 8 ✓
- Security rules → Task 12 ✓
- Notify workflow + script → Tasks 13–14, 16 ✓
- Cleanup workflow + script → Tasks 13, 15, 16 ✓
- Empty state → Task 7 ✓
- Time-ago label → Task 2 (TimeAgo) ✓
- Hard delete on cleanup → Task 15 ✓
- No re-notify on edit → enforced in `FirestoreContentRepository.update` (Task 4) and editor save path (Task 9) ✓
- Validation (empty fields, past expiration) → Task 9 ✓

**Type consistency:**
- `Announcement` fields used identically across all tasks: `id`, `title`, `description`, `type`, `expirationDate`, `createdAt`.
- `AnnouncementType` enum values: `Health`, `Alert`, `Tip`, `Offer` — referenced in editor (Task 9), `typeStyle` (Task 6), Firestore mapping (Task 4).
- `ContentRepository` method signatures match across Local impl (Task 3), Firestore impl (Task 4), editor call site (Task 9), and list call site (Task 10).
- Topic name `"announcements"` matches between `FcmTopics.ANNOUNCEMENTS` (Task 8) and `_init.mjs`'s `TOPIC_ANNOUNCEMENTS` (Task 13).

**No placeholders — confirmed.** Every code step contains the complete source; no "TBD" / "similar to" / "add appropriate error handling".
