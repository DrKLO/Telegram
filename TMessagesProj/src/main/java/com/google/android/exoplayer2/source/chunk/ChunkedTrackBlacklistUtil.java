/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2.source.chunk;

import android.util.Log;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.upstream.HttpDataSource.InvalidResponseCodeException;

/**
 * Helper class for blacklisting tracks in a {@link TrackSelection} when 404 (Not Found) and 410
 * (Gone) HTTP response codes are encountered.
 */
public final class ChunkedTrackBlacklistUtil {

  /**
   * The default duration for which a track is blacklisted in milliseconds.
   */
  public static final long DEFAULT_TRACK_BLACKLIST_MS = 60000;

  private static final String TAG = "ChunkedTrackBlacklist";

  /**
   * Blacklists {@code trackSelectionIndex} in {@code trackSelection} for
   * {@link #DEFAULT_TRACK_BLACKLIST_MS} if {@code e} is an {@link InvalidResponseCodeException}
   * with {@link InvalidResponseCodeException#responseCode} equal to 404 or 410. Else does nothing.
   * Note that blacklisting will fail if the track is the only non-blacklisted track in the
   * selection.
   *
   * @param trackSelection The track selection.
   * @param trackSelectionIndex The index in the selection to consider blacklisting.
   * @param e The error to inspect.
   * @return Whether the track was blacklisted in the selection.
   */
  public static boolean maybeBlacklistTrack(TrackSelection trackSelection, int trackSelectionIndex,
      Exception e) {
    return maybeBlacklistTrack(trackSelection, trackSelectionIndex, e, DEFAULT_TRACK_BLACKLIST_MS);
  }

  /**
   * Blacklists {@code trackSelectionIndex} in {@code trackSelection} for
   * {@code blacklistDurationMs} if calling {@link #shouldBlacklist(Exception)} for {@code e}
   * returns true. Else does nothing. Note that blacklisting will fail if the track is the only
   * non-blacklisted track in the selection.
   *
   * @param trackSelection The track selection.
   * @param trackSelectionIndex The index in the selection to consider blacklisting.
   * @param e The error to inspect.
   * @param blacklistDurationMs The duration to blacklist the track for, if it is blacklisted.
   * @return Whether the track was blacklisted.
   */
  public static boolean maybeBlacklistTrack(TrackSelection trackSelection, int trackSelectionIndex,
      Exception e, long blacklistDurationMs) {
    if (shouldBlacklist(e)) {
      boolean blacklisted = trackSelection.blacklist(trackSelectionIndex, blacklistDurationMs);
      int responseCode = ((InvalidResponseCodeException) e).responseCode;
      if (blacklisted) {
        Log.w(TAG, "Blacklisted: duration=" + blacklistDurationMs + ", responseCode="
            + responseCode + ", format=" + trackSelection.getFormat(trackSelectionIndex));
      } else {
        Log.w(TAG, "Blacklisting failed (cannot blacklist last enabled track): responseCode="
            + responseCode + ", format=" + trackSelection.getFormat(trackSelectionIndex));
      }
      return blacklisted;
    }
    return false;
  }

  /**
   * Returns whether a loading error is an {@link InvalidResponseCodeException} with
   * {@link InvalidResponseCodeException#responseCode} equal to 404 or 410.
   *
   * @param e The loading error.
   * @return Wheter the loading error is an {@link InvalidResponseCodeException} with
   *     {@link InvalidResponseCodeException#responseCode} equal to 404 or 410.
   */
  public static boolean shouldBlacklist(Exception e) {
    if (e instanceof InvalidResponseCodeException) {
      int responseCode = ((InvalidResponseCodeException) e).responseCode;
      return responseCode == 404 || responseCode == 410;
    }
    return false;
  }

  private ChunkedTrackBlacklistUtil() {}

}
