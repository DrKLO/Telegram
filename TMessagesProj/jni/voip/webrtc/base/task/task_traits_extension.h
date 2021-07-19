// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_TASK_TASK_TRAITS_EXTENSION_H_
#define BASE_TASK_TASK_TRAITS_EXTENSION_H_

#include <stdint.h>

#include <array>
#include <tuple>
#include <utility>

#include "base/base_export.h"
#include "base/traits_bag.h"

namespace base {

// Embedders can attach additional traits to a TaskTraits object in a way that
// is opaque to base. These extension traits can then be specified along the
// base traits when constructing the TaskTraits object. They are then stored and
// propagated with the TaskTraits object.
//
// To support constexpr-compatible construction, extension traits are stored in
// a fixed-size byte array in the TaskTraits object and serialized into and
// parsed from this storage by an embedder-provided extension class and
// MakeTaskTraitsExtension() template function. The embedder can later access
// the extension traits via TaskTraits::GetExtension<[ExtensionClass]>().
//
// A TaskTraits extension class needs to specify publicly:
//  (1) -- static constexpr uint8_t kExtensionId.
//      This field's value identifies the type of the extension uniquely within
//      each process. The embedder is responsible for ensuring uniqueness and
//      can assign values between kFirstEmbedderExtensionId and kMaxExtensionId
//      of TaskTraitsExtensionStorage::ExtensionId.
//  (2) -- static const [ExtensionClass] Parse(
//      --     const base::TaskTraitsExtensionStorage& extension).
//      Parses and constructs an extension object from the provided storage.
//
// For each TaskTraits extension class, the embedder has to provide a
// corresponding MakeTaskTraitsExtension definition inside the same namespace
// as its extension traits:
//  (3) -- template <...>
//      -- constexpr base::TaskTraitsExtensionStorage MakeTaskTraitsExtension(
//      --     ArgTypes... args).
//      Constructs and serializes an extension with the given arguments into
//      a TaskTraitsExtensionStorage and returns it. When the extension is used,
//      all traits, including the base ones, are passed to this function in
//      order make sure TaskTraits constructor only participates in overload
//      resolution if all traits are valid. As such, this function should only
//      accept valid task traits recognised by the extension and the base task
//      traits.
//
// EXAMPLE (see also base/task/test_task_traits_extension.h):
// --------
//
// namespace my_embedder {
// enum class MyExtensionTrait {kMyValue1, kMyValue2};
//
// class MyTaskTraitsExtension {
//  public:
//   static constexpr uint8_t kExtensionId =
//       TaskTraitsExtensionStorage::kFirstEmbedderExtensionId;
//
//   struct ValidTrait : public TaskTraits::ValidTrait {
//     // Accept base traits in MakeTaskTraitsExtension (see above).
//     using TaskTraits::ValidTrait::ValidTrait;
//
//     ValidTrait(MyExtensionTrait);
//   };
//
//   using MyExtensionTraitFilter =
//     trait_helpers::EnumTraitFilter<MyExtensionTrait, MyExtensionTrait::kA>;
//
//   // Constructor that accepts only valid traits as specified by ValidTraits.
//   template <class... ArgTypes,
//             class CheckArgumentsAreValid = std::enable_if_t<
//                 base::trait_helpers::AreValidTraits<
//                     ValidTrait, ArgTypes...>::value>>
//   constexpr MyTaskTraitsExtension(ArgTypes... args)
//       : my_trait_(trait_helpers::GetTraitFromArgList<MyExtensionTraitFilter>(
//                      args...)) {}
//
//   // Serializes MyTaskTraitsExtension into a storage object and returns it.
//   constexpr base::TaskTraitsExtensionStorage Serialize() const {
//     // Note: can't use reinterpret_cast or placement new because neither are
//     // constexpr-compatible.
//     return {kExtensionId, {{static_cast<uint8_t>(my_trait_)}}};
//   }
//
//   // Creates a MyTaskTraitsExtension by parsing it from a storage object.
//   static const MyTaskTraitsExtension Parse(
//       const base::TaskTraitsExtensionStorage& extension) {
//     return MyTaskTraitsExtension(
//         static_cast<MyExtensionTrait>(extension.data[0]));
//   }
//
//   constexpr MyExtensionTrait my_trait() const { return my_trait_; }
//
//  private:
//   MyExtensionTrait my_trait_;
// };
//
// // Creates a MyTaskTraitsExtension for the provided |args| and serializes it
// // into |extension|. Accepts only valid arguments for the
// // MyTaskTraitsExtension() constructor.
// template <class... ArgTypes,
//           class = std::enable_if_t<
//               base::trait_helpers::AreValidTraits<
//                   MyTaskTraitsExtension::ValidTrait, ArgTypes...>::value>>
// constexpr base::TaskTraitsExtensionStorage MakeTaskTraitsExtension(
//     ArgTypes... args) {
//   return MyTaskTraitsExtension(args...).Serialize();
// }
// }  // namespace my_embedder
//
// // Construction of TaskTraits with extension traits.
// constexpr TaskTraits t1 = {my_embedder::MyExtensionTrait::kValueB};
// constexpr TaskTraits t2 = {base::MayBlock(),
//                            my_embedder::MyExtensionTrait::kValueA};
//
// // Extension traits can also be specified directly when posting a task.
// base::PostTask(FROM_HERE,
//                          {my_embedder::MyExtensionTrait::kValueB},
//                          base::BindOnce(...));

// Stores extension traits opaquely inside a fixed-size data array. We store
// this data directly (rather than in a separate object on the heap) to support
// constexpr-compatible TaskTraits construction.
struct BASE_EXPORT TaskTraitsExtensionStorage {
  // Size in bytes.
  // Keep in sync with org.chromium.base.task.TaskTraits.EXTENSION_STORAGE_SIZE
  static constexpr size_t kStorageSize = 8;

  inline constexpr TaskTraitsExtensionStorage();
  inline constexpr TaskTraitsExtensionStorage(
      uint8_t extension_id_in,
      const std::array<uint8_t, kStorageSize>& data_in);
  inline constexpr TaskTraitsExtensionStorage(
      uint8_t extension_id_in,
      std::array<uint8_t, kStorageSize>&& data_in);

  inline constexpr TaskTraitsExtensionStorage(
      const TaskTraitsExtensionStorage& other);
  inline TaskTraitsExtensionStorage& operator=(
      const TaskTraitsExtensionStorage& other) = default;

  inline bool operator==(const TaskTraitsExtensionStorage& other) const;

  enum ExtensionId : uint8_t {
    // Keep in sync with org.chromium.base.task.TaskTraits.INVALID_EXTENSION_ID
    kInvalidExtensionId = 0,
    // The embedder is responsible for assigning the remaining values uniquely.
    kFirstEmbedderExtensionId = 1,
    // Maximum number of extension types is artificially limited to support
    // super efficient TaskExecutor lookup in post_task.cc.
    // Keep in sync with org.chromium.base.TaskTraits.MAX_EXTENSION_ID
    kMaxExtensionId = 4
  };

  // Identifies the type of extension. See ExtensionId enum above.
  uint8_t extension_id;

  // Serialized extension data.
  std::array<uint8_t, kStorageSize> data;
};

// TODO(https://crbug.com/874482): These constructors need to be "inline" but
// defined outside the class above, because the chromium-style clang plugin
// doesn't exempt constexpr constructors at the moment.
inline constexpr TaskTraitsExtensionStorage::TaskTraitsExtensionStorage()
    : extension_id(kInvalidExtensionId), data{} {}

inline constexpr TaskTraitsExtensionStorage::TaskTraitsExtensionStorage(
    uint8_t extension_id_in,
    const std::array<uint8_t, kStorageSize>& data_in)
    : extension_id(extension_id_in), data(data_in) {}

inline constexpr TaskTraitsExtensionStorage::TaskTraitsExtensionStorage(
    uint8_t extension_id_in,
    std::array<uint8_t, kStorageSize>&& data_in)
    : extension_id(extension_id_in), data(std::move(data_in)) {}

inline constexpr TaskTraitsExtensionStorage::TaskTraitsExtensionStorage(
    const TaskTraitsExtensionStorage& other) = default;

namespace trait_helpers {

// Helper class whose constructor tests if an extension accepts a list of
// argument types.
struct TaskTraitsExtension {
  template <class... ArgTypes,
            class CheckCanMakeExtension =
                decltype(MakeTaskTraitsExtension(std::declval<ArgTypes>()...))>
  constexpr TaskTraitsExtension(ArgTypes... args) {}
};

// Tests that that a trait extension accepts all |ArgsTypes...|.
template <class... ArgTypes>
using AreValidTraitsForExtension =
    std::is_constructible<TaskTraitsExtension, ArgTypes...>;

// Helper function that returns the TaskTraitsExtensionStorage of a
// serialized extension created with |args...| if there are arguments that are
// not valid base traits, or a default constructed TaskTraitsExtensionStorage
// otherwise.
template <class... ArgTypes>
constexpr TaskTraitsExtensionStorage GetTaskTraitsExtension(
    std::true_type base_traits,
    ArgTypes... args) {
  return TaskTraitsExtensionStorage();
}

template <class... ArgTypes>
constexpr TaskTraitsExtensionStorage GetTaskTraitsExtension(
    std::false_type base_traits,
    ArgTypes... args) {
  return MakeTaskTraitsExtension(args...);
}

}  // namespace trait_helpers

// TODO(eseckler): Default the comparison operator once C++20 arrives.
inline bool TaskTraitsExtensionStorage::operator==(
    const TaskTraitsExtensionStorage& other) const {
  static_assert(
      9 == sizeof(TaskTraitsExtensionStorage),
      "Update comparison operator when TaskTraitsExtensionStorage changes");
  return extension_id == other.extension_id && data == other.data;
}


}  // namespace base

#endif  // BASE_TASK_TASK_TRAITS_EXTENSION_H_
