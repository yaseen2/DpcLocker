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

/**
 * =========================================================================================
 * CLASS: BrowserBlocker
 * =========================================================================================
 * Purpose:
 *   Dynamically identifies, monitors, and suspends alternative third-party web browsers
 *   (e.g., Firefox, Opera, Brave, Edge, Tor, UC Browser, DuckDuckGo).
 *
 * Why Isolate to Google Chrome?
 *   Only Google Chrome natively supports Android Enterprise Device Owner managed application
 *   configuration (AppConfig) via `DevicePolicyManager.setApplicationRestrictions()`.
 *   Through these restrictions (configured in `ChromePolicyManager`), Chrome can enforce
 *   SafeSearch, URL blacklists, SafeSites content filtering, and disable Incognito mode.
 *   Third-party browsers do NOT respect these enterprise policies and would serve as an
 *   instant loophole for accessing adult content.
 *
 * Zero Hardcoding Principle:
 *   Instead of keeping an endless, fragile list of package names, BrowserBlocker utilizes
 *   pure dynamic Android Intent resolution against the Android PackageManager:
 *     1. CATEGORY_APP_BROWSER intent filter check.
 *     2. Dynamic open-ended HTTPS URL resolution check.
 *     3. Dynamic open-ended HTTP URL resolution check.
 * =========================================================================================
 */
public class BrowserBlocker {
    // Logcat filter identifier
    private static final String TAG = "BrowserBlocker";

    // SharedPreferences file name for browser blocker preferences
    private static final String PREFS_NAME = "browser_blocker_prefs";

    // Key storing whether auto-blocking of alternative browsers is enabled
    private static final String KEY_AUTO_BLOCK = "auto_block_browsers";

    // Guard flag to prevent registering redundant LauncherApps callbacks
    private static boolean isCallbackRegistered = false;

    /**
     * Helper to retrieve SharedPreferences for browser blocker settings.
     */
    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Checks if auto-blocking of non-Chrome browsers is currently enabled (default: true).
     */
    public static boolean isAutoBlockEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_AUTO_BLOCK, true);
    }

    /**
     * Toggles browser auto-blocking on or off.
     * When toggled ON: triggers a full boot/security scan to suspend any installed browsers.
     * When toggled OFF: immediately unsuspends all third-party browsers across the system.
     */
    public static void setAutoBlockEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_BLOCK, enabled).apply();
        Log.i(TAG, "setAutoBlockEnabled set to: " + enabled);
        if (enabled) {
            // Trigger full security pipeline boot scan to locate and suspend non-Chrome browsers
            SecurityPipelineManager.onBootCompleted(context);
        } else {
            // Un-suspend all browsers so user can use alternative browsers again
            unSuspendAllBrowsers(context);
        }
    }

    /**
     * Registers a system LauncherApps callback to listen for package additions and removals.
     * Available on Android 5.0 (API 21) and above.
     */
    public static synchronized void initLauncherAppsCallback(final Context context) {
        if (isCallbackRegistered) return; // Prevent duplicate observer registration
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                LauncherApps launcherApps = (LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
                if (launcherApps != null) {
                    launcherApps.registerCallback(new LauncherApps.Callback() {
                        /**
                         * Fired when a new package is installed on the user profile.
                         */
                        @Override
                        public void onPackageAdded(String packageName, UserHandle user) {
                            Log.i(TAG, "LauncherApps onPackageAdded: " + packageName);
                            // Send package into the unified pipeline for static audit and browser verification
                            SecurityPipelineManager.onPackageAddedOrUpdated(context, packageName);
                            // Re-evaluate app timer restrictions
                            AppTimerManager.checkAndEnforceLimits(context);
                        }

                        /**
                         * Fired when an existing package is updated or replaced.
                         */
                        @Override
                        public void onPackageChanged(String packageName, UserHandle user) {
                            Log.i(TAG, "LauncherApps onPackageChanged: " + packageName);
                            SecurityPipelineManager.onPackageAddedOrUpdated(context, packageName);
                        }

                        /**
                         * Fired when a package is uninstalled by the user or system.
                         */
                        @Override
                        public void onPackageRemoved(String packageName, UserHandle user) {
                            // Purge any pending background AI audit work
                            AiAppAuditor.removePendingAudit(context, packageName);
                        }

                        /**
                         * Fired when packages become available (e.g., SD card mounted or work profile unlocked).
                         */
                        @Override
                        public void onPackagesAvailable(String[] packageNames, UserHandle user, boolean replacing) {
                            if (packageNames != null) {
                                for (String pkg : packageNames) {
                                    SecurityPipelineManager.onPackageAddedOrUpdated(context, pkg);
                                }
                            }
                        }

                        /**
                         * Fired when packages become unavailable (e.g., storage unmounted).
                         */
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
     * =====================================================================================
     * DYNAMIC BROWSER RESOLUTION ALGORITHM
     * =====================================================================================
     * Evaluates whether an installed package is a standalone third-party web browser without
     * relying on hardcoded lists.
     *
     * Pipeline Steps:
     *   1. Whitelist Verification: If package is whitelisted (or Chrome/System), return false.
     *   2. Launcher Presence: Must possess a main launchable Activity (filters out background services).
     *   3. Intent Category Check: Matches Intent.CATEGORY_APP_BROWSER.
     *   4. Open HTTPS Intent Resolution: Tests if the package responds to an arbitrary HTTPS URI.
     *   5. Open HTTP Intent Resolution: Tests if the package responds to an arbitrary HTTP URI.
     *
     * @param context     Application context
     * @param packageName Target package to test
     * @return True if the package is an alternative, unmanageable web browser.
     */
    public static boolean isNonChromeBrowser(Context context, String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return false;
        }

        // Step 1: Whitelist check (Core system apps, Chrome, or User Whitelisted apps are immune)
        if (SecurityConfig.isWhitelisted(context, packageName)) {
            return false;
        }

        PackageManager pm = context.getPackageManager();

        try {
            // Step 2: Ensure package has a launchable app icon (standalone application)
            Intent launchIntent = pm.getLaunchIntentForPackage(packageName);
            if (launchIntent == null) {
                return false; // Background plugin or helper app, not an interactive browser
            }

            // Step 3: Dynamic Check 1 - Android Standard Browser Application Category
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
                Intent mainBrowserIntent = new Intent(Intent.ACTION_MAIN);
                mainBrowserIntent.addCategory(Intent.CATEGORY_APP_BROWSER);
                mainBrowserIntent.setPackage(packageName); // Scope strictly to target package
                List<ResolveInfo> browserApps = pm.queryIntentActivities(mainBrowserIntent, 0);
                if (browserApps != null && !browserApps.isEmpty()) {
                    Log.i(TAG, "Package " + packageName + " confirmed as Web Browser via CATEGORY_APP_BROWSER");
                    return true;
                }
            }

            // Step 4: Dynamic Check 2 - Open HTTPS URL Resolution
            // Constructs an arbitrary domain to verify if the app registers as a generic web viewer
            String randomHttpsHost = "https://arbitrary-open-web-check-" + System.currentTimeMillis() + ".org";
            Intent httpsIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(randomHttpsHost));
            httpsIntent.addCategory(Intent.CATEGORY_BROWSABLE);
            httpsIntent.setPackage(packageName);

            List<ResolveInfo> httpsResolvers = pm.queryIntentActivities(httpsIntent, 0);
            if (httpsResolvers != null && !httpsResolvers.isEmpty()) {
                Log.i(TAG, "Package " + packageName + " confirmed as open-ended HTTPS Web Browser");
                return true;
            }

            // Step 5: Dynamic Check 3 - Open HTTP URL Resolution
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

    /**
     * Convenience delegation method to dispatch package evaluation to SecurityPipelineManager.
     */
    public static void checkAndSuspendPackage(Context context, String packageName) {
        SecurityPipelineManager.onPackageAddedOrUpdated(context, packageName);
    }

    /**
     * Scans all installed packages on the device and suspends non-Chrome browsers.
     */
    public static void scanAndSuspendAllBrowsers(Context context) {
        SecurityPipelineManager.onBootCompleted(context);
    }

    /**
     * Unsuspends all third-party browsers when auto-block protection is disabled.
     */
    public static void unSuspendAllBrowsers(Context context) {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            if (dpm == null || !dpm.isDeviceOwnerApp(context.getPackageName())) {
                return;
            }

            PackageManager pm = context.getPackageManager();
            List<ApplicationInfo> apps = pm.getInstalledApplications(0);
            for (ApplicationInfo app : apps) {
                // If it is an alternative browser, lift the DPM suspension
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

