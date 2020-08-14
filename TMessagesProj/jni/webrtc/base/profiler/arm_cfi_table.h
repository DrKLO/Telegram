// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_PROFILER_ARM_CFI_TABLE_H_
#define BASE_PROFILER_ARM_CFI_TABLE_H_

#include <memory>

#include "base/containers/buffer_iterator.h"
#include "base/containers/span.h"
#include "base/macros.h"
#include "base/optional.h"

namespace base {

// This class implements methods to read and parse the arm Call Frame
// Information (CFI) for Chrome, which contains tables for unwinding Chrome
// functions. For detailed description of the format, see
// extract_unwind_tables.py.
class BASE_EXPORT ArmCFITable {
 public:
  // The CFI information that correspond to an instruction. {0, 0} is a valid
  // entry and should be interpreted as the default rule:
  // .cfa: sp; .cfa = lr (link register).
  struct FrameEntry {
    // The offset of the call frame address (CFA) of previous function, relative
    // to the current stack pointer. Rule for unwinding CFA:
    // .cfa: sp + cfa_offset.
    uint16_t cfa_offset = 0;
    // The offset of location of return address (RA), relative to the previous
    // call frame address. Rule for unwinding RA:
    // .ra = *(cfa - ra_offset).
    uint16_t ra_offset = 0;
  };

  // Parses |cfi_data| and creates a ArmCFITable that reads from it.
  // |cfi_data| is required to remain valid for the lifetime of the object.
  static std::unique_ptr<ArmCFITable> Parse(span<const uint8_t> cfi_data);

  ArmCFITable(span<const uint32_t> function_addresses,
              span<const uint16_t> entry_data_indices,
              span<const uint8_t> entry_data);
  ~ArmCFITable();

  // Finds the CFI row for the given |address| in terms of offset from the
  // start of the current binary. Concurrent calls are thread safe.
  Optional<FrameEntry> FindEntryForAddress(uintptr_t address) const;

  size_t GetTableSizeForTesting() const { return function_addresses_.size(); }

 private:
  // The UNW_INDEX table allows readers to map functions start addresses to
  // that function's respective entry in the UNW_DATA table.
  //   - A function's start address is at 0x123, and
  //   - function_addresses_[2] == 0x123, and
  //   - entry_data_indices_[2] = 42, then
  //   - entry_data_[42] is the corresponding entry in the UNW_DATA table for
  //     the function with the start address of 0x123
  //
  // Note that function_addresses is sorted to facilitate easy lookup.
  const span<const uint32_t> function_addresses_;
  const span<const uint16_t> entry_data_indices_;

  // A reference to the UNW_DATA table. Each entry in the UNW_DATA table
  // corresponds to a function, which in turn corresponds to an array of
  // CFIDataRows. (see arm_cfi_reader.cc).
  const span<const uint8_t> entry_data_;

  DISALLOW_COPY_AND_ASSIGN(ArmCFITable);
};

}  // namespace base

#endif  // BASE_PROFILER_ARM_CFI_TABLE_H_
