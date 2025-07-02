/*
 *  Copyright 2016 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/stats/rtc_stats.h"

#include <cstdio>

#include "rtc_base/strings/string_builder.h"

namespace webrtc {

RTCStats::RTCStats(const RTCStats& other)
    : RTCStats(other.id_, other.timestamp_) {}

RTCStats::~RTCStats() {}

bool RTCStats::operator==(const RTCStats& other) const {
  if (type() != other.type() || id() != other.id())
    return false;
  std::vector<Attribute> attributes = Attributes();
  std::vector<Attribute> other_attributes = other.Attributes();
  RTC_DCHECK_EQ(attributes.size(), other_attributes.size());
  for (size_t i = 0; i < attributes.size(); ++i) {
    if (attributes[i] != other_attributes[i]) {
      return false;
    }
  }
  return true;
}

bool RTCStats::operator!=(const RTCStats& other) const {
  return !(*this == other);
}

std::string RTCStats::ToJson() const {
  rtc::StringBuilder sb;
  sb << "{\"type\":\"" << type()
     << "\","
        "\"id\":\""
     << id_
     << "\","
        "\"timestamp\":"
     << timestamp_.us();
  for (const Attribute& attribute : Attributes()) {
    if (attribute.has_value()) {
      sb << ",\"" << attribute.name() << "\":";
      if (attribute.holds_alternative<std::string>()) {
        sb << "\"";
      }
      sb << attribute.ToString();
      if (attribute.holds_alternative<std::string>()) {
        sb << "\"";
      }
    }
  }
  sb << "}";
  return sb.Release();
}

std::vector<Attribute> RTCStats::Attributes() const {
  return AttributesImpl(0);
}

std::vector<Attribute> RTCStats::AttributesImpl(
    size_t additional_capacity) const {
  std::vector<Attribute> attributes;
  attributes.reserve(additional_capacity);
  return attributes;
}

}  // namespace webrtc
