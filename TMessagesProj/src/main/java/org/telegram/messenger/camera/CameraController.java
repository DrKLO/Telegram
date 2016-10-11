/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.messenger.camera;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.BitmapDrawable;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.media.ThumbnailUtils;
import android.os.Build;
import android.provider.MediaStore;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Bitmaps;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.Utilities;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class CameraController implements MediaRecorder.OnInfoListener {

    private static final int CORE_POOL_SIZE = 1;
    private static final int MAX_POOL_SIZE = Runtime.getRuntime().availableProcessors();
    private static final int KEEP_ALIVE_SECONDS = 60;

    private ThreadPoolExecutor threadPool;
    protected ArrayList<String> availableFlashModes = new ArrayList<>();
    private MediaRecorder recorder;
    private String recordedFile;
    protected ArrayList<CameraInfo> cameraInfos = null;
    private VideoTakeCallback onVideoTakeCallback;
    private boolean cameraInitied;

    private static volatile CameraController Instance = null;

    public interface VideoTakeCallback {
        void onFinishVideoRecording(Bitmap thumb);
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
        threadPool = new ThreadPoolExecutor(CORE_POOL_SIZE, MAX_POOL_SIZE, KEEP_ALIVE_SECONDS, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
    }

    public void initCamera() {
        if (cameraInitied) {
            return;
        }
        threadPool.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    if (cameraInfos == null) {
                        int count = Camera.getNumberOfCameras();
                        ArrayList<CameraInfo> result = new ArrayList<>();
                        Camera.CameraInfo info = new Camera.CameraInfo();

                        for (int cameraId = 0; cameraId < count; cameraId++) {
                            Camera.getCameraInfo(cameraId, info);
                            CameraInfo cameraInfo = new CameraInfo(cameraId, info);

                            Camera camera = Camera.open(cameraInfo.getCameraId());
                            Camera.Parameters params = camera.getParameters();

                            List<Camera.Size> list = params.getSupportedPreviewSizes();
                            for (int a = 0; a < list.size(); a++) {
                                Camera.Size size = list.get(a);
                                if (size.height < 2160 && size.width < 2160) {
                                    cameraInfo.previewSizes.add(new Size(size.width, size.height));
                                }
                            }

                            list = params.getSupportedPictureSizes();
                            for (int a = 0; a < list.size(); a++) {
                                Camera.Size size = list.get(a);
                                if (!"samsung".equals(Build.MANUFACTURER) || !"jflteuc".equals(Build.PRODUCT) || size.width < 2048) {
                                    cameraInfo.pictureSizes.add(new Size(size.width, size.height));
                                }
                            }

                            camera.release();
                            result.add(cameraInfo);
                        }
                        cameraInfos = result;
                    }
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            cameraInitied = true;
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.cameraInitied);
                        }
                    });
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }
        });
    }

    public boolean isCameraInitied() {
        return cameraInitied && cameraInfos != null && !cameraInfos.isEmpty();
    }

    public void cleanup() {
        threadPool.execute(new Runnable() {
            @Override
            public void run() {
                if (cameraInfos == null || cameraInfos.isEmpty()) {
                    return;
                }
                for (int a = 0; a < cameraInfos.size(); a++) {
                    CameraInfo info = cameraInfos.get(a);
                    if (info.camera != null) {
                        info.camera.stopPreview();
                        info.camera.release();
                        info.camera = null;
                    }
                }
                cameraInfos = null;
            }
        });
    }

    public void close(final CameraSession session, final Semaphore semaphore) {
        session.destroy();
        final Camera camera = session.cameraInfo.camera;
        session.cameraInfo.camera = null;
        threadPool.execute(new Runnable() {
                               @Override
                               public void run() {
                                   try {
                                       if (camera != null) {
                                           camera.stopPreview();
                                           camera.release();
                                       }
                                   } catch (Exception e) {
                                       FileLog.e("tmessages", e);
                                   }
                                   if (semaphore != null) {
                                       semaphore.release();
                                   }

                               }
                           });
        if (semaphore != null) {
            try {
                semaphore.acquire();
            } catch (Exception e) {
                FileLog.e("tmessages", e);
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
        Camera camera = info.camera;
        try {
            camera.takePicture(null, null, new Camera.PictureCallback() {
                @Override
                public void onPictureTaken(byte[] data, Camera camera) {
                    Bitmap bitmap = null;
                    int size = (int) (AndroidUtilities.getPhotoSize() / AndroidUtilities.density);
                    String key = String.format(Locale.US, "%s@%d_%d", Utilities.MD5(path.getAbsolutePath()), size, size);
                    try {
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inPurgeable = true;
                        bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
                    } catch (Throwable e) {
                        FileLog.e("tmessages", e);
                    }
                    try {
                        if (info.frontCamera != 0) {
                            try {
                                Matrix matrix = new Matrix();
                                matrix.setRotate(getOrientation(data));
                                matrix.postScale(-1, 1);
                                Bitmap scaled = Bitmaps.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, false);
                                bitmap.recycle();
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
                                FileLog.e("tmessages", e);
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
                        FileLog.e("tmessages", e);
                    }
                    if (callback != null) {
                        callback.run();
                    }
                }
            });
            return true;
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        return false;
    }

    public void startPreview(final CameraSession session) {
        if (session == null) {
            return;
        }
        threadPool.execute(new Runnable() {
            @SuppressLint("NewApi")
            @Override
            public void run() {
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
                    FileLog.e("tmessages", e);
                }
            }
        });
    }

    public void open(final CameraSession session, final SurfaceTexture texture, final Runnable callback) {
        if (session == null || texture == null) {
            return;
        }
        threadPool.execute(new Runnable() {
            @SuppressLint("NewApi")
            @Override
            public void run() {
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
                    FileLog.e("tmessages", e);
                }
            }
        });
    }

    public void recordVideo(CameraSession session, final File path, final VideoTakeCallback callback) {
        if (session == null) {
            return;
        }
        try {
            CameraInfo info = session.cameraInfo;
            Camera camera = info.camera;
            if (camera != null) {
                camera.stopPreview();
                camera.unlock();
                try {
                    recorder = new MediaRecorder();
                    recorder.setCamera(camera);
                    recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
                    recorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
                    session.configureRecorder(1, recorder);
                    recorder.setOutputFile(path.getAbsolutePath());
                    recorder.setMaxFileSize(1024 * 1024 * 1024);
                    recorder.setVideoFrameRate(30);
                    recorder.setMaxDuration(0);
                    org.telegram.messenger.camera.Size pictureSize = new Size(16, 9);
                    pictureSize = CameraController.chooseOptimalSize(info.getPictureSizes(), 720, 480, pictureSize);
                    recorder.setVideoSize(pictureSize.getWidth(), pictureSize.getHeight());
                    recorder.setVideoEncodingBitRate(900000 * 2);
                    recorder.setOnInfoListener(this);

                    recorder.prepare();
                    recorder.start();
                    onVideoTakeCallback = callback;
                    recordedFile = path.getAbsolutePath();
                } catch (Exception e) {
                    recorder.release();
                    recorder = null;
                    FileLog.e("tmessages", e);
                }
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
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
            final Bitmap bitmap = ThumbnailUtils.createVideoThumbnail(recordedFile, MediaStore.Video.Thumbnails.MINI_KIND);
            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    onVideoTakeCallback.onFinishVideoRecording(bitmap);
                }
            });
        }
    }

    public void stopVideoRecording(CameraSession session, boolean abandon) {
        try {
            CameraInfo info = session.cameraInfo;
            Camera camera = info.camera;
            if (camera != null && recorder != null) {
                MediaRecorder tempRecorder = recorder;
                recorder = null;
                tempRecorder.stop();
                tempRecorder.release();
                camera.reconnect();
                camera.startPreview();
                session.stopVideoRecording();
            }
            if (!abandon) {
                final Bitmap bitmap = ThumbnailUtils.createVideoThumbnail(recordedFile, MediaStore.Video.Thumbnails.MINI_KIND);
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        onVideoTakeCallback.onFinishVideoRecording(bitmap);
                    }
                });
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
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
