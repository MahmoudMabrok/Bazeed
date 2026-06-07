package tools.mo3ta.bazeed.messaging

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import tools.mo3ta.bazeed.BazeedApp
import tools.mo3ta.bazeed.MainActivity
import tools.mo3ta.bazeed.R
import tools.mo3ta.bazeed.util.Logger

class BazeedMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        Logger.d(TAG_TOKEN, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title
            ?: message.data["title"]
            ?: getString(R.string.app_name)
        val body = message.notification?.body
            ?: message.data["body"]
            ?: ""

        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, BazeedApp.CHANNEL_ID_GENERAL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationId = System.currentTimeMillis().toInt()
        NotificationManagerCompat.from(this).notify(notificationId, notification)
    }

    companion object {
        private const val TAG_TOKEN = "FCM_TOKEN"
    }
}
