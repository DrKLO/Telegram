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
package org.telegram.messenger.exoplayer.extractor;

import org.telegram.messenger.exoplayer.C;
import org.telegram.messenger.exoplayer.SampleHolder;
import org.telegram.messenger.exoplayer.upstream.Allocation;
import org.telegram.messenger.exoplayer.upstream.Allocator;
import org.telegram.messenger.exoplayer.upstream.DataSource;
import org.telegram.messenger.exoplayer.util.Assertions;
import org.telegram.messenger.exoplayer.util.ParsableByteArray;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * A rolling buffer of sample data and corresponding sample information.
 */
/* package */ final class RollingSampleBuffer {

  private static final int INITIAL_SCRATCH_SIZE = 32;

  private final Allocator allocator;
  private final int allocationLength;

  private final InfoQueue infoQueue;
  private final LinkedBlockingDeque<Allocation> dataQueue;
  private final SampleExtrasHolder extrasHolder;
  private final ParsableByteArray scratch;

  // Accessed only by the consuming thread.
  private long totalBytesDropped;

  // Accessed only by the loading thread.
  private long totalBytesWritten;
  private Allocation lastAllocation;
  private int lastAllocationOffset;

  /**
   * @param allocator An {@link Allocator} from which allocations for sample data can be obtained.
   */
  public RollingSampleBuffer(Allocator allocator) {
    this.allocator = allocator;
    allocationLength = allocator.getIndividualAllocationLength();
    infoQueue = new InfoQueue();
    dataQueue = new LinkedBlockingDeque<>();
    extrasHolder = new SampleExtrasHolder();
    scratch = new ParsableByteArray(INITIAL_SCRATCH_SIZE);
    lastAllocationOffset = allocationLength;
  }

  // Called by the consuming thread, but only when there is no loading thread.

  /**
   * Clears the buffer, returning all allocations to the allocator.
   */
  public void clear() {
    infoQueue.clear();

    allocator.release(dataQueue.toArray(new Allocation[dataQueue.size()]));
    dataQueue.clear();

    totalBytesDropped = 0;
    totalBytesWritten = 0;
    lastAllocation = null;
    lastAllocationOffset = allocationLength;
  }

  /**
   * Returns the current absolute write index.
   */
  public int getWriteIndex() {
    return infoQueue.getWriteIndex();
  }

  /**
   * Discards samples from the write side of the buffer.
   *
   * @param discardFromIndex The absolute index of the first sample to be discarded.
   */
  public void discardUpstreamSamples(int discardFromIndex) {
    totalBytesWritten = infoQueue.discardUpstreamSamples(discardFromIndex);
    dropUpstreamFrom(totalBytesWritten);
  }

  /**
   * Discards data from the write side of the buffer. Data is discarded from the specified absolute
   * position. Any allocations that are fully discarded are returned to the allocator.
   *
   * @param absolutePosition The absolute position (inclusive) from which to discard data.
   */
  private void dropUpstreamFrom(long absolutePosition) {
    int relativePosition = (int) (absolutePosition - totalBytesDropped);
    // Calculate the index of the allocation containing the position, and the offset within it.
    int allocationIndex = relativePosition / allocationLength;
    int allocationOffset = relativePosition % allocationLength;
    // We want to discard any allocations after the one at allocationIdnex.
    int allocationDiscardCount = dataQueue.size() - allocationIndex - 1;
    if (allocationOffset == 0) {
      // If the allocation at allocationIndex is empty, we should discard that one too.
      allocationDiscardCount++;
    }
    // Discard the allocations.
    for (int i = 0; i < allocationDiscardCount; i++) {
      allocator.release(dataQueue.removeLast());
    }
    // Update lastAllocation and lastAllocationOffset to reflect the new position.
    lastAllocation = dataQueue.peekLast();
    lastAllocationOffset = allocationOffset == 0 ? allocationLength : allocationOffset;
  }

  // Called by the consuming thread.

  /**
   * Returns the current absolute read index.
   */
  public int getReadIndex() {
    return infoQueue.getReadIndex();
  }

  /**
   * Fills {@code holder} with information about the current sample, but does not write its data.
   * <p>
   * The fields set are {@link SampleHolder#size}, {@link SampleHolder#timeUs} and
   * {@link SampleHolder#flags}.
   *
   * @param holder The holder into which the current sample information should be written.
   * @return True if the holder was filled. False if there is no current sample.
   */
  public boolean peekSample(SampleHolder holder) {
    return infoQueue.peekSample(holder, extrasHolder);
  }

  /**
   * Skips the current sample.
   */
  public void skipSample() {
    long nextOffset = infoQueue.moveToNextSample();
    dropDownstreamTo(nextOffset);
  }

  /**
   * Attempts to skip to the keyframe before the specified time, if it's present in the buffer.
   *
   * @param timeUs The seek time.
   * @return True if the skip was successful. False otherwise.
   */
  public boolean skipToKeyframeBefore(long timeUs) {
    long nextOffset = infoQueue.skipToKeyframeBefore(timeUs);
    if (nextOffset == -1) {
      return false;
    }
    dropDownstreamTo(nextOffset);
    return true;
  }

  /**
   * Reads the current sample, advancing the read index to the next sample.
   *
   * @param sampleHolder The holder into which the current sample should be written.
   * @return True if a sample was read. False if there is no current sample.
   */
  public boolean readSample(SampleHolder sampleHolder) {
    // Write the sample information into the holder and extrasHolder.
    boolean haveSample = infoQueue.peekSample(sampleHolder, extrasHolder);
    if (!haveSample) {
      return false;
    }

    // Read encryption data if the sample is encrypted.
    if (sampleHolder.isEncrypted()) {
      readEncryptionData(sampleHolder, extrasHolder);
    }
    // Write the sample data into the holder.
    sampleHolder.ensureSpaceForWrite(sampleHolder.size);
    readData(extrasHolder.offset, sampleHolder.data, sampleHolder.size);
    // Advance the read head.
    long nextOffset = infoQueue.moveToNextSample();
    dropDownstreamTo(nextOffset);
    return true;
  }

  /**
   * Reads encryption data for the current sample.
   * <p>
   * The encryption data is written into {@code sampleHolder.cryptoInfo}, and
   * {@code sampleHolder.size} is adjusted to subtract the number of bytes that were read. The
   * same value is added to {@code extrasHolder.offset}.
   *
   * @param sampleHolder The holder into which the encryption data should be written.
   * @param extrasHolder The extras holder whose offset should be read and subsequently adjusted.
   */
  private void readEncryptionData(SampleHolder sampleHolder, SampleExtrasHolder extrasHolder) {
    long offset = extrasHolder.offset;

    // Read the signal byte.
    readData(offset, scratch.data, 1);
    offset++;
    byte signalByte = scratch.data[0];
    boolean subsampleEncryption = (signalByte & 0x80) != 0;
    int ivSize = signalByte & 0x7F;

    // Read the initialization vector.
    if (sampleHolder.cryptoInfo.iv == null) {
      sampleHolder.cryptoInfo.iv = new byte[16];
    }
    readData(offset, sampleHolder.cryptoInfo.iv, ivSize);
    offset += ivSize;

    // Read the subsample count, if present.
    int subsampleCount;
    if (subsampleEncryption) {
      readData(offset, scratch.data, 2);
      offset += 2;
      scratch.setPosition(0);
      subsampleCount = scratch.readUnsignedShort();
    } else {
      subsampleCount = 1;
    }

    // Write the clear and encrypted subsample sizes.
    int[] clearDataSizes = sampleHolder.cryptoInfo.numBytesOfClearData;
    if (clearDataSizes == null || clearDataSizes.length < subsampleCount) {
      clearDataSizes = new int[subsampleCount];
    }
    int[] encryptedDataSizes = sampleHolder.cryptoInfo.numBytesOfEncryptedData;
    if (encryptedDataSizes == null || encryptedDataSizes.length < subsampleCount) {
      encryptedDataSizes = new int[subsampleCount];
    }
    if (subsampleEncryption) {
      int subsampleDataLength = 6 * subsampleCount;
      ensureCapacity(scratch, subsampleDataLength);
      readData(offset, scratch.data, subsampleDataLength);
      offset += subsampleDataLength;
      scratch.setPosition(0);
      for (int i = 0; i < subsampleCount; i++) {
        clearDataSizes[i] = scratch.readUnsignedShort();
        encryptedDataSizes[i] = scratch.readUnsignedIntToInt();
      }
    } else {
      clearDataSizes[0] = 0;
      encryptedDataSizes[0] = sampleHolder.size - (int) (offset - extrasHolder.offset);
    }

    // Populate the cryptoInfo.
    sampleHolder.cryptoInfo.set(subsampleCount, clearDataSizes, encryptedDataSizes,
        extrasHolder.encryptionKeyId, sampleHolder.cryptoInfo.iv, C.CRYPTO_MODE_AES_CTR);

    // Adjust the offset and size to take into account the bytes read.
    int bytesRead = (int) (offset - extrasHolder.offset);
    extrasHolder.offset += bytesRead;
    sampleHolder.size -= bytesRead;
  }

  /**
   * Reads data from the front of the rolling buffer.
   *
   * @param absolutePosition The absolute position from which data should be read.
   * @param target The buffer into which data should be written.
   * @param length The number of bytes to read.
   */
  private void readData(long absolutePosition, ByteBuffer target, int length) {
    int remaining = length;
    while (remaining > 0) {
      dropDownstreamTo(absolutePosition);
      int positionInAllocation = (int) (absolutePosition - totalBytesDropped);
      int toCopy = Math.min(remaining, allocationLength - positionInAllocation);
      Allocation allocation = dataQueue.peek();
      target.put(allocation.data, allocation.translateOffset(positionInAllocation), toCopy);
      absolutePosition += toCopy;
      remaining -= toCopy;
    }
  }

  /**
   * Reads data from the front of the rolling buffer.
   *
   * @param absolutePosition The absolute position from which data should be read.
   * @param target The array into which data should be written.
   * @param length The number of bytes to read.
   */
  // TODO: Consider reducing duplication of this method and the one above.
  private void readData(long absolutePosition, byte[] target, int length) {
    int bytesRead = 0;
    while (bytesRead < length) {
      dropDownstreamTo(absolutePosition);
      int positionInAllocation = (int) (absolutePosition - totalBytesDropped);
      int toCopy = Math.min(length - bytesRead, allocationLength - positionInAllocation);
      Allocation allocation = dataQueue.peek();
      System.arraycopy(allocation.data, allocation.translateOffset(positionInAllocation), target,
          bytesRead, toCopy);
      absolutePosition += toCopy;
      bytesRead += toCopy;
    }
  }

  /**
   * Discard any allocations that hold data prior to the specified absolute position, returning
   * them to the allocator.
   *
   * @param absolutePosition The absolute position up to which allocations can be discarded.
   */
  private void dropDownstreamTo(long absolutePosition) {
    int relativePosition = (int) (absolutePosition - totalBytesDropped);
    int allocationIndex = relativePosition / allocationLength;
    for (int i = 0; i < allocationIndex; i++) {
      allocator.release(dataQueue.remove());
      totalBytesDropped += allocationLength;
    }
  }

  /**
   * Ensure that the passed {@link ParsableByteArray} is of at least the specified limit.
   */
  private static void ensureCapacity(ParsableByteArray byteArray, int limit) {
    if (byteArray.limit() < limit) {
      byteArray.reset(new byte[limit], limit);
    }
  }

  // Called by the loading thread.

  /**
   * Returns the current write position in the rolling buffer.
   *
   * @return The current write position.
   */
  public long getWritePosition() {
    return totalBytesWritten;
  }

  /**
   * Appends data to the rolling buffer.
   *
   * @param dataSource The source from which to read.
   * @param length The maximum length of the read.
   * @param allowEndOfInput True if encountering the end of the input having appended no data is
   *     allowed, and should result in {@link C#RESULT_END_OF_INPUT} being returned. False if it
   *     should be considered an error, causing an {@link EOFException} to be thrown.
   * @return The number of bytes appended, or {@link C#RESULT_END_OF_INPUT} if the input has ended.
   * @throws IOException If an error occurs reading from the source.
   */
  public int appendData(DataSource dataSource, int length, boolean allowEndOfInput)
      throws IOException {
    length = prepareForAppend(length);
    int bytesAppended = dataSource.read(lastAllocation.data,
        lastAllocation.translateOffset(lastAllocationOffset), length);
    if (bytesAppended == C.RESULT_END_OF_INPUT) {
      if (allowEndOfInput) {
        return C.RESULT_END_OF_INPUT;
      }
      throw new EOFException();
    }
    lastAllocationOffset += bytesAppended;
    totalBytesWritten += bytesAppended;
    return bytesAppended;
  }

  /**
   * Appends data to the rolling buffer.
   *
   * @param input The source from which to read.
   * @param length The maximum length of the read.
   * @param allowEndOfInput True if encountering the end of the input having appended no data is
   *     allowed, and should result in {@link C#RESULT_END_OF_INPUT} being returned. False if it
   *     should be considered an error, causing an {@link EOFException} to be thrown.
   * @return The number of bytes appended, or {@link C#RESULT_END_OF_INPUT} if the input has ended.
   * @throws IOException If an error occurs reading from the source.
   * @throws InterruptedException If the thread has been interrupted.
   */
  public int appendData(ExtractorInput input, int length, boolean allowEndOfInput)
      throws IOException, InterruptedException {
    length = prepareForAppend(length);
    int bytesAppended = input.read(lastAllocation.data,
        lastAllocation.translateOffset(lastAllocationOffset), length);
    if (bytesAppended == C.RESULT_END_OF_INPUT) {
      if (allowEndOfInput) {
        return C.RESULT_END_OF_INPUT;
      }
      throw new EOFException();
    }
    lastAllocationOffset += bytesAppended;
    totalBytesWritten += bytesAppended;
    return bytesAppended;
  }

  /**
   * Appends data to the rolling buffer.
   *
   * @param buffer A buffer containing the data to append.
   * @param length The length of the data to append.
   */
  public void appendData(ParsableByteArray buffer, int length) {
    while (length > 0) {
      int thisAppendLength = prepareForAppend(length);
      buffer.readBytes(lastAllocation.data, lastAllocation.translateOffset(lastAllocationOffset),
          thisAppendLength);
      lastAllocationOffset += thisAppendLength;
      totalBytesWritten += thisAppendLength;
      length -= thisAppendLength;
    }
  }

  /**
   * Indicates the end point for the current sample, making it available for consumption.
   *
   * @param sampleTimeUs The sample timestamp.
   * @param flags Flags that accompany the sample. See {@link SampleHolder#flags}.
   * @param position The position of the sample data in the rolling buffer.
   * @param size The size of the sample, in bytes.
   * @param encryptionKey The encryption key associated with the sample, or null.
   */
  public void commitSample(long sampleTimeUs, int flags, long position, int size,
      byte[] encryptionKey) {
    infoQueue.commitSample(sampleTimeUs, flags, position, size, encryptionKey);
  }

  /**
   * Prepares the rolling sample buffer for an append of up to {@code length} bytes, returning the
   * number of bytes that can actually be appended.
   */
  private int prepareForAppend(int length) {
    if (lastAllocationOffset == allocationLength) {
      lastAllocationOffset = 0;
      lastAllocation = allocator.allocate();
      dataQueue.add(lastAllocation);
    }
    return Math.min(length, allocationLength - lastAllocationOffset);
  }

  /**
   * Holds information about the samples in the rolling buffer.
   */
  private static final class InfoQueue {

    private static final int SAMPLE_CAPACITY_INCREMENT = 1000;

    private int capacity;

    private long[] offsets;
    private int[] sizes;
    private int[] flags;
    private long[] timesUs;
    private byte[][] encryptionKeys;

    private int queueSize;
    private int absoluteReadIndex;
    private int relativeReadIndex;
    private int relativeWriteIndex;

    public InfoQueue() {
      capacity = SAMPLE_CAPACITY_INCREMENT;
      offsets = new long[capacity];
      timesUs = new long[capacity];
      flags = new int[capacity];
      sizes = new int[capacity];
      encryptionKeys = new byte[capacity][];
    }

    // Called by the consuming thread, but only when there is no loading thread.

    /**
     * Clears the queue.
     */
    public void clear() {
      absoluteReadIndex = 0;
      relativeReadIndex = 0;
      relativeWriteIndex = 0;
      queueSize = 0;
    }

    /**
     * Returns the current absolute write index.
     */
    public int getWriteIndex() {
      return absoluteReadIndex + queueSize;
    }

    /**
     * Discards samples from the write side of the buffer.
     *
     * @param discardFromIndex The absolute index of the first sample to be discarded.
     * @return The reduced total number of bytes written, after the samples have been discarded.
     */
    public long discardUpstreamSamples(int discardFromIndex) {
      int discardCount = getWriteIndex() - discardFromIndex;
      Assertions.checkArgument(0 <= discardCount && discardCount <= queueSize);

      if (discardCount == 0) {
        if (absoluteReadIndex == 0) {
          // queueSize == absoluteReadIndex == 0, so nothing has been written to the queue.
          return 0;
        }
        int lastWriteIndex = (relativeWriteIndex == 0 ? capacity : relativeWriteIndex) - 1;
        return offsets[lastWriteIndex] + sizes[lastWriteIndex];
      }

      queueSize -= discardCount;
      relativeWriteIndex = (relativeWriteIndex + capacity - discardCount) % capacity;
      return offsets[relativeWriteIndex];
    }

    // Called by the consuming thread.

    /**
     * Returns the current absolute read index.
     */
    public int getReadIndex() {
      return absoluteReadIndex;
    }

    /**
     * Fills {@code holder} with information about the current sample, but does not write its data.
     * The first entry in {@code offsetHolder} is filled with the absolute position of the sample's
     * data in the rolling buffer.
     * <p>
     * Populates {@link SampleHolder#size}, {@link SampleHolder#timeUs}, {@link SampleHolder#flags}
     * and the {@code extrasHolder}.
     *
     * @param holder The holder into which the current sample information should be written.
     * @param extrasHolder The holder into which extra sample information should be written.
     * @return True if the holders were filled. False if there is no current sample.
     */
    public synchronized boolean peekSample(SampleHolder holder, SampleExtrasHolder extrasHolder) {
      if (queueSize == 0) {
        return false;
      }
      holder.timeUs = timesUs[relativeReadIndex];
      holder.size = sizes[relativeReadIndex];
      holder.flags = flags[relativeReadIndex];
      extrasHolder.offset = offsets[relativeReadIndex];
      extrasHolder.encryptionKeyId = encryptionKeys[relativeReadIndex];
      return true;
    }

    /**
     * Advances the read index to the next sample.
     *
     * @return The absolute position of the first byte in the rolling buffer that may still be
     *     required after advancing the index. Data prior to this position can be dropped.
     */
    public synchronized long moveToNextSample() {
      queueSize--;
      int lastReadIndex = relativeReadIndex++;
      absoluteReadIndex++;
      if (relativeReadIndex == capacity) {
        // Wrap around.
        relativeReadIndex = 0;
      }
      return queueSize > 0 ? offsets[relativeReadIndex]
          : (sizes[lastReadIndex] + offsets[lastReadIndex]);
    }

    /**
     * Attempts to locate the keyframe before the specified time, if it's present in the buffer.
     *
     * @param timeUs The seek time.
     * @return The offset of the keyframe's data if the keyframe was present. -1 otherwise.
     */
    public synchronized long skipToKeyframeBefore(long timeUs) {
      if (queueSize == 0 || timeUs < timesUs[relativeReadIndex]) {
        return -1;
      }

      int lastWriteIndex = (relativeWriteIndex == 0 ? capacity : relativeWriteIndex) - 1;
      long lastTimeUs = timesUs[lastWriteIndex];
      if (timeUs > lastTimeUs) {
        return -1;
      }

      // TODO: This can be optimized further using binary search, although the fact that the array
      // is cyclic means we'd need to implement the binary search ourselves.
      int sampleCount = 0;
      int sampleCountToKeyframe = -1;
      int searchIndex = relativeReadIndex;
      while (searchIndex != relativeWriteIndex) {
        if (timesUs[searchIndex] > timeUs) {
          // We've gone too far.
          break;
        } else if ((flags[searchIndex] & C.SAMPLE_FLAG_SYNC) != 0) {
          // We've found a keyframe, and we're still before the seek position.
          sampleCountToKeyframe = sampleCount;
        }
        searchIndex = (searchIndex + 1) % capacity;
        sampleCount++;
      }

      if (sampleCountToKeyframe == -1) {
        return -1;
      }

      queueSize -= sampleCountToKeyframe;
      relativeReadIndex = (relativeReadIndex + sampleCountToKeyframe) % capacity;
      absoluteReadIndex += sampleCountToKeyframe;
      return offsets[relativeReadIndex];
    }

    // Called by the loading thread.

    public synchronized void commitSample(long timeUs, int sampleFlags, long offset, int size,
        byte[] encryptionKey) {
      timesUs[relativeWriteIndex] = timeUs;
      offsets[relativeWriteIndex] = offset;
      sizes[relativeWriteIndex] = size;
      flags[relativeWriteIndex] = sampleFlags;
      encryptionKeys[relativeWriteIndex] = encryptionKey;
      // Increment the write index.
      queueSize++;
      if (queueSize == capacity) {
        // Increase the capacity.
        int newCapacity = capacity + SAMPLE_CAPACITY_INCREMENT;
        long[] newOffsets = new long[newCapacity];
        long[] newTimesUs = new long[newCapacity];
        int[] newFlags = new int[newCapacity];
        int[] newSizes = new int[newCapacity];
        byte[][] newEncryptionKeys = new byte[newCapacity][];
        int beforeWrap = capacity - relativeReadIndex;
        System.arraycopy(offsets, relativeReadIndex, newOffsets, 0, beforeWrap);
        System.arraycopy(timesUs, relativeReadIndex, newTimesUs, 0, beforeWrap);
        System.arraycopy(flags, relativeReadIndex, newFlags, 0, beforeWrap);
        System.arraycopy(sizes, relativeReadIndex, newSizes, 0, beforeWrap);
        System.arraycopy(encryptionKeys, relativeReadIndex, newEncryptionKeys, 0, beforeWrap);
        int afterWrap = relativeReadIndex;
        System.arraycopy(offsets, 0, newOffsets, beforeWrap, afterWrap);
        System.arraycopy(timesUs, 0, newTimesUs, beforeWrap, afterWrap);
        System.arraycopy(flags, 0, newFlags, beforeWrap, afterWrap);
        System.arraycopy(sizes, 0, newSizes, beforeWrap, afterWrap);
        System.arraycopy(encryptionKeys, 0, newEncryptionKeys, beforeWrap, afterWrap);
        offsets = newOffsets;
        timesUs = newTimesUs;
        flags = newFlags;
        sizes = newSizes;
        encryptionKeys = newEncryptionKeys;
        relativeReadIndex = 0;
        relativeWriteIndex = capacity;
        queueSize = capacity;
        capacity = newCapacity;
      } else {
        relativeWriteIndex++;
        if (relativeWriteIndex == capacity) {
          // Wrap around.
          relativeWriteIndex = 0;
        }
      }
    }

  }

  /**
   * Holds additional sample information not held by {@link SampleHolder}.
   */
  private static final class SampleExtrasHolder {

    public long offset;
    public byte[] encryptionKeyId;

  }

}
