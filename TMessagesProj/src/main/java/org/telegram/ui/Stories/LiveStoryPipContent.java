package org.telegram.ui.Stories;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.voip.PipEglRenderer;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.Stories.recorder.LivePlayerView;
import org.telegram.utils.pip.PipContent;
import org.telegram.utils.pip.PipCoordinator;
import org.telegram.utils.pip.PipSession;
import org.telegram.utils.pip.PipTextureView;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;

/**
 * Live story ({@link LivePlayer}) in utils.pip, like a video:
 * <ul>
 *   <li>the PiP button (or "Minimize") of the story viewer opens the host's floating window: an
 *   overlay with the permission, an in-app window without it; the viewer closes, the player
 *   goes on in the window;</li>
 *   <li>leaving the app while the live story is open goes to system PiP (without the overlay
 *   permission; with it the floating window is opened instead, as for other video);</li>
 *   <li>the floating window gets the frames into its texture ({@link PipEglRenderer}), the same
 *   texture moves into system PiP and back; system PiP from the open viewer has its own view,
 *   without a source rect hint;</li>
 *   <li>expanding opens the story viewer again, which takes the same player back.</li>
 * </ul>
 * The player is the only display sink owner: the viewer's view, or ours while in PiP.
 */
public class LiveStoryPipContent implements PipContent {
    private static @Nullable LiveStoryPipContent instance;

    private @Nullable PipSession session;
    private @Nullable LivePlayer player;
    /** The viewer that shows {@link #player} in its own UI (primary), if any. */
    private @Nullable StoryViewer viewer;

    private final FrameTap tap = new FrameTap();
    private @Nullable PipEglRenderer floatingRenderer;
    private @Nullable LivePlayerView systemView;
    /** The viewer's sink before system PiP took the frames. */
    private @Nullable VideoSink primarySink;

    /** With the overlay permission, leaving the app moves the open live story to the overlay window. */
    private final Runnable onUserLeaveHint = () -> {
        final StoryViewer v = viewer;
        if (v != null && isPrimaryVisible() && session != null && session.getState() == PipSession.STATE_PRIMARY
            && LaunchActivity.instance != null && org.telegram.utils.pip.PipEnvironment.canDrawOverlays(LaunchActivity.instance)) {
            v.switchToPip();
        }
    };
    /** The activity the leave-hint listener is registered with (it may be recreated). */
    private @Nullable LaunchActivity leaveHintActivity;

    private LiveStoryPipContent() {}

    private void setLeaveHintRegistered(boolean registered) {
        final LaunchActivity target = registered ? LaunchActivity.instance : null;
        if (leaveHintActivity == target) {
            return;
        }
        if (leaveHintActivity != null) {
            leaveHintActivity.removeOnUserLeaveHintListener(onUserLeaveHint);
        }
        leaveHintActivity = target;
        if (target != null) {
            target.addOnUserLeaveHintListener(onUserLeaveHint);
        }
    }

    private static LiveStoryPipContent get() {
        if (instance == null) {
            instance = new LiveStoryPipContent();
        }
        return instance;
    }

    /* Static API for the story viewer */

    /** The viewer shows {@code player} now: it may go to system PiP when the user leaves the app. */
    public static void onPrimary(@NonNull StoryViewer viewer, @NonNull LivePlayer player) {
        final LiveStoryPipContent content = get();
        if (content.session != null && content.session.isAlive() && content.session.getState() != PipSession.STATE_PRIMARY) {
            // another live story is in a PiP window: the viewer replaces it
            if (content.player != player) {
                content.session.dismiss();
            } else {
                return;
            }
        }
        content.viewer = viewer;
        content.player = player;
        content.ensureSession();
        content.setLeaveHintRegistered(true);
        content.session.invalidate();
    }

    /** The viewer stopped showing its live player (closed, switched to another story). */
    public static void onPrimaryGone(@NonNull StoryViewer viewer) {
        final LiveStoryPipContent content = instance;
        if (content == null || content.viewer != viewer) {
            return;
        }
        content.viewer = null;
        content.setLeaveHintRegistered(false);
        if (content.session != null && content.session.isAlive() && content.session.getState() == PipSession.STATE_PRIMARY) {
            final PipSession session = content.session;
            content.session = null;
            content.player = null;
            session.release();
        }
    }

    /** The player is shown in a PiP window (or going to system PiP): the viewer must not destroy it. */
    public static boolean keepsPlayer(@Nullable LivePlayer player) {
        final LiveStoryPipContent content = instance;
        if (player == null || content == null || content.player != player || content.session == null || !content.session.isAlive()) {
            return false;
        }
        // entering system PiP starts before the viewer is paused (the coordinator is paused first,
        // and before that onUserLeaveHint on older systems): the state is already SYSTEM then
        return content.session.getState() != PipSession.STATE_PRIMARY;
    }

    /** The volume of a live story in PiP: full, or silent while its in-app window is hidden. */
    public static float getPipVolume() {
        final LiveStoryPipContent content = instance;
        return content != null && content.pausedInBackground ? 0f : 1f;
    }

    /** Full volume in PiP (the viewer fades it while it closes). */
    public static boolean isInPip(@Nullable LivePlayer player) {
        final LiveStoryPipContent content = instance;
        return player != null && content != null && content.player == player && content.session != null
            && content.session.isAlive() && content.session.getState() != PipSession.STATE_PRIMARY;
    }

    /** The floating window shows a live story (the call bar steps aside, as with the old overlay). */
    public static boolean isFloating() {
        final LiveStoryPipContent content = instance;
        return content != null && content.session != null && content.session.isFloating();
    }

    /**
     * The viewer opens the live story {@code call}: if it is shown in the floating window, the
     * player moves back to the viewer (the window closes), otherwise null.
     */
    public static @Nullable LivePlayer takePlayer(@NonNull TLRPC.InputGroupCall call) {
        final LiveStoryPipContent content = instance;
        if (content == null || content.player == null || content.session == null || !content.session.isFloating() || !content.player.equals(call)) {
            return null;
        }
        final LivePlayer player = content.player;
        content.closeFloatingForViewer();
        return player;
    }

    /** Another viewer comes to the front: a floating live story closes (as before). */
    public static void closeFloating() {
        final LiveStoryPipContent content = instance;
        if (content != null && content.session != null && content.session.isFloating()) {
            content.session.dismiss();
        }
    }

    /**
     * The PiP button of the viewer: the floating window replaces the viewer. Returns false if it
     * cannot be shown (the viewer stays).
     */
    public static boolean openFloating(@NonNull Activity activity, @NonNull StoryViewer viewer, @NonNull LivePlayer player, @Nullable Bitmap placeholder) {
        final LiveStoryPipContent content = get();
        if (content.session != null && content.session.isAlive() && content.session.getState() != PipSession.STATE_PRIMARY) {
            if (content.player == player) {
                return true;
            }
            content.session.dismiss();
        }
        content.viewer = viewer;
        content.player = player;
        content.ensureSession();
        final PipTextureView target = content.session.startFloating(activity, placeholder, true);
        if (target == null) {
            return false;
        }
        content.floatingRenderer = new PipEglRenderer(target);
        content.tap.target = content.floatingRenderer;
        player.setDisplaySink(content.tap);
        player.setVolume(1f);
        log("floating, call " + player.getCallId());
        return true;
    }

    /* Lifecycle */

    private void ensureSession() {
        if (session == null || !session.isAlive()) {
            session = PipCoordinator.getInstance().register(this);
        }
    }

    private void closeFloatingForViewer() {
        if (session == null) {
            return;
        }
        final PipEglRenderer renderer = floatingRenderer;
        floatingRenderer = null;
        tap.target = null;
        session.completeExpand(texture -> {
            if (renderer != null) {
                renderer.release();
            }
            if (texture != null) {
                texture.release();
            }
        });
    }

    /**
     * GONE, not INVISIBLE: a hidden viewer would still be measured in the PiP window's size and
     * keep that layout (e.g. the comments' text wrapped to the PiP width) after PiP. The frames
     * go to the system view meanwhile.
     */
    private void setViewerHidden(boolean hidden) {
        final StoryViewer v = viewer;
        if (v != null && v.windowView != null) {
            v.windowView.setVisibility(hidden ? View.GONE : View.VISIBLE);
        }
    }

    /* Geometry: live stories are portrait until the frames say otherwise */

    @Override
    public int getContentWidth() {
        if (tap.frameWidth > 0) {
            return tap.frameWidth;
        }
        final org.webrtc.TextureViewRenderer renderer = getViewerRenderer();
        return renderer != null && renderer.rotatedFrameWidth > 0 ? renderer.rotatedFrameWidth : 9;
    }

    @Override
    public int getContentHeight() {
        if (tap.frameHeight > 0) {
            return tap.frameHeight;
        }
        final org.webrtc.TextureViewRenderer renderer = getViewerRenderer();
        return renderer != null && renderer.rotatedFrameHeight > 0 ? renderer.rotatedFrameHeight : 16;
    }

    /** The viewer's renderer knows the stream's size before the frames come to us. */
    private @Nullable org.webrtc.TextureViewRenderer getViewerRenderer() {
        final StoryViewer v = viewer;
        return v != null && v.liveView != null ? v.liveView.textureView : null;
    }

    /* Playback: live, no controls */

    @Override
    public boolean hasPlaybackControls() {
        return false;
    }

    @Override
    public boolean resumesWithApp() {
        return true;
    }

    /** The in-app window is hidden with the app (no permissions): the live story is silent. */
    private boolean pausedInBackground;

    @Override
    public boolean isPlaying() {
        return !pausedInBackground;
    }

    @Override
    public boolean isCompleted() {
        return false;
    }

    @Override
    public void play() {
        if (pausedInBackground) {
            pausedInBackground = false;
            if (player != null) {
                player.setVolume(1f);
            }
        }
    }

    @Override
    public void pause() {
        if (!pausedInBackground) {
            pausedInBackground = true;
            if (player != null) {
                player.setVolume(0f);
            }
        }
    }

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

    /* Policy */

    @Override
    public boolean needsPipOutsideApp() {
        return player != null && !player.destroyed;
    }

    @Override
    public boolean isPrimaryVisible() {
        final StoryViewer v = viewer;
        return v != null && v.isShown() && player != null && v.livePlayer == player;
    }

    /** No hint, as for streams and calls: the system shows the app icon while the window shrinks. */
    @Override
    public void getPrimaryVideoRect(@NonNull Rect outScreenRect) {
        outScreenRect.setEmpty();
    }

    /* Primary ↔ floating */

    /** Not used: system PiP from the viewer has its own view ({@link #createSystemView}). */
    @Override
    public void releasePrimary(@NonNull PipTextureView target) {
        if (player == null) {
            return;
        }
        floatingRenderer = new PipEglRenderer(target);
        tap.target = floatingRenderer;
        primarySink = player.getDisplaySink();
        player.setDisplaySink(tap);
    }

    @Override
    public void restorePrimary(@NonNull SurfaceTexture surfaceTexture) {
        final PipEglRenderer renderer = floatingRenderer;
        floatingRenderer = null;
        tap.target = null;
        if (player != null && primarySink != null) {
            player.setDisplaySink(primarySink);
        }
        primarySink = null;
        if (renderer != null) {
            renderer.release();
        }
        surfaceTexture.release();
    }

    /** Opens the story viewer again; it takes the player back ({@link #takePlayer}). */
    @Override
    public void onExpandRequested() {
        final LivePlayer p = player;
        if (p == null || p.destroyed) {
            if (session != null) {
                session.dismiss();
            }
            return;
        }
        if (p.currentAccount != UserConfig.selectedAccount && LaunchActivity.instance != null) {
            LaunchActivity.instance.switchToAccount(p.currentAccount, true);
        }
        final BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        TL_stories.StoryItem storyItem = MessagesController.getInstance(p.currentAccount).getStoriesController().findStory(p.dialogId, p.storyId);
        if (storyItem == null) {
            storyItem = p.storyItem;
        }
        if (fragment == null || storyItem == null || fragment.getContext() == null) {
            if (session != null) {
                session.dismiss();
            }
            return;
        }
        // the viewer takes the player back when it shows this live story (takePlayer)
        fragment.getOrCreateStoryViewer().open(p.currentAccount, fragment.getContext(), storyItem, null);
    }

    @Override
    public void onDismissed() {
        pausedInBackground = false;
        final LivePlayer p = player;
        final StoryViewer v = viewer;
        final boolean fromViewer = systemView != null || primarySink != null;
        session = null;
        player = null;
        tap.target = null;
        if (floatingRenderer != null) {
            floatingRenderer.release();
            floatingRenderer = null;
        }
        if (fromViewer && v != null) {
            // system PiP of the open viewer was closed: close the viewer, it releases the player
            setViewerHidden(false);
            if (p != null && primarySink != null) {
                p.setDisplaySink(primarySink);
            }
            primarySink = null;
            viewer = null;
            v.close(false);
            return;
        }
        viewer = null;
        // the window owned the player (the viewer gave it away): release it
        if (p != null && !p.destroyed && (v == null || v.livePlayer != p)) {
            if (p.outgoing || p == LivePlayer.recording) {
                p.setDisplaySink(null);
            } else {
                p.destroy();
            }
        }
    }

    /* System PiP from the open viewer */

    @Override
    public View createSystemView(@NonNull Context context) {
        return new LivePlayerView(context, player != null ? player.currentAccount : UserConfig.selectedAccount, false);
    }

    @Override
    public void onSystemEnter(@NonNull View view) {
        systemView = (LivePlayerView) view;
    }

    @Override
    public void onSystemEntered(@NonNull View view) {
        if (player == null || view != systemView) {
            return;
        }
        primarySink = player.getDisplaySink();
        tap.target = systemView.getSink();
        player.setDisplaySink(tap);
        player.setVolume(1f);
        // a viewer in its own window would be drawn inside the PiP window
        setViewerHidden(true);
    }

    @Override
    public void onSystemExit(@NonNull View view) {
        setViewerHidden(false);
        if (view == systemView) {
            tap.target = null;
            if (player != null && primarySink != null) {
                player.setDisplaySink(primarySink);
            }
            primarySink = null;
            systemView = null;
        }
    }

    @Override
    public void releaseSystemView(@NonNull View view) {
        if (view == systemView) {
            tap.target = null;
            systemView = null;
        }
        ((LivePlayerView) view).release();
    }

    private static void log(String message) {
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("live story pip: " + message);
        }
    }

    /** The player's display sink while in PiP: forwards the frames, remembers their size. */
    private final class FrameTap implements VideoSink {
        volatile @Nullable VideoSink target;
        volatile int frameWidth, frameHeight;

        private final Runnable onSizeChanged = () -> {
            if (session != null) {
                session.invalidate();
            }
        };

        @Override
        public void onFrame(VideoFrame frame) {
            final int w = frame.getRotatedWidth(), h = frame.getRotatedHeight();
            if (w != frameWidth || h != frameHeight) {
                frameWidth = w;
                frameHeight = h;
                AndroidUtilities.runOnUIThread(onSizeChanged);
            }
            final VideoSink t = target;
            if (t != null) {
                t.onFrame(frame);
            }
        }
    }
}
