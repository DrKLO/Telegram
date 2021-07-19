/*
 *  Copyright 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.webrtc;

import androidx.annotation.Nullable;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implementation of RefCounted that executes a Runnable once the ref count reaches zero.
 */
class RefCountDelegate implements RefCounted {
  private final AtomicInteger refCount = new AtomicInteger(1);
  private final @Nullable Runnable releaseCallback;

  /**
   * @param releaseCallback Callback that will be executed once the ref count reaches zero.
   */
  public RefCountDelegate(@Nullable Runnable releaseCallback) {
    this.releaseCallback = releaseCallback;
  }

  @Override
  public void retain() {
    int updated_count = refCount.incrementAndGet();
    if (updated_count < 2) {
      throw new IllegalStateException("retain() called on an object with refcount < 1");
    }
  }

  @Override
  public void release() {
    int updated_count = refCount.decrementAndGet();
    if (updated_count < 0) {
      throw new IllegalStateException("release() called on an object with refcount < 1");
    }
    if (updated_count == 0 && releaseCallback != null) {
      releaseCallback.run();
    }
  }

  /**
   * Tries to retain the object. Can be used in scenarios where it is unknown if the object has
   * already been released. Returns true if successful or false if the object was already released.
   */
  boolean safeRetain() {
    int currentRefCount = refCount.get();
    while (currentRefCount != 0) {
      if (refCount.weakCompareAndSet(currentRefCount, currentRefCount + 1)) {
        return true;
      }
      currentRefCount = refCount.get();
    }
    return false;
  }
}
