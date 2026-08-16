package com.afwsamples.testdpc;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The Central Security Pipeline Coordinator for DpcLocker / Test DPC.
 * Implements the 3-Tier Short-Circuit Pipeline:
 * Tier 1: User UI Whitelist (0ms Instant Pass)
 * Tier 2: Precision Deterministic Fast-Path Suspension (<2ms) + Background AI False-Positive Rescue
 * Tier 3: Gray-Area Deep Manifest AI Scanner (for unclassified 3rd party apps)
 * Also optimizes device boot with a 0ms Verified Package Cache and auto-rescue mechanism.
 */
public class SecurityPipelineManager {

    private static final String TAG = "SecurityPipeline";
    private static final String PREFS_CACHE = "dpclocker_package_state_cache";
    private static final String KEY_SAFE_PACKAGES = "cache_verified_safe_packages";
    private static final String KEY_BLOCKED_PACKAGES = "cache_confirmed_blocked_packages";

    private static final ExecutorService sBgExecutor = Executors.newSingleThreadExecutor();

    private static SharedPreferences getCachePrefs(Context context) {
        return context.getSharedPreferences(PREFS_CACHE, Context.MODE_PRIVATE);
    }

    public static Set<String> getCachedSafePackages(Context context) {
        return new HashSet<>(getCachePrefs(context).getStringSet(KEY_SAFE_PACKAGES, new HashSet<String>()));
    }

    public static Set<String> getCachedBlockedPackages(Context context) {
        return new HashSet<>(getCachePrefs(context).getStringSet(KEY_BLOCKED_PACKAGES, new HashSet<String>()));
    }

    public static void markPackageSafe(Context context, String packageName) {
        if (packageName == null) return;
        String lower = packageName.trim().toLowerCase();
        Set<String> safeSet = getCachedSafePackages(context);
        safeSet.add(lower);
        Set<String> blockedSet = getCachedBlockedPackages(context);
        blockedSet.remove(lower);

        getCachePrefs(context).edit()
                .putStringSet(KEY_SAFE_PACKAGES, safeSet)
                .putStringSet(KEY_BLOCKED_PACKAGES, blockedSet)
                .apply();
    }

    public static void markPackageBlocked(Context context, String packageName) {
        if (packageName == null) return;
        String lower = packageName.trim().toLowerCase();
        Set<String> safeSet = getCachedSafePackages(context);
        safeSet.remove(lower);
        Set<String> blockedSet = getCachedBlockedPackages(context);
        blockedSet.add(lower);

        getCachePrefs(context).edit()
                .putStringSet(KEY_SAFE_PACKAGES, safeSet)
                .putStringSet(KEY_BLOCKED_PACKAGES, blockedSet)
                .apply();
    }

    /**
     * Checks if a package is permanently prohibited. Used by ImpulseGuardService to prevent
     * temporary penalty timer expirations or master unsuspend buttons from ever un-suspending blocked browsers.
     */
    public static boolean isPermanentlyProhibited(Context context, String packageName) {
        if (packageName == null) return false;
        String lower = packageName.trim().toLowerCase();

        // 1. Whitelist override
        if (SecurityConfig.isWhitelisted(context, lower)) {
            return false;
        }

        // 2. User blocklist or precision browser block check
        if (SecurityConfig.isBlocklisted(context, lower)) {
            return true;
        }

        if (BrowserBlocker.isAutoBlockEnabled(context) && BrowserBlocker.isNonChromeBrowser(context, lower)) {
            return true;
        }

        Set<String> blockedCache = getCachedBlockedPackages(context);
        return blockedCache.contains(lower);
    }

    /**
     * Main 3-Tier Security Entry Point for package installation and update events.
     */
    public static void onPackageAddedOrUpdated(final Context context, final String packageName) {
        if (packageName == null || packageName.isEmpty()) return;
        final String lowerPkg = packageName.trim().toLowerCase();

        sBgExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    processPackagePipeline(context, lowerPkg);
                } catch (Exception e) {
                    Log.e(TAG, "Error in security pipeline for " + lowerPkg, e);
                    SecurityLogger.log(context, "[PIPELINE_ERROR]", lowerPkg + ": " + e.getMessage());
                }
            }
        });
    }

    private static void processPackagePipeline(Context context, String packageName) {
        // --- TIER 1: User UI Whitelist & Core System (0ms) ---
        if (SecurityConfig.isWhitelisted(context, packageName)) {
            markPackageSafe(context, packageName);
            SecurityLogger.log(context, "[TIER1_PASS]", packageName + " -> Whitelisted / System Core (0ms)");
            return;
        }

        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName admin = DeviceAdminReceiver.getComponentName(context);
        boolean isDeviceOwner = (dpm != null && dpm.isDeviceOwnerApp(context.getPackageName()));

        // --- TIER 2: Precision Deterministic Fast-Path Checks (<2ms) ---
        boolean isBlocklisted = SecurityConfig.isBlocklisted(context, packageName);
        boolean isBrowser = BrowserBlocker.isAutoBlockEnabled(context) && BrowserBlocker.isNonChromeBrowser(context, packageName);

        if (isBlocklisted || isBrowser) {
            String triggerReason = isBrowser ? "Browser Intent Match" : "Notorious Social Blocklist";
            markPackageBlocked(context, packageName);

            if (isDeviceOwner) {
                dpm.setPackagesSuspended(admin, new String[]{packageName}, true);
            }

            SecurityLogger.log(context, "[TIER2_SUSPEND]", packageName + " -> " + triggerReason + " -> Provisionally Suspended");

            // Asynchronous AI False-Positive Rescue Verification
            AiAppAuditor.verifyAndRescuePackageAsync(context, packageName, triggerReason);
            return;
        }

        // --- TIER 3: AI Gray-Area Manifest & Adult App Scanner ---
        AiAppAuditor.checkAndAuditPackage(context, packageName);
    }

    /**
     * High-Performance Boot Optimizer & False-Positive Auto-Rescue:
     * - Rescues any innocent apps incorrectly marked blocked.
     * - Skips verified safe apps (0ms).
     * - Batch re-asserts suspensions on confirmed prohibited apps (<5ms).
     */
    public static void onBootCompleted(final Context context) {
        sBgExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
                    ComponentName admin = DeviceAdminReceiver.getComponentName(context);
                    boolean isDeviceOwner = (dpm != null && dpm.isDeviceOwnerApp(context.getPackageName()));

                    PackageManager pm = context.getPackageManager();
                    List<ApplicationInfo> installedApps = pm.getInstalledApplications(0);

                    Set<String> safeCache = getCachedSafePackages(context);
                    Set<String> blockedCache = getCachedBlockedPackages(context);

                    List<String> packagesToSuspend = new ArrayList<>();
                    List<String> packagesToUnsuspend = new ArrayList<>();
                    int skippedSafeCount = 0;
                    int newAppCount = 0;

                    for (ApplicationInfo ai : installedApps) {
                        String pkg = ai.packageName.toLowerCase();

                        boolean isProhibited = SecurityConfig.isBlocklisted(context, pkg) ||
                                (BrowserBlocker.isAutoBlockEnabled(context) && BrowserBlocker.isNonChromeBrowser(context, pkg));

                        // 1. Whitelist override -> always ensure un-suspended and marked safe
                        if (SecurityConfig.isWhitelisted(context, pkg)) {
                            if (blockedCache.contains(pkg)) {
                                packagesToUnsuspend.add(pkg);
                                markPackageSafe(context, pkg);
                            }
                            skippedSafeCount++;
                            continue;
                        }

                        // 2. Confirmed prohibited app
                        if (isProhibited) {
                            packagesToSuspend.add(pkg);
                            markPackageBlocked(context, pkg);
                            continue;
                        }

                        // 3. App was previously cached as blocked by broad filter but is NOT prohibited now -> Auto-Rescue!
                        if (blockedCache.contains(pkg) && !isProhibited) {
                            packagesToUnsuspend.add(pkg);
                            markPackageSafe(context, pkg);
                            SecurityLogger.log(context, "[BOOT_RESCUE]", pkg + " -> Rescued & Un-suspended (Verified clean app)");
                            continue;
                        }

                        // 4. If cached as safe, skip instantly (0ms)
                        if (safeCache.contains(pkg)) {
                            skippedSafeCount++;
                            continue;
                        }

                        // 5. Uncached new app -> evaluate through pipeline
                        newAppCount++;
                        processPackagePipeline(context, pkg);
                    }

                    // Bulk re-assert suspensions on confirmed prohibited apps (<5ms)
                    if (isDeviceOwner && !packagesToSuspend.isEmpty()) {
                        dpm.setPackagesSuspended(admin, packagesToSuspend.toArray(new String[0]), true);
                    }

                    // Bulk unsuspend innocent rescued apps
                    if (isDeviceOwner && !packagesToUnsuspend.isEmpty()) {
                        dpm.setPackagesSuspended(admin, packagesToUnsuspend.toArray(new String[0]), false);
                    }

                    SecurityLogger.log(context, "[BOOT_OPTIMIZER]", "Boot complete: " + skippedSafeCount + " safe apps skipped (0ms) | " +
                            packagesToSuspend.size() + " prohibited apps re-asserted | " + packagesToUnsuspend.size() + " apps rescued | " + newAppCount + " new apps evaluated");

                } catch (Exception e) {
                    Log.e(TAG, "Error in boot optimizer", e);
                }
            }
        });
    }
}
