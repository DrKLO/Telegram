/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.LOCAL_VARIABLE;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.os.Bundle;
import android.os.Looper;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import androidx.annotation.FloatRange;
import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.CueGroup;
import com.google.android.exoplayer2.trackselection.TrackSelectionParameters;
import com.google.android.exoplayer2.util.FlagSet;
import com.google.android.exoplayer2.util.Size;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.VideoListener;
import com.google.android.exoplayer2.video.VideoSize;
import com.google.common.base.Objects;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;

/**
 * A media player interface defining traditional high-level functionality, such as the ability to
 * play, pause, seek and query properties of the currently playing media.
 *
 * <p>All methods must be called from a single {@linkplain #getApplicationLooper() application
 * thread} unless indicated otherwise. Callbacks in registered listeners are called on the same
 * thread.
 *
 * <p>This interface includes some convenience methods that can be implemented by calling other
 * methods in the interface. {@link BasePlayer} implements these convenience methods so inheriting
 * {@link BasePlayer} is recommended when implementing the interface so that only the minimal set of
 * required methods can be implemented.
 *
 * <p>Some important properties of media players that implement this interface are:
 *
 * <ul>
 *   <li>They can provide a {@link Timeline} representing the structure of the media being played,
 *       which can be obtained by calling {@link #getCurrentTimeline()}.
 *   <li>They can provide a {@link Tracks} defining the currently available tracks and which are
 *       selected to be rendered, which can be obtained by calling {@link #getCurrentTracks()}.
 * </ul>
 */
public interface Player {

  /** A set of {@linkplain Event events}. */
  final class Events {

    private final FlagSet flags;

    /**
     * Creates an instance.
     *
     * @param flags The {@link FlagSet} containing the {@linkplain Event events}.
     */
    public Events(FlagSet flags) {
      this.flags = flags;
    }

    /**
     * Returns whether the given {@link Event} occurred.
     *
     * @param event The {@link Event}.
     * @return Whether the {@link Event} occurred.
     */
    public boolean contains(@Event int event) {
      return flags.contains(event);
    }

    /**
     * Returns whether any of the given {@linkplain Event events} occurred.
     *
     * @param events The {@linkplain Event events}.
     * @return Whether any of the {@linkplain Event events} occurred.
     */
    public boolean containsAny(@Event int... events) {
      return flags.containsAny(events);
    }

    /** Returns the number of events in the set. */
    public int size() {
      return flags.size();
    }

    /**
     * Returns the {@link Event} at the given index.
     *
     * <p>Although index-based access is possible, it doesn't imply a particular order of these
     * events.
     *
     * @param index The index. Must be between 0 (inclusive) and {@link #size()} (exclusive).
     * @return The {@link Event} at the given index.
     * @throws IndexOutOfBoundsException If index is outside the allowed range.
     */
    public @Event int get(int index) {
      return flags.get(index);
    }

    @Override
    public int hashCode() {
      return flags.hashCode();
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (this == obj) {
        return true;
      }
      if (!(obj instanceof Events)) {
        return false;
      }
      Events other = (Events) obj;
      return flags.equals(other.flags);
    }
  }

  /** Position info describing a playback position involved in a discontinuity. */
  final class PositionInfo implements Bundleable {

    /**
     * The UID of the window, or {@code null} if the timeline is {@link Timeline#isEmpty() empty}.
     */
    @Nullable public final Object windowUid;
    /**
     * @deprecated Use {@link #mediaItemIndex} instead.
     */
    @Deprecated public final int windowIndex;
    /** The media item index. */
    public final int mediaItemIndex;
    /** The media item, or {@code null} if the timeline is {@link Timeline#isEmpty() empty}. */
    @Nullable public final MediaItem mediaItem;
    /**
     * The UID of the period, or {@code null} if the timeline is {@link Timeline#isEmpty() empty}.
     */
    @Nullable public final Object periodUid;
    /** The period index. */
    public final int periodIndex;
    /** The playback position, in milliseconds. */
    public final long positionMs;
    /**
     * The content position, in milliseconds.
     *
     * <p>If {@link #adGroupIndex} is {@link C#INDEX_UNSET}, this is the same as {@link
     * #positionMs}.
     */
    public final long contentPositionMs;
    /**
     * The ad group index if the playback position is within an ad, {@link C#INDEX_UNSET} otherwise.
     */
    public final int adGroupIndex;
    /**
     * The index of the ad within the ad group if the playback position is within an ad, {@link
     * C#INDEX_UNSET} otherwise.
     */
    public final int adIndexInAdGroup;

    /**
     * @deprecated Use {@link #PositionInfo(Object, int, MediaItem, Object, int, long, long, int,
     *     int)} instead.
     */
    @Deprecated
    public PositionInfo(
        @Nullable Object windowUid,
        int mediaItemIndex,
        @Nullable Object periodUid,
        int periodIndex,
        long positionMs,
        long contentPositionMs,
        int adGroupIndex,
        int adIndexInAdGroup) {
      this(
          windowUid,
          mediaItemIndex,
          MediaItem.EMPTY,
          periodUid,
          periodIndex,
          positionMs,
          contentPositionMs,
          adGroupIndex,
          adIndexInAdGroup);
    }

    /** Creates an instance. */
    @SuppressWarnings("deprecation") // Setting deprecated windowIndex field
    public PositionInfo(
        @Nullable Object windowUid,
        int mediaItemIndex,
        @Nullable MediaItem mediaItem,
        @Nullable Object periodUid,
        int periodIndex,
        long positionMs,
        long contentPositionMs,
        int adGroupIndex,
        int adIndexInAdGroup) {
      this.windowUid = windowUid;
      this.windowIndex = mediaItemIndex;
      this.mediaItemIndex = mediaItemIndex;
      this.mediaItem = mediaItem;
      this.periodUid = periodUid;
      this.periodIndex = periodIndex;
      this.positionMs = positionMs;
      this.contentPositionMs = contentPositionMs;
      this.adGroupIndex = adGroupIndex;
      this.adIndexInAdGroup = adIndexInAdGroup;
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      PositionInfo that = (PositionInfo) o;
      return mediaItemIndex == that.mediaItemIndex
          && periodIndex == that.periodIndex
          && positionMs == that.positionMs
          && contentPositionMs == that.contentPositionMs
          && adGroupIndex == that.adGroupIndex
          && adIndexInAdGroup == that.adIndexInAdGroup
          && Objects.equal(windowUid, that.windowUid)
          && Objects.equal(periodUid, that.periodUid)
          && Objects.equal(mediaItem, that.mediaItem);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(
          windowUid,
          mediaItemIndex,
          mediaItem,
          periodUid,
          periodIndex,
          positionMs,
          contentPositionMs,
          adGroupIndex,
          adIndexInAdGroup);
    }

    // Bundleable implementation.

    private static final String FIELD_MEDIA_ITEM_INDEX = Util.intToStringMaxRadix(0);
    private static final String FIELD_MEDIA_ITEM = Util.intToStringMaxRadix(1);
    private static final String FIELD_PERIOD_INDEX = Util.intToStringMaxRadix(2);
    private static final String FIELD_POSITION_MS = Util.intToStringMaxRadix(3);
    private static final String FIELD_CONTENT_POSITION_MS = Util.intToStringMaxRadix(4);
    private static final String FIELD_AD_GROUP_INDEX = Util.intToStringMaxRadix(5);
    private static final String FIELD_AD_INDEX_IN_AD_GROUP = Util.intToStringMaxRadix(6);

    /**
     * {@inheritDoc}
     *
     * <p>It omits the {@link #windowUid} and {@link #periodUid} fields. The {@link #windowUid} and
     * {@link #periodUid} of an instance restored by {@link #CREATOR} will always be {@code null}.
     */
    @Override
    public Bundle toBundle() {
      return toBundle(/* canAccessCurrentMediaItem= */ true, /* canAccessTimeline= */ true);
    }

    /**
     * Returns a {@link Bundle} representing the information stored in this object, filtered by
     * available commands.
     *
     * @param canAccessCurrentMediaItem Whether the {@link Bundle} should contain information
     *     accessbile with {@link #COMMAND_GET_CURRENT_MEDIA_ITEM}.
     * @param canAccessTimeline Whether the {@link Bundle} should contain information accessbile
     *     with {@link #COMMAND_GET_TIMELINE}.
     */
    public Bundle toBundle(boolean canAccessCurrentMediaItem, boolean canAccessTimeline) {
      Bundle bundle = new Bundle();
      bundle.putInt(FIELD_MEDIA_ITEM_INDEX, canAccessTimeline ? mediaItemIndex : 0);
      if (mediaItem != null && canAccessCurrentMediaItem) {
        bundle.putBundle(FIELD_MEDIA_ITEM, mediaItem.toBundle());
      }
      bundle.putInt(FIELD_PERIOD_INDEX, canAccessTimeline ? periodIndex : 0);
      bundle.putLong(FIELD_POSITION_MS, canAccessCurrentMediaItem ? positionMs : 0);
      bundle.putLong(FIELD_CONTENT_POSITION_MS, canAccessCurrentMediaItem ? contentPositionMs : 0);
      bundle.putInt(FIELD_AD_GROUP_INDEX, canAccessCurrentMediaItem ? adGroupIndex : C.INDEX_UNSET);
      bundle.putInt(
          FIELD_AD_INDEX_IN_AD_GROUP, canAccessCurrentMediaItem ? adIndexInAdGroup : C.INDEX_UNSET);
      return bundle;
    }

    /** Object that can restore {@link PositionInfo} from a {@link Bundle}. */
    public static final Creator<PositionInfo> CREATOR = PositionInfo::fromBundle;

    private static PositionInfo fromBundle(Bundle bundle) {
      int mediaItemIndex = bundle.getInt(FIELD_MEDIA_ITEM_INDEX, /* defaultValue= */ 0);
      @Nullable Bundle mediaItemBundle = bundle.getBundle(FIELD_MEDIA_ITEM);
      @Nullable
      MediaItem mediaItem =
          mediaItemBundle == null ? null : MediaItem.CREATOR.fromBundle(mediaItemBundle);
      int periodIndex = bundle.getInt(FIELD_PERIOD_INDEX, /* defaultValue= */ 0);
      long positionMs = bundle.getLong(FIELD_POSITION_MS, /* defaultValue= */ 0);
      long contentPositionMs = bundle.getLong(FIELD_CONTENT_POSITION_MS, /* defaultValue= */ 0);
      int adGroupIndex = bundle.getInt(FIELD_AD_GROUP_INDEX, /* defaultValue= */ C.INDEX_UNSET);
      int adIndexInAdGroup =
          bundle.getInt(FIELD_AD_INDEX_IN_AD_GROUP, /* defaultValue= */ C.INDEX_UNSET);
      return new PositionInfo(
          /* windowUid= */ null,
          mediaItemIndex,
          mediaItem,
          /* periodUid= */ null,
          periodIndex,
          positionMs,
          contentPositionMs,
          adGroupIndex,
          adIndexInAdGroup);
    }
  }

  /**
   * A set of {@linkplain Command commands}.
   *
   * <p>Instances are immutable.
   */
  final class Commands implements Bundleable {

    /** A builder for {@link Commands} instances. */
    public static final class Builder {

      private static final @Command int[] SUPPORTED_COMMANDS = {
        COMMAND_PLAY_PAUSE,
        COMMAND_PREPARE,
        COMMAND_STOP,
        COMMAND_SEEK_TO_DEFAULT_POSITION,
        COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
        COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
        COMMAND_SEEK_TO_PREVIOUS,
        COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
        COMMAND_SEEK_TO_NEXT,
        COMMAND_SEEK_TO_MEDIA_ITEM,
        COMMAND_SEEK_BACK,
        COMMAND_SEEK_FORWARD,
        COMMAND_SET_SPEED_AND_PITCH,
        COMMAND_SET_SHUFFLE_MODE,
        COMMAND_SET_REPEAT_MODE,
        COMMAND_GET_CURRENT_MEDIA_ITEM,
        COMMAND_GET_TIMELINE,
        COMMAND_GET_MEDIA_ITEMS_METADATA,
        COMMAND_SET_MEDIA_ITEMS_METADATA,
        COMMAND_SET_MEDIA_ITEM,
        COMMAND_CHANGE_MEDIA_ITEMS,
        COMMAND_GET_AUDIO_ATTRIBUTES,
        COMMAND_GET_VOLUME,
        COMMAND_GET_DEVICE_VOLUME,
        COMMAND_SET_VOLUME,
        COMMAND_SET_DEVICE_VOLUME,
        COMMAND_ADJUST_DEVICE_VOLUME,
        COMMAND_SET_VIDEO_SURFACE,
        COMMAND_GET_TEXT,
        COMMAND_SET_TRACK_SELECTION_PARAMETERS,
        COMMAND_GET_TRACKS,
      };

      private final FlagSet.Builder flagsBuilder;

      /** Creates a builder. */
      public Builder() {
        flagsBuilder = new FlagSet.Builder();
      }

      private Builder(Commands commands) {
        flagsBuilder = new FlagSet.Builder();
        flagsBuilder.addAll(commands.flags);
      }

      /**
       * Adds a {@link Command}.
       *
       * @param command A {@link Command}.
       * @return This builder.
       * @throws IllegalStateException If {@link #build()} has already been called.
       */
      @CanIgnoreReturnValue
      public Builder add(@Command int command) {
        flagsBuilder.add(command);
        return this;
      }

      /**
       * Adds a {@link Command} if the provided condition is true. Does nothing otherwise.
       *
       * @param command A {@link Command}.
       * @param condition A condition.
       * @return This builder.
       * @throws IllegalStateException If {@link #build()} has already been called.
       */
      @CanIgnoreReturnValue
      public Builder addIf(@Command int command, boolean condition) {
        flagsBuilder.addIf(command, condition);
        return this;
      }

      /**
       * Adds {@linkplain Command commands}.
       *
       * @param commands The {@linkplain Command commands} to add.
       * @return This builder.
       * @throws IllegalStateException If {@link #build()} has already been called.
       */
      @CanIgnoreReturnValue
      public Builder addAll(@Command int... commands) {
        flagsBuilder.addAll(commands);
        return this;
      }

      /**
       * Adds {@link Commands}.
       *
       * @param commands The set of {@linkplain Command commands} to add.
       * @return This builder.
       * @throws IllegalStateException If {@link #build()} has already been called.
       */
      @CanIgnoreReturnValue
      public Builder addAll(Commands commands) {
        flagsBuilder.addAll(commands.flags);
        return this;
      }

      /**
       * Adds all existing {@linkplain Command commands}.
       *
       * @return This builder.
       * @throws IllegalStateException If {@link #build()} has already been called.
       */
      @CanIgnoreReturnValue
      public Builder addAllCommands() {
        flagsBuilder.addAll(SUPPORTED_COMMANDS);
        return this;
      }

      /**
       * Removes a {@link Command}.
       *
       * @param command A {@link Command}.
       * @return This builder.
       * @throws IllegalStateException If {@link #build()} has already been called.
       */
      @CanIgnoreReturnValue
      public Builder remove(@Command int command) {
        flagsBuilder.remove(command);
        return this;
      }

      /**
       * Removes a {@link Command} if the provided condition is true. Does nothing otherwise.
       *
       * @param command A {@link Command}.
       * @param condition A condition.
       * @return This builder.
       * @throws IllegalStateException If {@link #build()} has already been called.
       */
      @CanIgnoreReturnValue
      public Builder removeIf(@Command int command, boolean condition) {
        flagsBuilder.removeIf(command, condition);
        return this;
      }

      /**
       * Removes {@linkplain Command commands}.
       *
       * @param commands The {@linkplain Command commands} to remove.
       * @return This builder.
       * @throws IllegalStateException If {@link #build()} has already been called.
       */
      @CanIgnoreReturnValue
      public Builder removeAll(@Command int... commands) {
        flagsBuilder.removeAll(commands);
        return this;
      }

      /**
       * Builds a {@link Commands} instance.
       *
       * @throws IllegalStateException If this method has already been called.
       */
      public Commands build() {
        return new Commands(flagsBuilder.build());
      }
    }

    /** An empty set of commands. */
    public static final Commands EMPTY = new Builder().build();

    private final FlagSet flags;

    private Commands(FlagSet flags) {
      this.flags = flags;
    }

    /** Returns a {@link Builder} initialized with the values of this instance. */
    public Builder buildUpon() {
      return new Builder(this);
    }

    /** Returns whether the set of commands contains the specified {@link Command}. */
    public boolean contains(@Command int command) {
      return flags.contains(command);
    }

    /** Returns whether the set of commands contains at least one of the given {@code commands}. */
    public boolean containsAny(@Command int... commands) {
      return flags.containsAny(commands);
    }

    /** Returns the number of commands in this set. */
    public int size() {
      return flags.size();
    }

    /**
     * Returns the {@link Command} at the given index.
     *
     * @param index The index. Must be between 0 (inclusive) and {@link #size()} (exclusive).
     * @return The {@link Command} at the given index.
     * @throws IndexOutOfBoundsException If index is outside the allowed range.
     */
    public @Command int get(int index) {
      return flags.get(index);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (this == obj) {
        return true;
      }
      if (!(obj instanceof Commands)) {
        return false;
      }
      Commands commands = (Commands) obj;
      return flags.equals(commands.flags);
    }

    @Override
    public int hashCode() {
      return flags.hashCode();
    }

    // Bundleable implementation.

    private static final String FIELD_COMMANDS = Util.intToStringMaxRadix(0);

    @Override
    public Bundle toBundle() {
      Bundle bundle = new Bundle();
      ArrayList<Integer> commandsBundle = new ArrayList<>();
      for (int i = 0; i < flags.size(); i++) {
        commandsBundle.add(flags.get(i));
      }
      bundle.putIntegerArrayList(FIELD_COMMANDS, commandsBundle);
      return bundle;
    }

    /** Object that can restore {@link Commands} from a {@link Bundle}. */
    public static final Creator<Commands> CREATOR = Commands::fromBundle;

    private static Commands fromBundle(Bundle bundle) {
      @Nullable ArrayList<Integer> commands = bundle.getIntegerArrayList(FIELD_COMMANDS);
      if (commands == null) {
        return Commands.EMPTY;
      }
      Builder builder = new Builder();
      for (int i = 0; i < commands.size(); i++) {
        builder.add(commands.get(i));
      }
      return builder.build();
    }
  }

  /**
   * Listener for changes in a {@link Player}.
   *
   * <p>All methods have no-op default implementations to allow selective overrides.
   *
   * <p>If the return value of a {@link Player} getter changes due to a change in {@linkplain
   * #onAvailableCommandsChanged(Commands) command availability}, the corresponding listener
   * method(s) will be invoked. If the return value of a {@link Player} getter does not change
   * because the corresponding command is {@linkplain #onAvailableCommandsChanged(Commands) not
   * available}, the corresponding listener method will not be invoked.
   */
  interface Listener {

    /**
     * Called when one or more player states changed.
     *
     * <p>State changes and events that happen within one {@link Looper} message queue iteration are
     * reported together and only after all individual callbacks were triggered.
     *
     * <p>Listeners should prefer this method over individual callbacks in the following cases:
     *
     * <ul>
     *   <li>They intend to trigger the same logic for multiple events (e.g. when updating a UI for
     *       both {@link #onPlaybackStateChanged(int)} and {@link #onPlayWhenReadyChanged(boolean,
     *       int)}).
     *   <li>They need access to the {@link Player} object to trigger further events (e.g. to call
     *       {@link Player#seekTo(long)} after a {@link #onMediaItemTransition(MediaItem, int)}).
     *   <li>They intend to use multiple state values together or in combination with {@link Player}
     *       getter methods. For example using {@link #getCurrentMediaItemIndex()} with the {@code
     *       timeline} provided in {@link #onTimelineChanged(Timeline, int)} is only safe from
     *       within this method.
     *   <li>They are interested in events that logically happened together (e.g {@link
     *       #onPlaybackStateChanged(int)} to {@link #STATE_BUFFERING} because of {@link
     *       #onMediaItemTransition(MediaItem, int)}).
     * </ul>
     *
     * @param player The {@link Player} whose state changed. Use the getters to obtain the latest
     *     states.
     * @param events The {@link Events} that happened in this iteration, indicating which player
     *     states changed.
     */
    default void onEvents(Player player, Events events) {}

    /**
     * Called when the value of {@link Player#getCurrentTimeline()} changes.
     *
     * <p>Note that the current {@link MediaItem} or playback position may change as a result of a
     * timeline change. If playback can't continue smoothly because of this timeline change, a
     * separate {@link #onPositionDiscontinuity(PositionInfo, PositionInfo, int)} callback will be
     * triggered.
     *
     * <p>{@link #onEvents(Player, Events)} will also be called to report this event along with
     * other events that happen in the same {@link Looper} message queue iteration.
     *
     * @param timeline The latest timeline. Never null, but may be empty.
     * @param reason The {@link TimelineChangeReason} responsible for this timeline change.
     */
    default void onTimelineChanged(Timeline timeline, @TimelineChangeReason int reason) {}

    /**
     * Called when playback transitions to a media item or starts repeating a media item according
     * to the current {@link #getRepeatMode() repeat mode}.
     *
     * <p>Note that this callback is also called when the value of {@link #getCurrentTimeline()}
     * becomes non-empty or empty.
     *
     * <p>{@link #onEvents(Player, Events)} will also be called to report this event along with
     * other events that happen in the same {@link Looper} message queue iteration.
     *
     * @param mediaItem The {@link MediaItem}. May be null if the playlist becomes empty.
     * @param reason The reason for the transition.
     */
    default void onMediaItemTransition(
        @Nullable MediaItem mediaItem, @MediaItemTransitionReason int reason) {}

    /**
     * Called when the value of {@link Player#getCurrentTracks()} changes.
     *
     * <p>{@link #onEvents(Player, Events)} will also be called to report this event along with
     * other events that happen in the same {@link Looper} message queue iteration.
     *
     * @param tracks The available tracks information. Never null, but may be of length zero.
     */
    default void onTracksChanged(Tracks tracks) {}

    /**
     * Called when the value of {@link Player#getMediaMetadata()} changes.
     *
     * <p>This method may be called multiple times in quick succession.
     *
     * <p>{@link #onEvents(Player, Events)} will also be called to report this event along with
     * other events that happen in the same {@link Looper} message queue iteration.
     *
     * @param mediaMetadata The combined {@link MediaMetadata}.
     */
    default void onMediaMetadataChanged(MediaMetadata mediaMetadata) {}

    /**
     * Called when the value of {@link Player#getPlaylistMetadata()} changes.
     *
     * <p>{@link #onEvents(Player, Events)} will also be called to report this event along with
     * other events that happen in the same {@link Looper} message queue iteration.
     */
    default void onPlaylistMetadataChanged(MediaMetadata mediaMetadata) {}

    /**
     * Called when the player starts or stops loading the source.
     *
     * <p>{@link #onEvents(Player, Events)} will also be called to report this event along with
     * other events that happen in the same {@link Looper} message queue iteration.
     *
     * @param isLoading Whether the source is currently being loaded.
     */
    default void onIsLoadingChanged(boolean isLoading) {}

    /**
     * @deprecated Use {@link #onIsLoadingChanged(boolean)} instead.
     */
    @Deprecated
    default void onLoadingChanged(boolean isLoading) {}

    /**
     * Called when the value returned from {@link #isCommandAvailable(int)} changes for at least one
     * {@link Command}.
     *
     * <p>{@link #onEvents(Player, Events)} will also be called to report this event along with
     * other events that happen in the same {@link Looper} message queue iteration.
     *
     * @param availableCommands The available {@link Commands}.
     */
    default void onAvailableCommandsChanged(Commands availableCommands) {}

    /**
     * Called when the value returned from {@link #getTrackSelectionParameters()} changes.
     *
     * <p>{@link #onEvents(Player, Events)} will also be called to report this event along with
     * other events that happen in the same {@link Looper} message queue iteration.
     *
     * @param parameters The new {@link TrackSelectionParameters}.
     */
    default void onTrackSelectionParametersChanged(TrackSelectionParameters parameters) {}

    /**
     * @deprecated Use {@link #onPlaybackStateChanged(int)} and {@link
     *     #onPlayWhenReadyChanged(boolean, int)} instead.
     */
    @Deprecated
    default void onPlayerStateChanged(boolean playWhenReady, @State int playbackState) {}

    /**
     * Called when the value returned from {@link #getPlaybackState()} changes.
     *
     * <p>{@link #onEvents(Player, Events)} will also be called to report this event along with
     * other events that happen in the same {@link Looper} message queue iteration.
     *
     * @param playbackState The new playback {@link State}.
     */
    default void onPlaybackStateChanged(@State int playbackState) {}

    /**
     * Called when the value returned from {@link #getPlayWhenReady()} changes.
     *
     * <p>{@link #onEvents(Player, Events)} will also be called to report this event along with
     * other events that happen in the same {@link Looper} message queue iteration.
     *
     * @param playWhenReady Whether playback will proceed when ready.
     * @param reason The {@link PlayWhenReadyChangeReason} for the change.
     */
    default void onPlayWhenReadyChanged(
        boolean playWhenReady, @PlayWhenReadyChangeReason int reason) {}

    /**
     * Called when the value returned from {@link #getPlaybackSuppressionReason()} changes.
     *
     * <p>{@link #onEvents(Player, Events)} will also be called to report this event along with
     * other events that happen in the same {@link Looper} message queue iteration.
     *
     * @param playbackSuppressionReason The current {@link PlaybackSuppressionReason}.
     */
    default void onPlaybackSuppressionReasonChanged(
        @PlaybackSuppressionReason int playbackSuppressionReason) {}

    /**
     * Called when the value of {@link #isPlaying()} changes.
     *
     * <p>{@link #onEvents(Player, Events)} will also be called to report this event along with
     * other events that happen in the same {@link Looper} message queue iteration.
     *
     * @param isPlaying Whether the player is playing.
     */
    default void onIsPlayingChanged(boolean isPlaying) {}

    /**
     * Called when the value of {@link #getRepeatMode()} changes.
     *
     * <p>{@link #onEvents(Player, Events)} will also be called to report this event along with
     * other events that happen in the same {@link Looper} message queue iteration.
     *
     * @param repeatMode The {@link RepeatMode} used for playback.
     */
    default void onRepeatModeChanged(@RepeatMode int repeatMode) {}

    /**
     * Called when the value of {@link #getShuffleModeEnabled()} changes.
     *
     * <p>{@link #onEvents(Player, Events)} will also be called to report this event along with
     * other events that happen in the same {@link Looper} message queue iteration.
     *
     * @param shuffleModeEnabled Whether shuffling of {@linkplain MediaItem media items} is enabled.
     */
    default void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {}

    /**
     * Called when an error occurs. The playback state will transition to {@link #STATE_IDLE}
     * immediately after this method is called. The player instance can still be used, and {@link
     * #release()} must still be called on the player should it no longer be required.
     *
     * <p>{@link #onEvents(Player, Events)} will also be called to report this event along with
     * other events that happen in the same {@link Looper} message queue iteration.
     *
     * <p>Implementations of Player may pass an instance of a subclass of {@link PlaybackException}
     * to this method in order to include more information about the error.
     *
     * @param error The error.
     */
    default void onPlayerError(PlaybackException error) {}

    /**
     * Called when the {@link PlaybackException} returned by {@link #getPlayerError()} changes.
     *
     * <p>{@link #onEvents(Player, Events)} will also be called to report this event along with
     * other events that happen in the same {@link Looper} message queue iteration.
     *
     * <p>Implementations of Player may pass an instance of a subclass of {@link PlaybackException}
     * to this method in order to include more information about the error.
     *
     * @param error The new error, or null if the error is being cleared.
     */
    default void onPlayerErrorChanged(@Nullable PlaybackException error) {}

    /**
     * @deprecated Use {@link #onPositionDiscontinuity(PositionInfo, PositionInfo, int)} instead.
     */
    @Deprecated
    default void onPositionDiscontinuity(@DiscontinuityReason int reason) {}

    /**
     * Called when a position discontinuity occurs.
     *
     * <p>A position discontinuity occurs when the playing period changes, the playback position
     * jumps within the period currently being played, or when the playing period has been skipped
     * or removed.
     *
     * <p>{@link #onEvents(Player, Events)} will also be called to report this event along with
     * other events that happen in the same {@link Looper} message queue iteration.
     *
     * @param oldPosition The position before the discontinuity.
     * @param newPosition The position after the discontinuity.
     * @param reason The {@link DiscontinuityReason} responsible for the discontinuity.
     */
    default void onPositionDiscontinuity(
        PositionInfo oldPosition, PositionInfo newPosition, @DiscontinuityReason int reason) {}

    /**
     * Called when the value of {@link #getPlaybackParameters()} changes. The playback parameters
     * may change due to a call to {@link #setPlaybackParameters(PlaybackParameters)}, or the player
     * itself may change them (for example, if audio playback switches to passthrough or offload
     * mode, where speed adjustment is no longer possible).
     *
     * <p>{@link #onEvents(Player, Events)} will also be called to report this event along with
     * other events that happen in the same {@link Looper} message queue iteration.
     *
     * @param playbackParameters The playback parameters.
     */
    default void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {}

    /**
     * Called when the value of {@link #getSeekBackIncrement()} changes.
     *
     * <p>{@link #onEvents(Player, Events)} will also be called to report this event along with
     * other events that happen in the same {@link Looper} message queue iteration.
     *
     * @param seekBackIncrementMs The {@link #seekBack()} increment, in milliseconds.
     */
    default void onSeekBackIncrementChanged(long seekBackIncrementMs) {}

    /**
     * Called when the value of {@link #getSeekForwardIncrement()} changes.
     *
     * <p>{@link #onEvents(Player, Events)} will also be called to report this event along with
     * other events that happen in the same {@link Looper} message queue iteration.
     *
     * @param seekForwardIncrementMs The {@link #seekForward()} increment, in milliseconds.
     */
    default void onSeekForwardIncrementChanged(long seekForwardIncrementMs) {}

    /**
     * Called when the value of {@link #getMaxSeekToPreviousPosition()} changes.
     *
     * <p>{@link #onEvents(Player, Events)} will also be called to report this event along with
     * other events that happen in the same {@link Looper} message queue iteration.
     *
     * @param maxSeekToPreviousPositionMs The maximum position for which {@link #seekToPrevious()}
     *     seeks to the previous position, in milliseconds.
     */
    default void onMaxSeekToPreviousPositionChanged(long maxSeekToPreviousPositionMs) {}

    /**
     * @deprecated Seeks are processed without delay. Listen to {@link
     *     #onPositionDiscontinuity(PositionInfo, PositionInfo, int)} with reason {@link
     *     #DISCONTINUITY_REASON_SEEK} instead.
     */
    @Deprecated
    default void onSeekProcessed() {}

    /**
     * Called when the audio session ID changes.
     *
     * <p>{@link #onEvents(Player, Events)} will also be called to report this event along with
     * other events that happen in the same {@link Looper} message queue iteration.
     *
     * @param audioSessionId The audio session ID.
     */
    default void onAudioSessionIdChanged(int audioSessionId) {}

    /**
     * Called when the value of {@link #getAudioAttributes()} changes.
     *
     * <p>{@link #onEvents(Player, Events)} will also be called to report this event along with
     * other events that happen in the same {@link Looper} message queue iteration.
     *
     * @param audioAttributes The audio attributes.
     */
    default void onAudioAttributesChanged(AudioAttributes audioAttributes) {}

    /**
     * Called when the value of {@link #getVolume()} changes.
     *
     * <p>{@link #onEvents(Player, Events)} will also be called to report this event along with
     * other events that happen in the same {@link Looper} message queue iteration.
     *
     * @param volume The new volume, with 0 being silence and 1 being unity gain.
     */
    default void onVolumeChanged(float volume) {}

    /**
     * Called when skipping silences is enabled or disabled in the audio stream.
     *
     * <p>{@link #onEvents(Player, Events)} will also be called to report this event along with
     * other events that happen in the same {@link Looper} message queue iteration.
     *
     * @param skipSilenceEnabled Whether skipping silences in the audio stream is enabled.
     */
    default void onSkipSilenceEnabledChanged(boolean skipSilenceEnabled) {}

    /**
     * Called when the device information changes
     *
     * <p>{@link #onEvents(Player, Events)} will also be called to report this event along with
     * other events that happen in the same {@link Looper} message queue iteration.
     *
     * @param deviceInfo The new {@link DeviceInfo}.
     */
    default void onDeviceInfoChanged(DeviceInfo deviceInfo) {}

    /**
     * Called when the value of {@link #getDeviceVolume()} or {@link #isDeviceMuted()} changes.
     *
     * <p>{@link #onEvents(Player, Events)} will also be called to report this event along with
     * other events that happen in the same {@link Looper} message queue iteration.
     *
     * @param volume The new device volume, with 0 being silence and 1 being unity gain.
     * @param muted Whether the device is muted.
     */
    default void onDeviceVolumeChanged(int volume, boolean muted) {}

    /**
     * Called each time there's a change in the size of the video being rendered.
     *
     * <p>{@link #onEvents(Player, Events)} will also be called to report this event along with
     * other events that happen in the same {@link Looper} message queue iteration.
     *
     * @param videoSize The new size of the video.
     */
    default void onVideoSizeChanged(VideoSize videoSize) {}

    /**
     * Called each time there's a change in the size of the surface onto which the video is being
     * rendered.
     *
     * <p>{@link #onEvents(Player, Events)} will also be called to report this event along with
     * other events that happen in the same {@link Looper} message queue iteration.
     *
     * @param width The surface width in pixels. May be {@link C#LENGTH_UNSET} if unknown, or 0 if
     *     the video is not rendered onto a surface.
     * @param height The surface height in pixels. May be {@link C#LENGTH_UNSET} if unknown, or 0 if
     *     the video is not rendered onto a surface.
     */
    default void onSurfaceSizeChanged(int width, int height) {}

    /**
     * Called when a frame is rendered for the first time since setting the surface, or since the
     * renderer was reset, or since the stream being rendered was changed.
     *
     * <p>{@link #onEvents(Player, Events)} will also be called to report this event along with
     * other events that happen in the same {@link Looper} message queue iteration.
     */
    default void onRenderedFirstFrame() {}

    /**
     * Called when the value of {@link #getCurrentCues()} changes.
     *
     * <p>Both this method and {@link #onCues(CueGroup)} are called when there is a change in the
     * cues. You should only implement one or the other.
     *
     * <p>{@link #onEvents(Player, Events)} will also be called to report this event along with
     * other events that happen in the same {@link Looper} message queue iteration.
     *
     * @deprecated Use {@link #onCues(CueGroup)} instead.
     */
    @Deprecated
    default void onCues(List<Cue> cues) {}

    /**
     * Called when the value of {@link #getCurrentCues()} changes.
     *
     * <p>Both this method and {@link #onCues(List)} are called when there is a change in the cues.
     * You should only implement one or the other.
     *
     * <p>{@link #onEvents(Player, Events)} will also be called to report this event along with
     * other events that happen in the same {@link Looper} message queue iteration.
     */
    default void onCues(CueGroup cueGroup) {}

    /**
     * Called when there is metadata associated with the current playback time.
     *
     * <p>{@link #onEvents(Player, Events)} will also be called to report this event along with
     * other events that happen in the same {@link Looper} message queue iteration.
     *
     * @param metadata The metadata.
     */
    default void onMetadata(Metadata metadata) {}
  }

  /**
   * Playback state. One of {@link #STATE_IDLE}, {@link #STATE_BUFFERING}, {@link #STATE_READY} or
   * {@link #STATE_ENDED}.
   */
  // @Target list includes both 'default' targets and TYPE_USE, to ensure backwards compatibility
  // with Kotlin usages from before TYPE_USE was added.
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target({FIELD, METHOD, PARAMETER, LOCAL_VARIABLE, TYPE_USE})
  @IntDef({STATE_IDLE, STATE_BUFFERING, STATE_READY, STATE_ENDED})
  @interface State {}
  /**
   * The player is idle, meaning it holds only limited resources. The player must be {@link
   * #prepare() prepared} before it will play the media.
   */
  int STATE_IDLE = 1;
  /**
   * The player is not able to immediately play the media, but is doing work toward being able to do
   * so. This state typically occurs when the player needs to buffer more data before playback can
   * start.
   */
  int STATE_BUFFERING = 2;
  /**
   * The player is able to immediately play from its current position. The player will be playing if
   * {@link #getPlayWhenReady()} is true, and paused otherwise.
   */
  int STATE_READY = 3;
  /** The player has finished playing the media. */
  int STATE_ENDED = 4;

  /**
   * Reasons for {@link #getPlayWhenReady() playWhenReady} changes. One of {@link
   * #PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST}, {@link
   * #PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS}, {@link
   * #PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY}, {@link
   * #PLAY_WHEN_READY_CHANGE_REASON_REMOTE} or {@link
   * #PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM}.
   */
  // @Target list includes both 'default' targets and TYPE_USE, to ensure backwards compatibility
  // with Kotlin usages from before TYPE_USE was added.
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target({FIELD, METHOD, PARAMETER, LOCAL_VARIABLE, TYPE_USE})
  @IntDef({
    PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
    PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS,
    PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY,
    PLAY_WHEN_READY_CHANGE_REASON_REMOTE,
    PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM
  })
  @interface PlayWhenReadyChangeReason {}
  /** Playback has been started or paused by a call to {@link #setPlayWhenReady(boolean)}. */
  int PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST = 1;
  /** Playback has been paused because of a loss of audio focus. */
  int PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS = 2;
  /** Playback has been paused to avoid becoming noisy. */
  int PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY = 3;
  /** Playback has been started or paused because of a remote change. */
  int PLAY_WHEN_READY_CHANGE_REASON_REMOTE = 4;
  /** Playback has been paused at the end of a media item. */
  int PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM = 5;

  /**
   * Reason why playback is suppressed even though {@link #getPlayWhenReady()} is {@code true}. One
   * of {@link #PLAYBACK_SUPPRESSION_REASON_NONE} or {@link
   * #PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS}.
   */
  // @Target list includes both 'default' targets and TYPE_USE, to ensure backwards compatibility
  // with Kotlin usages from before TYPE_USE was added.
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target({FIELD, METHOD, PARAMETER, LOCAL_VARIABLE, TYPE_USE})
  @IntDef({
    PLAYBACK_SUPPRESSION_REASON_NONE,
    PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS
  })
  @interface PlaybackSuppressionReason {}
  /** Playback is not suppressed. */
  int PLAYBACK_SUPPRESSION_REASON_NONE = 0;
  /** Playback is suppressed due to transient audio focus loss. */
  int PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS = 1;

  /**
   * Repeat modes for playback. One of {@link #REPEAT_MODE_OFF}, {@link #REPEAT_MODE_ONE} or {@link
   * #REPEAT_MODE_ALL}.
   */
  // @Target list includes both 'default' targets and TYPE_USE, to ensure backwards compatibility
  // with Kotlin usages from before TYPE_USE was added.
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target({FIELD, METHOD, PARAMETER, LOCAL_VARIABLE, TYPE_USE})
  @IntDef({REPEAT_MODE_OFF, REPEAT_MODE_ONE, REPEAT_MODE_ALL})
  @interface RepeatMode {}
  /**
   * Normal playback without repetition. "Previous" and "Next" actions move to the previous and next
   * {@link MediaItem} respectively, and do nothing when there is no previous or next {@link
   * MediaItem} to move to.
   */
  int REPEAT_MODE_OFF = 0;
  /**
   * Repeats the currently playing {@link MediaItem} infinitely during ongoing playback. "Previous"
   * and "Next" actions behave as they do in {@link #REPEAT_MODE_OFF}, moving to the previous and
   * next {@link MediaItem} respectively, and doing nothing when there is no previous or next {@link
   * MediaItem} to move to.
   */
  int REPEAT_MODE_ONE = 1;
  /**
   * Repeats the entire timeline infinitely. "Previous" and "Next" actions behave as they do in
   * {@link #REPEAT_MODE_OFF}, but with looping at the ends so that "Previous" when playing the
   * first {@link MediaItem} will move to the last {@link MediaItem}, and "Next" when playing the
   * last {@link MediaItem} will move to the first {@link MediaItem}.
   */
  int REPEAT_MODE_ALL = 2;

  /**
   * Reasons for position discontinuities. One of {@link #DISCONTINUITY_REASON_AUTO_TRANSITION},
   * {@link #DISCONTINUITY_REASON_SEEK}, {@link #DISCONTINUITY_REASON_SEEK_ADJUSTMENT}, {@link
   * #DISCONTINUITY_REASON_SKIP}, {@link #DISCONTINUITY_REASON_REMOVE} or {@link
   * #DISCONTINUITY_REASON_INTERNAL}.
   */
  // @Target list includes both 'default' targets and TYPE_USE, to ensure backwards compatibility
  // with Kotlin usages from before TYPE_USE was added.
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target({FIELD, METHOD, PARAMETER, LOCAL_VARIABLE, TYPE_USE})
  @IntDef({
    DISCONTINUITY_REASON_AUTO_TRANSITION,
    DISCONTINUITY_REASON_SEEK,
    DISCONTINUITY_REASON_SEEK_ADJUSTMENT,
    DISCONTINUITY_REASON_SKIP,
    DISCONTINUITY_REASON_REMOVE,
    DISCONTINUITY_REASON_INTERNAL
  })
  @interface DiscontinuityReason {}
  /**
   * Automatic playback transition from one period in the timeline to the next. The period index may
   * be the same as it was before the discontinuity in case the current period is repeated.
   *
   * <p>This reason also indicates an automatic transition from the content period to an inserted ad
   * period or vice versa. Or a transition caused by another player (e.g. multiple controllers can
   * control the same playback on a remote device).
   */
  int DISCONTINUITY_REASON_AUTO_TRANSITION = 0;
  /** Seek within the current period or to another period. */
  int DISCONTINUITY_REASON_SEEK = 1;
  /**
   * Seek adjustment due to being unable to seek to the requested position or because the seek was
   * permitted to be inexact.
   */
  int DISCONTINUITY_REASON_SEEK_ADJUSTMENT = 2;
  /** Discontinuity introduced by a skipped period (for instance a skipped ad). */
  int DISCONTINUITY_REASON_SKIP = 3;
  /** Discontinuity caused by the removal of the current period from the {@link Timeline}. */
  int DISCONTINUITY_REASON_REMOVE = 4;
  /** Discontinuity introduced internally (e.g. by the source). */
  int DISCONTINUITY_REASON_INTERNAL = 5;

  /**
   * Reasons for timeline changes. One of {@link #TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED} or {@link
   * #TIMELINE_CHANGE_REASON_SOURCE_UPDATE}.
   */
  // @Target list includes both 'default' targets and TYPE_USE, to ensure backwards compatibility
  // with Kotlin usages from before TYPE_USE was added.
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target({FIELD, METHOD, PARAMETER, LOCAL_VARIABLE, TYPE_USE})
  @IntDef({TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED, TIMELINE_CHANGE_REASON_SOURCE_UPDATE})
  @interface TimelineChangeReason {}
  /** Timeline changed as a result of a change of the playlist items or the order of the items. */
  int TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED = 0;
  /**
   * Timeline changed as a result of a source update (e.g. result of a dynamic update by the played
   * media).
   *
   * <p>This reason also indicates a change caused by another player (e.g. multiple controllers can
   * control the same playback on the remote device).
   */
  int TIMELINE_CHANGE_REASON_SOURCE_UPDATE = 1;

  /**
   * Reasons for media item transitions. One of {@link #MEDIA_ITEM_TRANSITION_REASON_REPEAT}, {@link
   * #MEDIA_ITEM_TRANSITION_REASON_AUTO}, {@link #MEDIA_ITEM_TRANSITION_REASON_SEEK} or {@link
   * #MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED}.
   */
  // @Target list includes both 'default' targets and TYPE_USE, to ensure backwards compatibility
  // with Kotlin usages from before TYPE_USE was added.
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target({FIELD, METHOD, PARAMETER, LOCAL_VARIABLE, TYPE_USE})
  @IntDef({
    MEDIA_ITEM_TRANSITION_REASON_REPEAT,
    MEDIA_ITEM_TRANSITION_REASON_AUTO,
    MEDIA_ITEM_TRANSITION_REASON_SEEK,
    MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED
  })
  @interface MediaItemTransitionReason {}
  /** The media item has been repeated. */
  int MEDIA_ITEM_TRANSITION_REASON_REPEAT = 0;
  /**
   * Playback has automatically transitioned to the next media item.
   *
   * <p>This reason also indicates a transition caused by another player (e.g. multiple controllers
   * can control the same playback on a remote device).
   */
  int MEDIA_ITEM_TRANSITION_REASON_AUTO = 1;
  /** A seek to another media item has occurred. */
  int MEDIA_ITEM_TRANSITION_REASON_SEEK = 2;
  /**
   * The current media item has changed because of a change in the playlist. This can either be if
   * the media item previously being played has been removed, or when the playlist becomes non-empty
   * after being empty.
   */
  int MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED = 3;

  /**
   * Events that can be reported via {@link Listener#onEvents(Player, Events)}.
   *
   * <p>One of the {@link Player}{@code .EVENT_*} values.
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
    EVENT_AUDIO_ATTRIBUTES_CHANGED,
    EVENT_AUDIO_SESSION_ID,
    EVENT_VOLUME_CHANGED,
    EVENT_SKIP_SILENCE_ENABLED_CHANGED,
    EVENT_SURFACE_SIZE_CHANGED,
    EVENT_VIDEO_SIZE_CHANGED,
    EVENT_RENDERED_FIRST_FRAME,
    EVENT_CUES,
    EVENT_METADATA,
    EVENT_DEVICE_INFO_CHANGED,
    EVENT_DEVICE_VOLUME_CHANGED
  })
  @interface Event {}
  /** {@link #getCurrentTimeline()} changed. */
  int EVENT_TIMELINE_CHANGED = 0;
  /** {@link #getCurrentMediaItem()} changed or the player started repeating the current item. */
  int EVENT_MEDIA_ITEM_TRANSITION = 1;
  /** {@link #getCurrentTracks()} changed. */
  int EVENT_TRACKS_CHANGED = 2;
  /** {@link #isLoading()} ()} changed. */
  int EVENT_IS_LOADING_CHANGED = 3;
  /** {@link #getPlaybackState()} changed. */
  int EVENT_PLAYBACK_STATE_CHANGED = 4;
  /** {@link #getPlayWhenReady()} changed. */
  int EVENT_PLAY_WHEN_READY_CHANGED = 5;
  /** {@link #getPlaybackSuppressionReason()} changed. */
  int EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED = 6;
  /** {@link #isPlaying()} changed. */
  int EVENT_IS_PLAYING_CHANGED = 7;
  /** {@link #getRepeatMode()} changed. */
  int EVENT_REPEAT_MODE_CHANGED = 8;
  /** {@link #getShuffleModeEnabled()} changed. */
  int EVENT_SHUFFLE_MODE_ENABLED_CHANGED = 9;
  /** {@link #getPlayerError()} changed. */
  int EVENT_PLAYER_ERROR = 10;
  /**
   * A position discontinuity occurred. See {@link Listener#onPositionDiscontinuity(PositionInfo,
   * PositionInfo, int)}.
   */
  int EVENT_POSITION_DISCONTINUITY = 11;
  /** {@link #getPlaybackParameters()} changed. */
  int EVENT_PLAYBACK_PARAMETERS_CHANGED = 12;
  /** {@link #isCommandAvailable(int)} changed for at least one {@link Command}. */
  int EVENT_AVAILABLE_COMMANDS_CHANGED = 13;
  /** {@link #getMediaMetadata()} changed. */
  int EVENT_MEDIA_METADATA_CHANGED = 14;
  /** {@link #getPlaylistMetadata()} changed. */
  int EVENT_PLAYLIST_METADATA_CHANGED = 15;
  /** {@link #getSeekBackIncrement()} changed. */
  int EVENT_SEEK_BACK_INCREMENT_CHANGED = 16;
  /** {@link #getSeekForwardIncrement()} changed. */
  int EVENT_SEEK_FORWARD_INCREMENT_CHANGED = 17;
  /** {@link #getMaxSeekToPreviousPosition()} changed. */
  int EVENT_MAX_SEEK_TO_PREVIOUS_POSITION_CHANGED = 18;
  /** {@link #getTrackSelectionParameters()} changed. */
  int EVENT_TRACK_SELECTION_PARAMETERS_CHANGED = 19;
  /** {@link #getAudioAttributes()} changed. */
  int EVENT_AUDIO_ATTRIBUTES_CHANGED = 20;
  /** The audio session id was set. */
  int EVENT_AUDIO_SESSION_ID = 21;
  /** {@link #getVolume()} changed. */
  int EVENT_VOLUME_CHANGED = 22;
  /** Skipping silences in the audio stream is enabled or disabled. */
  int EVENT_SKIP_SILENCE_ENABLED_CHANGED = 23;
  /** The size of the surface onto which the video is being rendered changed. */
  int EVENT_SURFACE_SIZE_CHANGED = 24;
  /** {@link #getVideoSize()} changed. */
  int EVENT_VIDEO_SIZE_CHANGED = 25;
  /**
   * A frame is rendered for the first time since setting the surface, or since the renderer was
   * reset, or since the stream being rendered was changed.
   */
  int EVENT_RENDERED_FIRST_FRAME = 26;
  /** {@link #getCurrentCues()} changed. */
  int EVENT_CUES = 27;
  /** Metadata associated with the current playback time changed. */
  int EVENT_METADATA = 28;
  /** {@link #getDeviceInfo()} changed. */
  int EVENT_DEVICE_INFO_CHANGED = 29;
  /** {@link #getDeviceVolume()} changed. */
  int EVENT_DEVICE_VOLUME_CHANGED = 30;

  /**
   * Commands that indicate which method calls are currently permitted on a particular {@code
   * Player} instance.
   *
   * <p>The currently available commands can be inspected with {@link #getAvailableCommands()} and
   * {@link #isCommandAvailable(int)}.
   *
   * <p>See the documentation of each command constant for the details of which methods it permits
   * calling.
   *
   * <p>One of the following values:
   *
   * <ul>
   *   <li>{@link #COMMAND_PLAY_PAUSE}
   *   <li>{@link #COMMAND_PREPARE}
   *   <li>{@link #COMMAND_STOP}
   *   <li>{@link #COMMAND_SEEK_TO_DEFAULT_POSITION}
   *   <li>{@link #COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM}
   *   <li>{@link #COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM}
   *   <li>{@link #COMMAND_SEEK_TO_PREVIOUS}
   *   <li>{@link #COMMAND_SEEK_TO_NEXT_MEDIA_ITEM}
   *   <li>{@link #COMMAND_SEEK_TO_NEXT}
   *   <li>{@link #COMMAND_SEEK_TO_MEDIA_ITEM}
   *   <li>{@link #COMMAND_SEEK_BACK}
   *   <li>{@link #COMMAND_SEEK_FORWARD}
   *   <li>{@link #COMMAND_SET_SPEED_AND_PITCH}
   *   <li>{@link #COMMAND_SET_SHUFFLE_MODE}
   *   <li>{@link #COMMAND_SET_REPEAT_MODE}
   *   <li>{@link #COMMAND_GET_CURRENT_MEDIA_ITEM}
   *   <li>{@link #COMMAND_GET_TIMELINE}
   *   <li>{@link #COMMAND_GET_MEDIA_ITEMS_METADATA}
   *   <li>{@link #COMMAND_SET_MEDIA_ITEMS_METADATA}
   *   <li>{@link #COMMAND_SET_MEDIA_ITEM}
   *   <li>{@link #COMMAND_CHANGE_MEDIA_ITEMS}
   *   <li>{@link #COMMAND_GET_AUDIO_ATTRIBUTES}
   *   <li>{@link #COMMAND_GET_VOLUME}
   *   <li>{@link #COMMAND_GET_DEVICE_VOLUME}
   *   <li>{@link #COMMAND_SET_VOLUME}
   *   <li>{@link #COMMAND_SET_DEVICE_VOLUME}
   *   <li>{@link #COMMAND_ADJUST_DEVICE_VOLUME}
   *   <li>{@link #COMMAND_SET_VIDEO_SURFACE}
   *   <li>{@link #COMMAND_GET_TEXT}
   *   <li>{@link #COMMAND_SET_TRACK_SELECTION_PARAMETERS}
   *   <li>{@link #COMMAND_GET_TRACKS}
   * </ul>
   */
  // @Target list includes both 'default' targets and TYPE_USE, to ensure backwards compatibility
  // with Kotlin usages from before TYPE_USE was added.
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target({FIELD, METHOD, PARAMETER, LOCAL_VARIABLE, TYPE_USE})
  @IntDef({
    COMMAND_INVALID,
    COMMAND_PLAY_PAUSE,
    COMMAND_PREPARE,
    COMMAND_STOP,
    COMMAND_SEEK_TO_DEFAULT_POSITION,
    COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
    COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
    COMMAND_SEEK_TO_PREVIOUS,
    COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
    COMMAND_SEEK_TO_NEXT,
    COMMAND_SEEK_TO_MEDIA_ITEM,
    COMMAND_SEEK_BACK,
    COMMAND_SEEK_FORWARD,
    COMMAND_SET_SPEED_AND_PITCH,
    COMMAND_SET_SHUFFLE_MODE,
    COMMAND_SET_REPEAT_MODE,
    COMMAND_GET_CURRENT_MEDIA_ITEM,
    COMMAND_GET_TIMELINE,
    COMMAND_GET_MEDIA_ITEMS_METADATA,
    COMMAND_SET_MEDIA_ITEMS_METADATA,
    COMMAND_SET_MEDIA_ITEM,
    COMMAND_CHANGE_MEDIA_ITEMS,
    COMMAND_GET_AUDIO_ATTRIBUTES,
    COMMAND_GET_VOLUME,
    COMMAND_GET_DEVICE_VOLUME,
    COMMAND_SET_VOLUME,
    COMMAND_SET_DEVICE_VOLUME,
    COMMAND_ADJUST_DEVICE_VOLUME,
    COMMAND_SET_VIDEO_SURFACE,
    COMMAND_GET_TEXT,
    COMMAND_SET_TRACK_SELECTION_PARAMETERS,
    COMMAND_GET_TRACKS,
  })
  @interface Command {}
  /**
   * Command to start, pause or resume playback.
   *
   * <p>The following methods must only be called if this command is {@linkplain
   * #isCommandAvailable(int) available}:
   *
   * <ul>
   *   <li>{@link #play()}
   *   <li>{@link #pause()}
   *   <li>{@link #setPlayWhenReady(boolean)}
   * </ul>
   */
  int COMMAND_PLAY_PAUSE = 1;

  /**
   * Command to prepare the player.
   *
   * <p>The {@link #prepare()} method must only be called if this command is {@linkplain
   * #isCommandAvailable(int) available}.
   */
  int COMMAND_PREPARE = 2;

  /**
   * Command to stop playback.
   *
   * <p>The {@link #stop()} method must only be called if this command is {@linkplain
   * #isCommandAvailable(int) available}.
   */
  int COMMAND_STOP = 3;

  /**
   * Command to seek to the default position of the current {@link MediaItem}.
   *
   * <p>The {@link #seekToDefaultPosition()} method must only be called if this command is
   * {@linkplain #isCommandAvailable(int) available}.
   */
  int COMMAND_SEEK_TO_DEFAULT_POSITION = 4;

  /**
   * Command to seek anywhere inside the current {@link MediaItem}.
   *
   * <p>The {@link #seekTo(long)} method must only be called if this command is {@linkplain
   * #isCommandAvailable(int) available}.
   */
  int COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM = 5;
  /**
   * @deprecated Use {@link #COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM} instead.
   */
  @Deprecated int COMMAND_SEEK_IN_CURRENT_WINDOW = COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM;

  /**
   * Command to seek to the default position of the previous {@link MediaItem}.
   *
   * <p>The {@link #seekToPreviousMediaItem()} method must only be called if this command is
   * {@linkplain #isCommandAvailable(int) available}.
   */
  int COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM = 6;
  /**
   * @deprecated Use {@link #COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM} instead.
   */
  @Deprecated int COMMAND_SEEK_TO_PREVIOUS_WINDOW = COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM;
  /**
   * Command to seek to an earlier position in the current {@link MediaItem} or the default position
   * of the previous {@link MediaItem}.
   *
   * <p>The {@link #seekToPrevious()} method must only be called if this command is {@linkplain
   * #isCommandAvailable(int) available}.
   */
  int COMMAND_SEEK_TO_PREVIOUS = 7;
  /**
   * Command to seek to the default position of the next {@link MediaItem}.
   *
   * <p>The {@link #seekToNextMediaItem()} method must only be called if this command is {@linkplain
   * #isCommandAvailable(int) available}.
   */
  int COMMAND_SEEK_TO_NEXT_MEDIA_ITEM = 8;
  /**
   * @deprecated Use {@link #COMMAND_SEEK_TO_NEXT_MEDIA_ITEM} instead.
   */
  @Deprecated int COMMAND_SEEK_TO_NEXT_WINDOW = COMMAND_SEEK_TO_NEXT_MEDIA_ITEM;
  /**
   * Command to seek to a later position in the current {@link MediaItem} or the default position of
   * the next {@link MediaItem}.
   *
   * <p>The {@link #seekToNext()} method must only be called if this command is {@linkplain
   * #isCommandAvailable(int) available}.
   */
  int COMMAND_SEEK_TO_NEXT = 9;

  /**
   * Command to seek anywhere in any {@link MediaItem}.
   *
   * <p>The following methods must only be called if this command is {@linkplain
   * #isCommandAvailable(int) available}:
   *
   * <ul>
   *   <li>{@link #seekTo(int, long)}
   *   <li>{@link #seekToDefaultPosition(int)}
   * </ul>
   */
  int COMMAND_SEEK_TO_MEDIA_ITEM = 10;
  /**
   * @deprecated Use {@link #COMMAND_SEEK_TO_MEDIA_ITEM} instead.
   */
  @Deprecated int COMMAND_SEEK_TO_WINDOW = COMMAND_SEEK_TO_MEDIA_ITEM;
  /**
   * Command to seek back by a fixed increment inside the current {@link MediaItem}.
   *
   * <p>The {@link #seekBack()} method must only be called if this command is {@linkplain
   * #isCommandAvailable(int) available}.
   */
  int COMMAND_SEEK_BACK = 11;
  /**
   * Command to seek forward by a fixed increment inside the current {@link MediaItem}.
   *
   * <p>The {@link #seekForward()} method must only be called if this command is {@linkplain
   * #isCommandAvailable(int) available}.
   */
  int COMMAND_SEEK_FORWARD = 12;

  /**
   * Command to set the playback speed and pitch.
   *
   * <p>The following methods must only be called if this command is {@linkplain
   * #isCommandAvailable(int) available}:
   *
   * <ul>
   *   <li>{@link #setPlaybackParameters(PlaybackParameters)}
   *   <li>{@link #setPlaybackSpeed(float)}
   * </ul>
   */
  int COMMAND_SET_SPEED_AND_PITCH = 13;

  /**
   * Command to enable shuffling.
   *
   * <p>The {@link #setShuffleModeEnabled(boolean)} method must only be called if this command is
   * {@linkplain #isCommandAvailable(int) available}.
   */
  int COMMAND_SET_SHUFFLE_MODE = 14;

  /**
   * Command to set the repeat mode.
   *
   * <p>The {@link #setRepeatMode(int)} method must only be called if this command is {@linkplain
   * #isCommandAvailable(int) available}.
   */
  int COMMAND_SET_REPEAT_MODE = 15;

  /**
   * Command to get information about the currently playing {@link MediaItem}.
   *
   * <p>The following methods must only be called if this command is {@linkplain
   * #isCommandAvailable(int) available}:
   *
   * <ul>
   *   <li>{@link #getCurrentMediaItem()}
   *   <li>{@link #isCurrentMediaItemDynamic()}
   *   <li>{@link #isCurrentMediaItemLive()}
   *   <li>{@link #isCurrentMediaItemSeekable()}
   *   <li>{@link #getCurrentLiveOffset()}
   *   <li>{@link #getDuration()}
   *   <li>{@link #getCurrentPosition()}
   *   <li>{@link #getBufferedPosition()}
   *   <li>{@link #getContentDuration()}
   *   <li>{@link #getContentPosition()}
   *   <li>{@link #getContentBufferedPosition()}
   *   <li>{@link #getTotalBufferedDuration()}
   *   <li>{@link #isPlayingAd()}
   *   <li>{@link #getCurrentAdGroupIndex()}
   *   <li>{@link #getCurrentAdIndexInAdGroup()}
   * </ul>
   */
  int COMMAND_GET_CURRENT_MEDIA_ITEM = 16;

  /**
   * Command to get the information about the current timeline.
   *
   * <p>The following methods must only be called if this command is {@linkplain
   * #isCommandAvailable(int) available}:
   *
   * <ul>
   *   <li>{@link #getCurrentTimeline()}
   *   <li>{@link #getCurrentMediaItemIndex()}
   *   <li>{@link #getCurrentPeriodIndex()}
   *   <li>{@link #getMediaItemCount()}
   *   <li>{@link #getMediaItemAt(int)}
   *   <li>{@link #getNextMediaItemIndex()}
   *   <li>{@link #getPreviousMediaItemIndex()}
   *   <li>{@link #hasPreviousMediaItem()}
   *   <li>{@link #hasNextMediaItem()}
   * </ul>
   */
  int COMMAND_GET_TIMELINE = 17;

  /**
   * Command to get metadata related to the playlist and current {@link MediaItem}.
   *
   * <p>The following methods must only be called if this command is {@linkplain
   * #isCommandAvailable(int) available}:
   *
   * <ul>
   *   <li>{@link #getMediaMetadata()}
   *   <li>{@link #getPlaylistMetadata()}
   * </ul>
   */
  // TODO(b/263132691): Rename this to COMMAND_GET_METADATA
  int COMMAND_GET_MEDIA_ITEMS_METADATA = 18;

  /**
   * Command to set the playlist metadata.
   *
   * <p>The {@link #setPlaylistMetadata(MediaMetadata)} method must only be called if this command
   * is {@linkplain #isCommandAvailable(int) available}.
   */
  // TODO(b/263132691): Rename this to COMMAND_SET_PLAYLIST_METADATA
  int COMMAND_SET_MEDIA_ITEMS_METADATA = 19;

  /**
   * Command to set a {@link MediaItem}.
   *
   * <p>The following methods must only be called if this command is {@linkplain
   * #isCommandAvailable(int) available}:
   *
   * <ul>
   *   <li>{@link #setMediaItem(MediaItem)}
   *   <li>{@link #setMediaItem(MediaItem, boolean)}
   *   <li>{@link #setMediaItem(MediaItem, long)}
   * </ul>
   */
  int COMMAND_SET_MEDIA_ITEM = 31;
  /**
   * Command to change the {@linkplain MediaItem media items} in the playlist.
   *
   * <p>The following methods must only be called if this command is {@linkplain
   * #isCommandAvailable(int) available}:
   *
   * <ul>
   *   <li>{@link #addMediaItem(MediaItem)}
   *   <li>{@link #addMediaItem(int, MediaItem)}
   *   <li>{@link #addMediaItems(List)}
   *   <li>{@link #addMediaItems(int, List)}
   *   <li>{@link #clearMediaItems()}
   *   <li>{@link #moveMediaItem(int, int)}
   *   <li>{@link #moveMediaItems(int, int, int)}
   *   <li>{@link #removeMediaItem(int)}
   *   <li>{@link #removeMediaItems(int, int)}
   *   <li>{@link #setMediaItems(List)}
   *   <li>{@link #setMediaItems(List, boolean)}
   *   <li>{@link #setMediaItems(List, int, long)}
   * </ul>
   */
  int COMMAND_CHANGE_MEDIA_ITEMS = 20;

  /**
   * Command to get the player current {@link AudioAttributes}.
   *
   * <p>The {@link #getAudioAttributes()} method must only be called if this command is {@linkplain
   * #isCommandAvailable(int) available}.
   */
  int COMMAND_GET_AUDIO_ATTRIBUTES = 21;

  /**
   * Command to get the player volume.
   *
   * <p>The {@link #getVolume()} method must only be called if this command is {@linkplain
   * #isCommandAvailable(int) available}.
   */
  int COMMAND_GET_VOLUME = 22;

  /**
   * Command to get the device volume and whether it is muted.
   *
   * <p>The following methods must only be called if this command is {@linkplain
   * #isCommandAvailable(int) available}:
   *
   * <ul>
   *   <li>{@link #getDeviceVolume()}
   *   <li>{@link #isDeviceMuted()}
   * </ul>
   */
  int COMMAND_GET_DEVICE_VOLUME = 23;

  /**
   * Command to set the player volume.
   *
   * <p>The {@link #setVolume(float)} method must only be called if this command is {@linkplain
   * #isCommandAvailable(int) available}.
   */
  int COMMAND_SET_VOLUME = 24;
  /**
   * Command to set the device volume.
   *
   * <p>The {@link #setDeviceVolume(int)} method must only be called if this command is {@linkplain
   * #isCommandAvailable(int) available}.
   */
  int COMMAND_SET_DEVICE_VOLUME = 25;

  /**
   * Command to increase and decrease the device volume and mute it.
   *
   * <p>The following methods must only be called if this command is {@linkplain
   * #isCommandAvailable(int) available}:
   *
   * <ul>
   *   <li>{@link #increaseDeviceVolume()}
   *   <li>{@link #decreaseDeviceVolume()}
   *   <li>{@link #setDeviceMuted(boolean)}
   * </ul>
   */
  int COMMAND_ADJUST_DEVICE_VOLUME = 26;

  /**
   * Command to set and clear the surface on which to render the video.
   *
   * <p>The following methods must only be called if this command is {@linkplain
   * #isCommandAvailable(int) available}:
   *
   * <ul>
   *   <li>{@link #setVideoSurface(Surface)}
   *   <li>{@link #clearVideoSurface()}
   *   <li>{@link #clearVideoSurface(Surface)}
   *   <li>{@link #setVideoSurfaceHolder(SurfaceHolder)}
   *   <li>{@link #clearVideoSurfaceHolder(SurfaceHolder)}
   *   <li>{@link #setVideoSurfaceView(SurfaceView)}
   *   <li>{@link #clearVideoSurfaceView(SurfaceView)}
   * </ul>
   */
  int COMMAND_SET_VIDEO_SURFACE = 27;

  /**
   * Command to get the text that should currently be displayed by the player.
   *
   * <p>The {@link #getCurrentCues()} method must only be called if this command is {@linkplain
   * #isCommandAvailable(int) available}.
   */
  int COMMAND_GET_TEXT = 28;

  /**
   * Command to set the player's track selection parameters.
   *
   * <p>The {@link #setTrackSelectionParameters(TrackSelectionParameters)} method must only be
   * called if this command is {@linkplain #isCommandAvailable(int) available}.
   */
  int COMMAND_SET_TRACK_SELECTION_PARAMETERS = 29;

  /**
   * Command to get details of the current track selection.
   *
   * <p>The {@link #getCurrentTracks()} method must only be called if this command is {@linkplain
   * #isCommandAvailable(int) available}.
   */
  int COMMAND_GET_TRACKS = 30;

  /** Represents an invalid {@link Command}. */
  int COMMAND_INVALID = -1;

  /**
   * Returns the {@link Looper} associated with the application thread that's used to access the
   * player and on which player events are received.
   *
   * <p>This method can be called from any thread.
   */
  Looper getApplicationLooper();

  /**
   * Registers a listener to receive all events from the player.
   *
   * <p>The listener's methods will be called on the thread associated with {@link
   * #getApplicationLooper()}.
   *
   * <p>This method can be called from any thread.
   *
   * @param listener The listener to register.
   */
  void addListener(Listener listener);

  /**
   * Unregister a listener registered through {@link #addListener(Listener)}. The listener will no
   * longer receive events.
   *
   * @param listener The listener to unregister.
   */
  void removeListener(Listener listener);

  /**
   * Clears the playlist, adds the specified {@linkplain MediaItem media items} and resets the
   * position to the default position.
   *
   * <p>This method must only be called if {@link #COMMAND_CHANGE_MEDIA_ITEMS} is {@linkplain
   * #getAvailableCommands() available}.
   *
   * @param mediaItems The new {@linkplain MediaItem media items}.
   */
  void setMediaItems(List<MediaItem> mediaItems);

  /**
   * Clears the playlist and adds the specified {@linkplain MediaItem media items}.
   *
   * <p>This method must only be called if {@link #COMMAND_CHANGE_MEDIA_ITEMS} is {@linkplain
   * #getAvailableCommands() available}.
   *
   * @param mediaItems The new {@linkplain MediaItem media items}.
   * @param resetPosition Whether the playback position should be reset to the default position in
   *     the first {@link Timeline.Window}. If false, playback will start from the position defined
   *     by {@link #getCurrentMediaItemIndex()} and {@link #getCurrentPosition()}.
   */
  void setMediaItems(List<MediaItem> mediaItems, boolean resetPosition);

  /**
   * Clears the playlist and adds the specified {@linkplain MediaItem media items}.
   *
   * <p>This method must only be called if {@link #COMMAND_CHANGE_MEDIA_ITEMS} is {@linkplain
   * #getAvailableCommands() available}.
   *
   * @param mediaItems The new {@linkplain MediaItem media items}.
   * @param startIndex The {@link MediaItem} index to start playback from. If {@link C#INDEX_UNSET}
   *     is passed, the current position is not reset.
   * @param startPositionMs The position in milliseconds to start playback from. If {@link
   *     C#TIME_UNSET} is passed, the default position of the given {@link MediaItem} is used. In
   *     any case, if {@code startIndex} is set to {@link C#INDEX_UNSET}, this parameter is ignored
   *     and the position is not reset at all.
   * @throws IllegalSeekPositionException If the provided {@code startIndex} is not within the
   *     bounds of the list of media items.
   */
  void setMediaItems(List<MediaItem> mediaItems, int startIndex, long startPositionMs);

  /**
   * Clears the playlist, adds the specified {@link MediaItem} and resets the position to the
   * default position.
   *
   * <p>This method must only be called if {@link #COMMAND_SET_MEDIA_ITEM} is {@linkplain
   * #getAvailableCommands() available}.
   *
   * @param mediaItem The new {@link MediaItem}.
   */
  void setMediaItem(MediaItem mediaItem);

  /**
   * Clears the playlist and adds the specified {@link MediaItem}.
   *
   * <p>This method must only be called if {@link #COMMAND_SET_MEDIA_ITEM} is {@linkplain
   * #getAvailableCommands() available}.
   *
   * @param mediaItem The new {@link MediaItem}.
   * @param startPositionMs The position in milliseconds to start playback from.
   */
  void setMediaItem(MediaItem mediaItem, long startPositionMs);

  /**
   * Clears the playlist and adds the specified {@link MediaItem}.
   *
   * <p>This method must only be called if {@link #COMMAND_SET_MEDIA_ITEM} is {@linkplain
   * #getAvailableCommands() available}.
   *
   * @param mediaItem The new {@link MediaItem}.
   * @param resetPosition Whether the playback position should be reset to the default position. If
   *     false, playback will start from the position defined by {@link #getCurrentMediaItemIndex()}
   *     and {@link #getCurrentPosition()}.
   */
  void setMediaItem(MediaItem mediaItem, boolean resetPosition);

  /**
   * Adds a media item to the end of the playlist.
   *
   * <p>This method must only be called if {@link #COMMAND_CHANGE_MEDIA_ITEMS} is {@linkplain
   * #getAvailableCommands() available}.
   *
   * @param mediaItem The {@link MediaItem} to add.
   */
  void addMediaItem(MediaItem mediaItem);

  /**
   * Adds a media item at the given index of the playlist.
   *
   * <p>This method must only be called if {@link #COMMAND_CHANGE_MEDIA_ITEMS} is {@linkplain
   * #getAvailableCommands() available}.
   *
   * @param index The index at which to add the media item. If the index is larger than the size of
   *     the playlist, the media item is added to the end of the playlist.
   * @param mediaItem The {@link MediaItem} to add.
   */
  void addMediaItem(int index, MediaItem mediaItem);

  /**
   * Adds a list of media items to the end of the playlist.
   *
   * <p>This method must only be called if {@link #COMMAND_CHANGE_MEDIA_ITEMS} is {@linkplain
   * #getAvailableCommands() available}.
   *
   * @param mediaItems The {@linkplain MediaItem media items} to add.
   */
  void addMediaItems(List<MediaItem> mediaItems);

  /**
   * Adds a list of media items at the given index of the playlist.
   *
   * <p>This method must only be called if {@link #COMMAND_CHANGE_MEDIA_ITEMS} is {@linkplain
   * #getAvailableCommands() available}.
   *
   * @param index The index at which to add the media items. If the index is larger than the size of
   *     the playlist, the media items are added to the end of the playlist.
   * @param mediaItems The {@linkplain MediaItem media items} to add.
   */
  void addMediaItems(int index, List<MediaItem> mediaItems);

  /**
   * Moves the media item at the current index to the new index.
   *
   * <p>This method must only be called if {@link #COMMAND_CHANGE_MEDIA_ITEMS} is {@linkplain
   * #getAvailableCommands() available}.
   *
   * @param currentIndex The current index of the media item to move. If the index is larger than
   *     the size of the playlist, the request is ignored.
   * @param newIndex The new index of the media item. If the new index is larger than the size of
   *     the playlist the item is moved to the end of the playlist.
   */
  void moveMediaItem(int currentIndex, int newIndex);

  /**
   * Moves the media item range to the new index.
   *
   * <p>This method must only be called if {@link #COMMAND_CHANGE_MEDIA_ITEMS} is {@linkplain
   * #getAvailableCommands() available}.
   *
   * @param fromIndex The start of the range to move. If the index is larger than the size of the
   *     playlist, the request is ignored.
   * @param toIndex The first item not to be included in the range (exclusive). If the index is
   *     larger than the size of the playlist, items up to the end of the playlist are moved.
   * @param newIndex The new index of the first media item of the range. If the new index is larger
   *     than the size of the remaining playlist after removing the range, the range is moved to the
   *     end of the playlist.
   */
  void moveMediaItems(int fromIndex, int toIndex, int newIndex);

  /**
   * Removes the media item at the given index of the playlist.
   *
   * <p>This method must only be called if {@link #COMMAND_CHANGE_MEDIA_ITEMS} is {@linkplain
   * #getAvailableCommands() available}.
   *
   * @param index The index at which to remove the media item. If the index is larger than the size
   *     of the playlist, the request is ignored.
   */
  void removeMediaItem(int index);

  /**
   * Removes a range of media items from the playlist.
   *
   * <p>This method must only be called if {@link #COMMAND_CHANGE_MEDIA_ITEMS} is {@linkplain
   * #getAvailableCommands() available}.
   *
   * @param fromIndex The index at which to start removing media items. If the index is larger than
   *     the size of the playlist, the request is ignored.
   * @param toIndex The index of the first item to be kept (exclusive). If the index is larger than
   *     the size of the playlist, media items up to the end of the playlist are removed.
   */
  void removeMediaItems(int fromIndex, int toIndex);

  /**
   * Clears the playlist.
   *
   * <p>This method must only be called if {@link #COMMAND_CHANGE_MEDIA_ITEMS} is {@linkplain
   * #getAvailableCommands() available}.
   */
  void clearMediaItems();

  /**
   * Returns whether the provided {@link Command} is available.
   *
   * <p>This method does not execute the command.
   *
   * @param command A {@link Command}.
   * @return Whether the {@link Command} is available.
   * @see Listener#onAvailableCommandsChanged(Commands)
   */
  boolean isCommandAvailable(@Command int command);

  /** Returns whether the player can be used to advertise a media session. */
  boolean canAdvertiseSession();

  /**
   * Returns the player's currently available {@link Commands}.
   *
   * <p>The returned {@link Commands} are not updated when available commands change. Use {@link
   * Listener#onAvailableCommandsChanged(Commands)} to get an update when the available commands
   * change.
   *
   * @return The currently available {@link Commands}.
   * @see Listener#onAvailableCommandsChanged
   */
  Commands getAvailableCommands();

  /**
   * Prepares the player.
   *
   * <p>This method must only be called if {@link #COMMAND_PREPARE} is {@linkplain
   * #getAvailableCommands() available}.
   *
   * <p>This will move the player out of {@link #STATE_IDLE idle state} and the player will start
   * loading media and acquire resources needed for playback.
   */
  void prepare();

  /**
   * Returns the current {@linkplain State playback state} of the player.
   *
   * @return The current {@linkplain State playback state}.
   * @see Listener#onPlaybackStateChanged(int)
   */
  @State
  int getPlaybackState();

  /**
   * Returns the reason why playback is suppressed even though {@link #getPlayWhenReady()} is {@code
   * true}, or {@link #PLAYBACK_SUPPRESSION_REASON_NONE} if playback is not suppressed.
   *
   * @return The current {@link PlaybackSuppressionReason}.
   * @see Listener#onPlaybackSuppressionReasonChanged(int)
   */
  @PlaybackSuppressionReason
  int getPlaybackSuppressionReason();

  /**
   * Returns whether the player is playing, i.e. {@link #getCurrentPosition()} is advancing.
   *
   * <p>If {@code false}, then at least one of the following is true:
   *
   * <ul>
   *   <li>The {@link #getPlaybackState() playback state} is not {@link #STATE_READY ready}.
   *   <li>There is no {@link #getPlayWhenReady() intention to play}.
   *   <li>Playback is {@link #getPlaybackSuppressionReason() suppressed for other reasons}.
   * </ul>
   *
   * @return Whether the player is playing.
   * @see Listener#onIsPlayingChanged(boolean)
   */
  boolean isPlaying();

  /**
   * Returns the error that caused playback to fail. This is the same error that will have been
   * reported via {@link Listener#onPlayerError(PlaybackException)} at the time of failure. It can
   * be queried using this method until the player is re-prepared.
   *
   * <p>Note that this method will always return {@code null} if {@link #getPlaybackState()} is not
   * {@link #STATE_IDLE}.
   *
   * @return The error, or {@code null}.
   * @see Listener#onPlayerError(PlaybackException)
   */
  @Nullable
  PlaybackException getPlayerError();

  /**
   * Resumes playback as soon as {@link #getPlaybackState()} == {@link #STATE_READY}. Equivalent to
   * {@link #setPlayWhenReady(boolean) setPlayWhenReady(true)}.
   *
   * <p>This method must only be called if {@link #COMMAND_PLAY_PAUSE} is {@linkplain
   * #getAvailableCommands() available}.
   */
  void play();

  /**
   * Pauses playback. Equivalent to {@link #setPlayWhenReady(boolean) setPlayWhenReady(false)}.
   *
   * <p>This method must only be called if {@link #COMMAND_PLAY_PAUSE} is {@linkplain
   * #getAvailableCommands() available}.
   */
  void pause();

  /**
   * Sets whether playback should proceed when {@link #getPlaybackState()} == {@link #STATE_READY}.
   *
   * <p>If the player is already in the ready state then this method pauses and resumes playback.
   *
   * <p>This method must only be called if {@link #COMMAND_PLAY_PAUSE} is {@linkplain
   * #getAvailableCommands() available}.
   *
   * @param playWhenReady Whether playback should proceed when ready.
   */
  void setPlayWhenReady(boolean playWhenReady);

  /**
   * Whether playback will proceed when {@link #getPlaybackState()} == {@link #STATE_READY}.
   *
   * @return Whether playback will proceed when ready.
   * @see Listener#onPlayWhenReadyChanged(boolean, int)
   */
  boolean getPlayWhenReady();

  /**
   * Sets the {@link RepeatMode} to be used for playback.
   *
   * <p>This method must only be called if {@link #COMMAND_SET_REPEAT_MODE} is {@linkplain
   * #getAvailableCommands() available}.
   *
   * @param repeatMode The repeat mode.
   */
  void setRepeatMode(@RepeatMode int repeatMode);

  /**
   * Returns the current {@link RepeatMode} used for playback.
   *
   * @return The current repeat mode.
   * @see Listener#onRepeatModeChanged(int)
   */
  @RepeatMode
  int getRepeatMode();

  /**
   * Sets whether shuffling of media items is enabled.
   *
   * <p>This method must only be called if {@link #COMMAND_SET_SHUFFLE_MODE} is {@linkplain
   * #getAvailableCommands() available}.
   *
   * @param shuffleModeEnabled Whether shuffling is enabled.
   */
  void setShuffleModeEnabled(boolean shuffleModeEnabled);

  /**
   * Returns whether shuffling of media items is enabled.
   *
   * @see Listener#onShuffleModeEnabledChanged(boolean)
   */
  boolean getShuffleModeEnabled();

  /**
   * Whether the player is currently loading the source.
   *
   * @return Whether the player is currently loading the source.
   * @see Listener#onIsLoadingChanged(boolean)
   */
  boolean isLoading();

  /**
   * Seeks to the default position associated with the current {@link MediaItem}. The position can
   * depend on the type of media being played. For live streams it will typically be the live edge.
   * For other streams it will typically be the start.
   *
   * <p>This method must only be called if {@link #COMMAND_SEEK_TO_DEFAULT_POSITION} is {@linkplain
   * #getAvailableCommands() available}.
   */
  void seekToDefaultPosition();

  /**
   * Seeks to the default position associated with the specified {@link MediaItem}. The position can
   * depend on the type of media being played. For live streams it will typically be the live edge.
   * For other streams it will typically be the start.
   *
   * <p>This method must only be called if {@link #COMMAND_SEEK_TO_MEDIA_ITEM} is {@linkplain
   * #getAvailableCommands() available}.
   *
   * @param mediaItemIndex The index of the {@link MediaItem} whose associated default position
   *     should be seeked to. If the index is larger than the size of the playlist, the request is
   *     ignored.
   */
  void seekToDefaultPosition(int mediaItemIndex);

  /**
   * Seeks to a position specified in milliseconds in the current {@link MediaItem}.
   *
   * <p>This method must only be called if {@link #COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM} is
   * {@linkplain #getAvailableCommands() available}.
   *
   * @param positionMs The seek position in the current {@link MediaItem}, or {@link C#TIME_UNSET}
   *     to seek to the media item's default position.
   */
  void seekTo(long positionMs);

  /**
   * Seeks to a position specified in milliseconds in the specified {@link MediaItem}.
   *
   * <p>This method must only be called if {@link #COMMAND_SEEK_TO_MEDIA_ITEM} is {@linkplain
   * #getAvailableCommands() available}.
   *
   * @param mediaItemIndex The index of the {@link MediaItem}. If the index is larger than the size
   *     of the playlist, the request is ignored.
   * @param positionMs The seek position in the specified {@link MediaItem}, or {@link C#TIME_UNSET}
   *     to seek to the media item's default position.
   */
  void seekTo(int mediaItemIndex, long positionMs);

  /**
   * Returns the {@link #seekBack()} increment.
   *
   * @return The seek back increment, in milliseconds.
   * @see Listener#onSeekBackIncrementChanged(long)
   */
  long getSeekBackIncrement();

  /**
   * Seeks back in the current {@link MediaItem} by {@link #getSeekBackIncrement()} milliseconds.
   *
   * <p>This method must only be called if {@link #COMMAND_SEEK_BACK} is {@linkplain
   * #getAvailableCommands() available}.
   */
  void seekBack();

  /**
   * Returns the {@link #seekForward()} increment.
   *
   * @return The seek forward increment, in milliseconds.
   * @see Listener#onSeekForwardIncrementChanged(long)
   */
  long getSeekForwardIncrement();

  /**
   * Seeks forward in the current {@link MediaItem} by {@link #getSeekForwardIncrement()}
   * milliseconds.
   *
   * <p>This method must only be called if {@link #COMMAND_SEEK_FORWARD} is {@linkplain
   * #getAvailableCommands() available}.
   */
  void seekForward();

  /**
   * @deprecated Use {@link #hasPreviousMediaItem()} instead.
   */
  @Deprecated
  boolean hasPrevious();

  /**
   * @deprecated Use {@link #hasPreviousMediaItem()} instead.
   */
  @Deprecated
  boolean hasPreviousWindow();

  /**
   * Returns whether a previous media item exists, which may depend on the current repeat mode and
   * whether shuffle mode is enabled.
   *
   * <p>Note: When the repeat mode is {@link #REPEAT_MODE_ONE}, this method behaves the same as when
   * the current repeat mode is {@link #REPEAT_MODE_OFF}. See {@link #REPEAT_MODE_ONE} for more
   * details.
   *
   * <p>This method must only be called if {@link #COMMAND_GET_TIMELINE} is {@linkplain
   * #getAvailableCommands() available}.
   */
  boolean hasPreviousMediaItem();

  /**
   * @deprecated Use {@link #seekToPreviousMediaItem()} instead.
   */
  @Deprecated
  void previous();

  /**
   * @deprecated Use {@link #seekToPreviousMediaItem()} instead.
   */
  @Deprecated
  void seekToPreviousWindow();

  /**
   * Seeks to the default position of the previous {@link MediaItem}, which may depend on the
   * current repeat mode and whether shuffle mode is enabled. Does nothing if {@link
   * #hasPreviousMediaItem()} is {@code false}.
   *
   * <p>Note: When the repeat mode is {@link #REPEAT_MODE_ONE}, this method behaves the same as when
   * the current repeat mode is {@link #REPEAT_MODE_OFF}. See {@link #REPEAT_MODE_ONE} for more
   * details.
   *
   * <p>This method must only be called if {@link #COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM} is
   * {@linkplain #getAvailableCommands() available}.
   */
  void seekToPreviousMediaItem();

  /**
   * Returns the maximum position for which {@link #seekToPrevious()} seeks to the previous {@link
   * MediaItem}, in milliseconds.
   *
   * @return The maximum seek to previous position, in milliseconds.
   * @see Listener#onMaxSeekToPreviousPositionChanged(long)
   */
  long getMaxSeekToPreviousPosition();

  /**
   * Seeks to an earlier position in the current or previous {@link MediaItem} (if available). More
   * precisely:
   *
   * <ul>
   *   <li>If the timeline is empty or seeking is not possible, does nothing.
   *   <li>Otherwise, if the current {@link MediaItem} is {@link #isCurrentMediaItemLive()} live}
   *       and {@link #isCurrentMediaItemSeekable() unseekable}, then:
   *       <ul>
   *         <li>If {@link #hasPreviousMediaItem() a previous media item exists}, seeks to the
   *             default position of the previous media item.
   *         <li>Otherwise, does nothing.
   *       </ul>
   *   <li>Otherwise, if {@link #hasPreviousMediaItem() a previous media item exists} and the {@link
   *       #getCurrentPosition() current position} is less than {@link
   *       #getMaxSeekToPreviousPosition()}, seeks to the default position of the previous {@link
   *       MediaItem}.
   *   <li>Otherwise, seeks to 0 in the current {@link MediaItem}.
   * </ul>
   *
   * <p>This method must only be called if {@link #COMMAND_SEEK_TO_PREVIOUS} is {@linkplain
   * #getAvailableCommands() available}.
   */
  void seekToPrevious();

  /**
   * @deprecated Use {@link #hasNextMediaItem()} instead.
   */
  @Deprecated
  boolean hasNext();

  /**
   * @deprecated Use {@link #hasNextMediaItem()} instead.
   */
  @Deprecated
  boolean hasNextWindow();

  /**
   * Returns whether a next {@link MediaItem} exists, which may depend on the current repeat mode
   * and whether shuffle mode is enabled.
   *
   * <p>Note: When the repeat mode is {@link #REPEAT_MODE_ONE}, this method behaves the same as when
   * the current repeat mode is {@link #REPEAT_MODE_OFF}. See {@link #REPEAT_MODE_ONE} for more
   * details.
   *
   * <p>This method must only be called if {@link #COMMAND_GET_TIMELINE} is {@linkplain
   * #getAvailableCommands() available}.
   */
  boolean hasNextMediaItem();

  /**
   * @deprecated Use {@link #seekToNextMediaItem()} instead.
   */
  @Deprecated
  void next();

  /**
   * @deprecated Use {@link #seekToNextMediaItem()} instead.
   */
  @Deprecated
  void seekToNextWindow();

  /**
   * Seeks to the default position of the next {@link MediaItem}, which may depend on the current
   * repeat mode and whether shuffle mode is enabled. Does nothing if {@link #hasNextMediaItem()} is
   * {@code false}.
   *
   * <p>Note: When the repeat mode is {@link #REPEAT_MODE_ONE}, this method behaves the same as when
   * the current repeat mode is {@link #REPEAT_MODE_OFF}. See {@link #REPEAT_MODE_ONE} for more
   * details.
   *
   * <p>This method must only be called if {@link #COMMAND_SEEK_TO_NEXT_MEDIA_ITEM} is {@linkplain
   * #getAvailableCommands() available}.
   */
  void seekToNextMediaItem();

  /**
   * Seeks to a later position in the current or next {@link MediaItem} (if available). More
   * precisely:
   *
   * <ul>
   *   <li>If the timeline is empty or seeking is not possible, does nothing.
   *   <li>Otherwise, if {@link #hasNextMediaItem() a next media item exists}, seeks to the default
   *       position of the next {@link MediaItem}.
   *   <li>Otherwise, if the current {@link MediaItem} is {@link #isCurrentMediaItemLive() live} and
   *       has not ended, seeks to the live edge of the current {@link MediaItem}.
   *   <li>Otherwise, does nothing.
   * </ul>
   *
   * <p>This method must only be called if {@link #COMMAND_SEEK_TO_NEXT} is {@linkplain
   * #getAvailableCommands() available}.
   */
  void seekToNext();

  /**
   * Attempts to set the playback parameters. Passing {@link PlaybackParameters#DEFAULT} resets the
   * player to the default, which means there is no speed or pitch adjustment.
   *
   * <p>Playback parameters changes may cause the player to buffer. {@link
   * Listener#onPlaybackParametersChanged(PlaybackParameters)} will be called whenever the currently
   * active playback parameters change.
   *
   * <p>This method must only be called if {@link #COMMAND_SET_SPEED_AND_PITCH} is {@linkplain
   * #getAvailableCommands() available}.
   *
   * @param playbackParameters The playback parameters.
   */
  void setPlaybackParameters(PlaybackParameters playbackParameters);

  /**
   * Changes the rate at which playback occurs. The pitch is not changed.
   *
   * <p>This is equivalent to {@code
   * setPlaybackParameters(getPlaybackParameters().withSpeed(speed))}.
   *
   * <p>This method must only be called if {@link #COMMAND_SET_SPEED_AND_PITCH} is {@linkplain
   * #getAvailableCommands() available}.
   *
   * @param speed The linear factor by which playback will be sped up. Must be higher than 0. 1 is
   *     normal speed, 2 is twice as fast, 0.5 is half normal speed.
   */
  void setPlaybackSpeed(@FloatRange(from = 0, fromInclusive = false) float speed);

  /**
   * Returns the currently active playback parameters.
   *
   * @see Listener#onPlaybackParametersChanged(PlaybackParameters)
   */
  PlaybackParameters getPlaybackParameters();

  /**
   * Stops playback without resetting the playlist. Use {@link #pause()} rather than this method if
   * the intention is to pause playback.
   *
   * <p>Calling this method will cause the playback state to transition to {@link #STATE_IDLE} and
   * the player will release the loaded media and resources required for playback. The player
   * instance can still be used by calling {@link #prepare()} again, and {@link #release()} must
   * still be called on the player if it's no longer required.
   *
   * <p>Calling this method does not clear the playlist, reset the playback position or the playback
   * error.
   *
   * <p>This method must only be called if {@link #COMMAND_STOP} is {@linkplain
   * #getAvailableCommands() available}.
   */
  void stop();

  /**
   * @deprecated Use {@link #stop()} and {@link #clearMediaItems()} (if {@code reset} is true) or
   *     just {@link #stop()} (if {@code reset} is false). Any player error will be cleared when
   *     {@link #prepare() re-preparing} the player.
   */
  @Deprecated
  void stop(boolean reset);

  /**
   * Releases the player. This method must be called when the player is no longer required. The
   * player must not be used after calling this method.
   */
  // TODO(b/261158047): Document that COMMAND_RELEASE must be available once it exists.
  void release();

  /**
   * Returns the current tracks.
   *
   * <p>This method must only be called if {@link #COMMAND_GET_TRACKS} is {@linkplain
   * #getAvailableCommands() available}.
   *
   * @see Listener#onTracksChanged(Tracks)
   */
  Tracks getCurrentTracks();

  /**
   * Returns the parameters constraining the track selection.
   *
   * @see Listener#onTrackSelectionParametersChanged}
   */
  TrackSelectionParameters getTrackSelectionParameters();

  /**
   * Sets the parameters constraining the track selection.
   *
   * <p>Unsupported parameters will be silently ignored.
   *
   * <p>Use {@link #getTrackSelectionParameters()} to retrieve the current parameters. For example,
   * the following snippet restricts video to SD whilst keep other track selection parameters
   * unchanged:
   *
   * <pre>{@code
   * player.setTrackSelectionParameters(
   *   player.getTrackSelectionParameters()
   *         .buildUpon()
   *         .setMaxVideoSizeSd()
   *         .build())
   * }</pre>
   *
   * <p>This method must only be called if {@link #COMMAND_SET_TRACK_SELECTION_PARAMETERS} is
   * {@linkplain #getAvailableCommands() available}.
   */
  void setTrackSelectionParameters(TrackSelectionParameters parameters);

  /**
   * Returns the current combined {@link MediaMetadata}, or {@link MediaMetadata#EMPTY} if not
   * supported.
   *
   * <p>This {@link MediaMetadata} is a combination of the {@link MediaItem#mediaMetadata MediaItem
   * metadata}, the static metadata in the media's {@link Format#metadata Format}, and any timed
   * metadata that has been parsed from the media and output via {@link
   * Listener#onMetadata(Metadata)}. If a field is populated in the {@link MediaItem#mediaMetadata},
   * it will be prioritised above the same field coming from static or timed metadata.
   *
   * <p>This method must only be called if {@link #COMMAND_GET_MEDIA_ITEMS_METADATA} is {@linkplain
   * #getAvailableCommands() available}.
   */
  MediaMetadata getMediaMetadata();

  /**
   * Returns the playlist {@link MediaMetadata}, as set by {@link
   * #setPlaylistMetadata(MediaMetadata)}, or {@link MediaMetadata#EMPTY} if not supported.
   *
   * <p>This method must only be called if {@link #COMMAND_GET_MEDIA_ITEMS_METADATA} is {@linkplain
   * #getAvailableCommands() available}.
   */
  MediaMetadata getPlaylistMetadata();

  /**
   * Sets the playlist {@link MediaMetadata}.
   *
   * <p>This method must only be called if {@link #COMMAND_SET_MEDIA_ITEMS_METADATA} is {@linkplain
   * #getAvailableCommands() available}.
   */
  void setPlaylistMetadata(MediaMetadata mediaMetadata);

  /**
   * Returns the current manifest. The type depends on the type of media being played. May be null.
   */
  @Nullable
  Object getCurrentManifest();

  /**
   * Returns the current {@link Timeline}. Never null, but may be empty.
   *
   * <p>This method must only be called if {@link #COMMAND_GET_TIMELINE} is {@linkplain
   * #getAvailableCommands() available}.
   *
   * @see Listener#onTimelineChanged(Timeline, int)
   */
  Timeline getCurrentTimeline();

  /**
   * Returns the index of the period currently being played.
   *
   * <p>This method must only be called if {@link #COMMAND_GET_TIMELINE} is {@linkplain
   * #getAvailableCommands() available}.
   */
  int getCurrentPeriodIndex();

  /**
   * @deprecated Use {@link #getCurrentMediaItemIndex()} instead.
   */
  @Deprecated
  int getCurrentWindowIndex();

  /**
   * Returns the index of the current {@link MediaItem} in the {@link #getCurrentTimeline()
   * timeline}, or the prospective index if the {@link #getCurrentTimeline() current timeline} is
   * empty.
   *
   * <p>This method must only be called if {@link #COMMAND_GET_TIMELINE} is {@linkplain
   * #getAvailableCommands() available}.
   */
  int getCurrentMediaItemIndex();

  /**
   * @deprecated Use {@link #getNextMediaItemIndex()} instead.
   */
  @Deprecated
  int getNextWindowIndex();

  /**
   * Returns the index of the {@link MediaItem} that will be played if {@link
   * #seekToNextMediaItem()} is called, which may depend on the current repeat mode and whether
   * shuffle mode is enabled. Returns {@link C#INDEX_UNSET} if {@link #hasNextMediaItem()} is {@code
   * false}.
   *
   * <p>Note: When the repeat mode is {@link #REPEAT_MODE_ONE}, this method behaves the same as when
   * the current repeat mode is {@link #REPEAT_MODE_OFF}. See {@link #REPEAT_MODE_ONE} for more
   * details.
   *
   * <p>This method must only be called if {@link #COMMAND_GET_TIMELINE} is {@linkplain
   * #getAvailableCommands() available}.
   */
  int getNextMediaItemIndex();

  /**
   * @deprecated Use {@link #getPreviousMediaItemIndex()} instead.
   */
  @Deprecated
  int getPreviousWindowIndex();

  /**
   * Returns the index of the {@link MediaItem} that will be played if {@link
   * #seekToPreviousMediaItem()} is called, which may depend on the current repeat mode and whether
   * shuffle mode is enabled. Returns {@link C#INDEX_UNSET} if {@link #hasPreviousMediaItem()} is
   * {@code false}.
   *
   * <p>Note: When the repeat mode is {@link #REPEAT_MODE_ONE}, this method behaves the same as when
   * the current repeat mode is {@link #REPEAT_MODE_OFF}. See {@link #REPEAT_MODE_ONE} for more
   * details.
   *
   * <p>This method must only be called if {@link #COMMAND_GET_TIMELINE} is {@linkplain
   * #getAvailableCommands() available}.
   */
  int getPreviousMediaItemIndex();

  /**
   * Returns the currently playing {@link MediaItem}. May be null if the timeline is empty.
   *
   * <p>This method must only be called if {@link #COMMAND_GET_CURRENT_MEDIA_ITEM} is {@linkplain
   * #getAvailableCommands() available}.
   *
   * @see Listener#onMediaItemTransition(MediaItem, int)
   */
  @Nullable
  MediaItem getCurrentMediaItem();

  /**
   * Returns the number of {@linkplain MediaItem media items} in the playlist.
   *
   * <p>This method must only be called if {@link #COMMAND_GET_TIMELINE} is {@linkplain
   * #getAvailableCommands() available}.
   */
  int getMediaItemCount();

  /**
   * Returns the {@link MediaItem} at the given index.
   *
   * <p>This method must only be called if {@link #COMMAND_GET_TIMELINE} is {@linkplain
   * #getAvailableCommands() available}.
   */
  MediaItem getMediaItemAt(int index);

  /**
   * Returns the duration of the current content or ad in milliseconds, or {@link C#TIME_UNSET} if
   * the duration is not known.
   *
   * <p>This method must only be called if {@link #COMMAND_GET_CURRENT_MEDIA_ITEM} is {@linkplain
   * #getAvailableCommands() available}.
   */
  long getDuration();

  /**
   * Returns the playback position in the current content or ad, in milliseconds, or the prospective
   * position in milliseconds if the {@link #getCurrentTimeline() current timeline} is empty.
   *
   * <p>This method must only be called if {@link #COMMAND_GET_CURRENT_MEDIA_ITEM} is {@linkplain
   * #getAvailableCommands() available}.
   */
  long getCurrentPosition();

  /**
   * Returns an estimate of the position in the current content or ad up to which data is buffered,
   * in milliseconds.
   *
   * <p>This method must only be called if {@link #COMMAND_GET_CURRENT_MEDIA_ITEM} is {@linkplain
   * #getAvailableCommands() available}.
   */
  long getBufferedPosition();

  /**
   * Returns an estimate of the percentage in the current content or ad up to which data is
   * buffered, or 0 if no estimate is available.
   */
  @IntRange(from = 0, to = 100)
  int getBufferedPercentage();

  /**
   * Returns an estimate of the total buffered duration from the current position, in milliseconds.
   * This includes pre-buffered data for subsequent ads and {@linkplain MediaItem media items}.
   *
   * <p>This method must only be called if {@link #COMMAND_GET_CURRENT_MEDIA_ITEM} is {@linkplain
   * #getAvailableCommands() available}.
   */
  long getTotalBufferedDuration();

  /**
   * @deprecated Use {@link #isCurrentMediaItemDynamic()} instead.
   */
  @Deprecated
  boolean isCurrentWindowDynamic();

  /**
   * Returns whether the current {@link MediaItem} is dynamic (may change when the {@link Timeline}
   * is updated), or {@code false} if the {@link Timeline} is empty.
   *
   * <p>This method must only be called if {@link #COMMAND_GET_CURRENT_MEDIA_ITEM} is {@linkplain
   * #getAvailableCommands() available}.
   *
   * @see Timeline.Window#isDynamic
   */
  boolean isCurrentMediaItemDynamic();

  /**
   * @deprecated Use {@link #isCurrentMediaItemLive()} instead.
   */
  @Deprecated
  boolean isCurrentWindowLive();

  /**
   * Returns whether the current {@link MediaItem} is live, or {@code false} if the {@link Timeline}
   * is empty.
   *
   * <p>This method must only be called if {@link #COMMAND_GET_CURRENT_MEDIA_ITEM} is {@linkplain
   * #getAvailableCommands() available}.
   *
   * @see Timeline.Window#isLive()
   */
  boolean isCurrentMediaItemLive();

  /**
   * Returns the offset of the current playback position from the live edge in milliseconds, or
   * {@link C#TIME_UNSET} if the current {@link MediaItem} {@link #isCurrentMediaItemLive()} isn't
   * live} or the offset is unknown.
   *
   * <p>The offset is calculated as {@code currentTime - playbackPosition}, so should usually be
   * positive.
   *
   * <p>Note that this offset may rely on an accurate local time, so this method may return an
   * incorrect value if the difference between system clock and server clock is unknown.
   *
   * <p>This method must only be called if {@link #COMMAND_GET_CURRENT_MEDIA_ITEM} is {@linkplain
   * #getAvailableCommands() available}.
   */
  long getCurrentLiveOffset();

  /**
   * @deprecated Use {@link #isCurrentMediaItemSeekable()} instead.
   */
  @Deprecated
  boolean isCurrentWindowSeekable();

  /**
   * Returns whether the current {@link MediaItem} is seekable, or {@code false} if the {@link
   * Timeline} is empty.
   *
   * <p>This method must only be called if {@link #COMMAND_GET_CURRENT_MEDIA_ITEM} is {@linkplain
   * #getAvailableCommands() available}.
   *
   * @see Timeline.Window#isSeekable
   */
  boolean isCurrentMediaItemSeekable();

  /**
   * Returns whether the player is currently playing an ad.
   *
   * <p>This method must only be called if {@link #COMMAND_GET_CURRENT_MEDIA_ITEM} is {@linkplain
   * #getAvailableCommands() available}.
   */
  boolean isPlayingAd();

  /**
   * If {@link #isPlayingAd()} returns true, returns the index of the ad group in the period
   * currently being played. Returns {@link C#INDEX_UNSET} otherwise.
   *
   * <p>This method must only be called if {@link #COMMAND_GET_CURRENT_MEDIA_ITEM} is {@linkplain
   * #getAvailableCommands() available}.
   */
  int getCurrentAdGroupIndex();

  /**
   * If {@link #isPlayingAd()} returns true, returns the index of the ad in its ad group. Returns
   * {@link C#INDEX_UNSET} otherwise.
   *
   * <p>This method must only be called if {@link #COMMAND_GET_CURRENT_MEDIA_ITEM} is {@linkplain
   * #getAvailableCommands() available}.
   */
  int getCurrentAdIndexInAdGroup();

  /**
   * If {@link #isPlayingAd()} returns {@code true}, returns the duration of the current content in
   * milliseconds, or {@link C#TIME_UNSET} if the duration is not known. If there is no ad playing,
   * the returned duration is the same as that returned by {@link #getDuration()}.
   *
   * <p>This method must only be called if {@link #COMMAND_GET_CURRENT_MEDIA_ITEM} is {@linkplain
   * #getAvailableCommands() available}.
   */
  long getContentDuration();

  /**
   * If {@link #isPlayingAd()} returns {@code true}, returns the content position that will be
   * played once all ads in the ad group have finished playing, in milliseconds. If there is no ad
   * playing, the returned position is the same as that returned by {@link #getCurrentPosition()}.
   *
   * <p>This method must only be called if {@link #COMMAND_GET_CURRENT_MEDIA_ITEM} is {@linkplain
   * #getAvailableCommands() available}.
   */
  long getContentPosition();

  /**
   * If {@link #isPlayingAd()} returns {@code true}, returns an estimate of the content position in
   * the current content up to which data is buffered, in milliseconds. If there is no ad playing,
   * the returned position is the same as that returned by {@link #getBufferedPosition()}.
   *
   * <p>This method must only be called if {@link #COMMAND_GET_CURRENT_MEDIA_ITEM} is {@linkplain
   * #getAvailableCommands() available}.
   */
  long getContentBufferedPosition();

  /**
   * Returns the attributes for audio playback.
   *
   * <p>This method must only be called if {@link #COMMAND_GET_AUDIO_ATTRIBUTES} is {@linkplain
   * #getAvailableCommands() available}.
   */
  AudioAttributes getAudioAttributes();

  /**
   * Sets the audio volume, valid values are between 0 (silence) and 1 (unity gain, signal
   * unchanged), inclusive.
   *
   * <p>This method must only be called if {@link #COMMAND_SET_VOLUME} is {@linkplain
   * #getAvailableCommands() available}.
   *
   * @param volume Linear output gain to apply to all audio channels.
   */
  void setVolume(@FloatRange(from = 0, to = 1.0) float volume);

  /**
   * Returns the audio volume, with 0 being silence and 1 being unity gain (signal unchanged).
   *
   * <p>This method must only be called if {@link #COMMAND_GET_VOLUME} is {@linkplain
   * #getAvailableCommands() available}.
   *
   * @return The linear gain applied to all audio channels.
   */
  @FloatRange(from = 0, to = 1.0)
  float getVolume();

  /**
   * Clears any {@link Surface}, {@link SurfaceHolder}, {@link SurfaceView} or {@link TextureView}
   * currently set on the player.
   *
   * <p>This method must only be called if {@link #COMMAND_SET_VIDEO_SURFACE} is {@linkplain
   * #getAvailableCommands() available}.
   */
  void clearVideoSurface();

  /**
   * Clears the {@link Surface} onto which video is being rendered if it matches the one passed.
   * Else does nothing.
   *
   * <p>This method must only be called if {@link #COMMAND_SET_VIDEO_SURFACE} is {@linkplain
   * #getAvailableCommands() available}.
   *
   * @param surface The surface to clear.
   */
  void clearVideoSurface(@Nullable Surface surface);

  /**
   * Sets the {@link Surface} onto which video will be rendered. The caller is responsible for
   * tracking the lifecycle of the surface, and must clear the surface by calling {@code
   * setVideoSurface(null)} if the surface is destroyed.
   *
   * <p>If the surface is held by a {@link SurfaceView}, {@link TextureView} or {@link
   * SurfaceHolder} then it's recommended to use {@link #setVideoSurfaceView(SurfaceView)}, {@link
   * #setVideoTextureView(TextureView)} or {@link #setVideoSurfaceHolder(SurfaceHolder)} rather than
   * this method, since passing the holder allows the player to track the lifecycle of the surface
   * automatically.
   *
   * <p>This method must only be called if {@link #COMMAND_SET_VIDEO_SURFACE} is {@linkplain
   * #getAvailableCommands() available}.
   *
   * @param surface The {@link Surface}.
   */
  void setVideoSurface(@Nullable Surface surface);

  /**
   * Sets the {@link SurfaceHolder} that holds the {@link Surface} onto which video will be
   * rendered. The player will track the lifecycle of the surface automatically.
   *
   * <p>The thread that calls the {@link SurfaceHolder.Callback} methods must be the thread
   * associated with {@link #getApplicationLooper()}.
   *
   * <p>This method must only be called if {@link #COMMAND_SET_VIDEO_SURFACE} is {@linkplain
   * #getAvailableCommands() available}.
   *
   * @param surfaceHolder The surface holder.
   */
  void setVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder);

  /**
   * Clears the {@link SurfaceHolder} that holds the {@link Surface} onto which video is being
   * rendered if it matches the one passed. Else does nothing.
   *
   * <p>This method must only be called if {@link #COMMAND_SET_VIDEO_SURFACE} is {@linkplain
   * #getAvailableCommands() available}.
   *
   * @param surfaceHolder The surface holder to clear.
   */
  void clearVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder);

  /**
   * Sets the {@link SurfaceView} onto which video will be rendered. The player will track the
   * lifecycle of the surface automatically.
   *
   * <p>The thread that calls the {@link SurfaceHolder.Callback} methods must be the thread
   * associated with {@link #getApplicationLooper()}.
   *
   * <p>This method must only be called if {@link #COMMAND_SET_VIDEO_SURFACE} is {@linkplain
   * #getAvailableCommands() available}.
   *
   * @param surfaceView The surface view.
   */
  void setVideoSurfaceView(@Nullable SurfaceView surfaceView);

  /**
   * Clears the {@link SurfaceView} onto which video is being rendered if it matches the one passed.
   * Else does nothing.
   *
   * <p>This method must only be called if {@link #COMMAND_SET_VIDEO_SURFACE} is {@linkplain
   * #getAvailableCommands() available}.
   *
   * @param surfaceView The texture view to clear.
   */
  void clearVideoSurfaceView(@Nullable SurfaceView surfaceView);

  /**
   * Sets the {@link TextureView} onto which video will be rendered. The player will track the
   * lifecycle of the surface automatically.
   *
   * <p>The thread that calls the {@link TextureView.SurfaceTextureListener} methods must be the
   * thread associated with {@link #getApplicationLooper()}.
   *
   * <p>This method must only be called if {@link #COMMAND_SET_VIDEO_SURFACE} is {@linkplain
   * #getAvailableCommands() available}.
   *
   * @param textureView The texture view.
   */
  void setVideoTextureView(@Nullable TextureView textureView);

  /**
   * Clears the {@link TextureView} onto which video is being rendered if it matches the one passed.
   * Else does nothing.
   *
   * <p>This method must only be called if {@link #COMMAND_SET_VIDEO_SURFACE} is {@linkplain
   * #getAvailableCommands() available}.
   *
   * @param textureView The texture view to clear.
   */
  void clearVideoTextureView(@Nullable TextureView textureView);

  /**
   * Gets the size of the video.
   *
   * <p>The video's width and height are {@code 0} if there is no video or its size has not been
   * determined yet.
   *
   * @see Listener#onVideoSizeChanged(VideoSize)
   */
  VideoSize getVideoSize();

  /**
   * Gets the size of the surface on which the video is rendered.
   *
   * @see Listener#onSurfaceSizeChanged(int, int)
   */
  Size getSurfaceSize();

  /**
   * Returns the current {@link CueGroup}.
   *
   * <p>This method must only be called if {@link #COMMAND_GET_TEXT} is {@linkplain
   * #getAvailableCommands() available}.
   */
  CueGroup getCurrentCues();

  /** Gets the device information. */
  DeviceInfo getDeviceInfo();

  /**
   * Gets the current volume of the device.
   *
   * <p>For devices with {@link DeviceInfo#PLAYBACK_TYPE_LOCAL local playback}, the volume returned
   * by this method varies according to the current {@link C.StreamType stream type}. The stream
   * type is determined by {@link AudioAttributes#usage} which can be converted to stream type with
   * {@link Util#getStreamTypeForAudioUsage(int)}.
   *
   * <p>For devices with {@link DeviceInfo#PLAYBACK_TYPE_REMOTE remote playback}, the volume of the
   * remote device is returned.
   *
   * <p>This method must only be called if {@link #COMMAND_GET_DEVICE_VOLUME} is {@linkplain
   * #getAvailableCommands() available}.
   */
  @IntRange(from = 0)
  int getDeviceVolume();

  /**
   * Gets whether the device is muted or not.
   *
   * <p>This method must only be called if {@link #COMMAND_GET_DEVICE_VOLUME} is {@linkplain
   * #getAvailableCommands() available}.
   */
  boolean isDeviceMuted();

  /**
   * Sets the volume of the device.
   *
   * <p>This method must only be called if {@link #COMMAND_SET_DEVICE_VOLUME} is {@linkplain
   * #getAvailableCommands() available}.
   *
   * @param volume The volume to set.
   */
  void setDeviceVolume(@IntRange(from = 0) int volume);

  /**
   * Increases the volume of the device.
   *
   * <p>This method must only be called if {@link #COMMAND_ADJUST_DEVICE_VOLUME} is {@linkplain
   * #getAvailableCommands() available}.
   */
  void increaseDeviceVolume();

  /**
   * Decreases the volume of the device.
   *
   * <p>This method must only be called if {@link #COMMAND_ADJUST_DEVICE_VOLUME} is {@linkplain
   * #getAvailableCommands() available}.
   */
  void decreaseDeviceVolume();

  /**
   * Sets the mute state of the device.
   *
   * <p>This method must only be called if {@link #COMMAND_ADJUST_DEVICE_VOLUME} is {@linkplain
   * #getAvailableCommands() available}.
   */
  void setDeviceMuted(boolean muted);

  ArrayList<VideoListener> videoListeners = new ArrayList<>();

  default void addVideoListener(com.google.android.exoplayer2.video.VideoListener listener) {
    videoListeners.add(listener);
  }

  default void removeVideoListener(com.google.android.exoplayer2.video.VideoListener listener) {
    videoListeners.remove(listener);
  }
}
