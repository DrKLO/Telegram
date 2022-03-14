/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#ifndef NET_DCSCTP_TX_OUTSTANDING_DATA_H_
#define NET_DCSCTP_TX_OUTSTANDING_DATA_H_

#include <map>
#include <set>
#include <utility>
#include <vector>

#include "absl/types/optional.h"
#include "net/dcsctp/common/sequence_numbers.h"
#include "net/dcsctp/packet/chunk/forward_tsn_chunk.h"
#include "net/dcsctp/packet/chunk/iforward_tsn_chunk.h"
#include "net/dcsctp/packet/chunk/sack_chunk.h"
#include "net/dcsctp/packet/data.h"

namespace dcsctp {

// This class keeps track of outstanding data chunks (sent, not yet acked) and
// handles acking, nacking, rescheduling and abandoning.
class OutstandingData {
 public:
  // State for DATA chunks (message fragments) in the queue - used in tests.
  enum class State {
    // The chunk has been sent but not received yet (from the sender's point of
    // view, as no SACK has been received yet that reference this chunk).
    kInFlight,
    // A SACK has been received which explicitly marked this chunk as missing -
    // it's now NACKED and may be retransmitted if NACKED enough times.
    kNacked,
    // A chunk that will be retransmitted when possible.
    kToBeRetransmitted,
    // A SACK has been received which explicitly marked this chunk as received.
    kAcked,
    // A chunk whose message has expired or has been retransmitted too many
    // times (RFC3758). It will not be retransmitted anymore.
    kAbandoned,
  };

  // Contains variables scoped to a processing of an incoming SACK.
  struct AckInfo {
    explicit AckInfo(UnwrappedTSN cumulative_tsn_ack)
        : highest_tsn_acked(cumulative_tsn_ack) {}

    // Bytes acked by increasing cumulative_tsn_ack and gap_ack_blocks.
    size_t bytes_acked = 0;

    // Indicates if this SACK indicates that packet loss has occurred. Just
    // because a packet is missing in the SACK doesn't necessarily mean that
    // there is packet loss as that packet might be in-flight and received
    // out-of-order. But when it has been reported missing consecutive times, it
    // will eventually be considered "lost" and this will be set.
    bool has_packet_loss = false;

    // Highest TSN Newly Acknowledged, an SCTP variable.
    UnwrappedTSN highest_tsn_acked;
  };

  OutstandingData(
      size_t data_chunk_header_size,
      UnwrappedTSN next_tsn,
      UnwrappedTSN last_cumulative_tsn_ack,
      std::function<bool(IsUnordered, StreamID, MID)> discard_from_send_queue)
      : data_chunk_header_size_(data_chunk_header_size),
        next_tsn_(next_tsn),
        last_cumulative_tsn_ack_(last_cumulative_tsn_ack),
        discard_from_send_queue_(std::move(discard_from_send_queue)) {}

  AckInfo HandleSack(
      UnwrappedTSN cumulative_tsn_ack,
      rtc::ArrayView<const SackChunk::GapAckBlock> gap_ack_blocks,
      bool is_in_fast_retransmit);

  // Given `max_size` of space left in a packet, which chunks can be added to
  // it?
  std::vector<std::pair<TSN, Data>> GetChunksToBeRetransmitted(size_t max_size);

  size_t outstanding_bytes() const { return outstanding_bytes_; }

  // Returns the number of DATA chunks that are in-flight.
  size_t outstanding_items() const { return outstanding_items_; }

  // Given the current time `now_ms`, expire and abandon outstanding (sent at
  // least once) chunks that have a limited lifetime.
  void ExpireOutstandingChunks(TimeMs now);

  bool empty() const { return outstanding_data_.empty(); }

  bool has_data_to_be_retransmitted() const {
    return !to_be_retransmitted_.empty();
  }

  UnwrappedTSN last_cumulative_tsn_ack() const {
    return last_cumulative_tsn_ack_;
  }

  UnwrappedTSN next_tsn() const { return next_tsn_; }

  UnwrappedTSN highest_outstanding_tsn() const;

  // Schedules `data` to be sent, with the provided partial reliability
  // parameters. Returns the TSN if the item was actually added and scheduled to
  // be sent, and absl::nullopt if it shouldn't be sent.
  absl::optional<UnwrappedTSN> Insert(const Data& data,
                                      MaxRetransmits max_retransmissions,
                                      TimeMs time_sent,
                                      TimeMs expires_at);

  // Nacks all outstanding data.
  void NackAll();

  // Creates a FORWARD-TSN chunk.
  ForwardTsnChunk CreateForwardTsn() const;

  // Creates an I-FORWARD-TSN chunk.
  IForwardTsnChunk CreateIForwardTsn() const;

  // Given the current time and a TSN, it returns the measured RTT between when
  // the chunk was sent and now. It takes into acccount Karn's algorithm, so if
  // the chunk has ever been retransmitted, it will return absl::nullopt.
  absl::optional<DurationMs> MeasureRTT(TimeMs now, UnwrappedTSN tsn) const;

  // Returns the internal state of all queued chunks. This is only used in
  // unit-tests.
  std::vector<std::pair<TSN, State>> GetChunkStatesForTesting() const;

  // Returns true if the next chunk that is not acked by the peer has been
  // abandoned, which means that a FORWARD-TSN should be sent.
  bool ShouldSendForwardTsn() const;

 private:
  // A fragmented message's DATA chunk while in the retransmission queue, and
  // its associated metadata.
  class Item {
   public:
    enum class NackAction {
      kNothing,
      kRetransmit,
      kAbandon,
    };

    explicit Item(Data data,
                  MaxRetransmits max_retransmissions,
                  TimeMs time_sent,
                  TimeMs expires_at)
        : max_retransmissions_(max_retransmissions),
          time_sent_(time_sent),
          expires_at_(expires_at),
          data_(std::move(data)) {}

    TimeMs time_sent() const { return time_sent_; }

    const Data& data() const { return data_; }

    // Acks an item.
    void Ack();

    // Nacks an item. If it has been nacked enough times, or if `retransmit_now`
    // is set, it might be marked for retransmission. If the item has reached
    // its max retransmission value, it will instead be abandoned. The action
    // performed is indicated as return value.
    NackAction Nack(bool retransmit_now = false);

    // Prepares the item to be retransmitted. Sets it as outstanding and
    // clears all nack counters.
    void Retransmit();

    // Marks this item as abandoned.
    void Abandon();

    bool is_outstanding() const { return ack_state_ == AckState::kUnacked; }
    bool is_acked() const { return ack_state_ == AckState::kAcked; }
    bool is_nacked() const { return ack_state_ == AckState::kNacked; }
    bool is_abandoned() const { return is_abandoned_; }

    // Indicates if this chunk should be retransmitted.
    bool should_be_retransmitted() const { return should_be_retransmitted_; }
    // Indicates if this chunk has ever been retransmitted.
    bool has_been_retransmitted() const { return num_retransmissions_ > 0; }

    // Given the current time, and the current state of this DATA chunk, it will
    // indicate if it has expired (SCTP Partial Reliability Extension).
    bool has_expired(TimeMs now) const;

   private:
    enum class AckState {
      kUnacked,
      kAcked,
      kNacked,
    };
    // Indicates the presence of this chunk, if it's in flight (Unacked), has
    // been received (Acked) or is lost (Nacked).
    AckState ack_state_ = AckState::kUnacked;
    // Indicates if this chunk has been abandoned, which is a terminal state.
    bool is_abandoned_ = false;
    // Indicates if this chunk should be retransmitted.
    bool should_be_retransmitted_ = false;

    // The number of times the DATA chunk has been nacked (by having received a
    // SACK which doesn't include it). Will be cleared on retransmissions.
    uint8_t nack_count_ = 0;
    // The number of times the DATA chunk has been retransmitted.
    uint16_t num_retransmissions_ = 0;
    // If the message was sent with a maximum number of retransmissions, this is
    // set to that number. The value zero (0) means that it will never be
    // retransmitted.
    const MaxRetransmits max_retransmissions_;
    // When the packet was sent, and placed in this queue.
    const TimeMs time_sent_;
    // At this exact millisecond, the item is considered expired. If the message
    // is not to be expired, this is set to the infinite future.
    const TimeMs expires_at_;
    // The actual data to send/retransmit.
    Data data_;
  };

  // Returns how large a chunk will be, serialized, carrying the data
  size_t GetSerializedChunkSize(const Data& data) const;

  // Given a `cumulative_tsn_ack` from an incoming SACK, will remove those items
  // in the retransmission queue up until this value and will update `ack_info`
  // by setting `bytes_acked_by_cumulative_tsn_ack`.
  void RemoveAcked(UnwrappedTSN cumulative_tsn_ack, AckInfo& ack_info);

  // Will mark the chunks covered by the `gap_ack_blocks` from an incoming SACK
  // as "acked" and update `ack_info` by adding new TSNs to `added_tsns`.
  void AckGapBlocks(UnwrappedTSN cumulative_tsn_ack,
                    rtc::ArrayView<const SackChunk::GapAckBlock> gap_ack_blocks,
                    AckInfo& ack_info);

  // Mark chunks reported as "missing", as "nacked" or "to be retransmitted"
  // depending how many times this has happened. Only packets up until
  // `ack_info.highest_tsn_acked` (highest TSN newly acknowledged) are
  // nacked/retransmitted. The method will set `ack_info.has_packet_loss`.
  void NackBetweenAckBlocks(
      UnwrappedTSN cumulative_tsn_ack,
      rtc::ArrayView<const SackChunk::GapAckBlock> gap_ack_blocks,
      bool is_in_fast_recovery,
      OutstandingData::AckInfo& ack_info);

  // Acks the chunk referenced by `iter` and updates state in `ack_info` and the
  // object's state.
  void AckChunk(AckInfo& ack_info, std::map<UnwrappedTSN, Item>::iterator iter);

  // Helper method to nack an item and perform the correct operations given the
  // action indicated when nacking an item (e.g. retransmitting or abandoning).
  // The return value indicate if an action was performed, meaning that packet
  // loss was detected and acted upon.
  bool NackItem(UnwrappedTSN tsn, Item& item, bool retransmit_now);

  // Given that a message fragment, `item` has been abandoned, abandon all other
  // fragments that share the same message - both never-before-sent fragments
  // that are still in the SendQueue and outstanding chunks.
  void AbandonAllFor(const OutstandingData::Item& item);

  bool IsConsistent() const;

  // The size of the data chunk (DATA/I-DATA) header that is used.
  const size_t data_chunk_header_size_;
  // Next TSN to used.
  UnwrappedTSN next_tsn_;
  // The last cumulative TSN ack number.
  UnwrappedTSN last_cumulative_tsn_ack_;
  // Callback when to discard items from the send queue.
  std::function<bool(IsUnordered, StreamID, MID)> discard_from_send_queue_;

  std::map<UnwrappedTSN, Item> outstanding_data_;
  // The number of bytes that are in-flight (sent but not yet acked or nacked).
  size_t outstanding_bytes_ = 0;
  // The number of DATA chunks that are in-flight (sent but not yet acked or
  // nacked).
  size_t outstanding_items_ = 0;
  // Data chunks that are to be retransmitted.
  std::set<UnwrappedTSN> to_be_retransmitted_;
};
}  // namespace dcsctp
#endif  // NET_DCSCTP_TX_OUTSTANDING_DATA_H_
