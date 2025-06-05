/*
 *  Copyright 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef PC_PEER_CONNECTION_FACTORY_PROXY_H_
#define PC_PEER_CONNECTION_FACTORY_PROXY_H_

#include <memory>
#include <string>
#include <utility>

#include "api/peer_connection_interface.h"
#include "pc/proxy.h"

namespace webrtc {

// TODO(deadbeef): Move this to .cc file. What threads methods are called on is
// an implementation detail.
BEGIN_PROXY_MAP(PeerConnectionFactory)
PROXY_PRIMARY_THREAD_DESTRUCTOR()
PROXY_METHOD1(void, SetOptions, const Options&)
PROXY_METHOD2(RTCErrorOr<rtc::scoped_refptr<PeerConnectionInterface>>,
              CreatePeerConnectionOrError,
              const PeerConnectionInterface::RTCConfiguration&,
              PeerConnectionDependencies)
PROXY_CONSTMETHOD1(RtpCapabilities,
                   GetRtpSenderCapabilities,
                   cricket::MediaType)
PROXY_CONSTMETHOD1(RtpCapabilities,
                   GetRtpReceiverCapabilities,
                   cricket::MediaType)
PROXY_METHOD1(rtc::scoped_refptr<MediaStreamInterface>,
              CreateLocalMediaStream,
              const std::string&)
PROXY_METHOD1(rtc::scoped_refptr<AudioSourceInterface>,
              CreateAudioSource,
              const cricket::AudioOptions&)
PROXY_METHOD2(rtc::scoped_refptr<VideoTrackInterface>,
              CreateVideoTrack,
              rtc::scoped_refptr<VideoTrackSourceInterface>,
              absl::string_view)
PROXY_METHOD2(rtc::scoped_refptr<AudioTrackInterface>,
              CreateAudioTrack,
              const std::string&,
              AudioSourceInterface*)
PROXY_SECONDARY_METHOD2(bool, StartAecDump, FILE*, int64_t)
PROXY_SECONDARY_METHOD0(void, StopAecDump)
END_PROXY_MAP(PeerConnectionFactory)

}  // namespace webrtc

#endif  // PC_PEER_CONNECTION_FACTORY_PROXY_H_
