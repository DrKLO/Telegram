package org.telegram.messenger.pip;

import android.app.Activity;
import android.app.PictureInPictureParams;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build;
import android.util.Log;
import android.util.Rational;
import android.view.View;

import androidx.annotation.RequiresApi;

import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.video.VideoSize;

import org.webrtc.TextureViewRenderer;

public class PipSource {
    static final String TAG = "PIP_SOURCE";

    private static int sourceIdCounter = 0;

    public final int sourceId = sourceIdCounter++;
    public final String tag;
    public final int priority;
    public final boolean needMediaSession;

    private final View.OnLayoutChangeListener onLayoutChangeListener = this::onLayoutChange;
    private final PictureInPictureContentViewProvider listener;
    private final Rect position = new Rect();
    final Point ratio = new Point();

    private boolean isEnabled = true;
    private View contentView;
    Activity activity;
    Player player;

    private PipSource(PipSource.Builder builder) {
        this.tag = (builder.tagPrefix != null ? builder.tagPrefix : "pip-source") + "-" + sourceId;
        this.listener = builder.listener;
        this.activity = builder.activity;
        this.priority = builder.priority;
        this.needMediaSession = builder.needMediaSession;

        this.ratio.set(builder.width, builder.height);
        this.player = builder.player;
        setContentView(builder.contentView);

        PipNativeApiController.register(this);
    }

    public void setEnabled(boolean enabled) {
        isEnabled = enabled;
        PipNativeApiController.onUpdateSourcesMap();
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    public void destroy() {
        detachFromPictureInPicture();

        PipNativeApiController.unregister(this);
        setContentView(null);
        activity = null;
    }

    public void setContentView(View contentView) {
        if (this.contentView != null) {
            this.contentView.removeOnLayoutChangeListener(onLayoutChangeListener);
        }

        this.contentView = contentView;
        if (this.contentView != null) {
            this.contentView.addOnLayoutChangeListener(onLayoutChangeListener);
            updateContentPosition(this.contentView);
        }
    }

    public void setContentRatio(int width, int height) {
        final boolean changed = (ratio.x != width || ratio.y != height);
        ratio.set(width, height);
        if (player != null && changed) {
            PipNativeApiController.onUpdateSourcesMap();
        }
        if (PipNativeApiController.isMaxPrioritySource(tag) && changed) {
            applyPictureInPictureParams();
        }
    }

    public void setPlayer(Player player) {
        this.player = player;
        PipNativeApiController.onUpdateSourcesMap();
        if (PipNativeApiController.isMaxPrioritySource(tag)) {
            if (PipNativeApiController.mediaSessionConnector != null) {
                PipNativeApiController.mediaSessionConnector.setPlayer(player);
            }
        }
    }

    /* */

    private static final int[] tmpCords = new int[2];
    private void updateContentPosition(View v) {
        int x, y;
        v.getLocationOnScreen(tmpCords);
        x = tmpCords[0];
        y = tmpCords[1];

        if (activity != null) {
            activity.getWindow().getDecorView().getLocationOnScreen(tmpCords);
            x -= tmpCords[0];
            y -= tmpCords[1];
        }

        final int l = x, t = y, r = x + v.getWidth(), b = y + v.getHeight();
        boolean changed = position.left != l
            || position.top != t
            || position.right != r
            || position.bottom != b;
        position.set(l, t, r, b);

        if (v instanceof TextureViewRenderer) {
            final int width = ((TextureViewRenderer) v).rotatedFrameWidth;
            final int height = ((TextureViewRenderer) v).rotatedFrameHeight;
            changed |= (ratio.x != width || ratio.y != height);
            ratio.set(width, height);
            if (player != null && changed) {
                PipNativeApiController.onUpdateSourcesMap();
            }
        }

        if (PipNativeApiController.isMaxPrioritySource(tag) && changed) {
            applyPictureInPictureParams();
        }
    }

    private void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
        updateContentPosition(v);
    }

    void applyPictureInPictureParams() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !activity.isInPictureInPictureMode()) {
            // Log.i(TAG, "[UPDATE] setPictureInPictureParams " + tag);
            activity.setPictureInPictureParams(buildPictureInPictureParams());
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    PictureInPictureParams buildPictureInPictureParams() {
        PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder();
        if (ratio.x > 0 && ratio.y > 0) {
            final Rational r;
            final float rat = (float) ratio.x / ratio.y;
            if (rat < 0.45) {
                r = new Rational(45, 100);
            } else if (rat > 2.35) {
                r = new Rational(235, 100);
            } else {
                r = new Rational(ratio.x, ratio.y);
            }
            builder.setAspectRatio(r);
        } else {
            builder.setAspectRatio(null);
        }

        if (position.width() > 0 && position.height() > 0) {
            builder.setSourceRectHint(position);
        } else {
            builder.setSourceRectHint(null);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setSeamlessResizeEnabled(true);
            builder.setAutoEnterEnabled(true);
        }
        return builder.build();
    }

    /* */

    private View attachedToPictureInPictureView;

    public boolean isAttachedToPictureInPicture() {
        return attachedToPictureInPictureView != null;
    }

    void attachToPictureInPicture() {
        detachFromPictureInPicture();

        if (activity instanceof PictureInPictureActivityHandler) {
            attachedToPictureInPictureView = listener.detachContentFromWindow();
            ((PictureInPictureActivityHandler) activity).addActivityPipView(attachedToPictureInPictureView);
            listener.onAttachContentToPip();

            // Log.i(TAG, "[LIFECYCLE] pip attach " + tag);
        }
    }

    void detachFromPictureInPicture() {
        if (attachedToPictureInPictureView == null) {
            return;
        }

        if (activity instanceof PictureInPictureActivityHandler) {
            listener.prepareDetachContentFromPip();
            ((PictureInPictureActivityHandler) activity).removeActivityPipView(attachedToPictureInPictureView);
            listener.attachContentToWindow();

            attachedToPictureInPictureView = null;

            // Log.i(TAG, "[LIFECYCLE] pip detach " + tag);
        }
    }

    public static class Builder {
        private final PictureInPictureContentViewProvider listener;
        private final Activity activity;

        private String tagPrefix;
        private int priority = 0;
        private boolean needMediaSession = false;
        private Player player;
        private int width, height;
        private View contentView;

        public Builder(Activity activity, PictureInPictureContentViewProvider listener) {
            this.activity = activity;
            this.listener = listener;
        }

        public Builder setTagPrefix(String tagPrefix) {
            this.tagPrefix = tagPrefix;
            return this;
        }

        public Builder setPriority(int priority) {
            this.priority = priority;
            return this;
        }

        public Builder setNeedMediaSession(boolean needMediaSession) {
            this.needMediaSession = needMediaSession;
            return this;
        }

        public Builder setContentView(View contentView) {
            this.contentView = contentView;
            return this;
        }

        public Builder setContentRatio(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }

        public Builder setPlayer(Player player) {
            this.player = player;
            return this;
        }

        public PipSource build() {
            return new PipSource(this);
        }
    }
}
