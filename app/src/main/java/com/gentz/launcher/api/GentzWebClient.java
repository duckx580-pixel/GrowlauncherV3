package com.gentz.launcher.api;

import android.app.Activity;
import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;

/**
 * GentaHax v5.53 shouldOverrideUrlLoading.
 * libgrowtopia.so is NOT in the Genta GitHub dump.
 * Genta loads libgentahax.so for cheats; Chrome login is this Java host check.
 */
public final class GentzWebClient {
    private GentzWebClient() {}

    public static boolean override(Activity act, boolean allowExternal,
                                   String originalUrl, WebView view, String url) {
        try {
            Uri orig = Uri.parse(originalUrl != null ? originalUrl : "");
            Uri next = Uri.parse(url);
            String oh = orig.getHost();
            String nh = next.getHost();
            if (allowExternal && oh != null && nh != null && !oh.equalsIgnoreCase(nh)) {
                GentzGoogle.openAsResult(act, url);
                return true;
            }
        } catch (Exception ignored) {}
        if (view != null && url != null) view.loadUrl(url);
        return true;
    }

    public static boolean override(Activity act, boolean allowExternal,
                                   String originalUrl, WebView view, WebResourceRequest req) {
        if (req == null || req.getUrl() == null) return false;
        return override(act, allowExternal, originalUrl, view, req.getUrl().toString());
    }
}
