/*
 * Copyright (C) 2018 The Android Open Source Project
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
package org.telegram.messenger.exoplayer2.source;

import android.os.Handler;
import android.support.annotation.Nullable;
import org.telegram.messenger.exoplayer2.ExoPlayer;
import org.telegram.messenger.exoplayer2.Timeline;
import org.telegram.messenger.exoplayer2.util.Assertions;
import java.util.ArrayList;

/**
 * Base {@link MediaSource} implementation to handle parallel reuse and to keep a list of {@link
 * MediaSourceEventListener}s.
 *
 * <p>Whenever an implementing subclass needs to provide a new timeline and/or manifest, it must
 * call {@link #refreshSourceInfo(Timeline, Object)} to notify all listeners.
 */
public abstract class BaseMediaSource implements MediaSource {

  private final ArrayList<SourceInfoRefreshListener> sourceInfoListeners;
  private final MediaSourceEventListener.EventDispatcher eventDispatcher;

  private ExoPlayer player;
  private Timeline timeline;
  private Object manifest;

  public BaseMediaSource() {
    sourceInfoListeners = new ArrayList<>(/* initialCapacity= */ 1);
    eventDispatcher = new MediaSourceEventListener.EventDispatcher();
  }

  /**
   * Starts source preparation. This method is called at most once until the next call to {@link
   * #releaseSourceInternal()}.
   *
   * @param player The player for which this source is being prepared.
   * @param isTopLevelSource Whether this source has been passed directly to {@link
   *     ExoPlayer#prepare(MediaSource)} or {@link ExoPlayer#prepare(MediaSource, boolean,
   *     boolean)}.
   */
  protected abstract void prepareSourceInternal(ExoPlayer player, boolean isTopLevelSource);

  /**
   * Releases the source. This method is called exactly once after each call to {@link
   * #prepareSourceInternal(ExoPlayer, boolean)}.
   */
  protected abstract void releaseSourceInternal();

  /**
   * Updates timeline and manifest and notifies all listeners of the update.
   *
   * @param timeline The new {@link Timeline}.
   * @param manifest The new manifest. May be null.
   */
  protected final void refreshSourceInfo(Timeline timeline, @Nullable Object manifest) {
    this.timeline = timeline;
    this.manifest = manifest;
    for (SourceInfoRefreshListener listener : sourceInfoListeners) {
      listener.onSourceInfoRefreshed(/* source= */ this, timeline, manifest);
    }
  }

  /**
   * Returns a {@link MediaSourceEventListener.EventDispatcher} which dispatches all events to the
   * registered listeners with the specified media period id.
   *
   * @param mediaPeriodId The {@link MediaPeriodId} to be reported with the events. May be null, if
   *     the events do not belong to a specific media period.
   * @return An event dispatcher with pre-configured media period id.
   */
  protected final MediaSourceEventListener.EventDispatcher createEventDispatcher(
      @Nullable MediaPeriodId mediaPeriodId) {
    return eventDispatcher.withParameters(
        /* windowIndex= */ 0, mediaPeriodId, /* mediaTimeOffsetMs= */ 0);
  }

  /**
   * Returns a {@link MediaSourceEventListener.EventDispatcher} which dispatches all events to the
   * registered listeners with the specified media period id and time offset.
   *
   * @param mediaPeriodId The {@link MediaPeriodId} to be reported with the events.
   * @param mediaTimeOffsetMs The offset to be added to all media times, in milliseconds.
   * @return An event dispatcher with pre-configured media period id and time offset.
   */
  protected final MediaSourceEventListener.EventDispatcher createEventDispatcher(
      MediaPeriodId mediaPeriodId, long mediaTimeOffsetMs) {
    Assertions.checkArgument(mediaPeriodId != null);
    return eventDispatcher.withParameters(/* windowIndex= */ 0, mediaPeriodId, mediaTimeOffsetMs);
  }

  /**
   * Returns a {@link MediaSourceEventListener.EventDispatcher} which dispatches all events to the
   * registered listeners with the specified window index, media period id and time offset.
   *
   * @param windowIndex The timeline window index to be reported with the events.
   * @param mediaPeriodId The {@link MediaPeriodId} to be reported with the events. May be null, if
   *     the events do not belong to a specific media period.
   * @param mediaTimeOffsetMs The offset to be added to all media times, in milliseconds.
   * @return An event dispatcher with pre-configured media period id and time offset.
   */
  protected final MediaSourceEventListener.EventDispatcher createEventDispatcher(
      int windowIndex, @Nullable MediaPeriodId mediaPeriodId, long mediaTimeOffsetMs) {
    return eventDispatcher.withParameters(windowIndex, mediaPeriodId, mediaTimeOffsetMs);
  }

  @Override
  public final void addEventListener(Handler handler, MediaSourceEventListener eventListener) {
    eventDispatcher.addEventListener(handler, eventListener);
  }

  @Override
  public final void removeEventListener(MediaSourceEventListener eventListener) {
    eventDispatcher.removeEventListener(eventListener);
  }

  @Override
  public final void prepareSource(
      ExoPlayer player, boolean isTopLevelSource, SourceInfoRefreshListener listener) {
    Assertions.checkArgument(this.player == null || this.player == player);
    sourceInfoListeners.add(listener);
    if (this.player == null) {
      this.player = player;
      prepareSourceInternal(player, isTopLevelSource);
    } else if (timeline != null) {
      listener.onSourceInfoRefreshed(/* source= */ this, timeline, manifest);
    }
  }

  @Override
  public final void releaseSource(SourceInfoRefreshListener listener) {
    sourceInfoListeners.remove(listener);
    if (sourceInfoListeners.isEmpty()) {
      player = null;
      timeline = null;
      manifest = null;
      releaseSourceInternal();
    }
  }
}
