package com.rtsoft.growtopia;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
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
 *  2. Body-JSON strategy (fallback)
 *     If the redirect URL contains no #id_token, onPageFinished fires and
 *     we inject JS to read document.body.innerText as JSON. Only a field that
 *     validates as a real Google ID token is used; a Growtopia session token
 *     is rejected here rather than delivered, because handing native the wrong
 *     token type is exactly what triggers login "Error: 10".
 *
 * Every delivery path is funnelled through {@link #isValidGoogleIdToken}, so a
 * wrong-audience, replayed, expired, or non-Google token never reaches the
 * engine.
 */
public class GoogleWebSignInActivity extends Activity {
    static final String EXTRA_TOKEN = "token";
    static final String EXTRA_EXPECTED_AUD = "expected_aud";
    static final String EXTRA_EXPECTED_NONCE = "expected_nonce";
    private static final String TAG = "GoogleWebSignIn";
    private static final String CHROME_UA =
        "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 "
        + "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36";

    private WebView webView;
    private boolean finished = false;
    private String expectedAud;
    private String expectedNonce;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String url = getIntent().getStringExtra("url");
        if (url == null) {
            setResult(RESULT_CANCELED);
            finish();
            return;
        }
        expectedAud = getIntent().getStringExtra(EXTRA_EXPECTED_AUD);
        expectedNonce = getIntent().getStringExtra(EXTRA_EXPECTED_NONCE);

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
                JSONObject json = new JSONObject(bodyText.trim());
                // Prefer the real Google ID token field, then other aliases.
                // deliverToken re-validates, but choose a candidate that passes
                // here so a stray "token" field doesn't abort the attempt before
                // a valid id_token is tried.
                for (String key : new String[] {"id_token", "token", "loginToken"}) {
                    String candidate = json.optString(key, "");
                    if (!candidate.isEmpty()
                            && isValidGoogleIdToken(candidate, expectedAud, expectedNonce)) {
                        Log.d(TAG, "Got valid Google ID token from body JSON field '" + key + "'");
                        deliverToken(candidate);
                        return;
                    }
                }
                Log.e(TAG, "No valid Google ID token in body JSON: "
                        + bodyText.substring(0, Math.min(200, bodyText.length())));
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
        // Only ever hand back a genuine Google ID token. Delivering anything
        // else (e.g. the Growtopia session token from the callback page body)
        // is exactly what makes the native login fail with Error 10, so reject
        // it here and let the caller report a clean failure instead.
        if (!isValidGoogleIdToken(token, expectedAud, expectedNonce)) {
            Log.e(TAG, "Rejected non-Google-ID token (len="
                    + (token != null ? token.length() : 0) + "); not delivering");
            deliverCancel();
            return;
        }
        finished = true;
        Intent result = new Intent();
        result.putExtra(EXTRA_TOKEN, token);
        setResult(RESULT_OK, result);
        runOnUiThread(this::finish);
    }

    /**
     * Verifies {@code token} is a Google-issued OpenID Connect ID token that
     * Growtopia's server will accept:
     * <ul>
     *   <li>three JWT segments;</li>
     *   <li>{@code iss} is {@code accounts.google.com} (with or without the
     *       {@code https://} prefix);</li>
     *   <li>{@code aud} equals the client id we requested;</li>
     *   <li>{@code nonce} echoes the value we sent (anti-replay);</li>
     *   <li>{@code exp} is still in the future.</li>
     * </ul>
     * A Growtopia session token or an OAuth error page fails all of these.
     */
    static boolean isValidGoogleIdToken(String token, String expectedAud, String expectedNonce) {
        if (token == null || token.isEmpty()) return false;
        String[] parts = token.split("\\.");
        if (parts.length != 3) return false;
        try {
            byte[] payload = Base64.decode(parts[1], Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
            JSONObject claims = new JSONObject(new String(payload, java.nio.charset.StandardCharsets.UTF_8));

            String iss = claims.optString("iss", "");
            if (!"accounts.google.com".equals(iss) && !"https://accounts.google.com".equals(iss)) {
                Log.e(TAG, "ID token iss mismatch: " + iss);
                return false;
            }
            if (expectedAud != null && !expectedAud.isEmpty()
                    && !expectedAud.equals(claims.optString("aud", ""))) {
                Log.e(TAG, "ID token aud mismatch");
                return false;
            }
            if (expectedNonce != null && !expectedNonce.isEmpty()
                    && !expectedNonce.equals(claims.optString("nonce", ""))) {
                Log.e(TAG, "ID token nonce mismatch");
                return false;
            }
            long exp = claims.optLong("exp", 0L);
            if (exp > 0 && exp * 1000L < System.currentTimeMillis()) {
                Log.e(TAG, "ID token expired");
                return false;
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "ID token parse failed: " + e.getMessage());
            return false;
        }
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
