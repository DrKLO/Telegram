/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "net/dcsctp/socket/stream_reset_handler.h"

#include <cstdint>
#include <memory>
#include <utility>
#include <vector>

#include "absl/types/optional.h"
#include "api/array_view.h"
#include "api/units/time_delta.h"
#include "net/dcsctp/common/internal_types.h"
#include "net/dcsctp/packet/chunk/reconfig_chunk.h"
#include "net/dcsctp/packet/parameter/add_incoming_streams_request_parameter.h"
#include "net/dcsctp/packet/parameter/add_outgoing_streams_request_parameter.h"
#include "net/dcsctp/packet/parameter/incoming_ssn_reset_request_parameter.h"
#include "net/dcsctp/packet/parameter/outgoing_ssn_reset_request_parameter.h"
#include "net/dcsctp/packet/parameter/parameter.h"
#include "net/dcsctp/packet/parameter/reconfiguration_response_parameter.h"
#include "net/dcsctp/packet/parameter/ssn_tsn_reset_request_parameter.h"
#include "net/dcsctp/packet/sctp_packet.h"
#include "net/dcsctp/packet/tlv_trait.h"
#include "net/dcsctp/public/dcsctp_socket.h"
#include "net/dcsctp/rx/data_tracker.h"
#include "net/dcsctp/rx/reassembly_queue.h"
#include "net/dcsctp/socket/context.h"
#include "net/dcsctp/timer/timer.h"
#include "net/dcsctp/tx/retransmission_queue.h"
#include "rtc_base/logging.h"
#include "rtc_base/strings/str_join.h"

namespace dcsctp {
namespace {
using ::webrtc::TimeDelta;
using ResponseResult = ReconfigurationResponseParameter::Result;

bool DescriptorsAre(const std::vector<ParameterDescriptor>& c,
                    uint16_t e1,
                    uint16_t e2) {
  return (c[0].type == e1 && c[1].type == e2) ||
         (c[0].type == e2 && c[1].type == e1);
}

}  // namespace

bool StreamResetHandler::Validate(const ReConfigChunk& chunk) {
  const Parameters& parameters = chunk.parameters();

  // https://tools.ietf.org/html/rfc6525#section-3.1
  // "Note that each RE-CONFIG chunk holds at least one parameter
  // and at most two parameters. Only the following combinations are allowed:"
  std::vector<ParameterDescriptor> descriptors = parameters.descriptors();
  if (descriptors.size() == 1) {
    if ((descriptors[0].type == OutgoingSSNResetRequestParameter::kType) ||
        (descriptors[0].type == IncomingSSNResetRequestParameter::kType) ||
        (descriptors[0].type == SSNTSNResetRequestParameter::kType) ||
        (descriptors[0].type == AddOutgoingStreamsRequestParameter::kType) ||
        (descriptors[0].type == AddIncomingStreamsRequestParameter::kType) ||
        (descriptors[0].type == ReconfigurationResponseParameter::kType)) {
      return true;
    }
  } else if (descriptors.size() == 2) {
    if (DescriptorsAre(descriptors, OutgoingSSNResetRequestParameter::kType,
                       IncomingSSNResetRequestParameter::kType) ||
        DescriptorsAre(descriptors, AddOutgoingStreamsRequestParameter::kType,
                       AddIncomingStreamsRequestParameter::kType) ||
        DescriptorsAre(descriptors, ReconfigurationResponseParameter::kType,
                       OutgoingSSNResetRequestParameter::kType) ||
        DescriptorsAre(descriptors, ReconfigurationResponseParameter::kType,
                       ReconfigurationResponseParameter::kType)) {
      return true;
    }
  }

  RTC_LOG(LS_WARNING) << "Invalid set of RE-CONFIG parameters";
  return false;
}

absl::optional<std::vector<ReconfigurationResponseParameter>>
StreamResetHandler::Process(const ReConfigChunk& chunk) {
  if (!Validate(chunk)) {
    return absl::nullopt;
  }

  std::vector<ReconfigurationResponseParameter> responses;

  for (const ParameterDescriptor& desc : chunk.parameters().descriptors()) {
    switch (desc.type) {
      case OutgoingSSNResetRequestParameter::kType:
        HandleResetOutgoing(desc, responses);
        break;

      case IncomingSSNResetRequestParameter::kType:
        HandleResetIncoming(desc, responses);
        break;

      case ReconfigurationResponseParameter::kType:
        HandleResponse(desc);
        break;
    }
  }

  return responses;
}

void StreamResetHandler::HandleReConfig(ReConfigChunk chunk) {
  absl::optional<std::vector<ReconfigurationResponseParameter>> responses =
      Process(chunk);

  if (!responses.has_value()) {
    ctx_->callbacks().OnError(ErrorKind::kParseFailed,
                              "Failed to parse RE-CONFIG command");
    return;
  }

  if (!responses->empty()) {
    SctpPacket::Builder b = ctx_->PacketBuilder();
    Parameters::Builder params_builder;
    for (const auto& response : *responses) {
      params_builder.Add(response);
    }
    b.Add(ReConfigChunk(params_builder.Build()));
    ctx_->Send(b);
  }
}

bool StreamResetHandler::ValidateReqSeqNbr(
    UnwrappedReconfigRequestSn req_seq_nbr,
    std::vector<ReconfigurationResponseParameter>& responses) {
  if (req_seq_nbr == last_processed_req_seq_nbr_) {
    // https://www.rfc-editor.org/rfc/rfc6525.html#section-5.2.1 "If the
    // received RE-CONFIG chunk contains at least one request and based on the
    // analysis of the Re-configuration Request Sequence Numbers this is the
    // last received RE-CONFIG chunk (i.e., a retransmission), the same
    // RE-CONFIG chunk MUST to be sent back in response, as it was earlier."
    RTC_DLOG(LS_VERBOSE) << log_prefix_ << "req=" << *req_seq_nbr
                         << " already processed, returning result="
                         << ToString(last_processed_req_result_);
    responses.push_back(ReconfigurationResponseParameter(
        req_seq_nbr.Wrap(), last_processed_req_result_));
    return false;
  }

  if (req_seq_nbr != last_processed_req_seq_nbr_.next_value()) {
    // Too old, too new, from wrong association etc.
    // This is expected to happen when handing over a RTCPeerConnection from one
    // server to another. The client will notice this and may decide to close
    // old data channels, which may be sent to the wrong (or both) servers
    // during a handover.
    RTC_DLOG(LS_VERBOSE) << log_prefix_ << "req=" << *req_seq_nbr
                         << " bad seq_nbr";
    responses.push_back(ReconfigurationResponseParameter(
        req_seq_nbr.Wrap(), ResponseResult::kErrorBadSequenceNumber));
    return false;
  }

  return true;
}

void StreamResetHandler::HandleResetOutgoing(
    const ParameterDescriptor& descriptor,
    std::vector<ReconfigurationResponseParameter>& responses) {
  absl::optional<OutgoingSSNResetRequestParameter> req =
      OutgoingSSNResetRequestParameter::Parse(descriptor.data);
  if (!req.has_value()) {
    ctx_->callbacks().OnError(ErrorKind::kParseFailed,
                              "Failed to parse Outgoing Reset command");
    return;
  }

  UnwrappedReconfigRequestSn request_sn =
      incoming_reconfig_request_sn_unwrapper_.Unwrap(
          req->request_sequence_number());

  if (ValidateReqSeqNbr(request_sn, responses)) {
    last_processed_req_seq_nbr_ = request_sn;
    if (data_tracker_->IsLaterThanCumulativeAckedTsn(
            req->sender_last_assigned_tsn())) {
      // https://datatracker.ietf.org/doc/html/rfc6525#section-5.2.2
      // E2) "If the Sender's Last Assigned TSN is greater than the cumulative
      // acknowledgment point, then the endpoint MUST enter 'deferred reset
      // processing'."
      reassembly_queue_->EnterDeferredReset(req->sender_last_assigned_tsn(),
                                            req->stream_ids());
      // "If the endpoint enters 'deferred reset processing', it MUST put a
      // Re-configuration Response Parameter into a RE-CONFIG chunk indicating
      // 'In progress' and MUST send the RE-CONFIG chunk.
      last_processed_req_result_ = ResponseResult::kInProgress;
      RTC_DLOG(LS_VERBOSE) << log_prefix_
                           << "Reset outgoing; Sender last_assigned="
                           << *req->sender_last_assigned_tsn()
                           << " - not yet reached -> InProgress";
    } else {
      // https://datatracker.ietf.org/doc/html/rfc6525#section-5.2.2
      // E3) If no stream numbers are listed in the parameter, then all incoming
      // streams MUST be reset to 0 as the next expected SSN. If specific stream
      // numbers are listed, then only these specific streams MUST be reset to
      // 0, and all other non-listed SSNs remain unchanged. E4: Any queued TSNs
      // (queued at step E2) MUST now be released and processed normally.
      reassembly_queue_->ResetStreamsAndLeaveDeferredReset(req->stream_ids());
      ctx_->callbacks().OnIncomingStreamsReset(req->stream_ids());
      last_processed_req_result_ = ResponseResult::kSuccessPerformed;

      RTC_DLOG(LS_VERBOSE) << log_prefix_
                           << "Reset outgoing; Sender last_assigned="
                           << *req->sender_last_assigned_tsn()
                           << " - reached -> SuccessPerformed";
    }
    responses.push_back(ReconfigurationResponseParameter(
        req->request_sequence_number(), last_processed_req_result_));
  }
}

void StreamResetHandler::HandleResetIncoming(
    const ParameterDescriptor& descriptor,
    std::vector<ReconfigurationResponseParameter>& responses) {
  absl::optional<IncomingSSNResetRequestParameter> req =
      IncomingSSNResetRequestParameter::Parse(descriptor.data);
  if (!req.has_value()) {
    ctx_->callbacks().OnError(ErrorKind::kParseFailed,
                              "Failed to parse Incoming Reset command");
    return;
  }

  UnwrappedReconfigRequestSn request_sn =
      incoming_reconfig_request_sn_unwrapper_.Unwrap(
          req->request_sequence_number());

  if (ValidateReqSeqNbr(request_sn, responses)) {
    responses.push_back(ReconfigurationResponseParameter(
        req->request_sequence_number(), ResponseResult::kSuccessNothingToDo));
    last_processed_req_seq_nbr_ = request_sn;
  }
}

void StreamResetHandler::HandleResponse(const ParameterDescriptor& descriptor) {
  absl::optional<ReconfigurationResponseParameter> resp =
      ReconfigurationResponseParameter::Parse(descriptor.data);
  if (!resp.has_value()) {
    ctx_->callbacks().OnError(
        ErrorKind::kParseFailed,
        "Failed to parse Reconfiguration Response command");
    return;
  }

  if (current_request_.has_value() && current_request_->has_been_sent() &&
      resp->response_sequence_number() == current_request_->req_seq_nbr()) {
    reconfig_timer_->Stop();

    switch (resp->result()) {
      case ResponseResult::kSuccessNothingToDo:
      case ResponseResult::kSuccessPerformed:
        RTC_DLOG(LS_VERBOSE)
            << log_prefix_ << "Reset stream success, req_seq_nbr="
            << *current_request_->req_seq_nbr() << ", streams="
            << StrJoin(current_request_->streams(), ",",
                       [](rtc::StringBuilder& sb, StreamID stream_id) {
                         sb << *stream_id;
                       });
        ctx_->callbacks().OnStreamsResetPerformed(current_request_->streams());
        current_request_ = absl::nullopt;
        retransmission_queue_->CommitResetStreams();
        break;
      case ResponseResult::kInProgress:
        RTC_DLOG(LS_VERBOSE)
            << log_prefix_ << "Reset stream still pending, req_seq_nbr="
            << *current_request_->req_seq_nbr() << ", streams="
            << StrJoin(current_request_->streams(), ",",
                       [](rtc::StringBuilder& sb, StreamID stream_id) {
                         sb << *stream_id;
                       });
        // Force this request to be sent again, but with new req_seq_nbr.
        current_request_->PrepareRetransmission();
        reconfig_timer_->set_duration(ctx_->current_rto());
        reconfig_timer_->Start();
        break;
      case ResponseResult::kErrorRequestAlreadyInProgress:
      case ResponseResult::kDenied:
      case ResponseResult::kErrorWrongSSN:
      case ResponseResult::kErrorBadSequenceNumber:
        RTC_DLOG(LS_WARNING)
            << log_prefix_ << "Reset stream error=" << ToString(resp->result())
            << ", req_seq_nbr=" << *current_request_->req_seq_nbr()
            << ", streams="
            << StrJoin(current_request_->streams(), ",",
                       [](rtc::StringBuilder& sb, StreamID stream_id) {
                         sb << *stream_id;
                       });
        ctx_->callbacks().OnStreamsResetFailed(current_request_->streams(),
                                               ToString(resp->result()));
        current_request_ = absl::nullopt;
        retransmission_queue_->RollbackResetStreams();
        break;
    }
  }
}

absl::optional<ReConfigChunk> StreamResetHandler::MakeStreamResetRequest() {
  // Only send stream resets if there are streams to reset, and no current
  // ongoing request (there can only be one at a time), and if the stream
  // can be reset.
  if (current_request_.has_value() ||
      !retransmission_queue_->HasStreamsReadyToBeReset()) {
    return absl::nullopt;
  }

  current_request_.emplace(retransmission_queue_->last_assigned_tsn(),
                           retransmission_queue_->BeginResetStreams());
  reconfig_timer_->set_duration(ctx_->current_rto());
  reconfig_timer_->Start();
  return MakeReconfigChunk();
}

ReConfigChunk StreamResetHandler::MakeReconfigChunk() {
  // The req_seq_nbr will be empty if the request has never been sent before,
  // or if it was sent, but the sender responded "in progress", and then the
  // req_seq_nbr will be cleared to re-send with a new number. But if the
  // request is re-sent due to timeout (reconfig-timer expiring), the same
  // req_seq_nbr will be used.
  RTC_DCHECK(current_request_.has_value());

  if (!current_request_->has_been_sent()) {
    current_request_->PrepareToSend(next_outgoing_req_seq_nbr_);
    next_outgoing_req_seq_nbr_ =
        ReconfigRequestSN(*next_outgoing_req_seq_nbr_ + 1);
  }

  Parameters::Builder params_builder =
      Parameters::Builder().Add(OutgoingSSNResetRequestParameter(
          current_request_->req_seq_nbr(), current_request_->req_seq_nbr(),
          current_request_->sender_last_assigned_tsn(),
          current_request_->streams()));

  return ReConfigChunk(params_builder.Build());
}

void StreamResetHandler::ResetStreams(
    rtc::ArrayView<const StreamID> outgoing_streams) {
  for (StreamID stream_id : outgoing_streams) {
    retransmission_queue_->PrepareResetStream(stream_id);
  }
}

TimeDelta StreamResetHandler::OnReconfigTimerExpiry() {
  if (current_request_->has_been_sent()) {
    // There is an outstanding request, which timed out while waiting for a
    // response.
    if (!ctx_->IncrementTxErrorCounter("RECONFIG timeout")) {
      // Timed out. The connection will close after processing the timers.
      return TimeDelta::Zero();
    }
  } else {
    // There is no outstanding request, but there is a prepared one. This means
    // that the receiver has previously responded "in progress", which resulted
    // in retrying the request (but with a new req_seq_nbr) after a while.
  }

  ctx_->Send(ctx_->PacketBuilder().Add(MakeReconfigChunk()));
  return ctx_->current_rto();
}

HandoverReadinessStatus StreamResetHandler::GetHandoverReadiness() const {
  HandoverReadinessStatus status;
  if (retransmission_queue_->HasStreamsReadyToBeReset()) {
    status.Add(HandoverUnreadinessReason::kPendingStreamReset);
  }
  if (current_request_.has_value()) {
    status.Add(HandoverUnreadinessReason::kPendingStreamResetRequest);
  }
  return status;
}

void StreamResetHandler::AddHandoverState(DcSctpSocketHandoverState& state) {
  state.rx.last_completed_reset_req_sn =
      last_processed_req_seq_nbr_.Wrap().value();
  state.tx.next_reset_req_sn = next_outgoing_req_seq_nbr_.value();
}

}  // namespace dcsctp
