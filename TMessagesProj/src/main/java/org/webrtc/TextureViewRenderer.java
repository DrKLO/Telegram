package org.webrtc;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.os.Looper;
import android.view.TextureView;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.voip.VoIPService;

import java.util.concurrent.CountDownLatch;

public class TextureViewRenderer extends TextureView
        implements TextureView.SurfaceTextureListener, VideoSink, RendererCommon.RendererEvents {

    private static final String TAG = "TextureViewRenderer";

    // Cached resource name.
    private final String resourceName;
    private final RendererCommon.VideoLayoutMeasure videoLayoutMeasure =
            new RendererCommon.VideoLayoutMeasure();
    private final TextureEglRenderer eglRenderer;

    // Callback for reporting renderer events. Read-only after initialization so no lock required.
    private RendererCommon.RendererEvents rendererEvents;

    // Accessed only on the main thread.
    public int rotatedFrameWidth;
    public int rotatedFrameHeight;

    private int videoWidth;
    private int videoHeight;
    private boolean enableFixedSize;
    private int surfaceWidth;
    private int surfaceHeight;
    private boolean isCamera;
    private boolean mirror;
    private boolean rotateTextureWithScreen;
    private int screenRotation;

    private OrientationHelper orientationHelper;
    private int cameraRotation;
    private TextureView backgroundRenderer;
    private int maxTextureSize;

    private VideoSink parentSink;

    Runnable updateScreenRunnable;

    public void setBackgroundRenderer(@Nullable TextureView backgroundRenderer) {
        if (!LiteMode.isEnabled(LiteMode.FLAG_CALLS_ANIMATIONS)) {
            return;
        }
        this.backgroundRenderer = backgroundRenderer;
        if (backgroundRenderer == null) {
            ThreadUtils.checkIsOnMainThread();
            eglRenderer.releaseEglSurface(null, true);
            return;
        }
        backgroundRenderer.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surfaceTexture, int i, int i1) {
                createBackgroundSurface(surfaceTexture);
            }

            @Override
            public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surfaceTexture, int i, int i1) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surfaceTexture) {
                ThreadUtils.checkIsOnMainThread();
                eglRenderer.releaseEglSurface(null, true);
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surfaceTexture) {

            }
        });
    }

    public void clearFirstFrame() {
        eglRenderer.firstFrameRendered = false;
        eglRenderer.isFirstFrameRendered = false;
    }

    public static class TextureEglRenderer extends EglRenderer implements TextureView.SurfaceTextureListener {
        private static final String TAG = "TextureEglRenderer";

        // Callback for reporting renderer events. Read-only after initialization so no lock required.
        private RendererCommon.RendererEvents rendererEvents;

        private final Object layoutLock = new Object();
        private boolean isRenderingPaused;
        private boolean isFirstFrameRendered;
        private int rotatedFrameWidth;
        private int rotatedFrameHeight;
        private int frameRotation;

        /**
         * In order to render something, you must first call init().
         */
        public TextureEglRenderer(String name) {
            super(name);
        }

        /**
         * Initialize this class, sharing resources with |sharedContext|. The custom |drawer| will be used
         * for drawing frames on the EGLSurface. This class is responsible for calling release() on
         * |drawer|. It is allowed to call init() to reinitialize the renderer after a previous
         * init()/release() cycle.
         */
        public void init(final EglBase.Context sharedContext,
                         RendererCommon.RendererEvents rendererEvents, final int[] configAttributes,
                         RendererCommon.GlDrawer drawer) {
            ThreadUtils.checkIsOnMainThread();
            this.rendererEvents = rendererEvents;
            synchronized (layoutLock) {
                isFirstFrameRendered = false;
                rotatedFrameWidth = 0;
                rotatedFrameHeight = 0;
                frameRotation = 0;
            }
            super.init(sharedContext, configAttributes, drawer);
        }

        @Override
        public void init(final EglBase.Context sharedContext, final int[] configAttributes,
                         RendererCommon.GlDrawer drawer) {
            init(sharedContext, null /* rendererEvents */, configAttributes, drawer);
        }

        /**
         * Limit render framerate.
         *
         * @param fps Limit render framerate to this value, or use Float.POSITIVE_INFINITY to disable fps
         *            reduction.
         */
        @Override
        public void setFpsReduction(float fps) {
            synchronized (layoutLock) {
                isRenderingPaused = fps == 0f;
            }
            super.setFpsReduction(fps);
        }

        @Override
        public void disableFpsReduction() {
            synchronized (layoutLock) {
                isRenderingPaused = false;
            }
            super.disableFpsReduction();
        }

        @Override
        public void pauseVideo() {
            synchronized (layoutLock) {
                isRenderingPaused = true;
            }
            super.pauseVideo();
        }

        // VideoSink interface.
        @Override
        public void onFrame(VideoFrame frame) {
            updateFrameDimensionsAndReportEvents(frame);
            super.onFrame(frame);
        }

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
            ThreadUtils.checkIsOnMainThread();
            createEglSurface(surfaceTexture);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
            ThreadUtils.checkIsOnMainThread();
            logD("surfaceChanged: size: " + width + "x" + height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            ThreadUtils.checkIsOnMainThread();
            final CountDownLatch completionLatch = new CountDownLatch(1);
            releaseEglSurface(completionLatch::countDown, false);
            ThreadUtils.awaitUninterruptibly(completionLatch);
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

        }

        // Update frame dimensions and report any changes to |rendererEvents|.
        private void updateFrameDimensionsAndReportEvents(VideoFrame frame) {
            synchronized (layoutLock) {
                if (isRenderingPaused) {
                    return;
                }
                if (rotatedFrameWidth != frame.getRotatedWidth()
                        || rotatedFrameHeight != frame.getRotatedHeight()
                        || frameRotation != frame.getRotation()) {
                    logD("Reporting frame resolution changed to " + frame.getBuffer().getWidth() + "x"
                            + frame.getBuffer().getHeight() + " with rotation " + frame.getRotation());
                    if (rendererEvents != null) {
                        rendererEvents.onFrameResolutionChanged(
                                frame.getBuffer().getWidth(), frame.getBuffer().getHeight(), frame.getRotation());
                    }
                    rotatedFrameWidth = frame.getRotatedWidth();
                    rotatedFrameHeight = frame.getRotatedHeight();

                    frameRotation = frame.getRotation();
                }
            }
        }

        private void logD(String string) {
            Logging.d(TAG, name + ": " + string);
        }

        @Override
        protected void onFirstFrameRendered() {
            AndroidUtilities.runOnUIThread(() -> {
                isFirstFrameRendered = true;
                rendererEvents.onFirstFrameRendered();
            });
        }
    }

    /**
     * Standard View constructor. In order to render something, you must first call init().
     */
    public TextureViewRenderer(Context context) {
        super(context);
        this.resourceName = getResourceName();
        eglRenderer = new TextureEglRenderer(resourceName);
        setSurfaceTextureListener(this);
    }

    /**
     * Initialize this class, sharing resources with |sharedContext|. It is allowed to call init() to
     * reinitialize the renderer after a previous init()/release() cycle.
     */
    public void init(EglBase.Context sharedContext, RendererCommon.RendererEvents rendererEvents) {
        init(sharedContext, rendererEvents, EglBase.CONFIG_PLAIN, new GlRectDrawer());
    }

    /**
     * Initialize this class, sharing resources with |sharedContext|. The custom |drawer| will be used
     * for drawing frames on the EGLSurface. This class is responsible for calling release() on
     * |drawer|. It is allowed to call init() to reinitialize the renderer after a previous
     * init()/release() cycle.
     */
    public void init(final EglBase.Context sharedContext,
                     RendererCommon.RendererEvents rendererEvents, final int[] configAttributes,
                     RendererCommon.GlDrawer drawer) {
        ThreadUtils.checkIsOnMainThread();
        this.rendererEvents = rendererEvents;
        rotatedFrameWidth = 0;
        rotatedFrameHeight = 0;
        eglRenderer.init(sharedContext, this /* rendererEvents */, configAttributes, drawer);
    }

    /**
     * Block until any pending frame is returned and all GL resources released, even if an interrupt
     * occurs. If an interrupt occurs during release(), the interrupt flag will be set. This function
     * should be called before the Activity is destroyed and the EGLContext is still valid. If you
     * don't call this function, the GL resources might leak.
     */
    public void release() {
        eglRenderer.release();
        if (orientationHelper != null) {
            orientationHelper.stop();
        }
    }

    /**
     * Register a callback to be invoked when a new video frame has been received.
     *
     * @param listener The callback to be invoked. The callback will be invoked on the render thread.
     *                 It should be lightweight and must not call removeFrameListener.
     * @param scale    The scale of the Bitmap passed to the callback, or 0 if no Bitmap is
     *                 required.
     */
    public void addFrameListener(
            EglRenderer.FrameListener listener, float scale, RendererCommon.GlDrawer drawerParam) {
        eglRenderer.addFrameListener(listener, scale, drawerParam);
    }

    public void getRenderBufferBitmap(GlGenericDrawer.TextureCallback callback) {
        eglRenderer.getTexture(callback);
    }

    /**
     * Register a callback to be invoked when a new video frame has been received. This version uses
     * the drawer of the EglRenderer that was passed in init.
     *
     * @param listener The callback to be invoked. The callback will be invoked on the render thread.
     *                 It should be lightweight and must not call removeFrameListener.
     * @param scale    The scale of the Bitmap passed to the callback, or 0 if no Bitmap is
     *                 required.
     */
    public void addFrameListener(EglRenderer.FrameListener listener, float scale) {
        eglRenderer.addFrameListener(listener, scale);
    }

    public void removeFrameListener(EglRenderer.FrameListener listener) {
        eglRenderer.removeFrameListener(listener);
    }

    public void setIsCamera(boolean value) {
        isCamera = value;
        if (!isCamera) {
            orientationHelper = new OrientationHelper() {
                @Override
                protected void onOrientationUpdate(int orientation) {
                    if (!isCamera) {
                        updateRotation();
                    }
                }
            };
            orientationHelper.start();
       }
    }

    /**
     * Enables fixed size for the surface. This provides better performance but might be buggy on some
     * devices. By default this is turned off.
     */
    public void setEnableHardwareScaler(boolean enabled) {
        ThreadUtils.checkIsOnMainThread();
        enableFixedSize = enabled;
        updateSurfaceSize();
    }

    public void updateRotation() {
        if (orientationHelper == null || rotatedFrameWidth == 0 || rotatedFrameHeight == 0) {
            return;
        }
        View parentView = (View) getParent();
        if (parentView == null) {
            return;
        }
        int orientation = orientationHelper.getOrientation();
        float viewWidth = getMeasuredWidth();
        float viewHeight = getMeasuredHeight();
        float w;
        float h;
        float targetWidth = parentView.getMeasuredWidth();
        float targetHeight = parentView.getMeasuredHeight();
        if (orientation == 90 || orientation == 270) {
            w = viewHeight;
            h = viewWidth;
        } else {
            w = viewWidth;
            h = viewHeight;
        }
        float scale;
        if (w < h) {
            scale = Math.max(w / viewWidth, h / viewHeight);
        } else {
            scale = Math.min(w / viewWidth, h / viewHeight);
        }
        w *= scale;
        h *= scale;
        if (Math.abs(w / h - targetWidth / targetHeight) < 0.1f) {
            scale *= Math.max(targetWidth / w, targetHeight / h);
        }
        if (orientation == 270) {
            orientation = -90;
        }
        animate().scaleX(scale).scaleY(scale).rotation(-orientation).setDuration(180).start();
    }

    /**
     * Set if the video stream should be mirrored or not.
     */
    public void setMirror(final boolean mirror) {
        if (this.mirror != mirror) {
            this.mirror = mirror;
            if (rotateTextureWithScreen) {
                onRotationChanged();
            } else {
                eglRenderer.setMirror(mirror);
            }
            updateSurfaceSize();
            requestLayout();
        }
    }

    /**
     * Set how the video will fill the allowed layout area.
     */
    public void setScalingType(RendererCommon.ScalingType scalingType) {
        ThreadUtils.checkIsOnMainThread();
        videoLayoutMeasure.setScalingType(scalingType);
        requestLayout();
    }

    public void setScalingType(RendererCommon.ScalingType scalingTypeMatchOrientation,
                               RendererCommon.ScalingType scalingTypeMismatchOrientation) {
        ThreadUtils.checkIsOnMainThread();
        videoLayoutMeasure.setScalingType(scalingTypeMatchOrientation, scalingTypeMismatchOrientation);
        requestLayout();
    }

    /**
     * Limit render framerate.
     *
     * @param fps Limit render framerate to this value, or use Float.POSITIVE_INFINITY to disable fps
     *            reduction.
     */
    public void setFpsReduction(float fps) {
        eglRenderer.setFpsReduction(fps);
    }

    public void disableFpsReduction() {
        eglRenderer.disableFpsReduction();
    }

    public void pauseVideo() {
        eglRenderer.pauseVideo();
    }

    // VideoSink interface.
    @Override
    public void onFrame(VideoFrame frame) {
        eglRenderer.onFrame(frame);
    }

    // View layout interface.
    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        ThreadUtils.checkIsOnMainThread();
        if (!isCamera && rotateTextureWithScreen) {
            updateVideoSizes();
        }
        Point size;
        if (maxTextureSize > 0) {
            size = videoLayoutMeasure.measure(isCamera, MeasureSpec.makeMeasureSpec(Math.min(maxTextureSize, MeasureSpec.getSize(widthSpec)), MeasureSpec.getMode(widthSpec)), MeasureSpec.makeMeasureSpec(Math.min(maxTextureSize, MeasureSpec.getSize(heightSpec)), MeasureSpec.getMode(heightSpec)), rotatedFrameWidth, rotatedFrameHeight);
        } else {
            size = videoLayoutMeasure.measure(isCamera, widthSpec, heightSpec, rotatedFrameWidth, rotatedFrameHeight);
        }
        setMeasuredDimension(size.x, size.y);
       // logD("onMeasure(). New size: " + size.x + "x" + size.y);
        if (rotatedFrameWidth != 0 && rotatedFrameHeight != 0) {
            eglRenderer.setLayoutAspectRatio(getMeasuredWidth() / (float) getMeasuredHeight());
        }
        updateSurfaceSize();
    }

    private void updateSurfaceSize() {
        ThreadUtils.checkIsOnMainThread();
        if (enableFixedSize && rotatedFrameWidth != 0 && rotatedFrameHeight != 0 && getWidth() != 0
                && getHeight() != 0) {
            final float layoutAspectRatio = getWidth() / (float) getHeight();
            final float frameAspectRatio = rotatedFrameWidth / (float) rotatedFrameHeight;
            final int drawnFrameWidth;
            final int drawnFrameHeight;
            if (frameAspectRatio > layoutAspectRatio) {
                drawnFrameWidth = (int) (rotatedFrameHeight * layoutAspectRatio);
                drawnFrameHeight = rotatedFrameHeight;
            } else {
                drawnFrameWidth = rotatedFrameWidth;
                drawnFrameHeight = (int) (rotatedFrameHeight / layoutAspectRatio);
            }
            // Aspect ratio of the drawn frame and the view is the same.
            final int width = Math.min(getWidth(), drawnFrameWidth);
            final int height = Math.min(getHeight(), drawnFrameHeight);
            logD("updateSurfaceSize. Layout size: " + getWidth() + "x" + getHeight() + ", frame size: "
                    + rotatedFrameWidth + "x" + rotatedFrameHeight + ", requested surface size: " + width
                    + "x" + height + ", old surface size: " + surfaceWidth + "x" + surfaceHeight);
            if (width != surfaceWidth || height != surfaceHeight) {
                surfaceWidth = width;
                surfaceHeight = height;
                //getHolder().setFixedSize(width, height);
            }
        } else {
            surfaceWidth = surfaceHeight = 0;
            //getHolder().setSizeFromLayout();
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        ThreadUtils.checkIsOnMainThread();
        surfaceWidth = surfaceHeight = 0;
        updateSurfaceSize();
        eglRenderer.onSurfaceTextureAvailable(surface, width, height);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, final int width, final int height) {
        surfaceWidth = width;
        surfaceHeight = height;
        eglRenderer.onSurfaceTextureSizeChanged(surface, width, height);
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        if (parentSink instanceof VoIPService.ProxyVideoSink) {
            VoIPService.ProxyVideoSink proxyVideoSink = (VoIPService.ProxyVideoSink) parentSink;
            proxyVideoSink.removeTarget(this);
            proxyVideoSink.removeBackground(this);
        }
        eglRenderer.onSurfaceTextureDestroyed(surfaceTexture);
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
        eglRenderer.onSurfaceTextureUpdated(surfaceTexture);
    }

    private String getResourceName() {
        try {
            return getResources().getResourceEntryName(getId());
        } catch (Resources.NotFoundException e) {
            return "";
        }
    }

    /**
     * Post a task to clear the SurfaceView to a transparent uniform color.
     */
    public void clearImage() {
        eglRenderer.clearImage();
        eglRenderer.isFirstFrameRendered = false;
    }

    @Override
    public void setParentSink(VideoSink parent) {
        parentSink = parent;
    }

    @Override
    public void onFirstFrameRendered() {
        if (rendererEvents != null) {
            rendererEvents.onFirstFrameRendered();
        }
    }

    public boolean isFirstFrameRendered() {
        return eglRenderer.isFirstFrameRendered;
    }

    int textureRotation;
    @Override
    public void onFrameResolutionChanged(int videoWidth, int videoHeight, int rotation) {
        if (rendererEvents != null) {
            rendererEvents.onFrameResolutionChanged(videoWidth, videoHeight, rotation);
        }
        textureRotation = rotation;
        int rotatedWidth, rotatedHeight;

        if (rotateTextureWithScreen) {
            if (isCamera) {
                onRotationChanged();
            }
            if (useCameraRotation) {
                rotatedWidth = screenRotation == 0 ? videoHeight : videoWidth;
                rotatedHeight = screenRotation == 0 ? videoWidth : videoHeight;
            } else {
                rotatedWidth = textureRotation == 0 || textureRotation == 180 || textureRotation == -180 ? videoWidth : videoHeight;
                rotatedHeight = textureRotation == 0 || textureRotation == 180 || textureRotation == -180 ? videoHeight : videoWidth;
            }
        } else {
            if (isCamera) {
                eglRenderer.setRotation(-OrientationHelper.cameraRotation);
            }
            rotation -= OrientationHelper.cameraOrientation;
            rotatedWidth = rotation == 0 || rotation == 180 || rotation == -180 ? videoWidth : videoHeight;
            rotatedHeight = rotation == 0 || rotation == 180 || rotation == -180? videoHeight : videoWidth;
        }
        // run immediately if possible for ui thread tests
        synchronized (eglRenderer.layoutLock) {
            if (updateScreenRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(updateScreenRunnable);
            }
            postOrRun(updateScreenRunnable = () -> {
                updateScreenRunnable = null;
                this.videoWidth = videoWidth;
                this.videoHeight = videoHeight;

                rotatedFrameWidth = rotatedWidth;
                rotatedFrameHeight = rotatedHeight;

                updateSurfaceSize();
                requestLayout();
            });
        }
    }

    public void setScreenRotation(int screenRotation) {
        this.screenRotation = screenRotation;
        onRotationChanged();
        updateVideoSizes();
    }

    private void updateVideoSizes() {
        if (videoHeight != 0 && videoWidth != 0) {
            int rotatedWidth;
            int rotatedHeight;
            if (rotateTextureWithScreen) {
                if (useCameraRotation) {
                    rotatedWidth = screenRotation == 0 ? videoHeight : videoWidth;
                    rotatedHeight = screenRotation == 0 ? videoWidth : videoHeight;
                } else {
                    rotatedWidth = textureRotation == 0 || textureRotation == 180 || textureRotation == -180 ? videoWidth : videoHeight;
                    rotatedHeight = textureRotation == 0 || textureRotation == 180 || textureRotation == -180 ? videoHeight : videoWidth;
                }
            } else {
                int rotation = textureRotation;
                rotation -= OrientationHelper.cameraOrientation;
                rotatedWidth = rotation == 0 || rotation == 180 || rotation == -180 ? videoWidth : videoHeight;
                rotatedHeight = rotation == 0 || rotation == 180 || rotation == -180 ? videoHeight : videoWidth;
            }
            if (rotatedFrameWidth != rotatedWidth || rotatedFrameHeight != rotatedHeight) {
                synchronized (eglRenderer.layoutLock) {
                    if (updateScreenRunnable != null) {
                        AndroidUtilities.cancelRunOnUIThread(updateScreenRunnable);
                    }
                    postOrRun(updateScreenRunnable = () -> {
                        updateScreenRunnable = null;

                        rotatedFrameWidth = rotatedWidth;
                        rotatedFrameHeight = rotatedHeight;

                        updateSurfaceSize();
                        requestLayout();
                    });
                }
            }
        }
    }

    public void setRotateTextureWithScreen(boolean rotateTextureWithScreen) {
        if (this.rotateTextureWithScreen != rotateTextureWithScreen) {
            this.rotateTextureWithScreen = rotateTextureWithScreen;
            requestLayout();
        }
    }

    boolean useCameraRotation;

    public void setUseCameraRotation(boolean useCameraRotation) {
        if (this.useCameraRotation != useCameraRotation) {
            this.useCameraRotation = useCameraRotation;
            onRotationChanged();
            updateVideoSizes();
        }
    }
    private void onRotationChanged() {
        int rotation = useCameraRotation ? OrientationHelper.cameraOrientation : 0;
        if (mirror) {
            rotation = 360 - rotation;
        }
        int r = -rotation;
        if (useCameraRotation) {
            if (screenRotation == 1) {
                r += mirror ? 90 : -90;
            } else if (screenRotation == 3) {
                r += mirror ? 270 : -270;
            }
        }

        eglRenderer.setRotation(r);
        eglRenderer.setMirror(mirror);
    }

    @Override
    public void setRotation(float rotation) {
        super.setRotation(rotation);
    }
    @Override
    public void setRotationY(float rotation) {
        super.setRotationY(rotation);
    }
    @Override
    public void setRotationX(float rotation) {
        super.setRotationX(rotation);
    }

    private void postOrRun(Runnable r) {
        if (Thread.currentThread() == Looper.getMainLooper().getThread()) {
            r.run();
        } else {
            AndroidUtilities.runOnUIThread(r);
        }
    }

    private void logD(String string) {
        Logging.d(TAG, resourceName + ": " + string);
    }

    public void createBackgroundSurface(SurfaceTexture bluSurfaceTexturerRenderer) {
        eglRenderer.createBackgroundSurface(bluSurfaceTexturerRenderer);
    }

    public void setMaxTextureSize(int maxTextureSize) {
        this.maxTextureSize = maxTextureSize;
    }
}
