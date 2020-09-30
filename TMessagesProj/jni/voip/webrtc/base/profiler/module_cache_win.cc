// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/profiler/module_cache.h"

#include <objbase.h>
#include <psapi.h>

#include "base/process/process_handle.h"
#include "base/stl_util.h"
#include "base/strings/string_number_conversions.h"
#include "base/strings/string_util.h"
#include "base/strings/stringprintf.h"
#include "base/strings/utf_string_conversions.h"
#include "base/win/pe_image.h"
#include "base/win/scoped_handle.h"
#include "base/win/win_util.h"

namespace base {

namespace {

// Gets the unique build ID and the corresponding debug path for a module.
// Windows build IDs are created by a concatenation of a GUID and AGE fields
// found in the headers of a module. The GUID is stored in the first 16 bytes
// and the AGE is stored in the last 4 bytes. Returns the empty string if the
// function fails to get the build ID. The debug path (pdb file) can be found
// in the PE file and is the build time path where the debug file was produced.
//
// Example:
// dumpbin chrome.exe /headers | find "Format:"
//   ... Format: RSDS, {16B2A428-1DED-442E-9A36-FCE8CBD29726}, 10, ...
//
// The resulting buildID string of this instance of chrome.exe is
// "16B2A4281DED442E9A36FCE8CBD2972610".
//
// Note that the AGE field is encoded in decimal, not hex.
void GetDebugInfoForModule(HMODULE module_handle,
                           std::string* build_id,
                           FilePath* pdb_name) {
  GUID guid;
  DWORD age;
  LPCSTR pdb_file = nullptr;
  size_t pdb_file_length = 0;
  if (!win::PEImage(module_handle)
           .GetDebugId(&guid, &age, &pdb_file, &pdb_file_length)) {
    return;
  }

  FilePath::StringType pdb_filename;
  if (!UTF8ToWide(pdb_file, pdb_file_length, &pdb_filename))
    return;
  *pdb_name = FilePath(std::move(pdb_filename)).BaseName();

  auto buffer = win::String16FromGUID(guid);
  RemoveChars(buffer, STRING16_LITERAL("{}-"), &buffer);
  buffer.append(NumberToString16(age));
  *build_id = UTF16ToUTF8(buffer);
}

// Traits class to adapt GenericScopedHandle for HMODULES.
class ModuleHandleTraits : public win::HandleTraits {
 public:
  using Handle = HMODULE;

  static bool CloseHandle(HMODULE handle) { return ::FreeLibrary(handle) != 0; }
  static bool IsHandleValid(HMODULE handle) { return handle != nullptr; }
  static HMODULE NullHandle() { return nullptr; }

 private:
  DISALLOW_IMPLICIT_CONSTRUCTORS(ModuleHandleTraits);
};

// HMODULE is not really a handle, and has reference count semantics, so the
// standard VerifierTraits does not apply.
using ScopedModuleHandle =
    win::GenericScopedHandle<ModuleHandleTraits, win::DummyVerifierTraits>;

class WindowsModule : public ModuleCache::Module {
 public:
  WindowsModule(ScopedModuleHandle module_handle,
                const MODULEINFO module_info,
                const std::string& id,
                const FilePath& debug_basename)
      : module_handle_(std::move(module_handle)),
        module_info_(module_info),
        id_(id),
        debug_basename_(debug_basename) {}

  WindowsModule(const WindowsModule&) = delete;
  WindowsModule& operator=(const WindowsModule&) = delete;

  // ModuleCache::Module
  uintptr_t GetBaseAddress() const override {
    return reinterpret_cast<uintptr_t>(module_info_.lpBaseOfDll);
  }

  std::string GetId() const override { return id_; }
  FilePath GetDebugBasename() const override { return debug_basename_; }
  size_t GetSize() const override { return module_info_.SizeOfImage; }
  bool IsNative() const override { return true; }

 private:
  ScopedModuleHandle module_handle_;
  const MODULEINFO module_info_;
  std::string id_;
  FilePath debug_basename_;
};

ScopedModuleHandle GetModuleHandleForAddress(DWORD64 address) {
  HMODULE module_handle = nullptr;
  // GetModuleHandleEx() increments the module reference count, which is then
  // managed and ultimately decremented by ScopedModuleHandle.
  if (!::GetModuleHandleEx(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS,
                           reinterpret_cast<LPCTSTR>(address),
                           &module_handle)) {
    const DWORD error = ::GetLastError();
    DCHECK_EQ(ERROR_MOD_NOT_FOUND, static_cast<int>(error));
  }
  return ScopedModuleHandle(module_handle);
}

std::unique_ptr<ModuleCache::Module> CreateModuleForHandle(
    ScopedModuleHandle module_handle) {
  FilePath pdb_name;
  std::string build_id;
  GetDebugInfoForModule(module_handle.Get(), &build_id, &pdb_name);

  MODULEINFO module_info;
  if (!::GetModuleInformation(GetCurrentProcessHandle(), module_handle.Get(),
                              &module_info, sizeof(module_info))) {
    return nullptr;
  }

  return std::make_unique<WindowsModule>(std::move(module_handle), module_info,
                                         build_id, pdb_name);
}

}  // namespace

// static
std::unique_ptr<const ModuleCache::Module> ModuleCache::CreateModuleForAddress(
    uintptr_t address) {
  ScopedModuleHandle module_handle = GetModuleHandleForAddress(address);
  if (!module_handle.IsValid())
    return nullptr;
  return CreateModuleForHandle(std::move(module_handle));
}

}  // namespace base
