/*
 *  Copyright 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/jsep_ice_candidate.h"

#include <memory>

#include "pc/webrtc_sdp.h"

// This file contains JsepIceCandidate-related functions that are not
// included in api/jsep_ice_candidate.cc. Some of these link to SDP
// parsing/serializing functions, which some users may not want.
// TODO(bugs.webrtc.org/12330): Merge the two .cc files somehow.

namespace webrtc {

IceCandidateInterface* CreateIceCandidate(const std::string& sdp_mid,
                                          int sdp_mline_index,
                                          const std::string& sdp,
                                          SdpParseError* error) {
  JsepIceCandidate* jsep_ice = new JsepIceCandidate(sdp_mid, sdp_mline_index);
  if (!jsep_ice->Initialize(sdp, error)) {
    delete jsep_ice;
    return NULL;
  }
  return jsep_ice;
}

std::unique_ptr<IceCandidateInterface> CreateIceCandidate(
    const std::string& sdp_mid,
    int sdp_mline_index,
    const cricket::Candidate& candidate) {
  return std::make_unique<JsepIceCandidate>(sdp_mid, sdp_mline_index,
                                            candidate);
}

JsepIceCandidate::JsepIceCandidate(const std::string& sdp_mid,
                                   int sdp_mline_index)
    : sdp_mid_(sdp_mid), sdp_mline_index_(sdp_mline_index) {}

JsepIceCandidate::JsepIceCandidate(const std::string& sdp_mid,
                                   int sdp_mline_index,
                                   const cricket::Candidate& candidate)
    : sdp_mid_(sdp_mid),
      sdp_mline_index_(sdp_mline_index),
      candidate_(candidate) {}

JsepIceCandidate::~JsepIceCandidate() {}

JsepCandidateCollection JsepCandidateCollection::Clone() const {
  JsepCandidateCollection new_collection;
  for (const auto& candidate : candidates_) {
    new_collection.candidates_.push_back(std::make_unique<JsepIceCandidate>(
        candidate->sdp_mid(), candidate->sdp_mline_index(),
        candidate->candidate()));
  }
  return new_collection;
}

bool JsepIceCandidate::Initialize(const std::string& sdp, SdpParseError* err) {
  return SdpDeserializeCandidate(sdp, this, err);
}

bool JsepIceCandidate::ToString(std::string* out) const {
  if (!out)
    return false;
  *out = SdpSerializeCandidate(*this);
  return !out->empty();
}

}  // namespace webrtc
