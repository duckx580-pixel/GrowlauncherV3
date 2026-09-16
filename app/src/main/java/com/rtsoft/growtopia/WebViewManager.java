package com.rtsoft.growtopia;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Looper;
import android.util.Log;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.RelativeLayout;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WebViewManager {
    private static String originalURL;
    private Activity baseActivity;
    private final ExecutorService webViewWorkExecutor;
    boolean allowExternalLinks = true;
    private WebView webView = null;

    static volatile boolean sChromeLaunched = false;

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
    }

    void hideWebViewSync() {
        WebView wv = this.webView;
        if (wv != null) {
            wv.stopLoading();
            wv.loadUrl("about:blank");
            wv.setVisibility(android.view.View.GONE);
        }
    }

    // -----------------------------------------------------------------------
    // Device spoof injection
    // -----------------------------------------------------------------------

    /**
     * Parses a URL-encoded form body, replaces the known Growtopia device
     * identifier parameters (mac, rid, gid) with the spoofed values stored
     * by {@link DeviceSpoofer}, and returns the modified bytes.
     *
     * <p>Only called when the target URL is the Growtopia login dashboard.
     * If parsing fails for any reason the original bytes are returned
     * unchanged so the login still proceeds.
     */
    private byte[] applyDeviceSpoof(byte[] postData) {
        if (postData == null || postData.length == 0) return postData;
        try {
            DeviceSpoofer sp = new DeviceSpoofer(baseActivity);
            String body = new String(postData, StandardCharsets.ISO_8859_1);

            // Parse into an ordered map to preserve unknown parameters.
            Map<String, String> params = new LinkedHashMap<>();
            for (String pair : body.split("&")) {
                int eq = pair.indexOf('=');
                if (eq < 0) { params.put(pair, ""); continue; }
                String key = URLDecoder.decode(pair.substring(0, eq), "UTF-8");
                String val = URLDecoder.decode(pair.substring(eq + 1),  "UTF-8");
                params.put(key, val);
            }

            boolean changed = false;
            if (params.containsKey("mac")) {
                params.put("mac", sp.getMac());
                changed = true;
            }
            if (params.containsKey("rid")) {
                params.put("rid", sp.getRid());
                changed = true;
            }
            if (params.containsKey("gid")) {
                params.put("gid", sp.getGid());
                changed = true;
            }
            // Also handle alternate casing / abbreviations used by older GT builds
            if (params.containsKey("wk")) {
                // wk is typically a device token — leave untouched unless
                // the spoofer gains explicit support for it.
            }

            if (!changed) return postData; // nothing to replace

            // Re-encode
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> e : params.entrySet()) {
                if (sb.length() > 0) sb.append('&');
                sb.append(URLEncoder.encode(e.getKey(),   "UTF-8"));
                sb.append('=');
                sb.append(URLEncoder.encode(e.getValue(), "UTF-8"));
            }
            byte[] modified = sb.toString().getBytes(StandardCharsets.ISO_8859_1);
            Log.d("WebViewManager", "applyDeviceSpoof: injected mac/rid/gid into POST body");
            AppLogger.log("WebViewManager", "applyDeviceSpoof: device identifiers replaced");
            return modified;

        } catch (Exception e) {
            Log.e("WebViewManager",
                "applyDeviceSpoof: failed to parse POST body — using original: " + e.getMessage());
            return postData;
        }
    }

    // -----------------------------------------------------------------------
    // Google login launch
    // -----------------------------------------------------------------------

    /**
     * Launches Chrome with the raw dashboard URL (stored as {@link #last_url}
     * including the session {@code ?valKey=...}) so Chrome handles all
     * redirects and cookies natively.
     *
     * <p>No Java HTTP fetching is performed. Any Java GET of the valKey URL
     * consumes the token without the WebView session cookies, leaving Chrome
     * with a dead session. Chrome must be the first and only consumer.
     */
    void launchGoogleLogin(Activity activity) {
        Log.d("WebViewManager",
            "launchGoogleLogin: opening dashboard URL in Chrome — " + this.last_url);
        AppLogger.log("WebViewManager",
            "launchGoogleLogin: launching Chrome with raw dashboard URL (valKey)");
        launchGoogleLoginUrl(activity, this.last_url);
    }

    /**
     * Opens {@code url} in Chrome via {@link Intent#ACTION_VIEW}.
     * Guards against double-launch with {@link #sChromeLaunched}.
     */
    static void launchGoogleLoginUrl(Activity activity, String url) {
        if (sChromeLaunched) {
            Log.d("WebViewManager",
                "launchGoogleLoginUrl: Chrome already launched — skipping");
            return;
        }
        sChromeLaunched = true;
        Log.d("WebViewManager", "launchGoogleLoginUrl: opening Chrome — url=" + url);
        AppLogger.log("WebViewManager",
            "launchGoogleLoginUrl: launching Chrome for Google OAuth");
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            activity.startActivity(intent);
        } catch (android.content.ActivityNotFoundException e) {
            sChromeLaunched = false;
            Log.e("WebViewManager",
                "launchGoogleLoginUrl: no browser found: " + e.getMessage());
            AppLogger.warn("WebViewManager",
                "launchGoogleLoginUrl: ActivityNotFoundException");
        }
    }

    boolean handleGrowUrl(String url) {
        if (url == null || !url.startsWith("grow://")) return false;
        AppLogger.log("WebView", "grow:// intercepted — url=" + url);
        Log.d("WebView", "grow:// intercepted — url=" + url);
        try {
            Uri uri = Uri.parse(url);
            String token = uri.getQueryParameter("token");
            if (token == null || token.isEmpty()) token = uri.getQueryParameter("info");
            if (token != null && !token.isEmpty()) {
                final String safeToken = token;
                ZennKuyBridge.sTokenDelivered = true;
                baseActivity.runOnUiThread(() -> {
                    AppLogger.log("WebView",
                        "grow:// delivering token (len=" + safeToken.length() + ")");
                    this.hideWebViewSync();
                    WebViewManager.this.nativeOnScriptCall("nativeSignIn", safeToken);
                    WebViewManager.this.HideWebView();
                });
            } else {
                AppLogger.warn("WebView", "grow:// had no token — url=" + url);
            }
        } catch (Exception e) {
            Log.e("WebView", "grow:// intercept error: " + e);
        }
        return true;
    }

    // -----------------------------------------------------------------------
    // WebView lifecycle
    // -----------------------------------------------------------------------

    public synchronized void ShowWebView() {
        if (Looper.getMainLooper().getThread() != Thread.currentThread()) return;
        if (this.webView == null) {
            WebView wv = new WebView(this.baseActivity);
            this.webView = wv;
            wv.setWebViewClient(new WebViewClientImpl(this.baseActivity,
                new WebViewCallbackListener() {
                    @Override public void OnError(int e) {
                        WebViewManager.this.nativeOnErrorOccurred(e);
                    }
                    @Override public void OnPageLoaded(String url) {
                        WebViewManager.this.nativeOnPageLoaded(url);
                    }
                }));

            WebSettings s = wv.getSettings();
            s.setJavaScriptEnabled(true);
            s.setLoadsImagesAutomatically(true);
            s.setDomStorageEnabled(true);
            s.setSupportMultipleWindows(true);
            s.setJavaScriptCanOpenWindowsAutomatically(true);

            wv.setBackgroundColor(0);
            wv.setScrollBarStyle(android.view.View.SCROLLBARS_INSIDE_OVERLAY);
            wv.setLayoutParams(new RelativeLayout.LayoutParams(-1, -1));
            wv.addJavascriptInterface(new WebViewJavascriptInterface(this), "NativeApp");

            wv.setWebChromeClient(new WebChromeClient() {
                @Override
                public boolean onCreateWindow(WebView view, boolean isDialog,
                                              boolean isUserGesture,
                                              android.os.Message resultMsg) {
                    Log.d("WebViewManager", "onCreateWindow: popup requested");
                    AppLogger.log("WebViewManager", "onCreateWindow: creating popup WebView");

                    final WebView popup = new WebView(baseActivity);
                    popup.setLayoutParams(new RelativeLayout.LayoutParams(-1, -1));
                    WebSettings ps = popup.getSettings();
                    ps.setJavaScriptEnabled(true);
                    ps.setDomStorageEnabled(true);

                    popup.setWebViewClient(new WebViewClient() {
                        @Override
                        public boolean shouldOverrideUrlLoading(
                                WebView v, WebResourceRequest req) {
                            return interceptPopupUrl(req.getUrl().toString());
                        }
                        @Override @SuppressWarnings("deprecation")
                        public boolean shouldOverrideUrlLoading(WebView v, String url) {
                            return interceptPopupUrl(url);
                        }
                        private boolean interceptPopupUrl(String url) {
                            if (url == null) return false;
                            if (url.contains("accounts.google.com")) {
                                Log.d("WebViewManager",
                                    "popup: Google OAuth → Chrome: " + url);
                                baseActivity.runOnUiThread(() -> {
                                    closePopup(popup);
                                    launchGoogleLoginUrl(baseActivity, url);
                                });
                                return true;
                            }
                            if (handleGrowUrl(url)) {
                                baseActivity.runOnUiThread(() -> closePopup(popup));
                                return true;
                            }
                            return false;
                        }
                    });
                    popup.setWebChromeClient(new WebChromeClient() {
                        @Override public void onCloseWindow(WebView w) {
                            baseActivity.runOnUiThread(() -> closePopup(w));
                        }
                    });

                    ((SharedActivity) baseActivity).mViewGroup.addView(popup);
                    WebView.WebViewTransport t = (WebView.WebViewTransport) resultMsg.obj;
                    t.setWebView(popup);
                    resultMsg.sendToTarget();
                    return true;
                }
            });

            ((SharedActivity) this.baseActivity).mViewGroup.addView(wv);
        }
        this.webView.setBackgroundColor(0);
        this.webView.setLayoutParams(new RelativeLayout.LayoutParams(-1, -1));
        this.webView.setVisibility(android.view.View.VISIBLE);
    }

    private void closePopup(WebView popup) {
        if (popup == null) return;
        ViewGroup parent = (ViewGroup) popup.getParent();
        if (parent != null) parent.removeView(popup);
        popup.stopLoading();
        popup.destroy();
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

    public void LoadURLPost(final String url, final byte[] postData,
                            final boolean allowExternal) {
        this.webViewWorkExecutor.execute(() ->
            this.baseActivity.runOnUiThread(() -> {
                this.allowExternalLinks = allowExternal;
                this.last_url = url;
                if (postData != null) {
                    this.last_packet = new String(postData, StandardCharsets.ISO_8859_1);
                }

                LoginSpoof spoof = getActiveSpoof();
                if (spoof != null) {
                    String ltoken = spoof.getLtoken();
                    if (!ltoken.isEmpty()) {
                        AppLogger.log("WebViewManager",
                            "ltoken spoof active — injecting stored ltoken");
                        nativeOnScriptCall("nativeSignIn", ltoken);
                        return;
                    }
                    String rt = spoof.getRefreshToken();
                    if (!rt.isEmpty()) {
                        spoof.exchangeStoredRefreshToken(new LoginSpoof.ExchangeCallback() {
                            @Override public void onSuccess(String lt) {
                                nativeOnScriptCall("nativeSignIn", lt);
                            }
                            @Override public void onFailure(String msg, String raw) {
                                baseActivity.runOnUiThread(
                                    () -> showAndPostUrl(url, postData));
                            }
                        });
                        return;
                    }
                    Log.w("WebViewManager",
                        "ltoken spoof enabled but no tokens stored; showing WebView");
                }

                ZennKuyBridge.sTokenDelivered = false;
                sChromeLaunched = false;
                ClearCookieWebData();
                AppLogger.log("WebViewManager",
                    "LoadURLPost: showing login selection dialog in WebView");
                Log.d("WebViewManager", "LoadURLPost: showing WebView — url=" + url);

                // Inject spoofed device identifiers into the POST body before
                // handing it to the WebView. Only fires on the Growtopia login
                // dashboard; has no effect if the POST body lacks mac/rid/gid.
                byte[] spoofedData = (url != null && url.contains("growtopia"))
                    ? applyDeviceSpoof(postData)
                    : postData;

                showAndPostUrl(url, spoofedData);
            })
        );
    }

    private void showAndPostUrl(String url, byte[] postData) {
        ShowWebView();
        originalURL = url;
        this.webView.postUrl(url, postData);
    }

    private static LoginSpoof getActiveSpoof() {
        try {
            if (Main.mainApp == null) return null;
            LoginSpoof s = new LoginSpoof(Main.mainApp);
            return s.isEnabled() ? s : null;
        } catch (Exception e) { return null; }
    }

    public void SetFrame(final float x, final float y, final float w, final float h) {
        this.webViewWorkExecutor.execute(() ->
            this.baseActivity.runOnUiThread(() -> {
                WebView wv = this.webView;
                if (wv == null) return;
                RelativeLayout.LayoutParams lp =
                    new RelativeLayout.LayoutParams((int) w, (int) h);
                lp.setMargins((int) x, (int) y, 0, 0);
                wv.setLayoutParams(lp);
            })
        );
    }

    public void SetBgColor(final int r, final int g, final int b, final int a) {
        this.webViewWorkExecutor.execute(() ->
            this.baseActivity.runOnUiThread(() -> {
                WebView wv = this.webView;
                if (wv == null) return;
                wv.setBackgroundColor(Color.argb(r, g, b, a));
            })
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
            this.webView.loadUrl(
                "javascript:NativeApp.pageContent(document.body.innerText)");
        });
    }

    // -----------------------------------------------------------------------
    // JS interface
    // -----------------------------------------------------------------------

    public class WebViewJavascriptInterface {
        WebViewManager webviewManager;
        WebViewJavascriptInterface(WebViewManager wvm) { this.webviewManager = wvm; }

        @JavascriptInterface
        public void nativeSignIn(String token) {
            ZennKuyBridge.sTokenDelivered = false;
            sChromeLaunched = false;

            AppLogger.log("JSInterface",
                "nativeSignIn fired — token len="
                + (token != null ? token.length() : "null")
                + " — hiding WebView, launching Chrome with dashboard URL");
            Log.d("JSInterface",
                "nativeSignIn: cleared flags; token len="
                + (token != null ? token.length() : 0));

            WebViewManager.this.baseActivity.runOnUiThread(() -> {
                WebViewManager.this.hideWebViewSync();
                android.widget.Toast.makeText(
                    Main.mainApp,
                    "Opening Google sign-in...",
                    android.widget.Toast.LENGTH_SHORT).show();
                WebViewManager.this.launchGoogleLogin(
                    WebViewManager.this.baseActivity);
                WebViewManager.this.HideWebView();
            });
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
            this.webviewManager.nativeOnPageContent(content);
        }

        @JavascriptInterface
        public void openInBrowser(final String url) {
            Log.d("JSInterface", "openInBrowser: " + url);
            WebViewManager.this.baseActivity.runOnUiThread(() ->
                WebViewManager.this.baseActivity.startActivity(
                    new Intent(Intent.ACTION_VIEW, Uri.parse(url))));
        }

        @JavascriptInterface
        public void openAsResult(final String url) {
            Log.d("JSInterface", "openAsResult: url=" + url);
            AppLogger.log("JSInterface",
                "openAsResult: launching Chrome with server-provided URL");
            ZennKuyBridge.sTokenDelivered = false;
            sChromeLaunched = false;
            WebViewManager.this.baseActivity.runOnUiThread(() -> {
                WebViewManager.this.hideWebViewSync();
                launchGoogleLoginUrl(WebViewManager.this.baseActivity, url);
                WebViewManager.this.HideWebView();
            });
        }
    }

    // -----------------------------------------------------------------------
    // WebViewClient
    // -----------------------------------------------------------------------

    private class WebViewClientImpl extends WebViewClient {
        private final Activity baseActivity;
        private final WebViewCallbackListener listener;

        WebViewClientImpl(Activity a, WebViewCallbackListener l) {
            this.baseActivity = a;
            this.listener = l;
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
            return WebViewManager.this.handleGrowUrl(req.getUrl().toString());
        }
        @Override @SuppressWarnings("deprecation")
        public boolean shouldOverrideUrlLoading(WebView v, String url) {
            return WebViewManager.this.handleGrowUrl(url);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            view.loadUrl(
                "javascript:(function f(){"
                + "var a=document.getElementsByTagName('a');"
                + "for(var v of a){v.addEventListener('click',function(e){"
                + "if(e.currentTarget.target=='_blank'){"
                + "e.preventDefault();"
                + "NativeApp.openInBrowser(e.currentTarget.href);"
                + "return false;}})}})();");
            this.listener.OnPageLoaded(url);
        }

        @Override
        public void onReceivedError(WebView v, WebResourceRequest req,
                                    WebResourceError err) {
            super.onReceivedError(v, req, err);
            Log.e("WebView", "onReceivedError [" + err.getDescription()
                + "] : " + req.getUrl());
            this.listener.OnError(err.getErrorCode());
        }

        @Override
        public void onReceivedSslError(WebView v, SslErrorHandler h, SslError err) {
            super.onReceivedSslError(v, h, err);
            Log.e("WebView", "onReceivedSslError [" + err.getPrimaryError() + "]");
            this.listener.OnError(err.getPrimaryError());
        }

        @Override
        public void onReceivedHttpError(WebView v, WebResourceRequest req,
                                        WebResourceResponse resp) {
            super.onReceivedHttpError(v, req, resp);
            Log.e("WebView", "onReceivedHttpError [" + resp.getStatusCode()
                + "] : " + req.getUrl());
            this.listener.OnError(resp.getStatusCode());
        }
    }

    // -----------------------------------------------------------------------
    // Cleanup
    // -----------------------------------------------------------------------

    private void clearWebViewDirectories() {
        File dataDir = this.baseActivity.getDataDir();
        File cacheDir = this.baseActivity.getCacheDir();
        if (dataDir != null) {
            File[] files = dataDir.listFiles();
            if (files != null)
                for (File f : files)
                    if (isStaleWebViewDataDirectory(f.getName()))
                        deleteRecursively(f);
        }
        if (cacheDir != null) {
            File[] files = cacheDir.listFiles();
            if (files != null)
                for (File f : files)
                    if (isStaleWebViewCacheDirectory(f.getName()))
                        deleteRecursively(f);
        }
        safeDeleteDatabase("webview.db");
        safeDeleteDatabase("webviewCache.db");
    }

    private boolean isStaleWebViewDataDirectory(String n) {
        return n.startsWith("app_webview_") && n.matches(".*\\.\\d+$");
    }
    private boolean isStaleWebViewCacheDirectory(String n) {
        return n.startsWith("webview_") && n.matches(".*\\.\\d+$");
    }

    private void safeDeleteDatabase(String name) {
        try { this.baseActivity.deleteDatabase(name); }
        catch (Throwable t) {
            Log.e("WebViewManager", "Failed to delete database: " + name, t);
        }
    }

    private boolean deleteRecursively(File file) {
        if (file == null || !file.exists()) return true;
        boolean ok = true;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null)
                for (File child : children)
                    if (!deleteRecursively(child)) ok = false;
        }
        if (!file.delete()) {
            Log.w("WebViewManager", "Failed to delete: " + file.getAbsolutePath());
            return false;
        }
        return ok;
    }
}
