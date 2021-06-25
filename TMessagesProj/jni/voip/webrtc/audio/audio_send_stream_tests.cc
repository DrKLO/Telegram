/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <string>
#include <utility>
#include <vector>

#include "modules/rtp_rtcp/include/rtp_header_extension_map.h"
#include "modules/rtp_rtcp/source/rtp_header_extensions.h"
#include "modules/rtp_rtcp/source/rtp_packet.h"
#include "test/call_test.h"
#include "test/field_trial.h"
#include "test/gtest.h"
#include "test/rtcp_packet_parser.h"

namespace webrtc {
namespace test {
namespace {

enum : int {  // The first valid value is 1.
  kAudioLevelExtensionId = 1,
  kTransportSequenceNumberExtensionId,
};

class AudioSendTest : public SendTest {
 public:
  AudioSendTest() : SendTest(CallTest::kDefaultTimeoutMs) {}

  size_t GetNumVideoStreams() const override { return 0; }
  size_t GetNumAudioStreams() const override { return 1; }
  size_t GetNumFlexfecStreams() const override { return 0; }
};
}  // namespace

using AudioSendStreamCallTest = CallTest;

TEST_F(AudioSendStreamCallTest, SupportsCName) {
  static std::string kCName = "PjqatC14dGfbVwGPUOA9IH7RlsFDbWl4AhXEiDsBizo=";
  class CNameObserver : public AudioSendTest {
   public:
    CNameObserver() = default;

   private:
    Action OnSendRtcp(const uint8_t* packet, size_t length) override {
      RtcpPacketParser parser;
      EXPECT_TRUE(parser.Parse(packet, length));
      if (parser.sdes()->num_packets() > 0) {
        EXPECT_EQ(1u, parser.sdes()->chunks().size());
        EXPECT_EQ(kCName, parser.sdes()->chunks()[0].cname);

        observation_complete_.Set();
      }

      return SEND_PACKET;
    }

    void ModifyAudioConfigs(
        AudioSendStream::Config* send_config,
        std::vector<AudioReceiveStream::Config>* receive_configs) override {
      send_config->rtp.c_name = kCName;
    }

    void PerformTest() override {
      EXPECT_TRUE(Wait()) << "Timed out while waiting for RTCP with CNAME.";
    }
  } test;

  RunBaseTest(&test);
}

TEST_F(AudioSendStreamCallTest, NoExtensionsByDefault) {
  class NoExtensionsObserver : public AudioSendTest {
   public:
    NoExtensionsObserver() = default;

   private:
    Action OnSendRtp(const uint8_t* packet, size_t length) override {
      RtpPacket rtp_packet;
      EXPECT_TRUE(rtp_packet.Parse(packet, length));  // rtp packet is valid.
      EXPECT_EQ(packet[0] & 0b0001'0000, 0);          // extension bit not set.

      observation_complete_.Set();
      return SEND_PACKET;
    }

    void ModifyAudioConfigs(
        AudioSendStream::Config* send_config,
        std::vector<AudioReceiveStream::Config>* receive_configs) override {
      send_config->rtp.extensions.clear();
    }

    void PerformTest() override {
      EXPECT_TRUE(Wait()) << "Timed out while waiting for a single RTP packet.";
    }
  } test;

  RunBaseTest(&test);
}

TEST_F(AudioSendStreamCallTest, SupportsAudioLevel) {
  class AudioLevelObserver : public AudioSendTest {
   public:
    AudioLevelObserver() : AudioSendTest() {
      extensions_.Register<AudioLevel>(kAudioLevelExtensionId);
    }

    Action OnSendRtp(const uint8_t* packet, size_t length) override {
      RtpPacket rtp_packet(&extensions_);
      EXPECT_TRUE(rtp_packet.Parse(packet, length));

      uint8_t audio_level = 0;
      bool voice = false;
      EXPECT_TRUE(rtp_packet.GetExtension<AudioLevel>(&voice, &audio_level));
      if (audio_level != 0) {
        // Wait for at least one packet with a non-zero level.
        observation_complete_.Set();
      } else {
        RTC_LOG(LS_WARNING) << "Got a packet with zero audioLevel - waiting"
                               " for another packet...";
      }

      return SEND_PACKET;
    }

    void ModifyAudioConfigs(
        AudioSendStream::Config* send_config,
        std::vector<AudioReceiveStream::Config>* receive_configs) override {
      send_config->rtp.extensions.clear();
      send_config->rtp.extensions.push_back(
          RtpExtension(RtpExtension::kAudioLevelUri, kAudioLevelExtensionId));
    }

    void PerformTest() override {
      EXPECT_TRUE(Wait()) << "Timed out while waiting for single RTP packet.";
    }

   private:
    RtpHeaderExtensionMap extensions_;
  } test;

  RunBaseTest(&test);
}

class TransportWideSequenceNumberObserver : public AudioSendTest {
 public:
  explicit TransportWideSequenceNumberObserver(bool expect_sequence_number)
      : AudioSendTest(), expect_sequence_number_(expect_sequence_number) {
    extensions_.Register<TransportSequenceNumber>(
        kTransportSequenceNumberExtensionId);
  }

 private:
  Action OnSendRtp(const uint8_t* packet, size_t length) override {
    RtpPacket rtp_packet(&extensions_);
    EXPECT_TRUE(rtp_packet.Parse(packet, length));

    EXPECT_EQ(rtp_packet.HasExtension<TransportSequenceNumber>(),
              expect_sequence_number_);
    EXPECT_FALSE(rtp_packet.HasExtension<TransmissionOffset>());
    EXPECT_FALSE(rtp_packet.HasExtension<AbsoluteSendTime>());

    observation_complete_.Set();

    return SEND_PACKET;
  }

  void ModifyAudioConfigs(
      AudioSendStream::Config* send_config,
      std::vector<AudioReceiveStream::Config>* receive_configs) override {
    send_config->rtp.extensions.clear();
    send_config->rtp.extensions.push_back(
        RtpExtension(RtpExtension::kTransportSequenceNumberUri,
                     kTransportSequenceNumberExtensionId));
  }

  void PerformTest() override {
    EXPECT_TRUE(Wait()) << "Timed out while waiting for a single RTP packet.";
  }
  const bool expect_sequence_number_;
  RtpHeaderExtensionMap extensions_;
};

TEST_F(AudioSendStreamCallTest, SendsTransportWideSequenceNumbersInFieldTrial) {
  TransportWideSequenceNumberObserver test(/*expect_sequence_number=*/true);
  RunBaseTest(&test);
}

TEST_F(AudioSendStreamCallTest, SendDtmf) {
  static const uint8_t kDtmfPayloadType = 120;
  static const int kDtmfPayloadFrequency = 8000;
  static const int kDtmfEventFirst = 12;
  static const int kDtmfEventLast = 31;
  static const int kDtmfDuration = 50;
  class DtmfObserver : public AudioSendTest {
   public:
    DtmfObserver() = default;

   private:
    Action OnSendRtp(const uint8_t* packet, size_t length) override {
      RtpPacket rtp_packet;
      EXPECT_TRUE(rtp_packet.Parse(packet, length));

      if (rtp_packet.PayloadType() == kDtmfPayloadType) {
        EXPECT_EQ(rtp_packet.headers_size(), 12u);
        EXPECT_EQ(rtp_packet.size(), 16u);
        const int event = rtp_packet.payload()[0];
        if (event != expected_dtmf_event_) {
          ++expected_dtmf_event_;
          EXPECT_EQ(event, expected_dtmf_event_);
          if (expected_dtmf_event_ == kDtmfEventLast) {
            observation_complete_.Set();
          }
        }
      }

      return SEND_PACKET;
    }

    void OnAudioStreamsCreated(
        AudioSendStream* send_stream,
        const std::vector<AudioReceiveStream*>& receive_streams) override {
      // Need to start stream here, else DTMF events are dropped.
      send_stream->Start();
      for (int event = kDtmfEventFirst; event <= kDtmfEventLast; ++event) {
        send_stream->SendTelephoneEvent(kDtmfPayloadType, kDtmfPayloadFrequency,
                                        event, kDtmfDuration);
      }
    }

    void PerformTest() override {
      EXPECT_TRUE(Wait()) << "Timed out while waiting for DTMF stream.";
    }

    int expected_dtmf_event_ = kDtmfEventFirst;
  } test;

  RunBaseTest(&test);
}

}  // namespace test
}  // namespace webrtc
