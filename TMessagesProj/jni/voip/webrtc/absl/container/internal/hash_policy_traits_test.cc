// Copyright 2018 The Abseil Authors.
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

#include "absl/container/internal/hash_policy_traits.h"

#include <functional>
#include <memory>
#include <new>

#include "gmock/gmock.h"
#include "gtest/gtest.h"

namespace absl {
ABSL_NAMESPACE_BEGIN
namespace container_internal {
namespace {

using ::testing::MockFunction;
using ::testing::Return;
using ::testing::ReturnRef;

using Alloc = std::allocator<int>;
using Slot = int;

struct PolicyWithoutOptionalOps {
  using slot_type = Slot;
  using key_type = Slot;
  using init_type = Slot;

  static std::function<void(void*, Slot*, Slot)> construct;
  static std::function<void(void*, Slot*)> destroy;

  static std::function<Slot&(Slot*)> element;
  static int apply(int v) { return apply_impl(v); }
  static std::function<int(int)> apply_impl;
  static std::function<Slot&(Slot*)> value;
};

std::function<void(void*, Slot*, Slot)> PolicyWithoutOptionalOps::construct;
std::function<void(void*, Slot*)> PolicyWithoutOptionalOps::destroy;

std::function<Slot&(Slot*)> PolicyWithoutOptionalOps::element;
std::function<int(int)> PolicyWithoutOptionalOps::apply_impl;
std::function<Slot&(Slot*)> PolicyWithoutOptionalOps::value;

struct PolicyWithOptionalOps : PolicyWithoutOptionalOps {
  static std::function<void(void*, Slot*, Slot*)> transfer;
};

std::function<void(void*, Slot*, Slot*)> PolicyWithOptionalOps::transfer;

struct Test : ::testing::Test {
  Test() {
    PolicyWithoutOptionalOps::construct = [&](void* a1, Slot* a2, Slot a3) {
      construct.Call(a1, a2, std::move(a3));
    };
    PolicyWithoutOptionalOps::destroy = [&](void* a1, Slot* a2) {
      destroy.Call(a1, a2);
    };

    PolicyWithoutOptionalOps::element = [&](Slot* a1) -> Slot& {
      return element.Call(a1);
    };
    PolicyWithoutOptionalOps::apply_impl = [&](int a1) -> int {
      return apply.Call(a1);
    };
    PolicyWithoutOptionalOps::value = [&](Slot* a1) -> Slot& {
      return value.Call(a1);
    };

    PolicyWithOptionalOps::transfer = [&](void* a1, Slot* a2, Slot* a3) {
      return transfer.Call(a1, a2, a3);
    };
  }

  std::allocator<int> alloc;
  int a = 53;

  MockFunction<void(void*, Slot*, Slot)> construct;
  MockFunction<void(void*, Slot*)> destroy;

  MockFunction<Slot&(Slot*)> element;
  MockFunction<int(int)> apply;
  MockFunction<Slot&(Slot*)> value;

  MockFunction<void(void*, Slot*, Slot*)> transfer;
};

TEST_F(Test, construct) {
  EXPECT_CALL(construct, Call(&alloc, &a, 53));
  hash_policy_traits<PolicyWithoutOptionalOps>::construct(&alloc, &a, 53);
}

TEST_F(Test, destroy) {
  EXPECT_CALL(destroy, Call(&alloc, &a));
  hash_policy_traits<PolicyWithoutOptionalOps>::destroy(&alloc, &a);
}

TEST_F(Test, element) {
  int b = 0;
  EXPECT_CALL(element, Call(&a)).WillOnce(ReturnRef(b));
  EXPECT_EQ(&b, &hash_policy_traits<PolicyWithoutOptionalOps>::element(&a));
}

TEST_F(Test, apply) {
  EXPECT_CALL(apply, Call(42)).WillOnce(Return(1337));
  EXPECT_EQ(1337, (hash_policy_traits<PolicyWithoutOptionalOps>::apply(42)));
}

TEST_F(Test, value) {
  int b = 0;
  EXPECT_CALL(value, Call(&a)).WillOnce(ReturnRef(b));
  EXPECT_EQ(&b, &hash_policy_traits<PolicyWithoutOptionalOps>::value(&a));
}

TEST_F(Test, without_transfer) {
  int b = 42;
  EXPECT_CALL(element, Call(&b)).WillOnce(::testing::ReturnRef(b));
  EXPECT_CALL(construct, Call(&alloc, &a, b));
  EXPECT_CALL(destroy, Call(&alloc, &b));
  hash_policy_traits<PolicyWithoutOptionalOps>::transfer(&alloc, &a, &b);
}

TEST_F(Test, with_transfer) {
  int b = 42;
  EXPECT_CALL(transfer, Call(&alloc, &a, &b));
  hash_policy_traits<PolicyWithOptionalOps>::transfer(&alloc, &a, &b);
}

}  // namespace
}  // namespace container_internal
ABSL_NAMESPACE_END
}  // namespace absl
