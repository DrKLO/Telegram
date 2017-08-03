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

import android.net.Uri;
import android.os.SystemClock;
import org.telegram.messenger.exoplayer2.C;
import org.telegram.messenger.exoplayer2.Format;
import org.telegram.messenger.exoplayer2.extractor.ChunkIndex;
import org.telegram.messenger.exoplayer2.extractor.Extractor;
import org.telegram.messenger.exoplayer2.extractor.SeekMap;
import org.telegram.messenger.exoplayer2.extractor.mkv.MatroskaExtractor;
import org.telegram.messenger.exoplayer2.extractor.mp4.FragmentedMp4Extractor;
import org.telegram.messenger.exoplayer2.extractor.rawcc.RawCcExtractor;
import org.telegram.messenger.exoplayer2.source.BehindLiveWindowException;
import org.telegram.messenger.exoplayer2.source.chunk.Chunk;
import org.telegram.messenger.exoplayer2.source.chunk.ChunkExtractorWrapper;
import org.telegram.messenger.exoplayer2.source.chunk.ChunkHolder;
import org.telegram.messenger.exoplayer2.source.chunk.ChunkedTrackBlacklistUtil;
import org.telegram.messenger.exoplayer2.source.chunk.ContainerMediaChunk;
import org.telegram.messenger.exoplayer2.source.chunk.InitializationChunk;
import org.telegram.messenger.exoplayer2.source.chunk.MediaChunk;
import org.telegram.messenger.exoplayer2.source.chunk.SingleSampleMediaChunk;
import org.telegram.messenger.exoplayer2.source.dash.manifest.AdaptationSet;
import org.telegram.messenger.exoplayer2.source.dash.manifest.DashManifest;
import org.telegram.messenger.exoplayer2.source.dash.manifest.RangedUri;
import org.telegram.messenger.exoplayer2.source.dash.manifest.Representation;
import org.telegram.messenger.exoplayer2.trackselection.TrackSelection;
import org.telegram.messenger.exoplayer2.upstream.DataSource;
import org.telegram.messenger.exoplayer2.upstream.DataSpec;
import org.telegram.messenger.exoplayer2.upstream.HttpDataSource.InvalidResponseCodeException;
import org.telegram.messenger.exoplayer2.upstream.LoaderErrorThrower;
import org.telegram.messenger.exoplayer2.util.MimeTypes;
import org.telegram.messenger.exoplayer2.util.Util;
import java.io.IOException;
import java.util.List;

/**
 * A default {@link DashChunkSource} implementation.
 */
public class DefaultDashChunkSource implements DashChunkSource {

  public static final class Factory implements DashChunkSource.Factory {

    private final DataSource.Factory dataSourceFactory;
    private final int maxSegmentsPerLoad;

    public Factory(DataSource.Factory dataSourceFactory) {
      this(dataSourceFactory, 1);
    }

    public Factory(DataSource.Factory dataSourceFactory, int maxSegmentsPerLoad) {
      this.dataSourceFactory = dataSourceFactory;
      this.maxSegmentsPerLoad = maxSegmentsPerLoad;
    }

    @Override
    public DashChunkSource createDashChunkSource(LoaderErrorThrower manifestLoaderErrorThrower,
        DashManifest manifest, int periodIndex, int adaptationSetIndex,
        TrackSelection trackSelection, long elapsedRealtimeOffsetMs,
        boolean enableEventMessageTrack, boolean enableCea608Track) {
      DataSource dataSource = dataSourceFactory.createDataSource();
      return new DefaultDashChunkSource(manifestLoaderErrorThrower, manifest, periodIndex,
          adaptationSetIndex, trackSelection, dataSource, elapsedRealtimeOffsetMs,
          maxSegmentsPerLoad, enableEventMessageTrack, enableCea608Track);
    }

  }

  private final LoaderErrorThrower manifestLoaderErrorThrower;
  private final int adaptationSetIndex;
  private final TrackSelection trackSelection;
  private final RepresentationHolder[] representationHolders;
  private final DataSource dataSource;
  private final long elapsedRealtimeOffsetMs;
  private final int maxSegmentsPerLoad;

  private DashManifest manifest;
  private int periodIndex;

  private IOException fatalError;
  private boolean missingLastSegment;

  /**
   * @param manifestLoaderErrorThrower Throws errors affecting loading of manifests.
   * @param manifest The initial manifest.
   * @param periodIndex The index of the period in the manifest.
   * @param adaptationSetIndex The index of the adaptation set in the period.
   * @param trackSelection The track selection.
   * @param dataSource A {@link DataSource} suitable for loading the media data.
   * @param elapsedRealtimeOffsetMs If known, an estimate of the instantaneous difference between
   *     server-side unix time and {@link SystemClock#elapsedRealtime()} in milliseconds, specified
   *     as the server's unix time minus the local elapsed time. If unknown, set to 0.
   * @param maxSegmentsPerLoad The maximum number of segments to combine into a single request.
   *     Note that segments will only be combined if their {@link Uri}s are the same and if their
   *     data ranges are adjacent.
   * @param enableEventMessageTrack Whether the chunks generated by the source may output an event
   *     message track.
   * @param enableCea608Track Whether the chunks generated by the source may output a CEA-608 track.
   */
  public DefaultDashChunkSource(LoaderErrorThrower manifestLoaderErrorThrower,
      DashManifest manifest, int periodIndex, int adaptationSetIndex, TrackSelection trackSelection,
      DataSource dataSource, long elapsedRealtimeOffsetMs, int maxSegmentsPerLoad,
      boolean enableEventMessageTrack, boolean enableCea608Track) {
    this.manifestLoaderErrorThrower = manifestLoaderErrorThrower;
    this.manifest = manifest;
    this.adaptationSetIndex = adaptationSetIndex;
    this.trackSelection = trackSelection;
    this.dataSource = dataSource;
    this.periodIndex = periodIndex;
    this.elapsedRealtimeOffsetMs = elapsedRealtimeOffsetMs;
    this.maxSegmentsPerLoad = maxSegmentsPerLoad;

    long periodDurationUs = manifest.getPeriodDurationUs(periodIndex);
    AdaptationSet adaptationSet = getAdaptationSet();
    List<Representation> representations = adaptationSet.representations;
    representationHolders = new RepresentationHolder[trackSelection.length()];
    for (int i = 0; i < representationHolders.length; i++) {
      Representation representation = representations.get(trackSelection.getIndexInTrackGroup(i));
      representationHolders[i] = new RepresentationHolder(periodDurationUs, representation,
          enableEventMessageTrack, enableCea608Track, adaptationSet.type);
    }
  }

  @Override
  public void updateManifest(DashManifest newManifest, int newPeriodIndex) {
    try {
      manifest = newManifest;
      periodIndex = newPeriodIndex;
      long periodDurationUs = manifest.getPeriodDurationUs(periodIndex);
      List<Representation> representations = getAdaptationSet().representations;
      for (int i = 0; i < representationHolders.length; i++) {
        Representation representation = representations.get(trackSelection.getIndexInTrackGroup(i));
        representationHolders[i].updateRepresentation(periodDurationUs, representation);
      }
    } catch (BehindLiveWindowException e) {
      fatalError = e;
    }
  }

  @Override
  public void maybeThrowError() throws IOException {
    if (fatalError != null) {
      throw fatalError;
    } else {
      manifestLoaderErrorThrower.maybeThrowError();
    }
  }

  @Override
  public int getPreferredQueueSize(long playbackPositionUs, List<? extends MediaChunk> queue) {
    if (fatalError != null || trackSelection.length() < 2) {
      return queue.size();
    }
    return trackSelection.evaluateQueueSize(playbackPositionUs, queue);
  }

  @Override
  public final void getNextChunk(MediaChunk previous, long playbackPositionUs, ChunkHolder out) {
    if (fatalError != null) {
      return;
    }

    long bufferedDurationUs = previous != null ? (previous.endTimeUs - playbackPositionUs) : 0;
    trackSelection.updateSelectedTrack(bufferedDurationUs);

    RepresentationHolder representationHolder =
        representationHolders[trackSelection.getSelectedIndex()];

    if (representationHolder.extractorWrapper != null) {
      Representation selectedRepresentation = representationHolder.representation;
      RangedUri pendingInitializationUri = null;
      RangedUri pendingIndexUri = null;
      if (representationHolder.extractorWrapper.getSampleFormats() == null) {
        pendingInitializationUri = selectedRepresentation.getInitializationUri();
      }
      if (representationHolder.segmentIndex == null) {
        pendingIndexUri = selectedRepresentation.getIndexUri();
      }
      if (pendingInitializationUri != null || pendingIndexUri != null) {
        // We have initialization and/or index requests to make.
        out.chunk = newInitializationChunk(representationHolder, dataSource,
            trackSelection.getSelectedFormat(), trackSelection.getSelectionReason(),
            trackSelection.getSelectionData(), pendingInitializationUri, pendingIndexUri);
        return;
      }
    }

    long nowUs = getNowUnixTimeUs();
    int availableSegmentCount = representationHolder.getSegmentCount();
    if (availableSegmentCount == 0) {
      // The index doesn't define any segments.
      out.endOfStream = !manifest.dynamic || (periodIndex < manifest.getPeriodCount() - 1);
      return;
    }

    int firstAvailableSegmentNum = representationHolder.getFirstSegmentNum();
    int lastAvailableSegmentNum;
    if (availableSegmentCount == DashSegmentIndex.INDEX_UNBOUNDED) {
      // The index is itself unbounded. We need to use the current time to calculate the range of
      // available segments.
      long liveEdgeTimeUs = nowUs - manifest.availabilityStartTime * 1000;
      long periodStartUs = manifest.getPeriod(periodIndex).startMs * 1000;
      long liveEdgeTimeInPeriodUs = liveEdgeTimeUs - periodStartUs;
      if (manifest.timeShiftBufferDepth != C.TIME_UNSET) {
        long bufferDepthUs = manifest.timeShiftBufferDepth * 1000;
        firstAvailableSegmentNum = Math.max(firstAvailableSegmentNum,
            representationHolder.getSegmentNum(liveEdgeTimeInPeriodUs - bufferDepthUs));
      }
      // getSegmentNum(liveEdgeTimestampUs) will not be completed yet, so subtract one to get the
      // index of the last completed segment.
      lastAvailableSegmentNum = representationHolder.getSegmentNum(liveEdgeTimeInPeriodUs) - 1;
    } else {
      lastAvailableSegmentNum = firstAvailableSegmentNum + availableSegmentCount - 1;
    }

    int segmentNum;
    if (previous == null) {
      segmentNum = Util.constrainValue(representationHolder.getSegmentNum(playbackPositionUs),
          firstAvailableSegmentNum, lastAvailableSegmentNum);
    } else {
      segmentNum = previous.getNextChunkIndex();
      if (segmentNum < firstAvailableSegmentNum) {
        // This is before the first chunk in the current manifest.
        fatalError = new BehindLiveWindowException();
        return;
      }
    }

    if (segmentNum > lastAvailableSegmentNum
        || (missingLastSegment && segmentNum >= lastAvailableSegmentNum)) {
      // This is beyond the last chunk in the current manifest.
      out.endOfStream = !manifest.dynamic || (periodIndex < manifest.getPeriodCount() - 1);
      return;
    }

    int maxSegmentCount = Math.min(maxSegmentsPerLoad, lastAvailableSegmentNum - segmentNum + 1);
    out.chunk = newMediaChunk(representationHolder, dataSource, trackSelection.getSelectedFormat(),
        trackSelection.getSelectionReason(), trackSelection.getSelectionData(), segmentNum,
        maxSegmentCount);
  }

  @Override
  public void onChunkLoadCompleted(Chunk chunk) {
    if (chunk instanceof InitializationChunk) {
      InitializationChunk initializationChunk = (InitializationChunk) chunk;
      RepresentationHolder representationHolder =
          representationHolders[trackSelection.indexOf(initializationChunk.trackFormat)];
      // The null check avoids overwriting an index obtained from the manifest with one obtained
      // from the stream. If the manifest defines an index then the stream shouldn't, but in cases
      // where it does we should ignore it.
      if (representationHolder.segmentIndex == null) {
        SeekMap seekMap = representationHolder.extractorWrapper.getSeekMap();
        if (seekMap != null) {
          representationHolder.segmentIndex = new DashWrappingSegmentIndex((ChunkIndex) seekMap);
        }
      }
    }
  }

  @Override
  public boolean onChunkLoadError(Chunk chunk, boolean cancelable, Exception e) {
    if (!cancelable) {
      return false;
    }
    // Workaround for missing segment at the end of the period
    if (!manifest.dynamic && chunk instanceof MediaChunk
        && e instanceof InvalidResponseCodeException
        && ((InvalidResponseCodeException) e).responseCode == 404) {
      RepresentationHolder representationHolder =
          representationHolders[trackSelection.indexOf(chunk.trackFormat)];
      int segmentCount = representationHolder.getSegmentCount();
      if (segmentCount != DashSegmentIndex.INDEX_UNBOUNDED && segmentCount != 0) {
        int lastAvailableSegmentNum = representationHolder.getFirstSegmentNum() + segmentCount - 1;
        if (((MediaChunk) chunk).getNextChunkIndex() > lastAvailableSegmentNum) {
          missingLastSegment = true;
          return true;
        }
      }
    }
    // Blacklist if appropriate.
    return ChunkedTrackBlacklistUtil.maybeBlacklistTrack(trackSelection,
        trackSelection.indexOf(chunk.trackFormat), e);
  }

  // Private methods.

  private AdaptationSet getAdaptationSet() {
    return manifest.getPeriod(periodIndex).adaptationSets.get(adaptationSetIndex);
  }

  private long getNowUnixTimeUs() {
    if (elapsedRealtimeOffsetMs != 0) {
      return (SystemClock.elapsedRealtime() + elapsedRealtimeOffsetMs) * 1000;
    } else {
      return System.currentTimeMillis() * 1000;
    }
  }

  private static Chunk newInitializationChunk(RepresentationHolder representationHolder,
      DataSource dataSource, Format trackFormat, int trackSelectionReason,
      Object trackSelectionData, RangedUri initializationUri, RangedUri indexUri) {
    RangedUri requestUri;
    String baseUrl = representationHolder.representation.baseUrl;
    if (initializationUri != null) {
      // It's common for initialization and index data to be stored adjacently. Attempt to merge
      // the two requests together to request both at once.
      requestUri = initializationUri.attemptMerge(indexUri, baseUrl);
      if (requestUri == null) {
        requestUri = initializationUri;
      }
    } else {
      requestUri = indexUri;
    }
    DataSpec dataSpec = new DataSpec(requestUri.resolveUri(baseUrl), requestUri.start,
        requestUri.length, representationHolder.representation.getCacheKey());
    return new InitializationChunk(dataSource, dataSpec, trackFormat,
        trackSelectionReason, trackSelectionData, representationHolder.extractorWrapper);
  }

  private static Chunk newMediaChunk(RepresentationHolder representationHolder,
      DataSource dataSource, Format trackFormat, int trackSelectionReason,
      Object trackSelectionData, int firstSegmentNum, int maxSegmentCount) {
    Representation representation = representationHolder.representation;
    long startTimeUs = representationHolder.getSegmentStartTimeUs(firstSegmentNum);
    RangedUri segmentUri = representationHolder.getSegmentUrl(firstSegmentNum);
    String baseUrl = representation.baseUrl;
    if (representationHolder.extractorWrapper == null) {
      long endTimeUs = representationHolder.getSegmentEndTimeUs(firstSegmentNum);
      DataSpec dataSpec = new DataSpec(segmentUri.resolveUri(baseUrl),
          segmentUri.start, segmentUri.length, representation.getCacheKey());
      return new SingleSampleMediaChunk(dataSource, dataSpec, trackFormat, trackSelectionReason,
          trackSelectionData, startTimeUs, endTimeUs, firstSegmentNum,
          representationHolder.trackType, trackFormat);
    } else {
      int segmentCount = 1;
      for (int i = 1; i < maxSegmentCount; i++) {
        RangedUri nextSegmentUri = representationHolder.getSegmentUrl(firstSegmentNum + i);
        RangedUri mergedSegmentUri = segmentUri.attemptMerge(nextSegmentUri, baseUrl);
        if (mergedSegmentUri == null) {
          // Unable to merge segment fetches because the URIs do not merge.
          break;
        }
        segmentUri = mergedSegmentUri;
        segmentCount++;
      }
      long endTimeUs = representationHolder.getSegmentEndTimeUs(firstSegmentNum + segmentCount - 1);
      DataSpec dataSpec = new DataSpec(segmentUri.resolveUri(baseUrl),
          segmentUri.start, segmentUri.length, representation.getCacheKey());
      long sampleOffsetUs = -representation.presentationTimeOffsetUs;
      return new ContainerMediaChunk(dataSource, dataSpec, trackFormat, trackSelectionReason,
          trackSelectionData, startTimeUs, endTimeUs, firstSegmentNum, segmentCount,
          sampleOffsetUs, representationHolder.extractorWrapper);
    }
  }

  // Protected classes.

  protected static final class RepresentationHolder {

    public final int trackType;
    public final ChunkExtractorWrapper extractorWrapper;

    public Representation representation;
    public DashSegmentIndex segmentIndex;

    private long periodDurationUs;
    private int segmentNumShift;

    public RepresentationHolder(long periodDurationUs, Representation representation,
        boolean enableEventMessageTrack, boolean enableCea608Track, int trackType) {
      this.periodDurationUs = periodDurationUs;
      this.representation = representation;
      this.trackType = trackType;
      String containerMimeType = representation.format.containerMimeType;
      if (mimeTypeIsRawText(containerMimeType)) {
        extractorWrapper = null;
      } else {
        Extractor extractor;
        if (MimeTypes.APPLICATION_RAWCC.equals(containerMimeType)) {
          extractor = new RawCcExtractor(representation.format);
        } else if (mimeTypeIsWebm(containerMimeType)) {
          extractor = new MatroskaExtractor(MatroskaExtractor.FLAG_DISABLE_SEEK_FOR_CUES);
        } else {
          int flags = 0;
          if (enableEventMessageTrack) {
            flags |= FragmentedMp4Extractor.FLAG_ENABLE_EMSG_TRACK;
          }
          if (enableCea608Track) {
            flags |= FragmentedMp4Extractor.FLAG_ENABLE_CEA608_TRACK;
          }
          extractor = new FragmentedMp4Extractor(flags);
        }
        // Prefer drmInitData obtained from the manifest over drmInitData obtained from the stream,
        // as per DASH IF Interoperability Recommendations V3.0, 7.5.3.
        extractorWrapper = new ChunkExtractorWrapper(extractor, representation.format);
      }
      segmentIndex = representation.getIndex();
    }

    public void updateRepresentation(long newPeriodDurationUs, Representation newRepresentation)
        throws BehindLiveWindowException{
      DashSegmentIndex oldIndex = representation.getIndex();
      DashSegmentIndex newIndex = newRepresentation.getIndex();

      periodDurationUs = newPeriodDurationUs;
      representation = newRepresentation;
      if (oldIndex == null) {
        // Segment numbers cannot shift if the index isn't defined by the manifest.
        return;
      }

      segmentIndex = newIndex;
      if (!oldIndex.isExplicit()) {
        // Segment numbers cannot shift if the index isn't explicit.
        return;
      }

      int oldIndexSegmentCount = oldIndex.getSegmentCount(periodDurationUs);
      if (oldIndexSegmentCount == 0) {
        // Segment numbers cannot shift if the old index was empty.
        return;
      }

      int oldIndexLastSegmentNum = oldIndex.getFirstSegmentNum() + oldIndexSegmentCount - 1;
      long oldIndexEndTimeUs = oldIndex.getTimeUs(oldIndexLastSegmentNum)
          + oldIndex.getDurationUs(oldIndexLastSegmentNum, periodDurationUs);
      int newIndexFirstSegmentNum = newIndex.getFirstSegmentNum();
      long newIndexStartTimeUs = newIndex.getTimeUs(newIndexFirstSegmentNum);
      if (oldIndexEndTimeUs == newIndexStartTimeUs) {
        // The new index continues where the old one ended, with no overlap.
        segmentNumShift += oldIndexLastSegmentNum + 1 - newIndexFirstSegmentNum;
      } else if (oldIndexEndTimeUs < newIndexStartTimeUs) {
        // There's a gap between the old index and the new one which means we've slipped behind the
        // live window and can't proceed.
        throw new BehindLiveWindowException();
      } else {
        // The new index overlaps with the old one.
        segmentNumShift += oldIndex.getSegmentNum(newIndexStartTimeUs, periodDurationUs)
            - newIndexFirstSegmentNum;
      }
    }

    public int getFirstSegmentNum() {
      return segmentIndex.getFirstSegmentNum() + segmentNumShift;
    }

    public int getSegmentCount() {
      return segmentIndex.getSegmentCount(periodDurationUs);
    }

    public long getSegmentStartTimeUs(int segmentNum) {
      return segmentIndex.getTimeUs(segmentNum - segmentNumShift);
    }

    public long getSegmentEndTimeUs(int segmentNum) {
      return getSegmentStartTimeUs(segmentNum)
          + segmentIndex.getDurationUs(segmentNum - segmentNumShift, periodDurationUs);
    }

    public int getSegmentNum(long positionUs) {
      return segmentIndex.getSegmentNum(positionUs, periodDurationUs) + segmentNumShift;
    }

    public RangedUri getSegmentUrl(int segmentNum) {
      return segmentIndex.getSegmentUrl(segmentNum - segmentNumShift);
    }

    private static boolean mimeTypeIsWebm(String mimeType) {
      return mimeType.startsWith(MimeTypes.VIDEO_WEBM) || mimeType.startsWith(MimeTypes.AUDIO_WEBM)
          || mimeType.startsWith(MimeTypes.APPLICATION_WEBM);
    }

    private static boolean mimeTypeIsRawText(String mimeType) {
      return MimeTypes.isText(mimeType) || MimeTypes.APPLICATION_TTML.equals(mimeType);
    }

  }

}
