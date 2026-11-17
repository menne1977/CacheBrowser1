package de.dtm.cachebrowser

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.os.Bundle
import android.text.format.Formatter
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.*
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.*

/**
 * Mini-Browser mit Cache-Ansicht (nur zum Testen).
 * - Live-Puffer: liest buffered/currentTime aller <audio>/<video>-Elemente der Seite aus.
 * - Anfragen-Log: Medien-/Range-Anfragen (nur URL, nichts wird gespeichert oder umgeleitet).
 * - Cache-Liste: liest die Schlüssel (URLs) der Chromium-HTTP-Cache-Dateien aus dem App-Cache.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private lateinit var urlBar: EditText
    private lateinit var panel: ScrollView
    private lateinit var panelText: TextView

    private var liveJson = "[]"
    private val requestLog = ArrayDeque<String>()
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.GERMANY)

    private val patchJs = """
    (function(){
      if (window.__dtm) return; window.__dtm = 1;
      window.__els = new Set();
      var orig = HTMLMediaElement.prototype.play;
      HTMLMediaElement.prototype.play = function(){ window.__els.add(this); return orig.apply(this, arguments); };
      setInterval(function(){
        var all = new Set(window.__els);
        document.querySelectorAll('audio,video').forEach(function(m){ all.add(m); });
        var out = [];
        all.forEach(function(m){
          var b = [];
          for (var i = 0; i < m.buffered.length; i++) b.push([m.buffered.start(i), m.buffered.end(i)]);
          out.push({src: m.currentSrc || m.src || '', t: m.currentTime, d: m.duration, paused: m.paused, buf: b, title: document.title});
        });
        DTM.media(JSON.stringify(out));
      }, 1000);
    })();
    """.trimIndent()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        urlBar = EditText(this).apply {
            hint = "URL oder Suchbegriff"
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_GO
            setText("https://bandcamp.com")
        }
        val go = Button(this).apply { text = "Go" }
        val cacheBtn = Button(this).apply { text = "Cache" }
        bar.addView(urlBar, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(go)
        bar.addView(cacheBtn)

        val frame = FrameLayout(this)
        web = WebView(this)
        panelText = TextView(this).apply {
            typeface = Typeface.MONOSPACE; textSize = 11f; setPadding(16, 16, 16, 16); setTextIsSelectable(true)
        }
        val clear = Button(this).apply { text = "Cache leeren" }
        val panelBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(clear)
            addView(panelText)
        }
        panel = ScrollView(this).apply {
            setBackgroundColor(0xFFFFFFFF.toInt()); visibility = View.GONE; addView(panelBox)
        }
        frame.addView(web, FrameLayout.LayoutParams(-1, -1))
        frame.addView(panel, FrameLayout.LayoutParams(-1, -1))

        root.addView(bar)
        root.addView(frame, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = true
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        web.addJavascriptInterface(Bridge(), "DTM")
        web.webChromeClient = WebChromeClient()
        web.webViewClient = object : WebViewClient() {
            override fun onPageStarted(v: WebView, url: String, f: android.graphics.Bitmap?) {
                urlBar.setText(url)
            }
            override fun onPageFinished(v: WebView, url: String) {
                v.evaluateJavascript(patchJs, null)
            }
            override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse? {
                val u = r.url.toString()
                val range = r.requestHeaders["Range"]
                if (range != null || u.contains(Regex("\\.(mp3|m4a|aac|ogg|opus|flac|wav|mp4|webm|m3u8)(\\?|$)"))) {
                    synchronized(requestLog) {
                        requestLog.addFirst("${timeFmt.format(Date())} ${range ?: "-"}  $u")
                        while (requestLog.size > 40) requestLog.removeLast()
                    }
                }
                return null // WebView lädt ganz normal, nichts wird umgeleitet
            }
        }

        fun load() {
            val t = urlBar.text.toString().trim()
            val target = when {
                t.startsWith("http://") || t.startsWith("https://") -> t
                t.contains(".") && !t.contains(" ") -> "https://$t"
                else -> "https://duckduckgo.com/?q=" + java.net.URLEncoder.encode(t, "UTF-8")
            }
            panel.visibility = View.GONE
            web.loadUrl(target)
        }
        go.setOnClickListener { load() }
        urlBar.setOnEditorActionListener { _, _, _ -> load(); true }
        cacheBtn.setOnClickListener {
            if (panel.visibility == View.VISIBLE) panel.visibility = View.GONE
            else { panel.visibility = View.VISIBLE; refreshPanel() }
        }
        clear.setOnClickListener { web.clearCache(true); refreshPanel() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    panel.visibility == View.VISIBLE -> panel.visibility = View.GONE
                    web.canGoBack() -> web.goBack()
                    else -> finish()
                }
            }
        })

        load()
    }

    inner class Bridge {
        @JavascriptInterface
        fun media(json: String) {
            liveJson = json
            runOnUiThread { if (panel.visibility == View.VISIBLE) refreshPanel() }
        }
    }

    private fun fmt(s: Double): String {
        if (s.isNaN() || s.isInfinite()) return "?"
        val m = (s / 60).toInt(); val sec = (s % 60).toInt()
        return "%d:%02d".format(m, sec)
    }

    private fun refreshPanel() {
        val sb = StringBuilder()
        sb.append("=== LIVE-PUFFER (Player auf der Seite) ===\n")
        val arr = try { JSONArray(liveJson) } catch (e: Exception) { JSONArray() }
        if (arr.length() == 0) sb.append("Kein Audio/Video aktiv. Erst auf Play drücken.\n")
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val d = o.optDouble("d", Double.NaN)
            sb.append("\nSeite: ").append(o.optString("title")).append('\n')
            sb.append("Quelle: ").append(o.optString("src")).append('\n')
            sb.append("Status: ").append(if (o.optBoolean("paused")) "pausiert" else "spielt")
                .append("  Position ").append(fmt(o.optDouble("t"))).append(" / ").append(fmt(d)).append('\n')
            val b = o.optJSONArray("buf")
            if (b != null) for (j in 0 until b.length()) {
                val r = b.getJSONArray(j)
                val end = r.getDouble(1)
                val pct = if (!d.isNaN() && d > 0) " (${(end / d * 100).toInt()}%)" else ""
                sb.append("Gepuffert: ").append(fmt(r.getDouble(0))).append(" – ").append(fmt(end)).append(pct).append('\n')
            }
        }

        sb.append("\n=== MEDIEN-ANFRAGEN (zuletzt) ===\n")
        synchronized(requestLog) { if (requestLog.isEmpty()) sb.append("-\n") else requestLog.forEach { sb.append(it).append('\n') } }

        sb.append("\n=== HTTP-CACHE (Dateien im App-Cache) ===\n")
        val entries = scanCache()
        if (entries.isEmpty()) sb.append("Keine lesbaren Einträge gefunden.\n")
        entries.take(60).forEach { (f, key) ->
            val audio = key.contains(Regex("\\.(mp3|m4a|aac|ogg|opus|flac|wav)|audio|stream", RegexOption.IGNORE_CASE))
            sb.append(if (audio) "♪ " else "  ")
                .append(Formatter.formatShortFileSize(this, f.length())).append("  ")
                .append(timeFmt.format(Date(f.lastModified()))).append("  ")
                .append(key.substringAfterLast(' ').take(160)).append('\n')
        }
        sb.append("\nInsgesamt ${entries.size} Einträge, ")
            .append(Formatter.formatShortFileSize(this, entries.sumOf { it.first.length() })).append('\n')
        panelText.text = sb.toString()
    }

    /** Chromium "Simple Cache": Dateien <hash>_0, Header 24 Byte, danach der Schlüssel (URL). */
    private fun scanCache(): List<Pair<File, String>> {
        val out = ArrayList<Pair<File, String>>()
        cacheDir.walkTopDown().filter { it.isFile && it.name.matches(Regex("[0-9a-f]{16}_0")) }.forEach { f ->
            readKey(f)?.let { out.add(f to it) }
        }
        return out.sortedByDescending { it.first.lastModified() }
    }

    private fun readKey(f: File): String? = try {
        f.inputStream().use { s ->
            val h = ByteArray(24)
            if (s.read(h) < 24) return null
            val bb = ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN)
            if (bb.long != 0xfcfb6d1ba7725c30UL.toLong()) return null
            bb.int
            val kl = bb.int
            if (kl <= 0 || kl > 4096) return null
            val kb = ByteArray(kl)
            if (s.read(kb) < kl) null else String(kb, Charsets.UTF_8)
        }
    } catch (e: Exception) { null }
}
