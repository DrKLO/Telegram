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
package com.google.android.exoplayer2.source.hls;

import android.net.Uri;
import android.text.TextUtils;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.analytics.PlayerId;
import com.google.android.exoplayer2.drm.DrmInitData;
import com.google.android.exoplayer2.drm.DrmSession;
import com.google.android.exoplayer2.drm.DrmSessionEventListener;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.offline.StreamKey;
import com.google.android.exoplayer2.source.CompositeSequenceableLoaderFactory;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSourceEventListener.EventDispatcher;
import com.google.android.exoplayer2.source.SampleStream;
import com.google.android.exoplayer2.source.SequenceableLoader;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.hls.playlist.HlsMultivariantPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsMultivariantPlaylist.Rendition;
import com.google.android.exoplayer2.source.hls.playlist.HlsMultivariantPlaylist.Variant;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylistTracker;
import com.google.android.exoplayer2.trackselection.ExoTrackSelection;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import com.google.common.primitives.Ints;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.checkerframework.checker.nullness.compatqual.NullableType;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** A {@link MediaPeriod} that loads an HLS stream. */
public final class HlsMediaPeriod implements MediaPeriod, HlsPlaylistTracker.PlaylistEventListener {

  private final HlsExtractorFactory extractorFactory;
  private final HlsPlaylistTracker playlistTracker;
  private final HlsDataSourceFactory dataSourceFactory;
  @Nullable private final TransferListener mediaTransferListener;
  private final DrmSessionManager drmSessionManager;
  private final DrmSessionEventListener.EventDispatcher drmEventDispatcher;
  private final LoadErrorHandlingPolicy loadErrorHandlingPolicy;
  private final EventDispatcher eventDispatcher;
  private final Allocator allocator;
  private final IdentityHashMap<SampleStream, Integer> streamWrapperIndices;
  private final TimestampAdjusterProvider timestampAdjusterProvider;
  private final CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory;
  private final boolean allowChunklessPreparation;
  private final @HlsMediaSource.MetadataType int metadataType;
  private final boolean useSessionKeys;
  private final PlayerId playerId;
  private final HlsSampleStreamWrapper.Callback sampleStreamWrapperCallback;

  @Nullable private MediaPeriod.Callback mediaPeriodCallback;
  private int pendingPrepareCount;
  private @MonotonicNonNull TrackGroupArray trackGroups;
  private HlsSampleStreamWrapper[] sampleStreamWrappers;
  private HlsSampleStreamWrapper[] enabledSampleStreamWrappers;
  // Maps sample stream wrappers to variant/rendition index by matching array positions.
  private int[][] manifestUrlIndicesPerWrapper;
  private int audioVideoSampleStreamWrapperCount;
  private SequenceableLoader compositeSequenceableLoader;

  /**
   * Creates an HLS media period.
   *
   * @param extractorFactory An {@link HlsExtractorFactory} for {@link Extractor}s for the segments.
   * @param playlistTracker A tracker for HLS playlists.
   * @param dataSourceFactory An {@link HlsDataSourceFactory} for {@link DataSource}s for segments
   *     and keys.
   * @param mediaTransferListener The transfer listener to inform of any media data transfers. May
   *     be null if no listener is available.
   * @param drmSessionManager The {@link DrmSessionManager} to acquire {@link DrmSession
   *     DrmSessions} with.
   * @param loadErrorHandlingPolicy A {@link LoadErrorHandlingPolicy}.
   * @param eventDispatcher A dispatcher to notify of events.
   * @param allocator An {@link Allocator} from which to obtain media buffer allocations.
   * @param compositeSequenceableLoaderFactory A factory to create composite {@link
   *     SequenceableLoader}s for when this media source loads data from multiple streams.
   * @param allowChunklessPreparation Whether chunkless preparation is allowed.
   * @param useSessionKeys Whether to use #EXT-X-SESSION-KEY tags.
   */
  public HlsMediaPeriod(
      HlsExtractorFactory extractorFactory,
      HlsPlaylistTracker playlistTracker,
      HlsDataSourceFactory dataSourceFactory,
      @Nullable TransferListener mediaTransferListener,
      DrmSessionManager drmSessionManager,
      DrmSessionEventListener.EventDispatcher drmEventDispatcher,
      LoadErrorHandlingPolicy loadErrorHandlingPolicy,
      EventDispatcher eventDispatcher,
      Allocator allocator,
      CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory,
      boolean allowChunklessPreparation,
      @HlsMediaSource.MetadataType int metadataType,
      boolean useSessionKeys,
      PlayerId playerId) {
    this.extractorFactory = extractorFactory;
    this.playlistTracker = playlistTracker;
    this.dataSourceFactory = dataSourceFactory;
    this.mediaTransferListener = mediaTransferListener;
    this.drmSessionManager = drmSessionManager;
    this.drmEventDispatcher = drmEventDispatcher;
    this.loadErrorHandlingPolicy = loadErrorHandlingPolicy;
    this.eventDispatcher = eventDispatcher;
    this.allocator = allocator;
    this.compositeSequenceableLoaderFactory = compositeSequenceableLoaderFactory;
    this.allowChunklessPreparation = allowChunklessPreparation;
    this.metadataType = metadataType;
    this.useSessionKeys = useSessionKeys;
    this.playerId = playerId;
    sampleStreamWrapperCallback = new SampleStreamWrapperCallback();
    compositeSequenceableLoader =
        compositeSequenceableLoaderFactory.createCompositeSequenceableLoader();
    streamWrapperIndices = new IdentityHashMap<>();
    timestampAdjusterProvider = new TimestampAdjusterProvider();
    sampleStreamWrappers = new HlsSampleStreamWrapper[0];
    enabledSampleStreamWrappers = new HlsSampleStreamWrapper[0];
    manifestUrlIndicesPerWrapper = new int[0][];
  }

  public void release() {
    playlistTracker.removeListener(this);
    for (HlsSampleStreamWrapper sampleStreamWrapper : sampleStreamWrappers) {
      sampleStreamWrapper.release();
    }
    mediaPeriodCallback = null;
  }

  @Override
  public void prepare(Callback callback, long positionUs) {
    this.mediaPeriodCallback = callback;
    playlistTracker.addListener(this);
    buildAndPrepareSampleStreamWrappers(positionUs);
  }

  @Override
  public void maybeThrowPrepareError() throws IOException {
    for (HlsSampleStreamWrapper sampleStreamWrapper : sampleStreamWrappers) {
      sampleStreamWrapper.maybeThrowPrepareError();
    }
  }

  @Override
  public TrackGroupArray getTrackGroups() {
    // trackGroups will only be null if period hasn't been prepared or has been released.
    return Assertions.checkNotNull(trackGroups);
  }

  // TODO: When the multivariant playlist does not de-duplicate variants by URL and allows
  // Renditions with null URLs, this method must be updated to calculate stream keys that are
  // compatible with those that may already be persisted for offline.
  @Override
  public List<StreamKey> getStreamKeys(List<ExoTrackSelection> trackSelections) {
    // See HlsMultivariantPlaylist.copy for interpretation of StreamKeys.
    HlsMultivariantPlaylist multivariantPlaylist =
        Assertions.checkNotNull(playlistTracker.getMultivariantPlaylist());
    boolean hasVariants = !multivariantPlaylist.variants.isEmpty();
    int audioWrapperOffset = hasVariants ? 1 : 0;
    // Subtitle sample stream wrappers are held last.
    int subtitleWrapperOffset = sampleStreamWrappers.length - multivariantPlaylist.subtitles.size();

    TrackGroupArray mainWrapperTrackGroups;
    int mainWrapperPrimaryGroupIndex;
    int[] mainWrapperVariantIndices;
    if (hasVariants) {
      HlsSampleStreamWrapper mainWrapper = sampleStreamWrappers[0];
      mainWrapperVariantIndices = manifestUrlIndicesPerWrapper[0];
      mainWrapperTrackGroups = mainWrapper.getTrackGroups();
      mainWrapperPrimaryGroupIndex = mainWrapper.getPrimaryTrackGroupIndex();
    } else {
      mainWrapperVariantIndices = new int[0];
      mainWrapperTrackGroups = TrackGroupArray.EMPTY;
      mainWrapperPrimaryGroupIndex = 0;
    }

    List<StreamKey> streamKeys = new ArrayList<>();
    boolean needsPrimaryTrackGroupSelection = false;
    boolean hasPrimaryTrackGroupSelection = false;
    for (ExoTrackSelection trackSelection : trackSelections) {
      TrackGroup trackSelectionGroup = trackSelection.getTrackGroup();
      int mainWrapperTrackGroupIndex = mainWrapperTrackGroups.indexOf(trackSelectionGroup);
      if (mainWrapperTrackGroupIndex != C.INDEX_UNSET) {
        if (mainWrapperTrackGroupIndex == mainWrapperPrimaryGroupIndex) {
          // Primary group in main wrapper.
          hasPrimaryTrackGroupSelection = true;
          for (int i = 0; i < trackSelection.length(); i++) {
            int variantIndex = mainWrapperVariantIndices[trackSelection.getIndexInTrackGroup(i)];
            streamKeys.add(
                new StreamKey(HlsMultivariantPlaylist.GROUP_INDEX_VARIANT, variantIndex));
          }
        } else {
          // Embedded group in main wrapper.
          needsPrimaryTrackGroupSelection = true;
        }
      } else {
        // Audio or subtitle group.
        for (int i = audioWrapperOffset; i < sampleStreamWrappers.length; i++) {
          TrackGroupArray wrapperTrackGroups = sampleStreamWrappers[i].getTrackGroups();
          int selectedTrackGroupIndex = wrapperTrackGroups.indexOf(trackSelectionGroup);
          if (selectedTrackGroupIndex != C.INDEX_UNSET) {
            int groupIndexType =
                i < subtitleWrapperOffset
                    ? HlsMultivariantPlaylist.GROUP_INDEX_AUDIO
                    : HlsMultivariantPlaylist.GROUP_INDEX_SUBTITLE;
            int[] selectedWrapperUrlIndices = manifestUrlIndicesPerWrapper[i];
            for (int trackIndex = 0; trackIndex < trackSelection.length(); trackIndex++) {
              int renditionIndex =
                  selectedWrapperUrlIndices[trackSelection.getIndexInTrackGroup(trackIndex)];
              streamKeys.add(new StreamKey(groupIndexType, renditionIndex));
            }
            break;
          }
        }
      }
    }
    if (needsPrimaryTrackGroupSelection && !hasPrimaryTrackGroupSelection) {
      // A track selection includes a variant-embedded track, but no variant is added yet. We use
      // the valid variant with the lowest bitrate to reduce overhead.
      int lowestBitrateIndex = mainWrapperVariantIndices[0];
      int lowestBitrate =
          multivariantPlaylist.variants.get(mainWrapperVariantIndices[0]).format.bitrate;
      for (int i = 1; i < mainWrapperVariantIndices.length; i++) {
        int variantBitrate =
            multivariantPlaylist.variants.get(mainWrapperVariantIndices[i]).format.bitrate;
        if (variantBitrate < lowestBitrate) {
          lowestBitrate = variantBitrate;
          lowestBitrateIndex = mainWrapperVariantIndices[i];
        }
      }
      streamKeys.add(
          new StreamKey(HlsMultivariantPlaylist.GROUP_INDEX_VARIANT, lowestBitrateIndex));
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
    // Map each selection and stream onto a child period index.
    int[] streamChildIndices = new int[selections.length];
    int[] selectionChildIndices = new int[selections.length];
    for (int i = 0; i < selections.length; i++) {
      streamChildIndices[i] =
          streams[i] == null ? C.INDEX_UNSET : streamWrapperIndices.get(streams[i]);
      selectionChildIndices[i] = C.INDEX_UNSET;
      if (selections[i] != null) {
        TrackGroup trackGroup = selections[i].getTrackGroup();
        for (int j = 0; j < sampleStreamWrappers.length; j++) {
          if (sampleStreamWrappers[j].getTrackGroups().indexOf(trackGroup) != C.INDEX_UNSET) {
            selectionChildIndices[i] = j;
            break;
          }
        }
      }
    }

    boolean forceReset = false;
    streamWrapperIndices.clear();
    // Select tracks for each child, copying the resulting streams back into a new streams array.
    SampleStream[] newStreams = new SampleStream[selections.length];
    @NullableType SampleStream[] childStreams = new SampleStream[selections.length];
    @NullableType ExoTrackSelection[] childSelections = new ExoTrackSelection[selections.length];
    int newEnabledSampleStreamWrapperCount = 0;
    HlsSampleStreamWrapper[] newEnabledSampleStreamWrappers =
        new HlsSampleStreamWrapper[sampleStreamWrappers.length];
    for (int i = 0; i < sampleStreamWrappers.length; i++) {
      for (int j = 0; j < selections.length; j++) {
        childStreams[j] = streamChildIndices[j] == i ? streams[j] : null;
        childSelections[j] = selectionChildIndices[j] == i ? selections[j] : null;
      }
      HlsSampleStreamWrapper sampleStreamWrapper = sampleStreamWrappers[i];
      boolean wasReset =
          sampleStreamWrapper.selectTracks(
              childSelections,
              mayRetainStreamFlags,
              childStreams,
              streamResetFlags,
              positionUs,
              forceReset);
      boolean wrapperEnabled = false;
      for (int j = 0; j < selections.length; j++) {
        SampleStream childStream = childStreams[j];
        if (selectionChildIndices[j] == i) {
          // Assert that the child provided a stream for the selection.
          Assertions.checkNotNull(childStream);
          newStreams[j] = childStream;
          wrapperEnabled = true;
          streamWrapperIndices.put(childStream, i);
        } else if (streamChildIndices[j] == i) {
          // Assert that the child cleared any previous stream.
          Assertions.checkState(childStream == null);
        }
      }
      if (wrapperEnabled) {
        newEnabledSampleStreamWrappers[newEnabledSampleStreamWrapperCount] = sampleStreamWrapper;
        if (newEnabledSampleStreamWrapperCount++ == 0) {
          // The first enabled wrapper is always allowed to initialize timestamp adjusters. Note
          // that the first wrapper will correspond to a variant, or else an audio rendition, or
          // else a text rendition, in that order.
          sampleStreamWrapper.setIsTimestampMaster(true);
          if (wasReset
              || enabledSampleStreamWrappers.length == 0
              || sampleStreamWrapper != enabledSampleStreamWrappers[0]) {
            // The wrapper responsible for initializing the timestamp adjusters was reset or
            // changed. We need to reset the timestamp adjuster provider and all other wrappers.
            timestampAdjusterProvider.reset();
            forceReset = true;
          }
        } else {
          // Additional wrappers are also allowed to initialize timestamp adjusters if they contain
          // audio or video, since they are expected to contain dense samples. Text wrappers are not
          // permitted except in the case above in which no variant or audio rendition wrappers are
          // enabled.
          sampleStreamWrapper.setIsTimestampMaster(i < audioVideoSampleStreamWrapperCount);
        }
      }
    }
    // Copy the new streams back into the streams array.
    System.arraycopy(newStreams, 0, streams, 0, newStreams.length);
    // Update the local state.
    enabledSampleStreamWrappers =
        Util.nullSafeArrayCopy(newEnabledSampleStreamWrappers, newEnabledSampleStreamWrapperCount);
    compositeSequenceableLoader =
        compositeSequenceableLoaderFactory.createCompositeSequenceableLoader(
            enabledSampleStreamWrappers);
    return positionUs;
  }

  @Override
  public void discardBuffer(long positionUs, boolean toKeyframe) {
    for (HlsSampleStreamWrapper sampleStreamWrapper : enabledSampleStreamWrappers) {
      sampleStreamWrapper.discardBuffer(positionUs, toKeyframe);
    }
  }

  @Override
  public void reevaluateBuffer(long positionUs) {
    compositeSequenceableLoader.reevaluateBuffer(positionUs);
  }

  @Override
  public boolean continueLoading(long positionUs) {
    if (trackGroups == null) {
      // Preparation is still going on.
      for (HlsSampleStreamWrapper wrapper : sampleStreamWrappers) {
        wrapper.continuePreparing();
      }
      return false;
    } else {
      return compositeSequenceableLoader.continueLoading(positionUs);
    }
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
    if (enabledSampleStreamWrappers.length > 0) {
      // We need to reset all wrappers if the one responsible for initializing timestamp adjusters
      // is reset. Else each wrapper can decide whether to reset independently.
      boolean forceReset = enabledSampleStreamWrappers[0].seekToUs(positionUs, false);
      for (int i = 1; i < enabledSampleStreamWrappers.length; i++) {
        enabledSampleStreamWrappers[i].seekToUs(positionUs, forceReset);
      }
      if (forceReset) {
        timestampAdjusterProvider.reset();
      }
    }
    return positionUs;
  }

  @Override
  public long getAdjustedSeekPositionUs(long positionUs, SeekParameters seekParameters) {
    long seekTargetUs = positionUs;
    for (HlsSampleStreamWrapper sampleStreamWrapper : enabledSampleStreamWrappers) {
      if (sampleStreamWrapper.isVideoSampleStream()) {
        seekTargetUs = sampleStreamWrapper.getAdjustedSeekPositionUs(positionUs, seekParameters);
        break;
      }
    }
    return seekTargetUs;
  }

  // HlsSampleStreamWrapper.Callback implementation.

  // PlaylistListener implementation.

  @Override
  public void onPlaylistChanged() {
    for (HlsSampleStreamWrapper streamWrapper : sampleStreamWrappers) {
      streamWrapper.onPlaylistUpdated();
    }
    mediaPeriodCallback.onContinueLoadingRequested(this);
  }

  @Override
  public boolean onPlaylistError(
      Uri url, LoadErrorHandlingPolicy.LoadErrorInfo loadErrorInfo, boolean forceRetry) {
    boolean exclusionSucceeded = true;
    for (HlsSampleStreamWrapper streamWrapper : sampleStreamWrappers) {
      exclusionSucceeded &= streamWrapper.onPlaylistError(url, loadErrorInfo, forceRetry);
    }
    mediaPeriodCallback.onContinueLoadingRequested(this);
    return exclusionSucceeded;
  }

  // Internal methods.

  private void buildAndPrepareSampleStreamWrappers(long positionUs) {
    HlsMultivariantPlaylist multivariantPlaylist =
        Assertions.checkNotNull(playlistTracker.getMultivariantPlaylist());
    Map<String, DrmInitData> overridingDrmInitData =
        useSessionKeys
            ? deriveOverridingDrmInitData(multivariantPlaylist.sessionKeyDrmInitData)
            : Collections.emptyMap();

    boolean hasVariants = !multivariantPlaylist.variants.isEmpty();
    List<Rendition> audioRenditions = multivariantPlaylist.audios;
    List<Rendition> subtitleRenditions = multivariantPlaylist.subtitles;

    pendingPrepareCount = 0;
    ArrayList<HlsSampleStreamWrapper> sampleStreamWrappers = new ArrayList<>();
    ArrayList<int[]> manifestUrlIndicesPerWrapper = new ArrayList<>();

    if (hasVariants) {
      buildAndPrepareMainSampleStreamWrapper(
          multivariantPlaylist,
          positionUs,
          sampleStreamWrappers,
          manifestUrlIndicesPerWrapper,
          overridingDrmInitData);
    }

    // TODO: Build video stream wrappers here.

    buildAndPrepareAudioSampleStreamWrappers(
        positionUs,
        audioRenditions,
        sampleStreamWrappers,
        manifestUrlIndicesPerWrapper,
        overridingDrmInitData);

    audioVideoSampleStreamWrapperCount = sampleStreamWrappers.size();

    // Subtitle stream wrappers. We can always use multivariant playlist information to prepare
    // these.
    for (int i = 0; i < subtitleRenditions.size(); i++) {
      Rendition subtitleRendition = subtitleRenditions.get(i);
      String sampleStreamWrapperUid = "subtitle:" + i + ":" + subtitleRendition.name;
      HlsSampleStreamWrapper sampleStreamWrapper =
          buildSampleStreamWrapper(
              sampleStreamWrapperUid,
              C.TRACK_TYPE_TEXT,
              new Uri[] {subtitleRendition.url},
              new Format[] {subtitleRendition.format},
              null,
              Collections.emptyList(),
              overridingDrmInitData,
              positionUs);
      manifestUrlIndicesPerWrapper.add(new int[] {i});
      sampleStreamWrappers.add(sampleStreamWrapper);
      sampleStreamWrapper.prepareWithMultivariantPlaylistInfo(
          new TrackGroup[] {new TrackGroup(sampleStreamWrapperUid, subtitleRendition.format)},
          /* primaryTrackGroupIndex= */ 0);
    }

    this.sampleStreamWrappers = sampleStreamWrappers.toArray(new HlsSampleStreamWrapper[0]);
    this.manifestUrlIndicesPerWrapper = manifestUrlIndicesPerWrapper.toArray(new int[0][]);
    pendingPrepareCount = this.sampleStreamWrappers.length;
    // Set timestamp masters and trigger preparation (if not already prepared)
    for (int i = 0; i < audioVideoSampleStreamWrapperCount; i++) {
      this.sampleStreamWrappers[i].setIsTimestampMaster(true);
    }
    for (HlsSampleStreamWrapper sampleStreamWrapper : this.sampleStreamWrappers) {
      sampleStreamWrapper.continuePreparing();
    }
    // All wrappers are enabled during preparation.
    enabledSampleStreamWrappers = this.sampleStreamWrappers;
  }

  /**
   * This method creates and starts preparation of the main {@link HlsSampleStreamWrapper}.
   *
   * <p>The main sample stream wrapper is the first element of {@link #sampleStreamWrappers}. It
   * provides {@link SampleStream}s for the variant urls in the multivariant playlist. It may be
   * adaptive and may contain multiple muxed tracks.
   *
   * <p>If chunkless preparation is allowed, the media period will try preparation without segment
   * downloads. This is only possible if variants contain the CODECS attribute. If not, traditional
   * preparation with segment downloads will take place. The following points apply to chunkless
   * preparation:
   *
   * <ul>
   *   <li>A muxed audio track will be exposed if the codecs list contain an audio entry and the
   *       multivariant playlist either contains an EXT-X-MEDIA tag without the URI attribute or
   *       does not contain any EXT-X-MEDIA tag.
   *   <li>Closed captions will only be exposed if they are declared by the multivariant playlist.
   *   <li>An ID3 track is exposed preemptively, in case the segments contain an ID3 track.
   * </ul>
   *
   * @param multivariantPlaylist The HLS multivariant playlist.
   * @param positionUs If preparation requires any chunk downloads, the position in microseconds at
   *     which downloading should start. Ignored otherwise.
   * @param sampleStreamWrappers List to which the built main sample stream wrapper should be added.
   * @param manifestUrlIndicesPerWrapper List to which the selected variant indices should be added.
   * @param overridingDrmInitData Overriding {@link DrmInitData}, keyed by protection scheme type
   *     (i.e. {@link DrmInitData#schemeType}).
   */
  private void buildAndPrepareMainSampleStreamWrapper(
      HlsMultivariantPlaylist multivariantPlaylist,
      long positionUs,
      List<HlsSampleStreamWrapper> sampleStreamWrappers,
      List<int[]> manifestUrlIndicesPerWrapper,
      Map<String, DrmInitData> overridingDrmInitData) {
    int[] variantTypes = new int[multivariantPlaylist.variants.size()];
    int videoVariantCount = 0;
    int audioVariantCount = 0;
    for (int i = 0; i < multivariantPlaylist.variants.size(); i++) {
      Variant variant = multivariantPlaylist.variants.get(i);
      Format format = variant.format;
      if (format.height > 0 || Util.getCodecsOfType(format.codecs, C.TRACK_TYPE_VIDEO) != null) {
        variantTypes[i] = C.TRACK_TYPE_VIDEO;
        videoVariantCount++;
      } else if (Util.getCodecsOfType(format.codecs, C.TRACK_TYPE_AUDIO) != null) {
        variantTypes[i] = C.TRACK_TYPE_AUDIO;
        audioVariantCount++;
      } else {
        variantTypes[i] = C.TRACK_TYPE_UNKNOWN;
      }
    }
    boolean useVideoVariantsOnly = false;
    boolean useNonAudioVariantsOnly = false;
    int selectedVariantsCount = variantTypes.length;
    if (videoVariantCount > 0) {
      // We've identified some variants as definitely containing video. Assume variants within the
      // multivariant playlist are marked consistently, and hence that we have the full set. Filter
      // out any other variants, which are likely to be audio only.
      useVideoVariantsOnly = true;
      selectedVariantsCount = videoVariantCount;
    } else if (audioVariantCount < variantTypes.length) {
      // We've identified some variants, but not all, as being audio only. Filter them out to leave
      // the remaining variants, which are likely to contain video.
      useNonAudioVariantsOnly = true;
      selectedVariantsCount = variantTypes.length - audioVariantCount;
    }
    Uri[] selectedPlaylistUrls = new Uri[selectedVariantsCount];
    Format[] selectedPlaylistFormats = new Format[selectedVariantsCount];
    int[] selectedVariantIndices = new int[selectedVariantsCount];
    int outIndex = 0;
    for (int i = 0; i < multivariantPlaylist.variants.size(); i++) {
      if ((!useVideoVariantsOnly || variantTypes[i] == C.TRACK_TYPE_VIDEO)
          && (!useNonAudioVariantsOnly || variantTypes[i] != C.TRACK_TYPE_AUDIO)) {
        Variant variant = multivariantPlaylist.variants.get(i);
        selectedPlaylistUrls[outIndex] = variant.url;
        selectedPlaylistFormats[outIndex] = variant.format;
        selectedVariantIndices[outIndex++] = i;
      }
    }
    String codecs = selectedPlaylistFormats[0].codecs;
    int numberOfVideoCodecs = Util.getCodecCountOfType(codecs, C.TRACK_TYPE_VIDEO);
    int numberOfAudioCodecs = Util.getCodecCountOfType(codecs, C.TRACK_TYPE_AUDIO);
    boolean codecsStringAllowsChunklessPreparation =
        (numberOfAudioCodecs == 1
                || (numberOfAudioCodecs == 0 && multivariantPlaylist.audios.isEmpty()))
            && numberOfVideoCodecs <= 1
            && numberOfAudioCodecs + numberOfVideoCodecs > 0;
    @C.TrackType
    int trackType =
        !useVideoVariantsOnly && numberOfAudioCodecs > 0
            ? C.TRACK_TYPE_AUDIO
            : C.TRACK_TYPE_DEFAULT;
    String sampleStreamWrapperUid = "main";
    HlsSampleStreamWrapper sampleStreamWrapper =
        buildSampleStreamWrapper(
            sampleStreamWrapperUid,
            trackType,
            selectedPlaylistUrls,
            selectedPlaylistFormats,
            multivariantPlaylist.muxedAudioFormat,
            multivariantPlaylist.muxedCaptionFormats,
            overridingDrmInitData,
            positionUs);
    sampleStreamWrappers.add(sampleStreamWrapper);
    manifestUrlIndicesPerWrapper.add(selectedVariantIndices);
    if (allowChunklessPreparation && codecsStringAllowsChunklessPreparation) {
      List<TrackGroup> muxedTrackGroups = new ArrayList<>();
      if (numberOfVideoCodecs > 0) {
        Format[] videoFormats = new Format[selectedVariantsCount];
        for (int i = 0; i < videoFormats.length; i++) {
          videoFormats[i] = deriveVideoFormat(selectedPlaylistFormats[i]);
        }
        muxedTrackGroups.add(new TrackGroup(sampleStreamWrapperUid, videoFormats));

        if (numberOfAudioCodecs > 0
            && (multivariantPlaylist.muxedAudioFormat != null
                || multivariantPlaylist.audios.isEmpty())) {
          muxedTrackGroups.add(
              new TrackGroup(
                  /* id= */ sampleStreamWrapperUid + ":audio",
                  deriveAudioFormat(
                      selectedPlaylistFormats[0],
                      multivariantPlaylist.muxedAudioFormat,
                      /* isPrimaryTrackInVariant= */ false)));
        }
        List<Format> ccFormats = multivariantPlaylist.muxedCaptionFormats;
        if (ccFormats != null) {
          for (int i = 0; i < ccFormats.size(); i++) {
            String ccId = sampleStreamWrapperUid + ":cc:" + i;
            muxedTrackGroups.add(new TrackGroup(ccId, ccFormats.get(i)));
          }
        }
      } else /* numberOfAudioCodecs > 0 */ {
        // Variants only contain audio.
        Format[] audioFormats = new Format[selectedVariantsCount];
        for (int i = 0; i < audioFormats.length; i++) {
          audioFormats[i] =
              deriveAudioFormat(
                  /* variantFormat= */ selectedPlaylistFormats[i],
                  multivariantPlaylist.muxedAudioFormat,
                  /* isPrimaryTrackInVariant= */ true);
        }
        muxedTrackGroups.add(new TrackGroup(sampleStreamWrapperUid, audioFormats));
      }

      TrackGroup id3TrackGroup =
          new TrackGroup(
              /* id= */ sampleStreamWrapperUid + ":id3",
              new Format.Builder()
                  .setId("ID3")
                  .setSampleMimeType(MimeTypes.APPLICATION_ID3)
                  .build());
      muxedTrackGroups.add(id3TrackGroup);

      sampleStreamWrapper.prepareWithMultivariantPlaylistInfo(
          muxedTrackGroups.toArray(new TrackGroup[0]),
          /* primaryTrackGroupIndex= */ 0,
          /* optionalTrackGroupsIndices...= */ muxedTrackGroups.indexOf(id3TrackGroup));
    }
  }

  private void buildAndPrepareAudioSampleStreamWrappers(
      long positionUs,
      List<Rendition> audioRenditions,
      List<HlsSampleStreamWrapper> sampleStreamWrappers,
      List<int[]> manifestUrlsIndicesPerWrapper,
      Map<String, DrmInitData> overridingDrmInitData) {
    ArrayList<Uri> scratchPlaylistUrls =
        new ArrayList<>(/* initialCapacity= */ audioRenditions.size());
    ArrayList<Format> scratchPlaylistFormats =
        new ArrayList<>(/* initialCapacity= */ audioRenditions.size());
    ArrayList<Integer> scratchIndicesList =
        new ArrayList<>(/* initialCapacity= */ audioRenditions.size());
    HashSet<String> alreadyGroupedNames = new HashSet<>();
    for (int renditionByNameIndex = 0;
        renditionByNameIndex < audioRenditions.size();
        renditionByNameIndex++) {
      String name = audioRenditions.get(renditionByNameIndex).name;
      if (!alreadyGroupedNames.add(name)) {
        // This name already has a corresponding group.
        continue;
      }

      boolean codecStringsAllowChunklessPreparation = true;
      scratchPlaylistUrls.clear();
      scratchPlaylistFormats.clear();
      scratchIndicesList.clear();
      // Group all renditions with matching name.
      for (int renditionIndex = 0; renditionIndex < audioRenditions.size(); renditionIndex++) {
        if (Util.areEqual(name, audioRenditions.get(renditionIndex).name)) {
          Rendition rendition = audioRenditions.get(renditionIndex);
          scratchIndicesList.add(renditionIndex);
          scratchPlaylistUrls.add(rendition.url);
          scratchPlaylistFormats.add(rendition.format);
          codecStringsAllowChunklessPreparation &=
              Util.getCodecCountOfType(rendition.format.codecs, C.TRACK_TYPE_AUDIO) == 1;
        }
      }

      String sampleStreamWrapperUid = "audio:" + name;
      HlsSampleStreamWrapper sampleStreamWrapper =
          buildSampleStreamWrapper(
              sampleStreamWrapperUid,
              C.TRACK_TYPE_AUDIO,
              scratchPlaylistUrls.toArray(Util.castNonNullTypeArray(new Uri[0])),
              scratchPlaylistFormats.toArray(new Format[0]),
              /* muxedAudioFormat= */ null,
              /* muxedCaptionFormats= */ Collections.emptyList(),
              overridingDrmInitData,
              positionUs);
      manifestUrlsIndicesPerWrapper.add(Ints.toArray(scratchIndicesList));
      sampleStreamWrappers.add(sampleStreamWrapper);

      if (allowChunklessPreparation && codecStringsAllowChunklessPreparation) {
        Format[] renditionFormats = scratchPlaylistFormats.toArray(new Format[0]);
        sampleStreamWrapper.prepareWithMultivariantPlaylistInfo(
            new TrackGroup[] {new TrackGroup(sampleStreamWrapperUid, renditionFormats)},
            /* primaryTrackGroupIndex= */ 0);
      }
    }
  }

  private HlsSampleStreamWrapper buildSampleStreamWrapper(
      String uid,
      @C.TrackType int trackType,
      Uri[] playlistUrls,
      Format[] playlistFormats,
      @Nullable Format muxedAudioFormat,
      @Nullable List<Format> muxedCaptionFormats,
      Map<String, DrmInitData> overridingDrmInitData,
      long positionUs) {
    HlsChunkSource defaultChunkSource =
        new HlsChunkSource(
            extractorFactory,
            playlistTracker,
            playlistUrls,
            playlistFormats,
            dataSourceFactory,
            mediaTransferListener,
            timestampAdjusterProvider,
            muxedCaptionFormats,
            playerId);
    return new HlsSampleStreamWrapper(
        uid,
        trackType,
        /* callback= */ sampleStreamWrapperCallback,
        defaultChunkSource,
        overridingDrmInitData,
        allocator,
        positionUs,
        muxedAudioFormat,
        drmSessionManager,
        drmEventDispatcher,
        loadErrorHandlingPolicy,
        eventDispatcher,
        metadataType);
  }

  private static Map<String, DrmInitData> deriveOverridingDrmInitData(
      List<DrmInitData> sessionKeyDrmInitData) {
    ArrayList<DrmInitData> mutableSessionKeyDrmInitData = new ArrayList<>(sessionKeyDrmInitData);
    HashMap<String, DrmInitData> drmInitDataBySchemeType = new HashMap<>();
    for (int i = 0; i < mutableSessionKeyDrmInitData.size(); i++) {
      DrmInitData drmInitData = sessionKeyDrmInitData.get(i);
      String scheme = drmInitData.schemeType;
      // Merge any subsequent drmInitData instances that have the same scheme type. This is valid
      // due to the assumptions documented on HlsMediaSource.Builder.setUseSessionKeys, and is
      // necessary to get data for different CDNs (e.g. Widevine and PlayReady) into a single
      // drmInitData.
      int j = i + 1;
      while (j < mutableSessionKeyDrmInitData.size()) {
        DrmInitData nextDrmInitData = mutableSessionKeyDrmInitData.get(j);
        if (TextUtils.equals(nextDrmInitData.schemeType, scheme)) {
          drmInitData = drmInitData.merge(nextDrmInitData);
          mutableSessionKeyDrmInitData.remove(j);
        } else {
          j++;
        }
      }
      drmInitDataBySchemeType.put(scheme, drmInitData);
    }
    return drmInitDataBySchemeType;
  }

  private static Format deriveVideoFormat(Format variantFormat) {
    @Nullable String codecs = Util.getCodecsOfType(variantFormat.codecs, C.TRACK_TYPE_VIDEO);
    @Nullable String sampleMimeType = MimeTypes.getMediaMimeType(codecs);
    return new Format.Builder()
        .setId(variantFormat.id)
        .setLabel(variantFormat.label)
        .setContainerMimeType(variantFormat.containerMimeType)
        .setSampleMimeType(sampleMimeType)
        .setCodecs(codecs)
        .setMetadata(variantFormat.metadata)
        .setAverageBitrate(variantFormat.averageBitrate)
        .setPeakBitrate(variantFormat.peakBitrate)
        .setWidth(variantFormat.width)
        .setHeight(variantFormat.height)
        .setFrameRate(variantFormat.frameRate)
        .setSelectionFlags(variantFormat.selectionFlags)
        .setRoleFlags(variantFormat.roleFlags)
        .build();
  }

  private static Format deriveAudioFormat(
      Format variantFormat, @Nullable Format mediaTagFormat, boolean isPrimaryTrackInVariant) {
    @Nullable String codecs;
    @Nullable Metadata metadata;
    int channelCount = Format.NO_VALUE;
    int selectionFlags = 0;
    int roleFlags = 0;
    @Nullable String language = null;
    @Nullable String label = null;
    if (mediaTagFormat != null) {
      codecs = mediaTagFormat.codecs;
      metadata = mediaTagFormat.metadata;
      channelCount = mediaTagFormat.channelCount;
      selectionFlags = mediaTagFormat.selectionFlags;
      roleFlags = mediaTagFormat.roleFlags;
      language = mediaTagFormat.language;
      label = mediaTagFormat.label;
    } else {
      codecs = Util.getCodecsOfType(variantFormat.codecs, C.TRACK_TYPE_AUDIO);
      metadata = variantFormat.metadata;
      if (isPrimaryTrackInVariant) {
        channelCount = variantFormat.channelCount;
        selectionFlags = variantFormat.selectionFlags;
        roleFlags = variantFormat.roleFlags;
        language = variantFormat.language;
        label = variantFormat.label;
      }
    }
    @Nullable String sampleMimeType = MimeTypes.getMediaMimeType(codecs);
    int averageBitrate = isPrimaryTrackInVariant ? variantFormat.averageBitrate : Format.NO_VALUE;
    int peakBitrate = isPrimaryTrackInVariant ? variantFormat.peakBitrate : Format.NO_VALUE;
    return new Format.Builder()
        .setId(variantFormat.id)
        .setLabel(label)
        .setContainerMimeType(variantFormat.containerMimeType)
        .setSampleMimeType(sampleMimeType)
        .setCodecs(codecs)
        .setMetadata(metadata)
        .setAverageBitrate(averageBitrate)
        .setPeakBitrate(peakBitrate)
        .setChannelCount(channelCount)
        .setSelectionFlags(selectionFlags)
        .setRoleFlags(roleFlags)
        .setLanguage(language)
        .build();
  }

  private class SampleStreamWrapperCallback implements HlsSampleStreamWrapper.Callback {
    @Override
    public void onPrepared() {
      if (--pendingPrepareCount > 0) {
        return;
      }

      int totalTrackGroupCount = 0;
      for (HlsSampleStreamWrapper sampleStreamWrapper : sampleStreamWrappers) {
        totalTrackGroupCount += sampleStreamWrapper.getTrackGroups().length;
      }
      TrackGroup[] trackGroupArray = new TrackGroup[totalTrackGroupCount];
      int trackGroupIndex = 0;
      for (HlsSampleStreamWrapper sampleStreamWrapper : sampleStreamWrappers) {
        int wrapperTrackGroupCount = sampleStreamWrapper.getTrackGroups().length;
        for (int j = 0; j < wrapperTrackGroupCount; j++) {
          trackGroupArray[trackGroupIndex++] = sampleStreamWrapper.getTrackGroups().get(j);
        }
      }
      trackGroups = new TrackGroupArray(trackGroupArray);
      mediaPeriodCallback.onPrepared(HlsMediaPeriod.this);
    }

    @Override
    public void onPlaylistRefreshRequired(Uri url) {
      playlistTracker.refreshPlaylist(url);
    }

    @Override
    public void onContinueLoadingRequested(HlsSampleStreamWrapper sampleStreamWrapper) {
      mediaPeriodCallback.onContinueLoadingRequested(HlsMediaPeriod.this);
    }
  }
}
