/*
 *  Copyright 2018 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_MEMORY_STREAM_H_
#define RTC_BASE_MEMORY_STREAM_H_

#include <stddef.h>

#include "rtc_base/stream.h"

namespace rtc {

// MemoryStream dynamically resizes to accomodate written data.

class MemoryStream final : public StreamInterface {
 public:
  MemoryStream();
  ~MemoryStream() override;

  StreamState GetState() const override;
  StreamResult Read(rtc::ArrayView<uint8_t> buffer,
                    size_t& bytes_read,
                    int& error) override;
  StreamResult Write(rtc::ArrayView<const uint8_t> buffer,
                     size_t& bytes_written,
                     int& error) override;
  void Close() override;
  bool GetSize(size_t* size) const;
  bool ReserveSize(size_t size);

  bool SetPosition(size_t position);
  bool GetPosition(size_t* position) const;
  void Rewind();

  char* GetBuffer() { return buffer_; }
  const char* GetBuffer() const { return buffer_; }

  void SetData(const void* data, size_t length);

 private:
  StreamResult DoReserve(size_t size, int* error);

  // Invariant: 0 <= seek_position <= data_length_ <= buffer_length_
  char* buffer_ = nullptr;
  size_t buffer_length_ = 0;
  size_t data_length_ = 0;
  size_t seek_position_ = 0;
};

}  // namespace rtc

#endif  // RTC_BASE_MEMORY_STREAM_H_
