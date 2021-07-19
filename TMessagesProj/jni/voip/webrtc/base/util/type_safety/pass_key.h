// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_UTIL_TYPE_SAFETY_PASS_KEY_H_
#define BASE_UTIL_TYPE_SAFETY_PASS_KEY_H_

namespace util {

// util::PassKey can be used to restrict access to functions to an authorized
// caller. The primary use case is restricting the construction of an object in
// situations where the constructor needs to be public, which may be the case
// if the object must be constructed through a helper function, such as
// blink::MakeGarbageCollected.
//
// For example, to limit the creation of 'Foo' to the 'Manager' class:
//
//  class Foo {
//   public:
//    Foo(util::PassKey<Manager>);
//  };
//
//  class Manager {
//   public:
//    using PassKey = util::PassKey<Manager>;
//    Manager() : foo_(blink::MakeGarbageCollected<Foo>(PassKey())) {}
//    void Trace(blink::Visitor* visitor) { visitor->Trace(foo_); }
//    Foo* GetFooSingleton() { foo_; }
//
//   private:
//    blink::Member<Foo> foo_;
//  };
//
// In the above example, the 'Foo' constructor requires an instance of
// util::PassKey<Manager>. Only Manager is allowed to create such instances,
// making the constructor unusable elsewhere.
template <typename T>
class PassKey {
 private:
  // Avoid =default to disallow creation by uniform initialization.
  PassKey() {}

  friend T;
};

}  // namespace util

#endif  // BASE_UTIL_TYPE_SAFETY_PASS_KEY_H_
