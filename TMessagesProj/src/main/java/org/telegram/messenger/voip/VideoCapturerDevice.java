package org.telegram.messenger.voip;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Display;
import android.view.WindowManager;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.CapturerObserver;
import org.webrtc.EglBase;
import org.webrtc.Logging;
import org.webrtc.ScreenCapturerAndroid;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoCapturer;
import org.webrtc.voiceengine.WebRtcAudioRecord;

@TargetApi(18)
public class VideoCapturerDevice {

    private static final int CAPTURE_WIDTH = Build.VERSION.SDK_INT <= 19 ? 480 : 1280;
    private static final int CAPTURE_HEIGHT = Build.VERSION.SDK_INT <= 19 ? 320 : 720;
    private static final int CAPTURE_FPS = 30;

    public static EglBase eglBase;

    public static Intent mediaProjectionPermissionResultData;

    private VideoCapturer videoCapturer;
    private SurfaceTextureHelper videoCapturerSurfaceTextureHelper;

    private HandlerThread thread;
    private Handler handler;
    private int currentWidth;
    private int currentHeight;

    private long nativePtr;

    private static VideoCapturerDevice[] instance = new VideoCapturerDevice[2];
    private CapturerObserver nativeCapturerObserver;

    public VideoCapturerDevice(boolean screencast) {
        if (Build.VERSION.SDK_INT < 18) {
            return;
        }
        Logging.enableLogToDebugOutput(Logging.Severity.LS_INFO);
        Logging.d("VideoCapturerDevice", "device model = " + Build.MANUFACTURER + Build.MODEL);
        AndroidUtilities.runOnUIThread(() -> {
            if (eglBase == null) {
                eglBase = EglBase.create(null, EglBase.CONFIG_PLAIN);
            }
            instance[screencast ? 1 : 0] = this;
            thread = new HandlerThread("CallThread");
            thread.start();
            handler = new Handler(thread.getLooper());
        });
    }

    public static void checkScreenCapturerSize() {
        if (instance[1] == null) {
            return;
        }
        Point size = getScreenCaptureSize();
        if (instance[1].currentWidth != size.x || instance[1].currentHeight != size.y) {
            instance[1].currentWidth = size.x;
            instance[1].currentHeight = size.y;
            VideoCapturerDevice device = instance[1];
            instance[1].handler.post(() -> {
                if (device.videoCapturer != null) {
                    device.videoCapturer.changeCaptureFormat(size.x, size.y, CAPTURE_FPS);
                }
            });
        }
    }

    private static Point getScreenCaptureSize() {
        WindowManager wm = (WindowManager) ApplicationLoader.applicationContext.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        Point size = new Point();
        display.getRealSize(size);

        float aspect;
        if (size.x > size.y) {
            aspect = size.y / (float) size.x;
        } else {
            aspect = size.x / (float) size.y;
        }
        int dx = -1;
        int dy = -1;
        for (int a = 1; a <= 100; a++) {
            float val = a * aspect;
            if (val == (int) val) {
                if (size.x > size.y) {
                    dx = a;
                    dy = (int) (a * aspect);
                } else {
                    dy = a;
                    dx = (int) (a * aspect);
                }
                break;
            }
        }
        if (dx != -1 && aspect != 1) {
            while (size.x > 1000 || size.y > 1000 || size.x % 4 != 0 || size.y % 4 != 0) {
                size.x -= dx;
                size.y -= dy;
                if (size.x < 800 && size.y < 800) {
                    dx = -1;
                    break;
                }
            }
        }
        if (dx == -1 || aspect == 1) {
            float scale = Math.max(size.x / 970.0f, size.y / 970.0f);
            size.x = (int) Math.ceil((size.x / scale) / 4.0f) * 4;
            size.y = (int) Math.ceil((size.y / scale) / 4.0f) * 4;
        }
        return size;
    }

    private void init(long ptr, String deviceName) {
        if (Build.VERSION.SDK_INT < 18) {
            return;
        }
        AndroidUtilities.runOnUIThread(() -> {
            if (eglBase == null) {
                return;
            }
            nativePtr = ptr;
            if ("screen".equals(deviceName)) {
                if (Build.VERSION.SDK_INT < 21) {
                    return;
                }
                if (videoCapturer == null) {
                    videoCapturer = new ScreenCapturerAndroid(mediaProjectionPermissionResultData, new MediaProjection.Callback() {
                        @Override
                        public void onStop() {
                            AndroidUtilities.runOnUIThread(() -> {
                                if (VoIPService.getSharedInstance() != null) {
                                    VoIPService.getSharedInstance().stopScreenCapture();
                                }
                            });
                        }
                    });


                    Point size = getScreenCaptureSize();
                    currentWidth = size.x;
                    currentHeight = size.y;
                    videoCapturerSurfaceTextureHelper = SurfaceTextureHelper.create("ScreenCapturerThread", eglBase.getEglBaseContext());
                    handler.post(() -> {
                        if (videoCapturerSurfaceTextureHelper == null || nativePtr == 0) {
                            return;
                        }
                        nativeCapturerObserver = nativeGetJavaVideoCapturerObserver(nativePtr);
                        videoCapturer.initialize(videoCapturerSurfaceTextureHelper, ApplicationLoader.applicationContext, nativeCapturerObserver);
                        videoCapturer.startCapture(size.x, size.y, CAPTURE_FPS);
                        WebRtcAudioRecord audioRecord = WebRtcAudioRecord.Instance;
                        if (audioRecord != null) {
                            audioRecord.initDeviceAudioRecord(((ScreenCapturerAndroid) videoCapturer).getMediaProjection());
                        }
                    });
                }
            } else {
                CameraEnumerator enumerator = Camera2Enumerator.isSupported(ApplicationLoader.applicationContext) ? new Camera2Enumerator(ApplicationLoader.applicationContext) : new Camera1Enumerator();
                int index = -1;
                String[] names = enumerator.getDeviceNames();
                for (int a = 0; a < names.length; a++) {
                    boolean isFrontFace = enumerator.isFrontFacing(names[a]);
                    if (isFrontFace == "front".equals(deviceName)) {
                        index = a;
                        break;
                    }
                }
                if (index == -1) {
                    return;
                }
                String cameraName = names[index];
                if (videoCapturer == null) {
                    videoCapturer = enumerator.createCapturer(cameraName, new CameraVideoCapturer.CameraEventsHandler() {
                        @Override
                        public void onCameraError(String errorDescription) {

                        }

                        @Override
                        public void onCameraDisconnected() {

                        }

                        @Override
                        public void onCameraFreezed(String errorDescription) {

                        }

                        @Override
                        public void onCameraOpening(String cameraName) {

                        }

                        @Override
                        public void onFirstFrameAvailable() {
                            AndroidUtilities.runOnUIThread(() -> {
                                if (VoIPService.getSharedInstance() != null) {
                                    VoIPService.getSharedInstance().onCameraFirstFrameAvailable();
                                }
                            });
                        }

                        @Override
                        public void onCameraClosed() {

                        }
                    });
                    videoCapturerSurfaceTextureHelper = SurfaceTextureHelper.create("VideoCapturerThread", eglBase.getEglBaseContext());
                    handler.post(() -> {
                        if (videoCapturerSurfaceTextureHelper == null) {
                            return;
                        }
                        nativeCapturerObserver = nativeGetJavaVideoCapturerObserver(nativePtr);
                        videoCapturer.initialize(videoCapturerSurfaceTextureHelper, ApplicationLoader.applicationContext, nativeCapturerObserver);
                        videoCapturer.startCapture(CAPTURE_WIDTH, CAPTURE_HEIGHT, CAPTURE_FPS);
                    });
                } else {
                    handler.post(() -> ((CameraVideoCapturer) videoCapturer).switchCamera(new CameraVideoCapturer.CameraSwitchHandler() {
                        @Override
                        public void onCameraSwitchDone(boolean isFrontCamera) {
                            AndroidUtilities.runOnUIThread(() -> {
                                if (VoIPService.getSharedInstance() != null) {
                                    VoIPService.getSharedInstance().setSwitchingCamera(false, isFrontCamera);
                                }
                            });
                        }

                        @Override
                        public void onCameraSwitchError(String errorDescription) {

                        }
                    }, cameraName));
                }
            }
        });
    }

    public static MediaProjection getMediaProjection() {
        if (instance[1] == null) {
            return null;
        }
        return ((ScreenCapturerAndroid) instance[1].videoCapturer).getMediaProjection();
    }

    private void onAspectRatioRequested(float aspectRatio) {
        /*if (aspectRatio < 0.0001f) {
            return;
        }
        handler.post(() -> {
            if (nativeCapturerObserver instanceof NativeCapturerObserver) {
                int w;
                int h;
                if (aspectRatio < 1.0f) {
                    h = CAPTURE_HEIGHT;
                    w = (int) (h / aspectRatio);
                } else {
                    w = CAPTURE_WIDTH;
                    h = (int) (w * aspectRatio);
                }
                if (w <= 0 || h <= 0) {
                    return;
                }
                NativeCapturerObserver observer = (NativeCapturerObserver) nativeCapturerObserver;
                NativeAndroidVideoTrackSource source = observer.getNativeAndroidVideoTrackSource();
                source.adaptOutputFormat(new VideoSource.AspectRatio(w, h), w * h, new VideoSource.AspectRatio(h, w), w * h, CAPTURE_FPS);
            }
        });*/
    }

    private void onStateChanged(long ptr, int state) {
        if (Build.VERSION.SDK_INT < 18) {
            return;
        }
        AndroidUtilities.runOnUIThread(() -> {
            if (nativePtr != ptr) {
                return;
            }
            handler.post(() -> {
                if (videoCapturer == null) {
                    return;
                }
                if (state == Instance.VIDEO_STATE_ACTIVE) {
                    videoCapturer.startCapture(CAPTURE_WIDTH, CAPTURE_HEIGHT, CAPTURE_FPS);
                } else {
                    try {
                        videoCapturer.stopCapture();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        });
    }

    private void onDestroy() {
        if (Build.VERSION.SDK_INT < 18) {
            return;
        }
        nativePtr = 0;
        AndroidUtilities.runOnUIThread(() -> {
//            if (eglBase != null) {
//                eglBase.release();
//                eglBase = null;
//            }
            for (int a = 0; a < instance.length; a++) {
                if (instance[a] == this) {
                    instance[a] = null;
                    break;
                }
            }
            handler.post(() -> {
                if (videoCapturer instanceof ScreenCapturerAndroid) {
                    WebRtcAudioRecord audioRecord = WebRtcAudioRecord.Instance;
                    if (audioRecord != null) {
                        audioRecord.stopDeviceAudioRecord();
                    }
                }
                if (videoCapturer != null) {
                    try {
                        videoCapturer.stopCapture();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    videoCapturer.dispose();
                    videoCapturer = null;
                }
                if (videoCapturerSurfaceTextureHelper != null) {
                    videoCapturerSurfaceTextureHelper.dispose();
                    videoCapturerSurfaceTextureHelper = null;
                }
            });
            try {
                thread.quitSafely();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    private EglBase.Context getSharedEGLContext() {
        if (eglBase == null) {
            eglBase = EglBase.create(null, EglBase.CONFIG_PLAIN);
        }
        return eglBase != null ? eglBase.getEglBaseContext() : null;
    }

    public static EglBase getEglBase() {
        if (eglBase == null) {
            eglBase = EglBase.create(null, EglBase.CONFIG_PLAIN);
        }
        return eglBase;
    }

    private static native CapturerObserver nativeGetJavaVideoCapturerObserver(long ptr);
}
