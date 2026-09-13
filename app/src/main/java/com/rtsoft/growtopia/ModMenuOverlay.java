package com.rtsoft.growtopia;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Mod menu rendered as plain Android Views layered on top of the game's
 * GLSurfaceView, the same way {@link WebViewManager} layers the login WebView
 * over the game via {@code SharedActivity.mViewGroup}.
 *
 * <p>This replaces an earlier attempt that rendered the menu with Dear ImGui
 * inside a hooked {@code eglSwapBuffers}: libgrowtopia.so resolves EGL calls
 * through {@code eglGetProcAddress} function pointers rather than importing
 * them via the PLT, so the GOT hook never fired and the overlay never drew.
 * A View overlay needs no hook into the game's render loop at all.
 */
public final class ModMenuOverlay {

    private ModMenuOverlay() {}

    public static void attach(Activity activity, RelativeLayout root) {
        int toggleSize = dp(activity, 48);
        int margin = dp(activity, 12);

        Button toggle = new Button(activity);
        toggle.setId(View.generateViewId());
        toggle.setText("ZK");
        toggle.setTextColor(Color.WHITE);
        toggle.setAllCaps(false);
        toggle.setBackground(pill(0xCC2E7D32));
        toggle.setElevation(dp(activity, 4));

        RelativeLayout.LayoutParams toggleParams =
                new RelativeLayout.LayoutParams(toggleSize, toggleSize);
        toggleParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        toggleParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        toggleParams.setMargins(margin, margin, margin, margin);

        View panel = buildPanel(activity);
        panel.setId(View.generateViewId());
        panel.setVisibility(View.GONE);

        int screenW = activity.getResources().getDisplayMetrics().widthPixels;
        int screenH = activity.getResources().getDisplayMetrics().heightPixels;

        RelativeLayout.LayoutParams panelParams = new RelativeLayout.LayoutParams(
                (int) (screenW * 0.88f), (int) (screenH * 0.65f));
        panelParams.addRule(RelativeLayout.BELOW, toggle.getId());
        panelParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        panelParams.setMargins(margin, margin, margin, margin);

        toggle.setOnClickListener(v -> panel.setVisibility(
                panel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));

        root.addView(panel, panelParams);
        root.addView(toggle, toggleParams);
        panel.bringToFront();
        toggle.bringToFront();
    }

    private static View buildPanel(Activity activity) {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackground(rounded(0xE6161616));
        int pad = dp(activity, 16);
        layout.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(activity);
        title.setText("ZennKuy");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        layout.addView(title);

        addSectionLabel(activity, layout, "Google Login");
        TextView hint = new TextView(activity);
        hint.setText("Fix Google Sign-In Error 10 via ltoken flow.");
        hint.setTextColor(Color.LTGRAY);
        hint.setPadding(0, dp(activity, 4), 0, dp(activity, 8));
        layout.addView(hint);

        LoginSpoof spoof = new LoginSpoof(activity);

        addField(activity, layout, "MAC", spoof.getMac(), "02:00:00:00:00:00",
                spoof::setMac, () -> spoof.generateMac());
        addField(activity, layout, "RID", spoof.getRid(), "",
                spoof::setRid, () -> spoof.generateRid());
        addField(activity, layout, "WK", spoof.getWk(), "",
                spoof::setWk, () -> spoof.generateWk());

        Button startResolving = new Button(activity);
        startResolving.setText("Start Resolving");
        startResolving.setAllCaps(false);
        startResolving.setOnClickListener(v -> ZennKuyBridge.startResolving());
        LinearLayout.LayoutParams startParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        startParams.topMargin = dp(activity, 12);
        layout.addView(startResolving, startParams);

        TextView note = new TextView(activity);
        note.setText("Tap Start Resolving, sign in with Google, the game logs in automatically.");
        note.setTextColor(0xFF66DD66);
        note.setPadding(0, dp(activity, 8), 0, 0);
        layout.addView(note);

        addSectionLabel(activity, layout, "Info");
        TextView info = new TextView(activity);
        info.setText("ZennKuy - Growtopia Launcher Helper\n"
                + "Google login fix (Error 10)\n"
                + "View overlay (mViewGroup), no native hook required\n"
                + "Repo: duckx580-pixel/growlauncherv3");
        info.setTextColor(Color.LTGRAY);
        layout.addView(info);

        ScrollView scroll = new ScrollView(activity);
        scroll.addView(layout, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    private interface Setter { void set(String value); }
    private interface Generator { String generate(); }

    private static void addField(Activity activity, LinearLayout parent, String label,
                                  String initial, String placeholder,
                                  Setter setter, Generator generator) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = dp(activity, 6);
        parent.addView(row, rowParams);

        TextView labelView = new TextView(activity);
        labelView.setText(label + ":");
        labelView.setTextColor(Color.WHITE);
        labelView.setWidth(dp(activity, 48));
        row.addView(labelView);

        EditText field = new EditText(activity);
        field.setText(!initial.isEmpty() ? initial : placeholder);
        field.setTextColor(Color.WHITE);
        field.setSingleLine(true);
        field.setBackground(rounded(0xFF2A2A2A));
        field.setPadding(dp(activity, 8), dp(activity, 4), dp(activity, 8), dp(activity, 4));
        LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        fieldParams.setMargins(dp(activity, 4), 0, dp(activity, 4), 0);
        row.addView(field, fieldParams);
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { setter.set(s.toString()); }
        });

        Button rand = new Button(activity);
        rand.setText("Rand");
        rand.setAllCaps(false);
        rand.setOnClickListener(v -> {
            String value = generator.generate();
            if (value != null && !value.isEmpty()) field.setText(value);
        });
        row.addView(rand);
    }

    private static void addSectionLabel(Activity activity, LinearLayout parent, String text) {
        TextView label = new TextView(activity);
        label.setText(text);
        label.setTextColor(0xFFAAAAAA);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(activity, 14);
        parent.addView(label, params);
    }

    private static GradientDrawable rounded(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(16f);
        return d;
    }

    private static GradientDrawable pill(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setShape(GradientDrawable.OVAL);
        return d;
    }

    private static int dp(Activity activity, int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                activity.getResources().getDisplayMetrics());
    }
}
