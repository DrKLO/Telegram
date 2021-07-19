/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/transient/wpd_node.h"

#include <math.h>
#include <string.h>

#include "common_audio/fir_filter.h"
#include "common_audio/fir_filter_factory.h"
#include "modules/audio_processing/transient/dyadic_decimator.h"
#include "rtc_base/checks.h"

namespace webrtc {

WPDNode::WPDNode(size_t length,
                 const float* coefficients,
                 size_t coefficients_length)
    :  // The data buffer has parent data length to be able to contain and
       // filter it.
      data_(new float[2 * length + 1]),
      length_(length),
      filter_(
          CreateFirFilter(coefficients, coefficients_length, 2 * length + 1)) {
  RTC_DCHECK_GT(length, 0);
  RTC_DCHECK(coefficients);
  RTC_DCHECK_GT(coefficients_length, 0);
  memset(data_.get(), 0.f, (2 * length + 1) * sizeof(data_[0]));
}

WPDNode::~WPDNode() {}

int WPDNode::Update(const float* parent_data, size_t parent_data_length) {
  if (!parent_data || (parent_data_length / 2) != length_) {
    return -1;
  }

  // Filter data.
  filter_->Filter(parent_data, parent_data_length, data_.get());

  // Decimate data.
  const bool kOddSequence = true;
  size_t output_samples = DyadicDecimate(data_.get(), parent_data_length,
                                         kOddSequence, data_.get(), length_);
  if (output_samples != length_) {
    return -1;
  }

  // Get abs to all values.
  for (size_t i = 0; i < length_; ++i) {
    data_[i] = fabs(data_[i]);
  }

  return 0;
}

int WPDNode::set_data(const float* new_data, size_t length) {
  if (!new_data || length != length_) {
    return -1;
  }
  memcpy(data_.get(), new_data, length * sizeof(data_[0]));
  return 0;
}

}  // namespace webrtc
