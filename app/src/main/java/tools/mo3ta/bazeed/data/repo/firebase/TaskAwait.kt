package tools.mo3ta.bazeed.data.repo.firebase

import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Awaits a Google Play Services [Task] from a coroutine without depending on
 * `kotlinx-coroutines-play-services` (keeps the dependency surface minimal and
 * avoids coroutine version skew with the Compose BoM).
 */
internal suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { result -> if (cont.isActive) cont.resume(result) }
    addOnFailureListener { error -> if (cont.isActive) cont.resumeWithException(error) }
    addOnCanceledListener { if (cont.isActive) cont.cancel() }
}
