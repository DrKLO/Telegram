/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "media/engine/internal_decoder_factory.h"

#include "absl/strings/match.h"
#include "api/video_codecs/sdp_video_format.h"
#include "media/base/codec.h"
#include "media/base/media_constants.h"
#include "modules/video_coding/codecs/av1/libaom_av1_decoder.h"
#include "modules/video_coding/codecs/h264/include/h264.h"
#include "modules/video_coding/codecs/vp8/include/vp8.h"
#include "modules/video_coding/codecs/vp9/include/vp9.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

namespace webrtc {

std::vector<SdpVideoFormat> InternalDecoderFactory::GetSupportedFormats()
    const {
  std::vector<SdpVideoFormat> formats;
  formats.push_back(SdpVideoFormat(cricket::kVp8CodecName));
  for (const SdpVideoFormat& format : SupportedVP9DecoderCodecs())
    formats.push_back(format);
  for (const SdpVideoFormat& h264_format : SupportedH264Codecs())
    formats.push_back(h264_format);
  if (kIsLibaomAv1DecoderSupported)
    formats.push_back(SdpVideoFormat(cricket::kAv1CodecName));
  return formats;
}

std::unique_ptr<VideoDecoder> InternalDecoderFactory::CreateVideoDecoder(
    const SdpVideoFormat& format) {
  if (!format.IsCodecInList(GetSupportedFormats())) {
    RTC_LOG(LS_WARNING) << "Trying to create decoder for unsupported format. "
                        << format.ToString();
    return nullptr;
  }

  if (absl::EqualsIgnoreCase(format.name, cricket::kVp8CodecName))
    return VP8Decoder::Create();
  if (absl::EqualsIgnoreCase(format.name, cricket::kVp9CodecName))
    return VP9Decoder::Create();
  if (absl::EqualsIgnoreCase(format.name, cricket::kH264CodecName))
    return H264Decoder::Create();
  if (kIsLibaomAv1DecoderSupported &&
      absl::EqualsIgnoreCase(format.name, cricket::kAv1CodecName))
    return CreateLibaomAv1Decoder();

  RTC_NOTREACHED();
  return nullptr;
}

}  // namespace webrtc
