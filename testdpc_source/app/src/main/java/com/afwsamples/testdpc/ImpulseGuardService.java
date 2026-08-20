package com.afwsamples.testdpc;

import android.accessibilityservice.AccessibilityService;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
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
import android.widget.Toast;
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
    public static final String ACTION_RUN_FALCONS_DIAGNOSTICS = "com.afwsamples.testdpc.ACTION_RUN_FALCONS_DIAGNOSTICS";
    public static final String EXTRA_PACKAGE_NAME = "extra_package_name";

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService mBgExecutor = Executors.newSingleThreadExecutor();
    private final ConcurrentHashMap<String, String> mLastEvaluatedTextMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> mLastPenalizedQueryMap = new ConcurrentHashMap<>();

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

    private void recordPackageSuspension(String packageName, long expiryTimestamp) {
        getSharedPreferences(PREF_SUSPENSIONS, Context.MODE_PRIVATE)
                .edit()
                .putLong(packageName, expiryTimestamp)
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
                if (SecurityPipelineManager.isPermanentlyProhibited(context, pkg)) {
                    Log.i(TAG, "Preserving permanent prohibition for [" + pkg + "] during master unsuspend.");
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
                long expiryTimestamp = (val instanceof Long) ? (Long) val : 0L;

                // Legacy fallback if stored value was start timestamp instead of expiry timestamp
                if (expiryTimestamp > 0 && expiryTimestamp < (now - 120000L)) {
                    expiryTimestamp = expiryTimestamp + 60000L;
                }

                if (!isEngineActive || (now >= expiryTimestamp)) {
                    if (SecurityPipelineManager.isPermanentlyProhibited(this, pkg)) {
                        Log.i(TAG, "Preserving permanent prohibition for [" + pkg + "] during auto-unsuspend cycle.");
                        editor.remove(pkg);
                        continue;
                    }

                    try {
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

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.i(TAG, "ImpulseGuardService Accessibility Service CONNECTED & RUNNING!");
        createNotificationChannels();
        checkAndCleanExpiredSuspensions();

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_MONITOR_APP);
        filter.addAction(ACTION_WHITELIST_APP);
        filter.addAction(ACTION_RUN_FALCONS_DIAGNOSTICS);

        registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null) return;
                String action = intent.getAction();
                if (ACTION_RUN_FALCONS_DIAGNOSTICS.equals(action)) {
                    Log.i(TAG, "Received ACTION_RUN_FALCONS_DIAGNOSTICS. Triggering hardware benchmarks...");
                    mBgExecutor.execute(() -> {
                        String report = FalconsVisionGuardEngine.runSelfDiagnostics(ImpulseGuardService.this);
                        Log.i("FalconsDiagnostic", "\n" + report);
                    });
                }
            }
        }, filter);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        checkAndCleanExpiredSuspensions();

        boolean geminiActive = GeminiGuardEngine.isEnabled(this);
        boolean falconsActive = FalconsVisionGuardEngine.isEnabled(this);

        if ((!geminiActive && !falconsActive) || event == null) {
            return;
        }

        final CharSequence packageNameChar = event.getPackageName();
        if (packageNameChar == null) {
            return;
        }

        final String packageName = packageNameChar.toString();

        int eventType = event.getEventType();
        if (eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED || eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            mLastPenalizedQueryMap.remove(packageName);
        }

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

        // 4. STRICT TYPED SEARCH QUERY INSPECTOR (Only on Text Change or Direct Search Click)
        if (geminiActive) {
            boolean isTextInputEvent = (eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED);
            boolean isClickInputEvent = (eventType == AccessibilityEvent.TYPE_VIEW_CLICKED && isSearchOrInputNode(event.getSource()));

            if (isTextInputEvent || isClickInputEvent) {
                String extractedText = extractActiveText(event);
                if (extractedText != null && extractedText.trim().length() >= 3) {
                    final String typedText = extractedText.trim();
                    if (!typedText.equalsIgnoreCase("Search or type URL") && !typedText.equalsIgnoreCase("Search Google or type URL") &&
                            !typedText.startsWith("http://") && !typedText.startsWith("https://") && !typedText.startsWith("www.")) {

                        // 0ms Fast Path for Local Cache Risky Items
                        Boolean localCached = GeminiGuardEngine.getCachedVerdict(this, typedText);
                        if (localCached != null && localCached) {
                            mBgExecutor.execute(new Runnable() {
                                @Override
                                public void run() {
                                    evaluateAndEnforceImpulseGuard(packageName, typedText);
                                }
                            });
                        } else if (isClickInputEvent) {
                            // Instant evaluation when user taps Search / Suggestion
                            mBgExecutor.execute(new Runnable() {
                                @Override
                                public void run() {
                                    evaluateAndEnforceImpulseGuard(packageName, typedText);
                                }
                            });
                        } else {
                            // Typing Completion Trigger: Wait 1200ms idle pause after typing stops to ensure user finishes complete query
                            if (mPendingAuditRunnable != null) {
                                mHandler.removeCallbacks(mPendingAuditRunnable);
                            }

                            mPendingAuditRunnable = new Runnable() {
                                @Override
                                public void run() {
                                    String lastTextForPkg = mLastEvaluatedTextMap.get(packageName);
                                    if (lastTextForPkg == null || !lastTextForPkg.equalsIgnoreCase(typedText)) {
                                        mLastEvaluatedTextMap.put(packageName, typedText);
                                        Log.d(TAG, "Captured complete search query in [" + packageName + "]: \"" + typedText + "\"");

                                        mBgExecutor.execute(new Runnable() {
                                            @Override
                                            public void run() {
                                                evaluateAndEnforceImpulseGuard(packageName, typedText);
                                            }
                                        });
                                    }
                                }
                            };

                            mHandler.postDelayed(mPendingAuditRunnable, 1200);
                        }
                    }
                }
            }
        }

        // 5. Anti-Bypass Full Screen Text Scan (ONLY active if Screen Guard is enabled in Settings)
        if (GeminiGuardEngine.isScreenGuardEnabled(this)) {
            if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                    eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED ||
                    eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {

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

                mHandler.postDelayed(mPendingScreenAuditRunnable, 400);
            }
        }

        // 6. ON-DEVICE FALCONS.AI VISUAL GUARD (100% Offline ViT Vision Transformer via API 30+ takeScreenshot)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && FalconsVisionGuardEngine.isEnabled(this)) {
            if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                    eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED ||
                    eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                    eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
                    eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED) {

                scheduleFalconsVisualDwellAudit(packageName);
            }
        }
    }

    private Runnable mPendingFalconsVisualAuditRunnable;
    private long mLastFalconsScanTimestamp = 0;
    private volatile boolean mIsFalconsScanInFlight = false;

    private void scheduleFalconsVisualDwellAudit(final String packageName) {
        long now = System.currentTimeMillis();
        if (now - mLastFalconsScanTimestamp < 1500 || mIsFalconsScanInFlight) {
            return;
        }

        if (mPendingFalconsVisualAuditRunnable != null) {
            return; // Already pending, let it fire without resetting
        }

        mPendingFalconsVisualAuditRunnable = new Runnable() {
            @Override
            public void run() {
                mPendingFalconsVisualAuditRunnable = null;
                if (mIsFalconsScanInFlight) return;
                mIsFalconsScanInFlight = true;

                Log.i(TAG, "Triggering Falcons.ai visual screenshot audit for [" + packageName + "]...");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        takeScreenshot(Display.DEFAULT_DISPLAY, mBgExecutor, new TakeScreenshotCallback() {
                            @Override
                            public void onSuccess(ScreenshotResult screenshotResult) {
                                try {
                                    mLastFalconsScanTimestamp = System.currentTimeMillis();
                                    HardwareBuffer hardwareBuffer = screenshotResult.getHardwareBuffer();
                                    ColorSpace colorSpace = screenshotResult.getColorSpace();
                                    Bitmap bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace);
                                    if (bitmap != null) {
                                        Bitmap softwareBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false);
                                        bitmap.recycle();
                                        hardwareBuffer.close();

                                        if (softwareBitmap != null) {
                                            FalconsVisionGuardEngine.VisionResult result =
                                                    FalconsVisionGuardEngine.evaluateBitmap(ImpulseGuardService.this, packageName, softwareBitmap);
                                            softwareBitmap.recycle();

                                            if (result.isNsfw) {
                                                Log.w(TAG, "🚨 FALCONS.AI VISUAL NSFW VIOLATION in [" + packageName + "] (NSFW=" + String.format(Locale.US, "%.1f%%", result.nsfwProbability * 100) + ") -> ENFORCING SUSPENSION!");
                                                mHandler.post(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        enforcePackageSuspension(packageName, "FALCONS_AI_VISUAL_NSFW");
                                                    }
                                                });
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                    Log.e(TAG, "Error processing Falcons visual screenshot", e);
                                } finally {
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

        mHandler.postDelayed(mPendingFalconsVisualAuditRunnable, 1000);
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
            if (className != null) {
                String cls = className.toString().toLowerCase(Locale.US);
                if (cls.contains("password") || cls.contains("subtitle") || cls.contains("caption")) {
                    return;
                }
            }

            CharSequence text = node.getText();
            if (text == null || text.length() == 0) {
                text = node.getContentDescription();
            }

            if (text != null && text.length() > 2) {
                String str = text.toString().trim();
                if (!str.startsWith(">>") && !str.equalsIgnoreCase("Search or type URL") && !str.startsWith("http://") && !str.startsWith("https://")) {
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

    private boolean isSearchOrInputNode(AccessibilityNodeInfo node) {
        if (node == null) return false;
        try {
            if (node.isEditable()) return true;

            CharSequence className = node.getClassName();
            if (className != null) {
                String cls = className.toString().toLowerCase(Locale.US);
                if (cls.contains("edittext") || cls.contains("autocompletetextview")) {
                    return true;
                }
            }

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

    private boolean isLikelyBrowserOrSearchApp(String packageName) {
        String lower = packageName.toLowerCase(Locale.US);
        return lower.contains("browser") || lower.contains("chrome") || lower.contains("firefox") ||
                lower.contains("opera") || lower.contains("search") || lower.contains("duckduckgo");
    }

    private boolean isMeaningfulSearchText(String str) {
        if (str == null || str.length() < 3) return false;
        String lower = str.toLowerCase(Locale.US);
        if (str.startsWith(">>") || lower.startsWith(">>") ||
                lower.equals("search with meta ai") || lower.equals("search google or type url") ||
                lower.equals("search or type url") || lower.equals("search youtube") ||
                lower.equals("search") || lower.equals("application icon") ||
                lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("www.")) {
            return false;
        }
        return true;
    }

    private String extractActiveText(AccessibilityEvent event) {
        AccessibilityNodeInfo source = event.getSource();
        if (source != null && !isSearchOrInputNode(source)) {
            return null; // Ignore non-input / non-search nodes completely
        }

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

        if (isSearchOrInputNode(node)) {
            CharSequence text = node.getText();
            if (text != null && text.length() >= 3) {
                String str = text.toString().trim();
                if (isMeaningfulSearchText(str)) {
                    return str;
                }
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

        String lastPenalized = mLastPenalizedQueryMap.get(packageName);
        if (lastPenalized != null && typedText != null && lastPenalized.equalsIgnoreCase(typedText.trim())) {
            Log.d(TAG, "Skipping re-evaluation of static leftover search query for [" + packageName + "]: \"" + typedText + "\"");
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
        boolean geminiActive = GeminiGuardEngine.isEnabled(this);
        boolean falconsActive = FalconsVisionGuardEngine.isEnabled(this);

        if ((!geminiActive && !falconsActive) || packageName == null || SYSTEM_WHITELIST.contains(packageName) || packageName.startsWith("com.afwsamples.testdpc") || packageName.equals("test.sandbox")) {
            Log.i(TAG, "Ignoring suspension for protected or disabled package: " + packageName);
            return;
        }

        // Expiration Locking: If package is ALREADY suspended in OS, do not reset or extend the timer!
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
                // 1. Suspend the target app immediately in Device Policy Manager
                dpm.setPackagesSuspended(DeviceAdminReceiver.getComponentName(this), new String[]{packageName}, true);
                recordPackageSuspension(packageName, expiryTimestamp);
                showSuspensionNotification(packageName, penalty.violationCount, penalty.durationMinutes);

                // 2. Immediately close foreground violating activity by returning to Home Screen
                try {
                    performGlobalAction(GLOBAL_ACTION_HOME);
                } catch (Exception homeEx) {
                    Log.w(TAG, "Could not send GLOBAL_ACTION_HOME: " + homeEx.getMessage());
                }

                // 3. Schedule automatic unsuspend check
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

    private void showSuspensionNotification(String packageName, int violationCount, int durationMinutes) {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
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
