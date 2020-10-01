/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/video/encoded_image.h"

#include <stdlib.h>
#include <string.h>

#include "rtc_base/ref_counted_object.h"

namespace webrtc {

EncodedImageBuffer::EncodedImageBuffer(size_t size) : size_(size) {
  buffer_ = static_cast<uint8_t*>(malloc(size));
}

EncodedImageBuffer::EncodedImageBuffer(const uint8_t* data, size_t size)
    : EncodedImageBuffer(size) {
  memcpy(buffer_, data, size);
}

EncodedImageBuffer::~EncodedImageBuffer() {
  free(buffer_);
}

// static
rtc::scoped_refptr<EncodedImageBuffer> EncodedImageBuffer::Create(size_t size) {
  return new rtc::RefCountedObject<EncodedImageBuffer>(size);
}
// static
rtc::scoped_refptr<EncodedImageBuffer> EncodedImageBuffer::Create(
    const uint8_t* data,
    size_t size) {
  return new rtc::RefCountedObject<EncodedImageBuffer>(data, size);
}

const uint8_t* EncodedImageBuffer::data() const {
  return buffer_;
}
uint8_t* EncodedImageBuffer::data() {
  return buffer_;
}
size_t EncodedImageBuffer::size() const {
  return size_;
}

void EncodedImageBuffer::Realloc(size_t size) {
  // Calling realloc with size == 0 is equivalent to free, and returns nullptr.
  // Which is confusing on systems where malloc(0) doesn't return a nullptr.
  // More specifically, it breaks expectations of
  // VCMSessionInfo::UpdateDataPointers.
  RTC_DCHECK(size > 0);
  buffer_ = static_cast<uint8_t*>(realloc(buffer_, size));
  size_ = size;
}

EncodedImage::EncodedImage() : EncodedImage(nullptr, 0, 0) {}

EncodedImage::EncodedImage(EncodedImage&&) = default;
EncodedImage::EncodedImage(const EncodedImage&) = default;

EncodedImage::EncodedImage(uint8_t* buffer, size_t size, size_t capacity)
    : size_(size), buffer_(buffer), capacity_(capacity) {}

EncodedImage::~EncodedImage() = default;

EncodedImage& EncodedImage::operator=(EncodedImage&&) = default;
EncodedImage& EncodedImage::operator=(const EncodedImage&) = default;

void EncodedImage::Retain() {
  if (buffer_) {
    encoded_data_ = EncodedImageBuffer::Create(buffer_, size_);
    buffer_ = nullptr;
  }
}

void EncodedImage::SetEncodeTime(int64_t encode_start_ms,
                                 int64_t encode_finish_ms) {
  timing_.encode_start_ms = encode_start_ms;
  timing_.encode_finish_ms = encode_finish_ms;
}

absl::optional<size_t> EncodedImage::SpatialLayerFrameSize(
    int spatial_index) const {
  RTC_DCHECK_GE(spatial_index, 0);
  RTC_DCHECK_LE(spatial_index, spatial_index_.value_or(0));

  auto it = spatial_layer_frame_size_bytes_.find(spatial_index);
  if (it == spatial_layer_frame_size_bytes_.end()) {
    return absl::nullopt;
  }

  return it->second;
}

void EncodedImage::SetSpatialLayerFrameSize(int spatial_index,
                                            size_t size_bytes) {
  RTC_DCHECK_GE(spatial_index, 0);
  RTC_DCHECK_LE(spatial_index, spatial_index_.value_or(0));
  RTC_DCHECK_GE(size_bytes, 0);
  spatial_layer_frame_size_bytes_[spatial_index] = size_bytes;
}

}  // namespace webrtc
