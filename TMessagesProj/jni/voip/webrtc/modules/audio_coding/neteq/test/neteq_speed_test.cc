/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <stdio.h>

#include <iostream>
#include <vector>

#include "absl/flags/flag.h"
#include "absl/flags/parse.h"
#include "modules/audio_coding/neteq/tools/neteq_performance_test.h"
#include "rtc_base/checks.h"

// Define command line flags.
ABSL_FLAG(int, runtime_ms, 10000, "Simulated runtime in ms.");
ABSL_FLAG(int, lossrate, 10, "Packet lossrate; drop every N packets.");
ABSL_FLAG(float, drift, 0.1f, "Clockdrift factor.");

int main(int argc, char* argv[]) {
  std::vector<char*> args = absl::ParseCommandLine(argc, argv);
  std::string program_name = args[0];
  std::string usage =
      "Tool for measuring the speed of NetEq.\n"
      "Usage: " +
      program_name +
      " [options]\n\n"
      "  --runtime_ms=N         runtime in ms; default is 10000 ms\n"
      "  --lossrate=N           drop every N packets; default is 10\n"
      "  --drift=F              clockdrift factor between 0.0 and 1.0; "
      "default is 0.1\n";
  if (args.size() != 1) {
    printf("%s", usage.c_str());
    return 1;
  }
  RTC_CHECK_GT(absl::GetFlag(FLAGS_runtime_ms), 0);
  RTC_CHECK_GE(absl::GetFlag(FLAGS_lossrate), 0);
  RTC_CHECK(absl::GetFlag(FLAGS_drift) >= 0.0 &&
            absl::GetFlag(FLAGS_drift) < 1.0);

  int64_t result = webrtc::test::NetEqPerformanceTest::Run(
      absl::GetFlag(FLAGS_runtime_ms), absl::GetFlag(FLAGS_lossrate),
      absl::GetFlag(FLAGS_drift));
  if (result <= 0) {
    std::cout << "There was an error" << std::endl;
    return -1;
  }

  std::cout << "Simulation done" << std::endl;
  std::cout << "Runtime = " << result << " ms" << std::endl;
  return 0;
}
