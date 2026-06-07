package tools.mo3ta.bazeed.data.repo.firebase

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
