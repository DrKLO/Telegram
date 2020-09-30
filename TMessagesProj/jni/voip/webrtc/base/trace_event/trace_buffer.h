// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_TRACE_EVENT_TRACE_BUFFER_H_
#define BASE_TRACE_EVENT_TRACE_BUFFER_H_

#include <stddef.h>
#include <stdint.h>

#include "base/base_export.h"
#include "base/trace_event/trace_event.h"
#include "base/trace_event/trace_event_impl.h"

namespace base {

namespace trace_event {

// TraceBufferChunk is the basic unit of TraceBuffer.
class BASE_EXPORT TraceBufferChunk {
 public:
  explicit TraceBufferChunk(uint32_t seq);
  ~TraceBufferChunk();

  void Reset(uint32_t new_seq);
  TraceEvent* AddTraceEvent(size_t* event_index);
  bool IsFull() const { return next_free_ == kTraceBufferChunkSize; }

  uint32_t seq() const { return seq_; }
  size_t capacity() const { return kTraceBufferChunkSize; }
  size_t size() const { return next_free_; }

  TraceEvent* GetEventAt(size_t index) {
    DCHECK(index < size());
    return &chunk_[index];
  }
  const TraceEvent* GetEventAt(size_t index) const {
    DCHECK(index < size());
    return &chunk_[index];
  }

  void EstimateTraceMemoryOverhead(TraceEventMemoryOverhead* overhead);

  // These values must be kept consistent with the numbers of bits of
  // chunk_index and event_index fields in TraceEventHandle
  // (in trace_event_impl.h).
  static const size_t kMaxChunkIndex = (1u << 26) - 1;
  static const size_t kTraceBufferChunkSize = 64;

 private:
  size_t next_free_;
  std::unique_ptr<TraceEventMemoryOverhead> cached_overhead_estimate_;
  TraceEvent chunk_[kTraceBufferChunkSize];
  uint32_t seq_;
};

// TraceBuffer holds the events as they are collected.
class BASE_EXPORT TraceBuffer {
 public:
  virtual ~TraceBuffer() = default;

  virtual std::unique_ptr<TraceBufferChunk> GetChunk(size_t* index) = 0;
  virtual void ReturnChunk(size_t index,
                           std::unique_ptr<TraceBufferChunk> chunk) = 0;

  virtual bool IsFull() const = 0;
  virtual size_t Size() const = 0;
  virtual size_t Capacity() const = 0;
  virtual TraceEvent* GetEventByHandle(TraceEventHandle handle) = 0;

  // For iteration. Each TraceBuffer can only be iterated once.
  virtual const TraceBufferChunk* NextChunk() = 0;


  // Computes an estimate of the size of the buffer, including all the retained
  // objects.
  virtual void EstimateTraceMemoryOverhead(
      TraceEventMemoryOverhead* overhead) = 0;

  static TraceBuffer* CreateTraceBufferRingBuffer(size_t max_chunks);
  static TraceBuffer* CreateTraceBufferVectorOfSize(size_t max_chunks);
};

// TraceResultBuffer collects and converts trace fragments returned by TraceLog
// to JSON output.
class BASE_EXPORT TraceResultBuffer {
 public:
  using OutputCallback = base::RepeatingCallback<void(const std::string&)>;

  // If you don't need to stream JSON chunks out efficiently, and just want to
  // get a complete JSON string after calling Finish, use this struct to collect
  // JSON trace output.
  struct BASE_EXPORT SimpleOutput {
    OutputCallback GetCallback();
    void Append(const std::string& json_string);

    // Do what you want with the json_output_ string after calling
    // TraceResultBuffer::Finish.
    std::string json_output;
  };

  TraceResultBuffer();
  ~TraceResultBuffer();

  // Set callback. The callback will be called during Start with the initial
  // JSON output and during AddFragment and Finish with following JSON output
  // chunks. The callback target must live past the last calls to
  // TraceResultBuffer::Start/AddFragment/Finish.
  void SetOutputCallback(OutputCallback json_chunk_callback);

  // Start JSON output. This resets all internal state, so you can reuse
  // the TraceResultBuffer by calling Start.
  void Start();

  // Call AddFragment 0 or more times to add trace fragments from TraceLog.
  void AddFragment(const std::string& trace_fragment);

  // When all fragments have been added, call Finish to complete the JSON
  // formatted output.
  void Finish();

 private:
  OutputCallback output_callback_;
  bool append_comma_;
};

}  // namespace trace_event
}  // namespace base

#endif  // BASE_TRACE_EVENT_TRACE_BUFFER_H_
