/*
 * Copyright (C) 2014 The Android Open Source Project
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
package org.telegram.messenger.exoplayer.util;

import android.widget.TextView;
import org.telegram.messenger.exoplayer.CodecCounters;
import org.telegram.messenger.exoplayer.chunk.Format;
import org.telegram.messenger.exoplayer.upstream.BandwidthMeter;

/**
 * A helper class for periodically updating debug information displayed by a {@link TextView}.
 */
public final class DebugTextViewHelper implements Runnable {

  /**
   * Provides debug information about an ongoing playback.
   */
  public interface Provider {

    /**
     * Returns the current playback position, in milliseconds.
     */
    long getCurrentPosition();

    /**
     * Returns a format whose information should be displayed, or null.
     */
    Format getFormat();

    /**
     * Returns a {@link BandwidthMeter} whose estimate should be displayed, or null.
     */
    BandwidthMeter getBandwidthMeter();

    /**
     * Returns a {@link CodecCounters} whose information should be displayed, or null.
     */
    CodecCounters getCodecCounters();

  }

  private static final int REFRESH_INTERVAL_MS = 1000;

  private final TextView textView;
  private final Provider debuggable;

  /**
   * @param debuggable The {@link Provider} from which debug information should be obtained.
   * @param textView The {@link TextView} that should be updated to display the information.
   */
  public DebugTextViewHelper(Provider debuggable, TextView textView) {
    this.debuggable = debuggable;
    this.textView = textView;
  }

  /**
   * Starts periodic updates of the {@link TextView}.
   * <p>
   * Should be called from the application's main thread.
   */
  public void start() {
    stop();
    run();
  }

  /**
   * Stops periodic updates of the {@link TextView}.
   * <p>
   * Should be called from the application's main thread.
   */
  public void stop() {
    textView.removeCallbacks(this);
  }

  @Override
  public void run() {
    textView.setText(getRenderString());
    textView.postDelayed(this, REFRESH_INTERVAL_MS);
  }

  private String getRenderString() {
    return getTimeString() + " " + getQualityString() + " " + getBandwidthString() + " "
        + getVideoCodecCountersString();
  }

  private String getTimeString() {
    return "ms(" + debuggable.getCurrentPosition() + ")";
  }

  private String getQualityString() {
    Format format = debuggable.getFormat();
    return format == null ? "id:? br:? h:?"
        : "id:" + format.id + " br:" + format.bitrate + " h:" + format.height;
  }

  private String getBandwidthString() {
    BandwidthMeter bandwidthMeter = debuggable.getBandwidthMeter();
    if (bandwidthMeter == null
        || bandwidthMeter.getBitrateEstimate() == BandwidthMeter.NO_ESTIMATE) {
      return "bw:?";
    } else {
      return "bw:" + (bandwidthMeter.getBitrateEstimate() / 1000);
    }
  }

  private String getVideoCodecCountersString() {
    CodecCounters codecCounters = debuggable.getCodecCounters();
    return codecCounters == null ? "" : codecCounters.getDebugString();
  }

}
