/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "video/frame_dumping_decoder.h"

#include <memory>
#include <utility>

#include "modules/video_coding/include/video_codec_interface.h"
#include "modules/video_coding/utility/ivf_file_writer.h"

namespace webrtc {
namespace {

class FrameDumpingDecoder : public VideoDecoder {
 public:
  FrameDumpingDecoder(std::unique_ptr<VideoDecoder> decoder, FileWrapper file);
  ~FrameDumpingDecoder() override;

  int32_t InitDecode(const VideoCodec* codec_settings,
                     int32_t number_of_cores) override;
  int32_t Decode(const EncodedImage& input_image,
                 bool missing_frames,
                 int64_t render_time_ms) override;
  int32_t RegisterDecodeCompleteCallback(
      DecodedImageCallback* callback) override;
  int32_t Release() override;
  DecoderInfo GetDecoderInfo() const override;
  const char* ImplementationName() const override;

 private:
  std::unique_ptr<VideoDecoder> decoder_;
  VideoCodecType codec_type_ = VideoCodecType::kVideoCodecGeneric;
  std::unique_ptr<IvfFileWriter> writer_;
};

FrameDumpingDecoder::FrameDumpingDecoder(std::unique_ptr<VideoDecoder> decoder,
                                         FileWrapper file)
    : decoder_(std::move(decoder)),
      writer_(IvfFileWriter::Wrap(std::move(file),
                                  /* byte_limit= */ 100000000)) {}

FrameDumpingDecoder::~FrameDumpingDecoder() = default;

int32_t FrameDumpingDecoder::InitDecode(const VideoCodec* codec_settings,
                                        int32_t number_of_cores) {
  codec_type_ = codec_settings->codecType;
  return decoder_->InitDecode(codec_settings, number_of_cores);
}

int32_t FrameDumpingDecoder::Decode(const EncodedImage& input_image,
                                    bool missing_frames,
                                    int64_t render_time_ms) {
  int32_t ret = decoder_->Decode(input_image, missing_frames, render_time_ms);
  writer_->WriteFrame(input_image, codec_type_);

  return ret;
}

int32_t FrameDumpingDecoder::RegisterDecodeCompleteCallback(
    DecodedImageCallback* callback) {
  return decoder_->RegisterDecodeCompleteCallback(callback);
}

int32_t FrameDumpingDecoder::Release() {
  return decoder_->Release();
}

VideoDecoder::DecoderInfo FrameDumpingDecoder::GetDecoderInfo() const {
  return decoder_->GetDecoderInfo();
}

const char* FrameDumpingDecoder::ImplementationName() const {
  return decoder_->ImplementationName();
}

}  // namespace

std::unique_ptr<VideoDecoder> CreateFrameDumpingDecoderWrapper(
    std::unique_ptr<VideoDecoder> decoder,
    FileWrapper file) {
  return std::make_unique<FrameDumpingDecoder>(std::move(decoder),
                                               std::move(file));
}

}  // namespace webrtc
