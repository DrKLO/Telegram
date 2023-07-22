/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import static com.google.android.exoplayer2.C.TRACK_TYPE_AUDIO;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.AudioManager;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.audio.AudioCapabilities;
import com.google.android.exoplayer2.audio.AudioProcessor;
import com.google.android.exoplayer2.audio.AudioSink;
import com.google.android.exoplayer2.audio.DefaultAudioSink;
import com.google.android.exoplayer2.audio.TeeAudioProcessor;
import com.google.android.exoplayer2.mediacodec.MediaCodecRenderer;
import com.google.android.exoplayer2.source.LoopingMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultAllocator;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.video.SurfaceNotValidException;
import com.google.android.exoplayer2.video.VideoListener;
import com.google.android.exoplayer2.video.VideoSize;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FourierTransform;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.secretmedia.ExtendedDefaultDataSourceFactory;
import org.telegram.ui.Stories.recorder.StoryEntry;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

@SuppressLint("NewApi")
public class VideoPlayer implements Player.Listener, VideoListener, AnalyticsListener, NotificationCenter.NotificationCenterDelegate {

    private DispatchQueue workerQueue;
    private boolean isStory;

    public boolean createdWithAudioTrack() {
        return !audioDisabled;
    }

    public interface VideoPlayerDelegate {
        void onStateChanged(boolean playWhenReady, int playbackState);
        void onError(VideoPlayer player, Exception e);
        void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio);
        void onRenderedFirstFrame();
        void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture);
        boolean onSurfaceDestroyed(SurfaceTexture surfaceTexture);
        default void onRenderedFirstFrame(EventTime eventTime) {

        }
        default void onSeekStarted(EventTime eventTime) {

        }
        default void onSeekFinished(EventTime eventTime) {

        }
    }

    public interface AudioVisualizerDelegate {
        void onVisualizerUpdate(boolean playing, boolean animate, float[] values);
        boolean needUpdate();
    }

    public ExoPlayer player;
    private ExoPlayer audioPlayer;
    private MappingTrackSelector trackSelector;
    private DataSource.Factory mediaDataSourceFactory;
    private TextureView textureView;
    private SurfaceView surfaceView;
    private Surface surface;
    private boolean isStreaming;
    private boolean autoplay;
    private boolean mixedAudio;

    private boolean triedReinit;

    private Uri currentUri;

    private boolean videoPlayerReady;
    private boolean audioPlayerReady;
    private boolean mixedPlayWhenReady;

    private VideoPlayerDelegate delegate;
    private AudioVisualizerDelegate audioVisualizerDelegate;
    private int lastReportedPlaybackState;
    private boolean lastReportedPlayWhenReady;

    private Uri videoUri, audioUri;
    private String videoType, audioType;
    private boolean loopingMediaSource;
    private boolean looping;
    private int repeatCount;

    private boolean shouldPauseOther;
    MediaSource.Factory dashMediaSourceFactory;
    HlsMediaSource.Factory hlsMediaSourceFactory;
    SsMediaSource.Factory ssMediaSourceFactory;
    ProgressiveMediaSource.Factory progressiveMediaSourceFactory;

    Handler audioUpdateHandler = new Handler(Looper.getMainLooper());

    boolean audioDisabled;

    public VideoPlayer() {
        this(true, false);
    }

    static int playerCounter = 0;
    public VideoPlayer(boolean pauseOther, boolean audioDisabled) {
        this.audioDisabled = audioDisabled;
        mediaDataSourceFactory = new ExtendedDefaultDataSourceFactory(ApplicationLoader.applicationContext, "Mozilla/5.0 (X11; Linux x86_64; rv:10.0) Gecko/20150101 Firefox/47.0 (Chrome)");
        trackSelector = new DefaultTrackSelector(ApplicationLoader.applicationContext);
        if (audioDisabled) {
            trackSelector.setParameters(trackSelector.getParameters().buildUpon().setTrackTypeDisabled(TRACK_TYPE_AUDIO, true).build());
        }
        lastReportedPlaybackState = ExoPlayer.STATE_IDLE;
        shouldPauseOther = pauseOther;
        if (pauseOther) {
            NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.playerDidStartPlaying);
        }
        playerCounter++;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.playerDidStartPlaying) {
            VideoPlayer p = (VideoPlayer) args[0];
            if (p != this && isPlaying()) {
                pause();
            }
        }
    }

    private void ensurePlayerCreated() {
        DefaultLoadControl loadControl;
        if (isStory) {
            loadControl = new DefaultLoadControl(
                    new DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE),
                    DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                    DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
                    1000,
                    1000,
                    DefaultLoadControl.DEFAULT_TARGET_BUFFER_BYTES,
                    DefaultLoadControl.DEFAULT_PRIORITIZE_TIME_OVER_SIZE_THRESHOLDS,
                    DefaultLoadControl.DEFAULT_BACK_BUFFER_DURATION_MS,
                    DefaultLoadControl.DEFAULT_RETAIN_BACK_BUFFER_FROM_KEYFRAME);
        } else {
            loadControl = new DefaultLoadControl(
                    new DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE),
                    DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                    DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
                    100,
                    DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
                    DefaultLoadControl.DEFAULT_TARGET_BUFFER_BYTES,
                    DefaultLoadControl.DEFAULT_PRIORITIZE_TIME_OVER_SIZE_THRESHOLDS,
                    DefaultLoadControl.DEFAULT_BACK_BUFFER_DURATION_MS,
                    DefaultLoadControl.DEFAULT_RETAIN_BACK_BUFFER_FROM_KEYFRAME);
        }
        if (player == null) {
            DefaultRenderersFactory factory;
            if (audioVisualizerDelegate != null) {
                factory = new AudioVisualizerRenderersFactory(ApplicationLoader.applicationContext);
            } else {
                factory = new DefaultRenderersFactory(ApplicationLoader.applicationContext);
            }
            factory.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER);
            player = new ExoPlayer.Builder(ApplicationLoader.applicationContext).setRenderersFactory(factory)
                    .setTrackSelector(trackSelector)
                    .setLoadControl(loadControl).build();

            player.addAnalyticsListener(this);
            player.addListener(this);
            player.addVideoListener(this);
            if (textureView != null) {
                player.setVideoTextureView(textureView);
            } else if (surface != null) {
                player.setVideoSurface(surface);
            }
            player.setPlayWhenReady(autoplay);
            player.setRepeatMode(looping ? ExoPlayer.REPEAT_MODE_ALL : ExoPlayer.REPEAT_MODE_OFF);
        }
        if (mixedAudio) {
            if (audioPlayer == null) {
                audioPlayer = new ExoPlayer.Builder(ApplicationLoader.applicationContext)
                        .setTrackSelector(trackSelector)
                        .setLoadControl(loadControl).buildSimpleExoPlayer();
                audioPlayer.addListener(new Player.Listener() {

                    @Override
                    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
                        if (!audioPlayerReady && playbackState == Player.STATE_READY) {
                            audioPlayerReady = true;
                            checkPlayersReady();
                        }
                    }
                });
                audioPlayer.setPlayWhenReady(autoplay);
            }
        }
    }

    public void preparePlayerLoop(Uri videoUri, String videoType, Uri audioUri, String audioType) {
        this.videoUri = videoUri;
        this.audioUri = audioUri;
        this.videoType = videoType;
        this.audioType = audioType;
        this.loopingMediaSource = true;

        mixedAudio = true;
        audioPlayerReady = false;
        videoPlayerReady = false;
        ensurePlayerCreated();
        MediaSource mediaSource1 = null, mediaSource2 = null;
        for (int a = 0; a < 2; a++) {
            MediaSource mediaSource;
            String type;
            Uri uri;
            if (a == 0) {
                type = videoType;
                uri = videoUri;
            } else {
                type = audioType;
                uri = audioUri;
            }
            mediaSource = mediaSourceFromUri(uri, type);
            mediaSource = new LoopingMediaSource(mediaSource);
            if (a == 0) {
                mediaSource1 = mediaSource;
            } else {
                mediaSource2 = mediaSource;
            }
        }
        player.setMediaSource(mediaSource1, true);
        player.prepare();
        audioPlayer.setMediaSource(mediaSource2, true);
        audioPlayer.prepare();
    }

    private MediaSource mediaSourceFromUri(Uri uri, String type) {
        MediaItem mediaItem = new MediaItem.Builder().setUri(uri).build();
        switch (type) {
            case "dash":
                if (dashMediaSourceFactory == null) {
                    dashMediaSourceFactory = new DashMediaSource.Factory(mediaDataSourceFactory);
                }
                return dashMediaSourceFactory.createMediaSource(mediaItem);
            case "hls":
                if (hlsMediaSourceFactory == null) {
                    hlsMediaSourceFactory = new HlsMediaSource.Factory(mediaDataSourceFactory);
                }
                return hlsMediaSourceFactory.createMediaSource(mediaItem);
            case "ss":
                if (ssMediaSourceFactory == null) {
                    ssMediaSourceFactory = new SsMediaSource.Factory(mediaDataSourceFactory);
                }
                return ssMediaSourceFactory.createMediaSource(mediaItem);
            default:
                if (progressiveMediaSourceFactory == null) {
                    progressiveMediaSourceFactory = new ProgressiveMediaSource.Factory(mediaDataSourceFactory);
                }
                return progressiveMediaSourceFactory.createMediaSource(mediaItem);
        }
    }

    public void preparePlayer(Uri uri, String type) {
        preparePlayer(uri, type, FileLoader.PRIORITY_HIGH);
    }

    public void preparePlayer(Uri uri, String type, int priority) {
        this.videoUri = uri;
        this.videoType = type;
        this.audioUri = null;
        this.audioType = null;
        this.loopingMediaSource = false;

        videoPlayerReady = false;
        mixedAudio = false;
        currentUri = uri;
        String scheme = uri.getScheme();
        isStreaming = scheme != null && !scheme.startsWith("file");
        ensurePlayerCreated();
        MediaSource mediaSource = mediaSourceFromUri(uri, type);
        player.setMediaSource(mediaSource, true);
        player.prepare();
    }

    public boolean isPlayerPrepared() {
        return player != null;
    }

    public void releasePlayer(boolean async) {
        if (player != null) {
            player.release();
            player = null;
        }
        if (audioPlayer != null) {
            audioPlayer.release();
            audioPlayer = null;
        }
        if (shouldPauseOther) {
            NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.playerDidStartPlaying);
        }
        playerCounter--;
    }

    @Override
    public void onSeekStarted(EventTime eventTime) {
        if (delegate != null) {
            delegate.onSeekStarted(eventTime);
        }
    }

    @Override
    public void onSeekProcessed(EventTime eventTime) {
        if (delegate != null) {
            delegate.onSeekFinished(eventTime);
        }
    }

    @Override
    public void onRenderedFirstFrame(EventTime eventTime, Object output, long renderTimeMs) {
        if (delegate != null) {
            delegate.onRenderedFirstFrame(eventTime);
        }
    }

    public void setTextureView(TextureView texture) {
        if (textureView == texture) {
            return;
        }
        textureView = texture;
        if (player == null) {
            return;
        }
        player.setVideoTextureView(textureView);
    }

    public void setSurfaceView(SurfaceView surfaceView) {
        if (this.surfaceView == surfaceView) {
            return;
        }
        this.surfaceView = surfaceView;
        if (player == null) {
            return;
        }
        player.setVideoSurfaceView(surfaceView);
    }

    public void setSurface(Surface s) {
        if (surface == s) {
            return;
        }
        surface = s;
        if (player == null) {
            return;
        }
        player.setVideoSurface(surface);
    }

    public boolean getPlayWhenReady() {
        return player.getPlayWhenReady();
    }

    public int getPlaybackState() {
        return player.getPlaybackState();
    }

    public Uri getCurrentUri() {
        return currentUri;
    }

    public void play() {
        mixedPlayWhenReady = true;
        if (mixedAudio) {
            if (!audioPlayerReady || !videoPlayerReady) {
                if (player != null) {
                    player.setPlayWhenReady(false);
                }
                if (audioPlayer != null) {
                    audioPlayer.setPlayWhenReady(false);
                }
                return;
            }
        }
        if (player != null) {
            player.setPlayWhenReady(true);
        }
        if (audioPlayer != null) {
            audioPlayer.setPlayWhenReady(true);
        }
    }

    public void pause() {
        mixedPlayWhenReady = false;
        if (player != null) {
            player.setPlayWhenReady(false);
        }
        if (audioPlayer != null) {
            audioPlayer.setPlayWhenReady(false);
        }

        if (audioVisualizerDelegate != null) {
            audioUpdateHandler.removeCallbacksAndMessages(null);
            audioVisualizerDelegate.onVisualizerUpdate(false, true, null);
        }
    }

    public void setPlaybackSpeed(float speed) {
        if (player != null) {
            player.setPlaybackParameters(new PlaybackParameters(speed, speed > 1.0f ? 0.98f : 1.0f));
        }
    }

    public void setPlayWhenReady(boolean playWhenReady) {
        mixedPlayWhenReady = playWhenReady;
        if (playWhenReady && mixedAudio) {
            if (!audioPlayerReady || !videoPlayerReady) {
                if (player != null) {
                    player.setPlayWhenReady(false);
                }
                if (audioPlayer != null) {
                    audioPlayer.setPlayWhenReady(false);
                }
                return;
            }
        }
        autoplay = playWhenReady;
        if (player != null) {
            player.setPlayWhenReady(playWhenReady);
        }
        if (audioPlayer != null) {
            audioPlayer.setPlayWhenReady(playWhenReady);
        }
    }

    public long getDuration() {
        return player != null ? player.getDuration() : 0;
    }

    public long getCurrentPosition() {
        return player != null ? player.getCurrentPosition() : 0;
    }

    public boolean isMuted() {
        return player != null && player.getVolume() == 0.0f;
    }

    public void setMute(boolean value) {
        if (player != null) {
            player.setVolume(value ? 0.0f : 1.0f);
        }
        if (audioPlayer != null) {
            audioPlayer.setVolume(value ? 0.0f : 1.0f);
        }
    }

    @Override
    public void onRepeatModeChanged(int repeatMode) {

    }

    @Override
    public void onSurfaceSizeChanged(int width, int height) {

    }

    public void setVolume(float volume) {
        if (player != null) {
            player.setVolume(volume);
        }
        if (audioPlayer != null) {
            audioPlayer.setVolume(volume);
        }
    }

    public void seekTo(long positionMs) {
        if (player != null) {
            player.seekTo(positionMs);
        }
    }

    public void setDelegate(VideoPlayerDelegate videoPlayerDelegate) {
        delegate = videoPlayerDelegate;
    }

    public void setAudioVisualizerDelegate(AudioVisualizerDelegate audioVisualizerDelegate) {
        this.audioVisualizerDelegate = audioVisualizerDelegate;
    }

    public int getBufferedPercentage() {
        return isStreaming ? (player != null ? player.getBufferedPercentage() : 0) : 100;
    }

    public long getBufferedPosition() {
        return player != null ? (isStreaming ? player.getBufferedPosition() : player.getDuration()) : 0;
    }

    public boolean isStreaming() {
        return isStreaming;
    }

    public boolean isPlaying() {
        return mixedAudio && mixedPlayWhenReady || player != null && player.getPlayWhenReady();
    }

    public boolean isBuffering() {
        return player != null && lastReportedPlaybackState == ExoPlayer.STATE_BUFFERING;
    }

    public void setStreamType(int type) {
        if (player != null) {
            player.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(type == AudioManager.STREAM_VOICE_CALL ? C.USAGE_VOICE_COMMUNICATION : C.USAGE_MEDIA)
                .build(), false);
        }
        if (audioPlayer != null) {
            audioPlayer.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(type == AudioManager.STREAM_VOICE_CALL ? C.USAGE_VOICE_COMMUNICATION : C.USAGE_MEDIA)
                .build(), true);
        }
    }

    public void setLooping(boolean looping) {
        if (this.looping != looping) {
            this.looping = looping;
            if (player != null) {
                player.setRepeatMode(looping ? ExoPlayer.REPEAT_MODE_ALL : ExoPlayer.REPEAT_MODE_OFF);
            }
        }
    }

    public boolean isLooping() {
        return looping;
    }

    private void checkPlayersReady() {
        if (audioPlayerReady && videoPlayerReady && mixedPlayWhenReady) {
            play();
        }
    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        maybeReportPlayerState();
        if (playWhenReady && playbackState == Player.STATE_READY && !isMuted() && shouldPauseOther) {
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.playerDidStartPlaying, this);
        }
        if (!videoPlayerReady && playbackState == Player.STATE_READY) {
            videoPlayerReady = true;
            checkPlayersReady();
        }
        if (playbackState != Player.STATE_READY) {
            audioUpdateHandler.removeCallbacksAndMessages(null);
            if (audioVisualizerDelegate != null) {
                audioVisualizerDelegate.onVisualizerUpdate(false, true, null);
            }
        }
    }

    @Override
    public void onPositionDiscontinuity(Player.PositionInfo oldPosition, Player.PositionInfo newPosition, @Player.DiscontinuityReason int reason) {
        if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
            repeatCount++;
        }
    }

    @Override
    public void onPlayerError(PlaybackException error) {
        AndroidUtilities.runOnUIThread(() -> {
            Throwable cause = error.getCause();
            if (textureView != null && (!triedReinit && cause instanceof MediaCodecRenderer.DecoderInitializationException || cause instanceof SurfaceNotValidException)) {
                triedReinit = true;
                if (player != null) {
                    ViewGroup parent = (ViewGroup) textureView.getParent();
                    if (parent != null) {
                        int i = parent.indexOfChild(textureView);
                        parent.removeView(textureView);
                        parent.addView(textureView, i);
                    }
                    if (workerQueue != null) {
                        workerQueue.postRunnable(() -> {
                            if (player != null) {
                                player.clearVideoTextureView(textureView);
                                player.setVideoTextureView(textureView);
                                if (loopingMediaSource) {
                                    preparePlayerLoop(videoUri, videoType, audioUri, audioType);
                                } else {
                                    preparePlayer(videoUri, videoType);
                                }
                                play();
                            }
                        });
                    } else {
                        player.clearVideoTextureView(textureView);
                        player.setVideoTextureView(textureView);
                        if (loopingMediaSource) {
                            preparePlayerLoop(videoUri, videoType, audioUri, audioType);
                        } else {
                            preparePlayer(videoUri, videoType);
                        }
                        play();
                    }
                }
            } else {
                delegate.onError(this, error);
            }
        });
    }

    public VideoSize getVideoSize() {
        return player != null ? player.getVideoSize() : null;
    }

    @Override
    public void onVideoSizeChanged(VideoSize videoSize) {
        delegate.onVideoSizeChanged(videoSize.width, videoSize.height, videoSize.unappliedRotationDegrees, videoSize.pixelWidthHeightRatio);
        Player.Listener.super.onVideoSizeChanged(videoSize);
    }

    @Override
    public void onRenderedFirstFrame() {
        delegate.onRenderedFirstFrame();
    }

    @Override
    public boolean onSurfaceDestroyed(SurfaceTexture surfaceTexture) {
        return delegate.onSurfaceDestroyed(surfaceTexture);
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
        delegate.onSurfaceTextureUpdated(surfaceTexture);
    }

    @Override
    public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {

    }

    private void maybeReportPlayerState() {
        if (player == null) {
            return;
        }
        boolean playWhenReady = player.getPlayWhenReady();
        int playbackState = player.getPlaybackState();
        if (lastReportedPlayWhenReady != playWhenReady || lastReportedPlaybackState != playbackState) {
            delegate.onStateChanged(playWhenReady, playbackState);
            lastReportedPlayWhenReady = playWhenReady;
            lastReportedPlaybackState = playbackState;
        }
    }

    public int getRepeatCount() {
        return repeatCount;
    }

    private class AudioVisualizerRenderersFactory extends DefaultRenderersFactory {

        public AudioVisualizerRenderersFactory(Context context) {
            super(context);
        }

        @Nullable
        @Override
        protected AudioSink buildAudioSink(Context context, boolean enableFloatOutput, boolean enableAudioTrackPlaybackParams, boolean enableOffload) {
            return new DefaultAudioSink.Builder()
                    .setAudioCapabilities(AudioCapabilities.getCapabilities(context))
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(new AudioProcessor[] {new TeeAudioProcessor(new VisualizerBufferSink())})
                    .setOffloadMode(
                            enableOffload
                                    ? DefaultAudioSink.OFFLOAD_MODE_ENABLED_GAPLESS_REQUIRED
                                    : DefaultAudioSink.OFFLOAD_MODE_DISABLED)
                    .build();
        }
    }

    private class VisualizerBufferSink implements TeeAudioProcessor.AudioBufferSink {

        private final int BUFFER_SIZE = 1024;
        private final int MAX_BUFFER_SIZE = BUFFER_SIZE * 8;
        FourierTransform.FFT fft = new FourierTransform.FFT(BUFFER_SIZE, 48000);
        float[] real = new float[BUFFER_SIZE];
        ByteBuffer byteBuffer;
        int position = 0;

        public VisualizerBufferSink() {
            byteBuffer = ByteBuffer.allocateDirect(MAX_BUFFER_SIZE);
            byteBuffer.position(0);
        }

        @Override
        public void flush(int sampleRateHz, int channelCount, int encoding) {
            
        }


        long lastUpdateTime;

        @Override
        public void handleBuffer(ByteBuffer buffer) {
            if (audioVisualizerDelegate == null) {
                return;
            }
            if (buffer == AudioProcessor.EMPTY_BUFFER || !mixedPlayWhenReady) {
                audioUpdateHandler.postDelayed(() -> {
                    audioUpdateHandler.removeCallbacksAndMessages(null);
                    audioVisualizerDelegate.onVisualizerUpdate(false, true, null);
                }, 80);
                return;
            }

            if (!audioVisualizerDelegate.needUpdate()) {
                return;
            }

            int len = buffer.limit();
            if (len > MAX_BUFFER_SIZE) {
                audioUpdateHandler.removeCallbacksAndMessages(null);
                audioVisualizerDelegate.onVisualizerUpdate(false, true, null);
                return;
//                len = MAX_BUFFER_SIZE;
//                byte[] bytes = new byte[BUFFER_SIZE];
//                buffer.get(bytes);
//                byteBuffer.put(bytes, 0, BUFFER_SIZE);
            } else {
                byteBuffer.put(buffer);
            }
            position += len;

            if (position >= BUFFER_SIZE) {
                len = BUFFER_SIZE;
                byteBuffer.position(0);
                for (int i = 0; i < len; i++) {
                    real[i] = (byteBuffer.getShort()) / 32768.0F;
                }
                byteBuffer.rewind();
                position = 0;

                fft.forward(real);
                float sum = 0;
                for (int i = 0; i < len; i++) {
                    float r = fft.getSpectrumReal()[i];
                    float img = fft.getSpectrumImaginary()[i];
                    float peak = (float) Math.sqrt(r * r + img * img) / 30f;
                    if (peak > 1f) {
                        peak = 1f;
                    } else if (peak < 0) {
                        peak = 0;
                    }
                    sum += peak * peak;
                }
                float amplitude = (float) (Math.sqrt(sum / len));

                float[] partsAmplitude = new float[7];
                partsAmplitude[6] = amplitude;
                if (amplitude < 0.4f) {
                    for (int k = 0; k < 7; k++) {
                        partsAmplitude[k] = 0;
                    }
                } else {
                    int part = len / 6;

                    for (int k = 0; k < 6; k++) {
                        int start = part * k;
                        float r = fft.getSpectrumReal()[start];
                        float img = fft.getSpectrumImaginary()[start];
                        partsAmplitude[k] = (float) (Math.sqrt(r * r + img * img) / 30f);

                        if (partsAmplitude[k] > 1f) {
                            partsAmplitude[k] = 1f;
                        } else if (partsAmplitude[k] < 0) {
                            partsAmplitude[k] = 0;
                        }
                    }
                }

                int updateInterval = 64;

                if (System.currentTimeMillis() - lastUpdateTime < updateInterval) {
                    return;
                }
                lastUpdateTime = System.currentTimeMillis();

                audioUpdateHandler.postDelayed(() -> audioVisualizerDelegate.onVisualizerUpdate(true, true, partsAmplitude), 130);
            }
        }
    }

    public boolean isHDR() {
        if (player == null) {
            return false;
        }
        try {
            Format format = player.getVideoFormat();
            if (format == null || format.colorInfo == null) {
                return false;
            }
            return (
                format.colorInfo.colorTransfer == C.COLOR_TRANSFER_ST2084 ||
                format.colorInfo.colorTransfer == C.COLOR_TRANSFER_HLG
            );
        } catch (Exception ignore) {}
        return false;
    }


    public StoryEntry.HDRInfo getHDRStaticInfo(StoryEntry.HDRInfo hdrInfo) {
        if (hdrInfo == null) {
            hdrInfo = new StoryEntry.HDRInfo();
        }
        try {
            MediaFormat mediaFormat = ((MediaCodecRenderer) player.getRenderer(0)).codecOutputMediaFormat;
            ByteBuffer byteBuffer = mediaFormat.getByteBuffer(MediaFormat.KEY_HDR_STATIC_INFO);
            byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
            if (byteBuffer.get() == 0) {
                hdrInfo.maxlum = byteBuffer.getShort(17);
                hdrInfo.minlum = byteBuffer.getShort(19) * 0.0001f;
            }
        } catch (Exception ignore) {
            hdrInfo.maxlum = hdrInfo.minlum = 0;
        }
        return hdrInfo;
    }

    public void setWorkerQueue(DispatchQueue dispatchQueue) {
        workerQueue = dispatchQueue;
        player.setWorkerQueue(dispatchQueue);
    }

    public void setIsStory() {
        isStory = true;
    }
}
