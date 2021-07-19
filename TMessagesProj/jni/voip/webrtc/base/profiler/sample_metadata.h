// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_PROFILER_SAMPLE_METADATA_H_
#define BASE_PROFILER_SAMPLE_METADATA_H_

#include "base/optional.h"
#include "base/profiler/metadata_recorder.h"
#include "base/strings/string_piece.h"

// -----------------------------------------------------------------------------
// Usage documentation
// -----------------------------------------------------------------------------
//
// Overview:
// These functions provide a means to control the metadata attached to samples
// collected by the stack sampling profiler. Metadata state is shared between
// all threads within a process.
//
// Any samples collected by the sampling profiler will include the active
// metadata. This enables us to later analyze targeted subsets of samples
// (e.g. those collected during paint or layout).
//
// For example:
//
//   void DidStartLoad() {
//     base::SetSampleMetadata("Renderer.IsLoading", 1);
//   }
//
//   void DidFinishLoad() {
//     base::RemoveSampleMetadata("Renderer.IsLoading");
//   }
//
// Alternatively, ScopedSampleMetadata can be used to ensure that the metadata
// is removed correctly.
//
// For example:
//
//   void DoExpensiveWork() {
//     base::ScopedSampleMetadata metadata("xyz", 1);
//     if (...) {
//       ...
//       if (...) {
//         ...
//         return;
//       }
//     }
//     ...
//   }

namespace base {

class BASE_EXPORT ScopedSampleMetadata {
 public:
  // Set the metadata value associated with |name|.
  ScopedSampleMetadata(StringPiece name, int64_t value);

  // Set the metadata value associated with the pair (|name|, |key|). This
  // constructor allows the metadata to be associated with an additional
  // user-defined key. One might supply a key based on the frame id, for
  // example, to distinguish execution in service of scrolling between different
  // frames. Prefer the previous constructor if no user-defined metadata is
  // required. Note: values specified for a name and key are stored separately
  // from values specified with only a name.
  ScopedSampleMetadata(StringPiece name, int64_t key, int64_t value);

  ScopedSampleMetadata(const ScopedSampleMetadata&) = delete;
  ~ScopedSampleMetadata();

  ScopedSampleMetadata& operator=(const ScopedSampleMetadata&) = delete;

 private:
  const uint64_t name_hash_;
  Optional<int64_t> key_;
};

// Set the metadata value associated with |name| in the process-global stack
// sampling profiler metadata, overwriting any previous value set for that
// |name|.
BASE_EXPORT void SetSampleMetadata(StringPiece name, int64_t value);

// Set the metadata value associated with the pair (|name|, |key|) in the
// process-global stack sampling profiler metadata, overwriting any previous
// value set for that (|name|, |key|) pair. This constructor allows the metadata
// to be associated with an additional user-defined key. One might supply a key
// based on the frame id, for example, to distinguish execution in service of
// scrolling between different frames. Prefer the previous function if no
// user-defined metadata is required. Note: values specified for a name and key
// are stored separately from values specified with only a name.
BASE_EXPORT void SetSampleMetadata(StringPiece name,
                                   int64_t key,
                                   int64_t value);

// Removes the metadata item with the specified name from the process-global
// stack sampling profiler metadata.
//
// If such an item doesn't exist, this has no effect.
BASE_EXPORT void RemoveSampleMetadata(StringPiece name);

// Removes the metadata item with the specified (|name|, |key|) pair from the
// process-global stack sampling profiler metadata. This function does not alter
// values set with the name |name| but no key.
//
// If such an item doesn't exist, this has no effect.
BASE_EXPORT void RemoveSampleMetadata(StringPiece name, int64_t key);

// Applies the specified metadata to samples already recorded between
// |period_start| and |period_end| in all thread's active profiles, subject to
// the condition that the profile fully encompasses the period and the profile
// has not already completed. The condition ensures that the metadata is applied
// only if all execution during its scope was seen in the profile. This avoids
// biasng the samples towards the 'middle' of the execution seen during the
// metadata scope (i.e. because the start or end of execution was missed), at
// the cost of missing execution that are longer than the profiling period, or
// extend before or after it. |period_end| must be <= TimeTicks::Now().
BASE_EXPORT void ApplyMetadataToPastSamples(TimeTicks period_start,
                                            TimeTicks period_end,
                                            StringPiece name,
                                            int64_t value);
BASE_EXPORT void ApplyMetadataToPastSamples(TimeTicks period_start,
                                            TimeTicks period_end,
                                            StringPiece name,
                                            int64_t key,
                                            int64_t value);

// Returns the process-global metadata recorder instance used for tracking
// sampling profiler metadata.
//
// This function should not be called by non-profiler related code.
BASE_EXPORT MetadataRecorder* GetSampleMetadataRecorder();

}  // namespace base

#endif  // BASE_PROFILER_SAMPLE_METADATA_H_
