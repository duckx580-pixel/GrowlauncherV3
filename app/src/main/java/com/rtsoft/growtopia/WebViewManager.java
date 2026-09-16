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

    /**
     * Google OAuth URL — the Growtopia Google login endpoint.
     *
     * <p>When opened in Chrome, the server responds with HTTP 302 to:
     * {@code https://accounts.google.com/v3/signin/accountchooser
     *   ?client_id=389994132396-4s6ol46f60831v5blfpci7lnmsdnh8br.apps.googleusercontent.com
     *   &redirect_uri=https://login.growtopiagame.com/google/callback
     *   &response_type=code&scope=openid+profile+email&state=<server-nonce>
     *   &prompt=select_account}
     *
     * <p>Chrome follows this redirect chain natively. After the user picks an
     * account Google returns to the Growtopia callback, the server exchanges the
     * code for a session token and issues HTTP 302 to
     * {@code grow://login?token=SESSION_TOKEN}. Android routes that URI back to
     * the app via the {@code <intent-filter>} in AndroidManifest.xml.
     */
    private static final String GOOGLE_LOGIN_URL =
        "https://login.growtopiagame.com/player/login/google?valKey=40db4045f2d8c572efe8c4a060605726";

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
     * Kept for any remaining WebView usage (e.g. non-login WebView popups).
     * Not used in the Chrome-external Google login flow.
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
     * Opens the Growtopia Google login endpoint in the system browser (Chrome).
     *
     * <p>Chrome follows the server's HTTP 302 redirect chain automatically:
     * <ol>
     *   <li>{@code login.growtopiagame.com/player/login/google?valKey=...}</li>
     *   <li>{@code accounts.google.com/v3/signin/accountchooser?client_id=389994132396-...}</li>
     *   <li>User picks Google account</li>
     *   <li>{@code login.growtopiagame.com/google/callback?code=...&state=...}</li>
     *   <li>{@code grow://login?token=SESSION_TOKEN} (Android intent-filter catches this)</li>
     * </ol>
     *
     * <p>Android delivers the {@code grow://} URI to {@link Main#onNewIntent}, which
     * calls {@code handleIntent} → {@link Main#HandleDeeplink} →
     * {@code NativeAppInterface.OnDeepLinkProcess(schemeSpecificPart)}.
     *
     * <p>No in-app WebView is created. The game's "Getting server address…" overlay
     * never appears because the game UI remains in its pre-login state throughout.
     */
    static void launchGoogleLogin(Activity activity) {
        Uri loginUri = Uri.parse(GOOGLE_LOGIN_URL);
        Intent intent = new Intent(Intent.ACTION_VIEW, loginUri);
        // FLAG_ACTIVITY_NEW_TASK: Chrome opens in its own task.
        // The grow:// callback is routed back to the launcher's main task via
        // onNewIntent because the grow:// intent-filter targets the Main activity.
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Log.d("WebViewManager", "launchGoogleLogin: opening system browser for Google OAuth");
        AppLogger.log("WebViewManager", "launchGoogleLogin: launching Chrome — " + GOOGLE_LOGIN_URL);
        try {
            activity.startActivity(intent);
        } catch (android.content.ActivityNotFoundException e) {
            // No browser installed — extremely rare on consumer Android.
            Log.e("WebViewManager", "launchGoogleLogin: no browser found: " + e.getMessage());
            AppLogger.warn("WebViewManager", "launchGoogleLogin: ActivityNotFoundException — no browser installed");
        }
    }

    /**
     * Handles {@code grow://} deep-link URLs that arrive inside an in-app WebView.
     * Used as a safety net if a WebView is ever shown and receives a grow:// redirect.
     * In the Chrome-external flow this is normally not reached.
     */
    boolean handleGrowUrl(String url) {
        if (url == null || !url.startsWith("grow://")) return false;

        AppLogger.log("WebView", "grow:// intercepted — url=" + url);
        Log.d("WebView", "grow:// intercepted — full url=" + url);
        try {
            Uri uri = Uri.parse(url);
            String token = uri.getQueryParameter("token");
            if (token == null || token.isEmpty()) {
                token = uri.getQueryParameter("info");
                if (token != null && !token.isEmpty()) {
                    AppLogger.log("WebView", "grow:// token found in 'info' param (len=" + token.length() + ")");
                }
            }
            if (token != null && !token.isEmpty()) {
                final String safeToken = token;
                ZennKuyBridge.sTokenDelivered = true;
                baseActivity.runOnUiThread(() -> {
                    AppLogger.log("WebView", "grow:// delivering token via nativeOnScriptCall (len=" + safeToken.length() + ")");
                    this.hideWebViewSync();
                    WebViewManager.this.nativeOnScriptCall("nativeSignIn", safeToken);
                    WebViewManager.this.HideWebView();
                });
            } else {
                AppLogger.warn("WebView", "grow:// had NO token — url=" + url);
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
                    Log.d("WebViewManager", "onCreateWindow: popup WebView requested");
                    final WebView popup = new WebView(baseActivity);
                    popup.setLayoutParams(new RelativeLayout.LayoutParams(-1, -1));
                    WebSettings popupSettings = popup.getSettings();
                    popupSettings.setJavaScriptEnabled(true);
                    popupSettings.setDomStorageEnabled(true);
                    popup.setWebViewClient(new WebViewClient() {
                        @Override
                        public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req) {
                            String url = req.getUrl().toString();
                            if (handleGrowUrl(url)) {
                                baseActivity.runOnUiThread(() -> closePopup(popup));
                                return true;
                            }
                            return false;
                        }
                        @Override
                        @SuppressWarnings("deprecation")
                        public boolean shouldOverrideUrlLoading(WebView v, String url) {
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

                // Fast path: stored ltoken — inject directly without any browser or WebView.
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
                                Log.w("WebViewManager", "refresh->ltoken failed: " + msg + " — falling back to Chrome");
                                baseActivity.runOnUiThread(() -> launchGoogleLogin(baseActivity));
                            }
                        });
                        return;
                    }
                    // Spoof enabled but no tokens — fall through to Chrome.
                    Log.w("WebViewManager", "ltoken spoof enabled but no tokens stored; launching Chrome");
                }

                // Chrome-external flow — open system browser for Google OAuth.
                // No in-app WebView is created; the game's "Getting server address…"
                // overlay never appears.
                // The grow:// redirect comes back via:
                //   onNewIntent → handleIntent → HandleDeeplink → OnDeepLinkProcess
                ZennKuyBridge.sTokenDelivered = false;
                AppLogger.log("WebViewManager", "LoadURLPost: Chrome external flow — bypassing in-app WebView");
                Log.d("WebViewManager", "LoadURLPost: launching Chrome for Google OAuth — url=" + url);
                launchGoogleLogin(baseActivity);
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
         * <p>In the Chrome-external flow this method is NOT expected to fire because
         * no in-app WebView is shown for the login page. It is kept as a safety net
         * in case a WebView is shown for another reason and the page calls this.
         *
         * <ul>
         *   <li><b>Empty token</b> — launch Chrome for Google OAuth (belt-and-suspenders).</li>
         *   <li><b>Non-empty token</b> — popup WebView flow fallback; sync-hide then deliver.</li>
         * </ul>
         */
        @JavascriptInterface
        public void nativeSignIn(String token) {
            AppLogger.log("JSInterface", "nativeSignIn callback fired — token len=" + (token != null ? token.length() : "null"));
            Log.d("JSInterface", "nativeSignIn: token len=" + (token != null ? token.length() : 0));

            if (token == null || token.isEmpty()) {
                AppLogger.log("JSInterface", "nativeSignIn(\"\") — launching Chrome for Google OAuth");
                WebViewManager.this.baseActivity.runOnUiThread(() -> {
                    WebViewManager.this.hideWebViewSync();
                    launchGoogleLogin(WebViewManager.this.baseActivity);
                    WebViewManager.this.HideWebView();
                });
                return;
            }

            // Non-empty token fallback: deliver via nativeOnScriptCall.
            final String safeToken = token;
            ZennKuyBridge.sTokenDelivered = true;
            WebViewManager.this.baseActivity.runOnUiThread(() -> {
                android.widget.Toast.makeText(
                        Main.mainApp, "Logging in with google... wait a moment...",
                        android.widget.Toast.LENGTH_SHORT).show();
                WebViewManager.this.hideWebViewSync();
                AppLogger.log("JSInterface", "nativeSignIn: calling nativeOnScriptCall — token len=" + safeToken.length());
                WebViewManager.this.nativeOnScriptCall("nativeSignIn", safeToken);
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

        @JavascriptInterface
        public void openAsResult(final String url) {
            Log.d("JSInterface", "openAsResult: launching Chrome — url=" + url);
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
