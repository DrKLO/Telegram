// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/debug/profiler.h"

#include <string>

#include "base/allocator/buildflags.h"
#include "base/debug/debugging_buildflags.h"
#include "base/process/process_handle.h"
#include "base/strings/string_number_conversions.h"
#include "base/strings/string_util.h"
#include "build/build_config.h"

#if defined(OS_WIN)
#include "base/win/current_module.h"
#include "base/win/pe_image.h"
#endif  // defined(OS_WIN)

// TODO(peria): Enable profiling on Windows.
#if BUILDFLAG(ENABLE_PROFILING) && BUILDFLAG(USE_TCMALLOC) && !defined(OS_WIN)
#include "third_party/tcmalloc/chromium/src/gperftools/profiler.h"
#endif

namespace base {
namespace debug {

// TODO(peria): Enable profiling on Windows.
#if BUILDFLAG(ENABLE_PROFILING) && BUILDFLAG(USE_TCMALLOC) && !defined(OS_WIN)

static int profile_count = 0;

void StartProfiling(const std::string& name) {
  ++profile_count;
  std::string full_name(name);
  std::string pid = NumberToString(GetCurrentProcId());
  std::string count = NumberToString(profile_count);
  ReplaceSubstringsAfterOffset(&full_name, 0, "{pid}", pid);
  ReplaceSubstringsAfterOffset(&full_name, 0, "{count}", count);
  ProfilerStart(full_name.c_str());
}

void StopProfiling() {
  ProfilerFlush();
  ProfilerStop();
}

void FlushProfiling() {
  ProfilerFlush();
}

bool BeingProfiled() {
  return ProfilingIsEnabledForAllThreads();
}

void RestartProfilingAfterFork() {
  ProfilerRegisterThread();
}

bool IsProfilingSupported() {
  return true;
}

#else

void StartProfiling(const std::string& name) {
}

void StopProfiling() {
}

void FlushProfiling() {
}

bool BeingProfiled() {
  return false;
}

void RestartProfilingAfterFork() {
}

bool IsProfilingSupported() {
  return false;
}

#endif

#if !defined(OS_WIN)

ReturnAddressLocationResolver GetProfilerReturnAddrResolutionFunc() {
  return nullptr;
}

AddDynamicSymbol GetProfilerAddDynamicSymbolFunc() {
  return nullptr;
}

MoveDynamicSymbol GetProfilerMoveDynamicSymbolFunc() {
  return nullptr;
}

#else  // defined(OS_WIN)

namespace {

struct FunctionSearchContext {
  const char* name;
  FARPROC function;
};

// Callback function to PEImage::EnumImportChunks.
bool FindResolutionFunctionInImports(
    const base::win::PEImage &image, const char* module_name,
    PIMAGE_THUNK_DATA unused_name_table, PIMAGE_THUNK_DATA import_address_table,
    PVOID cookie) {
  FunctionSearchContext* context =
      reinterpret_cast<FunctionSearchContext*>(cookie);

  DCHECK(context);
  DCHECK(!context->function);

  // Our import address table contains pointers to the functions we import
  // at this point. Let's retrieve the first such function and use it to
  // find the module this import was resolved to by the loader.
  const wchar_t* function_in_module =
      reinterpret_cast<const wchar_t*>(import_address_table->u1.Function);

  // Retrieve the module by a function in the module.
  const DWORD kFlags = GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS |
                       GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT;
  HMODULE module = NULL;
  if (!::GetModuleHandleEx(kFlags, function_in_module, &module)) {
    // This can happen if someone IAT patches us to a thunk.
    return true;
  }

  // See whether this module exports the function we're looking for.
  FARPROC exported_func = ::GetProcAddress(module, context->name);
  if (exported_func != NULL) {
    // We found it, return the function and terminate the enumeration.
    context->function = exported_func;
    return false;
  }

  // Keep going.
  return true;
}

template <typename FunctionType>
FunctionType FindFunctionInImports(const char* function_name) {
  base::win::PEImage image(CURRENT_MODULE());

  FunctionSearchContext ctx = { function_name, NULL };
  image.EnumImportChunks(FindResolutionFunctionInImports, &ctx, nullptr);

  return reinterpret_cast<FunctionType>(ctx.function);
}

}  // namespace

ReturnAddressLocationResolver GetProfilerReturnAddrResolutionFunc() {
  return FindFunctionInImports<ReturnAddressLocationResolver>(
      "ResolveReturnAddressLocation");
}

AddDynamicSymbol GetProfilerAddDynamicSymbolFunc() {
  return FindFunctionInImports<AddDynamicSymbol>(
      "AddDynamicSymbol");
}

MoveDynamicSymbol GetProfilerMoveDynamicSymbolFunc() {
  return FindFunctionInImports<MoveDynamicSymbol>(
      "MoveDynamicSymbol");
}

#endif  // defined(OS_WIN)

}  // namespace debug
}  // namespace base
