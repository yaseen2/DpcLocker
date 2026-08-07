package com.afwsamples.testdpc;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class NotoriousAppBlocker {
    private static final String TAG = "NotoriousAppBlocker";
    private static final String PREF_NAME = "dpclocker_notorious_blocker";
    private static final String KEY_BLOCKED_PACKAGES = "blocked_packages";

    // Default notorious packages included out-of-the-box
    private static final String[] DEFAULT_NOTORIOUS_PACKAGES = new String[]{
            "com.twitter.android",       // X / Twitter
            "com.twitter.android.lite",  // X Lite
            "com.reddit.frontpage",      // Reddit
            "com.tumblr",                // Tumblr
            "org.telegram.messenger",    // Telegram
            "org.telegram.messenger.web",// Telegram Web
            "org.telegram.plus"          // Telegram Plus
    };

    public static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static Set<String> getBlockedPackages(Context context) {
        SharedPreferences prefs = getPrefs(context);
        if (!prefs.contains(KEY_BLOCKED_PACKAGES)) {
            // First time initialization: store default notorious package list
            Set<String> defaults = new HashSet<>(Arrays.asList(DEFAULT_NOTORIOUS_PACKAGES));
            prefs.edit().putStringSet(KEY_BLOCKED_PACKAGES, defaults).apply();
            return defaults;
        }
        return prefs.getStringSet(KEY_BLOCKED_PACKAGES, new HashSet<String>());
    }

    public static void addPackageToBlocklist(Context context, String packageName) {
        if (packageName == null || packageName.trim().isEmpty()) return;
        Set<String> set = new HashSet<>(getBlockedPackages(context));
        set.add(packageName.trim().toLowerCase());
        getPrefs(context).edit().putStringSet(KEY_BLOCKED_PACKAGES, set).apply();
        scanAndSuspendAllNotoriousApps(context);
    }

    public static void removePackageFromBlocklist(Context context, String packageName) {
        if (packageName == null) return;
        Set<String> set = new HashSet<>(getBlockedPackages(context));
        set.remove(packageName.trim().toLowerCase());
        getPrefs(context).edit().putStringSet(KEY_BLOCKED_PACKAGES, set).apply();

        // Unsuspend if removed from blocklist
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            if (dpm != null && dpm.isDeviceOwnerApp(context.getPackageName())) {
                dpm.setPackagesSuspended(DeviceAdminReceiver.getComponentName(context), new String[]{packageName}, false);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error unsuspending package " + packageName, e);
        }
    }

    public static boolean isPackageBlocked(Context context, String packageName) {
        if (packageName == null) return false;
        Set<String> blocked = getBlockedPackages(context);
        return blocked.contains(packageName.toLowerCase());
    }

    public static void checkAndSuspendNotoriousPackage(Context context, String packageName) {
        if (packageName == null) return;
        if (isPackageBlocked(context, packageName)) {
            try {
                DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
                if (dpm != null && dpm.isDeviceOwnerApp(context.getPackageName())) {
                    dpm.setPackagesSuspended(DeviceAdminReceiver.getComponentName(context), new String[]{packageName}, true);
                    dpm.setUninstallBlocked(DeviceAdminReceiver.getComponentName(context), packageName, false);
                    Log.i(TAG, "AUTO-BLOCKED NOTORIOUS APP INSTALLED: " + packageName);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error suspending notorious package " + packageName, e);
            }
        }
    }

    public static void scanAndSuspendAllNotoriousApps(Context context) {
        Set<String> blockedPackages = getBlockedPackages(context);
        for (String pkg : blockedPackages) {
            checkAndSuspendNotoriousPackage(context, pkg);
        }
    }
}
