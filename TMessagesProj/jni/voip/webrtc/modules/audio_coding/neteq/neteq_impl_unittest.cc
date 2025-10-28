/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/neteq/neteq_impl.h"

#include <memory>
#include <utility>
#include <vector>

#include "api/audio_codecs/builtin_audio_decoder_factory.h"
#include "api/neteq/default_neteq_controller_factory.h"
#include "api/neteq/neteq.h"
#include "api/neteq/neteq_controller.h"
#include "modules/audio_coding/codecs/g711/audio_decoder_pcm.h"
#include "modules/audio_coding/neteq/accelerate.h"
#include "modules/audio_coding/neteq/decision_logic.h"
#include "modules/audio_coding/neteq/default_neteq_factory.h"
#include "modules/audio_coding/neteq/expand.h"
#include "modules/audio_coding/neteq/histogram.h"
#include "modules/audio_coding/neteq/mock/mock_decoder_database.h"
#include "modules/audio_coding/neteq/mock/mock_dtmf_buffer.h"
#include "modules/audio_coding/neteq/mock/mock_dtmf_tone_generator.h"
#include "modules/audio_coding/neteq/mock/mock_neteq_controller.h"
#include "modules/audio_coding/neteq/mock/mock_packet_buffer.h"
#include "modules/audio_coding/neteq/mock/mock_red_payload_splitter.h"
#include "modules/audio_coding/neteq/preemptive_expand.h"
#include "modules/audio_coding/neteq/statistics_calculator.h"
#include "modules/audio_coding/neteq/sync_buffer.h"
#include "modules/audio_coding/neteq/timestamp_scaler.h"
#include "rtc_base/numerics/safe_conversions.h"
#include "system_wrappers/include/clock.h"
#include "test/audio_decoder_proxy_factory.h"
#include "test/function_audio_decoder_factory.h"
#include "test/gmock.h"
#include "test/gtest.h"
#include "test/mock_audio_decoder.h"
#include "test/mock_audio_decoder_factory.h"

using ::testing::_;
using ::testing::AtLeast;
using ::testing::DoAll;
using ::testing::ElementsAre;
using ::testing::InSequence;
using ::testing::Invoke;
using ::testing::IsEmpty;
using ::testing::IsNull;
using ::testing::Pointee;
using ::testing::Return;
using ::testing::ReturnNull;
using ::testing::SetArgPointee;
using ::testing::SetArrayArgument;
using ::testing::SizeIs;
using ::testing::WithArg;

namespace webrtc {

// This function is called when inserting a packet list into the mock packet
// buffer. The purpose is to delete all inserted packets properly, to avoid
// memory leaks in the test.
int DeletePacketsAndReturnOk(PacketList* packet_list) {
  packet_list->clear();
  return PacketBuffer::kOK;
}

class NetEqImplTest : public ::testing::Test {
 protected:
  NetEqImplTest() : clock_(0) { config_.sample_rate_hz = 8000; }

  void CreateInstance(
      const rtc::scoped_refptr<AudioDecoderFactory>& decoder_factory) {
    ASSERT_TRUE(decoder_factory);
    config_.enable_muted_state = enable_muted_state_;
    NetEqImpl::Dependencies deps(config_, &clock_, decoder_factory,
                                 DefaultNetEqControllerFactory());

    // Get a local pointer to NetEq's TickTimer object.
    tick_timer_ = deps.tick_timer.get();

    if (use_mock_decoder_database_) {
      std::unique_ptr<MockDecoderDatabase> mock(new MockDecoderDatabase);
      mock_decoder_database_ = mock.get();
      EXPECT_CALL(*mock_decoder_database_, GetActiveCngDecoder())
          .WillOnce(ReturnNull());
      deps.decoder_database = std::move(mock);
    }
    decoder_database_ = deps.decoder_database.get();

    if (use_mock_dtmf_buffer_) {
      std::unique_ptr<MockDtmfBuffer> mock(
          new MockDtmfBuffer(config_.sample_rate_hz));
      mock_dtmf_buffer_ = mock.get();
      deps.dtmf_buffer = std::move(mock);
    }
    dtmf_buffer_ = deps.dtmf_buffer.get();

    if (use_mock_dtmf_tone_generator_) {
      std::unique_ptr<MockDtmfToneGenerator> mock(new MockDtmfToneGenerator);
      mock_dtmf_tone_generator_ = mock.get();
      deps.dtmf_tone_generator = std::move(mock);
    }
    dtmf_tone_generator_ = deps.dtmf_tone_generator.get();

    if (use_mock_packet_buffer_) {
      std::unique_ptr<MockPacketBuffer> mock(new MockPacketBuffer(
          config_.max_packets_in_buffer, tick_timer_, deps.stats.get()));
      mock_packet_buffer_ = mock.get();
      deps.packet_buffer = std::move(mock);
    }
    packet_buffer_ = deps.packet_buffer.get();

    if (use_mock_neteq_controller_) {
      std::unique_ptr<MockNetEqController> mock(new MockNetEqController());
      mock_neteq_controller_ = mock.get();
      deps.neteq_controller = std::move(mock);
    } else {
      NetEqController::Config controller_config;
      controller_config.tick_timer = tick_timer_;
      controller_config.base_min_delay_ms = config_.min_delay_ms;
      controller_config.allow_time_stretching = true;
      controller_config.max_packets_in_buffer = config_.max_packets_in_buffer;
      controller_config.clock = &clock_;
      deps.neteq_controller =
          std::make_unique<DecisionLogic>(std::move(controller_config));
    }
    neteq_controller_ = deps.neteq_controller.get();

    if (use_mock_payload_splitter_) {
      std::unique_ptr<MockRedPayloadSplitter> mock(new MockRedPayloadSplitter);
      mock_payload_splitter_ = mock.get();
      deps.red_payload_splitter = std::move(mock);
    }
    red_payload_splitter_ = deps.red_payload_splitter.get();

    deps.timestamp_scaler = std::unique_ptr<TimestampScaler>(
        new TimestampScaler(*deps.decoder_database.get()));

    neteq_.reset(new NetEqImpl(config_, std::move(deps)));
    ASSERT_TRUE(neteq_ != NULL);
  }

  void CreateInstance() { CreateInstance(CreateBuiltinAudioDecoderFactory()); }

  void UseNoMocks() {
    ASSERT_TRUE(neteq_ == NULL) << "Must call UseNoMocks before CreateInstance";
    use_mock_decoder_database_ = false;
    use_mock_neteq_controller_ = false;
    use_mock_dtmf_buffer_ = false;
    use_mock_dtmf_tone_generator_ = false;
    use_mock_packet_buffer_ = false;
    use_mock_payload_splitter_ = false;
  }

  virtual ~NetEqImplTest() {
    if (use_mock_decoder_database_) {
      EXPECT_CALL(*mock_decoder_database_, Die()).Times(1);
    }
    if (use_mock_neteq_controller_) {
      EXPECT_CALL(*mock_neteq_controller_, Die()).Times(1);
    }
    if (use_mock_dtmf_buffer_) {
      EXPECT_CALL(*mock_dtmf_buffer_, Die()).Times(1);
    }
    if (use_mock_dtmf_tone_generator_) {
      EXPECT_CALL(*mock_dtmf_tone_generator_, Die()).Times(1);
    }
    if (use_mock_packet_buffer_) {
      EXPECT_CALL(*mock_packet_buffer_, Die()).Times(1);
    }
  }

  void TestDtmfPacket(int sample_rate_hz) {
    const size_t kPayloadLength = 4;
    const uint8_t kPayloadType = 110;
    const int kSampleRateHz = 16000;
    config_.sample_rate_hz = kSampleRateHz;
    UseNoMocks();
    CreateInstance();
    // Event: 2, E bit, Volume: 17, Length: 4336.
    uint8_t payload[kPayloadLength] = {0x02, 0x80 + 0x11, 0x10, 0xF0};
    RTPHeader rtp_header;
    rtp_header.payloadType = kPayloadType;
    rtp_header.sequenceNumber = 0x1234;
    rtp_header.timestamp = 0x12345678;
    rtp_header.ssrc = 0x87654321;

    EXPECT_TRUE(neteq_->RegisterPayloadType(
        kPayloadType, SdpAudioFormat("telephone-event", sample_rate_hz, 1)));

    // Insert first packet.
    EXPECT_EQ(NetEq::kOK, neteq_->InsertPacket(rtp_header, payload));

    // Pull audio once.
    const size_t kMaxOutputSize =
        static_cast<size_t>(10 * kSampleRateHz / 1000);
    AudioFrame output;
    bool muted;
    EXPECT_EQ(NetEq::kOK, neteq_->GetAudio(&output, &muted));
    ASSERT_FALSE(muted);
    ASSERT_EQ(kMaxOutputSize, output.samples_per_channel_);
    EXPECT_EQ(1u, output.num_channels_);
    EXPECT_EQ(AudioFrame::kNormalSpeech, output.speech_type_);

    // DTMF packets are immediately consumed by `InsertPacket()` and won't be
    // returned by `GetAudio()`.
    EXPECT_THAT(output.packet_infos_, IsEmpty());

    // Verify first 64 samples of actual output.
    const std::vector<int16_t> kOutput(
        {0,     0,     0,     0,     0,     0,     0,     0,     0,     0,
         -1578, -2816, -3460, -3403, -2709, -1594, -363,  671,   1269,  1328,
         908,   202,   -513,  -964,  -955,  -431,  504,   1617,  2602,  3164,
         3101,  2364,  1073,  -511,  -2047, -3198, -3721, -3525, -2688, -1440,
         -99,   1015,  1663,  1744,  1319,  588,   -171,  -680,  -747,  -315,
         515,   1512,  2378,  2828,  2674,  1877,  568,   -986,  -2446, -3482,
         -3864, -3516, -2534, -1163});
    ASSERT_GE(kMaxOutputSize, kOutput.size());
    EXPECT_TRUE(std::equal(kOutput.begin(), kOutput.end(), output.data()));
  }

  std::unique_ptr<NetEqImpl> neteq_;
  NetEq::Config config_;
  SimulatedClock clock_;
  TickTimer* tick_timer_ = nullptr;
  MockDecoderDatabase* mock_decoder_database_ = nullptr;
  DecoderDatabase* decoder_database_ = nullptr;
  bool use_mock_decoder_database_ = true;
  MockNetEqController* mock_neteq_controller_ = nullptr;
  NetEqController* neteq_controller_ = nullptr;
  bool use_mock_neteq_controller_ = true;
  MockDtmfBuffer* mock_dtmf_buffer_ = nullptr;
  DtmfBuffer* dtmf_buffer_ = nullptr;
  bool use_mock_dtmf_buffer_ = true;
  MockDtmfToneGenerator* mock_dtmf_tone_generator_ = nullptr;
  DtmfToneGenerator* dtmf_tone_generator_ = nullptr;
  bool use_mock_dtmf_tone_generator_ = true;
  MockPacketBuffer* mock_packet_buffer_ = nullptr;
  PacketBuffer* packet_buffer_ = nullptr;
  bool use_mock_packet_buffer_ = true;
  MockRedPayloadSplitter* mock_payload_splitter_ = nullptr;
  RedPayloadSplitter* red_payload_splitter_ = nullptr;
  bool use_mock_payload_splitter_ = true;
  bool enable_muted_state_ = false;
};

// This tests the interface class NetEq.
// TODO(hlundin): Move to separate file?
TEST(NetEq, CreateAndDestroy) {
  NetEq::Config config;
  SimulatedClock clock(0);
  auto decoder_factory = CreateBuiltinAudioDecoderFactory();
  std::unique_ptr<NetEq> neteq =
      DefaultNetEqFactory().CreateNetEq(config, decoder_factory, &clock);
}

TEST_F(NetEqImplTest, RegisterPayloadType) {
  CreateInstance();
  constexpr int rtp_payload_type = 0;
  const SdpAudioFormat format("pcmu", 8000, 1);
  EXPECT_CALL(*mock_decoder_database_,
              RegisterPayload(rtp_payload_type, format));
  neteq_->RegisterPayloadType(rtp_payload_type, format);
}

TEST_F(NetEqImplTest, RemovePayloadType) {
  CreateInstance();
  uint8_t rtp_payload_type = 0;
  EXPECT_CALL(*mock_decoder_database_, Remove(rtp_payload_type))
      .WillOnce(Return(DecoderDatabase::kDecoderNotFound));
  // Check that kOK is returned when database returns kDecoderNotFound, because
  // removing a payload type that was never registered is not an error.
  EXPECT_EQ(NetEq::kOK, neteq_->RemovePayloadType(rtp_payload_type));
}

TEST_F(NetEqImplTest, RemoveAllPayloadTypes) {
  CreateInstance();
  EXPECT_CALL(*mock_decoder_database_, RemoveAll()).WillOnce(Return());
  neteq_->RemoveAllPayloadTypes();
}

TEST_F(NetEqImplTest, InsertPacket) {
  using ::testing::AllOf;
  using ::testing::Field;
  CreateInstance();
  const size_t kPayloadLength = 100;
  const uint8_t kPayloadType = 0;
  const uint16_t kFirstSequenceNumber = 0x1234;
  const uint32_t kFirstTimestamp = 0x12345678;
  const uint32_t kSsrc = 0x87654321;
  uint8_t payload[kPayloadLength] = {0};
  RTPHeader rtp_header;
  rtp_header.payloadType = kPayloadType;
  rtp_header.sequenceNumber = kFirstSequenceNumber;
  rtp_header.timestamp = kFirstTimestamp;
  rtp_header.ssrc = kSsrc;
  Packet fake_packet;
  fake_packet.payload_type = kPayloadType;
  fake_packet.sequence_number = kFirstSequenceNumber;
  fake_packet.timestamp = kFirstTimestamp;

  auto mock_decoder_factory = rtc::make_ref_counted<MockAudioDecoderFactory>();
  EXPECT_CALL(*mock_decoder_factory, MakeAudioDecoderMock(_, _, _))
      .WillOnce(Invoke([&](const SdpAudioFormat& format,
                           absl::optional<AudioCodecPairId> codec_pair_id,
                           std::unique_ptr<AudioDecoder>* dec) {
        EXPECT_EQ("pcmu", format.name);

        std::unique_ptr<MockAudioDecoder> mock_decoder(new MockAudioDecoder);
        EXPECT_CALL(*mock_decoder, Channels()).WillRepeatedly(Return(1));
        EXPECT_CALL(*mock_decoder, SampleRateHz()).WillRepeatedly(Return(8000));
        EXPECT_CALL(*mock_decoder, Die()).Times(1);  // Called when deleted.

        *dec = std::move(mock_decoder);
      }));
  DecoderDatabase::DecoderInfo info(SdpAudioFormat("pcmu", 8000, 1),
                                    absl::nullopt, mock_decoder_factory.get());

  // Expectations for decoder database.
  EXPECT_CALL(*mock_decoder_database_, GetDecoderInfo(kPayloadType))
      .WillRepeatedly(Return(&info));

  // Expectations for packet buffer.
  EXPECT_CALL(*mock_packet_buffer_, Empty())
      .WillOnce(Return(false));  // Called once after first packet is inserted.
  EXPECT_CALL(*mock_packet_buffer_, Flush()).Times(1);
  EXPECT_CALL(*mock_packet_buffer_, InsertPacket(_))
      .Times(2)
      .WillRepeatedly(Return(PacketBuffer::kOK));
  EXPECT_CALL(*mock_packet_buffer_, PeekNextPacket())
      .Times(1)
      .WillOnce(Return(&fake_packet));

  // Expectations for DTMF buffer.
  EXPECT_CALL(*mock_dtmf_buffer_, Flush()).Times(1);

  // Expectations for delay manager.
  {
    // All expectations within this block must be called in this specific order.
    InSequence sequence;  // Dummy variable.
    // Expectations when the first packet is inserted.
    EXPECT_CALL(
        *mock_neteq_controller_,
        PacketArrived(
            /*fs_hz*/ 8000,
            /*should_update_stats*/ _,
            /*info*/
            AllOf(
                Field(&NetEqController::PacketArrivedInfo::is_cng_or_dtmf,
                      false),
                Field(&NetEqController::PacketArrivedInfo::main_sequence_number,
                      kFirstSequenceNumber),
                Field(&NetEqController::PacketArrivedInfo::main_timestamp,
                      kFirstTimestamp))));
    EXPECT_CALL(
        *mock_neteq_controller_,
        PacketArrived(
            /*fs_hz*/ 8000,
            /*should_update_stats*/ _,
            /*info*/
            AllOf(
                Field(&NetEqController::PacketArrivedInfo::is_cng_or_dtmf,
                      false),
                Field(&NetEqController::PacketArrivedInfo::main_sequence_number,
                      kFirstSequenceNumber + 1),
                Field(&NetEqController::PacketArrivedInfo::main_timestamp,
                      kFirstTimestamp + 160))));
  }

  // Insert first packet.
  neteq_->InsertPacket(rtp_header, payload);

  // Insert second packet.
  rtp_header.timestamp += 160;
  rtp_header.sequenceNumber += 1;
  neteq_->InsertPacket(rtp_header, payload);
}

TEST_F(NetEqImplTest, InsertPacketsUntilBufferIsFull) {
  UseNoMocks();
  CreateInstance();

  const int kPayloadLengthSamples = 80;
  const size_t kPayloadLengthBytes = 2 * kPayloadLengthSamples;  // PCM 16-bit.
  const uint8_t kPayloadType = 17;  // Just an arbitrary number.
  uint8_t payload[kPayloadLengthBytes] = {0};
  RTPHeader rtp_header;
  rtp_header.payloadType = kPayloadType;
  rtp_header.sequenceNumber = 0x1234;
  rtp_header.timestamp = 0x12345678;
  rtp_header.ssrc = 0x87654321;

  EXPECT_TRUE(neteq_->RegisterPayloadType(kPayloadType,
                                          SdpAudioFormat("l16", 8000, 1)));

  // Insert packets. The buffer should not flush.
  for (size_t i = 1; i <= config_.max_packets_in_buffer; ++i) {
    EXPECT_EQ(NetEq::kOK, neteq_->InsertPacket(rtp_header, payload));
    rtp_header.timestamp += kPayloadLengthSamples;
    rtp_header.sequenceNumber += 1;
    EXPECT_EQ(i, packet_buffer_->NumPacketsInBuffer());
  }

  // Insert one more packet and make sure the buffer got flushed. That is, it
  // should only hold one single packet.
  EXPECT_EQ(NetEq::kOK, neteq_->InsertPacket(rtp_header, payload));
  EXPECT_EQ(1u, packet_buffer_->NumPacketsInBuffer());
  const Packet* test_packet = packet_buffer_->PeekNextPacket();
  EXPECT_EQ(rtp_header.timestamp, test_packet->timestamp);
  EXPECT_EQ(rtp_header.sequenceNumber, test_packet->sequence_number);
}

TEST_F(NetEqImplTest, TestDtmfPacketAVT) {
  TestDtmfPacket(8000);
}

TEST_F(NetEqImplTest, TestDtmfPacketAVT16kHz) {
  TestDtmfPacket(16000);
}

TEST_F(NetEqImplTest, TestDtmfPacketAVT32kHz) {
  TestDtmfPacket(32000);
}

TEST_F(NetEqImplTest, TestDtmfPacketAVT48kHz) {
  TestDtmfPacket(48000);
}

// This test verifies that timestamps propagate from the incoming packets
// through to the sync buffer and to the playout timestamp.
TEST_F(NetEqImplTest, VerifyTimestampPropagation) {
  const uint8_t kPayloadType = 17;  // Just an arbitrary number.
  const int kSampleRateHz = 8000;
  const size_t kPayloadLengthSamples =
      static_cast<size_t>(10 * kSampleRateHz / 1000);  // 10 ms.
  const size_t kPayloadLengthBytes = kPayloadLengthSamples;
  uint8_t payload[kPayloadLengthBytes] = {0};
  RTPHeader rtp_header;
  rtp_header.payloadType = kPayloadType;
  rtp_header.sequenceNumber = 0x1234;
  rtp_header.timestamp = 0x12345678;
  rtp_header.ssrc = 0x87654321;
  rtp_header.numCSRCs = 3;
  rtp_header.arrOfCSRCs[0] = 43;
  rtp_header.arrOfCSRCs[1] = 65;
  rtp_header.arrOfCSRCs[2] = 17;

  // This is a dummy decoder that produces as many output samples as the input
  // has bytes. The output is an increasing series, starting at 1 for the first
  // sample, and then increasing by 1 for each sample.
  class CountingSamplesDecoder : public AudioDecoder {
   public:
    CountingSamplesDecoder() : next_value_(1) {}

    // Produce as many samples as input bytes (`encoded_len`).
    int DecodeInternal(const uint8_t* encoded,
                       size_t encoded_len,
                       int /* sample_rate_hz */,
                       int16_t* decoded,
                       SpeechType* speech_type) override {
      for (size_t i = 0; i < encoded_len; ++i) {
        decoded[i] = next_value_++;
      }
      *speech_type = kSpeech;
      return rtc::checked_cast<int>(encoded_len);
    }

    void Reset() override { next_value_ = 1; }

    int SampleRateHz() const override { return kSampleRateHz; }

    size_t Channels() const override { return 1; }

    uint16_t next_value() const { return next_value_; }

   private:
    int16_t next_value_;
  } decoder_;

  auto decoder_factory =
      rtc::make_ref_counted<test::AudioDecoderProxyFactory>(&decoder_);

  UseNoMocks();
  CreateInstance(decoder_factory);

  EXPECT_TRUE(neteq_->RegisterPayloadType(kPayloadType,
                                          SdpAudioFormat("L16", 8000, 1)));

  // Insert one packet.
  clock_.AdvanceTimeMilliseconds(123456);
  Timestamp expected_receive_time = clock_.CurrentTime();
  EXPECT_EQ(NetEq::kOK, neteq_->InsertPacket(rtp_header, payload));

  // Pull audio once.
  const size_t kMaxOutputSize = static_cast<size_t>(10 * kSampleRateHz / 1000);
  AudioFrame output;
  bool muted;
  EXPECT_EQ(NetEq::kOK, neteq_->GetAudio(&output, &muted));
  ASSERT_FALSE(muted);
  ASSERT_EQ(kMaxOutputSize, output.samples_per_channel_);
  EXPECT_EQ(1u, output.num_channels_);
  EXPECT_EQ(AudioFrame::kNormalSpeech, output.speech_type_);

  // Verify `output.packet_infos_`.
  ASSERT_THAT(output.packet_infos_, SizeIs(1));
  {
    const auto& packet_info = output.packet_infos_[0];
    EXPECT_EQ(packet_info.ssrc(), rtp_header.ssrc);
    EXPECT_THAT(packet_info.csrcs(), ElementsAre(43, 65, 17));
    EXPECT_EQ(packet_info.rtp_timestamp(), rtp_header.timestamp);
    EXPECT_FALSE(packet_info.audio_level().has_value());
    EXPECT_EQ(packet_info.receive_time(), expected_receive_time);
  }

  // Start with a simple check that the fake decoder is behaving as expected.
  EXPECT_EQ(kPayloadLengthSamples,
            static_cast<size_t>(decoder_.next_value() - 1));

  // The value of the last of the output samples is the same as the number of
  // samples played from the decoded packet. Thus, this number + the RTP
  // timestamp should match the playout timestamp.
  // Wrap the expected value in an absl::optional to compare them as such.
  EXPECT_EQ(
      absl::optional<uint32_t>(rtp_header.timestamp +
                               output.data()[output.samples_per_channel_ - 1]),
      neteq_->GetPlayoutTimestamp());

  // Check the timestamp for the last value in the sync buffer. This should
  // be one full frame length ahead of the RTP timestamp.
  const SyncBuffer* sync_buffer = neteq_->sync_buffer_for_test();
  ASSERT_TRUE(sync_buffer != NULL);
  EXPECT_EQ(rtp_header.timestamp + kPayloadLengthSamples,
            sync_buffer->end_timestamp());

  // Check that the number of samples still to play from the sync buffer add
  // up with what was already played out.
  EXPECT_EQ(
      kPayloadLengthSamples - output.data()[output.samples_per_channel_ - 1],
      sync_buffer->FutureLength());
}

TEST_F(NetEqImplTest, ReorderedPacket) {
  UseNoMocks();

  // Create a mock decoder object.
  MockAudioDecoder mock_decoder;

  CreateInstance(
      rtc::make_ref_counted<test::AudioDecoderProxyFactory>(&mock_decoder));

  const uint8_t kPayloadType = 17;  // Just an arbitrary number.
  const int kSampleRateHz = 8000;
  const size_t kPayloadLengthSamples =
      static_cast<size_t>(10 * kSampleRateHz / 1000);  // 10 ms.
  const size_t kPayloadLengthBytes = kPayloadLengthSamples;
  uint8_t payload[kPayloadLengthBytes] = {0};
  RTPHeader rtp_header;
  rtp_header.payloadType = kPayloadType;
  rtp_header.sequenceNumber = 0x1234;
  rtp_header.timestamp = 0x12345678;
  rtp_header.ssrc = 0x87654321;
  rtp_header.extension.hasAudioLevel = true;
  rtp_header.extension.audioLevel = 42;

  EXPECT_CALL(mock_decoder, Reset()).WillRepeatedly(Return());
  EXPECT_CALL(mock_decoder, SampleRateHz())
      .WillRepeatedly(Return(kSampleRateHz));
  EXPECT_CALL(mock_decoder, Channels()).WillRepeatedly(Return(1));
  EXPECT_CALL(mock_decoder, PacketDuration(_, kPayloadLengthBytes))
      .WillRepeatedly(Return(rtc::checked_cast<int>(kPayloadLengthSamples)));
  int16_t dummy_output[kPayloadLengthSamples] = {0};
  // The below expectation will make the mock decoder write
  // `kPayloadLengthSamples` zeros to the output array, and mark it as speech.
  EXPECT_CALL(mock_decoder, DecodeInternal(Pointee(0), kPayloadLengthBytes,
                                           kSampleRateHz, _, _))
      .WillOnce(DoAll(SetArrayArgument<3>(dummy_output,
                                          dummy_output + kPayloadLengthSamples),
                      SetArgPointee<4>(AudioDecoder::kSpeech),
                      Return(rtc::checked_cast<int>(kPayloadLengthSamples))));
  EXPECT_TRUE(neteq_->RegisterPayloadType(kPayloadType,
                                          SdpAudioFormat("L16", 8000, 1)));

  // Insert one packet.
  clock_.AdvanceTimeMilliseconds(123456);
  Timestamp expected_receive_time = clock_.CurrentTime();
  EXPECT_EQ(NetEq::kOK, neteq_->InsertPacket(rtp_header, payload));

  // Pull audio once.
  const size_t kMaxOutputSize = static_cast<size_t>(10 * kSampleRateHz / 1000);
  AudioFrame output;
  bool muted;
  EXPECT_EQ(NetEq::kOK, neteq_->GetAudio(&output, &muted));
  ASSERT_EQ(kMaxOutputSize, output.samples_per_channel_);
  EXPECT_EQ(1u, output.num_channels_);
  EXPECT_EQ(AudioFrame::kNormalSpeech, output.speech_type_);

  // Verify `output.packet_infos_`.
  ASSERT_THAT(output.packet_infos_, SizeIs(1));
  {
    const auto& packet_info = output.packet_infos_[0];
    EXPECT_EQ(packet_info.ssrc(), rtp_header.ssrc);
    EXPECT_THAT(packet_info.csrcs(), IsEmpty());
    EXPECT_EQ(packet_info.rtp_timestamp(), rtp_header.timestamp);
    EXPECT_EQ(packet_info.audio_level(), rtp_header.extension.audioLevel);
    EXPECT_EQ(packet_info.receive_time(), expected_receive_time);
  }

  // Insert two more packets. The first one is out of order, and is already too
  // old, the second one is the expected next packet.
  rtp_header.sequenceNumber -= 1;
  rtp_header.timestamp -= kPayloadLengthSamples;
  rtp_header.extension.audioLevel = 1;
  payload[0] = 1;
  clock_.AdvanceTimeMilliseconds(1000);
  EXPECT_EQ(NetEq::kOK, neteq_->InsertPacket(rtp_header, payload));
  rtp_header.sequenceNumber += 2;
  rtp_header.timestamp += 2 * kPayloadLengthSamples;
  rtp_header.extension.audioLevel = 2;
  payload[0] = 2;
  clock_.AdvanceTimeMilliseconds(2000);
  expected_receive_time = clock_.CurrentTime();
  EXPECT_EQ(NetEq::kOK, neteq_->InsertPacket(rtp_header, payload));

  // Expect only the second packet to be decoded (the one with "2" as the first
  // payload byte).
  EXPECT_CALL(mock_decoder, DecodeInternal(Pointee(2), kPayloadLengthBytes,
                                           kSampleRateHz, _, _))
      .WillOnce(DoAll(SetArrayArgument<3>(dummy_output,
                                          dummy_output + kPayloadLengthSamples),
                      SetArgPointee<4>(AudioDecoder::kSpeech),
                      Return(rtc::checked_cast<int>(kPayloadLengthSamples))));

  // Pull audio once.
  EXPECT_EQ(NetEq::kOK, neteq_->GetAudio(&output, &muted));
  ASSERT_EQ(kMaxOutputSize, output.samples_per_channel_);
  EXPECT_EQ(1u, output.num_channels_);
  EXPECT_EQ(AudioFrame::kNormalSpeech, output.speech_type_);

  // Now check the packet buffer, and make sure it is empty, since the
  // out-of-order packet should have been discarded.
  EXPECT_TRUE(packet_buffer_->Empty());

  // NetEq `packets_discarded` should capture this packet discard.
  EXPECT_EQ(1u, neteq_->GetLifetimeStatistics().packets_discarded);

  // Verify `output.packet_infos_`. Expect to only see the second packet.
  ASSERT_THAT(output.packet_infos_, SizeIs(1));
  {
    const auto& packet_info = output.packet_infos_[0];
    EXPECT_EQ(packet_info.ssrc(), rtp_header.ssrc);
    EXPECT_THAT(packet_info.csrcs(), IsEmpty());
    EXPECT_EQ(packet_info.rtp_timestamp(), rtp_header.timestamp);
    EXPECT_EQ(packet_info.audio_level(), rtp_header.extension.audioLevel);
    EXPECT_EQ(packet_info.receive_time(), expected_receive_time);
  }

  EXPECT_CALL(mock_decoder, Die());
}

// This test verifies that NetEq can handle the situation where the first
// incoming packet is rejected.
TEST_F(NetEqImplTest, FirstPacketUnknown) {
  UseNoMocks();
  CreateInstance();

  const uint8_t kPayloadType = 17;  // Just an arbitrary number.
  const int kSampleRateHz = 8000;
  const size_t kPayloadLengthSamples =
      static_cast<size_t>(10 * kSampleRateHz / 1000);  // 10 ms.
  const size_t kPayloadLengthBytes = kPayloadLengthSamples * 2;
  uint8_t payload[kPayloadLengthBytes] = {0};
  RTPHeader rtp_header;
  rtp_header.payloadType = kPayloadType;
  rtp_header.sequenceNumber = 0x1234;
  rtp_header.timestamp = 0x12345678;
  rtp_header.ssrc = 0x87654321;

  // Insert one packet. Note that we have not registered any payload type, so
  // this packet will be rejected.
  EXPECT_EQ(NetEq::kFail, neteq_->InsertPacket(rtp_header, payload));

  // Pull audio once.
  const size_t kMaxOutputSize = static_cast<size_t>(10 * kSampleRateHz / 1000);
  AudioFrame output;
  bool muted;
  EXPECT_EQ(NetEq::kOK, neteq_->GetAudio(&output, &muted));
  ASSERT_LE(output.samples_per_channel_, kMaxOutputSize);
  EXPECT_EQ(kMaxOutputSize, output.samples_per_channel_);
  EXPECT_EQ(1u, output.num_channels_);
  EXPECT_EQ(AudioFrame::kPLC, output.speech_type_);
  EXPECT_THAT(output.packet_infos_, IsEmpty());

  // Register the payload type.
  EXPECT_TRUE(neteq_->RegisterPayloadType(kPayloadType,
                                          SdpAudioFormat("l16", 8000, 1)));

  // Insert 10 packets.
  for (size_t i = 0; i < 10; ++i) {
    rtp_header.sequenceNumber++;
    rtp_header.timestamp += kPayloadLengthSamples;
    EXPECT_EQ(NetEq::kOK, neteq_->InsertPacket(rtp_header, payload));
    EXPECT_EQ(i + 1, packet_buffer_->NumPacketsInBuffer());
  }

  // Pull audio repeatedly and make sure we get normal output, that is not PLC.
  for (size_t i = 0; i < 3; ++i) {
    EXPECT_EQ(NetEq::kOK, neteq_->GetAudio(&output, &muted));
    ASSERT_LE(output.samples_per_channel_, kMaxOutputSize);
    EXPECT_EQ(kMaxOutputSize, output.samples_per_channel_);
    EXPECT_EQ(1u, output.num_channels_);
    EXPECT_EQ(AudioFrame::kNormalSpeech, output.speech_type_)
        << "NetEq did not decode the packets as expected.";
    EXPECT_THAT(output.packet_infos_, SizeIs(1));
  }
}

std::vector<uint8_t> CreateRedPayload(size_t num_payloads,
                                      int payload_type,
                                      int payload_size,
                                      int timestamp_offset) {
  constexpr int kRedHeaderLength = 4;
  const size_t size =
      payload_size + 1 + (num_payloads - 1) * (payload_size + kRedHeaderLength);
  std::vector<uint8_t> payload(size, 0);
  uint8_t* payload_ptr = payload.data();
  for (size_t i = 0; i < num_payloads; ++i) {
    // Write the RED headers.
    if (i == num_payloads - 1) {
      // Special case for last payload.
      *payload_ptr = payload_type & 0x7F;  // F = 0;
      ++payload_ptr;
      break;
    }
    *payload_ptr = payload_type & 0x7F;
    // Not the last block; set F = 1.
    *payload_ptr |= 0x80;
    ++payload_ptr;
    const int this_offset =
        rtc::checked_cast<int>((num_payloads - i - 1) * timestamp_offset);
    *payload_ptr = this_offset >> 6;
    ++payload_ptr;
    RTC_DCHECK_LE(payload_size, 1023);  // Max length described by 10 bits.
    *payload_ptr = ((this_offset & 0x3F) << 2) | (payload_size >> 8);
    ++payload_ptr;
    *payload_ptr = payload_size & 0xFF;
    ++payload_ptr;
  }
  return payload;
}

TEST_F(NetEqImplTest, InsertRedPayload) {
  UseNoMocks();
  CreateInstance();
  constexpr int kRedPayloadType = 7;
  neteq_->RegisterPayloadType(kRedPayloadType, SdpAudioFormat("red", 8000, 1));
  constexpr int kPayloadType = 8;
  neteq_->RegisterPayloadType(kPayloadType, SdpAudioFormat("l16", 8000, 1));
  size_t frame_size = 80;  // 10 ms.
  size_t payload_size = frame_size * 2;
  std::vector<uint8_t> payload =
      CreateRedPayload(3, kPayloadType, payload_size, frame_size);
  RTPHeader header;
  header.payloadType = kRedPayloadType;
  header.sequenceNumber = 0x1234;
  header.timestamp = 0x12345678;
  header.ssrc = 0x87654321;
  AbsoluteCaptureTime capture_time;
  capture_time.absolute_capture_timestamp = 1234;
  header.extension.absolute_capture_time = capture_time;
  header.extension.audioLevel = 12;
  header.extension.hasAudioLevel = true;
  header.numCSRCs = 1;
  header.arrOfCSRCs[0] = 123;
  neteq_->InsertPacket(header, payload);
  AudioFrame frame;
  bool muted;
  neteq_->GetAudio(&frame, &muted);
  // TODO(jakobi): Find a better way to test that the correct packet is decoded
  // than using the timestamp. The fixed NetEq delay is an implementation
  // detail that should not be tested.
  constexpr int kNetEqFixedDelay = 5;
  EXPECT_EQ(frame.timestamp_,
            header.timestamp - frame_size * 2 - kNetEqFixedDelay);
  EXPECT_TRUE(frame.packet_infos_.empty());
  neteq_->GetAudio(&frame, &muted);
  EXPECT_EQ(frame.timestamp_, header.timestamp - frame_size - kNetEqFixedDelay);
  EXPECT_TRUE(frame.packet_infos_.empty());
  neteq_->GetAudio(&frame, &muted);
  EXPECT_EQ(frame.timestamp_, header.timestamp - kNetEqFixedDelay);
  EXPECT_EQ(frame.packet_infos_.size(), 1u);
  EXPECT_EQ(frame.packet_infos_.front().absolute_capture_time(), capture_time);
  EXPECT_EQ(frame.packet_infos_.front().audio_level(),
            header.extension.audioLevel);
  EXPECT_EQ(frame.packet_infos_.front().csrcs()[0], header.arrOfCSRCs[0]);
}

// This test verifies that audio interruption is not logged for the initial
// PLC period before the first packet is deocoded.
// TODO(henrik.lundin) Maybe move this test to neteq_network_stats_unittest.cc.
// Make the test parametrized, so that we can test with different initial
// sample rates in NetEq.
class NetEqImplTestSampleRateParameter
    : public NetEqImplTest,
      public testing::WithParamInterface<int> {
 protected:
  NetEqImplTestSampleRateParameter()
      : NetEqImplTest(), initial_sample_rate_hz_(GetParam()) {
    config_.sample_rate_hz = initial_sample_rate_hz_;
  }

  const int initial_sample_rate_hz_;
};

class NetEqImplTestSdpFormatParameter
    : public NetEqImplTest,
      public testing::WithParamInterface<SdpAudioFormat> {
 protected:
  NetEqImplTestSdpFormatParameter()
      : NetEqImplTest(), sdp_format_(GetParam()) {}
  const SdpAudioFormat sdp_format_;
};

// This test does the following:
// 0. Set up NetEq with initial sample rate given by test parameter, and a codec
//    sample rate of 16000.
// 1. Start calling GetAudio before inserting any encoded audio. The audio
//    produced will be PLC.
// 2. Insert a number of encoded audio packets.
// 3. Keep calling GetAudio and verify that no audio interruption was logged.
//    Call GetAudio until NetEq runs out of data again; PLC starts.
// 4. Insert one more packet.
// 5. Call GetAudio until that packet is decoded and the PLC ends.

TEST_P(NetEqImplTestSampleRateParameter,
       NoAudioInterruptionLoggedBeforeFirstDecode) {
  UseNoMocks();
  CreateInstance();

  const uint8_t kPayloadType = 17;  // Just an arbitrary number.
  const int kPayloadSampleRateHz = 16000;
  const size_t kPayloadLengthSamples =
      static_cast<size_t>(10 * kPayloadSampleRateHz / 1000);  // 10 ms.
  const size_t kPayloadLengthBytes = kPayloadLengthSamples * 2;
  uint8_t payload[kPayloadLengthBytes] = {0};
  RTPHeader rtp_header;
  rtp_header.payloadType = kPayloadType;
  rtp_header.sequenceNumber = 0x1234;
  rtp_header.timestamp = 0x12345678;
  rtp_header.ssrc = 0x87654321;

  // Register the payload type.
  EXPECT_TRUE(neteq_->RegisterPayloadType(
      kPayloadType, SdpAudioFormat("l16", kPayloadSampleRateHz, 1)));

  // Pull audio several times. No packets have been inserted yet.
  const size_t initial_output_size =
      static_cast<size_t>(10 * initial_sample_rate_hz_ / 1000);  // 10 ms
  AudioFrame output;
  bool muted;
  for (int i = 0; i < 100; ++i) {
    EXPECT_EQ(NetEq::kOK, neteq_->GetAudio(&output, &muted));
    EXPECT_EQ(initial_output_size, output.samples_per_channel_);
    EXPECT_EQ(1u, output.num_channels_);
    EXPECT_NE(AudioFrame::kNormalSpeech, output.speech_type_);
    EXPECT_THAT(output.packet_infos_, IsEmpty());
  }

  // Lambda for inserting packets.
  auto insert_packet = [&]() {
    rtp_header.sequenceNumber++;
    rtp_header.timestamp += kPayloadLengthSamples;
    EXPECT_EQ(NetEq::kOK, neteq_->InsertPacket(rtp_header, payload));
  };
  // Insert 10 packets.
  for (size_t i = 0; i < 10; ++i) {
    insert_packet();
    EXPECT_EQ(i + 1, packet_buffer_->NumPacketsInBuffer());
  }

  // Pull audio repeatedly and make sure we get normal output, that is not PLC.
  constexpr size_t kOutputSize =
      static_cast<size_t>(10 * kPayloadSampleRateHz / 1000);  // 10 ms
  for (size_t i = 0; i < 3; ++i) {
    EXPECT_EQ(NetEq::kOK, neteq_->GetAudio(&output, &muted));
    EXPECT_EQ(kOutputSize, output.samples_per_channel_);
    EXPECT_EQ(1u, output.num_channels_);
    EXPECT_EQ(AudioFrame::kNormalSpeech, output.speech_type_)
        << "NetEq did not decode the packets as expected.";
    EXPECT_THAT(output.packet_infos_, SizeIs(1));
  }

  // Verify that no interruption was logged.
  auto lifetime_stats = neteq_->GetLifetimeStatistics();
  EXPECT_EQ(0, lifetime_stats.interruption_count);

  // Keep pulling audio data until a new PLC period is started.
  size_t count_loops = 0;
  while (output.speech_type_ == AudioFrame::kNormalSpeech) {
    // Make sure we don't hang the test if we never go to PLC.
    ASSERT_LT(++count_loops, 100u);
    EXPECT_EQ(NetEq::kOK, neteq_->GetAudio(&output, &muted));
  }

  // Insert a few packets to avoid postpone decoding after expand.
  for (size_t i = 0; i < 5; ++i) {
    insert_packet();
  }

  // Pull audio until the newly inserted packet is decoded and the PLC ends.
  while (output.speech_type_ != AudioFrame::kNormalSpeech) {
    // Make sure we don't hang the test if we never go to PLC.
    ASSERT_LT(++count_loops, 100u);
    EXPECT_EQ(NetEq::kOK, neteq_->GetAudio(&output, &muted));
  }

  // Verify that no interruption was logged.
  lifetime_stats = neteq_->GetLifetimeStatistics();
  EXPECT_EQ(0, lifetime_stats.interruption_count);
}

// This test does the following:
// 0. Set up NetEq with initial sample rate given by test parameter, and a codec
//    sample rate of 16000.
// 1. Insert a number of encoded audio packets.
// 2. Call GetAudio and verify that decoded audio is produced.
// 3. Keep calling GetAudio until NetEq runs out of data; PLC starts.
// 4. Keep calling GetAudio until PLC has been produced for at least 150 ms.
// 5. Insert one more packet.
// 6. Call GetAudio until that packet is decoded and the PLC ends.
// 7. Verify that an interruption was logged.

TEST_P(NetEqImplTestSampleRateParameter, AudioInterruptionLogged) {
  UseNoMocks();
  CreateInstance();

  const uint8_t kPayloadType = 17;  // Just an arbitrary number.
  const int kPayloadSampleRateHz = 16000;
  const size_t kPayloadLengthSamples =
      static_cast<size_t>(10 * kPayloadSampleRateHz / 1000);  // 10 ms.
  const size_t kPayloadLengthBytes = kPayloadLengthSamples * 2;
  uint8_t payload[kPayloadLengthBytes] = {0};
  RTPHeader rtp_header;
  rtp_header.payloadType = kPayloadType;
  rtp_header.sequenceNumber = 0x1234;
  rtp_header.timestamp = 0x12345678;
  rtp_header.ssrc = 0x87654321;

  // Register the payload type.
  EXPECT_TRUE(neteq_->RegisterPayloadType(
      kPayloadType, SdpAudioFormat("l16", kPayloadSampleRateHz, 1)));

  // Lambda for inserting packets.
  auto insert_packet = [&]() {
    rtp_header.sequenceNumber++;
    rtp_header.timestamp += kPayloadLengthSamples;
    EXPECT_EQ(NetEq::kOK, neteq_->InsertPacket(rtp_header, payload));
  };
  // Insert 10 packets.
  for (size_t i = 0; i < 10; ++i) {
    insert_packet();
    EXPECT_EQ(i + 1, packet_buffer_->NumPacketsInBuffer());
  }

  AudioFrame output;
  bool muted;
  // Keep pulling audio data until a new PLC period is started.
  size_t count_loops = 0;
  do {
    // Make sure we don't hang the test if we never go to PLC.
    ASSERT_LT(++count_loops, 100u);
    EXPECT_EQ(NetEq::kOK, neteq_->GetAudio(&output, &muted));
  } while (output.speech_type_ == AudioFrame::kNormalSpeech);

  // Pull audio 15 times, which produces 150 ms of output audio. This should
  // all be produced as PLC. The total length of the gap will then be 150 ms
  // plus an initial fraction of 10 ms at the start and the end of the PLC
  // period. In total, less than 170 ms.
  for (size_t i = 0; i < 15; ++i) {
    EXPECT_EQ(NetEq::kOK, neteq_->GetAudio(&output, &muted));
    EXPECT_NE(AudioFrame::kNormalSpeech, output.speech_type_);
  }

  // Insert a few packets to avoid postpone decoding after expand.
  for (size_t i = 0; i < 5; ++i) {
    insert_packet();
  }

  // Pull audio until the newly inserted packet is decoded and the PLC ends.
  while (output.speech_type_ != AudioFrame::kNormalSpeech) {
    // Make sure we don't hang the test if we never go to PLC.
    ASSERT_LT(++count_loops, 100u);
    EXPECT_EQ(NetEq::kOK, neteq_->GetAudio(&output, &muted));
  }

  // Verify that the interruption was logged.
  auto lifetime_stats = neteq_->GetLifetimeStatistics();
  EXPECT_EQ(1, lifetime_stats.interruption_count);
  EXPECT_GT(lifetime_stats.total_interruption_duration_ms, 150);
  EXPECT_LT(lifetime_stats.total_interruption_duration_ms, 170);
}

INSTANTIATE_TEST_SUITE_P(SampleRates,
                         NetEqImplTestSampleRateParameter,
                         testing::Values(8000, 16000, 32000, 48000));

TEST_P(NetEqImplTestSdpFormatParameter, GetNackListScaledTimestamp) {
  UseNoMocks();
  CreateInstance();

  neteq_->EnableNack(128);

  const uint8_t kPayloadType = 17;  // Just an arbitrary number.
  const int kPayloadSampleRateHz = sdp_format_.clockrate_hz;
  const size_t kPayloadLengthSamples =
      static_cast<size_t>(10 * kPayloadSampleRateHz / 1000);  // 10 ms.
  const size_t kPayloadLengthBytes = kPayloadLengthSamples * 2;
  std::vector<uint8_t> payload(kPayloadLengthBytes, 0);
  RTPHeader rtp_header;
  rtp_header.payloadType = kPayloadType;
  rtp_header.sequenceNumber = 0x1234;
  rtp_header.timestamp = 0x12345678;
  rtp_header.ssrc = 0x87654321;

  EXPECT_TRUE(neteq_->RegisterPayloadType(kPayloadType, sdp_format_));

  auto insert_packet = [&](bool lost = false) {
    rtp_header.sequenceNumber++;
    rtp_header.timestamp += kPayloadLengthSamples;
    if (!lost)
      EXPECT_EQ(NetEq::kOK, neteq_->InsertPacket(rtp_header, payload));
  };

  // Insert and decode 10 packets.
  for (size_t i = 0; i < 10; ++i) {
    insert_packet();
  }
  AudioFrame output;
  size_t count_loops = 0;
  do {
    bool muted;
    // Make sure we don't hang the test if we never go to PLC.
    ASSERT_LT(++count_loops, 100u);
    EXPECT_EQ(NetEq::kOK, neteq_->GetAudio(&output, &muted));
  } while (output.speech_type_ == AudioFrame::kNormalSpeech);

  insert_packet();

  insert_packet(/*lost=*/true);

  // Ensure packet gets marked as missing.
  for (int i = 0; i < 5; ++i) {
    insert_packet();
  }

  // Missing packet recoverable with 5ms RTT.
  EXPECT_THAT(neteq_->GetNackList(5), Not(IsEmpty()));

  // No packets should have TimeToPlay > 500ms.
  EXPECT_THAT(neteq_->GetNackList(500), IsEmpty());
}

INSTANTIATE_TEST_SUITE_P(GetNackList,
                         NetEqImplTestSdpFormatParameter,
                         testing::Values(SdpAudioFormat("g722", 8000, 1),
                                         SdpAudioFormat("opus", 48000, 2)));

// This test verifies that NetEq can handle comfort noise and enters/quits codec
// internal CNG mode properly.
TEST_F(NetEqImplTest, CodecInternalCng) {
  UseNoMocks();
  // Create a mock decoder object.
  MockAudioDecoder mock_decoder;
  CreateInstance(
      rtc::make_ref_counted<test::AudioDecoderProxyFactory>(&mock_decoder));

  const uint8_t kPayloadType = 17;  // Just an arbitrary number.
  const int kSampleRateKhz = 48;
  const size_t kPayloadLengthSamples =
      static_cast<size_t>(20 * kSampleRateKhz);  // 20 ms.
  const size_t kPayloadLengthBytes = 10;
  uint8_t payload[kPayloadLengthBytes] = {0};
  int16_t dummy_output[kPayloadLengthSamples] = {0};

  RTPHeader rtp_header;
  rtp_header.payloadType = kPayloadType;
  rtp_header.sequenceNumber = 0x1234;
  rtp_header.timestamp = 0x12345678;
  rtp_header.ssrc = 0x87654321;

  EXPECT_CALL(mock_decoder, Reset()).WillRepeatedly(Return());
  EXPECT_CALL(mock_decoder, SampleRateHz())
      .WillRepeatedly(Return(kSampleRateKhz * 1000));
  EXPECT_CALL(mock_decoder, Channels()).WillRepeatedly(Return(1));
  EXPECT_CALL(mock_decoder, PacketDuration(_, kPayloadLengthBytes))
      .WillRepeatedly(Return(rtc::checked_cast<int>(kPayloadLengthSamples)));
  // Packed duration when asking the decoder for more CNG data (without a new
  // packet).
  EXPECT_CALL(mock_decoder, PacketDuration(nullptr, 0))
      .WillRepeatedly(Return(rtc::checked_cast<int>(kPayloadLengthSamples)));

  EXPECT_TRUE(neteq_->RegisterPayloadType(kPayloadType,
                                          SdpAudioFormat("opus", 48000, 2)));

  struct Packet {
    int sequence_number_delta;
    int timestamp_delta;
    AudioDecoder::SpeechType decoder_output_type;
  };
  std::vector<Packet> packets = {
      {0, 0, AudioDecoder::kSpeech},
      {1, kPayloadLengthSamples, AudioDecoder::kComfortNoise},
      {2, 2 * kPayloadLengthSamples, AudioDecoder::kSpeech},
      {1, kPayloadLengthSamples, AudioDecoder::kSpeech}};

  for (size_t i = 0; i < packets.size(); ++i) {
    rtp_header.sequenceNumber += packets[i].sequence_number_delta;
    rtp_header.timestamp += packets[i].timestamp_delta;
    payload[0] = i;
    EXPECT_EQ(NetEq::kOK, neteq_->InsertPacket(rtp_header, payload));

    // Pointee(x) verifies that first byte of the payload equals x, this makes
    // it possible to verify that the correct payload is fed to Decode().
    EXPECT_CALL(mock_decoder, DecodeInternal(Pointee(i), kPayloadLengthBytes,
                                             kSampleRateKhz * 1000, _, _))
        .WillOnce(DoAll(SetArrayArgument<3>(
                            dummy_output, dummy_output + kPayloadLengthSamples),
                        SetArgPointee<4>(packets[i].decoder_output_type),
                        Return(rtc::checked_cast<int>(kPayloadLengthSamples))));
  }

  // Expect comfort noise to be returned by the decoder.
  EXPECT_CALL(mock_decoder,
              DecodeInternal(IsNull(), 0, kSampleRateKhz * 1000, _, _))
      .WillOnce(DoAll(SetArrayArgument<3>(dummy_output,
                                          dummy_output + kPayloadLengthSamples),
                      SetArgPointee<4>(AudioDecoder::kComfortNoise),
                      Return(rtc::checked_cast<int>(kPayloadLengthSamples))));

  std::vector<AudioFrame::SpeechType> expected_output = {
      AudioFrame::kNormalSpeech, AudioFrame::kCNG, AudioFrame::kNormalSpeech};
  size_t output_index = 0;

  int timeout_counter = 0;
  while (!packet_buffer_->Empty()) {
    ASSERT_LT(timeout_counter++, 20) << "Test timed out";
    AudioFrame output;
    bool muted;
    EXPECT_EQ(NetEq::kOK, neteq_->GetAudio(&output, &muted));
    if (output_index + 1 < expected_output.size() &&
        output.speech_type_ == expected_output[output_index + 1]) {
      ++output_index;
    } else {
      EXPECT_EQ(output.speech_type_, expected_output[output_index]);
    }
  }

  EXPECT_CALL(mock_decoder, Die());
}

TEST_F(NetEqImplTest, UnsupportedDecoder) {
  UseNoMocks();
  ::testing::NiceMock<MockAudioDecoder> decoder;

  CreateInstance(
      rtc::make_ref_counted<test::AudioDecoderProxyFactory>(&decoder));
  static const size_t kNetEqMaxFrameSize = 5760;  // 120 ms @ 48 kHz.
  static const size_t kChannels = 2;

  const uint8_t kPayloadType = 17;  // Just an arbitrary number.
  const int kSampleRateHz = 8000;

  const size_t kPayloadLengthSamples =
      static_cast<size_t>(10 * kSampleRateHz / 1000);  // 10 ms.
  const size_t kPayloadLengthBytes = 1;
  uint8_t payload[kPayloadLengthBytes] = {0};
  int16_t dummy_output[kPayloadLengthSamples * kChannels] = {0};
  RTPHeader rtp_header;
  rtp_header.payloadType = kPayloadType;
  rtp_header.sequenceNumber = 0x1234;
  rtp_header.timestamp = 0x12345678;
  rtp_header.ssrc = 0x87654321;

  const uint8_t kFirstPayloadValue = 1;
  const uint8_t kSecondPayloadValue = 2;

  EXPECT_CALL(decoder,
              PacketDuration(Pointee(kFirstPayloadValue), kPayloadLengthBytes))
      .Times(AtLeast(1))
      .WillRepeatedly(Return(rtc::checked_cast<int>(kNetEqMaxFrameSize + 1)));

  EXPECT_CALL(decoder, DecodeInternal(Pointee(kFirstPayloadValue), _, _, _, _))
      .Times(0);

  EXPECT_CALL(decoder, DecodeInternal(Pointee(kSecondPayloadValue),
                                      kPayloadLengthBytes, kSampleRateHz, _, _))
      .Times(1)
      .WillOnce(DoAll(
          SetArrayArgument<3>(dummy_output,
                              dummy_output + kPayloadLengthSamples * kChannels),
          SetArgPointee<4>(AudioDecoder::kSpeech),
          Return(static_cast<int>(kPayloadLengthSamples * kChannels))));

  EXPECT_CALL(decoder,
              PacketDuration(Pointee(kSecondPayloadValue), kPayloadLengthBytes))
      .Times(AtLeast(1))
      .WillRepeatedly(Return(rtc::checked_cast<int>(kNetEqMaxFrameSize)));

  EXPECT_CALL(decoder, SampleRateHz()).WillRepeatedly(Return(kSampleRateHz));

  EXPECT_CALL(decoder, Channels()).WillRepeatedly(Return(kChannels));

  EXPECT_TRUE(neteq_->RegisterPayloadType(kPayloadType,
                                          SdpAudioFormat("L16", 8000, 1)));

  // Insert one packet.
  payload[0] = kFirstPayloadValue;  // This will make Decode() fail.
  EXPECT_EQ(NetEq::kOK, neteq_->InsertPacket(rtp_header, payload));

  // Insert another packet.
  payload[0] = kSecondPayloadValue;  // This will make Decode() successful.
  rtp_header.sequenceNumber++;
  // The second timestamp needs to be at least 30 ms after the first to make
  // the second packet get decoded.
  rtp_header.timestamp += 3 * kPayloadLengthSamples;
  EXPECT_EQ(NetEq::kOK, neteq_->InsertPacket(rtp_header, payload));

  AudioFrame output;
  bool muted;
  // First call to GetAudio will try to decode the "faulty" packet.
  // Expect kFail return value.
  EXPECT_EQ(NetEq::kFail, neteq_->GetAudio(&output, &muted));
  // Output size and number of channels should be correct.
  const size_t kExpectedOutputSize = 10 * (kSampleRateHz / 1000) * kChannels;
  EXPECT_EQ(kExpectedOutputSize, output.samples_per_channel_ * kChannels);
  EXPECT_EQ(kChannels, output.num_channels_);
  EXPECT_THAT(output.packet_infos_, IsEmpty());

  // Call GetAudio until the next packet is decoded.
  int calls = 0;
  int kTimeout = 10;
  while (output.packet_infos_.empty() && calls < kTimeout) {
    EXPECT_EQ(NetEq::kOK, neteq_->GetAudio(&output, &muted));
    EXPECT_EQ(kExpectedOutputSize, output.samples_per_channel_ * kChannels);
    EXPECT_EQ(kChannels, output.num_channels_);
  }
  EXPECT_LT(calls, kTimeout);

  // Die isn't called through NiceMock (since it's called by the
  // MockAudioDecoder constructor), so it needs to be mocked explicitly.
  EXPECT_CALL(decoder, Die());
}

// This test inserts packets until the buffer is flushed. After that, it asks
// NetEq for the network statistics. The purpose of the test is to make sure
// that even though the buffer size increment is negative (which it becomes when
// the packet causing a flush is inserted), the packet length stored in the
// decision logic remains valid.
TEST_F(NetEqImplTest, FloodBufferAndGetNetworkStats) {
  UseNoMocks();
  CreateInstance();

  const size_t kPayloadLengthSamples = 80;
  const size_t kPayloadLengthBytes = 2 * kPayloadLengthSamples;  // PCM 16-bit.
  const uint8_t kPayloadType = 17;  // Just an arbitrary number.
  uint8_t payload[kPayloadLengthBytes] = {0};
  RTPHeader rtp_header;
  rtp_header.payloadType = kPayloadType;
  rtp_header.sequenceNumber = 0x1234;
  rtp_header.timestamp = 0x12345678;
  rtp_header.ssrc = 0x87654321;

  EXPECT_TRUE(neteq_->RegisterPayloadType(kPayloadType,
                                          SdpAudioFormat("l16", 8000, 1)));

  // Insert packets until the buffer flushes.
  for (size_t i = 0; i <= config_.max_packets_in_buffer; ++i) {
    EXPECT_EQ(i, packet_buffer_->NumPacketsInBuffer());
    EXPECT_EQ(NetEq::kOK, neteq_->InsertPacket(rtp_header, payload));
    rtp_header.timestamp += rtc::checked_cast<uint32_t>(kPayloadLengthSamples);
    ++rtp_header.sequenceNumber;
  }
  EXPECT_EQ(1u, packet_buffer_->NumPacketsInBuffer());

  // Ask for network statistics. This should not crash.
  NetEqNetworkStatistics stats;
  EXPECT_EQ(NetEq::kOK, neteq_->NetworkStatistics(&stats));
}

TEST_F(NetEqImplTest, DecodedPayloadTooShort) {
  UseNoMocks();
  // Create a mock decoder object.
  MockAudioDecoder mock_decoder;

  CreateInstance(
      rtc::make_ref_counted<test::AudioDecoderProxyFactory>(&mock_decoder));

  const uint8_t kPayloadType = 17;  // Just an arbitrary number.
  const int kSampleRateHz = 8000;
  const size_t kPayloadLengthSamples =
      static_cast<size_t>(10 * kSampleRateHz / 1000);  // 10 ms.
  const size_t kPayloadLengthBytes = 2 * kPayloadLengthSamples;
  uint8_t payload[kPayloadLengthBytes] = {0};
  RTPHeader rtp_header;
  rtp_header.payloadType = kPayloadType;
  rtp_header.sequenceNumber = 0x1234;
  rtp_header.timestamp = 0x12345678;
  rtp_header.ssrc = 0x87654321;

  EXPECT_CALL(mock_decoder, Reset()).WillRepeatedly(Return());
  EXPECT_CALL(mock_decoder, SampleRateHz())
      .WillRepeatedly(Return(kSampleRateHz));
  EXPECT_CALL(mock_decoder, Channels()).WillRepeatedly(Return(1));
  EXPECT_CALL(mock_decoder, PacketDuration(_, _))
      .WillRepeatedly(Return(rtc::checked_cast<int>(kPayloadLengthSamples)));
  int16_t dummy_output[kPayloadLengthSamples] = {0};
  // The below expectation will make the mock decoder write
  // `kPayloadLengthSamples` - 5 zeros to the output array, and mark it as
  // speech. That is, the decoded length is 5 samples shorter than the expected.
  EXPECT_CALL(mock_decoder,
              DecodeInternal(_, kPayloadLengthBytes, kSampleRateHz, _, _))
      .WillOnce(
          DoAll(SetArrayArgument<3>(dummy_output,
                                    dummy_output + kPayloadLengthSamples - 5),
                SetArgPointee<4>(AudioDecoder::kSpeech),
                Return(rtc::checked_cast<int>(kPayloadLengthSamples - 5))));
  EXPECT_TRUE(neteq_->RegisterPayloadType(kPayloadType,
                                          SdpAudioFormat("L16", 8000, 1)));

  // Insert one packet.
  EXPECT_EQ(NetEq::kOK, neteq_->InsertPacket(rtp_header, payload));

  EXPECT_EQ(5u, neteq_->sync_buffer_for_test()->FutureLength());

  // Pull audio once.
  const size_t kMaxOutputSize = static_cast<size_t>(10 * kSampleRateHz / 1000);
  AudioFrame output;
  bool muted;
  EXPECT_EQ(NetEq::kOK, neteq_->GetAudio(&output, &muted));
  ASSERT_EQ(kMaxOutputSize, output.samples_per_channel_);
  EXPECT_EQ(1u, output.num_channels_);
  EXPECT_EQ(AudioFrame::kNormalSpeech, output.speech_type_);
  EXPECT_THAT(output.packet_infos_, SizeIs(1));

  EXPECT_CALL(mock_decoder, Die());
}

// This test checks the behavior of NetEq when audio decoder fails.
TEST_F(NetEqImplTest, DecodingError) {
  UseNoMocks();
  // Create a mock decoder object.
  MockAudioDecoder mock_decoder;

  CreateInstance(
      rtc::make_ref_counted<test::AudioDecoderProxyFactory>(&mock_decoder));

  const uint8_t kPayloadType = 17;  // Just an arbitrary number.
  const int kSampleRateHz = 8000;
  const int kDecoderErrorCode = -97;  // Any negative number.

  // We let decoder return 5 ms each time, and therefore, 2 packets make 10 ms.
  const size_t kFrameLengthSamples =
      static_cast<size_t>(5 * kSampleRateHz / 1000);

  const size_t kPayloadLengthBytes = 1;  // This can be arbitrary.

  uint8_t payload[kPayloadLengthBytes] = {0};

  RTPHeader rtp_header;
  rtp_header.payloadType = kPayloadType;
  rtp_header.sequenceNumber = 0x1234;
  rtp_header.timestamp = 0x12345678;
  rtp_header.ssrc = 0x87654321;

  EXPECT_CALL(mock_decoder, Reset()).WillRepeatedly(Return());
  EXPECT_CALL(mock_decoder, SampleRateHz())
      .WillRepeatedly(Return(kSampleRateHz));
  EXPECT_CALL(mock_decoder, Channels()).WillRepeatedly(Return(1));
  EXPECT_CALL(mock_decoder, PacketDuration(_, _))
      .WillRepeatedly(Return(rtc::checked_cast<int>(kFrameLengthSamples)));
  EXPECT_CALL(mock_decoder, ErrorCode()).WillOnce(Return(kDecoderErrorCode));
  EXPECT_CALL(mock_decoder, HasDecodePlc()).WillOnce(Return(false));
  int16_t dummy_output[kFrameLengthSamples] = {0};

  {
    InSequence sequence;  // Dummy variable.
    // Mock decoder works normally the first time.
    EXPECT_CALL(mock_decoder,
                DecodeInternal(_, kPayloadLengthBytes, kSampleRateHz, _, _))
        .Times(3)
        .WillRepeatedly(
            DoAll(SetArrayArgument<3>(dummy_output,
                                      dummy_output + kFrameLengthSamples),
                  SetArgPointee<4>(AudioDecoder::kSpeech),
                  Return(rtc::checked_cast<int>(kFrameLengthSamples))))
        .RetiresOnSaturation();

    // Then mock decoder fails. A common reason for failure can be buffer being
    // too short
    EXPECT_CALL(mock_decoder,
                DecodeInternal(_, kPayloadLengthBytes, kSampleRateHz, _, _))
        .WillOnce(Return(-1))
        .RetiresOnSaturation();

    // Mock decoder finally returns to normal.
    EXPECT_CALL(mock_decoder,
                DecodeInternal(_, kPayloadLengthBytes, kSampleRateHz, _, _))
        .Times(2)
        .WillRepeatedly(
            DoAll(SetArrayArgument<3>(dummy_output,
                                      dummy_output + kFrameLengthSamples),
                  SetArgPointee<4>(AudioDecoder::kSpeech),
                  Return(rtc::checked_cast<int>(kFrameLengthSamples))));
  }

  EXPECT_TRUE(neteq_->RegisterPayloadType(kPayloadType,
                                          SdpAudioFormat("L16", 8000, 1)));

  // Insert packets.
  for (int i = 0; i < 20; ++i) {
    rtp_header.sequenceNumber += 1;
    rtp_header.timestamp += kFrameLengthSamples;
    EXPECT_EQ(NetEq::kOK, neteq_->InsertPacket(rtp_header, payload));
  }

  // Pull audio.
  const size_t kMaxOutputSize = static_cast<size_t>(10 * kSampleRateHz / 1000);
  AudioFrame output;
  bool muted;
  EXPECT_EQ(NetEq::kOK, neteq_->GetAudio(&output, &muted));
  EXPECT_EQ(kMaxOutputSize, output.samples_per_channel_);
  EXPECT_EQ(1u, output.num_channels_);
  EXPECT_EQ(AudioFrame::kNormalSpeech, output.speech_type_);
  EXPECT_THAT(output.packet_infos_, SizeIs(2));  // 5 ms packets vs 10 ms output

  // Pull audio again. Decoder fails.
  EXPECT_EQ(NetEq::kFail, neteq_->GetAudio(&output, &muted));
  EXPECT_EQ(kMaxOutputSize, output.samples_per_channel_);
  EXPECT_EQ(1u, output.num_channels_);
  // We are not expecting anything for output.speech_type_, since an error was
  // returned.

  // Pull audio again, should behave normal.
  EXPECT_EQ(NetEq::kOK, neteq_->GetAudio(&output, &muted));
  EXPECT_EQ(kMaxOutputSize, output.samples_per_channel_);
  EXPECT_EQ(1u, output.num_channels_);
  EXPECT_EQ(AudioFrame::kNormalSpeech, output.speech_type_);
  EXPECT_THAT(output.packet_infos_, SizeIs(2));  // 5 ms packets vs 10 ms output

  EXPECT_CALL(mock_decoder, Die());
}

// Tests that the return value from last_output_sample_rate_hz() is equal to the
// configured inital sample rate.
TEST_F(NetEqImplTest, InitialLastOutputSampleRate) {
  UseNoMocks();
  config_.sample_rate_hz = 48000;
  CreateInstance();
  EXPECT_EQ(48000, neteq_->last_output_sample_rate_hz());
}

TEST_F(NetEqImplTest, TickTimerIncrement) {
  UseNoMocks();
  CreateInstance();
  ASSERT_TRUE(tick_timer_);
  EXPECT_EQ(0u, tick_timer_->ticks());
  AudioFrame output;
  bool muted;
  EXPECT_EQ(NetEq::kOK, neteq_->GetAudio(&output, &muted));
  EXPECT_EQ(1u, tick_timer_->ticks());
}

TEST_F(NetEqImplTest, SetBaseMinimumDelay) {
  UseNoMocks();
  use_mock_neteq_controller_ = true;
  CreateInstance();

  EXPECT_CALL(*mock_neteq_controller_, SetBaseMinimumDelay(_))
      .WillOnce(Return(true))
      .WillOnce(Return(false));

  const int delay_ms = 200;

  EXPECT_EQ(true, neteq_->SetBaseMinimumDelayMs(delay_ms));
  EXPECT_EQ(false, neteq_->SetBaseMinimumDelayMs(delay_ms));
}

TEST_F(NetEqImplTest, GetBaseMinimumDelayMs) {
  UseNoMocks();
  use_mock_neteq_controller_ = true;
  CreateInstance();

  const int delay_ms = 200;

  EXPECT_CALL(*mock_neteq_controller_, GetBaseMinimumDelay())
      .WillOnce(Return(delay_ms));

  EXPECT_EQ(delay_ms, neteq_->GetBaseMinimumDelayMs());
}

TEST_F(NetEqImplTest, TargetDelayMs) {
  UseNoMocks();
  use_mock_neteq_controller_ = true;
  CreateInstance();
  constexpr int kTargetLevelMs = 510;
  EXPECT_CALL(*mock_neteq_controller_, TargetLevelMs())
      .WillOnce(Return(kTargetLevelMs));
  EXPECT_EQ(510, neteq_->TargetDelayMs());
}

TEST_F(NetEqImplTest, InsertEmptyPacket) {
  UseNoMocks();
  use_mock_neteq_controller_ = true;
  CreateInstance();

  RTPHeader rtp_header;
  rtp_header.payloadType = 17;
  rtp_header.sequenceNumber = 0x1234;
  rtp_header.timestamp = 0x12345678;
  rtp_header.ssrc = 0x87654321;

  EXPECT_CALL(*mock_neteq_controller_, RegisterEmptyPacket());
  neteq_->InsertEmptyPacket(rtp_header);
}

TEST_F(NetEqImplTest, NotifyControllerOfReorderedPacket) {
  using ::testing::AllOf;
  using ::testing::Field;
  UseNoMocks();
  use_mock_neteq_controller_ = true;
  CreateInstance();
  EXPECT_CALL(*mock_neteq_controller_, GetDecision(_, _))
      .Times(1)
      .WillOnce(Return(NetEq::Operation::kNormal));

  const int kPayloadLengthSamples = 80;
  const size_t kPayloadLengthBytes = 2 * kPayloadLengthSamples;  // PCM 16-bit.
  const uint8_t kPayloadType = 17;  // Just an arbitrary number.
  uint8_t payload[kPayloadLengthBytes] = {0};
  RTPHeader rtp_header;
  rtp_header.payloadType = kPayloadType;
  rtp_header.sequenceNumber = 0x1234;
  rtp_header.timestamp = 0x12345678;
  rtp_header.ssrc = 0x87654321;

  EXPECT_TRUE(neteq_->RegisterPayloadType(kPayloadType,
                                          SdpAudioFormat("l16", 8000, 1)));
  EXPECT_EQ(NetEq::kOK, neteq_->InsertPacket(rtp_header, payload));
  AudioFrame output;
  bool muted;
  EXPECT_EQ(NetEq::kOK, neteq_->GetAudio(&output, &muted));

  // Insert second packet that was sent before the first packet.
  rtp_header.sequenceNumber -= 1;
  rtp_header.timestamp -= kPayloadLengthSamples;
  EXPECT_CALL(
      *mock_neteq_controller_,
      PacketArrived(
          /*fs_hz*/ 8000,
          /*should_update_stats*/ true,
          /*info*/
          AllOf(
              Field(&NetEqController::PacketArrivedInfo::packet_length_samples,
                    kPayloadLengthSamples),
              Field(&NetEqController::PacketArrivedInfo::main_sequence_number,
                    rtp_header.sequenceNumber),
              Field(&NetEqController::PacketArrivedInfo::main_timestamp,
                    rtp_header.timestamp))));

  EXPECT_EQ(NetEq::kOK, neteq_->InsertPacket(rtp_header, payload));
}

// When using a codec with 1000 channels, there should be no crashes.
TEST_F(NetEqImplTest, NoCrashWith1000Channels) {
  using ::testing::AllOf;
  using ::testing::Field;
  UseNoMocks();
  use_mock_decoder_database_ = true;
  enable_muted_state_ = true;
  CreateInstance();
  const size_t kPayloadLength = 100;
  const uint8_t kPayloadType = 0;
  const uint16_t kFirstSequenceNumber = 0x1234;
  const uint32_t kFirstTimestamp = 0x12345678;
  const uint32_t kSsrc = 0x87654321;
  uint8_t payload[kPayloadLength] = {0};
  RTPHeader rtp_header;
  rtp_header.payloadType = kPayloadType;
  rtp_header.sequenceNumber = kFirstSequenceNumber;
  rtp_header.timestamp = kFirstTimestamp;
  rtp_header.ssrc = kSsrc;
  Packet fake_packet;
  fake_packet.payload_type = kPayloadType;
  fake_packet.sequence_number = kFirstSequenceNumber;
  fake_packet.timestamp = kFirstTimestamp;

  AudioDecoder* decoder = nullptr;

  auto mock_decoder_factory = rtc::make_ref_counted<MockAudioDecoderFactory>();
  EXPECT_CALL(*mock_decoder_factory, MakeAudioDecoderMock(_, _, _))
      .WillOnce(Invoke([&](const SdpAudioFormat& format,
                           absl::optional<AudioCodecPairId> codec_pair_id,
                           std::unique_ptr<AudioDecoder>* dec) {
        EXPECT_EQ("pcmu", format.name);
        *dec = std::make_unique<AudioDecoderPcmU>(1000);
        decoder = dec->get();
      }));
  DecoderDatabase::DecoderInfo info(SdpAudioFormat("pcmu", 8000, 1),
                                    absl::nullopt, mock_decoder_factory.get());
  // Expectations for decoder database.
  EXPECT_CALL(*mock_decoder_database_, GetDecoderInfo(kPayloadType))
      .WillRepeatedly(Return(&info));
  EXPECT_CALL(*mock_decoder_database_, GetActiveCngDecoder())
      .WillRepeatedly(ReturnNull());
  EXPECT_CALL(*mock_decoder_database_, GetActiveDecoder())
      .WillRepeatedly(Return(decoder));
  EXPECT_CALL(*mock_decoder_database_, SetActiveDecoder(_, _))
      .WillOnce(Invoke([](uint8_t rtp_payload_type, bool* new_decoder) {
        *new_decoder = true;
        return 0;
      }));

  // Insert first packet.
  neteq_->InsertPacket(rtp_header, payload);

  AudioFrame audio_frame;
  bool muted;

  // Repeat 40 times to ensure we enter muted state.
  for (int i = 0; i < 40; i++) {
    // GetAudio should return an error, and not crash, even in muted state.
    EXPECT_NE(0, neteq_->GetAudio(&audio_frame, &muted));
  }
}

// The test first inserts a packet with narrow-band CNG, then a packet with
// wide-band speech. The expected behavior is to detect a change in sample rate,
// even though no speech packet has been inserted before, and flush out the CNG
// packet.
TEST_F(NetEqImplTest, CngFirstThenSpeechWithNewSampleRate) {
  UseNoMocks();
  CreateInstance();
  constexpr int kCnPayloadType = 7;
  neteq_->RegisterPayloadType(kCnPayloadType, SdpAudioFormat("cn", 8000, 1));
  constexpr int kSpeechPayloadType = 8;
  neteq_->RegisterPayloadType(kSpeechPayloadType,
                              SdpAudioFormat("l16", 16000, 1));

  RTPHeader header;
  header.payloadType = kCnPayloadType;
  uint8_t payload[320] = {0};

  EXPECT_EQ(neteq_->InsertPacket(header, payload), NetEq::kOK);
  EXPECT_EQ(neteq_->GetLifetimeStatistics().packets_discarded, 0u);

  header.payloadType = kSpeechPayloadType;
  header.timestamp += 160;
  EXPECT_EQ(neteq_->InsertPacket(header, payload), NetEq::kOK);
  // CN packet should be discarded, since it does not match the
  // new speech sample rate.
  EXPECT_EQ(neteq_->GetLifetimeStatistics().packets_discarded, 1u);

  // Next decoded packet should be speech.
  AudioFrame audio_frame;
  bool muted;
  EXPECT_EQ(neteq_->GetAudio(&audio_frame, &muted), NetEq::kOK);
  EXPECT_EQ(audio_frame.sample_rate_hz(), 16000);
  EXPECT_EQ(audio_frame.speech_type_, AudioFrame::SpeechType::kNormalSpeech);
}

TEST_F(NetEqImplTest, InsertPacketChangePayloadType) {
  UseNoMocks();
  CreateInstance();
  constexpr int kPcmuPayloadType = 7;
  neteq_->RegisterPayloadType(kPcmuPayloadType,
                              SdpAudioFormat("pcmu", 8000, 1));
  constexpr int kPcmaPayloadType = 8;
  neteq_->RegisterPayloadType(kPcmaPayloadType,
                              SdpAudioFormat("pcma", 8000, 1));

  RTPHeader header;
  header.payloadType = kPcmuPayloadType;
  header.timestamp = 1234;
  uint8_t payload[160] = {0};

  EXPECT_EQ(neteq_->InsertPacket(header, payload), NetEq::kOK);
  EXPECT_EQ(neteq_->GetLifetimeStatistics().packets_discarded, 0u);

  header.payloadType = kPcmaPayloadType;
  header.timestamp += 80;
  EXPECT_EQ(neteq_->InsertPacket(header, payload), NetEq::kOK);
  // The previous packet should be discarded since the codec changed.
  EXPECT_EQ(neteq_->GetLifetimeStatistics().packets_discarded, 1u);

  // Next decoded packet should be speech.
  AudioFrame audio_frame;
  bool muted;
  EXPECT_EQ(neteq_->GetAudio(&audio_frame, &muted), NetEq::kOK);
  EXPECT_EQ(audio_frame.sample_rate_hz(), 8000);
  EXPECT_EQ(audio_frame.speech_type_, AudioFrame::SpeechType::kNormalSpeech);
  // TODO(jakobi): check active decoder.
}

class Decoder120ms : public AudioDecoder {
 public:
  Decoder120ms(int sample_rate_hz, SpeechType speech_type)
      : sample_rate_hz_(sample_rate_hz),
        next_value_(1),
        speech_type_(speech_type) {}

  int DecodeInternal(const uint8_t* encoded,
                     size_t encoded_len,
                     int sample_rate_hz,
                     int16_t* decoded,
                     SpeechType* speech_type) override {
    EXPECT_EQ(sample_rate_hz_, sample_rate_hz);
    size_t decoded_len =
        rtc::CheckedDivExact(sample_rate_hz, 1000) * 120 * Channels();
    for (size_t i = 0; i < decoded_len; ++i) {
      decoded[i] = next_value_++;
    }
    *speech_type = speech_type_;
    return rtc::checked_cast<int>(decoded_len);
  }

  void Reset() override { next_value_ = 1; }
  int SampleRateHz() const override { return sample_rate_hz_; }
  size_t Channels() const override { return 2; }

 private:
  int sample_rate_hz_;
  int16_t next_value_;
  SpeechType speech_type_;
};

class NetEqImplTest120ms : public NetEqImplTest {
 protected:
  NetEqImplTest120ms() : NetEqImplTest() {}
  virtual ~NetEqImplTest120ms() {}

  void CreateInstanceNoMocks() {
    UseNoMocks();
    CreateInstance(decoder_factory_);
    EXPECT_TRUE(neteq_->RegisterPayloadType(
        kPayloadType, SdpAudioFormat("opus", 48000, 2, {{"stereo", "1"}})));
  }

  void CreateInstanceWithDelayManagerMock() {
    UseNoMocks();
    use_mock_neteq_controller_ = true;
    CreateInstance(decoder_factory_);
    EXPECT_TRUE(neteq_->RegisterPayloadType(
        kPayloadType, SdpAudioFormat("opus", 48000, 2, {{"stereo", "1"}})));
  }

  uint32_t timestamp_diff_between_packets() const {
    return rtc::CheckedDivExact(kSamplingFreq_, 1000u) * 120;
  }

  uint32_t first_timestamp() const { return 10u; }

  void GetFirstPacket() {
    bool muted;
    for (int i = 0; i < 12; i++) {
      EXPECT_EQ(NetEq::kOK, neteq_->GetAudio(&output_, &muted));
      EXPECT_FALSE(muted);
    }
  }

  void InsertPacket(uint32_t timestamp) {
    RTPHeader rtp_header;
    rtp_header.payloadType = kPayloadType;
    rtp_header.sequenceNumber = sequence_number_;
    rtp_header.timestamp = timestamp;
    rtp_header.ssrc = 15;
    const size_t kPayloadLengthBytes = 1;  // This can be arbitrary.
    uint8_t payload[kPayloadLengthBytes] = {0};
    EXPECT_EQ(NetEq::kOK, neteq_->InsertPacket(rtp_header, payload));
    sequence_number_++;
  }

  void Register120msCodec(AudioDecoder::SpeechType speech_type) {
    const uint32_t sampling_freq = kSamplingFreq_;
    decoder_factory_ = rtc::make_ref_counted<test::FunctionAudioDecoderFactory>(
        [sampling_freq, speech_type]() {
          std::unique_ptr<AudioDecoder> decoder =
              std::make_unique<Decoder120ms>(sampling_freq, speech_type);
          RTC_CHECK_EQ(2, decoder->Channels());
          return decoder;
        });
  }

  rtc::scoped_refptr<AudioDecoderFactory> decoder_factory_;
  AudioFrame output_;
  const uint32_t kPayloadType = 17;
  const uint32_t kSamplingFreq_ = 48000;
  uint16_t sequence_number_ = 1;
};

TEST_F(NetEqImplTest120ms, CodecInternalCng) {
  Register120msCodec(AudioDecoder::kComfortNoise);
  CreateInstanceNoMocks();

  InsertPacket(first_timestamp());
  GetFirstPacket();

  bool muted;
  EXPECT_EQ(NetEq::kOK, neteq_->GetAudio(&output_, &muted));
  EXPECT_EQ(NetEq::Operation::kCodecInternalCng,
            neteq_->last_operation_for_test());
}

TEST_F(NetEqImplTest120ms, Normal) {
  Register120msCodec(AudioDecoder::kSpeech);
  CreateInstanceNoMocks();

  InsertPacket(first_timestamp());
  GetFirstPacket();

  EXPECT_EQ(NetEq::Operation::kNormal, neteq_->last_operation_for_test());
}

TEST_F(NetEqImplTest120ms, Merge) {
  Register120msCodec(AudioDecoder::kSpeech);
  CreateInstanceWithDelayManagerMock();

  InsertPacket(first_timestamp());

  GetFirstPacket();
  bool muted;
  EXPECT_CALL(*mock_neteq_controller_, GetDecision(_, _))
      .WillOnce(Return(NetEq::Operation::kExpand));
  EXPECT_EQ(NetEq::kOK, neteq_->GetAudio(&output_, &muted));

  InsertPacket(first_timestamp() + 2 * timestamp_diff_between_packets());

  EXPECT_CALL(*mock_neteq_controller_, GetDecision(_, _))
      .WillOnce(Return(NetEq::Operation::kMerge));

  EXPECT_EQ(NetEq::kOK, neteq_->GetAudio(&output_, &muted));
  EXPECT_EQ(NetEq::Operation::kMerge, neteq_->last_operation_for_test());
}

TEST_F(NetEqImplTest120ms, Expand) {
  Register120msCodec(AudioDecoder::kSpeech);
  CreateInstanceNoMocks();

  InsertPacket(first_timestamp());
  GetFirstPacket();

  bool muted;
  EXPECT_EQ(NetEq::kOK, neteq_->GetAudio(&output_, &muted));
  EXPECT_EQ(NetEq::Operation::kExpand, neteq_->last_operation_for_test());
}

TEST_F(NetEqImplTest120ms, FastAccelerate) {
  Register120msCodec(AudioDecoder::kSpeech);
  CreateInstanceWithDelayManagerMock();

  InsertPacket(first_timestamp());
  GetFirstPacket();
  InsertPacket(first_timestamp() + timestamp_diff_between_packets());

  EXPECT_CALL(*mock_neteq_controller_, GetDecision(_, _))
      .Times(1)
      .WillOnce(Return(NetEq::Operation::kFastAccelerate));

  bool muted;
  EXPECT_EQ(NetEq::kOK, neteq_->GetAudio(&output_, &muted));
  EXPECT_EQ(NetEq::Operation::kFastAccelerate,
            neteq_->last_operation_for_test());
}

TEST_F(NetEqImplTest120ms, PreemptiveExpand) {
  Register120msCodec(AudioDecoder::kSpeech);
  CreateInstanceWithDelayManagerMock();

  InsertPacket(first_timestamp());
  GetFirstPacket();

  InsertPacket(first_timestamp() + timestamp_diff_between_packets());

  EXPECT_CALL(*mock_neteq_controller_, GetDecision(_, _))
      .Times(1)
      .WillOnce(Return(NetEq::Operation::kPreemptiveExpand));

  bool muted;
  EXPECT_EQ(NetEq::kOK, neteq_->GetAudio(&output_, &muted));
  EXPECT_EQ(NetEq::Operation::kPreemptiveExpand,
            neteq_->last_operation_for_test());
}

TEST_F(NetEqImplTest120ms, Accelerate) {
  Register120msCodec(AudioDecoder::kSpeech);
  CreateInstanceWithDelayManagerMock();

  InsertPacket(first_timestamp());
  GetFirstPacket();

  InsertPacket(first_timestamp() + timestamp_diff_between_packets());

  EXPECT_CALL(*mock_neteq_controller_, GetDecision(_, _))
      .Times(1)
      .WillOnce(Return(NetEq::Operation::kAccelerate));

  bool muted;
  EXPECT_EQ(NetEq::kOK, neteq_->GetAudio(&output_, &muted));
  EXPECT_EQ(NetEq::Operation::kAccelerate, neteq_->last_operation_for_test());
}

}  // namespace webrtc
