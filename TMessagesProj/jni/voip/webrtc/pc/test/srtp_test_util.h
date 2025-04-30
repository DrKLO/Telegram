/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef PC_TEST_SRTP_TEST_UTIL_H_
#define PC_TEST_SRTP_TEST_UTIL_H_

#include <string>

namespace rtc {

extern const char kCsAesCm128HmacSha1_32[];
extern const char kCsAeadAes128Gcm[];
extern const char kCsAeadAes256Gcm[];

static const uint8_t kTestKey1[] = "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234";
static const uint8_t kTestKey2[] = "4321ZYXWVUTSRQPONMLKJIHGFEDCBA";
static const int kTestKeyLen = 30;

static int rtp_auth_tag_len(const std::string& cs) {
  if (cs == kCsAesCm128HmacSha1_32) {
    return 4;
  } else if (cs == kCsAeadAes128Gcm || cs == kCsAeadAes256Gcm) {
    return 16;
  } else {
    return 10;
  }
}
static int rtcp_auth_tag_len(const std::string& cs) {
  if (cs == kCsAeadAes128Gcm || cs == kCsAeadAes256Gcm) {
    return 16;
  } else {
    return 10;
  }
}

}  // namespace rtc

#endif  // PC_TEST_SRTP_TEST_UTIL_H_
