/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "net/dcsctp/public/dcsctp_handover_state.h"

#include <string>

#include "absl/strings/string_view.h"

namespace dcsctp {
namespace {
constexpr absl::string_view HandoverUnreadinessReasonToString(
    HandoverUnreadinessReason reason) {
  switch (reason) {
    case HandoverUnreadinessReason::kWrongConnectionState:
      return "WRONG_CONNECTION_STATE";
    case HandoverUnreadinessReason::kSendQueueNotEmpty:
      return "SEND_QUEUE_NOT_EMPTY";
    case HandoverUnreadinessReason::kDataTrackerTsnBlocksPending:
      return "DATA_TRACKER_TSN_BLOCKS_PENDING";
    case HandoverUnreadinessReason::kReassemblyQueueDeliveredTSNsGap:
      return "REASSEMBLY_QUEUE_DELIVERED_TSN_GAP";
    case HandoverUnreadinessReason::kStreamResetDeferred:
      return "STREAM_RESET_DEFERRED";
    case HandoverUnreadinessReason::kOrderedStreamHasUnassembledChunks:
      return "ORDERED_STREAM_HAS_UNASSEMBLED_CHUNKS";
    case HandoverUnreadinessReason::kUnorderedStreamHasUnassembledChunks:
      return "UNORDERED_STREAM_HAS_UNASSEMBLED_CHUNKS";
    case HandoverUnreadinessReason::kRetransmissionQueueOutstandingData:
      return "RETRANSMISSION_QUEUE_OUTSTANDING_DATA";
    case HandoverUnreadinessReason::kRetransmissionQueueFastRecovery:
      return "RETRANSMISSION_QUEUE_FAST_RECOVERY";
    case HandoverUnreadinessReason::kRetransmissionQueueNotEmpty:
      return "RETRANSMISSION_QUEUE_NOT_EMPTY";
    case HandoverUnreadinessReason::kPendingStreamReset:
      return "PENDING_STREAM_RESET";
    case HandoverUnreadinessReason::kPendingStreamResetRequest:
      return "PENDING_STREAM_RESET_REQUEST";
  }
}
}  // namespace

std::string HandoverReadinessStatus::ToString() const {
  std::string result;
  for (uint32_t bit = 1;
       bit <= static_cast<uint32_t>(HandoverUnreadinessReason::kMax);
       bit *= 2) {
    auto flag = static_cast<HandoverUnreadinessReason>(bit);
    if (Contains(flag)) {
      if (!result.empty()) {
        result.append(",");
      }
      absl::string_view s = HandoverUnreadinessReasonToString(flag);
      result.append(s.data(), s.size());
    }
  }
  if (result.empty()) {
    result = "READY";
  }
  return result;
}
}  // namespace dcsctp
