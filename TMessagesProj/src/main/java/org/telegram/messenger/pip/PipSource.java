package org.telegram.messenger.pip;

import android.app.Activity;
import android.app.PictureInPictureParams;
import android.app.RemoteAction;
import android.graphics.Rect;
import android.os.Build;
import android.view.View;

import androidx.annotation.RequiresApi;

import com.google.android.exoplayer2.Player;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.pip.activity.IPipActivity;
import org.telegram.messenger.pip.activity.IPipActivityActionListener;
import org.telegram.messenger.pip.source.IPipSourceDelegate;
import org.telegram.messenger.pip.source.PipSourceHandlerState2;
import org.telegram.messenger.pip.utils.PipPositionObserver;
import org.telegram.messenger.pip.utils.PipSourceParams;
import org.telegram.messenger.pip.utils.PipUtils;
import org.telegram.ui.Stories.LiveStoryPipOverlay;
import org.webrtc.TextureViewRenderer;

import java.util.ArrayList;

public class PipSource {
    private static int sourceIdCounter = 0;
    public final int sourceId = sourceIdCounter++;


    public final PipActivityController controller;
    public final PipSourceHandlerState2 state2;

    public final String tag;
    public final int priority;
    public final int cornerRadius;
    public final boolean needMediaSession;

    public final IPipActivityActionListener actionListener;
    private ArrayList<RemoteAction> remoteActions;

    public final IPipSourceDelegate delegate;
    public final PipSourceParams params = new PipSourceParams();

    private final PipPositionObserver pipPositionObserver = new PipPositionObserver(this::invalidatePosition);

    public View contentView;
    public View placeholderView;
    Player player;

    private PipSource(PipActivityController controller, PipSource.Builder builder) {
        this.tag = (builder.tagPrefix != null ? builder.tagPrefix : "pip-source") + "-" + sourceId;

        this.delegate = builder.delegate;
        this.actionListener = builder.actionListener;
        this.priority = builder.priority;
        this.cornerRadius = builder.cornerRadius;
        this.needMediaSession = builder.needMediaSession;
        this.controller = controller;
        this.params.setRatio(builder.width, builder.height);
        this.player = builder.player;
        this.placeholderView = builder.placeholderView;

        this.state2 = new PipSourceHandlerState2(this);

        setContentView(builder.contentView);

        checkAvailable(false);
        invalidateActions();
        controller.dispatchSourceRegister(this);
    }

    public void destroy() {
        pipPositionObserver.stop();
        controller.dispatchSourceUnregister(this);
    }

    public void setContentView(View contentView) {
        pipPositionObserver.start(contentView);

        this.contentView = contentView;
        if (this.contentView != null) {
            updateContentPosition(this.contentView);
        }
    }

    public void setPlaceholderView(View placeholderView) {
        this.placeholderView = placeholderView;
    }

    public void setContentRatio(int width, int height) {
        if (params.setRatio(width, height)) {
            checkAvailable(true);
            controller.dispatchSourceParamsChanged(this);
        }
    }

    public void setPlayer(Player player) {
        this.player = player;
        checkAvailable(true);
        controller.dispatchSourceParamsChanged(this);
    }

    /* */

    private static final Rect tmpRect = new Rect();

    private void updateContentPosition(View v) {
        if (AndroidUtilities.isInPictureInPictureMode(controller.activity)) {
            return;
        }

        PipUtils.getPipSourceRectHintPosition(controller.activity, v, tmpRect);
        boolean changed = params.setPosition(tmpRect);

        if (v instanceof TextureViewRenderer) {
            final int width = ((TextureViewRenderer) v).rotatedFrameWidth;
            final int height = ((TextureViewRenderer) v).rotatedFrameHeight;
            changed |= params.setRatio(width, height);
        } else if (v.getWidth() != 0 && v.getHeight() != 0) {
            final int width = v.getWidth();
            final int height = v.getHeight();
            changed |= params.setRatio(width, height);
        }

        if (changed) {
            checkAvailable(true);
            controller.dispatchSourceParamsChanged(this);
        }
    }

    public void invalidatePosition() {
        if (contentView != null) {
            updateContentPosition(contentView);
        }
    }

    public void invalidateActions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && actionListener != null) {
            remoteActions = new ArrayList<>();
            delegate.pipCreateActionsList(remoteActions, tag, controller.activity.getMaxNumPictureInPictureActions());
            controller.dispatchSourceParamsChanged(this);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public PictureInPictureParams buildPictureInPictureParams() {
        PictureInPictureParams.Builder builder = params.build();
        builder.setActions(remoteActions);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(PipUtils.useAutoEnterInPictureInPictureMode());
            //builder.setSeamlessResizeEnabled(true);
        }
        return builder.build();
    }

    /* */

    private boolean isAvailable;
    private void checkAvailable(boolean notify) {
        boolean isAvailable = params.isValid() && delegate.pipIsAvailable();
        if (this.isAvailable != isAvailable) {
            this.isAvailable = isAvailable;
            if (notify) {
                controller.dispatchSourceAvailabilityChanged(this);
            }
        }
    }

    public void invalidateAvailability() {
        checkAvailable(true);
    }

    public boolean isAvailable() {
        return isAvailable;
    }

    public static class Builder {
        private final Activity activity;
        private final IPipSourceDelegate delegate;

        private IPipActivityActionListener actionListener;
        private String tagPrefix;
        private int cornerRadius;
        private int priority = 0;
        private boolean needMediaSession = false;
        private Player player;
        private int width, height;
        private View contentView;
        private View contentLocationView;
        private View placeholderView;

        public Builder(Activity activity, IPipSourceDelegate delegate) {
            this.activity = activity;
            this.delegate = delegate;
        }

        public Builder setTagPrefix(String tagPrefix) {
            this.tagPrefix = tagPrefix;
            return this;
        }

        public Builder setPriority(int priority) {
            this.priority = priority;
            return this;
        }

        public Builder setActionListener(IPipActivityActionListener actionListener) {
            this.actionListener = actionListener;
            return this;
        }

        public Builder setPlaceholderView(View placeholderView) {
            this.placeholderView = placeholderView;
            return this;
        }

        public Builder setCornerRadius(int cornerRadius) {
            this.cornerRadius = cornerRadius;
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

        public Builder setContentLocationView(View contentLocationView) {
            this.contentLocationView = contentLocationView;
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
            if (activity instanceof IPipActivity) {
                PipActivityController controller = ((IPipActivity) activity).getPipController();
                return new PipSource(controller, this);
            }

            return null;
        }
    }
}
