package tools.mo3ta.bazeed

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationManagerCompat
import tools.mo3ta.bazeed.data.Repositories

class BazeedApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Repositories.init(this)
        createDefaultNotificationChannel()
    }

    private fun createDefaultNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID_GENERAL,
            getString(R.string.notification_channel_general_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = getString(R.string.notification_channel_general_description)
        }
        NotificationManagerCompat.from(this).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID_GENERAL = "general"
    }
}
