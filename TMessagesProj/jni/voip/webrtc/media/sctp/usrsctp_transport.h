/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MEDIA_SCTP_USRSCTP_TRANSPORT_H_
#define MEDIA_SCTP_USRSCTP_TRANSPORT_H_

#include <errno.h>

#include <cstdint>
#include <map>
#include <memory>
#include <set>
#include <string>
#include <vector>

#include "absl/types/optional.h"
#include "rtc_base/buffer.h"
#include "rtc_base/constructor_magic.h"
#include "rtc_base/copy_on_write_buffer.h"
#include "rtc_base/task_utils/pending_task_safety_flag.h"
#include "rtc_base/third_party/sigslot/sigslot.h"
#include "rtc_base/thread.h"
// For SendDataParams/ReceiveDataParams.
#include "media/base/media_channel.h"
#include "media/sctp/sctp_transport_internal.h"

// Defined by "usrsctplib/usrsctp.h"
struct sockaddr_conn;
struct sctp_assoc_change;
struct sctp_rcvinfo;
struct sctp_stream_reset_event;
struct sctp_sendv_spa;

// Defined by <sys/socket.h>
struct socket;
namespace cricket {

// Holds data to be passed on to a transport.
struct SctpInboundPacket;

// From transport calls, data flows like this:
// [network thread (although it can in princple be another thread)]
//  1.  SctpTransport::SendData(data)
//  2.  usrsctp_sendv(data)
// [network thread returns; sctp thread then calls the following]
//  3.  OnSctpOutboundPacket(wrapped_data)
// [sctp thread returns having async invoked on the network thread]
//  4.  SctpTransport::OnPacketFromSctpToNetwork(wrapped_data)
//  5.  DtlsTransport::SendPacket(wrapped_data)
//  6.  ... across network ... a packet is sent back ...
//  7.  SctpTransport::OnPacketReceived(wrapped_data)
//  8.  usrsctp_conninput(wrapped_data)
// [network thread returns; sctp thread then calls the following]
//  9.  OnSctpInboundData(data)
//  10. SctpTransport::OnDataFromSctpToTransport(data)
// [sctp thread returns having async invoked on the network thread]
//  11. SctpTransport::OnDataFromSctpToTransport(data)
//  12. SctpTransport::SignalDataReceived(data)
// [from the same thread, methods registered/connected to
//  SctpTransport are called with the recieved data]
class UsrsctpTransport : public SctpTransportInternal,
                         public sigslot::has_slots<> {
 public:
  // `network_thread` is where packets will be processed and callbacks from
  // this transport will be posted, and is the only thread on which public
  // methods can be called.
  // `transport` is not required (can be null).
  UsrsctpTransport(rtc::Thread* network_thread,
                   rtc::PacketTransportInternal* transport);
  ~UsrsctpTransport() override;

  // SctpTransportInternal overrides (see sctptransportinternal.h for comments).
  void SetDtlsTransport(rtc::PacketTransportInternal* transport) override;
  bool Start(int local_port, int remote_port, int max_message_size) override;
  bool OpenStream(int sid) override;
  bool ResetStream(int sid) override;
  bool SendData(int sid,
                const webrtc::SendDataParams& params,
                const rtc::CopyOnWriteBuffer& payload,
                SendDataResult* result = nullptr) override;
  bool ReadyToSendData() override;
  int max_message_size() const override { return max_message_size_; }
  absl::optional<int> max_outbound_streams() const override {
    return max_outbound_streams_;
  }
  absl::optional<int> max_inbound_streams() const override {
    return max_inbound_streams_;
  }
  void set_debug_name_for_testing(const char* debug_name) override {
    debug_name_ = debug_name;
  }
  void InjectDataOrNotificationFromSctpForTesting(const void* data,
                                                  size_t length,
                                                  struct sctp_rcvinfo rcv,
                                                  int flags);

  // Exposed to allow Post call from c-callbacks.
  // TODO(deadbeef): Remove this or at least make it return a const pointer.
  rtc::Thread* network_thread() const { return network_thread_; }

 private:
  // A message to be sent by the sctp library. This class is used to track the
  // progress of writing a single message to the sctp library in the presence of
  // partial writes. In this case, the Advance() function is provided in order
  // to advance over what has already been accepted by the sctp library and
  // avoid copying the remaining partial message buffer.
  class OutgoingMessage {
   public:
    OutgoingMessage(const rtc::CopyOnWriteBuffer& buffer,
                    int sid,
                    const webrtc::SendDataParams& send_params)
        : buffer_(buffer), sid_(sid), send_params_(send_params) {}

    // Advances the buffer by the incremented amount. Must not advance further
    // than the current data size.
    void Advance(size_t increment) {
      RTC_DCHECK_LE(increment + offset_, buffer_.size());
      offset_ += increment;
    }

    size_t size() const { return buffer_.size() - offset_; }

    const void* data() const { return buffer_.data() + offset_; }

    int sid() const { return sid_; }
    webrtc::SendDataParams send_params() const { return send_params_; }

   private:
    const rtc::CopyOnWriteBuffer buffer_;
    int sid_;
    const webrtc::SendDataParams send_params_;
    size_t offset_ = 0;
  };

  void ConnectTransportSignals();
  void DisconnectTransportSignals();

  // Creates the socket and connects.
  bool Connect();

  // Returns false when opening the socket failed.
  bool OpenSctpSocket();
  // Helpet method to set socket options.
  bool ConfigureSctpSocket();
  // Sets |sock_ |to nullptr.
  void CloseSctpSocket();

  // Sends a SCTP_RESET_STREAM for all streams in closing_ssids_.
  bool SendQueuedStreamResets();

  // Sets the "ready to send" flag and fires signal if needed.
  void SetReadyToSendData();

  // Sends the outgoing buffered message that was only partially accepted by the
  // sctp lib because it did not have enough space. Returns true if the entire
  // buffered message was accepted by the sctp lib.
  bool SendBufferedMessage();

  // Tries to send the `payload` on the usrsctp lib. The message will be
  // advanced by the amount that was sent.
  SendDataResult SendMessageInternal(OutgoingMessage* message);

  // Callbacks from DTLS transport.
  void OnWritableState(rtc::PacketTransportInternal* transport);
  virtual void OnPacketRead(rtc::PacketTransportInternal* transport,
                            const char* data,
                            size_t len,
                            const int64_t& packet_time_us,
                            int flags);
  void OnClosed(rtc::PacketTransportInternal* transport);

  // Methods related to usrsctp callbacks.
  void OnSendThresholdCallback();
  sockaddr_conn GetSctpSockAddr(int port);

  // Called using `invoker_` to send packet on the network.
  void OnPacketFromSctpToNetwork(const rtc::CopyOnWriteBuffer& buffer);

  // Called on the network thread.
  // Flags are standard socket API flags (RFC 6458).
  void OnDataOrNotificationFromSctp(const void* data,
                                    size_t length,
                                    struct sctp_rcvinfo rcv,
                                    int flags);
  // Called using `invoker_` to decide what to do with the data.
  void OnDataFromSctpToTransport(const ReceiveDataParams& params,
                                 const rtc::CopyOnWriteBuffer& buffer);
  // Called using `invoker_` to decide what to do with the notification.
  void OnNotificationFromSctp(const rtc::CopyOnWriteBuffer& buffer);
  void OnNotificationAssocChange(const sctp_assoc_change& change);

  void OnStreamResetEvent(const struct sctp_stream_reset_event* evt);

  // Responsible for marshalling incoming data to the transports listeners, and
  // outgoing data to the network interface.
  rtc::Thread* network_thread_;
  // Helps pass inbound/outbound packets asynchronously to the network thread.
  webrtc::ScopedTaskSafety task_safety_;
  // Underlying DTLS transport.
  rtc::PacketTransportInternal* transport_ = nullptr;

  // Track the data received from usrsctp between callbacks until the EOR bit
  // arrives.
  rtc::CopyOnWriteBuffer partial_incoming_message_;
  ReceiveDataParams partial_params_;
  int partial_flags_;
  // A message that was attempted to be sent, but was only partially accepted by
  // usrsctp lib with usrsctp_sendv() because it cannot buffer the full message.
  // This occurs because we explicitly set the EOR bit when sending, so
  // usrsctp_sendv() is not atomic.
  absl::optional<OutgoingMessage> partial_outgoing_message_;

  bool was_ever_writable_ = false;
  int local_port_ = kSctpDefaultPort;
  int remote_port_ = kSctpDefaultPort;
  int max_message_size_ = kSctpSendBufferSize;
  struct socket* sock_ = nullptr;  // The socket created by usrsctp_socket(...).

  // Has Start been called? Don't create SCTP socket until it has.
  bool started_ = false;
  // Are we ready to queue data (SCTP socket created, and not blocked due to
  // congestion control)? Different than `transport_`'s "ready to send".
  bool ready_to_send_data_ = false;

  // Used to keep track of the status of each stream (or rather, each pair of
  // incoming/outgoing streams with matching IDs). It's specifically used to
  // keep track of the status of resets, but more information could be put here
  // later.
  //
  // See datachannel.h for a summary of the closing procedure.
  struct StreamStatus {
    // Closure initiated by application via ResetStream? Note that
    // this may be true while outgoing_reset_initiated is false if the outgoing
    // reset needed to be queued.
    bool closure_initiated = false;
    // Whether we've initiated the outgoing stream reset via
    // SCTP_RESET_STREAMS.
    bool outgoing_reset_initiated = false;
    // Whether usrsctp has indicated that the incoming/outgoing streams have
    // been reset. It's expected that the peer will reset its outgoing stream
    // (our incoming stream) after receiving the reset for our outgoing stream,
    // though older versions of chromium won't do this. See crbug.com/559394
    // for context.
    bool outgoing_reset_complete = false;
    bool incoming_reset_complete = false;

    // Some helper methods to improve code readability.
    bool is_open() const {
      return !closure_initiated && !incoming_reset_complete &&
             !outgoing_reset_complete;
    }
    // We need to send an outgoing reset if the application has closed the data
    // channel, or if we received a reset of the incoming stream from the
    // remote endpoint, indicating the data channel was closed remotely.
    bool need_outgoing_reset() const {
      return (incoming_reset_complete || closure_initiated) &&
             !outgoing_reset_initiated;
    }
    bool reset_complete() const {
      return outgoing_reset_complete && incoming_reset_complete;
    }
  };

  // Entries should only be removed from this map if `reset_complete` is
  // true.
  std::map<uint32_t, StreamStatus> stream_status_by_sid_;

  // A static human-readable name for debugging messages.
  const char* debug_name_ = "UsrsctpTransport";
  // Hides usrsctp interactions from this header file.
  class UsrSctpWrapper;
  // Number of channels negotiated. Not set before negotiation completes.
  absl::optional<int> max_outbound_streams_;
  absl::optional<int> max_inbound_streams_;

  // Used for associating this transport with the underlying sctp socket in
  // various callbacks.
  uintptr_t id_ = 0;

  friend class UsrsctpTransportMap;

  RTC_DISALLOW_COPY_AND_ASSIGN(UsrsctpTransport);
};

class UsrsctpTransportMap;

}  // namespace cricket

#endif  // MEDIA_SCTP_USRSCTP_TRANSPORT_H_
