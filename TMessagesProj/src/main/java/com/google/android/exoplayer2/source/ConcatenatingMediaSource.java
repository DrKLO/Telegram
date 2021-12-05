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
package com.google.android.exoplayer2.source;

import android.os.Handler;
import android.os.Message;
import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.ConcatenatingMediaSource.MediaSourceHolder;
import com.google.android.exoplayer2.source.ShuffleOrder.DefaultShuffleOrder;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Concatenates multiple {@link MediaSource}s. The list of {@link MediaSource}s can be modified
 * during playback. It is valid for the same {@link MediaSource} instance to be present more than
 * once in the concatenation. Access to this class is thread-safe.
 */
public final class ConcatenatingMediaSource extends CompositeMediaSource<MediaSourceHolder> {

  private static final int MSG_ADD = 0;
  private static final int MSG_REMOVE = 1;
  private static final int MSG_MOVE = 2;
  private static final int MSG_SET_SHUFFLE_ORDER = 3;
  private static final int MSG_UPDATE_TIMELINE = 4;
  private static final int MSG_ON_COMPLETION = 5;

  // Accessed on any thread.
  @GuardedBy("this")
  private final List<MediaSourceHolder> mediaSourcesPublic;

  @GuardedBy("this")
  private final Set<HandlerAndRunnable> pendingOnCompletionActions;

  @GuardedBy("this")
  @Nullable
  private Handler playbackThreadHandler;

  // Accessed on the playback thread only.
  private final List<MediaSourceHolder> mediaSourceHolders;
  private final Map<MediaPeriod, MediaSourceHolder> mediaSourceByMediaPeriod;
  private final Map<Object, MediaSourceHolder> mediaSourceByUid;
  private final Set<MediaSourceHolder> enabledMediaSourceHolders;
  private final boolean isAtomic;
  private final boolean useLazyPreparation;

  private boolean timelineUpdateScheduled;
  private Set<HandlerAndRunnable> nextTimelineUpdateOnCompletionActions;
  private ShuffleOrder shuffleOrder;

  /**
   * @param mediaSources The {@link MediaSource}s to concatenate. It is valid for the same
   *     {@link MediaSource} instance to be present more than once in the array.
   */
  public ConcatenatingMediaSource(MediaSource... mediaSources) {
    this(/* isAtomic= */ false, mediaSources);
  }

  /**
   * @param isAtomic Whether the concatenating media source will be treated as atomic, i.e., treated
   *     as a single item for repeating and shuffling.
   * @param mediaSources The {@link MediaSource}s to concatenate. It is valid for the same {@link
   *     MediaSource} instance to be present more than once in the array.
   */
  public ConcatenatingMediaSource(boolean isAtomic, MediaSource... mediaSources) {
    this(isAtomic, new DefaultShuffleOrder(0), mediaSources);
  }

  /**
   * @param isAtomic Whether the concatenating media source will be treated as atomic, i.e., treated
   *     as a single item for repeating and shuffling.
   * @param shuffleOrder The {@link ShuffleOrder} to use when shuffling the child media sources.
   * @param mediaSources The {@link MediaSource}s to concatenate. It is valid for the same {@link
   *     MediaSource} instance to be present more than once in the array.
   */
  public ConcatenatingMediaSource(
      boolean isAtomic, ShuffleOrder shuffleOrder, MediaSource... mediaSources) {
    this(isAtomic, /* useLazyPreparation= */ false, shuffleOrder, mediaSources);
  }

  /**
   * @param isAtomic Whether the concatenating media source will be treated as atomic, i.e., treated
   *     as a single item for repeating and shuffling.
   * @param useLazyPreparation Whether playlist items are prepared lazily. If false, all manifest
   *     loads and other initial preparation steps happen immediately. If true, these initial
   *     preparations are triggered only when the player starts buffering the media.
   * @param shuffleOrder The {@link ShuffleOrder} to use when shuffling the child media sources.
   * @param mediaSources The {@link MediaSource}s to concatenate. It is valid for the same {@link
   *     MediaSource} instance to be present more than once in the array.
   */
  @SuppressWarnings("initialization")
  public ConcatenatingMediaSource(
      boolean isAtomic,
      boolean useLazyPreparation,
      ShuffleOrder shuffleOrder,
      MediaSource... mediaSources) {
    for (MediaSource mediaSource : mediaSources) {
      Assertions.checkNotNull(mediaSource);
    }
    this.shuffleOrder = shuffleOrder.getLength() > 0 ? shuffleOrder.cloneAndClear() : shuffleOrder;
    this.mediaSourceByMediaPeriod = new IdentityHashMap<>();
    this.mediaSourceByUid = new HashMap<>();
    this.mediaSourcesPublic = new ArrayList<>();
    this.mediaSourceHolders = new ArrayList<>();
    this.nextTimelineUpdateOnCompletionActions = new HashSet<>();
    this.pendingOnCompletionActions = new HashSet<>();
    this.enabledMediaSourceHolders = new HashSet<>();
    this.isAtomic = isAtomic;
    this.useLazyPreparation = useLazyPreparation;
    addMediaSources(Arrays.asList(mediaSources));
  }

  /**
   * Appends a {@link MediaSource} to the playlist.
   *
   * @param mediaSource The {@link MediaSource} to be added to the list.
   */
  public synchronized void addMediaSource(MediaSource mediaSource) {
    addMediaSource(mediaSourcesPublic.size(), mediaSource);
  }

  /**
   * Appends a {@link MediaSource} to the playlist and executes a custom action on completion.
   *
   * @param mediaSource The {@link MediaSource} to be added to the list.
   * @param handler The {@link Handler} to run {@code onCompletionAction}.
   * @param onCompletionAction A {@link Runnable} which is executed immediately after the media
   *     source has been added to the playlist.
   */
  public synchronized void addMediaSource(
      MediaSource mediaSource, Handler handler, Runnable onCompletionAction) {
    addMediaSource(mediaSourcesPublic.size(), mediaSource, handler, onCompletionAction);
  }

  /**
   * Adds a {@link MediaSource} to the playlist.
   *
   * @param index The index at which the new {@link MediaSource} will be inserted. This index must
   *     be in the range of 0 &lt;= index &lt;= {@link #getSize()}.
   * @param mediaSource The {@link MediaSource} to be added to the list.
   */
  public synchronized void addMediaSource(int index, MediaSource mediaSource) {
    addPublicMediaSources(
        index,
        Collections.singletonList(mediaSource),
        /* handler= */ null,
        /* onCompletionAction= */ null);
  }

  /**
   * Adds a {@link MediaSource} to the playlist and executes a custom action on completion.
   *
   * @param index The index at which the new {@link MediaSource} will be inserted. This index must
   *     be in the range of 0 &lt;= index &lt;= {@link #getSize()}.
   * @param mediaSource The {@link MediaSource} to be added to the list.
   * @param handler The {@link Handler} to run {@code onCompletionAction}.
   * @param onCompletionAction A {@link Runnable} which is executed immediately after the media
   *     source has been added to the playlist.
   */
  public synchronized void addMediaSource(
      int index, MediaSource mediaSource, Handler handler, Runnable onCompletionAction) {
    addPublicMediaSources(
        index, Collections.singletonList(mediaSource), handler, onCompletionAction);
  }

  /**
   * Appends multiple {@link MediaSource}s to the playlist.
   *
   * @param mediaSources A collection of {@link MediaSource}s to be added to the list. The media
   *     sources are added in the order in which they appear in this collection.
   */
  public synchronized void addMediaSources(Collection<MediaSource> mediaSources) {
    addPublicMediaSources(
        mediaSourcesPublic.size(),
        mediaSources,
        /* handler= */ null,
        /* onCompletionAction= */ null);
  }

  /**
   * Appends multiple {@link MediaSource}s to the playlist and executes a custom action on
   * completion.
   *
   * @param mediaSources A collection of {@link MediaSource}s to be added to the list. The media
   *     sources are added in the order in which they appear in this collection.
   * @param handler The {@link Handler} to run {@code onCompletionAction}.
   * @param onCompletionAction A {@link Runnable} which is executed immediately after the media
   *     sources have been added to the playlist.
   */
  public synchronized void addMediaSources(
      Collection<MediaSource> mediaSources, Handler handler, Runnable onCompletionAction) {
    addPublicMediaSources(mediaSourcesPublic.size(), mediaSources, handler, onCompletionAction);
  }

  /**
   * Adds multiple {@link MediaSource}s to the playlist.
   *
   * @param index The index at which the new {@link MediaSource}s will be inserted. This index must
   *     be in the range of 0 &lt;= index &lt;= {@link #getSize()}.
   * @param mediaSources A collection of {@link MediaSource}s to be added to the list. The media
   *     sources are added in the order in which they appear in this collection.
   */
  public synchronized void addMediaSources(int index, Collection<MediaSource> mediaSources) {
    addPublicMediaSources(index, mediaSources, /* handler= */ null, /* onCompletionAction= */ null);
  }

  /**
   * Adds multiple {@link MediaSource}s to the playlist and executes a custom action on completion.
   *
   * @param index The index at which the new {@link MediaSource}s will be inserted. This index must
   *     be in the range of 0 &lt;= index &lt;= {@link #getSize()}.
   * @param mediaSources A collection of {@link MediaSource}s to be added to the list. The media
   *     sources are added in the order in which they appear in this collection.
   * @param handler The {@link Handler} to run {@code onCompletionAction}.
   * @param onCompletionAction A {@link Runnable} which is executed immediately after the media
   *     sources have been added to the playlist.
   */
  public synchronized void addMediaSources(
      int index,
      Collection<MediaSource> mediaSources,
      Handler handler,
      Runnable onCompletionAction) {
    addPublicMediaSources(index, mediaSources, handler, onCompletionAction);
  }

  /**
   * Removes a {@link MediaSource} from the playlist.
   *
   * <p>Note: If you want to move the instance, it's preferable to use {@link #moveMediaSource(int,
   * int)} instead.
   *
   * <p>Note: If you want to remove a set of contiguous sources, it's preferable to use {@link
   * #removeMediaSourceRange(int, int)} instead.
   *
   * @param index The index at which the media source will be removed. This index must be in the
   *     range of 0 &lt;= index &lt; {@link #getSize()}.
   * @return The removed {@link MediaSource}.
   */
  public synchronized MediaSource removeMediaSource(int index) {
    MediaSource removedMediaSource = getMediaSource(index);
    removePublicMediaSources(index, index + 1, /* handler= */ null, /* onCompletionAction= */ null);
    return removedMediaSource;
  }

  /**
   * Removes a {@link MediaSource} from the playlist and executes a custom action on completion.
   *
   * <p>Note: If you want to move the instance, it's preferable to use {@link #moveMediaSource(int,
   * int, Handler, Runnable)} instead.
   *
   * <p>Note: If you want to remove a set of contiguous sources, it's preferable to use {@link
   * #removeMediaSourceRange(int, int, Handler, Runnable)} instead.
   *
   * @param index The index at which the media source will be removed. This index must be in the
   *     range of 0 &lt;= index &lt; {@link #getSize()}.
   * @param handler The {@link Handler} to run {@code onCompletionAction}.
   * @param onCompletionAction A {@link Runnable} which is executed immediately after the media
   *     source has been removed from the playlist.
   * @return The removed {@link MediaSource}.
   */
  public synchronized MediaSource removeMediaSource(
      int index, Handler handler, Runnable onCompletionAction) {
    MediaSource removedMediaSource = getMediaSource(index);
    removePublicMediaSources(index, index + 1, handler, onCompletionAction);
    return removedMediaSource;
  }

  /**
   * Removes a range of {@link MediaSource}s from the playlist, by specifying an initial index
   * (included) and a final index (excluded).
   *
   * <p>Note: when specified range is empty, no actual media source is removed and no exception is
   * thrown.
   *
   * @param fromIndex The initial range index, pointing to the first media source that will be
   *     removed. This index must be in the range of 0 &lt;= index &lt;= {@link #getSize()}.
   * @param toIndex The final range index, pointing to the first media source that will be left
   *     untouched. This index must be in the range of 0 &lt;= index &lt;= {@link #getSize()}.
   * @throws IndexOutOfBoundsException When the range is malformed, i.e. {@code fromIndex} &lt; 0,
   *     {@code toIndex} &gt; {@link #getSize()}, {@code fromIndex} &gt; {@code toIndex}
   */
  public synchronized void removeMediaSourceRange(int fromIndex, int toIndex) {
    removePublicMediaSources(
        fromIndex, toIndex, /* handler= */ null, /* onCompletionAction= */ null);
  }

  /**
   * Removes a range of {@link MediaSource}s from the playlist, by specifying an initial index
   * (included) and a final index (excluded), and executes a custom action on completion.
   *
   * <p>Note: when specified range is empty, no actual media source is removed and no exception is
   * thrown.
   *
   * @param fromIndex The initial range index, pointing to the first media source that will be
   *     removed. This index must be in the range of 0 &lt;= index &lt;= {@link #getSize()}.
   * @param toIndex The final range index, pointing to the first media source that will be left
   *     untouched. This index must be in the range of 0 &lt;= index &lt;= {@link #getSize()}.
   * @param handler The {@link Handler} to run {@code onCompletionAction}.
   * @param onCompletionAction A {@link Runnable} which is executed immediately after the media
   *     source range has been removed from the playlist.
   * @throws IllegalArgumentException When the range is malformed, i.e. {@code fromIndex} &lt; 0,
   *     {@code toIndex} &gt; {@link #getSize()}, {@code fromIndex} &gt; {@code toIndex}
   */
  public synchronized void removeMediaSourceRange(
      int fromIndex, int toIndex, Handler handler, Runnable onCompletionAction) {
    removePublicMediaSources(fromIndex, toIndex, handler, onCompletionAction);
  }

  /**
   * Moves an existing {@link MediaSource} within the playlist.
   *
   * @param currentIndex The current index of the media source in the playlist. This index must be
   *     in the range of 0 &lt;= index &lt; {@link #getSize()}.
   * @param newIndex The target index of the media source in the playlist. This index must be in the
   *     range of 0 &lt;= index &lt; {@link #getSize()}.
   */
  public synchronized void moveMediaSource(int currentIndex, int newIndex) {
    movePublicMediaSource(
        currentIndex, newIndex, /* handler= */ null, /* onCompletionAction= */ null);
  }

  /**
   * Moves an existing {@link MediaSource} within the playlist and executes a custom action on
   * completion.
   *
   * @param currentIndex The current index of the media source in the playlist. This index must be
   *     in the range of 0 &lt;= index &lt; {@link #getSize()}.
   * @param newIndex The target index of the media source in the playlist. This index must be in the
   *     range of 0 &lt;= index &lt; {@link #getSize()}.
   * @param handler The {@link Handler} to run {@code onCompletionAction}.
   * @param onCompletionAction A {@link Runnable} which is executed immediately after the media
   *     source has been moved.
   */
  public synchronized void moveMediaSource(
      int currentIndex, int newIndex, Handler handler, Runnable onCompletionAction) {
    movePublicMediaSource(currentIndex, newIndex, handler, onCompletionAction);
  }

  /** Clears the playlist. */
  public synchronized void clear() {
    removeMediaSourceRange(0, getSize());
  }

  /**
   * Clears the playlist and executes a custom action on completion.
   *
   * @param handler The {@link Handler} to run {@code onCompletionAction}.
   * @param onCompletionAction A {@link Runnable} which is executed immediately after the playlist
   *     has been cleared.
   */
  public synchronized void clear(Handler handler, Runnable onCompletionAction) {
    removeMediaSourceRange(0, getSize(), handler, onCompletionAction);
  }

  /** Returns the number of media sources in the playlist. */
  public synchronized int getSize() {
    return mediaSourcesPublic.size();
  }

  /**
   * Returns the {@link MediaSource} at a specified index.
   *
   * @param index An index in the range of 0 &lt;= index &lt;= {@link #getSize()}.
   * @return The {@link MediaSource} at this index.
   */
  public synchronized MediaSource getMediaSource(int index) {
    return mediaSourcesPublic.get(index).mediaSource;
  }

  /**
   * Sets a new shuffle order to use when shuffling the child media sources.
   *
   * @param shuffleOrder A {@link ShuffleOrder}.
   */
  public synchronized void setShuffleOrder(ShuffleOrder shuffleOrder) {
    setPublicShuffleOrder(shuffleOrder, /* handler= */ null, /* onCompletionAction= */ null);
  }

  /**
   * Sets a new shuffle order to use when shuffling the child media sources.
   *
   * @param shuffleOrder A {@link ShuffleOrder}.
   * @param handler The {@link Handler} to run {@code onCompletionAction}.
   * @param onCompletionAction A {@link Runnable} which is executed immediately after the shuffle
   *     order has been changed.
   */
  public synchronized void setShuffleOrder(
      ShuffleOrder shuffleOrder, Handler handler, Runnable onCompletionAction) {
    setPublicShuffleOrder(shuffleOrder, handler, onCompletionAction);
  }

  // CompositeMediaSource implementation.

  @Override
  @Nullable
  public Object getTag() {
    return null;
  }

  @Override
  protected synchronized void prepareSourceInternal(
      @Nullable TransferListener mediaTransferListener) {
    super.prepareSourceInternal(mediaTransferListener);
    playbackThreadHandler = new Handler(/* callback= */ this::handleMessage);
    if (mediaSourcesPublic.isEmpty()) {
      updateTimelineAndScheduleOnCompletionActions();
    } else {
      shuffleOrder = shuffleOrder.cloneAndInsert(0, mediaSourcesPublic.size());
      addMediaSourcesInternal(0, mediaSourcesPublic);
      scheduleTimelineUpdate();
    }
  }

  @SuppressWarnings("MissingSuperCall")
  @Override
  protected void enableInternal() {
    // Suppress enabling all child sources here as they can be lazily enabled when creating periods.
  }

  @Override
  public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator, long startPositionUs) {
    Object mediaSourceHolderUid = getMediaSourceHolderUid(id.periodUid);
    MediaPeriodId childMediaPeriodId = id.copyWithPeriodUid(getChildPeriodUid(id.periodUid));
    MediaSourceHolder holder = mediaSourceByUid.get(mediaSourceHolderUid);
    if (holder == null) {
      // Stale event. The media source has already been removed.
      holder = new MediaSourceHolder(new DummyMediaSource(), useLazyPreparation);
      holder.isRemoved = true;
      prepareChildSource(holder, holder.mediaSource);
    }
    enableMediaSource(holder);
    holder.activeMediaPeriodIds.add(childMediaPeriodId);
    MediaPeriod mediaPeriod =
        holder.mediaSource.createPeriod(childMediaPeriodId, allocator, startPositionUs);
    mediaSourceByMediaPeriod.put(mediaPeriod, holder);
    disableUnusedMediaSources();
    return mediaPeriod;
  }

  @Override
  public void releasePeriod(MediaPeriod mediaPeriod) {
    MediaSourceHolder holder =
        Assertions.checkNotNull(mediaSourceByMediaPeriod.remove(mediaPeriod));
    holder.mediaSource.releasePeriod(mediaPeriod);
    holder.activeMediaPeriodIds.remove(((MaskingMediaPeriod) mediaPeriod).id);
    if (!mediaSourceByMediaPeriod.isEmpty()) {
      disableUnusedMediaSources();
    }
    maybeReleaseChildSource(holder);
  }

  @Override
  protected void disableInternal() {
    super.disableInternal();
    enabledMediaSourceHolders.clear();
  }

  @Override
  protected synchronized void releaseSourceInternal() {
    super.releaseSourceInternal();
    mediaSourceHolders.clear();
    enabledMediaSourceHolders.clear();
    mediaSourceByUid.clear();
    shuffleOrder = shuffleOrder.cloneAndClear();
    if (playbackThreadHandler != null) {
      playbackThreadHandler.removeCallbacksAndMessages(null);
      playbackThreadHandler = null;
    }
    timelineUpdateScheduled = false;
    nextTimelineUpdateOnCompletionActions.clear();
    dispatchOnCompletionActions(pendingOnCompletionActions);
  }

  @Override
  protected void onChildSourceInfoRefreshed(
      MediaSourceHolder mediaSourceHolder, MediaSource mediaSource, Timeline timeline) {
    updateMediaSourceInternal(mediaSourceHolder, timeline);
  }

  @Override
  @Nullable
  protected MediaPeriodId getMediaPeriodIdForChildMediaPeriodId(
      MediaSourceHolder mediaSourceHolder, MediaPeriodId mediaPeriodId) {
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

  @Override
  protected int getWindowIndexForChildWindowIndex(
      MediaSourceHolder mediaSourceHolder, int windowIndex) {
    return windowIndex + mediaSourceHolder.firstWindowIndexInChild;
  }

  // Internal methods. Called from any thread.

  @GuardedBy("this")
  private void addPublicMediaSources(
      int index,
      Collection<MediaSource> mediaSources,
      @Nullable Handler handler,
      @Nullable Runnable onCompletionAction) {
    Assertions.checkArgument((handler == null) == (onCompletionAction == null));
    Handler playbackThreadHandler = this.playbackThreadHandler;
    for (MediaSource mediaSource : mediaSources) {
      Assertions.checkNotNull(mediaSource);
    }
    List<MediaSourceHolder> mediaSourceHolders = new ArrayList<>(mediaSources.size());
    for (MediaSource mediaSource : mediaSources) {
      mediaSourceHolders.add(new MediaSourceHolder(mediaSource, useLazyPreparation));
    }
    mediaSourcesPublic.addAll(index, mediaSourceHolders);
    if (playbackThreadHandler != null && !mediaSources.isEmpty()) {
      HandlerAndRunnable callbackAction = createOnCompletionAction(handler, onCompletionAction);
      playbackThreadHandler
          .obtainMessage(MSG_ADD, new MessageData<>(index, mediaSourceHolders, callbackAction))
          .sendToTarget();
    } else if (onCompletionAction != null && handler != null) {
      handler.post(onCompletionAction);
    }
  }

  @GuardedBy("this")
  private void removePublicMediaSources(
      int fromIndex,
      int toIndex,
      @Nullable Handler handler,
      @Nullable Runnable onCompletionAction) {
    Assertions.checkArgument((handler == null) == (onCompletionAction == null));
    Handler playbackThreadHandler = this.playbackThreadHandler;
    Util.removeRange(mediaSourcesPublic, fromIndex, toIndex);
    if (playbackThreadHandler != null) {
      HandlerAndRunnable callbackAction = createOnCompletionAction(handler, onCompletionAction);
      playbackThreadHandler
          .obtainMessage(MSG_REMOVE, new MessageData<>(fromIndex, toIndex, callbackAction))
          .sendToTarget();
    } else if (onCompletionAction != null && handler != null) {
      handler.post(onCompletionAction);
    }
  }

  @GuardedBy("this")
  private void movePublicMediaSource(
      int currentIndex,
      int newIndex,
      @Nullable Handler handler,
      @Nullable Runnable onCompletionAction) {
    Assertions.checkArgument((handler == null) == (onCompletionAction == null));
    Handler playbackThreadHandler = this.playbackThreadHandler;
    mediaSourcesPublic.add(newIndex, mediaSourcesPublic.remove(currentIndex));
    if (playbackThreadHandler != null) {
      HandlerAndRunnable callbackAction = createOnCompletionAction(handler, onCompletionAction);
      playbackThreadHandler
          .obtainMessage(MSG_MOVE, new MessageData<>(currentIndex, newIndex, callbackAction))
          .sendToTarget();
    } else if (onCompletionAction != null && handler != null) {
      handler.post(onCompletionAction);
    }
  }

  @GuardedBy("this")
  private void setPublicShuffleOrder(
      ShuffleOrder shuffleOrder, @Nullable Handler handler, @Nullable Runnable onCompletionAction) {
    Assertions.checkArgument((handler == null) == (onCompletionAction == null));
    Handler playbackThreadHandler = this.playbackThreadHandler;
    if (playbackThreadHandler != null) {
      int size = getSize();
      if (shuffleOrder.getLength() != size) {
        shuffleOrder =
            shuffleOrder
                .cloneAndClear()
                .cloneAndInsert(/* insertionIndex= */ 0, /* insertionCount= */ size);
      }
      HandlerAndRunnable callbackAction = createOnCompletionAction(handler, onCompletionAction);
      playbackThreadHandler
          .obtainMessage(
              MSG_SET_SHUFFLE_ORDER,
              new MessageData<>(/* index= */ 0, shuffleOrder, callbackAction))
          .sendToTarget();
    } else {
      this.shuffleOrder =
          shuffleOrder.getLength() > 0 ? shuffleOrder.cloneAndClear() : shuffleOrder;
      if (onCompletionAction != null && handler != null) {
        handler.post(onCompletionAction);
      }
    }
  }

  @GuardedBy("this")
  @Nullable
  private HandlerAndRunnable createOnCompletionAction(
      @Nullable Handler handler, @Nullable Runnable runnable) {
    if (handler == null || runnable == null) {
      return null;
    }
    HandlerAndRunnable handlerAndRunnable = new HandlerAndRunnable(handler, runnable);
    pendingOnCompletionActions.add(handlerAndRunnable);
    return handlerAndRunnable;
  }

  // Internal methods. Called on the playback thread.

  @SuppressWarnings("unchecked")
  private boolean handleMessage(Message msg) {
    switch (msg.what) {
      case MSG_ADD:
        MessageData<Collection<MediaSourceHolder>> addMessage =
            (MessageData<Collection<MediaSourceHolder>>) Util.castNonNull(msg.obj);
        shuffleOrder = shuffleOrder.cloneAndInsert(addMessage.index, addMessage.customData.size());
        addMediaSourcesInternal(addMessage.index, addMessage.customData);
        scheduleTimelineUpdate(addMessage.onCompletionAction);
        break;
      case MSG_REMOVE:
        MessageData<Integer> removeMessage = (MessageData<Integer>) Util.castNonNull(msg.obj);
        int fromIndex = removeMessage.index;
        int toIndex = removeMessage.customData;
        if (fromIndex == 0 && toIndex == shuffleOrder.getLength()) {
          shuffleOrder = shuffleOrder.cloneAndClear();
        } else {
          shuffleOrder = shuffleOrder.cloneAndRemove(fromIndex, toIndex);
        }
        for (int index = toIndex - 1; index >= fromIndex; index--) {
          removeMediaSourceInternal(index);
        }
        scheduleTimelineUpdate(removeMessage.onCompletionAction);
        break;
      case MSG_MOVE:
        MessageData<Integer> moveMessage = (MessageData<Integer>) Util.castNonNull(msg.obj);
        shuffleOrder = shuffleOrder.cloneAndRemove(moveMessage.index, moveMessage.index + 1);
        shuffleOrder = shuffleOrder.cloneAndInsert(moveMessage.customData, 1);
        moveMediaSourceInternal(moveMessage.index, moveMessage.customData);
        scheduleTimelineUpdate(moveMessage.onCompletionAction);
        break;
      case MSG_SET_SHUFFLE_ORDER:
        MessageData<ShuffleOrder> shuffleOrderMessage =
            (MessageData<ShuffleOrder>) Util.castNonNull(msg.obj);
        shuffleOrder = shuffleOrderMessage.customData;
        scheduleTimelineUpdate(shuffleOrderMessage.onCompletionAction);
        break;
      case MSG_UPDATE_TIMELINE:
        updateTimelineAndScheduleOnCompletionActions();
        break;
      case MSG_ON_COMPLETION:
        Set<HandlerAndRunnable> actions = (Set<HandlerAndRunnable>) Util.castNonNull(msg.obj);
        dispatchOnCompletionActions(actions);
        break;
      default:
        throw new IllegalStateException();
    }
    return true;
  }

  private void scheduleTimelineUpdate() {
    scheduleTimelineUpdate(/* onCompletionAction= */ null);
  }

  private void scheduleTimelineUpdate(@Nullable HandlerAndRunnable onCompletionAction) {
    if (!timelineUpdateScheduled) {
      getPlaybackThreadHandlerOnPlaybackThread().obtainMessage(MSG_UPDATE_TIMELINE).sendToTarget();
      timelineUpdateScheduled = true;
    }
    if (onCompletionAction != null) {
      nextTimelineUpdateOnCompletionActions.add(onCompletionAction);
    }
  }

  private void updateTimelineAndScheduleOnCompletionActions() {
    timelineUpdateScheduled = false;
    Set<HandlerAndRunnable> onCompletionActions = nextTimelineUpdateOnCompletionActions;
    nextTimelineUpdateOnCompletionActions = new HashSet<>();
    refreshSourceInfo(new ConcatenatedTimeline(mediaSourceHolders, shuffleOrder, isAtomic));
    getPlaybackThreadHandlerOnPlaybackThread()
        .obtainMessage(MSG_ON_COMPLETION, onCompletionActions)
        .sendToTarget();
  }

  @SuppressWarnings("GuardedBy")
  private Handler getPlaybackThreadHandlerOnPlaybackThread() {
    // Write access to this value happens on the playback thread only, so playback thread reads
    // don't need to be synchronized.
    return Assertions.checkNotNull(playbackThreadHandler);
  }

  private synchronized void dispatchOnCompletionActions(
      Set<HandlerAndRunnable> onCompletionActions) {
    for (HandlerAndRunnable pendingAction : onCompletionActions) {
      pendingAction.dispatch();
    }
    pendingOnCompletionActions.removeAll(onCompletionActions);
  }

  private void addMediaSourcesInternal(
      int index, Collection<MediaSourceHolder> mediaSourceHolders) {
    for (MediaSourceHolder mediaSourceHolder : mediaSourceHolders) {
      addMediaSourceInternal(index++, mediaSourceHolder);
    }
  }

  private void addMediaSourceInternal(int newIndex, MediaSourceHolder newMediaSourceHolder) {
    if (newIndex > 0) {
      MediaSourceHolder previousHolder = mediaSourceHolders.get(newIndex - 1);
      Timeline previousTimeline = previousHolder.mediaSource.getTimeline();
      newMediaSourceHolder.reset(
          newIndex, previousHolder.firstWindowIndexInChild + previousTimeline.getWindowCount());
    } else {
      newMediaSourceHolder.reset(newIndex, /* firstWindowIndexInChild= */ 0);
    }
    Timeline newTimeline = newMediaSourceHolder.mediaSource.getTimeline();
    correctOffsets(newIndex, /* childIndexUpdate= */ 1, newTimeline.getWindowCount());
    mediaSourceHolders.add(newIndex, newMediaSourceHolder);
    mediaSourceByUid.put(newMediaSourceHolder.uid, newMediaSourceHolder);
    prepareChildSource(newMediaSourceHolder, newMediaSourceHolder.mediaSource);
    if (isEnabled() && mediaSourceByMediaPeriod.isEmpty()) {
      enabledMediaSourceHolders.add(newMediaSourceHolder);
    } else {
      disableChildSource(newMediaSourceHolder);
    }
  }

  private void updateMediaSourceInternal(MediaSourceHolder mediaSourceHolder, Timeline timeline) {
    if (mediaSourceHolder == null) {
      throw new IllegalArgumentException();
    }
    if (mediaSourceHolder.childIndex + 1 < mediaSourceHolders.size()) {
      MediaSourceHolder nextHolder = mediaSourceHolders.get(mediaSourceHolder.childIndex + 1);
      int windowOffsetUpdate =
          timeline.getWindowCount()
              - (nextHolder.firstWindowIndexInChild - mediaSourceHolder.firstWindowIndexInChild);
      if (windowOffsetUpdate != 0) {
        correctOffsets(
            mediaSourceHolder.childIndex + 1, /* childIndexUpdate= */ 0, windowOffsetUpdate);
      }
    }
    scheduleTimelineUpdate();
  }

  private void removeMediaSourceInternal(int index) {
    MediaSourceHolder holder = mediaSourceHolders.remove(index);
    mediaSourceByUid.remove(holder.uid);
    Timeline oldTimeline = holder.mediaSource.getTimeline();
    correctOffsets(index, /* childIndexUpdate= */ -1, -oldTimeline.getWindowCount());
    holder.isRemoved = true;
    maybeReleaseChildSource(holder);
  }

  private void moveMediaSourceInternal(int currentIndex, int newIndex) {
    int startIndex = Math.min(currentIndex, newIndex);
    int endIndex = Math.max(currentIndex, newIndex);
    int windowOffset = mediaSourceHolders.get(startIndex).firstWindowIndexInChild;
    mediaSourceHolders.add(newIndex, mediaSourceHolders.remove(currentIndex));
    for (int i = startIndex; i <= endIndex; i++) {
      MediaSourceHolder holder = mediaSourceHolders.get(i);
      holder.childIndex = i;
      holder.firstWindowIndexInChild = windowOffset;
      windowOffset += holder.mediaSource.getTimeline().getWindowCount();
    }
  }

  private void correctOffsets(int startIndex, int childIndexUpdate, int windowOffsetUpdate) {
    // TODO: Replace window index with uid in reporting to get rid of this inefficient method and
    // the childIndex and firstWindowIndexInChild variables.
    for (int i = startIndex; i < mediaSourceHolders.size(); i++) {
      MediaSourceHolder holder = mediaSourceHolders.get(i);
      holder.childIndex += childIndexUpdate;
      holder.firstWindowIndexInChild += windowOffsetUpdate;
    }
  }

  private void maybeReleaseChildSource(MediaSourceHolder mediaSourceHolder) {
    // Release if the source has been removed from the playlist and no periods are still active.
    if (mediaSourceHolder.isRemoved && mediaSourceHolder.activeMediaPeriodIds.isEmpty()) {
      enabledMediaSourceHolders.remove(mediaSourceHolder);
      releaseChildSource(mediaSourceHolder);
    }
  }

  private void enableMediaSource(MediaSourceHolder mediaSourceHolder) {
    enabledMediaSourceHolders.add(mediaSourceHolder);
    enableChildSource(mediaSourceHolder);
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

  /** Return uid of media source holder from period uid of concatenated source. */
  private static Object getMediaSourceHolderUid(Object periodUid) {
    return ConcatenatedTimeline.getChildTimelineUidFromConcatenatedUid(periodUid);
  }

  /** Return uid of child period from period uid of concatenated source. */
  private static Object getChildPeriodUid(Object periodUid) {
    return ConcatenatedTimeline.getChildPeriodUidFromConcatenatedUid(periodUid);
  }

  private static Object getPeriodUid(MediaSourceHolder holder, Object childPeriodUid) {
    return ConcatenatedTimeline.getConcatenatedUid(holder.uid, childPeriodUid);
  }

  /** Data class to hold playlist media sources together with meta data needed to process them. */
  /* package */ static final class MediaSourceHolder {

    public final MaskingMediaSource mediaSource;
    public final Object uid;
    public final List<MediaPeriodId> activeMediaPeriodIds;

    public int childIndex;
    public int firstWindowIndexInChild;
    public boolean isRemoved;

    public MediaSourceHolder(MediaSource mediaSource, boolean useLazyPreparation) {
      this.mediaSource = new MaskingMediaSource(mediaSource, useLazyPreparation);
      this.activeMediaPeriodIds = new ArrayList<>();
      this.uid = new Object();
    }

    public void reset(int childIndex, int firstWindowIndexInChild) {
      this.childIndex = childIndex;
      this.firstWindowIndexInChild = firstWindowIndexInChild;
      this.isRemoved = false;
      this.activeMediaPeriodIds.clear();
    }
  }

  /** Message used to post actions from app thread to playback thread. */
  private static final class MessageData<T> {

    public final int index;
    public final T customData;
    @Nullable public final HandlerAndRunnable onCompletionAction;

    public MessageData(int index, T customData, @Nullable HandlerAndRunnable onCompletionAction) {
      this.index = index;
      this.customData = customData;
      this.onCompletionAction = onCompletionAction;
    }
  }

  /** Timeline exposing concatenated timelines of playlist media sources. */
  private static final class ConcatenatedTimeline extends AbstractConcatenatedTimeline {

    private final int windowCount;
    private final int periodCount;
    private final int[] firstPeriodInChildIndices;
    private final int[] firstWindowInChildIndices;
    private final Timeline[] timelines;
    private final Object[] uids;
    private final HashMap<Object, Integer> childIndexByUid;

    public ConcatenatedTimeline(
        Collection<MediaSourceHolder> mediaSourceHolders,
        ShuffleOrder shuffleOrder,
        boolean isAtomic) {
      super(isAtomic, shuffleOrder);
      int childCount = mediaSourceHolders.size();
      firstPeriodInChildIndices = new int[childCount];
      firstWindowInChildIndices = new int[childCount];
      timelines = new Timeline[childCount];
      uids = new Object[childCount];
      childIndexByUid = new HashMap<>();
      int index = 0;
      int windowCount = 0;
      int periodCount = 0;
      for (MediaSourceHolder mediaSourceHolder : mediaSourceHolders) {
        timelines[index] = mediaSourceHolder.mediaSource.getTimeline();
        firstWindowInChildIndices[index] = windowCount;
        firstPeriodInChildIndices[index] = periodCount;
        windowCount += timelines[index].getWindowCount();
        periodCount += timelines[index].getPeriodCount();
        uids[index] = mediaSourceHolder.uid;
        childIndexByUid.put(uids[index], index++);
      }
      this.windowCount = windowCount;
      this.periodCount = periodCount;
    }

    @Override
    protected int getChildIndexByPeriodIndex(int periodIndex) {
      return Util.binarySearchFloor(firstPeriodInChildIndices, periodIndex + 1, false, false);
    }

    @Override
    protected int getChildIndexByWindowIndex(int windowIndex) {
      return Util.binarySearchFloor(firstWindowInChildIndices, windowIndex + 1, false, false);
    }

    @Override
    protected int getChildIndexByChildUid(Object childUid) {
      Integer index = childIndexByUid.get(childUid);
      return index == null ? C.INDEX_UNSET : index;
    }

    @Override
    protected Timeline getTimelineByChildIndex(int childIndex) {
      return timelines[childIndex];
    }

    @Override
    protected int getFirstPeriodIndexByChildIndex(int childIndex) {
      return firstPeriodInChildIndices[childIndex];
    }

    @Override
    protected int getFirstWindowIndexByChildIndex(int childIndex) {
      return firstWindowInChildIndices[childIndex];
    }

    @Override
    protected Object getChildUidByChildIndex(int childIndex) {
      return uids[childIndex];
    }

    @Override
    public int getWindowCount() {
      return windowCount;
    }

    @Override
    public int getPeriodCount() {
      return periodCount;
    }
  }

  /** Dummy media source which does nothing and does not support creating periods. */
  private static final class DummyMediaSource extends BaseMediaSource {

    @Override
    protected void prepareSourceInternal(@Nullable TransferListener mediaTransferListener) {
      // Do nothing.
    }

    @Override
    @Nullable
    public Object getTag() {
      return null;
    }

    @Override
    protected void releaseSourceInternal() {
      // Do nothing.
    }

    @Override
    public void maybeThrowSourceInfoRefreshError() throws IOException {
      // Do nothing.
    }

    @Override
    public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator, long startPositionUs) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void releasePeriod(MediaPeriod mediaPeriod) {
      // Do nothing.
    }
  }

  private static final class HandlerAndRunnable {

    private final Handler handler;
    private final Runnable runnable;

    public HandlerAndRunnable(Handler handler, Runnable runnable) {
      this.handler = handler;
      this.runnable = runnable;
    }

    public void dispatch() {
      handler.post(runnable);
    }
  }
}

