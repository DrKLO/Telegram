// Copyright (c) 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_TASK_TEST_TASK_TRAITS_EXTENSION_H_
#define BASE_TASK_TEST_TASK_TRAITS_EXTENSION_H_

#include <utility>

#include "base/task/task_traits.h"

namespace base {

enum class TestExtensionEnumTrait { kA, kB, kC };
struct TestExtensionBoolTrait {};

// Example TaskTraits extension for use in tests.
class TestTaskTraitsExtension {
 public:
  static constexpr uint8_t kExtensionId =
      TaskTraitsExtensionStorage::kFirstEmbedderExtensionId;

  struct ValidTrait : public TaskTraits::ValidTrait {
    using TaskTraits::ValidTrait::ValidTrait;

    ValidTrait(TestExtensionEnumTrait);
    ValidTrait(TestExtensionBoolTrait);
  };

  template <class... ArgTypes,
            class CheckArgumentsAreValid = std::enable_if_t<
                trait_helpers::AreValidTraits<ValidTrait, ArgTypes...>::value>>
  constexpr TestTaskTraitsExtension(ArgTypes... args)
      : enum_trait_(
            trait_helpers::GetEnum<TestExtensionEnumTrait,
                                   TestExtensionEnumTrait::kA>(args...)),
        bool_trait_(
            trait_helpers::HasTrait<TestExtensionBoolTrait, ArgTypes...>()) {}

  constexpr TaskTraitsExtensionStorage Serialize() const {
    return {kExtensionId, {{static_cast<uint8_t>(enum_trait_), bool_trait_}}};
  }

  static const TestTaskTraitsExtension Parse(
      const TaskTraitsExtensionStorage& extension) {
    if (extension.data[1]) {
      return TestTaskTraitsExtension(
          static_cast<TestExtensionEnumTrait>(extension.data[0]),
          TestExtensionBoolTrait());
    } else {
      return TestTaskTraitsExtension(
          static_cast<TestExtensionEnumTrait>(extension.data[0]));
    }
  }

  constexpr TestExtensionEnumTrait enum_trait() const { return enum_trait_; }
  constexpr bool bool_trait() const { return bool_trait_; }

 private:
  TestExtensionEnumTrait enum_trait_;
  bool bool_trait_;
};

template <class... ArgTypes,
          class = std::enable_if_t<
              trait_helpers::AreValidTraits<TestTaskTraitsExtension::ValidTrait,
                                            ArgTypes...>::value>>
constexpr TaskTraitsExtensionStorage MakeTaskTraitsExtension(ArgTypes... args) {
  return TestTaskTraitsExtension(std::forward<ArgTypes>(args)...).Serialize();
}

}  // namespace base

#endif  // BASE_TASK_TEST_TASK_TRAITS_EXTENSION_H_
