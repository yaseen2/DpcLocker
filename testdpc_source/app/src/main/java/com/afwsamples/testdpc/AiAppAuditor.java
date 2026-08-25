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

                    JSONArray permArray = new JSONArray();
                    for (String perm : requestedPermissions) {
                        permArray.put(perm);
                    }
                    metadataJson.put("requested_permissions", permArray);

                    JSONArray actArray = new JSONArray();
                    int actLimit = Math.min(declaredActivities.size(), 25);
                    for (int i = 0; i < actLimit; i++) {
                        actArray.put(declaredActivities.get(i));
                    }
                    metadataJson.put("declared_activities", actArray);

                    String requestUrlString = "https://generativelanguage.googleapis.com/v1beta/models/" + modelName + ":generateContent?key=" + apiKey;

                    JSONObject systemInstruction = new JSONObject();
                    JSONObject sysParts = new JSONObject();
                    sysParts.put("text",
                        "SYSTEM ROLE:\n" +
                        "You are an expert Security Classifier for an Android Device Owner Security System. Your job is to classify newly installed Android applications to protect the user from ALL adult content, pornography, hookup platforms, stealth browsers, and video downloaders.\n\n" +
                        "IMPACT OF YOUR DECISION:\n" +
                        "- If you return \"is_risky\": true, the Android system will freeze/suspend the app.\n" +
                        "- If you return \"is_risky\": false, the Android system will un-suspend and permit the app.\n\n" +
                        "STRICT CLASSIFICATION RULES:\n" +
                        "1. MARK AS RISKY (\"is_risky\": true):\n" +
                        "   - PORNOGRAPHY & ADULT MEDIA: Any app designed to stream, view, browse, or download explicit adult/18+ content, hentai, erotica, adult comics/manga, OnlyFans/Fansly viewers, or NSFW hubs.\n" +
                        "   - ADULT DATING & LIVE CAM PLATFORMS: Casual hookup apps (Tinder, Grindr, Bumble, Badoo, AdultFriendFinder), unfiltered random video chat apps (Omegle-clones, OmeTV, Chatroulette, Monkey, Azar), and live adult cam platforms.\n" +
                        "   - VIDEO DOWNLOADERS & MEDIA SCRAPERS: Apps whose primary feature is downloading/scraping videos from web/social media (Snaptube, VidMate, TubeMate, All Video Downloader, XNXX, Video Saver).\n" +
                        "   - UNMANAGED BROWSERS & WEB VIEWERS: Third-party web browsers or private browsers with built-in search engines (Opera, Brave, Firefox, DuckDuckGo, UC Browser, X Browser, Tor, Aloha Browser).\n" +
                        "   - SECRET VAULTS & STEALTH BROWSERS: Apps disguised as calculators, clocks, or file locks that contain hidden private web browsers or hidden adult galleries (Calculator Vault, HideX, Secret Browser).\n" +
                        "   - UNCENSORED NSFW AI CHATBOTS: AI companion or roleplay apps designed for explicit romantic or sexual interaction.\n\n" +
                        "2. MARK AS SAFE (\"is_risky\": false):\n" +
                        "   - Standard Messaging & Communication (WhatsApp, Telegram, Signal, Messenger, Discord, Zoom, Microsoft Teams, Slack).\n" +
                        "   - Financial, Banking, Shopping, and Payment apps (PayPal, Amazon, Local Bank apps).\n" +
                        "   - Productivity, Office, Utilities, Standard Calculators, File Managers, PDF Readers, Weather apps.\n" +
                        "   - Normal Games (Action, Puzzle, Casual, Arcade, Strategy, Sports games without explicit porn).\n" +
                        "   - Mainstream family streaming services (Netflix, Spotify, Prime Video, YouTube ReVanced, Disney+).\n" +
                        "   - Educational, Language learning (Duolingo, Anki), and Reference apps.\n" +
                        "   - Official system tools and Google apps."
                    );
                    systemInstruction.put("parts", new JSONArray().put(sysParts));

                    JSONObject contentObj = new JSONObject();
                    JSONObject userPart = new JSONObject();
                    userPart.put("text", "Classify the following Android package metadata:\n" + metadataJson.toString(2));
                    contentObj.put("parts", new JSONArray().put(userPart));

                    JSONObject genConfig = new JSONObject();
                    genConfig.put("response_mime_type", "application/json");

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
                                    boolean isRisky = aiResult.optBoolean("is_risky", false);
                                    String reason = aiResult.optString("reason", "No reason provided");

                                    Log.i(TAG, "Gemini Model [" + modelName + "] Result for [" + packageName + "]: is_risky=" + isRisky + ", reason: " + reason);
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
                                            suspendPackage(context, packageName, "Gemini AI (" + modelName + "): " + reason);
                                            SecurityPipelineManager.markPackageBlocked(context, packageName);
                                            SecurityLogger.log(context, "[AI_RISKY_SUSPEND]", packageName + " -> Suspended by Gemini AI (" + modelName + "): " + reason);
                                        } else {
                                            SecurityPipelineManager.markPackageSafe(context, packageName);
                                            SecurityLogger.log(context, "[AI_PASSED_SAFE]", packageName + " -> Passed Safe by Gemini AI (" + modelName + ")");
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
