/*
 *  Copyright 2019 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef PC_SCTP_DATA_CHANNEL_TRANSPORT_H_
#define PC_SCTP_DATA_CHANNEL_TRANSPORT_H_

#include "api/rtc_error.h"
#include "api/transport/data_channel_transport_interface.h"
#include "media/base/media_channel.h"
#include "media/sctp/sctp_transport_internal.h"
#include "rtc_base/copy_on_write_buffer.h"
#include "rtc_base/third_party/sigslot/sigslot.h"

namespace webrtc {

// SCTP implementation of DataChannelTransportInterface.
class SctpDataChannelTransport : public DataChannelTransportInterface,
                                 public sigslot::has_slots<> {
 public:
  explicit SctpDataChannelTransport(
      cricket::SctpTransportInternal* sctp_transport);

  RTCError OpenChannel(int channel_id) override;
  RTCError SendData(int channel_id,
                    const SendDataParams& params,
                    const rtc::CopyOnWriteBuffer& buffer) override;
  RTCError CloseChannel(int channel_id) override;
  void SetDataSink(DataChannelSink* sink) override;
  bool IsReadyToSend() const override;

 private:
  void OnReadyToSendData();
  void OnDataReceived(const cricket::ReceiveDataParams& params,
                      const rtc::CopyOnWriteBuffer& buffer);
  void OnClosingProcedureStartedRemotely(int channel_id);
  void OnClosingProcedureComplete(int channel_id);
  void OnClosedAbruptly(RTCError error);

  cricket::SctpTransportInternal* const sctp_transport_;

  DataChannelSink* sink_ = nullptr;
  bool ready_to_send_ = false;
};

}  // namespace webrtc

#endif  // PC_SCTP_DATA_CHANNEL_TRANSPORT_H_
