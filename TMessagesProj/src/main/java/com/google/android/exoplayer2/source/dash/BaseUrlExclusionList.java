/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.google.android.exoplayer2.source.dash;

import static com.google.android.exoplayer2.util.Util.castNonNull;
import static java.lang.Math.max;

import android.os.SystemClock;
import android.util.Pair;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.source.dash.manifest.BaseUrl;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Holds the state of {@link #exclude(BaseUrl, long) excluded} base URLs to be used to {@link
 * #selectBaseUrl(List) select} a base URL based on these exclusions.
 */
public final class BaseUrlExclusionList {

  private final Map<String, Long> excludedServiceLocations;
  private final Map<Integer, Long> excludedPriorities;
  private final Map<List<Pair<String, Integer>>, BaseUrl> selectionsTaken = new HashMap<>();
  private final Random random;

  /** Creates an instance. */
  public BaseUrlExclusionList() {
    this(new Random());
  }

  /** Creates an instance with the given {@link Random}. */
  @VisibleForTesting
  /* package */ BaseUrlExclusionList(Random random) {
    this.random = random;
    excludedServiceLocations = new HashMap<>();
    excludedPriorities = new HashMap<>();
  }

  /**
   * Excludes the given base URL.
   *
   * @param baseUrlToExclude The base URL to exclude.
   * @param exclusionDurationMs The duration of exclusion, in milliseconds.
   */
  public void exclude(BaseUrl baseUrlToExclude, long exclusionDurationMs) {
    long excludeUntilMs = SystemClock.elapsedRealtime() + exclusionDurationMs;
    addExclusion(baseUrlToExclude.serviceLocation, excludeUntilMs, excludedServiceLocations);
    if (baseUrlToExclude.priority != BaseUrl.PRIORITY_UNSET) {
      addExclusion(baseUrlToExclude.priority, excludeUntilMs, excludedPriorities);
    }
  }

  /**
   * Selects the base URL to use from the given list.
   *
   * <p>The list is reduced by service location and priority of base URLs that have been passed to
   * {@link #exclude(BaseUrl, long)}. The base URL to use is then selected from the remaining base
   * URLs by priority and weight.
   *
   * @param baseUrls The list of {@link BaseUrl base URLs} to select from.
   * @return The selected base URL after exclusion or null if all elements have been excluded.
   */
  @Nullable
  public BaseUrl selectBaseUrl(List<BaseUrl> baseUrls) {
    List<BaseUrl> includedBaseUrls = applyExclusions(baseUrls);
    if (includedBaseUrls.size() < 2) {
      return Iterables.getFirst(includedBaseUrls, /* defaultValue= */ null);
    }
    // Sort by priority and service location to make the sort order of the candidates deterministic.
    Collections.sort(includedBaseUrls, BaseUrlExclusionList::compareBaseUrl);
    // Get candidates of the lowest priority from the head of the sorted list.
    List<Pair<String, Integer>> candidateKeys = new ArrayList<>();
    int lowestPriority = includedBaseUrls.get(0).priority;
    for (int i = 0; i < includedBaseUrls.size(); i++) {
      BaseUrl baseUrl = includedBaseUrls.get(i);
      if (lowestPriority != baseUrl.priority) {
        if (candidateKeys.size() == 1) {
          // Only a single candidate of lowest priority; no choice.
          return includedBaseUrls.get(0);
        }
        break;
      }
      candidateKeys.add(new Pair<>(baseUrl.serviceLocation, baseUrl.weight));
    }
    // Check whether selection has already been taken.
    @Nullable BaseUrl baseUrl = selectionsTaken.get(candidateKeys);
    if (baseUrl == null) {
      // Weighted random selection from multiple candidates of the same priority.
      baseUrl = selectWeighted(includedBaseUrls.subList(0, candidateKeys.size()));
      // Remember the selection taken for later.
      selectionsTaken.put(candidateKeys, baseUrl);
    }
    return baseUrl;
  }

  /**
   * Returns the number of priority levels for the given list of base URLs after exclusion.
   *
   * @param baseUrls The list of base URLs.
   * @return The number of priority levels after exclusion.
   */
  public int getPriorityCountAfterExclusion(List<BaseUrl> baseUrls) {
    Set<Integer> priorities = new HashSet<>();
    List<BaseUrl> includedBaseUrls = applyExclusions(baseUrls);
    for (int i = 0; i < includedBaseUrls.size(); i++) {
      priorities.add(includedBaseUrls.get(i).priority);
    }
    return priorities.size();
  }

  /**
   * Returns the number of priority levels of the given list of base URLs.
   *
   * @param baseUrls The list of base URLs.
   * @return The number of priority levels before exclusion.
   */
  public static int getPriorityCount(List<BaseUrl> baseUrls) {
    Set<Integer> priorities = new HashSet<>();
    for (int i = 0; i < baseUrls.size(); i++) {
      priorities.add(baseUrls.get(i).priority);
    }
    return priorities.size();
  }

  /** Resets the state. */
  public void reset() {
    excludedServiceLocations.clear();
    excludedPriorities.clear();
    selectionsTaken.clear();
  }

  // Internal methods.

  private List<BaseUrl> applyExclusions(List<BaseUrl> baseUrls) {
    long nowMs = SystemClock.elapsedRealtime();
    removeExpiredExclusions(nowMs, excludedServiceLocations);
    removeExpiredExclusions(nowMs, excludedPriorities);
    List<BaseUrl> includedBaseUrls = new ArrayList<>();
    for (int i = 0; i < baseUrls.size(); i++) {
      BaseUrl baseUrl = baseUrls.get(i);
      if (!excludedServiceLocations.containsKey(baseUrl.serviceLocation)
          && !excludedPriorities.containsKey(baseUrl.priority)) {
        includedBaseUrls.add(baseUrl);
      }
    }
    return includedBaseUrls;
  }

  private BaseUrl selectWeighted(List<BaseUrl> candidates) {
    int totalWeight = 0;
    for (int i = 0; i < candidates.size(); i++) {
      totalWeight += candidates.get(i).weight;
    }
    int randomChoice = random.nextInt(/* bound= */ totalWeight);
    totalWeight = 0;
    for (int i = 0; i < candidates.size(); i++) {
      BaseUrl baseUrl = candidates.get(i);
      totalWeight += baseUrl.weight;
      if (randomChoice < totalWeight) {
        return baseUrl;
      }
    }
    return Iterables.getLast(candidates);
  }

  private static <T> void addExclusion(
      T toExclude, long excludeUntilMs, Map<T, Long> currentExclusions) {
    if (currentExclusions.containsKey(toExclude)) {
      excludeUntilMs = max(excludeUntilMs, castNonNull(currentExclusions.get(toExclude)));
    }
    currentExclusions.put(toExclude, excludeUntilMs);
  }

  private static <T> void removeExpiredExclusions(long nowMs, Map<T, Long> exclusions) {
    List<T> expiredExclusions = new ArrayList<>();
    for (Map.Entry<T, Long> entries : exclusions.entrySet()) {
      if (entries.getValue() <= nowMs) {
        expiredExclusions.add(entries.getKey());
      }
    }
    for (int i = 0; i < expiredExclusions.size(); i++) {
      exclusions.remove(expiredExclusions.get(i));
    }
  }

  /** Compare by priority and service location. */
  private static int compareBaseUrl(BaseUrl a, BaseUrl b) {
    int compare = Integer.compare(a.priority, b.priority);
    return compare != 0 ? compare : a.serviceLocation.compareTo(b.serviceLocation);
  }
}
