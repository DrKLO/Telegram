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

#include <stdint.h>

#include <map>
#include <memory>
#include <string>
#include <vector>

#include "api/data_channel_interface.h"
#include "api/scoped_refptr.h"
#include "api/sequence_checker.h"
#include "api/transport/data_channel_transport_interface.h"
#include "media/base/media_channel.h"
#include "media/base/media_engine.h"
#include "media/base/stream_params.h"
#include "pc/channel.h"
#include "pc/data_channel_utils.h"
#include "pc/sctp_data_channel.h"
#include "rtc_base/checks.h"
#include "rtc_base/copy_on_write_buffer.h"
#include "rtc_base/ssl_stream_adapter.h"
#include "rtc_base/third_party/sigslot/sigslot.h"
#include "rtc_base/thread.h"
#include "rtc_base/thread_annotations.h"
#include "rtc_base/weak_ptr.h"

namespace webrtc {

class PeerConnection;

class DataChannelController : public SctpDataChannelProviderInterface,
                              public DataChannelSink {
 public:
  explicit DataChannelController(PeerConnection* pc) : pc_(pc) {}

  // Not copyable or movable.
  DataChannelController(DataChannelController&) = delete;
  DataChannelController& operator=(const DataChannelController& other) = delete;
  DataChannelController(DataChannelController&&) = delete;
  DataChannelController& operator=(DataChannelController&& other) = delete;

  // Implements
  // SctpDataChannelProviderInterface.
  bool SendData(int sid,
                const SendDataParams& params,
                const rtc::CopyOnWriteBuffer& payload,
                cricket::SendDataResult* result) override;
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
  void OnTransportClosed(RTCError error) override;

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

  // Accessors
  DataChannelTransportInterface* data_channel_transport() const;
  void set_data_channel_transport(DataChannelTransportInterface* transport);

  sigslot::signal1<SctpDataChannel*>& SignalSctpDataChannelCreated() {
    RTC_DCHECK_RUN_ON(signaling_thread());
    return SignalSctpDataChannelCreated_;
  }
  // Called when the transport for the data channels is closed or destroyed.
  void OnTransportChannelClosed(RTCError error);

  void OnSctpDataChannelClosed(SctpDataChannel* channel);

 private:
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

  // Called from SendData when data_channel_transport() is true.
  bool DataChannelSendData(int sid,
                           const SendDataParams& params,
                           const rtc::CopyOnWriteBuffer& payload,
                           cricket::SendDataResult* result);

  // Called when all data channels need to be notified of a transport channel
  // (calls OnTransportChannelCreated on the signaling thread).
  void NotifyDataChannelsOfTransportCreated();

  rtc::Thread* network_thread() const;
  rtc::Thread* signaling_thread() const;

  // Plugin transport used for data channels.  Pointer may be accessed and
  // checked from any thread, but the object may only be touched on the
  // network thread.
  // TODO(bugs.webrtc.org/9987): Accessed on both signaling and network
  // thread.
  DataChannelTransportInterface* data_channel_transport_ = nullptr;

  // Cached value of whether the data channel transport is ready to send.
  bool data_channel_transport_ready_to_send_
      RTC_GUARDED_BY(signaling_thread()) = false;

  SctpSidAllocator sid_allocator_ /* RTC_GUARDED_BY(signaling_thread()) */;
  std::vector<rtc::scoped_refptr<SctpDataChannel>> sctp_data_channels_
      RTC_GUARDED_BY(signaling_thread());
  std::vector<rtc::scoped_refptr<SctpDataChannel>> sctp_data_channels_to_free_
      RTC_GUARDED_BY(signaling_thread());

  // Signals from `data_channel_transport_`.  These are invoked on the
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

  sigslot::signal1<SctpDataChannel*> SignalSctpDataChannelCreated_
      RTC_GUARDED_BY(signaling_thread());

  // Owning PeerConnection.
  PeerConnection* const pc_;
  // The weak pointers must be dereferenced and invalidated on the signalling
  // thread only.
  rtc::WeakPtrFactory<DataChannelController> weak_factory_{this};
};

}  // namespace webrtc

#endif  // PC_DATA_CHANNEL_CONTROLLER_H_
