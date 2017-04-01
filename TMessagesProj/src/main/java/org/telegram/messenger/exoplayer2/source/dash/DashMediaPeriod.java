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
package org.telegram.messenger.exoplayer2.source.dash;

import org.telegram.messenger.exoplayer2.C;
import org.telegram.messenger.exoplayer2.Format;
import org.telegram.messenger.exoplayer2.source.AdaptiveMediaSourceEventListener.EventDispatcher;
import org.telegram.messenger.exoplayer2.source.CompositeSequenceableLoader;
import org.telegram.messenger.exoplayer2.source.MediaPeriod;
import org.telegram.messenger.exoplayer2.source.SampleStream;
import org.telegram.messenger.exoplayer2.source.SequenceableLoader;
import org.telegram.messenger.exoplayer2.source.TrackGroup;
import org.telegram.messenger.exoplayer2.source.TrackGroupArray;
import org.telegram.messenger.exoplayer2.source.chunk.ChunkSampleStream;
import org.telegram.messenger.exoplayer2.source.dash.manifest.AdaptationSet;
import org.telegram.messenger.exoplayer2.source.dash.manifest.DashManifest;
import org.telegram.messenger.exoplayer2.source.dash.manifest.Period;
import org.telegram.messenger.exoplayer2.source.dash.manifest.Representation;
import org.telegram.messenger.exoplayer2.trackselection.TrackSelection;
import org.telegram.messenger.exoplayer2.upstream.Allocator;
import org.telegram.messenger.exoplayer2.upstream.LoaderErrorThrower;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A DASH {@link MediaPeriod}.
 */
/* package */ final class DashMediaPeriod implements MediaPeriod,
    SequenceableLoader.Callback<ChunkSampleStream<DashChunkSource>> {

  /* package */ final int id;
  private final DashChunkSource.Factory chunkSourceFactory;
  private final int minLoadableRetryCount;
  private final EventDispatcher eventDispatcher;
  private final long elapsedRealtimeOffset;
  private final LoaderErrorThrower manifestLoaderErrorThrower;
  private final Allocator allocator;
  private final TrackGroupArray trackGroups;

  private Callback callback;
  private ChunkSampleStream<DashChunkSource>[] sampleStreams;
  private CompositeSequenceableLoader sequenceableLoader;
  private DashManifest manifest;
  private int index;
  private Period period;

  public DashMediaPeriod(int id, DashManifest manifest, int index,
      DashChunkSource.Factory chunkSourceFactory,  int minLoadableRetryCount,
      EventDispatcher eventDispatcher, long elapsedRealtimeOffset,
      LoaderErrorThrower manifestLoaderErrorThrower, Allocator allocator) {
    this.id = id;
    this.manifest = manifest;
    this.index = index;
    this.chunkSourceFactory = chunkSourceFactory;
    this.minLoadableRetryCount = minLoadableRetryCount;
    this.eventDispatcher = eventDispatcher;
    this.elapsedRealtimeOffset = elapsedRealtimeOffset;
    this.manifestLoaderErrorThrower = manifestLoaderErrorThrower;
    this.allocator = allocator;
    sampleStreams = newSampleStreamArray(0);
    sequenceableLoader = new CompositeSequenceableLoader(sampleStreams);
    period = manifest.getPeriod(index);
    trackGroups = buildTrackGroups(period);
  }

  public void updateManifest(DashManifest manifest, int index) {
    this.manifest = manifest;
    this.index = index;
    period = manifest.getPeriod(index);
    if (sampleStreams != null) {
      for (ChunkSampleStream<DashChunkSource> sampleStream : sampleStreams) {
        sampleStream.getChunkSource().updateManifest(manifest, index);
      }
      callback.onContinueLoadingRequested(this);
    }
  }

  public void release() {
    for (ChunkSampleStream<DashChunkSource> sampleStream : sampleStreams) {
      sampleStream.release();
    }
  }

  @Override
  public void prepare(Callback callback) {
    this.callback = callback;
    callback.onPrepared(this);
  }

  @Override
  public void maybeThrowPrepareError() throws IOException {
    manifestLoaderErrorThrower.maybeThrowError();
  }

  @Override
  public TrackGroupArray getTrackGroups() {
    return trackGroups;
  }

  @Override
  public long selectTracks(TrackSelection[] selections, boolean[] mayRetainStreamFlags,
      SampleStream[] streams, boolean[] streamResetFlags, long positionUs) {
    ArrayList<ChunkSampleStream<DashChunkSource>> sampleStreamsList = new ArrayList<>();
    for (int i = 0; i < selections.length; i++) {
      if (streams[i] != null) {
        @SuppressWarnings("unchecked")
        ChunkSampleStream<DashChunkSource> stream = (ChunkSampleStream<DashChunkSource>) streams[i];
        if (selections[i] == null || !mayRetainStreamFlags[i]) {
          stream.release();
          streams[i] = null;
        } else {
          sampleStreamsList.add(stream);
        }
      }
      if (streams[i] == null && selections[i] != null) {
        ChunkSampleStream<DashChunkSource> stream = buildSampleStream(selections[i], positionUs);
        sampleStreamsList.add(stream);
        streams[i] = stream;
        streamResetFlags[i] = true;
      }
    }
    sampleStreams = newSampleStreamArray(sampleStreamsList.size());
    sampleStreamsList.toArray(sampleStreams);
    sequenceableLoader = new CompositeSequenceableLoader(sampleStreams);
    return positionUs;
  }

  @Override
  public boolean continueLoading(long positionUs) {
    return sequenceableLoader.continueLoading(positionUs);
  }

  @Override
  public long getNextLoadPositionUs() {
    return sequenceableLoader.getNextLoadPositionUs();
  }

  @Override
  public long readDiscontinuity() {
    return C.TIME_UNSET;
  }

  @Override
  public long getBufferedPositionUs() {
    long bufferedPositionUs = Long.MAX_VALUE;
    for (ChunkSampleStream<DashChunkSource> sampleStream : sampleStreams) {
      long rendererBufferedPositionUs = sampleStream.getBufferedPositionUs();
      if (rendererBufferedPositionUs != C.TIME_END_OF_SOURCE) {
        bufferedPositionUs = Math.min(bufferedPositionUs, rendererBufferedPositionUs);
      }
    }
    return bufferedPositionUs == Long.MAX_VALUE ? C.TIME_END_OF_SOURCE : bufferedPositionUs;
  }

  @Override
  public long seekToUs(long positionUs) {
    for (ChunkSampleStream<DashChunkSource> sampleStream : sampleStreams) {
      sampleStream.seekToUs(positionUs);
    }
    return positionUs;
  }

  // SequenceableLoader.Callback implementation.

  @Override
  public void onContinueLoadingRequested(ChunkSampleStream<DashChunkSource> sampleStream) {
    callback.onContinueLoadingRequested(this);
  }

  // Internal methods.

  private static TrackGroupArray buildTrackGroups(Period period) {
    TrackGroup[] trackGroupArray = new TrackGroup[period.adaptationSets.size()];
    for (int i = 0; i < period.adaptationSets.size(); i++) {
      AdaptationSet adaptationSet = period.adaptationSets.get(i);
      List<Representation> representations = adaptationSet.representations;
      Format[] formats = new Format[representations.size()];
      for (int j = 0; j < formats.length; j++) {
        formats[j] = representations.get(j).format;
      }
      trackGroupArray[i] = new TrackGroup(formats);
    }
    return new TrackGroupArray(trackGroupArray);
  }

  private ChunkSampleStream<DashChunkSource> buildSampleStream(TrackSelection selection,
      long positionUs) {
    int adaptationSetIndex = trackGroups.indexOf(selection.getTrackGroup());
    AdaptationSet adaptationSet = period.adaptationSets.get(adaptationSetIndex);
    DashChunkSource chunkSource = chunkSourceFactory.createDashChunkSource(
        manifestLoaderErrorThrower, manifest, index, adaptationSetIndex, selection,
        elapsedRealtimeOffset);
    return new ChunkSampleStream<>(adaptationSet.type, chunkSource, this, allocator, positionUs,
        minLoadableRetryCount, eventDispatcher);
  }

  @SuppressWarnings("unchecked")
  private static ChunkSampleStream<DashChunkSource>[] newSampleStreamArray(int length) {
    return new ChunkSampleStream[length];
  }

}
