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

import android.graphics.RectF;
import android.view.animation.Interpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.math.MathUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import me.vkryl.core.ArrayUtils;
import me.vkryl.core.lambda.Destroyable;

public final class ListAnimator<T> implements Iterable<ListAnimator.Entry<T>> {
  public static class Entry<T> implements Comparable<Entry<T>>/*, Animatable*/ {
    public final T item;
    private int index;

    private final VariableFloat position;
    private final VariableFloat visibility;
    private final VariableRect measuredPositionRect;
    private final VariableFloat measuredSpacingStart;

    public Entry (T item, int index, boolean isVisible) {
      this.item = item;
      this.index = index;
      this.visibility = new VariableFloat(isVisible ? 1f : 0f);
      this.position = new VariableFloat(index);
      this.measuredPositionRect = new VariableRect();
      this.measuredSpacingStart = new VariableFloat(0);
      finishAnimation(false);
    }

    public boolean isJunk () {
      return getVisibility() == 0f && !isAffectingList();
    }

    private boolean isBeingRemoved = false;

    private void onPrepareRemove () {
      visibility.setTo(0f);
      isBeingRemoved = true;
    }

    private void onPrepareAppear () {
      visibility.setTo(1f);
      isBeingRemoved = false;
    }

    @Override
    public int compareTo(Entry<T> o) {
      return Integer.compare(index, o.index);
    }

    public float getPosition () {
      return position.get();
    }

    public int getIndex () {
      return index;
    }

    public float getVisibility () {
      return MathUtils.clamp(visibility.get(), 0f, 1f);
    }

    // State

    public boolean isAffectingList () {
      return !isBeingRemoved;
    }

    private void onRecycled () {
      if (item instanceof Destroyable) {
        ((Destroyable) item).performDestroy();
      }
    }

    // Measured

    public RectF getRectF () {
      return measuredPositionRect.toRectF();
    }

    public float getSpacingStart () {
      return measuredSpacingStart.get();
    }

    // Animation

    private void finishAnimation (boolean applyFutureState) {
      this.position.finishAnimation(applyFutureState);
      this.visibility.finishAnimation(applyFutureState);
      this.measuredPositionRect.finishAnimation(applyFutureState);
      this.measuredSpacingStart.finishAnimation(applyFutureState);
      if (item instanceof Animatable) {
        ((Animatable) this.item).finishAnimation(applyFutureState);
      }
    }

    private boolean applyAnimation (float factor) {
      boolean haveChanges;
      haveChanges = position.applyAnimation(factor);
      haveChanges = visibility.applyAnimation(factor) || haveChanges;
      haveChanges = measuredPositionRect.applyAnimation(factor) || haveChanges;
      haveChanges = measuredSpacingStart.applyAnimation(factor) || haveChanges;
      if (item instanceof Animatable) {
        haveChanges = ((Animatable) item).applyAnimation(factor) || haveChanges;
      }
      return haveChanges;
    }
  }

  public interface Measurable {
    default int getSpacingStart (boolean isFirst) { return 0; }
    default int getSpacingEnd (boolean isLast) { return 0; }

    int getWidth ();
    int getHeight ();
  }

  public interface MetadataCallback {
    default boolean hasChanges (ListAnimator<?> animator) {
      return false;
    }
    default void onForceApplyChanges (ListAnimator<?> animator) { }
    default void onPrepareMetadataAnimation (ListAnimator<?> animator) { }
    default boolean onApplyMetadataAnimation (ListAnimator<?> animator, float factor) {
      return false;
    }
    default void onFinishMetadataAnimation (ListAnimator<?> animator, boolean applyFuture) { }
  }

  public interface Callback extends MetadataCallback {
    void onItemsChanged (ListAnimator<?> animator);
  }

  public static class Metadata {
    private final ListAnimator<?> context;
    private final MetadataCallback metadataCallback;

    private final VariableFloat size = new VariableFloat(0);
    private final VariableFloat totalVisibility = new VariableFloat(0);
    private final VariableFloat maxItemWidth = new VariableFloat(0f);
    private final VariableFloat maxItemHeight = new VariableFloat(0f);
    private final VariableFloat totalWidth = new VariableFloat(0f);
    private final VariableFloat totalHeight = new VariableFloat(0f);

    private Metadata (ListAnimator<?> context, @NonNull MetadataCallback metadataCallback) {
      this.context = context;
      this.metadataCallback = metadataCallback;
    }

    public boolean applyAnimation (float factor) {
      boolean haveChanges;
      haveChanges = size.applyAnimation(factor);
      haveChanges = maxItemWidth.applyAnimation(factor) || haveChanges;
      haveChanges = maxItemHeight.applyAnimation(factor) || haveChanges;
      haveChanges = totalWidth.applyAnimation(factor) || haveChanges;
      haveChanges = totalHeight.applyAnimation(factor) || haveChanges;
      haveChanges = totalVisibility.applyAnimation(factor) || haveChanges;
      haveChanges = metadataCallback.onApplyMetadataAnimation(context, factor) || haveChanges;
      return haveChanges;
    }

    public void finishAnimation (boolean applyFuture) {
      size.finishAnimation(applyFuture);
      maxItemWidth.finishAnimation(applyFuture);
      maxItemHeight.finishAnimation(applyFuture);
      totalWidth.finishAnimation(applyFuture);
      totalHeight.finishAnimation(applyFuture);
      totalVisibility.finishAnimation(applyFuture);
      metadataCallback.onFinishMetadataAnimation(context, applyFuture);
    }

    private void setSize (int size, boolean animated) {
      if (animated) {
        this.size.setTo(size);
        this.totalVisibility.setTo(size > 0 ? 1.0f : 0.0f);
      } else {
        this.size.set(size);
        this.totalVisibility.set(size > 0 ? 1.0f : 0.0f);
      }
    }

    public float getMaximumItemWidth () {
      return maxItemWidth.get();
    }

    public float getMaximumItemHeight () {
      return maxItemHeight.get();
    }

    public float getTotalWidth () {
      return totalWidth.get();
    }

    public float getTotalHeight () {
      return totalHeight.get();
    }

    public float getSize () {
      return size.get();
    }

    public float getTotalVisibility () {
      return totalVisibility.get();
    }
  }

  private final Callback callback;
  private final ArrayList<Entry<T>> entries;
  private final @Nullable FactorAnimator animator;
  private final Metadata metadata;
  private final ArrayList<Entry<T>> actualList; // list after all animations finished

  public ListAnimator (@NonNull Callback callback) {
    this(callback, null, 0);
  }

  public ListAnimator (@NonNull Callback callback, @Nullable Interpolator interpolator, long duration) {
    this.callback = callback;
    this.metadata = new Metadata(this, callback);
    this.entries = new ArrayList<>();
    this.actualList = new ArrayList<>();
    if (interpolator != null && duration > 0) {
      this.animator = new FactorAnimator(0, new FactorAnimator.Target() {
        @Override
        public void onFactorChanged(int id, float factor, float fraction, FactorAnimator callee) {
          applyAnimation(factor);
        }

        @Override
        public void onFactorChangeFinished(int id, float finalFactor, FactorAnimator callee) {
          applyAnimation(finalFactor);
        }
      }, interpolator, duration);
    } else {
      this.animator = null;
    }
  }

  public int size () {
    return entries.size();
  }

  public Entry<T> getEntry (int index) {
    return entries.get(index);
  }

  public Metadata getMetadata () {
    return metadata;
  }

  public void applyAnimation (float factor) {
    boolean haveChanges = metadata.applyAnimation(factor);
    for (Entry<T> entry : entries) {
      haveChanges = entry.applyAnimation(factor) || haveChanges;
    }
    if (haveChanges) {
      callback.onItemsChanged(ListAnimator.this);
      if (factor == 1f) {
        removeJunk(true);
      }
    }
  }

  @NonNull
  @Override
  public Iterator<Entry<T>> iterator() {
    return entries.iterator();
  }

  private void removeJunk (boolean applyFuture) {
    boolean haveRemovedEntries = false;
    for (int i = entries.size() - 1; i >= 0; i--) {
      Entry<T> entry = entries.get(i);
      entry.finishAnimation(applyFuture);
      if (entry.isJunk()) {
        entries.remove(i);
        entry.onRecycled();
        haveRemovedEntries = true;
      }
    }
    if (haveRemovedEntries) {
      entries.trimToSize();
    }
    metadata.finishAnimation(applyFuture);
  }

  public void stopAnimation (boolean applyFuture) {
    if (animator != null) {
      animator.cancel();
      removeJunk(applyFuture);
      animator.forceFactor(0f);
    } else {
      removeJunk(applyFuture);
    }
  }

  private int indexOfItem (T item) {
    int index = 0;
    if (item == null) {
      for (Entry<T> entry : entries) {
        if (entry.item == null)
          return index;
        index++;
      }
    } else {
      for (Entry<T> entry : entries) {
        if (item.equals(entry.item))
          return index;
        index++;
      }
    }
    return -1;
  }

  public void clear (boolean animated) {
    reset(null, animated);
  }

  private boolean foundListChanges;

  private void onBeforeListChanged () {
    if (!foundListChanges) {
      foundListChanges = true;
      stopAnimation(false);
    }
  }

  private void onApplyListChanges () {
    if (foundListChanges) {
      foundListChanges = false;
      if (animator != null) {
        animator.animateTo(1f);
      }
    } else {
      if (animator == null) {
        for (Entry<T> entry : entries) {
          entry.visibility.setFrom(entry.visibility.get());
          entry.position.setFrom(entry.position.get());
        }
      }
    }
  }

  public void measure (boolean animated) {
    if (!animated) {
      stopAnimation(true);
    }
    measureImpl(animated);
    if (animated) {
      onApplyListChanges();
    }
  }

  public void measureImpl (boolean animated) {
    int totalWidth = 0, totalHeight = 0;
    int maxItemWidth = 0, maxItemHeight = 0;
    for (Entry<T> entry : actualList) {
      if (entry.item instanceof Measurable) {
        Measurable measurable = (Measurable) entry.item;

        boolean isFirst = entry.index == 0;
        boolean isLast = entry.index + 1 == actualList.size();

        int spacingStart = measurable.getSpacingStart(isFirst);
        int spacingEnd = measurable.getSpacingEnd(isLast);

        int itemWidth = measurable.getWidth();
        int itemHeight = measurable.getHeight();

        int left = totalWidth;
        int top = totalHeight;

        int width = spacingStart + itemWidth + spacingEnd;
        int height = spacingStart + itemHeight + spacingEnd;

        totalWidth += width;
        totalHeight += height;

        if (animated && entry.getVisibility() > 0f) {
          if (entry.measuredPositionRect.differs(left, top, totalWidth, totalHeight)) {
            onBeforeListChanged();
            entry.measuredPositionRect.setTo(left, top, totalWidth, totalHeight);
          }
          if (entry.measuredSpacingStart.differs(spacingStart)) {
            onBeforeListChanged();
            entry.measuredSpacingStart.setTo(spacingStart);
          }
        } else {
          entry.measuredPositionRect.set(left, top, totalWidth, totalHeight);
          entry.measuredSpacingStart.set(spacingStart);
        }

        maxItemWidth = Math.max(maxItemWidth, itemWidth);
        maxItemHeight = Math.max(maxItemHeight, itemHeight);
      }
    }

    boolean haveChanges = false;
    if (animated) {
      for (Entry<T> entry : entries) {
        if (entry.item instanceof Animatable && ((Animatable) entry.item).hasChanges()) {
          haveChanges = true;
          break;
        }
      }
    }
    if (haveChanges) {
      onBeforeListChanged();
    }

    for (Entry<T> entry : entries) {
      if (entry.item instanceof Animatable) {
        Animatable animatable = (Animatable) entry.item;
        if (animated) {
          if (animatable.hasChanges()) {
            animatable.prepareChanges();
          }
        } else {
          animatable.applyChanges();
        }
      }
    }

    if (animated) {
      if (metadata.totalWidth.differs(totalWidth)) {
        onBeforeListChanged();
        metadata.totalWidth.setTo(totalWidth);
      }
      if (metadata.totalHeight.differs(totalHeight)) {
        onBeforeListChanged();
        metadata.totalHeight.setTo(totalHeight);
      }
      if (metadata.maxItemWidth.differs(maxItemWidth)) {
        onBeforeListChanged();
        metadata.maxItemWidth.setTo(maxItemWidth);
      }
      if (metadata.maxItemHeight.differs(maxItemHeight)) {
        onBeforeListChanged();
        metadata.maxItemHeight.setTo(maxItemHeight);
      }
      if (metadata.metadataCallback.hasChanges(this)) {
        onBeforeListChanged();
        metadata.metadataCallback.onPrepareMetadataAnimation(this);
      }
    } else {
      metadata.totalWidth.set(totalWidth);
      metadata.totalHeight.set(totalHeight);
      metadata.maxItemWidth.set(maxItemWidth);
      metadata.maxItemHeight.set(maxItemHeight);
      metadata.metadataCallback.onForceApplyChanges(this);
    }
  }

  public interface ResetCallback<T> {
    void onItemRemoved (T item); // item is now removing
    void onItemAdded (T item, boolean isReturned); // item is now adding
  }

  public void reset (@Nullable List<T> newItems, boolean animated) {
    reset(newItems, animated, null);
  }

  public boolean compareContents (@Nullable List<T> items) {
    if (items == null || items.isEmpty()) {
      return this.actualList.isEmpty();
    } else {
      if (this.actualList.size() != items.size())
        return false;
      for (int i = 0; i < items.size(); i++) {
        if (!this.actualList.get(i).equals(items.get(i)))
          return false;
      }
      return true;
    }
  }

  public void reset (@Nullable List<T> newItems, boolean animated, @Nullable ResetCallback<T> resetCallback) {
    if (!animated) {
      stopAnimation(false);
      for (int i = entries.size() - 1; i >= 0; i--) {
        entries.get(i).onRecycled();
      }
      entries.clear();
      actualList.clear();
      int size = newItems != null ? newItems.size() : 0;
      if (size > 0) {
        entries.ensureCapacity(size);
        actualList.ensureCapacity(size);
        for (T item : newItems) {
          Entry<T> entry = new Entry<>(item, actualList.size(), true);
          entries.add(entry);
          actualList.add(entry);
        }
        entries.trimToSize();
        actualList.trimToSize();
      }
      metadata.setSize(size, false);
      measureImpl(false);
      callback.onItemsChanged(this);
      return;
    }

    if (compareContents(newItems))
      return;

    onBeforeListChanged();

    boolean needSort = false;
    if (newItems != null && !newItems.isEmpty()) {
      // First, detect removals & changes

      int foundItemCount = 0;

      boolean needSortActual = false;
      for (int i = 0; i < entries.size(); i++) {
        Entry<T> entry = entries.get(i);
        int newIndex = newItems.indexOf(entry.item);
        if (newIndex != -1) {
          foundItemCount++;
          if (entry.position.differs(newIndex)) {
            onBeforeListChanged();
            entry.position.setTo(newIndex);
          }
          if (entry.index != newIndex) {
            entry.index = newIndex;
            needSort = true;
            needSortActual = needSortActual || entry.isAffectingList();
          }
          if (entry.visibility.differs(1f)) {
            onBeforeListChanged();
            entry.onPrepareAppear();
            actualList.add(entry);
            needSortActual = true;
            metadata.setSize(actualList.size(), true);
            if (resetCallback != null) {
              resetCallback.onItemAdded(entry.item, true);
            }
          }
        } else {
          if (entry.visibility.differs(0f)) {
            onBeforeListChanged();
            entry.onPrepareRemove();
            boolean removed = needSortActual ? actualList.remove(entry) : ArrayUtils.removeSorted(actualList, entry);
            if (!removed) {
              throw new IllegalArgumentException();
            }
            metadata.setSize(actualList.size(), true);
            if (resetCallback != null) {
              resetCallback.onItemRemoved(entry.item);
            }
          }
        }
      }

      if (needSortActual) {
        Collections.sort(actualList);
      }

      // Second, find additions

      if (foundItemCount < newItems.size()) {
        entries.ensureCapacity(entries.size() + (newItems.size() - foundItemCount));
        int index = 0;
        for (T newItem : newItems) {
          int existingIndex = indexOfItem(newItem);
          if (existingIndex == -1) {
            if (index != entries.size()) {
              needSort = true;
            }
            onBeforeListChanged();
            Entry<T> entry = new Entry<>(newItem, index, false);
            entry.onPrepareAppear();
            entries.add(entry);
            ArrayUtils.addSorted(actualList, entry);
            metadata.setSize(actualList.size(), true);
            if (resetCallback != null) {
              resetCallback.onItemAdded(entry.item, false);
            }
          }
          index++;
        }
      }
    } else {
      if (!foundListChanges) {
        // Triggering the removeJunk call
        for (Entry<T> entry : entries) {
          if (entry.visibility.differs(0f)) {
            onBeforeListChanged();
            break;
          }
        }
      }
      if (foundListChanges) {
        for (Entry<T> entry : entries) {
          if (entry.visibility.differs(0f)) {
            onBeforeListChanged();
            entry.onPrepareRemove();
            ArrayUtils.removeSorted(actualList, entry);
            metadata.setSize(actualList.size(), true);
            if (resetCallback != null) {
              resetCallback.onItemRemoved(entry.item);
            }
          }
        }
      }
    }

    // Then, sort and run animation, if needed

    if (needSort) {
      Collections.sort(entries);
    }

    measureImpl(true);

    onApplyListChanges();
  }

  public static class MeasurableEntry<T extends Measurable> implements Measurable, Destroyable {
    public final T content;

    protected MeasurableEntry (T content) {
      this.content = content;
    }

    @Override
    public final int getSpacingStart (boolean isFirst) {
      return content.getSpacingStart(isFirst);
    }

    @Override
    public final int getSpacingEnd (boolean isLast) {
      return content.getSpacingEnd(isLast);
    }

    @Override
    public final int getWidth () {
      return content.getWidth();
    }

    @Override
    public final int getHeight () {
      return content.getHeight();
    }

    @Override
    public void performDestroy () {
      if (content instanceof Destroyable) {
        ((Destroyable) content).performDestroy();
      }
    }
  }
}
