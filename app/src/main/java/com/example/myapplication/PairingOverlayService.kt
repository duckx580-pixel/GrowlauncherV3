package com.example.myapplication

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.flyfishxu.kadb.mdns.MdnsServiceType

class PairingOverlayService : Service() {
    private var discovery: SafeMdnsDiscovery? = null
    private var found = false
    private var pairingHost: String? = null
    private var pairingPort: Int = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, notification("Searching for pairing service..."))
        startDiscovery()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) stopSelf()
        return START_NOT_STICKY
    }

    private fun startDiscovery() {
        discovery = SafeMdnsDiscovery(getSystemService(android.net.nsd.NsdManager::class.java)).also { resolver ->
            resolver.start(listOf(MdnsServiceType.TLS_PAIRING.dnsType, "_adb-pairing._tcp")) { endpoint ->
                if (found) return@start
                found = true
                pairingHost = endpoint.host
                pairingPort = endpoint.port
                updateNotification("Pairing service found")
                sendBroadcast(Intent(ACTION_ENDPOINT_FOUND).setPackage(packageName).apply {
                    putExtra(EXTRA_HOST, endpoint.host)
                    putExtra(EXTRA_PORT, endpoint.port)
                })
            }
        }
    }

    private fun notification(text: String): Notification {
        val actionIntent = if (found) {
            Intent(this, PairingCodeDialogActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                putExtra(EXTRA_HOST, pairingHost)
                putExtra(EXTRA_PORT, pairingPort)
            }
        } else {
            Intent(this, PairingOverlayService::class.java).setAction(ACTION_STOP)
        }
        
        val action = if (found) {
            PendingIntent.getActivity(
                this,
                11,
                actionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getService(
                this,
                11,
                actionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("Wireless Debugging")
            .setContentText(text)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .addAction(0, if (found) "ENTER PAIRING CODE" else "STOP SEARCHING", action)
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Wireless Debugging",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Wireless debugging pairing notifications"
                setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI, null)
                enableVibration(true)
                enableLights(true)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        discovery?.stop()
        discovery = null
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        super.onDestroy()
    }

    companion object {
        const val ACTION_ENDPOINT_FOUND = "com.example.myapplication.PAIRING_ENDPOINT_FOUND"
        const val ACTION_REQUEST_CODE = "com.example.myapplication.REQUEST_PAIRING_CODE"
        const val ACTION_STOP = "com.example.myapplication.STOP_PAIRING_SEARCH"
        const val EXTRA_HOST = "pairing_host"
        const val EXTRA_PORT = "pairing_port"
        private const val CHANNEL_ID = "wireless_debugging_pairing"
        private const val NOTIFICATION_ID = 701
    }
}
