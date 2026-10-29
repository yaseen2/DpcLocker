/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import com.afwsamples.testdpc.common.OnBackPressedHandler;
import com.afwsamples.testdpc.policy.PolicyManagementFragment;
import com.afwsamples.testdpc.search.PolicySearchFragment;

/**
 * An entry activity that shows a profile setup fragment if the app is not a profile or device
 * owner. Otherwise, a policy management fragment is shown.
 */
public class PolicyManagementActivity extends Activity implements
        FragmentManager.OnBackStackChangedListener {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Always enforce core security watchdogs when activity is launched, even if UI lock is active
        SecurityPipelineManager.enforceCoreDeviceOwnerTamperRestrictions(this);
        BrowserBlocker.initLauncherAppsCallback(this);
        BrowserBlocker.scanAndSuspendAllBrowsers(this);
        AppTimerManager.registerAllObservers(this);
        ChromePolicyManager.enforceDefaultChromePolicies(this);
        NotoriousAppBlocker.scanAndSuspendAllNotoriousApps(this);
        ImpulseGuardService.ensureAccessibilityServiceEnabled(this);

        int lockEnabled = android.provider.Settings.Global.getInt(getContentResolver(), "dpclocker_enabled", 1);
        if (lockEnabled == 1) {
            android.widget.Toast.makeText(this, "Protection Active! Connect via USB ADB to unlock.", android.widget.Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        android.app.admin.DevicePolicyManager dpm = (android.app.admin.DevicePolicyManager) getSystemService(android.content.Context.DEVICE_POLICY_SERVICE);
        boolean isOwner = (dpm != null) && (dpm.isDeviceOwnerApp(getPackageName()) || dpm.isProfileOwnerApp(getPackageName()));
        if (!isOwner) {
            startActivity(new android.content.Intent(this, SetupManagementActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_main);
        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction().add(R.id.container,
                    new PolicyManagementFragment(),
                    PolicyManagementFragment.FRAGMENT_TAG).commit();
        }
        getFragmentManager().addOnBackStackChangedListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        int lockEnabled = android.provider.Settings.Global.getInt(getContentResolver(), "dpclocker_enabled", 1);
        if (lockEnabled == 1) {
            android.widget.Toast.makeText(this, "Protection Active! Connect via USB ADB to unlock.", android.widget.Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.policy_management_menu, menu);
        return true;
    }

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_lock_testdpc:
                showLockTestDpcConfirmationDialog(this);
                return true;
            case R.id.action_show_search:
                getFragmentManager()
                        .beginTransaction()
                        .replace(R.id.container, PolicySearchFragment.newInstance())
                        .addToBackStack("search")
                        .commit();
                break;
            case android.R.id.home:
                getFragmentManager().popBackStack();
                break;
        }
        return false;
    }

    /**
     * Shows a confirmation dialog to lock TestDPC one-way from inside the app.
     * Unlocking remains strictly via PC ADB (DpcLocker.bat).
     */
    public static void showLockTestDpcConfirmationDialog(final Activity activity) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        new android.app.AlertDialog.Builder(activity)
                .setTitle("🔒 Lock TestDPC Now?")
                .setMessage("Once locked, TestDPC will immediately close and cannot be opened on the phone.\n\n"
                        + "You will need your PC (DpcLocker.bat) to unlock it again.")
                .setPositiveButton("🔒 Lock Now", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        lockTestDpcNow(activity);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Enforces all active protections, writes dpclocker_enabled = 1 to Settings.Global,
     * sends the user to the Home screen, and removes TestDPC from Recent Apps while
     * keeping ImpulseGuardService alive in the background.
     */
    public static void lockTestDpcNow(final Activity activity) {
        if (activity == null) {
            return;
        }
        try {
            // 1. Re-enforce all active Device Owner & Chrome policies before locking
            SecurityPipelineManager.enforceCoreDeviceOwnerTamperRestrictions(activity);
            ChromePolicyManager.enforceDefaultChromePolicies(activity);
            ImpulseGuardService.ensureAccessibilityServiceEnabled(activity);
        } catch (Throwable ignored) {
        }

        // 2. Best-effort self-grant of WRITE_SECURE_SETTINGS via DevicePolicyManager
        try {
            android.app.admin.DevicePolicyManager dpm =
                    (android.app.admin.DevicePolicyManager) activity.getSystemService(android.content.Context.DEVICE_POLICY_SERVICE);
            if (dpm != null && dpm.isDeviceOwnerApp(activity.getPackageName())) {
                dpm.setPermissionGrantState(
                        DeviceAdminReceiver.getComponentName(activity),
                        activity.getPackageName(),
                        android.Manifest.permission.WRITE_SECURE_SETTINGS,
                        android.app.admin.DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED);
            }
        } catch (Throwable ignored) {
        }

        // 3. Write dpclocker_enabled = 1 to Settings.Global
        boolean lockedSuccessfully = false;
        try {
            lockedSuccessfully = android.provider.Settings.Global.putInt(
                    activity.getContentResolver(), "dpclocker_enabled", 1);
        } catch (SecurityException se) {
            android.util.Log.e("PolicyManagementActivity", "Missing WRITE_SECURE_SETTINGS to set dpclocker_enabled", se);
        }

        int verifyLock = android.provider.Settings.Global.getInt(
                activity.getContentResolver(), "dpclocker_enabled", 0);
        if (!lockedSuccessfully && verifyLock != 1) {
            android.widget.Toast.makeText(
                    activity,
                    "⚠️ Permission required: Run 'adb shell pm grant com.afwsamples.testdpc android.permission.WRITE_SECURE_SETTINGS' once on PC.",
                    android.widget.Toast.LENGTH_LONG).show();
            return;
        }

        try {
            SecurityLogger.log(activity, "DPC_LOCKED_IN_APP", "User locked TestDPC via in-app lock button.");
        } catch (Throwable ignored) {
        }

        android.widget.Toast.makeText(
                activity,
                "🔒 TestDPC Locked! Connect via PC ADB to unlock.",
                android.widget.Toast.LENGTH_LONG).show();

        // 4. Navigate to Home screen and remove TestDPC task from Recents
        try {
            android.content.Intent homeIntent = new android.content.Intent(android.content.Intent.ACTION_MAIN);
            homeIntent.addCategory(android.content.Intent.CATEGORY_HOME);
            homeIntent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(homeIntent);
        } catch (Throwable ignored) {
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            activity.finishAndRemoveTask();
        } else {
            activity.finish();
        }
    }

    @Override
    public void onBackStackChanged() {
        // Show the up button in actionbar if back stack has any entry.
        getActionBar().setDisplayHomeAsUpEnabled(
                getFragmentManager().getBackStackEntryCount() > 0);
    }

    @Override
    public void onBackPressed() {
        Fragment currFragment = getFragmentManager().findFragmentById(R.id.container);
        boolean onBackPressHandled = false;
        if (currFragment != null && currFragment instanceof OnBackPressedHandler) {
            onBackPressHandled = ((OnBackPressedHandler) currFragment).onBackPressed();
        }
        if (!onBackPressHandled) {
            super.onBackPressed();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getFragmentManager().removeOnBackStackChangedListener(this);
    }

}
