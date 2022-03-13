/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_VIDEO_CODING_UTILITY_IVF_FILE_READER_H_
#define MODULES_VIDEO_CODING_UTILITY_IVF_FILE_READER_H_

#include <memory>
#include <utility>

#include "absl/types/optional.h"
#include "api/video/encoded_image.h"
#include "api/video_codecs/video_codec.h"
#include "rtc_base/system/file_wrapper.h"

namespace webrtc {

class IvfFileReader {
 public:
  // Creates IvfFileReader. Returns nullptr if error acquired.
  static std::unique_ptr<IvfFileReader> Create(FileWrapper file);
  ~IvfFileReader();
  // Reinitializes reader. Returns false if any error acquired.
  bool Reset();

  // Returns codec type which was used to create this IVF file and which should
  // be used to decode EncodedImages from this file.
  VideoCodecType GetVideoCodecType() const { return codec_type_; }
  // Returns count of frames in this file.
  size_t GetFramesCount() const { return num_frames_; }

  // Returns next frame or absl::nullopt if any error acquired. Always returns
  // absl::nullopt after first error was spotted.
  absl::optional<EncodedImage> NextFrame();
  bool HasMoreFrames() const { return num_read_frames_ < num_frames_; }
  bool HasError() const { return has_error_; }

  uint16_t GetFrameWidth() const { return width_; }
  uint16_t GetFrameHeight() const { return height_; }

  bool Close();

 private:
  struct FrameHeader {
    size_t frame_size;
    int64_t timestamp;
  };

  explicit IvfFileReader(FileWrapper file) : file_(std::move(file)) {}

  // Parses codec type from specified position of the buffer. Codec type
  // contains kCodecTypeBytesCount bytes and caller has to ensure that buffer
  // won't overflow.
  absl::optional<VideoCodecType> ParseCodecType(uint8_t* buffer,
                                                size_t start_pos);
  absl::optional<FrameHeader> ReadNextFrameHeader();

  VideoCodecType codec_type_;
  size_t num_frames_;
  size_t num_read_frames_;
  uint16_t width_;
  uint16_t height_;
  bool using_capture_timestamps_;
  FileWrapper file_;

  absl::optional<FrameHeader> next_frame_header_;
  bool has_error_;

  RTC_DISALLOW_COPY_AND_ASSIGN(IvfFileReader);
};

}  // namespace webrtc

#endif  // MODULES_VIDEO_CODING_UTILITY_IVF_FILE_READER_H_
