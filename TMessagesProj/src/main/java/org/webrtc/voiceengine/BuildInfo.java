/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.webrtc.voiceengine;

import android.os.Build;

public final class BuildInfo {
  public static String getDevice() {
    return Build.DEVICE;
  }

  public static String getDeviceModel() {
    return Build.MODEL;
  }

  public static String getProduct() {
    return Build.PRODUCT;
  }

  public static String getBrand() {
    return Build.BRAND;
  }

  public static String getDeviceManufacturer() {
    return Build.MANUFACTURER;
  }

  public static String getAndroidBuildId() {
    return Build.ID;
  }

  public static String getBuildType() {
    return Build.TYPE;
  }

  public static String getBuildRelease() {
    return Build.VERSION.RELEASE;
  }

  public static int getSdkVersion() {
    return Build.VERSION.SDK_INT;
  }
}
