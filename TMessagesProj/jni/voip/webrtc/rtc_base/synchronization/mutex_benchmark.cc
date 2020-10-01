/*
 *  Copyright 2020 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "benchmark/benchmark.h"
#include "rtc_base/synchronization/mutex.h"
#include "rtc_base/system/unused.h"

namespace webrtc {

class PerfTestData {
 public:
  PerfTestData() : cache_line_barrier_1_(), cache_line_barrier_2_() {
    cache_line_barrier_1_[0]++;  // Avoid 'is not used'.
    cache_line_barrier_2_[0]++;  // Avoid 'is not used'.
  }

  int AddToCounter(int add) {
    MutexLock mu(&mu_);
    my_counter_ += add;
    return 0;
  }

 private:
  uint8_t cache_line_barrier_1_[64];
  Mutex mu_;
  uint8_t cache_line_barrier_2_[64];
  int64_t my_counter_ = 0;
};

void BM_LockWithMutex(benchmark::State& state) {
  static PerfTestData test_data;
  for (auto s : state) {
    RTC_UNUSED(s);
    benchmark::DoNotOptimize(test_data.AddToCounter(2));
  }
}

BENCHMARK(BM_LockWithMutex)->Threads(1);
BENCHMARK(BM_LockWithMutex)->Threads(2);
BENCHMARK(BM_LockWithMutex)->Threads(4);
BENCHMARK(BM_LockWithMutex)->ThreadPerCpu();

}  // namespace webrtc

/*

Results:

NB when reproducing: Remember to turn of power management features such as CPU
scaling before running!

pthreads (Linux):
----------------------------------------------------------------------
Run on (12 X 4500 MHz CPU s)
CPU Caches:
  L1 Data 32 KiB (x6)
  L1 Instruction 32 KiB (x6)
  L2 Unified 1024 KiB (x6)
  L3 Unified 8448 KiB (x1)
Load Average: 0.26, 0.28, 0.44
----------------------------------------------------------------------
Benchmark                            Time             CPU   Iterations
----------------------------------------------------------------------
BM_LockWithMutex/threads:1        13.4 ns         13.4 ns     52192906
BM_LockWithMutex/threads:2        44.2 ns         88.4 ns      8189944
BM_LockWithMutex/threads:4        52.0 ns          198 ns      3743244
BM_LockWithMutex/threads:12       84.9 ns          944 ns       733524

std::mutex performs like the pthread implementation (Linux).

Abseil (Linux):
----------------------------------------------------------------------
Run on (12 X 4500 MHz CPU s)
CPU Caches:
  L1 Data 32 KiB (x6)
  L1 Instruction 32 KiB (x6)
  L2 Unified 1024 KiB (x6)
  L3 Unified 8448 KiB (x1)
Load Average: 0.27, 0.24, 0.37
----------------------------------------------------------------------
Benchmark                            Time             CPU   Iterations
----------------------------------------------------------------------
BM_LockWithMutex/threads:1        15.0 ns         15.0 ns     46550231
BM_LockWithMutex/threads:2        91.1 ns          182 ns      4059212
BM_LockWithMutex/threads:4        40.8 ns          131 ns      5496560
BM_LockWithMutex/threads:12       37.0 ns          130 ns      5377668

*/
