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
import android.widget.FrameLayout;

import java.io.File;
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

    // Google OAuth constants — same values the Growtopia dashboard uses.
    private static final String GOOGLE_CLIENT_ID =
        "389994132396-4s6ol46f60831v5e839e5llqvdv0g005.apps.googleusercontent.com";
    private static final String GOOGLE_REDIRECT_URI =
        "https://login.growtopiagame.com/google/callback";

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

    private byte[] applyDeviceSpoof(byte[] postData) {
        if (postData == null || postData.length == 0) return postData;
        try {
            DeviceSpoofer sp = new DeviceSpoofer(baseActivity);
            String body = new String(postData, StandardCharsets.ISO_8859_1);

            Map<String, String> params = new LinkedHashMap<>();
            for (String pair : body.split("&")) {
                int eq = pair.indexOf('=');
                if (eq < 0) { params.put(pair, ""); continue; }
                String key = URLDecoder.decode(pair.substring(0, eq), "UTF-8");
                String val = URLDecoder.decode(pair.substring(eq + 1),  "UTF-8");
                params.put(key, val);
            }

            boolean changed = false;
            if (params.containsKey("mac")) { params.put("mac", sp.getMac()); changed = true; }
            if (params.containsKey("rid")) { params.put("rid", sp.getRid()); changed = true; }
            if (params.containsKey("gid")) { params.put("gid", sp.getGid()); changed = true; }
            if (!changed) return postData;

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
    // Google OAuth handoff
    // -----------------------------------------------------------------------

    /**
     * Builds the Google OAuth authorization URL from a Ubisoft session state
     * token and launches it in Chrome.
     *
     * <p>The {@code state} parameter is the 576-char opaque string the
     * Growtopia dashboard delivers via {@code NativeApp.nativeSignIn(state)}.
     * Ubisoft's callback endpoint ({@code /google/callback}) validates this
     * state on return from Google, so it must be forwarded verbatim.
     */
    private void launchGoogleOAuth(String state) {
        if (sChromeLaunched) {
            Log.d("WebViewManager", "launchGoogleOAuth: Chrome already launched — skipping");
            return;
        }
        sChromeLaunched = true;

        Uri oauthUri = Uri.parse("https://accounts.google.com/o/oauth2/v2/auth")
            .buildUpon()
            .appendQueryParameter("client_id",     GOOGLE_CLIENT_ID)
            .appendQueryParameter("redirect_uri",  GOOGLE_REDIRECT_URI)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope",         "openid profile email")
            .appendQueryParameter("prompt",        "select_account")
            .appendQueryParameter("state",         state)
            .build();

        Log.d("WebViewManager", "launchGoogleOAuth: built URL — " + oauthUri);
        AppLogger.log("WebViewManager", "launchGoogleOAuth: launching Chrome for Google OAuth");

        // Prefer Chrome explicitly; fall back to whatever handles ACTION_VIEW.
        Intent intent = new Intent(Intent.ACTION_VIEW, oauthUri);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setPackage("com.android.chrome");
        try {
            baseActivity.startActivity(intent);
        } catch (android.content.ActivityNotFoundException ignored) {
            // Chrome not installed — try default browser.
            intent.setPackage(null);
            try {
                baseActivity.startActivity(intent);
            } catch (android.content.ActivityNotFoundException e2) {
                sChromeLaunched = false;
                Log.e("WebViewManager", "launchGoogleOAuth: no browser found: " + e2.getMessage());
                AppLogger.warn("WebViewManager", "launchGoogleOAuth: no browser available");
            }
        }
    }

    /**
     * Launches a fully-formed Google OAuth (or grow://) URL in Chrome.
     * Called from the popup WebViewClient and the shouldOverrideUrlLoading
     * safety net when the page navigates to accounts.google.com directly.
     */
    static void launchGoogleLoginUrl(Activity activity, String url) {
        if (sChromeLaunched) {
            Log.d("WebViewManager", "launchGoogleLoginUrl: Chrome already launched — skipping");
            return;
        }
        sChromeLaunched = true;
        Log.d("WebViewManager", "launchGoogleLoginUrl: sending URL to Chrome — " + url);
        AppLogger.log("WebViewManager", "launchGoogleLoginUrl: launching Chrome");
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setPackage("com.android.chrome");
        try {
            activity.startActivity(intent);
        } catch (android.content.ActivityNotFoundException ignored) {
            intent.setPackage(null);
            try {
                activity.startActivity(intent);
            } catch (android.content.ActivityNotFoundException e2) {
                sChromeLaunched = false;
                Log.e("WebViewManager", "launchGoogleLoginUrl: no browser found: " + e2.getMessage());
            }
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
            wv.addJavascriptInterface(new WebViewJavascriptInterface(this), "NativeApp");

            wv.setWebChromeClient(new WebChromeClient() {
                @Override
                public boolean onCreateWindow(WebView view, boolean isDialog,
                                              boolean isUserGesture,
                                              android.os.Message resultMsg) {
                    Log.d("WebViewManager", "onCreateWindow: popup requested");
                    final WebView popup = new WebView(baseActivity);
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
                    FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT);
                    baseActivity.addContentView(popup, pp);
                    WebView.WebViewTransport t = (WebView.WebViewTransport) resultMsg.obj;
                    t.setWebView(popup);
                    resultMsg.sendToTarget();
                    return true;
                }
            });

            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
            this.baseActivity.addContentView(wv, lp);
            Log.d("WebViewManager", "ShowWebView: WebView attached via addContentView");
        }

        if (this.webView.getParent() == null) {
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
            this.baseActivity.addContentView(this.webView, lp);
            Log.d("WebViewManager", "ShowWebView: WebView re-attached");
        }

        this.webView.setBackgroundColor(0);
        this.webView.setLayoutParams(new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT));
        this.webView.setVisibility(android.view.View.VISIBLE);
        Log.d("WebViewManager", "ShowWebView: visibility VISIBLE");
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
                        AppLogger.log("WebViewManager", "ltoken spoof active — injecting");
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
                                baseActivity.runOnUiThread(() -> showAndPostUrl(url, postData));
                            }
                        });
                        return;
                    }
                }

                ZennKuyBridge.sTokenDelivered = false;
                sChromeLaunched = false;
                ClearCookieWebData();
                AppLogger.log("WebViewManager", "LoadURLPost: showing login dialog in WebView");
                Log.d("WebViewManager", "LoadURLPost: showing WebView — url=" + url);

                byte[] spoofedData = (url != null && url.contains("growtopia"))
                    ? applyDeviceSpoof(postData) : postData;
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
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams((int) w, (int) h);
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

        /**
         * Called by the Growtopia dashboard page when the user taps a login
         * button. The {@code token} argument is Ubisoft's session {@code state}
         * parameter (~576 chars), NOT a Google OAuth URL and NOT the final
         * auth token.
         *
         * <p>The page never navigates the WebView after this call —
         * {@code shouldOverrideUrlLoading} will NOT fire. We build the Google
         * OAuth URL ourselves, injecting {@code token} as the {@code state}
         * parameter, hide the WebView, and launch Chrome immediately.
         */
        @JavascriptInterface
        public void nativeSignIn(String token) {
            ZennKuyBridge.sTokenDelivered = false;
            sChromeLaunched = false;

            Log.d("JSInterface", "nativeSignIn: state token len="
                + (token != null ? token.length() : "null")
                + " token=[" + token + "]");
            AppLogger.log("JSInterface",
                "nativeSignIn: Ubisoft state received (len="
                + (token != null ? token.length() : "null") + ") — building OAuth URL");

            if (token == null || token.isEmpty()) {
                Log.e("JSInterface", "nativeSignIn: empty state token — cannot build OAuth URL");
                AppLogger.warn("JSInterface", "nativeSignIn: empty token");
                return;
            }

            final String state = token;
            WebViewManager.this.baseActivity.runOnUiThread(() -> {
                // Hide the login dialog — Chrome takes over from here.
                WebViewManager.this.hideWebViewSync();
                // Build and launch the Google OAuth URL with Ubisoft's state.
                WebViewManager.this.launchGoogleOAuth(state);
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
            return interceptUrl(req.getUrl().toString());
        }

        @Override @SuppressWarnings("deprecation")
        public boolean shouldOverrideUrlLoading(WebView v, String url) {
            return interceptUrl(url);
        }

        /** Safety net — in case a page variant navigates the WebView directly. */
        private boolean interceptUrl(String url) {
            if (url == null) return false;
            if (url.startsWith("https://accounts.google.com/")) {
                Log.d("WebViewManager",
                    "shouldOverrideUrlLoading: Google OAuth URL → Chrome (safety net): " + url);
                baseActivity.runOnUiThread(() -> {
                    WebViewManager.this.hideWebViewSync();
                    launchGoogleLoginUrl(baseActivity, url);
                });
                return true;
            }
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
        public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) {
            super.onReceivedError(v, req, err);
            this.listener.OnError(err.getErrorCode());
        }

        @Override
        public void onReceivedSslError(WebView v, SslErrorHandler h, SslError err) {
            super.onReceivedSslError(v, h, err);
            this.listener.OnError(err.getPrimaryError());
        }

        @Override
        public void onReceivedHttpError(WebView v, WebResourceRequest req,
                                        WebResourceResponse resp) {
            super.onReceivedHttpError(v, req, resp);
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
