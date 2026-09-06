package com.afwsamples.testdpc;

import android.accessibilityservice.AccessibilityService;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.graphics.Bitmap;
import android.graphics.ColorSpace;
import android.hardware.HardwareBuffer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.os.Vibrator;
import android.os.VibrationEffect;
import android.widget.Toast;
import androidx.core.app.NotificationCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * =========================================================================================
 * CLASS: ImpulseGuardService
 * =========================================================================================
 * Purpose:
 *   The primary runtime enforcement engine and core Android Accessibility Service for the
 *   adult protection system.
 *
 * Major Responsibilities:
 *   1. Real-Time Application Usage Timer Enforcement:
 *      Tracks foreground execution of designated apps (YouTube, social media) to the exact
 *      second. Once the daily allowance is exhausted, immediately locks and suspends the app.
 *   2. Active Search Query Interception:
 *      Listens for typing events in browser URL bars and search boxes. If an adult query is
 *      detected, triggers immediate app suspension and returns the user to the Home screen.
 *   3. Sequential Phase 2 Post-Search Screen Text Scanner:
 *      Scans the resulting search page text via Gemini Cloud AI to catch adult content
 *      bypassing initial keyword filters.
 *   4. Falcons.ai On-Device Visual Screen Guard (API 30+):
 *      Takes hardware screenshots on visual dwell and classifies them offline with a Vision
 *      Transformer (ViT) in sub-60ms. Enforces a 3-Strike Escalation Ladder:
 *        - Strike 1: Press BACK + Warning Toast (1/2)
 *        - Strike 2: Press BACK + Double Haptic Pulse + Final Warning Toast (2/2)
 *        - Strike 3+: Enforce PenaltyManager lockout (starts at 10 minutes)
 *   5. Self-Defense & Anti-Tampering Protection:
 *      Detects when the user enters Android Settings attempting to toggle off Impulse Guard
 *      and instantly forces a GLOBAL_ACTION_BACK to prevent deactivation.
 * =========================================================================================
 */
public class ImpulseGuardService extends AccessibilityService {

    // Logcat tag for service diagnostics
    private static final String TAG = "ImpulseGuardService";

    // Notification channel and ID constants
    private static final String CHANNEL_ID = "impulse_guard_channel";
    private static final String NOTIF_PULL_CHANNEL_ID = "impulse_guard_pull_channel";
    private static final int NOTIF_ID = 9001;
    private static final int NOTIF_PULL_ID_BASE = 9100;

    // Default suspension penalty duration (60 seconds for minor infractions)
    private static final long SUSPENSION_DURATION_MS = 60000L; // 60 Seconds

    // SharedPreferences file names and keys
    private static final String PREF_SUSPENSIONS = "impulse_guard_active_suspensions";
    private static final String PREF_MONITORED_APPS = "impulse_guard_monitored_apps";
    private static final String PREF_NEVER_ASK_APPS = "impulse_guard_never_ask_apps";
    private static final String PREF_KEY_DAILY_STRIKES = "falcons_daily_strikes";
    private static final String PREF_KEY_LAST_STRIKE_DATE = "falcons_last_strike_date";

    // Timestamp tracker for debouncing rapid consecutive visual strikes
    private static long sLastStrikeTimestamp = 0;

    // =====================================================================================
    // SECTION 1: Daily Visual Strike Tracker (Falcons.ai Escalation)
    // =====================================================================================

    /**
     * Retrieves today's accumulated visual NSFW strikes.
     * Automatically resets to 0 if the calendar day has rolled over.
     */
    public static int getDailyStrikes(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("falcons_vision_guard_prefs", Context.MODE_PRIVATE);
        String today = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
        String lastDate = prefs.getString(PREF_KEY_LAST_STRIKE_DATE, "");
        // Midnight rollover check
        if (!today.equals(lastDate)) {
            prefs.edit().putInt(PREF_KEY_DAILY_STRIKES, 0).putString(PREF_KEY_LAST_STRIKE_DATE, today).apply();
            return 0;
        }
        return prefs.getInt(PREF_KEY_DAILY_STRIKES, 0);
    }

    /**
     * Increments today's visual strike count by 1 and updates the date stamp.
     *
     * @return The updated strike count for today.
     */
    public static int incrementDailyStrikes(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("falcons_vision_guard_prefs", Context.MODE_PRIVATE);
        String today = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
        String lastDate = prefs.getString(PREF_KEY_LAST_STRIKE_DATE, "");
        int currentStrikes = 0;
        if (today.equals(lastDate)) {
            currentStrikes = prefs.getInt(PREF_KEY_DAILY_STRIKES, 0);
        }
        int newStrikes = currentStrikes + 1;
        prefs.edit().putInt(PREF_KEY_DAILY_STRIKES, newStrikes).putString(PREF_KEY_LAST_STRIKE_DATE, today).apply();
        return newStrikes;
    }

    /**
     * Resets the daily strike counter to 0.
     */
    public static void resetDailyStrikes(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("falcons_vision_guard_prefs", Context.MODE_PRIVATE);
        String today = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
        prefs.edit().putInt(PREF_KEY_DAILY_STRIKES, 0).putString(PREF_KEY_LAST_STRIKE_DATE, today).apply();
    }

    // =====================================================================================
    // SECTION 2: Broadcast Action Identifiers & Background Thread Executors
    // =====================================================================================

    // Broadcast actions for user notifications and hardware diagnostics
    public static final String ACTION_MONITOR_APP = "com.afwsamples.testdpc.ACTION_MONITOR_APP";
    public static final String ACTION_WHITELIST_APP = "com.afwsamples.testdpc.ACTION_WHITELIST_APP";
    public static final String ACTION_RUN_FALCONS_DIAGNOSTICS = "com.afwsamples.testdpc.ACTION_RUN_FALCONS_DIAGNOSTICS";
    public static final String EXTRA_PACKAGE_NAME = "extra_package_name";

    // Main thread Handler for UI interactions and scheduled delays
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    // Single-thread background executor to serialize AI requests and screenshot processing
    private final ExecutorService mBgExecutor = Executors.newSingleThreadExecutor();

    // In-memory caches to deduplicate evaluation events and prevent duplicate penalties
    private final ConcurrentHashMap<String, String> mLastEvaluatedTextMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> mLastPenalizedQueryMap = new ConcurrentHashMap<>();

    // Debounce runnables for typing pauses and screen text aggregation
    private Runnable mPendingAuditRunnable;
    private Runnable mPendingScreenAuditRunnable;

    // Real-Time App Daily Usage Timer Tracking Variables
    private String mActiveTimedPackage = null;
    private long mActiveSessionStartTime = 0L;
    private final Handler mAppTimerHandler = new Handler(Looper.getMainLooper());
    private Runnable mAppTimerTickerRunnable = null;

    // =====================================================================================
    // SECTION 3: System Whitelists & Monitored Application Registries
    // =====================================================================================

    /**
     * Core system apps, keyboards, system UI, and essential utilities that are NEVER inspected.
     */
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

    /**
     * Default set of monitored web browsers and video streaming apps subjected to live inspection.
     */
    private static final Set<String> DEFAULT_BROWSERS = new HashSet<>(Arrays.asList(
            "com.android.chrome",
            "com.google.android.youtube",
            "app.revanced.android.youtube",
            "org.mozilla.firefox",
            "com.microsoft.emmx",
            "com.opera.browser",
            "com.brave.browser",
            "com.duckduckgo.mobile.android",
            "com.sec.android.app.sbrowser"
    ));

    /**
     * Determines whether an application package is currently subjected to live monitoring.
     */
    public static boolean isMonitoredApp(Context context, String packageName) {
        // System packages are exempt
        if (packageName == null || SYSTEM_WHITELIST.contains(packageName) || packageName.startsWith("com.afwsamples.testdpc")) {
            return false;
        }
        // Known browsers are monitored by default
        if (DEFAULT_BROWSERS.contains(packageName)) {
            return true;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREF_MONITORED_APPS, Context.MODE_PRIVATE);
        return prefs.getBoolean(packageName, false);
    }

    /**
     * Explicitly enables or disables monitoring for a specific application package.
     */
    public static void setMonitoredApp(Context context, String packageName, boolean isMonitored) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_MONITORED_APPS, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(packageName, isMonitored).apply();
        Log.i(TAG, "App [" + packageName + "] monitored state updated to: " + isMonitored);
    }

    /**
     * Returns the complete set of actively monitored package names.
     */
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

    /**
     * Enterprise Watchdog: Automatically restores the Accessibility Service if disabled.
     * Uses Settings.Secure writes enabled by Device Owner mode.
     */
    public static void ensureAccessibilityServiceEnabled(Context context) {
        try {
            ContentResolver cr = context.getContentResolver();
            String enabledServices = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            String targetService = context.getPackageName() + "/" + ImpulseGuardService.class.getName();
            if (enabledServices == null || !enabledServices.contains(context.getPackageName())) {
                String newServices = (enabledServices == null || enabledServices.isEmpty()) ? targetService : enabledServices + ":" + targetService;
                Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, newServices);
                Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1);
                Log.i(TAG, "Auto-enforced and restored Accessibility Service: " + newServices);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to auto-enforce accessibility service", e);
        }
    }

    /**
     * Checks if the user dismissed monitoring prompts for this app with "Keep Whitelisted".
     */
    public static boolean isNeverAskApp(Context context, String packageName) {
        return context.getSharedPreferences(PREF_NEVER_ASK_APPS, Context.MODE_PRIVATE).getBoolean(packageName, false);
    }

    /**
     * Marks an app as "Never Ask Again" so user is not prompted repeatedly.
     */
    public static void setNeverAskApp(Context context, String packageName) {
        context.getSharedPreferences(PREF_NEVER_ASK_APPS, Context.MODE_PRIVATE).edit().putBoolean(packageName, true).apply();
    }

    // =====================================================================================
    // SECTION 4: Temporary Penalty Suspensions & Expiration Cleanup
    // =====================================================================================

    /**
     * Records an active suspension penalty in SharedPreferences along with its expiration timestamp.
     */
    private void recordPackageSuspension(String packageName, long expiryTimestamp) {
        getSharedPreferences(PREF_SUSPENSIONS, Context.MODE_PRIVATE)
                .edit()
                .putLong(packageName, expiryTimestamp)
                .apply();
    }

    /**
     * Checks if an application is currently serving a temporary penalty cooldown.
     */
    public static boolean isTemporarilySuspended(Context context, String packageName) {
        if (packageName == null || packageName.isEmpty()) return false;
        SharedPreferences prefs = context.getSharedPreferences(PREF_SUSPENSIONS, Context.MODE_PRIVATE);
        long expiry = prefs.getLong(packageName, 0L);
        return expiry > System.currentTimeMillis();
    }

    /**
     * Master Unsuspend: Clears all active temporary penalties and unsuspends packages via DPM,
     * while rigorously maintaining suspensions for permanently prohibited apps and daily timer locks.
     */
    public static void unsuspendAllImpulseSuspendedPackages(Context context) {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            if (dpm == null || !dpm.isDeviceOwnerApp(context.getPackageName())) {
                return;
            }

            SharedPreferences prefs = context.getSharedPreferences(PREF_SUSPENSIONS, Context.MODE_PRIVATE);
            Map<String, ?> allEntries = prefs.getAll();

            for (String pkg : allEntries.keySet()) {
                // Safety Guard 1: Never unsuspend if permanently prohibited by security policy
                if (SecurityPipelineManager.isPermanentlyProhibited(context, pkg)) {
                    Log.i(TAG, "Preserving permanent prohibition for [" + pkg + "] during master unsuspend.");
                    continue;
                }
                // Safety Guard 2: Never unsuspend if daily app timer quota is exceeded
                if (AppTimerManager.isDailyLimitExceeded(context, pkg)) {
                    Log.i(TAG, "Preserving daily timer limit suspension for [" + pkg + "] during master unsuspend.");
                    continue;
                }
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

    /**
     * Periodic watchdog method: checks all registered temporary suspensions and unsuspends
     * apps whose penalty cooldown durations have expired.
     */
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

            boolean isEngineActive = GeminiGuardEngine.isEnabled(this) || FalconsVisionGuardEngine.isEnabled(this);

            for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
                String pkg = entry.getKey();
                Object val = entry.getValue();
                long expiryTimestamp = (val instanceof Long) ? (Long) val : 0L;

                // Legacy fallback if stored value was start timestamp instead of expiry timestamp
                if (expiryTimestamp > 0 && expiryTimestamp < (now - 120000L)) {
                    expiryTimestamp = expiryTimestamp + 60000L;
                }

                // If engine is disabled or penalty expiration timestamp has passed
                if (!isEngineActive || (now >= expiryTimestamp)) {
                    // Safety check: Never unsuspend permanently prohibited apps
                    if (SecurityPipelineManager.isPermanentlyProhibited(this, pkg)) {
                        Log.i(TAG, "Preserving permanent prohibition for [" + pkg + "] during auto-unsuspend cycle.");
                        editor.remove(pkg);
                        continue;
                    }

                    // Safety check: Never unsuspend apps that reached their daily usage limit
                    if (AppTimerManager.isDailyLimitExceeded(this, pkg)) {
                        Log.i(TAG, "Preserving daily timer limit suspension for [" + pkg + "] during auto-unsuspend cycle.");
                        editor.remove(pkg);
                        continue;
                    }

                    try {
                        // Unsuspend app via DevicePolicyManager
                        dpm.setPackagesSuspended(DeviceAdminReceiver.getComponentName(this), new String[]{pkg}, false);
                        Log.i(TAG, "AUTO-UNSUSPENDED PACKAGE [" + pkg + "] after penalty duration expired.");
                        GeminiGuardEngine.appendLog(this, "[" + pkg + "] Auto-unsuspended after penalty expiration.");
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

    // =====================================================================================
    // SECTION 5: Real-Time Application Daily Usage Timer Enforcement
    // =====================================================================================

    /**
     * Commits active foreground usage time to persistent storage when an app loses focus
     * or the device screen turns off.
     */
    public synchronized void commitActiveSessionUsage() {
        if (mActiveTimedPackage != null) {
            long now = System.currentTimeMillis();
            long elapsed = now - mActiveSessionStartTime;
            if (elapsed > 0) {
                // Add elapsed session milliseconds into AppTimerManager
                AppTimerManager.addForegroundUsageMillis(this, mActiveTimedPackage, elapsed);
                Log.i(TAG, "⏱️ Committed session usage for [" + mActiveTimedPackage + "]: " + (elapsed / 1000) + "s");
            }
            // Stop active 1-second ticker runnable
            if (mAppTimerTickerRunnable != null) {
                mAppTimerHandler.removeCallbacks(mAppTimerTickerRunnable);
                mAppTimerTickerRunnable = null;
            }
            // If session pushed total usage over limit, enforce suspension lockout immediately
            if (AppTimerManager.isDailyLimitExceeded(this, mActiveTimedPackage)) {
                enforceAppTimerLimit(mActiveTimedPackage);
            }
            mActiveTimedPackage = null;
            mActiveSessionStartTime = 0L;
        }
    }

    /**
     * Handles window focus changes for monitored app timers.
     * Transitions active tracking between apps and triggers immediate lockouts if quota is met.
     */
    private void handleAppTimerWindowStateChange(String newPkg) {
        if (newPkg == null || newPkg.isEmpty()) return;

        // Skip transient system overlays (virtual keyboards, system UI dialogs)
        if (newPkg.contains("inputmethod") || newPkg.contains("keyboard") || "com.android.systemui".equals(newPkg)) {
            return;
        }

        // If user navigated away from the currently tracked timed app, commit its elapsed time
        if (mActiveTimedPackage != null && !newPkg.equals(mActiveTimedPackage)) {
            commitActiveSessionUsage();
        }

        // Check if the newly focused app has a configured daily usage limit
        int limitMins = AppTimerManager.getAppLimitMinutes(this, newPkg);
        if (limitMins > 0) {
            // Check if app is already locked for today
            if (AppTimerManager.isDailyLimitExceeded(this, newPkg)) {
                Log.w(TAG, "🚨 Timed app [" + newPkg + "] opened but DAILY LIMIT IS EXCEEDED! Kicking to home.");
                enforceAppTimerLimit(newPkg);
                return;
            }

            long limitMillis = limitMins * 60 * 1000L;
            long usedMillis = AppTimerManager.getTodayUsageMillis(this, newPkg);

            // Check if usage already crossed limit
            if (usedMillis >= limitMillis) {
                Log.w(TAG, "🚨 Timed app [" + newPkg + "] reached limit (" + (usedMillis / 60000) + "m / " + limitMins + "m). Locking!");
                AppTimerManager.setDailyLimitExceeded(this, newPkg, true);
                enforceAppTimerLimit(newPkg);
                return;
            }

            // Start live session tracking if not already ticking for this package
            if (mActiveTimedPackage == null || !newPkg.equals(mActiveTimedPackage)) {
                mActiveTimedPackage = newPkg;
                mActiveSessionStartTime = System.currentTimeMillis();
                startAppTimerTicker(newPkg, limitMillis);
                Log.i(TAG, "⏱️ Active timer started for [" + newPkg + "] -> " + (usedMillis / 60000) + "m used / " + limitMins + "m limit");
            }
        }
    }

    /**
     * Starts a 1000ms (1-second) recurring ticker that tracks live session execution.
     * Flushes to disk every 5 seconds and triggers immediate suspension when limit is hit.
     */
    private void startAppTimerTicker(final String pkg, final long limitMillis) {
        if (mAppTimerTickerRunnable != null) {
            mAppTimerHandler.removeCallbacks(mAppTimerTickerRunnable);
        }

        mAppTimerTickerRunnable = new Runnable() {
            @Override
            public void run() {
                if (mActiveTimedPackage == null || !pkg.equals(mActiveTimedPackage)) {
                    return;
                }

                long now = System.currentTimeMillis();
                long sessionElapsed = now - mActiveSessionStartTime;

                // Periodically persist to SharedPreferences every 5 seconds to prevent data loss on sudden power-off
                if (sessionElapsed >= 5000L) {
                    AppTimerManager.addForegroundUsageMillis(ImpulseGuardService.this, pkg, sessionElapsed);
                    mActiveSessionStartTime = now;
                    sessionElapsed = 0L;
                }

                long baseUsage = AppTimerManager.getTodayUsageMillis(ImpulseGuardService.this, pkg);
                long currentTotal = baseUsage + sessionElapsed;

                // Check if total usage hits limit
                if (currentTotal >= limitMillis) {
                    Log.w(TAG, "⏱️ DAILY LIMIT HIT in real-time for [" + pkg + "] (" + (currentTotal / 60000) + "m / " + (limitMillis / 60000) + "m)!");
                    if (sessionElapsed > 0) {
                        AppTimerManager.addForegroundUsageMillis(ImpulseGuardService.this, pkg, sessionElapsed);
                    }
                    AppTimerManager.setDailyLimitExceeded(ImpulseGuardService.this, pkg, true);
                    mActiveTimedPackage = null;
                    mActiveSessionStartTime = 0L;
                    enforceAppTimerLimit(pkg);
                    return;
                }

                // Tick every 1000ms (1 second) for strict precision
                mAppTimerHandler.postDelayed(this, 1000L);
            }
        };

        mAppTimerHandler.postDelayed(mAppTimerTickerRunnable, 1000L);
    }

    /**
     * Enforces app timer lockout:
     * 1. Navigates user back to the Home screen instantly.
     * 2. Calls DevicePolicyManager.setPackagesSuspended() to grey out and lock the app icon.
     * 3. Displays an informative Toast message.
     */
    private void enforceAppTimerLimit(String pkg) {
        // 1. Kick user back to Home screen immediately
        performGlobalAction(GLOBAL_ACTION_HOME);

        // 2. Suspend package via Device Owner so icon is grayed out and cannot be launched
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
            if (dpm != null && dpm.isDeviceOwnerApp(getPackageName())) {
                dpm.setPackagesSuspended(DeviceAdminReceiver.getComponentName(this), new String[]{pkg}, true);
                Log.i(TAG, "🔒 Device Owner locked/suspended timed app: " + pkg);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error suspending package " + pkg, e);
        }

        // 3. Inform user with a high-priority Toast
        String appLabel = pkg;
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            appLabel = pm.getApplicationLabel(ai).toString();
        } catch (Exception ignored) {}

        int limitMins = AppTimerManager.getAppLimitMinutes(this, pkg);
        final String msg = "⏱️ " + appLabel + " daily limit (" + limitMins + " min) reached! Locked for today.";
        mHandler.post(() -> Toast.makeText(ImpulseGuardService.this, msg, Toast.LENGTH_LONG).show());
    }

    // =====================================================================================
    // SECTION 6: Service Lifecycle & Broadcast Receivers
    // =====================================================================================

    /**
     * Accessibility Service Lifecycle Callback: onServiceConnected
     * -----------------------------------------------------------
     * Invoked by the Android OS framework once the user or Device Owner grants
     * accessibility permissions and the service process starts up.
     *
     * Initialization duties performed here:
     * 1. Register high-priority notification channels for warnings and app monitoring pulls.
     * 2. Scan and auto-unsuspend any packages whose suspension lockout timer expired while
     *    the service was dormant or rebooting.
     * 3. Register a dynamic broadcast receiver listening for:
     *    - ACTION_MONITOR_APP: User confirmed monitoring for a newly detected browser/app.
     *    - ACTION_WHITELIST_APP: User chose to exempt an app.
     *    - ACTION_RUN_FALCONS_DIAGNOSTICS: Diagnostic trigger to benchmark ViT inference speeds.
     *    - Intent.ACTION_SCREEN_OFF: Immediately writes accumulated foreground usage to disk.
     */
    @Override
    protected void onServiceConnected() {
        // Delegate base initialization to parent AccessibilityService
        super.onServiceConnected();
        Log.i(TAG, "ImpulseGuardService Accessibility Service CONNECTED & RUNNING!");

        // Step 1: Create notification channels required for Android 8.0+ (API 26+)
        createNotificationChannels();

        // Step 2: Clean up any app suspensions whose penalty lockouts expired during downtime
        checkAndCleanExpiredSuspensions();

        // Step 3: Register broadcast receiver for runtime system signals and user actions
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_MONITOR_APP);
        filter.addAction(ACTION_WHITELIST_APP);
        filter.addAction(ACTION_RUN_FALCONS_DIAGNOSTICS);
        filter.addAction(Intent.ACTION_SCREEN_OFF);

        registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null) return;
                String action = intent.getAction();

                // Benchmarking trigger: runs on-device Falcons.ai ViT engine diagnostics in background
                if (ACTION_RUN_FALCONS_DIAGNOSTICS.equals(action)) {
                    Log.i(TAG, "Received ACTION_RUN_FALCONS_DIAGNOSTICS. Triggering hardware benchmarks...");
                    mBgExecutor.execute(() -> {
                        String report = FalconsVisionGuardEngine.runSelfDiagnostics(ImpulseGuardService.this);
                        Log.i("FalconsDiagnostic", "\n" + report);
                    });
                }
                // Screen sleep trigger: user locked device; flush active app usage seconds to disk
                else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                    Log.i(TAG, "Screen off detected: committing active session usage.");
                    commitActiveSessionUsage();
                }
            }
        }, filter);
    }

    /**
     * Service Lifecycle Callback: onDestroy
     * --------------------------------------
     * Invoked when the accessibility service is being terminated.
     * Flushes any in-flight foreground session usage to SharedPreferences
     * so that time spent immediately before teardown is not lost.
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        // Persist accumulated active session time before process termination
        commitActiveSessionUsage();
    }

    // =====================================================================================
    // SECTION 7: Core Event Pipeline - onAccessibilityEvent
    // =====================================================================================

    /**
     * Core Accessibility Event Dispatcher: onAccessibilityEvent
     * ---------------------------------------------------------
     * Called by the Android Accessibility Framework whenever a UI event matching the
     * service configuration occurs anywhere on the device (window state changes, text input,
     * scrolling, clicking, etc.).
     *
     * This method orchestrates the multi-tiered defensive pipeline in strict sequential order:
     *
     * - Phase 0: Real-Time App Usage Timers
     *   Detects foreground package transitions (TYPE_WINDOW_STATE_CHANGED) and updates daily
     *   active usage minutes. If limit reached, locks app immediately.
     *
     * - Phase 1: Settings Anti-Tamper Shield
     *   Detects if the user opens Android Settings targeting the "Impulse Guard" accessibility
     *   toggle or App Info screen. Immediately executes GLOBAL_ACTION_BACK to bounce them out.
     *
     * - Phase 2: Engine Status & Privacy Guards
     *   Bypasses processing if both Gemini AI & Falcons Vision engines are disabled, or if the
     *   active UI node is a password or sensitive input field.
     *
     * - Phase 3: System Whitelisting & Input Method Filtering
     *   Bypasses safe system apps (launchers, system UI, keyboards/IMEs, TestDPC itself).
     *
     * - Phase 4: Monitored Apps Registry & Dynamic Discovery
     *   Checks if the target app is in the monitored list. If it is an unmonitored browser
     *   or search engine, prompts the user with an interactive notification.
     *
     * - Phase 5: Search Query Inspector (GeminiGuardEngine)
     *   Extracts typed text from search bars/omniboxes. Checks local risk cache for instant
     *   0ms verdict, or debounces by 1200ms to allow typing completion before AI evaluation.
     *
     * - Phase 6: Screen Guard Full-Screen Text Audit
     *   Extracts all on-screen text via accessibility node hierarchy with a 400ms debounce
     *   and sends it to GeminiGuardEngine for classification.
     *
     * - Phase 7: Falcons.ai On-Device Visual Guard (ViT)
     *   On Android 11+ (API 30+), triggers hardware buffer screenshots during dwell periods
     *   to evaluate visual imagery with the offline Vision Transformer model.
     *
     * @param event The incoming AccessibilityEvent emitted by the Android OS framework.
     */
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Drop null events immediately to prevent NPEs
        if (event == null) return;

        // Extract the originating package name of the active app
        final CharSequence packageNameChar = event.getPackageName();
        if (packageNameChar == null) return;
        final String packageName = packageNameChar.toString();

        // Identify the exact user interaction type (window change, text typed, clicked, scrolled, etc.)
        int eventType = event.getEventType();

        // =================================================================================
        // PHASE 0: REAL-TIME APP DAILY USAGE TIMERS ENFORCEMENT
        // =================================================================================
        // Whenever the user switches apps or opens a new window, track active session duration
        // and evaluate if the daily time allowance has been exhausted.
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            handleAppTimerWindowStateChange(packageName);
        }

        // =================================================================================
        // PHASE 1: ANTI-TAMPER SHIELD (Settings Accessibility & App Info Lockout)
        // =================================================================================
        // If the user navigates into com.android.settings, inspect whether they are attempting
        // to disable Impulse Guard or force-stop TestDPC.
        if ("com.android.settings".equals(packageName)) {
            if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                    eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                    eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {

                // If the screen displays "Use Impulse Guard", "Turn off", "Disable", etc.
                if (isAccessibilitySettingsToggleScreen(event)) {
                    Log.w(TAG, "🚨 ANTI-TAMPER: User attempted to access Impulse Guard Accessibility Toggle! Bouncing back...");
                    // Eject user immediately back to the previous screen before they can tap the switch
                    performGlobalAction(GLOBAL_ACTION_BACK);
                    // Flash security notification
                    mHandler.post(() -> Toast.makeText(ImpulseGuardService.this, "🔒 Security Protected: Impulse Guard cannot be disabled.", Toast.LENGTH_SHORT).show());
                    return; // Stop event processing
                }
            }
        }

        // Housekeeping: purge any suspensions that have finished their lockout penalty duration
        checkAndCleanExpiredSuspensions();

        // Check if either inspection engine is active in SharedPreferences
        boolean geminiActive = GeminiGuardEngine.isEnabled(this);
        boolean falconsActive = FalconsVisionGuardEngine.isEnabled(this);

        // If both Gemini AI and Falcons Vision Guard are disabled, exit early to save CPU/battery
        if (!geminiActive && !falconsActive) {
            return;
        }

        // When user resumes typing or clicks elsewhere, clear the previous penalized query tracker
        // for this package so new queries won't be suppressed as duplicate violations.
        if (eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED || eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            mLastPenalizedQueryMap.remove(packageName);
        }

        // =================================================================================
        // PRIVACY SHIELD: Password & Sensitive Input Field Masking
        // =================================================================================
        // Strict privacy compliance: never inspect password boxes, PIN inputs, or secure fields
        if (isPasswordOrSensitiveNode(event)) {
            return;
        }

        // =================================================================================
        // PHASE 2: SYSTEM WHITELIST PROTECTION
        // =================================================================================
        // Exempt critical OS components (launchers, system UI), keyboards/IMEs, and TestDPC
        if (SYSTEM_WHITELIST.contains(packageName) || packageName.startsWith("com.afwsamples.testdpc") || packageName.contains("inputmethod") || packageName.contains("keyboard")) {
            return;
        }

        // =================================================================================
        // PHASE 3: MONITORED APPS REGISTRY & DYNAMIC DISCOVERY
        // =================================================================================
        // Only inspect apps that the user/admin has registered for monitoring
        if (!isMonitoredApp(this, packageName)) {
            // Dynamic Pull: If this app looks like a web browser or search engine and hasn't been
            // dismissed before, prompt user with an interactive notification asking to monitor it
            if (isLikelyBrowserOrSearchApp(packageName) && !isNeverAskApp(this, packageName)) {
                triggerAppMonitoringNotificationPull(packageName);
            }
            return; // Not monitored; bypass inspection
        }

        // =================================================================================
        // PHASE 4: STRICT TYPED SEARCH QUERY INSPECTOR (GeminiGuardEngine)
        // =================================================================================
        // Triggered only when the user types text into an input node or taps search/suggestion
        if (geminiActive) {
            boolean isTextInputEvent = (eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED);
            boolean isClickInputEvent = (eventType == AccessibilityEvent.TYPE_VIEW_CLICKED && isSearchOrInputNode(event.getSource()));

            if (isTextInputEvent || isClickInputEvent) {
                // Extract active text from the editable search node or event payload
                String extractedText = extractActiveText(event);
                if (extractedText != null && extractedText.trim().length() >= 3) {
                    final String typedText = extractedText.trim();
                    // Ignore search box default hints and direct full URLs (handled by BrowserBlocker)
                    if (!typedText.equalsIgnoreCase("Search or type URL") && !typedText.equalsIgnoreCase("Search Google or type URL") &&
                            !typedText.startsWith("http://") && !typedText.startsWith("https://") && !typedText.startsWith("www.")) {

                        // FAST-PATH: Check in-memory local cache for previously flagged risky terms
                        Boolean localCached = GeminiGuardEngine.getCachedVerdict(this, typedText);
                        if (localCached != null && localCached) {
                            // Instant 0ms lockout for cached violations
                            mBgExecutor.execute(new Runnable() {
                                @Override
                                public void run() {
                                    evaluateAndEnforceImpulseGuard(packageName, typedText);
                                }
                            });
                        } else if (isClickInputEvent) {
                            // User clicked Search or a search suggestion: evaluate immediately without debounce
                            mBgExecutor.execute(new Runnable() {
                                @Override
                                public void run() {
                                    evaluateAndEnforceImpulseGuard(packageName, typedText);
                                }
                            });
                        } else {
                            // TYPING DEBOUNCE: Wait 1200ms of user idle pause after typing stops.
                            // This ensures we evaluate the full, complete query rather than partial keystrokes.
                            if (mPendingAuditRunnable != null) {
                                mHandler.removeCallbacks(mPendingAuditRunnable);
                            }

                            mPendingAuditRunnable = new Runnable() {
                                @Override
                                public void run() {
                                    String lastTextForPkg = mLastEvaluatedTextMap.get(packageName);
                                    // Only evaluate if query differs from the last evaluated query for this app
                                    if (lastTextForPkg == null || !lastTextForPkg.equalsIgnoreCase(typedText)) {
                                        mLastEvaluatedTextMap.put(packageName, typedText);
                                        Log.d(TAG, "Captured complete search query in [" + packageName + "]: \"" + typedText + "\"");

                                        // Dispatch AI evaluation to background thread pool
                                        mBgExecutor.execute(new Runnable() {
                                            @Override
                                            public void run() {
                                                evaluateAndEnforceImpulseGuard(packageName, typedText);
                                            }
                                        });
                                    }
                                }
                            };

                            // Schedule execution after 1200ms idle pause
                            mHandler.postDelayed(mPendingAuditRunnable, 1200);
                        }
                    }
                }
            }
        }

        // =================================================================================
        // PHASE 5: ANTI-BYPASS SCREEN GUARD (Full-Screen UI Text Audit)
        // =================================================================================
        // Inspects entire on-screen text hierarchy when content changes, scrolls, or loads
        if (GeminiGuardEngine.isScreenGuardEnabled(this)) {
            if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                    eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED ||
                    eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {

                // Debounce screen audit by 400ms to allow layout settling
                if (mPendingScreenAuditRunnable != null) {
                    mHandler.removeCallbacks(mPendingScreenAuditRunnable);
                }

                mPendingScreenAuditRunnable = new Runnable() {
                    @Override
                    public void run() {
                        try {
                            // Extract full text tree from active window root node
                            AccessibilityNodeInfo root = getRootInActiveWindow();
                            final String screenText = extractFullScreenText(root);
                            if (screenText != null && screenText.length() >= 15) {
                                String lastScreen = mLastEvaluatedTextMap.get(packageName + "_screen");
                                // Deduplicate: only evaluate if on-screen text changed meaningfully
                                if (lastScreen == null || !lastScreen.equals(screenText)) {
                                    mLastEvaluatedTextMap.put(packageName + "_screen", screenText);
                                    Log.d(TAG, "Captured Full Screen Text in [" + packageName + "]: " + screenText.substring(0, Math.min(100, screenText.length())) + "...");

                                    // Run GeminiGuardEngine screen evaluation in background
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

                // Post with 400ms settling debounce
                mHandler.postDelayed(mPendingScreenAuditRunnable, 400);
            }
        }

        // =================================================================================
        // PHASE 6: ON-DEVICE FALCONS.AI VISUAL GUARD (ViT Vision Transformer)
        // =================================================================================
        // 100% offline computer vision inspection via Android 11+ (API 30+) takeScreenshot API
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && FalconsVisionGuardEngine.isEnabled(this)) {
            if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                    eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED ||
                    eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                    eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
                    eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED) {

                // Schedule visual dwell audit during user gaze pauses
                scheduleFalconsVisualDwellAudit(packageName);
            }
        }
    }

    // =====================================================================================
    // SECTION 8: Falcons.ai Hardware Screenshot & On-Device ViT Pipeline
    // =====================================================================================

    /** Debounced Runnable reference for scheduling Falcons screenshot audits */
    private Runnable mPendingFalconsVisualAuditRunnable;

    /** System timestamp of the most recent completed Falcons visual scan */
    private long mLastFalconsScanTimestamp = 0;

    /** Concurrency lock to guarantee only one screenshot capture & ViT inference runs at a time */
    private volatile boolean mIsFalconsScanInFlight = false;

    /**
     * Schedules an on-device computer vision audit using Falcons.ai Vision Transformer (ViT).
     * -------------------------------------------------------------------------------------
     * This method implements an intelligent "dwell-time" capture mechanism:
     * 1. Rate-Limiting: Guarantees at least 1500ms between consecutive visual audits to conserve battery.
     * 2. In-Flight Lock: Prevents spawning concurrent screenshots if an inference job is already executing.
     * 3. Dwell Debounce: Waits 1000ms after the user stops scrolling/clicking so the screenshot captures
     *    stable, rendered image content rather than motion blur.
     * 4. Zero-Copy Hardware Buffer: Uses Android 11+ (API 30+) `takeScreenshot()` API which provides a
     *    direct GPU `HardwareBuffer`. We downscale the buffer directly to the ViT's required 224x224
     *    input resolution BEFORE creating a software bitmap, eliminating ~10MB of memory copying lag.
     * 5. 3-Strike Escalation: If NSFW imagery is detected with confidence exceeding the threshold,
     *    increments the daily visual strike counter and triggers the progressive discipline ladder.
     *
     * @param packageName The package name of the active foreground app being audited.
     */
    private void scheduleFalconsVisualDwellAudit(final String packageName) {
        long now = System.currentTimeMillis();
        // Enforce 1500ms minimum throttle interval and check if an audit is already in flight
        if (now - mLastFalconsScanTimestamp < 1500 || mIsFalconsScanInFlight) {
            return;
        }

        // If an audit is already pending on the debounce timer, let it fire without resetting
        if (mPendingFalconsVisualAuditRunnable != null) {
            return;
        }

        mPendingFalconsVisualAuditRunnable = new Runnable() {
            @Override
            public void run() {
                // Clear the pending runnable reference upon trigger
                mPendingFalconsVisualAuditRunnable = null;
                if (mIsFalconsScanInFlight) return;
                mIsFalconsScanInFlight = true;

                Log.i(TAG, "Triggering Falcons.ai visual screenshot audit for [" + packageName + "]...");

                // Android 11 (API 30) introduced AccessibilityService.takeScreenshot()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        // Request an instantaneous, secure screenshot of the default display without overlaying chrome
                        takeScreenshot(Display.DEFAULT_DISPLAY, mBgExecutor, new TakeScreenshotCallback() {
                            @Override
                            public void onSuccess(ScreenshotResult screenshotResult) {
                                try {
                                    mLastFalconsScanTimestamp = System.currentTimeMillis();

                                    // Extract the low-level GPU hardware buffer and color space
                                    HardwareBuffer hardwareBuffer = screenshotResult.getHardwareBuffer();
                                    ColorSpace colorSpace = screenshotResult.getColorSpace();

                                    // Wrap the hardware buffer into an Android Bitmap without copying pixels
                                    Bitmap bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace);
                                    if (bitmap != null) {
                                        // High-speed downscale directly from GPU hardware buffer to 224x224.
                                        // This critically eliminates copying a 1080x2400 (10MB+) software bitmap.
                                        Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, 224, 224, false);
                                        bitmap.recycle(); // Free the native hardware buffer wrapper
                                        hardwareBuffer.close(); // Release the hardware buffer handle

                                        if (scaledBitmap != null) {
                                            // Convert the 224x224 hardware bitmap into software ARGB_8888 for TensorFlow Lite input
                                            Bitmap softwareBitmap = scaledBitmap.copy(Bitmap.Config.ARGB_8888, false);
                                            if (scaledBitmap != softwareBitmap) {
                                                scaledBitmap.recycle();
                                            }

                                            if (softwareBitmap != null) {
                                                // Optional diagnostic: save the downscaled frame to disk for developer verification
                                                try {
                                                    File debugFile = new File(getFilesDir(), "falcons_debug_last_frame.jpg");
                                                    try (FileOutputStream fos = new FileOutputStream(debugFile)) {
                                                        softwareBitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos);
                                                    }
                                                } catch (Exception ignored) {}

                                                // Run on-device ViT inference using the FalconsVisionGuardEngine
                                                FalconsVisionGuardEngine.VisionResult result =
                                                        FalconsVisionGuardEngine.evaluateBitmap(ImpulseGuardService.this, packageName, softwareBitmap);
                                                softwareBitmap.recycle(); // Immediately free software pixel memory

                                                // Evaluate AI classification verdict
                                                if (result.isNsfw) {
                                                    long now = System.currentTimeMillis();
                                                    // 5-second grace cooldown to prevent recording multiple strikes for
                                                    // the exact same screen frame while the user is reacting or exiting
                                                    if (now - sLastStrikeTimestamp < 5000) {
                                                        Log.i(TAG, "Falcons NSFW detected inside 5s grace cooldown. Skipping duplicate strike.");
                                                    } else {
                                                        sLastStrikeTimestamp = now;
                                                        // Atomically increment the persistent daily strike counter
                                                        final int strikeCount = incrementDailyStrikes(ImpulseGuardService.this);
                                                        Log.w(TAG, "🚨 FALCONS.AI VISUAL NSFW VIOLATION in [" + packageName + "] (NSFW=" + String.format(Locale.US, "%.1f%%", result.nsfwProbability * 100) + ") -> STRIKE #" + strikeCount + " TODAY!");

                                                        // Post enforcement action to the Main UI thread
                                                        mHandler.post(new Runnable() {
                                                            @Override
                                                            public void run() {
                                                                handleVisualNsfwStrike(packageName, strikeCount);
                                                            }
                                                        });
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                    Log.e(TAG, "Error processing Falcons visual screenshot", e);
                                } finally {
                                    // Release in-flight lock so subsequent audits can be scheduled
                                    mIsFalconsScanInFlight = false;
                                }
                            }

                            @Override
                            public void onFailure(int errorCode) {
                                mIsFalconsScanInFlight = false;
                                Log.w(TAG, "Falcons takeScreenshot failed with error code: " + errorCode);
                            }
                        });
                    } catch (Exception e) {
                        mIsFalconsScanInFlight = false;
                        Log.e(TAG, "Error requesting visual screenshot", e);
                    }
                } else {
                    mIsFalconsScanInFlight = false;
                }
            }
        };

        // Post the visual audit with a 1000ms debounce delay to detect user dwell time
        mHandler.postDelayed(mPendingFalconsVisualAuditRunnable, 1000);
    }

    // =====================================================================================
    // SECTION 9: Full-Screen Accessibility Text Extraction & Screen Guard
    // =====================================================================================

    /**
     * Extracts all visible text from the active accessibility node window hierarchy.
     * ----------------------------------------------------------------------------
     * Traverses the UI tree starting at the root window node, collecting text and content
     * descriptions while applying strict privacy filters and cycle protection.
     *
     * @param root The root AccessibilityNodeInfo of the active window.
     * @return Normalized string containing up to 4000 characters of screen text, or null.
     */
    private String extractFullScreenText(AccessibilityNodeInfo root) {
        if (root == null) return null;

        StringBuilder sb = new StringBuilder();
        // Use identity hash codes to detect and break recursive tree cycles
        collectNodeTextRecursive(root, sb, new HashSet<Integer>());

        // Collapse consecutive whitespace into single spaces and trim
        String result = sb.toString().replaceAll("\\s+", " ").trim();
        // Cap length at 4000 characters to prevent excessive memory and token usage
        if (result.length() > 4000) {
            result = result.substring(0, 4000);
        }
        return result;
    }

    /**
     * Recursive Depth-First Search helper to gather text from an accessibility node and its children.
     * ----------------------------------------------------------------------------------------------
     * Applies privacy safeguards:
     * - Skips password fields (`isPassword()`).
     * - Skips class names containing "password", "subtitle", or "caption".
     * - Discards URLs (`http://`, `https://`), breadcrumbs (`>>`), and standard search hints.
     *
     * @param node The current AccessibilityNodeInfo node being inspected.
     * @param sb The StringBuilder accumulating screen text.
     * @param visited Set of identity hash codes tracking already visited nodes to prevent cycles.
     */
    private void collectNodeTextRecursive(AccessibilityNodeInfo node, StringBuilder sb, Set<Integer> visited) {
        if (node == null) return;

        // Cycle prevention check using object identity hash code
        int id = System.identityHashCode(node);
        if (visited.contains(id)) return;
        visited.add(id);

        try {
            // Privacy filter: never inspect password fields
            if (node.isPassword()) return;

            // Class name filter: ignore password edit fields, video subtitles, and closed captions
            CharSequence className = node.getClassName();
            if (className != null) {
                String cls = className.toString().toLowerCase(Locale.US);
                if (cls.contains("password") || cls.contains("subtitle") || cls.contains("caption")) {
                    return;
                }
            }

            // Extract primary text, falling back to content description (accessibility labels)
            CharSequence text = node.getText();
            if (text == null || text.length() == 0) {
                text = node.getContentDescription();
            }

            // Accumulate meaningful text tokens (length > 2 characters)
            if (text != null && text.length() > 2) {
                String str = text.toString().trim();
                // Exclude breadcrumb navigation markers, browser URL placeholders, and raw web URLs
                if (!str.startsWith(">>") && !str.equalsIgnoreCase("Search or type URL") && !str.startsWith("http://") && !str.startsWith("https://")) {
                    sb.append(str).append(" ");
                }
            }

            // Recurse through all child nodes in the view hierarchy
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) {
                    collectNodeTextRecursive(child, sb, visited);
                }
            }
        } catch (Exception ignored) {
            // Suppress accessibility node recycling exceptions during active screen transitions
        }
    }

    /**
     * Evaluates full-screen text content using GeminiGuardEngine.
     * -----------------------------------------------------------
     * If the screen text contains explicit adult content or bypass patterns,
     * immediately triggers Device Owner app suspension on the target package.
     *
     * @param packageName The package name of the active app.
     * @param screenText The accumulated text visible on screen.
     */
    private void evaluateAndEnforceScreenGuard(final String packageName, String screenText) {
        // Bypass protected system packages and TestDPC
        if (packageName == null || SYSTEM_WHITELIST.contains(packageName) || packageName.startsWith("com.afwsamples.testdpc")) {
            return;
        }

        // Run full-screen semantic analysis via GeminiGuardEngine
        GeminiGuardEngine.EvaluationResult result = GeminiGuardEngine.evaluateScreenTextDetailed(this, packageName, screenText);

        // If flagged as risky adult content, schedule package suspension on UI thread
        if (result.isRisky) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    enforcePackageSuspension(packageName, "ADULT_SCREEN_CONTENT");
                }
            });
        }
    }

    /**
     * Privacy Helper: Inspects whether an accessibility event originates from a password
     * or sensitive input widget.
     *
     * @param event The incoming AccessibilityEvent.
     * @return True if the source node or event represents a password or sensitive field.
     */
    private boolean isPasswordOrSensitiveNode(AccessibilityEvent event) {
        try {
            // Check if the event itself flags password input
            if (event.isPassword()) {
                return true;
            }
            AccessibilityNodeInfo source = event.getSource();
            if (source != null) {
                // Check if the underlying UI node is flagged as a password field
                if (source.isPassword()) {
                    return true;
                }
                // Check if the class name denotes a password view
                CharSequence className = source.getClassName();
                if (className != null && className.toString().toLowerCase(Locale.US).contains("password")) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    // =====================================================================================
    // SECTION 10: UI Node Inspection, Search Detection & Anti-Tamper Classifiers
    // =====================================================================================

    /**
     * Determines whether a given AccessibilityNodeInfo represents a search bar, omnibox,
     * or user-editable text input widget.
     * ----------------------------------------------------------------------------------
     * Checks multiple node attributes:
     * 1. Direct editable flag: node.isEditable()
     * 2. View Class Name: EditText, AutoCompleteTextView
     * 3. View Resource ID: checks for keywords like "search", "url_bar", "search_box",
     *    "omnibox", "query", "search_src_text", "search_button", "search_plate".
     *
     * @param node The accessibility node to inspect.
     * @return True if the node is an editable or search-oriented view.
     */
    private boolean isSearchOrInputNode(AccessibilityNodeInfo node) {
        if (node == null) return false;
        try {
            // Check native editable flag
            if (node.isEditable()) return true;

            // Check view class hierarchy
            CharSequence className = node.getClassName();
            if (className != null) {
                String cls = className.toString().toLowerCase(Locale.US);
                if (cls.contains("edittext") || cls.contains("autocompletetextview")) {
                    return true;
                }
            }

            // Check XML resource ID name
            CharSequence viewId = node.getViewIdResourceName();
            if (viewId != null) {
                String id = viewId.toString().toLowerCase(Locale.US);
                if (id.contains("search") || id.contains("url_bar") || id.contains("search_box") ||
                        id.contains("omnibox") || id.contains("query") || id.contains("search_src_text") ||
                        id.contains("search_button") || id.contains("search_plate")) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /**
     * Heuristic classifier: checks if a package name matches common web browsers or search engines.
     * Used to trigger dynamic monitoring notifications when the user opens a new browser.
     *
     * @param packageName The package name to test.
     * @return True if the package name indicates a browser or search client.
     */
    private boolean isLikelyBrowserOrSearchApp(String packageName) {
        String lower = packageName.toLowerCase(Locale.US);
        return lower.contains("browser") || lower.contains("chrome") || lower.contains("firefox") ||
                lower.contains("opera") || lower.contains("search") || lower.contains("duckduckgo");
    }

    /**
     * Anti-Tamper Inspector: Determines if the current window is an Android Settings screen
     * attempting to disable or configure Impulse Guard's Accessibility Service.
     * -------------------------------------------------------------------------------------
     * Inspects:
     * 1. Entire window hierarchy starting from getRootInActiveWindow()
     * 2. Event's source node
     * 3. Event text payloads
     *
     * @param event The accessibility event emitted by com.android.settings.
     * @return True if the screen contains strings targeting Impulse Guard's service switches.
     */
    private boolean isAccessibilitySettingsToggleScreen(AccessibilityEvent event) {
        try {
            // Check root window node hierarchy
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null && inspectNodeForToggleScreen(root)) {
                return true;
            }
            // Check event source node
            if (event != null && event.getSource() != null && inspectNodeForToggleScreen(event.getSource())) {
                return true;
            }
            // Check event text list
            if (event != null && event.getText() != null) {
                for (CharSequence cs : event.getText()) {
                    if (cs != null && isTriggerTamperText(cs.toString())) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking settings toggle screen", e);
        }
        return false;
    }

    /**
     * Evaluates whether a text string contains tamper triggers targeting Impulse Guard.
     * Matches phrases like "use impulse guard", "turn off impulse guard", "disable impulse guard", etc.
     *
     * @param text The string to test.
     * @return True if the text represents an anti-tamper trigger.
     */
    private boolean isTriggerTamperText(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase(Locale.US);
        return lower.contains("use impulse guard") ||
                lower.contains("stop impulse guard") ||
                lower.contains("turn off impulse guard") ||
                lower.contains("disable impulse guard") ||
                (lower.contains("impulse guard") && lower.contains("shortcut")) ||
                (lower.contains("impulse guard") && lower.contains("app info"));
    }

    /**
     * Recursive DFS helper to inspect an accessibility node and its children for tamper triggers.
     *
     * @param node The current node to inspect.
     * @return True if this node or any child contains tamper triggers.
     */
    private boolean inspectNodeForToggleScreen(AccessibilityNodeInfo node) {
        if (node == null) return false;

        // Check node primary text
        CharSequence text = node.getText();
        if (text != null && isTriggerTamperText(text.toString())) {
            return true;
        }

        // Check node accessibility content description
        CharSequence desc = node.getContentDescription();
        if (desc != null && isTriggerTamperText(desc.toString())) {
            return true;
        }

        // Recurse through all child nodes
        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                if (inspectNodeForToggleScreen(child)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Filters out non-query placeholder strings, hints, breadcrumbs, and raw web URLs.
     *
     * @param str The candidate text string.
     * @return True if the string represents an actual user search term.
     */
    private boolean isMeaningfulSearchText(String str) {
        if (str == null || str.length() < 3) return false;
        String lower = str.toLowerCase(Locale.US);

        // Filter out search placeholders, navigation indicators, and raw URLs
        if (str.startsWith(">>") || lower.startsWith(">>") ||
                lower.equals("search with meta ai") || lower.equals("search google or type url") ||
                lower.equals("search or type url") || lower.equals("search youtube") ||
                lower.equals("search") || lower.equals("application icon") ||
                lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("www.")) {
            return false;
        }
        return true;
    }

    /**
     * Extracts active search query text from an AccessibilityEvent or its source node.
     *
     * @param event The incoming AccessibilityEvent.
     * @return Meaningful query text, or null if no valid query is present.
     */
    private String extractActiveText(AccessibilityEvent event) {
        AccessibilityNodeInfo source = event.getSource();
        // Ignore events that do not originate from an editable or search-capable node
        if (source != null && !isSearchOrInputNode(source)) {
            return null;
        }

        // Priority 1: Inspect event.getText() list
        if (event.getText() != null && !event.getText().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (CharSequence seq : event.getText()) {
                if (seq != null) {
                    String str = seq.toString().trim();
                    if (isMeaningfulSearchText(str)) {
                        sb.append(str).append(" ");
                    }
                }
            }
            if (sb.length() > 0) {
                return sb.toString().trim();
            }
        }

        // Priority 2: Traverse source node and its children for search input text
        if (source != null) {
            String textFromNode = findSearchNodeText(source);
            if (textFromNode != null) {
                return textFromNode;
            }
        }
        return null;
    }

    /**
     * Recursive DFS helper to extract text from a search node or its child components.
     *
     * @param node The accessibility node to search.
     * @return Extracted meaningful search query text, or null.
     */
    private String findSearchNodeText(AccessibilityNodeInfo node) {
        if (node == null) {
            return null;
        }

        // Check if current node is a search/input node with meaningful text
        if (isSearchOrInputNode(node)) {
            CharSequence text = node.getText();
            if (text != null && text.length() >= 3) {
                String str = text.toString().trim();
                if (isMeaningfulSearchText(str)) {
                    return str;
                }
            }
        }

        // Recurse through children
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

    // =====================================================================================
    // SECTION 11: Text Evaluation & Enforcement Dispatch
    // =====================================================================================

    /**
     * Evaluates typed search queries against the GeminiGuardEngine pipeline.
     * ---------------------------------------------------------------------
     * 1. Filters out protected system apps and sandbox testing packages.
     * 2. Deduplicates against mLastPenalizedQueryMap so leftover static search terms
     *    are not repeatedly penalized upon returning to the app.
     * 3. Runs detailed regex and AI semantic evaluation via GeminiGuardEngine.
     * 4. If RISKY: posts immediate Device Owner package suspension to Main UI thread.
     * 5. If ALLOWED: schedules a Phase 2 sequential post-search screen audit (400ms delay)
     *    to inspect the resulting web search engine response page for adult content.
     *
     * @param packageName The package name of the active app.
     * @param typedText The search query entered by the user.
     */
    private void evaluateAndEnforceImpulseGuard(final String packageName, String typedText) {
        // Bypass protected packages, system whitelist, and sandbox test packages
        if (packageName == null || SYSTEM_WHITELIST.contains(packageName) || packageName.startsWith("com.afwsamples.testdpc") || packageName.equals("test.sandbox")) {
            return;
        }

        // Check if this exact query was already penalized and remains visible as leftover text
        String lastPenalized = mLastPenalizedQueryMap.get(packageName);
        if (lastPenalized != null && typedText != null && lastPenalized.equalsIgnoreCase(typedText.trim())) {
            Log.d(TAG, "Skipping re-evaluation of static leftover search query for [" + packageName + "]: \"" + typedText + "\"");
            return;
        }

        // Evaluate query through GeminiGuardEngine
        GeminiGuardEngine.EvaluationResult result = GeminiGuardEngine.evaluateTextDetailed(this, packageName, typedText);

        if (result.isRisky) {
            // Immediate lockout enforcement
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    enforcePackageSuspension(packageName, typedText);
                }
            });
        } else {
            // Sequential Phase 2: If Search Query was ALLOWED, schedule Post-Search Screen Audit after 400ms
            // to verify that the resulting search engine results page does not display explicit media
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

    /**
     * Sequential Phase 2: Executes a full-screen audit on the search results page rendered
     * after a query has been approved.
     *
     * @param packageName The package name of the browser or search app.
     */
    private void triggerPostSearchScreenAudit(final String packageName) {
        try {
            // Capture full text of the newly loaded results screen
            AccessibilityNodeInfo root = getRootInActiveWindow();
            final String screenText = extractFullScreenText(root);
            if (screenText != null && screenText.length() >= 15) {
                Log.d(TAG, "Sequential Phase 2 Post-Search Screen Audit for [" + packageName + "]: " + screenText.substring(0, Math.min(80, screenText.length())) + "...");
                // Run screen evaluation in background
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

    // =====================================================================================
    // SECTION 12: Falcons Visual Strike Escalation Ladder & Haptic Warning
    // =====================================================================================

    /**
     * Executes progressive discipline for Falcons.ai visual NSFW violations.
     * ----------------------------------------------------------------------
     * Strike 1: Press BACK button to dismiss offending content + Warning Toast (1/2).
     * Strike 2: Press BACK button + Double Haptic Pulse + Final Warning Toast (2/2).
     * Strike 3+: Enforces 10-minute app lockout via Device Policy Manager package suspension.
     *
     * @param packageName The package name of the violating app.
     * @param strikeCount The accumulated daily strike count (1, 2, or 3+).
     */
    private void handleVisualNsfwStrike(final String packageName, int strikeCount) {
        if (strikeCount == 1) {
            // Strike 1: Non-punitive dismissal. Eject user from offending view and warn.
            performGlobalAction(GLOBAL_ACTION_BACK);
            Toast.makeText(this, "⚠️ Content Warning (1/2) - Exiting content", Toast.LENGTH_SHORT).show();
            SecurityLogger.log(this, "[FALCONS_STRIKE_1]", "Strike 1 in [" + packageName + "]: Triggered BACK action.");

        } else if (strikeCount == 2) {
            // Strike 2: Physical sensory warning. Eject user, trigger dual-pulse haptic vibration, and final toast.
            performGlobalAction(GLOBAL_ACTION_BACK);
            triggerHapticFeedback();
            Toast.makeText(this, "🚨 Final Warning (2/2) - Next violation locks app", Toast.LENGTH_LONG).show();
            SecurityLogger.log(this, "[FALCONS_STRIKE_2]", "Strike 2 in [" + packageName + "]: Triggered BACK action & Haptic Warning.");

        } else {
            // Strike 3+: Hard lockout. Suspend package for 10 minutes via Device Policy Manager.
            Toast.makeText(this, "🔒 Content Blocked - App locked for 10 minutes", Toast.LENGTH_LONG).show();
            enforcePackageSuspension(packageName, "FALCONS_AI_3RD_STRIKE_LOCKOUT");
            SecurityLogger.log(this, "[FALCONS_STRIKE_3]", "Strike 3 in [" + packageName + "]: Enforced 10-min suspension lockout.");
        }
    }

    /**
     * Emits a distinctive double-pulse haptic vibration waveform on devices equipped with a vibrator.
     * Pattern: 0ms wait, 150ms vibrate, 100ms pause, 200ms vibrate.
     */
    private void triggerHapticFeedback() {
        try {
            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // Modern Android 8.0+ vibration waveform
                    vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 150, 100, 200}, -1));
                } else {
                    // Legacy vibration waveform
                    vibrator.vibrate(new long[]{0, 150, 100, 200}, -1);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not trigger haptic vibration: " + e.getMessage());
        }
    }

    // =====================================================================================
    // SECTION 13: Device Policy App Suspension & OS-Level Enforcement
    // =====================================================================================

    /**
     * Suspends a violating package using native Android Device Owner APIs.
     * -------------------------------------------------------------------
     * Execution flow:
     * 1. Protection Checks: Exempts whitelisted system apps, TestDPC, and sandbox environments.
     * 2. Expiration Locking: If package is ALREADY suspended in the OS, does not reset or extend
     *    the timer, guaranteeing predictable penalty expiration.
     * 3. Penalty Calculation: Invokes PenaltyManager.recordViolationAndGetPenalty() to fetch
     *    escalating lockout duration (1 min -> 5 mins -> 15 mins -> 60 mins...).
     * 4. Native OS Suspension: Calls DevicePolicyManager.setPackagesSuspended() to gray out
     *    the app icon on launcher and disable launching via Android Intents.
     * 5. Foreground Dismissal: Sends GLOBAL_ACTION_HOME to dismiss the offending app window.
     * 6. Timer Scheduling: Schedules checkAndCleanExpiredSuspensions() after penalty duration.
     *
     * @param packageName The package name to suspend.
     * @param typedText The violating search query or trigger reason.
     */
    private void enforcePackageSuspension(final String packageName, String typedText) {
        boolean geminiActive = GeminiGuardEngine.isEnabled(this);
        boolean falconsActive = FalconsVisionGuardEngine.isEnabled(this);

        // Safety gate: ensure service engines are active and app is not whitelisted or TestDPC itself
        if ((!geminiActive && !falconsActive) || packageName == null || SYSTEM_WHITELIST.contains(packageName) || packageName.startsWith("com.afwsamples.testdpc") || packageName.equals("test.sandbox")) {
            Log.i(TAG, "Ignoring suspension for protected or disabled package: " + packageName);
            return;
        }

        // Expiration Locking: Verify if package is ALREADY suspended in OS.
        // If so, do NOT overwrite or reset the active countdown timer.
        SharedPreferences prefs = getSharedPreferences(PREF_SUSPENSIONS, Context.MODE_PRIVATE);
        final DevicePolicyManager dpmCheck = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        boolean isActuallySuspendedInOS = false;
        if (dpmCheck != null && dpmCheck.isDeviceOwnerApp(getPackageName())) {
            try {
                isActuallySuspendedInOS = dpmCheck.isPackageSuspended(DeviceAdminReceiver.getComponentName(this), packageName);
            } catch (Exception ignored) {
            }
        }

        if (prefs.contains(packageName) && isActuallySuspendedInOS) {
            Log.d(TAG, "Package [" + packageName + "] is ALREADY suspended in OS. Preserving active timer.");
            return;
        }

        if (!isActuallySuspendedInOS) {
            prefs.edit().remove(packageName).apply();
        }

        // Calculate penalty duration from escalating penalty tier
        PenaltyManager.PenaltyInfo penalty = PenaltyManager.recordViolationAndGetPenalty(this, packageName);
        if (typedText != null) {
            mLastPenalizedQueryMap.put(packageName, typedText.trim());
        }

        long now = System.currentTimeMillis();
        long expiryTimestamp = now + penalty.durationMs;

        Log.w(TAG, "SUSPENDING TARGET PACKAGE [" + packageName + "] FOR " + penalty.durationMinutes + " MINUTE(S) (VIOLATION #" + penalty.violationCount + ")! Reason/Query: \"" + typedText + "\"");

        try {
            final DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
            if (dpm != null && dpm.isDeviceOwnerApp(getPackageName())) {
                // Step 1: Suspend target app in Device Policy Manager (grays out icon, disables launch)
                dpm.setPackagesSuspended(DeviceAdminReceiver.getComponentName(this), new String[]{packageName}, true);
                // Step 2: Record suspension expiry timestamp in persistent storage
                recordPackageSuspension(packageName, expiryTimestamp);
                // Step 3: Show user notification with lockout countdown info
                showSuspensionNotification(packageName, penalty.violationCount, penalty.durationMinutes);

                // Step 4: Immediately kick user out of foreground violating activity back to Home Screen
                try {
                    performGlobalAction(GLOBAL_ACTION_HOME);
                } catch (Exception homeEx) {
                    Log.w(TAG, "Could not send GLOBAL_ACTION_HOME: " + homeEx.getMessage());
                }

                // Step 5: Schedule automatic unsuspend check when penalty period ends
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        checkAndCleanExpiredSuspensions();
                    }
                }, penalty.durationMs);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to suspend package: " + packageName, e);
        }
    }

    // =====================================================================================
    // SECTION 14: Dynamic App Discovery & User Notification Prompts
    // =====================================================================================

    /**
     * Prompts the user with an interactive notification when a new browser/search app is opened.
     * ------------------------------------------------------------------------------------------
     * Includes two action buttons:
     * 1. "🛡️ Monitor App": registers package into monitored apps registry.
     * 2. "⚪ Keep Whitelisted": marks package as exempt and suppresses future prompts.
     *
     * @param packageName The package name of the newly detected browser/search app.
     */
    private void triggerAppMonitoringNotificationPull(String packageName) {
        // Mark as asked so we never prompt user repeatedly for the same package
        setNeverAskApp(this, packageName);

        // Resolve human-readable application label from PackageManager
        String appLabel = packageName;
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);
            appLabel = pm.getApplicationLabel(ai).toString();
        } catch (Exception ignored) {
        }

        // Action 1: Monitor App broadcast intent
        Intent monitorIntent = new Intent(this, ActionReceiver.class);
        monitorIntent.setAction(ACTION_MONITOR_APP);
        monitorIntent.putExtra(EXTRA_PACKAGE_NAME, packageName);
        PendingIntent piMonitor = PendingIntent.getBroadcast(this, packageName.hashCode(), monitorIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Action 2: Whitelist App broadcast intent
        Intent whitelistIntent = new Intent(this, ActionReceiver.class);
        whitelistIntent.setAction(ACTION_WHITELIST_APP);
        whitelistIntent.putExtra(EXTRA_PACKAGE_NAME, packageName);
        PendingIntent piWhitelist = PendingIntent.getBroadcast(this, packageName.hashCode() + 1, whitelistIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Build interactive notification banner
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

    /**
     * Displays a high-priority system notification indicating that an app was suspended for adult content.
     *
     * @param packageName The suspended app package name.
     * @param violationCount The cumulative count of violations recorded for this package.
     * @param durationMinutes The lockout duration in minutes.
     */
    private void showSuspensionNotification(String packageName, int violationCount, int durationMinutes) {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                // Resolve app label
                String appLabel = packageName;
                try {
                    PackageManager pm = getPackageManager();
                    ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);
                    appLabel = pm.getApplicationLabel(ai).toString();
                } catch (Exception ignored) {}

                String title = "Gemini AI Guard Violation #" + violationCount + " 🔒";
                String text = appLabel + " suspended for " + durationMinutes + " minute(s) to break impulse.";

                NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.ic_lock_lock)
                        .setContentTitle(title)
                        .setContentText(text)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true);
                nm.notify(NOTIF_ID, builder.build());
                Toast.makeText(this, text, Toast.LENGTH_LONG).show();
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Initializes notification channels required on Android 8.0 (API 26) and higher.
     */
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

    /**
     * Accessibility Service Lifecycle Callback: onInterrupt
     * -----------------------------------------------------
     * Invoked when the system wants to interrupt the accessibility feedback.
     * Commits foreground app session usage so no minutes are dropped.
     */
    @Override
    public void onInterrupt() {
        Log.w(TAG, "ImpulseGuardService Interrupted!");
        commitActiveSessionUsage();
    }

    // =====================================================================================
    // SECTION 15: ActionReceiver for Dynamic Notification Responses
    // =====================================================================================

    /**
     * BroadcastReceiver handling user clicks on the "Monitor App" or "Keep Whitelisted"
     * action buttons of the dynamic monitoring notification.
     */
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

            // User chose to monitor app: add package to monitored list
            if (ACTION_MONITOR_APP.equals(intent.getAction())) {
                setMonitoredApp(context, pkg, true);
                Log.i(TAG, "User chose to MONITOR app: " + pkg);
            }
            // User chose to exempt app: remove from monitored list and remember decision
            else if (ACTION_WHITELIST_APP.equals(intent.getAction())) {
                setMonitoredApp(context, pkg, false);
                setNeverAskApp(context, pkg);
                Log.i(TAG, "User chose to KEEP WHITELISTED app: " + pkg);
            }

            // Dismiss the notification banner
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.cancel(NOTIF_PULL_ID_BASE + Math.abs(pkg.hashCode() % 100));
            }
        }
    }
}
