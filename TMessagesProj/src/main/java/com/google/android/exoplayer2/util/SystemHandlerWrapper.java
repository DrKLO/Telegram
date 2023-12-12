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

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;

import android.os.Handler;
import android.os.Looper;
import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.ArrayList;
import java.util.List;

/** The standard implementation of {@link HandlerWrapper}. */
/* package */ final class SystemHandlerWrapper implements HandlerWrapper {

  private static final int MAX_POOL_SIZE = 50;

  @GuardedBy("messagePool")
  private static final List<SystemMessage> messagePool = new ArrayList<>(MAX_POOL_SIZE);

  private final android.os.Handler handler;

  public SystemHandlerWrapper(android.os.Handler handler) {
    this.handler = handler;
  }

  @Override
  public Looper getLooper() {
    return handler.getLooper();
  }

  @Override
  public boolean hasMessages(int what) {
    return handler.hasMessages(what);
  }

  @Override
  public Message obtainMessage(int what) {
    return obtainSystemMessage().setMessage(handler.obtainMessage(what), /* handler= */ this);
  }

  @Override
  public Message obtainMessage(int what, @Nullable Object obj) {
    return obtainSystemMessage().setMessage(handler.obtainMessage(what, obj), /* handler= */ this);
  }

  @Override
  public Message obtainMessage(int what, int arg1, int arg2) {
    return obtainSystemMessage()
        .setMessage(handler.obtainMessage(what, arg1, arg2), /* handler= */ this);
  }

  @Override
  public Message obtainMessage(int what, int arg1, int arg2, @Nullable Object obj) {
    return obtainSystemMessage()
        .setMessage(handler.obtainMessage(what, arg1, arg2, obj), /* handler= */ this);
  }

  @Override
  public boolean sendMessageAtFrontOfQueue(Message message) {
    return ((SystemMessage) message).sendAtFrontOfQueue(handler);
  }

  @Override
  public boolean sendEmptyMessage(int what) {
    return handler.sendEmptyMessage(what);
  }

  @Override
  public boolean sendEmptyMessageDelayed(int what, int delayMs) {
    return handler.sendEmptyMessageDelayed(what, delayMs);
  }

  @Override
  public boolean sendEmptyMessageAtTime(int what, long uptimeMs) {
    return handler.sendEmptyMessageAtTime(what, uptimeMs);
  }

  @Override
  public void removeMessages(int what) {
    handler.removeMessages(what);
  }

  @Override
  public void removeCallbacksAndMessages(@Nullable Object token) {
    handler.removeCallbacksAndMessages(token);
  }

  @Override
  public boolean post(Runnable runnable) {
    return handler.post(runnable);
  }

  @Override
  public boolean postDelayed(Runnable runnable, long delayMs) {
    return handler.postDelayed(runnable, delayMs);
  }

  @Override
  public boolean postAtFrontOfQueue(Runnable runnable) {
    return handler.postAtFrontOfQueue(runnable);
  }

  private static SystemMessage obtainSystemMessage() {
    synchronized (messagePool) {
      return messagePool.isEmpty()
          ? new SystemMessage()
          : messagePool.remove(messagePool.size() - 1);
    }
  }

  private static void recycleMessage(SystemMessage message) {
    synchronized (messagePool) {
      if (messagePool.size() < MAX_POOL_SIZE) {
        messagePool.add(message);
      }
    }
  }

  private static final class SystemMessage implements Message {

    @Nullable private android.os.Message message;
    @Nullable private SystemHandlerWrapper handler;

    @CanIgnoreReturnValue
    public SystemMessage setMessage(android.os.Message message, SystemHandlerWrapper handler) {
      this.message = message;
      this.handler = handler;
      return this;
    }

    public boolean sendAtFrontOfQueue(Handler handler) {
      boolean success = handler.sendMessageAtFrontOfQueue(checkNotNull(message));
      recycle();
      return success;
    }

    @Override
    public void sendToTarget() {
      checkNotNull(message).sendToTarget();
      recycle();
    }

    @Override
    public HandlerWrapper getTarget() {
      return checkNotNull(handler);
    }

    private void recycle() {
      message = null;
      handler = null;
      recycleMessage(this);
    }
  }
}
