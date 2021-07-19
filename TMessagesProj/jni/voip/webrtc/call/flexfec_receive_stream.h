/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef CALL_FLEXFEC_RECEIVE_STREAM_H_
#define CALL_FLEXFEC_RECEIVE_STREAM_H_

#include <stdint.h>

#include <string>
#include <vector>

#include "api/call/transport.h"
#include "api/rtp_headers.h"
#include "api/rtp_parameters.h"
#include "call/rtp_packet_sink_interface.h"

namespace webrtc {

class FlexfecReceiveStream : public RtpPacketSinkInterface {
 public:
  ~FlexfecReceiveStream() override = default;

  struct Stats {
    std::string ToString(int64_t time_ms) const;

    // TODO(brandtr): Add appropriate stats here.
    int flexfec_bitrate_bps;
  };

  struct Config {
    explicit Config(Transport* rtcp_send_transport);
    Config(const Config&);
    ~Config();

    std::string ToString() const;

    // Returns true if all RTP information is available in order to
    // enable receiving FlexFEC.
    bool IsCompleteAndEnabled() const;

    // Payload type for FlexFEC.
    int payload_type = -1;

    // SSRC for FlexFEC stream to be received.
    uint32_t remote_ssrc = 0;

    // Vector containing a single element, corresponding to the SSRC of the
    // media stream being protected by this FlexFEC stream. The vector MUST have
    // size 1.
    //
    // TODO(brandtr): Update comment above when we support multistream
    // protection.
    std::vector<uint32_t> protected_media_ssrcs;

    // SSRC for RTCP reports to be sent.
    uint32_t local_ssrc = 0;

    // What RTCP mode to use in the reports.
    RtcpMode rtcp_mode = RtcpMode::kCompound;

    // Transport for outgoing RTCP packets.
    Transport* rtcp_send_transport = nullptr;

    // |transport_cc| is true whenever the send-side BWE RTCP feedback message
    // has been negotiated. This is a prerequisite for enabling send-side BWE.
    bool transport_cc = false;

    // RTP header extensions that have been negotiated for this track.
    std::vector<RtpExtension> rtp_header_extensions;
  };

  virtual Stats GetStats() const = 0;

  virtual const Config& GetConfig() const = 0;
};

}  // namespace webrtc

#endif  // CALL_FLEXFEC_RECEIVE_STREAM_H_
