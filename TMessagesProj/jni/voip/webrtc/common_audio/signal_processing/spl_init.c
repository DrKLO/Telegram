/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// Some code came from common/rtcd.c in the WebM project.

#include "common_audio/signal_processing/include/signal_processing_library.h"

// TODO(bugs.webrtc.org/9553): These function pointers are useless. Refactor
// things so that we simply have a bunch of regular functions with different
// implementations for different platforms.

#if defined(WEBRTC_HAS_NEON)

const MaxAbsValueW16 WebRtcSpl_MaxAbsValueW16 = WebRtcSpl_MaxAbsValueW16Neon;
const MaxAbsValueW32 WebRtcSpl_MaxAbsValueW32 = WebRtcSpl_MaxAbsValueW32Neon;
const MaxValueW16 WebRtcSpl_MaxValueW16 = WebRtcSpl_MaxValueW16Neon;
const MaxValueW32 WebRtcSpl_MaxValueW32 = WebRtcSpl_MaxValueW32Neon;
const MinValueW16 WebRtcSpl_MinValueW16 = WebRtcSpl_MinValueW16Neon;
const MinValueW32 WebRtcSpl_MinValueW32 = WebRtcSpl_MinValueW32Neon;
const CrossCorrelation WebRtcSpl_CrossCorrelation =
    WebRtcSpl_CrossCorrelationNeon;
const DownsampleFast WebRtcSpl_DownsampleFast = WebRtcSpl_DownsampleFastNeon;
const ScaleAndAddVectorsWithRound WebRtcSpl_ScaleAndAddVectorsWithRound =
    WebRtcSpl_ScaleAndAddVectorsWithRoundC;

#elif defined(MIPS32_LE)

const MaxAbsValueW16 WebRtcSpl_MaxAbsValueW16 = WebRtcSpl_MaxAbsValueW16_mips;
const MaxAbsValueW32 WebRtcSpl_MaxAbsValueW32 =
#ifdef MIPS_DSP_R1_LE
    WebRtcSpl_MaxAbsValueW32_mips;
#else
    WebRtcSpl_MaxAbsValueW32C;
#endif
const MaxValueW16 WebRtcSpl_MaxValueW16 = WebRtcSpl_MaxValueW16_mips;
const MaxValueW32 WebRtcSpl_MaxValueW32 = WebRtcSpl_MaxValueW32_mips;
const MinValueW16 WebRtcSpl_MinValueW16 = WebRtcSpl_MinValueW16_mips;
const MinValueW32 WebRtcSpl_MinValueW32 = WebRtcSpl_MinValueW32_mips;
const CrossCorrelation WebRtcSpl_CrossCorrelation =
    WebRtcSpl_CrossCorrelation_mips;
const DownsampleFast WebRtcSpl_DownsampleFast = WebRtcSpl_DownsampleFast_mips;
const ScaleAndAddVectorsWithRound WebRtcSpl_ScaleAndAddVectorsWithRound =
#ifdef MIPS_DSP_R1_LE
    WebRtcSpl_ScaleAndAddVectorsWithRound_mips;
#else
    WebRtcSpl_ScaleAndAddVectorsWithRoundC;
#endif

#else

const MaxAbsValueW16 WebRtcSpl_MaxAbsValueW16 = WebRtcSpl_MaxAbsValueW16C;
const MaxAbsValueW32 WebRtcSpl_MaxAbsValueW32 = WebRtcSpl_MaxAbsValueW32C;
const MaxValueW16 WebRtcSpl_MaxValueW16 = WebRtcSpl_MaxValueW16C;
const MaxValueW32 WebRtcSpl_MaxValueW32 = WebRtcSpl_MaxValueW32C;
const MinValueW16 WebRtcSpl_MinValueW16 = WebRtcSpl_MinValueW16C;
const MinValueW32 WebRtcSpl_MinValueW32 = WebRtcSpl_MinValueW32C;
const CrossCorrelation WebRtcSpl_CrossCorrelation = WebRtcSpl_CrossCorrelationC;
const DownsampleFast WebRtcSpl_DownsampleFast = WebRtcSpl_DownsampleFastC;
const ScaleAndAddVectorsWithRound WebRtcSpl_ScaleAndAddVectorsWithRound =
    WebRtcSpl_ScaleAndAddVectorsWithRoundC;

#endif
