// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_ANDROID_REACHED_ADDRESSES_BITSET_H_
#define BASE_ANDROID_REACHED_ADDRESSES_BITSET_H_

#include <atomic>
#include <vector>

#include "base/base_export.h"

namespace base {

template <typename T>
class NoDestructor;

namespace android {

// ReachedAddressesBitset is a set that stores addresses for the
// ReachedCodeProfiler in compact form. Its main features are lock-free
// thread-safety and fast adding of elements.
//
// The addresses are kept with |kBytesGranularity| to save the storage space.
//
// Once insterted, elements cannot be erased from the set.
//
// All methods can be called from any thread.
class BASE_EXPORT ReachedAddressesBitset {
 public:
  // Returns an instance of ReachedAddressesBitset having enough storage space
  // to keep all addresses from the .text section.
  // Returns nullptr if SUPPORTS_CODE_ORDERING isn't defined.
  // This instance is stored in the .bss section of the binary, meaning that it
  // doesn't incur the binary size overhead and it doesn't increase the resident
  // memory footprint when not used.
  static ReachedAddressesBitset* GetTextBitset();

  // Inserts |address| into the bitset iff |address| lies in the range between
  // |start_address_| and |end_address_|.
  void RecordAddress(uintptr_t address);

  // Returns a list of recorded addresses in the form of offsets from
  // |start_address_|.
  std::vector<uint32_t> GetReachedOffsets() const;

 private:
  friend class ReachedAddressesBitsetTest;
  friend class NoDestructor<ReachedAddressesBitset>;

  // Represents the number of bytes that are mapped into the same bit in the
  // bitset.
  static constexpr size_t kBytesGranularity = 4;

  // Constructs a ReachedAddressesBitset on top of an external storage of
  // |storage_size| pointed by |storage_ptr|. This external storage must outlive
  // the constructed bitset instance. The size of storage must be large enough
  // to fit all addresses in the range between |start_address| and
  // |end_address|.
  ReachedAddressesBitset(uintptr_t start_address,
                         uintptr_t end_address,
                         std::atomic<uint32_t>* storage_ptr,
                         size_t storage_size);

  size_t NumberOfReachableElements() const;

  uintptr_t start_address_;
  uintptr_t end_address_;
  std::atomic<uint32_t>* reached_;
};

}  // namespace android
}  // namespace base

#endif  // BASE_ANDROID_REACHED_ADDRESSES_BITSET_H_
