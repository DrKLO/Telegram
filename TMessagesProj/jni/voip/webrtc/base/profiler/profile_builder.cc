// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/profiler/profile_builder.h"

namespace base {

const size_t ProfileBuilder::MAX_METADATA_COUNT;

ProfileBuilder::MetadataItem::MetadataItem(uint64_t name_hash,
                                           Optional<int64_t> key,
                                           int64_t value)
    : name_hash(name_hash), key(key), value(value) {}

ProfileBuilder::MetadataItem::MetadataItem() : name_hash(0), value(0) {}

ProfileBuilder::MetadataItem::MetadataItem(const MetadataItem& other) = default;

ProfileBuilder::MetadataItem& ProfileBuilder::MetadataItem::MetadataItem::
operator=(const MetadataItem& other) = default;

}  // namespace base
