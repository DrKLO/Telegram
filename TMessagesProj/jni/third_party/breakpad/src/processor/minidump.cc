// Copyright (c) 2010 Google Inc.
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are
// met:
//
//     * Redistributions of source code must retain the above copyright
// notice, this list of conditions and the following disclaimer.
//     * Redistributions in binary form must reproduce the above
// copyright notice, this list of conditions and the following disclaimer
// in the documentation and/or other materials provided with the
// distribution.
//     * Neither the name of Google Inc. nor the names of its
// contributors may be used to endorse or promote products derived from
// this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
// "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
// LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
// A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
// OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
// SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
// LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
// DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
// THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
// OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

// minidump.cc: A minidump reader.
//
// See minidump.h for documentation.
//
// Author: Mark Mentovai

#include "google_breakpad/processor/minidump.h"

#include <assert.h>
#include <fcntl.h>
#include <stddef.h>
#include <stdio.h>
#include <string.h>
#include <time.h>

#ifdef _WIN32
#include <io.h>
#define PRIx64 "llx"
#define PRIx32 "lx"
#define snprintf _snprintf
#else  // _WIN32
#include <unistd.h>
#endif  // _WIN32

#include <fstream>
#include <iostream>
#include <limits>
#include <map>
#include <vector>

#include "processor/range_map-inl.h"

#include "common/scoped_ptr.h"
#include "google_breakpad/processor/dump_context.h"
#include "processor/basic_code_module.h"
#include "processor/basic_code_modules.h"
#include "processor/logging.h"

namespace google_breakpad {


using std::istream;
using std::ifstream;
using std::numeric_limits;
using std::vector;

// Returns true iff |context_size| matches exactly one of the sizes of the
// various MDRawContext* types.
// TODO(blundell): This function can be removed once
// http://code.google.com/p/google-breakpad/issues/detail?id=550 is fixed.
static bool IsContextSizeUnique(uint32_t context_size) {
  int num_matching_contexts = 0;
  if (context_size == sizeof(MDRawContextX86))
    num_matching_contexts++;
  if (context_size == sizeof(MDRawContextPPC))
    num_matching_contexts++;
  if (context_size == sizeof(MDRawContextPPC64))
    num_matching_contexts++;
  if (context_size == sizeof(MDRawContextAMD64))
    num_matching_contexts++;
  if (context_size == sizeof(MDRawContextSPARC))
    num_matching_contexts++;
  if (context_size == sizeof(MDRawContextARM))
    num_matching_contexts++;
  if (context_size == sizeof(MDRawContextARM64))
    num_matching_contexts++;
  if (context_size == sizeof(MDRawContextMIPS))
    num_matching_contexts++;
  return num_matching_contexts == 1;
}

//
// Swapping routines
//
// Inlining these doesn't increase code size significantly, and it saves
// a whole lot of unnecessary jumping back and forth.
//


// Swapping an 8-bit quantity is a no-op.  This function is only provided
// to account for certain templatized operations that require swapping for
// wider types but handle uint8_t too
// (MinidumpMemoryRegion::GetMemoryAtAddressInternal).
static inline void Swap(uint8_t* value) {
}


// Optimization: don't need to AND the furthest right shift, because we're
// shifting an unsigned quantity.  The standard requires zero-filling in this
// case.  If the quantities were signed, a bitmask whould be needed for this
// right shift to avoid an arithmetic shift (which retains the sign bit).
// The furthest left shift never needs to be ANDed bitmask.


static inline void Swap(uint16_t* value) {
  *value = (*value >> 8) |
           (*value << 8);
}


static inline void Swap(uint32_t* value) {
  *value =  (*value >> 24) |
           ((*value >> 8)  & 0x0000ff00) |
           ((*value << 8)  & 0x00ff0000) |
            (*value << 24);
}


static inline void Swap(uint64_t* value) {
  uint32_t* value32 = reinterpret_cast<uint32_t*>(value);
  Swap(&value32[0]);
  Swap(&value32[1]);
  uint32_t temp = value32[0];
  value32[0] = value32[1];
  value32[1] = temp;
}


// Given a pointer to a 128-bit int in the minidump data, set the "low"
// and "high" fields appropriately.
static void Normalize128(uint128_struct* value, bool is_big_endian) {
  // The struct format is [high, low], so if the format is big-endian,
  // the most significant bytes will already be in the high field.
  if (!is_big_endian) {
    uint64_t temp = value->low;
    value->low = value->high;
    value->high = temp;
  }
}

// This just swaps each int64 half of the 128-bit value.
// The value should also be normalized by calling Normalize128().
static void Swap(uint128_struct* value) {
  Swap(&value->low);
  Swap(&value->high);
}

// Swapping signed integers
static inline void Swap(int16_t* value) {
  Swap(reinterpret_cast<uint16_t*>(value));
}

static inline void Swap(int32_t* value) {
  Swap(reinterpret_cast<uint32_t*>(value));
}

static inline void Swap(int64_t* value) {
  Swap(reinterpret_cast<uint64_t*>(value));
}


static inline void Swap(MDLocationDescriptor* location_descriptor) {
  Swap(&location_descriptor->data_size);
  Swap(&location_descriptor->rva);
}


static inline void Swap(MDMemoryDescriptor* memory_descriptor) {
  Swap(&memory_descriptor->start_of_memory_range);
  Swap(&memory_descriptor->memory);
}


static inline void Swap(MDGUID* guid) {
  Swap(&guid->data1);
  Swap(&guid->data2);
  Swap(&guid->data3);
  // Don't swap guid->data4[] because it contains 8-bit quantities.
}

static inline void Swap(MDSystemTime* system_time) {
  Swap(&system_time->year);
  Swap(&system_time->month);
  Swap(&system_time->day_of_week);
  Swap(&system_time->day);
  Swap(&system_time->hour);
  Swap(&system_time->minute);
  Swap(&system_time->second);
  Swap(&system_time->milliseconds);
}

static inline void Swap(uint16_t* data, size_t size_in_bytes) {
  size_t data_length = size_in_bytes / sizeof(data[0]);
  for (size_t i = 0; i < data_length; i++) {
    Swap(&data[i]);
  }
}

//
// Character conversion routines
//


// Standard wide-character conversion routines depend on the system's own
// idea of what width a wide character should be: some use 16 bits, and
// some use 32 bits.  For the purposes of a minidump, wide strings are
// always represented with 16-bit UTF-16 chracters.  iconv isn't available
// everywhere, and its interface varies where it is available.  iconv also
// deals purely with char* pointers, so in addition to considering the swap
// parameter, a converter that uses iconv would also need to take the host
// CPU's endianness into consideration.  It doesn't seems worth the trouble
// of making it a dependency when we don't care about anything but UTF-16.
static string* UTF16ToUTF8(const vector<uint16_t>& in,
                           bool swap) {
  scoped_ptr<string> out(new string());

  // Set the string's initial capacity to the number of UTF-16 characters,
  // because the UTF-8 representation will always be at least this long.
  // If the UTF-8 representation is longer, the string will grow dynamically.
  out->reserve(in.size());

  for (vector<uint16_t>::const_iterator iterator = in.begin();
       iterator != in.end();
       ++iterator) {
    // Get a 16-bit value from the input
    uint16_t in_word = *iterator;
    if (swap)
      Swap(&in_word);

    // Convert the input value (in_word) into a Unicode code point (unichar).
    uint32_t unichar;
    if (in_word >= 0xdc00 && in_word <= 0xdcff) {
      BPLOG(ERROR) << "UTF16ToUTF8 found low surrogate " <<
                      HexString(in_word) << " without high";
      return NULL;
    } else if (in_word >= 0xd800 && in_word <= 0xdbff) {
      // High surrogate.
      unichar = (in_word - 0xd7c0) << 10;
      if (++iterator == in.end()) {
        BPLOG(ERROR) << "UTF16ToUTF8 found high surrogate " <<
                        HexString(in_word) << " at end of string";
        return NULL;
      }
      uint32_t high_word = in_word;
      in_word = *iterator;
      if (in_word < 0xdc00 || in_word > 0xdcff) {
        BPLOG(ERROR) << "UTF16ToUTF8 found high surrogate " <<
                        HexString(high_word) << " without low " <<
                        HexString(in_word);
        return NULL;
      }
      unichar |= in_word & 0x03ff;
    } else {
      // The ordinary case, a single non-surrogate Unicode character encoded
      // as a single 16-bit value.
      unichar = in_word;
    }

    // Convert the Unicode code point (unichar) into its UTF-8 representation,
    // appending it to the out string.
    if (unichar < 0x80) {
      (*out) += static_cast<char>(unichar);
    } else if (unichar < 0x800) {
      (*out) += 0xc0 | static_cast<char>(unichar >> 6);
      (*out) += 0x80 | static_cast<char>(unichar & 0x3f);
    } else if (unichar < 0x10000) {
      (*out) += 0xe0 | static_cast<char>(unichar >> 12);
      (*out) += 0x80 | static_cast<char>((unichar >> 6) & 0x3f);
      (*out) += 0x80 | static_cast<char>(unichar & 0x3f);
    } else if (unichar < 0x200000) {
      (*out) += 0xf0 | static_cast<char>(unichar >> 18);
      (*out) += 0x80 | static_cast<char>((unichar >> 12) & 0x3f);
      (*out) += 0x80 | static_cast<char>((unichar >> 6) & 0x3f);
      (*out) += 0x80 | static_cast<char>(unichar & 0x3f);
    } else {
      BPLOG(ERROR) << "UTF16ToUTF8 cannot represent high value " <<
                      HexString(unichar) << " in UTF-8";
      return NULL;
    }
  }

  return out.release();
}

// Return the smaller of the number of code units in the UTF-16 string,
// not including the terminating null word, or maxlen.
static size_t UTF16codeunits(const uint16_t *string, size_t maxlen) {
  size_t count = 0;
  while (count < maxlen && string[count] != 0)
    count++;
  return count;
}

static inline void Swap(MDTimeZoneInformation* time_zone) {
  Swap(&time_zone->bias);
  // Skip time_zone->standard_name.  No need to swap UTF-16 fields.
  // The swap will be done as part of the conversion to UTF-8.
  Swap(&time_zone->standard_date);
  Swap(&time_zone->standard_bias);
  // Skip time_zone->daylight_name.  No need to swap UTF-16 fields.
  // The swap will be done as part of the conversion to UTF-8.
  Swap(&time_zone->daylight_date);
  Swap(&time_zone->daylight_bias);
}

static void ConvertUTF16BufferToUTF8String(const uint16_t* utf16_data,
                                           size_t max_length_in_bytes,
                                           string* utf8_result,
                                           bool swap) {
  // Since there is no explicit byte length for each string, use
  // UTF16codeunits to calculate word length, then derive byte
  // length from that.
  size_t max_word_length = max_length_in_bytes / sizeof(utf16_data[0]);
  size_t word_length = UTF16codeunits(utf16_data, max_word_length);
  if (word_length > 0) {
    size_t byte_length = word_length * sizeof(utf16_data[0]);
    vector<uint16_t> utf16_vector(word_length);
    memcpy(&utf16_vector[0], &utf16_data[0], byte_length);
    scoped_ptr<string> temp(UTF16ToUTF8(utf16_vector, swap));
    if (temp.get()) {
      utf8_result->assign(*temp);
    }
  } else {
    utf8_result->clear();
  }
}


// For fields that may or may not be valid, PrintValueOrInvalid will print the
// string "(invalid)" if the field is not valid, and will print the value if
// the field is valid. The value is printed as hexadecimal or decimal.

enum NumberFormat {
  kNumberFormatDecimal,
  kNumberFormatHexadecimal,
};

static void PrintValueOrInvalid(bool valid,
                                NumberFormat number_format,
                                uint32_t value) {
  if (!valid) {
    printf("(invalid)\n");
  } else if (number_format == kNumberFormatDecimal) {
    printf("%d\n", value);
  } else {
    printf("0x%x\n", value);
  }
}

// Converts a time_t to a string showing the time in UTC.
string TimeTToUTCString(time_t tt) {
  struct tm timestruct;
#ifdef _WIN32
  gmtime_s(&timestruct, &tt);
#else
  gmtime_r(&tt, &timestruct);
#endif

  char timestr[20];
  int rv = strftime(timestr, 20, "%Y-%m-%d %H:%M:%S", &timestruct);
  if (rv == 0) {
    return string();
  }

  return string(timestr);
}


//
// MinidumpObject
//


MinidumpObject::MinidumpObject(Minidump* minidump)
    : DumpObject(),
      minidump_(minidump) {
}


//
// MinidumpStream
//


MinidumpStream::MinidumpStream(Minidump* minidump)
    : MinidumpObject(minidump) {
}


//
// MinidumpContext
//


MinidumpContext::MinidumpContext(Minidump* minidump)
    : DumpContext(),
      minidump_(minidump) {
}

MinidumpContext::~MinidumpContext() {
}

bool MinidumpContext::Read(uint32_t expected_size) {
  valid_ = false;

  // Certain raw context types are currently assumed to have unique sizes.
  if (!IsContextSizeUnique(sizeof(MDRawContextAMD64))) {
    BPLOG(ERROR) << "sizeof(MDRawContextAMD64) cannot match the size of any "
                 << "other raw context";
    return false;
  }
  if (!IsContextSizeUnique(sizeof(MDRawContextPPC64))) {
    BPLOG(ERROR) << "sizeof(MDRawContextPPC64) cannot match the size of any "
                 << "other raw context";
    return false;
  }
  if (!IsContextSizeUnique(sizeof(MDRawContextARM64))) {
    BPLOG(ERROR) << "sizeof(MDRawContextARM64) cannot match the size of any "
                 << "other raw context";
    return false;
  }

  FreeContext();

  // First, figure out what type of CPU this context structure is for.
  // For some reason, the AMD64 Context doesn't have context_flags
  // at the beginning of the structure, so special case it here.
  if (expected_size == sizeof(MDRawContextAMD64)) {
    BPLOG(INFO) << "MinidumpContext: looks like AMD64 context";

    scoped_ptr<MDRawContextAMD64> context_amd64(new MDRawContextAMD64());
    if (!minidump_->ReadBytes(context_amd64.get(),
                              sizeof(MDRawContextAMD64))) {
      BPLOG(ERROR) << "MinidumpContext could not read amd64 context";
      return false;
    }

    if (minidump_->swap())
      Swap(&context_amd64->context_flags);

    uint32_t cpu_type = context_amd64->context_flags & MD_CONTEXT_CPU_MASK;
    if (cpu_type == 0) {
      if (minidump_->GetContextCPUFlagsFromSystemInfo(&cpu_type)) {
        context_amd64->context_flags |= cpu_type;
      } else {
        BPLOG(ERROR) << "Failed to preserve the current stream position";
        return false;
      }
    }

    if (cpu_type != MD_CONTEXT_AMD64) {
      // TODO: Fall through to switch below.
      // http://code.google.com/p/google-breakpad/issues/detail?id=550
      BPLOG(ERROR) << "MinidumpContext not actually amd64 context";
      return false;
    }

    // Do this after reading the entire MDRawContext structure because
    // GetSystemInfo may seek minidump to a new position.
    if (!CheckAgainstSystemInfo(cpu_type)) {
      BPLOG(ERROR) << "MinidumpContext amd64 does not match system info";
      return false;
    }

    // Normalize the 128-bit types in the dump.
    // Since this is AMD64, by definition, the values are little-endian.
    for (unsigned int vr_index = 0;
         vr_index < MD_CONTEXT_AMD64_VR_COUNT;
         ++vr_index)
      Normalize128(&context_amd64->vector_register[vr_index], false);

    if (minidump_->swap()) {
      Swap(&context_amd64->p1_home);
      Swap(&context_amd64->p2_home);
      Swap(&context_amd64->p3_home);
      Swap(&context_amd64->p4_home);
      Swap(&context_amd64->p5_home);
      Swap(&context_amd64->p6_home);
      // context_flags is already swapped
      Swap(&context_amd64->mx_csr);
      Swap(&context_amd64->cs);
      Swap(&context_amd64->ds);
      Swap(&context_amd64->es);
      Swap(&context_amd64->fs);
      Swap(&context_amd64->ss);
      Swap(&context_amd64->eflags);
      Swap(&context_amd64->dr0);
      Swap(&context_amd64->dr1);
      Swap(&context_amd64->dr2);
      Swap(&context_amd64->dr3);
      Swap(&context_amd64->dr6);
      Swap(&context_amd64->dr7);
      Swap(&context_amd64->rax);
      Swap(&context_amd64->rcx);
      Swap(&context_amd64->rdx);
      Swap(&context_amd64->rbx);
      Swap(&context_amd64->rsp);
      Swap(&context_amd64->rbp);
      Swap(&context_amd64->rsi);
      Swap(&context_amd64->rdi);
      Swap(&context_amd64->r8);
      Swap(&context_amd64->r9);
      Swap(&context_amd64->r10);
      Swap(&context_amd64->r11);
      Swap(&context_amd64->r12);
      Swap(&context_amd64->r13);
      Swap(&context_amd64->r14);
      Swap(&context_amd64->r15);
      Swap(&context_amd64->rip);
      // FIXME: I'm not sure what actually determines
      // which member of the union {flt_save, sse_registers}
      // is valid.  We're not currently using either,
      // but it would be good to have them swapped properly.

      for (unsigned int vr_index = 0;
           vr_index < MD_CONTEXT_AMD64_VR_COUNT;
           ++vr_index)
        Swap(&context_amd64->vector_register[vr_index]);
      Swap(&context_amd64->vector_control);
      Swap(&context_amd64->debug_control);
      Swap(&context_amd64->last_branch_to_rip);
      Swap(&context_amd64->last_branch_from_rip);
      Swap(&context_amd64->last_exception_to_rip);
      Swap(&context_amd64->last_exception_from_rip);
    }

    SetContextFlags(context_amd64->context_flags);

    SetContextAMD64(context_amd64.release());
  } else if (expected_size == sizeof(MDRawContextPPC64)) {
    // |context_flags| of MDRawContextPPC64 is 64 bits, but other MDRawContext
    // in the else case have 32 bits |context_flags|, so special case it here.
    uint64_t context_flags;
    if (!minidump_->ReadBytes(&context_flags, sizeof(context_flags))) {
      BPLOG(ERROR) << "MinidumpContext could not read context flags";
      return false;
    }
    if (minidump_->swap())
      Swap(&context_flags);

    uint32_t cpu_type = context_flags & MD_CONTEXT_CPU_MASK;
    scoped_ptr<MDRawContextPPC64> context_ppc64(new MDRawContextPPC64());

    if (cpu_type == 0) {
      if (minidump_->GetContextCPUFlagsFromSystemInfo(&cpu_type)) {
        context_ppc64->context_flags |= cpu_type;
      } else {
        BPLOG(ERROR) << "Failed to preserve the current stream position";
        return false;
      }
    }

    if (cpu_type != MD_CONTEXT_PPC64) {
      // TODO: Fall through to switch below.
      // http://code.google.com/p/google-breakpad/issues/detail?id=550
      BPLOG(ERROR) << "MinidumpContext not actually ppc64 context";
      return false;
    }

    // Set the context_flags member, which has already been read, and
    // read the rest of the structure beginning with the first member
    // after context_flags.
    context_ppc64->context_flags = context_flags;

    size_t flags_size = sizeof(context_ppc64->context_flags);
    uint8_t* context_after_flags =
          reinterpret_cast<uint8_t*>(context_ppc64.get()) + flags_size;
    if (!minidump_->ReadBytes(context_after_flags,
                              sizeof(MDRawContextPPC64) - flags_size)) {
      BPLOG(ERROR) << "MinidumpContext could not read ppc64 context";
      return false;
    }

    // Do this after reading the entire MDRawContext structure because
    // GetSystemInfo may seek minidump to a new position.
    if (!CheckAgainstSystemInfo(cpu_type)) {
      BPLOG(ERROR) << "MinidumpContext ppc64 does not match system info";
      return false;
    }
    if (minidump_->swap()) {
      // context_ppc64->context_flags was already swapped.
      Swap(&context_ppc64->srr0);
      Swap(&context_ppc64->srr1);
      for (unsigned int gpr_index = 0;
           gpr_index < MD_CONTEXT_PPC64_GPR_COUNT;
           ++gpr_index) {
        Swap(&context_ppc64->gpr[gpr_index]);
      }
      Swap(&context_ppc64->cr);
      Swap(&context_ppc64->xer);
      Swap(&context_ppc64->lr);
      Swap(&context_ppc64->ctr);
      Swap(&context_ppc64->vrsave);
      for (unsigned int fpr_index = 0;
           fpr_index < MD_FLOATINGSAVEAREA_PPC_FPR_COUNT;
           ++fpr_index) {
        Swap(&context_ppc64->float_save.fpregs[fpr_index]);
      }
      // Don't swap context_ppc64->float_save.fpscr_pad because it is only
      // used for padding.
      Swap(&context_ppc64->float_save.fpscr);
      for (unsigned int vr_index = 0;
           vr_index < MD_VECTORSAVEAREA_PPC_VR_COUNT;
           ++vr_index) {
        Normalize128(&context_ppc64->vector_save.save_vr[vr_index], true);
        Swap(&context_ppc64->vector_save.save_vr[vr_index]);
      }
      Swap(&context_ppc64->vector_save.save_vscr);
      // Don't swap the padding fields in vector_save.
      Swap(&context_ppc64->vector_save.save_vrvalid);
    }

    SetContextFlags(static_cast<uint32_t>(context_ppc64->context_flags));

    // Check for data loss when converting context flags from uint64_t into
    // uint32_t
    if (static_cast<uint64_t>(GetContextFlags()) !=
        context_ppc64->context_flags) {
      BPLOG(ERROR) << "Data loss detected when converting PPC64 context_flags";
      return false;
    }

    SetContextPPC64(context_ppc64.release());
  } else if (expected_size == sizeof(MDRawContextARM64)) {
    // |context_flags| of MDRawContextARM64 is 64 bits, but other MDRawContext
    // in the else case have 32 bits |context_flags|, so special case it here.
    uint64_t context_flags;

    BPLOG(INFO) << "MinidumpContext: looks like ARM64 context";

    if (!minidump_->ReadBytes(&context_flags, sizeof(context_flags))) {
      BPLOG(ERROR) << "MinidumpContext could not read context flags";
      return false;
    }
    if (minidump_->swap())
      Swap(&context_flags);

    scoped_ptr<MDRawContextARM64> context_arm64(new MDRawContextARM64());

    uint32_t cpu_type = context_flags & MD_CONTEXT_CPU_MASK;
    if (cpu_type == 0) {
      if (minidump_->GetContextCPUFlagsFromSystemInfo(&cpu_type)) {
        context_arm64->context_flags |= cpu_type;
      } else {
        BPLOG(ERROR) << "Failed to preserve the current stream position";
        return false;
      }
    }

    if (cpu_type != MD_CONTEXT_ARM64) {
      // TODO: Fall through to switch below.
      // http://code.google.com/p/google-breakpad/issues/detail?id=550
      BPLOG(ERROR) << "MinidumpContext not actually arm64 context";
      return false;
    }

    // Set the context_flags member, which has already been read, and
    // read the rest of the structure beginning with the first member
    // after context_flags.
    context_arm64->context_flags = context_flags;

    size_t flags_size = sizeof(context_arm64->context_flags);
    uint8_t* context_after_flags =
        reinterpret_cast<uint8_t*>(context_arm64.get()) + flags_size;
    if (!minidump_->ReadBytes(context_after_flags,
                              sizeof(MDRawContextARM64) - flags_size)) {
      BPLOG(ERROR) << "MinidumpContext could not read arm64 context";
      return false;
    }

    // Do this after reading the entire MDRawContext structure because
    // GetSystemInfo may seek minidump to a new position.
    if (!CheckAgainstSystemInfo(cpu_type)) {
      BPLOG(ERROR) << "MinidumpContext arm64 does not match system info";
      return false;
    }

    if (minidump_->swap()) {
      // context_arm64->context_flags was already swapped.
      for (unsigned int ireg_index = 0;
           ireg_index < MD_CONTEXT_ARM64_GPR_COUNT;
           ++ireg_index) {
        Swap(&context_arm64->iregs[ireg_index]);
      }
      Swap(&context_arm64->cpsr);
      Swap(&context_arm64->float_save.fpsr);
      Swap(&context_arm64->float_save.fpcr);
      for (unsigned int fpr_index = 0;
           fpr_index < MD_FLOATINGSAVEAREA_ARM64_FPR_COUNT;
           ++fpr_index) {
        // While ARM64 is bi-endian, iOS (currently the only platform
        // for which ARM64 support has been brought up) uses ARM64 exclusively
        // in little-endian mode.
        Normalize128(&context_arm64->float_save.regs[fpr_index], false);
        Swap(&context_arm64->float_save.regs[fpr_index]);
      }
    }
    SetContextFlags(static_cast<uint32_t>(context_arm64->context_flags));

    // Check for data loss when converting context flags from uint64_t into
    // uint32_t
    if (static_cast<uint64_t>(GetContextFlags()) !=
        context_arm64->context_flags) {
      BPLOG(ERROR) << "Data loss detected when converting ARM64 context_flags";
      return false;
    }

    SetContextARM64(context_arm64.release());
  } else {
    uint32_t context_flags;
    if (!minidump_->ReadBytes(&context_flags, sizeof(context_flags))) {
      BPLOG(ERROR) << "MinidumpContext could not read context flags";
      return false;
    }
    if (minidump_->swap())
      Swap(&context_flags);

    uint32_t cpu_type = context_flags & MD_CONTEXT_CPU_MASK;
    if (cpu_type == 0) {
      // Unfortunately the flag for MD_CONTEXT_ARM that was taken
      // from a Windows CE SDK header conflicts in practice with
      // the CONTEXT_XSTATE flag. MD_CONTEXT_ARM has been renumbered,
      // but handle dumps with the legacy value gracefully here.
      if (context_flags & MD_CONTEXT_ARM_OLD) {
        context_flags |= MD_CONTEXT_ARM;
        context_flags &= ~MD_CONTEXT_ARM_OLD;
        cpu_type = MD_CONTEXT_ARM;
      }
    }

    if (cpu_type == 0) {
      if (minidump_->GetContextCPUFlagsFromSystemInfo(&cpu_type)) {
        context_flags |= cpu_type;
      } else {
        BPLOG(ERROR) << "Failed to preserve the current stream position";
        return false;
      }
    }

    // Allocate the context structure for the correct CPU and fill it.  The
    // casts are slightly unorthodox, but it seems better to do that than to
    // maintain a separate pointer for each type of CPU context structure
    // when only one of them will be used.
    switch (cpu_type) {
      case MD_CONTEXT_X86: {
        if (expected_size != sizeof(MDRawContextX86)) {
          BPLOG(ERROR) << "MinidumpContext x86 size mismatch, " <<
            expected_size << " != " << sizeof(MDRawContextX86);
          return false;
        }

        scoped_ptr<MDRawContextX86> context_x86(new MDRawContextX86());

        // Set the context_flags member, which has already been read, and
        // read the rest of the structure beginning with the first member
        // after context_flags.
        context_x86->context_flags = context_flags;

        size_t flags_size = sizeof(context_x86->context_flags);
        uint8_t* context_after_flags =
          reinterpret_cast<uint8_t*>(context_x86.get()) + flags_size;
        if (!minidump_->ReadBytes(context_after_flags,
                                  sizeof(MDRawContextX86) - flags_size)) {
          BPLOG(ERROR) << "MinidumpContext could not read x86 context";
          return false;
        }

        // Do this after reading the entire MDRawContext structure because
        // GetSystemInfo may seek minidump to a new position.
        if (!CheckAgainstSystemInfo(cpu_type)) {
          BPLOG(ERROR) << "MinidumpContext x86 does not match system info";
          return false;
        }

        if (minidump_->swap()) {
          // context_x86->context_flags was already swapped.
          Swap(&context_x86->dr0);
          Swap(&context_x86->dr1);
          Swap(&context_x86->dr2);
          Swap(&context_x86->dr3);
          Swap(&context_x86->dr6);
          Swap(&context_x86->dr7);
          Swap(&context_x86->float_save.control_word);
          Swap(&context_x86->float_save.status_word);
          Swap(&context_x86->float_save.tag_word);
          Swap(&context_x86->float_save.error_offset);
          Swap(&context_x86->float_save.error_selector);
          Swap(&context_x86->float_save.data_offset);
          Swap(&context_x86->float_save.data_selector);
          // context_x86->float_save.register_area[] contains 8-bit quantities
          // and does not need to be swapped.
          Swap(&context_x86->float_save.cr0_npx_state);
          Swap(&context_x86->gs);
          Swap(&context_x86->fs);
          Swap(&context_x86->es);
          Swap(&context_x86->ds);
          Swap(&context_x86->edi);
          Swap(&context_x86->esi);
          Swap(&context_x86->ebx);
          Swap(&context_x86->edx);
          Swap(&context_x86->ecx);
          Swap(&context_x86->eax);
          Swap(&context_x86->ebp);
          Swap(&context_x86->eip);
          Swap(&context_x86->cs);
          Swap(&context_x86->eflags);
          Swap(&context_x86->esp);
          Swap(&context_x86->ss);
          // context_x86->extended_registers[] contains 8-bit quantities and
          // does not need to be swapped.
        }

        SetContextX86(context_x86.release());

        break;
      }

      case MD_CONTEXT_PPC: {
        if (expected_size != sizeof(MDRawContextPPC)) {
          BPLOG(ERROR) << "MinidumpContext ppc size mismatch, " <<
            expected_size << " != " << sizeof(MDRawContextPPC);
          return false;
        }

        scoped_ptr<MDRawContextPPC> context_ppc(new MDRawContextPPC());

        // Set the context_flags member, which has already been read, and
        // read the rest of the structure beginning with the first member
        // after context_flags.
        context_ppc->context_flags = context_flags;

        size_t flags_size = sizeof(context_ppc->context_flags);
        uint8_t* context_after_flags =
          reinterpret_cast<uint8_t*>(context_ppc.get()) + flags_size;
        if (!minidump_->ReadBytes(context_after_flags,
                                  sizeof(MDRawContextPPC) - flags_size)) {
          BPLOG(ERROR) << "MinidumpContext could not read ppc context";
          return false;
        }

        // Do this after reading the entire MDRawContext structure because
        // GetSystemInfo may seek minidump to a new position.
        if (!CheckAgainstSystemInfo(cpu_type)) {
          BPLOG(ERROR) << "MinidumpContext ppc does not match system info";
          return false;
        }

        // Normalize the 128-bit types in the dump.
        // Since this is PowerPC, by definition, the values are big-endian.
        for (unsigned int vr_index = 0;
             vr_index < MD_VECTORSAVEAREA_PPC_VR_COUNT;
             ++vr_index) {
          Normalize128(&context_ppc->vector_save.save_vr[vr_index], true);
        }

        if (minidump_->swap()) {
          // context_ppc->context_flags was already swapped.
          Swap(&context_ppc->srr0);
          Swap(&context_ppc->srr1);
          for (unsigned int gpr_index = 0;
               gpr_index < MD_CONTEXT_PPC_GPR_COUNT;
               ++gpr_index) {
            Swap(&context_ppc->gpr[gpr_index]);
          }
          Swap(&context_ppc->cr);
          Swap(&context_ppc->xer);
          Swap(&context_ppc->lr);
          Swap(&context_ppc->ctr);
          Swap(&context_ppc->mq);
          Swap(&context_ppc->vrsave);
          for (unsigned int fpr_index = 0;
               fpr_index < MD_FLOATINGSAVEAREA_PPC_FPR_COUNT;
               ++fpr_index) {
            Swap(&context_ppc->float_save.fpregs[fpr_index]);
          }
          // Don't swap context_ppc->float_save.fpscr_pad because it is only
          // used for padding.
          Swap(&context_ppc->float_save.fpscr);
          for (unsigned int vr_index = 0;
               vr_index < MD_VECTORSAVEAREA_PPC_VR_COUNT;
               ++vr_index) {
            Swap(&context_ppc->vector_save.save_vr[vr_index]);
          }
          Swap(&context_ppc->vector_save.save_vscr);
          // Don't swap the padding fields in vector_save.
          Swap(&context_ppc->vector_save.save_vrvalid);
        }

        SetContextPPC(context_ppc.release());

        break;
      }

      case MD_CONTEXT_SPARC: {
        if (expected_size != sizeof(MDRawContextSPARC)) {
          BPLOG(ERROR) << "MinidumpContext sparc size mismatch, " <<
            expected_size << " != " << sizeof(MDRawContextSPARC);
          return false;
        }

        scoped_ptr<MDRawContextSPARC> context_sparc(new MDRawContextSPARC());

        // Set the context_flags member, which has already been read, and
        // read the rest of the structure beginning with the first member
        // after context_flags.
        context_sparc->context_flags = context_flags;

        size_t flags_size = sizeof(context_sparc->context_flags);
        uint8_t* context_after_flags =
            reinterpret_cast<uint8_t*>(context_sparc.get()) + flags_size;
        if (!minidump_->ReadBytes(context_after_flags,
                                  sizeof(MDRawContextSPARC) - flags_size)) {
          BPLOG(ERROR) << "MinidumpContext could not read sparc context";
          return false;
        }

        // Do this after reading the entire MDRawContext structure because
        // GetSystemInfo may seek minidump to a new position.
        if (!CheckAgainstSystemInfo(cpu_type)) {
          BPLOG(ERROR) << "MinidumpContext sparc does not match system info";
          return false;
        }

        if (minidump_->swap()) {
          // context_sparc->context_flags was already swapped.
          for (unsigned int gpr_index = 0;
               gpr_index < MD_CONTEXT_SPARC_GPR_COUNT;
               ++gpr_index) {
            Swap(&context_sparc->g_r[gpr_index]);
          }
          Swap(&context_sparc->ccr);
          Swap(&context_sparc->pc);
          Swap(&context_sparc->npc);
          Swap(&context_sparc->y);
          Swap(&context_sparc->asi);
          Swap(&context_sparc->fprs);
          for (unsigned int fpr_index = 0;
               fpr_index < MD_FLOATINGSAVEAREA_SPARC_FPR_COUNT;
               ++fpr_index) {
            Swap(&context_sparc->float_save.regs[fpr_index]);
          }
          Swap(&context_sparc->float_save.filler);
          Swap(&context_sparc->float_save.fsr);
        }
        SetContextSPARC(context_sparc.release());

        break;
      }

      case MD_CONTEXT_ARM: {
        if (expected_size != sizeof(MDRawContextARM)) {
          BPLOG(ERROR) << "MinidumpContext arm size mismatch, " <<
            expected_size << " != " << sizeof(MDRawContextARM);
          return false;
        }

        scoped_ptr<MDRawContextARM> context_arm(new MDRawContextARM());

        // Set the context_flags member, which has already been read, and
        // read the rest of the structure beginning with the first member
        // after context_flags.
        context_arm->context_flags = context_flags;

        size_t flags_size = sizeof(context_arm->context_flags);
        uint8_t* context_after_flags =
            reinterpret_cast<uint8_t*>(context_arm.get()) + flags_size;
        if (!minidump_->ReadBytes(context_after_flags,
                                  sizeof(MDRawContextARM) - flags_size)) {
          BPLOG(ERROR) << "MinidumpContext could not read arm context";
          return false;
        }

        // Do this after reading the entire MDRawContext structure because
        // GetSystemInfo may seek minidump to a new position.
        if (!CheckAgainstSystemInfo(cpu_type)) {
          BPLOG(ERROR) << "MinidumpContext arm does not match system info";
          return false;
        }

        if (minidump_->swap()) {
          // context_arm->context_flags was already swapped.
          for (unsigned int ireg_index = 0;
               ireg_index < MD_CONTEXT_ARM_GPR_COUNT;
               ++ireg_index) {
            Swap(&context_arm->iregs[ireg_index]);
          }
          Swap(&context_arm->cpsr);
          Swap(&context_arm->float_save.fpscr);
          for (unsigned int fpr_index = 0;
               fpr_index < MD_FLOATINGSAVEAREA_ARM_FPR_COUNT;
               ++fpr_index) {
            Swap(&context_arm->float_save.regs[fpr_index]);
          }
          for (unsigned int fpe_index = 0;
               fpe_index < MD_FLOATINGSAVEAREA_ARM_FPEXTRA_COUNT;
               ++fpe_index) {
            Swap(&context_arm->float_save.extra[fpe_index]);
          }
        }
        SetContextARM(context_arm.release());

        break;
      }

      case MD_CONTEXT_MIPS: {
        if (expected_size != sizeof(MDRawContextMIPS)) {
          BPLOG(ERROR) << "MinidumpContext MIPS size mismatch, "
                       << expected_size
                       << " != "
                       << sizeof(MDRawContextMIPS);
          return false;
        }

        scoped_ptr<MDRawContextMIPS> context_mips(new MDRawContextMIPS());

        // Set the context_flags member, which has already been read, and
        // read the rest of the structure beginning with the first member
        // after context_flags.
        context_mips->context_flags = context_flags;

        size_t flags_size = sizeof(context_mips->context_flags);
        uint8_t* context_after_flags =
            reinterpret_cast<uint8_t*>(context_mips.get()) + flags_size;
        if (!minidump_->ReadBytes(context_after_flags,
                                  sizeof(MDRawContextMIPS) - flags_size)) {
          BPLOG(ERROR) << "MinidumpContext could not read MIPS context";
          return false;
        }

        // Do this after reading the entire MDRawContext structure because
        // GetSystemInfo may seek minidump to a new position.
        if (!CheckAgainstSystemInfo(cpu_type)) {
          BPLOG(ERROR) << "MinidumpContext MIPS does not match system info";
          return false;
        }

        if (minidump_->swap()) {
          // context_mips->context_flags was already swapped.
          for (int ireg_index = 0;
               ireg_index < MD_CONTEXT_MIPS_GPR_COUNT;
               ++ireg_index) {
            Swap(&context_mips->iregs[ireg_index]);
          }
	  Swap(&context_mips->mdhi);
	  Swap(&context_mips->mdlo);
          for (int dsp_index = 0;
               dsp_index < MD_CONTEXT_MIPS_DSP_COUNT;
               ++dsp_index) {
            Swap(&context_mips->hi[dsp_index]);
            Swap(&context_mips->lo[dsp_index]);
          }
	  Swap(&context_mips->dsp_control);
          Swap(&context_mips->epc);
          Swap(&context_mips->badvaddr);
          Swap(&context_mips->status);
          Swap(&context_mips->cause);
          for (int fpr_index = 0;
               fpr_index < MD_FLOATINGSAVEAREA_MIPS_FPR_COUNT;
               ++fpr_index) {
            Swap(&context_mips->float_save.regs[fpr_index]);
          }
          Swap(&context_mips->float_save.fpcsr);
          Swap(&context_mips->float_save.fir);
        }
        SetContextMIPS(context_mips.release());

        break;
      }

      default: {
        // Unknown context type - Don't log as an error yet. Let the
        // caller work that out.
        BPLOG(INFO) << "MinidumpContext unknown context type " <<
          HexString(cpu_type);
        return false;
        break;
      }
    }
    SetContextFlags(context_flags);
  }

  valid_ = true;
  return true;
}

bool MinidumpContext::CheckAgainstSystemInfo(uint32_t context_cpu_type) {
  // It's OK if the minidump doesn't contain an MD_SYSTEM_INFO_STREAM,
  // as this function just implements a sanity check.
  MinidumpSystemInfo* system_info = minidump_->GetSystemInfo();
  if (!system_info) {
    BPLOG(INFO) << "MinidumpContext could not be compared against "
                   "MinidumpSystemInfo";
    return true;
  }

  // If there is an MD_SYSTEM_INFO_STREAM, it should contain valid system info.
  const MDRawSystemInfo* raw_system_info = system_info->system_info();
  if (!raw_system_info) {
    BPLOG(INFO) << "MinidumpContext could not be compared against "
                   "MDRawSystemInfo";
    return false;
  }

  MDCPUArchitecture system_info_cpu_type = static_cast<MDCPUArchitecture>(
      raw_system_info->processor_architecture);

  // Compare the CPU type of the context record to the CPU type in the
  // minidump's system info stream.
  bool return_value = false;
  switch (context_cpu_type) {
    case MD_CONTEXT_X86:
      if (system_info_cpu_type == MD_CPU_ARCHITECTURE_X86 ||
          system_info_cpu_type == MD_CPU_ARCHITECTURE_X86_WIN64 ||
          system_info_cpu_type == MD_CPU_ARCHITECTURE_AMD64) {
        return_value = true;
      }
      break;

    case MD_CONTEXT_PPC:
      if (system_info_cpu_type == MD_CPU_ARCHITECTURE_PPC)
        return_value = true;
      break;

    case MD_CONTEXT_PPC64:
      if (system_info_cpu_type == MD_CPU_ARCHITECTURE_PPC64)
        return_value = true;
      break;

    case MD_CONTEXT_AMD64:
      if (system_info_cpu_type == MD_CPU_ARCHITECTURE_AMD64)
        return_value = true;
      break;

    case MD_CONTEXT_SPARC:
      if (system_info_cpu_type == MD_CPU_ARCHITECTURE_SPARC)
        return_value = true;
      break;

    case MD_CONTEXT_ARM:
      if (system_info_cpu_type == MD_CPU_ARCHITECTURE_ARM)
        return_value = true;
      break;

    case MD_CONTEXT_ARM64:
      if (system_info_cpu_type == MD_CPU_ARCHITECTURE_ARM64)
        return_value = true;
      break;

    case MD_CONTEXT_MIPS:
      if (system_info_cpu_type == MD_CPU_ARCHITECTURE_MIPS)
        return_value = true;
      break;
  }

  BPLOG_IF(ERROR, !return_value) << "MinidumpContext CPU " <<
                                    HexString(context_cpu_type) <<
                                    " wrong for MinidumpSystemInfo CPU " <<
                                    HexString(system_info_cpu_type);

  return return_value;
}


//
// MinidumpMemoryRegion
//


uint32_t MinidumpMemoryRegion::max_bytes_ = 1024 * 1024;  // 1MB


MinidumpMemoryRegion::MinidumpMemoryRegion(Minidump* minidump)
    : MinidumpObject(minidump),
      descriptor_(NULL),
      memory_(NULL) {
}


MinidumpMemoryRegion::~MinidumpMemoryRegion() {
  delete memory_;
}


void MinidumpMemoryRegion::SetDescriptor(MDMemoryDescriptor* descriptor) {
  descriptor_ = descriptor;
  valid_ = descriptor &&
           descriptor_->memory.data_size <=
               numeric_limits<uint64_t>::max() -
               descriptor_->start_of_memory_range;
}


const uint8_t* MinidumpMemoryRegion::GetMemory() const {
  if (!valid_) {
    BPLOG(ERROR) << "Invalid MinidumpMemoryRegion for GetMemory";
    return NULL;
  }

  if (!memory_) {
    if (descriptor_->memory.data_size == 0) {
      BPLOG(ERROR) << "MinidumpMemoryRegion is empty";
      return NULL;
    }

    if (!minidump_->SeekSet(descriptor_->memory.rva)) {
      BPLOG(ERROR) << "MinidumpMemoryRegion could not seek to memory region";
      return NULL;
    }

    if (descriptor_->memory.data_size > max_bytes_) {
      BPLOG(ERROR) << "MinidumpMemoryRegion size " <<
                      descriptor_->memory.data_size << " exceeds maximum " <<
                      max_bytes_;
      return NULL;
    }

    scoped_ptr< vector<uint8_t> > memory(
        new vector<uint8_t>(descriptor_->memory.data_size));

    if (!minidump_->ReadBytes(&(*memory)[0], descriptor_->memory.data_size)) {
      BPLOG(ERROR) << "MinidumpMemoryRegion could not read memory region";
      return NULL;
    }

    memory_ = memory.release();
  }

  return &(*memory_)[0];
}


uint64_t MinidumpMemoryRegion::GetBase() const {
  if (!valid_) {
    BPLOG(ERROR) << "Invalid MinidumpMemoryRegion for GetBase";
    return static_cast<uint64_t>(-1);
  }

  return descriptor_->start_of_memory_range;
}


uint32_t MinidumpMemoryRegion::GetSize() const {
  if (!valid_) {
    BPLOG(ERROR) << "Invalid MinidumpMemoryRegion for GetSize";
    return 0;
  }

  return descriptor_->memory.data_size;
}


void MinidumpMemoryRegion::FreeMemory() {
  delete memory_;
  memory_ = NULL;
}


template<typename T>
bool MinidumpMemoryRegion::GetMemoryAtAddressInternal(uint64_t address,
                                                      T*        value) const {
  BPLOG_IF(ERROR, !value) << "MinidumpMemoryRegion::GetMemoryAtAddressInternal "
                             "requires |value|";
  assert(value);
  *value = 0;

  if (!valid_) {
    BPLOG(ERROR) << "Invalid MinidumpMemoryRegion for "
                    "GetMemoryAtAddressInternal";
    return false;
  }

  // Common failure case
  if (address < descriptor_->start_of_memory_range ||
      sizeof(T) > numeric_limits<uint64_t>::max() - address ||
      address + sizeof(T) > descriptor_->start_of_memory_range +
                            descriptor_->memory.data_size) {
    BPLOG(INFO) << "MinidumpMemoryRegion request out of range: " <<
                    HexString(address) << "+" << sizeof(T) << "/" <<
                    HexString(descriptor_->start_of_memory_range) << "+" <<
                    HexString(descriptor_->memory.data_size);
    return false;
  }

  const uint8_t* memory = GetMemory();
  if (!memory) {
    // GetMemory already logged a perfectly good message.
    return false;
  }

  // If the CPU requires memory accesses to be aligned, this can crash.
  // x86 and ppc are able to cope, though.
  *value = *reinterpret_cast<const T*>(
      &memory[address - descriptor_->start_of_memory_range]);

  if (minidump_->swap())
    Swap(value);

  return true;
}


bool MinidumpMemoryRegion::GetMemoryAtAddress(uint64_t  address,
                                              uint8_t*  value) const {
  return GetMemoryAtAddressInternal(address, value);
}


bool MinidumpMemoryRegion::GetMemoryAtAddress(uint64_t  address,
                                              uint16_t* value) const {
  return GetMemoryAtAddressInternal(address, value);
}


bool MinidumpMemoryRegion::GetMemoryAtAddress(uint64_t  address,
                                              uint32_t* value) const {
  return GetMemoryAtAddressInternal(address, value);
}


bool MinidumpMemoryRegion::GetMemoryAtAddress(uint64_t  address,
                                              uint64_t* value) const {
  return GetMemoryAtAddressInternal(address, value);
}


void MinidumpMemoryRegion::Print() const {
  if (!valid_) {
    BPLOG(ERROR) << "MinidumpMemoryRegion cannot print invalid data";
    return;
  }

  const uint8_t* memory = GetMemory();
  if (memory) {
    printf("0x");
    for (unsigned int byte_index = 0;
         byte_index < descriptor_->memory.data_size;
         byte_index++) {
      printf("%02x", memory[byte_index]);
    }
    printf("\n");
  } else {
    printf("No memory\n");
  }
}


//
// MinidumpThread
//


MinidumpThread::MinidumpThread(Minidump* minidump)
    : MinidumpObject(minidump),
      thread_(),
      memory_(NULL),
      context_(NULL) {
}


MinidumpThread::~MinidumpThread() {
  delete memory_;
  delete context_;
}


bool MinidumpThread::Read() {
  // Invalidate cached data.
  delete memory_;
  memory_ = NULL;
  delete context_;
  context_ = NULL;

  valid_ = false;

  if (!minidump_->ReadBytes(&thread_, sizeof(thread_))) {
    BPLOG(ERROR) << "MinidumpThread cannot read thread";
    return false;
  }

  if (minidump_->swap()) {
    Swap(&thread_.thread_id);
    Swap(&thread_.suspend_count);
    Swap(&thread_.priority_class);
    Swap(&thread_.priority);
    Swap(&thread_.teb);
    Swap(&thread_.stack);
    Swap(&thread_.thread_context);
  }

  // Check for base + size overflow or undersize.
  if (thread_.stack.memory.rva == 0 ||
      thread_.stack.memory.data_size == 0 ||
      thread_.stack.memory.data_size > numeric_limits<uint64_t>::max() -
                                       thread_.stack.start_of_memory_range) {
    // This is ok, but log an error anyway.
    BPLOG(ERROR) << "MinidumpThread has a memory region problem, " <<
                    HexString(thread_.stack.start_of_memory_range) << "+" <<
                    HexString(thread_.stack.memory.data_size) <<
                    ", RVA 0x" << HexString(thread_.stack.memory.rva);
  } else {
    memory_ = new MinidumpMemoryRegion(minidump_);
    memory_->SetDescriptor(&thread_.stack);
  }

  valid_ = true;
  return true;
}

uint64_t MinidumpThread::GetStartOfStackMemoryRange() const {
  if (!valid_) {
    BPLOG(ERROR) << "GetStartOfStackMemoryRange: Invalid MinidumpThread";
    return 0;
  }

  return thread_.stack.start_of_memory_range;
}

MinidumpMemoryRegion* MinidumpThread::GetMemory() {
  if (!valid_) {
    BPLOG(ERROR) << "Invalid MinidumpThread for GetMemory";
    return NULL;
  }

  return memory_;
}


MinidumpContext* MinidumpThread::GetContext() {
  if (!valid_) {
    BPLOG(ERROR) << "Invalid MinidumpThread for GetContext";
    return NULL;
  }

  if (!context_) {
    if (!minidump_->SeekSet(thread_.thread_context.rva)) {
      BPLOG(ERROR) << "MinidumpThread cannot seek to context";
      return NULL;
    }

    scoped_ptr<MinidumpContext> context(new MinidumpContext(minidump_));

    if (!context->Read(thread_.thread_context.data_size)) {
      BPLOG(ERROR) << "MinidumpThread cannot read context";
      return NULL;
    }

    context_ = context.release();
  }

  return context_;
}


bool MinidumpThread::GetThreadID(uint32_t *thread_id) const {
  BPLOG_IF(ERROR, !thread_id) << "MinidumpThread::GetThreadID requires "
                                 "|thread_id|";
  assert(thread_id);
  *thread_id = 0;

  if (!valid_) {
    BPLOG(ERROR) << "Invalid MinidumpThread for GetThreadID";
    return false;
  }

  *thread_id = thread_.thread_id;
  return true;
}


void MinidumpThread::Print() {
  if (!valid_) {
    BPLOG(ERROR) << "MinidumpThread cannot print invalid data";
    return;
  }

  printf("MDRawThread\n");
  printf("  thread_id                   = 0x%x\n",   thread_.thread_id);
  printf("  suspend_count               = %d\n",     thread_.suspend_count);
  printf("  priority_class              = 0x%x\n",   thread_.priority_class);
  printf("  priority                    = 0x%x\n",   thread_.priority);
  printf("  teb                         = 0x%" PRIx64 "\n", thread_.teb);
  printf("  stack.start_of_memory_range = 0x%" PRIx64 "\n",
         thread_.stack.start_of_memory_range);
  printf("  stack.memory.data_size      = 0x%x\n",
         thread_.stack.memory.data_size);
  printf("  stack.memory.rva            = 0x%x\n",   thread_.stack.memory.rva);
  printf("  thread_context.data_size    = 0x%x\n",
         thread_.thread_context.data_size);
  printf("  thread_context.rva          = 0x%x\n",
         thread_.thread_context.rva);

  MinidumpContext* context = GetContext();
  if (context) {
    printf("\n");
    context->Print();
  } else {
    printf("  (no context)\n");
    printf("\n");
  }

  MinidumpMemoryRegion* memory = GetMemory();
  if (memory) {
    printf("Stack\n");
    memory->Print();
  } else {
    printf("No stack\n");
  }
  printf("\n");
}


//
// MinidumpThreadList
//


uint32_t MinidumpThreadList::max_threads_ = 4096;


MinidumpThreadList::MinidumpThreadList(Minidump* minidump)
    : MinidumpStream(minidump),
      id_to_thread_map_(),
      threads_(NULL),
      thread_count_(0) {
}


MinidumpThreadList::~MinidumpThreadList() {
  delete threads_;
}


bool MinidumpThreadList::Read(uint32_t expected_size) {
  // Invalidate cached data.
  id_to_thread_map_.clear();
  delete threads_;
  threads_ = NULL;
  thread_count_ = 0;

  valid_ = false;

  uint32_t thread_count;
  if (expected_size < sizeof(thread_count)) {
    BPLOG(ERROR) << "MinidumpThreadList count size mismatch, " <<
                    expected_size << " < " << sizeof(thread_count);
    return false;
  }
  if (!minidump_->ReadBytes(&thread_count, sizeof(thread_count))) {
    BPLOG(ERROR) << "MinidumpThreadList cannot read thread count";
    return false;
  }

  if (minidump_->swap())
    Swap(&thread_count);

  if (thread_count > numeric_limits<uint32_t>::max() / sizeof(MDRawThread)) {
    BPLOG(ERROR) << "MinidumpThreadList thread count " << thread_count <<
                    " would cause multiplication overflow";
    return false;
  }

  if (expected_size != sizeof(thread_count) +
                       thread_count * sizeof(MDRawThread)) {
    // may be padded with 4 bytes on 64bit ABIs for alignment
    if (expected_size == sizeof(thread_count) + 4 +
                         thread_count * sizeof(MDRawThread)) {
      uint32_t useless;
      if (!minidump_->ReadBytes(&useless, 4)) {
        BPLOG(ERROR) << "MinidumpThreadList cannot read threadlist padded "
                        "bytes";
        return false;
      }
    } else {
      BPLOG(ERROR) << "MinidumpThreadList size mismatch, " << expected_size <<
                    " != " << sizeof(thread_count) +
                    thread_count * sizeof(MDRawThread);
      return false;
    }
  }


  if (thread_count > max_threads_) {
    BPLOG(ERROR) << "MinidumpThreadList count " << thread_count <<
                    " exceeds maximum " << max_threads_;
    return false;
  }

  if (thread_count != 0) {
    scoped_ptr<MinidumpThreads> threads(
        new MinidumpThreads(thread_count, MinidumpThread(minidump_)));

    for (unsigned int thread_index = 0;
         thread_index < thread_count;
         ++thread_index) {
      MinidumpThread* thread = &(*threads)[thread_index];

      // Assume that the file offset is correct after the last read.
      if (!thread->Read()) {
        BPLOG(ERROR) << "MinidumpThreadList cannot read thread " <<
                        thread_index << "/" << thread_count;
        return false;
      }

      uint32_t thread_id;
      if (!thread->GetThreadID(&thread_id)) {
        BPLOG(ERROR) << "MinidumpThreadList cannot get thread ID for thread " <<
                        thread_index << "/" << thread_count;
        return false;
      }

      if (GetThreadByID(thread_id)) {
        // Another thread with this ID is already in the list.  Data error.
        BPLOG(ERROR) << "MinidumpThreadList found multiple threads with ID " <<
                        HexString(thread_id) << " at thread " <<
                        thread_index << "/" << thread_count;
        return false;
      }
      id_to_thread_map_[thread_id] = thread;
    }

    threads_ = threads.release();
  }

  thread_count_ = thread_count;

  valid_ = true;
  return true;
}


MinidumpThread* MinidumpThreadList::GetThreadAtIndex(unsigned int index)
    const {
  if (!valid_) {
    BPLOG(ERROR) << "Invalid MinidumpThreadList for GetThreadAtIndex";
    return NULL;
  }

  if (index >= thread_count_) {
    BPLOG(ERROR) << "MinidumpThreadList index out of range: " <<
                    index << "/" << thread_count_;
    return NULL;
  }

  return &(*threads_)[index];
}


MinidumpThread* MinidumpThreadList::GetThreadByID(uint32_t thread_id) {
  // Don't check valid_.  Read calls this method before everything is
  // validated.  It is safe to not check valid_ here.
  return id_to_thread_map_[thread_id];
}


void MinidumpThreadList::Print() {
  if (!valid_) {
    BPLOG(ERROR) << "MinidumpThreadList cannot print invalid data";
    return;
  }

  printf("MinidumpThreadList\n");
  printf("  thread_count = %d\n", thread_count_);
  printf("\n");

  for (unsigned int thread_index = 0;
       thread_index < thread_count_;
       ++thread_index) {
    printf("thread[%d]\n", thread_index);

    (*threads_)[thread_index].Print();
  }
}


//
// MinidumpModule
//


uint32_t MinidumpModule::max_cv_bytes_ = 32768;
uint32_t MinidumpModule::max_misc_bytes_ = 32768;


MinidumpModule::MinidumpModule(Minidump* minidump)
    : MinidumpObject(minidump),
      module_valid_(false),
      has_debug_info_(false),
      module_(),
      name_(NULL),
      cv_record_(NULL),
      cv_record_signature_(MD_CVINFOUNKNOWN_SIGNATURE),
      misc_record_(NULL) {
}


MinidumpModule::~MinidumpModule() {
  delete name_;
  delete cv_record_;
  delete misc_record_;
}


bool MinidumpModule::Read() {
  // Invalidate cached data.
  delete name_;
  name_ = NULL;
  delete cv_record_;
  cv_record_ = NULL;
  cv_record_signature_ = MD_CVINFOUNKNOWN_SIGNATURE;
  delete misc_record_;
  misc_record_ = NULL;

  module_valid_ = false;
  has_debug_info_ = false;
  valid_ = false;

  if (!minidump_->ReadBytes(&module_, MD_MODULE_SIZE)) {
    BPLOG(ERROR) << "MinidumpModule cannot read module";
    return false;
  }

  if (minidump_->swap()) {
    Swap(&module_.base_of_image);
    Swap(&module_.size_of_image);
    Swap(&module_.checksum);
    Swap(&module_.time_date_stamp);
    Swap(&module_.module_name_rva);
    Swap(&module_.version_info.signature);
    Swap(&module_.version_info.struct_version);
    Swap(&module_.version_info.file_version_hi);
    Swap(&module_.version_info.file_version_lo);
    Swap(&module_.version_info.product_version_hi);
    Swap(&module_.version_info.product_version_lo);
    Swap(&module_.version_info.file_flags_mask);
    Swap(&module_.version_info.file_flags);
    Swap(&module_.version_info.file_os);
    Swap(&module_.version_info.file_type);
    Swap(&module_.version_info.file_subtype);
    Swap(&module_.version_info.file_date_hi);
    Swap(&module_.version_info.file_date_lo);
    Swap(&module_.cv_record);
    Swap(&module_.misc_record);
    // Don't swap reserved fields because their contents are unknown (as
    // are their proper widths).
  }

  // Check for base + size overflow or undersize.
  if (module_.size_of_image == 0 ||
      module_.size_of_image >
          numeric_limits<uint64_t>::max() - module_.base_of_image) {
    BPLOG(ERROR) << "MinidumpModule has a module problem, " <<
                    HexString(module_.base_of_image) << "+" <<
                    HexString(module_.size_of_image);
    return false;
  }

  module_valid_ = true;
  return true;
}


bool MinidumpModule::ReadAuxiliaryData() {
  if (!module_valid_) {
    BPLOG(ERROR) << "Invalid MinidumpModule for ReadAuxiliaryData";
    return false;
  }

  // Each module must have a name.
  name_ = minidump_->ReadString(module_.module_name_rva);
  if (!name_) {
    BPLOG(ERROR) << "MinidumpModule could not read name";
    return false;
  }

  // At this point, we have enough info for the module to be valid.
  valid_ = true;

  // CodeView and miscellaneous debug records are only required if the
  // module indicates that they exist.
  if (module_.cv_record.data_size && !GetCVRecord(NULL)) {
    BPLOG(ERROR) << "MinidumpModule has no CodeView record, "
                    "but one was expected";
    return false;
  }

  if (module_.misc_record.data_size && !GetMiscRecord(NULL)) {
    BPLOG(ERROR) << "MinidumpModule has no miscellaneous debug record, "
                    "but one was expected";
    return false;
  }

  has_debug_info_ = true;
  return true;
}


string MinidumpModule::code_file() const {
  if (!valid_) {
    BPLOG(ERROR) << "Invalid MinidumpModule for code_file";
    return "";
  }

  return *name_;
}


string MinidumpModule::code_identifier() const {
  if (!valid_) {
    BPLOG(ERROR) << "Invalid MinidumpModule for code_identifier";
    return "";
  }

  if (!has_debug_info_)
    return "";

  MinidumpSystemInfo *minidump_system_info = minidump_->GetSystemInfo();
  if (!minidump_system_info) {
    BPLOG(ERROR) << "MinidumpModule code_identifier requires "
                    "MinidumpSystemInfo";
    return "";
  }

  const MDRawSystemInfo *raw_system_info = minidump_system_info->system_info();
  if (!raw_system_info) {
    BPLOG(ERROR) << "MinidumpModule code_identifier requires MDRawSystemInfo";
    return "";
  }

  string identifier;

  switch (raw_system_info->platform_id) {
    case MD_OS_WIN32_NT:
    case MD_OS_WIN32_WINDOWS: {
      // Use the same format that the MS symbol server uses in filesystem
      // hierarchies.
      char identifier_string[17];
      snprintf(identifier_string, sizeof(identifier_string), "%08X%x",
               module_.time_date_stamp, module_.size_of_image);
      identifier = identifier_string;
      break;
    }

    case MD_OS_MAC_OS_X:
    case MD_OS_IOS:
    case MD_OS_SOLARIS:
    case MD_OS_ANDROID:
    case MD_OS_LINUX:
    case MD_OS_NACL:
    case MD_OS_PS3: {
      // TODO(mmentovai): support uuid extension if present, otherwise fall
      // back to version (from LC_ID_DYLIB?), otherwise fall back to something
      // else.
      identifier = "id";
      break;
    }

    default: {
      // Without knowing what OS generated the dump, we can't generate a good
      // identifier.  Return an empty string, signalling failure.
      BPLOG(ERROR) << "MinidumpModule code_identifier requires known platform, "
                      "found " << HexString(raw_system_info->platform_id);
      break;
    }
  }

  return identifier;
}


string MinidumpModule::debug_file() const {
  if (!valid_) {
    BPLOG(ERROR) << "Invalid MinidumpModule for debug_file";
    return "";
  }

  if (!has_debug_info_)
    return "";

  string file;
  // Prefer the CodeView record if present.
  if (cv_record_) {
    if (cv_record_signature_ == MD_CVINFOPDB70_SIGNATURE) {
      // It's actually an MDCVInfoPDB70 structure.
      const MDCVInfoPDB70* cv_record_70 =
          reinterpret_cast<const MDCVInfoPDB70*>(&(*cv_record_)[0]);
      assert(cv_record_70->cv_signature == MD_CVINFOPDB70_SIGNATURE);

      // GetCVRecord guarantees pdb_file_name is null-terminated.
      file = reinterpret_cast<const char*>(cv_record_70->pdb_file_name);
    } else if (cv_record_signature_ == MD_CVINFOPDB20_SIGNATURE) {
      // It's actually an MDCVInfoPDB20 structure.
      const MDCVInfoPDB20* cv_record_20 =
          reinterpret_cast<const MDCVInfoPDB20*>(&(*cv_record_)[0]);
      assert(cv_record_20->cv_header.signature == MD_CVINFOPDB20_SIGNATURE);

      // GetCVRecord guarantees pdb_file_name is null-terminated.
      file = reinterpret_cast<const char*>(cv_record_20->pdb_file_name);
    }

    // If there's a CodeView record but it doesn't match a known signature,
    // try the miscellaneous record.
  }

  if (file.empty()) {
    // No usable CodeView record.  Try the miscellaneous debug record.
    if (misc_record_) {
      const MDImageDebugMisc* misc_record =
          reinterpret_cast<const MDImageDebugMisc *>(&(*misc_record_)[0]);
      if (!misc_record->unicode) {
        // If it's not Unicode, just stuff it into the string.  It's unclear
        // if misc_record->data is 0-terminated, so use an explicit size.
        file = string(
            reinterpret_cast<const char*>(misc_record->data),
            module_.misc_record.data_size - MDImageDebugMisc_minsize);
      } else {
        // There's a misc_record but it encodes the debug filename in UTF-16.
        // (Actually, because miscellaneous records are so old, it's probably
        // UCS-2.)  Convert it to UTF-8 for congruity with the other strings
        // that this method (and all other methods in the Minidump family)
        // return.

        unsigned int bytes =
            module_.misc_record.data_size - MDImageDebugMisc_minsize;
        if (bytes % 2 == 0) {
          unsigned int utf16_words = bytes / 2;

          // UTF16ToUTF8 expects a vector<uint16_t>, so create a temporary one
          // and copy the UTF-16 data into it.
          vector<uint16_t> string_utf16(utf16_words);
          if (utf16_words)
            memcpy(&string_utf16[0], &misc_record->data, bytes);

          // GetMiscRecord already byte-swapped the data[] field if it contains
          // UTF-16, so pass false as the swap argument.
          scoped_ptr<string> new_file(UTF16ToUTF8(string_utf16, false));
          file = *new_file;
        }
      }
    }
  }

  // Relatively common case
  BPLOG_IF(INFO, file.empty()) << "MinidumpModule could not determine "
                                  "debug_file for " << *name_;

  return file;
}


string MinidumpModule::debug_identifier() const {
  if (!valid_) {
    BPLOG(ERROR) << "Invalid MinidumpModule for debug_identifier";
    return "";
  }

  if (!has_debug_info_)
    return "";

  string identifier;

  // Use the CodeView record if present.
  if (cv_record_) {
    if (cv_record_signature_ == MD_CVINFOPDB70_SIGNATURE) {
      // It's actually an MDCVInfoPDB70 structure.
      const MDCVInfoPDB70* cv_record_70 =
          reinterpret_cast<const MDCVInfoPDB70*>(&(*cv_record_)[0]);
      assert(cv_record_70->cv_signature == MD_CVINFOPDB70_SIGNATURE);

      // Use the same format that the MS symbol server uses in filesystem
      // hierarchies.
      char identifier_string[41];
      snprintf(identifier_string, sizeof(identifier_string),
               "%08X%04X%04X%02X%02X%02X%02X%02X%02X%02X%02X%x",
               cv_record_70->signature.data1,
               cv_record_70->signature.data2,
               cv_record_70->signature.data3,
               cv_record_70->signature.data4[0],
               cv_record_70->signature.data4[1],
               cv_record_70->signature.data4[2],
               cv_record_70->signature.data4[3],
               cv_record_70->signature.data4[4],
               cv_record_70->signature.data4[5],
               cv_record_70->signature.data4[6],
               cv_record_70->signature.data4[7],
               cv_record_70->age);
      identifier = identifier_string;
    } else if (cv_record_signature_ == MD_CVINFOPDB20_SIGNATURE) {
      // It's actually an MDCVInfoPDB20 structure.
      const MDCVInfoPDB20* cv_record_20 =
          reinterpret_cast<const MDCVInfoPDB20*>(&(*cv_record_)[0]);
      assert(cv_record_20->cv_header.signature == MD_CVINFOPDB20_SIGNATURE);

      // Use the same format that the MS symbol server uses in filesystem
      // hierarchies.
      char identifier_string[17];
      snprintf(identifier_string, sizeof(identifier_string),
               "%08X%x", cv_record_20->signature, cv_record_20->age);
      identifier = identifier_string;
    }
  }

  // TODO(mmentovai): if there's no usable CodeView record, there might be a
  // miscellaneous debug record.  It only carries a filename, though, and no
  // identifier.  I'm not sure what the right thing to do for the identifier
  // is in that case, but I don't expect to find many modules without a
  // CodeView record (or some other Breakpad extension structure in place of
  // a CodeView record).  Treat it as an error (empty identifier) for now.

  // TODO(mmentovai): on the Mac, provide fallbacks as in code_identifier().

  // Relatively common case
  BPLOG_IF(INFO, identifier.empty()) << "MinidumpModule could not determine "
                                        "debug_identifier for " << *name_;

  return identifier;
}


string MinidumpModule::version() const {
  if (!valid_) {
    BPLOG(ERROR) << "Invalid MinidumpModule for version";
    return "";
  }

  string version;

  if (module_.version_info.signature == MD_VSFIXEDFILEINFO_SIGNATURE &&
      module_.version_info.struct_version & MD_VSFIXEDFILEINFO_VERSION) {
    char version_string[24];
    snprintf(version_string, sizeof(version_string), "%u.%u.%u.%u",
             module_.version_info.file_version_hi >> 16,
             module_.version_info.file_version_hi & 0xffff,
             module_.version_info.file_version_lo >> 16,
             module_.version_info.file_version_lo & 0xffff);
    version = version_string;
  }

  // TODO(mmentovai): possibly support other struct types in place of
  // the one used with MD_VSFIXEDFILEINFO_SIGNATURE.  We can possibly use
  // a different structure that better represents versioning facilities on
  // Mac OS X and Linux, instead of forcing them to adhere to the dotted
  // quad of 16-bit ints that Windows uses.

  BPLOG_IF(INFO, version.empty()) << "MinidumpModule could not determine "
                                     "version for " << *name_;

  return version;
}


const CodeModule* MinidumpModule::Copy() const {
  return new BasicCodeModule(this);
}


const uint8_t* MinidumpModule::GetCVRecord(uint32_t* size) {
  if (!module_valid_) {
    BPLOG(ERROR) << "Invalid MinidumpModule for GetCVRecord";
    return NULL;
  }

  if (!cv_record_) {
    // This just guards against 0-sized CodeView records; more specific checks
    // are used when the signature is checked against various structure types.
    if (module_.cv_record.data_size == 0) {
      return NULL;
    }

    if (!minidump_->SeekSet(module_.cv_record.rva)) {
      BPLOG(ERROR) << "MinidumpModule could not seek to CodeView record";
      return NULL;
    }

    if (module_.cv_record.data_size > max_cv_bytes_) {
      BPLOG(ERROR) << "MinidumpModule CodeView record size " <<
                      module_.cv_record.data_size << " exceeds maximum " <<
                      max_cv_bytes_;
      return NULL;
    }

    // Allocating something that will be accessed as MDCVInfoPDB70 or
    // MDCVInfoPDB20 but is allocated as uint8_t[] can cause alignment
    // problems.  x86 and ppc are able to cope, though.  This allocation
    // style is needed because the MDCVInfoPDB70 or MDCVInfoPDB20 are
    // variable-sized due to their pdb_file_name fields; these structures
    // are not MDCVInfoPDB70_minsize or MDCVInfoPDB20_minsize and treating
    // them as such would result in incomplete structures or overruns.
    scoped_ptr< vector<uint8_t> > cv_record(
        new vector<uint8_t>(module_.cv_record.data_size));

    if (!minidump_->ReadBytes(&(*cv_record)[0], module_.cv_record.data_size)) {
      BPLOG(ERROR) << "MinidumpModule could not read CodeView record";
      return NULL;
    }

    uint32_t signature = MD_CVINFOUNKNOWN_SIGNATURE;
    if (module_.cv_record.data_size > sizeof(signature)) {
      MDCVInfoPDB70* cv_record_signature =
          reinterpret_cast<MDCVInfoPDB70*>(&(*cv_record)[0]);
      signature = cv_record_signature->cv_signature;
      if (minidump_->swap())
        Swap(&signature);
    }

    if (signature == MD_CVINFOPDB70_SIGNATURE) {
      // Now that the structure type is known, recheck the size.
      if (MDCVInfoPDB70_minsize > module_.cv_record.data_size) {
        BPLOG(ERROR) << "MinidumpModule CodeView7 record size mismatch, " <<
                        MDCVInfoPDB70_minsize << " > " <<
                        module_.cv_record.data_size;
        return NULL;
      }

      if (minidump_->swap()) {
        MDCVInfoPDB70* cv_record_70 =
            reinterpret_cast<MDCVInfoPDB70*>(&(*cv_record)[0]);
        Swap(&cv_record_70->cv_signature);
        Swap(&cv_record_70->signature);
        Swap(&cv_record_70->age);
        // Don't swap cv_record_70.pdb_file_name because it's an array of 8-bit
        // quantities.  (It's a path, is it UTF-8?)
      }

      // The last field of either structure is null-terminated 8-bit character
      // data.  Ensure that it's null-terminated.
      if ((*cv_record)[module_.cv_record.data_size - 1] != '\0') {
        BPLOG(ERROR) << "MinidumpModule CodeView7 record string is not "
                        "0-terminated";
        return NULL;
      }
    } else if (signature == MD_CVINFOPDB20_SIGNATURE) {
      // Now that the structure type is known, recheck the size.
      if (MDCVInfoPDB20_minsize > module_.cv_record.data_size) {
        BPLOG(ERROR) << "MinidumpModule CodeView2 record size mismatch, " <<
                        MDCVInfoPDB20_minsize << " > " <<
                        module_.cv_record.data_size;
        return NULL;
      }
      if (minidump_->swap()) {
        MDCVInfoPDB20* cv_record_20 =
            reinterpret_cast<MDCVInfoPDB20*>(&(*cv_record)[0]);
        Swap(&cv_record_20->cv_header.signature);
        Swap(&cv_record_20->cv_header.offset);
        Swap(&cv_record_20->signature);
        Swap(&cv_record_20->age);
        // Don't swap cv_record_20.pdb_file_name because it's an array of 8-bit
        // quantities.  (It's a path, is it UTF-8?)
      }

      // The last field of either structure is null-terminated 8-bit character
      // data.  Ensure that it's null-terminated.
      if ((*cv_record)[module_.cv_record.data_size - 1] != '\0') {
        BPLOG(ERROR) << "MindumpModule CodeView2 record string is not "
                        "0-terminated";
        return NULL;
      }
    }

    // If the signature doesn't match something above, it's not something
    // that Breakpad can presently handle directly.  Because some modules in
    // the wild contain such CodeView records as MD_CVINFOCV50_SIGNATURE,
    // don't bail out here - allow the data to be returned to the user,
    // although byte-swapping can't be done.

    // Store the vector type because that's how storage was allocated, but
    // return it casted to uint8_t*.
    cv_record_ = cv_record.release();
    cv_record_signature_ = signature;
  }

  if (size)
    *size = module_.cv_record.data_size;

  return &(*cv_record_)[0];
}


const MDImageDebugMisc* MinidumpModule::GetMiscRecord(uint32_t* size) {
  if (!module_valid_) {
    BPLOG(ERROR) << "Invalid MinidumpModule for GetMiscRecord";
    return NULL;
  }

  if (!misc_record_) {
    if (module_.misc_record.data_size == 0) {
      return NULL;
    }

    if (MDImageDebugMisc_minsize > module_.misc_record.data_size) {
      BPLOG(ERROR) << "MinidumpModule miscellaneous debugging record "
                      "size mismatch, " << MDImageDebugMisc_minsize << " > " <<
                      module_.misc_record.data_size;
      return NULL;
    }

    if (!minidump_->SeekSet(module_.misc_record.rva)) {
      BPLOG(ERROR) << "MinidumpModule could not seek to miscellaneous "
                      "debugging record";
      return NULL;
    }

    if (module_.misc_record.data_size > max_misc_bytes_) {
      BPLOG(ERROR) << "MinidumpModule miscellaneous debugging record size " <<
                      module_.misc_record.data_size << " exceeds maximum " <<
                      max_misc_bytes_;
      return NULL;
    }

    // Allocating something that will be accessed as MDImageDebugMisc but
    // is allocated as uint8_t[] can cause alignment problems.  x86 and
    // ppc are able to cope, though.  This allocation style is needed
    // because the MDImageDebugMisc is variable-sized due to its data field;
    // this structure is not MDImageDebugMisc_minsize and treating it as such
    // would result in an incomplete structure or an overrun.
    scoped_ptr< vector<uint8_t> > misc_record_mem(
        new vector<uint8_t>(module_.misc_record.data_size));
    MDImageDebugMisc* misc_record =
        reinterpret_cast<MDImageDebugMisc*>(&(*misc_record_mem)[0]);

    if (!minidump_->ReadBytes(misc_record, module_.misc_record.data_size)) {
      BPLOG(ERROR) << "MinidumpModule could not read miscellaneous debugging "
                      "record";
      return NULL;
    }

    if (minidump_->swap()) {
      Swap(&misc_record->data_type);
      Swap(&misc_record->length);
      // Don't swap misc_record.unicode because it's an 8-bit quantity.
      // Don't swap the reserved fields for the same reason, and because
      // they don't contain any valid data.
      if (misc_record->unicode) {
        // There is a potential alignment problem, but shouldn't be a problem
        // in practice due to the layout of MDImageDebugMisc.
        uint16_t* data16 = reinterpret_cast<uint16_t*>(&(misc_record->data));
        unsigned int dataBytes = module_.misc_record.data_size -
                                 MDImageDebugMisc_minsize;
        Swap(data16, dataBytes);
      }
    }

    if (module_.misc_record.data_size != misc_record->length) {
      BPLOG(ERROR) << "MinidumpModule miscellaneous debugging record data "
                      "size mismatch, " << module_.misc_record.data_size <<
                      " != " << misc_record->length;
      return NULL;
    }

    // Store the vector type because that's how storage was allocated, but
    // return it casted to MDImageDebugMisc*.
    misc_record_ = misc_record_mem.release();
  }

  if (size)
    *size = module_.misc_record.data_size;

  return reinterpret_cast<MDImageDebugMisc*>(&(*misc_record_)[0]);
}


void MinidumpModule::Print() {
  if (!valid_) {
    BPLOG(ERROR) << "MinidumpModule cannot print invalid data";
    return;
  }

  printf("MDRawModule\n");
  printf("  base_of_image                   = 0x%" PRIx64 "\n",
         module_.base_of_image);
  printf("  size_of_image                   = 0x%x\n",
         module_.size_of_image);
  printf("  checksum                        = 0x%x\n",
         module_.checksum);
  printf("  time_date_stamp                 = 0x%x %s\n",
         module_.time_date_stamp,
         TimeTToUTCString(module_.time_date_stamp).c_str());
  printf("  module_name_rva                 = 0x%x\n",
         module_.module_name_rva);
  printf("  version_info.signature          = 0x%x\n",
         module_.version_info.signature);
  printf("  version_info.struct_version     = 0x%x\n",
         module_.version_info.struct_version);
  printf("  version_info.file_version       = 0x%x:0x%x\n",
         module_.version_info.file_version_hi,
         module_.version_info.file_version_lo);
  printf("  version_info.product_version    = 0x%x:0x%x\n",
         module_.version_info.product_version_hi,
         module_.version_info.product_version_lo);
  printf("  version_info.file_flags_mask    = 0x%x\n",
         module_.version_info.file_flags_mask);
  printf("  version_info.file_flags         = 0x%x\n",
         module_.version_info.file_flags);
  printf("  version_info.file_os            = 0x%x\n",
         module_.version_info.file_os);
  printf("  version_info.file_type          = 0x%x\n",
         module_.version_info.file_type);
  printf("  version_info.file_subtype       = 0x%x\n",
         module_.version_info.file_subtype);
  printf("  version_info.file_date          = 0x%x:0x%x\n",
         module_.version_info.file_date_hi,
         module_.version_info.file_date_lo);
  printf("  cv_record.data_size             = %d\n",
         module_.cv_record.data_size);
  printf("  cv_record.rva                   = 0x%x\n",
         module_.cv_record.rva);
  printf("  misc_record.data_size           = %d\n",
         module_.misc_record.data_size);
  printf("  misc_record.rva                 = 0x%x\n",
         module_.misc_record.rva);

  printf("  (code_file)                     = \"%s\"\n", code_file().c_str());
  printf("  (code_identifier)               = \"%s\"\n",
         code_identifier().c_str());

  uint32_t cv_record_size;
  const uint8_t *cv_record = GetCVRecord(&cv_record_size);
  if (cv_record) {
    if (cv_record_signature_ == MD_CVINFOPDB70_SIGNATURE) {
      const MDCVInfoPDB70* cv_record_70 =
          reinterpret_cast<const MDCVInfoPDB70*>(cv_record);
      assert(cv_record_70->cv_signature == MD_CVINFOPDB70_SIGNATURE);

      printf("  (cv_record).cv_signature        = 0x%x\n",
             cv_record_70->cv_signature);
      printf("  (cv_record).signature           = %08x-%04x-%04x-%02x%02x-",
             cv_record_70->signature.data1,
             cv_record_70->signature.data2,
             cv_record_70->signature.data3,
             cv_record_70->signature.data4[0],
             cv_record_70->signature.data4[1]);
      for (unsigned int guidIndex = 2;
           guidIndex < 8;
           ++guidIndex) {
        printf("%02x", cv_record_70->signature.data4[guidIndex]);
      }
      printf("\n");
      printf("  (cv_record).age                 = %d\n",
             cv_record_70->age);
      printf("  (cv_record).pdb_file_name       = \"%s\"\n",
             cv_record_70->pdb_file_name);
    } else if (cv_record_signature_ == MD_CVINFOPDB20_SIGNATURE) {
      const MDCVInfoPDB20* cv_record_20 =
          reinterpret_cast<const MDCVInfoPDB20*>(cv_record);
      assert(cv_record_20->cv_header.signature == MD_CVINFOPDB20_SIGNATURE);

      printf("  (cv_record).cv_header.signature = 0x%x\n",
             cv_record_20->cv_header.signature);
      printf("  (cv_record).cv_header.offset    = 0x%x\n",
             cv_record_20->cv_header.offset);
      printf("  (cv_record).signature           = 0x%x %s\n",
             cv_record_20->signature,
             TimeTToUTCString(cv_record_20->signature).c_str());
      printf("  (cv_record).age                 = %d\n",
             cv_record_20->age);
      printf("  (cv_record).pdb_file_name       = \"%s\"\n",
             cv_record_20->pdb_file_name);
    } else {
      printf("  (cv_record)                     = ");
      for (unsigned int cv_byte_index = 0;
           cv_byte_index < cv_record_size;
           ++cv_byte_index) {
        printf("%02x", cv_record[cv_byte_index]);
      }
      printf("\n");
    }
  } else {
    printf("  (cv_record)                     = (null)\n");
  }

  const MDImageDebugMisc* misc_record = GetMiscRecord(NULL);
  if (misc_record) {
    printf("  (misc_record).data_type         = 0x%x\n",
           misc_record->data_type);
    printf("  (misc_record).length            = 0x%x\n",
           misc_record->length);
    printf("  (misc_record).unicode           = %d\n",
           misc_record->unicode);
    if (misc_record->unicode) {
      string misc_record_data_utf8;
      ConvertUTF16BufferToUTF8String(
          reinterpret_cast<const uint16_t*>(misc_record->data),
          misc_record->length - offsetof(MDImageDebugMisc, data),
          &misc_record_data_utf8,
          false);  // already swapped
      printf("  (misc_record).data              = \"%s\"\n",
             misc_record_data_utf8.c_str());
    } else {
      printf("  (misc_record).data              = \"%s\"\n",
             misc_record->data);
    }
  } else {
    printf("  (misc_record)                   = (null)\n");
  }

  printf("  (debug_file)                    = \"%s\"\n", debug_file().c_str());
  printf("  (debug_identifier)              = \"%s\"\n",
         debug_identifier().c_str());
  printf("  (version)                       = \"%s\"\n", version().c_str());
  printf("\n");
}


//
// MinidumpModuleList
//


uint32_t MinidumpModuleList::max_modules_ = 1024;


MinidumpModuleList::MinidumpModuleList(Minidump* minidump)
    : MinidumpStream(minidump),
      range_map_(new RangeMap<uint64_t, unsigned int>()),
      modules_(NULL),
      module_count_(0) {
}


MinidumpModuleList::~MinidumpModuleList() {
  delete range_map_;
  delete modules_;
}


bool MinidumpModuleList::Read(uint32_t expected_size) {
  // Invalidate cached data.
  range_map_->Clear();
  delete modules_;
  modules_ = NULL;
  module_count_ = 0;

  valid_ = false;

  uint32_t module_count;
  if (expected_size < sizeof(module_count)) {
    BPLOG(ERROR) << "MinidumpModuleList count size mismatch, " <<
                    expected_size << " < " << sizeof(module_count);
    return false;
  }
  if (!minidump_->ReadBytes(&module_count, sizeof(module_count))) {
    BPLOG(ERROR) << "MinidumpModuleList could not read module count";
    return false;
  }

  if (minidump_->swap())
    Swap(&module_count);

  if (module_count > numeric_limits<uint32_t>::max() / MD_MODULE_SIZE) {
    BPLOG(ERROR) << "MinidumpModuleList module count " << module_count <<
                    " would cause multiplication overflow";
    return false;
  }

  if (expected_size != sizeof(module_count) +
                       module_count * MD_MODULE_SIZE) {
    // may be padded with 4 bytes on 64bit ABIs for alignment
    if (expected_size == sizeof(module_count) + 4 +
                         module_count * MD_MODULE_SIZE) {
      uint32_t useless;
      if (!minidump_->ReadBytes(&useless, 4)) {
        BPLOG(ERROR) << "MinidumpModuleList cannot read modulelist padded "
                        "bytes";
        return false;
      }
    } else {
      BPLOG(ERROR) << "MinidumpModuleList size mismatch, " << expected_size <<
                      " != " << sizeof(module_count) +
                      module_count * MD_MODULE_SIZE;
      return false;
    }
  }

  if (module_count > max_modules_) {
    BPLOG(ERROR) << "MinidumpModuleList count " << module_count_ <<
                    " exceeds maximum " << max_modules_;
    return false;
  }

  if (module_count != 0) {
    scoped_ptr<MinidumpModules> modules(
        new MinidumpModules(module_count, MinidumpModule(minidump_)));

    for (unsigned int module_index = 0;
         module_index < module_count;
         ++module_index) {
      MinidumpModule* module = &(*modules)[module_index];

      // Assume that the file offset is correct after the last read.
      if (!module->Read()) {
        BPLOG(ERROR) << "MinidumpModuleList could not read module " <<
                        module_index << "/" << module_count;
        return false;
      }
    }

    // Loop through the module list once more to read additional data and
    // build the range map.  This is done in a second pass because
    // MinidumpModule::ReadAuxiliaryData seeks around, and if it were
    // included in the loop above, additional seeks would be needed where
    // none are now to read contiguous data.
    for (unsigned int module_index = 0;
         module_index < module_count;
         ++module_index) {
      MinidumpModule* module = &(*modules)[module_index];

      // ReadAuxiliaryData fails if any data that the module indicates should
      // exist is missing, but we treat some such cases as valid anyway.  See
      // issue #222: if a debugging record is of a format that's too large to
      // handle, it shouldn't render the entire dump invalid.  Check module
      // validity before giving up.
      if (!module->ReadAuxiliaryData() && !module->valid()) {
        BPLOG(ERROR) << "MinidumpModuleList could not read required module "
                        "auxiliary data for module " <<
                        module_index << "/" << module_count;
        return false;
      }

      // It is safe to use module->code_file() after successfully calling
      // module->ReadAuxiliaryData or noting that the module is valid.

      uint64_t base_address = module->base_address();
      uint64_t module_size = module->size();
      if (base_address == static_cast<uint64_t>(-1)) {
        BPLOG(ERROR) << "MinidumpModuleList found bad base address "
                        "for module " << module_index << "/" << module_count <<
                        ", " << module->code_file();
        return false;
      }

      if (!range_map_->StoreRange(base_address, module_size, module_index)) {
        // Android's shared memory implementation /dev/ashmem can contain
        // duplicate entries for JITted code, so ignore these.
        // TODO(wfh): Remove this code when Android is fixed.
        // See https://crbug.com/439531
        const string kDevAshmem("/dev/ashmem/");
        if (module->code_file().compare(
            0, kDevAshmem.length(), kDevAshmem) != 0) {
          BPLOG(ERROR) << "MinidumpModuleList could not store module " <<
                          module_index << "/" << module_count << ", " <<
                          module->code_file() << ", " <<
                          HexString(base_address) << "+" <<
                          HexString(module_size);
          return false;
        } else {
          BPLOG(INFO) << "MinidumpModuleList ignoring overlapping module " <<
                          module_index << "/" << module_count << ", " <<
                          module->code_file() << ", " <<
                          HexString(base_address) << "+" <<
                          HexString(module_size);
        }
      }
    }

    modules_ = modules.release();
  }

  module_count_ = module_count;

  valid_ = true;
  return true;
}


const MinidumpModule* MinidumpModuleList::GetModuleForAddress(
    uint64_t address) const {
  if (!valid_) {
    BPLOG(ERROR) << "Invalid MinidumpModuleList for GetModuleForAddress";
    return NULL;
  }

  unsigned int module_index;
  if (!range_map_->RetrieveRange(address, &module_index, NULL, NULL)) {
    BPLOG(INFO) << "MinidumpModuleList has no module at " <<
                   HexString(address);
    return NULL;
  }

  return GetModuleAtIndex(module_index);
}


const MinidumpModule* MinidumpModuleList::GetMainModule() const {
  if (!valid_) {
    BPLOG(ERROR) << "Invalid MinidumpModuleList for GetMainModule";
    return NULL;
  }

  // The main code module is the first one present in a minidump file's
  // MDRawModuleList.
  return GetModuleAtIndex(0);
}


const MinidumpModule* MinidumpModuleList::GetModuleAtSequence(
    unsigned int sequence) const {
  if (!valid_) {
    BPLOG(ERROR) << "Invalid MinidumpModuleList for GetModuleAtSequence";
    return NULL;
  }

  if (sequence >= module_count_) {
    BPLOG(ERROR) << "MinidumpModuleList sequence out of range: " <<
                    sequence << "/" << module_count_;
    return NULL;
  }

  unsigned int module_index;
  if (!range_map_->RetrieveRangeAtIndex(sequence, &module_index, NULL, NULL)) {
    BPLOG(ERROR) << "MinidumpModuleList has no module at sequence " << sequence;
    return NULL;
  }

  return GetModuleAtIndex(module_index);
}


const MinidumpModule* MinidumpModuleList::GetModuleAtIndex(
    unsigned int index) const {
  if (!valid_) {
    BPLOG(ERROR) << "Invalid MinidumpModuleList for GetModuleAtIndex";
    return NULL;
  }

  if (index >= module_count_) {
    BPLOG(ERROR) << "MinidumpModuleList index out of range: " <<
                    index << "/" << module_count_;
    return NULL;
  }

  return &(*modules_)[index];
}


const CodeModules* MinidumpModuleList::Copy() const {
  return new BasicCodeModules(this);
}


void MinidumpModuleList::Print() {
  if (!valid_) {
    BPLOG(ERROR) << "MinidumpModuleList cannot print invalid data";
    return;
  }

  printf("MinidumpModuleList\n");
  printf("  module_count = %d\n", module_count_);
  printf("\n");

  for (unsigned int module_index = 0;
       module_index < module_count_;
       ++module_index) {
    printf("module[%d]\n", module_index);

    (*modules_)[module_index].Print();
  }
}


//
// MinidumpMemoryList
//


uint32_t MinidumpMemoryList::max_regions_ = 4096;


MinidumpMemoryList::MinidumpMemoryList(Minidump* minidump)
    : MinidumpStream(minidump),
      range_map_(new RangeMap<uint64_t, unsigned int>()),
      descriptors_(NULL),
      regions_(NULL),
      region_count_(0) {
}


MinidumpMemoryList::~MinidumpMemoryList() {
  delete range_map_;
  delete descriptors_;
  delete regions_;
}


bool MinidumpMemoryList::Read(uint32_t expected_size) {
  // Invalidate cached data.
  delete descriptors_;
  descriptors_ = NULL;
  delete regions_;
  regions_ = NULL;
  range_map_->Clear();
  region_count_ = 0;

  valid_ = false;

  uint32_t region_count;
  if (expected_size < sizeof(region_count)) {
    BPLOG(ERROR) << "MinidumpMemoryList count size mismatch, " <<
                    expected_size << " < " << sizeof(region_count);
    return false;
  }
  if (!minidump_->ReadBytes(&region_count, sizeof(region_count))) {
    BPLOG(ERROR) << "MinidumpMemoryList could not read memory region count";
    return false;
  }

  if (minidump_->swap())
    Swap(&region_count);

  if (region_count >
          numeric_limits<uint32_t>::max() / sizeof(MDMemoryDescriptor)) {
    BPLOG(ERROR) << "MinidumpMemoryList region count " << region_count <<
                    " would cause multiplication overflow";
    return false;
  }

  if (expected_size != sizeof(region_count) +
                       region_count * sizeof(MDMemoryDescriptor)) {
    // may be padded with 4 bytes on 64bit ABIs for alignment
    if (expected_size == sizeof(region_count) + 4 +
                         region_count * sizeof(MDMemoryDescriptor)) {
      uint32_t useless;
      if (!minidump_->ReadBytes(&useless, 4)) {
        BPLOG(ERROR) << "MinidumpMemoryList cannot read memorylist padded "
                        "bytes";
        return false;
      }
    } else {
      BPLOG(ERROR) << "MinidumpMemoryList size mismatch, " << expected_size <<
                      " != " << sizeof(region_count) +
                      region_count * sizeof(MDMemoryDescriptor);
      return false;
    }
  }

  if (region_count > max_regions_) {
    BPLOG(ERROR) << "MinidumpMemoryList count " << region_count <<
                    " exceeds maximum " << max_regions_;
    return false;
  }

  if (region_count != 0) {
    scoped_ptr<MemoryDescriptors> descriptors(
        new MemoryDescriptors(region_count));

    // Read the entire array in one fell swoop, instead of reading one entry
    // at a time in the loop.
    if (!minidump_->ReadBytes(&(*descriptors)[0],
                              sizeof(MDMemoryDescriptor) * region_count)) {
      BPLOG(ERROR) << "MinidumpMemoryList could not read memory region list";
      return false;
    }

    scoped_ptr<MemoryRegions> regions(
        new MemoryRegions(region_count, MinidumpMemoryRegion(minidump_)));

    for (unsigned int region_index = 0;
         region_index < region_count;
         ++region_index) {
      MDMemoryDescriptor* descriptor = &(*descriptors)[region_index];

      if (minidump_->swap())
        Swap(descriptor);

      uint64_t base_address = descriptor->start_of_memory_range;
      uint32_t region_size = descriptor->memory.data_size;

      // Check for base + size overflow or undersize.
      if (region_size == 0 ||
          region_size > numeric_limits<uint64_t>::max() - base_address) {
        BPLOG(ERROR) << "MinidumpMemoryList has a memory region problem, " <<
                        " region " << region_index << "/" << region_count <<
                        ", " << HexString(base_address) << "+" <<
                        HexString(region_size);
        return false;
      }

      if (!range_map_->StoreRange(base_address, region_size, region_index)) {
        BPLOG(ERROR) << "MinidumpMemoryList could not store memory region " <<
                        region_index << "/" << region_count << ", " <<
                        HexString(base_address) << "+" <<
                        HexString(region_size);
        return false;
      }

      (*regions)[region_index].SetDescriptor(descriptor);
    }

    descriptors_ = descriptors.release();
    regions_ = regions.release();
  }

  region_count_ = region_count;

  valid_ = true;
  return true;
}


MinidumpMemoryRegion* MinidumpMemoryList::GetMemoryRegionAtIndex(
      unsigned int index) {
  if (!valid_) {
    BPLOG(ERROR) << "Invalid MinidumpMemoryList for GetMemoryRegionAtIndex";
    return NULL;
  }

  if (index >= region_count_) {
    BPLOG(ERROR) << "MinidumpMemoryList index out of range: " <<
                    index << "/" << region_count_;
    return NULL;
  }

  return &(*regions_)[index];
}


MinidumpMemoryRegion* MinidumpMemoryList::GetMemoryRegionForAddress(
    uint64_t address) {
  if (!valid_) {
    BPLOG(ERROR) << "Invalid MinidumpMemoryList for GetMemoryRegionForAddress";
    return NULL;
  }

  unsigned int region_index;
  if (!range_map_->RetrieveRange(address, &region_index, NULL, NULL)) {
    BPLOG(INFO) << "MinidumpMemoryList has no memory region at " <<
                   HexString(address);
    return NULL;
  }

  return GetMemoryRegionAtIndex(region_index);
}


void MinidumpMemoryList::Print() {
  if (!valid_) {
    BPLOG(ERROR) << "MinidumpMemoryList cannot print invalid data";
    return;
  }

  printf("MinidumpMemoryList\n");
  printf("  region_count = %d\n", region_count_);
  printf("\n");

  for (unsigned int region_index = 0;
       region_index < region_count_;
       ++region_index) {
    MDMemoryDescriptor* descriptor = &(*descriptors_)[region_index];
    printf("region[%d]\n", region_index);
    printf("MDMemoryDescriptor\n");
    printf("  start_of_memory_range = 0x%" PRIx64 "\n",
           descriptor->start_of_memory_range);
    printf("  memory.data_size      = 0x%x\n", descriptor->memory.data_size);
    printf("  memory.rva            = 0x%x\n", descriptor->memory.rva);
    MinidumpMemoryRegion* region = GetMemoryRegionAtIndex(region_index);
    if (region) {
      printf("Memory\n");
      region->Print();
    } else {
      printf("No memory\n");
    }
    printf("\n");
  }
}


//
// MinidumpException
//


MinidumpException::MinidumpException(Minidump* minidump)
    : MinidumpStream(minidump),
      exception_(),
      context_(NULL) {
}


MinidumpException::~MinidumpException() {
  delete context_;
}


bool MinidumpException::Read(uint32_t expected_size) {
  // Invalidate cached data.
  delete context_;
  context_ = NULL;

  valid_ = false;

  if (expected_size != sizeof(exception_)) {
    BPLOG(ERROR) << "MinidumpException size mismatch, " << expected_size <<
                    " != " << sizeof(exception_);
    return false;
  }

  if (!minidump_->ReadBytes(&exception_, sizeof(exception_))) {
    BPLOG(ERROR) << "MinidumpException cannot read exception";
    return false;
  }

  if (minidump_->swap()) {
    Swap(&exception_.thread_id);
    // exception_.__align is for alignment only and does not need to be
    // swapped.
    Swap(&exception_.exception_record.exception_code);
    Swap(&exception_.exception_record.exception_flags);
    Swap(&exception_.exception_record.exception_record);
    Swap(&exception_.exception_record.exception_address);
    Swap(&exception_.exception_record.number_parameters);
    // exception_.exception_record.__align is for alignment only and does not
    // need to be swapped.
    for (unsigned int parameter_index = 0;
         parameter_index < MD_EXCEPTION_MAXIMUM_PARAMETERS;
         ++parameter_index) {
      Swap(&exception_.exception_record.exception_information[parameter_index]);
    }
    Swap(&exception_.thread_context);
  }

  valid_ = true;
  return true;
}


bool MinidumpException::GetThreadID(uint32_t *thread_id) const {
  BPLOG_IF(ERROR, !thread_id) << "MinidumpException::GetThreadID requires "
                                 "|thread_id|";
  assert(thread_id);
  *thread_id = 0;

  if (!valid_) {
    BPLOG(ERROR) << "Invalid MinidumpException for GetThreadID";
    return false;
  }

  *thread_id = exception_.thread_id;
  return true;
}


MinidumpContext* MinidumpException::GetContext() {
  if (!valid_) {
    BPLOG(ERROR) << "Invalid MinidumpException for GetContext";
    return NULL;
  }

  if (!context_) {
    if (!minidump_->SeekSet(exception_.thread_context.rva)) {
      BPLOG(ERROR) << "MinidumpException cannot seek to context";
      return NULL;
    }

    scoped_ptr<MinidumpContext> context(new MinidumpContext(minidump_));

    // Don't log as an error if we can still fall back on the thread's context
    // (which must be possible if we got this far.)
    if (!context->Read(exception_.thread_context.data_size)) {
      BPLOG(INFO) << "MinidumpException cannot read context";
      return NULL;
    }

    context_ = context.release();
  }

  return context_;
}


void MinidumpException::Print() {
  if (!valid_) {
    BPLOG(ERROR) << "MinidumpException cannot print invalid data";
    return;
  }

  printf("MDException\n");
  printf("  thread_id                                  = 0x%x\n",
         exception_.thread_id);
  printf("  exception_record.exception_code            = 0x%x\n",
         exception_.exception_record.exception_code);
  printf("  exception_record.exception_flags           = 0x%x\n",
         exception_.exception_record.exception_flags);
  printf("  exception_record.exception_record          = 0x%" PRIx64 "\n",
         exception_.exception_record.exception_record);
  printf("  exception_record.exception_address         = 0x%" PRIx64 "\n",
         exception_.exception_record.exception_address);
  printf("  exception_record.number_parameters         = %d\n",
         exception_.exception_record.number_parameters);
  for (unsigned int parameterIndex = 0;
       parameterIndex < exception_.exception_record.number_parameters;
       ++parameterIndex) {
    printf("  exception_record.exception_information[%2d] = 0x%" PRIx64 "\n",
           parameterIndex,
           exception_.exception_record.exception_information[parameterIndex]);
  }
  printf("  thread_context.data_size                   = %d\n",
         exception_.thread_context.data_size);
  printf("  thread_context.rva                         = 0x%x\n",
         exception_.thread_context.rva);
  MinidumpContext* context = GetContext();
  if (context) {
    printf("\n");
    context->Print();
  } else {
    printf("  (no context)\n");
    printf("\n");
  }
}

//
// MinidumpAssertion
//


MinidumpAssertion::MinidumpAssertion(Minidump* minidump)
    : MinidumpStream(minidump),
      assertion_(),
      expression_(),
      function_(),
      file_() {
}


MinidumpAssertion::~MinidumpAssertion() {
}


bool MinidumpAssertion::Read(uint32_t expected_size) {
  // Invalidate cached data.
  valid_ = false;

  if (expected_size != sizeof(assertion_)) {
    BPLOG(ERROR) << "MinidumpAssertion size mismatch, " << expected_size <<
                    " != " << sizeof(assertion_);
    return false;
  }

  if (!minidump_->ReadBytes(&assertion_, sizeof(assertion_))) {
    BPLOG(ERROR) << "MinidumpAssertion cannot read assertion";
    return false;
  }

  // Each of {expression, function, file} is a UTF-16 string,
  // we'll convert them to UTF-8 for ease of use.
  ConvertUTF16BufferToUTF8String(assertion_.expression,
                                 sizeof(assertion_.expression), &expression_,
                                 minidump_->swap());
  ConvertUTF16BufferToUTF8String(assertion_.function,
                                 sizeof(assertion_.function), &function_,
                                 minidump_->swap());
  ConvertUTF16BufferToUTF8String(assertion_.file, sizeof(assertion_.file),
                                 &file_, minidump_->swap());

  if (minidump_->swap()) {
    Swap(&assertion_.line);
    Swap(&assertion_.type);
  }

  valid_ = true;
  return true;
}

void MinidumpAssertion::Print() {
  if (!valid_) {
    BPLOG(ERROR) << "MinidumpAssertion cannot print invalid data";
    return;
  }

  printf("MDAssertion\n");
  printf("  expression                                 = %s\n",
         expression_.c_str());
  printf("  function                                   = %s\n",
         function_.c_str());
  printf("  file                                       = %s\n",
         file_.c_str());
  printf("  line                                       = %u\n",
         assertion_.line);
  printf("  type                                       = %u\n",
         assertion_.type);
  printf("\n");
}

//
// MinidumpSystemInfo
//


MinidumpSystemInfo::MinidumpSystemInfo(Minidump* minidump)
    : MinidumpStream(minidump),
      system_info_(),
      csd_version_(NULL),
      cpu_vendor_(NULL) {
}


MinidumpSystemInfo::~MinidumpSystemInfo() {
  delete csd_version_;
  delete cpu_vendor_;
}


bool MinidumpSystemInfo::Read(uint32_t expected_size) {
  // Invalidate cached data.
  delete csd_version_;
  csd_version_ = NULL;
  delete cpu_vendor_;
  cpu_vendor_ = NULL;

  valid_ = false;

  if (expected_size != sizeof(system_info_)) {
    BPLOG(ERROR) << "MinidumpSystemInfo size mismatch, " << expected_size <<
                    " != " << sizeof(system_info_);
    return false;
  }

  if (!minidump_->ReadBytes(&system_info_, sizeof(system_info_))) {
    BPLOG(ERROR) << "MinidumpSystemInfo cannot read system info";
    return false;
  }

  if (minidump_->swap()) {
    Swap(&system_info_.processor_architecture);
    Swap(&system_info_.processor_level);
    Swap(&system_info_.processor_revision);
    // number_of_processors and product_type are 8-bit quantities and need no
    // swapping.
    Swap(&system_info_.major_version);
    Swap(&system_info_.minor_version);
    Swap(&system_info_.build_number);
    Swap(&system_info_.platform_id);
    Swap(&system_info_.csd_version_rva);
    Swap(&system_info_.suite_mask);
    // Don't swap the reserved2 field because its contents are unknown.

    if (system_info_.processor_architecture == MD_CPU_ARCHITECTURE_X86 ||
        system_info_.processor_architecture == MD_CPU_ARCHITECTURE_X86_WIN64) {
      for (unsigned int i = 0; i < 3; ++i)
        Swap(&system_info_.cpu.x86_cpu_info.vendor_id[i]);
      Swap(&system_info_.cpu.x86_cpu_info.version_information);
      Swap(&system_info_.cpu.x86_cpu_info.feature_information);
      Swap(&system_info_.cpu.x86_cpu_info.amd_extended_cpu_features);
    } else {
      for (unsigned int i = 0; i < 2; ++i)
        Swap(&system_info_.cpu.other_cpu_info.processor_features[i]);
    }
  }

  valid_ = true;
  return true;
}


string MinidumpSystemInfo::GetOS() {
  string os;

  if (!valid_) {
    BPLOG(ERROR) << "Invalid MinidumpSystemInfo for GetOS";
    return os;
  }

  switch (system_info_.platform_id) {
    case MD_OS_WIN32_NT:
    case MD_OS_WIN32_WINDOWS:
      os = "windows";
      break;

    case MD_OS_MAC_OS_X:
      os = "mac";
      break;

    case MD_OS_IOS:
      os = "ios";
      break;

    case MD_OS_LINUX:
      os = "linux";
      break;

    case MD_OS_SOLARIS:
      os = "solaris";
      break;

    case MD_OS_ANDROID:
      os = "android";
      break;

    case MD_OS_PS3:
      os = "ps3";
      break;

    case MD_OS_NACL:
      os = "nacl";
      break;

    default:
      BPLOG(ERROR) << "MinidumpSystemInfo unknown OS for platform " <<
                      HexString(system_info_.platform_id);
      break;
  }

  return os;
}


string MinidumpSystemInfo::GetCPU() {
  if (!valid_) {
    BPLOG(ERROR) << "Invalid MinidumpSystemInfo for GetCPU";
    return "";
  }

  string cpu;

  switch (system_info_.processor_architecture) {
    case MD_CPU_ARCHITECTURE_X86:
    case MD_CPU_ARCHITECTURE_X86_WIN64:
      cpu = "x86";
      break;

    case MD_CPU_ARCHITECTURE_AMD64:
      cpu = "x86-64";
      break;

    case MD_CPU_ARCHITECTURE_PPC:
      cpu = "ppc";
      break;

    case MD_CPU_ARCHITECTURE_PPC64:
      cpu = "ppc64";
      break;

    case MD_CPU_ARCHITECTURE_SPARC:
      cpu = "sparc";
      break;

    case MD_CPU_ARCHITECTURE_ARM:
      cpu = "arm";
      break;

    case MD_CPU_ARCHITECTURE_ARM64:
      cpu = "arm64";
      break;

    default:
      BPLOG(ERROR) << "MinidumpSystemInfo unknown CPU for architecture " <<
                      HexString(system_info_.processor_architecture);
      break;
  }

  return cpu;
}


const string* MinidumpSystemInfo::GetCSDVersion() {
  if (!valid_) {
    BPLOG(ERROR) << "Invalid MinidumpSystemInfo for GetCSDVersion";
    return NULL;
  }

  if (!csd_version_)
    csd_version_ = minidump_->ReadString(system_info_.csd_version_rva);

  BPLOG_IF(ERROR, !csd_version_) << "MinidumpSystemInfo could not read "
                                    "CSD version";

  return csd_version_;
}


const string* MinidumpSystemInfo::GetCPUVendor() {
  if (!valid_) {
    BPLOG(ERROR) << "Invalid MinidumpSystemInfo for GetCPUVendor";
    return NULL;
  }

  // CPU vendor information can only be determined from x86 minidumps.
  if (!cpu_vendor_ &&
      (system_info_.processor_architecture == MD_CPU_ARCHITECTURE_X86 ||
       system_info_.processor_architecture == MD_CPU_ARCHITECTURE_X86_WIN64)) {
    char cpu_vendor_string[13];
    snprintf(cpu_vendor_string, sizeof(cpu_vendor_string),
             "%c%c%c%c%c%c%c%c%c%c%c%c",
              system_info_.cpu.x86_cpu_info.vendor_id[0] & 0xff,
             (system_info_.cpu.x86_cpu_info.vendor_id[0] >> 8) & 0xff,
             (system_info_.cpu.x86_cpu_info.vendor_id[0] >> 16) & 0xff,
             (system_info_.cpu.x86_cpu_info.vendor_id[0] >> 24) & 0xff,
              system_info_.cpu.x86_cpu_info.vendor_id[1] & 0xff,
             (system_info_.cpu.x86_cpu_info.vendor_id[1] >> 8) & 0xff,
             (system_info_.cpu.x86_cpu_info.vendor_id[1] >> 16) & 0xff,
             (system_info_.cpu.x86_cpu_info.vendor_id[1] >> 24) & 0xff,
              system_info_.cpu.x86_cpu_info.vendor_id[2] & 0xff,
             (system_info_.cpu.x86_cpu_info.vendor_id[2] >> 8) & 0xff,
             (system_info_.cpu.x86_cpu_info.vendor_id[2] >> 16) & 0xff,
             (system_info_.cpu.x86_cpu_info.vendor_id[2] >> 24) & 0xff);
    cpu_vendor_ = new string(cpu_vendor_string);
  }

  return cpu_vendor_;
}


void MinidumpSystemInfo::Print() {
  if (!valid_) {
    BPLOG(ERROR) << "MinidumpSystemInfo cannot print invalid data";
    return;
  }

  printf("MDRawSystemInfo\n");
  printf("  processor_architecture                     = 0x%x\n",
         system_info_.processor_architecture);
  printf("  processor_level                            = %d\n",
         system_info_.processor_level);
  printf("  processor_revision                         = 0x%x\n",
         system_info_.processor_revision);
  printf("  number_of_processors                       = %d\n",
         system_info_.number_of_processors);
  printf("  product_type                               = %d\n",
         system_info_.product_type);
  printf("  major_version                              = %d\n",
         system_info_.major_version);
  printf("  minor_version                              = %d\n",
         system_info_.minor_version);
  printf("  build_number                               = %d\n",
         system_info_.build_number);
  printf("  platform_id                                = 0x%x\n",
         system_info_.platform_id);
  printf("  csd_version_rva                            = 0x%x\n",
         system_info_.csd_version_rva);
  printf("  suite_mask                                 = 0x%x\n",
         system_info_.suite_mask);
  if (system_info_.processor_architecture == MD_CPU_ARCHITECTURE_X86 ||
      system_info_.processor_architecture == MD_CPU_ARCHITECTURE_X86_WIN64) {
    printf("  cpu.x86_cpu_info (valid):\n");
  } else {
    printf("  cpu.x86_cpu_info (invalid):\n");
  }
  for (unsigned int i = 0; i < 3; ++i) {
    printf("  cpu.x86_cpu_info.vendor_id[%d]              = 0x%x\n",
           i, system_info_.cpu.x86_cpu_info.vendor_id[i]);
  }
  printf("  cpu.x86_cpu_info.version_information       = 0x%x\n",
         system_info_.cpu.x86_cpu_info.version_information);
  printf("  cpu.x86_cpu_info.feature_information       = 0x%x\n",
         system_info_.cpu.x86_cpu_info.feature_information);
  printf("  cpu.x86_cpu_info.amd_extended_cpu_features = 0x%x\n",
         system_info_.cpu.x86_cpu_info.amd_extended_cpu_features);
  if (system_info_.processor_architecture != MD_CPU_ARCHITECTURE_X86 &&
      system_info_.processor_architecture != MD_CPU_ARCHITECTURE_X86_WIN64) {
    printf("  cpu.other_cpu_info (valid):\n");
    for (unsigned int i = 0; i < 2; ++i) {
      printf("  cpu.other_cpu_info.processor_features[%d]   = 0x%" PRIx64 "\n",
             i, system_info_.cpu.other_cpu_info.processor_features[i]);
    }
  }
  const string* csd_version = GetCSDVersion();
  if (csd_version) {
    printf("  (csd_version)                              = \"%s\"\n",
           csd_version->c_str());
  } else {
    printf("  (csd_version)                              = (null)\n");
  }
  const string* cpu_vendor = GetCPUVendor();
  if (cpu_vendor) {
    printf("  (cpu_vendor)                               = \"%s\"\n",
           cpu_vendor->c_str());
  } else {
    printf("  (cpu_vendor)                               = (null)\n");
  }
  printf("\n");
}


//
// MinidumpMiscInfo
//


MinidumpMiscInfo::MinidumpMiscInfo(Minidump* minidump)
    : MinidumpStream(minidump),
      misc_info_() {
}


bool MinidumpMiscInfo::Read(uint32_t expected_size) {
  valid_ = false;

  if (expected_size != MD_MISCINFO_SIZE &&
      expected_size != MD_MISCINFO2_SIZE &&
      expected_size != MD_MISCINFO3_SIZE &&
      expected_size != MD_MISCINFO4_SIZE) {
    BPLOG(ERROR) << "MinidumpMiscInfo size mismatch, " << expected_size
                 << " != " << MD_MISCINFO_SIZE << ", " << MD_MISCINFO2_SIZE
                 << ", " << MD_MISCINFO3_SIZE << ", " << MD_MISCINFO4_SIZE
                 << ")";
    return false;
  }

  if (!minidump_->ReadBytes(&misc_info_, expected_size)) {
    BPLOG(ERROR) << "MinidumpMiscInfo cannot read miscellaneous info";
    return false;
  }

  if (minidump_->swap()) {
    // Swap version 1 fields
    Swap(&misc_info_.size_of_info);
    Swap(&misc_info_.flags1);
    Swap(&misc_info_.process_id);
    Swap(&misc_info_.process_create_time);
    Swap(&misc_info_.process_user_time);
    Swap(&misc_info_.process_kernel_time);
    if (misc_info_.size_of_info > MD_MISCINFO_SIZE) {
      // Swap version 2 fields
      Swap(&misc_info_.processor_max_mhz);
      Swap(&misc_info_.processor_current_mhz);
      Swap(&misc_info_.processor_mhz_limit);
      Swap(&misc_info_.processor_max_idle_state);
      Swap(&misc_info_.processor_current_idle_state);
    }
    if (misc_info_.size_of_info > MD_MISCINFO2_SIZE) {
      // Swap version 3 fields
      Swap(&misc_info_.process_integrity_level);
      Swap(&misc_info_.process_execute_flags);
      Swap(&misc_info_.protected_process);
      Swap(&misc_info_.time_zone_id);
      Swap(&misc_info_.time_zone);
    }
    if (misc_info_.size_of_info > MD_MISCINFO3_SIZE) {
      // Swap version 4 fields.
      // Do not swap UTF-16 strings.  The swap is done as part of the
      // conversion to UTF-8 (code follows below).
    }
  }

  if (expected_size != misc_info_.size_of_info) {
    BPLOG(ERROR) << "MinidumpMiscInfo size mismatch, " <<
                    expected_size << " != " << misc_info_.size_of_info;
    return false;
  }

  // Convert UTF-16 strings
  if (misc_info_.size_of_info > MD_MISCINFO2_SIZE) {
    // Convert UTF-16 strings in version 3 fields
    ConvertUTF16BufferToUTF8String(misc_info_.time_zone.standard_name,
                                   sizeof(misc_info_.time_zone.standard_name),
                                   &standard_name_, minidump_->swap());
    ConvertUTF16BufferToUTF8String(misc_info_.time_zone.daylight_name,
                                   sizeof(misc_info_.time_zone.daylight_name),
                                   &daylight_name_, minidump_->swap());
  }
  if (misc_info_.size_of_info > MD_MISCINFO3_SIZE) {
    // Convert UTF-16 strings in version 4 fields
    ConvertUTF16BufferToUTF8String(misc_info_.build_string,
                                   sizeof(misc_info_.build_string),
                                   &build_string_, minidump_->swap());
    ConvertUTF16BufferToUTF8String(misc_info_.dbg_bld_str,
                                   sizeof(misc_info_.dbg_bld_str),
                                   &dbg_bld_str_, minidump_->swap());
  }

  valid_ = true;
  return true;
}


void MinidumpMiscInfo::Print() {
  if (!valid_) {
    BPLOG(ERROR) << "MinidumpMiscInfo cannot print invalid data";
    return;
  }

  printf("MDRawMiscInfo\n");
  // Print version 1 fields
  printf("  size_of_info                 = %d\n",   misc_info_.size_of_info);
  printf("  flags1                       = 0x%x\n", misc_info_.flags1);
  printf("  process_id                   = ");
  PrintValueOrInvalid(misc_info_.flags1 & MD_MISCINFO_FLAGS1_PROCESS_ID,
                      kNumberFormatDecimal, misc_info_.process_id);
  if (misc_info_.flags1 & MD_MISCINFO_FLAGS1_PROCESS_TIMES) {
    printf("  process_create_time          = 0x%x %s\n",
           misc_info_.process_create_time,
           TimeTToUTCString(misc_info_.process_create_time).c_str());
  } else {
    printf("  process_create_time          = (invalid)\n");
  }
  printf("  process_user_time            = ");
  PrintValueOrInvalid(misc_info_.flags1 & MD_MISCINFO_FLAGS1_PROCESS_TIMES,
                      kNumberFormatDecimal, misc_info_.process_user_time);
  printf("  process_kernel_time          = ");
  PrintValueOrInvalid(misc_info_.flags1 & MD_MISCINFO_FLAGS1_PROCESS_TIMES,
                      kNumberFormatDecimal, misc_info_.process_kernel_time);
  if (misc_info_.size_of_info > MD_MISCINFO_SIZE) {
    // Print version 2 fields
    printf("  processor_max_mhz            = ");
    PrintValueOrInvalid(misc_info_.flags1 &
                            MD_MISCINFO_FLAGS1_PROCESSOR_POWER_INFO,
                        kNumberFormatDecimal, misc_info_.processor_max_mhz);
    printf("  processor_current_mhz        = ");
    PrintValueOrInvalid(misc_info_.flags1 &
                            MD_MISCINFO_FLAGS1_PROCESSOR_POWER_INFO,
                        kNumberFormatDecimal, misc_info_.processor_current_mhz);
    printf("  processor_mhz_limit          = ");
    PrintValueOrInvalid(misc_info_.flags1 &
                            MD_MISCINFO_FLAGS1_PROCESSOR_POWER_INFO,
                        kNumberFormatDecimal, misc_info_.processor_mhz_limit);
    printf("  processor_max_idle_state     = ");
    PrintValueOrInvalid(misc_info_.flags1 &
                            MD_MISCINFO_FLAGS1_PROCESSOR_POWER_INFO,
                        kNumberFormatDecimal,
                        misc_info_.processor_max_idle_state);
    printf("  processor_current_idle_state = ");
    PrintValueOrInvalid(misc_info_.flags1 &
                            MD_MISCINFO_FLAGS1_PROCESSOR_POWER_INFO,
                        kNumberFormatDecimal,
                        misc_info_.processor_current_idle_state);
  }
  if (misc_info_.size_of_info > MD_MISCINFO2_SIZE) {
    // Print version 3 fields
    printf("  process_integrity_level      = ");
    PrintValueOrInvalid(misc_info_.flags1 &
                            MD_MISCINFO_FLAGS1_PROCESS_INTEGRITY,
                        kNumberFormatHexadecimal,
                        misc_info_.process_integrity_level);
    printf("  process_execute_flags        = ");
    PrintValueOrInvalid(misc_info_.flags1 &
                            MD_MISCINFO_FLAGS1_PROCESS_EXECUTE_FLAGS,
                        kNumberFormatHexadecimal,
                        misc_info_.process_execute_flags);
    printf("  protected_process            = ");
    PrintValueOrInvalid(misc_info_.flags1 &
                            MD_MISCINFO_FLAGS1_PROTECTED_PROCESS,
                        kNumberFormatDecimal, misc_info_.protected_process);
    printf("  time_zone_id                 = ");
    PrintValueOrInvalid(misc_info_.flags1 & MD_MISCINFO_FLAGS1_TIMEZONE,
                        kNumberFormatDecimal, misc_info_.time_zone_id);
    if (misc_info_.flags1 & MD_MISCINFO_FLAGS1_TIMEZONE) {
      printf("  time_zone.bias               = %d\n",
             misc_info_.time_zone.bias);
      printf("  time_zone.standard_name      = %s\n", standard_name_.c_str());
      printf("  time_zone.standard_date      = "
                 "%04d-%02d-%02d (%d) %02d:%02d:%02d.%03d\n",
             misc_info_.time_zone.standard_date.year,
             misc_info_.time_zone.standard_date.month,
             misc_info_.time_zone.standard_date.day,
             misc_info_.time_zone.standard_date.day_of_week,
             misc_info_.time_zone.standard_date.hour,
             misc_info_.time_zone.standard_date.minute,
             misc_info_.time_zone.standard_date.second,
             misc_info_.time_zone.standard_date.milliseconds);
      printf("  time_zone.standard_bias      = %d\n",
             misc_info_.time_zone.standard_bias);
      printf("  time_zone.daylight_name      = %s\n", daylight_name_.c_str());
      printf("  time_zone.daylight_date      = "
                 "%04d-%02d-%02d (%d) %02d:%02d:%02d.%03d\n",
             misc_info_.time_zone.daylight_date.year,
             misc_info_.time_zone.daylight_date.month,
             misc_info_.time_zone.daylight_date.day,
             misc_info_.time_zone.daylight_date.day_of_week,
             misc_info_.time_zone.daylight_date.hour,
             misc_info_.time_zone.daylight_date.minute,
             misc_info_.time_zone.daylight_date.second,
             misc_info_.time_zone.daylight_date.milliseconds);
      printf("  time_zone.daylight_bias      = %d\n",
             misc_info_.time_zone.daylight_bias);
    } else {
      printf("  time_zone.bias               = (invalid)\n");
      printf("  time_zone.standard_name      = (invalid)\n");
      printf("  time_zone.standard_date      = (invalid)\n");
      printf("  time_zone.standard_bias      = (invalid)\n");
      printf("  time_zone.daylight_name      = (invalid)\n");
      printf("  time_zone.daylight_date      = (invalid)\n");
      printf("  time_zone.daylight_bias      = (invalid)\n");
    }
  }
  if (misc_info_.size_of_info > MD_MISCINFO3_SIZE) {
    // Print version 4 fields
    if (misc_info_.flags1 & MD_MISCINFO_FLAGS1_BUILDSTRING) {
      printf("  build_string                 = %s\n", build_string_.c_str());
      printf("  dbg_bld_str                  = %s\n", dbg_bld_str_.c_str());
    } else {
      printf("  build_string                 = (invalid)\n");
      printf("  dbg_bld_str                  = (invalid)\n");
    }
  }
  printf("\n");
}


//
// MinidumpBreakpadInfo
//


MinidumpBreakpadInfo::MinidumpBreakpadInfo(Minidump* minidump)
    : MinidumpStream(minidump),
      breakpad_info_() {
}


bool MinidumpBreakpadInfo::Read(uint32_t expected_size) {
  valid_ = false;

  if (expected_size != sizeof(breakpad_info_)) {
    BPLOG(ERROR) << "MinidumpBreakpadInfo size mismatch, " << expected_size <<
                    " != " << sizeof(breakpad_info_);
    return false;
  }

  if (!minidump_->ReadBytes(&breakpad_info_, sizeof(breakpad_info_))) {
    BPLOG(ERROR) << "MinidumpBreakpadInfo cannot read Breakpad info";
    return false;
  }

  if (minidump_->swap()) {
    Swap(&breakpad_info_.validity);
    Swap(&breakpad_info_.dump_thread_id);
    Swap(&breakpad_info_.requesting_thread_id);
  }

  valid_ = true;
  return true;
}


bool MinidumpBreakpadInfo::GetDumpThreadID(uint32_t *thread_id) const {
  BPLOG_IF(ERROR, !thread_id) << "MinidumpBreakpadInfo::GetDumpThreadID "
                                 "requires |thread_id|";
  assert(thread_id);
  *thread_id = 0;

  if (!valid_) {
    BPLOG(ERROR) << "Invalid MinidumpBreakpadInfo for GetDumpThreadID";
    return false;
  }

  if (!(breakpad_info_.validity & MD_BREAKPAD_INFO_VALID_DUMP_THREAD_ID)) {
    BPLOG(INFO) << "MinidumpBreakpadInfo has no dump thread";
    return false;
  }

  *thread_id = breakpad_info_.dump_thread_id;
  return true;
}


bool MinidumpBreakpadInfo::GetRequestingThreadID(uint32_t *thread_id)
    const {
  BPLOG_IF(ERROR, !thread_id) << "MinidumpBreakpadInfo::GetRequestingThreadID "
                                 "requires |thread_id|";
  assert(thread_id);
  *thread_id = 0;

  if (!thread_id || !valid_) {
    BPLOG(ERROR) << "Invalid MinidumpBreakpadInfo for GetRequestingThreadID";
    return false;
  }

  if (!(breakpad_info_.validity &
            MD_BREAKPAD_INFO_VALID_REQUESTING_THREAD_ID)) {
    BPLOG(INFO) << "MinidumpBreakpadInfo has no requesting thread";
    return false;
  }

  *thread_id = breakpad_info_.requesting_thread_id;
  return true;
}


void MinidumpBreakpadInfo::Print() {
  if (!valid_) {
    BPLOG(ERROR) << "MinidumpBreakpadInfo cannot print invalid data";
    return;
  }

  printf("MDRawBreakpadInfo\n");
  printf("  validity             = 0x%x\n", breakpad_info_.validity);
  printf("  dump_thread_id       = ");
  PrintValueOrInvalid(breakpad_info_.validity &
                          MD_BREAKPAD_INFO_VALID_DUMP_THREAD_ID,
                      kNumberFormatHexadecimal, breakpad_info_.dump_thread_id);
  printf("  requesting_thread_id = ");
  PrintValueOrInvalid(breakpad_info_.validity &
                          MD_BREAKPAD_INFO_VALID_REQUESTING_THREAD_ID,
                      kNumberFormatHexadecimal,
                      breakpad_info_.requesting_thread_id);

  printf("\n");
}


//
// MinidumpMemoryInfo
//


MinidumpMemoryInfo::MinidumpMemoryInfo(Minidump* minidump)
    : MinidumpObject(minidump),
      memory_info_() {
}


bool MinidumpMemoryInfo::IsExecutable() const {
  uint32_t protection =
      memory_info_.protection & MD_MEMORY_PROTECTION_ACCESS_MASK;
  return protection == MD_MEMORY_PROTECT_EXECUTE ||
      protection == MD_MEMORY_PROTECT_EXECUTE_READ ||
      protection == MD_MEMORY_PROTECT_EXECUTE_READWRITE;
}


bool MinidumpMemoryInfo::IsWritable() const {
  uint32_t protection =
      memory_info_.protection & MD_MEMORY_PROTECTION_ACCESS_MASK;
  return protection == MD_MEMORY_PROTECT_READWRITE ||
    protection == MD_MEMORY_PROTECT_WRITECOPY ||
    protection == MD_MEMORY_PROTECT_EXECUTE_READWRITE ||
    protection == MD_MEMORY_PROTECT_EXECUTE_WRITECOPY;
}


bool MinidumpMemoryInfo::Read() {
  valid_ = false;

  if (!minidump_->ReadBytes(&memory_info_, sizeof(memory_info_))) {
    BPLOG(ERROR) << "MinidumpMemoryInfo cannot read memory info";
    return false;
  }

  if (minidump_->swap()) {
    Swap(&memory_info_.base_address);
    Swap(&memory_info_.allocation_base);
    Swap(&memory_info_.allocation_protection);
    Swap(&memory_info_.region_size);
    Swap(&memory_info_.state);
    Swap(&memory_info_.protection);
    Swap(&memory_info_.type);
  }

  // Check for base + size overflow or undersize.
  if (memory_info_.region_size == 0 ||
      memory_info_.region_size > numeric_limits<uint64_t>::max() -
                                     memory_info_.base_address) {
    BPLOG(ERROR) << "MinidumpMemoryInfo has a memory region problem, " <<
                    HexString(memory_info_.base_address) << "+" <<
                    HexString(memory_info_.region_size);
    return false;
  }

  valid_ = true;
  return true;
}


void MinidumpMemoryInfo::Print() {
  if (!valid_) {
    BPLOG(ERROR) << "MinidumpMemoryInfo cannot print invalid data";
    return;
  }

  printf("MDRawMemoryInfo\n");
  printf("  base_address          = 0x%" PRIx64 "\n",
         memory_info_.base_address);
  printf("  allocation_base       = 0x%" PRIx64 "\n",
         memory_info_.allocation_base);
  printf("  allocation_protection = 0x%x\n",
         memory_info_.allocation_protection);
  printf("  region_size           = 0x%" PRIx64 "\n", memory_info_.region_size);
  printf("  state                 = 0x%x\n", memory_info_.state);
  printf("  protection            = 0x%x\n", memory_info_.protection);
  printf("  type                  = 0x%x\n", memory_info_.type);
}


//
// MinidumpMemoryInfoList
//


MinidumpMemoryInfoList::MinidumpMemoryInfoList(Minidump* minidump)
    : MinidumpStream(minidump),
      range_map_(new RangeMap<uint64_t, unsigned int>()),
      infos_(NULL),
      info_count_(0) {
}


MinidumpMemoryInfoList::~MinidumpMemoryInfoList() {
  delete range_map_;
  delete infos_;
}


bool MinidumpMemoryInfoList::Read(uint32_t expected_size) {
  // Invalidate cached data.
  delete infos_;
  infos_ = NULL;
  range_map_->Clear();
  info_count_ = 0;

  valid_ = false;

  MDRawMemoryInfoList header;
  if (expected_size < sizeof(MDRawMemoryInfoList)) {
    BPLOG(ERROR) << "MinidumpMemoryInfoList header size mismatch, " <<
                    expected_size << " < " << sizeof(MDRawMemoryInfoList);
    return false;
  }
  if (!minidump_->ReadBytes(&header, sizeof(header))) {
    BPLOG(ERROR) << "MinidumpMemoryInfoList could not read header";
    return false;
  }

  if (minidump_->swap()) {
    Swap(&header.size_of_header);
    Swap(&header.size_of_entry);
    Swap(&header.number_of_entries);
  }

  // Sanity check that the header is the expected size.
  // TODO(ted): could possibly handle this more gracefully, assuming
  // that future versions of the structs would be backwards-compatible.
  if (header.size_of_header != sizeof(MDRawMemoryInfoList)) {
    BPLOG(ERROR) << "MinidumpMemoryInfoList header size mismatch, " <<
                    header.size_of_header << " != " <<
                    sizeof(MDRawMemoryInfoList);
    return false;
  }

  // Sanity check that the entries are the expected size.
  if (header.size_of_entry != sizeof(MDRawMemoryInfo)) {
    BPLOG(ERROR) << "MinidumpMemoryInfoList entry size mismatch, " <<
                    header.size_of_entry << " != " <<
                    sizeof(MDRawMemoryInfo);
    return false;
  }

  if (header.number_of_entries >
          numeric_limits<uint32_t>::max() / sizeof(MDRawMemoryInfo)) {
    BPLOG(ERROR) << "MinidumpMemoryInfoList info count " <<
                    header.number_of_entries <<
                    " would cause multiplication overflow";
    return false;
  }

  if (expected_size != sizeof(MDRawMemoryInfoList) +
                        header.number_of_entries * sizeof(MDRawMemoryInfo)) {
    BPLOG(ERROR) << "MinidumpMemoryInfoList size mismatch, " << expected_size <<
                    " != " << sizeof(MDRawMemoryInfoList) +
                        header.number_of_entries * sizeof(MDRawMemoryInfo);
    return false;
  }

  // Check for data loss when converting header.number_of_entries from
  // uint64_t into MinidumpMemoryInfos::size_type (uint32_t)
  MinidumpMemoryInfos::size_type header_number_of_entries =
      static_cast<unsigned int>(header.number_of_entries);
  if (static_cast<uint64_t>(header_number_of_entries) !=
      header.number_of_entries) {
    BPLOG(ERROR) << "Data loss detected when converting "
                    "the header's number_of_entries";
    return false;
  }

  if (header.number_of_entries != 0) {
    scoped_ptr<MinidumpMemoryInfos> infos(
        new MinidumpMemoryInfos(header_number_of_entries,
                                MinidumpMemoryInfo(minidump_)));

    for (unsigned int index = 0;
         index < header.number_of_entries;
         ++index) {
      MinidumpMemoryInfo* info = &(*infos)[index];

      // Assume that the file offset is correct after the last read.
      if (!info->Read()) {
        BPLOG(ERROR) << "MinidumpMemoryInfoList cannot read info " <<
                        index << "/" << header.number_of_entries;
        return false;
      }

      uint64_t base_address = info->GetBase();
      uint64_t region_size = info->GetSize();

      if (!range_map_->StoreRange(base_address, region_size, index)) {
        BPLOG(ERROR) << "MinidumpMemoryInfoList could not store"
                        " memory region " <<
                        index << "/" << header.number_of_entries << ", " <<
                        HexString(base_address) << "+" <<
                        HexString(region_size);
        return false;
      }
    }

    infos_ = infos.release();
  }

  info_count_ = header_number_of_entries;

  valid_ = true;
  return true;
}


const MinidumpMemoryInfo* MinidumpMemoryInfoList::GetMemoryInfoAtIndex(
      unsigned int index) const {
  if (!valid_) {
    BPLOG(ERROR) << "Invalid MinidumpMemoryInfoList for GetMemoryInfoAtIndex";
    return NULL;
  }

  if (index >= info_count_) {
    BPLOG(ERROR) << "MinidumpMemoryInfoList index out of range: " <<
                    index << "/" << info_count_;
    return NULL;
  }

  return &(*infos_)[index];
}


const MinidumpMemoryInfo* MinidumpMemoryInfoList::GetMemoryInfoForAddress(
    uint64_t address) const {
  if (!valid_) {
    BPLOG(ERROR) << "Invalid MinidumpMemoryInfoList for"
                    " GetMemoryInfoForAddress";
    return NULL;
  }

  unsigned int info_index;
  if (!range_map_->RetrieveRange(address, &info_index, NULL, NULL)) {
    BPLOG(INFO) << "MinidumpMemoryInfoList has no memory info at " <<
                   HexString(address);
    return NULL;
  }

  return GetMemoryInfoAtIndex(info_index);
}


void MinidumpMemoryInfoList::Print() {
  if (!valid_) {
    BPLOG(ERROR) << "MinidumpMemoryInfoList cannot print invalid data";
    return;
  }

  printf("MinidumpMemoryInfoList\n");
  printf("  info_count = %d\n", info_count_);
  printf("\n");

  for (unsigned int info_index = 0;
       info_index < info_count_;
       ++info_index) {
    printf("info[%d]\n", info_index);
    (*infos_)[info_index].Print();
    printf("\n");
  }
}


//
// Minidump
//


uint32_t Minidump::max_streams_ = 128;
unsigned int Minidump::max_string_length_ = 1024;


Minidump::Minidump(const string& path)
    : header_(),
      directory_(NULL),
      stream_map_(new MinidumpStreamMap()),
      path_(path),
      stream_(NULL),
      swap_(false),
      valid_(false) {
}

Minidump::Minidump(istream& stream)
    : header_(),
      directory_(NULL),
      stream_map_(new MinidumpStreamMap()),
      path_(),
      stream_(&stream),
      swap_(false),
      valid_(false) {
}

Minidump::~Minidump() {
  if (stream_) {
    BPLOG(INFO) << "Minidump closing minidump";
  }
  if (!path_.empty()) {
    delete stream_;
  }
  delete directory_;
  delete stream_map_;
}


bool Minidump::Open() {
  if (stream_ != NULL) {
    BPLOG(INFO) << "Minidump reopening minidump " << path_;

    // The file is already open.  Seek to the beginning, which is the position
    // the file would be at if it were opened anew.
    return SeekSet(0);
  }

  stream_ = new ifstream(path_.c_str(), std::ios::in | std::ios::binary);
  if (!stream_ || !stream_->good()) {
    string error_string;
    int error_code = ErrnoString(&error_string);
    BPLOG(ERROR) << "Minidump could not open minidump " << path_ <<
                    ", error " << error_code << ": " << error_string;
    return false;
  }

  BPLOG(INFO) << "Minidump opened minidump " << path_;
  return true;
}

bool Minidump::GetContextCPUFlagsFromSystemInfo(uint32_t *context_cpu_flags) {
  // Initialize output parameters
  *context_cpu_flags = 0;

  // Save the current stream position
  off_t saved_position = Tell();
  if (saved_position == -1) {
    // Failed to save the current stream position.
    // Returns true because the current position of the stream is preserved.
    return true;
  }

  const MDRawSystemInfo* system_info =
    GetSystemInfo() ? GetSystemInfo()->system_info() : NULL;

  if (system_info != NULL) {
    switch (system_info->processor_architecture) {
      case MD_CPU_ARCHITECTURE_X86:
        *context_cpu_flags = MD_CONTEXT_X86;
        break;
      case MD_CPU_ARCHITECTURE_MIPS:
        *context_cpu_flags = MD_CONTEXT_MIPS;
        break;
      case MD_CPU_ARCHITECTURE_ALPHA:
        *context_cpu_flags = MD_CONTEXT_ALPHA;
        break;
      case MD_CPU_ARCHITECTURE_PPC:
        *context_cpu_flags = MD_CONTEXT_PPC;
        break;
      case MD_CPU_ARCHITECTURE_PPC64:
        *context_cpu_flags = MD_CONTEXT_PPC64;
        break;
      case MD_CPU_ARCHITECTURE_SHX:
        *context_cpu_flags = MD_CONTEXT_SHX;
        break;
      case MD_CPU_ARCHITECTURE_ARM:
        *context_cpu_flags = MD_CONTEXT_ARM;
        break;
      case MD_CPU_ARCHITECTURE_ARM64:
        *context_cpu_flags = MD_CONTEXT_ARM64;
        break;
      case MD_CPU_ARCHITECTURE_IA64:
        *context_cpu_flags = MD_CONTEXT_IA64;
        break;
      case MD_CPU_ARCHITECTURE_ALPHA64:
        *context_cpu_flags = 0;
        break;
      case MD_CPU_ARCHITECTURE_MSIL:
        *context_cpu_flags = 0;
        break;
      case MD_CPU_ARCHITECTURE_AMD64:
        *context_cpu_flags = MD_CONTEXT_AMD64;
        break;
      case MD_CPU_ARCHITECTURE_X86_WIN64:
        *context_cpu_flags = 0;
        break;
      case MD_CPU_ARCHITECTURE_SPARC:
        *context_cpu_flags = MD_CONTEXT_SPARC;
        break;
      case MD_CPU_ARCHITECTURE_UNKNOWN:
        *context_cpu_flags = 0;
        break;
      default:
        *context_cpu_flags = 0;
        break;
    }
  }

  // Restore position and return
  return SeekSet(saved_position);
}


bool Minidump::Read() {
  // Invalidate cached data.
  delete directory_;
  directory_ = NULL;
  stream_map_->clear();

  valid_ = false;

  if (!Open()) {
    BPLOG(ERROR) << "Minidump cannot open minidump";
    return false;
  }

  if (!ReadBytes(&header_, sizeof(MDRawHeader))) {
    BPLOG(ERROR) << "Minidump cannot read header";
    return false;
  }

  if (header_.signature != MD_HEADER_SIGNATURE) {
    // The file may be byte-swapped.  Under the present architecture, these
    // classes don't know or need to know what CPU (or endianness) the
    // minidump was produced on in order to parse it.  Use the signature as
    // a byte order marker.
    uint32_t signature_swapped = header_.signature;
    Swap(&signature_swapped);
    if (signature_swapped != MD_HEADER_SIGNATURE) {
      // This isn't a minidump or a byte-swapped minidump.
      BPLOG(ERROR) << "Minidump header signature mismatch: (" <<
                      HexString(header_.signature) << ", " <<
                      HexString(signature_swapped) << ") != " <<
                      HexString(MD_HEADER_SIGNATURE);
      return false;
    }
    swap_ = true;
  } else {
    // The file is not byte-swapped.  Set swap_ false (it may have been true
    // if the object is being reused?)
    swap_ = false;
  }

  BPLOG(INFO) << "Minidump " << (swap_ ? "" : "not ") <<
                 "byte-swapping minidump";

  if (swap_) {
    Swap(&header_.signature);
    Swap(&header_.version);
    Swap(&header_.stream_count);
    Swap(&header_.stream_directory_rva);
    Swap(&header_.checksum);
    Swap(&header_.time_date_stamp);
    Swap(&header_.flags);
  }

  // Version check.  The high 16 bits of header_.version contain something
  // else "implementation specific."
  if ((header_.version & 0x0000ffff) != MD_HEADER_VERSION) {
    BPLOG(ERROR) << "Minidump version mismatch: " <<
                    HexString(header_.version & 0x0000ffff) << " != " <<
                    HexString(MD_HEADER_VERSION);
    return false;
  }

  if (!SeekSet(header_.stream_directory_rva)) {
    BPLOG(ERROR) << "Minidump cannot seek to stream directory";
    return false;
  }

  if (header_.stream_count > max_streams_) {
    BPLOG(ERROR) << "Minidump stream count " << header_.stream_count <<
                    " exceeds maximum " << max_streams_;
    return false;
  }

  if (header_.stream_count != 0) {
    scoped_ptr<MinidumpDirectoryEntries> directory(
        new MinidumpDirectoryEntries(header_.stream_count));

    // Read the entire array in one fell swoop, instead of reading one entry
    // at a time in the loop.
    if (!ReadBytes(&(*directory)[0],
                   sizeof(MDRawDirectory) * header_.stream_count)) {
      BPLOG(ERROR) << "Minidump cannot read stream directory";
      return false;
    }

    for (unsigned int stream_index = 0;
         stream_index < header_.stream_count;
         ++stream_index) {
      MDRawDirectory* directory_entry = &(*directory)[stream_index];

      if (swap_) {
        Swap(&directory_entry->stream_type);
        Swap(&directory_entry->location);
      }

      // Initialize the stream_map_ map, which speeds locating a stream by
      // type.
      unsigned int stream_type = directory_entry->stream_type;
      switch (stream_type) {
        case MD_THREAD_LIST_STREAM:
        case MD_MODULE_LIST_STREAM:
        case MD_MEMORY_LIST_STREAM:
        case MD_EXCEPTION_STREAM:
        case MD_SYSTEM_INFO_STREAM:
        case MD_MISC_INFO_STREAM:
        case MD_BREAKPAD_INFO_STREAM: {
          if (stream_map_->find(stream_type) != stream_map_->end()) {
            // Another stream with this type was already found.  A minidump
            // file should contain at most one of each of these stream types.
            BPLOG(ERROR) << "Minidump found multiple streams of type " <<
                            stream_type << ", but can only deal with one";
            return false;
          }
          // Fall through to default
        }

        default: {
          // Overwrites for stream types other than those above, but it's
          // expected to be the user's burden in that case.
          (*stream_map_)[stream_type].stream_index = stream_index;
        }
      }
    }

    directory_ = directory.release();
  }

  valid_ = true;
  return true;
}


MinidumpThreadList* Minidump::GetThreadList() {
  MinidumpThreadList* thread_list;
  return GetStream(&thread_list);
}


MinidumpModuleList* Minidump::GetModuleList() {
  MinidumpModuleList* module_list;
  return GetStream(&module_list);
}


MinidumpMemoryList* Minidump::GetMemoryList() {
  MinidumpMemoryList* memory_list;
  return GetStream(&memory_list);
}


MinidumpException* Minidump::GetException() {
  MinidumpException* exception;
  return GetStream(&exception);
}

MinidumpAssertion* Minidump::GetAssertion() {
  MinidumpAssertion* assertion;
  return GetStream(&assertion);
}


MinidumpSystemInfo* Minidump::GetSystemInfo() {
  MinidumpSystemInfo* system_info;
  return GetStream(&system_info);
}


MinidumpMiscInfo* Minidump::GetMiscInfo() {
  MinidumpMiscInfo* misc_info;
  return GetStream(&misc_info);
}


MinidumpBreakpadInfo* Minidump::GetBreakpadInfo() {
  MinidumpBreakpadInfo* breakpad_info;
  return GetStream(&breakpad_info);
}

MinidumpMemoryInfoList* Minidump::GetMemoryInfoList() {
  MinidumpMemoryInfoList* memory_info_list;
  return GetStream(&memory_info_list);
}

static const char* get_stream_name(uint32_t stream_type) {
  switch (stream_type) {
  case MD_UNUSED_STREAM:
    return "MD_UNUSED_STREAM";
  case MD_RESERVED_STREAM_0:
    return "MD_RESERVED_STREAM_0";
  case MD_RESERVED_STREAM_1:
    return "MD_RESERVED_STREAM_1";
  case MD_THREAD_LIST_STREAM:
    return "MD_THREAD_LIST_STREAM";
  case MD_MODULE_LIST_STREAM:
    return "MD_MODULE_LIST_STREAM";
  case MD_MEMORY_LIST_STREAM:
    return "MD_MEMORY_LIST_STREAM";
  case MD_EXCEPTION_STREAM:
    return "MD_EXCEPTION_STREAM";
  case MD_SYSTEM_INFO_STREAM:
    return "MD_SYSTEM_INFO_STREAM";
  case MD_THREAD_EX_LIST_STREAM:
    return "MD_THREAD_EX_LIST_STREAM";
  case MD_MEMORY_64_LIST_STREAM:
    return "MD_MEMORY_64_LIST_STREAM";
  case MD_COMMENT_STREAM_A:
    return "MD_COMMENT_STREAM_A";
  case MD_COMMENT_STREAM_W:
    return "MD_COMMENT_STREAM_W";
  case MD_HANDLE_DATA_STREAM:
    return "MD_HANDLE_DATA_STREAM";
  case MD_FUNCTION_TABLE_STREAM:
    return "MD_FUNCTION_TABLE_STREAM";
  case MD_UNLOADED_MODULE_LIST_STREAM:
    return "MD_UNLOADED_MODULE_LIST_STREAM";
  case MD_MISC_INFO_STREAM:
    return "MD_MISC_INFO_STREAM";
  case MD_MEMORY_INFO_LIST_STREAM:
    return "MD_MEMORY_INFO_LIST_STREAM";
  case MD_THREAD_INFO_LIST_STREAM:
    return "MD_THREAD_INFO_LIST_STREAM";
  case MD_HANDLE_OPERATION_LIST_STREAM:
    return "MD_HANDLE_OPERATION_LIST_STREAM";
  case MD_LAST_RESERVED_STREAM:
    return "MD_LAST_RESERVED_STREAM";
  case MD_BREAKPAD_INFO_STREAM:
    return "MD_BREAKPAD_INFO_STREAM";
  case MD_ASSERTION_INFO_STREAM:
    return "MD_ASSERTION_INFO_STREAM";
  case MD_LINUX_CPU_INFO:
    return "MD_LINUX_CPU_INFO";
  case MD_LINUX_PROC_STATUS:
    return "MD_LINUX_PROC_STATUS";
  case MD_LINUX_LSB_RELEASE:
    return "MD_LINUX_LSB_RELEASE";
  case MD_LINUX_CMD_LINE:
    return "MD_LINUX_CMD_LINE";
  case MD_LINUX_ENVIRON:
    return "MD_LINUX_ENVIRON";
  case MD_LINUX_AUXV:
    return "MD_LINUX_AUXV";
  case MD_LINUX_MAPS:
    return "MD_LINUX_MAPS";
  case MD_LINUX_DSO_DEBUG:
    return "MD_LINUX_DSO_DEBUG";
  default:
    return "unknown";
  }
}

void Minidump::Print() {
  if (!valid_) {
    BPLOG(ERROR) << "Minidump cannot print invalid data";
    return;
  }

  printf("MDRawHeader\n");
  printf("  signature            = 0x%x\n",    header_.signature);
  printf("  version              = 0x%x\n",    header_.version);
  printf("  stream_count         = %d\n",      header_.stream_count);
  printf("  stream_directory_rva = 0x%x\n",    header_.stream_directory_rva);
  printf("  checksum             = 0x%x\n",    header_.checksum);
  printf("  time_date_stamp      = 0x%x %s\n",
         header_.time_date_stamp,
         TimeTToUTCString(header_.time_date_stamp).c_str());
  printf("  flags                = 0x%" PRIx64 "\n",  header_.flags);
  printf("\n");

  for (unsigned int stream_index = 0;
       stream_index < header_.stream_count;
       ++stream_index) {
    MDRawDirectory* directory_entry = &(*directory_)[stream_index];

    printf("mDirectory[%d]\n", stream_index);
    printf("MDRawDirectory\n");
    printf("  stream_type        = 0x%x (%s)\n", directory_entry->stream_type,
           get_stream_name(directory_entry->stream_type));
    printf("  location.data_size = %d\n",
           directory_entry->location.data_size);
    printf("  location.rva       = 0x%x\n", directory_entry->location.rva);
    printf("\n");
  }

  printf("Streams:\n");
  for (MinidumpStreamMap::const_iterator iterator = stream_map_->begin();
       iterator != stream_map_->end();
       ++iterator) {
    uint32_t stream_type = iterator->first;
    MinidumpStreamInfo info = iterator->second;
    printf("  stream type 0x%x (%s) at index %d\n", stream_type,
           get_stream_name(stream_type),
           info.stream_index);
  }
  printf("\n");
}


const MDRawDirectory* Minidump::GetDirectoryEntryAtIndex(unsigned int index)
      const {
  if (!valid_) {
    BPLOG(ERROR) << "Invalid Minidump for GetDirectoryEntryAtIndex";
    return NULL;
  }

  if (index >= header_.stream_count) {
    BPLOG(ERROR) << "Minidump stream directory index out of range: " <<
                    index << "/" << header_.stream_count;
    return NULL;
  }

  return &(*directory_)[index];
}


bool Minidump::ReadBytes(void* bytes, size_t count) {
  // Can't check valid_ because Read needs to call this method before
  // validity can be determined.
  if (!stream_) {
    return false;
  }
  stream_->read(static_cast<char*>(bytes), count);
  std::streamsize bytes_read = stream_->gcount();
  if (bytes_read == -1) {
    string error_string;
    int error_code = ErrnoString(&error_string);
    BPLOG(ERROR) << "ReadBytes: error " << error_code << ": " << error_string;
    return false;
  }

  // Convert to size_t and check for data loss
  size_t bytes_read_converted = static_cast<size_t>(bytes_read);
  if (static_cast<std::streamsize>(bytes_read_converted) != bytes_read) {
    BPLOG(ERROR) << "ReadBytes: conversion data loss detected when converting "
                 << bytes_read << " to " << bytes_read_converted;
    return false;
  }

  if (bytes_read_converted != count) {
    BPLOG(ERROR) << "ReadBytes: read " << bytes_read_converted << "/" << count;
    return false;
  }

  return true;
}


bool Minidump::SeekSet(off_t offset) {
  // Can't check valid_ because Read needs to call this method before
  // validity can be determined.
  if (!stream_) {
    return false;
  }
  stream_->seekg(offset, std::ios_base::beg);
  if (!stream_->good()) {
    string error_string;
    int error_code = ErrnoString(&error_string);
    BPLOG(ERROR) << "SeekSet: error " << error_code << ": " << error_string;
    return false;
  }
  return true;
}

off_t Minidump::Tell() {
  if (!valid_ || !stream_) {
    return (off_t)-1;
  }

  // Check for conversion data loss
  std::streamoff std_streamoff = stream_->tellg();
  off_t rv = static_cast<off_t>(std_streamoff);
  if (static_cast<std::streamoff>(rv) == std_streamoff) {
    return rv;
  } else {
    BPLOG(ERROR) << "Data loss detected";
    return (off_t)-1;
  }
}


string* Minidump::ReadString(off_t offset) {
  if (!valid_) {
    BPLOG(ERROR) << "Invalid Minidump for ReadString";
    return NULL;
  }
  if (!SeekSet(offset)) {
    BPLOG(ERROR) << "ReadString could not seek to string at offset " << offset;
    return NULL;
  }

  uint32_t bytes;
  if (!ReadBytes(&bytes, sizeof(bytes))) {
    BPLOG(ERROR) << "ReadString could not read string size at offset " <<
                    offset;
    return NULL;
  }
  if (swap_)
    Swap(&bytes);

  if (bytes % 2 != 0) {
    BPLOG(ERROR) << "ReadString found odd-sized " << bytes <<
                    "-byte string at offset " << offset;
    return NULL;
  }
  unsigned int utf16_words = bytes / 2;

  if (utf16_words > max_string_length_) {
    BPLOG(ERROR) << "ReadString string length " << utf16_words <<
                    " exceeds maximum " << max_string_length_ <<
                    " at offset " << offset;
    return NULL;
  }

  vector<uint16_t> string_utf16(utf16_words);

  if (utf16_words) {
    if (!ReadBytes(&string_utf16[0], bytes)) {
      BPLOG(ERROR) << "ReadString could not read " << bytes <<
                      "-byte string at offset " << offset;
      return NULL;
    }
  }

  return UTF16ToUTF8(string_utf16, swap_);
}


bool Minidump::SeekToStreamType(uint32_t  stream_type,
                                uint32_t* stream_length) {
  BPLOG_IF(ERROR, !stream_length) << "Minidump::SeekToStreamType requires "
                                     "|stream_length|";
  assert(stream_length);
  *stream_length = 0;

  if (!valid_) {
    BPLOG(ERROR) << "Invalid Mindump for SeekToStreamType";
    return false;
  }

  MinidumpStreamMap::const_iterator iterator = stream_map_->find(stream_type);
  if (iterator == stream_map_->end()) {
    // This stream type didn't exist in the directory.
    BPLOG(INFO) << "SeekToStreamType: type " << stream_type << " not present";
    return false;
  }

  MinidumpStreamInfo info = iterator->second;
  if (info.stream_index >= header_.stream_count) {
    BPLOG(ERROR) << "SeekToStreamType: type " << stream_type <<
                    " out of range: " <<
                    info.stream_index << "/" << header_.stream_count;
    return false;
  }

  MDRawDirectory* directory_entry = &(*directory_)[info.stream_index];
  if (!SeekSet(directory_entry->location.rva)) {
    BPLOG(ERROR) << "SeekToStreamType could not seek to stream type " <<
                    stream_type;
    return false;
  }

  *stream_length = directory_entry->location.data_size;

  return true;
}


template<typename T>
T* Minidump::GetStream(T** stream) {
  // stream is a garbage parameter that's present only to account for C++'s
  // inability to overload a method based solely on its return type.

  const uint32_t stream_type = T::kStreamType;

  BPLOG_IF(ERROR, !stream) << "Minidump::GetStream type " << stream_type <<
                              " requires |stream|";
  assert(stream);
  *stream = NULL;

  if (!valid_) {
    BPLOG(ERROR) << "Invalid Minidump for GetStream type " << stream_type;
    return NULL;
  }

  MinidumpStreamMap::iterator iterator = stream_map_->find(stream_type);
  if (iterator == stream_map_->end()) {
    // This stream type didn't exist in the directory.
    BPLOG(INFO) << "GetStream: type " << stream_type << " not present";
    return NULL;
  }

  // Get a pointer so that the stored stream field can be altered.
  MinidumpStreamInfo* info = &iterator->second;

  if (info->stream) {
    // This cast is safe because info.stream is only populated by this
    // method, and there is a direct correlation between T and stream_type.
    *stream = static_cast<T*>(info->stream);
    return *stream;
  }

  uint32_t stream_length;
  if (!SeekToStreamType(stream_type, &stream_length)) {
    BPLOG(ERROR) << "GetStream could not seek to stream type " << stream_type;
    return NULL;
  }

  scoped_ptr<T> new_stream(new T(this));

  if (!new_stream->Read(stream_length)) {
    BPLOG(ERROR) << "GetStream could not read stream type " << stream_type;
    return NULL;
  }

  *stream = new_stream.release();
  info->stream = *stream;
  return *stream;
}


}  // namespace google_breakpad
