// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_DEBUG_ACTIVITY_ANALYZER_H_
#define BASE_DEBUG_ACTIVITY_ANALYZER_H_

#include <map>
#include <memory>
#include <set>
#include <string>
#include <vector>

#include "base/base_export.h"
#include "base/debug/activity_tracker.h"
#include "base/memory/shared_memory_mapping.h"

namespace base {
namespace debug {

class GlobalActivityAnalyzer;

// This class provides analysis of data captured from a ThreadActivityTracker.
// When created, it takes a snapshot of the data held by the tracker and
// makes that information available to other code.
class BASE_EXPORT ThreadActivityAnalyzer {
 public:
  struct BASE_EXPORT Snapshot : ThreadActivityTracker::Snapshot {
    Snapshot();
    ~Snapshot();

    // The user-data snapshot for an activity, matching the |activity_stack|
    // of ThreadActivityTracker::Snapshot, if any.
    std::vector<ActivityUserData::Snapshot> user_data_stack;
  };

  // This class provides keys that uniquely identify a thread, even across
  // multiple processes.
  class ThreadKey {
   public:
    ThreadKey(int64_t pid, int64_t tid) : pid_(pid), tid_(tid) {}

    bool operator<(const ThreadKey& rhs) const {
      if (pid_ != rhs.pid_)
        return pid_ < rhs.pid_;
      return tid_ < rhs.tid_;
    }

    bool operator==(const ThreadKey& rhs) const {
      return (pid_ == rhs.pid_ && tid_ == rhs.tid_);
    }

   private:
    int64_t pid_;
    int64_t tid_;
  };

  // Creates an analyzer for an existing activity |tracker|. A snapshot is taken
  // immediately and the tracker is not referenced again.
  explicit ThreadActivityAnalyzer(const ThreadActivityTracker& tracker);

  // Creates an analyzer for a block of memory currently or previously in-use
  // by an activity-tracker. A snapshot is taken immediately and the memory
  // is not referenced again.
  ThreadActivityAnalyzer(void* base, size_t size);

  // Creates an analyzer for a block of memory held within a persistent-memory
  // |allocator| at the given |reference|. A snapshot is taken immediately and
  // the memory is not referenced again.
  ThreadActivityAnalyzer(PersistentMemoryAllocator* allocator,
                         PersistentMemoryAllocator::Reference reference);

  ~ThreadActivityAnalyzer();

  // Adds information from the global analyzer.
  void AddGlobalInformation(GlobalActivityAnalyzer* global);

  // Returns true iff the contained data is valid. Results from all other
  // methods are undefined if this returns false.
  bool IsValid() { return activity_snapshot_valid_; }

  // Gets the process id and its creation stamp.
  int64_t GetProcessId(int64_t* out_stamp = nullptr) {
    if (out_stamp)
      *out_stamp = activity_snapshot_.create_stamp;
    return activity_snapshot_.process_id;
  }

  // Gets the name of the thread.
  const std::string& GetThreadName() {
    return activity_snapshot_.thread_name;
  }

  // Gets the TheadKey for this thread.
  ThreadKey GetThreadKey() {
    return ThreadKey(activity_snapshot_.process_id,
                     activity_snapshot_.thread_id);
  }

  const Snapshot& activity_snapshot() { return activity_snapshot_; }

 private:
  friend class GlobalActivityAnalyzer;

  // The snapshot of the activity tracker taken at the moment of construction.
  Snapshot activity_snapshot_;

  // Flag indicating if the snapshot data is valid.
  bool activity_snapshot_valid_;

  // A reference into a persistent memory allocator, used by the global
  // analyzer to know where this tracker came from.
  PersistentMemoryAllocator::Reference allocator_reference_ = 0;

  DISALLOW_COPY_AND_ASSIGN(ThreadActivityAnalyzer);
};


// This class manages analyzers for all known processes and threads as stored
// in a persistent memory allocator. It supports retrieval of them through
// iteration and directly using a ThreadKey, which allows for cross-references
// to be resolved.
// Note that though atomic snapshots are used and everything has its snapshot
// taken at the same time, the multi-snapshot itself is not atomic and thus may
// show small inconsistencies between threads if attempted on a live system.
class BASE_EXPORT GlobalActivityAnalyzer {
 public:
  struct ProgramLocation {
    int module;
    uintptr_t offset;
  };

  using ThreadKey = ThreadActivityAnalyzer::ThreadKey;

  // Creates a global analyzer from a persistent memory allocator.
  explicit GlobalActivityAnalyzer(
      std::unique_ptr<PersistentMemoryAllocator> allocator);

  ~GlobalActivityAnalyzer();

  // Creates a global analyzer using a given persistent-memory |allocator|.
  static std::unique_ptr<GlobalActivityAnalyzer> CreateWithAllocator(
      std::unique_ptr<PersistentMemoryAllocator> allocator);

#if !defined(OS_NACL)
  // Creates a global analyzer using the contents of a file given in
  // |file_path|.
  static std::unique_ptr<GlobalActivityAnalyzer> CreateWithFile(
      const FilePath& file_path);
#endif  // !defined(OS_NACL)

  // Like above but accesses an allocator in a mapped shared-memory segment.
  static std::unique_ptr<GlobalActivityAnalyzer> CreateWithSharedMemory(
      base::ReadOnlySharedMemoryMapping mapping);

  // Iterates over all known valid processes and returns their PIDs or zero
  // if there are no more. Calls to GetFirstProcess() will perform a global
  // snapshot in order to provide a relatively consistent state across the
  // future calls to GetNextProcess() and GetFirst/NextAnalyzer(). PIDs are
  // returned in the order they're found meaning that a first-launched
  // controlling process will be found first. Note, however, that space
  // freed by an exiting process may be re-used by a later process.
  int64_t GetFirstProcess();
  int64_t GetNextProcess();

  // Iterates over all known valid analyzers for the a given process or returns
  // null if there are no more.
  //
  // GetFirstProcess() must be called first in order to capture a global
  // snapshot! Ownership stays with the global analyzer object and all existing
  // analyzer pointers are invalidated when GetFirstProcess() is called.
  ThreadActivityAnalyzer* GetFirstAnalyzer(int64_t pid);
  ThreadActivityAnalyzer* GetNextAnalyzer();

  // Gets the analyzer for a specific thread or null if there is none.
  // Ownership stays with the global analyzer object.
  ThreadActivityAnalyzer* GetAnalyzerForThread(const ThreadKey& key);

  // Extract user data based on a reference and its identifier.
  ActivityUserData::Snapshot GetUserDataSnapshot(int64_t pid,
                                                 uint32_t ref,
                                                 uint32_t id);

  // Extract the data for a specific process. An empty snapshot will be
  // returned if the process is not known.
  const ActivityUserData::Snapshot& GetProcessDataSnapshot(int64_t pid);

  // Gets all log messages stored within.
  std::vector<std::string> GetLogMessages();

  // Gets modules corresponding to a pid. This pid must come from a call to
  // GetFirst/NextProcess. Only modules that were first registered prior to
  // GetFirstProcess's snapshot are returned.
  std::vector<GlobalActivityTracker::ModuleInfo> GetModules(int64_t pid);

  // Gets the corresponding "program location" for a given "program counter".
  // This will return {0,0} if no mapping could be found.
  ProgramLocation GetProgramLocationFromAddress(uint64_t address);

  // Returns whether the data is complete. Data can be incomplete if the
  // recording size quota is hit.
  bool IsDataComplete() const;

 private:
  using AnalyzerMap =
      std::map<ThreadKey, std::unique_ptr<ThreadActivityAnalyzer>>;

  struct UserDataSnapshot {
    // Complex class needs out-of-line ctor/dtor.
    UserDataSnapshot();
    UserDataSnapshot(const UserDataSnapshot& rhs);
    UserDataSnapshot(UserDataSnapshot&& rhs);
    ~UserDataSnapshot();

    int64_t process_id;
    int64_t create_stamp;
    ActivityUserData::Snapshot data;
  };

  // Finds, creates, and indexes analyzers for all known processes and threads.
  void PrepareAllAnalyzers();

  // The persistent memory allocator holding all tracking data.
  std::unique_ptr<PersistentMemoryAllocator> allocator_;

  // The time stamp when analysis began. This is used to prevent looking into
  // process IDs that get reused when analyzing a live system.
  int64_t analysis_stamp_;

  // The iterator for finding tracking information in the allocator.
  PersistentMemoryAllocator::Iterator allocator_iterator_;

  // A set of all interesting memory references found within the allocator.
  std::set<PersistentMemoryAllocator::Reference> memory_references_;

  // A set of all process-data memory references found within the allocator.
  std::map<int64_t, UserDataSnapshot> process_data_;

  // A set of all process IDs collected during PrepareAllAnalyzers. These are
  // popped and returned one-by-one with calls to GetFirst/NextProcess().
  std::vector<int64_t> process_ids_;

  // A map, keyed by ThreadKey, of all valid activity analyzers.
  AnalyzerMap analyzers_;

  // The iterator within the analyzers_ map for returning analyzers through
  // first/next iteration.
  AnalyzerMap::iterator analyzers_iterator_;
  int64_t analyzers_iterator_pid_;

  DISALLOW_COPY_AND_ASSIGN(GlobalActivityAnalyzer);
};

}  // namespace debug
}  // namespace base

#endif  // BASE_DEBUG_ACTIVITY_ANALYZER_H_
