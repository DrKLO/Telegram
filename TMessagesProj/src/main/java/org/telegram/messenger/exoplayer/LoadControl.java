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

import org.telegram.messenger.exoplayer.upstream.Allocator;

/**
 * Coordinates multiple loaders of time series data.
 */
public interface LoadControl {

  /**
   * Registers a loader.
   *
   * @param loader The loader being registered.
   * @param bufferSizeContribution For instances whose {@link Allocator} maintains a pool of memory
   *     for the purpose of satisfying allocation requests, this is a hint indicating the loader's
   *     desired contribution to the size of the pool, in bytes.
   */
  void register(Object loader, int bufferSizeContribution);

  /**
   * Unregisters a loader.
   *
   * @param loader The loader being unregistered.
   */
  void unregister(Object loader);

  /**
   * Gets the {@link Allocator} that loaders should use to obtain memory allocations into which
   * data can be loaded.
   *
   * @return The {@link Allocator} to use.
   */
  Allocator getAllocator();

  /**
   * Hints to the control that it should consider trimming any unused memory being held in order
   * to satisfy allocation requests.
   * <p>
   * This method is typically invoked by a recently unregistered loader, once it has released all
   * of its allocations back to the {@link Allocator}.
   */
  void trimAllocator();

  /**
   * Invoked by a loader to update the control with its current state.
   * <p>
   * This method must be called by a registered loader whenever its state changes. This is true
   * even if the registered loader does not itself wish to start its next load (since the state of
   * the loader will still affect whether other registered loaders are allowed to proceed).
   *
   * @param loader The loader invoking the update.
   * @param playbackPositionUs The loader's playback position.
   * @param nextLoadPositionUs The loader's next load position. -1 if finished, failed, or if the
   *     next load position is not yet known.
   * @param loading Whether the loader is currently loading data.
   * @return True if the loader is allowed to start its next load. False otherwise.
   */
  boolean update(Object loader, long playbackPositionUs, long nextLoadPositionUs, boolean loading);

}
