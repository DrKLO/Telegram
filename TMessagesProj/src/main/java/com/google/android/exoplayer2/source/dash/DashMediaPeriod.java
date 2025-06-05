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
package com.google.android.exoplayer2.source.dash;

import static java.lang.Math.min;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.util.Pair;
import android.util.SparseArray;
import android.util.SparseIntArray;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.analytics.PlayerId;
import com.google.android.exoplayer2.drm.DrmSessionEventListener;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.offline.StreamKey;
import com.google.android.exoplayer2.source.CompositeSequenceableLoaderFactory;
import com.google.android.exoplayer2.source.EmptySampleStream;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSourceEventListener;
import com.google.android.exoplayer2.source.MediaSourceEventListener.EventDispatcher;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.source.SequenceableLoader;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.chunk.ChunkSampleStream;
import com.google.android.exoplayer2.source.chunk.ChunkSampleStream.EmbeddedSampleStream;
import com.google.android.exoplayer2.source.dash.PlayerEmsgHandler.PlayerEmsgCallback;
import com.google.android.exoplayer2.source.dash.PlayerEmsgHandler.PlayerTrackEmsgHandler;
import com.google.android.exoplayer2.source.dash.manifest.AdaptationSet;
import com.google.android.exoplayer2.source.dash.manifest.DashManifest;
import com.google.android.exoplayer2.source.dash.manifest.Descriptor;
import com.google.android.exoplayer2.source.dash.manifest.EventStream;
import com.google.android.exoplayer2.source.dash.manifest.Period;
import com.google.android.exoplayer2.source.dash.manifest.Representation;
import com.google.android.exoplayer2.trackselection.ExoTrackSelection;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.LoaderErrorThrower;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import com.google.common.primitives.Ints;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.checkerframework.checker.nullness.compatqual.NullableType;

/** A DASH {@link MediaPeriod}. */
/* package */ final class DashMediaPeriod
    implements MediaPeriod,
        SequenceableLoader.Callback<ChunkSampleStream<DashChunkSource>>,
        ChunkSampleStream.ReleaseCallback<DashChunkSource> {

  // Defined by ANSI/SCTE 214-1 2016 7.2.3.
  private static final Pattern CEA608_SERVICE_DESCRIPTOR_REGEX = Pattern.compile("CC([1-4])=(.+)");
  // Defined by ANSI/SCTE 214-1 2016 7.2.2.
  private static final Pattern CEA708_SERVICE_DESCRIPTOR_REGEX =
      Pattern.compile("([1-4])=lang:(\\w+)(,.+)?");

  /* package */ final int id;
  private final DashChunkSource.Factory chunkSourceFactory;
  @Nullable private final TransferListener transferListener;
  private final DrmSessionManager drmSessionManager;
  private final LoadErrorHandlingPolicy loadErrorHandlingPolicy;
  private final BaseUrlExclusionList baseUrlExclusionList;
  private final long elapsedRealtimeOffsetMs;
  private final LoaderErrorThrower manifestLoaderErrorThrower;
  private final Allocator allocator;
  private final TrackGroupArray trackGroups;
  private final TrackGroupInfo[] trackGroupInfos;
  private final CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory;
  private final PlayerEmsgHandler playerEmsgHandler;
  private final IdentityHashMap<ChunkSampleStream<DashChunkSource>, PlayerTrackEmsgHandler>
      trackEmsgHandlerBySampleStream;
  private final MediaSourceEventListener.EventDispatcher mediaSourceEventDispatcher;
  private final DrmSessionEventListener.EventDispatcher drmEventDispatcher;
  private final PlayerId playerId;

  @Nullable private Callback callback;
  private ChunkSampleStream<DashChunkSource>[] sampleStreams;
  private EventSampleStream[] eventSampleStreams;
  private SequenceableLoader compositeSequenceableLoader;
  private DashManifest manifest;
  private int periodIndex;
  private List<EventStream> eventStreams;

  public DashMediaPeriod(
      int id,
      DashManifest manifest,
      BaseUrlExclusionList baseUrlExclusionList,
      int periodIndex,
      DashChunkSource.Factory chunkSourceFactory,
      @Nullable TransferListener transferListener,
      DrmSessionManager drmSessionManager,
      DrmSessionEventListener.EventDispatcher drmEventDispatcher,
      LoadErrorHandlingPolicy loadErrorHandlingPolicy,
      EventDispatcher mediaSourceEventDispatcher,
      long elapsedRealtimeOffsetMs,
      LoaderErrorThrower manifestLoaderErrorThrower,
      Allocator allocator,
      CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory,
      PlayerEmsgCallback playerEmsgCallback,
      PlayerId playerId) {
    this.id = id;
    this.manifest = manifest;
    this.baseUrlExclusionList = baseUrlExclusionList;
    this.periodIndex = periodIndex;
    this.chunkSourceFactory = chunkSourceFactory;
    this.transferListener = transferListener;
    this.drmSessionManager = drmSessionManager;
    this.drmEventDispatcher = drmEventDispatcher;
    this.loadErrorHandlingPolicy = loadErrorHandlingPolicy;
    this.mediaSourceEventDispatcher = mediaSourceEventDispatcher;
    this.elapsedRealtimeOffsetMs = elapsedRealtimeOffsetMs;
    this.manifestLoaderErrorThrower = manifestLoaderErrorThrower;
    this.allocator = allocator;
    this.compositeSequenceableLoaderFactory = compositeSequenceableLoaderFactory;
    this.playerId = playerId;
    playerEmsgHandler = new PlayerEmsgHandler(manifest, playerEmsgCallback, allocator);
    sampleStreams = newSampleStreamArray(0);
    eventSampleStreams = new EventSampleStream[0];
    trackEmsgHandlerBySampleStream = new IdentityHashMap<>();
    compositeSequenceableLoader =
        compositeSequenceableLoaderFactory.createCompositeSequenceableLoader(sampleStreams);
    Period period = manifest.getPeriod(periodIndex);
    eventStreams = period.eventStreams;
    Pair<TrackGroupArray, TrackGroupInfo[]> result =
        buildTrackGroups(drmSessionManager, period.adaptationSets, eventStreams);
    trackGroups = result.first;
    trackGroupInfos = result.second;
  }

  /**
   * Updates the {@link DashManifest} and the index of this period in the manifest.
   *
   * @param manifest The updated manifest.
   * @param periodIndex the new index of this period in the updated manifest.
   */
  public void updateManifest(DashManifest manifest, int periodIndex) {
    this.manifest = manifest;
    this.periodIndex = periodIndex;
    playerEmsgHandler.updateManifest(manifest);
    if (sampleStreams != null) {
      for (ChunkSampleStream<DashChunkSource> sampleStream : sampleStreams) {
        sampleStream.getChunkSource().updateManifest(manifest, periodIndex);
      }
      callback.onContinueLoadingRequested(this);
    }
    eventStreams = manifest.getPeriod(periodIndex).eventStreams;
    for (EventSampleStream eventSampleStream : eventSampleStreams) {
      for (EventStream eventStream : eventStreams) {
        if (eventStream.id().equals(eventSampleStream.eventStreamId())) {
          int lastPeriodIndex = manifest.getPeriodCount() - 1;
          eventSampleStream.updateEventStream(
              eventStream,
              /* eventStreamAppendable= */ manifest.dynamic && periodIndex == lastPeriodIndex);
          break;
        }
      }
    }
  }

  public void release() {
    playerEmsgHandler.release();
    for (ChunkSampleStream<DashChunkSource> sampleStream : sampleStreams) {
      sampleStream.release(this);
    }
    callback = null;
  }

  // ChunkSampleStream.ReleaseCallback implementation.

  @Override
  public synchronized void onSampleStreamReleased(ChunkSampleStream<DashChunkSource> stream) {
    PlayerTrackEmsgHandler trackEmsgHandler = trackEmsgHandlerBySampleStream.remove(stream);
    if (trackEmsgHandler != null) {
      trackEmsgHandler.release();
    }
  }

  // MediaPeriod implementation.

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
  public List<StreamKey> getStreamKeys(List<ExoTrackSelection> trackSelections) {
    List<AdaptationSet> manifestAdaptationSets = manifest.getPeriod(periodIndex).adaptationSets;
    List<StreamKey> streamKeys = new ArrayList<>();
    for (ExoTrackSelection trackSelection : trackSelections) {
      int trackGroupIndex = trackGroups.indexOf(trackSelection.getTrackGroup());
      TrackGroupInfo trackGroupInfo = trackGroupInfos[trackGroupIndex];
      if (trackGroupInfo.trackGroupCategory != TrackGroupInfo.CATEGORY_PRIMARY) {
        // Ignore non-primary tracks.
        continue;
      }
      int[] adaptationSetIndices = trackGroupInfo.adaptationSetIndices;
      int[] trackIndices = new int[trackSelection.length()];
      for (int i = 0; i < trackSelection.length(); i++) {
        trackIndices[i] = trackSelection.getIndexInTrackGroup(i);
      }
      Arrays.sort(trackIndices);

      int currentAdaptationSetIndex = 0;
      int totalTracksInPreviousAdaptationSets = 0;
      int tracksInCurrentAdaptationSet =
          manifestAdaptationSets.get(adaptationSetIndices[0]).representations.size();
      for (int trackIndex : trackIndices) {
        while (trackIndex >= totalTracksInPreviousAdaptationSets + tracksInCurrentAdaptationSet) {
          currentAdaptationSetIndex++;
          totalTracksInPreviousAdaptationSets += tracksInCurrentAdaptationSet;
          tracksInCurrentAdaptationSet =
              manifestAdaptationSets
                  .get(adaptationSetIndices[currentAdaptationSetIndex])
                  .representations
                  .size();
        }
        streamKeys.add(
            new StreamKey(
                periodIndex,
                adaptationSetIndices[currentAdaptationSetIndex],
                trackIndex - totalTracksInPreviousAdaptationSets));
      }
    }
    return streamKeys;
  }

  @Override
  public long selectTracks(
      @NullableType ExoTrackSelection[] selections,
      boolean[] mayRetainStreamFlags,
      @NullableType SampleStream[] streams,
      boolean[] streamResetFlags,
      long positionUs) {
    int[] streamIndexToTrackGroupIndex = getStreamIndexToTrackGroupIndex(selections);
    releaseDisabledStreams(selections, mayRetainStreamFlags, streams);
    releaseOrphanEmbeddedStreams(selections, streams, streamIndexToTrackGroupIndex);
    selectNewStreams(
        selections, streams, streamResetFlags, positionUs, streamIndexToTrackGroupIndex);

    ArrayList<ChunkSampleStream<DashChunkSource>> sampleStreamList = new ArrayList<>();
    ArrayList<EventSampleStream> eventSampleStreamList = new ArrayList<>();
    for (SampleStream sampleStream : streams) {
      if (sampleStream instanceof ChunkSampleStream) {
        @SuppressWarnings("unchecked")
        ChunkSampleStream<DashChunkSource> stream =
            (ChunkSampleStream<DashChunkSource>) sampleStream;
        sampleStreamList.add(stream);
      } else if (sampleStream instanceof EventSampleStream) {
        eventSampleStreamList.add((EventSampleStream) sampleStream);
      }
    }
    sampleStreams = newSampleStreamArray(sampleStreamList.size());
    sampleStreamList.toArray(sampleStreams);
    eventSampleStreams = new EventSampleStream[eventSampleStreamList.size()];
    eventSampleStreamList.toArray(eventSampleStreams);

    compositeSequenceableLoader =
        compositeSequenceableLoaderFactory.createCompositeSequenceableLoader(sampleStreams);
    return positionUs;
  }

  @Override
  public void discardBuffer(long positionUs, boolean toKeyframe) {
    for (ChunkSampleStream<DashChunkSource> sampleStream : sampleStreams) {
      sampleStream.discardBuffer(positionUs, toKeyframe);
    }
  }

  @Override
  public void reevaluateBuffer(long positionUs) {
    compositeSequenceableLoader.reevaluateBuffer(positionUs);
  }

  @Override
  public boolean continueLoading(long positionUs) {
    return compositeSequenceableLoader.continueLoading(positionUs);
  }

  @Override
  public boolean isLoading() {
    return compositeSequenceableLoader.isLoading();
  }

  @Override
  public long getNextLoadPositionUs() {
    return compositeSequenceableLoader.getNextLoadPositionUs();
  }

  @Override
  public long readDiscontinuity() {
    return C.TIME_UNSET;
  }

  @Override
  public long getBufferedPositionUs() {
    return compositeSequenceableLoader.getBufferedPositionUs();
  }

  @Override
  public long seekToUs(long positionUs) {
    for (ChunkSampleStream<DashChunkSource> sampleStream : sampleStreams) {
      sampleStream.seekToUs(positionUs);
    }
    for (EventSampleStream sampleStream : eventSampleStreams) {
      sampleStream.seekToUs(positionUs);
    }
    return positionUs;
  }

  @Override
  public long getAdjustedSeekPositionUs(long positionUs, SeekParameters seekParameters) {
    for (ChunkSampleStream<DashChunkSource> sampleStream : sampleStreams) {
      if (sampleStream.primaryTrackType == C.TRACK_TYPE_VIDEO) {
        return sampleStream.getAdjustedSeekPositionUs(positionUs, seekParameters);
      }
    }
    return positionUs;
  }

  // SequenceableLoader.Callback implementation.

  @Override
  public void onContinueLoadingRequested(ChunkSampleStream<DashChunkSource> sampleStream) {
    callback.onContinueLoadingRequested(this);
  }

  // Internal methods.

  private int[] getStreamIndexToTrackGroupIndex(ExoTrackSelection[] selections) {
    int[] streamIndexToTrackGroupIndex = new int[selections.length];
    for (int i = 0; i < selections.length; i++) {
      if (selections[i] != null) {
        streamIndexToTrackGroupIndex[i] = trackGroups.indexOf(selections[i].getTrackGroup());
      } else {
        streamIndexToTrackGroupIndex[i] = C.INDEX_UNSET;
      }
    }
    return streamIndexToTrackGroupIndex;
  }

  private void releaseDisabledStreams(
      ExoTrackSelection[] selections, boolean[] mayRetainStreamFlags, SampleStream[] streams) {
    for (int i = 0; i < selections.length; i++) {
      if (selections[i] == null || !mayRetainStreamFlags[i]) {
        if (streams[i] instanceof ChunkSampleStream) {
          @SuppressWarnings("unchecked")
          ChunkSampleStream<DashChunkSource> stream =
              (ChunkSampleStream<DashChunkSource>) streams[i];
          stream.release(this);
        } else if (streams[i] instanceof EmbeddedSampleStream) {
          ((EmbeddedSampleStream) streams[i]).release();
        }
        streams[i] = null;
      }
    }
  }

  private void releaseOrphanEmbeddedStreams(
      ExoTrackSelection[] selections, SampleStream[] streams, int[] streamIndexToTrackGroupIndex) {
    for (int i = 0; i < selections.length; i++) {
      if (streams[i] instanceof EmptySampleStream || streams[i] instanceof EmbeddedSampleStream) {
        // We need to release an embedded stream if the corresponding primary stream is released.
        int primaryStreamIndex = getPrimaryStreamIndex(i, streamIndexToTrackGroupIndex);
        boolean mayRetainStream;
        if (primaryStreamIndex == C.INDEX_UNSET) {
          // If the corresponding primary stream is not selected, we may retain an existing
          // EmptySampleStream.
          mayRetainStream = streams[i] instanceof EmptySampleStream;
        } else {
          // If the corresponding primary stream is selected, we may retain the embedded stream if
          // the stream's parent still matches.
          mayRetainStream =
              (streams[i] instanceof EmbeddedSampleStream)
                  && ((EmbeddedSampleStream) streams[i]).parent == streams[primaryStreamIndex];
        }
        if (!mayRetainStream) {
          if (streams[i] instanceof EmbeddedSampleStream) {
            ((EmbeddedSampleStream) streams[i]).release();
          }
          streams[i] = null;
        }
      }
    }
  }

  private void selectNewStreams(
      ExoTrackSelection[] selections,
      SampleStream[] streams,
      boolean[] streamResetFlags,
      long positionUs,
      int[] streamIndexToTrackGroupIndex) {
    // Create newly selected primary and event streams.
    for (int i = 0; i < selections.length; i++) {
      ExoTrackSelection selection = selections[i];
      if (selection == null) {
        continue;
      }
      if (streams[i] == null) {
        // Create new stream for selection.
        streamResetFlags[i] = true;
        int trackGroupIndex = streamIndexToTrackGroupIndex[i];
        TrackGroupInfo trackGroupInfo = trackGroupInfos[trackGroupIndex];
        if (trackGroupInfo.trackGroupCategory == TrackGroupInfo.CATEGORY_PRIMARY) {
          streams[i] = buildSampleStream(trackGroupInfo, selection, positionUs);
        } else if (trackGroupInfo.trackGroupCategory == TrackGroupInfo.CATEGORY_MANIFEST_EVENTS) {
          EventStream eventStream = eventStreams.get(trackGroupInfo.eventStreamGroupIndex);
          Format format = selection.getTrackGroup().getFormat(0);
          streams[i] = new EventSampleStream(eventStream, format, manifest.dynamic);
        }
      } else if (streams[i] instanceof ChunkSampleStream) {
        // Update selection in existing stream.
        @SuppressWarnings("unchecked")
        ChunkSampleStream<DashChunkSource> stream = (ChunkSampleStream<DashChunkSource>) streams[i];
        stream.getChunkSource().updateTrackSelection(selection);
      }
    }
    // Create newly selected embedded streams from the corresponding primary stream. Note that this
    // second pass is needed because the primary stream may not have been created yet in a first
    // pass if the index of the primary stream is greater than the index of the embedded stream.
    for (int i = 0; i < selections.length; i++) {
      if (streams[i] == null && selections[i] != null) {
        int trackGroupIndex = streamIndexToTrackGroupIndex[i];
        TrackGroupInfo trackGroupInfo = trackGroupInfos[trackGroupIndex];
        if (trackGroupInfo.trackGroupCategory == TrackGroupInfo.CATEGORY_EMBEDDED) {
          int primaryStreamIndex = getPrimaryStreamIndex(i, streamIndexToTrackGroupIndex);
          if (primaryStreamIndex == C.INDEX_UNSET) {
            // If an embedded track is selected without the corresponding primary track, create an
            // empty sample stream instead.
            streams[i] = new EmptySampleStream();
          } else {
            streams[i] =
                ((ChunkSampleStream) streams[primaryStreamIndex])
                    .selectEmbeddedTrack(positionUs, trackGroupInfo.trackType);
          }
        }
      }
    }
  }

  private int getPrimaryStreamIndex(int embeddedStreamIndex, int[] streamIndexToTrackGroupIndex) {
    int embeddedTrackGroupIndex = streamIndexToTrackGroupIndex[embeddedStreamIndex];
    if (embeddedTrackGroupIndex == C.INDEX_UNSET) {
      return C.INDEX_UNSET;
    }
    int primaryTrackGroupIndex = trackGroupInfos[embeddedTrackGroupIndex].primaryTrackGroupIndex;
    for (int i = 0; i < streamIndexToTrackGroupIndex.length; i++) {
      int trackGroupIndex = streamIndexToTrackGroupIndex[i];
      if (trackGroupIndex == primaryTrackGroupIndex
          && trackGroupInfos[trackGroupIndex].trackGroupCategory
              == TrackGroupInfo.CATEGORY_PRIMARY) {
        return i;
      }
    }
    return C.INDEX_UNSET;
  }

  private static Pair<TrackGroupArray, TrackGroupInfo[]> buildTrackGroups(
      DrmSessionManager drmSessionManager,
      List<AdaptationSet> adaptationSets,
      List<EventStream> eventStreams) {
    int[][] groupedAdaptationSetIndices = getGroupedAdaptationSetIndices(adaptationSets);

    int primaryGroupCount = groupedAdaptationSetIndices.length;
    boolean[] primaryGroupHasEventMessageTrackFlags = new boolean[primaryGroupCount];
    Format[][] primaryGroupClosedCaptionTrackFormats = new Format[primaryGroupCount][];
    int totalEmbeddedTrackGroupCount =
        identifyEmbeddedTracks(
            primaryGroupCount,
            adaptationSets,
            groupedAdaptationSetIndices,
            primaryGroupHasEventMessageTrackFlags,
            primaryGroupClosedCaptionTrackFormats);

    int totalGroupCount = primaryGroupCount + totalEmbeddedTrackGroupCount + eventStreams.size();
    TrackGroup[] trackGroups = new TrackGroup[totalGroupCount];
    TrackGroupInfo[] trackGroupInfos = new TrackGroupInfo[totalGroupCount];

    int trackGroupCount =
        buildPrimaryAndEmbeddedTrackGroupInfos(
            drmSessionManager,
            adaptationSets,
            groupedAdaptationSetIndices,
            primaryGroupCount,
            primaryGroupHasEventMessageTrackFlags,
            primaryGroupClosedCaptionTrackFormats,
            trackGroups,
            trackGroupInfos);

    buildManifestEventTrackGroupInfos(eventStreams, trackGroups, trackGroupInfos, trackGroupCount);

    return Pair.create(new TrackGroupArray(trackGroups), trackGroupInfos);
  }

  /**
   * Groups adaptation sets. Two adaptations sets belong to the same group if either:
   *
   * <ul>
   *   <li>One is a trick-play adaptation set and uses a {@code
   *       http://dashif.org/guidelines/trickmode} essential or supplemental property to indicate
   *       that the other is the main adaptation set to which it corresponds.
   *   <li>The two adaptation sets are marked as safe for switching using {@code
   *       urn:mpeg:dash:adaptation-set-switching:2016} supplemental properties.
   * </ul>
   *
   * @param adaptationSets The adaptation sets to merge.
   * @return An array of groups, where each group is an array of adaptation set indices.
   */
  private static int[][] getGroupedAdaptationSetIndices(List<AdaptationSet> adaptationSets) {
    int adaptationSetCount = adaptationSets.size();
    SparseIntArray adaptationSetIdToIndex = new SparseIntArray(adaptationSetCount);
    List<List<Integer>> adaptationSetGroupedIndices = new ArrayList<>(adaptationSetCount);
    SparseArray<List<Integer>> adaptationSetIndexToGroupedIndices =
        new SparseArray<>(adaptationSetCount);

    // Initially make each adaptation set belong to its own group. Also build the
    // adaptationSetIdToIndex map.
    for (int i = 0; i < adaptationSetCount; i++) {
      adaptationSetIdToIndex.put(adaptationSets.get(i).id, i);
      List<Integer> initialGroup = new ArrayList<>();
      initialGroup.add(i);
      adaptationSetGroupedIndices.add(initialGroup);
      adaptationSetIndexToGroupedIndices.put(i, initialGroup);
    }

    // Merge adaptation set groups.
    for (int i = 0; i < adaptationSetCount; i++) {
      int mergedGroupIndex = i;
      AdaptationSet adaptationSet = adaptationSets.get(i);

      // Trick-play adaptation sets are merged with their corresponding main adaptation sets.
      @Nullable
      Descriptor trickPlayProperty = findTrickPlayProperty(adaptationSet.essentialProperties);
      if (trickPlayProperty == null) {
        // Trick-play can also be specified using a supplemental property.
        trickPlayProperty = findTrickPlayProperty(adaptationSet.supplementalProperties);
      }
      if (trickPlayProperty != null) {
        int mainAdaptationSetId = Integer.parseInt(trickPlayProperty.value);
        int mainAdaptationSetIndex =
            adaptationSetIdToIndex.get(mainAdaptationSetId, /* valueIfKeyNotFound= */ -1);
        if (mainAdaptationSetIndex != -1) {
          mergedGroupIndex = mainAdaptationSetIndex;
        }
      }

      // Adaptation sets that are safe for switching are merged, using the smallest index for the
      // merged group.
      if (mergedGroupIndex == i) {
        @Nullable
        Descriptor adaptationSetSwitchingProperty =
            findAdaptationSetSwitchingProperty(adaptationSet.supplementalProperties);
        if (adaptationSetSwitchingProperty != null) {
          String[] otherAdaptationSetIds = Util.split(adaptationSetSwitchingProperty.value, ",");
          for (String adaptationSetId : otherAdaptationSetIds) {
            int otherAdaptationSetId =
                adaptationSetIdToIndex.get(
                    Integer.parseInt(adaptationSetId), /* valueIfKeyNotFound= */ -1);
            if (otherAdaptationSetId != -1) {
              mergedGroupIndex = min(mergedGroupIndex, otherAdaptationSetId);
            }
          }
        }
      }

      // Merge the groups if necessary.
      if (mergedGroupIndex != i) {
        List<Integer> thisGroup = adaptationSetIndexToGroupedIndices.get(i);
        List<Integer> mergedGroup = adaptationSetIndexToGroupedIndices.get(mergedGroupIndex);
        mergedGroup.addAll(thisGroup);
        adaptationSetIndexToGroupedIndices.put(i, mergedGroup);
        adaptationSetGroupedIndices.remove(thisGroup);
      }
    }

    int[][] groupedAdaptationSetIndices = new int[adaptationSetGroupedIndices.size()][];
    for (int i = 0; i < groupedAdaptationSetIndices.length; i++) {
      groupedAdaptationSetIndices[i] = Ints.toArray(adaptationSetGroupedIndices.get(i));
      // Restore the original adaptation set order within each group.
      Arrays.sort(groupedAdaptationSetIndices[i]);
    }
    return groupedAdaptationSetIndices;
  }

  /**
   * Iterates through list of primary track groups and identifies embedded tracks.
   *
   * @param primaryGroupCount The number of primary track groups.
   * @param adaptationSets The list of {@link AdaptationSet} of the current DASH period.
   * @param groupedAdaptationSetIndices The indices of {@link AdaptationSet} that belongs to the
   *     same primary group, grouped in primary track groups order.
   * @param primaryGroupHasEventMessageTrackFlags An output array to be filled with flags indicating
   *     whether each of the primary track groups contains an embedded event message track.
   * @param primaryGroupClosedCaptionTrackFormats An output array to be filled with track formats
   *     for closed caption tracks embedded in each of the primary track groups.
   * @return Total number of embedded track groups.
   */
  private static int identifyEmbeddedTracks(
      int primaryGroupCount,
      List<AdaptationSet> adaptationSets,
      int[][] groupedAdaptationSetIndices,
      boolean[] primaryGroupHasEventMessageTrackFlags,
      Format[][] primaryGroupClosedCaptionTrackFormats) {
    int numEmbeddedTrackGroups = 0;
    for (int i = 0; i < primaryGroupCount; i++) {
      if (hasEventMessageTrack(adaptationSets, groupedAdaptationSetIndices[i])) {
        primaryGroupHasEventMessageTrackFlags[i] = true;
        numEmbeddedTrackGroups++;
      }
      primaryGroupClosedCaptionTrackFormats[i] =
          getClosedCaptionTrackFormats(adaptationSets, groupedAdaptationSetIndices[i]);
      if (primaryGroupClosedCaptionTrackFormats[i].length != 0) {
        numEmbeddedTrackGroups++;
      }
    }
    return numEmbeddedTrackGroups;
  }

  private static int buildPrimaryAndEmbeddedTrackGroupInfos(
      DrmSessionManager drmSessionManager,
      List<AdaptationSet> adaptationSets,
      int[][] groupedAdaptationSetIndices,
      int primaryGroupCount,
      boolean[] primaryGroupHasEventMessageTrackFlags,
      Format[][] primaryGroupClosedCaptionTrackFormats,
      TrackGroup[] trackGroups,
      TrackGroupInfo[] trackGroupInfos) {
    int trackGroupCount = 0;
    for (int i = 0; i < primaryGroupCount; i++) {
      int[] adaptationSetIndices = groupedAdaptationSetIndices[i];
      List<Representation> representations = new ArrayList<>();
      for (int adaptationSetIndex : adaptationSetIndices) {
        representations.addAll(adaptationSets.get(adaptationSetIndex).representations);
      }
      Format[] formats = new Format[representations.size()];
      for (int j = 0; j < formats.length; j++) {
        Format format = representations.get(j).format;
        formats[j] = format.copyWithCryptoType(drmSessionManager.getCryptoType(format));
      }

      AdaptationSet firstAdaptationSet = adaptationSets.get(adaptationSetIndices[0]);
      String trackGroupId =
          firstAdaptationSet.id != AdaptationSet.ID_UNSET
              ? Integer.toString(firstAdaptationSet.id)
              : ("unset:" + i);
      int primaryTrackGroupIndex = trackGroupCount++;
      int eventMessageTrackGroupIndex =
          primaryGroupHasEventMessageTrackFlags[i] ? trackGroupCount++ : C.INDEX_UNSET;
      int closedCaptionTrackGroupIndex =
          primaryGroupClosedCaptionTrackFormats[i].length != 0 ? trackGroupCount++ : C.INDEX_UNSET;

      trackGroups[primaryTrackGroupIndex] = new TrackGroup(trackGroupId, formats);
      trackGroupInfos[primaryTrackGroupIndex] =
          TrackGroupInfo.primaryTrack(
              firstAdaptationSet.type,
              adaptationSetIndices,
              primaryTrackGroupIndex,
              eventMessageTrackGroupIndex,
              closedCaptionTrackGroupIndex);
      if (eventMessageTrackGroupIndex != C.INDEX_UNSET) {
        String eventMessageTrackGroupId = trackGroupId + ":emsg";
        Format format =
            new Format.Builder()
                .setId(eventMessageTrackGroupId)
                .setSampleMimeType(MimeTypes.APPLICATION_EMSG)
                .build();
        trackGroups[eventMessageTrackGroupIndex] = new TrackGroup(eventMessageTrackGroupId, format);
        trackGroupInfos[eventMessageTrackGroupIndex] =
            TrackGroupInfo.embeddedEmsgTrack(adaptationSetIndices, primaryTrackGroupIndex);
      }
      if (closedCaptionTrackGroupIndex != C.INDEX_UNSET) {
        String closedCaptionTrackGroupId = trackGroupId + ":cc";
        trackGroups[closedCaptionTrackGroupIndex] =
            new TrackGroup(closedCaptionTrackGroupId, primaryGroupClosedCaptionTrackFormats[i]);
        trackGroupInfos[closedCaptionTrackGroupIndex] =
            TrackGroupInfo.embeddedClosedCaptionTrack(adaptationSetIndices, primaryTrackGroupIndex);
      }
    }
    return trackGroupCount;
  }

  private static void buildManifestEventTrackGroupInfos(
      List<EventStream> eventStreams,
      TrackGroup[] trackGroups,
      TrackGroupInfo[] trackGroupInfos,
      int existingTrackGroupCount) {
    for (int i = 0; i < eventStreams.size(); i++) {
      EventStream eventStream = eventStreams.get(i);
      Format format =
          new Format.Builder()
              .setId(eventStream.id())
              .setSampleMimeType(MimeTypes.APPLICATION_EMSG)
              .build();
      String uniqueTrackGroupId = eventStream.id() + ":" + i;
      trackGroups[existingTrackGroupCount] = new TrackGroup(uniqueTrackGroupId, format);
      trackGroupInfos[existingTrackGroupCount++] = TrackGroupInfo.mpdEventTrack(i);
    }
  }

  private ChunkSampleStream<DashChunkSource> buildSampleStream(
      TrackGroupInfo trackGroupInfo, ExoTrackSelection selection, long positionUs) {
    int embeddedTrackCount = 0;
    boolean enableEventMessageTrack =
        trackGroupInfo.embeddedEventMessageTrackGroupIndex != C.INDEX_UNSET;
    TrackGroup embeddedEventMessageTrackGroup = null;
    if (enableEventMessageTrack) {
      embeddedEventMessageTrackGroup =
          trackGroups.get(trackGroupInfo.embeddedEventMessageTrackGroupIndex);
      embeddedTrackCount++;
    }
    boolean enableClosedCaptionTrack =
        trackGroupInfo.embeddedClosedCaptionTrackGroupIndex != C.INDEX_UNSET;
    TrackGroup embeddedClosedCaptionTrackGroup = null;
    if (enableClosedCaptionTrack) {
      embeddedClosedCaptionTrackGroup =
          trackGroups.get(trackGroupInfo.embeddedClosedCaptionTrackGroupIndex);
      embeddedTrackCount += embeddedClosedCaptionTrackGroup.length;
    }

    Format[] embeddedTrackFormats = new Format[embeddedTrackCount];
    int[] embeddedTrackTypes = new int[embeddedTrackCount];
    embeddedTrackCount = 0;
    if (enableEventMessageTrack) {
      embeddedTrackFormats[embeddedTrackCount] = embeddedEventMessageTrackGroup.getFormat(0);
      embeddedTrackTypes[embeddedTrackCount] = C.TRACK_TYPE_METADATA;
      embeddedTrackCount++;
    }
    List<Format> embeddedClosedCaptionTrackFormats = new ArrayList<>();
    if (enableClosedCaptionTrack) {
      for (int i = 0; i < embeddedClosedCaptionTrackGroup.length; i++) {
        embeddedTrackFormats[embeddedTrackCount] = embeddedClosedCaptionTrackGroup.getFormat(i);
        embeddedTrackTypes[embeddedTrackCount] = C.TRACK_TYPE_TEXT;
        embeddedClosedCaptionTrackFormats.add(embeddedTrackFormats[embeddedTrackCount]);
        embeddedTrackCount++;
      }
    }

    PlayerTrackEmsgHandler trackPlayerEmsgHandler =
        manifest.dynamic && enableEventMessageTrack
            ? playerEmsgHandler.newPlayerTrackEmsgHandler()
            : null;
    DashChunkSource chunkSource =
        chunkSourceFactory.createDashChunkSource(
            manifestLoaderErrorThrower,
            manifest,
            baseUrlExclusionList,
            periodIndex,
            trackGroupInfo.adaptationSetIndices,
            selection,
            trackGroupInfo.trackType,
            elapsedRealtimeOffsetMs,
            enableEventMessageTrack,
            embeddedClosedCaptionTrackFormats,
            trackPlayerEmsgHandler,
            transferListener,
            playerId);
    ChunkSampleStream<DashChunkSource> stream =
        new ChunkSampleStream<>(
            trackGroupInfo.trackType,
            embeddedTrackTypes,
            embeddedTrackFormats,
            chunkSource,
            this,
            allocator,
            positionUs,
            drmSessionManager,
            drmEventDispatcher,
            loadErrorHandlingPolicy,
            mediaSourceEventDispatcher);
    synchronized (this) {
      // The map is also accessed on the loading thread so synchronize access.
      trackEmsgHandlerBySampleStream.put(stream, trackPlayerEmsgHandler);
    }
    return stream;
  }

  @Nullable
  private static Descriptor findAdaptationSetSwitchingProperty(List<Descriptor> descriptors) {
    return findDescriptor(descriptors, "urn:mpeg:dash:adaptation-set-switching:2016");
  }

  @Nullable
  private static Descriptor findTrickPlayProperty(List<Descriptor> descriptors) {
    return findDescriptor(descriptors, "http://dashif.org/guidelines/trickmode");
  }

  @Nullable
  private static Descriptor findDescriptor(List<Descriptor> descriptors, String schemeIdUri) {
    for (int i = 0; i < descriptors.size(); i++) {
      Descriptor descriptor = descriptors.get(i);
      if (schemeIdUri.equals(descriptor.schemeIdUri)) {
        return descriptor;
      }
    }
    return null;
  }

  private static boolean hasEventMessageTrack(
      List<AdaptationSet> adaptationSets, int[] adaptationSetIndices) {
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

  private static Format[] getClosedCaptionTrackFormats(
      List<AdaptationSet> adaptationSets, int[] adaptationSetIndices) {
    for (int i : adaptationSetIndices) {
      AdaptationSet adaptationSet = adaptationSets.get(i);
      List<Descriptor> descriptors = adaptationSets.get(i).accessibilityDescriptors;
      for (int j = 0; j < descriptors.size(); j++) {
        Descriptor descriptor = descriptors.get(j);
        if ("urn:scte:dash:cc:cea-608:2015".equals(descriptor.schemeIdUri)) {
          Format cea608Format =
              new Format.Builder()
                  .setSampleMimeType(MimeTypes.APPLICATION_CEA608)
                  .setId(adaptationSet.id + ":cea608")
                  .build();
          return parseClosedCaptionDescriptor(
              descriptor, CEA608_SERVICE_DESCRIPTOR_REGEX, cea608Format);
        } else if ("urn:scte:dash:cc:cea-708:2015".equals(descriptor.schemeIdUri)) {
          Format cea708Format =
              new Format.Builder()
                  .setSampleMimeType(MimeTypes.APPLICATION_CEA708)
                  .setId(adaptationSet.id + ":cea708")
                  .build();
          return parseClosedCaptionDescriptor(
              descriptor, CEA708_SERVICE_DESCRIPTOR_REGEX, cea708Format);
        }
      }
    }
    return new Format[0];
  }

  private static Format[] parseClosedCaptionDescriptor(
      Descriptor descriptor, Pattern serviceDescriptorRegex, Format baseFormat) {
    @Nullable String value = descriptor.value;
    if (value == null) {
      // There are embedded closed caption tracks, but service information is not declared.
      return new Format[] {baseFormat};
    }
    String[] services = Util.split(value, ";");
    Format[] formats = new Format[services.length];
    for (int i = 0; i < services.length; i++) {
      Matcher matcher = serviceDescriptorRegex.matcher(services[i]);
      if (!matcher.matches()) {
        // If we can't parse service information for all services, assume a single track.
        return new Format[] {baseFormat};
      }
      int accessibilityChannel = Integer.parseInt(matcher.group(1));
      formats[i] =
          baseFormat
              .buildUpon()
              .setId(baseFormat.id + ":" + accessibilityChannel)
              .setAccessibilityChannel(accessibilityChannel)
              .setLanguage(matcher.group(2))
              .build();
    }
    return formats;
  }

  // We won't assign the array to a variable that erases the generic type, and then write into it.
  @SuppressWarnings({"unchecked", "rawtypes"})
  private static ChunkSampleStream<DashChunkSource>[] newSampleStreamArray(int length) {
    return new ChunkSampleStream[length];
  }

  private static final class TrackGroupInfo {

    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target(TYPE_USE)
    @IntDef({CATEGORY_PRIMARY, CATEGORY_EMBEDDED, CATEGORY_MANIFEST_EVENTS})
    public @interface TrackGroupCategory {}

    /**
     * A normal track group that has its samples drawn from the stream. For example: a video Track
     * Group or an audio Track Group.
     */
    private static final int CATEGORY_PRIMARY = 0;

    /**
     * A track group whose samples are embedded within one of the primary streams. For example: an
     * EMSG track has its sample embedded in emsg atoms in one of the primary streams.
     */
    private static final int CATEGORY_EMBEDDED = 1;

    /**
     * A track group that has its samples listed explicitly in the DASH manifest file. For example:
     * an EventStream track has its sample (Events) included directly in the DASH manifest file.
     */
    private static final int CATEGORY_MANIFEST_EVENTS = 2;

    public final int[] adaptationSetIndices;
    public final @C.TrackType int trackType;
    public final @TrackGroupCategory int trackGroupCategory;

    public final int eventStreamGroupIndex;
    public final int primaryTrackGroupIndex;
    public final int embeddedEventMessageTrackGroupIndex;
    public final int embeddedClosedCaptionTrackGroupIndex;

    public static TrackGroupInfo primaryTrack(
        int trackType,
        int[] adaptationSetIndices,
        int primaryTrackGroupIndex,
        int embeddedEventMessageTrackGroupIndex,
        int embeddedClosedCaptionTrackGroupIndex) {
      return new TrackGroupInfo(
          trackType,
          CATEGORY_PRIMARY,
          adaptationSetIndices,
          primaryTrackGroupIndex,
          embeddedEventMessageTrackGroupIndex,
          embeddedClosedCaptionTrackGroupIndex,
          /* eventStreamGroupIndex= */ -1);
    }

    public static TrackGroupInfo embeddedEmsgTrack(
        int[] adaptationSetIndices, int primaryTrackGroupIndex) {
      return new TrackGroupInfo(
          C.TRACK_TYPE_METADATA,
          CATEGORY_EMBEDDED,
          adaptationSetIndices,
          primaryTrackGroupIndex,
          C.INDEX_UNSET,
          C.INDEX_UNSET,
          /* eventStreamGroupIndex= */ -1);
    }

    public static TrackGroupInfo embeddedClosedCaptionTrack(
        int[] adaptationSetIndices, int primaryTrackGroupIndex) {
      return new TrackGroupInfo(
          C.TRACK_TYPE_TEXT,
          CATEGORY_EMBEDDED,
          adaptationSetIndices,
          primaryTrackGroupIndex,
          C.INDEX_UNSET,
          C.INDEX_UNSET,
          /* eventStreamGroupIndex= */ -1);
    }

    public static TrackGroupInfo mpdEventTrack(int eventStreamIndex) {
      return new TrackGroupInfo(
          C.TRACK_TYPE_METADATA,
          CATEGORY_MANIFEST_EVENTS,
          new int[0],
          /* primaryTrackGroupIndex= */ -1,
          C.INDEX_UNSET,
          C.INDEX_UNSET,
          eventStreamIndex);
    }

    private TrackGroupInfo(
        @C.TrackType int trackType,
        @TrackGroupCategory int trackGroupCategory,
        int[] adaptationSetIndices,
        int primaryTrackGroupIndex,
        int embeddedEventMessageTrackGroupIndex,
        int embeddedClosedCaptionTrackGroupIndex,
        int eventStreamGroupIndex) {
      this.trackType = trackType;
      this.adaptationSetIndices = adaptationSetIndices;
      this.trackGroupCategory = trackGroupCategory;
      this.primaryTrackGroupIndex = primaryTrackGroupIndex;
      this.embeddedEventMessageTrackGroupIndex = embeddedEventMessageTrackGroupIndex;
      this.embeddedClosedCaptionTrackGroupIndex = embeddedClosedCaptionTrackGroupIndex;
      this.eventStreamGroupIndex = eventStreamGroupIndex;
    }
  }
}
