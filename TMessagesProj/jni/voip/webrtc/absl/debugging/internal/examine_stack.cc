//
// Copyright 2018 The Abseil Authors.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

#include "absl/debugging/internal/examine_stack.h"

#ifndef _WIN32
#include <unistd.h>
#endif

#include <csignal>
#include <cstdio>

#include "absl/base/attributes.h"
#include "absl/base/internal/raw_logging.h"
#include "absl/base/macros.h"
#include "absl/debugging/stacktrace.h"
#include "absl/debugging/symbolize.h"

namespace absl {
ABSL_NAMESPACE_BEGIN
namespace debugging_internal {

// Returns the program counter from signal context, nullptr if
// unknown. vuc is a ucontext_t*. We use void* to avoid the use of
// ucontext_t on non-POSIX systems.
void* GetProgramCounter(void* vuc) {
#ifdef __linux__
  if (vuc != nullptr) {
    ucontext_t* context = reinterpret_cast<ucontext_t*>(vuc);
#if defined(__aarch64__)
    return reinterpret_cast<void*>(context->uc_mcontext.pc);
#elif defined(__arm__)
    return reinterpret_cast<void*>(context->uc_mcontext.arm_pc);
#elif defined(__i386__)
    if (14 < ABSL_ARRAYSIZE(context->uc_mcontext.gregs))
      return reinterpret_cast<void*>(context->uc_mcontext.gregs[14]);
#elif defined(__mips__)
    return reinterpret_cast<void*>(context->uc_mcontext.pc);
#elif defined(__powerpc64__)
    return reinterpret_cast<void*>(context->uc_mcontext.gp_regs[32]);
#elif defined(__powerpc__)
    return reinterpret_cast<void*>(context->uc_mcontext.regs->nip);
#elif defined(__riscv)
    return reinterpret_cast<void*>(context->uc_mcontext.__gregs[REG_PC]);
#elif defined(__s390__) && !defined(__s390x__)
    return reinterpret_cast<void*>(context->uc_mcontext.psw.addr & 0x7fffffff);
#elif defined(__s390__) && defined(__s390x__)
    return reinterpret_cast<void*>(context->uc_mcontext.psw.addr);
#elif defined(__x86_64__)
    if (16 < ABSL_ARRAYSIZE(context->uc_mcontext.gregs))
      return reinterpret_cast<void*>(context->uc_mcontext.gregs[16]);
#else
#error "Undefined Architecture."
#endif
  }
#elif defined(__akaros__)
  auto* ctx = reinterpret_cast<struct user_context*>(vuc);
  return reinterpret_cast<void*>(get_user_ctx_pc(ctx));
#endif
  static_cast<void>(vuc);
  return nullptr;
}

// The %p field width for printf() functions is two characters per byte,
// and two extra for the leading "0x".
static constexpr int kPrintfPointerFieldWidth = 2 + 2 * sizeof(void*);

// Print a program counter, its stack frame size, and its symbol name.
// Note that there is a separate symbolize_pc argument. Return addresses may be
// at the end of the function, and this allows the caller to back up from pc if
// appropriate.
static void DumpPCAndFrameSizeAndSymbol(void (*writerfn)(const char*, void*),
                                        void* writerfn_arg, void* pc,
                                        void* symbolize_pc, int framesize,
                                        const char* const prefix) {
  char tmp[1024];
  const char* symbol = "(unknown)";
  if (absl::Symbolize(symbolize_pc, tmp, sizeof(tmp))) {
    symbol = tmp;
  }
  char buf[1024];
  if (framesize <= 0) {
    snprintf(buf, sizeof(buf), "%s@ %*p  (unknown)  %s\n", prefix,
             kPrintfPointerFieldWidth, pc, symbol);
  } else {
    snprintf(buf, sizeof(buf), "%s@ %*p  %9d  %s\n", prefix,
             kPrintfPointerFieldWidth, pc, framesize, symbol);
  }
  writerfn(buf, writerfn_arg);
}

// Print a program counter and the corresponding stack frame size.
static void DumpPCAndFrameSize(void (*writerfn)(const char*, void*),
                               void* writerfn_arg, void* pc, int framesize,
                               const char* const prefix) {
  char buf[100];
  if (framesize <= 0) {
    snprintf(buf, sizeof(buf), "%s@ %*p  (unknown)\n", prefix,
             kPrintfPointerFieldWidth, pc);
  } else {
    snprintf(buf, sizeof(buf), "%s@ %*p  %9d\n", prefix,
             kPrintfPointerFieldWidth, pc, framesize);
  }
  writerfn(buf, writerfn_arg);
}

void DumpPCAndFrameSizesAndStackTrace(
    void* pc, void* const stack[], int frame_sizes[], int depth,
    int min_dropped_frames, bool symbolize_stacktrace,
    void (*writerfn)(const char*, void*), void* writerfn_arg) {
  if (pc != nullptr) {
    // We don't know the stack frame size for PC, use 0.
    if (symbolize_stacktrace) {
      DumpPCAndFrameSizeAndSymbol(writerfn, writerfn_arg, pc, pc, 0, "PC: ");
    } else {
      DumpPCAndFrameSize(writerfn, writerfn_arg, pc, 0, "PC: ");
    }
  }
  for (int i = 0; i < depth; i++) {
    if (symbolize_stacktrace) {
      // Pass the previous address of pc as the symbol address because pc is a
      // return address, and an overrun may occur when the function ends with a
      // call to a function annotated noreturn (e.g. CHECK). Note that we don't
      // do this for pc above, as the adjustment is only correct for return
      // addresses.
      DumpPCAndFrameSizeAndSymbol(writerfn, writerfn_arg, stack[i],
                                  reinterpret_cast<char*>(stack[i]) - 1,
                                  frame_sizes[i], "    ");
    } else {
      DumpPCAndFrameSize(writerfn, writerfn_arg, stack[i], frame_sizes[i],
                         "    ");
    }
  }
  if (min_dropped_frames > 0) {
    char buf[100];
    snprintf(buf, sizeof(buf), "    @ ... and at least %d more frames\n",
             min_dropped_frames);
    writerfn(buf, writerfn_arg);
  }
}

}  // namespace debugging_internal
ABSL_NAMESPACE_END
}  // namespace absl
