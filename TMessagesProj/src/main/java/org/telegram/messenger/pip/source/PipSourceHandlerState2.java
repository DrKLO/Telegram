package org.telegram.messenger.pip.source;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.Log;
import android.view.View;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.pip.PipSource;
import org.telegram.messenger.pip.PipSourceContentView;
import org.telegram.messenger.pip.activity.IPipActivityAnimationListener;
import org.telegram.messenger.pip.activity.IPipActivityListener;
import org.telegram.messenger.pip.utils.PipUtils;
import org.telegram.messenger.pip.utils.Trigger;
import org.telegram.ui.ActionBar.Theme;

public class PipSourceHandlerState2 implements IPipActivityListener, IPipActivityAnimationListener {

    /**
     * STATE_DETACHED
     * PhotoViewer is visible, default state
     */

    public static final int STATE_DETACHED = 0;


    /**
     * STATE_PRE_ATTACHED
     * PhotoViewer is still visible
     * Waits for the next render activity to ensure
     * that the placeholder bitmap is guaranteed to be rendered
     */

    public static final int STATE_PRE_ATTACHED = 1;


    /**
     * STATE_ATTACHED
     * PhotoViewer is hidden, PipContentView is ready
     */

    public static final int STATE_ATTACHED = 2;


    /**
     * STATE_PRE_DETACHED_1
     * PhotoViewer is still hidden
     * Waits for the next render activity to ensure
     * that the placeholder bitmap is guaranteed to be rendered
     */

    public static final int STATE_PRE_DETACHED_1 = 3;

    /**
     * STATE_PRE_DETACHED_2
     * PhotoViewer starts to show, waiting for the first render
     */

    public static final int STATE_PRE_DETACHED_2 = 4;



    private int state = STATE_DETACHED;



    final public Rect positionSource = new Rect();
    public final Rect position = new Rect();

    private PipSourceSnapshot contentBackground;
    private PipSourceSnapshot contentForeground;

    private PipSourceContentView pictureInPictureWrapperView;
    public View pictureInPicturePlaceholderView;

    private PipSourcePlaceholder pipSourcePlaceholder;

    public View pictureInPictureView;

    private final PipSource source;

    public PipSourceHandlerState2(PipSource source) {
        this.source = source;
    }



    private void performPreAttach() {
        if (state != STATE_DETACHED) {
            FileLog.e("[" + PipUtils.TAG + "] wrong pip state STATE_DETACHED: " + state);
            /*for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
                FileLog.e("[" + PipUtils.TAG + "]" + element.toString());
            }*/
            return;
        }

        source.params.getPosition(positionSource);

        Log.i("PIP_DEBUG", "[HANDLER] pre attach start " + positionSource);

        final int width = source.controller.activity.getWindow().getDecorView().getMeasuredWidth();
        final int height = source.controller.activity.getWindow().getDecorView().getMeasuredHeight();

        Bitmap bitmap = source.delegate.pipCreatePrimaryWindowViewBitmap();

        contentBackground = new PipSourceSnapshot(width, height, source.delegate::pipRenderBackground);
        contentForeground = new PipSourceSnapshot(width, height, source.delegate::pipRenderForeground);

        pictureInPictureView = source.delegate.pipCreatePictureInPictureView();
        pictureInPicturePlaceholderView = new View(source.controller.activity);
        pictureInPictureWrapperView = new PipSourceContentView(source.controller.activity, this);
        pictureInPictureWrapperView.addView(pictureInPicturePlaceholderView);
        pictureInPictureWrapperView.addView(pictureInPictureView);

        pipSourcePlaceholder = new PipSourcePlaceholder(pictureInPicturePlaceholderView, source.placeholderView);
        pipSourcePlaceholder.setPlaceholder(bitmap);

        source.controller.getPipContentView()
            .addView(pictureInPictureWrapperView);

        state = STATE_PRE_ATTACHED;

        // wait render activity placeholder
        pictureInPictureWrapperView.invalidate();
        AndroidUtilities.doOnPreDraw(pictureInPictureView, Trigger.run(t ->
                AndroidUtilities.runOnUIThread(this::performAttach), 300));

        Log.i("PIP_DEBUG", "[HANDLER] pre attach end");
    }

    private void performAttach() {
        if (state != STATE_PRE_ATTACHED) {
            FileLog.e("[" + PipUtils.TAG + "] wrong pip state STATE_PRE_ATTACHED: " + state);
            return;
        }

        Log.i("PIP_DEBUG", "[HANDLER] attach");

        pipSourcePlaceholder.stopPlaceholderForSource();
        source.delegate.pipHidePrimaryWindowView(Trigger.run(timeout -> {
            pipSourcePlaceholder.stopPlaceholderForActivity();
            Log.i("PIP_DEBUG", "[HANDLER] on new source render first frame " + timeout);
        }, 400));

        state = STATE_ATTACHED;
        if (!shouldBeAttached) {
            performPreDetach1();
        }
    }

    private void performPreDetach1() {
        if (state != STATE_ATTACHED) {
            FileLog.e("[" + PipUtils.TAG + "] wrong pip state STATE_ATTACHED: " + state);
            /*for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
                FileLog.e("[" + PipUtils.TAG + "]" + element.toString());
            }*/
            return;
        }

        pipSourcePlaceholder.setPlaceholder(source.delegate.pipCreatePictureInPictureViewBitmap());
        state = STATE_PRE_DETACHED_1;

        pictureInPictureWrapperView.removeView(pictureInPictureView);
        pictureInPictureWrapperView.invalidate();
        pictureInPictureView = null;

        // wait render activity placeholder
        AndroidUtilities.doOnPreDraw(pictureInPictureWrapperView, Trigger.run(t ->
            AndroidUtilities.runOnUIThread(this::performPreDetach2), 300));

        Log.i("PIP_DEBUG", "[HANDLER] pre detach 1");
    }

    private void performPreDetach2() {
        if (state != STATE_PRE_DETACHED_1) {
            FileLog.e("[" + PipUtils.TAG + "] wrong pip state STATE_PRE_DETACHED_1: " + state);
            return;
        }

        source.delegate.pipShowPrimaryWindowView(Trigger.run(timeout -> {
            Log.i("PIP_DEBUG", "[HANDLER] on old source render first frame " + timeout);
            AndroidUtilities.runOnUIThread(pipSourcePlaceholder::stopPlaceholderForSource);
        }, 400));
        pictureInPictureWrapperView.invalidate();
        state = STATE_PRE_DETACHED_2;

        // wait first render window
        AndroidUtilities.doOnPreDraw(source.contentView, Trigger.run(t ->
            AndroidUtilities.runOnUIThread(this::performDetach), 300));

        Log.i("PIP_DEBUG", "[HANDLER] pre detach 2");
    }

    private void performDetach() {
        if (state != STATE_PRE_DETACHED_2) {
            FileLog.e("[" + PipUtils.TAG + "] wrong pip state STATE_PRE_DETACHED_2: " + state);
            return;
        }

        source.controller.getPipContentView()
            .removeView(pictureInPictureWrapperView);

        pictureInPictureView = null;
        pictureInPictureWrapperView = null;
        pictureInPicturePlaceholderView = null;

        if (contentForeground != null) {
            contentForeground.release();
            contentForeground = null;
        }
        if (contentBackground != null) {
            contentBackground.release();
            contentBackground = null;
        }

        pipSourcePlaceholder.stopPlaceholderForActivity();
        state = STATE_DETACHED;

        Log.i("PIP_DEBUG", "[HANDLER] detach");
        if (shouldBeAttached) {
            performPreAttach();
        }
    }





    public void updatePositionViewRect(int width, int height, boolean isInPipMode) {
        if (isInPipMode) {
            position.set(0, 0, width, height);
        } else {
            position.set(positionSource);
        }
    }

    private float lastRadius;
    private final RectF rect = new RectF();
    private final Path path = new Path();

    private void rebuildPath(float radius) {
        if (lastRadius == radius) {
            return;
        }

        lastRadius = radius;
        rect.set(position);

        path.reset();
        path.addRoundRect(rect, radius, radius, Path.Direction.CW);
        path.close();
    }

    public void draw(Canvas canvas, Utilities.Callback<Canvas> content) {
        final float radius = source.cornerRadius * (1f - lastProgress);
        final boolean needClipCorners = radius > 1f;

        drawBackground(canvas);

        if (needClipCorners) {
            rebuildPath(radius);
            canvas.save();
            canvas.clipPath(path);
        }

        content.run(canvas);
        drawForeground(canvas);

        if (needClipCorners) {
            canvas.restore();
        }
    }

    private void drawBackground(Canvas canvas) {
        final int color = Theme.getColor(Theme.key_windowBackgroundWhite);
        canvas.drawColor(ColorUtils.setAlphaComponent(color, (int) (Math.min(lastProgress * 420, 255))));
        contentBackground.draw(canvas, 1f);
    }

    private void drawForeground(Canvas canvas) {
        contentForeground.draw(canvas, 1f - lastProgress);
    }


    private boolean shouldBeAttached;
    private float lastProgress;

    public boolean isAttachedToPip() {
        return state != STATE_DETACHED;
    }

    @Override
    public void onStartEnterToPip() {
        shouldBeAttached = true;
        performPreAttach();
    }

    @Override
    public void onCompleteExitFromPip(boolean byActivityStop) {
        shouldBeAttached = false;
        performPreDetach1();
    }

    @Override
    public void onTransitionAnimationFrame() {
        if (pictureInPictureWrapperView != null) {
            pictureInPictureWrapperView.invalidate();
        }
    }

    @Override
    public void onTransitionAnimationProgress(float estimatedProgress) {
        lastProgress = estimatedProgress;
        if (pictureInPictureWrapperView != null) {
            pictureInPictureWrapperView.invalidate();
        }
    }

    public void onReceiveMaxPriority() {
        source.controller.addPipListener(this);
        source.controller.addAnimationListener(this);
        source.controller.addActionListener(source.tag, source.actionListener);
    }

    public void onLoseMaxPriority() {
        shouldBeAttached = false;
        performPreDetach1();

        source.controller.removePipListener(this);
        source.controller.removeAnimationListener(this);
        source.controller.removeActionListener(source.tag, source.actionListener);
    }
}
