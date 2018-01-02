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
package org.telegram.messenger.exoplayer2;

import org.telegram.messenger.exoplayer2.source.TrackGroupArray;
import org.telegram.messenger.exoplayer2.trackselection.TrackSelectionArray;
import org.telegram.messenger.exoplayer2.upstream.Allocator;
import org.telegram.messenger.exoplayer2.upstream.DefaultAllocator;
import org.telegram.messenger.exoplayer2.util.PriorityTaskManager;
import org.telegram.messenger.exoplayer2.util.Util;

/**
 * The default {@link LoadControl} implementation.
 */
public final class DefaultLoadControl implements LoadControl {

  /**
   * The default minimum duration of media that the player will attempt to ensure is buffered at all
   * times, in milliseconds.
   */
  public static final int DEFAULT_MIN_BUFFER_MS = 15000;

  /**
   * The default maximum duration of media that the player will attempt to buffer, in milliseconds.
   */
  public static final int DEFAULT_MAX_BUFFER_MS = 30000;

  /**
   * The default duration of media that must be buffered for playback to start or resume following a
   * user action such as a seek, in milliseconds.
   */
  public static final int DEFAULT_BUFFER_FOR_PLAYBACK_MS = 2500;

  /**
   * The default duration of media that must be buffered for playback to resume after a rebuffer,
   * in milliseconds. A rebuffer is defined to be caused by buffer depletion rather than a user
   * action.
   */
  public static final int DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS  = 5000;

  private static final int ABOVE_HIGH_WATERMARK = 0;
  private static final int BETWEEN_WATERMARKS = 1;
  private static final int BELOW_LOW_WATERMARK = 2;

  private final DefaultAllocator allocator;

  private final long minBufferUs;
  private final long maxBufferUs;
  private final long bufferForPlaybackUs;
  private final long bufferForPlaybackAfterRebufferUs;
  private final PriorityTaskManager priorityTaskManager;

  private int targetBufferSize;
  private boolean isBuffering;

  /**
   * Constructs a new instance, using the {@code DEFAULT_*} constants defined in this class.
   */
  public DefaultLoadControl() {
    this(new DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE));
  }

  /**
   * Constructs a new instance, using the {@code DEFAULT_*} constants defined in this class.
   *
   * @param allocator The {@link DefaultAllocator} used by the loader.
   */
  public DefaultLoadControl(DefaultAllocator allocator) {
    this(allocator, DEFAULT_MIN_BUFFER_MS, DEFAULT_MAX_BUFFER_MS, DEFAULT_BUFFER_FOR_PLAYBACK_MS,
        DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS);
  }

  /**
   * Constructs a new instance.
   *
   * @param allocator The {@link DefaultAllocator} used by the loader.
   * @param minBufferMs The minimum duration of media that the player will attempt to ensure is
   *     buffered at all times, in milliseconds.
   * @param maxBufferMs The maximum duration of media that the player will attempt buffer, in
   *     milliseconds.
   * @param bufferForPlaybackMs The duration of media that must be buffered for playback to start or
   *     resume following a user action such as a seek, in milliseconds.
   * @param bufferForPlaybackAfterRebufferMs The default duration of media that must be buffered for
   *     playback to resume after a rebuffer, in milliseconds. A rebuffer is defined to be caused by
   *     buffer depletion rather than a user action.
   */
  public DefaultLoadControl(DefaultAllocator allocator, int minBufferMs, int maxBufferMs,
      long bufferForPlaybackMs, long bufferForPlaybackAfterRebufferMs) {
    this(allocator, minBufferMs, maxBufferMs, bufferForPlaybackMs, bufferForPlaybackAfterRebufferMs,
        null);
  }

  /**
   * Constructs a new instance.
   *
   * @param allocator The {@link DefaultAllocator} used by the loader.
   * @param minBufferMs The minimum duration of media that the player will attempt to ensure is
   *     buffered at all times, in milliseconds.
   * @param maxBufferMs The maximum duration of media that the player will attempt buffer, in
   *     milliseconds.
   * @param bufferForPlaybackMs The duration of media that must be buffered for playback to start or
   *     resume following a user action such as a seek, in milliseconds.
   * @param bufferForPlaybackAfterRebufferMs The default duration of media that must be buffered for
   *     playback to resume after a rebuffer, in milliseconds. A rebuffer is defined to be caused by
   *     buffer depletion rather than a user action.
   * @param priorityTaskManager If not null, registers itself as a task with priority
   *     {@link C#PRIORITY_PLAYBACK} during loading periods, and unregisters itself during draining
   *     periods.
   */
  public DefaultLoadControl(DefaultAllocator allocator, int minBufferMs, int maxBufferMs,
      long bufferForPlaybackMs, long bufferForPlaybackAfterRebufferMs,
      PriorityTaskManager priorityTaskManager) {
    this.allocator = allocator;
    minBufferUs = minBufferMs * 1000L;
    maxBufferUs = maxBufferMs * 1000L;
    bufferForPlaybackUs = bufferForPlaybackMs * 1000L;
    bufferForPlaybackAfterRebufferUs = bufferForPlaybackAfterRebufferMs * 1000L;
    this.priorityTaskManager = priorityTaskManager;
  }

  @Override
  public void onPrepared() {
    reset(false);
  }

  @Override
  public void onTracksSelected(Renderer[] renderers, TrackGroupArray trackGroups,
      TrackSelectionArray trackSelections) {
    targetBufferSize = 0;
    for (int i = 0; i < renderers.length; i++) {
      if (trackSelections.get(i) != null) {
        targetBufferSize += Util.getDefaultBufferSize(renderers[i].getTrackType());
      }
    }
    allocator.setTargetBufferSize(targetBufferSize);
  }

  @Override
  public void onStopped() {
    reset(true);
  }

  @Override
  public void onReleased() {
    reset(true);
  }

  @Override
  public Allocator getAllocator() {
    return allocator;
  }

  @Override
  public boolean shouldStartPlayback(long bufferedDurationUs, boolean rebuffering) {
    long minBufferDurationUs = rebuffering ? bufferForPlaybackAfterRebufferUs : bufferForPlaybackUs;
    return minBufferDurationUs <= 0 || bufferedDurationUs >= minBufferDurationUs;
  }

  @Override
  public boolean shouldContinueLoading(long bufferedDurationUs) {
    int bufferTimeState = getBufferTimeState(bufferedDurationUs);
    boolean targetBufferSizeReached = allocator.getTotalBytesAllocated() >= targetBufferSize;
    boolean wasBuffering = isBuffering;
    isBuffering = bufferTimeState == BELOW_LOW_WATERMARK
        || (bufferTimeState == BETWEEN_WATERMARKS && isBuffering && !targetBufferSizeReached);
    if (priorityTaskManager != null && isBuffering != wasBuffering) {
      if (isBuffering) {
        priorityTaskManager.add(C.PRIORITY_PLAYBACK);
      } else {
        priorityTaskManager.remove(C.PRIORITY_PLAYBACK);
      }
    }
    return isBuffering;
  }

  private int getBufferTimeState(long bufferedDurationUs) {
    return bufferedDurationUs > maxBufferUs ? ABOVE_HIGH_WATERMARK
        : (bufferedDurationUs < minBufferUs ? BELOW_LOW_WATERMARK : BETWEEN_WATERMARKS);
  }

  private void reset(boolean resetAllocator) {
    targetBufferSize = 0;
    if (priorityTaskManager != null && isBuffering) {
      priorityTaskManager.remove(C.PRIORITY_PLAYBACK);
    }
    isBuffering = false;
    if (resetAllocator) {
      allocator.reset();
    }
  }

}
