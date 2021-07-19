/*
 *  Copyright 2015 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/log_sinks.h"

#include <string.h>

#include <cstdio>
#include <string>

#include "rtc_base/checks.h"

namespace rtc {

FileRotatingLogSink::FileRotatingLogSink(const std::string& log_dir_path,
                                         const std::string& log_prefix,
                                         size_t max_log_size,
                                         size_t num_log_files)
    : FileRotatingLogSink(new FileRotatingStream(log_dir_path,
                                                 log_prefix,
                                                 max_log_size,
                                                 num_log_files)) {}

FileRotatingLogSink::FileRotatingLogSink(FileRotatingStream* stream)
    : stream_(stream) {
  RTC_DCHECK(stream);
}

FileRotatingLogSink::~FileRotatingLogSink() {}

void FileRotatingLogSink::OnLogMessage(const std::string& message) {
  if (!stream_->IsOpen()) {
    std::fprintf(stderr, "Init() must be called before adding this sink.\n");
    return;
  }
  stream_->Write(message.c_str(), message.size());
}

void FileRotatingLogSink::OnLogMessage(const std::string& message,
                                       LoggingSeverity sev,
                                       const char* tag) {
  if (!stream_->IsOpen()) {
    std::fprintf(stderr, "Init() must be called before adding this sink.\n");
    return;
  }
  stream_->Write(tag, strlen(tag));
  stream_->Write(": ", 2);
  stream_->Write(message.c_str(), message.size());
}

bool FileRotatingLogSink::Init() {
  return stream_->Open();
}

bool FileRotatingLogSink::DisableBuffering() {
  return stream_->DisableBuffering();
}

CallSessionFileRotatingLogSink::CallSessionFileRotatingLogSink(
    const std::string& log_dir_path,
    size_t max_total_log_size)
    : FileRotatingLogSink(
          new CallSessionFileRotatingStream(log_dir_path, max_total_log_size)) {
}

CallSessionFileRotatingLogSink::~CallSessionFileRotatingLogSink() {}

}  // namespace rtc
