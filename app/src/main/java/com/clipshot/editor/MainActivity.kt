package com.clipshot.editor

import android.annotation.SuppressLint
import android.content.ContentValues
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private var chooser: ValueCallback<Array<Uri>>? = null
    private var tmp: File? = null
    private var out: FileOutputStream? = null

    private val picker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        chooser?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(r.resultCode, r.data))
        chooser = null
    }

    inner class Bridge {
        @JavascriptInterface fun begin() {
            out?.close()
            tmp = File(cacheDir, "export.bin")
            out = FileOutputStream(tmp)
        }
        @JavascriptInterface fun chunk(b64: String) { out?.write(Base64.decode(b64, Base64.DEFAULT)) }
        @JavascriptInterface fun end(name: String, mime: String): Boolean {
            return try {
                out?.close(); out = null
                val v = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, name)
                    put(MediaStore.Video.Media.MIME_TYPE, mime)
                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Clipshot")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, v)!!
                contentResolver.openOutputStream(uri)!!.use { o -> tmp!!.inputStream().use { it.copyTo(o) } }
                v.clear(); v.put(MediaStore.Video.Media.IS_PENDING, 0)
                contentResolver.update(uri, v, null, null)
                tmp?.delete()
                runOnUiThread { Toast.makeText(this@MainActivity, "Saved to Movies/Clipshot", Toast.LENGTH_LONG).show() }
                true
            } catch (e: Exception) { false }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        setContentView(web)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            mediaPlaybackRequiresUserGesture = false
        }
        web.addJavascriptInterface(Bridge(), "Android")
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(w: WebView, cb: ValueCallback<Array<Uri>>, p: FileChooserParams): Boolean {
                chooser?.onReceiveValue(null)
                chooser = cb
                return try { picker.launch(p.createIntent()); true } catch (e: Exception) { chooser = null; false }
            }
        }
        web.loadUrl("file:///android_asset/index.html")
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                web.evaluateJavascript("(window.__back&&window.__back())===true") { r -> if (r != "true") finish() }
            }
        })
    }
}
