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
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class AppTimerManager {
    private static final String TAG = "AppTimerManager";
    private static final String PREF_NAME = "dpclocker_app_timers";
    private static final String KEY_TIMER_PREFIX = "timer_min_";

    public static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static void setAppLimitMinutes(Context context, String packageName, int limitMinutes) {
        SharedPreferences prefs = getPrefs(context);
        if (limitMinutes <= 0) {
            prefs.edit().remove(KEY_TIMER_PREFIX + packageName).apply();
        } else {
            prefs.edit().putInt(KEY_TIMER_PREFIX + packageName, limitMinutes).apply();
        }
        updateAppObserver(context, packageName, limitMinutes);
        checkAndEnforceLimits(context);
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

    public static long getTodayUsageMillis(Context context, String packageName) {
        try {
            UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return 0;

            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            long startTime = calendar.getTimeInMillis();
            long endTime = System.currentTimeMillis();

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
        return 0;
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

                Log.i(TAG, "App " + pkg + " used " + (usedMillis / 60000) + " mins today / limit " + limitMins + " mins");

                if (usedMillis >= limitMillis) {
                    dpm.setPackagesSuspended(DeviceAdminReceiver.getComponentName(context), new String[]{pkg}, true);
                    Log.i(TAG, "EXCEEDED LIMIT: Suspended package " + pkg);
                } else {
                    // Unsuspend if limit not exceeded (e.g. new day or increased limit)
                    dpm.setPackagesSuspended(DeviceAdminReceiver.getComponentName(context), new String[]{pkg}, false);
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
