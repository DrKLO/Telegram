// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/metrics/histogram_delta_serialization.h"

#include "base/logging.h"
#include "base/metrics/histogram_base.h"
#include "base/metrics/histogram_snapshot_manager.h"
#include "base/metrics/statistics_recorder.h"
#include "base/numerics/safe_conversions.h"
#include "base/pickle.h"
#include "base/values.h"

namespace base {

namespace {

// Create or find existing histogram and add the samples from pickle.
// Silently returns when seeing any data problem in the pickle.
void DeserializeHistogramAndAddSamples(PickleIterator* iter) {
  HistogramBase* histogram = DeserializeHistogramInfo(iter);
  if (!histogram)
    return;

  if (histogram->flags() & HistogramBase::kIPCSerializationSourceFlag) {
    DVLOG(1) << "Single process mode, histogram observed and not copied: "
             << histogram->histogram_name();
    return;
  }
  histogram->AddSamplesFromPickle(iter);
}

}  // namespace

HistogramDeltaSerialization::HistogramDeltaSerialization(
    const std::string& caller_name)
    : histogram_snapshot_manager_(this), serialized_deltas_(nullptr) {}

HistogramDeltaSerialization::~HistogramDeltaSerialization() = default;

void HistogramDeltaSerialization::PrepareAndSerializeDeltas(
    std::vector<std::string>* serialized_deltas,
    bool include_persistent) {
  DCHECK(thread_checker_.CalledOnValidThread());

  serialized_deltas_ = serialized_deltas;
  // Note: Before serializing, we set the kIPCSerializationSourceFlag for all
  // the histograms, so that the receiving process can distinguish them from the
  // local histograms.
  StatisticsRecorder::PrepareDeltas(
      include_persistent, Histogram::kIPCSerializationSourceFlag,
      Histogram::kNoFlags, &histogram_snapshot_manager_);
  serialized_deltas_ = nullptr;
}

// static
void HistogramDeltaSerialization::DeserializeAndAddSamples(
    const std::vector<std::string>& serialized_deltas) {
  for (auto it = serialized_deltas.begin(); it != serialized_deltas.end();
       ++it) {
    Pickle pickle(it->data(), checked_cast<int>(it->size()));
    PickleIterator iter(pickle);
    DeserializeHistogramAndAddSamples(&iter);
  }
}

void HistogramDeltaSerialization::RecordDelta(
    const HistogramBase& histogram,
    const HistogramSamples& snapshot) {
  DCHECK(thread_checker_.CalledOnValidThread());
  DCHECK_NE(0, snapshot.TotalCount());

  Pickle pickle;
  histogram.SerializeInfo(&pickle);
  snapshot.Serialize(&pickle);
  serialized_deltas_->push_back(
      std::string(static_cast<const char*>(pickle.data()), pickle.size()));
}

}  // namespace base
