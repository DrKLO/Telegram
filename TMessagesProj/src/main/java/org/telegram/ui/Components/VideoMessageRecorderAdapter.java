package org.telegram.ui.Components;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Display;
import android.view.Surface;
import android.view.TextureView;

import java.io.File;

import io.github.webarmour.videomessagerecorder.DiagnosticsMode;
import io.github.webarmour.videomessagerecorder.RecordingMode;
import io.github.webarmour.videomessagerecorder.RecordingResult;
import io.github.webarmour.videomessagerecorder.VideoMessageRecorder;
import io.github.webarmour.videomessagerecorder.camera.CameraUiState;
import kotlin.Unit;

/**
 * Adapter between Telegram's existing circular preview/record-button UI and
 * video-message-recorder 0.0.2. This is SOURCE-INTEGRATION CODE, not an APK patch.
 *
 * Owns the VideoMessageRecorder and a Surface wrapper. The TextureView belongs
 * to Telegram. A separate Telegram integration must supply onReady, stop/send,
 * cancellation and the existing video-note sending pipeline.
 *
 * All public control methods must be called on the Android main thread.
 */
public final class VideoMessageRecorderAdapter implements TextureView.SurfaceTextureListener {

    public interface Listener {
        void onCameraReady();
        void onRecordingStarted();
        /** Caller MUST send as Telegram video note, not as generic video. */
        void onVideoNoteReady(File videoFile, long durationMs);
        void onCancelled();
        void onError(Exception exception);
    }

    private enum Completion { NONE, SEND, DISCARD }

    private final Context applicationContext;
    private final TextureView preview;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private VideoMessageRecorder recorder;
    private Surface surface;
    private boolean pendingStart;
    private boolean recordStarted;
    private boolean cameraReadyNotified;
    private Completion completion = Completion.NONE;
    private long startedAt;

    public VideoMessageRecorderAdapter(Context context, TextureView preview, Listener listener) {
        this.applicationContext = context.getApplicationContext();
        this.preview = preview;
        this.listener = listener;
    }

    /** Call once for each video-note session, after Telegram has obtained permissions. */
    public void startSession() {
        requireMainThread();
        disposeRecorder();
        if (Build.VERSION.SDK_INT < 28) {
            listener.onError(new UnsupportedOperationException("Recorder requires Android 9 (API 28)+"));
            return;
        }
        if (applicationContext.checkSelfPermission(Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED ||
                applicationContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
            listener.onError(new SecurityException("CAMERA and RECORD_AUDIO permissions are required"));
            return;
        }
        completion = Completion.NONE;
        pendingStart = true;
        recordStarted = false;
        cameraReadyNotified = false;
        startedAt = 0L;
        // Use explicit constructor parameters, matching the actual 0.0.2 AAR API.
        recorder = new VideoMessageRecorder(
                applicationContext,
                RoundVideoRecorderSettings.recordingMode(applicationContext),
                DiagnosticsMode.Disabled,
                state -> {
                    main.post(() -> onRecorderState(state));
                    return Unit.INSTANCE;
                },
                result -> {
                    File file = result.getFile();
                    main.post(() -> onRecordingFinished(file));
                    return Unit.INSTANCE;
                }
        );
        preview.setSurfaceTextureListener(this);
        if (preview.isAvailable()) {
            attachPreview(preview.getSurfaceTexture(), preview.getWidth(), preview.getHeight());
        }
        recorder.setPermissionGranted(true); // actual permissions verified above
        recorder.onStart();
    }

    /** Initiates finalization; file arrives asynchronously via Listener. */
    public void finishAndSend() {
        requireMainThread();
        if (recorder == null) return;
        if (!recordStarted) {
            cancel();
            return;
        }
        completion = Completion.SEND;
        pendingStart = false;
        recorder.stopRecording();
    }

    /** Telegram's swipe-to-cancel: finalize and discard the resulting MP4. */
    public void cancel() {
        requireMainThread();
        if (recorder == null) return;
        completion = Completion.DISCARD;
        pendingStart = false;
        if (recordStarted) {
            recorder.stopRecording();
        } else {
            disposeRecorder();
            listener.onCancelled();
        }
    }

    public void switchCamera() {
        requireMainThread();
        if (recorder != null && completion == Completion.NONE) recorder.switchCamera();
    }

    public void zoom(float ratio) {
        requireMainThread();
        if (recorder != null && completion == Completion.NONE) recorder.updateZoomRatio(ratio);
    }

    /** Call from Telegram's UI destruction path, not after stopRecording(). */
    public void close() {
        requireMainThread();
        disposeRecorder();
    }

    private void onRecorderState(CameraUiState state) {
        // The AAR callback may run on a camera or worker thread; this method runs on main.
        if (recorder == null || completion != Completion.NONE) return;
        if (state.getCameraReady() && !state.isBusy() && !state.isSwitchingCamera()) {
            if (!cameraReadyNotified) {
                cameraReadyNotified = true;
                listener.onCameraReady();
            }
            if (pendingStart && !state.isRecording()) {
                pendingStart = false;
                recordStarted = true;
                startedAt = SystemClock.elapsedRealtime();
                recorder.startRecording();
                listener.onRecordingStarted();
            }
        }
    }

    private void onRecordingFinished(File file) {
        if (recorder == null) {
            // This adapter no longer owns the session. Avoid stale callbacks.
            if (completion == Completion.DISCARD) file.delete();
            return;
        }
        Completion action = completion;
        long durationMs = startedAt == 0 ? 0 : SystemClock.elapsedRealtime() - startedAt;
        disposeRecorder();
        if (action == Completion.SEND) {
            listener.onVideoNoteReady(file, durationMs);
        } else {
            file.delete();
            listener.onCancelled();
        }
    }

    private void attachPreview(SurfaceTexture texture, int width, int height) {
        if (recorder == null || texture == null || width <= 0 || height <= 0) return;
        if (surface != null) {
            recorder.detachPreview();
            surface.release();
        }
        surface = new Surface(texture);
        Display display = preview.getDisplay();
        int rotation = display == null ? Surface.ROTATION_0 : display.getRotation();
        recorder.attachPreview(surface, width, height, rotation);
    }

    private void disposeRecorder() {
        pendingStart = false;
        recordStarted = false;
        if (recorder != null) {
            recorder.detachPreview();
            recorder.onStop();
            recorder.close();
            recorder = null;
        }
        if (surface != null) {
            surface.release();
            surface = null;
        }
        if (preview.getSurfaceTextureListener() == this) {
            preview.setSurfaceTextureListener(null);
        }
    }

    private static void requireMainThread() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new IllegalStateException("VideoMessageRecorderAdapter must run on main thread");
        }
    }

    @Override public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) {
        attachPreview(st, w, h);
    }
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) {
        // Do not recreate the camera pipeline during an active recording.
        if (surface == null) attachPreview(st, w, h);
    }
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
        if (recorder != null) recorder.detachPreview();
        if (surface != null) surface.release();
        surface = null;
        return true; // TextureView releases the SurfaceTexture itself.
    }
    @Override public void onSurfaceTextureUpdated(SurfaceTexture st) { }
}
