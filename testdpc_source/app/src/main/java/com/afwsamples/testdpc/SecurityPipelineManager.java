package com.afwsamples.testdpc;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.UserManager;
import android.util.Log;
import android.widget.Toast;
import androidx.core.app.NotificationCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
    // SECTION 2: 3-Tier Security Pipeline Execution & Play Store Abuse Lockout
    // =====================================================================================

    // Deduplication map preventing multiple broadcast receivers (PACKAGE_ADDED, SessionCallback, LauncherApps)
    // from firing 3-5 redundant Gemini API audits for a single package installation
    private static final ConcurrentHashMap<String, Long> sRecentPipelineTriggerMap = new ConcurrentHashMap<>();

    // Deduplication map ensuring a single blocked app install increments the Play Store strike counter once
    private static final ConcurrentHashMap<String, Long> sRecentPlayStoreStrikeMap = new ConcurrentHashMap<>();

    private static final String KEY_PLAYSTORE_STRIKES = "playstore_blocked_install_strikes";
    private static final String KEY_PLAYSTORE_LAST_STRIKE_TS = "playstore_last_strike_timestamp";
    private static final long PLAYSTORE_STRIKE_WINDOW_MS = 2 * 60 * 60 * 1000L; // 2-hour rolling strike window
    private static final String PLAYSTORE_PACKAGE = "com.android.vending";
    private static final String PLAYSTORE_NOTIF_CHANNEL = "playstore_guard_channel";
    private static final int PLAYSTORE_NOTIF_ID = 9450;

    /**
     * Public entry point called whenever a package is added, updated, or re-installed.
     * Dispatches processing to a single background worker thread to prevent UI stutter.
     *
     * @param context     Application context
     * @param packageName Installed or updated package identifier
     */
    public static void onPackageAddedOrUpdated(final Context context, final String packageName) {
        if (packageName == null || packageName.isEmpty()) return;
        final String lowerPkg = packageName.trim().toLowerCase(Locale.US);

        // Deduplicate rapid back-to-back broadcasts for the exact same package within 8 seconds
        long now = System.currentTimeMillis();
        Long lastTrigger = sRecentPipelineTriggerMap.get(lowerPkg);
        if (lastTrigger != null && (now - lastTrigger) < 8000L) {
            Log.d(TAG, "Skipping duplicate package install broadcast within 8s window for: " + lowerPkg);
            return;
        }
        sRecentPipelineTriggerMap.put(lowerPkg, now);

        // Fresh installation or update: purge from safe cache (never purge from blocked cache!)
        evictFromSafeCache(context, lowerPkg);

        sBgExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    processPackagePipeline(context, lowerPkg, true);
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
    private static void processPackagePipeline(Context context, String packageName, boolean isLiveInstallEvent) {
        // ---------------------------------------------------------------------------------
        // TIER 0: Verified Safe & Confirmed Blocked Package Caches (0ms Instant Return)
        // ---------------------------------------------------------------------------------
        String lowerPkg = (packageName != null) ? packageName.toLowerCase(Locale.US) : "";

        //Retrieve DevicePolicyManager system service
        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName admin = DeviceAdminReceiver.getComponentName(context);
        boolean isDeviceOwner = (dpm != null && dpm.isDeviceOwnerApp(context.getPackageName()));

        // Tier 0A: If already confirmed blocked in cache (e.g. user uninstalled & re-installed a 2.4MB browser
        // trying to drain Gemini API quota), block in 0ms with ZERO API calls and record a Play Store strike!
        if (getCachedBlockedPackages(context).contains(lowerPkg) &&
                !SecurityConfig.isWhitelisted(context, lowerPkg) &&
                !SecurityConfig.isUnderProbation(context, lowerPkg)) {
            if (isDeviceOwner) {
                dpm.setPackagesSuspended(admin, new String[]{lowerPkg}, true);
            }
            SecurityLogger.log(context, "[TIER0_BLOCKED_CACHE]", lowerPkg + " -> Previously Blocked Package Re-installed -> Instant 0ms Suspend (0 API Quota Used)");
            if (isLiveInstallEvent) {
                recordBlockedAppInstallStrike(context, lowerPkg, "Repeated install of blocked package");
            }
            return;
        }

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

        // ---------------------------------------------------------------------------------
        // TIER 2: Precision Deterministic Fast-Path Checks (<2ms)
        // ---------------------------------------------------------------------------------
        boolean isBlocklisted = SecurityConfig.isBlocklisted(context, packageName);
        boolean isBrowser = BrowserBlocker.isAutoBlockEnabled(context) && BrowserBlocker.isNonChromeBrowser(context, packageName);

        // Branch 2A: Explicit Hard Blocklist Match (e.g. TikTok, Twitter, Reddit, Streamflix, Cloners)
        if (isBlocklisted) {
            // Hard definitive block: user explicitly blocked this app -> DO NOT send to AI for rescue!
            markPackageBlocked(context, packageName);
            if (isDeviceOwner) {
                // Instantly suspend package via DevicePolicyManager
                dpm.setPackagesSuspended(admin, new String[]{packageName}, true);
            }
            SecurityLogger.log(context, "[TIER2_HARD_BLOCK]", packageName + " -> Explicit Blocklist Match -> Permanently Suspended");
            if (isLiveInstallEvent) {
                recordBlockedAppInstallStrike(context, packageName, "Explicit Blocklist Match");
            }
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

    /**
     * Play Store Abuse & Circumvention Strike Counter:
     * Called whenever a newly installed application is blocked/suspended by any security tier.
     *
     * Rules:
     *   - Strike 1: Issues a high-priority warning Toast & Notification (1/2).
     *   - Strike 2 (within 2 hours): Suspends Google Play Store ("com.android.vending") for 30 minutes!
     *   - Strike 3+ (within 2 hours): Suspends Google Play Store ("com.android.vending") for 60 minutes!
     */
    public static synchronized void recordBlockedAppInstallStrike(final Context context, final String blockedPackage, final String reason) {
        if (context == null || blockedPackage == null || blockedPackage.isEmpty()) return;
        final String lowerPkg = blockedPackage.trim().toLowerCase(Locale.US);

        // Never count core Android/Google system components or self
        if (lowerPkg.startsWith("com.afwsamples.testdpc") ||
                PLAYSTORE_PACKAGE.equals(lowerPkg) ||
                lowerPkg.startsWith("com.android.") ||
                (lowerPkg.startsWith("com.google.android.") && !lowerPkg.contains("chrome") && !lowerPkg.contains("youtube"))) {
            return;
        }

        // Verify the package was recently installed/updated (within last 15 minutes) so boot scans never trigger Play Store strikes
        try {
            android.content.pm.PackageInfo pi = context.getPackageManager().getPackageInfo(lowerPkg, 0);
            long recentInstallOrUpdate = Math.max(pi.firstInstallTime, pi.lastUpdateTime);
            if (System.currentTimeMillis() - recentInstallOrUpdate > 15 * 60 * 1000L) {
                return;
            }
        } catch (Exception ignored) {
        }

        // Deduplicate multiple callbacks for the exact same package install within 6 seconds
        long now = System.currentTimeMillis();
        Long lastStrikeForPkg = sRecentPlayStoreStrikeMap.get(lowerPkg);
        if (lastStrikeForPkg != null && (now - lastStrikeForPkg) < 6000L) {
            return;
        }
        sRecentPlayStoreStrikeMap.put(lowerPkg, now);

        SharedPreferences prefs = getCachePrefs(context);
        long lastStrikeTs = prefs.getLong(KEY_PLAYSTORE_LAST_STRIKE_TS, 0L);
        int strikes = prefs.getInt(KEY_PLAYSTORE_STRIKES, 0);

        if (lastStrikeTs <= 0L || (now - lastStrikeTs) > PLAYSTORE_STRIKE_WINDOW_MS) {
            strikes = 0;
        }
        strikes++;

        prefs.edit()
                .putInt(KEY_PLAYSTORE_STRIKES, strikes)
                .putLong(KEY_PLAYSTORE_LAST_STRIKE_TS, now)
                .apply();

        // Resolve human-readable app label if possible
        String label = lowerPkg;
        try {
            PackageManager pm = context.getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(lowerPkg, 0);
            CharSequence cs = pm.getApplicationLabel(ai);
            if (cs != null) label = cs.toString();
        } catch (Exception ignored) {
        }
        final String appDisplayLabel = label;
        final int currentStrikeCount = strikes;

        if (currentStrikeCount == 1) {
            final String warnMsg = "⚠️ Play Store Warning (1/2): Blocked app \"" + appDisplayLabel + "\". Installing 1 more restricted app will lock Play Store for 30 mins!";
            SecurityLogger.log(context, "[PLAYSTORE_STRIKE_1]", "Blocked install of " + lowerPkg + " (" + reason + ") -> Warning 1/2 issued");
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(context.getApplicationContext(), warnMsg, Toast.LENGTH_LONG).show();
                    showPlayStoreSecurityNotification(context.getApplicationContext(), "Play Store Security Warning (1/2) ⚠️", warnMsg);
                }
            });
        } else {
            // Strike 2 -> 30 minutes lockout; Strike 3+ -> 60 minutes lockout
            final int lockoutMinutes = (currentStrikeCount == 2) ? 30 : 60;
            enforcePlayStoreCooldownLock(context.getApplicationContext(), lockoutMinutes, appDisplayLabel + " (" + lowerPkg + ")", currentStrikeCount);
        }
    }

    /**
     * Suspends Google Play Store ("com.android.vending") for the specified cooldown duration
     * via DevicePolicyManager and schedules automatic un-suspension upon expiration.
     */
    public static void enforcePlayStoreCooldownLock(final Context context, final int durationMinutes, final String triggerSource, final int strikeCount) {
        try {
            final DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            if (dpm == null || !dpm.isDeviceOwnerApp(context.getPackageName())) {
                return;
            }
            final ComponentName admin = DeviceAdminReceiver.getComponentName(context);
            final long durationMs = durationMinutes * 60 * 1000L;
            final long expiryTimestamp = System.currentTimeMillis() + durationMs;

            // 1. Persist suspension expiry in ImpulseGuardService's shared suspension store
            // so both checkAndCleanExpiredSuspensions() and BootOptimizer respect and clean it up
            SharedPreferences suspPrefs = context.getSharedPreferences("impulse_guard_active_suspensions", Context.MODE_PRIVATE);
            long existingExpiry = suspPrefs.getLong(PLAYSTORE_PACKAGE, 0L);
            final long finalExpiry = Math.max(existingExpiry, expiryTimestamp);
            suspPrefs.edit().putLong(PLAYSTORE_PACKAGE, finalExpiry).apply();

            // 2. Suspend Google Play Store at the OS level via DevicePolicyManager
            dpm.setPackagesSuspended(admin, new String[]{PLAYSTORE_PACKAGE}, true);

            final String lockMsg = "🔒 Google Play Store Locked for " + durationMinutes + " Minutes! (Strike #" + strikeCount + " triggered by blocked app: " + triggerSource + ")";
            Log.w(TAG, lockMsg);
            SecurityLogger.log(context, "[PLAYSTORE_LOCKED]", lockMsg);

            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(context, lockMsg, Toast.LENGTH_LONG).show();
                    showPlayStoreSecurityNotification(context, "Google Play Store Locked (" + durationMinutes + " Mins) 🔒", lockMsg);
                }
            });

            // 3. Schedule automatic un-suspension on main Handler when cooldown finishes
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        SharedPreferences sp = context.getSharedPreferences("impulse_guard_active_suspensions", Context.MODE_PRIVATE);
                        long exp = sp.getLong(PLAYSTORE_PACKAGE, 0L);
                        if (System.currentTimeMillis() >= exp) {
                            dpm.setPackagesSuspended(admin, new String[]{PLAYSTORE_PACKAGE}, false);
                            sp.edit().remove(PLAYSTORE_PACKAGE).apply();
                            SecurityLogger.log(context, "[PLAYSTORE_UNLOCKED]", "Google Play Store cooldown expired; unsuspended automatically.");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error auto-unsuspending Play Store", e);
                    }
                }
            }, durationMs + 1500L);

        } catch (Exception e) {
            Log.e(TAG, "Failed to enforce Play Store cooldown lock", e);
        }
    }

    private static void showPlayStoreSecurityNotification(Context context, String title, String message) {
        try {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel ch = new NotificationChannel(PLAYSTORE_NOTIF_CHANNEL, "Play Store Security Guard", NotificationManager.IMPORTANCE_HIGH);
                nm.createNotificationChannel(ch);
            }
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, PLAYSTORE_NOTIF_CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_lock_lock)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true);
            nm.notify(PLAYSTORE_NOTIF_ID, builder.build());
        } catch (Exception ignored) {
        }
    }

    // =====================================================================================
    // SECTION 3: Boot Optimizer & False-Positive Auto-Rescue
    // =====================================================================================

    /**
     * Enforces OS-level Device Owner anti-tamper restrictions:
     * 1. DISALLOW_CONFIG_DATE_TIME + AutoTime: Prevents rolling the clock forward to bypass 10:00 PM timers or penalty lockouts.
     * 2. DISALLOW_SAFE_BOOT: Prevents rebooting into Android Safe Mode where Accessibility Services are disabled.
     * 3. DISALLOW_ADD_USER: Prevents switching to a Guest user or secondary user profile to bypass restrictions.
     * 4. DISALLOW_CONFIG_PRIVATE_DNS + CleanBrowsing Adult Filter Private DNS: Enforces system-wide SafeSearch & adult domain filtering and locks the setting in Android Settings so it never reverts to "Automatic".
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                dpm.setAutoTimeEnabled(admin, true);
            } else {
                dpm.setAutoTimeRequired(admin, true);
            }

            // Enforce & Permanently Lock System-Wide SafeSearch Private DNS:
            // Note: ISP blocks TCP/TLS port 853 to cleanbrowsing.org ("Private DNS server cannot be accessed"),
            // whereas family.cloudflare-dns.com (1.1.1.3) and family.adguard-dns.com (94.140.14.15) pass TLS 853
            // on this ISP while strictly mapping www.google.com -> forcesafesearch.google.com (216.239.38.120)
            // and sinkholing adult domains across ALL apps (including Google App)!
            final String defaultSafeDnsHost = "family.cloudflare-dns.com";
            try {
                String currentMode = android.provider.Settings.Global.getString(context.getContentResolver(), "private_dns_mode");
                String currentHost = android.provider.Settings.Global.getString(context.getContentResolver(), "private_dns_specifier");
                boolean isBlockedCleanBrowsing = currentHost != null && currentHost.toLowerCase().contains("cleanbrowsing");
                final String targetHost = (currentHost != null && !currentHost.trim().isEmpty() && currentHost.contains(".") && !isBlockedCleanBrowsing)
                        ? currentHost.trim()
                        : defaultSafeDnsHost;

                if (!"hostname".equals(currentMode) || currentHost == null || currentHost.trim().isEmpty() || isBlockedCleanBrowsing) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        dpm.clearUserRestriction(admin, UserManager.DISALLOW_CONFIG_PRIVATE_DNS);
                    }
                    dpm.setGlobalSetting(admin, "private_dns_specifier", targetHost);
                    dpm.setGlobalSetting(admin, "private_dns_mode", "hostname");
                    android.provider.Settings.Global.putString(context.getContentResolver(), "private_dns_specifier", targetHost);
                    android.provider.Settings.Global.putString(context.getContentResolver(), "private_dns_mode", "hostname");
                    Log.i(TAG, "🔒 Enforced system-wide SafeSearch Private DNS via Settings.Global: " + targetHost);
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    dpm.addUserRestriction(admin, UserManager.DISALLOW_CONFIG_PRIVATE_DNS);
                }
            } catch (Throwable ignored) {
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                final Context appCtx = context.getApplicationContext();
                sBgExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            DevicePolicyManager bgDpm = (DevicePolicyManager) appCtx.getSystemService(Context.DEVICE_POLICY_SERVICE);
                            if (bgDpm != null) {
                                ComponentName bgAdmin = DeviceAdminReceiver.getComponentName(appCtx);
                                String mode = android.provider.Settings.Global.getString(appCtx.getContentResolver(), "private_dns_mode");
                                String host = android.provider.Settings.Global.getString(appCtx.getContentResolver(), "private_dns_specifier");
                                boolean isCleanBrowsing = host != null && host.toLowerCase().contains("cleanbrowsing");
                                String target = (host != null && !host.trim().isEmpty() && host.contains(".") && !isCleanBrowsing)
                                        ? host.trim()
                                        : defaultSafeDnsHost;
                                if (!"hostname".equals(mode) || host == null || host.trim().isEmpty() || isCleanBrowsing) {
                                    bgDpm.clearUserRestriction(bgAdmin, UserManager.DISALLOW_CONFIG_PRIVATE_DNS);
                                    bgDpm.setGlobalPrivateDnsModeSpecifiedHost(bgAdmin, target);
                                }
                                bgDpm.addUserRestriction(bgAdmin, UserManager.DISALLOW_CONFIG_PRIVATE_DNS);
                            }
                        } catch (Throwable ignoredBg) {
                        }
                    }
                });
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
                        processPackagePipeline(context, pkg, false);
                    }

                    // If Play Store is currently under an active 30-min lockout, ensure it stays suspended across reboot
                    if (ImpulseGuardService.isTemporarilySuspended(context, PLAYSTORE_PACKAGE)) {
                        packagesToSuspend.add(PLAYSTORE_PACKAGE);
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

