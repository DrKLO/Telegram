package org.telegram.messenger.camera;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.media.MediaMetadataRetriever;
import android.provider.MediaStore;
import android.view.TextureView;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.CameraX;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.MeteringPointFactory;
import androidx.camera.core.Preview;
import androidx.camera.core.VideoCapture;
import androidx.camera.extensions.BeautyImageCaptureExtender;
import androidx.camera.extensions.BeautyPreviewExtender;
import androidx.camera.extensions.BokehImageCaptureExtender;
import androidx.camera.extensions.BokehPreviewExtender;
import androidx.camera.extensions.HdrImageCaptureExtender;
import androidx.camera.extensions.HdrPreviewExtender;
import androidx.camera.extensions.NightImageCaptureExtender;
import androidx.camera.extensions.NightPreviewExtender;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;

import com.google.common.util.concurrent.ListenableFuture;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.ExecutionException;

@TargetApi(21)
public class CameraXView extends CameraView {
    private boolean initialFrontface;
    private boolean isFrontface;
    private boolean isInited = false;
    private PreviewView previewView;
    private int focusAreaSize;
    private Paint outerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint innerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Size previewSize;
    private CameraLifecycle lifecycle;
    ProcessCameraProvider provider;
    Camera camera;
    CameraSelector cameraSelector;
    private Camera1View.CameraViewDelegate delegate;
    private int clipTop;
    private int clipBottom;
    private float focusProgress = 1.0f;
    private float innerAlpha;
    private float outerAlpha;
    private DecelerateInterpolator interpolator = new DecelerateInterpolator();
    private long lastDrawTime;
    private int cx;
    private int cy;
    private VideoCapture vCapture;
    private VideoSavedCallback videoSavedCallback;
    private boolean abandonCurrentVideo = false;
    private ImageCapture iCapture;
    private BokehImageCaptureExtender iBokehExt;
    private BeautyImageCaptureExtender iBeautyExt;
    private HdrImageCaptureExtender iHdrExt;
    private NightImageCaptureExtender iNightExt;
    private BokehPreviewExtender pBokehExt;
    private BeautyPreviewExtender pBeautyExt;
    private HdrPreviewExtender pHdrExt;
    private NightPreviewExtender pNightExt;


    public interface VideoSavedCallback {
        void onFinishVideoRecording(String thumbPath, long duration);
    }

    public CameraXView(Context context, boolean frontface) {
        super(context, null);
        initialFrontface = isFrontface = frontface;
        previewView = new PreviewView(context);
        previewView.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        addView(previewView);
        focusAreaSize = AndroidUtilities.dp(96);
        outerPaint.setColor(0xffffffff);
        outerPaint.setStyle(Paint.Style.STROKE);
        outerPaint.setStrokeWidth(AndroidUtilities.dp(2));
        innerPaint.setColor(0x7fffffff);
        lifecycle = new CameraLifecycle();
    }

    @Override
    public boolean isInitied() {
        return isInited;
    }

    @Override
    public boolean isFrontface() {
        return isFrontface;
    }

    @Override
    public void setClipTop(int cameraViewOffsetY) {
        clipTop = cameraViewOffsetY;
    }

    @Override
    public void setClipBottom(int cameraViewOffsetBottomY) {
        clipBottom = cameraViewOffsetBottomY;
    }


    @Override
    public void initCamera() {
        ListenableFuture<ProcessCameraProvider> providerFtr = ProcessCameraProvider.getInstance(getContext());
        providerFtr.addListener(
                () -> {
                    try {
                        provider = providerFtr.get();
                        bindUseCases();
                        lifecycle.start();
                        observeStream();
                    } catch (ExecutionException | InterruptedException e) {
                        e.printStackTrace();
                    }
                }, ContextCompat.getMainExecutor(getContext())
        );
    }

    private void observeStream() {
        previewView.getPreviewStreamState().observe(lifecycle, streamState -> {
            if (streamState == PreviewView.StreamState.STREAMING /*&& !isInited*/) {
                delegate.onCameraInit();
                isInited = true;
            }
        });
    }

    @Override
    public void switchCamera() {
        isFrontface = !isFrontface;
        bindUseCases();
    }

    @SuppressLint("RestrictedApi")
    @Override
    public boolean hasFrontFaceCamera() {
        Camera frontCam = CameraX.getCameraWithCameraSelector(new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                .build());
        return frontCam != null;
    }

    @SuppressLint("RestrictedApi")
    public static boolean hasGoodCamera(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA);
    }
    @Override
    public TextureView getTextureView(){
        return (TextureView)(previewView.getChildAt(0));
    }


    private String getNextFlashMode(String legacyMode) {
        String next = null;
        switch (legacyMode) {
            case android.hardware.Camera.Parameters.FLASH_MODE_AUTO:
                next = android.hardware.Camera.Parameters.FLASH_MODE_ON;
                break;
            case android.hardware.Camera.Parameters.FLASH_MODE_ON:
                next = android.hardware.Camera.Parameters.FLASH_MODE_OFF;
                break;
            case android.hardware.Camera.Parameters.FLASH_MODE_OFF:
                next = android.hardware.Camera.Parameters.FLASH_MODE_AUTO;
                break;
        }
        return next;
    }

    public String setNextFlashMode() {
        String next = getNextFlashMode(getCurrentFlashMode());
        int iCaptureFlashMode = ImageCapture.FLASH_MODE_AUTO;
        switch (next) {
            case android.hardware.Camera.Parameters.FLASH_MODE_AUTO:
                iCaptureFlashMode = ImageCapture.FLASH_MODE_AUTO;
                break;
            case android.hardware.Camera.Parameters.FLASH_MODE_OFF:
                iCaptureFlashMode = ImageCapture.FLASH_MODE_OFF;
                break;
            case android.hardware.Camera.Parameters.FLASH_MODE_ON:
                iCaptureFlashMode = ImageCapture.FLASH_MODE_ON;
                break;
        }
        iCapture.setFlashMode(iCaptureFlashMode);
        return next;
    }

    public String getCurrentFlashMode() {
        int mode = iCapture.getFlashMode();
        String legacyMode = null;
        switch (mode) {
            case ImageCapture.FLASH_MODE_AUTO:
                legacyMode = android.hardware.Camera.Parameters.FLASH_MODE_AUTO;
                break;
            case ImageCapture.FLASH_MODE_OFF:
                legacyMode = android.hardware.Camera.Parameters.FLASH_MODE_OFF;
                break;
            case ImageCapture.FLASH_MODE_ON:
                legacyMode = android.hardware.Camera.Parameters.FLASH_MODE_ON;
                break;
        }
        return legacyMode;
    }

    public boolean isFlashAvailable() {
        return camera.getCameraInfo().hasFlashUnit();
    }

    @Override
    public void setUseMaxPreview(boolean value) {

    }

    @Override
    public void setMirror(boolean value) {

    }

    @Override
    public void setOptimizeForBarcode(boolean value) {

    }

    @SuppressLint({"RestrictedApi", "UnsafeExperimentalUsageError"})
    private void bindUseCases() {
        Preview.Builder previewBuilder = new Preview.Builder();


        cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(isFrontface ? CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK)
                .build();

        vCapture = new VideoCapture.Builder()
                .setAudioBitRate(441000)
                .setVideoFrameRate(30)
                .setBitRate(3500000)
                .setMaxResolution(new android.util.Size(1024, 1024))
                .setDefaultResolution(new android.util.Size(1024, 720))
                .build();


        ImageCapture.Builder iCaptureBuilder = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setMaxResolution(new android.util.Size(1024, 1024))
                .setDefaultResolution(new android.util.Size(1024, 720));

        iBokehExt = BokehImageCaptureExtender.create(iCaptureBuilder);
        iBeautyExt = BeautyImageCaptureExtender.create(iCaptureBuilder);
        iHdrExt = HdrImageCaptureExtender.create(iCaptureBuilder);
        iNightExt = NightImageCaptureExtender.create(iCaptureBuilder);
        pBokehExt = BokehPreviewExtender.create(previewBuilder);
        pBeautyExt = BeautyPreviewExtender.create(previewBuilder);
        pHdrExt = HdrPreviewExtender.create(previewBuilder);
        pNightExt = NightPreviewExtender.create(previewBuilder);

        if(iBokehExt.isExtensionAvailable(cameraSelector)){
            iBokehExt.enableExtension(cameraSelector);
            if(pBokehExt.isExtensionAvailable(cameraSelector)){
                pBokehExt.enableExtension(cameraSelector);
            }
        }

        if(iBeautyExt.isExtensionAvailable(cameraSelector)){
            iBeautyExt.enableExtension(cameraSelector);
            if(pBeautyExt.isExtensionAvailable(cameraSelector)){
                pBeautyExt.enableExtension(cameraSelector);
            }
        }

        if(iHdrExt.isExtensionAvailable(cameraSelector)){
            iHdrExt.enableExtension(cameraSelector);
            if(pHdrExt.isExtensionAvailable(cameraSelector)){
                pHdrExt.enableExtension(cameraSelector);
            }
        }

        if(iNightExt.isExtensionAvailable(cameraSelector)){
            iNightExt.enableExtension(cameraSelector);
            if(pNightExt.isExtensionAvailable(cameraSelector)){
                pNightExt.enableExtension(cameraSelector);
            }
        }

        iCapture = iCaptureBuilder.build();

        provider.unbindAll();
        Preview preview = previewBuilder.build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());
        camera = provider.bindToLifecycle(lifecycle, cameraSelector, preview, vCapture, iCapture);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        lifecycle.stop();
    }

    public void setDelegate(Camera1View.CameraViewDelegate cameraViewDelegate) {
        delegate = cameraViewDelegate;
    }

    @Override
    public void setZoom(float value) {
        camera.getCameraControl().setLinearZoom(value);
    }

    @Override
    public void focusToPoint(int x, int y) {
        MeteringPointFactory factory = previewView.getMeteringPointFactory();
        MeteringPoint point = factory.createPoint(x, y);
        FocusMeteringAction action = new FocusMeteringAction.Builder(point).build();
        camera.getCameraControl().startFocusAndMetering(action);

        focusProgress = 0.0f;
        innerAlpha = 1.0f;
        outerAlpha = 1.0f;
        cx = x;
        cy = y;
        lastDrawTime = System.currentTimeMillis();
        invalidate();
    }

    @Override
    public Size getPreviewSize() {
        return null;
    }

    public static class CameraLifecycle implements LifecycleOwner {
        private final LifecycleRegistry lifecycleRegistry;

        public CameraLifecycle() {
            lifecycleRegistry = new LifecycleRegistry(this);
            lifecycleRegistry.setCurrentState(Lifecycle.State.CREATED);
        }

        public void start() {
            lifecycleRegistry.setCurrentState(Lifecycle.State.RESUMED);
        }

        public void stop() {
            lifecycleRegistry.setCurrentState(Lifecycle.State.DESTROYED);
        }

        public Lifecycle getLifecycle() {
            return lifecycleRegistry;
        }
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        boolean result = super.drawChild(canvas, child, drawingTime);
        if (focusProgress != 1.0f || innerAlpha != 0.0f || outerAlpha != 0.0f) {
            int baseRad = AndroidUtilities.dp(30);
            long newTime = System.currentTimeMillis();
            long dt = newTime - lastDrawTime;
            if (dt < 0 || dt > 17) {
                dt = 17;
            }
            lastDrawTime = newTime;
            outerPaint.setAlpha((int) (interpolator.getInterpolation(outerAlpha) * 255));
            innerPaint.setAlpha((int) (interpolator.getInterpolation(innerAlpha) * 127));
            float interpolated = interpolator.getInterpolation(focusProgress);
            canvas.drawCircle(cx, cy, baseRad + baseRad * (1.0f - interpolated), outerPaint);
            canvas.drawCircle(cx, cy, baseRad * interpolated, innerPaint);

            if (focusProgress < 1) {
                focusProgress += dt / 200.0f;
                if (focusProgress > 1) {
                    focusProgress = 1;
                }
                invalidate();
            } else if (innerAlpha != 0) {
                innerAlpha -= dt / 150.0f;
                if (innerAlpha < 0) {
                    innerAlpha = 0;
                }
                invalidate();
            } else if (outerAlpha != 0) {
                outerAlpha -= dt / 150.0f;
                if (outerAlpha < 0) {
                    outerAlpha = 0;
                }
                invalidate();
            }
        }
        return result;
    }

    @SuppressLint("RestrictedApi")
    public void recordVideo(final File path, boolean mirror, VideoSavedCallback onStop) {
        videoSavedCallback = onStop;
        VideoCapture.OutputFileOptions fileOpt = new VideoCapture.OutputFileOptions
                .Builder(path)
                .build();

        if (iCapture.getFlashMode() == ImageCapture.FLASH_MODE_ON) {
            camera.getCameraControl().enableTorch(true);
        }
        vCapture.startRecording(fileOpt, ContextCompat.getMainExecutor(getContext()), new VideoCapture.OnVideoSavedCallback() {
            @Override
            public void onVideoSaved(@NonNull VideoCapture.OutputFileResults outputFileResults) {
                if (abandonCurrentVideo) {
                    abandonCurrentVideo = false;
                } else {
                    finishRecordingVideo(path, mirror);
                    if (iCapture.getFlashMode() == ImageCapture.FLASH_MODE_ON) {
                        camera.getCameraControl().enableTorch(false);
                    }
                }
            }

            @Override
            public void onError(int videoCaptureError, @NonNull String message, @Nullable Throwable cause) {
                FileLog.e(cause);
            }
        });
    }

    private void finishRecordingVideo(final File path, boolean mirror) {
        MediaMetadataRetriever mediaMetadataRetriever = null;
        long duration = 0;
        try {
            mediaMetadataRetriever = new MediaMetadataRetriever();
            mediaMetadataRetriever.setDataSource(path.getAbsolutePath());
            String d = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (d != null) {
                duration = (int) Math.ceil(Long.parseLong(d) / 1000.0f);
            }
        } catch (Exception e) {
            FileLog.e(e);
        } finally {
            try {
                if (mediaMetadataRetriever != null) {
                    mediaMetadataRetriever.release();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        Bitmap bitmap = SendMessagesHelper.createVideoThumbnail(path.getAbsolutePath(), MediaStore.Video.Thumbnails.MINI_KIND);
        if (mirror) {
            Bitmap b = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(b);
            canvas.scale(-1, 1, b.getWidth() / 2, b.getHeight() / 2);
            canvas.drawBitmap(bitmap, 0, 0, null);
            bitmap.recycle();
            bitmap = b;
        }
        String fileName = Integer.MIN_VALUE + "_" + SharedConfig.getLastLocalId() + ".jpg";
        final File cacheFile = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), fileName);
        try {
            FileOutputStream stream = new FileOutputStream(cacheFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 87, stream);
        } catch (Throwable e) {
            FileLog.e(e);
        }
        SharedConfig.saveConfig();
        final long durationFinal = duration;
        final Bitmap bitmapFinal = bitmap;
        AndroidUtilities.runOnUIThread(() -> {
            if (videoSavedCallback != null) {
                String cachePath = cacheFile.getAbsolutePath();
                if (bitmapFinal != null) {
                    ImageLoader.getInstance().putImageToCache(new BitmapDrawable(bitmapFinal), Utilities.MD5(cachePath));
                }
                videoSavedCallback.onFinishVideoRecording(cachePath, durationFinal);
                videoSavedCallback = null;
            }
        });
    }


    @SuppressLint("RestrictedApi")
    public void stopVideoRecording(final boolean abandon) {
        abandonCurrentVideo = abandon;
        vCapture.stopRecording();
    }


    public void takePicture(final File file, Runnable onTake) {
        ImageCapture.OutputFileOptions fileOpt = new ImageCapture.OutputFileOptions
                .Builder(file)
                .build();

        iCapture.takePicture(fileOpt, ContextCompat.getMainExecutor(getContext()), new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                onTake.run();
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                FileLog.e(exception);
            }
        });
    }

    private float mix(Float v1, Float v2, Float a) {
        return v1 * (1 - a) + v2 * a;
    }
}
