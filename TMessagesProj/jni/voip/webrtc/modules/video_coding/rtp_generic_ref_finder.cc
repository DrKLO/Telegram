/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_coding/rtp_generic_ref_finder.h"

#include <utility>

#include "rtc_base/logging.h"

namespace webrtc {

RtpFrameReferenceFinder::ReturnVector RtpGenericFrameRefFinder::ManageFrame(
    std::unique_ptr<RtpFrameObject> frame,
    const RTPVideoHeader::GenericDescriptorInfo& descriptor) {
  // Frame IDs are unwrapped in the RtpVideoStreamReceiver, no need to unwrap
  // them here.
  frame->SetId(descriptor.frame_id);
  frame->SetSpatialIndex(descriptor.spatial_index);

  RtpFrameReferenceFinder::ReturnVector res;
  if (EncodedFrame::kMaxFrameReferences < descriptor.dependencies.size()) {
    RTC_LOG(LS_WARNING) << "Too many dependencies in generic descriptor.";
    return res;
  }

  frame->num_references = descriptor.dependencies.size();
  for (size_t i = 0; i < descriptor.dependencies.size(); ++i) {
    frame->references[i] = descriptor.dependencies[i];
  }

  res.push_back(std::move(frame));
  return res;
}

}  // namespace webrtc
