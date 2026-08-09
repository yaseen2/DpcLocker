package com.afwsamples.testdpc;

import android.accessibilityservice.AccessibilityService;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.core.app.NotificationCompat;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ImpulseGuardService extends AccessibilityService {

    private static final String TAG = "ImpulseGuardService";
    private static final String CHANNEL_ID = "impulse_guard_channel";
    private static final String NOTIF_PULL_CHANNEL_ID = "impulse_guard_pull_channel";
    private static final int NOTIF_ID = 9001;
    private static final int NOTIF_PULL_ID_BASE = 9100;

    private static final long SUSPENSION_DURATION_MS = 60000L; // 60 Seconds
    private static final String PREF_SUSPENSIONS = "impulse_guard_active_suspensions";
    private static final String PREF_MONITORED_APPS = "impulse_guard_monitored_apps";
    private static final String PREF_NEVER_ASK_APPS = "impulse_guard_never_ask_apps";

    public static final String ACTION_MONITOR_APP = "com.afwsamples.testdpc.ACTION_MONITOR_APP";
    public static final String ACTION_WHITELIST_APP = "com.afwsamples.testdpc.ACTION_WHITELIST_APP";
    public static final String EXTRA_PACKAGE_NAME = "extra_package_name";

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService mBgExecutor = Executors.newSingleThreadExecutor();
    private final ConcurrentHashMap<String, String> mLastEvaluatedTextMap = new ConcurrentHashMap<>();

    private Runnable mPendingAuditRunnable;
    private Runnable mPendingScreenAuditRunnable;

    // Built-In Never-Ask System & Productivity Whitelist
    private static final Set<String> SYSTEM_WHITELIST = new HashSet<>(Arrays.asList(
            "com.afwsamples.testdpc",
            "com.android.systemui",
            "android",
            "com.android.settings",
            "com.google.android.inputmethod.latin",
            "com.google.android.apps.nexuslauncher",
            "com.android.vending",
            "com.whatsapp",
            "com.google.android.gm",
            "com.google.android.keep",
            "com.android.calculator2",
            "com.google.android.apps.maps",
            "com.google.android.apps.photos",
            "com.android.camera",
            "com.android.deskclock",
            "com.ankidroid",
            "com.duolingo"
    ));

    // Default Monitored Web Browsers
    private static final Set<String> DEFAULT_BROWSERS = new HashSet<>(Arrays.asList(
            "com.android.chrome",
            "org.mozilla.firefox",
            "com.microsoft.emmx",
            "com.opera.browser",
            "com.brave.browser",
            "com.duckduckgo.mobile.android",
            "com.sec.android.app.sbrowser"
    ));

    public static boolean isMonitoredApp(Context context, String packageName) {
        if (packageName == null || SYSTEM_WHITELIST.contains(packageName) || packageName.startsWith("com.afwsamples.testdpc")) {
            return false;
        }
        if (DEFAULT_BROWSERS.contains(packageName)) {
            return true;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREF_MONITORED_APPS, Context.MODE_PRIVATE);
        return prefs.getBoolean(packageName, false);
    }

    public static void setMonitoredApp(Context context, String packageName, boolean isMonitored) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_MONITORED_APPS, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(packageName, isMonitored).apply();
        Log.i(TAG, "App [" + packageName + "] monitored state updated to: " + isMonitored);
    }

    public static Set<String> getMonitoredApps(Context context) {
        Set<String> result = new HashSet<>(DEFAULT_BROWSERS);
        SharedPreferences prefs = context.getSharedPreferences(PREF_MONITORED_APPS, Context.MODE_PRIVATE);
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            if (entry.getValue() instanceof Boolean && (Boolean) entry.getValue()) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    public static boolean isNeverAskApp(Context context, String packageName) {
        return context.getSharedPreferences(PREF_NEVER_ASK_APPS, Context.MODE_PRIVATE).getBoolean(packageName, false);
    }

    public static void setNeverAskApp(Context context, String packageName) {
        context.getSharedPreferences(PREF_NEVER_ASK_APPS, Context.MODE_PRIVATE).edit().putBoolean(packageName, true).apply();
    }

    private void recordPackageSuspension(String packageName) {
        getSharedPreferences(PREF_SUSPENSIONS, Context.MODE_PRIVATE)
                .edit()
                .putLong(packageName, System.currentTimeMillis())
                .apply();
    }

    public static void unsuspendAllImpulseSuspendedPackages(Context context) {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            if (dpm == null || !dpm.isDeviceOwnerApp(context.getPackageName())) {
                return;
            }

            SharedPreferences prefs = context.getSharedPreferences(PREF_SUSPENSIONS, Context.MODE_PRIVATE);
            Map<String, ?> allEntries = prefs.getAll();

            for (String pkg : allEntries.keySet()) {
                try {
                    dpm.setPackagesSuspended(DeviceAdminReceiver.getComponentName(context), new String[]{pkg}, false);
                    Log.i(TAG, "UNSUSPENDED PACKAGE [" + pkg + "] via master unsuspend.");
                    GeminiGuardEngine.appendLog(context, "[" + pkg + "] Master unsuspended.");
                } catch (Exception e) {
                    Log.e(TAG, "Error unsuspending " + pkg, e);
                }
            }
            prefs.edit().clear().apply();
        } catch (Exception e) {
            Log.e(TAG, "Error in unsuspendAllImpulseSuspendedPackages", e);
        }
    }

    public void checkAndCleanExpiredSuspensions() {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
            if (dpm == null || !dpm.isDeviceOwnerApp(getPackageName())) {
                return;
            }

            SharedPreferences prefs = getSharedPreferences(PREF_SUSPENSIONS, Context.MODE_PRIVATE);
            Map<String, ?> allEntries = prefs.getAll();
            long now = System.currentTimeMillis();
            SharedPreferences.Editor editor = prefs.edit();

            boolean isEngineActive = GeminiGuardEngine.isEnabled(this);

            for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
                String pkg = entry.getKey();
                Object val = entry.getValue();
                long suspendTime = (val instanceof Long) ? (Long) val : 0L;

                if (!isEngineActive || (now - suspendTime >= SUSPENSION_DURATION_MS)) {
                    try {
                        dpm.setPackagesSuspended(DeviceAdminReceiver.getComponentName(this), new String[]{pkg}, false);
                        Log.i(TAG, "AUTO-UNSUSPENDED PACKAGE [" + pkg + "] after 60s impulse duration.");
                        GeminiGuardEngine.appendLog(this, "[" + pkg + "] Auto-unsuspended after 60-second timer.");
                    } catch (Exception e) {
                        Log.e(TAG, "Error unsuspending package " + pkg, e);
                    }
                    editor.remove(pkg);
                }
            }
            editor.apply();
        } catch (Exception e) {
            Log.e(TAG, "Error checking expired suspensions", e);
        }
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.i(TAG, "ImpulseGuardService Accessibility Service CONNECTED & RUNNING!");
        createNotificationChannels();
        checkAndCleanExpiredSuspensions();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        checkAndCleanExpiredSuspensions();

        if (!GeminiGuardEngine.isEnabled(this) || event == null) {
            return;
        }

        final CharSequence packageNameChar = event.getPackageName();
        if (packageNameChar == null) {
            return;
        }

        final String packageName = packageNameChar.toString();

        // 1. Password & Sensitive Input Field Privacy Masking
        if (isPasswordOrSensitiveNode(event)) {
            return;
        }

        // 2. System Whitelist Protection
        if (SYSTEM_WHITELIST.contains(packageName) || packageName.startsWith("com.afwsamples.testdpc") || packageName.contains("inputmethod") || packageName.contains("keyboard")) {
            return;
        }

        // 3. Check if app is in Monitored Apps list
        if (!isMonitoredApp(this, packageName)) {
            // Trigger Dynamic App Monitoring Notification Pull ONLY if user was not asked before
            if (isLikelyBrowserOrSearchApp(packageName) && !isNeverAskApp(this, packageName)) {
                triggerAppMonitoringNotificationPull(packageName);
            }
            return;
        }

        String extractedText = extractActiveText(event);
        if (extractedText != null && extractedText.trim().length() >= 3) {
            final String typedText = extractedText.trim();
            if (!typedText.equalsIgnoreCase("Search or type URL") && !typedText.equalsIgnoreCase("Search Google or type URL") &&
                    !typedText.startsWith("http://") && !typedText.startsWith("https://") && !typedText.startsWith("www.")) {
                
                if (mPendingAuditRunnable != null) {
                    mHandler.removeCallbacks(mPendingAuditRunnable);
                }

                mPendingAuditRunnable = new Runnable() {
                    @Override
                    public void run() {
                        String lastTextForPkg = mLastEvaluatedTextMap.get(packageName);
                        if (lastTextForPkg == null || !lastTextForPkg.equalsIgnoreCase(typedText)) {
                            mLastEvaluatedTextMap.put(packageName, typedText);
                            Log.d(TAG, "Captured search/input text in [" + packageName + "]: \"" + typedText + "\"");

                            mBgExecutor.execute(new Runnable() {
                                @Override
                                public void run() {
                                    evaluateAndEnforceImpulseGuard(packageName, typedText);
                                }
                            });
                        }
                    }
                };

                mHandler.postDelayed(mPendingAuditRunnable, 800);
            }
        }

        // 5. Anti-Bypass Full Screen Text Scan on Render / Scroll / Switch
        int eventType = event.getEventType();
        if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED ||
                eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {

            if (GeminiGuardEngine.isScreenGuardEnabled(this)) {
                if (mPendingScreenAuditRunnable != null) {
                    mHandler.removeCallbacks(mPendingScreenAuditRunnable);
                }

                mPendingScreenAuditRunnable = new Runnable() {
                    @Override
                    public void run() {
                        try {
                            AccessibilityNodeInfo root = getRootInActiveWindow();
                            final String screenText = extractFullScreenText(root);
                            if (screenText != null && screenText.length() >= 15) {
                                String lastScreen = mLastEvaluatedTextMap.get(packageName + "_screen");
                                if (lastScreen == null || !lastScreen.equals(screenText)) {
                                    mLastEvaluatedTextMap.put(packageName + "_screen", screenText);
                                    Log.d(TAG, "Captured Full Screen Text in [" + packageName + "]: " + screenText.substring(0, Math.min(100, screenText.length())) + "...");

                                    mBgExecutor.execute(new Runnable() {
                                        @Override
                                        public void run() {
                                            evaluateAndEnforceScreenGuard(packageName, screenText);
                                        }
                                    });
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error in screen text extraction", e);
                        }
                    }
                };

                mHandler.postDelayed(mPendingScreenAuditRunnable, 300);
            }
        }
    }

    private String extractFullScreenText(AccessibilityNodeInfo root) {
        if (root == null) return null;

        StringBuilder sb = new StringBuilder();
        collectNodeTextRecursive(root, sb, new HashSet<Integer>());

        String result = sb.toString().replaceAll("\\s+", " ").trim();
        if (result.length() > 4000) {
            result = result.substring(0, 4000);
        }
        return result;
    }

    private void collectNodeTextRecursive(AccessibilityNodeInfo node, StringBuilder sb, Set<Integer> visited) {
        if (node == null) return;

        int id = System.identityHashCode(node);
        if (visited.contains(id)) return;
        visited.add(id);

        try {
            if (node.isPassword()) return;

            CharSequence className = node.getClassName();
            if (className != null && className.toString().toLowerCase(Locale.US).contains("password")) {
                return;
            }

            CharSequence text = node.getText();
            if (text == null || text.length() == 0) {
                text = node.getContentDescription();
            }

            if (text != null && text.length() > 2) {
                String str = text.toString().trim();
                if (!str.equalsIgnoreCase("Search or type URL") && !str.startsWith("http://") && !str.startsWith("https://")) {
                    sb.append(str).append(" ");
                }
            }

            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) {
                    collectNodeTextRecursive(child, sb, visited);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void evaluateAndEnforceScreenGuard(final String packageName, String screenText) {
        if (packageName == null || SYSTEM_WHITELIST.contains(packageName) || packageName.startsWith("com.afwsamples.testdpc")) {
            return;
        }

        GeminiGuardEngine.EvaluationResult result = GeminiGuardEngine.evaluateScreenTextDetailed(this, packageName, screenText);

        if (result.isRisky) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    enforcePackageSuspension(packageName, "ADULT_SCREEN_CONTENT");
                }
            });
        }
    }

    private boolean isPasswordOrSensitiveNode(AccessibilityEvent event) {
        try {
            if (event.isPassword()) {
                return true;
            }
            AccessibilityNodeInfo source = event.getSource();
            if (source != null) {
                if (source.isPassword()) {
                    return true;
                }
                CharSequence className = source.getClassName();
                if (className != null && className.toString().toLowerCase(Locale.US).contains("password")) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private boolean isLikelyBrowserOrSearchApp(String packageName) {
        String lower = packageName.toLowerCase(Locale.US);
        return lower.contains("browser") || lower.contains("chrome") || lower.contains("firefox") ||
                lower.contains("opera") || lower.contains("search") || lower.contains("duckduckgo");
    }

    private String extractActiveText(AccessibilityEvent event) {
        if (event.getText() != null && !event.getText().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (CharSequence seq : event.getText()) {
                if (seq != null) {
                    sb.append(seq).append(" ");
                }
            }
            if (sb.length() > 0) {
                return sb.toString().trim();
            }
        }

        AccessibilityNodeInfo source = event.getSource();
        if (source != null) {
            String textFromNode = findSearchNodeText(source);
            if (textFromNode != null) {
                return textFromNode;
            }
        }
        return null;
    }

    private String findSearchNodeText(AccessibilityNodeInfo node) {
        if (node == null) {
            return null;
        }

        if (node.isEditable() || (node.getClassName() != null && node.getClassName().toString().contains("EditText"))) {
            CharSequence text = node.getText();
            if (text != null && text.length() > 0) {
                return text.toString();
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                String childText = findSearchNodeText(child);
                if (childText != null) {
                    return childText;
                }
            }
        }
        return null;
    }

    private void evaluateAndEnforceImpulseGuard(final String packageName, String typedText) {
        if (packageName == null || SYSTEM_WHITELIST.contains(packageName) || packageName.startsWith("com.afwsamples.testdpc") || packageName.equals("test.sandbox")) {
            return;
        }

        GeminiGuardEngine.EvaluationResult result = GeminiGuardEngine.evaluateTextDetailed(this, packageName, typedText);

        if (result.isRisky) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    enforcePackageSuspension(packageName, typedText);
                }
            });
        } else {
            // Sequential Phase 2: If Search Query is ALLOWED, schedule Post-Search Screen Audit after 1200ms
            if (GeminiGuardEngine.isScreenGuardEnabled(this)) {
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        triggerPostSearchScreenAudit(packageName);
                    }
                }, 400);
            }
        }
    }

    private void triggerPostSearchScreenAudit(final String packageName) {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            final String screenText = extractFullScreenText(root);
            if (screenText != null && screenText.length() >= 15) {
                Log.d(TAG, "Sequential Phase 2 Post-Search Screen Audit for [" + packageName + "]: " + screenText.substring(0, Math.min(80, screenText.length())) + "...");
                mBgExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        evaluateAndEnforceScreenGuard(packageName, screenText);
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in triggerPostSearchScreenAudit", e);
        }
    }

    private void enforcePackageSuspension(final String packageName, String typedText) {
        if (!GeminiGuardEngine.isEnabled(this) || packageName == null || SYSTEM_WHITELIST.contains(packageName) || packageName.startsWith("com.afwsamples.testdpc") || packageName.equals("test.sandbox")) {
            Log.i(TAG, "Ignoring suspension for protected or disabled package: " + packageName);
            return;
        }

        // Expiration Locking: If package is ALREADY suspended, do not reset or extend the timer!
        SharedPreferences prefs = getSharedPreferences(PREF_SUSPENSIONS, Context.MODE_PRIVATE);
        if (prefs.contains(packageName)) {
            Log.d(TAG, "Package [" + packageName + "] is ALREADY suspended. Preserving original 60s timer.");
            return;
        }

        Log.w(TAG, "SUSPENDING TARGET PACKAGE [" + packageName + "] FOR 60 SECONDS TO BREAK IMPULSE! Query: \"" + typedText + "\"");

        try {
            final DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
            if (dpm != null && dpm.isDeviceOwnerApp(getPackageName())) {
                // 1. Suspend the target app immediately
                dpm.setPackagesSuspended(DeviceAdminReceiver.getComponentName(this), new String[]{packageName}, true);
                recordPackageSuspension(packageName);
                showSuspensionNotification(packageName);

                // 2. Schedule automatic 60-second unsuspend check
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        checkAndCleanExpiredSuspensions();
                    }
                }, SUSPENSION_DURATION_MS);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to suspend package: " + packageName, e);
        }
    }

    private void triggerAppMonitoringNotificationPull(String packageName) {
        setNeverAskApp(this, packageName); // Ensure we only ask once

        String appLabel = packageName;
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);
            appLabel = pm.getApplicationLabel(ai).toString();
        } catch (Exception ignored) {
        }

        Intent monitorIntent = new Intent(this, ActionReceiver.class);
        monitorIntent.setAction(ACTION_MONITOR_APP);
        monitorIntent.putExtra(EXTRA_PACKAGE_NAME, packageName);
        PendingIntent piMonitor = PendingIntent.getBroadcast(this, packageName.hashCode(), monitorIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent whitelistIntent = new Intent(this, ActionReceiver.class);
        whitelistIntent.setAction(ACTION_WHITELIST_APP);
        whitelistIntent.putExtra(EXTRA_PACKAGE_NAME, packageName);
        PendingIntent piWhitelist = PendingIntent.getBroadcast(this, packageName.hashCode() + 1, whitelistIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NOTIF_PULL_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Monitor " + appLabel + " for Impulse Guard? 🛡️")
                .setContentText("Do you want Gemini AI to inspect search queries in " + appLabel + "?")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .addAction(android.R.drawable.ic_menu_add, "🛡️ Monitor App", piMonitor)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "⚪ Keep Whitelisted", piWhitelist)
                .setAutoCancel(true);

        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIF_PULL_ID_BASE + Math.abs(packageName.hashCode() % 100), builder.build());
        }
    }

    private void showSuspensionNotification(String packageName) {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.ic_lock_lock)
                        .setContentTitle("Impulse Guard Protection Active 🔒")
                        .setContentText("Suspended " + packageName + " for 60 seconds to break relapse impulse.")
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true);
                nm.notify(NOTIF_ID, builder.build());
            }
        } catch (Exception ignored) {
        }
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Impulse Guard Notifications", NotificationManager.IMPORTANCE_HIGH);
            NotificationChannel pullChannel = new NotificationChannel(NOTIF_PULL_CHANNEL_ID, "App Monitoring Requests", NotificationManager.IMPORTANCE_HIGH);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
                nm.createNotificationChannel(pullChannel);
            }
        }
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "ImpulseGuardService Interrupted!");
    }

    public static class ActionReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) {
                return;
            }
            String pkg = intent.getStringExtra(EXTRA_PACKAGE_NAME);
            if (pkg == null) {
                return;
            }

            if (ACTION_MONITOR_APP.equals(intent.getAction())) {
                setMonitoredApp(context, pkg, true);
                Log.i(TAG, "User chose to MONITOR app: " + pkg);
            } else if (ACTION_WHITELIST_APP.equals(intent.getAction())) {
                setMonitoredApp(context, pkg, false);
                setNeverAskApp(context, pkg);
                Log.i(TAG, "User chose to KEEP WHITELISTED app: " + pkg);
            }

            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.cancel(NOTIF_PULL_ID_BASE + Math.abs(pkg.hashCode() % 100));
            }
        }
    }
}
