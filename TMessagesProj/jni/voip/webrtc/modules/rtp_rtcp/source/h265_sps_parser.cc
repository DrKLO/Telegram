/*
 * Intel License
 */

#include "webrtc/modules/rtp_rtcp/source/h265_sps_parser.h"

#include "webrtc/base/bitbuffer.h"
#include "webrtc/base/bytebuffer.h"
#include "webrtc/base/logging.h"

#include <vector>

#define RETURN_FALSE_ON_FAIL(x) \
  if (!(x)) {                   \
    return false;               \
  }

namespace webrtc {

H265SpsParser::H265SpsParser(const uint8_t* sps, size_t byte_length)
    : sps_(sps), byte_length_(byte_length), width_(), height_() {
}

bool H265SpsParser::Parse() {
  // General note: this is based off the 04/2015 version of the H.265 standard.
  // You can find it on this page:
  // http://www.itu.int/rec/T-REC-H.265

  const char* sps_bytes = reinterpret_cast<const char*>(sps_);
  // First, parse out rbsp, which is basically the source buffer minus emulation
  // bytes (the last byte of a 0x00 0x00 0x03 sequence). RBSP is defined in
  // section 7.3.1.1 of the H.265 standard, similar to H264.
  rtc::ByteBufferWriter rbsp_buffer;
  for (size_t i = 0; i < byte_length_;) {
    // Be careful about over/underflow here. byte_length_ - 3 can underflow, and
    // i + 3 can overflow, but byte_length_ - i can't, because i < byte_length_
    // above, and that expression will produce the number of bytes left in
    // the stream including the byte at i.
    if (byte_length_ - i >= 3 && sps_[i] == 0 && sps_[i + 1] == 0 &&
        sps_[i + 2] == 3) {
      // Two rbsp bytes + the emulation byte.
      rbsp_buffer.WriteBytes(sps_bytes + i, 2);
      i += 3;
    } else {
      // Single rbsp byte.
      rbsp_buffer.WriteBytes(sps_bytes + i, 1);
      i++;
    }
  }

  // Now, we need to use a bit buffer to parse through the actual HEVC SPS
  // format. See Section 7.3.2.1.1 ("Sequence parameter set data syntax") of the
  // H.265 standard for a complete description.
  // Since we only care about resolution, we ignore the majority of fields, but
  // we still have to actively parse through a lot of the data, since many of
  // the fields have variable size.
  // Unlike H264, for H265, the picture size is indicated by pic_width_in_luma_samples
  // and pic_height_in_luma_samples,  if conformance_window_flag !=1;
  // When conformance_window_flag is 1,  the width is adjusted with con_win_xx_offset
  //
  rtc::BitBuffer parser(reinterpret_cast<const uint8_t*>(rbsp_buffer.Data()),
                        rbsp_buffer.Length());

  // The golomb values we have to read, not just consume.
  uint32_t golomb_ignored;

  // separate_colour_plane_flag is optional (assumed 0), but has implications
  // about the ChromaArrayType, which modifies how we treat crop coordinates.
  uint32_t separate_colour_plane_flag = 0;
  // chroma_format_idc will be ChromaArrayType if separate_colour_plane_flag is
  // 0. It defaults to 1, when not specified.
  uint32_t chroma_format_idc = 1;


  // sps_video_parameter_set_id: u(4)
  RETURN_FALSE_ON_FAIL(parser.ConsumeBits(4));
  // sps_max_sub_layers_minus1: u(3)
  uint32_t sps_max_sub_layers_minus1 = 0;
  RETURN_FALSE_ON_FAIL(parser.ReadBits(3, sps_max_sub_layers_minus1));
  // sps_temporal_id_nesting_flag: u(1)
  RETURN_FALSE_ON_FAIL(parser.ConsumeBits(1));
  // profile_tier_level(1, sps_max_sub_layers_minus1). We are acutally not
  // using them, so read/skip over it.
  // general_profile_space+general_tier_flag+general_prfile_idc: u(8)
  RETURN_FALSE_ON_FAIL(parser.ConsumeBytes(1));
  // general_profile_compatabilitiy_flag[32]
  RETURN_FALSE_ON_FAIL(parser.ConsumeBytes(4));
  // general_progressive_source_flag + interlaced_source_flag+ non-packed_constraint
  // flag + frame_only_constraint_flag: u(4)
  RETURN_FALSE_ON_FAIL(parser.ConsumeBits(4));
  // general_profile_idc decided flags or reserved.  u(43)
  RETURN_FALSE_ON_FAIL(parser.ConsumeBits(43));
  // general_inbld_flag or reserved 0: u(1)
  RETURN_FALSE_ON_FAIL(parser.ConsumeBits(1));
  // general_level_idc: u(8)
  RETURN_FALSE_ON_FAIL(parser.ConsumeBytes(1));
  // if max_sub_layers_minus1 >=1, read the sublayer profile information
  std::vector<uint32_t> sub_layer_profile_present_flags;
  std::vector<uint32_t> sub_layer_level_present_flags;
  uint32_t sub_layer_profile_present = 0;
  uint32_t sub_layer_level_present = 0;
  for (uint32_t i = 0; i < sps_max_sub_layers_minus1; i++) {
      //sublayer_profile_present_flag and sublayer_level_presnet_flag:  u(2)
      RETURN_FALSE_ON_FAIL(parser.ReadBits(1, sub_layer_profile_present));
      RETURN_FALSE_ON_FAIL(parser.ReadBits(1, sub_layer_level_present));
      sub_layer_profile_present_flags.push_back(sub_layer_profile_present);
      sub_layer_level_present_flags.push_back(sub_layer_level_present);
  }
  if (sps_max_sub_layers_minus1 > 0) {
      for (uint32_t j = sps_max_sub_layers_minus1; j < 8; j++) {
        // reserved 2 bits: u(2)
          RETURN_FALSE_ON_FAIL(parser.ConsumeBits(2));
      }
  }
  for (uint32_t k = 0; k < sps_max_sub_layers_minus1; k++) {
      if(sub_layer_profile_present_flags[k]) {//
        // sub_layer profile_space/tier_flag/profile_idc. ignored. u(8)
        RETURN_FALSE_ON_FAIL(parser.ConsumeBytes(1));
        // profile_compatability_flag:  u(32)
        RETURN_FALSE_ON_FAIL(parser.ConsumeBytes(4));
        // sub_layer progressive_source_flag/interlaced_source_flag/
        // non_packed_constraint_flag/frame_only_constraint_flag: u(4)
        RETURN_FALSE_ON_FAIL(parser.ConsumeBits(4));
        // following 43-bits are profile_idc specific. We simply read/skip it. u(43)
        RETURN_FALSE_ON_FAIL(parser.ConsumeBits(43));
        // 1-bit profile_idc specific inbld flag.  We simply read/skip it. u(1)
        RETURN_FALSE_ON_FAIL(parser.ConsumeBits(1));
      }
      if (sub_layer_level_present_flags[k]) {
        // sub_layer_level_idc: u(8)
          RETURN_FALSE_ON_FAIL(parser.ConsumeBytes(1));
      }
  }
  //sps_seq_parameter_set_id: ue(v)
  RETURN_FALSE_ON_FAIL(parser.ReadExponentialGolomb(golomb_ignored));
  // chrome_format_idc: ue(v)
  RETURN_FALSE_ON_FAIL(parser.ReadExponentialGolomb(chroma_format_idc));
  if (chroma_format_idc == 3) {
    // seperate_colour_plane_flag: u(1)
    RETURN_FALSE_ON_FAIL(parser.ReadBits(1, separate_colour_plane_flag));
  }
  uint32_t pic_width_in_luma_samples = 0;
  uint32_t pic_height_in_luma_samples = 0;
  // pic_width_in_luma_samples: ue(v)
  RETURN_FALSE_ON_FAIL(parser.ReadExponentialGolomb(pic_width_in_luma_samples));
  // pic_height_in_luma_samples: ue(v)
  RETURN_FALSE_ON_FAIL(parser.ReadExponentialGolomb(pic_height_in_luma_samples));
  // conformance_window_flag: u(1)
  uint32_t conformance_window_flag = 0;
  RETURN_FALSE_ON_FAIL(parser.ReadBits(1, conformance_window_flag));

  uint32_t conf_win_left_offset = 0;
  uint32_t conf_win_right_offset = 0;
  uint32_t conf_win_top_offset = 0;
  uint32_t conf_win_bottom_offset = 0;
  if (conformance_window_flag) {
      // conf_win_left_offset: ue(v)
      RETURN_FALSE_ON_FAIL(parser.ReadExponentialGolomb(conf_win_left_offset));
      // conf_win_right_offset: ue(v)
      RETURN_FALSE_ON_FAIL(parser.ReadExponentialGolomb(conf_win_right_offset));
      // conf_win_top_offset: ue(v)
      RETURN_FALSE_ON_FAIL(parser.ReadExponentialGolomb(conf_win_top_offset));
      // conf_win_bottom_offset: ue(v)
      RETURN_FALSE_ON_FAIL(parser.ReadExponentialGolomb(conf_win_bottom_offset));
  }

  //For enough to get the resolution information. calcaluate according to HEVC spec 7.4.3.2
  int width = 0;
  int height = 0;

  width = pic_width_in_luma_samples;
  height = pic_height_in_luma_samples;

  if (conformance_window_flag) {
    int sub_width_c = ((1 == chroma_format_idc) || (2 == chroma_format_idc)) &&
                        (0 == separate_colour_plane_flag) ? 2 : 1;
    int sub_height_c = (1 == chroma_format_idc) && (0 == separate_colour_plane_flag) ? 2 : 1;
    //the offset includes the pixel within conformance window. so don't need to +1 as per spec
    width -= sub_width_c*(conf_win_right_offset + conf_win_left_offset);
    height -= sub_height_c*(conf_win_top_offset + conf_win_bottom_offset);
  }

  width_ = width;
  height_ = height;
  return true;

}

}  // namespace webrtc
