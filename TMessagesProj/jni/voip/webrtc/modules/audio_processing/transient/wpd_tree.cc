/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/transient/wpd_tree.h"

#include <string.h>

#include "modules/audio_processing/transient/wpd_node.h"
#include "rtc_base/checks.h"

namespace webrtc {

WPDTree::WPDTree(size_t data_length,
                 const float* high_pass_coefficients,
                 const float* low_pass_coefficients,
                 size_t coefficients_length,
                 int levels)
    : data_length_(data_length),
      levels_(levels),
      num_nodes_((1 << (levels + 1)) - 1) {
  RTC_DCHECK_GT(data_length, (static_cast<size_t>(1) << levels));
  RTC_DCHECK(high_pass_coefficients);
  RTC_DCHECK(low_pass_coefficients);
  RTC_DCHECK_GT(levels, 0);
  // Size is 1 more, so we can use the array as 1-based. nodes_[0] is never
  // allocated.
  nodes_.reset(new std::unique_ptr<WPDNode>[num_nodes_ + 1]);

  // Create the first node
  const float kRootCoefficient = 1.f;  // Identity Coefficient.
  nodes_[1].reset(new WPDNode(data_length, &kRootCoefficient, 1));
  // Variables used to create the rest of the nodes.
  size_t index = 1;
  size_t index_left_child = 0;
  size_t index_right_child = 0;

  int num_nodes_at_curr_level = 0;

  // Branching each node in each level to create its children. The last level is
  // not branched (all the nodes of that level are leaves).
  for (int current_level = 0; current_level < levels; ++current_level) {
    num_nodes_at_curr_level = 1 << current_level;
    for (int i = 0; i < num_nodes_at_curr_level; ++i) {
      index = (1 << current_level) + i;
      // Obtain the index of the current node children.
      index_left_child = index * 2;
      index_right_child = index_left_child + 1;
      nodes_[index_left_child].reset(new WPDNode(nodes_[index]->length() / 2,
                                                 low_pass_coefficients,
                                                 coefficients_length));
      nodes_[index_right_child].reset(new WPDNode(nodes_[index]->length() / 2,
                                                  high_pass_coefficients,
                                                  coefficients_length));
    }
  }
}

WPDTree::~WPDTree() {}

WPDNode* WPDTree::NodeAt(int level, int index) {
  if (level < 0 || level > levels_ || index < 0 || index >= 1 << level) {
    return NULL;
  }

  return nodes_[(1 << level) + index].get();
}

int WPDTree::Update(const float* data, size_t data_length) {
  if (!data || data_length != data_length_) {
    return -1;
  }

  // Update the root node.
  int update_result = nodes_[1]->set_data(data, data_length);
  if (update_result != 0) {
    return -1;
  }

  // Variables used to update the rest of the nodes.
  size_t index = 1;
  size_t index_left_child = 0;
  size_t index_right_child = 0;

  int num_nodes_at_curr_level = 0;

  for (int current_level = 0; current_level < levels_; ++current_level) {
    num_nodes_at_curr_level = 1 << current_level;
    for (int i = 0; i < num_nodes_at_curr_level; ++i) {
      index = (1 << current_level) + i;
      // Obtain the index of the current node children.
      index_left_child = index * 2;
      index_right_child = index_left_child + 1;

      update_result = nodes_[index_left_child]->Update(nodes_[index]->data(),
                                                       nodes_[index]->length());
      if (update_result != 0) {
        return -1;
      }

      update_result = nodes_[index_right_child]->Update(
          nodes_[index]->data(), nodes_[index]->length());
      if (update_result != 0) {
        return -1;
      }
    }
  }

  return 0;
}

}  // namespace webrtc
