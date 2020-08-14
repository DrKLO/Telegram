// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_MESSAGE_LOOP_MESSAGE_PUMP_TYPE_H_
#define BASE_MESSAGE_LOOP_MESSAGE_PUMP_TYPE_H_

#include "build/build_config.h"

namespace base {

// A MessagePump has a particular type, which indicates the set of
// asynchronous events it may process in addition to tasks and timers.

enum class MessagePumpType {
  // This type of pump only supports tasks and timers.
  DEFAULT,

  // This type of pump also supports native UI events (e.g., Windows
  // messages).
  UI,

  // User provided implementation of MessagePump interface
  CUSTOM,

  // This type of pump also supports asynchronous IO.
  IO,

#if defined(OS_ANDROID)
  // This type of pump is backed by a Java message handler which is
  // responsible for running the tasks added to the ML. This is only for use
  // on Android. TYPE_JAVA behaves in essence like TYPE_UI, except during
  // construction where it does not use the main thread specific pump factory.
  JAVA,
#endif  // defined(OS_ANDROID)

#if defined(OS_MACOSX)
  // This type of pump is backed by a NSRunLoop. This is only for use on
  // OSX and IOS.
  NS_RUNLOOP,
#endif  // defined(OS_MACOSX)

#if defined(OS_WIN)
  // This type of pump supports WM_QUIT messages in addition to other native
  // UI events. This is only for use on windows.
  UI_WITH_WM_QUIT_SUPPORT,
#endif  // defined(OS_WIN)
};

}  // namespace base

#endif  // BASE_MESSAGE_LOOP_MESSAGE_PUMP_TYPE_H_
