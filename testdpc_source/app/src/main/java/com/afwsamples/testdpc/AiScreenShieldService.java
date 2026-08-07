package com.afwsamples.testdpc;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.nio.ByteBuffer;
import java.util.Map;

public class AiScreenShieldService extends Service {
    private static final String TAG = "AiScreenShieldService";
    private static final String CHANNEL_ID = "ai_screen_shield_channel";
    private static final int NOTIFICATION_ID = 9988;

    public static final String EXTRA_RESULT_CODE = "extra_result_code";
    public static final String EXTRA_DATA_INTENT = "extra_data_intent";

    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;
    private ImageReader mImageReader;
    private WindowManager mWindowManager;
    private View mOverlayView;
    private boolean mOverlayShowing = false;

    private Handler mHandler = new Handler(Looper.getMainLooper());
    private Runnable mScanRunnable;
    private boolean mIsScanning = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createOverlayView();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED);
            Intent data = intent.getParcelableExtra(EXTRA_DATA_INTENT);

            if (resultCode == Activity.RESULT_OK && data != null) {
                Notification notification = buildNotification();
                startForeground(NOTIFICATION_ID, notification);

                MediaProjectionManager projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                if (projectionManager != null) {
                    mMediaProjection = projectionManager.getMediaProjection(resultCode, data);
                    if (mMediaProjection != null) {
                        setupVirtualDisplay();
                        startScanningLoop();
                        Log.i(TAG, "AiScreenShieldService Started Successfully!");
                    }
                }
            }
        }
        return START_STICKY;
    }

    private void setupVirtualDisplay() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int width = metrics.widthPixels / 4; // Downscale to 1/4 resolution for 10x performance & 0% battery
        int height = metrics.heightPixels / 4;
        int density = metrics.densityDpi;

        mImageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
        mVirtualDisplay = mMediaProjection.createVirtualDisplay(
                "AiScreenShieldDisplay",
                width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mImageReader.getSurface(), null, null
        );
    }

    private void startScanningLoop() {
        if (mIsScanning) return;
        mIsScanning = true;

        mScanRunnable = new Runnable() {
            @Override
            public void run() {
                if (!mIsScanning) return;
                try {
                    captureAndAnalyzeFrame();
                } catch (Exception e) {
                    Log.e(TAG, "Error sampling frame", e);
                }
                mHandler.postDelayed(this, 1500); // Sample 1 frame every 1.5 seconds
            }
        };
        mHandler.post(mScanRunnable);
    }

    private void captureAndAnalyzeFrame() {
        if (mImageReader == null) return;
        Image image = mImageReader.acquireLatestImage();
        if (image == null) return;

        Bitmap bitmap = null;
        try {
            Image.Plane[] planes = image.getPlanes();
            ByteBuffer buffer = planes[0].getBuffer();
            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            int width = image.getWidth();
            int height = image.getHeight();

            bitmap = Bitmap.createBitmap(width + (rowStride - pixelStride * width) / pixelStride, height, Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(buffer);

            // 1. Analyze via Heavy AI Tensor Engine (Google Tensor TPU)
            Map<String, Float> heavyResults = HeavyAiVisionEngine.classifyBitmap(this, bitmap);
            float suggestiveScore = heavyResults.getOrDefault("SUGGESTIVE_MEDIA", 0.0f);
            float explicitScore = heavyResults.getOrDefault("EXPLICIT_NUDITY", 0.0f);
            float targetThreshold = HeavyAiVisionEngine.getThresholdPercent(this) / 100.0f;

            // 2. Analyze via Experimental Vision Classifier
            boolean experimentalTriggered = ExperimentalAiScanner.analyzeBitmap(this, bitmap);

            boolean triggered = (suggestiveScore > targetThreshold) || (explicitScore > targetThreshold) || experimentalTriggered;

            if (triggered) {
                Log.w(TAG, "🛡️ AI SCREEN SHIELD TRIGGERED: Suggestive/Explicit Media Detected!");
                showOverlayWindow();
            } else if (mOverlayShowing) {
                hideOverlayWindow();
            }

        } catch (Exception e) {
            Log.e(TAG, "Error analyzing frame", e);
        } finally {
            if (bitmap != null) bitmap.recycle();
            image.close();
        }
    }

    private void createOverlayView() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setBackgroundColor(Color.parseColor("#EE000000")); // Deep translucent dark screen

        TextView shieldTitle = new TextView(this);
        shieldTitle.setText("🛡️ AI SCREEN SHIELD ACTIVE");
        shieldTitle.setTextColor(Color.WHITE);
        shieldTitle.setTextSize(22);
        shieldTitle.setGravity(Gravity.CENTER);

        TextView shieldSub = new TextView(this);
        shieldSub.setText("Suggestive Media / Explicit Content Detected & Shielded");
        shieldSub.setTextColor(Color.parseColor("#FFCC00"));
        shieldSub.setTextSize(16);
        shieldSub.setGravity(Gravity.CENTER);
        shieldSub.setPadding(30, 20, 30, 0);

        layout.addView(shieldTitle);
        layout.addView(shieldSub);

        mOverlayView = layout;
    }

    private void showOverlayWindow() {
        if (mOverlayShowing) return;
        mOverlayShowing = true;

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                            WindowManager.LayoutParams.MATCH_PARENT,
                            WindowManager.LayoutParams.MATCH_PARENT,
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                                    : WindowManager.LayoutParams.TYPE_PHONE,
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                            PixelFormat.TRANSLUCENT
                    );
                    mWindowManager.addView(mOverlayView, params);
                    Toast.makeText(AiScreenShieldService.this, "🛡️ AI Shield: Suggestive Media Blocked", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Log.e(TAG, "Error adding overlay window", e);
                }
            }
        });
    }

    private void hideOverlayWindow() {
        if (!mOverlayShowing) return;
        mOverlayShowing = false;

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    if (mOverlayView != null && mOverlayView.getParent() != null) {
                        mWindowManager.removeView(mOverlayView);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error removing overlay window", e);
                }
            }
        });
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("🛡️ AI Screen Shield Active")
                .setContentText("Monitoring screen in real time for suggestive media")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "AI Screen Shield Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mIsScanning = false;
        hideOverlayWindow();
        if (mVirtualDisplay != null) mVirtualDisplay.release();
        if (mMediaProjection != null) mMediaProjection.stop();
        Log.i(TAG, "AiScreenShieldService Stopped");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
