/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "net/dcsctp/rx/reassembly_queue.h"

#include <stddef.h>

#include <algorithm>
#include <cstdint>
#include <memory>
#include <set>
#include <string>
#include <utility>
#include <vector>

#include "absl/strings/string_view.h"
#include "absl/types/optional.h"
#include "api/array_view.h"
#include "net/dcsctp/common/sequence_numbers.h"
#include "net/dcsctp/common/str_join.h"
#include "net/dcsctp/packet/chunk/forward_tsn_common.h"
#include "net/dcsctp/packet/data.h"
#include "net/dcsctp/packet/parameter/outgoing_ssn_reset_request_parameter.h"
#include "net/dcsctp/packet/parameter/reconfiguration_response_parameter.h"
#include "net/dcsctp/public/dcsctp_message.h"
#include "net/dcsctp/rx/reassembly_streams.h"
#include "net/dcsctp/rx/traditional_reassembly_streams.h"
#include "rtc_base/logging.h"

namespace dcsctp {
ReassemblyQueue::ReassemblyQueue(
    absl::string_view log_prefix,
    TSN peer_initial_tsn,
    size_t max_size_bytes,
    const DcSctpSocketHandoverState* handover_state)
    : log_prefix_(std::string(log_prefix) + "reasm: "),
      max_size_bytes_(max_size_bytes),
      watermark_bytes_(max_size_bytes * kHighWatermarkLimit),
      last_assembled_tsn_watermark_(tsn_unwrapper_.Unwrap(
          handover_state ? TSN(handover_state->rx.last_assembled_tsn)
                         : TSN(*peer_initial_tsn - 1))),
      last_completed_reset_req_seq_nbr_(
          handover_state
              ? ReconfigRequestSN(
                    handover_state->rx.last_completed_deferred_reset_req_sn)
              : ReconfigRequestSN(0)),
      streams_(std::make_unique<TraditionalReassemblyStreams>(
          log_prefix_,
          [this](rtc::ArrayView<const UnwrappedTSN> tsns,
                 DcSctpMessage message) {
            AddReassembledMessage(tsns, std::move(message));
          },
          handover_state)) {}

void ReassemblyQueue::Add(TSN tsn, Data data) {
  RTC_DCHECK(IsConsistent());
  RTC_DLOG(LS_VERBOSE) << log_prefix_ << "added tsn=" << *tsn
                       << ", stream=" << *data.stream_id << ":"
                       << *data.message_id << ":" << *data.fsn << ", type="
                       << (data.is_beginning && data.is_end
                               ? "complete"
                               : data.is_beginning
                                     ? "first"
                                     : data.is_end ? "last" : "middle");

  UnwrappedTSN unwrapped_tsn = tsn_unwrapper_.Unwrap(tsn);

  if (unwrapped_tsn <= last_assembled_tsn_watermark_ ||
      delivered_tsns_.find(unwrapped_tsn) != delivered_tsns_.end()) {
    RTC_DLOG(LS_VERBOSE) << log_prefix_
                         << "Chunk has already been delivered - skipping";
    return;
  }

  // If a stream reset has been received with a "sender's last assigned tsn" in
  // the future, the socket is in "deferred reset processing" mode and must
  // buffer chunks until it's exited.
  if (deferred_reset_streams_.has_value() &&
      unwrapped_tsn >
          tsn_unwrapper_.Unwrap(
              deferred_reset_streams_->req.sender_last_assigned_tsn())) {
    RTC_DLOG(LS_VERBOSE)
        << log_prefix_ << "Deferring chunk with tsn=" << *tsn
        << " until cum_ack_tsn="
        << *deferred_reset_streams_->req.sender_last_assigned_tsn();
    // https://tools.ietf.org/html/rfc6525#section-5.2.2
    // "In this mode, any data arriving with a TSN larger than the
    // Sender's Last Assigned TSN for the affected stream(s) MUST be queued
    // locally and held until the cumulative acknowledgment point reaches the
    // Sender's Last Assigned TSN."
    queued_bytes_ += data.size();
    deferred_reset_streams_->deferred_chunks.emplace_back(
        std::make_pair(tsn, std::move(data)));
  } else {
    queued_bytes_ += streams_->Add(unwrapped_tsn, std::move(data));
  }

  // https://tools.ietf.org/html/rfc4960#section-6.9
  // "Note: If the data receiver runs out of buffer space while still
  // waiting for more fragments to complete the reassembly of the message, it
  // should dispatch part of its inbound message through a partial delivery
  // API (see Section 10), freeing some of its receive buffer space so that
  // the rest of the message may be received."

  // TODO(boivie): Support EOR flag and partial delivery?
  RTC_DCHECK(IsConsistent());
}

ReconfigurationResponseParameter::Result ReassemblyQueue::ResetStreams(
    const OutgoingSSNResetRequestParameter& req,
    TSN cum_tsn_ack) {
  RTC_DCHECK(IsConsistent());
  if (deferred_reset_streams_.has_value()) {
    // In deferred mode already.
    return ReconfigurationResponseParameter::Result::kInProgress;
  } else if (req.request_sequence_number() <=
             last_completed_reset_req_seq_nbr_) {
    // Already performed at some time previously.
    return ReconfigurationResponseParameter::Result::kSuccessPerformed;
  }

  UnwrappedTSN sla_tsn = tsn_unwrapper_.Unwrap(req.sender_last_assigned_tsn());
  UnwrappedTSN unwrapped_cum_tsn_ack = tsn_unwrapper_.Unwrap(cum_tsn_ack);

  // https://tools.ietf.org/html/rfc6525#section-5.2.2
  // "If the Sender's Last Assigned TSN is greater than the
  // cumulative acknowledgment point, then the endpoint MUST enter "deferred
  // reset processing"."
  if (sla_tsn > unwrapped_cum_tsn_ack) {
    RTC_DLOG(LS_VERBOSE)
        << log_prefix_
        << "Entering deferred reset processing mode until cum_tsn_ack="
        << *req.sender_last_assigned_tsn();
    deferred_reset_streams_ = absl::make_optional<DeferredResetStreams>(req);
    return ReconfigurationResponseParameter::Result::kInProgress;
  }

  // https://tools.ietf.org/html/rfc6525#section-5.2.2
  // "... streams MUST be reset to 0 as the next expected SSN."
  streams_->ResetStreams(req.stream_ids());
  last_completed_reset_req_seq_nbr_ = req.request_sequence_number();
  RTC_DCHECK(IsConsistent());
  return ReconfigurationResponseParameter::Result::kSuccessPerformed;
}

bool ReassemblyQueue::MaybeResetStreamsDeferred(TSN cum_ack_tsn) {
  RTC_DCHECK(IsConsistent());
  if (deferred_reset_streams_.has_value()) {
    UnwrappedTSN unwrapped_cum_ack_tsn = tsn_unwrapper_.Unwrap(cum_ack_tsn);
    UnwrappedTSN unwrapped_sla_tsn = tsn_unwrapper_.Unwrap(
        deferred_reset_streams_->req.sender_last_assigned_tsn());
    if (unwrapped_cum_ack_tsn >= unwrapped_sla_tsn) {
      RTC_DLOG(LS_VERBOSE) << log_prefix_
                           << "Leaving deferred reset processing with tsn="
                           << *cum_ack_tsn << ", feeding back "
                           << deferred_reset_streams_->deferred_chunks.size()
                           << " chunks";
      // https://tools.ietf.org/html/rfc6525#section-5.2.2
      // "... streams MUST be reset to 0 as the next expected SSN."
      streams_->ResetStreams(deferred_reset_streams_->req.stream_ids());
      std::vector<std::pair<TSN, Data>> deferred_chunks =
          std::move(deferred_reset_streams_->deferred_chunks);
      // The response will not be sent now, but as a reply to the retried
      // request, which will come as "in progress" has been sent prior.
      last_completed_reset_req_seq_nbr_ =
          deferred_reset_streams_->req.request_sequence_number();
      deferred_reset_streams_ = absl::nullopt;

      // https://tools.ietf.org/html/rfc6525#section-5.2.2
      // "Any queued TSNs (queued at step E2) MUST now be released and processed
      // normally."
      for (auto& p : deferred_chunks) {
        const TSN& tsn = p.first;
        Data& data = p.second;
        queued_bytes_ -= data.size();
        Add(tsn, std::move(data));
      }

      RTC_DCHECK(IsConsistent());
      return true;
    } else {
      RTC_DLOG(LS_VERBOSE) << "Staying in deferred reset processing. tsn="
                           << *cum_ack_tsn;
    }
  }

  return false;
}

std::vector<DcSctpMessage> ReassemblyQueue::FlushMessages() {
  std::vector<DcSctpMessage> ret;
  reassembled_messages_.swap(ret);
  return ret;
}

void ReassemblyQueue::AddReassembledMessage(
    rtc::ArrayView<const UnwrappedTSN> tsns,
    DcSctpMessage message) {
  RTC_DLOG(LS_VERBOSE) << log_prefix_ << "Assembled message from TSN=["
                       << StrJoin(tsns, ",",
                                  [](rtc::StringBuilder& sb, UnwrappedTSN tsn) {
                                    sb << *tsn.Wrap();
                                  })
                       << "], message; stream_id=" << *message.stream_id()
                       << ", ppid=" << *message.ppid()
                       << ", payload=" << message.payload().size() << " bytes";

  for (const UnwrappedTSN tsn : tsns) {
    // Update watermark, or insert into delivered_tsns_
    if (tsn == last_assembled_tsn_watermark_.next_value()) {
      last_assembled_tsn_watermark_.Increment();
    } else {
      delivered_tsns_.insert(tsn);
    }
  }

  // With new TSNs in delivered_tsns, gaps might be filled.
  MaybeMoveLastAssembledWatermarkFurther();

  reassembled_messages_.emplace_back(std::move(message));
}

void ReassemblyQueue::MaybeMoveLastAssembledWatermarkFurther() {
  // `delivered_tsns_` contain TSNS when there is a gap between ranges of
  // assembled TSNs. `last_assembled_tsn_watermark_` should not be adjacent to
  // that list, because if so, it can be moved.
  while (!delivered_tsns_.empty() &&
         *delivered_tsns_.begin() ==
             last_assembled_tsn_watermark_.next_value()) {
    last_assembled_tsn_watermark_.Increment();
    delivered_tsns_.erase(delivered_tsns_.begin());
  }
}

void ReassemblyQueue::Handle(const AnyForwardTsnChunk& forward_tsn) {
  RTC_DCHECK(IsConsistent());
  UnwrappedTSN tsn = tsn_unwrapper_.Unwrap(forward_tsn.new_cumulative_tsn());

  last_assembled_tsn_watermark_ = std::max(last_assembled_tsn_watermark_, tsn);
  delivered_tsns_.erase(delivered_tsns_.begin(),
                        delivered_tsns_.upper_bound(tsn));

  MaybeMoveLastAssembledWatermarkFurther();

  queued_bytes_ -=
      streams_->HandleForwardTsn(tsn, forward_tsn.skipped_streams());
  RTC_DCHECK(IsConsistent());
}

bool ReassemblyQueue::IsConsistent() const {
  // `delivered_tsns_` and `last_assembled_tsn_watermark_` mustn't overlap or be
  // adjacent.
  if (!delivered_tsns_.empty() &&
      last_assembled_tsn_watermark_.next_value() >= *delivered_tsns_.begin()) {
    return false;
  }

  // Allow queued_bytes_ to be larger than max_size_bytes, as it's not actively
  // enforced in this class. This comparison will still trigger if queued_bytes_
  // became "negative".
  return (queued_bytes_ >= 0 && queued_bytes_ <= 2 * max_size_bytes_);
}

HandoverReadinessStatus ReassemblyQueue::GetHandoverReadiness() const {
  HandoverReadinessStatus status = streams_->GetHandoverReadiness();
  if (!delivered_tsns_.empty()) {
    status.Add(HandoverUnreadinessReason::kReassemblyQueueDeliveredTSNsGap);
  }
  if (deferred_reset_streams_.has_value()) {
    status.Add(HandoverUnreadinessReason::kStreamResetDeferred);
  }
  return status;
}

void ReassemblyQueue::AddHandoverState(DcSctpSocketHandoverState& state) {
  state.rx.last_assembled_tsn = last_assembled_tsn_watermark_.Wrap().value();
  state.rx.last_completed_deferred_reset_req_sn =
      last_completed_reset_req_seq_nbr_.value();
  streams_->AddHandoverState(state);
}

}  // namespace dcsctp
