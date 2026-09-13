package com.rtsoft.growtopia;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Floating "ZK" button overlay drawn over the game via mViewGroup.
 * Tap it to open the Google Login fix menu (same container as WebView).
 */
public class ZennKuyOverlay {

    private final Context ctx;
    private View floatBtn;

    public ZennKuyOverlay(Context context) {
        this.ctx = context;
    }

    public void attachTo(ViewGroup parent) {
        floatBtn = makeToggleButton();
        // mViewGroup is a RelativeLayout (see SharedActivity), so this needs
        // RelativeLayout.LayoutParams + addRule — FrameLayout.LayoutParams'
        // gravity field is silently dropped when handed to a RelativeLayout.
        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                dp(56), dp(56));
        lp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        lp.addRule(RelativeLayout.ALIGN_PARENT_END);
        lp.topMargin   = dp(8);
        lp.rightMargin = dp(8);
        parent.addView(floatBtn, lp);
    }

    private View makeToggleButton() {
        Button btn = new Button(ctx);
        btn.setText("ZK");
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        btn.setTypeface(null, Typeface.BOLD);
        btn.setTextColor(Color.WHITE);
        btn.setBackgroundColor(Color.argb(210, 30, 30, 30));
        btn.setPadding(0, 0, 0, 0);
        btn.setOnClickListener(v -> showMenu());
        btn.setOnTouchListener(new DragListener(btn));
        return btn;
    }

    private void showMenu() {
        LoginSpoof spoof = new LoginSpoof(ctx);

        AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
        builder.setTitle("ZennKuy — Google Login Fix");

        ScrollView scroll = new ScrollView(ctx);
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(8), dp(16), dp(8));

        root.addView(label("Fix Error 10 — sign in via Google WebView\nto obtain an ltoken for this device."));
        root.addView(spacer(8));

        root.addView(label("MAC address"));
        LinearLayout macRow = row();
        EditText macEdit = field(spoof.getMac());
        Button macRand = smallBtn("Random");
        macRand.setOnClickListener(v -> macEdit.setText(spoof.generateMac()));
        macRow.addView(macEdit, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        macRow.addView(macRand);
        root.addView(macRow);
        root.addView(spacer(6));

        root.addView(label("RID"));
        LinearLayout ridRow = row();
        EditText ridEdit = field(spoof.getRid());
        Button ridRand = smallBtn("Random");
        ridRand.setOnClickListener(v -> ridEdit.setText(spoof.generateRid()));
        ridRow.addView(ridEdit, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        ridRow.addView(ridRand);
        root.addView(ridRow);
        root.addView(spacer(6));

        root.addView(label("WK"));
        LinearLayout wkRow = row();
        EditText wkEdit = field(spoof.getWk());
        Button wkRand = smallBtn("Random");
        wkRand.setOnClickListener(v -> wkEdit.setText(spoof.generateWk()));
        wkRow.addView(wkEdit, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        wkRow.addView(wkRand);
        root.addView(wkRow);
        root.addView(spacer(12));

        TextView hint = label("Tap Start Resolving → sign in with Google\n→ the game will log in automatically.");
        hint.setTextColor(Color.parseColor("#66BB6A"));
        root.addView(hint);
        root.addView(spacer(10));

        Button resolveBtn = new Button(ctx);
        resolveBtn.setText("Start Resolving");
        resolveBtn.setTextColor(Color.WHITE);
        resolveBtn.setBackgroundColor(Color.parseColor("#1B5E20"));
        root.addView(resolveBtn);

        scroll.addView(root);
        builder.setView(scroll);
        builder.setNegativeButton("Close", null);

        AlertDialog dialog = builder.create();

        resolveBtn.setOnClickListener(v -> {
            String mac = macEdit.getText().toString().trim();
            String rid = ridEdit.getText().toString().trim();
            String wk  = wkEdit.getText().toString().trim();
            if (!mac.isEmpty()) spoof.setMac(mac);
            if (!rid.isEmpty()) spoof.setRid(rid);
            if (!wk.isEmpty())  spoof.setWk(wk);

            dialog.dismiss();
            startResolving();
        });

        dialog.show();
    }

    private void startResolving() {
        try {
            Main app = (Main) ctx;
            GoogleSignInHelper helper = app.googleSignInHelper;
            if (helper != null) {
                helper.SignIn();
            } else {
                Toast.makeText(ctx, "GoogleSignInHelper not ready", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(ctx, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private TextView label(String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        return tv;
    }

    private EditText field(String value) {
        EditText et = new EditText(ctx);
        et.setText(value);
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        et.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        et.setSingleLine(true);
        return et;
    }

    private Button smallBtn(String text) {
        Button b = new Button(ctx);
        b.setText(text);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(6);
        b.setLayoutParams(lp);
        return b;
    }

    private LinearLayout row() {
        LinearLayout ll = new LinearLayout(ctx);
        ll.setOrientation(LinearLayout.HORIZONTAL);
        ll.setGravity(Gravity.CENTER_VERTICAL);
        return ll;
    }

    private View spacer(int dpH) {
        View v = new View(ctx);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(dpH)));
        return v;
    }

    private int dp(int v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }

    private static class DragListener implements View.OnTouchListener {
        private float startX, startY, origX, origY;
        private boolean dragging;
        private final View view;

        DragListener(View v) { this.view = v; }

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startX = e.getRawX(); startY = e.getRawY();
                    origX  = v.getX();    origY  = v.getY();
                    dragging = false;
                    return false;
                case MotionEvent.ACTION_MOVE:
                    float dx = e.getRawX() - startX;
                    float dy = e.getRawY() - startY;
                    if (!dragging && Math.abs(dx) + Math.abs(dy) > 10) dragging = true;
                    if (dragging) {
                        v.setX(origX + dx);
                        v.setY(origY + dy);
                    }
                    return dragging;
                case MotionEvent.ACTION_UP:
                    return dragging;
            }
            return false;
        }
    }
}
