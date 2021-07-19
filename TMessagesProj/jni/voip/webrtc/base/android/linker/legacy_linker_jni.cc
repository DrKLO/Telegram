// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

// This is the version of the Android-specific Chromium linker that uses
// the crazy linker to load libraries.

// This source code *cannot* depend on anything from base/ or the C++
// STL, to keep the final library small, and avoid ugly dependency issues.

#include "base/android/linker/legacy_linker_jni.h"

#include <crazy_linker.h>
#include <fcntl.h>
#include <jni.h>
#include <limits.h>
#include <stddef.h>
#include <stdlib.h>
#include <unistd.h>

#include "base/android/linker/linker_jni.h"

namespace chromium_android_linker {
namespace {

// The linker uses a single crazy_context_t object created on demand.
// There is no need to protect this against concurrent access, locking
// is already handled on the Java side.
crazy_context_t* GetCrazyContext() {
  static crazy_context_t* s_crazy_context = nullptr;

  if (!s_crazy_context) {
    // Create new context.
    s_crazy_context = crazy_context_create();

    // Ensure libraries located in the same directory as the linker
    // can be loaded before system ones.
    crazy_add_search_path_for_address(
        reinterpret_cast<void*>(&GetCrazyContext));
  }

  return s_crazy_context;
}

// A scoped crazy_library_t that automatically closes the handle
// on scope exit, unless Release() has been called.
class ScopedLibrary {
 public:
  ScopedLibrary() : lib_(nullptr) {}

  ~ScopedLibrary() {
    if (lib_)
      crazy_library_close_with_context(lib_, GetCrazyContext());
  }

  crazy_library_t* Get() { return lib_; }

  crazy_library_t** GetPtr() { return &lib_; }

  crazy_library_t* Release() {
    crazy_library_t* ret = lib_;
    lib_ = nullptr;
    return ret;
  }

 private:
  crazy_library_t* lib_;
};

// Add a zip archive file path to the context's current search path
// list. Making it possible to load libraries directly from it.
JNI_GENERATOR_EXPORT bool
Java_org_chromium_base_library_1loader_LegacyLinker_nativeAddZipArchivePath(
    JNIEnv* env,
    jclass clazz,
    jstring apk_path_obj) {
  String apk_path(env, apk_path_obj);

  char search_path[512];
  snprintf(search_path, sizeof(search_path), "%s!lib/" CURRENT_ABI "/",
           apk_path.c_str());

  crazy_add_search_path(search_path);
  return true;
}

// Load a library with the chromium linker. This will also call its
// JNI_OnLoad() method, which shall register its methods. Note that
// lazy native method resolution will _not_ work after this, because
// Dalvik uses the system's dlsym() which won't see the new library,
// so explicit registration is mandatory.
//
// |env| is the current JNI environment handle.
// |clazz| is the static class handle for org.chromium.base.Linker,
// and is ignored here.
// |library_name| is the library name (e.g. libfoo.so).
// |load_address| is an explicit load address.
// |lib_info_obj| is a LibInfo handle used to communicate information
// with the Java side.
// Return true on success.
JNI_GENERATOR_EXPORT bool
Java_org_chromium_base_library_1loader_LegacyLinker_nativeLoadLibrary(
    JNIEnv* env,
    jclass clazz,
    jstring lib_name_obj,
    jlong load_address,
    jobject lib_info_obj) {
  String library_name(env, lib_name_obj);
  LOG_INFO("Called for %s, at address 0x%llx", library_name.c_str(),
           static_cast<unsigned long long>(load_address));
  crazy_context_t* context = GetCrazyContext();

  if (!IsValidAddress(load_address)) {
    LOG_ERROR("Invalid address 0x%llx",
              static_cast<unsigned long long>(load_address));
    return false;
  }

  // Set the desired load address (0 means randomize it).
  crazy_context_set_load_address(context, static_cast<size_t>(load_address));

  ScopedLibrary library;
  if (!crazy_library_open(library.GetPtr(), library_name.c_str(), context)) {
    return false;
  }

  crazy_library_info_t info;
  if (!crazy_library_get_info(library.Get(), context, &info)) {
    LOG_ERROR("Could not get library information for %s: %s",
              library_name.c_str(), crazy_context_get_error(context));
    return false;
  }

  // Release library object to keep it alive after the function returns.
  library.Release();

  s_lib_info_fields.SetLoadInfo(env, lib_info_obj, info.load_address,
                                info.load_size);
  LOG_INFO("Success loading library %s", library_name.c_str());
  return true;
}

JNI_GENERATOR_EXPORT jboolean
Java_org_chromium_base_library_1loader_LegacyLinker_nativeCreateSharedRelro(
    JNIEnv* env,
    jclass clazz,
    jstring library_name,
    jlong load_address,
    jobject lib_info_obj) {
  String lib_name(env, library_name);

  LOG_INFO("Called for %s", lib_name.c_str());

  if (!IsValidAddress(load_address)) {
    LOG_ERROR("Invalid address 0x%llx",
              static_cast<unsigned long long>(load_address));
    return false;
  }

  ScopedLibrary library;
  if (!crazy_library_find_by_name(lib_name.c_str(), library.GetPtr())) {
    LOG_ERROR("Could not find %s", lib_name.c_str());
    return false;
  }

  crazy_context_t* context = GetCrazyContext();
  size_t relro_start = 0;
  size_t relro_size = 0;
  int relro_fd = -1;

  if (!crazy_library_create_shared_relro(
          library.Get(), context, static_cast<size_t>(load_address),
          &relro_start, &relro_size, &relro_fd)) {
    LOG_ERROR("Could not create shared RELRO sharing for %s: %s\n",
              lib_name.c_str(), crazy_context_get_error(context));
    return false;
  }

  s_lib_info_fields.SetRelroInfo(env, lib_info_obj, relro_start, relro_size,
                                 relro_fd);
  return true;
}

JNI_GENERATOR_EXPORT jboolean
Java_org_chromium_base_library_1loader_LegacyLinker_nativeUseSharedRelro(
    JNIEnv* env,
    jclass clazz,
    jstring library_name,
    jobject lib_info_obj) {
  String lib_name(env, library_name);

  LOG_INFO("Called for %s, lib_info_ref=%p", lib_name.c_str(), lib_info_obj);

  ScopedLibrary library;
  if (!crazy_library_find_by_name(lib_name.c_str(), library.GetPtr())) {
    LOG_ERROR("Could not find %s", lib_name.c_str());
    return false;
  }

  crazy_context_t* context = GetCrazyContext();
  size_t relro_start = 0;
  size_t relro_size = 0;
  int relro_fd = -1;
  s_lib_info_fields.GetRelroInfo(env, lib_info_obj, &relro_start, &relro_size,
                                 &relro_fd);

  LOG_INFO("library=%s relro start=%p size=%p fd=%d", lib_name.c_str(),
           (void*)relro_start, (void*)relro_size, relro_fd);

  if (!crazy_library_use_shared_relro(library.Get(), context, relro_start,
                                      relro_size, relro_fd)) {
    LOG_ERROR("Could not use shared RELRO for %s: %s", lib_name.c_str(),
              crazy_context_get_error(context));
    return false;
  }

  LOG_INFO("Library %s using shared RELRO section!", lib_name.c_str());

  return true;
}

}  // namespace

bool LegacyLinkerJNIInit(JavaVM* vm, JNIEnv* env) {
  LOG_INFO("Entering");

  // Save JavaVM* handle into linker, so that it can call JNI_OnLoad()
  // automatically when loading libraries containing JNI entry points.
  crazy_set_java_vm(vm, JNI_VERSION_1_4);

  return true;
}

}  // namespace chromium_android_linker
