// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_VALUE_ITERATORS_H_
#define BASE_VALUE_ITERATORS_H_

#include <memory>
#include <string>
#include <utility>

#include "base/base_export.h"
#include "base/containers/flat_map.h"
#include "base/macros.h"

namespace base {

class Value;

namespace detail {

using DictStorage = base::flat_map<std::string, std::unique_ptr<Value>>;

// This iterator closely resembles DictStorage::iterator, with one
// important exception. It abstracts the underlying unique_ptr away, meaning its
// value_type is std::pair<const std::string, Value>. It's reference type is a
// std::pair<const std::string&, Value&>, so that callers have read-write
// access without incurring a copy.
class BASE_EXPORT dict_iterator {
 public:
  using difference_type = DictStorage::iterator::difference_type;
  using value_type = std::pair<const std::string, Value>;
  using reference = std::pair<const std::string&, Value&>;
  using iterator_category = std::bidirectional_iterator_tag;

  class pointer {
   public:
    explicit pointer(const reference& ref);
    pointer(const pointer& ptr);
    pointer& operator=(const pointer& ptr) = delete;

    reference* operator->() { return &ref_; }

   private:
    reference ref_;
  };

  explicit dict_iterator(DictStorage::iterator dict_iter);
  dict_iterator(const dict_iterator& dict_iter);
  dict_iterator& operator=(const dict_iterator& dict_iter);
  ~dict_iterator();

  reference operator*();
  pointer operator->();

  dict_iterator& operator++();
  dict_iterator operator++(int);
  dict_iterator& operator--();
  dict_iterator operator--(int);

  BASE_EXPORT friend bool operator==(const dict_iterator& lhs,
                                     const dict_iterator& rhs);
  BASE_EXPORT friend bool operator!=(const dict_iterator& lhs,
                                     const dict_iterator& rhs);

 private:
  DictStorage::iterator dict_iter_;
};

// This iterator closely resembles DictStorage::const_iterator, with one
// important exception. It abstracts the underlying unique_ptr away, meaning its
// value_type is std::pair<const std::string, Value>. It's reference type is a
// std::pair<const std::string&, const Value&>, so that callers have read-only
// access without incurring a copy.
class BASE_EXPORT const_dict_iterator {
 public:
  using difference_type = DictStorage::const_iterator::difference_type;
  using value_type = std::pair<const std::string, Value>;
  using reference = std::pair<const std::string&, const Value&>;
  using iterator_category = std::bidirectional_iterator_tag;

  class pointer {
   public:
    explicit pointer(const reference& ref);
    pointer(const pointer& ptr);
    pointer& operator=(const pointer& ptr) = delete;

    const reference* operator->() const { return &ref_; }

   private:
    const reference ref_;
  };

  explicit const_dict_iterator(DictStorage::const_iterator dict_iter);
  const_dict_iterator(const const_dict_iterator& dict_iter);
  const_dict_iterator& operator=(const const_dict_iterator& dict_iter);
  ~const_dict_iterator();

  reference operator*() const;
  pointer operator->() const;

  const_dict_iterator& operator++();
  const_dict_iterator operator++(int);
  const_dict_iterator& operator--();
  const_dict_iterator operator--(int);

  BASE_EXPORT friend bool operator==(const const_dict_iterator& lhs,
                                     const const_dict_iterator& rhs);
  BASE_EXPORT friend bool operator!=(const const_dict_iterator& lhs,
                                     const const_dict_iterator& rhs);

 private:
  DictStorage::const_iterator dict_iter_;
};

// This class wraps the various |begin| and |end| methods of the underlying
// DictStorage in dict_iterators and const_dict_iterators. This allows callers
// to use this class for easy iteration over the underlying values, granting
// them either read-only or read-write access, depending on the
// const-qualification.
class BASE_EXPORT dict_iterator_proxy {
 public:
  using key_type = DictStorage::key_type;
  using mapped_type = DictStorage::mapped_type::element_type;
  using value_type = std::pair<key_type, mapped_type>;
  using key_compare = DictStorage::key_compare;
  using size_type = DictStorage::size_type;
  using difference_type = DictStorage::difference_type;

  using iterator = dict_iterator;
  using const_iterator = const_dict_iterator;
  using reverse_iterator = std::reverse_iterator<iterator>;
  using const_reverse_iterator = std::reverse_iterator<const_iterator>;

  explicit dict_iterator_proxy(DictStorage* storage);

  iterator begin();
  const_iterator begin() const;
  iterator end();
  const_iterator end() const;

  reverse_iterator rbegin();
  const_reverse_iterator rbegin() const;
  reverse_iterator rend();
  const_reverse_iterator rend() const;

  const_dict_iterator cbegin() const;
  const_dict_iterator cend() const;
  const_reverse_iterator crbegin() const;
  const_reverse_iterator crend() const;

 private:
  DictStorage* storage_;
};

// This class wraps the various const |begin| and |end| methods of the
// underlying DictStorage in const_dict_iterators. This allows callers to use
// this class for easy iteration over the underlying values, granting them
// either read-only access.
class BASE_EXPORT const_dict_iterator_proxy {
 public:
  using key_type = const DictStorage::key_type;
  using mapped_type = const DictStorage::mapped_type::element_type;
  using value_type = std::pair<key_type, mapped_type>;
  using key_compare = DictStorage::key_compare;
  using size_type = DictStorage::size_type;
  using difference_type = DictStorage::difference_type;

  using iterator = const_dict_iterator;
  using const_iterator = const_dict_iterator;
  using reverse_iterator = std::reverse_iterator<iterator>;
  using const_reverse_iterator = std::reverse_iterator<const_iterator>;

  explicit const_dict_iterator_proxy(const DictStorage* storage);

  const_iterator begin() const;
  const_iterator end() const;

  const_reverse_iterator rbegin() const;
  const_reverse_iterator rend() const;

  const_iterator cbegin() const;
  const_iterator cend() const;
  const_reverse_iterator crbegin() const;
  const_reverse_iterator crend() const;

 private:
  const DictStorage* storage_;
};
}  // namespace detail

}  // namespace base

#endif  // BASE_VALUE_ITERATORS_H_
