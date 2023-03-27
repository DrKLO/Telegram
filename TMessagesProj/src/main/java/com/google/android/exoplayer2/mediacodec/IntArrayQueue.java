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
package com.google.android.exoplayer2.mediacodec;

import java.util.NoSuchElementException;

/**
 * Array-based unbounded queue for int primitives with amortized O(1) add and remove.
 *
 * <p>Use this class instead of a {@link java.util.Deque} to avoid boxing int primitives to {@link
 * Integer} instances.
 */
/* package */ final class IntArrayQueue {

  /** Default capacity needs to be a power of 2. */
  private static final int DEFAULT_INITIAL_CAPACITY = 16;

  private int headIndex;
  private int tailIndex;
  private int size;
  private int[] data;
  private int wrapAroundMask;

  public IntArrayQueue() {
    headIndex = 0;
    tailIndex = -1;
    size = 0;
    data = new int[DEFAULT_INITIAL_CAPACITY];
    wrapAroundMask = data.length - 1;
  }

  /** Add a new item to the queue. */
  public void add(int value) {
    if (size == data.length) {
      doubleArraySize();
    }

    tailIndex = (tailIndex + 1) & wrapAroundMask;
    data[tailIndex] = value;
    size++;
  }

  /**
   * Remove an item from the queue.
   *
   * @throws NoSuchElementException if the queue is empty.
   */
  public int remove() {
    if (size == 0) {
      throw new NoSuchElementException();
    }

    int value = data[headIndex];
    headIndex = (headIndex + 1) & wrapAroundMask;
    size--;

    return value;
  }

  /** Returns the number of items in the queue. */
  public int size() {
    return size;
  }

  /** Returns whether the queue is empty. */
  public boolean isEmpty() {
    return size == 0;
  }

  /** Clears the queue. */
  public void clear() {
    headIndex = 0;
    tailIndex = -1;
    size = 0;
  }

  /** Returns the length of the backing array. */
  public int capacity() {
    return data.length;
  }

  private void doubleArraySize() {
    int newCapacity = data.length << 1;
    if (newCapacity < 0) {
      throw new IllegalStateException();
    }

    int[] newData = new int[newCapacity];
    int itemsToRight = data.length - headIndex;
    int itemsToLeft = headIndex;
    System.arraycopy(data, headIndex, newData, 0, itemsToRight);
    System.arraycopy(data, 0, newData, itemsToRight, itemsToLeft);

    headIndex = 0;
    tailIndex = size - 1;
    data = newData;
    wrapAroundMask = data.length - 1;
  }
}
