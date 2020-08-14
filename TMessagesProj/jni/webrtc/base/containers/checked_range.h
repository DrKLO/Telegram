// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_CONTAINERS_CHECKED_RANGE_H_
#define BASE_CONTAINERS_CHECKED_RANGE_H_

#include <stddef.h>

#include <iterator>
#include <type_traits>

#include "base/containers/checked_iterators.h"
#include "base/stl_util.h"

namespace base {

// CheckedContiguousRange is a light-weight wrapper around a container modeling
// the ContiguousContainer requirement [1, 2]. Effectively this means that the
// container stores its elements contiguous in memory. Furthermore, it is
// expected that base::data(container) and base::size(container) are valid
// expressions, and that data() + idx is dereferenceable for all idx in the
// range [0, size()). In the standard library this includes the containers
// std::string, std::vector and std::array, but other containers like
// std::initializer_list and C arrays are supported as well.
//
// In general this class is in nature quite similar to base::span, and its API
// is inspired by it. Similarly to base::span (and other view-like containers
// such as base::StringPiece) callers are encouraged to pass checked ranges by
// value.
//
// However, one important difference is that this class stores a pointer to the
// underlying container (as opposed to just storing its data() and size()), and
// thus is able to deal with changes to the container, such as removing or
// adding elements.
//
// Note however that this class still does not extend the life-time of the
// underlying container, and thus callers need to make sure that the container
// outlives the view to avoid dangling pointers and references.
//
// Lastly, this class leverages base::CheckedContiguousIterator to perform
// bounds CHECKs, causing program termination when e.g. dereferencing the end
// iterator.
//
// [1] https://en.cppreference.com/w/cpp/named_req/ContiguousContainer
// [2]
// https://eel.is/c++draft/container.requirements.general#def:contiguous_container
template <typename ContiguousContainer>
class CheckedContiguousRange {
 public:
  using element_type = std::remove_pointer_t<decltype(
      base::data(std::declval<ContiguousContainer&>()))>;
  using value_type = std::remove_cv_t<element_type>;
  using reference = element_type&;
  using const_reference = const element_type&;
  using pointer = element_type*;
  using const_pointer = const element_type*;
  using iterator = CheckedContiguousIterator<element_type>;
  using const_iterator = CheckedContiguousConstIterator<element_type>;
  using reverse_iterator = std::reverse_iterator<iterator>;
  using const_reverse_iterator = std::reverse_iterator<const_iterator>;
  using difference_type = typename iterator::difference_type;
  using size_type = size_t;

  static_assert(!std::is_reference<ContiguousContainer>::value,
                "Error: ContiguousContainer can not be a reference.");

  // Required for converting constructor below.
  template <typename Container>
  friend class CheckedContiguousRange;

  // Default constructor. Behaves as if the underlying container was empty.
  constexpr CheckedContiguousRange() noexcept = default;

  // Templated constructor restricted to possibly cvref qualified versions of
  // ContiguousContainer. This makes sure it does not shadow the auto generated
  // copy and move constructors.
  template <int&... ExplicitArgumentBarrier,
            typename Container,
            typename = std::enable_if_t<std::is_same<
                std::remove_cv_t<std::remove_reference_t<ContiguousContainer>>,
                std::remove_cv_t<std::remove_reference_t<Container>>>::value>>
  constexpr CheckedContiguousRange(Container&& container) noexcept
      : container_(&container) {}

  // Converting constructor allowing conversions like CCR<C> to CCR<const C>,
  // but disallowing CCR<const C> to CCR<C> or CCR<Derived[]> to CCR<Base[]>,
  // which are unsafe. Furthermore, this is the same condition as used by the
  // converting constructors of std::span<T> and std::unique_ptr<T[]>.
  // See https://wg21.link/n4042 for details.
  template <int&... ExplicitArgumentBarrier,
            typename Container,
            typename = std::enable_if_t<std::is_convertible<
                typename CheckedContiguousRange<Container>::element_type (*)[],
                element_type (*)[]>::value>>
  constexpr CheckedContiguousRange(
      CheckedContiguousRange<Container> range) noexcept
      : container_(range.container_) {}

  constexpr iterator begin() const noexcept {
    return iterator(data(), data(), data() + size());
  }

  constexpr iterator end() const noexcept {
    return iterator(data(), data() + size(), data() + size());
  }

  constexpr const_iterator cbegin() const noexcept { return begin(); }

  constexpr const_iterator cend() const noexcept { return end(); }

  constexpr reverse_iterator rbegin() const noexcept {
    return reverse_iterator(end());
  }

  constexpr reverse_iterator rend() const noexcept {
    return reverse_iterator(begin());
  }

  constexpr const_reverse_iterator crbegin() const noexcept { return rbegin(); }

  constexpr const_reverse_iterator crend() const noexcept { return rend(); }

  constexpr reference front() const noexcept { return *begin(); }

  constexpr reference back() const noexcept { return *(end() - 1); }

  constexpr reference operator[](size_type idx) const noexcept {
    return *(begin() + idx);
  }

  constexpr pointer data() const noexcept {
    return container_ ? base::data(*container_) : nullptr;
  }

  constexpr const_pointer cdata() const noexcept { return data(); }

  constexpr size_type size() const noexcept {
    return container_ ? base::size(*container_) : 0;
  }

  constexpr bool empty() const noexcept {
    return container_ ? base::empty(*container_) : true;
  }

 private:
  ContiguousContainer* container_ = nullptr;
};

// Utility functions helping to create const ranges and performing automatic
// type deduction.
template <typename ContiguousContainer>
using CheckedContiguousConstRange =
    CheckedContiguousRange<const ContiguousContainer>;

template <int&... ExplicitArgumentBarrier, typename ContiguousContainer>
constexpr auto MakeCheckedContiguousRange(
    ContiguousContainer&& container) noexcept {
  return CheckedContiguousRange<std::remove_reference_t<ContiguousContainer>>(
      std::forward<ContiguousContainer>(container));
}

template <int&... ExplicitArgumentBarrier, typename ContiguousContainer>
constexpr auto MakeCheckedContiguousConstRange(
    ContiguousContainer&& container) noexcept {
  return CheckedContiguousConstRange<
      std::remove_reference_t<ContiguousContainer>>(
      std::forward<ContiguousContainer>(container));
}

}  // namespace base

#endif  // BASE_CONTAINERS_CHECKED_RANGE_H_
