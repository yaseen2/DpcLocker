package com.afwsamples.testdpc;

import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

/**
 * =========================================================================================
 * CLASS: AppTimerReceiver
 * =========================================================================================
 * Purpose:
 *   Handles alarm manager broadcasts and system lifecycle intents related to app usage limits:
 *   1. ACTION_LIMIT_EXCEEDED: Triggered by Android UsageStatsManager / UsageStatsObserver
 *      when an app (such as YouTube, Instagram, or Reddit) reaches its daily usage threshold.
 *   2. ACTION_MIDNIGHT_RESET: Triggered at 00:00:00 each night by AlarmManager to clear daily
 *      locks and reset usage allowances for the new calendar day.
 *   3. ACTION_BOOT_COMPLETED: Triggered upon device reboot to re-register all usage observers
 *      and restore any persistent lock state.
 *
 * Security & Enterprise Role:
 *   Leverages DevicePolicyManager (DPM) enterprise privileges (Device Owner mode) to call
 *   `setPackagesSuspended(admin, packages, true)`. Suspending a package disables its launcher
 *   icon, greys it out, and blocks the user from launching it.
 * =========================================================================================
 */
public class AppTimerReceiver extends BroadcastReceiver {
    // Logcat tag for app timer lifecycle diagnostics
    private static final String TAG = "AppTimerReceiver";

    // Intent action fired when UsageStatsObserver trips a registered time threshold
    public static final String ACTION_LIMIT_EXCEEDED = "com.afwsamples.testdpc.ACTION_LIMIT_EXCEEDED";

    // Intent action scheduled via AlarmManager to trigger nightly at 10:00 PM (22:00:00)
    public static final String ACTION_DAILY_RESET = "com.afwsamples.testdpc.ACTION_DAILY_RESET";

    // Backwards compatibility alias for midnight reset
    public static final String ACTION_MIDNIGHT_RESET = "com.afwsamples.testdpc.ACTION_MIDNIGHT_RESET";

    // Key for extracting the target package name from the broadcast's Intent extras
    public static final String EXTRA_PACKAGE_NAME = "extra_package_name";

    /**
     * Primary entry point when a broadcast intent matching this receiver is received.
     *
     * @param context The application or system context.
     * @param intent  The broadcast intent with action and extras.
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        // Defensive check: abort if intent or action is missing
        if (intent == null || intent.getAction() == null) {
            return;
        }

        String action = intent.getAction();

        // ---------------------------------------------------------------------------------
        // CASE 1: App Usage Limit Reached (ACTION_LIMIT_EXCEEDED)
        // ---------------------------------------------------------------------------------
        if (ACTION_LIMIT_EXCEEDED.equals(action)) {
            // Extract the target package that exceeded its daily allowed time
            String packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME);
            Log.i(TAG, "Limit exceeded broadcast received for: " + packageName);

            if (packageName != null && !packageName.isEmpty()) {
                String cleanPkg = packageName.trim().toLowerCase( java.util.Locale.US );
                int limitMins = AppTimerManager.getAppLimitMinutes(context, packageName);
                long usedMillis = AppTimerManager.getTodayUsageMillis(context, packageName);
                if (SecurityConfig.IMMUTABLE_CORE_PACKAGES.contains(cleanPkg) ||
                        limitMins <= 0 ||
                        (!AppTimerManager.isDailyLimitExceeded(context, packageName) && usedMillis < (limitMins * 60000L))) {
                    Log.w(TAG, "Ignoring unverified or exempt ACTION_LIMIT_EXCEEDED for: " + packageName);
                    return;
                }
                try {
                    // Obtain DevicePolicyManager system service
                    DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);

                    // Ensure TestDPC has Device Owner permissions before invoking enterprise APIs
                    if (dpm != null && dpm.isDeviceOwnerApp(context.getPackageName())) {
                        // 1. Record in SharedPreferences that this app is locked out for today
                        AppTimerManager.setDailyLimitExceeded(context, packageName, true);

                        // 2. Hardware/OS level suspension: greys out the app and blocks launching
                        dpm.setPackagesSuspended(DeviceAdminReceiver.getComponentName(context), new String[]{packageName}, true);

                        // 3. User feedback: notify user why the application was suddenly locked
                        Toast.makeText(context.getApplicationContext(), "⏳ Daily time limit reached! App locked until 10:00 PM.", Toast.LENGTH_LONG).show();

                        Log.i(TAG, "SUSPENDED " + packageName + " due to daily timer limit.");

                        // 4. Audit trail logging to persistent security log
                        SecurityLogger.log(context, "[DAILY_TIMER_LIMIT]", "Daily limit exceeded for [" + packageName + "] -> Locked until 10:00 PM.");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error suspending package on limit exceeded", e);
                }
            }
        }
        // ---------------------------------------------------------------------------------
        // CASE 2: Daily Reset (10:00 PM) (ACTION_DAILY_RESET / ACTION_MIDNIGHT_RESET)
        // ---------------------------------------------------------------------------------
        else if (ACTION_DAILY_RESET.equals(action) || ACTION_MIDNIGHT_RESET.equals(action)) {
            Log.i(TAG, "Executing Daily Reset (10:00 PM): Clearing daily limit locks for new cycle...");

            // 1. Clear SharedPreferences lockout flags and un-suspend apps via DPM
            AppTimerManager.clearDailyExceededFlags(context);

            // 2. Re-register UsageStatsManager observers with fresh daily time quotas
            AppTimerManager.registerAllObservers(context);

            // 3. User feedback toast
            Toast.makeText(context.getApplicationContext(), "🌙 10:00 PM Reset: App timers refreshed for the night!", Toast.LENGTH_SHORT).show();

            // 4. Record reset event in security log
            SecurityLogger.log(context, "[DAILY_RESET_10PM]", "Daily app timer limits reset at 10:00 PM for new cycle.");
        }
        // ---------------------------------------------------------------------------------
        // CASE 3: Device Reboot Completed (ACTION_BOOT_COMPLETED)
        // ---------------------------------------------------------------------------------
        else if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            Log.i(TAG, "Executing Boot Check: Registering observers and checking timer limits...");

            // Ensure any missed 10:00 PM rollover while powered off executes before enforcing quotas
            AppTimerManager.ensureDailyCycleRolledOver(context);
            // System observers are wiped from RAM on reboot; re-register all app quotas immediately
            AppTimerManager.registerAllObservers(context);
            // Re-enforce Chrome enterprise policies on boot
            ChromePolicyManager.enforceDefaultChromePolicies(context);
        }
        // ---------------------------------------------------------------------------------
        // CASE 4: Direct Enterprise Policy Enforcement & Sync Intent
        // ---------------------------------------------------------------------------------
        else if ("com.afwsamples.testdpc.ACTION_ENFORCE_POLICIES".equals(action) ||
                "com.afwsamples.testdpc.ACTION_SYNC_POLICIES".equals(action)) {
            Log.i(TAG, "Executing Direct Policy Enforcement Broadcast...");
            ChromePolicyManager.enforceDefaultChromePolicies(context);
            AppTimerManager.ensureDailyCycleRolledOver(context);
            AppTimerManager.registerAllObservers(context);
            String auditPkg = intent.getStringExtra("extra_audit_package");
            if (auditPkg != null && !auditPkg.trim().isEmpty()) {
                Log.i(TAG, "Triggering immediate package audit for: " + auditPkg);
                SecurityPipelineManager.onPackageAddedOrUpdated(context, auditPkg.trim());
            } else {
                SecurityPipelineManager.onBootCompleted(context);
            }
        }
    }
}

