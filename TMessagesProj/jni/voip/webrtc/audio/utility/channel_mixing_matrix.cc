/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "audio/utility/channel_mixing_matrix.h"

#include <stddef.h>

#include <algorithm>

#include "audio/utility/channel_mixer.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "system_wrappers/include/field_trial.h"

namespace webrtc {

namespace {

// Selects the default usage of VoIP channel mapping adjustments.
bool UseChannelMappingAdjustmentsByDefault() {
  return !field_trial::IsEnabled(
      "WebRTC-VoIPChannelRemixingAdjustmentKillSwitch");
}

}  // namespace

static void ValidateLayout(ChannelLayout layout) {
  RTC_CHECK_NE(layout, CHANNEL_LAYOUT_NONE);
  RTC_CHECK_LE(layout, CHANNEL_LAYOUT_MAX);
  RTC_CHECK_NE(layout, CHANNEL_LAYOUT_UNSUPPORTED);
  RTC_CHECK_NE(layout, CHANNEL_LAYOUT_DISCRETE);
  RTC_CHECK_NE(layout, CHANNEL_LAYOUT_STEREO_AND_KEYBOARD_MIC);

  // Verify there's at least one channel.  Should always be true here by virtue
  // of not being one of the invalid layouts, but lets double check to be sure.
  int channel_count = ChannelLayoutToChannelCount(layout);
  RTC_DCHECK_GT(channel_count, 0);

  // If we have more than one channel, verify a symmetric layout for sanity.
  // The unit test will verify all possible layouts, so this can be a DCHECK.
  // Symmetry allows simplifying the matrix building code by allowing us to
  // assume that if one channel of a pair exists, the other will too.
  if (channel_count > 1) {
    // Assert that LEFT exists if and only if RIGHT exists, and so on.
    RTC_DCHECK_EQ(ChannelOrder(layout, LEFT) >= 0,
                  ChannelOrder(layout, RIGHT) >= 0);
    RTC_DCHECK_EQ(ChannelOrder(layout, SIDE_LEFT) >= 0,
                  ChannelOrder(layout, SIDE_RIGHT) >= 0);
    RTC_DCHECK_EQ(ChannelOrder(layout, BACK_LEFT) >= 0,
                  ChannelOrder(layout, BACK_RIGHT) >= 0);
    RTC_DCHECK_EQ(ChannelOrder(layout, LEFT_OF_CENTER) >= 0,
                  ChannelOrder(layout, RIGHT_OF_CENTER) >= 0);
  } else {
    RTC_DCHECK_EQ(layout, CHANNEL_LAYOUT_MONO);
  }
}

ChannelMixingMatrix::ChannelMixingMatrix(ChannelLayout input_layout,
                                         int input_channels,
                                         ChannelLayout output_layout,
                                         int output_channels)
    : use_voip_channel_mapping_adjustments_(
          UseChannelMappingAdjustmentsByDefault()),
      input_layout_(input_layout),
      input_channels_(input_channels),
      output_layout_(output_layout),
      output_channels_(output_channels) {
  // Stereo down mix should never be the output layout.
  RTC_CHECK_NE(output_layout, CHANNEL_LAYOUT_STEREO_DOWNMIX);

  // Verify that the layouts are supported
  if (input_layout != CHANNEL_LAYOUT_DISCRETE)
    ValidateLayout(input_layout);
  if (output_layout != CHANNEL_LAYOUT_DISCRETE)
    ValidateLayout(output_layout);

  // Special case for 5.0, 5.1 with back channels when upmixed to 7.0, 7.1,
  // which should map the back LR to side LR.
  if (input_layout_ == CHANNEL_LAYOUT_5_0_BACK &&
      output_layout_ == CHANNEL_LAYOUT_7_0) {
    input_layout_ = CHANNEL_LAYOUT_5_0;
  } else if (input_layout_ == CHANNEL_LAYOUT_5_1_BACK &&
             output_layout_ == CHANNEL_LAYOUT_7_1) {
    input_layout_ = CHANNEL_LAYOUT_5_1;
  }
}

ChannelMixingMatrix::~ChannelMixingMatrix() = default;

bool ChannelMixingMatrix::CreateTransformationMatrix(
    std::vector<std::vector<float>>* matrix) {
  matrix_ = matrix;

  // Size out the initial matrix.
  matrix_->reserve(output_channels_);
  for (int output_ch = 0; output_ch < output_channels_; ++output_ch)
    matrix_->push_back(std::vector<float>(input_channels_, 0));

  // First check for discrete case.
  if (input_layout_ == CHANNEL_LAYOUT_DISCRETE ||
      output_layout_ == CHANNEL_LAYOUT_DISCRETE) {
    // If the number of input channels is more than output channels, then
    // copy as many as we can then drop the remaining input channels.
    // If the number of input channels is less than output channels, then
    // copy them all, then zero out the remaining output channels.
    int passthrough_channels = std::min(input_channels_, output_channels_);
    for (int i = 0; i < passthrough_channels; ++i)
      (*matrix_)[i][i] = 1;

    return true;
  }

  // If specified, use adjusted channel mapping for the VoIP scenario.
  if (use_voip_channel_mapping_adjustments_ &&
      input_layout_ == CHANNEL_LAYOUT_MONO &&
      ChannelLayoutToChannelCount(output_layout_) >= 2) {
    // Only place the mono input in the front left and right channels.
    (*matrix_)[0][0] = 1.f;
    (*matrix_)[1][0] = 1.f;

    for (size_t output_ch = 2; output_ch < matrix_->size(); ++output_ch) {
      (*matrix_)[output_ch][0] = 0.f;
    }
    return true;
  }

  // Route matching channels and figure out which ones aren't accounted for.
  for (Channels ch = LEFT; ch < CHANNELS_MAX + 1;
       ch = static_cast<Channels>(ch + 1)) {
    int input_ch_index = ChannelOrder(input_layout_, ch);
    if (input_ch_index < 0)
      continue;

    int output_ch_index = ChannelOrder(output_layout_, ch);
    if (output_ch_index < 0) {
      unaccounted_inputs_.push_back(ch);
      continue;
    }

    RTC_DCHECK_LT(static_cast<size_t>(output_ch_index), matrix_->size());
    RTC_DCHECK_LT(static_cast<size_t>(input_ch_index),
                  (*matrix_)[output_ch_index].size());
    (*matrix_)[output_ch_index][input_ch_index] = 1;
  }

  // If all input channels are accounted for, there's nothing left to do.
  if (unaccounted_inputs_.empty()) {
    // Since all output channels map directly to inputs we can optimize.
    return true;
  }

  // Mix front LR into center.
  if (IsUnaccounted(LEFT)) {
    // When down mixing to mono from stereo, we need to be careful of full scale
    // stereo mixes.  Scaling by 1 / sqrt(2) here will likely lead to clipping
    // so we use 1 / 2 instead.
    float scale =
        (output_layout_ == CHANNEL_LAYOUT_MONO && input_channels_ == 2)
            ? 0.5
            : ChannelMixer::kHalfPower;
    Mix(LEFT, CENTER, scale);
    Mix(RIGHT, CENTER, scale);
  }

  // Mix center into front LR.
  if (IsUnaccounted(CENTER)) {
    // When up mixing from mono, just do a copy to front LR.
    float scale =
        (input_layout_ == CHANNEL_LAYOUT_MONO) ? 1 : ChannelMixer::kHalfPower;
    MixWithoutAccounting(CENTER, LEFT, scale);
    Mix(CENTER, RIGHT, scale);
  }

  // Mix back LR into: side LR || back center || front LR || front center.
  if (IsUnaccounted(BACK_LEFT)) {
    if (HasOutputChannel(SIDE_LEFT)) {
      // If the input has side LR, mix back LR into side LR, but instead if the
      // input doesn't have side LR (but output does) copy back LR to side LR.
      float scale = HasInputChannel(SIDE_LEFT) ? ChannelMixer::kHalfPower : 1;
      Mix(BACK_LEFT, SIDE_LEFT, scale);
      Mix(BACK_RIGHT, SIDE_RIGHT, scale);
    } else if (HasOutputChannel(BACK_CENTER)) {
      // Mix back LR into back center.
      Mix(BACK_LEFT, BACK_CENTER, ChannelMixer::kHalfPower);
      Mix(BACK_RIGHT, BACK_CENTER, ChannelMixer::kHalfPower);
    } else if (output_layout_ > CHANNEL_LAYOUT_MONO) {
      // Mix back LR into front LR.
      Mix(BACK_LEFT, LEFT, ChannelMixer::kHalfPower);
      Mix(BACK_RIGHT, RIGHT, ChannelMixer::kHalfPower);
    } else {
      // Mix back LR into front center.
      Mix(BACK_LEFT, CENTER, ChannelMixer::kHalfPower);
      Mix(BACK_RIGHT, CENTER, ChannelMixer::kHalfPower);
    }
  }

  // Mix side LR into: back LR || back center || front LR || front center.
  if (IsUnaccounted(SIDE_LEFT)) {
    if (HasOutputChannel(BACK_LEFT)) {
      // If the input has back LR, mix side LR into back LR, but instead if the
      // input doesn't have back LR (but output does) copy side LR to back LR.
      float scale = HasInputChannel(BACK_LEFT) ? ChannelMixer::kHalfPower : 1;
      Mix(SIDE_LEFT, BACK_LEFT, scale);
      Mix(SIDE_RIGHT, BACK_RIGHT, scale);
    } else if (HasOutputChannel(BACK_CENTER)) {
      // Mix side LR into back center.
      Mix(SIDE_LEFT, BACK_CENTER, ChannelMixer::kHalfPower);
      Mix(SIDE_RIGHT, BACK_CENTER, ChannelMixer::kHalfPower);
    } else if (output_layout_ > CHANNEL_LAYOUT_MONO) {
      // Mix side LR into front LR.
      Mix(SIDE_LEFT, LEFT, ChannelMixer::kHalfPower);
      Mix(SIDE_RIGHT, RIGHT, ChannelMixer::kHalfPower);
    } else {
      // Mix side LR into front center.
      Mix(SIDE_LEFT, CENTER, ChannelMixer::kHalfPower);
      Mix(SIDE_RIGHT, CENTER, ChannelMixer::kHalfPower);
    }
  }

  // Mix back center into: back LR || side LR || front LR || front center.
  if (IsUnaccounted(BACK_CENTER)) {
    if (HasOutputChannel(BACK_LEFT)) {
      // Mix back center into back LR.
      MixWithoutAccounting(BACK_CENTER, BACK_LEFT, ChannelMixer::kHalfPower);
      Mix(BACK_CENTER, BACK_RIGHT, ChannelMixer::kHalfPower);
    } else if (HasOutputChannel(SIDE_LEFT)) {
      // Mix back center into side LR.
      MixWithoutAccounting(BACK_CENTER, SIDE_LEFT, ChannelMixer::kHalfPower);
      Mix(BACK_CENTER, SIDE_RIGHT, ChannelMixer::kHalfPower);
    } else if (output_layout_ > CHANNEL_LAYOUT_MONO) {
      // Mix back center into front LR.
      // TODO(dalecurtis): Not sure about these values?
      MixWithoutAccounting(BACK_CENTER, LEFT, ChannelMixer::kHalfPower);
      Mix(BACK_CENTER, RIGHT, ChannelMixer::kHalfPower);
    } else {
      // Mix back center into front center.
      // TODO(dalecurtis): Not sure about these values?
      Mix(BACK_CENTER, CENTER, ChannelMixer::kHalfPower);
    }
  }

  // Mix LR of center into: front LR || front center.
  if (IsUnaccounted(LEFT_OF_CENTER)) {
    if (HasOutputChannel(LEFT)) {
      // Mix LR of center into front LR.
      Mix(LEFT_OF_CENTER, LEFT, ChannelMixer::kHalfPower);
      Mix(RIGHT_OF_CENTER, RIGHT, ChannelMixer::kHalfPower);
    } else {
      // Mix LR of center into front center.
      Mix(LEFT_OF_CENTER, CENTER, ChannelMixer::kHalfPower);
      Mix(RIGHT_OF_CENTER, CENTER, ChannelMixer::kHalfPower);
    }
  }

  // Mix LFE into: front center || front LR.
  if (IsUnaccounted(LFE)) {
    if (!HasOutputChannel(CENTER)) {
      // Mix LFE into front LR.
      MixWithoutAccounting(LFE, LEFT, ChannelMixer::kHalfPower);
      Mix(LFE, RIGHT, ChannelMixer::kHalfPower);
    } else {
      // Mix LFE into front center.
      Mix(LFE, CENTER, ChannelMixer::kHalfPower);
    }
  }

  // All channels should now be accounted for.
  RTC_DCHECK(unaccounted_inputs_.empty());

  // See if the output |matrix_| is simply a remapping matrix.  If each input
  // channel maps to a single output channel we can simply remap.  Doing this
  // programmatically is less fragile than logic checks on channel mappings.
  for (int output_ch = 0; output_ch < output_channels_; ++output_ch) {
    int input_mappings = 0;
    for (int input_ch = 0; input_ch < input_channels_; ++input_ch) {
      // We can only remap if each row contains a single scale of 1.  I.e., each
      // output channel is mapped from a single unscaled input channel.
      if ((*matrix_)[output_ch][input_ch] != 1 || ++input_mappings > 1)
        return false;
    }
  }

  // If we've gotten here, |matrix_| is simply a remapping.
  return true;
}

void ChannelMixingMatrix::AccountFor(Channels ch) {
  unaccounted_inputs_.erase(
      std::find(unaccounted_inputs_.begin(), unaccounted_inputs_.end(), ch));
}

bool ChannelMixingMatrix::IsUnaccounted(Channels ch) const {
  return std::find(unaccounted_inputs_.begin(), unaccounted_inputs_.end(),
                   ch) != unaccounted_inputs_.end();
}

bool ChannelMixingMatrix::HasInputChannel(Channels ch) const {
  return ChannelOrder(input_layout_, ch) >= 0;
}

bool ChannelMixingMatrix::HasOutputChannel(Channels ch) const {
  return ChannelOrder(output_layout_, ch) >= 0;
}

void ChannelMixingMatrix::Mix(Channels input_ch,
                              Channels output_ch,
                              float scale) {
  MixWithoutAccounting(input_ch, output_ch, scale);
  AccountFor(input_ch);
}

void ChannelMixingMatrix::MixWithoutAccounting(Channels input_ch,
                                               Channels output_ch,
                                               float scale) {
  int input_ch_index = ChannelOrder(input_layout_, input_ch);
  int output_ch_index = ChannelOrder(output_layout_, output_ch);

  RTC_DCHECK(IsUnaccounted(input_ch));
  RTC_DCHECK_GE(input_ch_index, 0);
  RTC_DCHECK_GE(output_ch_index, 0);

  RTC_DCHECK_EQ((*matrix_)[output_ch_index][input_ch_index], 0);
  (*matrix_)[output_ch_index][input_ch_index] = scale;
}

}  // namespace webrtc
