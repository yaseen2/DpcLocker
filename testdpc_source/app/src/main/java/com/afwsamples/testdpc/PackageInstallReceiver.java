package com.afwsamples.testdpc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

public class PackageInstallReceiver extends BroadcastReceiver {
    private static final String TAG = "PackageInstallReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }

        String action = intent.getAction();
        if (Intent.ACTION_PACKAGE_ADDED.equals(action) || Intent.ACTION_PACKAGE_REPLACED.equals(action)) {
            Uri data = intent.getData();
            if (data != null) {
                String packageName = data.getSchemeSpecificPart();
                Log.i(TAG, "New package installed/updated: " + packageName);
                BrowserBlocker.checkAndSuspendPackage(context, packageName);
            }
        }
    }
}
