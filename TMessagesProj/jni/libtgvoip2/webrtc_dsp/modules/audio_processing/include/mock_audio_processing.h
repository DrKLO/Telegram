/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_INCLUDE_MOCK_AUDIO_PROCESSING_H_
#define MODULES_AUDIO_PROCESSING_INCLUDE_MOCK_AUDIO_PROCESSING_H_

#include <memory>

#include "modules/audio_processing/include/aec_dump.h"
#include "modules/audio_processing/include/audio_processing.h"
#include "modules/audio_processing/include/audio_processing_statistics.h"
#include "test/gmock.h"

namespace webrtc {

namespace test {
class MockGainControl : public GainControl {
 public:
  virtual ~MockGainControl() {}
  MOCK_METHOD1(Enable, int(bool enable));
  MOCK_CONST_METHOD0(is_enabled, bool());
  MOCK_METHOD1(set_stream_analog_level, int(int level));
  MOCK_METHOD0(stream_analog_level, int());
  MOCK_METHOD1(set_mode, int(Mode mode));
  MOCK_CONST_METHOD0(mode, Mode());
  MOCK_METHOD1(set_target_level_dbfs, int(int level));
  MOCK_CONST_METHOD0(target_level_dbfs, int());
  MOCK_METHOD1(set_compression_gain_db, int(int gain));
  MOCK_CONST_METHOD0(compression_gain_db, int());
  MOCK_METHOD1(enable_limiter, int(bool enable));
  MOCK_CONST_METHOD0(is_limiter_enabled, bool());
  MOCK_METHOD2(set_analog_level_limits, int(int minimum, int maximum));
  MOCK_CONST_METHOD0(analog_level_minimum, int());
  MOCK_CONST_METHOD0(analog_level_maximum, int());
  MOCK_CONST_METHOD0(stream_is_saturated, bool());
};

class MockLevelEstimator : public LevelEstimator {
 public:
  virtual ~MockLevelEstimator() {}
  MOCK_METHOD1(Enable, int(bool enable));
  MOCK_CONST_METHOD0(is_enabled, bool());
  MOCK_METHOD0(RMS, int());
};

class MockNoiseSuppression : public NoiseSuppression {
 public:
  virtual ~MockNoiseSuppression() {}
  MOCK_METHOD1(Enable, int(bool enable));
  MOCK_CONST_METHOD0(is_enabled, bool());
  MOCK_METHOD1(set_level, int(Level level));
  MOCK_CONST_METHOD0(level, Level());
  MOCK_CONST_METHOD0(speech_probability, float());
  MOCK_METHOD0(NoiseEstimate, std::vector<float>());
};

class MockCustomProcessing : public CustomProcessing {
 public:
  virtual ~MockCustomProcessing() {}
  MOCK_METHOD2(Initialize, void(int sample_rate_hz, int num_channels));
  MOCK_METHOD1(Process, void(AudioBuffer* audio));
  MOCK_METHOD1(SetRuntimeSetting,
               void(AudioProcessing::RuntimeSetting setting));
  MOCK_CONST_METHOD0(ToString, std::string());
};

class MockCustomAudioAnalyzer : public CustomAudioAnalyzer {
 public:
  virtual ~MockCustomAudioAnalyzer() {}
  MOCK_METHOD2(Initialize, void(int sample_rate_hz, int num_channels));
  MOCK_METHOD1(Analyze, void(const AudioBuffer* audio));
  MOCK_CONST_METHOD0(ToString, std::string());
};

class MockEchoControl : public EchoControl {
 public:
  virtual ~MockEchoControl() {}
  MOCK_METHOD1(AnalyzeRender, void(AudioBuffer* render));
  MOCK_METHOD1(AnalyzeCapture, void(AudioBuffer* capture));
  MOCK_METHOD2(ProcessCapture,
               void(AudioBuffer* capture, bool echo_path_change));
  MOCK_CONST_METHOD0(GetMetrics, Metrics());
  MOCK_METHOD1(SetAudioBufferDelay, void(size_t delay_ms));
};

class MockVoiceDetection : public VoiceDetection {
 public:
  virtual ~MockVoiceDetection() {}
  MOCK_METHOD1(Enable, int(bool enable));
  MOCK_CONST_METHOD0(is_enabled, bool());
  MOCK_CONST_METHOD0(stream_has_voice, bool());
  MOCK_METHOD1(set_stream_has_voice, int(bool has_voice));
  MOCK_METHOD1(set_likelihood, int(Likelihood likelihood));
  MOCK_CONST_METHOD0(likelihood, Likelihood());
  MOCK_METHOD1(set_frame_size_ms, int(int size));
  MOCK_CONST_METHOD0(frame_size_ms, int());
};

class MockAudioProcessing : public testing::NiceMock<AudioProcessing> {
 public:
  MockAudioProcessing()
      : gain_control_(new testing::NiceMock<MockGainControl>()),
        level_estimator_(new testing::NiceMock<MockLevelEstimator>()),
        noise_suppression_(new testing::NiceMock<MockNoiseSuppression>()),
        voice_detection_(new testing::NiceMock<MockVoiceDetection>()) {}

  virtual ~MockAudioProcessing() {}

  MOCK_METHOD0(Initialize, int());
  MOCK_METHOD6(Initialize,
               int(int capture_input_sample_rate_hz,
                   int capture_output_sample_rate_hz,
                   int render_sample_rate_hz,
                   ChannelLayout capture_input_layout,
                   ChannelLayout capture_output_layout,
                   ChannelLayout render_input_layout));
  MOCK_METHOD1(Initialize, int(const ProcessingConfig& processing_config));
  MOCK_METHOD1(ApplyConfig, void(const Config& config));
  MOCK_METHOD1(SetExtraOptions, void(const webrtc::Config& config));
  MOCK_CONST_METHOD0(proc_sample_rate_hz, int());
  MOCK_CONST_METHOD0(proc_split_sample_rate_hz, int());
  MOCK_CONST_METHOD0(num_input_channels, size_t());
  MOCK_CONST_METHOD0(num_proc_channels, size_t());
  MOCK_CONST_METHOD0(num_output_channels, size_t());
  MOCK_CONST_METHOD0(num_reverse_channels, size_t());
  MOCK_METHOD1(set_output_will_be_muted, void(bool muted));
  MOCK_METHOD1(SetRuntimeSetting, void(RuntimeSetting setting));
  MOCK_METHOD1(ProcessStream, int(AudioFrame* frame));
  MOCK_METHOD7(ProcessStream,
               int(const float* const* src,
                   size_t samples_per_channel,
                   int input_sample_rate_hz,
                   ChannelLayout input_layout,
                   int output_sample_rate_hz,
                   ChannelLayout output_layout,
                   float* const* dest));
  MOCK_METHOD4(ProcessStream,
               int(const float* const* src,
                   const StreamConfig& input_config,
                   const StreamConfig& output_config,
                   float* const* dest));
  MOCK_METHOD1(ProcessReverseStream, int(AudioFrame* frame));
  MOCK_METHOD4(AnalyzeReverseStream,
               int(const float* const* data,
                   size_t samples_per_channel,
                   int sample_rate_hz,
                   ChannelLayout layout));
  MOCK_METHOD4(ProcessReverseStream,
               int(const float* const* src,
                   const StreamConfig& input_config,
                   const StreamConfig& output_config,
                   float* const* dest));
  MOCK_METHOD1(set_stream_delay_ms, int(int delay));
  MOCK_CONST_METHOD0(stream_delay_ms, int());
  MOCK_CONST_METHOD0(was_stream_delay_set, bool());
  MOCK_METHOD1(set_stream_key_pressed, void(bool key_pressed));
  MOCK_METHOD1(set_delay_offset_ms, void(int offset));
  MOCK_CONST_METHOD0(delay_offset_ms, int());

  virtual void AttachAecDump(std::unique_ptr<AecDump> aec_dump) {}
  MOCK_METHOD0(DetachAecDump, void());

  virtual void AttachPlayoutAudioGenerator(
      std::unique_ptr<AudioGenerator> audio_generator) {}
  MOCK_METHOD0(DetachPlayoutAudioGenerator, void());

  MOCK_METHOD0(UpdateHistogramsOnCallEnd, void());
  MOCK_CONST_METHOD1(GetStatistics, AudioProcessingStats(bool));
  virtual MockGainControl* gain_control() const { return gain_control_.get(); }
  virtual MockLevelEstimator* level_estimator() const {
    return level_estimator_.get();
  }
  virtual MockNoiseSuppression* noise_suppression() const {
    return noise_suppression_.get();
  }
  virtual MockVoiceDetection* voice_detection() const {
    return voice_detection_.get();
  }

  MOCK_CONST_METHOD0(GetConfig, AudioProcessing::Config());

 private:
  std::unique_ptr<MockGainControl> gain_control_;
  std::unique_ptr<MockLevelEstimator> level_estimator_;
  std::unique_ptr<MockNoiseSuppression> noise_suppression_;
  std::unique_ptr<MockVoiceDetection> voice_detection_;
};

}  // namespace test
}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_INCLUDE_MOCK_AUDIO_PROCESSING_H_
