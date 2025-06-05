/*
 *  Copyright 2016 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/weak_ptr.h"

#include <memory>
#include <string>

#include "rtc_base/event.h"
#include "rtc_base/task_queue_for_test.h"
#include "test/gtest.h"

namespace rtc {

namespace {

struct Base {
  std::string member;
};
struct Derived : public Base {};

struct Target {};

struct Arrow {
  WeakPtr<Target> target;
};

struct TargetWithFactory : public Target {
  TargetWithFactory() : factory(this) {}
  WeakPtrFactory<Target> factory;
};

}  // namespace

TEST(WeakPtrFactoryTest, Basic) {
  int data;
  WeakPtrFactory<int> factory(&data);
  WeakPtr<int> ptr = factory.GetWeakPtr();
  EXPECT_EQ(&data, ptr.get());
}

TEST(WeakPtrFactoryTest, Comparison) {
  int data;
  WeakPtrFactory<int> factory(&data);
  WeakPtr<int> ptr = factory.GetWeakPtr();
  WeakPtr<int> ptr2 = ptr;
  EXPECT_EQ(ptr.get(), ptr2.get());
}

TEST(WeakPtrFactoryTest, Move) {
  int data;
  WeakPtrFactory<int> factory(&data);
  WeakPtr<int> ptr = factory.GetWeakPtr();
  WeakPtr<int> ptr2 = factory.GetWeakPtr();
  WeakPtr<int> ptr3 = std::move(ptr2);
  EXPECT_NE(ptr.get(), ptr2.get());
  EXPECT_EQ(ptr.get(), ptr3.get());
}

TEST(WeakPtrFactoryTest, OutOfScope) {
  WeakPtr<int> ptr;
  EXPECT_EQ(nullptr, ptr.get());
  {
    int data;
    WeakPtrFactory<int> factory(&data);
    ptr = factory.GetWeakPtr();
    EXPECT_EQ(&data, ptr.get());
  }
  EXPECT_EQ(nullptr, ptr.get());
}

TEST(WeakPtrFactoryTest, Multiple) {
  WeakPtr<int> a, b;
  {
    int data;
    WeakPtrFactory<int> factory(&data);
    a = factory.GetWeakPtr();
    b = factory.GetWeakPtr();
    EXPECT_EQ(&data, a.get());
    EXPECT_EQ(&data, b.get());
  }
  EXPECT_EQ(nullptr, a.get());
  EXPECT_EQ(nullptr, b.get());
}

TEST(WeakPtrFactoryTest, MultipleStaged) {
  WeakPtr<int> a;
  {
    int data;
    WeakPtrFactory<int> factory(&data);
    a = factory.GetWeakPtr();
    { WeakPtr<int> b = factory.GetWeakPtr(); }
    EXPECT_NE(nullptr, a.get());
  }
  EXPECT_EQ(nullptr, a.get());
}

TEST(WeakPtrFactoryTest, Dereference) {
  Base data;
  data.member = "123456";
  WeakPtrFactory<Base> factory(&data);
  WeakPtr<Base> ptr = factory.GetWeakPtr();
  EXPECT_EQ(&data, ptr.get());
  EXPECT_EQ(data.member, (*ptr).member);
  EXPECT_EQ(data.member, ptr->member);
}

TEST(WeakPtrFactoryTest, UpCast) {
  Derived data;
  WeakPtrFactory<Derived> factory(&data);
  WeakPtr<Base> ptr = factory.GetWeakPtr();
  ptr = factory.GetWeakPtr();
  EXPECT_EQ(ptr.get(), &data);
}

TEST(WeakPtrTest, DefaultConstructor) {
  WeakPtr<int> ptr;
  EXPECT_EQ(nullptr, ptr.get());
}

TEST(WeakPtrFactoryTest, BooleanTesting) {
  int data;
  WeakPtrFactory<int> factory(&data);

  WeakPtr<int> ptr_to_an_instance = factory.GetWeakPtr();
  EXPECT_TRUE(ptr_to_an_instance);
  EXPECT_FALSE(!ptr_to_an_instance);

  if (ptr_to_an_instance) {
  } else {
    ADD_FAILURE() << "Pointer to an instance should result in true.";
  }

  if (!ptr_to_an_instance) {  // check for operator!().
    ADD_FAILURE() << "Pointer to an instance should result in !x being false.";
  }

  WeakPtr<int> null_ptr;
  EXPECT_FALSE(null_ptr);
  EXPECT_TRUE(!null_ptr);

  if (null_ptr) {
    ADD_FAILURE() << "Null pointer should result in false.";
  }

  if (!null_ptr) {  // check for operator!().
  } else {
    ADD_FAILURE() << "Null pointer should result in !x being true.";
  }
}

TEST(WeakPtrFactoryTest, ComparisonToNull) {
  int data;
  WeakPtrFactory<int> factory(&data);

  WeakPtr<int> ptr_to_an_instance = factory.GetWeakPtr();
  EXPECT_NE(nullptr, ptr_to_an_instance);
  EXPECT_NE(ptr_to_an_instance, nullptr);

  WeakPtr<int> null_ptr;
  EXPECT_EQ(null_ptr, nullptr);
  EXPECT_EQ(nullptr, null_ptr);
}

TEST(WeakPtrTest, InvalidateWeakPtrs) {
  int data;
  WeakPtrFactory<int> factory(&data);
  WeakPtr<int> ptr = factory.GetWeakPtr();
  EXPECT_EQ(&data, ptr.get());
  EXPECT_TRUE(factory.HasWeakPtrs());
  factory.InvalidateWeakPtrs();
  EXPECT_EQ(nullptr, ptr.get());
  EXPECT_FALSE(factory.HasWeakPtrs());

  // Test that the factory can create new weak pointers after a
  // InvalidateWeakPtrs call, and they remain valid until the next
  // InvalidateWeakPtrs call.
  WeakPtr<int> ptr2 = factory.GetWeakPtr();
  EXPECT_EQ(&data, ptr2.get());
  EXPECT_TRUE(factory.HasWeakPtrs());
  factory.InvalidateWeakPtrs();
  EXPECT_EQ(nullptr, ptr2.get());
  EXPECT_FALSE(factory.HasWeakPtrs());
}

TEST(WeakPtrTest, HasWeakPtrs) {
  int data;
  WeakPtrFactory<int> factory(&data);
  {
    WeakPtr<int> ptr = factory.GetWeakPtr();
    EXPECT_TRUE(factory.HasWeakPtrs());
  }
  EXPECT_FALSE(factory.HasWeakPtrs());
}

template <class T>
std::unique_ptr<T> NewObjectCreatedOnTaskQueue() {
  std::unique_ptr<T> obj;
  webrtc::TaskQueueForTest queue("NewObjectCreatedOnTaskQueue");
  queue.SendTask([&] { obj = std::make_unique<T>(); });
  return obj;
}

TEST(WeakPtrTest, ObjectAndWeakPtrOnDifferentThreads) {
  // Test that it is OK to create an object with a WeakPtrFactory one thread,
  // but use it on another.  This tests that we do not trip runtime checks that
  // ensure that a WeakPtr is not used by multiple threads.
  std::unique_ptr<TargetWithFactory> target(
      NewObjectCreatedOnTaskQueue<TargetWithFactory>());
  WeakPtr<Target> weak_ptr = target->factory.GetWeakPtr();
  EXPECT_EQ(target.get(), weak_ptr.get());
}

TEST(WeakPtrTest, WeakPtrInitiateAndUseOnDifferentThreads) {
  // Test that it is OK to create a WeakPtr on one thread, but use it on
  // another. This tests that we do not trip runtime checks that ensure that a
  // WeakPtr is not used by multiple threads.
  auto target = std::make_unique<TargetWithFactory>();
  // Create weak ptr on main thread
  WeakPtr<Target> weak_ptr = target->factory.GetWeakPtr();
  webrtc::TaskQueueForTest queue("queue");
  queue.SendTask([&] {
    // Dereference and invalide weak_ptr on another thread.
    EXPECT_EQ(weak_ptr.get(), target.get());
    target.reset();
  });
}

}  // namespace rtc
