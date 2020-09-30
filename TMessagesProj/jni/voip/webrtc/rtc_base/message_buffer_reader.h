/*
 *  Copyright 2018 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_MESSAGE_BUFFER_READER_H_
#define RTC_BASE_MESSAGE_BUFFER_READER_H_

#include "rtc_base/byte_buffer.h"

namespace webrtc {

// A simple subclass of the ByteBufferReader that exposes the starting address
// of the message and its length, so that we can recall previously parsed data.
class MessageBufferReader : public rtc::ByteBufferReader {
 public:
  MessageBufferReader(const char* bytes, size_t len)
      : rtc::ByteBufferReader(bytes, len) {}
  ~MessageBufferReader() = default;

  // Starting address of the message.
  const char* MessageData() const { return bytes_; }
  // Total length of the message. Note that this is different from Length(),
  // which is the length of the remaining message from the current offset.
  size_t MessageLength() const { return size_; }
  // Current offset in the message.
  size_t CurrentOffset() const { return start_; }
};

}  // namespace webrtc

#endif  // RTC_BASE_MESSAGE_BUFFER_READER_H_
