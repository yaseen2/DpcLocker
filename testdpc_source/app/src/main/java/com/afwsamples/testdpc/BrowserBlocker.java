package com.afwsamples.testdpc;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.UserHandle;
import android.util.Log;

import java.util.List;

public class BrowserBlocker {
    private static final String TAG = "BrowserBlocker";
    private static final String PREFS_NAME = "browser_blocker_prefs";
    private static final String KEY_AUTO_BLOCK = "auto_block_browsers";
    private static boolean isCallbackRegistered = false;

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isAutoBlockEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_AUTO_BLOCK, true);
    }

    public static void setAutoBlockEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_BLOCK, enabled).apply();
        Log.i(TAG, "setAutoBlockEnabled set to: " + enabled);
        if (enabled) {
            scanAndSuspendAllBrowsers(context);
        } else {
            unSuspendAllBrowsers(context);
        }
    }

    public static synchronized void initLauncherAppsCallback(final Context context) {
        if (isCallbackRegistered) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                LauncherApps launcherApps = (LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
                if (launcherApps != null) {
                    launcherApps.registerCallback(new LauncherApps.Callback() {
                        @Override
                        public void onPackageAdded(String packageName, UserHandle user) {
                            Log.i(TAG, "LauncherApps onPackageAdded: " + packageName);
                            checkAndSuspendPackage(context, packageName);
                        }

                        @Override
                        public void onPackageChanged(String packageName, UserHandle user) {
                            Log.i(TAG, "LauncherApps onPackageChanged: " + packageName);
                            checkAndSuspendPackage(context, packageName);
                        }

                        @Override
                        public void onPackageRemoved(String packageName, UserHandle user) {}

                        @Override
                        public void onPackagesAvailable(String[] packageNames, UserHandle user, boolean replacing) {
                            if (packageNames != null) {
                                for (String pkg : packageNames) {
                                    checkAndSuspendPackage(context, pkg);
                                }
                            }
                        }

                        @Override
                        public void onPackagesUnavailable(String[] packageNames, UserHandle user, boolean replacing) {}
                    });
                    isCallbackRegistered = true;
                    Log.i(TAG, "Registered LauncherApps callback successfully!");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to register LauncherApps callback", e);
        }
    }

    public static boolean isNonChromeBrowser(Context context, String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return false;
        }

        // Whitelist core system / essential applications
        if ("com.android.chrome".equals(packageName) ||
            "com.google.android.googlequicksearchbox".equals(packageName) ||
            "com.android.vending".equals(packageName) ||
            "com.custom.dpclocker".equals(packageName) ||
            "com.afwsamples.testdpc".equals(packageName) ||
            packageName.startsWith("com.google.android.") ||
            packageName.startsWith("com.android.")) {
            return false;
        }

        PackageManager pm = context.getPackageManager();

        // Must handle generic http/https web view intent with launcher intent
        boolean handlesWebIntent = false;
        try {
            Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"));
            webIntent.addCategory(Intent.CATEGORY_BROWSABLE);

            List<ResolveInfo> resolveInfos = pm.queryIntentActivities(webIntent, PackageManager.MATCH_ALL);
            if (resolveInfos != null) {
                for (ResolveInfo info : resolveInfos) {
                    if (info.activityInfo != null && packageName.equals(info.activityInfo.packageName)) {
                        Intent launchIntent = pm.getLaunchIntentForPackage(packageName);
                        if (launchIntent != null) {
                            handlesWebIntent = true;
                            Log.i(TAG, "Package " + packageName + " confirmed as generic Web Browser intent handler");
                            break;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }

        return handlesWebIntent;
    }

    public static void checkAndSuspendPackage(Context context, String packageName) {
        if (!isAutoBlockEnabled(context)) {
            return;
        }
        if (isNonChromeBrowser(context, packageName)) {
            try {
                DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
                if (dpm != null && dpm.isDeviceOwnerApp(context.getPackageName())) {
                    dpm.setPackagesSuspended(DeviceAdminReceiver.getComponentName(context), new String[]{packageName}, true);
                    Log.i(TAG, "SUCCESSFULLY DYNAMICALLY AUTO-SUSPENDED NON-CHROME BROWSER: " + packageName);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error suspending package " + packageName, e);
            }
        }
    }

    public static void scanAndSuspendAllBrowsers(Context context) {
        if (!isAutoBlockEnabled(context)) {
            return;
        }
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            if (dpm == null || !dpm.isDeviceOwnerApp(context.getPackageName())) {
                return;
            }

            PackageManager pm = context.getPackageManager();
            List<ApplicationInfo> apps = pm.getInstalledApplications(0);
            for (ApplicationInfo app : apps) {
                if (isNonChromeBrowser(context, app.packageName)) {
                    dpm.setPackagesSuspended(DeviceAdminReceiver.getComponentName(context), new String[]{app.packageName}, true);
                    Log.i(TAG, "SUCCESSFULLY DYNAMICALLY AUTO-SUSPENDED NON-CHROME BROWSER ON SCAN: " + app.packageName);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in scanAndSuspendAllBrowsers", e);
        }
    }

    public static void unSuspendAllBrowsers(Context context) {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            if (dpm == null || !dpm.isDeviceOwnerApp(context.getPackageName())) {
                return;
            }

            PackageManager pm = context.getPackageManager();
            List<ApplicationInfo> apps = pm.getInstalledApplications(0);
            for (ApplicationInfo app : apps) {
                if (isNonChromeBrowser(context, app.packageName)) {
                    dpm.setPackagesSuspended(DeviceAdminReceiver.getComponentName(context), new String[]{app.packageName}, false);
                    Log.i(TAG, "UNSUSPENDED BROWSER: " + app.packageName);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in unSuspendAllBrowsers", e);
        }
    }
}
