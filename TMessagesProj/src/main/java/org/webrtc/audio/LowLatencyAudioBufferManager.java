/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.webrtc.audio;

import android.media.AudioTrack;
import android.os.Build;
import org.webrtc.Logging;

// Lowers the buffer size if no underruns are detected for 100 ms. Once an
// underrun is detected, the buffer size is increased by 10 ms and it will not
// be lowered further. The buffer size will never be increased more than
// 5 times, to avoid the possibility of the buffer size increasing without
// bounds.
class LowLatencyAudioBufferManager {
  private static final String TAG = "LowLatencyAudioBufferManager";
  // The underrun count that was valid during the previous call to maybeAdjustBufferSize(). Used to
  // detect increases in the value.
  private int prevUnderrunCount;
  // The number of ticks to wait without an underrun before decreasing the buffer size.
  private int ticksUntilNextDecrease;
  // Indicate if we should continue to decrease the buffer size.
  private boolean keepLoweringBufferSize;
  // How often the buffer size was increased.
  private int bufferIncreaseCounter;

  public LowLatencyAudioBufferManager() {
    this.prevUnderrunCount = 0;
    this.ticksUntilNextDecrease = 10;
    this.keepLoweringBufferSize = true;
    this.bufferIncreaseCounter = 0;
  }

  public void maybeAdjustBufferSize(AudioTrack audioTrack) {
    if (Build.VERSION.SDK_INT >= 26) {
      final int underrunCount = audioTrack.getUnderrunCount();
      if (underrunCount > prevUnderrunCount) {
        // Don't increase buffer more than 5 times. Continuing to increase the buffer size
        // could be harmful on low-power devices that regularly experience underruns under
        // normal conditions.
        if (bufferIncreaseCounter < 5) {
          // Underrun detected, increase buffer size by 10ms.
          final int currentBufferSize = audioTrack.getBufferSizeInFrames();
          final int newBufferSize = currentBufferSize + audioTrack.getPlaybackRate() / 100;
          Logging.d(TAG,
              "Underrun detected! Increasing AudioTrack buffer size from " + currentBufferSize
                  + " to " + newBufferSize);
          audioTrack.setBufferSizeInFrames(newBufferSize);
          bufferIncreaseCounter++;
        }
        // Stop trying to lower the buffer size.
        keepLoweringBufferSize = false;
        prevUnderrunCount = underrunCount;
        ticksUntilNextDecrease = 10;
      } else if (keepLoweringBufferSize) {
        ticksUntilNextDecrease--;
        if (ticksUntilNextDecrease <= 0) {
          // No underrun seen for 100 ms, try to lower the buffer size by 10ms.
          final int bufferSize10ms = audioTrack.getPlaybackRate() / 100;
          // Never go below a buffer size of 10ms.
          final int currentBufferSize = audioTrack.getBufferSizeInFrames();
          final int newBufferSize = Math.max(bufferSize10ms, currentBufferSize - bufferSize10ms);
          if (newBufferSize != currentBufferSize) {
            Logging.d(TAG,
                "Lowering AudioTrack buffer size from " + currentBufferSize + " to "
                    + newBufferSize);
            audioTrack.setBufferSizeInFrames(newBufferSize);
          }
          ticksUntilNextDecrease = 10;
        }
      }
    }
  }
}
