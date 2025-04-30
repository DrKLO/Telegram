/*
 *  Copyright 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef PC_TEST_MOCK_CHANNEL_INTERFACE_H_
#define PC_TEST_MOCK_CHANNEL_INTERFACE_H_

#include <string>
#include <vector>

#include "media/base/media_channel.h"
#include "pc/channel_interface.h"
#include "test/gmock.h"

namespace cricket {

// Mock class for BaseChannel.
// Use this class in unit tests to avoid dependecy on a specific
// implementation of BaseChannel.
class MockChannelInterface : public cricket::ChannelInterface {
 public:
  MOCK_METHOD(cricket::MediaType, media_type, (), (const, override));
  MOCK_METHOD(VideoChannel*, AsVideoChannel, (), (override));
  MOCK_METHOD(VoiceChannel*, AsVoiceChannel, (), (override));
  MOCK_METHOD(MediaSendChannelInterface*, media_send_channel, (), (override));
  MOCK_METHOD(VoiceMediaSendChannelInterface*,
              voice_media_send_channel,
              (),
              (override));
  MOCK_METHOD(VideoMediaSendChannelInterface*,
              video_media_send_channel,
              (),
              (override));
  MOCK_METHOD(MediaReceiveChannelInterface*,
              media_receive_channel,
              (),
              (override));
  MOCK_METHOD(VoiceMediaReceiveChannelInterface*,
              voice_media_receive_channel,
              (),
              (override));
  MOCK_METHOD(VideoMediaReceiveChannelInterface*,
              video_media_receive_channel,
              (),
              (override));
  MOCK_METHOD(absl::string_view, transport_name, (), (const, override));
  MOCK_METHOD(const std::string&, mid, (), (const, override));
  MOCK_METHOD(void, Enable, (bool), (override));
  MOCK_METHOD(void,
              SetFirstPacketReceivedCallback,
              (std::function<void()>),
              (override));
  MOCK_METHOD(bool,
              SetLocalContent,
              (const cricket::MediaContentDescription*,
               webrtc::SdpType,
               std::string&),
              (override));
  MOCK_METHOD(bool,
              SetRemoteContent,
              (const cricket::MediaContentDescription*,
               webrtc::SdpType,
               std::string&),
              (override));
  MOCK_METHOD(bool, SetPayloadTypeDemuxingEnabled, (bool), (override));
  MOCK_METHOD(const std::vector<StreamParams>&,
              local_streams,
              (),
              (const, override));
  MOCK_METHOD(const std::vector<StreamParams>&,
              remote_streams,
              (),
              (const, override));
  MOCK_METHOD(bool,
              SetRtpTransport,
              (webrtc::RtpTransportInternal*),
              (override));
};

}  // namespace cricket

#endif  // PC_TEST_MOCK_CHANNEL_INTERFACE_H_
