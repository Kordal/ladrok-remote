// SPDX-License-Identifier: Apache-2.0
package tv.ladrok.remote

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.IBinder
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps the Remote connected to the TV while it's minimized, with a notification to come back or to
 * Disconnect. It runs only while a TV is connected and stops by itself once the TV is gone.
 */
class RemoteService : Service() {
    private val scope = MainScope()
    private val app get() = application as RemoteApp

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) {
            app.remote.disconnect()
            finish()
            return START_NOT_STICKY
        }
        // Started with startForegroundService, so it must go foreground even when the TV is already gone.
        val tv = (app.remote.link.value as? Link.Connected)?.tv
        startForeground(NOTIFICATION, notification(tv), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        if (tv == null) finish()
        else if (!watching) {
            watching = true
            scope.launch {
                app.remote.link.collect { link ->
                    if (link is Link.Connected) getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification(link.tv))
                    else finish()
                }
            }
        }
        return START_NOT_STICKY
    }

    private var watching = false

    /** The TV is gone or let go of: off Bluetooth too, unless the Remote is on screen. */
    private fun finish() {
        if (!app.visible) app.remote.stop()
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(tv: Tv?): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) == null) manager.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW),
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, RemoteActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val disconnect = PendingIntent.getService(
            this, 1, Intent(this, RemoteService::class.java).setAction(ACTION_DISCONNECT), PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(tv?.let { getString(R.string.notification_connected, it.name) } ?: getString(R.string.app_name))
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_launcher_foreground), getString(R.string.disconnect), disconnect).build())
            .build()
    }

    companion object {
        private const val CHANNEL = "connected"
        private const val NOTIFICATION = 1
        private const val ACTION_DISCONNECT = "tv.ladrok.remote.DISCONNECT"

        /** Called while the Remote is on screen, so Android lets a foreground service start. */
        fun start(context: Context) {
            context.startForegroundService(Intent(context, RemoteService::class.java))
        }
    }
}
