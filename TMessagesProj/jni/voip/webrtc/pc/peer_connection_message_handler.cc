/*
 *  Copyright 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "pc/peer_connection_message_handler.h"

#include <utility>

#include "api/jsep.h"
#include "api/legacy_stats_types.h"
#include "api/media_stream_interface.h"
#include "api/peer_connection_interface.h"
#include "api/scoped_refptr.h"
#include "api/sequence_checker.h"
#include "api/task_queue/pending_task_safety_flag.h"
#include "pc/legacy_stats_collector_interface.h"
#include "rtc_base/checks.h"

namespace webrtc {
namespace {

template <typename T>
rtc::scoped_refptr<T> WrapScoped(T* ptr) {
  return rtc::scoped_refptr<T>(ptr);
}

}  // namespace

void PeerConnectionMessageHandler::PostSetSessionDescriptionSuccess(
    SetSessionDescriptionObserver* observer) {
  signaling_thread_->PostTask(
      SafeTask(safety_.flag(),
               [observer = WrapScoped(observer)] { observer->OnSuccess(); }));
}

void PeerConnectionMessageHandler::PostSetSessionDescriptionFailure(
    SetSessionDescriptionObserver* observer,
    RTCError&& error) {
  RTC_DCHECK(!error.ok());
  signaling_thread_->PostTask(SafeTask(
      safety_.flag(),
      [observer = WrapScoped(observer), error = std::move(error)]() mutable {
        observer->OnFailure(std::move(error));
      }));
}

void PeerConnectionMessageHandler::PostCreateSessionDescriptionFailure(
    CreateSessionDescriptionObserver* observer,
    RTCError error) {
  RTC_DCHECK(!error.ok());
  // Do not protect this task with the safety_.flag() to ensure
  // observer is invoked even if the PeerConnection is destroyed early.
  signaling_thread_->PostTask(
      [observer = WrapScoped(observer), error = std::move(error)]() mutable {
        observer->OnFailure(std::move(error));
      });
}

void PeerConnectionMessageHandler::PostGetStats(
    StatsObserver* observer,
    LegacyStatsCollectorInterface* legacy_stats,
    MediaStreamTrackInterface* track) {
  signaling_thread_->PostTask(
      SafeTask(safety_.flag(), [observer = WrapScoped(observer), legacy_stats,
                                track = WrapScoped(track)] {
        StatsReports reports;
        legacy_stats->GetStats(track.get(), &reports);
        observer->OnComplete(reports);
      }));
}

void PeerConnectionMessageHandler::RequestUsagePatternReport(
    std::function<void()> func,
    int delay_ms) {
  signaling_thread_->PostDelayedTask(SafeTask(safety_.flag(), std::move(func)),
                                     TimeDelta::Millis(delay_ms));
}

}  // namespace webrtc
