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
    private static final String KEY_SCREEN_GUARD_ENABLED = "gemini_screen_guard_enabled";
    private static final String KEY_API_KEY = "gemini_api_key";
    private static final String KEY_CACHE_PREFIX = "cache_verdict_";
    private static final String KEY_LOGS = "gemini_guard_logs";

    private static final String PRIMARY_MODEL = "gemini-3.5-flash-lite";
    private static final String FALLBACK_MODEL_1 = "gemini-3.1-flash-lite";
    private static final String FALLBACK_MODEL_2 = "gemini-2.5-flash-lite";
    private static final String FALLBACK_MODEL_3 = "gemini-1.5-flash-8b";

    private static final android.util.LruCache<String, Boolean> sRamCache = new android.util.LruCache<>(500);
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

                String[] testModels = new String[]{PRIMARY_MODEL, FALLBACK_MODEL_1, FALLBACK_MODEL_2, FALLBACK_MODEL_3, "gemini-1.5-flash-8b"};
                String lastErrorMsg = "Connection timeout";

                for (String modelName : testModels) {
                    long start = System.currentTimeMillis();
                    String endpointUrl = "https://generativelanguage.googleapis.com/v1beta/models/" + modelName + ":generateContent?key=" + apiKey;

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
                        conn.setConnectTimeout(8000);
                        conn.setReadTimeout(8000);
                        conn.setDoOutput(true);

                        try (OutputStream os = conn.getOutputStream()) {
                            byte[] input = payload.toString().getBytes(StandardCharsets.UTF_8);
                            os.write(input, 0, input.length);
                        }

                        int code = conn.getResponseCode();
                        long latency = System.currentTimeMillis() - start;

                        if (code == 200) {
                            if (callback != null) callback.onResult(true, "✅ API Key Connected (" + modelName + " • " + latency + "ms)");
                            return;
                        } else {
                            lastErrorMsg = "HTTP " + code;
                        }
                    } catch (Exception e) {
                        lastErrorMsg = e.getMessage() != null ? e.getMessage() : "Timeout/Network Error";
                    }
                }

                if (callback != null) {
                    callback.onResult(false, "❌ Connection Error: " + lastErrorMsg);
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
            String[] modelsToTry = new String[]{PRIMARY_MODEL, FALLBACK_MODEL_1, FALLBACK_MODEL_2, FALLBACK_MODEL_3};
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
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
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

    public static boolean isScreenGuardEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_SCREEN_GUARD_ENABLED, true);
    }

    public static void setScreenGuardEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_SCREEN_GUARD_ENABLED, enabled).apply();
        Log.i(TAG, "Gemini Screen Guard enabled set to: " + enabled);
    }

    public static EvaluationResult evaluateScreenTextDetailed(Context context, String packageName, String screenText) {
        long startTime = System.currentTimeMillis();

        if (!isScreenGuardEnabled(context) || screenText == null || screenText.trim().length() < 15) {
            return new EvaluationResult(false, 1.0, "safe", "screen_text_too_short", 0, "{}", "none");
        }

        String trimmedScreenText = screenText.trim();
        String hashKey = packageName + "_screen_" + Math.abs(trimmedScreenText.hashCode());

        // 1. 0ms Cache Check: ONLY cache RISKY screens to prevent false safe caching!
        Boolean ramVerdict = sRamCache.get(hashKey);
        if (ramVerdict != null && ramVerdict.booleanValue()) {
            Log.d(TAG, "0ms RAM Cache HIT (RISKY) for Screen Text -> true");
            return new EvaluationResult(true, 0.95, "ADULT_SCREEN_CONTENT", "0ms_ram_cache", 0, "{\"ram_cached\": true}", "ram_cache");
        }

        // 2. Check API Key & Network
        String apiKey = getApiKey(context);
        if (apiKey.isEmpty() || !isNetworkConnected(context)) {
            return new EvaluationResult(false, 0.0, "safe", "api_key_missing_or_offline", 0, "{}", "none");
        }

        // 3. Single-Flight Lock
        if (!isRequestInFlight.compareAndSet(false, true)) {
            Log.d(TAG, "Single-flight lock active. Skipping screen text evaluation.");
            return new EvaluationResult(false, 0.0, "gpu_busy", "request_in_flight", 0, "{}", "none");
        }

        try {
            String[] modelsToTry = new String[]{PRIMARY_MODEL, FALLBACK_MODEL_1, FALLBACK_MODEL_2, FALLBACK_MODEL_3};
            EvaluationResult finalResult = null;

            for (String modelName : modelsToTry) {
                EvaluationResult res = executeGeminiScreenRequest(context, modelName, apiKey, packageName, trimmedScreenText, startTime);
                if (res != null) {
                    finalResult = res;
                    break;
                }
            }

            if (finalResult != null) {
                if (finalResult.isRisky) {
                    sRamCache.put(hashKey, true);
                    putCachedVerdict(context, hashKey, true);
                }
                return finalResult;
            }

            return new EvaluationResult(false, 0.0, "safe", "gemini_all_models_failed", System.currentTimeMillis() - startTime, "{}", "none");

        } finally {
            isRequestInFlight.set(false);
        }
    }

    private static EvaluationResult executeGeminiScreenRequest(Context context, String modelName, String apiKey, String packageName, String screenText, long startTime) {
        String endpointUrl = "https://generativelanguage.googleapis.com/v1beta/models/" + modelName + ":generateContent?key=" + apiKey;

        try {
            JSONObject payload = new JSONObject();
            JSONArray contents = new JSONArray();
            JSONObject contentObj = new JSONObject();
            JSONArray parts = new JSONArray();
            JSONObject partObj = new JSONObject();

            String prompt = "Classify if the following plain text visible on an Android screen contains explicit adult, pornographic, erotic, or NSFW material.\n\n" +
                    "Visible Screen Text: \"" + screenText + "\"\n\n" +
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
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
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

                Log.i(TAG, "Gemini Screen [" + modelName + "] Output (" + latency + "ms) for [" + packageName + "]: " + generatedText);

                boolean isRisky = false;
                double confidence = 0.05;
                String category = "SAFE_SCREEN_CONTENT";
                String reason = "gemini_parsed_screen";

                String cleanJson = generatedText.replaceAll("```json", "").replaceAll("```", "").trim();

                try {
                    JSONObject parsedObj = new JSONObject(cleanJson);
                    isRisky = parsedObj.optBoolean("is_risky", false);
                } catch (Exception e) {
                    String lowerText = generatedText.toLowerCase(Locale.US);
                    if (lowerText.contains("is_risky\": true") || lowerText.contains("is_risky\":true") || lowerResponseContainsRefusal(lowerText)) {
                        isRisky = true;
                    }
                }

                if (isRisky) {
                    confidence = 0.95;
                    category = "ADULT_SCREEN_CONTENT";
                    reason = "gemini_explicit_adult_screen";
                }

                if (isRisky) {
                    Log.w(TAG, "Gemini AI Guard detected ADULT SCREEN CONTENT (" + latency + "ms) in [" + packageName + "]");
                    appendLog(context, "[" + packageName + "] Gemini AI Screen Content (" + modelName + " • " + latency + "ms): ADULT DETECTED ON SCREEN -> SUSPEND TARGET APP (60s)");
                }

                return new EvaluationResult(isRisky, confidence, category, reason, latency, generatedText, modelName);

            } else {
                return null;
            }

        } catch (Exception e) {
            Log.e(TAG, "Error executing Gemini screen request for model: " + modelName, e);
            return null;
        }
    }

    private static boolean lowerResponseContainsRefusal(String lowerText) {
        return lowerText.contains("inappropriate") || lowerText.contains("sexually suggestive") ||
                lowerText.contains("pornographic") || lowerText.contains("cannot fulfill") ||
                lowerText.contains("not equipped to handle");
    }
}
