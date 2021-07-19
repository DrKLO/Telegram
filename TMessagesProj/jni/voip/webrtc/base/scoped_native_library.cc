// Copyright (c) 2011 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/scoped_native_library.h"

namespace base {

void NativeLibraryTraits::Free(NativeLibrary library) {
  UnloadNativeLibrary(library);
}

using BaseClass = ScopedGeneric<NativeLibrary, NativeLibraryTraits>;

ScopedNativeLibrary::ScopedNativeLibrary() : BaseClass(), error_() {}

ScopedNativeLibrary::~ScopedNativeLibrary() = default;

ScopedNativeLibrary::ScopedNativeLibrary(NativeLibrary library)
    : BaseClass(library), error_() {}

ScopedNativeLibrary::ScopedNativeLibrary(const FilePath& library_path)
    : ScopedNativeLibrary() {
  reset(LoadNativeLibrary(library_path, &error_));
}

ScopedNativeLibrary::ScopedNativeLibrary(ScopedNativeLibrary&& scoped_library)
    : BaseClass(scoped_library.release()), error_() {}

void* ScopedNativeLibrary::GetFunctionPointer(const char* function_name) const {
  if (!is_valid())
    return nullptr;
  return GetFunctionPointerFromNativeLibrary(get(), function_name);
}

const NativeLibraryLoadError* ScopedNativeLibrary::GetError() const {
  return &error_;
}

}  // namespace base
