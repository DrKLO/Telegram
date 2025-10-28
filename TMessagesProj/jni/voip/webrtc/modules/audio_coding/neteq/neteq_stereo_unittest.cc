/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// Test to verify correct stereo and multi-channel operation.

#include <algorithm>
#include <list>
#include <memory>
#include <string>

#include "api/audio/audio_frame.h"
#include "api/audio_codecs/builtin_audio_decoder_factory.h"
#include "api/neteq/neteq.h"
#include "modules/audio_coding/codecs/pcm16b/pcm16b.h"
#include "modules/audio_coding/neteq/default_neteq_factory.h"
#include "modules/audio_coding/neteq/tools/input_audio_file.h"
#include "modules/audio_coding/neteq/tools/rtp_generator.h"
#include "rtc_base/strings/string_builder.h"
#include "system_wrappers/include/clock.h"
#include "test/gtest.h"
#include "test/testsupport/file_utils.h"

namespace webrtc {

struct TestParameters {
  int frame_size;
  int sample_rate;
  size_t num_channels;
};

// This is a parameterized test. The test parameters are supplied through a
// TestParameters struct, which is obtained through the GetParam() method.
//
// The objective of the test is to create a mono input signal and a
// multi-channel input signal, where each channel is identical to the mono
// input channel. The two input signals are processed through their respective
// NetEq instances. After that, the output signals are compared. The expected
// result is that each channel in the multi-channel output is identical to the
// mono output.
class NetEqStereoTest : public ::testing::TestWithParam<TestParameters> {
 protected:
  static const int kTimeStepMs = 10;
  static const size_t kMaxBlockSize = 480;  // 10 ms @ 48 kHz.
  static const uint8_t kPayloadTypeMono = 95;
  static const uint8_t kPayloadTypeMulti = 96;

  NetEqStereoTest()
      : num_channels_(GetParam().num_channels),
        sample_rate_hz_(GetParam().sample_rate),
        samples_per_ms_(sample_rate_hz_ / 1000),
        frame_size_ms_(GetParam().frame_size),
        frame_size_samples_(
            static_cast<size_t>(frame_size_ms_ * samples_per_ms_)),
        output_size_samples_(10 * samples_per_ms_),
        clock_(0),
        rtp_generator_mono_(samples_per_ms_),
        rtp_generator_(samples_per_ms_),
        payload_size_bytes_(0),
        multi_payload_size_bytes_(0),
        last_send_time_(0),
        last_arrival_time_(0) {
    NetEq::Config config;
    config.sample_rate_hz = sample_rate_hz_;
    DefaultNetEqFactory neteq_factory;
    auto decoder_factory = CreateBuiltinAudioDecoderFactory();
    neteq_mono_ = neteq_factory.CreateNetEq(config, decoder_factory, &clock_);
    neteq_ = neteq_factory.CreateNetEq(config, decoder_factory, &clock_);
    input_ = new int16_t[frame_size_samples_];
    encoded_ = new uint8_t[2 * frame_size_samples_];
    input_multi_channel_ = new int16_t[frame_size_samples_ * num_channels_];
    encoded_multi_channel_ =
        new uint8_t[frame_size_samples_ * 2 * num_channels_];
  }

  ~NetEqStereoTest() {
    delete[] input_;
    delete[] encoded_;
    delete[] input_multi_channel_;
    delete[] encoded_multi_channel_;
  }

  virtual void SetUp() {
    const std::string file_name =
        webrtc::test::ResourcePath("audio_coding/testfile32kHz", "pcm");
    input_file_.reset(new test::InputAudioFile(file_name));
    RTC_CHECK_GE(num_channels_, 2);
    ASSERT_TRUE(neteq_mono_->RegisterPayloadType(
        kPayloadTypeMono, SdpAudioFormat("l16", sample_rate_hz_, 1)));
    ASSERT_TRUE(neteq_->RegisterPayloadType(
        kPayloadTypeMulti,
        SdpAudioFormat("l16", sample_rate_hz_, num_channels_)));
  }

  virtual void TearDown() {}

  int GetNewPackets() {
    if (!input_file_->Read(frame_size_samples_, input_)) {
      return -1;
    }
    payload_size_bytes_ =
        WebRtcPcm16b_Encode(input_, frame_size_samples_, encoded_);
    if (frame_size_samples_ * 2 != payload_size_bytes_) {
      return -1;
    }
    int next_send_time = rtp_generator_mono_.GetRtpHeader(
        kPayloadTypeMono, frame_size_samples_, &rtp_header_mono_);
    MakeMultiChannelInput();
    multi_payload_size_bytes_ = WebRtcPcm16b_Encode(
        input_multi_channel_, frame_size_samples_ * num_channels_,
        encoded_multi_channel_);
    if (frame_size_samples_ * 2 * num_channels_ != multi_payload_size_bytes_) {
      return -1;
    }
    rtp_generator_.GetRtpHeader(kPayloadTypeMulti, frame_size_samples_,
                                &rtp_header_);
    return next_send_time;
  }

  virtual void MakeMultiChannelInput() {
    test::InputAudioFile::DuplicateInterleaved(
        input_, frame_size_samples_, num_channels_, input_multi_channel_);
  }

  virtual void VerifyOutput(size_t num_samples) {
    const int16_t* output_data = output_.data();
    const int16_t* output_multi_channel_data = output_multi_channel_.data();
    for (size_t i = 0; i < num_samples; ++i) {
      for (size_t j = 0; j < num_channels_; ++j) {
        ASSERT_EQ(output_data[i],
                  output_multi_channel_data[i * num_channels_ + j])
            << "Diff in sample " << i << ", channel " << j << ".";
      }
    }
  }

  virtual int GetArrivalTime(int send_time) {
    int arrival_time = last_arrival_time_ + (send_time - last_send_time_);
    last_send_time_ = send_time;
    last_arrival_time_ = arrival_time;
    return arrival_time;
  }

  virtual bool Lost() { return false; }

  void RunTest(int num_loops) {
    // Get next input packets (mono and multi-channel).
    int next_send_time;
    int next_arrival_time;
    do {
      next_send_time = GetNewPackets();
      ASSERT_NE(-1, next_send_time);
      next_arrival_time = GetArrivalTime(next_send_time);
    } while (Lost());  // If lost, immediately read the next packet.

    int time_now = 0;
    for (int k = 0; k < num_loops; ++k) {
      while (time_now >= next_arrival_time) {
        // Insert packet in mono instance.
        ASSERT_EQ(NetEq::kOK,
                  neteq_mono_->InsertPacket(
                      rtp_header_mono_, rtc::ArrayView<const uint8_t>(
                                            encoded_, payload_size_bytes_)));
        // Insert packet in multi-channel instance.
        ASSERT_EQ(NetEq::kOK, neteq_->InsertPacket(
                                  rtp_header_, rtc::ArrayView<const uint8_t>(
                                                   encoded_multi_channel_,
                                                   multi_payload_size_bytes_)));
        // Get next input packets (mono and multi-channel).
        do {
          next_send_time = GetNewPackets();
          ASSERT_NE(-1, next_send_time);
          next_arrival_time = GetArrivalTime(next_send_time);
        } while (Lost());  // If lost, immediately read the next packet.
      }
      // Get audio from mono instance.
      bool muted;
      EXPECT_EQ(NetEq::kOK, neteq_mono_->GetAudio(&output_, &muted));
      ASSERT_FALSE(muted);
      EXPECT_EQ(1u, output_.num_channels_);
      EXPECT_EQ(output_size_samples_, output_.samples_per_channel_);
      // Get audio from multi-channel instance.
      ASSERT_EQ(NetEq::kOK, neteq_->GetAudio(&output_multi_channel_, &muted));
      ASSERT_FALSE(muted);
      EXPECT_EQ(num_channels_, output_multi_channel_.num_channels_);
      EXPECT_EQ(output_size_samples_,
                output_multi_channel_.samples_per_channel_);
      rtc::StringBuilder ss;
      ss << "Lap number " << k << ".";
      SCOPED_TRACE(ss.str());  // Print out the parameter values on failure.
      // Compare mono and multi-channel.
      ASSERT_NO_FATAL_FAILURE(VerifyOutput(output_size_samples_));

      time_now += kTimeStepMs;
      clock_.AdvanceTimeMilliseconds(kTimeStepMs);
    }
  }

  const size_t num_channels_;
  const int sample_rate_hz_;
  const int samples_per_ms_;
  const int frame_size_ms_;
  const size_t frame_size_samples_;
  const size_t output_size_samples_;
  SimulatedClock clock_;
  std::unique_ptr<NetEq> neteq_mono_;
  std::unique_ptr<NetEq> neteq_;
  test::RtpGenerator rtp_generator_mono_;
  test::RtpGenerator rtp_generator_;
  int16_t* input_;
  int16_t* input_multi_channel_;
  uint8_t* encoded_;
  uint8_t* encoded_multi_channel_;
  AudioFrame output_;
  AudioFrame output_multi_channel_;
  RTPHeader rtp_header_mono_;
  RTPHeader rtp_header_;
  size_t payload_size_bytes_;
  size_t multi_payload_size_bytes_;
  int last_send_time_;
  int last_arrival_time_;
  std::unique_ptr<test::InputAudioFile> input_file_;
};

class NetEqStereoTestNoJitter : public NetEqStereoTest {
 protected:
  NetEqStereoTestNoJitter() : NetEqStereoTest() {
    // Start the sender 100 ms before the receiver to pre-fill the buffer.
    // This is to avoid doing preemptive expand early in the test.
    // TODO(hlundin): Mock the decision making instead to control the modes.
    last_arrival_time_ = -100;
  }
};

TEST_P(NetEqStereoTestNoJitter, RunTest) {
  RunTest(8);
}

class NetEqStereoTestPositiveDrift : public NetEqStereoTest {
 protected:
  NetEqStereoTestPositiveDrift() : NetEqStereoTest(), drift_factor(0.9) {
    // Start the sender 100 ms before the receiver to pre-fill the buffer.
    // This is to avoid doing preemptive expand early in the test.
    // TODO(hlundin): Mock the decision making instead to control the modes.
    last_arrival_time_ = -100;
  }
  virtual int GetArrivalTime(int send_time) {
    int arrival_time =
        last_arrival_time_ + drift_factor * (send_time - last_send_time_);
    last_send_time_ = send_time;
    last_arrival_time_ = arrival_time;
    return arrival_time;
  }

  double drift_factor;
};

TEST_P(NetEqStereoTestPositiveDrift, RunTest) {
  RunTest(100);
}

class NetEqStereoTestNegativeDrift : public NetEqStereoTestPositiveDrift {
 protected:
  NetEqStereoTestNegativeDrift() : NetEqStereoTestPositiveDrift() {
    drift_factor = 1.1;
    last_arrival_time_ = 0;
  }
};

TEST_P(NetEqStereoTestNegativeDrift, RunTest) {
  RunTest(100);
}

class NetEqStereoTestDelays : public NetEqStereoTest {
 protected:
  static const int kDelayInterval = 10;
  static const int kDelay = 1000;
  NetEqStereoTestDelays() : NetEqStereoTest(), frame_index_(0) {}

  virtual int GetArrivalTime(int send_time) {
    // Deliver immediately, unless we have a back-log.
    int arrival_time = std::min(last_arrival_time_, send_time);
    if (++frame_index_ % kDelayInterval == 0) {
      // Delay this packet.
      arrival_time += kDelay;
    }
    last_send_time_ = send_time;
    last_arrival_time_ = arrival_time;
    return arrival_time;
  }

  int frame_index_;
};

TEST_P(NetEqStereoTestDelays, RunTest) {
  RunTest(1000);
}

class NetEqStereoTestLosses : public NetEqStereoTest {
 protected:
  static const int kLossInterval = 10;
  NetEqStereoTestLosses() : NetEqStereoTest(), frame_index_(0) {}

  virtual bool Lost() { return (++frame_index_) % kLossInterval == 0; }

  // TODO(hlundin): NetEq is not giving bitexact results for these cases.
  virtual void VerifyOutput(size_t num_samples) {
    for (size_t i = 0; i < num_samples; ++i) {
      const int16_t* output_data = output_.data();
      const int16_t* output_multi_channel_data = output_multi_channel_.data();
      auto first_channel_sample = output_multi_channel_data[i * num_channels_];
      for (size_t j = 0; j < num_channels_; ++j) {
        const int kErrorMargin = 200;
        EXPECT_NEAR(output_data[i],
                    output_multi_channel_data[i * num_channels_ + j],
                    kErrorMargin)
            << "Diff in sample " << i << ", channel " << j << ".";
        EXPECT_EQ(first_channel_sample,
                  output_multi_channel_data[i * num_channels_ + j]);
      }
    }
  }

  int frame_index_;
};

TEST_P(NetEqStereoTestLosses, RunTest) {
  RunTest(100);
}

class NetEqStereoTestSingleActiveChannelPlc : public NetEqStereoTestLosses {
 protected:
  NetEqStereoTestSingleActiveChannelPlc() : NetEqStereoTestLosses() {}

  virtual void MakeMultiChannelInput() override {
    // Create a multi-channel input by copying the mono channel from file to the
    // first channel, and setting the others to zero.
    memset(input_multi_channel_, 0,
           frame_size_samples_ * num_channels_ * sizeof(int16_t));
    for (size_t i = 0; i < frame_size_samples_; ++i) {
      input_multi_channel_[i * num_channels_] = input_[i];
    }
  }

  virtual void VerifyOutput(size_t num_samples) override {
    // Simply verify that all samples in channels other than the first are zero.
    const int16_t* output_multi_channel_data = output_multi_channel_.data();
    for (size_t i = 0; i < num_samples; ++i) {
      for (size_t j = 1; j < num_channels_; ++j) {
        EXPECT_EQ(0, output_multi_channel_data[i * num_channels_ + j])
            << "Sample " << i << ", channel " << j << " is non-zero.";
      }
    }
  }
};

TEST_P(NetEqStereoTestSingleActiveChannelPlc, RunTest) {
  RunTest(100);
}

// Creates a list of parameter sets.
std::list<TestParameters> GetTestParameters() {
  std::list<TestParameters> l;
  const int sample_rates[] = {8000, 16000, 32000};
  const int num_rates = sizeof(sample_rates) / sizeof(sample_rates[0]);
  // Loop through sample rates.
  for (int rate_index = 0; rate_index < num_rates; ++rate_index) {
    int sample_rate = sample_rates[rate_index];
    // Loop through all frame sizes between 10 and 60 ms.
    for (int frame_size = 10; frame_size <= 60; frame_size += 10) {
      TestParameters p;
      p.frame_size = frame_size;
      p.sample_rate = sample_rate;
      p.num_channels = 2;
      l.push_back(p);
      if (sample_rate == 8000) {
        // Add a five-channel test for 8000 Hz.
        p.num_channels = 5;
        l.push_back(p);
      }
    }
  }
  return l;
}

// Pretty-printing the test parameters in case of an error.
void PrintTo(const TestParameters& p, ::std::ostream* os) {
  *os << "{frame_size = " << p.frame_size
      << ", num_channels = " << p.num_channels
      << ", sample_rate = " << p.sample_rate << "}";
}

// Instantiate the tests. Each test is instantiated using the function above,
// so that all different parameter combinations are tested.
INSTANTIATE_TEST_SUITE_P(MultiChannel,
                         NetEqStereoTestNoJitter,
                         ::testing::ValuesIn(GetTestParameters()));

INSTANTIATE_TEST_SUITE_P(MultiChannel,
                         NetEqStereoTestPositiveDrift,
                         ::testing::ValuesIn(GetTestParameters()));

INSTANTIATE_TEST_SUITE_P(MultiChannel,
                         NetEqStereoTestNegativeDrift,
                         ::testing::ValuesIn(GetTestParameters()));

INSTANTIATE_TEST_SUITE_P(MultiChannel,
                         NetEqStereoTestDelays,
                         ::testing::ValuesIn(GetTestParameters()));

INSTANTIATE_TEST_SUITE_P(MultiChannel,
                         NetEqStereoTestLosses,
                         ::testing::ValuesIn(GetTestParameters()));

INSTANTIATE_TEST_SUITE_P(MultiChannel,
                         NetEqStereoTestSingleActiveChannelPlc,
                         ::testing::ValuesIn(GetTestParameters()));
}  // namespace webrtc
