/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger.camera;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.BitmapDrawable;
import android.hardware.Camera;
import android.media.MediaMetadataRetriever;
import android.media.MediaRecorder;
import android.os.Build;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.Bitmaps;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.SerializedData;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class CameraController implements MediaRecorder.OnInfoListener {

    private static final int CORE_POOL_SIZE = 1;
    private static final int MAX_POOL_SIZE = 1;
    private static final int KEEP_ALIVE_SECONDS = 60;

    protected ThreadPoolExecutor threadPool;
    private MediaRecorder recorder;
    private String recordedFile;
    private boolean mirrorRecorderVideo;
    protected volatile ArrayList<CameraInfo> cameraInfos;
    private VideoTakeCallback onVideoTakeCallback;
    private boolean cameraInitied;
    private boolean loadingCameras;

    private ArrayList<Runnable> onFinishCameraInitRunnables = new ArrayList<>();
    ICameraView recordingCurrentCameraView;

    public interface ICameraView {
        void stopRecording();
        boolean startRecording(File file, Runnable runnable);
    }

    private static volatile CameraController Instance = null;

    public interface VideoTakeCallback {
        void onFinishVideoRecording(String thumbPath, long duration);
    }

    public static CameraController getInstance() {
        CameraController localInstance = Instance;
        if (localInstance == null) {
            synchronized (CameraController.class) {
                localInstance = Instance;
                if (localInstance == null) {
                    Instance = localInstance = new CameraController();
                }
            }
        }
        return localInstance;
    }

    public CameraController() {
        threadPool = new ThreadPoolExecutor(CORE_POOL_SIZE, MAX_POOL_SIZE, KEEP_ALIVE_SECONDS, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
    }

    public void cancelOnInitRunnable(final Runnable onInitRunnable) {
        onFinishCameraInitRunnables.remove(onInitRunnable);
    }

    public void initCamera(final Runnable onInitRunnable) {
        initCamera(onInitRunnable, false);
    }

    private void initCamera(final Runnable onInitRunnable, boolean withDelay) {
        if (cameraInitied) {
            return;
        }
        if (onInitRunnable != null && !onFinishCameraInitRunnables.contains(onInitRunnable)) {
            onFinishCameraInitRunnables.add(onInitRunnable);
        }
        if (loadingCameras || cameraInitied) {
            return;
        }
        loadingCameras = true;
        threadPool.execute(() -> {
            try {
                if (cameraInfos == null) {
                    SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                    String cache = preferences.getString("cameraCache", null);
                    Comparator<Size> comparator = (o1, o2) -> {
                        if (o1.mWidth < o2.mWidth) {
                            return 1;
                        } else if (o1.mWidth > o2.mWidth) {
                            return -1;
                        } else {
                            if (o1.mHeight < o2.mHeight) {
                                return 1;
                            } else if (o1.mHeight > o2.mHeight) {
                                return -1;
                            }
                            return 0;
                        }
                    };
                    ArrayList<CameraInfo> result = new ArrayList<>();
                    if (cache != null) {
                        SerializedData serializedData = new SerializedData(Base64.decode(cache, Base64.DEFAULT));
                        int count = serializedData.readInt32(false);
                        for (int a = 0; a < count; a++) {
                            CameraInfo cameraInfo = new CameraInfo(serializedData.readInt32(false), serializedData.readInt32(false));
                            int pCount = serializedData.readInt32(false);
                            for (int b = 0; b < pCount; b++) {
                                cameraInfo.previewSizes.add(new Size(serializedData.readInt32(false), serializedData.readInt32(false)));
                            }
                            pCount = serializedData.readInt32(false);
                            for (int b = 0; b < pCount; b++) {
                                cameraInfo.pictureSizes.add(new Size(serializedData.readInt32(false), serializedData.readInt32(false)));
                            }
                            result.add(cameraInfo);

                            Collections.sort(cameraInfo.previewSizes, comparator);
                            Collections.sort(cameraInfo.pictureSizes, comparator);
                        }
                        serializedData.cleanup();
                    } else {
                        int count = Camera.getNumberOfCameras();
                        Camera.CameraInfo info = new Camera.CameraInfo();

                        int bufferSize = 4;
                        for (int cameraId = 0; cameraId < count; cameraId++) {
                            Camera.getCameraInfo(cameraId, info);
                            CameraInfo cameraInfo = new CameraInfo(cameraId, info.facing);

                            if (ApplicationLoader.mainInterfacePaused && ApplicationLoader.externalInterfacePaused) {
                                throw new RuntimeException("APP_PAUSED");
                            }
                            Camera camera = Camera.open(cameraInfo.getCameraId());
                            Camera.Parameters params = camera.getParameters();

                            List<Camera.Size> list = params.getSupportedPreviewSizes();
                            for (int a = 0; a < list.size(); a++) {
                                Camera.Size size = list.get(a);
//                                if (size.width == 1280 && size.height != 720) {
//                                    continue;
//                                }
                                if (size.height < 2160 && size.width < 2160) {
                                    cameraInfo.previewSizes.add(new Size(size.width, size.height));
                                    if (BuildVars.LOGS_ENABLED) {
                                        FileLog.d("preview size = " + size.width + " " + size.height);
                                    }
                                }
                            }

                            list = params.getSupportedPictureSizes();
                            for (int a = 0; a < list.size(); a++) {
                                Camera.Size size = list.get(a);
//                                if (size.width == 1280 && size.height != 720) {
//                                    continue;
//                                }
                                if (!"samsung".equals(Build.MANUFACTURER) || !"jflteuc".equals(Build.PRODUCT) || size.width < 2048) {
                                    cameraInfo.pictureSizes.add(new Size(size.width, size.height));
                                    if (BuildVars.LOGS_ENABLED) {
                                        FileLog.d("picture size = " + size.width + " " + size.height);
                                    }
                                }
                            }

                            camera.release();
                            result.add(cameraInfo);

                            Collections.sort(cameraInfo.previewSizes, comparator);
                            Collections.sort(cameraInfo.pictureSizes, comparator);

                            bufferSize += 4 + 4 + 8 * (cameraInfo.previewSizes.size() + cameraInfo.pictureSizes.size());
                        }

                        SerializedData serializedData = new SerializedData(bufferSize);
                        serializedData.writeInt32(result.size());
                        for (int a = 0; a < count; a++) {
                            CameraInfo cameraInfo = result.get(a);
                            serializedData.writeInt32(cameraInfo.cameraId);
                            serializedData.writeInt32(cameraInfo.frontCamera);

                            int pCount = cameraInfo.previewSizes.size();
                            serializedData.writeInt32(pCount);
                            for (int b = 0; b < pCount; b++) {
                                Size size = cameraInfo.previewSizes.get(b);
                                serializedData.writeInt32(size.mWidth);
                                serializedData.writeInt32(size.mHeight);
                            }
                            pCount = cameraInfo.pictureSizes.size();
                            serializedData.writeInt32(pCount);
                            for (int b = 0; b < pCount; b++) {
                                Size size = cameraInfo.pictureSizes.get(b);
                                serializedData.writeInt32(size.mWidth);
                                serializedData.writeInt32(size.mHeight);
                            }
                        }
                        preferences.edit().putString("cameraCache", Base64.encodeToString(serializedData.toByteArray(), Base64.DEFAULT)).commit();
                        serializedData.cleanup();
                    }
                    cameraInfos = result;
                }
                AndroidUtilities.runOnUIThread(() -> {
                    loadingCameras = false;
                    cameraInitied = true;
                    if (!onFinishCameraInitRunnables.isEmpty()) {
                        for (int a = 0; a < onFinishCameraInitRunnables.size(); a++) {
                            onFinishCameraInitRunnables.get(a).run();
                        }
                        onFinishCameraInitRunnables.clear();
                    }
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.cameraInitied);
                });
            } catch (Exception e) {
                FileLog.e(e, !"APP_PAUSED".equals(e.getMessage()));
                AndroidUtilities.runOnUIThread(() -> {
                    onFinishCameraInitRunnables.clear();
                    loadingCameras = false;
                    cameraInitied = false;
                    if (!withDelay && "APP_PAUSED".equals(e.getMessage()) && onInitRunnable != null) {
                        AndroidUtilities.runOnUIThread(() -> initCamera(onInitRunnable, true), 1000);
                    }
                });
            }
        });
    }

    public boolean isCameraInitied() {
        return cameraInitied && cameraInfos != null && !cameraInfos.isEmpty();
    }

    public void close(final CameraSession session, final CountDownLatch countDownLatch, final Runnable beforeDestroyRunnable) {
        close(session, countDownLatch, beforeDestroyRunnable, null);
    }

    public void close(final CameraSession session, final CountDownLatch countDownLatch, final Runnable beforeDestroyRunnable, final Runnable afterDestroyRunnable) {
        session.destroy();
        threadPool.execute(() -> {
            if (beforeDestroyRunnable != null) {
                beforeDestroyRunnable.run();
            }
            if (session.cameraInfo.camera != null) {
                try {
                    session.cameraInfo.camera.stopPreview();
                    session.cameraInfo.camera.setPreviewCallbackWithBuffer(null);
                } catch (Exception e) {
                    FileLog.e(e);
                }
                try {
                    session.cameraInfo.camera.release();
                } catch (Exception e) {
                    FileLog.e(e);
                }
                session.cameraInfo.camera = null;
            }
            if (countDownLatch != null) {
                countDownLatch.countDown();
            }
            if (afterDestroyRunnable != null) {
                AndroidUtilities.runOnUIThread(afterDestroyRunnable);
            }
        });
        if (countDownLatch != null) {
            try {
                countDownLatch.await();
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    public ArrayList<CameraInfo> getCameras() {
        return cameraInfos;
    }

    private static int getOrientation(byte[] jpeg) {
        if (jpeg == null) {
            return -1;
        }

        int offset = 0;
        int length = 0;

        while (offset + 3 < jpeg.length && (jpeg[offset++] & 0xFF) == 0xFF) {
            int marker = jpeg[offset] & 0xFF;

            if (marker == 0xFF) {
                continue;
            }
            offset++;

            if (marker == 0xD8 || marker == 0x01) {
                continue;
            }
            if (marker == 0xD9 || marker == 0xDA) {
                break;
            }

            length = pack(jpeg, offset, 2, false);
            if (length < 2 || offset + length > jpeg.length) {
                return -1;
            }

            // Break if the marker is EXIF in APP1.
            if (marker == 0xE1 && length >= 8 &&
                pack(jpeg, offset + 2, 4, false) == 0x45786966 &&
                pack(jpeg, offset + 6, 2, false) == 0) {
                offset += 8;
                length -= 8;
                break;
            }

            offset += length;
            length = 0;
        }

        if (length > 8) {
            int tag = pack(jpeg, offset, 4, false);
            if (tag != 0x49492A00 && tag != 0x4D4D002A) {
                return -1;
            }
            boolean littleEndian = (tag == 0x49492A00);

            int count = pack(jpeg, offset + 4, 4, littleEndian) + 2;
            if (count < 10 || count > length) {
                return -1;
            }
            offset += count;
            length -= count;

            count = pack(jpeg, offset - 2, 2, littleEndian);
            while (count-- > 0 && length >= 12) {
                tag = pack(jpeg, offset, 2, littleEndian);
                if (tag == 0x0112) {
                    int orientation = pack(jpeg, offset + 8, 2, littleEndian);
                    switch (orientation) {
                        case 1:
                            return 0;
                        case 3:
                            return 180;
                        case 6:
                            return 90;
                        case 8:
                            return 270;
                    }
                    return -1;
                }
                offset += 12;
                length -= 12;
            }
        }
        return -1;
    }

    private static int pack(byte[] bytes, int offset, int length, boolean littleEndian) {
        int step = 1;
        if (littleEndian) {
            offset += length - 1;
            step = -1;
        }

        int value = 0;
        while (length-- > 0) {
            value = (value << 8) | (bytes[offset] & 0xFF);
            offset += step;
        }
        return value;
    }

    public boolean takePicture(final File path, final boolean ignoreOrientation, final Object sessionObject, final Utilities.Callback<Integer> callback) {
        if (sessionObject == null) {
            return false;
        }
        if (sessionObject instanceof CameraSession) {
            CameraSession session = (CameraSession) sessionObject;
            final CameraInfo info = session.cameraInfo;
            final boolean flipFront = session.isFlipFront();
            Camera camera = info.camera;
            try {
                camera.takePicture(null, null, (data, camera1) -> {
                    Bitmap bitmap = null;
                    int orientation = 0;
                    int size = (int) (AndroidUtilities.getPhotoSize() / AndroidUtilities.density);
                    String key = String.format(Locale.US, "%s@%d_%d", Utilities.MD5(path.getAbsolutePath()), size, size);
                    try {
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inJustDecodeBounds = true;
                        BitmapFactory.decodeByteArray(data, 0, data.length, options);
                        //                    float scaleFactor = Math.max((float) options.outWidth / AndroidUtilities.getPhotoSize(), (float) options.outHeight / AndroidUtilities.getPhotoSize());
                        //                    if (scaleFactor < 1) {
                        //                        scaleFactor = 1;
                        //                    }
                        options.inJustDecodeBounds = false;
                        //    options.inSampleSize = (int) scaleFactor;
                        options.inPurgeable = true;
                        bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
                    } catch (Throwable e) {
                        FileLog.e(e);
                    }
                    try {
                        orientation = getOrientation(data);
                        if (info.frontCamera != 0 && flipFront) {
                            try {
                                Matrix matrix = new Matrix();
                                if (!ignoreOrientation && orientation != -1) {
                                    matrix.setRotate(orientation);
                                }
                                orientation = 0;
                                matrix.postScale(-1, 1);
                                Bitmap scaled = Bitmaps.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                                if (scaled != bitmap) {
                                    bitmap.recycle();
                                }
                                FileOutputStream outputStream = new FileOutputStream(path);
                                scaled.compress(Bitmap.CompressFormat.JPEG, 80, outputStream);
                                outputStream.flush();
                                outputStream.getFD().sync();
                                outputStream.close();
                                if (scaled != null) {
                                    ImageLoader.getInstance().putImageToCache(new BitmapDrawable(scaled), key, false);
                                }
                                if (callback != null) {
                                    callback.run(orientation);
                                }
                                return;
                            } catch (Throwable e) {
                                FileLog.e(e);
                            }
                        }
                        FileOutputStream outputStream = new FileOutputStream(path);
                        outputStream.write(data);
                        outputStream.flush();
                        outputStream.getFD().sync();
                        outputStream.close();
                        if (bitmap != null) {
                            ImageLoader.getInstance().putImageToCache(new BitmapDrawable(bitmap), key, false);
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    if (callback != null) {
                        callback.run(orientation);
                    }
                });
                return true;
            } catch (Exception e) {
                FileLog.e(e);
            }
            return false;
        } else if (sessionObject instanceof Camera2Session) {
            Camera2Session session = (Camera2Session) sessionObject;
            return session.takePicture(path, callback);
        } else {
            return false;
        }
    }

    public void startPreview(final Object sessionObject) {
        if (sessionObject == null || !(sessionObject instanceof CameraSession)) {
            return;
        }
        CameraSession session = (CameraSession) sessionObject;
        threadPool.execute(() -> {
            Camera camera = session.cameraInfo.camera;
            try {
                if (camera == null) {
                    camera = session.cameraInfo.camera = Camera.open(session.cameraInfo.cameraId);
                    camera.setErrorCallback(getErrorListener(session));
                }
                camera.startPreview();
            } catch (Exception e) {
                session.cameraInfo.camera = null;
                if (camera != null) {
                    camera.release();
                }
                FileLog.e(e);
            }
        });
    }

    public void stopPreview(final Object sessionObject) {
        if (sessionObject == null || !(sessionObject instanceof CameraSession)) {
            return;
        }
        CameraSession session = (CameraSession) sessionObject;
        threadPool.execute(() -> {
            Camera camera = session.cameraInfo.camera;
            try {
                if (camera == null) {
                    camera = session.cameraInfo.camera = Camera.open(session.cameraInfo.cameraId);
                    camera.setErrorCallback(getErrorListener(session));
                }
                camera.stopPreview();
            } catch (Exception e) {
                session.cameraInfo.camera = null;
                if (camera != null) {
                    camera.release();
                }
                FileLog.e(e);
            }
        });
    }


    public void openRound(final CameraSession session, final SurfaceTexture texture, final Runnable callback, final Runnable configureCallback) {
        if (session == null || texture == null) {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("failed to open round " + session + " tex = " + texture);
            }
            return;
        }
        threadPool.execute(() -> {
            Camera camera = session.cameraInfo.camera;
            try {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("start creating round camera session");
                }
                if (camera == null) {
                    camera = session.cameraInfo.camera = Camera.open(session.cameraInfo.cameraId);
                }
                Camera.Parameters params = camera.getParameters();

                List<String> rawFlashModes = params.getSupportedFlashModes();
                session.availableFlashModes.clear();
                if (rawFlashModes != null) {
                    for (int a = 0; a < rawFlashModes.size(); a++) {
                        String rawFlashMode = rawFlashModes.get(a);
                        if (rawFlashMode.equals(Camera.Parameters.FLASH_MODE_OFF) || rawFlashMode.equals(Camera.Parameters.FLASH_MODE_ON) || rawFlashMode.equals(Camera.Parameters.FLASH_MODE_AUTO)) {
                            session.availableFlashModes.add(rawFlashMode);
                        }
                    }
                    if (!TextUtils.equals(session.getCurrentFlashMode(), params.getFlashMode()) || !session.availableFlashModes.contains(session.getCurrentFlashMode())) {
                        session.checkFlashMode(session.availableFlashModes.get(0));
                    } else {
                        session.checkFlashMode(session.getCurrentFlashMode());
                    }
                }

                session.configureRoundCamera(true);
                if (configureCallback != null) {
                    configureCallback.run();
                }
                camera.setPreviewTexture(texture);
                camera.startPreview();
                if (callback != null) {
                    AndroidUtilities.runOnUIThread(callback);
                }
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("round camera session created");
                }
            } catch (Exception e) {
                session.cameraInfo.camera = null;
                if (camera != null) {
                    camera.release();
                }
                FileLog.e(e);
            }
        });
    }

    public void open(final CameraSession session, final SurfaceTexture texture, final Runnable callback, final Runnable prestartCallback) {
        if (session == null || texture == null) {
            return;
        }
        threadPool.execute(() -> {
            Camera camera = session.cameraInfo.camera;
            try {
                if (camera == null) {
                    camera = session.cameraInfo.camera = Camera.open(session.cameraInfo.cameraId);
                }
                camera.setErrorCallback(getErrorListener(session));
                Camera.Parameters params = camera.getParameters();

                List<String> rawFlashModes = params.getSupportedFlashModes();
                session.availableFlashModes.clear();
                if (rawFlashModes != null) {
                    for (int a = 0; a < rawFlashModes.size(); a++) {
                        String rawFlashMode = rawFlashModes.get(a);
                        if (rawFlashMode.equals(Camera.Parameters.FLASH_MODE_OFF) || rawFlashMode.equals(Camera.Parameters.FLASH_MODE_ON) || rawFlashMode.equals(Camera.Parameters.FLASH_MODE_AUTO)) {
                            session.availableFlashModes.add(rawFlashMode);
                        }
                    }
                    if (!TextUtils.equals(session.getCurrentFlashMode(), params.getFlashMode()) || !session.availableFlashModes.contains(session.getCurrentFlashMode())) {
                        session.checkFlashMode(session.availableFlashModes.get(0));
                    } else {
                        session.checkFlashMode(session.getCurrentFlashMode());
                    }
                }

                if (prestartCallback != null) {
                    prestartCallback.run();
                }

                session.configurePhotoCamera();
                camera.setPreviewTexture(texture);
                camera.startPreview();
                if (callback != null) {
                    AndroidUtilities.runOnUIThread(callback);
                }
            } catch (Exception e) {
                session.cameraInfo.camera = null;
                if (camera != null) {
                    camera.release();
                }
                FileLog.e(e);
            }
        });
    }

    public void recordVideo(final Object session, final File path, boolean mirror, final VideoTakeCallback callback, final Runnable onVideoStartRecord, ICameraView cameraView) {
        recordVideo(session, path, mirror, callback, onVideoStartRecord, cameraView, true);
    }

    public void recordVideo(final Object sessionObject, final File path, boolean mirror, final VideoTakeCallback callback, final Runnable onVideoStartRecord, ICameraView cameraView, boolean createThumbnail) {
        if (sessionObject == null) {
            return;
        }
        if (cameraView != null) {
            recordingCurrentCameraView = cameraView;
            onVideoTakeCallback = callback;
            recordedFile = path.getAbsolutePath();
            threadPool.execute(() -> {
                try {
                    if (sessionObject instanceof CameraSession) {
                        CameraSession session = (CameraSession) sessionObject;
                        final CameraInfo info = session.cameraInfo;
                        final Camera camera = info.camera;
                        if (camera != null) {
                            try {
                                Camera.Parameters params = camera.getParameters();
                                params.setFlashMode(session.getCurrentFlashMode().equals(Camera.Parameters.FLASH_MODE_ON) ? Camera.Parameters.FLASH_MODE_TORCH : Camera.Parameters.FLASH_MODE_OFF);
                                camera.setParameters(params);
                                session.onStartRecord();
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        }
                    } else if (sessionObject instanceof Camera2Session) {
                        Camera2Session session = (Camera2Session) sessionObject;
                        session.setRecordingVideo(true);
                    }
                    AndroidUtilities.runOnUIThread(() -> {
                        cameraView.startRecording(path, () -> finishRecordingVideo(createThumbnail));
                        if (onVideoStartRecord != null) {
                            onVideoStartRecord.run();
                        }
                    });
                } catch (Exception e) {
                    FileLog.e(e);
                }
            });

            return;
        }

        if (sessionObject instanceof CameraSession) {
            CameraSession session = (CameraSession) sessionObject;
            final CameraInfo info = session.cameraInfo;
            final Camera camera = info.camera;
            threadPool.execute(() -> {
                try {
                    if (camera != null) {
                        try {
                            Camera.Parameters params = camera.getParameters();
                            params.setFlashMode(session.getCurrentFlashMode().equals(Camera.Parameters.FLASH_MODE_ON) ? Camera.Parameters.FLASH_MODE_TORCH : Camera.Parameters.FLASH_MODE_OFF);
                            camera.setParameters(params);
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                        camera.unlock();
//                    camera.stopPreview();
                        try {
                            mirrorRecorderVideo = mirror;
                            recorder = new MediaRecorder();
                            recorder.setCamera(camera);
                            recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
                            recorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
                            session.configureRecorder(1, recorder);
                            recorder.setOutputFile(path.getAbsolutePath());
                            recorder.setMaxFileSize(1024 * 1024 * 1024);
                            recorder.setVideoFrameRate(30);
                            recorder.setMaxDuration(0);
                            Size pictureSize;
                            pictureSize = new Size(16, 9);
                            pictureSize = CameraController.chooseOptimalSize(info.getPictureSizes(), 720, 480, pictureSize, false);
                            int bitrate;
                            if (Math.min(pictureSize.mHeight, pictureSize.mWidth) >= 720) {
                                bitrate = 3500000;
                            } else {
                                bitrate = 1800000;
                            }
                            recorder.setVideoEncodingBitRate(bitrate);
                            recorder.setVideoSize(pictureSize.getWidth(), pictureSize.getHeight());
                            recorder.setOnInfoListener(CameraController.this);
                            recorder.prepare();
                            recorder.start();

                            onVideoTakeCallback = callback;
                            recordedFile = path.getAbsolutePath();
                            if (onVideoStartRecord != null) {
                                AndroidUtilities.runOnUIThread(onVideoStartRecord);
                            }
                        } catch (Exception e) {
                            recorder.release();
                            recorder = null;
                            FileLog.e(e);
                        }
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            });
        } else {
            return;
        }
    }

    private void finishRecordingVideo(boolean createThumbnail) {
        MediaMetadataRetriever mediaMetadataRetriever = null;
        long duration = 0;
        try {
            mediaMetadataRetriever = new MediaMetadataRetriever();
            mediaMetadataRetriever.setDataSource(recordedFile);
            String d = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (d != null) {
                duration = Long.parseLong(d);
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
        final File cacheFile;
        Bitmap bitmap = null;
        if (createThumbnail) {
            bitmap = SendMessagesHelper.createVideoThumbnail(recordedFile, MediaStore.Video.Thumbnails.MINI_KIND);
            if (mirrorRecorderVideo) {
                Bitmap b = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(b);
                canvas.scale(-1, 1, b.getWidth() / 2, b.getHeight() / 2);
                canvas.drawBitmap(bitmap, 0, 0, null);
                bitmap.recycle();
                bitmap = b;
            }
            String fileName = Integer.MIN_VALUE + "_" + SharedConfig.getLastLocalId() + ".jpg";
            cacheFile = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), fileName);
            FileOutputStream stream = null;
            try {
                stream = new FileOutputStream(cacheFile);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 87, stream);
            } catch (Throwable e) {
                FileLog.e(e);
            } finally {
                if (stream != null) {
                    try {
                        stream.close();
                    } catch (Throwable ignore) {}
                }
            }
        } else {
            cacheFile = null;
        }
        SharedConfig.saveConfig();
        final long durationFinal = duration;
        final Bitmap bitmapFinal = bitmap;
        AndroidUtilities.runOnUIThread(() -> {
            if (onVideoTakeCallback != null) {
                String path = null;
                if (cacheFile != null) {
                    path = cacheFile.getAbsolutePath();
                    if (bitmapFinal != null) {
                        ImageLoader.getInstance().putImageToCache(new BitmapDrawable(bitmapFinal), Utilities.MD5(path), false);
                    }
                }
                onVideoTakeCallback.onFinishVideoRecording(path, durationFinal);
                onVideoTakeCallback = null;
            }
        });
    }

    @Override
    public void onInfo(MediaRecorder mediaRecorder, int what, int extra) {
        if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED || what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED || what == MediaRecorder.MEDIA_RECORDER_INFO_UNKNOWN) {
            MediaRecorder tempRecorder = recorder;
            recorder = null;
            if (tempRecorder != null) {
                tempRecorder.stop();
                tempRecorder.release();
            }
            if (onVideoTakeCallback != null) {
                finishRecordingVideo(true);
            }
        }
    }

    public void stopVideoRecording(final Object sessionObject, final boolean abandon) {
        stopVideoRecording(sessionObject, abandon, true);
    }

    public void stopVideoRecording(final Object sessionObject, final boolean abandon, final boolean createThumbnail) {
        if (recordingCurrentCameraView != null) {
            recordingCurrentCameraView.stopRecording();
            recordingCurrentCameraView = null;
            return;
        }
        threadPool.execute(() -> {
            try {
                if (recorder != null) {
                    MediaRecorder tempRecorder = recorder;
                    recorder = null;
                    try {
                        tempRecorder.stop();
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    try {
                        tempRecorder.release();
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
                if (sessionObject instanceof CameraSession) {
                    CameraSession session = (CameraSession) sessionObject;
                    CameraInfo info = session.cameraInfo;
                    final Camera camera = info.camera;
                    if (camera != null) {
                        try {
                            camera.reconnect();
                            camera.startPreview();
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                        try {
                            session.stopVideoRecording();
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }
                    try {
                        Camera.Parameters params = camera.getParameters();
                        params.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                        camera.setParameters(params);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    threadPool.execute(() -> {
                        try {
                            Camera.Parameters params = camera.getParameters();
                            params.setFlashMode(session.getCurrentFlashMode());
                            camera.setParameters(params);
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    });
                } else if (sessionObject instanceof Camera2Session) {
                    Camera2Session session = (Camera2Session) sessionObject;
                    session.setRecordingVideo(false);
                }
                if (!abandon && onVideoTakeCallback != null) {
                    finishRecordingVideo(createThumbnail);
                } else {
                    onVideoTakeCallback = null;
                }
            } catch (Exception ignore) {
            }
        });
    }

    public static Size chooseOptimalSize(List<Size> choices, int width, int height, Size aspectRatio, boolean notBigger) {
        List<Size> bigEnoughWithAspectRatio = new ArrayList<>(choices.size());
        List<Size> bigEnough = new ArrayList<>(choices.size());
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (int a = 0; a < choices.size(); a++) {
            Size option = choices.get(a);
            if (notBigger && (option.getHeight() > height || option.getWidth() > width)) {
                continue;
            }
            if (option.getHeight() == option.getWidth() * h / w && option.getWidth() >= width && option.getHeight() >= height) {
                bigEnoughWithAspectRatio.add(option);
            } else if (option.getHeight() * option.getWidth() <= width * height * 4) {
                bigEnough.add(option);
            }
        }
        if (bigEnoughWithAspectRatio.size() > 0) {
            return Collections.min(bigEnoughWithAspectRatio, new CompareSizesByArea());
        } else if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            return Collections.max(choices, new CompareSizesByArea());
        }
    }

    static class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
        }
    }

    public interface ErrorCallback {
        public default void onError(int errorId, Camera camera, CameraSessionWrapper cameraSession) {

        }
    }

    private ArrayList<ErrorCallback> errorCallbacks;
    public void addOnErrorListener(ErrorCallback callback) {
        if (errorCallbacks == null) {
            errorCallbacks = new ArrayList<>();
        }
        errorCallbacks.remove(callback);
        errorCallbacks.add(callback);
    }

    public void removeOnErrorListener(ErrorCallback callback) {
        if (errorCallbacks != null) {
            errorCallbacks.remove(callback);
        }
    }

    public Camera.ErrorCallback getErrorListener(CameraSession session) {
        return (errorId, camera) -> {
            if (errorCallbacks != null) {
                for (int i = 0; i < errorCallbacks.size(); ++i) {
                    ErrorCallback callback = errorCallbacks.get(i);
                    if (callback != null) {
                        callback.onError(errorId, camera, CameraSessionWrapper.of(session));
                    }
                }
            }
        };
    }
}
