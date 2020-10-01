// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/android/reached_addresses_bitset.h"

#include "base/android/library_loader/anchor_functions.h"
#include "base/android/library_loader/anchor_functions_buildflags.h"
#include "base/logging.h"
#include "base/no_destructor.h"

namespace base {
namespace android {

namespace {
constexpr size_t kBitsPerElement = sizeof(uint32_t) * 8;

#if BUILDFLAG(SUPPORTS_CODE_ORDERING)
// Enough for 1 << 29 bytes of code, 512MB.
constexpr size_t kTextBitfieldSize = 1 << 20;
std::atomic<uint32_t> g_text_bitfield[kTextBitfieldSize];
#endif
}  // namespace

// static
ReachedAddressesBitset* ReachedAddressesBitset::GetTextBitset() {
#if BUILDFLAG(SUPPORTS_CODE_ORDERING)
  static base::NoDestructor<ReachedAddressesBitset> text_bitset(
      kStartOfText, kEndOfText, g_text_bitfield, kTextBitfieldSize);
  return text_bitset.get();
#else
  return nullptr;
#endif
}

void ReachedAddressesBitset::RecordAddress(uintptr_t address) {
  // |address| is outside of the range.
  if (address < start_address_ || address >= end_address_)
    return;

  uint32_t offset = address - start_address_;
  uint32_t offset_index = offset / kBytesGranularity;

  // Atomically set the corresponding bit in the array.
  std::atomic<uint32_t>* element = reached_ + (offset_index / kBitsPerElement);

  // First, a racy check. This saves a CAS if the bit is already set, and
  // allows the cache line to remain shared across CPUs in this case.
  uint32_t value = element->load(std::memory_order_relaxed);
  uint32_t mask = 1 << (offset_index % kBitsPerElement);
  if (value & mask)
    return;
  element->fetch_or(mask, std::memory_order_relaxed);
}

std::vector<uint32_t> ReachedAddressesBitset::GetReachedOffsets() const {
  std::vector<uint32_t> offsets;
  const size_t elements = NumberOfReachableElements();

  for (size_t i = 0; i < elements; ++i) {
    uint32_t element = reached_[i].load(std::memory_order_relaxed);
    // No reached addresses at this element.
    if (element == 0)
      continue;

    for (size_t j = 0; j < 32; ++j) {
      if (!((element >> j) & 1))
        continue;

      uint32_t offset_index = i * 32 + j;
      uint32_t offset = offset_index * kBytesGranularity;
      offsets.push_back(offset);
    }
  }

  return offsets;
}

ReachedAddressesBitset::ReachedAddressesBitset(
    uintptr_t start_address,
    uintptr_t end_address,
    std::atomic<uint32_t>* storage_ptr,
    size_t storage_size)
    : start_address_(start_address),
      end_address_(end_address),
      reached_(storage_ptr) {
  DCHECK_LE(start_address_, end_address_);
  DCHECK_LE(NumberOfReachableElements(), storage_size * kBitsPerElement);
}

size_t ReachedAddressesBitset::NumberOfReachableElements() const {
  size_t reachable_bits =
      (end_address_ + kBytesGranularity - 1) / kBytesGranularity -
      start_address_ / kBytesGranularity;
  return (reachable_bits + kBitsPerElement - 1) / kBitsPerElement;
}

}  // namespace android
}  // namespace base
