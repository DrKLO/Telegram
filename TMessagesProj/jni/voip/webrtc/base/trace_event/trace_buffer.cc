// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/trace_event/trace_buffer.h"

#include <memory>
#include <utility>
#include <vector>

#include "base/bind.h"
#include "base/macros.h"
#include "base/trace_event/heap_profiler.h"
#include "base/trace_event/trace_event_impl.h"

namespace base {
namespace trace_event {

namespace {

class TraceBufferRingBuffer : public TraceBuffer {
 public:
  TraceBufferRingBuffer(size_t max_chunks)
      : max_chunks_(max_chunks),
        recyclable_chunks_queue_(new size_t[queue_capacity()]),
        queue_head_(0),
        queue_tail_(max_chunks),
        current_iteration_index_(0),
        current_chunk_seq_(1) {
    chunks_.reserve(max_chunks);
    for (size_t i = 0; i < max_chunks; ++i)
      recyclable_chunks_queue_[i] = i;
  }

  std::unique_ptr<TraceBufferChunk> GetChunk(size_t* index) override {
    HEAP_PROFILER_SCOPED_IGNORE;

    // Because the number of threads is much less than the number of chunks,
    // the queue should never be empty.
    DCHECK(!QueueIsEmpty());

    *index = recyclable_chunks_queue_[queue_head_];
    queue_head_ = NextQueueIndex(queue_head_);
    current_iteration_index_ = queue_head_;

    if (*index >= chunks_.size())
      chunks_.resize(*index + 1);

    TraceBufferChunk* chunk = chunks_[*index].release();
    chunks_[*index] = nullptr;  // Put nullptr in the slot of a in-flight chunk.
    if (chunk)
      chunk->Reset(current_chunk_seq_++);
    else
      chunk = new TraceBufferChunk(current_chunk_seq_++);

    return std::unique_ptr<TraceBufferChunk>(chunk);
  }

  void ReturnChunk(size_t index,
                   std::unique_ptr<TraceBufferChunk> chunk) override {
    // When this method is called, the queue should not be full because it
    // can contain all chunks including the one to be returned.
    DCHECK(!QueueIsFull());
    DCHECK(chunk);
    DCHECK_LT(index, chunks_.size());
    DCHECK(!chunks_[index]);
    chunks_[index] = std::move(chunk);
    recyclable_chunks_queue_[queue_tail_] = index;
    queue_tail_ = NextQueueIndex(queue_tail_);
  }

  bool IsFull() const override { return false; }

  size_t Size() const override {
    // This is approximate because not all of the chunks are full.
    return chunks_.size() * TraceBufferChunk::kTraceBufferChunkSize;
  }

  size_t Capacity() const override {
    return max_chunks_ * TraceBufferChunk::kTraceBufferChunkSize;
  }

  TraceEvent* GetEventByHandle(TraceEventHandle handle) override {
    if (handle.chunk_index >= chunks_.size())
      return nullptr;
    TraceBufferChunk* chunk = chunks_[handle.chunk_index].get();
    if (!chunk || chunk->seq() != handle.chunk_seq)
      return nullptr;
    return chunk->GetEventAt(handle.event_index);
  }

  const TraceBufferChunk* NextChunk() override {
    if (chunks_.empty())
      return nullptr;

    while (current_iteration_index_ != queue_tail_) {
      size_t chunk_index = recyclable_chunks_queue_[current_iteration_index_];
      current_iteration_index_ = NextQueueIndex(current_iteration_index_);
      if (chunk_index >= chunks_.size())  // Skip uninitialized chunks.
        continue;
      DCHECK(chunks_[chunk_index]);
      return chunks_[chunk_index].get();
    }
    return nullptr;
  }

  void EstimateTraceMemoryOverhead(
      TraceEventMemoryOverhead* overhead) override {
    overhead->Add(TraceEventMemoryOverhead::kTraceBuffer, sizeof(*this));
    for (size_t queue_index = queue_head_; queue_index != queue_tail_;
         queue_index = NextQueueIndex(queue_index)) {
      size_t chunk_index = recyclable_chunks_queue_[queue_index];
      if (chunk_index >= chunks_.size())  // Skip uninitialized chunks.
        continue;
      chunks_[chunk_index]->EstimateTraceMemoryOverhead(overhead);
    }
  }

 private:
  bool QueueIsEmpty() const { return queue_head_ == queue_tail_; }

  size_t QueueSize() const {
    return queue_tail_ > queue_head_
               ? queue_tail_ - queue_head_
               : queue_tail_ + queue_capacity() - queue_head_;
  }

  bool QueueIsFull() const { return QueueSize() == queue_capacity() - 1; }

  size_t queue_capacity() const {
    // One extra space to help distinguish full state and empty state.
    return max_chunks_ + 1;
  }

  size_t NextQueueIndex(size_t index) const {
    index++;
    if (index >= queue_capacity())
      index = 0;
    return index;
  }

  size_t max_chunks_;
  std::vector<std::unique_ptr<TraceBufferChunk>> chunks_;

  std::unique_ptr<size_t[]> recyclable_chunks_queue_;
  size_t queue_head_;
  size_t queue_tail_;

  size_t current_iteration_index_;
  uint32_t current_chunk_seq_;

  DISALLOW_COPY_AND_ASSIGN(TraceBufferRingBuffer);
};

class TraceBufferVector : public TraceBuffer {
 public:
  TraceBufferVector(size_t max_chunks)
      : in_flight_chunk_count_(0),
        current_iteration_index_(0),
        max_chunks_(max_chunks) {
    chunks_.reserve(max_chunks_);
  }

  std::unique_ptr<TraceBufferChunk> GetChunk(size_t* index) override {
    HEAP_PROFILER_SCOPED_IGNORE;

    // This function may be called when adding normal events or indirectly from
    // AddMetadataEventsWhileLocked(). We can not DECHECK(!IsFull()) because we
    // have to add the metadata events and flush thread-local buffers even if
    // the buffer is full.
    *index = chunks_.size();
    // Put nullptr in the slot of a in-flight chunk.
    chunks_.push_back(nullptr);
    ++in_flight_chunk_count_;
    // + 1 because zero chunk_seq is not allowed.
    return std::unique_ptr<TraceBufferChunk>(
        new TraceBufferChunk(static_cast<uint32_t>(*index) + 1));
  }

  void ReturnChunk(size_t index,
                   std::unique_ptr<TraceBufferChunk> chunk) override {
    DCHECK_GT(in_flight_chunk_count_, 0u);
    DCHECK_LT(index, chunks_.size());
    DCHECK(!chunks_[index]);
    --in_flight_chunk_count_;
    chunks_[index] = std::move(chunk);
  }

  bool IsFull() const override { return chunks_.size() >= max_chunks_; }

  size_t Size() const override {
    // This is approximate because not all of the chunks are full.
    return chunks_.size() * TraceBufferChunk::kTraceBufferChunkSize;
  }

  size_t Capacity() const override {
    return max_chunks_ * TraceBufferChunk::kTraceBufferChunkSize;
  }

  TraceEvent* GetEventByHandle(TraceEventHandle handle) override {
    if (handle.chunk_index >= chunks_.size())
      return nullptr;
    TraceBufferChunk* chunk = chunks_[handle.chunk_index].get();
    if (!chunk || chunk->seq() != handle.chunk_seq)
      return nullptr;
    return chunk->GetEventAt(handle.event_index);
  }

  const TraceBufferChunk* NextChunk() override {
    while (current_iteration_index_ < chunks_.size()) {
      // Skip in-flight chunks.
      const TraceBufferChunk* chunk = chunks_[current_iteration_index_++].get();
      if (chunk)
        return chunk;
    }
    return nullptr;
  }

  void EstimateTraceMemoryOverhead(
      TraceEventMemoryOverhead* overhead) override {
    const size_t chunks_ptr_vector_allocated_size =
        sizeof(*this) + max_chunks_ * sizeof(decltype(chunks_)::value_type);
    const size_t chunks_ptr_vector_resident_size =
        sizeof(*this) + chunks_.size() * sizeof(decltype(chunks_)::value_type);
    overhead->Add(TraceEventMemoryOverhead::kTraceBuffer,
                  chunks_ptr_vector_allocated_size,
                  chunks_ptr_vector_resident_size);
    for (size_t i = 0; i < chunks_.size(); ++i) {
      TraceBufferChunk* chunk = chunks_[i].get();
      // Skip the in-flight (nullptr) chunks. They will be accounted by the
      // per-thread-local dumpers, see ThreadLocalEventBuffer::OnMemoryDump.
      if (chunk)
        chunk->EstimateTraceMemoryOverhead(overhead);
    }
  }

 private:
  size_t in_flight_chunk_count_;
  size_t current_iteration_index_;
  size_t max_chunks_;
  std::vector<std::unique_ptr<TraceBufferChunk>> chunks_;

  DISALLOW_COPY_AND_ASSIGN(TraceBufferVector);
};

}  // namespace

TraceBufferChunk::TraceBufferChunk(uint32_t seq) : next_free_(0), seq_(seq) {}

TraceBufferChunk::~TraceBufferChunk() = default;

void TraceBufferChunk::Reset(uint32_t new_seq) {
  for (size_t i = 0; i < next_free_; ++i)
    chunk_[i].Reset();
  next_free_ = 0;
  seq_ = new_seq;
  cached_overhead_estimate_.reset();
}

TraceEvent* TraceBufferChunk::AddTraceEvent(size_t* event_index) {
  DCHECK(!IsFull());
  *event_index = next_free_++;
  return &chunk_[*event_index];
}

void TraceBufferChunk::EstimateTraceMemoryOverhead(
    TraceEventMemoryOverhead* overhead) {
  if (!cached_overhead_estimate_) {
    cached_overhead_estimate_.reset(new TraceEventMemoryOverhead);

    // When estimating the size of TraceBufferChunk, exclude the array of trace
    // events, as they are computed individually below.
    cached_overhead_estimate_->Add(TraceEventMemoryOverhead::kTraceBufferChunk,
                                   sizeof(*this) - sizeof(chunk_));
  }

  const size_t num_cached_estimated_events =
      cached_overhead_estimate_->GetCount(
          TraceEventMemoryOverhead::kTraceEvent);
  DCHECK_LE(num_cached_estimated_events, size());

  if (IsFull() && num_cached_estimated_events == size()) {
    overhead->Update(*cached_overhead_estimate_);
    return;
  }

  for (size_t i = num_cached_estimated_events; i < size(); ++i)
    chunk_[i].EstimateTraceMemoryOverhead(cached_overhead_estimate_.get());

  if (IsFull()) {
    cached_overhead_estimate_->AddSelf();
  } else {
    // The unused TraceEvents in |chunks_| are not cached. They will keep
    // changing as new TraceEvents are added to this chunk, so they are
    // computed on the fly.
    const size_t num_unused_trace_events = capacity() - size();
    overhead->Add(TraceEventMemoryOverhead::kUnusedTraceEvent,
                  num_unused_trace_events * sizeof(TraceEvent));
  }

  overhead->Update(*cached_overhead_estimate_);
}

TraceResultBuffer::OutputCallback
TraceResultBuffer::SimpleOutput::GetCallback() {
  return BindRepeating(&SimpleOutput::Append, Unretained(this));
}

void TraceResultBuffer::SimpleOutput::Append(
    const std::string& json_trace_output) {
  json_output += json_trace_output;
}

TraceResultBuffer::TraceResultBuffer() : append_comma_(false) {}

TraceResultBuffer::~TraceResultBuffer() = default;

void TraceResultBuffer::SetOutputCallback(OutputCallback json_chunk_callback) {
  output_callback_ = std::move(json_chunk_callback);
}

void TraceResultBuffer::Start() {
  append_comma_ = false;
  output_callback_.Run("[");
}

void TraceResultBuffer::AddFragment(const std::string& trace_fragment) {
  if (append_comma_)
    output_callback_.Run(",");
  append_comma_ = true;
  output_callback_.Run(trace_fragment);
}

void TraceResultBuffer::Finish() {
  output_callback_.Run("]");
}

TraceBuffer* TraceBuffer::CreateTraceBufferRingBuffer(size_t max_chunks) {
  return new TraceBufferRingBuffer(max_chunks);
}

TraceBuffer* TraceBuffer::CreateTraceBufferVectorOfSize(size_t max_chunks) {
  return new TraceBufferVector(max_chunks);
}

}  // namespace trace_event
}  // namespace base
