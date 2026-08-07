package com.afwsamples.testdpc;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

public class ExperimentalAiScanner {
    private static final String TAG = "ExperimentalAiScanner";
    private static final String PREF_NAME = "dpclocker_ai_scanner";
    private static final String KEY_ENABLED = "ai_shield_enabled";
    private static final String KEY_THRESHOLD_PERCENT = "ai_threshold_percent";

    private static HandlerThread sHandlerThread;
    private static Handler sBackgroundHandler;
    private static boolean sIsRunning = false;

    public static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_ENABLED, false);
    }

    public static void setEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
        if (enabled) {
            startScanner(context);
        } else {
            stopScanner();
        }
    }

    public static int getThresholdPercent(Context context) {
        return getPrefs(context).getInt(KEY_THRESHOLD_PERCENT, 25); // Default 25% for strict protection
    }

    public static void setThresholdPercent(Context context, int percent) {
        if (percent < 10) percent = 10;
        if (percent > 80) percent = 80;
        getPrefs(context).edit().putInt(KEY_THRESHOLD_PERCENT, percent).apply();
        Log.i(TAG, "Updated AI Sensitivity Threshold to: " + percent + "%");
    }

    public static synchronized void startScanner(final Context context) {
        if (sIsRunning) return;
        sIsRunning = true;

        sHandlerThread = new HandlerThread("ExperimentalAiScannerThread");
        sHandlerThread.start();
        sBackgroundHandler = new Handler(sHandlerThread.getLooper());

        Log.i(TAG, "Experimental AI Screen Shield Started with threshold: " + getThresholdPercent(context) + "%");
    }

    public static synchronized void stopScanner() {
        sIsRunning = false;
        if (sHandlerThread != null) {
            sHandlerThread.quitSafely();
            sHandlerThread = null;
            sBackgroundHandler = null;
        }
        Log.i(TAG, "Experimental AI Screen Shield Stopped");
    }

    /**
     * Fast Heuristic Vision Classifier: Analyzes a sample Bitmap for customizable skin-density thresholds.
     */
    public static boolean analyzeBitmap(Context context, Bitmap bitmap) {
        if (bitmap == null) return false;

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int sampleStep = 10; // Sample every 10th pixel for 100x performance
        int totalPixels = 0;
        int skinPixels = 0;

        float[] hsv = new float[3];

        for (int x = 0; x < width; x += sampleStep) {
            for (int y = 0; y < height; y += sampleStep) {
                int pixel = bitmap.getPixel(x, y);
                int r = Color.red(pixel);
                int g = Color.green(pixel);
                int b = Color.blue(pixel);

                Color.colorToHSV(pixel, hsv);
                float hue = hsv[0];
                float saturation = hsv[1];

                totalPixels++;

                // HSV & RGB Skin Density Classifier Thresholds
                if (r > 95 && g > 40 && b > 20 &&
                        (Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) > 15) &&
                        Math.abs(r - g) > 15 && r > g && r > b) {
                    if (hue >= 0 && hue <= 50 && saturation >= 0.23 && saturation <= 0.68) {
                        skinPixels++;
                    }
                }
            }
        }

        if (totalPixels == 0) return false;

        double skinRatio = (double) skinPixels / totalPixels;
        double targetThreshold = getThresholdPercent(context) / 100.0;

        Log.d(TAG, "Experimental AI Scan: Skin Ratio = " + String.format("%.2f", skinRatio * 100) + "% / Target Threshold = " + getThresholdPercent(context) + "%");

        return skinRatio > targetThreshold;
    }
}
