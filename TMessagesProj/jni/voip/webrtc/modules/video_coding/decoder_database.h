/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_VIDEO_CODING_DECODER_DATABASE_H_
#define MODULES_VIDEO_CODING_DECODER_DATABASE_H_

#include <map>
#include <memory>

#include "modules/video_coding/generic_decoder.h"

namespace webrtc {

struct VCMDecoderMapItem {
 public:
  VCMDecoderMapItem(VideoCodec* settings, int number_of_cores);
  ~VCMDecoderMapItem();

  std::unique_ptr<VideoCodec> settings;
  int number_of_cores;
};

struct VCMExtDecoderMapItem {
 public:
  VCMExtDecoderMapItem(VideoDecoder* external_decoder_instance,
                       uint8_t payload_type);

  uint8_t payload_type;
  VideoDecoder* external_decoder_instance;
};

class VCMDecoderDataBase {
 public:
  VCMDecoderDataBase();
  ~VCMDecoderDataBase();

  bool DeregisterExternalDecoder(uint8_t payload_type);
  void RegisterExternalDecoder(VideoDecoder* external_decoder,
                               uint8_t payload_type);
  bool IsExternalDecoderRegistered(uint8_t payload_type) const;

  bool RegisterReceiveCodec(uint8_t payload_type,
                            const VideoCodec* receive_codec,
                            int number_of_cores);
  bool DeregisterReceiveCodec(uint8_t payload_type);

  // Returns a decoder specified by frame.PayloadType. The decoded frame
  // callback of the decoder is set to |decoded_frame_callback|. If no such
  // decoder already exists an instance will be created and initialized.
  // nullptr is returned if no decoder with the specified payload type was found
  // and the function failed to create one.
  VCMGenericDecoder* GetDecoder(
      const VCMEncodedFrame& frame,
      VCMDecodedFrameCallback* decoded_frame_callback);

 private:
  typedef std::map<uint8_t, VCMDecoderMapItem*> DecoderMap;
  typedef std::map<uint8_t, VCMExtDecoderMapItem*> ExternalDecoderMap;

  std::unique_ptr<VCMGenericDecoder> CreateAndInitDecoder(
      const VCMEncodedFrame& frame,
      VideoCodec* new_codec) const;

  const VCMDecoderMapItem* FindDecoderItem(uint8_t payload_type) const;

  const VCMExtDecoderMapItem* FindExternalDecoderItem(
      uint8_t payload_type) const;

  uint8_t current_payload_type_;  // Corresponding to receive_codec_.
  VideoCodec receive_codec_;
  std::unique_ptr<VCMGenericDecoder> ptr_decoder_;
  DecoderMap dec_map_;
  ExternalDecoderMap dec_external_map_;
};

}  // namespace webrtc

#endif  // MODULES_VIDEO_CODING_DECODER_DATABASE_H_
