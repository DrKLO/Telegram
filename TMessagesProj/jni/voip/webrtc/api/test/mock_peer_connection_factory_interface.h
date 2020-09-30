/*
 *  Copyright 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef API_TEST_MOCK_PEER_CONNECTION_FACTORY_INTERFACE_H_
#define API_TEST_MOCK_PEER_CONNECTION_FACTORY_INTERFACE_H_

#include <memory>
#include <string>

#include "api/peer_connection_interface.h"
#include "test/gmock.h"

namespace webrtc {

class MockPeerConnectionFactoryInterface final
    : public rtc::RefCountedObject<webrtc::PeerConnectionFactoryInterface> {
 public:
  rtc::scoped_refptr<MockPeerConnectionFactoryInterface> Create() {
    return new MockPeerConnectionFactoryInterface();
  }

  MOCK_METHOD(void, SetOptions, (const Options&), (override));
  MOCK_METHOD(rtc::scoped_refptr<PeerConnectionInterface>,
              CreatePeerConnection,
              (const PeerConnectionInterface::RTCConfiguration&,
               PeerConnectionDependencies),
              (override));
  MOCK_METHOD(rtc::scoped_refptr<PeerConnectionInterface>,
              CreatePeerConnection,
              (const PeerConnectionInterface::RTCConfiguration&,
               std::unique_ptr<cricket::PortAllocator>,
               std::unique_ptr<rtc::RTCCertificateGeneratorInterface>,
               PeerConnectionObserver*),
              (override));
  MOCK_METHOD(RtpCapabilities,
              GetRtpSenderCapabilities,
              (cricket::MediaType),
              (const override));
  MOCK_METHOD(RtpCapabilities,
              GetRtpReceiverCapabilities,
              (cricket::MediaType),
              (const override));
  MOCK_METHOD(rtc::scoped_refptr<MediaStreamInterface>,
              CreateLocalMediaStream,
              (const std::string&),
              (override));
  MOCK_METHOD(rtc::scoped_refptr<AudioSourceInterface>,
              CreateAudioSource,
              (const cricket::AudioOptions&),
              (override));
  MOCK_METHOD(rtc::scoped_refptr<VideoTrackInterface>,
              CreateVideoTrack,
              (const std::string&, VideoTrackSourceInterface*),
              (override));
  MOCK_METHOD(rtc::scoped_refptr<AudioTrackInterface>,
              CreateAudioTrack,
              (const std::string&, AudioSourceInterface*),
              (override));
  MOCK_METHOD(bool, StartAecDump, (FILE*, int64_t), (override));
  MOCK_METHOD(void, StopAecDump, (), (override));

 protected:
  MockPeerConnectionFactoryInterface() = default;
};

}  // namespace webrtc

#endif  // API_TEST_MOCK_PEER_CONNECTION_FACTORY_INTERFACE_H_
