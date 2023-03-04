/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/remote_bitrate_estimator/test/bwe_test_logging.h"

#if BWE_TEST_LOGGING_COMPILE_TIME_ENABLE

#include <inttypes.h>
#include <stdarg.h>
#include <stdio.h>

#include <algorithm>

#include "rtc_base/checks.h"
#include "rtc_base/platform_thread.h"
#include "rtc_base/strings/string_builder.h"

namespace webrtc {
namespace testing {
namespace bwe {

static std::string ToString(uint32_t v) {
  rtc::StringBuilder ss;
  ss << v;
  return ss.Release();
}

Logging::ThreadState::ThreadState() = default;
Logging::ThreadState::~ThreadState() = default;

Logging::Context::Context(uint32_t name, int64_t timestamp_ms, bool enabled) {
  Logging::GetInstance()->PushState(ToString(name), timestamp_ms, enabled);
}

Logging::Context::Context(const std::string& name,
                          int64_t timestamp_ms,
                          bool enabled) {
  Logging::GetInstance()->PushState(name, timestamp_ms, enabled);
}

Logging::Context::Context(const char* name,
                          int64_t timestamp_ms,
                          bool enabled) {
  Logging::GetInstance()->PushState(name, timestamp_ms, enabled);
}

Logging::Context::~Context() {
  Logging::GetInstance()->PopState();
}

Logging* Logging::GetInstance() {
  static Logging* logging = new Logging();
  return logging;
}

void Logging::SetGlobalContext(uint32_t name) {
  MutexLock lock(&mutex_);
  thread_map_[rtc::CurrentThreadId()].global_state.tag = ToString(name);
}

void Logging::SetGlobalContext(const std::string& name) {
  MutexLock lock(&mutex_);
  thread_map_[rtc::CurrentThreadId()].global_state.tag = name;
}

void Logging::SetGlobalContext(const char* name) {
  MutexLock lock(&mutex_);
  thread_map_[rtc::CurrentThreadId()].global_state.tag = name;
}

void Logging::SetGlobalEnable(bool enabled) {
  MutexLock lock(&mutex_);
  thread_map_[rtc::CurrentThreadId()].global_state.enabled = enabled;
}

void Logging::Log(const char format[], ...) {
  MutexLock lock(&mutex_);
  ThreadMap::iterator it = thread_map_.find(rtc::CurrentThreadId());
  RTC_DCHECK(it != thread_map_.end());
  const State& state = it->second.stack.top();
  if (state.enabled) {
    printf("%s\t", state.tag.c_str());
    va_list args;
    va_start(args, format);
    vprintf(format, args);
    va_end(args);
    printf("\n");
  }
}

void Logging::Plot(int figure, const std::string& name, double value) {
  Plot(figure, name, value, 0, "-");
}

void Logging::Plot(int figure,
                   const std::string& name,
                   double value,
                   uint32_t ssrc) {
  Plot(figure, name, value, ssrc, "-");
}

void Logging::Plot(int figure,
                   const std::string& name,
                   double value,
                   const std::string& alg_name) {
  Plot(figure, name, value, 0, alg_name);
}

void Logging::Plot(int figure,
                   const std::string& name,
                   double value,
                   uint32_t ssrc,
                   const std::string& alg_name) {
  MutexLock lock(&mutex_);
  ThreadMap::iterator it = thread_map_.find(rtc::CurrentThreadId());
  RTC_DCHECK(it != thread_map_.end());
  const State& state = it->second.stack.top();
  if (state.enabled) {
    printf("PLOT\t%d\t%s:%" PRIu32 "@%s\t%f\t%f\n", figure, name.c_str(), ssrc,
           alg_name.c_str(), state.timestamp_ms * 0.001, value);
  }
}

void Logging::PlotBar(int figure,
                      const std::string& name,
                      double value,
                      int flow_id) {
  MutexLock lock(&mutex_);
  ThreadMap::iterator it = thread_map_.find(rtc::CurrentThreadId());
  RTC_DCHECK(it != thread_map_.end());
  const State& state = it->second.stack.top();
  if (state.enabled) {
    printf("BAR\t%d\t%s_%d\t%f\n", figure, name.c_str(), flow_id, value);
  }
}

void Logging::PlotBaselineBar(int figure,
                              const std::string& name,
                              double value,
                              int flow_id) {
  MutexLock lock(&mutex_);
  ThreadMap::iterator it = thread_map_.find(rtc::CurrentThreadId());
  RTC_DCHECK(it != thread_map_.end());
  const State& state = it->second.stack.top();
  if (state.enabled) {
    printf("BASELINE\t%d\t%s_%d\t%f\n", figure, name.c_str(), flow_id, value);
  }
}

void Logging::PlotErrorBar(int figure,
                           const std::string& name,
                           double value,
                           double ylow,
                           double yhigh,
                           const std::string& error_title,
                           int flow_id) {
  MutexLock lock(&mutex_);
  ThreadMap::iterator it = thread_map_.find(rtc::CurrentThreadId());
  RTC_DCHECK(it != thread_map_.end());
  const State& state = it->second.stack.top();
  if (state.enabled) {
    printf("ERRORBAR\t%d\t%s_%d\t%f\t%f\t%f\t%s\n", figure, name.c_str(),
           flow_id, value, ylow, yhigh, error_title.c_str());
  }
}

void Logging::PlotLimitErrorBar(int figure,
                                const std::string& name,
                                double value,
                                double ylow,
                                double yhigh,
                                const std::string& error_title,
                                double ymax,
                                const std::string& limit_title,
                                int flow_id) {
  MutexLock lock(&mutex_);
  ThreadMap::iterator it = thread_map_.find(rtc::CurrentThreadId());
  RTC_DCHECK(it != thread_map_.end());
  const State& state = it->second.stack.top();
  if (state.enabled) {
    printf("LIMITERRORBAR\t%d\t%s_%d\t%f\t%f\t%f\t%s\t%f\t%s\n", figure,
           name.c_str(), flow_id, value, ylow, yhigh, error_title.c_str(), ymax,
           limit_title.c_str());
  }
}

void Logging::PlotLabel(int figure,
                        const std::string& title,
                        const std::string& y_label,
                        int num_flows) {
  MutexLock lock(&mutex_);
  ThreadMap::iterator it = thread_map_.find(rtc::CurrentThreadId());
  RTC_DCHECK(it != thread_map_.end());
  const State& state = it->second.stack.top();
  if (state.enabled) {
    printf("LABEL\t%d\t%s\t%s\t%d\n", figure, title.c_str(), y_label.c_str(),
           num_flows);
  }
}

Logging::Logging() : thread_map_() {}

Logging::~Logging() = default;

Logging::State::State() : tag(""), timestamp_ms(0), enabled(true) {}

Logging::State::State(const std::string& tag,
                      int64_t timestamp_ms,
                      bool enabled)
    : tag(tag), timestamp_ms(timestamp_ms), enabled(enabled) {}

void Logging::State::MergePrevious(const State& previous) {
  if (tag.empty()) {
    tag = previous.tag;
  } else if (!previous.tag.empty()) {
    tag = previous.tag + "_" + tag;
  }
  timestamp_ms = std::max(previous.timestamp_ms, timestamp_ms);
  enabled = previous.enabled && enabled;
}

void Logging::PushState(const std::string& append_to_tag,
                        int64_t timestamp_ms,
                        bool enabled) {
  MutexLock lock(&mutex_);
  State new_state(append_to_tag, timestamp_ms, enabled);
  ThreadState* thread_state = &thread_map_[rtc::CurrentThreadId()];
  std::stack<State>* stack = &thread_state->stack;
  if (stack->empty()) {
    new_state.MergePrevious(thread_state->global_state);
  } else {
    new_state.MergePrevious(stack->top());
  }
  stack->push(new_state);
}

void Logging::PopState() {
  MutexLock lock(&mutex_);
  ThreadMap::iterator it = thread_map_.find(rtc::CurrentThreadId());
  RTC_DCHECK(it != thread_map_.end());
  std::stack<State>* stack = &it->second.stack;
  int64_t newest_timestamp_ms = stack->top().timestamp_ms;
  stack->pop();
  if (!stack->empty()) {
    State* state = &stack->top();
    // Update time so that next log/plot will use the latest time seen so far
    // in this call tree.
    state->timestamp_ms = std::max(state->timestamp_ms, newest_timestamp_ms);
  }
}
}  // namespace bwe
}  // namespace testing
}  // namespace webrtc

#endif  // BWE_TEST_LOGGING_COMPILE_TIME_ENABLE
