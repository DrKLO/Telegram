/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/neteq/tools/neteq_quality_test.h"

#include <stdio.h>

#include <cmath>

#include "absl/flags/flag.h"
#include "absl/strings/string_view.h"
#include "modules/audio_coding/neteq/default_neteq_factory.h"
#include "modules/audio_coding/neteq/tools/neteq_quality_test.h"
#include "modules/audio_coding/neteq/tools/output_audio_file.h"
#include "modules/audio_coding/neteq/tools/output_wav_file.h"
#include "modules/audio_coding/neteq/tools/resample_input_audio_file.h"
#include "rtc_base/checks.h"
#include "rtc_base/string_encode.h"
#include "system_wrappers/include/clock.h"
#include "test/testsupport/file_utils.h"

ABSL_FLAG(std::string,
          in_filename,
          "audio_coding/speech_mono_16kHz.pcm",
          "Path of the input file (relative to the resources/ directory) for "
          "input audio (specify sample rate with --input_sample_rate, "
          "and channels with --channels).");

ABSL_FLAG(int, input_sample_rate, 16000, "Sample rate of input file in Hz.");

ABSL_FLAG(int, channels, 1, "Number of channels in input audio.");

ABSL_FLAG(std::string,
          out_filename,
          "neteq_quality_test_out.pcm",
          "Name of output audio file, which will be saved in " +
              ::webrtc::test::OutputPath());

ABSL_FLAG(
    int,
    runtime_ms,
    10000,
    "Simulated runtime (milliseconds). -1 will consume the complete file.");

ABSL_FLAG(int, packet_loss_rate, 10, "Percentile of packet loss.");

ABSL_FLAG(int,
          random_loss_mode,
          ::webrtc::test::kUniformLoss,
          "Random loss mode: 0--no loss, 1--uniform loss, 2--Gilbert Elliot "
          "loss, 3--fixed loss.");

ABSL_FLAG(int,
          burst_length,
          30,
          "Burst length in milliseconds, only valid for Gilbert Elliot loss.");

ABSL_FLAG(float, drift_factor, 0.0, "Time drift factor.");

ABSL_FLAG(int,
          preload_packets,
          1,
          "Preload the buffer with this many packets.");

ABSL_FLAG(std::string,
          loss_events,
          "",
          "List of loss events time and duration separated by comma: "
          "<first_event_time> <first_event_duration>, <second_event_time> "
          "<second_event_duration>, ...");

namespace webrtc {
namespace test {

namespace {

std::unique_ptr<NetEq> CreateNetEq(
    const NetEq::Config& config,
    Clock* clock,
    const rtc::scoped_refptr<AudioDecoderFactory>& decoder_factory) {
  return DefaultNetEqFactory().CreateNetEq(config, decoder_factory, clock);
}

const std::string& GetInFilenamePath(absl::string_view file_name) {
  std::vector<absl::string_view> name_parts = rtc::split(file_name, '.');
  RTC_CHECK_EQ(name_parts.size(), 2);
  static const std::string path =
      ::webrtc::test::ResourcePath(name_parts[0], name_parts[1]);
  return path;
}

const std::string& GetOutFilenamePath(absl::string_view file_name) {
  static const std::string path =
      ::webrtc::test::OutputPath() + std::string(file_name);
  return path;
}

}  // namespace

const uint8_t kPayloadType = 95;
const int kOutputSizeMs = 10;
const int kInitSeed = 0x12345678;
const int kPacketLossTimeUnitMs = 10;

// Common validator for file names.
static bool ValidateFilename(absl::string_view value, bool is_output) {
  if (!is_output) {
    RTC_CHECK_NE(value.substr(value.find_last_of('.') + 1), "wav")
        << "WAV file input is not supported";
  }
  FILE* fid = is_output ? fopen(std::string(value).c_str(), "wb")
                        : fopen(std::string(value).c_str(), "rb");
  if (fid == nullptr)
    return false;
  fclose(fid);
  return true;
}

// ProbTrans00Solver() is to calculate the transition probability from no-loss
// state to itself in a modified Gilbert Elliot packet loss model. The result is
// to achieve the target packet loss rate `loss_rate`, when a packet is not
// lost only if all `units` drawings within the duration of the packet result in
// no-loss.
static double ProbTrans00Solver(int units,
                                double loss_rate,
                                double prob_trans_10) {
  if (units == 1)
    return prob_trans_10 / (1.0f - loss_rate) - prob_trans_10;
  // 0 == prob_trans_00 ^ (units - 1) + (1 - loss_rate) / prob_trans_10 *
  //     prob_trans_00 - (1 - loss_rate) * (1 + 1 / prob_trans_10).
  // There is a unique solution between 0.0 and 1.0, due to the monotonicity and
  // an opposite sign at 0.0 and 1.0.
  // For simplicity, we reformulate the equation as
  //     f(x) = x ^ (units - 1) + a x + b.
  // Its derivative is
  //     f'(x) = (units - 1) x ^ (units - 2) + a.
  // The derivative is strictly greater than 0 when x is between 0 and 1.
  // We use Newton's method to solve the equation, iteration is
  //     x(k+1) = x(k) - f(x) / f'(x);
  const double kPrecision = 0.001f;
  const int kIterations = 100;
  const double a = (1.0f - loss_rate) / prob_trans_10;
  const double b = (loss_rate - 1.0f) * (1.0f + 1.0f / prob_trans_10);
  double x = 0.0;  // Starting point;
  double f = b;
  double f_p;
  int iter = 0;
  while ((f >= kPrecision || f <= -kPrecision) && iter < kIterations) {
    f_p = (units - 1.0f) * std::pow(x, units - 2) + a;
    x -= f / f_p;
    if (x > 1.0f) {
      x = 1.0f;
    } else if (x < 0.0f) {
      x = 0.0f;
    }
    f = std::pow(x, units - 1) + a * x + b;
    iter++;
  }
  return x;
}

NetEqQualityTest::NetEqQualityTest(
    int block_duration_ms,
    int in_sampling_khz,
    int out_sampling_khz,
    const SdpAudioFormat& format,
    const rtc::scoped_refptr<AudioDecoderFactory>& decoder_factory)
    : audio_format_(format),
      channels_(absl::GetFlag(FLAGS_channels)),
      decoded_time_ms_(0),
      decodable_time_ms_(0),
      drift_factor_(absl::GetFlag(FLAGS_drift_factor)),
      packet_loss_rate_(absl::GetFlag(FLAGS_packet_loss_rate)),
      block_duration_ms_(block_duration_ms),
      in_sampling_khz_(in_sampling_khz),
      out_sampling_khz_(out_sampling_khz),
      in_size_samples_(
          static_cast<size_t>(in_sampling_khz_ * block_duration_ms_)),
      payload_size_bytes_(0),
      max_payload_bytes_(0),
      in_file_(new ResampleInputAudioFile(
          GetInFilenamePath(absl::GetFlag(FLAGS_in_filename)),
          absl::GetFlag(FLAGS_input_sample_rate),
          in_sampling_khz * 1000,
          absl::GetFlag(FLAGS_runtime_ms) > 0)),
      rtp_generator_(
          new RtpGenerator(in_sampling_khz_, 0, 0, decodable_time_ms_)),
      total_payload_size_bytes_(0) {
  // Flag validation
  RTC_CHECK(ValidateFilename(
      GetInFilenamePath(absl::GetFlag(FLAGS_in_filename)), false))
      << "Invalid input filename.";

  RTC_CHECK(absl::GetFlag(FLAGS_input_sample_rate) == 8000 ||
            absl::GetFlag(FLAGS_input_sample_rate) == 16000 ||
            absl::GetFlag(FLAGS_input_sample_rate) == 32000 ||
            absl::GetFlag(FLAGS_input_sample_rate) == 48000)
      << "Invalid sample rate should be 8000, 16000, 32000 or 48000 Hz.";

  RTC_CHECK_EQ(absl::GetFlag(FLAGS_channels), 1)
      << "Invalid number of channels, current support only 1.";

  RTC_CHECK(ValidateFilename(
      GetOutFilenamePath(absl::GetFlag(FLAGS_out_filename)), true))
      << "Invalid output filename.";

  RTC_CHECK(absl::GetFlag(FLAGS_packet_loss_rate) >= 0 &&
            absl::GetFlag(FLAGS_packet_loss_rate) <= 100)
      << "Invalid packet loss percentile, should be between 0 and 100.";

  RTC_CHECK(absl::GetFlag(FLAGS_random_loss_mode) >= 0 &&
            absl::GetFlag(FLAGS_random_loss_mode) < kLastLossMode)
      << "Invalid random packet loss mode, should be between 0 and "
      << kLastLossMode - 1 << ".";

  RTC_CHECK_GE(absl::GetFlag(FLAGS_burst_length), kPacketLossTimeUnitMs)
      << "Invalid burst length, should be greater than or equal to "
      << kPacketLossTimeUnitMs << " ms.";

  RTC_CHECK_GT(absl::GetFlag(FLAGS_drift_factor), -0.1)
      << "Invalid drift factor, should be greater than -0.1.";

  RTC_CHECK_GE(absl::GetFlag(FLAGS_preload_packets), 0)
      << "Invalid number of packets to preload; must be non-negative.";

  const std::string out_filename =
      GetOutFilenamePath(absl::GetFlag(FLAGS_out_filename));
  const std::string log_filename = out_filename + ".log";
  log_file_.open(log_filename.c_str(), std::ofstream::out);
  RTC_CHECK(log_file_.is_open());

  if (out_filename.size() >= 4 &&
      out_filename.substr(out_filename.size() - 4) == ".wav") {
    // Open a wav file.
    output_.reset(
        new webrtc::test::OutputWavFile(out_filename, 1000 * out_sampling_khz));
  } else {
    // Open a pcm file.
    output_.reset(new webrtc::test::OutputAudioFile(out_filename));
  }

  NetEq::Config config;
  config.sample_rate_hz = out_sampling_khz_ * 1000;
  neteq_ = CreateNetEq(config, Clock::GetRealTimeClock(), decoder_factory);
  max_payload_bytes_ = in_size_samples_ * channels_ * sizeof(int16_t);
  in_data_.reset(new int16_t[in_size_samples_ * channels_]);
}

NetEqQualityTest::~NetEqQualityTest() {
  log_file_.close();
}

bool NoLoss::Lost(int now_ms) {
  return false;
}

UniformLoss::UniformLoss(double loss_rate) : loss_rate_(loss_rate) {}

bool UniformLoss::Lost(int now_ms) {
  int drop_this = rand();
  return (drop_this < loss_rate_ * RAND_MAX);
}

GilbertElliotLoss::GilbertElliotLoss(double prob_trans_11, double prob_trans_01)
    : prob_trans_11_(prob_trans_11),
      prob_trans_01_(prob_trans_01),
      lost_last_(false),
      uniform_loss_model_(new UniformLoss(0)) {}

GilbertElliotLoss::~GilbertElliotLoss() {}

bool GilbertElliotLoss::Lost(int now_ms) {
  // Simulate bursty channel (Gilbert model).
  // (1st order) Markov chain model with memory of the previous/last
  // packet state (lost or received).
  if (lost_last_) {
    // Previous packet was not received.
    uniform_loss_model_->set_loss_rate(prob_trans_11_);
    return lost_last_ = uniform_loss_model_->Lost(now_ms);
  } else {
    uniform_loss_model_->set_loss_rate(prob_trans_01_);
    return lost_last_ = uniform_loss_model_->Lost(now_ms);
  }
}

FixedLossModel::FixedLossModel(
    std::set<FixedLossEvent, FixedLossEventCmp> loss_events)
    : loss_events_(loss_events) {
  loss_events_it_ = loss_events_.begin();
}

FixedLossModel::~FixedLossModel() {}

bool FixedLossModel::Lost(int now_ms) {
  if (loss_events_it_ != loss_events_.end() &&
      now_ms > loss_events_it_->start_ms) {
    if (now_ms <= loss_events_it_->start_ms + loss_events_it_->duration_ms) {
      return true;
    } else {
      ++loss_events_it_;
      return false;
    }
  }
  return false;
}

void NetEqQualityTest::SetUp() {
  ASSERT_TRUE(neteq_->RegisterPayloadType(kPayloadType, audio_format_));
  rtp_generator_->set_drift_factor(drift_factor_);

  int units = block_duration_ms_ / kPacketLossTimeUnitMs;
  switch (absl::GetFlag(FLAGS_random_loss_mode)) {
    case kUniformLoss: {
      // `unit_loss_rate` is the packet loss rate for each unit time interval
      // (kPacketLossTimeUnitMs). Since a packet loss event is generated if any
      // of |block_duration_ms_ / kPacketLossTimeUnitMs| unit time intervals of
      // a full packet duration is drawn with a loss, `unit_loss_rate` fulfills
      // (1 - unit_loss_rate) ^ (block_duration_ms_ / kPacketLossTimeUnitMs) ==
      // 1 - packet_loss_rate.
      double unit_loss_rate =
          (1.0 - std::pow(1.0 - 0.01 * packet_loss_rate_, 1.0 / units));
      loss_model_.reset(new UniformLoss(unit_loss_rate));
      break;
    }
    case kGilbertElliotLoss: {
      // `FLAGS_burst_length` should be integer times of kPacketLossTimeUnitMs.
      ASSERT_EQ(0, absl::GetFlag(FLAGS_burst_length) % kPacketLossTimeUnitMs);

      // We do not allow 100 percent packet loss in Gilbert Elliot model, which
      // makes no sense.
      ASSERT_GT(100, packet_loss_rate_);

      // To guarantee the overall packet loss rate, transition probabilities
      // need to satisfy:
      // pi_0 * (1 - prob_trans_01_) ^ units +
      //     pi_1 * prob_trans_10_ ^ (units - 1) == 1 - loss_rate
      // pi_0 = prob_trans_10 / (prob_trans_10 + prob_trans_01_)
      //     is the stationary state probability of no-loss
      // pi_1 = prob_trans_01_ / (prob_trans_10 + prob_trans_01_)
      //     is the stationary state probability of loss
      // After a derivation prob_trans_00 should satisfy:
      // prob_trans_00 ^ (units - 1) = (loss_rate - 1) / prob_trans_10 *
      //     prob_trans_00 + (1 - loss_rate) * (1 + 1 / prob_trans_10).
      double loss_rate = 0.01f * packet_loss_rate_;
      double prob_trans_10 =
          1.0f * kPacketLossTimeUnitMs / absl::GetFlag(FLAGS_burst_length);
      double prob_trans_00 = ProbTrans00Solver(units, loss_rate, prob_trans_10);
      loss_model_.reset(
          new GilbertElliotLoss(1.0f - prob_trans_10, 1.0f - prob_trans_00));
      break;
    }
    case kFixedLoss: {
      std::istringstream loss_events_stream(absl::GetFlag(FLAGS_loss_events));
      std::string loss_event_string;
      std::set<FixedLossEvent, FixedLossEventCmp> loss_events;
      while (std::getline(loss_events_stream, loss_event_string, ',')) {
        std::vector<int> loss_event_params;
        std::istringstream loss_event_params_stream(loss_event_string);
        std::copy(std::istream_iterator<int>(loss_event_params_stream),
                  std::istream_iterator<int>(),
                  std::back_inserter(loss_event_params));
        RTC_CHECK_EQ(loss_event_params.size(), 2);
        auto result = loss_events.insert(
            FixedLossEvent(loss_event_params[0], loss_event_params[1]));
        RTC_CHECK(result.second);
      }
      RTC_CHECK_GT(loss_events.size(), 0);
      loss_model_.reset(new FixedLossModel(loss_events));
      break;
    }
    default: {
      loss_model_.reset(new NoLoss);
      break;
    }
  }

  // Make sure that the packet loss profile is same for all derived tests.
  srand(kInitSeed);
}

std::ofstream& NetEqQualityTest::Log() {
  return log_file_;
}

bool NetEqQualityTest::PacketLost() {
  int cycles = block_duration_ms_ / kPacketLossTimeUnitMs;

  // The loop is to make sure that codecs with different block lengths share the
  // same packet loss profile.
  bool lost = false;
  for (int idx = 0; idx < cycles; idx++) {
    if (loss_model_->Lost(decoded_time_ms_)) {
      // The packet will be lost if any of the drawings indicates a loss, but
      // the loop has to go on to make sure that codecs with different block
      // lengths keep the same pace.
      lost = true;
    }
  }
  return lost;
}

int NetEqQualityTest::Transmit() {
  int packet_input_time_ms = rtp_generator_->GetRtpHeader(
      kPayloadType, in_size_samples_, &rtp_header_);
  Log() << "Packet of size " << payload_size_bytes_ << " bytes, for frame at "
        << packet_input_time_ms << " ms ";
  if (payload_size_bytes_ > 0) {
    if (!PacketLost()) {
      int ret = neteq_->InsertPacket(
          rtp_header_,
          rtc::ArrayView<const uint8_t>(payload_.data(), payload_size_bytes_));
      if (ret != NetEq::kOK)
        return -1;
      Log() << "was sent.";
    } else {
      Log() << "was lost.";
    }
  }
  Log() << std::endl;
  return packet_input_time_ms;
}

int NetEqQualityTest::DecodeBlock() {
  bool muted;
  int ret = neteq_->GetAudio(&out_frame_, &muted);
  RTC_CHECK(!muted);

  if (ret != NetEq::kOK) {
    return -1;
  } else {
    RTC_DCHECK_EQ(out_frame_.num_channels_, channels_);
    RTC_DCHECK_EQ(out_frame_.samples_per_channel_,
                  static_cast<size_t>(kOutputSizeMs * out_sampling_khz_));
    RTC_CHECK(output_->WriteArray(
        out_frame_.data(),
        out_frame_.samples_per_channel_ * out_frame_.num_channels_));
    return static_cast<int>(out_frame_.samples_per_channel_);
  }
}

void NetEqQualityTest::Simulate() {
  int audio_size_samples;
  bool end_of_input = false;
  int runtime_ms = absl::GetFlag(FLAGS_runtime_ms) >= 0
                       ? absl::GetFlag(FLAGS_runtime_ms)
                       : INT_MAX;

  while (!end_of_input && decoded_time_ms_ < runtime_ms) {
    // Preload the buffer if needed.
    while (decodable_time_ms_ -
               absl::GetFlag(FLAGS_preload_packets) * block_duration_ms_ <
           decoded_time_ms_) {
      if (!in_file_->Read(in_size_samples_ * channels_, &in_data_[0])) {
        end_of_input = true;
        ASSERT_TRUE(end_of_input && absl::GetFlag(FLAGS_runtime_ms) < 0);
        break;
      }
      payload_.Clear();
      payload_size_bytes_ = EncodeBlock(&in_data_[0], in_size_samples_,
                                        &payload_, max_payload_bytes_);
      total_payload_size_bytes_ += payload_size_bytes_;
      decodable_time_ms_ = Transmit() + block_duration_ms_;
    }
    audio_size_samples = DecodeBlock();
    if (audio_size_samples > 0) {
      decoded_time_ms_ += audio_size_samples / out_sampling_khz_;
    }
  }
  Log() << "Average bit rate was "
        << 8.0f * total_payload_size_bytes_ / absl::GetFlag(FLAGS_runtime_ms)
        << " kbps" << std::endl;
}

}  // namespace test
}  // namespace webrtc
