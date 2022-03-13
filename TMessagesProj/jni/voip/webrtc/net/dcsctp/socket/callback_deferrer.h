/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#ifndef NET_DCSCTP_SOCKET_CALLBACK_DEFERRER_H_
#define NET_DCSCTP_SOCKET_CALLBACK_DEFERRER_H_

#include <cstdint>
#include <functional>
#include <memory>
#include <string>
#include <utility>
#include <vector>

#include "absl/strings/string_view.h"
#include "api/array_view.h"
#include "api/ref_counted_base.h"
#include "api/scoped_refptr.h"
#include "net/dcsctp/public/dcsctp_message.h"
#include "net/dcsctp/public/dcsctp_socket.h"
#include "rtc_base/ref_counted_object.h"

namespace dcsctp {
// Defers callbacks until they can be safely triggered.
//
// There are a lot of callbacks from the dcSCTP library to the client,
// such as when messages are received or streams are closed. When the client
// receives these callbacks, the client is expected to be able to call into the
// library - from within the callback. For example, sending a reply message when
// a certain SCTP message has been received, or to reconnect when the connection
// was closed for any reason. This means that the dcSCTP library must always be
// in a consistent and stable state when these callbacks are delivered, and to
// ensure that's the case, callbacks are not immediately delivered from where
// they originate, but instead queued (deferred) by this class. At the end of
// any public API method that may result in callbacks, they are triggered and
// then delivered.
//
// There are a number of exceptions, which is clearly annotated in the API.
class CallbackDeferrer : public DcSctpSocketCallbacks {
 public:
  class ScopedDeferrer {
   public:
    explicit ScopedDeferrer(CallbackDeferrer& callback_deferrer)
        : callback_deferrer_(callback_deferrer) {
      callback_deferrer_.Prepare();
    }

    ~ScopedDeferrer() { callback_deferrer_.TriggerDeferred(); }

   private:
    CallbackDeferrer& callback_deferrer_;
  };

  explicit CallbackDeferrer(DcSctpSocketCallbacks& underlying)
      : underlying_(underlying) {}

  // Implementation of DcSctpSocketCallbacks
  SendPacketStatus SendPacketWithStatus(
      rtc::ArrayView<const uint8_t> data) override;
  std::unique_ptr<Timeout> CreateTimeout() override;
  TimeMs TimeMillis() override;
  uint32_t GetRandomInt(uint32_t low, uint32_t high) override;
  void OnMessageReceived(DcSctpMessage message) override;
  void OnError(ErrorKind error, absl::string_view message) override;
  void OnAborted(ErrorKind error, absl::string_view message) override;
  void OnConnected() override;
  void OnClosed() override;
  void OnConnectionRestarted() override;
  void OnStreamsResetFailed(rtc::ArrayView<const StreamID> outgoing_streams,
                            absl::string_view reason) override;
  void OnStreamsResetPerformed(
      rtc::ArrayView<const StreamID> outgoing_streams) override;
  void OnIncomingStreamsReset(
      rtc::ArrayView<const StreamID> incoming_streams) override;
  void OnBufferedAmountLow(StreamID stream_id) override;
  void OnTotalBufferedAmountLow() override;

 private:
  void Prepare();
  void TriggerDeferred();

  DcSctpSocketCallbacks& underlying_;
  bool prepared_ = false;
  std::vector<std::function<void(DcSctpSocketCallbacks& cb)>> deferred_;
};
}  // namespace dcsctp

#endif  // NET_DCSCTP_SOCKET_CALLBACK_DEFERRER_H_
