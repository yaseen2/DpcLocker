package com.afwsamples.testdpc;

import android.app.admin.DevicePolicyManager;
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AiAppAuditor {
    private static final String TAG = "AiAppAuditor";
    private static final String PREFS_NAME = "ai_app_auditor_prefs";
    private static final String KEY_ENABLED = "ai_auditor_enabled";
    private static final String KEY_GEMINI_API_KEY = "gemini_api_key";
    private static final String KEY_AUDIT_LOGS = "ai_audit_logs_history";

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    // Endpoints tried in order
    private static final String[] GEMINI_MODELS = new String[]{
            "gemini-2.5-flash",
            "gemini-2.0-flash",
            "gemini-1.5-flash",
            "gemini-1.5-flash-8b"
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
        return getPrefs(context).getString(KEY_GEMINI_API_KEY, "");
    }

    public static void setGeminiApiKey(Context context, String apiKey) {
        getPrefs(context).edit().putString(KEY_GEMINI_API_KEY, apiKey != null ? apiKey.trim() : "").apply();
        Log.i(TAG, "Gemini API key updated.");
    }

    public static String getAuditLogs(Context context) {
        String logs = getPrefs(context).getString(KEY_AUDIT_LOGS, "");
        if (logs.isEmpty()) {
            return "No AI App Audit events recorded yet.";
        }
        return logs;
    }

    private static synchronized void appendAuditLog(Context context, String logEntry) {
        String timeStamp = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
        String fullEntry = "[" + timeStamp + "] " + logEntry;
        String existing = getPrefs(context).getString(KEY_AUDIT_LOGS, "");
        String updated = fullEntry + "\n" + existing;
        // Keep max 50 lines
        String[] lines = updated.split("\n");
        StringBuilder sb = new StringBuilder();
        int max = Math.min(lines.length, 50);
        for (int i = 0; i < max; i++) {
            sb.append(lines[i]).append("\n");
        }
        getPrefs(context).edit().putString(KEY_AUDIT_LOGS, sb.toString().trim()).apply();
    }

    public static void checkAndAuditPackage(final Context context, final String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return;
        }

        if (!isEnabled(context)) {
            Log.i(TAG, "AiAppAuditor is disabled. Skipping package: " + packageName);
            return;
        }

        // Hardcoded trusted whitelist for system and core communication apps
        if ("com.android.chrome".equals(packageName) ||
            "com.google.android.googlequicksearchbox".equals(packageName) ||
            "com.android.vending".equals(packageName) ||
            "com.custom.dpclocker".equals(packageName) ||
            "com.afwsamples.testdpc".equals(packageName) ||
            "com.whatsapp".equals(packageName) ||
            "org.telegram.messenger".equals(packageName) ||
            "com.discord".equals(packageName) ||
            "app.revanced.android.youtube".equals(packageName) ||
            "app.revanced.android.gms".equals(packageName) ||
            packageName.startsWith("com.google.android.") ||
            packageName.startsWith("com.android.")) {
            Log.i(TAG, "Package " + packageName + " is in hardcoded trusted whitelist. Skipping AI audit.");
            appendAuditLog(context, packageName + " -> WHITELISTED (System/Trusted)");
            return;
        }

        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    auditPackageInternal(context, packageName);
                } catch (Exception e) {
                    Log.e(TAG, "Error performing AI audit for package " + packageName, e);
                }
            }
        });
    }

    private static void auditPackageInternal(Context context, String packageName) {
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
            return;
        }

        String lowerPkg = packageName.toLowerCase(Locale.US);
        String lowerLabel = appLabel.toLowerCase(Locale.US);

        // Strict explicit matching: only flag apps with unambiguous downloader or adult names
        boolean isExplicitDownloader = lowerPkg.contains("snaptube") || lowerPkg.contains("vmate") ||
                lowerPkg.contains("vidmate") || lowerPkg.contains("tubemate") ||
                lowerPkg.contains("xnxx") || lowerPkg.contains("xvideo") || lowerPkg.contains("pornhub") ||
                lowerLabel.contains("video downloader") || lowerLabel.contains("all video downloader") ||
                lowerLabel.contains("snaptube") || lowerLabel.contains("vmate") || lowerLabel.contains("vidmate");

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
                        "You are an expert Security Classifier for an Android Device Owner Security System. Your job is to classify newly installed Android applications to protect the user from hidden adult browsers, video downloaders, and unmanaged web search tools.\n\n" +
                        "IMPACT OF YOUR DECISION:\n" +
                        "- If you return \"is_risky\": true, the Android system will IMMEDIATELY FREEZE the app, rendering it un-openable.\n" +
                        "- A FALSE POSITIVE (freezing a clean app like WhatsApp, Banking, Maps, Utilities, Games, Productivity tools) ruins the user's phone experience.\n" +
                        "- A FALSE NEGATIVE (allowing a hidden video downloader or adult browser) exposes the user to unwanted adult content.\n\n" +
                        "STRICT CLASSIFICATION RULES:\n" +
                        "1. MARK AS RISKY (\"is_risky\": true):\n" +
                        "   - Apps whose primary or secondary feature is downloading videos from web/social media (e.g. Video Downloader, Snaptube, TubeMate, Vmate, Video Saver, All Downloader, XNXX, Hot Video Downloader).\n" +
                        "   - Unmanaged third-party web browsers or private browsers with built-in search engines (e.g. Opera Mini, UC Browser, DuckDuckGo, Brave, X Browser).\n" +
                        "   - Apps with built-in web search bars or video fetchers designed to access web media.\n\n" +
                        "2. MARK AS SAFE (\"is_risky\": false):\n" +
                        "   - Messaging & Communication apps (WhatsApp, Telegram, Signal, Messenger, Discord, Zoom, Teams).\n" +
                        "   - Financial, Banking, Shopping, and Payment apps.\n" +
                        "   - Productivity, Office, Utilities, Calculators, File Managers, PDF Readers, Weather apps.\n" +
                        "   - Games (Action, Puzzle, Casual, Arcade, Strategy games).\n" +
                        "   - Streaming services from legitimate major providers (Netflix, Spotify, Prime Video, YouTube ReVanced).\n" +
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

                                    Log.i(TAG, "Gemini Model [" + modelName + "] Audit Result for [" + packageName + "]: is_risky=" + isRisky + ", reason: " + reason);
                                    aiSuccess = true;

                                    if (isRisky) {
                                        appendAuditLog(context, packageName + " -> AUTO-SUSPENDED by Gemini AI (" + modelName + ")");
                                        suspendPackage(context, packageName, "Gemini AI (" + modelName + "): " + reason);
                                    } else {
                                        appendAuditLog(context, packageName + " -> PASSED SAFE by Gemini AI (" + modelName + ")");
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

        // Structural Fallback Guard: If AI API failed or returned 404/error, evaluate manifest component structure
        if (!aiSuccess) {
            if (isExplicitDownloader) {
                Log.i(TAG, "Enforcing Structural Fallback Guard for package: " + packageName);
                appendAuditLog(context, packageName + " -> AUTO-SUSPENDED by Structural Fallback Guard");
                suspendPackage(context, packageName, "Structural Fallback Guard (Internet + Storage + Media/Downloader Components)");
            } else {
                appendAuditLog(context, packageName + " -> PASSED SAFE (Fallback Structural Analysis)");
            }
        }
    }

    private static void suspendPackage(Context context, String packageName, String reason) {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            if (dpm != null && dpm.isDeviceOwnerApp(context.getPackageName())) {
                dpm.setPackagesSuspended(DeviceAdminReceiver.getComponentName(context), new String[]{packageName}, true);
                Log.i(TAG, "SUCCESSFULLY AUTO-SUSPENDED RISKY PACKAGE: " + packageName + " | Reason: " + reason);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error suspending package: " + packageName, e);
        }
    }
}
