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

import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.net.Uri;
import android.os.Handler;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.drm.DrmSession;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.offline.StreamKey;
import com.google.android.exoplayer2.source.BaseMediaSource;
import com.google.android.exoplayer2.source.CompositeSequenceableLoaderFactory;
import com.google.android.exoplayer2.source.DefaultCompositeSequenceableLoaderFactory;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSourceEventListener;
import com.google.android.exoplayer2.source.MediaSourceEventListener.EventDispatcher;
import com.google.android.exoplayer2.source.MediaSourceFactory;
import com.google.android.exoplayer2.source.SequenceableLoader;
import com.google.android.exoplayer2.source.SinglePeriodTimeline;
import com.google.android.exoplayer2.source.hls.playlist.DefaultHlsPlaylistParserFactory;
import com.google.android.exoplayer2.source.hls.playlist.DefaultHlsPlaylistTracker;
import com.google.android.exoplayer2.source.hls.playlist.FilteringHlsPlaylistParserFactory;
import com.google.android.exoplayer2.source.hls.playlist.HlsMediaPlaylist;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylistParserFactory;
import com.google.android.exoplayer2.source.hls.playlist.HlsPlaylistTracker;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultLoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Assertions;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.util.List;

/** An HLS {@link MediaSource}. */
public final class HlsMediaSource extends BaseMediaSource
    implements HlsPlaylistTracker.PrimaryPlaylistListener {

  static {
    ExoPlayerLibraryInfo.registerModule("goog.exo.hls");
  }

  /**
   * The types of metadata that can be extracted from HLS streams.
   *
   * <p>Allowed values:
   *
   * <ul>
   *   <li>{@link #METADATA_TYPE_ID3}
   *   <li>{@link #METADATA_TYPE_EMSG}
   * </ul>
   *
   * <p>See {@link Factory#setMetadataType(int)}.
   */
  @Documented
  @Retention(SOURCE)
  @IntDef({METADATA_TYPE_ID3, METADATA_TYPE_EMSG})
  public @interface MetadataType {}

  /** Type for ID3 metadata in HLS streams. */
  public static final int METADATA_TYPE_ID3 = 1;
  /** Type for ESMG metadata in HLS streams. */
  public static final int METADATA_TYPE_EMSG = 3;

  /** Factory for {@link HlsMediaSource}s. */
  public static final class Factory implements MediaSourceFactory {

    private final HlsDataSourceFactory hlsDataSourceFactory;

    private HlsExtractorFactory extractorFactory;
    private HlsPlaylistParserFactory playlistParserFactory;
    @Nullable private List<StreamKey> streamKeys;
    private HlsPlaylistTracker.Factory playlistTrackerFactory;
    private CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory;
    private DrmSessionManager<?> drmSessionManager;
    private LoadErrorHandlingPolicy loadErrorHandlingPolicy;
    private boolean allowChunklessPreparation;
    @MetadataType private int metadataType;
    private boolean useSessionKeys;
    private boolean isCreateCalled;
    @Nullable private Object tag;

    /**
     * Creates a new factory for {@link HlsMediaSource}s.
     *
     * @param dataSourceFactory A data source factory that will be wrapped by a {@link
     *     DefaultHlsDataSourceFactory} to create {@link DataSource}s for manifests, segments and
     *     keys.
     */
    public Factory(DataSource.Factory dataSourceFactory) {
      this(new DefaultHlsDataSourceFactory(dataSourceFactory));
    }

    /**
     * Creates a new factory for {@link HlsMediaSource}s.
     *
     * @param hlsDataSourceFactory An {@link HlsDataSourceFactory} for {@link DataSource}s for
     *     manifests, segments and keys.
     */
    public Factory(HlsDataSourceFactory hlsDataSourceFactory) {
      this.hlsDataSourceFactory = Assertions.checkNotNull(hlsDataSourceFactory);
      playlistParserFactory = new DefaultHlsPlaylistParserFactory();
      playlistTrackerFactory = DefaultHlsPlaylistTracker.FACTORY;
      extractorFactory = HlsExtractorFactory.DEFAULT;
      drmSessionManager = DrmSessionManager.getDummyDrmSessionManager();
      loadErrorHandlingPolicy = new DefaultLoadErrorHandlingPolicy();
      compositeSequenceableLoaderFactory = new DefaultCompositeSequenceableLoaderFactory();
      metadataType = METADATA_TYPE_ID3;
    }

    /**
     * Sets a tag for the media source which will be published in the {@link
     * com.google.android.exoplayer2.Timeline} of the source as {@link
     * com.google.android.exoplayer2.Timeline.Window#tag}.
     *
     * @param tag A tag for the media source.
     * @return This factory, for convenience.
     * @throws IllegalStateException If one of the {@code create} methods has already been called.
     */
    public Factory setTag(@Nullable Object tag) {
      Assertions.checkState(!isCreateCalled);
      this.tag = tag;
      return this;
    }

    /**
     * Sets the factory for {@link Extractor}s for the segments. The default value is {@link
     * HlsExtractorFactory#DEFAULT}.
     *
     * @param extractorFactory An {@link HlsExtractorFactory} for {@link Extractor}s for the
     *     segments.
     * @return This factory, for convenience.
     * @throws IllegalStateException If one of the {@code create} methods has already been called.
     */
    public Factory setExtractorFactory(HlsExtractorFactory extractorFactory) {
      Assertions.checkState(!isCreateCalled);
      this.extractorFactory = Assertions.checkNotNull(extractorFactory);
      return this;
    }

    /**
     * Sets the {@link LoadErrorHandlingPolicy}. The default value is created by calling {@link
     * DefaultLoadErrorHandlingPolicy#DefaultLoadErrorHandlingPolicy()}.
     *
     * <p>Calling this method overrides any calls to {@link #setMinLoadableRetryCount(int)}.
     *
     * @param loadErrorHandlingPolicy A {@link LoadErrorHandlingPolicy}.
     * @return This factory, for convenience.
     * @throws IllegalStateException If one of the {@code create} methods has already been called.
     */
    public Factory setLoadErrorHandlingPolicy(LoadErrorHandlingPolicy loadErrorHandlingPolicy) {
      Assertions.checkState(!isCreateCalled);
      this.loadErrorHandlingPolicy = loadErrorHandlingPolicy;
      return this;
    }

    /**
     * Sets the minimum number of times to retry if a loading error occurs. The default value is
     * {@link DefaultLoadErrorHandlingPolicy#DEFAULT_MIN_LOADABLE_RETRY_COUNT}.
     *
     * <p>Calling this method is equivalent to calling {@link #setLoadErrorHandlingPolicy} with
     * {@link DefaultLoadErrorHandlingPolicy#DefaultLoadErrorHandlingPolicy(int)
     * DefaultLoadErrorHandlingPolicy(minLoadableRetryCount)}
     *
     * @param minLoadableRetryCount The minimum number of times to retry if a loading error occurs.
     * @return This factory, for convenience.
     * @throws IllegalStateException If one of the {@code create} methods has already been called.
     * @deprecated Use {@link #setLoadErrorHandlingPolicy(LoadErrorHandlingPolicy)} instead.
     */
    @Deprecated
    public Factory setMinLoadableRetryCount(int minLoadableRetryCount) {
      Assertions.checkState(!isCreateCalled);
      this.loadErrorHandlingPolicy = new DefaultLoadErrorHandlingPolicy(minLoadableRetryCount);
      return this;
    }

    /**
     * Sets the factory from which playlist parsers will be obtained. The default value is a {@link
     * DefaultHlsPlaylistParserFactory}.
     *
     * @param playlistParserFactory An {@link HlsPlaylistParserFactory}.
     * @return This factory, for convenience.
     * @throws IllegalStateException If one of the {@code create} methods has already been called.
     */
    public Factory setPlaylistParserFactory(HlsPlaylistParserFactory playlistParserFactory) {
      Assertions.checkState(!isCreateCalled);
      this.playlistParserFactory = Assertions.checkNotNull(playlistParserFactory);
      return this;
    }

    /**
     * Sets the {@link HlsPlaylistTracker} factory. The default value is {@link
     * DefaultHlsPlaylistTracker#FACTORY}.
     *
     * @param playlistTrackerFactory A factory for {@link HlsPlaylistTracker} instances.
     * @return This factory, for convenience.
     * @throws IllegalStateException If one of the {@code create} methods has already been called.
     */
    public Factory setPlaylistTrackerFactory(HlsPlaylistTracker.Factory playlistTrackerFactory) {
      Assertions.checkState(!isCreateCalled);
      this.playlistTrackerFactory = Assertions.checkNotNull(playlistTrackerFactory);
      return this;
    }

    /**
     * Sets the factory to create composite {@link SequenceableLoader}s for when this media source
     * loads data from multiple streams (video, audio etc...). The default is an instance of {@link
     * DefaultCompositeSequenceableLoaderFactory}.
     *
     * @param compositeSequenceableLoaderFactory A factory to create composite {@link
     *     SequenceableLoader}s for when this media source loads data from multiple streams (video,
     *     audio etc...).
     * @return This factory, for convenience.
     * @throws IllegalStateException If one of the {@code create} methods has already been called.
     */
    public Factory setCompositeSequenceableLoaderFactory(
        CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory) {
      Assertions.checkState(!isCreateCalled);
      this.compositeSequenceableLoaderFactory =
          Assertions.checkNotNull(compositeSequenceableLoaderFactory);
      return this;
    }

    /**
     * Sets whether chunkless preparation is allowed. If true, preparation without chunk downloads
     * will be enabled for streams that provide sufficient information in their master playlist.
     *
     * @param allowChunklessPreparation Whether chunkless preparation is allowed.
     * @return This factory, for convenience.
     * @throws IllegalStateException If one of the {@code create} methods has already been called.
     */
    public Factory setAllowChunklessPreparation(boolean allowChunklessPreparation) {
      Assertions.checkState(!isCreateCalled);
      this.allowChunklessPreparation = allowChunklessPreparation;
      return this;
    }

    /**
     * Sets the type of metadata to extract from the HLS source (defaults to {@link
     * #METADATA_TYPE_ID3}).
     *
     * <p>HLS supports in-band ID3 in both TS and fMP4 streams, but in the fMP4 case the data is
     * wrapped in an EMSG box [<a href="https://aomediacodec.github.io/av1-id3/">spec</a>].
     *
     * <p>If this is set to {@link #METADATA_TYPE_ID3} then raw ID3 metadata of will be extracted
     * from TS sources. From fMP4 streams EMSGs containing metadata of this type (in the variant
     * stream only) will be unwrapped to expose the inner data. All other in-band metadata will be
     * dropped.
     *
     * <p>If this is set to {@link #METADATA_TYPE_EMSG} then all EMSG data from the fMP4 variant
     * stream will be extracted. No metadata will be extracted from TS streams, since they don't
     * support EMSG.
     *
     * @param metadataType The type of metadata to extract.
     * @return This factory, for convenience.
     */
    public Factory setMetadataType(@MetadataType int metadataType) {
      Assertions.checkState(!isCreateCalled);
      this.metadataType = metadataType;
      return this;
    }

    /**
     * Sets whether to use #EXT-X-SESSION-KEY tags provided in the master playlist. If enabled, it's
     * assumed that any single session key declared in the master playlist can be used to obtain all
     * of the keys required for playback. For media where this is not true, this option should not
     * be enabled.
     *
     * @param useSessionKeys Whether to use #EXT-X-SESSION-KEY tags.
     * @return This factory, for convenience.
     */
    public Factory setUseSessionKeys(boolean useSessionKeys) {
      this.useSessionKeys = useSessionKeys;
      return this;
    }

    /**
     * @deprecated Use {@link #createMediaSource(Uri)} and {@link #addEventListener(Handler,
     *     MediaSourceEventListener)} instead.
     */
    @Deprecated
    public HlsMediaSource createMediaSource(
        Uri playlistUri,
        @Nullable Handler eventHandler,
        @Nullable MediaSourceEventListener eventListener) {
      HlsMediaSource mediaSource = createMediaSource(playlistUri);
      if (eventHandler != null && eventListener != null) {
        mediaSource.addEventListener(eventHandler, eventListener);
      }
      return mediaSource;
    }

    /**
     * Sets the {@link DrmSessionManager} to use for acquiring {@link DrmSession DrmSessions}. The
     * default value is {@link DrmSessionManager#DUMMY}.
     *
     * @param drmSessionManager The {@link DrmSessionManager}.
     * @return This factory, for convenience.
     * @throws IllegalStateException If one of the {@code create} methods has already been called.
     */
    @Override
    public Factory setDrmSessionManager(DrmSessionManager<?> drmSessionManager) {
      Assertions.checkState(!isCreateCalled);
      this.drmSessionManager =
          drmSessionManager != null
              ? drmSessionManager
              : DrmSessionManager.getDummyDrmSessionManager();
      return this;
    }

    /**
     * Returns a new {@link HlsMediaSource} using the current parameters.
     *
     * @return The new {@link HlsMediaSource}.
     */
    @Override
    public HlsMediaSource createMediaSource(Uri playlistUri) {
      isCreateCalled = true;
      if (streamKeys != null) {
        playlistParserFactory =
            new FilteringHlsPlaylistParserFactory(playlistParserFactory, streamKeys);
      }
      return new HlsMediaSource(
          playlistUri,
          hlsDataSourceFactory,
          extractorFactory,
          compositeSequenceableLoaderFactory,
          drmSessionManager,
          loadErrorHandlingPolicy,
          playlistTrackerFactory.createTracker(
              hlsDataSourceFactory, loadErrorHandlingPolicy, playlistParserFactory),
          allowChunklessPreparation,
          metadataType,
          useSessionKeys,
          tag);
    }

    @Override
    public Factory setStreamKeys(List<StreamKey> streamKeys) {
      Assertions.checkState(!isCreateCalled);
      this.streamKeys = streamKeys;
      return this;
    }

    @Override
    public int[] getSupportedTypes() {
      return new int[] {C.TYPE_HLS};
    }

  }

  private final HlsExtractorFactory extractorFactory;
  private final Uri manifestUri;
  private final HlsDataSourceFactory dataSourceFactory;
  private final CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory;
  private final DrmSessionManager<?> drmSessionManager;
  private final LoadErrorHandlingPolicy loadErrorHandlingPolicy;
  private final boolean allowChunklessPreparation;
  private final @MetadataType int metadataType;
  private final boolean useSessionKeys;
  private final HlsPlaylistTracker playlistTracker;
  @Nullable private final Object tag;

  @Nullable private TransferListener mediaTransferListener;

  private HlsMediaSource(
      Uri manifestUri,
      HlsDataSourceFactory dataSourceFactory,
      HlsExtractorFactory extractorFactory,
      CompositeSequenceableLoaderFactory compositeSequenceableLoaderFactory,
      DrmSessionManager<?> drmSessionManager,
      LoadErrorHandlingPolicy loadErrorHandlingPolicy,
      HlsPlaylistTracker playlistTracker,
      boolean allowChunklessPreparation,
      @MetadataType int metadataType,
      boolean useSessionKeys,
      @Nullable Object tag) {
    this.manifestUri = manifestUri;
    this.dataSourceFactory = dataSourceFactory;
    this.extractorFactory = extractorFactory;
    this.compositeSequenceableLoaderFactory = compositeSequenceableLoaderFactory;
    this.drmSessionManager = drmSessionManager;
    this.loadErrorHandlingPolicy = loadErrorHandlingPolicy;
    this.playlistTracker = playlistTracker;
    this.allowChunklessPreparation = allowChunklessPreparation;
    this.metadataType = metadataType;
    this.useSessionKeys = useSessionKeys;
    this.tag = tag;
  }

  @Override
  @Nullable
  public Object getTag() {
    return tag;
  }

  @Override
  protected void prepareSourceInternal(@Nullable TransferListener mediaTransferListener) {
    this.mediaTransferListener = mediaTransferListener;
    drmSessionManager.prepare();
    EventDispatcher eventDispatcher = createEventDispatcher(/* mediaPeriodId= */ null);
    playlistTracker.start(manifestUri, eventDispatcher, /* listener= */ this);
  }

  @Override
  public void maybeThrowSourceInfoRefreshError() throws IOException {
    playlistTracker.maybeThrowPrimaryPlaylistRefreshError();
  }

  @Override
  public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator, long startPositionUs) {
    EventDispatcher eventDispatcher = createEventDispatcher(id);
    return new HlsMediaPeriod(
        extractorFactory,
        playlistTracker,
        dataSourceFactory,
        mediaTransferListener,
        drmSessionManager,
        loadErrorHandlingPolicy,
        eventDispatcher,
        allocator,
        compositeSequenceableLoaderFactory,
        allowChunklessPreparation,
        metadataType,
        useSessionKeys);
  }

  @Override
  public void releasePeriod(MediaPeriod mediaPeriod) {
    ((HlsMediaPeriod) mediaPeriod).release();
  }

  @Override
  protected void releaseSourceInternal() {
    playlistTracker.stop();
    drmSessionManager.release();
  }

  @Override
  public void onPrimaryPlaylistRefreshed(HlsMediaPlaylist playlist) {
    SinglePeriodTimeline timeline;
    long windowStartTimeMs = playlist.hasProgramDateTime ? C.usToMs(playlist.startTimeUs)
        : C.TIME_UNSET;
    // For playlist types EVENT and VOD we know segments are never removed, so the presentation
    // started at the same time as the window. Otherwise, we don't know the presentation start time.
    long presentationStartTimeMs =
        playlist.playlistType == HlsMediaPlaylist.PLAYLIST_TYPE_EVENT
                || playlist.playlistType == HlsMediaPlaylist.PLAYLIST_TYPE_VOD
            ? windowStartTimeMs
            : C.TIME_UNSET;
    long windowDefaultStartPositionUs = playlist.startOffsetUs;
    // masterPlaylist is non-null because the first playlist has been fetched by now.
    HlsManifest manifest =
        new HlsManifest(Assertions.checkNotNull(playlistTracker.getMasterPlaylist()), playlist);
    if (playlistTracker.isLive()) {
      long offsetFromInitialStartTimeUs =
          playlist.startTimeUs - playlistTracker.getInitialStartTimeUs();
      long periodDurationUs =
          playlist.hasEndTag ? offsetFromInitialStartTimeUs + playlist.durationUs : C.TIME_UNSET;
      List<HlsMediaPlaylist.Segment> segments = playlist.segments;
      if (windowDefaultStartPositionUs == C.TIME_UNSET) {
        windowDefaultStartPositionUs = 0;
        if (!segments.isEmpty()) {
          int defaultStartSegmentIndex = Math.max(0, segments.size() - 3);
          // We attempt to set the default start position to be at least twice the target duration
          // behind the live edge.
          long minStartPositionUs = playlist.durationUs - playlist.targetDurationUs * 2;
          while (defaultStartSegmentIndex > 0
              && segments.get(defaultStartSegmentIndex).relativeStartTimeUs > minStartPositionUs) {
            defaultStartSegmentIndex--;
          }
          windowDefaultStartPositionUs = segments.get(defaultStartSegmentIndex).relativeStartTimeUs;
        }
      }
      timeline =
          new SinglePeriodTimeline(
              presentationStartTimeMs,
              windowStartTimeMs,
              periodDurationUs,
              /* windowDurationUs= */ playlist.durationUs,
              /* windowPositionInPeriodUs= */ offsetFromInitialStartTimeUs,
              windowDefaultStartPositionUs,
              /* isSeekable= */ true,
              /* isDynamic= */ !playlist.hasEndTag,
              /* isLive= */ true,
              manifest,
              tag);
    } else /* not live */ {
      if (windowDefaultStartPositionUs == C.TIME_UNSET) {
        windowDefaultStartPositionUs = 0;
      }
      timeline =
          new SinglePeriodTimeline(
              presentationStartTimeMs,
              windowStartTimeMs,
              /* periodDurationUs= */ playlist.durationUs,
              /* windowDurationUs= */ playlist.durationUs,
              /* windowPositionInPeriodUs= */ 0,
              windowDefaultStartPositionUs,
              /* isSeekable= */ true,
              /* isDynamic= */ false,
              /* isLive= */ false,
              manifest,
              tag);
    }
    refreshSourceInfo(timeline);
  }

}
