/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

/******************************************************************

 iLBC Speech Coder ANSI-C Source Code

 WebRtcIlbcfix_NearestNeighbor.h

******************************************************************/

#ifndef MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_NEAREST_NEIGHBOR_H_
#define MODULES_AUDIO_CODING_CODECS_ILBC_MAIN_SOURCE_NEAREST_NEIGHBOR_H_

#include <stddef.h>
#include <stdint.h>

/*----------------------------------------------------------------*
 * Find index in array such that the array element with said
 * index is the element of said array closest to "value"
 *---------------------------------------------------------------*/

void WebRtcIlbcfix_NearestNeighbor(
    size_t* index,       /* (o) index of array element closest to value */
    const size_t* array, /* (i) data array (Q2) */
    size_t value,        /* (i) value (Q2) */
    size_t arlength      /* (i) dimension of data array (==ENH_NBLOCKS_TOT) */
    );

#endif
