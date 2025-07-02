/*
 *  Copyright 2023 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#ifndef PC_TEST_MOCK_VOICE_MEDIA_RECEIVE_CHANNEL_INTERFACE_H_
#define PC_TEST_MOCK_VOICE_MEDIA_RECEIVE_CHANNEL_INTERFACE_H_

#include <memory>
#include <set>
#include <string>
#include <vector>

#include "api/call/audio_sink.h"
#include "media/base/media_channel.h"
#include "media/base/media_channel_impl.h"
#include "modules/rtp_rtcp/source/rtp_packet_received.h"
#include "rtc_base/gunit.h"
#include "test/gmock.h"
#include "test/gtest.h"

namespace cricket {

class MockVoiceMediaReceiveChannelInterface
    : public VoiceMediaReceiveChannelInterface {
 public:
  MockVoiceMediaReceiveChannelInterface() {
    ON_CALL(*this, AsVoiceReceiveChannel).WillByDefault(testing::Return(this));
  }

  // VoiceMediaReceiveChannelInterface
  MOCK_METHOD(bool,
              SetReceiverParameters,
              (const AudioReceiverParameters& params),
              (override));
  MOCK_METHOD(webrtc::RtpParameters,
              GetRtpReceiverParameters,
              (uint32_t ssrc),
              (const, override));
  MOCK_METHOD(std::vector<webrtc::RtpSource>,
              GetSources,
              (uint32_t ssrc),
              (const, override));
  MOCK_METHOD(webrtc::RtpParameters,
              GetDefaultRtpReceiveParameters,
              (),
              (const, override));
  MOCK_METHOD(void, SetPlayout, (bool playout), (override));
  MOCK_METHOD(bool,
              SetOutputVolume,
              (uint32_t ssrc, double volume),
              (override));
  MOCK_METHOD(bool, SetDefaultOutputVolume, (double volume), (override));
  MOCK_METHOD(void,
              SetRawAudioSink,
              (uint32_t ssrc, std::unique_ptr<webrtc::AudioSinkInterface> sink),
              (override));
  MOCK_METHOD(void,
              SetDefaultRawAudioSink,
              (std::unique_ptr<webrtc::AudioSinkInterface> sink),
              (override));
  MOCK_METHOD(bool,
              GetStats,
              (VoiceMediaReceiveInfo * stats, bool reset_legacy),
              (override));
  MOCK_METHOD(void, SetReceiveNackEnabled, (bool enabled), (override));
  MOCK_METHOD(void, SetReceiveNonSenderRttEnabled, (bool enabled), (override));

  // MediaReceiveChannelInterface
  MOCK_METHOD(VideoMediaReceiveChannelInterface*,
              AsVideoReceiveChannel,
              (),
              (override));
  MOCK_METHOD(VoiceMediaReceiveChannelInterface*,
              AsVoiceReceiveChannel,
              (),
              (override));
  MOCK_METHOD(cricket::MediaType, media_type, (), (const, override));
  MOCK_METHOD(bool, AddRecvStream, (const StreamParams& sp), (override));
  MOCK_METHOD(bool, RemoveRecvStream, (uint32_t ssrc), (override));
  MOCK_METHOD(void, ResetUnsignaledRecvStream, (), (override));
  MOCK_METHOD(void,
              SetInterface,
              (MediaChannelNetworkInterface * iface),
              (override));
  MOCK_METHOD(void,
              OnPacketReceived,
              (const webrtc::RtpPacketReceived& packet),
              (override));
  MOCK_METHOD(absl::optional<uint32_t>,
              GetUnsignaledSsrc,
              (),
              (const, override));
  MOCK_METHOD(void,
              ChooseReceiverReportSsrc,
              (const std::set<uint32_t>& choices),
              (override));
  MOCK_METHOD(void, OnDemuxerCriteriaUpdatePending, (), (override));
  MOCK_METHOD(void, OnDemuxerCriteriaUpdateComplete, (), (override));
  MOCK_METHOD(
      void,
      SetFrameDecryptor,
      (uint32_t ssrc,
       rtc::scoped_refptr<webrtc::FrameDecryptorInterface> frame_decryptor),
      (override));
  MOCK_METHOD(
      void,
      SetDepacketizerToDecoderFrameTransformer,
      (uint32_t ssrc,
       rtc::scoped_refptr<webrtc::FrameTransformerInterface> frame_transformer),
      (override));
  MOCK_METHOD(bool,
              SetBaseMinimumPlayoutDelayMs,
              (uint32_t ssrc, int delay_ms),
              (override));
  MOCK_METHOD(absl::optional<int>,
              GetBaseMinimumPlayoutDelayMs,
              (uint32_t ssrc),
              (const, override));
};

static_assert(!std::is_abstract_v<MockVoiceMediaReceiveChannelInterface>, "");

}  // namespace cricket

#endif  // PC_TEST_MOCK_VOICE_MEDIA_RECEIVE_CHANNEL_INTERFACE_H_
