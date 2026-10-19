package com.afwsamples.testdpc;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * =========================================================================================
 * CLASS: SecurityConfig
 * =========================================================================================
 * Purpose:
 *   Centralized, single source of truth configuration repository for TestDPC and DpcLocker
 *   security policies.
 *
 * Core Responsibilities:
 *   1. Unified Gemini AI API Key Vault:
 *      Consolidates and synchronizes the Gemini API Key across legacy SharedPreferences stores
 *      (e.g., `gemini_guard_engine_prefs` and `ai_app_auditor_prefs`) so users only need to
 *      configure their API key once.
 *   2. User Whitelist Management:
 *      Maintains an explicit list of packages (e.g. WhatsApp, Duolingo, AnkiDroid, ReVanced)
 *      that are completely immune to browser blocking, notoriety blocking, and AI audits.
 *   3. Core System Package Protection:
 *      Safeguards essential Android OS components (System UI, Settings, Google Keyboard, Play Store)
 *      so security enforcement never bricks or bootloops the Android device.
 *   4. Unified Proactive & Notorious App Blocklist:
 *      Stores high-risk adult apps, social media addictions (Twitter/X, Reddit, Tumblr, Telegram,
 *      TikTok), and synchronizes with DevicePolicyManager (DPM) to instantly suspend apps.
 * =========================================================================================
 */
public class SecurityConfig {

    // Logcat filter identifier
    private static final String TAG = "SecurityConfig";

    // Primary preferences file name where modern security configurations are stored
    public static final String PREFS_NAME = "dpclocker_security_config";

    // Configuration keys stored within SharedPreferences
    public static final String KEY_GEMINI_API_KEY = "gemini_shared_api_key";
    public static final String KEY_USER_WHITELIST = "user_whitelist_packages";
    public static final String KEY_USER_BLOCKLIST = "user_blocklist_packages";

    // Legacy preference and key constants retained for backwards-compatibility across older UI components
    public static final String PREFS_PROACTIVE_BLOCKLIST = "proactive_package_blocklist";
    public static final String KEY_PROACTIVE_SET = "blocked_packages_set";
    public static final String PREFS_NOTORIOUS = "dpclocker_notorious_blocker";
    public static final String KEY_NOTORIOUS_SET = "blocked_packages";

    /**
     * Critical immutable operating system packages that must never be suspended or blocked.
     * Suspending these could cause bootloops or lock the user out of the device.
     */
    public static final Set<String> IMMUTABLE_CORE_PACKAGES = new HashSet<>(Arrays.asList(
            "com.afwsamples.testdpc",                 // TestDPC itself (Device Owner)
            "android",                                // Android OS core framework
            "com.android.systemui",                   // Android Navigation Bar, Status Bar, Quick Settings
            "com.android.settings",                   // Android System Settings
            "com.google.android.inputmethod.latin",   // Gboard / Virtual Keyboard (required for lockscreen PIN/password)
            "com.google.android.apps.nexuslauncher",  // Pixel / AOSP Home Launcher
            "com.android.vending"                     // Google Play Store
    ));

    /**
     * Set of core operating system packages and critical utilities.
     * These apps are hard-exempt from any suspension, blocking, or AI auditing.
     * Note: "com.google.android.youtube" is handled with custom logic in isWhitelisted().
     */
    private static final Set<String> SYSTEM_CORE_PACKAGES = new HashSet<>(Arrays.asList(
            "com.afwsamples.testdpc",                 // TestDPC itself (Device Owner)
            "com.android.systemui",                   // Android Navigation Bar, Status Bar, Quick Settings
            "android",                                // Android OS core framework
            "com.android.settings",                   // Android System Settings
            "com.google.android.inputmethod.latin",   // Gboard / Virtual Keyboard
            "com.google.android.apps.nexuslauncher",  // Pixel / AOSP Home Launcher
            "com.android.vending",                    // Google Play Store
            "com.android.chrome",                     // Google Chrome (Managed via ChromePolicyManager)
            "com.google.android.googlequicksearchbox",// Google Search Widget
            "com.google.android.gm",                  // Gmail
            "com.google.android.apps.maps",           // Google Maps
            "com.google.android.apps.photos",         // Google Photos
            "com.android.calculator2",                // Basic Calculator
            "com.android.deskclock",                  // System Alarm Clock & Timer
            "com.whatsapp"                            // Essential messaging utility
    ));

    /**
     * Default list of notorious high-risk platforms known for adult/NSFW content or infinite scrolling.
     * Included automatically in the proactive blocklist unless explicitly whitelisted by user.
     */
    private static final String[] DEFAULT_NOTORIOUS_PACKAGES = new String[]{
            "com.twitter.android",          // X / Twitter Official App
            "com.twitter.android.lite",     // X Lite
            "com.reddit.frontpage",         // Reddit Official App
            "com.tumblr",                   // Tumblr
            "org.telegram.messenger",       // Telegram Official
            "org.telegram.messenger.web",   // Telegram Web Client
            "org.telegram.plus",            // Telegram Plus Client
            "com.google.android.youtube",   // Official YouTube App (Subject to AppTimerManager limits)
            "com.zhiliaoapp.musically",     // TikTok
            "com.zhiliaoapp.musically.go",  // TikTok Lite
            "com.ss.android.ugc.trill",     // TikTok Regional
            "com.ss.android.ugc.aweme",     // Douyin
            "com.chrome.beta",              // Chrome Beta (Unmanaged browser channel)
            "com.chrome.dev",               // Chrome Dev (Unmanaged browser channel)
            "com.chrome.canary"             // Chrome Canary (Unmanaged browser channel)
    };

    /**
     * Helper to retrieve the primary SharedPreferences instance.
     */
    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // =====================================================================================
    // SECTION 1: Gemini API Key Management (Single Shared Vault)
    // =====================================================================================

    /**
     * Retrieves the configured Gemini API Key.
     * Implements automated multi-tier migration:
     * 1. Checks primary "dpclocker_security_config".
     * 2. If absent, checks legacy "gemini_guard_engine_prefs" and migrates it forward.
     * 3. If absent, checks legacy "ai_app_auditor_prefs" and migrates it forward.
     *
     * @param context Application context
     * @return Trimmed API key string, or empty string if unconfigured.
     */
    public static String getGeminiApiKey(Context context) {
        SharedPreferences prefs = getPrefs(context);
        // Step 1: Check primary unified store
        if (prefs.contains(KEY_GEMINI_API_KEY)) {
            return prefs.getString(KEY_GEMINI_API_KEY, "").trim();
        }

        // Step 2: Legacy fallback 1 - GeminiGuardEngine store
        String legacyKey1 = context.getSharedPreferences("gemini_guard_engine_prefs", Context.MODE_PRIVATE)
                .getString("gemini_api_key", "").trim();
        if (!legacyKey1.isEmpty()) {
            setGeminiApiKey(context, legacyKey1); // Auto-migrate to unified store
            return legacyKey1;
        }

        // Step 3: Legacy fallback 2 - AiAppAuditor store
        String legacyKey2 = context.getSharedPreferences("ai_app_auditor_prefs", Context.MODE_PRIVATE)
                .getString("gemini_api_key", "").trim();
        if (!legacyKey2.isEmpty()) {
            setGeminiApiKey(context, legacyKey2); // Auto-migrate to unified store
            return legacyKey2;
        }

        return "";
    }

    /**
     * Saves the Gemini API Key into the unified vault and synchronizes it across all legacy
     * preference files to guarantee 100% interoperability across background threads.
     *
     * @param context Application context
     * @param apiKey  New Gemini API key provided by user
     */
    public static void setGeminiApiKey(Context context, String apiKey) {
        String trimmed = (apiKey != null) ? apiKey.trim() : "";
        // 1. Write to primary store
        getPrefs(context).edit().putString(KEY_GEMINI_API_KEY, trimmed).apply();

        // 2. Synchronize legacy preference stores
        context.getSharedPreferences("gemini_guard_engine_prefs", Context.MODE_PRIVATE)
                .edit().putString("gemini_api_key", trimmed).apply();
        context.getSharedPreferences("ai_app_auditor_prefs", Context.MODE_PRIVATE)
                .edit().putString("gemini_api_key", trimmed).apply();

        Log.i(TAG, "Unified Gemini API Key updated across all security subsystems.");
    }

    // =====================================================================================
    // SECTION 2: User Whitelist Management
    // =====================================================================================

    /**
     * Returns the set of user-whitelisted package names.
     * If uninitialized, seeds the set with safe, educational, and communication apps.
     */
    public static Set<String> getUserWhitelist(Context context) {
        SharedPreferences prefs = getPrefs(context);
        if (!prefs.contains(KEY_USER_WHITELIST)) {
            // Seed default whitelist
            Set<String> defaults = new HashSet<>(Arrays.asList(
                    "com.whatsapp",
                    "com.ankidroid",
                    "com.duolingo",
                    "app.revanced.android.youtube",
                    "app.revanced.android.gms"
            ));
            prefs.edit().putStringSet(KEY_USER_WHITELIST, defaults).apply();
            return defaults;
        }
        return new HashSet<>(prefs.getStringSet(KEY_USER_WHITELIST, new HashSet<String>()));
    }

    /**
     * Adds an application package name to the user whitelist.
     * Automatically purges the package from the blocklist if it was previously blocked.
     *
     * @param context     Application context
     * @param packageName Package identifier to whitelist
     */
    public static void addToUserWhitelist(Context context, String packageName) {
        if (packageName == null || packageName.trim().isEmpty()) return;
        String lower = packageName.trim().toLowerCase();
        Set<String> set = getUserWhitelist(context);
        set.add(lower);
        getPrefs(context).edit().putStringSet(KEY_USER_WHITELIST, set).apply();
        
        // Ensure whitelisted packages are not simultaneously present in the blocklist
        removeFromUserBlocklist(context, lower);
        Log.i(TAG, "Added package to User Whitelist: " + lower);
    }

    /**
     * Removes an application package name from the user whitelist.
     */
    public static void removeFromUserWhitelist(Context context, String packageName) {
        if (packageName == null) return;
        String lower = packageName.trim().toLowerCase();
        Set<String> set = getUserWhitelist(context);
        set.remove(lower);
        getPrefs(context).edit().putStringSet(KEY_USER_WHITELIST, set).apply();
        Log.i(TAG, "Removed package from User Whitelist: " + lower);
    }

    /**
     * Determines whether a package is exempt from all security restrictions.
     *
     * Evaluation Hierarchy:
     *   1. Empty/null -> considered safe (returns true).
     *   2. Immutable OS core packages (Settings, Framework, SystemUI, TestDPC, Gboard, Launcher, Vending) -> returns true.
     *   3. Explicit custom user blocklist -> returns false (User's manual blocklist ALWAYS takes precedence!).
     *   4. Custom user whitelist -> returns true if present in user whitelist.
     *   5. Default notorious packages (YouTube, TikTok, Reddit, etc.) -> returns false.
     *   6. Apps with configured usage timers (AppTimerManager) -> returns false (regulated by timer).
     *   7. Consumer Google apps / Alternative Browsers (Google Go, YouTube Music, YouTube Kids, AOSP Browser) -> returns false.
     *   8. Static system core utility packages (Maps, Gmail, Photos, Calculator, Clock, WhatsApp) -> returns true.
     *   9. System framework namespace prefixes ("com.android.", "com.google.android."):
     *      - Consumer app namespace ("com.google.android.apps.") -> returns false (must be inspectable/blockable).
     *      - Low-level OS framework services (GMS, GSF, telephony, package installer, etc.) -> returns true.
     *  10. Otherwise -> returns false.
     *
     * @param context     Application context
     * @param packageName Package identifier to test
     * @return True if package is whitelisted and immune to blocking.
     */
    public static boolean isWhitelisted(Context context, String packageName) {
        if (packageName == null || packageName.isEmpty()) return true;
        String lower = packageName.trim().toLowerCase();

        // 1. Immutable OS bedrock - critical packages that cannot be suspended without bricking the device
        if (IMMUTABLE_CORE_PACKAGES.contains(lower)) {
            return true;
        }

        // 2. Explicit custom user blocklist: If the user manually added this package, it is NEVER whitelisted!
        if (getCustomUserBlocklist(context).contains(lower)) {
            return false;
        }

        // 3. User custom whitelist: user explicitly whitelisted this package in the UI
        Set<String> userWhitelist = getUserWhitelist(context);
        if (userWhitelist.contains(lower)) {
            return true;
        }

        // 4. Default notorious packages (e.g. YouTube, TikTok, Reddit, Twitter)
        for (String notorious : DEFAULT_NOTORIOUS_PACKAGES) {
            if (notorious.equalsIgnoreCase(lower)) {
                return false;
            }
        }

        // 5. Apps with active usage quotas (regulated by AppTimerManager)
        if (AppTimerManager.getAppLimitMinutes(context, lower) > 0) {
            return false;
        }

        // 6. Known Google consumer apps & alternative browsers that bypass enterprise Chrome restrictions
        if ("com.google.android.apps.searchlite".equals(lower) ||      // Google Go (unmanaged browser)
                "com.google.android.apps.youtube.music".equals(lower) || // YouTube Music
                "com.google.android.apps.youtube.kids".equals(lower) ||  // YouTube Kids
                "com.google.android.youtube".equals(lower) ||            // YouTube
                "com.android.browser".equals(lower)) {                   // AOSP Browser
            return false;
        }

        // 7. Static system core utility packages (e.g. Maps, Gmail, Photos, Calculator, Clock, WhatsApp)
        if (SYSTEM_CORE_PACKAGES.contains(lower)) {
            return true;
        }

        // 8. System framework namespace prefixes:
        // Exclude Google consumer applications under "com.google.android.apps." so they can be blocked/audited
        if (lower.startsWith("com.google.android.apps.")) {
            return false;
        }

        // Low-level Android framework and Google Play Services (e.g. com.google.android.gms, com.android.phone)
        if (lower.startsWith("com.android.") || lower.startsWith("com.google.android.")) {
            return true;
        }

        return false;
    }

    // =====================================================================================
    // SECTION 3: Unified User Proactive Blocklist
    // =====================================================================================

    // Key storing explicitly removed notorious packages
    public static final String KEY_REMOVED_NOTORIOUS = "removed_notorious_packages";

    /**
     * Returns strictly the custom packages manually added to the blocklist by the user.
     * Does NOT include the hardcoded default notorious packages.
     */
    public static Set<String> getCustomUserBlocklist(Context context) {
        SharedPreferences primaryPrefs = getPrefs(context);
        Set<String> userSet = primaryPrefs.getStringSet(KEY_USER_BLOCKLIST, null);
        if (userSet != null) {
            return new HashSet<>(userSet);
        }
        return new HashSet<>();
    }

    /**
     * Returns the aggregated set of blocked packages merged across:
     * 1. Built-in DEFAULT_NOTORIOUS_PACKAGES (minus any explicitly removed by user)
     * 2. Modern unified custom blocklist store (getCustomUserBlocklist)
     * 3. Legacy "proactive_package_blocklist"
     * 4. Legacy "dpclocker_notorious_blocker"
     */
    public static Set<String> getUserBlocklist(Context context) {
        SharedPreferences primaryPrefs = getPrefs(context);

        // Start with default hardcoded notorious apps minus any explicitly removed
        Set<String> result = new HashSet<>(Arrays.asList(DEFAULT_NOTORIOUS_PACKAGES));
        Set<String> removedSet = primaryPrefs.getStringSet(KEY_REMOVED_NOTORIOUS, null);
        if (removedSet != null) {
            result.removeAll(removedSet);
        }

        // 1. Load and merge from custom user blocklist
        result.addAll(getCustomUserBlocklist(context));

        // 2. Merge from legacy proactive_package_blocklist
        SharedPreferences proactivePrefs = context.getSharedPreferences(PREFS_PROACTIVE_BLOCKLIST, Context.MODE_PRIVATE);
        Set<String> proactiveSet = proactivePrefs.getStringSet(KEY_PROACTIVE_SET, null);
        if (proactiveSet != null) {
            result.addAll(proactiveSet);
        }

        // 3. Merge from legacy notorious prefs
        SharedPreferences notoriousPrefs = context.getSharedPreferences(PREFS_NOTORIOUS, Context.MODE_PRIVATE);
        Set<String> notoriousSet = notoriousPrefs.getStringSet(KEY_NOTORIOUS_SET, null);
        if (notoriousSet != null) {
            result.addAll(notoriousSet);
        }

        return result;
    }

    /**
     * Adds an application package to the proactive blocklist, synchronizes all stores,
     * updates the in-memory cache, and immediately suspends the app via DevicePolicyManager.
     *
     * @param context     Application context
     * @param packageName Package identifier to block
     */
    public static void addToUserBlocklist(Context context, String packageName) {
        if (packageName == null || packageName.trim().isEmpty()) return;
        String lower = packageName.trim().toLowerCase();

        // 1. Update custom user set
        Set<String> customList = getCustomUserBlocklist(context);
        customList.add(lower);
        getPrefs(context).edit().putStringSet(KEY_USER_BLOCKLIST, customList).apply();

        // 2. If it was in removed notorious, un-remove it
        SharedPreferences primaryPrefs = getPrefs(context);
        Set<String> removedSet = primaryPrefs.getStringSet(KEY_REMOVED_NOTORIOUS, null);
        if (removedSet != null && removedSet.contains(lower)) {
            Set<String> newRemoved = new HashSet<>(removedSet);
            newRemoved.remove(lower);
            primaryPrefs.edit().putStringSet(KEY_REMOVED_NOTORIOUS, newRemoved).apply();
        }

        // 3. Update legacy stores for compatibility
        context.getSharedPreferences(PREFS_PROACTIVE_BLOCKLIST, Context.MODE_PRIVATE)
                .edit().putStringSet(KEY_PROACTIVE_SET, customList).apply();
        context.getSharedPreferences(PREFS_NOTORIOUS, Context.MODE_PRIVATE)
                .edit().putStringSet(KEY_NOTORIOUS_SET, customList).apply();

        // 4. Update pipeline cache so subsequent accessibility checks instantly reject this app
        SecurityPipelineManager.markPackageBlocked(context, lower);

        // 5. Immediate enterprise suspension if installed on device
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName admin = DeviceAdminReceiver.getComponentName(context);
            if (dpm != null && dpm.isDeviceOwnerApp(context.getPackageName())) {
                dpm.setPackagesSuspended(admin, new String[]{lower}, true);
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not immediately suspend package (might be uninstalled): " + lower);
        }

        Log.i(TAG, "Added package to Unified Proactive Blocklist: " + lower);
        SecurityLogger.log(context, "[PROACTIVE_BLOCK_ADD]", lower + " -> Added to Proactive Blocklist");
    }

    /**
     * Removes an application package from the blocklist, synchronizes all stores,
     * marks it safe in the cache, and unsuspends it via DevicePolicyManager.
     *
     * @param context     Application context
     * @param packageName Package identifier to unblock
     */
    public static void removeFromUserBlocklist(Context context, String packageName) {
        if (packageName == null) return;
        String lower = packageName.trim().toLowerCase();

        // 1. Remove from custom user set
        Set<String> customList = getCustomUserBlocklist(context);
        customList.remove(lower);
        getPrefs(context).edit().putStringSet(KEY_USER_BLOCKLIST, customList).apply();

        // 2. If this was a default notorious package, record in removed_notorious
        boolean isDefaultNotorious = false;
        for (String notorious : DEFAULT_NOTORIOUS_PACKAGES) {
            if (notorious.equalsIgnoreCase(lower)) {
                isDefaultNotorious = true;
                break;
            }
        }
        if (isDefaultNotorious) {
            Set<String> removedSet = new HashSet<>(getPrefs(context).getStringSet(KEY_REMOVED_NOTORIOUS, new HashSet<String>()));
            removedSet.add(lower);
            getPrefs(context).edit().putStringSet(KEY_REMOVED_NOTORIOUS, removedSet).apply();
        }

        // 3. Sync legacy stores
        context.getSharedPreferences(PREFS_PROACTIVE_BLOCKLIST, Context.MODE_PRIVATE)
                .edit().putStringSet(KEY_PROACTIVE_SET, customList).apply();
        context.getSharedPreferences(PREFS_NOTORIOUS, Context.MODE_PRIVATE)
                .edit().putStringSet(KEY_NOTORIOUS_SET, customList).apply();

        // 4. Mark package safe in pipeline cache
        SecurityPipelineManager.markPackageSafe(context, lower);

        // 5. Unsuspend package if currently installed (and not locked by AppTimerManager)
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName admin = DeviceAdminReceiver.getComponentName(context);
            if (dpm != null && dpm.isDeviceOwnerApp(context.getPackageName())) {
                if (!AppTimerManager.isDailyLimitExceeded(context, lower)) {
                    dpm.setPackagesSuspended(admin, new String[]{lower}, false);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not unsuspend package: " + lower);
        }

        Log.i(TAG, "Removed package from Unified Proactive Blocklist: " + lower);
        SecurityLogger.log(context, "[PROACTIVE_BLOCK_REMOVE]", lower + " -> Removed from Proactive Blocklist");
    }

    /**
     * Evaluates whether a package is considered blocklisted.
     *
     * Rules:
     *   1. If in IMMUTABLE_CORE_PACKAGES -> NEVER blocked (returns false).
     *   2. If found in getCustomUserBlocklist() -> ALWAYS blocked (returns true).
     *   3. If found in getUserWhitelist() -> whitelisted by user (returns false).
     *   4. If found in getUserBlocklist() -> blocked (returns true).
     *   5. Otherwise -> returns false.
     *
     * @param context     Application context
     * @param packageName Package identifier to test
     * @return True if the package is blocklisted.
     */
    public static boolean isBlocklisted(Context context, String packageName) {
        if (packageName == null || packageName.isEmpty()) return false;
        String lower = packageName.trim().toLowerCase();

        // 1. Immutable OS bedrock can NEVER be blocklisted
        if (IMMUTABLE_CORE_PACKAGES.contains(lower)) {
            return false;
        }

        // 2. Explicit custom user blocklist ALWAYS takes absolute precedence!
        if (getCustomUserBlocklist(context).contains(lower)) {
            return true;
        }

        // 3. User custom whitelist takes precedence over default notorious apps
        if (getUserWhitelist(context).contains(lower)) {
            return false;
        }

        // 4. Default notorious list or legacy stores
        Set<String> blocklist = getUserBlocklist(context);
        return blocklist.contains(lower);
    }

    // =====================================================================================
    // TARGETED VISUAL PROBATION CONSTANTS & METHODS
    // =====================================================================================
    public static final String PREFS_PROBATION = "dpclocker_probation_prefs";
    public static final String KEY_PROBATION_ACTIVE_SET = "probation_active_set";
    public static final String KEY_PROBATION_SHOTS_PREFIX = "probation_shots_";
    public static final String KEY_PROBATION_START_PREFIX = "probation_start_";
    public static final int MAX_PROBATION_SHOTS = 5;
    public static final long PROBATION_DURATION_MS = 5 * 60 * 1000L; // 5 minutes active window

    public static synchronized boolean isUnderProbation(Context context, String packageName) {
        if (packageName == null || packageName.isEmpty()) return false;
        String lower = packageName.trim().toLowerCase();
        if (isWhitelisted(context, lower) || isBlocklisted(context, lower)) {
            return false;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS_PROBATION, Context.MODE_PRIVATE);
        Set<String> activeSet = prefs.getStringSet(KEY_PROBATION_ACTIVE_SET, Collections.<String>emptySet());
        if (activeSet.contains(lower)) {
            long startTime = prefs.getLong(KEY_PROBATION_START_PREFIX + lower, 0L);
            int shots = prefs.getInt(KEY_PROBATION_SHOTS_PREFIX + lower, 0);
            if (startTime > 0 && (System.currentTimeMillis() - startTime > PROBATION_DURATION_MS) && shots >= 2) {
                // Auto graduate after 5 minutes of clean monitoring with at least 2 shots
                graduateProbation(context, lower);
                return false;
            }
            return true;
        }
        return false;
    }

    public static synchronized void startProbation(Context context, String packageName) {
        if (packageName == null || packageName.isEmpty()) return;
        String lower = packageName.trim().toLowerCase();
        if (isWhitelisted(context, lower) || isBlocklisted(context, lower)) {
            return;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS_PROBATION, Context.MODE_PRIVATE);
        Set<String> activeSet = new HashSet<>(prefs.getStringSet(KEY_PROBATION_ACTIVE_SET, Collections.<String>emptySet()));
        activeSet.add(lower);
        prefs.edit()
                .putStringSet(KEY_PROBATION_ACTIVE_SET, activeSet)
                .putInt(KEY_PROBATION_SHOTS_PREFIX + lower, 0)
                .putLong(KEY_PROBATION_START_PREFIX + lower, System.currentTimeMillis())
                .apply();

        // Also register in ImpulseGuardService so text/proxy inspection is active
        ImpulseGuardService.setMonitoredApp(context, lower, true);
        Log.i(TAG, "Enrolled package in Targeted Visual Probation: " + lower);
        SecurityLogger.log(context, "[PROBATION_START]", lower + " -> Enrolled in 5-Shot Visual Probation (Max 5 mins)");
    }

    public static synchronized int getProbationShotCount(Context context, String packageName) {
        if (packageName == null) return 0;
        String lower = packageName.trim().toLowerCase();
        SharedPreferences prefs = context.getSharedPreferences(PREFS_PROBATION, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_PROBATION_SHOTS_PREFIX + lower, 0);
    }

    public static synchronized int incrementProbationShotCount(Context context, String packageName) {
        if (packageName == null) return 0;
        String lower = packageName.trim().toLowerCase();
        SharedPreferences prefs = context.getSharedPreferences(PREFS_PROBATION, Context.MODE_PRIVATE);
        int current = prefs.getInt(KEY_PROBATION_SHOTS_PREFIX + lower, 0);
        int updated = current + 1;
        prefs.edit().putInt(KEY_PROBATION_SHOTS_PREFIX + lower, updated).apply();
        return updated;
    }

    public static synchronized void graduateProbation(Context context, String packageName) {
        if (packageName == null) return;
        String lower = packageName.trim().toLowerCase();
        SharedPreferences prefs = context.getSharedPreferences(PREFS_PROBATION, Context.MODE_PRIVATE);
        Set<String> activeSet = new HashSet<>(prefs.getStringSet(KEY_PROBATION_ACTIVE_SET, Collections.<String>emptySet()));
        if (activeSet.remove(lower)) {
            prefs.edit().putStringSet(KEY_PROBATION_ACTIVE_SET, activeSet).apply();
        }
        // Mark package as verified safe in cache
        SecurityPipelineManager.markPackageSafe(context, lower);
        Log.i(TAG, "Package graduated from Visual Probation: " + lower);
        SecurityLogger.log(context, "[PROBATION_GRADUATED]", lower + " -> Passed Visual Probation. Verified Safe Tool.");
    }

    public static synchronized void revokeProbation(Context context, String packageName, String reason) {
        if (packageName == null) return;
        String lower = packageName.trim().toLowerCase();
        SharedPreferences prefs = context.getSharedPreferences(PREFS_PROBATION, Context.MODE_PRIVATE);
        Set<String> activeSet = new HashSet<>(prefs.getStringSet(KEY_PROBATION_ACTIVE_SET, Collections.<String>emptySet()));
        if (activeSet.remove(lower)) {
            prefs.edit().putStringSet(KEY_PROBATION_ACTIVE_SET, activeSet).apply();
        }
        // Permanently add to blocklist, update pipeline cache, and suspend via DPM
        addToUserBlocklist(context, lower);

        Log.w(TAG, "REVOKED PROBATION FOR: " + lower + " | Reason: " + reason);
        SecurityLogger.log(context, "[PROBATION_REVOKED]", lower + " -> Confirmed Web Browser: " + reason + " -> Permanently Suspended.");
    }
}

