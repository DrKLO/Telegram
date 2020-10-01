/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/aec3/echo_audibility.h"

#include <algorithm>
#include <cmath>
#include <utility>
#include <vector>

#include "api/array_view.h"
#include "modules/audio_processing/aec3/block_buffer.h"
#include "modules/audio_processing/aec3/spectrum_buffer.h"
#include "modules/audio_processing/aec3/stationarity_estimator.h"

namespace webrtc {

EchoAudibility::EchoAudibility(bool use_render_stationarity_at_init)
    : use_render_stationarity_at_init_(use_render_stationarity_at_init) {
  Reset();
}

EchoAudibility::~EchoAudibility() = default;

void EchoAudibility::Update(const RenderBuffer& render_buffer,
                            rtc::ArrayView<const float> average_reverb,
                            int delay_blocks,
                            bool external_delay_seen) {
  UpdateRenderNoiseEstimator(render_buffer.GetSpectrumBuffer(),
                             render_buffer.GetBlockBuffer(),
                             external_delay_seen);

  if (external_delay_seen || use_render_stationarity_at_init_) {
    UpdateRenderStationarityFlags(render_buffer, average_reverb, delay_blocks);
  }
}

void EchoAudibility::Reset() {
  render_stationarity_.Reset();
  non_zero_render_seen_ = false;
  render_spectrum_write_prev_ = absl::nullopt;
}

void EchoAudibility::UpdateRenderStationarityFlags(
    const RenderBuffer& render_buffer,
    rtc::ArrayView<const float> average_reverb,
    int min_channel_delay_blocks) {
  const SpectrumBuffer& spectrum_buffer = render_buffer.GetSpectrumBuffer();
  int idx_at_delay = spectrum_buffer.OffsetIndex(spectrum_buffer.read,
                                                 min_channel_delay_blocks);

  int num_lookahead = render_buffer.Headroom() - min_channel_delay_blocks + 1;
  num_lookahead = std::max(0, num_lookahead);

  render_stationarity_.UpdateStationarityFlags(spectrum_buffer, average_reverb,
                                               idx_at_delay, num_lookahead);
}

void EchoAudibility::UpdateRenderNoiseEstimator(
    const SpectrumBuffer& spectrum_buffer,
    const BlockBuffer& block_buffer,
    bool external_delay_seen) {
  if (!render_spectrum_write_prev_) {
    render_spectrum_write_prev_ = spectrum_buffer.write;
    render_block_write_prev_ = block_buffer.write;
    return;
  }
  int render_spectrum_write_current = spectrum_buffer.write;
  if (!non_zero_render_seen_ && !external_delay_seen) {
    non_zero_render_seen_ = !IsRenderTooLow(block_buffer);
  }
  if (non_zero_render_seen_) {
    for (int idx = render_spectrum_write_prev_.value();
         idx != render_spectrum_write_current;
         idx = spectrum_buffer.DecIndex(idx)) {
      render_stationarity_.UpdateNoiseEstimator(spectrum_buffer.buffer[idx]);
    }
  }
  render_spectrum_write_prev_ = render_spectrum_write_current;
}

bool EchoAudibility::IsRenderTooLow(const BlockBuffer& block_buffer) {
  const int num_render_channels =
      static_cast<int>(block_buffer.buffer[0][0].size());
  bool too_low = false;
  const int render_block_write_current = block_buffer.write;
  if (render_block_write_current == render_block_write_prev_) {
    too_low = true;
  } else {
    for (int idx = render_block_write_prev_; idx != render_block_write_current;
         idx = block_buffer.IncIndex(idx)) {
      float max_abs_over_channels = 0.f;
      for (int ch = 0; ch < num_render_channels; ++ch) {
        auto block = block_buffer.buffer[idx][0][ch];
        auto r = std::minmax_element(block.cbegin(), block.cend());
        float max_abs_channel =
            std::max(std::fabs(*r.first), std::fabs(*r.second));
        max_abs_over_channels =
            std::max(max_abs_over_channels, max_abs_channel);
      }
      if (max_abs_over_channels < 10.f) {
        too_low = true;  // Discards all blocks if one of them is too low.
        break;
      }
    }
  }
  render_block_write_prev_ = render_block_write_current;
  return too_low;
}

}  // namespace webrtc
