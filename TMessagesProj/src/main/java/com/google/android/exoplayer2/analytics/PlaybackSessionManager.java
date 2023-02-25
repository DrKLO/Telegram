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
package com.google.android.exoplayer2.analytics;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Player.DiscontinuityReason;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.analytics.AnalyticsListener.EventTime;
import com.google.android.exoplayer2.source.MediaSource.MediaPeriodId;

/**
 * Manager for active playback sessions.
 *
 * <p>The manager keeps track of the association between window index and/or media period id to
 * session identifier.
 */
public interface PlaybackSessionManager {

  /** A listener for session updates. */
  interface Listener {

    /**
     * Called when a new session is created as a result of {@link #updateSessions(EventTime)}.
     *
     * @param eventTime The {@link EventTime} at which the session is created.
     * @param sessionId The identifier of the new session.
     */
    void onSessionCreated(EventTime eventTime, String sessionId);

    /**
     * Called when a session becomes active, i.e. playing in the foreground.
     *
     * @param eventTime The {@link EventTime} at which the session becomes active.
     * @param sessionId The identifier of the session.
     */
    void onSessionActive(EventTime eventTime, String sessionId);

    /**
     * Called when a session is interrupted by ad playback.
     *
     * @param eventTime The {@link EventTime} at which the ad playback starts.
     * @param contentSessionId The session identifier of the content session.
     * @param adSessionId The identifier of the ad session.
     */
    void onAdPlaybackStarted(EventTime eventTime, String contentSessionId, String adSessionId);

    /**
     * Called when a session is permanently finished.
     *
     * @param eventTime The {@link EventTime} at which the session finished.
     * @param sessionId The identifier of the finished session.
     * @param automaticTransitionToNextPlayback Whether the session finished because of an automatic
     *     transition to the next playback item.
     */
    void onSessionFinished(
        EventTime eventTime, String sessionId, boolean automaticTransitionToNextPlayback);
  }

  /**
   * Sets the listener to be notified of session updates. Must be called before the session manager
   * is used.
   *
   * @param listener The {@link Listener} to be notified of session updates.
   */
  void setListener(Listener listener);

  /**
   * Returns the session identifier for the given media period id.
   *
   * <p>Note that this will reserve a new session identifier if it doesn't exist yet, but will not
   * call any {@link Listener} callbacks.
   *
   * @param timeline The timeline, {@code mediaPeriodId} is part of.
   * @param mediaPeriodId A {@link MediaPeriodId}.
   */
  String getSessionForMediaPeriodId(Timeline timeline, MediaPeriodId mediaPeriodId);

  /**
   * Returns whether an event time belong to a session.
   *
   * @param eventTime The {@link EventTime}.
   * @param sessionId A session identifier.
   * @return Whether the event belongs to the specified session.
   */
  boolean belongsToSession(EventTime eventTime, String sessionId);

  /**
   * Updates or creates sessions based on a player {@link EventTime}.
   *
   * <p>Call {@link #updateSessionsWithTimelineChange(EventTime)} or {@link
   * #updateSessionsWithDiscontinuity(EventTime, int)} if the event is a {@link Timeline} change or
   * a position discontinuity respectively.
   *
   * @param eventTime The {@link EventTime}.
   */
  void updateSessions(EventTime eventTime);

  /**
   * Updates or creates sessions based on a {@link Timeline} change at {@link EventTime}.
   *
   * <p>Should be called instead of {@link #updateSessions(EventTime)} if a {@link Timeline} change
   * occurred.
   *
   * @param eventTime The {@link EventTime} with the timeline change.
   */
  void updateSessionsWithTimelineChange(EventTime eventTime);

  /**
   * Updates or creates sessions based on a position discontinuity at {@link EventTime}.
   *
   * <p>Should be called instead of {@link #updateSessions(EventTime)} if a position discontinuity
   * occurred.
   *
   * @param eventTime The {@link EventTime} of the position discontinuity.
   * @param reason The {@link DiscontinuityReason}.
   */
  void updateSessionsWithDiscontinuity(EventTime eventTime, @DiscontinuityReason int reason);

  /**
   * Returns the session identifier of the session that is currently actively playing, or {@code
   * null} if there no such session.
   */
  @Nullable
  String getActiveSessionId();

  /**
   * Finishes all existing sessions and calls their respective {@link
   * Listener#onSessionFinished(EventTime, String, boolean)} callback.
   *
   * @param eventTime The event time at which sessions are finished.
   */
  void finishAllSessions(EventTime eventTime);
}
