/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.google.android.exoplayer2.offline;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.offline.DownloadState.State;
import com.google.android.exoplayer2.util.Assertions;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;

/** {@link DownloadIndex} related utility methods. */
public final class DownloadIndexUtil {

  /** An interface to provide custom download ids during ActionFile upgrade. */
  public interface DownloadIdProvider {

    /**
     * Returns a custom download id for given action.
     *
     * @param downloadAction The action which is an id requested for.
     * @return A custom download id for given action.
     */
    String getId(DownloadAction downloadAction);
  }

  private DownloadIndexUtil() {}

  /**
   * Upgrades an {@link ActionFile} to {@link DownloadIndex}.
   *
   * <p>This method shouldn't be called while {@link DownloadIndex} is used by {@link
   * DownloadManager}.
   *
   * @param actionFile The action file to upgrade.
   * @param downloadIndex Actions are converted to {@link DownloadState}s and stored in this index.
   * @param downloadIdProvider A nullable custom download id provider.
   * @throws IOException If there is an error during loading actions.
   */
  public static void upgradeActionFile(
      ActionFile actionFile,
      DownloadIndex downloadIndex,
      @Nullable DownloadIdProvider downloadIdProvider)
      throws IOException {
    if (downloadIdProvider == null) {
      downloadIdProvider = downloadAction -> downloadAction.id;
    }
    for (DownloadAction action : actionFile.load()) {
      addAction(downloadIndex, downloadIdProvider.getId(action), action);
    }
  }

  /**
   * Converts a {@link DownloadAction} to {@link DownloadState} and stored in the given {@link
   * DownloadIndex}.
   *
   * <p>This method shouldn't be called while {@link DownloadIndex} is used by {@link
   * DownloadManager}.
   *
   * @param downloadIndex The action is converted to {@link DownloadState} and stored in this index.
   * @param id A nullable custom download id which overwrites {@link DownloadAction#id}.
   * @param action The action to be stored in {@link DownloadIndex}.
   */
  public static void addAction(
      DownloadIndex downloadIndex, @Nullable String id, DownloadAction action) {
    DownloadState downloadState = downloadIndex.getDownloadState(id != null ? id : action.id);
    if (downloadState != null) {
      downloadState = merge(downloadState, action);
    } else {
      downloadState = convert(action);
    }
    downloadIndex.putDownloadState(downloadState);
  }

  private static DownloadState merge(DownloadState downloadState, DownloadAction action) {
    Assertions.checkArgument(action.type.equals(downloadState.type));
    @State int newState;
    if (action.isRemoveAction) {
      newState = DownloadState.STATE_REMOVING;
    } else {
      if (downloadState.state == DownloadState.STATE_REMOVING
          || downloadState.state == DownloadState.STATE_RESTARTING) {
        newState = DownloadState.STATE_RESTARTING;
      } else if (downloadState.state == DownloadState.STATE_STOPPED) {
        newState = DownloadState.STATE_STOPPED;
      } else {
        newState = DownloadState.STATE_QUEUED;
      }
    }
    HashSet<StreamKey> keys = new HashSet<>(action.keys);
    Collections.addAll(keys, downloadState.streamKeys);
    StreamKey[] newKeys = keys.toArray(new StreamKey[0]);
    return new DownloadState(
        downloadState.id,
        downloadState.type,
        action.uri,
        action.customCacheKey,
        newState,
        /* downloadPercentage= */ C.PERCENTAGE_UNSET,
        downloadState.downloadedBytes,
        /* totalBytes= */ C.LENGTH_UNSET,
        downloadState.failureReason,
        downloadState.stopFlags,
        downloadState.startTimeMs,
        downloadState.updateTimeMs,
        newKeys,
        action.data);
  }

  private static DownloadState convert(DownloadAction action) {
    long currentTimeMs = System.currentTimeMillis();
    return new DownloadState(
        action.id,
        action.type,
        action.uri,
        action.customCacheKey,
        /* state= */ action.isRemoveAction
            ? DownloadState.STATE_REMOVING
            : DownloadState.STATE_QUEUED,
        /* downloadPercentage= */ C.PERCENTAGE_UNSET,
        /* downloadedBytes= */ 0,
        /* totalBytes= */ C.LENGTH_UNSET,
        DownloadState.FAILURE_REASON_NONE,
        /* stopFlags= */ 0,
        /* startTimeMs= */ currentTimeMs,
        /* updateTimeMs= */ currentTimeMs,
        action.keys.toArray(new StreamKey[0]),
        action.data);
  }
}
