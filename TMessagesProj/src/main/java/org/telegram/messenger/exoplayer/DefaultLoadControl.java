/*
 * Copyright (C) 2014 The Android Open Source Project
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
package org.telegram.messenger.exoplayer;

import android.os.Handler;
import org.telegram.messenger.exoplayer.upstream.Allocator;
import org.telegram.messenger.exoplayer.upstream.NetworkLock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * A {@link LoadControl} implementation that allows loads to continue in a sequence that prevents
 * any loader from getting too far ahead or behind any of the other loaders.
 * <p>
 * Loads are scheduled so as to fill the available buffer space as rapidly as possible. Once the
 * duration of buffered media and the buffer utilization both exceed respective thresholds, the
 * control switches to a draining state during which no loads are permitted to start. During
 * draining periods, resources such as the device radio have an opportunity to switch into low
 * power modes. The control reverts back to the loading state when either the duration of buffered
 * media or the buffer utilization fall below respective thresholds.
 * <p>
 * This implementation of {@link LoadControl} integrates with {@link NetworkLock}, by registering
 * itself as a task with priority {@link NetworkLock#STREAMING_PRIORITY} during loading periods,
 * and unregistering itself during draining periods.
 */
public final class DefaultLoadControl implements LoadControl {

  /**
   * Interface definition for a callback to be notified of {@link DefaultLoadControl} events.
   */
  public interface EventListener {

    /**
     * Invoked when the control transitions from a loading to a draining state, or vice versa.
     *
     * @param loading Whether the control is now in a loading state.
     */
    void onLoadingChanged(boolean loading);

  }

  public static final int DEFAULT_LOW_WATERMARK_MS = 15000;
  public static final int DEFAULT_HIGH_WATERMARK_MS = 30000;
  public static final float DEFAULT_LOW_BUFFER_LOAD = 0.2f;
  public static final float DEFAULT_HIGH_BUFFER_LOAD = 0.8f;

  private static final int ABOVE_HIGH_WATERMARK = 0;
  private static final int BETWEEN_WATERMARKS = 1;
  private static final int BELOW_LOW_WATERMARK = 2;

  private final Allocator allocator;
  private final List<Object> loaders;
  private final HashMap<Object, LoaderState> loaderStates;
  private final Handler eventHandler;
  private final EventListener eventListener;

  private final long lowWatermarkUs;
  private final long highWatermarkUs;
  private final float lowBufferLoad;
  private final float highBufferLoad;

  private int targetBufferSize;
  private long maxLoadStartPositionUs;
  private int bufferState;
  private boolean fillingBuffers;
  private boolean streamingPrioritySet;

  /**
   * Constructs a new instance, using the {@code DEFAULT_*} constants defined in this class.
   *
   * @param allocator The {@link Allocator} used by the loader.
   */
  public DefaultLoadControl(Allocator allocator) {
    this(allocator, null, null);
  }

  /**
   * Constructs a new instance, using the {@code DEFAULT_*} constants defined in this class.
   *
   * @param allocator The {@link Allocator} used by the loader.
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   */
  public DefaultLoadControl(Allocator allocator, Handler eventHandler,
      EventListener eventListener) {
    this(allocator, eventHandler, eventListener, DEFAULT_LOW_WATERMARK_MS,
        DEFAULT_HIGH_WATERMARK_MS, DEFAULT_LOW_BUFFER_LOAD, DEFAULT_HIGH_BUFFER_LOAD);
  }

  /**
   * Constructs a new instance.
   *
   * @param allocator The {@link Allocator} used by the loader.
   * @param eventHandler A handler to use when delivering events to {@code eventListener}. May be
   *     null if delivery of events is not required.
   * @param eventListener A listener of events. May be null if delivery of events is not required.
   * @param lowWatermarkMs The minimum duration of media that can be buffered for the control to
   *     be in the draining state. If less media is buffered, then the control will transition to
   *     the filling state.
   * @param highWatermarkMs The minimum duration of media that can be buffered for the control to
   *     transition from filling to draining.
   * @param lowBufferLoad The minimum fraction of the buffer that must be utilized for the control
   *     to be in the draining state. If the utilization is lower, then the control will transition
   *     to the filling state.
   * @param highBufferLoad The minimum fraction of the buffer that must be utilized for the control
   *     to transition from the loading state to the draining state.
   */
  public DefaultLoadControl(Allocator allocator, Handler eventHandler, EventListener eventListener,
      int lowWatermarkMs, int highWatermarkMs, float lowBufferLoad, float highBufferLoad) {
    this.allocator = allocator;
    this.eventHandler = eventHandler;
    this.eventListener = eventListener;
    this.loaders = new ArrayList<>();
    this.loaderStates = new HashMap<>();
    this.lowWatermarkUs = lowWatermarkMs * 1000L;
    this.highWatermarkUs = highWatermarkMs * 1000L;
    this.lowBufferLoad = lowBufferLoad;
    this.highBufferLoad = highBufferLoad;
  }

  @Override
  public void register(Object loader, int bufferSizeContribution) {
    loaders.add(loader);
    loaderStates.put(loader, new LoaderState(bufferSizeContribution));
    targetBufferSize += bufferSizeContribution;
  }

  @Override
  public void unregister(Object loader) {
    loaders.remove(loader);
    LoaderState state = loaderStates.remove(loader);
    targetBufferSize -= state.bufferSizeContribution;
    updateControlState();
  }

  @Override
  public void trimAllocator() {
    allocator.trim(targetBufferSize);
  }

  @Override
  public Allocator getAllocator() {
    return allocator;
  }

  @Override
  public boolean update(Object loader, long playbackPositionUs, long nextLoadPositionUs,
      boolean loading) {
    // Update the loader state.
    int loaderBufferState = getLoaderBufferState(playbackPositionUs, nextLoadPositionUs);
    LoaderState loaderState = loaderStates.get(loader);
    boolean loaderStateChanged = loaderState.bufferState != loaderBufferState
        || loaderState.nextLoadPositionUs != nextLoadPositionUs || loaderState.loading != loading;
    if (loaderStateChanged) {
      loaderState.bufferState = loaderBufferState;
      loaderState.nextLoadPositionUs = nextLoadPositionUs;
      loaderState.loading = loading;
    }

    // Update the buffer state.
    int currentBufferSize = allocator.getTotalBytesAllocated();
    int bufferState = getBufferState(currentBufferSize);
    boolean bufferStateChanged = this.bufferState != bufferState;
    if (bufferStateChanged) {
      this.bufferState = bufferState;
    }

    // If either of the individual states have changed, update the shared control state.
    if (loaderStateChanged || bufferStateChanged) {
      updateControlState();
    }

    return currentBufferSize < targetBufferSize && nextLoadPositionUs != -1
        && nextLoadPositionUs <= maxLoadStartPositionUs;
  }

  private int getLoaderBufferState(long playbackPositionUs, long nextLoadPositionUs) {
    if (nextLoadPositionUs == -1) {
      return ABOVE_HIGH_WATERMARK;
    } else {
      long timeUntilNextLoadPosition = nextLoadPositionUs - playbackPositionUs;
      return timeUntilNextLoadPosition > highWatermarkUs ? ABOVE_HIGH_WATERMARK :
          timeUntilNextLoadPosition < lowWatermarkUs ? BELOW_LOW_WATERMARK :
          BETWEEN_WATERMARKS;
    }
  }

  private int getBufferState(int currentBufferSize) {
    float bufferLoad = (float) currentBufferSize / targetBufferSize;
    return bufferLoad > highBufferLoad ? ABOVE_HIGH_WATERMARK
        : bufferLoad < lowBufferLoad ? BELOW_LOW_WATERMARK
        : BETWEEN_WATERMARKS;
  }

  private void updateControlState() {
    boolean loading = false;
    boolean haveNextLoadPosition = false;
    int highestState = bufferState;
    for (int i = 0; i < loaders.size(); i++) {
      LoaderState loaderState = loaderStates.get(loaders.get(i));
      loading |= loaderState.loading;
      haveNextLoadPosition |= loaderState.nextLoadPositionUs != -1;
      highestState = Math.max(highestState, loaderState.bufferState);
    }

    fillingBuffers = !loaders.isEmpty() && (loading || haveNextLoadPosition)
        && (highestState == BELOW_LOW_WATERMARK
        || (highestState == BETWEEN_WATERMARKS && fillingBuffers));
    if (fillingBuffers && !streamingPrioritySet) {
      NetworkLock.instance.add(NetworkLock.STREAMING_PRIORITY);
      streamingPrioritySet = true;
      notifyLoadingChanged(true);
    } else if (!fillingBuffers && streamingPrioritySet && !loading) {
      NetworkLock.instance.remove(NetworkLock.STREAMING_PRIORITY);
      streamingPrioritySet = false;
      notifyLoadingChanged(false);
    }

    maxLoadStartPositionUs = -1;
    if (fillingBuffers) {
      for (int i = 0; i < loaders.size(); i++) {
        Object loader = loaders.get(i);
        LoaderState loaderState = loaderStates.get(loader);
        long loaderTime = loaderState.nextLoadPositionUs;
        if (loaderTime != -1
            && (maxLoadStartPositionUs == -1 || loaderTime < maxLoadStartPositionUs)) {
          maxLoadStartPositionUs = loaderTime;
        }
      }
    }
  }

  private void notifyLoadingChanged(final boolean loading) {
    if (eventHandler != null && eventListener != null) {
      eventHandler.post(new Runnable()  {
        @Override
        public void run() {
          eventListener.onLoadingChanged(loading);
        }
      });
    }
  }

  private static class LoaderState {

    public final int bufferSizeContribution;

    public int bufferState;
    public boolean loading;
    public long nextLoadPositionUs;

    public LoaderState(int bufferSizeContribution) {
      this.bufferSizeContribution = bufferSizeContribution;
      bufferState = ABOVE_HIGH_WATERMARK;
      loading = false;
      nextLoadPositionUs = -1;
    }

  }

}
