package org.telegram.messenger.voip;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.CapturerObserver;
import org.webrtc.EglBase;
import org.webrtc.SurfaceTextureHelper;

@TargetApi(18)
public class VideoCameraCapturer {

    private static final int CAPTURE_WIDTH = Build.VERSION.SDK_INT <= 19 ? 480 : 1280;
    private static final int CAPTURE_HEIGHT = Build.VERSION.SDK_INT <= 19 ? 320 : 720;
    private static final int CAPTURE_FPS = 30;

    public static EglBase eglBase;

    private CameraVideoCapturer videoCapturer;
    private SurfaceTextureHelper videoCapturerSurfaceTextureHelper;

    private HandlerThread thread;
    private Handler handler;

    private long nativePtr;

    private static VideoCameraCapturer instance;
    private CapturerObserver nativeCapturerObserver;

    public static VideoCameraCapturer getInstance() {
        return instance;
    }

    public VideoCameraCapturer() {
        if (Build.VERSION.SDK_INT < 18) {
            return;
        }
        AndroidUtilities.runOnUIThread(() -> {
            instance = this;
            thread = new HandlerThread("CallThread");
            thread.start();
            handler = new Handler(thread.getLooper());

            if (eglBase == null) {
                eglBase = EglBase.create(null, EglBase.CONFIG_PLAIN);
            }
        });
    }

    private void init(long ptr, boolean useFrontCamera) {
        if (Build.VERSION.SDK_INT < 18) {
            return;
        }
        AndroidUtilities.runOnUIThread(() -> {
            if (eglBase == null) {
                return;
            }
            nativePtr = ptr;
            CameraEnumerator enumerator = Camera2Enumerator.isSupported(ApplicationLoader.applicationContext) ? new Camera2Enumerator(ApplicationLoader.applicationContext) : new Camera1Enumerator();
            int index = -1;
            String[] names = enumerator.getDeviceNames();
            for (int a = 0; a < names.length; a++) {
                boolean isFrontFace = enumerator.isFrontFacing(names[a]);
                if (isFrontFace == useFrontCamera) {
                    index = a;
                    break;
                }
            }
            if (index == -1) {
                return;
            }
            String cameraName = names[index];
            if (videoCapturer == null) {
                videoCapturer = enumerator.createCapturer(cameraName, null);
                videoCapturerSurfaceTextureHelper = SurfaceTextureHelper.create("VideoCapturerThread", eglBase.getEglBaseContext());
                handler.post(() -> {
                    nativeCapturerObserver = nativeGetJavaVideoCapturerObserver(nativePtr);
                    videoCapturer.initialize(videoCapturerSurfaceTextureHelper, ApplicationLoader.applicationContext, nativeCapturerObserver);
                    videoCapturer.startCapture(CAPTURE_WIDTH, CAPTURE_HEIGHT, CAPTURE_FPS);
                });
            } else {
                handler.post(() -> videoCapturer.switchCamera(new CameraVideoCapturer.CameraSwitchHandler() {
                    @Override
                    public void onCameraSwitchDone(boolean isFrontCamera) {
                        AndroidUtilities.runOnUIThread(() -> {
                            if (VoIPBaseService.getSharedInstance() != null) {
                                VoIPBaseService.getSharedInstance().setSwitchingCamera(false, isFrontCamera);
                            }
                        });
                    }

                    @Override
                    public void onCameraSwitchError(String errorDescription) {

                    }
                }, cameraName));
            }
        });
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
        AndroidUtilities.runOnUIThread(() -> {
            if (eglBase != null) {
                eglBase.release();
                eglBase = null;
            }
            if (instance == this) {
                instance = null;
            }
            handler.post(() -> {
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

    private static native CapturerObserver nativeGetJavaVideoCapturerObserver(long ptr);
}
