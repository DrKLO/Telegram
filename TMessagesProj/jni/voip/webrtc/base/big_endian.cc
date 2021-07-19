// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/big_endian.h"

#include "base/numerics/checked_math.h"
#include "base/strings/string_piece.h"

namespace base {

BigEndianReader::BigEndianReader(const char* buf, size_t len)
    : ptr_(buf), end_(ptr_ + len) {}

bool BigEndianReader::Skip(size_t len) {
  if (ptr_ + len > end_)
    return false;
  ptr_ += len;
  return true;
}

bool BigEndianReader::ReadBytes(void* out, size_t len) {
  if (ptr_ + len > end_)
    return false;
  memcpy(out, ptr_, len);
  ptr_ += len;
  return true;
}

bool BigEndianReader::ReadPiece(base::StringPiece* out, size_t len) {
  if (ptr_ + len > end_)
    return false;
  *out = base::StringPiece(ptr_, len);
  ptr_ += len;
  return true;
}

template<typename T>
bool BigEndianReader::Read(T* value) {
  if (ptr_ + sizeof(T) > end_)
    return false;
  ReadBigEndian<T>(ptr_, value);
  ptr_ += sizeof(T);
  return true;
}

bool BigEndianReader::ReadU8(uint8_t* value) {
  return Read(value);
}

bool BigEndianReader::ReadU16(uint16_t* value) {
  return Read(value);
}

bool BigEndianReader::ReadU32(uint32_t* value) {
  return Read(value);
}

bool BigEndianReader::ReadU64(uint64_t* value) {
  return Read(value);
}

template <typename T>
bool BigEndianReader::ReadLengthPrefixed(base::StringPiece* out) {
  T t_len;
  if (!Read(&t_len))
    return false;
  size_t len = strict_cast<size_t>(t_len);
  const char* original_ptr = ptr_;
  if (!Skip(len)) {
    ptr_ -= sizeof(T);
    return false;
  }
  *out = base::StringPiece(original_ptr, len);
  return true;
}

bool BigEndianReader::ReadU8LengthPrefixed(base::StringPiece* out) {
  return ReadLengthPrefixed<uint8_t>(out);
}

bool BigEndianReader::ReadU16LengthPrefixed(base::StringPiece* out) {
  return ReadLengthPrefixed<uint16_t>(out);
}

BigEndianWriter::BigEndianWriter(char* buf, size_t len)
    : ptr_(buf), end_(ptr_ + len) {}

bool BigEndianWriter::Skip(size_t len) {
  if (ptr_ + len > end_)
    return false;
  ptr_ += len;
  return true;
}

bool BigEndianWriter::WriteBytes(const void* buf, size_t len) {
  if (ptr_ + len > end_)
    return false;
  memcpy(ptr_, buf, len);
  ptr_ += len;
  return true;
}

template<typename T>
bool BigEndianWriter::Write(T value) {
  if (ptr_ + sizeof(T) > end_)
    return false;
  WriteBigEndian<T>(ptr_, value);
  ptr_ += sizeof(T);
  return true;
}

bool BigEndianWriter::WriteU8(uint8_t value) {
  return Write(value);
}

bool BigEndianWriter::WriteU16(uint16_t value) {
  return Write(value);
}

bool BigEndianWriter::WriteU32(uint32_t value) {
  return Write(value);
}

bool BigEndianWriter::WriteU64(uint64_t value) {
  return Write(value);
}

}  // namespace base
