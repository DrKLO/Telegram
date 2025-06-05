/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/include/audio_coding_module.h"

#include <stdio.h>
#include <string.h>

#include <atomic>
#include <memory>
#include <vector>

#include "absl/strings/string_view.h"
#include "api/audio_codecs/audio_encoder.h"
#include "api/audio_codecs/builtin_audio_decoder_factory.h"
#include "api/audio_codecs/builtin_audio_encoder_factory.h"
#include "api/audio_codecs/opus/audio_decoder_multi_channel_opus.h"
#include "api/audio_codecs/opus/audio_decoder_opus.h"
#include "api/audio_codecs/opus/audio_encoder_multi_channel_opus.h"
#include "api/audio_codecs/opus/audio_encoder_opus.h"
#include "modules/audio_coding/acm2/acm_receive_test.h"
#include "modules/audio_coding/acm2/acm_send_test.h"
#include "modules/audio_coding/codecs/cng/audio_encoder_cng.h"
#include "modules/audio_coding/codecs/g711/audio_decoder_pcm.h"
#include "modules/audio_coding/codecs/g711/audio_encoder_pcm.h"
#include "modules/audio_coding/include/audio_coding_module_typedefs.h"
#include "modules/audio_coding/neteq/tools/audio_checksum.h"
#include "modules/audio_coding/neteq/tools/audio_loop.h"
#include "modules/audio_coding/neteq/tools/constant_pcm_packet_source.h"
#include "modules/audio_coding/neteq/tools/input_audio_file.h"
#include "modules/audio_coding/neteq/tools/output_audio_file.h"
#include "modules/audio_coding/neteq/tools/output_wav_file.h"
#include "modules/audio_coding/neteq/tools/packet.h"
#include "modules/audio_coding/neteq/tools/rtp_file_source.h"
#include "rtc_base/event.h"
#include "rtc_base/message_digest.h"
#include "rtc_base/numerics/safe_conversions.h"
#include "rtc_base/platform_thread.h"
#include "rtc_base/synchronization/mutex.h"
#include "rtc_base/system/arch.h"
#include "rtc_base/thread_annotations.h"
#include "system_wrappers/include/clock.h"
#include "system_wrappers/include/cpu_features_wrapper.h"
#include "system_wrappers/include/sleep.h"
#include "test/audio_decoder_proxy_factory.h"
#include "test/gtest.h"
#include "test/mock_audio_decoder.h"
#include "test/mock_audio_encoder.h"
#include "test/testsupport/file_utils.h"
#include "test/testsupport/rtc_expect_death.h"

using ::testing::_;
using ::testing::AtLeast;
using ::testing::Invoke;

namespace webrtc {

namespace {
const int kSampleRateHz = 16000;
const int kNumSamples10ms = kSampleRateHz / 100;
const int kFrameSizeMs = 10;  // Multiple of 10.
const int kFrameSizeSamples = kFrameSizeMs / 10 * kNumSamples10ms;
const int kPayloadSizeBytes = kFrameSizeSamples * sizeof(int16_t);
const uint8_t kPayloadType = 111;
}  // namespace

class RtpData {
 public:
  RtpData(int samples_per_packet, uint8_t payload_type)
      : samples_per_packet_(samples_per_packet), payload_type_(payload_type) {}

  virtual ~RtpData() {}

  void Populate(RTPHeader* rtp_header) {
    rtp_header->sequenceNumber = 0xABCD;
    rtp_header->timestamp = 0xABCDEF01;
    rtp_header->payloadType = payload_type_;
    rtp_header->markerBit = false;
    rtp_header->ssrc = 0x1234;
    rtp_header->numCSRCs = 0;
  }

  void Forward(RTPHeader* rtp_header) {
    ++rtp_header->sequenceNumber;
    rtp_header->timestamp += samples_per_packet_;
  }

 private:
  int samples_per_packet_;
  uint8_t payload_type_;
};

class PacketizationCallbackStubOldApi : public AudioPacketizationCallback {
 public:
  PacketizationCallbackStubOldApi()
      : num_calls_(0),
        last_frame_type_(AudioFrameType::kEmptyFrame),
        last_payload_type_(-1),
        last_timestamp_(0) {}

  int32_t SendData(AudioFrameType frame_type,
                   uint8_t payload_type,
                   uint32_t timestamp,
                   const uint8_t* payload_data,
                   size_t payload_len_bytes,
                   int64_t absolute_capture_timestamp_ms) override {
    MutexLock lock(&mutex_);
    ++num_calls_;
    last_frame_type_ = frame_type;
    last_payload_type_ = payload_type;
    last_timestamp_ = timestamp;
    last_payload_vec_.assign(payload_data, payload_data + payload_len_bytes);
    return 0;
  }

  int num_calls() const {
    MutexLock lock(&mutex_);
    return num_calls_;
  }

  int last_payload_len_bytes() const {
    MutexLock lock(&mutex_);
    return rtc::checked_cast<int>(last_payload_vec_.size());
  }

  AudioFrameType last_frame_type() const {
    MutexLock lock(&mutex_);
    return last_frame_type_;
  }

  int last_payload_type() const {
    MutexLock lock(&mutex_);
    return last_payload_type_;
  }

  uint32_t last_timestamp() const {
    MutexLock lock(&mutex_);
    return last_timestamp_;
  }

  void SwapBuffers(std::vector<uint8_t>* payload) {
    MutexLock lock(&mutex_);
    last_payload_vec_.swap(*payload);
  }

 private:
  int num_calls_ RTC_GUARDED_BY(mutex_);
  AudioFrameType last_frame_type_ RTC_GUARDED_BY(mutex_);
  int last_payload_type_ RTC_GUARDED_BY(mutex_);
  uint32_t last_timestamp_ RTC_GUARDED_BY(mutex_);
  std::vector<uint8_t> last_payload_vec_ RTC_GUARDED_BY(mutex_);
  mutable Mutex mutex_;
};

class AudioCodingModuleTestOldApi : public ::testing::Test {
 protected:
  AudioCodingModuleTestOldApi()
      : rtp_utility_(new RtpData(kFrameSizeSamples, kPayloadType)),
        clock_(Clock::GetRealTimeClock()) {}

  ~AudioCodingModuleTestOldApi() {}

  void TearDown() {}

  void SetUp() {
    acm_ = AudioCodingModule::Create();
    acm2::AcmReceiver::Config config;
    config.clock = *clock_;
    config.decoder_factory = CreateBuiltinAudioDecoderFactory();
    acm_receiver_ = std::make_unique<acm2::AcmReceiver>(config);

    rtp_utility_->Populate(&rtp_header_);

    input_frame_.sample_rate_hz_ = kSampleRateHz;
    input_frame_.num_channels_ = 1;
    input_frame_.samples_per_channel_ = kSampleRateHz * 10 / 1000;  // 10 ms.
    static_assert(kSampleRateHz * 10 / 1000 <= AudioFrame::kMaxDataSizeSamples,
                  "audio frame too small");
    input_frame_.Mute();

    ASSERT_EQ(0, acm_->RegisterTransportCallback(&packet_cb_));

    SetUpL16Codec();
  }

  // Set up L16 codec.
  virtual void SetUpL16Codec() {
    audio_format_ = SdpAudioFormat("L16", kSampleRateHz, 1);
    pac_size_ = 160;
  }

  virtual void RegisterCodec() {
    acm_receiver_->SetCodecs({{kPayloadType, *audio_format_}});
    acm_->SetEncoder(CreateBuiltinAudioEncoderFactory()->MakeAudioEncoder(
        kPayloadType, *audio_format_, absl::nullopt));
  }

  virtual void InsertPacketAndPullAudio() {
    InsertPacket();
    PullAudio();
  }

  virtual void InsertPacket() {
    const uint8_t kPayload[kPayloadSizeBytes] = {0};
    ASSERT_EQ(0, acm_receiver_->InsertPacket(rtp_header_,
                                             rtc::ArrayView<const uint8_t>(
                                                 kPayload, kPayloadSizeBytes)));
    rtp_utility_->Forward(&rtp_header_);
  }

  virtual void PullAudio() {
    AudioFrame audio_frame;
    bool muted;
    ASSERT_EQ(0, acm_receiver_->GetAudio(-1, &audio_frame, &muted));
    ASSERT_FALSE(muted);
  }

  virtual void InsertAudio() {
    ASSERT_GE(acm_->Add10MsData(input_frame_), 0);
    input_frame_.timestamp_ += kNumSamples10ms;
  }

  virtual void VerifyEncoding() {
    int last_length = packet_cb_.last_payload_len_bytes();
    EXPECT_TRUE(last_length == 2 * pac_size_ || last_length == 0)
        << "Last encoded packet was " << last_length << " bytes.";
  }

  virtual void InsertAudioAndVerifyEncoding() {
    InsertAudio();
    VerifyEncoding();
  }

  std::unique_ptr<RtpData> rtp_utility_;
  std::unique_ptr<AudioCodingModule> acm_;
  std::unique_ptr<acm2::AcmReceiver> acm_receiver_;
  PacketizationCallbackStubOldApi packet_cb_;
  RTPHeader rtp_header_;
  AudioFrame input_frame_;

  absl::optional<SdpAudioFormat> audio_format_;
  int pac_size_ = -1;

  Clock* clock_;
};

class AudioCodingModuleTestOldApiDeathTest
    : public AudioCodingModuleTestOldApi {};

// The below test is temporarily disabled on Windows due to problems
// with clang debug builds.
// TODO(tommi): Re-enable when we've figured out what the problem is.
// http://crbug.com/615050
#if !defined(WEBRTC_WIN) && defined(__clang__) && RTC_DCHECK_IS_ON && \
    GTEST_HAS_DEATH_TEST && !defined(WEBRTC_ANDROID)
TEST_F(AudioCodingModuleTestOldApiDeathTest, FailOnZeroDesiredFrequency) {
  AudioFrame audio_frame;
  bool muted;
  RTC_EXPECT_DEATH(acm_receiver_->GetAudio(0, &audio_frame, &muted),
                   "dst_sample_rate_hz");
}
#endif

// Checks that the transport callback is invoked once for each speech packet.
// Also checks that the frame type is kAudioFrameSpeech.
TEST_F(AudioCodingModuleTestOldApi, TransportCallbackIsInvokedForEachPacket) {
  const int k10MsBlocksPerPacket = 3;
  pac_size_ = k10MsBlocksPerPacket * kSampleRateHz / 100;
  audio_format_->parameters["ptime"] = "30";
  RegisterCodec();
  const int kLoops = 10;
  for (int i = 0; i < kLoops; ++i) {
    EXPECT_EQ(i / k10MsBlocksPerPacket, packet_cb_.num_calls());
    if (packet_cb_.num_calls() > 0)
      EXPECT_EQ(AudioFrameType::kAudioFrameSpeech,
                packet_cb_.last_frame_type());
    InsertAudioAndVerifyEncoding();
  }
  EXPECT_EQ(kLoops / k10MsBlocksPerPacket, packet_cb_.num_calls());
  EXPECT_EQ(AudioFrameType::kAudioFrameSpeech, packet_cb_.last_frame_type());
}

// Introduce this class to set different expectations on the number of encoded
// bytes. This class expects all encoded packets to be 9 bytes (matching one
// CNG SID frame) or 0 bytes. This test depends on `input_frame_` containing
// (near-)zero values. It also introduces a way to register comfort noise with
// a custom payload type.
class AudioCodingModuleTestWithComfortNoiseOldApi
    : public AudioCodingModuleTestOldApi {
 protected:
  void RegisterCngCodec(int rtp_payload_type) {
    acm_receiver_->SetCodecs({{kPayloadType, *audio_format_},
                              {rtp_payload_type, {"cn", kSampleRateHz, 1}}});
    acm_->ModifyEncoder([&](std::unique_ptr<AudioEncoder>* enc) {
      AudioEncoderCngConfig config;
      config.speech_encoder = std::move(*enc);
      config.num_channels = 1;
      config.payload_type = rtp_payload_type;
      config.vad_mode = Vad::kVadNormal;
      *enc = CreateComfortNoiseEncoder(std::move(config));
    });
  }

  void VerifyEncoding() override {
    int last_length = packet_cb_.last_payload_len_bytes();
    EXPECT_TRUE(last_length == 9 || last_length == 0)
        << "Last encoded packet was " << last_length << " bytes.";
  }

  void DoTest(int blocks_per_packet, int cng_pt) {
    const int kLoops = 40;
    // This array defines the expected frame types, and when they should arrive.
    // We expect a frame to arrive each time the speech encoder would have
    // produced a packet, and once every 100 ms the frame should be non-empty,
    // that is contain comfort noise.
    const struct {
      int ix;
      AudioFrameType type;
    } expectation[] = {{2, AudioFrameType::kAudioFrameCN},
                       {5, AudioFrameType::kEmptyFrame},
                       {8, AudioFrameType::kEmptyFrame},
                       {11, AudioFrameType::kAudioFrameCN},
                       {14, AudioFrameType::kEmptyFrame},
                       {17, AudioFrameType::kEmptyFrame},
                       {20, AudioFrameType::kAudioFrameCN},
                       {23, AudioFrameType::kEmptyFrame},
                       {26, AudioFrameType::kEmptyFrame},
                       {29, AudioFrameType::kEmptyFrame},
                       {32, AudioFrameType::kAudioFrameCN},
                       {35, AudioFrameType::kEmptyFrame},
                       {38, AudioFrameType::kEmptyFrame}};
    for (int i = 0; i < kLoops; ++i) {
      int num_calls_before = packet_cb_.num_calls();
      EXPECT_EQ(i / blocks_per_packet, num_calls_before);
      InsertAudioAndVerifyEncoding();
      int num_calls = packet_cb_.num_calls();
      if (num_calls == num_calls_before + 1) {
        EXPECT_EQ(expectation[num_calls - 1].ix, i);
        EXPECT_EQ(expectation[num_calls - 1].type, packet_cb_.last_frame_type())
            << "Wrong frame type for lap " << i;
        EXPECT_EQ(cng_pt, packet_cb_.last_payload_type());
      } else {
        EXPECT_EQ(num_calls, num_calls_before);
      }
    }
  }
};

// Checks that the transport callback is invoked once per frame period of the
// underlying speech encoder, even when comfort noise is produced.
// Also checks that the frame type is kAudioFrameCN or kEmptyFrame.
TEST_F(AudioCodingModuleTestWithComfortNoiseOldApi,
       TransportCallbackTestForComfortNoiseRegisterCngLast) {
  const int k10MsBlocksPerPacket = 3;
  pac_size_ = k10MsBlocksPerPacket * kSampleRateHz / 100;
  audio_format_->parameters["ptime"] = "30";
  RegisterCodec();
  const int kCngPayloadType = 105;
  RegisterCngCodec(kCngPayloadType);
  DoTest(k10MsBlocksPerPacket, kCngPayloadType);
}

// A multi-threaded test for ACM that uses the PCM16b 16 kHz codec.
class AudioCodingModuleMtTestOldApi : public AudioCodingModuleTestOldApi {
 protected:
  static const int kNumPackets = 500;
  static const int kNumPullCalls = 500;

  AudioCodingModuleMtTestOldApi()
      : AudioCodingModuleTestOldApi(),
        send_count_(0),
        insert_packet_count_(0),
        pull_audio_count_(0),
        next_insert_packet_time_ms_(0),
        fake_clock_(new SimulatedClock(0)) {
    clock_ = fake_clock_.get();
  }

  void SetUp() {
    AudioCodingModuleTestOldApi::SetUp();
    RegisterCodec();  // Must be called before the threads start below.
    StartThreads();
  }

  void StartThreads() {
    quit_.store(false);

    const auto attributes =
        rtc::ThreadAttributes().SetPriority(rtc::ThreadPriority::kRealtime);
    send_thread_ = rtc::PlatformThread::SpawnJoinable(
        [this] {
          while (!quit_.load()) {
            CbSendImpl();
          }
        },
        "send", attributes);
    insert_packet_thread_ = rtc::PlatformThread::SpawnJoinable(
        [this] {
          while (!quit_.load()) {
            CbInsertPacketImpl();
          }
        },
        "insert_packet", attributes);
    pull_audio_thread_ = rtc::PlatformThread::SpawnJoinable(
        [this] {
          while (!quit_.load()) {
            CbPullAudioImpl();
          }
        },
        "pull_audio", attributes);
  }

  void TearDown() {
    AudioCodingModuleTestOldApi::TearDown();
    quit_.store(true);
    pull_audio_thread_.Finalize();
    send_thread_.Finalize();
    insert_packet_thread_.Finalize();
  }

  bool RunTest() { return test_complete_.Wait(TimeDelta::Minutes(10)); }

  virtual bool TestDone() {
    if (packet_cb_.num_calls() > kNumPackets) {
      MutexLock lock(&mutex_);
      if (pull_audio_count_ > kNumPullCalls) {
        // Both conditions for completion are met. End the test.
        return true;
      }
    }
    return false;
  }

  // The send thread doesn't have to care about the current simulated time,
  // since only the AcmReceiver is using the clock.
  void CbSendImpl() {
    SleepMs(1);
    if (HasFatalFailure()) {
      // End the test early if a fatal failure (ASSERT_*) has occurred.
      test_complete_.Set();
    }
    ++send_count_;
    InsertAudioAndVerifyEncoding();
    if (TestDone()) {
      test_complete_.Set();
    }
  }

  void CbInsertPacketImpl() {
    SleepMs(1);
    {
      MutexLock lock(&mutex_);
      if (clock_->TimeInMilliseconds() < next_insert_packet_time_ms_) {
        return;
      }
      next_insert_packet_time_ms_ += 10;
    }
    // Now we're not holding the crit sect when calling ACM.
    ++insert_packet_count_;
    InsertPacket();
  }

  void CbPullAudioImpl() {
    SleepMs(1);
    {
      MutexLock lock(&mutex_);
      // Don't let the insert thread fall behind.
      if (next_insert_packet_time_ms_ < clock_->TimeInMilliseconds()) {
        return;
      }
      ++pull_audio_count_;
    }
    // Now we're not holding the crit sect when calling ACM.
    PullAudio();
    fake_clock_->AdvanceTimeMilliseconds(10);
  }

  rtc::PlatformThread send_thread_;
  rtc::PlatformThread insert_packet_thread_;
  rtc::PlatformThread pull_audio_thread_;
  // Used to force worker threads to stop looping.
  std::atomic<bool> quit_;

  rtc::Event test_complete_;
  int send_count_;
  int insert_packet_count_;
  int pull_audio_count_ RTC_GUARDED_BY(mutex_);
  Mutex mutex_;
  int64_t next_insert_packet_time_ms_ RTC_GUARDED_BY(mutex_);
  std::unique_ptr<SimulatedClock> fake_clock_;
};

#if defined(WEBRTC_IOS)
#define MAYBE_DoTest DISABLED_DoTest
#else
#define MAYBE_DoTest DoTest
#endif
TEST_F(AudioCodingModuleMtTestOldApi, MAYBE_DoTest) {
  EXPECT_TRUE(RunTest());
}

// Disabling all of these tests on iOS until file support has been added.
// See https://code.google.com/p/webrtc/issues/detail?id=4752 for details.
#if !defined(WEBRTC_IOS)

// This test verifies bit exactness for the send-side of ACM. The test setup is
// a chain of three different test classes:
//
// test::AcmSendTest -> AcmSenderBitExactness -> test::AcmReceiveTest
//
// The receiver side is driving the test by requesting new packets from
// AcmSenderBitExactness::NextPacket(). This method, in turn, asks for the
// packet from test::AcmSendTest::NextPacket, which inserts audio from the
// input file until one packet is produced. (The input file loops indefinitely.)
// Before passing the packet to the receiver, this test class verifies the
// packet header and updates a payload checksum with the new payload. The
// decoded output from the receiver is also verified with a (separate) checksum.
class AcmSenderBitExactnessOldApi : public ::testing::Test,
                                    public test::PacketSource {
 protected:
  static const int kTestDurationMs = 1000;

  AcmSenderBitExactnessOldApi()
      : frame_size_rtp_timestamps_(0),
        packet_count_(0),
        payload_type_(0),
        last_sequence_number_(0),
        last_timestamp_(0),
        payload_checksum_(rtc::MessageDigestFactory::Create(rtc::DIGEST_MD5)) {}

  // Sets up the test::AcmSendTest object. Returns true on success, otherwise
  // false.
  bool SetUpSender(absl::string_view input_file_name, int source_rate) {
    // Note that `audio_source_` will loop forever. The test duration is set
    // explicitly by `kTestDurationMs`.
    audio_source_.reset(new test::InputAudioFile(input_file_name));
    send_test_.reset(new test::AcmSendTestOldApi(audio_source_.get(),
                                                 source_rate, kTestDurationMs));
    return send_test_.get() != NULL;
  }

  // Registers a send codec in the test::AcmSendTest object. Returns true on
  // success, false on failure.
  bool RegisterSendCodec(absl::string_view payload_name,
                         int sampling_freq_hz,
                         int channels,
                         int payload_type,
                         int frame_size_samples,
                         int frame_size_rtp_timestamps) {
    payload_type_ = payload_type;
    frame_size_rtp_timestamps_ = frame_size_rtp_timestamps;
    return send_test_->RegisterCodec(payload_name, sampling_freq_hz, channels,
                                     payload_type, frame_size_samples);
  }

  void RegisterExternalSendCodec(
      std::unique_ptr<AudioEncoder> external_speech_encoder,
      int payload_type) {
    payload_type_ = payload_type;
    frame_size_rtp_timestamps_ = rtc::checked_cast<uint32_t>(
        external_speech_encoder->Num10MsFramesInNextPacket() *
        external_speech_encoder->RtpTimestampRateHz() / 100);
    send_test_->RegisterExternalCodec(std::move(external_speech_encoder));
  }

  // Runs the test. SetUpSender() and RegisterSendCodec() must have been called
  // before calling this method.
  void Run(absl::string_view audio_checksum_ref,
           absl::string_view payload_checksum_ref,
           int expected_packets,
           test::AcmReceiveTestOldApi::NumOutputChannels expected_channels,
           rtc::scoped_refptr<AudioDecoderFactory> decoder_factory = nullptr) {
    if (!decoder_factory) {
      decoder_factory = CreateBuiltinAudioDecoderFactory();
    }
    // Set up the receiver used to decode the packets and verify the decoded
    // output.
    test::AudioChecksum audio_checksum;
    const std::string output_file_name =
        webrtc::test::OutputPath() +
        ::testing::UnitTest::GetInstance()
            ->current_test_info()
            ->test_case_name() +
        "_" + ::testing::UnitTest::GetInstance()->current_test_info()->name() +
        "_output.wav";
    const int kOutputFreqHz = 8000;
    test::OutputWavFile output_file(output_file_name, kOutputFreqHz,
                                    expected_channels);
    // Have the output audio sent both to file and to the checksum calculator.
    test::AudioSinkFork output(&audio_checksum, &output_file);
    test::AcmReceiveTestOldApi receive_test(this, &output, kOutputFreqHz,
                                            expected_channels, decoder_factory);
    ASSERT_NO_FATAL_FAILURE(receive_test.RegisterDefaultCodecs());

    // This is where the actual test is executed.
    receive_test.Run();

    // Extract and verify the audio checksum.
    std::string checksum_string = audio_checksum.Finish();
    ExpectChecksumEq(audio_checksum_ref, checksum_string);

    // Extract and verify the payload checksum.
    rtc::Buffer checksum_result(payload_checksum_->Size());
    payload_checksum_->Finish(checksum_result.data(), checksum_result.size());
    checksum_string = rtc::hex_encode(checksum_result);
    ExpectChecksumEq(payload_checksum_ref, checksum_string);

    // Verify number of packets produced.
    EXPECT_EQ(expected_packets, packet_count_);

    // Delete the output file.
    remove(output_file_name.c_str());
  }

  // Helper: result must be one the "|"-separated checksums.
  void ExpectChecksumEq(absl::string_view ref, absl::string_view result) {
    if (ref.size() == result.size()) {
      // Only one checksum: clearer message.
      EXPECT_EQ(ref, result);
    } else {
      EXPECT_NE(ref.find(result), absl::string_view::npos)
          << result << " must be one of these:\n"
          << ref;
    }
  }

  // Inherited from test::PacketSource.
  std::unique_ptr<test::Packet> NextPacket() override {
    auto packet = send_test_->NextPacket();
    if (!packet)
      return NULL;

    VerifyPacket(packet.get());
    // TODO(henrik.lundin) Save the packet to file as well.

    // Pass it on to the caller. The caller becomes the owner of `packet`.
    return packet;
  }

  // Verifies the packet.
  void VerifyPacket(const test::Packet* packet) {
    EXPECT_TRUE(packet->valid_header());
    // (We can check the header fields even if valid_header() is false.)
    EXPECT_EQ(payload_type_, packet->header().payloadType);
    if (packet_count_ > 0) {
      // This is not the first packet.
      uint16_t sequence_number_diff =
          packet->header().sequenceNumber - last_sequence_number_;
      EXPECT_EQ(1, sequence_number_diff);
      uint32_t timestamp_diff = packet->header().timestamp - last_timestamp_;
      EXPECT_EQ(frame_size_rtp_timestamps_, timestamp_diff);
    }
    ++packet_count_;
    last_sequence_number_ = packet->header().sequenceNumber;
    last_timestamp_ = packet->header().timestamp;
    // Update the checksum.
    payload_checksum_->Update(packet->payload(),
                              packet->payload_length_bytes());
  }

  void SetUpTest(absl::string_view codec_name,
                 int codec_sample_rate_hz,
                 int channels,
                 int payload_type,
                 int codec_frame_size_samples,
                 int codec_frame_size_rtp_timestamps) {
    ASSERT_TRUE(SetUpSender(
        channels == 1 ? kTestFileMono32kHz : kTestFileFakeStereo32kHz, 32000));
    ASSERT_TRUE(RegisterSendCodec(codec_name, codec_sample_rate_hz, channels,
                                  payload_type, codec_frame_size_samples,
                                  codec_frame_size_rtp_timestamps));
  }

  void SetUpTestExternalEncoder(
      std::unique_ptr<AudioEncoder> external_speech_encoder,
      int payload_type) {
    ASSERT_TRUE(send_test_);
    RegisterExternalSendCodec(std::move(external_speech_encoder), payload_type);
  }

  std::unique_ptr<test::AcmSendTestOldApi> send_test_;
  std::unique_ptr<test::InputAudioFile> audio_source_;
  uint32_t frame_size_rtp_timestamps_;
  int packet_count_;
  uint8_t payload_type_;
  uint16_t last_sequence_number_;
  uint32_t last_timestamp_;
  std::unique_ptr<rtc::MessageDigest> payload_checksum_;
  const std::string kTestFileMono32kHz =
      webrtc::test::ResourcePath("audio_coding/testfile32kHz", "pcm");
  const std::string kTestFileFakeStereo32kHz =
      webrtc::test::ResourcePath("audio_coding/testfile_fake_stereo_32kHz",
                                 "pcm");
  const std::string kTestFileQuad48kHz = webrtc::test::ResourcePath(
      "audio_coding/speech_4_channels_48k_one_second",
      "wav");
};

class AcmSenderBitExactnessNewApi : public AcmSenderBitExactnessOldApi {};

TEST_F(AcmSenderBitExactnessOldApi, Pcm16_8000khz_10ms) {
  ASSERT_NO_FATAL_FAILURE(SetUpTest("L16", 8000, 1, 107, 80, 80));
  Run(/*audio_checksum_ref=*/"3e43fd5d3c73a59e8118e68fbfafe2c7",
      /*payload_checksum_ref=*/"c1edd36339ce0326cc4550041ad719a0",
      /*expected_packets=*/100,
      /*expected_channels=*/test::AcmReceiveTestOldApi::kMonoOutput);
}

TEST_F(AcmSenderBitExactnessOldApi, Pcm16_16000khz_10ms) {
  ASSERT_NO_FATAL_FAILURE(SetUpTest("L16", 16000, 1, 108, 160, 160));
  Run(/*audio_checksum_ref=*/"608750138315cbab33d76d38e8367807",
      /*payload_checksum_ref=*/"ad786526383178b08d80d6eee06e9bad",
      /*expected_packets=*/100,
      /*expected_channels=*/test::AcmReceiveTestOldApi::kMonoOutput);
}

TEST_F(AcmSenderBitExactnessOldApi, Pcm16_32000khz_10ms) {
  ASSERT_NO_FATAL_FAILURE(SetUpTest("L16", 32000, 1, 109, 320, 320));
  Run(/*audio_checksum_ref=*/"02e9927ef5e4d2cd792a5df0bdee5e19",
      /*payload_checksum_ref=*/"5ef82ea885e922263606c6fdbc49f651",
      /*expected_packets=*/100,
      /*expected_channels=*/test::AcmReceiveTestOldApi::kMonoOutput);
}

TEST_F(AcmSenderBitExactnessOldApi, Pcm16_stereo_8000khz_10ms) {
  ASSERT_NO_FATAL_FAILURE(SetUpTest("L16", 8000, 2, 111, 80, 80));
  Run(/*audio_checksum_ref=*/"4ff38de045b19f64de9c7e229ba36317",
      /*payload_checksum_ref=*/"62ce5adb0d4965d0a52ec98ae7f98974",
      /*expected_packets=*/100,
      /*expected_channels=*/test::AcmReceiveTestOldApi::kStereoOutput);
}

TEST_F(AcmSenderBitExactnessOldApi, Pcm16_stereo_16000khz_10ms) {
  ASSERT_NO_FATAL_FAILURE(SetUpTest("L16", 16000, 2, 112, 160, 160));
  Run(/*audio_checksum_ref=*/"1ee35394cfca78ad6d55468441af36fa",
      /*payload_checksum_ref=*/"41ca8edac4b8c71cd54fd9f25ec14870",
      /*expected_packets=*/100,
      /*expected_channels=*/test::AcmReceiveTestOldApi::kStereoOutput);
}

TEST_F(AcmSenderBitExactnessOldApi, Pcm16_stereo_32000khz_10ms) {
  ASSERT_NO_FATAL_FAILURE(SetUpTest("L16", 32000, 2, 113, 320, 320));
  Run(/*audio_checksum_ref=*/"19cae34730a0f6a17cf4e76bf21b69d6",
      /*payload_checksum_ref=*/"50e58502fb04421bf5b857dda4c96879",
      /*expected_packets=*/100,
      /*expected_channels=*/test::AcmReceiveTestOldApi::kStereoOutput);
}

TEST_F(AcmSenderBitExactnessOldApi, Pcmu_20ms) {
  ASSERT_NO_FATAL_FAILURE(SetUpTest("PCMU", 8000, 1, 0, 160, 160));
  Run(/*audio_checksum_ref=*/"c8d1fc677f33c2022ec5f83c7f302280",
      /*payload_checksum_ref=*/"8f9b8750bd80fe26b6cbf6659b89f0f9",
      /*expected_packets=*/50,
      /*expected_channels=*/test::AcmReceiveTestOldApi::kMonoOutput);
}

TEST_F(AcmSenderBitExactnessOldApi, Pcma_20ms) {
  ASSERT_NO_FATAL_FAILURE(SetUpTest("PCMA", 8000, 1, 8, 160, 160));
  Run(/*audio_checksum_ref=*/"ae259cab624095270b7369e53a7b53a3",
      /*payload_checksum_ref=*/"6ad745e55aa48981bfc790d0eeef2dd1",
      /*expected_packets=*/50,
      /*expected_channels=*/test::AcmReceiveTestOldApi::kMonoOutput);
}

TEST_F(AcmSenderBitExactnessOldApi, Pcmu_stereo_20ms) {
  ASSERT_NO_FATAL_FAILURE(SetUpTest("PCMU", 8000, 2, 110, 160, 160));
  Run(/*audio_checksum_ref=*/"6ef2f57d4934714787fd0a834e3ea18e",
      /*payload_checksum_ref=*/"60b6f25e8d1e74cb679cfe756dd9bca5",
      /*expected_packets=*/50,
      /*expected_channels=*/test::AcmReceiveTestOldApi::kStereoOutput);
}

TEST_F(AcmSenderBitExactnessOldApi, Pcma_stereo_20ms) {
  ASSERT_NO_FATAL_FAILURE(SetUpTest("PCMA", 8000, 2, 118, 160, 160));
  Run(/*audio_checksum_ref=*/"f2e81d2531a805c40e61da5106b50006",
      /*payload_checksum_ref=*/"92b282c83efd20e7eeef52ba40842cf7",
      /*expected_packets=*/50,
      /*expected_channels=*/test::AcmReceiveTestOldApi::kStereoOutput);
}

#if defined(WEBRTC_CODEC_ILBC) && defined(WEBRTC_LINUX) && \
    defined(WEBRTC_ARCH_X86_64)
TEST_F(AcmSenderBitExactnessOldApi, Ilbc_30ms) {
  ASSERT_NO_FATAL_FAILURE(SetUpTest("ILBC", 8000, 1, 102, 240, 240));
  Run(/*audio_checksum_ref=*/"a739434bec8a754e9356ce2115603ce5",
      /*payload_checksum_ref=*/"cfae2e9f6aba96e145f2bcdd5050ce78",
      /*expected_packets=*/33,
      /*expected_channels=*/test::AcmReceiveTestOldApi::kMonoOutput);
}
#endif

#if defined(WEBRTC_LINUX) && defined(WEBRTC_ARCH_X86_64)
TEST_F(AcmSenderBitExactnessOldApi, G722_20ms) {
  ASSERT_NO_FATAL_FAILURE(SetUpTest("G722", 16000, 1, 9, 320, 160));
  Run(/*audio_checksum_ref=*/"b875d9a3e41f5470857bdff02e3b368f",
      /*payload_checksum_ref=*/"fc68a87e1380614e658087cb35d5ca10",
      /*expected_packets=*/50,
      /*expected_channels=*/test::AcmReceiveTestOldApi::kMonoOutput);
}
#endif

#if defined(WEBRTC_LINUX) && defined(WEBRTC_ARCH_X86_64)
TEST_F(AcmSenderBitExactnessOldApi, G722_stereo_20ms) {
  ASSERT_NO_FATAL_FAILURE(SetUpTest("G722", 16000, 2, 119, 320, 160));
  Run(/*audio_checksum_ref=*/"02c427d73363b2f37853a0dd17fe1aba",
      /*payload_checksum_ref=*/"66516152eeaa1e650ad94ff85f668dac",
      /*expected_packets=*/50,
      /*expected_channels=*/test::AcmReceiveTestOldApi::kStereoOutput);
}
#endif

namespace {
// Checksum depends on libopus being compiled with or without SSE.
const std::string audio_checksum =
    "6a76fe2ffba057c06eb63239b3c47abe"
    "|0c4f9d33b4a7379a34ee0c0d5718afe6";
const std::string payload_checksum =
    "b43bdf7638b2bc2a5a6f30bdc640b9ed"
    "|c30d463e7ed10bdd1da9045f80561f27";
}  // namespace

#if defined(WEBRTC_LINUX) && defined(WEBRTC_ARCH_X86_64)
TEST_F(AcmSenderBitExactnessOldApi, Opus_stereo_20ms) {
  ASSERT_NO_FATAL_FAILURE(SetUpTest("opus", 48000, 2, 120, 960, 960));
  Run(audio_checksum, payload_checksum, /*expected_packets=*/50,
      /*expected_channels=*/test::AcmReceiveTestOldApi::kStereoOutput);
}
#endif

#if defined(WEBRTC_LINUX) && defined(WEBRTC_ARCH_X86_64)
TEST_F(AcmSenderBitExactnessNewApi, OpusFromFormat_stereo_20ms) {
  const auto config = AudioEncoderOpus::SdpToConfig(
      SdpAudioFormat("opus", 48000, 2, {{"stereo", "1"}}));
  ASSERT_TRUE(SetUpSender(kTestFileFakeStereo32kHz, 32000));
  ASSERT_NO_FATAL_FAILURE(SetUpTestExternalEncoder(
      AudioEncoderOpus::MakeAudioEncoder(*config, 120), 120));
  Run(audio_checksum, payload_checksum, /*expected_packets=*/50,
      /*expected_channels=*/test::AcmReceiveTestOldApi::kStereoOutput);
}
#endif

// TODO(webrtc:8649): Disabled until the Encoder counterpart of
// https://webrtc-review.googlesource.com/c/src/+/129768 lands.
#if defined(WEBRTC_LINUX) && defined(WEBRTC_ARCH_X86_64)
TEST_F(AcmSenderBitExactnessNewApi, DISABLED_OpusManyChannels) {
  constexpr int kNumChannels = 4;
  constexpr int kOpusPayloadType = 120;

  // Read a 4 channel file at 48kHz.
  ASSERT_TRUE(SetUpSender(kTestFileQuad48kHz, 48000));

  const auto sdp_format = SdpAudioFormat("multiopus", 48000, kNumChannels,
                                         {{"channel_mapping", "0,1,2,3"},
                                          {"coupled_streams", "2"},
                                          {"num_streams", "2"}});
  const auto encoder_config =
      AudioEncoderMultiChannelOpus::SdpToConfig(sdp_format);

  ASSERT_TRUE(encoder_config.has_value());

  ASSERT_NO_FATAL_FAILURE(
      SetUpTestExternalEncoder(AudioEncoderMultiChannelOpus::MakeAudioEncoder(
                                   *encoder_config, kOpusPayloadType),
                               kOpusPayloadType));

  const auto decoder_config =
      AudioDecoderMultiChannelOpus::SdpToConfig(sdp_format);
  const auto opus_decoder =
      AudioDecoderMultiChannelOpus::MakeAudioDecoder(*decoder_config);

  rtc::scoped_refptr<AudioDecoderFactory> decoder_factory =
      rtc::make_ref_counted<test::AudioDecoderProxyFactory>(opus_decoder.get());

  // Set up an EXTERNAL DECODER to parse 4 channels.
  Run("audio checksum check downstream|8051617907766bec5f4e4a4f7c6d5291",
      "payload checksum check downstream|b09c52e44b2bdd9a0809e3a5b1623a76",
      /*expected_packets=*/50,
      /*expected_channels=*/test::AcmReceiveTestOldApi::kQuadOutput,
      decoder_factory);
}
#endif

#if defined(WEBRTC_LINUX) && defined(WEBRTC_ARCH_X86_64)
TEST_F(AcmSenderBitExactnessNewApi, OpusFromFormat_stereo_20ms_voip) {
  auto config = AudioEncoderOpus::SdpToConfig(
      SdpAudioFormat("opus", 48000, 2, {{"stereo", "1"}}));
  // If not set, default will be kAudio in case of stereo.
  config->application = AudioEncoderOpusConfig::ApplicationMode::kVoip;
  ASSERT_TRUE(SetUpSender(kTestFileFakeStereo32kHz, 32000));
  ASSERT_NO_FATAL_FAILURE(SetUpTestExternalEncoder(
      AudioEncoderOpus::MakeAudioEncoder(*config, 120), 120));
  const std::string audio_maybe_sse =
      "cb644fc17d9666a0f5986eef24818159"
      "|4a74024473c7c729543c2790829b1e42";

  const std::string payload_maybe_sse =
      "ea48d94e43217793af9b7e15ece94e54"
      "|bd93c492087093daf662cdd968f6cdda";

  Run(audio_maybe_sse, payload_maybe_sse, /*expected_packets=*/50,
      /*expected_channels=*/test::AcmReceiveTestOldApi::kStereoOutput);
}
#endif

// This test is for verifying the SetBitRate function. The bitrate is changed at
// the beginning, and the number of generated bytes are checked.
class AcmSetBitRateTest : public ::testing::Test {
 protected:
  static const int kTestDurationMs = 1000;

  // Sets up the test::AcmSendTest object. Returns true on success, otherwise
  // false.
  bool SetUpSender() {
    const std::string input_file_name =
        webrtc::test::ResourcePath("audio_coding/testfile32kHz", "pcm");
    // Note that `audio_source_` will loop forever. The test duration is set
    // explicitly by `kTestDurationMs`.
    audio_source_.reset(new test::InputAudioFile(input_file_name));
    static const int kSourceRateHz = 32000;
    send_test_.reset(new test::AcmSendTestOldApi(
        audio_source_.get(), kSourceRateHz, kTestDurationMs));
    return send_test_.get();
  }

  // Registers a send codec in the test::AcmSendTest object. Returns true on
  // success, false on failure.
  virtual bool RegisterSendCodec(absl::string_view payload_name,
                                 int sampling_freq_hz,
                                 int channels,
                                 int payload_type,
                                 int frame_size_samples,
                                 int frame_size_rtp_timestamps) {
    return send_test_->RegisterCodec(payload_name, sampling_freq_hz, channels,
                                     payload_type, frame_size_samples);
  }

  void RegisterExternalSendCodec(
      std::unique_ptr<AudioEncoder> external_speech_encoder,
      int payload_type) {
    send_test_->RegisterExternalCodec(std::move(external_speech_encoder));
  }

  void RunInner(int min_expected_total_bits, int max_expected_total_bits) {
    int nr_bytes = 0;
    while (std::unique_ptr<test::Packet> next_packet =
               send_test_->NextPacket()) {
      nr_bytes += rtc::checked_cast<int>(next_packet->payload_length_bytes());
    }
    EXPECT_LE(min_expected_total_bits, nr_bytes * 8);
    EXPECT_GE(max_expected_total_bits, nr_bytes * 8);
  }

  void SetUpTest(absl::string_view codec_name,
                 int codec_sample_rate_hz,
                 int channels,
                 int payload_type,
                 int codec_frame_size_samples,
                 int codec_frame_size_rtp_timestamps) {
    ASSERT_TRUE(SetUpSender());
    ASSERT_TRUE(RegisterSendCodec(codec_name, codec_sample_rate_hz, channels,
                                  payload_type, codec_frame_size_samples,
                                  codec_frame_size_rtp_timestamps));
  }

  std::unique_ptr<test::AcmSendTestOldApi> send_test_;
  std::unique_ptr<test::InputAudioFile> audio_source_;
};

class AcmSetBitRateNewApi : public AcmSetBitRateTest {
 protected:
  // Runs the test. SetUpSender() must have been called and a codec must be set
  // up before calling this method.
  void Run(int min_expected_total_bits, int max_expected_total_bits) {
    RunInner(min_expected_total_bits, max_expected_total_bits);
  }
};

TEST_F(AcmSetBitRateNewApi, OpusFromFormat_48khz_20ms_10kbps) {
  const auto config = AudioEncoderOpus::SdpToConfig(
      SdpAudioFormat("opus", 48000, 2, {{"maxaveragebitrate", "10000"}}));
  ASSERT_TRUE(SetUpSender());
  RegisterExternalSendCodec(AudioEncoderOpus::MakeAudioEncoder(*config, 107),
                            107);
  RunInner(7000, 12000);
}

TEST_F(AcmSetBitRateNewApi, OpusFromFormat_48khz_20ms_50kbps) {
  const auto config = AudioEncoderOpus::SdpToConfig(
      SdpAudioFormat("opus", 48000, 2, {{"maxaveragebitrate", "50000"}}));
  ASSERT_TRUE(SetUpSender());
  RegisterExternalSendCodec(AudioEncoderOpus::MakeAudioEncoder(*config, 107),
                            107);
  RunInner(40000, 60000);
}

// Verify that it works when the data to send is mono and the encoder is set to
// send surround audio.
TEST_F(AudioCodingModuleTestOldApi, SendingMultiChannelForMonoInput) {
  constexpr int kSampleRateHz = 48000;
  constexpr int kSamplesPerChannel = kSampleRateHz * 10 / 1000;

  audio_format_ = SdpAudioFormat({"multiopus",
                                  kSampleRateHz,
                                  6,
                                  {{"minptime", "10"},
                                   {"useinbandfec", "1"},
                                   {"channel_mapping", "0,4,1,2,3,5"},
                                   {"num_streams", "4"},
                                   {"coupled_streams", "2"}}});

  RegisterCodec();

  input_frame_.sample_rate_hz_ = kSampleRateHz;
  input_frame_.num_channels_ = 1;
  input_frame_.samples_per_channel_ = kSamplesPerChannel;
  for (size_t k = 0; k < 10; ++k) {
    ASSERT_GE(acm_->Add10MsData(input_frame_), 0);
    input_frame_.timestamp_ += kSamplesPerChannel;
  }
}

// Verify that it works when the data to send is stereo and the encoder is set
// to send surround audio.
TEST_F(AudioCodingModuleTestOldApi, SendingMultiChannelForStereoInput) {
  constexpr int kSampleRateHz = 48000;
  constexpr int kSamplesPerChannel = (kSampleRateHz * 10) / 1000;

  audio_format_ = SdpAudioFormat({"multiopus",
                                  kSampleRateHz,
                                  6,
                                  {{"minptime", "10"},
                                   {"useinbandfec", "1"},
                                   {"channel_mapping", "0,4,1,2,3,5"},
                                   {"num_streams", "4"},
                                   {"coupled_streams", "2"}}});

  RegisterCodec();

  input_frame_.sample_rate_hz_ = kSampleRateHz;
  input_frame_.num_channels_ = 2;
  input_frame_.samples_per_channel_ = kSamplesPerChannel;
  for (size_t k = 0; k < 10; ++k) {
    ASSERT_GE(acm_->Add10MsData(input_frame_), 0);
    input_frame_.timestamp_ += kSamplesPerChannel;
  }
}

// Verify that it works when the data to send is mono and the encoder is set to
// send stereo audio.
TEST_F(AudioCodingModuleTestOldApi, SendingStereoForMonoInput) {
  constexpr int kSampleRateHz = 48000;
  constexpr int kSamplesPerChannel = (kSampleRateHz * 10) / 1000;

  audio_format_ = SdpAudioFormat("L16", kSampleRateHz, 2);

  RegisterCodec();

  input_frame_.sample_rate_hz_ = kSampleRateHz;
  input_frame_.num_channels_ = 1;
  input_frame_.samples_per_channel_ = kSamplesPerChannel;
  for (size_t k = 0; k < 10; ++k) {
    ASSERT_GE(acm_->Add10MsData(input_frame_), 0);
    input_frame_.timestamp_ += kSamplesPerChannel;
  }
}

// Verify that it works when the data to send is stereo and the encoder is set
// to send mono audio.
TEST_F(AudioCodingModuleTestOldApi, SendingMonoForStereoInput) {
  constexpr int kSampleRateHz = 48000;
  constexpr int kSamplesPerChannel = (kSampleRateHz * 10) / 1000;

  audio_format_ = SdpAudioFormat("L16", kSampleRateHz, 1);

  RegisterCodec();

  input_frame_.sample_rate_hz_ = kSampleRateHz;
  input_frame_.num_channels_ = 1;
  input_frame_.samples_per_channel_ = kSamplesPerChannel;
  for (size_t k = 0; k < 10; ++k) {
    ASSERT_GE(acm_->Add10MsData(input_frame_), 0);
    input_frame_.timestamp_ += kSamplesPerChannel;
  }
}

// The result on the Android platforms is inconsistent for this test case.
// On android_rel the result is different from android and android arm64 rel.
#if defined(WEBRTC_ANDROID)
#define MAYBE_OpusFromFormat_48khz_20ms_100kbps \
  DISABLED_OpusFromFormat_48khz_20ms_100kbps
#else
#define MAYBE_OpusFromFormat_48khz_20ms_100kbps \
  OpusFromFormat_48khz_20ms_100kbps
#endif
TEST_F(AcmSetBitRateNewApi, MAYBE_OpusFromFormat_48khz_20ms_100kbps) {
  const auto config = AudioEncoderOpus::SdpToConfig(
      SdpAudioFormat("opus", 48000, 2, {{"maxaveragebitrate", "100000"}}));
  ASSERT_TRUE(SetUpSender());
  RegisterExternalSendCodec(AudioEncoderOpus::MakeAudioEncoder(*config, 107),
                            107);
  RunInner(80000, 120000);
}

TEST_F(AcmSenderBitExactnessOldApi, External_Pcmu_20ms) {
  AudioEncoderPcmU::Config config;
  config.frame_size_ms = 20;
  config.num_channels = 1;
  config.payload_type = 0;
  AudioEncoderPcmU encoder(config);
  auto mock_encoder = std::make_unique<MockAudioEncoder>();
  // Set expectations on the mock encoder and also delegate the calls to the
  // real encoder.
  EXPECT_CALL(*mock_encoder, SampleRateHz())
      .Times(AtLeast(1))
      .WillRepeatedly(Invoke(&encoder, &AudioEncoderPcmU::SampleRateHz));
  EXPECT_CALL(*mock_encoder, NumChannels())
      .Times(AtLeast(1))
      .WillRepeatedly(Invoke(&encoder, &AudioEncoderPcmU::NumChannels));
  EXPECT_CALL(*mock_encoder, RtpTimestampRateHz())
      .Times(AtLeast(1))
      .WillRepeatedly(Invoke(&encoder, &AudioEncoderPcmU::RtpTimestampRateHz));
  EXPECT_CALL(*mock_encoder, Num10MsFramesInNextPacket())
      .Times(AtLeast(1))
      .WillRepeatedly(
          Invoke(&encoder, &AudioEncoderPcmU::Num10MsFramesInNextPacket));
  EXPECT_CALL(*mock_encoder, GetTargetBitrate())
      .Times(AtLeast(1))
      .WillRepeatedly(Invoke(&encoder, &AudioEncoderPcmU::GetTargetBitrate));
  EXPECT_CALL(*mock_encoder, EncodeImpl(_, _, _))
      .Times(AtLeast(1))
      .WillRepeatedly(Invoke(
          &encoder, static_cast<AudioEncoder::EncodedInfo (AudioEncoder::*)(
                        uint32_t, rtc::ArrayView<const int16_t>, rtc::Buffer*)>(
                        &AudioEncoderPcmU::Encode)));
  ASSERT_TRUE(SetUpSender(kTestFileMono32kHz, 32000));
  ASSERT_NO_FATAL_FAILURE(
      SetUpTestExternalEncoder(std::move(mock_encoder), config.payload_type));
  Run("c8d1fc677f33c2022ec5f83c7f302280", "8f9b8750bd80fe26b6cbf6659b89f0f9",
      50, test::AcmReceiveTestOldApi::kMonoOutput);
}

// This test fixture is implemented to run ACM and change the desired output
// frequency during the call. The input packets are simply PCM16b-wb encoded
// payloads with a constant value of `kSampleValue`. The test fixture itself
// acts as PacketSource in between the receive test class and the constant-
// payload packet source class. The output is both written to file, and analyzed
// in this test fixture.
class AcmSwitchingOutputFrequencyOldApi : public ::testing::Test,
                                          public test::PacketSource,
                                          public test::AudioSink {
 protected:
  static const size_t kTestNumPackets = 50;
  static const int kEncodedSampleRateHz = 16000;
  static const size_t kPayloadLenSamples = 30 * kEncodedSampleRateHz / 1000;
  static const int kPayloadType = 108;  // Default payload type for PCM16b-wb.

  AcmSwitchingOutputFrequencyOldApi()
      : first_output_(true),
        num_packets_(0),
        packet_source_(kPayloadLenSamples,
                       kSampleValue,
                       kEncodedSampleRateHz,
                       kPayloadType),
        output_freq_2_(0),
        has_toggled_(false) {}

  void Run(int output_freq_1, int output_freq_2, int toggle_period_ms) {
    // Set up the receiver used to decode the packets and verify the decoded
    // output.
    const std::string output_file_name =
        webrtc::test::OutputPath() +
        ::testing::UnitTest::GetInstance()
            ->current_test_info()
            ->test_case_name() +
        "_" + ::testing::UnitTest::GetInstance()->current_test_info()->name() +
        "_output.pcm";
    test::OutputAudioFile output_file(output_file_name);
    // Have the output audio sent both to file and to the WriteArray method in
    // this class.
    test::AudioSinkFork output(this, &output_file);
    test::AcmReceiveTestToggleOutputFreqOldApi receive_test(
        this, &output, output_freq_1, output_freq_2, toggle_period_ms,
        test::AcmReceiveTestOldApi::kMonoOutput);
    ASSERT_NO_FATAL_FAILURE(receive_test.RegisterDefaultCodecs());
    output_freq_2_ = output_freq_2;

    // This is where the actual test is executed.
    receive_test.Run();

    // Delete output file.
    remove(output_file_name.c_str());
  }

  // Inherited from test::PacketSource.
  std::unique_ptr<test::Packet> NextPacket() override {
    // Check if it is time to terminate the test. The packet source is of type
    // ConstantPcmPacketSource, which is infinite, so we must end the test
    // "manually".
    if (num_packets_++ > kTestNumPackets) {
      EXPECT_TRUE(has_toggled_);
      return NULL;  // Test ended.
    }

    // Get the next packet from the source.
    return packet_source_.NextPacket();
  }

  // Inherited from test::AudioSink.
  bool WriteArray(const int16_t* audio, size_t num_samples) override {
    // Skip checking the first output frame, since it has a number of zeros
    // due to how NetEq is initialized.
    if (first_output_) {
      first_output_ = false;
      return true;
    }
    for (size_t i = 0; i < num_samples; ++i) {
      EXPECT_EQ(kSampleValue, audio[i]);
    }
    if (num_samples ==
        static_cast<size_t>(output_freq_2_ / 100))  // Size of 10 ms frame.
      has_toggled_ = true;
    // The return value does not say if the values match the expectation, just
    // that the method could process the samples.
    return true;
  }

  const int16_t kSampleValue = 1000;
  bool first_output_;
  size_t num_packets_;
  test::ConstantPcmPacketSource packet_source_;
  int output_freq_2_;
  bool has_toggled_;
};

TEST_F(AcmSwitchingOutputFrequencyOldApi, TestWithoutToggling) {
  Run(16000, 16000, 1000);
}

TEST_F(AcmSwitchingOutputFrequencyOldApi, Toggle16KhzTo32Khz) {
  Run(16000, 32000, 1000);
}

TEST_F(AcmSwitchingOutputFrequencyOldApi, Toggle32KhzTo16Khz) {
  Run(32000, 16000, 1000);
}

TEST_F(AcmSwitchingOutputFrequencyOldApi, Toggle16KhzTo8Khz) {
  Run(16000, 8000, 1000);
}

TEST_F(AcmSwitchingOutputFrequencyOldApi, Toggle8KhzTo16Khz) {
  Run(8000, 16000, 1000);
}

#endif

}  // namespace webrtc
