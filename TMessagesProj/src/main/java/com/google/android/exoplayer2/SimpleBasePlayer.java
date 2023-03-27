/*
 * Copyright 2022 The Android Open Source Project
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
package com.google.android.exoplayer2;

import static androidx.annotation.VisibleForTesting.PROTECTED;
import static com.google.android.exoplayer2.util.Assertions.checkArgument;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Util.castNonNull;
import static com.google.android.exoplayer2.util.Util.msToUs;
import static com.google.android.exoplayer2.util.Util.usToMs;
import static java.lang.Math.max;
import static java.lang.Math.min;

import android.graphics.Rect;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Pair;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import androidx.annotation.FloatRange;
import androidx.annotation.IntRange;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.source.ads.AdPlaybackState;
import com.google.android.exoplayer2.text.CueGroup;
import com.google.android.exoplayer2.trackselection.TrackSelectionParameters;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.HandlerWrapper;
import com.google.android.exoplayer2.util.ListenerSet;
import com.google.android.exoplayer2.util.Size;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.VideoSize;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.ForOverride;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/**
 * A base implementation for {@link Player} that reduces the number of methods to implement to a
 * minimum.
 *
 * <p>Implementation notes:
 *
 * <ul>
 *   <li>Subclasses must override {@link #getState()} to populate the current player state on
 *       request.
 *   <li>The {@link State} should set the {@linkplain State.Builder#setAvailableCommands available
 *       commands} to indicate which {@link Player} methods are supported.
 *   <li>All setter-like player methods (for example, {@link #setPlayWhenReady}) forward to
 *       overridable methods (for example, {@link #handleSetPlayWhenReady}) that can be used to
 *       handle these requests. These methods return a {@link ListenableFuture} to indicate when the
 *       request has been handled and is fully reflected in the values returned from {@link
 *       #getState}. This class will automatically request a state update once the request is done.
 *       If the state changes can be handled synchronously, these methods can return Guava's {@link
 *       Futures#immediateVoidFuture()}.
 *   <li>Subclasses can manually trigger state updates with {@link #invalidateState}, for example if
 *       something changes independent of {@link Player} method calls.
 * </ul>
 *
 * This base class handles various aspects of the player implementation to simplify the subclass:
 *
 * <ul>
 *   <li>The {@link State} can only be created with allowed combinations of state values, avoiding
 *       any invalid player states.
 *   <li>Only functionality that is declared as {@linkplain Player.Command available} needs to be
 *       implemented. Other methods are automatically ignored.
 *   <li>Listener handling and informing listeners of state changes is handled automatically.
 *   <li>The base class provides a framework for asynchronous handling of method calls. It changes
 *       the visible playback state immediately to the most likely outcome to ensure the
 *       user-visible state changes look like synchronous operations. The state is then updated
 *       again once the asynchronous method calls have been fully handled.
 * </ul>
 */
public abstract class SimpleBasePlayer extends BasePlayer {

  /** An immutable state description of the player. */
  protected static final class State {

    /** A builder for {@link State} objects. */
    public static final class Builder {

      private Commands availableCommands;
      private boolean playWhenReady;
      private @PlayWhenReadyChangeReason int playWhenReadyChangeReason;
      private @Player.State int playbackState;
      private @PlaybackSuppressionReason int playbackSuppressionReason;
      @Nullable private PlaybackException playerError;
      private @RepeatMode int repeatMode;
      private boolean shuffleModeEnabled;
      private boolean isLoading;
      private long seekBackIncrementMs;
      private long seekForwardIncrementMs;
      private long maxSeekToPreviousPositionMs;
      private PlaybackParameters playbackParameters;
      private TrackSelectionParameters trackSelectionParameters;
      private AudioAttributes audioAttributes;
      private float volume;
      private VideoSize videoSize;
      private CueGroup currentCues;
      private DeviceInfo deviceInfo;
      private int deviceVolume;
      private boolean isDeviceMuted;
      private Size surfaceSize;
      private boolean newlyRenderedFirstFrame;
      private Metadata timedMetadata;
      private ImmutableList<MediaItemData> playlist;
      private Timeline timeline;
      private MediaMetadata playlistMetadata;
      private int currentMediaItemIndex;
      private int currentAdGroupIndex;
      private int currentAdIndexInAdGroup;
      @Nullable private Long contentPositionMs;
      private PositionSupplier contentPositionMsSupplier;
      @Nullable private Long adPositionMs;
      private PositionSupplier adPositionMsSupplier;
      private PositionSupplier contentBufferedPositionMsSupplier;
      private PositionSupplier adBufferedPositionMsSupplier;
      private PositionSupplier totalBufferedDurationMsSupplier;
      private boolean hasPositionDiscontinuity;
      private @Player.DiscontinuityReason int positionDiscontinuityReason;
      private long discontinuityPositionMs;

      /** Creates the builder. */
      public Builder() {
        availableCommands = Commands.EMPTY;
        playWhenReady = false;
        playWhenReadyChangeReason = Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST;
        playbackState = Player.STATE_IDLE;
        playbackSuppressionReason = Player.PLAYBACK_SUPPRESSION_REASON_NONE;
        playerError = null;
        repeatMode = Player.REPEAT_MODE_OFF;
        shuffleModeEnabled = false;
        isLoading = false;
        seekBackIncrementMs = C.DEFAULT_SEEK_BACK_INCREMENT_MS;
        seekForwardIncrementMs = C.DEFAULT_SEEK_FORWARD_INCREMENT_MS;
        maxSeekToPreviousPositionMs = C.DEFAULT_MAX_SEEK_TO_PREVIOUS_POSITION_MS;
        playbackParameters = PlaybackParameters.DEFAULT;
        trackSelectionParameters = TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT;
        audioAttributes = AudioAttributes.DEFAULT;
        volume = 1f;
        videoSize = VideoSize.UNKNOWN;
        currentCues = CueGroup.EMPTY_TIME_ZERO;
        deviceInfo = DeviceInfo.UNKNOWN;
        deviceVolume = 0;
        isDeviceMuted = false;
        surfaceSize = Size.UNKNOWN;
        newlyRenderedFirstFrame = false;
        timedMetadata = new Metadata(/* presentationTimeUs= */ C.TIME_UNSET);
        playlist = ImmutableList.of();
        timeline = Timeline.EMPTY;
        playlistMetadata = MediaMetadata.EMPTY;
        currentMediaItemIndex = C.INDEX_UNSET;
        currentAdGroupIndex = C.INDEX_UNSET;
        currentAdIndexInAdGroup = C.INDEX_UNSET;
        contentPositionMs = null;
        contentPositionMsSupplier = PositionSupplier.getConstant(C.TIME_UNSET);
        adPositionMs = null;
        adPositionMsSupplier = PositionSupplier.ZERO;
        contentBufferedPositionMsSupplier = PositionSupplier.getConstant(C.TIME_UNSET);
        adBufferedPositionMsSupplier = PositionSupplier.ZERO;
        totalBufferedDurationMsSupplier = PositionSupplier.ZERO;
        hasPositionDiscontinuity = false;
        positionDiscontinuityReason = Player.DISCONTINUITY_REASON_INTERNAL;
        discontinuityPositionMs = 0;
      }

      private Builder(State state) {
        this.availableCommands = state.availableCommands;
        this.playWhenReady = state.playWhenReady;
        this.playWhenReadyChangeReason = state.playWhenReadyChangeReason;
        this.playbackState = state.playbackState;
        this.playbackSuppressionReason = state.playbackSuppressionReason;
        this.playerError = state.playerError;
        this.repeatMode = state.repeatMode;
        this.shuffleModeEnabled = state.shuffleModeEnabled;
        this.isLoading = state.isLoading;
        this.seekBackIncrementMs = state.seekBackIncrementMs;
        this.seekForwardIncrementMs = state.seekForwardIncrementMs;
        this.maxSeekToPreviousPositionMs = state.maxSeekToPreviousPositionMs;
        this.playbackParameters = state.playbackParameters;
        this.trackSelectionParameters = state.trackSelectionParameters;
        this.audioAttributes = state.audioAttributes;
        this.volume = state.volume;
        this.videoSize = state.videoSize;
        this.currentCues = state.currentCues;
        this.deviceInfo = state.deviceInfo;
        this.deviceVolume = state.deviceVolume;
        this.isDeviceMuted = state.isDeviceMuted;
        this.surfaceSize = state.surfaceSize;
        this.newlyRenderedFirstFrame = state.newlyRenderedFirstFrame;
        this.timedMetadata = state.timedMetadata;
        this.playlist = state.playlist;
        this.timeline = state.timeline;
        this.playlistMetadata = state.playlistMetadata;
        this.currentMediaItemIndex = state.currentMediaItemIndex;
        this.currentAdGroupIndex = state.currentAdGroupIndex;
        this.currentAdIndexInAdGroup = state.currentAdIndexInAdGroup;
        this.contentPositionMs = null;
        this.contentPositionMsSupplier = state.contentPositionMsSupplier;
        this.adPositionMs = null;
        this.adPositionMsSupplier = state.adPositionMsSupplier;
        this.contentBufferedPositionMsSupplier = state.contentBufferedPositionMsSupplier;
        this.adBufferedPositionMsSupplier = state.adBufferedPositionMsSupplier;
        this.totalBufferedDurationMsSupplier = state.totalBufferedDurationMsSupplier;
        this.hasPositionDiscontinuity = state.hasPositionDiscontinuity;
        this.positionDiscontinuityReason = state.positionDiscontinuityReason;
        this.discontinuityPositionMs = state.discontinuityPositionMs;
      }

      /**
       * Sets the available {@link Commands}.
       *
       * @param availableCommands The available {@link Commands}.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setAvailableCommands(Commands availableCommands) {
        this.availableCommands = availableCommands;
        return this;
      }

      /**
       * Sets whether playback should proceed when ready and not suppressed.
       *
       * @param playWhenReady Whether playback should proceed when ready and not suppressed.
       * @param playWhenReadyChangeReason The {@linkplain PlayWhenReadyChangeReason reason} for
       *     changing the value.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setPlayWhenReady(
          boolean playWhenReady, @PlayWhenReadyChangeReason int playWhenReadyChangeReason) {
        this.playWhenReady = playWhenReady;
        this.playWhenReadyChangeReason = playWhenReadyChangeReason;
        return this;
      }

      /**
       * Sets the {@linkplain Player.State state} of the player.
       *
       * <p>If the {@linkplain #setPlaylist playlist} is empty, the state must be either {@link
       * Player#STATE_IDLE} or {@link Player#STATE_ENDED}.
       *
       * @param playbackState The {@linkplain Player.State state} of the player.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setPlaybackState(@Player.State int playbackState) {
        this.playbackState = playbackState;
        return this;
      }

      /**
       * Sets the reason why playback is suppressed even if {@link #getPlayWhenReady()} is true.
       *
       * @param playbackSuppressionReason The {@link Player.PlaybackSuppressionReason} why playback
       *     is suppressed even if {@link #getPlayWhenReady()} is true.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setPlaybackSuppressionReason(
          @Player.PlaybackSuppressionReason int playbackSuppressionReason) {
        this.playbackSuppressionReason = playbackSuppressionReason;
        return this;
      }

      /**
       * Sets last error that caused playback to fail, or null if there was no error.
       *
       * <p>The {@linkplain #setPlaybackState playback state} must be set to {@link
       * Player#STATE_IDLE} while an error is set.
       *
       * @param playerError The last error that caused playback to fail, or null if there was no
       *     error.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setPlayerError(@Nullable PlaybackException playerError) {
        this.playerError = playerError;
        return this;
      }

      /**
       * Sets the {@link RepeatMode} used for playback.
       *
       * @param repeatMode The {@link RepeatMode} used for playback.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setRepeatMode(@Player.RepeatMode int repeatMode) {
        this.repeatMode = repeatMode;
        return this;
      }

      /**
       * Sets whether shuffling of media items is enabled.
       *
       * @param shuffleModeEnabled Whether shuffling of media items is enabled.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setShuffleModeEnabled(boolean shuffleModeEnabled) {
        this.shuffleModeEnabled = shuffleModeEnabled;
        return this;
      }

      /**
       * Sets whether the player is currently loading its source.
       *
       * <p>The player can not be marked as loading if the {@linkplain #setPlaybackState state} is
       * {@link Player#STATE_IDLE} or {@link Player#STATE_ENDED}.
       *
       * @param isLoading Whether the player is currently loading its source.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setIsLoading(boolean isLoading) {
        this.isLoading = isLoading;
        return this;
      }

      /**
       * Sets the {@link Player#seekBack()} increment in milliseconds.
       *
       * @param seekBackIncrementMs The {@link Player#seekBack()} increment in milliseconds.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setSeekBackIncrementMs(long seekBackIncrementMs) {
        this.seekBackIncrementMs = seekBackIncrementMs;
        return this;
      }

      /**
       * Sets the {@link Player#seekForward()} increment in milliseconds.
       *
       * @param seekForwardIncrementMs The {@link Player#seekForward()} increment in milliseconds.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setSeekForwardIncrementMs(long seekForwardIncrementMs) {
        this.seekForwardIncrementMs = seekForwardIncrementMs;
        return this;
      }

      /**
       * Sets the maximum position for which {@link #seekToPrevious()} seeks to the previous item,
       * in milliseconds.
       *
       * @param maxSeekToPreviousPositionMs The maximum position for which {@link #seekToPrevious()}
       *     seeks to the previous item, in milliseconds.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setMaxSeekToPreviousPositionMs(long maxSeekToPreviousPositionMs) {
        this.maxSeekToPreviousPositionMs = maxSeekToPreviousPositionMs;
        return this;
      }

      /**
       * Sets the currently active {@link PlaybackParameters}.
       *
       * @param playbackParameters The currently active {@link PlaybackParameters}.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setPlaybackParameters(PlaybackParameters playbackParameters) {
        this.playbackParameters = playbackParameters;
        return this;
      }

      /**
       * Sets the currently active {@link TrackSelectionParameters}.
       *
       * @param trackSelectionParameters The currently active {@link TrackSelectionParameters}.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setTrackSelectionParameters(
          TrackSelectionParameters trackSelectionParameters) {
        this.trackSelectionParameters = trackSelectionParameters;
        return this;
      }

      /**
       * Sets the current {@link AudioAttributes}.
       *
       * @param audioAttributes The current {@link AudioAttributes}.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setAudioAttributes(AudioAttributes audioAttributes) {
        this.audioAttributes = audioAttributes;
        return this;
      }

      /**
       * Sets the current audio volume, with 0 being silence and 1 being unity gain (signal
       * unchanged).
       *
       * @param volume The current audio volume, with 0 being silence and 1 being unity gain (signal
       *     unchanged).
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setVolume(@FloatRange(from = 0, to = 1.0) float volume) {
        checkArgument(volume >= 0.0f && volume <= 1.0f);
        this.volume = volume;
        return this;
      }

      /**
       * Sets the current video size.
       *
       * @param videoSize The current video size.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setVideoSize(VideoSize videoSize) {
        this.videoSize = videoSize;
        return this;
      }

      /**
       * Sets the current {@linkplain CueGroup cues}.
       *
       * @param currentCues The current {@linkplain CueGroup cues}.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setCurrentCues(CueGroup currentCues) {
        this.currentCues = currentCues;
        return this;
      }

      /**
       * Sets the {@link DeviceInfo}.
       *
       * @param deviceInfo The {@link DeviceInfo}.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setDeviceInfo(DeviceInfo deviceInfo) {
        this.deviceInfo = deviceInfo;
        return this;
      }

      /**
       * Sets the current device volume.
       *
       * @param deviceVolume The current device volume.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setDeviceVolume(@IntRange(from = 0) int deviceVolume) {
        checkArgument(deviceVolume >= 0);
        this.deviceVolume = deviceVolume;
        return this;
      }

      /**
       * Sets whether the device is muted.
       *
       * @param isDeviceMuted Whether the device is muted.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setIsDeviceMuted(boolean isDeviceMuted) {
        this.isDeviceMuted = isDeviceMuted;
        return this;
      }

      /**
       * Sets the size of the surface onto which the video is being rendered.
       *
       * @param surfaceSize The surface size. Dimensions may be {@link C#LENGTH_UNSET} if unknown,
       *     or 0 if the video is not rendered onto a surface.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setSurfaceSize(Size surfaceSize) {
        this.surfaceSize = surfaceSize;
        return this;
      }

      /**
       * Sets whether a frame has been rendered for the first time since setting the surface, a
       * rendering reset, or since the stream being rendered was changed.
       *
       * <p>Note: As this will trigger a {@link Listener#onRenderedFirstFrame()} event, the flag
       * should only be set for the first {@link State} update after the first frame was rendered.
       *
       * @param newlyRenderedFirstFrame Whether the first frame was newly rendered.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setNewlyRenderedFirstFrame(boolean newlyRenderedFirstFrame) {
        this.newlyRenderedFirstFrame = newlyRenderedFirstFrame;
        return this;
      }

      /**
       * Sets the most recent timed {@link Metadata}.
       *
       * <p>Metadata with a {@link Metadata#presentationTimeUs} of {@link C#TIME_UNSET} will not be
       * forwarded to listeners.
       *
       * @param timedMetadata The most recent timed {@link Metadata}.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setTimedMetadata(Metadata timedMetadata) {
        this.timedMetadata = timedMetadata;
        return this;
      }

      /**
       * Sets the list of {@link MediaItemData media items} in the playlist.
       *
       * <p>All items must have unique {@linkplain MediaItemData.Builder#setUid UIDs}.
       *
       * @param playlist The list of {@link MediaItemData media items} in the playlist.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setPlaylist(List<MediaItemData> playlist) {
        HashSet<Object> uids = new HashSet<>();
        for (int i = 0; i < playlist.size(); i++) {
          checkArgument(uids.add(playlist.get(i).uid), "Duplicate MediaItemData UID in playlist");
        }
        this.playlist = ImmutableList.copyOf(playlist);
        this.timeline = new PlaylistTimeline(this.playlist);
        return this;
      }

      /**
       * Sets the playlist {@link MediaMetadata}.
       *
       * @param playlistMetadata The playlist {@link MediaMetadata}.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setPlaylistMetadata(MediaMetadata playlistMetadata) {
        this.playlistMetadata = playlistMetadata;
        return this;
      }

      /**
       * Sets the current media item index.
       *
       * <p>The media item index must be less than the number of {@linkplain #setPlaylist media
       * items in the playlist}, if set.
       *
       * @param currentMediaItemIndex The current media item index, or {@link C#INDEX_UNSET} to
       *     assume the default first item in the playlist.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setCurrentMediaItemIndex(int currentMediaItemIndex) {
        this.currentMediaItemIndex = currentMediaItemIndex;
        return this;
      }

      /**
       * Sets the current ad indices, or {@link C#INDEX_UNSET} if no ad is playing.
       *
       * <p>Either both indices need to be {@link C#INDEX_UNSET} or both are not {@link
       * C#INDEX_UNSET}.
       *
       * <p>Ads indices can only be set if there is a corresponding {@link AdPlaybackState} defined
       * in the current {@linkplain MediaItemData.Builder#setPeriods period}.
       *
       * @param adGroupIndex The current ad group index, or {@link C#INDEX_UNSET} if no ad is
       *     playing.
       * @param adIndexInAdGroup The current ad index in the ad group, or {@link C#INDEX_UNSET} if
       *     no ad is playing.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setCurrentAd(int adGroupIndex, int adIndexInAdGroup) {
        checkArgument((adGroupIndex == C.INDEX_UNSET) == (adIndexInAdGroup == C.INDEX_UNSET));
        this.currentAdGroupIndex = adGroupIndex;
        this.currentAdIndexInAdGroup = adIndexInAdGroup;
        return this;
      }

      /**
       * Sets the current content playback position in milliseconds.
       *
       * <p>This position will be converted to an advancing {@link PositionSupplier} if the overall
       * state indicates an advancing playback position.
       *
       * <p>This method overrides any other {@link PositionSupplier} set via {@link
       * #setContentPositionMs(PositionSupplier)}.
       *
       * @param positionMs The current content playback position in milliseconds, or {@link
       *     C#TIME_UNSET} to indicate the default start position.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setContentPositionMs(long positionMs) {
        this.contentPositionMs = positionMs;
        return this;
      }

      /**
       * Sets the {@link PositionSupplier} for the current content playback position in
       * milliseconds.
       *
       * <p>The supplier is expected to return the updated position on every call if the playback is
       * advancing, for example by using {@link PositionSupplier#getExtrapolating}.
       *
       * <p>This method overrides any other position set via {@link #setContentPositionMs(long)}.
       *
       * @param contentPositionMsSupplier The {@link PositionSupplier} for the current content
       *     playback position in milliseconds, or {@link C#TIME_UNSET} to indicate the default
       *     start position.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setContentPositionMs(PositionSupplier contentPositionMsSupplier) {
        this.contentPositionMs = null;
        this.contentPositionMsSupplier = contentPositionMsSupplier;
        return this;
      }

      /**
       * Sets the current ad playback position in milliseconds. The value is unused if no ad is
       * playing.
       *
       * <p>This position will be converted to an advancing {@link PositionSupplier} if the overall
       * state indicates an advancing ad playback position.
       *
       * <p>This method overrides any other {@link PositionSupplier} set via {@link
       * #setAdPositionMs(PositionSupplier)}.
       *
       * @param positionMs The current ad playback position in milliseconds.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setAdPositionMs(long positionMs) {
        this.adPositionMs = positionMs;
        return this;
      }

      /**
       * Sets the {@link PositionSupplier} for the current ad playback position in milliseconds. The
       * value is unused if no ad is playing.
       *
       * <p>The supplier is expected to return the updated position on every call if the playback is
       * advancing, for example by using {@link PositionSupplier#getExtrapolating}.
       *
       * <p>This method overrides any other position set via {@link #setAdPositionMs(long)}.
       *
       * @param adPositionMsSupplier The {@link PositionSupplier} for the current ad playback
       *     position in milliseconds. The value is unused if no ad is playing.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setAdPositionMs(PositionSupplier adPositionMsSupplier) {
        this.adPositionMs = null;
        this.adPositionMsSupplier = adPositionMsSupplier;
        return this;
      }

      /**
       * Sets the {@link PositionSupplier} for the estimated position up to which the currently
       * playing content is buffered, in milliseconds.
       *
       * @param contentBufferedPositionMsSupplier The {@link PositionSupplier} for the estimated
       *     position up to which the currently playing content is buffered, in milliseconds, or
       *     {@link C#TIME_UNSET} to indicate the default start position.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setContentBufferedPositionMs(
          PositionSupplier contentBufferedPositionMsSupplier) {
        this.contentBufferedPositionMsSupplier = contentBufferedPositionMsSupplier;
        return this;
      }

      /**
       * Sets the {@link PositionSupplier} for the estimated position up to which the currently
       * playing ad is buffered, in milliseconds. The value is unused if no ad is playing.
       *
       * @param adBufferedPositionMsSupplier The {@link PositionSupplier} for the estimated position
       *     up to which the currently playing ad is buffered, in milliseconds. The value is unused
       *     if no ad is playing.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setAdBufferedPositionMs(PositionSupplier adBufferedPositionMsSupplier) {
        this.adBufferedPositionMsSupplier = adBufferedPositionMsSupplier;
        return this;
      }

      /**
       * Sets the {@link PositionSupplier} for the estimated total buffered duration in
       * milliseconds.
       *
       * @param totalBufferedDurationMsSupplier The {@link PositionSupplier} for the estimated total
       *     buffered duration in milliseconds.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setTotalBufferedDurationMs(PositionSupplier totalBufferedDurationMsSupplier) {
        this.totalBufferedDurationMsSupplier = totalBufferedDurationMsSupplier;
        return this;
      }

      /**
       * Signals that a position discontinuity happened since the last player update and sets the
       * reason for it.
       *
       * @param positionDiscontinuityReason The {@linkplain Player.DiscontinuityReason reason} for
       *     the discontinuity.
       * @param discontinuityPositionMs The position, in milliseconds, in the current content or ad
       *     from which playback continues after the discontinuity.
       * @return This builder.
       * @see #clearPositionDiscontinuity
       */
      @CanIgnoreReturnValue
      public Builder setPositionDiscontinuity(
          @Player.DiscontinuityReason int positionDiscontinuityReason,
          long discontinuityPositionMs) {
        this.hasPositionDiscontinuity = true;
        this.positionDiscontinuityReason = positionDiscontinuityReason;
        this.discontinuityPositionMs = discontinuityPositionMs;
        return this;
      }

      /**
       * Clears a previously set position discontinuity signal.
       *
       * @return This builder.
       * @see #hasPositionDiscontinuity
       */
      @CanIgnoreReturnValue
      public Builder clearPositionDiscontinuity() {
        this.hasPositionDiscontinuity = false;
        return this;
      }

      /** Builds the {@link State}. */
      public State build() {
        return new State(this);
      }
    }

    /** The available {@link Commands}. */
    public final Commands availableCommands;
    /** Whether playback should proceed when ready and not suppressed. */
    public final boolean playWhenReady;
    /** The last reason for changing {@link #playWhenReady}. */
    public final @PlayWhenReadyChangeReason int playWhenReadyChangeReason;
    /** The {@linkplain Player.State state} of the player. */
    public final @Player.State int playbackState;
    /** The reason why playback is suppressed even if {@link #getPlayWhenReady()} is true. */
    public final @PlaybackSuppressionReason int playbackSuppressionReason;
    /** The last error that caused playback to fail, or null if there was no error. */
    @Nullable public final PlaybackException playerError;
    /** The {@link RepeatMode} used for playback. */
    public final @RepeatMode int repeatMode;
    /** Whether shuffling of media items is enabled. */
    public final boolean shuffleModeEnabled;
    /** Whether the player is currently loading its source. */
    public final boolean isLoading;
    /** The {@link Player#seekBack()} increment in milliseconds. */
    public final long seekBackIncrementMs;
    /** The {@link Player#seekForward()} increment in milliseconds. */
    public final long seekForwardIncrementMs;
    /**
     * The maximum position for which {@link #seekToPrevious()} seeks to the previous item, in
     * milliseconds.
     */
    public final long maxSeekToPreviousPositionMs;
    /** The currently active {@link PlaybackParameters}. */
    public final PlaybackParameters playbackParameters;
    /** The currently active {@link TrackSelectionParameters}. */
    public final TrackSelectionParameters trackSelectionParameters;
    /** The current {@link AudioAttributes}. */
    public final AudioAttributes audioAttributes;
    /** The current audio volume, with 0 being silence and 1 being unity gain (signal unchanged). */
    @FloatRange(from = 0, to = 1.0)
    public final float volume;
    /** The current video size. */
    public final VideoSize videoSize;
    /** The current {@linkplain CueGroup cues}. */
    public final CueGroup currentCues;
    /** The {@link DeviceInfo}. */
    public final DeviceInfo deviceInfo;
    /** The current device volume. */
    @IntRange(from = 0)
    public final int deviceVolume;
    /** Whether the device is muted. */
    public final boolean isDeviceMuted;
    /** The size of the surface onto which the video is being rendered. */
    public final Size surfaceSize;
    /**
     * Whether a frame has been rendered for the first time since setting the surface, a rendering
     * reset, or since the stream being rendered was changed.
     */
    public final boolean newlyRenderedFirstFrame;
    /** The most recent timed metadata. */
    public final Metadata timedMetadata;
    /** The media items in the playlist. */
    public final ImmutableList<MediaItemData> playlist;
    /** The {@link Timeline} derived from the {@link #playlist}. */
    public final Timeline timeline;
    /** The playlist {@link MediaMetadata}. */
    public final MediaMetadata playlistMetadata;
    /**
     * The current media item index, or {@link C#INDEX_UNSET} to assume the default first item of
     * the playlist is played.
     */
    public final int currentMediaItemIndex;
    /** The current ad group index, or {@link C#INDEX_UNSET} if no ad is playing. */
    public final int currentAdGroupIndex;
    /** The current ad index in the ad group, or {@link C#INDEX_UNSET} if no ad is playing. */
    public final int currentAdIndexInAdGroup;
    /**
     * The {@link PositionSupplier} for the current content playback position in milliseconds, or
     * {@link C#TIME_UNSET} to indicate the default start position.
     */
    public final PositionSupplier contentPositionMsSupplier;
    /**
     * The {@link PositionSupplier} for the current ad playback position in milliseconds. The value
     * is unused if no ad is playing.
     */
    public final PositionSupplier adPositionMsSupplier;
    /**
     * The {@link PositionSupplier} for the estimated position up to which the currently playing
     * content is buffered, in milliseconds, or {@link C#TIME_UNSET} to indicate the default start
     * position.
     */
    public final PositionSupplier contentBufferedPositionMsSupplier;
    /**
     * The {@link PositionSupplier} for the estimated position up to which the currently playing ad
     * is buffered, in milliseconds. The value is unused if no ad is playing.
     */
    public final PositionSupplier adBufferedPositionMsSupplier;
    /** The {@link PositionSupplier} for the estimated total buffered duration in milliseconds. */
    public final PositionSupplier totalBufferedDurationMsSupplier;
    /** Signals that a position discontinuity happened since the last update to the player. */
    public final boolean hasPositionDiscontinuity;
    /**
     * The {@linkplain Player.DiscontinuityReason reason} for the last position discontinuity. The
     * value is unused if {@link #hasPositionDiscontinuity} is {@code false}.
     */
    public final @Player.DiscontinuityReason int positionDiscontinuityReason;
    /**
     * The position, in milliseconds, in the current content or ad from which playback continued
     * after the discontinuity. The value is unused if {@link #hasPositionDiscontinuity} is {@code
     * false}.
     */
    public final long discontinuityPositionMs;

    private State(Builder builder) {
      if (builder.timeline.isEmpty()) {
        checkArgument(
            builder.playbackState == Player.STATE_IDLE
                || builder.playbackState == Player.STATE_ENDED,
            "Empty playlist only allowed in STATE_IDLE or STATE_ENDED");
        checkArgument(
            builder.currentAdGroupIndex == C.INDEX_UNSET
                && builder.currentAdIndexInAdGroup == C.INDEX_UNSET,
            "Ads not allowed if playlist is empty");
      } else {
        int mediaItemIndex = builder.currentMediaItemIndex;
        if (mediaItemIndex == C.INDEX_UNSET) {
          mediaItemIndex = 0; // TODO: Use shuffle order to find first index.
        } else {
          checkArgument(
              builder.currentMediaItemIndex < builder.timeline.getWindowCount(),
              "currentMediaItemIndex must be less than playlist.size()");
        }
        if (builder.currentAdGroupIndex != C.INDEX_UNSET) {
          Timeline.Period period = new Timeline.Period();
          Timeline.Window window = new Timeline.Window();
          long contentPositionMs =
              builder.contentPositionMs != null
                  ? builder.contentPositionMs
                  : builder.contentPositionMsSupplier.get();
          int periodIndex =
              getPeriodIndexFromWindowPosition(
                  builder.timeline, mediaItemIndex, contentPositionMs, window, period);
          builder.timeline.getPeriod(periodIndex, period);
          checkArgument(
              builder.currentAdGroupIndex < period.getAdGroupCount(),
              "PeriodData has less ad groups than adGroupIndex");
          int adCountInGroup = period.getAdCountInAdGroup(builder.currentAdGroupIndex);
          if (adCountInGroup != C.LENGTH_UNSET) {
            checkArgument(
                builder.currentAdIndexInAdGroup < adCountInGroup,
                "Ad group has less ads than adIndexInGroupIndex");
          }
        }
      }
      if (builder.playerError != null) {
        checkArgument(
            builder.playbackState == Player.STATE_IDLE, "Player error only allowed in STATE_IDLE");
      }
      if (builder.playbackState == Player.STATE_IDLE
          || builder.playbackState == Player.STATE_ENDED) {
        checkArgument(
            !builder.isLoading, "isLoading only allowed when not in STATE_IDLE or STATE_ENDED");
      }
      PositionSupplier contentPositionMsSupplier = builder.contentPositionMsSupplier;
      if (builder.contentPositionMs != null) {
        if (builder.currentAdGroupIndex == C.INDEX_UNSET
            && builder.playWhenReady
            && builder.playbackState == Player.STATE_READY
            && builder.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE
            && builder.contentPositionMs != C.TIME_UNSET) {
          contentPositionMsSupplier =
              PositionSupplier.getExtrapolating(
                  builder.contentPositionMs, builder.playbackParameters.speed);
        } else {
          contentPositionMsSupplier = PositionSupplier.getConstant(builder.contentPositionMs);
        }
      }
      PositionSupplier adPositionMsSupplier = builder.adPositionMsSupplier;
      if (builder.adPositionMs != null) {
        if (builder.currentAdGroupIndex != C.INDEX_UNSET
            && builder.playWhenReady
            && builder.playbackState == Player.STATE_READY
            && builder.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE) {
          adPositionMsSupplier =
              PositionSupplier.getExtrapolating(builder.adPositionMs, /* playbackSpeed= */ 1f);
        } else {
          adPositionMsSupplier = PositionSupplier.getConstant(builder.adPositionMs);
        }
      }
      this.availableCommands = builder.availableCommands;
      this.playWhenReady = builder.playWhenReady;
      this.playWhenReadyChangeReason = builder.playWhenReadyChangeReason;
      this.playbackState = builder.playbackState;
      this.playbackSuppressionReason = builder.playbackSuppressionReason;
      this.playerError = builder.playerError;
      this.repeatMode = builder.repeatMode;
      this.shuffleModeEnabled = builder.shuffleModeEnabled;
      this.isLoading = builder.isLoading;
      this.seekBackIncrementMs = builder.seekBackIncrementMs;
      this.seekForwardIncrementMs = builder.seekForwardIncrementMs;
      this.maxSeekToPreviousPositionMs = builder.maxSeekToPreviousPositionMs;
      this.playbackParameters = builder.playbackParameters;
      this.trackSelectionParameters = builder.trackSelectionParameters;
      this.audioAttributes = builder.audioAttributes;
      this.volume = builder.volume;
      this.videoSize = builder.videoSize;
      this.currentCues = builder.currentCues;
      this.deviceInfo = builder.deviceInfo;
      this.deviceVolume = builder.deviceVolume;
      this.isDeviceMuted = builder.isDeviceMuted;
      this.surfaceSize = builder.surfaceSize;
      this.newlyRenderedFirstFrame = builder.newlyRenderedFirstFrame;
      this.timedMetadata = builder.timedMetadata;
      this.playlist = builder.playlist;
      this.timeline = builder.timeline;
      this.playlistMetadata = builder.playlistMetadata;
      this.currentMediaItemIndex = builder.currentMediaItemIndex;
      this.currentAdGroupIndex = builder.currentAdGroupIndex;
      this.currentAdIndexInAdGroup = builder.currentAdIndexInAdGroup;
      this.contentPositionMsSupplier = contentPositionMsSupplier;
      this.adPositionMsSupplier = adPositionMsSupplier;
      this.contentBufferedPositionMsSupplier = builder.contentBufferedPositionMsSupplier;
      this.adBufferedPositionMsSupplier = builder.adBufferedPositionMsSupplier;
      this.totalBufferedDurationMsSupplier = builder.totalBufferedDurationMsSupplier;
      this.hasPositionDiscontinuity = builder.hasPositionDiscontinuity;
      this.positionDiscontinuityReason = builder.positionDiscontinuityReason;
      this.discontinuityPositionMs = builder.discontinuityPositionMs;
    }

    /** Returns a {@link Builder} pre-populated with the current state values. */
    public Builder buildUpon() {
      return new Builder(this);
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof State)) {
        return false;
      }
      State state = (State) o;
      return playWhenReady == state.playWhenReady
          && playWhenReadyChangeReason == state.playWhenReadyChangeReason
          && availableCommands.equals(state.availableCommands)
          && playbackState == state.playbackState
          && playbackSuppressionReason == state.playbackSuppressionReason
          && Util.areEqual(playerError, state.playerError)
          && repeatMode == state.repeatMode
          && shuffleModeEnabled == state.shuffleModeEnabled
          && isLoading == state.isLoading
          && seekBackIncrementMs == state.seekBackIncrementMs
          && seekForwardIncrementMs == state.seekForwardIncrementMs
          && maxSeekToPreviousPositionMs == state.maxSeekToPreviousPositionMs
          && playbackParameters.equals(state.playbackParameters)
          && trackSelectionParameters.equals(state.trackSelectionParameters)
          && audioAttributes.equals(state.audioAttributes)
          && volume == state.volume
          && videoSize.equals(state.videoSize)
          && currentCues.equals(state.currentCues)
          && deviceInfo.equals(state.deviceInfo)
          && deviceVolume == state.deviceVolume
          && isDeviceMuted == state.isDeviceMuted
          && surfaceSize.equals(state.surfaceSize)
          && newlyRenderedFirstFrame == state.newlyRenderedFirstFrame
          && timedMetadata.equals(state.timedMetadata)
          && playlist.equals(state.playlist)
          && playlistMetadata.equals(state.playlistMetadata)
          && currentMediaItemIndex == state.currentMediaItemIndex
          && currentAdGroupIndex == state.currentAdGroupIndex
          && currentAdIndexInAdGroup == state.currentAdIndexInAdGroup
          && contentPositionMsSupplier.equals(state.contentPositionMsSupplier)
          && adPositionMsSupplier.equals(state.adPositionMsSupplier)
          && contentBufferedPositionMsSupplier.equals(state.contentBufferedPositionMsSupplier)
          && adBufferedPositionMsSupplier.equals(state.adBufferedPositionMsSupplier)
          && totalBufferedDurationMsSupplier.equals(state.totalBufferedDurationMsSupplier)
          && hasPositionDiscontinuity == state.hasPositionDiscontinuity
          && positionDiscontinuityReason == state.positionDiscontinuityReason
          && discontinuityPositionMs == state.discontinuityPositionMs;
    }

    @Override
    public int hashCode() {
      int result = 7;
      result = 31 * result + availableCommands.hashCode();
      result = 31 * result + (playWhenReady ? 1 : 0);
      result = 31 * result + playWhenReadyChangeReason;
      result = 31 * result + playbackState;
      result = 31 * result + playbackSuppressionReason;
      result = 31 * result + (playerError == null ? 0 : playerError.hashCode());
      result = 31 * result + repeatMode;
      result = 31 * result + (shuffleModeEnabled ? 1 : 0);
      result = 31 * result + (isLoading ? 1 : 0);
      result = 31 * result + (int) (seekBackIncrementMs ^ (seekBackIncrementMs >>> 32));
      result = 31 * result + (int) (seekForwardIncrementMs ^ (seekForwardIncrementMs >>> 32));
      result =
          31 * result + (int) (maxSeekToPreviousPositionMs ^ (maxSeekToPreviousPositionMs >>> 32));
      result = 31 * result + playbackParameters.hashCode();
      result = 31 * result + trackSelectionParameters.hashCode();
      result = 31 * result + audioAttributes.hashCode();
      result = 31 * result + Float.floatToRawIntBits(volume);
      result = 31 * result + videoSize.hashCode();
      result = 31 * result + currentCues.hashCode();
      result = 31 * result + deviceInfo.hashCode();
      result = 31 * result + deviceVolume;
      result = 31 * result + (isDeviceMuted ? 1 : 0);
      result = 31 * result + surfaceSize.hashCode();
      result = 31 * result + (newlyRenderedFirstFrame ? 1 : 0);
      result = 31 * result + timedMetadata.hashCode();
      result = 31 * result + playlist.hashCode();
      result = 31 * result + playlistMetadata.hashCode();
      result = 31 * result + currentMediaItemIndex;
      result = 31 * result + currentAdGroupIndex;
      result = 31 * result + currentAdIndexInAdGroup;
      result = 31 * result + contentPositionMsSupplier.hashCode();
      result = 31 * result + adPositionMsSupplier.hashCode();
      result = 31 * result + contentBufferedPositionMsSupplier.hashCode();
      result = 31 * result + adBufferedPositionMsSupplier.hashCode();
      result = 31 * result + totalBufferedDurationMsSupplier.hashCode();
      result = 31 * result + (hasPositionDiscontinuity ? 1 : 0);
      result = 31 * result + positionDiscontinuityReason;
      result = 31 * result + (int) (discontinuityPositionMs ^ (discontinuityPositionMs >>> 32));
      return result;
    }
  }

  private static final class PlaylistTimeline extends Timeline {

    private final ImmutableList<MediaItemData> playlist;
    private final int[] firstPeriodIndexByWindowIndex;
    private final int[] windowIndexByPeriodIndex;
    private final HashMap<Object, Integer> periodIndexByUid;

    public PlaylistTimeline(ImmutableList<MediaItemData> playlist) {
      int mediaItemCount = playlist.size();
      this.playlist = playlist;
      this.firstPeriodIndexByWindowIndex = new int[mediaItemCount];
      int periodCount = 0;
      for (int i = 0; i < mediaItemCount; i++) {
        MediaItemData mediaItemData = playlist.get(i);
        firstPeriodIndexByWindowIndex[i] = periodCount;
        periodCount += getPeriodCountInMediaItem(mediaItemData);
      }
      this.windowIndexByPeriodIndex = new int[periodCount];
      this.periodIndexByUid = new HashMap<>();
      int periodIndex = 0;
      for (int i = 0; i < mediaItemCount; i++) {
        MediaItemData mediaItemData = playlist.get(i);
        for (int j = 0; j < getPeriodCountInMediaItem(mediaItemData); j++) {
          periodIndexByUid.put(mediaItemData.getPeriodUid(j), periodIndex);
          windowIndexByPeriodIndex[periodIndex] = i;
          periodIndex++;
        }
      }
    }

    @Override
    public int getWindowCount() {
      return playlist.size();
    }

    @Override
    public int getNextWindowIndex(int windowIndex, int repeatMode, boolean shuffleModeEnabled) {
      // TODO: Support shuffle order.
      return super.getNextWindowIndex(windowIndex, repeatMode, shuffleModeEnabled);
    }

    @Override
    public int getPreviousWindowIndex(int windowIndex, int repeatMode, boolean shuffleModeEnabled) {
      // TODO: Support shuffle order.
      return super.getPreviousWindowIndex(windowIndex, repeatMode, shuffleModeEnabled);
    }

    @Override
    public int getLastWindowIndex(boolean shuffleModeEnabled) {
      // TODO: Support shuffle order.
      return super.getLastWindowIndex(shuffleModeEnabled);
    }

    @Override
    public int getFirstWindowIndex(boolean shuffleModeEnabled) {
      // TODO: Support shuffle order.
      return super.getFirstWindowIndex(shuffleModeEnabled);
    }

    @Override
    public Window getWindow(int windowIndex, Window window, long defaultPositionProjectionUs) {
      return playlist
          .get(windowIndex)
          .getWindow(firstPeriodIndexByWindowIndex[windowIndex], window);
    }

    @Override
    public int getPeriodCount() {
      return windowIndexByPeriodIndex.length;
    }

    @Override
    public Period getPeriodByUid(Object periodUid, Period period) {
      int periodIndex = checkNotNull(periodIndexByUid.get(periodUid));
      return getPeriod(periodIndex, period, /* setIds= */ true);
    }

    @Override
    public Period getPeriod(int periodIndex, Period period, boolean setIds) {
      int windowIndex = windowIndexByPeriodIndex[periodIndex];
      int periodIndexInWindow = periodIndex - firstPeriodIndexByWindowIndex[windowIndex];
      return playlist.get(windowIndex).getPeriod(windowIndex, periodIndexInWindow, period);
    }

    @Override
    public int getIndexOfPeriod(Object uid) {
      @Nullable Integer index = periodIndexByUid.get(uid);
      return index == null ? C.INDEX_UNSET : index;
    }

    @Override
    public Object getUidOfPeriod(int periodIndex) {
      int windowIndex = windowIndexByPeriodIndex[periodIndex];
      int periodIndexInWindow = periodIndex - firstPeriodIndexByWindowIndex[windowIndex];
      return playlist.get(windowIndex).getPeriodUid(periodIndexInWindow);
    }

    private static int getPeriodCountInMediaItem(MediaItemData mediaItemData) {
      return mediaItemData.periods.isEmpty() ? 1 : mediaItemData.periods.size();
    }
  }

  /**
   * An immutable description of an item in the playlist, containing both static setup information
   * like {@link MediaItem} and dynamic data that is generally read from the media like the
   * duration.
   */
  protected static final class MediaItemData {

    /** A builder for {@link MediaItemData} objects. */
    public static final class Builder {

      private Object uid;
      private Tracks tracks;
      private MediaItem mediaItem;
      @Nullable private MediaMetadata mediaMetadata;
      @Nullable private Object manifest;
      @Nullable private MediaItem.LiveConfiguration liveConfiguration;
      private long presentationStartTimeMs;
      private long windowStartTimeMs;
      private long elapsedRealtimeEpochOffsetMs;
      private boolean isSeekable;
      private boolean isDynamic;
      private long defaultPositionUs;
      private long durationUs;
      private long positionInFirstPeriodUs;
      private boolean isPlaceholder;
      private ImmutableList<PeriodData> periods;

      /**
       * Creates the builder.
       *
       * @param uid The unique identifier of the media item within a playlist. This value will be
       *     set as {@link Timeline.Window#uid} for this item.
       */
      public Builder(Object uid) {
        this.uid = uid;
        tracks = Tracks.EMPTY;
        mediaItem = MediaItem.EMPTY;
        mediaMetadata = null;
        manifest = null;
        liveConfiguration = null;
        presentationStartTimeMs = C.TIME_UNSET;
        windowStartTimeMs = C.TIME_UNSET;
        elapsedRealtimeEpochOffsetMs = C.TIME_UNSET;
        isSeekable = false;
        isDynamic = false;
        defaultPositionUs = 0;
        durationUs = C.TIME_UNSET;
        positionInFirstPeriodUs = 0;
        isPlaceholder = false;
        periods = ImmutableList.of();
      }

      private Builder(MediaItemData mediaItemData) {
        this.uid = mediaItemData.uid;
        this.tracks = mediaItemData.tracks;
        this.mediaItem = mediaItemData.mediaItem;
        this.mediaMetadata = mediaItemData.mediaMetadata;
        this.manifest = mediaItemData.manifest;
        this.liveConfiguration = mediaItemData.liveConfiguration;
        this.presentationStartTimeMs = mediaItemData.presentationStartTimeMs;
        this.windowStartTimeMs = mediaItemData.windowStartTimeMs;
        this.elapsedRealtimeEpochOffsetMs = mediaItemData.elapsedRealtimeEpochOffsetMs;
        this.isSeekable = mediaItemData.isSeekable;
        this.isDynamic = mediaItemData.isDynamic;
        this.defaultPositionUs = mediaItemData.defaultPositionUs;
        this.durationUs = mediaItemData.durationUs;
        this.positionInFirstPeriodUs = mediaItemData.positionInFirstPeriodUs;
        this.isPlaceholder = mediaItemData.isPlaceholder;
        this.periods = mediaItemData.periods;
      }

      /**
       * Sets the unique identifier of this media item within a playlist.
       *
       * <p>This value will be set as {@link Timeline.Window#uid} for this item.
       *
       * @param uid The unique identifier of this media item within a playlist.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setUid(Object uid) {
        this.uid = uid;
        return this;
      }

      /**
       * Sets the {@link Tracks} of this media item.
       *
       * @param tracks The {@link Tracks} of this media item.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setTracks(Tracks tracks) {
        this.tracks = tracks;
        return this;
      }

      /**
       * Sets the {@link MediaItem}.
       *
       * @param mediaItem The {@link MediaItem}.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setMediaItem(MediaItem mediaItem) {
        this.mediaItem = mediaItem;
        return this;
      }

      /**
       * Sets the {@link MediaMetadata}.
       *
       * <p>This data includes static data from the {@link MediaItem#mediaMetadata MediaItem} and
       * the media's {@link Format#metadata Format}, as well any dynamic metadata that has been
       * parsed from the media. If null, the metadata is assumed to be the simple combination of the
       * {@link MediaItem#mediaMetadata MediaItem} metadata and the metadata of the selected {@link
       * Format#metadata Formats}.
       *
       * @param mediaMetadata The {@link MediaMetadata}, or null to assume that the metadata is the
       *     simple combination of the {@link MediaItem#mediaMetadata MediaItem} metadata and the
       *     metadata of the selected {@link Format#metadata Formats}.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setMediaMetadata(@Nullable MediaMetadata mediaMetadata) {
        this.mediaMetadata = mediaMetadata;
        return this;
      }

      /**
       * Sets the manifest of the media item.
       *
       * @param manifest The manifest of the media item, or null if not applicable.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setManifest(@Nullable Object manifest) {
        this.manifest = manifest;
        return this;
      }

      /**
       * Sets the active {@link MediaItem.LiveConfiguration}, or null if the media item is not live.
       *
       * @param liveConfiguration The active {@link MediaItem.LiveConfiguration}, or null if the
       *     media item is not live.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setLiveConfiguration(@Nullable MediaItem.LiveConfiguration liveConfiguration) {
        this.liveConfiguration = liveConfiguration;
        return this;
      }

      /**
       * Sets the start time of the live presentation.
       *
       * <p>This value can only be set to anything other than {@link C#TIME_UNSET} if the stream is
       * {@linkplain #setLiveConfiguration live}.
       *
       * @param presentationStartTimeMs The start time of the live presentation, in milliseconds
       *     since the Unix epoch, or {@link C#TIME_UNSET} if unknown or not applicable.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setPresentationStartTimeMs(long presentationStartTimeMs) {
        this.presentationStartTimeMs = presentationStartTimeMs;
        return this;
      }

      /**
       * Sets the start time of the live window.
       *
       * <p>This value can only be set to anything other than {@link C#TIME_UNSET} if the stream is
       * {@linkplain #setLiveConfiguration live}. The value should also be greater or equal than the
       * {@linkplain #setPresentationStartTimeMs presentation start time}, if set.
       *
       * @param windowStartTimeMs The start time of the live window, in milliseconds since the Unix
       *     epoch, or {@link C#TIME_UNSET} if unknown or not applicable.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setWindowStartTimeMs(long windowStartTimeMs) {
        this.windowStartTimeMs = windowStartTimeMs;
        return this;
      }

      /**
       * Sets the offset between {@link SystemClock#elapsedRealtime()} and the time since the Unix
       * epoch according to the clock of the media origin server.
       *
       * <p>This value can only be set to anything other than {@link C#TIME_UNSET} if the stream is
       * {@linkplain #setLiveConfiguration live}.
       *
       * @param elapsedRealtimeEpochOffsetMs The offset between {@link
       *     SystemClock#elapsedRealtime()} and the time since the Unix epoch according to the clock
       *     of the media origin server, or {@link C#TIME_UNSET} if unknown or not applicable.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setElapsedRealtimeEpochOffsetMs(long elapsedRealtimeEpochOffsetMs) {
        this.elapsedRealtimeEpochOffsetMs = elapsedRealtimeEpochOffsetMs;
        return this;
      }

      /**
       * Sets whether it's possible to seek within this media item.
       *
       * @param isSeekable Whether it's possible to seek within this media item.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setIsSeekable(boolean isSeekable) {
        this.isSeekable = isSeekable;
        return this;
      }

      /**
       * Sets whether this media item may change over time, for example a moving live window.
       *
       * @param isDynamic Whether this media item may change over time, for example a moving live
       *     window.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setIsDynamic(boolean isDynamic) {
        this.isDynamic = isDynamic;
        return this;
      }

      /**
       * Sets the default position relative to the start of the media item at which to begin
       * playback, in microseconds.
       *
       * <p>The default position must be less or equal to the {@linkplain #setDurationUs duration},
       * is set.
       *
       * @param defaultPositionUs The default position relative to the start of the media item at
       *     which to begin playback, in microseconds.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setDefaultPositionUs(long defaultPositionUs) {
        checkArgument(defaultPositionUs >= 0);
        this.defaultPositionUs = defaultPositionUs;
        return this;
      }

      /**
       * Sets the duration of the media item, in microseconds.
       *
       * <p>If both this duration and all {@linkplain #setPeriods period} durations are set, the sum
       * of this duration and the {@linkplain #setPositionInFirstPeriodUs offset in the first
       * period} must match the total duration of all periods.
       *
       * @param durationUs The duration of the media item, in microseconds, or {@link C#TIME_UNSET}
       *     if unknown.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setDurationUs(long durationUs) {
        checkArgument(durationUs == C.TIME_UNSET || durationUs >= 0);
        this.durationUs = durationUs;
        return this;
      }

      /**
       * Sets the position of the start of this media item relative to the start of the first period
       * belonging to it, in microseconds.
       *
       * @param positionInFirstPeriodUs The position of the start of this media item relative to the
       *     start of the first period belonging to it, in microseconds.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setPositionInFirstPeriodUs(long positionInFirstPeriodUs) {
        checkArgument(positionInFirstPeriodUs >= 0);
        this.positionInFirstPeriodUs = positionInFirstPeriodUs;
        return this;
      }

      /**
       * Sets whether this media item contains placeholder information because the real information
       * has yet to be loaded.
       *
       * @param isPlaceholder Whether this media item contains placeholder information because the
       *     real information has yet to be loaded.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setIsPlaceholder(boolean isPlaceholder) {
        this.isPlaceholder = isPlaceholder;
        return this;
      }

      /**
       * Sets the list of {@linkplain PeriodData periods} in this media item.
       *
       * <p>All periods must have unique {@linkplain PeriodData.Builder#setUid UIDs} and only the
       * last period is allowed to have an unset {@linkplain PeriodData.Builder#setDurationUs
       * duration}.
       *
       * @param periods The list of {@linkplain PeriodData periods} in this media item, or an empty
       *     list to assume a single period without ads and the same duration as the media item.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setPeriods(List<PeriodData> periods) {
        int periodCount = periods.size();
        for (int i = 0; i < periodCount - 1; i++) {
          checkArgument(
              periods.get(i).durationUs != C.TIME_UNSET, "Periods other than last need a duration");
          for (int j = i + 1; j < periodCount; j++) {
            checkArgument(
                !periods.get(i).uid.equals(periods.get(j).uid),
                "Duplicate PeriodData UIDs in period list");
          }
        }
        this.periods = ImmutableList.copyOf(periods);
        return this;
      }

      /** Builds the {@link MediaItemData}. */
      public MediaItemData build() {
        return new MediaItemData(this);
      }
    }

    /** The unique identifier of this media item. */
    public final Object uid;
    /** The {@link Tracks} of this media item. */
    public final Tracks tracks;
    /** The {@link MediaItem}. */
    public final MediaItem mediaItem;
    /**
     * The {@link MediaMetadata}, including static data from the {@link MediaItem#mediaMetadata
     * MediaItem} and the media's {@link Format#metadata Format}, as well any dynamic metadata that
     * has been parsed from the media. If null, the metadata is assumed to be the simple combination
     * of the {@link MediaItem#mediaMetadata MediaItem} metadata and the metadata of the selected
     * {@link Format#metadata Formats}.
     */
    @Nullable public final MediaMetadata mediaMetadata;
    /** The manifest of the media item, or null if not applicable. */
    @Nullable public final Object manifest;
    /** The active {@link MediaItem.LiveConfiguration}, or null if the media item is not live. */
    @Nullable public final MediaItem.LiveConfiguration liveConfiguration;
    /**
     * The start time of the live presentation, in milliseconds since the Unix epoch, or {@link
     * C#TIME_UNSET} if unknown or not applicable.
     */
    public final long presentationStartTimeMs;
    /**
     * The start time of the live window, in milliseconds since the Unix epoch, or {@link
     * C#TIME_UNSET} if unknown or not applicable.
     */
    public final long windowStartTimeMs;
    /**
     * The offset between {@link SystemClock#elapsedRealtime()} and the time since the Unix epoch
     * according to the clock of the media origin server, or {@link C#TIME_UNSET} if unknown or not
     * applicable.
     */
    public final long elapsedRealtimeEpochOffsetMs;
    /** Whether it's possible to seek within this media item. */
    public final boolean isSeekable;
    /** Whether this media item may change over time, for example a moving live window. */
    public final boolean isDynamic;
    /**
     * The default position relative to the start of the media item at which to begin playback, in
     * microseconds.
     */
    public final long defaultPositionUs;
    /** The duration of the media item, in microseconds, or {@link C#TIME_UNSET} if unknown. */
    public final long durationUs;
    /**
     * The position of the start of this media item relative to the start of the first period
     * belonging to it, in microseconds.
     */
    public final long positionInFirstPeriodUs;
    /**
     * Whether this media item contains placeholder information because the real information has yet
     * to be loaded.
     */
    public final boolean isPlaceholder;
    /**
     * The list of {@linkplain PeriodData periods} in this media item, or an empty list to assume a
     * single period without ads and the same duration as the media item.
     */
    public final ImmutableList<PeriodData> periods;

    private final long[] periodPositionInWindowUs;
    private final MediaMetadata combinedMediaMetadata;

    private MediaItemData(Builder builder) {
      if (builder.liveConfiguration == null) {
        checkArgument(
            builder.presentationStartTimeMs == C.TIME_UNSET,
            "presentationStartTimeMs can only be set if liveConfiguration != null");
        checkArgument(
            builder.windowStartTimeMs == C.TIME_UNSET,
            "windowStartTimeMs can only be set if liveConfiguration != null");
        checkArgument(
            builder.elapsedRealtimeEpochOffsetMs == C.TIME_UNSET,
            "elapsedRealtimeEpochOffsetMs can only be set if liveConfiguration != null");
      } else if (builder.presentationStartTimeMs != C.TIME_UNSET
          && builder.windowStartTimeMs != C.TIME_UNSET) {
        checkArgument(
            builder.windowStartTimeMs >= builder.presentationStartTimeMs,
            "windowStartTimeMs can't be less than presentationStartTimeMs");
      }
      int periodCount = builder.periods.size();
      if (builder.durationUs != C.TIME_UNSET) {
        checkArgument(
            builder.defaultPositionUs <= builder.durationUs,
            "defaultPositionUs can't be greater than durationUs");
      }
      this.uid = builder.uid;
      this.tracks = builder.tracks;
      this.mediaItem = builder.mediaItem;
      this.mediaMetadata = builder.mediaMetadata;
      this.manifest = builder.manifest;
      this.liveConfiguration = builder.liveConfiguration;
      this.presentationStartTimeMs = builder.presentationStartTimeMs;
      this.windowStartTimeMs = builder.windowStartTimeMs;
      this.elapsedRealtimeEpochOffsetMs = builder.elapsedRealtimeEpochOffsetMs;
      this.isSeekable = builder.isSeekable;
      this.isDynamic = builder.isDynamic;
      this.defaultPositionUs = builder.defaultPositionUs;
      this.durationUs = builder.durationUs;
      this.positionInFirstPeriodUs = builder.positionInFirstPeriodUs;
      this.isPlaceholder = builder.isPlaceholder;
      this.periods = builder.periods;
      periodPositionInWindowUs = new long[periods.size()];
      if (!periods.isEmpty()) {
        periodPositionInWindowUs[0] = -positionInFirstPeriodUs;
        for (int i = 0; i < periodCount - 1; i++) {
          periodPositionInWindowUs[i + 1] = periodPositionInWindowUs[i] + periods.get(i).durationUs;
        }
      }
      combinedMediaMetadata =
          mediaMetadata != null ? mediaMetadata : getCombinedMediaMetadata(mediaItem, tracks);
    }

    /** Returns a {@link Builder} pre-populated with the current values. */
    public Builder buildUpon() {
      return new Builder(this);
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof MediaItemData)) {
        return false;
      }
      MediaItemData mediaItemData = (MediaItemData) o;
      return this.uid.equals(mediaItemData.uid)
          && this.tracks.equals(mediaItemData.tracks)
          && this.mediaItem.equals(mediaItemData.mediaItem)
          && Util.areEqual(this.mediaMetadata, mediaItemData.mediaMetadata)
          && Util.areEqual(this.manifest, mediaItemData.manifest)
          && Util.areEqual(this.liveConfiguration, mediaItemData.liveConfiguration)
          && this.presentationStartTimeMs == mediaItemData.presentationStartTimeMs
          && this.windowStartTimeMs == mediaItemData.windowStartTimeMs
          && this.elapsedRealtimeEpochOffsetMs == mediaItemData.elapsedRealtimeEpochOffsetMs
          && this.isSeekable == mediaItemData.isSeekable
          && this.isDynamic == mediaItemData.isDynamic
          && this.defaultPositionUs == mediaItemData.defaultPositionUs
          && this.durationUs == mediaItemData.durationUs
          && this.positionInFirstPeriodUs == mediaItemData.positionInFirstPeriodUs
          && this.isPlaceholder == mediaItemData.isPlaceholder
          && this.periods.equals(mediaItemData.periods);
    }

    @Override
    public int hashCode() {
      int result = 7;
      result = 31 * result + uid.hashCode();
      result = 31 * result + tracks.hashCode();
      result = 31 * result + mediaItem.hashCode();
      result = 31 * result + (mediaMetadata == null ? 0 : mediaMetadata.hashCode());
      result = 31 * result + (manifest == null ? 0 : manifest.hashCode());
      result = 31 * result + (liveConfiguration == null ? 0 : liveConfiguration.hashCode());
      result = 31 * result + (int) (presentationStartTimeMs ^ (presentationStartTimeMs >>> 32));
      result = 31 * result + (int) (windowStartTimeMs ^ (windowStartTimeMs >>> 32));
      result =
          31 * result
              + (int) (elapsedRealtimeEpochOffsetMs ^ (elapsedRealtimeEpochOffsetMs >>> 32));
      result = 31 * result + (isSeekable ? 1 : 0);
      result = 31 * result + (isDynamic ? 1 : 0);
      result = 31 * result + (int) (defaultPositionUs ^ (defaultPositionUs >>> 32));
      result = 31 * result + (int) (durationUs ^ (durationUs >>> 32));
      result = 31 * result + (int) (positionInFirstPeriodUs ^ (positionInFirstPeriodUs >>> 32));
      result = 31 * result + (isPlaceholder ? 1 : 0);
      result = 31 * result + periods.hashCode();
      return result;
    }

    private Timeline.Window getWindow(int firstPeriodIndex, Timeline.Window window) {
      int periodCount = periods.isEmpty() ? 1 : periods.size();
      window.set(
          uid,
          mediaItem,
          manifest,
          presentationStartTimeMs,
          windowStartTimeMs,
          elapsedRealtimeEpochOffsetMs,
          isSeekable,
          isDynamic,
          liveConfiguration,
          defaultPositionUs,
          durationUs,
          firstPeriodIndex,
          /* lastPeriodIndex= */ firstPeriodIndex + periodCount - 1,
          positionInFirstPeriodUs);
      window.isPlaceholder = isPlaceholder;
      return window;
    }

    private Timeline.Period getPeriod(
        int windowIndex, int periodIndexInMediaItem, Timeline.Period period) {
      if (periods.isEmpty()) {
        period.set(
            /* id= */ uid,
            uid,
            windowIndex,
            /* durationUs= */ positionInFirstPeriodUs + durationUs,
            /* positionInWindowUs= */ 0,
            AdPlaybackState.NONE,
            isPlaceholder);
      } else {
        PeriodData periodData = periods.get(periodIndexInMediaItem);
        Object periodId = periodData.uid;
        Object periodUid = Pair.create(uid, periodId);
        period.set(
            periodId,
            periodUid,
            windowIndex,
            periodData.durationUs,
            periodPositionInWindowUs[periodIndexInMediaItem],
            periodData.adPlaybackState,
            periodData.isPlaceholder);
      }
      return period;
    }

    private Object getPeriodUid(int periodIndexInMediaItem) {
      if (periods.isEmpty()) {
        return uid;
      }
      Object periodId = periods.get(periodIndexInMediaItem).uid;
      return Pair.create(uid, periodId);
    }

    private static MediaMetadata getCombinedMediaMetadata(MediaItem mediaItem, Tracks tracks) {
      MediaMetadata.Builder metadataBuilder = new MediaMetadata.Builder();
      int trackGroupCount = tracks.getGroups().size();
      for (int i = 0; i < trackGroupCount; i++) {
        Tracks.Group group = tracks.getGroups().get(i);
        for (int j = 0; j < group.length; j++) {
          if (group.isTrackSelected(j)) {
            Format format = group.getTrackFormat(j);
            if (format.metadata != null) {
              for (int k = 0; k < format.metadata.length(); k++) {
                format.metadata.get(k).populateMediaMetadata(metadataBuilder);
              }
            }
          }
        }
      }
      return metadataBuilder.populate(mediaItem.mediaMetadata).build();
    }
  }

  /** Data describing the properties of a period inside a {@link MediaItemData}. */
  protected static final class PeriodData {

    /** A builder for {@link PeriodData} objects. */
    public static final class Builder {

      private Object uid;
      private long durationUs;
      private AdPlaybackState adPlaybackState;
      private boolean isPlaceholder;

      /**
       * Creates the builder.
       *
       * @param uid The unique identifier of the period within its media item.
       */
      public Builder(Object uid) {
        this.uid = uid;
        this.durationUs = 0;
        this.adPlaybackState = AdPlaybackState.NONE;
        this.isPlaceholder = false;
      }

      private Builder(PeriodData periodData) {
        this.uid = periodData.uid;
        this.durationUs = periodData.durationUs;
        this.adPlaybackState = periodData.adPlaybackState;
        this.isPlaceholder = periodData.isPlaceholder;
      }

      /**
       * Sets the unique identifier of the period within its media item.
       *
       * @param uid The unique identifier of the period within its media item.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setUid(Object uid) {
        this.uid = uid;
        return this;
      }

      /**
       * Sets the total duration of the period, in microseconds, or {@link C#TIME_UNSET} if unknown.
       *
       * <p>Only the last period in a media item can have an unknown duration.
       *
       * @param durationUs The total duration of the period, in microseconds, or {@link
       *     C#TIME_UNSET} if unknown.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setDurationUs(long durationUs) {
        checkArgument(durationUs == C.TIME_UNSET || durationUs >= 0);
        this.durationUs = durationUs;
        return this;
      }

      /**
       * Sets the {@link AdPlaybackState}.
       *
       * @param adPlaybackState The {@link AdPlaybackState}, or {@link AdPlaybackState#NONE} if
       *     there are no ads.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setAdPlaybackState(AdPlaybackState adPlaybackState) {
        this.adPlaybackState = adPlaybackState;
        return this;
      }

      /**
       * Sets whether this period contains placeholder information because the real information has
       * yet to be loaded
       *
       * @param isPlaceholder Whether this period contains placeholder information because the real
       *     information has yet to be loaded.
       * @return This builder.
       */
      @CanIgnoreReturnValue
      public Builder setIsPlaceholder(boolean isPlaceholder) {
        this.isPlaceholder = isPlaceholder;
        return this;
      }

      /** Builds the {@link PeriodData}. */
      public PeriodData build() {
        return new PeriodData(this);
      }
    }

    /** The unique identifier of the period within its media item. */
    public final Object uid;
    /**
     * The total duration of the period, in microseconds, or {@link C#TIME_UNSET} if unknown. Only
     * the last period in a media item can have an unknown duration.
     */
    public final long durationUs;
    /**
     * The {@link AdPlaybackState} of the period, or {@link AdPlaybackState#NONE} if there are no
     * ads.
     */
    public final AdPlaybackState adPlaybackState;
    /**
     * Whether this period contains placeholder information because the real information has yet to
     * be loaded.
     */
    public final boolean isPlaceholder;

    private PeriodData(Builder builder) {
      this.uid = builder.uid;
      this.durationUs = builder.durationUs;
      this.adPlaybackState = builder.adPlaybackState;
      this.isPlaceholder = builder.isPlaceholder;
    }

    /** Returns a {@link Builder} pre-populated with the current values. */
    public Builder buildUpon() {
      return new Builder(this);
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof PeriodData)) {
        return false;
      }
      PeriodData periodData = (PeriodData) o;
      return this.uid.equals(periodData.uid)
          && this.durationUs == periodData.durationUs
          && this.adPlaybackState.equals(periodData.adPlaybackState)
          && this.isPlaceholder == periodData.isPlaceholder;
    }

    @Override
    public int hashCode() {
      int result = 7;
      result = 31 * result + uid.hashCode();
      result = 31 * result + (int) (durationUs ^ (durationUs >>> 32));
      result = 31 * result + adPlaybackState.hashCode();
      result = 31 * result + (isPlaceholder ? 1 : 0);
      return result;
    }
  }

  /** A supplier for a position. */
  protected interface PositionSupplier {

    /** An instance returning a constant position of zero. */
    PositionSupplier ZERO = getConstant(/* positionMs= */ 0);

    /**
     * Returns an instance that returns a constant value.
     *
     * @param positionMs The constant position to return, in milliseconds.
     */
    static PositionSupplier getConstant(long positionMs) {
      return () -> positionMs;
    }

    /**
     * Returns an instance that extrapolates the provided position into the future.
     *
     * @param currentPositionMs The current position in milliseconds.
     * @param playbackSpeed The playback speed with which the position is assumed to increase.
     */
    static PositionSupplier getExtrapolating(long currentPositionMs, float playbackSpeed) {
      long startTimeMs = SystemClock.elapsedRealtime();
      return () -> {
        long currentTimeMs = SystemClock.elapsedRealtime();
        return currentPositionMs + (long) ((currentTimeMs - startTimeMs) * playbackSpeed);
      };
    }

    /** Returns the position. */
    long get();
  }

  /**
   * Position difference threshold below which we do not automatically report a position
   * discontinuity, in milliseconds.
   */
  private static final long POSITION_DISCONTINUITY_THRESHOLD_MS = 1000;

  private final ListenerSet<Listener> listeners;
  private final Looper applicationLooper;
  private final HandlerWrapper applicationHandler;
  private final HashSet<ListenableFuture<?>> pendingOperations;
  private final Timeline.Period period;

  private @MonotonicNonNull State state;
  private boolean released;

  /**
   * Creates the base class.
   *
   * @param applicationLooper The {@link Looper} that must be used for all calls to the player and
   *     that is used to call listeners on.
   */
  protected SimpleBasePlayer(Looper applicationLooper) {
    this(applicationLooper, Clock.DEFAULT);
  }

  /**
   * Creates the base class.
   *
   * @param applicationLooper The {@link Looper} that must be used for all calls to the player and
   *     that is used to call listeners on.
   * @param clock The {@link Clock} that will be used by the player.
   */
  protected SimpleBasePlayer(Looper applicationLooper, Clock clock) {
    this.applicationLooper = applicationLooper;
    applicationHandler = clock.createHandler(applicationLooper, /* callback= */ null);
    pendingOperations = new HashSet<>();
    period = new Timeline.Period();
    @SuppressWarnings("nullness:argument.type.incompatible") // Using this in constructor.
    ListenerSet<Player.Listener> listenerSet =
        new ListenerSet<>(
            applicationLooper,
            clock,
            (listener, flags) -> listener.onEvents(/* player= */ this, new Events(flags)));
    listeners = listenerSet;
  }

  @Override
  public final void addListener(Listener listener) {
    // Don't verify application thread. We allow calls to this method from any thread.
    listeners.add(checkNotNull(listener));
  }

  @Override
  public final void removeListener(Listener listener) {
    verifyApplicationThreadAndInitState();
    listeners.remove(listener);
  }

  @Override
  public final Looper getApplicationLooper() {
    // Don't verify application thread. We allow calls to this method from any thread.
    return applicationLooper;
  }

  @Override
  public final Commands getAvailableCommands() {
    verifyApplicationThreadAndInitState();
    return state.availableCommands;
  }

  @Override
  public final void setPlayWhenReady(boolean playWhenReady) {
    verifyApplicationThreadAndInitState();
    // Use a local copy to ensure the lambda below uses the current state value.
    State state = this.state;
    if (!shouldHandleCommand(Player.COMMAND_PLAY_PAUSE)) {
      return;
    }
    updateStateForPendingOperation(
        /* pendingOperation= */ handleSetPlayWhenReady(playWhenReady),
        /* placeholderStateSupplier= */ () ->
            state
                .buildUpon()
                .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                .build());
  }

  @Override
  public final boolean getPlayWhenReady() {
    verifyApplicationThreadAndInitState();
    return state.playWhenReady;
  }

  @Override
  public final void setMediaItems(List<MediaItem> mediaItems, boolean resetPosition) {
    verifyApplicationThreadAndInitState();
    int startIndex = resetPosition ? C.INDEX_UNSET : state.currentMediaItemIndex;
    long startPositionMs = resetPosition ? C.TIME_UNSET : state.contentPositionMsSupplier.get();
    setMediaItemsInternal(mediaItems, startIndex, startPositionMs);
  }

  @Override
  public final void setMediaItems(
      List<MediaItem> mediaItems, int startIndex, long startPositionMs) {
    verifyApplicationThreadAndInitState();
    if (startIndex == C.INDEX_UNSET) {
      startIndex = state.currentMediaItemIndex;
      startPositionMs = state.contentPositionMsSupplier.get();
    }
    setMediaItemsInternal(mediaItems, startIndex, startPositionMs);
  }

  @RequiresNonNull("state")
  private void setMediaItemsInternal(
      List<MediaItem> mediaItems, int startIndex, long startPositionMs) {
    checkArgument(startIndex == C.INDEX_UNSET || startIndex >= 0);
    // Use a local copy to ensure the lambda below uses the current state value.
    State state = this.state;
    if (!shouldHandleCommand(Player.COMMAND_CHANGE_MEDIA_ITEMS)
        && (mediaItems.size() != 1 || !shouldHandleCommand(Player.COMMAND_SET_MEDIA_ITEM))) {
      return;
    }
    updateStateForPendingOperation(
        /* pendingOperation= */ handleSetMediaItems(mediaItems, startIndex, startPositionMs),
        /* placeholderStateSupplier= */ () -> {
          ArrayList<MediaItemData> placeholderPlaylist = new ArrayList<>();
          for (int i = 0; i < mediaItems.size(); i++) {
            placeholderPlaylist.add(getPlaceholderMediaItemData(mediaItems.get(i)));
          }
          return getStateWithNewPlaylistAndPosition(
              state, placeholderPlaylist, startIndex, startPositionMs);
        });
  }

  @Override
  public final void addMediaItems(int index, List<MediaItem> mediaItems) {
    verifyApplicationThreadAndInitState();
    checkArgument(index >= 0);
    // Use a local copy to ensure the lambda below uses the current state value.
    State state = this.state;
    int playlistSize = state.playlist.size();
    if (!shouldHandleCommand(Player.COMMAND_CHANGE_MEDIA_ITEMS) || mediaItems.isEmpty()) {
      return;
    }
    int correctedIndex = min(index, playlistSize);
    updateStateForPendingOperation(
        /* pendingOperation= */ handleAddMediaItems(correctedIndex, mediaItems),
        /* placeholderStateSupplier= */ () -> {
          ArrayList<MediaItemData> placeholderPlaylist = new ArrayList<>(state.playlist);
          for (int i = 0; i < mediaItems.size(); i++) {
            placeholderPlaylist.add(
                i + correctedIndex, getPlaceholderMediaItemData(mediaItems.get(i)));
          }
          return getStateWithNewPlaylist(state, placeholderPlaylist, period);
        });
  }

  @Override
  public final void moveMediaItems(int fromIndex, int toIndex, int newIndex) {
    verifyApplicationThreadAndInitState();
    checkArgument(fromIndex >= 0 && toIndex >= fromIndex && newIndex >= 0);
    // Use a local copy to ensure the lambda below uses the current state value.
    State state = this.state;
    int playlistSize = state.playlist.size();
    if (!shouldHandleCommand(Player.COMMAND_CHANGE_MEDIA_ITEMS)
        || playlistSize == 0
        || fromIndex >= playlistSize) {
      return;
    }
    int correctedToIndex = min(toIndex, playlistSize);
    int correctedNewIndex = min(newIndex, state.playlist.size() - (correctedToIndex - fromIndex));
    if (fromIndex == correctedToIndex || correctedNewIndex == fromIndex) {
      return;
    }
    updateStateForPendingOperation(
        /* pendingOperation= */ handleMoveMediaItems(
            fromIndex, correctedToIndex, correctedNewIndex),
        /* placeholderStateSupplier= */ () -> {
          ArrayList<MediaItemData> placeholderPlaylist = new ArrayList<>(state.playlist);
          Util.moveItems(placeholderPlaylist, fromIndex, correctedToIndex, correctedNewIndex);
          return getStateWithNewPlaylist(state, placeholderPlaylist, period);
        });
  }

  @Override
  public final void removeMediaItems(int fromIndex, int toIndex) {
    verifyApplicationThreadAndInitState();
    checkArgument(fromIndex >= 0 && toIndex >= fromIndex);
    // Use a local copy to ensure the lambda below uses the current state value.
    State state = this.state;
    int playlistSize = state.playlist.size();
    if (!shouldHandleCommand(Player.COMMAND_CHANGE_MEDIA_ITEMS)
        || playlistSize == 0
        || fromIndex >= playlistSize) {
      return;
    }
    int correctedToIndex = min(toIndex, playlistSize);
    if (fromIndex == correctedToIndex) {
      return;
    }
    updateStateForPendingOperation(
        /* pendingOperation= */ handleRemoveMediaItems(fromIndex, correctedToIndex),
        /* placeholderStateSupplier= */ () -> {
          ArrayList<MediaItemData> placeholderPlaylist = new ArrayList<>(state.playlist);
          Util.removeRange(placeholderPlaylist, fromIndex, correctedToIndex);
          return getStateWithNewPlaylist(state, placeholderPlaylist, period);
        });
  }

  @Override
  public final void prepare() {
    verifyApplicationThreadAndInitState();
    // Use a local copy to ensure the lambda below uses the current state value.
    State state = this.state;
    if (!shouldHandleCommand(Player.COMMAND_PREPARE)) {
      return;
    }
    updateStateForPendingOperation(
        /* pendingOperation= */ handlePrepare(),
        /* placeholderStateSupplier= */ () ->
            state
                .buildUpon()
                .setPlayerError(null)
                .setPlaybackState(state.timeline.isEmpty() ? STATE_ENDED : STATE_BUFFERING)
                .build());
  }

  @Override
  @Player.State
  public final int getPlaybackState() {
    verifyApplicationThreadAndInitState();
    return state.playbackState;
  }

  @Override
  public final int getPlaybackSuppressionReason() {
    verifyApplicationThreadAndInitState();
    return state.playbackSuppressionReason;
  }

  @Nullable
  @Override
  public final PlaybackException getPlayerError() {
    verifyApplicationThreadAndInitState();
    return state.playerError;
  }

  @Override
  public final void setRepeatMode(@Player.RepeatMode int repeatMode) {
    verifyApplicationThreadAndInitState();
    // Use a local copy to ensure the lambda below uses the current state value.
    State state = this.state;
    if (!shouldHandleCommand(Player.COMMAND_SET_REPEAT_MODE)) {
      return;
    }
    updateStateForPendingOperation(
        /* pendingOperation= */ handleSetRepeatMode(repeatMode),
        /* placeholderStateSupplier= */ () -> state.buildUpon().setRepeatMode(repeatMode).build());
  }

  @Override
  @Player.RepeatMode
  public final int getRepeatMode() {
    verifyApplicationThreadAndInitState();
    return state.repeatMode;
  }

  @Override
  public final void setShuffleModeEnabled(boolean shuffleModeEnabled) {
    verifyApplicationThreadAndInitState();
    // Use a local copy to ensure the lambda below uses the current state value.
    State state = this.state;
    if (!shouldHandleCommand(Player.COMMAND_SET_SHUFFLE_MODE)) {
      return;
    }
    updateStateForPendingOperation(
        /* pendingOperation= */ handleSetShuffleModeEnabled(shuffleModeEnabled),
        /* placeholderStateSupplier= */ () ->
            state.buildUpon().setShuffleModeEnabled(shuffleModeEnabled).build());
  }

  @Override
  public final boolean getShuffleModeEnabled() {
    verifyApplicationThreadAndInitState();
    return state.shuffleModeEnabled;
  }

  @Override
  public final boolean isLoading() {
    verifyApplicationThreadAndInitState();
    return state.isLoading;
  }

  @Override
  @VisibleForTesting(otherwise = PROTECTED)
  public final void seekTo(
      int mediaItemIndex,
      long positionMs,
      @Player.Command int seekCommand,
      boolean isRepeatingCurrentItem) {
    verifyApplicationThreadAndInitState();
    checkArgument(mediaItemIndex >= 0);
    // Use a local copy to ensure the lambda below uses the current state value.
    State state = this.state;
    if (!shouldHandleCommand(seekCommand)
        || isPlayingAd()
        || (!state.playlist.isEmpty() && mediaItemIndex >= state.playlist.size())) {
      return;
    }
    updateStateForPendingOperation(
        /* pendingOperation= */ handleSeek(mediaItemIndex, positionMs, seekCommand),
        /* placeholderStateSupplier= */ () ->
            getStateWithNewPlaylistAndPosition(state, state.playlist, mediaItemIndex, positionMs),
        /* seeked= */ true,
        isRepeatingCurrentItem);
  }

  @Override
  public final long getSeekBackIncrement() {
    verifyApplicationThreadAndInitState();
    return state.seekBackIncrementMs;
  }

  @Override
  public final long getSeekForwardIncrement() {
    verifyApplicationThreadAndInitState();
    return state.seekForwardIncrementMs;
  }

  @Override
  public final long getMaxSeekToPreviousPosition() {
    verifyApplicationThreadAndInitState();
    return state.maxSeekToPreviousPositionMs;
  }

  @Override
  public final void setPlaybackParameters(PlaybackParameters playbackParameters) {
    verifyApplicationThreadAndInitState();
    // Use a local copy to ensure the lambda below uses the current state value.
    State state = this.state;
    if (!shouldHandleCommand(Player.COMMAND_SET_SPEED_AND_PITCH)) {
      return;
    }
    updateStateForPendingOperation(
        /* pendingOperation= */ handleSetPlaybackParameters(playbackParameters),
        /* placeholderStateSupplier= */ () ->
            state.buildUpon().setPlaybackParameters(playbackParameters).build());
  }

  @Override
  public final PlaybackParameters getPlaybackParameters() {
    verifyApplicationThreadAndInitState();
    return state.playbackParameters;
  }

  @Override
  public final void stop() {
    verifyApplicationThreadAndInitState();
    // Use a local copy to ensure the lambda below uses the current state value.
    State state = this.state;
    if (!shouldHandleCommand(Player.COMMAND_STOP)) {
      return;
    }
    updateStateForPendingOperation(
        /* pendingOperation= */ handleStop(),
        /* placeholderStateSupplier= */ () ->
            state
                .buildUpon()
                .setPlaybackState(Player.STATE_IDLE)
                .setTotalBufferedDurationMs(PositionSupplier.ZERO)
                .setContentBufferedPositionMs(
                    PositionSupplier.getConstant(getContentPositionMsInternal(state)))
                .setAdBufferedPositionMs(state.adPositionMsSupplier)
                .setIsLoading(false)
                .build());
  }

  @Override
  public final void stop(boolean reset) {
    stop();
    if (reset) {
      clearMediaItems();
    }
  }

  @Override
  public final void release() {
    verifyApplicationThreadAndInitState();
    // Use a local copy to ensure the lambda below uses the current state value.
    State state = this.state;
    if (released) { // TODO(b/261158047): Replace by !shouldHandleCommand(Player.COMMAND_RELEASE)
      return;
    }
    updateStateForPendingOperation(
        /* pendingOperation= */ handleRelease(), /* placeholderStateSupplier= */ () -> state);
    released = true;
    listeners.release();
    // Enforce some final state values in case getters are called after release.
    this.state =
        this.state
            .buildUpon()
            .setPlaybackState(Player.STATE_IDLE)
            .setTotalBufferedDurationMs(PositionSupplier.ZERO)
            .setContentBufferedPositionMs(
                PositionSupplier.getConstant(getContentPositionMsInternal(state)))
            .setAdBufferedPositionMs(state.adPositionMsSupplier)
            .setIsLoading(false)
            .build();
  }

  @Override
  public final Tracks getCurrentTracks() {
    verifyApplicationThreadAndInitState();
    return getCurrentTracksInternal(state);
  }

  @Override
  public final TrackSelectionParameters getTrackSelectionParameters() {
    verifyApplicationThreadAndInitState();
    return state.trackSelectionParameters;
  }

  @Override
  public final void setTrackSelectionParameters(TrackSelectionParameters parameters) {
    verifyApplicationThreadAndInitState();
    // Use a local copy to ensure the lambda below uses the current state value.
    State state = this.state;
    if (!shouldHandleCommand(Player.COMMAND_SET_TRACK_SELECTION_PARAMETERS)) {
      return;
    }
    updateStateForPendingOperation(
        /* pendingOperation= */ handleSetTrackSelectionParameters(parameters),
        /* placeholderStateSupplier= */ () ->
            state.buildUpon().setTrackSelectionParameters(parameters).build());
  }

  @Override
  public final MediaMetadata getMediaMetadata() {
    verifyApplicationThreadAndInitState();
    return getMediaMetadataInternal(state);
  }

  @Override
  public final MediaMetadata getPlaylistMetadata() {
    verifyApplicationThreadAndInitState();
    return state.playlistMetadata;
  }

  @Override
  public final void setPlaylistMetadata(MediaMetadata mediaMetadata) {
    verifyApplicationThreadAndInitState();
    // Use a local copy to ensure the lambda below uses the current state value.
    State state = this.state;
    if (!shouldHandleCommand(Player.COMMAND_SET_MEDIA_ITEMS_METADATA)) {
      return;
    }
    updateStateForPendingOperation(
        /* pendingOperation= */ handleSetPlaylistMetadata(mediaMetadata),
        /* placeholderStateSupplier= */ () ->
            state.buildUpon().setPlaylistMetadata(mediaMetadata).build());
  }

  @Override
  public final Timeline getCurrentTimeline() {
    verifyApplicationThreadAndInitState();
    return state.timeline;
  }

  @Override
  public final int getCurrentPeriodIndex() {
    verifyApplicationThreadAndInitState();
    return getCurrentPeriodIndexInternal(state, window, period);
  }

  @Override
  public final int getCurrentMediaItemIndex() {
    verifyApplicationThreadAndInitState();
    return getCurrentMediaItemIndexInternal(state);
  }

  @Override
  public final long getDuration() {
    verifyApplicationThreadAndInitState();
    if (isPlayingAd()) {
      state.timeline.getPeriod(getCurrentPeriodIndex(), period);
      long adDurationUs =
          period.getAdDurationUs(state.currentAdGroupIndex, state.currentAdIndexInAdGroup);
      return Util.usToMs(adDurationUs);
    }
    return getContentDuration();
  }

  @Override
  public final long getCurrentPosition() {
    verifyApplicationThreadAndInitState();
    return isPlayingAd() ? state.adPositionMsSupplier.get() : getContentPosition();
  }

  @Override
  public final long getBufferedPosition() {
    verifyApplicationThreadAndInitState();
    return isPlayingAd()
        ? max(state.adBufferedPositionMsSupplier.get(), state.adPositionMsSupplier.get())
        : getContentBufferedPosition();
  }

  @Override
  public final long getTotalBufferedDuration() {
    verifyApplicationThreadAndInitState();
    return state.totalBufferedDurationMsSupplier.get();
  }

  @Override
  public final boolean isPlayingAd() {
    verifyApplicationThreadAndInitState();
    return state.currentAdGroupIndex != C.INDEX_UNSET;
  }

  @Override
  public final int getCurrentAdGroupIndex() {
    verifyApplicationThreadAndInitState();
    return state.currentAdGroupIndex;
  }

  @Override
  public final int getCurrentAdIndexInAdGroup() {
    verifyApplicationThreadAndInitState();
    return state.currentAdIndexInAdGroup;
  }

  @Override
  public final long getContentPosition() {
    verifyApplicationThreadAndInitState();
    return getContentPositionMsInternal(state);
  }

  @Override
  public final long getContentBufferedPosition() {
    verifyApplicationThreadAndInitState();
    return max(getContentBufferedPositionMsInternal(state), getContentPositionMsInternal(state));
  }

  @Override
  public final AudioAttributes getAudioAttributes() {
    verifyApplicationThreadAndInitState();
    return state.audioAttributes;
  }

  @Override
  public final void setVolume(float volume) {
    verifyApplicationThreadAndInitState();
    // Use a local copy to ensure the lambda below uses the current state value.
    State state = this.state;
    if (!shouldHandleCommand(Player.COMMAND_SET_VOLUME)) {
      return;
    }
    updateStateForPendingOperation(
        /* pendingOperation= */ handleSetVolume(volume),
        /* placeholderStateSupplier= */ () -> state.buildUpon().setVolume(volume).build());
  }

  @Override
  public final float getVolume() {
    verifyApplicationThreadAndInitState();
    return state.volume;
  }

  @Override
  public final void setVideoSurface(@Nullable Surface surface) {
    verifyApplicationThreadAndInitState();
    // Use a local copy to ensure the lambda below uses the current state value.
    State state = this.state;
    if (!shouldHandleCommand(Player.COMMAND_SET_VIDEO_SURFACE)) {
      return;
    }
    if (surface == null) {
      clearVideoSurface();
      return;
    }
    updateStateForPendingOperation(
        /* pendingOperation= */ handleSetVideoOutput(surface),
        /* placeholderStateSupplier= */ () ->
            state.buildUpon().setSurfaceSize(Size.UNKNOWN).build());
  }

  @Override
  public final void setVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder) {
    verifyApplicationThreadAndInitState();
    // Use a local copy to ensure the lambda below uses the current state value.
    State state = this.state;
    if (!shouldHandleCommand(Player.COMMAND_SET_VIDEO_SURFACE)) {
      return;
    }
    if (surfaceHolder == null) {
      clearVideoSurface();
      return;
    }
    updateStateForPendingOperation(
        /* pendingOperation= */ handleSetVideoOutput(surfaceHolder),
        /* placeholderStateSupplier= */ () ->
            state.buildUpon().setSurfaceSize(getSurfaceHolderSize(surfaceHolder)).build());
  }

  @Override
  public final void setVideoSurfaceView(@Nullable SurfaceView surfaceView) {
    verifyApplicationThreadAndInitState();
    // Use a local copy to ensure the lambda below uses the current state value.
    State state = this.state;
    if (!shouldHandleCommand(Player.COMMAND_SET_VIDEO_SURFACE)) {
      return;
    }
    if (surfaceView == null) {
      clearVideoSurface();
      return;
    }
    updateStateForPendingOperation(
        /* pendingOperation= */ handleSetVideoOutput(surfaceView),
        /* placeholderStateSupplier= */ () ->
            state
                .buildUpon()
                .setSurfaceSize(getSurfaceHolderSize(surfaceView.getHolder()))
                .build());
  }

  @Override
  public final void setVideoTextureView(@Nullable TextureView textureView) {
    verifyApplicationThreadAndInitState();
    // Use a local copy to ensure the lambda below uses the current state value.
    State state = this.state;
    if (!shouldHandleCommand(Player.COMMAND_SET_VIDEO_SURFACE)) {
      return;
    }
    if (textureView == null) {
      clearVideoSurface();
      return;
    }
    Size surfaceSize;
    if (textureView.isAvailable()) {
      surfaceSize = new Size(textureView.getWidth(), textureView.getHeight());
    } else {
      surfaceSize = Size.ZERO;
    }
    updateStateForPendingOperation(
        /* pendingOperation= */ handleSetVideoOutput(textureView),
        /* placeholderStateSupplier= */ () ->
            state.buildUpon().setSurfaceSize(surfaceSize).build());
  }

  @Override
  public final void clearVideoSurface() {
    clearVideoOutput(/* videoOutput= */ null);
  }

  @Override
  public final void clearVideoSurface(@Nullable Surface surface) {
    clearVideoOutput(surface);
  }

  @Override
  public final void clearVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder) {
    clearVideoOutput(surfaceHolder);
  }

  @Override
  public final void clearVideoSurfaceView(@Nullable SurfaceView surfaceView) {
    clearVideoOutput(surfaceView);
  }

  @Override
  public final void clearVideoTextureView(@Nullable TextureView textureView) {
    clearVideoOutput(textureView);
  }

  private void clearVideoOutput(@Nullable Object videoOutput) {
    verifyApplicationThreadAndInitState();
    // Use a local copy to ensure the lambda below uses the current state value.
    State state = this.state;
    if (!shouldHandleCommand(Player.COMMAND_SET_VIDEO_SURFACE)) {
      return;
    }
    updateStateForPendingOperation(
        /* pendingOperation= */ handleClearVideoOutput(videoOutput),
        /* placeholderStateSupplier= */ () -> state.buildUpon().setSurfaceSize(Size.ZERO).build());
  }

  @Override
  public final VideoSize getVideoSize() {
    verifyApplicationThreadAndInitState();
    return state.videoSize;
  }

  @Override
  public final Size getSurfaceSize() {
    verifyApplicationThreadAndInitState();
    return state.surfaceSize;
  }

  @Override
  public final CueGroup getCurrentCues() {
    verifyApplicationThreadAndInitState();
    return state.currentCues;
  }

  @Override
  public final DeviceInfo getDeviceInfo() {
    verifyApplicationThreadAndInitState();
    return state.deviceInfo;
  }

  @Override
  public final int getDeviceVolume() {
    verifyApplicationThreadAndInitState();
    return state.deviceVolume;
  }

  @Override
  public final boolean isDeviceMuted() {
    verifyApplicationThreadAndInitState();
    return state.isDeviceMuted;
  }

  @Override
  public final void setDeviceVolume(int volume) {
    verifyApplicationThreadAndInitState();
    // Use a local copy to ensure the lambda below uses the current state value.
    State state = this.state;
    if (!shouldHandleCommand(Player.COMMAND_SET_DEVICE_VOLUME)) {
      return;
    }
    updateStateForPendingOperation(
        /* pendingOperation= */ handleSetDeviceVolume(volume),
        /* placeholderStateSupplier= */ () -> state.buildUpon().setDeviceVolume(volume).build());
  }

  @Override
  public final void increaseDeviceVolume() {
    verifyApplicationThreadAndInitState();
    // Use a local copy to ensure the lambda below uses the current state value.
    State state = this.state;
    if (!shouldHandleCommand(Player.COMMAND_ADJUST_DEVICE_VOLUME)) {
      return;
    }
    updateStateForPendingOperation(
        /* pendingOperation= */ handleIncreaseDeviceVolume(),
        /* placeholderStateSupplier= */ () ->
            state.buildUpon().setDeviceVolume(state.deviceVolume + 1).build());
  }

  @Override
  public final void decreaseDeviceVolume() {
    verifyApplicationThreadAndInitState();
    // Use a local copy to ensure the lambda below uses the current state value.
    State state = this.state;
    if (!shouldHandleCommand(Player.COMMAND_ADJUST_DEVICE_VOLUME)) {
      return;
    }
    updateStateForPendingOperation(
        /* pendingOperation= */ handleDecreaseDeviceVolume(),
        /* placeholderStateSupplier= */ () ->
            state.buildUpon().setDeviceVolume(max(0, state.deviceVolume - 1)).build());
  }

  @Override
  public final void setDeviceMuted(boolean muted) {
    verifyApplicationThreadAndInitState();
    // Use a local copy to ensure the lambda below uses the current state value.
    State state = this.state;
    if (!shouldHandleCommand(Player.COMMAND_ADJUST_DEVICE_VOLUME)) {
      return;
    }
    updateStateForPendingOperation(
        /* pendingOperation= */ handleSetDeviceMuted(muted),
        /* placeholderStateSupplier= */ () -> state.buildUpon().setIsDeviceMuted(muted).build());
  }

  /**
   * Invalidates the current state.
   *
   * <p>Triggers a call to {@link #getState()} and informs listeners if the state changed.
   *
   * <p>Note that this may not have an immediate effect while there are still player methods being
   * handled asynchronously. The state will be invalidated automatically once these pending
   * synchronous operations are finished and there is no need to call this method again.
   */
  protected final void invalidateState() {
    verifyApplicationThreadAndInitState();
    if (!pendingOperations.isEmpty() || released) {
      return;
    }
    updateStateAndInformListeners(
        getState(), /* seeked= */ false, /* isRepeatingCurrentItem= */ false);
  }

  /**
   * Returns the current {@link State} of the player.
   *
   * <p>The {@link State} should include all {@linkplain
   * State.Builder#setAvailableCommands(Commands) available commands} indicating which player
   * methods are allowed to be called.
   *
   * <p>Note that this method won't be called while asynchronous handling of player methods is in
   * progress. This means that the implementation doesn't need to handle state changes caused by
   * these asynchronous operations until they are done and can return the currently known state
   * directly. The placeholder state used while these asynchronous operations are in progress can be
   * customized by overriding {@link #getPlaceholderState(State)} if required.
   */
  @ForOverride
  protected abstract State getState();

  /**
   * Returns the placeholder state used while a player method is handled asynchronously.
   *
   * <p>The {@code suggestedPlaceholderState} already contains the most likely state update, for
   * example setting {@link State#playWhenReady} to true if {@code player.setPlayWhenReady(true)} is
   * called, and an implementations only needs to override this method if it can determine a more
   * accurate placeholder state.
   *
   * @param suggestedPlaceholderState The suggested placeholder {@link State}, including the most
   *     likely outcome of handling all pending asynchronous operations.
   * @return The placeholder {@link State} to use while asynchronous operations are pending.
   */
  @ForOverride
  protected State getPlaceholderState(State suggestedPlaceholderState) {
    return suggestedPlaceholderState;
  }

  /**
   * Returns the placeholder {@link MediaItemData} used for a new {@link MediaItem} added to the
   * playlist.
   *
   * <p>An implementation only needs to override this method if it can determine a more accurate
   * placeholder state than the default.
   *
   * @param mediaItem The {@link MediaItem} added to the playlist.
   * @return The {@link MediaItemData} used as placeholder while adding the item to the playlist is
   *     in progress.
   */
  @ForOverride
  protected MediaItemData getPlaceholderMediaItemData(MediaItem mediaItem) {
    return new MediaItemData.Builder(new PlaceholderUid())
        .setMediaItem(mediaItem)
        .setIsDynamic(true)
        .setIsPlaceholder(true)
        .build();
  }

  /**
   * Handles calls to {@link Player#setPlayWhenReady}, {@link Player#play} and {@link Player#pause}.
   *
   * <p>Will only be called if {@link Player#COMMAND_PLAY_PAUSE} is available.
   *
   * @param playWhenReady The requested {@link State#playWhenReady}
   * @return A {@link ListenableFuture} indicating the completion of all immediate {@link State}
   *     changes caused by this call.
   */
  @ForOverride
  protected ListenableFuture<?> handleSetPlayWhenReady(boolean playWhenReady) {
    throw new IllegalStateException("Missing implementation to handle COMMAND_PLAY_PAUSE");
  }

  /**
   * Handles calls to {@link Player#prepare}.
   *
   * <p>Will only be called if {@link Player#COMMAND_PREPARE} is available.
   *
   * @return A {@link ListenableFuture} indicating the completion of all immediate {@link State}
   *     changes caused by this call.
   */
  @ForOverride
  protected ListenableFuture<?> handlePrepare() {
    throw new IllegalStateException("Missing implementation to handle COMMAND_PREPARE");
  }

  /**
   * Handles calls to {@link Player#stop}.
   *
   * <p>Will only be called if {@link Player#COMMAND_STOP} is available.
   *
   * @return A {@link ListenableFuture} indicating the completion of all immediate {@link State}
   *     changes caused by this call.
   */
  @ForOverride
  protected ListenableFuture<?> handleStop() {
    throw new IllegalStateException("Missing implementation to handle COMMAND_STOP");
  }

  /**
   * Handles calls to {@link Player#release}.
   *
   * @return A {@link ListenableFuture} indicating the completion of all immediate {@link State}
   *     changes caused by this call.
   */
  // TODO(b/261158047): Add that this method will only be called if COMMAND_RELEASE is available.
  @ForOverride
  protected ListenableFuture<?> handleRelease() {
    throw new IllegalStateException("Missing implementation to handle COMMAND_RELEASE");
  }

  /**
   * Handles calls to {@link Player#setRepeatMode}.
   *
   * <p>Will only be called if {@link Player#COMMAND_SET_REPEAT_MODE} is available.
   *
   * @param repeatMode The requested {@link RepeatMode}.
   * @return A {@link ListenableFuture} indicating the completion of all immediate {@link State}
   *     changes caused by this call.
   */
  @ForOverride
  protected ListenableFuture<?> handleSetRepeatMode(@RepeatMode int repeatMode) {
    throw new IllegalStateException("Missing implementation to handle COMMAND_SET_REPEAT_MODE");
  }

  /**
   * Handles calls to {@link Player#setShuffleModeEnabled}.
   *
   * <p>Will only be called if {@link Player#COMMAND_SET_SHUFFLE_MODE} is available.
   *
   * @param shuffleModeEnabled Whether shuffle mode was requested to be enabled.
   * @return A {@link ListenableFuture} indicating the completion of all immediate {@link State}
   *     changes caused by this call.
   */
  @ForOverride
  protected ListenableFuture<?> handleSetShuffleModeEnabled(boolean shuffleModeEnabled) {
    throw new IllegalStateException("Missing implementation to handle COMMAND_SET_SHUFFLE_MODE");
  }

  /**
   * Handles calls to {@link Player#setPlaybackParameters} or {@link Player#setPlaybackSpeed}.
   *
   * <p>Will only be called if {@link Player#COMMAND_SET_SPEED_AND_PITCH} is available.
   *
   * @param playbackParameters The requested {@link PlaybackParameters}.
   * @return A {@link ListenableFuture} indicating the completion of all immediate {@link State}
   *     changes caused by this call.
   */
  @ForOverride
  protected ListenableFuture<?> handleSetPlaybackParameters(PlaybackParameters playbackParameters) {
    throw new IllegalStateException("Missing implementation to handle COMMAND_SET_SPEED_AND_PITCH");
  }

  /**
   * Handles calls to {@link Player#setTrackSelectionParameters}.
   *
   * <p>Will only be called if {@link Player#COMMAND_SET_TRACK_SELECTION_PARAMETERS} is available.
   *
   * @param trackSelectionParameters The requested {@link TrackSelectionParameters}.
   * @return A {@link ListenableFuture} indicating the completion of all immediate {@link State}
   *     changes caused by this call.
   */
  @ForOverride
  protected ListenableFuture<?> handleSetTrackSelectionParameters(
      TrackSelectionParameters trackSelectionParameters) {
    throw new IllegalStateException(
        "Missing implementation to handle COMMAND_SET_TRACK_SELECTION_PARAMETERS");
  }

  /**
   * Handles calls to {@link Player#setPlaylistMetadata}.
   *
   * <p>Will only be called if {@link Player#COMMAND_SET_MEDIA_ITEMS_METADATA} is available.
   *
   * @param playlistMetadata The requested {@linkplain MediaMetadata playlist metadata}.
   * @return A {@link ListenableFuture} indicating the completion of all immediate {@link State}
   *     changes caused by this call.
   */
  @ForOverride
  protected ListenableFuture<?> handleSetPlaylistMetadata(MediaMetadata playlistMetadata) {
    throw new IllegalStateException(
        "Missing implementation to handle COMMAND_SET_MEDIA_ITEMS_METADATA");
  }

  /**
   * Handles calls to {@link Player#setVolume}.
   *
   * <p>Will only be called if {@link Player#COMMAND_SET_VOLUME} is available.
   *
   * @param volume The requested audio volume, with 0 being silence and 1 being unity gain (signal
   *     unchanged).
   * @return A {@link ListenableFuture} indicating the completion of all immediate {@link State}
   *     changes caused by this call.
   */
  @ForOverride
  protected ListenableFuture<?> handleSetVolume(@FloatRange(from = 0, to = 1.0) float volume) {
    throw new IllegalStateException("Missing implementation to handle COMMAND_SET_VOLUME");
  }

  /**
   * Handles calls to {@link Player#setDeviceVolume}.
   *
   * <p>Will only be called if {@link Player#COMMAND_SET_DEVICE_VOLUME} is available.
   *
   * @param deviceVolume The requested device volume.
   * @return A {@link ListenableFuture} indicating the completion of all immediate {@link State}
   *     changes caused by this call.
   */
  @ForOverride
  protected ListenableFuture<?> handleSetDeviceVolume(@IntRange(from = 0) int deviceVolume) {
    throw new IllegalStateException("Missing implementation to handle COMMAND_SET_DEVICE_VOLUME");
  }

  /**
   * Handles calls to {@link Player#increaseDeviceVolume()}.
   *
   * <p>Will only be called if {@link Player#COMMAND_ADJUST_DEVICE_VOLUME} is available.
   *
   * @return A {@link ListenableFuture} indicating the completion of all immediate {@link State}
   *     changes caused by this call.
   */
  @ForOverride
  protected ListenableFuture<?> handleIncreaseDeviceVolume() {
    throw new IllegalStateException(
        "Missing implementation to handle COMMAND_ADJUST_DEVICE_VOLUME");
  }

  /**
   * Handles calls to {@link Player#decreaseDeviceVolume()}.
   *
   * <p>Will only be called if {@link Player#COMMAND_ADJUST_DEVICE_VOLUME} is available.
   *
   * @return A {@link ListenableFuture} indicating the completion of all immediate {@link State}
   *     changes caused by this call.
   */
  @ForOverride
  protected ListenableFuture<?> handleDecreaseDeviceVolume() {
    throw new IllegalStateException(
        "Missing implementation to handle COMMAND_ADJUST_DEVICE_VOLUME");
  }

  /**
   * Handles calls to {@link Player#setDeviceMuted}.
   *
   * <p>Will only be called if {@link Player#COMMAND_ADJUST_DEVICE_VOLUME} is available.
   *
   * @param muted Whether the device was requested to be muted.
   * @return A {@link ListenableFuture} indicating the completion of all immediate {@link State}
   *     changes caused by this call.
   */
  @ForOverride
  protected ListenableFuture<?> handleSetDeviceMuted(boolean muted) {
    throw new IllegalStateException(
        "Missing implementation to handle COMMAND_ADJUST_DEVICE_VOLUME");
  }

  /**
   * Handles calls to set the video output.
   *
   * <p>Will only be called if {@link Player#COMMAND_SET_VIDEO_SURFACE} is available.
   *
   * @param videoOutput The requested video output. This is either a {@link Surface}, {@link
   *     SurfaceHolder}, {@link TextureView} or {@link SurfaceView}.
   * @return A {@link ListenableFuture} indicating the completion of all immediate {@link State}
   *     changes caused by this call.
   */
  @ForOverride
  protected ListenableFuture<?> handleSetVideoOutput(Object videoOutput) {
    throw new IllegalStateException("Missing implementation to handle COMMAND_SET_VIDEO_SURFACE");
  }

  /**
   * Handles calls to clear the video output.
   *
   * <p>Will only be called if {@link Player#COMMAND_SET_VIDEO_SURFACE} is available.
   *
   * @param videoOutput The video output to clear. If null any current output should be cleared. If
   *     non-null, the output should only be cleared if it matches the provided argument. This is
   *     either a {@link Surface}, {@link SurfaceHolder}, {@link TextureView} or {@link
   *     SurfaceView}.
   * @return A {@link ListenableFuture} indicating the completion of all immediate {@link State}
   *     changes caused by this call.
   */
  @ForOverride
  protected ListenableFuture<?> handleClearVideoOutput(@Nullable Object videoOutput) {
    throw new IllegalStateException("Missing implementation to handle COMMAND_SET_VIDEO_SURFACE");
  }

  /**
   * Handles calls to {@link Player#setMediaItem} and {@link Player#setMediaItems}.
   *
   * <p>Will only be called if {@link Player#COMMAND_SET_MEDIA_ITEM} or {@link
   * Player#COMMAND_CHANGE_MEDIA_ITEMS} is available. If only {@link Player#COMMAND_SET_MEDIA_ITEM}
   * is available, the list of media items will always contain exactly one item.
   *
   * @param mediaItems The media items to add.
   * @param startIndex The index at which to start playback from, or {@link C#INDEX_UNSET} to start
   *     at the default item.
   * @param startPositionMs The position in milliseconds to start playback from, or {@link
   *     C#TIME_UNSET} to start at the default position in the media item.
   * @return A {@link ListenableFuture} indicating the completion of all immediate {@link State}
   *     changes caused by this call.
   */
  @ForOverride
  protected ListenableFuture<?> handleSetMediaItems(
      List<MediaItem> mediaItems, int startIndex, long startPositionMs) {
    throw new IllegalStateException("Missing implementation to handle COMMAND_SET_MEDIA_ITEM(S)");
  }

  /**
   * Handles calls to {@link Player#addMediaItem} and {@link Player#addMediaItems}.
   *
   * <p>Will only be called if {@link Player#COMMAND_CHANGE_MEDIA_ITEMS} is available.
   *
   * @param index The index at which to add the items. The index is in the range 0 &lt;= {@code
   *     index} &lt;= {@link #getMediaItemCount()}.
   * @param mediaItems The media items to add.
   * @return A {@link ListenableFuture} indicating the completion of all immediate {@link State}
   *     changes caused by this call.
   */
  @ForOverride
  protected ListenableFuture<?> handleAddMediaItems(int index, List<MediaItem> mediaItems) {
    throw new IllegalStateException("Missing implementation to handle COMMAND_CHANGE_MEDIA_ITEMS");
  }

  /**
   * Handles calls to {@link Player#moveMediaItem} and {@link Player#moveMediaItems}.
   *
   * <p>Will only be called if {@link Player#COMMAND_CHANGE_MEDIA_ITEMS} is available.
   *
   * @param fromIndex The start index of the items to move. The index is in the range 0 &lt;= {@code
   *     fromIndex} &lt; {@link #getMediaItemCount()}.
   * @param toIndex The index of the first item not to be included in the move (exclusive). The
   *     index is in the range {@code fromIndex} &lt; {@code toIndex} &lt;= {@link
   *     #getMediaItemCount()}.
   * @param newIndex The new index of the first moved item. The index is in the range {@code 0}
   *     &lt;= {@code newIndex} &lt; {@link #getMediaItemCount() - (toIndex - fromIndex)}.
   * @return A {@link ListenableFuture} indicating the completion of all immediate {@link State}
   *     changes caused by this call.
   */
  @ForOverride
  protected ListenableFuture<?> handleMoveMediaItems(int fromIndex, int toIndex, int newIndex) {
    throw new IllegalStateException("Missing implementation to handle COMMAND_CHANGE_MEDIA_ITEMS");
  }

  /**
   * Handles calls to {@link Player#removeMediaItem} and {@link Player#removeMediaItems}.
   *
   * <p>Will only be called if {@link Player#COMMAND_CHANGE_MEDIA_ITEMS} is available.
   *
   * @param fromIndex The index at which to start removing media items. The index is in the range 0
   *     &lt;= {@code fromIndex} &lt; {@link #getMediaItemCount()}.
   * @param toIndex The index of the first item to be kept (exclusive). The index is in the range
   *     {@code fromIndex} &lt; {@code toIndex} &lt;= {@link #getMediaItemCount()}.
   * @return A {@link ListenableFuture} indicating the completion of all immediate {@link State}
   *     changes caused by this call.
   */
  @ForOverride
  protected ListenableFuture<?> handleRemoveMediaItems(int fromIndex, int toIndex) {
    throw new IllegalStateException("Missing implementation to handle COMMAND_CHANGE_MEDIA_ITEMS");
  }

  /**
   * Handles calls to {@link Player#seekTo} and other seek operations (for example, {@link
   * Player#seekToNext}).
   *
   * <p>Will only be called if the appropriate {@link Player.Command}, for example {@link
   * Player#COMMAND_SEEK_TO_MEDIA_ITEM} or {@link Player#COMMAND_SEEK_TO_NEXT}, is available.
   *
   * @param mediaItemIndex The media item index to seek to. The index is in the range 0 &lt;= {@code
   *     mediaItemIndex} &lt; {@code mediaItems.size()}.
   * @param positionMs The position in milliseconds to start playback from, or {@link C#TIME_UNSET}
   *     to start at the default position in the media item.
   * @param seekCommand The {@link Player.Command} used to trigger the seek.
   * @return A {@link ListenableFuture} indicating the completion of all immediate {@link State}
   *     changes caused by this call.
   */
  @ForOverride
  protected ListenableFuture<?> handleSeek(
      int mediaItemIndex, long positionMs, @Player.Command int seekCommand) {
    throw new IllegalStateException("Missing implementation to handle one of the COMMAND_SEEK_*");
  }

  @RequiresNonNull("state")
  private boolean shouldHandleCommand(@Player.Command int commandCode) {
    return !released && state.availableCommands.contains(commandCode);
  }

  @SuppressWarnings("deprecation") // Calling deprecated listener methods.
  @RequiresNonNull("state")
  private void updateStateAndInformListeners(
      State newState, boolean seeked, boolean isRepeatingCurrentItem) {
    State previousState = state;
    // Assign new state immediately such that all getters return the right values, but use a
    // snapshot of the previous and new state so that listener invocations are triggered correctly.
    this.state = newState;
    if (newState.hasPositionDiscontinuity || newState.newlyRenderedFirstFrame) {
      // Clear one-time events to avoid signalling them again later.
      this.state =
          this.state
              .buildUpon()
              .clearPositionDiscontinuity()
              .setNewlyRenderedFirstFrame(false)
              .build();
    }

    boolean playWhenReadyChanged = previousState.playWhenReady != newState.playWhenReady;
    boolean playbackStateChanged = previousState.playbackState != newState.playbackState;
    Tracks previousTracks = getCurrentTracksInternal(previousState);
    Tracks newTracks = getCurrentTracksInternal(newState);
    MediaMetadata previousMediaMetadata = getMediaMetadataInternal(previousState);
    MediaMetadata newMediaMetadata = getMediaMetadataInternal(newState);
    int positionDiscontinuityReason =
        getPositionDiscontinuityReason(previousState, newState, seeked, window, period);
    boolean timelineChanged = !previousState.timeline.equals(newState.timeline);
    int mediaItemTransitionReason =
        getMediaItemTransitionReason(
            previousState, newState, positionDiscontinuityReason, isRepeatingCurrentItem, window);

    if (timelineChanged) {
      @Player.TimelineChangeReason
      int timelineChangeReason = getTimelineChangeReason(previousState.playlist, newState.playlist);
      listeners.queueEvent(
          Player.EVENT_TIMELINE_CHANGED,
          listener -> listener.onTimelineChanged(newState.timeline, timelineChangeReason));
    }
    if (positionDiscontinuityReason != C.INDEX_UNSET) {
      PositionInfo previousPositionInfo =
          getPositionInfo(previousState, /* useDiscontinuityPosition= */ false, window, period);
      PositionInfo positionInfo =
          getPositionInfo(
              newState,
              /* useDiscontinuityPosition= */ newState.hasPositionDiscontinuity,
              window,
              period);
      listeners.queueEvent(
          Player.EVENT_POSITION_DISCONTINUITY,
          listener -> {
            listener.onPositionDiscontinuity(positionDiscontinuityReason);
            listener.onPositionDiscontinuity(
                previousPositionInfo, positionInfo, positionDiscontinuityReason);
          });
    }
    if (mediaItemTransitionReason != C.INDEX_UNSET) {
      @Nullable
      MediaItem mediaItem =
          newState.timeline.isEmpty()
              ? null
              : newState.playlist.get(getCurrentMediaItemIndexInternal(newState)).mediaItem;
      listeners.queueEvent(
          Player.EVENT_MEDIA_ITEM_TRANSITION,
          listener -> listener.onMediaItemTransition(mediaItem, mediaItemTransitionReason));
    }
    if (!Util.areEqual(previousState.playerError, newState.playerError)) {
      listeners.queueEvent(
          Player.EVENT_PLAYER_ERROR,
          listener -> listener.onPlayerErrorChanged(newState.playerError));
      if (newState.playerError != null) {
        listeners.queueEvent(
            Player.EVENT_PLAYER_ERROR,
            listener -> listener.onPlayerError(castNonNull(newState.playerError)));
      }
    }
    if (!previousState.trackSelectionParameters.equals(newState.trackSelectionParameters)) {
      listeners.queueEvent(
          Player.EVENT_TRACK_SELECTION_PARAMETERS_CHANGED,
          listener ->
              listener.onTrackSelectionParametersChanged(newState.trackSelectionParameters));
    }
    if (!previousTracks.equals(newTracks)) {
      listeners.queueEvent(
          Player.EVENT_TRACKS_CHANGED, listener -> listener.onTracksChanged(newTracks));
    }
    if (!previousMediaMetadata.equals(newMediaMetadata)) {
      listeners.queueEvent(
          EVENT_MEDIA_METADATA_CHANGED,
          listener -> listener.onMediaMetadataChanged(newMediaMetadata));
    }
    if (previousState.isLoading != newState.isLoading) {
      listeners.queueEvent(
          Player.EVENT_IS_LOADING_CHANGED,
          listener -> {
            listener.onLoadingChanged(newState.isLoading);
            listener.onIsLoadingChanged(newState.isLoading);
          });
    }
    if (playWhenReadyChanged || playbackStateChanged) {
      listeners.queueEvent(
          /* eventFlag= */ C.INDEX_UNSET,
          listener ->
              listener.onPlayerStateChanged(newState.playWhenReady, newState.playbackState));
    }
    if (playbackStateChanged) {
      listeners.queueEvent(
          Player.EVENT_PLAYBACK_STATE_CHANGED,
          listener -> listener.onPlaybackStateChanged(newState.playbackState));
    }
    if (playWhenReadyChanged
        || previousState.playWhenReadyChangeReason != newState.playWhenReadyChangeReason) {
      listeners.queueEvent(
          Player.EVENT_PLAY_WHEN_READY_CHANGED,
          listener ->
              listener.onPlayWhenReadyChanged(
                  newState.playWhenReady, newState.playWhenReadyChangeReason));
    }
    if (previousState.playbackSuppressionReason != newState.playbackSuppressionReason) {
      listeners.queueEvent(
          Player.EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED,
          listener ->
              listener.onPlaybackSuppressionReasonChanged(newState.playbackSuppressionReason));
    }
    if (isPlaying(previousState) != isPlaying(newState)) {
      listeners.queueEvent(
          Player.EVENT_IS_PLAYING_CHANGED,
          listener -> listener.onIsPlayingChanged(isPlaying(newState)));
    }
    if (!previousState.playbackParameters.equals(newState.playbackParameters)) {
      listeners.queueEvent(
          Player.EVENT_PLAYBACK_PARAMETERS_CHANGED,
          listener -> listener.onPlaybackParametersChanged(newState.playbackParameters));
    }
    if (previousState.repeatMode != newState.repeatMode) {
      listeners.queueEvent(
          Player.EVENT_REPEAT_MODE_CHANGED,
          listener -> listener.onRepeatModeChanged(newState.repeatMode));
    }
    if (previousState.shuffleModeEnabled != newState.shuffleModeEnabled) {
      listeners.queueEvent(
          Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
          listener -> listener.onShuffleModeEnabledChanged(newState.shuffleModeEnabled));
    }
    if (previousState.seekBackIncrementMs != newState.seekBackIncrementMs) {
      listeners.queueEvent(
          Player.EVENT_SEEK_BACK_INCREMENT_CHANGED,
          listener -> listener.onSeekBackIncrementChanged(newState.seekBackIncrementMs));
    }
    if (previousState.seekForwardIncrementMs != newState.seekForwardIncrementMs) {
      listeners.queueEvent(
          Player.EVENT_SEEK_FORWARD_INCREMENT_CHANGED,
          listener -> listener.onSeekForwardIncrementChanged(newState.seekForwardIncrementMs));
    }
    if (previousState.maxSeekToPreviousPositionMs != newState.maxSeekToPreviousPositionMs) {
      listeners.queueEvent(
          Player.EVENT_MAX_SEEK_TO_PREVIOUS_POSITION_CHANGED,
          listener ->
              listener.onMaxSeekToPreviousPositionChanged(newState.maxSeekToPreviousPositionMs));
    }
    if (!previousState.audioAttributes.equals(newState.audioAttributes)) {
      listeners.queueEvent(
          Player.EVENT_AUDIO_ATTRIBUTES_CHANGED,
          listener -> listener.onAudioAttributesChanged(newState.audioAttributes));
    }
    if (!previousState.videoSize.equals(newState.videoSize)) {
      listeners.queueEvent(
          Player.EVENT_VIDEO_SIZE_CHANGED,
          listener -> listener.onVideoSizeChanged(newState.videoSize));
    }
    if (!previousState.deviceInfo.equals(newState.deviceInfo)) {
      listeners.queueEvent(
          Player.EVENT_DEVICE_INFO_CHANGED,
          listener -> listener.onDeviceInfoChanged(newState.deviceInfo));
    }
    if (!previousState.playlistMetadata.equals(newState.playlistMetadata)) {
      listeners.queueEvent(
          Player.EVENT_PLAYLIST_METADATA_CHANGED,
          listener -> listener.onPlaylistMetadataChanged(newState.playlistMetadata));
    }
    if (newState.newlyRenderedFirstFrame) {
      listeners.queueEvent(Player.EVENT_RENDERED_FIRST_FRAME, Listener::onRenderedFirstFrame);
    }
    if (!previousState.surfaceSize.equals(newState.surfaceSize)) {
      listeners.queueEvent(
          Player.EVENT_SURFACE_SIZE_CHANGED,
          listener ->
              listener.onSurfaceSizeChanged(
                  newState.surfaceSize.getWidth(), newState.surfaceSize.getHeight()));
    }
    if (previousState.volume != newState.volume) {
      listeners.queueEvent(
          Player.EVENT_VOLUME_CHANGED, listener -> listener.onVolumeChanged(newState.volume));
    }
    if (previousState.deviceVolume != newState.deviceVolume
        || previousState.isDeviceMuted != newState.isDeviceMuted) {
      listeners.queueEvent(
          Player.EVENT_DEVICE_VOLUME_CHANGED,
          listener ->
              listener.onDeviceVolumeChanged(newState.deviceVolume, newState.isDeviceMuted));
    }
    if (!previousState.currentCues.equals(newState.currentCues)) {
      listeners.queueEvent(
          Player.EVENT_CUES,
          listener -> {
            listener.onCues(newState.currentCues.cues);
            listener.onCues(newState.currentCues);
          });
    }
    if (!previousState.timedMetadata.equals(newState.timedMetadata)
        && newState.timedMetadata.presentationTimeUs != C.TIME_UNSET) {
      listeners.queueEvent(
          Player.EVENT_METADATA, listener -> listener.onMetadata(newState.timedMetadata));
    }
    if (positionDiscontinuityReason == Player.DISCONTINUITY_REASON_SEEK) {
      listeners.queueEvent(/* eventFlag= */ C.INDEX_UNSET, Listener::onSeekProcessed);
    }
    if (!previousState.availableCommands.equals(newState.availableCommands)) {
      listeners.queueEvent(
          Player.EVENT_AVAILABLE_COMMANDS_CHANGED,
          listener -> listener.onAvailableCommandsChanged(newState.availableCommands));
    }
    listeners.flushEvents();
  }

  @EnsuresNonNull("state")
  private void verifyApplicationThreadAndInitState() {
    if (Thread.currentThread() != applicationLooper.getThread()) {
      String message =
          Util.formatInvariant(
              "Player is accessed on the wrong thread.\n"
                  + "Current thread: '%s'\n"
                  + "Expected thread: '%s'\n"
                  + "See https://exoplayer.dev/issues/player-accessed-on-wrong-thread",
              Thread.currentThread().getName(), applicationLooper.getThread().getName());
      throw new IllegalStateException(message);
    }
    if (state == null) {
      // First time accessing state.
      state = getState();
    }
  }

  @RequiresNonNull("state")
  private void updateStateForPendingOperation(
      ListenableFuture<?> pendingOperation, Supplier<State> placeholderStateSupplier) {
    updateStateForPendingOperation(
        pendingOperation,
        placeholderStateSupplier,
        /* seeked= */ false,
        /* isRepeatingCurrentItem= */ false);
  }

  @RequiresNonNull("state")
  private void updateStateForPendingOperation(
      ListenableFuture<?> pendingOperation,
      Supplier<State> placeholderStateSupplier,
      boolean seeked,
      boolean isRepeatingCurrentItem) {
    if (pendingOperation.isDone() && pendingOperations.isEmpty()) {
      updateStateAndInformListeners(getState(), seeked, isRepeatingCurrentItem);
    } else {
      pendingOperations.add(pendingOperation);
      State suggestedPlaceholderState = placeholderStateSupplier.get();
      updateStateAndInformListeners(
          getPlaceholderState(suggestedPlaceholderState), seeked, isRepeatingCurrentItem);
      pendingOperation.addListener(
          () -> {
            castNonNull(state); // Already checked by method @RequiresNonNull pre-condition.
            pendingOperations.remove(pendingOperation);
            if (pendingOperations.isEmpty() && !released) {
              updateStateAndInformListeners(
                  getState(), /* seeked= */ false, /* isRepeatingCurrentItem= */ false);
            }
          },
          this::postOrRunOnApplicationHandler);
    }
  }

  private void postOrRunOnApplicationHandler(Runnable runnable) {
    if (applicationHandler.getLooper() == Looper.myLooper()) {
      runnable.run();
    } else {
      applicationHandler.post(runnable);
    }
  }

  private static boolean isPlaying(State state) {
    return state.playWhenReady
        && state.playbackState == Player.STATE_READY
        && state.playbackSuppressionReason == PLAYBACK_SUPPRESSION_REASON_NONE;
  }

  private static Tracks getCurrentTracksInternal(State state) {
    return state.playlist.isEmpty()
        ? Tracks.EMPTY
        : state.playlist.get(getCurrentMediaItemIndexInternal(state)).tracks;
  }

  private static MediaMetadata getMediaMetadataInternal(State state) {
    return state.playlist.isEmpty()
        ? MediaMetadata.EMPTY
        : state.playlist.get(getCurrentMediaItemIndexInternal(state)).combinedMediaMetadata;
  }

  private static int getCurrentMediaItemIndexInternal(State state) {
    if (state.currentMediaItemIndex != C.INDEX_UNSET) {
      return state.currentMediaItemIndex;
    }
    return 0; // TODO: Use shuffle order to get first item if playlist is not empty.
  }

  private static long getContentPositionMsInternal(State state) {
    return getPositionOrDefaultInMediaItem(state.contentPositionMsSupplier.get(), state);
  }

  private static long getContentBufferedPositionMsInternal(State state) {
    return getPositionOrDefaultInMediaItem(state.contentBufferedPositionMsSupplier.get(), state);
  }

  private static long getPositionOrDefaultInMediaItem(long positionMs, State state) {
    if (positionMs != C.TIME_UNSET) {
      return positionMs;
    }
    if (state.playlist.isEmpty()) {
      return 0;
    }
    return usToMs(state.playlist.get(getCurrentMediaItemIndexInternal(state)).defaultPositionUs);
  }

  private static int getCurrentPeriodIndexInternal(
      State state, Timeline.Window window, Timeline.Period period) {
    int currentMediaItemIndex = getCurrentMediaItemIndexInternal(state);
    if (state.timeline.isEmpty()) {
      return currentMediaItemIndex;
    }
    return getPeriodIndexFromWindowPosition(
        state.timeline, currentMediaItemIndex, getContentPositionMsInternal(state), window, period);
  }

  private static int getPeriodIndexFromWindowPosition(
      Timeline timeline,
      int windowIndex,
      long windowPositionMs,
      Timeline.Window window,
      Timeline.Period period) {
    Object periodUid =
        timeline.getPeriodPositionUs(window, period, windowIndex, msToUs(windowPositionMs)).first;
    return timeline.getIndexOfPeriod(periodUid);
  }

  private static @Player.TimelineChangeReason int getTimelineChangeReason(
      List<MediaItemData> previousPlaylist, List<MediaItemData> newPlaylist) {
    if (previousPlaylist.size() != newPlaylist.size()) {
      return Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED;
    }
    for (int i = 0; i < previousPlaylist.size(); i++) {
      Object previousUid = previousPlaylist.get(i).uid;
      Object newUid = newPlaylist.get(i).uid;
      boolean resolvedAutoGeneratedPlaceholder =
          previousUid instanceof PlaceholderUid && !(newUid instanceof PlaceholderUid);
      if (!previousUid.equals(newUid) && !resolvedAutoGeneratedPlaceholder) {
        return Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED;
      }
    }
    return Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE;
  }

  private static int getPositionDiscontinuityReason(
      State previousState,
      State newState,
      boolean seeked,
      Timeline.Window window,
      Timeline.Period period) {
    if (newState.hasPositionDiscontinuity) {
      // We were asked to report a discontinuity.
      return newState.positionDiscontinuityReason;
    }
    if (seeked) {
      return Player.DISCONTINUITY_REASON_SEEK;
    }
    if (previousState.playlist.isEmpty()) {
      // First change from an empty playlist is not reported as a discontinuity.
      return C.INDEX_UNSET;
    }
    if (newState.playlist.isEmpty()) {
      // The playlist became empty.
      return Player.DISCONTINUITY_REASON_REMOVE;
    }
    Object previousPeriodUid =
        previousState.timeline.getUidOfPeriod(
            getCurrentPeriodIndexInternal(previousState, window, period));
    Object newPeriodUid =
        newState.timeline.getUidOfPeriod(getCurrentPeriodIndexInternal(newState, window, period));
    if (previousPeriodUid instanceof PlaceholderUid && !(newPeriodUid instanceof PlaceholderUid)) {
      // An auto-generated placeholder was resolved to a real item.
      return C.INDEX_UNSET;
    }
    if (!newPeriodUid.equals(previousPeriodUid)
        || previousState.currentAdGroupIndex != newState.currentAdGroupIndex
        || previousState.currentAdIndexInAdGroup != newState.currentAdIndexInAdGroup) {
      // The current period or ad inside a period changed.
      if (newState.timeline.getIndexOfPeriod(previousPeriodUid) == C.INDEX_UNSET) {
        // The previous period no longer exists.
        return Player.DISCONTINUITY_REASON_REMOVE;
      }
      // Check if reached the previous period's or ad's duration to assume an auto-transition.
      long previousPositionMs =
          getCurrentPeriodOrAdPositionMs(previousState, previousPeriodUid, period);
      long previousDurationMs = getPeriodOrAdDurationMs(previousState, previousPeriodUid, period);
      return previousDurationMs != C.TIME_UNSET && previousPositionMs >= previousDurationMs
          ? Player.DISCONTINUITY_REASON_AUTO_TRANSITION
          : Player.DISCONTINUITY_REASON_SKIP;
    }
    // We are in the same content period or ad. Check if the position deviates more than a
    // reasonable threshold from the previous one.
    long previousPositionMs =
        getCurrentPeriodOrAdPositionMs(previousState, previousPeriodUid, period);
    long newPositionMs = getCurrentPeriodOrAdPositionMs(newState, newPeriodUid, period);
    if (Math.abs(previousPositionMs - newPositionMs) < POSITION_DISCONTINUITY_THRESHOLD_MS) {
      return C.INDEX_UNSET;
    }
    // Check if we previously reached the end of the item to assume an auto-repetition.
    long previousDurationMs = getPeriodOrAdDurationMs(previousState, previousPeriodUid, period);
    return previousDurationMs != C.TIME_UNSET && previousPositionMs >= previousDurationMs
        ? Player.DISCONTINUITY_REASON_AUTO_TRANSITION
        : Player.DISCONTINUITY_REASON_INTERNAL;
  }

  private static long getCurrentPeriodOrAdPositionMs(
      State state, Object currentPeriodUid, Timeline.Period period) {
    return state.currentAdGroupIndex != C.INDEX_UNSET
        ? state.adPositionMsSupplier.get()
        : getContentPositionMsInternal(state)
            - state.timeline.getPeriodByUid(currentPeriodUid, period).getPositionInWindowMs();
  }

  private static long getPeriodOrAdDurationMs(
      State state, Object currentPeriodUid, Timeline.Period period) {
    state.timeline.getPeriodByUid(currentPeriodUid, period);
    long periodOrAdDurationUs =
        state.currentAdGroupIndex == C.INDEX_UNSET
            ? period.durationUs
            : period.getAdDurationUs(state.currentAdGroupIndex, state.currentAdIndexInAdGroup);
    return usToMs(periodOrAdDurationUs);
  }

  private static PositionInfo getPositionInfo(
      State state,
      boolean useDiscontinuityPosition,
      Timeline.Window window,
      Timeline.Period period) {
    @Nullable Object windowUid = null;
    @Nullable Object periodUid = null;
    int mediaItemIndex = getCurrentMediaItemIndexInternal(state);
    int periodIndex = C.INDEX_UNSET;
    @Nullable MediaItem mediaItem = null;
    if (!state.timeline.isEmpty()) {
      periodIndex = getCurrentPeriodIndexInternal(state, window, period);
      periodUid = state.timeline.getPeriod(periodIndex, period, /* setIds= */ true).uid;
      windowUid = state.timeline.getWindow(mediaItemIndex, window).uid;
      mediaItem = window.mediaItem;
    }
    long contentPositionMs;
    long positionMs;
    if (useDiscontinuityPosition) {
      positionMs = state.discontinuityPositionMs;
      contentPositionMs =
          state.currentAdGroupIndex == C.INDEX_UNSET
              ? positionMs
              : getContentPositionMsInternal(state);
    } else {
      contentPositionMs = getContentPositionMsInternal(state);
      positionMs =
          state.currentAdGroupIndex != C.INDEX_UNSET
              ? state.adPositionMsSupplier.get()
              : contentPositionMs;
    }
    return new PositionInfo(
        windowUid,
        mediaItemIndex,
        mediaItem,
        periodUid,
        periodIndex,
        positionMs,
        contentPositionMs,
        state.currentAdGroupIndex,
        state.currentAdIndexInAdGroup);
  }

  private static int getMediaItemTransitionReason(
      State previousState,
      State newState,
      int positionDiscontinuityReason,
      boolean isRepeatingCurrentItem,
      Timeline.Window window) {
    Timeline previousTimeline = previousState.timeline;
    Timeline newTimeline = newState.timeline;
    if (newTimeline.isEmpty() && previousTimeline.isEmpty()) {
      return C.INDEX_UNSET;
    } else if (newTimeline.isEmpty() != previousTimeline.isEmpty()) {
      return MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED;
    }
    Object previousWindowUid =
        previousState.timeline.getWindow(getCurrentMediaItemIndexInternal(previousState), window)
            .uid;
    Object newWindowUid =
        newState.timeline.getWindow(getCurrentMediaItemIndexInternal(newState), window).uid;
    if (previousWindowUid instanceof PlaceholderUid && !(newWindowUid instanceof PlaceholderUid)) {
      // An auto-generated placeholder was resolved to a real item.
      return C.INDEX_UNSET;
    }
    if (!previousWindowUid.equals(newWindowUid)) {
      if (positionDiscontinuityReason == DISCONTINUITY_REASON_AUTO_TRANSITION) {
        return MEDIA_ITEM_TRANSITION_REASON_AUTO;
      } else if (positionDiscontinuityReason == DISCONTINUITY_REASON_SEEK) {
        return MEDIA_ITEM_TRANSITION_REASON_SEEK;
      } else {
        return MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED;
      }
    }
    // Only mark changes within the current item as a transition if we are repeating automatically
    // or via a seek to next/previous.
    if (positionDiscontinuityReason == DISCONTINUITY_REASON_AUTO_TRANSITION
        && getContentPositionMsInternal(previousState) > getContentPositionMsInternal(newState)) {
      return MEDIA_ITEM_TRANSITION_REASON_REPEAT;
    }
    if (positionDiscontinuityReason == DISCONTINUITY_REASON_SEEK && isRepeatingCurrentItem) {
      return MEDIA_ITEM_TRANSITION_REASON_SEEK;
    }
    return C.INDEX_UNSET;
  }

  private static Size getSurfaceHolderSize(SurfaceHolder surfaceHolder) {
    if (!surfaceHolder.getSurface().isValid()) {
      return Size.ZERO;
    }
    Rect surfaceFrame = surfaceHolder.getSurfaceFrame();
    return new Size(surfaceFrame.width(), surfaceFrame.height());
  }

  private static int getMediaItemIndexInNewPlaylist(
      List<MediaItemData> oldPlaylist,
      Timeline newPlaylistTimeline,
      int oldMediaItemIndex,
      Timeline.Period period) {
    if (oldPlaylist.isEmpty()) {
      return oldMediaItemIndex < newPlaylistTimeline.getWindowCount()
          ? oldMediaItemIndex
          : C.INDEX_UNSET;
    }
    Object oldFirstPeriodUid =
        oldPlaylist.get(oldMediaItemIndex).getPeriodUid(/* periodIndexInMediaItem= */ 0);
    if (newPlaylistTimeline.getIndexOfPeriod(oldFirstPeriodUid) == C.INDEX_UNSET) {
      return C.INDEX_UNSET;
    }
    return newPlaylistTimeline.getPeriodByUid(oldFirstPeriodUid, period).windowIndex;
  }

  private static State getStateWithNewPlaylist(
      State oldState, List<MediaItemData> newPlaylist, Timeline.Period period) {
    State.Builder stateBuilder = oldState.buildUpon();
    stateBuilder.setPlaylist(newPlaylist);
    Timeline newTimeline = stateBuilder.timeline;
    long oldPositionMs = oldState.contentPositionMsSupplier.get();
    int oldIndex = getCurrentMediaItemIndexInternal(oldState);
    int newIndex = getMediaItemIndexInNewPlaylist(oldState.playlist, newTimeline, oldIndex, period);
    long newPositionMs = newIndex == C.INDEX_UNSET ? C.TIME_UNSET : oldPositionMs;
    // If the current item no longer exists, try to find a matching subsequent item.
    for (int i = oldIndex + 1; newIndex == C.INDEX_UNSET && i < oldState.playlist.size(); i++) {
      // TODO: Use shuffle order to iterate.
      newIndex =
          getMediaItemIndexInNewPlaylist(
              oldState.playlist, newTimeline, /* oldMediaItemIndex= */ i, period);
    }
    // If this fails, transition to ENDED state.
    if (oldState.playbackState != Player.STATE_IDLE && newIndex == C.INDEX_UNSET) {
      stateBuilder.setPlaybackState(Player.STATE_ENDED).setIsLoading(false);
    }
    return buildStateForNewPosition(
        stateBuilder,
        oldState,
        oldPositionMs,
        newPlaylist,
        newIndex,
        newPositionMs,
        /* keepAds= */ true);
  }

  private static State getStateWithNewPlaylistAndPosition(
      State oldState, List<MediaItemData> newPlaylist, int newIndex, long newPositionMs) {
    State.Builder stateBuilder = oldState.buildUpon();
    stateBuilder.setPlaylist(newPlaylist);
    if (oldState.playbackState != Player.STATE_IDLE) {
      if (newPlaylist.isEmpty()) {
        stateBuilder.setPlaybackState(Player.STATE_ENDED).setIsLoading(false);
      } else {
        stateBuilder.setPlaybackState(Player.STATE_BUFFERING);
      }
    }
    long oldPositionMs = oldState.contentPositionMsSupplier.get();
    return buildStateForNewPosition(
        stateBuilder,
        oldState,
        oldPositionMs,
        newPlaylist,
        newIndex,
        newPositionMs,
        /* keepAds= */ false);
  }

  private static State buildStateForNewPosition(
      State.Builder stateBuilder,
      State oldState,
      long oldPositionMs,
      List<MediaItemData> newPlaylist,
      int newIndex,
      long newPositionMs,
      boolean keepAds) {
    // Resolve unset or invalid index and position.
    oldPositionMs = getPositionOrDefaultInMediaItem(oldPositionMs, oldState);
    if (!newPlaylist.isEmpty() && (newIndex == C.INDEX_UNSET || newIndex >= newPlaylist.size())) {
      newIndex = 0; // TODO: Use shuffle order to get first index.
      newPositionMs = C.TIME_UNSET;
    }
    if (!newPlaylist.isEmpty() && newPositionMs == C.TIME_UNSET) {
      newPositionMs = usToMs(newPlaylist.get(newIndex).defaultPositionUs);
    }
    boolean oldOrNewPlaylistEmpty = oldState.playlist.isEmpty() || newPlaylist.isEmpty();
    boolean mediaItemChanged =
        !oldOrNewPlaylistEmpty
            && !oldState
                .playlist
                .get(getCurrentMediaItemIndexInternal(oldState))
                .uid
                .equals(newPlaylist.get(newIndex).uid);
    if (oldOrNewPlaylistEmpty || mediaItemChanged || newPositionMs < oldPositionMs) {
      // New item or seeking back. Assume no buffer and no ad playback persists.
      stateBuilder
          .setCurrentMediaItemIndex(newIndex)
          .setCurrentAd(C.INDEX_UNSET, C.INDEX_UNSET)
          .setContentPositionMs(newPositionMs)
          .setContentBufferedPositionMs(PositionSupplier.getConstant(newPositionMs))
          .setTotalBufferedDurationMs(PositionSupplier.ZERO);
    } else if (newPositionMs == oldPositionMs) {
      // Unchanged position. Assume ad playback and buffer in current item persists.
      stateBuilder.setCurrentMediaItemIndex(newIndex);
      if (oldState.currentAdGroupIndex != C.INDEX_UNSET && keepAds) {
        stateBuilder.setTotalBufferedDurationMs(
            PositionSupplier.getConstant(
                oldState.adBufferedPositionMsSupplier.get() - oldState.adPositionMsSupplier.get()));
      } else {
        stateBuilder
            .setCurrentAd(C.INDEX_UNSET, C.INDEX_UNSET)
            .setTotalBufferedDurationMs(
                PositionSupplier.getConstant(
                    getContentBufferedPositionMsInternal(oldState) - oldPositionMs));
      }
    } else {
      // Seeking forward. Assume remaining buffer in current item persist, but no ad playback.
      long contentBufferedDurationMs =
          max(getContentBufferedPositionMsInternal(oldState), newPositionMs);
      long totalBufferedDurationMs =
          max(0, oldState.totalBufferedDurationMsSupplier.get() - (newPositionMs - oldPositionMs));
      stateBuilder
          .setCurrentMediaItemIndex(newIndex)
          .setCurrentAd(C.INDEX_UNSET, C.INDEX_UNSET)
          .setContentPositionMs(newPositionMs)
          .setContentBufferedPositionMs(PositionSupplier.getConstant(contentBufferedDurationMs))
          .setTotalBufferedDurationMs(PositionSupplier.getConstant(totalBufferedDurationMs));
    }
    return stateBuilder.build();
  }

  private static final class PlaceholderUid {}
}
