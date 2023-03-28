/*
 * Copyright 2021 The Android Open Source Project
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

package com.google.android.exoplayer2.source.rtsp;

import static java.lang.Math.abs;
import static java.lang.Math.max;
import static java.lang.Math.min;

import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.C;
import java.util.TreeSet;

/**
 * Orders RTP packets by their sequence numbers to correct the possible alternation in packet
 * ordering, introduced by UDP transport.
 */
/* package */ final class RtpPacketReorderingQueue {
  /** The maximum sequence number discontinuity allowed without resetting the re-ordering buffer. */
  @VisibleForTesting /* package */ static final int MAX_SEQUENCE_LEAP_ALLOWED = 1000;

  /** Queue size threshold for resetting the queue. 5000 packets equate about 7MB in buffer size. */
  private static final int QUEUE_SIZE_THRESHOLD_FOR_RESET = 5000;

  // Use set to eliminate duplicating packets.
  @GuardedBy("this")
  private final TreeSet<RtpPacketContainer> packetQueue;

  @GuardedBy("this")
  private int lastReceivedSequenceNumber;

  @GuardedBy("this")
  private int lastDequeuedSequenceNumber;

  @GuardedBy("this")
  private boolean started;

  /** Creates an instance. */
  public RtpPacketReorderingQueue() {
    packetQueue =
        new TreeSet<>(
            (packetContainer1, packetContainer2) ->
                calculateSequenceNumberShift(
                    packetContainer1.packet.sequenceNumber,
                    packetContainer2.packet.sequenceNumber));

    reset();
  }

  public synchronized void reset() {
    packetQueue.clear();
    started = false;
    lastDequeuedSequenceNumber = C.INDEX_UNSET;
    lastReceivedSequenceNumber = C.INDEX_UNSET;
  }

  /**
   * Offer one packet to the reordering queue.
   *
   * <p>A packet will not be added to the queue, if a logically preceding packet has already been
   * dequeued.
   *
   * <p>If a packet creates a shift in sequence number that is at least {@link
   * #MAX_SEQUENCE_LEAP_ALLOWED} compared to the last offered packet, the queue is emptied and then
   * the packet is added.
   *
   * @param packet The packet to add.
   * @param receivedTimestampMs The timestamp in milliseconds, at which the packet was received.
   * @return Returns {@code false} if the packet was dropped because it was outside the expected
   *     range of accepted packets, otherwise {@code true} (on duplicated packets, this method
   *     returns {@code true}).
   */
  public synchronized boolean offer(RtpPacket packet, long receivedTimestampMs) {
    if (packetQueue.size() >= QUEUE_SIZE_THRESHOLD_FOR_RESET) {
      throw new IllegalStateException(
          "Queue size limit of " + QUEUE_SIZE_THRESHOLD_FOR_RESET + " reached.");
    }

    int packetSequenceNumber = packet.sequenceNumber;
    if (!started) {
      reset();
      lastDequeuedSequenceNumber = RtpPacket.getPreviousSequenceNumber(packetSequenceNumber);
      started = true;
      addToQueue(new RtpPacketContainer(packet, receivedTimestampMs));
      return true;
    }

    int expectedSequenceNumber = RtpPacket.getNextSequenceNumber(lastReceivedSequenceNumber);
    // A positive shift means the packet succeeds the last received packet.
    int sequenceNumberShift =
        calculateSequenceNumberShift(packetSequenceNumber, expectedSequenceNumber);
    if (abs(sequenceNumberShift) < MAX_SEQUENCE_LEAP_ALLOWED) {
      if (calculateSequenceNumberShift(packetSequenceNumber, lastDequeuedSequenceNumber) > 0) {
        // Add the packet in the queue only if a succeeding packet has not been dequeued already.
        addToQueue(new RtpPacketContainer(packet, receivedTimestampMs));
        return true;
      }
    } else {
      // Discard all previous received packets and start subsequent receiving from here.
      lastDequeuedSequenceNumber = RtpPacket.getPreviousSequenceNumber(packetSequenceNumber);
      packetQueue.clear();
      addToQueue(new RtpPacketContainer(packet, receivedTimestampMs));
      return true;
    }
    return false;
  }

  /**
   * Polls an {@link RtpPacket} from the queue.
   *
   * @param cutoffTimestampMs A cutoff timestamp in milliseconds used to determine if the head of
   *     the queue should be dequeued, even if it's not the next packet in sequence.
   * @return Returns a packet if the packet at the queue head is the next packet in sequence; or its
   *     {@link #offer received} timestamp is before {@code cutoffTimestampMs}. Otherwise {@code
   *     null}.
   */
  @Nullable
  public synchronized RtpPacket poll(long cutoffTimestampMs) {
    if (packetQueue.isEmpty()) {
      return null;
    }

    RtpPacketContainer packetContainer = packetQueue.first();
    int packetSequenceNumber = packetContainer.packet.sequenceNumber;

    if (packetSequenceNumber == RtpPacket.getNextSequenceNumber(lastDequeuedSequenceNumber)
        || cutoffTimestampMs >= packetContainer.receivedTimestampMs) {
      packetQueue.pollFirst();
      lastDequeuedSequenceNumber = packetSequenceNumber;
      return packetContainer.packet;
    }

    return null;
  }

  // Internals.

  private synchronized void addToQueue(RtpPacketContainer packet) {
    lastReceivedSequenceNumber = packet.packet.sequenceNumber;
    packetQueue.add(packet);
  }

  private static final class RtpPacketContainer {
    public final RtpPacket packet;
    public final long receivedTimestampMs;

    /** Creates an instance. */
    public RtpPacketContainer(RtpPacket packet, long receivedTimestampMs) {
      this.packet = packet;
      this.receivedTimestampMs = receivedTimestampMs;
    }
  }

  /**
   * Calculates the sequence number shift, accounting for wrapping around.
   *
   * @param sequenceNumber The currently received sequence number.
   * @param previousSequenceNumber The previous sequence number to compare against.
   * @return The shift in the sequence numbers. A positive shift indicates that {@code
   *     sequenceNumber} is logically after {@code previousSequenceNumber}, whereas a negative shift
   *     means that {@code sequenceNumber} is logically before {@code previousSequenceNumber}.
   */
  private static int calculateSequenceNumberShift(int sequenceNumber, int previousSequenceNumber) {
    int sequenceShift = sequenceNumber - previousSequenceNumber;
    if (abs(sequenceShift) > MAX_SEQUENCE_LEAP_ALLOWED) {
      int shift =
          min(sequenceNumber, previousSequenceNumber)
              - max(sequenceNumber, previousSequenceNumber)
              + RtpPacket.MAX_SEQUENCE_NUMBER;
      // Check whether this is actually an wrap-over. For example, it is a wrap around if receiving
      // 65500 (prevSequenceNumber) after 1 (sequenceNumber); but it is not when prevSequenceNumber
      // is 30000.
      if (shift < MAX_SEQUENCE_LEAP_ALLOWED) {
        return sequenceNumber < previousSequenceNumber
            ? /* receiving 65000 (curr) then 1 (prev) */ shift
            : /* receiving 1 (curr) then 65500 (prev) */ -shift;
      }
    }
    return sequenceShift;
  }
}
