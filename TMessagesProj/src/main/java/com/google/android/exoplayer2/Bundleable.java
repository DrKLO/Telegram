/*
 * Copyright 2021 The Android Open Source Project
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
package com.google.android.exoplayer2;

import android.os.Bundle;

/**
 * Interface for classes whose instance can be stored in a {@link Bundle} by {@link #toBundle()} and
 * can be restored from the {@link Bundle} by using the static {@code CREATOR} field that implements
 * {@link Bundleable.Creator}.
 *
 * <p>For example, a {@link Bundleable} class {@code Foo} supports the following:
 *
 * <pre>{@code
 * Foo foo = ...;
 * Bundle fooBundle = foo.toBundle();
 * Foo restoredFoo = Foo.CREATOR.fromBundle(fooBundle);
 * assertThat(restoredFoo).isEqualTo(foo);
 * }</pre>
 */
public interface Bundleable {

  /** Returns a {@link Bundle} representing the information stored in this object. */
  Bundle toBundle();

  /** Interface for the static {@code CREATOR} field of {@link Bundleable} classes. */
  interface Creator<T extends Bundleable> {

    /**
     * Restores a {@link Bundleable} instance from a {@link Bundle} produced by {@link
     * Bundleable#toBundle()}.
     *
     * <p>It guarantees the compatibility of {@link Bundle} representations produced by different
     * versions of {@link Bundleable#toBundle()} by providing best default values for missing
     * fields. It throws an exception if any essential fields are missing.
     */
    T fromBundle(Bundle bundle);
  }
}
