package com.afwsamples.testdpc;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Centralized, shared configuration repository for DpcLocker / Test DPC security systems.
 * Provides unified Gemini API Key management, User Whitelist, and User Proactive Blocklist storage.
 */
public class SecurityConfig {

    private static final String TAG = "SecurityConfig";
    public static final String PREFS_NAME = "dpclocker_security_config";

    public static final String KEY_GEMINI_API_KEY = "gemini_shared_api_key";
    public static final String KEY_USER_WHITELIST = "user_whitelist_packages";
    public static final String KEY_USER_BLOCKLIST = "user_blocklist_packages";

    // Legacy Preference Names for complete backwards and UI cross-compatibility
    public static final String PREFS_PROACTIVE_BLOCKLIST = "proactive_package_blocklist";
    public static final String KEY_PROACTIVE_SET = "blocked_packages_set";
    public static final String PREFS_NOTORIOUS = "dpclocker_notorious_blocker";
    public static final String KEY_NOTORIOUS_SET = "blocked_packages";

    // Core System Packages that are always inherently safe
    private static final Set<String> SYSTEM_CORE_PACKAGES = new HashSet<>(Arrays.asList(
            "com.afwsamples.testdpc",
            "com.android.systemui",
            "android",
            "com.android.settings",
            "com.google.android.inputmethod.latin",
            "com.google.android.apps.nexuslauncher",
            "com.android.vending",
            "com.android.chrome",
            "com.google.android.googlequicksearchbox",
            "com.google.android.gm",
            "com.google.android.apps.maps",
            "com.google.android.apps.photos",
            "com.android.calculator2",
            "com.android.deskclock",
            "com.whatsapp"
    ));

    private static final String[] DEFAULT_NOTORIOUS_PACKAGES = new String[]{
            "com.twitter.android",          // X / Twitter
            "com.twitter.android.lite",     // X Lite
            "com.reddit.frontpage",         // Reddit
            "com.tumblr",                   // Tumblr
            "org.telegram.messenger",       // Telegram
            "org.telegram.messenger.web",   // Telegram Web
            "org.telegram.plus",            // Telegram Plus
            "com.google.android.youtube",   // Official YouTube App
            "com.zhiliaoapp.musically",     // TikTok
            "com.zhiliaoapp.musically.go",  // TikTok Lite
            "com.ss.android.ugc.trill",     // TikTok Regional
            "com.ss.android.ugc.aweme"      // Douyin
    };

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // --- Gemini API Key (Single Shared Vault) ---

    public static String getGeminiApiKey(Context context) {
        SharedPreferences prefs = getPrefs(context);
        if (prefs.contains(KEY_GEMINI_API_KEY)) {
            return prefs.getString(KEY_GEMINI_API_KEY, "").trim();
        }

        // Legacy fallback 1: gemini_guard_engine_prefs
        String legacyKey1 = context.getSharedPreferences("gemini_guard_engine_prefs", Context.MODE_PRIVATE)
                .getString("gemini_api_key", "").trim();
        if (!legacyKey1.isEmpty()) {
            setGeminiApiKey(context, legacyKey1);
            return legacyKey1;
        }

        // Legacy fallback 2: ai_app_auditor_prefs
        String legacyKey2 = context.getSharedPreferences("ai_app_auditor_prefs", Context.MODE_PRIVATE)
                .getString("gemini_api_key", "").trim();
        if (!legacyKey2.isEmpty()) {
            setGeminiApiKey(context, legacyKey2);
            return legacyKey2;
        }

        return "";
    }

    public static void setGeminiApiKey(Context context, String apiKey) {
        String trimmed = (apiKey != null) ? apiKey.trim() : "";
        getPrefs(context).edit().putString(KEY_GEMINI_API_KEY, trimmed).apply();

        // Synchronize legacy stores
        context.getSharedPreferences("gemini_guard_engine_prefs", Context.MODE_PRIVATE)
                .edit().putString("gemini_api_key", trimmed).apply();
        context.getSharedPreferences("ai_app_auditor_prefs", Context.MODE_PRIVATE)
                .edit().putString("gemini_api_key", trimmed).apply();

        Log.i(TAG, "Unified Gemini API Key updated across all security subsystems.");
    }

    // --- User Whitelist (UI Managed) ---

    public static Set<String> getUserWhitelist(Context context) {
        SharedPreferences prefs = getPrefs(context);
        if (!prefs.contains(KEY_USER_WHITELIST)) {
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

    public static void addToUserWhitelist(Context context, String packageName) {
        if (packageName == null || packageName.trim().isEmpty()) return;
        String lower = packageName.trim().toLowerCase();
        Set<String> set = getUserWhitelist(context);
        set.add(lower);
        getPrefs(context).edit().putStringSet(KEY_USER_WHITELIST, set).apply();
        
        // Remove from blocklists if whitelisted
        removeFromUserBlocklist(context, lower);
        Log.i(TAG, "Added package to User Whitelist: " + lower);
    }

    public static void removeFromUserWhitelist(Context context, String packageName) {
        if (packageName == null) return;
        String lower = packageName.trim().toLowerCase();
        Set<String> set = getUserWhitelist(context);
        set.remove(lower);
        getPrefs(context).edit().putStringSet(KEY_USER_WHITELIST, set).apply();
        Log.i(TAG, "Removed package from User Whitelist: " + lower);
    }

    public static boolean isWhitelisted(Context context, String packageName) {
        if (packageName == null || packageName.isEmpty()) return true;
        String lower = packageName.trim().toLowerCase();
        if (SYSTEM_CORE_PACKAGES.contains(lower) || lower.startsWith("com.afwsamples.testdpc") ||
                lower.startsWith("com.android.") || lower.startsWith("com.google.android.")) {
            // Exceptions for YouTube and Google restart detector
            if ("com.google.android.youtube".equals(lower)) {
                return false;
            }
            return true;
        }
        Set<String> userWhitelist = getUserWhitelist(context);
        return userWhitelist.contains(lower);
    }

    // --- Unified User Proactive Blocklist ---

    public static Set<String> getUserBlocklist(Context context) {
        Set<String> result = new HashSet<>(Arrays.asList(DEFAULT_NOTORIOUS_PACKAGES));

        // 1. Load from primary config
        SharedPreferences primaryPrefs = getPrefs(context);
        Set<String> userSet = primaryPrefs.getStringSet(KEY_USER_BLOCKLIST, null);
        if (userSet != null) {
            result.addAll(userSet);
        }

        // 2. Merge from proactive_package_blocklist (UI input)
        SharedPreferences proactivePrefs = context.getSharedPreferences(PREFS_PROACTIVE_BLOCKLIST, Context.MODE_PRIVATE);
        Set<String> proactiveSet = proactivePrefs.getStringSet(KEY_PROACTIVE_SET, null);
        if (proactiveSet != null) {
            result.addAll(proactiveSet);
        }

        // 3. Merge from notorious prefs
        SharedPreferences notoriousPrefs = context.getSharedPreferences(PREFS_NOTORIOUS, Context.MODE_PRIVATE);
        Set<String> notoriousSet = notoriousPrefs.getStringSet(KEY_NOTORIOUS_SET, null);
        if (notoriousSet != null) {
            result.addAll(notoriousSet);
        }

        return result;
    }

    public static void addToUserBlocklist(Context context, String packageName) {
        if (packageName == null || packageName.trim().isEmpty()) return;
        String lower = packageName.trim().toLowerCase();

        Set<String> blocklist = getUserBlocklist(context);
        blocklist.add(lower);

        // Sync across all stores
        getPrefs(context).edit().putStringSet(KEY_USER_BLOCKLIST, blocklist).apply();
        context.getSharedPreferences(PREFS_PROACTIVE_BLOCKLIST, Context.MODE_PRIVATE)
                .edit().putStringSet(KEY_PROACTIVE_SET, blocklist).apply();
        context.getSharedPreferences(PREFS_NOTORIOUS, Context.MODE_PRIVATE)
                .edit().putStringSet(KEY_NOTORIOUS_SET, blocklist).apply();

        // Mark blocked in cache
        SecurityPipelineManager.markPackageBlocked(context, lower);

        // Immediate device suspension if installed
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

    public static void removeFromUserBlocklist(Context context, String packageName) {
        if (packageName == null) return;
        String lower = packageName.trim().toLowerCase();

        Set<String> blocklist = getUserBlocklist(context);
        blocklist.remove(lower);

        // Sync across all stores
        getPrefs(context).edit().putStringSet(KEY_USER_BLOCKLIST, blocklist).apply();
        context.getSharedPreferences(PREFS_PROACTIVE_BLOCKLIST, Context.MODE_PRIVATE)
                .edit().putStringSet(KEY_PROACTIVE_SET, blocklist).apply();
        context.getSharedPreferences(PREFS_NOTORIOUS, Context.MODE_PRIVATE)
                .edit().putStringSet(KEY_NOTORIOUS_SET, blocklist).apply();

        // Mark safe in cache
        SecurityPipelineManager.markPackageSafe(context, lower);

        // Unsuspend if installed
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName admin = DeviceAdminReceiver.getComponentName(context);
            if (dpm != null && dpm.isDeviceOwnerApp(context.getPackageName())) {
                dpm.setPackagesSuspended(admin, new String[]{lower}, false);
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not unsuspend package: " + lower);
        }

        Log.i(TAG, "Removed package from Unified Proactive Blocklist: " + lower);
        SecurityLogger.log(context, "[PROACTIVE_BLOCK_REMOVE]", lower + " -> Removed from Proactive Blocklist");
    }

    public static boolean isBlocklisted(Context context, String packageName) {
        if (packageName == null || packageName.isEmpty()) return false;
        String lower = packageName.trim().toLowerCase();

        // Whitelist always overrides
        if (isWhitelisted(context, lower)) {
            return false;
        }

        Set<String> blocklist = getUserBlocklist(context);
        return blocklist.contains(lower);
    }
}
