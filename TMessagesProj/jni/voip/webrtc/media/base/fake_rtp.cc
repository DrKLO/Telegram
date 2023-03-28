/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "media/base/fake_rtp.h"

#include <stdint.h>
#include <string.h>

#include "absl/algorithm/container.h"
#include "rtc_base/checks.h"
#include "test/gtest.h"

void CompareHeaderExtensions(const char* packet1,
                             size_t packet1_size,
                             const char* packet2,
                             size_t packet2_size,
                             const std::vector<int>& encrypted_headers,
                             bool expect_equal) {
  // Sanity check: packets must be large enough to contain the RTP header and
  // extensions header.
  RTC_CHECK_GE(packet1_size, 12 + 4);
  RTC_CHECK_GE(packet2_size, 12 + 4);
  // RTP extension headers are the same.
  EXPECT_EQ(0, memcmp(packet1 + 12, packet2 + 12, 4));
  // Check for one-byte header extensions.
  EXPECT_EQ('\xBE', packet1[12]);
  EXPECT_EQ('\xDE', packet1[13]);
  // Determine position and size of extension headers.
  size_t extension_words = packet1[14] << 8 | packet1[15];
  const char* extension_data1 = packet1 + 12 + 4;
  const char* extension_end1 = extension_data1 + extension_words * 4;
  const char* extension_data2 = packet2 + 12 + 4;
  // Sanity check: packets must be large enough to contain the RTP header
  // extensions.
  RTC_CHECK_GE(packet1_size, 12 + 4 + extension_words * 4);
  RTC_CHECK_GE(packet2_size, 12 + 4 + extension_words * 4);
  while (extension_data1 < extension_end1) {
    uint8_t id = (*extension_data1 & 0xf0) >> 4;
    uint8_t len = (*extension_data1 & 0x0f) + 1;
    extension_data1++;
    extension_data2++;
    EXPECT_LE(extension_data1, extension_end1);
    if (id == 15) {
      // Finished parsing.
      break;
    }

    // The header extension doesn't get encrypted if the id is not in the
    // list of header extensions to encrypt.
    if (expect_equal || !absl::c_linear_search(encrypted_headers, id)) {
      EXPECT_EQ(0, memcmp(extension_data1, extension_data2, len));
    } else {
      EXPECT_NE(0, memcmp(extension_data1, extension_data2, len));
    }

    extension_data1 += len;
    extension_data2 += len;
    // Skip padding.
    while (extension_data1 < extension_end1 && *extension_data1 == 0) {
      extension_data1++;
      extension_data2++;
    }
  }
}
