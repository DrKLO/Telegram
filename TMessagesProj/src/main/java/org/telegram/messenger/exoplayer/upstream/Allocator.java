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
package org.telegram.messenger.exoplayer.upstream;

/**
 * A source of allocations.
 */
public interface Allocator {

  /**
   * Obtain an {@link Allocation}.
   * <p>
   * When the caller has finished with the {@link Allocation}, it should be returned by calling
   * {@link #release(Allocation)}.
   *
   * @return The {@link Allocation}.
   */
  Allocation allocate();

  /**
   * Return an {@link Allocation}.
   *
   * @param allocation The {@link Allocation} being returned.
   */
  void release(Allocation allocation);

  /**
   * Return an array of {@link Allocation}s.
   *
   * @param allocations The array of {@link Allocation}s being returned.
   */
  void release(Allocation[] allocations);

  /**
   * Hints to the {@link Allocator} that it should make a best effort to release any memory that it
   * has allocated, beyond the specified target number of bytes.
   *
   * @param targetSize The target size in bytes.
   */
  void trim(int targetSize);

  /**
   * Blocks execution until the number of bytes allocated is not greater than the limit, or the
   * thread is interrupted.
   *
   * @param limit The limit in bytes.
   * @throws InterruptedException If the thread is interrupted.
   */
  void blockWhileTotalBytesAllocatedExceeds(int limit) throws InterruptedException;

  /**
   * Returns the total number of bytes currently allocated.
   */
  int getTotalBytesAllocated();

  /**
   * Returns the length of each individual {@link Allocation}.
   */
  int getIndividualAllocationLength();

}
