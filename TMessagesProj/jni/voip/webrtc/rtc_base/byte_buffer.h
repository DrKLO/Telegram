/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_BYTE_BUFFER_H_
#define RTC_BASE_BYTE_BUFFER_H_

#include <stddef.h>
#include <stdint.h>

#include <string>

#include "rtc_base/buffer.h"
#include "rtc_base/byte_order.h"

// Reads/Writes from/to buffer using network byte order (big endian)
namespace rtc {

template <class BufferClassT>
class ByteBufferWriterT {
 public:
  ByteBufferWriterT() { Construct(nullptr, kDefaultCapacity); }
  ByteBufferWriterT(const char* bytes, size_t len) { Construct(bytes, len); }

  ByteBufferWriterT(const ByteBufferWriterT&) = delete;
  ByteBufferWriterT& operator=(const ByteBufferWriterT&) = delete;

  const char* Data() const { return buffer_.data(); }
  size_t Length() const { return buffer_.size(); }
  size_t Capacity() const { return buffer_.capacity(); }

  // Write value to the buffer. Resizes the buffer when it is
  // neccessary.
  void WriteUInt8(uint8_t val) {
    WriteBytes(reinterpret_cast<const char*>(&val), 1);
  }
  void WriteUInt16(uint16_t val) {
    uint16_t v = HostToNetwork16(val);
    WriteBytes(reinterpret_cast<const char*>(&v), 2);
  }
  void WriteUInt24(uint32_t val) {
    uint32_t v = HostToNetwork32(val);
    char* start = reinterpret_cast<char*>(&v);
    ++start;
    WriteBytes(start, 3);
  }
  void WriteUInt32(uint32_t val) {
    uint32_t v = HostToNetwork32(val);
    WriteBytes(reinterpret_cast<const char*>(&v), 4);
  }
  void WriteUInt64(uint64_t val) {
    uint64_t v = HostToNetwork64(val);
    WriteBytes(reinterpret_cast<const char*>(&v), 8);
  }
  // Serializes an unsigned varint in the format described by
  // https://developers.google.com/protocol-buffers/docs/encoding#varints
  // with the caveat that integers are 64-bit, not 128-bit.
  void WriteUVarint(uint64_t val) {
    while (val >= 0x80) {
      // Write 7 bits at a time, then set the msb to a continuation byte
      // (msb=1).
      char byte = static_cast<char>(val) | 0x80;
      WriteBytes(&byte, 1);
      val >>= 7;
    }
    char last_byte = static_cast<char>(val);
    WriteBytes(&last_byte, 1);
  }
  void WriteString(const std::string& val) {
    WriteBytes(val.c_str(), val.size());
  }
  void WriteBytes(const char* val, size_t len) { buffer_.AppendData(val, len); }

  // Reserves the given number of bytes and returns a char* that can be written
  // into. Useful for functions that require a char* buffer and not a
  // ByteBufferWriter.
  char* ReserveWriteBuffer(size_t len) {
    buffer_.SetSize(buffer_.size() + len);
    return buffer_.data();
  }

  // Resize the buffer to the specified `size`.
  void Resize(size_t size) { buffer_.SetSize(size); }

  // Clears the contents of the buffer. After this, Length() will be 0.
  void Clear() { buffer_.Clear(); }

 private:
  static constexpr size_t kDefaultCapacity = 4096;

  void Construct(const char* bytes, size_t size) {
    if (bytes) {
      buffer_.AppendData(bytes, size);
    } else {
      buffer_.EnsureCapacity(size);
    }
  }

  BufferClassT buffer_;

  // There are sensible ways to define these, but they aren't needed in our code
  // base.
};

class ByteBufferWriter : public ByteBufferWriterT<BufferT<char>> {
 public:
  ByteBufferWriter();
  ByteBufferWriter(const char* bytes, size_t len);

  ByteBufferWriter(const ByteBufferWriter&) = delete;
  ByteBufferWriter& operator=(const ByteBufferWriter&) = delete;
};

// The ByteBufferReader references the passed data, i.e. the pointer must be
// valid during the lifetime of the reader.
class ByteBufferReader {
 public:
  ByteBufferReader(const char* bytes, size_t len);

  // Initializes buffer from a zero-terminated string.
  explicit ByteBufferReader(const char* bytes);

  explicit ByteBufferReader(const Buffer& buf);

  explicit ByteBufferReader(const ByteBufferWriter& buf);

  ByteBufferReader(const ByteBufferReader&) = delete;
  ByteBufferReader& operator=(const ByteBufferReader&) = delete;

  // Returns start of unprocessed data.
  const char* Data() const { return bytes_ + start_; }
  // Returns number of unprocessed bytes.
  size_t Length() const { return end_ - start_; }

  // Read a next value from the buffer. Return false if there isn't
  // enough data left for the specified type.
  bool ReadUInt8(uint8_t* val);
  bool ReadUInt16(uint16_t* val);
  bool ReadUInt24(uint32_t* val);
  bool ReadUInt32(uint32_t* val);
  bool ReadUInt64(uint64_t* val);
  bool ReadUVarint(uint64_t* val);
  bool ReadBytes(char* val, size_t len);

  // Appends next `len` bytes from the buffer to `val`. Returns false
  // if there is less than `len` bytes left.
  bool ReadString(std::string* val, size_t len);

  // Moves current position `size` bytes forward. Returns false if
  // there is less than `size` bytes left in the buffer. Consume doesn't
  // permanently remove data, so remembered read positions are still valid
  // after this call.
  bool Consume(size_t size);

 protected:
  void Construct(const char* bytes, size_t size);

  const char* bytes_;
  size_t size_;
  size_t start_;
  size_t end_;
};

}  // namespace rtc

#endif  // RTC_BASE_BYTE_BUFFER_H_
