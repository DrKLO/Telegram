package org.telegram.ui.Components.voip;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.voip.VideoCapturerDevice;
import org.telegram.messenger.voip.VoIPService;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BlobDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.GroupCallPip;
import org.telegram.ui.GroupCallActivity;
import org.telegram.utils.pip.MosaicLayout;
import org.telegram.utils.pip.PipAction;
import org.telegram.utils.pip.PipContent;
import org.telegram.utils.pip.PipCoordinator;
import org.telegram.utils.pip.PipSession;
import org.telegram.utils.pip.PipTextureView;
import org.webrtc.RendererCommon;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

/**
 * Group call in utils.pip: a simplified system PiP fallback when there is no overlay permission.
 * With the overlay permission the call keeps {@link org.telegram.ui.Components.GroupCallPip}
 * as is, and this content never asks for system PiP. Closing the window never leaves the call.
 *
 * <p>System PiP shows a mosaic of all remote videos ({@link MosaicLayout}: roughly equal areas,
 * little cropping); the window takes the mosaic's aspect. The mosaic is rebuilt, with an
 * animation, only when the set of videos changes or a video changes its proportions, never
 * because the participants were reordered. A tile whose frames stopped shows the avatar of its
 * participant with voice waves; with no videos at all a single tile shows the current (or the
 * last) speaker, or the chat. Actions: microphone and leave.
 */
public class GroupCallPipContent implements PipContent {
    private static final int PRIORITY = 10;
    /** The range SystemHost passes to the system. */
    private static final float MIN_ASPECT = 0.45f, MAX_ASPECT = 2.35f;
    private static final String AVATAR_KEY = "avatar";
    /** Cropping a shared screen loses text: twice as bad as cropping a camera. */
    private static final float PRESENTATION_CROP_WEIGHT = 2f;

    private static GroupCallPipContent instance;

    private PipSession session;

    private @Nullable MosaicView mosaic;
    /** Window aspect before entering system PiP: the mosaic the current videos would get. */
    private float plannedAspect = 1f;
    private @Nullable String plannedSignature;

    /** Call state, its UI or the participants changed. */
    public static void update() {
        RtmpStreamPipContent.update();
        final VoIPService service = VoIPService.getSharedInstance();
        final boolean active = service != null && service.groupCall != null && !service.isHangingUp();
        if (!active) {
            if (instance != null) {
                final GroupCallPipContent content = instance;
                instance = null;
                content.release();
            }
            return;
        }
        if (instance == null) {
            instance = new GroupCallPipContent();
        }
        if (instance.session == null || !instance.session.isAlive()) {
            instance.session = PipCoordinator.getInstance().register(instance);
            onBubbleChanged();
        }
        instance.planAspect();
        instance.session.invalidate();
    }

    /**
     * The call bubble ({@link GroupCallPip}) appeared or disappeared: it is the call's own floating
     * window, so an in-app bubble wins system PiP over a lower-priority floating video.
     */
    public static void onBubbleChanged() {
        if (instance == null || instance.session == null || !instance.session.isAlive()) {
            return;
        }
        final GroupCallPip bubble = GroupCallPip.getInstance();
        instance.session.setOwnFloating(bubble != null, bubble != null && bubble.isOverlay());
    }

        /** The call is shown in system PiP right now (an in-app bubble must not be shown then). */
    public static boolean isInSystemPip() {
        return instance != null && instance.mosaic != null;
    }

    private void release() {
        if (session != null) {
            final PipSession s = session;
            session = null;
            s.release();
        }
    }

    private static @Nullable ChatObject.Call getCall() {
        final VoIPService service = VoIPService.getSharedInstance();
        return service != null ? service.groupCall : null;
    }

    /**
     * Remote videos in the order new tiles are added: the one opened full screen in the sheet,
     * then the call's order. Own video and the "video unavailable" stub are skipped; a paused
     * stream stays (its tile shows the avatar until frames come back).
     */
    private static ArrayList<ChatObject.VideoParticipant> collectVideos(@NonNull ChatObject.Call call, long selfId) {
        final ArrayList<ChatObject.VideoParticipant> result = new ArrayList<>();
        final GroupCallActivity sheet = GroupCallActivity.groupCallInstance;
        final ChatObject.VideoParticipant pinned = sheet != null ? sheet.getFullscreenVideoParticipant() : null;
        if (pinned != null && isVideoPresent(call, pinned, selfId)) {
            result.add(pinned);
        }
        for (int i = 0; i < call.visibleVideoParticipants.size(); i++) {
            final ChatObject.VideoParticipant video = call.visibleVideoParticipants.get(i);
            if (video != pinned && isVideoPresent(call, video, selfId)) {
                result.add(video);
            }
        }
        return result;
    }

    private static boolean isVideoPresent(@NonNull ChatObject.Call call, @NonNull ChatObject.VideoParticipant video, long selfId) {
        if (video == call.videoNotAvailableParticipant || video.participant == null || video.participant.self) {
            return false;
        }
        final long peerId = MessageObject.getPeerId(video.participant.peer);
        if (peerId == selfId || call.participants.get(peerId) == null) {
            return false;
        }
        final TLRPC.TL_groupCallParticipantVideo stream = video.presentation ? video.participant.presentation : video.participant.video;
        return stream != null && keyOf(video) != null;
    }

    /** A stream is identified by its endpoint: VideoParticipant objects are reused. */
    private static @Nullable String keyOf(@NonNull ChatObject.VideoParticipant video) {
        return video.presentation ? video.participant.presentationEndpoint : video.participant.videoEndpoint;
    }

    /** The most recent speaker other than us, or 0. */
    private static long pickSpeaker(@NonNull ChatObject.Call call, long selfId) {
        long best = 0;
        long bestTime = 0;
        for (int i = 0; i < call.currentSpeakingPeers.size(); i++) {
            final TLRPC.GroupCallParticipant participant = call.currentSpeakingPeers.valueAt(i);
            final long peerId = call.currentSpeakingPeers.keyAt(i);
            if (participant == null || peerId == selfId) {
                continue;
            }
            if (best == 0 || participant.lastSpeakTime > bestTime) {
                best = peerId;
                bestTime = participant.lastSpeakTime;
            }
        }
        return best;
    }

    private void planAspect() {
        if (mosaic != null) {
            return;
        }
        final VoIPService service = VoIPService.getSharedInstance();
        final ChatObject.Call call = getCall();
        if (service == null || call == null) {
            return;
        }
        final ArrayList<ChatObject.VideoParticipant> videos = collectVideos(call, service.getSelfId());
        final float[] aspects = new float[Math.max(1, videos.size())];
        final float[] weights = new float[aspects.length];
        java.util.Arrays.fill(weights, 1f);
        final StringBuilder signature = new StringBuilder();
        for (int i = 0; i < videos.size(); i++) {
            final ChatObject.VideoParticipant video = videos.get(i);
            aspects[i] = video.aspectRatio > 0 ? video.aspectRatio : MosaicLayout.UNKNOWN;
            weights[i] = video.presentation ? PRESENTATION_CROP_WEIGHT : 1f;
            signature.append(keyOf(video)).append(':').append(Math.round(aspects[i] * 100)).append(';');
        }
        final String value = signature.toString();
        if (!value.equals(plannedSignature)) {
            plannedSignature = value;
            plannedAspect = MosaicLayout.layout(aspects, weights, MIN_ASPECT, MAX_ASPECT, null, 0).aspect;
        }
    }

    /* Geometry */

    private float getAspect() {
        return mosaic != null ? mosaic.containerAspect : plannedAspect;
    }

    @Override
    public int getContentWidth() {
        return Math.round(getAspect() * 1000);
    }

    @Override
    public int getContentHeight() {
        return 1000;
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    /* Policy */

    @Override
    public boolean needsPipOutsideApp() {
        final VoIPService service = VoIPService.getSharedInstance();
        final ChatObject.Call call = getCall();
        return service != null && call != null && !service.isHangingUp() && !call.isScheduled() && !(call.call != null && call.call.rtmp_stream);
    }

    @Override
    public boolean canEnterWithoutUi() {
        return true;
    }

    @Override
    public boolean isOverlayAllowed() {
        return true;
    }

    @Override
    public boolean isPrimaryVisible() {
        return GroupCallActivity.groupCallInstance != null && GroupCallActivity.groupCallUiVisible;
    }

    @Override
    public void getPrimaryVideoRect(@NonNull Rect outScreenRect) {
        outScreenRect.setEmpty();
    }

    /* Actions */

    @Override
    public void getSystemActions(@NonNull List<PipAction> out, int maxActions) {
        CallPipActions.build(out, maxActions, true);
    }

    @Override
    public void onSystemAction(int actionId) {
        CallPipActions.perform(actionId, true);
        AndroidUtilities.runOnUIThread(GroupCallPipContent::update, 300);
    }

    /* System PiP */

    @Override
    public View createSystemView(@NonNull Context context) {
        return new MosaicView(context);
    }

    @Override
    public void onSystemEnter(@NonNull View systemView) {
        CallPipActions.onSystemEnter();
        mosaic = (MosaicView) systemView;
        final GroupCallActivity sheet = GroupCallActivity.groupCallInstance;
        if (sheet != null) {
            // the activity is only paused in PiP: detach the grid renderers ourselves
            sheet.onPause();
            if (sheet.getWindow() != null) {
                sheet.getWindow().getDecorView().setVisibility(View.INVISIBLE);
            }
        }
        mosaic.start();
        // the in-app bubble would be drawn inside the PiP window
        GroupCallPip.updateVisibility(systemView.getContext());
    }

    @Override
    public void onSystemExit(@NonNull View systemView) {
        CallPipActions.onSystemExit();
        ((MosaicView) systemView).stop();
        if (mosaic == systemView) {
            plannedAspect = mosaic.containerAspect;
            mosaic = null;
        }
        final GroupCallActivity sheet = GroupCallActivity.groupCallInstance;
        if (sheet != null) {
            if (sheet.getWindow() != null) {
                sheet.getWindow().getDecorView().setVisibility(View.VISIBLE);
            }
            sheet.onResume();
        }
        GroupCallPip.updateVisibility(systemView.getContext());
    }

    @Override
    public void releaseSystemView(@NonNull View systemView) {
        ((MosaicView) systemView).stop();
        if (mosaic == systemView) {
            mosaic = null;
        }
    }

    @Override
    public void onDismissed() {
        // the PiP window was closed: the call goes on with its notification
        session = null;
        final GroupCallActivity sheet = GroupCallActivity.groupCallInstance;
        if (sheet != null && sheet.getWindow() != null) {
            sheet.getWindow().getDecorView().setVisibility(View.VISIBLE);
        }
        AndroidUtilities.runOnUIThread(GroupCallPipContent::update);
    }

    /**
     * The system PiP view: tiles of the mosaic, our microphone state and the connection status.
     * Tile positions are kept in 0..1 of the view, so they follow the window while the system
     * resizes it; transitions between layouts interpolate those positions.
     */
    private final class MosaicView extends ViewGroup {
        private static final long TICK_MS = 250;
        private static final long ANIMATION_MS = 420;
        /** Relayout when a video's proportions change by more than ~10%. */
        private static final float ASPECT_EPSILON = 0.1f;
        /**
         * A new window aspect is applied by the system a bit later: the transition waits for the
         * resized window (it would otherwise run in the old window and jump), but not longer than this.
         */
        private static final long WINDOW_RESIZE_TIMEOUT_MS = 500;
        /**
         * A new video joins the mosaic with its first frame, when its proportions are known, so the
         * mosaic is rebuilt once and right; without frames it joins after this anyway (as an avatar).
         */
        private static final long NEW_VIDEO_TIMEOUT_MS = 2000;
        private static final long ENTER_GRACE_MS = 1000;

        /** Alive tiles in the visual order. */
        private final ArrayList<Tile> tiles = new ArrayList<>();
        /** Tiles of streams that are gone, fading out. */
        private final ArrayList<Tile> removing = new ArrayList<>();
        /** New streams waiting for their first frame, not shown yet. */
        private final ArrayList<Tile> pending = new ArrayList<>();
        private final TextView statusView;
        private final ImageView micView;
        private final CallPipSelfView selfView;
        private final int gap = dp(2);

        float containerAspect;
        private float progress = 1f;
        private @Nullable ValueAnimator animator;
        private boolean started;
        private long startTime;
        private boolean animationPending;
        private final Runnable startPendingAnimation = () -> {
            if (animationPending) {
                startAnimation();
                // the system did not give the window the asked aspect in time
                post(this::checkWindowFit);
            }
        };
        /** Container aspect the current layout was made for: the declared one, or the window's own. */
        private float layoutContainerAspect;

        private long lastSpeakerId;
        private long lastActionsKey;
        private @Nullable int[] drawingOrder;

        private final Runnable tick = new Runnable() {
            @Override
            public void run() {
                if (!started) {
                    return;
                }
                update(false);
                AndroidUtilities.runOnUIThread(this, TICK_MS);
            }
        };

        MosaicView(Context context) {
            super(context);
            setBackgroundColor(0xFF000000);
            setChildrenDrawingOrderEnabled(true);
            containerAspect = plannedAspect;

            micView = new ImageView(context);
            micView.setScaleType(ImageView.ScaleType.CENTER);
            micView.setColorFilter(Color.WHITE);
            // readable over a bright video
            micView.setBackground(Theme.createCircleDrawable(dp(28), 0x66000000));
            addView(micView);

            statusView = new TextView(context);
            statusView.setTextColor(0xB3FFFFFF);
            statusView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
            statusView.setSingleLine();
            statusView.setGravity(Gravity.CENTER_HORIZONTAL);
            statusView.setShadowLayer(dp(2), 0, 0, 0x80000000);
            statusView.setVisibility(View.GONE);
            addView(statusView);

            selfView = new CallPipSelfView(context);
            addView(selfView);
        }

        void start() {
            if (started) {
                return;
            }
            started = true;
            startTime = SystemClock.elapsedRealtime();
            final VoIPService service = VoIPService.getSharedInstance();
            lastActionsKey = CallPipActions.stateKey();
            selfView.start();
            update(true);
            AndroidUtilities.runOnUIThread(tick, TICK_MS);
        }

        void stop() {
            if (!started) {
                return;
            }
            started = false;
            AndroidUtilities.cancelRunOnUIThread(tick);
            selfView.stop();
            animationPending = false;
            AndroidUtilities.cancelRunOnUIThread(startPendingAnimation);
            if (animator != null) {
                animator.removeAllListeners();
                animator.cancel();
                animator = null;
            }
            for (Tile tile : tiles) {
                tile.release();
                removeView(tile);
            }
            for (Tile tile : removing) {
                tile.release();
                removeView(tile);
            }
            for (Tile tile : pending) {
                tile.release();
                removeView(tile);
            }
            tiles.clear();
            removing.clear();
            pending.clear();
            progress = 1f;
        }

        void update(boolean initial) {
            if (!started) {
                return;
            }
            final VoIPService service = VoIPService.getSharedInstance();
            final ChatObject.Call call = getCall();
            if (service == null || call == null) {
                return;
            }
            final long selfId = service.getSelfId();
            final long now = SystemClock.elapsedRealtime();
            updateOverlays(service);

            // the set of streams
            final LinkedHashMap<String, ChatObject.VideoParticipant> wanted = new LinkedHashMap<>();
            for (ChatObject.VideoParticipant video : collectVideos(call, selfId)) {
                wanted.put(keyOf(video), video);
            }
            final boolean avatarOnly = wanted.isEmpty();
            boolean changed = false;
            for (int i = tiles.size() - 1; i >= 0; i--) {
                final Tile tile = tiles.get(i);
                // the avatar tile stays until a video tile replaces it (new videos wait for frames)
                if (avatarOnly ? !tile.isAvatar() : !tile.isAvatar() && !wanted.containsKey(tile.key)) {
                    tiles.remove(i);
                    tile.unsubscribe();
                    removing.add(tile);
                    changed = true;
                }
            }
            for (int i = pending.size() - 1; i >= 0; i--) {
                final Tile tile = pending.get(i);
                if (!wanted.containsKey(tile.key)) {
                    pending.remove(i);
                    tile.release();
                    removeView(tile);
                    drawingOrder = null;
                }
            }
            if (avatarOnly) {
                if (tiles.isEmpty()) {
                    addTile(new Tile(getContext(), AVATAR_KEY, null));
                    changed = true;
                }
            } else {
                for (ChatObject.VideoParticipant video : wanted.values()) {
                    final String key = keyOf(video);
                    Tile tile = findTile(tiles, key);
                    if (tile == null) {
                        tile = findTile(pending, key);
                    }
                    if (tile == null) {
                        tile = new Tile(getContext(), key, video);
                        if (initial) {
                            // entering PiP: everything at once, proportions from the call grid if known
                            addTile(tile);
                            changed = true;
                        } else {
                            tile.pendingSince = now;
                            tile.setAlpha(0f);
                            pending.add(tile);
                            addView(tile);
                        }
                    } else {
                        tile.video = video;
                    }
                }
            }
            for (int i = 0; i < pending.size(); i++) {
                final Tile tile = pending.get(i);
                // real frames only: the grid's hint may be left from an earlier stream of the same endpoint
                tile.updateAspect(false);
                if (tile.aspect > 0 || now - tile.pendingSince >= NEW_VIDEO_TIMEOUT_MS) {
                    pending.remove(i--);
                    tiles.add(tile);
                    drawingOrder = null;
                    changed = true;
                }
            }
            if (!avatarOnly) {
                boolean hasVideoTile = false;
                for (Tile tile : tiles) {
                    hasVideoTile |= !tile.isAvatar();
                }
                for (int i = tiles.size() - 1; i >= 0 && hasVideoTile; i--) {
                    final Tile tile = tiles.get(i);
                    if (tile.isAvatar()) {
                        tiles.remove(i);
                        removing.add(tile);
                        changed = true;
                    }
                }
            }
            // proportions: a new or a changed one rebuilds the mosaic, the order of participants does not;
            // while a video that just appeared has no frames yet, wait for it (once for all of them)
            boolean aspectChanged = false, waitingForFrames = false;
            for (Tile tile : tiles) {
                tile.updateAspect(true);
                final boolean wasKnown = tile.layoutAspect > 0, known = tile.aspect > 0;
                if (wasKnown != known || known && Math.abs(Math.log(tile.aspect / tile.layoutAspect)) > ASPECT_EPSILON) {
                    aspectChanged = true;
                }
                if (!known && !tile.isAvatar() && now - tile.createdAt < NEW_VIDEO_TIMEOUT_MS) {
                    waitingForFrames = true;
                }
            }
            if (aspectChanged && !waitingForFrames) {
                changed = true;
            }
            if (changed || initial) {
                relayout(!initial);
            }

            // avatars: a tile's own participant; the avatar-only tile shows the current speaker, else
            // the last one (or the last person whose video was shown), else the chat
            final long speakerId = pickSpeaker(call, selfId);
            if (speakerId != 0) {
                lastSpeakerId = speakerId;
            } else if (!avatarOnly && !tiles.isEmpty() && tiles.get(0).video != null) {
                lastSpeakerId = MessageObject.getPeerId(tiles.get(0).video.participant.peer);
            }
            final long avatarPeerId;
            if (speakerId != 0) {
                avatarPeerId = speakerId;
            } else if (lastSpeakerId != 0 && call.participants.get(lastSpeakerId) != null) {
                avatarPeerId = lastSpeakerId;
            } else {
                avatarPeerId = -service.getChat().id;
            }
            for (Tile tile : tiles) {
                tile.updateState(service, call, now, avatarPeerId);
            }
        }

        private void updateOverlays(@NonNull VoIPService service) {
            final boolean micMuted = service.isMicMute();
            micView.setImageResource(micMuted ? R.drawable.msg_voice_muted : R.drawable.msg_voice_unmuted);
            selfView.update();
            final long actionsKey = CallPipActions.stateKey();
            if (actionsKey != lastActionsKey) {
                // mic or camera changed from the notification, the call sheet or a PiP button
                lastActionsKey = actionsKey;
                if (session != null) {
                    session.invalidate();
                }
            }
            final int state = service.getCallState();
            if (state == VoIPService.STATE_RECONNECTING) {
                statusView.setText(getString(R.string.VoipReconnecting));
                statusView.setVisibility(View.VISIBLE);
            } else if (state != VoIPService.STATE_ESTABLISHED) {
                statusView.setText(getString(R.string.VoipConnecting));
                statusView.setVisibility(View.VISIBLE);
            } else {
                statusView.setVisibility(View.GONE);
            }
        }

        private @Nullable Tile findTile(ArrayList<Tile> list, @Nullable String key) {
            for (Tile tile : list) {
                if (TextUtils.equals(tile.key, key)) {
                    return tile;
                }
            }
            return null;
        }

        private void addTile(Tile tile) {
            tiles.add(tile);
            addView(tile);
        }

        /** New layout for the current tiles; everything moves from where it is now. */
        private void relayout(boolean animate) {
            final int n = tiles.size();
            final float[] aspects = new float[n];
            final float[] weights = new float[n];
            final int[] rank = new int[n];
            // the previous layout was a guess (a video without known proportions): no reason to
            // keep its window aspect, only the order of the tiles
            boolean guessed = false;
            for (int i = 0; i < n; i++) {
                final Tile tile = tiles.get(i);
                aspects[i] = tile.aspect;
                weights[i] = tile.video != null && tile.video.presentation ? PRESENTATION_CROP_WEIGHT : 1f;
                rank[i] = tile.hasLayout ? i : -1;
                guessed |= tile.hasLayout && tile.layoutAspect <= 0 && tile.aspect > 0;
            }
            MosaicLayout.Result result = MosaicLayout.layout(aspects, weights, MIN_ASPECT, MAX_ASPECT, rank, guessed ? 0 : containerAspect);
            final int width = getWidth(), height = getHeight();
            boolean fitted = false;
            if (Math.abs(result.aspect - containerAspect) <= 0.001f && width > 0 && height > 0 && !matchesAspect(width, height) && isWindowInPip()) {
                // the system keeps the window in another shape than asked (e.g. expanded by a double
                // tap with the aspect from entering PiP): lay out for the window as it is, the
                // declared aspect stays
                final float windowAspect = width / (float) height;
                result = MosaicLayout.layout(aspects, weights, windowAspect, windowAspect, rank, 0);
                fitted = true;
            }
            layoutContainerAspect = result.aspect;
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("group call pip: mosaic " + java.util.Arrays.toString(aspects) + (fitted ? " in the window" : "") + " -> " + result);
            }

            for (Tile tile : tiles) {
                tile.captureFrom(progress);
            }
            for (Tile tile : removing) {
                tile.captureFrom(progress);
                System.arraycopy(tile.fromRect, 0, tile.toRect, 0, 4);
                tile.toShown = 0f;
            }
            final ArrayList<Tile> ordered = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                final Tile tile = tiles.get(i);
                System.arraycopy(result.rects, i * 4, tile.toRect, 0, 4);
                tile.toShown = 1f;
                tile.layoutAspect = aspects[i];
                if (!tile.hasLayout || !animate) {
                    // appears in place (scale and fade); without animation: just there
                    System.arraycopy(tile.toRect, 0, tile.fromRect, 0, 4);
                    tile.fromShown = animate ? 0f : 1f;
                    tile.hasLayout = true;
                }
            }
            for (int index : result.order) {
                ordered.add(tiles.get(index));
            }
            tiles.clear();
            tiles.addAll(ordered);
            drawingOrder = null;

            final boolean aspectChanged = !fitted && Math.abs(result.aspect - containerAspect) > 0.001f;
            if (aspectChanged) {
                containerAspect = result.aspect;
                if (session != null) {
                    session.invalidate();
                }
            }
            updateTextureSizes();

            if (animator != null) {
                animator.removeAllListeners();
                animator.cancel();
                animator = null;
            }
            animationPending = false;
            AndroidUtilities.cancelRunOnUIThread(startPendingAnimation);
            if (animate) {
                progress = 0f;
                if (aspectChanged && getWidth() > 0 && getHeight() > 0 && !matchesAspect(getWidth(), getHeight())) {
                    // the old layout stays until the system resizes the window
                    animationPending = true;
                    AndroidUtilities.runOnUIThread(startPendingAnimation, WINDOW_RESIZE_TIMEOUT_MS);
                } else {
                    startAnimation();
                }
            } else {
                progress = 1f;
                finishRemoving();
            }
            layoutTiles();
        }

        /**
         * The window got a shape other than the layout was made for: lay out for the window as it
         * is. The system computes PiP min / max sizes for the aspect PiP was entered with and does
         * not update them when the aspect changes, so its expand / collapse (double tap) and pinch
         * may give the window another shape; asking for ours again would fight the user.
         */
        private void checkWindowFit() {
            final int width = getWidth(), height = getHeight();
            if (started && !animationPending && width > 0 && height > 0 && !tiles.isEmpty() && layoutContainerAspect > 0 && isWindowInPip()
                    && Math.abs(Math.log(width / (float) height / layoutContainerAspect)) > 0.06) {
                relayout(true);
            }
        }

        /**
         * Not while entering: the activity reports PiP mode a moment before its window shrinks,
         * and the window has the activity's size until then.
         */
        private boolean isWindowInPip() {
            final android.app.Activity activity = AndroidUtilities.findActivity(getContext());
            return activity != null && AndroidUtilities.isInPictureInPictureMode(activity) && SystemClock.elapsedRealtime() - startTime > ENTER_GRACE_MS;
        }

        private boolean matchesAspect(int width, int height) {
            return Math.abs(Math.log(width / (float) height / containerAspect)) < 0.06;
        }

        private void startAnimation() {
            animationPending = false;
            AndroidUtilities.cancelRunOnUIThread(startPendingAnimation);
            if (animator != null) {
                animator.removeAllListeners();
                animator.cancel();
            }
            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(ANIMATION_MS);
            animator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            animator.addUpdateListener(a -> {
                progress = (float) a.getAnimatedValue();
                layoutTiles();
            });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    animator = null;
                    progress = 1f;
                    finishRemoving();
                    layoutTiles();
                }
            });
            animator.start();
        }

        private void finishRemoving() {
            for (Tile tile : removing) {
                tile.release();
                removeView(tile);
            }
            removing.clear();
            drawingOrder = null;
        }

        /** Each video is laid out at the size that covers its target tile; see {@link Tile#onLayout}. */
        private void updateTextureSizes() {
            final int width = getWidth(), height = getHeight();
            if (width == 0 || height == 0) {
                return;
            }
            for (Tile tile : tiles) {
                tile.setTargetSize(Math.round((tile.toRect[2] - tile.toRect[0]) * width), Math.round((tile.toRect[3] - tile.toRect[1]) * height));
            }
        }

        private void layoutTiles() {
            final int width = getWidth(), height = getHeight();
            if (width == 0 || height == 0) {
                return;
            }
            for (Tile tile : removing) {
                layoutTile(tile, width, height);
            }
            for (Tile tile : tiles) {
                layoutTile(tile, width, height);
            }
            invalidate();
        }

        private void layoutTile(Tile tile, int width, int height) {
            final float[] rect = tile.currentRect;
            tile.getRect(progress, rect);
            final float half = gap / 2f;
            final int l = Math.round(rect[0] * width + (rect[0] > 0.001f ? half : 0));
            final int t = Math.round(rect[1] * height + (rect[1] > 0.001f ? half : 0));
            final int r = Math.round(rect[2] * width - (rect[2] < 0.999f ? half : 0));
            final int b = Math.round(rect[3] * height - (rect[3] < 0.999f ? half : 0));
            final int w = Math.max(1, r - l), h = Math.max(1, b - t);
            if (tile.getMeasuredWidth() != w || tile.getMeasuredHeight() != h || tile.isLayoutRequested()) {
                tile.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
            }
            tile.layout(l, t, l + w, t + h);
            final float shown = tile.getShown(progress);
            tile.setAlpha(shown);
            tile.setScaleX(0.8f + 0.2f * shown);
            tile.setScaleY(0.8f + 0.2f * shown);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            final int width = MeasureSpec.getSize(widthMeasureSpec), height = MeasureSpec.getSize(heightMeasureSpec);
            setMeasuredDimension(width, height);
            micView.measure(MeasureSpec.makeMeasureSpec(dp(28), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(28), MeasureSpec.EXACTLY));
            statusView.measure(MeasureSpec.makeMeasureSpec(Math.max(0, width - dp(64)), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            selfView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            updateTextureSizes();
            if (animationPending && w > 0 && h > 0 && matchesAspect(w, h)) {
                // started from the next layout pass, after the tiles got the new size
                post(startPendingAnimation);
            } else if (!animationPending) {
                post(this::checkWindowFit);
            }
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            micView.layout(dp(6), dp(6), dp(6) + micView.getMeasuredWidth(), dp(6) + micView.getMeasuredHeight());
            final int statusLeft = (r - l - statusView.getMeasuredWidth()) / 2;
            statusView.layout(statusLeft, dp(8), statusLeft + statusView.getMeasuredWidth(), dp(8) + statusView.getMeasuredHeight());
            selfView.layout(0, 0, r - l, b - t);
            layoutTiles();
        }

        @Override
        public void onViewAdded(View child) {
            super.onViewAdded(child);
            drawingOrder = null;
        }

        @Override
        public void onViewRemoved(View child) {
            super.onViewRemoved(child);
            drawingOrder = null;
        }

        /** Fading tiles below the alive ones, the overlays on top. */
        @Override
        protected int getChildDrawingOrder(int childCount, int drawingPosition) {
            if (drawingOrder == null || drawingOrder.length != childCount) {
                final int[] order = new int[childCount];
                int position = 0;
                for (Tile tile : pending) {
                    final int index = indexOfChild(tile);
                    if (index >= 0 && position < childCount) {
                        order[position++] = index;
                    }
                }
                for (Tile tile : removing) {
                    final int index = indexOfChild(tile);
                    if (index >= 0 && position < childCount) {
                        order[position++] = index;
                    }
                }
                for (Tile tile : tiles) {
                    final int index = indexOfChild(tile);
                    if (index >= 0 && position < childCount) {
                        order[position++] = index;
                    }
                }
                for (int i = 0; i < childCount && position < childCount; i++) {
                    final View child = getChildAt(i);
                    if (!(child instanceof Tile)) {
                        order[position++] = i;
                    }
                }
                if (position != childCount) {
                    for (int i = 0; i < childCount; i++) {
                        order[i] = i;
                    }
                }
                drawingOrder = order;
            }
            return drawingOrder[drawingPosition];
        }
    }

    /**
     * One stream of the mosaic: the remote video, or the avatar with voice waves when there are
     * no frames (paused by the sender, no frames for a while, waiting for the first frame).
     * The avatar-only tile has no video at all.
     */
    private final class Tile extends ViewGroup {
        private static final long STALL_MS = 1500;
        private static final long FIRST_FRAME_WAIT_MS = 1200;

        private boolean isStreamPaused(@NonNull ChatObject.VideoParticipant video) {
            final TLRPC.TL_groupCallParticipantVideo stream = video.presentation ? video.participant.presentation : video.participant.video;
            return stream == null || stream.paused;
        }

        final String key;
        @Nullable ChatObject.VideoParticipant video;
        private final @Nullable VoIPTextureView textureView;
        private final CallPipAvatarView avatarView;
        private final TextView nameView;
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF borderRect = new RectF();

        // what is actually subscribed: VideoParticipant objects are reused with new participants
        private @Nullable TLRPC.GroupCallParticipant subscribedParticipant;
        private boolean subscribedPresentation;
        private volatile long lastFrameTime;
        private volatile int frameWidth, frameHeight;
        long pendingSince;
        final long createdAt = SystemClock.elapsedRealtime();
        /** Starts with the avatar shown (the views' initial state). */
        private boolean videoShown = false;
        private long shownPeerId;
        private boolean speaking;
        private float speakingProgress;
        private long lastDrawTime;

        /** Known proportions of the video (w / h), {@link MosaicLayout#UNKNOWN} otherwise. */
        float aspect = MosaicLayout.UNKNOWN;
        /** {@link #aspect} the current layout was made for. */
        float layoutAspect = MosaicLayout.UNKNOWN;
        boolean hasLayout;
        final float[] fromRect = new float[4], toRect = new float[4], currentRect = new float[4];
        float fromShown, toShown;
        private int targetWidth, targetHeight;
        private int textureWidth, textureHeight;

        private final VideoSink frameSink = new VideoSink() {
            @Override
            public void onFrame(VideoFrame frame) {
                lastFrameTime = SystemClock.elapsedRealtime();
                frameWidth = frame.getRotatedWidth();
                frameHeight = frame.getRotatedHeight();
                if (textureView != null) {
                    textureView.renderer.onFrame(frame);
                }
            }
        };

        Tile(Context context, String key, @Nullable ChatObject.VideoParticipant video) {
            super(context);
            this.key = key;
            this.video = video;
            setBackgroundColor(0xFF1B1F23);
            setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(4));
                }
            });
            setClipToOutline(true);

            if (video != null) {
                textureView = new VoIPTextureView(context, false, false, false, false);
                textureView.renderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
                textureView.renderer.setEnableHardwareScaler(true);
                textureView.scaleType = VoIPTextureView.SCALE_TYPE_FIT;
                textureView.renderer.init(VideoCapturerDevice.getEglBase().getEglBaseContext(), new RendererCommon.RendererEvents() {
                    @Override
                    public void onFirstFrameRendered() {
                        AndroidUtilities.runOnUIThread(Tile.this::onVideoChanged);
                    }

                    @Override
                    public void onFrameResolutionChanged(int videoWidth, int videoHeight, int rotation) {
                        AndroidUtilities.runOnUIThread(Tile.this::onVideoChanged);
                    }
                });
                if (textureView.backgroundView != null) {
                    textureView.backgroundView.setVisibility(View.GONE);
                }
                // stays visible: a TextureView that is GONE has no surface and never renders the first frame
                addView(textureView);
            } else {
                textureView = null;
            }

            // above the video: covers it until frames arrive and while they are missing
            avatarView = new CallPipAvatarView(context);
            addView(avatarView);

            nameView = new TextView(context);
            nameView.setTextColor(Color.WHITE);
            nameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            nameView.setSingleLine();
            nameView.setEllipsize(TextUtils.TruncateAt.END);
            nameView.setGravity(Gravity.CENTER_HORIZONTAL);
            nameView.setShadowLayer(dp(2), 0, 0, 0x80000000);
            addView(nameView);

            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(dp(2));
            borderPaint.setColor(Theme.getColor(Theme.key_voipgroup_speakingText));
            setWillNotDraw(false);

            subscribe();
        }

        boolean isAvatar() {
            return textureView == null;
        }

        private void onVideoChanged() {
            if (mosaic != null && (mosaic.tiles.contains(this) || mosaic.pending.contains(this))) {
                mosaic.update(false);
            }
        }

        private void subscribe() {
            final VoIPService service = VoIPService.getSharedInstance();
            if (video == null || textureView == null || subscribedParticipant != null || service == null) {
                return;
            }
            if (service.addExtraRemoteSink(video.participant, video.presentation, frameSink)) {
                subscribedParticipant = video.participant;
                subscribedPresentation = video.presentation;
            }
        }

        void unsubscribe() {
            final VoIPService service = VoIPService.getSharedInstance();
            if (subscribedParticipant != null && service != null) {
                service.removeExtraRemoteSink(subscribedParticipant, subscribedPresentation, frameSink);
            }
            subscribedParticipant = null;
        }

        void release() {
            unsubscribe();
            if (textureView != null) {
                textureView.renderer.release();
            }
        }

        void updateAspect(boolean allowHint) {
            final int w = frameWidth, h = frameHeight;
            if (w > 0 && h > 0) {
                // straight from the frames: known before the first frame is rendered
                aspect = w / (float) h;
            } else if (textureView != null && textureView.renderer.isFirstFrameRendered() && textureView.renderer.rotatedFrameWidth > 0 && textureView.renderer.rotatedFrameHeight > 0) {
                aspect = textureView.renderer.rotatedFrameWidth / (float) textureView.renderer.rotatedFrameHeight;
            } else if (allowHint && aspect <= 0 && video != null && video.aspectRatio > 0) {
                aspect = video.aspectRatio;
            }
        }

        void updateState(@NonNull VoIPService service, @NonNull ChatObject.Call call, long now, long avatarPeerId) {
            if (subscribedParticipant == null) {
                subscribe();
            }
            final boolean hasFrames = textureView != null && subscribedParticipant != null && textureView.renderer.isFirstFrameRendered() && now - lastFrameTime < STALL_MS;
            // a new tile of a live stream waits a moment for its first frame: no avatar flash
            final boolean waitingForFirstFrame = textureView != null && subscribedParticipant != null && !textureView.renderer.isFirstFrameRendered()
                && video != null && !isStreamPaused(video) && now - createdAt < FIRST_FRAME_WAIT_MS;
            setVideoShown(hasFrames || waitingForFirstFrame);

            final long peerId = video != null ? MessageObject.getPeerId(video.participant.peer) : avatarPeerId;
            if (peerId != shownPeerId) {
                shownPeerId = peerId;
                showPeer(service.getAccount(), peerId);
            }
            final TLRPC.GroupCallParticipant participant = call.participants.get(peerId);
            final boolean isSpeaking = participant != null && call.currentSpeakingPeers.get(peerId, null) != null;
            avatarView.setSpeaking(isSpeaking, isSpeaking ? participant.amplitude : 0f);
            if (speaking != isSpeaking) {
                speaking = isSpeaking;
                invalidate();
            }
        }

        private void setVideoShown(boolean shown) {
            if (videoShown == shown) {
                return;
            }
            videoShown = shown;
            avatarView.setVisibility(shown ? View.GONE : View.VISIBLE);
            if (BuildVars.LOGS_ENABLED && textureView != null) {
                final long sinceFrame = lastFrameTime == 0 ? -1 : SystemClock.elapsedRealtime() - lastFrameTime;
                FileLog.d("group call pip: " + key + (shown ? " video" : " avatar") + ", last frame " + sinceFrame + " ms ago, subscribed " + (subscribedParticipant != null) + ", rendered " + textureView.renderer.isFirstFrameRendered());
            }
            invalidate();
        }

        private void showPeer(int account, long peerId) {
            final MessagesController controller = MessagesController.getInstance(account);
            final TLObject object;
            final String name;
            if (DialogObject.isUserDialog(peerId)) {
                final TLRPC.User user = controller.getUser(peerId);
                object = user;
                name = UserObject.getUserName(user);
            } else {
                final TLRPC.Chat chat = controller.getChat(-peerId);
                object = chat;
                name = chat != null ? chat.title : "";
            }
            avatarView.setPeer(account, object);
            nameView.setText(name);
        }

        /* Animation state */

        void captureFrom(float progress) {
            getRect(progress, fromRect);
            fromShown = getShown(progress);
        }

        void getRect(float progress, float[] out) {
            for (int i = 0; i < 4; i++) {
                out[i] = fromRect[i] + (toRect[i] - fromRect[i]) * progress;
            }
        }

        float getShown(float progress) {
            return fromShown + (toShown - fromShown) * progress;
        }

        /**
         * The video is laid out once per layout at the size that covers the target tile with the
         * video's proportions; during transitions it is only scaled (uniformly) to cover the
         * current tile, so its buffer is not reallocated on every frame and nothing is stretched.
         */
        void setTargetSize(int width, int height) {
            targetWidth = width;
            targetHeight = height;
            int w = Math.max(1, width), h = Math.max(1, height);
            if (aspect > 0) {
                if (w / (float) h > aspect) {
                    h = Math.round(w / aspect);
                } else {
                    w = Math.round(h * aspect);
                }
            }
            if (w != textureWidth || h != textureHeight) {
                textureWidth = w;
                textureHeight = h;
                requestLayout();
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            final int width = MeasureSpec.getSize(widthMeasureSpec), height = MeasureSpec.getSize(heightMeasureSpec);
            setMeasuredDimension(width, height);
            avatarView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
            nameView.measure(MeasureSpec.makeMeasureSpec(Math.max(0, width - dp(12)), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            if (textureView != null) {
                final int w = textureWidth > 0 ? textureWidth : width, h = textureHeight > 0 ? textureHeight : height;
                if (textureView.getMeasuredWidth() != w || textureView.getMeasuredHeight() != h || textureView.isLayoutRequested()) {
                    textureView.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
                }
            }
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            final int width = r - l, height = b - t;
            avatarView.layout(0, 0, width, height);
            if (textureView != null) {
                final int w = textureView.getMeasuredWidth(), h = textureView.getMeasuredHeight();
                final int left = (width - w) / 2, top = (height - h) / 2;
                textureView.layout(left, top, left + w, top + h);
                final float scale = Math.max(width / (float) w, height / (float) h);
                textureView.setScaleX(scale);
                textureView.setScaleY(scale);
            }
            final boolean nameFits = width >= dp(72) && height >= dp(48);
            nameView.setVisibility(nameFits ? View.VISIBLE : View.GONE);
            final int nameTop = height - dp(6) - nameView.getMeasuredHeight();
            nameView.layout(dp(6), nameTop, dp(6) + nameView.getMeasuredWidth(), nameTop + nameView.getMeasuredHeight());
            invalidateOutline();
        }

        @Override
        protected void dispatchDraw(@NonNull Canvas canvas) {
            super.dispatchDraw(canvas);
            // speaking: a border around the video (the avatar has its own waves)
            final long now = SystemClock.uptimeMillis();
            final long dt = lastDrawTime == 0 ? 16 : Math.min(48, now - lastDrawTime);
            lastDrawTime = now;
            final float target = speaking && videoShown ? 1f : 0f;
            speakingProgress += (target - speakingProgress) * Math.min(1f, dt / 150f);
            if (speakingProgress > 0.01f) {
                final float inset = borderPaint.getStrokeWidth() / 2f;
                borderRect.set(inset, inset, getWidth() - inset, getHeight() - inset);
                borderPaint.setAlpha((int) (255 * speakingProgress));
                canvas.drawRoundRect(borderRect, dp(4), dp(4), borderPaint);
            }
            if (Math.abs(target - speakingProgress) > 0.01f) {
                invalidate();
            } else {
                speakingProgress = target;
                lastDrawTime = 0;
            }
        }
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
