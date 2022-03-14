/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MEDIA_SCTP_DCSCTP_TRANSPORT_H_
#define MEDIA_SCTP_DCSCTP_TRANSPORT_H_

#include <memory>
#include <string>

#include "absl/strings/string_view.h"
#include "absl/types/optional.h"
#include "api/array_view.h"
#include "media/sctp/sctp_transport_internal.h"
#include "net/dcsctp/public/dcsctp_options.h"
#include "net/dcsctp/public/dcsctp_socket.h"
#include "net/dcsctp/public/types.h"
#include "net/dcsctp/timer/task_queue_timeout.h"
#include "p2p/base/packet_transport_internal.h"
#include "rtc_base/copy_on_write_buffer.h"
#include "rtc_base/random.h"
#include "rtc_base/third_party/sigslot/sigslot.h"
#include "rtc_base/thread.h"
#include "system_wrappers/include/clock.h"

namespace webrtc {

class DcSctpTransport : public cricket::SctpTransportInternal,
                        public dcsctp::DcSctpSocketCallbacks,
                        public sigslot::has_slots<> {
 public:
  DcSctpTransport(rtc::Thread* network_thread,
                  rtc::PacketTransportInternal* transport,
                  Clock* clock);
  ~DcSctpTransport() override;

  // cricket::SctpTransportInternal
  void SetDtlsTransport(rtc::PacketTransportInternal* transport) override;
  bool Start(int local_sctp_port,
             int remote_sctp_port,
             int max_message_size) override;
  bool OpenStream(int sid) override;
  bool ResetStream(int sid) override;
  bool SendData(int sid,
                const SendDataParams& params,
                const rtc::CopyOnWriteBuffer& payload,
                cricket::SendDataResult* result = nullptr) override;
  bool ReadyToSendData() override;
  int max_message_size() const override;
  absl::optional<int> max_outbound_streams() const override;
  absl::optional<int> max_inbound_streams() const override;
  void set_debug_name_for_testing(const char* debug_name) override;

 private:
  // dcsctp::DcSctpSocketCallbacks
  dcsctp::SendPacketStatus SendPacketWithStatus(
      rtc::ArrayView<const uint8_t> data) override;
  std::unique_ptr<dcsctp::Timeout> CreateTimeout() override;
  dcsctp::TimeMs TimeMillis() override;
  uint32_t GetRandomInt(uint32_t low, uint32_t high) override;
  void OnTotalBufferedAmountLow() override;
  void OnMessageReceived(dcsctp::DcSctpMessage message) override;
  void OnError(dcsctp::ErrorKind error, absl::string_view message) override;
  void OnAborted(dcsctp::ErrorKind error, absl::string_view message) override;
  void OnConnected() override;
  void OnClosed() override;
  void OnConnectionRestarted() override;
  void OnStreamsResetFailed(
      rtc::ArrayView<const dcsctp::StreamID> outgoing_streams,
      absl::string_view reason) override;
  void OnStreamsResetPerformed(
      rtc::ArrayView<const dcsctp::StreamID> outgoing_streams) override;
  void OnIncomingStreamsReset(
      rtc::ArrayView<const dcsctp::StreamID> incoming_streams) override;

  // Transport callbacks
  void ConnectTransportSignals();
  void DisconnectTransportSignals();
  void OnTransportWritableState(rtc::PacketTransportInternal* transport);
  void OnTransportReadPacket(rtc::PacketTransportInternal* transport,
                             const char* data,
                             size_t length,
                             const int64_t& /* packet_time_us */,
                             int flags);
  void OnTransportClosed(rtc::PacketTransportInternal* transport);

  void MaybeConnectSocket();

  rtc::Thread* network_thread_;
  rtc::PacketTransportInternal* transport_;
  Clock* clock_;
  Random random_;

  dcsctp::TaskQueueTimeoutFactory task_queue_timeout_factory_;
  std::unique_ptr<dcsctp::DcSctpSocketInterface> socket_;
  std::string debug_name_ = "DcSctpTransport";
  rtc::CopyOnWriteBuffer receive_buffer_;

  bool ready_to_send_data_ = false;
};

}  // namespace webrtc

#endif  // MEDIA_SCTP_DCSCTP_TRANSPORT_H_
