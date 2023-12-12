/*
 *  Copyright 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef PC_SCTP_DATA_CHANNEL_H_
#define PC_SCTP_DATA_CHANNEL_H_

#include <stdint.h>

#include <memory>
#include <set>
#include <string>

#include "absl/types/optional.h"
#include "api/data_channel_interface.h"
#include "api/priority.h"
#include "api/rtc_error.h"
#include "api/scoped_refptr.h"
#include "api/transport/data_channel_transport_interface.h"
#include "media/base/media_channel.h"
#include "pc/data_channel_utils.h"
#include "rtc_base/copy_on_write_buffer.h"
#include "rtc_base/ssl_stream_adapter.h"  // For SSLRole
#include "rtc_base/third_party/sigslot/sigslot.h"
#include "rtc_base/thread.h"
#include "rtc_base/thread_annotations.h"

namespace webrtc {

class SctpDataChannel;

// TODO(deadbeef): Get rid of this and have SctpDataChannel depend on
// SctpTransportInternal (pure virtual SctpTransport interface) instead.
class SctpDataChannelControllerInterface {
 public:
  // Sends the data to the transport.
  virtual bool SendData(int sid,
                        const SendDataParams& params,
                        const rtc::CopyOnWriteBuffer& payload,
                        cricket::SendDataResult* result) = 0;
  // Connects to the transport signals.
  virtual bool ConnectDataChannel(SctpDataChannel* data_channel) = 0;
  // Disconnects from the transport signals.
  virtual void DisconnectDataChannel(SctpDataChannel* data_channel) = 0;
  // Adds the data channel SID to the transport for SCTP.
  virtual void AddSctpDataStream(int sid) = 0;
  // Begins the closing procedure by sending an outgoing stream reset. Still
  // need to wait for callbacks to tell when this completes.
  virtual void RemoveSctpDataStream(int sid) = 0;
  // Returns true if the transport channel is ready to send data.
  virtual bool ReadyToSendData() const = 0;

 protected:
  virtual ~SctpDataChannelControllerInterface() {}
};

// TODO(tommi): Change to not inherit from DataChannelInit but to have it as
// a const member. Block access to the 'id' member since it cannot be const.
struct InternalDataChannelInit : public DataChannelInit {
  enum OpenHandshakeRole { kOpener, kAcker, kNone };
  // The default role is kOpener because the default `negotiated` is false.
  InternalDataChannelInit() : open_handshake_role(kOpener) {}
  explicit InternalDataChannelInit(const DataChannelInit& base);
  OpenHandshakeRole open_handshake_role;
};

// Helper class to allocate unique IDs for SCTP DataChannels.
class SctpSidAllocator {
 public:
  // Gets the first unused odd/even id based on the DTLS role. If `role` is
  // SSL_CLIENT, the allocated id starts from 0 and takes even numbers;
  // otherwise, the id starts from 1 and takes odd numbers.
  // Returns false if no ID can be allocated.
  bool AllocateSid(rtc::SSLRole role, int* sid);

  // Attempts to reserve a specific sid. Returns false if it's unavailable.
  bool ReserveSid(int sid);

  // Indicates that `sid` isn't in use any more, and is thus available again.
  void ReleaseSid(int sid);

 private:
  // Checks if `sid` is available to be assigned to a new SCTP data channel.
  bool IsSidAvailable(int sid) const;

  std::set<int> used_sids_;
};

// SctpDataChannel is an implementation of the DataChannelInterface based on
// SctpTransport. It provides an implementation of unreliable or
// reliabledata channels.

// DataChannel states:
// kConnecting: The channel has been created the transport might not yet be
//              ready.
// kOpen: The open handshake has been performed (if relevant) and the data
//        channel is able to send messages.
// kClosing: DataChannelInterface::Close has been called, or the remote side
//           initiated the closing procedure, but the closing procedure has not
//           yet finished.
// kClosed: The closing handshake is finished (possibly initiated from this,
//          side, possibly from the peer).
//
// How the closing procedure works for SCTP:
// 1. Alice calls Close(), state changes to kClosing.
// 2. Alice finishes sending any queued data.
// 3. Alice calls RemoveSctpDataStream, sends outgoing stream reset.
// 4. Bob receives incoming stream reset; OnClosingProcedureStartedRemotely
//    called.
// 5. Bob sends outgoing stream reset.
// 6. Alice receives incoming reset, Bob receives acknowledgement. Both receive
//    OnClosingProcedureComplete callback and transition to kClosed.
class SctpDataChannel : public DataChannelInterface,
                        public sigslot::has_slots<> {
 public:
  static rtc::scoped_refptr<SctpDataChannel> Create(
      SctpDataChannelControllerInterface* controller,
      const std::string& label,
      const InternalDataChannelInit& config,
      rtc::Thread* signaling_thread,
      rtc::Thread* network_thread);

  // Instantiates an API proxy for a SctpDataChannel instance that will be
  // handed out to external callers.
  static rtc::scoped_refptr<DataChannelInterface> CreateProxy(
      rtc::scoped_refptr<SctpDataChannel> channel);

  // Invalidate the link to the controller (DataChannelController);
  void DetachFromController();

  void RegisterObserver(DataChannelObserver* observer) override;
  void UnregisterObserver() override;

  std::string label() const override { return label_; }
  bool reliable() const override;
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

  uint64_t buffered_amount() const override;
  void Close() override;
  DataState state() const override;
  RTCError error() const override;
  uint32_t messages_sent() const override;
  uint64_t bytes_sent() const override;
  uint32_t messages_received() const override;
  uint64_t bytes_received() const override;
  bool Send(const DataBuffer& buffer) override;

  // Close immediately, ignoring any queued data or closing procedure.
  // This is called when the underlying SctpTransport is being destroyed.
  // It is also called by the PeerConnection if SCTP ID assignment fails.
  void CloseAbruptlyWithError(RTCError error);
  // Specializations of CloseAbruptlyWithError
  void CloseAbruptlyWithDataChannelFailure(const std::string& message);

  // Slots for controller to connect signals to.
  //
  // TODO(deadbeef): Make these private once we're hooking up signals ourselves,
  // instead of relying on SctpDataChannelControllerInterface.

  // Called when the SctpTransport's ready to use. That can happen when we've
  // finished negotiation, or if the channel was created after negotiation has
  // already finished.
  void OnTransportReady(bool writable);

  void OnDataReceived(const cricket::ReceiveDataParams& params,
                      const rtc::CopyOnWriteBuffer& payload);

  // Sets the SCTP sid and adds to transport layer if not set yet. Should only
  // be called once.
  void SetSctpSid(int sid);
  // The remote side started the closing procedure by resetting its outgoing
  // stream (our incoming stream). Sets state to kClosing.
  void OnClosingProcedureStartedRemotely(int sid);
  // The closing procedure is complete; both incoming and outgoing stream
  // resets are done and the channel can transition to kClosed. Called
  // asynchronously after RemoveSctpDataStream.
  void OnClosingProcedureComplete(int sid);
  // Called when the transport channel is created.
  // Only needs to be called for SCTP data channels.
  void OnTransportChannelCreated();
  // Called when the transport channel is unusable.
  // This method makes sure the DataChannel is disconnected and changes state
  // to kClosed.
  void OnTransportChannelClosed(RTCError error);

  DataChannelStats GetStats() const;

  // Emitted when state transitions to kOpen.
  sigslot::signal1<DataChannelInterface*> SignalOpened;
  // Emitted when state transitions to kClosed.
  // This signal can be used to tell when the channel's sid is free.
  sigslot::signal1<DataChannelInterface*> SignalClosed;

  // Reset the allocator for internal ID values for testing, so that
  // the internal IDs generated are predictable. Test only.
  static void ResetInternalIdAllocatorForTesting(int new_value);

 protected:
  SctpDataChannel(const InternalDataChannelInit& config,
                  SctpDataChannelControllerInterface* client,
                  const std::string& label,
                  rtc::Thread* signaling_thread,
                  rtc::Thread* network_thread);
  ~SctpDataChannel() override;

 private:
  // The OPEN(_ACK) signaling state.
  enum HandshakeState {
    kHandshakeInit,
    kHandshakeShouldSendOpen,
    kHandshakeShouldSendAck,
    kHandshakeWaitingForAck,
    kHandshakeReady
  };

  bool Init();
  void UpdateState();
  void SetState(DataState state);
  void DisconnectFromTransport();

  void DeliverQueuedReceivedData();

  void SendQueuedDataMessages();
  bool SendDataMessage(const DataBuffer& buffer, bool queue_if_blocked);
  bool QueueSendDataMessage(const DataBuffer& buffer);

  void SendQueuedControlMessages();
  void QueueControlMessage(const rtc::CopyOnWriteBuffer& buffer);
  bool SendControlMessage(const rtc::CopyOnWriteBuffer& buffer);

  rtc::Thread* const signaling_thread_;
  rtc::Thread* const network_thread_;
  const int internal_id_;
  const std::string label_;
  const InternalDataChannelInit config_;
  DataChannelObserver* observer_ RTC_GUARDED_BY(signaling_thread_) = nullptr;
  DataState state_ RTC_GUARDED_BY(signaling_thread_) = kConnecting;
  RTCError error_ RTC_GUARDED_BY(signaling_thread_);
  uint32_t messages_sent_ RTC_GUARDED_BY(signaling_thread_) = 0;
  uint64_t bytes_sent_ RTC_GUARDED_BY(signaling_thread_) = 0;
  uint32_t messages_received_ RTC_GUARDED_BY(signaling_thread_) = 0;
  uint64_t bytes_received_ RTC_GUARDED_BY(signaling_thread_) = 0;
  SctpDataChannelControllerInterface* const controller_
      RTC_GUARDED_BY(signaling_thread_);
  bool controller_detached_ RTC_GUARDED_BY(signaling_thread_) = false;
  HandshakeState handshake_state_ RTC_GUARDED_BY(signaling_thread_) =
      kHandshakeInit;
  bool connected_to_transport_ RTC_GUARDED_BY(signaling_thread_) = false;
  bool writable_ RTC_GUARDED_BY(signaling_thread_) = false;
  // Did we already start the graceful SCTP closing procedure?
  bool started_closing_procedure_ RTC_GUARDED_BY(signaling_thread_) = false;
  // Control messages that always have to get sent out before any queued
  // data.
  PacketQueue queued_control_data_ RTC_GUARDED_BY(signaling_thread_);
  PacketQueue queued_received_data_ RTC_GUARDED_BY(signaling_thread_);
  PacketQueue queued_send_data_ RTC_GUARDED_BY(signaling_thread_);
};

// Downcast a PeerConnectionInterface that points to a proxy object
// to its underlying SctpDataChannel object. For testing only.
SctpDataChannel* DowncastProxiedDataChannelInterfaceToSctpDataChannelForTesting(
    DataChannelInterface* channel);

}  // namespace webrtc

#endif  // PC_SCTP_DATA_CHANNEL_H_
