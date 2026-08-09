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
 * Manages daily progressive penalty escalation (1m -> 10m -> 30m default) with a 24-hour daily reset
 * for Gemini AI Guard violations.
 */
public class PenaltyManager {

    private static final String TAG = "PenaltyManager";
    public static final String PREF_PENALTIES = "gemini_penalty_history";

    public static final String KEY_V1_MINS = "penalty_v1_mins";
    public static final String KEY_V2_MINS = "penalty_v2_mins";
    public static final String KEY_V3_MINS = "penalty_v3_mins";
    public static final String KEY_V4_MINS = "penalty_v4_mins";

    public static final long ONE_DAY_MS = 24 * 60 * 60 * 1000L; // 24 Hours

    public static class PenaltyInfo {
        public int violationCount;
        public int durationMinutes;
        public long durationMs;
    }

    public static int getCustomPenaltyMinutes(Context context, int violationNumber) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_PENALTIES, Context.MODE_PRIVATE);
        switch (violationNumber) {
            case 1:
                return prefs.getInt(KEY_V1_MINS, 1);
            case 2:
                return prefs.getInt(KEY_V2_MINS, 10);
            case 3:
                return prefs.getInt(KEY_V3_MINS, 30);
            default:
                return prefs.getInt(KEY_V4_MINS, 30);
        }
    }

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

    public static PenaltyInfo recordViolationAndGetPenalty(Context context, String pkgName) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_PENALTIES, Context.MODE_PRIVATE);
        String countKey = "count_" + pkgName;
        String timeKey = "time_" + pkgName;

        long lastTime = prefs.getLong(timeKey, 0L);
        int currentCount = prefs.getInt(countKey, 0);
        long now = System.currentTimeMillis();

        // 24-hour reset rule: Reset violation count if a day (24 hours) passes without violations
        if (lastTime > 0 && (now - lastTime >= ONE_DAY_MS)) {
            Log.i(TAG, "24 hours passed since last violation for [" + pkgName + "]. Resetting daily violation count back to 1!");
            currentCount = 0;
        }

        currentCount++; // Increment violation count for today

        prefs.edit()
                .putInt(countKey, currentCount)
                .putLong(timeKey, now)
                .apply();

        PenaltyInfo info = new PenaltyInfo();
        info.violationCount = currentCount;
        info.durationMinutes = getCustomPenaltyMinutes(context, currentCount);
        info.durationMs = info.durationMinutes * 60 * 1000L;

        Log.w(TAG, "RECORDED VIOLATION #" + currentCount + " for [" + pkgName + "]. Penalty Duration: " + info.durationMinutes + " minute(s).");
        return info;
    }
}
