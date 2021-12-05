// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/profiler/arm_cfi_table.h"

#include <algorithm>

namespace base {

namespace {

// The value of index when the function does not have unwind information.
constexpr uint32_t kNoUnwindInformation = 0xFFFF;

// The mask on the CFI row data that is used to get the high 14 bits and
// multiply it by 4 to get CFA offset. Since the last 2 bits are masked out, a
// shift is not necessary.
constexpr uint16_t kCFAMask = 0xfffc;

// The mask on the CFI row data that is used to get the low 2 bits and multiply
// it by 4 to get the return address offset.
constexpr uint16_t kReturnAddressMask = 0x3;
constexpr uint16_t kReturnAddressShift = 2;

// The CFI data in UNW_DATA table starts with number of rows (N) encoded as
// uint16_t, followed by N 4 byte rows. The CFIDataRow represents a single row
// of CFI data of a function in the table. Since we cast the memory at the
// address after the address of number of rows into an array of CFIDataRow, the
// size of the struct should be 4 bytes and the order of the members is fixed
// according to the given format. The first 2 bytes is the address of function
// and last 2 bytes is the CFI data for the offset.
struct CFIDataRow {
  // The address of the instruction as an offset from the start of the
  // function.
  uint16_t addr_offset;
  // Represents the CFA and RA offsets to get information about next stack
  // frame. This is the CFI data at the point before executing the instruction
  // at |addr_offset| from the start of the function.
  uint16_t cfi_data;

  // Helper functions to convert the to ArmCFITable::FrameEntry
  size_t ra_offset() const {
    return (cfi_data & kReturnAddressMask) << kReturnAddressShift;
  }
  size_t cfa_offset() const { return cfi_data & kCFAMask; }
};

static_assert(sizeof(CFIDataRow) == 4,
              "The CFIDataEntry struct must be exactly 4 bytes to ensure "
              "correct parsing of input data");

}  // namespace

// static
std::unique_ptr<ArmCFITable> ArmCFITable::Parse(span<const uint8_t> cfi_data) {
  BufferIterator<const uint8_t> cfi_iterator(cfi_data);

  const uint32_t* unw_index_count = cfi_iterator.Object<uint32_t>();
  if (unw_index_count == nullptr || *unw_index_count == 0U)
    return nullptr;

  auto function_addresses = cfi_iterator.Span<uint32_t>(*unw_index_count);
  auto entry_data_indices = cfi_iterator.Span<uint16_t>(*unw_index_count);
  if (function_addresses.size() != *unw_index_count ||
      entry_data_indices.size() != *unw_index_count)
    return nullptr;

  // The UNW_DATA table data is right after the end of UNW_INDEX table.
  auto entry_data = cfi_iterator.Span<uint8_t>(
      (cfi_iterator.total_size() - cfi_iterator.position()) / sizeof(uint8_t));
  return std::make_unique<ArmCFITable>(function_addresses, entry_data_indices,
                                       entry_data);
}

ArmCFITable::ArmCFITable(span<const uint32_t> function_addresses,
                         span<const uint16_t> entry_data_indices,
                         span<const uint8_t> entry_data)
    : function_addresses_(function_addresses),
      entry_data_indices_(entry_data_indices),
      entry_data_(entry_data) {
  DCHECK_EQ(function_addresses.size(), entry_data_indices.size());
}

ArmCFITable::~ArmCFITable() = default;

Optional<ArmCFITable::FrameEntry> ArmCFITable::FindEntryForAddress(
    uintptr_t address) const {
  DCHECK(!function_addresses_.empty());

  // Find the required function address in UNW_INDEX as the last function lower
  // or equal to |address| (the value right before the result of upper_bound(),
  // if any).
  auto func_it = std::upper_bound(function_addresses_.begin(),
                                  function_addresses_.end(), address);
  // If no function comes before |address|, no CFI entry  is returned.
  if (func_it == function_addresses_.begin())
    return nullopt;
  --func_it;

  uint32_t func_start_addr = *func_it;
  size_t row_num = func_it - function_addresses_.begin();
  uint16_t index = entry_data_indices_[row_num];
  DCHECK_LE(func_start_addr, address);

  if (index == kNoUnwindInformation)
    return nullopt;

  // The unwind data for the current function is at a 2 bytes offset of the
  // index found in UNW_INDEX table.
  if (entry_data_.size() <= index * sizeof(uint16_t))
    return nullopt;
  BufferIterator<const uint8_t> entry_iterator(entry_data_);
  entry_iterator.Seek(index * sizeof(uint16_t));

  // The value of first 2 bytes is the CFI data row count for the function.
  const uint16_t* row_count = entry_iterator.Object<uint16_t>();
  if (row_count == nullptr)
    return nullopt;
  // And the actual CFI rows start after 2 bytes from the |unwind_data|. Cast
  // the data into an array of CFIUnwindDataRow since the struct is designed to
  // represent each row. We should be careful to read only |row_count| number of
  // elements in the array.
  auto function_cfi = entry_iterator.Span<CFIDataRow>(*row_count);
  if (function_cfi.size() != *row_count)
    return nullopt;

  FrameEntry last_frame_entry = {0, 0};
  // Iterate through all function entries to find a range covering |address|.
  // In practice, the majority of functions contain very few entries.
  for (const auto& entry : function_cfi) {
    // The return address of the function is the instruction that is not yet
    // been executed. The CFI row specifies the unwind info before executing the
    // given instruction. If the given address is equal to the instruction
    // offset, then use the current row. Or use the row with highest address
    // less than the given address.
    if (func_start_addr + entry.addr_offset > address)
      break;

    uint32_t cfa_offset = entry.cfa_offset();
    if (cfa_offset == 0)
      return nullopt;
    last_frame_entry.cfa_offset = cfa_offset;

    uint32_t ra_offset = entry.ra_offset();
    // The RA offset of the last specified row should be used, if unspecified.
    // Update |last_ra_offset| only if valid for this row. Otherwise, tthe last
    // valid |last_ra_offset| is used. TODO(ssid): This should be fixed in the
    // format and we should always output ra offset.
    if (ra_offset)
      last_frame_entry.ra_offset = ra_offset;

    if (last_frame_entry.ra_offset == 0)
      return nullopt;
  }

  return last_frame_entry;
}

}  // namespace base