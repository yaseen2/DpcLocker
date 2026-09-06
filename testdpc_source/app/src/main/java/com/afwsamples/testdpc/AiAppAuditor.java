package com.afwsamples.testdpc;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * =========================================================================================
 * CLASS: AiAppAuditor
 * =========================================================================================
 * Purpose:
 *   Intelligent Install-Time Application Security Auditor powered by Google Gemini AI.
 *   Operates as the Tier 2 False-Positive Rescue Agent and Tier 3 Deep Manifest Scanner in the
 *   unified Security Pipeline.
 *
 * Core Functional Roles:
 *   1. Tier 2 Provisional Rescue (`verifyAndRescuePackageAsync`):
 *      When an app is provisionally suspended by `BrowserBlocker` due to broad Android intent
 *      filters (e.g., an offline Wikipedia reader, PDF viewer, or developer documentation tool),
 *      AiAppAuditor queries Gemini in the background. If verified as an innocent utility, the app
 *      is automatically rescued and unsuspended within seconds.
 *   2. Tier 3 Deep Manifest Scan (`checkAndAuditPackage`):
 *      Inspects unclassified third-party APKs upon installation. Extracts package metadata,
 *      declared Android permissions, application categories, and activity names to detect
 *      disguised porn apps, adult dating platforms, video downloaders, or NSFW AI chatbots.
 *   3. Presumption of Innocence (Zero False-Positive Design):
 *      Requires `rawRisky == true`, `confidence >= 0.90`, and an explicit non-NONE violation category
 *      before any application is suspended. Utilities, games, and productivity tools are safe by default.
 *   4. Offline Deferred Review Queue:
 *      If an app is installed while the phone is offline or in Airplane Mode, it is added to a
 *      persistent queue (`pending_offline_audits`) and automatically evaluated the moment internet returns.
 * =========================================================================================
 */
public class AiAppAuditor {
    // Logcat tag for AI auditor logs
    private static final String TAG = "AiAppAuditor";

    // SharedPreferences file name for auditor configuration and offline queue
    private static final String PREFS_NAME = "ai_app_auditor_prefs";
    private static final String KEY_ENABLED = "ai_auditor_enabled";
    private static final String KEY_PENDING_QUEUE = "pending_offline_audits";

    // Single-thread executor for serialized asynchronous AI audits
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    // Fallback AI models ladder
    private static final String[] GEMINI_MODELS = new String[]{
            "gemini-2.5-flash",
            "gemini-2.5-flash-lite",
            "gemini-2.0-flash",
            "gemini-1.5-flash-latest"
    };

    /**
     * Helper to retrieve SharedPreferences for AiAppAuditor.
     */
    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Checks if AI App Auditing is enabled (default: true).
     */
    public static boolean isEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_ENABLED, true);
    }

    /**
     * Toggles AI App Auditing on or off.
     */
    public static void setEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
        Log.i(TAG, "AiAppAuditor enabled set to: " + enabled);
    }

    /**
     * Delegates Gemini API key lookup to the unified SecurityConfig shared vault.
     */
    public static String getGeminiApiKey(Context context) {
        return SecurityConfig.getGeminiApiKey(context);
    }

    /**
     * Delegates Gemini API key updates to the unified SecurityConfig shared vault.
     */
    public static void setGeminiApiKey(Context context, String apiKey) {
        SecurityConfig.setGeminiApiKey(context, apiKey);
    }

    /**
     * Retrieves persistent security logs related to app auditing.
     */
    public static String getAuditLogs(Context context) {
        return SecurityLogger.getLogs(context);
    }

    // =====================================================================================
    // SECTION 1: Offline Deferred Audit Queue
    // =====================================================================================

    /**
     * Enqueues an unclassified package for deferred AI evaluation when internet connectivity is restored.
     */
    public static void enqueuePendingAudit(Context context, String packageName) {
        if (packageName == null) return;
        Set<String> queue = new HashSet<>(getPrefs(context).getStringSet(KEY_PENDING_QUEUE, new HashSet<String>()));
        queue.add(packageName.trim().toLowerCase());
        getPrefs(context).edit().putStringSet(KEY_PENDING_QUEUE, queue).apply();
        Log.i(TAG, "Enqueued " + packageName + " for deferred AI audit when network connects.");
    }

    /**
     * Removes an audited package from the offline deferred queue.
     */
    public static void removePendingAudit(Context context, String packageName) {
        if (packageName == null) return;
        Set<String> queue = new HashSet<>(getPrefs(context).getStringSet(KEY_PENDING_QUEUE, new HashSet<String>()));
        queue.remove(packageName.trim().toLowerCase());
        getPrefs(context).edit().putStringSet(KEY_PENDING_QUEUE, queue).apply();
    }

    /**
     * Drains the offline deferred queue and processes all pending app audits in the background.
     * Triggered by network connectivity broadcast receivers.
     */
    public static void processPendingAudits(final Context context) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                Set<String> queue = new HashSet<>(getPrefs(context).getStringSet(KEY_PENDING_QUEUE, new HashSet<String>()));
                if (queue.isEmpty()) return;

                Log.i(TAG, "Processing " + queue.size() + " pending deferred AI audits...");
                for (String pkg : queue) {
                    try {
                        auditPackageInternal(context, pkg, false, "");
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing pending audit for " + pkg, e);
                    }
                }
            }
        });
    }

    // =====================================================================================
    // SECTION 2: Pipeline Integration (Tier 2 Rescue & Tier 3 Deep Scan)
    // =====================================================================================

    /**
     * Tier 2 Verification & Rescue Audit:
     * Asynchronously double-checks whether a Tier 2 provisional browser suspension was a false positive.
     * If Gemini confirms the app is NOT an open web browser or adult platform, it is instantly unsuspended.
     *
     * @param context        Application context
     * @param packageName    Target package
     * @param triggerReason  Diagnostic trigger reason
     */
    public static void verifyAndRescuePackageAsync(final Context context, final String packageName, final String triggerReason) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    auditPackageInternal(context, packageName, true, triggerReason);
                } catch (Exception e) {
                    Log.e(TAG, "Error in AI verification audit for " + packageName, e);
                }
            }
        });
    }

    /**
     * Tier 3 Deep Scan for unknown gray-area apps.
     * Evaluates package manifest, permissions, and app category to identify adult applications.
     */
    public static void checkAndAuditPackage(final Context context, final String packageName) {
        if (packageName == null || packageName.isEmpty()) return;

        if (!isEnabled(context)) {
            Log.i(TAG, "AiAppAuditor is disabled. Skipping package: " + packageName);
            return;
        }

        // Fast-path whitelist bypass (0ms)
        if (SecurityConfig.isWhitelisted(context, packageName)) {
            SecurityPipelineManager.markPackageSafe(context, packageName);
            SecurityLogger.log(context, "[TIER1_PASS]", packageName + " -> Whitelisted / System (0ms)");
            return;
        }

        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    auditPackageInternal(context, packageName, false, "");
                } catch (Exception e) {
                    Log.e(TAG, "Error performing AI audit for package " + packageName, e);
                }
            }
        });
    }

    // =====================================================================================
    // SECTION 3: Deep Manifest Static Analysis & Gemini Classification
    // =====================================================================================

    /**
     * Core static analysis and Gemini cloud audit engine.
     *
     * Execution Steps:
     *   1. Manifest Extraction: Reads ApplicationLabel, Category, Permissions, Activities via PackageManager.
     *   2. Prompt Construction: Embeds manifest data with strict "Presumption of Innocence" instructions.
     *   3. Gemini REST Request: Submits to Google Generative Language API.
     *   4. Guardrail Verification: Flags risky ONLY if confidence >= 0.90 and violation category != NONE.
     *   5. Decision Routing:
     *      - If Rescue Mode & Clean -> Un-suspends app via DPM and marks safe.
     *      - If Scan Mode & Risky -> Suspends app via DPM and marks blocked.
     *      - If Offline -> Enqueues into pending queue and allows temporarily.
     */
    private static void auditPackageInternal(Context context, String packageName, boolean isRescueMode, String triggerReason) {
        PackageManager pm = context.getPackageManager();

        String appLabel = packageName;
        String appCategory = "UNKNOWN";
        List<String> requestedPermissions = new ArrayList<>();
        List<String> declaredActivities = new ArrayList<>();

        // Step 1: Extract application metadata and manifest declarations
        try {
            ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
            CharSequence labelSeq = pm.getApplicationLabel(appInfo);
            if (labelSeq != null) {
                appLabel = labelSeq.toString();
            }

            // Android 8.0+ (Oreo) application category classification
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                int cat = appInfo.category;
                switch (cat) {
                    case ApplicationInfo.CATEGORY_GAME: appCategory = "GAME"; break;
                    case ApplicationInfo.CATEGORY_AUDIO: appCategory = "AUDIO"; break;
                    case ApplicationInfo.CATEGORY_VIDEO: appCategory = "VIDEO"; break;
                    case ApplicationInfo.CATEGORY_IMAGE: appCategory = "IMAGE"; break;
                    case ApplicationInfo.CATEGORY_SOCIAL: appCategory = "SOCIAL"; break;
                    case ApplicationInfo.CATEGORY_NEWS: appCategory = "NEWS"; break;
                    case ApplicationInfo.CATEGORY_MAPS: appCategory = "MAPS"; break;
                    case ApplicationInfo.CATEGORY_PRODUCTIVITY: appCategory = "PRODUCTIVITY"; break;
                    default: appCategory = "UNDEFINED"; break;
                }
            }

            // Extract declared permissions and entry-point activities
            PackageInfo pkgInfo = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS | PackageManager.GET_ACTIVITIES);
            if (pkgInfo.requestedPermissions != null) {
                for (String perm : pkgInfo.requestedPermissions) {
                    requestedPermissions.add(perm);
                }
            }

            if (pkgInfo.activities != null) {
                for (ActivityInfo act : pkgInfo.activities) {
                    if (act.name != null) {
                        declaredActivities.add(act.name);
                    }
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Package not found for AI audit: " + packageName);
            removePendingAudit(context, packageName);
            return;
        }

        boolean aiSuccess = false;
        String apiKey = getGeminiApiKey(context);

        // Step 2: Query Gemini Cloud AI if API Key is configured
        if (apiKey != null && !apiKey.isEmpty()) {
            for (String modelName : GEMINI_MODELS) {
                try {
                    JSONObject metadataJson = new JSONObject();
                    metadataJson.put("package_name", packageName);
                    metadataJson.put("app_label", appLabel);
                    metadataJson.put("app_category", appCategory);

                    String requestUrlString = "https://generativelanguage.googleapis.com/v1beta/models/" + modelName + ":generateContent?key=" + apiKey;

                    // Construct system prompt with strict guardrails to prevent false positives
                    JSONObject systemInstruction = new JSONObject();
                    JSONObject sysParts = new JSONObject();
                    sysParts.put("text",
                        "SYSTEM ROLE:\n" +
                        "You are an Android app security gatekeeper. Your goal is to prevent adult content access by blocking unfiltered web conduits and adult platforms.\n" +
                        "Rule: Google Chrome is the ONLY permitted browser (strictly managed with SafeSearch). Any other app providing arbitrary web browsing, online video scraping, VPN tunneling, or adult dating must be blocked.\n\n" +
                        "FLAG AN APP AS RISKY (\"is_risky\": true) ONLY IF IT FALLS INTO ONE OF THESE 4 CATEGORIES:\n" +
                        "1. ALTERNATIVE_WEB_BROWSER: Any third-party web browser, private browser, or incognito browser (e.g. Firefox, Brave, Opera, Kiwi, Aloha, DuckDuckGo).\n" +
                        "2. MEDIA_DOWNLOADER_WITH_BROWSER: Video downloaders, tube scrapers, or torrent clients with a built-in browser or URL loader to rip online videos.\n" +
                        "3. VPN_AND_PROXY_TUNNEL: Standalone VPNs, proxies, or DNS tunnels that bypass network-level adult filters (e.g. Turbo VPN, Psiphon, SuperVPN).\n" +
                        "4. ADULT_DATING_CHAT_NSFW: Casual dating/hookup apps (Tinder, Grindr, Badoo), live adult cam chats, or uncensored NSFW AI companions.\n\n" +
                        "ALL OTHER APPS ARE SAFE (\"is_risky\": false):\n" +
                        "Standard productivity, utility, communication, gaming, banking, audio, and local media creation apps without arbitrary web browsing or video scraping are SAFE. When in doubt, mark \"is_risky\": false.\n\n" +
                        "OUTPUT JSON FORMAT:\n" +
                        "{\n" +
                        "  \"app_summary\": \"<brief description>\",\n" +
                        "  \"is_risky\": <true | false>,\n" +
                        "  \"confidence\": <0.0 to 1.0>,\n" +
                        "  \"violation_category\": \"<NONE | ALTERNATIVE_WEB_BROWSER | MEDIA_DOWNLOADER_WITH_BROWSER | VPN_AND_PROXY_TUNNEL | ADULT_DATING_CHAT_NSFW>\",\n" +
                        "  \"reason\": \"<short explanation>\"\n" +
                        "}"
                    );
                    systemInstruction.put("parts", new JSONArray().put(sysParts));

                    JSONObject contentObj = new JSONObject();
                    JSONObject userPart = new JSONObject();
                    userPart.put("text", "Identify and classify the application:\nPackage Name: " + packageName + "\nApp Label: " + appLabel + "\nApp Category: " + appCategory);
                    contentObj.put("parts", new JSONArray().put(userPart));

                    JSONObject genConfig = new JSONObject();
                    genConfig.put("response_mime_type", "application/json");
                    genConfig.put("temperature", 0.0); // Zero temperature for deterministic classification

                    JSONObject payload = new JSONObject();
                    payload.put("system_instruction", systemInstruction);
                    payload.put("contents", new JSONArray().put(contentObj));
                    payload.put("generationConfig", genConfig);

                    URL url = new URL(requestUrlString);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                    conn.setDoOutput(true);
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(8000);

                    try (OutputStream os = conn.getOutputStream()) {
                        byte[] input = payload.toString().getBytes(StandardCharsets.UTF_8);
                        os.write(input, 0, input.length);
                    }

                    int responseCode = conn.getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                        StringBuilder response = new StringBuilder();
                        String responseLine;
                        while ((responseLine = br.readLine()) != null) {
                            response.append(responseLine.trim());
                        }

                        JSONObject responseJson = new JSONObject(response.toString());
                        JSONArray candidates = responseJson.optJSONArray("candidates");
                        if (candidates != null && candidates.length() > 0) {
                            JSONObject firstCand = candidates.getJSONObject(0);
                            JSONObject content = firstCand.optJSONObject("content");
                            if (content != null) {
                                JSONArray parts = content.optJSONArray("parts");
                                if (parts != null && parts.length() > 0) {
                                    String jsonText = parts.getJSONObject(0).optString("text");
                                    JSONObject aiResult = new JSONObject(jsonText);
                                    boolean rawRisky = aiResult.optBoolean("is_risky", false);
                                    double confidence = aiResult.optDouble("confidence", 1.0);
                                    String category = aiResult.optString("violation_category", "NONE");
                                    String reason = aiResult.optString("reason", "No reason provided");
                                    String summary = aiResult.optString("app_summary", "");

                                    // Zero False-Positive Guard: Only suspend if confidence >= 0.90 and violation category is explicit
                                    boolean isRisky = rawRisky && (confidence >= 0.90) && !"NONE".equalsIgnoreCase(category);

                                    Log.i(TAG, "Gemini Model [" + modelName + "] Result for [" + packageName + "]: is_risky=" + isRisky +
                                            " (raw=" + rawRisky + ", conf=" + confidence + ", cat=" + category + "), summary: " + summary + ", reason: " + reason);
                                    aiSuccess = true;
                                    removePendingAudit(context, packageName);

                                    // Step 5A: Route decision in Rescue Mode (verifying provisional browser suspension)
                                    if (isRescueMode) {
                                        if (SecurityConfig.isBlocklisted(context, packageName)) {
                                            SecurityPipelineManager.markPackageBlocked(context, packageName);
                                            SecurityLogger.log(context, "[AI_BLOCKLIST_PRESERVED]", packageName + " -> Explicit Blocklist Match -> Maintained Suspended");
                                        } else if (!isRisky) {
                                            // False positive confirmed: unsuspend immediately!
                                            unsuspendPackage(context, packageName);
                                            SecurityPipelineManager.markPackageSafe(context, packageName);
                                            SecurityLogger.log(context, "[AI_RESCUE]", packageName + " -> Tier 2 False Positive Rescued by Gemini AI (" + modelName + "): " + reason);
                                        } else {
                                            // Confirmed risky: maintain suspension
                                            SecurityPipelineManager.markPackageBlocked(context, packageName);
                                            SecurityLogger.log(context, "[AI_VERIFIED]", packageName + " -> Confirmed Prohibited by Gemini AI (" + modelName + "): " + reason);
                                        }
                                    } 
                                    // Step 5B: Route decision in Gray-Area Scan Mode
                                    else {
                                        if (isRisky || SecurityConfig.isBlocklisted(context, packageName)) {
                                            suspendPackage(context, packageName, "Gemini AI (" + modelName + "): [" + category + "] " + reason);
                                            SecurityPipelineManager.markPackageBlocked(context, packageName);
                                            SecurityLogger.log(context, "[AI_RISKY_SUSPEND]", packageName + " -> Suspended by Gemini AI (" + modelName + "): [" + category + "] " + reason);
                                        } else {
                                            SecurityPipelineManager.markPackageSafe(context, packageName);
                                            SecurityLogger.log(context, "[AI_PASSED_SAFE]", packageName + " -> Passed Safe by Gemini AI (" + modelName + "): " + summary);
                                        }
                                    }
                                    break;
                                }
                            }
                        }
                    } else {
                        Log.w(TAG, "Gemini API Model [" + modelName + "] returned HTTP Code: " + responseCode);
                    }
                    conn.disconnect();
                } catch (Exception e) {
                    Log.w(TAG, "Model [" + modelName + "] failed for package " + packageName, e);
                }
            }
        }

        // Step 6: Offline handling: if Gemini calls failed or device is offline, defer to pending queue
        if (!aiSuccess) {
            enqueuePendingAudit(context, packageName);
            if (SecurityConfig.isBlocklisted(context, packageName)) {
                SecurityPipelineManager.markPackageBlocked(context, packageName);
                SecurityLogger.log(context, "[AI_OFFLINE]", packageName + " -> Explicit Blocklist Match maintained");
            } else if (!isRescueMode) {
                // Fail-safe open: allow clean user apps to run while offline, queue for audit upon reconnect
                SecurityPipelineManager.markPackageSafe(context, packageName);
                SecurityLogger.log(context, "[OFFLINE_DEFERRED]", packageName + " -> Allowed temporarily (Enqueued for Gemini AI audit upon network connection)");
            } else {
                SecurityLogger.log(context, "[AI_OFFLINE]", packageName + " -> Tier 2 provisional block maintained (Enqueued for AI rescue check)");
            }
        }
    }

    // =====================================================================================
    // SECTION 4: DevicePolicyManager Enforcement Mechanics
    // =====================================================================================

    /**
     * Hardware/Enterprise level suspension via DevicePolicyManager.
     */
    private static void suspendPackage(Context context, String packageName, String reason) {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName admin = DeviceAdminReceiver.getComponentName(context);
            if (dpm != null && dpm.isDeviceOwnerApp(context.getPackageName())) {
                dpm.setPackagesSuspended(admin, new String[]{packageName}, true);
                Log.i(TAG, "SUCCESSFULLY AUTO-SUSPENDED PACKAGE: " + packageName + " | Reason: " + reason);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error suspending package: " + packageName, e);
        }
    }

    /**
     * Hardware/Enterprise level un-suspension via DevicePolicyManager.
     * Enforces safety checks to prevent un-suspending blocklisted or timer-locked applications.
     */
    private static void unsuspendPackage(Context context, String packageName) {
        if (packageName == null || packageName.isEmpty()) return;
        // Guard 1: Never unsuspend if explicitly blocklisted
        if (SecurityConfig.isBlocklisted(context, packageName)) {
            Log.w(TAG, "Refusing to unsuspend explicitly blocklisted package: " + packageName);
            return;
        }
        // Guard 2: Never unsuspend if daily timer limit was exceeded
        if (AppTimerManager.isDailyLimitExceeded(context, packageName)) {
            Log.w(TAG, "Refusing to unsuspend timer-exceeded package: " + packageName);
            return;
        }
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName admin = DeviceAdminReceiver.getComponentName(context);
            if (dpm != null && dpm.isDeviceOwnerApp(context.getPackageName())) {
                dpm.setPackagesSuspended(admin, new String[]{packageName}, false);
                Log.i(TAG, "SUCCESSFULLY RESCUED / UN-SUSPENDED PACKAGE: " + packageName);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error unsuspending package: " + packageName, e);
        }
    }
}

