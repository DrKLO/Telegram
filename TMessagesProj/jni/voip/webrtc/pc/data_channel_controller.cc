/*
 *  Copyright 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "pc/data_channel_controller.h"

#include <utility>

#include "absl/algorithm/container.h"
#include "api/peer_connection_interface.h"
#include "api/rtc_error.h"
#include "pc/peer_connection_internal.h"
#include "pc/sctp_utils.h"
#include "rtc_base/logging.h"

namespace webrtc {

DataChannelController::~DataChannelController() {
  RTC_DCHECK(sctp_data_channels_n_.empty())
      << "Missing call to TeardownDataChannelTransport_n?";
  RTC_DCHECK(!signaling_safety_.flag()->alive())
      << "Missing call to PrepareForShutdown?";
}

bool DataChannelController::HasDataChannels() const {
  RTC_DCHECK_RUN_ON(signaling_thread());
  return channel_usage_ == DataChannelUsage::kInUse;
}

bool DataChannelController::HasUsedDataChannels() const {
  RTC_DCHECK_RUN_ON(signaling_thread());
  return channel_usage_ != DataChannelUsage::kNeverUsed;
}

RTCError DataChannelController::SendData(
    StreamId sid,
    const SendDataParams& params,
    const rtc::CopyOnWriteBuffer& payload) {
  RTC_DCHECK_RUN_ON(network_thread());
  if (!data_channel_transport_) {
    RTC_LOG(LS_ERROR) << "SendData called before transport is ready";
    return RTCError(RTCErrorType::INVALID_STATE);
  }
  return data_channel_transport_->SendData(sid.stream_id_int(), params,
                                           payload);
}

void DataChannelController::AddSctpDataStream(StreamId sid) {
  RTC_DCHECK_RUN_ON(network_thread());
  RTC_DCHECK(sid.HasValue());
  if (data_channel_transport_) {
    data_channel_transport_->OpenChannel(sid.stream_id_int());
  }
}

void DataChannelController::RemoveSctpDataStream(StreamId sid) {
  RTC_DCHECK_RUN_ON(network_thread());
  if (data_channel_transport_) {
    data_channel_transport_->CloseChannel(sid.stream_id_int());
  }
}

void DataChannelController::OnChannelStateChanged(
    SctpDataChannel* channel,
    DataChannelInterface::DataState state) {
  RTC_DCHECK_RUN_ON(network_thread());

  // Stash away the internal id here in case `OnSctpDataChannelClosed` ends up
  // releasing the last reference to the channel.
  const int channel_id = channel->internal_id();

  if (state == DataChannelInterface::DataState::kClosed)
    OnSctpDataChannelClosed(channel);

  DataChannelUsage channel_usage = sctp_data_channels_n_.empty()
                                       ? DataChannelUsage::kHaveBeenUsed
                                       : DataChannelUsage::kInUse;
  signaling_thread()->PostTask(SafeTask(
      signaling_safety_.flag(), [this, channel_id, state, channel_usage] {
        RTC_DCHECK_RUN_ON(signaling_thread());
        channel_usage_ = channel_usage;
        pc_->OnSctpDataChannelStateChanged(channel_id, state);
      }));
}

void DataChannelController::OnDataReceived(
    int channel_id,
    DataMessageType type,
    const rtc::CopyOnWriteBuffer& buffer) {
  RTC_DCHECK_RUN_ON(network_thread());

  if (HandleOpenMessage_n(channel_id, type, buffer))
    return;

  auto it = absl::c_find_if(sctp_data_channels_n_, [&](const auto& c) {
    return c->sid_n().stream_id_int() == channel_id;
  });

  if (it != sctp_data_channels_n_.end())
    (*it)->OnDataReceived(type, buffer);
}

void DataChannelController::OnChannelClosing(int channel_id) {
  RTC_DCHECK_RUN_ON(network_thread());
  auto it = absl::c_find_if(sctp_data_channels_n_, [&](const auto& c) {
    return c->sid_n().stream_id_int() == channel_id;
  });

  if (it != sctp_data_channels_n_.end())
    (*it)->OnClosingProcedureStartedRemotely();
}

void DataChannelController::OnChannelClosed(int channel_id) {
  RTC_DCHECK_RUN_ON(network_thread());
  StreamId sid(channel_id);
  sid_allocator_.ReleaseSid(sid);
  auto it = absl::c_find_if(sctp_data_channels_n_,
                            [&](const auto& c) { return c->sid_n() == sid; });

  if (it != sctp_data_channels_n_.end()) {
    rtc::scoped_refptr<SctpDataChannel> channel = std::move(*it);
    sctp_data_channels_n_.erase(it);
    channel->OnClosingProcedureComplete();
  }
}

void DataChannelController::OnReadyToSend() {
  RTC_DCHECK_RUN_ON(network_thread());
  auto copy = sctp_data_channels_n_;
  for (const auto& channel : copy) {
    if (channel->sid_n().HasValue()) {
      channel->OnTransportReady();
    } else {
      // This happens for role==SSL_SERVER channels when we get notified by
      // the transport *before* the SDP code calls `AllocateSctpSids` to
      // trigger assignment of sids. In this case OnTransportReady() will be
      // called from within `AllocateSctpSids` below.
      RTC_LOG(LS_INFO) << "OnReadyToSend: Still waiting for an id for channel.";
    }
  }
}

void DataChannelController::OnTransportClosed(RTCError error) {
  RTC_DCHECK_RUN_ON(network_thread());

  // This loop will close all data channels and trigger a callback to
  // `OnSctpDataChannelClosed`. We'll empty `sctp_data_channels_n_`, first
  // and `OnSctpDataChannelClosed` will become a noop but we'll release the
  // StreamId here.
  std::vector<rtc::scoped_refptr<SctpDataChannel>> temp_sctp_dcs;
  temp_sctp_dcs.swap(sctp_data_channels_n_);
  for (const auto& channel : temp_sctp_dcs) {
    channel->OnTransportChannelClosed(error);
    sid_allocator_.ReleaseSid(channel->sid_n());
  }
}

void DataChannelController::SetupDataChannelTransport_n(
    DataChannelTransportInterface* transport) {
  RTC_DCHECK_RUN_ON(network_thread());
  RTC_DCHECK(transport);
  set_data_channel_transport(transport);
}

void DataChannelController::PrepareForShutdown() {
  RTC_DCHECK_RUN_ON(signaling_thread());
  signaling_safety_.reset(PendingTaskSafetyFlag::CreateDetachedInactive());
  if (channel_usage_ != DataChannelUsage::kNeverUsed)
    channel_usage_ = DataChannelUsage::kHaveBeenUsed;
}

void DataChannelController::TeardownDataChannelTransport_n(RTCError error) {
  RTC_DCHECK_RUN_ON(network_thread());
  OnTransportClosed(error);
  set_data_channel_transport(nullptr);
  RTC_DCHECK(sctp_data_channels_n_.empty());
  weak_factory_.InvalidateWeakPtrs();
}

void DataChannelController::OnTransportChanged(
    DataChannelTransportInterface* new_data_channel_transport) {
  RTC_DCHECK_RUN_ON(network_thread());
  if (data_channel_transport_ &&
      data_channel_transport_ != new_data_channel_transport) {
    // Changed which data channel transport is used for `sctp_mid_` (eg. now
    // it's bundled).
    set_data_channel_transport(new_data_channel_transport);
  }
}

std::vector<DataChannelStats> DataChannelController::GetDataChannelStats()
    const {
  RTC_DCHECK_RUN_ON(network_thread());
  std::vector<DataChannelStats> stats;
  stats.reserve(sctp_data_channels_n_.size());
  for (const auto& channel : sctp_data_channels_n_)
    stats.push_back(channel->GetStats());
  return stats;
}

bool DataChannelController::HandleOpenMessage_n(
    int channel_id,
    DataMessageType type,
    const rtc::CopyOnWriteBuffer& buffer) {
  if (type != DataMessageType::kControl || !IsOpenMessage(buffer))
    return false;

  // Received OPEN message; parse and signal that a new data channel should
  // be created.
  std::string label;
  InternalDataChannelInit config;
  config.id = channel_id;
  if (!ParseDataChannelOpenMessage(buffer, &label, &config)) {
    RTC_LOG(LS_WARNING) << "Failed to parse the OPEN message for sid "
                        << channel_id;
  } else {
    config.open_handshake_role = InternalDataChannelInit::kAcker;
    auto channel_or_error = CreateDataChannel(label, config);
    if (channel_or_error.ok()) {
      signaling_thread()->PostTask(SafeTask(
          signaling_safety_.flag(),
          [this, channel = channel_or_error.MoveValue(),
           ready_to_send = data_channel_transport_->IsReadyToSend()] {
            RTC_DCHECK_RUN_ON(signaling_thread());
            OnDataChannelOpenMessage(std::move(channel), ready_to_send);
          }));
    } else {
      RTC_LOG(LS_ERROR) << "Failed to create DataChannel from the OPEN message."
                        << ToString(channel_or_error.error().type());
    }
  }
  return true;
}

void DataChannelController::OnDataChannelOpenMessage(
    rtc::scoped_refptr<SctpDataChannel> channel,
    bool ready_to_send) {
  channel_usage_ = DataChannelUsage::kInUse;
  auto proxy = SctpDataChannel::CreateProxy(channel, signaling_safety_.flag());

  pc_->Observer()->OnDataChannel(proxy);
  pc_->NoteDataAddedEvent();

  if (ready_to_send) {
    network_thread()->PostTask([channel = std::move(channel)] {
      if (channel->state() != DataChannelInterface::DataState::kClosed)
        channel->OnTransportReady();
    });
  }
}

// RTC_RUN_ON(network_thread())
RTCError DataChannelController::ReserveOrAllocateSid(
    StreamId& sid,
    absl::optional<rtc::SSLRole> fallback_ssl_role) {
  if (sid.HasValue()) {
    return sid_allocator_.ReserveSid(sid)
               ? RTCError::OK()
               : RTCError(RTCErrorType::INVALID_RANGE,
                          "StreamId out of range or reserved.");
  }

  // Attempt to allocate an ID based on the negotiated role.
  absl::optional<rtc::SSLRole> role = pc_->GetSctpSslRole_n();
  if (!role)
    role = fallback_ssl_role;
  if (role) {
    sid = sid_allocator_.AllocateSid(*role);
    if (!sid.HasValue())
      return RTCError(RTCErrorType::RESOURCE_EXHAUSTED);
  }
  // When we get here, we may still not have an ID, but that's a supported case
  // whereby an id will be assigned later.
  RTC_DCHECK(sid.HasValue() || !role);
  return RTCError::OK();
}

// RTC_RUN_ON(network_thread())
RTCErrorOr<rtc::scoped_refptr<SctpDataChannel>>
DataChannelController::CreateDataChannel(const std::string& label,
                                         InternalDataChannelInit& config) {
  StreamId sid(config.id);
  RTCError err = ReserveOrAllocateSid(sid, config.fallback_ssl_role);
  if (!err.ok())
    return err;

  // In case `sid` has changed. Update `config` accordingly.
  config.id = sid.stream_id_int();

  rtc::scoped_refptr<SctpDataChannel> channel = SctpDataChannel::Create(
      weak_factory_.GetWeakPtr(), label, data_channel_transport_ != nullptr,
      config, signaling_thread(), network_thread());
  RTC_DCHECK(channel);
  sctp_data_channels_n_.push_back(channel);

  // If we have an id already, notify the transport.
  if (sid.HasValue())
    AddSctpDataStream(sid);

  return channel;
}

RTCErrorOr<rtc::scoped_refptr<DataChannelInterface>>
DataChannelController::InternalCreateDataChannelWithProxy(
    const std::string& label,
    const InternalDataChannelInit& config) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  RTC_DCHECK(!pc_->IsClosed());
  if (!config.IsValid()) {
    LOG_AND_RETURN_ERROR(RTCErrorType::INVALID_PARAMETER,
                         "Invalid DataChannelInit");
  }

  bool ready_to_send = false;
  InternalDataChannelInit new_config = config;
  StreamId sid(new_config.id);
  auto ret = network_thread()->BlockingCall(
      [&]() -> RTCErrorOr<rtc::scoped_refptr<SctpDataChannel>> {
        RTC_DCHECK_RUN_ON(network_thread());
        auto channel = CreateDataChannel(label, new_config);
        if (!channel.ok())
          return channel;
        ready_to_send =
            data_channel_transport_ && data_channel_transport_->IsReadyToSend();
        if (ready_to_send) {
          // If the transport is ready to send because the initial channel
          // ready signal may have been sent before the DataChannel creation.
          // This has to be done async because the upper layer objects (e.g.
          // Chrome glue and WebKit) are not wired up properly until after
          // `InternalCreateDataChannelWithProxy` returns.
          network_thread()->PostTask([channel = channel.value()] {
            if (channel->state() != DataChannelInterface::DataState::kClosed)
              channel->OnTransportReady();
          });
        }

        return channel;
      });

  if (!ret.ok())
    return ret.MoveError();

  channel_usage_ = DataChannelUsage::kInUse;
  return SctpDataChannel::CreateProxy(ret.MoveValue(),
                                      signaling_safety_.flag());
}

void DataChannelController::AllocateSctpSids(rtc::SSLRole role) {
  RTC_DCHECK_RUN_ON(network_thread());

  const bool ready_to_send =
      data_channel_transport_ && data_channel_transport_->IsReadyToSend();

  std::vector<std::pair<SctpDataChannel*, StreamId>> channels_to_update;
  std::vector<rtc::scoped_refptr<SctpDataChannel>> channels_to_close;
  for (auto it = sctp_data_channels_n_.begin();
       it != sctp_data_channels_n_.end();) {
    if (!(*it)->sid_n().HasValue()) {
      StreamId sid = sid_allocator_.AllocateSid(role);
      if (sid.HasValue()) {
        (*it)->SetSctpSid_n(sid);
        AddSctpDataStream(sid);
        if (ready_to_send) {
          RTC_LOG(LS_INFO) << "AllocateSctpSids: Id assigned, ready to send.";
          (*it)->OnTransportReady();
        }
        channels_to_update.push_back(std::make_pair((*it).get(), sid));
      } else {
        channels_to_close.push_back(std::move(*it));
        it = sctp_data_channels_n_.erase(it);
        continue;
      }
    }
    ++it;
  }

  // Since closing modifies the list of channels, we have to do the actual
  // closing outside the loop.
  for (const auto& channel : channels_to_close) {
    channel->CloseAbruptlyWithDataChannelFailure("Failed to allocate SCTP SID");
  }
}

void DataChannelController::OnSctpDataChannelClosed(SctpDataChannel* channel) {
  RTC_DCHECK_RUN_ON(network_thread());
  // After the closing procedure is done, it's safe to use this ID for
  // another data channel.
  if (channel->sid_n().HasValue()) {
    sid_allocator_.ReleaseSid(channel->sid_n());
  }
  auto it = absl::c_find_if(sctp_data_channels_n_,
                            [&](const auto& c) { return c.get() == channel; });
  if (it != sctp_data_channels_n_.end())
    sctp_data_channels_n_.erase(it);
}

void DataChannelController::set_data_channel_transport(
    DataChannelTransportInterface* transport) {
  RTC_DCHECK_RUN_ON(network_thread());

  if (data_channel_transport_)
    data_channel_transport_->SetDataSink(nullptr);

  data_channel_transport_ = transport;

  if (data_channel_transport_) {
    // There's a new data channel transport.  This needs to be signaled to the
    // `sctp_data_channels_n_` so that they can reopen and reconnect.  This is
    // necessary when bundling is applied.
    NotifyDataChannelsOfTransportCreated();
    data_channel_transport_->SetDataSink(this);
  }
}

void DataChannelController::NotifyDataChannelsOfTransportCreated() {
  RTC_DCHECK_RUN_ON(network_thread());
  RTC_DCHECK(data_channel_transport_);

  for (const auto& channel : sctp_data_channels_n_) {
    if (channel->sid_n().HasValue())
      AddSctpDataStream(channel->sid_n());
    channel->OnTransportChannelCreated();
  }
}

rtc::Thread* DataChannelController::network_thread() const {
  return pc_->network_thread();
}

rtc::Thread* DataChannelController::signaling_thread() const {
  return pc_->signaling_thread();
}

}  // namespace webrtc
