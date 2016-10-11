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

import org.telegram.messenger.exoplayer.util.Assertions;
import org.telegram.messenger.exoplayer.util.Util;
import java.util.Arrays;

/**
 * Default implementation of {@link Allocator}.
 */
public final class DefaultAllocator implements Allocator {

  private static final int AVAILABLE_EXTRA_CAPACITY = 100;

  private final int individualAllocationSize;
  private final byte[] initialAllocationBlock;

  private int allocatedCount;
  private int availableCount;
  private Allocation[] availableAllocations;

  /**
   * Constructs an initially empty pool.
   *
   * @param individualAllocationSize The length of each individual allocation.
   */
  public DefaultAllocator(int individualAllocationSize) {
    this(individualAllocationSize, 0);
  }

  /**
   * Constructs a pool with some {@link Allocation}s created up front.
   * <p>
   * Note: Initial {@link Allocation}s will never be discarded by {@link #trim(int)}.
   *
   * @param individualAllocationSize The length of each individual allocation.
   * @param initialAllocationCount The number of allocations to create up front.
   */
  public DefaultAllocator(int individualAllocationSize, int initialAllocationCount) {
    Assertions.checkArgument(individualAllocationSize > 0);
    Assertions.checkArgument(initialAllocationCount >= 0);
    this.individualAllocationSize = individualAllocationSize;
    this.availableCount = initialAllocationCount;
    this.availableAllocations = new Allocation[initialAllocationCount + AVAILABLE_EXTRA_CAPACITY];
    if (initialAllocationCount > 0) {
      initialAllocationBlock = new byte[initialAllocationCount * individualAllocationSize];
      for (int i = 0; i < initialAllocationCount; i++) {
        int allocationOffset = i * individualAllocationSize;
        availableAllocations[i] = new Allocation(initialAllocationBlock, allocationOffset);
      }
    } else {
      initialAllocationBlock = null;
    }
  }

  @Override
  public synchronized Allocation allocate() {
    allocatedCount++;
    Allocation allocation;
    if (availableCount > 0) {
      allocation = availableAllocations[--availableCount];
      availableAllocations[availableCount] = null;
    } else {
      allocation = new Allocation(new byte[individualAllocationSize], 0);
    }
    return allocation;
  }

  @Override
  public synchronized void release(Allocation allocation) {
    // Weak sanity check that the allocation probably originated from this pool.
    Assertions.checkArgument(allocation.data == initialAllocationBlock
        || allocation.data.length == individualAllocationSize);
    allocatedCount--;
    if (availableCount == availableAllocations.length) {
      availableAllocations = Arrays.copyOf(availableAllocations, availableAllocations.length * 2);
    }
    availableAllocations[availableCount++] = allocation;
    // Wake up threads waiting for the allocated size to drop.
    notifyAll();
  }

  @Override
  public synchronized void release(Allocation[] allocations) {
    if (availableCount + allocations.length >= availableAllocations.length) {
      availableAllocations = Arrays.copyOf(
          availableAllocations, Math.max(
              availableAllocations.length * 2,
              availableCount + allocations.length));
    }
    for (Allocation allocation : allocations) {
      // Weak sanity check that the allocation probably originated from this pool.
      Assertions.checkArgument(allocation.data == initialAllocationBlock
          || allocation.data.length == individualAllocationSize);
      availableAllocations[availableCount++] = allocation;
    }
    allocatedCount -= allocations.length;
    // Wake up threads waiting for the allocated size to drop.
    notifyAll();
  }

  @Override
  public synchronized void trim(int targetSize) {
    int targetAllocationCount = Util.ceilDivide(targetSize, individualAllocationSize);
    int targetAvailableCount = Math.max(0, targetAllocationCount - allocatedCount);
    if (targetAvailableCount >= availableCount) {
      // We're already at or below the target.
      return;
    }

    if (initialAllocationBlock != null) {
      // Some allocations are backed by an initial block. We need to make sure that we hold onto all
      // such allocations. Re-order the available allocations so that the ones backed by the initial
      // block come first.
      int lowIndex = 0;
      int highIndex = availableCount - 1;
      while (lowIndex <= highIndex) {
        Allocation lowAllocation = availableAllocations[lowIndex];
        if (lowAllocation.data == initialAllocationBlock) {
          lowIndex++;
        } else {
          Allocation highAllocation = availableAllocations[lowIndex];
          if (highAllocation.data != initialAllocationBlock) {
            highIndex--;
          } else {
            availableAllocations[lowIndex++] = highAllocation;
            availableAllocations[highIndex--] = lowAllocation;
          }
        }
      }
      // lowIndex is the index of the first allocation not backed by an initial block.
      targetAvailableCount = Math.max(targetAvailableCount, lowIndex);
      if (targetAvailableCount >= availableCount) {
        // We're already at or below the target.
        return;
      }
    }

    // Discard allocations beyond the target.
    Arrays.fill(availableAllocations, targetAvailableCount, availableCount, null);
    availableCount = targetAvailableCount;
  }

  @Override
  public synchronized int getTotalBytesAllocated() {
    return allocatedCount * individualAllocationSize;
  }

  @Override
  public synchronized void blockWhileTotalBytesAllocatedExceeds(int limit)
      throws InterruptedException {
    while (getTotalBytesAllocated() > limit) {
      wait();
    }
  }

  @Override
  public int getIndividualAllocationLength() {
    return individualAllocationSize;
  }

}
