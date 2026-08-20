package com.afwsamples.testdpc;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.util.Log;

import ai.onnxruntime.NodeInfo;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * On-Device Falcons.ai Vision Transformer (ViT) NSFW Detection Engine.
 * Runs 100% locally and offline via Microsoft ONNX Runtime Mobile.
 */
public class FalconsVisionGuardEngine {

    private static final String TAG = "FalconsVisionGuard";
    private static final String PREFS_NAME = "falcons_vision_guard_prefs";
    private static final String KEY_ENABLED = "falcons_vision_enabled";
    private static final String KEY_THRESHOLD = "falcons_vision_threshold";
    private static final String KEY_LOGS = "falcons_vision_logs";

    public static final String MODEL_FILE_NAME = "falcons_nsfw_quantized.onnx";
    public static final float DEFAULT_THRESHOLD = 0.85f;

    private static final int INPUT_SIZE = 224;
    private static final float[] MEAN = new float[]{0.5f, 0.5f, 0.5f};
    private static final float[] STD = new float[]{0.5f, 0.5f, 0.5f};

    private static OrtEnvironment sOrtEnv;
    private static OrtSession sOrtSession;
    private static final Object sLock = new Object();
    private static final AtomicBoolean sIsInitializing = new AtomicBoolean(false);
    private static final ConcurrentHashMap<Long, Boolean> sBitmapVerdictCache = new ConcurrentHashMap<>();
    private static final ExecutorService sWorkerExecutor = Executors.newSingleThreadExecutor();

    public static class VisionResult {
        public final boolean isNsfw;
        public final float nsfwProbability;
        public final float normalProbability;
        public final long latencyMs;
        public final String reason;

        public VisionResult(boolean isNsfw, float nsfwProbability, float normalProbability, long latencyMs, String reason) {
            this.isNsfw = isNsfw;
            this.nsfwProbability = nsfwProbability;
            this.normalProbability = normalProbability;
            this.latencyMs = latencyMs;
            this.reason = reason;
        }
    }

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_ENABLED, false);
    }

    public static void setEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
        Log.i(TAG, "Falcons Vision Guard enabled set to: " + enabled);
    }

    public static float getThreshold(Context context) {
        return getPrefs(context).getFloat(KEY_THRESHOLD, DEFAULT_THRESHOLD);
    }

    public static void setThreshold(Context context, float threshold) {
        getPrefs(context).edit().putFloat(KEY_THRESHOLD, threshold).apply();
        Log.i(TAG, "Falcons Vision Guard threshold set to: " + threshold);
    }

    public static String getLogs(Context context) {
        String logs = getPrefs(context).getString(KEY_LOGS, "");
        if (logs.isEmpty()) {
            return "No Falcons.ai visual events recorded yet.";
        }
        return logs;
    }

    public static void appendLog(Context context, String entry) {
        String timestamp = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(new Date());
        String currentLogs = getPrefs(context).getString(KEY_LOGS, "");
        String newLogs = "[" + timestamp + "] " + entry + "\n" + currentLogs;
        if (newLogs.length() > 10000) {
            newLogs = newLogs.substring(0, 10000);
        }
        getPrefs(context).edit().putString(KEY_LOGS, newLogs).apply();
    }

    public static void clearLogs(Context context) {
        getPrefs(context).edit().remove(KEY_LOGS).apply();
    }

    /**
     * Ensures the ONNX model is available either from assets or internal storage.
     */
    public static File getOrCopyModelFile(Context context) {
        File internalModelDir = new File(context.getFilesDir(), "models");
        if (!internalModelDir.exists()) {
            internalModelDir.mkdirs();
        }
        File modelFile = new File(internalModelDir, MODEL_FILE_NAME);

        if (modelFile.exists() && modelFile.length() > 1000000) {
            return modelFile;
        }

        // 1. Check Download directory (/sdcard/Download/falcons_models/)
        File externalModel = new File("/sdcard/Download/falcons_models/" + MODEL_FILE_NAME);
        if (externalModel.exists() && externalModel.length() > 1000000) {
            try (InputStream is = new java.io.FileInputStream(externalModel);
                 FileOutputStream fos = new FileOutputStream(modelFile)) {
                byte[] buffer = new byte[16384];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, read);
                }
                fos.flush();
                Log.i(TAG, "Imported " + MODEL_FILE_NAME + " from /sdcard/Download/falcons_models/ to internal storage!");
                return modelFile;
            } catch (Exception e) {
                Log.w(TAG, "Could not copy from external storage: " + e.getMessage());
            }
        }

        // 2. Try extracting from APK assets
        try (InputStream is = context.getAssets().open("models/" + MODEL_FILE_NAME);
             FileOutputStream fos = new FileOutputStream(modelFile)) {
            byte[] buffer = new byte[16384];
            int read;
            while ((read = is.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
            }
            fos.flush();
            Log.i(TAG, "Successfully extracted " + MODEL_FILE_NAME + " from assets to internal storage.");
            return modelFile;
        } catch (Exception e) {
            Log.w(TAG, "Model not found in assets. Checking download storage: " + e.getMessage());
        }

        return modelFile.exists() ? modelFile : null;
    }

    public static boolean isModelReady(Context context) {
        File model = getOrCopyModelFile(context);
        return model != null && model.exists() && model.length() > 1000000;
    }

    /**
     * Initializes the ONNX Runtime session with hardware acceleration fallback.
     */
    private static boolean ensureInitialized(Context context) {
        if (sOrtSession != null) return true;

        synchronized (sLock) {
            if (sOrtSession != null) return true;

            File modelFile = getOrCopyModelFile(context);
            if (modelFile == null || !modelFile.exists()) {
                Log.w(TAG, "Falcons ONNX Model file not found on device.");
                return false;
            }

            try {
                if (sOrtEnv == null) {
                    sOrtEnv = OrtEnvironment.getEnvironment();
                }

                OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
                opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
                opts.setIntraOpNumThreads(2);

                // Attempt NNAPI hardware acceleration on mobile chip
                try {
                    opts.addNnapi();
                    Log.i(TAG, "ONNX NNAPI Execution Provider enabled for Falcons.ai.");
                } catch (Exception nnapiEx) {
                    Log.w(TAG, "NNAPI not supported on this device. Defaulting to optimized CPU provider.", nnapiEx);
                }

                sOrtSession = sOrtEnv.createSession(modelFile.getAbsolutePath(), opts);
                Log.i(TAG, "Falcons.ai Vision Transformer session initialized successfully!");
                return true;

            } catch (Exception e) {
                Log.e(TAG, "Error initializing ONNX Runtime session for Falcons.ai", e);
                return false;
            }
        }
    }

    /**
     * Classifies a captured screen Bitmap locally on-device in ~50ms.
     */
    public static VisionResult evaluateBitmap(Context context, String packageName, Bitmap bitmap) {
        long startTime = SystemClock.elapsedRealtime();

        if (bitmap == null || bitmap.isRecycled()) {
            return new VisionResult(false, 0.0f, 1.0f, 0, "null_bitmap");
        }

        if (!ensureInitialized(context)) {
            return new VisionResult(false, 0.0f, 1.0f, 0, "model_not_ready");
        }

        long quickHash = computeQuickHash(bitmap);
        Boolean cachedVerdict = sBitmapVerdictCache.get(quickHash);
        if (cachedVerdict != null) {
            long latency = SystemClock.elapsedRealtime() - startTime;
            return new VisionResult(cachedVerdict, cachedVerdict ? 0.95f : 0.05f, cachedVerdict ? 0.05f : 0.95f, latency, "ram_hash_cache");
        }

        Bitmap scaledBitmap = null;
        OnnxTensor inputTensor = null;
        OrtSession.Result ortResult = null;

        try {
            // 1. Resize to ViT patch input: 224 x 224
            scaledBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true);

            int[] pixels = new int[INPUT_SIZE * INPUT_SIZE];
            scaledBitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE);

            // 2. Preprocess into FloatBuffer in CHW format (1 x 3 x 224 x 224)
            FloatBuffer floatBuffer = FloatBuffer.allocate(1 * 3 * INPUT_SIZE * INPUT_SIZE);
            int channelSize = INPUT_SIZE * INPUT_SIZE;

            // Red channel
            for (int i = 0; i < channelSize; i++) {
                int c = pixels[i];
                float r = (((c >> 16) & 0xFF) / 255.0f - MEAN[0]) / STD[0];
                floatBuffer.put(i, r);
            }
            // Green channel
            for (int i = 0; i < channelSize; i++) {
                int c = pixels[i];
                float g = (((c >> 8) & 0xFF) / 255.0f - MEAN[1]) / STD[1];
                floatBuffer.put(channelSize + i, g);
            }
            // Blue channel
            for (int i = 0; i < channelSize; i++) {
                int c = pixels[i];
                float b = ((c & 0xFF) / 255.0f - MEAN[2]) / STD[2];
                floatBuffer.put((2 * channelSize) + i, b);
            }

            floatBuffer.rewind();

            // 3. Create input tensor and run inference
            inputTensor = OnnxTensor.createTensor(sOrtEnv, floatBuffer, new long[]{1, 3, INPUT_SIZE, INPUT_SIZE});
            ortResult = sOrtSession.run(Collections.singletonMap("pixel_values", inputTensor));

            float[][] logits = (float[][]) ortResult.get(0).getValue();
            float normalLogit = logits[0][0];
            float nsfwLogit = logits[0][1];

            // 4. Softmax computation
            float maxLogit = Math.max(normalLogit, nsfwLogit);
            float expNormal = (float) Math.exp(normalLogit - maxLogit);
            float expNsfw = (float) Math.exp(nsfwLogit - maxLogit);
            float probNsfw = expNsfw / (expNormal + expNsfw);
            float probNormal = expNormal / (expNormal + expNsfw);

            long latencyMs = SystemClock.elapsedRealtime() - startTime;
            float threshold = getThreshold(context);
            boolean isNsfw = probNsfw >= threshold;

            // Cache result
            sBitmapVerdictCache.put(quickHash, isNsfw);
            if (sBitmapVerdictCache.size() > 500) {
                sBitmapVerdictCache.clear();
            }

            String logEntry = String.format(Locale.US, "[%s] Falcons.ai Vision (%dms): NSFW=%.1f%%, Normal=%.1f%% -> %s",
                    packageName, latencyMs, probNsfw * 100f, probNormal * 100f, isNsfw ? "ADULT BLOCKED" : "SAFE PASS");
            Log.i(TAG, logEntry);

            if (isNsfw) {
                appendLog(context, logEntry);
                SecurityLogger.log(context, "[FALCONS_NSFW]", logEntry);
            }

            return new VisionResult(isNsfw, probNsfw, probNormal, latencyMs, isNsfw ? "nsfw_detected" : "safe_pass");

        } catch (Exception e) {
            Log.e(TAG, "Error executing Falcons.ai inference", e);
            long latencyMs = SystemClock.elapsedRealtime() - startTime;
            return new VisionResult(false, 0.0f, 1.0f, latencyMs, "inference_error: " + e.getMessage());

        } finally {
            if (scaledBitmap != null && !scaledBitmap.isRecycled()) {
                scaledBitmap.recycle();
            }
            if (inputTensor != null) {
                inputTensor.close();
            }
            if (ortResult != null) {
                ortResult.close();
            }
        }
    }

    private static long computeQuickHash(Bitmap bitmap) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        long hash = 17;
        for (int x = 0; x < w; x += w / 4) {
            for (int y = 0; y < h; y += h / 4) {
                hash = hash * 31 + bitmap.getPixel(x, y);
            }
        }
        return hash;
    }
}
