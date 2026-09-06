package com.custom.dpclocker;

import android.accessibilityservice.AccessibilityService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;
import java.util.List;

/**
 * =========================================================================================
 * CLASS: DpcLockerService (Companion Watchdog App)
 * =========================================================================================
 * Purpose:
 *   An independent, companion Android Accessibility Service designed to prevent self-tampering,
 *   bypasses, and unauthorized reconfiguration of TestDPC (the Device Owner app).
 *
 * Why an Independent Companion App?
 *   If the primary Device Owner app (TestDPC) were opened, an impulsive user could modify
 *   policies, disable URL blacklists, turn off accessibility protection, or remove Device Admin.
 *   Furthermore, Android Accessibility Services can normally be disabled inside Android Settings.
 *   DpcLocker runs as a separate package ("com.custom.dpclocker") to act as a mutual watchdog:
 *     1. It completely blocks the user from opening the TestDPC application interface.
 *     2. It blocks the user from accessing the specific Android Settings sub-screen that toggles
 *        "DPC Locker" off.
 *
 * Emergency / Intentional Unlock Mechanism:
 *   To safely reconfigure the phone, the user must connect the device to a computer via USB
 *   debugging and execute the ADB command:
 *     adb shell settings put global dpclocker_enabled 0
 *   This sets the Global Setting "dpclocker_enabled" to 0, immediately lifting the block.
 * =========================================================================================
 */
public class DpcLockerService extends AccessibilityService {

    // Target package that DPC Locker shields from direct touch interaction
    private static final String TARGET_PACKAGE = "com.afwsamples.testdpc";

    // Android Notification Channel ID for running as an ongoing foreground service
    private static final String CHANNEL_ID = "dpclocker_channel";

    // Timestamp tracker used to debounce home-redirections and avoid infinite event loops
    private long lastActionTime = 0;

    /**
     * Service Lifecycle: Called when the accessibility service is first created and bound by Android.
     */
    @Override
    public void onCreate() {
        super.onCreate();
        // 1. Create low-priority notification channel for Android 8.0+ (Oreo and above)
        createNotificationChannel();
        // 2. Elevate service to foreground status to avoid process termination by Android LMK (Low Memory Killer)
        startForegroundNotification();
    }

    /**
     * Creates an Android Notification Channel with IMPORTANCE_MIN to ensure the persistent
     * notification remains silent and unobtrusive in the status bar.
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "DPC Locker Protection",
                    NotificationManager.IMPORTANCE_MIN
            );
            channel.setDescription("Running active protection for Test DPC");
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    /**
     * Starts the foreground service notification.
     * Running in the foreground ensures high process priority (oom_adj score), keeping the
     * watchdog alive continuously.
     */
    private void startForegroundNotification() {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        Notification notification = builder
                .setContentTitle("DPC Locker Active")
                .setContentText("Protecting Test DPC from unauthorized access")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setOngoing(true) // Cannot be swiped away by the user
                .build();

        // 1001 is the unique notification ID for DPC Locker
        startForeground(1001, notification);
    }

    /**
     * Main event hook: Fired whenever window state changes, views are clicked, or text changes.
     *
     * @param event The accessibility event dispatched by Android OS.
     */
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Defensive check: ignore null events
        if (event == null) {
            return;
        }

        // ---------------------------------------------------------------------------------
        // CHECK 1: ADB Global Kill-Switch Check
        // ---------------------------------------------------------------------------------
        // Reads 'Settings.Global.dpclocker_enabled' (defaults to 1 = locked).
        // If changed to 0 via ADB ('adb shell settings put global dpclocker_enabled 0'), bypass locks.
        int lockEnabled = Settings.Global.getInt(getContentResolver(), "dpclocker_enabled", 1);
        if (lockEnabled == 0) {
            return; // Locker is temporarily deactivated via authorized USB debugging
        }

        // Extract package name of the active foreground window
        CharSequence pkg = event.getPackageName();
        if (pkg == null) {
            return;
        }

        String pkgName = pkg.toString();
        boolean shouldBlock = false;

        // ---------------------------------------------------------------------------------
        // CHECK 2: Is the user trying to open Test DPC?
        // ---------------------------------------------------------------------------------
        if (TARGET_PACKAGE.equals(pkgName)) {
            // Unconditionally block access to Test DPC UI
            shouldBlock = true;
        } 
        // ---------------------------------------------------------------------------------
        // CHECK 3: Is the user in Android Settings trying to toggle off DPC Locker?
        // ---------------------------------------------------------------------------------
        else if ("com.android.settings".equals(pkgName)) {
            // Inspect the active view tree inside Android Settings
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                // Search for the specific toggle switch label "Use DPC Locker"
                List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText("Use DPC Locker");
                if (nodes != null && !nodes.isEmpty()) {
                    // User navigated into Accessibility -> DPC Locker detail screen to turn it off!
                    shouldBlock = true;
                }
                // Memory management: always recycle AccessibilityNodeInfo to avoid memory leaks
                root.recycle();
            }
        }

        // ---------------------------------------------------------------------------------
        // ENFORCEMENT ACTION: Kick user back to Android Home screen
        // ---------------------------------------------------------------------------------
        if (shouldBlock) {
            long currentTime = System.currentTimeMillis();
            // Debounce by 1000ms (1 second) to prevent hammering intents if multiple events fire in a burst
            if (currentTime - lastActionTime > 1000) {
                lastActionTime = currentTime;

                // Method A: Standard Android Intent to launch home launcher
                Intent homeIntent = new Intent(Intent.ACTION_MAIN);
                homeIntent.addCategory(Intent.CATEGORY_HOME);
                homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(homeIntent);

                // Method B: Direct Accessibility Global Action HOME (instant hardware-level navigation)
                performGlobalAction(GLOBAL_ACTION_HOME);

                // Toast notification explaining how to re-gain authorized access
                Toast.makeText(getApplicationContext(), "Protection Active! Connect via USB ADB to unlock.", Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Called when the system wants to interrupt the accessibility feedback (e.g. user audio/touch interrupt).
     */
    @Override
    public void onInterrupt() {
        // No-op for this watcher service
    }
}

