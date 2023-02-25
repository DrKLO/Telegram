/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2.trackselection;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.chunk.Chunk;
import com.google.android.exoplayer2.source.chunk.MediaChunk;
import com.google.android.exoplayer2.source.chunk.MediaChunkIterator;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.util.Log;
import java.util.List;
import org.checkerframework.checker.nullness.compatqual.NullableType;

/**
 * A {@link TrackSelection} that can change the individually selected track as a result of calling
 * {@link #updateSelectedTrack(long, long, long, List, MediaChunkIterator[])} or {@link
 * #evaluateQueueSize(long, List)}. This only happens between calls to {@link #enable()} and {@link
 * #disable()}.
 */
public interface ExoTrackSelection extends TrackSelection {

  /** Contains of a subset of selected tracks belonging to a {@link TrackGroup}. */
  final class Definition {
    /** The {@link TrackGroup} which tracks belong to. */
    public final TrackGroup group;
    /** The indices of the selected tracks in {@link #group}. */
    public final int[] tracks;
    /** The type that will be returned from {@link TrackSelection#getType()}. */
    public final @Type int type;

    private static final String TAG = "ETSDefinition";

    /**
     * @param group The {@link TrackGroup}. Must not be null.
     * @param tracks The indices of the selected tracks within the {@link TrackGroup}. Must not be
     *     null or empty. May be in any order.
     */
    public Definition(TrackGroup group, int... tracks) {
      this(group, tracks, TrackSelection.TYPE_UNSET);
    }

    /**
     * @param group The {@link TrackGroup}. Must not be null.
     * @param tracks The indices of the selected tracks within the {@link TrackGroup}. Must not be
     *     null or empty. May be in any order.
     * @param type The type that will be returned from {@link TrackSelection#getType()}.
     */
    public Definition(TrackGroup group, int[] tracks, @Type int type) {
      if (tracks.length == 0) {
        // TODO: Turn this into an assertion.
        Log.e(TAG, "Empty tracks are not allowed", new IllegalArgumentException());
      }
      this.group = group;
      this.tracks = tracks;
      this.type = type;
    }
  }

  /** Factory for {@link ExoTrackSelection} instances. */
  interface Factory {

    /**
     * Creates track selections for the provided {@link Definition Definitions}.
     *
     * <p>Implementations that create at most one adaptive track selection may use {@link
     * TrackSelectionUtil#createTrackSelectionsForDefinitions}.
     *
     * @param definitions A {@link Definition} array. May include null values.
     * @param bandwidthMeter A {@link BandwidthMeter} which can be used to select tracks.
     * @param mediaPeriodId The {@link MediaPeriodId} of the period for which tracks are to be
     *     selected.
     * @param timeline The {@link Timeline} holding the period for which tracks are to be selected.
     * @return The created selections. Must have the same length as {@code definitions} and may
     *     include null values.
     */
    @NullableType
    ExoTrackSelection[] createTrackSelections(
        @NullableType Definition[] definitions,
        BandwidthMeter bandwidthMeter,
        MediaPeriodId mediaPeriodId,
        Timeline timeline);
  }

  /**
   * Enables the track selection. Dynamic changes via {@link #updateSelectedTrack(long, long, long,
   * List, MediaChunkIterator[])}, {@link #evaluateQueueSize(long, List)} or {@link
   * #shouldCancelChunkLoad(long, Chunk, List)} will only happen after this call.
   *
   * <p>This method may not be called when the track selection is already enabled.
   */
  void enable();

  /**
   * Disables this track selection. No further dynamic changes via {@link #updateSelectedTrack(long,
   * long, long, List, MediaChunkIterator[])}, {@link #evaluateQueueSize(long, List)} or {@link
   * #shouldCancelChunkLoad(long, Chunk, List)} will happen after this call.
   *
   * <p>This method may only be called when the track selection is already enabled.
   */
  void disable();

  // Individual selected track.

  /** Returns the {@link Format} of the individual selected track. */
  Format getSelectedFormat();

  /** Returns the index in the track group of the individual selected track. */
  int getSelectedIndexInTrackGroup();

  /** Returns the index of the selected track. */
  int getSelectedIndex();

  /** Returns the reason for the current track selection. */
  @C.SelectionReason
  int getSelectionReason();

  /** Returns optional data associated with the current track selection. */
  @Nullable
  Object getSelectionData();

  // Adaptation.

  /**
   * Called to notify the selection of the current playback speed. The playback speed may affect
   * adaptive track selection.
   *
   * @param playbackSpeed The factor by which playback is sped up.
   */
  void onPlaybackSpeed(float playbackSpeed);

  /**
   * Called to notify the selection of a position discontinuity.
   *
   * <p>This happens when the playback position jumps, e.g., as a result of a seek being performed.
   */
  default void onDiscontinuity() {}

  /**
   * Called to notify when a rebuffer occurred.
   *
   * <p>A rebuffer is defined to be caused by buffer depletion rather than a user action. Hence this
   * method is not called during initial buffering or when buffering as a result of a seek
   * operation.
   */
  default void onRebuffer() {}

  /**
   * Called to notify when the playback is paused or resumed.
   *
   * @param playWhenReady Whether playback will proceed when ready.
   */
  default void onPlayWhenReadyChanged(boolean playWhenReady) {}

  /**
   * Updates the selected track for sources that load media in discrete {@link MediaChunk}s.
   *
   * <p>This method will only be called when the selection is enabled.
   *
   * @param playbackPositionUs The current playback position in microseconds. If playback of the
   *     period to which this track selection belongs has not yet started, the value will be the
   *     starting position in the period minus the duration of any media in previous periods still
   *     to be played.
   * @param bufferedDurationUs The duration of media currently buffered from the current playback
   *     position, in microseconds. Note that the next load position can be calculated as {@code
   *     (playbackPositionUs + bufferedDurationUs)}.
   * @param availableDurationUs The duration of media available for buffering from the current
   *     playback position, in microseconds, or {@link C#TIME_UNSET} if media can be buffered to the
   *     end of the current period. Note that if not set to {@link C#TIME_UNSET}, the position up to
   *     which media is available for buffering can be calculated as {@code (playbackPositionUs +
   *     availableDurationUs)}.
   * @param queue The queue of already buffered {@link MediaChunk}s. Must not be modified.
   * @param mediaChunkIterators An array of {@link MediaChunkIterator}s providing information about
   *     the sequence of upcoming media chunks for each track in the selection. All iterators start
   *     from the media chunk which will be loaded next if the respective track is selected. Note
   *     that this information may not be available for all tracks, and so some iterators may be
   *     empty.
   */
  void updateSelectedTrack(
      long playbackPositionUs,
      long bufferedDurationUs,
      long availableDurationUs,
      List<? extends MediaChunk> queue,
      MediaChunkIterator[] mediaChunkIterators);

  /**
   * Returns the number of chunks that should be retained in the queue.
   *
   * <p>May be called by sources that load media in discrete {@link MediaChunk MediaChunks} and
   * support discarding of buffered chunks.
   *
   * <p>To avoid excessive re-buffering, implementations should normally return the size of the
   * queue. An example of a case where a smaller value may be returned is if network conditions have
   * improved dramatically, allowing chunks to be discarded and re-buffered in a track of
   * significantly higher quality. Discarding chunks may allow faster switching to a higher quality
   * track in this case.
   *
   * <p>Note that even if the source supports discarding of buffered chunks, the actual number of
   * discarded chunks is not guaranteed. The source will call {@link #updateSelectedTrack(long,
   * long, long, List, MediaChunkIterator[])} with the updated queue of chunks before loading a new
   * chunk to allow switching to another quality.
   *
   * <p>This method will only be called when the selection is enabled and none of the {@link
   * MediaChunk MediaChunks} in the queue are currently loading.
   *
   * @param playbackPositionUs The current playback position in microseconds. If playback of the
   *     period to which this track selection belongs has not yet started, the value will be the
   *     starting position in the period minus the duration of any media in previous periods still
   *     to be played.
   * @param queue The queue of buffered {@link MediaChunk MediaChunks}. Must not be modified.
   * @return The number of chunks to retain in the queue.
   */
  int evaluateQueueSize(long playbackPositionUs, List<? extends MediaChunk> queue);

  /**
   * Returns whether an ongoing load of a chunk should be canceled.
   *
   * <p>May be called by sources that load media in discrete {@link MediaChunk MediaChunks} and
   * support canceling the ongoing chunk load. The ongoing chunk load is either the last {@link
   * MediaChunk} in the queue or another type of {@link Chunk}, for example, if the source loads
   * initialization or encryption data.
   *
   * <p>To avoid excessive re-buffering, implementations should normally return {@code false}. An
   * example where {@code true} might be returned is if a load of a high quality chunk gets stuck
   * and canceling this load in favor of a lower quality alternative may avoid a rebuffer.
   *
   * <p>The source will call {@link #evaluateQueueSize(long, List)} after the cancelation finishes
   * to allow discarding of chunks, and {@link #updateSelectedTrack(long, long, long, List,
   * MediaChunkIterator[])} before loading a new chunk to allow switching to another quality.
   *
   * <p>This method will only be called when the selection is enabled.
   *
   * @param playbackPositionUs The current playback position in microseconds. If playback of the
   *     period to which this track selection belongs has not yet started, the value will be the
   *     starting position in the period minus the duration of any media in previous periods still
   *     to be played.
   * @param loadingChunk The currently loading {@link Chunk} that will be canceled if this method
   *     returns {@code true}.
   * @param queue The queue of buffered {@link MediaChunk MediaChunks}, including the {@code
   *     loadingChunk} if it's a {@link MediaChunk}. Must not be modified.
   * @return Whether the ongoing load of {@code loadingChunk} should be canceled.
   */
  default boolean shouldCancelChunkLoad(
      long playbackPositionUs, Chunk loadingChunk, List<? extends MediaChunk> queue) {
    return false;
  }

  /**
   * Attempts to exclude the track at the specified index in the selection, making it ineligible for
   * selection by calls to {@link #updateSelectedTrack(long, long, long, List,
   * MediaChunkIterator[])} for the specified period of time.
   *
   * <p>Exclusion will fail if all other tracks are currently excluded. If excluding the currently
   * selected track, note that it will remain selected until the next call to {@link
   * #updateSelectedTrack(long, long, long, List, MediaChunkIterator[])}.
   *
   * <p>This method will only be called when the selection is enabled.
   *
   * @param index The index of the track in the selection.
   * @param exclusionDurationMs The duration of time for which the track should be excluded, in
   *     milliseconds.
   * @return Whether exclusion was successful.
   */
  boolean blacklist(int index, long exclusionDurationMs);

  /**
   * Returns whether the track at the specified index in the selection is excluded.
   *
   * @param index The index of the track in the selection.
   * @param nowMs The current time in the timebase of {@link
   *     android.os.SystemClock#elapsedRealtime()}.
   */
  boolean isBlacklisted(int index, long nowMs);
}
