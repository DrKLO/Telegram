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

import static java.lang.Math.max;
import static java.lang.Math.min;

import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.chunk.MediaChunk;
import com.google.android.exoplayer2.source.chunk.MediaChunkIterator;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import org.checkerframework.checker.nullness.compatqual.NullableType;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;

/**
 * A bandwidth based adaptive {@link ExoTrackSelection}, whose selected track is updated to be the
 * one of highest quality given the current network conditions and the state of the buffer.
 */
public class AdaptiveTrackSelection extends BaseTrackSelection {

  private static final String TAG = "AdaptiveTrackSelection";

  /** Factory for {@link AdaptiveTrackSelection} instances. */
  public static class Factory implements ExoTrackSelection.Factory {

    private final int minDurationForQualityIncreaseMs;
    private final int maxDurationForQualityDecreaseMs;
    private final int minDurationToRetainAfterDiscardMs;
    private final int maxWidthToDiscard;
    private final int maxHeightToDiscard;
    private final float bandwidthFraction;
    private final float bufferedFractionToLiveEdgeForQualityIncrease;
    private final Clock clock;

    /** Creates an adaptive track selection factory with default parameters. */
    public Factory() {
      this(
          DEFAULT_MIN_DURATION_FOR_QUALITY_INCREASE_MS,
          DEFAULT_MAX_DURATION_FOR_QUALITY_DECREASE_MS,
          DEFAULT_MIN_DURATION_TO_RETAIN_AFTER_DISCARD_MS,
          DEFAULT_BANDWIDTH_FRACTION);
    }

    /**
     * Creates an adaptive track selection factory.
     *
     * @param minDurationForQualityIncreaseMs The minimum duration of buffered data required for the
     *     selected track to switch to one of higher quality.
     * @param maxDurationForQualityDecreaseMs The maximum duration of buffered data required for the
     *     selected track to switch to one of lower quality.
     * @param minDurationToRetainAfterDiscardMs When switching to a video track of higher quality,
     *     the selection may indicate that media already buffered at the lower quality can be
     *     discarded to speed up the switch. This is the minimum duration of media that must be
     *     retained at the lower quality. It must be at least {@code
     *     minDurationForQualityIncreaseMs}.
     * @param bandwidthFraction The fraction of the available bandwidth that the selection should
     *     consider available for use. Setting to a value less than 1 is recommended to account for
     *     inaccuracies in the bandwidth estimator.
     */
    public Factory(
        int minDurationForQualityIncreaseMs,
        int maxDurationForQualityDecreaseMs,
        int minDurationToRetainAfterDiscardMs,
        float bandwidthFraction) {
      this(
          minDurationForQualityIncreaseMs,
          maxDurationForQualityDecreaseMs,
          minDurationToRetainAfterDiscardMs,
          DEFAULT_MAX_WIDTH_TO_DISCARD,
          DEFAULT_MAX_HEIGHT_TO_DISCARD,
          bandwidthFraction,
          DEFAULT_BUFFERED_FRACTION_TO_LIVE_EDGE_FOR_QUALITY_INCREASE,
          Clock.DEFAULT);
    }

    /**
     * Creates an adaptive track selection factory.
     *
     * @param minDurationForQualityIncreaseMs The minimum duration of buffered data required for the
     *     selected track to switch to one of higher quality.
     * @param maxDurationForQualityDecreaseMs The maximum duration of buffered data required for the
     *     selected track to switch to one of lower quality.
     * @param minDurationToRetainAfterDiscardMs When switching to a video track of higher quality,
     *     the selection may indicate that media already buffered at the lower quality can be
     *     discarded to speed up the switch. This is the minimum duration of media that must be
     *     retained at the lower quality. It must be at least {@code
     *     minDurationForQualityIncreaseMs}.
     * @param maxWidthToDiscard The maximum video width that the selector may discard from the
     *     buffer to speed up switching to a higher quality.
     * @param maxHeightToDiscard The maximum video height that the selector may discard from the
     *     buffer to speed up switching to a higher quality.
     * @param bandwidthFraction The fraction of the available bandwidth that the selection should
     *     consider available for use. Setting to a value less than 1 is recommended to account for
     *     inaccuracies in the bandwidth estimator.
     */
    public Factory(
        int minDurationForQualityIncreaseMs,
        int maxDurationForQualityDecreaseMs,
        int minDurationToRetainAfterDiscardMs,
        int maxWidthToDiscard,
        int maxHeightToDiscard,
        float bandwidthFraction) {
      this(
          minDurationForQualityIncreaseMs,
          maxDurationForQualityDecreaseMs,
          minDurationToRetainAfterDiscardMs,
          maxWidthToDiscard,
          maxHeightToDiscard,
          bandwidthFraction,
          DEFAULT_BUFFERED_FRACTION_TO_LIVE_EDGE_FOR_QUALITY_INCREASE,
          Clock.DEFAULT);
    }

    /**
     * Creates an adaptive track selection factory.
     *
     * @param minDurationForQualityIncreaseMs The minimum duration of buffered data required for the
     *     selected track to switch to one of higher quality.
     * @param maxDurationForQualityDecreaseMs The maximum duration of buffered data required for the
     *     selected track to switch to one of lower quality.
     * @param minDurationToRetainAfterDiscardMs When switching to a video track of higher quality,
     *     the selection may indicate that media already buffered at the lower quality can be
     *     discarded to speed up the switch. This is the minimum duration of media that must be
     *     retained at the lower quality. It must be at least {@code
     *     minDurationForQualityIncreaseMs}.
     * @param bandwidthFraction The fraction of the available bandwidth that the selection should
     *     consider available for use. Setting to a value less than 1 is recommended to account for
     *     inaccuracies in the bandwidth estimator.
     * @param bufferedFractionToLiveEdgeForQualityIncrease For live streaming, the fraction of the
     *     duration from current playback position to the live edge that has to be buffered before
     *     the selected track can be switched to one of higher quality. This parameter is only
     *     applied when the playback position is closer to the live edge than {@code
     *     minDurationForQualityIncreaseMs}, which would otherwise prevent switching to a higher
     *     quality from happening.
     * @param clock A {@link Clock}.
     */
    public Factory(
        int minDurationForQualityIncreaseMs,
        int maxDurationForQualityDecreaseMs,
        int minDurationToRetainAfterDiscardMs,
        float bandwidthFraction,
        float bufferedFractionToLiveEdgeForQualityIncrease,
        Clock clock) {
      this(
          minDurationForQualityIncreaseMs,
          maxDurationForQualityDecreaseMs,
          minDurationToRetainAfterDiscardMs,
          DEFAULT_MAX_WIDTH_TO_DISCARD,
          DEFAULT_MAX_HEIGHT_TO_DISCARD,
          bandwidthFraction,
          bufferedFractionToLiveEdgeForQualityIncrease,
          clock);
    }

    /**
     * Creates an adaptive track selection factory.
     *
     * @param minDurationForQualityIncreaseMs The minimum duration of buffered data required for the
     *     selected track to switch to one of higher quality.
     * @param maxDurationForQualityDecreaseMs The maximum duration of buffered data required for the
     *     selected track to switch to one of lower quality.
     * @param minDurationToRetainAfterDiscardMs When switching to a video track of higher quality,
     *     the selection may indicate that media already buffered at the lower quality can be
     *     discarded to speed up the switch. This is the minimum duration of media that must be
     *     retained at the lower quality. It must be at least {@code
     *     minDurationForQualityIncreaseMs}.
     * @param maxWidthToDiscard The maximum video width that the selector may discard from the
     *     buffer to speed up switching to a higher quality.
     * @param maxHeightToDiscard The maximum video height that the selector may discard from the
     *     buffer to speed up switching to a higher quality.
     * @param bandwidthFraction The fraction of the available bandwidth that the selection should
     *     consider available for use. Setting to a value less than 1 is recommended to account for
     *     inaccuracies in the bandwidth estimator.
     * @param bufferedFractionToLiveEdgeForQualityIncrease For live streaming, the fraction of the
     *     duration from current playback position to the live edge that has to be buffered before
     *     the selected track can be switched to one of higher quality. This parameter is only
     *     applied when the playback position is closer to the live edge than {@code
     *     minDurationForQualityIncreaseMs}, which would otherwise prevent switching to a higher
     *     quality from happening.
     * @param clock A {@link Clock}.
     */
    public Factory(
        int minDurationForQualityIncreaseMs,
        int maxDurationForQualityDecreaseMs,
        int minDurationToRetainAfterDiscardMs,
        int maxWidthToDiscard,
        int maxHeightToDiscard,
        float bandwidthFraction,
        float bufferedFractionToLiveEdgeForQualityIncrease,
        Clock clock) {
      this.minDurationForQualityIncreaseMs = minDurationForQualityIncreaseMs;
      this.maxDurationForQualityDecreaseMs = maxDurationForQualityDecreaseMs;
      this.minDurationToRetainAfterDiscardMs = minDurationToRetainAfterDiscardMs;
      this.maxWidthToDiscard = maxWidthToDiscard;
      this.maxHeightToDiscard = maxHeightToDiscard;
      this.bandwidthFraction = bandwidthFraction;
      this.bufferedFractionToLiveEdgeForQualityIncrease =
          bufferedFractionToLiveEdgeForQualityIncrease;
      this.clock = clock;
    }

    @Override
    public final @NullableType ExoTrackSelection[] createTrackSelections(
        @NullableType Definition[] definitions,
        BandwidthMeter bandwidthMeter,
        MediaPeriodId mediaPeriodId,
        Timeline timeline) {
      ImmutableList<ImmutableList<AdaptationCheckpoint>> adaptationCheckpoints =
          getAdaptationCheckpoints(definitions);
      ExoTrackSelection[] selections = new ExoTrackSelection[definitions.length];
      for (int i = 0; i < definitions.length; i++) {
        @Nullable Definition definition = definitions[i];
        if (definition == null || definition.tracks.length == 0) {
          continue;
        }
        selections[i] =
            definition.tracks.length == 1
                ? new FixedTrackSelection(
                    definition.group,
                    /* track= */ definition.tracks[0],
                    /* type= */ definition.type)
                : createAdaptiveTrackSelection(
                    definition.group,
                    definition.tracks,
                    definition.type,
                    bandwidthMeter,
                    adaptationCheckpoints.get(i));
      }
      return selections;
    }

    /**
     * Creates a single adaptive selection for the given group, bandwidth meter and tracks.
     *
     * @param group The {@link TrackGroup}.
     * @param tracks The indices of the selected tracks in the track group.
     * @param type The type that will be returned from {@link TrackSelection#getType()}.
     * @param bandwidthMeter A {@link BandwidthMeter} which can be used to select tracks.
     * @param adaptationCheckpoints The {@link AdaptationCheckpoint checkpoints} that can be used to
     *     calculate available bandwidth for this selection.
     * @return An {@link AdaptiveTrackSelection} for the specified tracks.
     */
    protected AdaptiveTrackSelection createAdaptiveTrackSelection(
        TrackGroup group,
        int[] tracks,
        int type,
        BandwidthMeter bandwidthMeter,
        ImmutableList<AdaptationCheckpoint> adaptationCheckpoints) {
      return new AdaptiveTrackSelection(
          group,
          tracks,
          type,
          bandwidthMeter,
          minDurationForQualityIncreaseMs,
          maxDurationForQualityDecreaseMs,
          minDurationToRetainAfterDiscardMs,
          maxWidthToDiscard,
          maxHeightToDiscard,
          bandwidthFraction,
          bufferedFractionToLiveEdgeForQualityIncrease,
          adaptationCheckpoints,
          clock);
    }
  }

  public static final int DEFAULT_MIN_DURATION_FOR_QUALITY_INCREASE_MS = 10_000;
  public static final int DEFAULT_MAX_DURATION_FOR_QUALITY_DECREASE_MS = 25_000;
  public static final int DEFAULT_MIN_DURATION_TO_RETAIN_AFTER_DISCARD_MS = 25_000;
  public static final int DEFAULT_MAX_WIDTH_TO_DISCARD = 1279;
  public static final int DEFAULT_MAX_HEIGHT_TO_DISCARD = 719;
  public static final float DEFAULT_BANDWIDTH_FRACTION = 0.7f;
  public static final float DEFAULT_BUFFERED_FRACTION_TO_LIVE_EDGE_FOR_QUALITY_INCREASE = 0.75f;

  private static final long MIN_TIME_BETWEEN_BUFFER_REEVALUTATION_MS = 1000;

  private final BandwidthMeter bandwidthMeter;
  private final long minDurationForQualityIncreaseUs;
  private final long maxDurationForQualityDecreaseUs;
  private final long minDurationToRetainAfterDiscardUs;
  private final int maxWidthToDiscard;
  private final int maxHeightToDiscard;
  private final float bandwidthFraction;
  private final float bufferedFractionToLiveEdgeForQualityIncrease;
  private final ImmutableList<AdaptationCheckpoint> adaptationCheckpoints;
  private final Clock clock;

  private float playbackSpeed;
  private int selectedIndex;
  private @C.SelectionReason int reason;
  private long lastBufferEvaluationMs;
  @Nullable private MediaChunk lastBufferEvaluationMediaChunk;

  /**
   * @param group The {@link TrackGroup}.
   * @param tracks The indices of the selected tracks within the {@link TrackGroup}. Must not be
   *     empty. May be in any order.
   * @param bandwidthMeter Provides an estimate of the currently available bandwidth.
   */
  public AdaptiveTrackSelection(TrackGroup group, int[] tracks, BandwidthMeter bandwidthMeter) {
    this(
        group,
        tracks,
        TrackSelection.TYPE_UNSET,
        bandwidthMeter,
        DEFAULT_MIN_DURATION_FOR_QUALITY_INCREASE_MS,
        DEFAULT_MAX_DURATION_FOR_QUALITY_DECREASE_MS,
        DEFAULT_MIN_DURATION_TO_RETAIN_AFTER_DISCARD_MS,
        DEFAULT_MAX_WIDTH_TO_DISCARD,
        DEFAULT_MAX_HEIGHT_TO_DISCARD,
        DEFAULT_BANDWIDTH_FRACTION,
        DEFAULT_BUFFERED_FRACTION_TO_LIVE_EDGE_FOR_QUALITY_INCREASE,
        /* adaptationCheckpoints= */ ImmutableList.of(),
        Clock.DEFAULT);
  }

  /**
   * @param group The {@link TrackGroup}.
   * @param tracks The indices of the selected tracks within the {@link TrackGroup}. Must not be
   *     empty. May be in any order.
   * @param type The type that will be returned from {@link TrackSelection#getType()}.
   * @param bandwidthMeter Provides an estimate of the currently available bandwidth.
   * @param minDurationForQualityIncreaseMs The minimum duration of buffered data required for the
   *     selected track to switch to one of higher quality.
   * @param maxDurationForQualityDecreaseMs The maximum duration of buffered data required for the
   *     selected track to switch to one of lower quality.
   * @param minDurationToRetainAfterDiscardMs When switching to a video track of higher quality, the
   *     selection may indicate that media already buffered at the lower quality can be discarded to
   *     speed up the switch. This is the minimum duration of media that must be retained at the
   *     lower quality. It must be at least {@code minDurationForQualityIncreaseMs}.
   * @param maxWidthToDiscard The maximum video width that the selector may discard from the buffer
   *     to speed up switching to a higher quality.
   * @param maxHeightToDiscard The maximum video height that the selector may discard from the
   *     buffer to speed up switching to a higher quality.
   * @param bandwidthFraction The fraction of the available bandwidth that the selection should
   *     consider available for use. Setting to a value less than 1 is recommended to account for
   *     inaccuracies in the bandwidth estimator.
   * @param bufferedFractionToLiveEdgeForQualityIncrease For live streaming, the fraction of the
   *     duration from current playback position to the live edge that has to be buffered before the
   *     selected track can be switched to one of higher quality. This parameter is only applied
   *     when the playback position is closer to the live edge than {@code
   *     minDurationForQualityIncreaseMs}, which would otherwise prevent switching to a higher
   *     quality from happening.
   * @param adaptationCheckpoints The {@link AdaptationCheckpoint checkpoints} that can be used to
   *     calculate available bandwidth for this selection.
   * @param clock The {@link Clock}.
   */
  protected AdaptiveTrackSelection(
      TrackGroup group,
      int[] tracks,
      @Type int type,
      BandwidthMeter bandwidthMeter,
      long minDurationForQualityIncreaseMs,
      long maxDurationForQualityDecreaseMs,
      long minDurationToRetainAfterDiscardMs,
      int maxWidthToDiscard,
      int maxHeightToDiscard,
      float bandwidthFraction,
      float bufferedFractionToLiveEdgeForQualityIncrease,
      List<AdaptationCheckpoint> adaptationCheckpoints,
      Clock clock) {
    super(group, tracks, type);
    if (minDurationToRetainAfterDiscardMs < minDurationForQualityIncreaseMs) {
      Log.w(
          TAG,
          "Adjusting minDurationToRetainAfterDiscardMs to be at least"
              + " minDurationForQualityIncreaseMs");
      minDurationToRetainAfterDiscardMs = minDurationForQualityIncreaseMs;
    }
    this.bandwidthMeter = bandwidthMeter;
    this.minDurationForQualityIncreaseUs = minDurationForQualityIncreaseMs * 1000L;
    this.maxDurationForQualityDecreaseUs = maxDurationForQualityDecreaseMs * 1000L;
    this.minDurationToRetainAfterDiscardUs = minDurationToRetainAfterDiscardMs * 1000L;
    this.maxWidthToDiscard = maxWidthToDiscard;
    this.maxHeightToDiscard = maxHeightToDiscard;
    this.bandwidthFraction = bandwidthFraction;
    this.bufferedFractionToLiveEdgeForQualityIncrease =
        bufferedFractionToLiveEdgeForQualityIncrease;
    this.adaptationCheckpoints = ImmutableList.copyOf(adaptationCheckpoints);
    this.clock = clock;
    playbackSpeed = 1f;
    reason = C.SELECTION_REASON_UNKNOWN;
    lastBufferEvaluationMs = C.TIME_UNSET;
  }

  @CallSuper
  @Override
  public void enable() {
    lastBufferEvaluationMs = C.TIME_UNSET;
    lastBufferEvaluationMediaChunk = null;
  }

  @CallSuper
  @Override
  public void disable() {
    // Avoid keeping a reference to a MediaChunk in case it prevents garbage collection.
    lastBufferEvaluationMediaChunk = null;
  }

  @Override
  public void onPlaybackSpeed(float playbackSpeed) {
    this.playbackSpeed = playbackSpeed;
  }

  @Override
  public void updateSelectedTrack(
      long playbackPositionUs,
      long bufferedDurationUs,
      long availableDurationUs,
      List<? extends MediaChunk> queue,
      MediaChunkIterator[] mediaChunkIterators) {
    long nowMs = clock.elapsedRealtime();
    long chunkDurationUs = getNextChunkDurationUs(mediaChunkIterators, queue);

    // Make initial selection
    if (reason == C.SELECTION_REASON_UNKNOWN) {
      reason = C.SELECTION_REASON_INITIAL;
      selectedIndex = determineIdealSelectedIndex(0, nowMs, chunkDurationUs);
      return;
    }

    int previousSelectedIndex = selectedIndex;
    @C.SelectionReason int previousReason = reason;
    int formatIndexOfPreviousChunk =
        queue.isEmpty() ? C.INDEX_UNSET : indexOf(Iterables.getLast(queue).trackFormat);
    if (formatIndexOfPreviousChunk != C.INDEX_UNSET) {
      previousSelectedIndex = formatIndexOfPreviousChunk;
      previousReason = Iterables.getLast(queue).trackSelectionReason;
    }
    int newSelectedIndex = determineIdealSelectedIndex(1, nowMs, chunkDurationUs);
    if (!isBlacklisted(previousSelectedIndex, nowMs)) {
      // Revert back to the previous selection if conditions are not suitable for switching.
      Format currentFormat = getFormat(previousSelectedIndex);
      Format selectedFormat = getFormat(newSelectedIndex);
      long minDurationForQualityIncreaseUs =
          minDurationForQualityIncreaseUs(availableDurationUs, chunkDurationUs);
      if (selectedFormat.bitrate > currentFormat.bitrate
          && bufferedDurationUs < minDurationForQualityIncreaseUs) {
        // The selected track is a higher quality, but we have insufficient buffer to safely switch
        // up. Defer switching up for now.
        newSelectedIndex = previousSelectedIndex;
      } else if (selectedFormat.bitrate < currentFormat.bitrate
          && bufferedDurationUs >= maxDurationForQualityDecreaseUs) {
        // The selected track is a lower quality, but we have sufficient buffer to defer switching
        // down for now. maxDurationForQualityDecreaseUs+ ")");
        newSelectedIndex = previousSelectedIndex;
      }
    }
    // If we adapted, update the trigger.
    reason =
        newSelectedIndex == previousSelectedIndex ? previousReason : C.SELECTION_REASON_ADAPTIVE;
    selectedIndex = newSelectedIndex;
  }

  @Override
  public int getSelectedIndex() {
    return selectedIndex;
  }

  @Override
  public @C.SelectionReason int getSelectionReason() {
    return reason;
  }

  @Override
  @Nullable
  public Object getSelectionData() {
    return null;
  }

  @Override
  public int evaluateQueueSize(long playbackPositionUs, List<? extends MediaChunk> queue) {
    long nowMs = clock.elapsedRealtime();
    if (!shouldEvaluateQueueSize(nowMs, queue)) {
      return queue.size();
    }
    lastBufferEvaluationMs = nowMs;
    lastBufferEvaluationMediaChunk = queue.isEmpty() ? null : Iterables.getLast(queue);

    if (queue.isEmpty()) {
      return 0;
    }
    int queueSize = queue.size();
    MediaChunk lastChunk = queue.get(queueSize - 1);
    long playoutBufferedDurationBeforeLastChunkUs =
        Util.getPlayoutDurationForMediaDuration(
            lastChunk.startTimeUs - playbackPositionUs, playbackSpeed);
    long minDurationToRetainAfterDiscardUs = getMinDurationToRetainAfterDiscardUs();
    if (playoutBufferedDurationBeforeLastChunkUs < minDurationToRetainAfterDiscardUs) {
      return queueSize;
    }
    int idealSelectedIndex = determineIdealSelectedIndex(-1, nowMs, getLastChunkDurationUs(queue));
    Format idealFormat = getFormat(idealSelectedIndex);
    // If chunks contain video, discard from the first chunk after minDurationToRetainAfterDiscardUs
    // whose resolution and bitrate are both lower than the ideal track, and whose width and height
    // are less than or equal to maxWidthToDiscard and maxHeightToDiscard respectively.
    for (int i = 0; i < queueSize; i++) {
      MediaChunk chunk = queue.get(i);
      Format format = chunk.trackFormat;
      long mediaDurationBeforeThisChunkUs = chunk.startTimeUs - playbackPositionUs;
      long playoutDurationBeforeThisChunkUs =
          Util.getPlayoutDurationForMediaDuration(mediaDurationBeforeThisChunkUs, playbackSpeed);
      if (playoutDurationBeforeThisChunkUs >= minDurationToRetainAfterDiscardUs
          && format.bitrate < idealFormat.bitrate
          && format.height != Format.NO_VALUE
          && format.height <= maxHeightToDiscard
          && format.width != Format.NO_VALUE
          && format.width <= maxWidthToDiscard
          && format.height < idealFormat.height) {
        return i;
      }
    }
    return queueSize;
  }

  /**
   * Called when updating the selected track to determine whether a candidate track can be selected.
   *
   * @param format The {@link Format} of the candidate track.
   * @param trackBitrate The estimated bitrate of the track. May differ from {@link Format#bitrate}
   *     if a more accurate estimate of the current track bitrate is available.
   * @param effectiveBitrate The bitrate available to this selection.
   * @return Whether this {@link Format} can be selected.
   */
  @SuppressWarnings("unused")
  protected boolean canSelectFormat(Format format, int trackBitrate, long effectiveBitrate) {
    return format.cached || trackBitrate <= effectiveBitrate;
  }

  /**
   * Called from {@link #evaluateQueueSize(long, List)} to determine whether an evaluation should be
   * performed.
   *
   * @param nowMs The current value of {@link Clock#elapsedRealtime()}.
   * @param queue The queue of buffered {@link MediaChunk MediaChunks}. Must not be modified.
   * @return Whether an evaluation should be performed.
   */
  protected boolean shouldEvaluateQueueSize(long nowMs, List<? extends MediaChunk> queue) {
    return lastBufferEvaluationMs == C.TIME_UNSET
        || nowMs - lastBufferEvaluationMs >= MIN_TIME_BETWEEN_BUFFER_REEVALUTATION_MS
        || (!queue.isEmpty() && !Iterables.getLast(queue).equals(lastBufferEvaluationMediaChunk));
  }

  /**
   * Called from {@link #evaluateQueueSize(long, List)} to determine the minimum duration of buffer
   * to retain after discarding chunks.
   *
   * @return The minimum duration of buffer to retain after discarding chunks, in microseconds.
   */
  protected long getMinDurationToRetainAfterDiscardUs() {
    return minDurationToRetainAfterDiscardUs;
  }

  /**
   * Computes the ideal selected index ignoring buffer health.
   *
   * @param nowMs The current time in the timebase of {@link Clock#elapsedRealtime()}, or {@link
   *     Long#MIN_VALUE} to ignore track exclusion.
   * @param chunkDurationUs The duration of a media chunk in microseconds, or {@link C#TIME_UNSET}
   *     if unknown.
   */
  private int determineIdealSelectedIndex(int type, long nowMs, long chunkDurationUs) {
    final long effectiveBitrate = getAllocatedBandwidth(chunkDurationUs);
    FileLog.d("debug_loading_player: determineIdealSelectedIndex: type="+type+" effectiveBitrate=" + effectiveBitrate);
    final HashMap<Integer, Integer> formatsByResolution = new HashMap<>();
    final ArrayList<Integer> formatIndices = new ArrayList<>();
    for (int i = 0; i < length; i++) {
      if (nowMs != Long.MIN_VALUE && isBlacklisted(i, nowMs)) continue;
      final Format format = getFormat(i);
      final int resolution = Math.max(format.width, format.height);
      if (!formatsByResolution.containsKey(resolution)) {
        formatsByResolution.put(resolution, i);
        formatIndices.add(i);
      } else {
        final int existingFormatIndex = formatsByResolution.get(resolution);
        final Format existingFormat = getFormat(existingFormatIndex);
        if (existingFormat.cached && !format.cached) continue;
        if (
          !existingFormat.cached && format.cached ||
          format.bitrate < existingFormat.bitrate
        ) {
          formatsByResolution.put(resolution, i);
          formatIndices.remove((Integer) existingFormatIndex);
          formatIndices.add(i);
        }
      }
    }
    if (type == 0) {
      for (int i : formatIndices) {
        Format format = getFormat(i);
        if (format.cached) {
          FileLog.d("debug_loading_player: determineIdealSelectedIndex: initial setup, choose cached format#" + i);
          return i;
        }
      }
    }
    int lowestBitrateAllowedIndex = 0;
    for (int i : formatIndices) {
      Format format = getFormat(i);
      FileLog.d("debug_loading_player: determineIdealSelectedIndex: format#" + i + " bitrate=" + format.bitrate + " " + format.width + "x" + format.height + " codecs="+format.codecs+" (cached=" + format.cached + ")");
      if (canSelectFormat(format, format.bitrate, effectiveBitrate)) {
//        if (!format.cached && type == 0) {
//          for (int j = i + 1; j < formatIndices.size(); ++j) {
//            int i2 = formatIndices.get(j);
//            if (getFormat(i2).cached) {
//              FileLog.d("debug_loading_player: determineIdealSelectedIndex: chose to start with lower but cached format#" + i);
//              return i2;
//            }
//          }
//        }
        FileLog.d("debug_loading_player: determineIdealSelectedIndex: selected format#" + i);
        return i;
      } else {
        lowestBitrateAllowedIndex = i;
      }
    }
    FileLog.d("debug_loading_player: determineIdealSelectedIndex: selected format#" + lowestBitrateAllowedIndex + " (lowest, nothing is fit)");
    return lowestBitrateAllowedIndex;
  }

  private long minDurationForQualityIncreaseUs(long availableDurationUs, long chunkDurationUs) {
    if (availableDurationUs == C.TIME_UNSET) {
      // We are not in a live stream. Use the configured value.
      return minDurationForQualityIncreaseUs;
    }
    if (chunkDurationUs != C.TIME_UNSET) {
      // We are currently selecting a new live chunk. Even under perfect conditions, the buffered
      // duration can't include the last chunk duration yet because we are still selecting a track
      // for this or a previous chunk. Hence, we subtract one chunk duration from the total
      // available live duration to ensure we only compare the buffered duration against what is
      // actually achievable.
      availableDurationUs -= chunkDurationUs;
    }
    long adjustedMinDurationForQualityIncreaseUs =
        (long) (availableDurationUs * bufferedFractionToLiveEdgeForQualityIncrease);
    return min(adjustedMinDurationForQualityIncreaseUs, minDurationForQualityIncreaseUs);
  }

  /**
   * Returns a best estimate of the duration of the next chunk, in microseconds, or {@link
   * C#TIME_UNSET} if an estimate could not be determined.
   */
  private long getNextChunkDurationUs(
      MediaChunkIterator[] mediaChunkIterators, List<? extends MediaChunk> queue) {
    // Try to get the next chunk duration for the currently selected format.
    if (selectedIndex < mediaChunkIterators.length && mediaChunkIterators[selectedIndex].next()) {
      MediaChunkIterator iterator = mediaChunkIterators[selectedIndex];
      return iterator.getChunkEndTimeUs() - iterator.getChunkStartTimeUs();
    }
    // Try to get the next chunk duration for another format, on the assumption that chunks
    // belonging to different formats are likely to have identical or similar durations.
    for (MediaChunkIterator iterator : mediaChunkIterators) {
      if (iterator.next()) {
        return iterator.getChunkEndTimeUs() - iterator.getChunkStartTimeUs();
      }
    }
    // Try to get chunk duration for last chunk in the queue, on the assumption that the next chunk
    // is likely to have a similar duration.
    return getLastChunkDurationUs(queue);
  }

  /**
   * Returns the duration of the last chunk in the queue, in microseconds, or {@link C#TIME_UNSET}
   * if the queue is empty or if the last chunk has an undefined start or end time.
   */
  private long getLastChunkDurationUs(List<? extends MediaChunk> queue) {
    if (queue.isEmpty()) {
      return C.TIME_UNSET;
    }
    MediaChunk lastChunk = Iterables.getLast(queue);
    return lastChunk.startTimeUs != C.TIME_UNSET && lastChunk.endTimeUs != C.TIME_UNSET
        ? lastChunk.endTimeUs - lastChunk.startTimeUs
        : C.TIME_UNSET;
  }

  private long getAllocatedBandwidth(long chunkDurationUs) {
    long totalBandwidth = getTotalAllocatableBandwidth(chunkDurationUs);
    if (adaptationCheckpoints.isEmpty()) {
      return totalBandwidth;
    }
    int nextIndex = 1;
    while (nextIndex < adaptationCheckpoints.size() - 1
        && adaptationCheckpoints.get(nextIndex).totalBandwidth < totalBandwidth) {
      nextIndex++;
    }
    AdaptationCheckpoint previous = adaptationCheckpoints.get(nextIndex - 1);
    AdaptationCheckpoint next = adaptationCheckpoints.get(nextIndex);
    float fractionBetweenCheckpoints =
        (float) (totalBandwidth - previous.totalBandwidth)
            / (next.totalBandwidth - previous.totalBandwidth);
    return previous.allocatedBandwidth
        + (long)
            (fractionBetweenCheckpoints * (next.allocatedBandwidth - previous.allocatedBandwidth));
  }

  private long getTotalAllocatableBandwidth(long chunkDurationUs) {
    long cautiousBandwidthEstimate =
        (long) (bandwidthMeter.getBitrateEstimate() * bandwidthFraction);
    long timeToFirstByteEstimateUs = bandwidthMeter.getTimeToFirstByteEstimateUs();
    if (timeToFirstByteEstimateUs == C.TIME_UNSET || chunkDurationUs == C.TIME_UNSET) {
      return (long) (cautiousBandwidthEstimate / playbackSpeed);
    }
    float availableTimeToLoadUs =
        max(chunkDurationUs / playbackSpeed - timeToFirstByteEstimateUs, 0);
    return (long) (cautiousBandwidthEstimate * availableTimeToLoadUs / chunkDurationUs);
  }

  /**
   * Returns adaptation checkpoints for allocating bandwidth for adaptive track selections.
   *
   * @param definitions Array of track selection {@link Definition definitions}. Elements may be
   *     null.
   * @return List of {@link AdaptationCheckpoint checkpoints} for each adaptive {@link Definition}
   *     with more than one selected track.
   */
  private static ImmutableList<ImmutableList<AdaptationCheckpoint>> getAdaptationCheckpoints(
      @NullableType Definition[] definitions) {
    List<ImmutableList.@NullableType Builder<AdaptationCheckpoint>> checkPointBuilders =
        new ArrayList<>();
    for (int i = 0; i < definitions.length; i++) {
      if (definitions[i] != null && definitions[i].tracks.length > 1) {
        ImmutableList.Builder<AdaptationCheckpoint> builder = ImmutableList.builder();
        // Add initial all-zero checkpoint.
        builder.add(new AdaptationCheckpoint(/* totalBandwidth= */ 0, /* allocatedBandwidth= */ 0));
        checkPointBuilders.add(builder);
      } else {
        checkPointBuilders.add(null);
      }
    }
    // Add minimum bitrate selection checkpoint.
    long[][] trackBitrates = getSortedTrackBitrates(definitions);
    int[] currentTrackIndices = new int[trackBitrates.length];
    long[] currentTrackBitrates = new long[trackBitrates.length];
    for (int i = 0; i < trackBitrates.length; i++) {
      currentTrackBitrates[i] = trackBitrates[i].length == 0 ? 0 : trackBitrates[i][0];
    }
    addCheckpoint(checkPointBuilders, currentTrackBitrates);
    // Iterate through all adaptive checkpoints.
    ImmutableList<Integer> switchOrder = getSwitchOrder(trackBitrates);
    for (int i = 0; i < switchOrder.size(); i++) {
      int switchIndex = switchOrder.get(i);
      int newTrackIndex = ++currentTrackIndices[switchIndex];
      currentTrackBitrates[switchIndex] = trackBitrates[switchIndex][newTrackIndex];
      addCheckpoint(checkPointBuilders, currentTrackBitrates);
    }
    // Add final checkpoint to extrapolate additional bandwidth for adaptive selections.
    for (int i = 0; i < definitions.length; i++) {
      if (checkPointBuilders.get(i) != null) {
        currentTrackBitrates[i] *= 2;
      }
    }
    addCheckpoint(checkPointBuilders, currentTrackBitrates);
    ImmutableList.Builder<ImmutableList<AdaptationCheckpoint>> output = ImmutableList.builder();
    for (int i = 0; i < checkPointBuilders.size(); i++) {
      @Nullable ImmutableList.Builder<AdaptationCheckpoint> builder = checkPointBuilders.get(i);
      output.add(builder == null ? ImmutableList.of() : builder.build());
    }
    return output.build();
  }

  /** Returns sorted track bitrates for all selected tracks. */
  private static long[][] getSortedTrackBitrates(@NullableType Definition[] definitions) {
    long[][] trackBitrates = new long[definitions.length][];
    for (int i = 0; i < definitions.length; i++) {
      @Nullable Definition definition = definitions[i];
      if (definition == null) {
        trackBitrates[i] = new long[0];
        continue;
      }
      trackBitrates[i] = new long[definition.tracks.length];
      for (int j = 0; j < definition.tracks.length; j++) {
        long bitrate = definition.group.getFormat(definition.tracks[j]).bitrate;
        trackBitrates[i][j] = bitrate == Format.NO_VALUE ? 0 : bitrate;
      }
      Arrays.sort(trackBitrates[i]);
    }
    return trackBitrates;
  }

  /**
   * Returns order of track indices in which the respective track should be switched up.
   *
   * @param trackBitrates Sorted tracks bitrates for each selection.
   * @return List of track indices indicating in which order tracks should be switched up.
   */
  private static ImmutableList<Integer> getSwitchOrder(long[][] trackBitrates) {
    // Algorithm:
    //  1. Use log bitrates to treat all bitrate update steps equally.
    //  2. Distribute switch points for each selection equally in the same [0.0-1.0] range.
    //  3. Switch up one format at a time in the order of the switch points.
    Multimap<Double, Integer> switchPoints = MultimapBuilder.treeKeys().arrayListValues().build();
    for (int i = 0; i < trackBitrates.length; i++) {
      if (trackBitrates[i].length <= 1) {
        continue;
      }
      double[] logBitrates = new double[trackBitrates[i].length];
      for (int j = 0; j < trackBitrates[i].length; j++) {
        logBitrates[j] =
            trackBitrates[i][j] == Format.NO_VALUE ? 0 : Math.log((double) trackBitrates[i][j]);
      }
      double totalBitrateDiff = logBitrates[logBitrates.length - 1] - logBitrates[0];
      for (int j = 0; j < logBitrates.length - 1; j++) {
        double switchBitrate = 0.5 * (logBitrates[j] + logBitrates[j + 1]);
        double switchPoint =
            totalBitrateDiff == 0.0 ? 1.0 : (switchBitrate - logBitrates[0]) / totalBitrateDiff;
        switchPoints.put(switchPoint, i);
      }
    }
    return ImmutableList.copyOf(switchPoints.values());
  }

  /**
   * Add a checkpoint to the builders.
   *
   * @param checkPointBuilders Builders for adaptation checkpoints. May have null elements.
   * @param checkpointBitrates The bitrates of each track at this checkpoint.
   */
  private static void addCheckpoint(
      List<ImmutableList.@NullableType Builder<AdaptationCheckpoint>> checkPointBuilders,
      long[] checkpointBitrates) {
    // Total bitrate includes all fixed tracks.
    long totalBitrate = 0;
    for (int i = 0; i < checkpointBitrates.length; i++) {
      totalBitrate += checkpointBitrates[i];
    }
    for (int i = 0; i < checkPointBuilders.size(); i++) {
      @Nullable ImmutableList.Builder<AdaptationCheckpoint> builder = checkPointBuilders.get(i);
      if (builder == null) {
        continue;
      }
      builder.add(
          new AdaptationCheckpoint(
              /* totalBandwidth= */ totalBitrate, /* allocatedBandwidth= */ checkpointBitrates[i]));
    }
  }

  /** Checkpoint to determine allocated bandwidth. */
  public static final class AdaptationCheckpoint {

    /** Total bandwidth in bits per second at which this checkpoint applies. */
    public final long totalBandwidth;
    /** Allocated bandwidth at this checkpoint in bits per second. */
    public final long allocatedBandwidth;

    public AdaptationCheckpoint(long totalBandwidth, long allocatedBandwidth) {
      this.totalBandwidth = totalBandwidth;
      this.allocatedBandwidth = allocatedBandwidth;
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof AdaptationCheckpoint)) {
        return false;
      }
      AdaptationCheckpoint that = (AdaptationCheckpoint) o;
      return totalBandwidth == that.totalBandwidth && allocatedBandwidth == that.allocatedBandwidth;
    }

    @Override
    public int hashCode() {
      return 31 * (int) totalBandwidth + (int) allocatedBandwidth;
    }
  }
}
