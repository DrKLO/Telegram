/*
 *  Copyright (c) 2019 The WebM project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <memory>
#include <vector>
#include "./ivfenc.h"
#include "vp9/common/vp9_entropymode.h"
#include "vp9/common/vp9_enums.h"
#include "vp9/common/vp9_onyxc_int.h"
#include "vp9/vp9_iface_common.h"
#include "vp9/encoder/vp9_encoder.h"
#include "vp9/encoder/vp9_firstpass.h"
#include "vp9/simple_encode.h"
#include "vp9/vp9_cx_iface.h"

namespace vp9 {

static int get_plane_height(vpx_img_fmt_t img_fmt, int frame_height,
                            int plane) {
  assert(plane < 3);
  if (plane == 0) {
    return frame_height;
  }
  switch (img_fmt) {
    case VPX_IMG_FMT_I420:
    case VPX_IMG_FMT_I440:
    case VPX_IMG_FMT_YV12:
    case VPX_IMG_FMT_I42016:
    case VPX_IMG_FMT_I44016: return (frame_height + 1) >> 1;
    default: return frame_height;
  }
}

static int get_plane_width(vpx_img_fmt_t img_fmt, int frame_width, int plane) {
  assert(plane < 3);
  if (plane == 0) {
    return frame_width;
  }
  switch (img_fmt) {
    case VPX_IMG_FMT_I420:
    case VPX_IMG_FMT_YV12:
    case VPX_IMG_FMT_I422:
    case VPX_IMG_FMT_I42016:
    case VPX_IMG_FMT_I42216: return (frame_width + 1) >> 1;
    default: return frame_width;
  }
}

// TODO(angiebird): Merge this function with vpx_img_plane_width()
static int img_plane_width(const vpx_image_t *img, int plane) {
  if (plane > 0 && img->x_chroma_shift > 0)
    return (img->d_w + 1) >> img->x_chroma_shift;
  else
    return img->d_w;
}

// TODO(angiebird): Merge this function with vpx_img_plane_height()
static int img_plane_height(const vpx_image_t *img, int plane) {
  if (plane > 0 && img->y_chroma_shift > 0)
    return (img->d_h + 1) >> img->y_chroma_shift;
  else
    return img->d_h;
}

// TODO(angiebird): Merge this function with vpx_img_read()
static int img_read(vpx_image_t *img, FILE *file) {
  int plane;

  for (plane = 0; plane < 3; ++plane) {
    unsigned char *buf = img->planes[plane];
    const int stride = img->stride[plane];
    const int w = img_plane_width(img, plane) *
                  ((img->fmt & VPX_IMG_FMT_HIGHBITDEPTH) ? 2 : 1);
    const int h = img_plane_height(img, plane);
    int y;

    for (y = 0; y < h; ++y) {
      if (fread(buf, 1, w, file) != (size_t)w) return 0;
      buf += stride;
    }
  }

  return 1;
}

class SimpleEncode::EncodeImpl {
 public:
  VP9_COMP *cpi;
  vpx_img_fmt_t img_fmt;
  vpx_image_t tmp_img;
  std::vector<FIRSTPASS_STATS> first_pass_stats;
};

static VP9_COMP *init_encoder(const VP9EncoderConfig *oxcf,
                              vpx_img_fmt_t img_fmt) {
  VP9_COMP *cpi;
  BufferPool *buffer_pool = (BufferPool *)vpx_calloc(1, sizeof(*buffer_pool));
  vp9_initialize_enc();
  cpi = vp9_create_compressor(oxcf, buffer_pool);
  vp9_update_compressor_with_img_fmt(cpi, img_fmt);
  return cpi;
}

static void free_encoder(VP9_COMP *cpi) {
  BufferPool *buffer_pool = cpi->common.buffer_pool;
  vp9_remove_compressor(cpi);
  // buffer_pool needs to be free after cpi because buffer_pool contains
  // allocated buffers that will be free in vp9_remove_compressor()
  vpx_free(buffer_pool);
}

static INLINE vpx_rational_t make_vpx_rational(int num, int den) {
  vpx_rational_t v;
  v.num = num;
  v.den = den;
  return v;
}

static INLINE FrameType
get_frame_type_from_update_type(FRAME_UPDATE_TYPE update_type) {
  switch (update_type) {
    case KF_UPDATE: return kFrameTypeKey;
    case ARF_UPDATE: return kFrameTypeAltRef;
    case GF_UPDATE: return kFrameTypeGolden;
    case OVERLAY_UPDATE: return kFrameTypeOverlay;
    case LF_UPDATE: return kFrameTypeInter;
    default:
      fprintf(stderr, "Unsupported update_type %d\n", update_type);
      abort();
      return kFrameTypeInter;
  }
}

static void update_partition_info(const PARTITION_INFO *input_partition_info,
                                  const int num_rows_4x4,
                                  const int num_cols_4x4,
                                  PartitionInfo *output_partition_info) {
  const int num_units_4x4 = num_rows_4x4 * num_cols_4x4;
  for (int i = 0; i < num_units_4x4; ++i) {
    output_partition_info[i].row = input_partition_info[i].row;
    output_partition_info[i].column = input_partition_info[i].column;
    output_partition_info[i].row_start = input_partition_info[i].row_start;
    output_partition_info[i].column_start =
        input_partition_info[i].column_start;
    output_partition_info[i].width = input_partition_info[i].width;
    output_partition_info[i].height = input_partition_info[i].height;
  }
}

// translate MV_REFERENCE_FRAME to RefFrameType
static RefFrameType mv_ref_frame_to_ref_frame_type(
    MV_REFERENCE_FRAME mv_ref_frame) {
  switch (mv_ref_frame) {
    case LAST_FRAME: return kRefFrameTypeLast;
    case GOLDEN_FRAME: return kRefFrameTypePast;
    case ALTREF_FRAME: return kRefFrameTypeFuture;
    default: return kRefFrameTypeNone;
  }
}

static void update_motion_vector_info(
    const MOTION_VECTOR_INFO *input_motion_vector_info, const int num_rows_4x4,
    const int num_cols_4x4, MotionVectorInfo *output_motion_vector_info) {
  const int num_units_4x4 = num_rows_4x4 * num_cols_4x4;
  for (int i = 0; i < num_units_4x4; ++i) {
    const MV_REFERENCE_FRAME *in_ref_frame =
        input_motion_vector_info[i].ref_frame;
    output_motion_vector_info[i].mv_count =
        (in_ref_frame[0] == INTRA_FRAME) ? 0
                                         : ((in_ref_frame[1] == NONE) ? 1 : 2);
    if (in_ref_frame[0] == NONE) {
      fprintf(stderr, "in_ref_frame[0] shouldn't be NONE\n");
      abort();
    }
    output_motion_vector_info[i].ref_frame[0] =
        mv_ref_frame_to_ref_frame_type(in_ref_frame[0]);
    output_motion_vector_info[i].ref_frame[1] =
        mv_ref_frame_to_ref_frame_type(in_ref_frame[1]);
    output_motion_vector_info[i].mv_row[0] =
        (double)input_motion_vector_info[i].mv[0].as_mv.row /
        kMotionVectorPrecision;
    output_motion_vector_info[i].mv_column[0] =
        (double)input_motion_vector_info[i].mv[0].as_mv.col /
        kMotionVectorPrecision;
    output_motion_vector_info[i].mv_row[1] =
        (double)input_motion_vector_info[i].mv[1].as_mv.row /
        kMotionVectorPrecision;
    output_motion_vector_info[i].mv_column[1] =
        (double)input_motion_vector_info[i].mv[1].as_mv.col /
        kMotionVectorPrecision;
  }
}

static void update_frame_counts(const FRAME_COUNTS *input_counts,
                                FrameCounts *output_counts) {
  // Init array sizes.
  output_counts->y_mode.resize(BLOCK_SIZE_GROUPS);
  for (int i = 0; i < BLOCK_SIZE_GROUPS; ++i) {
    output_counts->y_mode[i].resize(INTRA_MODES);
  }

  output_counts->uv_mode.resize(INTRA_MODES);
  for (int i = 0; i < INTRA_MODES; ++i) {
    output_counts->uv_mode[i].resize(INTRA_MODES);
  }

  output_counts->partition.resize(PARTITION_CONTEXTS);
  for (int i = 0; i < PARTITION_CONTEXTS; ++i) {
    output_counts->partition[i].resize(PARTITION_TYPES);
  }

  output_counts->coef.resize(TX_SIZES);
  output_counts->eob_branch.resize(TX_SIZES);
  for (int i = 0; i < TX_SIZES; ++i) {
    output_counts->coef[i].resize(PLANE_TYPES);
    output_counts->eob_branch[i].resize(PLANE_TYPES);
    for (int j = 0; j < PLANE_TYPES; ++j) {
      output_counts->coef[i][j].resize(REF_TYPES);
      output_counts->eob_branch[i][j].resize(REF_TYPES);
      for (int k = 0; k < REF_TYPES; ++k) {
        output_counts->coef[i][j][k].resize(COEF_BANDS);
        output_counts->eob_branch[i][j][k].resize(COEF_BANDS);
        for (int l = 0; l < COEF_BANDS; ++l) {
          output_counts->coef[i][j][k][l].resize(COEFF_CONTEXTS);
          output_counts->eob_branch[i][j][k][l].resize(COEFF_CONTEXTS);
          for (int m = 0; m < COEFF_CONTEXTS; ++m) {
            output_counts->coef[i][j][k][l][m].resize(UNCONSTRAINED_NODES + 1);
          }
        }
      }
    }
  }

  output_counts->switchable_interp.resize(SWITCHABLE_FILTER_CONTEXTS);
  for (int i = 0; i < SWITCHABLE_FILTER_CONTEXTS; ++i) {
    output_counts->switchable_interp[i].resize(SWITCHABLE_FILTERS);
  }

  output_counts->inter_mode.resize(INTER_MODE_CONTEXTS);
  for (int i = 0; i < INTER_MODE_CONTEXTS; ++i) {
    output_counts->inter_mode[i].resize(INTER_MODES);
  }

  output_counts->intra_inter.resize(INTRA_INTER_CONTEXTS);
  for (int i = 0; i < INTRA_INTER_CONTEXTS; ++i) {
    output_counts->intra_inter[i].resize(2);
  }

  output_counts->comp_inter.resize(COMP_INTER_CONTEXTS);
  for (int i = 0; i < COMP_INTER_CONTEXTS; ++i) {
    output_counts->comp_inter[i].resize(2);
  }

  output_counts->single_ref.resize(REF_CONTEXTS);
  for (int i = 0; i < REF_CONTEXTS; ++i) {
    output_counts->single_ref[i].resize(2);
    for (int j = 0; j < 2; ++j) {
      output_counts->single_ref[i][j].resize(2);
    }
  }

  output_counts->comp_ref.resize(REF_CONTEXTS);
  for (int i = 0; i < REF_CONTEXTS; ++i) {
    output_counts->comp_ref[i].resize(2);
  }

  output_counts->skip.resize(SKIP_CONTEXTS);
  for (int i = 0; i < SKIP_CONTEXTS; ++i) {
    output_counts->skip[i].resize(2);
  }

  output_counts->tx.p32x32.resize(TX_SIZE_CONTEXTS);
  output_counts->tx.p16x16.resize(TX_SIZE_CONTEXTS);
  output_counts->tx.p8x8.resize(TX_SIZE_CONTEXTS);
  for (int i = 0; i < TX_SIZE_CONTEXTS; i++) {
    output_counts->tx.p32x32[i].resize(TX_SIZES);
    output_counts->tx.p16x16[i].resize(TX_SIZES - 1);
    output_counts->tx.p8x8[i].resize(TX_SIZES - 2);
  }
  output_counts->tx.tx_totals.resize(TX_SIZES);

  output_counts->mv.joints.resize(MV_JOINTS);
  output_counts->mv.comps.resize(2);
  for (int i = 0; i < 2; ++i) {
    output_counts->mv.comps[i].sign.resize(2);
    output_counts->mv.comps[i].classes.resize(MV_CLASSES);
    output_counts->mv.comps[i].class0.resize(CLASS0_SIZE);
    output_counts->mv.comps[i].bits.resize(MV_OFFSET_BITS);
    for (int j = 0; j < MV_OFFSET_BITS; ++j) {
      output_counts->mv.comps[i].bits[j].resize(2);
    }
    output_counts->mv.comps[i].class0_fp.resize(CLASS0_SIZE);
    for (int j = 0; j < CLASS0_SIZE; ++j) {
      output_counts->mv.comps[i].class0_fp[j].resize(MV_FP_SIZE);
    }
    output_counts->mv.comps[i].fp.resize(MV_FP_SIZE);
    output_counts->mv.comps[i].class0_hp.resize(2);
    output_counts->mv.comps[i].hp.resize(2);
  }

  // Populate counts.
  for (int i = 0; i < BLOCK_SIZE_GROUPS; ++i) {
    for (int j = 0; j < INTRA_MODES; ++j) {
      output_counts->y_mode[i][j] = input_counts->y_mode[i][j];
    }
  }
  for (int i = 0; i < INTRA_MODES; ++i) {
    for (int j = 0; j < INTRA_MODES; ++j) {
      output_counts->uv_mode[i][j] = input_counts->uv_mode[i][j];
    }
  }
  for (int i = 0; i < PARTITION_CONTEXTS; ++i) {
    for (int j = 0; j < PARTITION_TYPES; ++j) {
      output_counts->partition[i][j] = input_counts->partition[i][j];
    }
  }
  for (int i = 0; i < TX_SIZES; ++i) {
    for (int j = 0; j < PLANE_TYPES; ++j) {
      for (int k = 0; k < REF_TYPES; ++k) {
        for (int l = 0; l < COEF_BANDS; ++l) {
          for (int m = 0; m < COEFF_CONTEXTS; ++m) {
            output_counts->eob_branch[i][j][k][l][m] =
                input_counts->eob_branch[i][j][k][l][m];
            for (int n = 0; n < UNCONSTRAINED_NODES + 1; n++) {
              output_counts->coef[i][j][k][l][m][n] =
                  input_counts->coef[i][j][k][l][m][n];
            }
          }
        }
      }
    }
  }
  for (int i = 0; i < SWITCHABLE_FILTER_CONTEXTS; ++i) {
    for (int j = 0; j < SWITCHABLE_FILTERS; ++j) {
      output_counts->switchable_interp[i][j] =
          input_counts->switchable_interp[i][j];
    }
  }
  for (int i = 0; i < INTER_MODE_CONTEXTS; ++i) {
    for (int j = 0; j < INTER_MODES; ++j) {
      output_counts->inter_mode[i][j] = input_counts->inter_mode[i][j];
    }
  }
  for (int i = 0; i < INTRA_INTER_CONTEXTS; ++i) {
    for (int j = 0; j < 2; ++j) {
      output_counts->intra_inter[i][j] = input_counts->intra_inter[i][j];
    }
  }
  for (int i = 0; i < COMP_INTER_CONTEXTS; ++i) {
    for (int j = 0; j < 2; ++j) {
      output_counts->comp_inter[i][j] = input_counts->comp_inter[i][j];
    }
  }
  for (int i = 0; i < REF_CONTEXTS; ++i) {
    for (int j = 0; j < 2; ++j) {
      for (int k = 0; k < 2; ++k) {
        output_counts->single_ref[i][j][k] = input_counts->single_ref[i][j][k];
      }
    }
  }
  for (int i = 0; i < REF_CONTEXTS; ++i) {
    for (int j = 0; j < 2; ++j) {
      output_counts->comp_ref[i][j] = input_counts->comp_ref[i][j];
    }
  }
  for (int i = 0; i < SKIP_CONTEXTS; ++i) {
    for (int j = 0; j < 2; ++j) {
      output_counts->skip[i][j] = input_counts->skip[i][j];
    }
  }
  for (int i = 0; i < TX_SIZE_CONTEXTS; i++) {
    for (int j = 0; j < TX_SIZES; j++) {
      output_counts->tx.p32x32[i][j] = input_counts->tx.p32x32[i][j];
    }
    for (int j = 0; j < TX_SIZES - 1; j++) {
      output_counts->tx.p16x16[i][j] = input_counts->tx.p16x16[i][j];
    }
    for (int j = 0; j < TX_SIZES - 2; j++) {
      output_counts->tx.p8x8[i][j] = input_counts->tx.p8x8[i][j];
    }
  }
  for (int i = 0; i < TX_SIZES; i++) {
    output_counts->tx.tx_totals[i] = input_counts->tx.tx_totals[i];
  }
  for (int i = 0; i < MV_JOINTS; i++) {
    output_counts->mv.joints[i] = input_counts->mv.joints[i];
  }
  for (int k = 0; k < 2; k++) {
    const nmv_component_counts *const comps_t = &input_counts->mv.comps[k];
    for (int i = 0; i < 2; i++) {
      output_counts->mv.comps[k].sign[i] = comps_t->sign[i];
      output_counts->mv.comps[k].class0_hp[i] = comps_t->class0_hp[i];
      output_counts->mv.comps[k].hp[i] = comps_t->hp[i];
    }
    for (int i = 0; i < MV_CLASSES; i++) {
      output_counts->mv.comps[k].classes[i] = comps_t->classes[i];
    }
    for (int i = 0; i < CLASS0_SIZE; i++) {
      output_counts->mv.comps[k].class0[i] = comps_t->class0[i];
      for (int j = 0; j < MV_FP_SIZE; j++) {
        output_counts->mv.comps[k].class0_fp[i][j] = comps_t->class0_fp[i][j];
      }
    }
    for (int i = 0; i < MV_OFFSET_BITS; i++) {
      for (int j = 0; j < 2; j++) {
        output_counts->mv.comps[k].bits[i][j] = comps_t->bits[i][j];
      }
    }
    for (int i = 0; i < MV_FP_SIZE; i++) {
      output_counts->mv.comps[k].fp[i] = comps_t->fp[i];
    }
  }
}

void output_image_buffer(const ImageBuffer &image_buffer, std::FILE *out_file) {
  for (int plane = 0; plane < 3; ++plane) {
    const int w = image_buffer.plane_width[plane];
    const int h = image_buffer.plane_height[plane];
    const uint8_t *buf = image_buffer.plane_buffer[plane].get();
    fprintf(out_file, "%d %d\n", h, w);
    for (int i = 0; i < w * h; ++i) {
      fprintf(out_file, "%d ", (int)buf[i]);
    }
    fprintf(out_file, "\n");
  }
}

static bool init_image_buffer(ImageBuffer *image_buffer, int frame_width,
                              int frame_height, vpx_img_fmt_t img_fmt) {
  for (int plane = 0; plane < 3; ++plane) {
    const int w = get_plane_width(img_fmt, frame_width, plane);
    const int h = get_plane_height(img_fmt, frame_height, plane);
    image_buffer->plane_width[plane] = w;
    image_buffer->plane_height[plane] = h;
    image_buffer->plane_buffer[plane].reset(new (std::nothrow) uint8_t[w * h]);
    if (image_buffer->plane_buffer[plane].get() == nullptr) {
      return false;
    }
  }
  return true;
}

static void ImageBuffer_to_IMAGE_BUFFER(const ImageBuffer &image_buffer,
                                        IMAGE_BUFFER *image_buffer_c) {
  image_buffer_c->allocated = 1;
  for (int plane = 0; plane < 3; ++plane) {
    image_buffer_c->plane_width[plane] = image_buffer.plane_width[plane];
    image_buffer_c->plane_height[plane] = image_buffer.plane_height[plane];
    image_buffer_c->plane_buffer[plane] =
        image_buffer.plane_buffer[plane].get();
  }
}

static size_t get_max_coding_data_byte_size(int frame_width, int frame_height) {
  return frame_width * frame_height * 3;
}

static bool init_encode_frame_result(EncodeFrameResult *encode_frame_result,
                                     int frame_width, int frame_height,
                                     vpx_img_fmt_t img_fmt) {
  const size_t max_coding_data_byte_size =
      get_max_coding_data_byte_size(frame_width, frame_height);

  encode_frame_result->coding_data.reset(
      new (std::nothrow) uint8_t[max_coding_data_byte_size]);

  encode_frame_result->num_rows_4x4 = get_num_unit_4x4(frame_width);
  encode_frame_result->num_cols_4x4 = get_num_unit_4x4(frame_height);
  encode_frame_result->partition_info.resize(encode_frame_result->num_rows_4x4 *
                                             encode_frame_result->num_cols_4x4);
  encode_frame_result->motion_vector_info.resize(
      encode_frame_result->num_rows_4x4 * encode_frame_result->num_cols_4x4);

  if (encode_frame_result->coding_data.get() == nullptr) {
    return false;
  }
  return init_image_buffer(&encode_frame_result->coded_frame, frame_width,
                           frame_height, img_fmt);
}

static void update_encode_frame_result(
    EncodeFrameResult *encode_frame_result,
    const ENCODE_FRAME_RESULT *encode_frame_info) {
  encode_frame_result->coding_data_bit_size =
      encode_frame_result->coding_data_byte_size * 8;
  encode_frame_result->show_idx = encode_frame_info->show_idx;
  encode_frame_result->coding_idx = encode_frame_info->frame_coding_index;
  assert(kRefFrameTypeMax == MAX_INTER_REF_FRAMES);
  for (int i = 0; i < kRefFrameTypeMax; ++i) {
    encode_frame_result->ref_frame_info.coding_indexes[i] =
        encode_frame_info->ref_frame_coding_indexes[i];
    encode_frame_result->ref_frame_info.valid_list[i] =
        encode_frame_info->ref_frame_valid_list[i];
  }
  encode_frame_result->frame_type =
      get_frame_type_from_update_type(encode_frame_info->update_type);
  encode_frame_result->psnr = encode_frame_info->psnr;
  encode_frame_result->sse = encode_frame_info->sse;
  encode_frame_result->quantize_index = encode_frame_info->quantize_index;
  update_partition_info(encode_frame_info->partition_info,
                        encode_frame_result->num_rows_4x4,
                        encode_frame_result->num_cols_4x4,
                        &encode_frame_result->partition_info[0]);
  update_motion_vector_info(encode_frame_info->motion_vector_info,
                            encode_frame_result->num_rows_4x4,
                            encode_frame_result->num_cols_4x4,
                            &encode_frame_result->motion_vector_info[0]);
  update_frame_counts(&encode_frame_info->frame_counts,
                      &encode_frame_result->frame_counts);
}

static void IncreaseGroupOfPictureIndex(GroupOfPicture *group_of_picture) {
  ++group_of_picture->next_encode_frame_index;
}

static int IsGroupOfPictureFinished(const GroupOfPicture &group_of_picture) {
  return static_cast<size_t>(group_of_picture.next_encode_frame_index) ==
         group_of_picture.encode_frame_list.size();
}

bool operator==(const RefFrameInfo &a, const RefFrameInfo &b) {
  bool match = true;
  for (int i = 0; i < kRefFrameTypeMax; ++i) {
    match &= a.coding_indexes[i] == b.coding_indexes[i];
    match &= a.valid_list[i] == b.valid_list[i];
  }
  return match;
}

static void InitRefFrameInfo(RefFrameInfo *ref_frame_info) {
  for (int i = 0; i < kRefFrameTypeMax; ++i) {
    ref_frame_info->coding_indexes[i] = -1;
    ref_frame_info->valid_list[i] = 0;
  }
}

// After finishing coding a frame, this function will update the coded frame
// into the ref_frame_info based on the frame_type and the coding_index.
static void PostUpdateRefFrameInfo(FrameType frame_type, int frame_coding_index,
                                   RefFrameInfo *ref_frame_info) {
  // This part is written based on the logics in vp9_configure_buffer_updates()
  // and update_ref_frames()
  int *ref_frame_coding_indexes = ref_frame_info->coding_indexes;
  switch (frame_type) {
    case kFrameTypeKey:
      ref_frame_coding_indexes[kRefFrameTypeLast] = frame_coding_index;
      ref_frame_coding_indexes[kRefFrameTypePast] = frame_coding_index;
      ref_frame_coding_indexes[kRefFrameTypeFuture] = frame_coding_index;
      break;
    case kFrameTypeInter:
      ref_frame_coding_indexes[kRefFrameTypeLast] = frame_coding_index;
      break;
    case kFrameTypeAltRef:
      ref_frame_coding_indexes[kRefFrameTypeFuture] = frame_coding_index;
      break;
    case kFrameTypeOverlay:
      // Reserve the past coding_index in the future slot. This logic is from
      // update_ref_frames() with condition vp9_preserve_existing_gf() == 1
      // TODO(angiebird): Invetegate why we need this.
      ref_frame_coding_indexes[kRefFrameTypeFuture] =
          ref_frame_coding_indexes[kRefFrameTypePast];
      ref_frame_coding_indexes[kRefFrameTypePast] = frame_coding_index;
      break;
    case kFrameTypeGolden:
      ref_frame_coding_indexes[kRefFrameTypePast] = frame_coding_index;
      ref_frame_coding_indexes[kRefFrameTypeLast] = frame_coding_index;
      break;
  }

  //  This part is written based on the logics in get_ref_frame_flags() but we
  //  rename the flags alt, golden to future, past respectively. Mark
  //  non-duplicated reference frames as valid. The priorities are
  //  kRefFrameTypeLast > kRefFrameTypePast > kRefFrameTypeFuture.
  const int last_index = ref_frame_coding_indexes[kRefFrameTypeLast];
  const int past_index = ref_frame_coding_indexes[kRefFrameTypePast];
  const int future_index = ref_frame_coding_indexes[kRefFrameTypeFuture];

  int *ref_frame_valid_list = ref_frame_info->valid_list;
  for (int ref_frame_idx = 0; ref_frame_idx < kRefFrameTypeMax;
       ++ref_frame_idx) {
    ref_frame_valid_list[ref_frame_idx] = 1;
  }

  if (past_index == last_index) {
    ref_frame_valid_list[kRefFrameTypePast] = 0;
  }

  if (future_index == last_index) {
    ref_frame_valid_list[kRefFrameTypeFuture] = 0;
  }

  if (future_index == past_index) {
    ref_frame_valid_list[kRefFrameTypeFuture] = 0;
  }
}

static void SetGroupOfPicture(int first_is_key_frame, int use_alt_ref,
                              int coding_frame_count, int first_show_idx,
                              int last_gop_use_alt_ref, int start_coding_index,
                              const RefFrameInfo &start_ref_frame_info,
                              GroupOfPicture *group_of_picture) {
  // Clean up the state of previous group of picture.
  group_of_picture->encode_frame_list.clear();
  group_of_picture->next_encode_frame_index = 0;
  group_of_picture->show_frame_count = coding_frame_count - use_alt_ref;
  group_of_picture->start_show_index = first_show_idx;
  group_of_picture->start_coding_index = start_coding_index;

  // We need to make a copy of start reference frame info because we
  // use it to simulate the ref frame update.
  RefFrameInfo ref_frame_info = start_ref_frame_info;

  {
    // First frame in the group of pictures. It's either key frame or show inter
    // frame.
    EncodeFrameInfo encode_frame_info;
    // Set frame_type
    if (first_is_key_frame) {
      encode_frame_info.frame_type = kFrameTypeKey;
    } else {
      if (last_gop_use_alt_ref) {
        encode_frame_info.frame_type = kFrameTypeOverlay;
      } else {
        encode_frame_info.frame_type = kFrameTypeGolden;
      }
    }

    encode_frame_info.show_idx = first_show_idx;
    encode_frame_info.coding_index = start_coding_index;

    encode_frame_info.ref_frame_info = ref_frame_info;
    PostUpdateRefFrameInfo(encode_frame_info.frame_type,
                           encode_frame_info.coding_index, &ref_frame_info);

    group_of_picture->encode_frame_list.push_back(encode_frame_info);
  }

  const int show_frame_count = coding_frame_count - use_alt_ref;
  if (use_alt_ref) {
    // If there is alternate reference, it is always coded at the second place.
    // Its show index (or timestamp) is at the last of this group
    EncodeFrameInfo encode_frame_info;
    encode_frame_info.frame_type = kFrameTypeAltRef;
    encode_frame_info.show_idx = first_show_idx + show_frame_count;
    encode_frame_info.coding_index = start_coding_index + 1;

    encode_frame_info.ref_frame_info = ref_frame_info;
    PostUpdateRefFrameInfo(encode_frame_info.frame_type,
                           encode_frame_info.coding_index, &ref_frame_info);

    group_of_picture->encode_frame_list.push_back(encode_frame_info);
  }

  // Encode the rest show inter frames.
  for (int i = 1; i < show_frame_count; ++i) {
    EncodeFrameInfo encode_frame_info;
    encode_frame_info.frame_type = kFrameTypeInter;
    encode_frame_info.show_idx = first_show_idx + i;
    encode_frame_info.coding_index = start_coding_index + use_alt_ref + i;

    encode_frame_info.ref_frame_info = ref_frame_info;
    PostUpdateRefFrameInfo(encode_frame_info.frame_type,
                           encode_frame_info.coding_index, &ref_frame_info);

    group_of_picture->encode_frame_list.push_back(encode_frame_info);
  }
}

// Gets group of picture information from VP9's decision, and update
// |group_of_picture| accordingly.
// This is called at the starting of encoding of each group of picture.
static void UpdateGroupOfPicture(const VP9_COMP *cpi, int start_coding_index,
                                 const RefFrameInfo &start_ref_frame_info,
                                 GroupOfPicture *group_of_picture) {
  int first_is_key_frame;
  int use_alt_ref;
  int coding_frame_count;
  int first_show_idx;
  int last_gop_use_alt_ref;
  vp9_get_next_group_of_picture(cpi, &first_is_key_frame, &use_alt_ref,
                                &coding_frame_count, &first_show_idx,
                                &last_gop_use_alt_ref);
  SetGroupOfPicture(first_is_key_frame, use_alt_ref, coding_frame_count,
                    first_show_idx, last_gop_use_alt_ref, start_coding_index,
                    start_ref_frame_info, group_of_picture);
}

SimpleEncode::SimpleEncode(int frame_width, int frame_height,
                           int frame_rate_num, int frame_rate_den,
                           int target_bitrate, int num_frames,
                           const char *infile_path, const char *outfile_path) {
  impl_ptr_ = std::unique_ptr<EncodeImpl>(new EncodeImpl());
  frame_width_ = frame_width;
  frame_height_ = frame_height;
  frame_rate_num_ = frame_rate_num;
  frame_rate_den_ = frame_rate_den;
  target_bitrate_ = target_bitrate;
  num_frames_ = num_frames;

  frame_coding_index_ = 0;
  show_frame_count_ = 0;

  key_frame_group_index_ = 0;
  key_frame_group_size_ = 0;

  // TODO(angirbid): Should we keep a file pointer here or keep the file_path?
  assert(infile_path != nullptr);
  in_file_ = fopen(infile_path, "r");
  if (outfile_path != nullptr) {
    out_file_ = fopen(outfile_path, "w");
  } else {
    out_file_ = nullptr;
  }
  impl_ptr_->cpi = nullptr;
  impl_ptr_->img_fmt = VPX_IMG_FMT_I420;

  InitRefFrameInfo(&ref_frame_info_);
}

void SimpleEncode::ComputeFirstPassStats() {
  vpx_rational_t frame_rate =
      make_vpx_rational(frame_rate_num_, frame_rate_den_);
  const VP9EncoderConfig oxcf =
      vp9_get_encoder_config(frame_width_, frame_height_, frame_rate,
                             target_bitrate_, VPX_RC_FIRST_PASS);
  VP9_COMP *cpi = init_encoder(&oxcf, impl_ptr_->img_fmt);
  struct lookahead_ctx *lookahead = cpi->lookahead;
  int i;
  int use_highbitdepth = 0;
#if CONFIG_VP9_HIGHBITDEPTH
  use_highbitdepth = cpi->common.use_highbitdepth;
#endif
  vpx_image_t img;
  vpx_img_alloc(&img, impl_ptr_->img_fmt, frame_width_, frame_height_, 1);
  rewind(in_file_);
  impl_ptr_->first_pass_stats.clear();
  for (i = 0; i < num_frames_; ++i) {
    assert(!vp9_lookahead_full(lookahead));
    if (img_read(&img, in_file_)) {
      int next_show_idx = vp9_lookahead_next_show_idx(lookahead);
      int64_t ts_start =
          timebase_units_to_ticks(&oxcf.g_timebase_in_ts, next_show_idx);
      int64_t ts_end =
          timebase_units_to_ticks(&oxcf.g_timebase_in_ts, next_show_idx + 1);
      YV12_BUFFER_CONFIG sd;
      image2yuvconfig(&img, &sd);
      vp9_lookahead_push(lookahead, &sd, ts_start, ts_end, use_highbitdepth, 0);
      {
        int64_t time_stamp;
        int64_t time_end;
        int flush = 1;  // Makes vp9_get_compressed_data process a frame
        size_t size;
        unsigned int frame_flags = 0;
        ENCODE_FRAME_RESULT encode_frame_info;
        vp9_init_encode_frame_result(&encode_frame_info);
        // TODO(angiebird): Call vp9_first_pass directly
        vp9_get_compressed_data(cpi, &frame_flags, &size, nullptr, &time_stamp,
                                &time_end, flush, &encode_frame_info);
        // vp9_get_compressed_data only generates first pass stats not
        // compresses data
        assert(size == 0);
      }
      impl_ptr_->first_pass_stats.push_back(vp9_get_frame_stats(&cpi->twopass));
    }
  }
  vp9_end_first_pass(cpi);
  // TODO(angiebird): Store the total_stats apart form first_pass_stats
  impl_ptr_->first_pass_stats.push_back(vp9_get_total_stats(&cpi->twopass));
  free_encoder(cpi);
  rewind(in_file_);
  vpx_img_free(&img);
}

std::vector<std::vector<double>> SimpleEncode::ObserveFirstPassStats() {
  std::vector<std::vector<double>> output_stats;
  // TODO(angiebird): This function make several assumptions of
  // FIRSTPASS_STATS. 1) All elements in FIRSTPASS_STATS are double except the
  // last one. 2) The last entry of first_pass_stats is the total_stats.
  // Change the code structure, so that we don't have to make these assumptions

  // Note the last entry of first_pass_stats is the total_stats, we don't need
  // it.
  for (size_t i = 0; i < impl_ptr_->first_pass_stats.size() - 1; ++i) {
    double *buf_start =
        reinterpret_cast<double *>(&impl_ptr_->first_pass_stats[i]);
    // We use - 1 here because the last member in FIRSTPASS_STATS is not double
    double *buf_end =
        buf_start + sizeof(impl_ptr_->first_pass_stats[i]) / sizeof(*buf_end) -
        1;
    std::vector<double> this_stats(buf_start, buf_end);
    output_stats.push_back(this_stats);
  }
  return output_stats;
}

void SimpleEncode::SetExternalGroupOfPicture(
    std::vector<int> external_arf_indexes) {
  external_arf_indexes_ = external_arf_indexes;
}

template <typename T>
T *GetVectorData(const std::vector<T> &v) {
  if (v.empty()) {
    return nullptr;
  }
  return const_cast<T *>(v.data());
}

void SimpleEncode::StartEncode() {
  assert(impl_ptr_->first_pass_stats.size() > 0);
  vpx_rational_t frame_rate =
      make_vpx_rational(frame_rate_num_, frame_rate_den_);
  VP9EncoderConfig oxcf =
      vp9_get_encoder_config(frame_width_, frame_height_, frame_rate,
                             target_bitrate_, VPX_RC_LAST_PASS);
  vpx_fixed_buf_t stats;
  stats.buf = GetVectorData(impl_ptr_->first_pass_stats);
  stats.sz = sizeof(impl_ptr_->first_pass_stats[0]) *
             impl_ptr_->first_pass_stats.size();

  vp9_set_first_pass_stats(&oxcf, &stats);
  assert(impl_ptr_->cpi == nullptr);
  impl_ptr_->cpi = init_encoder(&oxcf, impl_ptr_->img_fmt);
  vpx_img_alloc(&impl_ptr_->tmp_img, impl_ptr_->img_fmt, frame_width_,
                frame_height_, 1);

  frame_coding_index_ = 0;
  show_frame_count_ = 0;

  encode_command_set_external_arf_indexes(&impl_ptr_->cpi->encode_command,
                                          GetVectorData(external_arf_indexes_));

  UpdateKeyFrameGroup(show_frame_count_);

  UpdateGroupOfPicture(impl_ptr_->cpi, frame_coding_index_, ref_frame_info_,
                       &group_of_picture_);
  rewind(in_file_);

  if (out_file_ != nullptr) {
    const char *fourcc = "VP90";
    // In SimpleEncode, we use time_base = 1 / TICKS_PER_SEC.
    // Based on that, the ivf_timestamp for each image is set to
    // show_idx * TICKS_PER_SEC / frame_rate
    // such that each image's actual timestamp in seconds can be computed as
    // ivf_timestamp * time_base == show_idx / frame_rate
    // TODO(angiebird): 1) Add unit test for ivf timestamp.
    // 2) Simplify the frame_rate setting process.
    vpx_rational_t time_base = make_vpx_rational(1, TICKS_PER_SEC);
    ivf_write_file_header_with_video_info(out_file_, *(const uint32_t *)fourcc,
                                          num_frames_, frame_width_,
                                          frame_height_, time_base);
  }
}

void SimpleEncode::EndEncode() {
  free_encoder(impl_ptr_->cpi);
  impl_ptr_->cpi = nullptr;
  vpx_img_free(&impl_ptr_->tmp_img);
  rewind(in_file_);
}

void SimpleEncode::UpdateKeyFrameGroup(int key_frame_show_index) {
  const VP9_COMP *cpi = impl_ptr_->cpi;
  key_frame_group_index_ = 0;
  key_frame_group_size_ = vp9_get_frames_to_next_key(
      &cpi->oxcf, &cpi->frame_info, &cpi->twopass.first_pass_info,
      key_frame_show_index, cpi->rc.min_gf_interval);
  assert(key_frame_group_size_ > 0);
  // Init the reference frame info when a new key frame group appears.
  InitRefFrameInfo(&ref_frame_info_);
}

void SimpleEncode::PostUpdateKeyFrameGroupIndex(FrameType frame_type) {
  if (frame_type != kFrameTypeAltRef) {
    // key_frame_group_index_ only counts show frames
    ++key_frame_group_index_;
  }
}

int SimpleEncode::GetKeyFrameGroupSize() const { return key_frame_group_size_; }

GroupOfPicture SimpleEncode::ObserveGroupOfPicture() const {
  return group_of_picture_;
}

EncodeFrameInfo SimpleEncode::GetNextEncodeFrameInfo() const {
  return group_of_picture_
      .encode_frame_list[group_of_picture_.next_encode_frame_index];
}

void SimpleEncode::PostUpdateState(
    const EncodeFrameResult &encode_frame_result) {
  // This function needs to be called before the increament of
  // frame_coding_index_
  PostUpdateRefFrameInfo(encode_frame_result.frame_type, frame_coding_index_,
                         &ref_frame_info_);
  ++frame_coding_index_;
  if (encode_frame_result.frame_type != kFrameTypeAltRef) {
    // Only kFrameTypeAltRef is not a show frame
    ++show_frame_count_;
  }

  PostUpdateKeyFrameGroupIndex(encode_frame_result.frame_type);
  if (key_frame_group_index_ == key_frame_group_size_) {
    UpdateKeyFrameGroup(show_frame_count_);
  }

  IncreaseGroupOfPictureIndex(&group_of_picture_);
  if (IsGroupOfPictureFinished(group_of_picture_)) {
    // This function needs to be called after ref_frame_info_ is updated
    // properly in PostUpdateRefFrameInfo() and UpdateKeyFrameGroup().
    UpdateGroupOfPicture(impl_ptr_->cpi, frame_coding_index_, ref_frame_info_,
                         &group_of_picture_);
  }
}

void SimpleEncode::EncodeFrame(EncodeFrameResult *encode_frame_result) {
  VP9_COMP *cpi = impl_ptr_->cpi;
  struct lookahead_ctx *lookahead = cpi->lookahead;
  int use_highbitdepth = 0;
#if CONFIG_VP9_HIGHBITDEPTH
  use_highbitdepth = cpi->common.use_highbitdepth;
#endif
  // The lookahead's size is set to oxcf->lag_in_frames.
  // We want to fill lookahead to it's max capacity if possible so that the
  // encoder can construct alt ref frame in time.
  // In the other words, we hope vp9_get_compressed_data to encode a frame
  // every time in the function
  while (!vp9_lookahead_full(lookahead)) {
    // TODO(angiebird): Check whether we can move this file read logics to
    // lookahead
    if (img_read(&impl_ptr_->tmp_img, in_file_)) {
      int next_show_idx = vp9_lookahead_next_show_idx(lookahead);
      int64_t ts_start =
          timebase_units_to_ticks(&cpi->oxcf.g_timebase_in_ts, next_show_idx);
      int64_t ts_end = timebase_units_to_ticks(&cpi->oxcf.g_timebase_in_ts,
                                               next_show_idx + 1);
      YV12_BUFFER_CONFIG sd;
      image2yuvconfig(&impl_ptr_->tmp_img, &sd);
      vp9_lookahead_push(lookahead, &sd, ts_start, ts_end, use_highbitdepth, 0);
    } else {
      break;
    }
  }

  if (init_encode_frame_result(encode_frame_result, frame_width_, frame_height_,
                               impl_ptr_->img_fmt)) {
    int64_t time_stamp;
    int64_t time_end;
    int flush = 1;  // Make vp9_get_compressed_data encode a frame
    unsigned int frame_flags = 0;
    ENCODE_FRAME_RESULT encode_frame_info;
    vp9_init_encode_frame_result(&encode_frame_info);
    ImageBuffer_to_IMAGE_BUFFER(encode_frame_result->coded_frame,
                                &encode_frame_info.coded_frame);
    vp9_get_compressed_data(cpi, &frame_flags,
                            &encode_frame_result->coding_data_byte_size,
                            encode_frame_result->coding_data.get(), &time_stamp,
                            &time_end, flush, &encode_frame_info);
    if (out_file_ != nullptr) {
      ivf_write_frame_header(out_file_, time_stamp,
                             encode_frame_result->coding_data_byte_size);
      fwrite(encode_frame_result->coding_data.get(), 1,
             encode_frame_result->coding_data_byte_size, out_file_);
    }

    // vp9_get_compressed_data is expected to encode a frame every time, so the
    // data size should be greater than zero.
    if (encode_frame_result->coding_data_byte_size <= 0) {
      fprintf(stderr, "Coding data size <= 0\n");
      abort();
    }
    const size_t max_coding_data_byte_size =
        get_max_coding_data_byte_size(frame_width_, frame_height_);
    if (encode_frame_result->coding_data_byte_size >
        max_coding_data_byte_size) {
      fprintf(stderr, "Coding data size exceeds the maximum.\n");
      abort();
    }

    update_encode_frame_result(encode_frame_result, &encode_frame_info);
    PostUpdateState(*encode_frame_result);
  } else {
    // TODO(angiebird): Clean up encode_frame_result.
    fprintf(stderr, "init_encode_frame_result() failed.\n");
    this->EndEncode();
  }
}

void SimpleEncode::EncodeFrameWithQuantizeIndex(
    EncodeFrameResult *encode_frame_result, int quantize_index) {
  encode_command_set_external_quantize_index(&impl_ptr_->cpi->encode_command,
                                             quantize_index);
  EncodeFrame(encode_frame_result);
  encode_command_reset_external_quantize_index(&impl_ptr_->cpi->encode_command);
}

int SimpleEncode::GetCodingFrameNum() const {
  assert(impl_ptr_->first_pass_stats.size() - 1 > 0);
  // These are the default settings for now.
  const int multi_layer_arf = 0;
  const int allow_alt_ref = 1;
  vpx_rational_t frame_rate =
      make_vpx_rational(frame_rate_num_, frame_rate_den_);
  const VP9EncoderConfig oxcf =
      vp9_get_encoder_config(frame_width_, frame_height_, frame_rate,
                             target_bitrate_, VPX_RC_LAST_PASS);
  FRAME_INFO frame_info = vp9_get_frame_info(&oxcf);
  FIRST_PASS_INFO first_pass_info;
  fps_init_first_pass_info(&first_pass_info,
                           GetVectorData(impl_ptr_->first_pass_stats),
                           num_frames_);
  return vp9_get_coding_frame_num(external_arf_indexes_.data(), &oxcf,
                                  &frame_info, &first_pass_info,
                                  multi_layer_arf, allow_alt_ref);
}

uint64_t SimpleEncode::GetFramePixelCount() const {
  assert(frame_width_ % 2 == 0);
  assert(frame_height_ % 2 == 0);
  switch (impl_ptr_->img_fmt) {
    case VPX_IMG_FMT_I420: return frame_width_ * frame_height_ * 3 / 2;
    case VPX_IMG_FMT_I422: return frame_width_ * frame_height_ * 2;
    case VPX_IMG_FMT_I444: return frame_width_ * frame_height_ * 3;
    case VPX_IMG_FMT_I440: return frame_width_ * frame_height_ * 2;
    case VPX_IMG_FMT_I42016: return frame_width_ * frame_height_ * 3 / 2;
    case VPX_IMG_FMT_I42216: return frame_width_ * frame_height_ * 2;
    case VPX_IMG_FMT_I44416: return frame_width_ * frame_height_ * 3;
    case VPX_IMG_FMT_I44016: return frame_width_ * frame_height_ * 2;
    default: return 0;
  }
}

SimpleEncode::~SimpleEncode() {
  if (in_file_ != nullptr) {
    fclose(in_file_);
  }
  if (out_file_ != nullptr) {
    fclose(out_file_);
  }
}

}  // namespace vp9
