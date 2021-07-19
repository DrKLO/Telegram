/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#ifndef NET_DCSCTP_RX_DATA_TRACKER_H_
#define NET_DCSCTP_RX_DATA_TRACKER_H_

#include <stddef.h>
#include <stdint.h>

#include <cstdint>
#include <set>
#include <string>
#include <vector>

#include "absl/strings/string_view.h"
#include "net/dcsctp/common/sequence_numbers.h"
#include "net/dcsctp/packet/chunk/data_common.h"
#include "net/dcsctp/packet/chunk/sack_chunk.h"
#include "net/dcsctp/packet/data.h"
#include "net/dcsctp/timer/timer.h"

namespace dcsctp {

// Keeps track of received DATA chunks and handles all logic for _when_ to
// create SACKs and also _how_ to generate them.
//
// It only uses TSNs to track delivery and doesn't need to be aware of streams.
//
// SACKs are optimally sent every second packet on connections with no packet
// loss. When packet loss is detected, it's sent for every packet. When SACKs
// are not sent directly, a timer is used to send a SACK delayed (by RTO/2, or
// 200ms, whatever is smallest).
class DataTracker {
 public:
  // The maximum number of accepted in-flight DATA chunks. This indicates the
  // maximum difference from this buffer's last cumulative ack TSN, and any
  // received data. Data received beyond this limit will be dropped, which will
  // force the transmitter to send data that actually increases the last
  // cumulative acked TSN.
  static constexpr uint32_t kMaxAcceptedOutstandingFragments = 256;

  explicit DataTracker(absl::string_view log_prefix,
                       Timer* delayed_ack_timer,
                       TSN peer_initial_tsn)
      : log_prefix_(std::string(log_prefix) + "dtrack: "),
        delayed_ack_timer_(*delayed_ack_timer),
        last_cumulative_acked_tsn_(
            tsn_unwrapper_.Unwrap(TSN(*peer_initial_tsn - 1))) {}

  // Indicates if the provided TSN is valid. If this return false, the data
  // should be dropped and not added to any other buffers, which essentially
  // means that there is intentional packet loss.
  bool IsTSNValid(TSN tsn) const;

  // Call for every incoming data chunk.
  void Observe(TSN tsn,
               AnyDataChunk::ImmediateAckFlag immediate_ack =
                   AnyDataChunk::ImmediateAckFlag(false));
  // Called at the end of processing an SCTP packet.
  void ObservePacketEnd();

  // Called for incoming FORWARD-TSN/I-FORWARD-TSN chunks
  void HandleForwardTsn(TSN new_cumulative_ack);

  // Indicates if a SACK should be sent. There may be other reasons to send a
  // SACK, but if this function indicates so, it should be sent as soon as
  // possible. Calling this function will make it clear a flag so that if it's
  // called again, it will probably return false.
  //
  // If the delayed ack timer is running, this method will return false _unless_
  // `also_if_delayed` is set to true. Then it will return true as well.
  bool ShouldSendAck(bool also_if_delayed = false);

  // Returns the last cumulative ack TSN - the last seen data chunk's TSN
  // value before any packet loss was detected.
  TSN last_cumulative_acked_tsn() const {
    return TSN(last_cumulative_acked_tsn_.Wrap());
  }

  // Returns true if the received `tsn` would increase the cumulative ack TSN.
  bool will_increase_cum_ack_tsn(TSN tsn) const;

  // Forces `ShouldSendSack` to return true.
  void ForceImmediateSack();

  // Note that this will clear `duplicates_`, so every SackChunk that is
  // consumed must be sent.
  SackChunk CreateSelectiveAck(size_t a_rwnd);

  void HandleDelayedAckTimerExpiry();

 private:
  enum class AckState {
    // No need to send an ACK.
    kIdle,

    // Has received data chunks (but not yet end of packet).
    kBecomingDelayed,

    // Has received data chunks and the end of a packet. Delayed ack timer is
    // running and a SACK will be sent on expiry, or if DATA is sent, or after
    // next packet with data.
    kDelayed,

    // Send a SACK immediately after handling this packet.
    kImmediate,
  };
  std::vector<SackChunk::GapAckBlock> CreateGapAckBlocks() const;
  void UpdateAckState(AckState new_state, absl::string_view reason);
  static absl::string_view ToString(AckState ack_state);

  const std::string log_prefix_;
  // If a packet has ever been seen.
  bool seen_packet_ = false;
  Timer& delayed_ack_timer_;
  AckState ack_state_ = AckState::kIdle;
  UnwrappedTSN::Unwrapper tsn_unwrapper_;

  // All TSNs up until (and including) this value have been seen.
  UnwrappedTSN last_cumulative_acked_tsn_;
  // Received TSNs that are not directly following `last_cumulative_acked_tsn_`.
  std::set<UnwrappedTSN> additional_tsns_;
  std::set<TSN> duplicate_tsns_;
};
}  // namespace dcsctp

#endif  // NET_DCSCTP_RX_DATA_TRACKER_H_
