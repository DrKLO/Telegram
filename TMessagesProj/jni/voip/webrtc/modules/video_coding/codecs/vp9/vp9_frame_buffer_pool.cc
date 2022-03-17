/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 *
 */

#ifdef RTC_ENABLE_VP9

#include "modules/video_coding/codecs/vp9/vp9_frame_buffer_pool.h"

#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include <libvpx/vpx_codec.h>
#include <libvpx/vpx_decoder.h>
#include <libvpx/vpx_frame_buffer.h>

namespace webrtc {

uint8_t* Vp9FrameBufferPool::Vp9FrameBuffer::GetData() {
  return data_.data<uint8_t>();
}

size_t Vp9FrameBufferPool::Vp9FrameBuffer::GetDataSize() const {
  return data_.size();
}

void Vp9FrameBufferPool::Vp9FrameBuffer::SetSize(size_t size) {
  data_.SetSize(size);
}

bool Vp9FrameBufferPool::InitializeVpxUsePool(
    vpx_codec_ctx* vpx_codec_context) {
  RTC_DCHECK(vpx_codec_context);
  // Tell libvpx to use this pool.
  if (vpx_codec_set_frame_buffer_functions(
          // In which context to use these callback functions.
          vpx_codec_context,
          // Called by libvpx when it needs another frame buffer.
          &Vp9FrameBufferPool::VpxGetFrameBuffer,
          // Called by libvpx when it no longer uses a frame buffer.
          &Vp9FrameBufferPool::VpxReleaseFrameBuffer,
          // |this| will be passed as |user_priv| to VpxGetFrameBuffer.
          this)) {
    // Failed to configure libvpx to use Vp9FrameBufferPool.
    return false;
  }
  return true;
}

rtc::scoped_refptr<Vp9FrameBufferPool::Vp9FrameBuffer>
Vp9FrameBufferPool::GetFrameBuffer(size_t min_size) {
  RTC_DCHECK_GT(min_size, 0);
  rtc::scoped_refptr<Vp9FrameBuffer> available_buffer = nullptr;
  {
    MutexLock lock(&buffers_lock_);
    // Do we have a buffer we can recycle?
    for (const auto& buffer : allocated_buffers_) {
      if (buffer->HasOneRef()) {
        available_buffer = buffer;
        break;
      }
    }
    // Otherwise create one.
    if (available_buffer == nullptr) {
      available_buffer = new Vp9FrameBuffer();
      allocated_buffers_.push_back(available_buffer);
      if (allocated_buffers_.size() > max_num_buffers_) {
        RTC_LOG(LS_WARNING)
            << allocated_buffers_.size()
            << " Vp9FrameBuffers have been "
               "allocated by a Vp9FrameBufferPool (exceeding what is "
               "considered reasonable, "
            << max_num_buffers_ << ").";

        // TODO(phoglund): this limit is being hit in tests since Oct 5 2016.
        // See https://bugs.chromium.org/p/webrtc/issues/detail?id=6484.
        // RTC_NOTREACHED();
      }
    }
  }

  available_buffer->SetSize(min_size);
  return available_buffer;
}

int Vp9FrameBufferPool::GetNumBuffersInUse() const {
  int num_buffers_in_use = 0;
  MutexLock lock(&buffers_lock_);
  for (const auto& buffer : allocated_buffers_) {
    if (!buffer->HasOneRef())
      ++num_buffers_in_use;
  }
  return num_buffers_in_use;
}

bool Vp9FrameBufferPool::Resize(size_t max_number_of_buffers) {
  MutexLock lock(&buffers_lock_);
  size_t used_buffers_count = 0;
  for (const auto& buffer : allocated_buffers_) {
    // If the buffer is in use, the ref count will be >= 2, one from the list we
    // are looping over and one from the application. If the ref count is 1,
    // then the list we are looping over holds the only reference and it's safe
    // to reuse.
    if (!buffer->HasOneRef()) {
      used_buffers_count++;
    }
  }
  if (used_buffers_count > max_number_of_buffers) {
    return false;
  }
  max_num_buffers_ = max_number_of_buffers;

  size_t buffers_to_purge = allocated_buffers_.size() - max_num_buffers_;
  auto iter = allocated_buffers_.begin();
  while (iter != allocated_buffers_.end() && buffers_to_purge > 0) {
    if ((*iter)->HasOneRef()) {
      iter = allocated_buffers_.erase(iter);
      buffers_to_purge--;
    } else {
      ++iter;
    }
  }
  return true;
}

void Vp9FrameBufferPool::ClearPool() {
  MutexLock lock(&buffers_lock_);
  allocated_buffers_.clear();
}

// static
int32_t Vp9FrameBufferPool::VpxGetFrameBuffer(void* user_priv,
                                              size_t min_size,
                                              vpx_codec_frame_buffer* fb) {
  RTC_DCHECK(user_priv);
  RTC_DCHECK(fb);

#ifdef FUZZING_BUILD_MODE_UNSAFE_FOR_PRODUCTION
  // Limit size of 8k YUV highdef frame
  size_t size_limit = 7680 * 4320 * 3 / 2 * 2;
  if (min_size > size_limit)
    return -1;
#endif

  Vp9FrameBufferPool* pool = static_cast<Vp9FrameBufferPool*>(user_priv);

  rtc::scoped_refptr<Vp9FrameBuffer> buffer = pool->GetFrameBuffer(min_size);
  fb->data = buffer->GetData();
  fb->size = buffer->GetDataSize();
  // Store Vp9FrameBuffer* in |priv| for use in VpxReleaseFrameBuffer.
  // This also makes vpx_codec_get_frame return images with their |fb_priv| set
  // to |buffer| which is important for external reference counting.
  // Release from refptr so that the buffer's |ref_count_| remains 1 when
  // |buffer| goes out of scope.
  fb->priv = static_cast<void*>(buffer.release());
  return 0;
}

// static
int32_t Vp9FrameBufferPool::VpxReleaseFrameBuffer(void* user_priv,
                                                  vpx_codec_frame_buffer* fb) {
  RTC_DCHECK(user_priv);
  RTC_DCHECK(fb);
  Vp9FrameBuffer* buffer = static_cast<Vp9FrameBuffer*>(fb->priv);
  if (buffer != nullptr) {
    buffer->Release();
    // When libvpx fails to decode and you continue to try to decode (and fail)
    // libvpx can for some reason try to release the same buffer multiple times.
    // Setting |priv| to null protects against trying to Release multiple times.
    fb->priv = nullptr;
  }
  return 0;
}

}  // namespace webrtc

#endif  // RTC_ENABLE_VP9
