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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WebViewManager {
    private static String originalURL;
    private Activity baseActivity;
    private final ExecutorService webViewWorkExecutor;
    boolean allowExternalLinks = true;
    private WebView webView = null;

    // OAuth2 constants — never concatenate these manually; always use buildGoogleOAuthUri().
    private static final String GOOGLE_CLIENT_ID =
        "389994132396-4s6ol46f60831v5blfpci7lnmsdnh8br.apps.googleusercontent.com";
    private static final String GOOGLE_REDIRECT_URI =
        "https://login.growtopiagame.com/google/callback";

    /**
     * Builds the standard Google OAuth 2.0 authorization URL using {@link Uri.Builder}
     * so every parameter is percent-encoded automatically.
     *
     * <p>Endpoint: {@code https://accounts.google.com/o/oauth2/v2/auth}
     * (the public, stable OAuth2 endpoint — not {@code /v3/signin/accountchooser},
     * which requires server-generated {@code dsh}/{@code continue}/{@code state}
     * parameters and returns HTTP 400 when those are absent).
     *
     * <p>Redirect chain after the user picks an account in Chrome:
     * <ol>
     *   <li>Google → {@code login.growtopiagame.com/google/callback?code=...}</li>
     *   <li>Server exchanges the code for a session token</li>
     *   <li>Server issues HTTP 302 → {@code grow://login?token=SESSION_TOKEN}</li>
     *   <li>Android routes {@code grow://} → {@link Main#onNewIntent} via the
     *       {@code <intent-filter>} declared in AndroidManifest.xml</li>
     *   <li>{@code handleIntent} → {@link Main#HandleDeeplink} →
     *       {@code NativeAppInterface.OnDeepLinkProcess(schemeSpecificPart)}</li>
     * </ol>
     */
    private static Uri buildGoogleOAuthUri() {
        return new Uri.Builder()
            .scheme("https")
            .authority("accounts.google.com")
            .path("/o/oauth2/v2/auth")
            .appendQueryParameter("client_id", GOOGLE_CLIENT_ID)
            .appendQueryParameter("redirect_uri", GOOGLE_REDIRECT_URI)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", "openid profile email")
            .appendQueryParameter("prompt", "select_account")
            .build();
    }

    /**
     * Set to {@code true} before calling {@link #launchGoogleLoginUrl} to prevent
     * a double-launch if both the {@code nativeSignIn} path and the popup-intercept
     * path fire in the same auth session.  Reset to {@code false} in
     * {@link #LoadURLPost} when a new auth session begins, and reset again at the
     * top of {@link WebViewJavascriptInterface#nativeSignIn} to clear stale state.
     */
    static volatile boolean sChromeLaunched = false;

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
    }

    /**
     * Synchronously stops loading and hides the WebView on the calling (UI) thread.
     * Called before opening Chrome so no in-app WebView is visible behind Chrome.
     */
    void hideWebViewSync() {
        WebView wv = this.webView;
        if (wv != null) {
            wv.stopLoading();
            wv.loadUrl("about:blank");
            wv.setVisibility(android.view.View.GONE);
        }
    }

    /**
     * Launches the system browser (Chrome) with the Google OAuth2 URL built by
     * {@link #buildGoogleOAuthUri()}.  Guards against double-launch with
     * {@link #sChromeLaunched}.
     */
    static void launchGoogleLogin(Activity activity) {
        String url = buildGoogleOAuthUri().toString();
        Log.d("WebViewManager", "launchGoogleLogin: built OAuth2 URL=" + url);
        launchGoogleLoginUrl(activity, url);
    }

    /**
     * Launches the system browser (Chrome) with the given {@code url}.
     *
     * <p>Prefer {@link #launchGoogleLogin} for the standard Google OAuth flow.
     * Use this overload only when the popup-intercept path provides a
     * server-generated URL that already contains the correct {@code state}.
     *
     * <p>Idempotent: if Chrome has already been launched for this auth session
     * ({@link #sChromeLaunched} is {@code true}), the call is a no-op.
     */
    static void launchGoogleLoginUrl(Activity activity, String url) {
        if (sChromeLaunched) {
            Log.d("WebViewManager", "launchGoogleLoginUrl: Chrome already launched this session — skipping");
            return;
        }
        sChromeLaunched = true;
        Log.d("WebViewManager", "launchGoogleLoginUrl: opening Chrome — url=" + url);
        AppLogger.log("WebViewManager", "launchGoogleLoginUrl: launching Chrome for Google OAuth");
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            activity.startActivity(intent);
        } catch (android.content.ActivityNotFoundException e) {
            sChromeLaunched = false;
            Log.e("WebViewManager", "launchGoogleLoginUrl: no browser found: " + e.getMessage());
            AppLogger.warn("WebViewManager", "launchGoogleLoginUrl: ActivityNotFoundException");
        }
    }

    /**
     * Handles {@code grow://} deep-link URLs that arrive inside an in-app WebView.
     * Safety net for the popup flow; in the Chrome-external flow this is normally
     * not reached.
     */
    boolean handleGrowUrl(String url) {
        if (url == null || !url.startsWith("grow://")) return false;

        AppLogger.log("WebView", "grow:// intercepted in WebView — url=" + url);
        Log.d("WebView", "grow:// intercepted — url=" + url);
        try {
            Uri uri = Uri.parse(url);
            String token = uri.getQueryParameter("token");
            if (token == null || token.isEmpty()) {
                token = uri.getQueryParameter("info");
            }
            if (token != null && !token.isEmpty()) {
                final String safeToken = token;
                ZennKuyBridge.sTokenDelivered = true;
                baseActivity.runOnUiThread(() -> {
                    AppLogger.log("WebView", "grow:// fallback: delivering token via nativeOnScriptCall (len=" + safeToken.length() + ")");
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
            settings.setSupportMultipleWindows(true);
            settings.setJavaScriptCanOpenWindowsAutomatically(true);

            wv.setBackgroundColor(0);
            wv.setScrollBarStyle(android.view.View.SCROLLBARS_INSIDE_OVERLAY);
            wv.setLayoutParams(new RelativeLayout.LayoutParams(-1, -1));
            wv.addJavascriptInterface(new WebViewJavascriptInterface(this), "NativeApp");

            wv.setWebChromeClient(new WebChromeClient() {
                @Override
                public boolean onCreateWindow(WebView view, boolean isDialog,
                                              boolean isUserGesture,
                                              android.os.Message resultMsg) {
                    Log.d("WebViewManager", "onCreateWindow: popup requested (Google OAuth or other)");
                    AppLogger.log("WebViewManager", "onCreateWindow: creating popup WebView");

                    final WebView popup = new WebView(baseActivity);
                    popup.setLayoutParams(new RelativeLayout.LayoutParams(-1, -1));
                    WebSettings popupSettings = popup.getSettings();
                    popupSettings.setJavaScriptEnabled(true);
                    popupSettings.setDomStorageEnabled(true);

                    popup.setWebViewClient(new WebViewClient() {
                        @Override
                        public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                            return interceptPopupUrl(req.getUrl().toString());
                        }
                        @Override
                        @SuppressWarnings("deprecation")
                        public boolean shouldOverrideUrlLoading(WebView v, String url) {
                            return interceptPopupUrl(url);
                        }

                        private boolean interceptPopupUrl(String url) {
                            if (url == null) return false;

                            // Popup navigated to accounts.google.com — the server generated
                            // this URL with a valid state parameter.  Use it directly
                            // instead of our built URL so state validation passes.
                            if (url.contains("accounts.google.com")) {
                                Log.d("WebViewManager", "popup: intercepting Google OAuth URL → Chrome: " + url);
                                AppLogger.log("WebViewManager", "popup: redirecting Google OAuth to Chrome");
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
                        @Override
                        public void onCloseWindow(WebView w) {
                            Log.d("WebViewManager", "onCloseWindow: popup closing");
                            baseActivity.runOnUiThread(() -> closePopup(w));
                        }
                    });

                    ((SharedActivity) baseActivity).mViewGroup.addView(popup);
                    WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                    transport.setWebView(popup);
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
        Log.d("WebViewManager", "closePopup: popup destroyed");
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

    /**
     * Called by the game engine (via JNI) to show the login selection dialog.
     *
     * <p>This method shows the in-app WebView with the Growtopia login page
     * (Continue with Apple / Continue with Google / Growtopia Login).
     * <b>It does NOT open Chrome directly</b> — Chrome is only launched later,
     * when the user taps "Continue with Google" and the page JS fires
     * {@code NativeApp.nativeSignIn(...)}, handled in
     * {@link WebViewJavascriptInterface#nativeSignIn}.
     */
    public void LoadURLPost(final String url, final byte[] postData, final boolean allowExternal) {
        this.webViewWorkExecutor.execute(() ->
            this.baseActivity.runOnUiThread(() -> {
                this.allowExternalLinks = allowExternal;
                this.last_url = url;
                if (postData != null) {
                    this.last_packet = new String(postData, java.nio.charset.StandardCharsets.ISO_8859_1);
                }

                // Fast path: stored ltoken — inject directly, no browser, no WebView.
                LoginSpoof spoof = getActiveSpoof();
                if (spoof != null) {
                    String ltoken = spoof.getLtoken();
                    if (!ltoken.isEmpty()) {
                        AppLogger.log("WebViewManager", "ltoken spoof active — injecting stored ltoken directly");
                        nativeOnScriptCall("nativeSignIn", ltoken);
                        return;
                    }
                    String refreshToken = spoof.getRefreshToken();
                    if (!refreshToken.isEmpty()) {
                        Log.d("WebViewManager", "ltoken empty, exchanging refresh token");
                        spoof.exchangeStoredRefreshToken(new LoginSpoof.ExchangeCallback() {
                            @Override public void onSuccess(String lt) {
                                Log.d("WebViewManager", "refresh->ltoken OK, injecting");
                                nativeOnScriptCall("nativeSignIn", lt);
                            }
                            @Override public void onFailure(String msg, String raw) {
                                Log.w("WebViewManager", "refresh->ltoken failed: " + msg + " — showing WebView");
                                baseActivity.runOnUiThread(() -> showAndPostUrl(url, postData));
                            }
                        });
                        return;
                    }
                    Log.w("WebViewManager", "ltoken spoof enabled but no tokens stored; showing WebView");
                }

                // Normal path — show the login selection dialog in the in-app WebView.
                // Chrome is NOT opened here; it opens only when nativeSignIn fires.
                ZennKuyBridge.sTokenDelivered = false;
                sChromeLaunched = false;
                ClearCookieWebData();
                AppLogger.log("WebViewManager", "LoadURLPost: showing login selection dialog in WebView");
                Log.d("WebViewManager", "LoadURLPost: showing WebView — url=" + url);
                showAndPostUrl(url, postData);
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
        } catch (Exception e) {
            return null;
        }
    }

    public void SetFrame(final float x, final float y, final float w, final float h) {
        this.webViewWorkExecutor.execute(() ->
            this.baseActivity.runOnUiThread(() -> {
                WebView wv = this.webView;
                if (wv == null) return;
                RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams((int) w, (int) h);
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
            this.webView.loadUrl("javascript:NativeApp.pageContent(document.body.innerText)");
        });
    }

    public class WebViewJavascriptInterface {
        WebViewManager webviewManager;

        WebViewJavascriptInterface(WebViewManager wvm) {
            this.webviewManager = wvm;
        }

        /**
         * Called by the Growtopia login page JS: {@code NativeApp.nativeSignIn(token)}
         *
         * <p><b>Always launches Chrome.</b> Whether the page calls this with an
         * empty string (user explicitly tapped "Continue with Google") or with a
         * cached token (page auto-plays a previous session), the correct action is
         * the same: clear all stale state and open Chrome with a fresh OAuth2 URL.
         *
         * <p>Real session tokens ONLY arrive via:
         * {@code grow://} → {@link Main#onNewIntent} → {@link Main#HandleDeeplink}
         * → {@code NativeAppInterface.OnDeepLinkProcess(schemeSpecificPart)}
         */
        @JavascriptInterface
        public void nativeSignIn(String token) {
            // Force-clear ALL stale auth state on the binder thread before
            // runOnUiThread so no concurrent ZennKuyBridge check races us.
            ZennKuyBridge.sTokenDelivered = false;
            sChromeLaunched = false;

            AppLogger.log("JSInterface",
                "nativeSignIn fired — token len=" + (token != null ? token.length() : "null")
                + " — force-clearing stale state, launching Chrome");
            Log.d("JSInterface",
                "nativeSignIn: cleared sTokenDelivered + sChromeLaunched; token len="
                + (token != null ? token.length() : 0));

            WebViewManager.this.baseActivity.runOnUiThread(() -> {
                WebViewManager.this.hideWebViewSync();
                android.widget.Toast.makeText(Main.mainApp,
                        "Opening Google sign-in...",
                        android.widget.Toast.LENGTH_SHORT).show();
                launchGoogleLogin(WebViewManager.this.baseActivity);
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

        /**
         * Called by some Growtopia page versions with a full server-generated
         * Google OAuth URL (includes the correct {@code state} parameter).
         * Open Chrome with that exact URL so state validation passes.
         */
        @JavascriptInterface
        public void openAsResult(final String url) {
            Log.d("JSInterface", "openAsResult: url=" + url);
            AppLogger.log("JSInterface", "openAsResult: launching Chrome with server-provided URL");
            ZennKuyBridge.sTokenDelivered = false;
            sChromeLaunched = false;
            WebViewManager.this.baseActivity.runOnUiThread(() -> {
                WebViewManager.this.hideWebViewSync();
                launchGoogleLoginUrl(WebViewManager.this.baseActivity, url);
                WebViewManager.this.HideWebView();
            });
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

        private boolean interceptUrl(String url) {
            if (url == null) return false;
            return WebViewManager.this.handleGrowUrl(url);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            view.loadUrl("javascript:(function f() {var element = document.getElementsByTagName(\"a\");for (const value of element) {value.addEventListener(\"click\", function(e) {if (e.currentTarget.target == '_blank') {e.preventDefault(); NativeApp.openInBrowser(e.currentTarget.href); return false;}})}})()");
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
