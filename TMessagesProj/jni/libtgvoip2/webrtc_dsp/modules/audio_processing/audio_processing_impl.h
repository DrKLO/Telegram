/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_AUDIO_PROCESSING_IMPL_H_
#define MODULES_AUDIO_PROCESSING_AUDIO_PROCESSING_IMPL_H_

#include <list>
#include <memory>
#include <vector>

#include "modules/audio_processing/audio_buffer.h"
#include "modules/audio_processing/include/aec_dump.h"
#include "modules/audio_processing/include/audio_processing.h"
#include "modules/audio_processing/render_queue_item_verifier.h"
#include "modules/audio_processing/rms_level.h"
#include "rtc_base/criticalsection.h"
#include "rtc_base/function_view.h"
#include "rtc_base/gtest_prod_util.h"
#include "rtc_base/ignore_wundef.h"
#include "rtc_base/swap_queue.h"
#include "rtc_base/thread_annotations.h"

namespace webrtc {

class ApmDataDumper;
class AudioConverter;

class AudioProcessingImpl : public AudioProcessing {
 public:
  // Methods forcing APM to run in a single-threaded manner.
  // Acquires both the render and capture locks.
  explicit AudioProcessingImpl(const webrtc::Config& config);
  // AudioProcessingImpl takes ownership of capture post processor.
  AudioProcessingImpl(const webrtc::Config& config,
                      std::unique_ptr<CustomProcessing> capture_post_processor,
                      std::unique_ptr<CustomProcessing> render_pre_processor,
                      std::unique_ptr<EchoControlFactory> echo_control_factory,
                      rtc::scoped_refptr<EchoDetector> echo_detector,
                      std::unique_ptr<CustomAudioAnalyzer> capture_analyzer);
  ~AudioProcessingImpl() override;
  int Initialize() override;
  int Initialize(int capture_input_sample_rate_hz,
                 int capture_output_sample_rate_hz,
                 int render_sample_rate_hz,
                 ChannelLayout capture_input_layout,
                 ChannelLayout capture_output_layout,
                 ChannelLayout render_input_layout) override;
  int Initialize(const ProcessingConfig& processing_config) override;
  void ApplyConfig(const AudioProcessing::Config& config) override;
  void SetExtraOptions(const webrtc::Config& config) override;
  void UpdateHistogramsOnCallEnd() override;
  void AttachAecDump(std::unique_ptr<AecDump> aec_dump) override;
  void DetachAecDump() override;
  void AttachPlayoutAudioGenerator(
      std::unique_ptr<AudioGenerator> audio_generator) override;
  void DetachPlayoutAudioGenerator() override;

  void SetRuntimeSetting(RuntimeSetting setting) override;

  // Capture-side exclusive methods possibly running APM in a
  // multi-threaded manner. Acquire the capture lock.
  int ProcessStream(AudioFrame* frame) override;
  int ProcessStream(const float* const* src,
                    size_t samples_per_channel,
                    int input_sample_rate_hz,
                    ChannelLayout input_layout,
                    int output_sample_rate_hz,
                    ChannelLayout output_layout,
                    float* const* dest) override;
  int ProcessStream(const float* const* src,
                    const StreamConfig& input_config,
                    const StreamConfig& output_config,
                    float* const* dest) override;
  void set_output_will_be_muted(bool muted) override;
  int set_stream_delay_ms(int delay) override;
  void set_delay_offset_ms(int offset) override;
  int delay_offset_ms() const override;
  void set_stream_key_pressed(bool key_pressed) override;

  // Render-side exclusive methods possibly running APM in a
  // multi-threaded manner. Acquire the render lock.
  int ProcessReverseStream(AudioFrame* frame) override;
  int AnalyzeReverseStream(const float* const* data,
                           size_t samples_per_channel,
                           int sample_rate_hz,
                           ChannelLayout layout) override;
  int ProcessReverseStream(const float* const* src,
                           const StreamConfig& input_config,
                           const StreamConfig& output_config,
                           float* const* dest) override;

  // Methods only accessed from APM submodules or
  // from AudioProcessing tests in a single-threaded manner.
  // Hence there is no need for locks in these.
  int proc_sample_rate_hz() const override;
  int proc_split_sample_rate_hz() const override;
  size_t num_input_channels() const override;
  size_t num_proc_channels() const override;
  size_t num_output_channels() const override;
  size_t num_reverse_channels() const override;
  int stream_delay_ms() const override;
  bool was_stream_delay_set() const override
      RTC_EXCLUSIVE_LOCKS_REQUIRED(crit_capture_);

  AudioProcessingStats GetStatistics(bool has_remote_tracks) const override;

  // Methods returning pointers to APM submodules.
  // No locks are aquired in those, as those locks
  // would offer no protection (the submodules are
  // created only once in a single-treaded manner
  // during APM creation).
  GainControl* gain_control() const override;
  LevelEstimator* level_estimator() const override;
  NoiseSuppression* noise_suppression() const override;
  VoiceDetection* voice_detection() const override;

  // TODO(peah): Remove MutateConfig once the new API allows that.
  void MutateConfig(rtc::FunctionView<void(AudioProcessing::Config*)> mutator);
  AudioProcessing::Config GetConfig() const override;

 protected:
  // Overridden in a mock.
  virtual int InitializeLocked()
      RTC_EXCLUSIVE_LOCKS_REQUIRED(crit_render_, crit_capture_);

 private:
  // TODO(peah): These friend classes should be removed as soon as the new
  // parameter setting scheme allows.
  FRIEND_TEST_ALL_PREFIXES(ApmConfiguration, DefaultBehavior);
  FRIEND_TEST_ALL_PREFIXES(ApmConfiguration, ValidConfigBehavior);
  FRIEND_TEST_ALL_PREFIXES(ApmConfiguration, InValidConfigBehavior);

  // Class providing thread-safe message pipe functionality for
  // |runtime_settings_|.
  class RuntimeSettingEnqueuer {
   public:
    explicit RuntimeSettingEnqueuer(
        SwapQueue<RuntimeSetting>* runtime_settings);
    ~RuntimeSettingEnqueuer();
    void Enqueue(RuntimeSetting setting);

   private:
    SwapQueue<RuntimeSetting>& runtime_settings_;
  };
  struct ApmPublicSubmodules;
  struct ApmPrivateSubmodules;

  std::unique_ptr<ApmDataDumper> data_dumper_;
  static int instance_count_;

  SwapQueue<RuntimeSetting> capture_runtime_settings_;
  SwapQueue<RuntimeSetting> render_runtime_settings_;

  RuntimeSettingEnqueuer capture_runtime_settings_enqueuer_;
  RuntimeSettingEnqueuer render_runtime_settings_enqueuer_;

  // EchoControl factory.
  std::unique_ptr<EchoControlFactory> echo_control_factory_;

  class ApmSubmoduleStates {
   public:
    ApmSubmoduleStates(bool capture_post_processor_enabled,
                       bool render_pre_processor_enabled,
                       bool capture_analyzer_enabled);
    // Updates the submodule state and returns true if it has changed.
    bool Update(bool high_pass_filter_enabled,
                bool echo_canceller_enabled,
                bool mobile_echo_controller_enabled,
                bool residual_echo_detector_enabled,
                bool noise_suppressor_enabled,
                bool adaptive_gain_controller_enabled,
                bool gain_controller2_enabled,
                bool pre_amplifier_enabled,
                bool echo_controller_enabled,
                bool voice_activity_detector_enabled,
                bool level_estimator_enabled,
                bool transient_suppressor_enabled);
    bool CaptureMultiBandSubModulesActive() const;
    bool CaptureMultiBandProcessingActive() const;
    bool CaptureFullBandProcessingActive() const;
    bool CaptureAnalyzerActive() const;
    bool RenderMultiBandSubModulesActive() const;
    bool RenderFullBandProcessingActive() const;
    bool RenderMultiBandProcessingActive() const;
    bool LowCutFilteringRequired() const;

   private:
    const bool capture_post_processor_enabled_ = false;
    const bool render_pre_processor_enabled_ = false;
    const bool capture_analyzer_enabled_ = false;
    bool high_pass_filter_enabled_ = false;
    bool echo_canceller_enabled_ = false;
    bool mobile_echo_controller_enabled_ = false;
    bool residual_echo_detector_enabled_ = false;
    bool noise_suppressor_enabled_ = false;
    bool adaptive_gain_controller_enabled_ = false;
    bool gain_controller2_enabled_ = false;
    bool pre_amplifier_enabled_ = false;
    bool echo_controller_enabled_ = false;
    bool level_estimator_enabled_ = false;
    bool voice_activity_detector_enabled_ = false;
    bool transient_suppressor_enabled_ = false;
    bool first_update_ = true;
  };

  // Method for modifying the formats struct that are called from both
  // the render and capture threads. The check for whether modifications
  // are needed is done while holding the render lock only, thereby avoiding
  // that the capture thread blocks the render thread.
  // The struct is modified in a single-threaded manner by holding both the
  // render and capture locks.
  int MaybeInitialize(const ProcessingConfig& config, bool force_initialization)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(crit_render_);

  int MaybeInitializeRender(const ProcessingConfig& processing_config)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(crit_render_);

  int MaybeInitializeCapture(const ProcessingConfig& processing_config,
                             bool force_initialization)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(crit_render_);

  // Method for updating the state keeping track of the active submodules.
  // Returns a bool indicating whether the state has changed.
  bool UpdateActiveSubmoduleStates()
      RTC_EXCLUSIVE_LOCKS_REQUIRED(crit_capture_);

  // Methods requiring APM running in a single-threaded manner.
  // Are called with both the render and capture locks already
  // acquired.
  void InitializeTransient()
      RTC_EXCLUSIVE_LOCKS_REQUIRED(crit_render_, crit_capture_);
  int InitializeLocked(const ProcessingConfig& config)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(crit_render_, crit_capture_);
  void InitializeResidualEchoDetector()
      RTC_EXCLUSIVE_LOCKS_REQUIRED(crit_render_, crit_capture_);
  void InitializeLowCutFilter() RTC_EXCLUSIVE_LOCKS_REQUIRED(crit_capture_);
  void InitializeEchoController() RTC_EXCLUSIVE_LOCKS_REQUIRED(crit_capture_);
  void InitializeGainController2() RTC_EXCLUSIVE_LOCKS_REQUIRED(crit_capture_);
  void InitializePreAmplifier() RTC_EXCLUSIVE_LOCKS_REQUIRED(crit_capture_);
  void InitializePostProcessor() RTC_EXCLUSIVE_LOCKS_REQUIRED(crit_capture_);
  void InitializeAnalyzer() RTC_EXCLUSIVE_LOCKS_REQUIRED(crit_capture_);
  void InitializePreProcessor() RTC_EXCLUSIVE_LOCKS_REQUIRED(crit_render_);

  // Empties and handles the respective RuntimeSetting queues.
  void HandleCaptureRuntimeSettings()
      RTC_EXCLUSIVE_LOCKS_REQUIRED(crit_capture_);
  void HandleRenderRuntimeSettings() RTC_EXCLUSIVE_LOCKS_REQUIRED(crit_render_);

  void EmptyQueuedRenderAudio();
  void AllocateRenderQueue()
      RTC_EXCLUSIVE_LOCKS_REQUIRED(crit_render_, crit_capture_);
  void QueueBandedRenderAudio(AudioBuffer* audio)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(crit_render_);
  void QueueNonbandedRenderAudio(AudioBuffer* audio)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(crit_render_);

  // Capture-side exclusive methods possibly running APM in a multi-threaded
  // manner that are called with the render lock already acquired.
  int ProcessCaptureStreamLocked() RTC_EXCLUSIVE_LOCKS_REQUIRED(crit_capture_);
  void MaybeUpdateHistograms() RTC_EXCLUSIVE_LOCKS_REQUIRED(crit_capture_);

  // Render-side exclusive methods possibly running APM in a multi-threaded
  // manner that are called with the render lock already acquired.
  // TODO(ekm): Remove once all clients updated to new interface.
  int AnalyzeReverseStreamLocked(const float* const* src,
                                 const StreamConfig& input_config,
                                 const StreamConfig& output_config)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(crit_render_);
  int ProcessRenderStreamLocked() RTC_EXCLUSIVE_LOCKS_REQUIRED(crit_render_);

  // Collects configuration settings from public and private
  // submodules to be saved as an audioproc::Config message on the
  // AecDump if it is attached.  If not |forced|, only writes the current
  // config if it is different from the last saved one; if |forced|,
  // writes the config regardless of the last saved.
  void WriteAecDumpConfigMessage(bool forced)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(crit_capture_);

  // Notifies attached AecDump of current configuration and capture data.
  void RecordUnprocessedCaptureStream(const float* const* capture_stream)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(crit_capture_);

  void RecordUnprocessedCaptureStream(const AudioFrame& capture_frame)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(crit_capture_);

  // Notifies attached AecDump of current configuration and
  // processed capture data and issues a capture stream recording
  // request.
  void RecordProcessedCaptureStream(
      const float* const* processed_capture_stream)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(crit_capture_);

  void RecordProcessedCaptureStream(const AudioFrame& processed_capture_frame)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(crit_capture_);

  // Notifies attached AecDump about current state (delay, drift, etc).
  void RecordAudioProcessingState() RTC_EXCLUSIVE_LOCKS_REQUIRED(crit_capture_);

  // AecDump instance used for optionally logging APM config, input
  // and output to file in the AEC-dump format defined in debug.proto.
  std::unique_ptr<AecDump> aec_dump_;

  // Hold the last config written with AecDump for avoiding writing
  // the same config twice.
  InternalAPMConfig apm_config_for_aec_dump_ RTC_GUARDED_BY(crit_capture_);

  // Critical sections.
  rtc::CriticalSection crit_render_ RTC_ACQUIRED_BEFORE(crit_capture_);
  rtc::CriticalSection crit_capture_;

  // Struct containing the Config specifying the behavior of APM.
  AudioProcessing::Config config_;

  // Class containing information about what submodules are active.
  ApmSubmoduleStates submodule_states_;

  // Structs containing the pointers to the submodules.
  std::unique_ptr<ApmPublicSubmodules> public_submodules_;
  std::unique_ptr<ApmPrivateSubmodules> private_submodules_;

  // State that is written to while holding both the render and capture locks
  // but can be read without any lock being held.
  // As this is only accessed internally of APM, and all internal methods in APM
  // either are holding the render or capture locks, this construct is safe as
  // it is not possible to read the variables while writing them.
  struct ApmFormatState {
    ApmFormatState()
        :  // Format of processing streams at input/output call sites.
          api_format({{{kSampleRate16kHz, 1, false},
                       {kSampleRate16kHz, 1, false},
                       {kSampleRate16kHz, 1, false},
                       {kSampleRate16kHz, 1, false}}}),
          render_processing_format(kSampleRate16kHz, 1) {}
    ProcessingConfig api_format;
    StreamConfig render_processing_format;
  } formats_;

  // APM constants.
  const struct ApmConstants {
    ApmConstants(int agc_startup_min_volume,
                 int agc_clipped_level_min,
                 bool use_experimental_agc,
                 bool use_experimental_agc_agc2_level_estimation,
                 bool use_experimental_agc_agc2_digital_adaptive,
                 bool use_experimental_agc_process_before_aec)
        :  // Format of processing streams at input/output call sites.
          agc_startup_min_volume(agc_startup_min_volume),
          agc_clipped_level_min(agc_clipped_level_min),
          use_experimental_agc(use_experimental_agc),
          use_experimental_agc_agc2_level_estimation(
              use_experimental_agc_agc2_level_estimation),
          use_experimental_agc_agc2_digital_adaptive(
              use_experimental_agc_agc2_digital_adaptive),
          use_experimental_agc_process_before_aec(
              use_experimental_agc_process_before_aec) {}
    int agc_startup_min_volume;
    int agc_clipped_level_min;
    bool use_experimental_agc;
    bool use_experimental_agc_agc2_level_estimation;
    bool use_experimental_agc_agc2_digital_adaptive;
    bool use_experimental_agc_process_before_aec;

  } constants_;

  struct ApmCaptureState {
    ApmCaptureState(bool transient_suppressor_enabled);
    ~ApmCaptureState();
    int aec_system_delay_jumps;
    int delay_offset_ms;
    bool was_stream_delay_set;
    int last_stream_delay_ms;
    int last_aec_system_delay_ms;
    int stream_delay_jumps;
    bool output_will_be_muted;
    bool key_pressed;
    bool transient_suppressor_enabled;
    std::unique_ptr<AudioBuffer> capture_audio;
    // Only the rate and samples fields of capture_processing_format_ are used
    // because the capture processing number of channels is mutable and is
    // tracked by the capture_audio_.
    StreamConfig capture_processing_format;
    int split_rate;
    bool echo_path_gain_change;
    int prev_analog_mic_level;
    float prev_pre_amp_gain;
  } capture_ RTC_GUARDED_BY(crit_capture_);

  struct ApmCaptureNonLockedState {
    ApmCaptureNonLockedState()
        : capture_processing_format(kSampleRate16kHz),
          split_rate(kSampleRate16kHz),
          stream_delay_ms(0) {}
    // Only the rate and samples fields of capture_processing_format_ are used
    // because the forward processing number of channels is mutable and is
    // tracked by the capture_audio_.
    StreamConfig capture_processing_format;
    int split_rate;
    int stream_delay_ms;
    bool echo_controller_enabled = false;
  } capture_nonlocked_;

  struct ApmRenderState {
    ApmRenderState();
    ~ApmRenderState();
    std::unique_ptr<AudioConverter> render_converter;
    std::unique_ptr<AudioBuffer> render_audio;
  } render_ RTC_GUARDED_BY(crit_render_);

  size_t aec_render_queue_element_max_size_ RTC_GUARDED_BY(crit_render_)
      RTC_GUARDED_BY(crit_capture_) = 0;
  std::vector<float> aec_render_queue_buffer_ RTC_GUARDED_BY(crit_render_);
  std::vector<float> aec_capture_queue_buffer_ RTC_GUARDED_BY(crit_capture_);

  size_t aecm_render_queue_element_max_size_ RTC_GUARDED_BY(crit_render_)
      RTC_GUARDED_BY(crit_capture_) = 0;
  std::vector<int16_t> aecm_render_queue_buffer_ RTC_GUARDED_BY(crit_render_);
  std::vector<int16_t> aecm_capture_queue_buffer_ RTC_GUARDED_BY(crit_capture_);

  size_t agc_render_queue_element_max_size_ RTC_GUARDED_BY(crit_render_)
      RTC_GUARDED_BY(crit_capture_) = 0;
  std::vector<int16_t> agc_render_queue_buffer_ RTC_GUARDED_BY(crit_render_);
  std::vector<int16_t> agc_capture_queue_buffer_ RTC_GUARDED_BY(crit_capture_);

  size_t red_render_queue_element_max_size_ RTC_GUARDED_BY(crit_render_)
      RTC_GUARDED_BY(crit_capture_) = 0;
  std::vector<float> red_render_queue_buffer_ RTC_GUARDED_BY(crit_render_);
  std::vector<float> red_capture_queue_buffer_ RTC_GUARDED_BY(crit_capture_);

  RmsLevel capture_input_rms_ RTC_GUARDED_BY(crit_capture_);
  RmsLevel capture_output_rms_ RTC_GUARDED_BY(crit_capture_);
  int capture_rms_interval_counter_ RTC_GUARDED_BY(crit_capture_) = 0;

  // Lock protection not needed.
  std::unique_ptr<SwapQueue<std::vector<float>, RenderQueueItemVerifier<float>>>
      aec_render_signal_queue_;
  std::unique_ptr<
      SwapQueue<std::vector<int16_t>, RenderQueueItemVerifier<int16_t>>>
      aecm_render_signal_queue_;
  std::unique_ptr<
      SwapQueue<std::vector<int16_t>, RenderQueueItemVerifier<int16_t>>>
      agc_render_signal_queue_;
  std::unique_ptr<SwapQueue<std::vector<float>, RenderQueueItemVerifier<float>>>
      red_render_signal_queue_;
};

}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_AUDIO_PROCESSING_IMPL_H_
