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
                            AppTimerManager.checkAndEnforceLimits(context);
                            NotoriousAppBlocker.checkAndSuspendNotoriousPackage(context, packageName);
                            AiAppAuditor.checkAndAuditPackage(context, packageName);
                        }

                        @Override
                        public void onPackageChanged(String packageName, UserHandle user) {
                            Log.i(TAG, "LauncherApps onPackageChanged: " + packageName);
                            checkAndSuspendPackage(context, packageName);
                            AiAppAuditor.checkAndAuditPackage(context, packageName);
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

    // Comprehensive list of known dedicated third-party web browsers
    private static final java.util.Set<String> KNOWN_WEB_BROWSERS = new java.util.HashSet<>(java.util.Arrays.asList(
            "org.mozilla.firefox",
            "org.mozilla.firefox_beta",
            "org.mozilla.focus",
            "com.opera.browser",
            "com.opera.mini.native",
            "com.opera.gx",
            "com.opera.touch",
            "com.brave.browser",
            "com.duckduckgo.mobile.android",
            "com.microsoft.emmx",
            "com.sec.android.app.sbrowser",
            "com.sec.android.app.sbrowser.beta",
            "com.kiwibrowser.browser",
            "com.ucmobile.intl",
            "com.UCMobile",
            "mobi.mgeek.TunnyBrowser",
            "com.cloudmosa.puffinFree",
            "com.cloudmosa.puffin",
            "com.vivaldi.browser",
            "com.transsion.phoenix",
            "com.aloha.browser",
            "com.mx.browser",
            "org.torproject.torbrowser",
            "acr.browser.barebones",
            "acr.browser.lightning",
            "com.pure.mini.browser",
            "com.xbrowser.play",
            "com.ecosia.android",
            "com.qwant.liberty",
            "mark.via.gp",
            "com.apusapps.browser"
    ));

    public static boolean isNonChromeBrowser(Context context, String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return false;
        }

        String lowerPkg = packageName.toLowerCase(java.util.Locale.US);

        // 1. Whitelist core system, Google, and essential productivity/utility/ride-hailing/banking applications
        if ("com.android.chrome".equals(packageName) ||
            "com.google.android.googlequicksearchbox".equals(packageName) ||
            "com.android.vending".equals(packageName) ||
            "com.custom.dpclocker".equals(packageName) ||
            "com.afwsamples.testdpc".equals(packageName) ||
            lowerPkg.startsWith("com.google.android.") ||
            lowerPkg.startsWith("com.android.") ||
            lowerPkg.contains("yango") ||
            lowerPkg.contains("yandex") ||
            lowerPkg.contains("careem") ||
            lowerPkg.contains("uber") ||
            lowerPkg.contains("bykea") ||
            lowerPkg.contains("daraz") ||
            lowerPkg.contains("olx") ||
            lowerPkg.contains("pakwheels") ||
            lowerPkg.contains("zameen") ||
            lowerPkg.contains("whatsapp") ||
            lowerPkg.contains("banking") ||
            lowerPkg.contains("hbl") ||
            lowerPkg.contains("meezan") ||
            lowerPkg.contains("nayapay") ||
            lowerPkg.contains("sadapay") ||
            lowerPkg.contains("jazz") ||
            lowerPkg.contains("telenor")) {
            return false;
        }

        // 2. Direct match against known web browsers
        if (KNOWN_WEB_BROWSERS.contains(packageName)) {
            Log.i(TAG, "Package " + packageName + " matched KNOWN WEB BROWSER list.");
            return true;
        }

        // 3. Check for dedicated CATEGORY_APP_BROWSER intent filter
        PackageManager pm = context.getPackageManager();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
                Intent browserCategoryIntent = new Intent(Intent.ACTION_MAIN);
                browserCategoryIntent.addCategory(Intent.CATEGORY_APP_BROWSER);
                List<ResolveInfo> browserApps = pm.queryIntentActivities(browserCategoryIntent, PackageManager.MATCH_ALL);
                if (browserApps != null) {
                    for (ResolveInfo info : browserApps) {
                        if (info.activityInfo != null && packageName.equals(info.activityInfo.packageName)) {
                            Log.i(TAG, "Package " + packageName + " confirmed as CATEGORY_APP_BROWSER");
                            return true;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }

        return false;
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
