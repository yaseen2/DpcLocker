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
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.util.Log;

import android.graphics.Bitmap;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
            "gemini-3.5-flash-lite",   // Priority 1: Ultra-fast (~1s latency), highly available
            "gemini-3.5-flash",        // Priority 2
            "gemini-3.6-flash",        // Priority 3
            "gemini-3.8-flash",        // Priority 4
            "gemini-2.5-flash",        // Fallback
            "gemini-2.5-flash-lite",   // Fallback
            "gemini-2.0-flash"         // Emergency Quota Fallback
    };

    // Per-model circuit breaker cooldowns to skip quota-exhausted (429) or unavailable models immediately
    private static final ConcurrentHashMap<String, Long> sAuditorModelCooldownUntil = new ConcurrentHashMap<>();

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

    private static boolean sNetworkCallbackRegistered = false;
    private static volatile long sLastPendingDrainTimestamp = 0L;

    /**
     * Registers a ConnectivityManager.NetworkCallback to automatically drain the pending
     * offline audit queue the instant network connectivity is restored.
     */
    public static synchronized void initNetworkCallback(final Context context) {
        if (sNetworkCallbackRegistered) return;
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                cm.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(Network network) {
                        Log.i(TAG, "🌐 Network connectivity available. Draining pending AI audit queue...");
                        processPendingAudits(context.getApplicationContext());
                    }
                });
                sNetworkCallbackRegistered = true;
                Log.i(TAG, "Registered NetworkCallback for pending AI audits.");
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                NetworkRequest request = new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build();
                cm.registerNetworkCallback(request, new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(Network network) {
                        Log.i(TAG, "🌐 Network connectivity available. Draining pending AI audit queue...");
                        processPendingAudits(context.getApplicationContext());
                    }
                });
                sNetworkCallbackRegistered = true;
                Log.i(TAG, "Registered NetworkCallback for pending AI audits.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to register NetworkCallback", e);
        }
    }

    /**
     * Enqueues an unclassified package for deferred AI evaluation when internet connectivity is restored.
     */
    public static void enqueuePendingAudit(Context context, String packageName) {
        if (packageName == null) return;
        Set<String> queue = new HashSet<>(getPrefs(context).getStringSet(KEY_PENDING_QUEUE, new HashSet<String>()));
        queue.add(packageName.trim().toLowerCase());
        getPrefs(context).edit().putStringSet(KEY_PENDING_QUEUE, queue).apply();
        Log.i(TAG, "Enqueued " + packageName + " for deferred AI audit when network connects.");
        initNetworkCallback(context);
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
        long now = System.currentTimeMillis();
        if (now - sLastPendingDrainTimestamp < 60_000L) {
            return;
        }
        sLastPendingDrainTimestamp = now;
        executor.execute(new Runnable() {
            @Override
            public void run() {
                Set<String> queue = new HashSet<>(getPrefs(context).getStringSet(KEY_PENDING_QUEUE, new HashSet<String>()));
                if (queue.isEmpty()) return;

                Log.i(TAG, "Processing " + queue.size() + " pending deferred AI audits...");
                for (String pkg : queue) {
                    try {
                        if (SecurityConfig.isWhitelisted(context, pkg) ||
                                SecurityConfig.isManagedChromeWebApk(context, pkg) ||
                                SecurityPipelineManager.getCachedSafePackages(context).contains(pkg.toLowerCase(Locale.US))) {
                            removePendingAudit(context, pkg);
                            continue;
                        }
                        auditPackageInternal(context, pkg, false, "", true);
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
     */
    public static void verifyAndRescuePackageAsync(final Context context, final String packageName, final String triggerReason) {
        verifyAndRescuePackageAsync(context, packageName, triggerReason, false);
    }

    public static void verifyAndRescuePackageAsync(final Context context, final String packageName, final String triggerReason, final boolean isUpdate) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    auditPackageInternal(context, packageName, true, triggerReason, isUpdate);
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
        checkAndAuditPackage(context, packageName, false);
    }

    public static void checkAndAuditPackage(final Context context, final String packageName, final boolean isUpdate) {
        if (packageName == null || packageName.isEmpty()) return;

        if (!isEnabled(context)) {
            Log.i(TAG, "AiAppAuditor is disabled. Skipping package: " + packageName);
            return;
        }

        // Fast-path whitelist / managed Chrome WebAPK / user-timed app bypass (0ms)
        if (SecurityConfig.isWhitelisted(context, packageName) ||
                SecurityConfig.isManagedChromeWebApk(context, packageName) ||
                (!SecurityConfig.isBlocklisted(context, packageName) && AppTimerManager.getAppLimitMinutes(context, packageName) > 0)) {
            SecurityPipelineManager.markPackageSafe(context, packageName);
            SecurityLogger.log(context, "[TIER1_PASS]", packageName + " -> Whitelisted / System / Managed (0ms)");
            return;
        }

        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    auditPackageInternal(context, packageName, false, "", isUpdate);
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
    private static void auditPackageInternal(Context context, String packageName, boolean isRescueMode, String triggerReason, boolean isUpdate) {
        // Deterministic guard: Unmanaged Chrome channels (Beta, Dev, Canary) cannot be managed via DPM policies
        if (packageName.startsWith("com.chrome.") && !"com.android.chrome".equalsIgnoreCase(packageName)) {
            Log.w(TAG, "Unmanaged Chrome channel detected: " + packageName + " -> HARD BLOCK (No AI rescue)");
            suspendPackage(context, packageName, "Unmanaged Chrome Channel (" + packageName + ")");
            SecurityPipelineManager.markPackageBlocked(context, packageName);
            removePendingAudit(context, packageName);
            return;
        }

        // Exempt verified managed Chrome WebAPKs & user-timed non-blocklisted apps
        if (!SecurityConfig.isBlocklisted(context, packageName) &&
                (SecurityConfig.isManagedChromeWebApk(context, packageName) ||
                        AppTimerManager.getAppLimitMinutes(context, packageName) > 0)) {
            SecurityPipelineManager.markPackageSafe(context, packageName);
            removePendingAudit(context, packageName);
            return;
        }

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

        // Step 1.5: Deterministic 0ms Guard for Movie/Streaming Apps & Embedded Browser Video Downloaders
        String deterministicBlockReason = getDeterministicProhibitedConduitReason(packageName, appLabel, appCategory, declaredActivities);
        if (deterministicBlockReason != null) {
            Log.w(TAG, "Deterministic prohibited conduit detected: " + packageName + " (" + deterministicBlockReason + ") -> HARD BLOCK (0 API Quota Used)");
            suspendPackage(context, packageName, deterministicBlockReason);
            SecurityPipelineManager.markPackageBlocked(context, packageName);
            removePendingAudit(context, packageName);
            SecurityLogger.log(context, "[DETERMINISTIC_CONDUIT_BLOCK]", packageName + " (" + appLabel + ") -> " + deterministicBlockReason);
            if (!isUpdate) {
                SecurityPipelineManager.recordBlockedAppInstallStrike(context, packageName, deterministicBlockReason);
            }
            return;
        }

        // Provisionally suspend suspicious fresh-install candidates BEFORE network call so user cannot open app during 1-2s API audit
        if (!isRescueMode && !isUpdate && !SecurityConfig.isProtectedSystemApp(context, packageName) &&
                isBrowserOrDownloaderCandidate(context, packageName, appLabel, appCategory)) {
            suspendPackage(context, packageName, "Provisional suspension during Gemini AI security audit");
        }

        boolean aiSuccess = false;
        String apiKey = getGeminiApiKey(context);

        // Step 2: Query Gemini Cloud AI if API Key is configured and network is online
        if (apiKey != null && !apiKey.isEmpty() && GeminiGuardEngine.isNetworkConnected(context)) {
            for (String modelName : GEMINI_MODELS) {
                Long cooldownUntil = sAuditorModelCooldownUntil.get(modelName);
                if (cooldownUntil != null && System.currentTimeMillis() < cooldownUntil) {
                    continue;
                }
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
                        "You are an Android app security gatekeeper. Your goal is to prevent adult content access by blocking unfiltered web conduits, video downloaders, and movie/streaming platforms.\n" +
                        "Rule: Standard Google Chrome (\"com.android.chrome\") is the ONLY permitted browser because it is strictly managed via Device Owner policies. Any other browser, including Chrome pre-release channels (Chrome Beta \"com.chrome.beta\", Chrome Dev, Chrome Canary) and third-party browsers (Firefox, Brave, Opera, Kiwi), must be blocked as ALTERNATIVE_WEB_BROWSER.\n\n" +
                        "FLAG AN APP AS RISKY (\"is_risky\": true) ONLY IF IT FALLS INTO ONE OF THESE 6 CATEGORIES:\n" +
                        "1. ALTERNATIVE_WEB_BROWSER: Any third-party web browser, private browser, light browser, or unmanaged Chrome channel (e.g. Chrome Beta, Firefox, Brave, Opera, Kiwi, Aloha, DuckDuckGo, Qwant, Arc, Safe Browser).\n" +
                        "2. MEDIA_DOWNLOADER_WITH_BROWSER: Video downloaders, social media status/reel savers, file download managers, tube scrapers, or torrent clients (e.g. Video Downloader Hub, Snaptube, VidMate, ADM, 1DM, uTorrent).\n" +
                        "3. VPN_AND_PROXY_TUNNEL: Standalone VPNs, proxies, or DNS tunnels that bypass network-level adult filters (e.g. Turbo VPN, Psiphon, SuperVPN, ProxHub).\n" +
                        "4. ADULT_DATING_CHAT_NSFW: Casual dating/hookup apps (Tinder, Grindr, Badoo), live adult cam chats, anonymous stranger chats, or uncensored NSFW AI companions.\n" +
                        "5. APP_CLONER_OR_VIRTUAL_CONTAINER: App cloners, multi-account utilities, dual-space, or virtual containers used to run apps inside a sandbox and bypass device restrictions (e.g. Parallel Space, Clone App, Dual Space, Multiple Accounts, Shelter, Island, VirtualXposed, App Cloner).\n" +
                        "6. MOVIE_STREAMING_OR_VIDEO_DOWNLOADER: Any movie/TV streaming app, free cinema/OTT platform, full movies app, short-drama/reel app, IPTV player, or video player with built-in video/social-media downloading, network streaming, or web/cloud media access (e.g. Streamflix, Tubi, Plex, Netflix, Prime Video, Stremio, DramaBox, ReelShort, ShortMax, Loklok, MX Player, PLAYit, XPlayer).\n\n" +
                        "ALL OTHER APPS ARE SAFE (\"is_risky\": false):\n" +
                        "Standard productivity, utility, messaging (WhatsApp, Google Messages), ride-hailing/maps (Yango, Uber), file transfer (Send Anywhere), gaming, banking, audio/music (Spotify), and offline education apps without arbitrary web browsing, video downloading, movie streaming, or app virtualization are SAFE. Mark \"is_risky\": true ONLY when the app clearly belongs to one of the 6 categories above (confidence >= 0.85). Set \"confidence\" to your certainty (0.0 to 1.0) in your is_risky verdict (e.g. 0.95 for clearly safe apps too).\n\n" +
                        "OUTPUT JSON FORMAT:\n" +
                        "{\n" +
                        "  \"app_summary\": \"<brief description>\",\n" +
                        "  \"is_risky\": <true | false>,\n" +
                        "  \"confidence\": <0.0 to 1.0>,\n" +
                        "  \"violation_category\": \"<NONE | ALTERNATIVE_WEB_BROWSER | MEDIA_DOWNLOADER_WITH_BROWSER | VPN_AND_PROXY_TUNNEL | ADULT_DATING_CHAT_NSFW | APP_CLONER_OR_VIRTUAL_CONTAINER | MOVIE_STREAMING_OR_VIDEO_DOWNLOADER>\",\n" +
                        "  \"reason\": \"<short explanation>\"\n" +
                        "}"
                    );
                    systemInstruction.put("parts", new JSONArray().put(sysParts));

                    List<String> cappedPerms = requestedPermissions.size() > 25 ? requestedPermissions.subList(0, 25) : requestedPermissions;
                    List<String> cappedActs = declaredActivities.size() > 25 ? declaredActivities.subList(0, 25) : declaredActivities;

                    JSONObject contentObj = new JSONObject();
                    JSONObject userPart = new JSONObject();
                    userPart.put("text", "Identify and classify the application:\nPackage Name: " + packageName +
                            "\nApp Label: " + appLabel +
                            "\nApp Category: " + appCategory +
                            "\nRequested Permissions: " + cappedPerms.toString() +
                            "\nDeclared Activities (sample): " + cappedActs.toString());
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

                                    // Suspend if confidence >= 0.85 and violation category is explicit
                                    boolean isRisky = rawRisky && (confidence >= 0.85) && !"NONE".equalsIgnoreCase(category);

                                    Log.i(TAG, "Gemini Model [" + modelName + "] Result for [" + packageName + "]: is_risky=" + isRisky +
                                            " (raw=" + rawRisky + ", conf=" + confidence + ", cat=" + category + "), summary: " + summary + ", reason: " + reason);
                                    aiSuccess = true;
                                    removePendingAudit(context, packageName);

                                    // Step 5A: Route decision in Rescue Mode (verifying provisional browser suspension)
                                    if (isRescueMode) {
                                        if (SecurityConfig.isBlocklisted(context, packageName)) {
                                            SecurityPipelineManager.markPackageBlocked(context, packageName);
                                            SecurityLogger.log(context, "[AI_BLOCKLIST_PRESERVED]", packageName + " -> Explicit Blocklist Match -> Maintained Suspended");
                                            if (!isUpdate) {
                                                SecurityPipelineManager.recordBlockedAppInstallStrike(context, packageName, "Explicit Blocklist Match");
                                            }
                                        } else if (!isRisky) {
                                            // Contested app: OS flagged as browser, but static AI rescued.
                                            // Enroll in Targeted Visual Probation (5 shots / 5 mins) ONLY for non-system third-party apps
                                            if (!SecurityConfig.isProtectedSystemApp(context, packageName) &&
                                                    !SecurityConfig.isManagedChromeWebApk(context, packageName) &&
                                                    AppTimerManager.getAppLimitMinutes(context, packageName) <= 0) {
                                                SecurityConfig.startProbation(context, packageName);
                                                ImpulseGuardService.setMonitoredApp(context, packageName, true);
                                                unsuspendPackage(context, packageName);
                                                SecurityLogger.log(context, "[AI_PROBATION_START]", packageName + " -> Tier 2 rescue entered Visual Probation & Live Guard: " + reason);
                                            } else {
                                                SecurityPipelineManager.markPackageSafe(context, packageName);
                                                unsuspendPackage(context, packageName);
                                                SecurityLogger.log(context, "[AI_PASSED_SAFE]", packageName + " -> Tier 2 rescue passed safe: " + reason);
                                            }
                                        } else {
                                            // Confirmed risky: maintain suspension & record Play Store strike (fresh install only)
                                            SecurityPipelineManager.markPackageBlocked(context, packageName);
                                            SecurityConfig.addToUserBlocklist(context, packageName);
                                            SecurityLogger.log(context, "[AI_VERIFIED]", packageName + " -> Confirmed Prohibited by Gemini AI (" + modelName + "): " + reason);
                                            if (!isUpdate) {
                                                SecurityPipelineManager.recordBlockedAppInstallStrike(context, packageName, "Gemini AI [" + category + "]: " + reason);
                                            }
                                        }
                                    } 
                                    // Step 5B: Route decision in Gray-Area Scan Mode
                                    else {
                                        if (isRisky || SecurityConfig.isBlocklisted(context, packageName)) {
                                            suspendPackage(context, packageName, "Gemini AI (" + modelName + "): [" + category + "] " + reason);
                                            SecurityPipelineManager.markPackageBlocked(context, packageName);
                                            SecurityConfig.addToUserBlocklist(context, packageName);
                                            SecurityLogger.log(context, "[AI_RISKY_SUSPEND]", packageName + " -> Suspended by Gemini AI (" + modelName + "): [" + category + "] " + reason);
                                            if (!isUpdate) {
                                                SecurityPipelineManager.recordBlockedAppInstallStrike(context, packageName, "Gemini AI [" + category + "]: " + reason);
                                            }
                                        } else {
                                            // Ensure any provisional quarantine is lifted once verified safe
                                            unsuspendPackage(context, packageName);
                                            // NEVER enroll clearly safe apps into Visual Probation solely due to low confidence
                                            // (models frequently output confidence=0.05 to mean 5% risk probability for safe apps!).
                                            boolean isCandidate = isBrowserOrDownloaderCandidate(context, packageName, appLabel, appCategory);
                                            if (isCandidate && !SecurityConfig.isProtectedSystemApp(context, packageName) &&
                                                    !SecurityConfig.isManagedChromeWebApk(context, packageName) &&
                                                    AppTimerManager.getAppLimitMinutes(context, packageName) <= 0) {
                                                SecurityConfig.startProbation(context, packageName);
                                                ImpulseGuardService.setMonitoredApp(context, packageName, true);
                                                SecurityLogger.log(context, "[AI_PROBATION_START]", packageName + " -> Suspicious browser/downloader candidate entered Visual Probation: " + summary);
                                            } else {
                                                SecurityPipelineManager.markPackageSafe(context, packageName);
                                                if ("VIDEO".equalsIgnoreCase(appCategory) || "SOCIAL".equalsIgnoreCase(appCategory)) {
                                                    ImpulseGuardService.setMonitoredApp(context, packageName, true);
                                                }
                                                SecurityLogger.log(context, "[AI_PASSED_SAFE]", packageName + " -> Passed Safe by Gemini AI (" + modelName + "): " + summary);
                                            }
                                        }
                                    }
                                    break;
                                }
                            }
                        }
                    } else {
                        long now = System.currentTimeMillis();
                        if (responseCode == 429) {
                            sAuditorModelCooldownUntil.put(modelName, now + 90 * 1000L);
                        } else if (responseCode == 404 || responseCode == 403 || responseCode == 402) {
                            sAuditorModelCooldownUntil.put(modelName, now + 15 * 60 * 1000L);
                        } else if (responseCode >= 500) {
                            sAuditorModelCooldownUntil.put(modelName, now + 30 * 1000L);
                        }
                        Log.w(TAG, "Gemini API Model [" + modelName + "] returned HTTP Code: " + responseCode + " (cooldown applied)");
                    }
                    conn.disconnect();
                } catch (Exception e) {
                    Log.w(TAG, "Model [" + modelName + "] failed for package " + packageName, e);
                }
            }
        }

        // Step 6: Offline & Quota-Exhaustion Fallback Handling
        if (!aiSuccess) {
            enqueuePendingAudit(context, packageName);
            if (SecurityConfig.isBlocklisted(context, packageName)) {
                SecurityPipelineManager.markPackageBlocked(context, packageName);
                SecurityLogger.log(context, "[AI_OFFLINE]", packageName + " -> Explicit Blocklist Match maintained");
                if (!isUpdate) {
                    SecurityPipelineManager.recordBlockedAppInstallStrike(context, packageName, "Explicit Blocklist Match (Offline)");
                }
            } else if (!isRescueMode) {
                boolean isOffline = !GeminiGuardEngine.isNetworkConnected(context);
                boolean isSystemOrUpdate = isUpdate || SecurityConfig.isProtectedSystemApp(context, packageName) ||
                        SecurityConfig.isManagedChromeWebApk(context, packageName);
                boolean suspiciousCandidate = isBrowserOrDownloaderCandidate(context, packageName, appLabel, appCategory);
                if (isOffline && !isSystemOrUpdate) {
                    // Device is intentionally offline during a brand-new third-party app install: quarantine provisionally
                    suspendPackage(context, packageName, "Offline quarantine pending AI verification upon network reconnect");
                    SecurityLogger.log(context, "[OFFLINE_QUARANTINE]", packageName + " -> Provisionally suspended pending Gemini AI audit upon network connection.");
                    if (suspiciousCandidate) {
                        SecurityPipelineManager.recordBlockedAppInstallStrike(context, packageName, "Offline install of suspicious package");
                    }
                } else if (suspiciousCandidate && !isSystemOrUpdate) {
                    // Online, but Gemini API quota/billing exhausted AND fresh install matches strict browser/downloader/movie/VPN heuristics
                    suspendPackage(context, packageName, "Quota-fallback quarantine for suspicious browser/downloader/movie/VPN package");
                    SecurityLogger.log(context, "[QUOTA_FALLBACK_QUARANTINE]", packageName + " -> Suspended by local heuristic while Gemini API is unavailable.");
                    SecurityPipelineManager.recordBlockedAppInstallStrike(context, packageName, "Quota-fallback quarantine for restricted candidate");
                } else {
                    // Routine update, system app, or benign utility/game/banking app while Gemini is offline/rate-limited:
                    // NEVER lock the user out of legitimate apps!
                    unsuspendPackage(context, packageName);
                    SecurityLogger.log(context, "[QUOTA_FALLBACK_ALLOW]", packageName + " -> Allowed by local heuristic while Gemini API is unavailable (queued for later audit).");
                }
            } else {
                SecurityLogger.log(context, "[AI_OFFLINE]", packageName + " -> Tier 2 provisional block maintained (Enqueued for AI rescue check)");
                if (!isUpdate) {
                    SecurityPipelineManager.recordBlockedAppInstallStrike(context, packageName, "Tier 2 Browser Suspension");
                }
            }
        }
    }

    // =====================================================================================
    // SECTION 4: DevicePolicyManager Enforcement Mechanics & Local Heuristics
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

    /**
     * Deterministic 0ms inspector that catches Movie/Streaming/OTT apps, Short-Drama apps,
     * and Video Players / Hubs with built-in BrowserActivity or WebView downloaders without
     * spending any Gemini API quota.
     */
    private static String getDeterministicProhibitedConduitReason(
            String packageName, String appLabel, String appCategory, List<String> declaredActivities) {
        String pkg = (packageName != null ? packageName : "").toLowerCase(Locale.US);
        String label = (appLabel != null ? appLabel : "").toLowerCase(Locale.US);
        String combined = pkg + " " + label;

        // Exempt official Google & essential productivity/music apps
        if (pkg.startsWith("com.google.android.") && !pkg.contains("chrome") && !pkg.contains("youtube")) {
            return null;
        }
        if ("com.spotify.music".equals(pkg) || "com.whatsapp".equals(pkg) ||
                "com.openai.chatgpt".equals(pkg) || "com.anthropic.claude".equals(pkg) ||
                "us.zoom.videomeetings".equals(pkg) || "com.microsoft.teams".equals(pkg) ||
                "com.canva.editor".equals(pkg)) {
            return null;
        }

        // 1. Movie / TV / Free Cinema / OTT / Short-Drama Streaming Apps
        if (combined.contains("streamflix") || combined.contains("moviebox") ||
                combined.contains("hdobox") || combined.contains("hdo box") ||
                combined.contains("pikashow") || combined.contains("onstream") ||
                combined.contains("cloudstream") || combined.contains("dramabox") ||
                combined.contains("reelshort") || combined.contains("shortmax") ||
                combined.contains("netshort") || combined.contains("goodshort") ||
                combined.contains("flextv") || combined.contains("loklok") ||
                combined.contains("stremio") || combined.contains("tubitv") ||
                combined.contains("popcorntime") || combined.contains("fbox") ||
                combined.contains("cinehub") || combined.contains("cinema hd") ||
                combined.contains("beetv") || combined.contains("teatv") ||
                combined.contains("filmplus") || combined.contains("nova tv") ||
                label.contains("free movie") || label.contains("full movie") ||
                label.contains("watch movie") || label.contains("hd movie") ||
                label.contains("movies & tv") || label.contains("short drama") ||
                (pkg.contains("movie") && !pkg.contains("imobie"))) {
            return "Prohibited Movie / Video Streaming Platform (" + appLabel + ")";
        }

        // 2. Video Downloaders, Status/Reel Downloaders & Download Managers
        if (combined.contains("videodownloader") || combined.contains("video downloader") ||
                combined.contains("all video downloader") || combined.contains("downloader hub") ||
                combined.contains("utilityhub") || combined.contains("proxhub") ||
                combined.contains("vidmate") || combined.contains("snaptube") ||
                combined.contains("savefrom") || combined.contains("1dm") ||
                combined.contains("torrdroid") || combined.contains("libretorrent") ||
                combined.contains("utorrent") || combined.contains("bittorrent")) {
            return "Prohibited Video / Media Downloader (" + appLabel + ")";
        }

        // 3. Video Players or Utility Hubs declaring embedded Browser/WebView/Sniffer activities
        if (declaredActivities != null && !declaredActivities.isEmpty()) {
            boolean hasBrowserOrSniffActivity = false;
            boolean hasDownloadOrVideoActivity = false;
            for (String act : declaredActivities) {
                if (act == null) continue;
                String lowerAct = act.toLowerCase(Locale.US);
                // Ignore standard AdMob / Facebook / Auth / Billing SDK activities
                if (lowerAct.contains("google.android.gms") || lowerAct.contains("facebook.ads") ||
                        lowerAct.contains("unity3d") || lowerAct.contains("applovin") ||
                        lowerAct.contains("ironsource") || lowerAct.contains("vungle") ||
                        lowerAct.contains("mbridge") || lowerAct.contains("bytedance") ||
                        lowerAct.contains("firebase") || lowerAct.contains("auth") ||
                        lowerAct.contains("oauth") || lowerAct.contains("billing")) {
                    continue;
                }
                if (lowerAct.endsWith("browseractivity") || lowerAct.endsWith("openwebviewactivity") ||
                        lowerAct.contains("sniff") || lowerAct.contains("webbrowser") ||
                        lowerAct.contains("incognito")) {
                    hasBrowserOrSniffActivity = true;
                }
                if (lowerAct.contains("download") || lowerAct.contains("videoplayer") ||
                        lowerAct.contains("torrent") || lowerAct.contains("stream")) {
                    hasDownloadOrVideoActivity = true;
                }
            }
            if (hasBrowserOrSniffActivity && (hasDownloadOrVideoActivity ||
                    combined.contains("video") || combined.contains("player") ||
                    combined.contains("download") || combined.contains("hub") ||
                    combined.contains("saver"))) {
                return "Disguised Video Player / Downloader with Embedded BrowserActivity";
            }
        }

        return null;
    }

    /**
     * Precision check for whether an app's package name, label, or category specifically indicates
     * an unmanaged browser, video scraper/downloader, movie streamer, VPN/proxy, or app cloner.
     * Avoids overly broad substrings like "yandex" (which broke Yango Taxi), "webapk" (Chrome PWAs),
     * "short", "stream", "dual", "island", "player", "download", etc.
     */
    private static boolean isBrowserOrDownloaderCandidate(Context context, String packageName, String appLabel, String appCategory) {
        if (SecurityConfig.isProtectedSystemApp(context, packageName) ||
                SecurityConfig.isManagedChromeWebApk(context, packageName)) {
            return false;
        }
        String pkg = (packageName != null ? packageName : "").toLowerCase(Locale.US);
        String label = (appLabel != null ? appLabel : "").toLowerCase(Locale.US);
        String combined = pkg + " " + label;
        if (pkg.startsWith("com.google.android.") && !pkg.contains("chrome") && !pkg.contains("search")) {
            return false;
        }
        if ("VIDEO".equalsIgnoreCase(appCategory)) {
            return true;
        }
        return combined.contains("browser") ||
                combined.contains("videodownloader") ||
                combined.contains("video downloader") ||
                combined.contains("all downloader") ||
                combined.contains("downloader") ||
                combined.contains("vidmate") ||
                combined.contains("snaptube") ||
                combined.contains("newpipe") ||
                combined.contains("playtube") ||
                combined.contains("fastsave") ||
                combined.contains("orbit browser") ||
                combined.contains("smartbrowse") ||
                combined.contains("proxy") ||
                combined.contains("vpn") ||
                combined.contains("torrent") ||
                combined.contains("duckduckgo") ||
                pkg.contains("yandex.browser") ||
                pkg.contains("yandex.search") ||
                label.contains("yandex browser") ||
                pkg.contains("microsoft.bing") ||
                combined.contains("appcloner") ||
                combined.contains("parallel space") ||
                combined.contains("lbe.parallel") ||
                combined.contains("dualspace") ||
                combined.contains("dual.space") ||
                combined.contains("dual space") ||
                combined.contains("multiaccount") ||
                combined.contains("multispace") ||
                combined.contains("clonespace") ||
                combined.contains("clone app") ||
                combined.contains("2accounts") ||
                combined.contains("virtualxposed") ||
                pkg.contains("oasisfeng.island") ||
                pkg.contains("typeblog.shelter") ||
                combined.contains("moviebox") ||
                combined.contains("streamflix") ||
                combined.contains("cloudstream") ||
                combined.contains("onstream") ||
                combined.contains("dramabox") ||
                combined.contains("reelshort") ||
                combined.contains("shortmax") ||
                combined.contains("shortdrama") ||
                combined.contains("netshort") ||
                combined.contains("goodshort") ||
                combined.contains("flextv") ||
                combined.contains("iptv") ||
                combined.contains("video player") ||
                combined.contains("xplayer") ||
                combined.contains("playit") ||
                combined.contains("mxtech") ||
                combined.contains("kodi") ||
                combined.contains("stremio") ||
                combined.contains("plexapp") ||
                combined.contains("tubitv");
    }

    // =====================================================================================
    // SECTION 5: Targeted Visual Probation Vision Inspector (Gemini Vision)
    // =====================================================================================

    /**
     * Callback interface for asynchronous screenshot evaluation.
     */
    public interface ScreenshotAuditCallback {
        void onResult(boolean isBrowserUi, double confidence, String reason);
    }

    /**
     * Inspects a downscaled screen capture using Gemini Vision models to detect browser omniboxes,
     * URL navigation bars, and unmanaged web scrapers.
     *
     * @param context     Application context
     * @param packageName Target package being evaluated under probation
     * @param bitmap      Pre-downscaled hardware/software screenshot bitmap
     * @param callback    Callback for asynchronously returning the vision verdict
     */
    public static void auditScreenshotAsync(final Context context, final String packageName, final Bitmap bitmap, final ScreenshotAuditCallback callback) {
        if (context == null || bitmap == null) {
            if (callback != null) callback.onResult(false, 0.0, "Null context or bitmap");
            return;
        }

        final String apiKey = getGeminiApiKey(context);
        if (apiKey.isEmpty()) {
            Log.w(TAG, "Cannot perform visual probation audit: Gemini API Key is empty");
            if (callback != null) callback.onResult(false, 0.0, "API Key empty");
            return;
        }

        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    // Compress downscaled frame to JPEG Base64
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 75, baos);
                    byte[] imageBytes = baos.toByteArray();
                    String base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP);

                    String prompt = "Analyze this mobile screen screenshot for an app currently under security audit:\n" +
                            "Target Package: " + packageName + "\n\n" +
                            "Determine if this screen CLEARLY displays an UNMANAGED OPEN WEB BROWSER UI:\n" +
                            "1. User-editable URL Omnibox / Address Bar explicitly built for arbitrary web navigation (e.g., \"Search or type web address\", \"https://\", arbitrary domain bar + browser tab counter).\n" +
                            "2. Full Browser Navigation Chrome (Back/Forward arrows, refresh button, open-tab switcher, bookmarks bar) for unconstrained public web browsing.\n" +
                            "3. Video Downloader browser mode (URL search bar used to paste arbitrary web links or scrape media from websites).\n\n" +
                            "STRICT ANTI-FALSE-POSITIVE INSTRUCTIONS:\n" +
                            "- Mark is_browser_ui: true ONLY when there is unmistakable positive visual evidence of an open web browser URL bar / tab switcher.\n" +
                            "- Standard native app search bars (SMS/messaging search, ride-hailing/map destination search, file transfer search, social feed search, settings, games, OAuth login screens, help articles, or single-site Chrome PWAs without a URL bar) MUST be marked is_browser_ui: false.\n" +
                            "- When in doubt, mark is_browser_ui: false (presumption of innocence).\n\n" +
                            "Respond ONLY with valid JSON:\n" +
                            "{\n" +
                            "  \"is_browser_ui\": true/false,\n" +
                            "  \"confidence\": 0.0 - 1.0,\n" +
                            "  \"evidence\": \"Describe visible elements\",\n" +
                            "  \"reason\": \"Clear explanation of verdict\"\n" +
                            "}";

                    JSONObject textPart = new JSONObject();
                    textPart.put("text", prompt);

                    JSONObject inlineData = new JSONObject();
                    inlineData.put("mime_type", "image/jpeg");
                    inlineData.put("data", base64Image);

                    JSONObject imagePart = new JSONObject();
                    imagePart.put("inline_data", inlineData);

                    JSONArray parts = new JSONArray();
                    parts.put(textPart);
                    parts.put(imagePart);

                    JSONObject contentObj = new JSONObject();
                    contentObj.put("parts", parts);

                    JSONArray contents = new JSONArray();
                    contents.put(contentObj);

                    JSONObject genConfig = new JSONObject();
                    genConfig.put("temperature", 0.0);
                    genConfig.put("response_mime_type", "application/json");

                    JSONObject payload = new JSONObject();
                    payload.put("contents", contents);
                    payload.put("generationConfig", genConfig);

                    byte[] postData = payload.toString().getBytes(StandardCharsets.UTF_8);

                    boolean success = false;
                    for (String model : GEMINI_MODELS) {
                        try {
                            String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey;
                            URL url = new URL(endpoint);
                            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                            conn.setRequestMethod("POST");
                            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                            conn.setConnectTimeout(8000);
                            conn.setReadTimeout(12000);
                            conn.setDoOutput(true);

                            try (OutputStream os = conn.getOutputStream()) {
                                os.write(postData);
                                os.flush();
                            }

                            int code = conn.getResponseCode();
                            if (code >= 200 && code < 300) {
                                StringBuilder sb = new StringBuilder();
                                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                                    String line;
                                    while ((line = reader.readLine()) != null) {
                                        sb.append(line);
                                    }
                                }
                                JSONObject root = new JSONObject(sb.toString());
                                JSONArray candidates = root.optJSONArray("candidates");
                                if (candidates != null && candidates.length() > 0) {
                                    JSONObject first = candidates.getJSONObject(0);
                                    JSONObject content = first.optJSONObject("content");
                                    if (content != null) {
                                        JSONArray rParts = content.optJSONArray("parts");
                                        if (rParts != null && rParts.length() > 0) {
                                            String replyText = rParts.getJSONObject(0).optString("text", "{}");
                                            replyText = replyText.trim();
                                            if (replyText.startsWith("```json")) {
                                                replyText = replyText.substring(7);
                                            } else if (replyText.startsWith("```")) {
                                                replyText = replyText.substring(3);
                                            }
                                            if (replyText.endsWith("```")) {
                                                replyText = replyText.substring(0, replyText.length() - 3);
                                            }
                                            JSONObject resultJson = new JSONObject(replyText.trim());
                                            boolean isBrowserUi = resultJson.optBoolean("is_browser_ui", false);
                                            double conf = resultJson.optDouble("confidence", 1.0);
                                            String reason = resultJson.optString("reason", "No reason");
                                            String evidence = resultJson.optString("evidence", "");

                                            Log.i(TAG, "Visual Probation Scan [" + model + "] for [" + packageName + "]: is_browser=" + isBrowserUi +
                                                    ", conf=" + conf + ", evidence=" + evidence + ", reason=" + reason);

                                            if (callback != null) {
                                                callback.onResult(isBrowserUi, conf, reason);
                                            }
                                            success = true;
                                            break;
                                        }
                                    }
                                }
                            } else {
                                Log.w(TAG, "Visual Probation API Model [" + model + "] HTTP " + code);
                            }
                            conn.disconnect();
                        } catch (Exception e) {
                            Log.w(TAG, "Visual Probation Model [" + model + "] failed: " + e.getMessage());
                        }
                    }

                    if (!success && callback != null) {
                        callback.onResult(false, 0.0, "AI vision request failed on all models");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error in auditScreenshotAsync for " + packageName, e);
                    if (callback != null) {
                        callback.onResult(false, 0.0, e.getMessage());
                    }
                } finally {
                    try {
                        if (!bitmap.isRecycled()) {
                            bitmap.recycle();
                        }
                    } catch (Exception ignored) {}
                }
            }
        });
    }
}

