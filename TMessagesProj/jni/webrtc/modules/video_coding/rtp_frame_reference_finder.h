/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_VIDEO_CODING_RTP_FRAME_REFERENCE_FINDER_H_
#define MODULES_VIDEO_CODING_RTP_FRAME_REFERENCE_FINDER_H_

#include <array>
#include <deque>
#include <map>
#include <memory>
#include <set>
#include <utility>

#include "modules/include/module_common_types_public.h"
#include "modules/rtp_rtcp/source/rtp_video_header.h"
#include "modules/video_coding/codecs/vp9/include/vp9_globals.h"
#include "rtc_base/numerics/sequence_number_util.h"
#include "rtc_base/thread_annotations.h"

namespace webrtc {
namespace video_coding {

class EncodedFrame;
class RtpFrameObject;

// A complete frame is a frame which has received all its packets and all its
// references are known.
class OnCompleteFrameCallback {
 public:
  virtual ~OnCompleteFrameCallback() {}
  virtual void OnCompleteFrame(std::unique_ptr<EncodedFrame> frame) = 0;
};

class RtpFrameReferenceFinder {
 public:
  explicit RtpFrameReferenceFinder(OnCompleteFrameCallback* frame_callback);
  explicit RtpFrameReferenceFinder(OnCompleteFrameCallback* frame_callback,
                                   int64_t picture_id_offset);
  ~RtpFrameReferenceFinder();

  // Manage this frame until:
  //  - We have all information needed to determine its references, after
  //    which |frame_callback_| is called with the completed frame, or
  //  - We have too many stashed frames (determined by |kMaxStashedFrames|)
  //    so we drop this frame, or
  //  - It gets cleared by ClearTo, which also means we drop it.
  void ManageFrame(std::unique_ptr<RtpFrameObject> frame);

  // Notifies that padding has been received, which the reference finder
  // might need to calculate the references of a frame.
  void PaddingReceived(uint16_t seq_num);

  // Clear all stashed frames that include packets older than |seq_num|.
  void ClearTo(uint16_t seq_num);

 private:
  static const uint16_t kPicIdLength = 1 << 15;
  static const uint8_t kMaxTemporalLayers = 5;
  static const int kMaxLayerInfo = 50;
  static const int kMaxStashedFrames = 100;
  static const int kMaxNotYetReceivedFrames = 100;
  static const int kMaxGofSaved = 50;
  static const int kMaxPaddingAge = 100;

  enum FrameDecision { kStash, kHandOff, kDrop };

  struct GofInfo {
    GofInfo(GofInfoVP9* gof, uint16_t last_picture_id)
        : gof(gof), last_picture_id(last_picture_id) {}
    GofInfoVP9* gof;
    uint16_t last_picture_id;
  };

  // Find the relevant group of pictures and update its "last-picture-id-with
  // padding" sequence number.
  void UpdateLastPictureIdWithPadding(uint16_t seq_num);

  // Retry stashed frames until no more complete frames are found.
  void RetryStashedFrames();

  void HandOffFrame(std::unique_ptr<RtpFrameObject> frame);

  FrameDecision ManageFrameInternal(RtpFrameObject* frame);

  FrameDecision ManageFrameGeneric(
      RtpFrameObject* frame,
      const RTPVideoHeader::GenericDescriptorInfo& descriptor);

  // Find references for frames with no or very limited information in the
  // descriptor. If |picture_id| is unspecified then packet sequence numbers
  // will be used to determine the references of the frames.
  FrameDecision ManageFramePidOrSeqNum(RtpFrameObject* frame, int picture_id);

  // Find references for Vp8 frames
  FrameDecision ManageFrameVp8(RtpFrameObject* frame);

  // Updates necessary layer info state used to determine frame references for
  // Vp8.
  void UpdateLayerInfoVp8(RtpFrameObject* frame,
                          int64_t unwrapped_tl0,
                          uint8_t temporal_idx);

  // Find references for Vp9 frames
  FrameDecision ManageFrameVp9(RtpFrameObject* frame);

  // Check if we are missing a frame necessary to determine the references
  // for this frame.
  bool MissingRequiredFrameVp9(uint16_t picture_id, const GofInfo& info);

  // Updates which frames that have been received. If there is a gap,
  // missing frames will be added to |missing_frames_for_layer_| or
  // if this is an already missing frame then it will be removed.
  void FrameReceivedVp9(uint16_t picture_id, GofInfo* info);

  // Check if there is a frame with the up-switch flag set in the interval
  // (|pid_ref|, |picture_id|) with temporal layer smaller than |temporal_idx|.
  bool UpSwitchInIntervalVp9(uint16_t picture_id,
                             uint8_t temporal_idx,
                             uint16_t pid_ref);

  // Unwrap |frame|s picture id and its references to 16 bits.
  void UnwrapPictureIds(RtpFrameObject* frame);

  // Find references for H264 frames
  FrameDecision ManageFrameH264(RtpFrameObject* frame);

  // Update "last-picture-id-with-padding" sequence number for H264.
  void UpdateLastPictureIdWithPaddingH264();

  // Update H264 layer info state used to determine frame references.
  void UpdateLayerInfoH264(RtpFrameObject* frame,
                           int64_t unwrapped_tl0,
                           uint8_t temporal_idx);

  // Update H264 state for decodeable frames.
  void UpdateDataH264(RtpFrameObject* frame,
                      int64_t unwrapped_tl0,
                      uint8_t temporal_idx);

  // For every group of pictures, hold two sequence numbers. The first being
  // the sequence number of the last packet of the last completed frame, and
  // the second being the sequence number of the last packet of the last
  // completed frame advanced by any potential continuous packets of padding.
  std::map<uint16_t,
           std::pair<uint16_t, uint16_t>,
           DescendingSeqNumComp<uint16_t>>
      last_seq_num_gop_;

  // Save the last picture id in order to detect when there is a gap in frames
  // that have not yet been fully received.
  int last_picture_id_;

  // Padding packets that have been received but that are not yet continuous
  // with any group of pictures.
  std::set<uint16_t, DescendingSeqNumComp<uint16_t>> stashed_padding_;

  // Frames earlier than the last received frame that have not yet been
  // fully received.
  std::set<uint16_t, DescendingSeqNumComp<uint16_t, kPicIdLength>>
      not_yet_received_frames_;

  // Sequence numbers of frames earlier than the last received frame that
  // have not yet been fully received.
  std::set<uint16_t, DescendingSeqNumComp<uint16_t>> not_yet_received_seq_num_;

  // Frames that have been fully received but didn't have all the information
  // needed to determine their references.
  std::deque<std::unique_ptr<RtpFrameObject>> stashed_frames_;

  // Holds the information about the last completed frame for a given temporal
  // layer given an unwrapped Tl0 picture index.
  std::map<int64_t, std::array<int64_t, kMaxTemporalLayers>> layer_info_;

  // Where the current scalability structure is in the
  // |scalability_structures_| array.
  uint8_t current_ss_idx_;

  // Holds received scalability structures.
  std::array<GofInfoVP9, kMaxGofSaved> scalability_structures_;

  // Holds the the Gof information for a given unwrapped TL0 picture index.
  std::map<int64_t, GofInfo> gof_info_;

  // Keep track of which picture id and which temporal layer that had the
  // up switch flag set.
  std::map<uint16_t, uint8_t, DescendingSeqNumComp<uint16_t, kPicIdLength>>
      up_switch_;

  // For every temporal layer, keep a set of which frames that are missing.
  std::array<std::set<uint16_t, DescendingSeqNumComp<uint16_t, kPicIdLength>>,
             kMaxTemporalLayers>
      missing_frames_for_layer_;

  // How far frames have been cleared by sequence number. A frame will be
  // cleared if it contains a packet with a sequence number older than
  // |cleared_to_seq_num_|.
  int cleared_to_seq_num_;

  OnCompleteFrameCallback* frame_callback_;

  // Unwrapper used to unwrap generic RTP streams. In a generic stream we derive
  // a picture id from the packet sequence number.
  SeqNumUnwrapper<uint16_t> rtp_seq_num_unwrapper_;

  // Unwrapper used to unwrap VP8/VP9 streams which have their picture id
  // specified.
  SeqNumUnwrapper<uint16_t, kPicIdLength> unwrapper_;

  SeqNumUnwrapper<uint8_t> tl0_unwrapper_;

  const int64_t picture_id_offset_;
};

}  // namespace video_coding
}  // namespace webrtc

#endif  // MODULES_VIDEO_CODING_RTP_FRAME_REFERENCE_FINDER_H_
