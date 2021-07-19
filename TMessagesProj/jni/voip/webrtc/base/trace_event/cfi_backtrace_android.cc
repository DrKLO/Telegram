// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/trace_event/cfi_backtrace_android.h"

#include <sys/mman.h>
#include <sys/types.h>

#include "base/android/apk_assets.h"
#include "base/android/library_loader/anchor_functions.h"

#if !defined(ARCH_CPU_ARMEL)
#error This file should not be built for this architecture.
#endif

/*
Basics of unwinding:
For each instruction in a function we need to know what is the offset of SP
(Stack Pointer) to reach the previous function's stack frame. To know which
function is being invoked, we need the return address of the next function. The
CFI information for an instruction is made up of 2 offsets, CFA (Call Frame
Address) offset and RA (Return Address) offset. The CFA offset is the change in
SP made by the function till the current instruction. This depends on amount of
memory allocated on stack by the function plus some registers that the function
stores that needs to be restored at the end of function. So, at each instruction
the CFA offset tells the offset from original SP before the function call. The
RA offset tells us the offset from the previous SP into the current function
where the return address is stored.

The unwind table file has 2 tables UNW_INDEX and UNW_DATA, inspired from ARM
EHABI format. The first table contains function addresses and an index into the
UNW_DATA table. The second table contains one or more rows for the function
unwind information.

UNW_INDEX contains two columns of N rows each, where N is the number of
functions.
  1. First column 4 byte rows of all the function start address as offset from
     start of the binary, in sorted order.
  2. For each function addr, the second column contains 2 byte indices in order.
     The indices are offsets (in count of 2 bytes) of the CFI data from start of
     UNW_DATA.
The last entry in the table always contains CANT_UNWIND index to specify the
end address of the last function.

UNW_DATA contains data of all the functions. Each function data contains N rows.
The data found at the address pointed from UNW_INDEX will be:
  2 bytes: N - number of rows that belong to current function.
  N * 4 bytes: N rows of data. 16 bits : Address offset from function start.
                               14 bits : CFA offset / 4.
                                2 bits : RA offset / 4.
If the RA offset of a row is 0, then use the offset of the previous rows in the
same function.
TODO(ssid): Make sure RA offset is always present.

See extract_unwind_tables.py for details about how this data is extracted from
breakpad symbol files.
*/

extern "C" {

// The address of |__executable_start| gives the start address of the
// executable or shared library. This value is used to find the offset address
// of the instruction in binary from PC.
extern char __executable_start;

}

namespace base {
namespace trace_event {

namespace {

// The value of index when the function does not have unwind information.
constexpr uint32_t kCantUnwind = 0xFFFF;

// The mask on the CFI row data that is used to get the high 14 bits and
// multiply it by 4 to get CFA offset. Since the last 2 bits are masked out, a
// shift is not necessary.
constexpr uint16_t kCFAMask = 0xfffc;

// The mask on the CFI row data that is used to get the low 2 bits and multiply
// it by 4 to get the RA offset.
constexpr uint16_t kRAMask = 0x3;
constexpr uint16_t kRAShift = 2;

// The code in this file assumes we are running in 32-bit builds since all the
// addresses in the unwind table are specified in 32 bits.
static_assert(sizeof(uintptr_t) == 4,
              "The unwind table format is only valid for 32 bit builds.");

// The CFI data in UNW_DATA table starts with number of rows (N) and then
// followed by N rows of 4 bytes long. The CFIUnwindDataRow represents a single
// row of CFI data of a function in the table. Since we cast the memory at the
// address after the address of number of rows, into an array of
// CFIUnwindDataRow, the size of the struct should be 4 bytes and the order of
// the members is fixed according to the given format. The first 2 bytes tell
// the address of function and last 2 bytes give the CFI data for the offset.
struct CFIUnwindDataRow {
  // The address of the instruction in terms of offset from the start of the
  // function.
  uint16_t addr_offset;
  // Represents the CFA and RA offsets to get information about next stack
  // frame. This is the CFI data at the point before executing the instruction
  // at |addr_offset| from the start of the function.
  uint16_t cfi_data;

  // Return the RA offset for the current unwind row.
  size_t ra_offset() const { return (cfi_data & kRAMask) << kRAShift; }

  // Returns the CFA offset for the current unwind row.
  size_t cfa_offset() const { return cfi_data & kCFAMask; }
};

static_assert(
    sizeof(CFIUnwindDataRow) == 4,
    "The CFIUnwindDataRow struct must be exactly 4 bytes for searching.");

}  // namespace

// static
CFIBacktraceAndroid* CFIBacktraceAndroid::GetInitializedInstance() {
  static CFIBacktraceAndroid* instance = new CFIBacktraceAndroid();
  return instance;
}

// static
bool CFIBacktraceAndroid::is_chrome_address(uintptr_t pc) {
  return pc >= base::android::kStartOfText && pc < executable_end_addr();
}

// static
uintptr_t CFIBacktraceAndroid::executable_start_addr() {
  return reinterpret_cast<uintptr_t>(&__executable_start);
}

// static
uintptr_t CFIBacktraceAndroid::executable_end_addr() {
  return base::android::kEndOfText;
}

CFIBacktraceAndroid::CFIBacktraceAndroid()
    : thread_local_cfi_cache_(
          [](void* ptr) { delete static_cast<CFICache*>(ptr); }) {
  Initialize();
}

CFIBacktraceAndroid::~CFIBacktraceAndroid() {}

void CFIBacktraceAndroid::Initialize() {
  // This file name is defined by extract_unwind_tables.gni.
  static constexpr char kCfiFileName[] = "assets/unwind_cfi_32";
  MemoryMappedFile::Region cfi_region;
  int fd = base::android::OpenApkAsset(kCfiFileName, &cfi_region);
  if (fd < 0)
    return;
  cfi_mmap_ = std::make_unique<MemoryMappedFile>();
  // The CFI region starts at |cfi_region.offset|.
  if (!cfi_mmap_->Initialize(base::File(fd), cfi_region))
    return;

  ParseCFITables();
  can_unwind_stack_frames_ = true;
}

void CFIBacktraceAndroid::ParseCFITables() {
  // The first 4 bytes in the file is the number of entries in UNW_INDEX table.
  size_t unw_index_size = 0;
  memcpy(&unw_index_size, cfi_mmap_->data(), sizeof(unw_index_size));
  // UNW_INDEX table starts after 4 bytes.
  unw_index_function_col_ =
      reinterpret_cast<const uintptr_t*>(cfi_mmap_->data()) + 1;
  unw_index_row_count_ = unw_index_size;
  unw_index_indices_col_ = reinterpret_cast<const uint16_t*>(
      unw_index_function_col_ + unw_index_row_count_);

  // The UNW_DATA table data is right after the end of UNW_INDEX table.
  // Interpret the UNW_DATA table as an array of 2 byte numbers since the
  // indexes we have from the UNW_INDEX table are in terms of 2 bytes.
  unw_data_start_addr_ = unw_index_indices_col_ + unw_index_row_count_;
}

size_t CFIBacktraceAndroid::Unwind(const void** out_trace, size_t max_depth) {
  // This function walks the stack using the call frame information to find the
  // return addresses of all the functions that belong to current binary in call
  // stack. For each function the CFI table defines the offset of the previous
  // call frame and offset where the return address is stored.
  if (!can_unwind_stack_frames())
    return 0;

  // Get the current register state. This register state can be taken at any
  // point in the function and the unwind information would be for this point.
  // Define local variables before trying to get the current PC and SP to make
  // sure the register state obtained is consistent with each other.
  uintptr_t pc = 0, sp = 0;
  asm volatile("mov %0, pc" : "=r"(pc));
  asm volatile("mov %0, sp" : "=r"(sp));

  return Unwind(pc, sp, /*lr=*/0, out_trace, max_depth);
}

size_t CFIBacktraceAndroid::Unwind(uintptr_t pc,
                                   uintptr_t sp,
                                   uintptr_t lr,
                                   const void** out_trace,
                                   size_t max_depth) {
  if (!can_unwind_stack_frames())
    return 0;

  // We can only unwind as long as the pc is within the chrome.so.
  size_t depth = 0;
  while (is_chrome_address(pc) && depth < max_depth) {
    out_trace[depth++] = reinterpret_cast<void*>(pc);
    // The offset of function from the start of the chrome.so binary:
    uintptr_t func_addr = pc - executable_start_addr();
    CFIRow cfi{};
    if (!FindCFIRowForPC(func_addr, &cfi)) {
      if (depth == 1 && lr != 0 && pc != lr) {
        // If CFI data is not found for the frame, then we stopped in prolog of
        // a function. The return address is stored in LR when in function
        // prolog. So, update the PC with address in LR and do not update SP
        // since SP was not updated by the prolog yet.
        // TODO(ssid): Write tests / add info to detect if we are actually in
        // function prolog. https://crbug.com/898276
        pc = lr;
        continue;
      }
      break;
    }

    // The rules for unwinding using the CFI information are:
    // SP_prev = SP_cur + cfa_offset and
    // PC_prev = * (SP_prev - ra_offset).
    sp = sp + cfi.cfa_offset;
    memcpy(&pc, reinterpret_cast<uintptr_t*>(sp - cfi.ra_offset),
           sizeof(uintptr_t));
  }
  return depth;
}

void CFIBacktraceAndroid::AllocateCacheForCurrentThread() {
  GetThreadLocalCFICache();
}

bool CFIBacktraceAndroid::FindCFIRowForPC(uintptr_t func_addr,
                                          CFIBacktraceAndroid::CFIRow* cfi) {
  if (!can_unwind_stack_frames())
    return false;

  auto* cache = GetThreadLocalCFICache();
  *cfi = {0};
  if (cache->Find(func_addr, cfi))
    return true;

  // Consider each column of UNW_INDEX table as arrays of uintptr_t (function
  // addresses) and uint16_t (indices). Define start and end iterator on the
  // first column array (addresses) and use std::lower_bound() to binary search
  // on this array to find the required function address.
  static const uintptr_t* const unw_index_fn_end =
      unw_index_function_col_ + unw_index_row_count_;
  const uintptr_t* found =
      std::lower_bound(unw_index_function_col_, unw_index_fn_end, func_addr);

  // If found is start, then the given function is not in the table. If the
  // given pc is start of a function then we cannot unwind.
  if (found == unw_index_function_col_ || *found == func_addr)
    return false;

  // std::lower_bound() returns the iter that corresponds to the first address
  // that is greater than the given address. So, the required iter is always one
  // less than the value returned by std::lower_bound().
  --found;
  uintptr_t func_start_addr = *found;
  size_t row_num = found - unw_index_function_col_;
  uint16_t index = unw_index_indices_col_[row_num];
  DCHECK_LE(func_start_addr, func_addr);
  // If the index is CANT_UNWIND then we do not have unwind infomation for the
  // function.
  if (index == kCantUnwind)
    return false;

  // The unwind data for the current function is at an offsset of the index
  // found in UNW_INDEX table.
  const uint16_t* unwind_data = unw_data_start_addr_ + index;
  // The value of first 2 bytes is the CFI data row count for the function.
  uint16_t row_count = 0;
  memcpy(&row_count, unwind_data, sizeof(row_count));
  // And the actual CFI rows start after 2 bytes from the |unwind_data|. Cast
  // the data into an array of CFIUnwindDataRow since the struct is designed to
  // represent each row. We should be careful to read only |row_count| number of
  // elements in the array.
  const CFIUnwindDataRow* function_data =
      reinterpret_cast<const CFIUnwindDataRow*>(unwind_data + 1);

  // Iterate through the CFI rows of the function to find the row that gives
  // offset for the given instruction address.
  CFIUnwindDataRow cfi_row = {0, 0};
  uint16_t ra_offset = 0;
  for (uint16_t i = 0; i < row_count; ++i) {
    CFIUnwindDataRow row;
    memcpy(&row, function_data + i, sizeof(CFIUnwindDataRow));
    // The return address of the function is the instruction that is not yet
    // been executed. The cfi row specifies the unwind info before executing the
    // given instruction. If the given address is equal to the instruction
    // offset, then use the current row. Or use the row with highest address
    // less than the given address.
    if (row.addr_offset + func_start_addr > func_addr)
      break;

    cfi_row = row;
    // The ra offset of the last specified row should be used, if unspecified.
    // So, keep updating the RA offset till we reach the correct CFI row.
    // TODO(ssid): This should be fixed in the format and we should always
    // output ra offset.
    if (cfi_row.ra_offset())
      ra_offset = cfi_row.ra_offset();
  }
  DCHECK_NE(0u, cfi_row.addr_offset);
  *cfi = {cfi_row.cfa_offset(), ra_offset};
  DCHECK(cfi->cfa_offset);
  DCHECK(cfi->ra_offset);

  // safe to update since the cache is thread local.
  cache->Add(func_addr, *cfi);
  return true;
}

CFIBacktraceAndroid::CFICache* CFIBacktraceAndroid::GetThreadLocalCFICache() {
  auto* cache = static_cast<CFICache*>(thread_local_cfi_cache_.Get());
  if (!cache) {
    cache = new CFICache();
    thread_local_cfi_cache_.Set(cache);
  }
  return cache;
}

void CFIBacktraceAndroid::CFICache::Add(uintptr_t address, CFIRow cfi) {
  cache_[address % kLimit] = {address, cfi};
}

bool CFIBacktraceAndroid::CFICache::Find(uintptr_t address, CFIRow* cfi) {
  if (cache_[address % kLimit].address == address) {
    *cfi = cache_[address % kLimit].cfi;
    return true;
  }
  return false;
}

}  // namespace trace_event
}  // namespace base
