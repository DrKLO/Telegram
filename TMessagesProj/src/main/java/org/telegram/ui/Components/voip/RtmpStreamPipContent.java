package org.telegram.ui.Components.voip;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.os.SystemClock;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.voip.VideoCapturerDevice;
import org.telegram.messenger.voip.VoIPService;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.GroupCallActivity;
import org.telegram.ui.LaunchActivity;
import org.telegram.utils.pip.PipContent;
import org.telegram.utils.pip.PipCoordinator;
import org.telegram.utils.pip.PipSession;
import org.telegram.utils.pip.PipTextureView;
import org.webrtc.EglBase;
import org.webrtc.EglRenderer;
import org.webrtc.GlRectDrawer;
import org.webrtc.JavaI420Buffer;
import org.webrtc.RendererCommon;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;

import java.nio.ByteBuffer;

/**
 * RTMP live stream (a group call with {@code rtmp_stream}) in utils.pip, like a video:
 * <ul>
 *   <li>the PiP button of the stream sheet opens the host's floating window (an overlay with the
 *   permission, an in-app window without it); leaving the app with the overlay permission does
 *   the same, without it the floating window or the open sheet go to system PiP;</li>
 *   <li>the floating window gets the stream frames through its own receiver
 *   ({@link VoIPService#addExtraRemoteSink}), drawn into the window's texture, so the same texture
 *   moves into system PiP and back;</li>
 *   <li>system PiP from the open sheet shows its own view (the stream, or the chat's avatar while
 *   no frames come), without a source rect hint, like calls.</li>
 * </ul>
 * Closing the window never leaves the stream (the sheet or the call bar stay).
 */
public class RtmpStreamPipContent implements PipContent, NotificationCenter.NotificationCenterDelegate {
    private static final int PRIORITY = 10;
    /**
     * A live stream comes in parts of about a second: a gap of a couple of seconds is normal and
     * keeps the last frame. Only no frames for this long means the stream stopped (the avatar).
     */
    private static final long STALL_MS = 5000;
    /** A renderer that has not had a frame yet shows the avatar after this (not broadcasting yet). */
    private static final long NO_FRAMES_MS = 700;

    private static @Nullable RtmpStreamPipContent instance;

    private @Nullable PipSession session;

    /** All renderers of this content get the frames from here. */
    private final FrameFanout fanout = new FrameFanout();
    private @Nullable TLRPC.GroupCallParticipant boundParticipant;
    private boolean boundPresentation;

    /** Draws into the floating window's texture (the same texture goes to system PiP and back). */
    private @Nullable PipEglRenderer floatingRenderer;
    private @Nullable StreamSystemView systemView;
    /** The chat's avatar drawn into the floating texture while the stream has no frames. */
    private @Nullable StallPlaceholder stallPlaceholder;
    private final Runnable stallTick = new Runnable() {
        @Override
        public void run() {
            final PipEglRenderer renderer = floatingRenderer;
            if (renderer == null) {
                return;
            }
            if (!fanout.isShowingVideo(floatingStartTime)) {
                if (stallPlaceholder == null) {
                    stallPlaceholder = new StallPlaceholder();
                }
                stallPlaceholder.drawInto(renderer);
            }
            AndroidUtilities.runOnUIThread(this, 500);
        }
    };

    /** The account whose call updates rebind the stream (the participant appears when it starts). */
    private final int account;

    private RtmpStreamPipContent(int account) {
        this.account = account;
        NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.groupCallUpdated);
        NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.applyGroupCallVisibleParticipants);
    }

    private static @NonNull RtmpStreamPipContent obtain() {
        if (instance == null) {
            final VoIPService service = VoIPService.getSharedInstance();
            instance = new RtmpStreamPipContent(service != null ? service.getAccount() : UserConfig.selectedAccount);
        }
        return instance;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.groupCallUpdated || id == NotificationCenter.applyGroupCallVisibleParticipants) {
            // the stream participant appears, changes or goes away (not broadcasting)
            bindStream();
            if (systemView != null) {
                systemView.update();
            }
        }
    }

    /* Static API */

    private static @Nullable ChatObject.Call getStreamCall() {
        final VoIPService service = VoIPService.getSharedInstance();
        final ChatObject.Call call = service != null ? service.groupCall : null;
        if (service == null || call == null || call.call == null || !call.call.rtmp_stream || service.isHangingUp()) {
            return null;
        }
        return call;
    }

    /** Call state or its UI changed (called with {@link GroupCallPipContent#update}). */
    public static void update() {
        if (getStreamCall() == null) {
            if (instance != null) {
                final RtmpStreamPipContent content = instance;
                instance = null;
                content.release();
            }
            return;
        }
        obtain().ensureSession();
        instance.bindStream();
        instance.session.invalidate();
    }

    /** The stream is shown in the floating window (the call bar and the bubble step aside). */
    public static boolean isFloating() {
        return instance != null && instance.session != null && instance.session.isFloating();
    }

    /**
     * The PiP button of the stream sheet, or leaving the app with the overlay permission: the
     * floating window replaces the sheet. Returns false if the stream cannot be shown.
     */
    public static boolean openFloating(@NonNull Activity activity) {
        if (getStreamCall() == null) {
            return false;
        }
        final RtmpStreamPipContent content = obtain();
        content.ensureSession();
        if (content.session.getState() != PipSession.STATE_PRIMARY) {
            return content.session.isFloating();
        }
        final PipTextureView target = content.session.startFloating(activity, null, true);
        if (target == null) {
            return false;
        }
        content.floatingRenderer = new PipEglRenderer(target);
        content.fanout.floating = content.floatingRenderer;
        content.bindStream();
        content.startStallWatch();
        if (GroupCallActivity.groupCallInstance != null) {
            GroupCallActivity.groupCallInstance.dismiss();
        }
        notifyVisibility();
        return true;
    }

    /** The sheet is opened by other means (the call bar): it takes the stream back. */
    public static void onSheetShown() {
        if (instance != null && instance.session != null && instance.session.isFloating()) {
            instance.closeFloatingForSheet();
        }
    }

    /* Lifecycle */

    private void ensureSession() {
        if (session == null || !session.isAlive()) {
            session = PipCoordinator.getInstance().register(this);
        }
    }

    private void release() {
        NotificationCenter.getInstance(account).removeObserver(this, NotificationCenter.groupCallUpdated);
        NotificationCenter.getInstance(account).removeObserver(this, NotificationCenter.applyGroupCallVisibleParticipants);
        if (session != null) {
            final PipSession s = session;
            session = null;
            s.release();
        }
        releaseRenderers();
    }

    /** When the floating renderer started: frames before it do not count. */
    private long floatingStartTime;

    private void startStallWatch() {
        floatingStartTime = SystemClock.elapsedRealtime();
        AndroidUtilities.cancelRunOnUIThread(stallTick);
        // the stream's first frames usually come at once: no placeholder flash before them
        AndroidUtilities.runOnUIThread(stallTick, 700);
    }

    private void releaseRenderers() {
        AndroidUtilities.cancelRunOnUIThread(stallTick);
        if (stallPlaceholder != null) {
            stallPlaceholder.release();
            stallPlaceholder = null;
        }
        fanout.floating = null;
        fanout.system = null;
        if (floatingRenderer != null) {
            floatingRenderer.release();
            floatingRenderer = null;
        }
        unbindStream();
    }

    /** Receives the stream only while one of our renderers shows it. */
    private void bindStream() {
        final VoIPService service = VoIPService.getSharedInstance();
        final ChatObject.Call call = getStreamCall();
        final boolean needed = fanout.floating != null || fanout.system != null;
        TLRPC.GroupCallParticipant participant = null;
        boolean presentation = false;
        if (needed && call != null && !call.visibleVideoParticipants.isEmpty()) {
            final ChatObject.VideoParticipant video = call.visibleVideoParticipants.get(0);
            participant = video.participant;
            presentation = video.presentation;
        }
        if (participant == boundParticipant && presentation == boundPresentation) {
            return;
        }
        unbindStream();
        if (participant != null && service != null && service.addExtraRemoteSink(participant, presentation, fanout)) {
            boundParticipant = participant;
            boundPresentation = presentation;
            log("bound " + (presentation ? participant.presentationEndpoint : participant.videoEndpoint));
        }
    }

    private void unbindStream() {
        if (boundParticipant != null) {
            final VoIPService service = VoIPService.getSharedInstance();
            if (service != null) {
                service.removeExtraRemoteSink(boundParticipant, boundPresentation, fanout);
            }
            boundParticipant = null;
        }
    }

    private void closeFloatingForSheet() {
        if (session == null) {
            return;
        }
        final PipEglRenderer renderer = floatingRenderer;
        floatingRenderer = null;
        fanout.floating = null;
        session.completeExpand(texture -> {
            if (renderer != null) {
                renderer.release();
            }
            if (texture != null) {
                texture.release();
            }
            // only now: the sheet has subscribed its own renderer meanwhile, the native video
            // output is kept instead of being removed and added again
            bindStream();
        });
        notifyVisibility();
    }

    /** The call bar and the bubble depend on {@link #isFloating}. */
    private static void notifyVisibility() {
        AndroidUtilities.runOnUIThread(() -> NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.groupCallVisibilityChanged));
    }

    private static void setSheetHidden(boolean hidden) {
        final GroupCallActivity sheet = GroupCallActivity.groupCallInstance;
        if (sheet != null && sheet.getWindow() != null) {
            sheet.getWindow().getDecorView().setVisibility(hidden ? View.INVISIBLE : View.VISIBLE);
        }
    }

    /* Geometry */

    @Override
    public int getContentWidth() {
        if (fanout.frameWidth > 0) {
            return fanout.frameWidth;
        }
        final float aspect = getParticipantAspect();
        return aspect > 0 ? Math.round(aspect * 1000) : 16;
    }

    @Override
    public int getContentHeight() {
        if (fanout.frameHeight > 0) {
            return fanout.frameHeight;
        }
        return getParticipantAspect() > 0 ? 1000 : 9;
    }

    private static float getParticipantAspect() {
        final ChatObject.Call call = getStreamCall();
        if (call == null || call.visibleVideoParticipants.isEmpty()) {
            return 0;
        }
        return call.visibleVideoParticipants.get(0).aspectRatio;
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    /* Playback: a live stream has none */

    @Override
    public boolean hasPlaybackControls() {
        return false;
    }

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

    /* Policy */

    @Override
    public boolean needsPipOutsideApp() {
        return getStreamCall() != null;
    }

    @Override
    public boolean isPrimaryVisible() {
        final GroupCallActivity sheet = GroupCallActivity.groupCallInstance;
        return sheet != null && GroupCallActivity.groupCallUiVisible && sheet.isRtmpStream();
    }

    /** No hint: the system shows the app icon while the window shrinks, as for calls. */
    @Override
    public void getPrimaryVideoRect(@NonNull Rect outScreenRect) {
        outScreenRect.setEmpty();
    }

    /* Primary ↔ floating */

    /** Not used: system PiP from the sheet has its own view ({@link #createSystemView}). */
    @Override
    public void releasePrimary(@NonNull PipTextureView target) {
        floatingRenderer = new PipEglRenderer(target);
        fanout.floating = floatingRenderer;
        bindStream();
        startStallWatch();
    }

    @Override
    public void restorePrimary(@NonNull SurfaceTexture surfaceTexture) {
        final PipEglRenderer renderer = floatingRenderer;
        floatingRenderer = null;
        fanout.floating = null;
        bindStream();
        if (renderer != null) {
            renderer.release();
        }
        surfaceTexture.release();
    }

    @Override
    public void onExpandRequested() {
        final VoIPService service = VoIPService.getSharedInstance();
        final LaunchActivity activity = LaunchActivity.instance;
        if (service == null || activity == null || getStreamCall() == null) {
            if (session != null) {
                session.dismiss();
            }
            return;
        }
        if (GroupCallActivity.groupCallInstance == null) {
            GroupCallActivity.create(activity, AccountInstance.getInstance(service.getAccount()), null, null, false, null);
        }
        closeFloatingForSheet();
    }

    @Override
    public void onDismissed() {
        // the stream goes on (the sheet or the call bar)
        session = null;
        if (systemView != null) {
            setSheetHidden(false);
        }
        releaseRenderers();
        notifyVisibility();
        AndroidUtilities.runOnUIThread(RtmpStreamPipContent::update);
    }

    /* System PiP */

    @Override
    public View createSystemView(@NonNull Context context) {
        return new StreamSystemView(context);
    }

    @Override
    public void onSystemEnter(@NonNull View view) {
        systemView = (StreamSystemView) view;
        systemView.start();
        fanout.system = systemView.textureView.renderer;
        bindStream();
    }

    @Override
    public void onSystemEntered(@NonNull View view) {
        // the sheet is a window of its own: it would be drawn inside the PiP window
        setSheetHidden(true);
    }

    @Override
    public void onSystemExit(@NonNull View view) {
        setSheetHidden(false);
        detachSystemView(view);
    }

    @Override
    public void releaseSystemView(@NonNull View view) {
        if (view == systemView) {
            setSheetHidden(false);
        }
        detachSystemView(view);
        ((StreamSystemView) view).release();
    }

    private void detachSystemView(@NonNull View view) {
        if (view == systemView) {
            systemView.stop();
            systemView = null;
            fanout.system = null;
            bindStream();
        }
    }

    private static void log(String message) {
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("rtmp pip: " + message);
        }
    }

    /** Fans the stream frames out to our renderers; remembers the frame size and time. */
    private final class FrameFanout implements VideoSink {
        volatile @Nullable VideoSink floating;
        volatile @Nullable VideoSink system;
        volatile int frameWidth, frameHeight;
        volatile long lastFrameTime;

        private final Runnable onSizeChanged = () -> {
            if (session != null) {
                session.invalidate();
            }
            if (systemView != null) {
                systemView.update();
            }
        };

        @Override
        public void onFrame(VideoFrame frame) {
            final long now = SystemClock.elapsedRealtime();
            final boolean wasStalled = now - lastFrameTime > STALL_MS;
            lastFrameTime = now;
            final int w = frame.getRotatedWidth(), h = frame.getRotatedHeight();
            if (w != frameWidth || h != frameHeight || wasStalled) {
                frameWidth = w;
                frameHeight = h;
                AndroidUtilities.runOnUIThread(onSizeChanged);
            }
            final VideoSink f = floating, s = system;
            if (f != null) {
                f.onFrame(frame);
            }
            if (s != null) {
                s.onFrame(frame);
            }
        }

        /**
         * A renderer started at {@code since} shows the stream: it has had a frame since then and
         * the frames have not stopped for long; before its first frame only for a short while.
         */
        boolean isShowingVideo(long since) {
            final long now = SystemClock.elapsedRealtime();
            if (lastFrameTime < since) {
                return now - since < NO_FRAMES_MS;
            }
            return now - lastFrameTime < STALL_MS;
        }
    }

    /**
     * The chat's avatar on a dark background as a video frame: fed to the floating renderer while
     * the stream has no frames (not broadcasting yet, or stalled), so the window and system PiP
     * opened from it show it instead of a black or frozen picture.
     */
    private static final class StallPlaceholder {
        private static final int WIDTH = 640, HEIGHT = 360;
        private final ImageReceiver imageReceiver = new ImageReceiver();
        private @Nullable VideoFrame frame;

        StallPlaceholder() {
            final VoIPService service = VoIPService.getSharedInstance();
            final TLRPC.Chat chat = service != null ? service.getChat() : null;
            final AvatarDrawable avatarDrawable = new AvatarDrawable();
            if (chat != null) {
                avatarDrawable.setInfo(service.getAccount(), chat);
                imageReceiver.setCurrentAccount(service.getAccount());
            }
            imageReceiver.setRoundRadius(HEIGHT / 6);
            imageReceiver.setDelegate((receiver, set, thumb, memCache) -> {
                if (set && !thumb) {
                    rebuild();
                }
            });
            imageReceiver.onAttachedToWindow();
            imageReceiver.setImage(chat != null ? ImageLocation.getForChat(chat, ImageLocation.TYPE_SMALL) : null, "100_100", avatarDrawable, null, chat, 0);
            rebuild();
        }

        private void rebuild() {
            final Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
            final Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(0xFF1B1F23);
            final int size = HEIGHT / 3;
            imageReceiver.setImageCoords((WIDTH - size) / 2f, (HEIGHT - size) / 2f, size, size);
            imageReceiver.draw(canvas);
            final VideoFrame newFrame = new VideoFrame(toI420(bitmap), 0, 0);
            bitmap.recycle();
            if (frame != null) {
                frame.release();
            }
            frame = newFrame;
        }

        void drawInto(@NonNull VideoSink sink) {
            if (frame != null) {
                sink.onFrame(frame);
            }
        }

        void release() {
            imageReceiver.onDetachedFromWindow();
            if (frame != null) {
                frame.release();
                frame = null;
            }
        }

        private static JavaI420Buffer toI420(@NonNull Bitmap bitmap) {
            final int w = bitmap.getWidth(), h = bitmap.getHeight();
            final int[] argb = new int[w * h];
            bitmap.getPixels(argb, 0, w, 0, 0, w, h);
            final JavaI420Buffer buffer = JavaI420Buffer.allocate(w, h);
            final ByteBuffer y = buffer.getDataY(), u = buffer.getDataU(), v = buffer.getDataV();
            final int strideY = buffer.getStrideY(), strideU = buffer.getStrideU(), strideV = buffer.getStrideV();
            for (int row = 0; row < h; row++) {
                for (int col = 0; col < w; col++) {
                    final int c = argb[row * w + col];
                    final int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
                    y.put(row * strideY + col, (byte) (((66 * r + 129 * g + 25 * b + 128) >> 8) + 16));
                    if ((row & 1) == 0 && (col & 1) == 0) {
                        u.put((row >> 1) * strideU + (col >> 1), (byte) (((-38 * r - 74 * g + 112 * b + 128) >> 8) + 128));
                        v.put((row >> 1) * strideV + (col >> 1), (byte) (((112 * r - 94 * g - 18 * b + 128) >> 8) + 128));
                    }
                }
            }
            return buffer;
        }
    }

    /** System PiP opened from the sheet: the stream, or the chat's avatar while there are no frames. */
    @SuppressLint("ViewConstructor")
    private final class StreamSystemView extends FrameLayout {
        final VoIPTextureView textureView;
        private final CallPipAvatarView avatarView;
        private boolean started;
        private boolean videoShown = true;
        private final long createdAt = SystemClock.elapsedRealtime();
        private final Runnable tick = new Runnable() {
            @Override
            public void run() {
                update();
                if (started) {
                    AndroidUtilities.runOnUIThread(this, 500);
                }
            }
        };

        StreamSystemView(@NonNull Context context) {
            super(context);
            setBackgroundColor(0xFF000000);
            avatarView = new CallPipAvatarView(context);
            addView(avatarView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            textureView = new VoIPTextureView(context, false, false, false, false);
            textureView.renderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
            textureView.scaleType = VoIPTextureView.SCALE_TYPE_FILL;
            textureView.renderer.setRotateTextureWithScreen(true);
            textureView.renderer.init(VideoCapturerDevice.getEglBase().getEglBaseContext(), null);
            if (textureView.backgroundView != null) {
                textureView.backgroundView.setVisibility(View.GONE);
            }
            // stays visible: a renderer without a surface never draws its first frame
            addView(textureView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            final ChatObject.Call call = getStreamCall();
            final VoIPService service = VoIPService.getSharedInstance();
            if (call != null && service != null && service.getChat() != null) {
                avatarView.setPeer(service.getAccount(), service.getChat());
            }
        }

        void start() {
            if (!started) {
                started = true;
                AndroidUtilities.runOnUIThread(tick, 500);
            }
            update();
        }

        void stop() {
            started = false;
            AndroidUtilities.cancelRunOnUIThread(tick);
        }

        void update() {
            setVideoShown(fanout.isShowingVideo(createdAt));
        }

        private void setVideoShown(boolean shown) {
            if (videoShown == shown) {
                return;
            }
            videoShown = shown;
            textureView.animate().cancel();
            textureView.animate().alpha(shown ? 1f : 0f).setDuration(200).start();
        }

        void release() {
            stop();
            textureView.renderer.release();
        }
    }
}
