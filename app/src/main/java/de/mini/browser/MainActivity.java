package de.mini.browser;

import android.app.Activity;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.ProgressBar;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {

    // Startseite und Suchmaschine (hier aendern, falls gewuenscht)
    private static final String HOME = "https://www.google.com";
    private static final String SEARCH = "https://www.google.com/search?q=";

    // Bekannte Werbe-/Tracking-Domains (inkl. Subdomains)
    private static final Set<String> BLOCKED = new HashSet<>(Arrays.asList(
            "doubleclick.net", "googlesyndication.com", "googleadservices.com",
            "google-analytics.com", "googletagmanager.com", "googletagservices.com",
            "adservice.google.com", "app-measurement.com", "facebook.net",
            "connect.facebook.net", "criteo.com", "criteo.net", "taboola.com",
            "outbrain.com", "scorecardresearch.com", "quantserve.com", "hotjar.com",
            "adnxs.com", "amazon-adsystem.com", "adsrvr.org", "rubiconproject.com",
            "pubmatic.com", "openx.net", "moatads.com", "ads-twitter.com",
            "mixpanel.com", "segment.io", "segment.com", "clarity.ms", "bat.bing.com",
            "ads.linkedin.com", "snap.licdn.com", "analytics.tiktok.com",
            "ads.tiktok.com", "adform.net", "casalemedia.com", "smartadserver.com",
            "chartbeat.com", "newrelic.com", "nr-data.net", "demdex.net", "everesttech.net"
    ));

    private WebView web;
    private EditText urlBar;
    private ProgressBar bar;
    private final Map<String, String> headers = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        headers.put("DNT", "1");
        headers.put("Sec-GPC", "1");

        web = findViewById(R.id.web);
        urlBar = findViewById(R.id.url);
        bar = findViewById(R.id.progress);

        setupWebView();

        urlBar.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_SEARCH
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                go(urlBar.getText().toString());
                hideKeyboard();
                return true;
            }
            return false;
        });
        urlBar.setOnFocusChangeListener((v, focus) -> { if (focus) urlBar.selectAll(); });

        findViewById(R.id.back).setOnClickListener(v -> { if (web.canGoBack()) web.goBack(); });
        findViewById(R.id.forward).setOnClickListener(v -> { if (web.canGoForward()) web.goForward(); });
        findViewById(R.id.reload).setOnClickListener(v -> web.reload());
        findViewById(R.id.home).setOnClickListener(v -> go(HOME));
        findViewById(R.id.clear).setOnClickListener(v -> { wipe(); go(HOME); });

        go(HOME);
    }

    private void setupWebView() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);          // noetig fuer die meisten Seiten
        s.setDomStorageEnabled(true);
        s.setGeolocationEnabled(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setAllowFileAccessFromFileURLs(false);
        s.setAllowUniversalAccessFromFileURLs(false);
        s.setSaveFormData(false);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setSafeBrowsingEnabled(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, false); // keine Drittanbieter-Cookies

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int p) {
                bar.setProgress(p);
                bar.setVisibility(p >= 100 ? View.GONE : View.VISIBLE);
            }
        });

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                String scheme = u.getScheme() == null ? "" : u.getScheme();
                if (scheme.equals("http")) {            // HTTP -> HTTPS erzwingen
                    view.loadUrl(u.buildUpon().scheme("https").build().toString(), headers);
                    return true;
                }
                return !scheme.equals("https");         // andere Schemata (intent:, market: ...) blocken
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                if (isBlocked(req.getUrl().getHost())) {
                    return new WebResourceResponse("text/plain", "utf-8",
                            new ByteArrayInputStream(new byte[0]));
                }
                return null;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                if (!urlBar.hasFocus()) urlBar.setText(url);
            }
        });
    }

    private boolean isBlocked(String host) {
        if (host == null) return false;
        while (host.contains(".")) {
            if (BLOCKED.contains(host)) return true;
            host = host.substring(host.indexOf('.') + 1);
        }
        return false;
    }

    private void go(String input) {
        input = input.trim();
        if (input.isEmpty()) return;
        String target;
        if (input.startsWith("https://")) {
            target = input;
        } else if (input.startsWith("http://")) {
            target = "https://" + input.substring(7);
        } else if (!input.contains(" ") && input.contains(".")) {
            target = "https://" + input;
        } else {
            target = SEARCH + Uri.encode(input);
        }
        web.loadUrl(target, headers);
    }

    private void hideKeyboard() {
        urlBar.clearFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(urlBar.getWindowToken(), 0);
    }

    // Alles loeschen: Cache, Verlauf, Cookies, Webspeicher
    private void wipe() {
        web.clearCache(true);
        web.clearHistory();
        web.clearFormData();
        WebStorage.getInstance().deleteAllData();
        CookieManager.getInstance().removeAllCookies(null);
        CookieManager.getInstance().flush();
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack(); else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        wipe();                 // beim Schliessen bleibt nichts zurueck
        web.destroy();
        super.onDestroy();
    }
}
