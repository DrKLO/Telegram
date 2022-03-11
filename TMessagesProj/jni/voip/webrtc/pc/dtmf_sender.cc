/*
 *  Copyright 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "pc/dtmf_sender.h"

#include <ctype.h>
#include <string.h>

#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/ref_counted_object.h"
#include "rtc_base/task_utils/to_queued_task.h"
#include "rtc_base/thread.h"

namespace webrtc {

// RFC4733
//  +-------+--------+------+---------+
//  | Event | Code   | Type | Volume? |
//  +-------+--------+------+---------+
//  | 0--9  | 0--9   | tone | yes     |
//  | *     | 10     | tone | yes     |
//  | #     | 11     | tone | yes     |
//  | A--D  | 12--15 | tone | yes     |
//  +-------+--------+------+---------+
// The "," is a special event defined by the WebRTC spec. It means to delay for
// 2 seconds before processing the next tone. We use -1 as its code.
static const int kDtmfCommaDelay = -1;
static const char kDtmfValidTones[] = ",0123456789*#ABCDabcd";
static const char kDtmfTonesTable[] = ",0123456789*#ABCD";
// The duration cannot be more than 6000ms or less than 40ms. The gap between
// tones must be at least 50 ms.
// Source for values: W3C WEBRTC specification.
// https://w3c.github.io/webrtc-pc/#dom-rtcdtmfsender-insertdtmf
static const int kDtmfDefaultDurationMs = 100;
static const int kDtmfMinDurationMs = 40;
static const int kDtmfMaxDurationMs = 6000;
static const int kDtmfDefaultGapMs = 50;
static const int kDtmfMinGapMs = 30;

// Get DTMF code from the DTMF event character.
bool GetDtmfCode(char tone, int* code) {
  // Convert a-d to A-D.
  char event = toupper(tone);
  const char* p = strchr(kDtmfTonesTable, event);
  if (!p) {
    return false;
  }
  *code = p - kDtmfTonesTable - 1;
  return true;
}

rtc::scoped_refptr<DtmfSender> DtmfSender::Create(
    rtc::Thread* signaling_thread,
    DtmfProviderInterface* provider) {
  if (!signaling_thread) {
    return nullptr;
  }
  return rtc::make_ref_counted<DtmfSender>(signaling_thread, provider);
}

DtmfSender::DtmfSender(rtc::Thread* signaling_thread,
                       DtmfProviderInterface* provider)
    : observer_(nullptr),
      signaling_thread_(signaling_thread),
      provider_(provider),
      duration_(kDtmfDefaultDurationMs),
      inter_tone_gap_(kDtmfDefaultGapMs),
      comma_delay_(kDtmfDefaultCommaDelayMs) {
  RTC_DCHECK(signaling_thread_);
  if (provider_) {
    RTC_DCHECK(provider_->GetOnDestroyedSignal());
    provider_->GetOnDestroyedSignal()->connect(
        this, &DtmfSender::OnProviderDestroyed);
  }
}

DtmfSender::~DtmfSender() {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  StopSending();
}

void DtmfSender::RegisterObserver(DtmfSenderObserverInterface* observer) {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  observer_ = observer;
}

void DtmfSender::UnregisterObserver() {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  observer_ = nullptr;
}

bool DtmfSender::CanInsertDtmf() {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  if (!provider_) {
    return false;
  }
  return provider_->CanInsertDtmf();
}

bool DtmfSender::InsertDtmf(const std::string& tones,
                            int duration,
                            int inter_tone_gap,
                            int comma_delay) {
  RTC_DCHECK_RUN_ON(signaling_thread_);

  if (duration > kDtmfMaxDurationMs || duration < kDtmfMinDurationMs ||
      inter_tone_gap < kDtmfMinGapMs || comma_delay < kDtmfMinGapMs) {
    RTC_LOG(LS_ERROR)
        << "InsertDtmf is called with invalid duration or tones gap. "
           "The duration cannot be more than "
        << kDtmfMaxDurationMs << "ms or less than " << kDtmfMinDurationMs
        << "ms. The gap between tones must be at least " << kDtmfMinGapMs
        << "ms.";
    return false;
  }

  if (!CanInsertDtmf()) {
    RTC_LOG(LS_ERROR)
        << "InsertDtmf is called on DtmfSender that can't send DTMF.";
    return false;
  }

  tones_ = tones;
  duration_ = duration;
  inter_tone_gap_ = inter_tone_gap;
  comma_delay_ = comma_delay;

  // Cancel any remaining tasks for previous tones.
  if (safety_flag_) {
    safety_flag_->SetNotAlive();
  }
  safety_flag_ = PendingTaskSafetyFlag::Create();
  // Kick off a new DTMF task.
  QueueInsertDtmf(RTC_FROM_HERE, 1 /*ms*/);
  return true;
}

std::string DtmfSender::tones() const {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  return tones_;
}

int DtmfSender::duration() const {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  return duration_;
}

int DtmfSender::inter_tone_gap() const {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  return inter_tone_gap_;
}

int DtmfSender::comma_delay() const {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  return comma_delay_;
}

void DtmfSender::QueueInsertDtmf(const rtc::Location& posted_from,
                                 uint32_t delay_ms) {
  signaling_thread_->PostDelayedHighPrecisionTask(
      ToQueuedTask(safety_flag_,
                   [this] {
                     RTC_DCHECK_RUN_ON(signaling_thread_);
                     DoInsertDtmf();
                   }),
      delay_ms);
}

void DtmfSender::DoInsertDtmf() {
  // Get the first DTMF tone from the tone buffer. Unrecognized characters will
  // be ignored and skipped.
  size_t first_tone_pos = tones_.find_first_of(kDtmfValidTones);
  int code = 0;
  if (first_tone_pos == std::string::npos) {
    tones_.clear();
    // Fire a “OnToneChange” event with an empty string and stop.
    if (observer_) {
      observer_->OnToneChange(std::string(), tones_);
      observer_->OnToneChange(std::string());
    }
    return;
  } else {
    char tone = tones_[first_tone_pos];
    if (!GetDtmfCode(tone, &code)) {
      // The find_first_of(kDtmfValidTones) should have guarantee `tone` is
      // a valid DTMF tone.
      RTC_DCHECK_NOTREACHED();
    }
  }

  int tone_gap = inter_tone_gap_;
  if (code == kDtmfCommaDelay) {
    // Special case defined by WebRTC - By default, the character ',' indicates
    // a delay of 2 seconds before processing the next character in the tones
    // parameter. The comma delay can be set to a non default value via
    // InsertDtmf to comply with legacy WebRTC clients.
    tone_gap = comma_delay_;
  } else {
    if (!provider_) {
      RTC_LOG(LS_ERROR) << "The DtmfProvider has been destroyed.";
      return;
    }
    // The provider starts playout of the given tone on the
    // associated RTP media stream, using the appropriate codec.
    if (!provider_->InsertDtmf(code, duration_)) {
      RTC_LOG(LS_ERROR) << "The DtmfProvider can no longer send DTMF.";
      return;
    }
    // Wait for the number of milliseconds specified by `duration_`.
    tone_gap += duration_;
  }

  // Fire a “OnToneChange” event with the tone that's just processed.
  if (observer_) {
    observer_->OnToneChange(tones_.substr(first_tone_pos, 1),
                            tones_.substr(first_tone_pos + 1));
    observer_->OnToneChange(tones_.substr(first_tone_pos, 1));
  }

  // Erase the unrecognized characters plus the tone that's just processed.
  tones_.erase(0, first_tone_pos + 1);

  // Continue with the next tone.
  QueueInsertDtmf(RTC_FROM_HERE, tone_gap);
}

void DtmfSender::OnProviderDestroyed() {
  RTC_DCHECK_RUN_ON(signaling_thread_);

  RTC_LOG(LS_INFO) << "The Dtmf provider is deleted. Clear the sending queue.";
  StopSending();
  provider_ = nullptr;
}

void DtmfSender::StopSending() {
  if (safety_flag_) {
    safety_flag_->SetNotAlive();
  }
}

}  // namespace webrtc
