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
package com.google.android.exoplayer2.upstream;

import androidx.annotation.Nullable;

/** A source of allocations. */
public interface Allocator {

  /** A node in a chain of {@link Allocation Allocations}. */
  interface AllocationNode {

    /** Returns the {@link Allocation} associated to this chain node. */
    Allocation getAllocation();

    /** Returns the next chain node, or {@code null} if this is the last node in the chain. */
    @Nullable
    AllocationNode next();
  }

  /**
   * Obtain an {@link Allocation}.
   *
   * <p>When the caller has finished with the {@link Allocation}, it should be returned by calling
   * {@link #release(Allocation)}.
   *
   * @return The {@link Allocation}.
   */
  Allocation allocate();

  /**
   * Releases an {@link Allocation} back to the allocator.
   *
   * @param allocation The {@link Allocation} being released.
   */
  void release(Allocation allocation);

  /**
   * Releases all {@link Allocation Allocations} in the chain starting at the given {@link
   * AllocationNode}.
   *
   * <p>Implementations must not make memory allocations.
   */
  void release(AllocationNode allocationNode);

  /**
   * Hints to the allocator that it should make a best effort to release any excess {@link
   * Allocation Allocations}.
   */
  void trim();

  /** Returns the total number of bytes currently allocated. */
  int getTotalBytesAllocated();

  /** Returns the length of each individual {@link Allocation}. */
  int getIndividualAllocationLength();
}
