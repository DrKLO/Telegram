// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_CONTAINERS_FLAT_MAP_H_
#define BASE_CONTAINERS_FLAT_MAP_H_

#include <functional>
#include <tuple>
#include <utility>

#include "base/containers/flat_tree.h"
#include "base/logging.h"
#include "base/template_util.h"

namespace base {

namespace internal {

// An implementation of the flat_tree GetKeyFromValue template parameter that
// extracts the key as the first element of a pair.
template <class Key, class Mapped>
struct GetKeyFromValuePairFirst {
  const Key& operator()(const std::pair<Key, Mapped>& p) const {
    return p.first;
  }
};

}  // namespace internal

// flat_map is a container with a std::map-like interface that stores its
// contents in a sorted vector.
//
// Please see //base/containers/README.md for an overview of which container
// to select.
//
// PROS
//
//  - Good memory locality.
//  - Low overhead, especially for smaller maps.
//  - Performance is good for more workloads than you might expect (see
//    overview link above).
//  - Supports C++14 map interface.
//
// CONS
//
//  - Inserts and removals are O(n).
//
// IMPORTANT NOTES
//
//  - Iterators are invalidated across mutations.
//  - If possible, construct a flat_map in one operation by inserting into
//    a std::vector and moving that vector into the flat_map constructor.
//
// QUICK REFERENCE
//
// Most of the core functionality is inherited from flat_tree. Please see
// flat_tree.h for more details for most of these functions. As a quick
// reference, the functions available are:
//
// Constructors (inputs need not be sorted):
//   flat_map(InputIterator first, InputIterator last,
//            const Compare& compare = Compare());
//   flat_map(const flat_map&);
//   flat_map(flat_map&&);
//   flat_map(const std::vector<value_type>& items,
//            const Compare& compare = Compare());
//   flat_map(std::vector<value_type>&& items,
//            const Compare& compare = Compare()); // Re-use storage.
//   flat_map(std::initializer_list<value_type> ilist,
//            const Compare& comp = Compare());
//
// Assignment functions:
//   flat_map& operator=(const flat_map&);
//   flat_map& operator=(flat_map&&);
//   flat_map& operator=(initializer_list<value_type>);
//
// Memory management functions:
//   void   reserve(size_t);
//   size_t capacity() const;
//   void   shrink_to_fit();
//
// Size management functions:
//   void   clear();
//   size_t size() const;
//   size_t max_size() const;
//   bool   empty() const;
//
// Iterator functions:
//   iterator               begin();
//   const_iterator         begin() const;
//   const_iterator         cbegin() const;
//   iterator               end();
//   const_iterator         end() const;
//   const_iterator         cend() const;
//   reverse_iterator       rbegin();
//   const reverse_iterator rbegin() const;
//   const_reverse_iterator crbegin() const;
//   reverse_iterator       rend();
//   const_reverse_iterator rend() const;
//   const_reverse_iterator crend() const;
//
// Insert and accessor functions:
//   mapped_type&         operator[](const key_type&);
//   mapped_type&         operator[](key_type&&);
//   mapped_type&         at(const K&);
//   const mapped_type&   at(const K&) const;
//   pair<iterator, bool> insert(const value_type&);
//   pair<iterator, bool> insert(value_type&&);
//   iterator             insert(const_iterator hint, const value_type&);
//   iterator             insert(const_iterator hint, value_type&&);
//   void                 insert(InputIterator first, InputIterator last);
//   pair<iterator, bool> insert_or_assign(K&&, M&&);
//   iterator             insert_or_assign(const_iterator hint, K&&, M&&);
//   pair<iterator, bool> emplace(Args&&...);
//   iterator             emplace_hint(const_iterator, Args&&...);
//   pair<iterator, bool> try_emplace(K&&, Args&&...);
//   iterator             try_emplace(const_iterator hint, K&&, Args&&...);

// Underlying type functions:
//   underlying_type      extract() &&;
//   void                 replace(underlying_type&&);
//
// Erase functions:
//   iterator erase(iterator);
//   iterator erase(const_iterator);
//   iterator erase(const_iterator first, const_iterator& last);
//   template <class K> size_t erase(const K& key);
//
// Comparators (see std::map documentation).
//   key_compare   key_comp() const;
//   value_compare value_comp() const;
//
// Search functions:
//   template <typename K> size_t                   count(const K&) const;
//   template <typename K> iterator                 find(const K&);
//   template <typename K> const_iterator           find(const K&) const;
//   template <typename K> bool                     contains(const K&) const;
//   template <typename K> pair<iterator, iterator> equal_range(const K&);
//   template <typename K> iterator                 lower_bound(const K&);
//   template <typename K> const_iterator           lower_bound(const K&) const;
//   template <typename K> iterator                 upper_bound(const K&);
//   template <typename K> const_iterator           upper_bound(const K&) const;
//
// General functions:
//   void swap(flat_map&&);
//
// Non-member operators:
//   bool operator==(const flat_map&, const flat_map);
//   bool operator!=(const flat_map&, const flat_map);
//   bool operator<(const flat_map&, const flat_map);
//   bool operator>(const flat_map&, const flat_map);
//   bool operator>=(const flat_map&, const flat_map);
//   bool operator<=(const flat_map&, const flat_map);
//
template <class Key, class Mapped, class Compare = std::less<>>
class flat_map : public ::base::internal::flat_tree<
                     Key,
                     std::pair<Key, Mapped>,
                     ::base::internal::GetKeyFromValuePairFirst<Key, Mapped>,
                     Compare> {
 private:
  using tree = typename ::base::internal::flat_tree<
      Key,
      std::pair<Key, Mapped>,
      ::base::internal::GetKeyFromValuePairFirst<Key, Mapped>,
      Compare>;
  using underlying_type = typename tree::underlying_type;

 public:
  using key_type = typename tree::key_type;
  using mapped_type = Mapped;
  using value_type = typename tree::value_type;
  using iterator = typename tree::iterator;
  using const_iterator = typename tree::const_iterator;

  // --------------------------------------------------------------------------
  // Lifetime and assignments.
  //
  // Note: we could do away with these constructors, destructor and assignment
  // operator overloads by inheriting |tree|'s, but this breaks the GCC build
  // due to https://gcc.gnu.org/bugzilla/show_bug.cgi?id=84782 (see
  // https://crbug.com/837221).

  flat_map() = default;
  explicit flat_map(const Compare& comp);

  template <class InputIterator>
  flat_map(InputIterator first,
           InputIterator last,
           const Compare& comp = Compare());

  flat_map(const flat_map&) = default;
  flat_map(flat_map&&) noexcept = default;

  flat_map(const underlying_type& items, const Compare& comp = Compare());
  flat_map(underlying_type&& items, const Compare& comp = Compare());

  flat_map(std::initializer_list<value_type> ilist,
           const Compare& comp = Compare());

  ~flat_map() = default;

  flat_map& operator=(const flat_map&) = default;
  flat_map& operator=(flat_map&&) = default;
  // Takes the first if there are duplicates in the initializer list.
  flat_map& operator=(std::initializer_list<value_type> ilist);

  // Out-of-bound calls to at() will CHECK.
  template <class K>
  mapped_type& at(const K& key);
  template <class K>
  const mapped_type& at(const K& key) const;

  // --------------------------------------------------------------------------
  // Map-specific insert operations.
  //
  // Normal insert() functions are inherited from flat_tree.
  //
  // Assume that every operation invalidates iterators and references.
  // Insertion of one element can take O(size).

  mapped_type& operator[](const key_type& key);
  mapped_type& operator[](key_type&& key);

  template <class K, class M>
  std::pair<iterator, bool> insert_or_assign(K&& key, M&& obj);
  template <class K, class M>
  iterator insert_or_assign(const_iterator hint, K&& key, M&& obj);

  template <class K, class... Args>
  std::enable_if_t<std::is_constructible<key_type, K&&>::value,
                   std::pair<iterator, bool>>
  try_emplace(K&& key, Args&&... args);

  template <class K, class... Args>
  std::enable_if_t<std::is_constructible<key_type, K&&>::value, iterator>
  try_emplace(const_iterator hint, K&& key, Args&&... args);

  // --------------------------------------------------------------------------
  // General operations.
  //
  // Assume that swap invalidates iterators and references.

  void swap(flat_map& other) noexcept;

  friend void swap(flat_map& lhs, flat_map& rhs) noexcept { lhs.swap(rhs); }
};

// ----------------------------------------------------------------------------
// Lifetime.

template <class Key, class Mapped, class Compare>
flat_map<Key, Mapped, Compare>::flat_map(const Compare& comp) : tree(comp) {}

template <class Key, class Mapped, class Compare>
template <class InputIterator>
flat_map<Key, Mapped, Compare>::flat_map(InputIterator first,
                                         InputIterator last,
                                         const Compare& comp)
    : tree(first, last, comp) {}

template <class Key, class Mapped, class Compare>
flat_map<Key, Mapped, Compare>::flat_map(const underlying_type& items,
                                         const Compare& comp)
    : tree(items, comp) {}

template <class Key, class Mapped, class Compare>
flat_map<Key, Mapped, Compare>::flat_map(underlying_type&& items,
                                         const Compare& comp)
    : tree(std::move(items), comp) {}

template <class Key, class Mapped, class Compare>
flat_map<Key, Mapped, Compare>::flat_map(
    std::initializer_list<value_type> ilist,
    const Compare& comp)
    : flat_map(std::begin(ilist), std::end(ilist), comp) {}

// ----------------------------------------------------------------------------
// Assignments.

template <class Key, class Mapped, class Compare>
auto flat_map<Key, Mapped, Compare>::operator=(
    std::initializer_list<value_type> ilist) -> flat_map& {
  // When https://gcc.gnu.org/bugzilla/show_bug.cgi?id=84782 gets fixed, we
  // need to remember to inherit tree::operator= to prevent
  //   flat_map<...> x;
  //   x = {...};
  // from first creating a flat_map and then move assigning it. This most
  // likely would be optimized away but still affects our debug builds.
  tree::operator=(ilist);
  return *this;
}

// ----------------------------------------------------------------------------
// Lookups.

template <class Key, class Mapped, class Compare>
template <class K>
auto flat_map<Key, Mapped, Compare>::at(const K& key) -> mapped_type& {
  iterator found = tree::find(key);
  CHECK(found != tree::end());
  return found->second;
}

template <class Key, class Mapped, class Compare>
template <class K>
auto flat_map<Key, Mapped, Compare>::at(const K& key) const
    -> const mapped_type& {
  const_iterator found = tree::find(key);
  CHECK(found != tree::cend());
  return found->second;
}

// ----------------------------------------------------------------------------
// Insert operations.

template <class Key, class Mapped, class Compare>
auto flat_map<Key, Mapped, Compare>::operator[](const key_type& key)
    -> mapped_type& {
  iterator found = tree::lower_bound(key);
  if (found == tree::end() || tree::key_comp()(key, found->first))
    found = tree::unsafe_emplace(found, key, mapped_type());
  return found->second;
}

template <class Key, class Mapped, class Compare>
auto flat_map<Key, Mapped, Compare>::operator[](key_type&& key)
    -> mapped_type& {
  iterator found = tree::lower_bound(key);
  if (found == tree::end() || tree::key_comp()(key, found->first))
    found = tree::unsafe_emplace(found, std::move(key), mapped_type());
  return found->second;
}

template <class Key, class Mapped, class Compare>
template <class K, class M>
auto flat_map<Key, Mapped, Compare>::insert_or_assign(K&& key, M&& obj)
    -> std::pair<iterator, bool> {
  auto result =
      tree::emplace_key_args(key, std::forward<K>(key), std::forward<M>(obj));
  if (!result.second)
    result.first->second = std::forward<M>(obj);
  return result;
}

template <class Key, class Mapped, class Compare>
template <class K, class M>
auto flat_map<Key, Mapped, Compare>::insert_or_assign(const_iterator hint,
                                                      K&& key,
                                                      M&& obj) -> iterator {
  auto result = tree::emplace_hint_key_args(hint, key, std::forward<K>(key),
                                            std::forward<M>(obj));
  if (!result.second)
    result.first->second = std::forward<M>(obj);
  return result.first;
}

template <class Key, class Mapped, class Compare>
template <class K, class... Args>
auto flat_map<Key, Mapped, Compare>::try_emplace(K&& key, Args&&... args)
    -> std::enable_if_t<std::is_constructible<key_type, K&&>::value,
                        std::pair<iterator, bool>> {
  return tree::emplace_key_args(
      key, std::piecewise_construct,
      std::forward_as_tuple(std::forward<K>(key)),
      std::forward_as_tuple(std::forward<Args>(args)...));
}

template <class Key, class Mapped, class Compare>
template <class K, class... Args>
auto flat_map<Key, Mapped, Compare>::try_emplace(const_iterator hint,
                                                 K&& key,
                                                 Args&&... args)
    -> std::enable_if_t<std::is_constructible<key_type, K&&>::value, iterator> {
  return tree::emplace_hint_key_args(
             hint, key, std::piecewise_construct,
             std::forward_as_tuple(std::forward<K>(key)),
             std::forward_as_tuple(std::forward<Args>(args)...))
      .first;
}

// ----------------------------------------------------------------------------
// General operations.

template <class Key, class Mapped, class Compare>
void flat_map<Key, Mapped, Compare>::swap(flat_map& other) noexcept {
  tree::swap(other);
}

}  // namespace base

#endif  // BASE_CONTAINERS_FLAT_MAP_H_
