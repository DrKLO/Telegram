/*
 *  Copyright 2018 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "pc/rtc_stats_traversal.h"

#include <memory>
#include <vector>

#include "api/stats/rtcstats_objects.h"
#include "test/gtest.h"

// This file contains tests for TakeReferencedStats().
// GetStatsNeighborIds() is tested in rtcstats_integrationtest.cc.

namespace webrtc {

class RTCStatsTraversalTest : public ::testing::Test {
 public:
  RTCStatsTraversalTest() {
    transport_ = new RTCTransportStats("transport", Timestamp::Zero());
    candidate_pair_ =
        new RTCIceCandidatePairStats("candidate-pair", Timestamp::Zero());
    local_candidate_ =
        new RTCLocalIceCandidateStats("local-candidate", Timestamp::Zero());
    remote_candidate_ =
        new RTCRemoteIceCandidateStats("remote-candidate", Timestamp::Zero());
    initial_report_ = RTCStatsReport::Create(Timestamp::Zero());
    initial_report_->AddStats(std::unique_ptr<const RTCStats>(transport_));
    initial_report_->AddStats(std::unique_ptr<const RTCStats>(candidate_pair_));
    initial_report_->AddStats(
        std::unique_ptr<const RTCStats>(local_candidate_));
    initial_report_->AddStats(
        std::unique_ptr<const RTCStats>(remote_candidate_));
    result_ = RTCStatsReport::Create(Timestamp::Zero());
  }

  void TakeReferencedStats(std::vector<const RTCStats*> start_nodes) {
    std::vector<std::string> start_ids;
    start_ids.reserve(start_nodes.size());
    for (const RTCStats* start_node : start_nodes) {
      start_ids.push_back(start_node->id());
    }
    result_ = ::webrtc::TakeReferencedStats(initial_report_, start_ids);
  }

  void EXPECT_VISITED(const RTCStats* stats) {
    EXPECT_FALSE(initial_report_->Get(stats->id()))
        << '"' << stats->id()
        << "\" should be visited but it was not removed from initial report.";
    EXPECT_TRUE(result_->Get(stats->id()))
        << '"' << stats->id()
        << "\" should be visited but it was not added to the resulting report.";
  }

  void EXPECT_UNVISITED(const RTCStats* stats) {
    EXPECT_TRUE(initial_report_->Get(stats->id()))
        << '"' << stats->id()
        << "\" should not be visited but it was removed from initial report.";
    EXPECT_FALSE(result_->Get(stats->id()))
        << '"' << stats->id()
        << "\" should not be visited but it was added to the resulting report.";
  }

 protected:
  rtc::scoped_refptr<RTCStatsReport> initial_report_;
  rtc::scoped_refptr<RTCStatsReport> result_;
  // Raw pointers to stats owned by the reports.
  RTCTransportStats* transport_;
  RTCIceCandidatePairStats* candidate_pair_;
  RTCIceCandidateStats* local_candidate_;
  RTCIceCandidateStats* remote_candidate_;
};

TEST_F(RTCStatsTraversalTest, NoReachableConnections) {
  // Everything references transport but transport doesn't reference anything.
  //
  //          candidate-pair
  //            |    |  |
  //            v    |  v
  // local-candidate | remote-candidate
  //              |  |  |
  //              v  v  v
  //          start:transport
  candidate_pair_->transport_id = "transport";
  candidate_pair_->local_candidate_id = "local-candidate";
  candidate_pair_->remote_candidate_id = "remote-candidate";
  local_candidate_->transport_id = "transport";
  remote_candidate_->transport_id = "transport";
  TakeReferencedStats({transport_});
  EXPECT_VISITED(transport_);
  EXPECT_UNVISITED(candidate_pair_);
  EXPECT_UNVISITED(local_candidate_);
  EXPECT_UNVISITED(remote_candidate_);
}

TEST_F(RTCStatsTraversalTest, SelfReference) {
  transport_->rtcp_transport_stats_id = "transport";
  TakeReferencedStats({transport_});
  EXPECT_VISITED(transport_);
  EXPECT_UNVISITED(candidate_pair_);
  EXPECT_UNVISITED(local_candidate_);
  EXPECT_UNVISITED(remote_candidate_);
}

TEST_F(RTCStatsTraversalTest, BogusReference) {
  transport_->rtcp_transport_stats_id = "bogus-reference";
  TakeReferencedStats({transport_});
  EXPECT_VISITED(transport_);
  EXPECT_UNVISITED(candidate_pair_);
  EXPECT_UNVISITED(local_candidate_);
  EXPECT_UNVISITED(remote_candidate_);
}

TEST_F(RTCStatsTraversalTest, Tree) {
  //     start:candidate-pair
  //        |            |
  //        v            v
  // local-candidate   remote-candidate
  //       |
  //       v
  //   transport
  candidate_pair_->local_candidate_id = "local-candidate";
  candidate_pair_->remote_candidate_id = "remote-candidate";
  local_candidate_->transport_id = "transport";
  TakeReferencedStats({candidate_pair_});
  EXPECT_VISITED(transport_);
  EXPECT_VISITED(candidate_pair_);
  EXPECT_VISITED(local_candidate_);
  EXPECT_VISITED(remote_candidate_);
}

TEST_F(RTCStatsTraversalTest, MultiplePathsToSameNode) {
  //     start:candidate-pair
  //        |            |
  //        v            v
  // local-candidate   remote-candidate
  //              |     |
  //              v     v
  //             transport
  candidate_pair_->local_candidate_id = "local-candidate";
  candidate_pair_->remote_candidate_id = "remote-candidate";
  local_candidate_->transport_id = "transport";
  remote_candidate_->transport_id = "transport";
  TakeReferencedStats({candidate_pair_});
  EXPECT_VISITED(transport_);
  EXPECT_VISITED(candidate_pair_);
  EXPECT_VISITED(local_candidate_);
  EXPECT_VISITED(remote_candidate_);
}

TEST_F(RTCStatsTraversalTest, CyclicGraph) {
  //               candidate-pair
  //                  |     ^
  //                  v     |
  // start:local-candidate  |    remote-candidate
  //                    |   |
  //                    v   |
  //                  transport
  local_candidate_->transport_id = "transport";
  transport_->selected_candidate_pair_id = "candidate-pair";
  candidate_pair_->local_candidate_id = "local-candidate";
  TakeReferencedStats({local_candidate_});
  EXPECT_VISITED(transport_);
  EXPECT_VISITED(candidate_pair_);
  EXPECT_VISITED(local_candidate_);
  EXPECT_UNVISITED(remote_candidate_);
}

TEST_F(RTCStatsTraversalTest, MultipleStarts) {
  //           start:candidate-pair
  //                        |
  //                        v
  // local-candidate    remote-candidate
  //             |
  //             v
  //           start:transport
  candidate_pair_->remote_candidate_id = "remote-candidate";
  local_candidate_->transport_id = "transport";
  TakeReferencedStats({candidate_pair_, transport_});
  EXPECT_VISITED(transport_);
  EXPECT_VISITED(candidate_pair_);
  EXPECT_UNVISITED(local_candidate_);
  EXPECT_VISITED(remote_candidate_);
}

TEST_F(RTCStatsTraversalTest, MultipleStartsLeadingToSameNode) {
  //                candidate-pair
  //
  //
  // start:local-candidate   start:remote-candidate
  //                    |     |
  //                    v     v
  //                   transport
  local_candidate_->transport_id = "transport";
  remote_candidate_->transport_id = "transport";
  TakeReferencedStats({local_candidate_, remote_candidate_});
  EXPECT_VISITED(transport_);
  EXPECT_UNVISITED(candidate_pair_);
  EXPECT_VISITED(local_candidate_);
  EXPECT_VISITED(remote_candidate_);
}

}  // namespace webrtc
