/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "rtc_base/stream.h"

#include <errno.h>
#include <string.h>

#include <algorithm>
#include <string>

#include "rtc_base/checks.h"
#include "rtc_base/location.h"
#include "rtc_base/thread.h"

namespace rtc {

///////////////////////////////////////////////////////////////////////////////
// StreamInterface
///////////////////////////////////////////////////////////////////////////////

StreamResult StreamInterface::WriteAll(const void* data,
                                       size_t data_len,
                                       size_t* written,
                                       int* error) {
  StreamResult result = SR_SUCCESS;
  size_t total_written = 0, current_written;
  while (total_written < data_len) {
    result = Write(static_cast<const char*>(data) + total_written,
                   data_len - total_written, &current_written, error);
    if (result != SR_SUCCESS)
      break;
    total_written += current_written;
  }
  if (written)
    *written = total_written;
  return result;
}

bool StreamInterface::Flush() {
  return false;
}

StreamInterface::StreamInterface() {}

///////////////////////////////////////////////////////////////////////////////
// StreamAdapterInterface
///////////////////////////////////////////////////////////////////////////////

StreamAdapterInterface::StreamAdapterInterface(StreamInterface* stream,
                                               bool owned)
    : stream_(stream), owned_(owned) {
  if (nullptr != stream_)
    stream_->SignalEvent.connect(this, &StreamAdapterInterface::OnEvent);
}

StreamState StreamAdapterInterface::GetState() const {
  return stream_->GetState();
}
StreamResult StreamAdapterInterface::Read(void* buffer,
                                          size_t buffer_len,
                                          size_t* read,
                                          int* error) {
  return stream_->Read(buffer, buffer_len, read, error);
}
StreamResult StreamAdapterInterface::Write(const void* data,
                                           size_t data_len,
                                           size_t* written,
                                           int* error) {
  return stream_->Write(data, data_len, written, error);
}
void StreamAdapterInterface::Close() {
  stream_->Close();
}

bool StreamAdapterInterface::Flush() {
  return stream_->Flush();
}

void StreamAdapterInterface::Attach(StreamInterface* stream, bool owned) {
  if (nullptr != stream_)
    stream_->SignalEvent.disconnect(this);
  if (owned_)
    delete stream_;
  stream_ = stream;
  owned_ = owned;
  if (nullptr != stream_)
    stream_->SignalEvent.connect(this, &StreamAdapterInterface::OnEvent);
}

StreamInterface* StreamAdapterInterface::Detach() {
  if (nullptr != stream_)
    stream_->SignalEvent.disconnect(this);
  StreamInterface* stream = stream_;
  stream_ = nullptr;
  return stream;
}

StreamAdapterInterface::~StreamAdapterInterface() {
  if (owned_)
    delete stream_;
}

void StreamAdapterInterface::OnEvent(StreamInterface* stream,
                                     int events,
                                     int err) {
  SignalEvent(this, events, err);
}

}  // namespace rtc
