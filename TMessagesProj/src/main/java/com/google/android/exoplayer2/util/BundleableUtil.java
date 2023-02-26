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
package com.google.android.exoplayer2.util;

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Util.castNonNull;

import android.os.Bundle;
import android.util.SparseArray;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Bundleable;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Utilities for {@link Bundleable}. */
public final class BundleableUtil {

  /** Converts a list of {@link Bundleable} to a list {@link Bundle}. */
  public static <T extends Bundleable> ImmutableList<Bundle> toBundleList(List<T> bundleableList) {
    ImmutableList.Builder<Bundle> builder = ImmutableList.builder();
    for (int i = 0; i < bundleableList.size(); i++) {
      Bundleable bundleable = bundleableList.get(i);
      builder.add(bundleable.toBundle());
    }
    return builder.build();
  }

  /** Converts a list of {@link Bundle} to a list of {@link Bundleable}. */
  public static <T extends Bundleable> ImmutableList<T> fromBundleList(
      Bundleable.Creator<T> creator, List<Bundle> bundleList) {
    ImmutableList.Builder<T> builder = ImmutableList.builder();
    for (int i = 0; i < bundleList.size(); i++) {
      Bundle bundle = checkNotNull(bundleList.get(i)); // Fail fast during parsing.
      T bundleable = creator.fromBundle(bundle);
      builder.add(bundleable);
    }
    return builder.build();
  }

  /**
   * Converts a collection of {@link Bundleable} to an {@link ArrayList} of {@link Bundle} so that
   * the returned list can be put to {@link Bundle} using {@link Bundle#putParcelableArrayList}
   * conveniently.
   */
  public static <T extends Bundleable> ArrayList<Bundle> toBundleArrayList(
      Collection<T> bundleables) {
    ArrayList<Bundle> arrayList = new ArrayList<>(bundleables.size());
    for (T element : bundleables) {
      arrayList.add(element.toBundle());
    }
    return arrayList;
  }

  /**
   * Converts a {@link SparseArray} of {@link Bundle} to a {@link SparseArray} of {@link
   * Bundleable}.
   */
  public static <T extends Bundleable> SparseArray<T> fromBundleSparseArray(
      Bundleable.Creator<T> creator, SparseArray<Bundle> bundleSparseArray) {
    SparseArray<T> result = new SparseArray<>(bundleSparseArray.size());
    for (int i = 0; i < bundleSparseArray.size(); i++) {
      result.put(bundleSparseArray.keyAt(i), creator.fromBundle(bundleSparseArray.valueAt(i)));
    }
    return result;
  }

  /**
   * Converts a {@link SparseArray} of {@link Bundleable} to an {@link SparseArray} of {@link
   * Bundle} so that the returned {@link SparseArray} can be put to {@link Bundle} using {@link
   * Bundle#putSparseParcelableArray} conveniently.
   */
  public static <T extends Bundleable> SparseArray<Bundle> toBundleSparseArray(
      SparseArray<T> bundleableSparseArray) {
    SparseArray<Bundle> sparseArray = new SparseArray<>(bundleableSparseArray.size());
    for (int i = 0; i < bundleableSparseArray.size(); i++) {
      sparseArray.put(bundleableSparseArray.keyAt(i), bundleableSparseArray.valueAt(i).toBundle());
    }
    return sparseArray;
  }

  /**
   * Sets the application class loader to the given {@link Bundle} if no class loader is present.
   *
   * <p>This assumes that all classes unparceled from {@code bundle} are sharing the class loader of
   * {@code BundleableUtils}.
   */
  public static void ensureClassLoader(@Nullable Bundle bundle) {
    if (bundle != null) {
      bundle.setClassLoader(castNonNull(BundleableUtil.class.getClassLoader()));
    }
  }

  private BundleableUtil() {}
}
