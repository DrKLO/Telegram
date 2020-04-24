/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/agc/utility.h"

#include <math.h>

static const double kLog10 = 2.30258509299;
static const double kLinear2DbScale = 20.0 / kLog10;
static const double kLinear2LoudnessScale = 13.4 / kLog10;

double Loudness2Db(double loudness) {
  return loudness * kLinear2DbScale / kLinear2LoudnessScale;
}

double Linear2Loudness(double rms) {
  if (rms == 0)
    return -15;
  return kLinear2LoudnessScale * log(rms);
}

double Db2Loudness(double db) {
  return db * kLinear2LoudnessScale / kLinear2DbScale;
}

double Dbfs2Loudness(double dbfs) {
  return Db2Loudness(90 + dbfs);
}
