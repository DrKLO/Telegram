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

import android.util.Pair;
import android.util.SparseIntArray;
import org.telegram.messenger.exoplayer2.C;
import org.telegram.messenger.exoplayer2.Format;
import org.telegram.messenger.exoplayer2.source.AdaptiveMediaSourceEventListener.EventDispatcher;
import org.telegram.messenger.exoplayer2.source.CompositeSequenceableLoader;
import org.telegram.messenger.exoplayer2.source.EmptySampleStream;
import org.telegram.messenger.exoplayer2.source.MediaPeriod;
import org.telegram.messenger.exoplayer2.source.SampleStream;
import org.telegram.messenger.exoplayer2.source.SequenceableLoader;
import org.telegram.messenger.exoplayer2.source.TrackGroup;
import org.telegram.messenger.exoplayer2.source.TrackGroupArray;
import org.telegram.messenger.exoplayer2.source.chunk.ChunkSampleStream;
import org.telegram.messenger.exoplayer2.source.chunk.ChunkSampleStream.EmbeddedSampleStream;
import org.telegram.messenger.exoplayer2.source.dash.manifest.AdaptationSet;
import org.telegram.messenger.exoplayer2.source.dash.manifest.DashManifest;
import org.telegram.messenger.exoplayer2.source.dash.manifest.Descriptor;
import org.telegram.messenger.exoplayer2.source.dash.manifest.Representation;
import org.telegram.messenger.exoplayer2.trackselection.TrackSelection;
import org.telegram.messenger.exoplayer2.upstream.Allocator;
import org.telegram.messenger.exoplayer2.upstream.LoaderErrorThrower;
import org.telegram.messenger.exoplayer2.util.MimeTypes;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
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
  private final TrackGroupInfo[] trackGroupInfos;

  private Callback callback;
  private ChunkSampleStream<DashChunkSource>[] sampleStreams;
  private CompositeSequenceableLoader sequenceableLoader;
  private DashManifest manifest;
  private int periodIndex;
  private List<AdaptationSet> adaptationSets;

  public DashMediaPeriod(int id, DashManifest manifest, int periodIndex,
      DashChunkSource.Factory chunkSourceFactory,  int minLoadableRetryCount,
      EventDispatcher eventDispatcher, long elapsedRealtimeOffset,
      LoaderErrorThrower manifestLoaderErrorThrower, Allocator allocator) {
    this.id = id;
    this.manifest = manifest;
    this.periodIndex = periodIndex;
    this.chunkSourceFactory = chunkSourceFactory;
    this.minLoadableRetryCount = minLoadableRetryCount;
    this.eventDispatcher = eventDispatcher;
    this.elapsedRealtimeOffset = elapsedRealtimeOffset;
    this.manifestLoaderErrorThrower = manifestLoaderErrorThrower;
    this.allocator = allocator;
    sampleStreams = newSampleStreamArray(0);
    sequenceableLoader = new CompositeSequenceableLoader(sampleStreams);
    adaptationSets = manifest.getPeriod(periodIndex).adaptationSets;
    Pair<TrackGroupArray, TrackGroupInfo[]> result = buildTrackGroups(adaptationSets);
    trackGroups = result.first;
    trackGroupInfos = result.second;
  }

  public void updateManifest(DashManifest manifest, int periodIndex) {
    this.manifest = manifest;
    this.periodIndex = periodIndex;
    adaptationSets = manifest.getPeriod(periodIndex).adaptationSets;
    if (sampleStreams != null) {
      for (ChunkSampleStream<DashChunkSource> sampleStream : sampleStreams) {
        sampleStream.getChunkSource().updateManifest(manifest, periodIndex);
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
  public void prepare(Callback callback, long positionUs) {
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
    HashMap<Integer, ChunkSampleStream<DashChunkSource>> primarySampleStreams = new HashMap<>();
    // First pass for primary tracks.
    for (int i = 0; i < selections.length; i++) {
      if (streams[i] instanceof ChunkSampleStream) {
        @SuppressWarnings("unchecked")
        ChunkSampleStream<DashChunkSource> stream = (ChunkSampleStream<DashChunkSource>) streams[i];
        if (selections[i] == null || !mayRetainStreamFlags[i]) {
          stream.release();
          streams[i] = null;
        } else {
          int trackGroupIndex = trackGroups.indexOf(selections[i].getTrackGroup());
          primarySampleStreams.put(trackGroupIndex, stream);
        }
      }
      if (streams[i] == null && selections[i] != null) {
        int trackGroupIndex = trackGroups.indexOf(selections[i].getTrackGroup());
        TrackGroupInfo trackGroupInfo = trackGroupInfos[trackGroupIndex];
        if (trackGroupInfo.isPrimary) {
          ChunkSampleStream<DashChunkSource> stream = buildSampleStream(trackGroupInfo,
              selections[i], positionUs);
          primarySampleStreams.put(trackGroupIndex, stream);
          streams[i] = stream;
          streamResetFlags[i] = true;
        }
      }
    }
    // Second pass for embedded tracks.
    for (int i = 0; i < selections.length; i++) {
      if ((streams[i] instanceof EmbeddedSampleStream || streams[i] instanceof EmptySampleStream)
          && (selections[i] == null || !mayRetainStreamFlags[i])) {
        // The stream is for an embedded track and is either no longer selected or needs replacing.
        releaseIfEmbeddedSampleStream(streams[i]);
        streams[i] = null;
      }
      // We need to consider replacing the stream even if it's non-null because the primary stream
      // may have been replaced, selected or deselected.
      if (selections[i] != null) {
        int trackGroupIndex = trackGroups.indexOf(selections[i].getTrackGroup());
        TrackGroupInfo trackGroupInfo = trackGroupInfos[trackGroupIndex];
        if (!trackGroupInfo.isPrimary) {
          ChunkSampleStream<?> primaryStream = primarySampleStreams.get(
              trackGroupInfo.primaryTrackGroupIndex);
          SampleStream stream = streams[i];
          boolean mayRetainStream = primaryStream == null ? stream instanceof EmptySampleStream
              : (stream instanceof EmbeddedSampleStream
                  && ((EmbeddedSampleStream) stream).parent == primaryStream);
          if (!mayRetainStream) {
            releaseIfEmbeddedSampleStream(stream);
            streams[i] = primaryStream == null ? new EmptySampleStream()
                : primaryStream.selectEmbeddedTrack(positionUs, trackGroupInfo.trackType);
            streamResetFlags[i] = true;
          }
        }
      }
    }
    sampleStreams = newSampleStreamArray(primarySampleStreams.size());
    primarySampleStreams.values().toArray(sampleStreams);
    sequenceableLoader = new CompositeSequenceableLoader(sampleStreams);
    return positionUs;
  }

  @Override
  public void discardBuffer(long positionUs) {
    for (ChunkSampleStream<DashChunkSource> sampleStream : sampleStreams) {
      sampleStream.discardEmbeddedTracksTo(positionUs);
    }
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
    return sequenceableLoader.getBufferedPositionUs();
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

  private static Pair<TrackGroupArray, TrackGroupInfo[]> buildTrackGroups(
      List<AdaptationSet> adaptationSets) {
    int[][] groupedAdaptationSetIndices = getGroupedAdaptationSetIndices(adaptationSets);

    int primaryGroupCount = groupedAdaptationSetIndices.length;
    boolean[] primaryGroupHasEventMessageTrackFlags = new boolean[primaryGroupCount];
    boolean[] primaryGroupHasCea608TrackFlags = new boolean[primaryGroupCount];
    int totalGroupCount = primaryGroupCount;
    for (int i = 0; i < primaryGroupCount; i++) {
      if (hasEventMessageTrack(adaptationSets, groupedAdaptationSetIndices[i])) {
        primaryGroupHasEventMessageTrackFlags[i] = true;
        totalGroupCount++;
      }
      if (hasCea608Track(adaptationSets, groupedAdaptationSetIndices[i])) {
        primaryGroupHasCea608TrackFlags[i] = true;
        totalGroupCount++;
      }
    }

    TrackGroup[] trackGroups = new TrackGroup[totalGroupCount];
    TrackGroupInfo[] trackGroupInfos = new TrackGroupInfo[totalGroupCount];

    int trackGroupCount = 0;
    for (int i = 0; i < primaryGroupCount; i++) {
      int[] adaptationSetIndices = groupedAdaptationSetIndices[i];
      List<Representation> representations = new ArrayList<>();
      for (int adaptationSetIndex : adaptationSetIndices) {
        representations.addAll(adaptationSets.get(adaptationSetIndex).representations);
      }
      Format[] formats = new Format[representations.size()];
      for (int j = 0; j < formats.length; j++) {
        formats[j] = representations.get(j).format;
      }

      AdaptationSet firstAdaptationSet = adaptationSets.get(adaptationSetIndices[0]);
      int primaryTrackGroupIndex = trackGroupCount;
      boolean hasEventMessageTrack = primaryGroupHasEventMessageTrackFlags[i];
      boolean hasCea608Track = primaryGroupHasCea608TrackFlags[i];

      trackGroups[trackGroupCount] = new TrackGroup(formats);
      trackGroupInfos[trackGroupCount++] = new TrackGroupInfo(firstAdaptationSet.type,
          adaptationSetIndices, primaryTrackGroupIndex, true, hasEventMessageTrack, hasCea608Track);
      if (hasEventMessageTrack) {
        Format format = Format.createSampleFormat(firstAdaptationSet.id + ":emsg",
            MimeTypes.APPLICATION_EMSG, null, Format.NO_VALUE, null);
        trackGroups[trackGroupCount] = new TrackGroup(format);
        trackGroupInfos[trackGroupCount++] = new TrackGroupInfo(C.TRACK_TYPE_METADATA,
            adaptationSetIndices, primaryTrackGroupIndex, false, false, false);
      }
      if (hasCea608Track) {
        Format format = Format.createTextSampleFormat(firstAdaptationSet.id + ":cea608",
            MimeTypes.APPLICATION_CEA608, 0, null);
        trackGroups[trackGroupCount] = new TrackGroup(format);
        trackGroupInfos[trackGroupCount++] = new TrackGroupInfo(C.TRACK_TYPE_TEXT,
            adaptationSetIndices, primaryTrackGroupIndex, false, false, false);
      }
    }

    return Pair.create(new TrackGroupArray(trackGroups), trackGroupInfos);
  }

  private static int[][] getGroupedAdaptationSetIndices(List<AdaptationSet> adaptationSets) {
    int adaptationSetCount = adaptationSets.size();
    SparseIntArray idToIndexMap = new SparseIntArray(adaptationSetCount);
    for (int i = 0; i < adaptationSetCount; i++) {
      idToIndexMap.put(adaptationSets.get(i).id, i);
    }

    int[][] groupedAdaptationSetIndices = new int[adaptationSetCount][];
    boolean[] adaptationSetUsedFlags = new boolean[adaptationSetCount];

    int groupCount = 0;
    for (int i = 0; i < adaptationSetCount; i++) {
      if (adaptationSetUsedFlags[i]) {
        // This adaptation set has already been included in a group.
        continue;
      }
      adaptationSetUsedFlags[i] = true;
      Descriptor adaptationSetSwitchingProperty = findAdaptationSetSwitchingProperty(
          adaptationSets.get(i).supplementalProperties);
      if (adaptationSetSwitchingProperty == null) {
        groupedAdaptationSetIndices[groupCount++] = new int[] {i};
      } else {
        String[] extraAdaptationSetIds = adaptationSetSwitchingProperty.value.split(",");
        int[] adaptationSetIndices = new int[1 + extraAdaptationSetIds.length];
        adaptationSetIndices[0] = i;
        for (int j = 0; j < extraAdaptationSetIds.length; j++) {
          int extraIndex = idToIndexMap.get(Integer.parseInt(extraAdaptationSetIds[j]));
          adaptationSetUsedFlags[extraIndex] = true;
          adaptationSetIndices[1 + j] = extraIndex;
        }
        groupedAdaptationSetIndices[groupCount++] = adaptationSetIndices;
      }
    }

    return groupCount < adaptationSetCount
        ? Arrays.copyOf(groupedAdaptationSetIndices, groupCount) : groupedAdaptationSetIndices;
  }

  private ChunkSampleStream<DashChunkSource> buildSampleStream(TrackGroupInfo trackGroupInfo,
      TrackSelection selection, long positionUs) {
    int embeddedTrackCount = 0;
    int[] embeddedTrackTypes = new int[2];
    boolean enableEventMessageTrack = trackGroupInfo.hasEmbeddedEventMessageTrack;
    if (enableEventMessageTrack) {
      embeddedTrackTypes[embeddedTrackCount++] = C.TRACK_TYPE_METADATA;
    }
    boolean enableCea608Track = trackGroupInfo.hasEmbeddedCea608Track;
    if (enableCea608Track) {
      embeddedTrackTypes[embeddedTrackCount++] = C.TRACK_TYPE_TEXT;
    }
    if (embeddedTrackCount < embeddedTrackTypes.length) {
      embeddedTrackTypes = Arrays.copyOf(embeddedTrackTypes, embeddedTrackCount);
    }
    DashChunkSource chunkSource = chunkSourceFactory.createDashChunkSource(
        manifestLoaderErrorThrower, manifest, periodIndex, trackGroupInfo.adaptationSetIndices,
        selection, trackGroupInfo.trackType, elapsedRealtimeOffset, enableEventMessageTrack,
        enableCea608Track);
    ChunkSampleStream<DashChunkSource> stream = new ChunkSampleStream<>(trackGroupInfo.trackType,
        embeddedTrackTypes, chunkSource, this, allocator, positionUs, minLoadableRetryCount,
        eventDispatcher);
    return stream;
  }

  private static Descriptor findAdaptationSetSwitchingProperty(List<Descriptor> descriptors) {
    for (int i = 0; i < descriptors.size(); i++) {
      Descriptor descriptor = descriptors.get(i);
      if ("urn:mpeg:dash:adaptation-set-switching:2016".equals(descriptor.schemeIdUri)) {
        return descriptor;
      }
    }
    return null;
  }

  private static boolean hasEventMessageTrack(List<AdaptationSet> adaptationSets,
      int[] adaptationSetIndices) {
    for (int i : adaptationSetIndices) {
      List<Representation> representations = adaptationSets.get(i).representations;
      for (int j = 0; j < representations.size(); j++) {
        Representation representation = representations.get(j);
        if (!representation.inbandEventStreams.isEmpty()) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean hasCea608Track(List<AdaptationSet> adaptationSets,
      int[] adaptationSetIndices) {
    for (int i : adaptationSetIndices) {
      List<Descriptor> descriptors = adaptationSets.get(i).accessibilityDescriptors;
      for (int j = 0; j < descriptors.size(); j++) {
        Descriptor descriptor = descriptors.get(j);
        if ("urn:scte:dash:cc:cea-608:2015".equals(descriptor.schemeIdUri)) {
          return true;
        }
      }
    }
    return false;
  }

  @SuppressWarnings("unchecked")
  private static ChunkSampleStream<DashChunkSource>[] newSampleStreamArray(int length) {
    return new ChunkSampleStream[length];
  }

  private static void releaseIfEmbeddedSampleStream(SampleStream sampleStream) {
    if (sampleStream instanceof EmbeddedSampleStream) {
      ((EmbeddedSampleStream) sampleStream).release();
    }
  }

  private static final class TrackGroupInfo {

    public final int[] adaptationSetIndices;
    public final int trackType;
    public final boolean isPrimary;

    public final int primaryTrackGroupIndex;
    public final boolean hasEmbeddedEventMessageTrack;
    public final boolean hasEmbeddedCea608Track;

    public TrackGroupInfo(int trackType, int[] adaptationSetIndices, int primaryTrackGroupIndex,
        boolean isPrimary, boolean hasEmbeddedEventMessageTrack, boolean hasEmbeddedCea608Track) {
      this.trackType = trackType;
      this.adaptationSetIndices = adaptationSetIndices;
      this.primaryTrackGroupIndex = primaryTrackGroupIndex;
      this.isPrimary = isPrimary;
      this.hasEmbeddedEventMessageTrack = hasEmbeddedEventMessageTrack;
      this.hasEmbeddedCea608Track = hasEmbeddedCea608Track;
    }

  }

}
