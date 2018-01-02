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
package org.telegram.messenger.exoplayer2.source.hls;

import android.os.Handler;
import android.text.TextUtils;
import org.telegram.messenger.exoplayer2.C;
import org.telegram.messenger.exoplayer2.Format;
import org.telegram.messenger.exoplayer2.source.AdaptiveMediaSourceEventListener.EventDispatcher;
import org.telegram.messenger.exoplayer2.source.CompositeSequenceableLoader;
import org.telegram.messenger.exoplayer2.source.MediaPeriod;
import org.telegram.messenger.exoplayer2.source.SampleStream;
import org.telegram.messenger.exoplayer2.source.TrackGroup;
import org.telegram.messenger.exoplayer2.source.TrackGroupArray;
import org.telegram.messenger.exoplayer2.source.hls.playlist.HlsMasterPlaylist;
import org.telegram.messenger.exoplayer2.source.hls.playlist.HlsMasterPlaylist.HlsUrl;
import org.telegram.messenger.exoplayer2.source.hls.playlist.HlsPlaylistTracker;
import org.telegram.messenger.exoplayer2.trackselection.TrackSelection;
import org.telegram.messenger.exoplayer2.upstream.Allocator;
import org.telegram.messenger.exoplayer2.util.Assertions;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * A {@link MediaPeriod} that loads an HLS stream.
 */
public final class HlsMediaPeriod implements MediaPeriod, HlsSampleStreamWrapper.Callback,
    HlsPlaylistTracker.PlaylistEventListener {

  private final HlsPlaylistTracker playlistTracker;
  private final HlsDataSourceFactory dataSourceFactory;
  private final int minLoadableRetryCount;
  private final EventDispatcher eventDispatcher;
  private final Allocator allocator;
  private final IdentityHashMap<SampleStream, Integer> streamWrapperIndices;
  private final TimestampAdjusterProvider timestampAdjusterProvider;
  private final Handler continueLoadingHandler;

  private Callback callback;
  private int pendingPrepareCount;
  private TrackGroupArray trackGroups;
  private HlsSampleStreamWrapper[] sampleStreamWrappers;
  private HlsSampleStreamWrapper[] enabledSampleStreamWrappers;
  private CompositeSequenceableLoader sequenceableLoader;

  public HlsMediaPeriod(HlsPlaylistTracker playlistTracker, HlsDataSourceFactory dataSourceFactory,
      int minLoadableRetryCount, EventDispatcher eventDispatcher, Allocator allocator) {
    this.playlistTracker = playlistTracker;
    this.dataSourceFactory = dataSourceFactory;
    this.minLoadableRetryCount = minLoadableRetryCount;
    this.eventDispatcher = eventDispatcher;
    this.allocator = allocator;
    streamWrapperIndices = new IdentityHashMap<>();
    timestampAdjusterProvider = new TimestampAdjusterProvider();
    continueLoadingHandler = new Handler();
    sampleStreamWrappers = new HlsSampleStreamWrapper[0];
    enabledSampleStreamWrappers = new HlsSampleStreamWrapper[0];
  }

  public void release() {
    playlistTracker.removeListener(this);
    continueLoadingHandler.removeCallbacksAndMessages(null);
    for (HlsSampleStreamWrapper sampleStreamWrapper : sampleStreamWrappers) {
      sampleStreamWrapper.release();
    }
  }

  @Override
  public void prepare(Callback callback, long positionUs) {
    this.callback = callback;
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
    return trackGroups;
  }

  @Override
  public long selectTracks(TrackSelection[] selections, boolean[] mayRetainStreamFlags,
      SampleStream[] streams, boolean[] streamResetFlags, long positionUs) {
    // Map each selection and stream onto a child period index.
    int[] streamChildIndices = new int[selections.length];
    int[] selectionChildIndices = new int[selections.length];
    for (int i = 0; i < selections.length; i++) {
      streamChildIndices[i] = streams[i] == null ? C.INDEX_UNSET
          : streamWrapperIndices.get(streams[i]);
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
    SampleStream[] childStreams = new SampleStream[selections.length];
    TrackSelection[] childSelections = new TrackSelection[selections.length];
    int newEnabledSampleStreamWrapperCount = 0;
    HlsSampleStreamWrapper[] newEnabledSampleStreamWrappers =
        new HlsSampleStreamWrapper[sampleStreamWrappers.length];
    for (int i = 0; i < sampleStreamWrappers.length; i++) {
      for (int j = 0; j < selections.length; j++) {
        childStreams[j] = streamChildIndices[j] == i ? streams[j] : null;
        childSelections[j] = selectionChildIndices[j] == i ? selections[j] : null;
      }
      HlsSampleStreamWrapper sampleStreamWrapper = sampleStreamWrappers[i];
      boolean wasReset = sampleStreamWrapper.selectTracks(childSelections, mayRetainStreamFlags,
          childStreams, streamResetFlags, positionUs, forceReset);
      boolean wrapperEnabled = false;
      for (int j = 0; j < selections.length; j++) {
        if (selectionChildIndices[j] == i) {
          // Assert that the child provided a stream for the selection.
          Assertions.checkState(childStreams[j] != null);
          newStreams[j] = childStreams[j];
          wrapperEnabled = true;
          streamWrapperIndices.put(childStreams[j], i);
        } else if (streamChildIndices[j] == i) {
          // Assert that the child cleared any previous stream.
          Assertions.checkState(childStreams[j] == null);
        }
      }
      if (wrapperEnabled) {
        newEnabledSampleStreamWrappers[newEnabledSampleStreamWrapperCount] = sampleStreamWrapper;
        if (newEnabledSampleStreamWrapperCount++ == 0) {
          // The first enabled wrapper is responsible for initializing timestamp adjusters. This
          // way, if enabled, variants are responsible. Else audio renditions. Else text renditions.
          sampleStreamWrapper.setIsTimestampMaster(true);
          if (wasReset || enabledSampleStreamWrappers.length == 0
              || sampleStreamWrapper != enabledSampleStreamWrappers[0]) {
            // The wrapper responsible for initializing the timestamp adjusters was reset or
            // changed. We need to reset the timestamp adjuster provider and all other wrappers.
            timestampAdjusterProvider.reset();
            forceReset = true;
          }
        } else {
          sampleStreamWrapper.setIsTimestampMaster(false);
        }
      }
    }
    // Copy the new streams back into the streams array.
    System.arraycopy(newStreams, 0, streams, 0, newStreams.length);
    // Update the local state.
    enabledSampleStreamWrappers = Arrays.copyOf(newEnabledSampleStreamWrappers,
        newEnabledSampleStreamWrapperCount);
    sequenceableLoader = new CompositeSequenceableLoader(enabledSampleStreamWrappers);
    return positionUs;
  }

  @Override
  public void discardBuffer(long positionUs) {
    for (HlsSampleStreamWrapper sampleStreamWrapper : enabledSampleStreamWrappers) {
      sampleStreamWrapper.discardBuffer(positionUs);
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

  // HlsSampleStreamWrapper.Callback implementation.

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
    callback.onPrepared(this);
  }

  @Override
  public void onPlaylistRefreshRequired(HlsUrl url) {
    playlistTracker.refreshPlaylist(url);
  }

  @Override
  public void onContinueLoadingRequested(HlsSampleStreamWrapper sampleStreamWrapper) {
    if (trackGroups == null) {
      // Still preparing.
      return;
    }
    callback.onContinueLoadingRequested(this);
  }

  // PlaylistListener implementation.

  @Override
  public void onPlaylistChanged() {
    continuePreparingOrLoading();
  }

  @Override
  public void onPlaylistBlacklisted(HlsUrl url, long blacklistMs) {
    for (HlsSampleStreamWrapper streamWrapper : sampleStreamWrappers) {
      streamWrapper.onPlaylistBlacklisted(url, blacklistMs);
    }
    continuePreparingOrLoading();
  }

  // Internal methods.

  private void buildAndPrepareSampleStreamWrappers(long positionUs) {
    HlsMasterPlaylist masterPlaylist = playlistTracker.getMasterPlaylist();
    // Build the default stream wrapper.
    List<HlsUrl> selectedVariants = new ArrayList<>(masterPlaylist.variants);
    ArrayList<HlsUrl> definiteVideoVariants = new ArrayList<>();
    ArrayList<HlsUrl> definiteAudioOnlyVariants = new ArrayList<>();
    for (int i = 0; i < selectedVariants.size(); i++) {
      HlsUrl variant = selectedVariants.get(i);
      if (variant.format.height > 0 || variantHasExplicitCodecWithPrefix(variant, "avc")) {
        definiteVideoVariants.add(variant);
      } else if (variantHasExplicitCodecWithPrefix(variant, "mp4a")) {
        definiteAudioOnlyVariants.add(variant);
      }
    }
    if (!definiteVideoVariants.isEmpty()) {
      // We've identified some variants as definitely containing video. Assume variants within the
      // master playlist are marked consistently, and hence that we have the full set. Filter out
      // any other variants, which are likely to be audio only.
      selectedVariants = definiteVideoVariants;
    } else if (definiteAudioOnlyVariants.size() < selectedVariants.size()) {
      // We've identified some variants, but not all, as being audio only. Filter them out to leave
      // the remaining variants, which are likely to contain video.
      selectedVariants.removeAll(definiteAudioOnlyVariants);
    } else {
      // Leave the enabled variants unchanged. They're likely either all video or all audio.
    }
    List<HlsUrl> audioRenditions = masterPlaylist.audios;
    List<HlsUrl> subtitleRenditions = masterPlaylist.subtitles;
    sampleStreamWrappers = new HlsSampleStreamWrapper[1 /* variants */ + audioRenditions.size()
        + subtitleRenditions.size()];
    int currentWrapperIndex = 0;
    pendingPrepareCount = sampleStreamWrappers.length;

    Assertions.checkArgument(!selectedVariants.isEmpty());
    HlsUrl[] variants = new HlsMasterPlaylist.HlsUrl[selectedVariants.size()];
    selectedVariants.toArray(variants);
    HlsSampleStreamWrapper sampleStreamWrapper = buildSampleStreamWrapper(C.TRACK_TYPE_DEFAULT,
        variants, masterPlaylist.muxedAudioFormat, masterPlaylist.muxedCaptionFormats, positionUs);
    sampleStreamWrappers[currentWrapperIndex++] = sampleStreamWrapper;
    sampleStreamWrapper.setIsTimestampMaster(true);
    sampleStreamWrapper.continuePreparing();

    // TODO: Build video stream wrappers here.

    // Build audio stream wrappers.
    for (int i = 0; i < audioRenditions.size(); i++) {
      sampleStreamWrapper = buildSampleStreamWrapper(C.TRACK_TYPE_AUDIO,
          new HlsUrl[] {audioRenditions.get(i)}, null, Collections.<Format>emptyList(), positionUs);
      sampleStreamWrappers[currentWrapperIndex++] = sampleStreamWrapper;
      sampleStreamWrapper.continuePreparing();
    }

    // Build subtitle stream wrappers.
    for (int i = 0; i < subtitleRenditions.size(); i++) {
      HlsUrl url = subtitleRenditions.get(i);
      sampleStreamWrapper = buildSampleStreamWrapper(C.TRACK_TYPE_TEXT, new HlsUrl[] {url}, null,
          Collections.<Format>emptyList(), positionUs);
      sampleStreamWrapper.prepareSingleTrack(url.format);
      sampleStreamWrappers[currentWrapperIndex++] = sampleStreamWrapper;
    }

    // All wrappers are enabled during preparation.
    enabledSampleStreamWrappers = sampleStreamWrappers;
  }

  private HlsSampleStreamWrapper buildSampleStreamWrapper(int trackType, HlsUrl[] variants,
      Format muxedAudioFormat, List<Format> muxedCaptionFormats, long positionUs) {
    HlsChunkSource defaultChunkSource = new HlsChunkSource(playlistTracker, variants,
        dataSourceFactory, timestampAdjusterProvider, muxedCaptionFormats);
    return new HlsSampleStreamWrapper(trackType, this, defaultChunkSource, allocator, positionUs,
        muxedAudioFormat, minLoadableRetryCount, eventDispatcher);
  }

  private void continuePreparingOrLoading() {
    if (trackGroups != null) {
      callback.onContinueLoadingRequested(this);
    } else {
      // Some of the wrappers were waiting for their media playlist to prepare.
      for (HlsSampleStreamWrapper wrapper : sampleStreamWrappers) {
        wrapper.continuePreparing();
      }
    }
  }

  private static boolean variantHasExplicitCodecWithPrefix(HlsUrl variant, String prefix) {
    String codecs = variant.format.codecs;
    if (TextUtils.isEmpty(codecs)) {
      return false;
    }
    String[] codecArray = codecs.split("(\\s*,\\s*)|(\\s*$)");
    for (String codec : codecArray) {
      if (codec.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }

}
