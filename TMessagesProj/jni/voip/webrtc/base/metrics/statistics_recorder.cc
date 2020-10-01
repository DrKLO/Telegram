// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/metrics/statistics_recorder.h"

#include <memory>

#include "base/at_exit.h"
#include "base/debug/leak_annotations.h"
#include "base/json/string_escape.h"
#include "base/logging.h"
#include "base/memory/ptr_util.h"
#include "base/metrics/histogram.h"
#include "base/metrics/histogram_snapshot_manager.h"
#include "base/metrics/metrics_hashes.h"
#include "base/metrics/persistent_histogram_allocator.h"
#include "base/metrics/record_histogram_checker.h"
#include "base/stl_util.h"
#include "base/strings/stringprintf.h"
#include "base/values.h"

namespace base {
namespace {

bool HistogramNameLesser(const base::HistogramBase* a,
                         const base::HistogramBase* b) {
  return strcmp(a->histogram_name(), b->histogram_name()) < 0;
}

}  // namespace

// static
LazyInstance<Lock>::Leaky StatisticsRecorder::lock_;

// static
StatisticsRecorder* StatisticsRecorder::top_ = nullptr;

// static
bool StatisticsRecorder::is_vlog_initialized_ = false;

// static
std::atomic<bool> StatisticsRecorder::have_active_callbacks_{false};

// static
std::atomic<StatisticsRecorder::GlobalSampleCallback>
    StatisticsRecorder::global_sample_callback_{nullptr};

size_t StatisticsRecorder::BucketRangesHash::operator()(
    const BucketRanges* const a) const {
  return a->checksum();
}

bool StatisticsRecorder::BucketRangesEqual::operator()(
    const BucketRanges* const a,
    const BucketRanges* const b) const {
  return a->Equals(b);
}

StatisticsRecorder::~StatisticsRecorder() {
  const AutoLock auto_lock(lock_.Get());
  DCHECK_EQ(this, top_);
  top_ = previous_;
}

// static
void StatisticsRecorder::EnsureGlobalRecorderWhileLocked() {
  lock_.Get().AssertAcquired();
  if (top_)
    return;

  const StatisticsRecorder* const p = new StatisticsRecorder;
  // The global recorder is never deleted.
  ANNOTATE_LEAKING_OBJECT_PTR(p);
  DCHECK_EQ(p, top_);
}

// static
void StatisticsRecorder::RegisterHistogramProvider(
    const WeakPtr<HistogramProvider>& provider) {
  const AutoLock auto_lock(lock_.Get());
  EnsureGlobalRecorderWhileLocked();
  top_->providers_.push_back(provider);
}

// static
HistogramBase* StatisticsRecorder::RegisterOrDeleteDuplicate(
    HistogramBase* histogram) {
  // Declared before |auto_lock| to ensure correct destruction order.
  std::unique_ptr<HistogramBase> histogram_deleter;
  const AutoLock auto_lock(lock_.Get());
  EnsureGlobalRecorderWhileLocked();

  const char* const name = histogram->histogram_name();
  HistogramBase*& registered = top_->histograms_[name];

  if (!registered) {
    // |name| is guaranteed to never change or be deallocated so long
    // as the histogram is alive (which is forever).
    registered = histogram;
    ANNOTATE_LEAKING_OBJECT_PTR(histogram);  // see crbug.com/79322
    // If there are callbacks for this histogram, we set the kCallbackExists
    // flag.
    const auto callback_iterator = top_->callbacks_.find(name);
    if (callback_iterator != top_->callbacks_.end()) {
      if (!callback_iterator->second.is_null())
        histogram->SetFlags(HistogramBase::kCallbackExists);
      else
        histogram->ClearFlags(HistogramBase::kCallbackExists);
    }
    return histogram;
  }

  if (histogram == registered) {
    // The histogram was registered before.
    return histogram;
  }

  // We already have one histogram with this name.
  histogram_deleter.reset(histogram);
  return registered;
}

// static
const BucketRanges* StatisticsRecorder::RegisterOrDeleteDuplicateRanges(
    const BucketRanges* ranges) {
  DCHECK(ranges->HasValidChecksum());

  // Declared before |auto_lock| to ensure correct destruction order.
  std::unique_ptr<const BucketRanges> ranges_deleter;
  const AutoLock auto_lock(lock_.Get());
  EnsureGlobalRecorderWhileLocked();

  const BucketRanges* const registered = *top_->ranges_.insert(ranges).first;
  if (registered == ranges) {
    ANNOTATE_LEAKING_OBJECT_PTR(ranges);
  } else {
    ranges_deleter.reset(ranges);
  }

  return registered;
}

// static
void StatisticsRecorder::WriteHTMLGraph(const std::string& query,
                                        std::string* output) {
  for (const HistogramBase* const histogram :
       Sort(WithName(GetHistograms(), query))) {
    histogram->WriteHTMLGraph(output);
    *output += "<br><hr><br>";
  }
}

// static
void StatisticsRecorder::WriteGraph(const std::string& query,
                                    std::string* output) {
  if (query.length())
    StringAppendF(output, "Collections of histograms for %s\n", query.c_str());
  else
    output->append("Collections of all histograms\n");

  for (const HistogramBase* const histogram :
       Sort(WithName(GetHistograms(), query))) {
    histogram->WriteAscii(output);
    output->append("\n");
  }
}

// static
std::string StatisticsRecorder::ToJSON(JSONVerbosityLevel verbosity_level) {
  std::string output = "{\"histograms\":[";
  const char* sep = "";
  for (const HistogramBase* const histogram : Sort(GetHistograms())) {
    output += sep;
    sep = ",";
    std::string json;
    histogram->WriteJSON(&json, verbosity_level);
    output += json;
  }
  output += "]}";
  return output;
}

// static
std::vector<const BucketRanges*> StatisticsRecorder::GetBucketRanges() {
  std::vector<const BucketRanges*> out;
  const AutoLock auto_lock(lock_.Get());
  EnsureGlobalRecorderWhileLocked();
  out.reserve(top_->ranges_.size());
  out.assign(top_->ranges_.begin(), top_->ranges_.end());
  return out;
}

// static
HistogramBase* StatisticsRecorder::FindHistogram(base::StringPiece name) {
  // This must be called *before* the lock is acquired below because it will
  // call back into this object to register histograms. Those called methods
  // will acquire the lock at that time.
  ImportGlobalPersistentHistograms();

  const AutoLock auto_lock(lock_.Get());
  EnsureGlobalRecorderWhileLocked();

  const HistogramMap::const_iterator it = top_->histograms_.find(name);
  return it != top_->histograms_.end() ? it->second : nullptr;
}

// static
StatisticsRecorder::HistogramProviders
StatisticsRecorder::GetHistogramProviders() {
  const AutoLock auto_lock(lock_.Get());
  EnsureGlobalRecorderWhileLocked();
  return top_->providers_;
}

// static
void StatisticsRecorder::ImportProvidedHistograms() {
  // Merge histogram data from each provider in turn.
  for (const WeakPtr<HistogramProvider>& provider : GetHistogramProviders()) {
    // Weak-pointer may be invalid if the provider was destructed, though they
    // generally never are.
    if (provider)
      provider->MergeHistogramDeltas();
  }
}

// static
void StatisticsRecorder::PrepareDeltas(
    bool include_persistent,
    HistogramBase::Flags flags_to_set,
    HistogramBase::Flags required_flags,
    HistogramSnapshotManager* snapshot_manager) {
  Histograms histograms = GetHistograms();
  if (!include_persistent)
    histograms = NonPersistent(std::move(histograms));
  snapshot_manager->PrepareDeltas(Sort(std::move(histograms)), flags_to_set,
                                  required_flags);
}

// static
void StatisticsRecorder::InitLogOnShutdown() {
  const AutoLock auto_lock(lock_.Get());
  InitLogOnShutdownWhileLocked();
}

// static
bool StatisticsRecorder::SetCallback(const std::string& name,
                                     StatisticsRecorder::OnSampleCallback cb) {
  DCHECK(!cb.is_null());
  const AutoLock auto_lock(lock_.Get());
  EnsureGlobalRecorderWhileLocked();

  if (!top_->callbacks_.insert({name, std::move(cb)}).second)
    return false;

  const HistogramMap::const_iterator it = top_->histograms_.find(name);
  if (it != top_->histograms_.end())
    it->second->SetFlags(HistogramBase::kCallbackExists);

  have_active_callbacks_.store(
      global_sample_callback() || !top_->callbacks_.empty(),
      std::memory_order_relaxed);

  return true;
}

// static
void StatisticsRecorder::ClearCallback(const std::string& name) {
  const AutoLock auto_lock(lock_.Get());
  EnsureGlobalRecorderWhileLocked();

  top_->callbacks_.erase(name);

  // We also clear the flag from the histogram (if it exists).
  const HistogramMap::const_iterator it = top_->histograms_.find(name);
  if (it != top_->histograms_.end())
    it->second->ClearFlags(HistogramBase::kCallbackExists);

  have_active_callbacks_.store(
      global_sample_callback() || !top_->callbacks_.empty(),
      std::memory_order_relaxed);
}

// static
StatisticsRecorder::OnSampleCallback StatisticsRecorder::FindCallback(
    const std::string& name) {
  const AutoLock auto_lock(lock_.Get());
  EnsureGlobalRecorderWhileLocked();
  const auto it = top_->callbacks_.find(name);
  return it != top_->callbacks_.end() ? it->second : OnSampleCallback();
}

// static
void StatisticsRecorder::SetGlobalSampleCallback(
    const GlobalSampleCallback& new_global_sample_callback) {
  const AutoLock auto_lock(lock_.Get());
  EnsureGlobalRecorderWhileLocked();

  DCHECK(!global_sample_callback() || !new_global_sample_callback);
  global_sample_callback_.store(new_global_sample_callback);

  have_active_callbacks_.store(
      new_global_sample_callback || !top_->callbacks_.empty(),
      std::memory_order_relaxed);
}

// static
size_t StatisticsRecorder::GetHistogramCount() {
  const AutoLock auto_lock(lock_.Get());
  EnsureGlobalRecorderWhileLocked();
  return top_->histograms_.size();
}

// static
void StatisticsRecorder::ForgetHistogramForTesting(base::StringPiece name) {
  const AutoLock auto_lock(lock_.Get());
  EnsureGlobalRecorderWhileLocked();

  const HistogramMap::iterator found = top_->histograms_.find(name);
  if (found == top_->histograms_.end())
    return;

  HistogramBase* const base = found->second;
  if (base->GetHistogramType() != SPARSE_HISTOGRAM) {
    // When forgetting a histogram, it's likely that other information is
    // also becoming invalid. Clear the persistent reference that may no
    // longer be valid. There's no danger in this as, at worst, duplicates
    // will be created in persistent memory.
    static_cast<Histogram*>(base)->bucket_ranges()->set_persistent_reference(0);
  }

  top_->histograms_.erase(found);
}

// static
std::unique_ptr<StatisticsRecorder>
StatisticsRecorder::CreateTemporaryForTesting() {
  const AutoLock auto_lock(lock_.Get());
  return WrapUnique(new StatisticsRecorder());
}

// static
void StatisticsRecorder::SetRecordChecker(
    std::unique_ptr<RecordHistogramChecker> record_checker) {
  const AutoLock auto_lock(lock_.Get());
  EnsureGlobalRecorderWhileLocked();
  top_->record_checker_ = std::move(record_checker);
}

// static
bool StatisticsRecorder::ShouldRecordHistogram(uint64_t histogram_hash) {
  const AutoLock auto_lock(lock_.Get());
  EnsureGlobalRecorderWhileLocked();
  return !top_->record_checker_ ||
         top_->record_checker_->ShouldRecord(histogram_hash);
}

// static
StatisticsRecorder::Histograms StatisticsRecorder::GetHistograms() {
  // This must be called *before* the lock is acquired below because it will
  // call back into this object to register histograms. Those called methods
  // will acquire the lock at that time.
  ImportGlobalPersistentHistograms();

  Histograms out;

  const AutoLock auto_lock(lock_.Get());
  EnsureGlobalRecorderWhileLocked();

  out.reserve(top_->histograms_.size());
  for (const auto& entry : top_->histograms_)
    out.push_back(entry.second);

  return out;
}

// static
StatisticsRecorder::Histograms StatisticsRecorder::Sort(Histograms histograms) {
  std::sort(histograms.begin(), histograms.end(), &HistogramNameLesser);
  return histograms;
}

// static
StatisticsRecorder::Histograms StatisticsRecorder::WithName(
    Histograms histograms,
    const std::string& query) {
  // Need a C-string query for comparisons against C-string histogram name.
  const char* const query_string = query.c_str();
  histograms.erase(std::remove_if(histograms.begin(), histograms.end(),
                                  [query_string](const HistogramBase* const h) {
                                    return !strstr(h->histogram_name(),
                                                   query_string);
                                  }),
                   histograms.end());
  return histograms;
}

// static
StatisticsRecorder::Histograms StatisticsRecorder::NonPersistent(
    Histograms histograms) {
  histograms.erase(
      std::remove_if(histograms.begin(), histograms.end(),
                     [](const HistogramBase* const h) {
                       return (h->flags() & HistogramBase::kIsPersistent) != 0;
                     }),
      histograms.end());
  return histograms;
}

// static
void StatisticsRecorder::ImportGlobalPersistentHistograms() {
  // Import histograms from known persistent storage. Histograms could have been
  // added by other processes and they must be fetched and recognized locally.
  // If the persistent memory segment is not shared between processes, this call
  // does nothing.
  if (GlobalHistogramAllocator* allocator = GlobalHistogramAllocator::Get())
    allocator->ImportHistogramsToStatisticsRecorder();
}

// This singleton instance should be started during the single threaded portion
// of main(), and hence it is not thread safe. It initializes globals to provide
// support for all future calls.
StatisticsRecorder::StatisticsRecorder() {
  lock_.Get().AssertAcquired();
  previous_ = top_;
  top_ = this;
  InitLogOnShutdownWhileLocked();
}

// static
void StatisticsRecorder::InitLogOnShutdownWhileLocked() {
  lock_.Get().AssertAcquired();
  if (!is_vlog_initialized_ && VLOG_IS_ON(1)) {
    is_vlog_initialized_ = true;
    const auto dump_to_vlog = [](void*) {
      std::string output;
      WriteGraph("", &output);
      VLOG(1) << output;
    };
    AtExitManager::RegisterCallback(dump_to_vlog, nullptr);
  }
}

}  // namespace base
