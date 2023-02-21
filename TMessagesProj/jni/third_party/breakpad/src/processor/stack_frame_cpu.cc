// Copyright 2013 Google Inc.
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

// stack_frame_cpu.h: CPU-specific StackFrame extensions.
//
// See google_breakpad/processor/stack_frame_cpu.h for documentation.
//
// Author: Colin Blundell

#include "google_breakpad/processor/stack_frame_cpu.h"

namespace google_breakpad {

const uint64_t StackFrameARM64::CONTEXT_VALID_X0;
const uint64_t StackFrameARM64::CONTEXT_VALID_X1;
const uint64_t StackFrameARM64::CONTEXT_VALID_X2;
const uint64_t StackFrameARM64::CONTEXT_VALID_X3;
const uint64_t StackFrameARM64::CONTEXT_VALID_X4;
const uint64_t StackFrameARM64::CONTEXT_VALID_X5;
const uint64_t StackFrameARM64::CONTEXT_VALID_X6;
const uint64_t StackFrameARM64::CONTEXT_VALID_X7;
const uint64_t StackFrameARM64::CONTEXT_VALID_X8;
const uint64_t StackFrameARM64::CONTEXT_VALID_X9;
const uint64_t StackFrameARM64::CONTEXT_VALID_X10;
const uint64_t StackFrameARM64::CONTEXT_VALID_X11;
const uint64_t StackFrameARM64::CONTEXT_VALID_X12;
const uint64_t StackFrameARM64::CONTEXT_VALID_X13;
const uint64_t StackFrameARM64::CONTEXT_VALID_X14;
const uint64_t StackFrameARM64::CONTEXT_VALID_X15;
const uint64_t StackFrameARM64::CONTEXT_VALID_X16;
const uint64_t StackFrameARM64::CONTEXT_VALID_X17;
const uint64_t StackFrameARM64::CONTEXT_VALID_X18;
const uint64_t StackFrameARM64::CONTEXT_VALID_X19;
const uint64_t StackFrameARM64::CONTEXT_VALID_X20;
const uint64_t StackFrameARM64::CONTEXT_VALID_X21;
const uint64_t StackFrameARM64::CONTEXT_VALID_X22;
const uint64_t StackFrameARM64::CONTEXT_VALID_X23;
const uint64_t StackFrameARM64::CONTEXT_VALID_X24;
const uint64_t StackFrameARM64::CONTEXT_VALID_X25;
const uint64_t StackFrameARM64::CONTEXT_VALID_X26;
const uint64_t StackFrameARM64::CONTEXT_VALID_X27;
const uint64_t StackFrameARM64::CONTEXT_VALID_X28;
const uint64_t StackFrameARM64::CONTEXT_VALID_X29;
const uint64_t StackFrameARM64::CONTEXT_VALID_X30;
const uint64_t StackFrameARM64::CONTEXT_VALID_X31;
const uint64_t StackFrameARM64::CONTEXT_VALID_X32;
const uint64_t StackFrameARM64::CONTEXT_VALID_FP;
const uint64_t StackFrameARM64::CONTEXT_VALID_LR;
const uint64_t StackFrameARM64::CONTEXT_VALID_SP;
const uint64_t StackFrameARM64::CONTEXT_VALID_PC;
const uint64_t StackFrameARM64::CONTEXT_VALID_ALL;

}  // namespace google_breakpad
