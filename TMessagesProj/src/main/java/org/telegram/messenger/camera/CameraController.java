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
import android.util.Base64;

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

    private ThreadPoolExecutor threadPool;
    protected ArrayList<String> availableFlashModes = new ArrayList<>();
    private MediaRecorder recorder;
    private String recordedFile;
    private boolean mirrorRecorderVideo;
    protected volatile ArrayList<CameraInfo> cameraInfos;
    private VideoTakeCallback onVideoTakeCallback;
    private boolean cameraInitied;
    private boolean loadingCameras;

    private ArrayList<Runnable> onFinishCameraInitRunnables = new ArrayList<>();

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
                                if (size.width == 1280 && size.height != 720) {
                                    continue;
                                }
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
                                if (size.width == 1280 && size.height != 720) {
                                    continue;
                                }
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
                AndroidUtilities.runOnUIThread(() -> {
                    onFinishCameraInitRunnables.clear();
                    loadingCameras = false;
                    cameraInitied = false;
                    if (!withDelay && "APP_PAUSED".equals(e.getMessage())) {
                        AndroidUtilities.runOnUIThread(() -> initCamera(onInitRunnable, true), 1000);
                    }
                });

            }
        });
    }

    public boolean isCameraInitied() {
        return cameraInitied && cameraInfos != null && !cameraInfos.isEmpty();
    }

    public void runOnThreadPool(Runnable runnable) {
        threadPool.execute(runnable);
    }

    public void close(final CameraSession session, final CountDownLatch countDownLatch, final Runnable beforeDestroyRunnable) {
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
            return 0;
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
                return 0;
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
                return 0;
            }
            boolean littleEndian = (tag == 0x49492A00);

            int count = pack(jpeg, offset + 4, 4, littleEndian) + 2;
            if (count < 10 || count > length) {
                return 0;
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
                    return 0;
                }
                offset += 12;
                length -= 12;
            }
        }
        return 0;
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

    public boolean takePicture(final File path, final CameraSession session, final Runnable callback) {
        if (session == null) {
            return false;
        }
        final CameraInfo info = session.cameraInfo;
        final boolean flipFront = session.isFlipFront();
        Camera camera = info.camera;
        try {
            camera.takePicture(null, null, (data, camera1) -> {
                Bitmap bitmap = null;
                int size = (int) (AndroidUtilities.getPhotoSize() / AndroidUtilities.density);
                String key = String.format(Locale.US, "%s@%d_%d", Utilities.MD5(path.getAbsolutePath()), size, size);
                try {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = true;
                    BitmapFactory.decodeByteArray(data, 0, data.length, options);
                    float scaleFactor = Math.max((float) options.outWidth / AndroidUtilities.getPhotoSize(), (float) options.outHeight / AndroidUtilities.getPhotoSize());
                    if (scaleFactor < 1) {
                        scaleFactor = 1;
                    }
                    options.inJustDecodeBounds = false;
                    options.inSampleSize = (int) scaleFactor;
                    options.inPurgeable = true;
                    bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
                } catch (Throwable e) {
                    FileLog.e(e);
                }
                try {
                    if (info.frontCamera != 0 && flipFront) {
                        try {
                            Matrix matrix = new Matrix();
                            matrix.setRotate(getOrientation(data));
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
                                ImageLoader.getInstance().putImageToCache(new BitmapDrawable(scaled), key);
                            }
                            if (callback != null) {
                                callback.run();
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
                        ImageLoader.getInstance().putImageToCache(new BitmapDrawable(bitmap), key);
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
                if (callback != null) {
                    callback.run();
                }
            });
            return true;
        } catch (Exception e) {
            FileLog.e(e);
        }
        return false;
    }

    public void startPreview(final CameraSession session) {
        if (session == null) {
            return;
        }
        threadPool.execute(() -> {
            Camera camera = session.cameraInfo.camera;
            try {
                if (camera == null) {
                    camera = session.cameraInfo.camera = Camera.open(session.cameraInfo.cameraId);
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

    public void stopPreview(final CameraSession session) {
        if (session == null) {
            return;
        }
        threadPool.execute(() -> {
            Camera camera = session.cameraInfo.camera;
            try {
                if (camera == null) {
                    camera = session.cameraInfo.camera = Camera.open(session.cameraInfo.cameraId);
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

                session.configureRoundCamera();
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
                Camera.Parameters params = camera.getParameters();
                List<String> rawFlashModes = params.getSupportedFlashModes();

                availableFlashModes.clear();
                if (rawFlashModes != null) {
                    for (int a = 0; a < rawFlashModes.size(); a++) {
                        String rawFlashMode = rawFlashModes.get(a);
                        if (rawFlashMode.equals(Camera.Parameters.FLASH_MODE_OFF) || rawFlashMode.equals(Camera.Parameters.FLASH_MODE_ON) || rawFlashMode.equals(Camera.Parameters.FLASH_MODE_AUTO)) {
                            availableFlashModes.add(rawFlashMode);
                        }
                    }
                    session.checkFlashMode(availableFlashModes.get(0));
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

    public void recordVideo(final CameraSession session, final File path, boolean mirror, final VideoTakeCallback callback, final Runnable onVideoStartRecord) {
        if (session == null) {
            return;
        }

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
                    //camera.stopPreview();
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
                        pictureSize = CameraController.chooseOptimalSize(info.getPictureSizes(), 720, 480, pictureSize);
                        int bitrate;
                        if (Math.min(pictureSize.mHeight,pictureSize.mWidth) >= 720) {
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
    }

    private void finishRecordingVideo() {
        MediaMetadataRetriever mediaMetadataRetriever = null;
        long duration = 0;
        try {
            mediaMetadataRetriever = new MediaMetadataRetriever();
            mediaMetadataRetriever.setDataSource(recordedFile);
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
        Bitmap bitmap = SendMessagesHelper.createVideoThumbnail(recordedFile, MediaStore.Video.Thumbnails.MINI_KIND);
        if (mirrorRecorderVideo) {
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
            if (onVideoTakeCallback != null) {
                String path = cacheFile.getAbsolutePath();
                if (bitmapFinal != null) {
                    ImageLoader.getInstance().putImageToCache(new BitmapDrawable(bitmapFinal), Utilities.MD5(path));
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
                finishRecordingVideo();
            }
        }
    }

    public void stopVideoRecording(final CameraSession session, final boolean abandon) {
        threadPool.execute(() -> {
            try {
                CameraInfo info = session.cameraInfo;
                final Camera camera = info.camera;
                if (camera != null && recorder != null) {
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
                if (!abandon && onVideoTakeCallback != null) {
                    finishRecordingVideo();
                } else {
                    onVideoTakeCallback = null;
                }
            } catch (Exception ignore) {
            }
        });
    }

    public static Size chooseOptimalSize(List<Size> choices, int width, int height, Size aspectRatio) {
        List<Size> bigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (int a = 0; a < choices.size(); a++) {
            Size option = choices.get(a);
            if (option.getHeight() == option.getWidth() * h / w && option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }
        if (bigEnough.size() > 0) {
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
}
