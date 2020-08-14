/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// Everything declared/defined in this header is only required when WebRTC is
// build with H264 support, please do not move anything out of the
// #ifdef unless needed and tested.
#ifdef WEBRTC_USE_H264

#include "modules/video_coding/codecs/h264/h264_color_space.h"

namespace webrtc {

ColorSpace ExtractH264ColorSpace(AVCodecContext* codec) {
  ColorSpace::PrimaryID primaries = ColorSpace::PrimaryID::kUnspecified;
  switch (codec->color_primaries) {
    case AVCOL_PRI_BT709:
      primaries = ColorSpace::PrimaryID::kBT709;
      break;
    case AVCOL_PRI_BT470M:
      primaries = ColorSpace::PrimaryID::kBT470M;
      break;
    case AVCOL_PRI_BT470BG:
      primaries = ColorSpace::PrimaryID::kBT470BG;
      break;
    case AVCOL_PRI_SMPTE170M:
      primaries = ColorSpace::PrimaryID::kSMPTE170M;
      break;
    case AVCOL_PRI_SMPTE240M:
      primaries = ColorSpace::PrimaryID::kSMPTE240M;
      break;
    case AVCOL_PRI_FILM:
      primaries = ColorSpace::PrimaryID::kFILM;
      break;
    case AVCOL_PRI_BT2020:
      primaries = ColorSpace::PrimaryID::kBT2020;
      break;
    case AVCOL_PRI_SMPTE428:
      primaries = ColorSpace::PrimaryID::kSMPTEST428;
      break;
    case AVCOL_PRI_SMPTE431:
      primaries = ColorSpace::PrimaryID::kSMPTEST431;
      break;
    case AVCOL_PRI_SMPTE432:
      primaries = ColorSpace::PrimaryID::kSMPTEST432;
      break;
    case AVCOL_PRI_JEDEC_P22:
      primaries = ColorSpace::PrimaryID::kJEDECP22;
      break;
    case AVCOL_PRI_RESERVED0:
    case AVCOL_PRI_UNSPECIFIED:
    case AVCOL_PRI_RESERVED:
    default:
      break;
  }

  ColorSpace::TransferID transfer = ColorSpace::TransferID::kUnspecified;
  switch (codec->color_trc) {
    case AVCOL_TRC_BT709:
      transfer = ColorSpace::TransferID::kBT709;
      break;
    case AVCOL_TRC_GAMMA22:
      transfer = ColorSpace::TransferID::kGAMMA22;
      break;
    case AVCOL_TRC_GAMMA28:
      transfer = ColorSpace::TransferID::kGAMMA28;
      break;
    case AVCOL_TRC_SMPTE170M:
      transfer = ColorSpace::TransferID::kSMPTE170M;
      break;
    case AVCOL_TRC_SMPTE240M:
      transfer = ColorSpace::TransferID::kSMPTE240M;
      break;
    case AVCOL_TRC_LINEAR:
      transfer = ColorSpace::TransferID::kLINEAR;
      break;
    case AVCOL_TRC_LOG:
      transfer = ColorSpace::TransferID::kLOG;
      break;
    case AVCOL_TRC_LOG_SQRT:
      transfer = ColorSpace::TransferID::kLOG_SQRT;
      break;
    case AVCOL_TRC_IEC61966_2_4:
      transfer = ColorSpace::TransferID::kIEC61966_2_4;
      break;
    case AVCOL_TRC_BT1361_ECG:
      transfer = ColorSpace::TransferID::kBT1361_ECG;
      break;
    case AVCOL_TRC_IEC61966_2_1:
      transfer = ColorSpace::TransferID::kIEC61966_2_1;
      break;
    case AVCOL_TRC_BT2020_10:
      transfer = ColorSpace::TransferID::kBT2020_10;
      break;
    case AVCOL_TRC_BT2020_12:
      transfer = ColorSpace::TransferID::kBT2020_12;
      break;
    case AVCOL_TRC_SMPTE2084:
      transfer = ColorSpace::TransferID::kSMPTEST2084;
      break;
    case AVCOL_TRC_SMPTE428:
      transfer = ColorSpace::TransferID::kSMPTEST428;
      break;
    case AVCOL_TRC_ARIB_STD_B67:
      transfer = ColorSpace::TransferID::kARIB_STD_B67;
      break;
    case AVCOL_TRC_RESERVED0:
    case AVCOL_TRC_UNSPECIFIED:
    case AVCOL_TRC_RESERVED:
    default:
      break;
  }

  ColorSpace::MatrixID matrix = ColorSpace::MatrixID::kUnspecified;
  switch (codec->colorspace) {
    case AVCOL_SPC_RGB:
      matrix = ColorSpace::MatrixID::kRGB;
      break;
    case AVCOL_SPC_BT709:
      matrix = ColorSpace::MatrixID::kBT709;
      break;
    case AVCOL_SPC_FCC:
      matrix = ColorSpace::MatrixID::kFCC;
      break;
    case AVCOL_SPC_BT470BG:
      matrix = ColorSpace::MatrixID::kBT470BG;
      break;
    case AVCOL_SPC_SMPTE170M:
      matrix = ColorSpace::MatrixID::kSMPTE170M;
      break;
    case AVCOL_SPC_SMPTE240M:
      matrix = ColorSpace::MatrixID::kSMPTE240M;
      break;
    case AVCOL_SPC_YCGCO:
      matrix = ColorSpace::MatrixID::kYCOCG;
      break;
    case AVCOL_SPC_BT2020_NCL:
      matrix = ColorSpace::MatrixID::kBT2020_NCL;
      break;
    case AVCOL_SPC_BT2020_CL:
      matrix = ColorSpace::MatrixID::kBT2020_CL;
      break;
    case AVCOL_SPC_SMPTE2085:
      matrix = ColorSpace::MatrixID::kSMPTE2085;
      break;
    case AVCOL_SPC_CHROMA_DERIVED_NCL:
    case AVCOL_SPC_CHROMA_DERIVED_CL:
    case AVCOL_SPC_ICTCP:
    case AVCOL_SPC_UNSPECIFIED:
    case AVCOL_SPC_RESERVED:
    default:
      break;
  }

  ColorSpace::RangeID range = ColorSpace::RangeID::kInvalid;
  switch (codec->color_range) {
    case AVCOL_RANGE_MPEG:
      range = ColorSpace::RangeID::kLimited;
      break;
    case AVCOL_RANGE_JPEG:
      range = ColorSpace::RangeID::kFull;
      break;
    case AVCOL_RANGE_UNSPECIFIED:
    default:
      break;
  }
  return ColorSpace(primaries, transfer, matrix, range);
}

}  // namespace webrtc

#endif  // WEBRTC_USE_H264
