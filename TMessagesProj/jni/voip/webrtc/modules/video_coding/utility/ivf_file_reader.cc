/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_coding/utility/ivf_file_reader.h"

#include <string>
#include <vector>

#include "api/video_codecs/video_codec.h"
#include "modules/rtp_rtcp/source/byte_io.h"
#include "rtc_base/logging.h"

namespace webrtc {
namespace {

constexpr size_t kIvfHeaderSize = 32;
constexpr size_t kIvfFrameHeaderSize = 12;
constexpr int kCodecTypeBytesCount = 4;

constexpr uint8_t kFileHeaderStart[kCodecTypeBytesCount] = {'D', 'K', 'I', 'F'};
constexpr uint8_t kVp8Header[kCodecTypeBytesCount] = {'V', 'P', '8', '0'};
constexpr uint8_t kVp9Header[kCodecTypeBytesCount] = {'V', 'P', '9', '0'};
constexpr uint8_t kAv1Header[kCodecTypeBytesCount] = {'A', 'V', '0', '1'};
constexpr uint8_t kH264Header[kCodecTypeBytesCount] = {'H', '2', '6', '4'};

}  // namespace

std::unique_ptr<IvfFileReader> IvfFileReader::Create(FileWrapper file) {
  auto reader =
      std::unique_ptr<IvfFileReader>(new IvfFileReader(std::move(file)));
  if (!reader->Reset()) {
    return nullptr;
  }
  return reader;
}
IvfFileReader::~IvfFileReader() {
  Close();
}

bool IvfFileReader::Reset() {
  // Set error to true while initialization.
  has_error_ = true;
  if (!file_.Rewind()) {
    RTC_LOG(LS_ERROR) << "Failed to rewind IVF file";
    return false;
  }

  uint8_t ivf_header[kIvfHeaderSize] = {0};
  size_t read = file_.Read(&ivf_header, kIvfHeaderSize);
  if (read != kIvfHeaderSize) {
    RTC_LOG(LS_ERROR) << "Failed to read IVF header";
    return false;
  }

  if (memcmp(&ivf_header[0], kFileHeaderStart, 4) != 0) {
    RTC_LOG(LS_ERROR) << "File is not in IVF format: DKIF header expected";
    return false;
  }

  absl::optional<VideoCodecType> codec_type = ParseCodecType(ivf_header, 8);
  if (!codec_type) {
    return false;
  }
  codec_type_ = *codec_type;

  width_ = ByteReader<uint16_t>::ReadLittleEndian(&ivf_header[12]);
  height_ = ByteReader<uint16_t>::ReadLittleEndian(&ivf_header[14]);
  if (width_ == 0 || height_ == 0) {
    RTC_LOG(LS_ERROR) << "Invalid IVF header: width or height is 0";
    return false;
  }

  uint32_t time_scale = ByteReader<uint32_t>::ReadLittleEndian(&ivf_header[16]);
  if (time_scale == 1000) {
    using_capture_timestamps_ = true;
  } else if (time_scale == 90000) {
    using_capture_timestamps_ = false;
  } else {
    RTC_LOG(LS_ERROR) << "Invalid IVF header: Unknown time scale";
    return false;
  }

  num_frames_ = static_cast<size_t>(
      ByteReader<uint32_t>::ReadLittleEndian(&ivf_header[24]));
  if (num_frames_ <= 0) {
    RTC_LOG(LS_ERROR) << "Invalid IVF header: number of frames 0 or negative";
    return false;
  }

  num_read_frames_ = 0;
  next_frame_header_ = ReadNextFrameHeader();
  if (!next_frame_header_) {
    RTC_LOG(LS_ERROR) << "Failed to read 1st frame header";
    return false;
  }
  // Initialization succeed: reset error.
  has_error_ = false;

  const char* codec_name = CodecTypeToPayloadString(codec_type_);
  RTC_LOG(LS_INFO) << "Opened IVF file with codec data of type " << codec_name
                   << " at resolution " << width_ << " x " << height_
                   << ", using " << (using_capture_timestamps_ ? "1" : "90")
                   << "kHz clock resolution.";

  return true;
}

absl::optional<EncodedImage> IvfFileReader::NextFrame() {
  if (has_error_ || !HasMoreFrames()) {
    return absl::nullopt;
  }

  rtc::scoped_refptr<EncodedImageBuffer> payload = EncodedImageBuffer::Create();
  std::vector<size_t> layer_sizes;
  // next_frame_header_ have to be presented by the way how it was loaded. If it
  // is missing it means there is a bug in error handling.
  RTC_DCHECK(next_frame_header_);
  int64_t current_timestamp = next_frame_header_->timestamp;
  // The first frame from the file should be marked as Key frame.
  bool is_first_frame = num_read_frames_ == 0;
  while (next_frame_header_ &&
         current_timestamp == next_frame_header_->timestamp) {
    // Resize payload to fit next spatial layer.
    size_t current_layer_size = next_frame_header_->frame_size;
    size_t current_layer_start_pos = payload->size();
    payload->Realloc(payload->size() + current_layer_size);
    layer_sizes.push_back(current_layer_size);

    // Read next layer into payload
    size_t read = file_.Read(&payload->data()[current_layer_start_pos],
                             current_layer_size);
    if (read != current_layer_size) {
      RTC_LOG(LS_ERROR) << "Frame #" << num_read_frames_
                        << ": failed to read frame payload";
      has_error_ = true;
      return absl::nullopt;
    }
    num_read_frames_++;

    current_timestamp = next_frame_header_->timestamp;
    next_frame_header_ = ReadNextFrameHeader();
  }
  if (!next_frame_header_) {
    // If EOF was reached, we need to check that all frames were met.
    if (!has_error_ && num_read_frames_ != num_frames_) {
      RTC_LOG(LS_ERROR) << "Unexpected EOF";
      has_error_ = true;
      return absl::nullopt;
    }
  }

  EncodedImage image;
  if (using_capture_timestamps_) {
    image.capture_time_ms_ = current_timestamp;
    image.SetTimestamp(static_cast<uint32_t>(90 * current_timestamp));
  } else {
    image.SetTimestamp(static_cast<uint32_t>(current_timestamp));
  }
  image.SetEncodedData(payload);
  image.SetSpatialIndex(static_cast<int>(layer_sizes.size()) - 1);
  for (size_t i = 0; i < layer_sizes.size(); ++i) {
    image.SetSpatialLayerFrameSize(static_cast<int>(i), layer_sizes[i]);
  }
  if (is_first_frame) {
    image._frameType = VideoFrameType::kVideoFrameKey;
  }

  return image;
}

bool IvfFileReader::Close() {
  if (!file_.is_open())
    return false;

  file_.Close();
  return true;
}

absl::optional<VideoCodecType> IvfFileReader::ParseCodecType(uint8_t* buffer,
                                                             size_t start_pos) {
  if (memcmp(&buffer[start_pos], kVp8Header, kCodecTypeBytesCount) == 0) {
    return VideoCodecType::kVideoCodecVP8;
  }
  if (memcmp(&buffer[start_pos], kVp9Header, kCodecTypeBytesCount) == 0) {
    return VideoCodecType::kVideoCodecVP9;
  }
  if (memcmp(&buffer[start_pos], kAv1Header, kCodecTypeBytesCount) == 0) {
    return VideoCodecType::kVideoCodecAV1;
  }
  if (memcmp(&buffer[start_pos], kH264Header, kCodecTypeBytesCount) == 0) {
    return VideoCodecType::kVideoCodecH264;
  }
  has_error_ = true;
  RTC_LOG(LS_ERROR) << "Unknown codec type: "
                    << std::string(
                           reinterpret_cast<char const*>(&buffer[start_pos]),
                           kCodecTypeBytesCount);
  return absl::nullopt;
}

absl::optional<IvfFileReader::FrameHeader>
IvfFileReader::ReadNextFrameHeader() {
  uint8_t ivf_frame_header[kIvfFrameHeaderSize] = {0};
  size_t read = file_.Read(&ivf_frame_header, kIvfFrameHeaderSize);
  if (read != kIvfFrameHeaderSize) {
    if (read != 0 || !file_.ReadEof()) {
      has_error_ = true;
      RTC_LOG(LS_ERROR) << "Frame #" << num_read_frames_
                        << ": failed to read IVF frame header";
    }
    return absl::nullopt;
  }
  FrameHeader header;
  header.frame_size = static_cast<size_t>(
      ByteReader<uint32_t>::ReadLittleEndian(&ivf_frame_header[0]));
  header.timestamp =
      ByteReader<uint64_t>::ReadLittleEndian(&ivf_frame_header[4]);

  if (header.frame_size == 0) {
    has_error_ = true;
    RTC_LOG(LS_ERROR) << "Frame #" << num_read_frames_
                      << ": invalid frame size";
    return absl::nullopt;
  }

  if (header.timestamp < 0) {
    has_error_ = true;
    RTC_LOG(LS_ERROR) << "Frame #" << num_read_frames_
                      << ": negative timestamp";
    return absl::nullopt;
  }

  return header;
}

}  // namespace webrtc
