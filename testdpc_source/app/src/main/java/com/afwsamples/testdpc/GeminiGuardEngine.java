package com.afwsamples.testdpc;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
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
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class GeminiGuardEngine {

    private static final String TAG = "GeminiGuardEngine";
    private static final String PREFS_NAME = "gemini_guard_engine_prefs";
    private static final String KEY_ENABLED = "gemini_guard_enabled";
    private static final String KEY_API_KEY = "gemini_api_key";
    private static final String KEY_CACHE_PREFIX = "cache_verdict_";
    private static final String KEY_LOGS = "gemini_guard_logs";

    private static final String PRIMARY_MODEL = "gemini-3.6-flash";
    private static final String FALLBACK_MODEL_1 = "gemini-3.5-flash";
    private static final String FALLBACK_MODEL_2 = "gemini-2.5-flash";

    private static final AtomicBoolean isRequestInFlight = new AtomicBoolean(false);
    private static final ExecutorService sBgExecutor = Executors.newSingleThreadExecutor();

    public static class EvaluationResult {
        public boolean isRisky;
        public double confidence;
        public String category;
        public String reason;
        public long latencyMs;
        public String rawResponse;
        public String modelUsed;

        public EvaluationResult(boolean isRisky, double confidence, String category, String reason, long latencyMs, String rawResponse, String modelUsed) {
            this.isRisky = isRisky;
            this.confidence = confidence;
            this.category = category;
            this.reason = reason;
            this.latencyMs = latencyMs;
            this.rawResponse = rawResponse;
            this.modelUsed = modelUsed;
        }
    }

    public interface ApiTestCallback {
        void onResult(boolean success, String message);
    }

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_ENABLED, true);
    }

    public static void setEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
        Log.i(TAG, "GeminiGuardEngine enabled set to: " + enabled);
        if (!enabled) {
            ImpulseGuardService.unsuspendAllImpulseSuspendedPackages(context);
        }
    }

    public static String getApiKey(Context context) {
        return getPrefs(context).getString(KEY_API_KEY, "").trim();
    }

    public static void setApiKey(Context context, String apiKey) {
        getPrefs(context).edit().putString(KEY_API_KEY, apiKey != null ? apiKey.trim() : "").apply();
        Log.i(TAG, "Gemini API Key updated.");
    }

    public static boolean isNetworkConnected(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    public static Boolean getCachedVerdict(Context context, String query) {
        String key = KEY_CACHE_PREFIX + query.trim().toLowerCase(Locale.US);
        if (getPrefs(context).contains(key)) {
            return getPrefs(context).getBoolean(key, false);
        }
        return null;
    }

    public static void putCachedVerdict(Context context, String query, boolean isRisky) {
        String key = KEY_CACHE_PREFIX + query.trim().toLowerCase(Locale.US);
        getPrefs(context).edit().putBoolean(key, isRisky).apply();
    }

    public static void clearCache(Context context) {
        SharedPreferences.Editor editor = getPrefs(context).edit();
        for (String key : getPrefs(context).getAll().keySet()) {
            if (key.startsWith(KEY_CACHE_PREFIX)) {
                editor.remove(key);
            }
        }
        editor.apply();
        Log.i(TAG, "Local Gemini Cache cleared.");
    }

    public static String getLogs(Context context) {
        String logs = getPrefs(context).getString(KEY_LOGS, "");
        if (logs.isEmpty()) {
            return "No Gemini AI Guard events recorded yet.";
        }
        return logs;
    }

    public static void appendLog(Context context, String entry) {
        String timestamp = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
        String currentLogs = getPrefs(context).getString(KEY_LOGS, "");
        String newLogs = "[" + timestamp + "] " + entry + "\n" + currentLogs;
        if (newLogs.length() > 15000) {
            newLogs = newLogs.substring(0, 15000);
        }
        getPrefs(context).edit().putString(KEY_LOGS, newLogs).apply();
    }

    public static void clearLogs(Context context) {
        getPrefs(context).edit().remove(KEY_LOGS).apply();
    }

    public static void testApiKeyAsync(final Context context, final ApiTestCallback callback) {
        sBgExecutor.execute(new Runnable() {
            @Override
            public void run() {
                String apiKey = getApiKey(context);
                if (apiKey.isEmpty()) {
                    if (callback != null) callback.onResult(false, "Gemini API Key is blank.");
                    return;
                }

                long start = System.currentTimeMillis();
                String endpointUrl = "https://generativelanguage.googleapis.com/v1beta/models/" + PRIMARY_MODEL + ":generateContent?key=" + apiKey;

                try {
                    JSONObject payload = new JSONObject();
                    JSONArray contents = new JSONArray();
                    JSONObject contentObj = new JSONObject();
                    JSONArray parts = new JSONArray();
                    JSONObject partObj = new JSONObject();
                    partObj.put("text", "Respond ONLY with valid JSON: {\"status\": \"ok\"}");
                    parts.put(partObj);
                    contentObj.put("parts", parts);
                    contents.put(contentObj);
                    payload.put("contents", contents);

                    URL url = new URL(endpointUrl);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    conn.setDoOutput(true);

                    try (OutputStream os = conn.getOutputStream()) {
                        byte[] input = payload.toString().getBytes(StandardCharsets.UTF_8);
                        os.write(input, 0, input.length);
                    }

                    int code = conn.getResponseCode();
                    long latency = System.currentTimeMillis() - start;

                    if (code == 200) {
                        if (callback != null) callback.onResult(true, "✅ API Key Connected (" + PRIMARY_MODEL + " • " + latency + "ms)");
                    } else {
                        // Fallback check
                        if (callback != null) callback.onResult(false, "❌ API Error (HTTP " + code + ")");
                    }
                } catch (Exception e) {
                    if (callback != null) callback.onResult(false, "❌ Connection Error: " + e.getMessage());
                }
            }
        });
    }

    public static EvaluationResult evaluateTextDetailed(Context context, String packageName, String text) {
        long startTime = System.currentTimeMillis();

        if (text == null || text.trim().length() < 3) {
            return new EvaluationResult(false, 1.0, "safe", "query_too_short", 0, "{}", "none");
        }

        String trimmedText = text.trim();

        // 1. 0ms Disk Cache Check
        Boolean cachedVerdict = getCachedVerdict(context, trimmedText);
        if (cachedVerdict != null) {
            Log.d(TAG, "0ms Cache HIT for [" + trimmedText + "] -> " + cachedVerdict);
            return new EvaluationResult(cachedVerdict, cachedVerdict ? 0.95 : 0.05, cachedVerdict ? "ADULT_PORNOGRAPHY" : "SAFE_GENERAL", "0ms_local_cache", 0, "{\"cached\": true}", "cache");
        }

        // 2. Check API Key
        String apiKey = getApiKey(context);
        if (apiKey.isEmpty()) {
            Log.d(TAG, "Gemini API Key not set. Fail-safe allow for: " + trimmedText);
            return new EvaluationResult(false, 0.0, "safe", "api_key_missing", 0, "{}", "none");
        }

        // 3. Network Check
        if (!isNetworkConnected(context)) {
            Log.d(TAG, "Network disconnected. Fail-safe allow for: " + trimmedText);
            return new EvaluationResult(false, 0.0, "safe", "network_offline", 0, "{}", "none");
        }

        // 4. Single-Flight Lock: Drop intermediate keystrokes if request is currently executing
        if (!isRequestInFlight.compareAndSet(false, true)) {
            Log.d(TAG, "Single-flight lock active. Skipping intermediate evaluation for: " + trimmedText);
            return new EvaluationResult(false, 0.0, "gpu_busy", "request_in_flight", 0, "{}", "none");
        }

        try {
            // Model Fallback Ladder: Primary -> Fallback 1 -> Fallback 2
            String[] modelsToTry = new String[]{PRIMARY_MODEL, FALLBACK_MODEL_1, FALLBACK_MODEL_2};
            EvaluationResult finalResult = null;

            for (String modelName : modelsToTry) {
                EvaluationResult res = executeGeminiRequest(context, modelName, apiKey, packageName, trimmedText, startTime);
                if (res != null) {
                    finalResult = res;
                    break;
                }
            }

            if (finalResult != null) {
                // Save to 0ms Disk Cache
                putCachedVerdict(context, trimmedText, finalResult.isRisky);
                return finalResult;
            }

            // Default Fail-Safe Open
            return new EvaluationResult(false, 0.0, "safe", "gemini_all_models_failed", System.currentTimeMillis() - startTime, "{}", "none");

        } finally {
            isRequestInFlight.set(false);
        }
    }

    private static EvaluationResult executeGeminiRequest(Context context, String modelName, String apiKey, String packageName, String trimmedText, long startTime) {
        String endpointUrl = "https://generativelanguage.googleapis.com/v1beta/models/" + modelName + ":generateContent?key=" + apiKey;

        try {
            JSONObject payload = new JSONObject();
            JSONArray contents = new JSONArray();
            JSONObject contentObj = new JSONObject();
            JSONArray parts = new JSONArray();
            JSONObject partObj = new JSONObject();

            String prompt = "You are an AI content safety filter for an Android browser. Classify if the search query represents explicit adult, pornographic, erotic, or NSFW search intent.\n\n" +
                    "Search Query: \"" + trimmedText + "\"\n\n" +
                    "Respond ONLY with a valid JSON object: {\"is_risky\": true} or {\"is_risky\": false}";

            partObj.put("text", prompt);
            parts.put(partObj);
            contentObj.put("parts", parts);
            contents.put(contentObj);
            payload.put("contents", contents);

            URL url = new URL(endpointUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = payload.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int code = conn.getResponseCode();
            long latency = System.currentTimeMillis() - startTime;

            if (code == 200) {
                StringBuilder responseBuilder = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        responseBuilder.append(line.trim());
                    }
                }

                String rawResponseBody = responseBuilder.toString();
                JSONObject jsonResponse = new JSONObject(rawResponseBody);
                JSONArray candidates = jsonResponse.optJSONArray("candidates");
                String generatedText = "";

                if (candidates != null && candidates.length() > 0) {
                    JSONObject candidate = candidates.getJSONObject(0);
                    JSONObject candidateContent = candidate.optJSONObject("content");
                    if (candidateContent != null) {
                        JSONArray candidateParts = candidateContent.optJSONArray("parts");
                        if (candidateParts != null && candidateParts.length() > 0) {
                            generatedText = candidateParts.getJSONObject(0).optString("text", "");
                        }
                    }
                }

                Log.i(TAG, "Gemini [" + modelName + "] Output (" + latency + "ms) for [" + trimmedText + "]: " + generatedText);

                boolean isRisky = false;
                double confidence = 0.05;
                String category = "SAFE_GENERAL";
                String reason = "gemini_parsed";

                String lowerText = generatedText.toLowerCase(Locale.US);

                // Fuzzy JSON Extraction
                if (lowerText.contains("\"is_risky\": true") || lowerText.contains("\"is_risky\":true") || lowerText.contains("\"is_risky\":  true")) {
                    isRisky = true;
                    confidence = 0.95;
                    category = "ADULT_PORNOGRAPHY";
                    reason = "gemini_explicit_adult";
                } else if (lowerText.contains("\"is_risky\": false") || lowerText.contains("\"is_risky\":false") || lowerResponseContainsRefusal(lowerText)) {
                    if (lowerResponseContainsRefusal(lowerText)) {
                        isRisky = true;
                        confidence = 0.95;
                        category = "ADULT_PORNOGRAPHY_REFUSAL";
                        reason = "gemini_refusal_text";
                    } else {
                        isRisky = false;
                        confidence = 0.05;
                        category = "SAFE_GENERAL";
                        reason = "gemini_explicit_safe";
                    }
                }

                if (isRisky) {
                    Log.w(TAG, "Gemini AI Guard detected ADULT QUERY (" + latency + "ms) in [" + packageName + "]: \"" + trimmedText + "\"");
                    appendLog(context, "[" + packageName + "] Gemini AI (" + modelName + " • " + latency + "ms): ADULT DETECTED -> \"" + trimmedText + "\" -> SUSPEND TARGET APP (60s)");
                } else {
                    Log.d(TAG, "Gemini AI Guard evaluated safe text (" + latency + "ms) in [" + packageName + "]: \"" + trimmedText + "\"");
                }

                return new EvaluationResult(isRisky, confidence, category, reason, latency, generatedText, modelName);

            } else {
                Log.w(TAG, "Gemini API Model [" + modelName + "] returned HTTP " + code + ". Attempting fallback...");
                return null; // Triggers fallback ladder
            }

        } catch (Exception e) {
            Log.e(TAG, "Error executing Gemini request for model: " + modelName, e);
            return null;
        }
    }

    private static boolean lowerResponseContainsRefusal(String lowerText) {
        return lowerText.contains("inappropriate") || lowerText.contains("sexually suggestive") ||
                lowerText.contains("pornographic") || lowerText.contains("cannot fulfill") ||
                lowerText.contains("not equipped to handle");
    }
}
