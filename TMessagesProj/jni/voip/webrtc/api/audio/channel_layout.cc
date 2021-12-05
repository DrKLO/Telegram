/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/audio/channel_layout.h"

#include <stddef.h>

#include "rtc_base/arraysize.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

namespace webrtc {

static const int kLayoutToChannels[] = {
    0,  // CHANNEL_LAYOUT_NONE
    0,  // CHANNEL_LAYOUT_UNSUPPORTED
    1,  // CHANNEL_LAYOUT_MONO
    2,  // CHANNEL_LAYOUT_STEREO
    3,  // CHANNEL_LAYOUT_2_1
    3,  // CHANNEL_LAYOUT_SURROUND
    4,  // CHANNEL_LAYOUT_4_0
    4,  // CHANNEL_LAYOUT_2_2
    4,  // CHANNEL_LAYOUT_QUAD
    5,  // CHANNEL_LAYOUT_5_0
    6,  // CHANNEL_LAYOUT_5_1
    5,  // CHANNEL_LAYOUT_5_0_BACK
    6,  // CHANNEL_LAYOUT_5_1_BACK
    7,  // CHANNEL_LAYOUT_7_0
    8,  // CHANNEL_LAYOUT_7_1
    8,  // CHANNEL_LAYOUT_7_1_WIDE
    2,  // CHANNEL_LAYOUT_STEREO_DOWNMIX
    3,  // CHANNEL_LAYOUT_2POINT1
    4,  // CHANNEL_LAYOUT_3_1
    5,  // CHANNEL_LAYOUT_4_1
    6,  // CHANNEL_LAYOUT_6_0
    6,  // CHANNEL_LAYOUT_6_0_FRONT
    6,  // CHANNEL_LAYOUT_HEXAGONAL
    7,  // CHANNEL_LAYOUT_6_1
    7,  // CHANNEL_LAYOUT_6_1_BACK
    7,  // CHANNEL_LAYOUT_6_1_FRONT
    7,  // CHANNEL_LAYOUT_7_0_FRONT
    8,  // CHANNEL_LAYOUT_7_1_WIDE_BACK
    8,  // CHANNEL_LAYOUT_OCTAGONAL
    0,  // CHANNEL_LAYOUT_DISCRETE
    3,  // CHANNEL_LAYOUT_STEREO_AND_KEYBOARD_MIC
    5,  // CHANNEL_LAYOUT_4_1_QUAD_SIDE
    0,  // CHANNEL_LAYOUT_BITSTREAM
};

// The channel orderings for each layout as specified by FFmpeg. Each value
// represents the index of each channel in each layout.  Values of -1 mean the
// channel at that index is not used for that layout. For example, the left side
// surround sound channel in FFmpeg's 5.1 layout is in the 5th position (because
// the order is L, R, C, LFE, LS, RS), so
// kChannelOrderings[CHANNEL_LAYOUT_5_1][SIDE_LEFT] = 4;
static const int kChannelOrderings[CHANNEL_LAYOUT_MAX + 1][CHANNELS_MAX + 1] = {
    // FL | FR | FC | LFE | BL | BR | FLofC | FRofC | BC | SL | SR

    // CHANNEL_LAYOUT_NONE
    {-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1},

    // CHANNEL_LAYOUT_UNSUPPORTED
    {-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1},

    // CHANNEL_LAYOUT_MONO
    {-1, -1, 0, -1, -1, -1, -1, -1, -1, -1, -1},

    // CHANNEL_LAYOUT_STEREO
    {0, 1, -1, -1, -1, -1, -1, -1, -1, -1, -1},

    // CHANNEL_LAYOUT_2_1
    {0, 1, -1, -1, -1, -1, -1, -1, 2, -1, -1},

    // CHANNEL_LAYOUT_SURROUND
    {0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1},

    // CHANNEL_LAYOUT_4_0
    {0, 1, 2, -1, -1, -1, -1, -1, 3, -1, -1},

    // CHANNEL_LAYOUT_2_2
    {0, 1, -1, -1, -1, -1, -1, -1, -1, 2, 3},

    // CHANNEL_LAYOUT_QUAD
    {0, 1, -1, -1, 2, 3, -1, -1, -1, -1, -1},

    // CHANNEL_LAYOUT_5_0
    {0, 1, 2, -1, -1, -1, -1, -1, -1, 3, 4},

    // CHANNEL_LAYOUT_5_1
    {0, 1, 2, 3, -1, -1, -1, -1, -1, 4, 5},

    // FL | FR | FC | LFE | BL | BR | FLofC | FRofC | BC | SL | SR

    // CHANNEL_LAYOUT_5_0_BACK
    {0, 1, 2, -1, 3, 4, -1, -1, -1, -1, -1},

    // CHANNEL_LAYOUT_5_1_BACK
    {0, 1, 2, 3, 4, 5, -1, -1, -1, -1, -1},

    // CHANNEL_LAYOUT_7_0
    {0, 1, 2, -1, 5, 6, -1, -1, -1, 3, 4},

    // CHANNEL_LAYOUT_7_1
    {0, 1, 2, 3, 6, 7, -1, -1, -1, 4, 5},

    // CHANNEL_LAYOUT_7_1_WIDE
    {0, 1, 2, 3, -1, -1, 6, 7, -1, 4, 5},

    // CHANNEL_LAYOUT_STEREO_DOWNMIX
    {0, 1, -1, -1, -1, -1, -1, -1, -1, -1, -1},

    // CHANNEL_LAYOUT_2POINT1
    {0, 1, -1, 2, -1, -1, -1, -1, -1, -1, -1},

    // CHANNEL_LAYOUT_3_1
    {0, 1, 2, 3, -1, -1, -1, -1, -1, -1, -1},

    // CHANNEL_LAYOUT_4_1
    {0, 1, 2, 4, -1, -1, -1, -1, 3, -1, -1},

    // CHANNEL_LAYOUT_6_0
    {0, 1, 2, -1, -1, -1, -1, -1, 5, 3, 4},

    // CHANNEL_LAYOUT_6_0_FRONT
    {0, 1, -1, -1, -1, -1, 4, 5, -1, 2, 3},

    // FL | FR | FC | LFE | BL | BR | FLofC | FRofC | BC | SL | SR

    // CHANNEL_LAYOUT_HEXAGONAL
    {0, 1, 2, -1, 3, 4, -1, -1, 5, -1, -1},

    // CHANNEL_LAYOUT_6_1
    {0, 1, 2, 3, -1, -1, -1, -1, 6, 4, 5},

    // CHANNEL_LAYOUT_6_1_BACK
    {0, 1, 2, 3, 4, 5, -1, -1, 6, -1, -1},

    // CHANNEL_LAYOUT_6_1_FRONT
    {0, 1, -1, 6, -1, -1, 4, 5, -1, 2, 3},

    // CHANNEL_LAYOUT_7_0_FRONT
    {0, 1, 2, -1, -1, -1, 5, 6, -1, 3, 4},

    // CHANNEL_LAYOUT_7_1_WIDE_BACK
    {0, 1, 2, 3, 4, 5, 6, 7, -1, -1, -1},

    // CHANNEL_LAYOUT_OCTAGONAL
    {0, 1, 2, -1, 5, 6, -1, -1, 7, 3, 4},

    // CHANNEL_LAYOUT_DISCRETE
    {-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1},

    // CHANNEL_LAYOUT_STEREO_AND_KEYBOARD_MIC
    {0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1},

    // CHANNEL_LAYOUT_4_1_QUAD_SIDE
    {0, 1, -1, 4, -1, -1, -1, -1, -1, 2, 3},

    // CHANNEL_LAYOUT_BITSTREAM
    {-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1},

    // FL | FR | FC | LFE | BL | BR | FLofC | FRofC | BC | SL | SR
};

int ChannelLayoutToChannelCount(ChannelLayout layout) {
  RTC_DCHECK_LT(static_cast<size_t>(layout), arraysize(kLayoutToChannels));
  RTC_DCHECK_LE(kLayoutToChannels[layout], kMaxConcurrentChannels);
  return kLayoutToChannels[layout];
}

// Converts a channel count into a channel layout.
ChannelLayout GuessChannelLayout(int channels) {
  switch (channels) {
    case 1:
      return CHANNEL_LAYOUT_MONO;
    case 2:
      return CHANNEL_LAYOUT_STEREO;
    case 3:
      return CHANNEL_LAYOUT_SURROUND;
    case 4:
      return CHANNEL_LAYOUT_QUAD;
    case 5:
      return CHANNEL_LAYOUT_5_0;
    case 6:
      return CHANNEL_LAYOUT_5_1;
    case 7:
      return CHANNEL_LAYOUT_6_1;
    case 8:
      return CHANNEL_LAYOUT_7_1;
    default:
      RTC_DLOG(LS_WARNING) << "Unsupported channel count: " << channels;
  }
  return CHANNEL_LAYOUT_UNSUPPORTED;
}

int ChannelOrder(ChannelLayout layout, Channels channel) {
  RTC_DCHECK_LT(static_cast<size_t>(layout), arraysize(kChannelOrderings));
  RTC_DCHECK_LT(static_cast<size_t>(channel), arraysize(kChannelOrderings[0]));
  return kChannelOrderings[layout][channel];
}

const char* ChannelLayoutToString(ChannelLayout layout) {
  switch (layout) {
    case CHANNEL_LAYOUT_NONE:
      return "NONE";
    case CHANNEL_LAYOUT_UNSUPPORTED:
      return "UNSUPPORTED";
    case CHANNEL_LAYOUT_MONO:
      return "MONO";
    case CHANNEL_LAYOUT_STEREO:
      return "STEREO";
    case CHANNEL_LAYOUT_2_1:
      return "2.1";
    case CHANNEL_LAYOUT_SURROUND:
      return "SURROUND";
    case CHANNEL_LAYOUT_4_0:
      return "4.0";
    case CHANNEL_LAYOUT_2_2:
      return "QUAD_SIDE";
    case CHANNEL_LAYOUT_QUAD:
      return "QUAD";
    case CHANNEL_LAYOUT_5_0:
      return "5.0";
    case CHANNEL_LAYOUT_5_1:
      return "5.1";
    case CHANNEL_LAYOUT_5_0_BACK:
      return "5.0_BACK";
    case CHANNEL_LAYOUT_5_1_BACK:
      return "5.1_BACK";
    case CHANNEL_LAYOUT_7_0:
      return "7.0";
    case CHANNEL_LAYOUT_7_1:
      return "7.1";
    case CHANNEL_LAYOUT_7_1_WIDE:
      return "7.1_WIDE";
    case CHANNEL_LAYOUT_STEREO_DOWNMIX:
      return "STEREO_DOWNMIX";
    case CHANNEL_LAYOUT_2POINT1:
      return "2POINT1";
    case CHANNEL_LAYOUT_3_1:
      return "3.1";
    case CHANNEL_LAYOUT_4_1:
      return "4.1";
    case CHANNEL_LAYOUT_6_0:
      return "6.0";
    case CHANNEL_LAYOUT_6_0_FRONT:
      return "6.0_FRONT";
    case CHANNEL_LAYOUT_HEXAGONAL:
      return "HEXAGONAL";
    case CHANNEL_LAYOUT_6_1:
      return "6.1";
    case CHANNEL_LAYOUT_6_1_BACK:
      return "6.1_BACK";
    case CHANNEL_LAYOUT_6_1_FRONT:
      return "6.1_FRONT";
    case CHANNEL_LAYOUT_7_0_FRONT:
      return "7.0_FRONT";
    case CHANNEL_LAYOUT_7_1_WIDE_BACK:
      return "7.1_WIDE_BACK";
    case CHANNEL_LAYOUT_OCTAGONAL:
      return "OCTAGONAL";
    case CHANNEL_LAYOUT_DISCRETE:
      return "DISCRETE";
    case CHANNEL_LAYOUT_STEREO_AND_KEYBOARD_MIC:
      return "STEREO_AND_KEYBOARD_MIC";
    case CHANNEL_LAYOUT_4_1_QUAD_SIDE:
      return "4.1_QUAD_SIDE";
    case CHANNEL_LAYOUT_BITSTREAM:
      return "BITSTREAM";
  }
  RTC_NOTREACHED() << "Invalid channel layout provided: " << layout;
  return "";
}

}  // namespace webrtc
