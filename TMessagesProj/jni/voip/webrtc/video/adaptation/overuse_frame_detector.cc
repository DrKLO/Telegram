/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "video/adaptation/overuse_frame_detector.h"

#include <math.h>
#include <stdio.h>

#include <algorithm>
#include <list>
#include <map>
#include <memory>
#include <string>
#include <utility>

#include "api/video/video_frame.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/numerics/exp_filter.h"
#include "rtc_base/time_utils.h"
#include "system_wrappers/include/field_trial.h"

#if defined(WEBRTC_MAC) && !defined(WEBRTC_IOS)
#include <mach/mach.h>
#endif  // defined(WEBRTC_MAC) && !defined(WEBRTC_IOS)

namespace webrtc {

namespace {
const int64_t kCheckForOveruseIntervalMs = 5000;
const int64_t kTimeToFirstCheckForOveruseMs = 100;

// Delay between consecutive rampups. (Used for quick recovery.)
const int kQuickRampUpDelayMs = 10 * 1000;
// Delay between rampup attempts. Initially uses standard, scales up to max.
const int kStandardRampUpDelayMs = 40 * 1000;
const int kMaxRampUpDelayMs = 240 * 1000;
// Expontential back-off factor, to prevent annoying up-down behaviour.
const double kRampUpBackoffFactor = 2.0;

// Max number of overuses detected before always applying the rampup delay.
const int kMaxOverusesBeforeApplyRampupDelay = 4;

// The maximum exponent to use in VCMExpFilter.
const float kMaxExp = 7.0f;
// Default value used before first reconfiguration.
const int kDefaultFrameRate = 30;
// Default sample diff, default frame rate.
const float kDefaultSampleDiffMs = 1000.0f / kDefaultFrameRate;
// A factor applied to the sample diff on OnTargetFramerateUpdated to determine
// a max limit for the sample diff. For instance, with a framerate of 30fps,
// the sample diff is capped to (1000 / 30) * 1.35 = 45ms. This prevents
// triggering too soon if there are individual very large outliers.
const float kMaxSampleDiffMarginFactor = 1.35f;
// Minimum framerate allowed for usage calculation. This prevents crazy long
// encode times from being accepted if the frame rate happens to be low.
const int kMinFramerate = 7;
const int kMaxFramerate = 30;

// Class for calculating the processing usage on the send-side (the average
// processing time of a frame divided by the average time difference between
// captured frames).
class SendProcessingUsage1 : public OveruseFrameDetector::ProcessingUsage {
 public:
  explicit SendProcessingUsage1(const CpuOveruseOptions& options)
      : kWeightFactorFrameDiff(0.998f),
        kWeightFactorProcessing(0.995f),
        kInitialSampleDiffMs(40.0f),
        options_(options),
        count_(0),
        last_processed_capture_time_us_(-1),
        max_sample_diff_ms_(kDefaultSampleDiffMs * kMaxSampleDiffMarginFactor),
        filtered_processing_ms_(new rtc::ExpFilter(kWeightFactorProcessing)),
        filtered_frame_diff_ms_(new rtc::ExpFilter(kWeightFactorFrameDiff)) {
    Reset();
  }
  ~SendProcessingUsage1() override {}

  void Reset() override {
    frame_timing_.clear();
    count_ = 0;
    last_processed_capture_time_us_ = -1;
    max_sample_diff_ms_ = kDefaultSampleDiffMs * kMaxSampleDiffMarginFactor;
    filtered_frame_diff_ms_->Reset(kWeightFactorFrameDiff);
    filtered_frame_diff_ms_->Apply(1.0f, kInitialSampleDiffMs);
    filtered_processing_ms_->Reset(kWeightFactorProcessing);
    filtered_processing_ms_->Apply(1.0f, InitialProcessingMs());
  }

  void SetMaxSampleDiffMs(float diff_ms) override {
    max_sample_diff_ms_ = diff_ms;
  }

  void FrameCaptured(const VideoFrame& frame,
                     int64_t time_when_first_seen_us,
                     int64_t last_capture_time_us) override {
    if (last_capture_time_us != -1)
      AddCaptureSample(1e-3 * (time_when_first_seen_us - last_capture_time_us));

    frame_timing_.push_back(FrameTiming(frame.timestamp_us(), frame.timestamp(),
                                        time_when_first_seen_us));
  }

  absl::optional<int> FrameSent(
      uint32_t timestamp,
      int64_t time_sent_in_us,
      int64_t /* capture_time_us */,
      absl::optional<int> /* encode_duration_us */) override {
    absl::optional<int> encode_duration_us;
    // Delay before reporting actual encoding time, used to have the ability to
    // detect total encoding time when encoding more than one layer. Encoding is
    // here assumed to finish within a second (or that we get enough long-time
    // samples before one second to trigger an overuse even when this is not the
    // case).
    static const int64_t kEncodingTimeMeasureWindowMs = 1000;
    for (auto& it : frame_timing_) {
      if (it.timestamp == timestamp) {
        it.last_send_us = time_sent_in_us;
        break;
      }
    }
    // TODO(pbos): Handle the case/log errors when not finding the corresponding
    // frame (either very slow encoding or incorrect wrong timestamps returned
    // from the encoder).
    // This is currently the case for all frames on ChromeOS, so logging them
    // would be spammy, and triggering overuse would be wrong.
    // https://crbug.com/350106
    while (!frame_timing_.empty()) {
      FrameTiming timing = frame_timing_.front();
      if (time_sent_in_us - timing.capture_us <
          kEncodingTimeMeasureWindowMs * rtc::kNumMicrosecsPerMillisec) {
        break;
      }
      if (timing.last_send_us != -1) {
        encode_duration_us.emplace(
            static_cast<int>(timing.last_send_us - timing.capture_us));

        if (last_processed_capture_time_us_ != -1) {
          int64_t diff_us = timing.capture_us - last_processed_capture_time_us_;
          AddSample(1e-3 * (*encode_duration_us), 1e-3 * diff_us);
        }
        last_processed_capture_time_us_ = timing.capture_us;
      }
      frame_timing_.pop_front();
    }
    return encode_duration_us;
  }

  int Value() override {
    if (count_ < static_cast<uint32_t>(options_.min_frame_samples)) {
      return static_cast<int>(InitialUsageInPercent() + 0.5f);
    }
    float frame_diff_ms = std::max(filtered_frame_diff_ms_->filtered(), 1.0f);
    frame_diff_ms = std::min(frame_diff_ms, max_sample_diff_ms_);
    float encode_usage_percent =
        100.0f * filtered_processing_ms_->filtered() / frame_diff_ms;
    return static_cast<int>(encode_usage_percent + 0.5);
  }

 private:
  struct FrameTiming {
    FrameTiming(int64_t capture_time_us, uint32_t timestamp, int64_t now)
        : capture_time_us(capture_time_us),
          timestamp(timestamp),
          capture_us(now),
          last_send_us(-1) {}
    int64_t capture_time_us;
    uint32_t timestamp;
    int64_t capture_us;
    int64_t last_send_us;
  };

  void AddCaptureSample(float sample_ms) {
    float exp = sample_ms / kDefaultSampleDiffMs;
    exp = std::min(exp, kMaxExp);
    filtered_frame_diff_ms_->Apply(exp, sample_ms);
  }

  void AddSample(float processing_ms, int64_t diff_last_sample_ms) {
    ++count_;
    float exp = diff_last_sample_ms / kDefaultSampleDiffMs;
    exp = std::min(exp, kMaxExp);
    filtered_processing_ms_->Apply(exp, processing_ms);
  }

  float InitialUsageInPercent() const {
    // Start in between the underuse and overuse threshold.
    return (options_.low_encode_usage_threshold_percent +
            options_.high_encode_usage_threshold_percent) /
           2.0f;
  }

  float InitialProcessingMs() const {
    return InitialUsageInPercent() * kInitialSampleDiffMs / 100;
  }

  const float kWeightFactorFrameDiff;
  const float kWeightFactorProcessing;
  const float kInitialSampleDiffMs;

  const CpuOveruseOptions options_;
  std::list<FrameTiming> frame_timing_;
  uint64_t count_;
  int64_t last_processed_capture_time_us_;
  float max_sample_diff_ms_;
  std::unique_ptr<rtc::ExpFilter> filtered_processing_ms_;
  std::unique_ptr<rtc::ExpFilter> filtered_frame_diff_ms_;
};

// New cpu load estimator.
// TODO(bugs.webrtc.org/8504): For some period of time, we need to
// switch between the two versions of the estimator for experiments.
// When problems are sorted out, the old estimator should be deleted.
class SendProcessingUsage2 : public OveruseFrameDetector::ProcessingUsage {
 public:
  explicit SendProcessingUsage2(const CpuOveruseOptions& options)
      : options_(options) {
    Reset();
  }
  ~SendProcessingUsage2() override = default;

  void Reset() override {
    prev_time_us_ = -1;
    // Start in between the underuse and overuse threshold.
    load_estimate_ = (options_.low_encode_usage_threshold_percent +
                      options_.high_encode_usage_threshold_percent) /
                     200.0;
  }

  void SetMaxSampleDiffMs(float /* diff_ms */) override {}

  void FrameCaptured(const VideoFrame& frame,
                     int64_t time_when_first_seen_us,
                     int64_t last_capture_time_us) override {}

  absl::optional<int> FrameSent(
      uint32_t /* timestamp */,
      int64_t /* time_sent_in_us */,
      int64_t capture_time_us,
      absl::optional<int> encode_duration_us) override {
    if (encode_duration_us) {
      int duration_per_frame_us =
          DurationPerInputFrame(capture_time_us, *encode_duration_us);
      if (prev_time_us_ != -1) {
        if (capture_time_us < prev_time_us_) {
          // The weighting in AddSample assumes that samples are processed with
          // non-decreasing measurement timestamps. We could implement
          // appropriate weights for samples arriving late, but since it is a
          // rare case, keep things simple, by just pushing those measurements a
          // bit forward in time.
          capture_time_us = prev_time_us_;
        }
        AddSample(1e-6 * duration_per_frame_us,
                  1e-6 * (capture_time_us - prev_time_us_));
      }
    }
    prev_time_us_ = capture_time_us;

    return encode_duration_us;
  }

 private:
  void AddSample(double encode_time, double diff_time) {
    RTC_CHECK_GE(diff_time, 0.0);

    // Use the filter update
    //
    // load <-- x/d (1-exp (-d/T)) + exp (-d/T) load
    //
    // where we must take care for small d, using the proper limit
    // (1 - exp(-d/tau)) / d = 1/tau - d/2tau^2 + O(d^2)
    double tau = (1e-3 * options_.filter_time_ms);
    double e = diff_time / tau;
    double c;
    if (e < 0.0001) {
      c = (1 - e / 2) / tau;
    } else {
      c = -expm1(-e) / diff_time;
    }
    load_estimate_ = c * encode_time + exp(-e) * load_estimate_;
  }

  int64_t DurationPerInputFrame(int64_t capture_time_us,
                                int64_t encode_time_us) {
    // Discard data on old frames; limit 2 seconds.
    static constexpr int64_t kMaxAge = 2 * rtc::kNumMicrosecsPerSec;
    for (auto it = max_encode_time_per_input_frame_.begin();
         it != max_encode_time_per_input_frame_.end() &&
         it->first < capture_time_us - kMaxAge;) {
      it = max_encode_time_per_input_frame_.erase(it);
    }

    std::map<int64_t, int>::iterator it;
    bool inserted;
    std::tie(it, inserted) = max_encode_time_per_input_frame_.emplace(
        capture_time_us, encode_time_us);
    if (inserted) {
      // First encoded frame for this input frame.
      return encode_time_us;
    }
    if (encode_time_us <= it->second) {
      // Shorter encode time than previous frame (unlikely). Count it as being
      // done in parallel.
      return 0;
    }
    // Record new maximum encode time, and return increase from previous max.
    int increase = encode_time_us - it->second;
    it->second = encode_time_us;
    return increase;
  }

  int Value() override {
    return static_cast<int>(100.0 * load_estimate_ + 0.5);
  }

  const CpuOveruseOptions options_;
  // Indexed by the capture timestamp, used as frame id.
  std::map<int64_t, int> max_encode_time_per_input_frame_;

  int64_t prev_time_us_ = -1;
  double load_estimate_;
};

// Class used for manual testing of overuse, enabled via field trial flag.
class OverdoseInjector : public OveruseFrameDetector::ProcessingUsage {
 public:
  OverdoseInjector(std::unique_ptr<OveruseFrameDetector::ProcessingUsage> usage,
                   int64_t normal_period_ms,
                   int64_t overuse_period_ms,
                   int64_t underuse_period_ms)
      : usage_(std::move(usage)),
        normal_period_ms_(normal_period_ms),
        overuse_period_ms_(overuse_period_ms),
        underuse_period_ms_(underuse_period_ms),
        state_(State::kNormal),
        last_toggling_ms_(-1) {
    RTC_DCHECK_GT(overuse_period_ms, 0);
    RTC_DCHECK_GT(normal_period_ms, 0);
    RTC_LOG(LS_INFO) << "Simulating overuse with intervals " << normal_period_ms
                     << "ms normal mode, " << overuse_period_ms
                     << "ms overuse mode.";
  }

  ~OverdoseInjector() override {}

  void Reset() override { usage_->Reset(); }

  void SetMaxSampleDiffMs(float diff_ms) override {
    usage_->SetMaxSampleDiffMs(diff_ms);
  }

  void FrameCaptured(const VideoFrame& frame,
                     int64_t time_when_first_seen_us,
                     int64_t last_capture_time_us) override {
    usage_->FrameCaptured(frame, time_when_first_seen_us, last_capture_time_us);
  }

  absl::optional<int> FrameSent(
      // These two argument used by old estimator.
      uint32_t timestamp,
      int64_t time_sent_in_us,
      // And these two by the new estimator.
      int64_t capture_time_us,
      absl::optional<int> encode_duration_us) override {
    return usage_->FrameSent(timestamp, time_sent_in_us, capture_time_us,
                             encode_duration_us);
  }

  int Value() override {
    int64_t now_ms = rtc::TimeMillis();
    if (last_toggling_ms_ == -1) {
      last_toggling_ms_ = now_ms;
    } else {
      switch (state_) {
        case State::kNormal:
          if (now_ms > last_toggling_ms_ + normal_period_ms_) {
            state_ = State::kOveruse;
            last_toggling_ms_ = now_ms;
            RTC_LOG(LS_INFO) << "Simulating CPU overuse.";
          }
          break;
        case State::kOveruse:
          if (now_ms > last_toggling_ms_ + overuse_period_ms_) {
            state_ = State::kUnderuse;
            last_toggling_ms_ = now_ms;
            RTC_LOG(LS_INFO) << "Simulating CPU underuse.";
          }
          break;
        case State::kUnderuse:
          if (now_ms > last_toggling_ms_ + underuse_period_ms_) {
            state_ = State::kNormal;
            last_toggling_ms_ = now_ms;
            RTC_LOG(LS_INFO) << "Actual CPU overuse measurements in effect.";
          }
          break;
      }
    }

    absl::optional<int> overried_usage_value;
    switch (state_) {
      case State::kNormal:
        break;
      case State::kOveruse:
        overried_usage_value.emplace(250);
        break;
      case State::kUnderuse:
        overried_usage_value.emplace(5);
        break;
    }

    return overried_usage_value.value_or(usage_->Value());
  }

 private:
  const std::unique_ptr<OveruseFrameDetector::ProcessingUsage> usage_;
  const int64_t normal_period_ms_;
  const int64_t overuse_period_ms_;
  const int64_t underuse_period_ms_;
  enum class State { kNormal, kOveruse, kUnderuse } state_;
  int64_t last_toggling_ms_;
};

}  // namespace

CpuOveruseOptions::CpuOveruseOptions(const FieldTrialsView& field_trials)
    : high_encode_usage_threshold_percent(85),
      frame_timeout_interval_ms(1500),
      min_frame_samples(120),
      min_process_count(3),
      high_threshold_consecutive_count(2),
      // Disabled by default.
      filter_time_ms(0) {
#if defined(WEBRTC_MAC) && !defined(WEBRTC_IOS)
  // Kill switch for re-enabling special adaptation rules for macOS.
  // TODO(bugs.webrtc.org/14138): Remove once removal is deemed safe.
  if (field_trials.IsEnabled(
          "WebRTC-MacSpecialOveruseRulesRemovalKillSwitch")) {
    // This is proof-of-concept code for letting the physical core count affect
    // the interval into which we attempt to scale. For now, the code is Mac OS
    // specific, since that's the platform were we saw most problems.
    // TODO(torbjorng): Enhance SystemInfo to return this metric.

    mach_port_t mach_host = mach_host_self();
    host_basic_info hbi = {};
    mach_msg_type_number_t info_count = HOST_BASIC_INFO_COUNT;
    kern_return_t kr =
        host_info(mach_host, HOST_BASIC_INFO,
                  reinterpret_cast<host_info_t>(&hbi), &info_count);
    mach_port_deallocate(mach_task_self(), mach_host);

    int n_physical_cores;
    if (kr != KERN_SUCCESS) {
      // If we couldn't get # of physical CPUs, don't panic. Assume we have 1.
      n_physical_cores = 1;
      RTC_LOG(LS_ERROR)
          << "Failed to determine number of physical cores, assuming 1";
    } else {
      n_physical_cores = hbi.physical_cpu;
      RTC_LOG(LS_INFO) << "Number of physical cores:" << n_physical_cores;
    }

    // Change init list default for few core systems. The assumption here is
    // that encoding, which we measure here, takes about 1/4 of the processing
    // of a two-way call. This is roughly true for x86 using both vp8 and vp9
    // without hardware encoding. Since we don't affect the incoming stream
    // here, we only control about 1/2 of the total processing needs, but this
    // is not taken into account.
    if (n_physical_cores == 1)
      high_encode_usage_threshold_percent = 20;  // Roughly 1/4 of 100%.
    else if (n_physical_cores == 2)
      high_encode_usage_threshold_percent = 40;  // Roughly 1/4 of 200%.
  }
#endif  // defined(WEBRTC_MAC) && !defined(WEBRTC_IOS)
  // Note that we make the interval 2x+epsilon wide, since libyuv scaling steps
  // are close to that (when squared). This wide interval makes sure that
  // scaling up or down does not jump all the way across the interval.
  low_encode_usage_threshold_percent =
      (high_encode_usage_threshold_percent - 1) / 2;
}

std::unique_ptr<OveruseFrameDetector::ProcessingUsage>
OveruseFrameDetector::CreateProcessingUsage(const CpuOveruseOptions& options) {
  std::unique_ptr<ProcessingUsage> instance;
  if (options.filter_time_ms > 0) {
    instance = std::make_unique<SendProcessingUsage2>(options);
  } else {
    instance = std::make_unique<SendProcessingUsage1>(options);
  }
  std::string toggling_interval =
      field_trial::FindFullName("WebRTC-ForceSimulatedOveruseIntervalMs");
  if (!toggling_interval.empty()) {
    int normal_period_ms = 0;
    int overuse_period_ms = 0;
    int underuse_period_ms = 0;
    if (sscanf(toggling_interval.c_str(), "%d-%d-%d", &normal_period_ms,
               &overuse_period_ms, &underuse_period_ms) == 3) {
      if (normal_period_ms > 0 && overuse_period_ms > 0 &&
          underuse_period_ms > 0) {
        instance = std::make_unique<OverdoseInjector>(
            std::move(instance), normal_period_ms, overuse_period_ms,
            underuse_period_ms);
      } else {
        RTC_LOG(LS_WARNING)
            << "Invalid (non-positive) normal/overuse/underuse periods: "
            << normal_period_ms << " / " << overuse_period_ms << " / "
            << underuse_period_ms;
      }
    } else {
      RTC_LOG(LS_WARNING) << "Malformed toggling interval: "
                          << toggling_interval;
    }
  }
  return instance;
}

OveruseFrameDetector::OveruseFrameDetector(
    CpuOveruseMetricsObserver* metrics_observer,
    const FieldTrialsView& field_trials)
    : options_(field_trials),
      metrics_observer_(metrics_observer),
      num_process_times_(0),
      // TODO(bugs.webrtc.org/9078): Use absl::optional
      last_capture_time_us_(-1),
      num_pixels_(0),
      max_framerate_(kDefaultFrameRate),
      last_overuse_time_ms_(-1),
      checks_above_threshold_(0),
      num_overuse_detections_(0),
      last_rampup_time_ms_(-1),
      in_quick_rampup_(false),
      current_rampup_delay_ms_(kStandardRampUpDelayMs) {
  task_checker_.Detach();
  ParseFieldTrial({&filter_time_constant_},
                  field_trial::FindFullName("WebRTC-CpuLoadEstimator"));
}

OveruseFrameDetector::~OveruseFrameDetector() {}

void OveruseFrameDetector::StartCheckForOveruse(
    TaskQueueBase* task_queue_base,
    const CpuOveruseOptions& options,
    OveruseFrameDetectorObserverInterface* overuse_observer) {
  RTC_DCHECK_RUN_ON(&task_checker_);
  RTC_DCHECK(!check_overuse_task_.Running());
  RTC_DCHECK(overuse_observer != nullptr);

  SetOptions(options);
  check_overuse_task_ = RepeatingTaskHandle::DelayedStart(
      task_queue_base, TimeDelta::Millis(kTimeToFirstCheckForOveruseMs),
      [this, overuse_observer] {
        CheckForOveruse(overuse_observer);
        return TimeDelta::Millis(kCheckForOveruseIntervalMs);
      });
}
void OveruseFrameDetector::StopCheckForOveruse() {
  RTC_DCHECK_RUN_ON(&task_checker_);
  check_overuse_task_.Stop();
}

void OveruseFrameDetector::EncodedFrameTimeMeasured(int encode_duration_ms) {
  RTC_DCHECK_RUN_ON(&task_checker_);
  encode_usage_percent_ = usage_->Value();

  metrics_observer_->OnEncodedFrameTimeMeasured(encode_duration_ms,
                                                *encode_usage_percent_);
}

bool OveruseFrameDetector::FrameSizeChanged(int num_pixels) const {
  RTC_DCHECK_RUN_ON(&task_checker_);
  if (num_pixels != num_pixels_) {
    return true;
  }
  return false;
}

bool OveruseFrameDetector::FrameTimeoutDetected(int64_t now_us) const {
  RTC_DCHECK_RUN_ON(&task_checker_);
  if (last_capture_time_us_ == -1)
    return false;
  return (now_us - last_capture_time_us_) >
         options_.frame_timeout_interval_ms * rtc::kNumMicrosecsPerMillisec;
}

void OveruseFrameDetector::ResetAll(int num_pixels) {
  // Reset state, as a result resolution being changed. Do not however change
  // the current frame rate back to the default.
  RTC_DCHECK_RUN_ON(&task_checker_);
  num_pixels_ = num_pixels;
  usage_->Reset();
  last_capture_time_us_ = -1;
  num_process_times_ = 0;
  encode_usage_percent_ = absl::nullopt;
  OnTargetFramerateUpdated(max_framerate_);
}

void OveruseFrameDetector::OnTargetFramerateUpdated(int framerate_fps) {
  RTC_DCHECK_RUN_ON(&task_checker_);
  RTC_DCHECK_GE(framerate_fps, 0);
  max_framerate_ = std::min(kMaxFramerate, framerate_fps);
  usage_->SetMaxSampleDiffMs((1000 / std::max(kMinFramerate, max_framerate_)) *
                             kMaxSampleDiffMarginFactor);
}

void OveruseFrameDetector::FrameCaptured(const VideoFrame& frame,
                                         int64_t time_when_first_seen_us) {
  RTC_DCHECK_RUN_ON(&task_checker_);

  if (FrameSizeChanged(frame.width() * frame.height()) ||
      FrameTimeoutDetected(time_when_first_seen_us)) {
    ResetAll(frame.width() * frame.height());
  }

  usage_->FrameCaptured(frame, time_when_first_seen_us, last_capture_time_us_);
  last_capture_time_us_ = time_when_first_seen_us;
}

void OveruseFrameDetector::FrameSent(uint32_t timestamp,
                                     int64_t time_sent_in_us,
                                     int64_t capture_time_us,
                                     absl::optional<int> encode_duration_us) {
  RTC_DCHECK_RUN_ON(&task_checker_);
  encode_duration_us = usage_->FrameSent(timestamp, time_sent_in_us,
                                         capture_time_us, encode_duration_us);

  if (encode_duration_us) {
    EncodedFrameTimeMeasured(*encode_duration_us /
                             rtc::kNumMicrosecsPerMillisec);
  }
}

void OveruseFrameDetector::CheckForOveruse(
    OveruseFrameDetectorObserverInterface* observer) {
  RTC_DCHECK_RUN_ON(&task_checker_);
  RTC_DCHECK(observer);
  ++num_process_times_;
  if (num_process_times_ <= options_.min_process_count ||
      !encode_usage_percent_)
    return;

  int64_t now_ms = rtc::TimeMillis();

  if (IsOverusing(*encode_usage_percent_)) {
    // If the last thing we did was going up, and now have to back down, we need
    // to check if this peak was short. If so we should back off to avoid going
    // back and forth between this load, the system doesn't seem to handle it.
    bool check_for_backoff = last_rampup_time_ms_ > last_overuse_time_ms_;
    if (check_for_backoff) {
      if (now_ms - last_rampup_time_ms_ < kStandardRampUpDelayMs ||
          num_overuse_detections_ > kMaxOverusesBeforeApplyRampupDelay) {
        // Going up was not ok for very long, back off.
        current_rampup_delay_ms_ *= kRampUpBackoffFactor;
        if (current_rampup_delay_ms_ > kMaxRampUpDelayMs)
          current_rampup_delay_ms_ = kMaxRampUpDelayMs;
      } else {
        // Not currently backing off, reset rampup delay.
        current_rampup_delay_ms_ = kStandardRampUpDelayMs;
      }
    }

    last_overuse_time_ms_ = now_ms;
    in_quick_rampup_ = false;
    checks_above_threshold_ = 0;
    ++num_overuse_detections_;

    observer->AdaptDown();
  } else if (IsUnderusing(*encode_usage_percent_, now_ms)) {
    last_rampup_time_ms_ = now_ms;
    in_quick_rampup_ = true;

    observer->AdaptUp();
  }

  int rampup_delay =
      in_quick_rampup_ ? kQuickRampUpDelayMs : current_rampup_delay_ms_;

  RTC_LOG(LS_VERBOSE) << " Frame stats: "
                         " encode usage "
                      << *encode_usage_percent_ << " overuse detections "
                      << num_overuse_detections_ << " rampup delay "
                      << rampup_delay;
}

void OveruseFrameDetector::SetOptions(const CpuOveruseOptions& options) {
  RTC_DCHECK_RUN_ON(&task_checker_);
  options_ = options;

  // Time constant config overridable by field trial.
  if (filter_time_constant_) {
    options_.filter_time_ms = filter_time_constant_->ms();
  }
  // Force reset with next frame.
  num_pixels_ = 0;
  usage_ = CreateProcessingUsage(options);
}

bool OveruseFrameDetector::IsOverusing(int usage_percent) {
  RTC_DCHECK_RUN_ON(&task_checker_);

  if (usage_percent >= options_.high_encode_usage_threshold_percent) {
    ++checks_above_threshold_;
  } else {
    checks_above_threshold_ = 0;
  }
  return checks_above_threshold_ >= options_.high_threshold_consecutive_count;
}

bool OveruseFrameDetector::IsUnderusing(int usage_percent, int64_t time_now) {
  RTC_DCHECK_RUN_ON(&task_checker_);
  int delay = in_quick_rampup_ ? kQuickRampUpDelayMs : current_rampup_delay_ms_;
  if (time_now < last_rampup_time_ms_ + delay)
    return false;

  return usage_percent < options_.low_encode_usage_threshold_percent;
}
}  // namespace webrtc
