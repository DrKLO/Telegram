/*
 *  Copyright 2019 The Chromium Authors. All rights reserved.
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#ifndef NET_DCSCTP_PUBLIC_STRONG_ALIAS_H_
#define NET_DCSCTP_PUBLIC_STRONG_ALIAS_H_

#include <type_traits>
#include <utility>

namespace dcsctp {

// This is a copy of
// https://source.chromium.org/chromium/chromium/src/+/master:base/types/strong_alias.h
// as the API (and internals) are using type-safe integral identifiers, but this
// library can't depend on that file. The ostream operator has been removed
// per WebRTC library conventions, and the underlying type is exposed.

template <typename TagType, typename TheUnderlyingType>
class StrongAlias {
 public:
  using UnderlyingType = TheUnderlyingType;
  constexpr StrongAlias() = default;
  constexpr explicit StrongAlias(const UnderlyingType& v) : value_(v) {}
  constexpr explicit StrongAlias(UnderlyingType&& v) noexcept
      : value_(std::move(v)) {}

  constexpr UnderlyingType* operator->() { return &value_; }
  constexpr const UnderlyingType* operator->() const { return &value_; }

  constexpr UnderlyingType& operator*() & { return value_; }
  constexpr const UnderlyingType& operator*() const& { return value_; }
  constexpr UnderlyingType&& operator*() && { return std::move(value_); }
  constexpr const UnderlyingType&& operator*() const&& {
    return std::move(value_);
  }

  constexpr UnderlyingType& value() & { return value_; }
  constexpr const UnderlyingType& value() const& { return value_; }
  constexpr UnderlyingType&& value() && { return std::move(value_); }
  constexpr const UnderlyingType&& value() const&& { return std::move(value_); }

  constexpr explicit operator const UnderlyingType&() const& { return value_; }

  constexpr bool operator==(const StrongAlias& other) const {
    return value_ == other.value_;
  }
  constexpr bool operator!=(const StrongAlias& other) const {
    return value_ != other.value_;
  }
  constexpr bool operator<(const StrongAlias& other) const {
    return value_ < other.value_;
  }
  constexpr bool operator<=(const StrongAlias& other) const {
    return value_ <= other.value_;
  }
  constexpr bool operator>(const StrongAlias& other) const {
    return value_ > other.value_;
  }
  constexpr bool operator>=(const StrongAlias& other) const {
    return value_ >= other.value_;
  }

  // Hasher to use in std::unordered_map, std::unordered_set, etc.
  struct Hasher {
    using argument_type = StrongAlias;
    using result_type = std::size_t;
    result_type operator()(const argument_type& id) const {
      return std::hash<UnderlyingType>()(id.value());
    }
  };

 protected:
  UnderlyingType value_;
};

}  // namespace dcsctp

#endif  // NET_DCSCTP_PUBLIC_STRONG_ALIAS_H_
