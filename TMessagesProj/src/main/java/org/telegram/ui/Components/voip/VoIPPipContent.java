package org.telegram.ui.Components.voip;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.voip.Instance;
import org.telegram.messenger.voip.VideoCapturerDevice;
import org.telegram.messenger.voip.VoIPService;
import org.telegram.ui.VoIPFragment;
import org.telegram.utils.pip.PipAction;
import org.telegram.utils.pip.PipContent;
import org.telegram.utils.pip.PipCoordinator;
import org.telegram.utils.pip.PipSession;
import org.telegram.utils.pip.PipTextureView;
import org.webrtc.RendererCommon;
import org.webrtc.TextureViewRenderer;

import java.util.List;

/**
 * One-to-one call in utils.pip. The call keeps its own UI: the call screen ({@link VoIPFragment},
 * primary) and the call bubble ({@link VoIPPiPView}, own floating window, overlay or in-app).
 * Without the overlay permission, leaving the app moves the remote video into system PiP.
 * Closing PiP never ends the call.
 */
public class VoIPPipContent implements PipContent {
    private static final int PRIORITY = 10;

    private static VoIPPipContent instance;

    private PipSession session;

    /** The call screen or the bubble appeared or disappeared, or the video state changed. */
    public static void update() {
        final VoIPService service = VoIPService.getSharedInstance();
        final boolean hasUi = VoIPFragment.getInstance() != null || VoIPPiPView.getInstance() != null;
        if (service == null || !hasUi) {
            if (instance != null) {
                final VoIPPipContent content = instance;
                instance = null;
                content.release();
            }
            return;
        }
        if (instance == null) {
            instance = new VoIPPipContent();
        }
        instance.updateState();
    }

    private void updateState() {
        if (session == null || !session.isAlive()) {
            session = PipCoordinator.getInstance().register(this);
        }
        if (session.getState() == PipSession.STATE_SYSTEM) {
            session.invalidate();
            return;
        }
        final VoIPPiPView bubble = VoIPFragment.getInstance() == null ? VoIPPiPView.getInstance() : null;
        session.setOwnFloating(bubble != null, bubble != null && bubble.isOverlay());
        session.invalidate();
    }

    private void release() {
        if (session != null) {
            final PipSession s = session;
            session = null;
            s.release();
        }
    }

    private static @Nullable TextureViewRenderer getRemoteRenderer() {
        final VoIPFragment fragment = VoIPFragment.getInstance();
        if (fragment != null && fragment.getCallingUserVideoView() instanceof VoIPTextureView) {
            return ((VoIPTextureView) fragment.getCallingUserVideoView()).renderer;
        }
        final VoIPPiPView bubble = VoIPPiPView.getInstance();
        if (bubble != null) {
            return bubble.callingUserTextureView.renderer;
        }
        return null;
    }

    /* Geometry */

    /** The system PiP view while the call is shown there. */
    private @Nullable CallSystemPipView systemView;

    /** Video proportions while the remote video is on, a square for the avatar otherwise. */
    @Override
    public int getContentWidth() {
        if (!CallSystemPipView.hasRemoteVideo(VoIPService.getSharedInstance())) {
            return 1;
        }
        final TextureViewRenderer renderer = getSizeRenderer();
        return renderer != null && renderer.rotatedFrameWidth > 0 ? renderer.rotatedFrameWidth : 9;
    }

    @Override
    public int getContentHeight() {
        if (!CallSystemPipView.hasRemoteVideo(VoIPService.getSharedInstance())) {
            return 1;
        }
        final TextureViewRenderer renderer = getSizeRenderer();
        return renderer != null && renderer.rotatedFrameHeight > 0 ? renderer.rotatedFrameHeight : 16;
    }

    /** The system view's renderer once it has frames, the call screen's before (it keeps the last size). */
    private @Nullable TextureViewRenderer getSizeRenderer() {
        if (systemView != null && systemView.textureView.renderer.rotatedFrameWidth > 0) {
            return systemView.textureView.renderer;
        }
        return getRemoteRenderer();
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    /* Policy */

    /** Any ongoing call: system PiP shows the remote video, or the avatar of an audio call. */
    @Override
    public boolean needsPipOutsideApp() {
        final VoIPService service = VoIPService.getSharedInstance();
        return service != null && !service.isHangingUp() && service.getCallState() != VoIPService.STATE_ENDED && service.getCallState() != VoIPService.STATE_HANGING_UP;
    }

    @Override
    public boolean isPrimaryVisible() {
        final VoIPFragment fragment = VoIPFragment.getInstance();
        return fragment != null && fragment.getCallingUserVideoView().isAttachedToWindow();
    }

    /** No source rect hint for calls: the call screen does not need the system's enter animation. */
    @Override
    public void getPrimaryVideoRect(@NonNull Rect outScreenRect) {
        outScreenRect.setEmpty();
    }

    /* Actions */

    @Override
    public void getSystemActions(@NonNull List<PipAction> out, int maxActions) {
        CallPipActions.build(out, maxActions, false);
    }

    @Override
    public void onSystemAction(int actionId) {
        CallPipActions.perform(actionId, false);
        AndroidUtilities.runOnUIThread(VoIPPipContent::update, 300);
    }

    /**
     * While in system PiP: video or avatar, own mic state, buttons (mic or camera changed from the
     * notification or a PiP button) and proportions (the remote video turned on or off).
     */
    private long actionsKey;
    private boolean lastRemoteVideo;
    private final Runnable actionsTick = new Runnable() {
        @Override
        public void run() {
            if (systemView != null) {
                systemView.update();
            }
            final long key = CallPipActions.stateKey();
            final boolean remoteVideo = CallSystemPipView.hasRemoteVideo(VoIPService.getSharedInstance());
            if (key != actionsKey || remoteVideo != lastRemoteVideo) {
                actionsKey = key;
                lastRemoteVideo = remoteVideo;
                if (session != null) {
                    session.invalidate();
                }
            }
            AndroidUtilities.runOnUIThread(this, 500);
        }
    };

    /* System PiP */

    @Override
    public View createSystemView(@NonNull Context context) {
        return new CallSystemPipView(context);
    }

    @Override
    public void onSystemEnter(@NonNull View systemView) {
        CallPipActions.onSystemEnter();
        this.systemView = (CallSystemPipView) systemView;
        this.systemView.update();
        actionsKey = CallPipActions.stateKey();
        lastRemoteVideo = CallSystemPipView.hasRemoteVideo(VoIPService.getSharedInstance());
        AndroidUtilities.cancelRunOnUIThread(actionsTick);
        AndroidUtilities.runOnUIThread(actionsTick, 500);
        fragmentInSystem = false;
        // the bubble goes at once (it would be drawn inside the shrinking window); the call screen
        // stays until the window is in PiP, the system shrinks it under the app icon
        if (VoIPFragment.getInstance() == null && VoIPPiPView.getInstance() != null) {
            VoIPPiPView.getInstance().enterSystemPip(this.systemView.textureView);
        }
    }

    /** The call screen handed its frames to the system view. */
    private boolean fragmentInSystem;

    @Override
    public void onSystemEntered(@NonNull View systemView) {
        final VoIPFragment fragment = VoIPFragment.getInstance();
        if (fragment != null && this.systemView == systemView && !fragmentInSystem) {
            fragmentInSystem = true;
            this.systemView.restartFrameWait();
            fragment.enterSystemPip(this.systemView.textureView);
        }
    }

    @Override
    public void onSystemExit(@NonNull View systemView) {
        CallPipActions.onSystemExit();
        AndroidUtilities.cancelRunOnUIThread(actionsTick);
        this.systemView = null;
        final VoIPFragment fragment = VoIPFragment.getInstance();
        if (fragment != null) {
            fragment.exitSystemPip();
        } else if (VoIPPiPView.getInstance() != null) {
            VoIPPiPView.getInstance().exitSystemPip();
        }
    }

    @Override
    public void releaseSystemView(@NonNull View systemView) {
        AndroidUtilities.cancelRunOnUIThread(actionsTick);
        if (this.systemView == systemView) {
            this.systemView = null;
        }
        ((CallSystemPipView) systemView).release();
    }

    @Override
    public void onDismissed() {
        // system PiP closed by the user: the call goes on, its own UI is dropped
        session = null;
        if (instance == this) {
            instance = null;
        }
        final VoIPFragment fragment = VoIPFragment.getInstance();
        if (fragment != null) {
            fragment.closeWithoutPip();
        }
        VoIPPiPView.finish();
    }

    /* Not used: the call has its own UI */

    @Override
    public boolean isPlaying() {
        return true;
    }

    @Override
    public boolean isCompleted() {
        return false;
    }

    @Override
    public void play() {}

    @Override
    public void pause() {}

    @Override
    public long getDuration() {
        return 0;
    }

    @Override
    public long getCurrentPosition() {
        return 0;
    }

    @Override
    public float getBufferedProgress() {
        return 0;
    }

    @Override
    public void seekTo(long positionMs) {}

    @Override
    public void releasePrimary(@NonNull PipTextureView target) {}

    @Override
    public void restorePrimary(@NonNull SurfaceTexture surfaceTexture) {
        surfaceTexture.release();
    }

    @Override
    public void onExpandRequested() {}
}
