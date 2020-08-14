// Copyright (c) 2011 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

// Derived from google3/util/gtl/stl_util.h

#ifndef BASE_STL_UTIL_H_
#define BASE_STL_UTIL_H_

#include <algorithm>
#include <deque>
#include <forward_list>
#include <functional>
#include <initializer_list>
#include <iterator>
#include <list>
#include <map>
#include <set>
#include <string>
#include <type_traits>
#include <unordered_map>
#include <unordered_set>
#include <utility>
#include <vector>

#include "base/logging.h"
#include "base/optional.h"
#include "base/template_util.h"

namespace base {

namespace internal {

// Calls erase on iterators of matching elements and returns the number of
// removed elements.
template <typename Container, typename Predicate>
size_t IterateAndEraseIf(Container& container, Predicate pred) {
  size_t old_size = container.size();
  for (auto it = container.begin(), last = container.end(); it != last;) {
    if (pred(*it))
      it = container.erase(it);
    else
      ++it;
  }
  return old_size - container.size();
}

template <typename Iter>
constexpr bool IsRandomAccessIter =
    std::is_same<typename std::iterator_traits<Iter>::iterator_category,
                 std::random_access_iterator_tag>::value;

// Utility type traits used for specializing base::Contains() below.
template <typename Container, typename Element, typename = void>
struct HasFindWithNpos : std::false_type {};

template <typename Container, typename Element>
struct HasFindWithNpos<
    Container,
    Element,
    void_t<decltype(std::declval<const Container&>().find(
                        std::declval<const Element&>()) != Container::npos)>>
    : std::true_type {};

template <typename Container, typename Element, typename = void>
struct HasFindWithEnd : std::false_type {};

template <typename Container, typename Element>
struct HasFindWithEnd<Container,
                      Element,
                      void_t<decltype(std::declval<const Container&>().find(
                                          std::declval<const Element&>()) !=
                                      std::declval<const Container&>().end())>>
    : std::true_type {};

template <typename Container, typename Element, typename = void>
struct HasContains : std::false_type {};

template <typename Container, typename Element>
struct HasContains<Container,
                   Element,
                   void_t<decltype(std::declval<const Container&>().contains(
                       std::declval<const Element&>()))>> : std::true_type {};

}  // namespace internal

// C++14 implementation of C++17's std::size():
// http://en.cppreference.com/w/cpp/iterator/size
template <typename Container>
constexpr auto size(const Container& c) -> decltype(c.size()) {
  return c.size();
}

template <typename T, size_t N>
constexpr size_t size(const T (&array)[N]) noexcept {
  return N;
}

// C++14 implementation of C++17's std::empty():
// http://en.cppreference.com/w/cpp/iterator/empty
template <typename Container>
constexpr auto empty(const Container& c) -> decltype(c.empty()) {
  return c.empty();
}

template <typename T, size_t N>
constexpr bool empty(const T (&array)[N]) noexcept {
  return false;
}

template <typename T>
constexpr bool empty(std::initializer_list<T> il) noexcept {
  return il.size() == 0;
}

// C++14 implementation of C++17's std::data():
// http://en.cppreference.com/w/cpp/iterator/data
template <typename Container>
constexpr auto data(Container& c) -> decltype(c.data()) {
  return c.data();
}

// std::basic_string::data() had no mutable overload prior to C++17 [1].
// Hence this overload is provided.
// Note: str[0] is safe even for empty strings, as they are guaranteed to be
// null-terminated [2].
//
// [1] http://en.cppreference.com/w/cpp/string/basic_string/data
// [2] http://en.cppreference.com/w/cpp/string/basic_string/operator_at
template <typename CharT, typename Traits, typename Allocator>
CharT* data(std::basic_string<CharT, Traits, Allocator>& str) {
  return std::addressof(str[0]);
}

template <typename Container>
constexpr auto data(const Container& c) -> decltype(c.data()) {
  return c.data();
}

template <typename T, size_t N>
constexpr T* data(T (&array)[N]) noexcept {
  return array;
}

template <typename T>
constexpr const T* data(std::initializer_list<T> il) noexcept {
  return il.begin();
}

// std::array::data() was not constexpr prior to C++17 [1].
// Hence these overloads are provided.
//
// [1] https://en.cppreference.com/w/cpp/container/array/data
template <typename T, size_t N>
constexpr T* data(std::array<T, N>& array) noexcept {
  return !array.empty() ? &array[0] : nullptr;
}

template <typename T, size_t N>
constexpr const T* data(const std::array<T, N>& array) noexcept {
  return !array.empty() ? &array[0] : nullptr;
}

// C++14 implementation of C++17's std::as_const():
// https://en.cppreference.com/w/cpp/utility/as_const
template <typename T>
constexpr std::add_const_t<T>& as_const(T& t) noexcept {
  return t;
}

template <typename T>
void as_const(const T&& t) = delete;

// Returns a const reference to the underlying container of a container adapter.
// Works for std::priority_queue, std::queue, and std::stack.
template <class A>
const typename A::container_type& GetUnderlyingContainer(const A& adapter) {
  struct ExposedAdapter : A {
    using A::c;
  };
  return adapter.*&ExposedAdapter::c;
}

// Clears internal memory of an STL object.
// STL clear()/reserve(0) does not always free internal memory allocated
// This function uses swap/destructor to ensure the internal memory is freed.
template<class T>
void STLClearObject(T* obj) {
  T tmp;
  tmp.swap(*obj);
  // Sometimes "T tmp" allocates objects with memory (arena implementation?).
  // Hence using additional reserve(0) even if it doesn't always work.
  obj->reserve(0);
}

// Counts the number of instances of val in a container.
template <typename Container, typename T>
typename std::iterator_traits<
    typename Container::const_iterator>::difference_type
STLCount(const Container& container, const T& val) {
  return std::count(container.begin(), container.end(), val);
}

// General purpose implementation to check if |container| contains |value|.
template <typename Container,
          typename Value,
          std::enable_if_t<
              !internal::HasFindWithNpos<Container, Value>::value &&
              !internal::HasFindWithEnd<Container, Value>::value &&
              !internal::HasContains<Container, Value>::value>* = nullptr>
bool Contains(const Container& container, const Value& value) {
  using std::begin;
  using std::end;
  return std::find(begin(container), end(container), value) != end(container);
}

// Specialized Contains() implementation for when |container| has a find()
// member function and a static npos member, but no contains() member function.
template <typename Container,
          typename Value,
          std::enable_if_t<internal::HasFindWithNpos<Container, Value>::value &&
                           !internal::HasContains<Container, Value>::value>* =
              nullptr>
bool Contains(const Container& container, const Value& value) {
  return container.find(value) != Container::npos;
}

// Specialized Contains() implementation for when |container| has a find()
// and end() member function, but no contains() member function.
template <typename Container,
          typename Value,
          std::enable_if_t<internal::HasFindWithEnd<Container, Value>::value &&
                           !internal::HasContains<Container, Value>::value>* =
              nullptr>
bool Contains(const Container& container, const Value& value) {
  return container.find(value) != container.end();
}

// Specialized Contains() implementation for when |container| has a contains()
// member function.
template <
    typename Container,
    typename Value,
    std::enable_if_t<internal::HasContains<Container, Value>::value>* = nullptr>
bool Contains(const Container& container, const Value& value) {
  return container.contains(value);
}

// O(1) implementation of const casting an iterator for any sequence,
// associative or unordered associative container in the STL.
//
// Reference: https://stackoverflow.com/a/10669041
template <typename Container,
          typename ConstIter,
          std::enable_if_t<!internal::IsRandomAccessIter<ConstIter>>* = nullptr>
constexpr auto ConstCastIterator(Container& c, ConstIter it) {
  return c.erase(it, it);
}

// Explicit overload for std::forward_list where erase() is named erase_after().
template <typename T, typename Allocator>
constexpr auto ConstCastIterator(
    std::forward_list<T, Allocator>& c,
    typename std::forward_list<T, Allocator>::const_iterator it) {
// The erase_after(it, it) trick used below does not work for libstdc++ [1],
// thus we need a different way.
// TODO(crbug.com/972541): Remove this workaround once libstdc++ is fixed on all
// platforms.
//
// [1] https://gcc.gnu.org/bugzilla/show_bug.cgi?id=90857
#if defined(__GLIBCXX__)
  return c.insert_after(it, {});
#else
  return c.erase_after(it, it);
#endif
}

// Specialized O(1) const casting for random access iterators. This is
// necessary, because erase() is either not available (e.g. array-like
// containers), or has O(n) complexity (e.g. std::deque or std::vector).
template <typename Container,
          typename ConstIter,
          std::enable_if_t<internal::IsRandomAccessIter<ConstIter>>* = nullptr>
constexpr auto ConstCastIterator(Container& c, ConstIter it) {
  using std::begin;
  using std::cbegin;
  return begin(c) + (it - cbegin(c));
}

namespace internal {

template <typename Map, typename Key, typename Value>
std::pair<typename Map::iterator, bool> InsertOrAssignImpl(Map& map,
                                                           Key&& key,
                                                           Value&& value) {
  auto lower = map.lower_bound(key);
  if (lower != map.end() && !map.key_comp()(key, lower->first)) {
    // key already exists, perform assignment.
    lower->second = std::forward<Value>(value);
    return {lower, false};
  }

  // key did not yet exist, insert it.
  return {map.emplace_hint(lower, std::forward<Key>(key),
                           std::forward<Value>(value)),
          true};
}

template <typename Map, typename Key, typename Value>
typename Map::iterator InsertOrAssignImpl(Map& map,
                                          typename Map::const_iterator hint,
                                          Key&& key,
                                          Value&& value) {
  auto&& key_comp = map.key_comp();
  if ((hint == map.begin() || key_comp(std::prev(hint)->first, key))) {
    if (hint == map.end() || key_comp(key, hint->first)) {
      // *(hint - 1) < key < *hint => key did not exist and hint is correct.
      return map.emplace_hint(hint, std::forward<Key>(key),
                              std::forward<Value>(value));
    }

    if (!key_comp(hint->first, key)) {
      // key == *hint => key already exists and hint is correct.
      auto mutable_hint = ConstCastIterator(map, hint);
      mutable_hint->second = std::forward<Value>(value);
      return mutable_hint;
    }
  }

  // hint was not helpful, dispatch to hintless version.
  return InsertOrAssignImpl(map, std::forward<Key>(key),
                            std::forward<Value>(value))
      .first;
}

template <typename Map, typename Key, typename... Args>
std::pair<typename Map::iterator, bool> TryEmplaceImpl(Map& map,
                                                       Key&& key,
                                                       Args&&... args) {
  auto lower = map.lower_bound(key);
  if (lower != map.end() && !map.key_comp()(key, lower->first)) {
    // key already exists, do nothing.
    return {lower, false};
  }

  // key did not yet exist, insert it.
  return {map.emplace_hint(lower, std::piecewise_construct,
                           std::forward_as_tuple(std::forward<Key>(key)),
                           std::forward_as_tuple(std::forward<Args>(args)...)),
          true};
}

template <typename Map, typename Key, typename... Args>
typename Map::iterator TryEmplaceImpl(Map& map,
                                      typename Map::const_iterator hint,
                                      Key&& key,
                                      Args&&... args) {
  auto&& key_comp = map.key_comp();
  if ((hint == map.begin() || key_comp(std::prev(hint)->first, key))) {
    if (hint == map.end() || key_comp(key, hint->first)) {
      // *(hint - 1) < key < *hint => key did not exist and hint is correct.
      return map.emplace_hint(
          hint, std::piecewise_construct,
          std::forward_as_tuple(std::forward<Key>(key)),
          std::forward_as_tuple(std::forward<Args>(args)...));
    }

    if (!key_comp(hint->first, key)) {
      // key == *hint => no-op, return correct hint.
      return ConstCastIterator(map, hint);
    }
  }

  // hint was not helpful, dispatch to hintless version.
  return TryEmplaceImpl(map, std::forward<Key>(key),
                        std::forward<Args>(args)...)
      .first;
}

}  // namespace internal

// Implementation of C++17's std::map::insert_or_assign as a free function.
template <typename Map, typename Value>
std::pair<typename Map::iterator, bool>
InsertOrAssign(Map& map, const typename Map::key_type& key, Value&& value) {
  return internal::InsertOrAssignImpl(map, key, std::forward<Value>(value));
}

template <typename Map, typename Value>
std::pair<typename Map::iterator, bool>
InsertOrAssign(Map& map, typename Map::key_type&& key, Value&& value) {
  return internal::InsertOrAssignImpl(map, std::move(key),
                                      std::forward<Value>(value));
}

// Implementation of C++17's std::map::insert_or_assign with hint as a free
// function.
template <typename Map, typename Value>
typename Map::iterator InsertOrAssign(Map& map,
                                      typename Map::const_iterator hint,
                                      const typename Map::key_type& key,
                                      Value&& value) {
  return internal::InsertOrAssignImpl(map, hint, key,
                                      std::forward<Value>(value));
}

template <typename Map, typename Value>
typename Map::iterator InsertOrAssign(Map& map,
                                      typename Map::const_iterator hint,
                                      typename Map::key_type&& key,
                                      Value&& value) {
  return internal::InsertOrAssignImpl(map, hint, std::move(key),
                                      std::forward<Value>(value));
}

// Implementation of C++17's std::map::try_emplace as a free function.
template <typename Map, typename... Args>
std::pair<typename Map::iterator, bool>
TryEmplace(Map& map, const typename Map::key_type& key, Args&&... args) {
  return internal::TryEmplaceImpl(map, key, std::forward<Args>(args)...);
}

template <typename Map, typename... Args>
std::pair<typename Map::iterator, bool> TryEmplace(Map& map,
                                                   typename Map::key_type&& key,
                                                   Args&&... args) {
  return internal::TryEmplaceImpl(map, std::move(key),
                                  std::forward<Args>(args)...);
}

// Implementation of C++17's std::map::try_emplace with hint as a free
// function.
template <typename Map, typename... Args>
typename Map::iterator TryEmplace(Map& map,
                                  typename Map::const_iterator hint,
                                  const typename Map::key_type& key,
                                  Args&&... args) {
  return internal::TryEmplaceImpl(map, hint, key, std::forward<Args>(args)...);
}

template <typename Map, typename... Args>
typename Map::iterator TryEmplace(Map& map,
                                  typename Map::const_iterator hint,
                                  typename Map::key_type&& key,
                                  Args&&... args) {
  return internal::TryEmplaceImpl(map, hint, std::move(key),
                                  std::forward<Args>(args)...);
}

// Returns true if the container is sorted.
template <typename Container>
bool STLIsSorted(const Container& cont) {
  return std::is_sorted(std::begin(cont), std::end(cont));
}

// Returns a new ResultType containing the difference of two sorted containers.
template <typename ResultType, typename Arg1, typename Arg2>
ResultType STLSetDifference(const Arg1& a1, const Arg2& a2) {
  DCHECK(STLIsSorted(a1));
  DCHECK(STLIsSorted(a2));
  ResultType difference;
  std::set_difference(a1.begin(), a1.end(),
                      a2.begin(), a2.end(),
                      std::inserter(difference, difference.end()));
  return difference;
}

// Returns a new ResultType containing the union of two sorted containers.
template <typename ResultType, typename Arg1, typename Arg2>
ResultType STLSetUnion(const Arg1& a1, const Arg2& a2) {
  DCHECK(STLIsSorted(a1));
  DCHECK(STLIsSorted(a2));
  ResultType result;
  std::set_union(a1.begin(), a1.end(),
                 a2.begin(), a2.end(),
                 std::inserter(result, result.end()));
  return result;
}

// Returns a new ResultType containing the intersection of two sorted
// containers.
template <typename ResultType, typename Arg1, typename Arg2>
ResultType STLSetIntersection(const Arg1& a1, const Arg2& a2) {
  DCHECK(STLIsSorted(a1));
  DCHECK(STLIsSorted(a2));
  ResultType result;
  std::set_intersection(a1.begin(), a1.end(),
                        a2.begin(), a2.end(),
                        std::inserter(result, result.end()));
  return result;
}

// Returns true if the sorted container |a1| contains all elements of the sorted
// container |a2|.
template <typename Arg1, typename Arg2>
bool STLIncludes(const Arg1& a1, const Arg2& a2) {
  DCHECK(STLIsSorted(a1));
  DCHECK(STLIsSorted(a2));
  return std::includes(a1.begin(), a1.end(),
                       a2.begin(), a2.end());
}

// Erase/EraseIf are based on C++20's uniform container erasure API:
// - https://eel.is/c++draft/libraryindex#:erase
// - https://eel.is/c++draft/libraryindex#:erase_if
// They provide a generic way to erase elements from a container.
// The functions here implement these for the standard containers until those
// functions are available in the C++ standard.
// For Chromium containers overloads should be defined in their own headers
// (like standard containers).
// Note: there is no std::erase for standard associative containers so we don't
// have it either.

template <typename CharT, typename Traits, typename Allocator, typename Value>
size_t Erase(std::basic_string<CharT, Traits, Allocator>& container,
             const Value& value) {
  auto it = std::remove(container.begin(), container.end(), value);
  size_t removed = std::distance(it, container.end());
  container.erase(it, container.end());
  return removed;
}

template <typename CharT, typename Traits, typename Allocator, class Predicate>
size_t EraseIf(std::basic_string<CharT, Traits, Allocator>& container,
               Predicate pred) {
  auto it = std::remove_if(container.begin(), container.end(), pred);
  size_t removed = std::distance(it, container.end());
  container.erase(it, container.end());
  return removed;
}

template <class T, class Allocator, class Value>
size_t Erase(std::deque<T, Allocator>& container, const Value& value) {
  auto it = std::remove(container.begin(), container.end(), value);
  size_t removed = std::distance(it, container.end());
  container.erase(it, container.end());
  return removed;
}

template <class T, class Allocator, class Predicate>
size_t EraseIf(std::deque<T, Allocator>& container, Predicate pred) {
  auto it = std::remove_if(container.begin(), container.end(), pred);
  size_t removed = std::distance(it, container.end());
  container.erase(it, container.end());
  return removed;
}

template <class T, class Allocator, class Value>
size_t Erase(std::vector<T, Allocator>& container, const Value& value) {
  auto it = std::remove(container.begin(), container.end(), value);
  size_t removed = std::distance(it, container.end());
  container.erase(it, container.end());
  return removed;
}

template <class T, class Allocator, class Predicate>
size_t EraseIf(std::vector<T, Allocator>& container, Predicate pred) {
  auto it = std::remove_if(container.begin(), container.end(), pred);
  size_t removed = std::distance(it, container.end());
  container.erase(it, container.end());
  return removed;
}

template <class T, class Allocator, class Value>
size_t Erase(std::forward_list<T, Allocator>& container, const Value& value) {
  // Unlike std::forward_list::remove, this function template accepts
  // heterogeneous types and does not force a conversion to the container's
  // value type before invoking the == operator.
  return EraseIf(container, [&](const T& cur) { return cur == value; });
}

template <class T, class Allocator, class Predicate>
size_t EraseIf(std::forward_list<T, Allocator>& container, Predicate pred) {
  // Note: std::forward_list does not have a size() API, thus we need to use the
  // O(n) std::distance work-around. However, given that EraseIf is O(n)
  // already, this should not make a big difference.
  size_t old_size = std::distance(container.begin(), container.end());
  container.remove_if(pred);
  return old_size - std::distance(container.begin(), container.end());
}

template <class T, class Allocator, class Value>
size_t Erase(std::list<T, Allocator>& container, const Value& value) {
  // Unlike std::list::remove, this function template accepts heterogeneous
  // types and does not force a conversion to the container's value type before
  // invoking the == operator.
  return EraseIf(container, [&](const T& cur) { return cur == value; });
}

template <class T, class Allocator, class Predicate>
size_t EraseIf(std::list<T, Allocator>& container, Predicate pred) {
  size_t old_size = container.size();
  container.remove_if(pred);
  return old_size - container.size();
}

template <class Key, class T, class Compare, class Allocator, class Predicate>
size_t EraseIf(std::map<Key, T, Compare, Allocator>& container,
               Predicate pred) {
  return internal::IterateAndEraseIf(container, pred);
}

template <class Key, class T, class Compare, class Allocator, class Predicate>
size_t EraseIf(std::multimap<Key, T, Compare, Allocator>& container,
               Predicate pred) {
  return internal::IterateAndEraseIf(container, pred);
}

template <class Key, class Compare, class Allocator, class Predicate>
size_t EraseIf(std::set<Key, Compare, Allocator>& container, Predicate pred) {
  return internal::IterateAndEraseIf(container, pred);
}

template <class Key, class Compare, class Allocator, class Predicate>
size_t EraseIf(std::multiset<Key, Compare, Allocator>& container,
               Predicate pred) {
  return internal::IterateAndEraseIf(container, pred);
}

template <class Key,
          class T,
          class Hash,
          class KeyEqual,
          class Allocator,
          class Predicate>
size_t EraseIf(std::unordered_map<Key, T, Hash, KeyEqual, Allocator>& container,
               Predicate pred) {
  return internal::IterateAndEraseIf(container, pred);
}

template <class Key,
          class T,
          class Hash,
          class KeyEqual,
          class Allocator,
          class Predicate>
size_t EraseIf(
    std::unordered_multimap<Key, T, Hash, KeyEqual, Allocator>& container,
    Predicate pred) {
  return internal::IterateAndEraseIf(container, pred);
}

template <class Key,
          class Hash,
          class KeyEqual,
          class Allocator,
          class Predicate>
size_t EraseIf(std::unordered_set<Key, Hash, KeyEqual, Allocator>& container,
               Predicate pred) {
  return internal::IterateAndEraseIf(container, pred);
}

template <class Key,
          class Hash,
          class KeyEqual,
          class Allocator,
          class Predicate>
size_t EraseIf(
    std::unordered_multiset<Key, Hash, KeyEqual, Allocator>& container,
    Predicate pred) {
  return internal::IterateAndEraseIf(container, pred);
}

// A helper class to be used as the predicate with |EraseIf| to implement
// in-place set intersection. Helps implement the algorithm of going through
// each container an element at a time, erasing elements from the first
// container if they aren't in the second container. Requires each container be
// sorted. Note that the logic below appears inverted since it is returning
// whether an element should be erased.
template <class Collection>
class IsNotIn {
 public:
  explicit IsNotIn(const Collection& collection)
      : i_(collection.begin()), end_(collection.end()) {}

  bool operator()(const typename Collection::value_type& x) {
    while (i_ != end_ && *i_ < x)
      ++i_;
    if (i_ == end_)
      return true;
    if (*i_ == x) {
      ++i_;
      return false;
    }
    return true;
  }

 private:
  typename Collection::const_iterator i_;
  const typename Collection::const_iterator end_;
};

// Helper for returning the optional value's address, or nullptr.
template <class T>
T* OptionalOrNullptr(base::Optional<T>& optional) {
  return optional.has_value() ? &optional.value() : nullptr;
}

template <class T>
const T* OptionalOrNullptr(const base::Optional<T>& optional) {
  return optional.has_value() ? &optional.value() : nullptr;
}

}  // namespace base

#endif  // BASE_STL_UTIL_H_
