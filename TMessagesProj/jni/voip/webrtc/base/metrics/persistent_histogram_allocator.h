// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_METRICS_PERSISTENT_HISTOGRAM_ALLOCATOR_H_
#define BASE_METRICS_PERSISTENT_HISTOGRAM_ALLOCATOR_H_

#include <map>
#include <memory>
#include <string>
#include <vector>

#include "base/atomicops.h"
#include "base/base_export.h"
#include "base/feature_list.h"
#include "base/metrics/histogram_base.h"
#include "base/metrics/persistent_memory_allocator.h"
#include "base/process/process_handle.h"
#include "base/strings/string_piece.h"
#include "base/synchronization/lock.h"

namespace base {

class BucketRanges;
class FilePath;
class PersistentSampleMapRecords;
class PersistentSparseHistogramDataManager;
class WritableSharedMemoryRegion;

// Feature definition for enabling histogram persistence.
BASE_EXPORT extern const Feature kPersistentHistogramsFeature;


// A data manager for sparse histograms so each instance of such doesn't have
// to separately iterate over the entire memory segment. Though this class
// will generally be accessed through the PersistentHistogramAllocator above,
// it can be used independently on any PersistentMemoryAllocator (making it
// useable for testing). This object supports only one instance of a sparse
// histogram for a given id. Tests that create multiple identical histograms,
// perhaps to simulate multiple processes, should create a separate manager
// for each.
class BASE_EXPORT PersistentSparseHistogramDataManager {
 public:
  // Constructs the data manager. The allocator must live longer than any
  // managers that reference it.
  explicit PersistentSparseHistogramDataManager(
      PersistentMemoryAllocator* allocator);

  ~PersistentSparseHistogramDataManager();

  // Returns the object that manages the persistent-sample-map records for a
  // given |id|. Only one |user| of this data is allowed at a time. This does
  // an automatic Acquire() on the records. The user must call Release() on
  // the returned object when it is finished with it. Ownership of the records
  // object stays with this manager.
  PersistentSampleMapRecords* UseSampleMapRecords(uint64_t id,
                                                  const void* user);

  // Convenience method that gets the object for a given reference so callers
  // don't have to also keep their own pointer to the appropriate allocator.
  template <typename T>
  T* GetAsObject(PersistentMemoryAllocator::Reference ref) {
    return allocator_->GetAsObject<T>(ref);
  }

 private:
  friend class PersistentSampleMapRecords;

  // Gets the object holding records for a given sample-map id.
  PersistentSampleMapRecords* GetSampleMapRecordsWhileLocked(uint64_t id)
      EXCLUSIVE_LOCKS_REQUIRED(lock_);

  // Loads sample-map records looking for those belonging to the specified
  // |load_id|. Records found for other sample-maps are held for later use
  // without having to iterate again. This should be called only from a
  // PersistentSampleMapRecords object because those objects have a contract
  // that there are no other threads accessing the internal records_ field
  // of the object that is passed in.
  bool LoadRecords(PersistentSampleMapRecords* sample_map_records);

  // Weak-pointer to the allocator used by the sparse histograms.
  PersistentMemoryAllocator* allocator_;

  // Iterator within the allocator for finding sample records.
  PersistentMemoryAllocator::Iterator record_iterator_ GUARDED_BY(lock_);

  // Mapping of sample-map IDs to their sample records.
  std::map<uint64_t, std::unique_ptr<PersistentSampleMapRecords>>
      sample_records_ GUARDED_BY(lock_);

  base::Lock lock_;

  DISALLOW_COPY_AND_ASSIGN(PersistentSparseHistogramDataManager);
};


// This class manages sample-records used by a PersistentSampleMap container
// that underlies a persistent SparseHistogram object. It is broken out into a
// top-level class so that it can be forward-declared in other header files
// rather than include this entire file as would be necessary if it were
// declared within the PersistentSparseHistogramDataManager class above.
class BASE_EXPORT PersistentSampleMapRecords {
 public:
  // Constructs an instance of this class. The manager object must live longer
  // than all instances of this class that reference it, which is not usually
  // a problem since these objects are generally managed from within that
  // manager instance.
  PersistentSampleMapRecords(PersistentSparseHistogramDataManager* data_manager,
                             uint64_t sample_map_id);

  ~PersistentSampleMapRecords();

  // Resets the internal state for a new object using this data. The return
  // value is "this" as a convenience.
  PersistentSampleMapRecords* Acquire(const void* user);

  // Indicates that the using object is done with this data.
  void Release(const void* user);

  // Gets the next reference to a persistent sample-map record. The type and
  // layout of the data being referenced is defined entirely within the
  // PersistentSampleMap class.
  PersistentMemoryAllocator::Reference GetNext();

  // Creates a new persistent sample-map record for sample |value| and returns
  // a reference to it.
  PersistentMemoryAllocator::Reference CreateNew(HistogramBase::Sample value);

  // Convenience method that gets the object for a given reference so callers
  // don't have to also keep their own pointer to the appropriate allocator.
  // This is expected to be used with the SampleRecord structure defined inside
  // the persistent_sample_map.cc file but since that isn't exported (for
  // cleanliness of the interface), a template is defined that will be
  // resolved when used inside that file.
  template <typename T>
  T* GetAsObject(PersistentMemoryAllocator::Reference ref) {
    return data_manager_->GetAsObject<T>(ref);
  }

 private:
  friend PersistentSparseHistogramDataManager;

  // Weak-pointer to the parent data-manager object.
  PersistentSparseHistogramDataManager* data_manager_;

  // ID of PersistentSampleMap to which these records apply.
  const uint64_t sample_map_id_;

  // The current user of this set of records. It is used to ensure that no
  // more than one object is using these records at a given time.
  const void* user_ = nullptr;

  // This is the count of how many "records" have already been read by the
  // owning sample-map.
  size_t seen_ = 0;

  // This is the set of records previously found for a sample map. Because
  // there is ever only one object with a given ID (typically a hash of a
  // histogram name) and because the parent SparseHistogram has acquired
  // its own lock before accessing the PersistentSampleMap it controls, this
  // list can be accessed without acquiring any additional lock.
  std::vector<PersistentMemoryAllocator::Reference> records_;

  // This is the set of records found during iteration through memory. It
  // is appended in bulk to "records". Access to this vector can be done
  // only while holding the parent manager's lock.
  std::vector<PersistentMemoryAllocator::Reference> found_;

  DISALLOW_COPY_AND_ASSIGN(PersistentSampleMapRecords);
};


// This class manages histograms created within a PersistentMemoryAllocator.
class BASE_EXPORT PersistentHistogramAllocator {
 public:
  // A reference to a histogram. While this is implemented as PMA::Reference,
  // it is not conceptually the same thing. Outside callers should always use
  // a Reference matching the class it is for and not mix the two.
  using Reference = PersistentMemoryAllocator::Reference;

  // Iterator used for fetching persistent histograms from an allocator.
  // It is lock-free and thread-safe.
  // See PersistentMemoryAllocator::Iterator for more information.
  class BASE_EXPORT Iterator {
   public:
    // Constructs an iterator on a given |allocator|, starting at the beginning.
    // The allocator must live beyond the lifetime of the iterator.
    explicit Iterator(PersistentHistogramAllocator* allocator);

    // Gets the next histogram from persistent memory; returns null if there
    // are no more histograms to be found. This may still be called again
    // later to retrieve any new histograms added in the meantime.
    std::unique_ptr<HistogramBase> GetNext() { return GetNextWithIgnore(0); }

    // Gets the next histogram from persistent memory, ignoring one particular
    // reference in the process. Pass |ignore| of zero (0) to ignore nothing.
    std::unique_ptr<HistogramBase> GetNextWithIgnore(Reference ignore);

   private:
    // Weak-pointer to histogram allocator being iterated over.
    PersistentHistogramAllocator* allocator_;

    // The iterator used for stepping through objects in persistent memory.
    // It is lock-free and thread-safe which is why this class is also such.
    PersistentMemoryAllocator::Iterator memory_iter_;

    DISALLOW_COPY_AND_ASSIGN(Iterator);
  };

  // A PersistentHistogramAllocator is constructed from a PersistentMemory-
  // Allocator object of which it takes ownership.
  explicit PersistentHistogramAllocator(
      std::unique_ptr<PersistentMemoryAllocator> memory);
  virtual ~PersistentHistogramAllocator();

  // Direct access to underlying memory allocator. If the segment is shared
  // across threads or processes, reading data through these values does
  // not guarantee consistency. Use with care. Do not write.
  PersistentMemoryAllocator* memory_allocator() {
    return memory_allocator_.get();
  }

  // Implement the "metadata" API of a PersistentMemoryAllocator, forwarding
  // those requests to the real one.
  uint64_t Id() const { return memory_allocator_->Id(); }
  const char* Name() const { return memory_allocator_->Name(); }
  const void* data() const { return memory_allocator_->data(); }
  size_t length() const { return memory_allocator_->length(); }
  size_t size() const { return memory_allocator_->size(); }
  size_t used() const { return memory_allocator_->used(); }

  // Recreate a Histogram from data held in persistent memory. Though this
  // object will be local to the current process, the sample data will be
  // shared with all other threads referencing it. This method takes a |ref|
  // to where the top-level histogram data may be found in this allocator.
  // This method will return null if any problem is detected with the data.
  std::unique_ptr<HistogramBase> GetHistogram(Reference ref);

  // Allocate a new persistent histogram. The returned histogram will not
  // be able to be located by other allocators until it is "finalized".
  std::unique_ptr<HistogramBase> AllocateHistogram(
      HistogramType histogram_type,
      const std::string& name,
      int minimum,
      int maximum,
      const BucketRanges* bucket_ranges,
      int32_t flags,
      Reference* ref_ptr);

  // Finalize the creation of the histogram, making it available to other
  // processes if |registered| (as in: added to the StatisticsRecorder) is
  // True, forgetting it otherwise.
  void FinalizeHistogram(Reference ref, bool registered);

  // Merges the data in a persistent histogram with one held globally by the
  // StatisticsRecorder, updating the "logged" samples within the passed
  // object so that repeated merges are allowed. Don't call this on a "global"
  // allocator because histograms created there will already be in the SR.
  void MergeHistogramDeltaToStatisticsRecorder(HistogramBase* histogram);

  // As above but merge the "final" delta. No update of "logged" samples is
  // done which means it can operate on read-only objects. It's essential,
  // however, not to call this more than once or those final samples will
  // get recorded again.
  void MergeHistogramFinalDeltaToStatisticsRecorder(
      const HistogramBase* histogram);

  // Returns the object that manages the persistent-sample-map records for a
  // given |id|. Only one |user| of this data is allowed at a time. This does
  // an automatic Acquire() on the records. The user must call Release() on
  // the returned object when it is finished with it. Ownership stays with
  // this allocator.
  PersistentSampleMapRecords* UseSampleMapRecords(uint64_t id,
                                                  const void* user);

  // Create internal histograms for tracking memory use and allocation sizes
  // for allocator of |name| (which can simply be the result of Name()). This
  // is done seperately from construction for situations such as when the
  // histograms will be backed by memory provided by this very allocator.
  //
  // IMPORTANT: Callers must update tools/metrics/histograms/histograms.xml
  // with the following histograms:
  //    UMA.PersistentAllocator.name.Allocs
  //    UMA.PersistentAllocator.name.UsedPct
  void CreateTrackingHistograms(StringPiece name);
  void UpdateTrackingHistograms();

  // Clears the internal |last_created_| reference so testing can validate
  // operation without that optimization.
  void ClearLastCreatedReferenceForTesting();

 protected:
  // The structure used to hold histogram data in persistent memory. It is
  // defined and used entirely within the .cc file.
  struct PersistentHistogramData;

  // Gets the reference of the last histogram created, used to avoid
  // trying to import what was just created.
  PersistentHistogramAllocator::Reference last_created() {
    return subtle::NoBarrier_Load(&last_created_);
  }

  // Gets the next histogram in persistent data based on iterator while
  // ignoring a particular reference if it is found.
  std::unique_ptr<HistogramBase> GetNextHistogramWithIgnore(Iterator* iter,
                                                            Reference ignore);

 private:
  // Create a histogram based on saved (persistent) information about it.
  std::unique_ptr<HistogramBase> CreateHistogram(
      PersistentHistogramData* histogram_data_ptr);

  // Gets or creates an object in the global StatisticsRecorder matching
  // the |histogram| passed. Null is returned if one was not found and
  // one could not be created.
  HistogramBase* GetOrCreateStatisticsRecorderHistogram(
      const HistogramBase* histogram);

  // The memory allocator that provides the actual histogram storage.
  std::unique_ptr<PersistentMemoryAllocator> memory_allocator_;

  // The data-manager used to improve performance of sparse histograms.
  PersistentSparseHistogramDataManager sparse_histogram_data_manager_;

  // A reference to the last-created histogram in the allocator, used to avoid
  // trying to import what was just created.
  // TODO(bcwhite): Change this to std::atomic<PMA::Reference> when available.
  subtle::Atomic32 last_created_ = 0;

  DISALLOW_COPY_AND_ASSIGN(PersistentHistogramAllocator);
};


// A special case of the PersistentHistogramAllocator that operates on a
// global scale, collecting histograms created through standard macros and
// the FactoryGet() method.
class BASE_EXPORT GlobalHistogramAllocator
    : public PersistentHistogramAllocator {
 public:
  ~GlobalHistogramAllocator() override;

  // Create a global allocator using the passed-in memory |base|, |size|, and
  // other parameters. Ownership of the memory segment remains with the caller.
  static void CreateWithPersistentMemory(void* base,
                                         size_t size,
                                         size_t page_size,
                                         uint64_t id,
                                         StringPiece name);

  // Create a global allocator using an internal block of memory of the
  // specified |size| taken from the heap.
  static void CreateWithLocalMemory(size_t size, uint64_t id, StringPiece name);

#if !defined(OS_NACL)
  // Create a global allocator by memory-mapping a |file|. If the file does
  // not exist, it will be created with the specified |size|. If the file does
  // exist, the allocator will use and add to its contents, ignoring the passed
  // size in favor of the existing size. Returns whether the global allocator
  // was set.
  static bool CreateWithFile(const FilePath& file_path,
                             size_t size,
                             uint64_t id,
                             StringPiece name);

  // Creates a new file at |active_path|. If it already exists, it will first be
  // moved to |base_path|. In all cases, any old file at |base_path| will be
  // removed. If |spare_path| is non-empty and exists, that will be renamed and
  // used as the active file. Otherwise, the file will be created using the
  // given size, id, and name. Returns whether the global allocator was set.
  static bool CreateWithActiveFile(const FilePath& base_path,
                                   const FilePath& active_path,
                                   const FilePath& spare_path,
                                   size_t size,
                                   uint64_t id,
                                   StringPiece name);

  // Uses ConstructBaseActivePairFilePaths() to build a pair of file names which
  // are then used for CreateWithActiveFile(). |name| is used for both the
  // internal name for the allocator and also for the name of the file inside
  // |dir|.
  static bool CreateWithActiveFileInDir(const FilePath& dir,
                                        size_t size,
                                        uint64_t id,
                                        StringPiece name);

  // Constructs a filename using a name.
  static FilePath ConstructFilePath(const FilePath& dir, StringPiece name);

  // Like above but with timestamp and pid for use in upload directories.
  static FilePath ConstructFilePathForUploadDir(const FilePath& dir,
                                                StringPiece name,
                                                base::Time stamp,
                                                ProcessId pid);

  // Parses a filename to extract name, timestamp, and pid.
  static bool ParseFilePath(const FilePath& path,
                            std::string* out_name,
                            Time* out_stamp,
                            ProcessId* out_pid);

  // Constructs a set of names in |dir| based on name that can be used for a
  // base + active persistent memory mapped location for CreateWithActiveFile().
  // The spare path is a file that can be pre-created and moved to be active
  // without any startup penalty that comes from constructing the file. |name|
  // will be used as the basename of the file inside |dir|. |out_base_path|,
  // |out_active_path|, or |out_spare_path| may be null if not needed.
  static void ConstructFilePaths(const FilePath& dir,
                                 StringPiece name,
                                 FilePath* out_base_path,
                                 FilePath* out_active_path,
                                 FilePath* out_spare_path);

  // As above but puts the base files in a different "upload" directory. This
  // is useful when moving all completed files into a single directory for easy
  // upload management.
  static void ConstructFilePathsForUploadDir(const FilePath& active_dir,
                                             const FilePath& upload_dir,
                                             const std::string& name,
                                             FilePath* out_upload_path,
                                             FilePath* out_active_path,
                                             FilePath* out_spare_path);

  // Create a "spare" file that can later be made the "active" file. This
  // should be done on a background thread if possible.
  static bool CreateSpareFile(const FilePath& spare_path, size_t size);

  // Same as above but uses standard names. |name| is the name of the allocator
  // and is also used to create the correct filename.
  static bool CreateSpareFileInDir(const FilePath& dir_path,
                                   size_t size,
                                   StringPiece name);
#endif

  // Create a global allocator using a block of shared memory accessed
  // through the given |region|. The allocator maps the shared memory into
  // current process's virtual address space and frees it upon destruction.
  // The memory will continue to live if other processes have access to it.
  static void CreateWithSharedMemoryRegion(
      const WritableSharedMemoryRegion& region);

  // Sets a GlobalHistogramAllocator for globally storing histograms in
  // a space that can be persisted or shared between processes. There is only
  // ever one allocator for all such histograms created by a single process.
  // This takes ownership of the object and should be called as soon as
  // possible during startup to capture as many histograms as possible and
  // while operating single-threaded so there are no race-conditions.
  static void Set(std::unique_ptr<GlobalHistogramAllocator> allocator);

  // Gets a pointer to the global histogram allocator. Returns null if none
  // exists.
  static GlobalHistogramAllocator* Get();

  // This access to the persistent allocator is only for testing; it extracts
  // the current allocator completely. This allows easy creation of histograms
  // within persistent memory segments which can then be extracted and used in
  // other ways.
  static std::unique_ptr<GlobalHistogramAllocator> ReleaseForTesting();

  // Stores a pathname to which the contents of this allocator should be saved
  // in order to persist the data for a later use.
  void SetPersistentLocation(const FilePath& location);

  // Retrieves a previously set pathname to which the contents of this allocator
  // are to be saved.
  const FilePath& GetPersistentLocation() const;

  // Writes the internal data to a previously set location. This is generally
  // called when a process is exiting from a section of code that may not know
  // the filesystem. The data is written in an atomic manner. The return value
  // indicates success.
  bool WriteToPersistentLocation();

  // If there is a global metrics file being updated on disk, mark it to be
  // deleted when the process exits.
  void DeletePersistentLocation();

 private:
  friend class StatisticsRecorder;

  // Creates a new global histogram allocator.
  explicit GlobalHistogramAllocator(
      std::unique_ptr<PersistentMemoryAllocator> memory);

  // Import new histograms from the global histogram allocator. It's possible
  // for other processes to create histograms in the active memory segment;
  // this adds those to the internal list of known histograms to avoid creating
  // duplicates that would have to be merged during reporting. Every call to
  // this method resumes from the last entry it saw; it costs nothing if
  // nothing new has been added.
  void ImportHistogramsToStatisticsRecorder();

  // Builds a FilePath for a metrics file.
  static FilePath MakeMetricsFilePath(const FilePath& dir, StringPiece name);

  // Import always continues from where it left off, making use of a single
  // iterator to continue the work.
  Iterator import_iterator_;

  // The location to which the data should be persisted.
  FilePath persistent_location_;

  DISALLOW_COPY_AND_ASSIGN(GlobalHistogramAllocator);
};

}  // namespace base

#endif  // BASE_METRICS_PERSISTENT_HISTOGRAM_ALLOCATOR_H__
