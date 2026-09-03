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

public class AppTimerManager {
    private static final String TAG = "AppTimerManager";
    private static final String PREF_NAME = "dpclocker_app_timers";
    private static final String KEY_TIMER_PREFIX = "timer_min_";
    public static final String KEY_EXCEEDED_PREFIX = "exceeded_today_";

    public static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    private static String getTodayKey() {
        return new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
    }

    public static boolean isDailyLimitExceeded(Context context, String packageName) {
        if (packageName == null || packageName.isEmpty()) return false;
        SharedPreferences prefs = getPrefs(context);
        String today = getTodayKey();
        String savedDate = prefs.getString(KEY_EXCEEDED_PREFIX + packageName, "");
        if (today.equals(savedDate)) {
            return true;
        }

        // Also check real-time usage vs limit
        int limitMins = getAppLimitMinutes(context, packageName);
        if (limitMins > 0) {
            long limitMillis = limitMins * 60 * 1000L;
            long usedMillis = getTodayUsageMillis(context, packageName);
            if (usedMillis >= limitMillis) {
                setDailyLimitExceeded(context, packageName, true);
                return true;
            }
        }
        return false;
    }

    public static void setDailyLimitExceeded(Context context, String packageName, boolean exceeded) {
        if (packageName == null || packageName.isEmpty()) return;
        SharedPreferences prefs = getPrefs(context);
        if (exceeded) {
            prefs.edit().putString(KEY_EXCEEDED_PREFIX + packageName, getTodayKey()).apply();
            Log.i(TAG, "LOCKED FOR TODAY: App " + packageName + " marked daily limit exceeded for " + getTodayKey());
        } else {
            prefs.edit().remove(KEY_EXCEEDED_PREFIX + packageName).apply();
            Log.i(TAG, "UNLOCKED: App " + packageName + " daily limit exceeded flag removed.");
        }
    }

    public static void clearDailyExceededFlags(Context context) {
        SharedPreferences prefs = getPrefs(context);
        SharedPreferences.Editor editor = prefs.edit();
        java.util.List<String> packagesToUnsuspend = new java.util.ArrayList<>();

        for (String key : prefs.getAll().keySet()) {
            if (key.startsWith(KEY_EXCEEDED_PREFIX)) {
                String pkg = key.substring(KEY_EXCEEDED_PREFIX.length());
                packagesToUnsuspend.add(pkg);
                editor.remove(key);
            }
            if (key.startsWith(KEY_USAGE_MILLIS_PREFIX)) {
                editor.remove(key);
            }
        }
        editor.apply();
        Log.i(TAG, "Cleared daily exceeded flags and usage counters for midnight rollover.");

        // Unsuspend timed-out apps for the fresh new day
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            if (dpm != null && dpm.isDeviceOwnerApp(context.getPackageName())) {
                for (String pkg : packagesToUnsuspend) {
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

    public static void setAppLimitMinutes(Context context, String packageName, int limitMinutes) {
        SharedPreferences prefs = getPrefs(context);
        if (limitMinutes <= 0) {
            prefs.edit().remove(KEY_TIMER_PREFIX + packageName).apply();
            setDailyLimitExceeded(context, packageName, false);
            setUninstallBlocked(context, packageName, false);
        } else {
            prefs.edit().putInt(KEY_TIMER_PREFIX + packageName, limitMinutes).apply();
            setUninstallBlocked(context, packageName, true);
        }
        updateAppObserver(context, packageName, limitMinutes);
        checkAndEnforceLimits(context);
    }

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

    public static int getAppLimitMinutes(Context context, String packageName) {
        return getPrefs(context).getInt(KEY_TIMER_PREFIX + packageName, 0);
    }

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

    public static final String KEY_USAGE_MILLIS_PREFIX = "usage_today_";

    public static void addForegroundUsageMillis(Context context, String packageName, long additionalMillis) {
        if (packageName == null || packageName.isEmpty() || additionalMillis <= 0) return;
        SharedPreferences prefs = getPrefs(context);
        String todayKey = getTodayKey();
        String fullKey = KEY_USAGE_MILLIS_PREFIX + todayKey + "_" + packageName;
        long current = prefs.getLong(fullKey, 0L);
        long updated = current + additionalMillis;
        prefs.edit().putLong(fullKey, updated).apply();
    }

    public static long getLocalRecordedUsageMillis(Context context, String packageName) {
        if (packageName == null || packageName.isEmpty()) return 0L;
        SharedPreferences prefs = getPrefs(context);
        String todayKey = getTodayKey();
        return prefs.getLong(KEY_USAGE_MILLIS_PREFIX + todayKey + "_" + packageName, 0L);
    }

    public static long getTodayUsageMillis(Context context, String packageName) {
        long localUsage = getLocalRecordedUsageMillis(context, packageName);
        long usmUsage = getUsageStatsManagerUsage(context, packageName);
        return Math.max(localUsage, usmUsage);
    }

    private static long getUsageStatsManagerUsage(Context context, String packageName) {
        try {
            UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return 0L;

            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            long startTime = calendar.getTimeInMillis();
            long endTime = System.currentTimeMillis();

            // 1. Primary: High-precision queryAndAggregateUsageStats
            Map<String, UsageStats> statsMap = usm.queryAndAggregateUsageStats(startTime, endTime);
            if (statsMap != null && statsMap.containsKey(packageName)) {
                UsageStats stats = statsMap.get(packageName);
                if (stats != null && stats.getTotalTimeInForeground() > 0) {
                    return stats.getTotalTimeInForeground();
                }
            }

            // 2. Fallback: queryUsageStats interval
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

                // Enforce un-uninstallable state for apps with active timers
                setUninstallBlocked(context, pkg, true);

                boolean exceededToday = isDailyLimitExceeded(context, pkg);

                Log.i(TAG, "App " + pkg + " used " + (usedMillis / 60000) + " mins today / limit " + limitMins + " mins | ExceededToday=" + exceededToday);

                if (usedMillis >= limitMillis || exceededToday) {
                    setDailyLimitExceeded(context, pkg, true);
                    dpm.setPackagesSuspended(DeviceAdminReceiver.getComponentName(context), new String[]{pkg}, true);
                    Log.i(TAG, "EXCEEDED LIMIT: Suspended package " + pkg + " for the remainder of today.");
                } else {
                    // Only unsuspend if NOT prohibited by security policy and NOT under temporary visual penalty
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

    public static void updateAppObserver(Context context, String packageName, int limitMinutes) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
                if (usm == null) return;

                int observerId = Math.abs(packageName.hashCode());
                if (limitMinutes <= 0) {
                    try {
                        Method method = UsageStatsManager.class.getMethod("unregisterAppUsageObserver", int.class);
                        method.invoke(usm, observerId);
                        Log.i(TAG, "Unregistered usage observer for " + packageName);
                    } catch (Exception e) {
                        Log.w(TAG, "Reflection unregisterAppUsageObserver failed: " + e.getMessage());
                    }
                } else {
                    Intent intent = new Intent(context, AppTimerReceiver.class);
                    intent.setAction(AppTimerReceiver.ACTION_LIMIT_EXCEEDED);
                    intent.putExtra(AppTimerReceiver.EXTRA_PACKAGE_NAME, packageName);
                    PendingIntent pendingIntent = PendingIntent.getBroadcast(
                            context,
                            observerId,
                            intent,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                    );

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

    public static void registerAllObservers(Context context) {
        Map<String, Integer> limits = getAllConfiguredLimits(context);
        for (Map.Entry<String, Integer> entry : limits.entrySet()) {
            updateAppObserver(context, entry.getKey(), entry.getValue());
        }
        scheduleMidnightReset(context);
        checkAndEnforceLimits(context);
    }

    public static void scheduleMidnightReset(Context context) {
        try {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarmManager == null) return;

            Intent intent = new Intent(context, AppTimerReceiver.class);
            intent.setAction(AppTimerReceiver.ACTION_MIDNIGHT_RESET);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context,
                    99999,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            Calendar midnight = Calendar.getInstance();
            midnight.add(Calendar.DAY_OF_YEAR, 1);
            midnight.set(Calendar.HOUR_OF_DAY, 0);
            midnight.set(Calendar.MINUTE, 0);
            midnight.set(Calendar.SECOND, 2);
            midnight.set(Calendar.MILLISECOND, 0);

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
