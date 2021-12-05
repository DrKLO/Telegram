/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import android.view.TextureView;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.audio.AudioProcessor;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.audio.TeeAudioProcessor;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.mediacodec.MediaCodecRenderer;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.LoopingMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.smoothstreaming.DefaultSsChunkSource;
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultAllocator;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.video.SurfaceNotValidException;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FourierTransform;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.secretmedia.ExtendedDefaultDataSourceFactory;

import java.nio.ByteBuffer;
import java.util.ArrayList;

@SuppressLint("NewApi")
public class VideoPlayer implements ExoPlayer.EventListener, SimpleExoPlayer.VideoListener, AnalyticsListener, NotificationCenter.NotificationCenterDelegate {

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

    private SimpleExoPlayer player;
    private SimpleExoPlayer audioPlayer;
    private MappingTrackSelector trackSelector;
    private Handler mainHandler;
    private DataSource.Factory mediaDataSourceFactory;
    private TextureView textureView;
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

    private static final int RENDERER_BUILDING_STATE_IDLE = 1;
    private static final int RENDERER_BUILDING_STATE_BUILDING = 2;
    private static final int RENDERER_BUILDING_STATE_BUILT = 3;

    private Uri videoUri, audioUri;
    private String videoType, audioType;
    private boolean loopingMediaSource;
    private boolean looping;
    private int repeatCount;

    private boolean shouldPauseOther;

    Handler audioUpdateHandler = new Handler(Looper.getMainLooper());

    private static final DefaultBandwidthMeter BANDWIDTH_METER = new DefaultBandwidthMeter();

    public VideoPlayer() {
        this(true);
    }

    public VideoPlayer(boolean pauseOther) {
        mediaDataSourceFactory = new ExtendedDefaultDataSourceFactory(ApplicationLoader.applicationContext, BANDWIDTH_METER, new DefaultHttpDataSourceFactory("Mozilla/5.0 (X11; Linux x86_64; rv:10.0) Gecko/20150101 Firefox/47.0 (Chrome)", BANDWIDTH_METER));

        mainHandler = new Handler();

        TrackSelection.Factory videoTrackSelectionFactory = new AdaptiveTrackSelection.Factory(BANDWIDTH_METER);
        trackSelector = new DefaultTrackSelector(videoTrackSelectionFactory);

        lastReportedPlaybackState = ExoPlayer.STATE_IDLE;
        shouldPauseOther = pauseOther;
        if (pauseOther) {
            NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.playerDidStartPlaying);
        }
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

    private void ensurePleyaerCreated() {
        DefaultLoadControl loadControl = new DefaultLoadControl(
                new DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE),
                DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
                100,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
                DefaultLoadControl.DEFAULT_TARGET_BUFFER_BYTES,
                DefaultLoadControl.DEFAULT_PRIORITIZE_TIME_OVER_SIZE_THRESHOLDS);
        if (player == null) {
            DefaultRenderersFactory factory;
            if (audioVisualizerDelegate != null) {
                factory = new AudioVisualizerRenderersFactory(ApplicationLoader.applicationContext);
            } else {
                factory = new DefaultRenderersFactory(ApplicationLoader.applicationContext);
            }
            factory.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER);
            player = ExoPlayerFactory.newSimpleInstance(ApplicationLoader.applicationContext, factory, trackSelector, loadControl, null);

            player.addAnalyticsListener(this);
            player.addListener(this);
            player.setVideoListener(this);
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
                audioPlayer = ExoPlayerFactory.newSimpleInstance(ApplicationLoader.applicationContext, trackSelector, loadControl, null, DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER);
                audioPlayer.addListener(new Player.EventListener() {

                    @Override
                    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {

                    }

                    @Override
                    public void onLoadingChanged(boolean isLoading) {

                    }

                    @Override
                    public void onTimelineChanged(Timeline timeline, Object manifest, int reason) {

                    }

                    @Override
                    public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {

                    }

                    @Override
                    public void onPositionDiscontinuity(int reason) {

                    }

                    @Override
                    public void onSeekProcessed() {

                    }

                    @Override
                    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
                        if (!audioPlayerReady && playbackState == Player.STATE_READY) {
                            audioPlayerReady = true;
                            checkPlayersReady();
                        }
                    }

                    @Override
                    public void onRepeatModeChanged(int repeatMode) {

                    }

                    @Override
                    public void onPlayerError(ExoPlaybackException error) {

                    }

                    @Override
                    public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {

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
        ensurePleyaerCreated();
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
            switch (type) {
                case "dash":
                    mediaSource = new DashMediaSource(uri, mediaDataSourceFactory, new DefaultDashChunkSource.Factory(mediaDataSourceFactory), mainHandler, null);
                    break;
                case "hls":
                    mediaSource = new HlsMediaSource.Factory(mediaDataSourceFactory).createMediaSource(uri);
                    break;
                case "ss":
                    mediaSource = new SsMediaSource(uri, mediaDataSourceFactory, new DefaultSsChunkSource.Factory(mediaDataSourceFactory), mainHandler, null);
                    break;
                default:
                    mediaSource = new ExtractorMediaSource(uri, mediaDataSourceFactory, new DefaultExtractorsFactory(), mainHandler, null);
                    break;
            }
            mediaSource = new LoopingMediaSource(mediaSource);
            if (a == 0) {
                mediaSource1 = mediaSource;
            } else {
                mediaSource2 = mediaSource;
            }
        }
        player.prepare(mediaSource1, true, true);
        audioPlayer.prepare(mediaSource2, true, true);
    }

    public void preparePlayer(Uri uri, String type) {
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
        ensurePleyaerCreated();
        MediaSource mediaSource;
        switch (type) {
            case "dash":
                mediaSource = new DashMediaSource(uri, mediaDataSourceFactory, new DefaultDashChunkSource.Factory(mediaDataSourceFactory), mainHandler, null);
                break;
            case "hls":
                mediaSource = new HlsMediaSource.Factory(mediaDataSourceFactory).createMediaSource(uri);
                break;
            case "ss":
                mediaSource = new SsMediaSource(uri, mediaDataSourceFactory, new DefaultSsChunkSource.Factory(mediaDataSourceFactory), mainHandler, null);
                break;
            default:
                mediaSource = new ExtractorMediaSource(uri, mediaDataSourceFactory, new DefaultExtractorsFactory(), mainHandler, null);
                break;
        }
        player.prepare(mediaSource, true, true);
    }

    public boolean isPlayerPrepared() {
        return player != null;
    }

    public void releasePlayer(boolean async) {
        if (player != null) {
            player.release(async);
            player = null;
        }
        if (audioPlayer != null) {
            audioPlayer.release(async);
            audioPlayer = null;
        }
        if (shouldPauseOther) {
            NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.playerDidStartPlaying);
        }
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
    public void onRenderedFirstFrame(EventTime eventTime, Surface surface) {
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
            player.setAudioStreamType(type);
        }
        if (audioPlayer != null) {
            audioPlayer.setAudioStreamType(type);
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
    public void onLoadingChanged(boolean isLoading) {

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
    public void onTimelineChanged(Timeline timeline, Object manifest, int reason) {

    }

    @Override
    public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {

    }

    @Override
    public void onPositionDiscontinuity(int reason) {
        if (reason == Player.DISCONTINUITY_REASON_PERIOD_TRANSITION) {
            repeatCount++;
        }
    }

    @Override
    public void onSeekProcessed() {

    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
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
                player.clearVideoTextureView(textureView);
                player.setVideoTextureView(textureView);
                if (loopingMediaSource) {
                    preparePlayerLoop(videoUri, videoType, audioUri, audioType);
                } else {
                    preparePlayer(videoUri, videoType);
                }
                play();
            }
        } else {
            delegate.onError(this, error);
        }
    }

    @Override
    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {

    }

    @Override
    public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
        delegate.onVideoSizeChanged(width, height, unappliedRotationDegrees, pixelWidthHeightRatio);
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

        @Override
        protected void buildAudioRenderers(Context context, int extensionRendererMode, MediaCodecSelector mediaCodecSelector, @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, boolean playClearSamplesWithoutKeys, boolean enableDecoderFallback, AudioProcessor[] audioProcessors, Handler eventHandler, AudioRendererEventListener eventListener, ArrayList<Renderer> out) {
            AudioProcessor audioProcessor = new TeeAudioProcessor(new VisualizerBufferSink());
            super.buildAudioRenderers(context, extensionRendererMode, mediaCodecSelector, drmSessionManager, playClearSamplesWithoutKeys, enableDecoderFallback, new AudioProcessor[]{audioProcessor}, eventHandler, eventListener, out);
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
}
