// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_CONTAINERS_SMALL_MAP_H_
#define BASE_CONTAINERS_SMALL_MAP_H_

#include <stddef.h>

#include <limits>
#include <map>
#include <new>
#include <string>
#include <unordered_map>
#include <utility>

#include "base/logging.h"

namespace {
constexpr size_t kUsingFullMapSentinel = std::numeric_limits<size_t>::max();
}  // namespace

namespace base {

// small_map is a container with a std::map-like interface. It starts out backed
// by an unsorted array but switches to some other container type if it grows
// beyond this fixed size.
//
// Please see //base/containers/README.md for an overview of which container
// to select.
//
// PROS
//
//  - Good memory locality and low overhead for smaller maps.
//  - Handles large maps without the degenerate performance of flat_map.
//
// CONS
//
//  - Larger code size than the alternatives.
//
// IMPORTANT NOTES
//
//  - Iterators are invalidated across mutations.
//
// DETAILS
//
// base::small_map will pick up the comparator from the underlying map type. In
// std::map only a "less" operator is defined, which requires us to do two
// comparisons per element when doing the brute-force search in the simple
// array. std::unordered_map has a key_equal function which will be used.
//
// We define default overrides for the common map types to avoid this
// double-compare, but you should be aware of this if you use your own operator<
// for your map and supply yor own version of == to the small_map. You can use
// regular operator== by just doing:
//
//   base::small_map<std::map<MyKey, MyValue>, 4, std::equal_to<KyKey>>
//
//
// USAGE
// -----
//
// NormalMap:  The map type to fall back to. This also defines the key and value
//             types for the small_map.
// kArraySize:  The size of the initial array of results. This will be allocated
//              with the small_map object rather than separately on the heap.
//              Once the map grows beyond this size, the map type will be used
//              instead.
// EqualKey:  A functor which tests two keys for equality. If the wrapped map
//            type has a "key_equal" member (unordered_map does), then that will
//            be used by default. If the wrapped map type has a strict weak
//            ordering "key_compare" (std::map does), that will be used to
//            implement equality by default.
// MapInit: A functor that takes a NormalMap* and uses it to initialize the map.
//          This functor will be called at most once per small_map, when the map
//          exceeds the threshold of kArraySize and we are about to copy values
//          from the array to the map. The functor *must* initialize the
//          NormalMap* argument with placement new, since after it runs we
//          assume that the NormalMap has been initialized.
//
// Example:
//   base::small_map<std::map<string, int>> days;
//   days["sunday"   ] = 0;
//   days["monday"   ] = 1;
//   days["tuesday"  ] = 2;
//   days["wednesday"] = 3;
//   days["thursday" ] = 4;
//   days["friday"   ] = 5;
//   days["saturday" ] = 6;

namespace internal {

template <typename NormalMap>
class small_map_default_init {
 public:
  void operator()(NormalMap* map) const { new (map) NormalMap(); }
};

// has_key_equal<M>::value is true iff there exists a type M::key_equal. This is
// used to dispatch to one of the select_equal_key<> metafunctions below.
template <typename M>
struct has_key_equal {
  typedef char sml;  // "small" is sometimes #defined so we use an abbreviation.
  typedef struct { char dummy[2]; } big;
  // Two functions, one accepts types that have a key_equal member, and one that
  // accepts anything. They each return a value of a different size, so we can
  // determine at compile-time which function would have been called.
  template <typename U> static big test(typename U::key_equal*);
  template <typename> static sml test(...);
  // Determines if M::key_equal exists by looking at the size of the return
  // type of the compiler-chosen test() function.
  static const bool value = (sizeof(test<M>(0)) == sizeof(big));
};
template <typename M> const bool has_key_equal<M>::value;

// Base template used for map types that do NOT have an M::key_equal member,
// e.g., std::map<>. These maps have a strict weak ordering comparator rather
// than an equality functor, so equality will be implemented in terms of that
// comparator.
//
// There's a partial specialization of this template below for map types that do
// have an M::key_equal member.
template <typename M, bool has_key_equal_value>
struct select_equal_key {
  struct equal_key {
    bool operator()(const typename M::key_type& left,
                    const typename M::key_type& right) {
      // Implements equality in terms of a strict weak ordering comparator.
      typename M::key_compare comp;
      return !comp(left, right) && !comp(right, left);
    }
  };
};

// Partial template specialization handles case where M::key_equal exists, e.g.,
// unordered_map<>.
template <typename M>
struct select_equal_key<M, true> {
  typedef typename M::key_equal equal_key;
};

}  // namespace internal

template <typename NormalMap,
          size_t kArraySize = 4,
          typename EqualKey = typename internal::select_equal_key<
              NormalMap,
              internal::has_key_equal<NormalMap>::value>::equal_key,
          typename MapInit = internal::small_map_default_init<NormalMap>>
class small_map {
  static_assert(kArraySize > 0, "Initial size must be greater than 0");
  static_assert(kArraySize != kUsingFullMapSentinel,
                "Initial size out of range");

 public:
  typedef typename NormalMap::key_type key_type;
  typedef typename NormalMap::mapped_type data_type;
  typedef typename NormalMap::mapped_type mapped_type;
  typedef typename NormalMap::value_type value_type;
  typedef EqualKey key_equal;

  small_map() : size_(0), functor_(MapInit()) {}

  explicit small_map(const MapInit& functor) : size_(0), functor_(functor) {}

  // Allow copy-constructor and assignment, since STL allows them too.
  small_map(const small_map& src) {
    // size_ and functor_ are initted in InitFrom()
    InitFrom(src);
  }

  void operator=(const small_map& src) {
    if (&src == this) return;

    // This is not optimal. If src and dest are both using the small array, we
    // could skip the teardown and reconstruct. One problem to be resolved is
    // that the value_type itself is pair<const K, V>, and const K is not
    // assignable.
    Destroy();
    InitFrom(src);
  }

  ~small_map() { Destroy(); }

  class const_iterator;

  class iterator {
   public:
    typedef typename NormalMap::iterator::iterator_category iterator_category;
    typedef typename NormalMap::iterator::value_type value_type;
    typedef typename NormalMap::iterator::difference_type difference_type;
    typedef typename NormalMap::iterator::pointer pointer;
    typedef typename NormalMap::iterator::reference reference;

    inline iterator() : array_iter_(nullptr) {}

    inline iterator& operator++() {
      if (array_iter_ != nullptr) {
        ++array_iter_;
      } else {
        ++map_iter_;
      }
      return *this;
    }

    inline iterator operator++(int /*unused*/) {
      iterator result(*this);
      ++(*this);
      return result;
    }

    inline iterator& operator--() {
      if (array_iter_ != nullptr) {
        --array_iter_;
      } else {
        --map_iter_;
      }
      return *this;
    }

    inline iterator operator--(int /*unused*/) {
      iterator result(*this);
      --(*this);
      return result;
    }

    inline value_type* operator->() const {
      return array_iter_ ? array_iter_ : map_iter_.operator->();
    }

    inline value_type& operator*() const {
      return array_iter_ ? *array_iter_ : *map_iter_;
    }

    inline bool operator==(const iterator& other) const {
      if (array_iter_ != nullptr) {
        return array_iter_ == other.array_iter_;
      } else {
        return other.array_iter_ == nullptr && map_iter_ == other.map_iter_;
      }
    }

    inline bool operator!=(const iterator& other) const {
      return !(*this == other);
    }

    bool operator==(const const_iterator& other) const;
    bool operator!=(const const_iterator& other) const;

   private:
    friend class small_map;
    friend class const_iterator;
    inline explicit iterator(value_type* init) : array_iter_(init) {}
    inline explicit iterator(const typename NormalMap::iterator& init)
        : array_iter_(nullptr), map_iter_(init) {}

    value_type* array_iter_;
    typename NormalMap::iterator map_iter_;
  };

  class const_iterator {
   public:
    typedef typename NormalMap::const_iterator::iterator_category
        iterator_category;
    typedef typename NormalMap::const_iterator::value_type value_type;
    typedef typename NormalMap::const_iterator::difference_type difference_type;
    typedef typename NormalMap::const_iterator::pointer pointer;
    typedef typename NormalMap::const_iterator::reference reference;

    inline const_iterator() : array_iter_(nullptr) {}

    // Non-explicit constructor lets us convert regular iterators to const
    // iterators.
    inline const_iterator(const iterator& other)
        : array_iter_(other.array_iter_), map_iter_(other.map_iter_) {}

    inline const_iterator& operator++() {
      if (array_iter_ != nullptr) {
        ++array_iter_;
      } else {
        ++map_iter_;
      }
      return *this;
    }

    inline const_iterator operator++(int /*unused*/) {
      const_iterator result(*this);
      ++(*this);
      return result;
    }

    inline const_iterator& operator--() {
      if (array_iter_ != nullptr) {
        --array_iter_;
      } else {
        --map_iter_;
      }
      return *this;
    }

    inline const_iterator operator--(int /*unused*/) {
      const_iterator result(*this);
      --(*this);
      return result;
    }

    inline const value_type* operator->() const {
      return array_iter_ ? array_iter_ : map_iter_.operator->();
    }

    inline const value_type& operator*() const {
      return array_iter_ ? *array_iter_ : *map_iter_;
    }

    inline bool operator==(const const_iterator& other) const {
      if (array_iter_ != nullptr) {
        return array_iter_ == other.array_iter_;
      }
      return other.array_iter_ == nullptr && map_iter_ == other.map_iter_;
    }

    inline bool operator!=(const const_iterator& other) const {
      return !(*this == other);
    }

   private:
    friend class small_map;
    inline explicit const_iterator(const value_type* init)
        : array_iter_(init) {}
    inline explicit const_iterator(
        const typename NormalMap::const_iterator& init)
        : array_iter_(nullptr), map_iter_(init) {}

    const value_type* array_iter_;
    typename NormalMap::const_iterator map_iter_;
  };

  iterator find(const key_type& key) {
    key_equal compare;

    if (UsingFullMap()) {
      return iterator(map()->find(key));
    }

    for (size_t i = 0; i < size_; ++i) {
      if (compare(array_[i].first, key)) {
        return iterator(array_ + i);
      }
    }
    return iterator(array_ + size_);
  }

  const_iterator find(const key_type& key) const {
    key_equal compare;

    if (UsingFullMap()) {
      return const_iterator(map()->find(key));
    }

    for (size_t i = 0; i < size_; ++i) {
      if (compare(array_[i].first, key)) {
        return const_iterator(array_ + i);
      }
    }
    return const_iterator(array_ + size_);
  }

  // Invalidates iterators.
  data_type& operator[](const key_type& key) {
    key_equal compare;

    if (UsingFullMap()) {
      return map_[key];
    }

    // Search backwards to favor recently-added elements.
    for (size_t i = size_; i > 0; --i) {
      const size_t index = i - 1;
      if (compare(array_[index].first, key)) {
        return array_[index].second;
      }
    }

    if (size_ == kArraySize) {
      ConvertToRealMap();
      return map_[key];
    }

    DCHECK(size_ < kArraySize);
    new (&array_[size_]) value_type(key, data_type());
    return array_[size_++].second;
  }

  // Invalidates iterators.
  std::pair<iterator, bool> insert(const value_type& x) {
    key_equal compare;

    if (UsingFullMap()) {
      std::pair<typename NormalMap::iterator, bool> ret = map_.insert(x);
      return std::make_pair(iterator(ret.first), ret.second);
    }

    for (size_t i = 0; i < size_; ++i) {
      if (compare(array_[i].first, x.first)) {
        return std::make_pair(iterator(array_ + i), false);
      }
    }

    if (size_ == kArraySize) {
      ConvertToRealMap();  // Invalidates all iterators!
      std::pair<typename NormalMap::iterator, bool> ret = map_.insert(x);
      return std::make_pair(iterator(ret.first), ret.second);
    }

    DCHECK(size_ < kArraySize);
    new (&array_[size_]) value_type(x);
    return std::make_pair(iterator(array_ + size_++), true);
  }

  // Invalidates iterators.
  template <class InputIterator>
  void insert(InputIterator f, InputIterator l) {
    while (f != l) {
      insert(*f);
      ++f;
    }
  }

  // Invalidates iterators.
  template <typename... Args>
  std::pair<iterator, bool> emplace(Args&&... args) {
    key_equal compare;

    if (UsingFullMap()) {
      std::pair<typename NormalMap::iterator, bool> ret =
          map_.emplace(std::forward<Args>(args)...);
      return std::make_pair(iterator(ret.first), ret.second);
    }

    value_type x(std::forward<Args>(args)...);
    for (size_t i = 0; i < size_; ++i) {
      if (compare(array_[i].first, x.first)) {
        return std::make_pair(iterator(array_ + i), false);
      }
    }

    if (size_ == kArraySize) {
      ConvertToRealMap();  // Invalidates all iterators!
      std::pair<typename NormalMap::iterator, bool> ret =
          map_.emplace(std::move(x));
      return std::make_pair(iterator(ret.first), ret.second);
    }

    DCHECK(size_ < kArraySize);
    new (&array_[size_]) value_type(std::move(x));
    return std::make_pair(iterator(array_ + size_++), true);
  }

  iterator begin() {
    return UsingFullMap() ? iterator(map_.begin()) : iterator(array_);
  }

  const_iterator begin() const {
    return UsingFullMap() ? const_iterator(map_.begin())
                          : const_iterator(array_);
  }

  iterator end() {
    return UsingFullMap() ? iterator(map_.end()) : iterator(array_ + size_);
  }

  const_iterator end() const {
    return UsingFullMap() ? const_iterator(map_.end())
                          : const_iterator(array_ + size_);
  }

  void clear() {
    if (UsingFullMap()) {
      map_.~NormalMap();
    } else {
      for (size_t i = 0; i < size_; ++i) {
        array_[i].~value_type();
      }
    }
    size_ = 0;
  }

  // Invalidates iterators. Returns iterator following the last removed element.
  iterator erase(const iterator& position) {
    if (UsingFullMap()) {
      return iterator(map_.erase(position.map_iter_));
    }

    size_t i = position.array_iter_ - array_;
    // TODO(crbug.com/817982): When we have a checked iterator, this CHECK might
    // not be necessary.
    CHECK_LE(i, size_);
    array_[i].~value_type();
    --size_;
    if (i != size_) {
      new (&array_[i]) value_type(std::move(array_[size_]));
      array_[size_].~value_type();
      return iterator(array_ + i);
    }
    return end();
  }

  size_t erase(const key_type& key) {
    iterator iter = find(key);
    if (iter == end()) {
      return 0;
    }
    erase(iter);
    return 1;
  }

  size_t count(const key_type& key) const {
    return (find(key) == end()) ? 0 : 1;
  }

  size_t size() const { return UsingFullMap() ? map_.size() : size_; }

  bool empty() const { return UsingFullMap() ? map_.empty() : size_ == 0; }

  // Returns true if we have fallen back to using the underlying map
  // representation.
  bool UsingFullMap() const { return size_ == kUsingFullMapSentinel; }

  inline NormalMap* map() {
    CHECK(UsingFullMap());
    return &map_;
  }

  inline const NormalMap* map() const {
    CHECK(UsingFullMap());
    return &map_;
  }

 private:
  // When `size_ == kUsingFullMapSentinel`, we have switched storage strategies
  // from `array_[kArraySize] to `NormalMap map_`. See ConvertToRealMap and
  // UsingFullMap.
  size_t size_;

  MapInit functor_;

  // We want to call constructors and destructors manually, but we don't want
  // to allocate and deallocate the memory used for them separately. Since
  // array_ and map_ are mutually exclusive, we'll put them in a union.
  union {
    value_type array_[kArraySize];
    NormalMap map_;
  };

  void ConvertToRealMap() {
    // Storage for the elements in the temporary array. This is intentionally
    // declared as a union to avoid having to default-construct |kArraySize|
    // elements, only to move construct over them in the initial loop.
    union Storage {
      Storage() {}
      ~Storage() {}
      value_type array[kArraySize];
    } temp;

    // Move the current elements into a temporary array.
    for (size_t i = 0; i < kArraySize; ++i) {
      new (&temp.array[i]) value_type(std::move(array_[i]));
      array_[i].~value_type();
    }

    // Initialize the map.
    size_ = kUsingFullMapSentinel;
    functor_(&map_);

    // Insert elements into it.
    for (size_t i = 0; i < kArraySize; ++i) {
      map_.insert(std::move(temp.array[i]));
      temp.array[i].~value_type();
    }
  }

  // Helpers for constructors and destructors.
  void InitFrom(const small_map& src) {
    functor_ = src.functor_;
    size_ = src.size_;
    if (src.UsingFullMap()) {
      functor_(&map_);
      map_ = src.map_;
    } else {
      for (size_t i = 0; i < size_; ++i) {
        new (&array_[i]) value_type(src.array_[i]);
      }
    }
  }

  void Destroy() {
    if (UsingFullMap()) {
      map_.~NormalMap();
    } else {
      for (size_t i = 0; i < size_; ++i) {
        array_[i].~value_type();
      }
    }
  }
};

template <typename NormalMap,
          size_t kArraySize,
          typename EqualKey,
          typename Functor>
inline bool small_map<NormalMap, kArraySize, EqualKey, Functor>::iterator::
operator==(const const_iterator& other) const {
  return other == *this;
}

template <typename NormalMap,
          size_t kArraySize,
          typename EqualKey,
          typename Functor>
inline bool small_map<NormalMap, kArraySize, EqualKey, Functor>::iterator::
operator!=(const const_iterator& other) const {
  return other != *this;
}

}  // namespace base

#endif  // BASE_CONTAINERS_SMALL_MAP_H_
