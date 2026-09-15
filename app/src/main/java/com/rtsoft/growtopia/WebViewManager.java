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
        android.webkit.CookieManager cookieManager = android.webkit.CookieManager.getInstance();
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
        // NOTE: ClearCookieWebData() is intentionally NOT called here.
        // Clearing cookies on hide wipes Google auth cookies before the next
        // auth attempt starts, causing "Continue with Google" to hang on retry.
        // Cookies are cleared at the top of LoadURLPost (no-spoof path) instead,
        // so they are only nuked when a brand-new auth session begins.
    }

    /**
     * Handles {@code grow://} deep-link URLs that arrive at the tail of the Google OAuth chain.
     *
     * <p>This method is called from the main WebView's {@link WebViewClientImpl} so that
     * the token redirect is caught if it lands inside the WebView.
     *
     * @return {@code true} if the URL was a {@code grow://} link and has been consumed;
     *         {@code false} for all other URLs.
     */
    boolean handleGrowUrl(String url) {
        if (url == null || !url.startsWith("grow://")) return false;

        AppLogger.log("WebView", "grow:// intercepted — url=" + url);
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
                    AppLogger.log("WebView", "grow:// token found in 'info' param (len=" + token.length() + ")");
                    Log.d("WebView", "grow:// — token was in 'info' param (len=" + token.length() + ")");
                }
            }
            if (token != null && !token.isEmpty()) {
                final String safeToken = token;
                // Mark delivered before posting to UI thread — any concurrent
                // startResolving() will see the flag even if it runs before
                // the UI-thread runnable below.
                ZennKuyBridge.sTokenDelivered = true;
                baseActivity.runOnUiThread(() -> {
                    AppLogger.log("WebView", "grow:// delivering token (len=" + safeToken.length() + ") — LOGIN SHOULD COMPLETE");
                    Log.d("WebView", "grow:// delivering token (len=" + safeToken.length() + ")");
                    android.widget.Toast.makeText(Main.mainApp,
                            "Logging in with google... wait a moment...",
                            android.widget.Toast.LENGTH_SHORT).show();
                    WebViewManager.this.HideWebView();
                    WebViewManager.this.nativeOnScriptCall("nativeSignIn", safeToken);
                });
            } else {
                AppLogger.warn("WebView", "grow:// had NO token or info param — login will fail! url=" + url);
                Log.w("WebView", "grow:// redirect had no token or info param — url=" + url);
            }
        } catch (Exception e) {
            Log.e("WebView", "grow:// intercept error: " + e);
        }
        return true; // Always consume grow:// — must not dispatch as Android Intent
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
            // Note: setSupportMultipleWindows and setJavaScriptCanOpenWindowsAutomatically
            // are intentionally NOT set. Real Growlauncher v5.57 has no WebChromeClient
            // and the login page never calls window.open() — it calls nativeSignIn("").

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

                // Check ltoken spoof first — if a stored credential is available,
                // skip the WebView entirely and inject it straight into the engine.
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
                                Log.w("WebViewManager", "refresh->ltoken failed: " + msg + " — falling back to WebView");
                                baseActivity.runOnUiThread(() -> showAndPostUrl(url, postData));
                            }
                        });
                        return;
                    }
                    // Spoof enabled but no tokens — fall through to WebView.
                    Log.w("WebViewManager", "ltoken spoof enabled but no tokens stored; showing WebView");
                    showAndPostUrl(url, postData);
                    return;
                }

                // No spoof — new auth session starting.
                // Clear cookies NOW (before showing WebView) so stale Growtopia
                // session data from a previous attempt cannot interfere with the
                // fresh OAuth flow.  Clearing here (not in HideWebView/DestroyWebView)
                // means Google auth cookies survive the hide→show cycle on retry,
                // so "Continue with Google" works on second and subsequent attempts.
                ZennKuyBridge.sTokenDelivered = false;
                ClearCookieWebData();
                AppLogger.log("WebViewManager", "LoadURLPost: showing WebView with OAuth URL");
                Log.d("WebViewManager", "LoadURLPost: showing WebView (v5.57 path)");
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

        @JavascriptInterface
        public void nativeSignIn(String token) {
            AppLogger.log("JSInterface", "nativeSignIn callback fired — token len=" + (token != null ? token.length() : "null"));
            Log.d("JSInterface", "nativeSignIn: token len=" + (token != null ? token.length() : 0));

            if (token == null || token.isEmpty()) {
                // Login page calls nativeSignIn("") to trigger Google sign-in.
                // Cannot use Android SDK (Error 10 on debug-signed APK).
                // Open Chrome with the dashboard URL — Chrome loads the page WITHOUT
                // the NativeApp JS interface, so the page uses its built-in browser
                // OAuth flow (window.open / redirect). After Google auth, Growtopia
                // server redirects to grow:// → Android → onNewIntent → handleIntent
                // → ZennKuyBridge.sTokenDelivered = true → token delivery.
                Log.d("JSInterface", "nativeSignIn: empty token — opening Chrome with dashboard URL");
                AppLogger.log("JSInterface", "nativeSignIn: empty token — launching Chrome for Google OAuth");
                WebViewManager.this.baseActivity.runOnUiThread(() -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW,
                            Uri.parse(ZennKuyBridge.DASHBOARD_URL));
                    WebViewManager.this.baseActivity.startActivityForResult(intent, 1);
                });
                return;
            }

            // Non-empty token: ltoken delivered by grow:// redirect or page's own OAuth flow.
            // Mark delivered BEFORE calling nativeOnScriptCall so any concurrent
            // SignIn() → startResolving() sees the flag and bails out.
            ZennKuyBridge.sTokenDelivered = true;
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

        /**
         * Called by Growtopia's login page JS: NativeApp.openAsResult(googleOAuthUrl)
         *
         * This is used when the login page detects the NativeApp JS interface and
         * calls openAsResult() directly (instead of window.open()). It launches
         * Chrome with the Google OAuth URL via startActivityForResult so Android can
         * route the grow:// redirect back to us through onNewIntent → handleIntent().
         */
        @JavascriptInterface
        public void openAsResult(final String url) {
            Log.d("JSInterface", "openAsResult: launching Chrome for Google OAuth — url=" + url);
            AppLogger.log("JSInterface", "openAsResult: starting Chrome with Google OAuth URL");
            WebViewManager.this.baseActivity.runOnUiThread(() ->
                WebViewManager.this.baseActivity.startActivityForResult(
                    new Intent(Intent.ACTION_VIEW, Uri.parse(url)), 1)
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
         * Delegates {@code grow://} URLs to {@link WebViewManager#handleGrowUrl(String)}.
         *
         * <p>All other URLs return {@code false} so the WebView follows the full redirect
         * chain natively, preserving OAuth cookies and session state.
         */
        private boolean interceptUrl(String url) {
            if (url == null) return false;
            return WebViewManager.this.handleGrowUrl(url);
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
