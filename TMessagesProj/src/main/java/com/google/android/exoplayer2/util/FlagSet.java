/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static com.google.android.exoplayer2.util.Assertions.checkIndex;
import static com.google.android.exoplayer2.util.Assertions.checkState;

import android.util.SparseBooleanArray;
import androidx.annotation.Nullable;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

/**
 * A set of integer flags.
 *
 * <p>Intended for usages where the number of flags may exceed 32 and can no longer be represented
 * by an IntDef.
 *
 * <p>Instances are immutable.
 */
public final class FlagSet {

  /** A builder for {@link FlagSet} instances. */
  public static final class Builder {

    private final SparseBooleanArray flags;

    private boolean buildCalled;

    /** Creates a builder. */
    public Builder() {
      flags = new SparseBooleanArray();
    }

    /**
     * Adds a flag.
     *
     * @param flag A flag.
     * @return This builder.
     * @throws IllegalStateException If {@link #build()} has already been called.
     */
    @CanIgnoreReturnValue
    public Builder add(int flag) {
      checkState(!buildCalled);
      flags.append(flag, /* value= */ true);
      return this;
    }

    /**
     * Adds a flag if the provided condition is true. Does nothing otherwise.
     *
     * @param flag A flag.
     * @param condition A condition.
     * @return This builder.
     * @throws IllegalStateException If {@link #build()} has already been called.
     */
    @CanIgnoreReturnValue
    public Builder addIf(int flag, boolean condition) {
      if (condition) {
        return add(flag);
      }
      return this;
    }

    /**
     * Adds flags.
     *
     * @param flags The flags to add.
     * @return This builder.
     * @throws IllegalStateException If {@link #build()} has already been called.
     */
    @CanIgnoreReturnValue
    public Builder addAll(int... flags) {
      for (int flag : flags) {
        add(flag);
      }
      return this;
    }

    /**
     * Adds {@link FlagSet flags}.
     *
     * @param flags The set of flags to add.
     * @return This builder.
     * @throws IllegalStateException If {@link #build()} has already been called.
     */
    @CanIgnoreReturnValue
    public Builder addAll(FlagSet flags) {
      for (int i = 0; i < flags.size(); i++) {
        add(flags.get(i));
      }
      return this;
    }

    /**
     * Removes a flag.
     *
     * @param flag A flag.
     * @return This builder.
     * @throws IllegalStateException If {@link #build()} has already been called.
     */
    @CanIgnoreReturnValue
    public Builder remove(int flag) {
      checkState(!buildCalled);
      flags.delete(flag);
      return this;
    }

    /**
     * Removes a flag if the provided condition is true. Does nothing otherwise.
     *
     * @param flag A flag.
     * @param condition A condition.
     * @return This builder.
     * @throws IllegalStateException If {@link #build()} has already been called.
     */
    @CanIgnoreReturnValue
    public Builder removeIf(int flag, boolean condition) {
      if (condition) {
        return remove(flag);
      }
      return this;
    }

    /**
     * Removes flags.
     *
     * @param flags The flags to remove.
     * @return This builder.
     * @throws IllegalStateException If {@link #build()} has already been called.
     */
    @CanIgnoreReturnValue
    public Builder removeAll(int... flags) {
      for (int flag : flags) {
        remove(flag);
      }
      return this;
    }

    /**
     * Builds an {@link FlagSet} instance.
     *
     * @throws IllegalStateException If this method has already been called.
     */
    public FlagSet build() {
      checkState(!buildCalled);
      buildCalled = true;
      return new FlagSet(flags);
    }
  }

  // A SparseBooleanArray is used instead of a Set to avoid auto-boxing the flag values.
  private final SparseBooleanArray flags;

  private FlagSet(SparseBooleanArray flags) {
    this.flags = flags;
  }

  /**
   * Returns whether the set contains the given flag.
   *
   * @param flag The flag.
   * @return Whether the set contains the flag.
   */
  public boolean contains(int flag) {
    return flags.get(flag);
  }

  /**
   * Returns whether the set contains at least one of the given flags.
   *
   * @param flags The flags.
   * @return Whether the set contains at least one of the flags.
   */
  public boolean containsAny(int... flags) {
    for (int flag : flags) {
      if (contains(flag)) {
        return true;
      }
    }
    return false;
  }

  /** Returns the number of flags in this set. */
  public int size() {
    return flags.size();
  }

  /**
   * Returns the flag at the given index.
   *
   * @param index The index. Must be between 0 (inclusive) and {@link #size()} (exclusive).
   * @return The flag at the given index.
   * @throws IndexOutOfBoundsException If index is outside the allowed range.
   */
  public int get(int index) {
    checkIndex(index, /* start= */ 0, /* limit= */ size());
    return flags.keyAt(index);
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof FlagSet)) {
      return false;
    }
    FlagSet that = (FlagSet) o;
    if (Util.SDK_INT < 24) {
      // SparseBooleanArray.equals() is not implemented on API levels below 24.
      if (size() != that.size()) {
        return false;
      }
      for (int i = 0; i < size(); i++) {
        if (get(i) != that.get(i)) {
          return false;
        }
      }
      return true;
    } else {
      return flags.equals(that.flags);
    }
  }

  @Override
  public int hashCode() {
    if (Util.SDK_INT < 24) {
      // SparseBooleanArray.hashCode() is not implemented on API levels below 24.
      int hashCode = size();
      for (int i = 0; i < size(); i++) {
        hashCode = 31 * hashCode + get(i);
      }
      return hashCode;
    } else {
      return flags.hashCode();
    }
  }
}
