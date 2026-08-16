package com.afwsamples.testdpc;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * High-performance, rolling telemetry logger for Test DPC / DpcLocker security events.
 * Logs Tier 1 Whitelist evaluations, Tier 2 Deterministic Suspensions, and AI False-Positive Rescues.
 */
public class SecurityLogger {

    private static final String TAG = "SecurityLogger";
    private static final String PREFS_NAME = "dpclocker_security_logs";
    private static final String KEY_LOG_ENTRIES = "security_log_entries";
    private static final int MAX_LOG_ENTRIES = 150;

    public static synchronized void log(Context context, String eventTag, String message) {
        try {
            String timeStamp = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(new Date());
            String logLine = "[" + timeStamp + "] " + eventTag + " " + message;
            Log.i(TAG, logLine);

            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String existingLogs = prefs.getString(KEY_LOG_ENTRIES, "");
            String updatedLogs = logLine + "\n" + existingLogs;

            // Trim to max entries to avoid memory bloat
            String[] lines = updatedLogs.split("\n");
            if (lines.length > MAX_LOG_ENTRIES) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < MAX_LOG_ENTRIES; i++) {
                    sb.append(lines[i]).append("\n");
                }
                updatedLogs = sb.toString().trim();
            }

            prefs.edit().putString(KEY_LOG_ENTRIES, updatedLogs).apply();
        } catch (Exception e) {
            Log.e(TAG, "Error writing security log", e);
        }
    }

    public static String getLogs(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String logs = prefs.getString(KEY_LOG_ENTRIES, "");
        if (logs.isEmpty()) {
            return "No security pipeline events recorded yet.";
        }
        return logs;
    }

    public static void clearLogs(Context context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().remove(KEY_LOG_ENTRIES).apply();
        Log.i(TAG, "Security logs cleared.");
    }
}
