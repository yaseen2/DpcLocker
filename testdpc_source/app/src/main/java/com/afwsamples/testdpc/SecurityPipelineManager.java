package com.afwsamples.testdpc;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.UserManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * =========================================================================================
 * CLASS: SecurityPipelineManager
 * =========================================================================================
 * Purpose:
 *   The Central Security Orchestrator for DpcLocker and TestDPC.
 *   Coordinates package inspection across all security sub-modules using an optimized
 *   3-Tier Short-Circuit Pipeline designed for sub-millisecond execution.
 *
 * The 3-Tier Short-Circuit Architecture:
 *   ---------------------------------------------------------------------------------------
 *   Tier 0: In-Memory / SharedPreferences Safe Cache (0ms Instant Return)
 *           If an app was previously vetted as clean, bypass all checks immediately.
 *   Tier 1: User UI Whitelist & System Core Packages (0ms Instant Pass)
 *           Exempts system apps (Settings, SystemUI, Phone, Keyboard) and user-whitelisted apps.
 *   Tier 2: Precision Fast-Path Suspension (<2ms Deterministic Check)
 *           - If explicitly on User Proactive Blocklist -> HARD BLOCK (never rescued).
 *           - If flagged as an unmanaged web browser -> PROVISIONAL SUSPENSION + AI Rescue.
 *   Tier 3: Deep AI Static Manifest Audit (Background Gemini 1.5 Analysis)
 *           For unclassified third-party APKs, scans AndroidManifest permissions, services,
 *           and Play Store metadata to detect disguised adult apps, dating apps, or VPN tunnels.
 *
 * Boot Optimization & False-Positive Auto-Rescue:
 *   Upon device boot, scans all installed applications:
 *     - Skips 99% of pre-verified safe apps instantly.
 *     - Re-asserts DPM hardware suspensions on prohibited packages in a single batch (<5ms).
 *     - Automatically rescues and unsuspends clean apps if blocklists were updated.
 * =========================================================================================
 */
public class SecurityPipelineManager {

    // Logcat tag for security pipeline diagnostics
    private static final String TAG = "SecurityPipeline";

    // SharedPreferences file holding verified package classification cache
    private static final String PREFS_CACHE = "dpclocker_package_state_cache";

    // Cache key sets
    private static final String KEY_SAFE_PACKAGES = "cache_verified_safe_packages";
    private static final String KEY_BLOCKED_PACKAGES = "cache_confirmed_blocked_packages";

    // Single-thread background worker executor to serialize package pipeline evaluations
    private static final ExecutorService sBgExecutor = Executors.newSingleThreadExecutor();

    // High-speed volatile in-memory caches for verified safe and blocked packages
    private static volatile Set<String> sCachedSafeSet = null;
    private static volatile Set<String> sCachedBlockedSet = null;

    /**
     * Helper to retrieve SharedPreferences for package state cache.
     */
    private static SharedPreferences getCachePrefs(Context context) {
        return context.getSharedPreferences(PREFS_CACHE, Context.MODE_PRIVATE);
    }

    // =====================================================================================
    // SECTION 1: Package State Cache Management
    // =====================================================================================

    /**
     * Returns the cached set of verified safe package names.
     */
    public static Set<String> getCachedSafePackages(Context context) {
        Set<String> cached = sCachedSafeSet;
        if (cached != null) {
            return cached;
        }
        synchronized (SecurityPipelineManager.class) {
            if (sCachedSafeSet == null) {
                sCachedSafeSet = Collections.unmodifiableSet(
                        new HashSet<>(getCachePrefs(context).getStringSet(KEY_SAFE_PACKAGES, new HashSet<String>())));
            }
            return sCachedSafeSet;
        }
    }

    /**
     * Returns the cached set of confirmed blocked package names.
     */
    public static Set<String> getCachedBlockedPackages(Context context) {
        Set<String> cached = sCachedBlockedSet;
        if (cached != null) {
            return cached;
        }
        synchronized (SecurityPipelineManager.class) {
            if (sCachedBlockedSet == null) {
                sCachedBlockedSet = Collections.unmodifiableSet(
                        new HashSet<>(getCachePrefs(context).getStringSet(KEY_BLOCKED_PACKAGES, new HashSet<String>())));
            }
            return sCachedBlockedSet;
        }
    }

    /**
     * Records a package as verified clean/safe in the cache, removing it from the blocked cache.
     * Includes Hard Blocklist Protection: will refuse to mark an app safe if it is on the user blocklist.
     *
     * @param context     Application context
     * @param packageName Package identifier to mark safe
     */
    public static synchronized void markPackageSafe(Context context, String packageName) {
        if (packageName == null) return;
        String lower = packageName.trim().toLowerCase(Locale.US);

        // Hard Blocklist Protection: Never allow AI or auto-rescue to override an explicit user blocklist entry!
        if (SecurityConfig.isBlocklisted(context, lower)) {
            Log.w(TAG, "Attempted to mark explicitly blocklisted package as safe: " + lower + " -> REJECTED");
            markPackageBlocked(context, lower);
            return;
        }

        // Add to safe set and remove from blocked set
        Set<String> safeSet = new HashSet<>(getCachedSafePackages(context));
        safeSet.add(lower);
        Set<String> blockedSet = new HashSet<>(getCachedBlockedPackages(context));
        blockedSet.remove(lower);

        sCachedSafeSet = Collections.unmodifiableSet(new HashSet<>(safeSet));
        sCachedBlockedSet = Collections.unmodifiableSet(new HashSet<>(blockedSet));

        // Commit both sets atomically to cache preferences
        getCachePrefs(context).edit()
                .putStringSet(KEY_SAFE_PACKAGES, safeSet)
                .putStringSet(KEY_BLOCKED_PACKAGES, blockedSet)
                .apply();
    }

    /**
     * Records a package as blocked in the cache and removes it from the safe set.
     *
     * @param context     Application context
     * @param packageName Package identifier to mark blocked
     */
    public static synchronized void markPackageBlocked(Context context, String packageName) {
        if (packageName == null) return;
        String lower = packageName.trim().toLowerCase(Locale.US);
        Set<String> safeSet = new HashSet<>(getCachedSafePackages(context));
        safeSet.remove(lower);
        Set<String> blockedSet = new HashSet<>(getCachedBlockedPackages(context));
        blockedSet.add(lower);

        sCachedSafeSet = Collections.unmodifiableSet(new HashSet<>(safeSet));
        sCachedBlockedSet = Collections.unmodifiableSet(new HashSet<>(blockedSet));

        getCachePrefs(context).edit()
                .putStringSet(KEY_SAFE_PACKAGES, safeSet)
                .putStringSet(KEY_BLOCKED_PACKAGES, blockedSet)
                .apply();
    }

    /**
     * Purges a package from the safe cache so fresh installs or updates are re-evaluated.
     */
    public static synchronized void evictFromSafeCache(Context context, String packageName) {
        if (packageName == null) return;
        String lower = packageName.trim().toLowerCase(Locale.US);
        Set<String> safeSet = new HashSet<>(getCachedSafePackages(context));
        if (safeSet.remove(lower)) {
            sCachedSafeSet = Collections.unmodifiableSet(new HashSet<>(safeSet));
            getCachePrefs(context).edit().putStringSet(KEY_SAFE_PACKAGES, safeSet).apply();
            Log.i(TAG, "Evicted " + lower + " from safe package cache.");
        }
    }

    /**
     * Core Security Gatekeeper:
     * Checks if a package is permanently prohibited by any policy rule.
     * Used by ImpulseGuardService and AppTimerManager to prevent temporary timer expirations
     * or manual unsuspend buttons from ever un-suspending unauthorized apps.
     *
     * Rules:
     *   1. Whitelist override -> NOT prohibited (false).
     *   2. Explicitly blocklisted in SecurityConfig -> prohibited (true).
     *   3. Non-Chrome browser with auto-block active -> prohibited (true).
     *   4. Cached in blocked packages -> prohibited (true).
     *   5. Otherwise -> not prohibited (false).
     *
     * @param context     Application context
     * @param packageName Target package
     * @return True if the package is permanently prohibited from running.
     */
    public static boolean isPermanentlyProhibited(Context context, String packageName) {
        if (packageName == null) return false;
        String lower = packageName.trim().toLowerCase();

        // 1. Whitelist takes highest precedence
        if (SecurityConfig.isWhitelisted(context, lower)) {
            return false;
        }

        // 2. User proactive blocklist check
        if (SecurityConfig.isBlocklisted(context, lower)) {
            return true;
        }

        // 3. Alternative browser check: if it is under active probation, it is not permanently prohibited
        if (SecurityConfig.isUnderProbation(context, lower)) {
            return false;
        }

        if (BrowserBlocker.isAutoBlockEnabled(context) && BrowserBlocker.isNonChromeBrowser(context, lower)) {
            return true;
        }

        // 4. Confirmed blocked cache check
        Set<String> blockedCache = getCachedBlockedPackages(context);
        return blockedCache.contains(lower);
    }

    // =====================================================================================
    // SECTION 2: 3-Tier Security Pipeline Execution
    // =====================================================================================

    /**
     * Public entry point called whenever a package is added, updated, or re-installed.
     * Dispatches processing to a single background worker thread to prevent UI stutter.
     *
     * @param context     Application context
     * @param packageName Installed or updated package identifier
     */
    public static void onPackageAddedOrUpdated(final Context context, final String packageName) {
        if (packageName == null || packageName.isEmpty()) return;
        final String lowerPkg = packageName.trim().toLowerCase();

        // Fresh installation or update: always purge from safe cache to force re-evaluation!
        evictFromSafeCache(context, lowerPkg);

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

    /**
     * Executes the 3-Tier short-circuit evaluation logic for a single package.
     */
    private static void processPackagePipeline(Context context, String packageName) {
        // ---------------------------------------------------------------------------------
        // TIER 0: Verified Safe Package Cache (0ms Instant Return)
        // ---------------------------------------------------------------------------------
        String lowerPkg = (packageName != null) ? packageName.toLowerCase() : "";
        if (getCachedSafePackages(context).contains(lowerPkg)) {
            // Ensure an explicit blocklist entry or unmanaged browser invalidates stale safe cache
            if (!SecurityConfig.isBlocklisted(context, lowerPkg) &&
                    (!BrowserBlocker.isAutoBlockEnabled(context) || !BrowserBlocker.isNonChromeBrowser(context, lowerPkg))) {
                Log.i(TAG, "Package " + packageName + " is already verified safe in cache. Skipping audit (0ms).");
                return;
            } else {
                Log.w(TAG, "Package " + packageName + " was cached safe but is prohibited by policy. Evicting from safe cache.");
                markPackageBlocked(context, lowerPkg);
            }
        }

        // Active Probation Bypass: If package is currently undergoing visual probation, avoid re-suspension
        if (SecurityConfig.isUnderProbation(context, lowerPkg)) {
            Log.i(TAG, "Package " + packageName + " is undergoing Targeted Visual Probation. Skipping redundant pipeline trigger.");
            return;
        }

        // ---------------------------------------------------------------------------------
        // TIER 1: User UI Whitelist & Core System Packages (0ms Instant Pass)
        // ---------------------------------------------------------------------------------
        if (SecurityConfig.isWhitelisted(context, packageName)) {
            markPackageSafe(context, packageName);
            SecurityLogger.log(context, "[TIER1_PASS]", packageName + " -> Whitelisted / System Core (0ms)");
            return;
        }

        // Retrieve DevicePolicyManager system service
        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName admin = DeviceAdminReceiver.getComponentName(context);
        boolean isDeviceOwner = (dpm != null && dpm.isDeviceOwnerApp(context.getPackageName()));

        // ---------------------------------------------------------------------------------
        // TIER 2: Precision Deterministic Fast-Path Checks (<2ms)
        // ---------------------------------------------------------------------------------
        boolean isBlocklisted = SecurityConfig.isBlocklisted(context, packageName);
        boolean isBrowser = BrowserBlocker.isAutoBlockEnabled(context) && BrowserBlocker.isNonChromeBrowser(context, packageName);

        // Branch 2A: Explicit Hard Blocklist Match (e.g. TikTok, Twitter, Reddit)
        if (isBlocklisted) {
            // Hard definitive block: user explicitly blocked this app -> DO NOT send to AI for rescue!
            markPackageBlocked(context, packageName);
            if (isDeviceOwner) {
                // Instantly suspend package via DevicePolicyManager
                dpm.setPackagesSuspended(admin, new String[]{packageName}, true);
            }
            SecurityLogger.log(context, "[TIER2_HARD_BLOCK]", packageName + " -> Explicit Blocklist Match -> Permanently Suspended");
            return;
        }

        // Branch 2B: Heuristic Web Browser Match
        if (isBrowser) {
            // Flagged by intent filters -> suspend immediately to prevent instant bypass, but dispatch to AI rescue
            markPackageBlocked(context, packageName);
            if (isDeviceOwner) {
                dpm.setPackagesSuspended(admin, new String[]{packageName}, true);
            }
            SecurityLogger.log(context, "[TIER2_BROWSER_SUSPEND]", packageName + " -> Heuristic Browser Intent Match -> Provisionally Suspended");
            // Dispatch background AI audit to verify if app is an innocent web-view tool (e.g. documentation viewer)
            AiAppAuditor.verifyAndRescuePackageAsync(context, packageName, "Browser Intent Match");
            return;
        }

        // ---------------------------------------------------------------------------------
        // TIER 3: AI Gray-Area Manifest & Adult App Scanner
        // ---------------------------------------------------------------------------------
        // Package is unclassified; perform static manifest inspection and Gemini cloud audit
        AiAppAuditor.checkAndAuditPackage(context, packageName);
    }

    // =====================================================================================
    // SECTION 3: Boot Optimizer & False-Positive Auto-Rescue
    // =====================================================================================

    /**
     * Enforces OS-level Device Owner anti-tamper restrictions:
     * 1. DISALLOW_CONFIG_DATE_TIME + AutoTime: Prevents rolling the clock forward to bypass 10:00 PM timers or penalty lockouts.
     * 2. DISALLOW_SAFE_BOOT: Prevents rebooting into Android Safe Mode where Accessibility Services are disabled.
     * 3. DISALLOW_ADD_USER: Prevents switching to a Guest user or secondary user profile to bypass restrictions.
     */
    public static void enforceCoreDeviceOwnerTamperRestrictions(Context context) {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            if (dpm == null || !dpm.isDeviceOwnerApp(context.getPackageName())) {
                return;
            }
            ComponentName admin = DeviceAdminReceiver.getComponentName(context);
            dpm.addUserRestriction(admin, UserManager.DISALLOW_CONFIG_DATE_TIME);
            dpm.addUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT);
            dpm.addUserRestriction(admin, UserManager.DISALLOW_ADD_USER);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                dpm.addUserRestriction(admin, UserManager.DISALLOW_CONFIG_PRIVATE_DNS);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                dpm.setAutoTimeEnabled(admin, true);
            } else {
                dpm.setAutoTimeRequired(admin, true);
            }

            // Enforce System-Wide SafeSearch & Adult Filter Private DNS (0% CPU / 0MB RAM overhead):
            // Maps www.google.com -> forcesafesearch.google.com (216.239.38.120) across ALL apps
            // including the Google App (com.google.android.googlequicksearchbox), Images, and Videos.
            final String safeSearchDnsHost = "adult-filter-dns.cleanbrowsing.org";
            try {
                String currentMode = android.provider.Settings.Global.getString(context.getContentResolver(), "private_dns_mode");
                String currentHost = android.provider.Settings.Global.getString(context.getContentResolver(), "private_dns_specifier");
                if (!"hostname".equals(currentMode) || !safeSearchDnsHost.equalsIgnoreCase(currentHost)) {
                    android.provider.Settings.Global.putString(context.getContentResolver(), "private_dns_mode", "hostname");
                    android.provider.Settings.Global.putString(context.getContentResolver(), "private_dns_specifier", safeSearchDnsHost);
                    Log.i(TAG, "🔒 Enforced system-wide SafeSearch Private DNS via Settings.Global: " + safeSearchDnsHost);
                }
            } catch (Throwable ignored) {
                // Fallback to DevicePolicyManager background API if WRITE_SECURE_SETTINGS is not yet granted
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    final Context appCtx = context.getApplicationContext();
                    sBgExecutor.execute(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                DevicePolicyManager bgDpm = (DevicePolicyManager) appCtx.getSystemService(Context.DEVICE_POLICY_SERVICE);
                                if (bgDpm != null) {
                                    bgDpm.setGlobalPrivateDnsModeSpecifiedHost(
                                            DeviceAdminReceiver.getComponentName(appCtx),
                                            safeSearchDnsHost);
                                }
                            } catch (Throwable ignoredBg) {
                            }
                        }
                    });
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not enforce core Device Owner tamper restrictions: " + e.getMessage());
        }
    }

    /**
     * High-Performance Boot Optimizer:
     * Executes upon device reboot (ACTION_BOOT_COMPLETED).
     *
     * Operations:
     *   1. Scans all installed packages on device.
     *   2. Skips pre-verified safe applications instantly (0ms per package).
     *   3. Re-asserts DPM hardware suspensions in bulk (<5ms total).
     *   4. Rescues innocent packages that were previously blocked by broad filters but are now clean.
     *   5. Submits newly installed uncached apps into the evaluation pipeline.
     */
    public static void onBootCompleted(final Context context) {
        sBgExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    enforceCoreDeviceOwnerTamperRestrictions(context);

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

                    // Iterate over every app installed on the device
                    for (ApplicationInfo ai : installedApps) {
                        String pkg = ai.packageName.toLowerCase(Locale.US);

                        // 1. Whitelist override -> always unsuspend and ensure marked safe
                        if (SecurityConfig.isWhitelisted(context, pkg)) {
                            packagesToUnsuspend.add(pkg);
                            markPackageSafe(context, pkg);
                            continue;
                        }

                        // 2. Explicit User Blocklist match -> queue for hardware suspension
                        if (SecurityConfig.isBlocklisted(context, pkg)) {
                            packagesToSuspend.add(pkg);
                            markPackageBlocked(context, pkg);
                            continue;
                        }

                        // 3. Active Probation app -> ensure unsuspended for visual monitoring (checked BEFORE browser heuristic!)
                        if (SecurityConfig.isUnderProbation(context, pkg)) {
                            packagesToUnsuspend.add(pkg);
                            continue;
                        }

                        // 3.5 Alternative Browser match -> queue for hardware suspension
                        if (BrowserBlocker.isAutoBlockEnabled(context) && BrowserBlocker.isNonChromeBrowser(context, pkg)) {
                            boolean alreadyBlocked = blockedCache.contains(pkg);
                            packagesToSuspend.add(pkg);
                            markPackageBlocked(context, pkg);
                            if (!alreadyBlocked) {
                                AiAppAuditor.verifyAndRescuePackageAsync(context, pkg, "Boot Browser Check");
                            }
                            continue;
                        }

                        // 4. Confirmed blocked in cache -> maintain hardware suspension
                        if (blockedCache.contains(pkg)) {
                            packagesToSuspend.add(pkg);
                            continue;
                        }

                        // 5. Cached safe app -> skip immediately (0ms overhead)
                        if (safeCache.contains(pkg)) {
                            skippedSafeCount++;
                            continue;
                        }

                        // 6. Brand new unclassified app -> run through the full security pipeline
                        newAppCount++;
                        processPackagePipeline(context, pkg);
                    }

                    // Batch re-assert hardware suspensions on confirmed prohibited apps
                    if (isDeviceOwner && !packagesToSuspend.isEmpty()) {
                        dpm.setPackagesSuspended(admin, packagesToSuspend.toArray(new String[0]), true);
                    }

                    // Batch unsuspend rescued innocent apps (verifying timer, visual penalty, and blocklist status)
                    if (isDeviceOwner && !packagesToUnsuspend.isEmpty()) {
                        List<String> validUnsuspend = new java.util.ArrayList<>();
                        for (String p : packagesToUnsuspend) {
                            if (!AppTimerManager.isDailyLimitExceeded(context, p) &&
                                    !ImpulseGuardService.isTemporarilySuspended(context, p) &&
                                    !SecurityConfig.isBlocklisted(context, p)) {
                                validUnsuspend.add(p);
                            }
                        }
                        if (!validUnsuspend.isEmpty()) {
                            dpm.setPackagesSuspended(admin, validUnsuspend.toArray(new String[0]), false);
                        }
                    }

                    // Re-enforce Chrome enterprise policies & URLBlocklist upon boot
                    ChromePolicyManager.enforceDefaultChromePolicies(context);

                    // Log aggregate summary of the boot optimization cycle
                    SecurityLogger.log(context, "[BOOT_OPTIMIZER]", "Boot complete: " + skippedSafeCount + " safe apps skipped (0ms) | " +
                            packagesToSuspend.size() + " prohibited apps re-asserted | " + packagesToUnsuspend.size() + " apps rescued | " + newAppCount + " new apps evaluated");

                } catch (Exception e) {
                    Log.e(TAG, "Error in boot optimizer", e);
                }
            }
        });
    }
}

