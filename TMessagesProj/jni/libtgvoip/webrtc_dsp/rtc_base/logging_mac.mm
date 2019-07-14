/*
 *  Copyright 2016 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/logging_mac.h"

#import <Foundation/Foundation.h>

namespace rtc {
std::string DescriptionFromOSStatus(OSStatus err) {
  NSError* error =
      [NSError errorWithDomain:NSOSStatusErrorDomain code:err userInfo:nil];
  return error.description.UTF8String;
}

}  // namespace rtc
