// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/util/memory_pressure/memory_pressure_voter.h"

#include <numeric>

#include "base/stl_util.h"
#include "base/trace_event/trace_event.h"

namespace util {

class MemoryPressureVoterImpl : public MemoryPressureVoter {
 public:
  explicit MemoryPressureVoterImpl(MemoryPressureVoteAggregator* aggregator)
      : aggregator_(aggregator) {}
  ~MemoryPressureVoterImpl() override {
    // Remove this voter's vote.
    if (vote_)
      aggregator_->OnVote(vote_, base::nullopt);
  }

  MemoryPressureVoterImpl(MemoryPressureVoterImpl&&) = delete;
  MemoryPressureVoterImpl& operator=(MemoryPressureVoterImpl&&) = delete;

  void SetVote(base::MemoryPressureListener::MemoryPressureLevel level,
               bool notify_listeners) override {
    DCHECK_CALLED_ON_VALID_SEQUENCE(sequence_checker_);
    auto old_vote = vote_;
    vote_ = level;
    aggregator_->OnVote(old_vote, vote_);
    if (notify_listeners)
      aggregator_->NotifyListeners();
  }

 private:
  // This is the aggregator to which this voter's votes will be cast.
  MemoryPressureVoteAggregator* const aggregator_;

  // Optional<> is used here as the vote will be null until the voter's
  // first vote calculation.
  base::Optional<base::MemoryPressureListener::MemoryPressureLevel> vote_;

  SEQUENCE_CHECKER(sequence_checker_);
};

MemoryPressureVoteAggregator::MemoryPressureVoteAggregator(Delegate* delegate)
    : delegate_(delegate) {}

MemoryPressureVoteAggregator::~MemoryPressureVoteAggregator() {
  DCHECK_EQ(std::accumulate(votes_.begin(), votes_.end(), 0), 0);
}

std::unique_ptr<MemoryPressureVoter>
MemoryPressureVoteAggregator::CreateVoter() {
  return std::make_unique<MemoryPressureVoterImpl>(this);
}

void MemoryPressureVoteAggregator::OnVoteForTesting(
    base::Optional<MemoryPressureLevel> old_vote,
    base::Optional<MemoryPressureLevel> new_vote) {
  OnVote(old_vote, new_vote);
}

void MemoryPressureVoteAggregator::NotifyListenersForTesting() {
  NotifyListeners();
}

base::MemoryPressureListener::MemoryPressureLevel
MemoryPressureVoteAggregator::EvaluateVotesForTesting() {
  return EvaluateVotes();
}

void MemoryPressureVoteAggregator::OnVote(
    base::Optional<MemoryPressureLevel> old_vote,
    base::Optional<MemoryPressureLevel> new_vote) {
  DCHECK_CALLED_ON_VALID_SEQUENCE(sequence_checker_);
  DCHECK(old_vote || new_vote);
  if (old_vote) {
    DCHECK_LT(0u, votes_[old_vote.value()]);
    votes_[old_vote.value()]--;
  }
  if (new_vote)
    votes_[new_vote.value()]++;
  auto old_pressure_level = current_pressure_level_;

  // If the pressure level is not None then an asynchronous event will have been
  // started below, it needs to be ended.
  // Note that we record this event every time we receive a new vote to ensure
  // that the begin event doesn't get dropped during long pressure sessions.
  if (old_pressure_level ==
      MemoryPressureLevel::MEMORY_PRESSURE_LEVEL_CRITICAL) {
    TRACE_EVENT_NESTABLE_ASYNC_END0("base", "MemoryPressure::CriticalPressure",
                                    this);
  } else if (old_pressure_level ==
             MemoryPressureLevel::MEMORY_PRESSURE_LEVEL_MODERATE) {
    TRACE_EVENT_NESTABLE_ASYNC_END0("base", "MemoryPressure::ModeratePressure",
                                    this);
  }

  current_pressure_level_ = EvaluateVotes();

  // Start an asynchronous tracing event to record this pressure session.
  if (current_pressure_level_ ==
      MemoryPressureLevel::MEMORY_PRESSURE_LEVEL_CRITICAL) {
    TRACE_EVENT_NESTABLE_ASYNC_BEGIN0("base",
                                      "MemoryPressure::CriticalPressure", this);
  } else if (current_pressure_level_ ==
             MemoryPressureLevel::MEMORY_PRESSURE_LEVEL_MODERATE) {
    TRACE_EVENT_NESTABLE_ASYNC_BEGIN0("base",
                                      "MemoryPressure::ModeratePressure", this);
  }

  if (old_pressure_level != current_pressure_level_)
    delegate_->OnMemoryPressureLevelChanged(current_pressure_level_);
}

void MemoryPressureVoteAggregator::NotifyListeners() {
  delegate_->OnNotifyListenersRequested();
}

base::MemoryPressureListener::MemoryPressureLevel
MemoryPressureVoteAggregator::EvaluateVotes() const {
  DCHECK_CALLED_ON_VALID_SEQUENCE(sequence_checker_);
  static_assert(
      base::MemoryPressureListener::MEMORY_PRESSURE_LEVEL_CRITICAL == 2,
      "Ensure that each memory pressure level is handled by this method.");
  if (votes_[2])
    return base::MemoryPressureListener::MEMORY_PRESSURE_LEVEL_CRITICAL;
  if (votes_[1])
    return base::MemoryPressureListener::MEMORY_PRESSURE_LEVEL_MODERATE;
  return base::MemoryPressureListener::MEMORY_PRESSURE_LEVEL_NONE;
}

void MemoryPressureVoteAggregator::SetVotesForTesting(size_t none_votes,
                                                      size_t moderate_votes,
                                                      size_t critical_votes) {
  DCHECK_CALLED_ON_VALID_SEQUENCE(sequence_checker_);
  votes_[0] = none_votes;
  votes_[1] = moderate_votes;
  votes_[2] = critical_votes;
}

}  // namespace util
