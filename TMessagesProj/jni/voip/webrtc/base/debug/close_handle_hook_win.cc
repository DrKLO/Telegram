// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/debug/close_handle_hook_win.h"

#include <Windows.h>
#include <psapi.h>
#include <stddef.h>

#include <algorithm>
#include <memory>
#include <vector>

#include "base/macros.h"
#include "base/win/iat_patch_function.h"
#include "base/win/pe_image.h"
#include "base/win/scoped_handle.h"
#include "build/build_config.h"

namespace {

typedef BOOL (WINAPI* CloseHandleType) (HANDLE handle);

typedef BOOL (WINAPI* DuplicateHandleType)(HANDLE source_process,
                                           HANDLE source_handle,
                                           HANDLE target_process,
                                           HANDLE* target_handle,
                                           DWORD desired_access,
                                           BOOL inherit_handle,
                                           DWORD options);

CloseHandleType g_close_function = NULL;
DuplicateHandleType g_duplicate_function = NULL;

// The entry point for CloseHandle interception. This function notifies the
// verifier about the handle that is being closed, and calls the original
// function.
BOOL WINAPI CloseHandleHook(HANDLE handle) {
  base::win::OnHandleBeingClosed(handle);
  return g_close_function(handle);
}

BOOL WINAPI DuplicateHandleHook(HANDLE source_process,
                                HANDLE source_handle,
                                HANDLE target_process,
                                HANDLE* target_handle,
                                DWORD desired_access,
                                BOOL inherit_handle,
                                DWORD options) {
  if ((options & DUPLICATE_CLOSE_SOURCE) &&
      (GetProcessId(source_process) == ::GetCurrentProcessId())) {
    base::win::OnHandleBeingClosed(source_handle);
  }

  return g_duplicate_function(source_process, source_handle, target_process,
                              target_handle, desired_access, inherit_handle,
                              options);
}

}  // namespace

namespace base {
namespace debug {

namespace {

// Provides a simple way to temporarily change the protection of a memory page.
class AutoProtectMemory {
 public:
  AutoProtectMemory()
      : changed_(false), address_(NULL), bytes_(0), old_protect_(0) {}

  ~AutoProtectMemory() {
    RevertProtection();
  }

  // Grants write access to a given memory range.
  bool ChangeProtection(void* address, size_t bytes);

  // Restores the original page protection.
  void RevertProtection();

 private:
  bool changed_;
  void* address_;
  size_t bytes_;
  DWORD old_protect_;

  DISALLOW_COPY_AND_ASSIGN(AutoProtectMemory);
};

bool AutoProtectMemory::ChangeProtection(void* address, size_t bytes) {
  DCHECK(!changed_);
  DCHECK(address);

  // Change the page protection so that we can write.
  MEMORY_BASIC_INFORMATION memory_info;
  if (!VirtualQuery(address, &memory_info, sizeof(memory_info)))
    return false;

  DWORD is_executable = (PAGE_EXECUTE | PAGE_EXECUTE_READ |
                        PAGE_EXECUTE_READWRITE | PAGE_EXECUTE_WRITECOPY) &
                        memory_info.Protect;

  DWORD protect = is_executable ? PAGE_EXECUTE_READWRITE : PAGE_READWRITE;
  if (!VirtualProtect(address, bytes, protect, &old_protect_))
    return false;

  changed_ = true;
  address_ = address;
  bytes_ = bytes;
  return true;
}

void AutoProtectMemory::RevertProtection() {
  if (!changed_)
    return;

  DCHECK(address_);
  DCHECK(bytes_);

  VirtualProtect(address_, bytes_, old_protect_, &old_protect_);
  changed_ = false;
  address_ = NULL;
  bytes_ = 0;
  old_protect_ = 0;
}

// Performs an EAT interception.
void EATPatch(HMODULE module, const char* function_name,
              void* new_function, void** old_function) {
  if (!module)
    return;

  base::win::PEImage pe(module);
  if (!pe.VerifyMagic())
    return;

  DWORD* eat_entry = pe.GetExportEntry(function_name);
  if (!eat_entry)
    return;

  if (!(*old_function))
    *old_function = pe.RVAToAddr(*eat_entry);

  AutoProtectMemory memory;
  if (!memory.ChangeProtection(eat_entry, sizeof(DWORD)))
    return;

  // Perform the patch.
  *eat_entry = static_cast<DWORD>(reinterpret_cast<uintptr_t>(new_function) -
                                  reinterpret_cast<uintptr_t>(module));
}

// Performs an IAT interception.
base::win::IATPatchFunction* IATPatch(HMODULE module, const char* function_name,
                                      void* new_function, void** old_function) {
  if (!module)
    return NULL;

  base::win::IATPatchFunction* patch = new base::win::IATPatchFunction;
  __try {
    // There is no guarantee that |module| is still loaded at this point.
    if (patch->PatchFromModule(module, "kernel32.dll", function_name,
                               new_function)) {
      delete patch;
      return NULL;
    }
  } __except((GetExceptionCode() == EXCEPTION_ACCESS_VIOLATION ||
              GetExceptionCode() == EXCEPTION_GUARD_PAGE ||
              GetExceptionCode() == EXCEPTION_IN_PAGE_ERROR) ?
             EXCEPTION_EXECUTE_HANDLER : EXCEPTION_CONTINUE_SEARCH) {
    // Leak the patch.
    return NULL;
  }

  if (!(*old_function)) {
    // Things are probably messed up if each intercepted function points to
    // a different place, but we need only one function to call.
    *old_function = patch->original_function();
  }
  return patch;
}

// Keeps track of all the hooks needed to intercept functions which could
// possibly close handles.
class HandleHooks {
 public:
  HandleHooks() {}
  ~HandleHooks() {}

  void AddIATPatch(HMODULE module);
  void AddEATPatch();

 private:
  std::vector<base::win::IATPatchFunction*> hooks_;
  DISALLOW_COPY_AND_ASSIGN(HandleHooks);
};

void HandleHooks::AddIATPatch(HMODULE module) {
  if (!module)
    return;

  base::win::IATPatchFunction* patch = NULL;
  patch =
      IATPatch(module, "CloseHandle", reinterpret_cast<void*>(&CloseHandleHook),
               reinterpret_cast<void**>(&g_close_function));
  if (!patch)
    return;
  hooks_.push_back(patch);

  patch = IATPatch(module, "DuplicateHandle",
                   reinterpret_cast<void*>(&DuplicateHandleHook),
                   reinterpret_cast<void**>(&g_duplicate_function));
  if (!patch)
    return;
  hooks_.push_back(patch);
}

void HandleHooks::AddEATPatch() {
  // An attempt to restore the entry on the table at destruction is not safe.
  EATPatch(GetModuleHandleA("kernel32.dll"), "CloseHandle",
           reinterpret_cast<void*>(&CloseHandleHook),
           reinterpret_cast<void**>(&g_close_function));
  EATPatch(GetModuleHandleA("kernel32.dll"), "DuplicateHandle",
           reinterpret_cast<void*>(&DuplicateHandleHook),
           reinterpret_cast<void**>(&g_duplicate_function));
}

void PatchLoadedModules(HandleHooks* hooks) {
  const DWORD kSize = 256;
  DWORD returned;
  std::unique_ptr<HMODULE[]> modules(new HMODULE[kSize]);
  if (!EnumProcessModules(GetCurrentProcess(), modules.get(),
                          kSize * sizeof(HMODULE), &returned)) {
    return;
  }
  returned /= sizeof(HMODULE);
  returned = std::min(kSize, returned);

  for (DWORD current = 0; current < returned; current++) {
    hooks->AddIATPatch(modules[current]);
  }
}

}  // namespace

void InstallHandleHooks() {
  static HandleHooks* hooks = new HandleHooks();

  // Performing EAT interception first is safer in the presence of other
  // threads attempting to call CloseHandle.
  hooks->AddEATPatch();
  PatchLoadedModules(hooks);
}

}  // namespace debug
}  // namespace base
