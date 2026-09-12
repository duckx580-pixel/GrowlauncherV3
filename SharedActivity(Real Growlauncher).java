package com.rtsoft.growtopia;

import aj.d;
import al.k;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Color;
import android.graphics.Rect;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.Vibrator;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.n;
import com.anzu.sdk.Anzu;
import com.tapjoy.TJActionRequest;
import com.tapjoy.TJPlacement;
import eh.m;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Locale;
import java.util.WeakHashMap;
import launcher.powerkuy.App;
import m0.g0;
import rc.b0;
import rc.q;
import rc.s;
import rc.u;
import rc.w;
import s6.a;
import s6.b;
import s6.c;
import sc.q0;
import t3.e2;
import t3.f2;
import t3.g2;
import t3.h2;
import t3.i1;
import t3.j1;
import t3.n0;
import t3.x;
import t3.y0;

/* JADX INFO: compiled from: r8-map-id-d54062505e0b897b6ce43519f4a85b629f053b64b0d6ed2b86131a7f1732bd67 */
/* JADX INFO: loaded from: classes.dex */
public class SharedActivity extends n implements SensorEventListener, s, w {
    public static boolean HookedEnabled = false;
    public static boolean IAPEnabled = false;
    static final int MESSAGE_OPEN_TEXTBOX_SECRET = 41;
    static final int MESSAGE_TYPE_ADD_COMPONENT = 18;
    static final int MESSAGE_TYPE_APP_VERSION = 47;
    static final int MESSAGE_TYPE_CALL_COMPONENT_FUNCTION_BY_NAME = 14;
    static final int MESSAGE_TYPE_CALL_ENTITY_FUNCTION = 13;
    static final int MESSAGE_TYPE_CALL_ENTITY_FUNCTION_RECURSIVELY = 40;
    static final int MESSAGE_TYPE_CALL_STATIC_FUNCTION = 46;
    static final int MESSAGE_TYPE_GUI_ACCELEROMETER = 4;
    static final int MESSAGE_TYPE_GUI_CHAR = 6;
    static final int MESSAGE_TYPE_GUI_CHAR_RAW = 23;
    static final int MESSAGE_TYPE_GUI_CLICK_END = 1;
    static final int MESSAGE_TYPE_GUI_CLICK_MOVE = 2;
    static final int MESSAGE_TYPE_GUI_CLICK_MOVE_RAW = 3;
    static final int MESSAGE_TYPE_GUI_CLICK_START = 0;
    static final int MESSAGE_TYPE_GUI_COPY = 9;
    static final int MESSAGE_TYPE_GUI_JOYPAD = 38;
    static final int MESSAGE_TYPE_GUI_JOYPAD_BUTTONS = 37;
    static final int MESSAGE_TYPE_GUI_JOYPAD_CONNECT = 39;
    static final int MESSAGE_TYPE_GUI_KEYBWD_CURSORPOS = 8;
    static final int MESSAGE_TYPE_GUI_KEYBWD_STRING = 7;
    static final int MESSAGE_TYPE_GUI_PASTE = 10;
    static final int MESSAGE_TYPE_GUI_TOGGLE_FULLSCREEN = 11;
    static final int MESSAGE_TYPE_GUI_TRACKBALL = 5;
    static final int MESSAGE_TYPE_HW_KEYBOARD_INPUT_ENDING = 43;
    static final int MESSAGE_TYPE_HW_KEYBOARD_INPUT_STARTING = 44;
    static final int MESSAGE_TYPE_HW_TOUCH_KEYBOARD_WILL_HIDE = 42;
    static final int MESSAGE_TYPE_HW_TOUCH_KEYBOARD_WILL_SHOW = 41;
    static final int MESSAGE_TYPE_IAP_ITEM_INFO_RESULT = 54;
    static final int MESSAGE_TYPE_IAP_ITEM_STATE = 29;
    static final int MESSAGE_TYPE_IAP_PURCHASED_LIST_STATE = 45;
    static final int MESSAGE_TYPE_IAP_RESULT = 28;
    static final int MESSAGE_TYPE_OS_CONNECTION_CHECKED = 19;
    static final int MESSAGE_TYPE_PLAY_MUSIC = 20;
    static final int MESSAGE_TYPE_PLAY_SOUND = 15;
    static final int MESSAGE_TYPE_PRELOAD_SOUND = 22;
    static final int MESSAGE_TYPE_REMOVE_COMPONENT = 17;
    static final int MESSAGE_TYPE_SET_ENTITY_VARIANT = 12;
    static final int MESSAGE_TYPE_SET_SOUND_ENABLED = 24;
    static final int MESSAGE_TYPE_TAPJOY_AD_READY = 25;
    static final int MESSAGE_TYPE_TAPJOY_AWARD_TAP_POINTS_RETURN = 34;
    static final int MESSAGE_TYPE_TAPJOY_AWARD_TAP_POINTS_RETURN_ERROR = 35;
    static final int MESSAGE_TYPE_TAPJOY_EARNED_TAP_POINTS = 36;
    static final int MESSAGE_TYPE_TAPJOY_FEATURED_APP_READY = 26;
    static final int MESSAGE_TYPE_TAPJOY_MOVIE_AD_READY = 27;
    static final int MESSAGE_TYPE_TAPJOY_SPEND_TAP_POINTS_RETURN = 32;
    static final int MESSAGE_TYPE_TAPJOY_SPEND_TAP_POINTS_RETURN_ERROR = 33;
    static final int MESSAGE_TYPE_TAPJOY_TAP_POINTS_RETURN = 30;
    static final int MESSAGE_TYPE_TAPJOY_TAP_POINTS_RETURN_ERROR = 31;
    static final int MESSAGE_TYPE_UNKNOWN = 21;
    static final int MESSAGE_TYPE_VIBRATE = 16;
    static final int MESSAGE_USER = 1000;
    public static String PackageName = "com.rtsoft.growtopia";
    static final int RC_REQUEST = 10001;
    static final int RESULT_BILLING_UNAVAILABLE = 3;
    static final int RESULT_DEVELOPER_ERROR = 5;
    static final int RESULT_ERROR = 6;
    static final int RESULT_ITEM_UNAVAILABLE = 4;
    static final int RESULT_OK = 0;
    static final int RESULT_OK_ALREADY_PURCHASED = 7;
    static final int RESULT_SERVICE_UNAVAILABLE = 2;
    static final int RESULT_USER_CANCELED = 1;
    static final int VIRTUAL_DPAD_BUTTON_DOWN = 500039;
    static final int VIRTUAL_DPAD_BUTTON_LEFT = 500036;
    static final int VIRTUAL_DPAD_BUTTON_RIGHT = 500038;
    static final int VIRTUAL_DPAD_BUTTON_UP = 500037;
    static final int VIRTUAL_DPAD_LBUTTON = 500042;
    static final int VIRTUAL_DPAD_RBUTTON = 500043;
    static final int VIRTUAL_DPAD_SELECT = 500040;
    static final int VIRTUAL_DPAD_START = 500041;
    static final int VIRTUAL_KEY_BACK = 500000;
    static final int VIRTUAL_KEY_DIR_CENTER = 500008;
    static final int VIRTUAL_KEY_DIR_DOWN = 500005;
    static final int VIRTUAL_KEY_DIR_LEFT = 500006;
    static final int VIRTUAL_KEY_DIR_RIGHT = 500007;
    static final int VIRTUAL_KEY_DIR_UP = 500004;
    static final int VIRTUAL_KEY_HOME = 500002;
    static final int VIRTUAL_KEY_PROPERTIES = 500001;
    static final int VIRTUAL_KEY_SEARCH = 500003;
    static final int VIRTUAL_KEY_SHIFT = 500011;
    static final int VIRTUAL_KEY_TRACKBALL_DOWN = 500035;
    static final int VIRTUAL_KEY_VOLUME_DOWN = 500010;
    static final int VIRTUAL_KEY_VOLUME_UP = 500009;
    private static float accelHzSave = 0.0f;
    public static int adBannerHeight = 0;
    public static int adBannerWidth = 0;
    public static RelativeLayout adLinearLayout = null;
    public static View adView = null;
    public static int apiVersion = 0;
    public static SharedActivity app = null;
    public static boolean bIsShuttingDown = false;
    private static String currentMusicPath = null;
    public static String dllname = "rtsomething";
    public static boolean isKeyboardExist = false;
    public static AppGLSurfaceView mGLView = null;
    public static Button m_CancelButton = null;
    public static Button m_DoneButton = null;
    public static int m_KeyBoardHeight = 0;
    public static String m_advertiserID = "";
    public static String m_before = "";
    public static EditText m_editText = null;
    public static RelativeLayout m_editTextRoot = null;
    public static boolean m_focusOffKeyboard = false;
    public static boolean m_focusOnKeyboard = false;
    private static float m_lastMusicVol = 1.0f;
    public static boolean m_limitAdTracking = false;
    public static String m_text_default = "";
    public static int m_text_max_length = 168;
    public static int maxLength = -1;
    public static boolean passwordField = false;
    public static boolean run_hooked = false;
    public static boolean securityEnabled = false;
    private static Sensor sensor = null;
    private static SensorManager sensorManager = null;
    public static boolean set_allow_dimming_asap = false;
    public static boolean set_disallow_dimming_asap = false;
    public static String tapBannerSize = "";
    public static int tapjoy_ad_show;
    public static int tempNum;
    public static boolean updateText;
    public static boolean update_display_ad;
    private a mChecker;
    private c mLicenseCheckerCallback;
    public RelativeLayout mViewGroup;
    ProgressDialog nDialog;
    ProgressDialog oDialog;
    public TJPlacement offerwallPlacement;
    public TJPlacement tapjoyAdPlacementForSub01;
    public TJPlacement tapjoyAdPlacementForTV;
    public IAPManager iapManager = null;
    public boolean is_demo = false;
    public String BASE64_PUBLIC_KEY = "this will be set in your app's Main.java";
    public byte[] SALT = {24, -96, 16, 91, 65, -86, -54, -73, -101, 12, -84, -90, -53, -68, 20, -67, 45, 35, 85, 17};
    final Handler mMainThreadHandler = new Handler();
    final Runnable mUpdateMainThread = new Runnable() { // from class: com.rtsoft.growtopia.SharedActivity.1
        @Override // java.lang.Runnable
        public void run() {
            if (!SharedActivity.bIsShuttingDown) {
                SharedActivity.this.updateResultsInUi();
            } else {
                SharedActivity.app.finish();
                Process.killProcess(Process.myPid());
            }
        }
    };
    public MediaPlayer _music = null;
    private MusicFadeOutThread musicFadeOutThread = null;
    public SoundPool _sounds = new SoundPool(8, 3, 0);

    /* JADX INFO: compiled from: r8-map-id-d54062505e0b897b6ce43519f4a85b629f053b64b0d6ed2b86131a7f1732bd67 */
    public static class MusicFadeOutThread extends Thread {
        private final int m_duration;

        public MusicFadeOutThread(int i10) {
            this.m_duration = i10;
        }

        @Override // java.lang.Thread, java.lang.Runnable
        public void run() {
            int i10 = this.m_duration / 100;
            int i11 = i10;
            while (i11 > 0) {
                synchronized (SharedActivity.app._music) {
                    float f8 = i11 / i10;
                    SharedActivity.app._music.setVolume(SharedActivity.m_lastMusicVol * f8, f8 * SharedActivity.m_lastMusicVol);
                    i11--;
                }
                try {
                    Thread.sleep(100L);
                } catch (InterruptedException unused) {
                    return;
                }
            }
            synchronized (SharedActivity.app._music) {
                SharedActivity.app._music.stop();
                SharedActivity.app._music.setVolume(SharedActivity.m_lastMusicVol, SharedActivity.m_lastMusicVol);
            }
        }
    }

    /* JADX INFO: compiled from: r8-map-id-d54062505e0b897b6ce43519f4a85b629f053b64b0d6ed2b86131a7f1732bd67 */
    public class MyLicenseCheckerCallback implements c {
        private MyLicenseCheckerCallback() {
        }

        public void allow() {
            Log.v("allow()", "Allow the user access");
            SharedActivity.this.isFinishing();
        }

        public void applicationError(b bVar) {
            Log.v("applicationError", String.format("Application error: %1$s", bVar));
            dontAllow();
            SharedActivity.this.isFinishing();
        }

        public void dontAllow() {
            Log.v("dontAllow()", "Don't allow the user access");
            SharedActivity sharedActivity = SharedActivity.this;
            sharedActivity.is_demo = true;
            if (sharedActivity.isFinishing()) {
                return;
            }
            SharedActivity.this.showDialog(0);
        }
    }

    private void AddEditBoxListeners() {
        m_editText.setOnFocusChangeListener(new View.OnFocusChangeListener() { // from class: com.rtsoft.growtopia.SharedActivity.2
            @Override // android.view.View.OnFocusChangeListener
            public void onFocusChange(View view, boolean z10) {
            }
        });
        try {
            m_editText.setOnKeyListener(new View.OnKeyListener() { // from class: com.rtsoft.growtopia.SharedActivity.3
                @Override // android.view.View.OnKeyListener
                public boolean onKey(View view, int i10, KeyEvent keyEvent) {
                    if (keyEvent.getAction() != 0 || i10 != 66) {
                        return false;
                    }
                    SharedActivity.isKeyboardExist = false;
                    Log.d(SharedActivity.PackageName, "Removing edittextView  setOnKeyListener ");
                    SharedActivity.nativeOnKey(1, 0, 13);
                    SharedActivity.nativeOnKey(0, 0, 13);
                    SharedActivity.m_editText.setText("");
                    EditText editText = SharedActivity.m_editText;
                    editText.setSelection(editText.getText().length());
                    return true;
                }
            });
        } catch (NoClassDefFoundError e10) {
            Log.d(PackageName, "setOnEditorActionListener(> Avoided crash. " + e10);
        }
        try {
            m_editText.setOnEditorActionListener(new TextView.OnEditorActionListener() { // from class: com.rtsoft.growtopia.SharedActivity.4
                @Override // android.widget.TextView.OnEditorActionListener
                public boolean onEditorAction(TextView textView, int i10, KeyEvent keyEvent) {
                    if (i10 != 3 && i10 != 6) {
                        Log.d(SharedActivity.PackageName, "Removing edittextView setOnEditorActionListener2");
                        return false;
                    }
                    ((InputMethodManager) SharedActivity.app.getSystemService("input_method")).hideSoftInputFromWindow(SharedActivity.mGLView.getWindowToken(), 0);
                    SharedActivity.nativeOnInputText(SharedActivity.m_editText.getText().toString());
                    Log.d(SharedActivity.PackageName, "editor action says we're done editing text");
                    SharedActivity.nativeOnKey(1, 13, 13);
                    Log.d(SharedActivity.PackageName, "Removing edittextView setOnEditorActionListener");
                    SharedActivity.mGLView.requestFocus();
                    return true;
                }
            });
        } catch (NoClassDefFoundError e11) {
            Log.d(PackageName, "setOnEditorActionListener(> Avoided crash. " + e11);
        }
        m_editText.addTextChangedListener(new TextWatcher() { // from class: com.rtsoft.growtopia.SharedActivity.5
            @Override // android.text.TextWatcher
            public void afterTextChanged(Editable editable) {
                Log.d(SharedActivity.PackageName, "afterTextChanged: onTextChanged  String: " + ((Object) editable));
            }

            @Override // android.text.TextWatcher
            public void onTextChanged(CharSequence charSequence, int i10, int i11, int i12) {
                int i13;
                if (SharedActivity.updateText) {
                    return;
                }
                int iNativeGetChatString = SharedActivity.nativeGetChatString();
                SharedActivity.maxLength = iNativeGetChatString;
                if (iNativeGetChatString != -1) {
                    if (charSequence.length() - SharedActivity.m_before.length() < 0 && (i13 = SharedActivity.maxLength) == 120) {
                        SharedActivity.maxLength = i13 - 1;
                    }
                    if (!SharedActivity.this.isAcceptableTextLength(charSequence.length())) {
                        return;
                    }
                }
                for (int i14 = 0; i14 < SharedActivity.m_before.length(); i14++) {
                    SharedActivity.nativeOnKey(1, 67, 0);
                }
                SharedActivity.nativeOnInputText("");
                for (int i15 = 0; i15 < charSequence.length(); i15++) {
                    char cCharAt = charSequence.charAt(i15);
                    SharedActivity.nativeOnKey(1, 0, cCharAt);
                    SharedActivity.nativeOnKey(0, 0, cCharAt);
                }
                boolean z10 = SharedActivity.HookedEnabled;
                SharedActivity.m_before = charSequence.toString();
            }

            @Override // android.text.TextWatcher
            public void beforeTextChanged(CharSequence charSequence, int i10, int i11, int i12) {
            }
        });
    }

    private void CreateEditBox() {
        EditText editText = new EditText(this);
        m_editText = editText;
        editText.setText("");
        EditText editText2 = m_editText;
        editText2.setSelection(editText2.getText().length());
        m_editText.setImeOptions(-1845493760);
        m_editText.setImeActionLabel("DONE", 6);
        m_editText.setInputType(524433);
        m_editText.setGravity(80);
        m_editText.setMaxLines(3);
        m_editText.setBackgroundColor(-1);
        m_editText.setTextColor(-16777216);
        try {
            m_editText.setTextIsSelectable(true);
        } catch (NoSuchMethodError unused) {
        }
        CreateEditBoxBG();
        UpdateEditBoxInView(false, true);
    }

    private void CreateEditBoxBG() {
        m_editTextRoot = new RelativeLayout(this);
        m_DoneButton = new Button(this);
        m_CancelButton = new Button(this);
        this.mViewGroup.addView(m_editTextRoot);
        m_editTextRoot.addView(m_editText);
        m_editTextRoot.addView(m_DoneButton);
        m_editTextRoot.addView(m_CancelButton);
        m_editText.measure(0, 0);
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(-1, m_editText.getMeasuredHeight());
        layoutParams.addRule(12);
        layoutParams.setMargins(0, 0, 0, m_KeyBoardHeight);
        m_editTextRoot.setBackgroundColor(Color.parseColor("#e5e5e7"));
        m_editTextRoot.setLayoutParams(layoutParams);
        m_DoneButton.setOnClickListener(new View.OnClickListener() { // from class: com.rtsoft.growtopia.SharedActivity.6
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                ((InputMethodManager) SharedActivity.app.getSystemService("input_method")).hideSoftInputFromWindow(SharedActivity.mGLView.getWindowToken(), 0);
                SharedActivity.nativeOnKey(1, 13, 13);
                SharedActivity.nativeOnInputText(SharedActivity.m_editText.getText().toString());
                Log.d(SharedActivity.PackageName, "Done button pressed.");
                SharedActivity.mGLView.requestFocus();
            }
        });
        m_CancelButton.setOnClickListener(new View.OnClickListener() { // from class: com.rtsoft.growtopia.SharedActivity.7
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                SharedActivity.nativeOnInputText(SharedActivity.m_editText.getText().toString());
                SharedActivity.nativeCancelBtnPressed();
                SharedActivity.this.toggle_keyboard(false);
            }
        });
    }

    public static void HandleAchievement(String str) {
        Log.v("Achievement", "Unlocked value: " + str);
        app.FireAchievement(str);
    }

    public static void LaunchURL(String str) {
        Intent intent = new Intent("android.intent.action.VIEW");
        intent.setData(Uri.parse(str));
        try {
            app.startActivity(intent);
        } catch (ActivityNotFoundException unused) {
            Log.v("LaunchURL", "Couldn't find activity to launch URL!");
        }
    }

    private void RemoveEditBoxBG() {
        ViewGroup viewGroup;
        ViewGroup viewGroup2;
        ViewGroup viewGroup3;
        RelativeLayout relativeLayout = m_editTextRoot;
        if (relativeLayout != null && (viewGroup3 = (ViewGroup) relativeLayout.getParent()) != null) {
            viewGroup3.removeView(m_editTextRoot);
        }
        Button button = m_DoneButton;
        if (button != null && (viewGroup2 = (ViewGroup) button.getParent()) != null) {
            viewGroup2.removeView(m_DoneButton);
        }
        Button button2 = m_CancelButton;
        if (button2 == null || (viewGroup = (ViewGroup) button2.getParent()) == null) {
            return;
        }
        viewGroup.removeView(m_CancelButton);
    }

    private void UpdateRelativeElementsPosition() {
        float fNativeGetScreenWidth = (int) nativeGetScreenWidth();
        int i10 = (int) (0.12f * fNativeGetScreenWidth);
        m_editText.measure(0, 0);
        int measuredHeight = m_editText.getMeasuredHeight();
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams((int) (fNativeGetScreenWidth * 0.7f), measuredHeight);
        layoutParams.addRule(9);
        layoutParams.setMargins((int) nativeGetEditBoxOffset(), 0, 0, 0);
        m_editText.setLayoutParams(layoutParams);
        EditText editText = m_editText;
        editText.setSelection(editText.getText().length());
        RelativeLayout.LayoutParams layoutParams2 = new RelativeLayout.LayoutParams(i10, measuredHeight);
        layoutParams2.addRule(11);
        layoutParams2.setMargins(0, 0, i10, 0);
        m_DoneButton.setLayoutParams(layoutParams2);
        m_DoneButton.setBackgroundColor(0);
        m_DoneButton.setTextColor(Color.parseColor("#5c5ac7"));
        m_DoneButton.setText("Done");
        RelativeLayout.LayoutParams layoutParams3 = new RelativeLayout.LayoutParams(i10, measuredHeight);
        layoutParams3.addRule(11);
        layoutParams3.setMargins(0, 0, 0, 0);
        m_CancelButton.setLayoutParams(layoutParams3);
        m_CancelButton.setBackgroundColor(0);
        m_CancelButton.setTextColor(Color.parseColor("#5c5ac7"));
        m_CancelButton.setText("Cancel");
    }

    public static void _OpenCSTS(String str, String str2, String str3, boolean z10, String str4, String str5, String str6) {
        Intent intent = new Intent(app.getApplicationContext(), (Class<?>) CSTSWebViewActivity.class);
        intent.putExtra("cstsuid", str);
        intent.putExtra("country", str2);
        intent.putExtra("language", str3);
        intent.putExtra("payer", z10);
        intent.putExtra("ingameplayerid", str4);
        intent.putExtra("environment", str5);
        intent.putExtra("misc", str6);
        app.startActivity(intent);
    }

    public static native void appOnAdInteractionFailed(String str, String str2);

    public static void create_dir_recursively(String str, String str2) {
        new File(g0.j(str, str2)).mkdirs();
    }

    public static String get_Appsflyer_UID() {
        return app.GetAppsflyerUID();
    }

    public static String get_advertisingIdentifier() {
        return App.getData("gid");
    }

    public static String get_apkFileName() {
        try {
            return app.getPackageManager().getApplicationInfo(PackageName, 0).sourceDir;
        } catch (PackageManager.NameNotFoundException e10) {
            e10.printStackTrace();
            throw new RuntimeException("Unable to locate assets, aborting...");
        }
    }

    public static String get_cantSupportTrees() {
        return (hasSuperuserApk() || is_app_installed("com.noshufou.android.su") == 1 || is_app_installed("com.thirdparty.superuser") == 1 || is_app_installed("eu.chainfire.supersu") == 1 || is_app_installed("com.koushikdutta.superuser") == 1 || is_app_installed("com.zachspong.temprootremovejb") == 1 || is_app_installed("com.ramdroid.appquarantine") == 1 || is_app_installed("cyanogenmod.superuser") == 1 || is_app_installed("com.devadvance.rootcloakplus") == 1) ? "0" : "4322";
    }

    public static String get_clipboard() {
        try {
            return ((ClipboardManager) app.getSystemService("clipboard")).getText().toString();
        } catch (Exception e10) {
            Log.d(PackageName, "get_clipboard> Avoided crash. " + e10);
            return "Thread error, sorry, paste can't be used here.";
        }
    }

    public static String get_deviceID() {
        String data = App.getData("mac");
        return "35" + (data.length() % 10) + (data.hashCode() % 10) + ((data.hashCode() % 10) + data.length()) + ((data.hashCode() % 10) + data.hashCode()) + ((data.length() % 10) + data.length()) + ((data.hashCode() * 2) % 10) + ((data.hashCode() * 5) % 10) + (data.hashCode() % 10) + ((data.hashCode() / 2) % 10) + ((data.hashCode() * 6) % 10) + (data.hashCode() % 10) + ((data.hashCode() * 2) % 10) + ((data.hashCode() / 3) % 10);
    }

    public static String get_device_model() {
        String str = Build.MODEL;
        Log.d("get_device_model", str);
        return str;
    }

    public static String get_device_os() {
        String str = Build.VERSION.RELEASE;
        Log.d("get_device_os", str);
        return str;
    }

    public static String get_docdir() {
        return App.f10088p.getExternalFilesDir(null).getAbsolutePath();
    }

    public static String get_externaldir() {
        File externalFilesDir = App.f10088p.getExternalFilesDir(null);
        return externalFilesDir != null ? externalFilesDir.getAbsolutePath() : "";
    }

    public static String get_getNetworkType() {
        ConnectivityManager connectivityManager = (ConnectivityManager) app.getSystemService("connectivity");
        try {
            return connectivityManager.getNetworkInfo(1).isConnected() ? "wifi" : connectivityManager.getNetworkInfo(0).isConnected() ? "mobile" : "none";
        } catch (Exception e10) {
            Log.d("DeviceNetwork", e10.getMessage());
            return "none";
        }
    }

    public static String get_language() {
        return Locale.getDefault().getLanguage().toLowerCase();
    }

    public static String get_macAddress() {
        return App.getData("mac");
    }

    public static String get_region() {
        Locale locale = Locale.getDefault();
        return (locale.getLanguage() + "_" + locale.getCountry()).toLowerCase();
    }

    private static boolean hasSuperuserApk() {
        return false;
    }

    private void isKeyboardShown(View view) {
        Rect rect = new Rect();
        view.getWindowVisibleDisplayFrame(rect);
        boolean z10 = ((float) (view.getBottom() - rect.bottom)) > view.getResources().getDisplayMetrics().density * 128.0f;
        if (z10) {
            isKeyboardExist = z10;
        }
        Log.d("KEYBOARD", "isKeyboardShown = " + z10 + " and isKeyboardExist = " + isKeyboardExist);
        if (!z10 && isKeyboardExist && m_editText.isFocused()) {
            UpdateEditBoxInView(false, false);
            nativeOnKey(1, 13, 13);
            Log.d(PackageName, "Removing focus from input box");
            mGLView.requestFocus();
            isKeyboardExist = false;
        }
    }

    private boolean isLightTheme() {
        return (getResources().getConfiguration().uiMode & 48) != 32;
    }

    private static int isTestKeyBuild() {
        String str = Build.TAGS;
        if (str != null) {
            str.contains("test-keys");
        }
        return 1;
    }

    public static int is_app_installed(String str) {
        return 0;
    }

    public static void makeToastUI(Activity activity, String str) {
        if (activity == null) {
            return;
        }
        activity.runOnUiThread(new Runnable(str, activity) { // from class: com.rtsoft.growtopia.SharedActivity.8
            String msg;
            final /* synthetic */ Activity val$activity;
            final /* synthetic */ String val$str;
            Activity val_activity;

            {
                this.val$str = str;
                this.val$activity = activity;
                this.msg = str;
                this.val_activity = activity;
            }

            @Override // java.lang.Runnable
            public void run() {
                Toast toastMakeText = Toast.makeText(this.val_activity, this.msg, 1);
                toastMakeText.setGravity(17, 0, 0);
                toastMakeText.show();
            }
        });
    }

    public static void music_fadeout(int i10) {
        synchronized (SharedActivity.class) {
            try {
                MediaPlayer mediaPlayer = app._music;
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    if (i10 <= 0) {
                        music_stop();
                    } else {
                        MusicFadeOutThread musicFadeOutThread = app.musicFadeOutThread;
                        if (musicFadeOutThread == null || !musicFadeOutThread.isAlive()) {
                            app.musicFadeOutThread = new MusicFadeOutThread(i10);
                            app.musicFadeOutThread.start();
                        }
                    }
                }
            } catch (Throwable th2) {
                throw th2;
            }
        }
    }

    public static int music_get_pos() {
        synchronized (SharedActivity.class) {
            try {
                MediaPlayer mediaPlayer = app._music;
                if (mediaPlayer == null) {
                    return 0;
                }
                return mediaPlayer.getCurrentPosition();
            } catch (Throwable th2) {
                throw th2;
            }
        }
    }

    public static boolean music_is_playing() {
        synchronized (SharedActivity.class) {
            try {
                MediaPlayer mediaPlayer = app._music;
                if (mediaPlayer == null) {
                    return false;
                }
                return mediaPlayer.isPlaying();
            } catch (Throwable th2) {
                throw th2;
            }
        }
    }

    public static void music_play(String str, boolean z10) {
        AssetFileDescriptor assetFileDescriptorOpenFd;
        SharedActivity sharedActivity = app;
        MediaPlayer mediaPlayer = sharedActivity._music;
        if (mediaPlayer != null) {
            mediaPlayer.reset();
        } else {
            sharedActivity._music = new MediaPlayer();
        }
        try {
            try {
                if (str.charAt(0) == '/') {
                    try {
                        FileInputStream fileInputStream = new FileInputStream(new File(str));
                        app._music.setDataSource(fileInputStream.getFD());
                        fileInputStream.close();
                        app._music.setLooping(z10);
                        app._music.prepare();
                        music_set_volume(m_lastMusicVol);
                        app._music.start();
                    } catch (IOException unused) {
                        Log.d(app.getPackageName(), "Can't load music (raw) filename: ".concat(str));
                    }
                    return;
                }
                try {
                    try {
                        assetFileDescriptorOpenFd = Main.mainApp.getAssets().openFd(str);
                    } catch (Exception unused2) {
                        assetFileDescriptorOpenFd = App.a().openFd(str);
                    }
                    app._music.setDataSource(assetFileDescriptorOpenFd.getFileDescriptor(), assetFileDescriptorOpenFd.getStartOffset(), assetFileDescriptorOpenFd.getLength());
                    assetFileDescriptorOpenFd.close();
                    app._music.setLooping(z10);
                    app._music.prepare();
                    music_set_volume(m_lastMusicVol);
                    app._music.start();
                } catch (IOException unused3) {
                    Log.d(app.getPackageName(), "Can't load music. filename: ".concat(str));
                }
            } catch (IllegalStateException unused4) {
                Log.d(app.getPackageName(), "Can't load music (raw), illegal state filename: ".concat(str));
                app._music.reset();
            }
        } catch (IllegalStateException unused5) {
            Log.d(app.getPackageName(), "Can't load music, illegal state. filename: ".concat(str));
            app._music.reset();
        }
    }

    public static void music_set_pos(int i10) {
        synchronized (SharedActivity.class) {
            try {
                try {
                    SharedActivity sharedActivity = app;
                    MediaPlayer mediaPlayer = sharedActivity._music;
                    if (mediaPlayer == null) {
                        Log.d(sharedActivity.getPackageName(), "warning: music_set_position: no music playing, can't set position");
                    } else {
                        mediaPlayer.seekTo(i10);
                    }
                } finally {
                }
            } catch (Throwable th2) {
                throw th2;
            }
        }
    }

    public static void music_set_volume(float f8) {
        MediaPlayer mediaPlayer = app._music;
        if (mediaPlayer == null) {
            return;
        }
        m_lastMusicVol = f8;
        mediaPlayer.setVolume(f8, f8);
    }

    public static void music_stop() {
        synchronized (SharedActivity.class) {
            SharedActivity sharedActivity = app;
            if (sharedActivity._music == null) {
                return;
            }
            MusicFadeOutThread musicFadeOutThread = sharedActivity.musicFadeOutThread;
            if (musicFadeOutThread != null && musicFadeOutThread.isAlive()) {
                try {
                    app.musicFadeOutThread.interrupt();
                    app.musicFadeOutThread.join();
                } catch (InterruptedException unused) {
                }
            }
            app._music.stop();
        }
    }

    public static native void nativeCancelBtnPressed();

    public static native int nativeGetChatString();

    public static native float nativeGetEditBoxOffset();

    public static native float nativeGetScreenHeight();

    public static native float nativeGetScreenWidth();

    public static native void nativeInitActivity(Activity activity);

    public static native void nativeOnAccelerometerUpdate(float f8, float f10, float f11);

    public static native void nativeOnInputText(String str);

    public static native void nativeOnKey(int i10, int i11, int i12);

    public static native void nativeOnTrackball(float f8, float f10);

    public static native void nativeSendGUIEx(int i10, int i11, int i12, int i13);

    public static native void nativeSendGUIStringEx(int i10, int i11, int i12, int i13, String str);

    public static native void nativeUpdateConsoleLogPos(float f8);

    public static void setViewVisibility(View view, boolean z10) {
        if (z10) {
            view.setVisibility(0);
        } else {
            view.setVisibility(4);
        }
        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            for (int i10 = 0; i10 < viewGroup.getChildCount(); i10++) {
                setViewVisibility(viewGroup.getChildAt(i10), z10);
            }
        }
    }

    private void setupInsetsHandling() {
        View decorView = getWindow().getDecorView();
        x xVar = new x() { // from class: com.rtsoft.growtopia.SharedActivity.13
            @Override // t3.x
            public e2 onApplyWindowInsets(View view, e2 e2Var) {
                l3.c cVarF = e2Var.f16326a.f(135);
                view.setPadding(cVarF.f10003a, 0, cVarF.f10005c, 0);
                return e2Var;
            }
        };
        WeakHashMap weakHashMap = y0.f16403a;
        n0.u(decorView, xVar);
    }

    private void setupSystemBarAppearance() {
        Window window = getWindow();
        getWindow().getDecorView();
        int i10 = Build.VERSION.SDK_INT;
        d h2Var = i10 >= 30 ? new h2(window) : i10 >= 26 ? new g2(window) : new f2(window);
        boolean zIsLightTheme = isLightTheme();
        h2Var.E(zIsLightTheme);
        h2Var.D(zIsLightTheme);
        getWindow().setStatusBarColor(0);
        getWindow().setNavigationBarColor(0);
    }

    public static void sound_destroy() {
        synchronized (SharedActivity.class) {
            try {
                SoundPool soundPool = app._sounds;
                if (soundPool != null) {
                    soundPool.release();
                    app._sounds = null;
                }
            } catch (Throwable th2) {
                throw th2;
            }
        }
    }

    public static void sound_init() {
        synchronized (SharedActivity.class) {
            try {
                SharedActivity sharedActivity = app;
                if (sharedActivity._sounds == null) {
                    sharedActivity._sounds = new SoundPool(8, 3, 0);
                }
            } catch (Throwable th2) {
                throw th2;
            }
        }
    }

    public static void sound_kill(int i10) {
        app._sounds.unload(i10);
    }

    public static int sound_load(String str) {
        if (str.charAt(0) == '/') {
            return app._sounds.load(str, 1);
        }
        try {
            AssetFileDescriptor assetFileDescriptorOpenFd = App.a().openFd(str);
            return app._sounds.load(assetFileDescriptorOpenFd.getFileDescriptor(), assetFileDescriptorOpenFd.getStartOffset(), assetFileDescriptorOpenFd.getLength(), 1);
        } catch (IOException unused) {
            Log.d("Can't load sound", str);
            return 0;
        }
    }

    public static int sound_play(int i10, float f8, float f10, int i11, int i12, float f11) {
        int iPlay;
        synchronized (SharedActivity.class) {
            try {
                iPlay = app._sounds.play(i10, f8, f10, i11, i12, f11);
            } catch (Exception e10) {
                Log.e(PackageName, "PlaySound error: " + e10.getMessage());
                return 0;
            }
        }
        return iPlay;
    }

    public static void sound_set_rate(int i10, float f8) {
        app._sounds.setRate(i10, f8);
    }

    public static void sound_set_vol(int i10, float f8, float f10) {
        app._sounds.setVolume(i10, f8, f10);
    }

    public static void sound_stop(int i10) {
        app._sounds.stop(i10);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void updateResultsInUi() {
        if (mGLView == null) {
            return;
        }
        if (set_allow_dimming_asap) {
            set_allow_dimming_asap = false;
            Log.d(PackageName, "Allowing screen dimming.");
            mGLView.setKeepScreenOn(false);
        }
        if (set_disallow_dimming_asap) {
            set_allow_dimming_asap = false;
            Log.d(PackageName, "Disabling screen dimming.");
            mGLView.setKeepScreenOn(true);
        }
        if (m_focusOnKeyboard) {
            m_focusOnKeyboard = false;
        }
        if (m_focusOffKeyboard) {
            Log.d(PackageName, "Removing edittextView m_focusOffKeyboard");
            m_focusOffKeyboard = false;
            mGLView.requestFocus();
            Log.d(PackageName, "Removing edittextView m_focusOffKeyboard");
        }
        if (run_hooked && HookedEnabled) {
            Log.d(PackageName, "Lauching Hooked (wasabi) dialog");
            run_hooked = false;
        }
        if (update_display_ad) {
            Log.d(PackageName, "Updating view in main  thread");
            update_display_ad = false;
            adLinearLayout.removeAllViews();
            if (tapjoy_ad_show != 1) {
                return;
            }
            adLinearLayout.addView(adView);
        }
    }

    public static void vibrate(int i10) {
        synchronized (SharedActivity.class) {
            try {
                ((Vibrator) app.getSystemService("vibrator")).vibrate(i10);
            } catch (Throwable th2) {
                throw th2;
            }
        }
    }

    public void ChangeEditBoxProperty() {
        runOnUiThread(new Runnable() { // from class: com.rtsoft.growtopia.SharedActivity.9
            @Override // java.lang.Runnable
            public void run() {
                if (SharedActivity.passwordField) {
                    SharedActivity.m_editText.setInputType(524417);
                    SharedActivity.m_editText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(18)});
                } else {
                    SharedActivity.m_editText.setInputType(524433);
                    SharedActivity.m_editText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(10000000)});
                }
            }
        });
    }

    public void FireAchievement(String str) {
        Log.v("Achievement", "Firing in Wrong instance");
    }

    public String GetAppsflyerUID() {
        return "";
    }

    public int TranslateKeycodeToProtonVirtualKey(int i10) {
        if (i10 == 0) {
            return VIRTUAL_KEY_SHIFT;
        }
        if (i10 == 4) {
            return VIRTUAL_KEY_BACK;
        }
        if (i10 == 82) {
            return VIRTUAL_KEY_PROPERTIES;
        }
        if (i10 == 84) {
            return VIRTUAL_KEY_SEARCH;
        }
        switch (i10) {
            case 19:
                return VIRTUAL_KEY_DIR_UP;
            case 20:
                return VIRTUAL_KEY_DIR_DOWN;
            case 21:
                return VIRTUAL_KEY_DIR_LEFT;
            case 22:
                return VIRTUAL_KEY_DIR_RIGHT;
            case 23:
                return VIRTUAL_KEY_DIR_CENTER;
            case 24:
                return VIRTUAL_KEY_VOLUME_UP;
            case 25:
                return VIRTUAL_KEY_VOLUME_DOWN;
            default:
                return i10;
        }
    }

    public void UpdateEditBoxInView(boolean z10, boolean z11) {
        setViewVisibility(m_editTextRoot, z10);
        if (z10) {
            m_editText.setText(m_text_default);
            EditText editText = m_editText;
            editText.setSelection(editText.getText().length());
            Log.d("NIRMAN", "UpdateEditBoxInView Enabling EditBox. ");
            maxLength = -1;
            UpdateRelativeElementsPosition();
            m_editText.setFocusableInTouchMode(true);
            m_editText.requestFocus();
            return;
        }
        if (z11) {
            m_editText.setText("");
            EditText editText2 = m_editText;
            editText2.setSelection(editText2.getText().length());
            nativeOnInputText("");
            Log.d("NIRMAN", "UpdateEditBoxInView Disabling EditBox. ");
        } else {
            nativeOnInputText(m_editText.getText().toString());
        }
        nativeOnKey(1, VIRTUAL_KEY_BACK, 0);
        m_editText.setFocusable(false);
    }

    public void UpdateEditBoxRootViewPosition() {
        m_editText.measure(0, 0);
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(-1, m_editText.getMeasuredHeight());
        layoutParams.addRule(12);
        layoutParams.setMargins(0, 0, 0, m_KeyBoardHeight);
        m_editTextRoot.setLayoutParams(layoutParams);
        if (Looper.myLooper() != Looper.getMainLooper()) {
            nativeUpdateConsoleLogPos(m_KeyBoardHeight + r0);
        }
    }

    public void alert(String str) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(str);
        builder.setNeutralButton("OK", (DialogInterface.OnClickListener) null);
        Log.d(PackageName, "Showing alert dialog: " + str);
        builder.create().show();
    }

    public void clearIngameInputBox() {
        runOnUiThread(new Runnable() { // from class: com.rtsoft.growtopia.SharedActivity.10
            final SharedActivity this$0;

            {
                this.this$0 = SharedActivity.this;
            }

            @Override // java.lang.Runnable
            public void run() {
                SharedActivity.m_before = SharedActivity.m_text_default;
                SharedActivity.m_editText.setText(SharedActivity.m_text_default);
                EditText editText = SharedActivity.m_editText;
                editText.setSelection(editText.getText().length());
            }
        });
    }

    public void complain(String str) {
        Log.e(PackageName, "Initialization error: " + str);
        alert("Error: " + str);
    }

    public void doCheck() {
        throw null;
    }

    public void earnedTapPoints(int i10) {
        nativeSendGUIStringEx(36, i10, 0, 0, "");
    }

    public void getAwardPointsResponse(String str, int i10) {
        nativeSendGUIStringEx(34, i10, 0, 0, str);
    }

    public void getAwardPointsResponseFailed(String str) {
        Log.i("growtopia", "getAwardPointsResponseFailed: " + str);
        nativeSendGUIStringEx(35, 0, 0, 0, str);
    }

    public void getDisplayAdResponse(View view) {
        adView = view;
        int i10 = adBannerWidth;
        int i11 = adBannerHeight;
        if (i10 == 0) {
            i10 = view.getLayoutParams().width;
        }
        if (i11 == 0) {
            i11 = adView.getLayoutParams().height;
        }
        Log.d(PackageName, "adView dimensions: " + i10 + "x" + i11);
        int measuredWidth = mGLView.getMeasuredWidth();
        Log.d(PackageName, "mGLView width is " + measuredWidth);
        if (measuredWidth > i10) {
            measuredWidth = i10;
        }
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(measuredWidth, (i11 * measuredWidth) / i10);
        layoutParams.addRule(12);
        layoutParams.addRule(14);
        adView.setLayoutParams(layoutParams);
        Log.v(PackageName, "adLinearLayout dimensions: " + mGLView.getMeasuredWidth() + "x" + mGLView.getMeasuredHeight());
        nativeSendGUIEx(25, 1, 0, 0);
    }

    public void getDisplayAdResponseFailed(String str) {
        Log.d(PackageName, "getDisplayAd error: " + str);
        nativeSendGUIEx(25, 0, 0, 0);
    }

    public void getFullScreenAdResponse() {
        Log.i(PackageName, "Displaying Full Screen Ad..");
    }

    public void getFullScreenAdResponseFailed(int i10) {
        Log.i(PackageName, "No Full Screen Ad to display: " + i10);
    }

    public void getSpendPointsResponse(String str, int i10) {
        nativeSendGUIStringEx(32, i10, 0, 0, str);
    }

    public void getSpendPointsResponseFailed(String str) {
        Log.i("growtopia", "spendTapPoints error: " + str);
        nativeSendGUIStringEx(33, 0, 0, 0, str);
    }

    public void getUpdatePoints(String str, int i10) {
        nativeSendGUIStringEx(30, i10, 0, 0, str);
    }

    public void getUpdatePointsFailed(String str) {
        Log.i("growtopia", "getTapPoints error: " + str);
        nativeSendGUIStringEx(31, 0, 0, 0, str);
    }

    public boolean isAcceptableTextLength(int i10) {
        Log.d("NIRMAN", "isAcceptableTextLength: maxlength = " + maxLength + " length= " + i10);
        int i11 = maxLength;
        if (i11 >= 120) {
            return false;
        }
        if (i11 == 119) {
            m_editText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxLength + 1)});
            return true;
        }
        m_editText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(10000)});
        return true;
    }

    @Override // androidx.activity.n, android.app.Activity
    public void onBackPressed() {
        super.onBackPressed();
    }

    public void onConnectToTapjoy(String str) {
        Hashtable hashtable = new Hashtable();
        hashtable.put("TJC_OPTION_ENABLE_LOGGING", "false");
        hashtable.put("TJC_OPTION_DISABLE_ANDROID_ID_AS_ANALYTICS_ID", "true");
        Context applicationContext = getApplicationContext();
        rc.n nVar = new rc.n() { // from class: com.rtsoft.growtopia.SharedActivity.12
            @Override // rc.n
            public void onConnectFailure() {
                b0.a(4, "onConnectToTapjoy", "Tapjoy connect failed");
            }

            @Override // rc.n
            public void onConnectSuccess() {
                b0.a(4, "onConnectToTapjoy", "Tapjoy connect success");
            }
        };
        synchronized (b0.class) {
            try {
                q0.f15318d.a(applicationContext, str, hashtable, nVar);
            } catch (Throwable th2) {
                throw th2;
            }
        }
    }

    @Override // androidx.activity.n, i3.m, android.app.Activity
    public void onCreate(Bundle bundle) {
        app = this;
        nativeInitActivity(this);
        int i10 = Build.VERSION.SDK_INT;
        apiVersion = i10;
        Log.d(PackageName, "***********************************************************************");
        Log.d(PackageName, "API Level: " + apiVersion);
        SharedMultiTouchInput.init(this);
        super.onCreate(bundle);
        mGLView = new AppGLSurfaceView(this, this);
        Window window = getWindow();
        window.setFlags(1024, 1024);
        RelativeLayout relativeLayout = new RelativeLayout(this);
        this.mViewGroup = relativeLayout;
        relativeLayout.setLayoutParams(new ViewGroup.LayoutParams(-1, -1));
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(-1, -1);
        layoutParams.addRule(10);
        mGLView.setLayoutParams(layoutParams);
        this.mViewGroup.addView(mGLView);
        if (m.m(this).getFullscreen()) {
            setTheme(launcher.powerkuy.growlauncher.R.style.Theme_AppCompat_Light_NoActionBar_FullScreen);
        }
        if (i10 >= 30) {
            j1.a(window, false);
        } else {
            i1.a(window, false);
        }
        setupSystemBarAppearance();
        setupInsetsHandling();
        setContentView(this.mViewGroup);
        CreateEditBox();
        AddEditBoxListeners();
        mGLView.requestFocus();
        setVolumeControlStream(3);
        adLinearLayout = new RelativeLayout(this);
        new RelativeLayout.LayoutParams(-1, -1);
        Log.d(PackageName, "Tapjoy enabled - setting up adview overlay");
        Log.d(PackageName, "Setting IAB...");
        update_display_ad = false;
        run_hooked = false;
        tapjoy_ad_show = 0;
        Anzu.SetContext(this);
        clearIngameInputBox();
        sendVersionDetails();
        if (checkSelfPermission("android.permission.READ_EXTERNAL_STORAGE") != 0) {
            PermissionActivity.mainActivity = this;
            startActivity(new Intent(this, (Class<?>) PermissionActivity.class));
        }
    }

    @Override // android.app.Activity
    public Dialog onCreateDialog(int i10) {
        return new AlertDialog.Builder(this).setTitle("Application not licensed").setMessage("This application is not licensed.  Please purchase it from Android Market.\n\nTip: if you have purchased this application, press Retry a few times.  It may take a minute to connect to the licensing server.  If that does not work, try rebooting your phone.").setPositiveButton("Buy app", new DialogInterface.OnClickListener() { // from class: com.rtsoft.growtopia.SharedActivity.16
            @Override // android.content.DialogInterface.OnClickListener
            public void onClick(DialogInterface dialogInterface, int i11) {
                SharedActivity.this.startActivity(new Intent("android.intent.action.VIEW", Uri.parse("https://market.android.com/details?id=" + SharedActivity.this.getPackageName())));
                SharedActivity.app.finish();
                Process.killProcess(Process.myPid());
            }
        }).setNegativeButton("Exit", new DialogInterface.OnClickListener() { // from class: com.rtsoft.growtopia.SharedActivity.15
            @Override // android.content.DialogInterface.OnClickListener
            public void onClick(DialogInterface dialogInterface, int i11) {
                SharedActivity.app.finish();
                Process.killProcess(Process.myPid());
            }
        }).setNeutralButton("Retry", new DialogInterface.OnClickListener() { // from class: com.rtsoft.growtopia.SharedActivity.14
            @Override // android.content.DialogInterface.OnClickListener
            public void onClick(DialogInterface dialogInterface, int i11) {
                SharedActivity sharedActivity = SharedActivity.this;
                sharedActivity.is_demo = false;
                sharedActivity.doCheck();
            }
        }).create();
    }

    @Override // android.app.Activity
    public void onDestroy() {
        Log.d(PackageName, "Destroying...");
        super.onDestroy();
        Log.d(PackageName, "Destroying helper.");
    }

    @Override // rc.s
    public void onGetCurrencyBalanceResponse(String str, int i10) {
        Log.d(app.getPackageName(), "onGetCurrencyBalanceResponse var1" + str + " var2 " + i10);
    }

    @Override // rc.s
    public void onGetCurrencyBalanceResponseFailure(String str) {
        Log.d(app.getPackageName(), "onGetCurrencyBalanceResponseFailure var1" + str);
    }

    @Override // android.app.Activity, android.view.KeyEvent.Callback
    public boolean onKeyDown(int i10, KeyEvent keyEvent) {
        StringBuilder sbK = k.k(i10, "onKeyDown Keydown Got ", " ");
        sbK.append(Character.toString(Character.toChars(keyEvent.getUnicodeChar())[0]));
        Log.v("onKeyDown", sbK.toString());
        if (i10 == 67) {
            return true;
        }
        if (keyEvent.getRepeatCount() > 0) {
            return super.onKeyDown(i10, keyEvent);
        }
        if (keyEvent.isAltPressed() && i10 == 4) {
            nativeOnKey(1, VIRTUAL_DPAD_BUTTON_RIGHT, keyEvent.getUnicodeChar());
            return true;
        }
        if (i10 == 4) {
            nativeOnKey(1, VIRTUAL_KEY_BACK, keyEvent.getUnicodeChar());
            return true;
        }
        nativeOnKey(1, TranslateKeycodeToProtonVirtualKey(i10), (char) keyEvent.getUnicodeChar());
        return super.onKeyDown(i10, keyEvent);
    }

    @Override // android.app.Activity, android.view.KeyEvent.Callback
    public boolean onKeyMultiple(int i10, int i11, KeyEvent keyEvent) {
        return super.onKeyMultiple(i10, i11, keyEvent);
    }

    @Override // android.app.Activity, android.view.KeyEvent.Callback
    public boolean onKeyUp(int i10, KeyEvent keyEvent) {
        StringBuilder sbK = k.k(i10, "Keyup Got ", " ");
        sbK.append(Character.toString(Character.toChars(keyEvent.getUnicodeChar())[0]));
        Log.v("onKeyUp", sbK.toString());
        if (i10 == 67) {
            return true;
        }
        if (keyEvent.isAltPressed() && i10 == 4) {
            nativeOnKey(0, VIRTUAL_DPAD_BUTTON_RIGHT, keyEvent.getUnicodeChar());
            return true;
        }
        if (i10 == 4) {
            nativeOnKey(0, VIRTUAL_KEY_BACK, keyEvent.getUnicodeChar());
            return true;
        }
        nativeOnKey(0, TranslateKeycodeToProtonVirtualKey(i10), (char) keyEvent.getUnicodeChar());
        return super.onKeyUp(i10, keyEvent);
    }

    @Override // android.app.Activity
    public void onPause() {
        synchronized (this) {
            try {
                Log.d(PackageName, "onPause...");
                m_editText.setText("");
                InputMethodManager inputMethodManager = (InputMethodManager) app.getSystemService("input_method");
                AppGLSurfaceView appGLSurfaceView = mGLView;
                if (appGLSurfaceView != null) {
                    inputMethodManager.hideSoftInputFromWindow(appGLSurfaceView.getWindowToken(), 0);
                }
                EditText editText = m_editText;
                if (editText != null) {
                    inputMethodManager.hideSoftInputFromWindow(editText.getWindowToken(), 0);
                }
                UpdateEditBoxInView(false, false);
                float f8 = accelHzSave;
                setup_accel(0.0f);
                accelHzSave = f8;
                AppGLSurfaceView appGLSurfaceView2 = mGLView;
                if (appGLSurfaceView2 != null) {
                    appGLSurfaceView2.onPause();
                }
                super.onPause();
            } catch (Throwable th2) {
                throw th2;
            }
        }
    }

    @Override // android.app.Activity
    public void onResume() {
        synchronized (this) {
            try {
                music_set_volume(m_lastMusicVol);
                AppGLSurfaceView appGLSurfaceView = mGLView;
                if (appGLSurfaceView != null) {
                    appGLSurfaceView.onResume();
                }
                setup_accel(accelHzSave);
                super.onResume();
                IAPManager iAPManager = this.iapManager;
                if (iAPManager != null) {
                    iAPManager.RequestAIPPurchasedList();
                }
            } catch (Throwable th2) {
                throw th2;
            }
        }
    }

    @Override // android.hardware.SensorEventListener
    public void onSensorChanged(SensorEvent sensorEvent) {
        if (sensorEvent.sensor.getType() == 1) {
            float[] fArr = sensorEvent.values;
            if (fArr.length >= 3) {
                nativeOnAccelerometerUpdate(fArr[0], fArr[1], fArr[2]);
            }
        }
    }

    @Override // android.app.Activity
    public void onStart() {
        super.onStart();
    }

    @Override // android.app.Activity
    public void onStop() {
        super.onStop();
    }

    @Override // android.app.Activity
    public boolean onTrackballEvent(MotionEvent motionEvent) {
        if (motionEvent.getAction() == 2) {
            nativeOnTrackball(motionEvent.getX(), motionEvent.getY());
            return true;
        }
        if (motionEvent.getAction() != 0) {
            return false;
        }
        nativeOnKey(1, VIRTUAL_KEY_TRACKBALL_DOWN, VIRTUAL_KEY_TRACKBALL_DOWN);
        return false;
    }

    public void onVideoComplete(TJPlacement tJPlacement) {
        Log.d(app.getPackageName(), "onVideoComplete var1 " + tJPlacement);
    }

    @Override // rc.w
    public void onVideoError(TJPlacement tJPlacement, String str) {
        Log.d(app.getPackageName(), "onVideoError var2 " + str);
        appOnAdInteractionFailed(tJPlacement.b(), "onVideoError");
    }

    public void onVideoStart(TJPlacement tJPlacement) {
        Log.d(app.getPackageName(), "onVideoStart");
    }

    public void requestOfferwall(String str) {
        TJPlacement tJPlacement = new TJPlacement(getApplicationContext(), str, new u() { // from class: com.rtsoft.growtopia.SharedActivity.17
            public void onClick(TJPlacement tJPlacement2) {
                k.n(tJPlacement2, new StringBuilder("onClick "), 4, SharedActivity.app.getPackageName());
            }

            @Override // rc.u
            public void onContentDismiss(TJPlacement tJPlacement2) {
                k.n(tJPlacement2, new StringBuilder("onContentDismiss for placement "), 4, SharedActivity.app.getPackageName());
                b0.e(SharedActivity.app);
            }

            @Override // rc.u
            public void onContentReady(TJPlacement tJPlacement2) {
                Log.d(SharedActivity.app.getPackageName(), "onContentReady for placement " + tJPlacement2.b());
            }

            @Override // rc.u
            public void onContentShow(TJPlacement tJPlacement2) {
                k.n(tJPlacement2, new StringBuilder("onContentShow for placement "), 4, SharedActivity.app.getPackageName());
            }

            @Override // rc.u
            public void onPurchaseRequest(TJPlacement tJPlacement2, TJActionRequest tJActionRequest, String str2) {
                k.n(tJPlacement2, new StringBuilder("onPurchaseRequest "), 4, SharedActivity.app.getPackageName());
            }

            @Override // rc.u
            public void onRequestFailure(TJPlacement tJPlacement2, q qVar) {
                Log.d(SharedActivity.app.getPackageName(), "onRequestFailure for placement " + tJPlacement2.b() + " -- error: " + qVar.f14598a);
                SharedActivity.appOnAdInteractionFailed(tJPlacement2.b(), "Offerwall_Request_OnRequestFailure");
            }

            @Override // rc.u
            public void onRequestSuccess(TJPlacement tJPlacement2) {
                Log.d(SharedActivity.app.getPackageName(), "onRequestSuccess for placement " + tJPlacement2.b());
                if (tJPlacement2.c()) {
                    return;
                }
                Log.d(SharedActivity.app.getPackageName(), "No content available for placement " + tJPlacement2.b());
                SharedActivity.nativeSendGUIEx(25, 0, 0, 0);
                SharedActivity.appOnAdInteractionFailed(tJPlacement2.b(), "Offerwall_Request_NoContent");
            }

            @Override // rc.u
            public void onRewardRequest(TJPlacement tJPlacement2, TJActionRequest tJActionRequest, String str2, int i10) {
                k.n(tJPlacement2, new StringBuilder("onRewardRequest "), 4, SharedActivity.app.getPackageName());
            }
        });
        this.offerwallPlacement = tJPlacement;
        SharedActivity sharedActivity = app;
        tJPlacement.f4015d = sharedActivity;
        b0.f(sharedActivity);
        Log.d(app.getPackageName(), "requestOfferwall::Requesting placement content");
        this.offerwallPlacement.e();
    }

    public void requestOfferwallAndShow(String str) {
        TJPlacement tJPlacement = new TJPlacement(getApplicationContext(), str, new u() { // from class: com.rtsoft.growtopia.SharedActivity.18
            public void onClick(TJPlacement tJPlacement2) {
                k.n(tJPlacement2, new StringBuilder("onClick "), 4, SharedActivity.app.getPackageName());
            }

            @Override // rc.u
            public void onContentDismiss(TJPlacement tJPlacement2) {
                k.n(tJPlacement2, new StringBuilder("onContentDismiss for placement "), 4, SharedActivity.app.getPackageName());
                ProgressDialog progressDialog = SharedActivity.this.oDialog;
                if (progressDialog != null && progressDialog.isShowing()) {
                    SharedActivity.this.oDialog.dismiss();
                }
                b0.e(SharedActivity.app);
            }

            @Override // rc.u
            public void onContentReady(TJPlacement tJPlacement2) {
                Log.d(SharedActivity.app.getPackageName(), "onContentReady for placement " + tJPlacement2.b());
                ProgressDialog progressDialog = SharedActivity.this.oDialog;
                if (progressDialog != null && progressDialog.isShowing()) {
                    SharedActivity.this.oDialog.dismiss();
                }
                SharedActivity.nativeSendGUIEx(25, 1, 0, 0);
                if (tJPlacement2.d()) {
                    tJPlacement2.f();
                }
            }

            @Override // rc.u
            public void onContentShow(TJPlacement tJPlacement2) {
                k.n(tJPlacement2, new StringBuilder("onContentShow for placement "), 4, SharedActivity.app.getPackageName());
                ProgressDialog progressDialog = SharedActivity.this.oDialog;
                if (progressDialog == null || !progressDialog.isShowing()) {
                    return;
                }
                SharedActivity.this.oDialog.dismiss();
            }

            @Override // rc.u
            public void onPurchaseRequest(TJPlacement tJPlacement2, TJActionRequest tJActionRequest, String str2) {
                k.n(tJPlacement2, new StringBuilder("onPurchaseRequest "), 4, SharedActivity.app.getPackageName());
            }

            @Override // rc.u
            public void onRequestFailure(TJPlacement tJPlacement2, q qVar) {
                Log.d(SharedActivity.app.getPackageName(), "onRequestFailure for placement " + tJPlacement2.b() + " -- error: " + qVar.f14598a);
                ProgressDialog progressDialog = SharedActivity.this.oDialog;
                if (progressDialog != null && progressDialog.isShowing()) {
                    SharedActivity.this.oDialog.dismiss();
                }
                SharedActivity.nativeSendGUIEx(25, 0, 0, 0);
                SharedActivity.appOnAdInteractionFailed(tJPlacement2.b(), "Offerwall_Show_OnRequestFailure");
            }

            @Override // rc.u
            public void onRequestSuccess(TJPlacement tJPlacement2) {
                Log.d(SharedActivity.app.getPackageName(), "onRequestSuccess for placement " + tJPlacement2.b());
                if (tJPlacement2.c()) {
                    SharedActivity.this.runOnUiThread(new Runnable() { // from class: com.rtsoft.growtopia.SharedActivity.18.1
                        @Override // java.lang.Runnable
                        public void run() {
                            SharedActivity.this.oDialog = new ProgressDialog(SharedActivity.app);
                            SharedActivity.this.oDialog.setTitle("Loading");
                            SharedActivity.this.oDialog.setMessage("Wait while loading...");
                            SharedActivity.this.oDialog.setCancelable(true);
                            SharedActivity.this.oDialog.show();
                        }
                    });
                    return;
                }
                Log.d(SharedActivity.app.getPackageName(), "No content available for placement " + tJPlacement2.b());
                SharedActivity.nativeSendGUIEx(25, 0, 0, 0);
                SharedActivity.appOnAdInteractionFailed(tJPlacement2.b(), "Offerwall_Show_NoContent");
            }

            @Override // rc.u
            public void onRewardRequest(TJPlacement tJPlacement2, TJActionRequest tJActionRequest, String str2, int i10) {
                k.n(tJPlacement2, new StringBuilder("onRewardRequest "), 4, SharedActivity.app.getPackageName());
            }
        });
        this.offerwallPlacement = tJPlacement;
        SharedActivity sharedActivity = app;
        tJPlacement.f4015d = sharedActivity;
        b0.f(sharedActivity);
        Log.d(app.getPackageName(), "requestOfferwallAndShow::Requesting placement content");
        this.offerwallPlacement.e();
    }

    public void requestPlacement(String str) {
        TJPlacement tJPlacement = new TJPlacement(getApplicationContext(), str, new u() { // from class: com.rtsoft.growtopia.SharedActivity.19
            public void onClick(TJPlacement tJPlacement2) {
                k.n(tJPlacement2, new StringBuilder("onClick "), 4, SharedActivity.app.getPackageName());
            }

            @Override // rc.u
            public void onContentDismiss(TJPlacement tJPlacement2) {
                k.n(tJPlacement2, new StringBuilder("onContentDismiss for placement "), 4, SharedActivity.app.getPackageName());
                b0.e(SharedActivity.app);
            }

            @Override // rc.u
            public void onContentReady(TJPlacement tJPlacement2) {
                Log.d(SharedActivity.app.getPackageName(), "onContentReady for placement " + tJPlacement2.b());
            }

            @Override // rc.u
            public void onContentShow(TJPlacement tJPlacement2) {
                k.n(tJPlacement2, new StringBuilder("onContentShow for placement "), 4, SharedActivity.app.getPackageName());
            }

            @Override // rc.u
            public void onPurchaseRequest(TJPlacement tJPlacement2, TJActionRequest tJActionRequest, String str2) {
                k.n(tJPlacement2, new StringBuilder("onPurchaseRequest "), 4, SharedActivity.app.getPackageName());
            }

            @Override // rc.u
            public void onRequestFailure(TJPlacement tJPlacement2, q qVar) {
                Log.d(SharedActivity.app.getPackageName(), "onRequestFailure for placement " + tJPlacement2.b() + " -- error: " + qVar.f14598a);
                SharedActivity.appOnAdInteractionFailed(tJPlacement2.b(), "TapJoy_Request_OnRequestFailure");
            }

            @Override // rc.u
            public void onRequestSuccess(TJPlacement tJPlacement2) {
                Log.d(SharedActivity.app.getPackageName(), "onRequestSuccess for placement " + tJPlacement2.b());
                if (tJPlacement2.c()) {
                    return;
                }
                Log.d(SharedActivity.app.getPackageName(), "No content available for placement " + tJPlacement2.b());
                SharedActivity.nativeSendGUIEx(25, 0, 0, 0);
                SharedActivity.appOnAdInteractionFailed(tJPlacement2.b(), "TapJoy_Request_NoContent");
            }

            @Override // rc.u
            public void onRewardRequest(TJPlacement tJPlacement2, TJActionRequest tJActionRequest, String str2, int i10) {
                k.n(tJPlacement2, new StringBuilder("onRewardRequest "), 4, SharedActivity.app.getPackageName());
            }
        });
        SharedActivity sharedActivity = app;
        tJPlacement.f4015d = sharedActivity;
        b0.f(sharedActivity);
        Log.d(app.getPackageName(), "requestPlacement::Requesting placement content");
        if (str.equals("Sub_01")) {
            this.tapjoyAdPlacementForSub01 = tJPlacement;
        } else if (str.equals("GROW_GGP_V4VC_TV")) {
            this.tapjoyAdPlacementForTV = tJPlacement;
        }
    }

    public void requestPlacementAndShow(String str) {
        TJPlacement tJPlacement = new TJPlacement(getApplicationContext(), str, new u() { // from class: com.rtsoft.growtopia.SharedActivity.20
            public void onClick(TJPlacement tJPlacement2) {
                k.n(tJPlacement2, new StringBuilder("onClick "), 4, SharedActivity.app.getPackageName());
            }

            @Override // rc.u
            public void onContentDismiss(TJPlacement tJPlacement2) {
                k.n(tJPlacement2, new StringBuilder("onContentDismiss for placement "), 4, SharedActivity.app.getPackageName());
                ProgressDialog progressDialog = SharedActivity.this.nDialog;
                if (progressDialog != null && progressDialog.isShowing()) {
                    SharedActivity.this.nDialog.dismiss();
                }
                b0.e(SharedActivity.app);
            }

            @Override // rc.u
            public void onContentReady(TJPlacement tJPlacement2) {
                Log.d(SharedActivity.app.getPackageName(), "onContentReady for placement " + tJPlacement2.b());
                ProgressDialog progressDialog = SharedActivity.this.nDialog;
                if (progressDialog != null && progressDialog.isShowing()) {
                    SharedActivity.this.nDialog.dismiss();
                }
                SharedActivity.nativeSendGUIEx(25, 1, 0, 0);
                if (tJPlacement2.d()) {
                    tJPlacement2.f();
                }
            }

            @Override // rc.u
            public void onContentShow(TJPlacement tJPlacement2) {
                k.n(tJPlacement2, new StringBuilder("onContentShow for placement "), 4, SharedActivity.app.getPackageName());
                ProgressDialog progressDialog = SharedActivity.this.nDialog;
                if (progressDialog == null || !progressDialog.isShowing()) {
                    return;
                }
                SharedActivity.this.nDialog.dismiss();
            }

            @Override // rc.u
            public void onPurchaseRequest(TJPlacement tJPlacement2, TJActionRequest tJActionRequest, String str2) {
                k.n(tJPlacement2, new StringBuilder("onPurchaseRequest "), 4, SharedActivity.app.getPackageName());
            }

            @Override // rc.u
            public void onRequestFailure(TJPlacement tJPlacement2, q qVar) {
                Log.d(SharedActivity.app.getPackageName(), "onRequestFailure for placement " + tJPlacement2.b() + " -- error: " + qVar.f14598a);
                ProgressDialog progressDialog = SharedActivity.this.nDialog;
                if (progressDialog != null && progressDialog.isShowing()) {
                    SharedActivity.this.nDialog.dismiss();
                }
                SharedActivity.nativeSendGUIEx(25, 0, 0, 0);
                SharedActivity.appOnAdInteractionFailed(tJPlacement2.b(), "TapJoy_Show_OnRequestFailure");
            }

            @Override // rc.u
            public void onRequestSuccess(TJPlacement tJPlacement2) {
                Log.d(SharedActivity.app.getPackageName(), "onRequestSuccess for placement " + tJPlacement2.b());
                if (tJPlacement2.c()) {
                    SharedActivity.this.runOnUiThread(new Runnable() { // from class: com.rtsoft.growtopia.SharedActivity.20.1
                        @Override // java.lang.Runnable
                        public void run() {
                            SharedActivity.this.nDialog = new ProgressDialog(SharedActivity.app);
                            SharedActivity.this.nDialog.setTitle("Loading");
                            SharedActivity.this.nDialog.setMessage("Wait while loading...");
                            SharedActivity.this.nDialog.setCancelable(true);
                            SharedActivity.this.nDialog.show();
                        }
                    });
                    return;
                }
                Log.d(SharedActivity.app.getPackageName(), "No content available for placement " + tJPlacement2.b());
                SharedActivity.nativeSendGUIEx(25, 0, 0, 0);
                SharedActivity.app.makeToastUI("No video content is available for your device.");
                SharedActivity.appOnAdInteractionFailed(tJPlacement2.b(), "TapJoy_Show_NoContent");
            }

            @Override // rc.u
            public void onRewardRequest(TJPlacement tJPlacement2, TJActionRequest tJActionRequest, String str2, int i10) {
                k.n(tJPlacement2, new StringBuilder("onRewardRequest "), 4, SharedActivity.app.getPackageName());
            }
        });
        SharedActivity sharedActivity = app;
        tJPlacement.f4015d = sharedActivity;
        b0.f(sharedActivity);
        Log.d(app.getPackageName(), "requestPlacementAndShow::Requesting placement content");
        tJPlacement.e();
        if (str.equals("Sub_01")) {
            this.tapjoyAdPlacementForSub01 = tJPlacement;
        } else if (str.equals("GROW_GGP_V4VC_TV")) {
            this.tapjoyAdPlacementForTV = tJPlacement;
        }
    }

    public void sendVersionDetails() {
        try {
            nativeSendGUIStringEx(47, 0, 0, 0, getPackageManager().getPackageInfo(BuildConfig.APPLICATION_ID, 0).versionName);
        } catch (PackageManager.NameNotFoundException e10) {
            e10.printStackTrace();
            Log.d(PackageName, "Cannot load App Version!");
        }
    }

    public void setup_accel(float f8) {
        accelHzSave = f8;
        SensorManager sensorManager2 = (SensorManager) app.getSystemService("sensor");
        sensorManager = sensorManager2;
        sensorManager2.unregisterListener(this);
        if (f8 > 0.0f) {
            SensorManager sensorManager3 = sensorManager;
            sensorManager3.registerListener(app, sensorManager3.getDefaultSensor(1), 1);
        }
    }

    public void toggle_keyboard(final boolean z10) {
        if (z10) {
            Main.OriginalKeyboard = true;
        }
        runOnUiThread(new Runnable() { // from class: com.rtsoft.growtopia.SharedActivity.21
            @Override // java.lang.Runnable
            public void run() {
                final InputMethodManager inputMethodManager = (InputMethodManager) SharedActivity.app.getSystemService("input_method");
                if (!z10) {
                    Log.d("Msg", "Disabling keyboard");
                    inputMethodManager.hideSoftInputFromWindow(SharedActivity.m_editText.getWindowToken(), 0);
                    SharedActivity.this.UpdateEditBoxInView(false, false);
                    SharedActivity.m_focusOnKeyboard = false;
                    return;
                }
                Log.d("Msg", "Enabling keyboard");
                SharedActivity.this.clearIngameInputBox();
                SharedActivity.this.UpdateEditBoxInView(true, false);
                SharedActivity.m_editText.post(new Runnable() { // from class: com.rtsoft.growtopia.SharedActivity.21.1
                    @Override // java.lang.Runnable
                    public void run() {
                        if (inputMethodManager.showSoftInput(SharedActivity.m_editText, 1)) {
                            return;
                        }
                        inputMethodManager.toggleSoftInput(2, 0);
                    }
                });
                SharedActivity.m_focusOnKeyboard = true;
            }
        });
    }

    public void videoComplete() {
        Log.i("growtopia", "VIDEO COMPLETE");
        nativeSendGUIStringEx(27, 2, 0, 0, "");
    }

    public void videoReady() {
        Log.i("growtopia", "VIDEO READY");
        nativeSendGUIStringEx(27, 1, 0, 0, "");
    }

    public void videoStart() {
        Log.i("growtopia", "VIDEO START");
    }

    public void makeToastUI(String str) {
        runOnUiThread(new Runnable(str) { // from class: com.rtsoft.growtopia.SharedActivity.11
            String msg;
            final /* synthetic */ String val$str;

            {
                this.val$str = str;
                this.msg = str;
            }

            @Override // java.lang.Runnable
            public void run() {
                Toast toastMakeText = Toast.makeText(SharedActivity.app.getApplicationContext(), this.msg, 1);
                toastMakeText.setGravity(17, 0, 0);
                toastMakeText.show();
            }
        });
    }

    private void license_init() {
    }

    @Override // android.hardware.SensorEventListener
    public void onAccuracyChanged(Sensor sensor2, int i10) {
    }

    public void onApplsFlyerLogEvent(String str, String str2) {
    }

    public void onApplsFlyerLogPurchase(String str, String str2, String str3) {
    }
}
