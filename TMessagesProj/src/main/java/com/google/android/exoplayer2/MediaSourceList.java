/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.google.android.exoplayer2;

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static java.lang.Math.max;
import static java.lang.Math.min;

import android.os.Handler;
import android.util.Pair;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.analytics.AnalyticsCollector;
import com.google.android.exoplayer2.analytics.PlayerId;
import com.google.android.exoplayer2.drm.DrmSession;
import com.google.android.exoplayer2.drm.DrmSessionEventListener;
import com.google.android.exoplayer2.source.LoadEventInfo;
import com.google.android.exoplayer2.source.MaskingMediaPeriod;
import com.google.android.exoplayer2.source.MaskingMediaSource;
import com.google.android.exoplayer2.source.MediaLoadData;
import com.google.android.exoplayer2.source.MediaPeriod;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MediaSourceEventListener;
import com.google.android.exoplayer2.source.ShuffleOrder;
import com.google.android.exoplayer2.source.ShuffleOrder.DefaultShuffleOrder;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.HandlerWrapper;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.checkerframework.checker.nullness.compatqual.NullableType;

/**
 * Concatenates multiple {@link MediaSource}s. The list of {@link MediaSource}s can be modified
 * during playback. It is valid for the same {@link MediaSource} instance to be present more than
 * once in the playlist.
 *
 * <p>With the exception of the constructor, all methods are called on the playback thread.
 */
/* package */ final class MediaSourceList {

  /** Listener for source events. */
  public interface MediaSourceListInfoRefreshListener {

    /**
     * Called when the timeline of a media item has changed and a new timeline that reflects the
     * current playlist state needs to be created by calling {@link #createTimeline()}.
     *
     * <p>Called on the playback thread.
     */
    void onPlaylistUpdateRequested();
  }

  private static final String TAG = "MediaSourceList";

  private final PlayerId playerId;
  private final List<MediaSourceHolder> mediaSourceHolders;
  private final IdentityHashMap<MediaPeriod, MediaSourceHolder> mediaSourceByMediaPeriod;
  private final Map<Object, MediaSourceHolder> mediaSourceByUid;
  private final MediaSourceListInfoRefreshListener mediaSourceListInfoListener;
  private final HashMap<MediaSourceList.MediaSourceHolder, MediaSourceAndListener> childSources;
  private final Set<MediaSourceHolder> enabledMediaSourceHolders;
  private final AnalyticsCollector eventListener;
  private final HandlerWrapper eventHandler;
  private ShuffleOrder shuffleOrder;
  private boolean isPrepared;

  @Nullable private TransferListener mediaTransferListener;

  /**
   * Creates the media source list.
   *
   * @param listener The {@link MediaSourceListInfoRefreshListener} to be informed of timeline
   *     changes.
   * @param analyticsCollector An {@link AnalyticsCollector} to be registered for media source
   *     events.
   * @param analyticsCollectorHandler The {@link Handler} to call {@link AnalyticsCollector} methods
   *     on.
   * @param playerId The {@link PlayerId} of the player using this list.
   */
  public MediaSourceList(
      MediaSourceListInfoRefreshListener listener,
      AnalyticsCollector analyticsCollector,
      HandlerWrapper analyticsCollectorHandler,
      PlayerId playerId) {
    this.playerId = playerId;
    mediaSourceListInfoListener = listener;
    shuffleOrder = new DefaultShuffleOrder(0);
    mediaSourceByMediaPeriod = new IdentityHashMap<>();
    mediaSourceByUid = new HashMap<>();
    mediaSourceHolders = new ArrayList<>();
    eventListener = analyticsCollector;
    eventHandler = analyticsCollectorHandler;
    childSources = new HashMap<>();
    enabledMediaSourceHolders = new HashSet<>();
  }

  /**
   * Sets the media sources replacing any sources previously contained in the playlist.
   *
   * @param holders The list of {@link MediaSourceHolder}s to set.
   * @param shuffleOrder The new shuffle order.
   * @return The new {@link Timeline}.
   */
  public Timeline setMediaSources(List<MediaSourceHolder> holders, ShuffleOrder shuffleOrder) {
    removeMediaSourcesInternal(/* fromIndex= */ 0, /* toIndex= */ mediaSourceHolders.size());
    return addMediaSources(/* index= */ this.mediaSourceHolders.size(), holders, shuffleOrder);
  }

  /**
   * Adds multiple {@link MediaSourceHolder}s to the playlist.
   *
   * @param index The index at which the new {@link MediaSourceHolder}s will be inserted. This index
   *     must be in the range of 0 &lt;= index &lt;= {@link #getSize()}.
   * @param holders A list of {@link MediaSourceHolder}s to be added.
   * @param shuffleOrder The new shuffle order.
   * @return The new {@link Timeline}.
   */
  public Timeline addMediaSources(
      int index, List<MediaSourceHolder> holders, ShuffleOrder shuffleOrder) {
    if (!holders.isEmpty()) {
      this.shuffleOrder = shuffleOrder;
      for (int insertionIndex = index; insertionIndex < index + holders.size(); insertionIndex++) {
        MediaSourceHolder holder = holders.get(insertionIndex - index);
        if (insertionIndex > 0) {
          MediaSourceHolder previousHolder = mediaSourceHolders.get(insertionIndex - 1);
          Timeline previousTimeline = previousHolder.mediaSource.getTimeline();
          holder.reset(
              /* firstWindowIndexInChild= */ previousHolder.firstWindowIndexInChild
                  + previousTimeline.getWindowCount());
        } else {
          holder.reset(/* firstWindowIndexInChild= */ 0);
        }
        Timeline newTimeline = holder.mediaSource.getTimeline();
        correctOffsets(
            /* startIndex= */ insertionIndex,
            /* windowOffsetUpdate= */ newTimeline.getWindowCount());
        mediaSourceHolders.add(insertionIndex, holder);
        mediaSourceByUid.put(holder.uid, holder);
        if (isPrepared) {
          prepareChildSource(holder);
          if (mediaSourceByMediaPeriod.isEmpty()) {
            enabledMediaSourceHolders.add(holder);
          } else {
            disableChildSource(holder);
          }
        }
      }
    }
    return createTimeline();
  }

  /**
   * Removes a range of {@link MediaSourceHolder}s from the playlist, by specifying an initial index
   * (included) and a final index (excluded).
   *
   * <p>Note: when specified range is empty, no actual media source is removed and no exception is
   * thrown.
   *
   * @param fromIndex The initial range index, pointing to the first media source that will be
   *     removed. This index must be in the range of 0 &lt;= index &lt;= {@link #getSize()}.
   * @param toIndex The final range index, pointing to the first media source that will be left
   *     untouched. This index must be in the range of 0 &lt;= index &lt;= {@link #getSize()}.
   * @param shuffleOrder The new shuffle order.
   * @return The new {@link Timeline}.
   * @throws IllegalArgumentException When the range is malformed, i.e. {@code fromIndex} &lt; 0,
   *     {@code toIndex} &gt; {@link #getSize()}, {@code fromIndex} &gt; {@code toIndex}
   */
  public Timeline removeMediaSourceRange(int fromIndex, int toIndex, ShuffleOrder shuffleOrder) {
    Assertions.checkArgument(fromIndex >= 0 && fromIndex <= toIndex && toIndex <= getSize());
    this.shuffleOrder = shuffleOrder;
    removeMediaSourcesInternal(fromIndex, toIndex);
    return createTimeline();
  }

  /**
   * Moves an existing media source within the playlist.
   *
   * @param currentIndex The current index of the media source in the playlist. This index must be
   *     in the range of 0 &lt;= index &lt; {@link #getSize()}.
   * @param newIndex The target index of the media source in the playlist. This index must be in the
   *     range of 0 &lt;= index &lt; {@link #getSize()}.
   * @param shuffleOrder The new shuffle order.
   * @return The new {@link Timeline}.
   * @throws IllegalArgumentException When an index is invalid, i.e. {@code currentIndex} &lt; 0,
   *     {@code currentIndex} &gt;= {@link #getSize()}, {@code newIndex} &lt; 0
   */
  public Timeline moveMediaSource(int currentIndex, int newIndex, ShuffleOrder shuffleOrder) {
    return moveMediaSourceRange(currentIndex, currentIndex + 1, newIndex, shuffleOrder);
  }

  /**
   * Moves a range of media sources within the playlist.
   *
   * <p>Note: when specified range is empty or the from index equals the new from index, no actual
   * media source is moved and no exception is thrown.
   *
   * @param fromIndex The initial range index, pointing to the first media source of the range that
   *     will be moved. This index must be in the range of 0 &lt;= index &lt;= {@link #getSize()}.
   * @param toIndex The final range index, pointing to the first media source that will be left
   *     untouched. This index must be larger or equals than {@code fromIndex}.
   * @param newFromIndex The target index of the first media source of the range that will be moved.
   * @param shuffleOrder The new shuffle order.
   * @return The new {@link Timeline}.
   * @throws IllegalArgumentException When the range is malformed, i.e. {@code fromIndex} &lt; 0,
   *     {@code toIndex} &lt; {@code fromIndex}, {@code fromIndex} &gt; {@code toIndex}, {@code
   *     newFromIndex} &lt; 0
   */
  public Timeline moveMediaSourceRange(
      int fromIndex, int toIndex, int newFromIndex, ShuffleOrder shuffleOrder) {
    Assertions.checkArgument(
        fromIndex >= 0 && fromIndex <= toIndex && toIndex <= getSize() && newFromIndex >= 0);
    this.shuffleOrder = shuffleOrder;
    if (fromIndex == toIndex || fromIndex == newFromIndex) {
      return createTimeline();
    }
    int startIndex = min(fromIndex, newFromIndex);
    int newEndIndex = newFromIndex + (toIndex - fromIndex) - 1;
    int endIndex = max(newEndIndex, toIndex - 1);
    int windowOffset = mediaSourceHolders.get(startIndex).firstWindowIndexInChild;
    Util.moveItems(mediaSourceHolders, fromIndex, toIndex, newFromIndex);
    for (int i = startIndex; i <= endIndex; i++) {
      MediaSourceHolder holder = mediaSourceHolders.get(i);
      holder.firstWindowIndexInChild = windowOffset;
      windowOffset += holder.mediaSource.getTimeline().getWindowCount();
    }
    return createTimeline();
  }

  /** Clears the playlist. */
  public Timeline clear(@Nullable ShuffleOrder shuffleOrder) {
    this.shuffleOrder = shuffleOrder != null ? shuffleOrder : this.shuffleOrder.cloneAndClear();
    removeMediaSourcesInternal(/* fromIndex= */ 0, /* toIndex= */ getSize());
    return createTimeline();
  }

  /** Whether the playlist is prepared. */
  public boolean isPrepared() {
    return isPrepared;
  }

  /** Returns the number of media sources in the playlist. */
  public int getSize() {
    return mediaSourceHolders.size();
  }

  /**
   * Sets a new shuffle order to use when shuffling the child media sources.
   *
   * @param shuffleOrder A {@link ShuffleOrder}.
   */
  public Timeline setShuffleOrder(ShuffleOrder shuffleOrder) {
    int size = getSize();
    if (shuffleOrder.getLength() != size) {
      shuffleOrder =
          shuffleOrder
              .cloneAndClear()
              .cloneAndInsert(/* insertionIndex= */ 0, /* insertionCount= */ size);
    }
    this.shuffleOrder = shuffleOrder;
    return createTimeline();
  }

  /** Prepares the playlist. */
  public void prepare(@Nullable TransferListener mediaTransferListener) {
    Assertions.checkState(!isPrepared);
    this.mediaTransferListener = mediaTransferListener;
    for (int i = 0; i < mediaSourceHolders.size(); i++) {
      MediaSourceHolder mediaSourceHolder = mediaSourceHolders.get(i);
      prepareChildSource(mediaSourceHolder);
      enabledMediaSourceHolders.add(mediaSourceHolder);
    }
    isPrepared = true;
  }

  /**
   * Returns a new {@link MediaPeriod} identified by {@code periodId}.
   *
   * @param id The identifier of the period.
   * @param allocator An {@link Allocator} from which to obtain media buffer allocations.
   * @param startPositionUs The expected start position, in microseconds.
   * @return A new {@link MediaPeriod}.
   */
  public MediaPeriod createPeriod(
      MediaSource.MediaPeriodId id, Allocator allocator, long startPositionUs) {
    Object mediaSourceHolderUid = getMediaSourceHolderUid(id.periodUid);
    MediaSource.MediaPeriodId childMediaPeriodId =
        id.copyWithPeriodUid(getChildPeriodUid(id.periodUid));
    MediaSourceHolder holder = checkNotNull(mediaSourceByUid.get(mediaSourceHolderUid));
    enableMediaSource(holder);
    holder.activeMediaPeriodIds.add(childMediaPeriodId);
    MediaPeriod mediaPeriod =
        holder.mediaSource.createPeriod(childMediaPeriodId, allocator, startPositionUs);
    mediaSourceByMediaPeriod.put(mediaPeriod, holder);
    disableUnusedMediaSources();
    return mediaPeriod;
  }

  /**
   * Releases the period.
   *
   * @param mediaPeriod The period to release.
   */
  public void releasePeriod(MediaPeriod mediaPeriod) {
    MediaSourceHolder holder = checkNotNull(mediaSourceByMediaPeriod.remove(mediaPeriod));
    holder.mediaSource.releasePeriod(mediaPeriod);
    holder.activeMediaPeriodIds.remove(((MaskingMediaPeriod) mediaPeriod).id);
    if (!mediaSourceByMediaPeriod.isEmpty()) {
      disableUnusedMediaSources();
    }
    maybeReleaseChildSource(holder);
  }

  /** Releases the playlist. */
  public void release() {
    for (MediaSourceAndListener childSource : childSources.values()) {
      try {
        childSource.mediaSource.releaseSource(childSource.caller);
      } catch (RuntimeException e) {
        // There's nothing we can do.
        Log.e(TAG, "Failed to release child source.", e);
      }
      childSource.mediaSource.removeEventListener(childSource.eventListener);
      childSource.mediaSource.removeDrmEventListener(childSource.eventListener);
    }
    childSources.clear();
    enabledMediaSourceHolders.clear();
    isPrepared = false;
  }

  /** Creates a timeline reflecting the current state of the playlist. */
  public Timeline createTimeline() {
    if (mediaSourceHolders.isEmpty()) {
      return Timeline.EMPTY;
    }
    int windowOffset = 0;
    for (int i = 0; i < mediaSourceHolders.size(); i++) {
      MediaSourceHolder mediaSourceHolder = mediaSourceHolders.get(i);
      mediaSourceHolder.firstWindowIndexInChild = windowOffset;
      windowOffset += mediaSourceHolder.mediaSource.getTimeline().getWindowCount();
    }
    return new PlaylistTimeline(mediaSourceHolders, shuffleOrder);
  }

  // Internal methods.

  private void enableMediaSource(MediaSourceHolder mediaSourceHolder) {
    enabledMediaSourceHolders.add(mediaSourceHolder);
    @Nullable MediaSourceAndListener enabledChild = childSources.get(mediaSourceHolder);
    if (enabledChild != null) {
      enabledChild.mediaSource.enable(enabledChild.caller);
    }
  }

  private void disableUnusedMediaSources() {
    Iterator<MediaSourceHolder> iterator = enabledMediaSourceHolders.iterator();
    while (iterator.hasNext()) {
      MediaSourceHolder holder = iterator.next();
      if (holder.activeMediaPeriodIds.isEmpty()) {
        disableChildSource(holder);
        iterator.remove();
      }
    }
  }

  private void disableChildSource(MediaSourceHolder holder) {
    @Nullable MediaSourceAndListener disabledChild = childSources.get(holder);
    if (disabledChild != null) {
      disabledChild.mediaSource.disable(disabledChild.caller);
    }
  }

  private void removeMediaSourcesInternal(int fromIndex, int toIndex) {
    for (int index = toIndex - 1; index >= fromIndex; index--) {
      MediaSourceHolder holder = mediaSourceHolders.remove(index);
      mediaSourceByUid.remove(holder.uid);
      Timeline oldTimeline = holder.mediaSource.getTimeline();
      correctOffsets(
          /* startIndex= */ index, /* windowOffsetUpdate= */ -oldTimeline.getWindowCount());
      holder.isRemoved = true;
      if (isPrepared) {
        maybeReleaseChildSource(holder);
      }
    }
  }

  private void correctOffsets(int startIndex, int windowOffsetUpdate) {
    for (int i = startIndex; i < mediaSourceHolders.size(); i++) {
      MediaSourceHolder mediaSourceHolder = mediaSourceHolders.get(i);
      mediaSourceHolder.firstWindowIndexInChild += windowOffsetUpdate;
    }
  }

  // Internal methods to manage child sources.

  @Nullable
  private static MediaSource.MediaPeriodId getMediaPeriodIdForChildMediaPeriodId(
      MediaSourceHolder mediaSourceHolder, MediaSource.MediaPeriodId mediaPeriodId) {
    for (int i = 0; i < mediaSourceHolder.activeMediaPeriodIds.size(); i++) {
      // Ensure the reported media period id has the same window sequence number as the one created
      // by this media source. Otherwise it does not belong to this child source.
      if (mediaSourceHolder.activeMediaPeriodIds.get(i).windowSequenceNumber
          == mediaPeriodId.windowSequenceNumber) {
        Object periodUid = getPeriodUid(mediaSourceHolder, mediaPeriodId.periodUid);
        return mediaPeriodId.copyWithPeriodUid(periodUid);
      }
    }
    return null;
  }

  private static int getWindowIndexForChildWindowIndex(
      MediaSourceHolder mediaSourceHolder, int windowIndex) {
    return windowIndex + mediaSourceHolder.firstWindowIndexInChild;
  }

  private void prepareChildSource(MediaSourceHolder holder) {
    MediaSource mediaSource = holder.mediaSource;
    MediaSource.MediaSourceCaller caller =
        (source, timeline) -> mediaSourceListInfoListener.onPlaylistUpdateRequested();
    ForwardingEventListener eventListener = new ForwardingEventListener(holder);
    childSources.put(holder, new MediaSourceAndListener(mediaSource, caller, eventListener));
    mediaSource.addEventListener(Util.createHandlerForCurrentOrMainLooper(), eventListener);
    mediaSource.addDrmEventListener(Util.createHandlerForCurrentOrMainLooper(), eventListener);
    mediaSource.prepareSource(caller, mediaTransferListener, playerId);
  }

  private void maybeReleaseChildSource(MediaSourceHolder mediaSourceHolder) {
    // Release if the source has been removed from the playlist and no periods are still active.
    if (mediaSourceHolder.isRemoved && mediaSourceHolder.activeMediaPeriodIds.isEmpty()) {
      MediaSourceAndListener removedChild = checkNotNull(childSources.remove(mediaSourceHolder));
      removedChild.mediaSource.releaseSource(removedChild.caller);
      removedChild.mediaSource.removeEventListener(removedChild.eventListener);
      removedChild.mediaSource.removeDrmEventListener(removedChild.eventListener);
      enabledMediaSourceHolders.remove(mediaSourceHolder);
    }
  }

  /** Return uid of media source holder from period uid of concatenated source. */
  private static Object getMediaSourceHolderUid(Object periodUid) {
    return PlaylistTimeline.getChildTimelineUidFromConcatenatedUid(periodUid);
  }

  /** Return uid of child period from period uid of concatenated source. */
  private static Object getChildPeriodUid(Object periodUid) {
    return PlaylistTimeline.getChildPeriodUidFromConcatenatedUid(periodUid);
  }

  private static Object getPeriodUid(MediaSourceHolder holder, Object childPeriodUid) {
    return PlaylistTimeline.getConcatenatedUid(holder.uid, childPeriodUid);
  }

  /** Data class to hold playlist media sources together with meta data needed to process them. */
  /* package */ static final class MediaSourceHolder implements MediaSourceInfoHolder {

    public final MaskingMediaSource mediaSource;
    public final Object uid;
    public final List<MediaSource.MediaPeriodId> activeMediaPeriodIds;

    public int firstWindowIndexInChild;
    public boolean isRemoved;

    public MediaSourceHolder(MediaSource mediaSource, boolean useLazyPreparation) {
      this.mediaSource = new MaskingMediaSource(mediaSource, useLazyPreparation);
      this.activeMediaPeriodIds = new ArrayList<>();
      this.uid = new Object();
    }

    public void reset(int firstWindowIndexInChild) {
      this.firstWindowIndexInChild = firstWindowIndexInChild;
      this.isRemoved = false;
      this.activeMediaPeriodIds.clear();
    }

    @Override
    public Object getUid() {
      return uid;
    }

    @Override
    public Timeline getTimeline() {
      return mediaSource.getTimeline();
    }
  }

  private static final class MediaSourceAndListener {

    public final MediaSource mediaSource;
    public final MediaSource.MediaSourceCaller caller;
    public final ForwardingEventListener eventListener;

    public MediaSourceAndListener(
        MediaSource mediaSource,
        MediaSource.MediaSourceCaller caller,
        ForwardingEventListener eventListener) {
      this.mediaSource = mediaSource;
      this.caller = caller;
      this.eventListener = eventListener;
    }
  }

  private final class ForwardingEventListener
      implements MediaSourceEventListener, DrmSessionEventListener {

    private final MediaSourceList.MediaSourceHolder id;

    public ForwardingEventListener(MediaSourceList.MediaSourceHolder id) {
      this.id = id;
    }

    // MediaSourceEventListener implementation

    @Override
    public void onLoadStarted(
        int windowIndex,
        @Nullable MediaSource.MediaPeriodId mediaPeriodId,
        LoadEventInfo loadEventData,
        MediaLoadData mediaLoadData) {
      @Nullable
      Pair<Integer, MediaSource.@NullableType MediaPeriodId> eventParameters =
          getEventParameters(windowIndex, mediaPeriodId);
      if (eventParameters != null) {
        eventHandler.post(
            () ->
                eventListener.onLoadStarted(
                    eventParameters.first, eventParameters.second, loadEventData, mediaLoadData));
      }
    }

    @Override
    public void onLoadCompleted(
        int windowIndex,
        @Nullable MediaSource.MediaPeriodId mediaPeriodId,
        LoadEventInfo loadEventData,
        MediaLoadData mediaLoadData) {
      @Nullable
      Pair<Integer, MediaSource.@NullableType MediaPeriodId> eventParameters =
          getEventParameters(windowIndex, mediaPeriodId);
      if (eventParameters != null) {
        eventHandler.post(
            () ->
                eventListener.onLoadCompleted(
                    eventParameters.first, eventParameters.second, loadEventData, mediaLoadData));
      }
    }

    @Override
    public void onLoadCanceled(
        int windowIndex,
        @Nullable MediaSource.MediaPeriodId mediaPeriodId,
        LoadEventInfo loadEventData,
        MediaLoadData mediaLoadData) {
      @Nullable
      Pair<Integer, MediaSource.@NullableType MediaPeriodId> eventParameters =
          getEventParameters(windowIndex, mediaPeriodId);
      if (eventParameters != null) {
        eventHandler.post(
            () ->
                eventListener.onLoadCanceled(
                    eventParameters.first, eventParameters.second, loadEventData, mediaLoadData));
      }
    }

    @Override
    public void onLoadError(
        int windowIndex,
        @Nullable MediaSource.MediaPeriodId mediaPeriodId,
        LoadEventInfo loadEventData,
        MediaLoadData mediaLoadData,
        IOException error,
        boolean wasCanceled) {
      @Nullable
      Pair<Integer, MediaSource.@NullableType MediaPeriodId> eventParameters =
          getEventParameters(windowIndex, mediaPeriodId);
      if (eventParameters != null) {
        eventHandler.post(
            () ->
                eventListener.onLoadError(
                    eventParameters.first,
                    eventParameters.second,
                    loadEventData,
                    mediaLoadData,
                    error,
                    wasCanceled));
      }
    }

    @Override
    public void onUpstreamDiscarded(
        int windowIndex,
        @Nullable MediaSource.MediaPeriodId mediaPeriodId,
        MediaLoadData mediaLoadData) {
      @Nullable
      Pair<Integer, MediaSource.@NullableType MediaPeriodId> eventParameters =
          getEventParameters(windowIndex, mediaPeriodId);
      if (eventParameters != null) {
        eventHandler.post(
            () ->
                eventListener.onUpstreamDiscarded(
                    eventParameters.first, checkNotNull(eventParameters.second), mediaLoadData));
      }
    }

    @Override
    public void onDownstreamFormatChanged(
        int windowIndex,
        @Nullable MediaSource.MediaPeriodId mediaPeriodId,
        MediaLoadData mediaLoadData) {
      @Nullable
      Pair<Integer, MediaSource.@NullableType MediaPeriodId> eventParameters =
          getEventParameters(windowIndex, mediaPeriodId);
      if (eventParameters != null) {
        eventHandler.post(
            () ->
                eventListener.onDownstreamFormatChanged(
                    eventParameters.first, eventParameters.second, mediaLoadData));
      }
    }

    // DrmSessionEventListener implementation

    @Override
    public void onDrmSessionAcquired(
        int windowIndex,
        @Nullable MediaSource.MediaPeriodId mediaPeriodId,
        @DrmSession.State int state) {
      @Nullable
      Pair<Integer, MediaSource.@NullableType MediaPeriodId> eventParameters =
          getEventParameters(windowIndex, mediaPeriodId);
      if (eventParameters != null) {
        eventHandler.post(
            () ->
                eventListener.onDrmSessionAcquired(
                    eventParameters.first, eventParameters.second, state));
      }
    }

    @Override
    public void onDrmKeysLoaded(
        int windowIndex, @Nullable MediaSource.MediaPeriodId mediaPeriodId) {
      @Nullable
      Pair<Integer, MediaSource.@NullableType MediaPeriodId> eventParameters =
          getEventParameters(windowIndex, mediaPeriodId);
      if (eventParameters != null) {
        eventHandler.post(
            () -> eventListener.onDrmKeysLoaded(eventParameters.first, eventParameters.second));
      }
    }

    @Override
    public void onDrmSessionManagerError(
        int windowIndex, @Nullable MediaSource.MediaPeriodId mediaPeriodId, Exception error) {
      @Nullable
      Pair<Integer, MediaSource.@NullableType MediaPeriodId> eventParameters =
          getEventParameters(windowIndex, mediaPeriodId);
      if (eventParameters != null) {
        eventHandler.post(
            () ->
                eventListener.onDrmSessionManagerError(
                    eventParameters.first, eventParameters.second, error));
      }
    }

    @Override
    public void onDrmKeysRestored(
        int windowIndex, @Nullable MediaSource.MediaPeriodId mediaPeriodId) {
      @Nullable
      Pair<Integer, MediaSource.@NullableType MediaPeriodId> eventParameters =
          getEventParameters(windowIndex, mediaPeriodId);
      if (eventParameters != null) {
        eventHandler.post(
            () -> eventListener.onDrmKeysRestored(eventParameters.first, eventParameters.second));
      }
    }

    @Override
    public void onDrmKeysRemoved(
        int windowIndex, @Nullable MediaSource.MediaPeriodId mediaPeriodId) {
      @Nullable
      Pair<Integer, MediaSource.@NullableType MediaPeriodId> eventParameters =
          getEventParameters(windowIndex, mediaPeriodId);
      if (eventParameters != null) {
        eventHandler.post(
            () -> eventListener.onDrmKeysRemoved(eventParameters.first, eventParameters.second));
      }
    }

    @Override
    public void onDrmSessionReleased(
        int windowIndex, @Nullable MediaSource.MediaPeriodId mediaPeriodId) {
      @Nullable
      Pair<Integer, MediaSource.@NullableType MediaPeriodId> eventParameters =
          getEventParameters(windowIndex, mediaPeriodId);
      if (eventParameters != null) {
        eventHandler.post(
            () ->
                eventListener.onDrmSessionReleased(eventParameters.first, eventParameters.second));
      }
    }

    /** Updates the event parameters and returns whether the event should be dispatched. */
    @Nullable
    private Pair<Integer, MediaSource.@NullableType MediaPeriodId> getEventParameters(
        int childWindowIndex, @Nullable MediaSource.MediaPeriodId childMediaPeriodId) {
      @Nullable MediaSource.MediaPeriodId mediaPeriodId = null;
      if (childMediaPeriodId != null) {
        mediaPeriodId = getMediaPeriodIdForChildMediaPeriodId(id, childMediaPeriodId);
        if (mediaPeriodId == null) {
          // Media period not found. Ignore event.
          return null;
        }
      }
      int windowIndex = getWindowIndexForChildWindowIndex(id, childWindowIndex);
      return Pair.create(windowIndex, mediaPeriodId);
    }
  }
}
