package org.telegram.ui.Components.voip;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Outline;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.voip.Instance;
import org.telegram.messenger.voip.VideoCapturerDevice;
import org.telegram.messenger.voip.VoIPService;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.webrtc.RendererCommon;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;

/**
 * Own camera in a call's system PiP: a small rounded preview in the top-right corner (the
 * microphone is top-left, names are at the bottom). Fills its parent and places the preview
 * itself. Frames come as an extra receiver of the local camera, independent of the call UI.
 */
@SuppressLint("ViewConstructor")
final class CallPipSelfView extends ViewGroup {
    /** Share of the PiP area the preview takes. */
    private static final float AREA_FRACTION = 0.1f;
    /** The preview never takes more of the PiP width or height than this. */
    private static final float MAX_SIDE_FRACTION = 0.42f;
    private static final float DEFAULT_ASPECT = 3f / 4f;
    /** No own frames for this long (camera paused or stopped): the preview goes away. */
    private static final long STALL_MS = 1000;

    private final FrameLayout frame;
    private final VoIPTextureView textureView;
    private final int margin = AndroidUtilities.dp(6);
    private final int radius = AndroidUtilities.dp(6);

    private boolean started;
    private boolean shown;
    private boolean mirror = true;
    /** Width / height of the own frames, 0 until known. */
    private float frameAspect;

    // written by the capture thread
    private volatile long lastFrameTime;
    private volatile int frameWidth, frameHeight;
    private volatile boolean updatePosted;
    private final Runnable postedUpdate = () -> {
        updatePosted = false;
        update();
    };
    /**
     * Counts the frames itself: a renderer that is not visible has no surface and never reports
     * its first frame, and the preview is shown only once frames come.
     */
    private final VideoSink frameSink = new VideoSink() {
        @Override
        public void onFrame(VideoFrame frame) {
            final boolean wasStalled = android.os.SystemClock.elapsedRealtime() - lastFrameTime > STALL_MS;
            lastFrameTime = android.os.SystemClock.elapsedRealtime();
            frameWidth = frame.getRotatedWidth();
            frameHeight = frame.getRotatedHeight();
            textureView.renderer.onFrame(frame);
            if (wasStalled && !updatePosted) {
                updatePosted = true;
                AndroidUtilities.runOnUIThread(postedUpdate);
            }
        }
    };

    CallPipSelfView(@NonNull Context context) {
        super(context);
        frame = new FrameLayout(context);
        frame.setBackgroundColor(0xFF1B1F23);
        if (Build.VERSION.SDK_INT >= 21) {
            frame.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
                }
            });
            frame.setClipToOutline(true);
            frame.setElevation(AndroidUtilities.dp(2));
        }
        textureView = new VoIPTextureView(context, false, true, false, false);
        textureView.renderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
        textureView.renderer.setEnableHardwareScaler(true);
        textureView.renderer.setMirror(true);
        textureView.scaleType = VoIPTextureView.SCALE_TYPE_FILL;
        if (textureView.backgroundView != null) {
            textureView.backgroundView.setVisibility(View.GONE);
        }
        frame.addView(textureView);
        frame.setAlpha(0f);
        frame.setScaleX(0.6f);
        frame.setScaleY(0.6f);
        frame.setVisibility(View.INVISIBLE);
        addView(frame);
    }

    void start() {
        if (started) {
            return;
        }
        started = true;
        textureView.renderer.init(VideoCapturerDevice.getEglBase().getEglBaseContext(), null);
        final VoIPService service = VoIPService.getSharedInstance();
        if (service != null) {
            service.addExtraLocalSink(frameSink);
        }
        update();
    }

    void stop() {
        if (!started) {
            return;
        }
        started = false;
        final VoIPService service = VoIPService.getSharedInstance();
        if (service != null) {
            service.removeExtraLocalSink(frameSink);
        }
        AndroidUtilities.cancelRunOnUIThread(postedUpdate);
        updatePosted = false;
        textureView.renderer.release();
    }

    /** Own camera sends video (not a screen share). */
    static boolean isCameraOn(@Nullable VoIPService service) {
        return service != null && service.getVideoState(false) == Instance.VIDEO_STATE_ACTIVE && !service.isScreencast();
    }

    void update() {
        if (!started) {
            return;
        }
        final VoIPService service = VoIPService.getSharedInstance();
        final boolean cameraOn = isCameraOn(service);
        final boolean hasFrames = android.os.SystemClock.elapsedRealtime() - lastFrameTime < STALL_MS;
        if (service != null && mirror != service.isFrontFaceCamera()) {
            mirror = service.isFrontFaceCamera();
            textureView.renderer.setMirror(mirror);
        }
        final int fw = frameWidth, fh = frameHeight;
        final float aspect = fw > 0 && fh > 0 ? fw / (float) fh : 0;
        if (aspect > 0 && Math.abs(aspect - frameAspect) > 0.01f) {
            frameAspect = aspect;
            requestLayout();
        }
        setShown(cameraOn && hasFrames);
    }

    private void setShown(boolean show) {
        if (shown == show) {
            return;
        }
        shown = show;
        frame.animate().cancel();
        if (show) {
            frame.setVisibility(View.VISIBLE);
            frame.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).withEndAction(null).start();
        } else {
            frame.animate().alpha(0f).scaleX(0.6f).scaleY(0.6f).setDuration(180).setInterpolator(CubicBezierInterpolator.EASE_OUT).withEndAction(() -> frame.setVisibility(View.INVISIBLE)).start();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int width = MeasureSpec.getSize(widthMeasureSpec), height = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(width, height);
        final float aspect = frameAspect > 0 ? frameAspect : DEFAULT_ASPECT;
        float h = (float) Math.sqrt(AREA_FRACTION * width * height / aspect);
        float w = h * aspect;
        final float scale = Math.min(1f, Math.min(MAX_SIDE_FRACTION * width / Math.max(1f, w), MAX_SIDE_FRACTION * height / Math.max(1f, h)));
        w *= scale;
        h *= scale;
        frame.measure(MeasureSpec.makeMeasureSpec(Math.max(1, Math.round(w)), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(Math.max(1, Math.round(h)), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        final int right = r - l - margin;
        frame.layout(right - frame.getMeasuredWidth(), margin, right, margin + frame.getMeasuredHeight());
        frame.setPivotX(frame.getMeasuredWidth());
        frame.setPivotY(0);
    }
}
