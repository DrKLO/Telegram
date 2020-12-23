/*
 *  Copyright 2019 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_MEMORY_FIFO_BUFFER_H_
#define RTC_BASE_MEMORY_FIFO_BUFFER_H_

#include <memory>

#include "rtc_base/stream.h"
#include "rtc_base/synchronization/mutex.h"
#include "rtc_base/task_utils/pending_task_safety_flag.h"
#include "rtc_base/task_utils/to_queued_task.h"

namespace rtc {

// FifoBuffer allows for efficient, thread-safe buffering of data between
// writer and reader.
class FifoBuffer final : public StreamInterface {
 public:
  // Creates a FIFO buffer with the specified capacity.
  explicit FifoBuffer(size_t length);
  // Creates a FIFO buffer with the specified capacity and owner
  FifoBuffer(size_t length, Thread* owner);
  ~FifoBuffer() override;
  // Gets the amount of data currently readable from the buffer.
  bool GetBuffered(size_t* data_len) const;
  // Resizes the buffer to the specified capacity. Fails if data_length_ > size
  bool SetCapacity(size_t length);

  // Read into |buffer| with an offset from the current read position, offset
  // is specified in number of bytes.
  // This method doesn't adjust read position nor the number of available
  // bytes, user has to call ConsumeReadData() to do this.
  StreamResult ReadOffset(void* buffer,
                          size_t bytes,
                          size_t offset,
                          size_t* bytes_read);

  // Write |buffer| with an offset from the current write position, offset is
  // specified in number of bytes.
  // This method doesn't adjust the number of buffered bytes, user has to call
  // ConsumeWriteBuffer() to do this.
  StreamResult WriteOffset(const void* buffer,
                           size_t bytes,
                           size_t offset,
                           size_t* bytes_written);

  // StreamInterface methods
  StreamState GetState() const override;
  StreamResult Read(void* buffer,
                    size_t bytes,
                    size_t* bytes_read,
                    int* error) override;
  StreamResult Write(const void* buffer,
                     size_t bytes,
                     size_t* bytes_written,
                     int* error) override;
  void Close() override;

  // Seek to a byte offset from the beginning of the stream.  Returns false if
  // the stream does not support seeking, or cannot seek to the specified
  // position.
  bool SetPosition(size_t position);

  // Get the byte offset of the current position from the start of the stream.
  // Returns false if the position is not known.
  bool GetPosition(size_t* position) const;

  // Seek to the start of the stream.
  bool Rewind() { return SetPosition(0); }

  // GetReadData returns a pointer to a buffer which is owned by the stream.
  // The buffer contains data_len bytes.  null is returned if no data is
  // available, or if the method fails.  If the caller processes the data, it
  // must call ConsumeReadData with the number of processed bytes.  GetReadData
  // does not require a matching call to ConsumeReadData if the data is not
  // processed.  Read and ConsumeReadData invalidate the buffer returned by
  // GetReadData.
  const void* GetReadData(size_t* data_len);
  void ConsumeReadData(size_t used);
  // GetWriteBuffer returns a pointer to a buffer which is owned by the stream.
  // The buffer has a capacity of buf_len bytes.  null is returned if there is
  // no buffer available, or if the method fails.  The call may write data to
  // the buffer, and then call ConsumeWriteBuffer with the number of bytes
  // written.  GetWriteBuffer does not require a matching call to
  // ConsumeWriteData if no data is written.  Write and
  // ConsumeWriteData invalidate the buffer returned by GetWriteBuffer.
  void* GetWriteBuffer(size_t* buf_len);
  void ConsumeWriteBuffer(size_t used);

  // Return the number of Write()-able bytes remaining before end-of-stream.
  // Returns false if not known.
  bool GetWriteRemaining(size_t* size) const;

 private:
  void PostEvent(int events, int err) {
    owner_->PostTask(webrtc::ToQueuedTask(task_safety_, [this, events, err]() {
      SignalEvent(this, events, err);
    }));
  }

  // Helper method that implements ReadOffset. Caller must acquire a lock
  // when calling this method.
  StreamResult ReadOffsetLocked(void* buffer,
                                size_t bytes,
                                size_t offset,
                                size_t* bytes_read)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(mutex_);

  // Helper method that implements WriteOffset. Caller must acquire a lock
  // when calling this method.
  StreamResult WriteOffsetLocked(const void* buffer,
                                 size_t bytes,
                                 size_t offset,
                                 size_t* bytes_written)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(mutex_);

  webrtc::ScopedTaskSafety task_safety_;

  // keeps the opened/closed state of the stream
  StreamState state_ RTC_GUARDED_BY(mutex_);
  // the allocated buffer
  std::unique_ptr<char[]> buffer_ RTC_GUARDED_BY(mutex_);
  // size of the allocated buffer
  size_t buffer_length_ RTC_GUARDED_BY(mutex_);
  // amount of readable data in the buffer
  size_t data_length_ RTC_GUARDED_BY(mutex_);
  // offset to the readable data
  size_t read_position_ RTC_GUARDED_BY(mutex_);
  // stream callbacks are dispatched on this thread
  Thread* const owner_;
  // object lock
  mutable webrtc::Mutex mutex_;
  RTC_DISALLOW_COPY_AND_ASSIGN(FifoBuffer);
};

}  // namespace rtc

#endif  // RTC_BASE_MEMORY_FIFO_BUFFER_H_
