/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_REMOTE_BITRATE_ESTIMATOR_TEST_BWE_TEST_LOGGING_H_
#define MODULES_REMOTE_BITRATE_ESTIMATOR_TEST_BWE_TEST_LOGGING_H_

// To enable BWE logging, run this command from trunk/ :
// build/gyp_chromium --depth=. webrtc/modules/modules.gyp
//   -Denable_bwe_test_logging=1
#ifndef BWE_TEST_LOGGING_COMPILE_TIME_ENABLE
#define BWE_TEST_LOGGING_COMPILE_TIME_ENABLE 0
#endif  // BWE_TEST_LOGGING_COMPILE_TIME_ENABLE

// BWE logging allows you to insert dynamically named log/plot points in the
// call tree. E.g. the function:
//  void f1() {
//    BWE_TEST_LOGGING_TIME(clock_->TimeInMilliseconds());
//    BWE_TEST_LOGGING_CONTEXT("stream");
//    for (uint32_t i=0; i<4; ++i) {
//      BWE_TEST_LOGGING_ENABLE(i & 1);
//      BWE_TEST_LOGGING_CONTEXT(i);
//      BWE_TEST_LOGGING_LOG1("weight", "%f tonnes", weights_[i]);
//      for (float j=0.0f; j<1.0; j+=0.4f) {
//        BWE_TEST_LOGGING_PLOT(0, "bps", -1, j);
//      }
//    }
//  }
//
// Might produce the output:
//   stream_00000001_weight 13.000000 tonnes
//   PLOT  stream_00000001_bps  1.000000  0.000000
//   PLOT  stream_00000001_bps  1.000000  0.400000
//   PLOT  stream_00000001_bps  1.000000  0.800000
//   stream_00000003_weight 39.000000 tonnes
//   PLOT  stream_00000003_bps  1.000000  0.000000
//   PLOT  stream_00000003_bps  1.000000  0.400000
//   PLOT  stream_00000003_bps  1.000000  0.800000
//
// Log *contexts* are names concatenated with '_' between them, with the name
// of the logged/plotted string/value last. Plot *time* is inherited down the
// tree. A branch is enabled by default but can be *disabled* to reduce output.
// The difference between the RTC_LOG and PLOT macros is that PLOT prefixes the
// line so it can be easily filtered, plus it outputs the current time.

#if !(BWE_TEST_LOGGING_COMPILE_TIME_ENABLE)

// Set a thread-global base logging context. This name will be prepended to all
// hierarchical contexts.
// `name` is a char*, std::string or uint32_t to name the context.
#define BWE_TEST_LOGGING_GLOBAL_CONTEXT(name)

// Thread-globally allow/disallow logging.
// `enable` is expected to be a bool.
#define BWE_TEST_LOGGING_GLOBAL_ENABLE(enabled)

// Insert a (hierarchical) logging context.
// `name` is a char*, std::string or uint32_t to name the context.
#define BWE_TEST_LOGGING_CONTEXT(name)

// Allow/disallow logging down the call tree from this point. Logging must be
// enabled all the way to the root of the call tree to take place.
// `enable` is expected to be a bool.
#define BWE_TEST_LOGGING_ENABLE(enabled)

// Set current time (only affects PLOT output). Down the call tree, the latest
// time set always takes precedence.
// `time` is an int64_t time in ms, or -1 to inherit time from previous context.
#define BWE_TEST_LOGGING_TIME(time)

// Print to stdout, e.g.:
//   Context1_Context2_Name  printf-formated-string
// `name` is a char*, std::string or uint32_t to name the log line.
// `format` is a printf format string.
// |_1...| are arguments for printf.
#define BWE_TEST_LOGGING_LOG1(name, format, _1)
#define BWE_TEST_LOGGING_LOG2(name, format, _1, _2)
#define BWE_TEST_LOGGING_LOG3(name, format, _1, _2, _3)
#define BWE_TEST_LOGGING_LOG4(name, format, _1, _2, _3, _4)
#define BWE_TEST_LOGGING_LOG5(name, format, _1, _2, _3, _4, _5)

// Print to stdout in tab-separated format suitable for plotting, e.g.:
//   PLOT figure Context1_Context2_Name  time  value
// `figure` is a figure id. Different figures are plotted in different windows.
// `name` is a char*, std::string or uint32_t to name the plotted value.
// `time` is an int64_t time in ms, or -1 to inherit time from previous context.
// `value` is a double precision float to be plotted.
// `ssrc` identifies the source of a stream
// `alg_name` is an optional argument, a string
#define BWE_TEST_LOGGING_PLOT(figure, name, time, value)
#define BWE_TEST_LOGGING_PLOT_WITH_NAME(figure, name, time, value, alg_name)
#define BWE_TEST_LOGGING_PLOT_WITH_SSRC(figure, name, time, value, ssrc)
#define BWE_TEST_LOGGING_PLOT_WITH_NAME_AND_SSRC(figure, name, time, value, \
                                                 ssrc, alg_name)

// Print to stdout in tab-separated format suitable for plotting, e.g.:
//   BAR figure Context1_Context2_Name  x_left  width  value
// `figure` is a figure id. Different figures are plotted in different windows.
// `name` is a char*, std::string or uint32_t to name the plotted value.
// `value` is a double precision float to be plotted.
// `ylow` and `yhigh` are double precision float for the error line.
// `title` is a string and refers to the error label.
// `ymax` is a double precision float for the limit horizontal line.
// `limit_title` is a string and refers to the limit label.
#define BWE_TEST_LOGGING_BAR(figure, name, value, flow_id)
#define BWE_TEST_LOGGING_ERRORBAR(figure, name, value, ylow, yhigh, \
                                  error_title, flow_id)
#define BWE_TEST_LOGGING_LIMITERRORBAR( \
    figure, name, value, ylow, yhigh, error_title, ymax, limit_title, flow_id)

#define BWE_TEST_LOGGING_BASELINEBAR(figure, name, value, flow_id)

// `num_flows` is an integer refering to the number of RMCAT flows in the
// scenario.
// Define `x_label` and `y_label` for plots.
#define BWE_TEST_LOGGING_LABEL(figure, x_label, y_label, num_flows)

#else  // BWE_TEST_LOGGING_COMPILE_TIME_ENABLE

#include <map>
#include <memory>
#include <stack>
#include <string>

#include "rtc_base/constructor_magic.h"
#include "rtc_base/synchronization/mutex.h"

#define BWE_TEST_LOGGING_GLOBAL_CONTEXT(name)                             \
  do {                                                                    \
    webrtc::testing::bwe::Logging::GetInstance()->SetGlobalContext(name); \
  } while (0)

#define BWE_TEST_LOGGING_GLOBAL_ENABLE(enabled)                             \
  do {                                                                      \
    webrtc::testing::bwe::Logging::GetInstance()->SetGlobalEnable(enabled); \
  } while (0)

#define __BWE_TEST_LOGGING_CONTEXT_NAME(ctx, line) ctx##line
#define __BWE_TEST_LOGGING_CONTEXT_DECLARE(ctx, line, name, time, enabled) \
  webrtc::testing::bwe::Logging::Context __BWE_TEST_LOGGING_CONTEXT_NAME(  \
      ctx, line)(name, time, enabled)

#define BWE_TEST_LOGGING_CONTEXT(name) \
  __BWE_TEST_LOGGING_CONTEXT_DECLARE(__bwe_log_, __LINE__, name, -1, true)
#define BWE_TEST_LOGGING_ENABLE(enabled)                           \
  __BWE_TEST_LOGGING_CONTEXT_DECLARE(__bwe_log_, __LINE__, "", -1, \
                                     static_cast<bool>(enabled))
#define BWE_TEST_LOGGING_TIME(time)                            \
  __BWE_TEST_LOGGING_CONTEXT_DECLARE(__bwe_log_, __LINE__, "", \
                                     static_cast<int64_t>(time), true)

#define BWE_TEST_LOGGING_LOG1(name, format, _1)                    \
  do {                                                             \
    BWE_TEST_LOGGING_CONTEXT(name);                                \
    webrtc::testing::bwe::Logging::GetInstance()->Log(format, _1); \
  } while (0)
#define BWE_TEST_LOGGING_LOG2(name, format, _1, _2)                    \
  do {                                                                 \
    BWE_TEST_LOGGING_CONTEXT(name);                                    \
    webrtc::testing::bwe::Logging::GetInstance()->Log(format, _1, _2); \
  } while (0)
#define BWE_TEST_LOGGING_LOG3(name, format, _1, _2, _3)                    \
  do {                                                                     \
    BWE_TEST_LOGGING_CONTEXT(name);                                        \
    webrtc::testing::bwe::Logging::GetInstance()->Log(format, _1, _2, _3); \
  } while (0)
#define BWE_TEST_LOGGING_LOG4(name, format, _1, _2, _3, _4)                    \
  do {                                                                         \
    BWE_TEST_LOGGING_CONTEXT(name);                                            \
    webrtc::testing::bwe::Logging::GetInstance()->Log(format, _1, _2, _3, _4); \
  } while (0)
#define BWE_TEST_LOGGING_LOG5(name, format, _1, _2, _3, _4, _5)               \
  do {                                                                        \
    BWE_TEST_LOGGING_CONTEXT(name);                                           \
    webrtc::testing::bwe::Logging::GetInstance()->Log(format, _1, _2, _3, _4, \
                                                      _5);                    \
  } while (0)

#define BWE_TEST_LOGGING_PLOT(figure, name, time, value)                     \
  do {                                                                       \
    __BWE_TEST_LOGGING_CONTEXT_DECLARE(__bwe_log_, __PLOT__, name,           \
                                       static_cast<int64_t>(time), true);    \
    webrtc::testing::bwe::Logging::GetInstance()->Plot(figure, name, value); \
  } while (0)

#define BWE_TEST_LOGGING_PLOT_WITH_NAME(figure, name, time, value, alg_name) \
  do {                                                                       \
    __BWE_TEST_LOGGING_CONTEXT_DECLARE(__bwe_log_, __PLOT__, name,           \
                                       static_cast<int64_t>(time), true);    \
    webrtc::testing::bwe::Logging::GetInstance()->Plot(figure, name, value,  \
                                                       alg_name);            \
  } while (0)

#define BWE_TEST_LOGGING_PLOT_WITH_SSRC(figure, name, time, value, ssrc)    \
  do {                                                                      \
    __BWE_TEST_LOGGING_CONTEXT_DECLARE(__bwe_log_, __PLOT__, name,          \
                                       static_cast<int64_t>(time), true);   \
    webrtc::testing::bwe::Logging::GetInstance()->Plot(figure, name, value, \
                                                       ssrc);               \
  } while (0)

#define BWE_TEST_LOGGING_PLOT_WITH_NAME_AND_SSRC(figure, name, time, value, \
                                                 ssrc, alg_name)            \
  do {                                                                      \
    __BWE_TEST_LOGGING_CONTEXT_DECLARE(__bwe_log_, __PLOT__, name,          \
                                       static_cast<int64_t>(time), true);   \
    webrtc::testing::bwe::Logging::GetInstance()->Plot(figure, name, value, \
                                                       ssrc, alg_name);     \
  } while (0)

#define BWE_TEST_LOGGING_BAR(figure, name, value, flow_id)                     \
  do {                                                                         \
    BWE_TEST_LOGGING_CONTEXT(name);                                            \
    webrtc::testing::bwe::Logging::GetInstance()->PlotBar(figure, name, value, \
                                                          flow_id);            \
  } while (0)

#define BWE_TEST_LOGGING_BASELINEBAR(figure, name, value, flow_id) \
  do {                                                             \
    BWE_TEST_LOGGING_CONTEXT(name);                                \
    webrtc::testing::bwe::Logging::GetInstance()->PlotBaselineBar( \
        figure, name, value, flow_id);                             \
  } while (0)

#define BWE_TEST_LOGGING_ERRORBAR(figure, name, value, ylow, yhigh, title, \
                                  flow_id)                                 \
  do {                                                                     \
    BWE_TEST_LOGGING_CONTEXT(name);                                        \
    webrtc::testing::bwe::Logging::GetInstance()->PlotErrorBar(            \
        figure, name, value, ylow, yhigh, title, flow_id);                 \
  } while (0)

#define BWE_TEST_LOGGING_LIMITERRORBAR(                                        \
    figure, name, value, ylow, yhigh, error_title, ymax, limit_title, flow_id) \
  do {                                                                         \
    BWE_TEST_LOGGING_CONTEXT(name);                                            \
    webrtc::testing::bwe::Logging::GetInstance()->PlotLimitErrorBar(           \
        figure, name, value, ylow, yhigh, error_title, ymax, limit_title,      \
        flow_id);                                                              \
  } while (0)

#define BWE_TEST_LOGGING_LABEL(figure, title, y_label, num_flows) \
  do {                                                            \
    BWE_TEST_LOGGING_CONTEXT(title);                              \
    webrtc::testing::bwe::Logging::GetInstance()->PlotLabel(      \
        figure, title, y_label, num_flows);                       \
  } while (0)

namespace webrtc {
namespace testing {
namespace bwe {

class Logging {
 public:
  class Context {
   public:
    Context(uint32_t name, int64_t timestamp_ms, bool enabled);
    Context(const std::string& name, int64_t timestamp_ms, bool enabled);
    Context(const char* name, int64_t timestamp_ms, bool enabled);

    Context() = delete;
    Context(const Context&) = delete;
    Context& operator=(const Context&) = delete;
    ~Context();
  };

  static Logging* GetInstance();

  void SetGlobalContext(uint32_t name);
  void SetGlobalContext(const std::string& name);
  void SetGlobalContext(const char* name);
  void SetGlobalEnable(bool enabled);

#if defined(__GNUC__)
  // Note: Implicit `this` argument counts as the first argument.
  __attribute__((__format__(__printf__, 2, 3)))
#endif
  void
  Log(const char format[], ...);
  void Plot(int figure, const std::string& name, double value);
  void Plot(int figure,
            const std::string& name,
            double value,
            const std::string& alg_name);
  void Plot(int figure, const std::string& name, double value, uint32_t ssrc);
  void Plot(int figure,
            const std::string& name,
            double value,
            uint32_t ssrc,
            const std::string& alg_name);
  void PlotBar(int figure, const std::string& name, double value, int flow_id);
  void PlotBaselineBar(int figure,
                       const std::string& name,
                       double value,
                       int flow_id);
  void PlotErrorBar(int figure,
                    const std::string& name,
                    double value,
                    double ylow,
                    double yhigh,
                    const std::string& error_title,
                    int flow_id);

  void PlotLimitErrorBar(int figure,
                         const std::string& name,
                         double value,
                         double ylow,
                         double yhigh,
                         const std::string& error_title,
                         double ymax,
                         const std::string& limit_title,
                         int flow_id);
  void PlotLabel(int figure,
                 const std::string& title,
                 const std::string& y_label,
                 int num_flows);

 private:
  struct State {
    State();
    State(const std::string& new_tag, int64_t timestamp_ms, bool enabled);
    void MergePrevious(const State& previous);

    std::string tag;
    int64_t timestamp_ms;
    bool enabled;
  };
  struct ThreadState {
    ThreadState();
    ~ThreadState();
    State global_state;
    std::stack<State> stack;
  };
  typedef std::map<uint32_t, ThreadState> ThreadMap;

  Logging();
  ~Logging();
  void PushState(const std::string& append_to_tag,
                 int64_t timestamp_ms,
                 bool enabled);
  void PopState();

  Mutex mutex_;
  ThreadMap thread_map_;

  RTC_DISALLOW_COPY_AND_ASSIGN(Logging);
};
}  // namespace bwe
}  // namespace testing
}  // namespace webrtc

#endif  // BWE_TEST_LOGGING_COMPILE_TIME_ENABLE
#endif  // MODULES_REMOTE_BITRATE_ESTIMATOR_TEST_BWE_TEST_LOGGING_H_
