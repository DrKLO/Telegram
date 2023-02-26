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
package com.google.android.exoplayer2.source;

import static java.lang.Math.min;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.decoder.CryptoInfo;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer.InsufficientCapacityException;
import com.google.android.exoplayer2.extractor.TrackOutput.CryptoData;
import com.google.android.exoplayer2.source.SampleQueue.SampleExtrasHolder;
import com.google.android.exoplayer2.upstream.Allocation;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.DataReader;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

/** A queue of media sample data. */
/* package */ class SampleDataQueue {

  private static final int INITIAL_SCRATCH_SIZE = 32;

  private final Allocator allocator;
  private final int allocationLength;
  private final ParsableByteArray scratch;

  // References into the linked list of allocations.
  private AllocationNode firstAllocationNode;
  private AllocationNode readAllocationNode;
  private AllocationNode writeAllocationNode;

  // Accessed only by the loading thread (or the consuming thread when there is no loading thread).
  private long totalBytesWritten;

  public SampleDataQueue(Allocator allocator) {
    this.allocator = allocator;
    allocationLength = allocator.getIndividualAllocationLength();
    scratch = new ParsableByteArray(INITIAL_SCRATCH_SIZE);
    firstAllocationNode = new AllocationNode(/* startPosition= */ 0, allocationLength);
    readAllocationNode = firstAllocationNode;
    writeAllocationNode = firstAllocationNode;
  }

  // Called by the consuming thread, but only when there is no loading thread.

  /** Clears all sample data. */
  public void reset() {
    clearAllocationNodes(firstAllocationNode);
    firstAllocationNode.reset(/* startPosition= */ 0, allocationLength);
    readAllocationNode = firstAllocationNode;
    writeAllocationNode = firstAllocationNode;
    totalBytesWritten = 0;
    allocator.trim();
  }

  /**
   * Discards sample data bytes from the write side of the queue.
   *
   * @param totalBytesWritten The reduced total number of bytes written after the samples have been
   *     discarded, or 0 if the queue is now empty.
   */
  public void discardUpstreamSampleBytes(long totalBytesWritten) {
    Assertions.checkArgument(totalBytesWritten <= this.totalBytesWritten);
    this.totalBytesWritten = totalBytesWritten;
    if (this.totalBytesWritten == 0
        || this.totalBytesWritten == firstAllocationNode.startPosition) {
      clearAllocationNodes(firstAllocationNode);
      firstAllocationNode = new AllocationNode(this.totalBytesWritten, allocationLength);
      readAllocationNode = firstAllocationNode;
      writeAllocationNode = firstAllocationNode;
    } else {
      // Find the last node containing at least 1 byte of data that we need to keep.
      AllocationNode lastNodeToKeep = firstAllocationNode;
      while (this.totalBytesWritten > lastNodeToKeep.endPosition) {
        lastNodeToKeep = lastNodeToKeep.next;
      }
      // Discard all subsequent nodes. lastNodeToKeep is initialized, therefore next cannot be null.
      AllocationNode firstNodeToDiscard = Assertions.checkNotNull(lastNodeToKeep.next);
      clearAllocationNodes(firstNodeToDiscard);
      // Reset the successor of the last node to be an uninitialized node.
      lastNodeToKeep.next = new AllocationNode(lastNodeToKeep.endPosition, allocationLength);
      // Update writeAllocationNode and readAllocationNode as necessary.
      writeAllocationNode =
          this.totalBytesWritten == lastNodeToKeep.endPosition
              ? lastNodeToKeep.next
              : lastNodeToKeep;
      if (readAllocationNode == firstNodeToDiscard) {
        readAllocationNode = lastNodeToKeep.next;
      }
    }
  }

  // Called by the consuming thread.

  /** Rewinds the read position to the first sample in the queue. */
  public void rewind() {
    readAllocationNode = firstAllocationNode;
  }

  /**
   * Reads data from the rolling buffer to populate a decoder input buffer, and advances the read
   * position.
   *
   * @param buffer The buffer to populate.
   * @param extrasHolder The extras holder whose offset should be read and subsequently adjusted.
   * @throws InsufficientCapacityException If the {@code buffer} has insufficient capacity to hold
   *     the data being read.
   */
  public void readToBuffer(DecoderInputBuffer buffer, SampleExtrasHolder extrasHolder) {
    readAllocationNode = readSampleData(readAllocationNode, buffer, extrasHolder, scratch);
  }

  /**
   * Peeks data from the rolling buffer to populate a decoder input buffer, without advancing the
   * read position.
   *
   * @param buffer The buffer to populate.
   * @param extrasHolder The extras holder whose offset should be read and subsequently adjusted.
   * @throws InsufficientCapacityException If the {@code buffer} has insufficient capacity to hold
   *     the data being peeked.
   */
  public void peekToBuffer(DecoderInputBuffer buffer, SampleExtrasHolder extrasHolder) {
    readSampleData(readAllocationNode, buffer, extrasHolder, scratch);
  }

  /**
   * Advances the read position to the specified absolute position.
   *
   * @param absolutePosition The new absolute read position. May be {@link C#POSITION_UNSET}, in
   *     which case calling this method is a no-op.
   */
  public void discardDownstreamTo(long absolutePosition) {
    if (absolutePosition == C.POSITION_UNSET) {
      return;
    }
    while (absolutePosition >= firstAllocationNode.endPosition) {
      // Advance firstAllocationNode to the specified absolute position. Also clear nodes that are
      // advanced past, and return their underlying allocations to the allocator.
      allocator.release(firstAllocationNode.allocation);
      firstAllocationNode = firstAllocationNode.clear();
    }
    if (readAllocationNode.startPosition < firstAllocationNode.startPosition) {
      // We discarded the node referenced by readAllocationNode. We need to advance it to the first
      // remaining node.
      readAllocationNode = firstAllocationNode;
    }
  }

  // Called by the loading thread.

  public long getTotalBytesWritten() {
    return totalBytesWritten;
  }

  public int sampleData(DataReader input, int length, boolean allowEndOfInput) throws IOException {
    length = preAppend(length);
    int bytesAppended =
        input.read(
            writeAllocationNode.allocation.data,
            writeAllocationNode.translateOffset(totalBytesWritten),
            length);
    if (bytesAppended == C.RESULT_END_OF_INPUT) {
      if (allowEndOfInput) {
        return C.RESULT_END_OF_INPUT;
      }
      throw new EOFException();
    }
    postAppend(bytesAppended);
    return bytesAppended;
  }

  public void sampleData(ParsableByteArray buffer, int length) {
    while (length > 0) {
      int bytesAppended = preAppend(length);
      buffer.readBytes(
          writeAllocationNode.allocation.data,
          writeAllocationNode.translateOffset(totalBytesWritten),
          bytesAppended);
      length -= bytesAppended;
      postAppend(bytesAppended);
    }
  }

  // Private methods.

  /**
   * Clears allocation nodes starting from {@code fromNode}.
   *
   * @param fromNode The node from which to clear.
   */
  private void clearAllocationNodes(AllocationNode fromNode) {
    if (fromNode.allocation == null) {
      return;
    }
    // Bulk release allocations for performance (it's significantly faster when using
    // DefaultAllocator because the allocator's lock only needs to be acquired and released once)
    // [Internal: See b/29542039].
    allocator.release(fromNode);
    fromNode.clear();
  }

  /**
   * Called before writing sample data to {@link #writeAllocationNode}. May cause {@link
   * #writeAllocationNode} to be initialized.
   *
   * @param length The number of bytes that the caller wishes to write.
   * @return The number of bytes that the caller is permitted to write, which may be less than
   *     {@code length}.
   */
  private int preAppend(int length) {
    if (writeAllocationNode.allocation == null) {
      writeAllocationNode.initialize(
          allocator.allocate(),
          new AllocationNode(writeAllocationNode.endPosition, allocationLength));
    }
    return min(length, (int) (writeAllocationNode.endPosition - totalBytesWritten));
  }

  /**
   * Called after writing sample data. May cause {@link #writeAllocationNode} to be advanced.
   *
   * @param length The number of bytes that were written.
   */
  private void postAppend(int length) {
    totalBytesWritten += length;
    if (totalBytesWritten == writeAllocationNode.endPosition) {
      writeAllocationNode = writeAllocationNode.next;
    }
  }

  /**
   * Reads data from the rolling buffer to populate a decoder input buffer.
   *
   * @param allocationNode The first {@link AllocationNode} containing data yet to be read.
   * @param buffer The buffer to populate.
   * @param extrasHolder The extras holder whose offset should be read and subsequently adjusted.
   * @param scratch A scratch {@link ParsableByteArray}.
   * @return The first {@link AllocationNode} that contains unread bytes after the last byte that
   *     the invocation read.
   * @throws InsufficientCapacityException If the {@code buffer} has insufficient capacity to hold
   *     the sample data.
   */
  private static AllocationNode readSampleData(
      AllocationNode allocationNode,
      DecoderInputBuffer buffer,
      SampleExtrasHolder extrasHolder,
      ParsableByteArray scratch) {
    if (buffer.isEncrypted()) {
      allocationNode = readEncryptionData(allocationNode, buffer, extrasHolder, scratch);
    }
    // Read sample data, extracting supplemental data into a separate buffer if needed.
    if (buffer.hasSupplementalData()) {
      // If there is supplemental data, the sample data is prefixed by its size.
      scratch.reset(4);
      allocationNode = readData(allocationNode, extrasHolder.offset, scratch.getData(), 4);
      int sampleSize = scratch.readUnsignedIntToInt();
      extrasHolder.offset += 4;
      extrasHolder.size -= 4;

      // Write the sample data.
      buffer.ensureSpaceForWrite(sampleSize);
      allocationNode = readData(allocationNode, extrasHolder.offset, buffer.data, sampleSize);
      extrasHolder.offset += sampleSize;
      extrasHolder.size -= sampleSize;

      // Write the remaining data as supplemental data.
      buffer.resetSupplementalData(extrasHolder.size);
      allocationNode =
          readData(allocationNode, extrasHolder.offset, buffer.supplementalData, extrasHolder.size);
    } else {
      // Write the sample data.
      buffer.ensureSpaceForWrite(extrasHolder.size);
      allocationNode =
          readData(allocationNode, extrasHolder.offset, buffer.data, extrasHolder.size);
    }
    return allocationNode;
  }

  /**
   * Reads encryption data for the sample described by {@code extrasHolder}.
   *
   * <p>The encryption data is written into {@link DecoderInputBuffer#cryptoInfo}, and {@link
   * SampleExtrasHolder#size} is adjusted to subtract the number of bytes that were read. The same
   * value is added to {@link SampleExtrasHolder#offset}.
   *
   * @param allocationNode The first {@link AllocationNode} containing data yet to be read.
   * @param buffer The buffer into which the encryption data should be written.
   * @param extrasHolder The extras holder whose offset should be read and subsequently adjusted.
   * @param scratch A scratch {@link ParsableByteArray}.
   * @return The first {@link AllocationNode} that contains unread bytes after this method returns.
   */
  private static AllocationNode readEncryptionData(
      AllocationNode allocationNode,
      DecoderInputBuffer buffer,
      SampleExtrasHolder extrasHolder,
      ParsableByteArray scratch) {
    long offset = extrasHolder.offset;

    // Read the signal byte.
    scratch.reset(1);
    allocationNode = readData(allocationNode, offset, scratch.getData(), 1);
    offset++;
    byte signalByte = scratch.getData()[0];
    boolean subsampleEncryption = (signalByte & 0x80) != 0;
    int ivSize = signalByte & 0x7F;

    // Read the initialization vector.
    CryptoInfo cryptoInfo = buffer.cryptoInfo;
    if (cryptoInfo.iv == null) {
      cryptoInfo.iv = new byte[16];
    } else {
      // Zero out cryptoInfo.iv so that if ivSize < 16, the remaining bytes are correctly set to 0.
      Arrays.fill(cryptoInfo.iv, (byte) 0);
    }
    allocationNode = readData(allocationNode, offset, cryptoInfo.iv, ivSize);
    offset += ivSize;

    // Read the subsample count, if present.
    int subsampleCount;
    if (subsampleEncryption) {
      scratch.reset(2);
      allocationNode = readData(allocationNode, offset, scratch.getData(), 2);
      offset += 2;
      subsampleCount = scratch.readUnsignedShort();
    } else {
      subsampleCount = 1;
    }

    // Write the clear and encrypted subsample sizes.
    @Nullable int[] clearDataSizes = cryptoInfo.numBytesOfClearData;
    if (clearDataSizes == null || clearDataSizes.length < subsampleCount) {
      clearDataSizes = new int[subsampleCount];
    }
    @Nullable int[] encryptedDataSizes = cryptoInfo.numBytesOfEncryptedData;
    if (encryptedDataSizes == null || encryptedDataSizes.length < subsampleCount) {
      encryptedDataSizes = new int[subsampleCount];
    }
    if (subsampleEncryption) {
      int subsampleDataLength = 6 * subsampleCount;
      scratch.reset(subsampleDataLength);
      allocationNode = readData(allocationNode, offset, scratch.getData(), subsampleDataLength);
      offset += subsampleDataLength;
      scratch.setPosition(0);
      for (int i = 0; i < subsampleCount; i++) {
        clearDataSizes[i] = scratch.readUnsignedShort();
        encryptedDataSizes[i] = scratch.readUnsignedIntToInt();
      }
    } else {
      clearDataSizes[0] = 0;
      encryptedDataSizes[0] = extrasHolder.size - (int) (offset - extrasHolder.offset);
    }

    // Populate the cryptoInfo.
    CryptoData cryptoData = Util.castNonNull(extrasHolder.cryptoData);
    cryptoInfo.set(
        subsampleCount,
        clearDataSizes,
        encryptedDataSizes,
        cryptoData.encryptionKey,
        cryptoInfo.iv,
        cryptoData.cryptoMode,
        cryptoData.encryptedBlocks,
        cryptoData.clearBlocks);

    // Adjust the offset and size to take into account the bytes read.
    int bytesRead = (int) (offset - extrasHolder.offset);
    extrasHolder.offset += bytesRead;
    extrasHolder.size -= bytesRead;
    return allocationNode;
  }

  /**
   * Reads data from {@code allocationNode} and its following nodes.
   *
   * @param allocationNode The first {@link AllocationNode} containing data yet to be read.
   * @param absolutePosition The absolute position from which data should be read.
   * @param target The buffer into which data should be written.
   * @param length The number of bytes to read.
   * @return The first {@link AllocationNode} that contains unread bytes after this method returns.
   */
  private static AllocationNode readData(
      AllocationNode allocationNode, long absolutePosition, ByteBuffer target, int length) {
    allocationNode = getNodeContainingPosition(allocationNode, absolutePosition);
    int remaining = length;
    while (remaining > 0) {
      int toCopy = min(remaining, (int) (allocationNode.endPosition - absolutePosition));
      Allocation allocation = allocationNode.allocation;
      target.put(allocation.data, allocationNode.translateOffset(absolutePosition), toCopy);
      remaining -= toCopy;
      absolutePosition += toCopy;
      if (absolutePosition == allocationNode.endPosition) {
        allocationNode = allocationNode.next;
      }
    }
    return allocationNode;
  }

  /**
   * Reads data from {@code allocationNode} and its following nodes.
   *
   * @param allocationNode The first {@link AllocationNode} containing data yet to be read.
   * @param absolutePosition The absolute position from which data should be read.
   * @param target The array into which data should be written.
   * @param length The number of bytes to read.
   * @return The first {@link AllocationNode} that contains unread bytes after this method returns.
   */
  private static AllocationNode readData(
      AllocationNode allocationNode, long absolutePosition, byte[] target, int length) {
    allocationNode = getNodeContainingPosition(allocationNode, absolutePosition);
    int remaining = length;
    while (remaining > 0) {
      int toCopy = min(remaining, (int) (allocationNode.endPosition - absolutePosition));
      Allocation allocation = allocationNode.allocation;
      System.arraycopy(
          allocation.data,
          allocationNode.translateOffset(absolutePosition),
          target,
          length - remaining,
          toCopy);
      remaining -= toCopy;
      absolutePosition += toCopy;
      if (absolutePosition == allocationNode.endPosition) {
        allocationNode = allocationNode.next;
      }
    }
    return allocationNode;
  }

  /**
   * Returns the {@link AllocationNode} in {@code allocationNode}'s chain which contains the given
   * {@code absolutePosition}.
   */
  private static AllocationNode getNodeContainingPosition(
      AllocationNode allocationNode, long absolutePosition) {
    while (absolutePosition >= allocationNode.endPosition) {
      allocationNode = allocationNode.next;
    }
    return allocationNode;
  }

  /** A node in a linked list of {@link Allocation}s held by the output. */
  private static final class AllocationNode implements Allocator.AllocationNode {

    /** The absolute position of the start of the data (inclusive). */
    public long startPosition;
    /** The absolute position of the end of the data (exclusive). */
    public long endPosition;
    /**
     * The {@link Allocation}, or {@code null} if the node is not {@link #initialize initialized}.
     */
    @Nullable public Allocation allocation;
    /**
     * The next {@link AllocationNode} in the list, or {@code null} if the node is not {@link
     * #initialize initialized}.
     */
    @Nullable public AllocationNode next;

    /**
     * @param startPosition See {@link #startPosition}.
     * @param allocationLength The length of the {@link Allocation} with which this node will be
     *     initialized.
     */
    public AllocationNode(long startPosition, int allocationLength) {
      reset(startPosition, allocationLength);
    }

    /**
     * Sets the {@link #startPosition} and the {@link Allocation} length.
     *
     * <p>Must only be called for uninitialized instances, where {@link #allocation} is {@code
     * null}.
     */
    public void reset(long startPosition, int allocationLength) {
      Assertions.checkState(allocation == null);
      this.startPosition = startPosition;
      this.endPosition = startPosition + allocationLength;
    }

    /**
     * Initializes the node.
     *
     * @param allocation The node's {@link Allocation}.
     * @param next The next {@link AllocationNode}.
     */
    public void initialize(Allocation allocation, AllocationNode next) {
      this.allocation = allocation;
      this.next = next;
    }

    /**
     * Gets the offset into the {@link #allocation}'s {@link Allocation#data} that corresponds to
     * the specified absolute position.
     *
     * @param absolutePosition The absolute position.
     * @return The corresponding offset into the allocation's data.
     */
    public int translateOffset(long absolutePosition) {
      return (int) (absolutePosition - startPosition) + allocation.offset;
    }

    /**
     * Clears {@link #allocation} and {@link #next}.
     *
     * @return The cleared next {@link AllocationNode}.
     */
    public AllocationNode clear() {
      allocation = null;
      AllocationNode temp = next;
      next = null;
      return temp;
    }

    // AllocationChainNode implementation.

    @Override
    public Allocation getAllocation() {
      return Assertions.checkNotNull(allocation);
    }

    @Override
    @Nullable
    public Allocator.AllocationNode next() {
      if (next == null || next.allocation == null) {
        return null;
      } else {
        return next;
      }
    }
  }
}
