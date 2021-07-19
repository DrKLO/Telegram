#pragma once

#include <cinttypes>
#include <cstring>

namespace tgcalls {
struct AudioFrame {
  const int16_t *audio_samples;
  size_t num_samples;
  size_t bytes_per_sample;
  size_t num_channels;
  uint32_t samples_per_sec;
  int64_t elapsed_time_ms;
  int64_t ntp_time_ms;
};
}  // namespace tgcalls
