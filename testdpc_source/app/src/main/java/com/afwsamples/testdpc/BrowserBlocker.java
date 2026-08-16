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
            SecurityPipelineManager.onBootCompleted(context);
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
                            SecurityPipelineManager.onPackageAddedOrUpdated(context, packageName);
                            AppTimerManager.checkAndEnforceLimits(context);
                        }

                        @Override
                        public void onPackageChanged(String packageName, UserHandle user) {
                            Log.i(TAG, "LauncherApps onPackageChanged: " + packageName);
                            SecurityPipelineManager.onPackageAddedOrUpdated(context, packageName);
                        }

                        @Override
                        public void onPackageRemoved(String packageName, UserHandle user) {
                            AiAppAuditor.removePendingAudit(context, packageName);
                        }

                        @Override
                        public void onPackagesAvailable(String[] packageNames, UserHandle user, boolean replacing) {
                            if (packageNames != null) {
                                for (String pkg : packageNames) {
                                    SecurityPipelineManager.onPackageAddedOrUpdated(context, pkg);
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

    /**
     * Pure Dynamic Android Intent Resolution to identify standalone web browsers.
     * Uses zero hardcoded package names. Evaluates:
     * 1. Category APP_BROWSER
     * 2. Package-scoped arbitrary HTTPS URI resolution
     * 3. Package-scoped arbitrary HTTP URI resolution
     */
    public static boolean isNonChromeBrowser(Context context, String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return false;
        }

        // 1. Whitelist check (System, Chrome, or User Whitelisted)
        if (SecurityConfig.isWhitelisted(context, packageName)) {
            return false;
        }

        PackageManager pm = context.getPackageManager();

        try {
            // Must have a launchable application icon (standalone app)
            Intent launchIntent = pm.getLaunchIntentForPackage(packageName);
            if (launchIntent == null) {
                return false;
            }

            // Dynamic Check 1: Dedicated Browser Application Category
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
                Intent mainBrowserIntent = new Intent(Intent.ACTION_MAIN);
                mainBrowserIntent.addCategory(Intent.CATEGORY_APP_BROWSER);
                mainBrowserIntent.setPackage(packageName);
                List<ResolveInfo> browserApps = pm.queryIntentActivities(mainBrowserIntent, 0);
                if (browserApps != null && !browserApps.isEmpty()) {
                    Log.i(TAG, "Package " + packageName + " confirmed as Web Browser via CATEGORY_APP_BROWSER");
                    return true;
                }
            }

            // Dynamic Check 2: Targeted Arbitrary HTTPS URI Resolution (No domain authority restrictions)
            String randomHttpsHost = "https://arbitrary-open-web-check-" + System.currentTimeMillis() + ".org";
            Intent httpsIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(randomHttpsHost));
            httpsIntent.addCategory(Intent.CATEGORY_BROWSABLE);
            httpsIntent.setPackage(packageName);

            List<ResolveInfo> httpsResolvers = pm.queryIntentActivities(httpsIntent, 0);
            if (httpsResolvers != null && !httpsResolvers.isEmpty()) {
                Log.i(TAG, "Package " + packageName + " confirmed as open-ended HTTPS Web Browser");
                return true;
            }

            // Dynamic Check 3: Targeted Arbitrary HTTP URI Resolution
            String randomHttpHost = "http://arbitrary-open-web-check-" + System.currentTimeMillis() + ".org";
            Intent httpIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(randomHttpHost));
            httpIntent.addCategory(Intent.CATEGORY_BROWSABLE);
            httpIntent.setPackage(packageName);

            List<ResolveInfo> httpResolvers = pm.queryIntentActivities(httpIntent, 0);
            if (httpResolvers != null && !httpResolvers.isEmpty()) {
                Log.i(TAG, "Package " + packageName + " confirmed as open-ended HTTP Web Browser");
                return true;
            }

        } catch (Exception e) {
            Log.e(TAG, "Error evaluating isNonChromeBrowser for " + packageName, e);
        }

        return false;
    }

    public static void checkAndSuspendPackage(Context context, String packageName) {
        SecurityPipelineManager.onPackageAddedOrUpdated(context, packageName);
    }

    public static void scanAndSuspendAllBrowsers(Context context) {
        SecurityPipelineManager.onBootCompleted(context);
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
