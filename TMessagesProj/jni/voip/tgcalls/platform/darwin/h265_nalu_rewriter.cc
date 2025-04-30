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

#include "h265_nalu_rewriter.h"

#include <CoreFoundation/CoreFoundation.h>
#include <memory>
#include <vector>

#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "common_video/h265/h265_common.h"

namespace webrtc {

namespace {

const char kAnnexBHeaderBytes[4] = {0, 0, 0, 1};
const size_t kAvccHeaderByteSize = sizeof(uint32_t);

// Helper class for reading NALUs from an RTP Annex B buffer.
class H265AnnexBBufferReader final {
 public:
  H265AnnexBBufferReader(const uint8_t* annexb_buffer, size_t length);
  ~H265AnnexBBufferReader();
  H265AnnexBBufferReader(const H265AnnexBBufferReader& other) = delete;
  void operator=(const H265AnnexBBufferReader& other) = delete;

  // Returns a pointer to the beginning of the next NALU slice without the
  // header bytes and its length. Returns false if no more slices remain.
  bool ReadNalu(const uint8_t** out_nalu, size_t* out_length);

  // Returns the number of unread NALU bytes, including the size of the header.
  // If the buffer has no remaining NALUs this will return zero.
  size_t BytesRemaining() const;

  // Reset the reader to start reading from the first NALU
  void SeekToStart();

  // Seek to the next position that holds a NALU of the desired type,
  // or the end if no such NALU is found.
  // Return true if a NALU of the desired type is found, false if we
  // reached the end instead
  bool SeekToNextNaluOfType(H265::NaluType type);

 private:
  // Returns the the next offset that contains NALU data.
  size_t FindNextNaluHeader(const uint8_t* start,
                            size_t length,
                            size_t offset) const;

  const uint8_t* const start_;
  std::vector<NaluIndex> offsets_;
  std::vector<NaluIndex>::iterator offset_;
  const size_t length_;
};

// Helper class for writing NALUs using avcc format into a buffer.
class H265AvccBufferWriter final {
 public:
  H265AvccBufferWriter(uint8_t* const avcc_buffer, size_t length);
  ~H265AvccBufferWriter() {}
  H265AvccBufferWriter(const H265AvccBufferWriter& other) = delete;
  void operator=(const H265AvccBufferWriter& other) = delete;

  // Writes the data slice into the buffer. Returns false if there isn't
  // enough space left.
  bool WriteNalu(const uint8_t* data, size_t data_size);

  // Returns the unused bytes in the buffer.
  size_t BytesRemaining() const;

 private:
  uint8_t* const start_;
  size_t offset_;
  const size_t length_;
};

H265AnnexBBufferReader::H265AnnexBBufferReader(const uint8_t* annexb_buffer,
                                       size_t length)
    : start_(annexb_buffer), length_(length) {
  RTC_DCHECK(annexb_buffer);
  offsets_ = H264::FindNaluIndices(annexb_buffer, length);
  offset_ = offsets_.begin();
}

H265AnnexBBufferReader::~H265AnnexBBufferReader() = default;

bool H265AnnexBBufferReader::ReadNalu(const uint8_t** out_nalu,
                                  size_t* out_length) {
  RTC_DCHECK(out_nalu);
  RTC_DCHECK(out_length);
  *out_nalu = nullptr;
  *out_length = 0;

  if (offset_ == offsets_.end()) {
    return false;
  }
  *out_nalu = start_ + offset_->payload_start_offset;
  *out_length = offset_->payload_size;
  ++offset_;
  return true;
}

size_t H265AnnexBBufferReader::BytesRemaining() const {
  if (offset_ == offsets_.end()) {
    return 0;
  }
  return length_ - offset_->start_offset;
}

void H265AnnexBBufferReader::SeekToStart() {
  offset_ = offsets_.begin();
}

bool H265AnnexBBufferReader::SeekToNextNaluOfType(H265::NaluType type) {
  for (; offset_ != offsets_.end(); ++offset_) {
    if (offset_->payload_size < 1)
      continue;
    if (H265::ParseNaluType(*(start_ + offset_->payload_start_offset)) == type)
      return true;
  }
  return false;
}
H265AvccBufferWriter::H265AvccBufferWriter(uint8_t* const avcc_buffer, size_t length)
    : start_(avcc_buffer), offset_(0), length_(length) {
  RTC_DCHECK(avcc_buffer);
}

bool H265AvccBufferWriter::WriteNalu(const uint8_t* data, size_t data_size) {
  // Check if we can write this length of data.
  if (data_size + kAvccHeaderByteSize > BytesRemaining()) {
    return false;
  }
  // Write length header, which needs to be big endian.
  uint32_t big_endian_length = CFSwapInt32HostToBig(data_size);
  memcpy(start_ + offset_, &big_endian_length, sizeof(big_endian_length));
  offset_ += sizeof(big_endian_length);
  // Write data.
  memcpy(start_ + offset_, data, data_size);
  offset_ += data_size;
  return true;
}

size_t H265AvccBufferWriter::BytesRemaining() const {
  return length_ - offset_;
}

}

bool H265CMSampleBufferToAnnexBBuffer(
    CMSampleBufferRef hvcc_sample_buffer,
    bool is_keyframe,
    rtc::Buffer* annexb_buffer) {
  RTC_DCHECK(hvcc_sample_buffer);

  // Get format description from the sample buffer.
  CMVideoFormatDescriptionRef description =
      CMSampleBufferGetFormatDescription(hvcc_sample_buffer);
  if (description == nullptr) {
    RTC_LOG(LS_ERROR) << "Failed to get sample buffer's description.";
    return false;
  }

  // Get parameter set information.
  int nalu_header_size = 0;
  size_t param_set_count = 0;
  OSStatus status = CMVideoFormatDescriptionGetHEVCParameterSetAtIndex(
      description, 0, nullptr, nullptr, &param_set_count, &nalu_header_size);
  if (status != noErr) {
    RTC_LOG(LS_ERROR) << "Failed to get parameter set.";
    return false;
  }
  RTC_CHECK_EQ(nalu_header_size, kAvccHeaderByteSize);
  RTC_DCHECK_EQ(param_set_count, 3);

  // Truncate any previous data in the buffer without changing its capacity.
  annexb_buffer->SetSize(0);

  size_t nalu_offset = 0;
  std::vector<size_t> frag_offsets;
  std::vector<size_t> frag_lengths;

  // Place all parameter sets at the front of buffer.
  if (is_keyframe) {
    size_t param_set_size = 0;
    const uint8_t* param_set = nullptr;
    for (size_t i = 0; i < param_set_count; ++i) {
      status = CMVideoFormatDescriptionGetHEVCParameterSetAtIndex(
          description, i, &param_set, &param_set_size, nullptr, nullptr);
      if (status != noErr) {
        RTC_LOG(LS_ERROR) << "Failed to get parameter set.";
        return false;
      }
      // Update buffer.
      annexb_buffer->AppendData(kAnnexBHeaderBytes, sizeof(kAnnexBHeaderBytes));
      annexb_buffer->AppendData(reinterpret_cast<const char*>(param_set),
                                param_set_size);
      // Update fragmentation.
      frag_offsets.push_back(nalu_offset + sizeof(kAnnexBHeaderBytes));
      frag_lengths.push_back(param_set_size);
      nalu_offset += sizeof(kAnnexBHeaderBytes) + param_set_size;
    }
  }

  // Get block buffer from the sample buffer.
  CMBlockBufferRef block_buffer =
      CMSampleBufferGetDataBuffer(hvcc_sample_buffer);
  if (block_buffer == nullptr) {
    RTC_LOG(LS_ERROR) << "Failed to get sample buffer's block buffer.";
    return false;
  }
  CMBlockBufferRef contiguous_buffer = nullptr;
  // Make sure block buffer is contiguous.
  if (!CMBlockBufferIsRangeContiguous(block_buffer, 0, 0)) {
    status = CMBlockBufferCreateContiguous(
        nullptr, block_buffer, nullptr, nullptr, 0, 0, 0, &contiguous_buffer);
    if (status != noErr) {
      RTC_LOG(LS_ERROR) << "Failed to flatten non-contiguous block buffer: "
                        << status;
      return false;
    }
  } else {
    contiguous_buffer = block_buffer;
    // Retain to make cleanup easier.
    CFRetain(contiguous_buffer);
    block_buffer = nullptr;
  }

  // Now copy the actual data.
  char* data_ptr = nullptr;
  size_t block_buffer_size = CMBlockBufferGetDataLength(contiguous_buffer);
  status = CMBlockBufferGetDataPointer(contiguous_buffer, 0, nullptr, nullptr,
                                       &data_ptr);
  if (status != noErr) {
    RTC_LOG(LS_ERROR) << "Failed to get block buffer data.";
    CFRelease(contiguous_buffer);
    return false;
  }
  size_t bytes_remaining = block_buffer_size;
  while (bytes_remaining > 0) {
    // The size type here must match |nalu_header_size|, we expect 4 bytes.
    // Read the length of the next packet of data. Must convert from big endian
    // to host endian.
    RTC_DCHECK_GE(bytes_remaining, (size_t)nalu_header_size);
    uint32_t* uint32_data_ptr = reinterpret_cast<uint32_t*>(data_ptr);
    uint32_t packet_size = CFSwapInt32BigToHost(*uint32_data_ptr);
    // Update buffer.
    annexb_buffer->AppendData(kAnnexBHeaderBytes, sizeof(kAnnexBHeaderBytes));
    annexb_buffer->AppendData(data_ptr + nalu_header_size, packet_size);
    // Update fragmentation.
    frag_offsets.push_back(nalu_offset + sizeof(kAnnexBHeaderBytes));
    frag_lengths.push_back(packet_size);
    nalu_offset += sizeof(kAnnexBHeaderBytes) + packet_size;

    size_t bytes_written = packet_size + sizeof(kAnnexBHeaderBytes);
    bytes_remaining -= bytes_written;
    data_ptr += bytes_written;
  }
  RTC_DCHECK_EQ(bytes_remaining, (size_t)0);

  CFRelease(contiguous_buffer);
  return true;
}

bool H265AnnexBBufferToCMSampleBuffer(const uint8_t* annexb_buffer,
                                      size_t annexb_buffer_size,
                                      CMVideoFormatDescriptionRef video_format,
                                      CMSampleBufferRef* out_sample_buffer) {
  RTC_DCHECK(annexb_buffer);
  RTC_DCHECK(out_sample_buffer);
  RTC_DCHECK(video_format);
  *out_sample_buffer = nullptr;

  H265AnnexBBufferReader reader(annexb_buffer, annexb_buffer_size);
  if (reader.SeekToNextNaluOfType(H265::kVps)) {
    // Buffer contains an SPS NALU - skip it and the following PPS
    const uint8_t* data;
    size_t data_len;
    if (!reader.ReadNalu(&data, &data_len)) {
      RTC_LOG(LS_ERROR) << "Failed to read VPS";
      return false;
    }
    if (!reader.ReadNalu(&data, &data_len)) {
      RTC_LOG(LS_ERROR) << "Failed to read SPS";
      return false;
    }
    if (!reader.ReadNalu(&data, &data_len)) {
      RTC_LOG(LS_ERROR) << "Failed to read PPS";
      return false;
    }
  } else {
    // No SPS NALU - start reading from the first NALU in the buffer
    reader.SeekToStart();
  }

  // Allocate memory as a block buffer.
  // TODO(tkchin): figure out how to use a pool.
  CMBlockBufferRef block_buffer = nullptr;
  OSStatus status = CMBlockBufferCreateWithMemoryBlock(
      nullptr, nullptr, reader.BytesRemaining(), nullptr, nullptr, 0,
      reader.BytesRemaining(), kCMBlockBufferAssureMemoryNowFlag,
      &block_buffer);
  if (status != kCMBlockBufferNoErr) {
    RTC_LOG(LS_ERROR) << "Failed to create block buffer.";
    return false;
  }

  // Make sure block buffer is contiguous.
  CMBlockBufferRef contiguous_buffer = nullptr;
  if (!CMBlockBufferIsRangeContiguous(block_buffer, 0, 0)) {
    status = CMBlockBufferCreateContiguous(
        nullptr, block_buffer, nullptr, nullptr, 0, 0, 0, &contiguous_buffer);
    if (status != noErr) {
      RTC_LOG(LS_ERROR) << "Failed to flatten non-contiguous block buffer: "
                        << status;
      CFRelease(block_buffer);
      return false;
    }
  } else {
    contiguous_buffer = block_buffer;
    block_buffer = nullptr;
  }

  // Get a raw pointer into allocated memory.
  size_t block_buffer_size = 0;
  char* data_ptr = nullptr;
  status = CMBlockBufferGetDataPointer(contiguous_buffer, 0, nullptr,
                                       &block_buffer_size, &data_ptr);
  if (status != kCMBlockBufferNoErr) {
    RTC_LOG(LS_ERROR) << "Failed to get block buffer data pointer.";
    CFRelease(contiguous_buffer);
    return false;
  }
  RTC_DCHECK(block_buffer_size == reader.BytesRemaining());

  // Write Avcc NALUs into block buffer memory.
  H265AvccBufferWriter writer(reinterpret_cast<uint8_t*>(data_ptr),
                          block_buffer_size);
  while (reader.BytesRemaining() > 0) {
    const uint8_t* nalu_data_ptr = nullptr;
    size_t nalu_data_size = 0;
    if (reader.ReadNalu(&nalu_data_ptr, &nalu_data_size)) {
      writer.WriteNalu(nalu_data_ptr, nalu_data_size);
    }
  }

  // Create sample buffer.
  status = CMSampleBufferCreate(nullptr, contiguous_buffer, true, nullptr,
                                nullptr, video_format, 1, 0, nullptr, 0,
                                nullptr, out_sample_buffer);
  if (status != noErr) {
    RTC_LOG(LS_ERROR) << "Failed to create sample buffer.";
    CFRelease(contiguous_buffer);
    return false;
  }
  CFRelease(contiguous_buffer);
  return true;
}

CMVideoFormatDescriptionRef CreateH265VideoFormatDescription(
    const uint8_t* annexb_buffer,
    size_t annexb_buffer_size) {
  const uint8_t* param_set_ptrs[3] = {};
  size_t param_set_sizes[3] = {};
  H265AnnexBBufferReader reader(annexb_buffer, annexb_buffer_size);
  // Skip everyting before the VPS, then read the VPS, SPS and PPS
  if (!reader.SeekToNextNaluOfType(H265::kVps)) {
    return nullptr;
  }
  if (!reader.ReadNalu(&param_set_ptrs[0], &param_set_sizes[0])) {
    RTC_LOG(LS_ERROR) << "Failed to read VPS";
    return nullptr;
  }
  if (!reader.ReadNalu(&param_set_ptrs[1], &param_set_sizes[1])) {
    RTC_LOG(LS_ERROR) << "Failed to read SPS";
    return nullptr;
  }
  if (!reader.ReadNalu(&param_set_ptrs[2], &param_set_sizes[2])) {
    RTC_LOG(LS_ERROR) << "Failed to read PPS";
    return nullptr;
  }

  // Parse the SPS and PPS into a CMVideoFormatDescription.
  CMVideoFormatDescriptionRef description = nullptr;
  OSStatus status = CMVideoFormatDescriptionCreateFromHEVCParameterSets(
      kCFAllocatorDefault, 3, param_set_ptrs, param_set_sizes, 4, nullptr,
      &description);
  if (status != noErr) {
    RTC_LOG(LS_ERROR) << "Failed to create video format description.";
    return nullptr;
  }
  return description;
}



}  // namespace webrtc
