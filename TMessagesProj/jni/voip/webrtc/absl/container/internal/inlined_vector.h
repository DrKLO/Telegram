// Copyright 2019 The Abseil Authors.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#ifndef ABSL_CONTAINER_INTERNAL_INLINED_VECTOR_INTERNAL_H_
#define ABSL_CONTAINER_INTERNAL_INLINED_VECTOR_INTERNAL_H_

#include <algorithm>
#include <cstddef>
#include <cstring>
#include <iterator>
#include <limits>
#include <memory>
#include <utility>

#include "absl/base/macros.h"
#include "absl/container/internal/compressed_tuple.h"
#include "absl/memory/memory.h"
#include "absl/meta/type_traits.h"
#include "absl/types/span.h"

namespace absl {
ABSL_NAMESPACE_BEGIN
namespace inlined_vector_internal {

template <typename Iterator>
using IsAtLeastForwardIterator = std::is_convertible<
    typename std::iterator_traits<Iterator>::iterator_category,
    std::forward_iterator_tag>;

template <typename AllocatorType,
          typename ValueType =
              typename absl::allocator_traits<AllocatorType>::value_type>
using IsMemcpyOk =
    absl::conjunction<std::is_same<AllocatorType, std::allocator<ValueType>>,
                      absl::is_trivially_copy_constructible<ValueType>,
                      absl::is_trivially_copy_assignable<ValueType>,
                      absl::is_trivially_destructible<ValueType>>;

template <typename AllocatorType, typename Pointer, typename SizeType>
void DestroyElements(AllocatorType* alloc_ptr, Pointer destroy_first,
                     SizeType destroy_size) {
  using AllocatorTraits = absl::allocator_traits<AllocatorType>;

  if (destroy_first != nullptr) {
    for (auto i = destroy_size; i != 0;) {
      --i;
      AllocatorTraits::destroy(*alloc_ptr, destroy_first + i);
    }

#if !defined(NDEBUG)
    {
      using ValueType = typename AllocatorTraits::value_type;

      // Overwrite unused memory with `0xab` so we can catch uninitialized
      // usage.
      //
      // Cast to `void*` to tell the compiler that we don't care that we might
      // be scribbling on a vtable pointer.
      void* memory_ptr = destroy_first;
      auto memory_size = destroy_size * sizeof(ValueType);
      std::memset(memory_ptr, 0xab, memory_size);
    }
#endif  // !defined(NDEBUG)
  }
}

template <typename AllocatorType, typename Pointer, typename ValueAdapter,
          typename SizeType>
void ConstructElements(AllocatorType* alloc_ptr, Pointer construct_first,
                       ValueAdapter* values_ptr, SizeType construct_size) {
  for (SizeType i = 0; i < construct_size; ++i) {
    ABSL_INTERNAL_TRY {
      values_ptr->ConstructNext(alloc_ptr, construct_first + i);
    }
    ABSL_INTERNAL_CATCH_ANY {
      inlined_vector_internal::DestroyElements(alloc_ptr, construct_first, i);
      ABSL_INTERNAL_RETHROW;
    }
  }
}

template <typename Pointer, typename ValueAdapter, typename SizeType>
void AssignElements(Pointer assign_first, ValueAdapter* values_ptr,
                    SizeType assign_size) {
  for (SizeType i = 0; i < assign_size; ++i) {
    values_ptr->AssignNext(assign_first + i);
  }
}

template <typename AllocatorType>
struct StorageView {
  using AllocatorTraits = absl::allocator_traits<AllocatorType>;
  using Pointer = typename AllocatorTraits::pointer;
  using SizeType = typename AllocatorTraits::size_type;

  Pointer data;
  SizeType size;
  SizeType capacity;
};

template <typename AllocatorType, typename Iterator>
class IteratorValueAdapter {
  using AllocatorTraits = absl::allocator_traits<AllocatorType>;
  using Pointer = typename AllocatorTraits::pointer;

 public:
  explicit IteratorValueAdapter(const Iterator& it) : it_(it) {}

  void ConstructNext(AllocatorType* alloc_ptr, Pointer construct_at) {
    AllocatorTraits::construct(*alloc_ptr, construct_at, *it_);
    ++it_;
  }

  void AssignNext(Pointer assign_at) {
    *assign_at = *it_;
    ++it_;
  }

 private:
  Iterator it_;
};

template <typename AllocatorType>
class CopyValueAdapter {
  using AllocatorTraits = absl::allocator_traits<AllocatorType>;
  using ValueType = typename AllocatorTraits::value_type;
  using Pointer = typename AllocatorTraits::pointer;
  using ConstPointer = typename AllocatorTraits::const_pointer;

 public:
  explicit CopyValueAdapter(const ValueType& v) : ptr_(std::addressof(v)) {}

  void ConstructNext(AllocatorType* alloc_ptr, Pointer construct_at) {
    AllocatorTraits::construct(*alloc_ptr, construct_at, *ptr_);
  }

  void AssignNext(Pointer assign_at) { *assign_at = *ptr_; }

 private:
  ConstPointer ptr_;
};

template <typename AllocatorType>
class DefaultValueAdapter {
  using AllocatorTraits = absl::allocator_traits<AllocatorType>;
  using ValueType = typename AllocatorTraits::value_type;
  using Pointer = typename AllocatorTraits::pointer;

 public:
  explicit DefaultValueAdapter() {}

  void ConstructNext(AllocatorType* alloc_ptr, Pointer construct_at) {
    AllocatorTraits::construct(*alloc_ptr, construct_at);
  }

  void AssignNext(Pointer assign_at) { *assign_at = ValueType(); }
};

template <typename AllocatorType>
class AllocationTransaction {
  using AllocatorTraits = absl::allocator_traits<AllocatorType>;
  using Pointer = typename AllocatorTraits::pointer;
  using SizeType = typename AllocatorTraits::size_type;

 public:
  explicit AllocationTransaction(AllocatorType* alloc_ptr)
      : alloc_data_(*alloc_ptr, nullptr) {}

  ~AllocationTransaction() {
    if (DidAllocate()) {
      AllocatorTraits::deallocate(GetAllocator(), GetData(), GetCapacity());
    }
  }

  AllocationTransaction(const AllocationTransaction&) = delete;
  void operator=(const AllocationTransaction&) = delete;

  AllocatorType& GetAllocator() { return alloc_data_.template get<0>(); }
  Pointer& GetData() { return alloc_data_.template get<1>(); }
  SizeType& GetCapacity() { return capacity_; }

  bool DidAllocate() { return GetData() != nullptr; }
  Pointer Allocate(SizeType capacity) {
    GetData() = AllocatorTraits::allocate(GetAllocator(), capacity);
    GetCapacity() = capacity;
    return GetData();
  }

  void Reset() {
    GetData() = nullptr;
    GetCapacity() = 0;
  }

 private:
  container_internal::CompressedTuple<AllocatorType, Pointer> alloc_data_;
  SizeType capacity_ = 0;
};

template <typename AllocatorType>
class ConstructionTransaction {
  using AllocatorTraits = absl::allocator_traits<AllocatorType>;
  using Pointer = typename AllocatorTraits::pointer;
  using SizeType = typename AllocatorTraits::size_type;

 public:
  explicit ConstructionTransaction(AllocatorType* alloc_ptr)
      : alloc_data_(*alloc_ptr, nullptr) {}

  ~ConstructionTransaction() {
    if (DidConstruct()) {
      inlined_vector_internal::DestroyElements(std::addressof(GetAllocator()),
                                               GetData(), GetSize());
    }
  }

  ConstructionTransaction(const ConstructionTransaction&) = delete;
  void operator=(const ConstructionTransaction&) = delete;

  AllocatorType& GetAllocator() { return alloc_data_.template get<0>(); }
  Pointer& GetData() { return alloc_data_.template get<1>(); }
  SizeType& GetSize() { return size_; }

  bool DidConstruct() { return GetData() != nullptr; }
  template <typename ValueAdapter>
  void Construct(Pointer data, ValueAdapter* values_ptr, SizeType size) {
    inlined_vector_internal::ConstructElements(std::addressof(GetAllocator()),
                                               data, values_ptr, size);
    GetData() = data;
    GetSize() = size;
  }
  void Commit() {
    GetData() = nullptr;
    GetSize() = 0;
  }

 private:
  container_internal::CompressedTuple<AllocatorType, Pointer> alloc_data_;
  SizeType size_ = 0;
};

template <typename T, size_t N, typename A>
class Storage {
 public:
  using AllocatorTraits = absl::allocator_traits<A>;
  using allocator_type = typename AllocatorTraits::allocator_type;
  using value_type = typename AllocatorTraits::value_type;
  using pointer = typename AllocatorTraits::pointer;
  using const_pointer = typename AllocatorTraits::const_pointer;
  using size_type = typename AllocatorTraits::size_type;
  using difference_type = typename AllocatorTraits::difference_type;

  using reference = value_type&;
  using const_reference = const value_type&;
  using RValueReference = value_type&&;
  using iterator = pointer;
  using const_iterator = const_pointer;
  using reverse_iterator = std::reverse_iterator<iterator>;
  using const_reverse_iterator = std::reverse_iterator<const_iterator>;
  using MoveIterator = std::move_iterator<iterator>;
  using IsMemcpyOk = inlined_vector_internal::IsMemcpyOk<allocator_type>;

  using StorageView = inlined_vector_internal::StorageView<allocator_type>;

  template <typename Iterator>
  using IteratorValueAdapter =
      inlined_vector_internal::IteratorValueAdapter<allocator_type, Iterator>;
  using CopyValueAdapter =
      inlined_vector_internal::CopyValueAdapter<allocator_type>;
  using DefaultValueAdapter =
      inlined_vector_internal::DefaultValueAdapter<allocator_type>;

  using AllocationTransaction =
      inlined_vector_internal::AllocationTransaction<allocator_type>;
  using ConstructionTransaction =
      inlined_vector_internal::ConstructionTransaction<allocator_type>;

  static size_type NextCapacity(size_type current_capacity) {
    return current_capacity * 2;
  }

  static size_type ComputeCapacity(size_type current_capacity,
                                   size_type requested_capacity) {
    return (std::max)(NextCapacity(current_capacity), requested_capacity);
  }

  // ---------------------------------------------------------------------------
  // Storage Constructors and Destructor
  // ---------------------------------------------------------------------------

  Storage() : metadata_() {}

  explicit Storage(const allocator_type& alloc) : metadata_(alloc, {}) {}

  ~Storage() {
    pointer data = GetIsAllocated() ? GetAllocatedData() : GetInlinedData();
    inlined_vector_internal::DestroyElements(GetAllocPtr(), data, GetSize());
    DeallocateIfAllocated();
  }

  // ---------------------------------------------------------------------------
  // Storage Member Accessors
  // ---------------------------------------------------------------------------

  size_type& GetSizeAndIsAllocated() { return metadata_.template get<1>(); }

  const size_type& GetSizeAndIsAllocated() const {
    return metadata_.template get<1>();
  }

  size_type GetSize() const { return GetSizeAndIsAllocated() >> 1; }

  bool GetIsAllocated() const { return GetSizeAndIsAllocated() & 1; }

  pointer GetAllocatedData() { return data_.allocated.allocated_data; }

  const_pointer GetAllocatedData() const {
    return data_.allocated.allocated_data;
  }

  pointer GetInlinedData() {
    return reinterpret_cast<pointer>(
        std::addressof(data_.inlined.inlined_data[0]));
  }

  const_pointer GetInlinedData() const {
    return reinterpret_cast<const_pointer>(
        std::addressof(data_.inlined.inlined_data[0]));
  }

  size_type GetAllocatedCapacity() const {
    return data_.allocated.allocated_capacity;
  }

  size_type GetInlinedCapacity() const { return static_cast<size_type>(N); }

  StorageView MakeStorageView() {
    return GetIsAllocated()
               ? StorageView{GetAllocatedData(), GetSize(),
                             GetAllocatedCapacity()}
               : StorageView{GetInlinedData(), GetSize(), GetInlinedCapacity()};
  }

  allocator_type* GetAllocPtr() {
    return std::addressof(metadata_.template get<0>());
  }

  const allocator_type* GetAllocPtr() const {
    return std::addressof(metadata_.template get<0>());
  }

  // ---------------------------------------------------------------------------
  // Storage Member Mutators
  // ---------------------------------------------------------------------------

  template <typename ValueAdapter>
  void Initialize(ValueAdapter values, size_type new_size);

  template <typename ValueAdapter>
  void Assign(ValueAdapter values, size_type new_size);

  template <typename ValueAdapter>
  void Resize(ValueAdapter values, size_type new_size);

  template <typename ValueAdapter>
  iterator Insert(const_iterator pos, ValueAdapter values,
                  size_type insert_count);

  template <typename... Args>
  reference EmplaceBack(Args&&... args);

  iterator Erase(const_iterator from, const_iterator to);

  void Reserve(size_type requested_capacity);

  void ShrinkToFit();

  void Swap(Storage* other_storage_ptr);

  void SetIsAllocated() {
    GetSizeAndIsAllocated() |= static_cast<size_type>(1);
  }

  void UnsetIsAllocated() {
    GetSizeAndIsAllocated() &= ((std::numeric_limits<size_type>::max)() - 1);
  }

  void SetSize(size_type size) {
    GetSizeAndIsAllocated() =
        (size << 1) | static_cast<size_type>(GetIsAllocated());
  }

  void SetAllocatedSize(size_type size) {
    GetSizeAndIsAllocated() = (size << 1) | static_cast<size_type>(1);
  }

  void SetInlinedSize(size_type size) {
    GetSizeAndIsAllocated() = size << static_cast<size_type>(1);
  }

  void AddSize(size_type count) {
    GetSizeAndIsAllocated() += count << static_cast<size_type>(1);
  }

  void SubtractSize(size_type count) {
    assert(count <= GetSize());

    GetSizeAndIsAllocated() -= count << static_cast<size_type>(1);
  }

  void SetAllocatedData(pointer data, size_type capacity) {
    data_.allocated.allocated_data = data;
    data_.allocated.allocated_capacity = capacity;
  }

  void AcquireAllocatedData(AllocationTransaction* allocation_tx_ptr) {
    SetAllocatedData(allocation_tx_ptr->GetData(),
                     allocation_tx_ptr->GetCapacity());

    allocation_tx_ptr->Reset();
  }

  void MemcpyFrom(const Storage& other_storage) {
    assert(IsMemcpyOk::value || other_storage.GetIsAllocated());

    GetSizeAndIsAllocated() = other_storage.GetSizeAndIsAllocated();
    data_ = other_storage.data_;
  }

  void DeallocateIfAllocated() {
    if (GetIsAllocated()) {
      AllocatorTraits::deallocate(*GetAllocPtr(), GetAllocatedData(),
                                  GetAllocatedCapacity());
    }
  }

 private:
  using Metadata =
      container_internal::CompressedTuple<allocator_type, size_type>;

  struct Allocated {
    pointer allocated_data;
    size_type allocated_capacity;
  };

  struct Inlined {
    alignas(value_type) char inlined_data[sizeof(value_type[N])];
  };

  union Data {
    Allocated allocated;
    Inlined inlined;
  };

  Metadata metadata_;
  Data data_;
};

template <typename T, size_t N, typename A>
template <typename ValueAdapter>
auto Storage<T, N, A>::Initialize(ValueAdapter values, size_type new_size)
    -> void {
  // Only callable from constructors!
  assert(!GetIsAllocated());
  assert(GetSize() == 0);

  pointer construct_data;
  if (new_size > GetInlinedCapacity()) {
    // Because this is only called from the `InlinedVector` constructors, it's
    // safe to take on the allocation with size `0`. If `ConstructElements(...)`
    // throws, deallocation will be automatically handled by `~Storage()`.
    size_type new_capacity = ComputeCapacity(GetInlinedCapacity(), new_size);
    construct_data = AllocatorTraits::allocate(*GetAllocPtr(), new_capacity);
    SetAllocatedData(construct_data, new_capacity);
    SetIsAllocated();
  } else {
    construct_data = GetInlinedData();
  }

  inlined_vector_internal::ConstructElements(GetAllocPtr(), construct_data,
                                             &values, new_size);

  // Since the initial size was guaranteed to be `0` and the allocated bit is
  // already correct for either case, *adding* `new_size` gives us the correct
  // result faster than setting it directly.
  AddSize(new_size);
}

template <typename T, size_t N, typename A>
template <typename ValueAdapter>
auto Storage<T, N, A>::Assign(ValueAdapter values, size_type new_size) -> void {
  StorageView storage_view = MakeStorageView();

  AllocationTransaction allocation_tx(GetAllocPtr());

  absl::Span<value_type> assign_loop;
  absl::Span<value_type> construct_loop;
  absl::Span<value_type> destroy_loop;

  if (new_size > storage_view.capacity) {
    size_type new_capacity = ComputeCapacity(storage_view.capacity, new_size);
    construct_loop = {allocation_tx.Allocate(new_capacity), new_size};
    destroy_loop = {storage_view.data, storage_view.size};
  } else if (new_size > storage_view.size) {
    assign_loop = {storage_view.data, storage_view.size};
    construct_loop = {storage_view.data + storage_view.size,
                      new_size - storage_view.size};
  } else {
    assign_loop = {storage_view.data, new_size};
    destroy_loop = {storage_view.data + new_size, storage_view.size - new_size};
  }

  inlined_vector_internal::AssignElements(assign_loop.data(), &values,
                                          assign_loop.size());

  inlined_vector_internal::ConstructElements(
      GetAllocPtr(), construct_loop.data(), &values, construct_loop.size());

  inlined_vector_internal::DestroyElements(GetAllocPtr(), destroy_loop.data(),
                                           destroy_loop.size());

  if (allocation_tx.DidAllocate()) {
    DeallocateIfAllocated();
    AcquireAllocatedData(&allocation_tx);
    SetIsAllocated();
  }

  SetSize(new_size);
}

template <typename T, size_t N, typename A>
template <typename ValueAdapter>
auto Storage<T, N, A>::Resize(ValueAdapter values, size_type new_size) -> void {
  StorageView storage_view = MakeStorageView();

  IteratorValueAdapter<MoveIterator> move_values(
      MoveIterator(storage_view.data));

  AllocationTransaction allocation_tx(GetAllocPtr());
  ConstructionTransaction construction_tx(GetAllocPtr());

  absl::Span<value_type> construct_loop;
  absl::Span<value_type> move_construct_loop;
  absl::Span<value_type> destroy_loop;

  if (new_size > storage_view.capacity) {
    size_type new_capacity = ComputeCapacity(storage_view.capacity, new_size);
    pointer new_data = allocation_tx.Allocate(new_capacity);
    construct_loop = {new_data + storage_view.size,
                      new_size - storage_view.size};
    move_construct_loop = {new_data, storage_view.size};
    destroy_loop = {storage_view.data, storage_view.size};
  } else if (new_size > storage_view.size) {
    construct_loop = {storage_view.data + storage_view.size,
                      new_size - storage_view.size};
  } else {
    destroy_loop = {storage_view.data + new_size, storage_view.size - new_size};
  }

  construction_tx.Construct(construct_loop.data(), &values,
                            construct_loop.size());

  inlined_vector_internal::ConstructElements(
      GetAllocPtr(), move_construct_loop.data(), &move_values,
      move_construct_loop.size());

  inlined_vector_internal::DestroyElements(GetAllocPtr(), destroy_loop.data(),
                                           destroy_loop.size());

  construction_tx.Commit();
  if (allocation_tx.DidAllocate()) {
    DeallocateIfAllocated();
    AcquireAllocatedData(&allocation_tx);
    SetIsAllocated();
  }

  SetSize(new_size);
}

template <typename T, size_t N, typename A>
template <typename ValueAdapter>
auto Storage<T, N, A>::Insert(const_iterator pos, ValueAdapter values,
                              size_type insert_count) -> iterator {
  StorageView storage_view = MakeStorageView();

  size_type insert_index =
      std::distance(const_iterator(storage_view.data), pos);
  size_type insert_end_index = insert_index + insert_count;
  size_type new_size = storage_view.size + insert_count;

  if (new_size > storage_view.capacity) {
    AllocationTransaction allocation_tx(GetAllocPtr());
    ConstructionTransaction construction_tx(GetAllocPtr());
    ConstructionTransaction move_construciton_tx(GetAllocPtr());

    IteratorValueAdapter<MoveIterator> move_values(
        MoveIterator(storage_view.data));

    size_type new_capacity = ComputeCapacity(storage_view.capacity, new_size);
    pointer new_data = allocation_tx.Allocate(new_capacity);

    construction_tx.Construct(new_data + insert_index, &values, insert_count);

    move_construciton_tx.Construct(new_data, &move_values, insert_index);

    inlined_vector_internal::ConstructElements(
        GetAllocPtr(), new_data + insert_end_index, &move_values,
        storage_view.size - insert_index);

    inlined_vector_internal::DestroyElements(GetAllocPtr(), storage_view.data,
                                             storage_view.size);

    construction_tx.Commit();
    move_construciton_tx.Commit();
    DeallocateIfAllocated();
    AcquireAllocatedData(&allocation_tx);

    SetAllocatedSize(new_size);
    return iterator(new_data + insert_index);
  } else {
    size_type move_construction_destination_index =
        (std::max)(insert_end_index, storage_view.size);

    ConstructionTransaction move_construction_tx(GetAllocPtr());

    IteratorValueAdapter<MoveIterator> move_construction_values(
        MoveIterator(storage_view.data +
                     (move_construction_destination_index - insert_count)));
    absl::Span<value_type> move_construction = {
        storage_view.data + move_construction_destination_index,
        new_size - move_construction_destination_index};

    pointer move_assignment_values = storage_view.data + insert_index;
    absl::Span<value_type> move_assignment = {
        storage_view.data + insert_end_index,
        move_construction_destination_index - insert_end_index};

    absl::Span<value_type> insert_assignment = {move_assignment_values,
                                                move_construction.size()};

    absl::Span<value_type> insert_construction = {
        insert_assignment.data() + insert_assignment.size(),
        insert_count - insert_assignment.size()};

    move_construction_tx.Construct(move_construction.data(),
                                   &move_construction_values,
                                   move_construction.size());

    for (pointer destination = move_assignment.data() + move_assignment.size(),
                 last_destination = move_assignment.data(),
                 source = move_assignment_values + move_assignment.size();
         ;) {
      --destination;
      --source;
      if (destination < last_destination) break;
      *destination = std::move(*source);
    }

    inlined_vector_internal::AssignElements(insert_assignment.data(), &values,
                                            insert_assignment.size());

    inlined_vector_internal::ConstructElements(
        GetAllocPtr(), insert_construction.data(), &values,
        insert_construction.size());

    move_construction_tx.Commit();

    AddSize(insert_count);
    return iterator(storage_view.data + insert_index);
  }
}

template <typename T, size_t N, typename A>
template <typename... Args>
auto Storage<T, N, A>::EmplaceBack(Args&&... args) -> reference {
  StorageView storage_view = MakeStorageView();

  AllocationTransaction allocation_tx(GetAllocPtr());

  IteratorValueAdapter<MoveIterator> move_values(
      MoveIterator(storage_view.data));

  pointer construct_data;
  if (storage_view.size == storage_view.capacity) {
    size_type new_capacity = NextCapacity(storage_view.capacity);
    construct_data = allocation_tx.Allocate(new_capacity);
  } else {
    construct_data = storage_view.data;
  }

  pointer last_ptr = construct_data + storage_view.size;

  AllocatorTraits::construct(*GetAllocPtr(), last_ptr,
                             std::forward<Args>(args)...);

  if (allocation_tx.DidAllocate()) {
    ABSL_INTERNAL_TRY {
      inlined_vector_internal::ConstructElements(
          GetAllocPtr(), allocation_tx.GetData(), &move_values,
          storage_view.size);
    }
    ABSL_INTERNAL_CATCH_ANY {
      AllocatorTraits::destroy(*GetAllocPtr(), last_ptr);
      ABSL_INTERNAL_RETHROW;
    }

    inlined_vector_internal::DestroyElements(GetAllocPtr(), storage_view.data,
                                             storage_view.size);

    DeallocateIfAllocated();
    AcquireAllocatedData(&allocation_tx);
    SetIsAllocated();
  }

  AddSize(1);
  return *last_ptr;
}

template <typename T, size_t N, typename A>
auto Storage<T, N, A>::Erase(const_iterator from, const_iterator to)
    -> iterator {
  StorageView storage_view = MakeStorageView();

  size_type erase_size = std::distance(from, to);
  size_type erase_index =
      std::distance(const_iterator(storage_view.data), from);
  size_type erase_end_index = erase_index + erase_size;

  IteratorValueAdapter<MoveIterator> move_values(
      MoveIterator(storage_view.data + erase_end_index));

  inlined_vector_internal::AssignElements(storage_view.data + erase_index,
                                          &move_values,
                                          storage_view.size - erase_end_index);

  inlined_vector_internal::DestroyElements(
      GetAllocPtr(), storage_view.data + (storage_view.size - erase_size),
      erase_size);

  SubtractSize(erase_size);
  return iterator(storage_view.data + erase_index);
}

template <typename T, size_t N, typename A>
auto Storage<T, N, A>::Reserve(size_type requested_capacity) -> void {
  StorageView storage_view = MakeStorageView();

  if (ABSL_PREDICT_FALSE(requested_capacity <= storage_view.capacity)) return;

  AllocationTransaction allocation_tx(GetAllocPtr());

  IteratorValueAdapter<MoveIterator> move_values(
      MoveIterator(storage_view.data));

  size_type new_capacity =
      ComputeCapacity(storage_view.capacity, requested_capacity);
  pointer new_data = allocation_tx.Allocate(new_capacity);

  inlined_vector_internal::ConstructElements(GetAllocPtr(), new_data,
                                             &move_values, storage_view.size);

  inlined_vector_internal::DestroyElements(GetAllocPtr(), storage_view.data,
                                           storage_view.size);

  DeallocateIfAllocated();
  AcquireAllocatedData(&allocation_tx);
  SetIsAllocated();
}

template <typename T, size_t N, typename A>
auto Storage<T, N, A>::ShrinkToFit() -> void {
  // May only be called on allocated instances!
  assert(GetIsAllocated());

  StorageView storage_view{GetAllocatedData(), GetSize(),
                           GetAllocatedCapacity()};

  if (ABSL_PREDICT_FALSE(storage_view.size == storage_view.capacity)) return;

  AllocationTransaction allocation_tx(GetAllocPtr());

  IteratorValueAdapter<MoveIterator> move_values(
      MoveIterator(storage_view.data));

  pointer construct_data;
  if (storage_view.size > GetInlinedCapacity()) {
    size_type new_capacity = storage_view.size;
    construct_data = allocation_tx.Allocate(new_capacity);
  } else {
    construct_data = GetInlinedData();
  }

  ABSL_INTERNAL_TRY {
    inlined_vector_internal::ConstructElements(GetAllocPtr(), construct_data,
                                               &move_values, storage_view.size);
  }
  ABSL_INTERNAL_CATCH_ANY {
    SetAllocatedData(storage_view.data, storage_view.capacity);
    ABSL_INTERNAL_RETHROW;
  }

  inlined_vector_internal::DestroyElements(GetAllocPtr(), storage_view.data,
                                           storage_view.size);

  AllocatorTraits::deallocate(*GetAllocPtr(), storage_view.data,
                              storage_view.capacity);

  if (allocation_tx.DidAllocate()) {
    AcquireAllocatedData(&allocation_tx);
  } else {
    UnsetIsAllocated();
  }
}

template <typename T, size_t N, typename A>
auto Storage<T, N, A>::Swap(Storage* other_storage_ptr) -> void {
  using std::swap;
  assert(this != other_storage_ptr);

  if (GetIsAllocated() && other_storage_ptr->GetIsAllocated()) {
    swap(data_.allocated, other_storage_ptr->data_.allocated);
  } else if (!GetIsAllocated() && !other_storage_ptr->GetIsAllocated()) {
    Storage* small_ptr = this;
    Storage* large_ptr = other_storage_ptr;
    if (small_ptr->GetSize() > large_ptr->GetSize()) swap(small_ptr, large_ptr);

    for (size_type i = 0; i < small_ptr->GetSize(); ++i) {
      swap(small_ptr->GetInlinedData()[i], large_ptr->GetInlinedData()[i]);
    }

    IteratorValueAdapter<MoveIterator> move_values(
        MoveIterator(large_ptr->GetInlinedData() + small_ptr->GetSize()));

    inlined_vector_internal::ConstructElements(
        large_ptr->GetAllocPtr(),
        small_ptr->GetInlinedData() + small_ptr->GetSize(), &move_values,
        large_ptr->GetSize() - small_ptr->GetSize());

    inlined_vector_internal::DestroyElements(
        large_ptr->GetAllocPtr(),
        large_ptr->GetInlinedData() + small_ptr->GetSize(),
        large_ptr->GetSize() - small_ptr->GetSize());
  } else {
    Storage* allocated_ptr = this;
    Storage* inlined_ptr = other_storage_ptr;
    if (!allocated_ptr->GetIsAllocated()) swap(allocated_ptr, inlined_ptr);

    StorageView allocated_storage_view{allocated_ptr->GetAllocatedData(),
                                       allocated_ptr->GetSize(),
                                       allocated_ptr->GetAllocatedCapacity()};

    IteratorValueAdapter<MoveIterator> move_values(
        MoveIterator(inlined_ptr->GetInlinedData()));

    ABSL_INTERNAL_TRY {
      inlined_vector_internal::ConstructElements(
          inlined_ptr->GetAllocPtr(), allocated_ptr->GetInlinedData(),
          &move_values, inlined_ptr->GetSize());
    }
    ABSL_INTERNAL_CATCH_ANY {
      allocated_ptr->SetAllocatedData(allocated_storage_view.data,
                                      allocated_storage_view.capacity);
      ABSL_INTERNAL_RETHROW;
    }

    inlined_vector_internal::DestroyElements(inlined_ptr->GetAllocPtr(),
                                             inlined_ptr->GetInlinedData(),
                                             inlined_ptr->GetSize());

    inlined_ptr->SetAllocatedData(allocated_storage_view.data,
                                  allocated_storage_view.capacity);
  }

  swap(GetSizeAndIsAllocated(), other_storage_ptr->GetSizeAndIsAllocated());
  swap(*GetAllocPtr(), *other_storage_ptr->GetAllocPtr());
}

}  // namespace inlined_vector_internal
ABSL_NAMESPACE_END
}  // namespace absl

#endif  // ABSL_CONTAINER_INTERNAL_INLINED_VECTOR_INTERNAL_H_
