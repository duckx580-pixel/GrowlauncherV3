package com.rtsoft.growtopia;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.RelativeLayout;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WebViewManager {
    private static String originalURL;
    private Activity baseActivity;
    private final ExecutorService webViewWorkExecutor;
    boolean allowExternalLinks = true;
    private WebView webView = null;

    // Fields used by the launcher's native message handler
    public boolean needed_to_render = false;
    public String to_render = "";
    public String last_packet = "";
    public String last_url = "";

    private interface WebViewCallbackListener {
        void OnError(int errorCode);
        void OnPageLoaded(String url);
    }

    native void nativeOnErrorOccurred(int i);
    native void nativeOnPageContent(String str);
    public native void nativeOnPageLoaded(String str);
    native void nativeOnScriptCall(String str, String str2);

    public WebViewManager(Activity activity) {
        this.baseActivity = null;
        this.webViewWorkExecutor = Executors.newSingleThreadExecutor();
        this.baseActivity = activity;
        this.webViewWorkExecutor.execute(() -> {
            try {
                clearWebViewDirectories();
            } catch (Exception e) {
                Log.e("WebView", "WebView cleanup failed", e);
            }
        });
    }

    public void destroy() {
        this.webViewWorkExecutor.shutdown();
    }

    public boolean IsVisible() {
        WebView wv = this.webView;
        return wv != null && wv.getVisibility() == android.view.View.VISIBLE;
    }

    private void ClearCookieWebData() {
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.removeAllCookies(null);
        cookieManager.flush();
        WebStorage.getInstance().deleteAllData();
    }

    private void DestroyWebView() {
        if (this.webView == null) return;
        Log.i(SharedActivity.PackageName, "Destroying WebView.");
        ViewGroup parent = (ViewGroup) this.webView.getParent();
        if (parent != null) parent.removeView(this.webView);
        this.webView.stopLoading();
        this.webView.loadUrl("about:blank");
        this.webView.clearHistory();
        this.webView.clearCache(true);
        this.webView.clearFormData();
        this.webView.removeJavascriptInterface("NativeApp");
        this.webView.destroy();
        this.webView = null;
        ClearCookieWebData();
    }

    public synchronized void ShowWebView() {
        if (Looper.getMainLooper().getThread() != Thread.currentThread()) return;
        if (this.webView == null) {
            WebView wv = new WebView(this.baseActivity);
            this.webView = wv;
            wv.setWebViewClient(new WebViewClientImpl(this.baseActivity, new WebViewCallbackListener() {
                @Override
                public void OnError(int errorCode) {
                    WebViewManager.this.nativeOnErrorOccurred(errorCode);
                }
                @Override
                public void OnPageLoaded(String url) {
                    WebViewManager.this.nativeOnPageLoaded(url);
                }
            }));
            WebSettings settings = wv.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setLoadsImagesAutomatically(true);
            settings.setDomStorageEnabled(true);
            wv.setBackgroundColor(0);
            wv.setScrollBarStyle(android.view.View.SCROLLBARS_INSIDE_OVERLAY);
            wv.setLayoutParams(new RelativeLayout.LayoutParams(-1, -1));
            wv.addJavascriptInterface(new WebViewJavascriptInterface(this), "NativeApp");
            ((SharedActivity) this.baseActivity).mViewGroup.addView(wv);
        }
        this.webView.setBackgroundColor(0);
        this.webView.setLayoutParams(new RelativeLayout.LayoutParams(-1, -1));
        this.webView.setVisibility(android.view.View.VISIBLE);
    }

    public void LoadURL(final String url, final boolean allowExternal) {
        this.webViewWorkExecutor.execute(() ->
            this.baseActivity.runOnUiThread(() -> {
                this.allowExternalLinks = allowExternal;
                ShowWebView();
                originalURL = url;
                this.webView.loadUrl(url);
            })
        );
    }

    public void LoadURLPost(final String url, final byte[] postData, final boolean allowExternal) {
        this.webViewWorkExecutor.execute(() ->
            this.baseActivity.runOnUiThread(() -> {
                this.allowExternalLinks = allowExternal;
                this.last_url = url;
                if (postData != null) {
                    this.last_packet = new String(postData, java.nio.charset.StandardCharsets.ISO_8859_1);
                }
                // When ltoken spoof is active we skip the WebView entirely and feed the
                // stored credential straight to the engine via nativeOnScriptCall.
                // libgrowtopia.so handles nativeOnScriptCall the same way regardless of
                // whether a WebView is alive — it just processes the ("nativeSignIn", ltoken)
                // pair and continues the connection sequence.
                LoginSpoof spoof = getActiveSpoof();
                if (spoof != null) {
                    String ltoken = spoof.getLtoken();
                    if (!ltoken.isEmpty()) {
                        Log.d("WebViewManager", "ltoken spoof active — injecting stored ltoken");
                        nativeOnScriptCall("nativeSignIn", ltoken);
                        return;
                    }
                    String refreshToken = spoof.getRefreshToken();
                    if (!refreshToken.isEmpty()) {
                        // No ltoken yet, but we have a refresh token — exchange it first.
                        Log.d("WebViewManager", "ltoken empty, exchanging refresh token");
                        spoof.exchangeStoredRefreshToken(new LoginSpoof.ExchangeCallback() {
                            @Override public void onSuccess(String lt) {
                                Log.d("WebViewManager", "refresh→ltoken OK, injecting");
                                nativeOnScriptCall("nativeSignIn", lt);
                            }
                            @Override public void onFailure(String msg, String raw) {
                                Log.w("WebViewManager", "refresh→ltoken failed: " + msg + " — falling back to WebView");
                                baseActivity.runOnUiThread(() -> showAndPostUrl(url, postData));
                            }
                        });
                        return;
                    }
                    // Spoof enabled but no tokens at all — fall through to normal WebView.
                    Log.w("WebViewManager", "ltoken spoof enabled but no tokens stored; showing WebView");
                    showAndPostUrl(url, postData);
                    return;
                }
                // No spoof active — do NOT show the WebView here.
                // libgrowtopia.so always calls LoadURLPost *before* SignIn().
                // SignIn() will delegate to ZennKuyBridge.startResolving() which loads
                // the Growtopia dashboard URL directly via LoadURL(). Showing the OAuth
                // URL here would race against that: if Google auto-redirects (cached session)
                // before the dashboard URL loads, interceptUrl fires with a grow:// URL whose
                // ltoken lives in the "info" param (not "token"), we miss it, and the engine
                // times out → "Please try login again."  Storing last_url/last_packet is enough
                // for ZennKuyBridge to have the fallback URL if needed.
                Log.d("WebViewManager", "LoadURLPost: no spoof active — storing URL, deferring WebView to SignIn()");
            })
        );
    }

    private void showAndPostUrl(String url, byte[] postData) {
        ShowWebView();
        originalURL = url;
        this.webView.postUrl(url, postData);
    }

    /**
     * Returns a {@link LoginSpoof} if the spoof feature is enabled, or {@code null} if it is
     * disabled. Swallows any exception so a broken prefs state never crashes the login flow.
     */
    private static LoginSpoof getActiveSpoof() {
        try {
            if (Main.mainApp == null) return null;
            LoginSpoof s = new LoginSpoof(Main.mainApp);
            return s.isEnabled() ? s : null;
        } catch (Exception e) {
            return null;
        }
    }

    public void SetFrame(final float x, final float y, final float w, final float h) {
        this.webViewWorkExecutor.execute(() ->
            this.baseActivity.runOnUiThread(() -> {
                RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams((int) w, (int) h);
                lp.setMargins((int) x, (int) y, 0, 0);
                this.webView.setLayoutParams(lp);
            })
        );
    }

    public void SetBgColor(final int r, final int g, final int b, final int a) {
        this.webViewWorkExecutor.execute(() ->
            this.baseActivity.runOnUiThread(() ->
                this.webView.setBackgroundColor(Color.argb(r, g, b, a))
            )
        );
    }

    public void MoveView(int height) {
        WebView wv = this.webView;
        if (wv == null) return;
        ObjectAnimator anim = ObjectAnimator.ofFloat(wv, "translationY", (-height) / 2.0f);
        anim.setDuration(200L);
        anim.start();
    }

    public void HideWebView() {
        this.webViewWorkExecutor.execute(() ->
            this.baseActivity.runOnUiThread(() -> {
                WebView wv = this.webView;
                if (wv == null) return;
                wv.stopLoading();
                wv.setVisibility(android.view.View.GONE);
                wv.loadUrl("about:blank");
                wv.clearHistory();
                DestroyWebView();
            })
        );
    }

    public void requestPageSource() {
        if (this.webView == null) return;
        this.baseActivity.runOnUiThread(() -> {
            if (this.needed_to_render) {
                nativeOnPageContent(this.to_render);
                this.needed_to_render = false;
                this.to_render = "";
                return;
            }
            this.webView.loadUrl("javascript:NativeApp.pageContent(document.body.innerText)");
        });
    }

    public class WebViewJavascriptInterface {
        WebViewManager webviewManager;

        WebViewJavascriptInterface(WebViewManager wvm) {
            this.webviewManager = wvm;
        }

        @JavascriptInterface
        public void nativeSignIn(String token) {
            Log.d("JSInterface", "nativeSignIn: " + token);
            // Match v5.57 behaviour: hide the WebView before feeding the token to the engine.
            // In real GrowLauncher the WebView just goes GONE here and libpowerkuy calls
            // nativeOnScriptCall from C++; we call it directly since we have no libpowerkuy.
            android.widget.Toast.makeText(
                    Main.mainApp, "Logging in with google... wait a moment...",
                    android.widget.Toast.LENGTH_SHORT).show();
            this.webviewManager.HideWebView();
            this.webviewManager.nativeOnScriptCall("nativeSignIn", token);
        }

        @JavascriptInterface
        public void onloginselection(String token) {
            Log.d("JSInterface", "onloginselection: " + token);
            this.webviewManager.nativeOnScriptCall("onloginselection", token);
        }

        @JavascriptInterface
        public void onnameselection(String token) {
            Log.d("JSInterface", "onnameselection: " + token);
            this.webviewManager.nativeOnScriptCall("onnameselection", token);
        }

        @JavascriptInterface
        public void pageContent(String content) {
            Log.d("JSInterface", "pageContent");
            this.webviewManager.nativeOnPageContent(content);
        }

        @JavascriptInterface
        public void openInBrowser(final String url) {
            Log.d("JSInterface", "openInBrowser: " + url);
            WebViewManager.this.baseActivity.runOnUiThread(() ->
                WebViewManager.this.baseActivity.startActivity(
                    new Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            );
        }
    }

    private class WebViewClientImpl extends WebViewClient {
        private Activity baseActivity;
        private WebViewCallbackListener listener;

        WebViewClientImpl(Activity activity, WebViewCallbackListener listener) {
            this.baseActivity = activity;
            this.listener = listener;
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return interceptUrl(request.getUrl().toString());
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return interceptUrl(url);
        }

        /**
         * Intercepts {@code grow://} redirects that arrive at the tail of the Google OAuth
         * chain — before they leave the WebView and become an Android Intent.
         *
         * <p><b>Why this matters:</b> Growtopia's server redirects the completed Google OAuth
         * callback to {@code grow://growtopia?info=...&token=...}. In the Chrome flow that
         * URL becomes an Android {@code ACTION_VIEW} Intent, which Android may route to the
         * <em>official</em> Growtopia app if it is installed — causing the "login loops back"
         * symptom. Inside the WebView we capture it here, deliver the token directly to the
         * game engine via {@code nativeOnScriptCall("nativeSignIn", token)}, and hide the
         * WebView — exactly matching v5.57 behaviour but without touching the Android Intent
         * system.
         *
         * <p>All other URLs ({@code https://accounts.google.com/...},
         * {@code https://login.growtopiagame.com/...}, etc.) return {@code false} so the
         * WebView follows the full redirect chain natively, preserving the OAuth cookies and
         * session state that break if you call {@code loadUrl()} on each hop.
         */
        private boolean interceptUrl(String url) {
            if (url == null) return false;

            if (url.startsWith("grow://")) {
                // grow:// is the Growtopia custom scheme — the game's own deep-link format.
                // Consume it here so it never becomes an Android Intent.
                Log.d("WebView", "grow:// intercepted — full url=" + url);
                try {
                    Uri uri = Uri.parse(url);
                    // Try "token" first (standard grow:// OAuth redirect).
                    // Fall back to "info" — the Growtopia dashboard page uses "info" instead
                    // of "token" as the ltoken parameter in its grow:// redirect.
                    String token = uri.getQueryParameter("token");
                    if (token == null || token.isEmpty()) {
                        token = uri.getQueryParameter("info");
                        if (token != null && !token.isEmpty()) {
                            Log.d("WebView", "grow:// — token was in 'info' param (len=" + token.length() + ")");
                        }
                    }
                    if (token != null && !token.isEmpty()) {
                        final String safeToken = token;
                        baseActivity.runOnUiThread(() -> {
                            Log.d("WebView", "grow:// delivering token to engine (len=" + safeToken.length() + ")");
                            android.widget.Toast.makeText(Main.mainApp,
                                    "Logging in with google... wait a moment...",
                                    android.widget.Toast.LENGTH_SHORT).show();
                            WebViewManager.this.HideWebView();
                            WebViewManager.this.nativeOnScriptCall("nativeSignIn", safeToken);
                        });
                    } else {
                        Log.w("WebView", "grow:// redirect had no token or info param — url=" + url);
                    }
                } catch (Exception e) {
                    Log.e("WebView", "grow:// intercept error: " + e);
                }
                return true; // Always consume grow:// — must not dispatch as Android Intent
            }

            // All other URLs: let the WebView follow redirects natively.
            // Calling view.loadUrl() on each hop breaks the Google OAuth chain because
            // every redirect carries session state in cookies and headers that loadUrl()
            // silently discards by starting a fresh GET.
            return false;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            view.loadUrl("javascript:(function f() {var element = document.getElementsByTagName(\"a\");for (const value of element) {value.addEventListener(\"click\", function(e) {if (e.currentTarget.target == '_blank') {e.preventDefault(); NativeApp.openInBrowser(e.currentTarget.href); return false;}})}})()" );
            this.listener.OnPageLoaded(url);
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            super.onReceivedError(view, request, error);
            Log.e("WebView", "onReceivedError [" + error.getDescription() + "] : " + request.getUrl());
            this.listener.OnError(error.getErrorCode());
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            super.onReceivedSslError(view, handler, error);
            Log.e("WebView", "onReceivedSslError [" + error.getPrimaryError() + "] : " + error);
            this.listener.OnError(error.getPrimaryError());
        }

        @Override
        public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
            super.onReceivedHttpError(view, request, response);
            Log.e("WebView", "onReceivedHttpError [" + response.getStatusCode() + "] : " + request.getUrl());
            this.listener.OnError(response.getStatusCode());
        }
    }

    private void clearWebViewDirectories() {
        File dataDir = this.baseActivity.getDataDir();
        File cacheDir = this.baseActivity.getCacheDir();
        if (dataDir != null) {
            File[] files = dataDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (isStaleWebViewDataDirectory(f.getName())) {
                        Log.d("WebViewManager", "Deleting stale WebView data dir: " + f.getAbsolutePath());
                        deleteRecursively(f);
                    }
                }
            }
        }
        if (cacheDir != null) {
            File[] files = cacheDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (isStaleWebViewCacheDirectory(f.getName())) {
                        Log.d("WebViewManager", "Deleting stale WebView cache dir: " + f.getAbsolutePath());
                        deleteRecursively(f);
                    }
                }
            }
        }
        safeDeleteDatabase("webview.db");
        safeDeleteDatabase("webviewCache.db");
    }

    private boolean isStaleWebViewDataDirectory(String name) {
        return name.startsWith("app_webview_") && name.matches(".*\\.\\d+$");
    }

    private boolean isStaleWebViewCacheDirectory(String name) {
        return name.startsWith("webview_") && name.matches(".*\\.\\d+$");
    }

    private void safeDeleteDatabase(String name) {
        try {
            Log.d("WebViewManager", "deleteDatabase(" + name + ") = " + this.baseActivity.deleteDatabase(name));
        } catch (Throwable t) {
            Log.e("WebViewManager", "Failed to delete database: " + name, t);
        }
    }

    private boolean deleteRecursively(File file) {
        if (file == null || !file.exists()) return true;
        boolean ok = true;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (!deleteRecursively(child)) ok = false;
                }
            }
        }
        if (!file.delete()) {
            Log.w("WebViewManager", "Failed to delete: " + file.getAbsolutePath());
            return false;
        }
        return ok;
    }
}
