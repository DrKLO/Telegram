// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

// Activity tracking provides a low-overhead method of collecting information
// about the state of the application for analysis both while it is running
// and after it has terminated unexpectedly. Its primary purpose is to help
// locate reasons the browser becomes unresponsive by providing insight into
// what all the various threads and processes are (or were) doing.

#ifndef BASE_DEBUG_ACTIVITY_TRACKER_H_
#define BASE_DEBUG_ACTIVITY_TRACKER_H_

// std::atomic is undesired due to performance issues when used as global
// variables. There are no such instances here. This module uses the
// PersistentMemoryAllocator which also uses std::atomic and is written
// by the same author.
#include <atomic>
#include <map>
#include <memory>
#include <string>
#include <vector>

#include "base/atomicops.h"
#include "base/base_export.h"
#include "base/callback.h"
#include "base/compiler_specific.h"
#include "base/gtest_prod_util.h"
#include "base/location.h"
#include "base/memory/shared_memory_mapping.h"
#include "base/metrics/persistent_memory_allocator.h"
#include "base/process/process_handle.h"
#include "base/sequenced_task_runner.h"
#include "base/strings/string_piece.h"
#include "base/strings/utf_string_conversions.h"
#include "base/threading/platform_thread.h"
#include "base/threading/thread_local.h"

namespace base {

struct PendingTask;

class FilePath;
class Lock;
class PlatformThreadHandle;
class Process;
class WaitableEvent;

namespace debug {

class ThreadActivityTracker;


enum : int {
  // The maximum number of call-stack addresses stored per activity. This
  // cannot be changed without also changing the version number of the
  // structure. See kTypeIdActivityTracker in GlobalActivityTracker.
  kActivityCallStackSize = 10,
};

// A class for keeping all information needed to verify that a structure is
// associated with a given process.
struct OwningProcess {
  OwningProcess();
  ~OwningProcess();

  // Initializes structure with the current process id and the current time.
  // These can uniquely identify a process. A unique non-zero data_id will be
  // set making it possible to tell using atomic reads if the data has changed.
  void Release_Initialize(int64_t pid = 0);

  // Explicitly sets the process ID.
  void SetOwningProcessIdForTesting(int64_t pid, int64_t stamp);

  // Gets the associated process ID, in native form, and the creation timestamp
  // from memory without loading the entire structure for analysis. This will
  // return false if no valid process ID is available.
  static bool GetOwningProcessId(const void* memory,
                                 int64_t* out_id,
                                 int64_t* out_stamp);

  // SHA1(base::debug::OwningProcess): Increment this if structure changes!
  static constexpr uint32_t kPersistentTypeId = 0xB1179672 + 1;

  // Expected size for 32/64-bit check by PersistentMemoryAllocator.
  static constexpr size_t kExpectedInstanceSize = 24;

  std::atomic<uint32_t> data_id;
  uint32_t padding;
  int64_t process_id;
  int64_t create_stamp;
};

// The data associated with an activity is dependent upon the activity type.
// This union defines all of the various fields. All fields must be explicitly
// sized types to ensure no interoperability problems between 32-bit and
// 64-bit systems.
union ActivityData {
  // Expected size for 32/64-bit check.
  // TODO(bcwhite): VC2015 doesn't allow statics in unions. Fix when it does.
  // static constexpr size_t kExpectedInstanceSize = 8;

  // Generic activities don't have any defined structure.
  struct {
    uint32_t id;   // An arbitrary identifier used for association.
    int32_t info;  // An arbitrary value used for information purposes.
  } generic;
  struct {
    uint64_t sequence_id;  // The sequence identifier of the posted task.
  } task;
  struct {
    uint64_t lock_address;  // The memory address of the lock object.
  } lock;
  struct {
    uint64_t event_address;  // The memory address of the event object.
  } event;
  struct {
    int64_t thread_id;  // A unique identifier for a thread within a process.
  } thread;
  struct {
    int64_t process_id;  // A unique identifier for a process.
  } process;
  struct {
    uint32_t code;  // An "exception code" number.
  } exception;

  // These methods create an ActivityData object from the appropriate
  // parameters. Objects of this type should always be created this way to
  // ensure that no fields remain unpopulated should the set of recorded
  // fields change. They're defined inline where practical because they
  // reduce to loading a small local structure with a few values, roughly
  // the same as loading all those values into parameters.

  static ActivityData ForGeneric(uint32_t id, int32_t info) {
    ActivityData data;
    data.generic.id = id;
    data.generic.info = info;
    return data;
  }

  static ActivityData ForTask(uint64_t sequence) {
    ActivityData data;
    data.task.sequence_id = sequence;
    return data;
  }

  static ActivityData ForLock(const void* lock) {
    ActivityData data;
    data.lock.lock_address = reinterpret_cast<uintptr_t>(lock);
    return data;
  }

  static ActivityData ForEvent(const void* event) {
    ActivityData data;
    data.event.event_address = reinterpret_cast<uintptr_t>(event);
    return data;
  }

  static ActivityData ForThread(const PlatformThreadHandle& handle);
  static ActivityData ForThread(const int64_t id) {
    ActivityData data;
    data.thread.thread_id = id;
    return data;
  }

  static ActivityData ForProcess(const int64_t id) {
    ActivityData data;
    data.process.process_id = id;
    return data;
  }

  static ActivityData ForException(const uint32_t code) {
    ActivityData data;
    data.exception.code = code;
    return data;
  }
};

// A "null" activity-data that can be passed to indicate "do not change".
extern const ActivityData kNullActivityData;


// A helper class that is used for managing memory allocations within a
// persistent memory allocator. Instances of this class are NOT thread-safe.
// Use from a single thread or protect access with a lock.
class BASE_EXPORT ActivityTrackerMemoryAllocator {
 public:
  using Reference = PersistentMemoryAllocator::Reference;

  // Creates a instance for allocating objects of a fixed |object_type|, a
  // corresponding |object_free| type, and the |object_size|. An internal
  // cache of the last |cache_size| released references will be kept for
  // quick future fetches. If |make_iterable| then allocated objects will
  // be marked "iterable" in the allocator.
  ActivityTrackerMemoryAllocator(PersistentMemoryAllocator* allocator,
                                 uint32_t object_type,
                                 uint32_t object_free_type,
                                 size_t object_size,
                                 size_t cache_size,
                                 bool make_iterable);
  ~ActivityTrackerMemoryAllocator();

  // Gets a reference to an object of the configured type. This can return
  // a null reference if it was not possible to allocate the memory.
  Reference GetObjectReference();

  // Returns an object to the "free" pool.
  void ReleaseObjectReference(Reference ref);

  // Helper function to access an object allocated using this instance.
  template <typename T>
  T* GetAsObject(Reference ref) {
    return allocator_->GetAsObject<T>(ref);
  }

  // Similar to GetAsObject() but converts references to arrays of objects.
  template <typename T>
  T* GetAsArray(Reference ref, size_t count) {
    return allocator_->GetAsArray<T>(ref, object_type_, count);
  }

  // The current "used size" of the internal cache, visible for testing.
  size_t cache_used() const { return cache_used_; }

 private:
  PersistentMemoryAllocator* const allocator_;
  const uint32_t object_type_;
  const uint32_t object_free_type_;
  const size_t object_size_;
  const size_t cache_size_;
  const bool make_iterable_;

  // An iterator for going through persistent memory looking for free'd objects.
  PersistentMemoryAllocator::Iterator iterator_;

  // The cache of released object memories.
  std::unique_ptr<Reference[]> cache_values_;
  size_t cache_used_;

  DISALLOW_COPY_AND_ASSIGN(ActivityTrackerMemoryAllocator);
};


// This structure is the full contents recorded for every activity pushed
// onto the stack. The |activity_type| indicates what is actually stored in
// the |data| field. All fields must be explicitly sized types to ensure no
// interoperability problems between 32-bit and 64-bit systems.
struct Activity {
  // SHA1(base::debug::Activity): Increment this if structure changes!
  static constexpr uint32_t kPersistentTypeId = 0x99425159 + 1;
  // Expected size for 32/64-bit check. Update this if structure changes!
  static constexpr size_t kExpectedInstanceSize =
      48 + 8 * kActivityCallStackSize;

  // The type of an activity on the stack. Activities are broken into
  // categories with the category ID taking the top 4 bits and the lower
  // bits representing an action within that category. This combination
  // makes it easy to "switch" based on the type during analysis.
  enum Type : uint8_t {
    // This "null" constant is used to indicate "do not change" in calls.
    ACT_NULL = 0,

    // Task activities involve callbacks posted to a thread or thread-pool
    // using the PostTask() method or any of its friends.
    ACT_TASK = 1 << 4,
    ACT_TASK_RUN = ACT_TASK,

    // Lock activities involve the acquisition of "mutex" locks.
    ACT_LOCK = 2 << 4,
    ACT_LOCK_ACQUIRE = ACT_LOCK,
    ACT_LOCK_RELEASE,

    // Event activities involve operations on a WaitableEvent.
    ACT_EVENT = 3 << 4,
    ACT_EVENT_WAIT = ACT_EVENT,
    ACT_EVENT_SIGNAL,

    // Thread activities involve the life management of threads.
    ACT_THREAD = 4 << 4,
    ACT_THREAD_START = ACT_THREAD,
    ACT_THREAD_JOIN,

    // Process activities involve the life management of processes.
    ACT_PROCESS = 5 << 4,
    ACT_PROCESS_START = ACT_PROCESS,
    ACT_PROCESS_WAIT,

    // Exception activities indicate the occurence of something unexpected.
    ACT_EXCEPTION = 14 << 4,

    // Generic activities are user defined and can be anything.
    ACT_GENERIC = 15 << 4,

    // These constants can be used to separate the category and action from
    // a combined activity type.
    ACT_CATEGORY_MASK = 0xF << 4,
    ACT_ACTION_MASK = 0xF
  };

  // Internal representation of time. During collection, this is in "ticks"
  // but when returned in a snapshot, it is "wall time".
  int64_t time_internal;

  // The address that pushed the activity onto the stack as a raw number.
  uint64_t calling_address;

  // The address that is the origin of the activity if it not obvious from
  // the call stack. This is useful for things like tasks that are posted
  // from a completely different thread though most activities will leave
  // it null.
  uint64_t origin_address;

  // Array of program-counters that make up the top of the call stack.
  // Despite the fixed size, this list is always null-terminated. Entries
  // after the terminator have no meaning and may or may not also be null.
  // The list will be completely empty if call-stack collection is not
  // enabled.
  uint64_t call_stack[kActivityCallStackSize];

  // Reference to arbitrary user data within the persistent memory segment
  // and a unique identifier for it.
  uint32_t user_data_ref;
  uint32_t user_data_id;

  // The (enumerated) type of the activity. This defines what fields of the
  // |data| record are valid.
  uint8_t activity_type;

  // Padding to ensure that the next member begins on a 64-bit boundary
  // even on 32-bit builds which ensures inter-operability between CPU
  // architectures. New fields can be taken from this space.
  uint8_t padding[7];

  // Information specific to the |activity_type|.
  ActivityData data;

  static void FillFrom(Activity* activity,
                       const void* program_counter,
                       const void* origin,
                       Type type,
                       const ActivityData& data);
};

// This class manages arbitrary user data that can be associated with activities
// done by a thread by supporting key/value pairs of any type. This can provide
// additional information during debugging. It is also used to store arbitrary
// global data. All updates must be done from the same thread though other
// threads can read it concurrently if they create new objects using the same
// memory. For a thread-safe version, see ThreadSafeUserData later on.
class BASE_EXPORT ActivityUserData {
 public:
  // List of known value type. REFERENCE types must immediately follow the non-
  // external types.
  enum ValueType : uint8_t {
    END_OF_VALUES = 0,
    RAW_VALUE,
    RAW_VALUE_REFERENCE,
    STRING_VALUE,
    STRING_VALUE_REFERENCE,
    CHAR_VALUE,
    BOOL_VALUE,
    SIGNED_VALUE,
    UNSIGNED_VALUE,
  };

  class BASE_EXPORT TypedValue {
   public:
    TypedValue();
    TypedValue(const TypedValue& other);
    ~TypedValue();

    ValueType type() const { return type_; }

    // These methods return the extracted value in the correct format.
    StringPiece Get() const;
    StringPiece GetString() const;
    bool GetBool() const;
    char GetChar() const;
    int64_t GetInt() const;
    uint64_t GetUint() const;

    // These methods return references to process memory as originally provided
    // to corresponding Set calls. USE WITH CAUTION! There is no guarantee that
    // the referenced memory is assessible or useful.  It's possible that:
    //  - the memory was free'd and reallocated for a different purpose
    //  - the memory has been released back to the OS
    //  - the memory belongs to a different process's address space
    // Dereferencing the returned StringPiece when the memory is not accessible
    // will cause the program to SEGV!
    StringPiece GetReference() const;
    StringPiece GetStringReference() const;

   private:
    friend class ActivityUserData;

    ValueType type_ = END_OF_VALUES;
    uint64_t short_value_;    // Used to hold copy of numbers, etc.
    std::string long_value_;  // Used to hold copy of raw/string data.
    StringPiece ref_value_;   // Used to hold reference to external data.
  };

  using Snapshot = std::map<std::string, TypedValue>;

  // Initialize the object either as a "sink" that just accepts and discards
  // data or an active one that writes to a given (zeroed) memory block.
  ActivityUserData();
  ActivityUserData(void* memory, size_t size, int64_t pid = 0);
  virtual ~ActivityUserData();

  // Gets the unique ID number for this user data. If this changes then the
  // contents have been overwritten by another thread. The return value is
  // always non-zero unless it's actually just a data "sink".
  uint32_t id() const {
    return header_ ? header_->owner.data_id.load(std::memory_order_relaxed) : 0;
  }

  // Writes a |value| (as part of a key/value pair) that will be included with
  // the activity in any reports. The same |name| can be written multiple times
  // with each successive call overwriting the previously stored |value|. For
  // raw and string values, the maximum size of successive writes is limited by
  // the first call. The length of "name" is limited to 255 characters.
  //
  // This information is stored on a "best effort" basis. It may be dropped if
  // the memory buffer is full or the associated activity is beyond the maximum
  // recording depth.
  //
  // Some methods return pointers to the stored value that can be further
  // modified using normal std::atomic operations without having to go through
  // this interface, thus avoiding the relatively expensive name lookup.
  // ==> Use std::memory_order_relaxed as the "order" parameter to atomic ops.
  // Remember that the return value will be nullptr if the value could not
  // be stored!
  void Set(StringPiece name, const void* memory, size_t size) {
    Set(name, RAW_VALUE, memory, size);
  }
  void SetString(StringPiece name, StringPiece value) {
    Set(name, STRING_VALUE, value.data(), value.length());
  }
  void SetString(StringPiece name, StringPiece16 value) {
    SetString(name, UTF16ToUTF8(value));
  }
  std::atomic<bool>* SetBool(StringPiece name, bool value) {
    char cvalue = value ? 1 : 0;
    void* addr = Set(name, BOOL_VALUE, &cvalue, sizeof(cvalue));
    return reinterpret_cast<std::atomic<bool>*>(addr);
  }
  std::atomic<char>* SetChar(StringPiece name, char value) {
    void* addr = Set(name, CHAR_VALUE, &value, sizeof(value));
    return reinterpret_cast<std::atomic<char>*>(addr);
  }
  std::atomic<int64_t>* SetInt(StringPiece name, int64_t value) {
    void* addr = Set(name, SIGNED_VALUE, &value, sizeof(value));
    return reinterpret_cast<std::atomic<int64_t>*>(addr);
  }
  std::atomic<uint64_t>* SetUint(StringPiece name, uint64_t value) {
    void* addr = Set(name, UNSIGNED_VALUE, &value, sizeof(value));
    return reinterpret_cast<std::atomic<uint64_t>*>(addr);
  }

  // These function as above but don't actually copy the data into the
  // persistent memory. They store unaltered pointers along with a size. These
  // can be used in conjuction with a memory dump to find certain large pieces
  // of information.
  void SetReference(StringPiece name, const void* memory, size_t size) {
    SetReference(name, RAW_VALUE_REFERENCE, memory, size);
  }
  void SetStringReference(StringPiece name, StringPiece value) {
    SetReference(name, STRING_VALUE_REFERENCE, value.data(), value.length());
  }

  // Creates a snapshot of the key/value pairs contained within. The returned
  // data will be fixed, independent of whatever changes afterward. There is
  // some protection against concurrent modification. This will return false
  // if the data is invalid or if a complete overwrite of the contents is
  // detected.
  bool CreateSnapshot(Snapshot* output_snapshot) const;

  // Gets the base memory address used for storing data.
  const void* GetBaseAddress() const;

  // Explicitly sets the process ID.
  void SetOwningProcessIdForTesting(int64_t pid, int64_t stamp);

  // Gets the associated process ID, in native form, and the creation timestamp
  // from tracker memory without loading the entire structure for analysis. This
  // will return false if no valid process ID is available.
  static bool GetOwningProcessId(const void* memory,
                                 int64_t* out_id,
                                 int64_t* out_stamp);

 protected:
  virtual void* Set(StringPiece name,
                    ValueType type,
                    const void* memory,
                    size_t size);

 private:
  FRIEND_TEST_ALL_PREFIXES(ActivityTrackerTest, UserDataTest);

  enum : size_t { kMemoryAlignment = sizeof(uint64_t) };

  // A structure that defines the structure header in memory.
  struct MemoryHeader {
    MemoryHeader();
    ~MemoryHeader();

    OwningProcess owner;  // Information about the creating process.
  };

  // Header to a key/value record held in persistent memory.
  struct FieldHeader {
    FieldHeader();
    ~FieldHeader();

    std::atomic<uint8_t> type;         // Encoded ValueType
    uint8_t name_size;                 // Length of "name" key.
    std::atomic<uint16_t> value_size;  // Actual size of of the stored value.
    uint16_t record_size;              // Total storage of name, value, header.
  };

  // A structure used to reference data held outside of persistent memory.
  struct ReferenceRecord {
    uint64_t address;
    uint64_t size;
  };

  // This record is used to hold known value is a map so that they can be
  // found and overwritten later.
  struct ValueInfo {
    ValueInfo();
    ValueInfo(ValueInfo&&);
    ~ValueInfo();

    StringPiece name;                 // The "key" of the record.
    ValueType type;                   // The type of the value.
    void* memory;                     // Where the "value" is held.
    std::atomic<uint16_t>* size_ptr;  // Address of the actual size of value.
    size_t extent;                    // The total storage of the value,
  };                                  // typically rounded up for alignment.

  void SetReference(StringPiece name,
                    ValueType type,
                    const void* memory,
                    size_t size);

  // Loads any data already in the memory segment. This allows for accessing
  // records created previously. If this detects that the underlying data has
  // gone away (cleared by another thread/process), it will invalidate all the
  // data in this object and turn it into simple "sink" with no values to
  // return.
  void ImportExistingData() const;

  // A map of all the values within the memory block, keyed by name for quick
  // updates of the values. This is "mutable" because it changes on "const"
  // objects even when the actual data values can't change.
  mutable std::map<StringPiece, ValueInfo> values_;

  // Information about the memory block in which new data can be stored. These
  // are "mutable" because they change even on "const" objects that are just
  // skipping already set values.
  mutable char* memory_;
  mutable size_t available_;

  // A pointer to the memory header for this instance.
  MemoryHeader* const header_;

  // These hold values used when initially creating the object. They are
  // compared against current header values to check for outside changes.
  const uint32_t orig_data_id;
  const int64_t orig_process_id;
  const int64_t orig_create_stamp;

  DISALLOW_COPY_AND_ASSIGN(ActivityUserData);
};

// This class manages tracking a stack of activities for a single thread in
// a persistent manner, implementing a bounded-size stack in a fixed-size
// memory allocation. In order to support an operational mode where another
// thread is analyzing this data in real-time, atomic operations are used
// where necessary to guarantee a consistent view from the outside.
//
// This class is not generally used directly but instead managed by the
// GlobalActivityTracker instance and updated using Scoped*Activity local
// objects.
class BASE_EXPORT ThreadActivityTracker {
 public:
  using ActivityId = uint32_t;

  // This structure contains all the common information about the thread so
  // it doesn't have to be repeated in every entry on the stack. It is defined
  // and used completely within the .cc file.
  struct Header;

  // This structure holds a copy of all the internal data at the moment the
  // "snapshot" operation is done. It is disconnected from the live tracker
  // so that continued operation of the thread will not cause changes here.
  struct BASE_EXPORT Snapshot {
    // Explicit constructor/destructor are needed because of complex types
    // with non-trivial default constructors and destructors.
    Snapshot();
    ~Snapshot();

    // The name of the thread as set when it was created. The name may be
    // truncated due to internal length limitations.
    std::string thread_name;

    // The timestamp at which this process was created.
    int64_t create_stamp;

    // The process and thread IDs. These values have no meaning other than
    // they uniquely identify a running process and a running thread within
    // that process.  Thread-IDs can be re-used across different processes
    // and both can be re-used after the process/thread exits.
    int64_t process_id = 0;
    int64_t thread_id = 0;

    // The current stack of activities that are underway for this thread. It
    // is limited in its maximum size with later entries being left off.
    std::vector<Activity> activity_stack;

    // The current total depth of the activity stack, including those later
    // entries not recorded in the |activity_stack| vector.
    uint32_t activity_stack_depth = 0;

    // The last recorded "exception" activity.
    Activity last_exception;
  };

  // This is the base class for having the compiler manage an activity on the
  // tracker's stack. It does nothing but call methods on the passed |tracker|
  // if it is not null, making it safe (and cheap) to create these objects
  // even if activity tracking is not enabled.
  class BASE_EXPORT ScopedActivity {
   public:
    ScopedActivity(ThreadActivityTracker* tracker,
                   const void* program_counter,
                   const void* origin,
                   Activity::Type type,
                   const ActivityData& data);
    ~ScopedActivity();

    // Indicates if this activity is actually being recorded. It may not be if
    // (a) activity tracking is not enabled globally or
    // (b) there was insufficient stack space to hold it.
    bool IsRecorded();

    // Changes some basic metadata about the activity.
    void ChangeTypeAndData(Activity::Type type, const ActivityData& data);

   protected:
    // The thread tracker to which this object reports. It can be null if
    // activity tracking is not (yet) enabled.
    ThreadActivityTracker* const tracker_;

    // An identifier that indicates a specific activity on the stack.
    ActivityId activity_id_;

   private:
    DISALLOW_COPY_AND_ASSIGN(ScopedActivity);
  };

  // A ThreadActivityTracker runs on top of memory that is managed externally.
  // It must be large enough for the internal header and a few Activity
  // blocks. See SizeForStackDepth().
  ThreadActivityTracker(void* base, size_t size);
  virtual ~ThreadActivityTracker();

  // Indicates that an activity has started from a given |origin| address in
  // the code, though it can be null if the creator's address is not known.
  // The |type| and |data| describe the activity. |program_counter| should be
  // the result of GetProgramCounter() where push is called. Returned is an
  // ID that can be used to adjust the pushed activity.
  ActivityId PushActivity(const void* program_counter,
                          const void* origin,
                          Activity::Type type,
                          const ActivityData& data);

  // An inlined version of the above that gets the program counter where it
  // is called.
  ALWAYS_INLINE
  ActivityId PushActivity(const void* origin,
                          Activity::Type type,
                          const ActivityData& data) {
    return PushActivity(GetProgramCounter(), origin, type, data);
  }

  // Changes the activity |type| and |data| of the top-most entry on the stack.
  // This is useful if the information has changed and it is desireable to
  // track that change without creating a new stack entry. If the type is
  // ACT_NULL or the data is kNullActivityData then that value will remain
  // unchanged. The type, if changed, must remain in the same category.
  // Changing both is not atomic so a snapshot operation could occur between
  // the update of |type| and |data| or between update of |data| fields.
  void ChangeActivity(ActivityId id,
                      Activity::Type type,
                      const ActivityData& data);

  // Indicates that an activity has completed.
  void PopActivity(ActivityId id);

  // Indicates if an activity is actually being recorded.
  bool IsRecorded(ActivityId id);

  // Sets the user-data information for an activity.
  std::unique_ptr<ActivityUserData> GetUserData(
      ActivityId id,
      ActivityTrackerMemoryAllocator* allocator);

  // Returns if there is true use-data associated with a given ActivityId since
  // it's possible than any returned object is just a sink.
  bool HasUserData(ActivityId id);

  // Release the user-data information for an activity.
  void ReleaseUserData(ActivityId id,
                       ActivityTrackerMemoryAllocator* allocator);

  // Save an exception. |origin| is the location of the exception.
  void RecordExceptionActivity(const void* program_counter,
                               const void* origin,
                               Activity::Type type,
                               const ActivityData& data);

  // Returns whether the current data is valid or not. It is not valid if
  // corruption has been detected in the header or other data structures.
  bool IsValid() const;

  // Gets a copy of the tracker contents for analysis. Returns false if a
  // snapshot was not possible, perhaps because the data is not valid; the
  // contents of |output_snapshot| are undefined in that case. The current
  // implementation does not support concurrent snapshot operations.
  bool CreateSnapshot(Snapshot* output_snapshot) const;

  // Gets the base memory address used for storing data.
  const void* GetBaseAddress();

  // Access the "data version" value so tests can determine if an activity
  // was pushed and popped in a single call.
  uint32_t GetDataVersionForTesting();

  // Explicitly sets the process ID.
  void SetOwningProcessIdForTesting(int64_t pid, int64_t stamp);

  // Gets the associated process ID, in native form, and the creation timestamp
  // from tracker memory without loading the entire structure for analysis. This
  // will return false if no valid process ID is available.
  static bool GetOwningProcessId(const void* memory,
                                 int64_t* out_id,
                                 int64_t* out_stamp);

  // Calculates the memory size required for a given stack depth, including
  // the internal header structure for the stack.
  static size_t SizeForStackDepth(int stack_depth);

 private:
  friend class ActivityTrackerTest;

  bool CalledOnValidThread();

  std::unique_ptr<ActivityUserData> CreateUserDataForActivity(
      Activity* activity,
      ActivityTrackerMemoryAllocator* allocator);

  Header* const header_;        // Pointer to the Header structure.
  Activity* const stack_;       // The stack of activities.

#if DCHECK_IS_ON()
  // The ActivityTracker is thread bound, and will be invoked across all the
  // sequences that run on the thread. A ThreadChecker does not work here, as it
  // asserts on running in the same sequence each time.
  const PlatformThreadRef thread_id_;  // The thread this instance is bound to.
#endif
  const uint32_t stack_slots_;  // The total number of stack slots.

  bool valid_ = false;          // Tracks whether the data is valid or not.

  DISALLOW_COPY_AND_ASSIGN(ThreadActivityTracker);
};


// The global tracker manages all the individual thread trackers. Memory for
// the thread trackers is taken from a PersistentMemoryAllocator which allows
// for the data to be analyzed by a parallel process or even post-mortem.
class BASE_EXPORT GlobalActivityTracker {
 public:
  // Type identifiers used when storing in persistent memory so they can be
  // identified during extraction; the first 4 bytes of the SHA1 of the name
  // is used as a unique integer. A "version number" is added to the base
  // so that, if the structure of that object changes, stored older versions
  // will be safely ignored. These are public so that an external process
  // can recognize records of this type within an allocator.
  enum : uint32_t {
    kTypeIdActivityTracker = 0x5D7381AF + 4,   // SHA1(ActivityTracker) v4
    kTypeIdUserDataRecord = 0x615EDDD7 + 3,    // SHA1(UserDataRecord) v3
    kTypeIdGlobalLogMessage = 0x4CF434F9 + 1,  // SHA1(GlobalLogMessage) v1
    kTypeIdProcessDataRecord = kTypeIdUserDataRecord + 0x100,

    kTypeIdActivityTrackerFree = ~kTypeIdActivityTracker,
    kTypeIdUserDataRecordFree = ~kTypeIdUserDataRecord,
    kTypeIdProcessDataRecordFree = ~kTypeIdProcessDataRecord,
  };

  // An enumeration of common process life stages. All entries are given an
  // explicit number so they are known and remain constant; this allows for
  // cross-version analysis either locally or on a server.
  enum ProcessPhase : int {
    // The phases are generic and may have meaning to the tracker.
    PROCESS_PHASE_UNKNOWN = 0,
    PROCESS_LAUNCHED = 1,
    PROCESS_LAUNCH_FAILED = 2,
    PROCESS_EXITED_CLEANLY = 10,
    PROCESS_EXITED_WITH_CODE = 11,

    // Add here whatever is useful for analysis.
    PROCESS_SHUTDOWN_STARTED = 100,
    PROCESS_MAIN_LOOP_STARTED = 101,
  };

  // A callback made when a process exits to allow immediate analysis of its
  // data. Note that the system may reuse the |process_id| so when fetching
  // records it's important to ensure that what is returned was created before
  // the |exit_stamp|. Movement of |process_data| information is allowed.
  using ProcessExitCallback =
      RepeatingCallback<void(int64_t process_id,
                             int64_t exit_stamp,
                             int exit_code,
                             ProcessPhase exit_phase,
                             std::string&& command_line,
                             ActivityUserData::Snapshot&& process_data)>;

  // This structure contains information about a loaded module, as shown to
  // users of the tracker.
  struct BASE_EXPORT ModuleInfo {
    ModuleInfo();
    ModuleInfo(ModuleInfo&& rhs);
    ModuleInfo(const ModuleInfo& rhs);
    ~ModuleInfo();

    ModuleInfo& operator=(ModuleInfo&& rhs);
    ModuleInfo& operator=(const ModuleInfo& rhs);

    // Information about where and when the module was loaded/unloaded.
    bool is_loaded = false;  // Was the last operation a load or unload?
    uintptr_t address = 0;   // Address of the last load operation.
    int64_t load_time = 0;   // Time of last change; set automatically.

    // Information about the module itself. These never change no matter how
    // many times a module may be loaded and unloaded.
    size_t size = 0;         // The size of the loaded module.
    uint32_t timestamp = 0;  // Opaque "timestamp" for the module.
    uint32_t age = 0;        // Opaque "age" for the module.
    uint8_t identifier[16];  // Opaque identifier (GUID, etc.) for the module.
    std::string file;        // The full path to the file. (UTF-8)
    std::string debug_file;  // The full path to the debug file.
  };

  // This is a thin wrapper around the thread-tracker's ScopedActivity that
  // allows thread-safe access to data values. It is safe to use even if
  // activity tracking is not enabled.
  class BASE_EXPORT ScopedThreadActivity
      : public ThreadActivityTracker::ScopedActivity {
   public:
    ScopedThreadActivity(const void* program_counter,
                         const void* origin,
                         Activity::Type type,
                         const ActivityData& data,
                         bool lock_allowed);
    ~ScopedThreadActivity();

    // Returns an object for manipulating user data.
    ActivityUserData& user_data();

   private:
    // Gets (or creates) a tracker for the current thread. If locking is not
    // allowed (because a lock is being tracked which would cause recursion)
    // then the attempt to create one if none found will be skipped. Once
    // the tracker for this thread has been created for other reasons, locks
    // will be tracked. The thread-tracker uses locks.
    static ThreadActivityTracker* GetOrCreateTracker(bool lock_allowed) {
      GlobalActivityTracker* global_tracker = Get();
      if (!global_tracker)
        return nullptr;

      if (lock_allowed)
        return global_tracker->GetOrCreateTrackerForCurrentThread();
      else
        return global_tracker->GetTrackerForCurrentThread();
    }

    // An object that manages additional user data, created only upon request.
    std::unique_ptr<ActivityUserData> user_data_;

    DISALLOW_COPY_AND_ASSIGN(ScopedThreadActivity);
  };

  ~GlobalActivityTracker();

  // Creates a global tracker using a given persistent-memory |allocator| and
  // providing the given |stack_depth| to each thread tracker it manages. The
  // created object is activated so tracking will begin immediately upon return.
  // The |process_id| can be zero to get it from the OS but is taken for testing
  // purposes.
  static void CreateWithAllocator(
      std::unique_ptr<PersistentMemoryAllocator> allocator,
      int stack_depth,
      int64_t process_id);

#if !defined(OS_NACL)
  // Like above but internally creates an allocator around a disk file with
  // the specified |size| at the given |file_path|. Any existing file will be
  // overwritten. The |id| and |name| are arbitrary and stored in the allocator
  // for reference by whatever process reads it. Returns true if successful.
  static bool CreateWithFile(const FilePath& file_path,
                             size_t size,
                             uint64_t id,
                             StringPiece name,
                             int stack_depth);
#endif  // !defined(OS_NACL)

  // Like above but internally creates an allocator using local heap memory of
  // the specified size. This is used primarily for unit tests. The |process_id|
  // can be zero to get it from the OS but is taken for testing purposes.
  static bool CreateWithLocalMemory(size_t size,
                                    uint64_t id,
                                    StringPiece name,
                                    int stack_depth,
                                    int64_t process_id);

  // Like above but internally creates an allocator using a shared-memory
  // segment that is already mapped into the local memory space.
  static bool CreateWithSharedMemory(base::WritableSharedMemoryMapping mapping,
                                     uint64_t id,
                                     StringPiece name,
                                     int stack_depth);

  // Gets the global activity-tracker or null if none exists.
  static GlobalActivityTracker* Get() {
    return reinterpret_cast<GlobalActivityTracker*>(
        subtle::Acquire_Load(&g_tracker_));
  }

  // Sets the global activity-tracker for testing purposes.
  static void SetForTesting(std::unique_ptr<GlobalActivityTracker> tracker);

  // This access to the persistent allocator is only for testing; it extracts
  // the global tracker completely. All tracked threads must exit before
  // calling this. Tracking for the current thread will be automatically
  // stopped.
  static std::unique_ptr<GlobalActivityTracker> ReleaseForTesting();

  // Convenience method for determining if a global tracker is active.
  static bool IsEnabled() { return Get() != nullptr; }

  // Gets the persistent-memory-allocator in which data is stored. Callers
  // can store additional records here to pass more information to the
  // analysis process.
  PersistentMemoryAllocator* allocator() { return allocator_.get(); }

  // Gets the thread's activity-tracker if it exists. This is inline for
  // performance reasons and it uses thread-local-storage (TLS) so that there
  // is no significant lookup time required to find the one for the calling
  // thread. Ownership remains with the global tracker.
  ThreadActivityTracker* GetTrackerForCurrentThread() {
    // It is not safe to use TLS once TLS has been destroyed.
    if (base::ThreadLocalStorage::HasBeenDestroyed())
      return nullptr;

    return this_thread_tracker_.Get();
  }

  // Gets the thread's activity-tracker or creates one if none exists. This
  // is inline for performance reasons. Ownership remains with the global
  // tracker.
  ThreadActivityTracker* GetOrCreateTrackerForCurrentThread() {
    ThreadActivityTracker* tracker = GetTrackerForCurrentThread();
    if (tracker)
      return tracker;
    return CreateTrackerForCurrentThread();
  }

  // Creates an activity-tracker for the current thread.
  ThreadActivityTracker* CreateTrackerForCurrentThread();

  // Releases the activity-tracker for the current thread (for testing only).
  void ReleaseTrackerForCurrentThreadForTesting();

  // Sets a task-runner that can be used for background work.
  void SetBackgroundTaskRunner(
      const scoped_refptr<SequencedTaskRunner>& runner);

  // Sets an optional callback to be called when a process exits.
  void SetProcessExitCallback(ProcessExitCallback callback);

  // Manages process lifetimes. These are called by the process that launched
  // and reaped the subprocess, not the subprocess itself. If it is expensive
  // to generate the parameters, Get() the global tracker and call these
  // conditionally rather than using the static versions.
  void RecordProcessLaunch(ProcessId process_id,
                           const FilePath::StringType& cmd);
  void RecordProcessLaunch(ProcessId process_id,
                           const FilePath::StringType& exe,
                           const FilePath::StringType& args);
  void RecordProcessExit(ProcessId process_id, int exit_code);
  static void RecordProcessLaunchIfEnabled(ProcessId process_id,
                                           const FilePath::StringType& cmd) {
    GlobalActivityTracker* tracker = Get();
    if (tracker)
      tracker->RecordProcessLaunch(process_id, cmd);
  }
  static void RecordProcessLaunchIfEnabled(ProcessId process_id,
                                           const FilePath::StringType& exe,
                                           const FilePath::StringType& args) {
    GlobalActivityTracker* tracker = Get();
    if (tracker)
      tracker->RecordProcessLaunch(process_id, exe, args);
  }
  static void RecordProcessExitIfEnabled(ProcessId process_id, int exit_code) {
    GlobalActivityTracker* tracker = Get();
    if (tracker)
      tracker->RecordProcessExit(process_id, exit_code);
  }

  // Sets the "phase" of the current process, useful for knowing what it was
  // doing when it last reported.
  void SetProcessPhase(ProcessPhase phase);
  static void SetProcessPhaseIfEnabled(ProcessPhase phase) {
    GlobalActivityTracker* tracker = Get();
    if (tracker)
      tracker->SetProcessPhase(phase);
  }

  // Records a log message. The current implementation does NOT recycle these
  // only store critical messages such as FATAL ones.
  void RecordLogMessage(StringPiece message);
  static void RecordLogMessageIfEnabled(StringPiece message) {
    GlobalActivityTracker* tracker = Get();
    if (tracker)
      tracker->RecordLogMessage(message);
  }

  // Records a module load/unload event. This is safe to call multiple times
  // even with the same information.
  void RecordModuleInfo(const ModuleInfo& info);
  static void RecordModuleInfoIfEnabled(const ModuleInfo& info) {
    GlobalActivityTracker* tracker = Get();
    if (tracker)
      tracker->RecordModuleInfo(info);
  }

  // Record exception information for the current thread.
  ALWAYS_INLINE
  void RecordException(const void* origin, uint32_t code) {
    return RecordExceptionImpl(GetProgramCounter(), origin, code);
  }
  void RecordException(const void* pc, const void* origin, uint32_t code);

  // Marks the tracked data as deleted.
  void MarkDeleted();

  // Gets the process ID used for tracking. This is typically the same as what
  // the OS thinks is the current process but can be overridden for testing.
  int64_t process_id() { return process_id_; }

  // Accesses the process data record for storing arbitrary key/value pairs.
  // Updates to this are thread-safe.
  ActivityUserData& process_data() { return process_data_; }

 private:
  friend class GlobalActivityAnalyzer;
  friend class ScopedThreadActivity;
  friend class ActivityTrackerTest;

  enum : int {
    // The maximum number of threads that can be tracked within a process. If
    // more than this number run concurrently, tracking of new ones may cease.
    kMaxThreadCount = 100,
    kCachedThreadMemories = 10,
    kCachedUserDataMemories = 10,
  };

  // A wrapper around ActivityUserData that is thread-safe and thus can be used
  // in the global scope without the requirement of being called from only one
  // thread.
  class ThreadSafeUserData : public ActivityUserData {
   public:
    ThreadSafeUserData(void* memory, size_t size, int64_t pid = 0);
    ~ThreadSafeUserData() override;

   private:
    void* Set(StringPiece name,
              ValueType type,
              const void* memory,
              size_t size) override;

    Lock data_lock_;

    DISALLOW_COPY_AND_ASSIGN(ThreadSafeUserData);
  };

  // State of a module as stored in persistent memory. This supports a single
  // loading of a module only. If modules are loaded multiple times at
  // different addresses, only the last will be recorded and an unload will
  // not revert to the information of any other addresses.
  struct BASE_EXPORT ModuleInfoRecord {
    // SHA1(ModuleInfoRecord): Increment this if structure changes!
    static constexpr uint32_t kPersistentTypeId = 0x05DB5F41 + 1;

    // Expected size for 32/64-bit check by PersistentMemoryAllocator.
    static constexpr size_t kExpectedInstanceSize =
        OwningProcess::kExpectedInstanceSize + 56;

    // The atomic unfortunately makes this a "complex" class on some compilers
    // and thus requires an out-of-line constructor & destructor even though
    // they do nothing.
    ModuleInfoRecord();
    ~ModuleInfoRecord();

    OwningProcess owner;            // The process that created this record.
    uint64_t address;               // The base address of the module.
    uint64_t load_time;             // Time of last load/unload.
    uint64_t size;                  // The size of the module in bytes.
    uint32_t timestamp;             // Opaque timestamp of the module.
    uint32_t age;                   // Opaque "age" associated with the module.
    uint8_t identifier[16];         // Opaque identifier for the module.
    std::atomic<uint32_t> changes;  // Number load/unload actions.
    uint16_t pickle_size;           // The size of the following pickle.
    uint8_t loaded;                 // Flag if module is loaded or not.
    char pickle[1];                 // Other strings; may allocate larger.

    // Decodes/encodes storage structure from more generic info structure.
    bool DecodeTo(GlobalActivityTracker::ModuleInfo* info,
                  size_t record_size) const;
    static ModuleInfoRecord* CreateFrom(
        const GlobalActivityTracker::ModuleInfo& info,
        PersistentMemoryAllocator* allocator);

    // Updates the core information without changing the encoded strings. This
    // is useful when a known module changes state (i.e. new load or unload).
    bool UpdateFrom(const GlobalActivityTracker::ModuleInfo& info);

   private:
    DISALLOW_COPY_AND_ASSIGN(ModuleInfoRecord);
  };

  // A thin wrapper around the main thread-tracker that keeps additional
  // information that the global tracker needs to handle joined threads.
  class ManagedActivityTracker : public ThreadActivityTracker {
   public:
    ManagedActivityTracker(PersistentMemoryAllocator::Reference mem_reference,
                           void* base,
                           size_t size);
    ~ManagedActivityTracker() override;

    // The reference into persistent memory from which the thread-tracker's
    // memory was created.
    const PersistentMemoryAllocator::Reference mem_reference_;

    // The physical address used for the thread-tracker's memory.
    void* const mem_base_;

   private:
    DISALLOW_COPY_AND_ASSIGN(ManagedActivityTracker);
  };

  // Creates a global tracker using a given persistent-memory |allocator| and
  // providing the given |stack_depth| to each thread tracker it manages. The
  // created object is activated so tracking has already started upon return.
  // The |process_id| can be zero to get it from the OS but is taken for testing
  // purposes.
  GlobalActivityTracker(std::unique_ptr<PersistentMemoryAllocator> allocator,
                        int stack_depth,
                        int64_t process_id);

  // Returns the memory used by an activity-tracker managed by this class.
  // It is called during the destruction of a ManagedActivityTracker object.
  void ReturnTrackerMemory(ManagedActivityTracker* tracker);

  // Records exception information.
  void RecordExceptionImpl(const void* pc, const void* origin, uint32_t code);

  // Releases the activity-tracker associcated with thread. It is called
  // automatically when a thread is joined and thus there is nothing more to
  // be tracked. |value| is a pointer to a ManagedActivityTracker.
  static void OnTLSDestroy(void* value);

  // Does process-exit work. This can be run on any thread.
  void CleanupAfterProcess(int64_t process_id,
                           int64_t exit_stamp,
                           int exit_code,
                           std::string&& command_line);

  // The persistent-memory allocator from which the memory for all trackers
  // is taken.
  std::unique_ptr<PersistentMemoryAllocator> allocator_;

  // The size (in bytes) of memory required by a ThreadActivityTracker to
  // provide the stack-depth requested during construction.
  const size_t stack_memory_size_;

  // The process-id of the current process. This is kept as a member variable,
  // defined during initialization, for testing purposes.
  const int64_t process_id_;

  // The activity tracker for the currently executing thread.
  ThreadLocalOwnedPointer<ThreadActivityTracker> this_thread_tracker_;

  // The number of thread trackers currently active.
  std::atomic<int> thread_tracker_count_;

  // A caching memory allocator for thread-tracker objects.
  ActivityTrackerMemoryAllocator thread_tracker_allocator_;
  Lock thread_tracker_allocator_lock_;

  // A caching memory allocator for user data attached to activity data.
  ActivityTrackerMemoryAllocator user_data_allocator_;
  Lock user_data_allocator_lock_;

  // An object for holding arbitrary key value pairs with thread-safe access.
  ThreadSafeUserData process_data_;

  // A map of global module information, keyed by module path.
  std::map<const std::string, ModuleInfoRecord*> modules_;
  Lock modules_lock_;

  // The active global activity tracker.
  static subtle::AtomicWord g_tracker_;

  // A lock that is used to protect access to the following fields.
  Lock global_tracker_lock_;

  // The collection of processes being tracked and their command-lines.
  std::map<int64_t, std::string> known_processes_;

  // A task-runner that can be used for doing background processing.
  scoped_refptr<SequencedTaskRunner> background_task_runner_;

  // A callback performed when a subprocess exits, including its exit-code
  // and the phase it was in when that occurred. This will be called via
  // the |background_task_runner_| if one is set or whatever thread reaped
  // the process otherwise.
  ProcessExitCallback process_exit_callback_;

  DISALLOW_COPY_AND_ASSIGN(GlobalActivityTracker);
};


// Record entry in to and out of an arbitrary block of code.
class BASE_EXPORT ScopedActivity
    : public GlobalActivityTracker::ScopedThreadActivity {
 public:
  // Track activity at the specified FROM_HERE location for an arbitrary
  // 4-bit |action|, an arbitrary 32-bit |id|, and 32-bits of arbitrary
  // |info|. None of these values affect operation; they're all purely
  // for association and analysis. To have unique identifiers across a
  // diverse code-base, create the number by taking the first 8 characters
  // of the hash of the activity being tracked.
  //
  // For example:
  //   Tracking method: void MayNeverExit(uint32_t foo) {...}
  //   echo -n "MayNeverExit" | sha1sum   =>   e44873ccab21e2b71270da24aa1...
  //
  //   void MayNeverExit(int32_t foo) {
  //     base::debug::ScopedActivity track_me(0, 0xE44873CC, foo);
  //     ...
  //   }
  ALWAYS_INLINE
  ScopedActivity(uint8_t action, uint32_t id, int32_t info)
      : ScopedActivity(GetProgramCounter(), action, id, info) {}
  ScopedActivity(Location from_here, uint8_t action, uint32_t id, int32_t info)
      : ScopedActivity(from_here.program_counter(), action, id, info) {}
  ScopedActivity() : ScopedActivity(0, 0, 0) {}

  // Changes the |action| and/or |info| of this activity on the stack. This
  // is useful for tracking progress through a function, updating the action
  // to indicate "milestones" in the block (max 16 milestones: 0-15) or the
  // info to reflect other changes. Changing both is not atomic so a snapshot
  // operation could occur between the update of |action| and |info|.
  void ChangeAction(uint8_t action);
  void ChangeInfo(int32_t info);
  void ChangeActionAndInfo(uint8_t action, int32_t info);

 private:
  // Constructs the object using a passed-in program-counter.
  ScopedActivity(const void* program_counter,
                 uint8_t action,
                 uint32_t id,
                 int32_t info);

  // A copy of the ID code so it doesn't have to be passed by the caller when
  // changing the |info| field.
  uint32_t id_;

  DISALLOW_COPY_AND_ASSIGN(ScopedActivity);
};


// These "scoped" classes provide easy tracking of various blocking actions.

class BASE_EXPORT ScopedTaskRunActivity
    : public GlobalActivityTracker::ScopedThreadActivity {
 public:
  ALWAYS_INLINE
  explicit ScopedTaskRunActivity(const PendingTask& task)
      : ScopedTaskRunActivity(GetProgramCounter(), task) {}

 private:
  ScopedTaskRunActivity(const void* program_counter, const PendingTask& task);
  DISALLOW_COPY_AND_ASSIGN(ScopedTaskRunActivity);
};

class BASE_EXPORT ScopedLockAcquireActivity
    : public GlobalActivityTracker::ScopedThreadActivity {
 public:
  ALWAYS_INLINE
  explicit ScopedLockAcquireActivity(const base::internal::LockImpl* lock)
      : ScopedLockAcquireActivity(GetProgramCounter(), lock) {}

 private:
  ScopedLockAcquireActivity(const void* program_counter,
                            const base::internal::LockImpl* lock);
  DISALLOW_COPY_AND_ASSIGN(ScopedLockAcquireActivity);
};

class BASE_EXPORT ScopedEventWaitActivity
    : public GlobalActivityTracker::ScopedThreadActivity {
 public:
  ALWAYS_INLINE
  explicit ScopedEventWaitActivity(const WaitableEvent* event)
      : ScopedEventWaitActivity(GetProgramCounter(), event) {}

 private:
  ScopedEventWaitActivity(const void* program_counter,
                          const WaitableEvent* event);
  DISALLOW_COPY_AND_ASSIGN(ScopedEventWaitActivity);
};

class BASE_EXPORT ScopedThreadJoinActivity
    : public GlobalActivityTracker::ScopedThreadActivity {
 public:
  ALWAYS_INLINE
  explicit ScopedThreadJoinActivity(const PlatformThreadHandle* thread)
      : ScopedThreadJoinActivity(GetProgramCounter(), thread) {}

 private:
  ScopedThreadJoinActivity(const void* program_counter,
                           const PlatformThreadHandle* thread);
  DISALLOW_COPY_AND_ASSIGN(ScopedThreadJoinActivity);
};

// Some systems don't have base::Process
#if !defined(OS_NACL) && !defined(OS_IOS)
class BASE_EXPORT ScopedProcessWaitActivity
    : public GlobalActivityTracker::ScopedThreadActivity {
 public:
  ALWAYS_INLINE
  explicit ScopedProcessWaitActivity(const Process* process)
      : ScopedProcessWaitActivity(GetProgramCounter(), process) {}

 private:
  ScopedProcessWaitActivity(const void* program_counter,
                            const Process* process);
  DISALLOW_COPY_AND_ASSIGN(ScopedProcessWaitActivity);
};
#endif

}  // namespace debug
}  // namespace base

#endif  // BASE_DEBUG_ACTIVITY_TRACKER_H_
