package com.afwsamples.testdpc;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.Set;

/**
 * ==============================================================================
 * NOTORIOUS APP BLOCKER :: BLOCKLIST SUSPENSION & POLICY BRIDGE
 * ==============================================================================
 * Purpose:
 * Serves as the operational bridge between the unified SecurityConfig blocklist
 * and Android's DevicePolicyManager (DPM).
 *
 * How It Works:
 * 1. Delegates all blocklist queries and modifications to SecurityConfig so that
 *    user blocklist additions, removals, and notorious defaults are kept in a single
 *    source of truth.
 * 2. When an app matches the blocklist (e.g. TikTok, Twitter, Reddit, YouTube),
 *    `checkAndSuspendNotoriousPackage()` enforces immediate package suspension
 *    (`dpm.setPackagesSuspended = true`), greying out the icon and locking execution.
 * 3. Sets `dpm.setUninstallBlocked = false` so that the user is permitted (and encouraged)
 *    to delete the prohibited app from the device.
 * ==============================================================================
 */
public class NotoriousAppBlocker {

    // Logcat tag for debugging notorious app detection and suspension
    private static final String TAG = "NotoriousAppBlocker";

    // Legacy preference file name for backwards-compatibility
    private static final String PREF_NAME = "dpclocker_notorious_blocker";

    /**
     * Returns legacy SharedPreferences instance for backwards compatibility.
     */
    public static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Returns the complete, unified set of blocked packages from SecurityConfig.
     * Merges default notorious packages (TikTok, X, Reddit, Telegram, etc.) with user additions.
     */
    public static Set<String> getBlockedPackages(Context context) {
        return SecurityConfig.getUserBlocklist(context);
    }

    /**
     * Adds a new package name to the unified blocklist and immediately suspends it if installed.
     */
    public static void addPackageToBlocklist(Context context, String packageName) {
        SecurityConfig.addToUserBlocklist(context, packageName);
    }

    /**
     * Removes a package name from the blocklist and unsuspends it if installed.
     */
    public static void removePackageFromBlocklist(Context context, String packageName) {
        SecurityConfig.removeFromUserBlocklist(context, packageName);
    }

    /**
     * Checks if a package is currently blocklisted (taking whitelist overrides into account).
     */
    public static boolean isPackageBlocked(Context context, String packageName) {
        return SecurityConfig.isBlocklisted(context, packageName);
    }

    /**
     * Checks if a package is in the notorious blocklist. If so, immediately suspends it
     * via DevicePolicyManager, marks it blocked in SecurityPipelineManager cache, and
     * ensures uninstallation remains allowed.
     *
     * @param context Android context
     * @param packageName Target package to evaluate
     */
    public static void checkAndSuspendNotoriousPackage(Context context, String packageName) {
        if (packageName == null) return;

        // Verify against unified blocklist (respects whitelist overrides)
        if (isPackageBlocked(context, packageName)) {
            try {
                DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
                if (dpm != null && dpm.isDeviceOwnerApp(context.getPackageName())) {
                    // 1. Immediately suspend the package (greys out icon and blocks launching)
                    dpm.setPackagesSuspended(DeviceAdminReceiver.getComponentName(context), new String[]{packageName}, true);

                    // 2. Allow uninstallation so user can cleanly delete the prohibited app
                    dpm.setUninstallBlocked(DeviceAdminReceiver.getComponentName(context), packageName, false);

                    // 3. Mark blocked in SecurityPipelineManager persistent state cache
                    SecurityPipelineManager.markPackageBlocked(context, packageName);

                    Log.i(TAG, "AUTO-BLOCKED NOTORIOUS APP INSTALLED: " + packageName);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error suspending notorious package " + packageName, e);
            }
        }
    }

    /**
     * Iterates over all known blocked packages and enforces suspension on any that are installed.
     * Called on fragment resume and during administrative policy synchronization.
     */
    public static void scanAndSuspendAllNotoriousApps(Context context) {
        Set<String> blockedPackages = getBlockedPackages(context);
        for (String pkg : blockedPackages) {
            checkAndSuspendNotoriousPackage(context, pkg);
        }
    }
}
