//
// Copyright 2019 The Abseil Authors.
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

#ifndef ABSL_FLAGS_INTERNAL_REGISTRY_H_
#define ABSL_FLAGS_INTERNAL_REGISTRY_H_

#include <functional>
#include <map>
#include <string>

#include "absl/base/config.h"
#include "absl/base/macros.h"
#include "absl/flags/internal/commandlineflag.h"
#include "absl/strings/string_view.h"

// --------------------------------------------------------------------
// Global flags registry API.

namespace absl {
ABSL_NAMESPACE_BEGIN
namespace flags_internal {

CommandLineFlag* FindCommandLineFlag(absl::string_view name);
CommandLineFlag* FindRetiredFlag(absl::string_view name);

// Executes specified visitor for each non-retired flag in the registry.
// Requires the caller hold the registry lock.
void ForEachFlagUnlocked(std::function<void(CommandLineFlag*)> visitor);
// Executes specified visitor for each non-retired flag in the registry. While
// callback are executed, the registry is locked and can't be changed.
void ForEachFlag(std::function<void(CommandLineFlag*)> visitor);

//-----------------------------------------------------------------------------

bool RegisterCommandLineFlag(CommandLineFlag*);

//-----------------------------------------------------------------------------
// Retired registrations:
//
// Retired flag registrations are treated specially. A 'retired' flag is
// provided only for compatibility with automated invocations that still
// name it.  A 'retired' flag:
//   - is not bound to a C++ FLAGS_ reference.
//   - has a type and a value, but that value is intentionally inaccessible.
//   - does not appear in --help messages.
//   - is fully supported by _all_ flag parsing routines.
//   - consumes args normally, and complains about type mismatches in its
//     argument.
//   - emits a complaint but does not die (e.g. LOG(ERROR)) if it is
//     accessed by name through the flags API for parsing or otherwise.
//
// The registrations for a flag happen in an unspecified order as the
// initializers for the namespace-scope objects of a program are run.
// Any number of weak registrations for a flag can weakly define the flag.
// One non-weak registration will upgrade the flag from weak to non-weak.
// Further weak registrations of a non-weak flag are ignored.
//
// This mechanism is designed to support moving dead flags into a
// 'graveyard' library.  An example migration:
//
//   0: Remove references to this FLAGS_flagname in the C++ codebase.
//   1: Register as 'retired' in old_lib.
//   2: Make old_lib depend on graveyard.
//   3: Add a redundant 'retired' registration to graveyard.
//   4: Remove the old_lib 'retired' registration.
//   5: Eventually delete the graveyard registration entirely.
//

// Retire flag with name "name" and type indicated by ops.
bool Retire(const char* name, FlagFastTypeId type_id);

// Registered a retired flag with name 'flag_name' and type 'T'.
template <typename T>
inline bool RetiredFlag(const char* flag_name) {
  return flags_internal::Retire(flag_name, base_internal::FastTypeId<T>());
}

// If the flag is retired, returns true and indicates in |*type_is_bool|
// whether the type of the retired flag is a bool.
// Only to be called by code that needs to explicitly ignore retired flags.
bool IsRetiredFlag(absl::string_view name, bool* type_is_bool);

//-----------------------------------------------------------------------------
// Saves the states (value, default value, whether the user has set
// the flag, registered validators, etc) of all flags, and restores
// them when the FlagSaver is destroyed.
//
// This class is thread-safe.  However, its destructor writes to
// exactly the set of flags that have changed value during its
// lifetime, so concurrent _direct_ access to those flags
// (i.e. FLAGS_foo instead of {Get,Set}CommandLineOption()) is unsafe.

class FlagSaver {
 public:
  FlagSaver();
  ~FlagSaver();

  FlagSaver(const FlagSaver&) = delete;
  void operator=(const FlagSaver&) = delete;

  // Prevents saver from restoring the saved state of flags.
  void Ignore();

 private:
  class FlagSaverImpl* impl_;  // we use pimpl here to keep API steady
};

}  // namespace flags_internal
ABSL_NAMESPACE_END
}  // namespace absl

#endif  // ABSL_FLAGS_INTERNAL_REGISTRY_H_
