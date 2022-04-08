/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_coding/utility/quality_scaler.h"

#include <memory>
#include <utility>

#include "api/video/video_adaptation_reason.h"
#include "rtc_base/checks.h"
#include "rtc_base/experiments/quality_scaler_settings.h"
#include "rtc_base/logging.h"
#include "rtc_base/numerics/exp_filter.h"
#include "rtc_base/task_queue.h"
#include "rtc_base/task_utils/to_queued_task.h"
#include "rtc_base/weak_ptr.h"

// TODO(kthelgason): Some versions of Android have issues with log2.
// See https://code.google.com/p/android/issues/detail?id=212634 for details
#if defined(WEBRTC_ANDROID)
#define log2(x) (log(x) / log(2))
#endif

namespace webrtc {

namespace {
// TODO(nisse): Delete, delegate to encoders.
// Threshold constant used until first downscale (to permit fast rampup).
static const int kMeasureMs = 2000;
static const float kSamplePeriodScaleFactor = 2.5;
static const int kFramedropPercentThreshold = 60;
static const size_t kMinFramesNeededToScale = 2 * 30;

}  // namespace

class QualityScaler::QpSmoother {
 public:
  explicit QpSmoother(float alpha)
      : alpha_(alpha),
        // The initial value of last_sample_ms doesn't matter since the smoother
        // will ignore the time delta for the first update.
        last_sample_ms_(0),
        smoother_(alpha) {}

  absl::optional<int> GetAvg() const {
    float value = smoother_.filtered();
    if (value == rtc::ExpFilter::kValueUndefined) {
      return absl::nullopt;
    }
    return static_cast<int>(value);
  }

  void Add(float sample, int64_t time_sent_us) {
    int64_t now_ms = time_sent_us / 1000;
    smoother_.Apply(static_cast<float>(now_ms - last_sample_ms_), sample);
    last_sample_ms_ = now_ms;
  }

  void Reset() { smoother_.Reset(alpha_); }

 private:
  const float alpha_;
  int64_t last_sample_ms_;
  rtc::ExpFilter smoother_;
};

// The QualityScaler checks for QP periodically by queuing CheckQpTasks. The
// task will either run to completion and trigger a new task being queued, or it
// will be destroyed because the QualityScaler is destroyed.
//
// When high or low QP is reported, the task will be pending until a callback is
// invoked. This lets the QualityScalerQpUsageHandlerInterface react to QP usage
// asynchronously and prevents checking for QP until the stream has potentially
// been reconfigured.
class QualityScaler::CheckQpTask {
 public:
  // The result of one CheckQpTask may influence the delay of the next
  // CheckQpTask.
  struct Result {
    bool observed_enough_frames = false;
    bool qp_usage_reported = false;
  };

  CheckQpTask(QualityScaler* quality_scaler, Result previous_task_result)
      : quality_scaler_(quality_scaler),
        state_(State::kNotStarted),
        previous_task_result_(previous_task_result),
        weak_ptr_factory_(this) {}

  void StartDelayedTask() {
    RTC_DCHECK_EQ(state_, State::kNotStarted);
    state_ = State::kCheckingQp;
    TaskQueueBase::Current()->PostDelayedTask(
        ToQueuedTask([this_weak_ptr = weak_ptr_factory_.GetWeakPtr(), this] {
          if (!this_weak_ptr) {
            // The task has been cancelled through destruction.
            return;
          }
          RTC_DCHECK_EQ(state_, State::kCheckingQp);
          RTC_DCHECK_RUN_ON(&quality_scaler_->task_checker_);
          switch (quality_scaler_->CheckQp()) {
            case QualityScaler::CheckQpResult::kInsufficientSamples: {
              result_.observed_enough_frames = false;
              // After this line, `this` may be deleted.
              break;
            }
            case QualityScaler::CheckQpResult::kNormalQp: {
              result_.observed_enough_frames = true;
              break;
            }
            case QualityScaler::CheckQpResult::kHighQp: {
              result_.observed_enough_frames = true;
              result_.qp_usage_reported = true;
              quality_scaler_->fast_rampup_ = false;
              quality_scaler_->handler_->OnReportQpUsageHigh();
              quality_scaler_->ClearSamples();
              break;
            }
            case QualityScaler::CheckQpResult::kLowQp: {
              result_.observed_enough_frames = true;
              result_.qp_usage_reported = true;
              quality_scaler_->handler_->OnReportQpUsageLow();
              quality_scaler_->ClearSamples();
              break;
            }
          }
          state_ = State::kCompleted;
          // Starting the next task deletes the pending task. After this line,
          // `this` has been deleted.
          quality_scaler_->StartNextCheckQpTask();
        }),
        GetCheckingQpDelayMs());
  }

  bool HasCompletedTask() const { return state_ == State::kCompleted; }

  Result result() const {
    RTC_DCHECK(HasCompletedTask());
    return result_;
  }

 private:
  enum class State {
    kNotStarted,
    kCheckingQp,
    kCompleted,
  };

  // Determines the sampling period of CheckQpTasks.
  int64_t GetCheckingQpDelayMs() const {
    RTC_DCHECK_RUN_ON(&quality_scaler_->task_checker_);
    if (quality_scaler_->fast_rampup_) {
      return quality_scaler_->sampling_period_ms_;
    }
    if (quality_scaler_->experiment_enabled_ &&
        !previous_task_result_.observed_enough_frames) {
      // Use half the interval while waiting for enough frames.
      return quality_scaler_->sampling_period_ms_ / 2;
    }
    if (quality_scaler_->scale_factor_ &&
        !previous_task_result_.qp_usage_reported) {
      // Last CheckQp did not call AdaptDown/Up, possibly reduce interval.
      return quality_scaler_->sampling_period_ms_ *
             quality_scaler_->scale_factor_.value();
    }
    return quality_scaler_->sampling_period_ms_ *
           quality_scaler_->initial_scale_factor_;
  }

  QualityScaler* const quality_scaler_;
  State state_;
  const Result previous_task_result_;
  Result result_;

  rtc::WeakPtrFactory<CheckQpTask> weak_ptr_factory_;
};

QualityScaler::QualityScaler(QualityScalerQpUsageHandlerInterface* handler,
                             VideoEncoder::QpThresholds thresholds)
    : QualityScaler(handler, thresholds, kMeasureMs) {}

// Protected ctor, should not be called directly.
QualityScaler::QualityScaler(QualityScalerQpUsageHandlerInterface* handler,
                             VideoEncoder::QpThresholds thresholds,
                             int64_t default_sampling_period_ms)
    : handler_(handler),
      thresholds_(thresholds),
      sampling_period_ms_(QualityScalerSettings::ParseFromFieldTrials()
                              .SamplingPeriodMs()
                              .value_or(default_sampling_period_ms)),
      fast_rampup_(true),
      // Arbitrarily choose size based on 30 fps for 5 seconds.
      average_qp_(QualityScalerSettings::ParseFromFieldTrials()
                      .AverageQpWindow()
                      .value_or(5 * 30)),
      framedrop_percent_media_opt_(5 * 30),
      framedrop_percent_all_(5 * 30),
      experiment_enabled_(QualityScalingExperiment::Enabled()),
      min_frames_needed_(
          QualityScalerSettings::ParseFromFieldTrials().MinFrames().value_or(
              kMinFramesNeededToScale)),
      initial_scale_factor_(QualityScalerSettings::ParseFromFieldTrials()
                                .InitialScaleFactor()
                                .value_or(kSamplePeriodScaleFactor)),
      scale_factor_(
          QualityScalerSettings::ParseFromFieldTrials().ScaleFactor()) {
  RTC_DCHECK_RUN_ON(&task_checker_);
  if (experiment_enabled_) {
    config_ = QualityScalingExperiment::GetConfig();
    qp_smoother_high_.reset(new QpSmoother(config_.alpha_high));
    qp_smoother_low_.reset(new QpSmoother(config_.alpha_low));
  }
  RTC_DCHECK(handler_ != nullptr);
  StartNextCheckQpTask();
  RTC_LOG(LS_INFO) << "QP thresholds: low: " << thresholds_.low
                   << ", high: " << thresholds_.high;
}

QualityScaler::~QualityScaler() {
  RTC_DCHECK_RUN_ON(&task_checker_);
}

void QualityScaler::StartNextCheckQpTask() {
  RTC_DCHECK_RUN_ON(&task_checker_);
  RTC_DCHECK(!pending_qp_task_ || pending_qp_task_->HasCompletedTask())
      << "A previous CheckQpTask has not completed yet!";
  CheckQpTask::Result previous_task_result;
  if (pending_qp_task_) {
    previous_task_result = pending_qp_task_->result();
  }
  pending_qp_task_ = std::make_unique<CheckQpTask>(this, previous_task_result);
  pending_qp_task_->StartDelayedTask();
}

void QualityScaler::SetQpThresholds(VideoEncoder::QpThresholds thresholds) {
  RTC_DCHECK_RUN_ON(&task_checker_);
  thresholds_ = thresholds;
}

void QualityScaler::ReportDroppedFrameByMediaOpt() {
  RTC_DCHECK_RUN_ON(&task_checker_);
  framedrop_percent_media_opt_.AddSample(100);
  framedrop_percent_all_.AddSample(100);
}

void QualityScaler::ReportDroppedFrameByEncoder() {
  RTC_DCHECK_RUN_ON(&task_checker_);
  framedrop_percent_all_.AddSample(100);
}

void QualityScaler::ReportQp(int qp, int64_t time_sent_us) {
  RTC_DCHECK_RUN_ON(&task_checker_);
  framedrop_percent_media_opt_.AddSample(0);
  framedrop_percent_all_.AddSample(0);
  average_qp_.AddSample(qp);
  if (qp_smoother_high_)
    qp_smoother_high_->Add(qp, time_sent_us);
  if (qp_smoother_low_)
    qp_smoother_low_->Add(qp, time_sent_us);
}

bool QualityScaler::QpFastFilterLow() const {
  RTC_DCHECK_RUN_ON(&task_checker_);
  size_t num_frames = config_.use_all_drop_reasons
                          ? framedrop_percent_all_.Size()
                          : framedrop_percent_media_opt_.Size();
  const size_t kMinNumFrames = 10;
  if (num_frames < kMinNumFrames) {
    return false;  // Wait for more frames before making a decision.
  }
  absl::optional<int> avg_qp_high = qp_smoother_high_
                                        ? qp_smoother_high_->GetAvg()
                                        : average_qp_.GetAverageRoundedDown();
  return (avg_qp_high) ? (avg_qp_high.value() <= thresholds_.low) : false;
}

QualityScaler::CheckQpResult QualityScaler::CheckQp() const {
  RTC_DCHECK_RUN_ON(&task_checker_);
  // Should be set through InitEncode -> Should be set by now.
  RTC_DCHECK_GE(thresholds_.low, 0);

  // If we have not observed at least this many frames we can't make a good
  // scaling decision.
  const size_t frames = config_.use_all_drop_reasons
                            ? framedrop_percent_all_.Size()
                            : framedrop_percent_media_opt_.Size();
  if (frames < min_frames_needed_) {
    return CheckQpResult::kInsufficientSamples;
  }

  // Check if we should scale down due to high frame drop.
  const absl::optional<int> drop_rate =
      config_.use_all_drop_reasons
          ? framedrop_percent_all_.GetAverageRoundedDown()
          : framedrop_percent_media_opt_.GetAverageRoundedDown();
  if (drop_rate && *drop_rate >= kFramedropPercentThreshold) {
    RTC_LOG(LS_INFO) << "Reporting high QP, framedrop percent " << *drop_rate;
    return CheckQpResult::kHighQp;
  }

  // Check if we should scale up or down based on QP.
  const absl::optional<int> avg_qp_high =
      qp_smoother_high_ ? qp_smoother_high_->GetAvg()
                        : average_qp_.GetAverageRoundedDown();
  const absl::optional<int> avg_qp_low =
      qp_smoother_low_ ? qp_smoother_low_->GetAvg()
                       : average_qp_.GetAverageRoundedDown();
  if (avg_qp_high && avg_qp_low) {
    RTC_LOG(LS_INFO) << "Checking average QP " << *avg_qp_high << " ("
                     << *avg_qp_low << ").";
    if (*avg_qp_high > thresholds_.high) {
      return CheckQpResult::kHighQp;
    }
    if (*avg_qp_low <= thresholds_.low) {
      // QP has been low. We want to try a higher resolution.
      return CheckQpResult::kLowQp;
    }
  }
  return CheckQpResult::kNormalQp;
}

void QualityScaler::ClearSamples() {
  RTC_DCHECK_RUN_ON(&task_checker_);
  framedrop_percent_media_opt_.Reset();
  framedrop_percent_all_.Reset();
  average_qp_.Reset();
  if (qp_smoother_high_)
    qp_smoother_high_->Reset();
  if (qp_smoother_low_)
    qp_smoother_low_->Reset();
}

QualityScalerQpUsageHandlerInterface::~QualityScalerQpUsageHandlerInterface() {}

}  // namespace webrtc
