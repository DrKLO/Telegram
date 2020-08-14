/*
 *  Copyright (c) 2019 The WebM project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef VPX_VP9_SIMPLE_ENCODE_H_
#define VPX_VP9_SIMPLE_ENCODE_H_

#include <cstddef>
#include <cstdint>
#include <cstdio>
#include <memory>
#include <vector>

namespace vp9 {

// TODO(angiebird): Add description for each frame type.
enum FrameType {
  kFrameTypeKey = 0,
  kFrameTypeInter,
  kFrameTypeAltRef,
  kFrameTypeOverlay,
  kFrameTypeGolden,
};

// TODO(angiebird): Add description for each reference frame type.
// This enum numbers have to be contiguous and start from zero except
// kNoneRefFrame.
enum RefFrameType {
  kRefFrameTypeLast = 0,
  kRefFrameTypePast = 1,
  kRefFrameTypeFuture = 2,
  kRefFrameTypeMax = 3,
  kRefFrameTypeNone = -1,
};

// The frame is split to 4x4 blocks.
// This structure contains the information of each 4x4 block.
struct PartitionInfo {
  int row;           // row pixel offset of current 4x4 block
  int column;        // column pixel offset of current 4x4 block
  int row_start;     // row pixel offset of the start of the prediction block
  int column_start;  // column pixel offset of the start of the prediction block
  int width;         // prediction block width
  int height;        // prediction block height
};

constexpr int kMotionVectorPrecision = 8;

// The frame is split to 4x4 blocks.
// This structure contains the information of each 4x4 block.
struct MotionVectorInfo {
  // Number of valid motion vectors, always 0 if this block is in the key frame.
  // For inter frames, it could be 1 or 2.
  int mv_count;
  // The reference frame for motion vectors. If the second motion vector does
  // not exist (mv_count = 1), the reference frame is kNoneRefFrame.
  // Otherwise, the reference frame is either kLastFrame, or kGoldenFrame,
  // or kAltRefFrame.
  RefFrameType ref_frame[2];
  // The row offset of motion vectors in the unit of pixel.
  // If the second motion vector does not exist, the value is 0.
  double mv_row[2];
  // The column offset of motion vectors in the unit of pixel.
  // If the second motion vector does not exist, the value is 0.
  double mv_column[2];
};

struct RefFrameInfo {
  int coding_indexes[kRefFrameTypeMax];

  // Indicate whether the reference frames are available or not.
  // When the reference frame type is not valid, it means either the to-be-coded
  // frame is a key frame or the reference frame already appears in other
  // reference frame type. vp9 always keeps three types of reference frame
  // available.  However, the duplicated reference frames will not be
  // chosen by the encoder. The priorities of choosing reference frames are
  // kRefFrameTypeLast > kRefFrameTypePast > kRefFrameTypeFuture.
  // For example, if kRefFrameTypeLast and kRefFrameTypePast both point to the
  // same frame, kRefFrameTypePast will be set to invalid.
  // 1: the ref frame type is available 0: the ref frame type is not available
  int valid_list[kRefFrameTypeMax];
};

bool operator==(const RefFrameInfo &a, const RefFrameInfo &b);

struct EncodeFrameInfo {
  int show_idx;

  // Each show or no show frame is assigned with a coding index based on its
  // coding order (starting from zero) in the coding process of the entire
  // video. The coding index for each frame is unique.
  int coding_index;
  RefFrameInfo ref_frame_info;
  FrameType frame_type;
};

// This structure is a copy of vp9 |nmv_component_counts|.
struct NewMotionvectorComponentCounts {
  std::vector<unsigned int> sign;
  std::vector<unsigned int> classes;
  std::vector<unsigned int> class0;
  std::vector<std::vector<unsigned int>> bits;
  std::vector<std::vector<unsigned int>> class0_fp;
  std::vector<unsigned int> fp;
  std::vector<unsigned int> class0_hp;
  std::vector<unsigned int> hp;
};

// This structure is a copy of vp9 |nmv_context_counts|.
struct NewMotionVectorContextCounts {
  std::vector<unsigned int> joints;
  std::vector<NewMotionvectorComponentCounts> comps;
};

using UintArray2D = std::vector<std::vector<unsigned int>>;
using UintArray3D = std::vector<std::vector<std::vector<unsigned int>>>;
using UintArray5D = std::vector<
    std::vector<std::vector<std::vector<std::vector<unsigned int>>>>>;
using UintArray6D = std::vector<std::vector<
    std::vector<std::vector<std::vector<std::vector<unsigned int>>>>>>;

// This structure is a copy of vp9 |tx_counts|.
struct TransformSizeCounts {
  // Transform size found in blocks of partition size 32x32.
  // First dimension: transform size contexts (2).
  // Second dimension: transform size type (3: 32x32, 16x16, 8x8)
  UintArray2D p32x32;
  // Transform size found in blocks of partition size 16x16.
  // First dimension: transform size contexts (2).
  // Second dimension: transform size type (2: 16x16, 8x8)
  UintArray2D p16x16;
  // Transform size found in blocks of partition size 8x8.
  // First dimension: transform size contexts (2).
  // Second dimension: transform size type (1: 8x8)
  UintArray2D p8x8;
  // Overall transform size count.
  std::vector<unsigned int> tx_totals;
};

// This structure is a copy of vp9 |FRAME_COUNTS|.
struct FrameCounts {
  // Intra prediction mode for luma plane. First dimension: block size (4).
  // Second dimension: intra prediction mode (10).
  UintArray2D y_mode;
  // Intra prediction mode for chroma plane. First and second dimension:
  // intra prediction mode (10).
  UintArray2D uv_mode;
  // Partition type. First dimension: partition contexts (16).
  // Second dimension: partition type (4).
  UintArray2D partition;
  // Transform coefficient.
  UintArray6D coef;
  // End of block (the position of the last non-zero transform coefficient)
  UintArray5D eob_branch;
  // Interpolation filter type. First dimension: switchable filter contexts (4).
  // Second dimension: filter types (3).
  UintArray2D switchable_interp;
  // Inter prediction mode (the motion vector type).
  // First dimension: inter mode contexts (7).
  // Second dimension: mode type (4).
  UintArray2D inter_mode;
  // Block is intra or inter predicted. First dimension: contexts (4).
  // Second dimension: type (0 for intra, 1 for inter).
  UintArray2D intra_inter;
  // Block is compound predicted (predicted from average of two blocks).
  // First dimension: contexts (5).
  // Second dimension: type (0 for single, 1 for compound prediction).
  UintArray2D comp_inter;
  // Type of the reference frame. Only one reference frame.
  // First dimension: context (5). Second dimension: context (2).
  // Third dimension: count (2).
  UintArray3D single_ref;
  // Type of the two reference frames.
  // First dimension: context (5). Second dimension: count (2).
  UintArray2D comp_ref;
  // Block skips transform and quantization, uses prediction as reconstruction.
  // First dimension: contexts (3). Second dimension: type (0 not skip, 1 skip).
  UintArray2D skip;
  // Transform size.
  TransformSizeCounts tx;
  // New motion vector.
  NewMotionVectorContextCounts mv;
};

struct ImageBuffer {
  // The image data is stored in raster order,
  // i.e. image[plane][r][c] =
  // plane_buffer[plane][r * plane_width[plane] + plane_height[plane]].
  std::unique_ptr<unsigned char[]> plane_buffer[3];
  int plane_width[3];
  int plane_height[3];
};

void output_image_buffer(const ImageBuffer &image_buffer, std::FILE *out_file);

struct EncodeFrameResult {
  int show_idx;
  FrameType frame_type;
  int coding_idx;
  RefFrameInfo ref_frame_info;
  size_t coding_data_bit_size;
  size_t coding_data_byte_size;
  // The EncodeFrame will allocate a buffer, write the coding data into the
  // buffer and give the ownership of the buffer to coding_data.
  std::unique_ptr<unsigned char[]> coding_data;
  double psnr;
  uint64_t sse;
  int quantize_index;
  FrameCounts frame_counts;
  int num_rows_4x4;  // number of row units, in size of 4.
  int num_cols_4x4;  // number of column units, in size of 4.
  // A vector of the partition information of the frame.
  // The number of elements is |num_rows_4x4| * |num_cols_4x4|.
  // The frame is divided 4x4 blocks of |num_rows_4x4| rows and
  // |num_cols_4x4| columns.
  // Each 4x4 block contains the current pixel position (|row|, |column|),
  // the start pixel position of the partition (|row_start|, |column_start|),
  // and the |width|, |height| of the partition.
  // The current pixel position can be the same as the start pixel position
  // if the 4x4 block is the top-left block in the partition. Otherwise, they
  // are different.
  // Within the same partition, all 4x4 blocks have the same |row_start|,
  // |column_start|, |width| and |height|.
  // For example, if the frame is partitioned to a 32x32 block,
  // starting at (0, 0). Then, there're 64 4x4 blocks within this partition.
  // They all have the same |row_start|, |column_start|, |width|, |height|,
  // which can be used to figure out the start of the current partition and
  // the start of the next partition block.
  // Horizontal next: |column_start| + |width|,
  // Vertical next: |row_start| + |height|.
  std::vector<PartitionInfo> partition_info;
  // A vector of the motion vector information of the frame.
  // The number of elements is |num_rows_4x4| * |num_cols_4x4|.
  // The frame is divided 4x4 blocks of |num_rows_4x4| rows and
  // |num_cols_4x4| columns.
  // Each 4x4 block contains 0 motion vector if this is an intra predicted
  // frame (for example, the key frame). If the frame is inter predicted,
  // each 4x4 block contains either 1 or 2 motion vectors.
  // Similar to partition info, all 4x4 blocks inside the same partition block
  // share the same motion vector information.
  std::vector<MotionVectorInfo> motion_vector_info;
  ImageBuffer coded_frame;
};

struct GroupOfPicture {
  // This list will be updated internally in StartEncode() and
  // EncodeFrame()/EncodeFrameWithQuantizeIndex().
  // In EncodeFrame()/EncodeFrameWithQuantizeIndex(), the update will only be
  // triggered when the coded frame is the last one in the previous group of
  // pictures.
  std::vector<EncodeFrameInfo> encode_frame_list;
  // Indicates the index of the next coding frame in encode_frame_list.
  // In other words, EncodeFrameInfo of the next coding frame can be
  // obtained with encode_frame_list[next_encode_frame_index].
  // Internally, next_encode_frame_index will be set to zero after the last
  // frame of the group of pictures is coded. Otherwise, next_encode_frame_index
  // will be increased after each EncodeFrame()/EncodeFrameWithQuantizeIndex()
  // call.
  int next_encode_frame_index;
  // Number of show frames in this group of pictures.
  int show_frame_count;
  // The show index/timestamp of the earliest show frame in the group of
  // pictures.
  int start_show_index;
  // The coding index of the first coding frame in the group of picture.
  int start_coding_index;
};

class SimpleEncode {
 public:
  // When outfile_path is set, the encoder will output the bitstream in ivf
  // format.
  SimpleEncode(int frame_width, int frame_height, int frame_rate_num,
               int frame_rate_den, int target_bitrate, int num_frames,
               const char *infile_path, const char *outfile_path = nullptr);
  ~SimpleEncode();
  SimpleEncode(SimpleEncode &) = delete;
  SimpleEncode &operator=(const SimpleEncode &) = delete;

  // Makes encoder compute the first pass stats and store it internally for
  // future encode.
  void ComputeFirstPassStats();

  // Outputs the first pass stats represented by a 2-D vector.
  // One can use the frame index at first dimension to retrieve the stats for
  // each video frame. The stats of each video frame is a vector of 25 double
  // values. For details, please check FIRSTPASS_STATS in vp9_firstpass.h
  std::vector<std::vector<double>> ObserveFirstPassStats();

  // Sets arf indexes for the video from external input.
  // The arf index determines whether a frame is arf or not.
  // Therefore it also determines the group of picture size.
  // If set, VP9 will use the external arf index to make decision.
  // This function should be called only once after ComputeFirstPassStats(),
  // before StartEncode().
  void SetExternalGroupOfPicture(std::vector<int> external_arf_indexes);

  // Initializes the encoder for actual encoding.
  // This function should be called after ComputeFirstPassStats().
  void StartEncode();

  // Frees the encoder.
  // This function should be called after StartEncode() or EncodeFrame().
  void EndEncode();

  // The key frame group size includes one key frame plus the number of
  // following inter frames. Note that the key frame group size only counts the
  // show frames. The number of no show frames like alternate refereces are not
  // counted.
  int GetKeyFrameGroupSize() const;

  // Provides the group of pictures that the next coding frame is in.
  // Only call this function between StartEncode() and EndEncode()
  GroupOfPicture ObserveGroupOfPicture() const;

  // Gets encode_frame_info for the next coding frame.
  // Only call this function between StartEncode() and EndEncode()
  EncodeFrameInfo GetNextEncodeFrameInfo() const;

  // Encodes a frame
  // This function should be called after StartEncode() and before EndEncode().
  void EncodeFrame(EncodeFrameResult *encode_frame_result);

  // Encodes a frame with a specific quantize index.
  // This function should be called after StartEncode() and before EndEncode().
  void EncodeFrameWithQuantizeIndex(EncodeFrameResult *encode_frame_result,
                                    int quantize_index);

  // Gets the number of coding frames for the video. The coding frames include
  // show frame and no show frame.
  // This function should be called after ComputeFirstPassStats().
  int GetCodingFrameNum() const;

  // Gets the total number of pixels of YUV planes per frame.
  uint64_t GetFramePixelCount() const;

 private:
  // Updates key_frame_group_size_, reset key_frame_group_index_ and init
  // ref_frame_info_.
  void UpdateKeyFrameGroup(int key_frame_show_index);

  // Update key_frame_group_index_.
  void PostUpdateKeyFrameGroupIndex(FrameType frame_type);

  void PostUpdateState(const EncodeFrameResult &encode_frame_result);

  class EncodeImpl;

  int frame_width_;   // frame width in pixels.
  int frame_height_;  // frame height in pixels.
  int frame_rate_num_;
  int frame_rate_den_;
  int target_bitrate_;
  int num_frames_;

  std::FILE *in_file_;
  std::FILE *out_file_;
  std::unique_ptr<EncodeImpl> impl_ptr_;

  std::vector<int> external_arf_indexes_;
  GroupOfPicture group_of_picture_;

  // The key frame group size includes one key frame plus the number of
  // following inter frames. Note that the key frame group size only counts the
  // show frames. The number of no show frames like alternate references are not
  // counted.
  int key_frame_group_size_;

  // The index for the to-be-coded show frame in the key frame group.
  int key_frame_group_index_;

  // Each show or no show frame is assigned with a coding index based on its
  // coding order (starting from zero) in the coding process of the entire
  // video. The coding index of the to-be-coded frame.
  int frame_coding_index_;

  // Number of show frames we have coded so far.
  int show_frame_count_;

  // TODO(angiebird): Do we need to reset ref_frames_info_ when the next key
  // frame appears?
  // Reference frames info of the to-be-coded frame.
  RefFrameInfo ref_frame_info_;
};

}  // namespace vp9

#endif  // VPX_VP9_SIMPLE_ENCODE_H_
