/*
 *  Copyright 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef PC_DATA_CHANNEL_CONTROLLER_H_
#define PC_DATA_CHANNEL_CONTROLLER_H_

#include <map>
#include <memory>
#include <string>
#include <vector>

#include "pc/channel.h"
#include "pc/rtp_data_channel.h"
#include "pc/sctp_data_channel.h"
#include "rtc_base/weak_ptr.h"

namespace webrtc {

class PeerConnection;

class DataChannelController : public RtpDataChannelProviderInterface,
                              public SctpDataChannelProviderInterface,
                              public DataChannelSink {
 public:
  explicit DataChannelController(PeerConnection* pc) : pc_(pc) {}

  // Not copyable or movable.
  DataChannelController(DataChannelController&) = delete;
  DataChannelController& operator=(const DataChannelController& other) = delete;
  DataChannelController(DataChannelController&&) = delete;
  DataChannelController& operator=(DataChannelController&& other) = delete;

  // Implements RtpDataChannelProviderInterface/
  // SctpDataChannelProviderInterface.
  bool SendData(const cricket::SendDataParams& params,
                const rtc::CopyOnWriteBuffer& payload,
                cricket::SendDataResult* result) override;
  bool ConnectDataChannel(RtpDataChannel* webrtc_data_channel) override;
  void DisconnectDataChannel(RtpDataChannel* webrtc_data_channel) override;
  bool ConnectDataChannel(SctpDataChannel* webrtc_data_channel) override;
  void DisconnectDataChannel(SctpDataChannel* webrtc_data_channel) override;
  void AddSctpDataStream(int sid) override;
  void RemoveSctpDataStream(int sid) override;
  bool ReadyToSendData() const override;

  // Implements DataChannelSink.
  void OnDataReceived(int channel_id,
                      DataMessageType type,
                      const rtc::CopyOnWriteBuffer& buffer) override;
  void OnChannelClosing(int channel_id) override;
  void OnChannelClosed(int channel_id) override;
  void OnReadyToSend() override;
  void OnTransportClosed() override;

  // Called from PeerConnection::SetupDataChannelTransport_n
  void SetupDataChannelTransport_n();
  // Called from PeerConnection::TeardownDataChannelTransport_n
  void TeardownDataChannelTransport_n();

  // Called from PeerConnection::OnTransportChanged
  // to make required changes to datachannels' transports.
  void OnTransportChanged(
      DataChannelTransportInterface* data_channel_transport);

  // Called from PeerConnection::GetDataChannelStats on the signaling thread.
  std::vector<DataChannelStats> GetDataChannelStats() const;

  // Creates channel and adds it to the collection of DataChannels that will
  // be offered in a SessionDescription, and wraps it in a proxy object.
  rtc::scoped_refptr<DataChannelInterface> InternalCreateDataChannelWithProxy(
      const std::string& label,
      const InternalDataChannelInit*
          config) /* RTC_RUN_ON(signaling_thread()) */;
  void AllocateSctpSids(rtc::SSLRole role);

  SctpDataChannel* FindDataChannelBySid(int sid) const;

  // Checks if any data channel has been added.
  bool HasDataChannels() const;
  bool HasSctpDataChannels() const {
    RTC_DCHECK_RUN_ON(signaling_thread());
    return !sctp_data_channels_.empty();
  }
  bool HasRtpDataChannels() const {
    RTC_DCHECK_RUN_ON(signaling_thread());
    return !rtp_data_channels_.empty();
  }

  void UpdateLocalRtpDataChannels(const cricket::StreamParamsVec& streams);
  void UpdateRemoteRtpDataChannels(const cricket::StreamParamsVec& streams);

  // Accessors
  cricket::DataChannelType data_channel_type() const;
  void set_data_channel_type(cricket::DataChannelType type);
  cricket::RtpDataChannel* rtp_data_channel() const {
    return rtp_data_channel_;
  }
  void set_rtp_data_channel(cricket::RtpDataChannel* channel) {
    rtp_data_channel_ = channel;
  }
  DataChannelTransportInterface* data_channel_transport() const;
  void set_data_channel_transport(DataChannelTransportInterface* transport);
  const std::map<std::string, rtc::scoped_refptr<RtpDataChannel>>*
  rtp_data_channels() const;

  sigslot::signal1<RtpDataChannel*>& SignalRtpDataChannelCreated() {
    RTC_DCHECK_RUN_ON(signaling_thread());
    return SignalRtpDataChannelCreated_;
  }
  sigslot::signal1<SctpDataChannel*>& SignalSctpDataChannelCreated() {
    RTC_DCHECK_RUN_ON(signaling_thread());
    return SignalSctpDataChannelCreated_;
  }
  // Called when the transport for the data channels is closed or destroyed.
  void OnTransportChannelClosed();

  void OnSctpDataChannelClosed(SctpDataChannel* channel);

 private:
  rtc::scoped_refptr<RtpDataChannel> InternalCreateRtpDataChannel(
      const std::string& label,
      const DataChannelInit* config) /* RTC_RUN_ON(signaling_thread()) */;

  rtc::scoped_refptr<SctpDataChannel> InternalCreateSctpDataChannel(
      const std::string& label,
      const InternalDataChannelInit*
          config) /* RTC_RUN_ON(signaling_thread()) */;

  // Parses and handles open messages.  Returns true if the message is an open
  // message, false otherwise.
  bool HandleOpenMessage_s(const cricket::ReceiveDataParams& params,
                           const rtc::CopyOnWriteBuffer& buffer)
      RTC_RUN_ON(signaling_thread());
  // Called when a valid data channel OPEN message is received.
  void OnDataChannelOpenMessage(const std::string& label,
                                const InternalDataChannelInit& config)
      RTC_RUN_ON(signaling_thread());

  void CreateRemoteRtpDataChannel(const std::string& label,
                                  uint32_t remote_ssrc)
      RTC_RUN_ON(signaling_thread());

  void UpdateClosingRtpDataChannels(
      const std::vector<std::string>& active_channels,
      bool is_local_update) RTC_RUN_ON(signaling_thread());

  // Called from SendData when data_channel_transport() is true.
  bool DataChannelSendData(const cricket::SendDataParams& params,
                           const rtc::CopyOnWriteBuffer& payload,
                           cricket::SendDataResult* result);

  // Called when all data channels need to be notified of a transport channel
  // (calls OnTransportChannelCreated on the signaling thread).
  void NotifyDataChannelsOfTransportCreated();

  rtc::Thread* network_thread() const;
  rtc::Thread* signaling_thread() const;

  // Specifies which kind of data channel is allowed. This is controlled
  // by the chrome command-line flag and constraints:
  // 1. If chrome command-line switch 'enable-sctp-data-channels' is enabled,
  // constraint kEnableDtlsSrtp is true, and constaint kEnableRtpDataChannels is
  // not set or false, SCTP is allowed (DCT_SCTP);
  // 2. If constraint kEnableRtpDataChannels is true, RTP is allowed (DCT_RTP);
  // 3. If both 1&2 are false, data channel is not allowed (DCT_NONE).
  cricket::DataChannelType data_channel_type_ =
      cricket::DCT_NONE;  // TODO(bugs.webrtc.org/9987): Accessed on both
                          // signaling and network thread.

  // Plugin transport used for data channels.  Pointer may be accessed and
  // checked from any thread, but the object may only be touched on the
  // network thread.
  // TODO(bugs.webrtc.org/9987): Accessed on both signaling and network
  // thread.
  DataChannelTransportInterface* data_channel_transport_ = nullptr;

  // Cached value of whether the data channel transport is ready to send.
  bool data_channel_transport_ready_to_send_
      RTC_GUARDED_BY(signaling_thread()) = false;

  // |rtp_data_channel_| is used if in RTP data channel mode,
  // |data_channel_transport_| when using SCTP.
  cricket::RtpDataChannel* rtp_data_channel_ = nullptr;
  // TODO(bugs.webrtc.org/9987): Accessed on both
  // signaling and some other thread.

  SctpSidAllocator sid_allocator_ /* RTC_GUARDED_BY(signaling_thread()) */;
  std::vector<rtc::scoped_refptr<SctpDataChannel>> sctp_data_channels_
      RTC_GUARDED_BY(signaling_thread());
  std::vector<rtc::scoped_refptr<SctpDataChannel>> sctp_data_channels_to_free_
      RTC_GUARDED_BY(signaling_thread());

  // Map of label -> DataChannel
  std::map<std::string, rtc::scoped_refptr<RtpDataChannel>> rtp_data_channels_
      RTC_GUARDED_BY(signaling_thread());

  // Signals from |data_channel_transport_|.  These are invoked on the
  // signaling thread.
  // TODO(bugs.webrtc.org/11547): These '_s' signals likely all belong on the
  // network thread.
  sigslot::signal1<bool> SignalDataChannelTransportWritable_s
      RTC_GUARDED_BY(signaling_thread());
  sigslot::signal2<const cricket::ReceiveDataParams&,
                   const rtc::CopyOnWriteBuffer&>
      SignalDataChannelTransportReceivedData_s
          RTC_GUARDED_BY(signaling_thread());
  sigslot::signal1<int> SignalDataChannelTransportChannelClosing_s
      RTC_GUARDED_BY(signaling_thread());
  sigslot::signal1<int> SignalDataChannelTransportChannelClosed_s
      RTC_GUARDED_BY(signaling_thread());

  sigslot::signal1<RtpDataChannel*> SignalRtpDataChannelCreated_
      RTC_GUARDED_BY(signaling_thread());
  sigslot::signal1<SctpDataChannel*> SignalSctpDataChannelCreated_
      RTC_GUARDED_BY(signaling_thread());

  // Used from the network thread to invoke data channel transport signals on
  // the signaling thread.
  rtc::AsyncInvoker data_channel_transport_invoker_
      RTC_GUARDED_BY(network_thread());

  // Owning PeerConnection.
  PeerConnection* const pc_;
  rtc::WeakPtrFactory<DataChannelController> weak_factory_{this};
};

}  // namespace webrtc

#endif  // PC_DATA_CHANNEL_CONTROLLER_H_
