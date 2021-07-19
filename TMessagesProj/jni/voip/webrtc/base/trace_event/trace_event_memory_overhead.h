// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_TRACE_EVENT_TRACE_EVENT_MEMORY_OVERHEAD_H_
#define BASE_TRACE_EVENT_TRACE_EVENT_MEMORY_OVERHEAD_H_

#include <stddef.h>
#include <stdint.h>

#include <string>
#include <unordered_map>

#include "base/base_export.h"
#include "base/macros.h"

namespace base {

class RefCountedString;
class Value;

namespace trace_event {

class ProcessMemoryDump;

// Used to estimate the memory overhead of the tracing infrastructure.
class BASE_EXPORT TraceEventMemoryOverhead {
 public:
  enum ObjectType : uint32_t {
    kOther = 0,
    kTraceBuffer,
    kTraceBufferChunk,
    kTraceEvent,
    kUnusedTraceEvent,
    kTracedValue,
    kConvertableToTraceFormat,
    kHeapProfilerAllocationRegister,
    kHeapProfilerTypeNameDeduplicator,
    kHeapProfilerStackFrameDeduplicator,
    kStdString,
    kBaseValue,
    kTraceEventMemoryOverhead,
    kFrameMetrics,
    kLast
  };

  TraceEventMemoryOverhead();
  ~TraceEventMemoryOverhead();

  // Use this method to account the overhead of an object for which an estimate
  // is known for both the allocated and resident memory.
  void Add(ObjectType object_type,
           size_t allocated_size_in_bytes,
           size_t resident_size_in_bytes);

  // Similar to Add() above, but assumes that
  // |resident_size_in_bytes| == |allocated_size_in_bytes|.
  void Add(ObjectType object_type, size_t allocated_size_in_bytes);

  // Specialized profiling functions for commonly used object types.
  void AddString(const std::string& str);
  void AddValue(const Value& value);
  void AddRefCountedString(const RefCountedString& str);

  // Call this after all the Add* methods above to account the memory used by
  // this TraceEventMemoryOverhead instance itself.
  void AddSelf();

  // Retrieves the count, that is, the count of Add*(|object_type|, ...) calls.
  size_t GetCount(ObjectType object_type) const;

  // Adds up and merges all the values from |other| to this instance.
  void Update(const TraceEventMemoryOverhead& other);

  void DumpInto(const char* base_name, ProcessMemoryDump* pmd) const;

 private:
  struct ObjectCountAndSize {
    size_t count;
    size_t allocated_size_in_bytes;
    size_t resident_size_in_bytes;
  };
  ObjectCountAndSize allocated_objects_[ObjectType::kLast];

  void AddInternal(ObjectType object_type,
                   size_t count,
                   size_t allocated_size_in_bytes,
                   size_t resident_size_in_bytes);

  DISALLOW_COPY_AND_ASSIGN(TraceEventMemoryOverhead);
};

}  // namespace trace_event
}  // namespace base

#endif  // BASE_TRACE_EVENT_TRACE_EVENT_MEMORY_OVERHEAD_H_
