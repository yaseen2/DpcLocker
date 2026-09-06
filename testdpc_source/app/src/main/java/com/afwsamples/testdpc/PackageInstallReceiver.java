package com.afwsamples.testdpc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

/**
 * =========================================================================================
 * CLASS: PackageInstallReceiver
 * =========================================================================================
 * Purpose:
 *   Listens for system-wide application installation and update broadcast events
 *   (ACTION_PACKAGE_ADDED and ACTION_PACKAGE_REPLACED).
 *
 * Role in Adult Protection System:
 *   When an end-user attempts to bypass restrictions by installing a new unmonitored browser,
 *   a high-risk adult app, or a sideloaded APK, the Android OS broadcasts these intents.
 *   This receiver intercepts the event in real-time and routes the package name to the
 *   unified SecurityPipelineManager.
 *
 * Execution Flow:
 *   1. Android OS fires android.intent.action.PACKAGE_ADDED / PACKAGE_REPLACED.
 *   2. onReceive() inspects the Intent action and URI data payload.
 *   3. Extracts the package identifier (e.g. "org.mozilla.firefox").
 *   4. Filters out self-events (ignores "com.afwsamples.testdpc").
 *   5. Dispatches to SecurityPipelineManager.onPackageAddedOrUpdated() for:
 *      - Static AI App Audit (AiAppAuditor)
 *      - Blocklist / Timer policy enforcement (BrowserBlocker / NotoriousAppBlocker / AppTimerManager)
 * =========================================================================================
 */
public class PackageInstallReceiver extends BroadcastReceiver {
    // Tag used for Android Logcat output filtering
    private static final String TAG = "PackageInstallReceiver";

    /**
     * Called whenever a matching broadcast intent is delivered to this receiver.
     *
     * @param context The Context in which the receiver is running.
     * @param intent  The Intent being received, containing the action and package URI.
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        // Step 1: Defensive validation - verify intent and its action are non-null
        if (intent == null || intent.getAction() == null) {
            return; // Abort if invalid or malformed broadcast
        }

        // Step 2: Retrieve the exact action string broadcast by Android OS
        String action = intent.getAction();

        // Step 3: Check if the event is a brand new app installation or an existing app update/reinstallation
        if (Intent.ACTION_PACKAGE_ADDED.equals(action) || Intent.ACTION_PACKAGE_REPLACED.equals(action)) {
            // Android package broadcasts deliver the target package as a data URI (e.g., "package:com.example.app")
            Uri data = intent.getData();
            if (data != null) {
                // Extract the scheme-specific part which strips "package:" and leaves "com.example.app"
                String packageName = data.getSchemeSpecificPart();

                // Step 4: Validate package name and avoid processing TestDPC itself (self-loop prevention)
                if (packageName != null && !packageName.isEmpty() && !packageName.startsWith("com.afwsamples.testdpc")) {
                    Log.i(TAG, "PackageInstallReceiver routing package event to pipeline: " + packageName);

                    // Step 5: Route to unified security pipeline to audit permissions, category, and enforce restrictions
                    SecurityPipelineManager.onPackageAddedOrUpdated(context, packageName);
                }
            }
        }
    }
}

