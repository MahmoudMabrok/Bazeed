package tools.mo3ta.bazeed.data.repo.firebase

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import tools.mo3ta.bazeed.data.repo.PharmacyStatusRepository
import tools.mo3ta.bazeed.util.Logger

/**
 * Firestore-backed flag at /config/pharmacy.openNow. Public read (rules), admin write.
 * Initial value is true so the pill never flashes "closed" on cold start before the
 * snapshot listener delivers.
 */
class FirestorePharmacyStatusRepository(
    firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : PharmacyStatusRepository {

    private val docRef = firestore.collection("config").document("pharmacy")

    private val _openNow = MutableStateFlow(true)
    override val openNow: StateFlow<Boolean> = _openNow.asStateFlow()

    init {
        docRef.addSnapshotListener { snap, err ->
            if (err != null) {
                Logger.w(TAG, "pharmacy status snapshot failed", err)
                return@addSnapshotListener
            }
            val value = snap?.getBoolean("openNow") ?: true
            _openNow.value = value
        }
    }

    override suspend fun setOpen(open: Boolean): Result<Unit> = runCatching {
        docRef.set(mapOf("openNow" to open)).await()
        Unit
    }

    private companion object {
        private const val TAG = "FirestorePharmacyStatusRepo"
    }
}
