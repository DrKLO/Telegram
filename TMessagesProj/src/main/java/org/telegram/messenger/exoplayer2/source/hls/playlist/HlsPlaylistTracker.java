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
package org.telegram.messenger.exoplayer2.source.hls.playlist;

import android.net.Uri;
import android.os.Handler;
import android.os.SystemClock;
import org.telegram.messenger.exoplayer2.C;
import org.telegram.messenger.exoplayer2.ParserException;
import org.telegram.messenger.exoplayer2.source.AdaptiveMediaSourceEventListener.EventDispatcher;
import org.telegram.messenger.exoplayer2.source.chunk.ChunkedTrackBlacklistUtil;
import org.telegram.messenger.exoplayer2.source.hls.HlsDataSourceFactory;
import org.telegram.messenger.exoplayer2.source.hls.playlist.HlsMasterPlaylist.HlsUrl;
import org.telegram.messenger.exoplayer2.source.hls.playlist.HlsMediaPlaylist.Segment;
import org.telegram.messenger.exoplayer2.upstream.DataSource;
import org.telegram.messenger.exoplayer2.upstream.Loader;
import org.telegram.messenger.exoplayer2.upstream.ParsingLoadable;
import org.telegram.messenger.exoplayer2.util.UriUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * Tracks playlists linked to a provided playlist url. The provided url might reference an HLS
 * master playlist or a media playlist.
 */
public final class HlsPlaylistTracker implements Loader.Callback<ParsingLoadable<HlsPlaylist>> {

  /**
   * Thrown when a playlist is considered to be stuck due to a server side error.
   */
  public static final class PlaylistStuckException extends IOException {

    /**
     * The url of the stuck playlist.
     */
    public final String url;

    private PlaylistStuckException(String url) {
      this.url = url;
    }

  }

  /**
   * Thrown when the media sequence of a new snapshot indicates the server has reset.
   */
  public static final class PlaylistResetException extends IOException {

    /**
     * The url of the reset playlist.
     */
    public final String url;

    private PlaylistResetException(String url) {
      this.url = url;
    }

  }

  /**
   * Listener for primary playlist changes.
   */
  public interface PrimaryPlaylistListener {

    /**
     * Called when the primary playlist changes.
     *
     * @param mediaPlaylist The primary playlist new snapshot.
     */
    void onPrimaryPlaylistRefreshed(HlsMediaPlaylist mediaPlaylist);

  }

  /**
   * Called on playlist loading events.
   */
  public interface PlaylistEventListener {

    /**
     * Called a playlist changes.
     */
    void onPlaylistChanged();

    /**
     * Called if an error is encountered while loading a playlist.
     *
     * @param url The loaded url that caused the error.
     * @param blacklistDurationMs The number of milliseconds for which the playlist has been
     *     blacklisted.
     */
    void onPlaylistBlacklisted(HlsUrl url, long blacklistDurationMs);

  }

  /**
   * Coefficient applied on the target duration of a playlist to determine the amount of time after
   * which an unchanging playlist is considered stuck.
   */
  private static final double PLAYLIST_STUCK_TARGET_DURATION_COEFFICIENT = 3.5;

  private final Uri initialPlaylistUri;
  private final HlsDataSourceFactory dataSourceFactory;
  private final ParsingLoadable.Parser<HlsPlaylist> playlistParser;
  private final int minRetryCount;
  private final IdentityHashMap<HlsUrl, MediaPlaylistBundle> playlistBundles;
  private final Handler playlistRefreshHandler;
  private final PrimaryPlaylistListener primaryPlaylistListener;
  private final List<PlaylistEventListener> listeners;
  private final Loader initialPlaylistLoader;
  private final EventDispatcher eventDispatcher;

  private HlsMasterPlaylist masterPlaylist;
  private HlsUrl primaryHlsUrl;
  private HlsMediaPlaylist primaryUrlSnapshot;
  private boolean isLive;

  /**
   * @param initialPlaylistUri Uri for the initial playlist of the stream. Can refer a media
   *     playlist or a master playlist.
   * @param dataSourceFactory A factory for {@link DataSource} instances.
   * @param eventDispatcher A dispatcher to notify of events.
   * @param minRetryCount The minimum number of times the load must be retried before blacklisting a
   *     playlist.
   * @param primaryPlaylistListener A callback for the primary playlist change events.
   */
  public HlsPlaylistTracker(Uri initialPlaylistUri, HlsDataSourceFactory dataSourceFactory,
      EventDispatcher eventDispatcher, int minRetryCount,
      PrimaryPlaylistListener primaryPlaylistListener,
      ParsingLoadable.Parser<HlsPlaylist> playlistParser) {
    this.initialPlaylistUri = initialPlaylistUri;
    this.dataSourceFactory = dataSourceFactory;
    this.eventDispatcher = eventDispatcher;
    this.minRetryCount = minRetryCount;
    this.primaryPlaylistListener = primaryPlaylistListener;
    this.playlistParser = playlistParser;
    listeners = new ArrayList<>();
    initialPlaylistLoader = new Loader("HlsPlaylistTracker:MasterPlaylist");
    playlistBundles = new IdentityHashMap<>();
    playlistRefreshHandler = new Handler();
  }

  /**
   * Registers a listener to receive events from the playlist tracker.
   *
   * @param listener The listener.
   */
  public void addListener(PlaylistEventListener listener) {
    listeners.add(listener);
  }

  /**
   * Unregisters a listener.
   *
   * @param listener The listener to unregister.
   */
  public void removeListener(PlaylistEventListener listener) {
    listeners.remove(listener);
  }

  /**
   * Starts tracking all the playlists related to the provided Uri.
   */
  public void start() {
    ParsingLoadable<HlsPlaylist> masterPlaylistLoadable = new ParsingLoadable<>(
        dataSourceFactory.createDataSource(C.DATA_TYPE_MANIFEST), initialPlaylistUri,
        C.DATA_TYPE_MANIFEST, playlistParser);
    initialPlaylistLoader.startLoading(masterPlaylistLoadable, this, minRetryCount);
  }

  /**
   * Returns the master playlist.
   *
   * @return The master playlist. Null if the initial playlist has yet to be loaded.
   */
  public HlsMasterPlaylist getMasterPlaylist() {
    return masterPlaylist;
  }

  /**
   * Returns the most recent snapshot available of the playlist referenced by the provided
   * {@link HlsUrl}.
   *
   * @param url The {@link HlsUrl} corresponding to the requested media playlist.
   * @return The most recent snapshot of the playlist referenced by the provided {@link HlsUrl}. May
   *     be null if no snapshot has been loaded yet.
   */
  public HlsMediaPlaylist getPlaylistSnapshot(HlsUrl url) {
    HlsMediaPlaylist snapshot = playlistBundles.get(url).getPlaylistSnapshot();
    if (snapshot != null) {
      maybeSetPrimaryUrl(url);
    }
    return snapshot;
  }

  /**
   * Returns whether the snapshot of the playlist referenced by the provided {@link HlsUrl} is
   * valid, meaning all the segments referenced by the playlist are expected to be available. If the
   * playlist is not valid then some of the segments may no longer be available.

   * @param url The {@link HlsUrl}.
   * @return Whether the snapshot of the playlist referenced by the provided {@link HlsUrl} is
   *     valid.
   */
  public boolean isSnapshotValid(HlsUrl url) {
    return playlistBundles.get(url).isSnapshotValid();
  }

  /**
   * Releases the playlist tracker.
   */
  public void release() {
    initialPlaylistLoader.release();
    for (MediaPlaylistBundle bundle : playlistBundles.values()) {
      bundle.release();
    }
    playlistRefreshHandler.removeCallbacksAndMessages(null);
    playlistBundles.clear();
  }

  /**
   * If the tracker is having trouble refreshing the master playlist or the primary playlist, this
   * method throws the underlying error. Otherwise, does nothing.
   *
   * @throws IOException The underlying error.
   */
  public void maybeThrowPrimaryPlaylistRefreshError() throws IOException {
    initialPlaylistLoader.maybeThrowError();
    if (primaryHlsUrl != null) {
      maybeThrowPlaylistRefreshError(primaryHlsUrl);
    }
  }

  /**
   * If the playlist is having trouble refreshing the playlist referenced by the given
   * {@link HlsUrl}, this method throws the underlying error.
   *
   * @param url The {@link HlsUrl}.
   * @throws IOException The underyling error.
   */
  public void maybeThrowPlaylistRefreshError(HlsUrl url) throws IOException {
    playlistBundles.get(url).maybeThrowPlaylistRefreshError();
  }

  /**
   * Triggers a playlist refresh and whitelists it.
   *
   * @param url The {@link HlsUrl} of the playlist to be refreshed.
   */
  public void refreshPlaylist(HlsUrl url) {
    playlistBundles.get(url).loadPlaylist();
  }

  /**
   * Returns whether this is live content.
   *
   * @return True if the content is live. False otherwise.
   */
  public boolean isLive() {
    return isLive;
  }

  // Loader.Callback implementation.

  @Override
  public void onLoadCompleted(ParsingLoadable<HlsPlaylist> loadable, long elapsedRealtimeMs,
      long loadDurationMs) {
    HlsPlaylist result = loadable.getResult();
    HlsMasterPlaylist masterPlaylist;
    boolean isMediaPlaylist = result instanceof HlsMediaPlaylist;
    if (isMediaPlaylist) {
      masterPlaylist = HlsMasterPlaylist.createSingleVariantMasterPlaylist(result.baseUri);
    } else /* result instanceof HlsMasterPlaylist */ {
      masterPlaylist = (HlsMasterPlaylist) result;
    }
    this.masterPlaylist = masterPlaylist;
    primaryHlsUrl = masterPlaylist.variants.get(0);
    ArrayList<HlsUrl> urls = new ArrayList<>();
    urls.addAll(masterPlaylist.variants);
    urls.addAll(masterPlaylist.audios);
    urls.addAll(masterPlaylist.subtitles);
    createBundles(urls);
    MediaPlaylistBundle primaryBundle = playlistBundles.get(primaryHlsUrl);
    if (isMediaPlaylist) {
      // We don't need to load the playlist again. We can use the same result.
      primaryBundle.processLoadedPlaylist((HlsMediaPlaylist) result);
    } else {
      primaryBundle.loadPlaylist();
    }
    eventDispatcher.loadCompleted(loadable.dataSpec, C.DATA_TYPE_MANIFEST, elapsedRealtimeMs,
        loadDurationMs, loadable.bytesLoaded());
  }

  @Override
  public void onLoadCanceled(ParsingLoadable<HlsPlaylist> loadable, long elapsedRealtimeMs,
      long loadDurationMs, boolean released) {
    eventDispatcher.loadCanceled(loadable.dataSpec, C.DATA_TYPE_MANIFEST, elapsedRealtimeMs,
        loadDurationMs, loadable.bytesLoaded());
  }

  @Override
  public int onLoadError(ParsingLoadable<HlsPlaylist> loadable, long elapsedRealtimeMs,
      long loadDurationMs, IOException error) {
    boolean isFatal = error instanceof ParserException;
    eventDispatcher.loadError(loadable.dataSpec, C.DATA_TYPE_MANIFEST, elapsedRealtimeMs,
        loadDurationMs, loadable.bytesLoaded(), error, isFatal);
    return isFatal ? Loader.DONT_RETRY_FATAL : Loader.RETRY;
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
      }
      primaryUrlSnapshot = newSnapshot;
      primaryPlaylistListener.onPrimaryPlaylistRefreshed(newSnapshot);
    }
    int listenersSize = listeners.size();
    for (int i = 0; i < listenersSize; i++) {
      listeners.get(i).onPlaylistChanged();
    }
  }

  private void notifyPlaylistBlacklisting(HlsUrl url, long blacklistMs) {
    int listenersSize = listeners.size();
    for (int i = 0; i < listenersSize; i++) {
      listeners.get(i).onPlaylistBlacklisted(url, blacklistMs);
    }
  }

  private HlsMediaPlaylist getLatestPlaylistSnapshot(HlsMediaPlaylist oldPlaylist,
      HlsMediaPlaylist loadedPlaylist) {
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

  private long getLoadedPlaylistStartTimeUs(HlsMediaPlaylist oldPlaylist,
      HlsMediaPlaylist loadedPlaylist) {
    if (loadedPlaylist.hasProgramDateTime) {
      return loadedPlaylist.startTimeUs;
    }
    long primarySnapshotStartTimeUs = primaryUrlSnapshot != null
        ? primaryUrlSnapshot.startTimeUs : 0;
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

  private int getLoadedPlaylistDiscontinuitySequence(HlsMediaPlaylist oldPlaylist,
      HlsMediaPlaylist loadedPlaylist) {
    if (loadedPlaylist.hasDiscontinuitySequence) {
      return loadedPlaylist.discontinuitySequence;
    }
    // TODO: Improve cross-playlist discontinuity adjustment.
    int primaryUrlDiscontinuitySequence = primaryUrlSnapshot != null
        ? primaryUrlSnapshot.discontinuitySequence : 0;
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

  private static Segment getFirstOldOverlappingSegment(HlsMediaPlaylist oldPlaylist,
      HlsMediaPlaylist loadedPlaylist) {
    int mediaSequenceOffset = loadedPlaylist.mediaSequence - oldPlaylist.mediaSequence;
    List<Segment> oldSegments = oldPlaylist.segments;
    return mediaSequenceOffset < oldSegments.size() ? oldSegments.get(mediaSequenceOffset) : null;
  }

  /**
   * Holds all information related to a specific Media Playlist.
   */
  private final class MediaPlaylistBundle implements Loader.Callback<ParsingLoadable<HlsPlaylist>>,
      Runnable {

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
      mediaPlaylistLoader = new Loader("HlsPlaylistTracker:MediaPlaylist");
      mediaPlaylistLoadable = new ParsingLoadable<>(
          dataSourceFactory.createDataSource(C.DATA_TYPE_MANIFEST),
          UriUtil.resolveToUri(masterPlaylist.baseUri, playlistUrl.url), C.DATA_TYPE_MANIFEST,
          playlistParser);
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
    public void onLoadCompleted(ParsingLoadable<HlsPlaylist> loadable, long elapsedRealtimeMs,
        long loadDurationMs) {
      HlsPlaylist result = loadable.getResult();
      if (result instanceof HlsMediaPlaylist) {
        processLoadedPlaylist((HlsMediaPlaylist) result);
        eventDispatcher.loadCompleted(loadable.dataSpec, C.DATA_TYPE_MANIFEST, elapsedRealtimeMs,
            loadDurationMs, loadable.bytesLoaded());
      } else {
        playlistError = new ParserException("Loaded playlist has unexpected type.");
      }
    }

    @Override
    public void onLoadCanceled(ParsingLoadable<HlsPlaylist> loadable, long elapsedRealtimeMs,
        long loadDurationMs, boolean released) {
      eventDispatcher.loadCanceled(loadable.dataSpec, C.DATA_TYPE_MANIFEST, elapsedRealtimeMs,
          loadDurationMs, loadable.bytesLoaded());
    }

    @Override
    public int onLoadError(ParsingLoadable<HlsPlaylist> loadable, long elapsedRealtimeMs,
        long loadDurationMs, IOException error) {
      boolean isFatal = error instanceof ParserException;
      eventDispatcher.loadError(loadable.dataSpec, C.DATA_TYPE_MANIFEST, elapsedRealtimeMs,
          loadDurationMs, loadable.bytesLoaded(), error, isFatal);
      if (isFatal) {
        return Loader.DONT_RETRY_FATAL;
      }
      boolean shouldRetry = true;
      if (ChunkedTrackBlacklistUtil.shouldBlacklist(error)) {
        shouldRetry = blacklistPlaylist();
      }
      return shouldRetry ? Loader.RETRY : Loader.DONT_RETRY;
    }

    // Runnable implementation.

    @Override
    public void run() {
      loadPending = false;
      loadPlaylistImmediately();
    }

    // Internal methods.

    private void loadPlaylistImmediately() {
      mediaPlaylistLoader.startLoading(mediaPlaylistLoadable, this, minRetryCount);
    }

    private void processLoadedPlaylist(HlsMediaPlaylist loadedPlaylist) {
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
          // The media sequence jumped backwards. The server has probably reset.
          playlistError = new PlaylistResetException(playlistUrl.url);
        } else if (currentTimeMs - lastSnapshotChangeMs
            > C.usToMs(playlistSnapshot.targetDurationUs)
                * PLAYLIST_STUCK_TARGET_DURATION_COEFFICIENT) {
          // The playlist seems to be stuck. Blacklist it.
          playlistError = new PlaylistStuckException(playlistUrl.url);
          blacklistPlaylist();
        }
      }
      // Do not allow the playlist to load again within the target duration if we obtained a new
      // snapshot, or half the target duration otherwise.
      earliestNextLoadTimeMs = currentTimeMs + C.usToMs(playlistSnapshot != oldPlaylist
          ? playlistSnapshot.targetDurationUs : (playlistSnapshot.targetDurationUs / 2));
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
     * @return Whether the playlist is the primary, despite being blacklisted.
     */
    private boolean blacklistPlaylist() {
      blacklistUntilMs = SystemClock.elapsedRealtime()
          + ChunkedTrackBlacklistUtil.DEFAULT_TRACK_BLACKLIST_MS;
      notifyPlaylistBlacklisting(playlistUrl, ChunkedTrackBlacklistUtil.DEFAULT_TRACK_BLACKLIST_MS);
      return primaryHlsUrl == playlistUrl && !maybeSelectNewPrimaryUrl();
    }

  }

}
