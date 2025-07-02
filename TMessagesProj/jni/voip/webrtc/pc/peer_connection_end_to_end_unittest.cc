/*
 *  Copyright 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <stdint.h>

#include <cstddef>
#include <limits>
#include <memory>
#include <string>
#include <type_traits>
#include <utility>
#include <vector>

#include "absl/strings/match.h"
#include "absl/types/optional.h"
#include "api/audio_codecs/L16/audio_decoder_L16.h"
#include "api/audio_codecs/L16/audio_encoder_L16.h"
#include "api/audio_codecs/audio_codec_pair_id.h"
#include "api/audio_codecs/audio_decoder.h"
#include "api/audio_codecs/audio_decoder_factory.h"
#include "api/audio_codecs/audio_decoder_factory_template.h"
#include "api/audio_codecs/audio_encoder.h"
#include "api/audio_codecs/audio_encoder_factory.h"
#include "api/audio_codecs/audio_encoder_factory_template.h"
#include "api/audio_codecs/audio_format.h"
#include "api/audio_codecs/opus_audio_decoder_factory.h"
#include "api/audio_codecs/opus_audio_encoder_factory.h"
#include "api/audio_options.h"
#include "api/data_channel_interface.h"
#include "api/media_stream_interface.h"
#include "api/peer_connection_interface.h"
#include "api/rtc_error.h"
#include "api/scoped_refptr.h"
#include "media/sctp/sctp_transport_internal.h"
#include "rtc_base/checks.h"
#include "rtc_base/copy_on_write_buffer.h"
#include "rtc_base/gunit.h"
#include "rtc_base/physical_socket_server.h"
#include "rtc_base/third_party/sigslot/sigslot.h"
#include "rtc_base/thread.h"
#include "test/gmock.h"
#include "test/gtest.h"

#ifdef WEBRTC_ANDROID
#include "pc/test/android_test_initializer.h"
#endif
#include "pc/test/peer_connection_test_wrapper.h"
// Notice that mockpeerconnectionobservers.h must be included after the above!
#include "pc/test/mock_peer_connection_observers.h"
#include "test/mock_audio_decoder.h"
#include "test/mock_audio_decoder_factory.h"
#include "test/mock_audio_encoder_factory.h"

using ::testing::_;
using ::testing::AtLeast;
using ::testing::Invoke;
using ::testing::StrictMock;
using ::testing::Values;

using webrtc::DataChannelInterface;
using webrtc::MediaStreamInterface;
using webrtc::PeerConnectionInterface;
using webrtc::SdpSemantics;

namespace {

const int kMaxWait = 25000;

}  // namespace

class PeerConnectionEndToEndBaseTest : public sigslot::has_slots<>,
                                       public ::testing::Test {
 public:
  typedef std::vector<rtc::scoped_refptr<DataChannelInterface>> DataChannelList;

  explicit PeerConnectionEndToEndBaseTest(SdpSemantics sdp_semantics)
      : network_thread_(std::make_unique<rtc::Thread>(&pss_)),
        worker_thread_(rtc::Thread::Create()) {
    RTC_CHECK(network_thread_->Start());
    RTC_CHECK(worker_thread_->Start());
    caller_ = rtc::make_ref_counted<PeerConnectionTestWrapper>(
        "caller", &pss_, network_thread_.get(), worker_thread_.get());
    callee_ = rtc::make_ref_counted<PeerConnectionTestWrapper>(
        "callee", &pss_, network_thread_.get(), worker_thread_.get());
    webrtc::PeerConnectionInterface::IceServer ice_server;
    ice_server.uri = "stun:stun.l.google.com:19302";
    config_.servers.push_back(ice_server);
    config_.sdp_semantics = sdp_semantics;

#ifdef WEBRTC_ANDROID
    webrtc::InitializeAndroidObjects();
#endif
  }

  void CreatePcs(
      rtc::scoped_refptr<webrtc::AudioEncoderFactory> audio_encoder_factory1,
      rtc::scoped_refptr<webrtc::AudioDecoderFactory> audio_decoder_factory1,
      rtc::scoped_refptr<webrtc::AudioEncoderFactory> audio_encoder_factory2,
      rtc::scoped_refptr<webrtc::AudioDecoderFactory> audio_decoder_factory2) {
    EXPECT_TRUE(caller_->CreatePc(config_, audio_encoder_factory1,
                                  audio_decoder_factory1));
    EXPECT_TRUE(callee_->CreatePc(config_, audio_encoder_factory2,
                                  audio_decoder_factory2));
    PeerConnectionTestWrapper::Connect(caller_.get(), callee_.get());

    caller_->SignalOnDataChannel.connect(
        this, &PeerConnectionEndToEndBaseTest::OnCallerAddedDataChanel);
    callee_->SignalOnDataChannel.connect(
        this, &PeerConnectionEndToEndBaseTest::OnCalleeAddedDataChannel);
  }

  void CreatePcs(
      rtc::scoped_refptr<webrtc::AudioEncoderFactory> audio_encoder_factory,
      rtc::scoped_refptr<webrtc::AudioDecoderFactory> audio_decoder_factory) {
    CreatePcs(audio_encoder_factory, audio_decoder_factory,
              audio_encoder_factory, audio_decoder_factory);
  }

  void GetAndAddUserMedia() {
    cricket::AudioOptions audio_options;
    GetAndAddUserMedia(true, audio_options, true);
  }

  void GetAndAddUserMedia(bool audio,
                          const cricket::AudioOptions& audio_options,
                          bool video) {
    caller_->GetAndAddUserMedia(audio, audio_options, video);
    callee_->GetAndAddUserMedia(audio, audio_options, video);
  }

  void Negotiate() {
    caller_->CreateOffer(
        webrtc::PeerConnectionInterface::RTCOfferAnswerOptions());
  }

  void WaitForCallEstablished() {
    caller_->WaitForCallEstablished();
    callee_->WaitForCallEstablished();
  }

  void WaitForConnection() {
    caller_->WaitForConnection();
    callee_->WaitForConnection();
  }

  void OnCallerAddedDataChanel(DataChannelInterface* dc) {
    caller_signaled_data_channels_.push_back(
        rtc::scoped_refptr<DataChannelInterface>(dc));
  }

  void OnCalleeAddedDataChannel(DataChannelInterface* dc) {
    callee_signaled_data_channels_.push_back(
        rtc::scoped_refptr<DataChannelInterface>(dc));
  }

  // Tests that `dc1` and `dc2` can send to and receive from each other.
  void TestDataChannelSendAndReceive(DataChannelInterface* dc1,
                                     DataChannelInterface* dc2,
                                     size_t size = 6) {
    std::unique_ptr<webrtc::MockDataChannelObserver> dc1_observer(
        new webrtc::MockDataChannelObserver(dc1));

    std::unique_ptr<webrtc::MockDataChannelObserver> dc2_observer(
        new webrtc::MockDataChannelObserver(dc2));

    static const std::string kDummyData =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    webrtc::DataBuffer buffer("");

    size_t sizeLeft = size;
    while (sizeLeft > 0) {
      size_t chunkSize =
          sizeLeft > kDummyData.length() ? kDummyData.length() : sizeLeft;
      buffer.data.AppendData(kDummyData.data(), chunkSize);
      sizeLeft -= chunkSize;
    }

    EXPECT_TRUE(dc1->Send(buffer));
    EXPECT_EQ_WAIT(buffer.data,
                   rtc::CopyOnWriteBuffer(dc2_observer->last_message()),
                   kMaxWait);

    EXPECT_TRUE(dc2->Send(buffer));
    EXPECT_EQ_WAIT(buffer.data,
                   rtc::CopyOnWriteBuffer(dc1_observer->last_message()),
                   kMaxWait);

    EXPECT_EQ(1U, dc1_observer->received_message_count());
    EXPECT_EQ(size, dc1_observer->last_message().length());
    EXPECT_EQ(1U, dc2_observer->received_message_count());
    EXPECT_EQ(size, dc2_observer->last_message().length());
  }

  void WaitForDataChannelsToOpen(DataChannelInterface* local_dc,
                                 const DataChannelList& remote_dc_list,
                                 size_t remote_dc_index) {
    EXPECT_EQ_WAIT(DataChannelInterface::kOpen, local_dc->state(), kMaxWait);

    ASSERT_TRUE_WAIT(remote_dc_list.size() > remote_dc_index, kMaxWait);
    EXPECT_EQ_WAIT(DataChannelInterface::kOpen,
                   remote_dc_list[remote_dc_index]->state(), kMaxWait);
    EXPECT_EQ(local_dc->id(), remote_dc_list[remote_dc_index]->id());
  }

  void CloseDataChannels(DataChannelInterface* local_dc,
                         const DataChannelList& remote_dc_list,
                         size_t remote_dc_index) {
    local_dc->Close();
    EXPECT_EQ_WAIT(DataChannelInterface::kClosed, local_dc->state(), kMaxWait);
    EXPECT_EQ_WAIT(DataChannelInterface::kClosed,
                   remote_dc_list[remote_dc_index]->state(), kMaxWait);
  }

 protected:
  rtc::AutoThread main_thread_;
  rtc::PhysicalSocketServer pss_;
  std::unique_ptr<rtc::Thread> network_thread_;
  std::unique_ptr<rtc::Thread> worker_thread_;
  rtc::scoped_refptr<PeerConnectionTestWrapper> caller_;
  rtc::scoped_refptr<PeerConnectionTestWrapper> callee_;
  DataChannelList caller_signaled_data_channels_;
  DataChannelList callee_signaled_data_channels_;
  webrtc::PeerConnectionInterface::RTCConfiguration config_;
};

class PeerConnectionEndToEndTest
    : public PeerConnectionEndToEndBaseTest,
      public ::testing::WithParamInterface<SdpSemantics> {
 protected:
  PeerConnectionEndToEndTest() : PeerConnectionEndToEndBaseTest(GetParam()) {}
};

namespace {

std::unique_ptr<webrtc::AudioDecoder> CreateForwardingMockDecoder(
    std::unique_ptr<webrtc::AudioDecoder> real_decoder) {
  class ForwardingMockDecoder : public StrictMock<webrtc::MockAudioDecoder> {
   public:
    explicit ForwardingMockDecoder(std::unique_ptr<AudioDecoder> decoder)
        : decoder_(std::move(decoder)) {}

   private:
    std::unique_ptr<AudioDecoder> decoder_;
  };

  const auto dec = real_decoder.get();  // For lambda capturing.
  auto mock_decoder =
      std::make_unique<ForwardingMockDecoder>(std::move(real_decoder));
  EXPECT_CALL(*mock_decoder, Channels())
      .Times(AtLeast(1))
      .WillRepeatedly(Invoke([dec] { return dec->Channels(); }));
  EXPECT_CALL(*mock_decoder, DecodeInternal(_, _, _, _, _))
      .Times(AtLeast(1))
      .WillRepeatedly(
          Invoke([dec](const uint8_t* encoded, size_t encoded_len,
                       int sample_rate_hz, int16_t* decoded,
                       webrtc::AudioDecoder::SpeechType* speech_type) {
            return dec->Decode(encoded, encoded_len, sample_rate_hz,
                               std::numeric_limits<size_t>::max(), decoded,
                               speech_type);
          }));
  EXPECT_CALL(*mock_decoder, Die());
  EXPECT_CALL(*mock_decoder, HasDecodePlc()).WillRepeatedly(Invoke([dec] {
    return dec->HasDecodePlc();
  }));
  EXPECT_CALL(*mock_decoder, PacketDuration(_, _))
      .Times(AtLeast(1))
      .WillRepeatedly(Invoke([dec](const uint8_t* encoded, size_t encoded_len) {
        return dec->PacketDuration(encoded, encoded_len);
      }));
  EXPECT_CALL(*mock_decoder, SampleRateHz())
      .Times(AtLeast(1))
      .WillRepeatedly(Invoke([dec] { return dec->SampleRateHz(); }));

  return std::move(mock_decoder);
}

rtc::scoped_refptr<webrtc::AudioDecoderFactory>
CreateForwardingMockDecoderFactory(
    webrtc::AudioDecoderFactory* real_decoder_factory) {
  rtc::scoped_refptr<webrtc::MockAudioDecoderFactory> mock_decoder_factory =
      rtc::make_ref_counted<StrictMock<webrtc::MockAudioDecoderFactory>>();
  EXPECT_CALL(*mock_decoder_factory, GetSupportedDecoders())
      .Times(AtLeast(1))
      .WillRepeatedly(Invoke([real_decoder_factory] {
        return real_decoder_factory->GetSupportedDecoders();
      }));
  EXPECT_CALL(*mock_decoder_factory, IsSupportedDecoder(_))
      .Times(AtLeast(1))
      .WillRepeatedly(
          Invoke([real_decoder_factory](const webrtc::SdpAudioFormat& format) {
            return real_decoder_factory->IsSupportedDecoder(format);
          }));
  EXPECT_CALL(*mock_decoder_factory, MakeAudioDecoderMock(_, _, _))
      .Times(AtLeast(2))
      .WillRepeatedly(
          Invoke([real_decoder_factory](
                     const webrtc::SdpAudioFormat& format,
                     absl::optional<webrtc::AudioCodecPairId> codec_pair_id,
                     std::unique_ptr<webrtc::AudioDecoder>* return_value) {
            auto real_decoder =
                real_decoder_factory->MakeAudioDecoder(format, codec_pair_id);
            *return_value =
                real_decoder
                    ? CreateForwardingMockDecoder(std::move(real_decoder))
                    : nullptr;
          }));
  return mock_decoder_factory;
}

struct AudioEncoderUnicornSparklesRainbow {
  using Config = webrtc::AudioEncoderL16::Config;
  static absl::optional<Config> SdpToConfig(webrtc::SdpAudioFormat format) {
    if (absl::EqualsIgnoreCase(format.name, "UnicornSparklesRainbow")) {
      const webrtc::CodecParameterMap expected_params = {{"num_horns", "1"}};
      EXPECT_EQ(expected_params, format.parameters);
      format.parameters.clear();
      format.name = "L16";
      return webrtc::AudioEncoderL16::SdpToConfig(format);
    } else {
      return absl::nullopt;
    }
  }
  static void AppendSupportedEncoders(
      std::vector<webrtc::AudioCodecSpec>* specs) {
    std::vector<webrtc::AudioCodecSpec> new_specs;
    webrtc::AudioEncoderL16::AppendSupportedEncoders(&new_specs);
    for (auto& spec : new_specs) {
      spec.format.name = "UnicornSparklesRainbow";
      EXPECT_TRUE(spec.format.parameters.empty());
      spec.format.parameters.emplace("num_horns", "1");
      specs->push_back(spec);
    }
  }
  static webrtc::AudioCodecInfo QueryAudioEncoder(const Config& config) {
    return webrtc::AudioEncoderL16::QueryAudioEncoder(config);
  }
  static std::unique_ptr<webrtc::AudioEncoder> MakeAudioEncoder(
      const Config& config,
      int payload_type,
      absl::optional<webrtc::AudioCodecPairId> codec_pair_id = absl::nullopt) {
    return webrtc::AudioEncoderL16::MakeAudioEncoder(config, payload_type,
                                                     codec_pair_id);
  }
};

struct AudioDecoderUnicornSparklesRainbow {
  using Config = webrtc::AudioDecoderL16::Config;
  static absl::optional<Config> SdpToConfig(webrtc::SdpAudioFormat format) {
    if (absl::EqualsIgnoreCase(format.name, "UnicornSparklesRainbow")) {
      const webrtc::CodecParameterMap expected_params = {{"num_horns", "1"}};
      EXPECT_EQ(expected_params, format.parameters);
      format.parameters.clear();
      format.name = "L16";
      return webrtc::AudioDecoderL16::SdpToConfig(format);
    } else {
      return absl::nullopt;
    }
  }
  static void AppendSupportedDecoders(
      std::vector<webrtc::AudioCodecSpec>* specs) {
    std::vector<webrtc::AudioCodecSpec> new_specs;
    webrtc::AudioDecoderL16::AppendSupportedDecoders(&new_specs);
    for (auto& spec : new_specs) {
      spec.format.name = "UnicornSparklesRainbow";
      EXPECT_TRUE(spec.format.parameters.empty());
      spec.format.parameters.emplace("num_horns", "1");
      specs->push_back(spec);
    }
  }
  static std::unique_ptr<webrtc::AudioDecoder> MakeAudioDecoder(
      const Config& config,
      absl::optional<webrtc::AudioCodecPairId> codec_pair_id = absl::nullopt) {
    return webrtc::AudioDecoderL16::MakeAudioDecoder(config, codec_pair_id);
  }
};

}  // namespace

TEST_P(PeerConnectionEndToEndTest, Call) {
  rtc::scoped_refptr<webrtc::AudioDecoderFactory> real_decoder_factory =
      webrtc::CreateOpusAudioDecoderFactory();
  CreatePcs(webrtc::CreateOpusAudioEncoderFactory(),
            CreateForwardingMockDecoderFactory(real_decoder_factory.get()));
  GetAndAddUserMedia();
  Negotiate();
  WaitForCallEstablished();
}

#if defined(IS_FUCHSIA)
TEST_P(PeerConnectionEndToEndTest, CallWithSdesKeyNegotiation) {
  config_.enable_dtls_srtp = false;
  CreatePcs(webrtc::CreateOpusAudioEncoderFactory(),
            webrtc::CreateOpusAudioDecoderFactory());
  GetAndAddUserMedia();
  Negotiate();
  WaitForCallEstablished();
}
#endif

TEST_P(PeerConnectionEndToEndTest, CallWithCustomCodec) {
  class IdLoggingAudioEncoderFactory : public webrtc::AudioEncoderFactory {
   public:
    IdLoggingAudioEncoderFactory(
        rtc::scoped_refptr<AudioEncoderFactory> real_factory,
        std::vector<webrtc::AudioCodecPairId>* const codec_ids)
        : fact_(real_factory), codec_ids_(codec_ids) {}
    std::vector<webrtc::AudioCodecSpec> GetSupportedEncoders() override {
      return fact_->GetSupportedEncoders();
    }
    absl::optional<webrtc::AudioCodecInfo> QueryAudioEncoder(
        const webrtc::SdpAudioFormat& format) override {
      return fact_->QueryAudioEncoder(format);
    }
    std::unique_ptr<webrtc::AudioEncoder> MakeAudioEncoder(
        int payload_type,
        const webrtc::SdpAudioFormat& format,
        absl::optional<webrtc::AudioCodecPairId> codec_pair_id) override {
      EXPECT_TRUE(codec_pair_id.has_value());
      codec_ids_->push_back(*codec_pair_id);
      return fact_->MakeAudioEncoder(payload_type, format, codec_pair_id);
    }

   private:
    const rtc::scoped_refptr<webrtc::AudioEncoderFactory> fact_;
    std::vector<webrtc::AudioCodecPairId>* const codec_ids_;
  };

  class IdLoggingAudioDecoderFactory : public webrtc::AudioDecoderFactory {
   public:
    IdLoggingAudioDecoderFactory(
        rtc::scoped_refptr<AudioDecoderFactory> real_factory,
        std::vector<webrtc::AudioCodecPairId>* const codec_ids)
        : fact_(real_factory), codec_ids_(codec_ids) {}
    std::vector<webrtc::AudioCodecSpec> GetSupportedDecoders() override {
      return fact_->GetSupportedDecoders();
    }
    bool IsSupportedDecoder(const webrtc::SdpAudioFormat& format) override {
      return fact_->IsSupportedDecoder(format);
    }
    std::unique_ptr<webrtc::AudioDecoder> MakeAudioDecoder(
        const webrtc::SdpAudioFormat& format,
        absl::optional<webrtc::AudioCodecPairId> codec_pair_id) override {
      EXPECT_TRUE(codec_pair_id.has_value());
      codec_ids_->push_back(*codec_pair_id);
      return fact_->MakeAudioDecoder(format, codec_pair_id);
    }

   private:
    const rtc::scoped_refptr<webrtc::AudioDecoderFactory> fact_;
    std::vector<webrtc::AudioCodecPairId>* const codec_ids_;
  };

  std::vector<webrtc::AudioCodecPairId> encoder_id1, encoder_id2, decoder_id1,
      decoder_id2;
  CreatePcs(rtc::make_ref_counted<IdLoggingAudioEncoderFactory>(
                webrtc::CreateAudioEncoderFactory<
                    AudioEncoderUnicornSparklesRainbow>(),
                &encoder_id1),
            rtc::make_ref_counted<IdLoggingAudioDecoderFactory>(
                webrtc::CreateAudioDecoderFactory<
                    AudioDecoderUnicornSparklesRainbow>(),
                &decoder_id1),
            rtc::make_ref_counted<IdLoggingAudioEncoderFactory>(
                webrtc::CreateAudioEncoderFactory<
                    AudioEncoderUnicornSparklesRainbow>(),
                &encoder_id2),
            rtc::make_ref_counted<IdLoggingAudioDecoderFactory>(
                webrtc::CreateAudioDecoderFactory<
                    AudioDecoderUnicornSparklesRainbow>(),
                &decoder_id2));
  GetAndAddUserMedia();
  Negotiate();
  WaitForCallEstablished();

  // Each codec factory has been used to create one codec. The first pair got
  // the same ID because they were passed to the same PeerConnectionFactory,
  // and the second pair got the same ID---but these two IDs are not equal,
  // because each PeerConnectionFactory has its own ID.
  EXPECT_EQ(1U, encoder_id1.size());
  EXPECT_EQ(1U, encoder_id2.size());
  EXPECT_EQ(encoder_id1, decoder_id1);
  EXPECT_EQ(encoder_id2, decoder_id2);
  EXPECT_NE(encoder_id1, encoder_id2);
}

#ifdef WEBRTC_HAVE_SCTP
// Verifies that a DataChannel created before the negotiation can transition to
// "OPEN" and transfer data.
TEST_P(PeerConnectionEndToEndTest, CreateDataChannelBeforeNegotiate) {
  CreatePcs(webrtc::MockAudioEncoderFactory::CreateEmptyFactory(),
            webrtc::MockAudioDecoderFactory::CreateEmptyFactory());

  webrtc::DataChannelInit init;
  rtc::scoped_refptr<DataChannelInterface> caller_dc(
      caller_->CreateDataChannel("data", init));
  rtc::scoped_refptr<DataChannelInterface> callee_dc(
      callee_->CreateDataChannel("data", init));

  Negotiate();
  WaitForConnection();

  WaitForDataChannelsToOpen(caller_dc.get(), callee_signaled_data_channels_, 0);
  WaitForDataChannelsToOpen(callee_dc.get(), caller_signaled_data_channels_, 0);

  TestDataChannelSendAndReceive(caller_dc.get(),
                                callee_signaled_data_channels_[0].get());
  TestDataChannelSendAndReceive(callee_dc.get(),
                                caller_signaled_data_channels_[0].get());

  CloseDataChannels(caller_dc.get(), callee_signaled_data_channels_, 0);
  CloseDataChannels(callee_dc.get(), caller_signaled_data_channels_, 0);
}

// Verifies that a DataChannel created after the negotiation can transition to
// "OPEN" and transfer data.
TEST_P(PeerConnectionEndToEndTest, CreateDataChannelAfterNegotiate) {
  CreatePcs(webrtc::MockAudioEncoderFactory::CreateEmptyFactory(),
            webrtc::MockAudioDecoderFactory::CreateEmptyFactory());

  webrtc::DataChannelInit init;

  // This DataChannel is for creating the data content in the negotiation.
  rtc::scoped_refptr<DataChannelInterface> dummy(
      caller_->CreateDataChannel("data", init));
  Negotiate();
  WaitForConnection();

  // Wait for the data channel created pre-negotiation to be opened.
  WaitForDataChannelsToOpen(dummy.get(), callee_signaled_data_channels_, 0);

  // Create new DataChannels after the negotiation and verify their states.
  rtc::scoped_refptr<DataChannelInterface> caller_dc(
      caller_->CreateDataChannel("hello", init));
  rtc::scoped_refptr<DataChannelInterface> callee_dc(
      callee_->CreateDataChannel("hello", init));

  WaitForDataChannelsToOpen(caller_dc.get(), callee_signaled_data_channels_, 1);
  WaitForDataChannelsToOpen(callee_dc.get(), caller_signaled_data_channels_, 0);

  TestDataChannelSendAndReceive(caller_dc.get(),
                                callee_signaled_data_channels_[1].get());
  TestDataChannelSendAndReceive(callee_dc.get(),
                                caller_signaled_data_channels_[0].get());

  CloseDataChannels(caller_dc.get(), callee_signaled_data_channels_, 1);
  CloseDataChannels(callee_dc.get(), caller_signaled_data_channels_, 0);
}

// Verifies that a DataChannel created can transfer large messages.
TEST_P(PeerConnectionEndToEndTest, CreateDataChannelLargeTransfer) {
  CreatePcs(webrtc::MockAudioEncoderFactory::CreateEmptyFactory(),
            webrtc::MockAudioDecoderFactory::CreateEmptyFactory());

  webrtc::DataChannelInit init;

  // This DataChannel is for creating the data content in the negotiation.
  rtc::scoped_refptr<DataChannelInterface> dummy(
      caller_->CreateDataChannel("data", init));
  Negotiate();
  WaitForConnection();

  // Wait for the data channel created pre-negotiation to be opened.
  WaitForDataChannelsToOpen(dummy.get(), callee_signaled_data_channels_, 0);

  // Create new DataChannels after the negotiation and verify their states.
  rtc::scoped_refptr<DataChannelInterface> caller_dc(
      caller_->CreateDataChannel("hello", init));
  rtc::scoped_refptr<DataChannelInterface> callee_dc(
      callee_->CreateDataChannel("hello", init));

  WaitForDataChannelsToOpen(caller_dc.get(), callee_signaled_data_channels_, 1);
  WaitForDataChannelsToOpen(callee_dc.get(), caller_signaled_data_channels_, 0);

  TestDataChannelSendAndReceive(
      caller_dc.get(), callee_signaled_data_channels_[1].get(), 256 * 1024);
  TestDataChannelSendAndReceive(
      callee_dc.get(), caller_signaled_data_channels_[0].get(), 256 * 1024);

  CloseDataChannels(caller_dc.get(), callee_signaled_data_channels_, 1);
  CloseDataChannels(callee_dc.get(), caller_signaled_data_channels_, 0);
}

// Verifies that DataChannel IDs are even/odd based on the DTLS roles.
TEST_P(PeerConnectionEndToEndTest, DataChannelIdAssignment) {
  CreatePcs(webrtc::MockAudioEncoderFactory::CreateEmptyFactory(),
            webrtc::MockAudioDecoderFactory::CreateEmptyFactory());

  webrtc::DataChannelInit init;
  rtc::scoped_refptr<DataChannelInterface> caller_dc_1(
      caller_->CreateDataChannel("data", init));
  rtc::scoped_refptr<DataChannelInterface> callee_dc_1(
      callee_->CreateDataChannel("data", init));

  Negotiate();
  WaitForConnection();

  EXPECT_EQ(1, caller_dc_1->id() % 2);
  EXPECT_EQ(0, callee_dc_1->id() % 2);

  rtc::scoped_refptr<DataChannelInterface> caller_dc_2(
      caller_->CreateDataChannel("data", init));
  rtc::scoped_refptr<DataChannelInterface> callee_dc_2(
      callee_->CreateDataChannel("data", init));

  EXPECT_EQ(1, caller_dc_2->id() % 2);
  EXPECT_EQ(0, callee_dc_2->id() % 2);
}

// Verifies that the message is received by the right remote DataChannel when
// there are multiple DataChannels.
TEST_P(PeerConnectionEndToEndTest,
       MessageTransferBetweenTwoPairsOfDataChannels) {
  CreatePcs(webrtc::MockAudioEncoderFactory::CreateEmptyFactory(),
            webrtc::MockAudioDecoderFactory::CreateEmptyFactory());

  webrtc::DataChannelInit init;

  rtc::scoped_refptr<DataChannelInterface> caller_dc_1(
      caller_->CreateDataChannel("data", init));
  rtc::scoped_refptr<DataChannelInterface> caller_dc_2(
      caller_->CreateDataChannel("data", init));

  Negotiate();
  WaitForConnection();
  WaitForDataChannelsToOpen(caller_dc_1.get(), callee_signaled_data_channels_,
                            0);
  WaitForDataChannelsToOpen(caller_dc_2.get(), callee_signaled_data_channels_,
                            1);

  std::unique_ptr<webrtc::MockDataChannelObserver> dc_1_observer(
      new webrtc::MockDataChannelObserver(
          callee_signaled_data_channels_[0].get()));

  std::unique_ptr<webrtc::MockDataChannelObserver> dc_2_observer(
      new webrtc::MockDataChannelObserver(
          callee_signaled_data_channels_[1].get()));

  const std::string message_1 = "hello 1";
  const std::string message_2 = "hello 2";

  caller_dc_1->Send(webrtc::DataBuffer(message_1));
  EXPECT_EQ_WAIT(message_1, dc_1_observer->last_message(), kMaxWait);

  caller_dc_2->Send(webrtc::DataBuffer(message_2));
  EXPECT_EQ_WAIT(message_2, dc_2_observer->last_message(), kMaxWait);

  EXPECT_EQ(1U, dc_1_observer->received_message_count());
  EXPECT_EQ(1U, dc_2_observer->received_message_count());
}

// Verifies that a DataChannel added from an OPEN message functions after
// a channel has been previously closed (webrtc issue 3778).
// This previously failed because the new channel re-used the ID of the closed
// channel, and the closed channel was incorrectly still assigned to the ID.
TEST_P(PeerConnectionEndToEndTest,
       DataChannelFromOpenWorksAfterPreviousChannelClosed) {
  CreatePcs(webrtc::MockAudioEncoderFactory::CreateEmptyFactory(),
            webrtc::MockAudioDecoderFactory::CreateEmptyFactory());

  webrtc::DataChannelInit init;
  rtc::scoped_refptr<DataChannelInterface> caller_dc(
      caller_->CreateDataChannel("data", init));

  Negotiate();
  WaitForConnection();

  WaitForDataChannelsToOpen(caller_dc.get(), callee_signaled_data_channels_, 0);
  int first_channel_id = caller_dc->id();
  // Wait for the local side to say it's closed, but not the remote side.
  // Previously, the channel on which Close is called reported being closed
  // prematurely, and this caused issues; see bugs.webrtc.org/4453.
  caller_dc->Close();
  EXPECT_EQ_WAIT(DataChannelInterface::kClosed, caller_dc->state(), kMaxWait);

  // Create a new channel and ensure it works after closing the previous one.
  caller_dc = caller_->CreateDataChannel("data2", init);
  WaitForDataChannelsToOpen(caller_dc.get(), callee_signaled_data_channels_, 1);
  // Since the second channel was created after the first finished closing, it
  // should be able to re-use the first one's ID.
  EXPECT_EQ(first_channel_id, caller_dc->id());
  TestDataChannelSendAndReceive(caller_dc.get(),
                                callee_signaled_data_channels_[1].get());

  CloseDataChannels(caller_dc.get(), callee_signaled_data_channels_, 1);
}

// This tests that if a data channel is closed remotely while not referenced
// by the application (meaning only the PeerConnection contributes to its
// reference count), no memory access violation will occur.
// See: https://code.google.com/p/chromium/issues/detail?id=565048
TEST_P(PeerConnectionEndToEndTest, CloseDataChannelRemotelyWhileNotReferenced) {
  CreatePcs(webrtc::MockAudioEncoderFactory::CreateEmptyFactory(),
            webrtc::MockAudioDecoderFactory::CreateEmptyFactory());

  webrtc::DataChannelInit init;
  rtc::scoped_refptr<DataChannelInterface> caller_dc(
      caller_->CreateDataChannel("data", init));

  Negotiate();
  WaitForConnection();

  WaitForDataChannelsToOpen(caller_dc.get(), callee_signaled_data_channels_, 0);
  // This removes the reference to the remote data channel that we hold.
  callee_signaled_data_channels_.clear();
  caller_dc->Close();
  EXPECT_EQ_WAIT(DataChannelInterface::kClosed, caller_dc->state(), kMaxWait);

  // Wait for a bit longer so the remote data channel will receive the
  // close message and be destroyed.
  rtc::Thread::Current()->ProcessMessages(100);
}

// Test behavior of creating too many datachannels.
TEST_P(PeerConnectionEndToEndTest, TooManyDataChannelsOpenedBeforeConnecting) {
  CreatePcs(webrtc::MockAudioEncoderFactory::CreateEmptyFactory(),
            webrtc::MockAudioDecoderFactory::CreateEmptyFactory());

  webrtc::DataChannelInit init;
  std::vector<rtc::scoped_refptr<DataChannelInterface>> channels;
  for (int i = 0; i <= cricket::kMaxSctpStreams / 2; i++) {
    rtc::scoped_refptr<DataChannelInterface> caller_dc(
        caller_->CreateDataChannel("data", init));
    channels.push_back(std::move(caller_dc));
  }
  Negotiate();
  WaitForConnection();
  EXPECT_EQ_WAIT(callee_signaled_data_channels_.size(),
                 static_cast<size_t>(cricket::kMaxSctpStreams / 2), kMaxWait);
  EXPECT_EQ(DataChannelInterface::kOpen,
            channels[(cricket::kMaxSctpStreams / 2) - 1]->state());
  EXPECT_EQ(DataChannelInterface::kClosed,
            channels[cricket::kMaxSctpStreams / 2]->state());
}

#endif  // WEBRTC_HAVE_SCTP

TEST_P(PeerConnectionEndToEndTest, CanRestartIce) {
  rtc::scoped_refptr<webrtc::AudioDecoderFactory> real_decoder_factory =
      webrtc::CreateOpusAudioDecoderFactory();
  CreatePcs(webrtc::CreateOpusAudioEncoderFactory(),
            CreateForwardingMockDecoderFactory(real_decoder_factory.get()));
  GetAndAddUserMedia();
  Negotiate();
  WaitForCallEstablished();
  // Cause ICE restart to be requested.
  auto config = caller_->pc()->GetConfiguration();
  ASSERT_NE(PeerConnectionInterface::kRelay, config.type);
  config.type = PeerConnectionInterface::kRelay;
  ASSERT_TRUE(caller_->pc()->SetConfiguration(config).ok());
  // When solving https://crbug.com/webrtc/10504, all we need to check
  // is that we do not crash. We should also be testing that restart happens.
}

INSTANTIATE_TEST_SUITE_P(PeerConnectionEndToEndTest,
                         PeerConnectionEndToEndTest,
                         Values(SdpSemantics::kPlanB_DEPRECATED,
                                SdpSemantics::kUnifiedPlan));
