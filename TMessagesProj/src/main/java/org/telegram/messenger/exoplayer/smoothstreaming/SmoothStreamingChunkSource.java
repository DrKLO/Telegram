/*
 * Copyright (C) 2014 The Android Open Source Project
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
package org.telegram.messenger.exoplayer.smoothstreaming;

import android.net.Uri;
import android.os.SystemClock;
import android.util.Base64;
import android.util.SparseArray;
import org.telegram.messenger.exoplayer.BehindLiveWindowException;
import org.telegram.messenger.exoplayer.C;
import org.telegram.messenger.exoplayer.MediaFormat;
import org.telegram.messenger.exoplayer.chunk.Chunk;
import org.telegram.messenger.exoplayer.chunk.ChunkExtractorWrapper;
import org.telegram.messenger.exoplayer.chunk.ChunkOperationHolder;
import org.telegram.messenger.exoplayer.chunk.ChunkSource;
import org.telegram.messenger.exoplayer.chunk.ContainerMediaChunk;
import org.telegram.messenger.exoplayer.chunk.Format;
import org.telegram.messenger.exoplayer.chunk.Format.DecreasingBandwidthComparator;
import org.telegram.messenger.exoplayer.chunk.FormatEvaluator;
import org.telegram.messenger.exoplayer.chunk.FormatEvaluator.Evaluation;
import org.telegram.messenger.exoplayer.chunk.MediaChunk;
import org.telegram.messenger.exoplayer.drm.DrmInitData;
import org.telegram.messenger.exoplayer.drm.DrmInitData.SchemeInitData;
import org.telegram.messenger.exoplayer.extractor.mp4.FragmentedMp4Extractor;
import org.telegram.messenger.exoplayer.extractor.mp4.Track;
import org.telegram.messenger.exoplayer.extractor.mp4.TrackEncryptionBox;
import org.telegram.messenger.exoplayer.smoothstreaming.SmoothStreamingManifest.ProtectionElement;
import org.telegram.messenger.exoplayer.smoothstreaming.SmoothStreamingManifest.StreamElement;
import org.telegram.messenger.exoplayer.smoothstreaming.SmoothStreamingManifest.TrackElement;
import org.telegram.messenger.exoplayer.upstream.DataSource;
import org.telegram.messenger.exoplayer.upstream.DataSpec;
import org.telegram.messenger.exoplayer.util.Assertions;
import org.telegram.messenger.exoplayer.util.CodecSpecificDataUtil;
import org.telegram.messenger.exoplayer.util.ManifestFetcher;
import org.telegram.messenger.exoplayer.util.MimeTypes;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * An {@link ChunkSource} for SmoothStreaming.
 */
public class SmoothStreamingChunkSource implements ChunkSource,
    SmoothStreamingTrackSelector.Output {

  private static final int MINIMUM_MANIFEST_REFRESH_PERIOD_MS = 5000;
  private static final int INITIALIZATION_VECTOR_SIZE = 8;

  private final SmoothStreamingTrackSelector trackSelector;
  private final DataSource dataSource;
  private final Evaluation evaluation;
  private final long liveEdgeLatencyUs;
  private final TrackEncryptionBox[] trackEncryptionBoxes;
  private final ManifestFetcher<SmoothStreamingManifest> manifestFetcher;
  private final DrmInitData.Mapped drmInitData;
  private final FormatEvaluator adaptiveFormatEvaluator;
  private final boolean live;

  // The tracks exposed by this source.
  private final ArrayList<ExposedTrack> tracks;

  // Mappings from manifest track key.
  private final SparseArray<ChunkExtractorWrapper> extractorWrappers;
  private final SparseArray<MediaFormat> mediaFormats;

  private boolean prepareCalled;
  private SmoothStreamingManifest currentManifest;
  private int currentManifestChunkOffset;
  private boolean needManifestRefresh;
  private ExposedTrack enabledTrack;
  private IOException fatalError;

  /**
   * Constructor to use for live streaming.
   * <p>
   * May also be used for fixed duration content, in which case the call is equivalent to calling
   * the other constructor, passing {@code manifestFetcher.getManifest()} is the first argument.
   *
   * @param manifestFetcher A fetcher for the manifest, which must have already successfully
   *     completed an initial load.
   * @param trackSelector Selects tracks from the manifest to be exposed by this source.
   * @param dataSource A {@link DataSource} suitable for loading the media data.
   * @param adaptiveFormatEvaluator For adaptive tracks, selects from the available formats.
   * @param liveEdgeLatencyMs For live streams, the number of milliseconds that the playback should
   *     lag behind the "live edge" (i.e. the end of the most recently defined media in the
   *     manifest). Choosing a small value will minimize latency introduced by the player, however
   *     note that the value sets an upper bound on the length of media that the player can buffer.
   *     Hence a small value may increase the probability of rebuffering and playback failures.
   */
  public SmoothStreamingChunkSource(ManifestFetcher<SmoothStreamingManifest> manifestFetcher,
      SmoothStreamingTrackSelector trackSelector, DataSource dataSource,
      FormatEvaluator adaptiveFormatEvaluator, long liveEdgeLatencyMs) {
    this(manifestFetcher, manifestFetcher.getManifest(), trackSelector, dataSource,
        adaptiveFormatEvaluator, liveEdgeLatencyMs);
  }

  /**
   * Constructor to use for fixed duration content.
   *
   * @param manifest The manifest parsed from {@code baseUrl + "/Manifest"}.
   * @param trackSelector Selects tracks from the manifest to be exposed by this source.
   * @param dataSource A {@link DataSource} suitable for loading the media data.
   * @param adaptiveFormatEvaluator For adaptive tracks, selects from the available formats.
   */
  public SmoothStreamingChunkSource(SmoothStreamingManifest manifest,
      SmoothStreamingTrackSelector trackSelector, DataSource dataSource,
      FormatEvaluator adaptiveFormatEvaluator) {
    this(null, manifest, trackSelector, dataSource, adaptiveFormatEvaluator, 0);
  }

  private SmoothStreamingChunkSource(ManifestFetcher<SmoothStreamingManifest> manifestFetcher,
      SmoothStreamingManifest initialManifest, SmoothStreamingTrackSelector trackSelector,
      DataSource dataSource, FormatEvaluator adaptiveFormatEvaluator, long liveEdgeLatencyMs) {
    this.manifestFetcher = manifestFetcher;
    this.currentManifest = initialManifest;
    this.trackSelector = trackSelector;
    this.dataSource = dataSource;
    this.adaptiveFormatEvaluator = adaptiveFormatEvaluator;
    this.liveEdgeLatencyUs = liveEdgeLatencyMs * 1000;
    evaluation = new Evaluation();
    tracks = new ArrayList<>();
    extractorWrappers = new SparseArray<>();
    mediaFormats = new SparseArray<>();
    live = initialManifest.isLive;

    ProtectionElement protectionElement = initialManifest.protectionElement;
    if (protectionElement != null) {
      byte[] keyId = getProtectionElementKeyId(protectionElement.data);
      trackEncryptionBoxes = new TrackEncryptionBox[1];
      trackEncryptionBoxes[0] = new TrackEncryptionBox(true, INITIALIZATION_VECTOR_SIZE, keyId);
      drmInitData = new DrmInitData.Mapped();
      drmInitData.put(protectionElement.uuid,
          new SchemeInitData(MimeTypes.VIDEO_MP4, protectionElement.data));
    } else {
      trackEncryptionBoxes = null;
      drmInitData = null;
    }
  }

  // ChunkSource implementation.

  @Override
  public void maybeThrowError() throws IOException {
    if (fatalError != null) {
      throw fatalError;
    } else {
      manifestFetcher.maybeThrowError();
    }
  }

  @Override
  public boolean prepare() {
    if (!prepareCalled) {
      prepareCalled = true;
      try {
        trackSelector.selectTracks(currentManifest, this);
      } catch (IOException e) {
        fatalError = e;
      }
    }
    return fatalError == null;
  }

  @Override
  public int getTrackCount() {
    return tracks.size();
  }

  @Override
  public final MediaFormat getFormat(int track) {
    return tracks.get(track).trackFormat;
  }

  @Override
  public void enable(int track) {
    enabledTrack = tracks.get(track);
    if (enabledTrack.isAdaptive()) {
      adaptiveFormatEvaluator.enable();
    }
    if (manifestFetcher != null) {
      manifestFetcher.enable();
    }
  }

  @Override
  public void continueBuffering(long playbackPositionUs) {
    if (manifestFetcher == null || !currentManifest.isLive || fatalError != null) {
      return;
    }

    SmoothStreamingManifest newManifest = manifestFetcher.getManifest();
    if (currentManifest != newManifest && newManifest != null) {
      StreamElement currentElement = currentManifest.streamElements[enabledTrack.elementIndex];
      int currentElementChunkCount = currentElement.chunkCount;
      StreamElement newElement = newManifest.streamElements[enabledTrack.elementIndex];
      if (currentElementChunkCount == 0 || newElement.chunkCount == 0) {
        // There's no overlap between the old and new elements because at least one is empty.
        currentManifestChunkOffset += currentElementChunkCount;
      } else {
        long currentElementEndTimeUs = currentElement.getStartTimeUs(currentElementChunkCount - 1)
            + currentElement.getChunkDurationUs(currentElementChunkCount - 1);
        long newElementStartTimeUs = newElement.getStartTimeUs(0);
        if (currentElementEndTimeUs <= newElementStartTimeUs) {
          // There's no overlap between the old and new elements.
          currentManifestChunkOffset += currentElementChunkCount;
        } else {
          // The new element overlaps with the old one.
          currentManifestChunkOffset += currentElement.getChunkIndex(newElementStartTimeUs);
        }
      }
      currentManifest = newManifest;
      needManifestRefresh = false;
    }

    if (needManifestRefresh && (SystemClock.elapsedRealtime()
        > manifestFetcher.getManifestLoadStartTimestamp() + MINIMUM_MANIFEST_REFRESH_PERIOD_MS)) {
      manifestFetcher.requestRefresh();
    }
  }

  @Override
  public final void getChunkOperation(List<? extends MediaChunk> queue, long playbackPositionUs,
      ChunkOperationHolder out) {
    if (fatalError != null) {
      out.chunk = null;
      return;
    }

    evaluation.queueSize = queue.size();
    if (enabledTrack.isAdaptive()) {
      adaptiveFormatEvaluator.evaluate(queue, playbackPositionUs, enabledTrack.adaptiveFormats,
          evaluation);
    } else {
      evaluation.format = enabledTrack.fixedFormat;
      evaluation.trigger = Chunk.TRIGGER_MANUAL;
    }

    Format selectedFormat = evaluation.format;
    out.queueSize = evaluation.queueSize;

    if (selectedFormat == null) {
      out.chunk = null;
      return;
    } else if (out.queueSize == queue.size() && out.chunk != null
        && out.chunk.format.equals(selectedFormat)) {
      // We already have a chunk, and the evaluation hasn't changed either the format or the size
      // of the queue. Leave unchanged.
      return;
    }

    // In all cases where we return before instantiating a new chunk, we want out.chunk to be null.
    out.chunk = null;

    StreamElement streamElement = currentManifest.streamElements[enabledTrack.elementIndex];
    if (streamElement.chunkCount == 0) {
      if (currentManifest.isLive) {
        needManifestRefresh = true;
      } else {
        out.endOfStream = true;
      }
      return;
    }

    int chunkIndex;
    if (queue.isEmpty()) {
      if (live) {
        playbackPositionUs = getLiveSeekPosition(currentManifest, liveEdgeLatencyUs);
      }
      chunkIndex = streamElement.getChunkIndex(playbackPositionUs);
    } else {
      MediaChunk previous = queue.get(out.queueSize - 1);
      chunkIndex = previous.chunkIndex + 1 - currentManifestChunkOffset;
    }

    if (live && chunkIndex < 0) {
      // This is before the first chunk in the current manifest.
      fatalError = new BehindLiveWindowException();
      return;
    } else if (currentManifest.isLive) {
      if (chunkIndex >= streamElement.chunkCount) {
        // This is beyond the last chunk in the current manifest.
        needManifestRefresh = true;
        return;
      } else if (chunkIndex == streamElement.chunkCount - 1) {
        // This is the last chunk in the current manifest. Mark the manifest as being finished,
        // but continue to return the final chunk.
        needManifestRefresh = true;
      }
    } else if (chunkIndex >= streamElement.chunkCount) {
      out.endOfStream = true;
      return;
    }

    boolean isLastChunk = !currentManifest.isLive && chunkIndex == streamElement.chunkCount - 1;
    long chunkStartTimeUs = streamElement.getStartTimeUs(chunkIndex);
    long chunkEndTimeUs = isLastChunk ? -1
        : chunkStartTimeUs + streamElement.getChunkDurationUs(chunkIndex);
    int currentAbsoluteChunkIndex = chunkIndex + currentManifestChunkOffset;

    int manifestTrackIndex = getManifestTrackIndex(streamElement, selectedFormat);
    int manifestTrackKey = getManifestTrackKey(enabledTrack.elementIndex, manifestTrackIndex);
    Uri uri = streamElement.buildRequestUri(manifestTrackIndex, chunkIndex);
    Chunk mediaChunk = newMediaChunk(selectedFormat, uri, null,
        extractorWrappers.get(manifestTrackKey), drmInitData, dataSource, currentAbsoluteChunkIndex,
        chunkStartTimeUs, chunkEndTimeUs, evaluation.trigger, mediaFormats.get(manifestTrackKey),
        enabledTrack.adaptiveMaxWidth, enabledTrack.adaptiveMaxHeight);
    out.chunk = mediaChunk;
  }

  @Override
  public void onChunkLoadCompleted(Chunk chunk) {
    // Do nothing.
  }

  @Override
  public void onChunkLoadError(Chunk chunk, Exception e) {
    // Do nothing.
  }

  @Override
  public void disable(List<? extends MediaChunk> queue) {
    if (enabledTrack.isAdaptive()) {
      adaptiveFormatEvaluator.disable();
    }
    if (manifestFetcher != null) {
      manifestFetcher.disable();
    }
    evaluation.format = null;
    fatalError = null;
  }

  // SmoothStreamingTrackSelector.Output implementation.

  @Override
  public void adaptiveTrack(SmoothStreamingManifest manifest, int element, int[] trackIndices) {
    if (adaptiveFormatEvaluator == null) {
      // Do nothing.
      return;
    }
    MediaFormat maxHeightMediaFormat = null;
    StreamElement streamElement = manifest.streamElements[element];
    int maxWidth = -1;
    int maxHeight = -1;
    Format[] formats = new Format[trackIndices.length];
    for (int i = 0; i < formats.length; i++) {
      int manifestTrackIndex = trackIndices[i];
      formats[i] = streamElement.tracks[manifestTrackIndex].format;
      MediaFormat mediaFormat = initManifestTrack(manifest, element, manifestTrackIndex);
      if (maxHeightMediaFormat == null || mediaFormat.height > maxHeight) {
        maxHeightMediaFormat = mediaFormat;
      }
      maxWidth = Math.max(maxWidth, mediaFormat.width);
      maxHeight = Math.max(maxHeight, mediaFormat.height);
    }
    Arrays.sort(formats, new DecreasingBandwidthComparator());
    MediaFormat adaptiveMediaFormat = maxHeightMediaFormat.copyAsAdaptive(null);
    tracks.add(new ExposedTrack(adaptiveMediaFormat, element, formats, maxWidth, maxHeight));
  }

  @Override
  public void fixedTrack(SmoothStreamingManifest manifest, int element, int trackIndex) {
    MediaFormat mediaFormat = initManifestTrack(manifest, element, trackIndex);
    Format format = manifest.streamElements[element].tracks[trackIndex].format;
    tracks.add(new ExposedTrack(mediaFormat, element, format));
  }

  // Private methods.

  private MediaFormat initManifestTrack(SmoothStreamingManifest manifest, int elementIndex,
      int trackIndex) {
    int manifestTrackKey = getManifestTrackKey(elementIndex, trackIndex);
    MediaFormat mediaFormat = mediaFormats.get(manifestTrackKey);
    if (mediaFormat != null) {
      // Already initialized.
      return mediaFormat;
    }

    // Build the media format.
    long durationUs = live ? C.UNKNOWN_TIME_US : manifest.durationUs;
    StreamElement element = manifest.streamElements[elementIndex];
    Format format = element.tracks[trackIndex].format;
    byte[][] csdArray = element.tracks[trackIndex].csd;
    int mp4TrackType;
    switch (element.type) {
      case StreamElement.TYPE_VIDEO:
        mediaFormat = MediaFormat.createVideoFormat(format.id, format.mimeType, format.bitrate,
            MediaFormat.NO_VALUE, durationUs, format.width, format.height, Arrays.asList(csdArray));
        mp4TrackType = Track.TYPE_vide;
        break;
      case StreamElement.TYPE_AUDIO:
        List<byte[]> csd;
        if (csdArray != null) {
          csd = Arrays.asList(csdArray);
        } else {
          csd = Collections.singletonList(CodecSpecificDataUtil.buildAacAudioSpecificConfig(
              format.audioSamplingRate, format.audioChannels));
        }
        mediaFormat = MediaFormat.createAudioFormat(format.id, format.mimeType, format.bitrate,
            MediaFormat.NO_VALUE, durationUs, format.audioChannels, format.audioSamplingRate, csd,
            format.language);
        mp4TrackType = Track.TYPE_soun;
        break;
      case StreamElement.TYPE_TEXT:
        mediaFormat = MediaFormat.createTextFormat(format.id, format.mimeType, format.bitrate,
            durationUs, format.language);
        mp4TrackType = Track.TYPE_text;
        break;
      default:
        throw new IllegalStateException("Invalid type: " + element.type);
    }

    Track mp4Track = new Track(trackIndex, mp4TrackType, element.timescale, C.UNKNOWN_TIME_US,
        durationUs, mediaFormat, trackEncryptionBoxes, mp4TrackType == Track.TYPE_vide ? 4 : -1,
        null, null);
    // Build the extractor.
    FragmentedMp4Extractor mp4Extractor = new FragmentedMp4Extractor(
        FragmentedMp4Extractor.FLAG_WORKAROUND_EVERY_VIDEO_FRAME_IS_SYNC_FRAME
        | FragmentedMp4Extractor.FLAG_WORKAROUND_IGNORE_TFDT_BOX, mp4Track);

    // Store the format and a wrapper around the extractor.
    mediaFormats.put(manifestTrackKey, mediaFormat);
    extractorWrappers.put(manifestTrackKey, new ChunkExtractorWrapper(mp4Extractor));
    return mediaFormat;
  }

  /**
   * For live playbacks, determines the seek position that snaps playback to be
   * {@code liveEdgeLatencyUs} behind the live edge of the provided manifest.
   *
   * @param manifest The manifest.
   * @param liveEdgeLatencyUs The live edge latency, in microseconds.
   * @return The seek position in microseconds.
   */
  private static long getLiveSeekPosition(SmoothStreamingManifest manifest,
      long liveEdgeLatencyUs) {
    long liveEdgeTimestampUs = Long.MIN_VALUE;
    for (int i = 0; i < manifest.streamElements.length; i++) {
      StreamElement streamElement = manifest.streamElements[i];
      if (streamElement.chunkCount > 0) {
        long elementLiveEdgeTimestampUs =
            streamElement.getStartTimeUs(streamElement.chunkCount - 1)
            + streamElement.getChunkDurationUs(streamElement.chunkCount - 1);
        liveEdgeTimestampUs = Math.max(liveEdgeTimestampUs, elementLiveEdgeTimestampUs);
      }
    }
    return liveEdgeTimestampUs - liveEdgeLatencyUs;
  }

  private static int getManifestTrackIndex(StreamElement element, Format format) {
    TrackElement[] tracks = element.tracks;
    for (int i = 0; i < tracks.length; i++) {
      if (tracks[i].format.equals(format)) {
        return i;
      }
    }
    // Should never happen.
    throw new IllegalStateException("Invalid format: " + format);
  }

  private static MediaChunk newMediaChunk(Format formatInfo, Uri uri, String cacheKey,
      ChunkExtractorWrapper extractorWrapper, DrmInitData drmInitData, DataSource dataSource,
      int chunkIndex, long chunkStartTimeUs, long chunkEndTimeUs, int trigger,
      MediaFormat mediaFormat, int adaptiveMaxWidth, int adaptiveMaxHeight) {
    long offset = 0;
    DataSpec dataSpec = new DataSpec(uri, offset, -1, cacheKey);
    // In SmoothStreaming each chunk contains sample timestamps relative to the start of the chunk.
    // To convert them the absolute timestamps, we need to set sampleOffsetUs to -chunkStartTimeUs.
    return new ContainerMediaChunk(dataSource, dataSpec, trigger, formatInfo, chunkStartTimeUs,
        chunkEndTimeUs, chunkIndex, chunkStartTimeUs, extractorWrapper, mediaFormat,
        adaptiveMaxWidth, adaptiveMaxHeight, drmInitData, true, Chunk.NO_PARENT_ID);
  }

  private static int getManifestTrackKey(int elementIndex, int trackIndex) {
    Assertions.checkState(elementIndex <= 65536 && trackIndex <= 65536);
    return (elementIndex << 16) | trackIndex;
  }

  private static byte[] getProtectionElementKeyId(byte[] initData) {
    StringBuilder initDataStringBuilder = new StringBuilder();
    for (int i = 0; i < initData.length; i += 2) {
      initDataStringBuilder.append((char) initData[i]);
    }
    String initDataString = initDataStringBuilder.toString();
    String keyIdString = initDataString.substring(
        initDataString.indexOf("<KID>") + 5, initDataString.indexOf("</KID>"));
    byte[] keyId = Base64.decode(keyIdString, Base64.DEFAULT);
    swap(keyId, 0, 3);
    swap(keyId, 1, 2);
    swap(keyId, 4, 5);
    swap(keyId, 6, 7);
    return keyId;
  }

  private static void swap(byte[] data, int firstPosition, int secondPosition) {
    byte temp = data[firstPosition];
    data[firstPosition] = data[secondPosition];
    data[secondPosition] = temp;
  }

  // Private classes.

  private static final class ExposedTrack {

    public final MediaFormat trackFormat;

    private final int elementIndex;

    // Non-adaptive track variables.
    private final Format fixedFormat;

    // Adaptive track variables.
    private final Format[] adaptiveFormats;
    private final int adaptiveMaxWidth;
    private final int adaptiveMaxHeight;

    public ExposedTrack(MediaFormat trackFormat, int elementIndex, Format fixedFormat) {
      this.trackFormat = trackFormat;
      this.elementIndex = elementIndex;
      this.fixedFormat = fixedFormat;
      this.adaptiveFormats = null;
      this.adaptiveMaxWidth = MediaFormat.NO_VALUE;
      this.adaptiveMaxHeight = MediaFormat.NO_VALUE;
    }

    public ExposedTrack(MediaFormat trackFormat, int elementIndex, Format[] adaptiveFormats,
        int adaptiveMaxWidth, int adaptiveMaxHeight) {
      this.trackFormat = trackFormat;
      this.elementIndex = elementIndex;
      this.adaptiveFormats = adaptiveFormats;
      this.adaptiveMaxWidth = adaptiveMaxWidth;
      this.adaptiveMaxHeight = adaptiveMaxHeight;
      this.fixedFormat = null;
    }

    public boolean isAdaptive() {
      return adaptiveFormats != null;
    }

  }

}
