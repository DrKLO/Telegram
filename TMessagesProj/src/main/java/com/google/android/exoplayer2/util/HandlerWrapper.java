/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.util;

import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;

/**
 * An interface to call through to a {@link Handler}. Instances must be created by calling {@link
 * Clock#createHandler(Looper, Handler.Callback)} on {@link Clock#DEFAULT} for all non-test cases.
 */
public interface HandlerWrapper {

  /** A message obtained from the handler. */
  interface Message {

    /** See {@link android.os.Message#sendToTarget()}. */
    void sendToTarget();

    /** See {@link android.os.Message#getTarget()}. */
    HandlerWrapper getTarget();
  }

  /** See {@link Handler#getLooper()}. */
  Looper getLooper();

  /** See {@link Handler#hasMessages(int)}. */
  boolean hasMessages(int what);

  /** See {@link Handler#obtainMessage(int)}. */
  Message obtainMessage(int what);

  /** See {@link Handler#obtainMessage(int, Object)}. */
  Message obtainMessage(int what, @Nullable Object obj);

  /** See {@link Handler#obtainMessage(int, int, int)}. */
  Message obtainMessage(int what, int arg1, int arg2);

  /** See {@link Handler#obtainMessage(int, int, int, Object)}. */
  Message obtainMessage(int what, int arg1, int arg2, @Nullable Object obj);

  /** See {@link Handler#sendMessageAtFrontOfQueue(android.os.Message)}. */
  boolean sendMessageAtFrontOfQueue(Message message);

  /** See {@link Handler#sendEmptyMessage(int)}. */
  boolean sendEmptyMessage(int what);

  /** See {@link Handler#sendEmptyMessageDelayed(int, long)}. */
  boolean sendEmptyMessageDelayed(int what, int delayMs);

  /** See {@link Handler#sendEmptyMessageAtTime(int, long)}. */
  boolean sendEmptyMessageAtTime(int what, long uptimeMs);

  /** See {@link Handler#removeMessages(int)}. */
  void removeMessages(int what);

  /** See {@link Handler#removeCallbacksAndMessages(Object)}. */
  void removeCallbacksAndMessages(@Nullable Object token);

  /** See {@link Handler#post(Runnable)}. */
  boolean post(Runnable runnable);

  /** See {@link Handler#postDelayed(Runnable, long)}. */
  boolean postDelayed(Runnable runnable, long delayMs);

  /** See {@link android.os.Handler#postAtFrontOfQueue(Runnable)}. */
  boolean postAtFrontOfQueue(Runnable runnable);
}
