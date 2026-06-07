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
