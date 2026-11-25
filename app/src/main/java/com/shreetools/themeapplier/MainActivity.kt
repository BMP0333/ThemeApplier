package com.shreetools.themeapplier

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.InputStream

/**
 * Applies a .mtz theme on Xiaomi / Redmi / POCO (MIUI & HyperOS).
 * It copies the .mtz into the public Downloads folder (so the system Themes app can read it)
 * and then starts the Themes app's ApplyThemeForScreenshot activity with the file path.
 */
class MainActivity : Activity() {

    companion object {
        const val THEME_PKG = "com.android.thememanager"
        const val APPLY_ACTIVITY = "com.android.thememanager.ApplyThemeForScreenshot"
        const val BUNDLED = "iPhone18_Style_HyperOS.mtz"
        const val REQ_PICK = 101
    }

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val density = resources.displayMetrics.density
        val pad = (20 * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
        }
        addText(root, "Theme Applier", 28f, Color.WHITE, true)
        addText(root, "iPhone 18 Style for Xiaomi / Redmi / POCO", 15f, Color.parseColor("#9AA3C7"), false)
        status = addText(root, "", 14f, Color.parseColor("#C9D1F5"), false)
        (status.layoutParams as LinearLayout.LayoutParams).topMargin = pad

        addButton(root, "Apply iPhone 18 theme") { applyBundled() }
        addButton(root, "Choose another .mtz file") { pickMtz() }
        addButton(root, "Open Themes app") { openThemesApp() }
        addButton(root, "Theme resets after a minute? Get Zyper") { openZyper() }

        val tip = addText(
            root,
            "Tip: if nothing happens, open the Themes app once and accept its terms, then try again. " +
                "If HyperOS reverts the theme after 30-60 seconds, install Zyper.",
            12f, Color.parseColor("#7C86B0"), false
        )
        (tip.layoutParams as LinearLayout.LayoutParams).topMargin = pad

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(Color.parseColor("#0B0D1A"))
        scroll.isFillViewport = true
        scroll.addView(root)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        status.text = if (themeManagerInstalled()) {
            "Xiaomi Themes app found. Ready."
        } else {
            "Xiaomi Themes app (com.android.thememanager) not found. " +
                "This tool only works on Xiaomi, Redmi and POCO phones."
        }
    }

    // ------------------------------------------------------------ actions

    private fun applyBundled() {
        try {
            assets.open(BUNDLED).use { applySaved(saveToDownloads(BUNDLED, it)) }
        } catch (e: Exception) {
            say("Could not read bundled theme: ${e.message}")
        }
    }

    private fun pickMtz() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "*/*"
        startActivityForResult(i, REQ_PICK)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PICK && resultCode == RESULT_OK) {
            data?.data?.let { applyFromUri(it) }
        }
    }

    private fun applyFromUri(uri: Uri) {
        try {
            var name = "custom_theme.mtz"
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) name = it.getString(0) ?: name
            }
            if (!name.endsWith(".mtz", ignoreCase = true)) name += ".mtz"
            val input = contentResolver.openInputStream(uri)
            if (input == null) {
                say("Cannot open that file.")
                return
            }
            input.use { applySaved(saveToDownloads(name, it)) }
        } catch (e: Exception) {
            say("Failed: ${e.message}")
        }
    }

    private fun applySaved(path: String?) {
        if (path == null) {
            say("Could not save the theme into Downloads.")
            return
        }
        if (!themeManagerInstalled()) {
            say("Xiaomi Themes app not found on this phone.")
            return
        }
        val i = Intent(Intent.ACTION_MAIN)
        i.component = ComponentName(THEME_PKG, APPLY_ACTIVITY)
        i.putExtra("theme_file_path", path)
        i.putExtra("api_called_from", "ThemeApplier")
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(i)
            say("Sent to the Themes app:\n$path")
        } catch (e: ActivityNotFoundException) {
            say("This ROM does not expose the apply screen. The file is saved at:\n$path\nOpen it from the Themes app instead.")
        } catch (e: SecurityException) {
            say("The Themes app refused the request. The file is saved at:\n$path\nOpen it from the Themes app instead.")
        }
    }

    private fun openThemesApp() {
        val launch = packageManager.getLaunchIntentForPackage(THEME_PKG)
        if (launch != null) startActivity(launch) else say("Themes app not found.")
    }

    private fun openZyper() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.htetz.zyper")))
        } catch (e: ActivityNotFoundException) {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.htetz.zyper"))
            )
        }
    }

    // ------------------------------------------------------------ storage

    /** Writes into public Downloads via MediaStore (no storage permission needed) and returns the real path. */
    private fun saveToDownloads(name: String, input: InputStream): String? {
        val resolver = contentResolver
        try { // remove our own earlier copy so the file name stays the same
            resolver.delete(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
                arrayOf(name, Environment.DIRECTORY_DOWNLOADS + "%")
            )
        } catch (e: Exception) {
            // not ours / not deletable: a numbered copy will be created instead
        }

        val values = ContentValues()
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        values.put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        values.put(MediaStore.MediaColumns.IS_PENDING, 1)

        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        val out = resolver.openOutputStream(uri) ?: return null
        out.use { input.copyTo(it) }

        val done = ContentValues()
        done.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, done, null, null)

        resolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use {
            if (it.moveToFirst()) return it.getString(0)
        }
        return null
    }

    private fun themeManagerInstalled(): Boolean {
        return try {
            packageManager.getPackageInfo(THEME_PKG, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    // ------------------------------------------------------------ tiny UI helpers

    private fun say(msg: String) {
        status.text = msg
    }

    private fun addText(parent: LinearLayout, s: String, sp: Float, color: Int, bold: Boolean): TextView {
        val tv = TextView(this)
        tv.text = s
        tv.textSize = sp
        tv.setTextColor(color)
        if (bold) tv.setTypeface(tv.typeface, Typeface.BOLD)
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = (6 * resources.displayMetrics.density).toInt()
        tv.layoutParams = lp
        parent.addView(tv)
        return tv
    }

    private fun addButton(parent: LinearLayout, label: String, onClick: () -> Unit) {
        val b = Button(this)
        b.text = label
        b.isAllCaps = false
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = (12 * resources.displayMetrics.density).toInt()
        b.layoutParams = lp
        b.setOnClickListener { onClick() }
        parent.addView(b)
    }
}
