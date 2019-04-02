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
package com.google.android.exoplayer2.source.hls.playlist;

import android.net.Uri;
import android.os.Handler;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.source.MediaSourceEventListener.EventDispatcher;
import com.google.android.exoplayer2.source.hls.HlsDataSourceFactory;
import com.google.android.exoplayer2.source.hls.playlist.HlsMasterPlaylist.HlsUrl;
import com.google.android.exoplayer2.source.hls.playlist.HlsMediaPlaylist.Segment;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.Loader;
import com.google.android.exoplayer2.upstream.Loader.LoadErrorAction;
import com.google.android.exoplayer2.upstream.ParsingLoadable;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.UriUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

/** Default implementation for {@link HlsPlaylistTracker}. */
public final class DefaultHlsPlaylistTracker
    implements HlsPlaylistTracker, Loader.Callback<ParsingLoadable<HlsPlaylist>> {

  /** Factory for {@link DefaultHlsPlaylistTracker} instances. */
  public static final Factory FACTORY = DefaultHlsPlaylistTracker::new;

  /**
   * Coefficient applied on the target duration of a playlist to determine the amount of time after
   * which an unchanging playlist is considered stuck.
   */
  private static final double PLAYLIST_STUCK_TARGET_DURATION_COEFFICIENT = 3.5;

  private final HlsDataSourceFactory dataSourceFactory;
  private final HlsPlaylistParserFactory playlistParserFactory;
  private final LoadErrorHandlingPolicy loadErrorHandlingPolicy;
  private final IdentityHashMap<HlsUrl, MediaPlaylistBundle> playlistBundles;
  private final List<PlaylistEventListener> listeners;

  private @Nullable ParsingLoadable.Parser<HlsPlaylist> mediaPlaylistParser;
  private @Nullable EventDispatcher eventDispatcher;
  private @Nullable Loader initialPlaylistLoader;
  private @Nullable Handler playlistRefreshHandler;
  private @Nullable PrimaryPlaylistListener primaryPlaylistListener;
  private @Nullable HlsMasterPlaylist masterPlaylist;
  private @Nullable HlsUrl primaryHlsUrl;
  private @Nullable HlsMediaPlaylist primaryUrlSnapshot;
  private boolean isLive;
  private long initialStartTimeUs;

  /**
   * @param dataSourceFactory A factory for {@link DataSource} instances.
   * @param loadErrorHandlingPolicy The {@link LoadErrorHandlingPolicy}.
   * @param playlistParser A {@link ParsingLoadable.Parser} for HLS playlists.
   * @deprecated Use {@link #DefaultHlsPlaylistTracker(HlsDataSourceFactory,
   *     LoadErrorHandlingPolicy, HlsPlaylistParserFactory)} instead. Using this constructor
   *     prevents support for attributes that are carried over from the master playlist to the media
   *     playlists.
   */
  @Deprecated
  public DefaultHlsPlaylistTracker(
      HlsDataSourceFactory dataSourceFactory,
      LoadErrorHandlingPolicy loadErrorHandlingPolicy,
      ParsingLoadable.Parser<HlsPlaylist> playlistParser) {
    this(dataSourceFactory, loadErrorHandlingPolicy, createFixedFactory(playlistParser));
  }

  /**
   * @param dataSourceFactory A factory for {@link DataSource} instances.
   * @param loadErrorHandlingPolicy The {@link LoadErrorHandlingPolicy}.
   * @param playlistParserFactory An {@link HlsPlaylistParserFactory}.
   */
  public DefaultHlsPlaylistTracker(
      HlsDataSourceFactory dataSourceFactory,
      LoadErrorHandlingPolicy loadErrorHandlingPolicy,
      HlsPlaylistParserFactory playlistParserFactory) {
    this.dataSourceFactory = dataSourceFactory;
    this.playlistParserFactory = playlistParserFactory;
    this.loadErrorHandlingPolicy = loadErrorHandlingPolicy;
    listeners = new ArrayList<>();
    playlistBundles = new IdentityHashMap<>();
    initialStartTimeUs = C.TIME_UNSET;
  }

  // HlsPlaylistTracker implementation.

  @Override
  public void start(
      Uri initialPlaylistUri,
      EventDispatcher eventDispatcher,
      PrimaryPlaylistListener primaryPlaylistListener) {
    this.playlistRefreshHandler = new Handler();
    this.eventDispatcher = eventDispatcher;
    this.primaryPlaylistListener = primaryPlaylistListener;
    ParsingLoadable<HlsPlaylist> masterPlaylistLoadable =
        new ParsingLoadable<>(
            dataSourceFactory.createDataSource(C.DATA_TYPE_MANIFEST),
            initialPlaylistUri,
            C.DATA_TYPE_MANIFEST,
            playlistParserFactory.createPlaylistParser());
    Assertions.checkState(initialPlaylistLoader == null);
    initialPlaylistLoader = new Loader("DefaultHlsPlaylistTracker:MasterPlaylist");
    long elapsedRealtime =
        initialPlaylistLoader.startLoading(
            masterPlaylistLoadable,
            this,
            loadErrorHandlingPolicy.getMinimumLoadableRetryCount(masterPlaylistLoadable.type));
    eventDispatcher.loadStarted(
        masterPlaylistLoadable.dataSpec,
        masterPlaylistLoadable.type,
        elapsedRealtime);
  }

  @Override
  public void stop() {
    primaryHlsUrl = null;
    primaryUrlSnapshot = null;
    masterPlaylist = null;
    initialStartTimeUs = C.TIME_UNSET;
    initialPlaylistLoader.release();
    initialPlaylistLoader = null;
    for (MediaPlaylistBundle bundle : playlistBundles.values()) {
      bundle.release();
    }
    playlistRefreshHandler.removeCallbacksAndMessages(null);
    playlistRefreshHandler = null;
    playlistBundles.clear();
  }

  @Override
  public void addListener(PlaylistEventListener listener) {
    listeners.add(listener);
  }

  @Override
  public void removeListener(PlaylistEventListener listener) {
    listeners.remove(listener);
  }

  @Override
  public @Nullable HlsMasterPlaylist getMasterPlaylist() {
    return masterPlaylist;
  }

  @Override
  public HlsMediaPlaylist getPlaylistSnapshot(HlsUrl url, boolean isForPlayback) {
    HlsMediaPlaylist snapshot = playlistBundles.get(url).getPlaylistSnapshot();
    if (snapshot != null && isForPlayback) {
      maybeSetPrimaryUrl(url);
    }
    return snapshot;
  }

  @Override
  public long getInitialStartTimeUs() {
    return initialStartTimeUs;
  }

  @Override
  public boolean isSnapshotValid(HlsUrl url) {
    return playlistBundles.get(url).isSnapshotValid();
  }

  @Override
  public void maybeThrowPrimaryPlaylistRefreshError() throws IOException {
    if (initialPlaylistLoader != null) {
      initialPlaylistLoader.maybeThrowError();
    }
    if (primaryHlsUrl != null) {
      maybeThrowPlaylistRefreshError(primaryHlsUrl);
    }
  }

  @Override
  public void maybeThrowPlaylistRefreshError(HlsUrl url) throws IOException {
    playlistBundles.get(url).maybeThrowPlaylistRefreshError();
  }

  @Override
  public void refreshPlaylist(HlsUrl url) {
    playlistBundles.get(url).loadPlaylist();
  }

  @Override
  public boolean isLive() {
    return isLive;
  }

  // Loader.Callback implementation.

  @Override
  public void onLoadCompleted(
      ParsingLoadable<HlsPlaylist> loadable, long elapsedRealtimeMs, long loadDurationMs) {
    HlsPlaylist result = loadable.getResult();
    HlsMasterPlaylist masterPlaylist;
    boolean isMediaPlaylist = result instanceof HlsMediaPlaylist;
    if (isMediaPlaylist) {
      masterPlaylist = HlsMasterPlaylist.createSingleVariantMasterPlaylist(result.baseUri);
    } else /* result instanceof HlsMasterPlaylist */ {
      masterPlaylist = (HlsMasterPlaylist) result;
    }
    this.masterPlaylist = masterPlaylist;
    mediaPlaylistParser = playlistParserFactory.createPlaylistParser(masterPlaylist);
    primaryHlsUrl = masterPlaylist.variants.get(0);
    ArrayList<HlsUrl> urls = new ArrayList<>();
    urls.addAll(masterPlaylist.variants);
    urls.addAll(masterPlaylist.audios);
    urls.addAll(masterPlaylist.subtitles);
    createBundles(urls);
    MediaPlaylistBundle primaryBundle = playlistBundles.get(primaryHlsUrl);
    if (isMediaPlaylist) {
      // We don't need to load the playlist again. We can use the same result.
      primaryBundle.processLoadedPlaylist((HlsMediaPlaylist) result, loadDurationMs);
    } else {
      primaryBundle.loadPlaylist();
    }
    eventDispatcher.loadCompleted(
        loadable.dataSpec,
        loadable.getUri(),
        loadable.getResponseHeaders(),
        C.DATA_TYPE_MANIFEST,
        elapsedRealtimeMs,
        loadDurationMs,
        loadable.bytesLoaded());
  }

  @Override
  public void onLoadCanceled(
      ParsingLoadable<HlsPlaylist> loadable,
      long elapsedRealtimeMs,
      long loadDurationMs,
      boolean released) {
    eventDispatcher.loadCanceled(
        loadable.dataSpec,
        loadable.getUri(),
        loadable.getResponseHeaders(),
        C.DATA_TYPE_MANIFEST,
        elapsedRealtimeMs,
        loadDurationMs,
        loadable.bytesLoaded());
  }

  @Override
  public LoadErrorAction onLoadError(
      ParsingLoadable<HlsPlaylist> loadable,
      long elapsedRealtimeMs,
      long loadDurationMs,
      IOException error,
      int errorCount) {
    long retryDelayMs =
        loadErrorHandlingPolicy.getRetryDelayMsFor(
            loadable.type, loadDurationMs, error, errorCount);
    boolean isFatal = retryDelayMs == C.TIME_UNSET;
    eventDispatcher.loadError(
        loadable.dataSpec,
        loadable.getUri(),
        loadable.getResponseHeaders(),
        C.DATA_TYPE_MANIFEST,
        elapsedRealtimeMs,
        loadDurationMs,
        loadable.bytesLoaded(),
        error,
        isFatal);
    return isFatal
        ? Loader.DONT_RETRY_FATAL
        : Loader.createRetryAction(/* resetErrorCount= */ false, retryDelayMs);
  }

  // Internal methods.

  private boolean maybeSelectNewPrimaryUrl() {
    List<HlsUrl> variants = masterPlaylist.variants;
    int variantsSize = variants.size();
    long currentTimeMs = SystemClock.elapsedRealtime();
    for (int i = 0; i < variantsSize; i++) {
      MediaPlaylistBundle bundle = playlistBundles.get(variants.get(i));
      if (currentTimeMs > bundle.blacklistUntilMs) {
        primaryHlsUrl = bundle.playlistUrl;
        bundle.loadPlaylist();
        return true;
      }
    }
    return false;
  }

  private void maybeSetPrimaryUrl(HlsUrl url) {
    if (url == primaryHlsUrl
        || !masterPlaylist.variants.contains(url)
        || (primaryUrlSnapshot != null && primaryUrlSnapshot.hasEndTag)) {
      // Ignore if the primary url is unchanged, if the url is not a variant url, or if the last
      // primary snapshot contains an end tag.
      return;
    }
    primaryHlsUrl = url;
    playlistBundles.get(primaryHlsUrl).loadPlaylist();
  }

  private void createBundles(List<HlsUrl> urls) {
    int listSize = urls.size();
    for (int i = 0; i < listSize; i++) {
      HlsUrl url = urls.get(i);
      MediaPlaylistBundle bundle = new MediaPlaylistBundle(url);
      playlistBundles.put(url, bundle);
    }
  }

  /**
   * Called by the bundles when a snapshot changes.
   *
   * @param url The url of the playlist.
   * @param newSnapshot The new snapshot.
   */
  private void onPlaylistUpdated(HlsUrl url, HlsMediaPlaylist newSnapshot) {
    if (url == primaryHlsUrl) {
      if (primaryUrlSnapshot == null) {
        // This is the first primary url snapshot.
        isLive = !newSnapshot.hasEndTag;
        initialStartTimeUs = newSnapshot.startTimeUs;
      }
      primaryUrlSnapshot = newSnapshot;
      primaryPlaylistListener.onPrimaryPlaylistRefreshed(newSnapshot);
    }
    int listenersSize = listeners.size();
    for (int i = 0; i < listenersSize; i++) {
      listeners.get(i).onPlaylistChanged();
    }
  }

  private boolean notifyPlaylistError(HlsUrl playlistUrl, long blacklistDurationMs) {
    int listenersSize = listeners.size();
    boolean anyBlacklistingFailed = false;
    for (int i = 0; i < listenersSize; i++) {
      anyBlacklistingFailed |= !listeners.get(i).onPlaylistError(playlistUrl, blacklistDurationMs);
    }
    return anyBlacklistingFailed;
  }

  private HlsMediaPlaylist getLatestPlaylistSnapshot(
      HlsMediaPlaylist oldPlaylist, HlsMediaPlaylist loadedPlaylist) {
    if (!loadedPlaylist.isNewerThan(oldPlaylist)) {
      if (loadedPlaylist.hasEndTag) {
        // If the loaded playlist has an end tag but is not newer than the old playlist then we have
        // an inconsistent state. This is typically caused by the server incorrectly resetting the
        // media sequence when appending the end tag. We resolve this case as best we can by
        // returning the old playlist with the end tag appended.
        return oldPlaylist.copyWithEndTag();
      } else {
        return oldPlaylist;
      }
    }
    long startTimeUs = getLoadedPlaylistStartTimeUs(oldPlaylist, loadedPlaylist);
    int discontinuitySequence = getLoadedPlaylistDiscontinuitySequence(oldPlaylist, loadedPlaylist);
    return loadedPlaylist.copyWith(startTimeUs, discontinuitySequence);
  }

  private long getLoadedPlaylistStartTimeUs(
      HlsMediaPlaylist oldPlaylist, HlsMediaPlaylist loadedPlaylist) {
    if (loadedPlaylist.hasProgramDateTime) {
      return loadedPlaylist.startTimeUs;
    }
    long primarySnapshotStartTimeUs =
        primaryUrlSnapshot != null ? primaryUrlSnapshot.startTimeUs : 0;
    if (oldPlaylist == null) {
      return primarySnapshotStartTimeUs;
    }
    int oldPlaylistSize = oldPlaylist.segments.size();
    Segment firstOldOverlappingSegment = getFirstOldOverlappingSegment(oldPlaylist, loadedPlaylist);
    if (firstOldOverlappingSegment != null) {
      return oldPlaylist.startTimeUs + firstOldOverlappingSegment.relativeStartTimeUs;
    } else if (oldPlaylistSize == loadedPlaylist.mediaSequence - oldPlaylist.mediaSequence) {
      return oldPlaylist.getEndTimeUs();
    } else {
      // No segments overlap, we assume the new playlist start coincides with the primary playlist.
      return primarySnapshotStartTimeUs;
    }
  }

  private int getLoadedPlaylistDiscontinuitySequence(
      HlsMediaPlaylist oldPlaylist, HlsMediaPlaylist loadedPlaylist) {
    if (loadedPlaylist.hasDiscontinuitySequence) {
      return loadedPlaylist.discontinuitySequence;
    }
    // TODO: Improve cross-playlist discontinuity adjustment.
    int primaryUrlDiscontinuitySequence =
        primaryUrlSnapshot != null ? primaryUrlSnapshot.discontinuitySequence : 0;
    if (oldPlaylist == null) {
      return primaryUrlDiscontinuitySequence;
    }
    Segment firstOldOverlappingSegment = getFirstOldOverlappingSegment(oldPlaylist, loadedPlaylist);
    if (firstOldOverlappingSegment != null) {
      return oldPlaylist.discontinuitySequence
          + firstOldOverlappingSegment.relativeDiscontinuitySequence
          - loadedPlaylist.segments.get(0).relativeDiscontinuitySequence;
    }
    return primaryUrlDiscontinuitySequence;
  }

  private static Segment getFirstOldOverlappingSegment(
      HlsMediaPlaylist oldPlaylist, HlsMediaPlaylist loadedPlaylist) {
    int mediaSequenceOffset = (int) (loadedPlaylist.mediaSequence - oldPlaylist.mediaSequence);
    List<Segment> oldSegments = oldPlaylist.segments;
    return mediaSequenceOffset < oldSegments.size() ? oldSegments.get(mediaSequenceOffset) : null;
  }

  /** Holds all information related to a specific Media Playlist. */
  private final class MediaPlaylistBundle
      implements Loader.Callback<ParsingLoadable<HlsPlaylist>>, Runnable {

    private final HlsUrl playlistUrl;
    private final Loader mediaPlaylistLoader;
    private final ParsingLoadable<HlsPlaylist> mediaPlaylistLoadable;

    private HlsMediaPlaylist playlistSnapshot;
    private long lastSnapshotLoadMs;
    private long lastSnapshotChangeMs;
    private long earliestNextLoadTimeMs;
    private long blacklistUntilMs;
    private boolean loadPending;
    private IOException playlistError;

    public MediaPlaylistBundle(HlsUrl playlistUrl) {
      this.playlistUrl = playlistUrl;
      mediaPlaylistLoader = new Loader("DefaultHlsPlaylistTracker:MediaPlaylist");
      mediaPlaylistLoadable =
          new ParsingLoadable<>(
              dataSourceFactory.createDataSource(C.DATA_TYPE_MANIFEST),
              UriUtil.resolveToUri(masterPlaylist.baseUri, playlistUrl.url),
              C.DATA_TYPE_MANIFEST,
              mediaPlaylistParser);
    }

    public HlsMediaPlaylist getPlaylistSnapshot() {
      return playlistSnapshot;
    }

    public boolean isSnapshotValid() {
      if (playlistSnapshot == null) {
        return false;
      }
      long currentTimeMs = SystemClock.elapsedRealtime();
      long snapshotValidityDurationMs = Math.max(30000, C.usToMs(playlistSnapshot.durationUs));
      return playlistSnapshot.hasEndTag
          || playlistSnapshot.playlistType == HlsMediaPlaylist.PLAYLIST_TYPE_EVENT
          || playlistSnapshot.playlistType == HlsMediaPlaylist.PLAYLIST_TYPE_VOD
          || lastSnapshotLoadMs + snapshotValidityDurationMs > currentTimeMs;
    }

    public void release() {
      mediaPlaylistLoader.release();
    }

    public void loadPlaylist() {
      blacklistUntilMs = 0;
      if (loadPending || mediaPlaylistLoader.isLoading()) {
        // Load already pending or in progress. Do nothing.
        return;
      }
      long currentTimeMs = SystemClock.elapsedRealtime();
      if (currentTimeMs < earliestNextLoadTimeMs) {
        loadPending = true;
        playlistRefreshHandler.postDelayed(this, earliestNextLoadTimeMs - currentTimeMs);
      } else {
        loadPlaylistImmediately();
      }
    }

    public void maybeThrowPlaylistRefreshError() throws IOException {
      mediaPlaylistLoader.maybeThrowError();
      if (playlistError != null) {
        throw playlistError;
      }
    }

    // Loader.Callback implementation.

    @Override
    public void onLoadCompleted(
        ParsingLoadable<HlsPlaylist> loadable, long elapsedRealtimeMs, long loadDurationMs) {
      HlsPlaylist result = loadable.getResult();
      if (result instanceof HlsMediaPlaylist) {
        processLoadedPlaylist((HlsMediaPlaylist) result, loadDurationMs);
        eventDispatcher.loadCompleted(
            loadable.dataSpec,
            loadable.getUri(),
            loadable.getResponseHeaders(),
            C.DATA_TYPE_MANIFEST,
            elapsedRealtimeMs,
            loadDurationMs,
            loadable.bytesLoaded());
      } else {
        playlistError = new ParserException("Loaded playlist has unexpected type.");
      }
    }

    @Override
    public void onLoadCanceled(
        ParsingLoadable<HlsPlaylist> loadable,
        long elapsedRealtimeMs,
        long loadDurationMs,
        boolean released) {
      eventDispatcher.loadCanceled(
          loadable.dataSpec,
          loadable.getUri(),
          loadable.getResponseHeaders(),
          C.DATA_TYPE_MANIFEST,
          elapsedRealtimeMs,
          loadDurationMs,
          loadable.bytesLoaded());
    }

    @Override
    public LoadErrorAction onLoadError(
        ParsingLoadable<HlsPlaylist> loadable,
        long elapsedRealtimeMs,
        long loadDurationMs,
        IOException error,
        int errorCount) {
      LoadErrorAction loadErrorAction;

      long blacklistDurationMs =
          loadErrorHandlingPolicy.getBlacklistDurationMsFor(
              loadable.type, loadDurationMs, error, errorCount);
      boolean shouldBlacklist = blacklistDurationMs != C.TIME_UNSET;

      boolean blacklistingFailed =
          notifyPlaylistError(playlistUrl, blacklistDurationMs) || !shouldBlacklist;
      if (shouldBlacklist) {
        blacklistingFailed |= blacklistPlaylist(blacklistDurationMs);
      }

      if (blacklistingFailed) {
        long retryDelay =
            loadErrorHandlingPolicy.getRetryDelayMsFor(
                loadable.type, loadDurationMs, error, errorCount);
        loadErrorAction =
            retryDelay != C.TIME_UNSET
                ? Loader.createRetryAction(false, retryDelay)
                : Loader.DONT_RETRY_FATAL;
      } else {
        loadErrorAction = Loader.DONT_RETRY;
      }

      eventDispatcher.loadError(
          loadable.dataSpec,
          loadable.getUri(),
          loadable.getResponseHeaders(),
          C.DATA_TYPE_MANIFEST,
          elapsedRealtimeMs,
          loadDurationMs,
          loadable.bytesLoaded(),
          error,
          /* wasCanceled= */ !loadErrorAction.isRetry());

      return loadErrorAction;
    }

    // Runnable implementation.

    @Override
    public void run() {
      loadPending = false;
      loadPlaylistImmediately();
    }

    // Internal methods.

    private void loadPlaylistImmediately() {
      long elapsedRealtime =
          mediaPlaylistLoader.startLoading(
              mediaPlaylistLoadable,
              this,
              loadErrorHandlingPolicy.getMinimumLoadableRetryCount(mediaPlaylistLoadable.type));
      eventDispatcher.loadStarted(
          mediaPlaylistLoadable.dataSpec,
          mediaPlaylistLoadable.type,
          elapsedRealtime);
    }

    private void processLoadedPlaylist(HlsMediaPlaylist loadedPlaylist, long loadDurationMs) {
      HlsMediaPlaylist oldPlaylist = playlistSnapshot;
      long currentTimeMs = SystemClock.elapsedRealtime();
      lastSnapshotLoadMs = currentTimeMs;
      playlistSnapshot = getLatestPlaylistSnapshot(oldPlaylist, loadedPlaylist);
      if (playlistSnapshot != oldPlaylist) {
        playlistError = null;
        lastSnapshotChangeMs = currentTimeMs;
        onPlaylistUpdated(playlistUrl, playlistSnapshot);
      } else if (!playlistSnapshot.hasEndTag) {
        if (loadedPlaylist.mediaSequence + loadedPlaylist.segments.size()
            < playlistSnapshot.mediaSequence) {
          // TODO: Allow customization of playlist resets handling.
          // The media sequence jumped backwards. The server has probably reset. We do not try
          // blacklisting in this case.
          playlistError = new PlaylistResetException(playlistUrl.url);
          notifyPlaylistError(playlistUrl, C.TIME_UNSET);
        } else if (currentTimeMs - lastSnapshotChangeMs
            > C.usToMs(playlistSnapshot.targetDurationUs)
                * PLAYLIST_STUCK_TARGET_DURATION_COEFFICIENT) {
          // TODO: Allow customization of stuck playlists handling.
          playlistError = new PlaylistStuckException(playlistUrl.url);
          long blacklistDurationMs =
              loadErrorHandlingPolicy.getBlacklistDurationMsFor(
                  C.DATA_TYPE_MANIFEST, loadDurationMs, playlistError, /* errorCount= */ 1);
          notifyPlaylistError(playlistUrl, blacklistDurationMs);
          if (blacklistDurationMs != C.TIME_UNSET) {
            blacklistPlaylist(blacklistDurationMs);
          }
        }
      }
      // Do not allow the playlist to load again within the target duration if we obtained a new
      // snapshot, or half the target duration otherwise.
      earliestNextLoadTimeMs =
          currentTimeMs
              + C.usToMs(
                  playlistSnapshot != oldPlaylist
                      ? playlistSnapshot.targetDurationUs
                      : (playlistSnapshot.targetDurationUs / 2));
      // Schedule a load if this is the primary playlist and it doesn't have an end tag. Else the
      // next load will be scheduled when refreshPlaylist is called, or when this playlist becomes
      // the primary.
      if (playlistUrl == primaryHlsUrl && !playlistSnapshot.hasEndTag) {
        loadPlaylist();
      }
    }

    /**
     * Blacklists the playlist.
     *
     * @param blacklistDurationMs The number of milliseconds for which the playlist should be
     *     blacklisted.
     * @return Whether the playlist is the primary, despite being blacklisted.
     */
    private boolean blacklistPlaylist(long blacklistDurationMs) {
      blacklistUntilMs = SystemClock.elapsedRealtime() + blacklistDurationMs;
      return primaryHlsUrl == playlistUrl && !maybeSelectNewPrimaryUrl();
    }
  }

  /**
   * Creates a factory which always returns the given playlist parser.
   *
   * @param playlistParser The parser to return.
   * @return A factory which always returns the given playlist parser.
   */
  private static HlsPlaylistParserFactory createFixedFactory(
      ParsingLoadable.Parser<HlsPlaylist> playlistParser) {
    return new HlsPlaylistParserFactory() {
      @Override
      public ParsingLoadable.Parser<HlsPlaylist> createPlaylistParser() {
        return playlistParser;
      }

      @Override
      public ParsingLoadable.Parser<HlsPlaylist> createPlaylistParser(
          HlsMasterPlaylist masterPlaylist) {
        return playlistParser;
      }
    };
  }
}
