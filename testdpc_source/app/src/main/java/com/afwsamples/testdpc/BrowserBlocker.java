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
import java.util.Locale;

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
        if (isCallbackRegistered || context == null) return; // Prevent duplicate observer registration
        final Context appContext = context.getApplicationContext();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                LauncherApps launcherApps = (LauncherApps) appContext.getSystemService(Context.LAUNCHER_APPS_SERVICE);
                if (launcherApps != null) {
                    launcherApps.registerCallback(new LauncherApps.Callback() {
                        /**
                         * Fired when a new package is installed on the user profile.
                         */
                        @Override
                        public void onPackageAdded(String packageName, UserHandle user) {
                            Log.i(TAG, "LauncherApps onPackageAdded: " + packageName);
                            // Send package into the unified pipeline for static audit and browser verification
                            SecurityPipelineManager.onPackageAddedOrUpdated(appContext, packageName, false);
                            // Re-evaluate app timer restrictions
                            AppTimerManager.checkAndEnforceLimits(appContext);
                        }

                        /**
                         * Fired when an existing package is updated or replaced.
                         */
                        @Override
                        public void onPackageChanged(String packageName, UserHandle user) {
                            Log.i(TAG, "LauncherApps onPackageChanged: " + packageName);
                            SecurityPipelineManager.onPackageAddedOrUpdated(appContext, packageName, true);
                        }

                        /**
                         * Fired when a package is uninstalled by the user or system.
                         */
                        @Override
                        public void onPackageRemoved(String packageName, UserHandle user) {
                            // Purge any safe cache entry and pending background AI audit work
                            SecurityPipelineManager.evictFromSafeCache(appContext, packageName);
                            AiAppAuditor.removePendingAudit(appContext, packageName);
                        }

                        /**
                         * Fired when packages become available (e.g., SD card mounted or work profile unlocked).
                         */
                        @Override
                        public void onPackagesAvailable(String[] packageNames, UserHandle user, boolean replacing) {
                            if (packageNames != null) {
                                for (String pkg : packageNames) {
                                    SecurityPipelineManager.onPackageAddedOrUpdated(appContext, pkg, replacing);
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

        // Step 1: Whitelist, managed Chrome WebAPK, protected system app, or user-timed app exemptions
        if (SecurityConfig.isWhitelisted(context, packageName) ||
                SecurityConfig.isManagedChromeWebApk(context, packageName) ||
                SecurityConfig.isProtectedSystemApp(context, packageName) ||
                (AppTimerManager.getAppLimitMinutes(context, packageName) > 0 && !SecurityConfig.isBlocklisted(context, packageName))) {
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

            // Step 6: Dynamic Check 4 - Generic HTTPS ACTION_VIEW (without requiring CATEGORY_BROWSABLE)
            // Catches browsers that omit CATEGORY_BROWSABLE to avoid default handler dialogs (e.g. Orbit Browser)
            Intent genericViewIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"));
            genericViewIntent.setPackage(packageName);
            List<ResolveInfo> genericResolvers = pm.queryIntentActivities(genericViewIntent, 0);
            if (genericResolvers != null && !genericResolvers.isEmpty()) {
                Log.i(TAG, "Package " + packageName + " confirmed as Web Browser via generic ACTION_VIEW");
                return true;
            }

            // Step 7: Dynamic Check 5 - Standard Web Search Intent Filter
            Intent webSearchIntent = new Intent(Intent.ACTION_WEB_SEARCH);
            webSearchIntent.setPackage(packageName);
            List<ResolveInfo> searchResolvers = pm.queryIntentActivities(webSearchIntent, 0);
            if (searchResolvers != null && !searchResolvers.isEmpty()) {
                Log.i(TAG, "Package " + packageName + " confirmed as Web Browser via ACTION_WEB_SEARCH");
                return true;
            }

            // Step 8: Dynamic Check 6 - Targeted Web Browser Signature Check
            // Inspect package name and application label for browser identifiers, verifying INTERNET permission
            String lowerPkg = packageName.toLowerCase(Locale.US);

            // Direct recognition of unmanaged browser conduits (Google Go, AOSP Browser, stealth/privacy browsers)
            if ("com.google.android.apps.searchlite".equals(lowerPkg) ||
                    "com.android.browser".equals(lowerPkg) ||
                    "mark.via.gp".equals(lowerPkg) ||
                    "mark.via".equals(lowerPkg) ||
                    "org.cromite.cromite".equals(lowerPkg) ||
                    "us.spotco.fennec_dos".equals(lowerPkg) ||
                    "org.torproject.torbrowser".equals(lowerPkg) ||
                    "com.duckduckgo.mobile.android".equals(lowerPkg) ||
                    "com.cloudmosa.puffinfree".equals(lowerPkg) ||
                    lowerPkg.startsWith("com.chrome.")) {
                Log.i(TAG, "Package " + packageName + " confirmed as unmanaged Web Browser conduit");
                return true;
            }

            CharSequence labelSeq = pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0));
            String lowerLabel = (labelSeq != null) ? labelSeq.toString().toLowerCase(Locale.US) : "";

            boolean hasBrowserKeyword = lowerPkg.contains("browser") || lowerPkg.contains("smartbrowse") ||
                    lowerPkg.contains("astroorbit") || lowerLabel.contains("browser");

            if (hasBrowserKeyword) {
                // Ensure it is not a file browser, photo browser, or database viewer
                boolean isFileManager = lowerPkg.contains("filemanager") || lowerPkg.contains("fileexplorer") ||
                        lowerPkg.contains("explorer.file") || lowerPkg.contains("db.browser") ||
                        lowerLabel.contains("file manager") || lowerLabel.contains("file explorer");

                if (!isFileManager) {
                    int internetPerm = pm.checkPermission(android.Manifest.permission.INTERNET, packageName);
                    if (internetPerm == PackageManager.PERMISSION_GRANTED) {
                        Log.i(TAG, "Package " + packageName + " (" + lowerLabel + ") confirmed as Web Browser via signature & INTERNET permission");
                        return true;
                    }
                }
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
     * Unsuspends all third-party browsers when auto-block protection is disabled,
     * while preserving explicit blocklist locks and daily usage timer locks.
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
                String pkg = app.packageName;
                if (SecurityConfig.isBlocklisted(context, pkg) ||
                        AppTimerManager.isDailyLimitExceeded(context, pkg) ||
                        ImpulseGuardService.isTemporarilySuspended(context, pkg)) {
                    continue;
                }
                // If it is an alternative browser, lift the DPM suspension
                if (isNonChromeBrowser(context, pkg)) {
                    dpm.setPackagesSuspended(DeviceAdminReceiver.getComponentName(context), new String[]{pkg}, false);
                    Log.i(TAG, "UNSUSPENDED BROWSER: " + pkg);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in unSuspendAllBrowsers", e);
        }
    }
}

