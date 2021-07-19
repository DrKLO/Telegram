// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/value_iterators.h"

namespace base {

namespace detail {

// ----------------------------------------------------------------------------
// dict_iterator.

dict_iterator::pointer::pointer(const reference& ref) : ref_(ref) {}

dict_iterator::pointer::pointer(const pointer& ptr) = default;

dict_iterator::dict_iterator(DictStorage::iterator dict_iter)
    : dict_iter_(dict_iter) {}

dict_iterator::dict_iterator(const dict_iterator& dict_iter) = default;

dict_iterator& dict_iterator::operator=(const dict_iterator& dict_iter) =
    default;

dict_iterator::~dict_iterator() = default;

dict_iterator::reference dict_iterator::operator*() {
  return {dict_iter_->first, *dict_iter_->second};
}

dict_iterator::pointer dict_iterator::operator->() {
  return pointer(operator*());
}

dict_iterator& dict_iterator::operator++() {
  ++dict_iter_;
  return *this;
}

dict_iterator dict_iterator::operator++(int) {
  dict_iterator tmp(*this);
  ++dict_iter_;
  return tmp;
}

dict_iterator& dict_iterator::operator--() {
  --dict_iter_;
  return *this;
}

dict_iterator dict_iterator::operator--(int) {
  dict_iterator tmp(*this);
  --dict_iter_;
  return tmp;
}

bool operator==(const dict_iterator& lhs, const dict_iterator& rhs) {
  return lhs.dict_iter_ == rhs.dict_iter_;
}

bool operator!=(const dict_iterator& lhs, const dict_iterator& rhs) {
  return !(lhs == rhs);
}

// ----------------------------------------------------------------------------
// const_dict_iterator.

const_dict_iterator::pointer::pointer(const reference& ref) : ref_(ref) {}

const_dict_iterator::pointer::pointer(const pointer& ptr) = default;

const_dict_iterator::const_dict_iterator(DictStorage::const_iterator dict_iter)
    : dict_iter_(dict_iter) {}

const_dict_iterator::const_dict_iterator(const const_dict_iterator& dict_iter) =
    default;

const_dict_iterator& const_dict_iterator::operator=(
    const const_dict_iterator& dict_iter) = default;

const_dict_iterator::~const_dict_iterator() = default;

const_dict_iterator::reference const_dict_iterator::operator*() const {
  return {dict_iter_->first, *dict_iter_->second};
}

const_dict_iterator::pointer const_dict_iterator::operator->() const {
  return pointer(operator*());
}

const_dict_iterator& const_dict_iterator::operator++() {
  ++dict_iter_;
  return *this;
}

const_dict_iterator const_dict_iterator::operator++(int) {
  const_dict_iterator tmp(*this);
  ++dict_iter_;
  return tmp;
}

const_dict_iterator& const_dict_iterator::operator--() {
  --dict_iter_;
  return *this;
}

const_dict_iterator const_dict_iterator::operator--(int) {
  const_dict_iterator tmp(*this);
  --dict_iter_;
  return tmp;
}

bool operator==(const const_dict_iterator& lhs,
                const const_dict_iterator& rhs) {
  return lhs.dict_iter_ == rhs.dict_iter_;
}

bool operator!=(const const_dict_iterator& lhs,
                const const_dict_iterator& rhs) {
  return !(lhs == rhs);
}

// ----------------------------------------------------------------------------
// dict_iterator_proxy.

dict_iterator_proxy::dict_iterator_proxy(DictStorage* storage)
    : storage_(storage) {}

dict_iterator_proxy::iterator dict_iterator_proxy::begin() {
  return iterator(storage_->begin());
}

dict_iterator_proxy::const_iterator dict_iterator_proxy::begin() const {
  return const_iterator(storage_->begin());
}

dict_iterator_proxy::iterator dict_iterator_proxy::end() {
  return iterator(storage_->end());
}

dict_iterator_proxy::const_iterator dict_iterator_proxy::end() const {
  return const_iterator(storage_->end());
}

dict_iterator_proxy::reverse_iterator dict_iterator_proxy::rbegin() {
  return reverse_iterator(end());
}

dict_iterator_proxy::const_reverse_iterator dict_iterator_proxy::rbegin()
    const {
  return const_reverse_iterator(end());
}

dict_iterator_proxy::reverse_iterator dict_iterator_proxy::rend() {
  return reverse_iterator(begin());
}

dict_iterator_proxy::const_reverse_iterator dict_iterator_proxy::rend() const {
  return const_reverse_iterator(begin());
}

dict_iterator_proxy::const_iterator dict_iterator_proxy::cbegin() const {
  return const_iterator(begin());
}

dict_iterator_proxy::const_iterator dict_iterator_proxy::cend() const {
  return const_iterator(end());
}

dict_iterator_proxy::const_reverse_iterator dict_iterator_proxy::crbegin()
    const {
  return const_reverse_iterator(rbegin());
}

dict_iterator_proxy::const_reverse_iterator dict_iterator_proxy::crend() const {
  return const_reverse_iterator(rend());
}

// ----------------------------------------------------------------------------
// const_dict_iterator_proxy.

const_dict_iterator_proxy::const_dict_iterator_proxy(const DictStorage* storage)
    : storage_(storage) {}

const_dict_iterator_proxy::const_iterator const_dict_iterator_proxy::begin()
    const {
  return const_iterator(storage_->begin());
}

const_dict_iterator_proxy::const_iterator const_dict_iterator_proxy::end()
    const {
  return const_iterator(storage_->end());
}

const_dict_iterator_proxy::const_reverse_iterator
const_dict_iterator_proxy::rbegin() const {
  return const_reverse_iterator(end());
}

const_dict_iterator_proxy::const_reverse_iterator
const_dict_iterator_proxy::rend() const {
  return const_reverse_iterator(begin());
}

const_dict_iterator_proxy::const_iterator const_dict_iterator_proxy::cbegin()
    const {
  return const_iterator(begin());
}

const_dict_iterator_proxy::const_iterator const_dict_iterator_proxy::cend()
    const {
  return const_iterator(end());
}

const_dict_iterator_proxy::const_reverse_iterator
const_dict_iterator_proxy::crbegin() const {
  return const_reverse_iterator(rbegin());
}

const_dict_iterator_proxy::const_reverse_iterator
const_dict_iterator_proxy::crend() const {
  return const_reverse_iterator(rend());
}

}  // namespace detail

}  // namespace base
