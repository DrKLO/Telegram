// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

// StatisticsRecorder holds all Histograms and BucketRanges that are used by
// Histograms in the system. It provides a general place for
// Histograms/BucketRanges to register, and supports a global API for accessing
// (i.e., dumping, or graphing) the data.

#ifndef BASE_METRICS_STATISTICS_RECORDER_H_
#define BASE_METRICS_STATISTICS_RECORDER_H_

#include <stdint.h>

#include <atomic>
#include <memory>
#include <string>
#include <unordered_map>
#include <unordered_set>
#include <vector>

#include "base/base_export.h"
#include "base/callback.h"
#include "base/gtest_prod_util.h"
#include "base/lazy_instance.h"
#include "base/macros.h"
#include "base/memory/weak_ptr.h"
#include "base/metrics/histogram_base.h"
#include "base/metrics/record_histogram_checker.h"
#include "base/strings/string_piece.h"
#include "base/synchronization/lock.h"

namespace base {

class BucketRanges;
class HistogramSnapshotManager;

// In-memory recorder of usage statistics (aka metrics, aka histograms).
//
// All the public methods are static and act on a global recorder. This global
// recorder is internally synchronized and all the static methods are thread
// safe.
//
// StatisticsRecorder doesn't have any public constructor. For testing purpose,
// you can create a temporary recorder using the factory method
// CreateTemporaryForTesting(). This temporary recorder becomes the global one
// until deleted. When this temporary recorder is deleted, it restores the
// previous global one.
class BASE_EXPORT StatisticsRecorder {
 public:
  // An interface class that allows the StatisticsRecorder to forcibly merge
  // histograms from providers when necessary.
  class HistogramProvider {
   public:
    // Merges all histogram information into the global versions.
    virtual void MergeHistogramDeltas() = 0;
  };

  typedef std::vector<HistogramBase*> Histograms;

  // Restores the previous global recorder.
  //
  // When several temporary recorders are created using
  // CreateTemporaryForTesting(), these recorders must be deleted in reverse
  // order of creation.
  //
  // This method is thread safe.
  //
  // Precondition: The recorder being deleted is the current global recorder.
  ~StatisticsRecorder();

  // Registers a provider of histograms that can be called to merge those into
  // the global recorder. Calls to ImportProvidedHistograms() will fetch from
  // registered providers.
  //
  // This method is thread safe.
  static void RegisterHistogramProvider(
      const WeakPtr<HistogramProvider>& provider);

  // Registers or adds a new histogram to the collection of statistics. If an
  // identically named histogram is already registered, then the argument
  // |histogram| will be deleted. The returned value is always the registered
  // histogram (either the argument, or the pre-existing registered histogram).
  //
  // This method is thread safe.
  static HistogramBase* RegisterOrDeleteDuplicate(HistogramBase* histogram);

  // Registers or adds a new BucketRanges. If an equivalent BucketRanges is
  // already registered, then the argument |ranges| will be deleted. The
  // returned value is always the registered BucketRanges (either the argument,
  // or the pre-existing one).
  //
  // This method is thread safe.
  static const BucketRanges* RegisterOrDeleteDuplicateRanges(
      const BucketRanges* ranges);

  // Methods for appending histogram data to a string.  Only histograms which
  // have |query| as a substring are written to |output| (an empty string will
  // process all registered histograms).
  //
  // These methods are thread safe.
  static void WriteHTMLGraph(const std::string& query, std::string* output);
  static void WriteGraph(const std::string& query, std::string* output);

  // Returns the histograms with |verbosity_level| as the serialization
  // verbosity.
  //
  // This method is thread safe.
  static std::string ToJSON(JSONVerbosityLevel verbosity_level);

  // Gets existing histograms.
  //
  // The order of returned histograms is not guaranteed.
  //
  // Ownership of the individual histograms remains with the StatisticsRecorder.
  //
  // This method is thread safe.
  static Histograms GetHistograms();

  // Gets BucketRanges used by all histograms registered. The order of returned
  // BucketRanges is not guaranteed.
  //
  // This method is thread safe.
  static std::vector<const BucketRanges*> GetBucketRanges();

  // Finds a histogram by name. Matches the exact name. Returns a null pointer
  // if a matching histogram is not found.
  //
  // This method is thread safe.
  static HistogramBase* FindHistogram(base::StringPiece name);

  // Imports histograms from providers.
  //
  // This method must be called on the UI thread.
  static void ImportProvidedHistograms();

  // Snapshots all histograms via |snapshot_manager|. |flags_to_set| is used to
  // set flags for each histogram. |required_flags| is used to select
  // histograms to be recorded. Only histograms that have all the flags
  // specified by the argument will be chosen. If all histograms should be
  // recorded, set it to |Histogram::kNoFlags|.
  static void PrepareDeltas(bool include_persistent,
                            HistogramBase::Flags flags_to_set,
                            HistogramBase::Flags required_flags,
                            HistogramSnapshotManager* snapshot_manager);

  using OnSampleCallback = base::RepeatingCallback<void(HistogramBase::Sample)>;

  // Sets the callback to notify when a new sample is recorded on the histogram
  // referred to by |histogram_name|. Can be called before or after the
  // histogram is created. Returns whether the callback was successfully set.
  //
  // This method is thread safe.
  static bool SetCallback(const std::string& histogram_name,
                          OnSampleCallback callback);

  // Clears any callback set on the histogram referred to by |histogram_name|.
  //
  // This method is thread safe.
  static void ClearCallback(const std::string& histogram_name);

  // Retrieves the callback for the histogram referred to by |histogram_name|,
  // or a null callback if no callback exists for this histogram.
  //
  // This method is thread safe.
  static OnSampleCallback FindCallback(const std::string& histogram_name);

  // Returns the number of known histograms.
  //
  // This method is thread safe.
  static size_t GetHistogramCount();

  // Initializes logging histograms with --v=1. Safe to call multiple times.
  // Is called from ctor but for browser it seems that it is more useful to
  // start logging after statistics recorder, so we need to init log-on-shutdown
  // later.
  //
  // This method is thread safe.
  static void InitLogOnShutdown();

  // Removes a histogram from the internal set of known ones. This can be
  // necessary during testing persistent histograms where the underlying
  // memory is being released.
  //
  // This method is thread safe.
  static void ForgetHistogramForTesting(base::StringPiece name);

  // Creates a temporary StatisticsRecorder object for testing purposes. All new
  // histograms will be registered in it until it is destructed or pushed aside
  // for the lifetime of yet another StatisticsRecorder object. The destruction
  // of the returned object will re-activate the previous one.
  // StatisticsRecorder objects must be deleted in the opposite order to which
  // they're created.
  //
  // This method is thread safe.
  static std::unique_ptr<StatisticsRecorder> CreateTemporaryForTesting()
      WARN_UNUSED_RESULT;

  // Sets the record checker for determining if a histogram should be recorded.
  // Record checker doesn't affect any already recorded histograms, so this
  // method must be called very early, before any threads have started.
  // Record checker methods can be called on any thread, so they shouldn't
  // mutate any state.
  static void SetRecordChecker(
      std::unique_ptr<RecordHistogramChecker> record_checker);

  // Checks if the given histogram should be recorded based on the
  // ShouldRecord() method of the record checker. If the record checker is not
  // set, returns true.
  //
  // This method is thread safe.
  static bool ShouldRecordHistogram(uint64_t histogram_hash);

  // Sorts histograms by name.
  static Histograms Sort(Histograms histograms);

  // Filters histograms by name. Only histograms which have |query| as a
  // substring in their name are kept. An empty query keeps all histograms.
  static Histograms WithName(Histograms histograms, const std::string& query);

  // Filters histograms by persistency. Only non-persistent histograms are kept.
  static Histograms NonPersistent(Histograms histograms);

  using GlobalSampleCallback = void (*)(const char* /*=histogram_name*/,
                                        uint64_t /*=name_hash*/,
                                        HistogramBase::Sample);
  // Installs a global callback which will be called for every added
  // histogram sample. The given callback is a raw function pointer in order
  // to be accessed lock-free and can be called on any thread.
  static void SetGlobalSampleCallback(
      const GlobalSampleCallback& global_sample_callback);

  // Returns the global callback, if any, that should be called every time a
  // histogram sample is added.
  static GlobalSampleCallback global_sample_callback() {
    return global_sample_callback_.load(std::memory_order_relaxed);
  }

  // Returns whether there's either a global histogram callback set,
  // or if any individual histograms have callbacks set. Used for early return
  // when histogram samples are added.
  static bool have_active_callbacks() {
    return have_active_callbacks_.load(std::memory_order_relaxed);
  }

 private:
  typedef std::vector<WeakPtr<HistogramProvider>> HistogramProviders;

  typedef std::unordered_map<StringPiece, HistogramBase*, StringPieceHash>
      HistogramMap;

  // We keep a map of callbacks to histograms, so that as histograms are
  // created, we can set the callback properly.
  typedef std::unordered_map<std::string, OnSampleCallback> CallbackMap;

  struct BucketRangesHash {
    size_t operator()(const BucketRanges* a) const;
  };

  struct BucketRangesEqual {
    bool operator()(const BucketRanges* a, const BucketRanges* b) const;
  };

  typedef std::
      unordered_set<const BucketRanges*, BucketRangesHash, BucketRangesEqual>
          RangesMap;

  friend class StatisticsRecorderTest;
  FRIEND_TEST_ALL_PREFIXES(StatisticsRecorderTest, IterationTest);

  // Initializes the global recorder if it doesn't already exist. Safe to call
  // multiple times.
  //
  // Precondition: The global lock is already acquired.
  static void EnsureGlobalRecorderWhileLocked();

  // Gets histogram providers.
  //
  // This method is thread safe.
  static HistogramProviders GetHistogramProviders();

  // Imports histograms from global persistent memory.
  //
  // Precondition: The global lock must not be held during this call.
  static void ImportGlobalPersistentHistograms();

  // Constructs a new StatisticsRecorder and sets it as the current global
  // recorder.
  //
  // Precondition: The global lock is already acquired.
  StatisticsRecorder();

  // Initialize implementation but without lock. Caller should guard
  // StatisticsRecorder by itself if needed (it isn't in unit tests).
  //
  // Precondition: The global lock is already acquired.
  static void InitLogOnShutdownWhileLocked();

  HistogramMap histograms_;
  CallbackMap callbacks_;
  RangesMap ranges_;
  HistogramProviders providers_;
  std::unique_ptr<RecordHistogramChecker> record_checker_;

  // Previous global recorder that existed when this one was created.
  StatisticsRecorder* previous_ = nullptr;

  // Global lock for internal synchronization.
  static LazyInstance<Lock>::Leaky lock_;

  // Current global recorder. This recorder is used by static methods. When a
  // new global recorder is created by CreateTemporaryForTesting(), then the
  // previous global recorder is referenced by top_->previous_.
  static StatisticsRecorder* top_;

  // Tracks whether InitLogOnShutdownWhileLocked() has registered a logging
  // function that will be called when the program finishes.
  static bool is_vlog_initialized_;

  // Track whether there are active histogram callbacks present.
  static std::atomic<bool> have_active_callbacks_;

  // Stores a raw callback which should be called on any every histogram sample
  // which gets added.
  static std::atomic<GlobalSampleCallback> global_sample_callback_;

  DISALLOW_COPY_AND_ASSIGN(StatisticsRecorder);
};

}  // namespace base

#endif  // BASE_METRICS_STATISTICS_RECORDER_H_
