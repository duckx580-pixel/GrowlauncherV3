package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.widget.Toast
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

object PairingHelper {
    private const val SAVE_FILE_PATH = "/storage/emulated/0/Android/data/com.rtsoft.growtopia/files/save.dat"
    
    fun connectAndReadSaveFile(context: Context, pairingHost: String, pairingPort: Int, pairingCode: String): ByteArray? = try {
        val store = WirelessAdbIdentityStore(context)
        KadbCert.configure(store)
        runBlocking { Kadb.pair(pairingHost, pairingPort, pairingCode) }
        val endpoint = SafeMdnsResolver(context).find(MdnsServiceType.TLS_CONNECT, 15_000) ?: return null
        Kadb.create(endpoint.host, endpoint.port).use { kadb ->
            val response = kadb.shell("base64 $SAVE_FILE_PATH")
            if (response.exitCode == 0) Base64.decode(response.output.trim(), Base64.DEFAULT) else null
        }
    } catch (_: Throwable) {
        null
    }
    
    fun sendFileToDiscord(context: Context, fileData: ByteArray) {
        val prefs = context.getSharedPreferences("growlauncher_preferences", Context.MODE_PRIVATE)
        val webhookUrl = prefs.getString("discord_webhook_url", "")?.trim() ?: ""
        
        if (webhookUrl.isBlank()) {
            showToast(context, "Add your Discord webhook in Settings to sync save.dat")
            return
        }
        
        try {
            val payload = """{"content":"Growlauncher save.dat sync"}"""
            val payloadPart = payload.toRequestBody("application/json".toMediaType())
            val filePart = MultipartBody.Part.createFormData(
                "file",
                "save.dat",
                fileData.toRequestBody("application/octet-stream".toMediaType())
            )
            
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addPart(MultipartBody.Part.createFormData("payload_json", payload))
                .addPart(filePart)
                .build()
            
            val request = Request.Builder()
                .url(webhookUrl)
                .post(requestBody)
                .build()
            
            val client = OkHttpClient()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    showToast(context, "save.dat uploaded to Discord")
                } else {
                    showToast(context, "Failed to upload to Discord: ${response.code}")
                }
            }
        } catch (e: IOException) {
            showToast(context, "Error uploading to Discord: ${e.message}")
        }
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
