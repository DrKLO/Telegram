// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/profiler/metadata_recorder.h"

#include "base/metrics/histogram_macros.h"

namespace base {

MetadataRecorder::ItemInternal::ItemInternal() = default;

MetadataRecorder::ItemInternal::~ItemInternal() = default;

MetadataRecorder::MetadataRecorder() {
  // Ensure that we have necessary atomic support.
  DCHECK(items_[0].is_active.is_lock_free());
  DCHECK(items_[0].value.is_lock_free());
}

MetadataRecorder::~MetadataRecorder() = default;

void MetadataRecorder::Set(uint64_t name_hash,
                           Optional<int64_t> key,
                           int64_t value) {
  AutoLock lock(write_lock_);

  // Acquiring the |write_lock_| ensures that:
  //
  //   - We don't try to write into the same new slot at the same time as
  //     another thread
  //   - We see all writes by other threads (acquiring a mutex implies acquire
  //     semantics)
  size_t item_slots_used = item_slots_used_.load(std::memory_order_relaxed);
  for (size_t i = 0; i < item_slots_used; ++i) {
    auto& item = items_[i];
    if (item.name_hash == name_hash && item.key == key) {
      item.value.store(value, std::memory_order_relaxed);

      const bool was_active =
          item.is_active.exchange(true, std::memory_order_release);
      if (!was_active)
        inactive_item_count_--;

      UMA_HISTOGRAM_COUNTS_10000("StackSamplingProfiler.MetadataSlotsUsed",
                                 item_slots_used);

      return;
    }
  }

  item_slots_used = TryReclaimInactiveSlots(item_slots_used);

  UMA_HISTOGRAM_COUNTS_10000("StackSamplingProfiler.MetadataSlotsUsed",
                             item_slots_used + 1);

  if (item_slots_used == items_.size()) {
    // The metadata recorder is full, forcing us to drop this metadata. The
    // above UMA histogram counting occupied metadata slots should help us set a
    // max size that avoids this condition during normal Chrome use.
    return;
  }

  // Wait until the item is fully created before setting |is_active| to true and
  // incrementing |item_slots_used_|, which will signal to readers that the item
  // is ready.
  auto& item = items_[item_slots_used];
  item.name_hash = name_hash;
  item.key = key;
  item.value.store(value, std::memory_order_relaxed);
  item.is_active.store(true, std::memory_order_release);
  item_slots_used_.fetch_add(1, std::memory_order_release);
}

void MetadataRecorder::Remove(uint64_t name_hash, Optional<int64_t> key) {
  AutoLock lock(write_lock_);

  size_t item_slots_used = item_slots_used_.load(std::memory_order_relaxed);
  for (size_t i = 0; i < item_slots_used; ++i) {
    auto& item = items_[i];
    if (item.name_hash == name_hash && item.key == key) {
      // A removed item will occupy its slot until that slot is reclaimed.
      const bool was_active =
          item.is_active.exchange(false, std::memory_order_relaxed);
      if (was_active)
        inactive_item_count_++;

      return;
    }
  }
}

MetadataRecorder::ScopedGetItems::ScopedGetItems(
    MetadataRecorder* metadata_recorder)
    : metadata_recorder_(metadata_recorder),
      auto_lock_(&metadata_recorder->read_lock_) {}

MetadataRecorder::ScopedGetItems::~ScopedGetItems() {}

// This function is marked as NO_THREAD_SAFETY_ANALYSIS because the analyzer
// doesn't understand that the lock is acquired in the constructor initializer
// list and can therefore be safely released here.
size_t MetadataRecorder::ScopedGetItems::GetItems(
    ProfileBuilder::MetadataItemArray* const items) NO_THREAD_SAFETY_ANALYSIS {
  size_t item_count = metadata_recorder_->GetItems(items);
  auto_lock_.Release();
  return item_count;
}

std::unique_ptr<ProfileBuilder::MetadataProvider>
MetadataRecorder::CreateMetadataProvider() {
  return std::make_unique<MetadataRecorder::ScopedGetItems>(this);
}

size_t MetadataRecorder::GetItems(
    ProfileBuilder::MetadataItemArray* const items) const {
  read_lock_.AssertAcquired();

  // If a writer adds a new item after this load, it will be ignored.  We do
  // this instead of calling item_slots_used_.load() explicitly in the for loop
  // bounds checking, which would be expensive.
  //
  // Also note that items are snapshotted sequentially and that items can be
  // modified mid-snapshot by non-suspended threads. This means that there's a
  // small chance that some items, especially those that occur later in the
  // array, may have values slightly "in the future" from when the sample was
  // actually collected. It also means that the array as returned may have never
  // existed in its entirety, although each name/value pair represents a
  // consistent item that existed very shortly after the thread was supended.
  size_t item_slots_used = item_slots_used_.load(std::memory_order_acquire);
  size_t write_index = 0;
  for (size_t read_index = 0; read_index < item_slots_used; ++read_index) {
    const auto& item = items_[read_index];
    // Because we wait until |is_active| is set to consider an item active and
    // that field is always set last, we ignore half-created items.
    if (item.is_active.load(std::memory_order_acquire)) {
      (*items)[write_index++] = ProfileBuilder::MetadataItem{
          item.name_hash, item.key, item.value.load(std::memory_order_relaxed)};
    }
  }

  return write_index;
}

size_t MetadataRecorder::TryReclaimInactiveSlots(size_t item_slots_used) {
  const size_t remaining_slots =
      ProfileBuilder::MAX_METADATA_COUNT - item_slots_used;

  if (inactive_item_count_ == 0 || inactive_item_count_ < remaining_slots) {
    // This reclaiming threshold has a few nice properties:
    //
    //   - It avoids reclaiming when no items have been removed
    //   - It makes doing so more likely as free slots become more scarce
    //   - It makes doing so less likely when the benefits are lower
    return item_slots_used;
  }

  if (read_lock_.Try()) {
    // The lock isn't already held by a reader or another thread reclaiming
    // slots.
    item_slots_used = ReclaimInactiveSlots(item_slots_used);
    read_lock_.Release();
  }

  return item_slots_used;
}

size_t MetadataRecorder::ReclaimInactiveSlots(size_t item_slots_used) {
  // From here until the end of the reclamation, we can safely use
  // memory_order_relaxed for all reads and writes. We don't need
  // memory_order_acquire because acquiring the write mutex gives acquire
  // semantics and no other threads can write after we hold that mutex. We don't
  // need memory_order_release because no readers can read until we release the
  // read mutex, which itself has release semantics.
  size_t first_inactive_item_idx = 0;
  size_t last_active_item_idx = item_slots_used - 1;
  while (first_inactive_item_idx < last_active_item_idx) {
    ItemInternal& inactive_item = items_[first_inactive_item_idx];
    ItemInternal& active_item = items_[last_active_item_idx];

    if (inactive_item.is_active.load(std::memory_order_relaxed)) {
      // Keep seeking forward to an inactive item.
      ++first_inactive_item_idx;
      continue;
    }

    if (!active_item.is_active.load(std::memory_order_relaxed)) {
      // Keep seeking backward to an active item. Skipping over this item
      // indicates that we're freeing the slot at this index.
      --last_active_item_idx;
      item_slots_used--;
      continue;
    }

    inactive_item.name_hash = active_item.name_hash;
    inactive_item.value.store(active_item.value.load(std::memory_order_relaxed),
                              std::memory_order_relaxed);
    inactive_item.is_active.store(true, std::memory_order_relaxed);

    ++first_inactive_item_idx;
    --last_active_item_idx;
    item_slots_used--;
  }

  item_slots_used_.store(item_slots_used, std::memory_order_relaxed);
  return item_slots_used;
}
}  // namespace base
