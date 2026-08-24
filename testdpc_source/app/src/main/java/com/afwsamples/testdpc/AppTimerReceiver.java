package com.afwsamples.testdpc;

import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

public class AppTimerReceiver extends BroadcastReceiver {
    private static final String TAG = "AppTimerReceiver";

    public static final String ACTION_LIMIT_EXCEEDED = "com.afwsamples.testdpc.ACTION_LIMIT_EXCEEDED";
    public static final String ACTION_MIDNIGHT_RESET = "com.afwsamples.testdpc.ACTION_MIDNIGHT_RESET";
    public static final String EXTRA_PACKAGE_NAME = "extra_package_name";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }

        String action = intent.getAction();
        if (ACTION_LIMIT_EXCEEDED.equals(action)) {
            String packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME);
            Log.i(TAG, "Limit exceeded broadcast received for: " + packageName);
            if (packageName != null && !packageName.isEmpty()) {
                try {
                    DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
                    if (dpm != null && dpm.isDeviceOwnerApp(context.getPackageName())) {
                        AppTimerManager.setDailyLimitExceeded(context, packageName, true);
                        dpm.setPackagesSuspended(DeviceAdminReceiver.getComponentName(context), new String[]{packageName}, true);
                        Toast.makeText(context.getApplicationContext(), "⏳ Daily time limit reached! App locked until tomorrow.", Toast.LENGTH_LONG).show();
                        Log.i(TAG, "SUSPENDED " + packageName + " due to daily timer limit.");
                        SecurityLogger.log(context, "[DAILY_TIMER_LIMIT]", "Daily limit exceeded for [" + packageName + "] -> Locked for the remainder of today.");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error suspending package on limit exceeded", e);
                }
            }
        } else if (ACTION_MIDNIGHT_RESET.equals(action)) {
            Log.i(TAG, "Executing Midnight Reset: Clearing daily limit locks for new day...");
            AppTimerManager.clearDailyExceededFlags(context);
            AppTimerManager.registerAllObservers(context);
            SecurityLogger.log(context, "[MIDNIGHT_RESET]", "Daily app timer limits reset for new day.");
        } else if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            Log.i(TAG, "Executing Boot Check: Registering observers and checking timer limits...");
            AppTimerManager.registerAllObservers(context);
        }
    }
}
