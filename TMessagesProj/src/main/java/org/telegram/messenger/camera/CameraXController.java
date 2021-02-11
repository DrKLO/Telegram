package org.telegram.messenger.camera;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaMetadataRetriever;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.util.Range;
import android.view.Surface;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraInfoUnavailableException;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.MeteringPointFactory;
import androidx.camera.core.Preview;
import androidx.camera.core.VideoCapture;
import androidx.camera.core.impl.utils.CameraOrientationUtil;
import androidx.camera.core.impl.utils.Exif;
import androidx.camera.core.internal.compat.workaround.ExifRotationAvailability;
import androidx.camera.extensions.BeautyImageCaptureExtender;
import androidx.camera.extensions.BeautyPreviewExtender;
import androidx.camera.extensions.BokehImageCaptureExtender;
import androidx.camera.extensions.BokehPreviewExtender;
import androidx.camera.extensions.HdrImageCaptureExtender;
import androidx.camera.extensions.HdrPreviewExtender;
import androidx.camera.extensions.NightImageCaptureExtender;
import androidx.camera.extensions.NightPreviewExtender;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;

import com.google.common.util.concurrent.ListenableFuture;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@TargetApi(21)
public class CameraXController {
    static int VIDEO_BITRATE_1080 = 10000000;
    static int VIDEO_BITRATE_720 = 6500000;
    static int VIDEO_BITRATE_480 = 3000000;

    private boolean isFrontface;
    private boolean isInited = false;
    private final CameraLifecycle lifecycle;
    private ProcessCameraProvider provider;
    private Camera camera;
    private CameraSelector cameraSelector;
    private CameraXView.VideoSavedCallback videoSavedCallback;
    private boolean abandonCurrentVideo = false;
    private ImageCapture iCapture;
    private Preview previewUseCase;
    private VideoCapture vCapture;
    private final MeteringPointFactory meteringPointFactory;
    private final Preview.SurfaceProvider surfaceProvider;
    private boolean stableFPSPreviewOnly = false;
    private boolean noSupportedSurfaceCombinationWorkaround = false;

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

    public CameraXController(CameraLifecycle lifecycle, MeteringPointFactory factory, Preview.SurfaceProvider surfaceProvider) {
        this.lifecycle = lifecycle;
        this.meteringPointFactory = factory;
        this.surfaceProvider = surfaceProvider;
    }


    public boolean isInitied() {
        return isInited;
    }

    public boolean setFrontFace(boolean isFrontFace) {
        return this.isFrontface = isFrontFace;
    }

    public boolean isFrontface() {
        return isFrontface;
    }

    public void setStableFPSPreviewOnly(boolean isEnabled) {
        stableFPSPreviewOnly = isEnabled;
    }

    public void initCamera(Context context, boolean isInitialFrontface, Runnable onPreInit) {
        this.isFrontface = isInitialFrontface;
        ListenableFuture<ProcessCameraProvider> providerFtr = ProcessCameraProvider.getInstance(context);
        providerFtr.addListener(
                () -> {
                    try {
                        provider = providerFtr.get();
                        bindUseCases();
                        lifecycle.start();
                        onPreInit.run();
                        isInited = true;
                    } catch (ExecutionException | InterruptedException e) {
                        e.printStackTrace();
                    }
                }, ContextCompat.getMainExecutor(context)
        );
    }

    public void switchCamera() {
        isFrontface = !isFrontface;
        bindUseCases();
    }

    public void closeCamera() {
        lifecycle.stop();
    }

    @SuppressLint("RestrictedApi")
    public boolean hasFrontFaceCamera() {
        if (provider == null) {
            return false;
        }
        try {
            return provider.hasCamera(
                    new CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_FRONT).build());
        } catch (CameraInfoUnavailableException e) {
            return false;
        }
    }

    @SuppressLint("RestrictedApi")
    public static boolean hasGoodCamera(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA);
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

    @SuppressLint({"RestrictedApi", "UnsafeExperimentalUsageError"})
    public void bindUseCases() {
        if(provider == null) return;
        android.util.Size targetSize;
        if ((getDisplayOrientation() == 0 || getDisplayOrientation() == 180) && getDeviceDefaultOrientation() == Configuration.ORIENTATION_PORTRAIT) {
            targetSize = new android.util.Size(1080, 1920);
        } else {
            targetSize = new android.util.Size(1920, 1080);
        }

        Preview.Builder previewBuilder = new Preview.Builder();
        previewBuilder.setTargetResolution(targetSize);

        cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(isFrontface ? CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK)
                .build();

        int bitrate = chooseVideoBitrate();

        vCapture = new VideoCapture.Builder()
                .setAudioBitRate(441000)
                .setVideoFrameRate(30)
                .setBitRate(bitrate)
                .setTargetResolution(targetSize)
                .build();


        ImageCapture.Builder iCaptureBuilder = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setTargetAspectRatio(AspectRatio.RATIO_16_9);

        provider.unbindAll();
        previewUseCase = previewBuilder.build();
        previewUseCase.setSurfaceProvider(surfaceProvider);

        if (stableFPSPreviewOnly) {
            camera = provider.bindToLifecycle(lifecycle, cameraSelector, previewUseCase, vCapture);
        } else {
            iCapture = iCaptureBuilder.build();

            try {
                camera = provider.bindToLifecycle(lifecycle, cameraSelector, previewUseCase, vCapture, iCapture);
            } catch (java.lang.IllegalArgumentException e) {
                noSupportedSurfaceCombinationWorkaround = true;
                camera = provider.bindToLifecycle(lifecycle, cameraSelector, previewUseCase, iCapture);
            }
        }
    }


    public void setZoom(float value) {
        camera.getCameraControl().setLinearZoom(value);
    }

    @SuppressLint("UnsafeExperimentalUsageError")
    public boolean isExposureCompensationSupported() {
        return camera.getCameraInfo().getExposureState().isExposureCompensationSupported();
    }

    @SuppressLint("UnsafeExperimentalUsageError")
    public void setExposureCompensation(float value) {
        if (!camera.getCameraInfo().getExposureState().isExposureCompensationSupported()) return;
        Range<Integer> evRange = camera.getCameraInfo().getExposureState().getExposureCompensationRange();
        int index = (int) (mix(evRange.getLower().floatValue(), evRange.getUpper().floatValue(), value) + 0.5f);
        camera.getCameraControl().setExposureCompensationIndex(index);
    }

    @SuppressLint({"UnsafeExperimentalUsageError", "RestrictedApi"})
    public void setTargetOrientation(int rotation) {
        if (previewUseCase != null) {
            previewUseCase.setTargetRotation(rotation);
        }
        if (iCapture != null) {
            iCapture.setTargetRotation(rotation);
        }
        if (vCapture != null) {
            vCapture.setTargetRotation(rotation);
        }
    }

    @SuppressLint({"UnsafeExperimentalUsageError", "RestrictedApi"})
    public void setWorldCaptureOrientation(int rotation) {
        if (iCapture != null) {
            iCapture.setTargetRotation(rotation);
        }
        if (vCapture != null) {
            vCapture.setTargetRotation(rotation);
        }
    }

    @SuppressLint({"UnsafeExperimentalUsageError", "RestrictedApi"})
    public void focusToPoint(int x, int y) {
        MeteringPoint point = meteringPointFactory.createPoint(x, y);

        FocusMeteringAction action = new FocusMeteringAction
                .Builder(point, FocusMeteringAction.FLAG_AE | FocusMeteringAction.FLAG_AF | FocusMeteringAction.FLAG_AWB)
                //.disableAutoCancel()
                .build();

        camera.getCameraControl().startFocusAndMetering(action);
    }


    @SuppressLint("RestrictedApi")
    public void recordVideo(final File path, boolean mirror, CameraXView.VideoSavedCallback onStop) {
        if (noSupportedSurfaceCombinationWorkaround) {
            provider.unbindAll();
            provider.bindToLifecycle(lifecycle, cameraSelector, previewUseCase, vCapture);
        }
        videoSavedCallback = onStop;
        VideoCapture.OutputFileOptions fileOpt = new VideoCapture.OutputFileOptions
                .Builder(path)
                .build();

        if (iCapture.getFlashMode() == ImageCapture.FLASH_MODE_ON) {
            camera.getCameraControl().enableTorch(true);
        }
        vCapture.startRecording(fileOpt, AsyncTask.THREAD_POOL_EXECUTOR /*ContextCompat.getMainExecutor(context)*/, new VideoCapture.OnVideoSavedCallback() {
            @Override
            public void onVideoSaved(@NonNull VideoCapture.OutputFileResults outputFileResults) {
                if (noSupportedSurfaceCombinationWorkaround) {
                    AndroidUtilities.runOnUIThread(()->{
                        provider.unbindAll();
                        provider.bindToLifecycle(lifecycle, cameraSelector, previewUseCase, iCapture);
                    });
                }

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
                if (noSupportedSurfaceCombinationWorkaround) {
                    AndroidUtilities.runOnUIThread(()-> {
                        provider.unbindAll();
                        provider.bindToLifecycle(lifecycle, cameraSelector, previewUseCase, iCapture);
                    });
                }
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
        if (stableFPSPreviewOnly) return;
        iCapture.takePicture(AsyncTask.THREAD_POOL_EXECUTOR  /*ContextCompat.getMainExecutor(context)*/, new ImageCapture.OnImageCapturedCallback() {
            @SuppressLint("RestrictedApi")
            @Override
            public void onCaptureSuccess(@NonNull ImageProxy image) {
                int orientation = image.getImageInfo().getRotationDegrees();
                try {

                    FileOutputStream output = new FileOutputStream(file);

                    int flipState = 0;
                    if (isFrontface && (orientation == 90 || orientation == 270)) {
                        flipState = JpegImageUtils.FLIP_Y;
                    } else if (isFrontface && (orientation == 0 || orientation == 180)) {
                        flipState = JpegImageUtils.FLIP_X;
                    }

                    byte[] jpegByteArray = JpegImageUtils.imageToJpegByteArray(image, flipState);
                    output.write(jpegByteArray);
                    output.close();
                    Exif exif = Exif.createFromFile(file);
                    exif.attachTimestamp();

                    if (new ExifRotationAvailability().shouldUseExifOrientation(image)) {
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        buffer.rewind();
                        byte[] data = new byte[buffer.capacity()];
                        buffer.get(data);
                        InputStream inputStream = new ByteArrayInputStream(data);
                        Exif originalExif = Exif.createFromInputStream(inputStream);
                        exif.setOrientation(originalExif.getOrientation());
                    } else {
                        exif.rotate(orientation);
                    }
                    exif.save();
                } catch (JpegImageUtils.CodecFailedException | FileNotFoundException e) {
                    e.printStackTrace();
                    FileLog.e(e);
                } catch (IOException e) {
                    e.printStackTrace();
                    FileLog.e(e);
                }
                image.close();
                AndroidUtilities.runOnUIThread(onTake);
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                FileLog.e(exception);
            }
        });
    }

    @SuppressLint("RestrictedApi")
    public Size getPreviewSize() {
        Size size = new Size(0, 0);
        if (previewUseCase != null) {
            android.util.Size s = previewUseCase.getAttachedSurfaceResolution();
            size = new Size(s.getWidth(), s.getHeight());

        }
        return size;
    }

    public int getDisplayOrientation() {
        WindowManager mgr = (WindowManager) ApplicationLoader.applicationContext.getSystemService(Context.WINDOW_SERVICE);
        int rotation = mgr.getDefaultDisplay().getRotation();
        int degrees = 0;

        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }
        return degrees;
    }

    private int getDeviceDefaultOrientation() {
        WindowManager windowManager = (WindowManager) (ApplicationLoader.applicationContext.getSystemService(Context.WINDOW_SERVICE));
        Configuration config = ApplicationLoader.applicationContext.getResources().getConfiguration();
        int rotation = windowManager.getDefaultDisplay().getRotation();

        if (((rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) && config.orientation == Configuration.ORIENTATION_LANDSCAPE) ||
                ((rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) && config.orientation == Configuration.ORIENTATION_PORTRAIT)) {
            return Configuration.ORIENTATION_LANDSCAPE;
        } else {
            return Configuration.ORIENTATION_PORTRAIT;
        }
    }

    private int chooseVideoBitrate() {
        android.util.Size maxSize = null;
        try {
            maxSize = getDeviceMaxVideoResolution(isFrontface ? CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK);
            if (maxSize == null) return VIDEO_BITRATE_1080;
            int maxSide = Math.min(maxSize.getWidth(), maxSize.getHeight());
            if (maxSide >= 1080) return VIDEO_BITRATE_1080;
            else if (maxSide >= 720) return VIDEO_BITRATE_720;
            else return VIDEO_BITRATE_480;
        } catch (Exception e) {
            FileLog.e(e);
        }
        return VIDEO_BITRATE_1080;
    }


    private android.util.Size getDeviceMaxVideoResolution(int facing) throws CameraAccessException {
        int targetFacing;
        if (facing == CameraSelector.LENS_FACING_FRONT) {
            targetFacing = CameraCharacteristics.LENS_FACING_FRONT;
        } else {
            targetFacing = CameraCharacteristics.LENS_FACING_BACK;
        }

        CameraManager mgr = (CameraManager) ApplicationLoader.applicationContext.getSystemService(Context.CAMERA_SERVICE);
        String[] cameras = mgr.getCameraIdList();
        for (String cameraId : cameras) {
            CameraCharacteristics info = mgr.getCameraCharacteristics(cameraId);
            int cameraFacing = info.get(CameraCharacteristics.LENS_FACING);
            if (cameraFacing == targetFacing) {
                StreamConfigurationMap streamConfigurationMap = info.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                int sensorOrientation = info.get(CameraCharacteristics.SENSOR_ORIENTATION);
                android.util.Size[] sizes = streamConfigurationMap.getOutputSizes(MediaRecorder.class);
                android.util.Size maxSize = sizes[0];

                for (android.util.Size currentSize : sizes) {
                    if (sensorOrientation == 0 || sensorOrientation == 180) {
                        if (currentSize.getWidth() > maxSize.getWidth()) {
                            maxSize = currentSize;
                        }
                    } else {
                        if (currentSize.getHeight() > maxSize.getHeight()) {
                            maxSize = currentSize;
                        }
                    }
                }
                return maxSize;
            }
        }
        return null;
    }

    private float mix(Float x, Float y, Float f) {
        return x * (1 - f) + y * f;
    }
}
