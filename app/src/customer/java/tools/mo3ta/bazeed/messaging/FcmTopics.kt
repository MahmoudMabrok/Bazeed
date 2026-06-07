package tools.mo3ta.bazeed.messaging

import com.google.firebase.messaging.FirebaseMessaging
import tools.mo3ta.bazeed.util.Logger

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
                if (task.isSuccessful) Logger.d(TAG, "subscribed to $ANNOUNCEMENTS")
                else Logger.w(TAG, "subscribe failed", task.exception)
            }
    }
}
