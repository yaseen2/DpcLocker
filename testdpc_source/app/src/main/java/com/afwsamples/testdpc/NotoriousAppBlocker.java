package com.afwsamples.testdpc;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.Set;

public class NotoriousAppBlocker {
    private static final String TAG = "NotoriousAppBlocker";
    private static final String PREF_NAME = "dpclocker_notorious_blocker";

    public static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static Set<String> getBlockedPackages(Context context) {
        return SecurityConfig.getUserBlocklist(context);
    }

    public static void addPackageToBlocklist(Context context, String packageName) {
        SecurityConfig.addToUserBlocklist(context, packageName);
    }

    public static void removePackageFromBlocklist(Context context, String packageName) {
        SecurityConfig.removeFromUserBlocklist(context, packageName);
    }

    public static boolean isPackageBlocked(Context context, String packageName) {
        return SecurityConfig.isBlocklisted(context, packageName);
    }

    public static void checkAndSuspendNotoriousPackage(Context context, String packageName) {
        if (packageName == null) return;
        if (isPackageBlocked(context, packageName)) {
            try {
                DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
                if (dpm != null && dpm.isDeviceOwnerApp(context.getPackageName())) {
                    dpm.setPackagesSuspended(DeviceAdminReceiver.getComponentName(context), new String[]{packageName}, true);
                    dpm.setUninstallBlocked(DeviceAdminReceiver.getComponentName(context), packageName, false);
                    SecurityPipelineManager.markPackageBlocked(context, packageName);
                    Log.i(TAG, "AUTO-BLOCKED NOTORIOUS APP INSTALLED: " + packageName);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error suspending notorious package " + packageName, e);
            }
        }
    }

    public static void scanAndSuspendAllNotoriousApps(Context context) {
        Set<String> blockedPackages = getBlockedPackages(context);
        for (String pkg : blockedPackages) {
            checkAndSuspendNotoriousPackage(context, pkg);
        }
    }
}
