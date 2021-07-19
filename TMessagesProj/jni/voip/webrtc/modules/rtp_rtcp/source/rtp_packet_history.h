/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_RTP_RTCP_SOURCE_RTP_PACKET_HISTORY_H_
#define MODULES_RTP_RTCP_SOURCE_RTP_PACKET_HISTORY_H_

#include <deque>
#include <map>
#include <memory>
#include <set>
#include <vector>

#include "api/function_view.h"
#include "modules/rtp_rtcp/include/rtp_rtcp_defines.h"
#include "rtc_base/synchronization/mutex.h"
#include "rtc_base/thread_annotations.h"

namespace webrtc {

class Clock;
class RtpPacketToSend;

class RtpPacketHistory {
 public:
  enum class StorageMode {
    kDisabled,     // Don't store any packets.
    kStoreAndCull  // Store up to |number_to_store| packets, but try to remove
                   // packets as they time out or as signaled as received.
  };

  // Snapshot indicating the state of a packet in the history.
  struct PacketState {
    PacketState();
    PacketState(const PacketState&);
    ~PacketState();

    uint16_t rtp_sequence_number = 0;
    absl::optional<int64_t> send_time_ms;
    int64_t capture_time_ms = 0;
    uint32_t ssrc = 0;
    size_t packet_size = 0;
    // Number of times RE-transmitted, ie not including the first transmission.
    size_t times_retransmitted = 0;
    bool pending_transmission = false;
  };

  // Maximum number of packets we ever allow in the history.
  static constexpr size_t kMaxCapacity = 9600;
  // Maximum number of entries in prioritized queue of padding packets.
  static constexpr size_t kMaxPaddingtHistory = 63;
  // Don't remove packets within max(1000ms, 3x RTT).
  static constexpr int64_t kMinPacketDurationMs = 1000;
  static constexpr int kMinPacketDurationRtt = 3;
  // With kStoreAndCull, always remove packets after 3x max(1000ms, 3x rtt).
  static constexpr int kPacketCullingDelayFactor = 3;

  RtpPacketHistory(Clock* clock, bool enable_padding_prio);

  RtpPacketHistory() = delete;
  RtpPacketHistory(const RtpPacketHistory&) = delete;
  RtpPacketHistory& operator=(const RtpPacketHistory&) = delete;

  ~RtpPacketHistory();

  // Set/get storage mode. Note that setting the state will clear the history,
  // even if setting the same state as is currently used.
  void SetStorePacketsStatus(StorageMode mode, size_t number_to_store);
  StorageMode GetStorageMode() const;

  // Set RTT, used to avoid premature retransmission and to prevent over-writing
  // a packet in the history before we are reasonably sure it has been received.
  void SetRtt(int64_t rtt_ms);

  // If |send_time| is set, packet was sent without using pacer, so state will
  // be set accordingly.
  void PutRtpPacket(std::unique_ptr<RtpPacketToSend> packet,
                    absl::optional<int64_t> send_time_ms);

  // Gets stored RTP packet corresponding to the input |sequence number|.
  // Returns nullptr if packet is not found or was (re)sent too recently.
  std::unique_ptr<RtpPacketToSend> GetPacketAndSetSendTime(
      uint16_t sequence_number);

  // Gets stored RTP packet corresponding to the input |sequence number|.
  // Returns nullptr if packet is not found or was (re)sent too recently.
  // If a packet copy is returned, it will be marked as pending transmission but
  // does not update send time, that must be done by MarkPacketAsSent().
  std::unique_ptr<RtpPacketToSend> GetPacketAndMarkAsPending(
      uint16_t sequence_number);

  // In addition to getting packet and marking as sent, this method takes an
  // encapsulator function that takes a reference to the packet and outputs a
  // copy that may be wrapped in a container, eg RTX.
  // If the the encapsulator returns nullptr, the retransmit is aborted and the
  // packet will not be marked as pending.
  std::unique_ptr<RtpPacketToSend> GetPacketAndMarkAsPending(
      uint16_t sequence_number,
      rtc::FunctionView<std::unique_ptr<RtpPacketToSend>(
          const RtpPacketToSend&)> encapsulate);

  // Updates the send time for the given packet and increments the transmission
  // counter. Marks the packet as no longer being in the pacer queue.
  void MarkPacketAsSent(uint16_t sequence_number);

  // Similar to GetPacketAndSetSendTime(), but only returns a snapshot of the
  // current state for packet, and never updates internal state.
  absl::optional<PacketState> GetPacketState(uint16_t sequence_number) const;

  // Get the packet (if any) from the history, that is deemed most likely to
  // the remote side. This is calculated from heuristics such as packet age
  // and times retransmitted. Updated the send time of the packet, so is not
  // a const method.
  std::unique_ptr<RtpPacketToSend> GetPayloadPaddingPacket();

  // Same as GetPayloadPaddingPacket(void), but adds an encapsulation
  // that can be used for instance to encapsulate the packet in an RTX
  // container, or to abort getting the packet if the function returns
  // nullptr.
  std::unique_ptr<RtpPacketToSend> GetPayloadPaddingPacket(
      rtc::FunctionView<std::unique_ptr<RtpPacketToSend>(
          const RtpPacketToSend&)> encapsulate);

  // Cull packets that have been acknowledged as received by the remote end.
  void CullAcknowledgedPackets(rtc::ArrayView<const uint16_t> sequence_numbers);

  // Mark packet as queued for transmission. This will prevent premature
  // removal or duplicate retransmissions in the pacer queue.
  // Returns true if status was set, false if packet was not found.
  bool SetPendingTransmission(uint16_t sequence_number);

  // Remove all pending packets from the history, but keep storage mode and
  // capacity.
  void Clear();

 private:
  struct MoreUseful;
  class StoredPacket;
  using PacketPrioritySet = std::set<StoredPacket*, MoreUseful>;

  class StoredPacket {
   public:
    StoredPacket(std::unique_ptr<RtpPacketToSend> packet,
                 absl::optional<int64_t> send_time_ms,
                 uint64_t insert_order);
    StoredPacket(StoredPacket&&);
    StoredPacket& operator=(StoredPacket&&);
    ~StoredPacket();

    uint64_t insert_order() const { return insert_order_; }
    size_t times_retransmitted() const { return times_retransmitted_; }
    void IncrementTimesRetransmitted(PacketPrioritySet* priority_set);

    // The time of last transmission, including retransmissions.
    absl::optional<int64_t> send_time_ms_;

    // The actual packet.
    std::unique_ptr<RtpPacketToSend> packet_;

    // True if the packet is currently in the pacer queue pending transmission.
    bool pending_transmission_;

   private:
    // Unique number per StoredPacket, incremented by one for each added
    // packet. Used to sort on insert order.
    uint64_t insert_order_;

    // Number of times RE-transmitted, ie excluding the first transmission.
    size_t times_retransmitted_;
  };
  struct MoreUseful {
    bool operator()(StoredPacket* lhs, StoredPacket* rhs) const;
  };

  // Helper method used by GetPacketAndSetSendTime() and GetPacketState() to
  // check if packet has too recently been sent.
  bool VerifyRtt(const StoredPacket& packet, int64_t now_ms) const
      RTC_EXCLUSIVE_LOCKS_REQUIRED(lock_);
  void Reset() RTC_EXCLUSIVE_LOCKS_REQUIRED(lock_);
  void CullOldPackets(int64_t now_ms) RTC_EXCLUSIVE_LOCKS_REQUIRED(lock_);
  // Removes the packet from the history, and context/mapping that has been
  // stored. Returns the RTP packet instance contained within the StoredPacket.
  std::unique_ptr<RtpPacketToSend> RemovePacket(int packet_index)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(lock_);
  int GetPacketIndex(uint16_t sequence_number) const
      RTC_EXCLUSIVE_LOCKS_REQUIRED(lock_);
  StoredPacket* GetStoredPacket(uint16_t sequence_number)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(lock_);
  static PacketState StoredPacketToPacketState(
      const StoredPacket& stored_packet);

  Clock* const clock_;
  const bool enable_padding_prio_;
  mutable Mutex lock_;
  size_t number_to_store_ RTC_GUARDED_BY(lock_);
  StorageMode mode_ RTC_GUARDED_BY(lock_);
  int64_t rtt_ms_ RTC_GUARDED_BY(lock_);

  // Queue of stored packets, ordered by sequence number, with older packets in
  // the front and new packets being added to the back. Note that there may be
  // wrap-arounds so the back may have a lower sequence number.
  // Packets may also be removed out-of-order, in which case there will be
  // instances of StoredPacket with |packet_| set to nullptr. The first and last
  // entry in the queue will however always be populated.
  std::deque<StoredPacket> packet_history_ RTC_GUARDED_BY(lock_);

  // Total number of packets with inserted.
  uint64_t packets_inserted_ RTC_GUARDED_BY(lock_);
  // Objects from |packet_history_| ordered by "most likely to be useful", used
  // in GetPayloadPaddingPacket().
  PacketPrioritySet padding_priority_ RTC_GUARDED_BY(lock_);
};
}  // namespace webrtc
#endif  // MODULES_RTP_RTCP_SOURCE_RTP_PACKET_HISTORY_H_
