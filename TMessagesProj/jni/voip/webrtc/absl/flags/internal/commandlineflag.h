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

#ifndef ABSL_FLAGS_INTERNAL_COMMANDLINEFLAG_H_
#define ABSL_FLAGS_INTERNAL_COMMANDLINEFLAG_H_

#include <stddef.h>
#include <stdint.h>

#include <memory>
#include <string>
#include <typeinfo>

#include "absl/base/config.h"
#include "absl/base/internal/fast_type_id.h"
#include "absl/base/macros.h"
#include "absl/flags/config.h"
#include "absl/flags/marshalling.h"
#include "absl/strings/string_view.h"
#include "absl/types/optional.h"

namespace absl {
ABSL_NAMESPACE_BEGIN
namespace flags_internal {

// An alias for flag fast type id. This value identifies the flag value type
// simialarly to typeid(T), without relying on RTTI being available. In most
// cases this id is enough to uniquely identify the flag's value type. In a few
// cases we'll have to resort to using actual RTTI implementation if it is
// available.
using FlagFastTypeId = base_internal::FastTypeIdType;

// Options that control SetCommandLineOptionWithMode.
enum FlagSettingMode {
  // update the flag's value unconditionally (can call this multiple times).
  SET_FLAGS_VALUE,
  // update the flag's value, but *only if* it has not yet been updated
  // with SET_FLAGS_VALUE, SET_FLAG_IF_DEFAULT, or "FLAGS_xxx = nondef".
  SET_FLAG_IF_DEFAULT,
  // set the flag's default value to this.  If the flag has not been updated
  // yet (via SET_FLAGS_VALUE, SET_FLAG_IF_DEFAULT, or "FLAGS_xxx = nondef")
  // change the flag's current value to the new default value as well.
  SET_FLAGS_DEFAULT
};

// Options that control ParseFrom: Source of a value.
enum ValueSource {
  // Flag is being set by value specified on a command line.
  kCommandLine,
  // Flag is being set by value specified in the code.
  kProgrammaticChange,
};

// Handle to FlagState objects. Specific flag state objects will restore state
// of a flag produced this flag state from method CommandLineFlag::SaveState().
class FlagStateInterface {
 public:
  virtual ~FlagStateInterface();

  // Restores the flag originated this object to the saved state.
  virtual void Restore() const = 0;
};

// Holds all information for a flag.
class CommandLineFlag {
 public:
  constexpr CommandLineFlag() = default;

  // Not copyable/assignable.
  CommandLineFlag(const CommandLineFlag&) = delete;
  CommandLineFlag& operator=(const CommandLineFlag&) = delete;

  // Non-polymorphic access methods.

  // Return true iff flag has type T.
  template <typename T>
  inline bool IsOfType() const {
    return TypeId() == base_internal::FastTypeId<T>();
  }

  // Attempts to retrieve the flag value. Returns value on success,
  // absl::nullopt otherwise.
  template <typename T>
  absl::optional<T> Get() const {
    if (IsRetired() || !IsOfType<T>()) {
      return absl::nullopt;
    }

    // Implementation notes:
    //
    // We are wrapping a union around the value of `T` to serve three purposes:
    //
    //  1. `U.value` has correct size and alignment for a value of type `T`
    //  2. The `U.value` constructor is not invoked since U's constructor does
    //     not do it explicitly.
    //  3. The `U.value` destructor is invoked since U's destructor does it
    //     explicitly. This makes `U` a kind of RAII wrapper around non default
    //     constructible value of T, which is destructed when we leave the
    //     scope. We do need to destroy U.value, which is constructed by
    //     CommandLineFlag::Read even though we left it in a moved-from state
    //     after std::move.
    //
    // All of this serves to avoid requiring `T` being default constructible.
    union U {
      T value;
      U() {}
      ~U() { value.~T(); }
    };
    U u;

    Read(&u.value);
    return std::move(u.value);
  }

  // Polymorphic access methods

  // Returns name of this flag.
  virtual absl::string_view Name() const = 0;
  // Returns name of the file where this flag is defined.
  virtual std::string Filename() const = 0;
  // Returns name of the flag's value type for some built-in types or empty
  // string.
  virtual absl::string_view Typename() const = 0;
  // Returns help message associated with this flag.
  virtual std::string Help() const = 0;
  // Returns true iff this object corresponds to retired flag.
  virtual bool IsRetired() const;
  // Returns true iff this is a handle to an Abseil Flag.
  virtual bool IsAbseilFlag() const;
  // Returns id of the flag's value type.
  virtual FlagFastTypeId TypeId() const = 0;
  virtual bool IsModified() const = 0;
  virtual bool IsSpecifiedOnCommandLine() const = 0;
  virtual std::string DefaultValue() const = 0;
  virtual std::string CurrentValue() const = 0;

  // Interfaces to operate on validators.
  virtual bool ValidateInputValue(absl::string_view value) const = 0;

  // Interface to save flag to some persistent state. Returns current flag state
  // or nullptr if flag does not support saving and restoring a state.
  virtual std::unique_ptr<FlagStateInterface> SaveState() = 0;

  // Sets the value of the flag based on specified string `value`. If the flag
  // was successfully set to new value, it returns true. Otherwise, sets `error`
  // to indicate the error, leaves the flag unchanged, and returns false. There
  // are three ways to set the flag's value:
  //  * Update the current flag value
  //  * Update the flag's default value
  //  * Update the current flag value if it was never set before
  // The mode is selected based on `set_mode` parameter.
  virtual bool ParseFrom(absl::string_view value,
                         flags_internal::FlagSettingMode set_mode,
                         flags_internal::ValueSource source,
                         std::string* error) = 0;

  // Checks that flags default value can be converted to string and back to the
  // flag's value type.
  virtual void CheckDefaultValueParsingRoundtrip() const = 0;

 protected:
  ~CommandLineFlag() = default;

 private:
  // Copy-construct a new value of the flag's type in a memory referenced by
  // the dst based on the current flag's value.
  virtual void Read(void* dst) const = 0;
};

// This macro is the "source of truth" for the list of supported flag built-in
// types.
#define ABSL_FLAGS_INTERNAL_BUILTIN_TYPES(A) \
  A(bool)                                    \
  A(short)                                   \
  A(unsigned short)                          \
  A(int)                                     \
  A(unsigned int)                            \
  A(long)                                    \
  A(unsigned long)                           \
  A(long long)                               \
  A(unsigned long long)                      \
  A(double)                                  \
  A(float)                                   \
  A(std::string)                             \
  A(std::vector<std::string>)

}  // namespace flags_internal
ABSL_NAMESPACE_END
}  // namespace absl

#endif  // ABSL_FLAGS_INTERNAL_COMMANDLINEFLAG_H_
