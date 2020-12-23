/*
 *  Copyright 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef PC_RTP_DATA_CHANNEL_H_
#define PC_RTP_DATA_CHANNEL_H_

#include <memory>
#include <string>

#include "api/data_channel_interface.h"
#include "api/priority.h"
#include "api/scoped_refptr.h"
#include "api/transport/data_channel_transport_interface.h"
#include "media/base/media_channel.h"
#include "pc/channel.h"
#include "pc/data_channel_utils.h"
#include "rtc_base/async_invoker.h"
#include "rtc_base/third_party/sigslot/sigslot.h"

namespace webrtc {

class RtpDataChannel;

// TODO(deadbeef): Once RTP data channels go away, get rid of this and have
// DataChannel depend on SctpTransportInternal (pure virtual SctpTransport
// interface) instead.
class RtpDataChannelProviderInterface {
 public:
  // Sends the data to the transport.
  virtual bool SendData(const cricket::SendDataParams& params,
                        const rtc::CopyOnWriteBuffer& payload,
                        cricket::SendDataResult* result) = 0;
  // Connects to the transport signals.
  virtual bool ConnectDataChannel(RtpDataChannel* data_channel) = 0;
  // Disconnects from the transport signals.
  virtual void DisconnectDataChannel(RtpDataChannel* data_channel) = 0;
  // Returns true if the transport channel is ready to send data.
  virtual bool ReadyToSendData() const = 0;

 protected:
  virtual ~RtpDataChannelProviderInterface() {}
};

// RtpDataChannel is an implementation of the DataChannelInterface based on
// libjingle's data engine. It provides an implementation of unreliable data
// channels.

// DataChannel states:
// kConnecting: The channel has been created the transport might not yet be
//              ready.
// kOpen: The channel have a local SSRC set by a call to UpdateSendSsrc
//        and a remote SSRC set by call to UpdateReceiveSsrc and the transport
//        has been writable once.
// kClosing: DataChannelInterface::Close has been called or UpdateReceiveSsrc
//           has been called with SSRC==0
// kClosed: Both UpdateReceiveSsrc and UpdateSendSsrc has been called with
//          SSRC==0.
class RtpDataChannel : public DataChannelInterface,
                       public sigslot::has_slots<> {
 public:
  static rtc::scoped_refptr<RtpDataChannel> Create(
      RtpDataChannelProviderInterface* provider,
      const std::string& label,
      const DataChannelInit& config,
      rtc::Thread* signaling_thread);

  // Instantiates an API proxy for a DataChannel instance that will be handed
  // out to external callers.
  static rtc::scoped_refptr<DataChannelInterface> CreateProxy(
      rtc::scoped_refptr<RtpDataChannel> channel);

  void RegisterObserver(DataChannelObserver* observer) override;
  void UnregisterObserver() override;

  std::string label() const override { return label_; }
  bool reliable() const override { return false; }
  bool ordered() const override { return config_.ordered; }
  // Backwards compatible accessors
  uint16_t maxRetransmitTime() const override {
    return config_.maxRetransmitTime ? *config_.maxRetransmitTime
                                     : static_cast<uint16_t>(-1);
  }
  uint16_t maxRetransmits() const override {
    return config_.maxRetransmits ? *config_.maxRetransmits
                                  : static_cast<uint16_t>(-1);
  }
  absl::optional<int> maxPacketLifeTime() const override {
    return config_.maxRetransmitTime;
  }
  absl::optional<int> maxRetransmitsOpt() const override {
    return config_.maxRetransmits;
  }
  std::string protocol() const override { return config_.protocol; }
  bool negotiated() const override { return config_.negotiated; }
  int id() const override { return config_.id; }
  Priority priority() const override {
    return config_.priority ? *config_.priority : Priority::kLow;
  }

  virtual int internal_id() const { return internal_id_; }

  uint64_t buffered_amount() const override { return 0; }
  void Close() override;
  DataState state() const override;
  RTCError error() const override;
  uint32_t messages_sent() const override;
  uint64_t bytes_sent() const override;
  uint32_t messages_received() const override;
  uint64_t bytes_received() const override;
  bool Send(const DataBuffer& buffer) override;

  // Close immediately, ignoring any queued data or closing procedure.
  // This is called when SDP indicates a channel should be removed.
  void CloseAbruptlyWithError(RTCError error);

  // Called when the channel's ready to use.  That can happen when the
  // underlying DataMediaChannel becomes ready, or when this channel is a new
  // stream on an existing DataMediaChannel, and we've finished negotiation.
  void OnChannelReady(bool writable);

  // Slots for provider to connect signals to.
  void OnDataReceived(const cricket::ReceiveDataParams& params,
                      const rtc::CopyOnWriteBuffer& payload);

  // Called when the transport channel is unusable.
  // This method makes sure the DataChannel is disconnected and changes state
  // to kClosed.
  void OnTransportChannelClosed();

  DataChannelStats GetStats() const;

  // The remote peer requested that this channel should be closed.
  void RemotePeerRequestClose();
  // Set the SSRC this channel should use to send data on the
  // underlying data engine. |send_ssrc| == 0 means that the channel is no
  // longer part of the session negotiation.
  void SetSendSsrc(uint32_t send_ssrc);
  // Set the SSRC this channel should use to receive data from the
  // underlying data engine.
  void SetReceiveSsrc(uint32_t receive_ssrc);

  // Emitted when state transitions to kOpen.
  sigslot::signal1<DataChannelInterface*> SignalOpened;
  // Emitted when state transitions to kClosed.
  sigslot::signal1<DataChannelInterface*> SignalClosed;

  // Reset the allocator for internal ID values for testing, so that
  // the internal IDs generated are predictable. Test only.
  static void ResetInternalIdAllocatorForTesting(int new_value);

 protected:
  RtpDataChannel(const DataChannelInit& config,
                 RtpDataChannelProviderInterface* client,
                 const std::string& label,
                 rtc::Thread* signaling_thread);
  ~RtpDataChannel() override;

 private:
  bool Init();
  void UpdateState();
  void SetState(DataState state);
  void DisconnectFromProvider();

  void DeliverQueuedReceivedData();

  bool SendDataMessage(const DataBuffer& buffer);

  rtc::Thread* const signaling_thread_;
  const int internal_id_;
  const std::string label_;
  const DataChannelInit config_;
  DataChannelObserver* observer_ RTC_GUARDED_BY(signaling_thread_) = nullptr;
  DataState state_ RTC_GUARDED_BY(signaling_thread_) = kConnecting;
  RTCError error_ RTC_GUARDED_BY(signaling_thread_);
  uint32_t messages_sent_ RTC_GUARDED_BY(signaling_thread_) = 0;
  uint64_t bytes_sent_ RTC_GUARDED_BY(signaling_thread_) = 0;
  uint32_t messages_received_ RTC_GUARDED_BY(signaling_thread_) = 0;
  uint64_t bytes_received_ RTC_GUARDED_BY(signaling_thread_) = 0;
  RtpDataChannelProviderInterface* const provider_;
  bool connected_to_provider_ RTC_GUARDED_BY(signaling_thread_) = false;
  bool send_ssrc_set_ RTC_GUARDED_BY(signaling_thread_) = false;
  bool receive_ssrc_set_ RTC_GUARDED_BY(signaling_thread_) = false;
  bool writable_ RTC_GUARDED_BY(signaling_thread_) = false;
  uint32_t send_ssrc_ RTC_GUARDED_BY(signaling_thread_) = 0;
  uint32_t receive_ssrc_ RTC_GUARDED_BY(signaling_thread_) = 0;
  PacketQueue queued_received_data_ RTC_GUARDED_BY(signaling_thread_);
};

}  // namespace webrtc

#endif  // PC_RTP_DATA_CHANNEL_H_
