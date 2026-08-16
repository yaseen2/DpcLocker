package com.afwsamples.testdpc;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Centralized, shared configuration repository for DpcLocker / Test DPC security systems.
 * Provides unified Gemini API Key management, User Whitelist, and User Blocklist storage.
 */
public class SecurityConfig {

    private static final String TAG = "SecurityConfig";
    public static final String PREFS_NAME = "dpclocker_security_config";

    public static final String KEY_GEMINI_API_KEY = "gemini_shared_api_key";
    public static final String KEY_USER_WHITELIST = "user_whitelist_packages";
    public static final String KEY_USER_BLOCKLIST = "user_blocklist_packages";

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

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // --- Gemini API Key (Single Shared Vault) ---

    public static String getGeminiApiKey(Context context) {
        // Fallback to legacy prefs if shared key not yet set
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

        // Synchronize legacy stores for backwards compatibility
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
            // Default user whitelist seed
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
        Set<String> set = getUserWhitelist(context);
        set.add(packageName.trim().toLowerCase());
        getPrefs(context).edit().putStringSet(KEY_USER_WHITELIST, set).apply();
        Log.i(TAG, "Added package to User Whitelist: " + packageName);
    }

    public static void removeFromUserWhitelist(Context context, String packageName) {
        if (packageName == null) return;
        Set<String> set = getUserWhitelist(context);
        set.remove(packageName.trim().toLowerCase());
        getPrefs(context).edit().putStringSet(KEY_USER_WHITELIST, set).apply();
        Log.i(TAG, "Removed package from User Whitelist: " + packageName);
    }

    public static boolean isWhitelisted(Context context, String packageName) {
        if (packageName == null || packageName.isEmpty()) return true;
        String lower = packageName.trim().toLowerCase();
        if (SYSTEM_CORE_PACKAGES.contains(lower) || lower.startsWith("com.afwsamples.testdpc") ||
                lower.startsWith("com.android.") || lower.startsWith("com.google.android.")) {
            return true;
        }
        Set<String> userWhitelist = getUserWhitelist(context);
        return userWhitelist.contains(lower);
    }

    // --- User Blocklist ---

    public static Set<String> getUserBlocklist(Context context) {
        return NotoriousAppBlocker.getBlockedPackages(context);
    }

    public static boolean isBlocklisted(Context context, String packageName) {
        return NotoriousAppBlocker.isPackageBlocked(context, packageName);
    }
}
