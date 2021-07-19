// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/trace_event/memory_allocator_dump.h"

#include <string.h>

#include "base/format_macros.h"
#include "base/memory/ptr_util.h"
#include "base/strings/stringprintf.h"
#include "base/trace_event/memory_dump_manager.h"
#include "base/trace_event/memory_dump_provider.h"
#include "base/trace_event/process_memory_dump.h"
#include "base/trace_event/traced_value.h"
#include "base/values.h"

namespace base {
namespace trace_event {

const char MemoryAllocatorDump::kNameSize[] = "size";
const char MemoryAllocatorDump::kNameObjectCount[] = "object_count";
const char MemoryAllocatorDump::kTypeScalar[] = "scalar";
const char MemoryAllocatorDump::kTypeString[] = "string";
const char MemoryAllocatorDump::kUnitsBytes[] = "bytes";
const char MemoryAllocatorDump::kUnitsObjects[] = "objects";

MemoryAllocatorDump::MemoryAllocatorDump(
    const std::string& absolute_name,
    MemoryDumpLevelOfDetail level_of_detail,
    const MemoryAllocatorDumpGuid& guid)
    : absolute_name_(absolute_name),
      guid_(guid),
      level_of_detail_(level_of_detail),
      flags_(Flags::DEFAULT) {
  // The |absolute_name| cannot be empty.
  DCHECK(!absolute_name.empty());

  // The |absolute_name| can contain slash separator, but not leading or
  // trailing ones.
  DCHECK(absolute_name[0] != '/' && *absolute_name.rbegin() != '/');
}

MemoryAllocatorDump::~MemoryAllocatorDump() = default;

void MemoryAllocatorDump::AddScalar(const char* name,
                                    const char* units,
                                    uint64_t value) {
  entries_.emplace_back(name, units, value);
}

void MemoryAllocatorDump::AddString(const char* name,
                                    const char* units,
                                    const std::string& value) {
  // String attributes are disabled in background mode.
  if (level_of_detail_ == MemoryDumpLevelOfDetail::BACKGROUND) {
    NOTREACHED();
    return;
  }
  entries_.emplace_back(name, units, value);
}

void MemoryAllocatorDump::AsValueInto(TracedValue* value) const {
  std::string string_conversion_buffer;
  value->BeginDictionaryWithCopiedName(absolute_name_);
  value->SetString("guid", guid_.ToString());
  value->BeginDictionary("attrs");

  for (const Entry& entry : entries_) {
    value->BeginDictionaryWithCopiedName(entry.name);
    switch (entry.entry_type) {
      case Entry::kUint64:
        SStringPrintf(&string_conversion_buffer, "%" PRIx64,
                      entry.value_uint64);
        value->SetString("type", kTypeScalar);
        value->SetString("units", entry.units);
        value->SetString("value", string_conversion_buffer);
        break;
      case Entry::kString:
        value->SetString("type", kTypeString);
        value->SetString("units", entry.units);
        value->SetString("value", entry.value_string);
        break;
    }
    value->EndDictionary();
  }
  value->EndDictionary();  // "attrs": { ... }
  if (flags_)
    value->SetInteger("flags", flags_);
  value->EndDictionary();  // "allocator_name/heap_subheap": { ... }
}

uint64_t MemoryAllocatorDump::GetSizeInternal() const {
  if (cached_size_.has_value())
    return *cached_size_;
  for (const auto& entry : entries_) {
    if (entry.entry_type == Entry::kUint64 && entry.units == kUnitsBytes &&
        strcmp(entry.name.c_str(), kNameSize) == 0) {
      cached_size_ = entry.value_uint64;
      return entry.value_uint64;
    }
  }
  return 0;
}

MemoryAllocatorDump::Entry::Entry() : entry_type(kString), value_uint64() {}
MemoryAllocatorDump::Entry::Entry(MemoryAllocatorDump::Entry&&) noexcept =
    default;
MemoryAllocatorDump::Entry& MemoryAllocatorDump::Entry::operator=(
    MemoryAllocatorDump::Entry&&) = default;
MemoryAllocatorDump::Entry::Entry(std::string name,
                                  std::string units,
                                  uint64_t value)
    : name(name), units(units), entry_type(kUint64), value_uint64(value) {}
MemoryAllocatorDump::Entry::Entry(std::string name,
                                  std::string units,
                                  std::string value)
    : name(name), units(units), entry_type(kString), value_string(value) {}

bool MemoryAllocatorDump::Entry::operator==(const Entry& rhs) const {
  if (!(name == rhs.name && units == rhs.units && entry_type == rhs.entry_type))
    return false;
  switch (entry_type) {
    case EntryType::kUint64:
      return value_uint64 == rhs.value_uint64;
    case EntryType::kString:
      return value_string == rhs.value_string;
  }
  NOTREACHED();
  return false;
}

void PrintTo(const MemoryAllocatorDump::Entry& entry, std::ostream* out) {
  switch (entry.entry_type) {
    case MemoryAllocatorDump::Entry::EntryType::kUint64:
      *out << "<Entry(\"" << entry.name << "\", \"" << entry.units << "\", "
           << entry.value_uint64 << ")>";
      return;
    case MemoryAllocatorDump::Entry::EntryType::kString:
      *out << "<Entry(\"" << entry.name << "\", \"" << entry.units << "\", \""
           << entry.value_string << "\")>";
      return;
  }
  NOTREACHED();
}

}  // namespace trace_event
}  // namespace base
