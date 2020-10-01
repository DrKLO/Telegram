/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_REMOTE_BITRATE_ESTIMATOR_TOOLS_BWE_RTP_H_
#define MODULES_REMOTE_BITRATE_ESTIMATOR_TOOLS_BWE_RTP_H_

#include <memory>
#include <string>

namespace webrtc {
class Clock;
class RemoteBitrateEstimator;
class RemoteBitrateObserver;
class RtpHeaderParser;
namespace test {
class RtpFileReader;
}
}  // namespace webrtc

std::unique_ptr<webrtc::RtpHeaderParser> ParseArgsAndSetupEstimator(
    int argc,
    char** argv,
    webrtc::Clock* clock,
    webrtc::RemoteBitrateObserver* observer,
    std::unique_ptr<webrtc::test::RtpFileReader>* rtp_reader,
    std::unique_ptr<webrtc::RemoteBitrateEstimator>* estimator,
    std::string* estimator_used);

#endif  // MODULES_REMOTE_BITRATE_ESTIMATOR_TOOLS_BWE_RTP_H_
