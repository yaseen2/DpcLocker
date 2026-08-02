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

public class DpcLockerService extends AccessibilityService {

    private static final String TARGET_PACKAGE = "com.afwsamples.testdpc";
    private static final String CHANNEL_ID = "dpclocker_channel";
    private long lastActionTime = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForegroundNotification();
    }

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
                .setOngoing(true)
                .build();

        startForeground(1001, notification);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) {
            return;
        }

        int lockEnabled = Settings.Global.getInt(getContentResolver(), "dpclocker_enabled", 1);
        if (lockEnabled == 0) {
            return;
        }

        CharSequence pkg = event.getPackageName();
        if (pkg == null) {
            return;
        }

        String pkgName = pkg.toString();
        boolean shouldBlock = false;

        // 1. Block Test DPC app
        if (TARGET_PACKAGE.equals(pkgName)) {
            shouldBlock = true;
        } 
        // 2. Block ONLY the detail toggle screen where "Use DPC Locker" is displayed
        else if ("com.android.settings".equals(pkgName)) {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText("Use DPC Locker");
                if (nodes != null && !nodes.isEmpty()) {
                    shouldBlock = true;
                }
                root.recycle();
            }
        }

        if (shouldBlock) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastActionTime > 1000) {
                lastActionTime = currentTime;

                Intent homeIntent = new Intent(Intent.ACTION_MAIN);
                homeIntent.addCategory(Intent.CATEGORY_HOME);
                homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(homeIntent);

                performGlobalAction(GLOBAL_ACTION_HOME);

                Toast.makeText(getApplicationContext(), "Protection Active! Connect via USB ADB to unlock.", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onInterrupt() {
    }
}
