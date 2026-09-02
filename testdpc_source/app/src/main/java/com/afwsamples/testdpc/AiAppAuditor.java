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
 * Intelligent Install-Time Security Auditor powered by Google Gemini AI.
 * Performs Tier 3 Deep Manifest Scans and Tier 2 False-Positive Rescue Audits.
 */
public class AiAppAuditor {
    private static final String TAG = "AiAppAuditor";
    private static final String PREFS_NAME = "ai_app_auditor_prefs";
    private static final String KEY_ENABLED = "ai_auditor_enabled";
    private static final String KEY_PENDING_QUEUE = "pending_offline_audits";

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    // Fallback AI models ladder
    private static final String[] GEMINI_MODELS = new String[]{
            "gemini-2.5-flash",
            "gemini-2.5-flash-lite",
            "gemini-2.0-flash",
            "gemini-1.5-flash-latest"
    };

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_ENABLED, true);
    }

    public static void setEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
        Log.i(TAG, "AiAppAuditor enabled set to: " + enabled);
    }

    public static String getGeminiApiKey(Context context) {
        return SecurityConfig.getGeminiApiKey(context);
    }

    public static void setGeminiApiKey(Context context, String apiKey) {
        SecurityConfig.setGeminiApiKey(context, apiKey);
    }

    public static String getAuditLogs(Context context) {
        return SecurityLogger.getLogs(context);
    }

    // --- Offline Retry Queue ---

    public static void enqueuePendingAudit(Context context, String packageName) {
        if (packageName == null) return;
        Set<String> queue = new HashSet<>(getPrefs(context).getStringSet(KEY_PENDING_QUEUE, new HashSet<String>()));
        queue.add(packageName.trim().toLowerCase());
        getPrefs(context).edit().putStringSet(KEY_PENDING_QUEUE, queue).apply();
        Log.i(TAG, "Enqueued " + packageName + " for deferred AI audit when network connects.");
    }

    public static void removePendingAudit(Context context, String packageName) {
        if (packageName == null) return;
        Set<String> queue = new HashSet<>(getPrefs(context).getStringSet(KEY_PENDING_QUEUE, new HashSet<String>()));
        queue.remove(packageName.trim().toLowerCase());
        getPrefs(context).edit().putStringSet(KEY_PENDING_QUEUE, queue).apply();
    }

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

    /**
     * Tier 2 Verification & Rescue Audit:
     * Asynchronously double-checks whether a Tier 2 provisional suspension was a false positive.
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
     */
    public static void checkAndAuditPackage(final Context context, final String packageName) {
        if (packageName == null || packageName.isEmpty()) return;

        if (!isEnabled(context)) {
            Log.i(TAG, "AiAppAuditor is disabled. Skipping package: " + packageName);
            return;
        }

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

    private static void auditPackageInternal(Context context, String packageName, boolean isRescueMode, String triggerReason) {
        PackageManager pm = context.getPackageManager();

        String appLabel = packageName;
        String appCategory = "UNKNOWN";
        List<String> requestedPermissions = new ArrayList<>();
        List<String> declaredActivities = new ArrayList<>();

        try {
            ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
            CharSequence labelSeq = pm.getApplicationLabel(appInfo);
            if (labelSeq != null) {
                appLabel = labelSeq.toString();
            }

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

        if (apiKey != null && !apiKey.isEmpty()) {
            for (String modelName : GEMINI_MODELS) {
                try {
                    JSONObject metadataJson = new JSONObject();
                    metadataJson.put("package_name", packageName);
                    metadataJson.put("app_label", appLabel);
                    metadataJson.put("app_category", appCategory);

                    String requestUrlString = "https://generativelanguage.googleapis.com/v1beta/models/" + modelName + ":generateContent?key=" + apiKey;

                    JSONObject systemInstruction = new JSONObject();
                    JSONObject sysParts = new JSONObject();
                    sysParts.put("text",
                        "SYSTEM ROLE:\n" +
                        "You are an automated application safety gatekeeper for an Android Device Owner security system.\n" +
                        "Your objective is to identify whether newly installed apps are dedicated adult/pornographic or hookup apps.\n\n" +
                        "CORE PRINCIPLE: PRESUMPTION OF INNOCENCE (ZERO FALSE POSITIVES):\n" +
                        "1. An application is SAFE BY DEFAULT. You must NEVER block legitimate utility, productivity, creative, note-taking, gaming, communication, educational, reference, financial, media, or development apps.\n" +
                        "2. Package names and application labels from Google Play Store are AUTHENTIC. Do NOT suspect legitimate apps of being 'stealth disguises' or 'hidden vaults'. Play Store is already safe from fake package identities.\n" +
                        "3. Standard Android permissions (e.g. MANAGE_EXTERNAL_STORAGE, INTERNET, RECORD_AUDIO, CAMERA) are normal platform capabilities and must NEVER be used as evidence of adult content.\n\n" +
                        "STRICT PROHIBITION CRITERIA (ONLY 4 DEFINITIVE CATEGORIES):\n" +
                        "Mark an app as risky (\"is_risky\": true) ONLY if its primary, advertised purpose is:\n" +
                        "1. EXPLICIT_PORNOGRAPHY: Dedicated app for viewing, streaming, or browsing explicit 18+ pornographic videos, hentai, or erotica (e.g. Pornhub, RedTube, XHamster, Eporner, Nhentai).\n" +
                        "2. ADULT_HOOKUP_DATING: Casual sexual hookup dating apps (Tinder, Grindr, Bumble, Badoo, AdultFriendFinder) or live adult cam/chat roulette platforms.\n" +
                        "3. UNRESTRICTED_VIDEO_SCRAPER: Dedicated media downloaders whose primary marketed feature is ripping/downloading videos from adult/tube websites (e.g. Snaptube, VidMate).\n" +
                        "4. UNCENSORED_NSFW_AI: Uncensored adult roleplay chatbots specifically marketed for explicit sexual/romantic interaction.\n\n" +
                        "ALL OTHER APPS ARE SAFE (\"is_risky\": false).\n" +
                        "If an app is a general tool, notes editor, file manager, calculator, game, or reference tool, it is SAFE.\n" +
                        "If you have ANY doubt or lack definitive evidence of an adult violation, mark \"is_risky\": false.\n\n" +
                        "OUTPUT FORMAT (JSON):\n" +
                        "{\n" +
                        "  \"app_summary\": \"<1-sentence description of what the app is>\",\n" +
                        "  \"is_risky\": <true | false>,\n" +
                        "  \"confidence\": <float 0.0 to 1.0>,\n" +
                        "  \"violation_category\": \"<NONE | EXPLICIT_PORNOGRAPHY | ADULT_HOOKUP_DATING | UNRESTRICTED_VIDEO_SCRAPER | UNCENSORED_NSFW_AI>\",\n" +
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
                    genConfig.put("temperature", 0.0);

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

                                    if (isRescueMode) {
                                        // Tier 2 Rescue Check
                                        if (SecurityConfig.isBlocklisted(context, packageName)) {
                                            SecurityPipelineManager.markPackageBlocked(context, packageName);
                                            SecurityLogger.log(context, "[AI_BLOCKLIST_PRESERVED]", packageName + " -> Explicit Blocklist Match -> Maintained Suspended");
                                        } else if (!isRisky) {
                                            // False positive rescued!
                                            unsuspendPackage(context, packageName);
                                            SecurityPipelineManager.markPackageSafe(context, packageName);
                                            SecurityLogger.log(context, "[AI_RESCUE]", packageName + " -> Tier 2 False Positive Rescued by Gemini AI (" + modelName + "): " + reason);
                                        } else {
                                            // Confirmed risky
                                            SecurityPipelineManager.markPackageBlocked(context, packageName);
                                            SecurityLogger.log(context, "[AI_VERIFIED]", packageName + " -> Confirmed Prohibited by Gemini AI (" + modelName + "): " + reason);
                                        }
                                    } else {
                                        // Tier 3 Gray-Area Scan
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

        // If AI is offline or failed, enqueue for deferred background review when network connects
        if (!aiSuccess) {
            enqueuePendingAudit(context, packageName);
            if (SecurityConfig.isBlocklisted(context, packageName)) {
                SecurityPipelineManager.markPackageBlocked(context, packageName);
                SecurityLogger.log(context, "[AI_OFFLINE]", packageName + " -> Explicit Blocklist Match maintained");
            } else if (!isRescueMode) {
                SecurityPipelineManager.markPackageSafe(context, packageName);
                SecurityLogger.log(context, "[OFFLINE_DEFERRED]", packageName + " -> Allowed temporarily (Enqueued for Gemini AI audit upon network connection)");
            } else {
                SecurityLogger.log(context, "[AI_OFFLINE]", packageName + " -> Tier 2 provisional block maintained (Enqueued for AI rescue check)");
            }
        }
    }

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

    private static void unsuspendPackage(Context context, String packageName) {
        if (packageName == null || packageName.isEmpty()) return;
        if (SecurityConfig.isBlocklisted(context, packageName)) {
            Log.w(TAG, "Refusing to unsuspend explicitly blocklisted package: " + packageName);
            return;
        }
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
