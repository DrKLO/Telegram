/*
 *  Copyright 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef PC_RTP_RECEIVER_PROXY_H_
#define PC_RTP_RECEIVER_PROXY_H_

#include <string>
#include <vector>

#include "api/rtp_receiver_interface.h"
#include "pc/proxy.h"

namespace webrtc {

// Define proxy for RtpReceiverInterface.
// TODO(deadbeef): Move this to .cc file. What threads methods are called on is
// an implementation detail.
BEGIN_PROXY_MAP(RtpReceiver)
PROXY_PRIMARY_THREAD_DESTRUCTOR()
BYPASS_PROXY_CONSTMETHOD0(rtc::scoped_refptr<MediaStreamTrackInterface>, track)
PROXY_CONSTMETHOD0(rtc::scoped_refptr<DtlsTransportInterface>, dtls_transport)
PROXY_CONSTMETHOD0(std::vector<std::string>, stream_ids)
PROXY_CONSTMETHOD0(std::vector<rtc::scoped_refptr<MediaStreamInterface>>,
                   streams)
BYPASS_PROXY_CONSTMETHOD0(cricket::MediaType, media_type)
BYPASS_PROXY_CONSTMETHOD0(std::string, id)
PROXY_SECONDARY_CONSTMETHOD0(RtpParameters, GetParameters)
PROXY_METHOD1(void, SetObserver, RtpReceiverObserverInterface*)
PROXY_SECONDARY_METHOD1(void,
                        SetJitterBufferMinimumDelay,
                        absl::optional<double>)
PROXY_SECONDARY_CONSTMETHOD0(std::vector<RtpSource>, GetSources)
// TODO(bugs.webrtc.org/12772): Remove.
PROXY_SECONDARY_METHOD1(void,
                        SetFrameDecryptor,
                        rtc::scoped_refptr<FrameDecryptorInterface>)
// TODO(bugs.webrtc.org/12772): Remove.
PROXY_SECONDARY_CONSTMETHOD0(rtc::scoped_refptr<FrameDecryptorInterface>,
                             GetFrameDecryptor)
PROXY_SECONDARY_METHOD1(void,
                        SetDepacketizerToDecoderFrameTransformer,
                        rtc::scoped_refptr<FrameTransformerInterface>)
END_PROXY_MAP(RtpReceiver)

}  // namespace webrtc

#endif  // PC_RTP_RECEIVER_PROXY_H_
