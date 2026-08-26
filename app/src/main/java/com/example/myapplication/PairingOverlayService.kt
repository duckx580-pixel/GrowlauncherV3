package com.example.myapplication

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.flyfishxu.kadb.mdns.MdnsServiceType

class PairingOverlayService : Service() {
    private var discovery: SafeMdnsDiscovery? = null
    private var overlay: View? = null
    private var windowManager: WindowManager? = null
    private var found = false
    private var pairingHost: String? = null
    private var pairingPort: Int = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, notification("Searching for pairing service"))
        showOverlay()
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
                updateOverlay(found = true)
                updateNotification("Pairing service found")
                sendBroadcast(Intent(ACTION_ENDPOINT_FOUND).setPackage(packageName).apply {
                    putExtra(EXTRA_HOST, endpoint.host)
                    putExtra(EXTRA_PORT, endpoint.port)
                })
            }
        }
    }

    private fun showOverlay() {
        val manager = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager = manager
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 20, 28, 18)
            setBackgroundColor(Color.WHITE)
        }
        val title = TextView(this).apply {
            text = "Searching for pairing service"
            textSize = 16f
            setTextColor(Color.rgb(35, 35, 40))
        }
        val actionButton = Button(this).apply {
            text = "STOP SEARCHING"
            setOnClickListener { stopSelf() }
        }
        container.addView(title)
        container.addView(actionButton)
        overlay = container
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            y = 24
        }
        runCatching { manager.addView(container, params) }
            .onFailure { overlay = null }
        container.tag = actionButton
    }

    private fun updateOverlay(found: Boolean) {
        val container = overlay as? LinearLayout ?: return
        val title = container.getChildAt(0) as? TextView ?: return
        val action = container.getChildAt(1) as? Button ?: return
        title.text = if (found) "Pairing service found" else "Searching for pairing service"
        action.text = if (found) "ENTER PAIRING CODE" else "STOP SEARCHING"
        action.setOnClickListener {
            if (found) {
                startActivity(Intent(this, MainActivity::class.java).apply {
                    this.action = ACTION_REQUEST_CODE
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra(EXTRA_HOST, pairingHost)
                    putExtra(EXTRA_PORT, pairingPort)
                })
            } else {
                stopSelf()
            }
        }
    }

    private fun notification(text: String): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            10,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val actionIntent = if (found) {
            Intent(this, MainActivity::class.java).apply {
                action = ACTION_REQUEST_CODE
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
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
            .setContentIntent(openApp)
            .addAction(0, if (found) "ENTER PAIRING CODE" else "STOP SEARCHING", action)
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Wireless Debugging", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    override fun onDestroy() {
        discovery?.stop()
        discovery = null
        overlay?.let { view -> runCatching { windowManager?.removeView(view) } }
        overlay = null
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
