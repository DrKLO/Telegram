/*
 *  Copyright 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/jsep_ice_candidate.h"

#include <memory>
#include <utility>

#include "absl/algorithm/container.h"
#include "absl/memory/memory.h"

namespace webrtc {

std::string JsepIceCandidate::sdp_mid() const {
  return sdp_mid_;
}

int JsepIceCandidate::sdp_mline_index() const {
  return sdp_mline_index_;
}

const cricket::Candidate& JsepIceCandidate::candidate() const {
  return candidate_;
}

std::string JsepIceCandidate::server_url() const {
  return candidate_.url();
}

JsepCandidateCollection::JsepCandidateCollection() = default;

JsepCandidateCollection::JsepCandidateCollection(JsepCandidateCollection&& o)
    : candidates_(std::move(o.candidates_)) {}

size_t JsepCandidateCollection::count() const {
  return candidates_.size();
}

void JsepCandidateCollection::add(JsepIceCandidate* candidate) {
  candidates_.push_back(absl::WrapUnique(candidate));
}

const IceCandidateInterface* JsepCandidateCollection::at(size_t index) const {
  return candidates_[index].get();
}

bool JsepCandidateCollection::HasCandidate(
    const IceCandidateInterface* candidate) const {
  return absl::c_any_of(
      candidates_, [&](const std::unique_ptr<JsepIceCandidate>& entry) {
        return entry->sdp_mid() == candidate->sdp_mid() &&
               entry->sdp_mline_index() == candidate->sdp_mline_index() &&
               entry->candidate().IsEquivalent(candidate->candidate());
      });
}

size_t JsepCandidateCollection::remove(const cricket::Candidate& candidate) {
  auto iter = absl::c_find_if(
      candidates_, [&](const std::unique_ptr<JsepIceCandidate>& c) {
        return candidate.MatchesForRemoval(c->candidate());
      });
  if (iter != candidates_.end()) {
    candidates_.erase(iter);
    return 1;
  }
  return 0;
}

}  // namespace webrtc
