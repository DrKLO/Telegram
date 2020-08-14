/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.webrtc;

import android.content.Context;

/**
 * Class for storing the application context and retrieving it in a static context. Similar to
 * org.chromium.base.ContextUtils.
 */
public class ContextUtils {
  private static final String TAG = "ContextUtils";
  private static Context applicationContext;

  /**
   * Stores the application context that will be returned by getApplicationContext. This is called
   * by PeerConnectionFactory.initialize. The application context must be set before creating
   * a PeerConnectionFactory and must not be modified while it is alive.
   */
  public static void initialize(Context applicationContext) {
    if (applicationContext == null) {
      throw new IllegalArgumentException(
          "Application context cannot be null for ContextUtils.initialize.");
    }
    ContextUtils.applicationContext = applicationContext;
  }

  /**
   * Returns the stored application context.
   *
   * @deprecated crbug.com/webrtc/8937
   */
  @Deprecated
  public static Context getApplicationContext() {
    return applicationContext;
  }
}
