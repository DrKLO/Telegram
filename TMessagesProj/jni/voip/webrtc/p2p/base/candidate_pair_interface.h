/*
 *  Copyright 2016 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef P2P_BASE_CANDIDATE_PAIR_INTERFACE_H_
#define P2P_BASE_CANDIDATE_PAIR_INTERFACE_H_

namespace cricket {

class Candidate;

class CandidatePairInterface {
 public:
  virtual ~CandidatePairInterface() {}

  virtual const Candidate& local_candidate() const = 0;
  virtual const Candidate& remote_candidate() const = 0;
};

// Specific implementation of the interface, suitable for being a
// data member of other structs.
struct CandidatePair final : public CandidatePairInterface {
  ~CandidatePair() override = default;

  const Candidate& local_candidate() const override { return local; }
  const Candidate& remote_candidate() const override { return remote; }

  Candidate local;
  Candidate remote;
};

}  // namespace cricket

#endif  // P2P_BASE_CANDIDATE_PAIR_INTERFACE_H_
