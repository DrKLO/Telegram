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

/** Simplest possible GL shader that just draws frames as opaque quads. */
public class GlRectDrawer extends GlGenericDrawer {
  private static final String FRAGMENT_SHADER = "void main() {\n"
      + "  gl_FragColor = sample(tc);\n"
      + "}\n";

  private static class ShaderCallbacks implements GlGenericDrawer.ShaderCallbacks {
    @Override
    public void onNewShader(GlShader shader) {}

    @Override
    public void onPrepareShader(GlShader shader, float[] texMatrix, int frameWidth, int frameHeight,
        int viewportWidth, int viewportHeight) {}
  }

  public GlRectDrawer() {
    super(FRAGMENT_SHADER, new ShaderCallbacks());
  }
}
