/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui.Components;

import android.annotation.SuppressLint;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.os.Handler;
import android.view.TextureView;

import org.telegram.messenger.secretmedia.ExtendedDefaultDataSourceFactory;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.exoplayer2.DefaultLoadControl;
import org.telegram.messenger.exoplayer2.DefaultRenderersFactory;
import org.telegram.messenger.exoplayer2.ExoPlaybackException;
import org.telegram.messenger.exoplayer2.ExoPlayer;
import org.telegram.messenger.exoplayer2.ExoPlayerFactory;
import org.telegram.messenger.exoplayer2.PlaybackParameters;
import org.telegram.messenger.exoplayer2.SimpleExoPlayer;
import org.telegram.messenger.exoplayer2.Timeline;
import org.telegram.messenger.exoplayer2.extractor.DefaultExtractorsFactory;
import org.telegram.messenger.exoplayer2.source.ExtractorMediaSource;
import org.telegram.messenger.exoplayer2.source.LoopingMediaSource;
import org.telegram.messenger.exoplayer2.source.MediaSource;
import org.telegram.messenger.exoplayer2.source.MergingMediaSource;
import org.telegram.messenger.exoplayer2.source.TrackGroupArray;
import org.telegram.messenger.exoplayer2.source.dash.DashMediaSource;
import org.telegram.messenger.exoplayer2.source.dash.DefaultDashChunkSource;
import org.telegram.messenger.exoplayer2.source.hls.HlsMediaSource;
import org.telegram.messenger.exoplayer2.source.smoothstreaming.DefaultSsChunkSource;
import org.telegram.messenger.exoplayer2.source.smoothstreaming.SsMediaSource;
import org.telegram.messenger.exoplayer2.trackselection.AdaptiveTrackSelection;
import org.telegram.messenger.exoplayer2.trackselection.DefaultTrackSelector;
import org.telegram.messenger.exoplayer2.trackselection.MappingTrackSelector;
import org.telegram.messenger.exoplayer2.trackselection.TrackSelection;
import org.telegram.messenger.exoplayer2.trackselection.TrackSelectionArray;
import org.telegram.messenger.exoplayer2.upstream.DataSource;
import org.telegram.messenger.exoplayer2.upstream.DefaultBandwidthMeter;
import org.telegram.messenger.exoplayer2.upstream.DefaultHttpDataSourceFactory;

@SuppressLint("NewApi")
public class VideoPlayer implements ExoPlayer.EventListener, SimpleExoPlayer.VideoListener {

    public interface RendererBuilder {
        void buildRenderers(VideoPlayer player);
        void cancel();
    }

    public interface VideoPlayerDelegate {
        void onStateChanged(boolean playWhenReady, int playbackState);
        void onError(Exception e);
        void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio);
        void onRenderedFirstFrame();
        void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture);
        boolean onSurfaceDestroyed(SurfaceTexture surfaceTexture);
    }

    private SimpleExoPlayer player;
    private MappingTrackSelector trackSelector;
    private Handler mainHandler;
    private DataSource.Factory mediaDataSourceFactory;
    private TextureView textureView;
    private boolean autoplay;

    private VideoPlayerDelegate delegate;
    private int lastReportedPlaybackState;
    private boolean lastReportedPlayWhenReady;

    private static final int RENDERER_BUILDING_STATE_IDLE = 1;
    private static final int RENDERER_BUILDING_STATE_BUILDING = 2;
    private static final int RENDERER_BUILDING_STATE_BUILT = 3;

    private static final DefaultBandwidthMeter BANDWIDTH_METER = new DefaultBandwidthMeter();

    public VideoPlayer() {
        mediaDataSourceFactory = new ExtendedDefaultDataSourceFactory(ApplicationLoader.applicationContext, BANDWIDTH_METER, new DefaultHttpDataSourceFactory("Mozilla/5.0 (X11; Linux x86_64; rv:10.0) Gecko/20150101 Firefox/47.0 (Chrome)", BANDWIDTH_METER));

        mainHandler = new Handler();

        TrackSelection.Factory videoTrackSelectionFactory = new AdaptiveTrackSelection.Factory(BANDWIDTH_METER);
        trackSelector = new DefaultTrackSelector(videoTrackSelectionFactory);

        lastReportedPlaybackState = ExoPlayer.STATE_IDLE;
    }

    private void ensurePleyaerCreated() {
        if (player == null) {
            player = ExoPlayerFactory.newSimpleInstance(ApplicationLoader.applicationContext, trackSelector, new DefaultLoadControl(), null, DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF);
            player.addListener(this);
            player.setVideoListener(this);
            player.setVideoTextureView(textureView);
            player.setPlayWhenReady(autoplay);
        }
    }

    public void preparePlayerLoop(Uri videoUri, String videoType, Uri audioUri, String audioType) {
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
                    mediaSource = new HlsMediaSource(uri, mediaDataSourceFactory, mainHandler, null);
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
        MediaSource mediaSource = new MergingMediaSource(mediaSource1, mediaSource2);
        player.prepare(mediaSource1, true, true);
    }

    public void preparePlayer(Uri uri, String type) {
        ensurePleyaerCreated();
        MediaSource mediaSource;
        switch (type) {
            case "dash":
                mediaSource = new DashMediaSource(uri, mediaDataSourceFactory, new DefaultDashChunkSource.Factory(mediaDataSourceFactory), mainHandler, null);
                break;
            case "hls":
                mediaSource = new HlsMediaSource(uri, mediaDataSourceFactory, mainHandler, null);
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

    public void releasePlayer() {
        if (player != null) {
            player.release();
            player = null;
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

    public void play() {
        if (player == null) {
            return;
        }
        player.setPlayWhenReady(true);
    }

    public void pause() {
        if (player == null) {
            return;
        }
        player.setPlayWhenReady(false);
    }

    public void setPlayWhenReady(boolean playWhenReady) {
        autoplay = playWhenReady;
        if (player == null) {
            return;
        }
        player.setPlayWhenReady(playWhenReady);
    }

    public long getDuration() {
        return player != null ? player.getDuration() : 0;
    }

    public long getCurrentPosition() {
        return player != null ? player.getCurrentPosition() : 0;
    }

    public boolean isMuted() {
        return player.getVolume() == 0.0f;
    }

    public void setMute(boolean value) {
        if (player == null) {
            return;
        }
        if (value) {
            player.setVolume(0.0f);
        } else {
            player.setVolume(1.0f);
        }
    }

    public void setVolume(float volume) {
        if (player == null) {
            return;
        }
        player.setVolume(volume);
    }

    public void seekTo(long positionMs) {
        if (player == null) {
            return;
        }
        player.seekTo(positionMs);
    }

    public void setDelegate(VideoPlayerDelegate videoPlayerDelegate) {
        delegate = videoPlayerDelegate;
    }

    public int getBufferedPercentage() {
        return player != null ? player.getBufferedPercentage() : 0;
    }

    public long getBufferedPosition() {
        return player != null ? player.getBufferedPosition() : 0;
    }

    public boolean isPlaying() {
        return player != null && player.getPlayWhenReady();
    }

    public boolean isBuffering() {
        return player != null && lastReportedPlaybackState == ExoPlayer.STATE_BUFFERING;
    }

    public void setStreamType(int type) {
        if (player != null) {
            player.setAudioStreamType(type);
        }
    }

    @Override
    public void onLoadingChanged(boolean isLoading) {

    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        maybeReportPlayerState();
    }

    @Override
    public void onTimelineChanged(Timeline timeline, Object manifest) {

    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
        delegate.onError(error);
    }

    @Override
    public void onPositionDiscontinuity() {

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
        boolean playWhenReady = player.getPlayWhenReady();
        int playbackState = player.getPlaybackState();
        if (lastReportedPlayWhenReady != playWhenReady || lastReportedPlaybackState != playbackState) {
            delegate.onStateChanged(playWhenReady, playbackState);
            lastReportedPlayWhenReady = playWhenReady;
            lastReportedPlaybackState = playbackState;
        }
    }
}
