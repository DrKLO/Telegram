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

// exploitability_win.cc: Windows specific exploitability engine.
//
// Provides a guess at the exploitability of the crash for the Windows
// platform given a minidump and process_state.
//
// Author: Cris Neckar

#include <vector>

#include "processor/exploitability_win.h"

#include "common/scoped_ptr.h"
#include "google_breakpad/common/minidump_exception_win32.h"
#include "google_breakpad/processor/minidump.h"
#include "processor/disassembler_x86.h"
#include "processor/logging.h"

#include "third_party/libdisasm/libdis.h"

namespace google_breakpad {

// The cutoff that we use to judge if and address is likely an offset
// from various interesting addresses.
static const uint64_t kProbableNullOffset = 4096;
static const uint64_t kProbableStackOffset = 8192;

// The various cutoffs for the different ratings.
static const size_t kHighCutoff        = 100;
static const size_t kMediumCutoff      = 80;
static const size_t kLowCutoff         = 50;
static const size_t kInterestingCutoff = 25;

// Predefined incremental values for conditional weighting.
static const size_t kTinyBump          = 5;
static const size_t kSmallBump         = 20;
static const size_t kMediumBump        = 50;
static const size_t kLargeBump         = 70;
static const size_t kHugeBump          = 90;

// The maximum number of bytes to disassemble past the program counter.
static const size_t kDisassembleBytesBeyondPC = 2048;

ExploitabilityWin::ExploitabilityWin(Minidump *dump,
                                     ProcessState *process_state)
    : Exploitability(dump, process_state) { }

ExploitabilityRating ExploitabilityWin::CheckPlatformExploitability() {
  MinidumpException *exception = dump_->GetException();
  if (!exception) {
    BPLOG(INFO) << "Minidump does not have exception record.";
    return EXPLOITABILITY_ERR_PROCESSING;
  }

  const MDRawExceptionStream *raw_exception = exception->exception();
  if (!raw_exception) {
    BPLOG(INFO) << "Could not obtain raw exception info.";
    return EXPLOITABILITY_ERR_PROCESSING;
  }

  const MinidumpContext *context = exception->GetContext();
  if (!context) {
    BPLOG(INFO) << "Could not obtain exception context.";
    return EXPLOITABILITY_ERR_PROCESSING;
  }

  MinidumpMemoryList *memory_list = dump_->GetMemoryList();
  bool memory_available = true;
  if (!memory_list) {
    BPLOG(INFO) << "Minidump memory segments not available.";
    memory_available = false;
  }
  uint64_t address = process_state_->crash_address();
  uint32_t exception_code = raw_exception->exception_record.exception_code;

  uint32_t exploitability_weight = 0;

  uint64_t stack_ptr = 0;
  uint64_t instruction_ptr = 0;

  switch (context->GetContextCPU()) {
    case MD_CONTEXT_X86:
      stack_ptr = context->GetContextX86()->esp;
      instruction_ptr = context->GetContextX86()->eip;
      break;
    case MD_CONTEXT_AMD64:
      stack_ptr = context->GetContextAMD64()->rsp;
      instruction_ptr = context->GetContextAMD64()->rip;
      break;
    default:
      BPLOG(INFO) << "Unsupported architecture.";
      return EXPLOITABILITY_ERR_PROCESSING;
  }

  // Check if we are executing on the stack.
  if (instruction_ptr <= (stack_ptr + kProbableStackOffset) &&
      instruction_ptr >= (stack_ptr - kProbableStackOffset))
    exploitability_weight += kHugeBump;

  switch (exception_code) {
    // This is almost certainly recursion.
    case MD_EXCEPTION_CODE_WIN_STACK_OVERFLOW:
      exploitability_weight += kTinyBump;
      break;

    // These exceptions tend to be benign and we can generally ignore them.
    case MD_EXCEPTION_CODE_WIN_INTEGER_DIVIDE_BY_ZERO:
    case MD_EXCEPTION_CODE_WIN_INTEGER_OVERFLOW:
    case MD_EXCEPTION_CODE_WIN_FLOAT_DIVIDE_BY_ZERO:
    case MD_EXCEPTION_CODE_WIN_FLOAT_INEXACT_RESULT:
    case MD_EXCEPTION_CODE_WIN_FLOAT_OVERFLOW:
    case MD_EXCEPTION_CODE_WIN_FLOAT_UNDERFLOW:
    case MD_EXCEPTION_CODE_WIN_IN_PAGE_ERROR:
      exploitability_weight += kTinyBump;
      break;

    // These exceptions will typically mean that we have jumped where we
    // shouldn't.
    case MD_EXCEPTION_CODE_WIN_ILLEGAL_INSTRUCTION:
    case MD_EXCEPTION_CODE_WIN_FLOAT_INVALID_OPERATION:
    case MD_EXCEPTION_CODE_WIN_PRIVILEGED_INSTRUCTION:
      exploitability_weight += kLargeBump;
      break;

    // These represent bugs in exception handlers.
    case MD_EXCEPTION_CODE_WIN_INVALID_DISPOSITION:
    case MD_EXCEPTION_CODE_WIN_NONCONTINUABLE_EXCEPTION:
      exploitability_weight += kSmallBump;
      break;

    case MD_EXCEPTION_CODE_WIN_HEAP_CORRUPTION:
    case MD_EXCEPTION_CODE_WIN_STACK_BUFFER_OVERRUN:
      exploitability_weight += kHugeBump;
      break;

    case MD_EXCEPTION_CODE_WIN_GUARD_PAGE_VIOLATION:
      exploitability_weight += kLargeBump;
      break;

    case MD_EXCEPTION_CODE_WIN_ACCESS_VIOLATION:
      bool near_null = (address <= kProbableNullOffset);
      bool bad_read = false;
      bool bad_write = false;
      if (raw_exception->exception_record.number_parameters >= 1) {
        MDAccessViolationTypeWin av_type =
            static_cast<MDAccessViolationTypeWin>
            (raw_exception->exception_record.exception_information[0]);
        switch (av_type) {
          case MD_ACCESS_VIOLATION_WIN_READ:
            bad_read = true;
            if (near_null)
              exploitability_weight += kSmallBump;
            else
              exploitability_weight += kMediumBump;
            break;
          case MD_ACCESS_VIOLATION_WIN_WRITE:
            bad_write = true;
            if (near_null)
              exploitability_weight += kSmallBump;
            else
              exploitability_weight += kHugeBump;
            break;
          case MD_ACCESS_VIOLATION_WIN_EXEC:
            if (near_null)
              exploitability_weight += kSmallBump;
            else
              exploitability_weight += kHugeBump;
            break;
          default:
            BPLOG(INFO) << "Unrecognized access violation type.";
            return EXPLOITABILITY_ERR_PROCESSING;
            break;
        }
        MinidumpMemoryRegion *instruction_region = 0;
        if (memory_available) {
          instruction_region =
              memory_list->GetMemoryRegionForAddress(instruction_ptr);
        }
        if (!near_null && instruction_region &&
            context->GetContextCPU() == MD_CONTEXT_X86 &&
            (bad_read || bad_write)) {
          // Perform checks related to memory around instruction pointer.
          uint32_t memory_offset =
              instruction_ptr - instruction_region->GetBase();
          uint32_t available_memory =
              instruction_region->GetSize() - memory_offset;
          available_memory = available_memory > kDisassembleBytesBeyondPC ?
              kDisassembleBytesBeyondPC : available_memory;
          if (available_memory) {
            const uint8_t *raw_memory =
                instruction_region->GetMemory() + memory_offset;
            DisassemblerX86 disassembler(raw_memory,
                                         available_memory,
                                         instruction_ptr);
            disassembler.NextInstruction();
            if (bad_read)
              disassembler.setBadRead();
            else
              disassembler.setBadWrite();
            if (disassembler.currentInstructionValid()) {
              // Check if the faulting instruction falls into one of
              // several interesting groups.
              switch (disassembler.currentInstructionGroup()) {
                case libdis::insn_controlflow:
                  exploitability_weight += kLargeBump;
                  break;
                case libdis::insn_string:
                  exploitability_weight += kHugeBump;
                  break;
                default:
                  break;
              }
              // Loop the disassembler through the code and check if it
              // IDed any interesting conditions in the near future.
              // Multiple flags may be set so treat each equally.
              while (disassembler.NextInstruction() &&
                     disassembler.currentInstructionValid() &&
                     !disassembler.endOfBlock())
                continue;
              if (disassembler.flags() & DISX86_BAD_BRANCH_TARGET)
                exploitability_weight += kLargeBump;
              if (disassembler.flags() & DISX86_BAD_ARGUMENT_PASSED)
                exploitability_weight += kTinyBump;
              if (disassembler.flags() & DISX86_BAD_WRITE)
                exploitability_weight += kMediumBump;
              if (disassembler.flags() & DISX86_BAD_BLOCK_WRITE)
                exploitability_weight += kMediumBump;
              if (disassembler.flags() & DISX86_BAD_READ)
                exploitability_weight += kTinyBump;
              if (disassembler.flags() & DISX86_BAD_BLOCK_READ)
                exploitability_weight += kTinyBump;
              if (disassembler.flags() & DISX86_BAD_COMPARISON)
                exploitability_weight += kTinyBump;
            }
          }
        }
        if (!near_null && AddressIsAscii(address))
          exploitability_weight += kMediumBump;
      } else {
        BPLOG(INFO) << "Access violation type parameter missing.";
        return EXPLOITABILITY_ERR_PROCESSING;
      }
  }

  // Based on the calculated weight we return a simplified classification.
  BPLOG(INFO) << "Calculated exploitability weight: " << exploitability_weight;
  if (exploitability_weight >= kHighCutoff)
    return EXPLOITABILITY_HIGH;
  if (exploitability_weight >= kMediumCutoff)
    return EXPLOITABLITY_MEDIUM;
  if (exploitability_weight >= kLowCutoff)
    return EXPLOITABILITY_LOW;
  if (exploitability_weight >= kInterestingCutoff)
    return EXPLOITABILITY_INTERESTING;

  return EXPLOITABILITY_NONE;
}

}  // namespace google_breakpad
