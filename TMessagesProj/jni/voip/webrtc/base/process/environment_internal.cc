// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/process/environment_internal.h"

#include <stddef.h>

#include <vector>

namespace base {
namespace internal {

namespace {

#if defined(OS_POSIX) || defined(OS_FUCHSIA) || defined(OS_WIN)
// Parses a null-terminated input string of an environment block. The key is
// placed into the given string, and the total length of the line, including
// the terminating null, is returned.
size_t ParseEnvLine(const NativeEnvironmentString::value_type* input,
                    NativeEnvironmentString* key) {
  // Skip to the equals or end of the string, this is the key.
  size_t cur = 0;
  while (input[cur] && input[cur] != '=')
    cur++;
  *key = NativeEnvironmentString(&input[0], cur);

  // Now just skip to the end of the string.
  while (input[cur])
    cur++;
  return cur + 1;
}
#endif

}  // namespace

#if defined(OS_POSIX) || defined(OS_FUCHSIA)

std::unique_ptr<char* []> AlterEnvironment(const char* const* const env,
                                           const EnvironmentMap& changes) {
  std::string value_storage;  // Holds concatenated null-terminated strings.
  std::vector<size_t> result_indices;  // Line indices into value_storage.

  // First build up all of the unchanged environment strings. These are
  // null-terminated of the form "key=value".
  std::string key;
  for (size_t i = 0; env[i]; i++) {
    size_t line_length = ParseEnvLine(env[i], &key);

    // Keep only values not specified in the change vector.
    auto found_change = changes.find(key);
    if (found_change == changes.end()) {
      result_indices.push_back(value_storage.size());
      value_storage.append(env[i], line_length);
    }
  }

  // Now append all modified and new values.
  for (const auto& i : changes) {
    if (!i.second.empty()) {
      result_indices.push_back(value_storage.size());
      value_storage.append(i.first);
      value_storage.push_back('=');
      value_storage.append(i.second);
      value_storage.push_back(0);
    }
  }

  size_t pointer_count_required =
      result_indices.size() + 1 +  // Null-terminated array of pointers.
      (value_storage.size() + sizeof(char*) - 1) / sizeof(char*);  // Buffer.
  std::unique_ptr<char*[]> result(new char*[pointer_count_required]);

  // The string storage goes after the array of pointers.
  char* storage_data =
      reinterpret_cast<char*>(&result.get()[result_indices.size() + 1]);
  if (!value_storage.empty())
    memcpy(storage_data, value_storage.data(), value_storage.size());

  // Fill array of pointers at the beginning of the result.
  for (size_t i = 0; i < result_indices.size(); i++)
    result[i] = &storage_data[result_indices[i]];
  result[result_indices.size()] = 0;  // Null terminator.

  return result;
}

#elif defined(OS_WIN)

NativeEnvironmentString AlterEnvironment(const wchar_t* env,
                                         const EnvironmentMap& changes) {
  NativeEnvironmentString result;

  // First build up all of the unchanged environment strings.
  const wchar_t* ptr = env;
  while (*ptr) {
    std::wstring key;
    size_t line_length = ParseEnvLine(ptr, &key);

    // Keep only values not specified in the change vector.
    if (changes.find(key) == changes.end()) {
      result.append(ptr, line_length);
    }
    ptr += line_length;
  }

  // Now append all modified and new values.
  for (const auto& i : changes) {
    // Windows environment blocks cannot handle keys or values with NULs.
    CHECK_EQ(std::wstring::npos, i.first.find(L'\0'));
    CHECK_EQ(std::wstring::npos, i.second.find(L'\0'));
    if (!i.second.empty()) {
      result += i.first;
      result.push_back('=');
      result += i.second;
      result.push_back('\0');
    }
  }

  // Add the terminating NUL.
  result.push_back('\0');
  return result;
}

#endif  // OS_POSIX || OS_FUCHSIA

}  // namespace internal
}  // namespace base
