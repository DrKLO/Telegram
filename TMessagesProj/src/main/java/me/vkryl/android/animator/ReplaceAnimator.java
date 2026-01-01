/*
 * This file is a part of X-Android
 * Copyright © Vyacheslav Krylov 2014
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

package me.vkryl.android.animator;

import android.view.animation.Interpolator;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.Iterator;

public class ReplaceAnimator<T> implements Iterable<ListAnimator.Entry<T>> {
  public interface Callback {
    void onItemChanged (ReplaceAnimator<?> animator);

    default boolean hasChanges (ReplaceAnimator<?> animator) {
      return false;
    }
    default void onForceApplyChanges (ReplaceAnimator<?> animator) { }
    default void onPrepareMetadataAnimation (ReplaceAnimator<?> animator) { }
    default boolean onApplyMetadataAnimation (ReplaceAnimator<?> animator, float factor) {
      return false;
    }
    default void onFinishMetadataAnimation (ReplaceAnimator<?> animator, boolean applyFuture) { }
  }

  private final ListAnimator<T> list;

  public ReplaceAnimator (@NonNull Callback callback) {
    this(callback, null, 0);
  }

  public ReplaceAnimator (@NonNull Callback callback, Interpolator interpolator, long duration) {
    this.list = new ListAnimator<>(new ListAnimator.Callback() {
      @Override
      public void onItemsChanged (ListAnimator<?> animator) {
        callback.onItemChanged(ReplaceAnimator.this);
      }

      @Override
      public boolean hasChanges (ListAnimator<?> animator) {
        return callback.hasChanges(ReplaceAnimator.this);
      }

      @Override
      public void onForceApplyChanges (ListAnimator<?> animator) {
        callback.onForceApplyChanges(ReplaceAnimator.this);
      }

      @Override
      public void onPrepareMetadataAnimation (ListAnimator<?> animator) {
        callback.onPrepareMetadataAnimation(ReplaceAnimator.this);
      }

      @Override
      public boolean onApplyMetadataAnimation (ListAnimator<?> animator, float factor) {
        return callback.onApplyMetadataAnimation(ReplaceAnimator.this, factor);
      }

      @Override
      public void onFinishMetadataAnimation (ListAnimator<?> animator, boolean applyFuture) {
        callback.onFinishMetadataAnimation(ReplaceAnimator.this, applyFuture);
      }
    }, interpolator, duration);
  }

  public void replace (T item, boolean animated) {
    this.list.reset(item != null ? Collections.singletonList(item) : null, animated);
  }

  public ListAnimator.Entry<T> singleton () {
    ListAnimator.Entry<T> singleton = null;
    for (ListAnimator.Entry<T> entry : this.list) {
      if (entry.isAffectingList()) {
        if (singleton == null) {
          singleton = entry;
        } else {
          throw new IllegalStateException(); // Should be always either 0, or 1.
        }
      }
    }
    return singleton;
  }

  public T singletonItem () {
    ListAnimator.Entry<T> entry = singleton();
    return entry != null ? entry.item : null;
  }

  public boolean isEmpty () {
    return singleton() == null;
  }

  public ListAnimator.Metadata getMetadata () {
    return list.getMetadata();
  }

  public void measure (boolean animated) {
    list.measure(animated);
  }

  public void applyAnimation (float factor) {
    this.list.applyAnimation(factor);
  }

  public void clear (boolean animated) {
    this.list.clear(animated);
  }

  public void stopAnimation (boolean applyFuture) {
    this.list.stopAnimation(applyFuture);
  }

  @NonNull
  @Override
  public Iterator<ListAnimator.Entry<T>> iterator() {
    return list.iterator();
  }
}
