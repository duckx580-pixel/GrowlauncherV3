package com.rtsoft.growtopia;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import org.json.JSONObject;

/**
 * WebView-based Google sign-in used as a fallback when AccountManager cannot
 * deliver a token directly.
 *
 * Two token extraction strategies are attempted in order:
 *
 *  1. URL-fragment strategy (response_type=id_token)
 *     Google puts the id_token in the redirect URL fragment:
 *       https://login.growtopiagame.com/google/callback#id_token=eyJ…
 *     shouldOverrideUrlLoading intercepts this redirect before the page loads
 *     and extracts the JWT.  This is the preferred path because it yields a
 *     real Google ID token (what the native engine expects).
 *
 *  2. Body-JSON strategy (response_type=code, legacy)
 *     If the redirect URL contains no #id_token, onPageFinished fires and
 *     we inject JS to read document.body.innerText as JSON {"token":"..."}.
 *     This gets a Growtopia session token which may or may not work.
 */
public class GoogleWebSignInActivity extends Activity {
    static final String EXTRA_TOKEN = "token";
    private static final String TAG = "GoogleWebSignIn";
    private static final String CHROME_UA =
        "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 "
        + "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36";

    private WebView webView;
    private boolean finished = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String url = getIntent().getStringExtra("url");
        if (url == null) {
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        webView = new WebView(this);
        // Disable hardware acceleration to prevent Chrome GPU process SIGSEGV (null ptr in
        // Chrome_InProcGp) that occurs when the WebView renderer initialises its GPU context
        // while the game's OpenGL surface is already active on the same device.
        webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setUserAgentString(CHROME_UA);

        webView.addJavascriptInterface(new TokenBridge(), "TokenBridge");

        webView.setWebViewClient(new WebViewClient() {

            // ── Strategy 1: intercept the redirect before the page loads ──────
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String redirectUrl = request.getUrl().toString();
                if (redirectUrl.startsWith("https://login.growtopiagame.com")) {
                    // Check for id_token in the fragment (response_type=id_token)
                    String fragment = request.getUrl().getFragment();
                    if (fragment != null && fragment.contains("id_token=")) {
                        String idToken = extractParam(fragment, "id_token");
                        if (idToken != null && !idToken.isEmpty()) {
                            Log.d(TAG, "Extracted Google ID token from fragment, len=" + idToken.length());
                            deliverToken(idToken);
                            return true; // block the WebView from loading the page
                        }
                    }
                    // Check for error in fragment
                    if (fragment != null && fragment.contains("error=")) {
                        String error = extractParam(fragment, "error");
                        Log.e(TAG, "OAuth error in fragment: " + error);
                        // Fall through – let onPageFinished try the body strategy
                    }
                    // Let the WebView load the page so onPageFinished can try body strategy
                    return false;
                }
                return false;
            }

            // ── Strategy 2: read body JSON after the page loads ───────────────
            @Override
            public void onPageFinished(WebView view, String pageUrl) {
                if (finished) return;
                if (pageUrl == null) return;

                // Also try fragment extraction here in case shouldOverrideUrlLoading
                // was not called (e.g. server-side redirect)
                if (pageUrl.contains("login.growtopiagame.com")) {
                    try {
                        Uri uri = Uri.parse(pageUrl);
                        String fragment = uri.getFragment();
                        if (fragment != null && fragment.contains("id_token=")) {
                            String idToken = extractParam(fragment, "id_token");
                            if (idToken != null && !idToken.isEmpty()) {
                                Log.d(TAG, "Extracted ID token from pageFinished fragment");
                                deliverToken(idToken);
                                return;
                            }
                        }
                    } catch (Exception ignored) {}

                    // Body strategy – Growtopia server returns {"token":"..."}
                    view.loadUrl("javascript:TokenBridge.onBodyText(document.body.innerText)");
                }
            }
        });

        FrameLayout frame = new FrameLayout(this);
        frame.addView(webView);
        setContentView(frame);
        webView.loadUrl(url);
    }

    // ── JS bridge ─────────────────────────────────────────────────────────────

    private class TokenBridge {
        @JavascriptInterface
        public void onBodyText(String bodyText) {
            if (finished) return;
            try {
                // Try JSON body first ({"token":"..."})
                JSONObject json = new JSONObject(bodyText.trim());
                String token = json.optString("token", "");
                if (!token.isEmpty()) {
                    Log.d(TAG, "Got token from body JSON, length=" + token.length());
                    deliverToken(token);
                    return;
                }
                // Try id_token field in JSON
                String idToken = json.optString("id_token", "");
                if (!idToken.isEmpty()) {
                    Log.d(TAG, "Got id_token from body JSON, length=" + idToken.length());
                    deliverToken(idToken);
                    return;
                }
                Log.e(TAG, "No token field in JSON: " + bodyText.substring(0, Math.min(200, bodyText.length())));
                deliverCancel();
            } catch (Exception e) {
                Log.e(TAG, "Body parse error: " + e.getMessage());
                deliverCancel();
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void deliverToken(String token) {
        if (finished) return;
        finished = true;
        Intent result = new Intent();
        result.putExtra(EXTRA_TOKEN, token);
        setResult(RESULT_OK, result);
        runOnUiThread(this::finish);
    }

    void deliverCancel() {
        if (finished) return;
        finished = true;
        setResult(RESULT_CANCELED);
        runOnUiThread(this::finish);
    }

    /** Parse a single param from a query/fragment string like "key=val&key2=val2". */
    private static String extractParam(String params, String key) {
        for (String part : params.split("&")) {
            if (part.startsWith(key + "=")) {
                return Uri.decode(part.substring(key.length() + 1));
            }
        }
        return null;
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            deliverCancel();
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.setWebViewClient(null);
            webView.destroy();
            webView = null;
        }
        if (!finished) {
            finished = true;
            setResult(RESULT_CANCELED);
        }
        super.onDestroy();
    }
}
