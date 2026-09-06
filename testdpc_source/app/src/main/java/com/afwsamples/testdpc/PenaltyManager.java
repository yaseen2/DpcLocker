/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.afwsamples.testdpc;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * ==============================================================================
 * PENALTY MANAGER :: PROGRESSIVE SUSPENSION LADDER & 24-HOUR DAILY RESET
 * ==============================================================================
 * Purpose:
 * When an adult content violation occurs (either an explicit search query detected
 * by GeminiGuardEngine or visual NSFW content hitting Strike 3+ in FalconsVisionGuardEngine),
 * this class calculates how long the violating app must be suspended via Android
 * DevicePolicyManager (DPM).
 *
 * Key Concepts:
 * 1. Escalating Ladder: First offense gets a short penalty (1 min), second offense
 *    escalates to 10 mins, third to 30 mins, and subsequent to 30 mins.
 * 2. 24-Hour Reset Rule: If 24 continuous hours elapse without any violations for
 *    a package, the violation counter resets back to 1.
 * 3. Persistence: All violation counts, timestamps, and custom ladder durations are
 *    saved in SharedPreferences ("gemini_penalty_history").
 * ==============================================================================
 */
public class PenaltyManager {

    // Logcat tag for debugging penalty escalation events
    private static final String TAG = "PenaltyManager";

    // SharedPreferences file name dedicated to penalty tracking
    public static final String PREF_PENALTIES = "gemini_penalty_history";

    // Preference keys for custom penalty durations (in minutes) configured via the UI
    public static final String KEY_V1_MINS = "penalty_v1_mins"; // 1st violation duration
    public static final String KEY_V2_MINS = "penalty_v2_mins"; // 2nd violation duration
    public static final String KEY_V3_MINS = "penalty_v3_mins"; // 3rd violation duration
    public static final String KEY_V4_MINS = "penalty_v4_mins"; // 4th+ violation duration

    // 24 Hours represented in milliseconds (used to evaluate the inactivity reset rule)
    public static final long ONE_DAY_MS = 24 * 60 * 60 * 1000L; // 24 Hours = 86,400,000 ms

    /**
     * Data Transfer Object (DTO) holding penalty calculation details returned to the caller.
     */
    public static class PenaltyInfo {
        // The cumulative number of violations recorded today for this package (e.g., 1, 2, 3...)
        public int violationCount;

        // The penalty duration calculated for this violation in whole minutes
        public int durationMinutes;

        // The penalty duration converted to milliseconds (used for postDelayed and expiry timestamps)
        public long durationMs;
    }

    /**
     * Reads the configured penalty duration in minutes for a specific violation level.
     * Checks SharedPreferences first; if not customized by the user, returns sensible defaults.
     *
     * @param context Android context to access SharedPreferences
     * @param violationNumber Which violation in sequence (1 = 1st, 2 = 2nd, etc.)
     * @return Duration in minutes for which the app should be suspended
     */
    public static int getCustomPenaltyMinutes(Context context, int violationNumber) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_PENALTIES, Context.MODE_PRIVATE);
        switch (violationNumber) {
            case 1:
                // 1st offense default: 1 minute (designed as an immediate impulse break)
                return prefs.getInt(KEY_V1_MINS, 1);
            case 2:
                // 2nd offense default: 10 minutes (moderate cooldown)
                return prefs.getInt(KEY_V2_MINS, 10);
            case 3:
                // 3rd offense default: 30 minutes (extended block)
                return prefs.getInt(KEY_V3_MINS, 30);
            default:
                // 4th and subsequent offenses default: 30 minutes
                return prefs.getInt(KEY_V4_MINS, 30);
        }
    }

    /**
     * Saves user-customized penalty durations into SharedPreferences.
     * Called from PolicyManagementFragment when the user edits the penalty ladder.
     *
     * @param context Android context
     * @param v1 Minutes for 1st violation (clamped to at least 1 min)
     * @param v2 Minutes for 2nd violation (clamped to at least 1 min)
     * @param v3 Minutes for 3rd violation (clamped to at least 1 min)
     * @param v4 Minutes for 4th+ violations (clamped to at least 1 min)
     */
    public static void setCustomPenaltyMinutes(Context context, int v1, int v2, int v3, int v4) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_PENALTIES, Context.MODE_PRIVATE);
        prefs.edit()
                .putInt(KEY_V1_MINS, Math.max(1, v1))
                .putInt(KEY_V2_MINS, Math.max(1, v2))
                .putInt(KEY_V3_MINS, Math.max(1, v3))
                .putInt(KEY_V4_MINS, Math.max(1, v4))
                .apply();
        Log.i(TAG, "Custom Penalty Ladder Durations Saved: 1st=" + v1 + "m, 2nd=" + v2 + "m, 3rd=" + v3 + "m, 4th+=" + v4 + "m");
    }

    /**
     * Records a new violation event for a specific package, updates violation counts & timestamps,
     * evaluates the 24-hour reset rule, and returns a PenaltyInfo object.
     *
     * Workflow:
     * 1. Reads last violation timestamp and current violation count for pkgName.
     * 2. Checks if (now - lastTime >= 24 hours):
     *    - If true, resets currentCount to 0 so this violation becomes #1 again.
     * 3. Increments currentCount by 1.
     * 4. Persists the updated count and current timestamp.
     * 5. Looks up the penalty duration for this violation level and converts to ms.
     *
     * @param context Android context
     * @param pkgName Package name of the violating app (e.g. "com.android.chrome")
     * @return PenaltyInfo containing the new violation count, duration in minutes, and duration in ms
     */
    public static PenaltyInfo recordViolationAndGetPenalty(Context context, String pkgName) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_PENALTIES, Context.MODE_PRIVATE);

        // Keys unique to each package so apps have independent penalty escalation histories
        String countKey = "count_" + pkgName;
        String timeKey = "time_" + pkgName;

        // Fetch previous violation timestamp and count
        long lastTime = prefs.getLong(timeKey, 0L);
        int currentCount = prefs.getInt(countKey, 0);
        long now = System.currentTimeMillis();

        // --------------------------------------------------------------------------
        // 24-Hour Reset Rule:
        // If 24 continuous hours have passed since the previous violation for this app,
        // reward the user for good behavior by resetting the ladder back to step 1.
        // --------------------------------------------------------------------------
        if (lastTime > 0 && (now - lastTime >= ONE_DAY_MS)) {
            Log.i(TAG, "24 hours passed since last violation for [" + pkgName + "]. Resetting daily violation count back to 1!");
            currentCount = 0;
        }

        // Increment today's violation count
        currentCount++;

        // Persist the new state immediately
        prefs.edit()
                .putInt(countKey, currentCount)
                .putLong(timeKey, now)
                .apply();

        // Build and populate the return object
        PenaltyInfo info = new PenaltyInfo();
        info.violationCount = currentCount;
        info.durationMinutes = getCustomPenaltyMinutes(context, currentCount);
        info.durationMs = info.durationMinutes * 60 * 1000L; // Convert minutes to milliseconds

        Log.w(TAG, "RECORDED VIOLATION #" + currentCount + " for [" + pkgName + "]. Penalty Duration: " + info.durationMinutes + " minute(s).");
        return info;
    }
}
