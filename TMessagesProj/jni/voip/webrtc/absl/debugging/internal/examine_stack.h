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

#ifndef ABSL_DEBUGGING_INTERNAL_EXAMINE_STACK_H_
#define ABSL_DEBUGGING_INTERNAL_EXAMINE_STACK_H_

#include "absl/base/config.h"

namespace absl {
ABSL_NAMESPACE_BEGIN
namespace debugging_internal {

// Returns the program counter from signal context, or nullptr if
// unknown. `vuc` is a ucontext_t*. We use void* to avoid the use of
// ucontext_t on non-POSIX systems.
void* GetProgramCounter(void* vuc);

// Uses `writerfn` to dump the program counter, stack trace, and stack
// frame sizes.
void DumpPCAndFrameSizesAndStackTrace(
    void* pc, void* const stack[], int frame_sizes[], int depth,
    int min_dropped_frames, bool symbolize_stacktrace,
    void (*writerfn)(const char*, void*), void* writerfn_arg);

}  // namespace debugging_internal
ABSL_NAMESPACE_END
}  // namespace absl

#endif  // ABSL_DEBUGGING_INTERNAL_EXAMINE_STACK_H_
