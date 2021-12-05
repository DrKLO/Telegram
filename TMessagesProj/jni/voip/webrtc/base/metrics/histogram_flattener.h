// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_METRICS_HISTOGRAM_FLATTENER_H_
#define BASE_METRICS_HISTOGRAM_FLATTENER_H_

#include <map>
#include <string>

#include "base/macros.h"
#include "base/metrics/histogram.h"

namespace base {

class HistogramSamples;

// HistogramFlattener is an interface used by HistogramSnapshotManager, which
// handles the logistics of gathering up available histograms for recording.
class BASE_EXPORT HistogramFlattener {
 public:
  virtual ~HistogramFlattener() = default;

  virtual void RecordDelta(const HistogramBase& histogram,
                           const HistogramSamples& snapshot) = 0;

 protected:
  HistogramFlattener() = default;

 private:
  DISALLOW_COPY_AND_ASSIGN(HistogramFlattener);
};

}  // namespace base

#endif  // BASE_METRICS_HISTOGRAM_FLATTENER_H_
