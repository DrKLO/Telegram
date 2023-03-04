// Copyright 2017 The Abseil Authors.
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

// Tests for pointer utilities.

#include "absl/memory/memory.h"

#include <sys/types.h>

#include <cstddef>
#include <memory>
#include <string>
#include <type_traits>
#include <utility>
#include <vector>

#include "gmock/gmock.h"
#include "gtest/gtest.h"

namespace {

using ::testing::ElementsAre;
using ::testing::Return;

// This class creates observable behavior to verify that a destructor has
// been called, via the instance_count variable.
class DestructorVerifier {
 public:
  DestructorVerifier() { ++instance_count_; }
  DestructorVerifier(const DestructorVerifier&) = delete;
  DestructorVerifier& operator=(const DestructorVerifier&) = delete;
  ~DestructorVerifier() { --instance_count_; }

  // The number of instances of this class currently active.
  static int instance_count() { return instance_count_; }

 private:
  // The number of instances of this class currently active.
  static int instance_count_;
};

int DestructorVerifier::instance_count_ = 0;

TEST(WrapUniqueTest, WrapUnique) {
  // Test that the unique_ptr is constructed properly by verifying that the
  // destructor for its payload gets called at the proper time.
  {
    auto dv = new DestructorVerifier;
    EXPECT_EQ(1, DestructorVerifier::instance_count());
    std::unique_ptr<DestructorVerifier> ptr = absl::WrapUnique(dv);
    EXPECT_EQ(1, DestructorVerifier::instance_count());
  }
  EXPECT_EQ(0, DestructorVerifier::instance_count());
}

// InitializationVerifier fills in a pattern when allocated so we can
// distinguish between its default and value initialized states (without
// accessing truly uninitialized memory).
struct InitializationVerifier {
  static constexpr int kDefaultScalar = 0x43;
  static constexpr int kDefaultArray = 0x4B;

  static void* operator new(size_t n) {
    void* ret = ::operator new(n);
    memset(ret, kDefaultScalar, n);
    return ret;
  }

  static void* operator new[](size_t n) {
    void* ret = ::operator new[](n);
    memset(ret, kDefaultArray, n);
    return ret;
  }

  int a;
  int b;
};

struct ArrayWatch {
  void* operator new[](size_t n) {
    allocs().push_back(n);
    return ::operator new[](n);
  }
  void operator delete[](void* p) { return ::operator delete[](p); }
  static std::vector<size_t>& allocs() {
    static auto& v = *new std::vector<size_t>;
    return v;
  }
};

TEST(RawPtrTest, RawPointer) {
  int i = 5;
  EXPECT_EQ(&i, absl::RawPtr(&i));
}

TEST(RawPtrTest, SmartPointer) {
  int* o = new int(5);
  std::unique_ptr<int> p(o);
  EXPECT_EQ(o, absl::RawPtr(p));
}

class IntPointerNonConstDeref {
 public:
  explicit IntPointerNonConstDeref(int* p) : p_(p) {}
  friend bool operator!=(const IntPointerNonConstDeref& a, std::nullptr_t) {
    return a.p_ != nullptr;
  }
  int& operator*() { return *p_; }

 private:
  std::unique_ptr<int> p_;
};

TEST(RawPtrTest, SmartPointerNonConstDereference) {
  int* o = new int(5);
  IntPointerNonConstDeref p(o);
  EXPECT_EQ(o, absl::RawPtr(p));
}

TEST(RawPtrTest, NullValuedRawPointer) {
  int* p = nullptr;
  EXPECT_EQ(nullptr, absl::RawPtr(p));
}

TEST(RawPtrTest, NullValuedSmartPointer) {
  std::unique_ptr<int> p;
  EXPECT_EQ(nullptr, absl::RawPtr(p));
}

TEST(RawPtrTest, Nullptr) {
  auto p = absl::RawPtr(nullptr);
  EXPECT_TRUE((std::is_same<std::nullptr_t, decltype(p)>::value));
  EXPECT_EQ(nullptr, p);
}

TEST(RawPtrTest, Null) {
  auto p = absl::RawPtr(nullptr);
  EXPECT_TRUE((std::is_same<std::nullptr_t, decltype(p)>::value));
  EXPECT_EQ(nullptr, p);
}

TEST(RawPtrTest, Zero) {
  auto p = absl::RawPtr(nullptr);
  EXPECT_TRUE((std::is_same<std::nullptr_t, decltype(p)>::value));
  EXPECT_EQ(nullptr, p);
}

TEST(ShareUniquePtrTest, Share) {
  auto up = absl::make_unique<int>();
  int* rp = up.get();
  auto sp = absl::ShareUniquePtr(std::move(up));
  EXPECT_EQ(sp.get(), rp);
}

TEST(ShareUniquePtrTest, ShareNull) {
  struct NeverDie {
    using pointer = void*;
    void operator()(pointer) {
      ASSERT_TRUE(false) << "Deleter should not have been called.";
    }
  };

  std::unique_ptr<void, NeverDie> up;
  auto sp = absl::ShareUniquePtr(std::move(up));
}

TEST(WeakenPtrTest, Weak) {
  auto sp = std::make_shared<int>();
  auto wp = absl::WeakenPtr(sp);
  EXPECT_EQ(sp.get(), wp.lock().get());
  sp.reset();
  EXPECT_TRUE(wp.expired());
}

// Should not compile.
/*
TEST(RawPtrTest, NotAPointer) {
  absl::RawPtr(1.5);
}
*/

template <typename T>
struct SmartPointer {
  using difference_type = char;
};

struct PointerWith {
  using element_type = int32_t;
  using difference_type = int16_t;
  template <typename U>
  using rebind = SmartPointer<U>;

  static PointerWith pointer_to(
      element_type& r) {  // NOLINT(runtime/references)
    return PointerWith{&r};
  }

  element_type* ptr;
};

template <typename... Args>
struct PointerWithout {};

TEST(PointerTraits, Types) {
  using TraitsWith = absl::pointer_traits<PointerWith>;
  EXPECT_TRUE((std::is_same<TraitsWith::pointer, PointerWith>::value));
  EXPECT_TRUE((std::is_same<TraitsWith::element_type, int32_t>::value));
  EXPECT_TRUE((std::is_same<TraitsWith::difference_type, int16_t>::value));
  EXPECT_TRUE((
      std::is_same<TraitsWith::rebind<int64_t>, SmartPointer<int64_t>>::value));

  using TraitsWithout = absl::pointer_traits<PointerWithout<double, int>>;
  EXPECT_TRUE((std::is_same<TraitsWithout::pointer,
                            PointerWithout<double, int>>::value));
  EXPECT_TRUE((std::is_same<TraitsWithout::element_type, double>::value));
  EXPECT_TRUE(
      (std::is_same<TraitsWithout ::difference_type, std::ptrdiff_t>::value));
  EXPECT_TRUE((std::is_same<TraitsWithout::rebind<int64_t>,
                            PointerWithout<int64_t, int>>::value));

  using TraitsRawPtr = absl::pointer_traits<char*>;
  EXPECT_TRUE((std::is_same<TraitsRawPtr::pointer, char*>::value));
  EXPECT_TRUE((std::is_same<TraitsRawPtr::element_type, char>::value));
  EXPECT_TRUE(
      (std::is_same<TraitsRawPtr::difference_type, std::ptrdiff_t>::value));
  EXPECT_TRUE((std::is_same<TraitsRawPtr::rebind<int64_t>, int64_t*>::value));
}

TEST(PointerTraits, Functions) {
  int i;
  EXPECT_EQ(&i, absl::pointer_traits<PointerWith>::pointer_to(i).ptr);
  EXPECT_EQ(&i, absl::pointer_traits<int*>::pointer_to(i));
}

TEST(AllocatorTraits, Typedefs) {
  struct A {
    struct value_type {};
  };
  EXPECT_TRUE((
      std::is_same<A,
                   typename absl::allocator_traits<A>::allocator_type>::value));
  EXPECT_TRUE(
      (std::is_same<A::value_type,
                    typename absl::allocator_traits<A>::value_type>::value));

  struct X {};
  struct HasPointer {
    using value_type = X;
    using pointer = SmartPointer<X>;
  };
  EXPECT_TRUE((std::is_same<SmartPointer<X>, typename absl::allocator_traits<
                                                 HasPointer>::pointer>::value));
  EXPECT_TRUE(
      (std::is_same<A::value_type*,
                    typename absl::allocator_traits<A>::pointer>::value));

  EXPECT_TRUE(
      (std::is_same<
          SmartPointer<const X>,
          typename absl::allocator_traits<HasPointer>::const_pointer>::value));
  EXPECT_TRUE(
      (std::is_same<const A::value_type*,
                    typename absl::allocator_traits<A>::const_pointer>::value));

  struct HasVoidPointer {
    using value_type = X;
    struct void_pointer {};
  };

  EXPECT_TRUE((std::is_same<HasVoidPointer::void_pointer,
                            typename absl::allocator_traits<
                                HasVoidPointer>::void_pointer>::value));
  EXPECT_TRUE(
      (std::is_same<SmartPointer<void>, typename absl::allocator_traits<
                                            HasPointer>::void_pointer>::value));

  struct HasConstVoidPointer {
    using value_type = X;
    struct const_void_pointer {};
  };

  EXPECT_TRUE(
      (std::is_same<HasConstVoidPointer::const_void_pointer,
                    typename absl::allocator_traits<
                        HasConstVoidPointer>::const_void_pointer>::value));
  EXPECT_TRUE((std::is_same<SmartPointer<const void>,
                            typename absl::allocator_traits<
                                HasPointer>::const_void_pointer>::value));

  struct HasDifferenceType {
    using value_type = X;
    using difference_type = int;
  };
  EXPECT_TRUE(
      (std::is_same<int, typename absl::allocator_traits<
                             HasDifferenceType>::difference_type>::value));
  EXPECT_TRUE((std::is_same<char, typename absl::allocator_traits<
                                      HasPointer>::difference_type>::value));

  struct HasSizeType {
    using value_type = X;
    using size_type = unsigned int;
  };
  EXPECT_TRUE((std::is_same<unsigned int, typename absl::allocator_traits<
                                              HasSizeType>::size_type>::value));
  EXPECT_TRUE((std::is_same<unsigned char, typename absl::allocator_traits<
                                               HasPointer>::size_type>::value));

  struct HasPropagateOnCopy {
    using value_type = X;
    struct propagate_on_container_copy_assignment {};
  };

  EXPECT_TRUE(
      (std::is_same<HasPropagateOnCopy::propagate_on_container_copy_assignment,
                    typename absl::allocator_traits<HasPropagateOnCopy>::
                        propagate_on_container_copy_assignment>::value));
  EXPECT_TRUE(
      (std::is_same<std::false_type,
                    typename absl::allocator_traits<
                        A>::propagate_on_container_copy_assignment>::value));

  struct HasPropagateOnMove {
    using value_type = X;
    struct propagate_on_container_move_assignment {};
  };

  EXPECT_TRUE(
      (std::is_same<HasPropagateOnMove::propagate_on_container_move_assignment,
                    typename absl::allocator_traits<HasPropagateOnMove>::
                        propagate_on_container_move_assignment>::value));
  EXPECT_TRUE(
      (std::is_same<std::false_type,
                    typename absl::allocator_traits<
                        A>::propagate_on_container_move_assignment>::value));

  struct HasPropagateOnSwap {
    using value_type = X;
    struct propagate_on_container_swap {};
  };

  EXPECT_TRUE(
      (std::is_same<HasPropagateOnSwap::propagate_on_container_swap,
                    typename absl::allocator_traits<HasPropagateOnSwap>::
                        propagate_on_container_swap>::value));
  EXPECT_TRUE(
      (std::is_same<std::false_type, typename absl::allocator_traits<A>::
                                         propagate_on_container_swap>::value));

  struct HasIsAlwaysEqual {
    using value_type = X;
    struct is_always_equal {};
  };

  EXPECT_TRUE((std::is_same<HasIsAlwaysEqual::is_always_equal,
                            typename absl::allocator_traits<
                                HasIsAlwaysEqual>::is_always_equal>::value));
  EXPECT_TRUE((std::is_same<std::true_type, typename absl::allocator_traits<
                                                A>::is_always_equal>::value));
  struct NonEmpty {
    using value_type = X;
    int i;
  };
  EXPECT_TRUE(
      (std::is_same<std::false_type,
                    absl::allocator_traits<NonEmpty>::is_always_equal>::value));
}

template <typename T>
struct AllocWithPrivateInheritance : private std::allocator<T> {
  using value_type = T;
};

TEST(AllocatorTraits, RebindWithPrivateInheritance) {
  // Regression test for some versions of gcc that do not like the sfinae we
  // used in combination with private inheritance.
  EXPECT_TRUE(
      (std::is_same<AllocWithPrivateInheritance<int>,
                    absl::allocator_traits<AllocWithPrivateInheritance<char>>::
                        rebind_alloc<int>>::value));
}

template <typename T>
struct Rebound {};

struct AllocWithRebind {
  using value_type = int;
  template <typename T>
  struct rebind {
    using other = Rebound<T>;
  };
};

template <typename T, typename U>
struct AllocWithoutRebind {
  using value_type = int;
};

TEST(AllocatorTraits, Rebind) {
  EXPECT_TRUE(
      (std::is_same<Rebound<int>,
                    typename absl::allocator_traits<
                        AllocWithRebind>::template rebind_alloc<int>>::value));
  EXPECT_TRUE(
      (std::is_same<absl::allocator_traits<Rebound<int>>,
                    typename absl::allocator_traits<
                        AllocWithRebind>::template rebind_traits<int>>::value));

  EXPECT_TRUE(
      (std::is_same<AllocWithoutRebind<double, char>,
                    typename absl::allocator_traits<AllocWithoutRebind<
                        int, char>>::template rebind_alloc<double>>::value));
  EXPECT_TRUE(
      (std::is_same<absl::allocator_traits<AllocWithoutRebind<double, char>>,
                    typename absl::allocator_traits<AllocWithoutRebind<
                        int, char>>::template rebind_traits<double>>::value));
}

struct TestValue {
  TestValue() {}
  explicit TestValue(int* trace) : trace(trace) { ++*trace; }
  ~TestValue() {
    if (trace) --*trace;
  }
  int* trace = nullptr;
};

struct MinimalMockAllocator {
  MinimalMockAllocator() : value(0) {}
  explicit MinimalMockAllocator(int value) : value(value) {}
  MinimalMockAllocator(const MinimalMockAllocator& other)
      : value(other.value) {}
  using value_type = TestValue;
  MOCK_METHOD(value_type*, allocate, (size_t));
  MOCK_METHOD(void, deallocate, (value_type*, size_t));

  int value;
};

TEST(AllocatorTraits, FunctionsMinimal) {
  int trace = 0;
  int hint;
  alignas(TestValue) char buffer[sizeof(TestValue)];
  auto* x = reinterpret_cast<TestValue*>(buffer);
  MinimalMockAllocator mock;
  using Traits = absl::allocator_traits<MinimalMockAllocator>;
  EXPECT_CALL(mock, allocate(7)).WillRepeatedly(Return(x));
  EXPECT_CALL(mock, deallocate(x, 7));

  EXPECT_EQ(x, Traits::allocate(mock, 7));
  static_cast<void>(Traits::allocate(mock, 7, static_cast<const void*>(&hint)));
  EXPECT_EQ(x, Traits::allocate(mock, 7, static_cast<const void*>(&hint)));
  Traits::deallocate(mock, x, 7);

  EXPECT_EQ(0, trace);
  Traits::construct(mock, x, &trace);
  EXPECT_EQ(1, trace);
  Traits::destroy(mock, x);
  EXPECT_EQ(0, trace);

  EXPECT_EQ(std::numeric_limits<size_t>::max() / sizeof(TestValue),
            Traits::max_size(mock));

  EXPECT_EQ(0, mock.value);
  EXPECT_EQ(0, Traits::select_on_container_copy_construction(mock).value);
}

struct FullMockAllocator {
  FullMockAllocator() : value(0) {}
  explicit FullMockAllocator(int value) : value(value) {}
  FullMockAllocator(const FullMockAllocator& other) : value(other.value) {}
  using value_type = TestValue;
  MOCK_METHOD(value_type*, allocate, (size_t));
  MOCK_METHOD(value_type*, allocate, (size_t, const void*));
  MOCK_METHOD(void, construct, (value_type*, int*));
  MOCK_METHOD(void, destroy, (value_type*));
  MOCK_METHOD(size_t, max_size, (),
              (const));
  MOCK_METHOD(FullMockAllocator, select_on_container_copy_construction, (),
              (const));

  int value;
};

TEST(AllocatorTraits, FunctionsFull) {
  int trace = 0;
  int hint;
  TestValue x(&trace), y;
  FullMockAllocator mock;
  using Traits = absl::allocator_traits<FullMockAllocator>;
  EXPECT_CALL(mock, allocate(7)).WillRepeatedly(Return(&x));
  EXPECT_CALL(mock, allocate(13, &hint)).WillRepeatedly(Return(&y));
  EXPECT_CALL(mock, construct(&x, &trace));
  EXPECT_CALL(mock, destroy(&x));
  EXPECT_CALL(mock, max_size()).WillRepeatedly(Return(17u));
  EXPECT_CALL(mock, select_on_container_copy_construction())
      .WillRepeatedly(Return(FullMockAllocator(23)));

  EXPECT_EQ(&x, Traits::allocate(mock, 7));
  EXPECT_EQ(&y, Traits::allocate(mock, 13, static_cast<const void*>(&hint)));

  EXPECT_EQ(1, trace);
  Traits::construct(mock, &x, &trace);
  EXPECT_EQ(1, trace);
  Traits::destroy(mock, &x);
  EXPECT_EQ(1, trace);

  EXPECT_EQ(17u, Traits::max_size(mock));

  EXPECT_EQ(0, mock.value);
  EXPECT_EQ(23, Traits::select_on_container_copy_construction(mock).value);
}

TEST(AllocatorNoThrowTest, DefaultAllocator) {
#if defined(ABSL_ALLOCATOR_NOTHROW) && ABSL_ALLOCATOR_NOTHROW
  EXPECT_TRUE(absl::default_allocator_is_nothrow::value);
#else
  EXPECT_FALSE(absl::default_allocator_is_nothrow::value);
#endif
}

TEST(AllocatorNoThrowTest, StdAllocator) {
#if defined(ABSL_ALLOCATOR_NOTHROW) && ABSL_ALLOCATOR_NOTHROW
  EXPECT_TRUE(absl::allocator_is_nothrow<std::allocator<int>>::value);
#else
  EXPECT_FALSE(absl::allocator_is_nothrow<std::allocator<int>>::value);
#endif
}

TEST(AllocatorNoThrowTest, CustomAllocator) {
  struct NoThrowAllocator {
    using is_nothrow = std::true_type;
  };
  struct CanThrowAllocator {
    using is_nothrow = std::false_type;
  };
  struct UnspecifiedAllocator {};
  EXPECT_TRUE(absl::allocator_is_nothrow<NoThrowAllocator>::value);
  EXPECT_FALSE(absl::allocator_is_nothrow<CanThrowAllocator>::value);
  EXPECT_FALSE(absl::allocator_is_nothrow<UnspecifiedAllocator>::value);
}

}  // namespace
