/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.SurfaceTexture;
import android.media.AudioManager;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.net.Uri;
import android.opengl.EGLContext;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.util.LongSparseArray;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
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
import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.Tracks;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.audio.AudioCapabilities;
import com.google.android.exoplayer2.audio.AudioProcessor;
import com.google.android.exoplayer2.audio.AudioSink;
import com.google.android.exoplayer2.audio.DefaultAudioSink;
import com.google.android.exoplayer2.audio.TeeAudioProcessor;
import com.google.android.exoplayer2.mediacodec.MediaCodecDecoderException;
import com.google.android.exoplayer2.mediacodec.MediaCodecRenderer;
import com.google.android.exoplayer2.mediacodec.MediaCodecUtil;
import com.google.android.exoplayer2.source.LoopingMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectionOverride;
import com.google.android.exoplayer2.trackselection.TrackSelectionParameters;
import com.google.android.exoplayer2.upstream.DefaultAllocator;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.video.SurfaceNotValidException;
import com.google.android.exoplayer2.video.VideoListener;
import com.google.android.exoplayer2.video.VideoSize;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.FourierTransform;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.secretmedia.ExtendedDefaultDataSourceFactory;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Stories.recorder.StoryEntry;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;

@SuppressLint("NewApi")
public class VideoPlayer implements Player.Listener, VideoListener, AnalyticsListener, NotificationCenter.NotificationCenterDelegate {

    private static int lastPlayerId = 0;
    private int playerId = lastPlayerId++;
    public static final HashSet<Integer> activePlayers = new HashSet<>();

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
        default void onSeekFinished(AnalyticsListener.EventTime eventTime) {

        }
    }

    public interface AudioVisualizerDelegate {
        void onVisualizerUpdate(boolean playing, boolean animate, float[] values);
        boolean needUpdate();
    }

    public ExoPlayer player;
    private ExoPlayer audioPlayer;
    private DefaultBandwidthMeter bandwidthMeter;
    private MappingTrackSelector trackSelector;
    private ExtendedDefaultDataSourceFactory mediaDataSourceFactory;
    private TextureView textureView;
    private SurfaceView surfaceView;
    private Surface surface;
    private boolean isStreaming;
    private boolean autoplay;
    private boolean mixedAudio;
    public boolean allowMultipleInstances;

    private boolean triedReinit;

    private Uri currentUri;

    private boolean videoPlayerReady;
    private boolean audioPlayerReady;
    private boolean mixedPlayWhenReady;

    private VideoPlayerDelegate delegate;
    private AudioVisualizerDelegate audioVisualizerDelegate;
    private int lastReportedPlaybackState;
    private boolean lastReportedPlayWhenReady;

    private ArrayList<Quality> videoQualities;
    private Quality videoQualityToSelect;
    private ArrayList<VideoUri> manifestUris;
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
        trackSelector = new DefaultTrackSelector(ApplicationLoader.applicationContext, new AdaptiveTrackSelection.Factory());
        if (audioDisabled) {
            trackSelector.setParameters(trackSelector.getParameters().buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true).build());
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
            if (p != this && isPlaying() && !allowMultipleInstances) {
                pause();
            }
        }
    }

    private Looper looper;
    public void setLooper(Looper looper) {
        this.looper = looper;
    }

    private EGLContext eglParentContext;
    public void setEGLContext(EGLContext ctx) {
        eglParentContext = ctx;
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
            ExoPlayer.Builder builder = new ExoPlayer.Builder(ApplicationLoader.applicationContext).setRenderersFactory(factory)
                    .setTrackSelector(trackSelector)
                    .setLoadControl(loadControl);
            if (looper != null) {
                builder.setLooper(looper);
            }
            if (eglParentContext != null) {
                builder.eglContext = eglParentContext;
            }
            player = builder.build();

            player.addAnalyticsListener(this);
            player.addListener(this);
            player.addVideoListener(this);
            if (textureView != null) {
                player.setVideoTextureView(textureView);
            } else if (surface != null) {
                player.setVideoSurface(surface);
            } else if (surfaceView != null) {
                player.setVideoSurfaceView(surfaceView);
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
        this.videoQualities = null;
        this.videoQualityToSelect = null;
        this.videoUri = videoUri;
        this.audioUri = audioUri;
        this.videoType = videoType;
        this.audioType = audioType;
        this.loopingMediaSource = true;
        currentStreamIsHls = false;

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
        activePlayers.add(playerId);
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
        this.videoQualities = null;
        this.videoQualityToSelect = null;
        this.videoUri = uri;
        this.videoType = type;
        this.audioUri = null;
        this.audioType = null;
        this.loopingMediaSource = false;
        this.autoIsOriginal = false;
        this.currentStreamIsHls = false;

        videoPlayerReady = false;
        mixedAudio = false;
        currentUri = uri;
        String scheme = uri != null ? uri.getScheme() : null;
        isStreaming = scheme != null && !scheme.startsWith("file");
        ensurePlayerCreated();
        MediaSource mediaSource = mediaSourceFromUri(uri, type);
        player.setMediaSource(mediaSource, true);
        player.prepare();
    }

    public void preparePlayer(ArrayList<Quality> qualities, Quality select) {
        this.videoQualities = qualities;
        this.videoQualityToSelect = select;
        this.videoUri = null;
        this.videoType = "hls";
        this.audioUri = null;
        this.audioType = null;
        this.loopingMediaSource = false;
        this.autoIsOriginal = false;

        videoPlayerReady = false;
        mixedAudio = false;
        currentUri = null;
        isStreaming = true;
        ensurePlayerCreated();

        currentStreamIsHls = false;
        selectedQualityIndex = select == null || videoQualities == null ? QUALITY_AUTO : videoQualities.indexOf(select);
        setSelectedQuality(true, select);
        if (autoIsOriginal) {
            selectedQualityIndex = QUALITY_AUTO;
        }
    }

    public static Quality getSavedQuality(ArrayList<Quality> qualities, MessageObject messageObject) {
        if (messageObject == null) return null;
        return getSavedQuality(qualities, messageObject.getDialogId(), messageObject.getId());
    }

    public static Quality getSavedQuality(ArrayList<Quality> qualities, long did, int mid) {
        final SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("media_saved_pos", Activity.MODE_PRIVATE);
        final String setting = preferences.getString(did + "_" + mid + "q2", "");
        if (TextUtils.isEmpty(setting)) return null;
        for (Quality q : qualities) {
            final String idx = q.width + "x" + q.height + (q.original ? "s" : "");
            if (TextUtils.equals(setting, idx)) return q;
        }
        return null;
    }

    public static void saveQuality(Quality q, MessageObject messageObject) {
        if (messageObject == null) return;
        saveQuality(q, messageObject.getDialogId(), messageObject.getId());
    }

    public static void saveQuality(Quality q, long did, int mid) {
        final SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("media_saved_pos", Activity.MODE_PRIVATE);
        final SharedPreferences.Editor editor = preferences.edit();
        if (q == null) {
            editor.remove(did + "_" + mid + "q2");
        } else {
            editor.putString(did + "_" + mid + "q2", q.width + "x" + q.height + (q.original ? "s" : ""));
        }
        editor.apply();
    }

    public static void saveLooping(boolean looping, MessageObject messageObject) {
        if (messageObject == null) return;
        final SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("media_saved_pos", Activity.MODE_PRIVATE);
        final String key = messageObject.getDialogId() + "_" + messageObject.getId() + "loop";
        preferences.edit().putBoolean(key, looping).apply();
    }

    public static Boolean getLooping(MessageObject messageObject) {
        if (messageObject == null) return null;
        final SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("media_saved_pos", Activity.MODE_PRIVATE);
        final String key = messageObject.getDialogId() + "_" + messageObject.getId() + "loop";
        if (!preferences.contains(key)) return null;
        return preferences.getBoolean(key, false);
    }

    public static final int QUALITY_AUTO = -1; // HLS
    private boolean autoIsOriginal = false;
    private int selectedQualityIndex = QUALITY_AUTO;
    private boolean currentStreamIsHls;

    public Quality getQuality(int index) {
        if (videoQualities == null) return getHighestQuality(false);
        if (index < 0 || index >= videoQualities.size()) return getHighestQuality(false);
        return videoQualities.get(index);
    }

    public Quality getOriginalQuality() {
        for (int i = 0; i < getQualitiesCount(); ++i) {
            final Quality q = getQuality(i);
            if (q.original) return q;
        }
        return null;
    }

    public Quality getHighestQuality(Boolean original) {
        Quality max = null;
        for (int i = 0; i < getQualitiesCount(); ++i) {
            final Quality q = getQuality(i);
            if (original != null && q.original != original) continue;
            if (max == null || max.width * max.height < q.width * q.height) {
                max = q;
            }
        }
        return max;
    }

    public int getHighestQualityIndex(Boolean original) {
        int maxIndex = -1;
        Quality max = null;
        for (int i = 0; i < getQualitiesCount(); ++i) {
            final Quality q = getQuality(i);
            if (original != null && q.original != original) continue;
            if (max == null || max.width * max.height < q.width * q.height) {
                max = q;
                maxIndex = i;
            }
        }
        return maxIndex;
    }

    public Quality getLowestQuality() {
        Quality min = null;
        for (int i = 0; i < getQualitiesCount(); ++i) {
            final Quality q = getQuality(i);
            if (min == null || min.width * min.height > q.width * q.height) {
                min = q;
            }
        }
        return min;
    }

    public int getQualitiesCount() {
        if (videoQualities == null) return 0;
        return videoQualities.size();
    }

    public File getFile() {
        if (videoQualities != null) {
            for (Quality q : videoQualities) {
                for (VideoUri v : q.uris) {
                    if (v.isCached())
                        return new File(v.uri.getPath());
                }
            }
        }
        if (videoUri != null) {
            if ("file".equalsIgnoreCase(videoUri.getScheme()))
                return new File(videoUri.getPath());
        }
        return null;
    }

    public File getLowestFile() {
        if (videoQualities != null) {
            for (int i = videoQualities.size() - 1; i >= 0; --i) {
                Quality q = videoQualities.get(i);
                for (VideoUri v : q.uris) {
                    if (!v.isCached())
                        v.updateCached(true);
                    if (v.isCached())
                        return new File(v.uri.getPath());
                }
            }
        }
        if (videoUri != null) {
            if ("file".equalsIgnoreCase(videoUri.getScheme()))
                return new File(videoUri.getPath());

        }
        return null;
    }

    public int getSelectedQuality() {
        return selectedQualityIndex;
    }

    public TLRPC.Document getCurrentDocument() {
        if (player == null) return null;
        final Format format = player.getVideoFormat();
        if (format == null || format.documentId == 0)
            return null;
        if (videoQualities != null) {
            for (Quality q : videoQualities) {
                for (VideoUri u : q.uris) {
                    if (u.docId == format.documentId)
                        return u.document;
                }
            }
        }
        return null;
    }

    public int getCurrentQualityIndex() {
        if (selectedQualityIndex == QUALITY_AUTO) {
            try {
                if (autoIsOriginal) {
                    for (int j = 0; j < getQualitiesCount(); ++j) {
                        final Quality q = getQuality(j);
                        if (q.original) {
                            return j;
                        }
                    }
                }

                if (player == null) return -1;
                final Format format = player.getVideoFormat();
                if (format == null) return -1;
                for (int j = 0; j < getQualitiesCount(); ++j) {
                    final Quality q = getQuality(j);
                    if (!q.original && format.width == q.width && format.height == q.height && format.bitrate == (int) Math.floor(q.uris.get(0).bitrate * 8)) {
                        return j;
                    }
                }

//                final MappingTrackSelector.MappedTrackInfo mapTrackInfo = trackSelector.getCurrentMappedTrackInfo();
//                for (int renderIndex = 0; renderIndex < mapTrackInfo.getRendererCount(); ++renderIndex) {
//                    final TrackGroupArray trackGroups = mapTrackInfo.getTrackGroups(renderIndex);
//                    for (int groupIndex = 0; groupIndex < trackGroups.length; ++groupIndex) {
//                        final TrackGroup trackGroup = trackGroups.get(groupIndex);
//                        for (int trackIndex = 0; trackIndex < trackGroup.length; ++trackIndex) {
//                            final Format format = trackGroup.getFormat(trackIndex);
//                            int formatIndex;
//                            try {
//                                formatIndex = Integer.parseInt(format.id);
//                            } catch (Exception e) {
//                                formatIndex = -1;
//                            }
//                            if (formatIndex >= 0) {
//                                int formatOrder = 0;
//                                for (int j = 0; j < getQualitiesCount(); ++j) {
//                                    final Quality q = getQuality(j);
//                                    for (int i = 0; i < q.uris.size(); ++i){
//                                        if (q.uris.get(i).m3u8uri != null) {
//                                            if (formatOrder == formatIndex) {
//                                                return j;
//                                            }
//                                            formatOrder++;
//                                        }
//                                    }
//                                }
//                            }
//                            for (int j = 0; j < getQualitiesCount(); ++j) {
//                                final Quality q = getQuality(j);
//                                if (format.width == q.width && format.height == q.height) {
//                                    return j;
//                                }
//                            }
//                        }
//                    }
//                }
            } catch (Exception e) {
                FileLog.e(e);
                return -1;
            }
        }
        return selectedQualityIndex;
    }

    private TrackSelectionOverride getQualityTrackSelection(VideoUri videoUri) {
        try {
            int qualityOrder = manifestUris.indexOf(videoUri);
            final MappingTrackSelector.MappedTrackInfo mapTrackInfo = trackSelector.getCurrentMappedTrackInfo();
            for (int renderIndex = 0; renderIndex < mapTrackInfo.getRendererCount(); ++renderIndex) {
                final TrackGroupArray trackGroups = mapTrackInfo.getTrackGroups(renderIndex);
                for (int groupIndex = 0; groupIndex < trackGroups.length; ++groupIndex) {
                    final TrackGroup trackGroup = trackGroups.get(groupIndex);
                    for (int trackIndex = 0; trackIndex < trackGroup.length; ++trackIndex) {
                        final Format format = trackGroup.getFormat(trackIndex);

                        int formatIndex;
                        try {
                            formatIndex = Integer.parseInt(format.id);
                        } catch (Exception e) {
                            formatIndex = -1;
                        }
                        if (formatIndex >= 0) {
                            if (qualityOrder == formatIndex) {
                                return new TrackSelectionOverride(trackGroup, trackIndex);
                            }
                        }
                        if (format.width == videoUri.width && format.height == videoUri.height) {
                            return new TrackSelectionOverride(trackGroup, trackIndex);
                        }
                    }
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    @Override
    public void onTrackSelectionParametersChanged(TrackSelectionParameters parameters) {
        Player.Listener.super.onTrackSelectionParametersChanged(parameters);
        if (onQualityChangeListener != null) {
            AndroidUtilities.runOnUIThread(onQualityChangeListener);
        }
    }

    private long fallbackDuration = C.TIME_UNSET;
    private long fallbackPosition = C.TIME_UNSET;

    public void setSelectedQuality(int index) {
        if (player == null) return;
        if (index != selectedQualityIndex) {
            selectedQualityIndex = index;
            Quality q = null;
            if (videoQualities != null && index >= 0 && index < videoQualities.size())  q = videoQualities.get(index);
            setSelectedQuality(false, q);
        }
    }

    private void setSelectedQuality(boolean start, Quality quality) {
        if (player == null) return;

        final boolean lastPlaying = player.isPlaying();
        final long lastPosition = player.getCurrentPosition();
        if (!start) {
            fallbackPosition = lastPosition;
            fallbackDuration = player.getDuration();
        }

        boolean reset = false;

        videoQualityToSelect = quality;
        if (quality == null) { // AUTO
            final Uri hlsManifest = makeManifest(videoQualities);
            final Quality original = getOriginalQuality();
            if (original != null && original.uris.size() == 1 && original.uris.get(0).isCached()) {
                currentStreamIsHls = false;
                autoIsOriginal = true;
                quality = original;
                videoQualityToSelect = quality;
                player.setMediaSource(mediaSourceFromUri(quality.getDownloadUri().uri, "other"), false);
                reset = true;
            } else if (hlsManifest != null) {
                autoIsOriginal = false;
                trackSelector.setParameters(trackSelector.getParameters().buildUpon().clearOverrides().build());
                if (!currentStreamIsHls) {
                    currentStreamIsHls = true;
                    player.setMediaSource(mediaSourceFromUri(hlsManifest, "hls"), false);
                    reset = true;
                }
            } else {
                quality = getHighestQuality(true);
                if (quality == null) quality = getHighestQuality(false);
                if (quality == null || quality.uris.isEmpty()) return;
                currentStreamIsHls = false;
                videoQualityToSelect = quality;
                autoIsOriginal = quality.original;
                player.setMediaSource(mediaSourceFromUri(quality.getDownloadUri().uri, "other"), false);
                reset = true;
            }
        } else {
            autoIsOriginal = false;
            if (quality.uris.isEmpty()) return;
            Uri hlsManifest = null;
            if (quality.uris.size() > 1) {
                hlsManifest = makeManifest(videoQualities);
            }
            if (hlsManifest == null || quality.uris.size() == 1 || trackSelector.getCurrentMappedTrackInfo() == null) {
                currentStreamIsHls = false;
                player.setMediaSource(mediaSourceFromUri(quality.getDownloadUri().uri, "other"), false);
                reset = true;
            } else {
                if (!currentStreamIsHls) {
                    currentStreamIsHls = true;
                    player.setMediaSource(mediaSourceFromUri(hlsManifest, "hls"), false);
                    reset = true;
                }
                TrackSelectionParameters.Builder selector = trackSelector.getParameters().buildUpon().clearOverrides();
                for (VideoUri uri : quality.uris) {
                    TrackSelectionOverride override = getQualityTrackSelection(uri);
                    if (override == null) continue;
                    selector.addOverride(override);
                }
                trackSelector.setParameters(selector.build());
            }
        }

        if (reset) {
            player.prepare();
            if (!start) {
                player.seekTo(lastPosition);
                if (lastPlaying) {
                    player.play();
                }
            }
            if (onQualityChangeListener != null) {
                AndroidUtilities.runOnUIThread(onQualityChangeListener);
            }
            activePlayers.add(playerId);
        }
    }

    public Quality getCurrentQuality() {
        final int index = getCurrentQualityIndex();
        if (index < 0 || index >= getQualitiesCount()) return null;
        return getQuality(index);
    }

    private Runnable onQualityChangeListener;
    public void setOnQualityChangeListener(Runnable listener) {
        this.onQualityChangeListener = listener;
    }

    public static ArrayList<Quality> getQualities(int currentAccount, TLRPC.Document original, ArrayList<TLRPC.Document> alt_documents, int reference, boolean forThumb) {
        ArrayList<TLRPC.Document> documents = new ArrayList<>();
        if (original != null) {
            documents.add(original);
        }
        if (!MessagesController.getInstance(currentAccount).videoIgnoreAltDocuments && alt_documents != null) {
            documents.addAll(alt_documents);
        }

        final LongSparseArray<TLRPC.Document> manifests = new LongSparseArray<>();
        for (int i = 0; i < documents.size(); ++i) {
            final TLRPC.Document document = documents.get(i);
            if ("application/x-mpegurl".equalsIgnoreCase(document.mime_type)) {
                if (document.file_name_fixed == null || !document.file_name_fixed.startsWith("mtproto")) continue;
                try {
                    long videoDocumentId = Long.parseLong(document.file_name_fixed.substring(7));
                    manifests.put(videoDocumentId, document);
                    documents.remove(i);
                    i--;
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        }

        ArrayList<VideoUri> result = new ArrayList<>();
        for (int i = 0; i < documents.size(); ++i) {
            try {
                final TLRPC.Document document = documents.get(i);
                if ("application/x-mpegurl".equalsIgnoreCase(document.mime_type)) {
                    continue;
                }
                VideoUri q = VideoUri.of(currentAccount, document, manifests.get(document.id), reference);
                if (q.width <= 0 || q.height <= 0) {
                    continue;
                }
                if (document == original) {
                    q.original = true;
                }
                result.add(q);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        ArrayList<VideoUri> filtered = new ArrayList<>();
        for (int i = 0; i < result.size(); ++i) {
            final VideoUri q = result.get(i);
            if (q.codec != null) {
                if (forThumb) {
                    if (!("avc".equals(q.codec) || "h264".equals(q.codec) || "vp9".equals(q.codec) || "vp8".equals(q.codec) || ("av1".equals(q.codec) || "av01".equals(q.codec)) && supportsHardwareDecoder(q.codec))) {
                        continue;
                    }
                } else {
                    if (("av1".equals(q.codec) || "av01".equals(q.codec) || "hevc".equals(q.codec) || "h265".equals(q.codec) || "vp9".equals(q.codec)) && !supportsHardwareDecoder(q.codec)) {
                        continue;
                    }
                }
            }
            filtered.add(q);
        }

        ArrayList<VideoUri> qualities = new ArrayList<>();
        if (filtered.isEmpty())
            qualities.addAll(result);
        else
            qualities.addAll(filtered);

        return Quality.group(qualities);
    }

    public static ArrayList<Quality> getQualities(int currentAccount, TLRPC.MessageMedia media) {
        if (!(media instanceof TLRPC.TL_messageMediaDocument))
            return new ArrayList<>();
        return getQualities(currentAccount, media.document, media.alt_documents, 0, false);
    }

    public static boolean hasQualities(int currentAccount, TLRPC.MessageMedia media) {
        if (!(media instanceof TLRPC.TL_messageMediaDocument))
            return false;
        ArrayList<Quality> qualities = getQualities(currentAccount, media.document, media.alt_documents, 0, false);
        return qualities != null && qualities.size() > 1;
    }

    public static TLRPC.Document getDocumentForThumb(int currentAccount, TLRPC.MessageMedia media) {
        if (!(media instanceof TLRPC.TL_messageMediaDocument))
            return null;
        final VideoUri videoUri = getQualityForThumb(getQualities(currentAccount, media.document, media.alt_documents, 0, true));
        return videoUri == null ? null : videoUri.document;
    }

    public static VideoUri getQualityForThumb(ArrayList<Quality> qualities) {
        for (final Quality q : qualities) {
            for (final VideoUri v : q.uris) {
                if (v.isCached())
                    return v;
            }
        }

        final int MAX_SIZE = 900;
        VideoUri uri = null;
        for (final Quality q : qualities) {
            for (final VideoUri v : q.uris) {
                if (!v.original && (uri == null || uri.width * uri.height > v.width * v.height || v.bitrate < uri.bitrate) && v.width <= MAX_SIZE && v.height <= MAX_SIZE) {
                    uri = v;
                }
            }
        }
        if (uri == null) {
            for (final Quality q : qualities) {
                for (final VideoUri v : q.uris) {
                    if ((uri == null || uri.width * uri.height > v.width * v.height || v.bitrate < uri.bitrate)) {
                        uri = v;
                    }
                }
            }
        }
        return uri;
    }

    public static VideoUri getQualityForPlayer(ArrayList<Quality> qualities) {
        for (final Quality q : qualities) {
            for (final VideoUri v : q.uris) {
                if (v.original && v.isCached())
                    return v;
            }
        }

        VideoUri uri = null;
        if (uri == null) {
            for (final Quality q : qualities) {
                for (final VideoUri v : q.uris) {
                    if (!v.original && VideoPlayer.supportsHardwareDecoder(v.codec) && (uri == null || v.width * v.height > uri.width * uri.height || v.width * v.height == uri.width * uri.height && v.bitrate < uri.bitrate)) {
                        uri = v;
                    }
                }
            }
        }
        if (uri == null) {
            for (final Quality q : qualities) {
                for (final VideoUri v : q.uris) {
                    if (uri == null || uri.width * uri.height > v.width * v.height || v.bitrate < uri.bitrate) {
                        uri = v;
                    }
                }
            }
        }
        return uri;
    }

    public static String toMime(String codec) {
        if (codec == null) return null;
        switch (codec) {
            case "h264":
            case "avc": return "video/avc";
            case "vp8": return "video/x-vnd.on2.vp8";
            case "vp9": return "video/x-vnd.on2.vp9";
            case "h265":
            case "hevc": return "video/hevc";
            case "av1": case "av01": return "video/av01";
            default: return "video/" + codec;
        }
    }

    private static HashMap<String, Boolean> cachedSupportedCodec;
    public static boolean supportsHardwareDecoder(String codec) {
        try {
            final String mime = toMime(codec);
            if (mime == null) return false;
            if (cachedSupportedCodec == null) cachedSupportedCodec = new HashMap<>();
            Boolean cached = cachedSupportedCodec.get(mime);
            if (cached != null) return cached;
            if (MessagesController.getGlobalMainSettings().getBoolean("unsupport_" + mime, false)) {
                return false;
            }
            final int count = MediaCodecList.getCodecCount();
            for (int i = 0; i < count; i++) {
                final MediaCodecInfo info = MediaCodecList.getCodecInfoAt(i);
                if (info.isEncoder()) continue;
                if (!MediaCodecUtil.isHardwareAccelerated(info, mime)) continue;
                final String[] supportedTypes = info.getSupportedTypes();
                for (int j = 0; j < supportedTypes.length; ++j) {
                    if (supportedTypes[j].equalsIgnoreCase(mime)) {
                        cachedSupportedCodec.put(mime, true);
                        return true;
                    }
                }
            }
            cachedSupportedCodec.put(mime, false);
            return false;
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
    }

    public Uri makeManifest(ArrayList<Quality> qualities) {
        final StringBuilder sb = new StringBuilder();
        sb.append("#EXTM3U\n");
        sb.append("#EXT-X-VERSION:6\n");
        sb.append("#EXT-X-INDEPENDENT-SEGMENTS\n\n");
        manifestUris = new ArrayList<>();
        boolean hasManifests = false;
        ArrayList<String> streams = new ArrayList<>();
        for (Quality q : qualities) {
            for (VideoUri v : q.uris) {
                mediaDataSourceFactory.putDocumentUri(v.docId, v.uri);
                mediaDataSourceFactory.putDocumentUri(v.manifestDocId, v.m3u8uri);
                if (v.m3u8uri != null) {
                    manifestUris.add(v);
                    final StringBuilder stream = new StringBuilder();
                    stream.append("#EXT-X-STREAM-INF:BANDWIDTH=").append((int) Math.floor(v.bitrate * 8)).append(",RESOLUTION=").append(v.width).append("x").append(v.height);
                    final String mime = toMime(v.codec);
                    if (mime != null) {
                        stream.append(",MIME=\"").append(mime).append("\"");
                    }
                    if (v.isCached() && v.isManifestCached()) {
                        stream.append(",CACHED=\"true\"");
                    }
                    stream.append(",DOCID=\"").append(v.docId).append("\"");
                    stream.append(",ACCOUNT=\"").append(v.currentAccount).append("\"");
                    stream.append("\n");
                    if (v.isManifestCached()) {
                        stream.append(v.m3u8uri).append("\n\n");
                    } else {
                        stream.append("mtproto:").append(v.manifestDocId).append("\n\n");
                    }
                    hasManifests = true;
                    streams.add(stream.toString());
                }
            }
        }
        if (!hasManifests) return null;
        Collections.reverse(streams);
        sb.append(TextUtils.join("", streams));
        final String base64 = Base64.encodeToString(sb.toString().getBytes(), Base64.NO_WRAP);
        return Uri.parse("data:application/x-mpegurl;base64," + base64);
    }

    public static class Quality {

        public boolean original;
        public int width, height;
        public final ArrayList<VideoUri> uris = new ArrayList<>();

        public Quality(VideoUri uri) {
            original = uri.original;
            width = uri.width;
            height = uri.height;
            uris.add(uri);
        }

        public static ArrayList<Quality> group(ArrayList<VideoUri> uris) {
            final ArrayList<Quality> qualities = new ArrayList<>();

            for (VideoUri uri : uris) {
                if (uri.original) {
                    qualities.add(new Quality(uri));
                    continue;
                }

                Quality q = null;
                for (Quality _q : qualities) {
                    if (!_q.original && _q.width == uri.width && _q.height == uri.height) {
                        q = _q;
                        break;
                    }
                }

                if (q != null && !SharedConfig.debugVideoQualities) {
                    q.uris.add(uri);
                } else {
                    qualities.add(new Quality(uri));
                }
            }

            if (BuildVars.LOGS_ENABLED) {
                for (Quality q : qualities) {
                    FileLog.d("debug_loading_player: Quality "+q.p()+"p (" + q.width + "x" + q.height + ")" + (q.original ? " (source)" : "") + ":");
                    for (VideoUri uri : q.uris) {
                        FileLog.d("debug_loading_player: - video " + uri.width + "x" + uri.height + ", codec=" + uri.codec + ", bitrate=" + (int) (uri.bitrate*8) + ", doc#" + uri.docId + (uri.isCached() ? " (cached)" : "") + ", manifest#" + uri.manifestDocId + (uri.isManifestCached() ? " (cached)" : ""));
                    }
                }
                FileLog.d("debug_loading_player: ");
            }

            return qualities;
        }

        public static ArrayList<Quality> filterByCodec(ArrayList<Quality> qualities) {
            if (qualities == null) return null;
            for (int i = 0; i < qualities.size(); ++i) {
                Quality q = qualities.get(i);
                for (int j = 0; j < q.uris.size(); ++j) {
                    VideoUri u = q.uris.get(j);
                    if (!TextUtils.isEmpty(u.codec) && !supportsHardwareDecoder(u.codec)) {
                        q.uris.remove(j);
                        j--;
                    }
                }
                if (q.uris.isEmpty()) {
                    qualities.remove(i);
                    i--;
                }
            }
            return qualities;
        }

        @NonNull
        @Override
        public String toString() {
            if (SharedConfig.debugVideoQualities) {
                return width + "x" + height +
                    (original ? " (" + getString(R.string.QualitySource) + ")" : "") + "\n" +
                    AndroidUtilities.formatFileSize((long) uris.get(0).bitrate).replace(" ", "") + "/s" +
                    (uris.get(0).codec != null ? ", " + uris.get(0).codec : "");
            } else {
                return p() + "p" + (original ? " (" + getString(R.string.QualitySource) + ")" : "");
            }
        }

        public int p() {
            int p = Math.min(width, height);
            if      (Math.abs(p - 2160) < 55) p = 2160;
            else if (Math.abs(p - 1440) < 55) p = 1440;
            else if (Math.abs(p - 1080) < 55) p = 1080;
            else if (Math.abs(p - 720) <  55) p = 720;
            else if (Math.abs(p - 480) <  55) p = 480;
            else if (Math.abs(p - 360) <  55) p = 360;
            else if (Math.abs(p - 240) <  55) p = 240;
            else if (Math.abs(p - 144) <  55) p = 144;
            return p;
        }

        public TLRPC.Document getDownloadDocument() {
            if (uris.isEmpty()) return null;
            for (VideoUri uri : uris) {
                if (uri.isCached())
                    return uri.document;
            }
            long min_size = Long.MAX_VALUE;
            VideoUri selected_uri = null;
            for (int i = 0; i < uris.size(); ++i) {
                VideoUri uri = uris.get(i);
                if (uri.size < min_size && supportsHardwareDecoder(uri.codec)) {
                    min_size = uri.size;
                    selected_uri = uri;
                }
            }
            if (selected_uri != null) {
                return selected_uri.document;
            }
            return uris.get(0).document;
        }

        public VideoUri getDownloadUri() {
            if (uris.isEmpty()) return null;
            for (VideoUri uri : uris) {
                if (uri.isCached())
                    return uri;
            }
            long min_size = Long.MAX_VALUE;
            VideoUri selected_uri = null;
            for (int i = 0; i < uris.size(); ++i) {
                VideoUri uri = uris.get(i);
                if (uri.size < min_size && supportsHardwareDecoder(uri.codec)) {
                    min_size = uri.size;
                    selected_uri = uri;
                }
            }
            if (selected_uri != null) {
                return selected_uri;
            }
            return uris.get(0);
        }
    }

    public static class VideoUri {

        public int currentAccount;
        public boolean original;
        public long docId;
        public Uri uri;
        public long manifestDocId;
        public Uri m3u8uri;

        public TLRPC.Document document;
        public TLRPC.Document manifestDocument;

        public boolean isCached() {
            return uri != null && "file".equalsIgnoreCase(uri.getScheme());
        }

        public boolean isManifestCached() {
            return m3u8uri != null && "file".equalsIgnoreCase(m3u8uri.getScheme());
        }

        public void updateCached(boolean useFileDatabaseQueue) {
            if (!isCached() && document != null) {
                File file = FileLoader.getInstance(currentAccount).getPathToAttach(document, null, false, useFileDatabaseQueue);
                if (file != null && file.exists()) {
                    this.uri = Uri.fromFile(file);
                } else {
                    file = FileLoader.getInstance(currentAccount).getPathToAttach(document, null, true, useFileDatabaseQueue);
                    if (file != null && file.exists()) {
                        this.uri = Uri.fromFile(file);
                    }
                }
            }
            if (!isManifestCached() && manifestDocument != null) {
                File file = FileLoader.getInstance(currentAccount).getPathToAttach(manifestDocument, null, false, useFileDatabaseQueue);
                if (file != null && file.exists()) {
                    this.m3u8uri = Uri.fromFile(file);
                } else {
                    file = FileLoader.getInstance(currentAccount).getPathToAttach(manifestDocument, null, true, useFileDatabaseQueue);
                    if (file != null && file.exists()) {
                        this.m3u8uri = Uri.fromFile(file);
                    }
                }
            }
        }

        public int width, height;
        public double duration;
        public long size;
        public double bitrate;

        public String codec;
        public MediaItem mediaItem;

        public static Uri getUri(int currentAccount, TLRPC.Document document, int reference) throws UnsupportedEncodingException {
            final String params =
                "?account=" + currentAccount +
                "&id=" + document.id +
                "&hash=" + document.access_hash +
                "&dc=" + document.dc_id +
                "&size=" + document.size +
                "&mime=" + URLEncoder.encode(document.mime_type, "UTF-8") +
                "&rid=" + reference +
                "&name=" + URLEncoder.encode(FileLoader.getDocumentFileName(document), "UTF-8") +
                "&reference=" + Utilities.bytesToHex(document.file_reference != null ? document.file_reference : new byte[0]);
            return Uri.parse("tg://" + MessageObject.getFileName(document) + params);
        }

        public static VideoUri of(int currentAccount, TLRPC.Document document, TLRPC.Document manifest, int reference) throws UnsupportedEncodingException {
            final VideoUri videoUri = new VideoUri();
            TLRPC.TL_documentAttributeVideo attributeVideo = null;
            for (int i = 0; i < document.attributes.size(); ++i) {
                final TLRPC.DocumentAttribute attribute = document.attributes.get(i);
                if (attribute instanceof TLRPC.TL_documentAttributeVideo) {
                    attributeVideo = (TLRPC.TL_documentAttributeVideo) attribute;
                    break;
                }
            }
            final String codec = attributeVideo == null || attributeVideo.video_codec == null ? null : attributeVideo.video_codec.toLowerCase();

            videoUri.currentAccount = currentAccount;
            videoUri.document = document;
            videoUri.docId = document.id;
            videoUri.uri = getUri(currentAccount, document, reference);
            if (manifest != null) {
                videoUri.manifestDocument = manifest;
                videoUri.manifestDocId = manifest.id;
                videoUri.m3u8uri = getUri(currentAccount, manifest, reference);
                File file = FileLoader.getInstance(currentAccount).getPathToAttach(manifest, null, false, true);
                if (file != null && file.exists()) {
                    videoUri.m3u8uri = Uri.fromFile(file);
                } else {
                    file = FileLoader.getInstance(currentAccount).getPathToAttach(manifest, null, true, true);
                    if (file != null && file.exists()) {
                        videoUri.m3u8uri = Uri.fromFile(file);
                    }
                }
            }

            videoUri.codec = codec;
            videoUri.size = document.size;
            if (attributeVideo != null) {
                videoUri.duration = attributeVideo.duration;
                videoUri.width = attributeVideo.w;
                videoUri.height = attributeVideo.h;

                videoUri.bitrate = videoUri.size / videoUri.duration;
            }

            File file = FileLoader.getInstance(currentAccount).getPathToAttach(document, null, false, true);
            if (file != null && file.exists()) {
                videoUri.uri = Uri.fromFile(file);
            } else {
                file = FileLoader.getInstance(currentAccount).getPathToAttach(document, null, true, true);
                if (file != null && file.exists()) {
                    videoUri.uri = Uri.fromFile(file);
                }
            }

            return videoUri;
        }

        public MediaItem getMediaItem() {
            if (mediaItem == null) {
                mediaItem = new MediaItem.Builder().setUri(uri).build();
            }
            return mediaItem;
        }

    }

    public boolean isPlayerPrepared() {
        return player != null;
    }

    public void releasePlayer(boolean async) {
        activePlayers.remove(playerId);
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

    private final ArrayList<Runnable> seekFinishedListeners = new ArrayList<>();

    @Override
    public void onSeekProcessed(EventTime eventTime) {
        if (delegate != null) {
            delegate.onSeekFinished(eventTime);
        }
        for (Runnable r : seekFinishedListeners) {
            r.run();
        }
        seekFinishedListeners.clear();
    }

    @Override
    public void onRenderedFirstFrame(EventTime eventTime, Object output, long renderTimeMs) {
        fallbackPosition = C.TIME_UNSET;
        fallbackDuration = C.TIME_UNSET;
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
        if (fallbackDuration != C.TIME_UNSET) {
            return fallbackDuration;
        }
        return player != null ? player.getDuration() : 0;
    }

    public long getCurrentPosition() {
        if (fallbackPosition != C.TIME_UNSET) {
            return fallbackPosition;
        }
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
        seekTo(positionMs, false);
    }

    public void seekTo(long positionMs, boolean fast) {
        if (player != null) {
            player.setSeekParameters(fast ? SeekParameters.CLOSEST_SYNC : SeekParameters.EXACT);
            player.seekTo(positionMs);
        }
    }

    public void seekTo(long positionMs, boolean fast, Runnable whenDone) {
        if (player != null) {
            if (whenDone != null) {
                seekFinishedListeners.add(whenDone);
            }
            player.setSeekParameters(fast ? SeekParameters.CLOSEST_SYNC : SeekParameters.EXACT);
            player.seekTo(positionMs);
        }
    }

    public void seekToBack(long positionMs, boolean fast, Runnable whenDone) {
        if (player != null) {
            if (whenDone != null) {
                seekFinishedListeners.add(whenDone);
            }
            player.setSeekParameters(fast ? SeekParameters.PREVIOUS_SYNC : SeekParameters.EXACT);
            player.seekTo(positionMs);
        }
    }

    public void seekToForward(long positionMs, boolean fast, Runnable whenDone) {
        if (player != null) {
            if (whenDone != null) {
                seekFinishedListeners.add(whenDone);
            }
            player.setSeekParameters(fast ? SeekParameters.NEXT_SYNC : SeekParameters.EXACT);
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


    private boolean handleAudioFocus = false;
    public void handleAudioFocus(boolean handleAudioFocus) {
        this.handleAudioFocus = handleAudioFocus;
        if (player != null) {
            player.setAudioAttributes(player.getAudioAttributes(), handleAudioFocus);
        }
    }

    public void setStreamType(int type) {
        if (player != null) {
            player.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(type == AudioManager.STREAM_VOICE_CALL ? C.USAGE_VOICE_COMMUNICATION : C.USAGE_MEDIA)
                .build(), handleAudioFocus);
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
            if (cause instanceof MediaCodecDecoderException) {
                if (cause.toString().contains("av1") || cause.toString().contains("av01")) {
                    FileLog.e(error);
                    FileLog.e("av1 codec failed, we think this codec is not supported");
                    MessagesController.getGlobalMainSettings().edit().putBoolean("unsupport_video/av01", true).commit();
                    if (cachedSupportedCodec != null) {
                        cachedSupportedCodec.clear();
                    }
                    videoQualities = Quality.filterByCodec(videoQualities);
                    if (videoQualities != null) {
                        preparePlayer(videoQualities, videoQualityToSelect);
                    }
                    return;
                }
            }
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
                                if (videoQualities != null) {
                                    preparePlayer(videoQualities, videoQualityToSelect);
                                } else if (loopingMediaSource) {
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
                        if (videoQualities != null) {
                            preparePlayer(videoQualities, videoQualityToSelect);
                        } else if (loopingMediaSource) {
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

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                if (mediaFormat.containsKey(MediaFormat.KEY_COLOR_TRANSFER)) {
                    hdrInfo.colorTransfer = mediaFormat.getInteger(MediaFormat.KEY_COLOR_TRANSFER);
                }
                if (mediaFormat.containsKey(MediaFormat.KEY_COLOR_STANDARD)) {
                    hdrInfo.colorStandard = mediaFormat.getInteger(MediaFormat.KEY_COLOR_STANDARD);
                }
                if (mediaFormat.containsKey(MediaFormat.KEY_COLOR_RANGE)) {
                    hdrInfo.colorRange = mediaFormat.getInteger(MediaFormat.KEY_COLOR_RANGE);
                }
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

    @Override
    public void onTracksChanged(Tracks tracks) {
        Player.Listener.super.onTracksChanged(tracks);
        if (onQualityChangeListener != null) {
            AndroidUtilities.runOnUIThread(onQualityChangeListener);
        }
    }

}
