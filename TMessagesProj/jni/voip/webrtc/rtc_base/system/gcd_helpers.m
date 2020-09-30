/*
 *  Copyright 2020 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/system/gcd_helpers.h"

dispatch_queue_t RTCDispatchQueueCreateWithTarget(const char *label,
                                                  dispatch_queue_attr_t attr,
                                                  dispatch_queue_t target) {
  if (@available(iOS 10, macOS 10.12, tvOS 10, watchOS 3, *)) {
    return dispatch_queue_create_with_target(label, attr, target);
  }
  dispatch_queue_t queue = dispatch_queue_create(label, attr);
  dispatch_set_target_queue(queue, target);
  return queue;
}
