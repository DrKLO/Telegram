/*
 *  Copyright 2020 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "p2p/base/stun_dictionary.h"

#include <algorithm>
#include <deque>
#include <utility>

#include "rtc_base/logging.h"

namespace cricket {

const StunAddressAttribute* StunDictionaryView::GetAddress(int key) const {
  const StunAttribute* attr = GetOrNull(key, STUN_VALUE_ADDRESS);
  if (attr == nullptr) {
    return nullptr;
  }
  return reinterpret_cast<const StunAddressAttribute*>(attr);
}

const StunUInt32Attribute* StunDictionaryView::GetUInt32(int key) const {
  const StunAttribute* attr = GetOrNull(key, STUN_VALUE_UINT32);
  if (attr == nullptr) {
    return nullptr;
  }
  return reinterpret_cast<const StunUInt32Attribute*>(attr);
}

const StunUInt64Attribute* StunDictionaryView::GetUInt64(int key) const {
  const StunAttribute* attr = GetOrNull(key, STUN_VALUE_UINT64);
  if (attr == nullptr) {
    return nullptr;
  }
  return reinterpret_cast<const StunUInt64Attribute*>(attr);
}

const StunByteStringAttribute* StunDictionaryView::GetByteString(
    int key) const {
  const StunAttribute* attr = GetOrNull(key, STUN_VALUE_BYTE_STRING);
  if (attr == nullptr) {
    return nullptr;
  }
  return reinterpret_cast<const StunByteStringAttribute*>(attr);
}

const StunUInt16ListAttribute* StunDictionaryView::GetUInt16List(
    int key) const {
  const StunAttribute* attr = GetOrNull(key, STUN_VALUE_UINT16_LIST);
  if (attr == nullptr) {
    return nullptr;
  }
  return reinterpret_cast<const StunUInt16ListAttribute*>(attr);
}

const StunAttribute* StunDictionaryView::GetOrNull(
    int key,
    absl::optional<StunAttributeValueType> type) const {
  const auto it = attrs_.find(key);
  if (it == attrs_.end()) {
    return nullptr;
  }

  if (type && it->second->value_type() != *type) {
    RTC_LOG(LS_WARNING) << "Get key: " << key << " with type: " << *type
                        << " found different type: "
                        << it->second->value_type();
    return nullptr;
  }
  return (*it).second.get();
}

webrtc::RTCErrorOr<
    std::pair<uint64_t, std::deque<std::unique_ptr<StunAttribute>>>>
StunDictionaryView::ParseDelta(const StunByteStringAttribute& delta) {
  rtc::ByteBufferReader buf(delta.array_view());
  uint16_t magic;
  if (!buf.ReadUInt16(&magic)) {
    return webrtc::RTCError(webrtc::RTCErrorType::INVALID_PARAMETER,
                            "Failed to read magic number");
  }
  if (magic != kDeltaMagic) {
    return webrtc::RTCError(webrtc::RTCErrorType::INVALID_PARAMETER,
                            "Invalid magic number");
  }

  uint16_t delta_version;
  if (!buf.ReadUInt16(&delta_version)) {
    return webrtc::RTCError(webrtc::RTCErrorType::INVALID_PARAMETER,
                            "Failed to read version");
  }

  if (delta_version != kDeltaVersion) {
    return webrtc::RTCError(webrtc::RTCErrorType::INVALID_PARAMETER,
                            "Unsupported delta version");
  }

  // Now read all the attributes
  std::deque<std::unique_ptr<StunAttribute>> attrs;
  while (buf.Length()) {
    uint16_t key, length, value_type;
    if (!buf.ReadUInt16(&key)) {
      return webrtc::RTCError(webrtc::RTCErrorType::INVALID_PARAMETER,
                              "Failed to read attribute key");
    }
    if (!buf.ReadUInt16(&length)) {
      return webrtc::RTCError(webrtc::RTCErrorType::INVALID_PARAMETER,
                              "Failed to read attribute length");
    }
    if (!buf.ReadUInt16(&value_type)) {
      return webrtc::RTCError(webrtc::RTCErrorType::INVALID_PARAMETER,
                              "Failed to read value type");
    }

    StunAttributeValueType value_type_enum =
        static_cast<StunAttributeValueType>(value_type);
    std::unique_ptr<StunAttribute> attr(
        StunAttribute::Create(value_type_enum, key, length, nullptr));
    if (!attr) {
      return webrtc::RTCError(webrtc::RTCErrorType::INVALID_PARAMETER,
                              "Failed to create attribute");
    }
    if (attr->length() != length) {
      return webrtc::RTCError(webrtc::RTCErrorType::INVALID_PARAMETER,
                              "Inconsistent attribute length");
    }
    if (!attr->Read(&buf)) {
      return webrtc::RTCError(webrtc::RTCErrorType::INVALID_PARAMETER,
                              "Failed to read attribute content");
    }
    attrs.push_back(std::move(attr));
  }

  // The first attribute should be the version...
  if (attrs.empty()) {
    return webrtc::RTCError(webrtc::RTCErrorType::INVALID_PARAMETER,
                            "Empty delta!");
  }

  if (attrs[0]->type() != kVersionKey ||
      attrs[0]->value_type() != STUN_VALUE_UINT64) {
    return webrtc::RTCError(webrtc::RTCErrorType::INVALID_PARAMETER,
                            "Missing version!");
  }

  uint64_t version_in_delta =
      reinterpret_cast<const StunUInt64Attribute*>(attrs[0].get())->value();
  attrs.pop_front();

  return std::make_pair(std::max(version_in_delta, version_in_delta),
                        std::move(attrs));
}

// Apply a delta return an StunUInt64Attribute to ack the update.
webrtc::RTCErrorOr<
    std::pair<std::unique_ptr<StunUInt64Attribute>, std::vector<uint16_t>>>
StunDictionaryView::ApplyDelta(const StunByteStringAttribute& delta) {
  auto parsed_delta = ParseDelta(delta);
  if (!parsed_delta.ok()) {
    return webrtc::RTCError(parsed_delta.error());
  }

  uint64_t version_in_delta = parsed_delta.value().first;

  // Check that update does not overflow max_bytes_stored_.
  int new_bytes_stored = bytes_stored_;
  for (auto& attr : parsed_delta.value().second) {
    auto old_version = version_per_key_.find(attr->type());
    if (old_version == version_per_key_.end() ||
        version_in_delta > old_version->second) {
      size_t new_length = attr->length();
      size_t old_length = GetLength(attr->type());
      if (old_version == version_per_key_.end()) {
        new_length += sizeof(int64_t);
      }

      new_bytes_stored = new_bytes_stored + new_length - old_length;
      if (new_bytes_stored <= 0) {
        RTC_LOG(LS_WARNING)
            << "attr: " << attr->type() << " old_length: " << old_length
            << " new_length: " << new_length
            << " bytes_stored_: " << bytes_stored_
            << " new_bytes_stored: " << new_bytes_stored;
        return webrtc::RTCError(webrtc::RTCErrorType::INVALID_PARAMETER);
      }
      if (new_bytes_stored > max_bytes_stored_) {
        RTC_LOG(LS_INFO) << "attr: " << attr->type()
                         << " old_length: " << old_length
                         << " new_length: " << new_length
                         << " bytes_stored_: " << bytes_stored_
                         << " new_bytes_stored: " << new_bytes_stored;
      }
    }
  }
  if (new_bytes_stored > max_bytes_stored_) {
    RTC_LOG(LS_INFO) << " bytes_stored_: " << bytes_stored_
                     << " new_bytes_stored: " << new_bytes_stored;
    return webrtc::RTCError(webrtc::RTCErrorType::RESOURCE_EXHAUSTED);
  }

  // Apply the update.
  std::vector<uint16_t> keys;
  for (auto& attr : parsed_delta.value().second) {
    if (version_in_delta > version_per_key_[attr->type()]) {
      version_per_key_[attr->type()] = version_in_delta;
      keys.push_back(attr->type());
      if (attr->value_type() == STUN_VALUE_BYTE_STRING && attr->length() == 0) {
        attrs_.erase(attr->type());
      } else {
        int attribute_type = attr->type();
        attrs_[attribute_type] = std::move(attr);
      }
    }
  }
  bytes_stored_ = new_bytes_stored;

  return std::make_pair(std::make_unique<StunUInt64Attribute>(
                            STUN_ATTR_GOOG_DELTA_ACK, version_in_delta),
                        std::move(keys));
}

size_t StunDictionaryView::GetLength(int key) const {
  auto attr = GetOrNull(key);
  if (attr != nullptr) {
    return attr->length();
  }
  return 0;
}

void StunDictionaryWriter::Disable() {
  disabled_ = true;
}

void StunDictionaryWriter::Delete(int key) {
  if (disabled_) {
    return;
  }

  if (dictionary_) {
    if (dictionary_->attrs_.find(key) == dictionary_->attrs_.end()) {
      return;
    }
  }

  // remove any pending updates.
  pending_.erase(
      std::remove_if(pending_.begin(), pending_.end(),
                     [key](const auto& p) { return p.second->type() == key; }),
      pending_.end());

  // Create tombstone.
  auto tombstone = std::make_unique<StunByteStringAttribute>(key);

  // add a pending entry.
  pending_.push_back(std::make_pair(++version_, tombstone.get()));

  // store the tombstone.
  tombstones_[key] = std::move(tombstone);

  if (dictionary_) {
    // remove value
    dictionary_->attrs_.erase(key);
  }
}

void StunDictionaryWriter::Set(std::unique_ptr<StunAttribute> attr) {
  if (disabled_) {
    return;
  }
  int key = attr->type();
  // remove any pending updates.
  pending_.erase(
      std::remove_if(pending_.begin(), pending_.end(),
                     [key](const auto& p) { return p.second->type() == key; }),
      pending_.end());

  // remove any existing key.
  tombstones_.erase(key);

  // create pending entry.
  pending_.push_back(std::make_pair(++version_, attr.get()));

  if (dictionary_) {
    // store attribute.
    dictionary_->attrs_[key] = std::move(attr);
  }
}

// Create an StunByteStringAttribute containing the pending (e.g not ack:ed)
// modifications.
std::unique_ptr<StunByteStringAttribute> StunDictionaryWriter::CreateDelta() {
  if (disabled_) {
    return nullptr;
  }
  if (pending_.empty()) {
    return nullptr;
  }

  rtc::ByteBufferWriter buf;
  buf.WriteUInt16(StunDictionaryView::kDeltaMagic);    // 0,1
  buf.WriteUInt16(StunDictionaryView::kDeltaVersion);  // 2,3

  // max version in Delta.
  buf.WriteUInt16(StunDictionaryView::kVersionKey);  // 4,5
  buf.WriteUInt16(8);                                // 6,7
  buf.WriteUInt16(STUN_VALUE_UINT64);                // 8,9
  buf.WriteUInt64(pending_.back().first);            // 10-17
  // attributes
  for (const auto& attr : pending_) {
    buf.WriteUInt16(attr.second->type());
    buf.WriteUInt16(static_cast<uint16_t>(attr.second->length()));
    buf.WriteUInt16(attr.second->value_type());
    if (!attr.second->Write(&buf)) {
      RTC_LOG(LS_ERROR) << "Failed to write key: " << attr.second->type();
      return nullptr;
    }
  }
  return std::make_unique<StunByteStringAttribute>(STUN_ATTR_GOOG_DELTA,
                                                   buf.Data(), buf.Length());
}

// Apply a delta ack, i.e prune list of pending changes.
void StunDictionaryWriter::ApplyDeltaAck(const StunUInt64Attribute& ack) {
  uint64_t acked_version = ack.value();
  auto entries_to_remove = std::remove_if(
      pending_.begin(), pending_.end(),
      [acked_version](const auto& p) { return p.first <= acked_version; });

  // remove tombstones.
  for (auto it = entries_to_remove; it != pending_.end(); ++it) {
    tombstones_.erase((*it).second->type());
  }
  pending_.erase(entries_to_remove, pending_.end());
}

// Check if a key has a pending change (i.e a change
// that has not been acked).
bool StunDictionaryWriter::Pending(int key) const {
  for (const auto& attr : pending_) {
    if (attr.second->type() == key) {
      return true;
    }
  }
  return false;
}

int StunDictionaryWriter::Pending() const {
  return pending_.size();
}

}  // namespace cricket
