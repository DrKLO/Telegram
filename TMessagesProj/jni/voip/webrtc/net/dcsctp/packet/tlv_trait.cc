/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "net/dcsctp/packet/tlv_trait.h"

#include "rtc_base/logging.h"

namespace dcsctp {
namespace tlv_trait_impl {
void ReportInvalidSize(size_t actual_size, size_t expected_size) {
  RTC_DLOG(LS_WARNING) << "Invalid size (" << actual_size
                       << ", expected minimum " << expected_size << " bytes)";
}

void ReportInvalidType(int actual_type, int expected_type) {
  RTC_DLOG(LS_WARNING) << "Invalid type (" << actual_type << ", expected "
                       << expected_type << ")";
}

void ReportInvalidFixedLengthField(size_t value, size_t expected) {
  RTC_DLOG(LS_WARNING) << "Invalid length field (" << value << ", expected "
                       << expected << " bytes)";
}

void ReportInvalidVariableLengthField(size_t value, size_t available) {
  RTC_DLOG(LS_WARNING) << "Invalid length field (" << value << ", available "
                       << available << " bytes)";
}

void ReportInvalidPadding(size_t padding_bytes) {
  RTC_DLOG(LS_WARNING) << "Invalid padding (" << padding_bytes << " bytes)";
}

void ReportInvalidLengthMultiple(size_t length, size_t alignment) {
  RTC_DLOG(LS_WARNING) << "Invalid length field (" << length
                       << ", expected an even multiple of " << alignment
                       << " bytes)";
}
}  // namespace tlv_trait_impl
}  // namespace dcsctp
