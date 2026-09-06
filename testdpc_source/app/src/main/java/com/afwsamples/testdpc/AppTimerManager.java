package com.afwsamples.testdpc;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * =========================================================================================
 * CLASS: AppTimerManager
 * =========================================================================================
 * Purpose:
 *   Enforces daily time quotas (e.g. 15 minutes, 30 minutes, 1 hour) on high-temptation apps
 *   such as YouTube, Instagram, or Reddit. Once the daily limit is exhausted, the app is
 *   automatically suspended until midnight.
 *
 * Core Architectural Mechanisms:
 *   1. Anti-Bypass / Anti-Uninstall Lock:
 *      Whenever a timer is assigned to an app, AppTimerManager immediately invokes
 *      `dpm.setUninstallBlocked(admin, packageName, true)`. This prevents the user from
 *      uninstalling and reinstalling the app to circumvent the timer.
 *   2. Dual-Engine Time Tracking:
 *      - Local Accumulator: ImpulseGuardService tracks live foreground session durations in ms.
 *      - System UsageStatsManager: Queries Android's historical foreground statistics.
 *      - Resolves `Math.max(localUsage, usmUsage)` to guarantee zero lost seconds.
 *   3. Hardware/OS Level Usage Observer (Android 9.0+ / Pie):
 *      Uses hidden system reflection on `UsageStatsManager.registerAppUsageObserver()` to register
 *      a kernel/framework level watchdog that triggers `AppTimerReceiver.ACTION_LIMIT_EXCEEDED`
 *      the exact instant the limit is reached, without needing a continuous polling loop.
 *   4. Midnight Rollover (AlarmManager):
 *      Schedules an exact/inexact daily alarm at 00:00:02 to clear daily lockout flags and
 *      re-enable apps with a brand new daily allowance.
 * =========================================================================================
 */
public class AppTimerManager {
    // Logcat filter identifier
    private static final String TAG = "AppTimerManager";

    // SharedPreferences file name for storing app timers and daily lockout flags
    private static final String PREF_NAME = "dpclocker_app_timers";

    // Key prefixes used in SharedPreferences
    private static final String KEY_TIMER_PREFIX = "timer_min_";       // Stores configured limit in minutes
    public static final String KEY_EXCEEDED_PREFIX = "exceeded_today_"; // Stores date string (yyyyMMdd) when limit was hit
    public static final String KEY_USAGE_MILLIS_PREFIX = "usage_today_";// Stores local accumulated milliseconds

    /**
     * Helper to retrieve SharedPreferences for app timers.
     */
    public static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Returns today's calendar date formatted as "yyyyMMdd" (e.g., "20260906").
     * Used as a partition key to detect whether a lockout belongs to today or an earlier date.
     */
    private static String getTodayKey() {
        return new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
    }

    // =====================================================================================
    // SECTION 1: Daily Limit Status & Enforcement State
    // =====================================================================================

    /**
     * Checks whether an app has exceeded its daily usage quota for today.
     * Evaluates both stored lockout flags and real-time usage calculations.
     *
     * @param context     Application context
     * @param packageName Target package identifier
     * @return True if the app is currently locked out for the remainder of today.
     */
    public static boolean isDailyLimitExceeded(Context context, String packageName) {
        if (packageName == null || packageName.isEmpty()) return false;
        SharedPreferences prefs = getPrefs(context);
        String today = getTodayKey();

        // 1. Check if a lockout was already recorded today
        String savedDate = prefs.getString(KEY_EXCEEDED_PREFIX + packageName, "");
        if (today.equals(savedDate)) {
            return true;
        }

        // 2. Real-time verification: compare aggregated foreground usage against configured limit
        int limitMins = getAppLimitMinutes(context, packageName);
        if (limitMins > 0) {
            long limitMillis = limitMins * 60 * 1000L;
            long usedMillis = getTodayUsageMillis(context, packageName);
            if (usedMillis >= limitMillis) {
                // Limit reached! Record the lockout flag immediately
                setDailyLimitExceeded(context, packageName, true);
                return true;
            }
        }
        return false;
    }

    /**
     * Sets or clears the daily lockout flag for a specific package.
     *
     * @param context     Application context
     * @param packageName Target package identifier
     * @param exceeded    True to mark locked out for today; False to clear lockout.
     */
    public static void setDailyLimitExceeded(Context context, String packageName, boolean exceeded) {
        if (packageName == null || packageName.isEmpty()) return;
        SharedPreferences prefs = getPrefs(context);
        if (exceeded) {
            // Write today's date stamp (yyyyMMdd) into SharedPreferences
            prefs.edit().putString(KEY_EXCEEDED_PREFIX + packageName, getTodayKey()).apply();
            Log.i(TAG, "LOCKED FOR TODAY: App " + packageName + " marked daily limit exceeded for " + getTodayKey());
        } else {
            // Remove the flag so the app can be launched again
            prefs.edit().remove(KEY_EXCEEDED_PREFIX + packageName).apply();
            Log.i(TAG, "UNLOCKED: App " + packageName + " daily limit exceeded flag removed.");
        }
    }

    /**
     * Resets all daily usage counters and lockout flags when rolling over to a new day.
     * Called at midnight (00:00:02) by AppTimerReceiver via AlarmManager.
     */
    public static void clearDailyExceededFlags(Context context) {
        SharedPreferences prefs = getPrefs(context);
        SharedPreferences.Editor editor = prefs.edit();
        java.util.List<String> packagesToUnsuspend = new java.util.ArrayList<>();

        // Iterate through all preference keys to find exceeded flags and usage counters
        for (String key : prefs.getAll().keySet()) {
            if (key.startsWith(KEY_EXCEEDED_PREFIX)) {
                String pkg = key.substring(KEY_EXCEEDED_PREFIX.length());
                packagesToUnsuspend.add(pkg);
                editor.remove(key); // Clear lockout flag
            }
            if (key.startsWith(KEY_USAGE_MILLIS_PREFIX)) {
                editor.remove(key); // Reset recorded usage milliseconds
            }
        }
        editor.apply();
        Log.i(TAG, "Cleared daily exceeded flags and usage counters for midnight rollover.");

        // Unsuspend the apps via DevicePolicyManager for the fresh new day
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            if (dpm != null && dpm.isDeviceOwnerApp(context.getPackageName())) {
                for (String pkg : packagesToUnsuspend) {
                    // Safety check: do NOT unsuspend if app is permanently prohibited (e.g. adult app)
                    // or currently serving a temporary penalty cooldown from ImpulseGuardService
                    if (!SecurityPipelineManager.isPermanentlyProhibited(context, pkg) &&
                            !ImpulseGuardService.isTemporarilySuspended(context, pkg)) {
                        dpm.setPackagesSuspended(DeviceAdminReceiver.getComponentName(context), new String[]{pkg}, false);
                        Log.i(TAG, "Midnight rollover: Unsuspended " + pkg + " with fresh daily timer.");
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error unsuspending packages on midnight rollover", e);
        }
    }

    // =====================================================================================
    // SECTION 2: Limit Configuration & Anti-Uninstall Enforcement
    // =====================================================================================

    /**
     * Sets or clears the daily limit (in minutes) for a package.
     *
     * @param context      Application context
     * @param packageName  Target package
     * @param limitMinutes Quota in minutes (<= 0 removes timer limit)
     */
    public static void setAppLimitMinutes(Context context, String packageName, int limitMinutes) {
        SharedPreferences prefs = getPrefs(context);
        if (limitMinutes <= 0) {
            // Remove timer configuration
            prefs.edit().remove(KEY_TIMER_PREFIX + packageName).apply();
            setDailyLimitExceeded(context, packageName, false);
            // Allow user to uninstall app again since timer is removed
            setUninstallBlocked(context, packageName, false);
        } else {
            // Save timer limit
            prefs.edit().putInt(KEY_TIMER_PREFIX + packageName, limitMinutes).apply();
            // CRITICAL SECURITY: Block uninstall to prevent uninstalling & reinstalling to reset timer!
            setUninstallBlocked(context, packageName, true);
        }
        // Update the Android OS hidden UsageObserver
        updateAppObserver(context, packageName, limitMinutes);
        // Immediately enforce the new limit against today's usage
        checkAndEnforceLimits(context);
    }

    /**
     * Prevents the user from uninstalling a package by invoking DevicePolicyManager.setUninstallBlocked().
     */
    public static void setUninstallBlocked(Context context, String packageName) {
        setUninstallBlocked(context, packageName, true);
    }

    /**
     * Enterprise Device Owner call: blocks or unblocks uninstallation of the given package.
     */
    public static void setUninstallBlocked(Context context, String packageName, boolean blocked) {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            if (dpm != null && dpm.isDeviceOwnerApp(context.getPackageName())) {
                dpm.setUninstallBlocked(DeviceAdminReceiver.getComponentName(context), packageName, blocked);
                Log.i(TAG, "setUninstallBlocked for " + packageName + " = " + blocked);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in setUninstallBlocked for " + packageName, e);
        }
    }

    /**
     * Returns the configured daily limit in minutes for the given package (0 if none).
     */
    public static int getAppLimitMinutes(Context context, String packageName) {
        return getPrefs(context).getInt(KEY_TIMER_PREFIX + packageName, 0);
    }

    /**
     * Returns a map of all package names with actively configured minute limits.
     */
    public static Map<String, Integer> getAllConfiguredLimits(Context context) {
        Map<String, Integer> limits = new HashMap<>();
        SharedPreferences prefs = getPrefs(context);
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            if (entry.getKey().startsWith(KEY_TIMER_PREFIX) && entry.getValue() instanceof Integer) {
                String pkg = entry.getKey().substring(KEY_TIMER_PREFIX.length());
                int mins = (Integer) entry.getValue();
                if (mins > 0) {
                    limits.put(pkg, mins);
                }
            }
        }
        return limits;
    }

    // =====================================================================================
    // SECTION 3: Dual-Engine Time Tracking & Aggregation
    // =====================================================================================

    /**
     * Adds foreground execution time in milliseconds to local storage.
     * Called by ImpulseGuardService when an app moves out of the foreground.
     */
    public static void addForegroundUsageMillis(Context context, String packageName, long additionalMillis) {
        if (packageName == null || packageName.isEmpty() || additionalMillis <= 0) return;
        SharedPreferences prefs = getPrefs(context);
        String todayKey = getTodayKey();
        String fullKey = KEY_USAGE_MILLIS_PREFIX + todayKey + "_" + packageName;
        long current = prefs.getLong(fullKey, 0L);
        long updated = current + additionalMillis;
        prefs.edit().putLong(fullKey, updated).apply();
    }

    /**
     * Returns the locally accumulated foreground milliseconds for today.
     */
    public static long getLocalRecordedUsageMillis(Context context, String packageName) {
        if (packageName == null || packageName.isEmpty()) return 0L;
        SharedPreferences prefs = getPrefs(context);
        String todayKey = getTodayKey();
        return prefs.getLong(KEY_USAGE_MILLIS_PREFIX + todayKey + "_" + packageName, 0L);
    }

    /**
     * Calculates today's total foreground usage by taking the maximum between
     * locally recorded time and Android's UsageStatsManager data:
     *   Total Usage = Math.max(localUsage, usmUsage)
     *
     * This dual calculation eliminates gaps if an app was used while the service was rebooting.
     */
    public static long getTodayUsageMillis(Context context, String packageName) {
        long localUsage = getLocalRecordedUsageMillis(context, packageName);
        long usmUsage = getUsageStatsManagerUsage(context, packageName);
        return Math.max(localUsage, usmUsage);
    }

    /**
     * Queries Android's system UsageStatsManager for foreground runtime since 00:00:00 today.
     */
    private static long getUsageStatsManagerUsage(Context context, String packageName) {
        try {
            UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return 0L;

            // Compute midnight timestamp (00:00:00.000) of the current day
            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            long startTime = calendar.getTimeInMillis();
            long endTime = System.currentTimeMillis();

            // 1. Primary Method: High-precision queryAndAggregateUsageStats
            Map<String, UsageStats> statsMap = usm.queryAndAggregateUsageStats(startTime, endTime);
            if (statsMap != null && statsMap.containsKey(packageName)) {
                UsageStats stats = statsMap.get(packageName);
                if (stats != null && stats.getTotalTimeInForeground() > 0) {
                    return stats.getTotalTimeInForeground();
                }
            }

            // 2. Fallback Method: Standard daily interval queryUsageStats
            List<UsageStats> statsList = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime);
            if (statsList != null) {
                long totalTime = 0;
                for (UsageStats stats : statsList) {
                    if (packageName.equals(stats.getPackageName())) {
                        if (stats.getTotalTimeInForeground() > totalTime) {
                            totalTime = stats.getTotalTimeInForeground();
                        }
                    }
                }
                return totalTime;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error querying usage stats for " + packageName, e);
        }
        return 0L;
    }

    // =====================================================================================
    // SECTION 4: Active Limit Verification & Suspension
    // =====================================================================================

    /**
     * Evaluates all configured app timers against today's aggregated usage.
     * If an app has reached or exceeded its limit:
     *   - Marks daily limit exceeded.
     *   - Suspends the app package via DPM.
     * If an app is within its quota and not otherwise prohibited:
     *   - Ensures the app package remains unsuspended.
     */
    public static void checkAndEnforceLimits(Context context) {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            if (dpm == null || !dpm.isDeviceOwnerApp(context.getPackageName())) {
                return;
            }

            Map<String, Integer> limits = getAllConfiguredLimits(context);
            for (Map.Entry<String, Integer> entry : limits.entrySet()) {
                String pkg = entry.getKey();
                int limitMins = entry.getValue();
                long limitMillis = limitMins * 60 * 1000L;
                long usedMillis = getTodayUsageMillis(context, pkg);

                // Enforce uninstall-blocked status to prevent bypasses
                setUninstallBlocked(context, pkg, true);

                boolean exceededToday = isDailyLimitExceeded(context, pkg);

                Log.i(TAG, "App " + pkg + " used " + (usedMillis / 60000) + " mins today / limit " + limitMins + " mins | ExceededToday=" + exceededToday);

                // Check if limit exceeded or previously flagged today
                if (usedMillis >= limitMillis || exceededToday) {
                    setDailyLimitExceeded(context, pkg, true);
                    dpm.setPackagesSuspended(DeviceAdminReceiver.getComponentName(context), new String[]{pkg}, true);
                    Log.i(TAG, "EXCEEDED LIMIT: Suspended package " + pkg + " for the remainder of today.");
                } else {
                    // Only unsuspend if NOT prohibited by security policy and NOT serving a temporary penalty
                    if (!SecurityPipelineManager.isPermanentlyProhibited(context, pkg) &&
                            !ImpulseGuardService.isTemporarilySuspended(context, pkg)) {
                        dpm.setPackagesSuspended(DeviceAdminReceiver.getComponentName(context), new String[]{pkg}, false);
                        Log.i(TAG, "Limit not exceeded: App " + pkg + " active.");
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in checkAndEnforceLimits", e);
        }
    }

    // =====================================================================================
    // SECTION 5: Framework UsageStatsObserver Registration (Hidden Reflection API)
    // =====================================================================================

    /**
     * Registers an OS-level UsageObserver via Java Reflection on hidden Android Enterprise APIs.
     * On Android 9.0 (API 28) and above, UsageStatsManager supports registering observer triggers
     * that fire a PendingIntent broadcast when a specific usage duration is reached.
     *
     * @param context      Application context
     * @param packageName  Target package
     * @param limitMinutes Quota in minutes
     */
    public static void updateAppObserver(Context context, String packageName, int limitMinutes) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
                if (usm == null) return;

                // Derive a unique integer ID from the package name hash code
                int observerId = Math.abs(packageName.hashCode());
                if (limitMinutes <= 0) {
                    // Unregister existing observer via reflection
                    try {
                        Method method = UsageStatsManager.class.getMethod("unregisterAppUsageObserver", int.class);
                        method.invoke(usm, observerId);
                        Log.i(TAG, "Unregistered usage observer for " + packageName);
                    } catch (Exception e) {
                        Log.w(TAG, "Reflection unregisterAppUsageObserver failed: " + e.getMessage());
                    }
                } else {
                    // Construct PendingIntent targeting AppTimerReceiver with ACTION_LIMIT_EXCEEDED
                    Intent intent = new Intent(context, AppTimerReceiver.class);
                    intent.setAction(AppTimerReceiver.ACTION_LIMIT_EXCEEDED);
                    intent.putExtra(AppTimerReceiver.EXTRA_PACKAGE_NAME, packageName);
                    PendingIntent pendingIntent = PendingIntent.getBroadcast(
                            context,
                            observerId,
                            intent,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                    );

                    // Invoke hidden system method:
                    // UsageStatsManager.registerAppUsageObserver(int observerId, String[] packages, long timeLimit, TimeUnit timeUnit, PendingIntent callbackIntent)
                    try {
                        Method method = UsageStatsManager.class.getMethod(
                                "registerAppUsageObserver",
                                int.class,
                                String[].class,
                                long.class,
                                TimeUnit.class,
                                PendingIntent.class
                        );
                        method.invoke(usm, observerId, new String[]{packageName}, (long) limitMinutes, TimeUnit.MINUTES, pendingIntent);
                        Log.i(TAG, "Registered UsageObserver via reflection for " + packageName + " limit " + limitMinutes + " mins");
                    } catch (Exception e) {
                        Log.w(TAG, "Reflection registerAppUsageObserver failed: " + e.getMessage());
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error registering UsageObserver for " + packageName, e);
            }
        }
    }

    /**
     * Iterates over all configured app limits and re-registers their UsageObservers.
     * Called on device boot (ACTION_BOOT_COMPLETED) and after midnight resets.
     */
    public static void registerAllObservers(Context context) {
        Map<String, Integer> limits = getAllConfiguredLimits(context);
        for (Map.Entry<String, Integer> entry : limits.entrySet()) {
            updateAppObserver(context, entry.getKey(), entry.getValue());
        }
        scheduleMidnightReset(context);
        checkAndEnforceLimits(context);
    }

    // =====================================================================================
    // SECTION 6: Daily Midnight Alarm Scheduling
    // =====================================================================================

    /**
     * Schedules a recurring AlarmManager broadcast set to wake up the system at 00:00:02
     * each night to fire ACTION_MIDNIGHT_RESET.
     */
    public static void scheduleMidnightReset(Context context) {
        try {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarmManager == null) return;

            // Intent fired at midnight
            Intent intent = new Intent(context, AppTimerReceiver.class);
            intent.setAction(AppTimerReceiver.ACTION_MIDNIGHT_RESET);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context,
                    99999, // Unique request code for midnight reset alarm
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            // Calculate tomorrow at 00:00:02
            Calendar midnight = Calendar.getInstance();
            midnight.add(Calendar.DAY_OF_YEAR, 1);
            midnight.set(Calendar.HOUR_OF_DAY, 0);
            midnight.set(Calendar.MINUTE, 0);
            midnight.set(Calendar.SECOND, 2);
            midnight.set(Calendar.MILLISECOND, 0);

            // Inexact repeating alarm to conserve battery while guaranteeing nightly reset
            alarmManager.setInexactRepeating(
                    AlarmManager.RTC_WAKEUP,
                    midnight.getTimeInMillis(),
                    AlarmManager.INTERVAL_DAY,
                    pendingIntent
            );
            Log.i(TAG, "Scheduled Midnight Reset Alarm for " + midnight.getTime());
        } catch (Exception e) {
            Log.e(TAG, "Error scheduling midnight reset", e);
        }
    }
}

