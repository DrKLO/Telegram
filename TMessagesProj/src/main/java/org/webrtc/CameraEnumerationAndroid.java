/*
 *  Copyright 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.webrtc;

import static java.lang.Math.abs;

import android.graphics.ImageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@SuppressWarnings("deprecation")
public class CameraEnumerationAndroid {
  private final static String TAG = "CameraEnumerationAndroid";

  static final ArrayList<Size> COMMON_RESOLUTIONS = new ArrayList<Size>(Arrays.asList(
      // 0, Unknown resolution
      new Size(160, 120), // 1, QQVGA
      new Size(240, 160), // 2, HQVGA
      new Size(320, 240), // 3, QVGA
      new Size(400, 240), // 4, WQVGA
      new Size(480, 320), // 5, HVGA
      new Size(640, 360), // 6, nHD
      new Size(640, 480), // 7, VGA
      new Size(768, 480), // 8, WVGA
      new Size(854, 480), // 9, FWVGA
      new Size(800, 600), // 10, SVGA
      new Size(960, 540), // 11, qHD
      new Size(960, 640), // 12, DVGA
      new Size(1024, 576), // 13, WSVGA
      new Size(1024, 600), // 14, WVSGA
      new Size(1280, 720), // 15, HD
      new Size(1280, 1024), // 16, SXGA
      new Size(1920, 1080), // 17, Full HD
      new Size(1920, 1440), // 18, Full HD 4:3
      new Size(2560, 1440), // 19, QHD
      new Size(3840, 2160) // 20, UHD
      ));

  public static class CaptureFormat {
    // Class to represent a framerate range. The framerate varies because of lightning conditions.
    // The values are multiplied by 1000, so 1000 represents one frame per second.
    public static class FramerateRange {
      public int min;
      public int max;

      public FramerateRange(int min, int max) {
        this.min = min;
        this.max = max;
      }

      @Override
      public String toString() {
        return "[" + (min / 1000.0f) + ":" + (max / 1000.0f) + "]";
      }

      @Override
      public boolean equals(Object other) {
        if (!(other instanceof FramerateRange)) {
          return false;
        }
        final FramerateRange otherFramerate = (FramerateRange) other;
        return min == otherFramerate.min && max == otherFramerate.max;
      }

      @Override
      public int hashCode() {
        // Use prime close to 2^16 to avoid collisions for normal values less than 2^16.
        return 1 + 65537 * min + max;
      }
    }

    public final int width;
    public final int height;
    public final FramerateRange framerate;

    // TODO(hbos): If VideoCapturer.startCapture is updated to support other image formats then this
    // needs to be updated and VideoCapturer.getSupportedFormats need to return CaptureFormats of
    // all imageFormats.
    public final int imageFormat = ImageFormat.NV21;

    public CaptureFormat(int width, int height, int minFramerate, int maxFramerate) {
      this.width = width;
      this.height = height;
      this.framerate = new FramerateRange(minFramerate, maxFramerate);
    }

    public CaptureFormat(int width, int height, FramerateRange framerate) {
      this.width = width;
      this.height = height;
      this.framerate = framerate;
    }

    // Calculates the frame size of this capture format.
    public int frameSize() {
      return frameSize(width, height, imageFormat);
    }

    // Calculates the frame size of the specified image format. Currently only
    // supporting ImageFormat.NV21.
    // The size is width * height * number of bytes per pixel.
    // http://developer.android.com/reference/android/hardware/Camera.html#addCallbackBuffer(byte[])
    public static int frameSize(int width, int height, int imageFormat) {
      if (imageFormat != ImageFormat.NV21) {
        throw new UnsupportedOperationException("Don't know how to calculate "
            + "the frame size of non-NV21 image formats.");
      }
      return (width * height * ImageFormat.getBitsPerPixel(imageFormat)) / 8;
    }

    @Override
    public String toString() {
      return width + "x" + height + "@" + framerate;
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof CaptureFormat)) {
        return false;
      }
      final CaptureFormat otherFormat = (CaptureFormat) other;
      return width == otherFormat.width && height == otherFormat.height
          && framerate.equals(otherFormat.framerate);
    }

    @Override
    public int hashCode() {
      return 1 + (width * 65497 + height) * 251 + framerate.hashCode();
    }
  }

  // Helper class for finding the closest supported format for the two functions below. It creates a
  // comparator based on the difference to some requested parameters, where the element with the
  // minimum difference is the element that is closest to the requested parameters.
  private static abstract class ClosestComparator<T> implements Comparator<T> {
    // Difference between supported and requested parameter.
    abstract int diff(T supportedParameter);

    @Override
    public int compare(T t1, T t2) {
      return diff(t1) - diff(t2);
    }
  }

  // Prefer a fps range with an upper bound close to |framerate|. Also prefer a fps range with a low
  // lower bound, to allow the framerate to fluctuate based on lightning conditions.
  public static CaptureFormat.FramerateRange getClosestSupportedFramerateRange(
      List<CaptureFormat.FramerateRange> supportedFramerates, final int requestedFps) {
    return Collections.min(
        supportedFramerates, new ClosestComparator<CaptureFormat.FramerateRange>() {
          // Progressive penalty if the upper bound is further away than |MAX_FPS_DIFF_THRESHOLD|
          // from requested.
          private static final int MAX_FPS_DIFF_THRESHOLD = 5000;
          private static final int MAX_FPS_LOW_DIFF_WEIGHT = 1;
          private static final int MAX_FPS_HIGH_DIFF_WEIGHT = 3;

          // Progressive penalty if the lower bound is bigger than |MIN_FPS_THRESHOLD|.
          private static final int MIN_FPS_THRESHOLD = 8000;
          private static final int MIN_FPS_LOW_VALUE_WEIGHT = 1;
          private static final int MIN_FPS_HIGH_VALUE_WEIGHT = 4;

          // Use one weight for small |value| less than |threshold|, and another weight above.
          private int progressivePenalty(int value, int threshold, int lowWeight, int highWeight) {
            return (value < threshold) ? value * lowWeight
                                       : threshold * lowWeight + (value - threshold) * highWeight;
          }

          @Override
          int diff(CaptureFormat.FramerateRange range) {
            final int minFpsError = progressivePenalty(
                range.min, MIN_FPS_THRESHOLD, MIN_FPS_LOW_VALUE_WEIGHT, MIN_FPS_HIGH_VALUE_WEIGHT);
            final int maxFpsError = progressivePenalty(Math.abs(requestedFps * 1000 - range.max),
                MAX_FPS_DIFF_THRESHOLD, MAX_FPS_LOW_DIFF_WEIGHT, MAX_FPS_HIGH_DIFF_WEIGHT);
            return minFpsError + maxFpsError;
          }
        });
  }

  public static Size getClosestSupportedSize(
      List<Size> supportedSizes, final int requestedWidth, final int requestedHeight) {
    return Collections.min(supportedSizes, new ClosestComparator<Size>() {
      @Override
      int diff(Size size) {
        return abs(requestedWidth - size.width) + abs(requestedHeight - size.height);
      }
    });
  }

  // Helper method for camera classes.
  static void reportCameraResolution(Histogram histogram, Size resolution) {
    int index = COMMON_RESOLUTIONS.indexOf(resolution);
    // 0 is reserved for unknown resolution, so add 1.
    // indexOf returns -1 for unknown resolutions so it becomes 0 automatically.
    histogram.addSample(index + 1);
  }
}
