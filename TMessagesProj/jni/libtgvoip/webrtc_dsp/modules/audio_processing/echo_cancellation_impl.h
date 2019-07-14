/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_ECHO_CANCELLATION_IMPL_H_
#define MODULES_AUDIO_PROCESSING_ECHO_CANCELLATION_IMPL_H_

#include <stddef.h>
#include <memory>
#include <string>
#include <vector>

#include "api/array_view.h"
#include "modules/audio_processing/include/audio_processing.h"
#include "rtc_base/constructormagic.h"
#include "rtc_base/criticalsection.h"
#include "rtc_base/thread_annotations.h"

namespace webrtc {

class AudioBuffer;

// The acoustic echo cancellation (AEC) component provides better performance
// than AECM but also requires more processing power and is dependent on delay
// stability and reporting accuracy. As such it is well-suited and recommended
// for PC and IP phone applications.
class EchoCancellationImpl {
 public:
  explicit EchoCancellationImpl();
  ~EchoCancellationImpl();

  void ProcessRenderAudio(rtc::ArrayView<const float> packed_render_audio);
  int ProcessCaptureAudio(AudioBuffer* audio, int stream_delay_ms);

  int Enable(bool enable);
  bool is_enabled() const;

  // Differences in clock speed on the primary and reverse streams can impact
  // the AEC performance. On the client-side, this could be seen when different
  // render and capture devices are used, particularly with webcams.
  //
  // This enables a compensation mechanism, and requires that
  // set_stream_drift_samples() be called.
  int enable_drift_compensation(bool enable);
  bool is_drift_compensation_enabled() const;

  // Sets the difference between the number of samples rendered and captured by
  // the audio devices since the last call to |ProcessStream()|. Must be called
  // if drift compensation is enabled, prior to |ProcessStream()|.
  void set_stream_drift_samples(int drift);
  int stream_drift_samples() const;

  enum SuppressionLevel {
    kLowSuppression,
    kModerateSuppression,
    kHighSuppression
  };

  // Sets the aggressiveness of the suppressor. A higher level trades off
  // double-talk performance for increased echo suppression.
  int set_suppression_level(SuppressionLevel level);
  SuppressionLevel suppression_level() const;

  // Returns false if the current frame almost certainly contains no echo
  // and true if it _might_ contain echo.
  bool stream_has_echo() const;

  // Enables the computation of various echo metrics. These are obtained
  // through |GetMetrics()|.
  int enable_metrics(bool enable);
  bool are_metrics_enabled() const;

  // Each statistic is reported in dB.
  // P_far:  Far-end (render) signal power.
  // P_echo: Near-end (capture) echo signal power.
  // P_out:  Signal power at the output of the AEC.
  // P_a:    Internal signal power at the point before the AEC's non-linear
  //         processor.
  struct Metrics {
    struct Statistic {
      int instant = 0;  // Instantaneous value.
      int average = 0;  // Long-term average.
      int maximum = 0;  // Long-term maximum.
      int minimum = 0;  // Long-term minimum.
    };
    // RERL = ERL + ERLE
    Statistic residual_echo_return_loss;

    // ERL = 10log_10(P_far / P_echo)
    Statistic echo_return_loss;

    // ERLE = 10log_10(P_echo / P_out)
    Statistic echo_return_loss_enhancement;

    // (Pre non-linear processing suppression) A_NLP = 10log_10(P_echo / P_a)
    Statistic a_nlp;

    // Fraction of time that the AEC linear filter is divergent, in a 1-second
    // non-overlapped aggregation window.
    float divergent_filter_fraction;
  };

  // Provides various statistics about the AEC.
  int GetMetrics(Metrics* metrics);

  // Enables computation and logging of delay values. Statistics are obtained
  // through |GetDelayMetrics()|.
  int enable_delay_logging(bool enable);
  bool is_delay_logging_enabled() const;

  // Provides delay metrics.
  // The delay metrics consists of the delay |median| and the delay standard
  // deviation |std|. It also consists of the fraction of delay estimates
  // |fraction_poor_delays| that can make the echo cancellation perform poorly.
  // The values are aggregated until the first call to |GetDelayMetrics()| and
  // afterwards aggregated and updated every second.
  // Note that if there are several clients pulling metrics from
  // |GetDelayMetrics()| during a session the first call from any of them will
  // change to one second aggregation window for all.
  int GetDelayMetrics(int* median, int* std);
  int GetDelayMetrics(int* median, int* std, float* fraction_poor_delays);

  // Returns a pointer to the low level AEC component.  In case of multiple
  // channels, the pointer to the first one is returned.  A NULL pointer is
  // returned when the AEC component is disabled or has not been initialized
  // successfully.
  struct AecCore* aec_core() const;

  void Initialize(int sample_rate_hz,
                  size_t num_reverse_channels_,
                  size_t num_output_channels_,
                  size_t num_proc_channels_);
  void SetExtraOptions(const webrtc::Config& config);
  bool is_delay_agnostic_enabled() const;
  bool is_extended_filter_enabled() const;
  std::string GetExperimentsDescription();
  bool is_refined_adaptive_filter_enabled() const;

  // Returns the system delay of the first AEC component.
  int GetSystemDelayInSamples() const;

  static void PackRenderAudioBuffer(const AudioBuffer* audio,
                                    size_t num_output_channels,
                                    size_t num_channels,
                                    std::vector<float>* packed_buffer);
  static size_t NumCancellersRequired(size_t num_output_channels,
                                      size_t num_reverse_channels);

 private:
  class Canceller;
  struct StreamProperties;

  void AllocateRenderQueue();
  int Configure();

  bool enabled_ = false;
  bool drift_compensation_enabled_;
  bool metrics_enabled_;
  SuppressionLevel suppression_level_;
  int stream_drift_samples_;
  bool was_stream_drift_set_;
  bool stream_has_echo_;
  bool delay_logging_enabled_;
  bool extended_filter_enabled_;
  bool delay_agnostic_enabled_;
  bool refined_adaptive_filter_enabled_ = false;

  // Only active on Chrome OS devices.
  const bool enforce_zero_stream_delay_;

  std::vector<std::unique_ptr<Canceller>> cancellers_;
  std::unique_ptr<StreamProperties> stream_properties_;
};

}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_ECHO_CANCELLATION_IMPL_H_
