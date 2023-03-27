/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.analytics;

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.LOCAL_VARIABLE;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.media.MediaCodec;
import android.media.MediaCodec.CodecException;
import android.os.Looper;
import android.os.SystemClock;
import android.util.SparseArray;
import android.view.Surface;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DeviceInfo;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.MediaMetadata;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Player.DiscontinuityReason;
import com.google.android.exoplayer2.Player.PlaybackSuppressionReason;
import com.google.android.exoplayer2.Player.TimelineChangeReason;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.Tracks;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.audio.AudioSink;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.decoder.DecoderException;
import com.google.android.exoplayer2.decoder.DecoderReuseEvaluation;
import com.google.android.exoplayer2.drm.DrmSession;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.MetadataOutput;
import com.google.android.exoplayer2.source.LoadEventInfo;
import com.google.android.exoplayer2.source.MediaLoadData;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.CueGroup;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionParameters;
import com.google.android.exoplayer2.util.FlagSet;
import com.google.android.exoplayer2.video.VideoDecoderOutputBufferRenderer;
import com.google.android.exoplayer2.video.VideoSize;
import com.google.common.base.Objects;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;

/**
 * A listener for analytics events.
 *
 * <p>All events are recorded with an {@link EventTime} specifying the elapsed real time and media
 * time at the time of the event.
 *
 * <p>All methods have no-op default implementations to allow selective overrides.
 *
 * <p>Listeners can choose to implement individual events (e.g. {@link
 * #onIsPlayingChanged(EventTime, boolean)}) or {@link #onEvents(Player, Events)}, which is called
 * after one or more events occurred together.
 */
public interface AnalyticsListener {

  /** A set of {@link EventFlags}. */
  final class Events {

    private final FlagSet flags;
    private final SparseArray<EventTime> eventTimes;

    /**
     * Creates an instance.
     *
     * @param flags The {@link FlagSet} containing the {@link EventFlags} in the set.
     * @param eventTimes A map from {@link EventFlags} to {@link EventTime}. Must at least contain
     *     all the events recorded in {@code flags}. Events that are not recorded in {@code flags}
     *     are ignored.
     */
    public Events(FlagSet flags, SparseArray<EventTime> eventTimes) {
      this.flags = flags;
      SparseArray<EventTime> flagsToTimes = new SparseArray<>(/* initialCapacity= */ flags.size());
      for (int i = 0; i < flags.size(); i++) {
        @EventFlags int eventFlag = flags.get(i);
        flagsToTimes.append(eventFlag, checkNotNull(eventTimes.get(eventFlag)));
      }
      this.eventTimes = flagsToTimes;
    }

    /**
     * Returns the {@link EventTime} for the specified event.
     *
     * @param event The {@link EventFlags event}.
     * @return The {@link EventTime} of this event.
     */
    public EventTime getEventTime(@EventFlags int event) {
      return checkNotNull(eventTimes.get(event));
    }

    /**
     * Returns whether the given event occurred.
     *
     * @param event The {@link EventFlags event}.
     * @return Whether the event occurred.
     */
    public boolean contains(@EventFlags int event) {
      return flags.contains(event);
    }

    /**
     * Returns whether any of the given events occurred.
     *
     * @param events The {@link EventFlags events}.
     * @return Whether any of the events occurred.
     */
    public boolean containsAny(@EventFlags int... events) {
      return flags.containsAny(events);
    }

    /** Returns the number of events in the set. */
    public int size() {
      return flags.size();
    }

    /**
     * Returns the {@link EventFlags event} at the given index.
     *
     * <p>Although index-based access is possible, it doesn't imply a particular order of these
     * events.
     *
     * @param index The index. Must be between 0 (inclusive) and {@link #size()} (exclusive).
     * @return The {@link EventFlags event} at the given index.
     */
    public @EventFlags int get(int index) {
      return flags.get(index);
    }
  }

  /**
   * Events that can be reported via {@link #onEvents(Player, Events)}.
   *
   * <p>One of the {@link AnalyticsListener}{@code .EVENT_*} flags.
   */
  // @Target list includes both 'default' targets and TYPE_USE, to ensure backwards compatibility
  // with Kotlin usages from before TYPE_USE was added.
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target({FIELD, METHOD, PARAMETER, LOCAL_VARIABLE, TYPE_USE})
  @IntDef({
    EVENT_TIMELINE_CHANGED,
    EVENT_MEDIA_ITEM_TRANSITION,
    EVENT_TRACKS_CHANGED,
    EVENT_IS_LOADING_CHANGED,
    EVENT_PLAYBACK_STATE_CHANGED,
    EVENT_PLAY_WHEN_READY_CHANGED,
    EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED,
    EVENT_IS_PLAYING_CHANGED,
    EVENT_REPEAT_MODE_CHANGED,
    EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
    EVENT_PLAYER_ERROR,
    EVENT_POSITION_DISCONTINUITY,
    EVENT_PLAYBACK_PARAMETERS_CHANGED,
    EVENT_AVAILABLE_COMMANDS_CHANGED,
    EVENT_MEDIA_METADATA_CHANGED,
    EVENT_PLAYLIST_METADATA_CHANGED,
    EVENT_SEEK_BACK_INCREMENT_CHANGED,
    EVENT_SEEK_FORWARD_INCREMENT_CHANGED,
    EVENT_MAX_SEEK_TO_PREVIOUS_POSITION_CHANGED,
    EVENT_TRACK_SELECTION_PARAMETERS_CHANGED,
    EVENT_DEVICE_INFO_CHANGED,
    EVENT_DEVICE_VOLUME_CHANGED,
    EVENT_LOAD_STARTED,
    EVENT_LOAD_COMPLETED,
    EVENT_LOAD_CANCELED,
    EVENT_LOAD_ERROR,
    EVENT_DOWNSTREAM_FORMAT_CHANGED,
    EVENT_UPSTREAM_DISCARDED,
    EVENT_BANDWIDTH_ESTIMATE,
    EVENT_METADATA,
    EVENT_CUES,
    EVENT_AUDIO_ENABLED,
    EVENT_AUDIO_DECODER_INITIALIZED,
    EVENT_AUDIO_INPUT_FORMAT_CHANGED,
    EVENT_AUDIO_POSITION_ADVANCING,
    EVENT_AUDIO_UNDERRUN,
    EVENT_AUDIO_DECODER_RELEASED,
    EVENT_AUDIO_DISABLED,
    EVENT_AUDIO_SESSION_ID,
    EVENT_AUDIO_ATTRIBUTES_CHANGED,
    EVENT_SKIP_SILENCE_ENABLED_CHANGED,
    EVENT_AUDIO_SINK_ERROR,
    EVENT_VOLUME_CHANGED,
    EVENT_VIDEO_ENABLED,
    EVENT_VIDEO_DECODER_INITIALIZED,
    EVENT_VIDEO_INPUT_FORMAT_CHANGED,
    EVENT_DROPPED_VIDEO_FRAMES,
    EVENT_VIDEO_DECODER_RELEASED,
    EVENT_VIDEO_DISABLED,
    EVENT_VIDEO_FRAME_PROCESSING_OFFSET,
    EVENT_RENDERED_FIRST_FRAME,
    EVENT_VIDEO_SIZE_CHANGED,
    EVENT_SURFACE_SIZE_CHANGED,
    EVENT_DRM_SESSION_ACQUIRED,
    EVENT_DRM_KEYS_LOADED,
    EVENT_DRM_SESSION_MANAGER_ERROR,
    EVENT_DRM_KEYS_RESTORED,
    EVENT_DRM_KEYS_REMOVED,
    EVENT_DRM_SESSION_RELEASED,
    EVENT_PLAYER_RELEASED,
    EVENT_AUDIO_CODEC_ERROR,
    EVENT_VIDEO_CODEC_ERROR,
  })
  @interface EventFlags {}
  /** {@link Player#getCurrentTimeline()} changed. */
  int EVENT_TIMELINE_CHANGED = Player.EVENT_TIMELINE_CHANGED;
  /**
   * {@link Player#getCurrentMediaItem()} changed or the player started repeating the current item.
   */
  int EVENT_MEDIA_ITEM_TRANSITION = Player.EVENT_MEDIA_ITEM_TRANSITION;
  /** {@link Player#getCurrentTracks()} changed. */
  int EVENT_TRACKS_CHANGED = Player.EVENT_TRACKS_CHANGED;
  /** {@link Player#isLoading()} ()} changed. */
  int EVENT_IS_LOADING_CHANGED = Player.EVENT_IS_LOADING_CHANGED;
  /** {@link Player#getPlaybackState()} changed. */
  int EVENT_PLAYBACK_STATE_CHANGED = Player.EVENT_PLAYBACK_STATE_CHANGED;
  /** {@link Player#getPlayWhenReady()} changed. */
  int EVENT_PLAY_WHEN_READY_CHANGED = Player.EVENT_PLAY_WHEN_READY_CHANGED;
  /** {@link Player#getPlaybackSuppressionReason()} changed. */
  int EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED = Player.EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED;
  /** {@link Player#isPlaying()} changed. */
  int EVENT_IS_PLAYING_CHANGED = Player.EVENT_IS_PLAYING_CHANGED;
  /** {@link Player#getRepeatMode()} changed. */
  int EVENT_REPEAT_MODE_CHANGED = Player.EVENT_REPEAT_MODE_CHANGED;
  /** {@link Player#getShuffleModeEnabled()} changed. */
  int EVENT_SHUFFLE_MODE_ENABLED_CHANGED = Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED;
  /** {@link Player#getPlayerError()} changed. */
  int EVENT_PLAYER_ERROR = Player.EVENT_PLAYER_ERROR;
  /**
   * A position discontinuity occurred. See {@link
   * Player.Listener#onPositionDiscontinuity(Player.PositionInfo, Player.PositionInfo, int)}.
   */
  int EVENT_POSITION_DISCONTINUITY = Player.EVENT_POSITION_DISCONTINUITY;
  /** {@link Player#getPlaybackParameters()} changed. */
  int EVENT_PLAYBACK_PARAMETERS_CHANGED = Player.EVENT_PLAYBACK_PARAMETERS_CHANGED;
  /** {@link Player#getAvailableCommands()} changed. */
  int EVENT_AVAILABLE_COMMANDS_CHANGED = Player.EVENT_AVAILABLE_COMMANDS_CHANGED;
  /** {@link Player#getMediaMetadata()} changed. */
  int EVENT_MEDIA_METADATA_CHANGED = Player.EVENT_MEDIA_METADATA_CHANGED;
  /** {@link Player#getPlaylistMetadata()} changed. */
  int EVENT_PLAYLIST_METADATA_CHANGED = Player.EVENT_PLAYLIST_METADATA_CHANGED;
  /** {@link Player#getSeekBackIncrement()} changed. */
  int EVENT_SEEK_BACK_INCREMENT_CHANGED = Player.EVENT_SEEK_BACK_INCREMENT_CHANGED;
  /** {@link Player#getSeekForwardIncrement()} changed. */
  int EVENT_SEEK_FORWARD_INCREMENT_CHANGED = Player.EVENT_SEEK_FORWARD_INCREMENT_CHANGED;
  /** {@link Player#getMaxSeekToPreviousPosition()} changed. */
  int EVENT_MAX_SEEK_TO_PREVIOUS_POSITION_CHANGED =
      Player.EVENT_MAX_SEEK_TO_PREVIOUS_POSITION_CHANGED;
  /** {@link Player#getTrackSelectionParameters()} changed. */
  int EVENT_TRACK_SELECTION_PARAMETERS_CHANGED = Player.EVENT_TRACK_SELECTION_PARAMETERS_CHANGED;
  /** Audio attributes changed. */
  int EVENT_AUDIO_ATTRIBUTES_CHANGED = Player.EVENT_AUDIO_ATTRIBUTES_CHANGED;
  /** An audio session id was set. */
  int EVENT_AUDIO_SESSION_ID = Player.EVENT_AUDIO_SESSION_ID;
  /** The volume changed. */
  int EVENT_VOLUME_CHANGED = Player.EVENT_VOLUME_CHANGED;
  /** Skipping silences was enabled or disabled in the audio stream. */
  int EVENT_SKIP_SILENCE_ENABLED_CHANGED = Player.EVENT_SKIP_SILENCE_ENABLED_CHANGED;
  /** The surface size changed. */
  int EVENT_SURFACE_SIZE_CHANGED = Player.EVENT_SURFACE_SIZE_CHANGED;
  /** The video size changed. */
  int EVENT_VIDEO_SIZE_CHANGED = Player.EVENT_VIDEO_SIZE_CHANGED;
  /**
   * The first frame has been rendered since setting the surface, since the renderer was reset or
   * since the stream changed.
   */
  int EVENT_RENDERED_FIRST_FRAME = Player.EVENT_RENDERED_FIRST_FRAME;
  /** Metadata associated with the current playback time was reported. */
  int EVENT_METADATA = Player.EVENT_METADATA;
  /** {@link Player#getCurrentCues()} changed. */
  int EVENT_CUES = Player.EVENT_CUES;
  /** {@link Player#getDeviceInfo()} changed. */
  int EVENT_DEVICE_INFO_CHANGED = Player.EVENT_DEVICE_INFO_CHANGED;
  /** {@link Player#getDeviceVolume()} changed. */
  int EVENT_DEVICE_VOLUME_CHANGED = Player.EVENT_DEVICE_VOLUME_CHANGED;
  /** A source started loading data. */
  int EVENT_LOAD_STARTED = 1000; // Intentional gap to leave space for new Player events
  /** A source started completed loading data. */
  int EVENT_LOAD_COMPLETED = 1001;
  /** A source canceled loading data. */
  int EVENT_LOAD_CANCELED = 1002;
  /** A source had a non-fatal error loading data. */
  int EVENT_LOAD_ERROR = 1003;
  /** The downstream format sent to renderers changed. */
  int EVENT_DOWNSTREAM_FORMAT_CHANGED = 1004;
  /** Data was removed from the end of the media buffer. */
  int EVENT_UPSTREAM_DISCARDED = 1005;
  /** The bandwidth estimate has been updated. */
  int EVENT_BANDWIDTH_ESTIMATE = 1006;
  /** An audio renderer was enabled. */
  int EVENT_AUDIO_ENABLED = 1007;
  /** An audio renderer created a decoder. */
  int EVENT_AUDIO_DECODER_INITIALIZED = 1008;
  /** The format consumed by an audio renderer changed. */
  int EVENT_AUDIO_INPUT_FORMAT_CHANGED = 1009;
  /** The audio position has increased for the first time since the last pause or position reset. */
  int EVENT_AUDIO_POSITION_ADVANCING = 1010;
  /** An audio underrun occurred. */
  int EVENT_AUDIO_UNDERRUN = 1011;
  /** An audio renderer released a decoder. */
  int EVENT_AUDIO_DECODER_RELEASED = 1012;
  /** An audio renderer was disabled. */
  int EVENT_AUDIO_DISABLED = 1013;
  /** The audio sink encountered a non-fatal error. */
  int EVENT_AUDIO_SINK_ERROR = 1014;
  /** A video renderer was enabled. */
  int EVENT_VIDEO_ENABLED = 1015;
  /** A video renderer created a decoder. */
  int EVENT_VIDEO_DECODER_INITIALIZED = 1016;
  /** The format consumed by a video renderer changed. */
  int EVENT_VIDEO_INPUT_FORMAT_CHANGED = 1017;
  /** Video frames have been dropped. */
  int EVENT_DROPPED_VIDEO_FRAMES = 1018;
  /** A video renderer released a decoder. */
  int EVENT_VIDEO_DECODER_RELEASED = 1019;
  /** A video renderer was disabled. */
  int EVENT_VIDEO_DISABLED = 1020;
  /** Video frame processing offset data has been reported. */
  int EVENT_VIDEO_FRAME_PROCESSING_OFFSET = 1021;
  /** A DRM session has been acquired. */
  int EVENT_DRM_SESSION_ACQUIRED = 1022;
  /** DRM keys were loaded. */
  int EVENT_DRM_KEYS_LOADED = 1023;
  /** A non-fatal DRM session manager error occurred. */
  int EVENT_DRM_SESSION_MANAGER_ERROR = 1024;
  /** DRM keys were restored. */
  int EVENT_DRM_KEYS_RESTORED = 1025;
  /** DRM keys were removed. */
  int EVENT_DRM_KEYS_REMOVED = 1026;
  /** A DRM session has been released. */
  int EVENT_DRM_SESSION_RELEASED = 1027;
  /** The player was released. */
  int EVENT_PLAYER_RELEASED = 1028;
  /** The audio codec encountered an error. */
  int EVENT_AUDIO_CODEC_ERROR = 1029;
  /** The video codec encountered an error. */
  int EVENT_VIDEO_CODEC_ERROR = 1030;

  /** Time information of an event. */
  final class EventTime {

    /**
     * Elapsed real-time as returned by {@code SystemClock.elapsedRealtime()} at the time of the
     * event, in milliseconds.
     */
    public final long realtimeMs;

    /** Most recent {@link Timeline} that contains the event position. */
    public final Timeline timeline;

    /**
     * Window index in the {@link #timeline} this event belongs to, or the prospective window index
     * if the timeline is not yet known and empty.
     */
    public final int windowIndex;

    /**
     * {@link MediaPeriodId Media period identifier} for the media period this event belongs to, or
     * {@code null} if the event is not associated with a specific media period.
     */
    @Nullable public final MediaPeriodId mediaPeriodId;

    /**
     * Position in the window or ad this event belongs to at the time of the event, in milliseconds.
     */
    public final long eventPlaybackPositionMs;

    /**
     * The current {@link Timeline} at the time of the event (equivalent to {@link
     * Player#getCurrentTimeline()}).
     */
    public final Timeline currentTimeline;

    /**
     * The current window index in {@link #currentTimeline} at the time of the event, or the
     * prospective window index if the timeline is not yet known and empty (equivalent to {@link
     * Player#getCurrentMediaItemIndex()}).
     */
    public final int currentWindowIndex;

    /**
     * {@link MediaPeriodId Media period identifier} for the currently playing media period at the
     * time of the event, or {@code null} if no current media period identifier is available.
     */
    @Nullable public final MediaPeriodId currentMediaPeriodId;

    /**
     * Position in the {@link #currentWindowIndex current timeline window} or the currently playing
     * ad at the time of the event, in milliseconds.
     */
    public final long currentPlaybackPositionMs;

    /**
     * Total buffered duration from {@link #currentPlaybackPositionMs} at the time of the event, in
     * milliseconds. This includes pre-buffered data for subsequent ads and windows.
     */
    public final long totalBufferedDurationMs;

    /**
     * @param realtimeMs Elapsed real-time as returned by {@code SystemClock.elapsedRealtime()} at
     *     the time of the event, in milliseconds.
     * @param timeline Most recent {@link Timeline} that contains the event position.
     * @param windowIndex Window index in the {@code timeline} this event belongs to, or the
     *     prospective window index if the timeline is not yet known and empty.
     * @param mediaPeriodId {@link MediaPeriodId Media period identifier} for the media period this
     *     event belongs to, or {@code null} if the event is not associated with a specific media
     *     period.
     * @param eventPlaybackPositionMs Position in the window or ad this event belongs to at the time
     *     of the event, in milliseconds.
     * @param currentTimeline The current {@link Timeline} at the time of the event (equivalent to
     *     {@link Player#getCurrentTimeline()}).
     * @param currentWindowIndex The current window index in {@code currentTimeline} at the time of
     *     the event, or the prospective window index if the timeline is not yet known and empty
     *     (equivalent to {@link Player#getCurrentMediaItemIndex()}).
     * @param currentMediaPeriodId {@link MediaPeriodId Media period identifier} for the currently
     *     playing media period at the time of the event, or {@code null} if no current media period
     *     identifier is available.
     * @param currentPlaybackPositionMs Position in the current timeline window or the currently
     *     playing ad at the time of the event, in milliseconds.
     * @param totalBufferedDurationMs Total buffered duration from {@code currentPlaybackPositionMs}
     *     at the time of the event, in milliseconds. This includes pre-buffered data for subsequent
     *     ads and windows.
     */
    public EventTime(
        long realtimeMs,
        Timeline timeline,
        int windowIndex,
        @Nullable MediaPeriodId mediaPeriodId,
        long eventPlaybackPositionMs,
        Timeline currentTimeline,
        int currentWindowIndex,
        @Nullable MediaPeriodId currentMediaPeriodId,
        long currentPlaybackPositionMs,
        long totalBufferedDurationMs) {
      this.realtimeMs = realtimeMs;
      this.timeline = timeline;
      this.windowIndex = windowIndex;
      this.mediaPeriodId = mediaPeriodId;
      this.eventPlaybackPositionMs = eventPlaybackPositionMs;
      this.currentTimeline = currentTimeline;
      this.currentWindowIndex = currentWindowIndex;
      this.currentMediaPeriodId = currentMediaPeriodId;
      this.currentPlaybackPositionMs = currentPlaybackPositionMs;
      this.totalBufferedDurationMs = totalBufferedDurationMs;
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      EventTime eventTime = (EventTime) o;
      return realtimeMs == eventTime.realtimeMs
          && windowIndex == eventTime.windowIndex
          && eventPlaybackPositionMs == eventTime.eventPlaybackPositionMs
          && currentWindowIndex == eventTime.currentWindowIndex
          && currentPlaybackPositionMs == eventTime.currentPlaybackPositionMs
          && totalBufferedDurationMs == eventTime.totalBufferedDurationMs
          && Objects.equal(timeline, eventTime.timeline)
          && Objects.equal(mediaPeriodId, eventTime.mediaPeriodId)
          && Objects.equal(currentTimeline, eventTime.currentTimeline)
          && Objects.equal(currentMediaPeriodId, eventTime.currentMediaPeriodId);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(
          realtimeMs,
          timeline,
          windowIndex,
          mediaPeriodId,
          eventPlaybackPositionMs,
          currentTimeline,
          currentWindowIndex,
          currentMediaPeriodId,
          currentPlaybackPositionMs,
          totalBufferedDurationMs);
    }
  }

  /**
   * @deprecated Use {@link #onPlaybackStateChanged(EventTime, int)} and {@link
   *     #onPlayWhenReadyChanged(EventTime, boolean, int)} instead.
   */
  @Deprecated
  default void onPlayerStateChanged(
      EventTime eventTime, boolean playWhenReady, @Player.State int playbackState) {}

  /**
   * Called when the playback state changed.
   *
   * @param eventTime The event time.
   * @param state The new {@link Player.State playback state}.
   */
  default void onPlaybackStateChanged(EventTime eventTime, @Player.State int state) {}

  /**
   * Called when the value changed that indicates whether playback will proceed when ready.
   *
   * @param eventTime The event time.
   * @param playWhenReady Whether playback will proceed when ready.
   * @param reason The {@link Player.PlayWhenReadyChangeReason reason} of the change.
   */
  default void onPlayWhenReadyChanged(
      EventTime eventTime, boolean playWhenReady, @Player.PlayWhenReadyChangeReason int reason) {}

  /**
   * Called when playback suppression reason changed.
   *
   * @param eventTime The event time.
   * @param playbackSuppressionReason The new {@link PlaybackSuppressionReason}.
   */
  default void onPlaybackSuppressionReasonChanged(
      EventTime eventTime, @PlaybackSuppressionReason int playbackSuppressionReason) {}

  /**
   * Called when the player starts or stops playing.
   *
   * @param eventTime The event time.
   * @param isPlaying Whether the player is playing.
   */
  default void onIsPlayingChanged(EventTime eventTime, boolean isPlaying) {}

  /**
   * Called when the timeline changed.
   *
   * @param eventTime The event time.
   * @param reason The reason for the timeline change.
   */
  default void onTimelineChanged(EventTime eventTime, @TimelineChangeReason int reason) {}

  /**
   * Called when playback transitions to a different media item.
   *
   * @param eventTime The event time.
   * @param mediaItem The media item.
   * @param reason The reason for the media item transition.
   */
  default void onMediaItemTransition(
      EventTime eventTime,
      @Nullable MediaItem mediaItem,
      @Player.MediaItemTransitionReason int reason) {}

  /**
   * @deprecated Use {@link #onPositionDiscontinuity(EventTime, Player.PositionInfo,
   *     Player.PositionInfo, int)} instead.
   */
  @Deprecated
  default void onPositionDiscontinuity(EventTime eventTime, @DiscontinuityReason int reason) {}

  /**
   * Called when a position discontinuity occurred.
   *
   * @param eventTime The event time.
   * @param oldPosition The position before the discontinuity.
   * @param newPosition The position after the discontinuity.
   * @param reason The reason for the position discontinuity.
   */
  default void onPositionDiscontinuity(
      EventTime eventTime,
      Player.PositionInfo oldPosition,
      Player.PositionInfo newPosition,
      @DiscontinuityReason int reason) {}

  /**
   * @deprecated Use {@link #onPositionDiscontinuity(EventTime, Player.PositionInfo,
   *     Player.PositionInfo, int)} instead, listening to changes with {@link
   *     Player#DISCONTINUITY_REASON_SEEK}.
   */
  @Deprecated
  default void onSeekStarted(EventTime eventTime) {}

  /**
   * @deprecated Seeks are processed without delay. Use {@link #onPositionDiscontinuity(EventTime,
   *     int)} with reason {@link Player#DISCONTINUITY_REASON_SEEK} instead.
   */
  @Deprecated
  default void onSeekProcessed(EventTime eventTime) {}

  /**
   * Called when the playback parameters changed.
   *
   * @param eventTime The event time.
   * @param playbackParameters The new playback parameters.
   */
  default void onPlaybackParametersChanged(
      EventTime eventTime, PlaybackParameters playbackParameters) {}

  /**
   * Called when the seek back increment changed.
   *
   * @param eventTime The event time.
   * @param seekBackIncrementMs The seek back increment, in milliseconds.
   */
  default void onSeekBackIncrementChanged(EventTime eventTime, long seekBackIncrementMs) {}

  /**
   * Called when the seek forward increment changed.
   *
   * @param eventTime The event time.
   * @param seekForwardIncrementMs The seek forward increment, in milliseconds.
   */
  default void onSeekForwardIncrementChanged(EventTime eventTime, long seekForwardIncrementMs) {}

  /**
   * Called when the maximum position for which {@link Player#seekToPrevious()} seeks to the
   * previous window changes.
   *
   * @param eventTime The event time.
   * @param maxSeekToPreviousPositionMs The maximum seek to previous position, in milliseconds.
   */
  default void onMaxSeekToPreviousPositionChanged(
      EventTime eventTime, long maxSeekToPreviousPositionMs) {}

  /**
   * Called when the repeat mode changed.
   *
   * @param eventTime The event time.
   * @param repeatMode The new repeat mode.
   */
  default void onRepeatModeChanged(EventTime eventTime, @Player.RepeatMode int repeatMode) {}

  /**
   * Called when the shuffle mode changed.
   *
   * @param eventTime The event time.
   * @param shuffleModeEnabled Whether the shuffle mode is enabled.
   */
  default void onShuffleModeChanged(EventTime eventTime, boolean shuffleModeEnabled) {}

  /**
   * Called when the player starts or stops loading data from a source.
   *
   * @param eventTime The event time.
   * @param isLoading Whether the player is loading.
   */
  default void onIsLoadingChanged(EventTime eventTime, boolean isLoading) {}

  /**
   * @deprecated Use {@link #onIsLoadingChanged(EventTime, boolean)} instead.
   */
  @Deprecated
  default void onLoadingChanged(EventTime eventTime, boolean isLoading) {}

  /**
   * Called when the player's available commands changed.
   *
   * @param eventTime The event time.
   * @param availableCommands The available commands.
   */
  default void onAvailableCommandsChanged(EventTime eventTime, Player.Commands availableCommands) {}

  /**
   * Called when a fatal player error occurred.
   *
   * <p>Implementations of {@link Player} may pass an instance of a subclass of {@link
   * PlaybackException} to this method in order to include more information about the error.
   *
   * @param eventTime The event time.
   * @param error The error.
   */
  default void onPlayerError(EventTime eventTime, PlaybackException error) {}

  /**
   * Called when the {@link PlaybackException} returned by {@link Player#getPlayerError()} changes.
   *
   * <p>Implementations of Player may pass an instance of a subclass of {@link PlaybackException} to
   * this method in order to include more information about the error.
   *
   * @param eventTime The event time.
   * @param error The new error, or null if the error is being cleared.
   */
  default void onPlayerErrorChanged(EventTime eventTime, @Nullable PlaybackException error) {}

  /**
   * Called when the tracks change.
   *
   * @param eventTime The event time.
   * @param tracks The tracks. Never null, but may be of length zero.
   */
  default void onTracksChanged(EventTime eventTime, Tracks tracks) {}

  /**
   * Called when track selection parameters change.
   *
   * @param eventTime The event time.
   * @param trackSelectionParameters The new {@link TrackSelectionParameters}.
   */
  default void onTrackSelectionParametersChanged(
      EventTime eventTime, TrackSelectionParameters trackSelectionParameters) {}

  /**
   * Called when the combined {@link MediaMetadata} changes.
   *
   * <p>The provided {@link MediaMetadata} is a combination of the {@link MediaItem#mediaMetadata}
   * and the static and dynamic metadata from the {@link TrackSelection#getFormat(int) track
   * selections' formats} and {@link MetadataOutput#onMetadata(Metadata)}.
   *
   * @param eventTime The event time.
   * @param mediaMetadata The combined {@link MediaMetadata}.
   */
  default void onMediaMetadataChanged(EventTime eventTime, MediaMetadata mediaMetadata) {}

  /**
   * Called when the playlist {@link MediaMetadata} changes.
   *
   * @param eventTime The event time.
   * @param playlistMetadata The playlist {@link MediaMetadata}.
   */
  default void onPlaylistMetadataChanged(EventTime eventTime, MediaMetadata playlistMetadata) {}

  /**
   * Called when a media source started loading data.
   *
   * @param eventTime The event time.
   * @param loadEventInfo The {@link LoadEventInfo} defining the load event.
   * @param mediaLoadData The {@link MediaLoadData} defining the data being loaded.
   */
  default void onLoadStarted(
      EventTime eventTime, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {}

  /**
   * Called when a media source completed loading data.
   *
   * @param eventTime The event time.
   * @param loadEventInfo The {@link LoadEventInfo} defining the load event.
   * @param mediaLoadData The {@link MediaLoadData} defining the data being loaded.
   */
  default void onLoadCompleted(
      EventTime eventTime, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {}

  /**
   * Called when a media source canceled loading data.
   *
   * @param eventTime The event time.
   * @param loadEventInfo The {@link LoadEventInfo} defining the load event.
   * @param mediaLoadData The {@link MediaLoadData} defining the data being loaded.
   */
  default void onLoadCanceled(
      EventTime eventTime, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {}

  /**
   * Called when a media source loading error occurred.
   *
   * <p>This method being called does not indicate that playback has failed, or that it will fail.
   * The player may be able to recover from the error. Hence applications should <em>not</em>
   * implement this method to display a user visible error or initiate an application level retry.
   * {@link Player.Listener#onPlayerError} is the appropriate place to implement such behavior. This
   * method is called to provide the application with an opportunity to log the error if it wishes
   * to do so.
   *
   * @param eventTime The event time.
   * @param loadEventInfo The {@link LoadEventInfo} defining the load event.
   * @param mediaLoadData The {@link MediaLoadData} defining the data being loaded.
   * @param error The load error.
   * @param wasCanceled Whether the load was canceled as a result of the error.
   */
  default void onLoadError(
      EventTime eventTime,
      LoadEventInfo loadEventInfo,
      MediaLoadData mediaLoadData,
      IOException error,
      boolean wasCanceled) {}

  /**
   * Called when the downstream format sent to the renderers changed.
   *
   * @param eventTime The event time.
   * @param mediaLoadData The {@link MediaLoadData} defining the newly selected media data.
   */
  default void onDownstreamFormatChanged(EventTime eventTime, MediaLoadData mediaLoadData) {}

  /**
   * Called when data is removed from the back of a media buffer, typically so that it can be
   * re-buffered in a different format.
   *
   * @param eventTime The event time.
   * @param mediaLoadData The {@link MediaLoadData} defining the media being discarded.
   */
  default void onUpstreamDiscarded(EventTime eventTime, MediaLoadData mediaLoadData) {}

  /**
   * Called when the bandwidth estimate for the current data source has been updated.
   *
   * @param eventTime The event time.
   * @param totalLoadTimeMs The total time spend loading this update is based on, in milliseconds.
   * @param totalBytesLoaded The total bytes loaded this update is based on.
   * @param bitrateEstimate The bandwidth estimate, in bits per second.
   */
  default void onBandwidthEstimate(
      EventTime eventTime, int totalLoadTimeMs, long totalBytesLoaded, long bitrateEstimate) {}

  /**
   * Called when there is {@link Metadata} associated with the current playback time.
   *
   * @param eventTime The event time.
   * @param metadata The metadata.
   */
  default void onMetadata(EventTime eventTime, Metadata metadata) {}

  /**
   * Called when there is a change in the {@link Cue Cues}.
   *
   * <p>Both {@link #onCues(EventTime, List)} and {@link #onCues(EventTime, CueGroup)} are called
   * when there is a change in the cues. You should only implement one or the other.
   *
   * @param eventTime The event time.
   * @param cues The {@link Cue Cues}.
   * @deprecated Use {@link #onCues(EventTime, CueGroup)} instead.
   */
  @Deprecated
  default void onCues(EventTime eventTime, List<Cue> cues) {}

  /**
   * Called when there is a change in the {@link CueGroup}.
   *
   * <p>Both {@link #onCues(EventTime, List)} and {@link #onCues(EventTime, CueGroup)} are called
   * when there is a change in the cues. You should only implement one or the other.
   *
   * @param eventTime The event time.
   * @param cueGroup The {@link CueGroup}.
   */
  default void onCues(EventTime eventTime, CueGroup cueGroup) {}

  /**
   * @deprecated Use {@link #onAudioEnabled} and {@link #onVideoEnabled} instead.
   */
  @Deprecated
  default void onDecoderEnabled(
      EventTime eventTime, int trackType, DecoderCounters decoderCounters) {}

  /**
   * @deprecated Use {@link #onAudioDecoderInitialized} and {@link #onVideoDecoderInitialized}
   *     instead.
   */
  @Deprecated
  default void onDecoderInitialized(
      EventTime eventTime, int trackType, String decoderName, long initializationDurationMs) {}

  /**
   * @deprecated Use {@link #onAudioInputFormatChanged(EventTime, Format, DecoderReuseEvaluation)}
   *     and {@link #onVideoInputFormatChanged(EventTime, Format, DecoderReuseEvaluation)}. instead.
   */
  @Deprecated
  default void onDecoderInputFormatChanged(EventTime eventTime, int trackType, Format format) {}

  /**
   * @deprecated Use {@link #onAudioDisabled} and {@link #onVideoDisabled} instead.
   */
  @Deprecated
  default void onDecoderDisabled(
      EventTime eventTime, int trackType, DecoderCounters decoderCounters) {}

  /**
   * Called when an audio renderer is enabled.
   *
   * @param eventTime The event time.
   * @param decoderCounters {@link DecoderCounters} that will be updated by the renderer for as long
   *     as it remains enabled.
   */
  default void onAudioEnabled(EventTime eventTime, DecoderCounters decoderCounters) {}

  /**
   * Called when an audio renderer creates a decoder.
   *
   * @param eventTime The event time.
   * @param decoderName The decoder that was created.
   * @param initializedTimestampMs {@link SystemClock#elapsedRealtime()} when initialization
   *     finished.
   * @param initializationDurationMs The time taken to initialize the decoder in milliseconds.
   */
  default void onAudioDecoderInitialized(
      EventTime eventTime,
      String decoderName,
      long initializedTimestampMs,
      long initializationDurationMs) {}

  /**
   * @deprecated Use {@link #onAudioDecoderInitialized(EventTime, String, long, long)}.
   */
  @Deprecated
  default void onAudioDecoderInitialized(
      EventTime eventTime, String decoderName, long initializationDurationMs) {}

  /**
   * @deprecated Use {@link #onAudioInputFormatChanged(EventTime, Format, DecoderReuseEvaluation)}.
   */
  @Deprecated
  default void onAudioInputFormatChanged(EventTime eventTime, Format format) {}

  /**
   * Called when the format of the media being consumed by an audio renderer changes.
   *
   * @param eventTime The event time.
   * @param format The new format.
   * @param decoderReuseEvaluation The result of the evaluation to determine whether an existing
   *     decoder instance can be reused for the new format, or {@code null} if the renderer did not
   *     have a decoder.
   */
  default void onAudioInputFormatChanged(
      EventTime eventTime,
      Format format,
      @Nullable DecoderReuseEvaluation decoderReuseEvaluation) {}

  /**
   * Called when the audio position has increased for the first time since the last pause or
   * position reset.
   *
   * @param eventTime The event time.
   * @param playoutStartSystemTimeMs The approximate derived {@link System#currentTimeMillis()} at
   *     which playout started.
   */
  default void onAudioPositionAdvancing(EventTime eventTime, long playoutStartSystemTimeMs) {}

  /**
   * Called when an audio underrun occurs.
   *
   * @param eventTime The event time.
   * @param bufferSize The size of the audio output buffer, in bytes.
   * @param bufferSizeMs The size of the audio output buffer, in milliseconds, if it contains PCM
   *     encoded audio. {@link C#TIME_UNSET} if the output buffer contains non-PCM encoded audio.
   * @param elapsedSinceLastFeedMs The time since audio was last written to the output buffer.
   */
  default void onAudioUnderrun(
      EventTime eventTime, int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs) {}

  /**
   * Called when an audio renderer releases a decoder.
   *
   * @param eventTime The event time.
   * @param decoderName The decoder that was released.
   */
  default void onAudioDecoderReleased(EventTime eventTime, String decoderName) {}

  /**
   * Called when an audio renderer is disabled.
   *
   * @param eventTime The event time.
   * @param decoderCounters {@link DecoderCounters} that were updated by the renderer.
   */
  default void onAudioDisabled(EventTime eventTime, DecoderCounters decoderCounters) {}

  /**
   * Called when the audio session ID changes.
   *
   * @param eventTime The event time.
   * @param audioSessionId The audio session ID.
   */
  default void onAudioSessionIdChanged(EventTime eventTime, int audioSessionId) {}

  /**
   * Called when the audio attributes change.
   *
   * @param eventTime The event time.
   * @param audioAttributes The audio attributes.
   */
  default void onAudioAttributesChanged(EventTime eventTime, AudioAttributes audioAttributes) {}

  /**
   * Called when skipping silences is enabled or disabled in the audio stream.
   *
   * @param eventTime The event time.
   * @param skipSilenceEnabled Whether skipping silences in the audio stream is enabled.
   */
  default void onSkipSilenceEnabledChanged(EventTime eventTime, boolean skipSilenceEnabled) {}

  /**
   * Called when {@link AudioSink} has encountered an error.
   *
   * <p>This method being called does not indicate that playback has failed, or that it will fail.
   * The player may be able to recover from the error. Hence applications should <em>not</em>
   * implement this method to display a user visible error or initiate an application level retry.
   * {@link Player.Listener#onPlayerError} is the appropriate place to implement such behavior. This
   * method is called to provide the application with an opportunity to log the error if it wishes
   * to do so.
   *
   * @param eventTime The event time.
   * @param audioSinkError The error that occurred. Typically an {@link
   *     AudioSink.InitializationException}, a {@link AudioSink.WriteException}, or an {@link
   *     AudioSink.UnexpectedDiscontinuityException}.
   */
  default void onAudioSinkError(EventTime eventTime, Exception audioSinkError) {}

  /**
   * Called when an audio decoder encounters an error.
   *
   * <p>This method being called does not indicate that playback has failed, or that it will fail.
   * The player may be able to recover from the error. Hence applications should <em>not</em>
   * implement this method to display a user visible error or initiate an application level retry.
   * {@link Player.Listener#onPlayerError} is the appropriate place to implement such behavior. This
   * method is called to provide the application with an opportunity to log the error if it wishes
   * to do so.
   *
   * @param eventTime The event time.
   * @param audioCodecError The error. Typically a {@link CodecException} if the renderer uses
   *     {@link MediaCodec}, or a {@link DecoderException} if the renderer uses a software decoder.
   */
  default void onAudioCodecError(EventTime eventTime, Exception audioCodecError) {}

  /**
   * Called when the volume changes.
   *
   * @param eventTime The event time.
   * @param volume The new volume, with 0 being silence and 1 being unity gain.
   */
  default void onVolumeChanged(EventTime eventTime, float volume) {}

  /**
   * Called when the device information changes
   *
   * @param eventTime The event time.
   * @param deviceInfo The new {@link DeviceInfo}.
   */
  default void onDeviceInfoChanged(EventTime eventTime, DeviceInfo deviceInfo) {}

  /**
   * Called when the device volume or mute state changes.
   *
   * @param eventTime The event time.
   * @param volume The new device volume, with 0 being silence and 1 being unity gain.
   * @param muted Whether the device is muted.
   */
  default void onDeviceVolumeChanged(EventTime eventTime, int volume, boolean muted) {}

  /**
   * Called when a video renderer is enabled.
   *
   * @param eventTime The event time.
   * @param decoderCounters {@link DecoderCounters} that will be updated by the renderer for as long
   *     as it remains enabled.
   */
  default void onVideoEnabled(EventTime eventTime, DecoderCounters decoderCounters) {}

  /**
   * Called when a video renderer creates a decoder.
   *
   * @param eventTime The event time.
   * @param decoderName The decoder that was created.
   * @param initializedTimestampMs {@link SystemClock#elapsedRealtime()} when initialization
   *     finished.
   * @param initializationDurationMs The time taken to initialize the decoder in milliseconds.
   */
  default void onVideoDecoderInitialized(
      EventTime eventTime,
      String decoderName,
      long initializedTimestampMs,
      long initializationDurationMs) {}

  /**
   * @deprecated Use {@link #onVideoDecoderInitialized(EventTime, String, long, long)}.
   */
  @Deprecated
  default void onVideoDecoderInitialized(
      EventTime eventTime, String decoderName, long initializationDurationMs) {}

  /**
   * @deprecated Use {@link #onVideoInputFormatChanged(EventTime, Format, DecoderReuseEvaluation)}.
   */
  @Deprecated
  default void onVideoInputFormatChanged(EventTime eventTime, Format format) {}

  /**
   * Called when the format of the media being consumed by a video renderer changes.
   *
   * @param eventTime The event time.
   * @param format The new format.
   * @param decoderReuseEvaluation The result of the evaluation to determine whether an existing
   *     decoder instance can be reused for the new format, or {@code null} if the renderer did not
   *     have a decoder.
   */
  default void onVideoInputFormatChanged(
      EventTime eventTime,
      Format format,
      @Nullable DecoderReuseEvaluation decoderReuseEvaluation) {}

  /**
   * Called after video frames have been dropped.
   *
   * @param eventTime The event time.
   * @param droppedFrames The number of dropped frames since the last call to this method.
   * @param elapsedMs The duration in milliseconds over which the frames were dropped. This duration
   *     is timed from when the renderer was started or from when dropped frames were last reported
   *     (whichever was more recent), and not from when the first of the reported drops occurred.
   */
  default void onDroppedVideoFrames(EventTime eventTime, int droppedFrames, long elapsedMs) {}

  /**
   * Called when a video renderer releases a decoder.
   *
   * @param eventTime The event time.
   * @param decoderName The decoder that was released.
   */
  default void onVideoDecoderReleased(EventTime eventTime, String decoderName) {}

  /**
   * Called when a video renderer is disabled.
   *
   * @param eventTime The event time.
   * @param decoderCounters {@link DecoderCounters} that were updated by the renderer.
   */
  default void onVideoDisabled(EventTime eventTime, DecoderCounters decoderCounters) {}

  /**
   * Called when there is an update to the video frame processing offset reported by a video
   * renderer.
   *
   * <p>The processing offset for a video frame is the difference between the time at which the
   * frame became available to render, and the time at which it was scheduled to be rendered. A
   * positive value indicates the frame became available early enough, whereas a negative value
   * indicates that the frame wasn't available until after the time at which it should have been
   * rendered.
   *
   * @param eventTime The event time.
   * @param totalProcessingOffsetUs The sum of the video frame processing offsets for frames
   *     rendered since the last call to this method.
   * @param frameCount The number to samples included in {@code totalProcessingOffsetUs}.
   */
  default void onVideoFrameProcessingOffset(
      EventTime eventTime, long totalProcessingOffsetUs, int frameCount) {}

  /**
   * Called when a video decoder encounters an error.
   *
   * <p>This method being called does not indicate that playback has failed, or that it will fail.
   * The player may be able to recover from the error. Hence applications should <em>not</em>
   * implement this method to display a user visible error or initiate an application level retry.
   * {@link Player.Listener#onPlayerError} is the appropriate place to implement such behavior. This
   * method is called to provide the application with an opportunity to log the error if it wishes
   * to do so.
   *
   * @param eventTime The event time.
   * @param videoCodecError The error. Typically a {@link CodecException} if the renderer uses
   *     {@link MediaCodec}, or a {@link DecoderException} if the renderer uses a software decoder.
   */
  default void onVideoCodecError(EventTime eventTime, Exception videoCodecError) {}

  /**
   * Called when a frame is rendered for the first time since setting the surface, or since the
   * renderer was reset, or since the stream being rendered was changed.
   *
   * @param eventTime The event time.
   * @param output The output to which a frame has been rendered. Normally a {@link Surface},
   *     however may also be other output types (e.g., a {@link VideoDecoderOutputBufferRenderer}).
   * @param renderTimeMs {@link SystemClock#elapsedRealtime()} when the first frame was rendered.
   */
  default void onRenderedFirstFrame(EventTime eventTime, Object output, long renderTimeMs) {}

  /**
   * Called before a frame is rendered for the first time since setting the surface, and each time
   * there's a change in the size or pixel aspect ratio of the video being rendered.
   *
   * @param eventTime The event time.
   * @param videoSize The new size of the video.
   */
  default void onVideoSizeChanged(EventTime eventTime, VideoSize videoSize) {}

  /**
   * @deprecated Implement {@link #onVideoSizeChanged(EventTime eventTime, VideoSize)} instead.
   */
  @Deprecated
  default void onVideoSizeChanged(
      EventTime eventTime,
      int width,
      int height,
      int unappliedRotationDegrees,
      float pixelWidthHeightRatio) {}

  /**
   * Called when the output surface size changed.
   *
   * @param eventTime The event time.
   * @param width The surface width in pixels. May be {@link C#LENGTH_UNSET} if unknown, or 0 if the
   *     video is not rendered onto a surface.
   * @param height The surface height in pixels. May be {@link C#LENGTH_UNSET} if unknown, or 0 if
   *     the video is not rendered onto a surface.
   */
  default void onSurfaceSizeChanged(EventTime eventTime, int width, int height) {}

  /**
   * @deprecated Implement {@link #onDrmSessionAcquired(EventTime, int)} instead.
   */
  @Deprecated
  default void onDrmSessionAcquired(EventTime eventTime) {}

  /**
   * Called each time a drm session is acquired.
   *
   * @param eventTime The event time.
   * @param state The {@link DrmSession.State} of the session when the acquisition completed.
   */
  default void onDrmSessionAcquired(EventTime eventTime, @DrmSession.State int state) {}

  /**
   * Called each time drm keys are loaded.
   *
   * @param eventTime The event time.
   */
  default void onDrmKeysLoaded(EventTime eventTime) {}

  /**
   * Called when a drm error occurs.
   *
   * <p>This method being called does not indicate that playback has failed, or that it will fail.
   * The player may be able to recover from the error. Hence applications should <em>not</em>
   * implement this method to display a user visible error or initiate an application level retry.
   * {@link Player.Listener#onPlayerError} is the appropriate place to implement such behavior. This
   * method is called to provide the application with an opportunity to log the error if it wishes
   * to do so.
   *
   * @param eventTime The event time.
   * @param error The error.
   */
  default void onDrmSessionManagerError(EventTime eventTime, Exception error) {}

  /**
   * Called each time offline drm keys are restored.
   *
   * @param eventTime The event time.
   */
  default void onDrmKeysRestored(EventTime eventTime) {}

  /**
   * Called each time offline drm keys are removed.
   *
   * @param eventTime The event time.
   */
  default void onDrmKeysRemoved(EventTime eventTime) {}

  /**
   * Called each time a drm session is released.
   *
   * @param eventTime The event time.
   */
  default void onDrmSessionReleased(EventTime eventTime) {}

  /**
   * Called when the {@link Player} is released.
   *
   * @param eventTime The event time.
   */
  default void onPlayerReleased(EventTime eventTime) {}

  /**
   * Called after one or more events occurred.
   *
   * <p>State changes and events that happen within one {@link Looper} message queue iteration are
   * reported together and only after all individual callbacks were triggered.
   *
   * <p>Listeners should prefer this method over individual callbacks in the following cases:
   *
   * <ul>
   *   <li>They intend to trigger the same logic for multiple events (e.g. when updating a UI for
   *       both {@link #onPlaybackStateChanged(EventTime, int)} and {@link
   *       #onPlayWhenReadyChanged(EventTime, boolean, int)}).
   *   <li>They need access to the {@link Player} object to trigger further events (e.g. to call
   *       {@link Player#seekTo(long)} after a {@link
   *       AnalyticsListener#onMediaItemTransition(EventTime, MediaItem, int)}).
   *   <li>They intend to use multiple state values together or in combination with {@link Player}
   *       getter methods. For example using {@link Player#getCurrentMediaItemIndex()} with the
   *       {@code timeline} provided in {@link #onTimelineChanged(EventTime, int)} is only safe from
   *       within this method.
   *   <li>They are interested in events that logically happened together (e.g {@link
   *       #onPlaybackStateChanged(EventTime, int)} to {@link Player#STATE_BUFFERING} because of
   *       {@link #onMediaItemTransition(EventTime, MediaItem, int)}).
   * </ul>
   *
   * @param player The {@link Player}.
   * @param events The {@link Events} that occurred in this iteration.
   */
  default void onEvents(Player player, Events events) {}
}
