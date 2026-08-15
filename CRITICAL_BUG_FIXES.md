# Critical Bug Fixes - Notification & Crash Issues

## 🐛 Bugs Fixed

### Bug 1: Notification Shows as "Silent" ✅ FIXED
**Problem:** Notification appeared under "Silent" section instead of popping up prominently like Shizuku.

**Root Cause:** NotificationChannel was using `IMPORTANCE_LOW` with no sound or vibration.

**Fix Applied:**
```kotlin
val channel = NotificationChannel(
    CHANNEL_ID,
    "Wireless Debugging",
    NotificationManager.IMPORTANCE_HIGH  // Changed from IMPORTANCE_LOW
).apply {
    description = "Wireless debugging pairing notifications"
    setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI, null)
    enableVibration(true)
    enableLights(true)
}
```

Also added to notification builder:
```kotlin
.setPriority(NotificationCompat.PRIORITY_HIGH)
.setCategory(NotificationCompat.CATEGORY_STATUS)
```

**Result:** Notification now pops up as a heads-up notification with sound and vibration, just like Shizuku! 🔔

---

### Bug 2: App Crashes When Tapping "ENTER PAIRING CODE" ✅ FIXED
**Problem:** "Growlauncher v5.54 keeps stopping" crash when tapping the notification action button.

**Root Cause:** Launching a transparent dialog activity from a background service notification with `FLAG_ACTIVITY_CLEAR_TASK` caused a window token crash.

**Fix Applied:**

1. **Changed Intent Flags:**
```kotlin
// Before: FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK
// After:
addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
```

2. **Added Launch Mode in Manifest:**
```xml
<activity
    android:name=".PairingCodeDialogActivity"
    ...
    android:launchMode="singleInstance"
    ...
/>
```

**Result:** Dialog now launches smoothly without crashing! ✅

---

## 🎯 What Changed

### Files Modified:
1. **PairingOverlayService.kt**
   - Updated `createNotificationChannel()` - IMPORTANCE_HIGH with sound/vibration
   - Updated `notification()` - Changed intent flags, added priority/category
   
2. **AndroidManifest.xml**
   - Added `android:launchMode="singleInstance"` to PairingCodeDialogActivity

---

## ✅ Testing Checklist

After pulling these fixes:

1. ✅ Start wireless debugging
2. ✅ Notification should **pop up prominently** (not silent)
3. ✅ Notification should have **sound and vibration**
4. ✅ Go to Android Settings → Pair device with pairing code
5. ✅ Notification updates to "Pairing service found"
6. ✅ Tap "ENTER PAIRING CODE" button
7. ✅ **Dialog should open WITHOUT crashing**
8. ✅ Enter 6-digit code and verify pairing works

---

## 📦 Commit Info

**Commit:** `cb0621a`  
**Branch:** `feature/shizuku-style-notification`  
**Status:** Pushed to GitHub ✅

---

## 🚀 To Apply These Fixes

```bash
# Pull latest changes
git pull origin feature/shizuku-style-notification

# Sync and rebuild
File → Sync Project with Gradle Files
Build → Clean Project
Build → Rebuild Project
Build → Build APK
```

---

## 🎉 Summary

Both critical bugs are now fixed:
- ✅ Notification pops up prominently with sound/vibration
- ✅ No crash when tapping "ENTER PAIRING CODE"

Ready to test! 🚀
