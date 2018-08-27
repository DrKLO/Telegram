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
package com.google.android.exoplayer2;

import android.util.Log;
import com.google.android.exoplayer2.source.ClippingMediaPeriod;
import com.google.android.exoplayer2.source.EmptySampleStream;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectorResult;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.util.Assertions;

/** Holds a {@link MediaPeriod} with information required to play it as part of a timeline. */
/* package */ final class MediaPeriodHolder {

  private static final String TAG = "MediaPeriodHolder";

  public final MediaPeriod mediaPeriod;
  public final Object uid;
  public final SampleStream[] sampleStreams;
  public final boolean[] mayRetainStreamFlags;

  public long rendererPositionOffsetUs;
  public boolean prepared;
  public boolean hasEnabledTracks;
  public MediaPeriodInfo info;
  public MediaPeriodHolder next;
  public TrackGroupArray trackGroups;
  public TrackSelectorResult trackSelectorResult;

  private final RendererCapabilities[] rendererCapabilities;
  private final TrackSelector trackSelector;
  private final MediaSource mediaSource;

  private TrackSelectorResult periodTrackSelectorResult;

  /**
   * Creates a new holder with information required to play it as part of a timeline.
   *
   * @param rendererCapabilities The renderer capabilities.
   * @param rendererPositionOffsetUs The time offset of the start of the media period to provide to
   *     renderers.
   * @param trackSelector The track selector.
   * @param allocator The allocator.
   * @param mediaSource The media source that produced the media period.
   * @param uid The unique identifier for the containing timeline period.
   * @param info Information used to identify this media period in its timeline period.
   */
  public MediaPeriodHolder(
      RendererCapabilities[] rendererCapabilities,
      long rendererPositionOffsetUs,
      TrackSelector trackSelector,
      Allocator allocator,
      MediaSource mediaSource,
      Object uid,
      MediaPeriodInfo info) {
    this.rendererCapabilities = rendererCapabilities;
    this.rendererPositionOffsetUs = rendererPositionOffsetUs - info.startPositionUs;
    this.trackSelector = trackSelector;
    this.mediaSource = mediaSource;
    this.uid = Assertions.checkNotNull(uid);
    this.info = info;
    sampleStreams = new SampleStream[rendererCapabilities.length];
    mayRetainStreamFlags = new boolean[rendererCapabilities.length];
    MediaPeriod mediaPeriod = mediaSource.createPeriod(info.id, allocator);
    if (info.id.endPositionUs != C.TIME_END_OF_SOURCE) {
      mediaPeriod =
          new ClippingMediaPeriod(
              mediaPeriod,
              /* enableInitialDiscontinuity= */ true,
              /* startUs= */ 0,
              info.id.endPositionUs);
    }
    this.mediaPeriod = mediaPeriod;
  }

  public long toRendererTime(long periodTimeUs) {
    return periodTimeUs + getRendererOffset();
  }

  public long toPeriodTime(long rendererTimeUs) {
    return rendererTimeUs - getRendererOffset();
  }

  public long getRendererOffset() {
    return rendererPositionOffsetUs;
  }

  public boolean isFullyBuffered() {
    return prepared
        && (!hasEnabledTracks || mediaPeriod.getBufferedPositionUs() == C.TIME_END_OF_SOURCE);
  }

  public long getDurationUs() {
    return info.durationUs;
  }

  /**
   * Returns the buffered position in microseconds. If the period is buffered to the end then
   * {@link C#TIME_END_OF_SOURCE} is returned unless {@code convertEosToDuration} is true, in which
   * case the period duration is returned.
   *
   * @param convertEosToDuration Whether to return the period duration rather than
   *     {@link C#TIME_END_OF_SOURCE} if the period is fully buffered.
   * @return The buffered position in microseconds.
   */
  public long getBufferedPositionUs(boolean convertEosToDuration) {
    if (!prepared) {
      return info.startPositionUs;
    }
    long bufferedPositionUs =
        hasEnabledTracks ? mediaPeriod.getBufferedPositionUs() : C.TIME_END_OF_SOURCE;
    return bufferedPositionUs == C.TIME_END_OF_SOURCE && convertEosToDuration
        ? info.durationUs
        : bufferedPositionUs;
  }

  public long getNextLoadPositionUs() {
    return !prepared ? 0 : mediaPeriod.getNextLoadPositionUs();
  }

  public void handlePrepared(float playbackSpeed) throws ExoPlaybackException {
    prepared = true;
    trackGroups = mediaPeriod.getTrackGroups();
    selectTracks(playbackSpeed);
    long newStartPositionUs = applyTrackSelection(info.startPositionUs, false);
    rendererPositionOffsetUs += info.startPositionUs - newStartPositionUs;
    info = info.copyWithStartPositionUs(newStartPositionUs);
  }

  public void reevaluateBuffer(long rendererPositionUs) {
    if (prepared) {
      mediaPeriod.reevaluateBuffer(toPeriodTime(rendererPositionUs));
    }
  }

  public void continueLoading(long rendererPositionUs) {
    long loadingPeriodPositionUs = toPeriodTime(rendererPositionUs);
    mediaPeriod.continueLoading(loadingPeriodPositionUs);
  }

  public boolean selectTracks(float playbackSpeed) throws ExoPlaybackException {
    TrackSelectorResult selectorResult =
        trackSelector.selectTracks(rendererCapabilities, trackGroups);
    if (selectorResult.isEquivalent(periodTrackSelectorResult)) {
      return false;
    }
    trackSelectorResult = selectorResult;
    for (TrackSelection trackSelection : trackSelectorResult.selections.getAll()) {
      if (trackSelection != null) {
        trackSelection.onPlaybackSpeed(playbackSpeed);
      }
    }
    return true;
  }

  public long applyTrackSelection(long positionUs, boolean forceRecreateStreams) {
    return applyTrackSelection(
        positionUs, forceRecreateStreams, new boolean[rendererCapabilities.length]);
  }

  public long applyTrackSelection(
      long positionUs, boolean forceRecreateStreams, boolean[] streamResetFlags) {
    for (int i = 0; i < trackSelectorResult.length; i++) {
      mayRetainStreamFlags[i] =
          !forceRecreateStreams && trackSelectorResult.isEquivalent(periodTrackSelectorResult, i);
    }

    // Undo the effect of previous call to associate no-sample renderers with empty tracks
    // so the mediaPeriod receives back whatever it sent us before.
    disassociateNoSampleRenderersWithEmptySampleStream(sampleStreams);
    updatePeriodTrackSelectorResult(trackSelectorResult);
    // Disable streams on the period and get new streams for updated/newly-enabled tracks.
    TrackSelectionArray trackSelections = trackSelectorResult.selections;
    positionUs =
        mediaPeriod.selectTracks(
            trackSelections.getAll(),
            mayRetainStreamFlags,
            sampleStreams,
            streamResetFlags,
            positionUs);
    associateNoSampleRenderersWithEmptySampleStream(sampleStreams);

    // Update whether we have enabled tracks and sanity check the expected streams are non-null.
    hasEnabledTracks = false;
    for (int i = 0; i < sampleStreams.length; i++) {
      if (sampleStreams[i] != null) {
        Assertions.checkState(trackSelectorResult.isRendererEnabled(i));
        // hasEnabledTracks should be true only when non-empty streams exists.
        if (rendererCapabilities[i].getTrackType() != C.TRACK_TYPE_NONE) {
          hasEnabledTracks = true;
        }
      } else {
        Assertions.checkState(trackSelections.get(i) == null);
      }
    }
    return positionUs;
  }

  public void release() {
    updatePeriodTrackSelectorResult(null);
    try {
      if (info.id.endPositionUs != C.TIME_END_OF_SOURCE) {
        mediaSource.releasePeriod(((ClippingMediaPeriod) mediaPeriod).mediaPeriod);
      } else {
        mediaSource.releasePeriod(mediaPeriod);
      }
    } catch (RuntimeException e) {
      // There's nothing we can do.
      Log.e(TAG, "Period release failed.", e);
    }
  }

  private void updatePeriodTrackSelectorResult(TrackSelectorResult trackSelectorResult) {
    if (periodTrackSelectorResult != null) {
      disableTrackSelectionsInResult(periodTrackSelectorResult);
    }
    periodTrackSelectorResult = trackSelectorResult;
    if (periodTrackSelectorResult != null) {
      enableTrackSelectionsInResult(periodTrackSelectorResult);
    }
  }

  private void enableTrackSelectionsInResult(TrackSelectorResult trackSelectorResult) {
    for (int i = 0; i < trackSelectorResult.length; i++) {
      boolean rendererEnabled = trackSelectorResult.isRendererEnabled(i);
      TrackSelection trackSelection = trackSelectorResult.selections.get(i);
      if (rendererEnabled && trackSelection != null) {
        trackSelection.enable();
      }
    }
  }

  private void disableTrackSelectionsInResult(TrackSelectorResult trackSelectorResult) {
    for (int i = 0; i < trackSelectorResult.length; i++) {
      boolean rendererEnabled = trackSelectorResult.isRendererEnabled(i);
      TrackSelection trackSelection = trackSelectorResult.selections.get(i);
      if (rendererEnabled && trackSelection != null) {
        trackSelection.disable();
      }
    }
  }

  /**
   * For each renderer of type {@link C#TRACK_TYPE_NONE}, we will remove the dummy {@link
   * EmptySampleStream} that was associated with it.
   */
  private void disassociateNoSampleRenderersWithEmptySampleStream(SampleStream[] sampleStreams) {
    for (int i = 0; i < rendererCapabilities.length; i++) {
      if (rendererCapabilities[i].getTrackType() == C.TRACK_TYPE_NONE) {
        sampleStreams[i] = null;
      }
    }
  }

  /**
   * For each renderer of type {@link C#TRACK_TYPE_NONE} that was enabled, we will associate it with
   * a dummy {@link EmptySampleStream}.
   */
  private void associateNoSampleRenderersWithEmptySampleStream(SampleStream[] sampleStreams) {
    for (int i = 0; i < rendererCapabilities.length; i++) {
      if (rendererCapabilities[i].getTrackType() == C.TRACK_TYPE_NONE
          && trackSelectorResult.isRendererEnabled(i)) {
        sampleStreams[i] = new EmptySampleStream();
      }
    }
  }
}
