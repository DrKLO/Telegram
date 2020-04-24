/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/aec3/render_reverb_model.h"

#include <algorithm>

#include "api/array_view.h"
#include "rtc_base/checks.h"

namespace webrtc {

RenderReverbModel::RenderReverbModel() {
  Reset();
}

RenderReverbModel::~RenderReverbModel() = default;

void RenderReverbModel::Reset() {
  render_reverb_.Reset();
}

void RenderReverbModel::Apply(const VectorBuffer& spectrum_buffer,
                              int delay_blocks,
                              float reverb_decay,
                              rtc::ArrayView<float> reverb_power_spectrum) {
  int idx_at_delay =
      spectrum_buffer.OffsetIndex(spectrum_buffer.read, delay_blocks);
  int idx_past = spectrum_buffer.IncIndex(idx_at_delay);
  const auto& X2 = spectrum_buffer.buffer[idx_at_delay];
  RTC_DCHECK_EQ(X2.size(), reverb_power_spectrum.size());
  std::copy(X2.begin(), X2.end(), reverb_power_spectrum.begin());
  render_reverb_.AddReverbNoFreqShaping(spectrum_buffer.buffer[idx_past], 1.0f,
                                        reverb_decay, reverb_power_spectrum);
}

}  // namespace webrtc
