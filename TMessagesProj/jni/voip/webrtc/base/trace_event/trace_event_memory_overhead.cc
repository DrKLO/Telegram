// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/trace_event/trace_event_memory_overhead.h"

#include <algorithm>

#include "base/bits.h"
#include "base/memory/ref_counted_memory.h"
#include "base/strings/stringprintf.h"
#include "base/trace_event/memory_allocator_dump.h"
#include "base/trace_event/memory_usage_estimator.h"
#include "base/trace_event/process_memory_dump.h"
#include "base/values.h"

namespace base {
namespace trace_event {

namespace {

const char* ObjectTypeToString(TraceEventMemoryOverhead::ObjectType type) {
  switch (type) {
    case TraceEventMemoryOverhead::kOther:
      return "(Other)";
    case TraceEventMemoryOverhead::kTraceBuffer:
      return "TraceBuffer";
    case TraceEventMemoryOverhead::kTraceBufferChunk:
      return "TraceBufferChunk";
    case TraceEventMemoryOverhead::kTraceEvent:
      return "TraceEvent";
    case TraceEventMemoryOverhead::kUnusedTraceEvent:
      return "TraceEvent(Unused)";
    case TraceEventMemoryOverhead::kTracedValue:
      return "TracedValue";
    case TraceEventMemoryOverhead::kConvertableToTraceFormat:
      return "ConvertableToTraceFormat";
    case TraceEventMemoryOverhead::kHeapProfilerAllocationRegister:
      return "AllocationRegister";
    case TraceEventMemoryOverhead::kHeapProfilerTypeNameDeduplicator:
      return "TypeNameDeduplicator";
    case TraceEventMemoryOverhead::kHeapProfilerStackFrameDeduplicator:
      return "StackFrameDeduplicator";
    case TraceEventMemoryOverhead::kStdString:
      return "std::string";
    case TraceEventMemoryOverhead::kBaseValue:
      return "base::Value";
    case TraceEventMemoryOverhead::kTraceEventMemoryOverhead:
      return "TraceEventMemoryOverhead";
    case TraceEventMemoryOverhead::kFrameMetrics:
      return "FrameMetrics";
    case TraceEventMemoryOverhead::kLast:
      NOTREACHED();
  }
  NOTREACHED();
  return "BUG";
}

}  // namespace

TraceEventMemoryOverhead::TraceEventMemoryOverhead() : allocated_objects_() {}

TraceEventMemoryOverhead::~TraceEventMemoryOverhead() = default;

void TraceEventMemoryOverhead::AddInternal(ObjectType object_type,
                                           size_t count,
                                           size_t allocated_size_in_bytes,
                                           size_t resident_size_in_bytes) {
  ObjectCountAndSize& count_and_size =
      allocated_objects_[static_cast<uint32_t>(object_type)];
  count_and_size.count += count;
  count_and_size.allocated_size_in_bytes += allocated_size_in_bytes;
  count_and_size.resident_size_in_bytes += resident_size_in_bytes;
}

void TraceEventMemoryOverhead::Add(ObjectType object_type,
                                   size_t allocated_size_in_bytes) {
  Add(object_type, allocated_size_in_bytes, allocated_size_in_bytes);
}

void TraceEventMemoryOverhead::Add(ObjectType object_type,
                                   size_t allocated_size_in_bytes,
                                   size_t resident_size_in_bytes) {
  AddInternal(object_type, 1, allocated_size_in_bytes, resident_size_in_bytes);
}

void TraceEventMemoryOverhead::AddString(const std::string& str) {
  Add(kStdString, EstimateMemoryUsage(str));
}

void TraceEventMemoryOverhead::AddRefCountedString(
    const RefCountedString& str) {
  Add(kOther, sizeof(RefCountedString));
  AddString(str.data());
}

void TraceEventMemoryOverhead::AddValue(const Value& value) {
  switch (value.type()) {
    case Value::Type::NONE:
    case Value::Type::BOOLEAN:
    case Value::Type::INTEGER:
    case Value::Type::DOUBLE:
      Add(kBaseValue, sizeof(Value));
      break;

    case Value::Type::STRING: {
      const Value* string_value = nullptr;
      value.GetAsString(&string_value);
      Add(kBaseValue, sizeof(Value));
      AddString(string_value->GetString());
    } break;

    case Value::Type::BINARY: {
      Add(kBaseValue, sizeof(Value) + value.GetBlob().size());
    } break;

    case Value::Type::DICTIONARY: {
      const DictionaryValue* dictionary_value = nullptr;
      value.GetAsDictionary(&dictionary_value);
      Add(kBaseValue, sizeof(DictionaryValue));
      for (DictionaryValue::Iterator it(*dictionary_value); !it.IsAtEnd();
           it.Advance()) {
        AddString(it.key());
        AddValue(it.value());
      }
    } break;

    case Value::Type::LIST: {
      const ListValue* list_value = nullptr;
      value.GetAsList(&list_value);
      Add(kBaseValue, sizeof(ListValue));
      for (const auto& v : *list_value)
        AddValue(v);
    } break;

    default:
      NOTREACHED();
  }
}

void TraceEventMemoryOverhead::AddSelf() {
  Add(kTraceEventMemoryOverhead, sizeof(*this));
}

size_t TraceEventMemoryOverhead::GetCount(ObjectType object_type) const {
  CHECK(object_type < kLast);
  return allocated_objects_[static_cast<uint32_t>(object_type)].count;
}

void TraceEventMemoryOverhead::Update(const TraceEventMemoryOverhead& other) {
  for (uint32_t i = 0; i < kLast; i++) {
    const ObjectCountAndSize& other_entry = other.allocated_objects_[i];
    AddInternal(static_cast<ObjectType>(i), other_entry.count,
                other_entry.allocated_size_in_bytes,
                other_entry.resident_size_in_bytes);
  }
}

void TraceEventMemoryOverhead::DumpInto(const char* base_name,
                                        ProcessMemoryDump* pmd) const {
  for (uint32_t i = 0; i < kLast; i++) {
    const ObjectCountAndSize& count_and_size = allocated_objects_[i];
    if (count_and_size.allocated_size_in_bytes == 0)
      continue;
    std::string dump_name = StringPrintf(
        "%s/%s", base_name, ObjectTypeToString(static_cast<ObjectType>(i)));
    MemoryAllocatorDump* mad = pmd->CreateAllocatorDump(dump_name);
    mad->AddScalar(MemoryAllocatorDump::kNameSize,
                   MemoryAllocatorDump::kUnitsBytes,
                   count_and_size.allocated_size_in_bytes);
    mad->AddScalar("resident_size", MemoryAllocatorDump::kUnitsBytes,
                   count_and_size.resident_size_in_bytes);
    mad->AddScalar(MemoryAllocatorDump::kNameObjectCount,
                   MemoryAllocatorDump::kUnitsObjects, count_and_size.count);
  }
}

}  // namespace trace_event
}  // namespace base
