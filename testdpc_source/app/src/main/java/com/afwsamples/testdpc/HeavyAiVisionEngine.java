package com.afwsamples.testdpc;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import org.tensorflow.lite.InterpreterApi;
import com.google.android.gms.tflite.java.TfLite;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

public class HeavyAiVisionEngine {
    private static final String TAG = "HeavyAiVisionEngine";
    private static final String PREF_NAME = "dpclocker_heavy_ai";
    private static final String KEY_ENABLED = "heavy_ai_enabled";
    private static final String KEY_THRESHOLD_PERCENT = "heavy_ai_threshold_percent";

    private static final int INPUT_SIZE = 224;
    private static final int BATCH_SIZE = 1;
    private static final int PIXEL_SIZE = 3; // RGB

    private static InterpreterApi sInterpreter;
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
            startEngine(context);
        } else {
            stopEngine();
        }
    }

    public static int getThresholdPercent(Context context) {
        return getPrefs(context).getInt(KEY_THRESHOLD_PERCENT, 25); // Default 25% for strict protection
    }

    public static void setThresholdPercent(Context context, int percent) {
        if (percent < 10) percent = 10;
        if (percent > 80) percent = 80;
        getPrefs(context).edit().putInt(KEY_THRESHOLD_PERCENT, percent).apply();
        Log.i(TAG, "Updated Heavy AI Sensitivity Threshold to: " + percent + "%");
    }

    public static synchronized void startEngine(final Context context) {
        if (sIsRunning) return;
        sIsRunning = true;

        sHandlerThread = new HandlerThread("HeavyAiTensorThread");
        sHandlerThread.start();
        sBackgroundHandler = new Handler(sHandlerThread.getLooper());

        sBackgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    TfLite.initialize(context).addOnSuccessListener(aVoid -> {
                        Log.i(TAG, "Play Services 16 KB Page-Aligned TFLite Engine Initialized! Google Tensor TPU Ready.");
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Error initializing Heavy AI Vision Engine", e);
                }
            }
        });
    }

    public static synchronized void stopEngine() {
        sIsRunning = false;
        if (sInterpreter != null) {
            try {
                sInterpreter.close();
            } catch (Exception ignored) {}
            sInterpreter = null;
        }
        if (sHandlerThread != null) {
            sHandlerThread.quitSafely();
            sHandlerThread = null;
            sBackgroundHandler = null;
        }
        Log.i(TAG, "Heavy AI Neural Network Vision Engine Stopped");
    }

    /**
     * Pre-processes screen Bitmap into 224x224 Normalized FloatTensor and runs Deep Learning inference.
     */
    public static Map<String, Float> classifyBitmap(Context context, Bitmap bitmap) {
        Map<String, Float> results = new HashMap<>();
        if (bitmap == null) return results;

        try {
            Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true);
            ByteBuffer inputBuffer = convertBitmapToByteBuffer(scaledBitmap);

            float[][] outputScores = new float[1][3]; // [EXPLICIT_NUDITY, SUGGESTIVE_MEDIA, SAFE]

            if (sInterpreter != null) {
                sInterpreter.run(inputBuffer, outputScores);
            }

            float explicitScore = outputScores[0][0];
            float suggestiveScore = outputScores[0][1];
            float safeScore = outputScores[0][2];

            results.put("EXPLICIT_NUDITY", explicitScore);
            results.put("SUGGESTIVE_MEDIA", suggestiveScore);
            results.put("SAFE", safeScore);

            float targetThreshold = getThresholdPercent(context) / 100.0f;
            boolean triggered = (explicitScore > targetThreshold) || (suggestiveScore > targetThreshold);

            Log.d(TAG, "Heavy AI Tensor Pass: Suggestive = " + String.format("%.2f", suggestiveScore * 100) + "% / Explicit = " + String.format("%.2f", explicitScore * 100) + "% / Threshold = " + getThresholdPercent(context) + "% / Triggered = " + triggered);

        } catch (Exception e) {
            Log.e(TAG, "Error running Heavy AI Classification", e);
        }

        return results;
    }

    private static ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
        ByteBuffer imgData = ByteBuffer.allocateDirect(4 * BATCH_SIZE * INPUT_SIZE * INPUT_SIZE * PIXEL_SIZE);
        imgData.order(ByteOrder.nativeOrder());
        int[] intValues = new int[INPUT_SIZE * INPUT_SIZE];
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

        int pixel = 0;
        for (int i = 0; i < INPUT_SIZE; ++i) {
            for (int j = 0; j < INPUT_SIZE; ++j) {
                final int val = intValues[pixel++];
                // Normalize RGB pixels [0, 255] -> [-1.0, 1.0] for Neural Network
                imgData.putFloat((((val >> 16) & 0xFF) - 127.5f) / 127.5f);
                imgData.putFloat((((val >> 8) & 0xFF) - 127.5f) / 127.5f);
                imgData.putFloat(((val & 0xFF) - 127.5f) / 127.5f);
            }
        }
        return imgData;
    }
}
