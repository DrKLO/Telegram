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
import org.telegram.messenger.exoplayer2.C;
import org.telegram.messenger.exoplayer2.ParserException;
import org.telegram.messenger.exoplayer2.source.AdaptiveMediaSourceEventListener.EventDispatcher;
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
   * Called when the playlist changes.
   */
  public interface PlaylistRefreshCallback {

    /**
     * Called when the target playlist changes.
     */
    void onPlaylistChanged();

    /**
     * Called if an error is encountered while loading the target playlist.
     *
     * @param url The loaded url that caused the error.
     * @param error The loading error.
     */
    void onPlaylistLoadError(HlsUrl url, IOException error);

  }

  /**
   * Determines the minimum amount of time by which a media playlist segment's start time has to
   * drift from the actual start time of the chunk it refers to for it to be adjusted.
   */
  private static final long TIMESTAMP_ADJUSTMENT_THRESHOLD_US = 500000;

  /**
   * Period for refreshing playlists.
   */
  private static final long PLAYLIST_REFRESH_PERIOD_MS = 5000;

  private final Uri initialPlaylistUri;
  private final DataSource.Factory dataSourceFactory;
  private final HlsPlaylistParser playlistParser;
  private final int minRetryCount;
  private final IdentityHashMap<HlsUrl, MediaPlaylistBundle> playlistBundles;
  private final Handler playlistRefreshHandler;
  private final PrimaryPlaylistListener primaryPlaylistListener;
  private final Loader initialPlaylistLoader;
  private final EventDispatcher eventDispatcher;

  private HlsMasterPlaylist masterPlaylist;
  private HlsUrl primaryHlsUrl;
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
  public HlsPlaylistTracker(Uri initialPlaylistUri, DataSource.Factory dataSourceFactory,
      EventDispatcher eventDispatcher, int minRetryCount,
      PrimaryPlaylistListener primaryPlaylistListener) {
    this.initialPlaylistUri = initialPlaylistUri;
    this.dataSourceFactory = dataSourceFactory;
    this.eventDispatcher = eventDispatcher;
    this.minRetryCount = minRetryCount;
    this.primaryPlaylistListener = primaryPlaylistListener;
    initialPlaylistLoader = new Loader("HlsPlaylistTracker:MasterPlaylist");
    playlistParser = new HlsPlaylistParser();
    playlistBundles = new IdentityHashMap<>();
    playlistRefreshHandler = new Handler();
  }

  /**
   * Starts tracking all the playlists related to the provided Uri.
   */
  public void start() {
    ParsingLoadable<HlsPlaylist> masterPlaylistLoadable = new ParsingLoadable<>(
        dataSourceFactory.createDataSource(), initialPlaylistUri, C.DATA_TYPE_MANIFEST,
        playlistParser);
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
    return playlistBundles.get(url).latestPlaylistSnapshot;
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
   * If the tracker is having trouble refreshing the primary playlist, this method throws the
   * underlying error. Otherwise, does nothing.
   *
   * @throws IOException The underlying error.
   */
  public void maybeThrowPrimaryPlaylistRefreshError() throws IOException {
    initialPlaylistLoader.maybeThrowError();
    if (primaryHlsUrl != null) {
      playlistBundles.get(primaryHlsUrl).mediaPlaylistLoader.maybeThrowError();
    }
  }

  /**
   * Triggers a playlist refresh and sets the callback to be called once the playlist referenced by
   * the provided {@link HlsUrl} changes.
   *
   * @param key The {@link HlsUrl} of the playlist to be refreshed.
   * @param callback The callback.
   */
  public void refreshPlaylist(HlsUrl key, PlaylistRefreshCallback callback) {
    MediaPlaylistBundle bundle = playlistBundles.get(key);
    bundle.setCallback(callback);
    bundle.loadPlaylist();
  }

  /**
   * Returns whether this is live content.
   *
   * @return True if the content is live. False otherwise.
   */
  public boolean isLive() {
    return isLive;
  }

  /**
   * Called when a chunk from a media playlist is loaded.
   *
   * @param hlsUrl The url of the playlist from which the chunk was obtained.
   * @param chunkMediaSequence The media sequence number of the loaded chunk.
   * @param adjustedStartTimeUs The adjusted start time of the loaded chunk.
   */
  public void onChunkLoaded(HlsUrl hlsUrl, int chunkMediaSequence, long adjustedStartTimeUs) {
    playlistBundles.get(hlsUrl).adjustTimestampsOfPlaylist(chunkMediaSequence, adjustedStartTimeUs);
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

  private void createBundles(List<HlsUrl> urls) {
    int listSize = urls.size();
    for (int i = 0; i < listSize; i++) {
      HlsUrl url = urls.get(i);
      MediaPlaylistBundle bundle = new MediaPlaylistBundle(url);
      playlistBundles.put(urls.get(i), bundle);
    }
  }

  /**
   * Called by the bundles when a snapshot changes.
   *
   * @param url The url of the playlist.
   * @param newSnapshot The new snapshot.
   * @param isFirstSnapshot Whether this is the first snapshot for the given playlist.
   * @return True if a refresh should be scheduled.
   */
  private boolean onPlaylistUpdated(HlsUrl url, HlsMediaPlaylist newSnapshot,
      boolean isFirstSnapshot) {
    if (url == primaryHlsUrl) {
      if (isFirstSnapshot) {
        isLive = !newSnapshot.hasEndTag;
      }
      primaryPlaylistListener.onPrimaryPlaylistRefreshed(newSnapshot);
      // If the primary playlist is not the final one, we should schedule a refresh.
      return !newSnapshot.hasEndTag;
    }
    return false;
  }

  /**
   * TODO: Track discontinuities for media playlists that don't include the discontinuity number.
   */
  private HlsMediaPlaylist adjustPlaylistTimestamps(HlsMediaPlaylist oldPlaylist,
      HlsMediaPlaylist newPlaylist) {
    HlsMediaPlaylist primaryPlaylistSnapshot =
        playlistBundles.get(primaryHlsUrl).latestPlaylistSnapshot;
    if (oldPlaylist == null) {
      if (primaryPlaylistSnapshot == null) {
        // Playback has just started so no adjustment is needed.
        return newPlaylist;
      } else {
        return newPlaylist.copyWithStartTimeUs(primaryPlaylistSnapshot.getStartTimeUs());
      }
    }
    List<HlsMediaPlaylist.Segment> oldSegments = oldPlaylist.segments;
    int oldPlaylistSize = oldSegments.size();
    int newPlaylistSize = newPlaylist.segments.size();
    int mediaSequenceOffset = newPlaylist.mediaSequence - oldPlaylist.mediaSequence;
    if (newPlaylistSize == oldPlaylistSize && mediaSequenceOffset == 0
        && oldPlaylist.hasEndTag == newPlaylist.hasEndTag) {
      // Playlist has not changed.
      return oldPlaylist;
    }
    if (mediaSequenceOffset < 0) {
      // Playlist has changed but media sequence has regressed.
      return oldPlaylist;
    }
    if (mediaSequenceOffset <= oldPlaylistSize) {
      // We can extrapolate the start time of new segments from the segments of the old snapshot.
      ArrayList<HlsMediaPlaylist.Segment> newSegments = new ArrayList<>(newPlaylistSize);
      for (int i = mediaSequenceOffset; i < oldPlaylistSize; i++) {
        newSegments.add(oldSegments.get(i));
      }
      HlsMediaPlaylist.Segment lastSegment = oldSegments.get(oldPlaylistSize - 1);
      for (int i = newSegments.size(); i < newPlaylistSize; i++) {
        lastSegment = newPlaylist.segments.get(i).copyWithStartTimeUs(
            lastSegment.startTimeUs + lastSegment.durationUs);
        newSegments.add(lastSegment);
      }
      return newPlaylist.copyWithSegments(newSegments);
    } else {
      // No segments overlap, we assume the new playlist start coincides with the primary playlist.
      return newPlaylist.copyWithStartTimeUs(primaryPlaylistSnapshot.getStartTimeUs());
    }
  }

  /**
   * Holds all information related to a specific Media Playlist.
   */
  private final class MediaPlaylistBundle implements Loader.Callback<ParsingLoadable<HlsPlaylist>>,
      Runnable {

    private final HlsUrl playlistUrl;
    private final Loader mediaPlaylistLoader;
    private final ParsingLoadable<HlsPlaylist> mediaPlaylistLoadable;

    private PlaylistRefreshCallback callback;
    private HlsMediaPlaylist latestPlaylistSnapshot;

    public MediaPlaylistBundle(HlsUrl playlistUrl) {
      this(playlistUrl, null);
    }

    public MediaPlaylistBundle(HlsUrl playlistUrl, HlsMediaPlaylist initialSnapshot) {
      this.playlistUrl = playlistUrl;
      latestPlaylistSnapshot = initialSnapshot;
      mediaPlaylistLoader = new Loader("HlsPlaylistTracker:MediaPlaylist");
      mediaPlaylistLoadable = new ParsingLoadable<>(dataSourceFactory.createDataSource(),
          UriUtil.resolveToUri(masterPlaylist.baseUri, playlistUrl.url), C.DATA_TYPE_MANIFEST,
          playlistParser);
    }

    public void release() {
      mediaPlaylistLoader.release();
    }

    public void loadPlaylist() {
      if (!mediaPlaylistLoader.isLoading()) {
        mediaPlaylistLoader.startLoading(mediaPlaylistLoadable, this, minRetryCount);
      }
    }

    public void setCallback(PlaylistRefreshCallback callback) {
      this.callback = callback;
    }

    public void adjustTimestampsOfPlaylist(int chunkMediaSequence, long adjustedStartTimeUs) {
      ArrayList<Segment> segments = new ArrayList<>(latestPlaylistSnapshot.segments);
      int indexOfChunk = chunkMediaSequence - latestPlaylistSnapshot.mediaSequence;
      if (indexOfChunk < 0) {
        return;
      }
      Segment actualSegment = segments.get(indexOfChunk);
      long timestampDriftUs = Math.abs(actualSegment.startTimeUs - adjustedStartTimeUs);
      if (timestampDriftUs < TIMESTAMP_ADJUSTMENT_THRESHOLD_US) {
        return;
      }
      segments.set(indexOfChunk, actualSegment.copyWithStartTimeUs(adjustedStartTimeUs));
      // Propagate the adjustment backwards.
      for (int i = indexOfChunk - 1; i >= 0; i--) {
        Segment segment = segments.get(i);
        segments.set(i,
            segment.copyWithStartTimeUs(segments.get(i + 1).startTimeUs - segment.durationUs));
      }
      // Propagate the adjustment forward.
      int segmentsSize = segments.size();
      for (int i = indexOfChunk + 1; i < segmentsSize; i++) {
        Segment segment = segments.get(i);
        segments.set(i,
            segment.copyWithStartTimeUs(segments.get(i - 1).startTimeUs + segment.durationUs));
      }
      latestPlaylistSnapshot = latestPlaylistSnapshot.copyWithSegments(segments);
    }

    // Loader.Callback implementation.

    @Override
    public void onLoadCompleted(ParsingLoadable<HlsPlaylist> loadable, long elapsedRealtimeMs,
        long loadDurationMs) {
      processLoadedPlaylist((HlsMediaPlaylist) loadable.getResult());
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
      // TODO: Change primary playlist if this is the primary playlist bundle.
      boolean isFatal = error instanceof ParserException;
      eventDispatcher.loadError(loadable.dataSpec, C.DATA_TYPE_MANIFEST, elapsedRealtimeMs,
          loadDurationMs, loadable.bytesLoaded(), error, isFatal);
      if (callback != null) {
        callback.onPlaylistLoadError(playlistUrl, error);
      }
      if (isFatal) {
        return Loader.DONT_RETRY_FATAL;
      } else {
        return primaryHlsUrl == playlistUrl ? Loader.RETRY : Loader.DONT_RETRY;
      }
    }

    // Runnable implementation.

    @Override
    public void run() {
      loadPlaylist();
    }

    // Internal methods.

    private void processLoadedPlaylist(HlsMediaPlaylist loadedMediaPlaylist) {
      HlsMediaPlaylist oldPlaylist = latestPlaylistSnapshot;
      latestPlaylistSnapshot = adjustPlaylistTimestamps(oldPlaylist, loadedMediaPlaylist);
      boolean shouldScheduleRefresh;
      if (oldPlaylist != latestPlaylistSnapshot) {
        if (callback != null) {
          callback.onPlaylistChanged();
          callback = null;
        }
        shouldScheduleRefresh = onPlaylistUpdated(playlistUrl, latestPlaylistSnapshot,
            oldPlaylist == null);
      } else {
        shouldScheduleRefresh = !loadedMediaPlaylist.hasEndTag;
      }
      if (shouldScheduleRefresh) {
        playlistRefreshHandler.postDelayed(this, PLAYLIST_REFRESH_PERIOD_MS);
      }
    }

  }

}
