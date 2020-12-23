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

#include "pc/peer_connection.h"
#include "pc/sctp_utils.h"

namespace webrtc {

bool DataChannelController::HasDataChannels() const {
  RTC_DCHECK_RUN_ON(signaling_thread());
  return !rtp_data_channels_.empty() || !sctp_data_channels_.empty();
}

bool DataChannelController::SendData(const cricket::SendDataParams& params,
                                     const rtc::CopyOnWriteBuffer& payload,
                                     cricket::SendDataResult* result) {
  if (data_channel_transport())
    return DataChannelSendData(params, payload, result);
  if (rtp_data_channel())
    return rtp_data_channel()->SendData(params, payload, result);
  RTC_LOG(LS_ERROR) << "SendData called before transport is ready";
  return false;
}

bool DataChannelController::ConnectDataChannel(
    RtpDataChannel* webrtc_data_channel) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  if (!rtp_data_channel()) {
    // Don't log an error here, because DataChannels are expected to call
    // ConnectDataChannel in this state. It's the only way to initially tell
    // whether or not the underlying transport is ready.
    return false;
  }
  rtp_data_channel()->SignalReadyToSendData.connect(
      webrtc_data_channel, &RtpDataChannel::OnChannelReady);
  rtp_data_channel()->SignalDataReceived.connect(
      webrtc_data_channel, &RtpDataChannel::OnDataReceived);
  return true;
}

void DataChannelController::DisconnectDataChannel(
    RtpDataChannel* webrtc_data_channel) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  if (!rtp_data_channel()) {
    RTC_LOG(LS_ERROR)
        << "DisconnectDataChannel called when rtp_data_channel_ is NULL.";
    return;
  }
  rtp_data_channel()->SignalReadyToSendData.disconnect(webrtc_data_channel);
  rtp_data_channel()->SignalDataReceived.disconnect(webrtc_data_channel);
}

bool DataChannelController::ConnectDataChannel(
    SctpDataChannel* webrtc_data_channel) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  if (!data_channel_transport()) {
    // Don't log an error here, because DataChannels are expected to call
    // ConnectDataChannel in this state. It's the only way to initially tell
    // whether or not the underlying transport is ready.
    return false;
  }
  SignalDataChannelTransportWritable_s.connect(
      webrtc_data_channel, &SctpDataChannel::OnTransportReady);
  SignalDataChannelTransportReceivedData_s.connect(
      webrtc_data_channel, &SctpDataChannel::OnDataReceived);
  SignalDataChannelTransportChannelClosing_s.connect(
      webrtc_data_channel, &SctpDataChannel::OnClosingProcedureStartedRemotely);
  SignalDataChannelTransportChannelClosed_s.connect(
      webrtc_data_channel, &SctpDataChannel::OnClosingProcedureComplete);
  return true;
}

void DataChannelController::DisconnectDataChannel(
    SctpDataChannel* webrtc_data_channel) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  if (!data_channel_transport()) {
    RTC_LOG(LS_ERROR)
        << "DisconnectDataChannel called when sctp_transport_ is NULL.";
    return;
  }
  SignalDataChannelTransportWritable_s.disconnect(webrtc_data_channel);
  SignalDataChannelTransportReceivedData_s.disconnect(webrtc_data_channel);
  SignalDataChannelTransportChannelClosing_s.disconnect(webrtc_data_channel);
  SignalDataChannelTransportChannelClosed_s.disconnect(webrtc_data_channel);
}

void DataChannelController::AddSctpDataStream(int sid) {
  if (data_channel_transport()) {
    network_thread()->Invoke<void>(RTC_FROM_HERE, [this, sid] {
      if (data_channel_transport()) {
        data_channel_transport()->OpenChannel(sid);
      }
    });
  }
}

void DataChannelController::RemoveSctpDataStream(int sid) {
  if (data_channel_transport()) {
    network_thread()->Invoke<void>(RTC_FROM_HERE, [this, sid] {
      if (data_channel_transport()) {
        data_channel_transport()->CloseChannel(sid);
      }
    });
  }
}

bool DataChannelController::ReadyToSendData() const {
  RTC_DCHECK_RUN_ON(signaling_thread());
  return (rtp_data_channel() && rtp_data_channel()->ready_to_send_data()) ||
         (data_channel_transport() && data_channel_transport_ready_to_send_);
}

void DataChannelController::OnDataReceived(
    int channel_id,
    DataMessageType type,
    const rtc::CopyOnWriteBuffer& buffer) {
  RTC_DCHECK_RUN_ON(network_thread());
  cricket::ReceiveDataParams params;
  params.sid = channel_id;
  params.type = ToCricketDataMessageType(type);
  data_channel_transport_invoker_.AsyncInvoke<void>(
      RTC_FROM_HERE, signaling_thread(), [this, params, buffer] {
        RTC_DCHECK_RUN_ON(signaling_thread());
        // TODO(bugs.webrtc.org/11547): The data being received should be
        // delivered on the network thread. The way HandleOpenMessage_s works
        // right now is that it's called for all types of buffers and operates
        // as a selector function. Change this so that it's only called for
        // buffers that it should be able to handle. Once we do that, we can
        // deliver all other buffers on the network thread (change
        // SignalDataChannelTransportReceivedData_s to
        // SignalDataChannelTransportReceivedData_n).
        if (!HandleOpenMessage_s(params, buffer)) {
          SignalDataChannelTransportReceivedData_s(params, buffer);
        }
      });
}

void DataChannelController::OnChannelClosing(int channel_id) {
  RTC_DCHECK_RUN_ON(network_thread());
  data_channel_transport_invoker_.AsyncInvoke<void>(
      RTC_FROM_HERE, signaling_thread(), [this, channel_id] {
        RTC_DCHECK_RUN_ON(signaling_thread());
        SignalDataChannelTransportChannelClosing_s(channel_id);
      });
}

void DataChannelController::OnChannelClosed(int channel_id) {
  RTC_DCHECK_RUN_ON(network_thread());
  data_channel_transport_invoker_.AsyncInvoke<void>(
      RTC_FROM_HERE, signaling_thread(), [this, channel_id] {
        RTC_DCHECK_RUN_ON(signaling_thread());
        SignalDataChannelTransportChannelClosed_s(channel_id);
      });
}

void DataChannelController::OnReadyToSend() {
  RTC_DCHECK_RUN_ON(network_thread());
  data_channel_transport_invoker_.AsyncInvoke<void>(
      RTC_FROM_HERE, signaling_thread(), [this] {
        RTC_DCHECK_RUN_ON(signaling_thread());
        data_channel_transport_ready_to_send_ = true;
        SignalDataChannelTransportWritable_s(
            data_channel_transport_ready_to_send_);
      });
}

void DataChannelController::OnTransportClosed() {
  RTC_DCHECK_RUN_ON(network_thread());
  data_channel_transport_invoker_.AsyncInvoke<void>(
      RTC_FROM_HERE, signaling_thread(), [this] {
        RTC_DCHECK_RUN_ON(signaling_thread());
        OnTransportChannelClosed();
      });
}

void DataChannelController::SetupDataChannelTransport_n() {
  RTC_DCHECK_RUN_ON(network_thread());

  // There's a new data channel transport.  This needs to be signaled to the
  // |sctp_data_channels_| so that they can reopen and reconnect.  This is
  // necessary when bundling is applied.
  NotifyDataChannelsOfTransportCreated();
}

void DataChannelController::TeardownDataChannelTransport_n() {
  RTC_DCHECK_RUN_ON(network_thread());
  if (data_channel_transport()) {
    data_channel_transport()->SetDataSink(nullptr);
  }
  set_data_channel_transport(nullptr);
}

void DataChannelController::OnTransportChanged(
    DataChannelTransportInterface* new_data_channel_transport) {
  RTC_DCHECK_RUN_ON(network_thread());
  if (data_channel_transport() &&
      data_channel_transport() != new_data_channel_transport) {
    // Changed which data channel transport is used for |sctp_mid_| (eg. now
    // it's bundled).
    data_channel_transport()->SetDataSink(nullptr);
    set_data_channel_transport(new_data_channel_transport);
    if (new_data_channel_transport) {
      new_data_channel_transport->SetDataSink(this);

      // There's a new data channel transport.  This needs to be signaled to the
      // |sctp_data_channels_| so that they can reopen and reconnect.  This is
      // necessary when bundling is applied.
      NotifyDataChannelsOfTransportCreated();
    }
  }
}

std::vector<DataChannelStats> DataChannelController::GetDataChannelStats()
    const {
  RTC_DCHECK_RUN_ON(signaling_thread());
  std::vector<DataChannelStats> stats;
  stats.reserve(sctp_data_channels_.size());
  for (const auto& channel : sctp_data_channels_)
    stats.push_back(channel->GetStats());
  return stats;
}

bool DataChannelController::HandleOpenMessage_s(
    const cricket::ReceiveDataParams& params,
    const rtc::CopyOnWriteBuffer& buffer) {
  if (params.type == cricket::DMT_CONTROL && IsOpenMessage(buffer)) {
    // Received OPEN message; parse and signal that a new data channel should
    // be created.
    std::string label;
    InternalDataChannelInit config;
    config.id = params.ssrc;
    if (!ParseDataChannelOpenMessage(buffer, &label, &config)) {
      RTC_LOG(LS_WARNING) << "Failed to parse the OPEN message for ssrc "
                          << params.ssrc;
      return true;
    }
    config.open_handshake_role = InternalDataChannelInit::kAcker;
    OnDataChannelOpenMessage(label, config);
    return true;
  }
  return false;
}

void DataChannelController::OnDataChannelOpenMessage(
    const std::string& label,
    const InternalDataChannelInit& config) {
  rtc::scoped_refptr<DataChannelInterface> channel(
      InternalCreateDataChannelWithProxy(label, &config));
  if (!channel.get()) {
    RTC_LOG(LS_ERROR) << "Failed to create DataChannel from the OPEN message.";
    return;
  }

  pc_->Observer()->OnDataChannel(std::move(channel));
  pc_->NoteDataAddedEvent();
}

rtc::scoped_refptr<DataChannelInterface>
DataChannelController::InternalCreateDataChannelWithProxy(
    const std::string& label,
    const InternalDataChannelInit* config) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  if (pc_->IsClosed()) {
    return nullptr;
  }
  if (data_channel_type_ == cricket::DCT_NONE) {
    RTC_LOG(LS_ERROR)
        << "InternalCreateDataChannel: Data is not supported in this call.";
    return nullptr;
  }
  if (IsSctpLike(data_channel_type())) {
    rtc::scoped_refptr<SctpDataChannel> channel =
        InternalCreateSctpDataChannel(label, config);
    if (channel) {
      return SctpDataChannel::CreateProxy(channel);
    }
  } else if (data_channel_type() == cricket::DCT_RTP) {
    rtc::scoped_refptr<RtpDataChannel> channel =
        InternalCreateRtpDataChannel(label, config);
    if (channel) {
      return RtpDataChannel::CreateProxy(channel);
    }
  }

  return nullptr;
}

rtc::scoped_refptr<RtpDataChannel>
DataChannelController::InternalCreateRtpDataChannel(
    const std::string& label,
    const DataChannelInit* config) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  DataChannelInit new_config = config ? (*config) : DataChannelInit();
  rtc::scoped_refptr<RtpDataChannel> channel(
      RtpDataChannel::Create(this, label, new_config, signaling_thread()));
  if (!channel) {
    return nullptr;
  }
  if (rtp_data_channels_.find(channel->label()) != rtp_data_channels_.end()) {
    RTC_LOG(LS_ERROR) << "DataChannel with label " << channel->label()
                      << " already exists.";
    return nullptr;
  }
  rtp_data_channels_[channel->label()] = channel;
  SignalRtpDataChannelCreated_(channel.get());
  return channel;
}

rtc::scoped_refptr<SctpDataChannel>
DataChannelController::InternalCreateSctpDataChannel(
    const std::string& label,
    const InternalDataChannelInit* config) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  InternalDataChannelInit new_config =
      config ? (*config) : InternalDataChannelInit();
  if (new_config.id < 0) {
    rtc::SSLRole role;
    if ((pc_->GetSctpSslRole(&role)) &&
        !sid_allocator_.AllocateSid(role, &new_config.id)) {
      RTC_LOG(LS_ERROR) << "No id can be allocated for the SCTP data channel.";
      return nullptr;
    }
  } else if (!sid_allocator_.ReserveSid(new_config.id)) {
    RTC_LOG(LS_ERROR) << "Failed to create a SCTP data channel "
                         "because the id is already in use or out of range.";
    return nullptr;
  }
  rtc::scoped_refptr<SctpDataChannel> channel(SctpDataChannel::Create(
      this, label, new_config, signaling_thread(), network_thread()));
  if (!channel) {
    sid_allocator_.ReleaseSid(new_config.id);
    return nullptr;
  }
  sctp_data_channels_.push_back(channel);
  channel->SignalClosed.connect(pc_, &PeerConnection::OnSctpDataChannelClosed);
  SignalSctpDataChannelCreated_(channel.get());
  return channel;
}

void DataChannelController::AllocateSctpSids(rtc::SSLRole role) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  std::vector<rtc::scoped_refptr<SctpDataChannel>> channels_to_close;
  for (const auto& channel : sctp_data_channels_) {
    if (channel->id() < 0) {
      int sid;
      if (!sid_allocator_.AllocateSid(role, &sid)) {
        RTC_LOG(LS_ERROR) << "Failed to allocate SCTP sid, closing channel.";
        channels_to_close.push_back(channel);
        continue;
      }
      channel->SetSctpSid(sid);
    }
  }
  // Since closing modifies the list of channels, we have to do the actual
  // closing outside the loop.
  for (const auto& channel : channels_to_close) {
    channel->CloseAbruptlyWithDataChannelFailure("Failed to allocate SCTP SID");
  }
}

void DataChannelController::OnSctpDataChannelClosed(SctpDataChannel* channel) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  for (auto it = sctp_data_channels_.begin(); it != sctp_data_channels_.end();
       ++it) {
    if (it->get() == channel) {
      if (channel->id() >= 0) {
        // After the closing procedure is done, it's safe to use this ID for
        // another data channel.
        sid_allocator_.ReleaseSid(channel->id());
      }
      // Since this method is triggered by a signal from the DataChannel,
      // we can't free it directly here; we need to free it asynchronously.
      sctp_data_channels_to_free_.push_back(*it);
      sctp_data_channels_.erase(it);
      signaling_thread()->PostTask(
          RTC_FROM_HERE, [self = weak_factory_.GetWeakPtr()] {
            if (self) {
              RTC_DCHECK_RUN_ON(self->signaling_thread());
              self->sctp_data_channels_to_free_.clear();
            }
          });
      return;
    }
  }
}

void DataChannelController::OnTransportChannelClosed() {
  RTC_DCHECK_RUN_ON(signaling_thread());
  // Use a temporary copy of the RTP/SCTP DataChannel list because the
  // DataChannel may callback to us and try to modify the list.
  std::map<std::string, rtc::scoped_refptr<RtpDataChannel>> temp_rtp_dcs;
  temp_rtp_dcs.swap(rtp_data_channels_);
  for (const auto& kv : temp_rtp_dcs) {
    kv.second->OnTransportChannelClosed();
  }

  std::vector<rtc::scoped_refptr<SctpDataChannel>> temp_sctp_dcs;
  temp_sctp_dcs.swap(sctp_data_channels_);
  for (const auto& channel : temp_sctp_dcs) {
    channel->OnTransportChannelClosed();
  }
}

SctpDataChannel* DataChannelController::FindDataChannelBySid(int sid) const {
  RTC_DCHECK_RUN_ON(signaling_thread());
  for (const auto& channel : sctp_data_channels_) {
    if (channel->id() == sid) {
      return channel;
    }
  }
  return nullptr;
}

void DataChannelController::UpdateLocalRtpDataChannels(
    const cricket::StreamParamsVec& streams) {
  std::vector<std::string> existing_channels;

  RTC_DCHECK_RUN_ON(signaling_thread());
  // Find new and active data channels.
  for (const cricket::StreamParams& params : streams) {
    // |it->sync_label| is actually the data channel label. The reason is that
    // we use the same naming of data channels as we do for
    // MediaStreams and Tracks.
    // For MediaStreams, the sync_label is the MediaStream label and the
    // track label is the same as |streamid|.
    const std::string& channel_label = params.first_stream_id();
    auto data_channel_it = rtp_data_channels()->find(channel_label);
    if (data_channel_it == rtp_data_channels()->end()) {
      RTC_LOG(LS_ERROR) << "channel label not found";
      continue;
    }
    // Set the SSRC the data channel should use for sending.
    data_channel_it->second->SetSendSsrc(params.first_ssrc());
    existing_channels.push_back(data_channel_it->first);
  }

  UpdateClosingRtpDataChannels(existing_channels, true);
}

void DataChannelController::UpdateRemoteRtpDataChannels(
    const cricket::StreamParamsVec& streams) {
  RTC_DCHECK_RUN_ON(signaling_thread());

  std::vector<std::string> existing_channels;

  // Find new and active data channels.
  for (const cricket::StreamParams& params : streams) {
    // The data channel label is either the mslabel or the SSRC if the mslabel
    // does not exist. Ex a=ssrc:444330170 mslabel:test1.
    std::string label = params.first_stream_id().empty()
                            ? rtc::ToString(params.first_ssrc())
                            : params.first_stream_id();
    auto data_channel_it = rtp_data_channels()->find(label);
    if (data_channel_it == rtp_data_channels()->end()) {
      // This is a new data channel.
      CreateRemoteRtpDataChannel(label, params.first_ssrc());
    } else {
      data_channel_it->second->SetReceiveSsrc(params.first_ssrc());
    }
    existing_channels.push_back(label);
  }

  UpdateClosingRtpDataChannels(existing_channels, false);
}

cricket::DataChannelType DataChannelController::data_channel_type() const {
  // TODO(bugs.webrtc.org/9987): Should be restricted to the signaling thread.
  // RTC_DCHECK_RUN_ON(signaling_thread());
  return data_channel_type_;
}

void DataChannelController::set_data_channel_type(
    cricket::DataChannelType type) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  data_channel_type_ = type;
}

DataChannelTransportInterface* DataChannelController::data_channel_transport()
    const {
  // TODO(bugs.webrtc.org/11547): Only allow this accessor to be called on the
  // network thread.
  // RTC_DCHECK_RUN_ON(network_thread());
  return data_channel_transport_;
}

void DataChannelController::set_data_channel_transport(
    DataChannelTransportInterface* transport) {
  RTC_DCHECK_RUN_ON(network_thread());
  data_channel_transport_ = transport;
}

const std::map<std::string, rtc::scoped_refptr<RtpDataChannel>>*
DataChannelController::rtp_data_channels() const {
  RTC_DCHECK_RUN_ON(signaling_thread());
  return &rtp_data_channels_;
}

void DataChannelController::UpdateClosingRtpDataChannels(
    const std::vector<std::string>& active_channels,
    bool is_local_update) {
  auto it = rtp_data_channels_.begin();
  while (it != rtp_data_channels_.end()) {
    RtpDataChannel* data_channel = it->second;
    if (absl::c_linear_search(active_channels, data_channel->label())) {
      ++it;
      continue;
    }

    if (is_local_update) {
      data_channel->SetSendSsrc(0);
    } else {
      data_channel->RemotePeerRequestClose();
    }

    if (data_channel->state() == RtpDataChannel::kClosed) {
      rtp_data_channels_.erase(it);
      it = rtp_data_channels_.begin();
    } else {
      ++it;
    }
  }
}

void DataChannelController::CreateRemoteRtpDataChannel(const std::string& label,
                                                       uint32_t remote_ssrc) {
  if (data_channel_type() != cricket::DCT_RTP) {
    return;
  }
  rtc::scoped_refptr<RtpDataChannel> channel(
      InternalCreateRtpDataChannel(label, nullptr));
  if (!channel.get()) {
    RTC_LOG(LS_WARNING) << "Remote peer requested a DataChannel but"
                           "CreateDataChannel failed.";
    return;
  }
  channel->SetReceiveSsrc(remote_ssrc);
  rtc::scoped_refptr<DataChannelInterface> proxy_channel =
      RtpDataChannel::CreateProxy(std::move(channel));
  pc_->Observer()->OnDataChannel(std::move(proxy_channel));
}

bool DataChannelController::DataChannelSendData(
    const cricket::SendDataParams& params,
    const rtc::CopyOnWriteBuffer& payload,
    cricket::SendDataResult* result) {
  // TODO(bugs.webrtc.org/11547): Expect method to be called on the network
  // thread instead. Remove the Invoke() below and move assocated state to
  // the network thread.
  RTC_DCHECK_RUN_ON(signaling_thread());
  RTC_DCHECK(data_channel_transport());

  SendDataParams send_params;
  send_params.type = ToWebrtcDataMessageType(params.type);
  send_params.ordered = params.ordered;
  if (params.max_rtx_count >= 0) {
    send_params.max_rtx_count = params.max_rtx_count;
  } else if (params.max_rtx_ms >= 0) {
    send_params.max_rtx_ms = params.max_rtx_ms;
  }

  RTCError error = network_thread()->Invoke<RTCError>(
      RTC_FROM_HERE, [this, params, send_params, payload] {
        return data_channel_transport()->SendData(params.sid, send_params,
                                                  payload);
      });

  if (error.ok()) {
    *result = cricket::SendDataResult::SDR_SUCCESS;
    return true;
  } else if (error.type() == RTCErrorType::RESOURCE_EXHAUSTED) {
    // SCTP transport uses RESOURCE_EXHAUSTED when it's blocked.
    // TODO(mellem):  Stop using RTCError here and get rid of the mapping.
    *result = cricket::SendDataResult::SDR_BLOCK;
    return false;
  }
  *result = cricket::SendDataResult::SDR_ERROR;
  return false;
}

void DataChannelController::NotifyDataChannelsOfTransportCreated() {
  RTC_DCHECK_RUN_ON(network_thread());
  data_channel_transport_invoker_.AsyncInvoke<void>(
      RTC_FROM_HERE, signaling_thread(), [this] {
        RTC_DCHECK_RUN_ON(signaling_thread());
        for (const auto& channel : sctp_data_channels_) {
          channel->OnTransportChannelCreated();
        }
      });
}

rtc::Thread* DataChannelController::network_thread() const {
  return pc_->network_thread();
}
rtc::Thread* DataChannelController::signaling_thread() const {
  return pc_->signaling_thread();
}

}  // namespace webrtc
