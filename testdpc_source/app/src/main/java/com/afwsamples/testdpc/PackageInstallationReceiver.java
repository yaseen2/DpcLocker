/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.afwsamples.testdpc;

import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import java.util.Set;

/**
 * Proactively listens for newly installed apps and automatically suspends/greys them out
 * if their package name is in the Blocked Apps & Package Blocklist.
 */
public class PackageInstallationReceiver extends BroadcastReceiver {

    private static final String TAG = "PackageInstallReceiver";
    public static final String PREFS_BLOCKLIST = "proactive_package_blocklist";
    public static final String KEY_BLOCKED_PACKAGES = "blocked_packages_set";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }

        String action = intent.getAction();
        if (Intent.ACTION_PACKAGE_ADDED.equals(action) || Intent.ACTION_PACKAGE_REPLACED.equals(action)) {
            Uri data = intent.getData();
            if (data == null) return;

            String packageName = data.getSchemeSpecificPart();
            if (packageName == null || packageName.isEmpty() || packageName.startsWith("com.afwsamples.testdpc")) {
                return;
            }

            Log.i(TAG, "New package installed/replaced: " + packageName + ". Checking proactive blocklist...");
            enforceProactiveBlockIfBlacklisted(context, packageName);
        }
    }

    public static void enforceProactiveBlockIfBlacklisted(Context context, String packageName) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_BLOCKLIST, Context.MODE_PRIVATE);
            Set<String> blockedSet = prefs.getStringSet(KEY_BLOCKED_PACKAGES, null);

            if (blockedSet != null && blockedSet.contains(packageName.toLowerCase())) {
                DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
                ComponentName admin = DeviceAdminReceiver.getComponentName(context);

                if (dpm != null && dpm.isDeviceOwnerApp(context.getPackageName())) {
                    dpm.setPackagesSuspended(admin, new String[]{packageName}, true);
                    Log.w(TAG, "PROACTIVE BLOCK ENFORCED! Newly installed package [" + packageName + "] automatically GREYED OUT / SUSPENDED!");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error enforcing proactive block on " + packageName, e);
        }
    }
}
